package cl.camodev.wosbot.serv.task.rules;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOPoint;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTORawImage;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.InjectionRule;

/**
 * Injection rule that watches for the Furnace Upgrade Pack shortcut button.
 * When detected, it clicks the pack button, claims the upgrade reward, taps the
 * close/confirm coordinate, and presses back twice before returning control.
 */
public class FurnaceUpgradeInjectionRule implements InjectionRule {

    @Override
    public boolean shouldInject(EmulatorManager emuManager, DTOProfiles profile, DTORawImage screenshot) {
        // Use the shared screenshot — no new ADB capture here
        try {
            DTOImageSearchResult pack = emuManager.searchTemplate(
                    profile.getEmulatorNumber(),
                    screenshot,
                    EnumTemplates.FURNACE_UPGRADE_PACK,
                    90);
            return pack.isFound();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void executeInjection(EmulatorManager emuManager, DTOProfiles profile, DelayedTask currentTask) {
        currentTask.logDebug("FurnaceUpgradeInjectionRule: starting execution");
        try {
            // Search for the upgrade pack button again on the live screen
            DTOImageSearchResult pack = emuManager.searchTemplate(
                    profile.getEmulatorNumber(),
                    EnumTemplates.FURNACE_UPGRADE_PACK,
                    90);

            if (!pack.isFound()) {
                currentTask.logDebug("FurnaceUpgradeInjectionRule: pack button no longer visible, aborting.");
                return;
            }

            // Click the furnace upgrade pack shortcut
            emuManager.tapAtPoint(profile.getEmulatorNumber(), pack.getPoint());
            Thread.sleep(500);

            // Search for the claim button and click it if found
            DTOImageSearchResult claimBtn = emuManager.searchTemplate(
                    profile.getEmulatorNumber(),
                    EnumTemplates.FURNACE_UPGRADE_CLAIM,
                    90);

            if (claimBtn.isFound()) {
                emuManager.tapAtPoint(profile.getEmulatorNumber(), claimBtn.getPoint());
                Thread.sleep(200);
                // Tap the close/confirm coordinate (360, 858)
                emuManager.tapAtPoint(profile.getEmulatorNumber(), new DTOPoint(360, 858));
                Thread.sleep(200);
                // Press back twice to return to the previous screen
                emuManager.tapBackButton(profile.getEmulatorNumber());
                Thread.sleep(200);
                emuManager.tapBackButton(profile.getEmulatorNumber());

            } else {
                currentTask.logDebug("FurnaceUpgradeInjectionRule: claim button not found, aborting.");
                return;
            }

            currentTask.logDebug("FurnaceUpgradeInjectionRule: completed successfully.");
        } catch (Exception e) {
            currentTask.logError("FurnaceUpgradeInjectionRule failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getRuleName() {
        return "FurnaceUpgradeInjectionRule";
    }
}
