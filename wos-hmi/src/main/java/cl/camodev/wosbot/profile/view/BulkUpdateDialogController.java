package cl.camodev.wosbot.profile.view;

import cl.camodev.wosbot.profile.model.ProfileAux;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the bulk update dialog that allows users to select which profiles
 * to update with settings from a template profile.
 */
public class BulkUpdateDialogController {

    @FXML
    private Label lblTemplateProfile;

    @FXML
    private VBox vboxProfileList;

    @FXML
    private Button btnSelectAll;

    @FXML
    private Button btnDeselectAll;

    @FXML
    private Button btnCancel;

    @FXML
    private Button btnUpdate;

    private ProfileAux templateProfile;
    private List<ProfileAux> availableProfiles;
    private List<CheckBox> profileCheckBoxes;
    private Stage dialogStage;
    private boolean updateConfirmed = false;
    private List<ProfileAux> selectedProfiles;

    /**
     * Initializes the controller.
     */
    @FXML
    private void initialize() {
        profileCheckBoxes = new ArrayList<>();
        selectedProfiles = new ArrayList<>();
    }

    /**
     * Sets up the dialog with the template profile and available profiles to update.
     */
    public void setupDialog(ProfileAux templateProfile, List<ProfileAux> availableProfiles, Stage dialogStage) {
        this.templateProfile = templateProfile;
        this.availableProfiles = availableProfiles;
        this.dialogStage = dialogStage;

        // Set template profile label
        lblTemplateProfile.setText("Template Profile: " + templateProfile.getName());

        // Create checkboxes for each available profile (excluding the template)
        createProfileCheckBoxes();
    }

    /**
     * Creates checkboxes for each profile that can be updated.
     */
    private void createProfileCheckBoxes() {
        vboxProfileList.getChildren().clear();
        profileCheckBoxes.clear();

        for (ProfileAux profile : availableProfiles) {
            // Skip the template profile itself
            if (profile.getId().equals(templateProfile.getId())) {
                continue;
            }

            CheckBox checkBox = new CheckBox(profile.getName() + " (Emulator: " + profile.getEmulatorNumber() + ")");
            checkBox.setUserData(profile);
            checkBox.setStyle("-fx-font-size: 12px;");

            profileCheckBoxes.add(checkBox);
            vboxProfileList.getChildren().add(checkBox);
        }
    }

    /**
     * Handles the "Select All" button action.
     */
    @FXML
    private void handleSelectAll() {
        profileCheckBoxes.forEach(checkBox -> checkBox.setSelected(true));
    }

    /**
     * Handles the "Deselect All" button action.
     */
    @FXML
    private void handleDeselectAll() {
        profileCheckBoxes.forEach(checkBox -> checkBox.setSelected(false));
    }

    /**
     * Handles the "Cancel" button action.
     */
    @FXML
    private void handleCancel() {
        updateConfirmed = false;
        dialogStage.close();
    }

    /**
     * Handles the "Update Selected" button action.
     */
    @FXML
    private void handleUpdate() {
        // Get selected profiles
        selectedProfiles.clear();
        for (CheckBox checkBox : profileCheckBoxes) {
            if (checkBox.isSelected()) {
                selectedProfiles.add((ProfileAux) checkBox.getUserData());
            }
        }

        if (selectedProfiles.isEmpty()) {
            // Show warning - no profiles selected
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
            alert.setTitle("No Profiles Selected");
            alert.setHeaderText(null);
            alert.setContentText("Please select at least one profile to update.");
            alert.showAndWait();
            return;
        }

        // Confirm the update
        javafx.scene.control.Alert confirmAlert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Bulk Update");
        confirmAlert.setHeaderText("Update " + selectedProfiles.size() + " profile(s)");
        confirmAlert.setContentText("This will apply settings from '" + templateProfile.getName() +
                                   "' to the selected profiles. This action cannot be undone. Continue?");

        if (confirmAlert.showAndWait().orElse(null) == javafx.scene.control.ButtonType.OK) {
            updateConfirmed = true;
            dialogStage.close();
        }
    }

    /**
     * Returns whether the user confirmed the update operation.
     */
    public boolean isUpdateConfirmed() {
        return updateConfirmed;
    }

    /**
     * Returns the list of profiles selected for update.
     */
    public List<ProfileAux> getSelectedProfiles() {
        return new ArrayList<>(selectedProfiles);
    }
}
