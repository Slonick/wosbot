package cl.camodev.wosbot.beasthunting.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;

public class BeastHuntingLayoutController extends AbstractProfileController {

    @FXML
    private CheckBox checkBoxEnableBeastHunting;

    @FXML
    private ComboBox<Integer> comboBoxBeastHuntingMarches;

    @FXML
    private ComboBox<Integer> comboBoxBeastHuntingLevel;

    @FXML
    private void initialize() {
        // Map checkbox
        checkBoxMappings.put(checkBoxEnableBeastHunting, EnumConfigurationKey.BEAST_HUNTING_ENABLED_BOOL);

        // Initialize marches ComboBox (1–6)
        comboBoxBeastHuntingMarches.getItems().addAll(1, 2, 3, 4, 5, 6);
        comboBoxMappings.put(comboBoxBeastHuntingMarches, EnumConfigurationKey.BEAST_HUNTING_MARCHES_INT);

        // Initialize level ComboBox (1–30)
        for (int i = 1; i <= 30; i++) {
            comboBoxBeastHuntingLevel.getItems().add(i);
        }
        comboBoxMappings.put(comboBoxBeastHuntingLevel, EnumConfigurationKey.BEAST_HUNTING_LEVEL_INT);

        // Disable controls when checkbox is not selected
        comboBoxBeastHuntingMarches.disableProperty().bind(checkBoxEnableBeastHunting.selectedProperty().not());
        comboBoxBeastHuntingLevel.disableProperty().bind(checkBoxEnableBeastHunting.selectedProperty().not());

        // Initialize change events (inherited from AbstractProfileController)
        initializeChangeEvents();
    }
}
