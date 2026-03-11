package cl.camodev.wosbot.serv.task;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.IdleBehavior;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ex.ADBConnectionException;
import cl.camodev.wosbot.ex.HomeNotFoundException;
import cl.camodev.wosbot.ex.ProfileInReconnectStateException;
import cl.camodev.wosbot.ex.StopExecutionException;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOProfileStatus;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTaskQueueStatus;
import cl.camodev.wosbot.ot.DTOTaskState;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import cl.camodev.wosbot.serv.impl.ServConfig;
import cl.camodev.wosbot.serv.task.impl.InitializeTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TaskQueue manages and executes scheduled tasks for a game profile.
 * It handles task scheduling, execution, and error recovery.
 */
public class TaskQueue {

    private static final Logger logger = LoggerFactory.getLogger(TaskQueue.class);
    private static final long IDLE_WAIT_TIME = 999; // milliseconds to wait between task checking cycles

    private final TaskPriorityProvider priorityProvider = new DefaultTaskPriorityProvider();

    // Priority comparator: Earlier scheduled time first.
    private final PriorityBlockingQueue<DelayedTask> taskQueue = new PriorityBlockingQueue<>(11,
            Comparator.comparing(DelayedTask::getScheduled));

    protected final EmulatorManager emuManager = EmulatorManager.getInstance();

    // State flags
    final DTOTaskQueueStatus taskQueueStatus = new DTOTaskQueueStatus();

    // Thread that will evaluate and execute tasks
    private Thread schedulerThread;
    private DTOProfiles profile;

    // Track currently executing task context for preemption
    private volatile ExecutionContext currentExecutionContext;

    public TaskQueue(DTOProfiles profile) {
        this.profile = profile;
    }

    /**
     * Adds a task to the queue.
     */
    /**
     * Adds a task to the queue.
     */
    public synchronized void addTask(DelayedTask task) {
        taskQueue.offer(task);
    }

    /**
     * Removes a specific task from the queue based on task type
     */
    public synchronized boolean removeTask(TpDailyTaskEnum taskEnum) {
        DelayedTask prototype = DelayedTaskRegistry.create(taskEnum, profile);
        if (prototype == null) {
            logWarning("Cannot create prototype for task removal: " + taskEnum.getName());
            return false;
        }

        boolean removed = taskQueue.removeIf(task -> task.equals(prototype));

        if (removed) {
            logInfoWithTask(prototype, "Removed task " + taskEnum.getName() + " from queue");
        } else {
            logInfo("Task " + taskEnum.getName() + " was not found in queue");
        }

        return removed;
    }

    public LocalDateTime getDelay() {
        return taskQueueStatus.getDelayUntil();
    }

    public boolean isRunning() {
        return taskQueueStatus.isRunning();
    }

    public boolean isExecutingTask(TpDailyTaskEnum taskEnum) {
        ExecutionContext current = currentExecutionContext;
        return current != null && current.getTask().getTpTask() == taskEnum;
    }

    /**
     * Preempts the currently executing task if a rule triggers it.
     */
    /**
     * Preempts the currently executing task if a rule triggers it.
     */
    public synchronized void preemptCurrentTask(PreemptionRule rule) {

        DelayedTask incomingTask = DelayedTaskRegistry.create(rule.getTaskToExecute(), profile);
        if (incomingTask == null) {
            logWarning("Preemption priority failed: " + rule.getRuleName() + " (Task "
                    + rule.getTaskToExecute() + " not found)");
            return;
        }

        ExecutionContext current = currentExecutionContext;

        // Check if a task is running to decide ON PREEMPTION
        boolean shouldPreempt = false;

        if (current != null) {
            DelayedTask currentTask = current.getTask();
            int currentPriority = priorityProvider.getPriority(currentTask);
            int incomingPriority = priorityProvider.getPriority(incomingTask);

            // Equal priority ALLOWS preemption (only strictly higher priority blocks it)
            if (currentPriority > incomingPriority) {
                logInfo("Ignoring preemption: " + rule.getRuleName() + " (Current task priority higher)");
                // We DO NOT return here anymore. We proceeds to enqueue the task.
            } else {
                logWarning("Preempting " + currentTask.getTaskName() + " for: " + rule.getRuleName());
                shouldPreempt = true;
            }
        }

        // Check if the task is already scheduled (e.g. for later)
        // delayedTask.equals() checks task type and profile, so this removes any
        // existing future instance
        if (taskQueue.remove(incomingTask)) {
            logInfo("Rescheduling " + incomingTask.getTaskName()
                    + " to NOW (Preempted by: " + rule.getRuleName() + ")");
        } else {
            logInfo("Enqueued " + incomingTask.getTaskName() + " NOW (Preempted by: "
                    + rule.getRuleName() + ")");
        }

        addTask(incomingTask);

        // Execute preemption trigger LAST
        if (shouldPreempt && current != null) {
            current.preempt(rule);
        }
    }

