package cl.camodev.wosbot.taskmanager.model;

import java.util.List;

import cl.camodev.wosbot.ot.DTODailyTaskStatus;
import cl.camodev.wosbot.taskmanager.ITaskStatusChangeListener;

public interface ITaskStatusModel {

	public List<DTODailyTaskStatus> getDailyTaskStatusList(Long profileId);

	public void addTaskStatusChangeListener(ITaskStatusChangeListener taskManagerActionController);

}
