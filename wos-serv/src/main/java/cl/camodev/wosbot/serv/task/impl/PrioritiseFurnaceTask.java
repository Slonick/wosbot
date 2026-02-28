package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.constants.SearchConfigConstants;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import cl.camodev.wosbot.ot.DTOPoint;

import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import static cl.camodev.wosbot.serv.task.constants.LeftMenuTextSettings.*;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.GAME_HOME_FURNACE;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.GAME_HOME_SHORTCUTS_GO;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.GAME_HOME_SHORTCUTS_OBTAIN;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.GAME_HOME_SHORTCUTS_UPGRADE;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.GAME_HOME_SHORTCUTS_UPGRADE_TEXT;
import static cl.camodev.wosbot.console.enumerable.EnumTemplates.GAME_HOME_WORLD;

public class PrioritiseFurnaceTask extends DelayedTask {

    // Upgrade queue areas
    private static final DTOArea QUEUE_AREA_1 = new DTOArea(new DTOPoint(95, 377), new DTOPoint(358, 398));
    private static final DTOArea QUEUE_AREA_2 = new DTOArea(new DTOPoint(95, 450), new DTOPoint(358, 474));

    // List of queue areas to check
    private final List<DTOArea> queues = new ArrayList<>(Arrays.asList(QUEUE_AREA_1, QUEUE_AREA_2));

    // Retry counter to prevent infinite loops
    private int retryCount = 0;

    public PrioritiseFurnaceTask(DTOProfiles profile, TpDailyTaskEnum tpDailyTaskEnum) {
        super(profile, tpDailyTaskEnum);
    }