    public synchronized boolean isTaskScheduled(TpDailyTaskEnum taskEnum) {
        DelayedTask prototype = DelayedTaskRegistry.create(taskEnum, profile);
        if (prototype == null) {
            return false;
        }
        return taskQueue.stream().anyMatch(task -> task.equals(prototype));
    }

    /**
     * Checks if a task of the given type is scheduled to run soon.
     * 
     * @param taskEnum        The task type
     * @param maxDelaySeconds The maximum delay in seconds to consider "soon"
     * @return true if found
     */
    public synchronized boolean isTaskScheduledSoon(TpDailyTaskEnum taskEnum, long maxDelaySeconds) {
        DelayedTask prototype = DelayedTaskRegistry.create(taskEnum, profile);
        if (prototype == null) {
            return false;
        }
        return taskQueue.stream().filter(task -> task.equals(prototype))
                .anyMatch(task -> task.getDelay(TimeUnit.SECONDS) <= maxDelaySeconds);
    }

    public void start() {
        if (taskQueueStatus.isRunning()) {
            return;
        }

        taskQueueStatus.setRunning(true);

        schedulerThread = Thread.ofVirtual().unstarted(this::processTaskQueue);
        schedulerThread.setName("TaskQueue-" + profile.getName());
        schedulerThread.start();
    }

