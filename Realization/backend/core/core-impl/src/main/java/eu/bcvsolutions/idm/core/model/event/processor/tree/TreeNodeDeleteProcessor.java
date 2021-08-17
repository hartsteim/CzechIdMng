package eu.bcvsolutions.idm.core.model.event.processor.tree;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmAutomaticRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractPositionFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractSliceFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmContractPositionService;
import eu.bcvsolutions.idm.core.api.service.IdmContractSliceService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.exception.TreeNodeException;
import eu.bcvsolutions.idm.core.model.event.TreeNodeEvent.TreeNodeEventType;
import eu.bcvsolutions.idm.core.model.repository.IdmIdentityContractRepository;

/**
 * Deletes tree node - ensures referential integrity.
 * 
 * @author Radek Tomiška
 *
 */
@Component
@Description("Deletes tree node - ensures referential integrity")
public class TreeNodeDeleteProcessor extends CoreEventProcessor<IdmTreeNodeDto> {

	public static final String PROCESSOR_NAME = "tree-node-delete-processor";
	private final IdmTreeNodeService service;
	private final IdmRoleTreeNodeService roleTreeNodeService;
	private final IdmIdentityContractRepository identityContractRepository;
	//
	@Autowired private IdmContractSliceService contractSliceService;
	@Autowired private IdmContractPositionService contractPositionService;
	@Autowired private IdmAutomaticRoleRequestService roleRequestService;
	
	@Autowired
	public TreeNodeDeleteProcessor(
			IdmTreeNodeService service,
			IdmIdentityContractRepository identityContractRepository,
			IdmRoleTreeNodeService roleTreeNodeService) {
		super(TreeNodeEventType.DELETE);
		//
		Assert.notNull(service, "Service is required.");
		Assert.notNull(identityContractRepository, "Repository is required.");
		Assert.notNull(roleTreeNodeService, "Service is required.");
		//
		this.service = service;
		this.roleTreeNodeService = roleTreeNodeService;	
		this.identityContractRepository = identityContractRepository;
	}
	
	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}
	
	@Override
	public EventResult<IdmTreeNodeDto> process(EntityEvent<IdmTreeNodeDto> event) {
		IdmTreeNodeDto treeNode = event.getContent();
		Assert.notNull(treeNode, "Tree node is required.");
		Assert.notNull(treeNode.getId(), "Tree node identifier is required.");
		//
		if (identityContractRepository.countByWorkPosition_Id(treeNode.getId()) > 0) {
			throw new TreeNodeException(CoreResultCode.TREE_NODE_DELETE_FAILED_HAS_CONTRACTS, ImmutableMap.of("treeNode", treeNode.getName()));
		}
		//
		IdmContractSliceFilter sliceFilter = new IdmContractSliceFilter();
		sliceFilter.setTreeNode(treeNode.getId());
		if(contractSliceService.find(sliceFilter, null).getTotalElements() > 0) {
			throw new TreeNodeException(CoreResultCode.TREE_NODE_DELETE_FAILED_HAS_CONTRACT_SLICES, ImmutableMap.of("treeNode", treeNode.getName()));
		}
		//
		IdmContractPositionFilter positionFilter = new IdmContractPositionFilter();
		positionFilter.setWorkPosition(treeNode.getId());
		if (contractPositionService.find(positionFilter, PageRequest.of(0, 1)).getTotalElements() > 0) {
			throw new TreeNodeException(CoreResultCode.TREE_NODE_DELETE_FAILED_HAS_CONTRACT_POSITIONS, ImmutableMap.of("treeNode", treeNode.getName()));
		}
		//
		// check related automatic roles
		IdmRoleTreeNodeFilter filter = new IdmRoleTreeNodeFilter();
		filter.setTreeNodeId(treeNode.getId());
		if (roleTreeNodeService.find(filter, null).getTotalElements() > 0) {
			throw new TreeNodeException(CoreResultCode.TREE_NODE_DELETE_FAILED_HAS_ROLE, 
					ImmutableMap.of("treeNode", treeNode.getName()));
		}
		// check automatic role request
		IdmAutomaticRoleRequestFilter roleRequestFilter = new IdmAutomaticRoleRequestFilter();
		roleRequestFilter.setTreeNodeId(treeNode.getId());
		List<IdmAutomaticRoleRequestDto> roleRequestDtos = roleRequestService.find(roleRequestFilter, null).getContent();
		for (IdmAutomaticRoleRequestDto request : roleRequestDtos) {
			if (!request.getState().isTerminatedState()) {
				roleRequestService.cancel(request);
			}
			request.setTreeNode(null);
			roleRequestService.save(request);
		}
		//		
		service.deleteInternal(treeNode);
		//
		return new DefaultEventResult<>(event, this);
	}
}
