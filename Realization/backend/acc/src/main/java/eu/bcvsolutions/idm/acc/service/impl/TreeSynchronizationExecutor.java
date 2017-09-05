package eu.bcvsolutions.idm.acc.service.impl;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.persistence.EntityManager;

import org.apache.commons.collections.CollectionUtils;
import org.joda.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.domain.AttributeMapping;
import eu.bcvsolutions.idm.acc.domain.OperationResultType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationContext;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccTreeAccountDto;
import eu.bcvsolutions.idm.acc.dto.EntityAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncActionLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncItemLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.EntityAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.TreeAccountFilter;
import eu.bcvsolutions.idm.acc.entity.SysSystem;
import eu.bcvsolutions.idm.acc.exception.ProvisioningException;
import eu.bcvsolutions.idm.acc.repository.SysSyncConfigRepository;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccTreeAccountService;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningService;
import eu.bcvsolutions.idm.acc.service.api.SynchronizationEntityExecutor;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncActionLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncConfigService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncItemLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.CorrelationFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.service.ConfidentialStorage;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.GroovyScriptService;
import eu.bcvsolutions.idm.core.api.service.ReadWriteDtoService;
import eu.bcvsolutions.idm.core.eav.api.entity.FormableEntity;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.entity.IdmTreeNode;
import eu.bcvsolutions.idm.core.model.event.TreeNodeEvent;
import eu.bcvsolutions.idm.core.model.event.TreeNodeEvent.TreeNodeEventType;
import eu.bcvsolutions.idm.core.model.repository.IdmTreeNodeRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmTreeTypeRepository;
import eu.bcvsolutions.idm.core.model.service.api.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowProcessInstanceService;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.api.IcObjectClass;
import eu.bcvsolutions.idm.ic.filter.api.IcFilter;
import eu.bcvsolutions.idm.ic.filter.api.IcResultsHandler;
import eu.bcvsolutions.idm.ic.impl.IcAttributeImpl;
import eu.bcvsolutions.idm.ic.impl.IcLoginAttributeImpl;
import eu.bcvsolutions.idm.ic.impl.IcObjectClassImpl;
import eu.bcvsolutions.idm.ic.service.api.IcConnectorFacade;

/**
 * Default implementation of tree sync
 * 
 * @author svandav
 *
 */
