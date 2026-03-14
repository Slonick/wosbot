package cl.camodev.wosbot.statistics.view;

import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cl.camodev.wosbot.common.view.AbstractProfileController;
import cl.camodev.wosbot.console.enumerable.EnumConfigurationKey;
import cl.camodev.wosbot.profile.model.ProfileAux;
import cl.camodev.wosbot.ot.DTOProfileStatistics;
import cl.camodev.wosbot.ot.DTOTaskStatistic;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class StatisticsLayoutController extends AbstractProfileController {

    // ========================================================================
    // COUNTER CATEGORY MAPPING
    // ========================================================================

    private static final Map<String, String> COUNTER_CATEGORIES = new LinkedHashMap<>();
    static {
        // Combat
        COUNTER_CATEGORIES.put("Arena Battles Won", "Combat");
        COUNTER_CATEGORIES.put("Arena Battles Lost", "Combat");
        COUNTER_CATEGORIES.put("Arena Gems Spent", "Combat");
        COUNTER_CATEGORIES.put("Arena Refreshes", "Combat");
        COUNTER_CATEGORIES.put("Exploration Fights Won", "Combat");
        COUNTER_CATEGORIES.put("Exploration Fights Lost", "Combat");
        // Intel & Exploration
        COUNTER_CATEGORIES.put("Intel Beast", "Intel & Exploration");
        COUNTER_CATEGORIES.put("Intel Survivor Camps", "Intel & Exploration");
        COUNTER_CATEGORIES.put("Intel Journeys", "Intel & Exploration");
        COUNTER_CATEGORIES.put("Beast Attacks Sent", "Intel & Exploration");
        // Economy
        COUNTER_CATEGORIES.put("Mystery Shop Purchases", "Economy");
        COUNTER_CATEGORIES.put("Mystery Shop Free Claims", "Economy");
        COUNTER_CATEGORIES.put("Daily Refreshes Used", "Economy");
        COUNTER_CATEGORIES.put("Alliance Shop Purchases", "Economy");
        COUNTER_CATEGORIES.put("Gather Marches Deployed", "Economy");
        // Training & Research
        COUNTER_CATEGORIES.put("Training Batches Started", "Training & Research");
        COUNTER_CATEGORIES.put("Research Started", "Training & Research");
        // Utility
        COUNTER_CATEGORIES.put("Mail Rewards Claimed", "Utility");
        COUNTER_CATEGORIES.put("Daily Missions Claimed", "Utility");
    }

    // Ordered list of categories for display
    private static final List<String> CATEGORY_ORDER = List.of(
            "Combat", "Intel & Exploration", "Economy", "Training & Research", "Utility", "Other");

    // ========================================================================
    // FXML FIELDS
    // ========================================================================

    @FXML private Button btnRefresh;
    @FXML private Button btnReset;
    @FXML private HBox hboxSummaryCards;
    @FXML private TableView<DTOTaskStatistic> tableTasks;
    @FXML private TableColumn<DTOTaskStatistic, String> colTaskName;
    @FXML private TableColumn<DTOTaskStatistic, Number> colRuns;
    @FXML private TableColumn<DTOTaskStatistic, String> colAvgTime;
    @FXML private TableColumn<DTOTaskStatistic, String> colTotalTime;
    @FXML private TableColumn<DTOTaskStatistic, String> colLastRun;
    @FXML private TableColumn<DTOTaskStatistic, String> colAvgOcr;
    @FXML private TableColumn<DTOTaskStatistic, String> colAvgImg;
    @FXML private VBox vboxCounterSections;
    @FXML private Label lblNoData;

    private ProfileAux currentProfile;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    @FXML
    private void initialize() {
        colTaskName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTaskName()));
        colRuns.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getNumberOfRuns()));
        colAvgTime.setCellValueFactory(cellData -> new SimpleStringProperty(String.format("%.2f", cellData.getValue().getAverageExecutionTimeMs() / 1000.0)));
        colTotalTime.setCellValueFactory(cellData -> new SimpleStringProperty(String.format("%.2f", cellData.getValue().getTotalExecutionTimeMs() / 1000.0)));
        colAvgOcr.setCellValueFactory(cellData -> new SimpleStringProperty(String.format("%.2f", cellData.getValue().getAverageOcrFailures())));
        colAvgImg.setCellValueFactory(cellData -> new SimpleStringProperty(String.format("%.2f", cellData.getValue().getAverageTemplateFailures())));
        colLastRun.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getLastRunTime()));
    }

    // ========================================================================
    // PROFILE LIFECYCLE
    // ========================================================================

    @Override
    public void onProfileLoad(ProfileAux profile) {
        super.onProfileLoad(profile);
        this.currentProfile = profile;
        refreshStatisticsView();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        if (currentProfile != null) {
            refreshStatisticsView();
        }
    }

    @FXML
    private void handleReset(ActionEvent event) {
        if (currentProfile == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reset Statistics");
        alert.setHeaderText("Reset all statistics for this profile?");
        alert.setContentText("This action cannot be undone.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                if (profileObserver != null) {
                    profileObserver.notifyProfileChange(EnumConfigurationKey.STATISTICS_JSON_STRING, "{}");
                }
                Platform.runLater(this::refreshStatisticsView);
            }
        });
    }

    // ========================================================================
    // MAIN REFRESH
    // ========================================================================

    private void refreshStatisticsView() {
        if (currentProfile == null) return;

        String json = currentProfile.getConfiguration(EnumConfigurationKey.STATISTICS_JSON_STRING);
        DTOProfileStatistics stats = parseJsonToStats(json);

        hboxSummaryCards.getChildren().clear();
        tableTasks.getItems().clear();
        vboxCounterSections.getChildren().clear();

        if (stats.getTaskStatistics().isEmpty() && stats.getCustomCounters().isEmpty()) {
            lblNoData.setVisible(true);
            lblNoData.setManaged(true);
            tableTasks.setVisible(false);
            tableTasks.setManaged(false);
        } else {
            lblNoData.setVisible(false);
            lblNoData.setManaged(false);
            tableTasks.setVisible(true);
            tableTasks.setManaged(true);

            // Build summary cards
            buildSummaryCards(stats);

            // Populate task table
            ObservableList<DTOTaskStatistic> taskData = FXCollections.observableArrayList(stats.getTaskStatistics().values());
            tableTasks.setItems(taskData);

            // Build grouped counter sections
            buildGroupedCounters(stats.getCustomCounters());
        }
    }

    // ========================================================================
    // SUMMARY CARDS
    // ========================================================================

    private void buildSummaryCards(DTOProfileStatistics stats) {
        int totalRuns = 0;
        long totalTimeMs = 0;
        long totalOcrFails = 0;
        long totalImgFails = 0;

        for (DTOTaskStatistic task : stats.getTaskStatistics().values()) {
            totalRuns += task.getNumberOfRuns();
            totalTimeMs += task.getTotalExecutionTimeMs();
            totalOcrFails += task.getTotalOcrFailures();
            totalImgFails += task.getTotalTemplateSearchFailures();
        }

        String totalTimeStr;
        double totalHours = totalTimeMs / 3_600_000.0;
        if (totalHours >= 1.0) {
            totalTimeStr = String.format("%.1fh", totalHours);
        } else {
            totalTimeStr = String.format("%.0fm", totalTimeMs / 60_000.0);
        }

        String avgOcr = totalRuns > 0 ? String.format("%.2f", (double) totalOcrFails / totalRuns) : "0";
        String avgImg = totalRuns > 0 ? String.format("%.2f", (double) totalImgFails / totalRuns) : "0";
        int counterTotal = stats.getCustomCounters().values().stream().mapToInt(Integer::intValue).sum();

        hboxSummaryCards.getChildren().addAll(
                createSummaryCard("Total Runs", String.valueOf(totalRuns), "#4fc3f7"),
                createSummaryCard("Total Time", totalTimeStr, "#81c784"),
                createSummaryCard("Avg OCR Fail", avgOcr, "#ffb74d"),
                createSummaryCard("Avg Img Fail", avgImg, "#ff8a65"),
                createSummaryCard("Actions", String.valueOf(counterTotal), "#ba68c8")
        );
    }

    private VBox createSummaryCard(String title, String value, String accentColor) {
        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12, 18, 12, 18));
        card.setStyle("-fx-background-color: #242424; -fx-background-radius: 8; -fx-border-color: #333; -fx-border-radius: 8;");
        card.setMinWidth(120);
        HBox.setHgrow(card, Priority.ALWAYS);

        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");

        Label lblValue = new Label(value);
        lblValue.setStyle("-fx-text-fill: " + accentColor + "; -fx-font-size: 22px; -fx-font-weight: bold;");

        card.getChildren().addAll(lblTitle, lblValue);

        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #2a2a2a; -fx-background-radius: 8; -fx-border-color: #444; -fx-border-radius: 8;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: #242424; -fx-background-radius: 8; -fx-border-color: #333; -fx-border-radius: 8;"));

        return card;
    }

    // ========================================================================
    // GROUPED COUNTER SECTIONS
    // ========================================================================

    private void buildGroupedCounters(Map<String, Integer> customCounters) {
        if (customCounters.isEmpty()) return;

        // Group counters by category
        Map<String, Map<String, Integer>> grouped = new LinkedHashMap<>();
        for (String cat : CATEGORY_ORDER) {
            grouped.put(cat, new LinkedHashMap<>());
        }

        for (Map.Entry<String, Integer> entry : customCounters.entrySet()) {
            String category = COUNTER_CATEGORIES.getOrDefault(entry.getKey(), "Other");
            grouped.computeIfAbsent(category, k -> new LinkedHashMap<>()).put(entry.getKey(), entry.getValue());
        }

        // Build TitledPanes per category
        for (String category : CATEGORY_ORDER) {
            Map<String, Integer> counters = grouped.get(category);
            if (counters == null || counters.isEmpty()) continue;

            FlowPane flowPane = new FlowPane();
            flowPane.setHgap(12);
            flowPane.setVgap(12);
            flowPane.setPadding(new Insets(8));

            for (Map.Entry<String, Integer> entry : counters.entrySet()) {
                flowPane.getChildren().add(createCounterCard(entry.getKey(), entry.getValue()));
            }

            TitledPane titledPane = new TitledPane(category + " (" + counters.size() + ")", flowPane);
            titledPane.setExpanded(true);
            titledPane.setCollapsible(true);
            titledPane.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");

            vboxCounterSections.getChildren().add(titledPane);
        }
    }

    private VBox createCounterCard(String name, Integer value) {
        VBox card = new VBox();
        card.setSpacing(6);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: #242424; -fx-background-radius: 8; -fx-border-color: #333; -fx-border-radius: 8;");
        card.setPrefWidth(160);
        card.setMinHeight(80);
        card.setAlignment(Pos.CENTER);

        Label lblName = new Label(name);
        lblName.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 12px; -fx-font-weight: bold;");
        lblName.setWrapText(true);
        lblName.setAlignment(Pos.CENTER);

        Label lblValue = new Label(String.valueOf(value));
        lblValue.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 22px; -fx-font-weight: bold;");

        card.getChildren().addAll(lblName, lblValue);

        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #2a2a2a; -fx-background-radius: 8; -fx-border-color: #444; -fx-border-radius: 8;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: #242424; -fx-background-radius: 8; -fx-border-color: #333; -fx-border-radius: 8;"));

        return card;
    }

    // ========================================================================
    // JSON PARSING
    // ========================================================================

    private DTOProfileStatistics parseJsonToStats(String json) {
        if (json == null || json.trim().isEmpty() || json.equals("{}")) {
            return new DTOProfileStatistics();
        }
        try {
            return objectMapper.readValue(json, DTOProfileStatistics.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return new DTOProfileStatistics();
        }
    }
}
