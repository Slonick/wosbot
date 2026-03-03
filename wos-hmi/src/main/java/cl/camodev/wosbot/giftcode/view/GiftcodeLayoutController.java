package cl.camodev.wosbot.giftcode.view;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class GiftcodeLayoutController {

	private static final String API_URL = "http://gift-code-api.whiteout-bot.com/giftcode_api.php";
	private static final String API_KEY = "super_secret_bot_token_nobody_will_ever_find";

	@FXML
	private Button buttonFetch;

	@FXML
	private Label labelStatus;

	@FXML
	private TableView<GiftcodeEntry> tableGiftcodes;

	@FXML
	private TableColumn<GiftcodeEntry, String> columnCode;

	@FXML
	private TableColumn<GiftcodeEntry, String> columnDate;

	private final ObservableList<GiftcodeEntry> giftcodeList = FXCollections.observableArrayList();

	@FXML
	private void initialize() {
		columnCode.setCellValueFactory(cellData -> cellData.getValue().codeProperty());
		columnDate.setCellValueFactory(cellData -> cellData.getValue().dateProperty());
		tableGiftcodes.setItems(giftcodeList);
		tableGiftcodes.setPlaceholder(new Label("Click 'Fetch' to load gift codes"));
	}

	@FXML
	private void handleFetch() {
		buttonFetch.setDisable(true);
		labelStatus.setText("Fetching gift codes...");
		giftcodeList.clear();

		Thread fetchThread = new Thread(() -> {
			try {
				HttpClient client = HttpClient.newBuilder()
						.connectTimeout(Duration.ofSeconds(10))
						.build();

				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(API_URL))
						.header("X-API-Key", API_KEY)
						.header("Content-Type", "application/json")
						.GET()
						.build();

				HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					List<GiftcodeEntry> entries = parseResponse(response.body());
					Platform.runLater(() -> {
						giftcodeList.setAll(entries);
						labelStatus.setText("Fetched " + entries.size() + " gift code(s) successfully.");
						buttonFetch.setDisable(false);
					});
				} else {
					Platform.runLater(() -> {
						labelStatus.setText("Error: HTTP " + response.statusCode());
						buttonFetch.setDisable(false);
					});
				}
			} catch (Exception e) {
				Platform.runLater(() -> {
					labelStatus.setText("Error: " + e.getMessage());
					buttonFetch.setDisable(false);
				});
			}
		});
		fetchThread.setDaemon(true);
		fetchThread.start();
	}

	/**
	 * Parses the JSON response to extract gift codes.
	 * Expected format: {"codes":["CODE1 DD.MM.YYYY","CODE2 DD.MM.YYYY",...]}
	 */
	private List<GiftcodeEntry> parseResponse(String json) {
		List<GiftcodeEntry> entries = new ArrayList<>();

		// Extract the "codes" array content
		int codesStart = json.indexOf("\"codes\"");
		if (codesStart == -1) {
			return entries;
		}

		int arrayStart = json.indexOf('[', codesStart);
		int arrayEnd = json.indexOf(']', arrayStart);
		if (arrayStart == -1 || arrayEnd == -1) {
			return entries;
		}

		String arrayContent = json.substring(arrayStart + 1, arrayEnd);

		// Split by comma and parse each entry
		String[] items = arrayContent.split(",");
		for (String item : items) {
			String trimmed = item.trim();
			// Remove surrounding quotes
			if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
				trimmed = trimmed.substring(1, trimmed.length() - 1);
			}
			if (trimmed.isEmpty()) {
				continue;
			}

			// Split code and date by last space
			int lastSpace = trimmed.lastIndexOf(' ');
			if (lastSpace > 0) {
				String code = trimmed.substring(0, lastSpace).trim();
				String date = trimmed.substring(lastSpace + 1).trim();
				entries.add(new GiftcodeEntry(code, date));
			} else {
				entries.add(new GiftcodeEntry(trimmed, "N/A"));
			}
		}

		return entries;
	}

	/**
	 * Represents a single gift code entry with code and date.
	 */
	public static class GiftcodeEntry {
		private final SimpleStringProperty code;
		private final SimpleStringProperty date;

		public GiftcodeEntry(String code, String date) {
			this.code = new SimpleStringProperty(code);
			this.date = new SimpleStringProperty(date);
		}

		public SimpleStringProperty codeProperty() {
			return code;
		}

		public SimpleStringProperty dateProperty() {
			return date;
		}

		public String getCode() {
			return code.get();
		}

		public String getDate() {
			return date.get();
		}
	}
}
