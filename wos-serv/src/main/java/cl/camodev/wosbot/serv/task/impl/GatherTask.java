package cl.camodev.wosbot.serv.task.impl;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import cl.camodev.utiles.UtilTime;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.utiles.time.TimeConverters;
import cl.camodev.utiles.time.TimeValidators;
import cl.camodev.wosbot.almac.entity.DailyTask;
import cl.camodev.wosbot.almac.repo.DailyTaskRepository;
import cl.camodev.wosbot.almac.repo.IDailyTaskRepository;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOArea;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.EnumStartLocation;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper.SearchConfig;

/**
 * Optimized GatherTask: Manages persistent resource rotation, fairness, and
 * efficient queue utilization.
 */
public class GatherTask extends DelayedTask {

    // ========== Constants & Config Keys ==========
    private static final int DEFAULT_QUEUES = 6;
    private static final int DEFAULT_LEVEL = 5;
    private static final boolean DEFAULT_REMOVE_HEROES = false;
    private static final boolean DEFAULT_INTEL_SMART = false;

    // Region Constants (UI)
    private static final MarchQueueRegion[] MARCH_QUEUES = {
            new MarchQueueRegion(new DTOPoint(10, 342), new DTOPoint(435, 407), new DTOPoint(152, 378)),
            new MarchQueueRegion(new DTOPoint(10, 415), new DTOPoint(435, 480), new DTOPoint(152, 451)),
            new MarchQueueRegion(new DTOPoint(10, 488), new DTOPoint(435, 553), new DTOPoint(152, 524)),
            new MarchQueueRegion(new DTOPoint(10, 561), new DTOPoint(435, 626), new DTOPoint(152, 597)),
            new MarchQueueRegion(new DTOPoint(10, 634), new DTOPoint(435, 699), new DTOPoint(152, 670)),
            new MarchQueueRegion(new DTOPoint(10, 707), new DTOPoint(435, 772), new DTOPoint(152, 743)),
    };
    private static final int TIME_TEXT_WIDTH = 140;
    private static final int TIME_TEXT_HEIGHT = 19;

    // Coordinate Constants (UI)
    private static final DTOPoint SEARCH_BTN_TL = new DTOPoint(25, 850);
    private static final DTOPoint SEARCH_BTN_BR = new DTOPoint(67, 898);
    private static final DTOPoint RES_TAB_SWIPE_START = new DTOPoint(678, 913);
    private static final DTOPoint RES_TAB_SWIPE_END = new DTOPoint(40, 913);
    private static final DTOPoint LEVEL_DISPLAY_TL = new DTOPoint(78, 991);
    private static final DTOPoint LEVEL_DISPLAY_BR = new DTOPoint(474, 1028);
    private static final DTOPoint LEVEL_SLIDER_START = new DTOPoint(435, 1052);
    private static final DTOPoint LEVEL_SLIDER_END = new DTOPoint(40, 1052);
    private static final DTOPoint LEVEL_INC_TL = new DTOPoint(470, 1040);
    private static final DTOPoint LEVEL_INC_BR = new DTOPoint(500, 1066);
    private static final DTOPoint LEVEL_DEC_TL = new DTOPoint(50, 1040);
    private static final DTOPoint LEVEL_DEC_BR = new DTOPoint(85, 1066);
    private static final DTOPoint LEVEL_LOCK_BTN = new DTOPoint(183, 1140);
    private static final DTOPoint SEARCH_EXEC_TL = new DTOPoint(301, 1200);
    private static final DTOPoint SEARCH_EXEC_BR = new DTOPoint(412, 1229);

    private final IDailyTaskRepository dailyTaskRepository = DailyTaskRepository.getRepository();

    // ========== State & Configuration ==========
    private int activeQueues;
    private boolean removeHeroes;
    private boolean intelSmart;
    private boolean intelRecall;
    private boolean intelEnabled;
    private boolean gatherSpeed;

