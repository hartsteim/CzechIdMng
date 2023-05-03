package eu.bcvsolutions.idm.core.scheduler.api.dto;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.quartz.CronTrigger;

/**
 * Cron task trigger
 * 
 * @author Radek Tomiška
 */
public class CronTaskTrigger extends AbstractTaskTrigger {

	private static final long serialVersionUID = 1L;
	
	@NotNull
	private String cron;
	private List<ZonedDateTime> nextFireTimes;
	
	public CronTaskTrigger() {
	}
	
	/**
	 * Creates a new instance using trigger and state
	 * 
	 * @param trigger trigger
	 * @param state state
	 */
	public CronTaskTrigger(String taskId, CronTrigger trigger, TaskTriggerState state) {
		super(taskId, trigger, state);
		this.nextFireTimes = new ArrayList<>();
		this.nextFireTimes.add(this.getNextFireTime());
		cron = trigger.getCronExpression();
	}
	
	public String getCron() {
		return cron;
	}
	
	public void setCron(String cron) {
		this.cron = cron;
	}

	public List<ZonedDateTime> getNextFireTimes() {
		return nextFireTimes;
	}

	public void setNextFireTimes(List<ZonedDateTime> nextFireTimes) {
		this.nextFireTimes = nextFireTimes;
	}

	@Override
	public void accept(TaskTriggerVisitor visitor) {
		visitor.visit(this);
	}
}
