package eu.bcvsolutions.idm.core.scheduler.api.dto;

/**
 * @author Jan Potočiar
 */
public interface TaskTriggerVisitee {
	public void accept(TaskTriggerVisitor visitor);
}
