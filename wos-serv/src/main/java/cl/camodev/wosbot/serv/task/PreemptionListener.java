package cl.camodev.wosbot.serv.task;

import cl.camodev.wosbot.ot.DTOProfiles;

/**
 * Listener interface for handling preemption events triggered by the
 * GlobalMonitorService.
 */
public interface PreemptionListener {
    /**
     * Called when a preemption rule is triggered for a profile.
     * 
     * @param profile The profile affecting by the preemption
     * @param rule    The rule that was triggered
     */
    void onPreemption(DTOProfiles profile, PreemptionRule rule);
}
