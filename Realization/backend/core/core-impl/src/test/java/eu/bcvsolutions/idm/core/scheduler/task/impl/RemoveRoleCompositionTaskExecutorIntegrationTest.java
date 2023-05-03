package eu.bcvsolutions.idm.core.scheduler.task.impl;

import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.config.domain.EventConfiguration;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.IdmEntityEventDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCompositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmEntityEventFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCompositionService;
import eu.bcvsolutions.idm.core.api.utils.AutowireHelper;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.event.LongRunningTaskEvent.LongRunningTaskEventType;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Business role removal tests.
 * 
 * @author Radek Tomiška
 *
 */
public class RemoveRoleCompositionTaskExecutorIntegrationTest extends AbstractIntegrationTest {
	
	@Autowired private LongRunningTaskManager longRunningTaskManager;
	@Autowired private EntityEventManager entityEventManager;
	@Autowired private IdmIdentityRoleService identityRoleService;
	@Autowired private IdmRoleCompositionService roleCompositionService;
	//
	@Mock private IdmIdentityRoleService mockIdentityRoleService;

	@Test
	public void testRemoveAssignedRoles() {
		// prepare role composition
		IdmRoleDto superior = getHelper().createRole();
		IdmRoleDto subOne = getHelper().createRole();
		IdmRoleDto subOneSub = getHelper().createRole();
		getHelper().createRoleComposition(superior, subOne);
		IdmRoleCompositionDto subOneSubRoleComposition = getHelper().createRoleComposition(subOne, subOneSub);
		//
		// assign superior role
		IdmIdentityDto identity = getHelper().createIdentity();
		getHelper().createIdentityRole(identity, superior);
		//
		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(3, assignedRoles.size());
		Assert.assertTrue(assignedRoles.stream().anyMatch(ir -> ir.getRole().equals(superior.getId())));
		Assert.assertTrue(assignedRoles.stream().anyMatch(ir -> ir.getRole().equals(subOne.getId())));
		Assert.assertTrue(assignedRoles.stream().anyMatch(ir -> ir.getRole().equals(subOneSub.getId())));
		//
		// remove role composition by task
		RemoveRoleCompositionTaskExecutor taskExecutor = new RemoveRoleCompositionTaskExecutor();
		taskExecutor.setRoleCompositionId(subOneSubRoleComposition.getId());
		longRunningTaskManager.executeSync(taskExecutor);
		//
		assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(2, assignedRoles.size());
		Assert.assertTrue(assignedRoles.stream().anyMatch(ir -> ir.getRole().equals(superior.getId())));
		Assert.assertTrue(assignedRoles.stream().anyMatch(ir -> ir.getRole().equals(subOne.getId())));
	}
	
	
	@Test
	public void testRemoveAssignedRolesWithExceptionOnEnd() {
		// prepare role composition
		IdmRoleDto superior = getHelper().createRole();
		IdmRoleDto subOne = getHelper().createRole();
		IdmRoleDto subOneSub = getHelper().createRole();
		getHelper().createRoleComposition(superior, subOne);
		IdmRoleCompositionDto subOneSubRoleComposition = getHelper().createRoleComposition(subOne, subOneSub);
		//
		// assign superior role
		IdmIdentityDto identity = getHelper().createIdentity();
		getHelper().createIdentityRole(identity, superior);
		//
		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(3, assignedRoles.size());
		Assert.assertTrue(assignedRoles.stream().anyMatch(ir -> ir.getRole().equals(superior.getId())));
		Assert.assertTrue(assignedRoles.stream().anyMatch(ir -> ir.getRole().equals(subOne.getId())));
		Assert.assertTrue(assignedRoles.stream().anyMatch(ir -> ir.getRole().equals(subOneSub.getId())));
		//
		// remove role composition by task with mock service => we want to throw exception
		Mockito.when(mockIdentityRoleService.count(ArgumentMatchers.any(IdmIdentityRoleFilter.class)))
			.thenReturn(1L);
		Mockito.when(mockIdentityRoleService.find(ArgumentMatchers.any(IdmIdentityRoleFilter.class), ArgumentMatchers.isNull()))
			.then(new AssignedRolesAnswer(subOneSubRoleComposition.getId()));
		RemoveRoleCompositionTaskExecutor taskExecutor = new RemoveRoleCompositionTaskExecutor();
		AutowireHelper.autowire(taskExecutor);
		IdmLongRunningTaskDto lrt = longRunningTaskManager.resolveLongRunningTask(taskExecutor, null, OperationState.RUNNING);
		UUID taskId = lrt.getId();
		taskExecutor.setIdentityRoleService(mockIdentityRoleService);
		taskExecutor.setRoleCompositionId(subOneSubRoleComposition.getId());
		//
		try {
			getHelper().setConfigurationValue(EventConfiguration.PROPERTY_EVENT_ASYNCHRONOUS_ENABLED, true);
			taskExecutor.call();
			
			getHelper().waitForResult(res -> {
				return longRunningTaskManager.getLongRunningTask(taskId).getResultState().isRunnable();
			});
		} finally {
			getHelper().setConfigurationValue(EventConfiguration.PROPERTY_EVENT_ASYNCHRONOUS_ENABLED, false);
		}
		//
		// long running task has a proper state with exception
		lrt = longRunningTaskManager.getLongRunningTask(lrt.getId());
		Assert.assertEquals(OperationState.EXCEPTION, lrt.getResultState());
		Assert.assertEquals(CoreResultCode.ROLE_COMPOSITION_REMOVE_HAS_ASSIGNED_ROLES.name(), lrt.getResult().getCode());
		//
		// start event is ended
		IdmEntityEventFilter eventFilter = new IdmEntityEventFilter();
		eventFilter.setTransactionId(lrt.getTransactionId());
		eventFilter.setEventType(LongRunningTaskEventType.START.name());
		List<IdmEntityEventDto> startEvents = entityEventManager.findEvents(eventFilter, null).getContent();
		Assert.assertEquals(1, startEvents.size());
		Assert.assertEquals(OperationState.EXECUTED, startEvents.get(0).getResult().getState());
		//
		// business role still exists
		Assert.assertNotNull(roleCompositionService.get(subOneSubRoleComposition));
		//
		// but assigned roles should be removed
		assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(2, assignedRoles.size());
		Assert.assertTrue(assignedRoles.stream().anyMatch(ir -> ir.getRole().equals(superior.getId())));
		Assert.assertTrue(assignedRoles.stream().anyMatch(ir -> ir.getRole().equals(subOne.getId())));
	}