    @Override
    protected void execute() {
        logInfo("Starting Prioritise Furnace task");

        DTOImageSearchResult worldResult = templateSearchHelper.searchTemplate(GAME_HOME_WORLD,
                SearchConfigConstants.DEFAULT_SINGLE);
        DTOImageSearchResult cityResult = templateSearchHelper.searchTemplate(GAME_HOME_FURNACE,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (worldResult.isFound()) {
            logInfo("World image found (Not on correct screen). Clicking to navigate.");
            tapPoint(worldResult.getPoint());
        } else if (cityResult.isFound()) {
            logInfo("City image found. Screen state validated.");
        } else {
            logInfo("Neither World nor City image found.");
        }

        // Pre-check clicks (Left Menu -> City Tab)
        tapPoint(new DTOPoint(15, 552));
        sleepTask(200);
        for (int i = 0; i < 4; i++) {
            tapPoint(new DTOPoint(120, 270));
            sleepTask(20);
        }
        sleepTask(200); // Allow UI to update

        // Analyze queues first
        List<QueueAnalysisResult> queueResults = analyzeAllQueues();
        logQueueSummary(queueResults);

        boolean hasIdleQueue = queueResults.stream()
                .anyMatch(result -> result.state.status == QueueStatus.IDLE ||
                        result.state.status == QueueStatus.IDLE_TEMP);

        if (!hasIdleQueue) {
            rescheduleBasedOnBusyQueues(queueResults);
            return;
        }

        // Step 2: Shortcuts and Upgrade Logic
        tapPoint(new DTOPoint(300, 20));
        sleepTask(500);
        tapPoint(new DTOPoint(80, 649));
        sleepTask(500);

        DTOImageSearchResult goResult = templateSearchHelper.searchTemplate(GAME_HOME_SHORTCUTS_GO,
                SearchConfigConstants.DEFAULT_SINGLE);

        if (goResult.isFound()) {
            tapPoint(goResult.getPoint());
            sleepTask(500);
            tapPoint(new DTOPoint(7, 440));
            sleepTask(500);
            tapPoint(new DTOPoint(136, 658));
            sleepTask(500);

            DTOImageSearchResult upgradeTextResult = templateSearchHelper.searchTemplate(
                    GAME_HOME_SHORTCUTS_UPGRADE_TEXT,
                    SearchConfigConstants.DEFAULT_SINGLE);

            if (upgradeTextResult.isFound()) {
                logInfo("upgradetext.png found. Clicking coordinates " + upgradeTextResult.getPoint());
                tapPoint(upgradeTextResult.getPoint());
                sleepTask(200);

                // Step 3 Logic
                for (int n = 0; n < 2; n++) {
                    DTOImageSearchResult upgradeTextLoopResult = templateSearchHelper.searchTemplate(
                            GAME_HOME_SHORTCUTS_UPGRADE_TEXT,
                            SearchConfigConstants.DEFAULT_SINGLE);
                    DTOImageSearchResult obtainResult = templateSearchHelper.searchTemplate(
                            GAME_HOME_SHORTCUTS_OBTAIN,
                            SearchConfigConstants.DEFAULT_SINGLE);

                    if (upgradeTextLoopResult.isFound()) {
                        logInfo("upgradetext.png found again. Clicking it.");
                        // ocr here to read time once and use that time.
                        // OCR Logic to get time
                        long minutesToWait = 30; // Default to 30 minutes if OCR fails
                        try {
                            emuManager.captureScreenshotViaADB(EMULATOR_NUMBER); // Capture fresh screenshot
                            DTOTesseractSettings[] settingsToTry = {
                                    WHITE_SETTINGS,
                                    WHITE_NUMBERS,
                                    RED_SETTINGS,
                                    ORANGE_SETTINGS,
                            };

                            DTOArea timeArea = new DTOArea(new DTOPoint(490, 1209), new DTOPoint(605, 1239));
                            boolean timeFound = false;

                            for (DTOTesseractSettings settings : settingsToTry) {
                                String ocrText = emuManager.ocrRegionText(
                                        EMULATOR_NUMBER,
                                        timeArea.topLeft(),
                                        timeArea.bottomRight(),
                                        settings).trim();

                                logDebug("OCR result with settings " + settings.getClass().getSimpleName() + ": '"
                                        + ocrText + "'");

                                if (ocrText.matches(".*(\\d+d\\s*)?\\d{6}.*")) {
                                    // Clean up the text to extract time
                                    String cleanedTime = ocrText.replaceAll("[^0-9d]", "").trim();
                                    if (!cleanedTime.isEmpty()) {
                                        minutesToWait = parseTimeToMinutes(cleanedTime);
                                        timeFound = true;
                                        logInfo("OCR Success. Time found: " + cleanedTime + " (" + minutesToWait
                                                + " minutes)");
                                        break;
                                    }
                                }
                            }
                            if (!timeFound) {
                                logWarning("Could not parse time from OCR. Using default 30 minutes.");
                            }

                        } catch (Exception e) {
                            logError("Error during OCR time extraction: " + e.getMessage());
                        }

                        tapPoint(upgradeTextLoopResult.getPoint());
                        sleepTask(1000);
                        if (obtainResult.isFound()) {
                            tapPoint(new DTOPoint(362, 1138));
                            sleepTask(200);
                            tapPoint(new DTOPoint(519, 1040));
                            sleepTask(500);
                            tapPoint(upgradeTextLoopResult.getPoint());
                        }
                        tapPoint(new DTOPoint(133, 545));

                        // Smart Rescheduling Logic
                        LocalDateTime rescheduleTime;
                        if (minutesToWait > 30) {
                            long halfTime = minutesToWait / 2;
                            rescheduleTime = LocalDateTime.now().plusMinutes(halfTime);
                            logInfo("Wait time exceeds 30 minutes (" + minutesToWait
                                    + " min). Rescheduling for half time: " +
                                    halfTime + " minutes from now");
                        } else if (minutesToWait < 5) {
                            rescheduleTime = LocalDateTime.now().plusMinutes(minutesToWait);
                            logInfo("Wait time is less than 5 minutes. Keeping normal schedule: " +
                                    minutesToWait + " minutes from now");
                        } else {
                            rescheduleTime = LocalDateTime.now().plusMinutes(minutesToWait);
                            logInfo("Wait time is " + minutesToWait + " minutes. Using normal schedule");
                        }

                        this.retryCount = 0; // Reset retry count on success
                        this.reschedule(rescheduleTime);
                        return;
                    } else {
                        DTOImageSearchResult goLoopResult = templateSearchHelper.searchTemplate(
                                GAME_HOME_SHORTCUTS_GO,
                                SearchConfigConstants.DEFAULT_SINGLE);

                        if (goLoopResult.isFound()) {
                            logInfo("go.png found. Clicking it.");
                            tapPoint(goLoopResult.getPoint());
                            sleepTask(500);

                            // Loop: Max 4 iterations to find upgrade.png
                            for (int j = 0; j < 4; j++) {
                                logInfo("Upgrade search iteration: " + (j + 1));
                                tapPoint(new DTOPoint(350, 667));
                                sleepTask(200);

                                DTOImageSearchResult upgradeResult = templateSearchHelper.searchTemplate(
                                        GAME_HOME_SHORTCUTS_UPGRADE,
                                        SearchConfigConstants.DEFAULT_SINGLE);

                                if (upgradeResult.isFound()) {
                                    logInfo("upgrade.png found. Clicking it.");
                                    tapPoint(upgradeResult.getPoint());
                                    break; // Break loop when upgrade.png is clicked
                                }
                            }
                        }
                    }
                }

            }

        }

        sleepTask(200);

        // Step 4: Upgrade/Help/Obtain Loop (Max 3 times)
        for (int k = 0; k < 3; k++) {
            logInfo("Step 4 Loop iteration: " + (k + 1));

            DTOImageSearchResult upgradeTextResult = templateSearchHelper.searchTemplate(
                    GAME_HOME_SHORTCUTS_UPGRADE_TEXT,
                    SearchConfigConstants.DEFAULT_SINGLE);

            if (upgradeTextResult.isFound()) {
                logInfo("upgradetext.png found in Step 4. Clicking it.");

                // OCR Logic to get time
                long minutesToWait = 60; // Default to 60 minutes if OCR fails
                try {
                    emuManager.captureScreenshotViaADB(EMULATOR_NUMBER); // Capture fresh screenshot
                    DTOTesseractSettings[] settingsToTry = {
                            WHITE_SETTINGS,
                            WHITE_NUMBERS,
                            RED_SETTINGS,
                            ORANGE_SETTINGS,
                    };

                    DTOArea timeArea = new DTOArea(new DTOPoint(480, 1045), new DTOPoint(596, 1070));
                    boolean timeFound = false;

                    for (DTOTesseractSettings settings : settingsToTry) {
                        String ocrText = emuManager.ocrRegionText(
                                EMULATOR_NUMBER,
                                timeArea.topLeft(),
                                timeArea.bottomRight(),
                                settings).trim();

                        logDebug("OCR result with settings " + settings.getClass().getSimpleName() + ": '" + ocrText
                                + "'");

                        if (ocrText.matches(".*(\\d+d\\s*)?\\d{6}.*")) {
                            // Clean up the text to extract time
                            String cleanedTime = ocrText.replaceAll("[^0-9d]", "").trim();
                            if (!cleanedTime.isEmpty()) {
                                minutesToWait = parseTimeToMinutes(cleanedTime);
                                timeFound = true;
                                logInfo("OCR Success. Time found: " + cleanedTime + " (" + minutesToWait + " minutes)");
                                break;
                            }
                        }
                    }
                    if (!timeFound) {
                        logWarning("Could not parse time from OCR. Using default 60 minutes.");
                    }

                } catch (Exception e) {
                    logError("Error during OCR time extraction: " + e.getMessage());
                }

                tapPoint(upgradeTextResult.getPoint());
                sleepTask(1000);

                tapPoint(new DTOPoint(362, 581));

                // Smart Rescheduling Logic
                LocalDateTime rescheduleTime;
                if (minutesToWait > 30) {
                    long halfTime = minutesToWait / 2;
                    rescheduleTime = LocalDateTime.now().plusMinutes(halfTime);
                    logInfo("Wait time exceeds 30 minutes (" + minutesToWait + " min). Rescheduling for half time: " +
                            halfTime + " minutes from now");
                } else if (minutesToWait < 5) {
                    rescheduleTime = LocalDateTime.now().plusMinutes(minutesToWait);
                    logInfo("Wait time is less than 5 minutes. Keeping normal schedule: " +
                            minutesToWait + " minutes from now");
                } else {
                    rescheduleTime = LocalDateTime.now().plusMinutes(minutesToWait);
                    logInfo("Wait time is " + minutesToWait + " minutes. Using normal schedule");
                }

                this.retryCount = 0; // Reset retry count on success
                this.reschedule(rescheduleTime);
                return;
            } else {
                // Inner Loop: Max 3 times trying to find obtain.png
                for (int m = 0; m < 3; m++) {
                    logInfo("Obtain loop iteration: " + (m + 1));
                    DTOImageSearchResult obtainResult = templateSearchHelper.searchTemplate(
                            GAME_HOME_SHORTCUTS_OBTAIN,
                            SearchConfigConstants.DEFAULT_SINGLE);

                    if (obtainResult.isFound()) {
                        logInfo("obtain.png found. Executing obtain sequence.");
                        tapPoint(obtainResult.getPoint());
                        sleepTask(200);
                        tapPoint(new DTOPoint(362, 1138));
                        sleepTask(200);
                        tapPoint(new DTOPoint(519, 1040));
                        sleepTask(500);
                    } else {
                        break;
                    }
                }
            }
        }

        // If we reach here, no actions were taken (fall through)
        handleTaskFailure();
    }

    /**
     * Analyzes all building queues and returns their states
     *
     * @return List of queue analysis results
     */
    private List<QueueAnalysisResult> analyzeAllQueues() {
        List<QueueAnalysisResult> results = new ArrayList<>();

        try {
            // Capture screenshot once for all OCR operations
            emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);

            int queueIndex = 1;
            for (DTOArea queueArea : queues) {
                logInfo("Analyzing queue " + queueIndex);

                // Analyze queue state
                QueueState state = analyzeQueueState(queueArea);

                // Store result
                QueueAnalysisResult result = new QueueAnalysisResult(
                        queueIndex, queueArea, state);
                results.add(result);

                // Log queue state
                logQueueState(queueIndex, state);

                queueIndex++;
            }

            // Check if any queue has UNKNOWN status and retry those
            List<QueueAnalysisResult> unknownResults = results.stream()
                    .filter(result -> result.state.status == QueueStatus.UNKNOWN)
                    .collect(Collectors.toList());

            if (!unknownResults.isEmpty()) {
                logInfo("Found " + unknownResults.size()
                        + " queue(s) with UNKNOWN status. Retrying with a new screenshot.");

                // Capture a new screenshot
                emuManager.captureScreenshotViaADB(EMULATOR_NUMBER);

                // Create new results list to replace the old one
                List<QueueAnalysisResult> updatedResults = new ArrayList<>();

                // Process each queue result, reanalyzing the unknown ones
                for (QueueAnalysisResult originalResult : results) {
                    if (originalResult.state.status == QueueStatus.UNKNOWN) {
                        // Reanalyze this queue
                        logInfo("Retrying analysis for queue " + originalResult.queueNumber);
                        QueueState newState = analyzeQueueState(originalResult.queueArea);

                        // Create new result with updated state
                        QueueAnalysisResult newResult = new QueueAnalysisResult(
                                originalResult.queueNumber, originalResult.queueArea, newState);

                        // Add to new results list
                        updatedResults.add(newResult);

                        // Log the updated state
                        logInfo("Queue " + originalResult.queueNumber + " reanalyzed. New state: " + newState.status);
                        logQueueState(originalResult.queueNumber, newState);
                    } else {
                        // Keep original result for non-UNKNOWN queues
                        updatedResults.add(originalResult);
                    }
                }

                // Replace original results with updated ones
                results = updatedResults;
            }
        } catch (Exception e) {
            logError("Error analyzing construction queues: " + e.getMessage());
        }

        return results;
    }

