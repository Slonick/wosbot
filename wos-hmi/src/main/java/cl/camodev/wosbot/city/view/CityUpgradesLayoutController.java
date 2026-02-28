package cl.camodev.wosbot.city.view;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;

public class CityUpgradesLayoutController extends AbstractProfileController {

	@FXML
	private CheckBox checkBoxUpgradeFurnace, checkBoxPrioritiseFurnace, checkboxAcceptNewSurvivors;

	@FXML
	private TextField textFieldSirvivorsOffset;

	@FXML
	private void initialize() {

		checkBoxMappings.put(checkBoxUpgradeFurnace, EnumConfigurationKey.CITY_UPGRADE_FURNACE_BOOL);
		checkBoxMappings.put(checkBoxPrioritiseFurnace, EnumConfigurationKey.CITY_UPGRADE_PRIORITISE_FURNACE_BOOL);
		checkBoxMappings.put(checkboxAcceptNewSurvivors, EnumConfigurationKey.CITY_ACCEPT_NEW_SURVIVORS_BOOL);

		textFieldMappings.put(textFieldSirvivorsOffset, EnumConfigurationKey.CITY_ACCEPT_NEW_SURVIVORS_OFFSET_INT);

		initializeChangeEvents();
	}

}
