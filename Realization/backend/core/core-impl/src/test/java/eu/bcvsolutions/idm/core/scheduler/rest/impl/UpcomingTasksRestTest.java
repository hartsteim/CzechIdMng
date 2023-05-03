package eu.bcvsolutions.idm.core.scheduler.rest.impl;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.MultiValueMap;

import eu.bcvsolutions.idm.core.api.rest.BaseController;
import eu.bcvsolutions.idm.core.scheduler.ObserveLongRunningTaskEndProcessor;
import eu.bcvsolutions.idm.core.scheduler.api.dto.CronTaskTrigger;
import eu.bcvsolutions.idm.core.scheduler.api.dto.DependentTaskTrigger;
import eu.bcvsolutions.idm.core.scheduler.api.dto.SimpleTaskTrigger;
import eu.bcvsolutions.idm.core.scheduler.api.dto.Task;
import eu.bcvsolutions.idm.core.scheduler.api.dto.filter.TaskFilter;
import eu.bcvsolutions.idm.core.scheduler.api.service.SchedulableTaskExecutor;
import eu.bcvsolutions.idm.core.scheduler.api.service.SchedulerManager;
import eu.bcvsolutions.idm.core.scheduler.service.impl.TestSchedulableTask;
import eu.bcvsolutions.idm.test.api.AbstractRestTest;
import eu.bcvsolutions.idm.test.api.TestHelper;

public class UpcomingTasksRestTest extends AbstractRestTest {
	@Autowired
	private SchedulerManager manager;

	// helper field for cleaning up after each test
	private List<Task> createdTasks;

	@Before
	public void init() {
		createdTasks = new ArrayList<>();
	}

	@After
	public void clean() {
		createdTasks.forEach(task -> manager.deleteTask(task.getId()));
	}

	@Test
	public void testOneTaskWithCronTriggerQueryParams() {
		String instanceId = getHelper().createName();
		Task task = createTask(TestSchedulableTask.class, instanceId, "mock" + getHelper().createName());

		CronTaskTrigger cronTrigger = new CronTaskTrigger();
		cronTrigger.setTaskId(task.getId());
		// At the start of every hour
		cronTrigger.setCron("0 0 * * * ?");
		manager.createTrigger(task.getId(), cronTrigger);

		TaskFilter filter = new TaskFilter();
		filter.setInstanceId(instanceId);
		List<Task> results = find(filter);
		Assert.assertNotNull(results.get(0).getTriggers());
		Assert.assertEquals(1, results.get(0).getTriggers().size());
		CronTaskTrigger realTrigger = (CronTaskTrigger) results.get(0).getTriggers().get(0);
		Assert.assertEquals(1, realTrigger.getNextFireTimes().size());

		filter.set("nextFireTimesLimitCount", 100);
		List<Task> resultsLimitHundred = find(filter);
		CronTaskTrigger realTriggerLimitHundred = (CronTaskTrigger) resultsLimitHundred.get(0).getTriggers().get(0);
		// implicit limit of 1 day applies
		Assert.assertEquals(24, realTriggerLimitHundred.getNextFireTimes().size());

		filter.set("nextFireTimesLimitCount", 10);
		List<Task> resultsLimitTen = find(filter);
		CronTaskTrigger realTriggerLimitTen = (CronTaskTrigger) resultsLimitTen.get(0).getTriggers().get(0);
		// explicit limit of 10 records applies
		Assert.assertEquals(10, realTriggerLimitTen.getNextFireTimes().size());

		filter.set("nextFireTimesLimitCount", 10);
		filter.set("nextFireTimesLimitSeconds", 60*60*5); // 5 hours
		List<Task> resultsLimitFiveHours = find(filter);
		CronTaskTrigger realTriggerLimitFiveHours = (CronTaskTrigger) resultsLimitFiveHours.get(0).getTriggers().get(0);
		// explicit limit of 10 records, 5 hours applies (the lesser limit will apply - in this case 5)
		Assert.assertEquals(5, realTriggerLimitFiveHours.getNextFireTimes().size());
	}

	@Test
	public void testOneTaskWithSimpleTrigger() {
		String instanceId = getHelper().createName();
		Task task = createTask(TestSchedulableTask.class, instanceId, "mock" + getHelper().createName());

		SimpleTaskTrigger trigger = new SimpleTaskTrigger();
		trigger.setTaskId(task.getId());
		trigger.setFireTime(ZonedDateTime.now().plusMinutes(5));
		manager.createTrigger(task.getId(), trigger);

		TaskFilter filter = new TaskFilter();
		filter.setInstanceId(instanceId);
		List<Task> results = find(filter);
		//
		Assert.assertEquals(1, results.size());
		Assert.assertNotNull(results.get(0).getTriggers());
		Assert.assertEquals(1, results.get(0).getTriggers().size());
	}

