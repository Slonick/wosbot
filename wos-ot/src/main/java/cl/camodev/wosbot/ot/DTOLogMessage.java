package cl.camodev.wosbot.ot;

import cl.camodev.wosbot.console.enumerable.EnumTpMessageSeverity;

public class DTOLogMessage {

	private EnumTpMessageSeverity severity;
	private String message;
	private String task;
	private String profile;

	public DTOLogMessage(EnumTpMessageSeverity severity, String message, String task, String profile) {
		this.severity = severity;
		this.message = message;
		this.task = task;
		this.profile = profile;
	}

	public EnumTpMessageSeverity getSeverity() {
		return severity;
	}

	public void setSeverity(EnumTpMessageSeverity severity) {
		this.severity = severity;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getTask() {
		return task;
	}

	public void setTask(String task) {
		this.task = task;
	}

	public String getProfile() {
		return profile;
	}

	public void setProfile(String profile) {
		this.profile = profile;
	}

}
