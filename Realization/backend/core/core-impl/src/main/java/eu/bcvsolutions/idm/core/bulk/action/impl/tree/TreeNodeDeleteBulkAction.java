package eu.bcvsolutions.idm.core.bulk.action.impl.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;

import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.bulk.action.AbstractRemoveBulkAction;
import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.PriorityType;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.event.EntityEventProcessor;
import eu.bcvsolutions.idm.core.api.event.EventContext;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.EntityStateManager;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.ReadWriteDtoService;
import eu.bcvsolutions.idm.core.api.utils.ExceptionUtils;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.event.TreeNodeEvent;
import eu.bcvsolutions.idm.core.model.event.TreeNodeEvent.TreeNodeEventType;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;

/**
 * Delete given tree nodes.
 *
 * @author Radek Tomiška
 * @since 12.0.0
 */
@Component(TreeNodeDeleteBulkAction.NAME)
@Description("Delete given tree nodes.")
public class TreeNodeDeleteBulkAction extends AbstractRemoveBulkAction<IdmTreeNodeDto, IdmTreeNodeFilter> {

	public static final String NAME = "core-tree-node-delete-bulk-action";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TreeNodeDeleteBulkAction.class);
	//
	@Autowired private IdmTreeNodeService treeNodeService;
	@Autowired private SecurityService securityService;
	@Autowired private EntityStateManager entityStateManager;
	@Qualifier("entityManager")
	@Autowired private EntityManager entityManager;
	//
	private final List<UUID> processedIds = new ArrayList<UUID>();

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	protected List<String> getAuthoritiesForEntity() {
		return Lists.newArrayList(CoreGroupPermission.TREENODE_DELETE);
	}
	
	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> formAttributes = super.getFormAttributes();
		//
		// add force delete, if currently logged user is TREENODE_ADMIN
		if (securityService.hasAnyAuthority(CoreGroupPermission.TREENODE_ADMIN)) {
			formAttributes.add(new IdmFormAttributeDto(EntityEventProcessor.PROPERTY_FORCE_DELETE, "Force delete", PersistentType.BOOLEAN));
		}
		//
		return formAttributes;
	}
	
	@Override
	protected OperationResult processDto(IdmTreeNodeDto treeNode) {
		boolean forceDelete = getParameterConverter().toBoolean(getProperties(), EntityEventProcessor.PROPERTY_FORCE_DELETE, false);
		if (!forceDelete) {
			return super.processDto(treeNode);
		}
		// force delete - without request by event
		try {
			// force delete can execute tree node admin only
			getService().checkAccess(treeNode, IdmBasePermission.ADMIN);
			//
			TreeNodeEvent treeNodeEvent = new TreeNodeEvent(TreeNodeEventType.DELETE, treeNode, new ConfigurationMap(getProperties()).toMap());
			treeNodeEvent.setPriority(PriorityType.HIGH);
			EventContext<IdmTreeNodeDto> result = treeNodeService.publish(treeNodeEvent);
			processedIds.add(result.getContent().getId());
			//
			return new OperationResult.Builder(OperationState.EXECUTED).build();
		} catch (ResultCodeException ex) {
			return new OperationResult.Builder(OperationState.EXCEPTION).setException(ex).build();
		} catch (Exception ex) {
			Throwable resolvedException = ExceptionUtils.resolveException(ex);
			if (resolvedException instanceof ResultCodeException) {
				return new OperationResult.Builder(OperationState.EXCEPTION)
						.setException((ResultCodeException) resolvedException)
						.build();
			}
			return new OperationResult.Builder(OperationState.EXCEPTION).setCause(ex).build();
		}
	}
	
	@Override
	protected OperationResult end(OperationResult result, Exception exception) {
		if (exception != null 
				|| (result != null && OperationState.EXECUTED != result.getState())) {
			return super.end(result, exception);
		}
		// success
		boolean forceDelete = isForceDelete();
		//
		if (forceDelete) {
			for (UUID treeNodeId : processedIds) {
				IdmTreeNodeDto treeNode = treeNodeService.get(treeNodeId);
				if (treeNode != null) {
					// delete all tree node children
					OperationResult errorResult = deleteTreeNode(treeNode, result);
					if (errorResult != null) {
						return errorResult;
					}
				} else {
					LOG.debug("Tree Node [{}] already deleted.", treeNodeId);
				}
				// clean up all states.
				entityStateManager.deleteStates(new IdmTreeNodeDto(treeNodeId), null, null);
			}
		}
		return super.end(result, exception);
	}

	@Override
	public ReadWriteDtoService<IdmTreeNodeDto, IdmTreeNodeFilter> getService() {
		return treeNodeService;
	}
	
	/**
	 * Delete tree node recursively.
	 * 
	 * @param treeNodeId
	 */
	public OperationResult deleteTreeNode(IdmTreeNodeDto treeNode, OperationResult result) {
		UUID treeNodeId = treeNode.getId();
		//
		IdmTreeNodeFilter filter = new IdmTreeNodeFilter();
		filter.setParent(treeNodeId);
		filter.setRecursively(false);
		for (IdmTreeNodeDto child: treeNodeService.find(filter, null)) {
			OperationResult errorResult = deleteTreeNode(child, result);
			if (errorResult != null) {
				return errorResult;
			}
		}
		//
		treeNodeService.delete(treeNode);
		// clean up all states
		entityStateManager.deleteStates(treeNode, null, null);
		clearSession();
		//
		LOG.debug("Tree Node [{}] deleted.", treeNodeId);
		return null; // ~ ok
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
