package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.UtilTime;
import cl.camodev.wosbot.almac.entity.DailyTask;
import cl.camodev.wosbot.almac.repo.DailyTaskRepository;
import cl.camodev.wosbot.almac.repo.IDailyTaskRepository;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PolarTerrorHuntingTask extends DelayedTask {
    private final int refreshStaminaLevel = 180;
    private final int minStaminaLevel = 100;
    private final IDailyTaskRepository iDailyTaskRepository = DailyTaskRepository.getRepository();
    private final ServTaskManager servTaskManager = ServTaskManager.getInstance();
    private static final int MAX_POLAR_LEVEL = 8;

    // Static tracker to persist deployment state across task boundaries
    private static final Map<Long, List<LocalDateTime>> activeDeployments = new ConcurrentHashMap<>();

    // Configuration (loaded fresh each execution after profile refresh)
    private int polarTerrorLevel;
    private boolean limitedHunting;
    private int maxMarches;

    public PolarTerrorHuntingTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    /**
     * Registers a deployed march with its computed return time.
     */
    private static void registerDeployment(long profileId, LocalDateTime returnTime) {
        activeDeployments.computeIfAbsent(profileId, k -> new CopyOnWriteArrayList<>())
                .add(returnTime);
    }

    /**
     * Retrieves the number of active (non-expired) deployments for a profile.
     */
    private static int getActiveDeploymentsCount(long profileId) {
        List<LocalDateTime> deployments = activeDeployments.get(profileId);
        if (deployments == null)
            return 0;
        // Clean up expired deployments first to get an accurate count
        deployments.removeIf(time -> LocalDateTime.now().isAfter(time));
        return deployments.size();
    }

    @Override
    protected void execute() {
        logInfo("=== Starting Polar Terror Hunting Task ===");

        // Load configuration fresh after profile refresh
        loadConfiguration();

        if (eventHelper.isBearRunning()) {
            LocalDateTime rescheduleTo = LocalDateTime.now().plusMinutes(30);
            logInfo("Bear Hunt is running, rescheduling for " + rescheduleTo);
            reschedule(rescheduleTo);
            return;
        }
        logDebug("Bear Hunt is not running, continuing with Polar Terror Hunting Task");

        if (profile.getConfig(EnumConfigurationKey.INTEL_BOOL, Boolean.class)
                && profile.getConfig(EnumConfigurationKey.POLAR_TERROR_ENABLED_BOOL, Boolean.class)
                && servTaskManager.getTaskState(profile.getId(), TpDailyTaskEnum.INTEL.getId()).isScheduled()) {
            // Make sure intel isn't about to run
            DailyTask intel = iDailyTaskRepository.findByProfileIdAndTaskName(profile.getId(), TpDailyTaskEnum.INTEL);
            if (ChronoUnit.MINUTES.between(LocalDateTime.now(), intel.getNextSchedule()) < 5) {
                reschedule(LocalDateTime.now().plusMinutes(5)); // Reschedule in 5 minutes after intel has run
                logWarning("Intel task is scheduled to run soon. Rescheduling Polar Hunt to run 5 min after intel.");
                return;
            }
        }

        logInfo(String.format("Configuration: Level %d | %s Mode | Marches: %d",
                polarTerrorLevel,
                limitedHunting ? "Limited (10 hunts)" : "Unlimited",
                maxMarches));

        // Verify if there's enough stamina to hunt, if not, reschedule the task
        if (!staminaHelper.checkStaminaAndMarchesOrReschedule(minStaminaLevel, refreshStaminaLevel, this::reschedule))
            return;

        // Check limited hunting limit
        if (limitedHunting && !polarsRemaining(polarTerrorLevel)) {
            return; // Already rescheduled in polarsRemaining
        }

        int deployedCount = getActiveDeploymentsCount(profile.getId());
        int consecutiveFailures = 0;

        while (deployedCount < maxMarches) {
            // Check marches before each rally
            if (!marchHelper.checkMarchesAvailable()) {
                logInfo("No marches available after " + deployedCount + " rallies. Waiting for marches to return.");
                reschedule(LocalDateTime.now().plusMinutes(1));
                return;
            }

            // Check limited hunting limit
            if (limitedHunting && !polarsRemaining(polarTerrorLevel)) {
                return; // Already rescheduled in polarsRemaining
            }

            // Determine flag for current march
            String flagConfigKey = "POLAR_TERROR_MARCH_" + (deployedCount + 1) + "_FLAG_STRING";
            EnumConfigurationKey enumKey = EnumConfigurationKey.valueOf(flagConfigKey);
            String flagString = profile.getConfig(enumKey, String.class);

            boolean useFlag = false;
            int currentFlagNumber = 0;

            if (flagString != null && !flagString.trim().equals("No Flag")) {
                try {
                    currentFlagNumber = Integer.parseInt(flagString.trim());
                    useFlag = true;
                } catch (NumberFormatException e) {
                    logWarning("Invalid flag number in config: " + flagString + " for march " + (deployedCount + 1));
                    useFlag = false;
                }
            }

            logInfo("Launching rally " + (deployedCount + 1) + " of " + maxMarches +
                    (useFlag ? " with flag #" + currentFlagNumber : " (Equalize fallback)"));

            int result = launchSingleRally(polarTerrorLevel, useFlag, currentFlagNumber);

            if (result == -1) {
                // OCR error
                logError("OCR error occurred during deployment " + (deployedCount + 1));
                consecutiveFailures++;
                if (consecutiveFailures >= 3) {
                    logInfo("Too many consecutive OCR failures. Rescheduling in 5 minutes.");
                    reschedule(LocalDateTime.now().plusMinutes(5));
                    return;
                }
                sleepTask(2000);
                continue;
            } else if (result == 0) {
                // Deployment failed - probably out of marches or button missing
                logInfo("Deployment " + (deployedCount + 1) + " failed. Rescheduling in 5 minutes.");
                reschedule(LocalDateTime.now().plusMinutes(5));
                return;
            } else if (result == 3) {
                // Low stamina
                logInfo("Stamina too low after " + deployedCount + " rallies. Task rescheduled.");
                return;
            }

            // result == 1 or 2: success
            deployedCount++;
            consecutiveFailures = 0;
            logInfo("Rally #" + deployedCount + " deployed successfully. Current stamina: "
                    + staminaHelper.getCurrentStamina());
            sleepTask(1500);
        }

        logInfo("Successfully deployed all " + maxMarches + " configured rallies. Rescheduling main routine...");
        // Reschedule task to run again in roughly the time it takes for a full
        // max-stamina bar to deplete + return time
        // Just arbitrarily choose 15 minutes to let other tasks weave in between the
        // deploys.
        reschedule(LocalDateTime.now().plusMinutes(15));

    }

    /**
     * Load configuration from profile after refresh.
     * Called at the start of each execution to ensure config is current.
     */
    private void loadConfiguration() {
        this.polarTerrorLevel = profile.getConfig(EnumConfigurationKey.POLAR_TERROR_LEVEL_INT, Integer.class);
        this.limitedHunting = profile.getConfig(EnumConfigurationKey.POLAR_TERROR_MODE_STRING, String.class)
                .equals("Limited (10)");
        this.maxMarches = profile.getConfig(EnumConfigurationKey.POLAR_TERROR_MARCHES_INT, Integer.class);

        // Safety bounds
        if (this.maxMarches < 1)
            this.maxMarches = 1;
        if (this.maxMarches > 6)
            this.maxMarches = 6;

        logDebug("Configuration loaded: polarLevel=" + polarTerrorLevel + ", limitedHunting=" + limitedHunting +
                ", maxMarches=" + maxMarches);
    }

    /**
     * Launches a single polar rally without recursion
     * 
     * @return -1 if OCR error occurred
     * @return 0 if deployment failed
     * @return 1 if deployment successful (no-flag mode)
     * @return 2 if deployment successful (flag mode)
     * @return 3 if deployment successful but stamina too low to continue
     */
    private int launchSingleRally(int polarLevel, boolean useFlag, int flagNumber) {
        navigationHelper.ensureCorrectScreenLocation(getRequiredStartLocation());

        // Open polars menu
        logInfo("Navigating to polars menu");
        if (!openPolarsMenu(polarLevel)) {
            logError("Failed to open polars menu.");
            return 0;
        }

        // Open rally menu
        logInfo("Navigating to rally menu");
        if (!openRallyMenu()) {
            logError("Failed to open rally menu.");
            return 0;
        }

        // Tap "Hold a Rally" button
        tapRandomPoint(new DTOPoint(275, 821), new DTOPoint(444, 856), 1, 400);
        sleepTask(500);

        // Select flag if needed, else fallback equalize
        if (useFlag) {
            marchHelper.selectFlag(flagNumber);
        } else {
            DTOImageSearchResult equalizeBtn = templateSearchHelper.searchTemplate(
                    EnumTemplates.RALLY_EQUALIZE_BUTTON,
                    SearchConfigConstants.SINGLE_WITH_RETRIES);
            if (equalizeBtn.isFound()) {
                tapPoint(equalizeBtn.getPoint());
                sleepTask(500);
            }
        }

        // Parse travel time before leaving screen
        long travelTimeSeconds = staminaHelper.parseTravelTime();

        Integer spentStamina = staminaHelper.getSpentStamina();

        // Deploy march
        DTOImageSearchResult deploy = templateSearchHelper.searchTemplate(
                EnumTemplates.DEPLOY_BUTTON,
                SearchConfigConstants.SINGLE_WITH_RETRIES);

        if (!deploy.isFound()) {
            logDebug("Deploy button not found. Rescheduling to try again in 5 minutes.");
            return 0;
        }

        tapPoint(deploy.getPoint());
        sleepTask(2000);

        deploy = templateSearchHelper.searchTemplate(
                EnumTemplates.DEPLOY_BUTTON,
                SearchConfigConstants.SINGLE_WITH_RETRIES);
        if (deploy.isFound()) {
            // Probably march got taken by auto-join or something
            logInfo("Deploy button still found after trying to deploy march. Rescheduling to try again in 5 minutes.");
            return 0;
        }

        logInfo("March deployed successfully.");

        // Update stamina
        staminaHelper.subtractStamina(spentStamina, true);

        // We successfully sent it, now persist it into the active array tracker
        if (travelTimeSeconds > 0) {
            long returnTimeSeconds = travelTimeSeconds * 2 + 2;
            LocalDateTime returnTime = LocalDateTime.now().plusSeconds(returnTimeSeconds);
            registerDeployment(profile.getId(), returnTime);
            logInfo("March scheduled to return at " + cl.camodev.utiles.UtilTime.localDateTimeToDDHHMMSS(returnTime));
        } else {
            // OCR failed to parse travel time. We'll default to holding this queue spot for
            // 10 minutes.
            logWarning("Failed to parse travel time via OCR. Holding slot for 10 minutes.");
            registerDeployment(profile.getId(), LocalDateTime.now().plusMinutes(10));
        }

        // Check stamina for next rally
        if (staminaHelper.getCurrentStamina() <= minStaminaLevel) {
            logInfo("Stamina is at or below minimum. Stopping deployment and rescheduling.");
            reschedule(
                    LocalDateTime.now().plusMinutes(staminaHelper
                            .staminaRegenerationTime(staminaHelper.getCurrentStamina(), refreshStaminaLevel)));
            return 3;
        }
        return 1;
    }

    private boolean openRallyMenu() {
        // Search for rally button
        DTOImageSearchResult rallyButton = templateSearchHelper.searchTemplate(
                EnumTemplates.RALLY_BUTTON,
                SearchConfigConstants.SINGLE_WITH_RETRIES);
        sleepTask(500);

        if (!rallyButton.isFound()) {
            logDebug("Rally button not found.");
            sleepTask(500);
            return false;
        }

        tapPoint(rallyButton.getPoint());
        sleepTask(1000);
        return true;
    }

    private boolean openPolarsMenu(int polarLevel) {
        // Navigate to the specified polar terror level
        // Open search (magnifying glass)
        tapRandomPoint(new DTOPoint(25, 850), new DTOPoint(67, 898));
        sleepTask(2000);

        // Swipe left to find polar terror icon
        swipe(new DTOPoint(40, 913), new DTOPoint(678, 913));
        sleepTask(500);

        // Search the polar terror search icon
        DTOImageSearchResult polarTerror = templateSearchHelper.searchTemplate(
                EnumTemplates.POLAR_TERROR_SEARCH_ICON,
                SearchConfigConstants.SINGLE_WITH_RETRIES);
        logDebug("Searching for Polar Terror icon");
        for (int i = 0; i < 3 && !polarTerror.isFound(); i++) {
            swipe(new DTOPoint(40, 913), new DTOPoint(678, 913));
            sleepTask(500);
            polarTerror = templateSearchHelper.searchTemplate(
                    EnumTemplates.POLAR_TERROR_SEARCH_ICON,
                    SearchConfigConstants.SINGLE_WITH_RETRIES);
        }

        if (!polarTerror.isFound()) {
            logWarning("Failed to find the polar terrors.");
            return false;
        }

        DTOPoint[] levelPoints = {
                new DTOPoint(129, 1052), // Level 1
                new DTOPoint(173, 1052), // Level 2
                new DTOPoint(217, 1052), // Level 3
                new DTOPoint(261, 1052), // Level 4
                new DTOPoint(305, 1052), // Level 5
                new DTOPoint(349, 1052), // Level 6
                new DTOPoint(393, 1052), // Level 7
                new DTOPoint(437, 1052) // Level 8
        };
        // Need to tap on the polar terror icon and set the level
        tapPoint(polarTerror.getPoint());
        sleepTask(100);
        if (polarLevel != -1) {
            logInfo(String.format("Adjusting Polar Terror level to %d", polarLevel));
            if (polarLevel < 1 || polarLevel > levelPoints.length) {
                logError(String.format("Invalid Polar Terror level configured: %d. Must be between 1 and %d.",
                        polarLevel, MAX_POLAR_LEVEL));
                return false;
            }
            tapRandomPoint(levelPoints[polarLevel - 1], levelPoints[polarLevel - 1], 3, 100);
        }
        // tap on search button
        logDebug("Tapping on search button...");
        tapRandomPoint(new DTOPoint(301, 1200), new DTOPoint(412, 1229));
        sleepTask(1500);
        return true;
    }

    private boolean polarsRemaining(int polarLevel) {
        if (!openPolarsMenu(polarLevel)) {
            return false;
        }

        // Need to search for the magnifying glass icon to be sure we're on the search
        // screen
        DTOImageSearchResult magnifyingGlass = templateSearchHelper.searchTemplate(
                EnumTemplates.POLAR_TERROR_TAB_MAGNIFYING_GLASS_ICON,
                SearchConfigConstants.SINGLE_WITH_RETRIES);
        logDebug("Searching for magnifying glass icon");
        sleepTask(500);

        if (!magnifyingGlass.isFound()) {
            return false;
        }

        // Need to scroll down a little bit and search for the remaining hunts "Special
        // Rewards (n left)"
        tapPoint(magnifyingGlass.getPoint());
        sleepTask(2000);

        DTOImageSearchResult specialRewardsCompleted = templateSearchHelper.searchTemplate(
                EnumTemplates.POLAR_TERROR_TAB_SPECIAL_REWARDS,
                SearchConfigConstants.QUICK_SEARCH);
        for (int i = 0; i < 5 && !specialRewardsCompleted.isFound(); i++) {
            specialRewardsCompleted = templateSearchHelper.searchTemplate(
                    EnumTemplates.POLAR_TERROR_TAB_SPECIAL_REWARDS,
                    SearchConfigConstants.QUICK_SEARCH);
            if (specialRewardsCompleted.isFound()) {
                // Due to limited mode being enabled, and there's no special rewards left,
                // means there's no hunts left
                logWarning(
                        "No special rewards left, meaning there's no hunts left for today. Rescheduling task for reset");
                // Add 30 minutes to let intel and other tasks be processed
                reschedule(UtilTime.getGameReset().plusMinutes(30));
                return false;
            }
            swipe(new DTOPoint(363, 1088), new DTOPoint(363, 1030));
            sleepTask(300);
        }

        // There are still rallies available, continue navigating to world screen
        tapBackButton();
        sleepTask(500);
        return true;
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.WORLD;
    }

    @Override
    protected boolean consumesStamina() {
        return true;
    }

}
