package cl.camodev.wosbot.serv.task.rules;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTORawImage;
import cl.camodev.wosbot.serv.task.PreemptionRule;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;

public class ManualRallyJoinPreemptionRule implements PreemptionRule {

    private static final Logger logger = LoggerFactory.getLogger(ManualRallyJoinPreemptionRule.class);
    private final Map<Long, Integer> lastSeenCounts = new ConcurrentHashMap<>();
    private final Map<Long, Integer> notFoundCounts = new ConcurrentHashMap<>();
    private static final Map<Long, List<LocalDateTime>> activeDeployments = new ConcurrentHashMap<>();
    private static final Map<Long, Integer> sessionJoinedCounts = new ConcurrentHashMap<>();

    /**
     * Increments the number of rallies manually joined during this session.
     * To be called from ManualRallyJoin task after a successful deploy.
     */
    public static void incrementSessionJoinedCount(long profileId) {
        int newCount = sessionJoinedCounts.merge(profileId, 1, Integer::sum);
        logger.info("Session joined count for profile " + profileId + " incremented to " + newCount);
    }

    /**
     * Registers a deployed march with its computed return time.
     *
     * @param profileId  the profile that sent the march
     * @param returnTime the LocalDateTime when the march is expected to have
     *                   returned
     *                   (caller should compute as: now + travelTime*2 + buffer)
     */
    public static void registerDeployment(long profileId, LocalDateTime returnTime) {
        activeDeployments.computeIfAbsent(profileId, k -> new CopyOnWriteArrayList<>())
                .add(returnTime);
    }

    /**
     * Retrieves the number of active (non-expired) deployments for a profile.
     * 
     * @param profileId the profile ID
     * @return the number of active deployments
     */
    public static int getActiveDeploymentsCount(long profileId) {
        List<LocalDateTime> deployments = activeDeployments.get(profileId);
        if (deployments == null)
            return 0;
        // Clean up expired deployments first to get an accurate count
        deployments.removeIf(time -> LocalDateTime.now().isAfter(time));
        return deployments.size();
    }

    // Define the specific search region that contains both the indicator and the
    // numbers
    private static final DTOPoint SEARCH_TOP_LEFT = new DTOPoint(606, 485);
    private static final DTOPoint SEARCH_BOTTOM_RIGHT = new DTOPoint(718, 605);

