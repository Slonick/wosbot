package cl.camodev.wosbot.launcher.view;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import cl.camodev.utiles.ImageSearchUtil;
import cl.camodev.wosbot.alliance.view.AllianceLayoutController;
import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.alliancechampionship.view.AllianceChampionshipLayoutController;
import cl.camodev.wosbot.bear.view.BearTrapLayoutController;
import cl.camodev.wosbot.beasthunting.view.BeastHuntingLayoutController;
import cl.camodev.wosbot.chieforder.view.ChiefOrderLayoutController;
import cl.camodev.wosbot.city.view.CityEventsExtraLayoutController;
import cl.camodev.wosbot.city.view.CityEventsLayoutController;
import cl.camodev.wosbot.city.view.CityUpgradesLayoutController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;
import cl.camodev.wosbot.console.view.ConsoleLogLayoutController;
import cl.camodev.wosbot.debugging.view.DebuggingLayoutController;
import cl.camodev.wosbot.dummy.view.DummyLayoutController;
import cl.camodev.wosbot.emulator.EmulatorType;
import cl.camodev.wosbot.emulator.view.EmuConfigLayoutController;
import cl.camodev.wosbot.events.view.EventsLayoutController;
import cl.camodev.wosbot.experts.view.ExpertsLayoutController;
import cl.camodev.wosbot.fishing.view.FishingLayoutController;
import cl.camodev.wosbot.gather.view.GatherLayoutController;
import cl.camodev.wosbot.giftcode.view.GiftcodeLayoutController;
import cl.camodev.wosbot.intel.view.IntelLayoutController;
import cl.camodev.wosbot.mobilization.view.MobilizationLayoutController;
import cl.camodev.wosbot.ot.DTOBotState;
import cl.camodev.wosbot.ot.DTOLogMessage;
import cl.camodev.wosbot.ot.DTOQueueProfileState;
import cl.camodev.wosbot.pets.view.PetsLayoutController;
import cl.camodev.wosbot.polarterror.view.PolarTerrorLayoutController;
import cl.camodev.wosbot.profile.model.IProfileChangeObserver;
import cl.camodev.wosbot.profile.model.IProfileLoadListener;
import cl.camodev.wosbot.profile.model.IProfileObserverInjectable;
import cl.camodev.wosbot.profile.model.ProfileAux;
import cl.camodev.wosbot.profile.view.ProfileManagerLayoutController;
import cl.camodev.wosbot.ot.DTOQueueState;
import cl.camodev.wosbot.serv.IStaminaChangeListener;
import cl.camodev.wosbot.serv.impl.ServConfig;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.serv.impl.StaminaService;
import cl.camodev.wosbot.shop.view.ShopLayoutController;
import cl.camodev.wosbot.taskmanager.view.TaskManagerLayoutController;
import cl.camodev.wosbot.skiptutorial.view.SkipTutorialLayoutController;
import cl.camodev.wosbot.training.view.TrainingLayoutController;
import cl.camodev.wosbot.research.view.ResearchLayoutController;
import cl.camodev.wosbot.character.view.CharacterLayoutController;
import cl.camodev.wosbot.statistics.view.StatisticsLayoutController;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.animation.*;
import javafx.util.Duration;
import cl.camodev.wosbot.alliance.view.AllianceShopController;
import cl.camodev.wosbot.telegram.view.TelegramLayoutController;
import cl.camodev.wosbot.serv.impl.TelegramBotService;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignG;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignB;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.materialdesign2.MaterialDesignV;
import org.kordamp.ikonli.Ikon;

public class LauncherLayoutController implements IProfileLoadListener, IStaminaChangeListener {

    private static LauncherLayoutController instance;

    private final Map<String, Object> moduleControllers = new HashMap<>();
    @FXML
    private VBox buttonsContainer;
    @FXML
    private VBox logoContainer;
    @FXML
    private StackPane centerStack;
    @FXML
    private VBox pinnedButtonsContainer;
    @FXML
    private Button buttonStartStop;
    @FXML
    private SplitMenuButton buttonPauseResume;
    @FXML
    private MenuItem menuToggleAllQueues;
    @FXML
    private AnchorPane mainContentPane;
    @FXML
    private Label labelRunTime;
    @FXML
    private Label labelVersion;
    @FXML
    private ComboBox<ProfileAux> profileComboBox;
    @FXML
    private TextField navSearchField;
    @FXML
    private Label logoWhiteout;
    @FXML
    private Label logoSurvival;
    @FXML
    private VBox sidebarHeader;

    @FXML
    private FontIcon iconDiscord;

    @FXML
    private FontIcon iconGithub;

    private Stage stage;
    private boolean quickNavVisible = false;
    private ScrollPane quickNavOverlay;
    private final List<QuickNavEntry> quickNavEntries = new ArrayList<>();
    private VBox searchOverlay;
    private LauncherActionController actionController;
    private ConsoleLogLayoutController consoleLogLayoutController;
    private ProfileManagerLayoutController profileManagerLayoutController;
    private boolean estado = false;
    private boolean updatingComboBox = false;
    private ProfileAux currentProfile = null;
    private boolean allQueuesPaused = false;
    private final Map<Long, DTOQueueProfileState> activeQueueStates = new HashMap<>();
    private Timeline autoStartTimeline;
    private int autoStartSecondsRemaining;
    private boolean isStartup = true;

    public LauncherLayoutController(Stage stage) {
        this.stage = stage;
        instance = this;
        StaminaService.getServices().addStaminaChangeListener(this);
    }

    public static LauncherLayoutController getInstance() {
        return instance;
    }

    @FXML
    private void initialize() {
        initializeDiscordBot();
        initializeEmulatorManager();
        initializeLogModule();
        initializeProfileModule();
        initializePinnedModules();
        initializeProfileComboBox();
        initializeModules();
        initializeExternalLibraries();
        initializeTelegramBot();
        showVersion();
        buttonStartStop.setDisable(false);
        buttonPauseResume.setDisable(true);
        configurePauseMenu();
        scheduleAutoStart();
        initializeQuickNav();
        initializeSearch();
        setupSocialIcons();
    }

