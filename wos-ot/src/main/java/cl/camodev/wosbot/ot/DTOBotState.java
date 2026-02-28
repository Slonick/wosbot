package cl.camodev.wosbot.ot;

import java.time.LocalDateTime;

public class DTOBotState {

	private Boolean running;
	private Boolean paused;
	private LocalDateTime actionTime;

	public Boolean getRunning() {
		return running;
	}

	public void setRunning(Boolean running) {
		this.running = running;
	}

	public Boolean getPaused() {
		return paused;
	}

	public void setPaused(Boolean paused) {
		this.paused = paused;
	}

	public LocalDateTime getActionTime() {
		return actionTime;
	}

	public void setActionTime(LocalDateTime actionTime) {
		this.actionTime = actionTime;
	}

}
