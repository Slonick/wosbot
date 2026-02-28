package cl.camodev.wosbot.serv.task.rules;

import cl.camodev.wosbot.serv.task.PreemptionRule;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTORawImage;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;

/**
 * PREEMPTION RULE
 * Checks if "farmMeat.png" is visible.
 * Triggers InitializeTask if found.
 */
public class GatherDeployPreemptionRule implements PreemptionRule {

    @Override
    public boolean shouldPreempt(EmulatorManager emuManager, DTOProfiles profile, DTORawImage screenshot) {
        // Only run if enabled in configuration
        Boolean enabled = profile.getConfig(EnumConfigurationKey.TEST_GATHER_DEPLOY_PREEMPTION_BOOL, Boolean.class);
        if (enabled == null || !enabled) {
            return false;
        }

        try {
            DTOImageSearchResult result = emuManager.searchTemplate(
                    profile.getEmulatorNumber(),
                    screenshot,
                    EnumTemplates.GAME_HOME_SHORTCUTS_FARM_MEAT,
                    90);

            return result.isFound();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public TpDailyTaskEnum getTaskToExecute() {
        return TpDailyTaskEnum.GATHER_RESOURCES;
    }

    @Override
    public String getRuleName() {
        return "TestGatherDeployPreemption";
    }
}
