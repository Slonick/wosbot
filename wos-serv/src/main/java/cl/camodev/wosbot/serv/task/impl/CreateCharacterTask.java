package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.TaskQueue;
import cl.camodev.wosbot.serv.task.constants.CommonGameAreas;

/**
 * Task to create character based on state age.
 * 1. Profile -> Settings -> Character -> Create
 * 2. OCR Tips check
 * 3. OCR State Age -> Parse -> Compare -> Accept/Reschedule
 */
public class CreateCharacterTask extends DelayedTask {

    private static final int RESCHEDULE_DELAY_MINUTES = 5;

    // --- Coordinates ---
    private static final DTOPoint PROFILE_AVATAR_TL = CommonGameAreas.PROFILE_AVATAR.topLeft();
    private static final DTOPoint PROFILE_AVATAR_BR = CommonGameAreas.PROFILE_AVATAR.bottomRight();
    private static final DTOPoint SETTINGS_BUTTON_TL = new DTOPoint(582, 1197);
    private static final DTOPoint SETTINGS_BUTTON_BR = new DTOPoint(687, 1249);
    private static final DTOPoint CHARACTER_BUTTON = new DTOPoint(198, 339);
    private static final DTOPoint CREATE_CHARACTER_BUTTON = new DTOPoint(162, 312);
    private static final DTOPoint TIPS_OCR_TL = new DTOPoint(312, 411);
    private static final DTOPoint TIPS_OCR_BR = new DTOPoint(408, 469);
    private static final DTOPoint AGE_OCR_TL = new DTOPoint(88, 370);
    private static final DTOPoint AGE_OCR_BR = new DTOPoint(337, 400);
    private static final DTOPoint ACCEPT_BUTTON = new DTOPoint(340, 367);
    private static final DTOPoint CONFIRM_BUTTON = new DTOPoint(507, 780);
    private static final DTOPoint CROSS_BUTTON = new DTOPoint(631, 158);// FIXXX ITTT <----------

    // --- OCR ---
    private static final DTOTesseractSettings TIPS_OCR_SETTINGS = DTOTesseractSettings.builder()
            .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
            .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
            .setAllowedChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
            .setRemoveBackground(true)
            .build();

    private static final DTOTesseractSettings AGE_OCR_SETTINGS = DTOTesseractSettings.builder()
            .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
            .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
            .setAllowedChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789() ")
            .setRemoveBackground(true)
            .build();

    private static final Pattern HOURS_PATTERN = Pattern.compile("(\\d+)\\s*hour");
    private static final Pattern MINUTES_PATTERN = Pattern.compile("(\\d+)\\s*minute");
    private static final Pattern SECONDS_PATTERN = Pattern.compile("(\\d+)\\s*second");

    public CreateCharacterTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected void execute() {
        logInfo("Start");

        Integer maxAgeMinutes = profile.getConfig(
                EnumConfigurationKey.CREATE_CHARACTER_MAX_AGE_MINUTES_INT,
                Integer.class);

        if (maxAgeMinutes == null || maxAgeMinutes <= 0) {
            logWarning("Wait Max Age config");
            return;
        }

        logInfo("Max age: " + maxAgeMinutes);

        logInfo("Profile");
        tapRandomPoint(PROFILE_AVATAR_TL, PROFILE_AVATAR_BR);
        sleepTask(500);

        logInfo("Settings");
        tapRandomPoint(SETTINGS_BUTTON_TL, SETTINGS_BUTTON_BR);
        sleepTask(500);

        boolean Created = false;
        while(!Created) {

        logInfo("Character");
        tapPoint(CHARACTER_BUTTON);
        sleepTask(500);

        logInfo("Create");
        tapPoint(CREATE_CHARACTER_BUTTON);
        sleepTask(500);

        logInfo("Check Tips");
        String tipsText = readStringValue(TIPS_OCR_TL, TIPS_OCR_BR, TIPS_OCR_SETTINGS);

        if (tipsText != null && tipsText.toLowerCase().contains("tips")) {
            logInfo("Change Emulator");
            Created = true; // Ends the loop explicitly
            return;
        }

        logInfo("Check Age");
        sleepTask(5000);

        String ageText = readStringValue(AGE_OCR_TL, AGE_OCR_BR, AGE_OCR_SETTINGS);

        if (ageText == null || ageText.isEmpty()) {
            logWarning("OCR fail. Retry 5m");
            reschedule(LocalDateTime.now().plusMinutes(RESCHEDULE_DELAY_MINUTES));
            Created = true;
            return;
        }

        logInfo("OCR result: " + ageText);

        int ageTotalMinutes = parseAgeToMinutes(ageText);

        if (ageTotalMinutes < 0) {
            logWarning("Parse fail. Retry 5m");
            reschedule(LocalDateTime.now().plusMinutes(RESCHEDULE_DELAY_MINUTES));
            Created = true;
            return;
        }

        logInfo("Age: " + ageTotalMinutes + " min (max: " + maxAgeMinutes + ")");

        if (ageTotalMinutes <= maxAgeMinutes) {
            logInfo("Accepting...");
            tapPoint(ACCEPT_BUTTON);
            sleepTask(300);
            tapPoint(CONFIRM_BUTTON);
            sleepTask(100);
            Created = true;
            logInfo("Done");
            setRecurring(false); // Task will gracefully finish and not be rescheduled
            
            Boolean skipTutorial = profile.getConfig(
                    EnumConfigurationKey.CREATE_CHARACTER_SKIP_TUTORIAL_BOOL,
                    Boolean.class);
            TaskQueue queue = ServScheduler.getServices().getQueueManager().getQueue(profile.getId());
            if (queue != null) {
                if (Boolean.TRUE.equals(skipTutorial)) {
                    logInfo("Running Skip Tutorial Task...");
                    queue.executeTaskNow(TpDailyTaskEnum.SKIP_TUTORIAL, false);
                    
                    logInfo("Removing Create Character task...");
                    queue.removeTask(TpDailyTaskEnum.CREATE_CHARACTER);
                    
                    throw new cl.camodev.wosbot.ex.TaskPreemptedException("Switching to Skip Tutorial task immediately");
                }
                logInfo("Removing Create Character task...");
                queue.removeTask(TpDailyTaskEnum.CREATE_CHARACTER);
            }
            return;
        } else {
            logInfo("Age > max (" + ageTotalMinutes + "). Retry");
        }
        tapPoint(CROSS_BUTTON);
        sleepTask(500);
        }
    }

    /** Parses age string into total minutes. */
    private int parseAgeToMinutes(String ageText) {
        int totalMinutes = 0;
        boolean foundAny = false;

        Matcher hoursMatcher = HOURS_PATTERN.matcher(ageText);
        if (hoursMatcher.find()) {
            totalMinutes += Integer.parseInt(hoursMatcher.group(1)) * 60;
            foundAny = true;
        }

        Matcher minutesMatcher = MINUTES_PATTERN.matcher(ageText);
        if (minutesMatcher.find()) {
            totalMinutes += Integer.parseInt(minutesMatcher.group(1));
            foundAny = true;
        }

        Matcher secondsMatcher = SECONDS_PATTERN.matcher(ageText);
        if (secondsMatcher.find()) {
            foundAny = true;
        }

        return foundAny ? totalMinutes : -1;
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.ANY;
    }

    @Override
    public boolean provideDailyMissionProgress() {
        return false;
    }
}
