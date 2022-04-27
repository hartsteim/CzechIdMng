package eu.bcvsolutions.idm.core.model.event.processor.role;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.PriorityType;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCompositionDto;
import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.event.processor.RoleCompositionProcessor;
import eu.bcvsolutions.idm.core.api.utils.AutowireHelper;
import eu.bcvsolutions.idm.core.model.event.RoleCompositionEvent.RoleCompositionEventType;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.filter.IdmLongRunningTaskFilter;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.core.scheduler.task.impl.RemoveRoleCompositionTaskExecutor;

/**
 * Deletes role composition - ensures referential integrity.
 * 
 * @author Radek Tomiška
 * @since 9.0.0
 */
@Component(RoleCompositionDeleteProcessor.PROCESSOR_NAME)
@Description("Deletes role composition from repository.")
public class RoleCompositionDeleteProcessor
		extends CoreEventProcessor<IdmRoleCompositionDto>
		implements RoleCompositionProcessor {
	
	public static final String PROCESSOR_NAME = "core-role-composition-delete-processor";
	//
	@Autowired private LongRunningTaskManager longRunningTaskManager;
	
	public RoleCompositionDeleteProcessor() {
		super(RoleCompositionEventType.DELETE);
	}
	
	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	@Override
	public boolean conditional(EntityEvent<IdmRoleCompositionDto> event) {
		IdmLongRunningTaskFilter filter = new IdmLongRunningTaskFilter();
		filter.setOperationState(OperationState.RUNNING);
		filter.setTaskType(RemoveRoleCompositionTaskExecutor.class.getCanonicalName());
		List<IdmLongRunningTaskDto> tasks = longRunningTaskManager.findLongRunningTasks(filter, null).getContent();
		for (IdmLongRunningTaskDto task : tasks) {
			if (task.getTaskProperties() != null && task.getTaskProperties().get(RemoveRoleCompositionTaskExecutor.PARAMETER_ROLE_COMPOSITION_ID) != null
					&& task.getTaskProperties().get(RemoveRoleCompositionTaskExecutor.PARAMETER_ROLE_COMPOSITION_ID).equals(event.getContent().getId())) {
				return false;
			}
		}
		
		return true;
	}

	@Override
	public EventResult<IdmRoleCompositionDto> process(EntityEvent<IdmRoleCompositionDto> event) {
		IdmRoleCompositionDto roleComposition = event.getContent();
		//
		if (roleComposition.getId() == null) {
			return new DefaultEventResult<>(event, this);
		}
		//
		// delete all assigned roles gained by this automatic role by long running task
		RemoveRoleCompositionTaskExecutor roleCompositionTask = AutowireHelper.createBean(RemoveRoleCompositionTaskExecutor.class);
		roleCompositionTask.setRoleCompositionId(roleComposition.getId());
		if (event.getPriority() == PriorityType.IMMEDIATE) {
			longRunningTaskManager.executeSync(roleCompositionTask);
			return new DefaultEventResult<>(event, this);
		}
		//
		roleCompositionTask.setRequireNewTransaction(true);
		// always execute as sync, so prevent warning status if multiple compositions should be deleted. It occurs if you remove
		// more or same number of compositions as number in property scheduler.task.executor.corePoolSize
		longRunningTaskManager.executeSync(roleCompositionTask);
		// TODO: new flag asynchronous?
		return new DefaultEventResult.Builder<>(event, this).setSuspended(true).build();
	}
}