	@Test
	public void testTwoTasksCronAndDependent() {
		String instanceId = getHelper().createName();
		Task task = createTask(TestSchedulableTask.class, instanceId, "mock" + getHelper().createName());
		Task taskDependent = createTask(TestSchedulableTask.class, getHelper().createName(), "mock" + getHelper().createName());

		SimpleTaskTrigger simpleTaskTrigger = new SimpleTaskTrigger();
		simpleTaskTrigger.setFireTime(ZonedDateTime.now().plusMinutes(5));
		manager.createTrigger(task.getId(), simpleTaskTrigger);

		DependentTaskTrigger trigger = new DependentTaskTrigger();
		trigger.setInitiatorTaskId(task.getId());
		manager.createTrigger(taskDependent.getId(), trigger);

		TaskFilter filter = new TaskFilter();
		filter.setTaskType(TestSchedulableTask.class.getName());
		List<Task> results = find(filter);
		//
		Assert.assertEquals(1, results.size());
		Task taskFetched = results.get(0);
		// only the cron task fetched, not the dependent
		// tasks without nextFireTime are not directly returned
		Assert.assertEquals(task.getId(), taskFetched.getId());
		Assert.assertNotNull(taskFetched.getTriggers());
		Assert.assertEquals(1, taskFetched.getTriggers().size());
		Assert.assertEquals(1, taskFetched.getDependentTasks().size());
		Assert.assertEquals(taskDependent.getId(), taskFetched.getDependentTasks().get(0).getId());
	}

	// a more complex scenario
	// First runs A, then B, then C, C and D and E are dependent on A, E is also dependent on C
	// A - EDC(- E)
	// |
	// B
	// |
	// C - E
	@Test
	public void testFiveTasksComplexScenario() {
		Task taskA = createTask(TestSchedulableTask.class, getHelper().createName(), "mock" + getHelper().createName());
		SimpleTaskTrigger simpleTaskTriggerA = new SimpleTaskTrigger();
		simpleTaskTriggerA.setFireTime(ZonedDateTime.now().plusMinutes(5));
		manager.createTrigger(taskA.getId(), simpleTaskTriggerA);

		Task taskB = createTask(TestSchedulableTask.class, getHelper().createName(), "mock" + getHelper().createName());
		SimpleTaskTrigger simpleTaskTriggerB = new SimpleTaskTrigger();
		simpleTaskTriggerB.setFireTime(ZonedDateTime.now().plusMinutes(10));
		manager.createTrigger(taskB.getId(), simpleTaskTriggerB);

		Task taskC = createTask(TestSchedulableTask.class, getHelper().createName(), "mock" + getHelper().createName());
		SimpleTaskTrigger simpleTaskTriggerC = new SimpleTaskTrigger();
		simpleTaskTriggerC.setFireTime(ZonedDateTime.now().plusMinutes(15));
		manager.createTrigger(taskC.getId(), simpleTaskTriggerC);
		DependentTaskTrigger dependentTaskTriggerC = new DependentTaskTrigger();
		dependentTaskTriggerC.setInitiatorTaskId(taskA.getId());
		manager.createTrigger(taskC.getId(), dependentTaskTriggerC);

		Task taskD = createTask(TestSchedulableTask.class, getHelper().createName(), "mock" + getHelper().createName());
		DependentTaskTrigger dependentTaskTriggerD = new DependentTaskTrigger();
		dependentTaskTriggerD.setInitiatorTaskId(taskA.getId());
		manager.createTrigger(taskD.getId(), dependentTaskTriggerD);

		Task taskE = createTask(TestSchedulableTask.class, getHelper().createName(), "mock" + getHelper().createName());
		DependentTaskTrigger dependentTaskTriggerEA = new DependentTaskTrigger();
		dependentTaskTriggerEA.setInitiatorTaskId(taskA.getId());
		manager.createTrigger(taskE.getId(), dependentTaskTriggerEA);
		DependentTaskTrigger dependentTaskTriggerEC = new DependentTaskTrigger();
		dependentTaskTriggerEC.setInitiatorTaskId(taskC.getId());
		manager.createTrigger(taskE.getId(), dependentTaskTriggerEC);

		TaskFilter filter = new TaskFilter();
		filter.setTaskType(TestSchedulableTask.class.getName());
		List<Task> results = find(filter);

		// only A, B, C tasks are directly visible and sorted by nextFireTime
		Assert.assertEquals(3, results.size());
		Assert.assertEquals(taskA.getId(), results.get(0).getId());
		Assert.assertEquals(taskB.getId(), results.get(1).getId());
		Assert.assertEquals(taskC.getId(), results.get(2).getId());
		// A has dependent tasks - C, D, E
		Set<String> idsCDE = Set.of(taskC.getId(), taskD.getId(), taskE.getId());
		Set<String> idsDependentTasksOnA = results.get(0).getDependentTasks().stream().map(Task::getId).collect(Collectors.toSet());
		Assert.assertEquals(idsCDE, idsDependentTasksOnA);
		// C knows E is dependent on it
		Assert.assertEquals(1, results.get(2).getDependentTasks().size());
		Assert.assertEquals(taskE.getId(), results.get(2).getDependentTasks().get(0).getId());
	}

