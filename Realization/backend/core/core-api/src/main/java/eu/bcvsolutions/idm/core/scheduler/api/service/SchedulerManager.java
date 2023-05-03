package eu.bcvsolutions.idm.core.scheduler.api.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import eu.bcvsolutions.idm.core.scheduler.api.dto.AbstractTaskTrigger;
import eu.bcvsolutions.idm.core.scheduler.api.dto.Task;
import eu.bcvsolutions.idm.core.scheduler.api.dto.filter.TaskFilter;

/**
 * Interface for scheduler service.
 * 
 * @author Radek Tomiška
 */
public interface SchedulerManager {

	/**
	 * Returns registered task definitions
	 * 
	 * @return
	 */
	List<Task> getSupportedTasks();
	
	/**
	 * Returns all scheduled tasks.
	 *
	 * @return all tasks
	 */
	List<Task> getAllTasks();
	
	/**
	 * Returns scheduled tasks by given filter.
	 *
	 * @param filter
	 * @param pageable page + sort
	 * @return tasks match given filter
	 */
	Page<Task> find(TaskFilter filter, Pageable pageable);
	
	/**
	 * Reads existed task by id.
	 * 
	 * @param taskId
	 */
	Task getTask(String taskId);
	
	/**
	 * Creates new task
	 * 
	 * @return
	 */
	Task createTask(Task task);
	
	/**
	 * Deletes task
	 * 
	 * @param taskId
	 */
	void deleteTask(String taskId);
	
	/**
	 * Run task manually
	 * 
	 * @param taskId
	 * @return
	 */
	AbstractTaskTrigger runTask(String taskId);

	/**
	 * Run task manually
	 *
	 * @param taskId
	 * @param dryRun
	 * @return
	 */
	AbstractTaskTrigger runTask(String taskId, boolean dryRun);
	
	/**
	 * Run task manually in new transaction.
	 *
	 * @param taskId
	 * @param dryRun
	 * @return
	 * @since 10.6.0
	 */
	AbstractTaskTrigger runTaskNewTransactional(String taskId, boolean dryRun);
	
	/**
	 * Interrupt given task
	 * 
	 * @param taskId
	 * @return Returns true, then task was successfully interrupt. otherwise false
	 */
	boolean interruptTask(String taskId);

	/**
	 * Creates trigger for task
	 *
	 * @param taskId task identifier
	 * @param trigger trigger to add
	 * @return trigger containing name
	 */
	AbstractTaskTrigger createTrigger(String taskId, AbstractTaskTrigger trigger);

	/**
	 * Creates trigger for task
	 *
	 * @param taskId task identifier
	 * @param trigger trigger to add
	* @param dryRun
	 * @return trigger containing name
	 */
	AbstractTaskTrigger createTrigger(String taskId, AbstractTaskTrigger trigger, boolean dryRun);

	/**
	 * Pauses trigger
	 *
	 * @param taskId task identifier
	 * @param triggerId trigger identifier
	 */
	void pauseTrigger(String taskId, String triggerId);

	/**
	 * Resumes trigger
	 *
	 * @param taskId task identifier
	 * @param triggerId trigger identifier
	 */
	void resumeTrigger(String taskId, String triggerId);
	
	/**
	 * Deletes trigger
	 *
	 * @param taskId task identifier
	 * @param triggerId trigger identifier
	 */
	void deleteTrigger(String taskId, String triggerId);

	/**
	 * Find all task with given task type
	 * 
	 * @param taskType
	 * @return
	 */
	List<Task> getAllTasksByType(Class<?> taskType);
	
	/**
	 * Update scheduled task - only parameters and descriptions
	 * 
	 * @param taskId
	 * @param description
	 * @param parameters
	 * @return
	 */
	Task updateTask(String taskId, Task task);
	
	/**
	 * Switch instanceId for creating long running tasks.
	 * All scheduled tasks, which creates long running tasks for previous instance will be updated to ctreate tasks to new instance.
	 * 
	 * @param previousInstanceId previously used instance
	 * @param newInstanceId [optional] currently configured instance will be used as default
	 * @return updated scheduled tasks count
	 * @since 11.1.0
	 */
	int switchInstanceId(String previousInstanceId, String newInstanceId);

	Page<Task> findUpcomingTasks(TaskFilter toFilter, Pageable pageable);
}
