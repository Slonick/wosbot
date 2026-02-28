package cl.camodev.wosbot.taskmanager;

import cl.camodev.wosbot.ot.DTOTaskState;

public interface ITaskStatusChangeListener {

	public void onTaskStatusChange(Long profileId, int taskNameId, DTOTaskState taskState);

}