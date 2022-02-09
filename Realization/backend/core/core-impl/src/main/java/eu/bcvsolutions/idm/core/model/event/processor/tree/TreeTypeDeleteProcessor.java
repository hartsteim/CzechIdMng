package eu.bcvsolutions.idm.core.model.event.processor.tree;

import java.util.UUID;

import javax.persistence.EntityManager;

import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmEntityStateDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeTypeDto;
import eu.bcvsolutions.idm.core.api.dto.OperationResultDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.service.EntityStateManager;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeTypeService;
import eu.bcvsolutions.idm.core.exception.TreeTypeException;
import eu.bcvsolutions.idm.core.model.event.TreeNodeEvent;
import eu.bcvsolutions.idm.core.model.event.TreeNodeEvent.TreeNodeEventType;
import eu.bcvsolutions.idm.core.model.event.TreeTypeEvent.TreeTypeEventType;
import eu.bcvsolutions.idm.core.model.repository.IdmIdentityContractRepository;

/**
 * Deletes tree type - ensures referential integrity.
 * 
 * @author Radek Tomiška
 * @since 12.0.0
 */
@Component(TreeTypeDeleteProcessor.PROCESSOR_NAME)
@Description("Deletes tree type")
public class TreeTypeDeleteProcessor extends CoreEventProcessor<IdmTreeTypeDto> {

	public static final String PROCESSOR_NAME = "core-tree-type-delete-processor";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TreeTypeDeleteProcessor.class);
	//
	@Autowired private EntityStateManager entityStateManager;
	@Autowired private IdmTreeTypeService service;
	@Autowired private IdmTreeNodeService treeNodeService;
	@Autowired private IdmIdentityContractRepository identityContractRepository;
	@Qualifier("entityManager")
	@Autowired private EntityManager entityManager;
	
	public TreeTypeDeleteProcessor() {
		super(TreeTypeEventType.DELETE);
	}
	
	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}
	
	@Override
	public EventResult<IdmTreeTypeDto> process(EntityEvent<IdmTreeTypeDto> event) {
		IdmTreeTypeDto treeType = event.getContent();
		UUID treeTypeId = treeType.getId();
		Assert.notNull(treeTypeId, "Tree type identifier is required.");
		boolean forceDelete = getBooleanProperty(PROPERTY_FORCE_DELETE, event.getProperties());
		//
		if (!forceDelete) {
			if (identityContractRepository.countByWorkPosition_TreeType_Id(treeTypeId) > 0) {
				throw new TreeTypeException(CoreResultCode.TREE_TYPE_DELETE_FAILED_HAS_CONTRACTS,  ImmutableMap.of("treeType", treeType.getCode()));
			}
			IdmTreeNodeFilter filter = new IdmTreeNodeFilter();
			filter.setTreeTypeId(treeTypeId);
			if (treeNodeService.count(filter) > 0) {
				throw new TreeTypeException(CoreResultCode.TREE_TYPE_DELETE_FAILED_HAS_CHILDREN,  ImmutableMap.of("treeType", treeType.getCode()));
			}
		} else {
			// delete tree nodes
			IdmTreeNodeFilter filter = new IdmTreeNodeFilter();
			filter.setTreeTypeId(treeTypeId);
			treeNodeService.find(filter, null).forEach(treeNode -> {
				TreeNodeEvent treeNodeEvent = new TreeNodeEvent(
						TreeNodeEventType.DELETE, 
						treeNode
				);
				//
				treeNodeService.publish(treeNodeEvent, event);
				clearSession();
			});
		}
		//
		// deletes tree type at end
		if (forceDelete) {
			LOG.debug("Tree type [{}] should be deleted by caller after all asynchronous processes are completed.", treeTypeId);
			//
			// dirty flag only - will be processed after asynchronous events ends
			IdmEntityStateDto stateDeleted = new IdmEntityStateDto();
			stateDeleted.setEvent(event.getId());
			stateDeleted.setResult(
					new OperationResultDto.Builder(OperationState.RUNNING)
						.setModel(new DefaultResultModel(CoreResultCode.DELETED))
						.build()
			);
			entityStateManager.saveState(treeType, stateDeleted);
		} else {
			service.deleteInternal(treeType);
		}
		//
		return new DefaultEventResult<>(event, this);
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