    private void setupSocialIcons() {
        if (iconDiscord != null) {
            iconDiscord.setIconCode(MaterialDesignD.DISCORD);
        }
        if (iconGithub != null) {
            iconGithub.setIconCode(MaterialDesignG.GITHUB);
        }
    }

    private void initializeTelegramBot() {
        HashMap<String, String> cfg = ServConfig.getServices().getGlobalConfig();
        if (cfg == null)
            return;
        boolean enabled = Boolean.parseBoolean(
                cfg.getOrDefault(EnumConfigurationKey.TELEGRAM_BOT_ENABLED_BOOL.name(), "false"));
        String token = cfg.getOrDefault(EnumConfigurationKey.TELEGRAM_BOT_TOKEN_STRING.name(), "");
        String chatIdStr = cfg.getOrDefault(EnumConfigurationKey.TELEGRAM_ALLOWED_CHAT_ID_STRING.name(), "");
        if (enabled && !token.isBlank()) {
            long chatId = chatIdStr.isBlank() ? 0L : Long.parseLong(chatIdStr);
            TelegramBotService.getInstance().start(token, chatId);
            
            // Auto-start watcher
            cl.camodev.wosbot.serv.impl.TelegramWatcherLauncher.startWatcherIfNotRunning();
        }
    }

    private void showVersion() {
        String version = getVersion();
        labelVersion.setText("Version: " + version);
    }

