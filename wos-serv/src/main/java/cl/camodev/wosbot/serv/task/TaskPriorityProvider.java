package cl.camodev.wosbot.serv.task;

/**
 * Strategy interface for determining the priority of a DelayedTask.
 */
public interface TaskPriorityProvider {
    /**
     * Calculates the priority for a given task.
     * Higher values indicate higher priority.
     * 
     * @param task The task to evaluate
     * @return The priority score
     */
    int getPriority(DelayedTask task);
}
