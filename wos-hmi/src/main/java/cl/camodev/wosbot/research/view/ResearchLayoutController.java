package cl.camodev.wosbot.research.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;

public class ResearchLayoutController extends AbstractProfileController {

    @FXML
    private CheckBox checkBoxEnableResearch;

    @FXML
    private CheckBox checkBoxGrowth;
    @FXML
    private CheckBox checkBoxEconomy;
    @FXML
    private CheckBox checkBoxBattle;

    @FXML
    private void initialize() {
        checkBoxMappings.put(checkBoxEnableResearch, EnumConfigurationKey.RESEARCH_ENABLED_BOOL);
        checkBoxMappings.put(checkBoxGrowth, EnumConfigurationKey.RESEARCH_GROWTH_BOOL);
        checkBoxMappings.put(checkBoxEconomy, EnumConfigurationKey.RESEARCH_ECONOMY_BOOL);
        checkBoxMappings.put(checkBoxBattle, EnumConfigurationKey.RESEARCH_BATTLE_BOOL);
        initializeChangeEvents();
    }
}
