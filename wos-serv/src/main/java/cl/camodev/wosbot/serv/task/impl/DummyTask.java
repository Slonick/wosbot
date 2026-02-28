package cl.camodev.wosbot.serv.task.impl;

import java.time.LocalDateTime;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper.SearchConfig;

/**
 * Dummy Task that runs infinitely until it finds a specific image.
 * If found, it reschedules itself to run again in 60 minutes.
 * Checks every 5 seconds.
 */
public class DummyTask extends DelayedTask {

    public DummyTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected void execute() {
        logInfo("Starting Dummy Task - Searching for GO image...");

        while (true) {
            // Check for preemption or stop signals relative to the task framework
            // (The framework usually handles interrupts, but good to be safe in infinite
            // loops)
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            // Check if task is still enabled in configuration
            try {
                // Refresh specific config to see if we should continue
                Boolean enabled = cl.camodev.wosbot.serv.impl.ServProfiles.getServices().getProfiles().stream()
                        .filter(p -> p.getId().equals(profile.getId()))
                        .findFirst()
                        .map(p -> p.getConfig(
                                cl.camodev.wosbot.console.enumerable.EnumConfigurationKey.DUMMY_TASK_ENABLED_BOOL,
                                Boolean.class))
                        .orElse(false);

                if (enabled != null && !enabled) {
                    logInfo("Dummy Task disabled in settings. Stopping task.");
                    break;
                }
            } catch (Exception e) {
                logWarning("Failed to check task status: " + e.getMessage());
            }

            // Search for the image
            DTOImageSearchResult result = templateSearchHelper.searchTemplate(
                    EnumTemplates.GAME_HOME_SHORTCUTS_GO,
                    SearchConfig.builder().build());

            if (result.isFound()) {
                logInfo("Image found! Rescheduling task for 60 minutes from now.");

                // Reschedule for 60 minutes later
                reschedule(LocalDateTime.now().plusMinutes(60));

                // Exit the loop and finish the task
                break;
            } else {
                logInfo("Image not found. Waiting 5 seconds...");
                sleepTask(5000); // Wait 5 seconds before next check
            }
        }
    }
}
