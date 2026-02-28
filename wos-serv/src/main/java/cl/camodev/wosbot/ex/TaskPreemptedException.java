package cl.camodev.wosbot.ex;

/**
 * Exception thrown when a task is preempted by the Global Monitor Service.
 * This indicates that the current task should stop immediately to allow
 * a higher priority task to execute.
 */
public class TaskPreemptedException extends RuntimeException {

    private final String reasoning;

    public TaskPreemptedException(String reasoning) {
        super("Task preempted: " + reasoning);
        this.reasoning = reasoning;
    }

    public String getReasoning() {
        return reasoning;
    }
}