    @Override
    public boolean shouldPreempt(EmulatorManager emuManager, DTOProfiles profile, DTORawImage screenshot) {
        // Only run if the Manual Rally Join feature is actually enabled in the
        // configuration
        Boolean rallyEnabled = profile.getConfig(EnumConfigurationKey.RALLY_ENABLED_BOOL, Boolean.class);
        if (rallyEnabled == null || !rallyEnabled) {
            // Also clear lastSeenCounts to prevent stale states if toggled off and on again
            if (lastSeenCounts.containsKey(profile.getId())) {
                lastSeenCounts.remove(profile.getId());
            }
            if (notFoundCounts.containsKey(profile.getId())) {
                notFoundCounts.remove(profile.getId());
            }
            return false;
        }

        try {
            // First check if we have enough marches
            int maxMarches = profile.getConfig(EnumConfigurationKey.RALLY_MARCHES_INT, Integer.class);
            List<LocalDateTime> deployments = activeDeployments.get(profile.getId());
            if (deployments != null) {
                // Remove expired deployments (those that have returned)
                boolean removed = deployments.removeIf(time -> {
                    if (LocalDateTime.now().isAfter(time)) {
                        logger.info("A deployed march has returned. Restoring it to the available pool.");
                        ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, getRuleName(), profile.getName(),
                                "A deployed march has returned. Restoring it to the available pool.");
                        return true;
                    }
                    return false;
                });
                // If a march just returned, reset lastSeenCounts so the next cycle
                // treats the current rally count as "new" and triggers a fresh deployment.
                if (removed) {
                    lastSeenCounts.remove(profile.getId());
                    notFoundCounts.remove(profile.getId());
                }
                if (deployments.size() >= maxMarches) {
                    // All slots still busy — but keep lastSeenCounts up-to-date with the
                    // current on-screen count so we don't get a stale value when a slot
                    // eventually opens up.
                    DTOImageSearchResult indicator = emuManager.searchTemplate(
                            profile.getEmulatorNumber(),
                            screenshot,
                            EnumTemplates.RALLY_INDICATOR,
                            SEARCH_TOP_LEFT,
                            SEARCH_BOTTOM_RIGHT,
                            SearchConfigConstants.DEFAULT_SINGLE.getThreshold());
                    if (!indicator.isFound()) {
                        int misses = notFoundCounts.getOrDefault(profile.getId(), 0) + 1;
                        notFoundCounts.put(profile.getId(), misses);
                        if (misses >= 3) {
                            lastSeenCounts.remove(profile.getId());
                        }
                    } else {
                        notFoundCounts.remove(profile.getId());
                    }
                    return false; // All allocated marches are currently busy
                }
            }

            // Use the shared screenshot — no new ADB capture needed
            DTOImageSearchResult indicator = emuManager.searchTemplate(
                    profile.getEmulatorNumber(),
                    screenshot,
                    EnumTemplates.RALLY_INDICATOR,
                    SEARCH_TOP_LEFT,
                    SEARCH_BOTTOM_RIGHT,
                    SearchConfigConstants.DEFAULT_SINGLE.getThreshold());

            if (!indicator.isFound()) {
                // If indicator not found, reset count to allow re-triggering if it appears
                // again after 3 consecutive misses
                int misses = notFoundCounts.getOrDefault(profile.getId(), 0) + 1;
                notFoundCounts.put(profile.getId(), misses);
                if (misses >= 3) {
                    lastSeenCounts.remove(profile.getId());
                }
                return false;
            }

            // Indicator found, reset not found counter
            notFoundCounts.remove(profile.getId());

            // Indicator found, search for number templates in the same region
            EnumTemplates[] numberTemplates = {
                    EnumTemplates.NUMBER_1,
                    EnumTemplates.NUMBER_2,
                    EnumTemplates.NUMBER_3,
                    EnumTemplates.NUMBER_4,
                    EnumTemplates.NUMBER_5,
                    EnumTemplates.NUMBER_6,
                    EnumTemplates.NUMBER_7,
                    EnumTemplates.NUMBER_8,
                    EnumTemplates.NUMBER_9,
                    EnumTemplates.NUMBER_10
            };

            int currentCount = -1;
            double bestMatch = 0.88d; // Minimum acceptable threshold

            for (int i = 0; i < numberTemplates.length; i++) {
                DTOImageSearchResult numResult = emuManager.searchTemplate(
                        profile.getEmulatorNumber(),
                        screenshot,
                        numberTemplates[i],
                        SEARCH_TOP_LEFT,
                        SEARCH_BOTTOM_RIGHT,
                        0.88d);

                if (numResult.isFound() && numResult.getMatchPercentage() > bestMatch) {
                    currentCount = i + 1;
                    bestMatch = numResult.getMatchPercentage(); // Keep the highest probability match
                }
            }

            if (currentCount == -1) {
                return false;
            }
            Integer lastCount = lastSeenCounts.get(profile.getId());

            // "initially the stored no will be null so now it is stored as 1"
            if (lastCount == null) {
                lastSeenCounts.put(profile.getId(), currentCount);
                logger.info("Rally count initially: " + currentCount);
                ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, getRuleName(), profile.getName(),
                        "Rally count initially: " + currentCount);
                return true;
            }

            // "if the stored number increases like from 1 to 2 the bot again runs
            // ManualRallyJoin"
            if (currentCount > lastCount) {
                lastSeenCounts.put(profile.getId(), currentCount);

                // Parse the limit from the selected mode string
                String modeString = profile.getConfig(EnumConfigurationKey.RALLY_MODE_STRING, String.class);
                int limit = -1; // Unlimited
                if (modeString != null && modeString.toLowerCase().contains("limited")) {
                    try {
                        limit = Integer.parseInt(modeString.replaceAll("[^0-9]", ""));
                    } catch (NumberFormatException ignored) {
                        // fallback to unlimited if parsing fails
                    }
                }

                int totalJoinedSession = sessionJoinedCounts.getOrDefault(profile.getId(), 0);
                if (limit != -1 && totalJoinedSession >= limit) {
                    logger.info("Rally limit reached (" + limit + "). Not joining new rally.");
                    ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, getRuleName(), profile.getName(),
                            "Rally limit reached (" + limit + "). Not joining new rally.");
                    return false;
                }

                logger.info("Rally count increased: " + lastCount + " -> " + currentCount);
                ServLogs.getServices().appendLog(EnumTpMessageSeverity.INFO, getRuleName(), profile.getName(),
                        "Rally count increased: " + lastCount + " -> " + currentCount);
                return true;
            }

            // "if the number decreases nothing happens and the decreased no is now stored"
            if (currentCount < lastCount) {
                lastSeenCounts.put(profile.getId(), currentCount);
                logger.debug("Rally count decreased: " + lastCount + " -> " + currentCount);
                ServLogs.getServices().appendLog(EnumTpMessageSeverity.DEBUG, getRuleName(), profile.getName(),
                        "Rally count decreased: " + lastCount + " -> " + currentCount);
                return false;
            }

            // If equal, do nothing
            return false;

        } catch (Exception e) {
            logger.error("Rally Preemption error [" + profile.getName() + "]", e);
            ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, getRuleName(), profile.getName(),
                    "Error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public TpDailyTaskEnum getTaskToExecute() {
        return TpDailyTaskEnum.EVENT_BERSERK_CRYPTID;
    }

    @Override
    public String getRuleName() {
        return "ManualRallyJoin";
    }
}
