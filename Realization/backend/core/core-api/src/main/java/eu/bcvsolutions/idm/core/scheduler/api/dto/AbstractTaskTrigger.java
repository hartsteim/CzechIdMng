package eu.bcvsolutions.idm.core.scheduler.api.dto;

import java.io.Serializable;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;


import org.quartz.Trigger;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;

/**
 * Base class for task triggers
 * 
 * @author Radek Tomiška
 */
@JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "_type")
@JsonSubTypes({
	@JsonSubTypes.Type(value = SimpleTaskTrigger.class),
	@JsonSubTypes.Type(value = CronTaskTrigger.class),
	@JsonSubTypes.Type(value = DependentTaskTrigger.class)
})
public abstract class AbstractTaskTrigger implements BaseDto, TaskTriggerVisitee {

	private static final long serialVersionUID = 1L;
	//
	@JsonDeserialize(as = String.class)
	private String id;
	@NotNull
	private String taskId;
	@Size(max = DefaultFieldLengths.DESCRIPTION)
	private String description;	
	private ZonedDateTime nextFireTime;
	private ZonedDateTime previousFireTime;
	private TaskTriggerState state;
	private ZonedDateTime executeDate;

	public AbstractTaskTrigger() {
	}
	
	/**
	 * New instance by task and trigger id only 
	 * 
	 * @param taskId
	 * @param triggerId
	 */
	public AbstractTaskTrigger(String taskId, String triggerId) {
		this.taskId = taskId;
		this.id = triggerId;
	}
	
	/**
	 * Creates a new instance using trigger and state
	 * 
	 * @param trigger trigger
	 * @param state state
	 */
	public AbstractTaskTrigger(String taskId, Trigger trigger, TaskTriggerState state) {
		this(taskId, trigger.getKey().getName());
		//
		this.description = trigger.getDescription();
		if (trigger.getNextFireTime() != null) {
			this.nextFireTime = trigger.getNextFireTime().toInstant().atZone(ZoneId.systemDefault());
		}
		if (trigger.getPreviousFireTime() != null) {
			this.previousFireTime = trigger.getPreviousFireTime().toInstant().atZone(ZoneId.systemDefault());
		}
		this.state = state;
	}
	
	/**
	 * Creates a new instance using trigger and state
	 * 
	 * @param trigger trigger
	 * @param state state
	 * @since 10.2.0
	 */
	public AbstractTaskTrigger(String taskId, Trigger trigger, TaskTriggerState state, ZonedDateTime executeDate) {
		this(taskId, trigger, state);
		//
		this.executeDate = executeDate;
	}
	
	@Override
	public String getId() {
		return id;
	}
	
	@Override
	public void setId(Serializable id) {
		this.id = id == null ? null : id.toString();
	}
	
	public String getTaskId() {
		return taskId;
	}
	
	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public ZonedDateTime getNextFireTime() {
		return nextFireTime;
	}
	
	public void setNextFireTime(ZonedDateTime nextFireTime) {
		this.nextFireTime = nextFireTime;
	}
	
	public ZonedDateTime getPreviousFireTime() {
		return previousFireTime;
	}
	
	public void setPreviousFireTime(ZonedDateTime previousFireTime) {
		this.previousFireTime = previousFireTime;
	}
	
	public TaskTriggerState getState() {
		return state;
	}
	
	public void setState(TaskTriggerState state) {
		this.state = state;
	}
	
	/**
	 * Delayed first execute date.
	 * 
	 * @return delayed first execute date.
	 * @since 10.2.0
	 */
	public ZonedDateTime getExecuteDate() {
		return executeDate;
	}

	/**
	 * Delayed first execute date.
	 * 
	 * @param executeDate
	 * @since 10.2.0
	 */
	public void setExecuteDate(ZonedDateTime executeDate) {
		this.executeDate = executeDate;
	}
}