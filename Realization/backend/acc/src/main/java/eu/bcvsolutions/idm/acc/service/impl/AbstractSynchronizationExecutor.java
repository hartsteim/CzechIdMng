package eu.bcvsolutions.idm.acc.service.impl;

import java.beans.IntrospectionException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;

import eu.bcvsolutions.idm.core.api.config.datasource.CoreEntityManager;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.delegate.VariableScope;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.AccModuleDescriptor;
import eu.bcvsolutions.idm.acc.config.domain.ProvisioningConfiguration;
import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.domain.AccountType;
import eu.bcvsolutions.idm.acc.domain.AttributeMapping;
import eu.bcvsolutions.idm.acc.domain.OperationResultType;
import eu.bcvsolutions.idm.acc.domain.ReconciliationMissingAccountActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationContext;
import eu.bcvsolutions.idm.acc.domain.SynchronizationLinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationMissingEntityActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationSituationType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationUnlinkedActionType;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AttributeValueWrapperDto;
import eu.bcvsolutions.idm.acc.dto.EntityAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncActionLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncItemLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.EntityAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncActionLogFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncLogFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemAttributeMappingFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemEntityFilter;
import eu.bcvsolutions.idm.acc.entity.SysSchemaObjectClass_;
import eu.bcvsolutions.idm.acc.entity.SysSyncConfig;
import eu.bcvsolutions.idm.acc.entity.SysSyncConfig_;
import eu.bcvsolutions.idm.acc.entity.SysSystemAttributeMapping_;
import eu.bcvsolutions.idm.acc.event.SynchronizationEventType;
import eu.bcvsolutions.idm.acc.exception.ProvisioningException;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.EntityAccountService;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningService;
import eu.bcvsolutions.idm.acc.service.api.SynchronizationEntityExecutor;
import eu.bcvsolutions.idm.acc.service.api.SynchronizationService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncActionLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncConfigService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncItemLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityTypeManager;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.acc.system.entity.SystemEntityTypeRegistrable;
import eu.bcvsolutions.idm.core.api.config.cache.domain.ValueWrapper;
import eu.bcvsolutions.idm.core.api.domain.Codeable;
import eu.bcvsolutions.idm.core.api.domain.Loggable;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.FormableDto;
import eu.bcvsolutions.idm.core.api.dto.filter.BaseFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.CorrelationFilter;
import eu.bcvsolutions.idm.core.api.event.CoreEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.api.service.ConfidentialStorage;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.GroovyScriptService;
import eu.bcvsolutions.idm.core.api.service.IdmCacheManager;
import eu.bcvsolutions.idm.core.api.service.ReadWriteDtoService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.api.utils.EntityUtils;
import eu.bcvsolutions.idm.core.api.utils.ExceptionUtils;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowProcessInstanceService;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;
import eu.bcvsolutions.idm.ic.api.IcConnectorKey;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.api.IcObjectClass;
import eu.bcvsolutions.idm.ic.api.IcSyncDelta;
import eu.bcvsolutions.idm.ic.api.IcSyncResultsHandler;
import eu.bcvsolutions.idm.ic.api.IcSyncToken;
import eu.bcvsolutions.idm.ic.domain.IcFilterOperationType;
import eu.bcvsolutions.idm.ic.filter.api.IcFilter;
import eu.bcvsolutions.idm.ic.filter.api.IcResultsHandler;
import eu.bcvsolutions.idm.ic.filter.impl.IcAndFilter;
import eu.bcvsolutions.idm.ic.filter.impl.IcFilterBuilder;
import eu.bcvsolutions.idm.ic.filter.impl.IcNotFilter;
import eu.bcvsolutions.idm.ic.filter.impl.IcOrFilter;
import eu.bcvsolutions.idm.ic.impl.IcAttributeImpl;
import eu.bcvsolutions.idm.ic.impl.IcObjectClassImpl;
import eu.bcvsolutions.idm.ic.impl.IcSyncDeltaTypeEnum;
import eu.bcvsolutions.idm.ic.impl.IcSyncTokenImpl;
import eu.bcvsolutions.idm.ic.service.api.IcConnectorFacade;

/**
 * Abstract executor for do synchronization and reconciliation.
 *
 * @author svandav
 * @author Roman Kucera
 * @param <DTO>
 *
 */
