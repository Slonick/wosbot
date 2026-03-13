package cl.camodev.wosbot.serv.task.impl;

import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.task.DelayedTask;

public class CreateCharacterTask extends DelayedTask {

    public CreateCharacterTask(DTOProfiles profile, TpDailyTaskEnum tpTask) {
        super(profile, tpTask);
    }

    @Override
    protected void execute() {
        logInfo("Starting Create Character Task...");
        // Boilerplate code here

        // Integer maxAgeMinutes = profile.getConfig(
        //         cl.camodev.wosbot.console.enumerable.EnumConfigurationKey.CREATE_CHARACTER_MAX_AGE_MINUTES_INT,
        //         Integer.class);
        
        // Wait or implement logic

        logInfo("Finished Create Character Task.");
    }
}