    private List<GatherType> enabledTypes;
    private List<GatherType> rotationPool;
    private LocalDateTime earliestReschedule;
    private TextRecognitionRetrier<LocalDateTime> textHelper;

    public GatherTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    // ================= EXECUTE =================

    @Override
    protected void execute() {
        loadConfig();

        if (enabledTypes.isEmpty()) {
            logInfo("No gather types enabled. Disabling task.");
            setRecurring(false);
            return;
        }

        if (checkIntelConflict())
            return;
        if (checkGatherSpeedWait())
            return;

        // 1. Scan Active Marches
        List<GatherType> activeMarches = scanActiveMarches();
        int activeCount = activeMarches.size();
        logInfo(String.format("Active Marches: %d / %d", activeCount, activeQueues));

        // 2. Fill Queues (Persistent Rotation)
        fillQueues(activeCount, activeMarches);

        // 3. Save & Finalize
        finalizeReschedule();
    }

    // ================= CONFIGURATION =================

    private void loadConfig() {
        this.activeQueues = get(EnumConfigurationKey.GATHER_ACTIVE_MARCH_QUEUE_INT, DEFAULT_QUEUES);
        this.removeHeroes = get(EnumConfigurationKey.GATHER_REMOVE_HEROS_BOOL, DEFAULT_REMOVE_HEROES);
        this.intelSmart = get(EnumConfigurationKey.INTEL_SMART_PROCESSING_BOOL, DEFAULT_INTEL_SMART);
        this.intelRecall = get(EnumConfigurationKey.INTEL_RECALL_GATHER_TROOPS_BOOL, false);
        this.intelEnabled = get(EnumConfigurationKey.INTEL_BOOL, false);
        this.gatherSpeed = get(EnumConfigurationKey.GATHER_SPEED_BOOL, false);

        this.enabledTypes = Arrays.stream(GatherType.values())
                .filter(this::isTypeEnabled)
                .collect(Collectors.toList());

        loadRotationPool();
        if (rotationPool != null) {
            rotationPool.retainAll(enabledTypes);
            saveRotationPool(); // Ensure consistent state
        }

        this.textHelper = new TextRecognitionRetrier<>(provider);
        this.earliestReschedule = null;
    }

    private boolean isTypeEnabled(GatherType type) {
        return get(type.enabledKey, false);
    }

    @SuppressWarnings("unchecked")
    private <T> T get(EnumConfigurationKey key, T defaultValue) {
        T val = profile.getConfig(key, (Class<T>) defaultValue.getClass());
        return val != null ? val : defaultValue;
    }

    // ================= ROTATION LOGIC =================

    private void fillQueues(int currentActive, List<GatherType> activeMarches) {
        int freeSlots = activeQueues - currentActive;
        logInfo(String.format("Free slots: %d. Pool: %s", freeSlots, rotationPool));

        // Remove types already marching from the current pool for initial fairness
        if (rotationPool.removeAll(activeMarches)) {
            logInfo("Removed active marches from pool: " + activeMarches);
            saveRotationPool();
        }

        // If pool is empty after removing active marches but there are free slots,
        // allow duplicate types so we can fill all available march queues
        if (rotationPool.isEmpty() && freeSlots > 0) {
            logInfo("Pool empty after removing active marches. Allowing duplicates for remaining slots.");
            rotationPool = new ArrayList<>(enabledTypes);
        }

        if (freeSlots <= 0) {
            saveRotationPool();
            return;
        }

        // Shuffle loaded pool for randomness in this run
        Collections.shuffle(rotationPool);

        int remaining = freeSlots;
        int safetyLoop = 0;

        while (remaining > 0 && safetyLoop++ < 10) {

            // Refill if empty
            if (rotationPool.isEmpty()) {
                logInfo("Pool empty. Resetting.");
                rotationPool = new ArrayList<>(enabledTypes);
                // Don't remove active marches on refill — duplicates are needed
                // to fill remaining slots when activeQueues > enabledTypes.size()
                Collections.shuffle(rotationPool);
            }

            // Try ALL pool items — don't limit to remaining, so if one type fails
            // we still try others. The inner loop stops when slots are full.
            List<GatherType> batch = new ArrayList<>(rotationPool);

            if (batch.isEmpty())
                break;

            boolean progress = false;
            for (GatherType type : batch) {
                if (remaining <= 0 || currentActive >= activeQueues)
                    break;

                if (deploy(type)) {
                    currentActive++;
                    remaining--;
                    rotationPool.remove(type);
                    progress = true;
                    logInfo(String.format("Deployed %s. Removed from pool.", type));
                    activeMarches.add(type); // Add to avoid re-picking if we loop
                } else {
                    // Remove failed type from pool to avoid retrying it endlessly
                    rotationPool.remove(type);
                    logInfo(String.format("Failed to deploy %s. Skipping.", type));
                }
            }

            if (!progress || currentActive >= activeQueues)
                break;
        }

        saveRotationPool();
    }