public abstract class AbstractSynchronizationExecutor<DTO extends AbstractDto>
		implements SynchronizationEntityExecutor {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory
			.getLogger(AbstractSynchronizationExecutor.class);

	public static final String CACHE_NAME = IdmCacheManager.getCacheName(AccModuleDescriptor.MODULE_ID, "sync-mapping-cache");

	@Autowired
	private WorkflowProcessInstanceService workflowProcessInstanceService;
	@Autowired
	protected IcConnectorFacade connectorFacade;
	@Autowired
	protected SysSystemService systemService;
	@Autowired
	protected SysSystemAttributeMappingService systemAttributeMappingService;
	@Autowired
	protected SysSyncConfigService synchronizationConfigService;
	@Autowired
	protected SysSyncLogService synchronizationLogService;
	@Autowired
	protected SysSyncItemLogService syncItemLogService;
	@Autowired
	protected SysSyncActionLogService syncActionLogService;
	@Autowired
	protected SysSystemEntityService systemEntityService;
	@Autowired
	protected AccAccountService accountService;
	@Autowired
	protected GroovyScriptService groovyScriptService;
	@Autowired
	private ConfidentialStorage confidentialStorage;
	@Autowired
	protected FormService formService;
	@Autowired
	protected EntityEventManager entityEventManager;
	@Autowired
	@CoreEntityManager
	private EntityManager entityManager;
	@Autowired
	protected SysSystemMappingService systemMappingService;
	@Autowired
	private SysSchemaObjectClassService schemaObjectClassService;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	@Autowired
	protected ProvisioningConfiguration provisioningConfiguration;
	@Autowired
	private ProcessEngine processEngine;
	@Autowired
	@Lazy
	private ProvisioningService provisioningService;
	@Autowired(required = false)
	private IdmCacheManager idmCacheManager;
	@Autowired
	private SysSystemEntityTypeManager systemEntityManager;
	// Instance of LRT
	protected AbstractSchedulableTaskExecutor<Boolean> longRunningTaskExecutor;
	// Context for whole sync.
	protected SynchronizationContext syncContext;

	/**
	 * Returns entity type for this synchronization executor
	 */
	protected String getEntityType() {
		return systemEntityManager.getSystemEntityByClass(getService().getDtoClass()).getSystemEntityCode();
	}

	@Override
	public boolean supports(SystemEntityTypeRegistrable delimiter) {
		return getEntityType().equals(delimiter.getSystemEntityCode());
	}

	@Override
	public AbstractSysSyncConfigDto process(UUID synchronizationConfigId) {
		// Clear cache
		idmCacheManager.evictCache(CACHE_NAME);
		SysSyncLogDto log = new SysSyncLogDto();
		// Create basic synchronization log
		log.setSynchronizationConfig(synchronizationConfigId);
		log.setStarted(ZonedDateTime.now());
		try {
			// Validate and create basic context
			SynchronizationContext context = this.validate(synchronizationConfigId);

			AbstractSysSyncConfigDto config = context.getConfig();
			String entityType = context.getEntityType();
			SysSystemDto system = context.getSystem();
			IcConnectorConfiguration connectorConfig = context.getConnectorConfig();
			SysSystemMappingDto systemMapping = systemMappingService.get(config.getSystemMapping());
			SysSchemaObjectClassDto schemaObjectClassDto = schemaObjectClassService.get(systemMapping.getObjectClass());
			IcObjectClass objectClass = new IcObjectClassImpl(schemaObjectClassDto.getObjectClassName());
			// Load last token
			String lastToken = config.isReconciliation() ? null : config.getToken();
			IcSyncToken lastIcToken = Strings.isNullOrEmpty(lastToken) ? null : new IcSyncTokenImpl(lastToken);

			log.setToken(lastToken != null ? lastToken : null);
			log.setRunning(true);
			log = syncStarted(log, context);

			// List of all accounts keys (used in reconciliation)
			Set<String> systemAccountsList = new HashSet<>();

			longRunningTaskExecutor.setCounter(0L);

			log = synchronizationLogService.save(log);
			List<SysSyncActionLogDto> actionsLog = new ArrayList<>();

			// add logs to context
			context.addLog(log).addActionLogs(actionsLog);

			// Is differential sync enabled?
			if (config.isDifferentialSync()) {
				log.addToLog(
						"Synchronization is running as differential (entities will be updated only if least one attribute was changed).");
			}

			if (config.isCustomFilter() || config.isReconciliation()) {
				// Custom filter Sync
				log.addToLog("Synchronization will use custom filter (not synchronization implemented in connector).");
				AttributeMapping tokenAttribute = null;
				if (config.getTokenAttribute() != null) {
					tokenAttribute = systemAttributeMappingService.get(config.getTokenAttribute());
				}

				if (tokenAttribute == null && !config.isReconciliation()) {
					throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_TOKEN_ATTRIBUTE_NOT_FOUND);
				}
				context.addTokenAttribute(tokenAttribute);
				// Resolve filter for custom search
				IcFilter filter = resolveSynchronizationFilter(config);
				log.addToLog(MessageFormat.format("Start search with filter [{0}].", filter != null ? filter : "NONE"));
				
				connectorFacade.search(systemService.getConnectorInstance(system), connectorConfig, objectClass, filter,
						new DefaultResultHandler(context, systemAccountsList));
			} else {
				// Inner Sync
				log.addToLog("Synchronization will use inner connector synchronization implementation.");
				DefalutSyncResultHandler syncResultsHandler = new DefalutSyncResultHandler(context, systemAccountsList);
				connectorFacade.synchronization(systemService.getConnectorInstance(system), connectorConfig, objectClass,
						lastIcToken, syncResultsHandler);
			}

			// We do reconciliation (find missing account)
			if (config.isReconciliation() && log.isRunning()) {
				startReconciliation(entityType, systemAccountsList, config, system, log, actionsLog);
			}
			// Sync is correctly ends if wasn't cancelled
			if (log.isRunning()) {
				log = syncCorrectlyEnded(log, context);
			}
			return synchronizationConfigService.save(config);
		} catch (Exception e) {
			String message = "Error during synchronization";
			log.addToLog(message);
			log.setContainsError(true);
			log.addToLog(Throwables.getStackTraceAsString(e));
			throw e;
		} finally {
			syncEnd(log, syncContext);
			log.setRunning(false);
			log.setEnded(ZonedDateTime.now());
			synchronizationLogService.save(log);
			//
			longRunningTaskExecutor.setCount(longRunningTaskExecutor.getCounter());
			longRunningTaskExecutor.updateState();
			// Clear cache
			idmCacheManager.evictCache(CACHE_NAME);
		}
	}

	// It is called in any case after the synchronization is completed.
	protected void syncEnd(SysSyncLogDto log, SynchronizationContext syncContext) {
		//
	}

	/**
	 * Method called after sync started.
	 *
	 * @param log
	 * @param context
	 * @return
	 */
	protected SysSyncLogDto syncStarted(SysSyncLogDto log, SynchronizationContext context) {
		log.addToLog(MessageFormat.format("Synchronization was started in [{0}].", log.getStarted()));
		AbstractSysSyncConfigDto config = context.getConfig();
		if (config != null) {
			log.addToLog(getSynchronizationConfigurationDetail(config));
		}
		
		return log;
	}
	
	/**
	 * Return the configuration used to run the synchronization. This will be saved in the synchronization
	 * log.
	 * 
	 * @param config
	 * @return
	 */
	private String getSynchronizationConfigurationDetail(AbstractSysSyncConfigDto config) {
		StringBuilder settings = new StringBuilder();
		settings.append("Synchronization used the following settings:");
		// mapping
		SysSystemMappingDto mapping = DtoUtils.getEmbedded(config,
				SysSyncConfig_.systemMapping, SysSystemMappingDto.class);
		if (mapping != null) {
			settings.append(MessageFormat.format("\nSynchronization mapping: [{0}]", mapping.getName()));
		}
		// correlation attribute
		SysSystemAttributeMappingDto correlationAttribute = DtoUtils.getEmbedded(config,
				SysSyncConfig_.correlationAttribute, SysSystemAttributeMappingDto.class);
		if (correlationAttribute != null) {
			settings.append(MessageFormat.format("\nCorrelation attribute: [{0}]", correlationAttribute.getName()));
		}
		settings.append(MessageFormat.format("\nIs running as reconciliation: [{0}]", config.isReconciliation()));
		// initial token
		if (!StringUtils.isEmpty(config.getToken())) {
			settings.append(MessageFormat.format("\nInitial token: [{0}]", config.getToken()));
		}
		// linked account action
		if (config.getLinkedAction() != null) {
			settings.append(MessageFormat.format("\nLinked account action: [{0}]", config.getLinkedAction()));
		}
		if (!StringUtils.isEmpty(config.getLinkedActionWfKey())) {
			settings.append(MessageFormat.format("\nLinked account action WF key: [{0}]", config.getLinkedActionWfKey()));
		}
		// unlinked account action
		if (config.getUnlinkedAction() != null) {
			settings.append(MessageFormat.format("\nUnlinked account action: [{0}]", config.getUnlinkedAction()));
		}
		if (!StringUtils.isEmpty(config.getUnlinkedActionWfKey())) {
			settings.append(MessageFormat.format("\nUnlinked account action WF key: [{0}]", config.getUnlinkedActionWfKey()));
		}
		// missing entity action
		if (config.getMissingEntityAction() != null) {
			settings.append(MessageFormat.format("\nMissing entity action: [{0}]", config.getMissingEntityAction()));
		}
		if (!StringUtils.isEmpty(config.getMissingEntityActionWfKey())) {
			settings.append(MessageFormat.format("\nMissing entity action WF key: [{0}]", config.getMissingEntityActionWfKey()));
		}
		// missing account action
		if (config.getMissingAccountAction() != null) {
			settings.append(MessageFormat.format("\nMissing account action: [{0}]", config.getMissingAccountAction()));
		}
		if (!StringUtils.isEmpty(config.getMissingAccountActionWfKey())) {
			settings.append(MessageFormat.format("\nMissing account action WF key: [{0}]", config.getMissingAccountActionWfKey()));
		}
		
		return settings.toString();
	}

	/**
	 * Method called after sync correctly ended.
	 *
	 * @param log
	 * @param context
	 * @return
	 */
	protected SysSyncLogDto syncCorrectlyEnded(SysSyncLogDto log, SynchronizationContext context) {
		log.addToLog(MessageFormat.format("Synchronization was correctly ended in [{0}].", ZonedDateTime.now()));
		return log;
	}

	@Override
	public boolean doItemSynchronization(SynchronizationContext context) {
		Assert.notNull(context, "Context is required.");

		String uid = context.getUid();
		IcConnectorObject icObject = context.getIcObject();
		IcSyncDeltaTypeEnum type = context.getType();
		AbstractSysSyncConfigDto config = context.getConfig();
		SysSystemDto system = context.getSystem();
		String entityType = context.getEntityType();
		AccAccountDto account = context.getAccount();
		SysSyncLogDto log = context.getLog();
		SysSyncItemLogDto logItem = context.getLogItem();
		List<SysSyncActionLogDto> actionLogs = context.getActionLogs();
		// Set default unknown action type
		context.addActionType(SynchronizationActionType.UNKNOWN);

		// If differential sync is disabled, then is every entity marks as different.
		context.setIsEntityDifferent(!config.isDifferentialSync());

		try {

			// Find system entity for uid
			SysSystemEntityDto systemEntity = findSystemEntity(uid, system, entityType);
			context.addSystemEntity(systemEntity);

			// Find acc account for uid or system entity
			if (account == null) {
				account = findAccount(context);
				if (systemEntity == null) {
					addToItemLog(logItem, "SystemEntity for this uid doesn't exist. We will create it.");
					systemEntity = createSystemEntity(uid, entityType, system);
				}

			}

			context.addSystemEntity(systemEntity).addAccount(account);

			if (IcSyncDeltaTypeEnum.CREATE == type || IcSyncDeltaTypeEnum.UPDATE == type
					|| IcSyncDeltaTypeEnum.CREATE_OR_UPDATE == type) {
				// Update or create
				Assert.notNull(icObject, "Connector object is required.");
				List<IcAttribute> icAttributes = icObject.getAttributes();

				if (account == null) {
					// Account doesn't exist in IDM
					systemEntity = removeSystemEntityWishIfPossible(systemEntity, false, context);
					context.addSystemEntity(systemEntity);

					resolveAccountNotExistSituation(context, systemEntity, icAttributes);

				} else {
					// Account exist in IdM (LINKED)
					SynchronizationLinkedActionType linkedAction = config.getLinkedAction();
					SynchronizationActionType action = linkedAction.getAction();
					context.addActionType(action);
					SynchronizationSituationType situation = SynchronizationSituationType.LINKED;

					// Since removing 'Wish' can affect existing identities and provisioning of
					// their accounts,
					// we will not do it if Ignore is set or if anything else than "update" is
					// configured
					if (linkedAction == SynchronizationLinkedActionType.UPDATE_ENTITY
							|| linkedAction == SynchronizationLinkedActionType.UPDATE_ACCOUNT) {
						systemEntity = removeSystemEntityWishIfPossible(systemEntity, true, context);
						context.addSystemEntity(systemEntity);
					}

					if (StringUtils.hasLength(config.getLinkedActionWfKey())) {
						// We will start specific workflow
						startWorkflow(config.getLinkedActionWfKey(), situation, action, null, context);

					} else {
						resolveLinkedSituation(config.getLinkedAction(), context);
					}
					addToItemLog(logItem, "Account exist in IdM (LINKED) - ended");

				}

			} else if (IcSyncDeltaTypeEnum.DELETE == type) {
				// Missing account situation, can be call from connector
				// (support delete account event) and from reconciliation

				context.addActionType(config.getMissingAccountAction().getAction());
				SynchronizationSituationType situation = SynchronizationSituationType.MISSING_ACCOUNT;
				if (StringUtils.hasLength(config.getMissingAccountActionWfKey())) {
					ReconciliationMissingAccountActionType missingAccountActionType = config.getMissingAccountAction();
					SynchronizationActionType action = missingAccountActionType.getAction();

					// We will start specific workflow
					startWorkflow(config.getMissingAccountActionWfKey(), situation, action, null, context);

				} else {
					// Resolve missing account situation for one item
					this.resolveMissingAccountSituation(config.getMissingAccountAction(), context);
				}
			} else if (context.isExportAction()) {
				// Export situation - create account to system
				this.resolveUnlinkedSituation(SynchronizationUnlinkedActionType.LINK_AND_UPDATE_ACCOUNT, context);
			}
			// Call hard hibernate session flush and clear
			if (getHibernateSession().isOpen()) {
				getHibernateSession().flush();
				getHibernateSession().clear();
			}
			return true;
		} catch (Exception e) {
			loggingException(context.getActionType(), log, logItem, actionLogs, uid, e);
			throw e;
		}
	}

	/**
	 * Resolve "Account doesn't exist in IDM" situation. Result can be UNLINKED or
	 * UNMATCHED situations.
	 *
	 * @param context
	 * @param systemEntity
	 * @param icAttributes
	 */
	protected void resolveAccountNotExistSituation(SynchronizationContext context, SysSystemEntityDto systemEntity,
			List<IcAttribute> icAttributes) {
		Assert.notNull(context, "Context is required.");

		AbstractSysSyncConfigDto config = context.getConfig();
		SysSyncItemLogDto logItem = context.getLogItem();

		addToItemLog(logItem, "Account doesn't exist in IdM");

		DTO entity = findByCorrelationAttribute(systemAttributeMappingService.get(config.getCorrelationAttribute()),
				icAttributes, context);
		if (entity != null) {
			// Account not exist but, entity by correlation was
			// found (UNLINKED)
			context.addActionType(config.getUnlinkedAction().getAction());
			SynchronizationSituationType situation = SynchronizationSituationType.UNLINKED;
			if (StringUtils.hasLength(config.getUnlinkedActionWfKey())) {
				SynchronizationUnlinkedActionType unlinkedActionType = config.getUnlinkedAction();
				SynchronizationActionType action = unlinkedActionType.getAction();

				// We will start specific workflow
				startWorkflow(config.getUnlinkedActionWfKey(), situation, action, entity, context);

			} else {
				context.addEntityId(entity.getId()).addSystemEntity(systemEntity);
				resolveUnlinkedSituation(config.getUnlinkedAction(), context);
			}
		} else {
			// Account not exist and entity too (UNMATCHED)
			context.addActionType(config.getMissingEntityAction().getAction());
			SynchronizationSituationType situation = SynchronizationSituationType.MISSING_ENTITY;
			if (StringUtils.hasLength(config.getMissingEntityActionWfKey())) {
				SynchronizationMissingEntityActionType missingEntityAction = config.getMissingEntityAction();
				SynchronizationActionType action = missingEntityAction.getAction();

				// We will start specific workflow
				startWorkflow(config.getMissingEntityActionWfKey(), situation, action, entity, context);

			} else {
				resolveMissingEntitySituation(config.getMissingEntityAction(), context);
			}
		}
	}

	/**
	 * Handle IC connector object
	 *
	 * @param itemContext
	 * @return
	 */
	protected boolean handleIcObject(SynchronizationContext itemContext) {
		Assert.notNull(itemContext, "Item context is required.");

		IcConnectorObject icObject = itemContext.getIcObject();
		AbstractSysSyncConfigDto config = itemContext.getConfig();
		SysSyncLogDto log = itemContext.getLog();
		AttributeMapping tokenAttribute = itemContext.getTokenAttribute();

		SysSyncItemLogDto itemLog = new SysSyncItemLogDto();
		// Synchronization by custom filter not supported DELETE
		// event
		IcSyncDeltaTypeEnum type = IcSyncDeltaTypeEnum.CREATE_OR_UPDATE;
		itemContext.addLogItem(itemLog).addType(type);

		// Find token by token attribute
		// For Reconciliation can be token attribute null
		Object tokenObj = null;
		if (tokenAttribute != null) {
			tokenObj = getValueByMappedAttribute(tokenAttribute, icObject.getAttributes(), itemContext);
		}

		// Token is saved in Sync as String, therefore we transform token (from
		// IcObject) to String too.
		String token = tokenObj != null ? tokenObj.toString() : null;

		// In custom filter mode, we don't have token. We find
		// token in object by tokenAttribute, but
		// order of returned (searched) objects is random. We
		// have to do !!STRING!! compare and save only
		// grater token to config and log.
		if (token != null && config.getToken() != null && token.compareTo(config.getToken()) <= -1) {
			token = config.getToken();
		}
		// Save token
		log.setToken(token);
		if (!config.isReconciliation()) {
			config.setToken(token);
		}

		boolean result = startItemSynchronization(itemContext);
		// Update (increased counter) and check state of sync (maybe was cancelled from
		// sync or LRT)
		return updateAndCheckState(result, log);
	}

	/**
	 * Main method for synchronization item. This method is call form "custom
	 * filter" and "connector sync" mode.
	 *
	 * @param itemContext
	 * @return
	 */
	protected boolean startItemSynchronization(SynchronizationContext itemContext) {
		String uid = itemContext.getUid();
		AbstractSysSyncConfigDto config = itemContext.getConfig();
		String entityType = itemContext.getEntityType();
		SysSyncLogDto log = itemContext.getLog();
		SysSyncItemLogDto itemLog = itemContext.getLogItem();
		boolean ignored = false;

		List<SysSyncActionLogDto> actionsLog = new ArrayList<>();
		try {

			SysSyncActionLogFilter actionFilter = new SysSyncActionLogFilter();
			actionFilter.setSynchronizationLogId(log.getId());
			actionsLog.addAll(syncActionLogService.find(actionFilter, null).getContent());
			itemContext.addActionLogs(actionsLog);

			// Default setting for log item
			itemLog.setIdentification(uid);
			itemLog.setDisplayName(uid);
			itemLog.setType(entityType);

			// Do synchronization for one item (produces item)
			// Start in new Transaction
			CoreEvent<SysSyncItemLogDto> event = new CoreEvent<>(SynchronizationEventType.START_ITEM,
					itemLog);
			event.getProperties().put(SynchronizationService.WRAPPER_SYNC_ITEM, itemContext);
			EventResult<SysSyncItemLogDto> lastResult = entityEventManager.process(event).getLastResult();
			boolean result = false;
			if (lastResult != null
					&& lastResult.getEvent().getProperties().containsKey(SynchronizationService.RESULT_SYNC_ITEM)) {
				result = (boolean) lastResult.getEvent().getProperties().get(SynchronizationService.RESULT_SYNC_ITEM);
			}
			ignored = shouldBeIgnored(lastResult);

			return result;
		} catch (Exception ex) {
			Pair<SysSyncActionLogDto, SysSyncItemLogDto> actionWithItemLog = getActionLogThatContains(actionsLog,
					itemLog);
			if (actionWithItemLog != null) {
				// We have to decrement count and log as error
				itemLog = actionWithItemLog.getRight();
				SysSyncActionLogDto actionLogDto = actionWithItemLog.getLeft();
				actionLogDto.setOperationCount(actionLogDto.getOperationCount() - 1);
				actionLogDto.getLogItems().remove(itemLog);
				loggingException(actionLogDto.getSyncAction(), log, itemLog, actionsLog, uid, ex);
			} else {
				loggingException(SynchronizationActionType.UNKNOWN, log, itemLog, actionsLog, uid, ex);
			}
			return true;
		} finally {
			synchronizationConfigService.save(config);
			if (!ignored) {
				boolean existingItemLog = existItemLogInActions(actionsLog, itemLog);
				actionsLog = saveActionLogs(actionsLog, log.getId());
				//
				if (!existingItemLog) {
					addToItemLog(itemLog, MessageFormat.format("Missing action log for UID [{0}]!", uid));
					initSyncActionLog(SynchronizationActionType.UNKNOWN, OperationResultType.ERROR, itemLog, log,
							actionsLog);
					syncItemLogService.save(itemLog);
				}
			}
		}
	}

	/**
	 * Start reconciliation. Is call after synchronization. Main purpose is find and
	 * resolve missing accounts
	 *
	 * @param entityType
	 * @param allAccountsSet
	 * @param config
	 * @param system
	 * @param log
	 * @param actionsLog
	 */
	protected void startReconciliation(String entityType, Set<String> allAccountsSet,
			AbstractSysSyncConfigDto config, SysSystemDto system, SysSyncLogDto log,
			List<SysSyncActionLogDto> actionsLog) {
		if (!log.isRunning()) {
			return;
		}
		AccAccountFilter accountFilter = new AccAccountFilter();
		accountFilter.setSystemId(system.getId());

		List<AccAccountDto> accounts = accountService.find(accountFilter, null).getContent();

		for (AccAccountDto account : accounts) {
			if (!log.isRunning()) {
				return;
			}
			String uid = account.getRealUid();
			if (!allAccountsSet.contains(uid)) {
				SysSyncItemLogDto itemLog = new SysSyncItemLogDto();
				boolean ignored = false;
				try {

					// Default setting for log item
					itemLog.setIdentification(uid);
					itemLog.setDisplayName(uid);
					itemLog.setType(entityType);

					// Do reconciliation for one item (produces event)
					// Start in new Transaction
					SynchronizationContext builder = new SynchronizationContext();
					builder.addUid(uid).addType(IcSyncDeltaTypeEnum.DELETE).addConfig(config).addSystem(system)
							.addEntityType(entityType).addAccount(account).addLog(log).addLogItem(itemLog)
							.addActionLogs(actionsLog);

					CoreEvent<SysSyncItemLogDto> event = new CoreEvent<>(
							SynchronizationEventType.START_ITEM, itemLog);
					event.getProperties().put(SynchronizationService.WRAPPER_SYNC_ITEM, builder);
					EventResult<SysSyncItemLogDto> lastResult = entityEventManager.process(event).getLastResult();
					boolean result = false;
					if (lastResult != null && lastResult.getEvent().getProperties()
							.containsKey(SynchronizationService.RESULT_SYNC_ITEM)) {
						result = (boolean) lastResult.getEvent().getProperties()
								.get(SynchronizationService.RESULT_SYNC_ITEM);
					}
					// The strategy was set as ignored
					ignored = shouldBeIgnored(lastResult);
					// Update (increased counter) and check state of sync (maybe was cancelled from
					// sync or LRT)
					updateAndCheckState(result, log);
				} catch (Exception ex) {
					String message = MessageFormat.format("Reconciliation - error for uid [{0}]", uid);
					log.addToLog(message);
					log.addToLog(Throwables.getStackTraceAsString(ex));
					LOG.error(message, ex);
				} finally {
					config = synchronizationConfigService.save(config);
					if (!ignored) {
						boolean existingItemLog = existItemLogInActions(actionsLog, itemLog);
						actionsLog = saveActionLogs(actionsLog, log.getId());
						//
						if (!existingItemLog) {
							addToItemLog(itemLog, MessageFormat.format("Missing action log for UID [{0}]!", uid));
							initSyncActionLog(SynchronizationActionType.UNKNOWN, OperationResultType.ERROR, itemLog, log,
									actionsLog);
							syncItemLogService.save(itemLog);
						}
					}
				}
			}
		}
	}

	/**
	 * Start export item (entity) to target resource
	 *
	 * @param context
	 * @param uidAttribute
	 * @param entity
	 */
	protected void exportEntity(SynchronizationContext context, SysSystemAttributeMappingDto uidAttribute,
			AbstractDto entity) {
		String entityType = context.getEntityType();
		AbstractSysSyncConfigDto config = context.getConfig();
		SysSyncLogDto log = context.getLog();
		List<SysSyncActionLogDto> actionsLog = context.getActionLogs();
		SysSystemDto system = context.getSystem();
		SysSyncItemLogDto itemLog = new SysSyncItemLogDto();
		boolean ignored = false;
		try {
			// Default setting for log item
			itemLog.setIdentification(entity.getId().toString());
			itemLog.setDisplayName(this.getDisplayNameForEntity(entity));
			itemLog.setType(entityType);
			itemLog.addToLog(
					MessageFormat.format("Start export for entity [{0}].", this.getDisplayNameForEntity(entity)));

			UUID accountId = this.getAccountByEntity(entity.getId(), system.getId());
			if (accountId != null) {
				initSyncActionLog(SynchronizationActionType.CREATE_ACCOUNT, OperationResultType.IGNORE, itemLog, log,
						actionsLog);
				itemLog.addToLog(MessageFormat.format(
						"For entity [{0}] AccAccount [{1}] was found. Export for this entity ends (only entity without AccAccount can be exported)!",
						this.getDisplayNameForEntity(entity), accountId));
				return;
			}

			String uid = systemAttributeMappingService.generateUid(entity, uidAttribute);

			// Do export for one item (produces event)
			// Start in new Transaction
			context.addUid(uid) //
					.addConfig(config) //
					.addSystem(system) //
					.addEntityType(entityType) //
					.addEntityId(entity.getId()).addLog(log) //
					.addLogItem(itemLog) //
					.addActionLogs(actionsLog) //
					.addExportAction(true);

			CoreEvent<SysSyncItemLogDto> event = new CoreEvent<>(SynchronizationEventType.START_ITEM,
					itemLog);
			event.getProperties().put(SynchronizationService.WRAPPER_SYNC_ITEM, context);
			EventResult<SysSyncItemLogDto> lastResult = entityEventManager.process(event).getLastResult();
			boolean result = false;
			if (lastResult != null
					&& lastResult.getEvent().getProperties().containsKey(SynchronizationService.RESULT_SYNC_ITEM)) {
				result = (boolean) lastResult.getEvent().getProperties().get(SynchronizationService.RESULT_SYNC_ITEM);
			}
			ignored = shouldBeIgnored(lastResult);

			// Update (increased counter) and check state of sync (maybe was cancelled from
			// sync or LRT)
			updateAndCheckState(result, log);

		} catch (Exception ex) {
			String message = MessageFormat.format("Export - error for entity [{0}]", entity.getId());
			log.addToLog(message);
			log.addToLog(Throwables.getStackTraceAsString(ex));
			LOG.error(message, ex);
		} finally {
			synchronizationConfigService.save(config);
			if (!ignored) {
				boolean existingItemLog = existItemLogInActions(actionsLog, itemLog);
				actionsLog = (List<SysSyncActionLogDto>) syncActionLogService.saveAll(actionsLog);
				//
				if (!existingItemLog) {
					addToItemLog(itemLog, MessageFormat.format("Missing action log for entity [{0}]!", entity.getId()));
					initSyncActionLog(SynchronizationActionType.UNKNOWN, OperationResultType.ERROR, itemLog, log,
							actionsLog);
					syncItemLogService.save(itemLog);
				}
			}
		}
	}

	/**
	 * Validate synchronization on: Exist, enable, running, has mapping, has
	 * connector key, has connector configuration
	 *
	 * @param synchronizationConfigId
	 * @return
	 */
	protected SynchronizationContext validate(UUID synchronizationConfigId) {

		SynchronizationContext context = new SynchronizationContext();
		// Set context as main context for whole sync.
		syncContext = context;
		AbstractSysSyncConfigDto config = synchronizationConfigService.get(synchronizationConfigId);
		//
		if (config == null) {
			throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_NOT_FOUND,
					ImmutableMap.of("id", synchronizationConfigId));
		}
		//
		// Synchronization must be enabled
		if (!config.isEnabled()) {
			throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_IS_NOT_ENABLED,
					ImmutableMap.of("name", config.getName()));
		}

		// Synchronization can not be running twice
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(config.getId());
		logFilter.setRunning(Boolean.TRUE);
		if (!synchronizationLogService.find(logFilter, null).getContent().isEmpty()) {
			throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_IS_RUNNING,
					ImmutableMap.of("name", config.getName()));
		}

		SysSystemMappingDto mapping = systemMappingService.get(config.getSystemMapping());
		Assert.notNull(mapping, "Mapping is required.");
		SysSchemaObjectClassDto schemaObjectClassDto = schemaObjectClassService.get(mapping.getObjectClass());
		SysSystemDto system = DtoUtils.getEmbedded(schemaObjectClassDto, SysSchemaObjectClass_.system);
		Assert.notNull(system, "System is required.");

		// System must be enabled
		if (system.isDisabled()) {
			throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_SYSTEM_IS_NOT_ENABLED,
					ImmutableMap.of("name", config.getName(), "system", system.getName()));
		}

		String entityType = mapping.getEntityType();
		SysSystemAttributeMappingFilter attributeHandlingFilter = new SysSystemAttributeMappingFilter();
		attributeHandlingFilter.setSystemMappingId(mapping.getId());
		List<SysSystemAttributeMappingDto> mappedAttributes = systemAttributeMappingService
				.find(attributeHandlingFilter, null).getContent();

		// Find connector identification persisted in system
		IcConnectorKey connectorKey = system.getConnectorKey();
		if (connectorKey == null) {
			throw new ProvisioningException(AccResultCode.CONNECTOR_KEY_FOR_SYSTEM_NOT_FOUND,
					ImmutableMap.of("system", system.getName()));
		}

		// Find connector configuration persisted in system
		IcConnectorConfiguration connectorConfig = systemService.getConnectorConfiguration(system);
		if (connectorConfig == null) {
			throw new ProvisioningException(AccResultCode.CONNECTOR_CONFIGURATION_FOR_SYSTEM_NOT_FOUND,
					ImmutableMap.of("system", system.getName()));
		}

		context.addConfig(config).addSystem(system).addEntityType(entityType).addMappedAttributes(mappedAttributes)
				.addConnectorConfig(connectorConfig);

		return context;
	}

	/**
	 * Compile filter for search from filter attribute and filter script
	 *
	 * @param config
	 * @return
	 */
	protected IcFilter resolveSynchronizationFilter(AbstractSysSyncConfigDto config) {
		// If is reconciliation, then is filter null
		if (config.isReconciliation()) {
			return null;
		}
		IcFilter filter = null;
		AttributeMapping filterAttributeMapping = null;
		if (config.getFilterAttribute() != null) {
			filterAttributeMapping = systemAttributeMappingService.get(config.getFilterAttribute());
		}
		String configToken = config.getToken();
		String filterScript = config.getCustomFilterScript();

		if (filterAttributeMapping == null && configToken == null && StringUtils.isEmpty(filterScript)) {
			return null;
		}

		if (filterAttributeMapping != null) {
			Object transformedValue = systemAttributeMappingService.transformValueToResource(null, configToken,
					filterAttributeMapping, config);

			if (transformedValue != null) {
				SysSchemaAttributeDto schemaAttributeDto = schemaAttributeService
						.get(filterAttributeMapping.getSchemaAttribute());
				IcAttributeImpl filterAttribute = new IcAttributeImpl(schemaAttributeDto.getName(), transformedValue);

				switch (config.getFilterOperation()) {
				case GREATER_THAN:
					filter = IcFilterBuilder.greaterThan(filterAttribute);
					break;

				case LESS_THAN:
					filter = IcFilterBuilder.lessThan(filterAttribute);
					break;

				case EQUAL_TO:
					filter = IcFilterBuilder.equalTo(filterAttribute);
					break;

				case CONTAINS:
					filter = IcFilterBuilder.contains(filterAttribute);
					break;

				case ENDS_WITH:
					filter = IcFilterBuilder.endsWith(filterAttribute);
					break;

				case STARTS_WITH:
					filter = IcFilterBuilder.startsWith(filterAttribute);
					break;
				}
			}
		}

		if (StringUtils.hasLength(filterScript)) {
			Map<String, Object> variables = new HashMap<>();
			variables.put("filter", filter);
			variables.put("token", configToken);

			IcFilterOperationType[] values = IcFilterOperationType.values();
			List<Class<?>> allowTypes = new ArrayList<>(values.length + 6);
			// Allow all IC filter operator
			for (IcFilterOperationType operation : values) {
				allowTypes.add(operation.getImplementation());
			}
			allowTypes.add(IcAndFilter.class);
			allowTypes.add(IcOrFilter.class);
			allowTypes.add(IcFilterBuilder.class);
			allowTypes.add(IcAttributeImpl.class);
			allowTypes.add(IcAttribute.class);
			allowTypes.add(IcNotFilter.class);
			Object filterObj = groovyScriptService.evaluate(filterScript, variables, allowTypes);
			if (filterObj != null && !(filterObj instanceof IcFilter)) {
				throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_FILTER_VALUE_WRONG_TYPE,
						ImmutableMap.of("type", filterObj.getClass().getName()));
			}
			filter = (IcFilter) filterObj;
		}
		return filter;
	}

	/**
	 * Method for resolve linked situation for one item.
	 *
	 * @param action
	 * @param context
	 */
	@Override
	public void resolveLinkedSituation(SynchronizationLinkedActionType action, SynchronizationContext context) {

		String entityType = context.getEntityType();
		SysSyncLogDto log = context.getLog();
		SysSyncItemLogDto logItem = context.getLogItem();
		List<SysSyncActionLogDto> actionLogs = context.getActionLogs();
		AccAccountDto account = context.getAccount();

		addToItemLog(logItem, MessageFormat.format("IdM Account [{0}] exists in IDM (LINKED)", account.getUid()));

		addToItemLog(logItem, MessageFormat.format("Linked action is [{0}]", action));

		switch (action) {
		case IGNORE:
			// Linked action is IGNORE. We will do nothing
			initSyncActionLog(SynchronizationActionType.LINKED, OperationResultType.IGNORE, logItem, log, actionLogs);
			return;
		case IGNORE_AND_DO_NOT_LOG:
			// Linked action is IGNORE_AND_DO_NOT_LOG. We will do nothing and LOG NOTHING.
			return;
		case UNLINK:
			// Linked action is UNLINK
			setMappingIdToAccount(context, account, true);

			updateAccountUid(context);
			doUnlink(account, false, log, logItem, actionLogs);

			initSyncActionLog(SynchronizationActionType.UNLINK, OperationResultType.SUCCESS, logItem, log, actionLogs);

			return;
		case UNLINK_AND_REMOVE_ROLE:
			// Linked action is UNLINK_AND_REMOVE_ROLE
			setMappingIdToAccount(context, account, true);

			updateAccountUid(context);
			doUnlink(account, true, log, logItem, actionLogs);

			initSyncActionLog(SynchronizationActionType.UNLINK, OperationResultType.SUCCESS, logItem, log, actionLogs);

			return;
		case UPDATE_ENTITY:
			// Linked action is UPDATE_ENTITY
			updateAccountUid(context);
			doUpdateEntity(context);
			if (context.getConfig().isDifferentialSync() && !context.isEntityDifferent()) {
				// If differential sync is enabled and this entity should be not updated, then
				// set item to ignore action.
				initSyncActionLog(SynchronizationActionType.UPDATE_ENTITY, OperationResultType.IGNORE, logItem, log,
						actionLogs);
				return;
			}
			setMappingIdToAccount(context, account, true);
			initSyncActionLog(SynchronizationActionType.UPDATE_ENTITY, OperationResultType.SUCCESS, logItem, log,
					actionLogs);
			return;
		case UPDATE_ACCOUNT:
			// Linked action is UPDATE_ACCOUNT
			setMappingIdToAccount(context, account, true);

			updateAccountUid(context);
			doUpdateAccount(account, entityType, log, logItem, actionLogs);
			initSyncActionLog(SynchronizationActionType.UPDATE_ACCOUNT, OperationResultType.SUCCESS, logItem, log,
					actionLogs);
		default:
			break;
		}
	}

	/**
	 * Method for resolve missing entity situation for one item.
	 *
	 * @param actionType
	 * @param context
	 */
	@Override
	public void resolveMissingEntitySituation(SynchronizationMissingEntityActionType actionType,
			SynchronizationContext context) {

		String uid = context.getUid();
		String entityType = context.getEntityType();
		SysSystemDto system = context.getSystem();
		SysSyncLogDto log = context.getLog();
		SysSyncItemLogDto logItem = context.getLogItem();
		List<SysSyncActionLogDto> actionLogs = context.getActionLogs();
		List<SysSystemAttributeMappingDto> mappedAttributes = context.getMappedAttributes();
		List<IcAttribute> icAttributes = context.getIcObject().getAttributes();

		addToItemLog(logItem, "Account and entity don't exist (missing entity).");
		addToItemLog(logItem, MessageFormat.format("Missing entity action is [{0}]", actionType));

		switch (actionType) {
		case IGNORE:
			// Ignore we will do nothing
			addToItemLog(logItem, "Missing entity action is IGNORE, we will do nothing.");
			initSyncActionLog(SynchronizationActionType.MISSING_ENTITY, OperationResultType.IGNORE, logItem, log,
					actionLogs);
			return;
		case IGNORE_AND_DO_NOT_LOG:
			// Missing account action is IGNORE_AND_DO_NOT_LOG. We will do nothing and LOG NOTHING.
			return;
		case CREATE_ENTITY:

			// We don't want compute different in create entity situation.
			context.setIsEntityDifferent(true);

			// Generate UID value from mapped attribute marked as UID (Unique
			// ID).
			// UID mapped attribute must exist and returned value must be not
			// null and must be String
			String attributeUid = this.generateUID(context);

			// Create idm account
			AccAccountDto account = doCreateIdmAccount(attributeUid, system);

			// Find and set SystemEntity (must exist)
			account.setSystemEntity(this.findSystemEntity(uid, system, entityType).getId());

			setMappingIdToAccount(context, account, false);

			// Apply specific settings - check, if the account and the entity can be created
			account = this.applySpecificSettingsBeforeLink(account, null, context);
			if (account == null) {
				return;
			}
			account = accountService.save(account);
			// Create new entity
			doCreateEntity(entityType, mappedAttributes, logItem, uid, icAttributes, account, context);
			initSyncActionLog(SynchronizationActionType.CREATE_ENTITY, OperationResultType.SUCCESS, logItem, log,
					actionLogs);
		}
	}

	/**
	 * Method for resolve unlinked situation for one item.
	 *
	 * @param action
	 * @param context
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void resolveUnlinkedSituation(SynchronizationUnlinkedActionType action, SynchronizationContext context) {

		UUID entityId = context.getEntityId();
		SysSyncLogDto log = context.getLog();
		SysSyncItemLogDto logItem = context.getLogItem();
		List<SysSyncActionLogDto> actionLogs = context.getActionLogs();

		addToItemLog(logItem, MessageFormat.format(
				"Account does not exist, but an entity [{0}] was found by correlation (entity unlinked).", entityId));
		addToItemLog(logItem, MessageFormat.format("Unlinked action is [{0}]", action));
		DTO entity = findById(entityId);

		switch (action) {
		case IGNORE:
			// Ignore we will do nothing
			initSyncActionLog(SynchronizationActionType.UNLINKED, OperationResultType.IGNORE, logItem, log, actionLogs);
			return;
		case IGNORE_AND_DO_NOT_LOG:
			// Linked action is IGNORE_AND_DO_NOT_LOG. We will do nothing and LOG NOTHING.
			return;
		case LINK:
			// Create IdM account
			doCreateLink(entity, false, context);
			initSyncActionLog(SynchronizationActionType.LINK, OperationResultType.SUCCESS, logItem, log, actionLogs);
			return;
		case LINK_AND_UPDATE_ACCOUNT:
			// Create IdM account
			doCreateLink(entity, true, context);
			initSyncActionLog(SynchronizationActionType.LINK_AND_UPDATE_ACCOUNT, OperationResultType.SUCCESS, logItem,
					log, actionLogs);
			return;
		case LINK_AND_UPDATE_ENTITY:
			// Could be update of entity skipped?
			context.addSkipEntityUpdate(skipEntityUpdate(entity, context));
			// Update entity without provisioning
			context.addSkipProvisioning(true);
			doUpdateEntity(context);
			context.addSkipProvisioning(false);
			// Get updated entity from context
			if (context.getEntityDto() != null) {
				entity = (DTO) context.getEntityDto();
			}
			// Create IdM account
			doCreateLink(entity, true, context);
			initSyncActionLog(SynchronizationActionType.LINK_AND_UPDATE_ENTITY, OperationResultType.SUCCESS, logItem,
					log, actionLogs);

		}
	}

	/**
	 * Method for resolve missing account situation for one item.
	 *
	 * @param action
	 * @param context
	 */
	@Override
	public void resolveMissingAccountSituation(ReconciliationMissingAccountActionType action,
			SynchronizationContext context) {

		String entityType = context.getEntityType();
		SysSyncLogDto log = context.getLog();
		SysSyncItemLogDto logItem = context.getLogItem();
		List<SysSyncActionLogDto> actionLogs = context.getActionLogs();
		AccAccountDto account = context.getAccount();

		addToItemLog(logItem,
				"Account doesn't exist on target system, but account in IdM was found (missing account).");
		addToItemLog(logItem, MessageFormat.format("Missing account action is [{0}]", action));
		switch (action) {
		case IGNORE:
			// Ignore we will do nothing
			initSyncActionLog(SynchronizationActionType.MISSING_ACCOUNT, OperationResultType.IGNORE, logItem, log,
					actionLogs);
			return;
		case IGNORE_AND_DO_NOT_LOG:
			// Missing account action is IGNORE_AND_DO_NOT_LOG. We will do nothing and LOG NOTHING.
			return;
		case CREATE_ACCOUNT:
			doUpdateAccount(account, entityType, log, logItem, actionLogs);
			initSyncActionLog(SynchronizationActionType.CREATE_ACCOUNT, OperationResultType.SUCCESS, logItem, log,
					actionLogs);
			return;
		case DELETE_ENTITY:
			doDeleteEntity(account, log, logItem, actionLogs);
			initSyncActionLog(SynchronizationActionType.DELETE_ENTITY, OperationResultType.SUCCESS, logItem, log,
					actionLogs);
			return;
		case UNLINK:
			doUnlink(account, false, log, logItem, actionLogs);
			initSyncActionLog(SynchronizationActionType.UNLINK, OperationResultType.SUCCESS, logItem, log, actionLogs);
			return;
		case UNLINK_AND_REMOVE_ROLE:
			doUnlink(account, true, log, logItem, actionLogs);
			initSyncActionLog(SynchronizationActionType.UNLINK_AND_REMOVE_ROLE, OperationResultType.SUCCESS, logItem,
					log, actionLogs);

		}
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
	protected void doUpdateAccount(AccAccountDto account, String entityType, SysSyncLogDto log,
			SysSyncItemLogDto logItem, List<SysSyncActionLogDto> actionLogs) {
		UUID entityId = getEntityByAccount(account.getId());
		DTO entity = null;
		if (entityId != null) {
			entity = getService().get(entityId);
		}
		if (entity == null) {
			addToItemLog(logItem, MessageFormat.format("Warning! - Entity for account [{0}] was not found!", account.getUid()));
			initSyncActionLog(SynchronizationActionType.UPDATE_ENTITY, OperationResultType.WARNING, logItem, log,
					actionLogs);
			return;
		}
		if (this.isProvisioningImplemented(entityType, logItem)) {
			// Call provisioning for this entity
			callProvisioningForEntity(entity, entityType, logItem);
		}
	}

	/**
	 * Check if is supported provisioning for given entity type.
	 *
	 * @param entityType
	 * @param logItem
	 * @return
	 */
	protected boolean isProvisioningImplemented(String entityType, SysSyncItemLogDto logItem) {
		SystemEntityTypeRegistrable systemEntityType = systemEntityManager.getSystemEntityByCode(entityType);
		return systemEntityType != null && systemEntityType.isSupportsProvisioning();

	}

	/**
	 * Call provisioning for given account
	 *
	 * @param dto
	 * @param entityType
	 * @param logItem
	 */
	protected void callProvisioningForEntity(DTO dto, String entityType, SysSyncItemLogDto logItem) {
		throw new UnsupportedOperationException("Call provisioning method is not implemented!");
	}

	/**
	 * Create new instance of ACC account
	 *
	 * @param uid
	 * @param system
	 * @return
	 */
	protected AccAccountDto doCreateIdmAccount(String uid, SysSystemDto system) {
		AccAccountDto account = new AccAccountDto();
		account.setSystem(system.getId());
		account.setUid(uid);
		account.setEntityType(getEntityType());
		return account;
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
	 * @param context
	 */
	protected void doCreateEntity(String entityType, List<SysSystemAttributeMappingDto> mappedAttributes,
			SysSyncItemLogDto logItem, String uid, List<IcAttribute> icAttributes, AccAccountDto account,
			SynchronizationContext context) {
		// We will create new entity
		addToItemLog(logItem, "Missing entity action is CREATE_ENTITY, we will create new entity.");
		DTO entity = this.createEntityDto();
		// Fill entity by mapped attribute
		entity = fillEntity(mappedAttributes, uid, icAttributes, entity, true, context);
		// Fill extended attributes to the entity. EAV attributes will be saved within entity.
		if (entity instanceof FormableDto) {
			FormableDto formableDto = (FormableDto) entity;
			formableDto.getEavs().clear();
			IdmFormInstanceDto formInstanceDto = fillExtendedAttributes(mappedAttributes, uid, icAttributes, entity, true, context);
			formableDto.getEavs().add(formInstanceDto);
		}
		// Create new entity
		entity = this.save(entity, true, context);

		EntityAccountDto roleAccount = createEntityAccount(account, entity, context);
		this.getEntityAccountService().save(roleAccount);

		// Entity created
		addToItemLog(logItem, MessageFormat.format("Entity with id [{0}] was created", entity.getId()));
		if (logItem != null) {
			logItem.setDisplayName(this.getDisplayNameForEntity(entity));
		}

		if (this.isProvisioningImplemented(entityType, logItem)) {
			// Call provisioning for this entity
			callProvisioningForEntity(entity, entityType, logItem);
		}
	}

	/**
	 * Create instance of relation between account and sync entity. Create and fill
	 * relation instance. Do not persist she.
	 *
	 * @param account
	 * @param entity
	 * @param context
	 * @return
	 */
	protected EntityAccountDto createEntityAccount(AccAccountDto account, DTO entity, SynchronizationContext context) {
		// Create new entity account relation
		EntityAccountDto entityAccount = this.createEntityAccountDto();
		entityAccount.setAccount(account.getId());
		entityAccount.setEntity(entity.getId());
		entityAccount.setOwnership(true);
		return entityAccount;
	}

	/**
	 * Fill data from IC attributes to entity (EAV and confidential storage too)
	 *
	 * @param context
	 */
	protected void doUpdateEntity(SynchronizationContext context) {

		String uid = context.getUid();
		SysSyncLogDto log = context.getLog();
		SysSyncItemLogDto logItem = context.getLogItem();

		if (context.isSkipEntityUpdate()) {
			addToItemLog(logItem, MessageFormat.format("Update of entity for account with uid [{0}] is skipped", uid));
			return;
		}

		List<SysSyncActionLogDto> actionLogs = context.getActionLogs();
		List<SysSystemAttributeMappingDto> mappedAttributes = context.getMappedAttributes();
		AccAccountDto account = context.getAccount();
		List<IcAttribute> icAttributes = context.getIcObject().getAttributes();

		// Find entity ID, first try entity ID in the context then load by account
		UUID entityId = context.getEntityId();
		if (entityId == null && account != null) {
			entityId = getEntityByAccount(account.getId());
		}
		DTO entity = null;
		if (entityId != null) {
			entity = this.getService().get(entityId);
		}
		if (entity != null) {
			// Fill entity
			entity = fillEntity(mappedAttributes, uid, icAttributes, entity, false, context);
			// Fill extended attributes to the entity. EAV attributes will be saved within entity.
			if (entity instanceof FormableDto) {
				FormableDto formableDto = (FormableDto) entity;
				formableDto.getEavs().clear();
				IdmFormInstanceDto formInstanceDto = fillExtendedAttributes(mappedAttributes, uid, icAttributes, entity, false, context);
				formableDto.getEavs().add(formInstanceDto);
			}
			// Update entity
			if (context.isEntityDifferent()) {
				entity = this.save(entity, true, context);
			}

			// Entity updated
			addToItemLog(logItem, MessageFormat.format("Entity with id [{0}] was updated", entity.getId()));
			if (logItem != null) {
				logItem.setDisplayName(this.getDisplayNameForEntity(entity));
			}

			String entityType = context.getEntityType();
			if (context.isEntityDifferent() && this.isProvisioningImplemented(entityType, logItem)
					&& !context.isSkipProvisioning()) {
				// Call provisioning for this entity
				callProvisioningForEntity(entity, entityType, logItem);
			}
			// Add updated entity to the context
			context.addEntityDto(entity);
		} else {
			addToItemLog(logItem, "Warning! - Entity-account relation (with ownership = true) was not found!");
			initSyncActionLog(SynchronizationActionType.UPDATE_ENTITY, OperationResultType.WARNING, logItem, log,
					actionLogs);
		}
	}

	/**
	 * Add message to logItem. Add timestamp.
	 *
	 * @param logItem
	 * @param text
	 */
	protected void addToItemLog(Loggable logItem, String text) {
		StringBuilder sb = new StringBuilder();
		ZonedDateTime now = ZonedDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter
				.ofPattern(ConfigurationService.DEFAULT_APP_DATETIME_WITH_SECONDS_FORMAT);
		sb.append(formatter.format(now));
		sb.append(": ");
		sb.append(text);
		text = sb.toString();
		if (logItem == null) {
			// Log item is null, we will log to console only.
			// We probably call this outside standard sync cycle (workflow
			// maybe)
			LOG.info(text);
		} else {
			logItem.addToLog(text);
			LOG.info(text);
		}
	}

	/**
	 * Operation remove entity account relations and linked roles
	 *
	 * @param account
	 * @param removeRoleRole
	 * @param log
	 * @param logItem
	 * @param actionLogs
	 */
	protected void doUnlink(AccAccountDto account, boolean removeRoleRole, SysSyncLogDto log, SysSyncItemLogDto logItem,
			List<SysSyncActionLogDto> actionLogs) {

		EntityAccountFilter entityAccountFilter = this.createEntityAccountFilter();
		entityAccountFilter.setAccountId(account.getId());
		List<EntityAccountDto> entityAccounts = this.getEntityAccountService().find(entityAccountFilter, null)
				.getContent();
		if (entityAccounts.isEmpty()) {
			addToItemLog(logItem, "Warning! - Entity account relation was not found!");
			initSyncActionLog(SynchronizationActionType.UPDATE_ENTITY, OperationResultType.WARNING, logItem, log,
					actionLogs);
			return;
		}
		addToItemLog(logItem, MessageFormat.format("Entity-account relations to delete [{0}]", entityAccounts));

		entityAccounts.stream().forEach(entityAccount -> {
			// We will remove role account, but without delete connected
			// account
			this.getEntityAccountService().delete(entityAccount, false);
			addToItemLog(logItem,
					MessageFormat.format(
							"Entity-account relation deleted (without call delete provisioning) (entity: [{0}], id: [{1}])",
							entityAccount.getEntity(), entityAccount.getId()));

		});
	}

	/**
	 * Log exception to SyncLog and SyncItemLog, do init syncActionLog
	 *
	 * @param synchronizationActionType
	 * @param log
	 * @param logItem
	 * @param actionLogs
	 * @param uid
	 * @param e
	 */
	private void loggingException(SynchronizationActionType synchronizationActionType, SysSyncLogDto log,
			SysSyncItemLogDto logItem, List<SysSyncActionLogDto> actionLogs, String uid, Exception e) {
		String message = MessageFormat.format("Synchronization - exception during [{0}] for UID [{1}]",
				synchronizationActionType, uid);
		log.setContainsError(true);
		logItem.setMessage(message);
		// prefer IdM exception on the top
		Throwable ex = e;
		if (!(e instanceof CoreException)) {
			Throwable idmEx = ExceptionUtils.resolveException(e);
			if (idmEx != e) {
				addToItemLog(logItem, Throwables.getStackTraceAsString(idmEx));
				ex = idmEx;
			} else {
				addToItemLog(logItem, Throwables.getStackTraceAsString(e));
			}
		} else {
			addToItemLog(logItem, Throwables.getStackTraceAsString(e));
		}
		initSyncActionLog(synchronizationActionType, OperationResultType.ERROR, logItem, log, actionLogs);
		LOG.error(message, ex);
	}

	/**
	 * Find entity by correlation attribute
	 *
	 * @param attribute
	 * @param icAttributes
	 * @param context
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected DTO findByCorrelationAttribute(AttributeMapping attribute, List<IcAttribute> icAttributes,
			SynchronizationContext context) {
		Assert.notNull(attribute, "Attribute is required.");
		Assert.notNull(icAttributes, "Connector attribues are required.");

		Object value = getValueByMappedAttribute(attribute, icAttributes, context);
		if (value == null) {
			return null;
		}
		if (attribute.isEntityAttribute()) {
			return findByAttribute(attribute.getIdmPropertyName(), value.toString(), context);
		} else if (attribute.isExtendedAttribute()) {
			try {
				Serializable serializableValue = Serializable.class.cast(value);
				String entityType = context.getEntityType();
				SystemEntityTypeRegistrable systemEntityType = systemEntityManager.getSystemEntityByCode(entityType);
				Assert.notNull(systemEntityType, "Entity type is required!");

				List<? extends BaseDto> entities = formService.findOwners(systemEntityType.getEntityType(),
						attribute.getIdmPropertyName(), serializableValue, null).getContent();
				if (CollectionUtils.isEmpty(entities)) {
					return null;
				}
				if (entities.size() > 1) {
					throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_CORRELATION_TO_MANY_RESULTS,
							ImmutableMap.of("correlationAttribute", attribute.getName(), "value", value));
				}
				if (entities.size() == 1) {
					return (DTO) entities.get(0);
				}
			} catch (ClassCastException e) {
				throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_CORRELATION_BAD_VALUE,
						ImmutableMap.of("value", value), e);
			}
		}
		return null;
	}

	/**
	 * Return specific correlation filter
	 *
	 * @param context
	 * @return
	 */
	protected abstract CorrelationFilter getEntityFilter(SynchronizationContext context);

	/**
	 * Find all records
	 *
	 * @return
	 */
	protected List<DTO> findAll() {
		return getService().find((Pageable) null).getContent();
	}

	/**
	 * Find by identifier
	 *
	 * @param entityId
	 * @return
	 */
	protected DTO findById(UUID entityId) {
		return getService().get(entityId);
	}

	/**
	 * Find dto by idm attribute
	 *
	 * @param idmAttributeName
	 * @param value
	 * @param context
	 * @return
	 */
	protected DTO findByAttribute(String idmAttributeName, String value, SynchronizationContext context) {
		CorrelationFilter filter = this.getEntityFilter(context);
		filter.setProperty(idmAttributeName);
		filter.setValue(value);

		@SuppressWarnings("unchecked")
		ReadWriteDtoService<DTO, BaseFilter> service = (ReadWriteDtoService<DTO, BaseFilter>) getService();

		List<DTO> entities = service.find((BaseFilter) filter, (Pageable) null).getContent();

		if (CollectionUtils.isEmpty(entities)) {
			return null;
		}
		if (entities.size() > 1) {
			throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_CORRELATION_TO_MANY_RESULTS,
					ImmutableMap.of("correlationAttribute", idmAttributeName, "value", value));
		}
		if (entities.size() == 1) {
			return entities.get(0);
		}
		return null;
	}

	protected abstract EntityAccountFilter createEntityAccountFilter();

	protected abstract EntityAccountDto createEntityAccountDto();

	protected abstract DTO createEntityDto();

	protected abstract EntityAccountService<EntityAccountDto, EntityAccountFilter> getEntityAccountService();

	protected abstract ReadWriteDtoService<DTO, ?> getService();

	/**
	 * Fill entity with attributes from IC module (by mapped attributes).
	 *
	 * @param mappedAttributes
	 * @param uid
	 * @param icAttributes
	 * @param dto
	 * @param create (is create or update entity situation)
	 * @param context
	 * @return
	 */
	protected DTO fillEntity(List<SysSystemAttributeMappingDto> mappedAttributes, String uid,
			List<IcAttribute> icAttributes, DTO dto, boolean create, SynchronizationContext context) {
		mappedAttributes.stream().filter(attribute -> {
			// Skip disabled attributes
			// Skip extended attributes (we need update/ create entity first)
			// Skip confidential attributes (we need update/ create entity
			// first)
			boolean fastResult = !attribute.isDisabledAttribute() && attribute.isEntityAttribute()
					&& !attribute.isConfidentialAttribute();
			if (!fastResult) {
				return false;
			}
			// Can be value set by attribute strategy?
			return this.canSetValue(uid, attribute, dto, create);

		}).forEach(attribute -> {
			String attributeProperty = attribute.getIdmPropertyName();
			// Set transformed value from target system to entity
			Object transformedValue = getValueByMappedAttribute(attribute, icAttributes, context);
			setEntityValue(uid, dto, context, attribute, attributeProperty, transformedValue);
		});
		return dto;
	}

	/**
	 * Set attribute's value to the entity with check difference between old and new
	 * value.
	 *
	 * @param uid
	 * @param dto
	 * @param context
	 * @param attribute
	 * @param attributeProperty
	 * @param transformedValue
	 */
	protected void setEntityValue(String uid, DTO dto, SynchronizationContext context,
			SysSystemAttributeMappingDto attribute, String attributeProperty, Object transformedValue) {

		try {
			if (context.isEntityDifferent()) {
				EntityUtils.setEntityValue(dto, attributeProperty, transformedValue);
			} else {
				Object entityValue = EntityUtils.getEntityValue(dto, attributeProperty);
				SysSchemaAttributeDto schemaAttributeDto = DtoUtils.getEmbedded(attribute,
						SysSystemAttributeMapping_.schemaAttribute.getName(), SysSchemaAttributeDto.class);
				Assert.notNull(schemaAttributeDto, "Schema attribute cannot be null!");
				if (!provisioningService.isAttributeValueEquals(entityValue, transformedValue, schemaAttributeDto)) {
					context.setIsEntityDifferent(true);
					addToItemLog(context.getLogItem(),
							MessageFormat.format(
									"Value of entity attribute [{0}] was changed. First change was detected -> entity in IdM will be updated.",
									attributeProperty));
					EntityUtils.setEntityValue(dto, attributeProperty, transformedValue);
				}
			}
		} catch (IntrospectionException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| ProvisioningException e) {
			throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_IDM_FIELD_NOT_SET,
					ImmutableMap.of("property", attributeProperty, "uid", uid), e);
		}
	}

	/**
	 * Fill extended attribute for given entity. Entity must be persisted first.
	 *
	 * @param mappedAttributes
	 * @param uid
	 * @param icAttributes
	 * @param dto
	 * @param create (is create or update entity situation)
	 * @param context
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected IdmFormInstanceDto fillExtendedAttributes(List<SysSystemAttributeMappingDto> mappedAttributes, String uid,
														List<IcAttribute> icAttributes, DTO dto, boolean create, SynchronizationContext context) {

		IdmFormInstanceDto formInstanceDto = new IdmFormInstanceDto();
		SystemEntityTypeRegistrable systemEntityType = systemEntityManager.getSystemEntityByCode(context.getEntityType());
		IdmFormDefinitionDto formDefinitionDto = formService.getDefinition(systemEntityType.getExtendedAttributeOwnerType());
		formInstanceDto.setFormDefinition(formDefinitionDto);

		mappedAttributes.stream().filter(attribute -> {
			// Skip disabled attributes
			// Only for extended attributes
			boolean fastResult = !attribute.isDisabledAttribute() && attribute.isExtendedAttribute();
			if (!fastResult) {
				return false;
			}
			// Can be value set by attribute strategy?
			return this.canSetValue(uid, attribute, dto, create);

		}).forEach(attribute -> {
			String attributeProperty = attribute.getIdmPropertyName();
			Object transformedValue = getValueByMappedAttribute(attribute, icAttributes, context);
			//
			// Save to extended attribute
			if (!formService.isFormable(dto.getClass())) {
				String message = MessageFormat.format("Entity [{0}] is not instance of formable entity!", dto.getId());
				throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_ERROR_DURING_SYNC_ITEM,
						ImmutableMap.of("uid", uid, "message", message));
			}
			String entityType = context.getEntityType();
			Assert.notNull(entityType, "Entity type is requierd!");
			IdmFormAttributeDto defAttribute = formService.getDefinition(systemEntityType.getExtendedAttributeOwnerType())
					.getMappedAttributeByCode(attributeProperty);
			if (defAttribute == null) {
				// eav definition could be changed
				String message = MessageFormat.format("Form attribute definition [{0}] was not found!",
						attributeProperty);
				throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_ERROR_DURING_SYNC_ITEM,
						ImmutableMap.of("uid", uid, "message", message));
			}

			List<Serializable> values;
			if (transformedValue instanceof List<?>) {
				// TODO: Convert List GuardedStrings to Strings!
				((List<?>) transformedValue).stream().forEach(value -> {
					if (value != null && !(value instanceof Serializable)) {
						String message = MessageFormat.format(
								"Value [{0}] is not serializable for the attribute [{1}] and UID [{2}]!", value,
								attribute, uid);
						throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_ERROR_DURING_SYNC_ITEM,
								ImmutableMap.of("uid", uid, "message", message));
					}
				});
				values = ((List<Serializable>) transformedValue);
			} else {
				// Convert GuardedString to string
				values = Lists.newArrayList(
						transformedValue instanceof GuardedString ? ((GuardedString) transformedValue).asString()
								: (Serializable) transformedValue);
			}

			if (!context.isEntityDifferent()) {
				List<IdmFormValueDto> previousValues = formService.getValues(dto.getId(),
						systemEntityType.getEntityType(), defAttribute);
				if (defAttribute.isConfidential() && previousValues != null) {
					previousValues.forEach(formValue -> {
						formValue.setValue(formService.getConfidentialPersistentValue(formValue));
					});
				}

				List<IdmFormValueDto> newValues = values.stream().filter(value -> value != null).map(value -> {
					IdmFormValueDto formValue = new IdmFormValueDto(defAttribute);
					formValue.setValue(value);
					return formValue;
				}).collect(Collectors.toList());

				boolean isEavValuesSame = this.isEavValuesSame(newValues, previousValues);
				if (!isEavValuesSame) {
					context.setIsEntityDifferent(true);
					addToItemLog(context.getLogItem(),
							MessageFormat.format(
									"Value of EAV attribute [{0}] was changed. First change was detected -> entity in IdM will be updated.",
									attributeProperty));
				}
			}

			if (context.isEntityDifferent()) {
				if (values.isEmpty()) {
					// empty list synchronized, remove all values
					IdmFormValueDto formValue = new IdmFormValueDto(defAttribute);
					formInstanceDto.getValues().addAll(Lists.newArrayList(formValue));
				} else {
					List<IdmFormValueDto> formValues = values.stream().map(value -> {
						IdmFormValueDto formValue = new IdmFormValueDto(defAttribute);
						formValue.setValue(value);
						return formValue;
					}).collect(Collectors.toList());
					formInstanceDto.getValues().addAll(formValues);
				}
			}
		});
		return formInstanceDto;
	}

	/**
	 * @deprecated since 10.1.0
	 *
	 * Update confidential attribute for given entity. Entity must be persisted
	 * first.
	 *
	 * @param mappedAttributes
	 * @param uid
	 * @param icAttributes
	 * @param dto
	 * @param create (is create or update entity situation)
	 * @param context
	 * @return
	 */
	@Deprecated
	protected DTO updateConfidentialAttributes(List<SysSystemAttributeMappingDto> mappedAttributes, String uid,
			List<IcAttribute> icAttributes, DTO dto, boolean create, SynchronizationContext context) {
		mappedAttributes.stream().filter(attribute -> {
			// Skip disabled attributes
			// Only for confidential attribute
			boolean fastResult = !attribute.isDisabledAttribute() && attribute.isConfidentialAttribute();
			if (!fastResult) {
				return false;
			}
			// Can be value set by attribute strategy?
			return this.canSetValue(uid, attribute, dto, create);

		}).forEach(attribute -> {
			String attributeProperty = attribute.getIdmPropertyName();
			Object transformedValue = getValueByMappedAttribute(attribute, icAttributes, context);
			// If is attribute confidential, then we will set
			// value to
			// secured storage
			if (!(transformedValue == null || transformedValue instanceof GuardedString)) {
				throw new ProvisioningException(AccResultCode.CONFIDENTIAL_VALUE_IS_NOT_GUARDED_STRING,
						ImmutableMap.of("property", attributeProperty, "class", transformedValue.getClass().getName()));
			}

			confidentialStorage.saveGuardedString(dto.getId(), dto.getClass(), attributeProperty,
					(GuardedString) transformedValue);
		});
		return dto;
	}

	/**
	 * Return true if can be value set to this entity for this mapped attribute.
	 *
	 * @param uid
	 * @param attribute
	 * @param dto
	 * @param create (create or update entity situation)
	 * @return
	 */
	protected boolean canSetValue(String uid, SysSystemAttributeMappingDto attribute, DTO dto, boolean create) {
		Assert.notNull(attribute, "Attribute is required.");
		//
		switch (attribute.getStrategyType()) {
			case CREATE: {
				return create;
			}
			case SET: {
				return true;
			}
			case WRITE_IF_NULL: {
				// For creation isn't possible get value - owner doesn't exist.
				if (create) {
					return true;
				}
				Object value = systemAttributeMappingService.getAttributeValue(uid, dto, attribute);
				return value == null;
			}
			default: {
				return false;
			}
		}
	}

	protected Object getValueByMappedAttribute(AttributeMapping attribute, List<IcAttribute> icAttributes,
			SynchronizationContext context) {
		if (attribute == null || icAttributes == null) {
			return null;
		}
		if (attribute instanceof SysSystemAttributeMappingDto && context != null && context.getConfig() != null) {
			SysSystemAttributeMappingDto attributeMappingDto = (SysSystemAttributeMappingDto) attribute;
			// Set ID of this sync to attribute instance. A configuration of sync can be use in script.
			attributeMappingDto.setSyncConfigId(context.getConfig().getId());
		}

		// If is attribute marked as not "cached", then none cache is using
		if (!attribute.isCached()) {
			return systemAttributeMappingService.getValueByMappedAttribute(attribute, icAttributes);
		}

		AttributeValueWrapperDto key = new AttributeValueWrapperDto(attribute, icAttributes);
		ValueWrapper value = this.getCachedValue(key);
		if (value != null) {
			return value.get();
		}
		Object valueByMappedAttribute = systemAttributeMappingService.getValueByMappedAttribute(attribute, icAttributes);
		this.setCachedValue(key, valueByMappedAttribute);
		//
		return valueByMappedAttribute;

	}

	protected ValueWrapper getCachedValue(AttributeValueWrapperDto key) {
		return idmCacheManager.getValue(CACHE_NAME, key);
	}

	protected void setCachedValue(AttributeValueWrapperDto key, Object value) {
		idmCacheManager.cacheValue(CACHE_NAME, key, value);
	}

	/**
	 * Are input EAV values same
	 *
	 * @param newValues
	 * @param previousValues
	 * @return
	 */
	private boolean isEavValuesSame(List<IdmFormValueDto> newValues, List<IdmFormValueDto> previousValues) {
		if (newValues == null && previousValues == null) {
			return true;
		}

		if (newValues == null && previousValues != null) {
			newValues = Lists.newArrayList();
		}

		if (previousValues == null && newValues != null) {
			previousValues = Lists.newArrayList();
		}

		if (newValues != null && newValues.isEmpty() && previousValues != null && previousValues.isEmpty()) {
			return true;
		}

		if (newValues != null && previousValues != null && newValues.size() != previousValues.size()) {
			return false;
		}

		final List<IdmFormValueDto> previousValuesFinal = previousValues;
		if (newValues == null) {
			return false;
		}
		if (previousValuesFinal == null) {
			return false;
		}
		long countOfChangedValues = newValues.stream()
				.filter(value -> {
					return previousValuesFinal.stream()
							.filter(previousValue -> {
								return previousValue.isEquals(value);
							})
							.count() == 0;
				})
				.count();

		return countOfChangedValues == 0;
	}

	private AccAccountDto findAccount(SynchronizationContext context) {

		String uid = context.getUid();
		SysSystemDto system = context.getSystem();
		SysSyncItemLogDto logItem = context.getLogItem();
		SysSystemEntityDto systemEntity = context.getSystemEntity();

		AccAccountFilter accountFilter = new AccAccountFilter();
		accountFilter.setSystemId(system.getId());
		List<AccAccountDto> accounts = null;
		if (systemEntity != null) {
			// System entity for this uid was found. We will find account
			// for this system entity.
			addToItemLog(logItem, MessageFormat.format(
					"System entity [{1}] for this UID [{0}] was found. We try to find account for this system entity",
					uid, systemEntity.getId()));
			accountFilter.setSystemEntityId(systemEntity.getId());
			accounts = accountService.find(accountFilter, null).getContent();
		}
		if (CollectionUtils.isEmpty(accounts)) {
			// System entity was not found. We will find account by generated UID directly.

			// Generate UID value from mapped attribute marked as UID (Unique ID).
			// UID mapped attribute must exist and returned value must be not null
			// and must be String
			String attributeUid = this.generateUID(context);
			addToItemLog(logItem, MessageFormat.format(
					"Account was not found. We try to find account for UID [{0}] (generated from the mapped attribute marked as Identifier)",
					attributeUid));

			accountFilter.setUid(attributeUid);
			accountFilter.setSystemEntityId(null);
			accounts = accountService.find(accountFilter, null).getContent();
		}
		if (accounts != null && accounts.size() > 1) {
			throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_TO_MANY_ACC_ACCOUNT, uid);
		}
		if (accounts != null && !accounts.isEmpty()) {
			return accounts.get(0);
		}
		return null;
	}

	/**
	 * Generate UID value from mapped attribute marked as UID (Unique ID). UID
	 * mapped attribute must exist and returned value must be not null and must be
	 * String.
	 *
	 * If is generated UID in the context, then will used.
	 *
	 * @param context
	 * @return
	 */
	private String generateUID(SynchronizationContext context) {
		Assert.notNull(context, "Context is required!");

		SysSystemDto system = context.getSystem();
		List<IcAttribute> icAttributes = context.getIcObject().getAttributes();
		List<SysSystemAttributeMappingDto> mappedAttributes = context.getMappedAttributes();
		String generatedUid = context.getGeneratedUid();
		if (generatedUid == null) {
			context.addGeneratedUid(
					systemAttributeMappingService.getUidValueFromResource(icAttributes, mappedAttributes, system));
		}
		return context.getGeneratedUid();

	}

	private SysSystemEntityDto createSystemEntity(String uid, String entityType, SysSystemDto system) {
		SysSystemEntityDto systemEntityNew = new SysSystemEntityDto();
		systemEntityNew.setUid(uid);
		systemEntityNew.setEntityType(entityType);
		systemEntityNew.setSystem(system.getId());
		systemEntityNew.setWish(false);
		return systemEntityService.save(systemEntityNew);
	}

	private SysSystemEntityDto findSystemEntity(String uid, SysSystemDto system, String entityType) {
		SysSystemEntityFilter systemEntityFilter = new SysSystemEntityFilter();
		systemEntityFilter.setEntityType(entityType);
		systemEntityFilter.setSystemId(system.getId());
		systemEntityFilter.setUid(uid);
		List<SysSystemEntityDto> systemEntities = systemEntityService.find(systemEntityFilter, null).getContent();
		SysSystemEntityDto systemEntity = null;
		if (systemEntities.size() == 1) {
			systemEntity = systemEntities.get(0);
		} else if (systemEntities.size() > 1) {
			throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_TO_MANY_SYSTEM_ENTITY, uid);
		}
		return systemEntity;
	}

	/**
	 * Removes the flag "wish" from system entity, if the flag is true and removing
	 * the flag is possible and safe = it won't lead to any unwanted linking: A) The
	 * system entity wasn't linked to any IdM entity before this synchronization.
	 * It's just some relic of previous operations in IdM. The entity on the system
	 * exists, so we will correct the information that it is only Wish (because it
	 * really exists). B) The system entity is linked to IdM entity and automapping
	 * existing accounts is allowed. This can happen when identity had been assigned
	 * a role, but provisioning hadn't been executed yet for some reason (read-only
	 * system, error,...). Since automapping is enabled, we can remove the flag, so
	 * following provisioning will be Update and not Create.
	 *
	 * @param systemEntity The system entity which will be processed
	 * @param existingLink If the link (AccAccount) already exists for this system
	 * entity
	 * @param context
	 * @return Updated system entity
	 */
	private SysSystemEntityDto removeSystemEntityWishIfPossible(SysSystemEntityDto systemEntity, boolean existingLink,
			SynchronizationContext context) {

		if (systemEntity == null || !systemEntity.isWish()) {
			return systemEntity;
		}

		SysSyncItemLogDto logItem = context.getLogItem();

		if (existingLink && !provisioningConfiguration.isAllowedAutoMappingOnExistingAccount()) {
			addToItemLog(logItem, MessageFormat.format(
					"WARNING: Existing system entity ({0}) has the flag Wish, which means it was neither created by IdM nor linked by synchronization. "
					+ "But account for this entity already exists and it is linked to IdM entity [{1}]."
					+ "Auto mapping of existing accounts is not allowed by property [{2}]. "
					+ "We will not remove the flag Wish, because that would effectively complete the auto mapping.",
					systemEntity.getUid(), context.getEntityId(),
					ProvisioningConfiguration.PROPERTY_ALLOW_AUTO_MAPPING_ON_EXISTING_ACCOUNT));
			initSyncActionLog(context.getActionType(), OperationResultType.WARNING, logItem, context.getLog(),
					context.getActionLogs());
			return systemEntity;
		}
		addToItemLog(logItem, MessageFormat.format(
				"Existing system entity [{0}] has the flag Wish, we can safely remove it (the system entity really exists).",
				systemEntity.getUid()));
		systemEntity.setWish(false);
		return systemEntityService.save(systemEntity);
	}

	/**
	 * Start workflow process by wfDefinitionKey. Create input variables and put
	 * them to the process. If log variable is present after the process started,
	 * then add the log to the synchronization log.
	 *
	 * @param wfDefinitionKey
	 * @param situation
	 * @param action
	 * @param dto
	 */
	private void startWorkflow(String wfDefinitionKey, SynchronizationSituationType situation,
			SynchronizationActionType action, DTO dto, SynchronizationContext context) {

		String entityType = context.getEntityType();
		SysSyncLogDto log = context.getLog();
		SysSyncItemLogDto logItem = context.getLogItem();
		List<SysSyncActionLogDto> actionLogs = context.getActionLogs();
		AccAccountDto account = context.getAccount();
		String uid = context.getUid();
		AbstractSysSyncConfigDto config = context.getConfig();

		addToItemLog(logItem,
				MessageFormat.format("Workflow for [{0}] situation was found. We will start it.", situation));

		Map<String, Object> variables = new HashMap<>();
		variables.put(SynchronizationService.WF_VARIABLE_KEY_UID, uid);
		variables.put(SynchronizationService.WF_VARIABLE_KEY_ENTITY_TYPE, entityType);
		variables.put(SynchronizationService.WF_VARIABLE_KEY_SYNC_SITUATION, situation.name());
		variables.put(SynchronizationService.WF_VARIABLE_KEY_IC_ATTRIBUTES,
				context.getIcObject() != null ? context.getIcObject().getAttributes() : null);
		variables.put(SynchronizationService.WF_VARIABLE_KEY_ACTION_TYPE, action.name());
		variables.put(SynchronizationService.WF_VARIABLE_KEY_ENTITY_ID, dto != null ? dto.getId() : null);
		variables.put(SynchronizationService.WF_VARIABLE_KEY_ACC_ACCOUNT_ID, account != null ? account.getId() : null);
		variables.put(SynchronizationService.WF_VARIABLE_KEY_SYNC_CONFIG_ID, config.getId());
		variables.put(SynchronizationService.WF_VARIABLE_KEY_SYNC_CONFIG_ID, config.getId());
		variables.put(SynchronizationService.WF_VARIABLE_KEY_SYSTEM_ID, context.getSystem().getId());

		ProcessInstance processInstance = workflowProcessInstanceService.startProcess(wfDefinitionKey,
				SysSyncConfig.class.getSimpleName(), config.getId().toString(), variables);

		if (processInstance instanceof VariableScope) {
			Object logItemObj = ((VariableScope) processInstance)
					.getVariable(SynchronizationService.WF_VARIABLE_KEY_LOG_ITEM);
			if (logItemObj instanceof String) {
				addToItemLog(logItem, (String) logItemObj);
			}

		}
		if (processInstance != null && processInstance.isEnded()) {
			addToItemLog(logItem, MessageFormat.format("Workflow (with id [{0}]) for missing entity situation ended.",
					processInstance.getId()));
			initSyncActionLog(situation.getAction(), OperationResultType.WF, logItem, log, actionLogs);

			// We don't wont history for workflow executed in synchronization!
			processEngine.getHistoryService().deleteHistoricProcessInstance(processInstance.getId());
			addToItemLog(logItem, MessageFormat.format("Workflow history for process instance [{0}] was deleted.",
					processInstance.getId()));

		} else {
			// If workflow not ended, then the history will be not deleted!
			addToItemLog(logItem, MessageFormat.format(
					"Workflow (with id [{0}]) for missing entity situation not ended (will be ended asynchronously).",
					processInstance != null ? processInstance.getId() : null));
			initSyncActionLog(situation.getAction(), OperationResultType.WF, logItem, log, actionLogs);
		}
	}

	protected Session getHibernateSession() {
		return (Session) this.entityManager.getDelegate();
	}

	/**
	 * Init sync action log
	 *
	 * @param actionType
	 * @param resultType
	 * @param logItem
	 * @param log
	 * @param actionLogs
	 */
	protected void initSyncActionLog(SynchronizationActionType actionType, OperationResultType resultType,
			SysSyncItemLogDto logItem, SysSyncLogDto log, List<SysSyncActionLogDto> actionLogs) {
		this.initSyncActionLog(actionType, resultType, logItem, log, actionLogs, true);
	}

	/**
	 * Init sync action log
	 *
	 * @param actionType
	 * @param resultType
	 * @param logItem
	 * @param log
	 * @param actionLogs
	 * @param interateCount
	 */
	protected void initSyncActionLog(SynchronizationActionType actionType, OperationResultType resultType,
			SysSyncItemLogDto logItem, SysSyncLogDto log, List<SysSyncActionLogDto> actionLogs, boolean interateCount) {

		if (logItem == null || actionLogs == null) {
			// If is logItem null, then we have nothing for init.
			// We probably call this outside standard sync cycle (workflow
			// maybe)
			return;
		}

		// Log is already initialized, but if is new result type ERROR or
		// WARNING, then have priority
		if (this.existItemLogInActions(actionLogs, logItem)) {
			if (OperationResultType.ERROR != resultType && OperationResultType.WARNING != resultType) {
				return;
			}
			Pair<SysSyncActionLogDto, SysSyncItemLogDto> actionWithItemLog = getActionLogThatContains(actionLogs,
					logItem);
			if (OperationResultType.ERROR == resultType
					&& OperationResultType.ERROR == actionWithItemLog.getLeft().getOperationResult()) {
				return;
			}
			if (OperationResultType.WARNING == resultType
					&& OperationResultType.WARNING == actionWithItemLog.getLeft().getOperationResult()) {
				return;
			}
		}
		SysSyncActionLogDto actionLog;
		Optional<SysSyncActionLogDto> optionalActionLog = actionLogs.stream()
				.filter(al -> {
					return actionType == al.getSyncAction() && resultType == al.getOperationResult();
				})
				.findFirst();
		if (optionalActionLog.isPresent()) {
			actionLog = optionalActionLog.get();
		} else {
			actionLog = new SysSyncActionLogDto();
			actionLog.setOperationResult(resultType);
			actionLog.setSyncAction(actionType);
			actionLog.setSyncLog(log.getId());
			actionLogs.add(actionLog);
		}
		actionLog.addLogItems(logItem);
		if (interateCount) {
			actionLog.setOperationCount(actionLog.getOperationCount() + 1);
			addToItemLog(logItem, MessageFormat.format("Operation count for [{0}] is [{1}]", actionLog.getSyncAction(),
					actionLog.getOperationCount()));
		}
	}

	/**
	 * Find entity by account
	 *
	 * @param accountId
	 * @return
	 */
	@Override
	public UUID getEntityByAccount(UUID accountId) {
		EntityAccountFilter entityAccountFilter = createEntityAccountFilter();
		entityAccountFilter.setAccountId(accountId);
		entityAccountFilter.setOwnership(Boolean.TRUE);
		List<EntityAccountDto> entityAccounts = this.getEntityAccountService()
				.find(entityAccountFilter, PageRequest.of(0, 1)).getContent();
		if (entityAccounts.isEmpty()) {
			return null;
		} else {
			// We assume that all identity accounts
			// (mark as
			// ownership) have same identity!
			return entityAccounts.get(0).getEntity();
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public DTO getDtoByAccount(UUID entityId, AccAccountDto account) {
		if (entityId == null && account != null) {
			Assert.notNull(account.getId(), "Account ID cannot be null!");

			EntityAccountFilter entityAccountFilter = createEntityAccountFilter();
			entityAccountFilter.setAccountId(account.getId());
			entityAccountFilter.setOwnership(Boolean.TRUE);
			List<EntityAccountDto> entityAccounts = this.getEntityAccountService()//
					.find(entityAccountFilter, PageRequest.of(0, 1))//
					.getContent();
			if (!entityAccounts.isEmpty()) {
				// We assume that all identity accounts
				// (mark as
				// ownership) have same identity!
				EntityAccountDto entityAccountDto = entityAccounts.get(0);
				if (entityAccountDto instanceof AbstractDto && entityAccountDto.getEntity() != null) {
					UUID entityIdLocal = entityAccountDto.getEntity();
					AbstractDto entityAccount = (AbstractDto) entityAccountDto;

					// Try to find target entity in embedded by entityId.
					BaseDto targetDto = entityAccount.getEmbedded().values()//
							.stream()//
							.filter(dto -> entityIdLocal.equals(dto.getId()))//
							.findFirst()//
							.orElse(null);
					if (targetDto != null) {
						return (DTO) targetDto;
					} else {
						entityId = entityIdLocal;
					}
				}
			}
		}
		DTO entity = null;
		if (entityId != null) {
			entity = this.getService().get(entityId);
		}
		return entity;
	}

	/**
	 * Find account ID by entity ID
	 *
	 * @param entityId
	 * @param systemId
	 * @return
	 */
	protected UUID getAccountByEntity(UUID entityId, UUID systemId) {
		EntityAccountFilter entityAccountFilter = createEntityAccountFilter();
		entityAccountFilter.setEntityId(entityId);
		entityAccountFilter.setSystemId(systemId);
		entityAccountFilter.setOwnership(Boolean.TRUE);
		List<EntityAccountDto> entityAccounts = this.getEntityAccountService().find(entityAccountFilter, null)
				.getContent();
		if (entityAccounts.isEmpty()) {
			return null;
		} else {
			// We assume that all entity accounts
			// (mark as
			// ownership) have same account!
			return entityAccounts.get(0).getEntity();
		}
	}

	/**
	 * Create account and relation on him
	 *
	 * @param callProvisioning
	 * @param dto
	 * @param context
	 */
	protected void doCreateLink(DTO dto, boolean callProvisioning, SynchronizationContext context) {
		String uid = context.getUid();
		String entityType = context.getEntityType();
		SysSystemDto system = context.getSystem();
		SysSyncItemLogDto logItem = context.getLogItem();
		SysSystemEntityDto systemEntity = context.getSystemEntity();

		String entityIdentification = dto.getId().toString();
		if (dto instanceof Codeable) {
			entityIdentification = ((Codeable) dto).getCode();
		}

		logItem.setDisplayName(entityIdentification);

		// Generate UID value from mapped attribute marked as UID (Unique ID).
		// UID mapped attribute must exist and returned value must be not null
		// and must be String
		String attributeUid = this.generateUID(context);

		AccAccountDto account = doCreateIdmAccount(attributeUid, system);
		if (systemEntity != null) {
			// If SystemEntity for this account already exist, then we linked
			// him to new account
			account.setSystemEntity(systemEntity.getId());
		}

		setMappingIdToAccount(context, account, false);

		account = this.applySpecificSettingsBeforeLink(account, dto, context);

		if (account == null) {
			// Identity account won't be created
			addToItemLog(logItem, MessageFormat.format(
					"Link between uid [{0}] and entity [{1}] will not be created due to specific settings of synchronization. "
					+ "Processing of this item is finished.",
					uid, entityIdentification));
			return;
		}

		account = accountService.save(account);
		addToItemLog(logItem,
				MessageFormat.format("Account with uid [{0}] and id [{1}] was created", uid, account.getId()));

		// Create new entity account relation
		EntityAccountDto entityAccount = this.createEntityAccount(account, dto, context);
		entityAccount = (EntityAccountDto) getEntityAccountService().save(entityAccount);
		context.addAccount(account);

		// Identity account Created
		addToItemLog(logItem,
				MessageFormat.format(
						"Entity account relation  with id [{0}], between account [{1}] and entity [{2}] was created",
						entityAccount.getId(), uid, entityIdentification));
		logItem.setType(entityAccount.getClass().getSimpleName());
		logItem.setIdentification(entityAccount.getId().toString());

		if (callProvisioning) {
			if (this.isProvisioningImplemented(entityType, logItem)) {
				// Call provisioning for this entity
				callProvisioningForEntity(dto, entityType, logItem);
			}
		}
	}

	/**
	 * Apply settings that are specific to this type of entity.Default
	 * implementation does nothing to the account.
	 *
	 * @param account
	 * @param entity
	 * @param context
	 * @return
	 */
	protected AccAccountDto applySpecificSettingsBeforeLink(AccAccountDto account, DTO entity,
			SynchronizationContext context) {
		return account;
	}

	protected String getDisplayNameForEntity(AbstractDto entity) {
		if (entity == null) {
			return null;
		}
		if (entity instanceof Codeable) {
			return ((Codeable) entity).getCode();
		}
		return entity.getId().toString();
	}

	/**
	 * Delete entity linked with given account
	 *
	 * @param account
	 * @param log
	 * @param logItem
	 * @param actionLogs
	 */
	protected void doDeleteEntity(AccAccountDto account, SysSyncLogDto log,
			SysSyncItemLogDto logItem, List<SysSyncActionLogDto> actionLogs) {
		DTO dto = this.getDtoByAccount(null, account);
		if (dto == null) {
			addToItemLog(logItem, MessageFormat.format("Warning! - Entity for account [{0}] was not found!", account.getUid()));
			initSyncActionLog(SynchronizationActionType.DELETE_ENTITY, OperationResultType.WARNING, logItem, log,
					actionLogs);
			return;
		}
		String entityIdentification = dto.getId().toString();
		if (dto instanceof Codeable) {
			entityIdentification = ((Codeable) dto).getCode();
		}
		logItem.setDisplayName(entityIdentification);
		// Delete entity
		getService().delete(dto);
	}

	@Override
	public void setLongRunningTaskExecutor(AbstractSchedulableTaskExecutor<Boolean> longRunningTaskExecutor) {
		this.longRunningTaskExecutor = longRunningTaskExecutor;
	}

	protected SysSystemMappingDto getSystemMapping(List<SysSystemAttributeMappingDto> attributes) {
		if (attributes == null || attributes.isEmpty()) {
			return null;
		}
		return systemMappingService.get(attributes.get(0).getSystemMapping());
	}

	protected SysSystemAttributeMappingDto getAttributeByIdmProperty(String idmProperty,
			List<SysSystemAttributeMappingDto> mappedAttributes) {
		Optional<SysSystemAttributeMappingDto> optional = mappedAttributes.stream().filter(attribute -> {
			return !attribute.isDisabledAttribute() && attribute.isEntityAttribute()
					&& idmProperty.equals(attribute.getIdmPropertyName());
		}).findFirst();

		if (!optional.isPresent()) {
			return null;
		}
		return optional.get();
	}

	/**
	 * Update (increased counter) and check state of sync (maybe was cancelled from
	 * sync or LRT)
	 *
	 * @param result
	 * @param log
	 */
	private boolean updateAndCheckState(boolean result, SysSyncLogDto log) {
		// We reload log (maybe was synchronization canceled)
		log.setRunning(synchronizationLogService.get(log.getId()).isRunning());
		longRunningTaskExecutor.increaseCounter();
		boolean lrtResult = longRunningTaskExecutor.updateState();
		if (!log.isRunning() || !lrtResult) {
			result = false;
		}
		if (!result) {
			log.setRunning(false);
			log.addToLog("Synchronization canceled!");
		}
		return result;
	}

	/**
	 * Default implementation of {@link IcResultsHandler}
	 *
	 * @author svandav
	 *
	 */
	private class DefaultResultHandler implements IcResultsHandler {

		private final SynchronizationContext context;
		private final Set<String> systemAccountsList;

		public DefaultResultHandler(final SynchronizationContext context, final Set<String> systemAccountsList) {
			this.context = context;
			this.systemAccountsList = systemAccountsList;
		}

		@Override
		public boolean handle(IcConnectorObject connectorObject) {
			Assert.notNull(connectorObject, "Connector object is required.");
			Assert.notNull(connectorObject.getUidValue(), "Connector object uid is required.");
			String uid = connectorObject.getUidValue();

			if (context.getConfig().isReconciliation()) {
				systemAccountsList.add(uid);
			}

			SynchronizationContext itemContext = cloneItemContext(context);
			itemContext //
					.addUid(uid) //
					.addIcObject(connectorObject) //
					.addGeneratedUid(null); //

			return handleIcObject(itemContext);
		}
	}
	
	protected SynchronizationContext cloneItemContext(SynchronizationContext context) {
		return SynchronizationContext.cloneContext(context);
	}

	/**
	 * Default implementation of {@link IcSyncResultsHandler}
	 *
	 * @author svandav
	 *
	 */
	private class DefalutSyncResultHandler implements IcSyncResultsHandler {

		private final SynchronizationContext context;
		private final Set<String> systemAccountsList;

		public DefalutSyncResultHandler(final SynchronizationContext context, final Set<String> systemAccountsList) {
			this.context = context;
			this.systemAccountsList = systemAccountsList;
		}

		@Override
		public boolean handle(IcSyncDelta delta) {
			SysSyncLogDto log = context.getLog();
			AbstractSysSyncConfigDto config = context.getConfig();
			SysSyncItemLogDto itemLog = new SysSyncItemLogDto();

			Assert.notNull(delta, "Synchronization delta is required.");
			Assert.notNull(delta.getUid(), "Synchronization delta uid is required.");
			String uid = delta.getUid().getUidValue();
			IcSyncDeltaTypeEnum type = delta.getDeltaType();
			IcConnectorObject icObject = delta.getObject();
			IcSyncToken token = delta.getToken();
			String tokenObject = token.getValue() != null ? token.getValue().toString() : null;
			// Save token
			log.setToken(tokenObject);
			if (!config.isReconciliation()) {
				config.setToken(tokenObject);
			}
			//
			if (config.isReconciliation()) {
				systemAccountsList.add(uid);
			}

			SynchronizationContext itemContext = cloneItemContext(context);
			itemContext //
					.addUid(uid) //
					.addLogItem(itemLog) //
					.addType(type) //
					.addIcObject(icObject) //
					.addGeneratedUid(null); //

			boolean result = startItemSynchronization(itemContext);

			// Update (increased counter) and check state of sync (maybe was cancelled from
			// sync or LRT)
			return updateAndCheckState(result, log);

		}
	}

	/**
	 * Save DTO
	 *
	 * @param dto
	 * @param skipProvisioning
	 * @param context
	 * @return
	 */
	protected abstract DTO save(DTO dto, boolean skipProvisioning, SynchronizationContext context);

	/**
	 * Update account UID from system. UID mapped attribute must exist and returned
	 * value must be not null and must be String
	 *
	 * @param context
	 */
	private void updateAccountUid(SynchronizationContext context) {
		Assert.notNull(context, "Context is required!");

		SysSyncItemLogDto logItem = context.getLogItem();
		AccAccountDto account = context.getAccount();
		// Generate UID value from mapped attribute marked as UID (Unique ID).
		// UID mapped attribute must exist and returned value must be not null
		// and must be String
		String attributeUid = this.generateUID(context);
		if (!account.getUid().equals(attributeUid)) {
			addToItemLog(logItem, MessageFormat.format("IdM Account UID [{0}] is different [{1}]. We will update it.",
					account.getUid(), attributeUid));
			account.setUid(attributeUid);
			accountService.save(account);
		}
	}

	/**
	 * Method return Pair with left side contains action log that contains given
	 * item log from parameter. And right side contains instance of item log from
	 * action log.
	 *
	 * @param actionsLog
	 * @param itemLog
	 * @return
	 */
	private Pair<SysSyncActionLogDto, SysSyncItemLogDto> getActionLogThatContains(List<SysSyncActionLogDto> actionsLog,
			SysSyncItemLogDto itemLog) {
		for (SysSyncActionLogDto actionLog : actionsLog) {
			for (SysSyncItemLogDto item : actionLog.getLogItems()) {
				if (item.equals(itemLog)) {
					return new ImmutablePair<>(actionLog, item);
				}
			}
		}
		return null;
	}

	/**
	 * Method iterate over actionsLg given in parameter. And search itemLog in each
	 * actionLog.
	 *
	 * @param actionsLog
	 * @param itemLog
	 * @return
	 */
	private boolean existItemLogInActions(List<SysSyncActionLogDto> actionsLog, SysSyncItemLogDto itemLog) {
		return actionsLog.stream()
				.anyMatch((actionLog) -> (actionLog.getLogItems()
				.stream()
				.anyMatch((item) -> (item.equals(itemLog)))));
	}

	/**
	 * Save action logs
	 *
	 * @param actionsLog
	 * @return
	 */
	private List<SysSyncActionLogDto> saveActionLogs(List<SysSyncActionLogDto> actionsLog, UUID syncLogId) {
		syncActionLogService.saveAll(actionsLog);
		SysSyncActionLogFilter actionFilter = new SysSyncActionLogFilter();
		actionFilter.setSynchronizationLogId(syncLogId);
		return new ArrayList<>(syncActionLogService.find(actionFilter, null).getContent());
	}

	/**
	 * Skip entity update
	 *
	 * @param entity
	 * @param context
	 * @return
	 */
	protected boolean skipEntityUpdate(DTO entity, SynchronizationContext context) {
		return false;
	}

	/**
	 * Check that the strategy was set to IGNORE_AND_DO_NOT_LOG.
	 *
	 * @param lastResult
	 * @return
	 */
	private boolean shouldBeIgnored(EventResult<SysSyncItemLogDto> lastResult) {
		return lastResult != null && lastResult.getEvent().getContent() != null &&
				lastResult.getEvent().getContent().getLog().contains(SynchronizationActionType.IGNORE_AND_DO_NOT_LOG.name());
	}

	private void setMappingIdToAccount(SynchronizationContext context, AccAccountDto account, boolean saveIt) {
		SysSystemMappingDto systemMappingDto = systemMappingService.get(context.getConfig().getSystemMapping());
		if (systemMappingDto != null && systemMappingDto.getConnectedSystemMappingId() != null &&
				!systemMappingDto.getConnectedSystemMappingId().equals(account.getSystemMapping())) {
			account.setSystemMapping(systemMappingDto.getConnectedSystemMappingId());
			if (saveIt) {
				accountService.save(account);
			}
		}
	}
}
