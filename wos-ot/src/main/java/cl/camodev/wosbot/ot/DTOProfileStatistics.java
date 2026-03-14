package cl.camodev.wosbot.ot;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DTOProfileStatistics implements Serializable {
    
    // Tracks metrics distinct to individual automated tasks
    private Map<String, DTOTaskStatistic> taskStatistics = new HashMap<>();
    
    // Tracks random specific counting logic (like "Arena Battles Won") 
    private Map<String, Integer> customCounters = new HashMap<>();

    public DTOProfileStatistics() {}

    public Map<String, DTOTaskStatistic> getTaskStatistics() {
        return taskStatistics;
    }

    public void setTaskStatistics(Map<String, DTOTaskStatistic> taskStatistics) {
        this.taskStatistics = taskStatistics;
    }

    public Map<String, Integer> getCustomCounters() {
        return customCounters;
    }

    public void setCustomCounters(Map<String, Integer> customCounters) {
        this.customCounters = customCounters;
    }
}
