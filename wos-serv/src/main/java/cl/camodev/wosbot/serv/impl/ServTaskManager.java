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

	private ConcurrentHashMap<Long, HashMap<Integer, DTOTaskState>> map = new ConcurrentHashMap<>();

	private ServTaskManager() {
		// Private constructor to prevent instantiation
	}

	public static ServTaskManager getInstance() {
		return INSTANCE;
	}

	public void setTaskState(Long profileId, DTOTaskState taskState) {
		map.computeIfAbsent(profileId, k -> new HashMap<>()).put(taskState.getTaskId(), taskState);
		notifyListeners(profileId, taskState.getTaskId(), taskState);
	}

	public DTOTaskState getTaskState(Long profileId, int taskNameId) {
		HashMap<Integer, DTOTaskState> tasks = map.get(profileId);
		if (tasks != null) {
			return tasks.get(taskNameId);
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
		Map<Integer, DTODailyTaskStatus> taskSchedules = DailyTaskRepository.getRepository().findDailyTasksStatusByProfile(profileId);
		if (taskSchedules != null && !taskSchedules.isEmpty()) {
			return new ArrayList<>(taskSchedules.values());
		}
		return new ArrayList<>();
	}

}
