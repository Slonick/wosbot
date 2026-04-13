package cl.camodev.wosbot.profile.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.ot.DTOProfileStatus;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.profile.controller.ProfileManagerActionController;
import cl.camodev.wosbot.profile.model.ConfigAux;
import cl.camodev.wosbot.profile.model.IProfileChangeObserver;
import cl.camodev.wosbot.profile.model.IProfileLoadListener;
import cl.camodev.wosbot.profile.model.ProfileAux;
import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServProfiles;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

public class ProfileManagerLayoutController implements IProfileChangeObserver {

	private final ExecutorService profileQueueExecutor = Executors.newSingleThreadExecutor();
	private ProfileManagerActionController profileManagerActionController;
	private ObservableList<ProfileAux> profiles;
	private FilteredList<ProfileAux> filteredProfiles;
	private SortedList<ProfileAux> sortedProfiles;

	@FXML
	private TableView<ProfileAux> tableviewLogMessages;
	@FXML
	private TableColumn<ProfileAux, Void> columnDelete;
	@FXML
	private TableColumn<ProfileAux, String> columnEmulatorNumber;
	@FXML
	private TableColumn<ProfileAux, Boolean> columnEnabled;
	@FXML
	private TableColumn<ProfileAux, String> columnProfileName;
	@FXML
	private TableColumn<ProfileAux, Long> columnPriority;
	@FXML
	private TableColumn<ProfileAux, String> columnStatus;
	@FXML
	private TableColumn<ProfileAux, String> columnFurnaceLevel;
	@FXML
	private TableColumn<ProfileAux, String> columnStamina;
	@FXML
	private Button btnBulkUpdate;
	@FXML
	private TextField txtSearchProfiles;
	@FXML
	private ComboBox<String> comboBoxSortBy;
	@FXML
	private Button btnColumnSettings;

	private Long loadedProfileId;
	private List<IProfileLoadListener> profileLoadListeners;

	@FXML
	private void initialize() {
		initializeController();
		initializeTableView();
		initializeSearchAndSort();
		loadProfiles();
        ServProfiles.getServices().addProfileDataChangeListener(dto -> Platform.runLater(() -> handleProfileDataChange(dto)));
	}

	private void initializeController() {
		profileManagerActionController = new ProfileManagerActionController(this);
	}

	private void initializeSearchAndSort() {
		// Sort by options
		ObservableList<String> sortOptions = FXCollections.observableArrayList("Name", "Priority", "Status", "Emulator");
		comboBoxSortBy.setItems(sortOptions);
		comboBoxSortBy.getSelectionModel().selectFirst();

		// Search listener
		if (txtSearchProfiles != null) {
			txtSearchProfiles.textProperty().addListener((obs, oldVal, newVal) -> applyFilter(newVal));
		}

		// Sort listener
		comboBoxSortBy.valueProperty().addListener((obs, oldVal, newVal) -> applySort(newVal));
	}

	@FXML
	private void handleClearSearch(ActionEvent event) {
		if (txtSearchProfiles != null) {
			txtSearchProfiles.clear();
		}
	}

	private void applyFilter(String searchText) {
		if (filteredProfiles == null) return;
		String lSearch = (searchText == null) ? "" : searchText.toLowerCase().trim();
		filteredProfiles.setPredicate(profile -> {
			if (lSearch.isEmpty()) return true;
			String name = profile.getName();
			return name != null && name.toLowerCase().contains(lSearch);
		});
	}

	private void applySort(String sortBy) {
		if (sortedProfiles == null || sortBy == null) return;
		// MUST unbind before calling setComparator — a bound SortedList throws:
		// "SortedList.comparator: A bound value cannot be set."
		sortedProfiles.comparatorProperty().unbind();
		switch (sortBy) {
			case "Priority":
				sortedProfiles.setComparator(Comparator.comparingLong(p -> p.getPriority() == null ? Long.MAX_VALUE : p.getPriority()));
				break;
			case "Status":
				sortedProfiles.setComparator(Comparator.comparing(p -> p.getStatus() == null ? "" : p.getStatus()));
				break;
			case "Emulator":
				sortedProfiles.setComparator(Comparator.comparing(p -> p.getEmulatorNumber() == null ? "" : String.valueOf(p.getEmulatorNumber())));
				break;
			default: // Name — re-bind to table so column-header clicking still works
				sortedProfiles.comparatorProperty().bind(tableviewLogMessages.comparatorProperty());
				break;
		}
	}

