package eu.bcvsolutions.idm.acc.service.impl;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.ImmutableList;

import eu.bcvsolutions.idm.acc.TestHelper;
import eu.bcvsolutions.idm.acc.domain.AccountType;
import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.OperationResultType;
import eu.bcvsolutions.idm.acc.domain.ReconciliationMissingAccountActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationLinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationMissingEntityActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationUnlinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncActionLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncIdentityConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncItemLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccIdentityAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncActionLogFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncConfigFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncItemLogFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncLogFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemAttributeMappingFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemMappingFilter;
import eu.bcvsolutions.idm.acc.entity.TestResource;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncActionLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncConfigService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncItemLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.ic.domain.IcFilterOperationType;
import eu.bcvsolutions.idm.ic.service.api.IcConnectorFacade;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Synchronization tests
 * 
 * @author Svanda
 * @author Ondrej Husnik
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Rollback(false)
public class DefaultSynchronizationServiceTest extends AbstractIntegrationTest {
	private static final String IDENTITY_USERNAME_ONE = "syncUserOneTest";
	private static final String IDENTITY_USERNAME_TWO = "syncUserTwoTest";
	private static final String IDENTITY_USERNAME_THREE = "syncUserThreeTest";
	private static final String ATTRIBUTE_NAME = "__NAME__";
	private static final String ATTRIBUTE_EMAIL = "email";
	private static final String ATTRIBUTE_MODIFIED = "modified";
	private static final String ATTRIBUTE_VALUE_CHANGED = "changed";
	private static final String EAV_ATTRIBUTE = "EAV_ATTRIBUTE";

	private static final String SYNC_CONFIG_NAME = "syncConfigName";

	private static final String IDENTITY_EMAIL_WRONG = "email";
	private static final String IDENTITY_EMAIL_CORRECT = "email@test.cz";
	private static final String IDENTITY_EMAIL_CORRECT_CHANGED = "email@changed.cz";

	@Autowired
	private TestHelper helper;
	@Autowired
	private SysSystemService systemService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private AccIdentityAccountService identityAccoutnService;
	@Autowired
	private AccAccountService accountService;
	@Autowired
	private SysSystemMappingService systemMappingService;
	@Autowired
	private SysSystemAttributeMappingService schemaAttributeMappingService;
	@Autowired
	private SysSchemaObjectClassService schemaObjectClassService;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	@Autowired
	private SysSyncConfigService syncConfigService;
	@Autowired
	private SysSyncLogService syncLogService;
	@Autowired
	private SysSyncItemLogService syncItemLogService;
	@Autowired
	private SysSyncActionLogService syncActionLogService;
	@Qualifier("entityManager")
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	private FormService formService;

	private SysSystemDto system;

	@Before
	public void init() {
		loginAsAdmin();
	}

	@After
	public void logout() {
		super.logout();
	}

