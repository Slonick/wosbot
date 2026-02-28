package cl.camodev.wosbot.serv.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTORawImage;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;

/**
 * GlobalMonitorService runs in the background and continuously checks for
 * high-priority conditions (like Bear Trap, attacks, etc.) that should
 * preempt the currently running task.
 *
 * <p>
 * Performance: One screenshot is captured per profile per monitoring cycle
 * and shared across all preemption and injection rules, avoiding expensive
 * redundant ADB captures.
 * </p>
 */
public class GlobalMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(GlobalMonitorService.class);
    private static GlobalMonitorService instance;

    private final EmulatorManager emuManager = EmulatorManager.getInstance();
    private final List<PreemptionRule> globalRules = new ArrayList<>();

    // Listeners for preemption events
    private final List<PreemptionListener> listeners = new CopyOnWriteArrayList<>();

    // Monitoring context: Profile + Check function
    private record MonitorContext(DTOProfiles profile, Function<TpDailyTaskEnum, Boolean> isExecutingCheck) {
    }

    private final Map<Long, MonitorContext> monitoredContexts = new ConcurrentHashMap<>();

    // Injection rules and queues
    private final List<InjectionRule> globalInjectionRules = new ArrayList<>();
    private final Map<Long, Queue<InjectionRule>> pendingInjections = new ConcurrentHashMap<>();

    // Single scheduler for all profiles
    private final ScheduledExecutorService scheduler;

    private GlobalMonitorService() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GlobalMonitor-Scheduler");
            t.setDaemon(true);
            return t;
        });

        // Schedule the global monitoring loop
        scheduler.scheduleWithFixedDelay(this::monitorLoop, 5, 5, TimeUnit.SECONDS);
    }

    public static synchronized GlobalMonitorService getInstance() {
        if (instance == null) {
            instance = new GlobalMonitorService();
        }
        return instance;
    }

    public void registerListener(PreemptionListener listener) {
        listeners.add(listener);
    }

    /**
     * Registers a new global rule that applies to all profiles.
     */
    public void registerRule(PreemptionRule rule) {
        globalRules.add(rule);
        logger.info("Preemption rule registered: {}", rule.getRuleName());
    }

    /**
     * Registers a new global injection rule that applies to all profiles.
     */
    public void registerInjectionRule(InjectionRule rule) {
        globalInjectionRules.add(rule);
        logger.info("Injection rule registered: {}", rule.getRuleName());
    }

    /**
     * Starts monitoring for a specific profile.
     *
     * @param profile          The profile to monitor
     * @param isExecutingCheck Function to check if a specific task is currently
     *                         executing (to avoid self-interruption)
     */
    public void startMonitoring(DTOProfiles profile, Function<TpDailyTaskEnum, Boolean> isExecutingCheck) {
        monitoredContexts.put(profile.getId(), new MonitorContext(profile, isExecutingCheck));
        pendingInjections.putIfAbsent(profile.getId(), new ConcurrentLinkedQueue<>());
        logger.info("Monitor started: {}", profile.getName());
    }

    /**
     * Stops monitoring for a specific profile.
     */
    public void stopMonitoring(Long profileId) {
        monitoredContexts.remove(profileId);
        pendingInjections.remove(profileId);
        logger.info("Monitor stopped: {}", profileId);
    }

    /**
     * Stops all monitoring.
     */
    public void shutdown() {
        monitoredContexts.clear();
        pendingInjections.clear();
        scheduler.shutdownNow();
        synchronized (GlobalMonitorService.class) {
            instance = null;
        }
        logger.info("Monitor shutdown");
    }

    private void monitorLoop() {
        monitoredContexts.values().forEach(context -> {
            try {
                // Capture ONE screenshot per profile per cycle — shared by all rules below.
                if (!emuManager.isRunning(context.profile.getEmulatorNumber())) {
                    return;
                }
                DTORawImage screenshot = emuManager.captureScreenshotViaADB(context.profile.getEmulatorNumber());
                runChecks(context, screenshot);
                runInjectionChecks(context, screenshot);
            } catch (Exception e) {
                logger.warn("Monitor error [{}]: {}", context.profile.getId(), e.getMessage());
            }
        });
    }

    private void runChecks(MonitorContext context, DTORawImage screenshot) {
        DTOProfiles profile = context.profile;

        try {
            for (PreemptionRule rule : globalRules) {
                // Don't interrupt if we are already running the target task
                if (context.isExecutingCheck.apply(rule.getTaskToExecute())) {
                    continue;
                }

                try {
                    if (rule.shouldPreempt(emuManager, profile, screenshot)) {
                        logger.info("Preempting [{}]: {}", profile.getName(), rule.getRuleName());
                        ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING,
                                "GlobalMonitor", profile.getName(),
                                "Preempted by: " + rule.getRuleName());

                        // Notify listeners
                        for (PreemptionListener listener : listeners) {
                            listener.onPreemption(profile, rule);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Rule check error [{}]: {} - {}", profile.getName(), rule.getRuleName(),
                            e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Monitor loop error [{}]: {}", profile.getName(), e.getMessage());
        }
    }

    private void runInjectionChecks(MonitorContext context, DTORawImage screenshot) {
        DTOProfiles profile = context.profile;
        Queue<InjectionRule> queue = pendingInjections.get(profile.getId());

        if (queue == null) {
            return;
        }

        try {
            for (InjectionRule rule : globalInjectionRules) {
                // Skip if this rule is already queued to prevent flooding the queue
                if (queue.stream().anyMatch(r -> r.getClass().equals(rule.getClass()))) {
                    continue;
                }

                try {
                    if (rule.shouldInject(emuManager, profile, screenshot)) {
                        logger.debug("Injecting [{}]: {}", profile.getName(), rule.getRuleName());
                        queue.offer(rule);
                    }
                } catch (Exception e) {
                    logger.error("Injection check error [{}]: {} - {}", profile.getName(), rule.getRuleName(),
                            e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Injection loop error [{}]: {}", profile.getName(), e.getMessage());
        }
    }

    /**
     * Polls a pending injection rule for the given profile to be executed by the
     * main task thread.
     *
     * @param profileId The ID of the profile.
     * @return An InjectionRule if one is pending, null otherwise.
     */
    public InjectionRule pollPendingInjection(Long profileId) {
        Queue<InjectionRule> queue = pendingInjections.get(profileId);
        if (queue != null) {
            return queue.poll();
        }
        return null;
    }
}
