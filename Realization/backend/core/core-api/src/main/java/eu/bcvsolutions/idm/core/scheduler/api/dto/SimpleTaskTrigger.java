package eu.bcvsolutions.idm.core.scheduler.api.dto;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import javax.validation.constraints.NotNull;


import org.quartz.SimpleTrigger;

/**
 * Simple time task trigger
 */
public class SimpleTaskTrigger extends AbstractTaskTrigger {

	private static final long serialVersionUID = 1L;
	
	@NotNull
	private ZonedDateTime fireTime;
	
	public SimpleTaskTrigger() {
	}
	
	/**
	 * Creates a new instance using trigger and state
	 * 
	 * @param trigger trigger
	 * @param state state
	 */
	public SimpleTaskTrigger(String taskId, SimpleTrigger trigger, TaskTriggerState state) {
		super(taskId, trigger, state);
		//
		if (trigger.getStartTime() != null) {
			this.fireTime = trigger.getStartTime().toInstant().atZone(ZoneId.systemDefault());
		}
	}
	
	public ZonedDateTime getFireTime() {
		return fireTime;
	}
	
	public void setFireTime(ZonedDateTime fireTime) {
		this.fireTime = fireTime;
	}

	@Override
	public void accept(TaskTriggerVisitor visitor) {
		visitor.visit(this);
	}
}