    /**
     * Analyzes the state of a queue using OCR with different text color
     * configurations
     *
     * @param queueArea The area to analyze
     * @return QueueState representing the queue's current status
     */
    private QueueState analyzeQueueState(DTOArea queueArea) {
        try {
            // Try all OCR configurations in order
            DTOTesseractSettings[] settingsToTry = {
                    WHITE_SETTINGS,
                    WHITE_NUMBERS,
                    RED_SETTINGS,
                    ORANGE_SETTINGS,
            };

            for (DTOTesseractSettings settings : settingsToTry) {
                String ocrText = emuManager.ocrRegionText(
                        EMULATOR_NUMBER,
                        queueArea.topLeft(),
                        queueArea.bottomRight(),
                        settings).trim();

                logDebug("OCR result with settings " + settings.getClass().getSimpleName() + ": '" + ocrText + "'");

                // Check for "Idle" state (case-insensitive)
                if (ocrText.toLowerCase().contains("idle")) {

                    if (settings == ORANGE_SETTINGS) {
                        logDebug("Orange 'idle' text detected - IDLE_TEMP");
                        return new QueueState(QueueStatus.IDLE_TEMP, null);
                    } else {
                        return new QueueState(QueueStatus.IDLE, null);
                    }
                }

                // Check for "Purchase Queue" state (case insensitive)
                if (ocrText.toLowerCase().contains("purchase") ||
                        ocrText.toLowerCase().contains("queue")) {
                    return new QueueState(QueueStatus.NOT_PURCHASED, null);
                }

                // Check for time duration format: xxd hhmmss or hhmmss (without colons)
                if (ocrText.matches(".*(\\d+d\\s*)?\\d{6}.*")) {
                    // Clean up the text to extract time
                    String cleanedTime = ocrText.replaceAll("[^0-9d]", "").trim();
                    if (!cleanedTime.isEmpty()) {
                        return new QueueState(QueueStatus.BUSY, cleanedTime);
                    }
                }
            }

            // If no configuration detected a valid state
            return new QueueState(QueueStatus.UNKNOWN, null);

        } catch (Exception e) {
            logError("Error during OCR analysis: " + e.getMessage());
            return new QueueState(QueueStatus.UNKNOWN, null);
        }
    }

