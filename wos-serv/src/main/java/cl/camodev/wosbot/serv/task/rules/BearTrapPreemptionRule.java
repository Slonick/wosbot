package cl.camodev.wosbot.serv.task.rules;

import cl.camodev.wosbot.serv.task.PreemptionRule;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTORawImage;

/**
 * Preemption rule for the Bear Trap event.
 * Checks if the "Bear Trap is Running" indicator is visible on screen.
 */
public class BearTrapPreemptionRule implements PreemptionRule {

    @Override
    public boolean shouldPreempt(EmulatorManager emuManager, DTOProfiles profile, DTORawImage screenshot) {
        try {
            DTOImageSearchResult result = emuManager.searchTemplate(
                    profile.getEmulatorNumber(),
                    screenshot,
                    EnumTemplates.BEAR_HUNT_IS_RUNNING,
                    90);

            return result.isFound();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public TpDailyTaskEnum getTaskToExecute() {
        return TpDailyTaskEnum.BEAR_TRAP;
    }

    @Override
    public String getRuleName() {
        return "BearTrapActive";
    }
}