	@Test
	public void testRemovingUnassignedRoleComposition() {
		IdmRoleDto superior = getHelper().createRole();
		IdmRoleDto subOne = getHelper().createRole();
		IdmRoleCompositionDto roleComposition = getHelper().createRoleComposition(superior, subOne);
		//
		roleCompositionService.delete(roleComposition);
		//
		IdmRoleCompositionDto deletedRoleComposition = roleCompositionService.get(roleComposition.getId());
		Assert.assertNull(deletedRoleComposition);
	}
	
	private class AssignedRolesAnswer implements Answer<Page<IdmIdentityRoleDto>> {
		
		private final UUID roleCompostionId;
		
		public AssignedRolesAnswer(UUID roleCompostionId) {
			this.roleCompostionId = roleCompostionId;
		}
		
		@Override
		public Page<IdmIdentityRoleDto> answer(InvocationOnMock invocation) throws Throwable {
			IdmIdentityRoleFilter filter = (IdmIdentityRoleFilter) invocation.getArguments()[0];
			PageRequest pageRequest = (PageRequest) invocation.getArguments()[1];
			if (pageRequest != null 
					&& pageRequest.getPageSize() == 1 
					&& filter.getRoleId() == null
					&& roleCompostionId.equals(filter.getRoleCompositionId())) {
				// end phase => mock role is returned to throw exception
				return new PageImpl<>(Lists.newArrayList(new IdmIdentityRoleDto(UUID.randomUUID())));
			}
			// otherwise original
			return identityRoleService.find(filter, pageRequest);
		}
	}
}
