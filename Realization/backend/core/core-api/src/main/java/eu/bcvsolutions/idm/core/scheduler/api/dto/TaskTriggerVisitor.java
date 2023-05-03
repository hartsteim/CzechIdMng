package eu.bcvsolutions.idm.core.scheduler.api.dto;

/**
 * @author Jan Potočiar
 */
public interface TaskTriggerVisitor {
	public void visit(CronTaskTrigger trigger);
	public void visit(DependentTaskTrigger trigger);
	public void visit(SimpleTaskTrigger trigger);
}