    private void loadRotationPool() {
        String saved = profile.getConfig(EnumConfigurationKey.GATHER_ROTATION_POOL, String.class);
        logInfo("DEBUG: Loaded pool config: '" + saved + "'");
        if (saved == null || saved.isEmpty()) {
            rotationPool = new ArrayList<>(enabledTypes);
            logInfo("DEBUG: Pool config empty/null. Resetting to full: " + rotationPool);
            return;
        }
        try {
            rotationPool = Arrays.stream(saved.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(GatherType::valueOf)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            rotationPool = new ArrayList<>(enabledTypes);
            logInfo("DEBUG: Error parsing pool. Resetting: " + e.getMessage());
        }
    }

    private void saveRotationPool() {
        if (rotationPool == null)
            return;
        String val = rotationPool.stream().map(Enum::name).collect(Collectors.joining(","));
        logInfo("DEBUG: Saving pool config: '" + val + "'");
        profile.setConfig(EnumConfigurationKey.GATHER_ROTATION_POOL, val);
        setShouldUpdateConfig(true);
    }

    // ================= SCAN & CHECKS =================

    private List<GatherType> scanActiveMarches() {
        List<GatherType> active = new ArrayList<>();
        marchHelper.openLeftMenuCitySection(false);

        // Fix: Scan ALL types (even disabled ones) to correctly count occupied slots
        for (GatherType type : GatherType.values()) {
            List<ActiveMarchResult> results = checkActiveMarches(type);
            for (ActiveMarchResult result : results) {
                if (result.isActive()) {
                    active.add(type);
                    if (result.getReturnTime() != null) {
                        updateReschedule(result.getReturnTime());
                        logInfo(String.format("%s ACTIVE. Return: %s", type,
                                UtilTime.localDateTimeToDDHHMMSS(result.getReturnTime())));
                    } else {
                        logInfo(String.format("%s ACTIVE. Return time unknown (error reading)", type));
                    }
                }
            }
        }

        marchHelper.closeLeftMenu();
        return active;
    }

    private List<ActiveMarchResult> checkActiveMarches(GatherType type) {
        DTOPoint limit = new DTOPoint(415,
                MARCH_QUEUES[Math.min(activeQueues - 1, MARCH_QUEUES.length - 1)].bottomRight.getY());

        // Fix: Use searchTemplates (plural) to find ALL matches of this type
        List<DTOImageSearchResult> results = templateSearchHelper.searchTemplates(
                type.template,
                SearchConfig.builder()
                        .withArea(new DTOArea(MARCH_QUEUES[0].topLeft, limit))
                        .withMaxAttempts(3)
                        .withDelay(3).build());

        List<ActiveMarchResult> marchResults = new ArrayList<>();

        if (results.isEmpty()) {
            return marchResults; // Empty list = no marches of this type
        }

        for (DTOImageSearchResult res : results) {
            int qIdx = findQueueIndex(res.getPoint());
            if (qIdx != -1) {
                LocalDateTime time = readReturnTime(qIdx);
                // If time read fails, we still count it as active with a fallback time
                LocalDateTime returnTime = (time != null) ? time.plusMinutes(2) : LocalDateTime.now().plusMinutes(5);
                marchResults.add(ActiveMarchResult.active(returnTime));
            } else {
                // Found the icon but couldn't map to a queue... treat as active anyway to be
                // safe?
                // For now, if we can't map it to a queue line, we might ignore it or treat as
                // error.
                // Safer to count it as active with default time to avoid over-deploying
                marchResults.add(ActiveMarchResult.error());
            }
        }

        return marchResults;
    }

    // ================= DEPLOYMENT PIPELINE =================

    private boolean deploy(GatherType type) {
        logInfo("Deploying " + type);

        if (!openSearchMenu())
            return retryLater();
        if (!selectTile(type))
            return retryLater();

        int level = get(type.levelKey, DEFAULT_LEVEL);
        if (!setLevel(level))
            return retryLater();

        if (!executeSearch())
            return retryLater();
        if (!deployMarchAction(type))
            return retryLater();

        return true;
    }

    private boolean openSearchMenu() {
        tapRandomPoint(SEARCH_BTN_TL, SEARCH_BTN_BR);
        sleepTask(2000);
        swipe(RES_TAB_SWIPE_START, RES_TAB_SWIPE_END);
        sleepTask(500);
        return true;
    }

    private boolean selectTile(GatherType type) {
        for (int i = 0; i < 4; i++) {
            DTOImageSearchResult tile = templateSearchHelper.searchTemplate(type.tile, SearchConfig.builder().build());
            if (tile.isFound()) {
                tapPoint(tile.getPoint());
                sleepTask(500);
                return true;
            }
            if (i < 3) {
                swipe(RES_TAB_SWIPE_START, RES_TAB_SWIPE_END);
                sleepTask(500);
            }
        }
        return false;
    }

    private boolean setLevel(int target) {
        Integer current = readLevel();
        if (current != null && current == target)
            return true;

        if (current == null) {
            resetLevelToOne();
            if (target > 1)
                tapRandomPoint(LEVEL_INC_TL, LEVEL_INC_BR, target - 1, 150);
        } else {
            if (current < target)
                tapRandomPoint(LEVEL_INC_TL, LEVEL_INC_BR, target - current, 150);
            else
                tapRandomPoint(LEVEL_DEC_TL, LEVEL_DEC_BR, current - target, 150);
        }
        ensureLevelLocked();
        return true;
    }

    private boolean executeSearch() {
        tapRandomPoint(SEARCH_EXEC_TL, SEARCH_EXEC_BR);
        sleepTask(3000);
        return true;
    }

    private boolean deployMarchAction(GatherType type) {
        DTOImageSearchResult btn = templateSearchHelper.searchTemplate(EnumTemplates.GAME_HOME_SHORTCUTS_FARM_GATHER,
                SearchConfig.builder().build());
        if (!btn.isFound())
            return false;

        tapPoint(btn.getPoint());
        sleepTask(1000);

        DTOImageSearchResult hero = templateSearchHelper.searchTemplate(type.preferredHero,
                SearchConfig.builder().withCoordinates(new DTOPoint(51, 231), new DTOPoint(295, 649)).build());

        if (!hero.isFound()) {
            logInfo("Preferred hero not found for " + type + ". Proceeding with default march.");
        }

        if (removeHeroes)
            removeDefaultHeroes();

        DTOImageSearchResult deploy = templateSearchHelper.searchTemplate(EnumTemplates.GATHER_DEPLOY_BUTTON,
                SearchConfig.builder().build());
        if (!deploy.isFound())
            return false;

        tapPoint(deploy.getPoint());
        sleepTask(1000);

        if (templateSearchHelper.searchTemplate(EnumTemplates.TROOPS_ALREADY_MARCHING, SearchConfig.builder().build())
                .isFound()) {
            tapBackButton();
            tapBackButton();
            return false;
        }
        return true;
    }

    // ================= HELPERS (UI/OCR) =================

    private int findQueueIndex(DTOPoint p) {
        int max = Math.min(activeQueues, MARCH_QUEUES.length);
        for (int i = 0; i < max; i++) {
            MarchQueueRegion r = MARCH_QUEUES[i];
            if (p.getX() >= r.topLeft.getX() && p.getX() <= r.bottomRight.getX() &&
                    p.getY() >= r.topLeft.getY() && p.getY() <= r.bottomRight.getY())
                return i;
        }
        return -1;
    }

    private LocalDateTime readReturnTime(int idx) {
        MarchQueueRegion r = MARCH_QUEUES[idx];
        DTOTesseractSettings s = DTOTesseractSettings.builder()
                .setPageSegMode(DTOTesseractSettings.PageSegMode.SINGLE_LINE)
                .setOcrEngineMode(DTOTesseractSettings.OcrEngineMode.LSTM)
                .setRemoveBackground(true)
                .setTextColor(new Color(255, 255, 255))
                .setAllowedChars("0123456789:").build();

        return textHelper.execute(r.timeTextStart,
                new DTOPoint(r.timeTextStart.getX() + TIME_TEXT_WIDTH, r.timeTextStart.getY() + TIME_TEXT_HEIGHT),
                3, 200L, s, TimeValidators::isValidTime, TimeConverters::toLocalDateTime);
    }

    private Integer readLevel() {
        DTOTesseractSettings s = DTOTesseractSettings.builder().setAllowedChars("0123456789")
                .setRemoveBackground(true).setTextColor(new Color(255, 255, 255)).build();
        return readNumberValue(LEVEL_DISPLAY_TL, LEVEL_DISPLAY_BR, s);
    }

    private void removeDefaultHeroes() {
        List<DTOImageSearchResult> btns = templateSearchHelper.searchTemplates(
                EnumTemplates.RALLY_REMOVE_HERO_BUTTON,
                SearchConfig.builder().withThreshold(90).withMaxResults(3).build());

        if (btns.isEmpty())
            return;
        btns.sort(Comparator.comparingInt(r -> r.getPoint().getX()));

        for (int i = 1; i < btns.size(); i++) {
            tapPoint(btns.get(i).getPoint());
            sleepTask(300);
        }
    }

    private void resetLevelToOne() {
        swipe(LEVEL_SLIDER_START, LEVEL_SLIDER_END);
        sleepTask(300);
    }

    private void ensureLevelLocked() {
        if (!templateSearchHelper
                .searchTemplate(EnumTemplates.GAME_HOME_SHORTCUTS_FARM_TICK, SearchConfig.builder().build())
                .isFound()) {
            tapPoint(LEVEL_LOCK_BTN);
            sleepTask(300);
        }
    }

    private boolean retryLater() {
        updateReschedule(LocalDateTime.now().plusMinutes(5));
        tapBackButton(); // Safety back
        return false;
    }

    // ================= SCHEDULING & CONFLICTS =================

    private void updateReschedule(LocalDateTime t) {
        if (earliestReschedule == null || t.isBefore(earliestReschedule))
            earliestReschedule = t;
    }

    private void finalizeReschedule() {
        reschedule(earliestReschedule != null ? earliestReschedule : LocalDateTime.now().plusMinutes(5));
    }

    private boolean checkIntelConflict() {
        if ((!intelSmart && !intelRecall) || !intelEnabled)
            return false;
        try {
            DailyTask t = dailyTaskRepository.findByProfileIdAndTaskName(profile.getId(), TpDailyTaskEnum.INTEL);
            if (t != null && ChronoUnit.MINUTES.between(LocalDateTime.now(), t.getNextSchedule()) < 5) {
                reschedule(LocalDateTime.now().plusMinutes(35));
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    private boolean checkGatherSpeedWait() {
        if (!gatherSpeed)
            return false;
        try {
            DailyTask t = dailyTaskRepository.findByProfileIdAndTaskName(profile.getId(), TpDailyTaskEnum.GATHER_BOOST);
            if (t == null)
                return false;
            long m = ChronoUnit.MINUTES.between(LocalDateTime.now(), t.getNextSchedule());
            if (m > 0 && m < 5) {
                reschedule(LocalDateTime.now().plusMinutes(2));
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    @Override
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.WORLD;
    }

    @Override
    public boolean provideDailyMissionProgress() {
        return true;
    }

    // ================= INNER CLASSES =================

    public enum GatherType {
        MEAT(EnumTemplates.GAME_HOME_SHORTCUTS_MEAT, EnumTemplates.GAME_HOME_SHORTCUTS_FARM_MEAT,
                EnumTemplates.GATHER_MEAT_HERO,
                EnumConfigurationKey.GATHER_MEAT_BOOL, EnumConfigurationKey.GATHER_MEAT_LEVEL_INT),
        WOOD(EnumTemplates.GAME_HOME_SHORTCUTS_WOOD, EnumTemplates.GAME_HOME_SHORTCUTS_FARM_WOOD,
                EnumTemplates.GATHER_WOOD_HERO,
                EnumConfigurationKey.GATHER_WOOD_BOOL, EnumConfigurationKey.GATHER_WOOD_LEVEL_INT),
        COAL(EnumTemplates.GAME_HOME_SHORTCUTS_COAL, EnumTemplates.GAME_HOME_SHORTCUTS_FARM_COAL,
                EnumTemplates.GATHER_COAL_HERO,
                EnumConfigurationKey.GATHER_COAL_BOOL, EnumConfigurationKey.GATHER_COAL_LEVEL_INT),
        IRON(EnumTemplates.GAME_HOME_SHORTCUTS_IRON, EnumTemplates.GAME_HOME_SHORTCUTS_FARM_IRON,
                EnumTemplates.GATHER_IRON_HERO,
                EnumConfigurationKey.GATHER_IRON_BOOL, EnumConfigurationKey.GATHER_IRON_LEVEL_INT);

        final EnumTemplates template, tile, preferredHero;
        final EnumConfigurationKey enabledKey, levelKey;

        GatherType(EnumTemplates template, EnumTemplates tile, EnumTemplates preferredHero,
                EnumConfigurationKey enabledKey, EnumConfigurationKey levelKey) {
            this.template = template;
            this.tile = tile;
            this.preferredHero = preferredHero;
            this.enabledKey = enabledKey;
            this.levelKey = levelKey;
        }
    }

    private static class MarchQueueRegion {
        final DTOPoint topLeft, bottomRight, timeTextStart;

        MarchQueueRegion(DTOPoint topLeft, DTOPoint bottomRight, DTOPoint timeTextStart) {
            this.topLeft = topLeft;
            this.bottomRight = bottomRight;
            this.timeTextStart = timeTextStart;
        }
    }

    private static class ActiveMarchResult {
        final boolean active;
        final LocalDateTime returnTime;

        private ActiveMarchResult(boolean active, LocalDateTime returnTime) {
            this.active = active;
            this.returnTime = returnTime;
        }

        static ActiveMarchResult active(LocalDateTime t) {
            return new ActiveMarchResult(true, t);
        }

        static ActiveMarchResult error() {
            return new ActiveMarchResult(true, LocalDateTime.now().plusMinutes(5));
        }

        boolean isActive() {
            return active;
        }

        LocalDateTime getReturnTime() {
            return returnTime;
        }
    }
}