	@Test
	@Transactional
	public void doCreateSyncConfig() {
		initData();

		SysSystemMappingFilter mappingFilter = new SysSystemMappingFilter();
		mappingFilter.setEntityType(SystemEntityType.IDENTITY);
		mappingFilter.setSystemId(system.getId());
		mappingFilter.setOperationType(SystemOperationType.SYNCHRONIZATION);
		List<SysSystemMappingDto> mappings = systemMappingService.find(mappingFilter, null).getContent();
		Assert.assertEquals(1, mappings.size());
		SysSystemMappingDto mapping = mappings.get(0);
		SysSystemAttributeMappingFilter attributeMappingFilter = new SysSystemAttributeMappingFilter();
		attributeMappingFilter.setSystemMappingId(mapping.getId());

		List<SysSystemAttributeMappingDto> attributes = schemaAttributeMappingService.find(attributeMappingFilter, null)
				.getContent();
		SysSystemAttributeMappingDto nameAttribute = attributes.stream().filter(attribute -> {
			return attribute.getName().equals(ATTRIBUTE_NAME);
		}).findFirst().get();

		SysSystemAttributeMappingDto modifiedAttribute = attributes.stream().filter(attribute -> {
			return attribute.getName().equals(ATTRIBUTE_MODIFIED);
		}).findFirst().get();

		// Create default synchronization config
		AbstractSysSyncConfigDto syncConfigCustom = new SysSyncIdentityConfigDto();
		syncConfigCustom.setCustomFilter(true);
		syncConfigCustom.setSystemMapping(mapping.getId());
		syncConfigCustom.setCorrelationAttribute(nameAttribute.getId());
		syncConfigCustom.setTokenAttribute(modifiedAttribute.getId());
		syncConfigCustom.setFilterAttribute(modifiedAttribute.getId());
		syncConfigCustom.setReconciliation(true);
		syncConfigCustom.setName(SYNC_CONFIG_NAME);
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.IGNORE);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.IGNORE);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.CREATE_ENTITY);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);

		syncConfigService.save(syncConfigCustom);

		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setSystemId(system.getId());
		Assert.assertEquals(1, syncConfigService.find(configFilter, null).getTotalElements());
	}

	@Test
	public void doStartSyncA_MissingEntity() {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		//
		helper.startSynchronization(syncConfigCustom);
	
		//		
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(1, actions.size());

		SysSyncActionLogDto createEntityActionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.CREATE_ENTITY == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(createEntityActionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(2, items.size());

		// Delete log
		syncLogService.delete(log);
	}
	
	@Test
	public void doStartSyncA_TryFindSyncConfigByMappingId() {
		SysSyncConfigFilter filterByName = new SysSyncConfigFilter();
		filterByName.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigsOrig = syncConfigService.find(filterByName, null).getContent();
		
		// Sync config with known name has to be found.
		Assert.assertEquals(1, syncConfigsOrig.size());

		AbstractSysSyncConfigDto syncConfigOrig = syncConfigsOrig.get(0);
		SysSyncConfigFilter filterByMappingId = new SysSyncConfigFilter();
		filterByMappingId.setSystemMappingId(syncConfigOrig.getSystemMapping());
		List<AbstractSysSyncConfigDto> syncConfigsTested = syncConfigService.find(filterByMappingId, null).getContent();
		
		// Test that exists a sync config found by mapping id.
		Assert.assertEquals(1, syncConfigsTested.size());

		AbstractSysSyncConfigDto syncConfigTested = syncConfigsTested.get(0);
		
		// Test that both sync configs found by different filters are same.
		Assert.assertTrue(syncConfigOrig.getName().equals(syncConfigTested.getName()));

		UUID nonexistentUUID = new UUID(11111111, 2222222);
		filterByMappingId.setSystemMappingId(nonexistentUUID);
		List<AbstractSysSyncConfigDto> syncConfigsEmpty = syncConfigService.find(filterByMappingId, null).getContent();
		
		// Test that searching by nonexistent mapping id finds nothing.
		Assert.assertEquals(0, syncConfigsEmpty.size());
	}
	
	@Test
	public void doStartSyncB_Linked_doFilterByDifferentialSync () {
		SysSyncConfigFilter configFilterByName = new SysSyncConfigFilter();
		SysSyncConfigFilter configFilterByDiffSync = new SysSyncConfigFilter();
		configFilterByName.setName(SYNC_CONFIG_NAME);
		configFilterByDiffSync.setDifferentialSync(null);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilterByName, null).getContent();
		
		// Make sure that there is just 1 SysSyncConfigDto with given name
		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigDto = syncConfigs.get(0);
		syncConfigDto.setDifferentialSync(true);
		syncConfigDto = syncConfigService.save(syncConfigDto);
		
		// Test that all found DTOs have differentialSync item set to true.
		configFilterByDiffSync.setDifferentialSync(true);
		syncConfigService.find(configFilterByDiffSync, null).getContent().forEach(dto -> Assert.assertEquals(true, dto.isDifferentialSync()));
		// Test that all found DTOs have differentialSync item set to false.		
		configFilterByDiffSync.setDifferentialSync(false);
		syncConfigService.find(configFilterByDiffSync, null).getContent().forEach(dto -> Assert.assertEquals(false, dto.isDifferentialSync()));
	}

	@Test
	public void doStartSyncB_Linked_doEntityUpdate() {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		this.getBean().changeResourceData();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));

		// Set sync config
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UPDATE_ENTITY);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.IGNORE);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.IGNORE);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);
		syncConfigService.save(syncConfigCustom);

		// Check state before sync
		Assert.assertEquals(IDENTITY_USERNAME_ONE,
				identityService.getByUsername("x" + IDENTITY_USERNAME_ONE).getFirstName());
		Assert.assertEquals(IDENTITY_USERNAME_TWO,
				identityService.getByUsername("x" + IDENTITY_USERNAME_TWO).getLastName());

		helper.startSynchronization(syncConfigCustom);
	
		//
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(1, actions.size());

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.UPDATE_ENTITY == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(2, items.size());

		// Check state after sync
		Assert.assertEquals(ATTRIBUTE_VALUE_CHANGED,
				identityService.getByUsername("x" + IDENTITY_USERNAME_ONE).getFirstName());
		Assert.assertEquals(ATTRIBUTE_VALUE_CHANGED,
				identityService.getByUsername("x" + IDENTITY_USERNAME_TWO).getLastName());
		// Delete log
		syncLogService.delete(log);

	}
	
	@Test
	/**
	 * We will do synchronize with use inner connector synch function.
	 */
	public void doStartSyncB_Linked_doEntityUpdate_Filtered() {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		this.getBean().changeResourceData();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		
		SysSystemMappingDto systemMapping = systemMappingService.get(syncConfigCustom.getSystemMapping());
		SysSystemDto system = systemService.get(schemaObjectClassService.get(systemMapping.getObjectClass()).getSystem());
		
		IdmFormDefinitionDto savedFormDefinition = systemService.getConnectorFormDefinition(system);
		IdmFormAttributeDto changeLogColumn = savedFormDefinition.getMappedAttributeByCode("changeLogColumn");
		formService.saveValues(system, changeLogColumn, ImmutableList.of("modified"));

		// Set sync config
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UPDATE_ENTITY);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.IGNORE);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.IGNORE);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);
		
		syncConfigCustom.setCustomFilter(false);
		syncConfigCustom.setReconciliation(false);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		syncConfigCustom.setToken(ZonedDateTime.now().format(formatter)); // We want do sync for account changed in future
		syncConfigCustom.setFilterOperation(IcFilterOperationType.ENDS_WITH); // We don`t use custom filter. This option will be not used.
		syncConfigService.save(syncConfigCustom);

		helper.startSynchronization(syncConfigCustom);
	
		//
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(1, actions.size());

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.UPDATE_ENTITY == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(1, items.size());
		Assert.assertEquals("x"+IDENTITY_USERNAME_TWO, items.get(0).getIdentification());

		// Delete log
		syncLogService.delete(log);
		
		// We have to change property of connector configuration "changeLogColumn" from "modified" on empty string. 
		// When is this property set, then custom filter not working. Bug in Table connector !!!
		formService.saveValues(system, changeLogColumn, ImmutableList.of(""));
	}
	
	@Test
	/**
	 * We will do sync with use custom filter. Only account modified in last will be synchronized.
	 */
	public void doStartSyncB_Linked_doEntityUpdate_Filtered_Custom() {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		this.getBean().changeResourceData();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));

		// Set sync config
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UPDATE_ENTITY);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.IGNORE);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.IGNORE);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);
		
		syncConfigCustom.setCustomFilter(true);
		syncConfigCustom.setReconciliation(false);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		syncConfigCustom.setToken(ZonedDateTime.now().format(formatter));
		syncConfigCustom.setFilterOperation(IcFilterOperationType.LESS_THAN);
		syncConfigService.save(syncConfigCustom);
		//
		helper.startSynchronization(syncConfigCustom);
	
		//
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(1, actions.size());

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.UPDATE_ENTITY == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(1, items.size());
		Assert.assertEquals("x"+IDENTITY_USERNAME_ONE, items.get(0).getIdentification());

		// Delete log
		syncLogService.delete(log);
	}

	/*
	 * We will assert, that in log will be errors, when we will set incorrect
	 * email format.
	 */
	@Test
	public void doStartSyncB_Linked_doEntityUpdate_WrongEmail() {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		// Set wrong email to resource
		this.getBean().changeResourceDataWrongEmail();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));

		// Set sync config
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UPDATE_ENTITY);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.IGNORE);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.IGNORE);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);
		syncConfigCustom.setReconciliation(true);
		syncConfigService.save(syncConfigCustom);
		//
		helper.startSynchronization(syncConfigCustom);
	
		//
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		// Log must contains error
		Assert.assertTrue(log.isContainsError());

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(1, actions.size());

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.UPDATE_ENTITY == action.getSyncAction()
					&& OperationResultType.ERROR == action.getOperationResult();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(2, items.size());

		// Delete log
		syncLogService.delete(log);
	}

	@Test
	public void doStartSyncB_Linked_doUnLinked() {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));

		// Set sync config
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UNLINK);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.IGNORE);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.IGNORE);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);
		syncConfigCustom.setReconciliation(true);
		syncConfigService.save(syncConfigCustom);

		// Check state before sync
		AccIdentityAccountFilter identityAccountFilterOne = new AccIdentityAccountFilter();
		identityAccountFilterOne.setIdentityId(identityService.getByUsername("x" + IDENTITY_USERNAME_ONE).getId());
		Assert.assertEquals(1, identityAccoutnService.find(identityAccountFilterOne, null).getTotalElements());

		AccIdentityAccountFilter identityAccountFilterTwo = new AccIdentityAccountFilter();
		identityAccountFilterTwo.setIdentityId(identityService.getByUsername("x" + IDENTITY_USERNAME_ONE).getId());
		Assert.assertEquals(1, identityAccoutnService.find(identityAccountFilterTwo, null).getTotalElements());

		// Start synchronization
		helper.startSynchronization(syncConfigCustom);
	
		//
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(1, actions.size());

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.UNLINK == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(2, items.size());

		// Check state after sync
		Assert.assertEquals(0, identityAccoutnService.find(identityAccountFilterOne, null).getTotalElements());
		Assert.assertEquals(0, identityAccoutnService.find(identityAccountFilterTwo, null).getTotalElements());

		// Delete log
		syncLogService.delete(log);

	}

	@Test
	public void doStartSyncC_Unlinked_doLink() {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));

		// Set sync config
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.IGNORE);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.LINK);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.IGNORE);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);
		syncConfigCustom.setReconciliation(true);
		syncConfigService.save(syncConfigCustom);

		// Check state before sync

		IdmIdentityDto identityOne = identityService.getByUsername("x" + IDENTITY_USERNAME_ONE);
		IdmIdentityDto identityTwo = identityService.getByUsername("x" + IDENTITY_USERNAME_TWO);
		AccIdentityAccountFilter identityAccountFilterOne = new AccIdentityAccountFilter();
		identityAccountFilterOne.setIdentityId(identityOne.getId());
		Assert.assertEquals(0, identityAccoutnService.find(identityAccountFilterOne, null).getTotalElements());

		AccIdentityAccountFilter identityAccountFilterTwo = new AccIdentityAccountFilter();
		identityAccountFilterTwo.setIdentityId(identityTwo.getId());
		Assert.assertEquals(0, identityAccoutnService.find(identityAccountFilterTwo, null).getTotalElements());

		// Start synchronization
		helper.startSynchronization(syncConfigCustom);
	
		//
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		// log.getSyncActionLogs();
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(1, actions.size());

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.LINK == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(2, items.size());

		// Check state after sync
		Assert.assertEquals(1, identityAccoutnService.find(identityAccountFilterOne, null).getTotalElements());
		Assert.assertEquals(1, identityAccoutnService.find(identityAccountFilterTwo, null).getTotalElements());

		// Delete log
		syncLogService.delete(log);

	}

	@Test
	public void doStartSyncC_Unlinked_doLinkAndUpdateAccount() {
		// We have to do unlink first
		this.doStartSyncB_Linked_doUnLinked();

		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));

		// Set sync config
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.IGNORE);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.LINK_AND_UPDATE_ACCOUNT);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.IGNORE);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);
		syncConfigCustom.setReconciliation(true);
		syncConfigService.save(syncConfigCustom);

		IdmIdentityDto identityOne = identityService.getByUsername("x" + IDENTITY_USERNAME_ONE);
		IdmIdentityDto identityTwo = identityService.getByUsername("x" + IDENTITY_USERNAME_TWO);

		identityOne.setFirstName(IDENTITY_USERNAME_ONE);
		identityTwo.setLastName(IDENTITY_USERNAME_TWO);
		identityService.save(identityOne);
		identityService.save(identityTwo);

		// Change account on resource
		getBean().changeResourceData();

		// Check state before sync
		AccIdentityAccountFilter identityAccountFilterOne = new AccIdentityAccountFilter();
		identityAccountFilterOne.setIdentityId(identityOne.getId());
		Assert.assertEquals(0, identityAccoutnService.find(identityAccountFilterOne, null).getTotalElements());

		AccIdentityAccountFilter identityAccountFilterTwo = new AccIdentityAccountFilter();
		identityAccountFilterTwo.setIdentityId(identityTwo.getId());
		Assert.assertEquals(0, identityAccoutnService.find(identityAccountFilterTwo, null).getTotalElements());

		Assert.assertEquals(IDENTITY_USERNAME_ONE, identityOne.getFirstName());
		Assert.assertEquals(IDENTITY_USERNAME_TWO, identityTwo.getLastName());
		Assert.assertNotEquals(IDENTITY_USERNAME_ONE,
				entityManager.find(TestResource.class, "x" + IDENTITY_USERNAME_ONE).getFirstname());
		Assert.assertNotEquals(IDENTITY_USERNAME_TWO,
				entityManager.find(TestResource.class, "x" + IDENTITY_USERNAME_TWO).getLastname());

		// Start synchronization
		helper.startSynchronization(syncConfigCustom);
	
		//
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(1, actions.size());

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.LINK_AND_UPDATE_ACCOUNT == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(2, items.size());

		// Check state after sync
		Assert.assertEquals(IDENTITY_USERNAME_ONE,
				entityManager.find(TestResource.class, "x" + IDENTITY_USERNAME_ONE).getFirstname());
		Assert.assertEquals(IDENTITY_USERNAME_TWO,
				entityManager.find(TestResource.class, "x" + IDENTITY_USERNAME_TWO).getLastname());

		// Delete log
		syncLogService.delete(log);

	}

	@Test
	public void doStartSyncD_Missing_Account_doCreateAccount() {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));

		// Create new identity THREE, with account
		IdmIdentityDto identity = new IdmIdentityDto();
		identity.setUsername("x" + IDENTITY_USERNAME_THREE);
		identity.setFirstName(IDENTITY_USERNAME_THREE);
		identity.setLastName(IDENTITY_USERNAME_THREE);
		identity = identityService.save(identity);

		AccAccountDto accountOne = new AccAccountDto();
		SysSystemMappingDto systemMapping = systemMappingService.get(syncConfigCustom.getSystemMapping());
		SysSystemDto system = systemService.get(schemaObjectClassService.get(systemMapping.getObjectClass()).getSystem());
		accountOne.setSystem(system.getId());
		accountOne.setUid("x" + IDENTITY_USERNAME_THREE);
		accountOne.setAccountType(AccountType.PERSONAL);
		accountOne.setEntityType(SystemEntityType.IDENTITY);
		accountOne = accountService.save(accountOne);

		AccIdentityAccountDto accountIdentityOne = new AccIdentityAccountDto();
		accountIdentityOne.setIdentity(identity.getId());
		accountIdentityOne.setOwnership(true);
		accountIdentityOne.setAccount(accountOne.getId());

		accountIdentityOne = identityAccoutnService.save(accountIdentityOne);

		// Set sync config
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.IGNORE);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.IGNORE);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.IGNORE);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.CREATE_ACCOUNT);
		syncConfigCustom.setReconciliation(true);
		syncConfigService.save(syncConfigCustom);

		// Check state before sync
		Assert.assertNull(entityManager.find(TestResource.class, "x" + IDENTITY_USERNAME_THREE));

		// Start synchronization
		helper.startSynchronization(syncConfigCustom);
	
		//
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(2, actions.size());

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.CREATE_ACCOUNT == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(1, items.size());

		// Check state after sync
		Assert.assertNotNull(entityManager.find(TestResource.class, "x" + IDENTITY_USERNAME_THREE));

		// Delete log
		syncLogService.delete(log);
	}

	@Test
	public void doStartSyncD_Missing_Account_doDeleteEntity() {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));

		// Delete all accounts in resource
		this.getBean().deleteAllResourceData();

		// Set sync config
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.IGNORE);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.IGNORE);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.IGNORE);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.DELETE_ENTITY);
		syncConfigCustom.setReconciliation(true);
		syncConfigService.save(syncConfigCustom);

		// Check state before sync
		Assert.assertNotNull(identityService.getByUsername("x" + IDENTITY_USERNAME_ONE));
		Assert.assertNotNull(identityService.getByUsername("x" + IDENTITY_USERNAME_TWO));
		Assert.assertNotNull(identityService.getByUsername("x" + IDENTITY_USERNAME_THREE));

		// Start synchronization
		helper.startSynchronization(syncConfigCustom);
	
		//
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(1, actions.size());

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.DELETE_ENTITY == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(3, items.size());

		// Check state after sync
		Assert.assertNull(identityService.getByUsername("x" + IDENTITY_USERNAME_ONE));
		Assert.assertNull(identityService.getByUsername("x" + IDENTITY_USERNAME_TWO));
		Assert.assertNull(identityService.getByUsername("x" + IDENTITY_USERNAME_THREE));

		// Delete log
		syncLogService.delete(log);
	}
	
	@Test
	public void doStartSyncE_StrategyCreate() {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));

		// Delete all accounts in resource
		this.getBean().deleteAllResourceData();
		
		// Create new accounts
		this.getBean().initResourceData();
		
		// Find email attribute and change startegy on CREATE
		SysSystemMappingFilter mappingFilter = new SysSystemMappingFilter();
		mappingFilter.setEntityType(SystemEntityType.IDENTITY);
		SysSystemMappingDto systemMapping = systemMappingService.get(syncConfigCustom.getSystemMapping());
		SysSystemDto system = systemService.get(schemaObjectClassService.get(systemMapping.getObjectClass()).getSystem());
		mappingFilter.setSystemId(system.getId());
		mappingFilter.setOperationType(SystemOperationType.SYNCHRONIZATION);
		List<SysSystemMappingDto> mappings = systemMappingService.find(mappingFilter, null).getContent();
		Assert.assertEquals(1, mappings.size());
		SysSystemMappingDto mapping = mappings.get(0);
		SysSystemAttributeMappingFilter attributeMappingFilter = new SysSystemAttributeMappingFilter();
		attributeMappingFilter.setSystemMappingId(mapping.getId());

		List<SysSystemAttributeMappingDto> attributes = schemaAttributeMappingService.find(attributeMappingFilter, null)
				.getContent();
		SysSystemAttributeMappingDto emailAttribute = attributes.stream().filter(attribute -> {
			return attribute.getName().equalsIgnoreCase(ATTRIBUTE_EMAIL);
		}).findFirst().get();
		
		emailAttribute.setStrategyType(AttributeMappingStrategyType.CREATE);
		schemaAttributeMappingService.save(emailAttribute);
		//
		
		// Set sync config
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UPDATE_ENTITY);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.IGNORE);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.CREATE_ENTITY);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);
		syncConfigCustom.setReconciliation(true);
		syncConfigService.save(syncConfigCustom);

		// Check state before sync
		Assert.assertNull(identityService.getByUsername("x" + IDENTITY_USERNAME_ONE));
		Assert.assertNull(identityService.getByUsername("x" + IDENTITY_USERNAME_TWO));

		// Start synchronization
		helper.startSynchronization(syncConfigCustom);
	
		//
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(1, actions.size());

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.CREATE_ENTITY == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(2, items.size());

		// Check state after sync
		Assert.assertEquals(IDENTITY_USERNAME_ONE, identityService.getByUsername("x" + IDENTITY_USERNAME_ONE).getFirstName());
		Assert.assertEquals(IDENTITY_USERNAME_TWO, identityService.getByUsername("x" + IDENTITY_USERNAME_TWO).getLastName());
		Assert.assertEquals(IDENTITY_EMAIL_CORRECT, identityService.getByUsername("x" + IDENTITY_USERNAME_ONE).getEmail());
		Assert.assertEquals(IDENTITY_EMAIL_CORRECT, identityService.getByUsername("x" + IDENTITY_USERNAME_TWO).getEmail());
		
		// Delete log
		syncLogService.delete(log);
		
		// Change data
		this.getBean().changeResourceData();
		
		// Start synchronization aging
		helper.startSynchronization(syncConfigCustom);
	
		//
		logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(1, actions.size());

		actionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.UPDATE_ENTITY == action.getSyncAction();
		}).findFirst().get();

		itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(2, items.size());

		// Check state after sync
		Assert.assertEquals(ATTRIBUTE_VALUE_CHANGED, identityService.getByUsername("x" + IDENTITY_USERNAME_ONE).getFirstName());
		Assert.assertEquals(ATTRIBUTE_VALUE_CHANGED, identityService.getByUsername("x" + IDENTITY_USERNAME_TWO).getLastName());
		Assert.assertEquals(IDENTITY_EMAIL_CORRECT, identityService.getByUsername("x" + IDENTITY_USERNAME_ONE).getEmail());
		Assert.assertEquals(IDENTITY_EMAIL_CORRECT, identityService.getByUsername("x" + IDENTITY_USERNAME_TWO).getEmail());
		// Delete log
		syncLogService.delete(log);
	}
	
	@Test
	public void doStartSyncE_StrategyWriteIfNull() {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		
		// Find email attribute and change strategy on WRITE_IF_NULL
		SysSystemMappingFilter mappingFilter = new SysSystemMappingFilter();
		mappingFilter.setEntityType(SystemEntityType.IDENTITY);
		SysSystemMappingDto systemMapping = systemMappingService.get(syncConfigCustom.getSystemMapping());
		SysSystemDto system = systemService.get(schemaObjectClassService.get(systemMapping.getObjectClass()).getSystem());
		mappingFilter.setSystemId(system.getId());
		mappingFilter.setOperationType(SystemOperationType.SYNCHRONIZATION);
		List<SysSystemMappingDto> mappings = systemMappingService.find(mappingFilter, null).getContent();
		Assert.assertEquals(1, mappings.size());
		SysSystemMappingDto mapping = mappings.get(0);
		SysSystemAttributeMappingFilter attributeMappingFilter = new SysSystemAttributeMappingFilter();
		attributeMappingFilter.setSystemMappingId(mapping.getId());

		List<SysSystemAttributeMappingDto> attributes = schemaAttributeMappingService.find(attributeMappingFilter, null)
				.getContent();
		SysSystemAttributeMappingDto emailAttribute = attributes.stream().filter(attribute -> {
			return attribute.getName().equalsIgnoreCase(ATTRIBUTE_EMAIL);
		}).findFirst().get();
		
		emailAttribute.setStrategyType(AttributeMappingStrategyType.WRITE_IF_NULL);
		schemaAttributeMappingService.save(emailAttribute);
		//
		// Set email on identity ONE to null
		IdmIdentityDto one = identityService.getByUsername("x" + IDENTITY_USERNAME_ONE);
		one.setEmail(null);
		identityService.save(one);
		
		// Prepare resource data
		this.getBean().deleteAllResourceData();
		this.getBean().initResourceData();
		this.getBean().changeResourceData();
		
		// Set sync config
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UPDATE_ENTITY);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.IGNORE);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.CREATE_ENTITY);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);
		syncConfigCustom.setReconciliation(true);
		syncConfigService.save(syncConfigCustom);


		// Check state before sync
		Assert.assertEquals(null, identityService.getByUsername("x" + IDENTITY_USERNAME_ONE).getEmail());
		Assert.assertEquals(IDENTITY_EMAIL_CORRECT, identityService.getByUsername("x" + IDENTITY_USERNAME_TWO).getEmail());
		

		// Start synchronization
		helper.startSynchronization(syncConfigCustom);
	
		//
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(1, actions.size());

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.UPDATE_ENTITY == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(2, items.size());

		// Check state after sync
		Assert.assertEquals(IDENTITY_EMAIL_CORRECT_CHANGED, identityService.getByUsername("x" + IDENTITY_USERNAME_ONE).getEmail());
		Assert.assertEquals(IDENTITY_EMAIL_CORRECT, identityService.getByUsername("x" + IDENTITY_USERNAME_TWO).getEmail());
		
		// Delete log
		syncLogService.delete(log);
	}
	
	@Test
	public void doStartSyncE_StrategyWriteIfNull_EAV() {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		
		// Find email attribute and change strategy on WRITE_IF_NULL
		SysSystemMappingFilter mappingFilter = new SysSystemMappingFilter();
		mappingFilter.setEntityType(SystemEntityType.IDENTITY);
		SysSystemMappingDto systemMapping = systemMappingService.get(syncConfigCustom.getSystemMapping());
		SysSystemDto system = systemService.get(schemaObjectClassService.get(systemMapping.getObjectClass()).getSystem());
		mappingFilter.setSystemId(system.getId());
		mappingFilter.setOperationType(SystemOperationType.SYNCHRONIZATION);
		List<SysSystemMappingDto> mappings = systemMappingService.find(mappingFilter, null).getContent();
		Assert.assertEquals(1, mappings.size());
		SysSystemMappingDto mapping = mappings.get(0);
		SysSystemAttributeMappingFilter attributeMappingFilter = new SysSystemAttributeMappingFilter();
		attributeMappingFilter.setSystemMappingId(mapping.getId());

		List<SysSystemAttributeMappingDto> attributes = schemaAttributeMappingService.find(attributeMappingFilter, null)
				.getContent();
		SysSystemAttributeMappingDto eavAttribute = attributes.stream().filter(attribute -> {
			return attribute.getName().equalsIgnoreCase(EAV_ATTRIBUTE);
		}).findFirst().get();
		
		eavAttribute.setStrategyType(AttributeMappingStrategyType.WRITE_IF_NULL);
		schemaAttributeMappingService.save(eavAttribute);
		//
		// Set eav on identity ONE to null
		IdmIdentityDto one = identityService.getByUsername("x" + IDENTITY_USERNAME_ONE);
		formService.saveValues(one.getId(), IdmIdentity.class, eavAttribute.getIdmPropertyName(), null);
		IdmIdentityDto two = identityService.getByUsername("x" + IDENTITY_USERNAME_TWO);
		formService.saveValues(two.getId(), IdmIdentity.class, eavAttribute.getIdmPropertyName(), ImmutableList.of(ATTRIBUTE_EMAIL));
		
		// Prepare resource data
		this.getBean().deleteAllResourceData();
		this.getBean().initResourceData();
		this.getBean().changeResourceData();
		
		// Set sync config
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UPDATE_ENTITY);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.IGNORE);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.CREATE_ENTITY);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);
		syncConfigCustom.setReconciliation(true);
		syncConfigService.save(syncConfigCustom);
		

		// Start synchronization
		helper.startSynchronization(syncConfigCustom);
	
		//
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(1, actions.size());

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.UPDATE_ENTITY == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(2, items.size());

		// Check state after sync
		one = identityService.getByUsername("x" + IDENTITY_USERNAME_ONE);
		Assert.assertEquals(ATTRIBUTE_VALUE_CHANGED, formService.getValues(one.getId(), IdmIdentity.class, eavAttribute.getIdmPropertyName()).get(0).getValue());

		two = identityService.getByUsername("x" + IDENTITY_USERNAME_TWO);
		Assert.assertEquals(ATTRIBUTE_EMAIL, formService.getValues(two.getId(), IdmIdentity.class, eavAttribute.getIdmPropertyName()).get(0).getValue());
		
		// Revert strategy
		eavAttribute.setStrategyType(AttributeMappingStrategyType.SET);
		schemaAttributeMappingService.save(eavAttribute);
		// Set EAV value to default
		formService.saveValues(one.getId(), IdmIdentity.class, eavAttribute.getIdmPropertyName(), ImmutableList.of("1"));
		formService.saveValues(two.getId(), IdmIdentity.class, eavAttribute.getIdmPropertyName(), ImmutableList.of("2"));
		// Delete log
		syncLogService.delete(log);
	}
	
	
	@Test
	public void doStartSyncF_Unlinked_doLinkByEavAttribute() {
				
		// Prepare resource data
		this.getBean().deleteAllResourceData();
		this.getBean().initResourceData();
		//
		// call unlink
		this.doStartSyncB_Linked_doUnLinked();
		//
		AbstractSysSyncConfigDto syncConfigCustom = setSyncConfigForEav(SYNC_CONFIG_NAME);

		// Check state before sync
		IdmIdentityDto identityOne = identityService.getByUsername("x" + IDENTITY_USERNAME_ONE);
		IdmIdentityDto identityTwo = identityService.getByUsername("x" + IDENTITY_USERNAME_TWO);
		
		AccIdentityAccountFilter identityAccountFilterOne = new AccIdentityAccountFilter();
		identityAccountFilterOne.setIdentityId(identityOne.getId());
		Assert.assertEquals(0, identityAccoutnService.find(identityAccountFilterOne, null).getTotalElements());

		AccIdentityAccountFilter identityAccountFilterTwo = new AccIdentityAccountFilter();
		identityAccountFilterTwo.setIdentityId(identityTwo.getId());
		Assert.assertEquals(0, identityAccoutnService.find(identityAccountFilterTwo, null).getTotalElements());

		// Start synchronization
		helper.startSynchronization(syncConfigCustom);
	
		//
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		// log.getSyncActionLogs();
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(1, actions.size());

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.LINK == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(2, items.size());

		// Check state after sync
		Assert.assertEquals(1, identityAccoutnService.find(identityAccountFilterOne, null).getTotalElements());
		Assert.assertEquals(1, identityAccoutnService.find(identityAccountFilterTwo, null).getTotalElements());

		// Delete log
		syncLogService.delete(log);
	}
	
	@Test
	public void doStartSyncF_Unlinked_doLinkByEavAttribute_ChangeValue() {
		// call unlink
		this.doStartSyncB_Linked_doUnLinked();
		//
		AbstractSysSyncConfigDto syncConfigCustom = setSyncConfigForEav(SYNC_CONFIG_NAME);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		//
		// Check state before sync
		IdmIdentityDto identityOne = identityService.getByUsername("x" + IDENTITY_USERNAME_ONE);
		IdmIdentityDto identityTwo = identityService.getByUsername("x" + IDENTITY_USERNAME_TWO);
		
		AccIdentityAccountFilter identityAccountFilterOne = new AccIdentityAccountFilter();
		identityAccountFilterOne.setIdentityId(identityOne.getId());
		Assert.assertEquals(0, identityAccoutnService.find(identityAccountFilterOne, null).getTotalElements());

		AccIdentityAccountFilter identityAccountFilterTwo = new AccIdentityAccountFilter();
		identityAccountFilterTwo.setIdentityId(identityTwo.getId());
		Assert.assertEquals(0, identityAccoutnService.find(identityAccountFilterTwo, null).getTotalElements());
		
		// change eav atttribute for identity two
		List<Serializable> list = new ArrayList<>();
		list.add("5");
		formService.saveValues(identityOne.getId(), IdmIdentity.class, EAV_ATTRIBUTE, list);
		
		// Start synchronization
		helper.startSynchronization(syncConfigCustom);
	
		//
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		// log.getSyncActionLogs();
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();
		Assert.assertEquals(2, actions.size()); // LINK and MISSING_ENTITY

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.MISSING_ENTITY == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(1, items.size());

		// Check state after sync
		Assert.assertEquals(0, identityAccoutnService.find(identityAccountFilterOne, null).getTotalElements());
		Assert.assertEquals(1, identityAccoutnService.find(identityAccountFilterTwo, null).getTotalElements());

		// Delete log
		syncLogService.delete(log);
	}
	
	@Test
	@Transactional
	/**
	 * Testing problem on Postgres, where cannot be save 0x00 to TEXT column.
	 * Problem solving converter between entity and DTO (StringToStringConverter)
	 */
	public void escape0x00FromStringTest() {
		String errorString0x00 = "\0x00";
		
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		SysSyncLogDto log = new SysSyncLogDto();
		log.setSynchronizationConfig(syncConfigCustom.getId());
		log.setLog(errorString0x00);
		log.setToken(errorString0x00);
		syncLogService.save(log);
		syncConfigCustom.setName(errorString0x00);
		syncConfigCustom.setToken(errorString0x00);
		syncConfigService.save(syncConfigCustom);
		
	}
	
	
	private AbstractSysSyncConfigDto setSyncConfigForEav(String configName) {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(configName);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		
		SysSystemMappingDto systemMapping = systemMappingService.get(syncConfigCustom.getSystemMapping());
		SysSystemDto system = systemService.get(schemaObjectClassService.get(systemMapping.getObjectClass()).getSystem());
		
		SysSystemMappingFilter mappingFilter = new SysSystemMappingFilter();
		mappingFilter.setEntityType(SystemEntityType.IDENTITY);
		mappingFilter.setSystemId(system.getId());
		mappingFilter.setOperationType(SystemOperationType.SYNCHRONIZATION);
		List<SysSystemMappingDto> mappings = systemMappingService.find(mappingFilter, null).getContent();
		Assert.assertEquals(1, mappings.size());
		SysSystemMappingDto mapping = mappings.get(0);
		
		SysSystemAttributeMappingFilter attributeMappingFilter = new SysSystemAttributeMappingFilter();
		attributeMappingFilter.setSystemMappingId(mapping.getId());

		List<SysSystemAttributeMappingDto> attributes = schemaAttributeMappingService.find(attributeMappingFilter, null)
				.getContent();
		
		// Set sync config
		SysSystemAttributeMappingDto eavAttribute = attributes.stream().filter(attribute -> {
			return attribute.getName().equals(EAV_ATTRIBUTE);
		}).findFirst().get();
		
		Assert.assertNotNull(eavAttribute);
		
		syncConfigCustom.setCorrelationAttribute(eavAttribute.getId());
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.IGNORE);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.LINK);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.IGNORE);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);
		syncConfigCustom.setReconciliation(true);
		syncConfigService.save(syncConfigCustom);
		
		return syncConfigCustom;
	}

	@Transactional
	public void changeResourceData() {
		// Change data on resource
		TestResource one = entityManager.find(TestResource.class, "x" + IDENTITY_USERNAME_ONE);
		one.setFirstname(ATTRIBUTE_VALUE_CHANGED);
		one.setEmail(IDENTITY_EMAIL_CORRECT_CHANGED);
		one.setEavAttribute(ATTRIBUTE_VALUE_CHANGED);
		TestResource two = entityManager.find(TestResource.class, "x" + IDENTITY_USERNAME_TWO);
		two.setLastname(ATTRIBUTE_VALUE_CHANGED);
		two.setEmail(IDENTITY_EMAIL_CORRECT_CHANGED);
		two.setEavAttribute(ATTRIBUTE_VALUE_CHANGED);
		entityManager.persist(one);
		entityManager.persist(two);
	}

	@Transactional
	public void changeResourceDataWrongEmail() {
		// Set wrong email to resource
		TestResource one = entityManager.find(TestResource.class, "x" + IDENTITY_USERNAME_ONE);
		one.setEmail(IDENTITY_EMAIL_WRONG);
		TestResource two = entityManager.find(TestResource.class, "x" + IDENTITY_USERNAME_TWO);
		two.setEmail(IDENTITY_EMAIL_WRONG);
		// Change data on resource
		entityManager.persist(one);
		entityManager.persist(two);
	}

	@Transactional
	public void addIdentityThreeToResourceData() {
		// Change data on resource
		// Insert data to testResource table
		TestResource resourceUser = new TestResource();
		resourceUser.setName("x" + IDENTITY_USERNAME_THREE);
		resourceUser.setFirstname(IDENTITY_USERNAME_THREE);
		resourceUser.setLastname(IDENTITY_USERNAME_THREE);
		resourceUser.setEmail(IDENTITY_EMAIL_CORRECT);
		resourceUser.setEavAttribute("3");
		entityManager.persist(resourceUser);
	}
	
	@Transactional
	public void setEmailOneToNull() {
		// Set null email to resource
		TestResource one = entityManager.find(TestResource.class, "x" + IDENTITY_USERNAME_ONE);
		one.setEmail(null);
		// Change data on resource
		entityManager.persist(one);
	}

	@Transactional
	public void initResourceData() {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM test_resource");
		q.executeUpdate();
		// Insert data to testResource table
		ZonedDateTime paste = ZonedDateTime.now().minusYears(1);
		ZonedDateTime future = paste.plusYears(2);
		
		TestResource resourceUserOne = new TestResource();
		resourceUserOne.setName("x" + IDENTITY_USERNAME_ONE);
		resourceUserOne.setFirstname(IDENTITY_USERNAME_ONE);
		resourceUserOne.setLastname(IDENTITY_USERNAME_ONE);
		resourceUserOne.setEmail(IDENTITY_EMAIL_CORRECT);
		resourceUserOne.setModified(paste);
		resourceUserOne.setEavAttribute("1");
		entityManager.persist(resourceUserOne);

		TestResource resourceUserTwo = new TestResource();
		resourceUserTwo.setName("x" + IDENTITY_USERNAME_TWO);
		resourceUserTwo.setFirstname(IDENTITY_USERNAME_TWO);
		resourceUserTwo.setLastname(IDENTITY_USERNAME_TWO);
		resourceUserTwo.setEmail(IDENTITY_EMAIL_CORRECT);
		resourceUserTwo.setModified(future);
		resourceUserTwo.setEavAttribute("2");
		entityManager.persist(resourceUserTwo);
	}

	@Transactional
	public void deleteAllResourceData() {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM test_resource");
		q.executeUpdate();
	}

	private void initData() {

		// create test system
		system = helper.createSystem("test_resource");

		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);

		// Create provisioning mapping
		SysSystemMappingDto systemMapping = new SysSystemMappingDto();
		systemMapping.setName("default_" + System.currentTimeMillis());
		systemMapping.setEntityType(SystemEntityType.IDENTITY);
		systemMapping.setOperationType(SystemOperationType.PROVISIONING);
		systemMapping.setObjectClass(objectClasses.get(0).getId());
		final SysSystemMappingDto provisioningMapping = systemMappingService.save(systemMapping);

		createMapping(system, provisioningMapping);

		// Create synchronization mapping
		SysSystemMappingDto syncSystemMapping = new SysSystemMappingDto();
		syncSystemMapping.setName("default_" + System.currentTimeMillis());
		syncSystemMapping.setEntityType(SystemEntityType.IDENTITY);
		syncSystemMapping.setOperationType(SystemOperationType.SYNCHRONIZATION);
		syncSystemMapping.setObjectClass(objectClasses.get(0).getId());
		final SysSystemMappingDto syncMapping = systemMappingService.save(syncSystemMapping);

		createMapping(system, syncMapping);

		initResourceData();
		syncConfigService.find(null).getContent().forEach(config -> {
			syncConfigService.delete(config);
		});
	}

	private void createMapping(SysSystemDto system, final SysSystemMappingDto entityHandlingResult) {
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());

		Page<SysSchemaAttributeDto> schemaAttributesPage = schemaAttributeService.find(schemaAttributeFilter, null);
		schemaAttributesPage.forEach(schemaAttr -> {
			if (ATTRIBUTE_NAME.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(true);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName("username");
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMapping);

			} else if ("firstname".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName("firstName");
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMapping);

			} else if ("lastname".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName("lastName");
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMapping);

			} else if (ATTRIBUTE_EMAIL.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName("email");
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMapping);

			} else if (IcConnectorFacade.PASSWORD_ATTRIBUTE_NAME.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName("password");
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMapping);

			} else if (SystemOperationType.SYNCHRONIZATION == entityHandlingResult.getOperationType()
					&& ATTRIBUTE_MODIFIED.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setEntityAttribute(false);
				attributeMapping.setExtendedAttribute(true);
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setIdmPropertyName(ATTRIBUTE_MODIFIED);
				attributeMapping.setName(ATTRIBUTE_MODIFIED);
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMapping);

			}  else if (schemaAttr.getName().equalsIgnoreCase(EAV_ATTRIBUTE)) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setExtendedAttribute(true);
				attributeMapping.setEntityAttribute(false);
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setIdmPropertyName(EAV_ATTRIBUTE);
				attributeMapping.setName(EAV_ATTRIBUTE);
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMapping);
			}
		});
	}

	private DefaultSynchronizationServiceTest getBean() {
		return applicationContext.getAutowireCapableBeanFactory().createBean(this.getClass());
	}
}