    /**
     * Logs the state of a queue
     *
     * @param queueIndex The queue number
     * @param state      The queue state
     */
    private void logQueueState(int queueIndex, QueueState state) {
        switch (state.status) {
            case IDLE:
                logInfo("Queue " + queueIndex + " is IDLE - available for use");
                break;
            case BUSY:
                logInfo("Queue " + queueIndex + " is BUSY - Time remaining: " + state.timeRemaining);
                break;
            case NOT_PURCHASED:
                logInfo("Queue " + queueIndex + " is NOT PURCHASED - needs to be acquired");
                break;
            case IDLE_TEMP:
                logInfo("Queue " + queueIndex + " is IDLE_TEMP - detected by orange color");
                break;
            case UNKNOWN:
                logWarning("Queue " + queueIndex + " state is UNKNOWN - OCR failed to detect state");
                break;
        }
    }

    /**
     * Logs a summary of all analyzed queues
     *
     * @param queueResults List of queue analysis results
     */
    private void logQueueSummary(List<QueueAnalysisResult> queueResults) {
        logInfo("=== Queue Analysis Summary ===");
        for (QueueAnalysisResult result : queueResults) {
            logInfo(result.toString());
        }
    }

    /**
     * Reschedules the task based on the shortest busy queue time
     *
     * @param queueResults List of queue analysis results
     */
    private void rescheduleBasedOnBusyQueues(List<QueueAnalysisResult> queueResults) {
        logInfo("No IDLE queues available. Checking BUSY queues to reschedule...");
        this.retryCount = 0; // Reset retry count since we successfully analyzed queues

        // Filter only BUSY queues and find the one with minimum time
        QueueAnalysisResult shortestBusyQueue = queueResults.stream()
                .filter(result -> result.state.status == QueueStatus.BUSY && result.state.timeRemaining != null)
                .min((q1, q2) -> {
                    long time1 = parseTimeToMinutes(q1.state.timeRemaining);
                    long time2 = parseTimeToMinutes(q2.state.timeRemaining);
                    return Long.compare(time1, time2);
                })
                .orElse(null);

        if (shortestBusyQueue != null) {
            long minutesToWait = parseTimeToMinutes(shortestBusyQueue.state.timeRemaining);
            LocalDateTime rescheduleTime;

            if (minutesToWait > 30) {

                long halfTime = minutesToWait / 2;
                rescheduleTime = LocalDateTime.now().plusMinutes(halfTime);
                logInfo("Wait time exceeds 30 minutes (" + minutesToWait + " min). Rescheduling for half time: " +
                        halfTime + " minutes from now");
            } else if (minutesToWait < 5) {
                rescheduleTime = LocalDateTime.now().plusMinutes(minutesToWait);
                logInfo("Wait time is less than 5 minutes. Keeping normal schedule: " +
                        minutesToWait + " minutes from now");
            } else {
                rescheduleTime = LocalDateTime.now().plusMinutes(minutesToWait);
                logInfo("Wait time is " + minutesToWait + " minutes. Using normal schedule");
            }

            logInfo("Shortest busy queue: Queue " + shortestBusyQueue.queueNumber +
                    " with " + shortestBusyQueue.state.timeRemaining + " remaining");
            logInfo("Rescheduling task for: " + rescheduleTime);

            this.reschedule(rescheduleTime);
        } else {
            // No busy queues with time info, reschedule for default time
            LocalDateTime rescheduleTime = LocalDateTime.now().plusHours(1);
            logWarning("No BUSY queues with time information found. Rescheduling for 1 hour: " + rescheduleTime);
            this.reschedule(rescheduleTime);
        }
    }

