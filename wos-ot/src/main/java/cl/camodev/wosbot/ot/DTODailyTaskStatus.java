package cl.camodev.wosbot.ot;

import java.time.LocalDateTime;

public class DTODailyTaskStatus {

	private Long idProfile;
	private Integer idTpDailyTask;
	private LocalDateTime lastExecution;
	private LocalDateTime nextSchedule;

	public DTODailyTaskStatus() {
	}

	public DTODailyTaskStatus(Long idProfile, Integer idTpDailyTask, LocalDateTime lastExecution, LocalDateTime nextSchedule) {
		this.lastExecution = lastExecution;
		this.nextSchedule = nextSchedule;
		this.idProfile = idProfile;
		this.idTpDailyTask = idTpDailyTask;
	}

	public Long getIdProfile() {
		return idProfile;
	}

	public void setIdProfile(Long idProfile) {
		this.idProfile = idProfile;
	}

	public Integer getIdTpDailyTask() {
		return idTpDailyTask;
	}

	public void setIdTpDailyTask(Integer idTpDailyTask) {
		this.idTpDailyTask = idTpDailyTask;
	}

	public LocalDateTime getLastExecution() {
		return lastExecution;
	}

	public void setLastExecution(LocalDateTime lastExecution) {
		this.lastExecution = lastExecution;
	}

	public LocalDateTime getNextSchedule() {
		return nextSchedule;
	}

	public void setNextSchedule(LocalDateTime nextSchedule) {
		this.nextSchedule = nextSchedule;
	}
}
