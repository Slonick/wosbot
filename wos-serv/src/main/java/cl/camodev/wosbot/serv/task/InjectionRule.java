package cl.camodev.wosbot.serv.task;

import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTORawImage;

/**
 * Interface for defining quick, cooperative rules that can be injected into a
 * currently running task during its sleep cycles. These rules are evaluated by
 * the GlobalMonitorService.
 */
public interface InjectionRule {

    /**
     * Checks if the injection condition is met using a pre-captured screenshot.
     * This is the preferred method — called by GlobalMonitorService so that a
     * single screenshot is shared across all rules in one monitoring cycle.
     *
     * @param emuManager The emulator manager (for template matching, no new
     *                   capture)
     * @param profile    The profile being monitored
     * @param screenshot A pre-captured raw screenshot taken by GlobalMonitorService
     * @return true if the injection should be queued for execution
     */
    boolean shouldInject(EmulatorManager emuManager, DTOProfiles profile, DTORawImage screenshot);

    /**
     * Executes the injection task on the main thread during a task's sleep cycle.
     *
     * @param emuManager  The emulator manager to use for interacting with the game
     * @param profile     The profile being monitored
     * @param currentTask The task that is currently sleeping/executing
     */
    void executeInjection(EmulatorManager emuManager, DTOProfiles profile, DelayedTask currentTask);

    /**
     * Gets a human-readable name for this rule.
     *
     * @return Rule name
     */
    String getRuleName();
}
