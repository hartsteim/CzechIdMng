package eu.bcvsolutions.idm.core.model.event.processor.tree;

import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;

import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.RecursionType;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmEntityStateDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.OperationResultDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmAutomaticRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractPositionFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractSliceFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityContractFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.EntityStateManager;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmContractPositionService;
import eu.bcvsolutions.idm.core.api.service.IdmContractSliceService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.api.utils.AutowireHelper;
import eu.bcvsolutions.idm.core.exception.TreeNodeException;
import eu.bcvsolutions.idm.core.model.event.ContractPositionEvent;
import eu.bcvsolutions.idm.core.model.event.ContractPositionEvent.ContractPositionEventType;
import eu.bcvsolutions.idm.core.model.event.IdentityContractEvent;
import eu.bcvsolutions.idm.core.model.event.IdentityContractEvent.IdentityContractEventType;
import eu.bcvsolutions.idm.core.model.event.TreeNodeEvent;
import eu.bcvsolutions.idm.core.model.event.TreeNodeEvent.TreeNodeEventType;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.core.scheduler.task.impl.RemoveAutomaticRoleTaskExecutor;

/**
 * Deletes tree node - ensures referential integrity.
 * 
 * @author Radek Tomiška
 *
 */
@Component
@Description("Deletes tree node - ensures referential integrity")
public class TreeNodeDeleteProcessor extends CoreEventProcessor<IdmTreeNodeDto> {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TreeNodeDeleteProcessor.class);
	public static final String PROCESSOR_NAME = "tree-node-delete-processor";
	//
	@Autowired private IdmTreeNodeService service;
	@Autowired private IdmRoleTreeNodeService roleTreeNodeService;
	@Autowired private IdmIdentityContractService identityContractService;
	@Autowired private IdmContractSliceService contractSliceService;
	@Autowired private IdmContractPositionService contractPositionService;
	@Autowired private IdmAutomaticRoleRequestService roleRequestService;
	@Autowired private EntityStateManager entityStateManager;
	@Qualifier("entityManager")
	@Autowired private EntityManager entityManager;
	@Autowired private LongRunningTaskManager longRunningTaskManager;
	
	public TreeNodeDeleteProcessor() {
		super(TreeNodeEventType.DELETE);
	}
	
	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}
	
	@Override
	public EventResult<IdmTreeNodeDto> process(EntityEvent<IdmTreeNodeDto> event) {
		IdmTreeNodeDto treeNode = event.getContent();
		Assert.notNull(treeNode, "Tree node is required.");
		UUID treeNodeId = treeNode.getId();
		Assert.notNull(treeNodeId, "Tree node identifier is required.");
		boolean forceDelete = getBooleanProperty(PROPERTY_FORCE_DELETE, event.getProperties());
		//
		// check role can be removed without force
		if (!forceDelete) {
			checkWithoutForceDelete(treeNode);
		}
		//
		// check automatic role request
		IdmAutomaticRoleRequestFilter roleRequestFilter = new IdmAutomaticRoleRequestFilter();
		roleRequestFilter.setTreeNodeId(treeNodeId);
		List<IdmAutomaticRoleRequestDto> roleRequestDtos = roleRequestService.find(roleRequestFilter, null).getContent();
		for (IdmAutomaticRoleRequestDto request : roleRequestDtos) {
			if (!request.getState().isTerminatedState()) {
				roleRequestService.cancel(request);
			}
			request.setTreeNode(null);
			roleRequestService.save(request);
		}
		if (forceDelete) {
			// delete all tree node children
			service.findChildrenByParent(treeNodeId, null).forEach(child -> {
				TreeNodeEvent treeNodeEvent = new TreeNodeEvent(
						TreeNodeEventType.DELETE, 
						child
				);
				//
				service.publish(treeNodeEvent, event);
				clearSession();
			});
			//
			// update contract slices
			IdmContractSliceFilter sliceFilter = new IdmContractSliceFilter();
			sliceFilter.setTreeNode(treeNodeId);
			sliceFilter.setRecursionType(RecursionType.NO);
			contractSliceService.find(sliceFilter, null).forEach(slice -> {
				slice.setWorkPosition(null);
				contractSliceService.save(slice);
				clearSession();
			});
			//
			// update related contracts
			IdmIdentityContractFilter contractFilter = new IdmIdentityContractFilter();
			contractFilter.setWorkPosition(treeNodeId);
			contractFilter.setRecursionType(RecursionType.NO);
			identityContractService.find(contractFilter, null).forEach(identityContract -> {
				// prepare event
				identityContract.setWorkPosition(null);
				IdentityContractEvent contractEvent = new IdentityContractEvent(
						IdentityContractEventType.UPDATE, 
						identityContract
				);
				//
				identityContractService.publish(contractEvent, event);
				clearSession();
			});
			//
			// update related contract positions
			IdmContractPositionFilter positionFilter = new IdmContractPositionFilter();
			positionFilter.setWorkPosition(treeNodeId);
			contractPositionService.find(positionFilter, null).forEach(contractPosition -> {
				// prepare event
				contractPosition.setWorkPosition(null);
				ContractPositionEvent contractPositionEvent = new ContractPositionEvent(
						ContractPositionEventType.UPDATE, 
						contractPosition
				);
				//
				contractPositionService.publish(contractPositionEvent, event);
				clearSession();
			});
			//
			// related automatic roles by tree structure
			IdmRoleTreeNodeFilter roleTreeNodefilter = new IdmRoleTreeNodeFilter();
			roleTreeNodefilter.setTreeNodeId(treeNodeId);
			roleTreeNodeService
				.findIds(roleTreeNodefilter, null)
				.stream()
				.forEach(roleTreeNodeId -> {
					// sync => all asynchronous requests have to be prepared in event queue
					RemoveAutomaticRoleTaskExecutor automaticRoleTask = AutowireHelper.createBean(RemoveAutomaticRoleTaskExecutor.class);
					automaticRoleTask.setAutomaticRoleId(roleTreeNodeId);
					longRunningTaskManager.executeSync(automaticRoleTask);
					clearSession();
				});
		}
		//		
		if (forceDelete) {
			LOG.debug("Tree node [{}] should be deleted by caller after all asynchronus processes are completed.", treeNode.getCode());
			//
			// dirty flag only - will be processed after asynchronous events ends
			IdmEntityStateDto stateDeleted = new IdmEntityStateDto();
			stateDeleted.setEvent(event.getId());
			stateDeleted.setResult(
					new OperationResultDto.Builder(OperationState.RUNNING)
						.setModel(new DefaultResultModel(CoreResultCode.DELETED))
						.build()
			);
			entityStateManager.saveState(treeNode, stateDeleted);
			//
			// set disabled
			treeNode.setDisabled(true);
			service.saveInternal(treeNode);
		} else {
			service.deleteInternal(treeNode);
		}
		//
		return new DefaultEventResult<>(event, this);
	}
	
	/**
	 * Check tree node can be deleted without force delete.
	 * 
	 * @param treeNode deleted tree node
	 * @throws ResultCodeException if not
	 */
	private void checkWithoutForceDelete(IdmTreeNodeDto treeNode) {
		UUID treeNodeId = treeNode.getId();
		//
		if (service.findChildrenByParent(treeNodeId, PageRequest.of(0, 1)).getTotalElements() > 0) {
			throw new TreeNodeException(CoreResultCode.TREE_NODE_DELETE_FAILED_HAS_CHILDREN,  ImmutableMap.of("treeNode", treeNode.getCode()));
		} 
		//
		IdmIdentityContractFilter contractFilter = new IdmIdentityContractFilter();
		contractFilter.setWorkPosition(treeNodeId);
		contractFilter.setRecursionType(RecursionType.NO);
		if (identityContractService.count(contractFilter) > 0) {
			throw new TreeNodeException(CoreResultCode.TREE_NODE_DELETE_FAILED_HAS_CONTRACTS, ImmutableMap.of("treeNode", treeNode.getCode()));
		}
		//
		IdmContractSliceFilter sliceFilter = new IdmContractSliceFilter();
		sliceFilter.setTreeNode(treeNodeId);
		if (contractSliceService.find(sliceFilter, null).getTotalElements() > 0) {
			throw new TreeNodeException(CoreResultCode.TREE_NODE_DELETE_FAILED_HAS_CONTRACT_SLICES, ImmutableMap.of("treeNode", treeNode.getCode()));
		}
		//
		IdmContractPositionFilter positionFilter = new IdmContractPositionFilter();
		positionFilter.setWorkPosition(treeNodeId);
		if (contractPositionService.find(positionFilter, PageRequest.of(0, 1)).getTotalElements() > 0) {
			throw new TreeNodeException(CoreResultCode.TREE_NODE_DELETE_FAILED_HAS_CONTRACT_POSITIONS, ImmutableMap.of("treeNode", treeNode.getCode()));
		}
		//
		// check related automatic roles
		IdmRoleTreeNodeFilter filter = new IdmRoleTreeNodeFilter();
		filter.setTreeNodeId(treeNodeId);
		if (roleTreeNodeService.find(filter, null).getTotalElements() > 0) {
			throw new TreeNodeException(CoreResultCode.TREE_NODE_DELETE_FAILED_HAS_ROLE, 
					ImmutableMap.of("treeNode", treeNode.getCode()));
		}
	}

	private void clearSession() {
		// flush and clear session - manager can have a lot of subordinates
		if (getHibernateSession().isOpen()) {
			getHibernateSession().flush();
			getHibernateSession().clear();
		}
	}
	
	private Session getHibernateSession() {
		return (Session) this.entityManager.getDelegate();
	}
}
