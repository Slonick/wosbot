package cl.camodev.wosbot.taskmanager.model;

import java.time.LocalDateTime;

import cl.camodev.wosbot.console.enumerable.TpDailyTaskEnum;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class TaskManagerAux {

	private final StringProperty taskName;
	private final ObjectProperty<LocalDateTime> lastExecution;
	private final ObjectProperty<LocalDateTime> nextExecution;
	private final ObjectProperty<TpDailyTaskEnum> taskEnum;
	private final LongProperty profileId;
	private final LongProperty nearestMinutesUntilExecution;
	private final BooleanProperty hasReadyTask;
	private final BooleanProperty scheduled;
	private final BooleanProperty executing;

	// —————————————————————
	// CONSTRUCTOR
	// —————————————————————
	public TaskManagerAux(String taskName, LocalDateTime lastExecution, LocalDateTime nextExecution, TpDailyTaskEnum taskEnum, Long profileId, long nearestMinutesUntilExecution, boolean hasReadyTask, boolean isScheduled, boolean executing) {

		this.taskName = new SimpleStringProperty(taskName);
		this.lastExecution = new SimpleObjectProperty<>(lastExecution);
		this.nextExecution = new SimpleObjectProperty<>(nextExecution);
		this.taskEnum = new SimpleObjectProperty<>(taskEnum);
		this.profileId = new SimpleLongProperty(profileId);
		this.nearestMinutesUntilExecution = new SimpleLongProperty(nearestMinutesUntilExecution);
		this.hasReadyTask = new SimpleBooleanProperty(hasReadyTask);
		this.scheduled = new SimpleBooleanProperty(isScheduled);
		this.executing = new SimpleBooleanProperty(executing);
	}

	public String getTaskName() {
		return taskName.get();
	}

	public StringProperty taskNameProperty() {
		return taskName;
	}

	public void setTaskName(String v) {
		taskName.set(v);
	}

	public LocalDateTime getLastExecution() {
		return lastExecution.get();
	}

	public ObjectProperty<LocalDateTime> lastExecutionProperty() {
		return lastExecution;
	}

	public void setLastExecution(LocalDateTime v) {
		lastExecution.set(v);
	}

	public LocalDateTime getNextExecution() {
		return nextExecution.get();
	}

	public ObjectProperty<LocalDateTime> nextExecutionProperty() {
		return nextExecution;
	}

	public void setNextExecution(LocalDateTime v) {
		nextExecution.set(v);
	}

	public TpDailyTaskEnum getTaskEnum() {
		return taskEnum.get();
	}

	public ObjectProperty<TpDailyTaskEnum> taskEnumProperty() {
		return taskEnum;
	}

	public void setTaskEnum(TpDailyTaskEnum v) {
		taskEnum.set(v);
	}

	public long getProfileId() {
		return profileId.get();
	}

	public LongProperty profileIdProperty() {
		return profileId;
	}

	public void setProfileId(long v) {
		profileId.set(v);
	}

	public long getNearestMinutesUntilExecution() {
		return nearestMinutesUntilExecution.get();
	}

	public LongProperty nearestMinutesUntilExecutionProperty() {
		return nearestMinutesUntilExecution;
	}

	public void setNearestMinutesUntilExecution(long v) {
		nearestMinutesUntilExecution.set(v);
	}

	public boolean hasReadyTask() {
		return hasReadyTask.get();
	}

	public BooleanProperty hasReadyTaskProperty() {
		return hasReadyTask;
	}

	public void setHasReadyTask(boolean v) {
		hasReadyTask.set(v);
	}

	public boolean isScheduled() {
		return scheduled.get();
	}

	public BooleanProperty scheduledProperty() {
		return scheduled;
	}

	public void setScheduled(boolean v) {
		scheduled.set(v);
	}

	public boolean isExecuting() {
		return executing.get();
	}

	public BooleanProperty executingProperty() {
		return executing;
	}

	public void setExecuting(boolean v) {
		executing.set(v);
	}

}
