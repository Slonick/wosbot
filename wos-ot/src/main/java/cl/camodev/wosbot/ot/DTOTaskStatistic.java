package cl.camodev.wosbot.ot;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DTOTaskStatistic implements Serializable {
    private String taskName;
    private int numberOfRuns;
    private long totalExecutionTimeMs;
    private String lastRunTime;
    private int totalOcrFailures;
    private int totalTemplateSearchFailures;

    public DTOTaskStatistic() {}

    public DTOTaskStatistic(String taskName) {
        this.taskName = taskName;
        this.numberOfRuns = 0;
        this.totalExecutionTimeMs = 0;
        this.totalOcrFailures = 0;
        this.totalTemplateSearchFailures = 0;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public int getNumberOfRuns() {
        return numberOfRuns;
    }

    public void setNumberOfRuns(int numberOfRuns) {
        this.numberOfRuns = numberOfRuns;
    }

    public long getTotalExecutionTimeMs() {
        return totalExecutionTimeMs;
    }

    public void setTotalExecutionTimeMs(long totalExecutionTimeMs) {
        this.totalExecutionTimeMs = totalExecutionTimeMs;
    }

    public String getLastRunTime() {
        return lastRunTime;
    }

    public void setLastRunTime(String lastRunTime) {
        this.lastRunTime = lastRunTime;
    }

    // Convenience computed property for UI display
    public long getAverageExecutionTimeMs() {
        if (numberOfRuns == 0) return 0;
        return totalExecutionTimeMs / numberOfRuns;
    }

    public double getAverageOcrFailures() {
        if (numberOfRuns == 0) return 0.0;
        return (double) totalOcrFailures / numberOfRuns;
    }

    public double getAverageTemplateFailures() {
        if (numberOfRuns == 0) return 0.0;
        return (double) totalTemplateSearchFailures / numberOfRuns;
    }

    public int getTotalOcrFailures() {
        return totalOcrFailures;
    }

    public void setTotalOcrFailures(int totalOcrFailures) {
        this.totalOcrFailures = totalOcrFailures;
    }

    public int getTotalTemplateSearchFailures() {
        return totalTemplateSearchFailures;
    }

    public void setTotalTemplateSearchFailures(int totalTemplateSearchFailures) {
        this.totalTemplateSearchFailures = totalTemplateSearchFailures;
    }
}
