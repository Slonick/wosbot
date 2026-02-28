package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTORawImage;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;
import cl.camodev.utiles.UtilOCR;

import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.List;
import cl.camodev.wosbot.serv.task.rules.ManualRallyJoinPreemptionRule;

public class ManualRallyJoin extends DelayedTask {

    public ManualRallyJoin(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected void execute() {
        logInfo("=== Starting Manual Rally Join Task ===");

        // Search for the rally indicator
        logInfo("Searching for rally indicator...");
        DTOImageSearchResult indicator = templateSearchHelper.searchTemplate(
                EnumTemplates.RALLY_INDICATOR,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (indicator.isFound()) {
            logInfo("Rally indicator found at " + indicator.getPoint());
            tapPoint(indicator.getPoint());
            sleepTask(1000); // Wait for the menu to open

            // Read flag and target configuration once before the loop
            String targetKey = profile.getConfig(EnumConfigurationKey.RALLY_TARGET_STRING, String.class);
            boolean isEverything = "everything".equalsIgnoreCase(targetKey);
            EnumTemplates rallyTarget = isEverything ? null : resolveTargetTemplate(targetKey);
            logInfo("Using rally target template: " + (isEverything ? "Everything" : rallyTarget.name()) + " (key: "
                    + targetKey + ")");

            Integer marchesConfig = profile.getConfig(EnumConfigurationKey.RALLY_MARCHES_INT, Integer.class);
            int maxMarches = (marchesConfig != null) ? marchesConfig : 1;
            int deployedCount = ManualRallyJoinPreemptionRule.getActiveDeploymentsCount(profile.getId());

            logInfo("Entering deploy loop - will deploy up to " + maxMarches + " marches...");
            while (deployedCount < maxMarches) {

                // Step 1: Search for a valid matching pair (target + active green join button)
                logInfo("Searching for targets and join buttons...");
                List<DTOImageSearchResult> targets = isEverything ? null
                        : templateSearchHelper.searchTemplates(
                                rallyTarget, SearchConfigConstants.MULTIPLE_RESULTS);
                List<DTOImageSearchResult> joinButtons = templateSearchHelper.searchTemplates(
                        EnumTemplates.RALLY_JOIN, SearchConfigConstants.MULTIPLE_RESULTS);

                DTOPoint validJoinPoint = null;
                if (isEverything) {
                    if (joinButtons != null) {
                        for (DTOImageSearchResult join : joinButtons) {
                            if (isJoinButtonGreen(join.getPoint())) {
                                logInfo("Found an active green Join button at " + join.getPoint()
                                        + ". Clicking Join (Everything mode).");
                                validJoinPoint = join.getPoint();
                                break;
                            } else {
                                logInfo("Join button at " + join.getPoint()
                                        + " appears greyed out (not green). Skipping.");
                            }
                        }
                    }
                } else if (targets != null && joinButtons != null) {
                    outerLoop: for (DTOImageSearchResult target : targets) {
                        for (DTOImageSearchResult join : joinButtons) {
                            int yDiff = Math.abs(target.getPoint().getY() - join.getPoint().getY());
                            if (yDiff < 50) {
                                if (!isJoinButtonGreen(join.getPoint())) {
                                    logInfo("Join button at " + join.getPoint()
                                            + " appears greyed out (not green). Skipping.");
                                    continue;
                                }
                                logInfo("Match found! Target at " + target.getPoint() +
                                        " and Join at " + join.getPoint() +
                                        " (Diff: " + yDiff + "). Clicking Join.");
                                validJoinPoint = join.getPoint();
                                break outerLoop;
                            }
                        }
                    }
                }

                // No valid pair found — terminate
                if (validJoinPoint == null) {
                    logInfo("No matching target and join button found. Ending task.");
                    reschedule(LocalDateTime.now().plusYears(100));
                    return;
                }

                // Step 2: Click the join button and wait for the formation screen to load
                tapPoint(validJoinPoint);
                sleepTask(1000);

                // Step 3: Search for Deploy button — confirms the formation screen is open
                logInfo("Searching for Deploy button...");
                DTOImageSearchResult deployBtn = templateSearchHelper.searchTemplate(
                        EnumTemplates.RALLY_DEPLOY_BUTTON, SearchConfigConstants.SINGLE_WITH_RETRIES);

                if (!deployBtn.isFound()) {
                    logError("Deploy button not found. Ending task.");
                    reschedule(LocalDateTime.now().plusYears(100));
                    return;
                }

                logInfo("Deploy button found. Proceeding with formation settings.");

                // Step 4: Apply flag selection or equalize
                EnumConfigurationKey flagKey;
                switch (deployedCount) {
                    case 0:
                        flagKey = EnumConfigurationKey.RALLY_MARCH_1_FLAG_STRING;
                        break;
                    case 1:
                        flagKey = EnumConfigurationKey.RALLY_MARCH_2_FLAG_STRING;
                        break;
                    case 2:
                        flagKey = EnumConfigurationKey.RALLY_MARCH_3_FLAG_STRING;
                        break;
                    case 3:
                        flagKey = EnumConfigurationKey.RALLY_MARCH_4_FLAG_STRING;
                        break;
                    case 4:
                        flagKey = EnumConfigurationKey.RALLY_MARCH_5_FLAG_STRING;
                        break;
                    case 5:
                        flagKey = EnumConfigurationKey.RALLY_MARCH_6_FLAG_STRING;
                        break;
                    default:
                        flagKey = EnumConfigurationKey.RALLY_MARCH_1_FLAG_STRING;
                        break;
                }

                String currentFlagString = profile.getConfig(flagKey, String.class);
                boolean useFlag = false;
                int currentFlagNumber = 0;
                if (currentFlagString != null && !currentFlagString.trim().isEmpty()
                        && !currentFlagString.trim().equalsIgnoreCase("No Flag")) {
                    try {
                        currentFlagNumber = Integer.parseInt(currentFlagString.trim());
                        useFlag = true;
                    } catch (NumberFormatException e) {
                        logWarning("Invalid flag number in config for march " + (deployedCount + 1) + ": "
                                + currentFlagString + ". Proceeding without flag.");
                    }
                }

                if (useFlag) {
                    logInfo("Flag configuration found for march " + (deployedCount + 1) + ": #" + currentFlagNumber
                            + ". Selecting flag.");
                    marchHelper.selectFlag(currentFlagNumber);
                    sleepTask(300);
                } else {
                    logInfo("No flag configured for march " + (deployedCount + 1) + ". Searching for Equalize button.");
                    DTOImageSearchResult equalizeBtn = templateSearchHelper.searchTemplate(
                            EnumTemplates.RALLY_EQUALIZE_BUTTON, SearchConfigConstants.SINGLE_WITH_RETRIES);
                    if (equalizeBtn.isFound()) {
                        tapPoint(equalizeBtn.getPoint());
                        sleepTask(300);
                    } else {
                        logWarning("Equalize button not found for march " + (deployedCount + 1) + ".");
                    }
                }

                // Step 5: OCR travel time, then click Deploy and register the march
                long travelTimeSeconds = staminaHelper.parseTravelTime();
                LocalDateTime returnTime;
                if (travelTimeSeconds > 0) {
                    // returnTime = there + back + 5 min buffer
                    returnTime = LocalDateTime.now().plusSeconds(travelTimeSeconds * 2).plusMinutes(5);
                    logInfo("Travel time: " + travelTimeSeconds + "s → march expected back at " + returnTime);
                } else {
                    // OCR failed — fall back to 7-minute estimate
                    returnTime = LocalDateTime.now().plusMinutes(7);
                    logWarning("Could not read travel time via OCR. Using 7-minute fallback.");
                }

                logInfo("Clicking Deploy button.");
                tapPoint(deployBtn.getPoint());
                sleepTask(500);
                ManualRallyJoinPreemptionRule.registerDeployment(profile.getId(), returnTime);
                ManualRallyJoinPreemptionRule.incrementSessionJoinedCount(profile.getId());
                deployedCount++;

                if (deployedCount >= maxMarches) {
                    logInfo("Successfully deployed " + deployedCount
                            + " marches. Reached configured limit. Ending task.");
                    break;
                }

                logInfo("March " + deployedCount + " deployed successfully. Re-scanning for more available rallies...");
                // Loop back to Step 1
            }
        } else {
            logInfo("Rally indicator not found.");
        }

        // If nothing happened or didn't find match, reschedule to try again later
        reschedule(LocalDateTime.now().plusYears(100));
    }

    /**
     * Checks whether the join button at the given centre point is the active green
     * colour (#25B756 = R:37, G:183, B:86). A greyed-out button will fail this
     * check and will be skipped.
     *
     * @param center The matched centre point of the join button template
     * @return true if enough green pixels are found in the button region
     */
    private boolean isJoinButtonGreen(DTOPoint center) {
        // Target colour #25B756
        final int TARGET_R = 0x25; // 37
        final int TARGET_G = 0xB7; // 183
        final int TARGET_B = 0x56; // 86
        final int TOLERANCE = 40;
        final int MIN_GREEN_PIXELS = 5;

        try {
            DTORawImage rawImage = emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);
            BufferedImage img = UtilOCR.convertRawImageToBufferedImage(rawImage);

            int imgW = img.getWidth();
            int imgH = img.getHeight();

            // Sample a 40x20 region centred on the match point
            int x0 = Math.max(0, center.getX() - 20);
            int y0 = Math.max(0, center.getY() - 10);
            int x1 = Math.min(imgW - 1, center.getX() + 20);
            int y1 = Math.min(imgH - 1, center.getY() + 10);

            int greenCount = 0;
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    if (Math.abs(r - TARGET_R) <= TOLERANCE
                            && Math.abs(g - TARGET_G) <= TOLERANCE
                            && Math.abs(b - TARGET_B) <= TOLERANCE) {
                        greenCount++;
                        if (greenCount >= MIN_GREEN_PIXELS) {
                            return true;
                        }
                    }
                }
            }
            logInfo("Green pixel count at join button: " + greenCount + " (needs >= " + MIN_GREEN_PIXELS + ")");
            return false;
        } catch (Exception e) {
            logWarning("Color check failed for join button at " + center + ": " + e.getMessage()
                    + ". Allowing tap anyway.");
            return true; // fail-open: if screenshot fails, attempt the tap
        }
    }

    /**
     * Resolves the config target key string to the matching EnumTemplates entry.
     * Falls back to BERSERK_CRYPTID_TARGET if the key is unrecognised.
     *
     * @param key the filename key stored in RALLY_TARGET_STRING config (e.g.
     *            "caveLion")
     * @return the corresponding EnumTemplates entry
     */
    private EnumTemplates resolveTargetTemplate(String key) {
        if (key == null)
            return EnumTemplates.BERSERK_CRYPTID_TARGET;
        switch (key.trim()) {
            case "caveLion":
                return EnumTemplates.CAVE_LION_TARGET;
            case "snowApe":
                return EnumTemplates.SNOW_APE_TARGET;
            case "berserkCryptid":
            default:
                return EnumTemplates.BERSERK_CRYPTID_TARGET;
        }
    }
}