    /**
     * Enum representing the possible states of a construction queue
     */
    private enum QueueStatus {
        IDLE, // Queue is available
        BUSY, // Queue is currently upgrading something
        NOT_PURCHASED, // Queue needs to be purchased
        IDLE_TEMP, // Queue is available but is temp queue
        UNKNOWN // Could not determine state
    }

    /**
     * Class to hold queue state information
     */
    private record QueueState(QueueStatus status, String timeRemaining) {
    }

    /**
     * Class to hold queue analysis results including queue number and state
     */
    private record QueueAnalysisResult(int queueNumber, DTOArea queueArea, QueueState state) {

        @Override
        public @NotNull String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Queue ").append(queueNumber).append(": ");
            sb.append(state.status);
            if (state.timeRemaining != null) {
                sb.append(" (").append(state.timeRemaining).append(")");
            }
            return sb.toString();
        }
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.HOME;
    }

    /**
     * Parses time string to total minutes
     *
     * @param timeString Time string in various formats
     * @return Total minutes
     */
    private long parseTimeToMinutes(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            return 0;
        }

        try {
            long totalMinutes = 0;
            String timePart = timeString.trim();

            // Extract days if present
            if (timePart.toLowerCase().contains("d")) {
                String[] daysPart = timePart.toLowerCase().split("d");
                if (daysPart.length > 0) {
                    String daysStr = daysPart[0].replaceAll("[^0-9]", "");
                    if (!daysStr.isEmpty()) {
                        int days = Integer.parseInt(daysStr);
                        totalMinutes += (long) days * 24 * 60; // Convert days to minutes
                    }
                }
                // Get the time part after 'd'
                if (daysPart.length > 1) {
                    timePart = daysPart[1].trim();
                } else {
                    return totalMinutes;
                }
            }

            // Clean the time part - remove any non-digit and non-colon characters
            timePart = timePart.replaceAll("[^0-9:]", "");

            if (timePart.isEmpty()) {
                return totalMinutes;
            }

            // Check if format has colons (hh:mm:ss) or not (hhmmss)
            if (timePart.contains(":")) {
                // Parse hh:mm:ss format
                String[] timeParts = timePart.split(":");
                if (timeParts.length >= 2) {
                    // Hours
                    if (!timeParts[0].isEmpty()) {
                        int hours = Integer.parseInt(timeParts[0]);
                        totalMinutes += hours * 60L;
                    }

                    // Minutes
                    if (!timeParts[1].isEmpty()) {
                        int minutes = Integer.parseInt(timeParts[1]);
                        totalMinutes += minutes;
                    }
                }
            } else {
                // Parse hhmmss format (6 digits)
                if (timePart.length() >= 4) {
                    // Extract hours (first 2 digits)
                    String hoursStr = timePart.substring(0, 2);
                    int hours = Integer.parseInt(hoursStr);
                    totalMinutes += hours * 60L;

                    // Extract minutes (next 2 digits)
                    String minutesStr = timePart.substring(2, 4);
                    int minutes = Integer.parseInt(minutesStr);
                    totalMinutes += minutes;
                }
            }

            return totalMinutes;

        } catch (Exception e) {
            logError("Error parsing time string '" + timeString + "': " + e.getMessage());
            return 15; // Default to 1 hour if parsing fails
        }
    }

    /**
     * Handles task failure by retrying a few times before backing off.
     * Prevents infinite loops when UI elements are not found.
     */
    private void handleTaskFailure() {
        retryCount++;
        if (retryCount < 3) {
            logWarning("Task iteration failed to find expected elements. Retrying (" + retryCount + "/3).");
            // Retry quickly (10 seconds)
            this.reschedule(LocalDateTime.now().plusSeconds(10));
        } else {
            logError("Task failed 3 consecutive times. Rescheduling for 30 minutes to avoid loop.");
            retryCount = 0;
            this.reschedule(LocalDateTime.now().plusMinutes(30));
        }
    }
}
