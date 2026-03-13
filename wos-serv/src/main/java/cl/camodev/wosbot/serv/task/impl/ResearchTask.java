package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.utiles.time.TimeValidators;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTORawImage;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;
import net.sourceforge.tess4j.TesseractException;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ResearchTask extends DelayedTask {

    private static final int HAND_CLICK_OFFSET_X = -73;
    private static final int HAND_CLICK_OFFSET_Y = 88;
    private static final int RESEARCH_CLICK_OFFSET_X = -3;
    private static final int RESEARCH_CLICK_OFFSET_Y = -54;
    private static final int MAX_SCROLL_ATTEMPTS = 10;

    public ResearchTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    @Override
    protected void execute() {
        logInfo("Starting Research task...");

        // Ensure we start on the HOME screen before doing anything else
        navigationHelper.ensureCorrectScreenLocation(EnumStartLocation.HOME);

        // Open the left menu city section
        marchHelper.openLeftMenuCitySection(true);

        // Check research queue status via OCR before proceeding
        logDebug("Checking research queue status via OCR...");
        try {
            String queueStatus = emuManager.ocrRegionText(
                    EMULATOR_NUMBER,
                    new DTOPoint(164, 811),
                    new DTOPoint(303, 841),
                    null).trim();

            logInfo("Research queue OCR status: '" + queueStatus + "'");

            if (!queueStatus.toLowerCase().contains("idle")) {
                // Research is busy - try to read the time and reschedule smartly
                logInfo("Research queue is busy. Attempting to read remaining time...");
                Duration busyTime = durationHelper.execute(
                        new DTOPoint(164, 811),
                        new DTOPoint(303, 841),
                        5,
                        300,
                        null,
                        TimeValidators::isValidTime,
                        TimeConverters::toDuration);

                if (busyTime != null) {
                    long minutesToWait = busyTime.toMinutes();
                    LocalDateTime rescheduleTime;

                    if (minutesToWait > 30) {
                        long halfTime = minutesToWait / 2;
                        rescheduleTime = LocalDateTime.now().plusMinutes(halfTime);
                        logInfo("Research busy for " + minutesToWait + " min. Rescheduling at half time: " + halfTime + " min from now.");
                    } else {
                        rescheduleTime = LocalDateTime.now().plusMinutes(minutesToWait);
                        logInfo("Research busy for " + minutesToWait + " min. Rescheduling at: " + minutesToWait + " min from now.");
                    }

                    this.reschedule(rescheduleTime);
                } else {
                    logWarning("Could not read research queue time. Rescheduling in 1 hour.");
                    this.reschedule(LocalDateTime.now().plusHours(1));
                }
                return;
            }
        } catch (IOException | TesseractException | RuntimeException e) {
            logError("Error during research status OCR: " + e.getMessage());
            this.reschedule(LocalDateTime.now().plusHours(1));
            return;
        }

        logInfo("Research queue is Idle. Proceeding...");

        // Search for Research Center shortcut
        DTOImageSearchResult researchCenter = templateSearchHelper.searchTemplate(
                EnumTemplates.GAME_HOME_SHORTCUTS_RESEARCH_CENTER,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (!researchCenter.isFound()) {
            logError("Research Center shortcut not found.");
            return;
        }

        logDebug("Tapping Research Center");
        tapPoint(researchCenter.getPoint());
        sleepTask(1000);

        // Take a screenshot and search for hand/handMirror templates
        DTORawImage screenshot = emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);

        if (screenshot != null) {
            DTOImageSearchResult result = emuManager.searchTemplate(EMULATOR_NUMBER, screenshot, EnumTemplates.SKIP_TUTORIAL_HAND, 80.0);
            DTOImageSearchResult mirrorResult = emuManager.searchTemplate(EMULATOR_NUMBER, screenshot, EnumTemplates.SKIP_TUTORIAL_HAND_MIRROR, 80.0);

            if ((result != null && result.isFound()) || (mirrorResult != null && mirrorResult.isFound())) {
                logInfo("Hand template or mirror found! Clicking it with offset.");
                DTOPoint adjustedPoint;
                if (result != null && result.isFound()) {
                    DTOPoint handPoint = result.getPoint();
                    adjustedPoint = new DTOPoint(handPoint.getX() + HAND_CLICK_OFFSET_X, handPoint.getY() + HAND_CLICK_OFFSET_Y);
                } else {
                    // For mirrored hand, mirror the X offset as well
                    DTOPoint handPoint = mirrorResult.getPoint();
                    adjustedPoint = new DTOPoint(handPoint.getX() - HAND_CLICK_OFFSET_X, handPoint.getY() + HAND_CLICK_OFFSET_Y);
                }
                tapPoint(adjustedPoint);
            }
        }

        // Normalize menu by swiping top to bottom twice
        logDebug("Normalizing research menu with swipes...");
        for (int i = 0; i < 2; i++) {
            swipe(new DTOPoint(489, 320), new DTOPoint(489, 1156));
            sleepTask(500);
        }

        // Search for research templates with scroll-up retry
        EnumTemplates[] researchTemplates = {
                EnumTemplates.RESEARCH_0_3,
                EnumTemplates.RESEARCH_1_3,
                EnumTemplates.RESEARCH_2_3
        };

        for (int scrollAttempt = 0; scrollAttempt < MAX_SCROLL_ATTEMPTS; scrollAttempt++) {
            checkPreemption();
            sleepTask(500);

            DTORawImage researchScreenshot = emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);
            if (researchScreenshot == null) {
                logWarning("Failed to capture screenshot for research template search.");
                continue;
            }

            // Search all research templates and collect found ones
            List<DTOImageSearchResult> foundResults = new ArrayList<>();
            for (EnumTemplates template : researchTemplates) {
                DTOImageSearchResult templateResult = emuManager.searchTemplate(
                        EMULATOR_NUMBER, researchScreenshot, template, 90.0);
                if (templateResult != null && templateResult.isFound()) {
                    logInfo("Found research template: " + template.name());
                    foundResults.add(templateResult);
                }
            }

            if (!foundResults.isEmpty()) {
                // Click the one with the highest position (smallest Y value)
                DTOImageSearchResult highest = foundResults.stream()
                        .min(Comparator.comparingInt(r -> r.getPoint().getY()))
                        .get();
                logInfo("Clicking research template at position: (" + highest.getPoint().getX() + ", " + highest.getPoint().getY() + ") with offset");

                DTOPoint researchPoint = highest.getPoint();
                DTOPoint adjustedResearchPoint = new DTOPoint(researchPoint.getX() + RESEARCH_CLICK_OFFSET_X, researchPoint.getY() + RESEARCH_CLICK_OFFSET_Y);

                tapPoint(adjustedResearchPoint);
                break;
            }

            // None found - swipe up 500 pixels to scroll the menu
            logDebug("No research templates found, scrolling up (attempt " + (scrollAttempt + 1) + "/" + MAX_SCROLL_ATTEMPTS + ")");
            swipe(new DTOPoint(489, 800), new DTOPoint(489, 300));
        }

        sleepTask(1000);

        // Search for research text template to confirm research screen
        DTORawImage researchTextScreenshot = emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);
        if (researchTextScreenshot != null) {
            DTOImageSearchResult researchTextResult = emuManager.searchTemplate(
                    EMULATOR_NUMBER, researchTextScreenshot, EnumTemplates.RESEARCH_TEXT, 80.0);

            if (researchTextResult != null && researchTextResult.isFound()) {
                logInfo("Research text template found.");

                // Click the research text template location
                tapPoint(researchTextResult.getPoint());
                sleepTask(500);

                // Confirm by clicking (600, 1190)
                tapPoint(new DTOPoint(600, 1190));
                sleepTask(2000);

                // OCR the research time after confirming
                logInfo("Reading research time via OCR...");
                Duration researchTime = durationHelper.execute(
                        new DTOPoint(226, 1194),
                        new DTOPoint(422, 1234),
                        5,
                        300,
                        null,
                        TimeValidators::isValidTime,
                        TimeConverters::toDuration);

                // Dynamic rescheduling based on OCR'd time
                if (researchTime != null) {
                    long minutesToWait = researchTime.toMinutes();
                    LocalDateTime rescheduleTime;

                    if (minutesToWait > 30) {
                        long halfTime = minutesToWait / 2;
                        rescheduleTime = LocalDateTime.now().plusMinutes(halfTime);
                        logInfo("Research time exceeds 30 minutes (" + minutesToWait + " min). Rescheduling for half time: " +
                                halfTime + " minutes from now");
                    } else if (minutesToWait < 5) {
                        rescheduleTime = LocalDateTime.now().plusMinutes(minutesToWait);
                        logInfo("Research time is less than 5 minutes. Keeping normal schedule: " +
                                minutesToWait + " minutes from now");
                    } else {
                        rescheduleTime = LocalDateTime.now().plusMinutes(minutesToWait);
                        logInfo("Research time is " + minutesToWait + " minutes. Using normal schedule");
                    }

                    logInfo("Research task completed. Rescheduling for: " + rescheduleTime);
                    this.reschedule(rescheduleTime);
                    return;
                } else {
                    logWarning("Failed to OCR research time. Falling back to 1 hour reschedule.");
                }
            } else {
                logWarning("Research text template not found.");
            }
        }

        logInfo("Research task ended. Rescheduling in 1 hour.");
        this.reschedule(LocalDateTime.now().plusHours(1));
    }
}
