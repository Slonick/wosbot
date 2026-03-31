package cl.camodev.wosbot.console.view;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import cl.camodev.wosbot.console.controller.ConsoleLogActionController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.model.LogMessageAux;
import cl.camodev.wosbot.ot.DTOLogMessage;
import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.IProfileDataChangeListener;
import cl.camodev.wosbot.serv.impl.ServConfig;
import cl.camodev.wosbot.serv.impl.ServProfiles;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;

public class ConsoleLogLayoutController implements IProfileDataChangeListener {

	@FXML
	private Button buttonClearLogs;
	
	@FXML
	private Button buttonOpenLogFolder;

	@FXML
	private CheckBox checkboxDebug;

	@FXML
	private ComboBox<String> comboBoxProfileFilter;

	@FXML
	private ComboBox<String> comboBoxLevelFilter;

	@FXML
	private TextField txtSearchLogs;

	@FXML
	private Button btnClearSearch;

	@FXML
	private TableView<LogMessageAux> tableviewLogMessages;

	@FXML
	private TableColumn<LogMessageAux, String> columnMessage;

	@FXML
	private TableColumn<LogMessageAux, String> columnTimeStamp;

	@FXML
	private TableColumn<LogMessageAux, String> columnProfile;

	@FXML
	private TableColumn<LogMessageAux, String> columnTask;

	@FXML
	private TableColumn<LogMessageAux, String> columnLevel;

	private ObservableList<LogMessageAux> logMessages;
	private FilteredList<LogMessageAux> filteredLogMessages;

