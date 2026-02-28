package cl.camodev.wosbot.taskmanager.model.impl;

import java.util.List;

import cl.camodev.wosbot.ot.DTODailyTaskStatus;
import cl.camodev.wosbot.serv.impl.ServTaskManager;
import cl.camodev.wosbot.taskmanager.ITaskStatusChangeListener;
import cl.camodev.wosbot.taskmanager.model.ITaskStatusModel;

public class TaskStatusModel implements ITaskStatusModel {

	private ServTaskManager servTaskManager = ServTaskManager.getInstance();

	@Override
	public List<DTODailyTaskStatus> getDailyTaskStatusList(Long profileId) {
		return servTaskManager.getDailyTaskStatusPersistence(profileId);

	}

	@Override
	public void addTaskStatusChangeListener(ITaskStatusChangeListener taskManagerActionController) {
		ServTaskManager.getInstance().addTaskStatusChangeListener(taskManagerActionController);

	}

}