	@FXML
	private void handleColumnSettings(ActionEvent event) {
		ContextMenu menu = new ContextMenu();

		addColumnToggle(menu, "Enabled", columnEnabled);
		addColumnToggle(menu, "Emulator", columnEmulatorNumber);
		addColumnToggle(menu, "Furnace Level", columnFurnaceLevel);
		addColumnToggle(menu, "Name", columnProfileName);
		addColumnToggle(menu, "Priority", columnPriority);
		addColumnToggle(menu, "Status", columnStatus);
		addColumnToggle(menu, "Stamina", columnStamina);
		addColumnToggle(menu, "Actions", columnDelete);

		menu.show(btnColumnSettings,
				javafx.geometry.Side.BOTTOM,
				0, 4);
	}

	private void addColumnToggle(ContextMenu menu, String label, TableColumn<?, ?> column) {
		if (column == null) return;
		CheckMenuItem item = new CheckMenuItem(label);
		item.setSelected(column.isVisible());
		item.setOnAction(e -> column.setVisible(item.isSelected()));
		menu.getItems().add(item);
	}

	private void initializeTableView() {
		profiles = FXCollections.observableArrayList();
		filteredProfiles = new FilteredList<>(profiles);

		columnProfileName.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
		columnEmulatorNumber.setCellValueFactory(cellData -> cellData.getValue().emulatorNumberProperty());
		columnPriority.setCellValueFactory(cellData -> cellData.getValue().priorityProperty().asObject());
		columnStatus.setCellValueFactory(cellData -> cellData.getValue().statusProperty());

		// Furnace Level column — display "-" placeholder (model may not have this field yet)
		if (columnFurnaceLevel != null) {
			columnFurnaceLevel.setCellFactory(col -> new TableCell<ProfileAux, String>() {
				@Override
				protected void updateItem(String item, boolean empty) {
					super.updateItem(item, empty);
					setText(empty ? null : "-");
					setAlignment(Pos.CENTER);
				}
			});
		}

		// Stamina column — display "-" placeholder
		if (columnStamina != null) {
			columnStamina.setCellFactory(col -> new TableCell<ProfileAux, String>() {
				@Override
				protected void updateItem(String item, boolean empty) {
					super.updateItem(item, empty);
					setText(empty ? null : "-");
					setAlignment(Pos.CENTER);
				}
			});
		}

		// Add double-click event handler to open edit dialog
		tableviewLogMessages.setRowFactory(tv -> {
			javafx.scene.control.TableRow<ProfileAux> row = new javafx.scene.control.TableRow<>();
			row.setOnMouseClicked(event -> {
				if (event.getClickCount() == 2 && (!row.isEmpty())) {
					ProfileAux selectedProfile = row.getItem();
					profileManagerActionController.showEditProfileDialog(selectedProfile, tableviewLogMessages);
				}
			});
			return row;
		});

		columnDelete.setCellFactory(new Callback<TableColumn<ProfileAux, Void>, TableCell<ProfileAux, Void>>() {
			@Override
			public TableCell<ProfileAux, Void> call(TableColumn<ProfileAux, Void> param) {
				return new TableCell<ProfileAux, Void>() {
					private final Button btnDelete = new Button();
					private final Button btnLoad = new Button();
					private final Button btnDuplicate = new Button();

					{
						FontIcon iconDelete = new FontIcon("mdi2c-close");
						iconDelete.setIconSize(16);
						iconDelete.setIconColor(Color.web("#f85149")); // red X
						btnDelete.setGraphic(iconDelete);
						btnDelete.getStyleClass().add("action-icon-button");
						btnDelete.setTooltip(new Tooltip("Delete Profile"));

						FontIcon iconDuplicate = new FontIcon("mdi2c-content-copy");
						iconDuplicate.setIconSize(16);
						iconDuplicate.setIconColor(Color.web("#388bfd")); // blue copy
						btnDuplicate.setGraphic(iconDuplicate);
						btnDuplicate.getStyleClass().add("action-icon-button");
						btnDuplicate.setTooltip(new Tooltip("Duplicate Profile"));

						FontIcon iconLoad = new FontIcon("mdi2p-play");
						iconLoad.setIconSize(22);
						iconLoad.setIconColor(Color.web("#2ea043")); // green play
						btnLoad.setGraphic(iconLoad);
						btnLoad.getStyleClass().add("action-icon-button");
						btnLoad.setTooltip(new Tooltip("Load Profile"));

						btnDuplicate.setOnAction(e -> {
							System.out.println("User clicked duplicate. Add actual duplication logic if needed.");
						});

						btnDelete.setOnAction((ActionEvent event) -> {
							if (getTableView().getItems().size() <= 1) {
								Alert alert = new Alert(Alert.AlertType.WARNING);
								alert.setTitle("WARNING");
								alert.setHeaderText(null);
								alert.setContentText("You must have at least one profile.");
								alert.showAndWait();
								return;
							}

							ProfileAux currentProfile = getTableView().getItems().get(getIndex());
							boolean deletionResult = profileManagerActionController.deleteProfile(new DTOProfiles(currentProfile.getId()));

							Alert alert;
							if (deletionResult) {
								alert = new Alert(Alert.AlertType.INFORMATION);
								alert.setTitle("SUCCESS");
								alert.setHeaderText(null);
								alert.setContentText("Profile deleted successfully.");
								loadProfiles();
							} else {
								alert = new Alert(Alert.AlertType.ERROR);
								alert.setTitle("ERROR");
								alert.setHeaderText(null);
								alert.setContentText("Error deleting profile.");
							}
							alert.showAndWait();
						});

						btnLoad.setOnAction((ActionEvent event) -> {
							ProfileAux currentProfile = getTableView().getItems().get(getIndex());
							loadedProfileId = currentProfile.getId();
							notifyProfileLoadListeners(currentProfile);
						});
					}

					@Override
					protected void updateItem(Void item, boolean empty) {
						super.updateItem(item, empty);
						if (empty) {
							setGraphic(null);
						} else {
							HBox buttonContainer = new HBox(5, btnLoad, btnDuplicate, btnDelete);
							buttonContainer.setAlignment(Pos.CENTER);
							setGraphic(buttonContainer);
						}
					}
				};
			}
		});

		columnEnabled.setCellValueFactory(cellData -> cellData.getValue().enabledProperty());

		columnEnabled.setCellFactory(col -> new TableCell<ProfileAux, Boolean>() {

			private final ToggleButton toggleButton = new ToggleButton();
			private final StackPane switchContainer;
			private final Circle knob;
			private final Rectangle background;

			{
				background = new Rectangle(32, 16, Color.web("#3b3f4c"));
				background.setArcWidth(16);
				background.setArcHeight(16);

				knob = new Circle(6, Color.web("#1a1c24"));
				knob.setTranslateX(-8);

				switchContainer = new StackPane(background, knob);
				switchContainer.setMinSize(40, 20);
				switchContainer.setMaxSize(40, 20);
				switchContainer.setAlignment(Pos.CENTER);
				switchContainer.setOnMouseClicked(event -> toggleSwitch());

				toggleButton.setOnAction(event -> {
					ProfileAux currentProfile = getTableView().getItems().get(getIndex());
					boolean newValue = toggleButton.isSelected();
					currentProfile.setEnabled(newValue);
					animateSwitch(newValue);
				});
			}

			private void toggleSwitch() {
				boolean newValue = !toggleButton.isSelected();
				toggleButton.setSelected(newValue);
				animateSwitch(newValue);

				ProfileAux currentProfile = getTableView().getItems().get(getIndex());
				if (currentProfile != null) {
					currentProfile.setEnabled(newValue);
					profileManagerActionController.saveProfile(currentProfile);
				}
			}

			private void animateSwitch(boolean isOn) {
				TranslateTransition slide = new TranslateTransition(Duration.millis(180), knob);
				slide.setToX(isOn ? 8 : -8);
				background.setFill(isOn ? Color.web("#ffcd53") : Color.web("#3b3f4c"));
				slide.play();
			}

			@Override
			protected void updateItem(Boolean item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setGraphic(null);
				} else {
					toggleButton.setSelected(item);
					background.setFill(item ? Color.web("#ffcd53") : Color.web("#3b3f4c"));
					knob.setTranslateX(item ? 8 : -8);
					setGraphic(switchContainer);
					setAlignment(Pos.CENTER);
				}
			}
		});

