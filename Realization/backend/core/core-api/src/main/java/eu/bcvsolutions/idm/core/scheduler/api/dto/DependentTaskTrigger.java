package eu.bcvsolutions.idm.core.scheduler.api.dto;

import java.util.UUID;

import javax.validation.constraints.NotNull;

/**
 * Trigger by other scheduled task.
 * 
 * @author Radek Tomiška
 *
 */
public class DependentTaskTrigger extends AbstractTaskTrigger {
	
	private static final long serialVersionUID = 1L;

	@NotNull
	private String initiatorTaskId;
	
	public DependentTaskTrigger() {
	}
	
	public DependentTaskTrigger(String taskId, UUID triggerId, String initiatorTaskId) {
		super(taskId, triggerId.toString());
		//
		this.initiatorTaskId = initiatorTaskId;
	}
	
	public String getInitiatorTaskId() {
		return initiatorTaskId;
	}
	
	public void setInitiatorTaskId(String initiatorTaskId) {
		this.initiatorTaskId = initiatorTaskId;
	}

	@Override
	public void accept(TaskTriggerVisitor visitor) {
		visitor.visit(this);
	}
}
