package cl.camodev.wosbot.console.model;

import javafx.beans.property.SimpleStringProperty;

public class LogMessageAux {

	private final SimpleStringProperty timeStamp;
	private final SimpleStringProperty message;
	private final SimpleStringProperty severity;
	private final SimpleStringProperty task;
	private final SimpleStringProperty profile;

	public LogMessageAux(String timeStamp, String severity, String message, String task, String profile) {
		this.timeStamp = new SimpleStringProperty(timeStamp);
		this.message = new SimpleStringProperty(message);
		this.severity = new SimpleStringProperty(severity);
		this.task = new SimpleStringProperty(task);
		this.profile = new SimpleStringProperty(profile);
	}

	public SimpleStringProperty timeStampProperty() {
		return timeStamp;
	}

	public SimpleStringProperty messageProperty() {
		return message;
	}

	public SimpleStringProperty severityProperty() {
		return severity;
	}

	public SimpleStringProperty taskProperty() {
		return task;
	}

	public SimpleStringProperty profileProperty() {
		return profile;
	}

}
