package eu.bcvsolutions.idm.core.scheduler.entity;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.ConstraintMode;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.hibernate.envers.Audited;

import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;
import eu.bcvsolutions.idm.core.api.domain.InstanceIdentifiable;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.ecm.api.entity.AttachableEntity;

/**
 * Persisted instance of one task execution.
 *
 * @author Radek Tomiška
 *
 */
@Entity
@Table(name = "idm_long_running_task", indexes = {
		@Index(name = "idx_idm_long_r_t_inst", columnList = "instance_id"),
		@Index(name = "idx_idm_long_r_t_type", columnList = "task_type"),
		@Index(name = "idx_idm_long_r_t_s_task", columnList = "scheduled_task_id")
		})
public class IdmLongRunningTask extends AbstractEntity implements AttachableEntity, InstanceIdentifiable {

	private static final long serialVersionUID = -4665452018920201474L;

	@Audited
	@NotEmpty
	@Column(name = "task_type", length = DefaultFieldLengths.NAME, nullable = false)
	private String taskType;

	@Audited
	@Column(name = "task_description", length = DefaultFieldLengths.DESCRIPTION)
	private String taskDescription;

	@Audited
	@Column(name = "task_properties", length = Integer.MAX_VALUE)
	private Serializable taskProperties;

	@Column(name = "task_count")
	private Long count;

	@Column(name = "task_counter")
	private Long counter;

	@Audited
	@Column(name = "running", nullable = false)
	private boolean running = false;

	@NotNull
	@Audited
	@Column(name = "instance_id", length = DefaultFieldLengths.NAME, nullable = false)
	private String instanceId;

	@Audited
	@Column(name = "thread_id", nullable = false)
	private long threadId;

	@Audited
	@Column(name = "thread_name", length = DefaultFieldLengths.NAME)
	private String threadName;

	@Audited
	@Column(name = "task_started")
	private ZonedDateTime taskStarted;
	
	@Audited
	@Column(name = "task_ended")
	private ZonedDateTime taskEnded;

	// @Audited
	@NotNull
	@Embedded
	private OperationResult result;

	@Audited
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "scheduled_task_id",
		referencedColumnName = "id",
		foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
	private IdmScheduledTask scheduledTask;

	@Audited
	@Column(name = "stateful", nullable = false)
	private boolean stateful;
	
	@Audited
	@Column(name = "recoverable", nullable = false)
	private boolean recoverable;

	@Audited
	@Column(name = "dry_run", nullable = false)
	private boolean dryRun;

	public void setTaskStarted(ZonedDateTime date){
		taskStarted = date;
	}

	public ZonedDateTime getTaskStarted(){
		return taskStarted;
	}

	public String getTaskType() {
		return taskType;
	}

	public void setTaskType(String taskType) {
		this.taskType = taskType;
	}

	public Long getCount() {
		return count;
	}

	public void setCount(Long count) {
		this.count = count;
	}

	public Long getCounter() {
		return counter;
	}

	public void setCounter(Long counter) {
		this.counter = counter;
	}

	public OperationState getResultState() {
		if (result == null) {
			return null;
		}
		return result.getState();
	}

	public OperationResult getResult() {
		return result;
	}

	public void setResult(OperationResult result) {
		this.result = result;
	}

	public String getTaskDescription() {
		return taskDescription;
	}

	public void setTaskDescription(String taskDescription) {
		this.taskDescription = taskDescription;
	}

	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		this.running = running;
	}

	public long getThreadId() {
		return threadId;
	}

	public void setThreadId(long threadId) {
		this.threadId = threadId;
	}

	@Override
	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}

	@Override
	public String getInstanceId() {
		return instanceId;
	}

	public String getThreadName() {
		return threadName;
	}

	public void setThreadName(String threadName) {
		this.threadName = threadName;
	}

	public void setTaskProperties(Map<String, Object> taskProperties) {
		this.taskProperties = (Serializable) taskProperties;
	}

	// workaround
	// we need to change data type on getter/setter for modelmapper to work
	@SuppressWarnings("unchecked")
	public Map<String, Object> getTaskProperties() {
		if (taskProperties == null) {
			taskProperties = new HashMap<>();
		}
		return (Map<String, Object>) taskProperties;
	}

	public IdmScheduledTask getScheduledTask() {
		return scheduledTask;
	}

	public void setScheduledTask(IdmScheduledTask scheduledTask) {
		this.scheduledTask = scheduledTask;
	}

	public boolean isStateful() {
		return stateful;
	}

	public void setStateful(boolean stateful) {
		this.stateful = stateful;
	}

	public boolean isDryRun() {
		return dryRun;
	}

	public void setDryRun(boolean dryRun) {
		this.dryRun = dryRun;
	}

	/**
	 * Task can be executed repetitively without reschedule is needed.
	 * When task is canceled (e.g. by server is restarted), then task can be executed again.
	 * 
	 * @return true - LRT can be executed again.
	 * @since 10.2.0
	 */
	public boolean isRecoverable() {
		return recoverable;
	}
	
	/**
	 * Task can be executed repetitively without reschedule is needed.
	 * When task is canceled (e.g. by server is restarted), then task can be executed again.
	 * 
	 * @param recoverable  true - LRT can be executed again.
	 * @since 10.2.0
	 */
	public void setRecoverable(boolean recoverable) {
		this.recoverable = recoverable;
	}
	
	/**
	 * Task ended datetime.
	 * 
	 * @return task end datetime
	 * @since 10.6.0
	 */
	public ZonedDateTime getTaskEnded() {
		return taskEnded;
	}
	
	/**
	 * Task ended datetime.
	 * 
	 * @param taskEnded task end datetime
	 * @since 10.6.0
	 */
	public void setTaskEnded(ZonedDateTime taskEnded) {
		this.taskEnded = taskEnded;
	}
}
