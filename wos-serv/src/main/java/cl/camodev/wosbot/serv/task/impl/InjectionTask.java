package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;

/**
 * A lightweight task context specifically created for running pending
 * Injections
 * when the TaskQueue is idle. This task does not execute game logic itself;
 * instead, it acts as a host environment to provide logging, preemption checks,
 * and context for InjectionRules.
 */
public class InjectionTask extends DelayedTask {

    public InjectionTask(DTOProfiles profile) {
        // We use INITIALIZE simply to satisfy the superclass constructor.
        // This task is never passed to TaskQueue#executeTask() so it won't affect DB
        // state.
        super(profile, TpDailyTaskEnum.INITIALIZE);
        this.taskName = "Idle Injection";
    }

    @Override
    protected void execute() {
        // Intentionally empty. This task is passed directly to the injection rule,
        // and its execute() method is never called by the TaskQueue.
    }
}
