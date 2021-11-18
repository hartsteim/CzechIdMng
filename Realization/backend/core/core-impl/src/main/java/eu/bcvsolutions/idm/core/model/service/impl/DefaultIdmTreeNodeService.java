package eu.bcvsolutions.idm.core.model.service.impl;

import java.util.List;
import java.util.UUID;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.config.domain.TreeConfiguration;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeTypeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeTypeService;
import eu.bcvsolutions.idm.core.api.utils.AutowireHelper;
import eu.bcvsolutions.idm.core.eav.api.service.AbstractFormableService;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.exception.TreeNodeException;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmForestIndexEntity;
import eu.bcvsolutions.idm.core.model.entity.IdmForestIndexEntity_;
import eu.bcvsolutions.idm.core.model.entity.IdmTreeNode;
import eu.bcvsolutions.idm.core.model.entity.IdmTreeNode_;
import eu.bcvsolutions.idm.core.model.entity.IdmTreeType;
import eu.bcvsolutions.idm.core.model.entity.IdmTreeType_;
import eu.bcvsolutions.idm.core.model.event.TreeNodeEvent;
import eu.bcvsolutions.idm.core.model.event.processor.tree.TreeNodeDeleteProcessor;
import eu.bcvsolutions.idm.core.model.repository.IdmIdentityContractRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmTreeNodeRepository;
import eu.bcvsolutions.idm.core.model.service.api.IdmTreeNodeForestContentService;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.core.scheduler.task.impl.RebuildTreeNodeIndexTaskExecutor;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;

/**
 * Tree node service
 * - supports {@link TreeNodeEvent}
 * 
 * @author Radek Tomiška
 *
 */
