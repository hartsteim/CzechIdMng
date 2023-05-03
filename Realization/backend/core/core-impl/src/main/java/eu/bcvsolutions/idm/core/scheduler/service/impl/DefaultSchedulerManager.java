package eu.bcvsolutions.idm.core.scheduler.service.impl;

import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.stream.Collectors;


import org.apache.commons.lang3.StringUtils;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.Trigger.TriggerState;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.utils.Key;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.utils.AutowireHelper;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.scheduler.api.dto.AbstractTaskTrigger;
import eu.bcvsolutions.idm.core.scheduler.api.dto.CronTaskTrigger;
import eu.bcvsolutions.idm.core.scheduler.api.dto.DependentTaskTrigger;
import eu.bcvsolutions.idm.core.scheduler.api.dto.NextFireTimesTaskTriggerVisitor;
import eu.bcvsolutions.idm.core.scheduler.api.dto.SimpleTaskTrigger;
import eu.bcvsolutions.idm.core.scheduler.api.dto.Task;
import eu.bcvsolutions.idm.core.scheduler.api.dto.TaskTriggerState;
import eu.bcvsolutions.idm.core.scheduler.api.dto.filter.TaskFilter;
import eu.bcvsolutions.idm.core.scheduler.api.exception.DryRunNotSupportedException;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskExecutor;
import eu.bcvsolutions.idm.core.scheduler.api.service.SchedulableTaskExecutor;
import eu.bcvsolutions.idm.core.scheduler.api.service.SchedulerManager;
import eu.bcvsolutions.idm.core.scheduler.entity.IdmDependentTaskTrigger;
import eu.bcvsolutions.idm.core.scheduler.exception.InvalidCronExpressionException;
import eu.bcvsolutions.idm.core.scheduler.exception.SchedulerException;
import eu.bcvsolutions.idm.core.scheduler.repository.IdmDependentTaskTriggerRepository;

/**
 * Default implementation of {@link SchedulerManager}.
 * This implementation adds long running task to queue and not execute them directly.
 * It is needed for choose server instance id, where task will be physically executed (more instances can read one database).
 *
 * @author Radek Tomiška
 */
public class DefaultSchedulerManager implements SchedulerManager {