	// a task with multiple triggers gets them sorted
	@Test
	public void testTaskWithMultipleTriggersGetsThemSorted() {
		Task task = createTask(TestSchedulableTask.class, getHelper().createName(), "mock" + getHelper().createName());

		// add 4 triggers, NOT sorted by fireTime and one without fireTime (dependent task)
		Task dependentTask = createTask(TestSchedulableTask.class, getHelper().createName(), "mock" + getHelper().createName());
		DependentTaskTrigger dependentTaskTrigger = new DependentTaskTrigger();
		dependentTaskTrigger.setInitiatorTaskId(dependentTask.getId());
		manager.createTrigger(task.getId(), dependentTaskTrigger);

		SimpleTaskTrigger simpleTaskTriggerA = new SimpleTaskTrigger();
		simpleTaskTriggerA.setFireTime(ZonedDateTime.now().plusMinutes(5));
		manager.createTrigger(task.getId(), simpleTaskTriggerA);

		SimpleTaskTrigger simpleTaskTriggerB = new SimpleTaskTrigger();
		simpleTaskTriggerB.setFireTime(ZonedDateTime.now().plusMinutes(15));
		manager.createTrigger(task.getId(), simpleTaskTriggerB);

		SimpleTaskTrigger simpleTaskTriggerC = new SimpleTaskTrigger();
		simpleTaskTriggerC.setFireTime(ZonedDateTime.now().plusMinutes(10));
		manager.createTrigger(task.getId(), simpleTaskTriggerC);

		TaskFilter filter = new TaskFilter();
		filter.setTaskType(TestSchedulableTask.class.getName());
		List<Task> results = find(filter);

		Assert.assertEquals(simpleTaskTriggerA.getId(), results.get(0).getTriggers().get(0).getId());
		Assert.assertEquals(simpleTaskTriggerC.getId(), results.get(0).getTriggers().get(1).getId());
		Assert.assertEquals(simpleTaskTriggerB.getId(), results.get(0).getTriggers().get(2).getId());
		// dependent task trigger has no fireTime and is last
		Assert.assertEquals(null, results.get(0).getTriggers().get(3).getNextFireTime());
	}

	protected List<Task> find(TaskFilter filter) {
		MultiValueMap<String, String> queryParams = toQueryParams(filter);
		queryParams.set("size", "10000");
		//
		try {
			String response = getMockMvc().perform(get(BaseController.BASE_PATH + "/upcoming-tasks")
							.with(authentication(getAdminAuthentication()))
							.params(queryParams)
							.contentType(TestHelper.HAL_CONTENT_TYPE))
					.andExpect(status().isOk())
					.andExpect(content().contentType(TestHelper.HAL_CONTENT_TYPE))
					.andReturn()
					.getResponse()
					.getContentAsString();
			//
			return toDtos(response, Task.class);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to find entities", ex);
		}
	}

	private Task createTask(
			Class<? extends SchedulableTaskExecutor<?>> taskType,
			String instanceId,
			String description) {
		Task task = new Task();
		task.setInstanceId(instanceId);
		task.setTaskType(taskType);
		task.setDescription(description);
		task.getParameters().put(ObserveLongRunningTaskEndProcessor.RESULT_PROPERTY, "mock");
		//
		Task createdTask = manager.createTask(task);
		createdTasks.add(createdTask);
		return createdTask;
	}
}
