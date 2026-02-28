package cl.camodev.wosbot.dummy.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import cl.camodev.wosbot.profile.model.ProfileAux;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.task.TaskQueue;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;

public class DummyLayoutController extends AbstractProfileController {

    @FXML
    private CheckBox checkBoxEnableDummyTask;
    @FXML
    private TextField textFieldPriority;

    private ProfileAux currentProfile;

    @FXML
    private void initialize() {
        // Map the checkbox to the configuration key
        checkBoxMappings.put(checkBoxEnableDummyTask, EnumConfigurationKey.DUMMY_TASK_ENABLED_BOOL);
        textFieldMappings.put(textFieldPriority, EnumConfigurationKey.DUMMY_TASK_PRIORITY_INT);

        // Initialize change events (inherited from AbstractProfileController)
        initializeChangeEvents();

        // Add additional listener for dynamic control
        checkBoxEnableDummyTask.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (currentProfile != null) {
                handleDynamicToggle(newVal);
            }
        });

        // Add listener for priority chances to update the task immediately
        textFieldPriority.textProperty().addListener((obs, oldVal, newVal) -> {
            if (currentProfile != null && checkBoxEnableDummyTask.isSelected()) {
                // Re-trigger the toggle logic to update queue
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
            queue.executeTaskNow(TpDailyTaskEnum.DUMMY_TASK, true);
        } else {
            queue.removeTask(TpDailyTaskEnum.DUMMY_TASK);
            // The running task will stop itself on next loop iteration due to logic in
            // DummyTask.java
        }
    }
}
