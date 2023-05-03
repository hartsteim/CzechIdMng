package eu.bcvsolutions.idm.core.scheduler.api.dto;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;


import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

import eu.bcvsolutions.idm.core.api.exception.CoreException;

/** Visitor for setting next fire times for task triggers.
 * @author Jan Potočiar
 */
public class NextFireTimesTaskTriggerVisitor implements TaskTriggerVisitor {

	private final int nextFireTimesLimitCount;
	private final ZonedDateTime nextFireTimeLimitDate;
	private final Scheduler scheduler;
	private final JobKey jobKey;
	public NextFireTimesTaskTriggerVisitor(int nextFireTimesLimitCount, ZonedDateTime nextFireTimeLimitDate, Scheduler scheduler, JobKey jobKey) {
		this.nextFireTimesLimitCount = nextFireTimesLimitCount;
		this.nextFireTimeLimitDate = nextFireTimeLimitDate;
		this.scheduler = scheduler;
		this.jobKey = jobKey;
	}

	@Override
	public void visit(CronTaskTrigger trigger) {
		List<ZonedDateTime> nextFireTimes = trigger.getNextFireTimes();
		ZonedDateTime lastFireTime = trigger.getNextFireTime();
		List<? extends Trigger> schedulerTriggers;
		try {
			schedulerTriggers = scheduler.getTriggersOfJob(jobKey);
		} catch (SchedulerException e) {
			throw new CoreException(e);
		}
		Trigger schedulerTrigger = schedulerTriggers.stream().filter(t -> t.getJobKey().equals(jobKey)).findFirst().get();
		ZonedDateTime nextScheduledFireTime = schedulerTrigger.getFireTimeAfter(Date.from(lastFireTime.toInstant())).toInstant().atZone(ZoneId.systemDefault());
		while (nextFireTimes.size() < nextFireTimesLimitCount && nextScheduledFireTime.isBefore(nextFireTimeLimitDate)) {
			nextFireTimes.add(nextScheduledFireTime);
			nextScheduledFireTime = schedulerTrigger.getFireTimeAfter(Date.from(nextScheduledFireTime.toInstant())).toInstant().atZone(ZoneId.systemDefault());
		}
		trigger.setNextFireTimes(nextFireTimes);
	}

	@Override
	public void visit(DependentTaskTrigger trigger) {
		// Do nothing, nextFireTimes are not defined for dependent triggers
	}

	@Override
	public void visit(SimpleTaskTrigger trigger) {
		// Do nothing, nextFireTimes are not defined for simple triggers
	}
}
