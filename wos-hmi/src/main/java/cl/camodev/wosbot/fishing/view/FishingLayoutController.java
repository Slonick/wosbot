package cl.camodev.wosbot.fishing.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.profile.model.ProfileAux;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.TaskQueue;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;

public class FishingLayoutController extends AbstractProfileController {

    @FXML
    private CheckBox checkBoxEnableFishing;

    @FXML
    private CheckBox checkBoxEnableTestHookLoop;

    private ProfileAux currentProfile;

    @FXML
    private void initialize() {
        checkBoxMappings.put(checkBoxEnableFishing, EnumConfigurationKey.FISHING_MINIGAME_ENABLED_BOOL);
        checkBoxMappings.put(checkBoxEnableTestHookLoop, EnumConfigurationKey.TEST_HOOK_LOOP_ENABLED_BOOL);
        initializeChangeEvents();

        // Immediately start/stop the loop when the checkbox is toggled
        checkBoxEnableTestHookLoop.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (currentProfile != null) {
                handleTestHookLoopToggle(newVal);
            }
        });
    }

    @Override
    public void onProfileLoad(ProfileAux profile) {
        super.onProfileLoad(profile);
        this.currentProfile = profile;
    }

    private void handleTestHookLoopToggle(boolean enabled) {
        if (currentProfile == null) return;

        TaskQueue queue = ServScheduler.getServices().getQueueManager().getQueue(currentProfile.getId());
        if (queue == null) return;

        if (enabled) {
            queue.executeTaskNow(TpDailyTaskEnum.TEST_HOOK_LOOP, true);
        } else {
            queue.removeTask(TpDailyTaskEnum.TEST_HOOK_LOOP);
            // TestHookLoopTask checks the config flag each iteration and will exit naturally
        }
    }
}
