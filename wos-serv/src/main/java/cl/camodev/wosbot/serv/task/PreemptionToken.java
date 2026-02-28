package cl.camodev.wosbot.serv.task;

import cl.camodev.wosbot.ex.TaskPreemptedException;

/**
 * Handles the state and logic for task preemption.
 * Ensures that preemption is triggered correctly and respects a cooldown
 * period.
 */
public class PreemptionToken {
    private volatile PreemptionRule rule;

    public synchronized void trigger(PreemptionRule r) {
        // If already triggered, ignore subsequent triggers (first cause wins)
        if (rule != null) {
            return;
        }

        this.rule = r;
    }

    /**
     * Checks if preemption has been triggered and throws an exception if so.
     * This method should be called frequently during task execution.
     * 
     * @throws TaskPreemptedException if the task has been preempted
     */
    public void check() {
        if (rule != null) {
            throw new TaskPreemptedException(rule.getRuleName());
        }
    }

    /**
     * Checks if preemption has been triggered without throwing an exception.
     * 
     * @return true if preemption is triggered
     */
    public boolean isTriggered() {
        return rule != null;
    }

    /**
     * Resets the preemption token to its initial state.
     */
    public synchronized void clear() {
        this.rule = null;
    }
}
