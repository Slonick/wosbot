package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;

import cl.camodev.wosbot.serv.task.helper.TemplateSearchHelper.SearchConfig;

import java.time.LocalDateTime;

public class DoExplorationTask extends DelayedTask {

    public DoExplorationTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override

    protected void execute() {
        logInfo("Starting Do Exploration task...");

        tapRandomPoint(new DTOPoint(40, 1190), new DTOPoint(100, 1250));
        sleepTask(500);

        DTOImageSearchResult result = templateSearchHelper.searchTemplate(
                EnumTemplates.EXPLORATION_BUTTON,
                SearchConfig.builder().withMaxAttempts(3).withDelay(1000L).build());

        if (result != null && result.isFound()) {
            logInfo("Exploring...");

            tapRandomPoint(new DTOPoint(240, 1150), new DTOPoint(480, 1200));
            sleepTask(200);
            boolean keepFighting = true;

            while (keepFighting) {
                tapRandomPoint(new DTOPoint(55, 1170), new DTOPoint(330, 1220));
                sleepTask(200);
                tapRandomPoint(new DTOPoint(390, 1170), new DTOPoint(670, 1220));

                boolean battleResultFound = false;

                for (int i = 0; i < 24; i++) {
                    DTOImageSearchResult victory = templateSearchHelper.searchTemplate(
                            EnumTemplates.EXPLORATION_VICTORY,
                            SearchConfig.builder().withMaxAttempts(1).withDelay(0L).build());

                    if (victory != null && victory.isFound()) {
                        logInfo("Victory! Continue...");
                        battleResultFound = true;
                        tapRandomPoint(new DTOPoint(400, 990), new DTOPoint(658, 1038));
                        sleepTask(200);
                        break; 
                    }

                    DTOImageSearchResult defeat = templateSearchHelper.searchTemplate(
                            EnumTemplates.EXPLORATION_DEFEAT,
                            SearchConfig.builder().withMaxAttempts(1).withDelay(0L).build());

                    if (defeat != null && defeat.isFound()) {
                        logInfo("Defeated.. Rescheduling...");
                        battleResultFound = true;
                        keepFighting = false; 
                        this.reschedule(LocalDateTime.now().plusHours(1));
                        return; 
                    }

                    sleepTask(5000);
                }

                if (!battleResultFound) {
                    logWarning("Battle timeout: Neither victory nor defeat screen found within 2 minutes.");
                    keepFighting = false;
                }
            }

            logInfo("Exploration loop ended. Rescheduling.");
            this.reschedule(LocalDateTime.now().plusHours(1));

        } else {
            logWarning("Exploration button not found. Stopping task.");
            this.reschedule(LocalDateTime.now().plusHours(1));
        }
    }
}
