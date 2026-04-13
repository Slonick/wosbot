package cl.camodev.wosbot.serv.task;

import cl.camodev.wosbot.serv.task.impl.ArenaTask;
import cl.camodev.wosbot.serv.task.impl.BearTrapTask;
import cl.camodev.wosbot.serv.task.impl.InitializeTask;
import cl.camodev.wosbot.serv.task.impl.DummyTask;

/**
 * Default implementation of TaskPriorityProvider.
 * Assigns priority based on task type:
 * - InitializeTask: 1000 (Highest)
 * - BearTrapTask: 900
 * - ArenaTask: 800
 * - Others: 0
 */
public class DefaultTaskPriorityProvider implements TaskPriorityProvider {

    @Override
    public int getPriority(DelayedTask task) {
        if (task instanceof InitializeTask) {
            return 1000;
        }
        if (task instanceof cl.camodev.wosbot.serv.task.impl.SkipTutorialTask) {
            return 950;
        }
        if (task instanceof BearTrapTask) {
            return 900;
        }
        if (task instanceof ArenaTask) {
            return 800;
        }
        if (task instanceof DummyTask) {
            Integer priority = task.getProfile().getConfig(
                    cl.camodev.wosbot.console.enumerable.EnumConfigurationKey.DUMMY_TASK_PRIORITY_INT,
                    Integer.class);
            return priority != null ? priority : 100;
        }
        // Custom tasks with user-specified priority
        Integer customPriority = task.getCustomPriority();
        if (customPriority != null) {
            return customPriority;
        }
        return 0;
    }
}
