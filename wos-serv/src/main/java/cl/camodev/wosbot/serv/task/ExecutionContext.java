package cl.camodev.wosbot.serv.task;

/**
 * Holds independent execution context for a running task.
 * Manages the connection between the task and its preemption token.
 */
public class ExecutionContext {
    private final DelayedTask task;
    private final PreemptionToken token;

    /**
     * Creates a new execution context for the given task.
     * It automatically creates a new PreemptionToken and attaches it to the task.
     * 
     * @param task The task to execute
     */
    public ExecutionContext(DelayedTask task) {
        this.task = task;
        this.token = new PreemptionToken();
        task.attachToken(token);
    }

    /**
     * Triggers preemption on this execution context.
     * 
     * @param rule The rule triggering the preemption
     */
    public void preempt(PreemptionRule rule) {
        token.trigger(rule);
    }

    /**
     * Gets the task associated with this context.
     * 
     * @return The task
     */
    public DelayedTask getTask() {
        return task;
    }

    /**
     * Clears the preemption token associated with this context.
     */
    public void clear() {
        token.clear();
    }
}
