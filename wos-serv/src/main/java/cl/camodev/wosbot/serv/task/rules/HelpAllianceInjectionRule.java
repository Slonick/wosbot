package cl.camodev.wosbot.serv.task.rules;

import cl.camodev.wosbot.console.enumerable.EnumTemplates;
import cl.camodev.wosbot.emulator.EmulatorManager;
import cl.camodev.wosbot.ot.DTOImageSearchResult;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.ot.DTORawImage;
import cl.camodev.wosbot.serv.task.DelayedTask;
import cl.camodev.wosbot.serv.task.InjectionRule;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;

/**
 * A sample InjectionRule that searches for the "Alliance Help" shortcut button
 * on the game screen and gracefully injects a tap when yielding inside
 * sleepTask().
 */
public class HelpAllianceInjectionRule implements InjectionRule {

    @Override
    public boolean shouldInject(EmulatorManager emuManager, DTOProfiles profile, DTORawImage screenshot) {
        // Respect profile settings before scanning
        boolean runHelpAllies = profile.getConfig(EnumConfigurationKey.ALLIANCE_HELP_BOOL, Boolean.class);
        if (!runHelpAllies) {
            return false;
        }

        // Use the shared screenshot — no new ADB capture here
        try {
            DTOImageSearchResult helpRequest = emuManager.searchTemplate(
                    profile.getEmulatorNumber(),
                    screenshot,
                    EnumTemplates.GAME_HOME_SHORTCUTS_HELP_REQUEST2,
                    90);
            return helpRequest.isFound();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void executeInjection(EmulatorManager emuManager, DTOProfiles profile, DelayedTask currentTask) {
        // Runs in the main worker thread during a sleep yielding loop:
        // We actually click the help button with a fresh screenshot.
        currentTask.logDebug("Tapping Alliance Help");
        try {
            DTOImageSearchResult helpRequest = emuManager.searchTemplate(
                    profile.getEmulatorNumber(),
                    EnumTemplates.GAME_HOME_SHORTCUTS_HELP_REQUEST2,
                    90);
            if (helpRequest.isFound()) {
                emuManager.tapAtPoint(profile.getEmulatorNumber(), helpRequest.getPoint());
                // Sleep inside the injection safely, delayed tasks guard against recursion.
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            currentTask.logError("Help tap failed.", e);
        }
    }

    @Override
    public String getRuleName() {
        return "HelpAllianceInjectionRule";
    }
}
