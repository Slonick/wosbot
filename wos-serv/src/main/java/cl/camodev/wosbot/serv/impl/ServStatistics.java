package cl.camodev.wosbot.serv.impl;

import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.ot.DTOProfileStatistics;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOTaskStatistic;

/**
 * Service to manage and persist bot statistics per profile.
 */
public class ServStatistics {

    private static ServStatistics instance;
    private static final Logger logger = LoggerFactory.getLogger(ServStatistics.class);
    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private ServStatistics() {
        this.objectMapper = new ObjectMapper();
    }

    public static synchronized ServStatistics getServices() {
        if (instance == null) {
            instance = new ServStatistics();
        }
        return instance;
    }

    /**
     * Increments a specific custom counter for the given profile and persists it.
     * 
     * @param profile The profile object
     * @param statKey The name of the statistic to increment
     * @param amount  The amount to increment by
     */
    public synchronized void increment(DTOProfiles profile, String statKey, int amount) {
        if (profile == null || statKey == null) {
            return;
        }

        DTOProfileStatistics stats = getStatistics(profile);
        int currentValue = stats.getCustomCounters().getOrDefault(statKey, 0);
        stats.getCustomCounters().put(statKey, currentValue + amount);

        saveStatistics(profile, stats);
    }

    /**
     * Records an execution of a specific automated task.
     *
     * @param profile The profile running the task
     * @param taskName The name of the task
     * @param executionTimeMs Time taken to execute
     * @param ocrFailures Number of OCR failures during this execution
     * @param templateFailures Number of template search failures during this execution
     */
    public synchronized void recordTaskExecution(DTOProfiles profile, String taskName, long executionTimeMs, int ocrFailures, int templateFailures) {
        if (profile == null || taskName == null) return;

        DTOProfileStatistics stats = getStatistics(profile);
        
        DTOTaskStatistic taskStat = stats.getTaskStatistics().computeIfAbsent(taskName, DTOTaskStatistic::new);
        taskStat.setNumberOfRuns(taskStat.getNumberOfRuns() + 1);
        taskStat.setTotalExecutionTimeMs(taskStat.getTotalExecutionTimeMs() + executionTimeMs);
        taskStat.setTotalOcrFailures(taskStat.getTotalOcrFailures() + ocrFailures);
        taskStat.setTotalTemplateSearchFailures(taskStat.getTotalTemplateSearchFailures() + templateFailures);
        taskStat.setLastRunTime(LocalDateTime.now().format(DATETIME_FORMATTER));

        saveStatistics(profile, stats);
    }

    /**
     * Resets all statistics for the given profile.
     * 
     * @param profile The profile object
     */
    public synchronized void resetAll(DTOProfiles profile) {
        if (profile == null) {
            return;
        }
        saveStatistics(profile, new DTOProfileStatistics());
        logger.info("Statistics reset for profile ID: {}", profile.getId());
    }

    /**
     * Retrieves the current statistics object for a profile.
     * 
     * @param profile The profile object
     * @return DTOProfileStatistics object
     */
    public DTOProfileStatistics getStatistics(DTOProfiles profile) {
        if (profile == null) return new DTOProfileStatistics();

        String json = profile.getConfig(EnumConfigurationKey.STATISTICS_JSON_STRING, String.class);
        if (json == null || json.trim().isEmpty() || json.equals("{}")) {
            return new DTOProfileStatistics();
        }

        try {
            return objectMapper.readValue(json, DTOProfileStatistics.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse statistics JSON for profile ID {}: {}", profile.getId(), e.getMessage());
            return new DTOProfileStatistics();
        }
    }

    private void saveStatistics(DTOProfiles profile, DTOProfileStatistics stats) {
        try {
            String json = objectMapper.writeValueAsString(stats);
            ServConfig.getServices().updateProfileConfig(profile, EnumConfigurationKey.STATISTICS_JSON_STRING, json);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize statistics JSON for profile ID {}: {}", profile.getId(), e.getMessage());
        }
    }
}
