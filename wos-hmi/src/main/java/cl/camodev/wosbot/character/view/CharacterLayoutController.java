package cl.camodev.wosbot.character.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.profile.model.ProfileAux;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.TaskQueue;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;

public class CharacterLayoutController extends AbstractProfileController {

    @FXML
    private CheckBox checkBoxEnableCreateCharacter;
    @FXML
    private TextField textFieldMaxAgeMinutes;

    private ProfileAux currentProfile;

    @FXML
    private void initialize() {
        // Map the checkbox and textfield to the configuration keys
        checkBoxMappings.put(checkBoxEnableCreateCharacter, EnumConfigurationKey.CREATE_CHARACTER_ENABLED_BOOL);
        textFieldMappings.put(textFieldMaxAgeMinutes, EnumConfigurationKey.CREATE_CHARACTER_MAX_AGE_MINUTES_INT);

        // Initialize change events (inherited from AbstractProfileController)
        initializeChangeEvents();

        // Add additional listener for dynamic control
        checkBoxEnableCreateCharacter.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (currentProfile != null) {
                handleDynamicToggle(newVal);
            }
        });

        // Add listener for Max Age changes to update the task immediately
        textFieldMaxAgeMinutes.textProperty().addListener((obs, oldVal, newVal) -> {
            if (currentProfile != null && checkBoxEnableCreateCharacter.isSelected()) {
                handleDynamicToggle(true);
            }
        });
    }

    @Override
    public void onProfileLoad(ProfileAux profile) {
        super.onProfileLoad(profile);
        this.currentProfile = profile;
    }

    private void handleDynamicToggle(boolean enabled) {
        if (currentProfile == null)
            return;

        TaskQueue queue = ServScheduler.getServices().getQueueManager().getQueue(currentProfile.getId());

        if (queue == null)
            return;

        if (enabled) {
            queue.executeTaskNow(TpDailyTaskEnum.CREATE_CHARACTER, true);
        } else {
            queue.removeTask(TpDailyTaskEnum.CREATE_CHARACTER);
        }
    }
}