@Component
public class TreeSynchronizationExecutor extends AbstractSynchronizationExecutor<IdmTreeNodeDto>
		implements SynchronizationEntityExecutor {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TreeSynchronizationExecutor.class);
	public final static String PARENT_FIELD = "parent";
	public final static String CODE_FIELD = "code";

	private final IdmTreeTypeRepository treeTypeRepository;
	private final IdmTreeNodeService treeNodeService;
	private final AccTreeAccountService treeAccoutnService;
	private final IdmTreeNodeRepository treeNodeRepository;
	private final SysSystemMappingService systemMappingService;
	private final SysSystemAttributeMappingService attributeHandlingService;
	private final SysSchemaObjectClassService schemaObjectClassService;
	
	@Autowired
	public TreeSynchronizationExecutor(IcConnectorFacade connectorFacade, SysSystemService systemService,
			SysSystemAttributeMappingService attributeHandlingService,
			SysSyncConfigService synchronizationConfigService, SysSyncLogService synchronizationLogService,
			SysSyncActionLogService syncActionLogService, AccAccountService accountService,
			SysSystemEntityService systemEntityService, ConfidentialStorage confidentialStorage,
			FormService formService, AccTreeAccountService treeAccoutnService, SysSyncItemLogService syncItemLogService,
			EntityEventManager entityEventManager, GroovyScriptService groovyScriptService,
			WorkflowProcessInstanceService workflowProcessInstanceService, EntityManager entityManager,
			IdmTreeNodeService treeNodeService, IdmTreeNodeRepository treeNodeRepository,
			IdmTreeTypeRepository treeTypeRepository,
			SysSystemMappingService systemMappingService,
			SysSyncConfigRepository synchronizationConfigRepository,
			SysSchemaObjectClassService schemaObjectClassService,
			SysSchemaAttributeService schemaAttributeService) {
		super(connectorFacade, systemService, attributeHandlingService, synchronizationConfigService,
				synchronizationLogService, syncActionLogService, accountService, systemEntityService,
				confidentialStorage, formService, syncItemLogService, entityEventManager, groovyScriptService,
				workflowProcessInstanceService, entityManager, systemMappingService, synchronizationConfigRepository,
				schemaObjectClassService, schemaAttributeService);

		Assert.notNull(treeNodeService, "Tree node service is mandatory!");
		Assert.notNull(treeAccoutnService, "Tree account service is mandatory!");
		Assert.notNull(treeNodeRepository);
		Assert.notNull(systemMappingService);
		Assert.notNull(attributeHandlingService);
		Assert.notNull(schemaObjectClassService);
		Assert.notNull(treeTypeRepository);

		this.treeNodeService = treeNodeService;
		this.treeAccoutnService = treeAccoutnService;
		this.treeNodeRepository = treeNodeRepository;
		this.systemMappingService = systemMappingService;
		this.attributeHandlingService = attributeHandlingService;
		this.schemaObjectClassService = schemaObjectClassService;
		this.treeTypeRepository = treeTypeRepository;
	}

	@Override
	public SysSyncConfigDto process(UUID synchronizationConfigId) {
		// Validate and create basic context
		SynchronizationContext context = this.validate(synchronizationConfigId);
				
		SysSyncConfigDto config = context.getConfig();
		SystemEntityType entityType = context.getEntityType();
		SysSystem system = context.getSystem();
		IcConnectorConfiguration connectorConfig = context.getConnectorConfig();
		List<SysSystemAttributeMappingDto> mappedAttributes = context.getMappedAttributes();	
		SysSystemMappingDto systemMapping = systemMappingService.get(context.getConfig().getSystemMapping());
		SysSchemaObjectClassDto schemaObjectClassDto = schemaObjectClassService.get(systemMapping.getObjectClass());
		IcObjectClass objectClass = new IcObjectClassImpl(schemaObjectClassDto.getObjectClassName());
		
		// Load last token
		Object lastToken = config.isReconciliation() ? null : config.getToken();

		// Create basic synchronization log
		SysSyncLogDto log = new SysSyncLogDto();
		log.setSynchronizationConfig(config.getId());
		log.setStarted(LocalDateTime.now());
		log.setRunning(true);
		log.setToken(lastToken != null ? lastToken.toString() : null);
		log.addToLog(MessageFormat.format("Synchronization was started in {0}.", log.getStarted()));

		// List of all accounts with full IC object (used in tree sync)
		Map<String, IcConnectorObject> accountsMap = new HashMap<>();

		longRunningTaskExecutor.setCounter(0L);

		try {
			log = synchronizationLogService.save(log);
			List<SysSyncActionLogDto> actionsLog = new ArrayList<>();
			// Add logs to context
			context
			.addLog(log)
			.addActionLogs(actionsLog);
			
			boolean export = false;

			if (export) {
				// Start exporting entities to resource
				log.addToLog("Exporting entities to resource started...");
				this.startExport(entityType, config, mappedAttributes, log, actionsLog);
			} else {
				
				if (config.getTokenAttribute() == null && !config.isReconciliation()) {
					throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_TOKEN_ATTRIBUTE_NOT_FOUND);
				}

				TreeResultsHandler resultHandler = new TreeResultsHandler(accountsMap);

				IcFilter filter = null; // We have to search all data for tree
				log.addToLog(MessageFormat.format("Start search with filter {0}.", "NONE"));
				log = synchronizationLogService.save(log);

				connectorFacade.search(system.getConnectorInstance(), connectorConfig, objectClass, filter,
						resultHandler);
				// Execute sync for this tree and searched accounts
				processTreeSync(context, accountsMap);
				log = context.getLog();
			}
			//
			log.addToLog(MessageFormat.format("Synchronization was correctly ended in {0}.", LocalDateTime.now()));
			synchronizationConfigService.save(config);
		} catch (Exception e) {
			String message = "Error during synchronization";
			log.addToLog(message);
			log.setContainsError(true);
			log.addToLog(Throwables.getStackTraceAsString(e));
			LOG.error(message, e);
		} finally {
			log.setRunning(false);
			log.setEnded(LocalDateTime.now());
			log = synchronizationLogService.save(log);
			//
			longRunningTaskExecutor.setCount(longRunningTaskExecutor.getCounter());
			longRunningTaskExecutor.updateState();
		}
		return config;
	}


	/**
	 * Call provisioning for given account
	 * 
	 * @param account
	 * @param entityType
	 * @param log
	 * @param logItem
	 * @param actionLogs
	 */
	@Override
	protected void doUpdateAccount(AccAccountDto account, SystemEntityType entityType, SysSyncLogDto log,
			SysSyncItemLogDto logItem, List<SysSyncActionLogDto> actionLogs) {
		UUID entityId = getEntityByAccount(account.getId());
		IdmTreeNode treeNode = null;
		if (entityId != null) {
			treeNode = treeNodeRepository.findOne(entityId);
		}
		if (treeNode == null) {
			addToItemLog(logItem, "Tree account relation (with ownership = true) was not found!");
			initSyncActionLog(SynchronizationActionType.UPDATE_ENTITY, OperationResultType.WARNING, logItem, log,
					actionLogs);
			return;
		}
		// Call provisioning for this entity
		callProvisioningForEntity(treeNode, entityType, logItem);
	}

	/**
	 * Call provisioning for given account
	 * 
	 * @param entity
	 * @param entityType
	 * @param logItem
	 */
	/**
	 * Call provisioning for given account
	 * 
	 * @param entity
	 * @param entityType
	 * @param logItem
	 */
	@Override
	protected void callProvisioningForEntity(AbstractEntity entity, SystemEntityType entityType, SysSyncItemLogDto logItem) {
		IdmTreeNode treeNode = (IdmTreeNode) entity;
		addToItemLog(logItem,
				MessageFormat.format(
						"Call provisioning (process TreeNodeEventType.SAVE) for treeNode ({0}) with name ({1}).",
						treeNode.getId(), treeNode.getName()));
		entityEventManager.process(new TreeNodeEvent(TreeNodeEventType.UPDATE, treeNodeService.get(treeNode.getId()))).getContent();
	}
	
	/**
	 * Save entity
	 * @param entity
	 * @param skipProvisioning
	 * @return
	 */
	@Override
	protected AbstractEntity saveEntity(AbstractEntity entity, boolean skipProvisioning) {
		IdmTreeNode treeNode = (IdmTreeNode) entity;
		// Create DTO mock ...
		IdmTreeNodeDto dummyDTO = new IdmTreeNodeDto(entity.getId());
		boolean isNew = treeNodeService.isNew(dummyDTO);
		
		// Content will be set in service (we need do transform entity to DTO). 
		// Here we set only dummy dto (null content is not allowed)
		EntityEvent<IdmTreeNodeDto> event = new TreeNodeEvent(isNew ? TreeNodeEventType.CREATE : TreeNodeEventType.UPDATE, dummyDTO, ImmutableMap.of(ProvisioningService.SKIP_PROVISIONING, skipProvisioning));
		
		return treeNodeService.publishTreeNode(treeNode, event);
	}


	/**
	 * Create and persist new entity by data from IC attributes
	 * 
	 * @param entityType
	 * @param mappedAttributes
	 * @param logItem
	 * @param uid
	 * @param icAttributes
	 * @param account
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected void doCreateEntity(SystemEntityType entityType, List<SysSystemAttributeMappingDto> mappedAttributes,
			SysSyncItemLogDto logItem, String uid, List<IcAttribute> icAttributes, AccAccountDto account) {
		// We will create new TreeNode
		addToItemLog(logItem, "Missing entity action is CREATE_ENTITY, we will create a new entity.");
		IdmTreeNode treeNode = new IdmTreeNode();
		// Fill entity by mapped attribute
		treeNode = (IdmTreeNode) fillEntity(mappedAttributes, uid, icAttributes, treeNode, true);
		
		treeNode.setTreeType(treeTypeRepository.findOne(this.getSystemMapping(mappedAttributes).getTreeType()));
		// Create new Entity
		treeNode = (IdmTreeNode) this.saveEntity(treeNode, true);
		// Update extended attribute (entity must be persisted first)
		updateExtendedAttributes(mappedAttributes, uid, icAttributes, treeNode, true);
		// Update confidential attribute (entity must be persisted first)
		updateConfidentialAttributes(mappedAttributes, uid, icAttributes, treeNode, true);

		// Create new Entity account relation
		EntityAccountDto entityAccount = this.createEntityAccountDto();
		entityAccount.setAccount(account.getId());
		entityAccount.setEntity(treeNode.getId());
		entityAccount.setOwnership(true);
		this.getEntityAccountService().save(entityAccount);

		// Call provisioning for entity
		this.callProvisioningForEntity(treeNode, entityType, logItem);

		// Entity Created
		addToItemLog(logItem, MessageFormat.format("Tree node with id {0} was created", treeNode.getId()));
		if (logItem != null) {
			logItem.setDisplayName(treeNode.getName());
		}
	}

	/**
	 * Fill data from IC attributes to entity (EAV and confidential storage too)
	 * 
	 * @param account
	 * @param entityType
	 * @param uid
	 * @param icAttributes
	 * @param mappedAttributes
	 * @param log
	 * @param logItem
	 * @param actionLogs
	 */
	@Override
	protected void doUpdateEntity(SynchronizationContext context) {
		
		String uid = context.getUid();
		SysSyncLogDto log = context.getLog(); 
		SysSyncItemLogDto logItem = context.getLogItem();
		List<SysSyncActionLogDto> actionLogs = context.getActionLogs();
		List<SysSystemAttributeMappingDto> mappedAttributes = context.getMappedAttributes();
		AccAccountDto account = context.getAccount();
		List<IcAttribute> icAttributes = context.getIcObject().getAttributes();
		UUID entityId = getEntityByAccount(account.getId());
		IdmTreeNode treeNode = null;
		
		if (entityId != null) {
			treeNode = treeNodeRepository.findOne(entityId);
		}
		if (treeNode != null) {
			// Update entity
			treeNode = (IdmTreeNode) fillEntity(mappedAttributes, uid, icAttributes, treeNode, false);
			treeNode = (IdmTreeNode) this.saveEntity(treeNode, true);
			// Update extended attribute (entity must be persisted first)
			updateExtendedAttributes(mappedAttributes, uid, icAttributes, treeNode, false);
			// Update confidential attribute (entity must be persisted first)
			updateConfidentialAttributes(mappedAttributes, uid, icAttributes, treeNode, false);

			// TreeNode Updated
			addToItemLog(logItem, MessageFormat.format("TreeNode with id {0} was updated", treeNode.getId()));
			if (logItem != null) {
				logItem.setDisplayName(treeNode.getName());
			}
			
			// Call provisioning for entity
			this.callProvisioningForEntity(treeNode, context.getEntityType(), logItem);

			return;
		} else {
			addToItemLog(logItem, "Tree - account relation (with ownership = true) was not found!");
			initSyncActionLog(SynchronizationActionType.UPDATE_ENTITY, OperationResultType.WARNING, logItem, log,
					actionLogs);
			return;
		}
	}

	/**
	 * Operation remove EntityAccount relations and linked roles
	 * 
	 * @param account
	 * @param removeIdentityRole
	 * @param log
	 * @param logItem
	 * @param actionLogs
	 */
	@Override
	protected void doUnlink(AccAccountDto account, boolean removeIdentityRole, SysSyncLogDto log, SysSyncItemLogDto logItem,
			List<SysSyncActionLogDto> actionLogs) {

		TreeAccountFilter treeAccountFilter = new TreeAccountFilter();
		treeAccountFilter.setAccountId(account.getId());
		List<AccTreeAccountDto> treeAccounts = treeAccoutnService.find(treeAccountFilter, null).getContent();
		if (treeAccounts.isEmpty()) {
			addToItemLog(logItem, "Tree account relation was not found!");
			initSyncActionLog(SynchronizationActionType.UPDATE_ENTITY, OperationResultType.WARNING, logItem, log,
					actionLogs);
			return;
		}
		addToItemLog(logItem, MessageFormat.format("Tree-account relations to delete {0}", treeAccounts));

		treeAccounts.stream().forEach(treeAccount -> {
			// We will remove tree account, but without delete connected
			// account
			treeAccoutnService.delete(treeAccount, false);
			addToItemLog(logItem,
					MessageFormat.format(
							"Tree-account relation deleted (without call delete provisioning) (treeNode: {0}, id: {1})",
							treeAccount.getTreeNode(), treeAccount.getId()));

		});
		return;
	}

	/**
	 * Delete entity linked with given account
	 * 
	 * @param account
	 * @param entityType
	 * @param log
	 * @param logItem
	 * @param actionLogs
	 */
	@Override
	protected void doDeleteEntity(AccAccountDto account, SystemEntityType entityType, SysSyncLogDto log,
			SysSyncItemLogDto logItem, List<SysSyncActionLogDto> actionLogs) {
		UUID entityId = getEntityByAccount(account.getId());
		IdmTreeNode treeNode = null;
		if (entityId != null) {
			treeNode = treeNodeRepository.findOne(entityId);
		}
		if (treeNode == null) {
			addToItemLog(logItem, "Tree account relation (with ownership = true) was not found!");
			initSyncActionLog(SynchronizationActionType.DELETE_ENTITY, OperationResultType.WARNING, logItem, log,
					actionLogs);
			return;
		}
		
		logItem.setDisplayName(treeNode.getName());
		// Delete entity (recursively)
		deleteChildrenRecursively(treeNode, logItem);
	}
	
	/**
	 * Start export entities to target resource
	 * @param entityType
	 * @param config
	 * @param mappedAttributes
	 * @param log
	 * @param actionsLog
	 */
	@Override
	protected void startExport(SystemEntityType entityType, SysSyncConfigDto config,
			List<SysSystemAttributeMappingDto> mappedAttributes, SysSyncLogDto log, List<SysSyncActionLogDto> actionsLog) {
		SysSystemMappingDto systemMapping = systemMappingService.get(config.getSystemMapping());
		SysSchemaObjectClassDto schemaObjectClassDto = schemaObjectClassService.get(systemMapping.getObjectClass());
		SysSystem system = systemService.get(schemaObjectClassDto.getSystem());
		SysSystemAttributeMappingDto uidAttribute = attributeHandlingService.getUidAttribute(mappedAttributes,
				system);

		List<IdmTreeNode> roots = treeNodeRepository.findChildren(systemMapping.getTreeType(), null, null).getContent();
		roots.stream().forEach(root -> {
			SynchronizationContext itemBuilder = new SynchronizationContext();
			itemBuilder.addConfig(config) //
					.addSystem(system) //
					.addEntityType(entityType) //
					.addLog(log) //
					.addActionLogs(actionsLog);
			// Start export for this entity
			exportChildrenRecursively(root, itemBuilder, uidAttribute);
		});
	}

	@Override
	protected Object getValueByMappedAttribute(AttributeMapping attribute, List<IcAttribute> icAttributes) {
		
		Object transformedValue = super.getValueByMappedAttribute(attribute, icAttributes);
		
		if (transformedValue != null && PARENT_FIELD.equals(attribute.getIdmPropertyName())) {
			String parentUid = transformedValue.toString();
			SysSystemMappingDto systemMapping = systemMappingService.get(((SysSystemAttributeMappingDto)attribute).getSystemMapping());
			SysSchemaObjectClassDto schemaObjectClass = schemaObjectClassService.get(systemMapping.getObjectClass());
			UUID systemId = schemaObjectClass.getSystem();
			// Find account by UID from parent field
			AccountFilter accountFilter = new AccountFilter();
			accountFilter.setUid(parentUid);
			accountFilter.setSystemId(systemId);
			transformedValue = null;
			List<AccAccountDto> parentAccounts = accountService.find(accountFilter, null).getContent();
			if (!parentAccounts.isEmpty()) {
				UUID parentAccount = parentAccounts.get(0).getId();
				// Find relation between tree and account
				TreeAccountFilter treeAccountFilter = new TreeAccountFilter();
				treeAccountFilter.setAccountId(parentAccount);
				List<AccTreeAccountDto> treeAccounts = treeAccoutnService.find(treeAccountFilter, null).getContent();
				if(!treeAccounts.isEmpty()){
					// Find parent tree node by ID
					// TODO: resolve more treeAccounts situations
					transformedValue = treeNodeRepository.findOne(treeAccounts.get(0).getTreeNode());
				} else {
					LOG.warn(
							"For parent UID: [{}] on system ID [{}] and acc account: [{}], was not found tree accounts! Return null value in parent!!",
							parentUid, systemId, parentAccount);
					throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_TREE_PARENT_TREE_ACCOUNT_NOT_FOUND,
							ImmutableMap.of("parentUid", parentUid, "systemId", systemId, "parentAccount",
									parentAccount));
				}
			} else {
				LOG.warn(
						"For parent UID: [{}] on system ID [{}], was not found parents account! Return null value in parent!!",
						parentUid, systemId);
				throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_TREE_PARENT_ACCOUNT_NOT_FOUND,
						ImmutableMap.of("parentUid", parentUid, "systemId", systemId));
			}
		}
		return transformedValue;
	}

	@Override
	public boolean supports(SystemEntityType delimiter) {
		return SystemEntityType.TREE == delimiter;
	}

	@Override
	protected AbstractEntity findEntityById(UUID entityId, SystemEntityType entityType) {
		return treeNodeRepository.findOne(entityId);
	}

	@Override
	protected EntityAccountFilter createEntityAccountFilter() {
		return new TreeAccountFilter();
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected ReadWriteDtoService getEntityAccountService() {
		return treeAccoutnService;
	}

	@Override
	protected EntityAccountDto createEntityAccountDto() {
		return new AccTreeAccountDto();
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected ReadWriteDtoService getEntityService() {
		return null; // We don't have DTO service for IdmTreeNode now
	}

	public static class TreeResultsHandler implements IcResultsHandler {

		// List of all accounts
		private Map<String, IcConnectorObject> accountsMap = new HashMap<>();

		public TreeResultsHandler(Map<String, IcConnectorObject> accountsMap) {
			this.accountsMap = accountsMap;
		}

		@Override
		public boolean handle(IcConnectorObject connectorObject) {
			Assert.notNull(connectorObject);
			Assert.notNull(connectorObject.getUidValue());
			String uid = connectorObject.getUidValue();
			accountsMap.put(uid, connectorObject);
			return true;

		}
	}

	@Override
	protected List<IdmTreeNode> findAllEntity() {
		return Lists.newArrayList(treeNodeRepository.findAll());
	}
	
	/**
	 * Execute sync for tree and given accounts.
	 * @param context
	 * @param accountsMap
	 */
	private void processTreeSync(SynchronizationContext context,
			Map<String, IcConnectorObject> accountsMap) {
		
		SysSyncConfigDto config = context.getConfig();
		SystemEntityType entityType = context.getEntityType();
		SysSystem system = context.getSystem();
		List<SysSystemAttributeMappingDto> mappedAttributes = context.getMappedAttributes();
		SysSyncLogDto log = context.getLog();
		List<SysSyncActionLogDto> actionsLog = context.getActionLogs();
		AttributeMapping tokenAttribute = context.getTokenAttribute();
		Set<String> accountsUseInTreeList = new HashSet<>();
		
		// Find UID/PARENT/CODE attribute
		SysSystemAttributeMappingDto uidAttribute = attributeHandlingService.getUidAttribute(mappedAttributes, system);
		SysSystemAttributeMappingDto parentAttribute = getAttributeByIdmProperty(PARENT_FIELD, mappedAttributes);
		SysSystemAttributeMappingDto codeAttribute = getAttributeByIdmProperty(CODE_FIELD, mappedAttributes);
		if (parentAttribute == null) {
			LOG.warn("Parent attribute is not specified! Organization tree will not be recomputed.");
		}
		if (codeAttribute == null) {
			LOG.warn("Code attribute is not specified!");
		}
		// Find all roots
		Collection<String> roots = findRoots(parentAttribute, accountsMap, config);

		if (roots.isEmpty()) {
			log.addToLog("No roots to synchronization found!");
		} else {
			log.addToLog(MessageFormat.format("We found [{0}] roots: [{1}]", roots.size(), roots));
		}

		if (parentAttribute == null) {
			// just alias all accounts as roots and process
			roots.addAll(accountsMap.keySet());
		}
		for (String root : roots) {
			accountsUseInTreeList.add(root);
			IcConnectorObject account = accountsMap.get(root);

			SynchronizationContext itemContext = SynchronizationContext.cloneContext(context);
			itemContext
					.addUid(root)
					.addAccount(null)
					.addIcObject(account)
					.addTokenAttribute(tokenAttribute);

			boolean result = handleIcObject(itemContext);
			if (!result) {
				return;
			}

			if (parentAttribute != null) {
				Object uidValueParent = this.getValueByMappedAttribute(uidAttribute, account.getAttributes());
				processChildren(parentAttribute, uidValueParent, uidAttribute, accountsMap, accountsUseInTreeList,
						itemContext, roots);
			}
		}

		if (config.isReconciliation()) {
			// We do reconciliation (find missing account)
			startReconciliation(entityType, accountsUseInTreeList, config, system, log, actionsLog);
		}
	}
	
	private void exportChildrenRecursively(IdmTreeNode treeNode, SynchronizationContext itemBuilder, SysSystemAttributeMappingDto uidAttribute){
		SysSyncItemLogDto logItem = itemBuilder.getLogItem();
		
		List<IdmTreeNode> children = treeNodeRepository.findChildren(null, treeNode.getId(), null).getContent();
		if (children.isEmpty()) {
			this.exportEntity(itemBuilder, uidAttribute, treeNode);
		} else {
			addToItemLog(logItem,
					MessageFormat.format("Tree node [{0}] has children [count={1}].",
							treeNode.getName(), children.size()));
			this.exportEntity(itemBuilder, uidAttribute, treeNode);
			children.forEach(child -> {
				exportChildrenRecursively(child, itemBuilder, uidAttribute);
			});
		}
	}

	private void deleteChildrenRecursively(IdmTreeNode treeNode, SysSyncItemLogDto logItem) {
		List<IdmTreeNode> children = treeNodeRepository.findChildren(null, treeNode.getId(), null).getContent();
		if (children.isEmpty()) {
			treeNodeService.deleteById(treeNode.getId());
			addToItemLog(logItem, MessageFormat.format("Tree node [{0}] was deleted.", treeNode.getName()));
		} else {
			addToItemLog(logItem,
					MessageFormat.format("Tree node [{0}] has children [count={1}]. We have to delete them first.",
							treeNode.getName(), children.size()));
			children.forEach(child -> {
				deleteChildrenRecursively(child, logItem);
			});
			treeNodeService.deleteById(treeNode.getId());
			addToItemLog(logItem, MessageFormat.format("Tree node [{0}] was deleted.", treeNode.getName()));
		}
	}
	

	/**
	 * Find all roots for this tree (uses groovy script for root definition)
	 * @param parentAttribute
	 * @param accountsMap
	 * @param config
	 * @return
	 */
	private Collection<String> findRoots(SysSystemAttributeMappingDto parentAttribute,
										 Map<String, IcConnectorObject> accountsMap, SysSyncConfigDto config) {
		Set<String> roots = Sets.newHashSet();
		if (parentAttribute == null) {
			return roots;
		}
		accountsMap.forEach((uid, account) -> {
			if (StringUtils.hasLength(config.getRootsFilterScript())) {
				Map<String, Object> variables = new HashMap<>();
				variables.put("account", account);

				List<Class<?>> allowTypes = new ArrayList<>();
				allowTypes.add(IcAttributeImpl.class);
				allowTypes.add(IcAttribute.class);
				allowTypes.add(IcLoginAttributeImpl.class);
				Object isRoot = groovyScriptService.evaluate(config.getRootsFilterScript(), variables,
						allowTypes);
				if (isRoot != null && !(isRoot instanceof Boolean)) {
					throw new ProvisioningException(
							AccResultCode.SYNCHRONIZATION_TREE_ROOT_FILTER_VALUE_WRONG_TYPE,
							ImmutableMap.of("type", isRoot.getClass().getName()));
				}
				if ((Boolean) isRoot) {
					roots.add(uid);
				}
			} else {
				// Default search root strategy (if is parent null, then is node root)
				Object parentValue = super.getValueByMappedAttribute(parentAttribute, account.getAttributes());
				if (parentValue == null) {
					roots.add(uid);
				}
			}
		});
		return roots;
	}

	/**
	 * Process recursively tree children
	 *
	 * @param parentAttribute
	 * @param uidValueParent
	 * @param uidAttribute
	 * @param accountsMap
	 * @param accountsUseInTreeList
	 * @param context
	 */
	private void processChildren(SysSystemAttributeMappingDto parentAttribute, Object uidValueParent,
			SysSystemAttributeMappingDto uidAttribute, Map<String, IcConnectorObject> accountsMap,
			Set<String> accountsUseInTreeList, SynchronizationContext context, Collection<String> roots) {

		accountsMap.forEach((uid, account) -> {
			if (roots.contains(uid)) {
				return;
			}
			Object parentValue = super.getValueByMappedAttribute(parentAttribute, account.getAttributes());
			if (parentValue != null && parentValue.equals(uidValueParent)) {
				// Account is use in tree
				accountsUseInTreeList.add(uid);

				// Do provisioning for this account
				SynchronizationContext itemContext = SynchronizationContext.cloneContext(context);
				itemContext
				.addUid(uid)
				.addIcObject(account)
				.addAccount(null);

				boolean resultChild = handleIcObject(itemContext);
				if (!resultChild) {
					return;
				}
				Object uidValueParentChilde = super.getValueByMappedAttribute(uidAttribute, account.getAttributes());
				processChildren(parentAttribute, uidValueParentChilde, uidAttribute, accountsMap, accountsUseInTreeList,
						itemContext, roots);

			}
		});
	}

	@Override
	protected Class<? extends FormableEntity> getEntityClass() {
		return IdmTreeNode.class;
	}

	@Override
	protected CorrelationFilter getEntityFilter() {
		return new IdmTreeNodeFilter();
	}

	@Override
	protected AbstractEntity findEntityByAttribute(String idmAttributeName, String value) {
		CorrelationFilter filter = getEntityFilter();
		filter.setProperty(idmAttributeName);
		filter.setValue(value);
		
		List<IdmTreeNodeDto> entities = treeNodeService.find((IdmTreeNodeFilter) filter, null).getContent();
		
		if (CollectionUtils.isEmpty(entities)) {
			return null;
		}
		if (entities.size() > 1) {
			throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_CORRELATION_TO_MANY_RESULTS,
					ImmutableMap.of("correlationAttribute", idmAttributeName, "value", value));
		}
		if (entities.size() == 1) {
			return treeNodeRepository.findOne(entities.get(0).getId());
		}
		return null;
	}
}
