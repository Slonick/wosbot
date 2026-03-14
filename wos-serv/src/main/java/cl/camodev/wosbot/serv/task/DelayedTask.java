package cl.camodev.wosbot.serv.task;

import cl.camodev.utiles.number.NumberConverters;
import cl.camodev.utiles.number.NumberValidators;
import cl.camodev.utiles.ocr.TextRecognitionRetrier;
import cl.camodev.wosbot.almac.repo.ProfileRepository;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ex.HomeNotFoundException;
import cl.camodev.wosbot.logging.ProfileLogger;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTesseractSettings;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.impl.StaminaService;
import cl.camodev.wosbot.serv.ocr.BotTextRecognitionProvider;
import cl.camodev.wosbot.serv.task.constants.CommonOCRSettings;
import cl.camodev.wosbot.serv.task.helper.*;
import cl.camodev.wosbot.serv.task.impl.ArenaTask;
import cl.camodev.wosbot.serv.task.impl.BearTrapTask;
import cl.camodev.wosbot.serv.task.impl.InitializeTask;
import cl.camodev.wosbot.serv.task.impl.SkipTutorialTask;
import cl.camodev.wosbot.ex.StopExecutionException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for all game automation tasks.
 * 
 * <p>
 * This class provides the core infrastructure for scheduled task execution
 * including:
 * <ul>
 * <li>Task scheduling and priority management</li>
 * <li>Profile configuration refresh</li>
 * <li>Helper class initialization (stamina, march, navigation, etc.)</li>
 * <li>Screen location verification</li>
 * <li>Logging utilities</li>
 * <li>Basic emulator interaction (tap, swipe)</li>
 * </ul>
 * 
 * <p>
 * <b>Task Lifecycle:</b>
 * <ol>
 * <li>Task is scheduled in {@link TaskQueue}</li>
 * <li>{@link #run()} method refreshes profile and validates game state</li>
 * <li>{@link #execute()} method (implemented by subclass) performs the
 * task</li>
 * <li>Task reschedules itself or completes</li>
 * </ol>
 * 
 * <p>
 * <b>Helper Classes:</b>
 * All helper instances are initialized eagerly in the constructor and available
 * to subclasses for use in task implementations.
 * 
 * @author WoS Bot
 * @see TaskQueue
 * @see StaminaHelper
 * @see MarchHelper
 * @see NavigationHelper
 */
public abstract class DelayedTask implements Runnable, Delayed {

    // ========================================================================
    // CORE TASK FIELDS
    // ========================================================================

    protected volatile boolean recurring = true;
    protected LocalDateTime lastExecutionTime;
    protected LocalDateTime scheduledTime;
    protected String taskName;
    protected DTOProfiles profile;
    protected String EMULATOR_NUMBER;
    protected TpDailyTaskEnum tpTask;
    protected boolean shouldUpdateConfig;
    protected boolean isInjecting = false;

    // ========================================================================
    // SERVICE INSTANCES
    // ========================================================================

    protected EmulatorManager emuManager = EmulatorManager.getInstance();
    protected ServScheduler servScheduler = ServScheduler.getServices();
    protected ServLogs servLogs = ServLogs.getServices();
    private ProfileLogger logger;
    protected int currentOcrFailures = 0;

    // ========================================================================
    // HELPER INSTANCES
    // ========================================================================

    protected BotTextRecognitionProvider provider;
    protected TextRecognitionRetrier<Integer> integerHelper;
    protected TextRecognitionRetrier<Duration> durationHelper;
    protected TextRecognitionRetrier<String> stringHelper;

    protected NavigationHelper navigationHelper;
    protected TemplateSearchHelper templateSearchHelper;
    protected StaminaHelper staminaHelper;
    protected MarchHelper marchHelper;
    protected IntelScreenHelper intelScreenHelper;
    protected AllianceHelper allianceHelper;
    protected EventHelper eventHelper;

    // ========================================================================
    // TIME FORMATTERS
    // ========================================================================

    protected static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    protected static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    /**
     * Constructs a new DelayedTask with all helper instances initialized.
     * 
     * @param profile The profile this task will execute for
     * @param tpTask  The task type enum
     */
    public DelayedTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        this.profile = profile;
        this.taskName = tpTask.getName();
        this.scheduledTime = LocalDateTime.now();
        this.EMULATOR_NUMBER = profile.getEmulatorNumber();
        this.tpTask = tpTask;
        this.logger = new ProfileLogger(this.getClass(), profile);

        // Initialize OCR providers and helpers
        this.provider = new BotTextRecognitionProvider(emuManager, EMULATOR_NUMBER);
        this.integerHelper = new TextRecognitionRetrier<>(provider);
        this.durationHelper = new TextRecognitionRetrier<>(provider);
        this.stringHelper = new TextRecognitionRetrier<>(provider);

        // Initialize game helpers
        this.templateSearchHelper = new TemplateSearchHelper(emuManager, EMULATOR_NUMBER, profile);
        this.templateSearchHelper.setPreemptionCheck(this::checkPreemption); // wire preemption hook
        this.navigationHelper = new NavigationHelper(emuManager, EMULATOR_NUMBER, profile);
        this.marchHelper = new MarchHelper(emuManager, EMULATOR_NUMBER, stringHelper, profile);
        this.staminaHelper = new StaminaHelper(emuManager, EMULATOR_NUMBER, integerHelper, durationHelper, profile,
                marchHelper);
        this.intelScreenHelper = new IntelScreenHelper(emuManager, EMULATOR_NUMBER, templateSearchHelper,
                navigationHelper, profile);
        this.allianceHelper = new AllianceHelper(emuManager, EMULATOR_NUMBER, templateSearchHelper, navigationHelper,
                profile);
        this.eventHelper = new EventHelper(emuManager, EMULATOR_NUMBER, profile);
    }

    /**
     * Returns a distinct key for task identification in equals/hashCode.
     * 
     * <p>
     * Override this to provide unique identification for tasks that can have
     * multiple instances with different parameters (e.g., different targets).
     * 
     * @return A unique identifier for this task instance, or null if not needed
     */
    protected Object getDistinctKey() {
        return null;
    }

    /**
     * Specifies the required screen location before task execution.
     * 
     * <p>
     * Override this to indicate whether the task needs to start from:
     * <ul>
     * <li>{@link EnumStartLocation#HOME} - City view</li>
     * <li>{@link EnumStartLocation#WORLD} - World map view</li>
     * <li>{@link EnumStartLocation#ANY} - Either location (default)</li>
     * </ul>
     * 
     * @return The required starting screen location
     */
    protected EnumStartLocation getRequiredStartLocation() {
        return EnumStartLocation.ANY;
    }

    /**
     * Main task execution entry point.
     * 
     * <p>
     * This method:
     * <ol>
     * <li>Refreshes profile from database</li>
     * <li>Verifies game is running</li>
     * <li>Ensures correct screen location</li>
     * <li>Validates stamina if needed</li>
     * <li>Calls {@link #execute()} for task-specific logic</li>
     * <li>Saves profile if configuration changed</li>
     * <li>Returns to correct screen location</li>
     * </ol>
     */
    @Override
    public void run() {
        refreshProfileFromDatabase();
        long startTimeMs = System.currentTimeMillis();
        int initialOcrFailures = this.currentOcrFailures;
        int initialTemplateFailures = this.templateSearchHelper.getFailedSearches();

        try {
            // InitializeTask and SkipTutorialTask have special handling
            if (this instanceof InitializeTask || this instanceof SkipTutorialTask) {
                execute();
                return;
            }

            validateGameIsRunning();
            navigationHelper.ensureCorrectScreenLocation(getRequiredStartLocation());

            if (consumesStamina()) {
                validateAndUpdateStamina();
            }

            execute();

            if (shouldUpdateConfig) {
                ServProfiles.getServices().saveProfile(profile);
                shouldUpdateConfig = false;
            }

            sleepTask(2000); // Brief delay before cleanup
            navigationHelper.ensureCorrectScreenLocation(EnumStartLocation.ANY);
        } finally {
            long executionTimeMs = System.currentTimeMillis() - startTimeMs;
            int ocrFailuresDelta = this.currentOcrFailures - initialOcrFailures;
            int templateFailuresDelta = this.templateSearchHelper.getFailedSearches() - initialTemplateFailures;

            cl.camodev.wosbot.serv.impl.ServStatistics.getServices().recordTaskExecution(profile, taskName,
                    executionTimeMs, ocrFailuresDelta, templateFailuresDelta);
        }
    }

    /**
     * Task-specific execution logic.
     * 
     * <p>
     * Subclasses must implement this method with their task-specific behavior.
     * This method is called after all setup and validation is complete.
     */
    protected abstract void execute();

    /**
     * Refreshes the profile from the database to ensure current configurations.
     */
    private void refreshProfileFromDatabase() {
        try {
            if (profile != null && profile.getId() != null) {
                DTOProfiles updated = ProfileRepository.getRepository()
                        .getProfileWithConfigsById(profile.getId());
                if (updated != null) {
                    this.profile = updated;
                }
            }
        } catch (Exception e) {
            logWarning("Could not refresh profile before execution: " + e.getMessage());
        }
    }

    /**
     * Validates that the game package is currently running.
     * 
     * @throws HomeNotFoundException if game is not running
     */
    private void validateGameIsRunning() {
        if (!emuManager.isPackageRunning(EMULATOR_NUMBER, EmulatorManager.GAME.getPackageName())) {
            throw new HomeNotFoundException("Game is not running");
        }
    }

    /**
     * Validates and updates stamina if service requires refresh.
     */
    private void validateAndUpdateStamina() {
        if (StaminaService.getServices().requiresUpdate(profile.getId())) {
            staminaHelper.updateStaminaFromProfile();
        }
    }

    // ========================================================================
    // OCR CONVENIENCE METHODS
    // ========================================================================

    /**
     * Reads an integer value from a screen region using OCR.
     * 
     * @param topLeft     Top-left corner of OCR region
     * @param bottomRight Bottom-right corner of OCR region
     * @param settings    Tesseract OCR settings
     * @return Parsed integer value, or null if OCR failed
     */
    protected Integer readNumberValue(DTOPoint topLeft, DTOPoint bottomRight, DTOTesseractSettings settings) {
        Integer result = integerHelper.execute(
                topLeft,
                bottomRight,
                5, // Max retry attempts
                200L, // Delay between retries
                settings,
                text -> NumberValidators.matchesPattern(text, CommonOCRSettings.NUMBER_PATTERN),
                text -> NumberConverters.regexToInt(text, CommonOCRSettings.NUMBER_PATTERN));

        logDebug("Number value read: " + (result != null ? result : "null"));
        return result;
    }

    /**
     * Reads a string value from a screen region using OCR.
     * 
     * @param topLeft     Top-left corner of OCR region
     * @param bottomRight Bottom-right corner of OCR region
     * @param settings    Tesseract OCR settings
     * @return Parsed string value, or null if OCR failed
     */
    protected String readStringValue(DTOPoint topLeft, DTOPoint bottomRight, DTOTesseractSettings settings) {
        String result = stringHelper.execute(
                topLeft,
                bottomRight,
                5, // Max retry attempts
                200L, // Delay between retries
                settings,
                Objects::nonNull,
                text -> text);

        logDebug("String value read: " + (result != null ? result : "null"));
        return result;
    }

    // ========================================================================
    // EMULATOR INTERACTION METHODS
    // ========================================================================

    /**
     * Taps at the specified point on the emulator screen.
     * 
     * @param point The point to tap
     */
    public void tapPoint(DTOPoint point) {
        checkPreemption();
        emuManager.tapAtPoint(EMULATOR_NUMBER, point);
    }

    /**
     * Taps at a random point within the specified rectangle.
     * 
     * @param p1 First corner of the rectangle
     * @param p2 Opposite corner of the rectangle
     */
    public void tapRandomPoint(DTOPoint p1, DTOPoint p2) {
        checkPreemption();
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, p1, p2);
    }

    /**
     * Taps at random points within the specified rectangle multiple times.
     * 
     * @param p1    First corner of the rectangle
     * @param p2    Opposite corner of the rectangle
     * @param count Number of taps to perform
     * @param delay Delay in milliseconds between taps
     */
    public void tapRandomPoint(DTOPoint p1, DTOPoint p2, int count, int delay) {
        checkPreemption();
        emuManager.tapAtRandomPoint(EMULATOR_NUMBER, p1, p2, count, delay);
    }

    /**
     * Performs a swipe gesture on the emulator screen.
     * 
     * @param start Starting point of the swipe
     * @param end   Ending point of the swipe
     */
    public void swipe(DTOPoint start, DTOPoint end) {
        checkPreemption();
        emuManager.executeSwipe(EMULATOR_NUMBER, start, end);
    }

    /**
     * Taps the back button on the emulator.
     */
    public void tapBackButton() {
        checkPreemption();
        emuManager.tapBackButton(EMULATOR_NUMBER);
    }

    /**
     * Sleeps for the specified duration, occasionally checking for pending
     * injections.
     * 
     * @param millis Duration to sleep in milliseconds
     * @throws RuntimeException if interrupted
     */
    protected void sleepTask(long millis) {
        checkPreemption();
        long endTime = System.currentTimeMillis() + millis;
        long interval = 200; // Check every 200ms

        try {
            while (System.currentTimeMillis() < endTime) {
                long remaining = endTime - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }

                // Check for and run any pending injections contextually.
                // Skipped entirely if the task has declared it does not accept injections
                // (e.g. screen-owning tasks like BearTrapTask).
                if (!isInjecting && acceptsInjections()) {
                    InjectionRule pendingInjection = GlobalMonitorService.getInstance()
                            .pollPendingInjection(profile.getId());
                    if (pendingInjection != null) {
                        isInjecting = true;
                        try {
                            logDebug("Injecting: " + pendingInjection.getRuleName());
                            pendingInjection.executeInjection(emuManager, profile, this);
                            logDebug("Injection done: " + pendingInjection.getRuleName());
                        } catch (Exception e) {
                            logError("Injection error: " + pendingInjection.getRuleName(), e);
                        } finally {
                            isInjecting = false;
                        }
                    }
                }

                Thread.sleep(Math.min(remaining, interval));
                checkPreemption(); // Check repeatedly during sleep
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task was interrupted during sleep", e);
        }
    }

    // ========================================================================
    // LOGGING METHODS
    // ========================================================================

    public void logInfo(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.info(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.INFO, taskName, profile.getName(), message);
    }

    public void logWarning(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.warn(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.WARNING, taskName, profile.getName(), message);

        if (message != null && message.toLowerCase().contains("ocr")) {
            this.currentOcrFailures++;
        }
    }

    public void logError(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.error(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), message);

        if (message != null && message.toLowerCase().contains("ocr")) {
            this.currentOcrFailures++;
        }
    }

    public void logError(String message, Throwable t) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.error(prefixedMessage, t);
        servLogs.appendLog(EnumTpMessageSeverity.ERROR, taskName, profile.getName(), message);

        if (message != null && message.toLowerCase().contains("ocr")) {
            this.currentOcrFailures++;
        }
    }

    public void logDebug(String message) {
        String prefixedMessage = profile.getName() + " - " + message;
        logger.debug(prefixedMessage);
        servLogs.appendLog(EnumTpMessageSeverity.DEBUG, taskName, profile.getName(), message);
    }

    // ========================================================================
    // HOOK METHODS (Override in subclasses)
    // ========================================================================

    /**
     * Indicates whether this task provides daily mission progress.
     * 
     * @return true if task completion counts toward daily missions
     */
    public boolean provideDailyMissionProgress() {
        return false;
    }

    /**
     * Indicates whether this task provides triumph progress.
     * 
     * @return true if task completion counts toward triumph rewards
     */
    public boolean provideTriumphProgress() {
        return false;
    }

    /**
     * Indicates whether this task consumes stamina.
     * 
     * @return true if task requires stamina validation before execution
     */
    protected boolean consumesStamina() {
        return false;
    }

    /**
     * Indicates whether injection rules may be run during this task's sleep cycles.
     *
     * <p>
     * Override and return {@code false} in tasks that own the emulator screen
     * exclusively (e.g. {@code BearTrapTask}) to prevent injection rules from
     * firing unexpected taps or navigation changes in the middle of a complex flow.
     *
     * @return {@code true} if injections are permitted (default); {@code false} to
     *         suppress all injections while this task is executing
     */
    protected boolean acceptsInjections() {
        return true;
    }

    // ========================================================================
    // SCHEDULING METHODS
    // ========================================================================

    public void reschedule(LocalDateTime rescheduledTime) {
        Duration difference = Duration.between(LocalDateTime.now(), rescheduledTime);
        scheduledTime = LocalDateTime.now().plus(difference);
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long diff = scheduledTime.toEpochSecond(ZoneOffset.UTC) -
                LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        return unit.convert(diff, TimeUnit.SECONDS);
    }

    // ========================================================================
    // GETTERS & SETTERS
    // ========================================================================

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public LocalDateTime getLastExecutionTime() {
        return lastExecutionTime;
    }

    public void setLastExecutionTime(LocalDateTime lastExecutionTime) {
        this.lastExecutionTime = lastExecutionTime;
    }

    public Integer getTpDailyTaskId() {
        return tpTask.getId();
    }

    public TpDailyTaskEnum getTpTask() {
        return tpTask;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setProfile(DTOProfiles profile) {
        this.profile = profile;
    }

    public LocalDateTime getScheduled() {
        return scheduledTime;
    }

    public DTOProfiles getProfile() {
        return profile;
    }

    public void setShouldUpdateConfig(boolean shouldUpdateConfig) {
        this.shouldUpdateConfig = shouldUpdateConfig;
    }

    // ========================================================================
    // PREEMPTION LOGIC
    // ========================================================================

    private PreemptionToken preemptionToken;

    /**
     * Attaches a preemption token to this task.
     * This is called by the ExecutionContext when the task is about to be executed.
     * 
     * @param token The token to attach
     */
    public void attachToken(PreemptionToken token) {
        this.preemptionToken = token;
    }

    /**
     * Checks if the task has been preempted.
     * 
     * @throws cl.camodev.wosbot.ex.TaskPreemptedException if the task was preempted
     */
    protected void checkPreemption() {
        if (Thread.currentThread().isInterrupted()) {
            throw new StopExecutionException("Task interrupted by user request");
        }
        if (preemptionToken != null) {
            preemptionToken.check();
        }
    }

    // ========================================================================
    // EQUALITY & HASHING
    // ========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof DelayedTask))
            return false;
        if (getClass() != o.getClass())
            return false;

        DelayedTask that = (DelayedTask) o;

        if (tpTask != that.tpTask)
            return false;
        if (!Objects.equals(profile.getId(), that.profile.getId()))
            return false;

        Object keyThis = this.getDistinctKey();
        Object keyThat = that.getDistinctKey();
        if (keyThis != null || keyThat != null) {
            return Objects.equals(keyThis, keyThat);
        }

        return true;
    }

    @Override
    public int hashCode() {
        Object key = getDistinctKey();
        if (key != null) {
            return Objects.hash(getClass(), tpTask, profile.getId(), key);
        } else {
            return Objects.hash(getClass(), tpTask, profile.getId());
        }
    }

    @Override
    public int compareTo(Delayed o) {
        if (this == o)
            return 0;

        // Determine the scheduled time of the other task
        // We assume 'o' is also a DelayedTask or at least has a delay convertible to
        // us.
        // Since Delayed interface mainly provides getDelay, we can use that to
        // estimate,
        // but for accurate scheduled time comparison, we should cast if possible.

        if (o instanceof DelayedTask) {
            return this.scheduledTime.compareTo(((DelayedTask) o).getScheduled());
        }

        // Fallback to getDelay if strictly Delayed (less precise)
        return Long.compare(this.getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
    }
}