    /**
     * Main task processing loop
     */
    private void processTaskQueue() {
        acquireEmulatorSlot();

        while (taskQueueStatus.isRunning()) {
            taskQueueStatus.loopStarted();

            profile = ServProfiles.getServices().getProfiles().stream()
                    .filter(p -> p.getId().equals(profile.getId()))
                    .findFirst()
                    .orElse(profile);

            if (taskQueueStatus.isPaused()) {
                handlePausedState();
                continue;
            } else if (taskQueueStatus.isReadyToReconnect() && !emuManager.isRunning(profile.getEmulatorNumber())) {
                logInfo("Emulator is not running, acquiring emulator slot now");
                acquireEmulatorSlot();
            }

            // --- SCHEDULING LOGIC ---
            // --- SCHEDULING LOGIC ---
            DelayedTask taskToExecute = null;

            synchronized (this) {
                DelayedTask head = taskQueue.peek();

                if (head == null) {
                    taskQueueStatus.setDelayUntil(LocalDateTime.now().plusSeconds(1));
                } else {
                    long delayMs = head.getDelay(TimeUnit.MILLISECONDS);

                    if (delayMs > 0) {
                        // Head is in the future. Queue sorted by Time, so all are future.
                        taskQueueStatus.setDelayUntil(head.getScheduled());
                    } else {
                        // Head is ready! Collect ALL ready tasks.
                        List<DelayedTask> readyTasks = new ArrayList<>();
                        readyTasks.add(taskQueue.poll());

                        while (taskQueue.peek() != null && taskQueue.peek().getDelay(TimeUnit.MILLISECONDS) <= 0) {
                            readyTasks.add(taskQueue.poll());
                        }

                        // Find highest priority task
                        DelayedTask bestTask = readyTasks.stream()
                                .max(Comparator.comparingInt(priorityProvider::getPriority))
                                .orElse(readyTasks.get(0));

                        readyTasks.remove(bestTask);

                        // Re-queue the rest
                        if (!readyTasks.isEmpty()) {
                            readyTasks.forEach(taskQueue::offer);
                        }

                        // Prepare to execute
                        taskToExecute = bestTask;
                    }
                }
            }

            if (taskToExecute != null) {
                taskQueueStatus.getLoopState().setExecutedTask(executeTask(taskToExecute));
            } else if (!taskQueueStatus.isPaused()) {
                // Check for pending injections during idle time
                InjectionRule pendingInjection = GlobalMonitorService.getInstance()
                        .pollPendingInjection(profile.getId());
                if (pendingInjection != null) {
                    updateProfileStatus("Executing pending injection: " + pendingInjection.getRuleName());
                    logInfo("Executing pending injection during idle: " + pendingInjection.getRuleName());

                    try {
                        cl.camodev.wosbot.serv.task.impl.InjectionTask injectionContext = new cl.camodev.wosbot.serv.task.impl.InjectionTask(
                                profile);
                        pendingInjection.executeInjection(emuManager, profile, injectionContext);
                    } catch (Exception e) {
                        logError("Error executing pending injection: " + e.getMessage());
                    }

                    // Mark as executed so we don't sleep the full idle cycle and can poll again
                    // quickly
                    taskQueueStatus.getLoopState().setExecutedTask(true);
                }
            }

            handleIdleTime();

            if (!taskQueueStatus.getLoopState().isExecutedTask() && !taskQueueStatus.isPaused()) {
                String nextTaskName = taskQueue.isEmpty() ? "None" : taskQueue.peek().getTaskName();
                String timeFormatted = formatTimeUntil(taskQueueStatus.getDelayUntil());
                updateProfileStatus("Idling for " + timeFormatted + "\nNext task: " + nextTaskName);

                taskQueueStatus.getLoopState().endLoop();
                long sleepTime = Math.max(0, IDLE_WAIT_TIME - taskQueueStatus.getLoopState().getDuration());
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Executes a task and handles any exceptions
     * 
     * @param task The task to execute
     * @return true if the task was executed, false if it wasn't
     */
    private boolean executeTask(DelayedTask task) {
        // Special handling for Initialize tasks - check if next task has acceptable
        // delay
        if (task.getTpTask() == TpDailyTaskEnum.INITIALIZE && !shouldExecuteInitializeTask()) {
            logInfoWithTask(task, "Skipping Initialize task - no upcoming tasks within acceptable idle time");
            return false;
        }

        LocalDateTime scheduledBefore = task.getScheduled();
        DTOTaskState taskState = createInitialTaskState(task);
        boolean executionSuccessful;

        ExecutionContext context = new ExecutionContext(task);
        synchronized (this) {
            currentExecutionContext = context;
        }

        try {
            logInfoWithTask(task, "Starting task execution: " + task.getTaskName());
            updateProfileStatus("Executing " + task.getTaskName());

            task.setLastExecutionTime(LocalDateTime.now());
            task.run();

            executionSuccessful = true;

            // Check if daily missions should be scheduled
            checkAndScheduleDailyMissions(task);

            // Add support for triumph progress if needed
            if (task.provideTriumphProgress()) {
                // Handle triumph progress logic here if needed
            }

        } catch (cl.camodev.wosbot.ex.TaskPreemptedException e) {
            logWarningWithTask(task, "PREEMPTED: " + e.getReasoning());
            executionSuccessful = false;

            // Handle the preemption:
            // 1. Reschedule the preempted task to run again soon
            task.reschedule(LocalDateTime.now());

            // 2. The interrupting task should have been enqueued by preemptCurrentTask

        } catch (Exception e) {
            handleTaskExecutionException(task, e);
            executionSuccessful = false;
        } finally {
            synchronized (this) {
                if (currentExecutionContext != null) {
                    currentExecutionContext.clear();
                }
                currentExecutionContext = null; // CRITICAL:
                                                // Clear
                                                // context
            }
            // Always handle task rescheduling, regardless of success or failure
            handleTaskRescheduling(task, scheduledBefore);
            finalizeTaskState(task, taskState);
        }

        return executionSuccessful;
    }

    /**
     * Determines if an Initialize task should be executed by checking if there are
     * upcoming tasks within the acceptable idle time window
     * 
     * @return true if the Initialize task should proceed, false otherwise
     */
    private boolean shouldExecuteInitializeTask() {
        // If Skip Tutorial is enabled, we bypass the standard InitializeTask
        // because SkipTutorialTask handles its own initialization.
        boolean skipTutorialEnabled = profile.getConfig(EnumConfigurationKey.SKIP_TUTORIAL_ENABLED_BOOL,
                Boolean.class);
        if (skipTutorialEnabled) {
            return false;
        }

        // Get the maximum idle time configuration desde ServConfig con fallback al
        // default
        int maxIdleMinutes = Optional.ofNullable(ServConfig.getServices().getGlobalConfig())
                .map(cfg -> cfg.get(EnumConfigurationKey.MAX_IDLE_TIME_INT.name())).map(Integer::parseInt)
                .orElse(Integer.parseInt(EnumConfigurationKey.MAX_IDLE_TIME_INT.getDefaultValue()));

        // Check if there are tasks with acceptable idle time (excluding Initialize
        // tasks)
        return hasTasksWithAcceptableIdleTime(maxIdleMinutes);
    }

    private DTOTaskState createInitialTaskState(DelayedTask task) {
        DTOTaskState taskState = new DTOTaskState();
        taskState.setProfileId(profile.getId());
        taskState.setTaskId(task.getTpDailyTaskId());
        taskState.setScheduled(true);
        taskState.setExecuting(true);
        taskState.setLastExecutionTime(LocalDateTime.now());
        taskState.setNextExecutionTime(task.getScheduled());

        ServTaskManager.getInstance().setTaskState(profile.getId(), taskState);
        return taskState;
    }

    private void finalizeTaskState(DelayedTask task, DTOTaskState taskState) {
        taskState.setExecuting(false);
        taskState.setScheduled(task.isRecurring());
        taskState.setLastExecutionTime(LocalDateTime.now());
        taskState.setNextExecutionTime(task.getScheduled());

        ServTaskManager.getInstance().setTaskState(profile.getId(), taskState);
        ServScheduler.getServices().updateDailyTaskStatus(profile, task.getTpTask(), task.getScheduled());
    }

    private void handleTaskRescheduling(DelayedTask task, LocalDateTime scheduledBefore) {
        LocalDateTime scheduledAfter = task.getScheduled();

        // Prevent infinite loop by ensuring the scheduled time has changed
        if (scheduledBefore.equals(scheduledAfter)) {
            if (task.isRecurring()) {
                logInfoWithTask(task, "Task " + task.getTaskName()
                        + " executed without rescheduling, changing scheduled time to now to avoid infinite loop");
                task.reschedule(LocalDateTime.now());
            }
        }

        if (task.isRecurring()) {
            logInfoWithTask(task,
                    "Next task scheduled to run in: " + UtilTime.localDateTimeToDDHHMMSS(task.getScheduled()));
            addTask(task);
        } else {
            logInfoWithTask(task, "Task removed from schedule");
        }
    }

    private void handleTaskExecutionException(DelayedTask task, Exception e) {
        if (e instanceof HomeNotFoundException) {
            logErrorWithTask(task, "Home not found: " + e.getMessage());
            addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
        } else if (e instanceof StopExecutionException) {
            logErrorWithTask(task, "Execution stopped: " + e.getMessage());
        } else if (e instanceof ProfileInReconnectStateException) {
            handleReconnectStateException((ProfileInReconnectStateException) e);
        } else if (e instanceof ADBConnectionException) {
            logErrorWithTask(task, "ADB connection error: " + e.getMessage());
            addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
        } else {
            logErrorWithTask(task, "Error executing task: " + e.getMessage());
        }
    }

    private void handleReconnectStateException(ProfileInReconnectStateException e) {
        Long reconnectionTime = profile.getReconnectionTime(); // in
                                                               // minutes
        if (reconnectionTime != null && reconnectionTime > 0) {
            logInfo("Profile in reconnect state, pausing queue for " + reconnectionTime + " minutes");
            taskQueueStatus.setReconnectAt(reconnectionTime);
        } else {
            logError("Profile in reconnect state, but no reconnection time set");
            attemptReconnectAndInitialize();
        }
    }

    private void resumeAfterReconnectionDelay() {
        taskQueueStatus.setNeedsReconnect(false);
        updateProfileStatus("RESUMING AFTER PAUSE");
        logInfo("TaskQueue resuming after "
                + Duration.between(taskQueueStatus.getPausedAt(), LocalDateTime.now()).toMinutes() + " minutes pause");
        taskQueueStatus.setPaused(false);

        if (!emuManager.isRunning(profile.getEmulatorNumber())) {
            logInfo("While resuming, found instance closed. Acquiring a slot now.");
            acquireEmulatorSlot();
        }
        attemptReconnectAndInitialize();
    }

    private void attemptReconnectAndInitialize() {
        try {
            // Click reconnect button if found
            DTOImageSearchResult reconnect = emuManager.searchTemplate(profile.getEmulatorNumber(),
                    EnumTemplates.GAME_HOME_RECONNECT, 90);
            if (reconnect.isFound()) {
                emuManager.tapAtPoint(profile.getEmulatorNumber(), reconnect.getPoint());
            }

            addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
        } catch (Exception ex) {
            logError("Error during reconnection: " + ex.getMessage());
        }
    }

    private void checkAndScheduleDailyMissions(DelayedTask task) {
        boolean dailyAutoSchedule = profile.getConfig(EnumConfigurationKey.DAILY_MISSION_AUTO_SCHEDULE_BOOL,
                Boolean.class);
        if (!dailyAutoSchedule || !task.provideDailyMissionProgress()) {
            return;
        }

        DTOTaskState state = ServTaskManager.getInstance().getTaskState(profile.getId(),
                TpDailyTaskEnum.DAILY_MISSIONS.getId());
        LocalDateTime next = (state != null) ? state.getNextExecutionTime() : null;
        LocalDateTime now = LocalDateTime.now();

        if (state == null || next == null || next.isAfter(now)) {
            scheduleDailyMissionsNow();
        }
    }

    private synchronized void scheduleDailyMissionsNow() {
        DelayedTask prototype = DelayedTaskRegistry.create(TpDailyTaskEnum.DAILY_MISSIONS, profile);

        // Check if task already exists in the queue
        DelayedTask existing = taskQueue.stream().filter(prototype::equals).findFirst().orElse(null);

        if (existing != null) {
            // Task already exists, reschedule it to run now
            taskQueue.remove(existing);
            existing.reschedule(LocalDateTime.now());
            existing.setRecurring(true);
            taskQueue.offer(existing);

            logInfoWithTask(existing, "Rescheduled existing " + TpDailyTaskEnum.DAILY_MISSIONS + " to run now");
        } else {
            // Task does not exist, create a new instance
            prototype.reschedule(LocalDateTime.now());
            prototype.setRecurring(false);
            taskQueue.offer(prototype);
            logInfoWithTask(prototype, "Enqueued new immediate " + TpDailyTaskEnum.DAILY_MISSIONS);
        }
    }

    // Idle time management methods
    private void idlingEmulator(LocalDateTime delayUntil) {
        IdleBehavior behavior = IdleBehavior.fromString(Optional.ofNullable(ServConfig.getServices().getGlobalConfig())
                .map(cfg -> cfg.getOrDefault(EnumConfigurationKey.IDLE_BEHAVIOR_STRING.name(),
                        EnumConfigurationKey.IDLE_BEHAVIOR_STRING.getDefaultValue()))
                .orElse(EnumConfigurationKey.IDLE_BEHAVIOR_STRING.getDefaultValue()));

        if (behavior == IdleBehavior.SEND_TO_BACKGROUND) {
            // Send game to background (home screen), keep emulator and game running
            emuManager.sendGameToBackground(profile.getEmulatorNumber());
            logInfo("Sending game to background due to large inactivity. Next task: " + delayUntil);
        } else if (behavior == IdleBehavior.PC_SLEEP) {
            logInfo("PC Sleep is enabled for idle behavior. Suspending PC...");
            schedulePcWakeUpAndSleep(delayUntil);
            return;
        } else {
            // Close the entire emulator (original behavior)
            emuManager.closeEmulator(profile.getEmulatorNumber());
            logInfo("Closing emulator due to large inactivity. Next task: " + delayUntil);
            emuManager.releaseEmulatorSlot(profile);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        updateProfileStatus("Idling till " + formatter.format(delayUntil));
    }

    private void enqueueNewTask() {
        logInfo("Scheduled task will start soon");

        // Always attempt to acquire emulator slot - EmulatorManager will handle:
        // 1. Early return if we already have a valid slot
        // 2. Conflict detection if another profile is using the same emulator
        // 3. Queuing if no slots available or conflicts exist
        // This ensures we properly reacquire slots after releasing them, even if
        // the emulator is still running (used by another profile)
        acquireEmulatorSlot();

        addTask(new InitializeTask(profile, TpDailyTaskEnum.INITIALIZE));
    }

    /**
     * Acquires an emulator slot for this profile
     */
    private void acquireEmulatorSlot() {
        updateProfileStatus("Getting queue slot");
        try {
            emuManager.adquireEmulatorSlot(profile,
                    (thread, position) -> updateProfileStatus("Waiting for slot, position: " + position));
        } catch (InterruptedException e) {
            logError("Interrupted while acquiring emulator slot: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Handles the paused state of the task queue
     */
    private void handlePausedState() {
        if (taskQueueStatus.getDelayUntil().isBefore(LocalDateTime.now())) {
            if (taskQueueStatus.needsReconnect()) {
                resumeAfterReconnectionDelay();
            } else {
                taskQueueStatus.setPaused(false);
                updateProfileStatus("RESUMING");
                if (!emuManager.isRunning(profile.getEmulatorNumber())) {
                    logInfo("While resuming, found instance closed. Acquiring a slot now.");
                    acquireEmulatorSlot();
                }
            }
            return;
        }
        try {
            updateProfileStatus("PAUSED");
            if (LocalDateTime.now().getSecond() % 10 == 0)
                logInfo("Profile is paused");
            Thread.sleep(1000); // Wait
                                // while
                                // paused
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Formats the duration until the target time as HH:mm:ss
     */
    private String formatTimeUntil(LocalDateTime targetTime) {
        Duration timeUntilNext = Duration.between(LocalDateTime.now(), targetTime);

        long hours = timeUntilNext.toHours();
        long minutes = timeUntilNext.toMinutesPart();
        long seconds = timeUntilNext.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Handles idle time logic
     */
    private void handleIdleTime() {
        if (taskQueueStatus.getLoopState().isExecutedTask() || taskQueue.isEmpty()) {
            return;
        }

        // Obtener MAX_IDLE_TIME_INT desde ServConfig en lugar del perfil, con fallback
        // al default
        int idleLimit = Optional.ofNullable(ServConfig.getServices().getGlobalConfig())
                .map(cfg -> cfg.get(EnumConfigurationKey.MAX_IDLE_TIME_INT.name())).map(Integer::parseInt)
                .orElse(Integer.parseInt(EnumConfigurationKey.MAX_IDLE_TIME_INT.getDefaultValue()));
        taskQueueStatus.setIdleTimeLimit(idleLimit);

        if (currentExecutionContext != null) {
            return;
        }

        // If delay exceeds max idle time, and we haven't yet handled it
        if (!taskQueueStatus.isIdleTimeExceeded() && taskQueueStatus.checkIdleTimeExceeded()) {

            // Fix for preemption: If the user configured the emulator to stay open (e.g.
            // for visual preemption rules),
            // we MUST NOT close/background the emulator because GlobalMonitorService needs
            // to see the screen triggers.
            boolean keepOpen = Boolean.TRUE
                    .equals(profile.getConfig(EnumConfigurationKey.KEEP_EMULATOR_OPEN_BOOL, Boolean.class));

            if (keepOpen) {
                logInfo("Idle time exceeded, but keeping emulator OPEN as requested by configuration.");
                taskQueueStatus.setIdleTimeExceeded(true);
                return;
            }

            idlingEmulator(taskQueueStatus.getDelayUntil());
            taskQueueStatus.setIdleTimeExceeded(true);
            return;
        }

        // If we're idling but the next task is coming soon, re-acquire the emulator
        if (taskQueueStatus.isIdleTimeExceeded()
                && LocalDateTime.now().plusMinutes(1).isAfter(taskQueueStatus.getDelayUntil())) {
            enqueueNewTask();
            taskQueueStatus.setIdleTimeExceeded(false);
            return;
        }
    }

    /**
     * Checks if there are queued tasks (excluding Initialize tasks) with idle time
     * less than the specified delay
     * 
     * @param maxIdleMinutes Maximum idle time allowed in minutes
     * @return true if there are tasks with acceptable idle time, false otherwise
     */
    public boolean hasTasksWithAcceptableIdleTime(int maxIdleMinutes) {
        if (taskQueue.isEmpty()) {
            return false;
        }

        long maxIdleSeconds = TimeUnit.MINUTES.toSeconds(maxIdleMinutes);

        return taskQueue.stream().filter(task -> task.getTpTask() != TpDailyTaskEnum.INITIALIZE) // Exclude Initialize
                                                                                                 // tasks
                .anyMatch(task -> {
                    long taskDelay = task.getDelay(TimeUnit.SECONDS);
                    // return taskDelay >= 0 && taskDelay < maxIdleSeconds;
                    return taskDelay < maxIdleSeconds;

                });
    }

    // Logging helper methods

    private void logInfo(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.info(prefixedMessage);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, "TaskQueue", profile.getName(), message);
    }

    private void logInfoWithTask(DelayedTask task, String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.info(prefixedMessage);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, task.getTaskName(), profile.getName(), message);
    }

    private void logWarning(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.warn(prefixedMessage);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING, "TaskQueue", profile.getName(), message);
    }

    @SuppressWarnings(value = { "unused" })
    private void logWarningWithTask(DelayedTask task, String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.warn(prefixedMessage);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.WARNING, task.getTaskName(), profile.getName(), message);
    }

    private void logError(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.error(prefixedMessage);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, "TaskQueue", profile.getName(), message);
    }

    private void logErrorWithTask(DelayedTask task, String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.error(prefixedMessage);
        ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, task.getTaskName(), profile.getName(), message);
    }

    private void updateProfileStatus(String status) {
        ServProfiles.getServices().notifyProfileStatusChange(new DTOProfileStatus(profile.getId(), status));
    }

    /**
     * Immediately stops queue processing, regardless of its state.
     */
    public void stop() {
        taskQueueStatus.setRunning(false); // Stop the main loop

        if (schedulerThread != null) {
            schedulerThread.interrupt(); // Interrupt the thread to force an immediate exit

            try {
                schedulerThread.join(1000); // Wait up to 1 second for the thread to finish
            } catch (InterruptedException e) {
                logError("Interrupted while stopping TaskQueue: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        taskQueueStatus.reset(); // Reset status
        // Remove all pending tasks from the queue
        taskQueue.clear();
        updateProfileStatus("NOT RUNNING");
        logInfo("TaskQueue stopped immediately");
    }

    /**
     * Pauses queue processing, keeping tasks in the queue.
     */
    public void pause() {
        taskQueueStatus.pause();
        updateProfileStatus("PAUSE REQUESTED");
        logInfo("TaskQueue paused");
    }

    /**
     * Resumes queue processing.
     */
    public void resume() {
        taskQueueStatus.setPaused(false);
        updateProfileStatus("RESUMING");
        logInfo("TaskQueue resumed");
    }

    /**
     * Executes a specific task immediately
     */
    public synchronized void executeTaskNow(TpDailyTaskEnum taskEnum, boolean recurring) {
        // Obtain the task prototype from the registry
        DelayedTask prototype = DelayedTaskRegistry.create(taskEnum, profile);
        if (prototype == null) {
            logWarning("Task not found: " + taskEnum);
            return;
        }

        taskQueueStatus.setNeedsReconnect(true);
        taskQueueStatus.setPaused(false);

        // Check if the task already exists in the queue
        DelayedTask existing = taskQueue.stream().filter(prototype::equals).findFirst().orElse(null);

        if (existing != null) {
            // Task already exists, reschedule it to run now
            taskQueue.remove(existing);
            existing.setProfile(profile);
            existing.reschedule(LocalDateTime.now());
            existing.setRecurring(recurring);
            taskQueue.offer(existing);

            logInfoWithTask(existing, "Rescheduled existing " + taskEnum + " to run now");
        } else {
            // Task does not exist, create a new instance
            prototype.reschedule(LocalDateTime.now());
            prototype.setRecurring(recurring);
            taskQueue.offer(prototype);
            logInfoWithTask(prototype, "Enqueued new immediate " + taskEnum);
        }

        // Update task state
        DTOTaskState taskState = new DTOTaskState();
        taskState.setProfileId(profile.getId());
        taskState.setTaskId(taskEnum.getId());
        taskState.setScheduled(true);
        taskState.setExecuting(false);
        taskState.setLastExecutionTime(LocalDateTime.now());
        taskState.setNextExecutionTime(prototype.getScheduled());
        ServTaskManager.getInstance().setTaskState(profile.getId(), taskState);
    }

    public DTOProfiles getProfile() {
        return profile;
    }

	private void schedulePcWakeUpAndSleep(LocalDateTime wakeUpTime) {
		try {
			// Close emulator before sleep
			emuManager.closeEmulator(profile.getEmulatorNumber());
			emuManager.releaseEmulatorSlot(profile);
			logInfo("Emulator closed for PC sleep.");

			// Calculate when to wake up. Wake up 1 minutes before the task.
			LocalDateTime actualWakeTime = wakeUpTime.minusMinutes(1);
			if (actualWakeTime.isBefore(LocalDateTime.now())) {
				actualWakeTime = LocalDateTime.now().plusMinutes(1);
			}

			java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
			String timeStr = actualWakeTime.format(timeFormatter);
			java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy");
			String dateStr = actualWakeTime.format(dateFormatter);

			String botJarPath = "C:\\Users\\parad\\OneDrive\\Desktop\\wosbot-main\\wos-hmi\\target\\wos-bot-1.7.0.jar";

			// Step 1: Enable wake timers in the active power plan
			logInfo("Enabling wake timers in Windows power settings...");
			new ProcessBuilder("powercfg", "-SETACVALUEINDEX", "SCHEME_CURRENT", "238c9fa8-0aad-41ed-83f4-97be242c8f20", "bd3b718a-0680-4d9d-8ab2-e1d2b4ac806d", "1").start().waitFor();
			new ProcessBuilder("powercfg", "-SETDCVALUEINDEX", "SCHEME_CURRENT", "238c9fa8-0aad-41ed-83f4-97be242c8f20", "bd3b718a-0680-4d9d-8ab2-e1d2b4ac806d", "1").start().waitFor();
			new ProcessBuilder("powercfg", "-SETACTIVE", "SCHEME_CURRENT").start().waitFor();

			// Step 2: Create scheduled task using schtasks.exe
			logInfo("Scheduling PC wake up at " + dateStr + " " + timeStr);
			String taskRunCommand = "javaw.exe -jar \"" + botJarPath + "\" --autostart";

			ProcessBuilder schtasksPb = new ProcessBuilder(
					"schtasks", "/create",
					"/TN", "WosBot_AutoStart",
					"/TR", taskRunCommand,
					"/SC", "ONCE",
					"/ST", timeStr,
					"/SD", dateStr,
					"/RL", "HIGHEST", // Run with highest privileges
					"/F"
			);
			schtasksPb.redirectErrorStream(true);
			Process schtasksProcess = schtasksPb.start();
			int exitCode = schtasksProcess.waitFor();
			logInfo("schtasks registration exit code: " + exitCode);

			if (exitCode != 0) {
				logError("Failed to register scheduled task! Wake-up will not work.");
				return;
			}

			// Step 3: Enable WakeToRun and Battery settings on the task (Exhaustive settings for S0/S3)
			java.nio.file.Path wakeScriptPath = java.nio.file.Paths.get(System.getProperty("user.dir"), "wosbot_wake_setup.ps1");
			String wakeScript = 
					"$settings = New-ScheduledTaskSettingsSet -WakeToRun -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -Priority 1\n" +
					"$settings.DisallowStartIfOnBatteries = $false\n" +
					"Set-ScheduledTask -TaskName 'WosBot_AutoStart' -Settings $settings\n";
			java.nio.file.Files.writeString(wakeScriptPath, wakeScript);

			logInfo("Task scheduled. Configuring wake settings...");
			ProcessBuilder wakePb = new ProcessBuilder(
					"powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", wakeScriptPath.toString()
			);
			wakePb.redirectErrorStream(true);
			Process wakeProcess = wakePb.start();
			int wakeExitCode = wakeProcess.waitFor();
			logInfo("Wake settings setup exit code: " + wakeExitCode);

			// Step 4: Put PC to sleep (fire and let OS suspend everything)
			logInfo("Initiating PC sleep...");
			java.nio.file.Path sleepScriptPath = java.nio.file.Paths.get(System.getProperty("user.dir"), "wosbot_sleep.ps1");
			String sleepScript = 
					"Start-Sleep -Seconds 2\n" +
					"Add-Type -AssemblyName System.Windows.Forms\n" +
					"[System.Windows.Forms.Application]::SetSuspendState('Suspend', $false, $false)\n";
			java.nio.file.Files.writeString(sleepScriptPath, sleepScript);

			ProcessBuilder sleepPb = new ProcessBuilder(
					"powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", sleepScriptPath.toString()
			);
			sleepPb.start();

			System.exit(0);

		} catch (Exception e) {
			logError("Error scheduling PC wake up or putting PC to sleep: " + e.getMessage());
		}
	}
}