	/**
	 * Name of task group
	 */
	public static final String DEFAULT_GROUP_NAME = "default";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DefaultSchedulerManager.class);
	private final ApplicationContext context;
	private final Scheduler scheduler;
	private final IdmDependentTaskTriggerRepository dependentTaskTriggerRepository;
	private ConfigurationService configurationService;

	@Autowired
	public DefaultSchedulerManager(
			ApplicationContext context,
			Scheduler scheduler,
			IdmDependentTaskTriggerRepository dependentTaskTriggerRepository) {
		Assert.notNull(context, "Context is required.");
		Assert.notNull(scheduler, "Scheduler is required.");
		Assert.notNull(dependentTaskTriggerRepository, "Repository is required.");
		//
		this.context = context;
		this.scheduler = scheduler;
		this.dependentTaskTriggerRepository = dependentTaskTriggerRepository;
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public List<Task> getSupportedTasks() {
		return context.getBeansOfType(SchedulableTaskExecutor.class)
				.entrySet()
				.stream()
				.map(entry -> {
					SchedulableTaskExecutor<?> taskExecutor = entry.getValue();
					Task task = new Task();
					task.setId(entry.getKey());
					task.setModule(taskExecutor.getModule());
					task.setTaskType((Class<? extends SchedulableTaskExecutor<?>>) AutowireHelper.getTargetClass(taskExecutor));
					task.setDescription(AutowireHelper.getBeanDescription(entry.getKey()));
					task.setDisabled(taskExecutor.isDisabled());
					if (!task.isDisabled()) {
						for (String parameterName : taskExecutor.getPropertyNames()) {
							task.getParameters().put(parameterName, null);
						}
						task.setFormDefinition(taskExecutor.getFormDefinition());
					}
					return task;
				})
				.sorted(Comparator.comparing(task -> task.getTaskType().getSimpleName(), Comparator.naturalOrder()))
			    .collect(Collectors.toList());
	}

	@Override
	public List<Task> getAllTasks() {
		return find(null, null).getContent();
	}

	@Override
	public Page<Task> find(TaskFilter filter, Pageable pageable) {
		try {
			// pageable is required internally
			Pageable internalPageable = configurePageable(pageable);

			List<Task> tasks = new ArrayList<>();
			// load scheduled tasks
			for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(DEFAULT_GROUP_NAME))) {
				Task task = getTask(jobKey);
				//
				if (passFilter(task, filter)) {
					tasks.add(task);
				}
			}

			// apply "naive" sort and pagination
			tasks = tasks
					.stream()
					.sorted((taskOne, taskTwo) -> {
						Sort sort = internalPageable.getSort();
						if (internalPageable.getSort() == null) {
							return 0;
						}
						int compareAscValue = 0;
						boolean asc = true;
						// "naive" sort implementation
						Order orderForNextFireTime = sort.getOrderFor(Task.PROPERTY_NEXT_FIRE_TIME);
						if (orderForNextFireTime != null) {
							asc = orderForNextFireTime.isAscending();
							compareAscValue = compareTasksByEarliestFireTime(taskOne, taskTwo);
						}
						Order orderForTaskType = sort.getOrderFor(Task.PROPERTY_TASK_TYPE);
						if (orderForTaskType != null) {
							asc = orderForTaskType.isAscending();
							compareAscValue = taskOne.getTaskType().getSimpleName().compareTo(taskTwo.getTaskType().getSimpleName());
						}
						Order orderForDescription = sort.getOrderFor(Task.PROPERTY_DESCRIPTION);
						if (orderForDescription != null) {
							asc = orderForDescription.isAscending();
							compareAscValue = StringUtils.compare(taskOne.getDescription(), taskTwo.getDescription());
						}
						Order orderForInstance = sort.getOrderFor(Task.PROPERTY_INSTANCE_ID);
						if (orderForInstance != null) {
							asc = orderForInstance.isAscending();
							compareAscValue = StringUtils.compare(taskOne.getInstanceId(), taskTwo.getInstanceId());
						}
						//
						return asc ? compareAscValue : compareAscValue * -1;
					})
					.collect(Collectors.toList());
			// "naive" pagination
			int first = internalPageable.getPageNumber() * internalPageable.getPageSize();
			int last = internalPageable.getPageSize() + first;
			List<Task> taskPage = tasks.subList(
					first < tasks.size() ? first : tasks.size() > 0 ? tasks.size() - 1 : 0,
					last < tasks.size() ? last : tasks.size()
			);
			//
			return new PageImpl<>(taskPage, internalPageable, tasks.size());
		} catch (org.quartz.SchedulerException ex) {
			throw new CoreException(ex);
		}
	}

	private static int compareTasksByEarliestFireTime(Task taskOne, Task taskTwo) {
		int compareAscValue;
		ZonedDateTime taskOneEarliestFireTime = null;
		ZonedDateTime taskTwoEarliestFireTime = null;
		try {
			Function<Task, ZonedDateTime> findEarliestFireTime = task -> task.getTriggers().stream()
					.map(AbstractTaskTrigger::getNextFireTime)
					.filter(Objects::nonNull)
					.min(ChronoZonedDateTime::compareTo)
					.get();
			taskOneEarliestFireTime = findEarliestFireTime.apply(taskOne);
			taskTwoEarliestFireTime = findEarliestFireTime.apply(taskTwo);
			compareAscValue = taskOneEarliestFireTime.compareTo(taskTwoEarliestFireTime);
		} catch (NoSuchElementException e) {
			if (taskOneEarliestFireTime == null) {
				compareAscValue = 1;
			} else {
				compareAscValue = -1;
			}
		}
		return compareAscValue;
	}

	@Override
	public List<Task> getAllTasksByType(Class<?> taskType){
		return this.getAllTasks()
				.stream()
				.filter(task -> {
					Class<? extends SchedulableTaskExecutor<?>> type = task.getTaskType();
					return type.equals(taskType);
				})
				.collect(Collectors.toList());
	}

	@Override
	public Task getTask(String taskId) {
		return getTask(getKey(taskId));
	}

	@Override
	public Task createTask(Task task) {
		Assert.notNull(task, "Task is required.");
		Assert.notNull(task.getInstanceId(), "Task instance identifier is required.");
		Assert.notNull(task.getTaskType(), "Task type is required.");
		//
		try {
			// task id
			String taskId = Key.createUniqueName(DEFAULT_GROUP_NAME);
			// description
			String description = task.getDescription();
			if (StringUtils.isEmpty(description)) {
				description = AutowireHelper.getBeanDescription(task.getTaskType());
			}
			// job properties
			JobDataMap jobDataMap = new JobDataMap();
			jobDataMap.put(SchedulableTaskExecutor.PARAMETER_INSTANCE_ID, task.getInstanceId());
			jobDataMap.put(SchedulableTaskExecutor.PARAMETER_MODIFIED, ZonedDateTime.now());
			task.getParameters().entrySet().forEach(entry -> {
				jobDataMap.put(entry.getKey(), entry.getValue());
			});
			// validate init method
			try {
				LongRunningTaskExecutor<?> taskExecutor = AutowireHelper.createBean(task.getTaskType());
				taskExecutor.init(jobDataMap);
			} catch (ResultCodeException ex) {
				throw ex;
			} catch (Exception ex) {
				throw new ResultCodeException(
						CoreResultCode.LONG_RUNNING_TASK_INIT_FAILED,
						ImmutableMap.of(
								"taskId", taskId,
								"taskType", task.getTaskType().getSimpleName(),
								ConfigurationService.PROPERTY_INSTANCE_ID, task.getInstanceId()),
						ex
				);
			}
			// create job detail - job definition
			JobDetail jobDetail = JobBuilder.newJob()
					.withIdentity(taskId, DEFAULT_GROUP_NAME)
					.withDescription(description)
					.ofType(task.getTaskType())
					.usingJobData(jobDataMap)
					.storeDurably()
					.build();
			// add job 
			scheduler.addJob(jobDetail, false);
			//
			LOG.debug("Job [{}] ([{}]) was created and registered", taskId, task.getTaskType());
			//
			return getTask(taskId);
		} catch (org.quartz.SchedulerException ex) {
			throw new SchedulerException(CoreResultCode.SCHEDULER_CREATE_TASK_FAILED, ex);
		}
	}

	@Override
	public Task updateTask(String taskId, Task newTask) {
		return updateTask(taskId, newTask, true);
	}

	@Override
	@Transactional
	public void deleteTask(String taskId) {
		try {
			// delete dependent task triggers - are stored in our repository
			dependentTaskTriggerRepository
				.findByDependentTaskId(taskId)
				.forEach(trigger -> {
					dependentTaskTriggerRepository.delete(trigger);
				});
			dependentTaskTriggerRepository
				.findByInitiatorTaskId(taskId)
				.forEach(trigger -> {
					dependentTaskTriggerRepository.delete(trigger);
				});
			scheduler.deleteJob(new JobKey(taskId, DEFAULT_GROUP_NAME));
		} catch (org.quartz.SchedulerException ex) {
			throw new SchedulerException(CoreResultCode.SCHEDULER_DELETE_TASK_FAILED, ex);
		}
	}

	@Override
	public AbstractTaskTrigger runTask(String taskId) {
		return runTask(taskId, false);
	}

	// rt: @Transactional not work here form FE - user waits 30s (timeout?) to run task, why?
	@Override
	public AbstractTaskTrigger runTask(String taskId, boolean dryRun) {
 		// run job - add simple trigger
 		SimpleTaskTrigger trigger = new SimpleTaskTrigger();
 		trigger.setTaskId(taskId);
 		trigger.setDescription("run manually");
 		trigger.setFireTime(ZonedDateTime.now());
 		//
		return createTrigger(taskId, trigger, dryRun);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public AbstractTaskTrigger runTaskNewTransactional(String taskId, boolean dryRun) {
		return runTask(taskId, dryRun);
	}

	@Override
	public boolean interruptTask(String taskId) {
		try {
			return scheduler.interrupt(getKey(taskId));
		} catch (org.quartz.SchedulerException ex) {
			throw new SchedulerException(CoreResultCode.SCHEDULER_INTERRUPT_TASK_FAILED, ex);
		}
	}

	@Override
	public AbstractTaskTrigger createTrigger(String taskId, AbstractTaskTrigger trigger) {
		return createTrigger(taskId, trigger, false);
	}

	@Override
	public AbstractTaskTrigger createTrigger(String taskId, AbstractTaskTrigger trigger, boolean dryRun) {
		Assert.notNull(taskId, "Task identifier is required.");
		Assert.notNull(trigger, "Trigger is required.");
		//
		// task has to support dry run mode
		Task task = getTask(taskId);
		Assert.notNull(task, "Task is required.");
		if (dryRun && !task.isSupportsDryRun()) {
			throw new DryRunNotSupportedException(task.getTaskType().getCanonicalName());
		}
		//
		String triggerId = Key.createUniqueName(taskId);
		trigger.setId(triggerId);
		trigger.setTaskId(taskId);
		//
		// TODO use of visitor pattern may be good
		if (trigger instanceof SimpleTaskTrigger) {
			createTriggerInternal(taskId, (SimpleTaskTrigger) trigger, dryRun);
		} else if (trigger instanceof CronTaskTrigger) {
			createTriggerInternal(taskId, (CronTaskTrigger) trigger, dryRun);
		} else if(trigger instanceof DependentTaskTrigger) {
			createTriggerInternal(taskId, (DependentTaskTrigger) trigger);
		} else {
			throw new SchedulerException(CoreResultCode.SCHEDULER_UNSUPPORTED_TASK_TRIGGER, ImmutableMap.of("trigger", trigger.getClass()));
		}
		return trigger;
	}

	@Override
	public void deleteTrigger(String taskId, String triggerId) {
		try {
			if (!scheduler.unscheduleJob(new TriggerKey(triggerId, taskId))) {
				try {
					dependentTaskTriggerRepository.deleteById(DtoUtils.toUuid(triggerId));
				} catch (ClassCastException ex) {
					throw new SchedulerException(CoreResultCode.SCHEDULER_DELETE_TRIGGER_FAILED, ex);
				}
			}
		} catch (org.quartz.SchedulerException ex) {
			throw new SchedulerException(CoreResultCode.SCHEDULER_DELETE_TRIGGER_FAILED, ex);
		}
	}

	@Override
	public void pauseTrigger(String taskId, String triggerId) {
		try {
			scheduler.pauseTrigger(new TriggerKey(triggerId, taskId));
		} catch (org.quartz.SchedulerException ex) {
			throw new SchedulerException(CoreResultCode.SCHEDULER_PAUSE_TRIGGER_FAILED, ex);
		}
	}

	@Override
	public void resumeTrigger(String taskId, String triggerId) {
		try {
			scheduler.resumeTrigger(new TriggerKey(triggerId, taskId));
		} catch (org.quartz.SchedulerException ex) {
			throw new SchedulerException(CoreResultCode.SCHEDULER_RESUME_TRIGGER_FAILED, ex);
		}
	}

	@Override
	@Transactional
	public int switchInstanceId(String previousInstanceId, String newInstanceId) {
		Assert.hasLength(previousInstanceId, "Previous asynchronous instance is required.");
		//
		String asynchronousInstanceId = getConfigurationService().getInstanceId();
		int count = 0;
		// resolve default
		if (StringUtils.isEmpty(newInstanceId)) {
			newInstanceId = asynchronousInstanceId;
		}
		if (previousInstanceId.equals(newInstanceId)) {
			LOG.info("Previous instance is same as newly used for asynchronous task processing [{}].", newInstanceId);
			//
			return count;
		}
		// find all tasks with old instance and update them to use new
		for (Task task : getAllTasks()) {
			Map<String, String> parameters = task.getParameters();

			String taskInstanceId = parameters.get(SchedulableTaskExecutor.PARAMETER_INSTANCE_ID);
			if (previousInstanceId.equals(taskInstanceId)) {
				task.setInstanceId(newInstanceId);
				parameters.put(SchedulableTaskExecutor.PARAMETER_INSTANCE_ID, newInstanceId);
				// set new instance id
				updateTask(task.getId(), task, false);
				//
				count++;
			}
		}
		//
		return count;
	}

	@Override
	public Page<Task> findUpcomingTasks(TaskFilter filter, Pageable pageable) {
		try {
			// pageable is required internally
			Pageable internalPageable = configurePageable(pageable);

			List<Task> filteredTasks = new ArrayList<>();
			List<Task> allTasks = new ArrayList<>();
			// load scheduled tasks
			for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(DEFAULT_GROUP_NAME))) {
				Task task = getTask(jobKey);
				allTasks.add(task);
				//
				if (passFilter(task, filter)) {
					filteredTasks.add(task);

					// add nextfiretimes
					int nextFireTimesLimitCount = 1;
					ZonedDateTime nextFireTimeLimitDate = ZonedDateTime.now().plusDays(1);
					Object promptNextFireTimesLimitCount = filter.getData().getFirst("nextFireTimesLimitCount");
					if (promptNextFireTimesLimitCount != null) {
						nextFireTimesLimitCount = Integer.parseInt(promptNextFireTimesLimitCount.toString());
					}
					Object nextFireTimesLimitSeconds = filter.getData().getFirst("nextFireTimesLimitSeconds");
					if (nextFireTimesLimitSeconds != null) {
						nextFireTimeLimitDate = ZonedDateTime.now().plusSeconds(Long.parseLong(nextFireTimesLimitSeconds.toString()));
					}
					NextFireTimesTaskTriggerVisitor visitor = new NextFireTimesTaskTriggerVisitor(nextFireTimesLimitCount, nextFireTimeLimitDate, scheduler, jobKey);
					task.getTriggers().forEach(t -> t.accept(visitor));
				}
			}

			for (Task task : allTasks) {
				for (AbstractTaskTrigger trigger : task.getTriggers()) {
					if (trigger instanceof DependentTaskTrigger) {
						String initiatorTaskId = ((DependentTaskTrigger) trigger).getInitiatorTaskId();
						Optional<Task> initiatorTask = filteredTasks.stream().filter(task1 -> task1.getId().equals(initiatorTaskId)).findAny();
						initiatorTask.ifPresent(task1 -> {
							List<Task> dependentTasks = task1.getDependentTasks();
							dependentTasks.add(task);
						});
					}
				}
			}

			// for each task sort triggers by next fire time
			for (Task task: allTasks) {
				task.getTriggers().sort((t1, t2) -> {
					if (t1.getNextFireTime() == null) {
						return 1;
					} else if (t2.getNextFireTime() == null) {
						return -1;
					} else {
						return t1.getNextFireTime().compareTo(t2.getNextFireTime());
					}
				});
			}

			// apply "naive" sort and pagination
			filteredTasks = filteredTasks
					.stream()
					// filter out purely dependent tasks, they are contained in their dependent tasks
					.filter(task -> !task.getTriggers().stream().allMatch(trigger -> trigger.getNextFireTime() == null))
					.sorted(DefaultSchedulerManager::compareTasksByEarliestFireTime)
					.collect(Collectors.toList());
			// "naive" pagination
			int first = internalPageable.getPageNumber() * internalPageable.getPageSize();
			int last = internalPageable.getPageSize() + first;
			List<Task> taskPage = filteredTasks.subList(
					first < filteredTasks.size() ? first : filteredTasks.size() > 0 ? filteredTasks.size() - 1 : 0,
					last < filteredTasks.size() ? last : filteredTasks.size()
			);
			//
			return new PageImpl<>(taskPage, internalPageable, filteredTasks.size());
		} catch (org.quartz.SchedulerException ex) {
			throw new CoreException(ex);
		}
	}

	private static Pageable configurePageable(Pageable pageable) {
		Pageable internalPageable;
		if (pageable == null) {
			internalPageable = PageRequest.of(0, Integer.MAX_VALUE);
		} else {
			internalPageable = pageable;
		}
		return internalPageable;
	}

	/**
	 * Returns job key to given taskId
	 *
	 * @param taskId
	 * @return
	 */
	protected JobKey getKey(String taskId) {
		return new JobKey(taskId, DEFAULT_GROUP_NAME);
	}

	/**
	 * Returns task by given key
	 *
	 * @param jobKey
	 * @return task dto
	 */
	@SuppressWarnings("unchecked")
	private Task getTask(JobKey jobKey) {
		try {
			JobDetail jobDetail = scheduler.getJobDetail(jobKey);
			if (jobDetail == null) {
				// job does not exists
				return null;
			}
			Task task = new Task();
			// task setting
			task.setId(jobKey.getName());
			// app context is needed here
			SchedulableTaskExecutor<?> taskExecutor = (SchedulableTaskExecutor<?>) context.getAutowireCapableBeanFactory()
					.createBean(jobDetail.getJobClass());
			task.setTaskType((Class<? extends SchedulableTaskExecutor<?>>) AutowireHelper.getTargetClass(taskExecutor));
			task.setDescription(jobDetail.getDescription());
			JobDataMap jobDataMap = jobDetail.getJobDataMap();
			task.setInstanceId(jobDataMap.getString(SchedulableTaskExecutor.PARAMETER_INSTANCE_ID));
			task.setModified((ZonedDateTime) jobDataMap.get(SchedulableTaskExecutor.PARAMETER_MODIFIED));
			task.setTriggers(new ArrayList<>());
			// task properties
			for (Entry<String, Object> entry : jobDataMap.entrySet()) {
				task.getParameters().put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
			}
			task.setDisabled(taskExecutor.isDisabled());
			if (!task.isDisabled()) {
				task.setSupportsDryRun(taskExecutor.supportsDryRun());
				task.setFormDefinition(taskExecutor.getFormDefinition());
				task.setRecoverable(taskExecutor.isRecoverable());
			} else {
				LOG.warn("Task [{}] is disabled and cannot be executed, remove schedule for this task to hide this warning.",
						task.getTaskType().getSimpleName());
			}
			// scheduled triggers - native
			for (Trigger trigger : scheduler.getTriggersOfJob(jobKey)) {
				TriggerState state = scheduler.getTriggerState(trigger.getKey());
				if (trigger instanceof CronTrigger) {
					task.getTriggers().add(new CronTaskTrigger(task.getId(), (CronTrigger) trigger, TaskTriggerState.convert(state)));
				} else if (trigger instanceof SimpleTrigger) {
					task.getTriggers().add(new SimpleTaskTrigger(task.getId(), (SimpleTrigger) trigger, TaskTriggerState.convert(state)));
				} else {
					LOG.warn("Job '{}' ({}) has registered trigger of unsupported type {}", jobKey,
							jobDetail.getJobClass(), trigger);
				}
			}
			// dependent tasks
			dependentTaskTriggerRepository
				.findByDependentTaskId(jobKey.getName())
				.forEach(dependentTask -> {
					task.getTriggers().add(new DependentTaskTrigger(task.getId(), dependentTask.getId(), dependentTask.getInitiatorTaskId()));
				});
			return task;
		} catch (org.quartz.SchedulerException ex) {
			if (ex.getCause() instanceof ClassNotFoundException) {
				deleteTask(jobKey.getName());
				LOG.warn("Job [{}] inicialization failed, job class was removed, scheduled task is removed.", jobKey, ex);
				return null;
			}
			throw new CoreException(ex);
		} catch (BeansException | IllegalArgumentException ex) {
			deleteTask(jobKey.getName());
			LOG.warn("Job [{}] inicialization failed, scheduled task is removed", jobKey, ex);
			return null;
		}
	}

	private void createTriggerInternal(String taskId, CronTaskTrigger trigger, boolean dryRun) {
		CronTaskTrigger cronTaskTrigger = (CronTaskTrigger) trigger;
		CronScheduleBuilder cronBuilder;
		try {
			cronBuilder = CronScheduleBuilder
					.cronSchedule(cronTaskTrigger.getCron())
					.withMisfireHandlingInstructionFireAndProceed() // prevent to skip job execution.
					.inTimeZone(TimeZone.getDefault());
		} catch(RuntimeException ex) {
			throw new InvalidCronExpressionException(cronTaskTrigger.getCron(), ex);
		}
		//
		try {
			ZonedDateTime executeDate = trigger.getExecuteDate(); // postpone first fire time
			scheduler.scheduleJob(
					TriggerBuilder.newTrigger()
						.withIdentity(trigger.getId(), taskId)
						.forJob(getKey(taskId))
						.withDescription(cronTaskTrigger.getDescription())
						.withSchedule(cronBuilder)
						.usingJobData(SchedulableTaskExecutor.PARAMETER_DRY_RUN, dryRun)
						.startAt(executeDate == null ? new Date() : Date.from(executeDate.toInstant()))
						.build());
		} catch (org.quartz.SchedulerException ex) {
			throw new SchedulerException(CoreResultCode.SCHEDULER_CREATE_TRIGGER_FAILED, ex);
		}
	}

	private void createTriggerInternal(String taskId, SimpleTaskTrigger trigger, boolean dryRun) {
		try {
			scheduler.scheduleJob(
					TriggerBuilder.newTrigger()
						.withIdentity(trigger.getId(), taskId)
						.forJob(getKey(taskId))
						.withDescription(trigger.getDescription())
						.withSchedule(
								SimpleScheduleBuilder
									.simpleSchedule()
									.withMisfireHandlingInstructionFireNow()
						)
						.startAt(Date.from(((SimpleTaskTrigger) trigger).getFireTime().toInstant()))
						.usingJobData(SchedulableTaskExecutor.PARAMETER_DRY_RUN, dryRun)
						.build());
		} catch (org.quartz.SchedulerException ex) {
			throw new SchedulerException(CoreResultCode.SCHEDULER_CREATE_TRIGGER_FAILED, ex);
		}
	}

	private void createTriggerInternal(String taskId, DependentTaskTrigger trigger) {
		dependentTaskTriggerRepository.save(new IdmDependentTaskTrigger(trigger.getInitiatorTaskId(), taskId));
	}

	/**
	 * Returns true, when given processor pass given filter
	 *
	 * @param processor
	 * @param filter
	 * @return
	 */
	private boolean passFilter(Task task, TaskFilter filter) {
		if (task == null) {
			return false;
		}
		if (filter == null) {
			// empty filter
			return true;
		}
		// id - not supported
		if (filter.getId() != null) {
			throw new UnsupportedOperationException("Filtering event processors by [id] is not supported.");
		}
		// text - lowercase like in type, description
		String text = filter.getText();
		if (StringUtils.isNotEmpty(text)) {
			text = filter.getText().toLowerCase();
			if (!task.getTaskType().getCanonicalName().toLowerCase().contains(text)
					&& (StringUtils.isEmpty(task.getDescription()) || !task.getDescription().toLowerCase().contains(text))) {
				return false;
			}
		}
		String instanceId = filter.getInstanceId();
		if (StringUtils.isNotEmpty(instanceId)) {
			if (!StringUtils.equals(instanceId, task.getInstanceId())) {
				return false;
			}
		}
		String taskType = filter.getTaskType();
		if (StringUtils.isNotEmpty(taskType)) {
			if (!StringUtils.equals(taskType, task.getTaskType().getCanonicalName())) {
				return false;
			}
		}
		//
		return true;
	}

	private Task updateTask(String taskId, Task newTask, boolean validate) {
		Assert.notNull(taskId, "Task identifier is required.");
		//
		Task task = getTask(taskId);
		String description = newTask.getDescription();
		if (StringUtils.isEmpty(description)) {
			description = AutowireHelper.getBeanDescription(task.getTaskType());
		}
		try {
			// job properties
			JobDataMap jobDataMap = new JobDataMap();
			newTask.getParameters().entrySet().forEach(entry -> {
				jobDataMap.put(entry.getKey(), entry.getValue());
			});
			jobDataMap.put(SchedulableTaskExecutor.PARAMETER_INSTANCE_ID, newTask.getInstanceId());
			jobDataMap.put(SchedulableTaskExecutor.PARAMETER_MODIFIED, ZonedDateTime.now());
			// validate init method
			if (validate) {
				try {
					LongRunningTaskExecutor<?> taskExecutor = AutowireHelper.createBean(task.getTaskType());
					taskExecutor.init(jobDataMap);
				} catch (ResultCodeException ex) {
					throw ex;
				} catch (Exception ex) {
					throw new ResultCodeException(
							CoreResultCode.LONG_RUNNING_TASK_INIT_FAILED,
							ImmutableMap.of(
									"taskId", taskId,
									"taskType", task.getTaskType().getSimpleName(),
									ConfigurationService.PROPERTY_INSTANCE_ID, task.getInstanceId()),
							ex
					);
				}
			}
			// create job detail - job definition
			JobDetail jobDetail = JobBuilder.newJob().withIdentity(task.getId(), DEFAULT_GROUP_NAME)
					.withDescription(description)
					.ofType(task.getTaskType())
					.usingJobData(jobDataMap)
					.storeDurably().build();
			// add job
			scheduler.addJob(jobDetail, true);
			//
			LOG.debug("Job '{}' ({}) was updated and registered", task.getId(), task.getTaskType());
			//
		} catch (org.quartz.SchedulerException ex) {
			throw new SchedulerException(CoreResultCode.SCHEDULER_CREATE_TASK_FAILED, ex);
		}
		return getTask(task.getId());
	}

	private ConfigurationService getConfigurationService() {
		if (configurationService == null) {
			configurationService = context.getBean(ConfigurationService.class);
		}
		return configurationService;
	}
}
