package cl.camodev.wosbot.taskmanager.model.impl;

import java.util.List;

import cl.camodev.wosbot.ot.DTODailyTaskStatus;

@FunctionalInterface
public interface TaskCallback {

	void onTasksLoaded(List<DTODailyTaskStatus> profiles);

}