    private String getVersion() {
        Package pkg = getClass().getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null) {
            return pkg.getImplementationVersion();
        }
        try {
            Path parentPomPath = Paths.get("..", "pom.xml");
            if (!Files.exists(parentPomPath)) {
                parentPomPath = Paths.get("pom.xml");
            }
            List<String> lines = Files.readAllLines(parentPomPath);
            String revision = null;
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("<revision>") && line.endsWith("</revision>")) {
                    revision = line.replace("<revision>", "").replace("</revision>", "").trim();
                    break;
                }
            }
            if (revision != null) {
                return revision;
            }
        } catch (Exception e) {
            // Ignore error
        }
        return "Unknown";
    }

    private void initializeEmulatorManager() {
        HashMap<String, String> globalConfig = ServConfig.getServices().getGlobalConfig();

        if (globalConfig == null || globalConfig.isEmpty()) {
            globalConfig = new HashMap<>();
        }

        String savedActiveEmulator = globalConfig.get(EnumConfigurationKey.CURRENT_EMULATOR_STRING.name());
        EmulatorType activeEmulator = null;
        if (savedActiveEmulator != null && !savedActiveEmulator.isEmpty()) {
            try {
                activeEmulator = EmulatorType.valueOf(savedActiveEmulator);
            } catch (IllegalArgumentException e) {
                // Ignore Invalid Enum constant
            }
        }
        boolean activeEmulatorValid = false;

        if (activeEmulator != null) {
            String activePath = globalConfig.get(activeEmulator.getConfigKey());
            if (activePath != null && new File(activePath).exists()) {
                activeEmulatorValid = true;
            } else {
                ServScheduler.getServices().saveEmulatorPath(activeEmulator.getConfigKey(), null);
            }
        }

        List<EmulatorType> foundEmulators = new ArrayList<>();
        for (EmulatorType emulator : EmulatorType.values()) {
            if (activeEmulator == emulator)
                continue;

            String emulatorPath = globalConfig.get(emulator.getConfigKey());
            if (emulatorPath != null && new File(emulatorPath).exists()) {
                foundEmulators.add(emulator);
            } else {
                File emulatorFile = new File(emulator.getDefaultPath());
                if (emulatorFile.exists()) {
                    ServScheduler.getServices().saveEmulatorPath(emulator.getConfigKey(), emulatorFile.getParent());
                    foundEmulators.add(emulator);
                }
            }
        }

        if (!activeEmulatorValid) {
            if (foundEmulators.size() == 1) {
                ServScheduler.getServices().saveEmulatorPath(EnumConfigurationKey.CURRENT_EMULATOR_STRING.name(),
                        foundEmulators.get(0).name());
                return;
            } else if (foundEmulators.isEmpty()) {
                selectEmulatorManually();
            } else {
                EmulatorType selectedEmulator = askUserForPreferredEmulator(foundEmulators);
                ServScheduler.getServices().saveEmulatorPath(EnumConfigurationKey.CURRENT_EMULATOR_STRING.name(),
                        selectedEmulator.name());
            }
        }
    }

    private EmulatorType askUserForPreferredEmulator(List<EmulatorType> emulators) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Select Emulator");
        alert.setHeaderText("Multiple emulators found. Please select which one to use.");

        List<ButtonType> buttons = new ArrayList<>();
        for (EmulatorType emulator : emulators) {
            buttons.add(new ButtonType(emulator.getDisplayName()));
        }
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        buttons.add(cancelButton);

        alert.getButtonTypes().setAll(buttons);
        Optional<ButtonType> result = alert.showAndWait();

        for (EmulatorType emulator : emulators) {
            if (result.isPresent() && result.get().getText().equals(emulator.getDisplayName())) {
                return emulator;
            }
        }

        showErrorAndExit("No emulator selected. The application will close.");
        return null;
    }

    private void selectEmulatorManually() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Emulator Executable");

        FileChooser.ExtensionFilter exeFilter = new FileChooser.ExtensionFilter("Emulator Executable", "*.exe");
        fileChooser.getExtensionFilters().add(exeFilter);
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            for (EmulatorType emulator : EmulatorType.values()) {
                if (selectedFile.getName().equals(new File(emulator.getDefaultPath()).getName())) {
                    ServScheduler.getServices().saveEmulatorPath(emulator.getConfigKey(), selectedFile.getParent());
                    ServScheduler.getServices().saveEmulatorPath(EnumConfigurationKey.CURRENT_EMULATOR_STRING.name(),
                            emulator.name());
                    return;
                }
            }
            showErrorAndExit("Invalid emulator file selected. Please select a valid emulator executable.");
        } else {
            showErrorAndExit("No emulator selected. The application will close.");
        }
    }

    private void showErrorAndExit(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("ERROR");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
        System.exit(0);
    }

    private void initializeDiscordBot() {
        // ServDiscord.getServices();
    }

    private void initializeLogModule() {
        actionController = new LauncherActionController(this);
        consoleLogLayoutController = new ConsoleLogLayoutController();
    }

    private void initializeProfileModule() {
        profileManagerLayoutController = new ProfileManagerLayoutController();
        actionController.setProfileManagerController(profileManagerLayoutController);
    }

    // ==================== PINNED MODULES ====================

    private void initializePinnedModules() {
        // Control tab: Logs + Profiles + Task Manager
        ConsoleLogLayoutController logsCtrl = consoleLogLayoutController;
        ProfileManagerLayoutController profilesCtrl = profileManagerLayoutController;
        TaskManagerLayoutController taskCtrl = new TaskManagerLayoutController();

        Parent logsPane = loadNode("ConsoleLogLayout", logsCtrl);
        Parent profilesPane = loadNode("ProfileManagerLayout", profilesCtrl);
        Parent taskPane = loadNode("TaskManagerLayout", taskCtrl);

        // Show Logs by default on startup
        setMainContent(logsPane);

        TabPane controlTabs = new TabPane();
        controlTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        controlTabs.getTabs().addAll(
                makeTab("Logs", logsPane),
                makeTab("Profiles", profilesPane),
                makeTab("Tasks", taskPane)
        );
        controlTabs.setMaxWidth(Double.MAX_VALUE);
        controlTabs.setMaxHeight(Double.MAX_VALUE);

        addPinnedButton("Control", MaterialDesignC.CONTROLLER_CLASSIC, controlTabs);

        // Config pinned button
        EmuConfigLayoutController configCtrl = new EmuConfigLayoutController();
        Parent configPane = loadNode("EmuConfigLayout", configCtrl);
        addPinnedButton("Config", MaterialDesignC.COG_OUTLINE, configPane);
    }

    private Tab makeTab(String title, Parent content) {
        Tab tab = new Tab(title, content);
        return tab;
    }

    private void setMainContent(Parent content) {
        mainContentPane.getChildren().clear();
        AnchorPane.setTopAnchor(content, 0.0);
        AnchorPane.setBottomAnchor(content, 0.0);
        AnchorPane.setLeftAnchor(content, 0.0);
        AnchorPane.setRightAnchor(content, 0.0);
        mainContentPane.getChildren().add(content);
    }

    private void initializeProfileComboBox() {
        configureComboCells();

        profileComboBox.setOnAction(event -> {
            if (!updatingComboBox) {
                ProfileAux selectedProfile = profileComboBox.getSelectionModel().getSelectedItem();
                if (selectedProfile != null) {
                    actionController.selectProfile(selectedProfile);
                }
            }
        });

        if (profileManagerLayoutController != null) {
            profileManagerLayoutController.addProfileLoadListener(new IProfileLoadListener() {
                @Override
                public void onProfileLoad(ProfileAux profile) {
                    Platform.runLater(() -> {
                        actionController.updateProfileComboBox();
                    });
                }
            });
        }

        Platform.runLater(() -> {
            actionController.loadProfilesIntoComboBox();
        });
    }

    public void updateComboBoxItems(javafx.collections.ObservableList<ProfileAux> profiles) {
        updatingComboBox = true;
        profileComboBox.getItems().clear();
        profileComboBox.getItems().addAll(profiles);
        configureComboCells();
        updatingComboBox = false;
    }

    private void configureComboCells() {
        profileComboBox.setCellFactory(listView -> new ListCell<ProfileAux>() {
            @Override
            protected void updateItem(ProfileAux profile, boolean empty) {
                super.updateItem(profile, empty);
                if (empty || profile == null) {
                    setText(null);
                } else {
                    setText(profile.getName() + " (Emulator: " + profile.getEmulatorNumber() + ")");
                }
            }
        });

        profileComboBox.setButtonCell(new ListCell<ProfileAux>() {
            @Override
            protected void updateItem(ProfileAux profile, boolean empty) {
                super.updateItem(profile, empty);
                if (empty || profile == null) {
                    setText(null);
                } else {
                    setText(profile.getName() + " (Emulator: " + profile.getEmulatorNumber() + ")");
                }
            }
        });
    }

    public ProfileAux getSelectedProfile() {
        return profileComboBox.getSelectionModel().getSelectedItem();
    }

    public void selectProfileInComboBox(ProfileAux profile) {
        updatingComboBox = true;
        profileComboBox.getSelectionModel().select(profile);
        updatingComboBox = false;
    }

    public void refreshProfileComboBox() {
        actionController.refreshProfileComboBox();
    }

    private void initializeExternalLibraries() {
        try {
            ImageSearchUtil.loadNativeLibrary("/native/opencv/opencv_java4110.dll");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initializeModules() {
        //@formatter:off
        List<ModuleDefinition> modules = Arrays.asList(
                new ModuleDefinition("DummyLayout",               "Dummy Task",           MaterialDesignD.DATABASE_REMOVE_OUTLINE,    DummyLayoutController::new),
                new ModuleDefinition("CityUpgradesLayout",        "City Upgrades",        MaterialDesignC.CITY_VARIANT_OUTLINE,       CityUpgradesLayoutController::new),
                new ModuleDefinition("CityEventsLayout",         "City Events",          MaterialDesignC.CALENDAR_OUTLINE,           CityEventsLayoutController::new),
                new ModuleDefinition("CityEventsExtraLayout",    "Extra City Events",    MaterialDesignC.CALENDAR_PLUS,              CityEventsExtraLayoutController::new),
                new ModuleDefinition("PolarTerrorLayout",        "Rally",                MaterialDesignF.FLAG_OUTLINE,               PolarTerrorLayoutController::new),
                new ModuleDefinition("ShopLayout",               "Shop",                 MaterialDesignS.STORE_OUTLINE,              ShopLayoutController::new),
                new ModuleDefinition("GatherLayout",             "Gather",               MaterialDesignP.PACKAGE_VARIANT,            GatherLayoutController::new),
                new ModuleDefinition("IntelLayout",              "Intel",                MaterialDesignB.BINOCULARS,                 IntelLayoutController::new),
                new ModuleDefinition("AllianceLayout",           "Alliance",             MaterialDesignA.ACCOUNT_GROUP_OUTLINE,      AllianceLayoutController::new),
                new ModuleDefinition("AllianceChampionshipLayout","Alliance Championship",MaterialDesignT.TROPHY_OUTLINE,            AllianceChampionshipLayoutController::new),
                new ModuleDefinition("AllianceShop",             "Alliance Shop",        MaterialDesignS.SHOPPING_OUTLINE,           AllianceShopController::new),
                new ModuleDefinition("AllianceMobilizationLayout","Alliance Mobilization",MaterialDesignA.ALARM_LIGHT_OUTLINE,       MobilizationLayoutController::new),
                new ModuleDefinition("BearTrapLayout",           "Bear Trap",            MaterialDesignT.TOOLS,                      BearTrapLayoutController::new),
                new ModuleDefinition("BeastHuntingLayout",       "Beast Hunting",        MaterialDesignP.PAW_OUTLINE,                BeastHuntingLayoutController::new),
                new ModuleDefinition("FishingLayout",            "Fishing Tournament",   MaterialDesignF.FISH,                       FishingLayoutController::new),
                new ModuleDefinition("TrainingLayout",           "Training",             MaterialDesignS.SWORD_CROSS,                TrainingLayoutController::new),
                new ModuleDefinition("ResearchLayout",           "Research",             MaterialDesignF.FLASK_OUTLINE,              ResearchLayoutController::new),
                new ModuleDefinition("PetsLayout",               "Pets",                 MaterialDesignP.PAW,                        PetsLayoutController::new),
                new ModuleDefinition("EventsLayout",             "Events",               MaterialDesignC.CALENDAR_STAR,              EventsLayoutController::new),
                new ModuleDefinition("ExpertsLayout",            "Experts",              MaterialDesignA.ACCOUNT_STAR_OUTLINE,       ExpertsLayoutController::new),
                new ModuleDefinition("ChiefOrderLayout",         "Chief Order",          MaterialDesignC.CROWN_OUTLINE,              ChiefOrderLayoutController::new),
                new ModuleDefinition("GiftcodeLayout",           "Get Giftcodes",        MaterialDesignG.GIFT_OUTLINE,               GiftcodeLayoutController::new),
                new ModuleDefinition("DebuggingLayout",          "Debugging",            MaterialDesignB.BUG_OUTLINE,                DebuggingLayoutController::new),
                new ModuleDefinition("TelegramLayout",           "Telegram",             MaterialDesignT.TELEGRAM,                   TelegramLayoutController::new),
                new ModuleDefinition("SkipTutorialLayout",       "Skip Tutorial",        MaterialDesignS.SKIP_NEXT_OUTLINE,          SkipTutorialLayoutController::new),
                new ModuleDefinition("CharacterLayout",          "Character",            MaterialDesignA.ACCOUNT_OUTLINE,            CharacterLayoutController::new),
                new ModuleDefinition("StatisticsLayout",         "Statistics",           MaterialDesignV.VIEW_DASHBOARD_OUTLINE,     StatisticsLayoutController::new)
        );
        //@formatter:on

        for (ModuleDefinition module : modules) {
            consoleLogLayoutController.appendMessage(
                    new DTOLogMessage(EnumTpMessageSeverity.INFO, "Loading module: " + module.buttonTitle(), "-", "-"));

            Object controller = module.createController(profileManagerLayoutController);
            moduleControllers.put(module.buttonTitle(), controller);
            addButton(module.fxmlName(), module.buttonTitle(), module.icon(), controller);

            if (controller instanceof IProfileLoadListener) {
                profileManagerLayoutController.addProfileLoadListener((IProfileLoadListener) controller);
            }
        }
        profileManagerLayoutController.addProfileLoadListener(this);
    }

    @Override
    public void onProfileLoad(ProfileAux profile) {
        this.currentProfile = profile;
        updateWindowTitle();
        selectProfileInComboBox(profile);
        refreshPauseMenuItems();
    }

    @Override
    public void onStaminaChanged(Long profileId, int newStamina) {
        if (currentProfile != null && currentProfile.getId().equals(profileId)) {
            updateWindowTitle();
        }
    }

    private void updateWindowTitle() {
        if (currentProfile == null) {
            return;
        }

        String version = getVersion();
        int stamina = StaminaService.getServices().getCurrentStamina(currentProfile.getId());
        String title = String.format("Whiteout Survival Bot v%s - %s [Stamina: %d]",
                version,
                currentProfile.getName(),
                stamina);

        Platform.runLater(() -> stage.setTitle(title));
    }

    public void onBotStateChange(DTOBotState botState) {
        if (botState != null) {
            if (botState.getRunning()) {
                cancelAutoStart();
                if (botState.getPaused() != null && botState.getPaused()) {
                    buttonStartStop.setText("Stop");
                    buttonStartStop.setDisable(false);
                    allQueuesPaused = true;
                    buttonPauseResume.setDisable(false);
                    estado = true;
                    updatePauseButtonState();
                    refreshPauseMenuItems();
                } else {
                    buttonStartStop.setText("Stop");
                    buttonStartStop.setDisable(false);
                    allQueuesPaused = false;
                    buttonPauseResume.setDisable(false);
                    estado = true;
                    updatePauseButtonState();
                    refreshPauseMenuItems();
                }
            } else {
                buttonStartStop.setText("Start Bot");
                buttonStartStop.setDisable(false);
                buttonPauseResume.setDisable(true);
                resetPauseStates();
                estado = false;
                isStartup = false;
                scheduleAutoStart();
            }
        }
    }

    public void onQueueStateChange(DTOQueueState queueState) {
        if (queueState == null) {
            return;
        }

        if (queueState.getActiveQueues() != null) {
            updateActiveQueueStates(queueState.getActiveQueues());
        }

        if (queueState.getProfileId() == null) {
            activeQueueStates.values().forEach(state -> state.setPaused(queueState.isPaused()));
        } else {
            DTOQueueProfileState profileState = activeQueueStates.get(queueState.getProfileId());
            if (profileState != null) {
                profileState.setPaused(queueState.isPaused());
            }
        }

        updateAggregatedPauseStates();

        if (estado && (!activeQueueStates.isEmpty() || queueState.getProfileId() != null)) {
            buttonPauseResume.setDisable(false);
        }

        refreshPauseMenuItems();
        updatePauseButtonState();
    }

    @FXML
    public void handleButtonStartStop(ActionEvent event) {
        cancelAutoStart();
        Thread startStopThread = Thread.ofVirtual().unstarted(() -> {
            if (!estado) {
                Platform.runLater(() -> {
                    buttonStartStop.setText("Starting...");
                    buttonStartStop.setDisable(true);
                });
                actionController.startBot();
            } else {
                Platform.runLater(() -> {
                    buttonStartStop.setText("Stopping...");
                    buttonStartStop.setDisable(true);
                    buttonPauseResume.setDisable(true);
                });
                isStartup = false;
                actionController.stopBot();
            }
        });
        startStopThread.setName("Start-Stop-Thread");
        startStopThread.start();
    }

    public void forceStartBot() {
        if (!estado) {
            cancelAutoStart();
            Thread startStopThread = Thread.ofVirtual().unstarted(() -> {
                Platform.runLater(() -> {
                    buttonStartStop.setText("Starting...");
                    buttonStartStop.setDisable(true);
                });
                actionController.startBot();
            });
            startStopThread.setName("Force-Start-Thread");
            startStopThread.start();
        }
    }

    @FXML
    public void handleButtonPauseResume(ActionEvent event) {
        toggleAllQueues();
    }

    @FXML
    private void handleToggleCurrentQueue(ActionEvent event) {
        toggleCurrentQueue();
    }

    @FXML
    private void handleToggleAllQueues(ActionEvent event) {
        toggleAllQueues();
    }

    private void handleToggleSpecificQueue(Long profileId) {
        toggleSpecificQueue(profileId, true);
    }

    private void toggleAllQueues() {
        if (!estado) return;
        if (!allQueuesPaused) {
            actionController.pauseAllQueues();
            setAllQueuesPausedLocally(true);
        } else {
            actionController.resumeAllQueues();
            setAllQueuesPausedLocally(false);
        }
    }

    private void toggleCurrentQueue() {
        if (!estado) return;
        ProfileAux selectedProfile = currentProfile != null ? currentProfile : getSelectedProfile();
        if (selectedProfile == null) {
            showProfileSelectionWarning();
            return;
        }
        toggleSpecificQueue(selectedProfile.getId(), true);
    }

    private void toggleSpecificQueue(Long profileId, boolean showWarnings) {
        if (!estado) return;
        if (profileId == null) {
            if (showWarnings) showQueueUnavailableWarning();
            return;
        }
        DTOQueueProfileState targetState = activeQueueStates.get(profileId);
        if (targetState == null) {
            if (showWarnings) showQueueUnavailableWarning();
            return;
        }
        ProfileAux targetProfile = findProfileById(profileId);
        if (targetProfile == null) {
            if (showWarnings) showQueueUnavailableWarning();
            return;
        }
        if (!targetState.isPaused()) {
            actionController.pauseQueue(targetProfile);
            setQueuePausedLocally(profileId, true);
        } else {
            actionController.resumeQueue(targetProfile);
            setQueuePausedLocally(profileId, false);
        }
    }

    private void configurePauseMenu() {
        refreshPauseMenuItems();
        updatePauseButtonState();
    }

    private void updateActiveQueueStates(List<DTOQueueProfileState> queueProfiles) {
        activeQueueStates.clear();
        if (queueProfiles == null) return;
        queueProfiles.stream()
                .filter(state -> state != null && state.getProfileId() != null)
                .forEach(state -> activeQueueStates.put(state.getProfileId(),
                        new DTOQueueProfileState(state.getProfileId(), state.getProfileName(), state.isPaused())));
    }

    private void updateAggregatedPauseStates() {
        if (activeQueueStates.isEmpty()) {
            allQueuesPaused = false;
        } else {
            allQueuesPaused = activeQueueStates.values().stream().allMatch(DTOQueueProfileState::isPaused);
        }
    }

    private void refreshPauseMenuItems() {
        if (buttonPauseResume == null) return;
        List<MenuItem> items = new ArrayList<>();
        if (menuToggleAllQueues != null) {
            menuToggleAllQueues.setText(allQueuesPaused ? "Resume" : "Pause");
            menuToggleAllQueues.setDisable(!estado);
            items.add(menuToggleAllQueues);
        }
        List<DTOQueueProfileState> queueStates = new ArrayList<>(activeQueueStates.values());
        queueStates.sort(Comparator.comparing(DTOQueueProfileState::getProfileName, String.CASE_INSENSITIVE_ORDER));
        if (!queueStates.isEmpty()) {
            items.add(new SeparatorMenuItem());
            for (DTOQueueProfileState state : queueStates) {
                items.add(createQueueMenuItem(state));
            }
        }
        buttonPauseResume.getItems().setAll(items);
    }

    private MenuItem createQueueMenuItem(DTOQueueProfileState state) {
        MenuItem item = new MenuItem(formatQueueMenuItemLabel(state));
        item.setOnAction(evt -> handleToggleSpecificQueue(state.getProfileId()));
        item.setDisable(!estado);
        return item;
    }

    private String formatQueueMenuItemLabel(DTOQueueProfileState state) {
        if (state == null) return "Toggle queue";
        String profileName = state.getProfileName() != null ? state.getProfileName() : String.valueOf(state.getProfileId());
        return (state.isPaused() ? "Resume " : "Pause ") + profileName;
    }

    private void updatePauseButtonState() {
        if (buttonPauseResume == null) return;
        buttonPauseResume.setText(allQueuesPaused ? "Resume All Queues" : "Pause All Queues");
    }

    private void resetPauseStates() {
        allQueuesPaused = false;
        activeQueueStates.clear();
        refreshPauseMenuItems();
        updatePauseButtonState();
    }

    public void scheduleAutoStart() {
        cancelAutoStart();
        HashMap<String, String> globalConfig = ServConfig.getServices().getGlobalConfig();
        if (globalConfig == null) return;
        boolean autoStartEnabled = Boolean.parseBoolean(
                globalConfig.getOrDefault(EnumConfigurationKey.AUTO_START_ENABLED_BOOL.name(), "false"));
        if (!autoStartEnabled) return;

        String autoStartMode = globalConfig.getOrDefault(EnumConfigurationKey.AUTO_START_MODE_STRING.name(), "Continuous");
        if ("Startup Only".equalsIgnoreCase(autoStartMode) && !isStartup) return;

        int delayMinutes;
        try {
            delayMinutes = Integer.parseInt(
                    globalConfig.getOrDefault(EnumConfigurationKey.AUTO_START_DELAY_MINUTES_INT.name(), "5"));
        } catch (NumberFormatException e) {
            delayMinutes = 5;
        }
        if (delayMinutes <= 0) return;

        autoStartSecondsRemaining = delayMinutes * 60;
        autoStartTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            autoStartSecondsRemaining--;
            int mins = autoStartSecondsRemaining / 60;
            int secs = autoStartSecondsRemaining % 60;
            buttonStartStop.setText(String.format("Start (%02d:%02d)", mins, secs));
            if (autoStartSecondsRemaining <= 0) {
                cancelAutoStart();
                Thread autoStartThread = Thread.ofVirtual().unstarted(() -> {
                    Platform.runLater(() -> {
                        buttonStartStop.setText("Starting...");
                        buttonStartStop.setDisable(true);
                    });
                    actionController.startBot();
                });
                autoStartThread.setName("Auto-Start-Thread");
                autoStartThread.start();
            }
        }));
        autoStartTimeline.setCycleCount(Timeline.INDEFINITE);
        autoStartTimeline.play();
    }

    private void cancelAutoStart() {
        if (autoStartTimeline != null) {
            autoStartTimeline.stop();
            autoStartTimeline = null;
            buttonStartStop.setText("Start Bot");
        }
    }

    private void showProfileSelectionWarning() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Profile selection required");
        alert.setHeaderText(null);
        alert.setContentText("Please select a profile to control its queue.");
        alert.showAndWait();
    }

    private void showQueueUnavailableWarning() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Queue not available");
        alert.setHeaderText(null);
        alert.setContentText("The selected queue is not currently running.");
        alert.showAndWait();
    }

    private ProfileAux findProfileById(Long profileId) {
        if (profileId == null || profileComboBox == null) return null;
        for (ProfileAux profile : profileComboBox.getItems()) {
            if (profile != null && profileId.equals(profile.getId())) return profile;
        }
        return null;
    }

    private void setAllQueuesPausedLocally(boolean paused) {
        activeQueueStates.values().forEach(state -> state.setPaused(paused));
        updateAggregatedPauseStates();
        refreshPauseMenuItems();
        updatePauseButtonState();
    }

    private void setQueuePausedLocally(Long profileId, boolean paused) {
        if (profileId == null) return;
        DTOQueueProfileState state = activeQueueStates.get(profileId);
        if (state != null) state.setPaused(paused);
        updateAggregatedPauseStates();
        refreshPauseMenuItems();
        updatePauseButtonState();
    }

    // ==================== BUTTON FACTORY ====================

    private Button createAndConfigureButton(String title, Ikon icon, Parent root) {
        FontIcon btnIcon = new FontIcon(icon);
        btnIcon.setIconSize(16);
        btnIcon.getStyleClass().add("nav-button-icon");

        Label btnLabel = new Label(title);
        btnLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnLabel, Priority.ALWAYS);

        HBox content = new HBox(10, btnIcon, btnLabel);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMaxWidth(Double.MAX_VALUE);

        Button button = new Button();
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-button");

        button.setOnAction(e -> {
            hideSearchOverlay();
            setMainContent(root);
            // Deselect all buttons in both containers
            for (Node node : buttonsContainer.getChildren()) {
                if (node instanceof Button) node.getStyleClass().remove("active");
            }
            if (pinnedButtonsContainer != null) {
                for (Node node : pinnedButtonsContainer.getChildren()) {
                    if (node instanceof Button) node.getStyleClass().remove("active");
                }
            }
            button.getStyleClass().add("active");
        });
        return button;
    }

    private Button addButton(String fxmlName, String title, Ikon icon, Object controller) {
        Parent root = loadNode(fxmlName, controller);
        Button button = createAndConfigureButton(title, icon, root);
        buttonsContainer.getChildren().add(button);
        Map<EnumConfigurationKey, String> settings = new HashMap<>();
        if (controller instanceof AbstractProfileController) {
            settings = ((AbstractProfileController) controller).getRegisteredSettings();
        }
        quickNavEntries.add(new QuickNavEntry(title, icon, button, settings));
        return button;
    }

    private Button addPinnedButton(String title, Ikon icon, Parent root) {
        Button button = createAndConfigureButton(title, icon, root);
        if (pinnedButtonsContainer != null) {
            pinnedButtonsContainer.getChildren().add(button);
        }
        return button;
    }

    private Parent loadNode(String fxmlName, Object controller) {
        try {
            FXMLLoader loader = new FXMLLoader(controller.getClass().getResource(fxmlName + ".fxml"));
            loader.setController(controller);
            return loader.load();
        } catch (IOException e) {
            e.printStackTrace();
            return new VBox();
        }
    }

    public <T> T getModuleController(String key, Class<T> type) {
        Object controller = moduleControllers.get(key);
        if (controller == null) return null;
        return type.cast(controller);
    }

    // ==================== SEARCH OVERLAY ====================

    private void initializeSearch() {
        if (navSearchField == null) return;
        navSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String q = newVal == null ? "" : newVal.trim();
            if (q.isEmpty()) {
                hideSearchOverlay();
            } else {
                showSearchResults(q);
            }
        });
        navSearchField.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                navSearchField.clear();
            }
        });
    }

    private void showSearchResults(String query) {
        if (centerStack == null) return;

        String lq = query.toLowerCase();
        Map<QuickNavEntry, Map<EnumConfigurationKey, String>> matches = new java.util.LinkedHashMap<>();
        int totalMatches = 0;

        for (QuickNavEntry entry : quickNavEntries) {
            boolean titleMatch = entry.title().toLowerCase().contains(lq);
            Map<EnumConfigurationKey, String> matchedSettings = new java.util.LinkedHashMap<>();

            if (entry.settings() != null) {
                for (Map.Entry<EnumConfigurationKey, String> setEntry : entry.settings().entrySet()) {
                    String lbl = setEntry.getValue().toLowerCase();
                    String keyName = setEntry.getKey().name().toLowerCase();
                    String humanKey = setEntry.getKey().name().replace("_", " ").toLowerCase();
                    if (titleMatch || lbl.contains(lq) || keyName.contains(lq) || humanKey.contains(lq)) {
                        matchedSettings.put(setEntry.getKey(), setEntry.getValue());
                    }
                }
            }

            if (titleMatch || !matchedSettings.isEmpty()) {
                matches.put(entry, matchedSettings);
                totalMatches += matchedSettings.isEmpty() && titleMatch ? 1 : matchedSettings.size();
            }
        }

        hideSearchOverlay();

        // Header
        Label title = new Label("Search Results");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #e6edf3;");

        Label searchingFor = new Label("Searching for: \"" + query + "\"");
        searchingFor.setStyle("-fx-font-size: 12px; -fx-text-fill: #636a75;");

        Label countLabel = new Label(totalMatches + " result" + (totalMatches == 1 ? "" : "s") + " found");
        countLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (totalMatches == 0 ? "#e05c5c;" : "#e2b340;"));

        VBox headerBox = new VBox(2, title, searchingFor, countLabel);
        headerBox.setPadding(new Insets(32, 32, 24, 32));

        VBox resultsList = new VBox(16);
        resultsList.setPadding(new Insets(16, 32, 32, 32));

        if (matches.isEmpty()) {
            Label none = new Label("No matching modules or settings.");
            none.setStyle("-fx-text-fill: #636a75; -fx-font-size: 13px; -fx-padding: 16 0 0 0;");
            resultsList.getChildren().add(none);
        } else {
            for (Map.Entry<QuickNavEntry, Map<EnumConfigurationKey, String>> moduleMatch : matches.entrySet()) {
                resultsList.getChildren().add(createSearchResultGroup(moduleMatch.getKey(), moduleMatch.getValue(), query));
            }
        }

        VBox content = new VBox(0, headerBox, resultsList);
        content.setStyle("-fx-background-color: #11141a;");
        content.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: #11141a; -fx-background: #11141a;");
        scroll.getStyleClass().add("search-results-pane");

        searchOverlay = new VBox(scroll);
        searchOverlay.setFillWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        searchOverlay.setMaxWidth(Double.MAX_VALUE);
        searchOverlay.setMaxHeight(Double.MAX_VALUE);
        searchOverlay.setStyle("-fx-background-color: #11141a;");
        searchOverlay.setOpacity(0);

        StackPane.setAlignment(searchOverlay, Pos.TOP_LEFT);
        centerStack.getChildren().add(searchOverlay);

        FadeTransition ft = new FadeTransition(Duration.millis(180), searchOverlay);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private VBox createSearchResultGroup(QuickNavEntry entry, Map<EnumConfigurationKey, String> matchedSettings, String query) {
        Label moduleLabel = new Label(entry.title());
        moduleLabel.setStyle("-fx-text-fill: #e2b340; -fx-font-size: 14px; -fx-font-weight: bold;");

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        org.kordamp.ikonli.javafx.FontIcon navArrow = new org.kordamp.ikonli.javafx.FontIcon("mdi2c-chevron-right");
        navArrow.setIconSize(24);

        HBox header = new HBox(12, moduleLabel, spacer, navArrow);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 16, 10, 16));
        header.getStyleClass().add("search-group-header");
        header.setCursor(javafx.scene.Cursor.HAND);
        header.setOnMouseClicked(e -> {
            navSearchField.clear();
            entry.sidebarButton().fire();
        });

        VBox rowsBox = new VBox(0);

        for (Map.Entry<EnumConfigurationKey, String> setEntry : matchedSettings.entrySet()) {
            String labelStr = setEntry.getValue();
            String enumStr = setEntry.getKey().name();
            String humanStr = enumStr.replace("_", " ").toLowerCase();

            TextFlow labelFlow = buildHighlightedText(labelStr, query, "#e6edf3");
            TextFlow enumFlow = buildHighlightedText(enumStr, query, "#8b949e");
            TextFlow humanFlow = buildHighlightedText(humanStr, query, "#636a75");
            humanFlow.setStyle("-fx-font-style: italic;");

            HBox rowContent = new HBox(16, labelFlow, enumFlow, humanFlow);
            rowContent.setAlignment(Pos.CENTER_LEFT);
            rowContent.setPadding(new Insets(12, 16, 12, 16));
            rowContent.getStyleClass().add("search-result-row");
            rowContent.setCursor(javafx.scene.Cursor.HAND);
            rowContent.setOnMouseClicked(e -> {
                navSearchField.clear();
                entry.sidebarButton().fire();
            });

            rowsBox.getChildren().add(rowContent);
        }

        VBox group = new VBox(0);
        group.getChildren().add(header);
        if (!matchedSettings.isEmpty()) {
            group.getChildren().add(rowsBox);
        }
        group.getStyleClass().add("search-result-group");
        group.setFillWidth(true);
        return group;
    }

    private TextFlow buildHighlightedText(String text, String query, String baseColor) {
        TextFlow flow = new TextFlow();
        String lText = text.toLowerCase();
        String lQuery = query.toLowerCase();
        int idx = lText.indexOf(lQuery);
        if (idx < 0) {
            Text t = new Text(text);
            t.setStyle("-fx-fill: " + baseColor + "; -fx-font-size: 13px;");
            flow.getChildren().add(t);
            return flow;
        }
        if (idx > 0) {
            Text before = new Text(text.substring(0, idx));
            before.setStyle("-fx-fill: " + baseColor + "; -fx-font-size: 13px;");
            flow.getChildren().add(before);
        }
        Text match = new Text(text.substring(idx, idx + query.length()));
        match.setStyle("-fx-fill: #ffffff; -fx-font-size: 13px; -fx-font-weight: bold;");
        flow.getChildren().add(match);
        if (idx + query.length() < text.length()) {
            Text after = new Text(text.substring(idx + query.length()));
            after.setStyle("-fx-fill: " + baseColor + "; -fx-font-size: 13px;");
            flow.getChildren().add(after);
        }
        return flow;
    }

    private void hideSearchOverlay() {
        if (searchOverlay == null) return;
        centerStack.getChildren().remove(searchOverlay);
        searchOverlay = null;
    }

    // ==================== QUICK NAV OVERLAY ====================

    private record QuickNavEntry(String title, Ikon icon, Button sidebarButton, Map<EnumConfigurationKey, String> settings) {}

    private void initializeQuickNav() {
        if (logoContainer != null) {
            logoContainer.setOnMouseClicked(e -> toggleQuickNav());
        }
    }

    private void toggleQuickNav() {
        if (quickNavVisible) {
            hideQuickNav();
        } else {
            showQuickNav();
        }
    }

    private void showQuickNav() {
        if (centerStack == null) return;
        quickNavVisible = true;

        mainContentPane.setEffect(new GaussianBlur(18));

        int columns = 4;
        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(24);
        grid.setAlignment(Pos.CENTER);
        grid.setPadding(new Insets(16));

        for (int c = 0; c < columns; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / columns);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }

        int index = 0;
        for (QuickNavEntry entry : quickNavEntries) {
            HBox card = createQuickNavCard(entry, index);
            grid.add(card, index % columns, index / columns);
            index++;
        }

        Label heading = new Label("Quick Navigation");
        heading.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label subtitle = new Label("Click any tile to jump directly to that module");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #636a75;");

        VBox headingBox = new VBox(6);
        headingBox.setAlignment(Pos.CENTER);
        headingBox.getChildren().addAll(heading, subtitle);

        VBox overlayContent = new VBox(32);
        overlayContent.setAlignment(Pos.TOP_CENTER);
        overlayContent.setPadding(new Insets(60, 40, 60, 40));
        overlayContent.setMaxWidth(1100);
        overlayContent.getChildren().addAll(headingBox, grid);

        overlayContent.setOnMouseClicked(e -> {
            if (e.getTarget() == overlayContent) hideQuickNav();
        });

        ScrollPane scrollOverlay = new ScrollPane(overlayContent);
        scrollOverlay.setFitToWidth(true);
        scrollOverlay.setFitToHeight(true);
        scrollOverlay.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollOverlay.getStyleClass().add("quick-nav-overlay");
        scrollOverlay.setOpacity(0);
        scrollOverlay.setScaleX(0.96);
        scrollOverlay.setScaleY(0.96);

        quickNavOverlay = scrollOverlay;
        centerStack.getChildren().add(scrollOverlay);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(220), scrollOverlay);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(280), scrollOverlay);
        scaleIn.setFromX(0.96); scaleIn.setFromY(0.96);
        scaleIn.setToX(1.0); scaleIn.setToY(1.0);
        scaleIn.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fadeIn, scaleIn).play();
    }

    private HBox createQuickNavCard(QuickNavEntry entry, int index) {
        FontIcon cardIcon = new FontIcon(entry.icon());
        cardIcon.setIconSize(20);
        cardIcon.getStyleClass().add("quick-nav-card-icon");

        Label cardLabel = new Label(entry.title());
        cardLabel.getStyleClass().add("quick-nav-card-label");
        cardLabel.setAlignment(Pos.CENTER_LEFT);

        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER);
        card.setMinHeight(80);
        card.setPadding(new Insets(16, 20, 16, 20));
        card.getStyleClass().add("quick-nav-card");
        GridPane.setHgrow(card, Priority.ALWAYS);

        card.getChildren().addAll(cardIcon, cardLabel);
        card.setOpacity(0);
        card.setTranslateY(24);

        PauseTransition delay = new PauseTransition(Duration.millis(25 * index));
        delay.setOnFinished(e -> {
            FadeTransition cardFade = new FadeTransition(Duration.millis(180), card);
            cardFade.setFromValue(0); cardFade.setToValue(1);
            TranslateTransition cardSlide = new TranslateTransition(Duration.millis(220), card);
            cardSlide.setFromY(24); cardSlide.setToY(0);
            cardSlide.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(cardFade, cardSlide).play();
        });
        delay.play();

        card.setOnMouseClicked(e -> {
            e.consume();
            hideQuickNav();
            entry.sidebarButton().fire();
        });
        return card;
    }

    private void hideQuickNav() {
        if (!quickNavVisible || quickNavOverlay == null || centerStack == null) return;
        quickNavVisible = false;

        FadeTransition fadeOut = new FadeTransition(Duration.millis(150), quickNavOverlay);
        fadeOut.setFromValue(1); fadeOut.setToValue(0);

        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(150), quickNavOverlay);
        scaleOut.setToX(0.96); scaleOut.setToY(0.96);
        scaleOut.setInterpolator(Interpolator.EASE_IN);

        ParallelTransition exitAnim = new ParallelTransition(fadeOut, scaleOut);
        exitAnim.setOnFinished(e -> {
            centerStack.getChildren().remove(quickNavOverlay);
            quickNavOverlay = null;
            mainContentPane.setEffect(null);
        });
        exitAnim.play();
    }

    // ==================== MODULE DEFINITION ====================

    private record ModuleDefinition(String fxmlName, String buttonTitle, Ikon icon, Supplier<Object> controllerSupplier) {

        public Object createController(IProfileChangeObserver profileObserver) {
            Object controller = controllerSupplier.get();
            if (controller instanceof IProfileObserverInjectable) {
                ((IProfileObserverInjectable) controller).setProfileObserver(profileObserver);
            }
            return controller;
        }

    }

    @FXML
    private void openDiscord() {
        openWebPage("https://discord.com/invite/sUthSHRVvU");
    }

    @FXML
    private void openGithub() {
        openWebPage("https://github.com/Shederator/wosbot");
    }

    private void openWebPage(String uri) {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI(uri));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