	@FXML
	private void initialize() {
		new ConsoleLogActionController(this);
		logMessages = FXCollections.observableArrayList();
		filteredLogMessages = new FilteredList<>(logMessages);

        checkboxDebug.setSelected(Optional
                .ofNullable(ServConfig.getServices().getGlobalConfig())
                .map(cfg -> cfg.get(EnumConfigurationKey.BOOL_DEBUG.name()))
                .map(Boolean::parseBoolean)
                .orElse(Boolean.parseBoolean(EnumConfigurationKey.BOOL_DEBUG.getDefaultValue())));

        checkboxDebug.setOnAction(e -> {
            ServScheduler.getServices().saveEmulatorPath(EnumConfigurationKey.BOOL_DEBUG.name(), String.valueOf(checkboxDebug.isSelected()));
        });
		
		columnTimeStamp.setCellValueFactory(cellData -> cellData.getValue().timeStampProperty());
		columnMessage.setCellValueFactory(cellData -> cellData.getValue().messageProperty());
		columnLevel.setCellValueFactory(cellData -> cellData.getValue().severityProperty());
		columnTask.setCellValueFactory(cellData -> cellData.getValue().taskProperty());
		columnProfile.setCellValueFactory(cellData -> cellData.getValue().profileProperty());
		
		columnMessage.setCellFactory(column -> new TableCell<>() {
			private final Text text = new Text();
			{
				setGraphic(text);
				text.wrappingWidthProperty().bind(widthProperty());
				text.fillProperty().bind(textFillProperty());
				setPrefHeight(USE_COMPUTED_SIZE);
			}
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					text.setText(null);
				} else {
					text.setText(item);
				}
			}
		});

        columnLevel.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("log-level-info", "status-stopped");
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    if ("INFO".equalsIgnoreCase(item)) {
                        getStyleClass().add("log-level-info");
                    } else if ("ERROR".equalsIgnoreCase(item)) {
                        getStyleClass().add("status-stopped");
                    }
                }
            }
        });

        columnTask.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("log-module-text");
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().add("log-module-text");
                }
            }
        });

        columnProfile.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("log-module-text");
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    getStyleClass().add("log-module-text");
                }
            }
        });
		
		tableviewLogMessages.setItems(filteredLogMessages);
		tableviewLogMessages.setPlaceholder(new Label("NO LOGS"));

		// Initialize level filter
		initializeLevelFilter();
		
		// Initialize profile filter
		initializeProfileFilter();
		
		// Set up filter listeners
		setupFilterListeners();
		ServProfiles.getServices().addProfileDataChangeListener(this);
	}

	@FXML
	void handleButtonClearLogs(ActionEvent event) {
		Platform.runLater(() -> {
			logMessages.clear();
		});
	}

	@FXML
	void handleClearSearch(ActionEvent event) {
		if (txtSearchLogs != null) {
			txtSearchLogs.clear();
		}
	}
	
	@FXML
	void handleButtonOpenLogFolder(ActionEvent event) {
		try {
			File logsDir = new File("log");
			if (!logsDir.exists()) {
				logsDir.mkdirs();
			}
			Desktop.getDesktop().open(logsDir);
		} catch (IOException e) {
			System.err.println("Error opening logs folder: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void initializeLevelFilter() {
		ObservableList<String> levels = FXCollections.observableArrayList(
				"All levels", "INFO", "DEBUG", "WARNING", "ERROR"
		);
		comboBoxLevelFilter.setItems(levels);
		comboBoxLevelFilter.getSelectionModel().selectFirst();
	}

	private void initializeProfileFilter() {
		try {
			List<DTOProfiles> profiles = ServProfiles.getServices().getProfiles();
			if (profiles != null) {
				ObservableList<String> profileNames = FXCollections.observableArrayList();
				profileNames.add("All profiles");
				profiles.forEach(profile -> profileNames.add(profile.getName()));
				comboBoxProfileFilter.setItems(profileNames);

				String currentSelection = comboBoxProfileFilter.getSelectionModel().getSelectedItem();
				if (currentSelection != null && profileNames.contains(currentSelection)) {
					comboBoxProfileFilter.getSelectionModel().select(currentSelection);
				} else {
					comboBoxProfileFilter.getSelectionModel().selectFirst();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void setupFilterListeners() {
		comboBoxProfileFilter.valueProperty().addListener((obs, oldVal, newVal) -> updateLogFilter());
		comboBoxLevelFilter.valueProperty().addListener((obs, oldVal, newVal) -> updateLogFilter());
		if (txtSearchLogs != null) {
			txtSearchLogs.textProperty().addListener((obs, oldVal, newVal) -> updateLogFilter());
		}
	}

	private void updateLogFilter() {
		filteredLogMessages.setPredicate(logMessage -> {
			// Search text filter
			if (txtSearchLogs != null) {
				String search = txtSearchLogs.getText();
				if (search != null && !search.isEmpty()) {
					String lSearch = search.toLowerCase();
					String msg = logMessage.messageProperty().get();
					String task = logMessage.taskProperty().get();
					String profile = logMessage.profileProperty().get();
					boolean matchesSearch = (msg != null && msg.toLowerCase().contains(lSearch))
							|| (task != null && task.toLowerCase().contains(lSearch))
							|| (profile != null && profile.toLowerCase().contains(lSearch));
					if (!matchesSearch) return false;
				}
			}

			// Profile filter
			String selectedProfile = comboBoxProfileFilter.getValue();
			if (selectedProfile != null && !selectedProfile.isEmpty() && !"All profiles".equals(selectedProfile)) {
				String messageProfile = logMessage.profileProperty().get();
				if (messageProfile == null || !messageProfile.equals(selectedProfile)) {
					return false;
				}
			}

			// Level filter
			String selectedLevel = comboBoxLevelFilter.getValue();
			if (selectedLevel != null && !selectedLevel.isEmpty() && !"All levels".equals(selectedLevel)) {
				String msgLevel = logMessage.severityProperty().get();
				if (msgLevel == null || !msgLevel.equalsIgnoreCase(selectedLevel)) {
					return false;
				}
			}

			return true;
		});
	}

	public void appendMessage(DTOLogMessage dtoMessage) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
		String formattedDate = LocalDateTime.now().format(formatter);

		if (!checkboxDebug.isSelected() && dtoMessage.getSeverity() == EnumTpMessageSeverity.DEBUG) {
			return;
		}

		Platform.runLater(() -> {
			logMessages.add(0, new LogMessageAux(formattedDate, dtoMessage.getSeverity().toString(), dtoMessage.getMessage(), dtoMessage.getTask(), dtoMessage.getProfile()));

			if (logMessages.size() > 600) {
				logMessages.remove(logMessages.size() - 1);
			}
		});
	}

	@Override
	public void onProfileDataChanged(DTOProfiles profile) {
		Platform.runLater(() -> {
			initializeProfileFilter();
		});
	}

}
