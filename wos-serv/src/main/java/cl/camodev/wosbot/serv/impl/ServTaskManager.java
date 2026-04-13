package cl.camodev.wosbot.serv.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cl.camodev.wosbot.almac.repo.DailyTaskRepository;
import cl.camodev.wosbot.ot.DTODailyTaskStatus;
import cl.camodev.wosbot.ot.DTOTaskState;
import cl.camodev.wosbot.taskmanager.ITaskStatusChangeListener;

public class ServTaskManager {

	private List<ITaskStatusChangeListener> listeners = new ArrayList<>();

	private static final ServTaskManager INSTANCE = new ServTaskManager();

	private ConcurrentHashMap<Long, HashMap<String, DTOTaskState>> map = new ConcurrentHashMap<>();

	private ServTaskManager() {
		// Private constructor to prevent instantiation
	}

	public static ServTaskManager getInstance() {
		return INSTANCE;
	}

	private String generateKey(Integer taskId, String customTaskName) {
		if (customTaskName != null && !customTaskName.isEmpty()) {
			return taskId + "_" + customTaskName;
		}
		return String.valueOf(taskId);
	}

	public void setTaskState(Long profileId, DTOTaskState taskState) {
		String key = generateKey(taskState.getTaskId(), taskState.getCustomTaskName());
		map.computeIfAbsent(profileId, k -> new HashMap<>()).put(key, taskState);
		notifyListeners(profileId, taskState.getTaskId(), taskState);
	}

	public DTOTaskState getTaskState(Long profileId, int taskNameId) {
		return getTaskState(profileId, taskNameId, null);
	}

	public DTOTaskState getTaskState(Long profileId, int taskNameId, String customTaskName) {
		HashMap<String, DTOTaskState> tasks = map.get(profileId);
		if (tasks != null) {
			return tasks.get(generateKey(taskNameId, customTaskName));
		}
		return null;
	}

	private void notifyListeners(Long profileId, int taskNameId, DTOTaskState taskState) {
		for (ITaskStatusChangeListener listener : listeners) {
			listener.onTaskStatusChange(profileId, taskNameId, taskState);
		}
	}

	public void addTaskStatusChangeListener(ITaskStatusChangeListener taskManagerLayoutController) {

		if (!listeners.contains(taskManagerLayoutController)) {
			listeners.add(taskManagerLayoutController);
		}
	}

	public List<DTODailyTaskStatus> getDailyTaskStatusPersistence(Long profileId) {
		List<DTODailyTaskStatus> taskSchedules = DailyTaskRepository.getRepository().findDailyTasksStatusByProfile(profileId);
		if (taskSchedules != null && !taskSchedules.isEmpty()) {
			return taskSchedules;
		}
		return new ArrayList<>();
	}

}