@Service("treeNodeService")
public class DefaultIdmTreeNodeService 
		extends AbstractFormableService<IdmTreeNodeDto, IdmTreeNode, IdmTreeNodeFilter>
		implements IdmTreeNodeService {
	
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DefaultIdmTreeNodeService.class);
	//
	private final IdmTreeNodeRepository repository;
	private final IdmTreeTypeService treeTypeService;
	private final ConfigurationService configurationService;
	private final LongRunningTaskManager longRunningTaskManager;
	private final DefaultBaseTreeService<IdmTreeNode> baseTreeService;
	private final IdmTreeNodeForestContentService forestContentService;
	//
	@Autowired private TreeConfiguration treeConfiguration;

	@Autowired
	public DefaultIdmTreeNodeService(
		IdmTreeNodeRepository treeNodeRepository,
		IdmTreeTypeService treeTypeService,
		ConfigurationService configurationService,
		LongRunningTaskManager longRunningTaskManager,
		FormService formService,
		EntityEventManager entityEventManager,
		IdmIdentityContractRepository identityContractRepository, // TODO: should be removed, referential integrity is solved in processor (backward compatibility ...)
		DefaultBaseTreeService<IdmTreeNode> baseTreeService,
		IdmTreeNodeForestContentService forestContentService) {
		super(treeNodeRepository, entityEventManager, formService);
		//
		Assert.notNull(treeTypeService, "Service is required.");
		Assert.notNull(configurationService, "Service is required.");
		Assert.notNull(longRunningTaskManager, "Manager is required.");
		Assert.notNull(baseTreeService, "Service is required.");
		Assert.notNull(forestContentService, "Service is required.");
		//
		this.repository = treeNodeRepository;
		this.treeTypeService = treeTypeService;
		this.configurationService = configurationService;
		this.longRunningTaskManager = longRunningTaskManager;
		this.baseTreeService = baseTreeService;
		this.forestContentService = forestContentService;
	}
	
	@Override
	public AuthorizableType getAuthorizableType() {
		return new AuthorizableType(CoreGroupPermission.TREENODE, getEntityClass());
	}
	
	@Override
	@Transactional
	public IdmTreeNodeDto saveInternal(IdmTreeNodeDto treeNode) {
		// if index rebuild is in progress, then throw exception
		checkTreeType(treeNode.getTreeType());
		//
		if (isNew(treeNode)) {
			this.validate(treeNode, true);
			// create new
			treeNode = super.saveInternal(treeNode);
			IdmForestIndexEntity index = forestContentService.createIndex(IdmTreeNode.toForestTreeType(treeNode.getTreeType()), treeNode.getId(), treeNode.getParent());
			return setForestIndex(treeNode, index);
		}
		this.validate(treeNode, false);	
		// update - we need to reindex first
		IdmForestIndexEntity index = forestContentService.updateIndex(IdmTreeNode.toForestTreeType(treeNode.getTreeType()), treeNode.getId(), treeNode.getParent());
		treeNode = super.saveInternal(treeNode);
		return setForestIndex(treeNode, index);
	}
	
	/**
	 * Publish {@link TreeNodeEvent} only.
	 * 
	 * @see {@link TreeNodeDeleteProcessor}
	 */
	@Override
	@Transactional
	public void deleteInternal(IdmTreeNodeDto treeNode) {
		Assert.notNull(treeNode, "Tree node is required.");
		Assert.notNull(treeNode.getTreeType(), "Tree type is required.");
		LOG.debug("Deleting tree node [{}] - [{}]", treeNode.getTreeType(), treeNode.getCode());
		//
		// if index rebuild is in progress, then throw exception
		checkTreeType(treeNode.getTreeType());
		//
		Page<IdmTreeNode> nodes = repository.findChildren(null, treeNode.getId(), PageRequest.of(0, 1));
		if (nodes.getTotalElements() > 0) {
			throw new TreeNodeException(CoreResultCode.TREE_NODE_DELETE_FAILED_HAS_CHILDREN,  ImmutableMap.of("treeNode", treeNode.getCode()));
		}
		//
		forestContentService.deleteIndex(treeNode.getId());
		super.deleteInternal(treeNode);
	}
	
	@Override
	@Transactional(readOnly = true)
	public Page<IdmTreeNodeDto> findRoots(UUID treeTypeId, Pageable pageable) {
		return toDtoPage(this.repository.findRoots(treeTypeId, pageable));
	}

	@Override
	@Transactional(readOnly = true)
	public Page<IdmTreeNodeDto> findChildrenByParent(UUID parentId, Pageable pageable) {
		return toDtoPage(this.repository.findChildren(null, parentId, pageable));
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<IdmTreeNodeDto> findAllParents(UUID treeNodeId, Sort sort) {
		return toDtos(forestContentService.findAllParents(treeNodeId, sort), true);
	}
	
	@Override
	@Transactional
	public UUID rebuildIndexes(UUID treeTypeId) {
		Assert.notNull(treeTypeId, "Tree type identifier is required.");
		IdmTreeTypeDto treeType = treeTypeService.get(treeTypeId);
		Assert.notNull(treeType, "Tree type is required");
		//
		String treeTypeCode = treeType.getCode();
		RebuildTreeNodeIndexTaskExecutor rebuildTask = AutowireHelper.createBean(RebuildTreeNodeIndexTaskExecutor.class);
		rebuildTask.setTreeTypeCode(treeTypeCode);
		UUID longRunningTaskId = longRunningTaskManager.execute(rebuildTask).getExecutor().getLongRunningTaskId();
		// wee need long running task related to index rebuild immediately
		configurationService.setValue(treeTypeService.getConfigurationPropertyName(treeTypeCode, IdmTreeTypeService.CONFIGURATION_PROPERTY_REBUILD), longRunningTaskId.toString());
		return longRunningTaskId;
	}
	
	public IdmTreeNodeDto getDefaultTreeNode() {
		return treeConfiguration.getDefaultNode();
	}

	@Override
	protected List<Predicate> toPredicates(Root<IdmTreeNode> root, CriteriaQuery<?> query, CriteriaBuilder builder, IdmTreeNodeFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
		// fulltext
		if (!StringUtils.isEmpty(filter.getText())) {
			predicates.add(builder.or(
				builder.like(builder.lower(root.get(IdmTreeNode_.code)), "%" + filter.getText().toLowerCase() + "%"),
				builder.like(builder.lower(root.get(IdmTreeNode_.name)), "%" + filter.getText().toLowerCase() + "%")
			));
		}
		// tree type identifier
		if (filter.getTreeTypeId() != null) {
			predicates.add(builder.equal(root.get(IdmTreeNode_.treeType).get(AbstractEntity_.id), filter.getTreeTypeId()));
		}
		// tree type code
		String treeTypeCode = filter.getTreeTypeCode();
		if (StringUtils.isNotEmpty(treeTypeCode)) {
			predicates.add(builder.equal(root.get(IdmTreeNode_.treeType).get(IdmTreeType_.code), treeTypeCode));
		}
		// parent node
		UUID parent = filter.getParent();
		if (parent != null) {
			if (filter.isRecursively()) {
				// forest index needs tree type => same numbers in different trees
				Subquery<IdmTreeType> subqueryTreeType = query.subquery(IdmTreeType.class);
				Root<IdmTreeNode> subRootTreeType = subqueryTreeType.from(IdmTreeNode.class);
				subqueryTreeType.select(subRootTreeType.get(IdmTreeNode_.treeType));
				subqueryTreeType.where(builder.equal(subRootTreeType.get(IdmTreeNode_.id), parent));
				//
				Subquery<IdmTreeNode> subquery = query.subquery(IdmTreeNode.class);
				Root<IdmTreeNode> subRoot = subquery.from(IdmTreeNode.class);
				subquery.select(subRoot);
				Join<IdmTreeNode, IdmForestIndexEntity> forestIndexPath = subRoot.join(IdmTreeNode_.forestIndex);
				subquery.where(
						builder.and(
							builder.equal(subRoot.get(IdmTreeNode_.id), parent),
							// join tree type
							builder.equal(root.get(IdmTreeNode_.treeType), subqueryTreeType),
							// This is here because of the structure of forest index. We need to select only subtree and not the element itself.
							// In order to do that, we must shrink the boundaries of query so it is true only for subtree of given node.
							// Remember that between clause looks like this a >= x <= b, where a and b are boundaries, in our case lft+1 and rgt-1.
							builder.between(root.join(IdmTreeNode_.forestIndex).get(IdmForestIndexEntity_.lft),
									builder.sum(forestIndexPath.get(IdmForestIndexEntity_.lft), 1L),
									builder.diff(forestIndexPath.get(IdmForestIndexEntity_.rgt), 1L))));
				predicates.add(builder.exists(subquery));
			} else {
				predicates.add(builder.equal(root.get(IdmTreeNode_.parent).get(AbstractEntity_.id), parent));
			}
		}
		// default tree type
		if (filter.getDefaultTreeType() != null) {
			IdmTreeTypeDto defaultTreeType = treeTypeService.getDefaultTreeType();
			if (defaultTreeType == null) {
				// nothing to find
				predicates.add(builder.disjunction());
			} else {
				predicates.add(builder.equal(root.get(IdmTreeNode_.treeType).get(IdmTreeType_.id), defaultTreeType.getId()));
			}
		}
		// roots
		Boolean roots = filter.getRoots();
		if (roots != null) {
			if (roots) {
				predicates.add(builder.isNull(root.get(IdmTreeNode_.parent)));
			} else {
				predicates.add(builder.isNotNull(root.get(IdmTreeNode_.parent)));
			}
		}
		//
		return predicates;
	}
	
	private IdmTreeNodeDto setForestIndex(IdmTreeNodeDto treeNode, IdmForestIndexEntity index) {
		if (index != null) {
			treeNode.setLft(index.getLft());
			treeNode.setRgt(index.getRgt());
		}
		return treeNode;
	}
	
	private void validate(IdmTreeNodeDto node, boolean isNew) {	
		if (checkCorrectType(node, isNew)) {
			throw new TreeNodeException(CoreResultCode.TREE_NODE_BAD_TYPE,  "TreeNode ["+node.getName() +"] have bad type.");
		}
		if (this.baseTreeService.validateTreeNodeParents(isNew ? toEntity(node) : toEntity(node, repository.findById(node.getId()).get()))) {
			throw new TreeNodeException(CoreResultCode.TREE_NODE_BAD_PARENT,  "TreeNode ["+node.getName() +"] have bad parent.");
		}
	}
	
	/**
	 * Method check type of current node and saved node.
	 * 
	 * @param treeNode
	 * @return bool. True - if current and saved node is not same, false - if everything ist OK. When is node new return false;
	 */
	private boolean checkCorrectType(IdmTreeNodeDto treeNode, boolean isNew) {
		if (isNew) {
			if (treeNode.getParent() != null) {
				IdmTreeNodeDto parent = this.get(treeNode.getParent());
				return !treeNode.getTreeType().equals(parent.getTreeType());
			}
			// treeNode is new and hasn't parent
			return false;
		}
		
		IdmTreeNode currentNode = repository.findById(treeNode.getId()).orElse(null);
		
		if (currentNode != null) {
			return !currentNode.getTreeType().getId().equals(treeNode.getTreeType());
		} else {
			return false;
		}
	}
	
	private void checkTreeType(UUID treeTypeId) {
		IdmTreeTypeDto treeType = treeTypeService.get(treeTypeId);
		if (StringUtils.isNotBlank(configurationService.getValue(treeTypeService.getConfigurationPropertyName(treeType.getCode(), IdmTreeTypeService.CONFIGURATION_PROPERTY_REBUILD)))) {
			throw new ResultCodeException(CoreResultCode.FOREST_INDEX_RUNNING, ImmutableMap.of("treeType", treeType.getCode()));
		}
	}

}
