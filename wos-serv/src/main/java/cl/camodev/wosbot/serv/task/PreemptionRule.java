package cl.camodev.wosbot.serv.task;

import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTORawImage;

/**
 * Interface for defining rules that can trigger task preemption.
 * These rules are evaluated by the GlobalMonitorService.
 */
public interface PreemptionRule {

    /**
     * Checks if the preemption condition is met using a pre-captured screenshot.
     * This is the preferred method — called by GlobalMonitorService so that a
     * single screenshot is shared across all rules in one monitoring cycle.
     *
     * @param emuManager The emulator manager (for template matching, no new
     *                   capture)
     * @param profile    The profile being monitored
     * @param screenshot A pre-captured raw screenshot taken by GlobalMonitorService
     * @return true if preemption should be triggered
     */
    boolean shouldPreempt(EmulatorManager emuManager, DTOProfiles profile, DTORawImage screenshot);

    /**
     * Gets the task type that should be executed when this rule is triggered.
     *
     * @return The task enum to execute
     */
    TpDailyTaskEnum getTaskToExecute();

    /**
     * Gets a human-readable name for this rule.
     *
     * @return Rule name
     */
    String getRuleName();
}