		sortedProfiles = new SortedList<>(filteredProfiles);
		// Bind to table comparator by default so column-click sorting works
		sortedProfiles.comparatorProperty().bind(tableviewLogMessages.comparatorProperty());
		tableviewLogMessages.setItems(sortedProfiles);
	}

	@FXML
	void handleButtonAddProfile(ActionEvent event) {
		profileManagerActionController.showNewProfileDialog();
	}

	@FXML
	void handleButtonBulkUpdateProfiles(ActionEvent event) {
		profileManagerActionController.showBulkUpdateDialog(loadedProfileId, profiles, btnBulkUpdate);
	}

	public void loadProfiles() {
		profileManagerActionController.loadProfiles(dtoProfiles -> {
			Platform.runLater(() -> {
				profiles.clear();
				dtoProfiles.forEach(dtoProfile -> {
					ProfileAux profileAux = new ProfileAux(dtoProfile.getId(), dtoProfile.getName(), dtoProfile.getEmulatorNumber(), dtoProfile.getEnabled(), dtoProfile.getPriority(), "NOT RUNNING", dtoProfile.getReconnectionTime(), dtoProfile.getCharacterId(), dtoProfile.getCharacterName(), dtoProfile.getCharacterAllianceCode(), dtoProfile.getCharacterServer());
					dtoProfile.getConfigs().forEach(config -> {
						profileAux.getConfigs().add(new ConfigAux(config.getConfigurationName(), config.getValue()));
					});
					profiles.add(profileAux);
				});

				if (!profiles.isEmpty()) {
					ProfileAux selectedProfile = profiles.stream().filter(p -> p.getId().equals(loadedProfileId)).findFirst().orElse(profiles.get(0));
					notifyProfileLoadListeners(selectedProfile);
					loadedProfileId = selectedProfile.getId();
				}

				// Re-apply sort only for non-default (non-Name) sorts.
				// When sortBy is "Name", the table binding already handles ordering —
				// calling applySort("Name") would re-bind an already-bound property and crash.
				String currentSort = comboBoxSortBy != null ? comboBoxSortBy.getValue() : null;
				if (currentSort != null && !currentSort.equals("Name")) {
					applySort(currentSort);
				}
			});
		});
	}

	private void handleProfileDataChange(DTOProfiles dto) {
		try {
			if (dto == null) {
				loadProfiles();
				return;
			}

			if (profiles == null || profiles.isEmpty()) {
				loadProfiles();
				return;
			}

			ProfileAux target = profiles.stream()
					.filter(p -> p.getId().equals(dto.getId()))
					.findFirst()
					.orElse(null);

			if (target == null) {
				loadProfiles();
				return;
			}

			if (dto.getName() != null) target.setName(dto.getName());
			if (dto.getEmulatorNumber() != null) target.setEmulatorNumber(dto.getEmulatorNumber());
			if (dto.getPriority() != null) target.setPriority(dto.getPriority());
			if (dto.getEnabled() != null) target.setEnabled(dto.getEnabled());
			if (dto.getReconnectionTime() != null) target.setReconnectionTime(dto.getReconnectionTime());
			if (dto.getCharacterId() != null) target.setCharacterId(dto.getCharacterId());
			if (dto.getCharacterName() != null) target.setCharacterName(dto.getCharacterName());
			if (dto.getCharacterAllianceCode() != null) target.setCharacterAllianceCode(dto.getCharacterAllianceCode());
			if (dto.getCharacterServer() != null) target.setCharacterServer(dto.getCharacterServer());

			if (dto.getConfigs() == null || dto.getConfigs().isEmpty()) {
				loadProfiles();
				return;
			}

			dto.getConfigs().forEach(cfgDto -> {
				ConfigAux existing = target.getConfigs().stream()
						.filter(c -> c.getName().equals(cfgDto.getConfigurationName()))
						.findFirst()
						.orElse(null);
				if (existing != null) {
					existing.setValue(cfgDto.getValue());
				} else {
					target.getConfigs().add(new ConfigAux(cfgDto.getConfigurationName(), cfgDto.getValue()));
				}
			});

			if (tableviewLogMessages != null) {
				tableviewLogMessages.refresh();
			}

            if (target.getId().equals(loadedProfileId)) {
				notifyProfileLoadListeners(target);
			}
		} catch (Exception ex) {
			loadProfiles();
		}
	}

	public void addProfileLoadListener(IProfileLoadListener moduleController) {
		if (profileLoadListeners == null) {
			profileLoadListeners = new ArrayList<>();
		}
		profileLoadListeners.add(moduleController);
	}

	public javafx.collections.ObservableList<ProfileAux> getProfiles() {
		return profiles;
	}

	public void setLoadedProfileId(Long profileId) {
		this.loadedProfileId = profileId;
	}

	public Long getLoadedProfileId() {
		return loadedProfileId;
	}

	public void notifyProfileLoadListeners(ProfileAux currentProfile) {
		if (profileLoadListeners != null) {
			profileLoadListeners.forEach(listener -> listener.onProfileLoad(currentProfile));
		}
	}

	public void handleProfileStatusChange(DTOProfileStatus status) {
		Platform.runLater(() -> {
			profiles.stream().filter(p -> p.getId() == status.getId()).forEach(p -> {
				p.setStatus(status.getStatus());
			});
			tableviewLogMessages.refresh();
			tableviewLogMessages.sort();
		});
	}

	@Override
	public void notifyProfileChange(EnumConfigurationKey key, Object value) {
		try {
			ProfileAux loadedProfile = profiles.stream().filter(profile -> profile.getId().equals(loadedProfileId)).findFirst().orElse(null);

			if (loadedProfile == null) {
				return;
			}

			loadedProfile.setConfig(key, value);
			profileQueueExecutor.submit(() -> {
				profileManagerActionController.saveProfile(loadedProfile);
			});

		} catch (Exception e) {
			e.printStackTrace();
			ServLogs.getServices().appendLog(EnumTpMessageSeverity.ERROR, "Profile Manager", "-", "Error while saving profile: " + e.getMessage());
		}
	}

}
