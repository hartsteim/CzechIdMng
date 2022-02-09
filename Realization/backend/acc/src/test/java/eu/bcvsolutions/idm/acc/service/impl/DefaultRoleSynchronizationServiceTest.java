package eu.bcvsolutions.idm.acc.service.impl;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
import eu.bcvsolutions.idm.acc.domain.ReconciliationMissingAccountActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationLinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationMissingEntityActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationUnlinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncActionLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncItemLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncRoleConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncActionLogFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncConfigFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncItemLogFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncLogFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemAttributeMappingFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemMappingFilter;
import eu.bcvsolutions.idm.acc.entity.TestRoleResource;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncActionLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncConfigService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncItemLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.domain.RoleType;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.entity.IdmRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRole_;
import eu.bcvsolutions.idm.ic.domain.IcFilterOperationType;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;


/**
 * Role synchronization and provisioning tests
 * 
 * @author Svanda
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Rollback(false)
public class DefaultRoleSynchronizationServiceTest extends AbstractIntegrationTest {

	private static final String SYNC_CONFIG_NAME = "syncConfigNameRole";
	private static final String SYSTEM_NAME = "systemRole";
	private static final String ATTRIBUTE_NAME = "__NAME__";
	private static final String CHANGED = "changed";
	private static final String ROLE_NAME_TEN = "roleName10";
	private static final String DATE_TABLE_CONNECTOR_FORMAT = "yyyy-MM-dd HH:mm:ss";
	
	@Autowired
	private TestHelper helper;
	@Autowired
	private SysSystemService systemService;
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
	private IdmRoleService roleService;
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
		mappingFilter.setEntityType(SystemEntityType.ROLE);
		mappingFilter.setSystemId(system.getId());
		mappingFilter.setOperationType(SystemOperationType.SYNCHRONIZATION);
		List<SysSystemMappingDto> mappings = systemMappingService.find(mappingFilter, null).getContent();
		Assert.assertEquals(1, mappings.size());
		SysSystemMappingDto mapping = mappings.get(0);
		SysSystemAttributeMappingFilter attributeMappingFilter = new SysSystemAttributeMappingFilter();
		attributeMappingFilter.setSystemMappingId(mapping.getId());

		List<SysSystemAttributeMappingDto> attributes = schemaAttributeMappingService.find(attributeMappingFilter, null)
				.getContent();
		SysSystemAttributeMappingDto uidAttribute = attributes.stream().filter(attribute -> {
			return attribute.isUid();
		}).findFirst().orElse(null);
		
		SysSystemAttributeMappingDto tokenAttribute = attributes.stream().filter(attribute -> {
			return "changed".equals(attribute.getIdmPropertyName());
		}).findFirst().orElse(null);


		// Create default synchronization config
		AbstractSysSyncConfigDto syncConfigCustom = new SysSyncRoleConfigDto();
		syncConfigCustom.setReconciliation(false);
		syncConfigCustom.setCustomFilter(true);
		syncConfigCustom.setSystemMapping(mapping.getId());
		syncConfigCustom.setCorrelationAttribute(uidAttribute.getId());
		syncConfigCustom.setTokenAttribute(tokenAttribute.getId());
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
		Assert.assertEquals(5, items.size());
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setProperty(IdmRole_.code.getName());
		roleFilter.setValue("1");
		
		Assert.assertEquals(1, roleService.find(roleFilter, null).getTotalElements());
		
		roleFilter.setValue("2");
		Assert.assertEquals(1, roleService.find(roleFilter, null).getTotalElements());
		
		roleFilter.setValue("3");
		Assert.assertEquals(1, roleService.find(roleFilter, null).getTotalElements());
		
		roleFilter.setValue("4");
		Assert.assertEquals(1, roleService.find(roleFilter, null).getTotalElements());
		
		roleFilter.setValue("5");
		Assert.assertEquals(1, roleService.find(roleFilter, null).getTotalElements());

		// Delete log
		syncLogService.delete(log);
	}

	@Test
	public void doStartSyncB_Linked_doEntityUpdate() {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		//Change node code to changed
		this.getBean().changeOne();

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
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setProperty(IdmRole_.code.getName());
		roleFilter.setValue("1");
		Assert.assertEquals("1", roleService.find(roleFilter, null).getContent().get(0).getDescription());

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
		Assert.assertEquals(5, items.size());

		// Check state after sync
		Assert.assertEquals(CHANGED, roleService.find(roleFilter, null).getContent().get(0).getDescription());

		// Delete log
		syncLogService.delete(log);

	}
	
	@Test
	public void doStartSyncB_MissingAccount_DeleteEntity() {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		//Remove node code to changed
		this.getBean().removeOne();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));

		// Set sync config
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.IGNORE);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.IGNORE);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.IGNORE);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.DELETE_ENTITY);
		syncConfigCustom.setReconciliation(true);
		syncConfigService.save(syncConfigCustom);

		// Check state before sync
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setProperty(IdmRole_.code.getName());
		roleFilter.setValue("1");
		IdmRoleDto roleOne = roleService.find(roleFilter, null).getContent().get(0);
		Assert.assertNotNull(roleOne);
		
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
			return SynchronizationActionType.DELETE_ENTITY == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(1, items.size());

		// Check state after sync
		roleOne = roleService.get(roleOne.getId());
		Assert.assertNull(roleOne);

		// Delete log
		syncLogService.delete(log);

	}
	
	@Test
	public void doStartSyncC_filterByToken() {
		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();

		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setProperty(IdmRole_.code.getName());
		roleFilter.setValue("3");
		IdmRoleDto roleThree = roleService.find(roleFilter, null).getContent().get(0);
		Assert.assertNotNull(roleThree);
		IdmFormValueDto changedRole = (IdmFormValueDto) formService.getValues(roleThree.getId(), IdmRole.class, "changed").get(0);
		Assert.assertNotNull(changedRole);

		// Set sync config
		syncConfigCustom.setReconciliation(false);
		syncConfigCustom.setCustomFilter(true);
		syncConfigCustom.setFilterOperation(IcFilterOperationType.GREATER_THAN);
		syncConfigCustom.setFilterAttribute(syncConfigCustom.getTokenAttribute());
		syncConfigCustom.setToken((String)changedRole.getValue());
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UPDATE_ENTITY);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.IGNORE);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.IGNORE);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);
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

		SysSyncActionLogDto createEntityActionLog = actions.stream().filter(action -> {
			return SynchronizationActionType.UPDATE_ENTITY == action.getSyncAction();
		}).findFirst().get();

		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(createEntityActionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(2, items.size());
	
		SysSyncItemLogDto item = items.stream().filter(logitem -> {
			return "4".equals(logitem.getIdentification());
		}).findFirst().orElse(null);
		Assert.assertNotNull("Log for role 4 must exist!", item);
		
		item = items.stream().filter(logitem -> {
			return "5".equals(logitem.getIdentification());
		}).findFirst().orElse(null);
		Assert.assertNotNull("Log for role 5 must exist!", item);
		
		// Delete log
		syncLogService.delete(log);
	}
	
	@Test
	@Transactional
	public void provisioningA_CreateAccount_withOutMapping() {
		
		// Delete all resource data
		this.deleteAllResourceData();
		
		
		// Create role in IDM tree
		IdmRoleDto roleTen = new IdmRoleDto();
		roleTen.setCode(ROLE_NAME_TEN);
		roleTen.setPriority(2);
		roleTen = roleService.save(roleTen);
		
		// Check state before provisioning
		TestRoleResource one = entityManager.find(TestRoleResource.class, ROLE_NAME_TEN);
		Assert.assertNull(one);
	}
	
	@Test
	public void provisioningB_CreateAccounts() {

		IdmRoleFilter filter = new IdmRoleFilter();
		filter.setProperty(IdmRole_.code.getName());
		filter.setValue(ROLE_NAME_TEN);

		IdmRoleDto roleTen = roleService.find(filter, null).getContent().get(0);
		Assert.assertNotNull(roleTen);

		// Check state before provisioning
		TestRoleResource ten = entityManager.find(TestRoleResource.class, ROLE_NAME_TEN);
		Assert.assertNull(ten);

		// Create mapping for provisioning
		SysSystemMappingDto mapping = this.createProvisionigMapping();

		// Save IDM role (must invoke provisioning)
		roleService.save(roleTen);
		
		// Check state before provisioning
		ten = entityManager.find(TestRoleResource.class, ROLE_NAME_TEN);
		Assert.assertNotNull(ten);
		// Delete role mapping
		systemMappingService.delete(mapping);
	}
	
	@Test
	public void provisioningD_UpdateAccount() {
		
		// Create mapping for provisioning
		SysSystemMappingDto mapping = this.createProvisionigMapping();
		
		IdmRoleFilter filter = new IdmRoleFilter();
		filter.setProperty(IdmRole_.code.getName());
		filter.setValue(ROLE_NAME_TEN);

		IdmRoleDto roleTen = roleService.find(filter, null).getContent().get(0);
		Assert.assertNotNull(roleTen);
		
		// Check state before provisioning
		TestRoleResource ten = entityManager.find(TestRoleResource.class, ROLE_NAME_TEN);
		Assert.assertNotNull(ten);
		Assert.assertEquals(2, ten.getPriority());
		
		roleTen.setPriority(10);
		
		// Save IDM changed node (must invoke provisioning)
		roleService.save(roleTen);
		
		// Check state after provisioning
		ten = entityManager.find(TestRoleResource.class, ROLE_NAME_TEN);
		Assert.assertNotNull(ten);
		Assert.assertEquals(10, ten.getPriority());
		
		// Delete role mapping
		systemMappingService.delete(mapping);
	}
	
	@Test
	public void provisioningD_UpdateAccount_Extended_Attribute() {
		
		// Create mapping for provisioning
		SysSystemMappingDto mapping = this.createProvisionigMapping();
		
		IdmRoleFilter filter = new IdmRoleFilter();
		filter.setProperty(IdmRole_.code.getName());
		filter.setValue(ROLE_NAME_TEN);

		IdmRoleDto roleTen = roleService.find(filter, null).getContent().get(0);
		Assert.assertNotNull(roleTen);
		Assert.assertTrue(formService.getValues(roleTen.getId(), IdmRole.class, "changed").isEmpty());
		
		// Check state before provisioning
		TestRoleResource ten = entityManager.find(TestRoleResource.class, ROLE_NAME_TEN);
		Assert.assertNotNull(ten);
		Assert.assertEquals(null, ten.getModified());
	
		// Create extended attribute
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TABLE_CONNECTOR_FORMAT);
		ZonedDateTime now = ZonedDateTime.now();
		formService.saveValues(
				roleTen.getId(), 
				IdmRole.class, 
				"changed", ImmutableList.of(now.withZoneSameInstant(ZoneOffset.UTC).format(formatter)));
		
		// Save IDM changed node (must invoke provisioning)
		roleService.save(roleTen);
		// Check state after provisioning
		ten = entityManager.find(TestRoleResource.class, ROLE_NAME_TEN);
		Assert.assertNotNull(ten);
		Assert.assertEquals(now.format(formatter), ten.getModified().format(formatter));
		
		// Delete role mapping
		systemMappingService.delete(mapping);
	}
	
	@Test
	public void provisioningF_DeleteAccount() {
		
		// Create mapping for provisioning
		SysSystemMappingDto mapping = this.createProvisionigMapping();
		
		IdmRoleFilter filter = new IdmRoleFilter();
		filter.setProperty(IdmRole_.code.getName());
		filter.setValue(ROLE_NAME_TEN);

		IdmRoleDto roleTen = roleService.find(filter, null).getContent().get(0);
		Assert.assertNotNull(roleTen);
		
		TestRoleResource ten = entityManager.find(TestRoleResource.class, ROLE_NAME_TEN);
		Assert.assertNotNull(ten);
		
		// Delete IDM role (must invoke provisioning)
		roleService.delete(roleTen);
		
		ten = entityManager.find(TestRoleResource.class, ROLE_NAME_TEN);
		Assert.assertNull(ten);
		
		// Delete role mapping
		systemMappingService.delete(mapping);
	}

	@Transactional
	public void deleteAllResourceData() {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM test_role_resource");
		q.executeUpdate();
	}

	private SysSystemMappingDto createProvisionigMapping() {

		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setName(SYNC_CONFIG_NAME);
		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.find(configFilter, null).getContent();
		
		Assert.assertEquals(1, syncConfigs.size());
		AbstractSysSyncConfigDto syncConfigCustom = syncConfigs.get(0);
	
		SysSystemMappingDto systemMappingSync = systemMappingService.get(syncConfigCustom.getSystemMapping());
		
		// Create provisioning mapping
		SysSystemMappingDto systemMapping = new SysSystemMappingDto();
		systemMapping.setName("default_" + System.currentTimeMillis());
		systemMapping.setEntityType(SystemEntityType.ROLE);
		systemMapping.setOperationType(SystemOperationType.PROVISIONING);
		systemMapping.setObjectClass(systemMappingSync.getObjectClass());
		final SysSystemMappingDto syncMapping = systemMappingService.save(systemMapping);

		createMapping(systemService.get(schemaObjectClassService.get(systemMappingSync.getObjectClass()).getSystem()),
				syncMapping);
		return syncMapping;
	}
	
	private void initData() {

		// create test system
		system = helper.createSystem("test_role_resource");
		system.setName(SYSTEM_NAME);
		system = systemService.save(system);

		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);
		
		// Create synchronization mapping
		SysSystemMappingDto syncSystemMapping = new SysSystemMappingDto();
		syncSystemMapping.setName("default_" + System.currentTimeMillis());
		syncSystemMapping.setEntityType(SystemEntityType.ROLE);
		syncSystemMapping.setOperationType(SystemOperationType.SYNCHRONIZATION);
		syncSystemMapping.setObjectClass(objectClasses.get(0).getId());
		final SysSystemMappingDto syncMapping = systemMappingService.save(syncSystemMapping);

		createMapping(system, syncMapping);
		initRoleData();
		
		syncConfigService.find(null).getContent().forEach(config -> {
			syncConfigService.delete(config);
		});

	}
	
	private void initRoleData(){
		deleteAllResourceData();
		ZonedDateTime now = ZonedDateTime.now();
		entityManager.persist(this.createRole("1", RoleType.SYSTEM.name(), now.plusHours(1), 0));
		entityManager.persist(this.createRole("2", RoleType.SYSTEM.name(), now.plusHours(2), 0));
		entityManager.persist(this.createRole("3", RoleType.SYSTEM.name(), now.plusHours(3), 0));
		entityManager.persist(this.createRole("4", RoleType.SYSTEM.name(), now.plusHours(4), 1));
		entityManager.persist(this.createRole("5", RoleType.SYSTEM.name(), now.plusHours(5), 1));

	}
	
	private TestRoleResource createRole(String code, String type, ZonedDateTime changed, int priority){
		TestRoleResource role = new TestRoleResource();
		role.setType(type);
		role.setName(code);
		role.setPriority(priority);
		role.setModified(changed);
		role.setDescription(code);
		return role;
	}
	
	@Transactional
	public void changeOne(){
		TestRoleResource one = entityManager.find(TestRoleResource.class, "1");
		one.setDescription(CHANGED);
		entityManager.persist(one);
	}
	
	@Transactional
	public void removeOne(){
		TestRoleResource one = entityManager.find(TestRoleResource.class, "1");
		entityManager.remove(one);
	}
	

	private void createMapping(SysSystemDto system, final SysSystemMappingDto entityHandlingResult) {
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());

		Page<SysSchemaAttributeDto> schemaAttributesPage = schemaAttributeService.find(schemaAttributeFilter, null);
		schemaAttributesPage.forEach(schemaAttr -> {
			if (ATTRIBUTE_NAME.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeHandlingName = new SysSystemAttributeMappingDto();
				attributeHandlingName.setUid(true);
				attributeHandlingName.setEntityAttribute(true);
				attributeHandlingName.setIdmPropertyName("name");
				attributeHandlingName.setName(schemaAttr.getName());
				attributeHandlingName.setSchemaAttribute(schemaAttr.getId());
				// For provisioning .. we need create UID
				attributeHandlingName.setTransformToResourceScript("return entity.getName();");
				attributeHandlingName.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeHandlingName);

			} else if ("TYPE".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeHandlingName = new SysSystemAttributeMappingDto();
				attributeHandlingName.setIdmPropertyName("roleType");
				attributeHandlingName.setEntityAttribute(true);
				attributeHandlingName.setSchemaAttribute(schemaAttr.getId());
				attributeHandlingName.setName(schemaAttr.getName());
				attributeHandlingName.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeHandlingName);
			
			} else if ("PRIORITY".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeHandlingName = new SysSystemAttributeMappingDto();
				attributeHandlingName.setIdmPropertyName("priority");
				attributeHandlingName.setEntityAttribute(true);
				attributeHandlingName.setSchemaAttribute(schemaAttr.getId());
				attributeHandlingName.setName(schemaAttr.getName());
				attributeHandlingName.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeHandlingName);

			} else if ("APPROVE_REMOVE".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeHandlingName = new SysSystemAttributeMappingDto();
				attributeHandlingName.setIdmPropertyName("approveRemove");
				attributeHandlingName.setName(schemaAttr.getName());
				attributeHandlingName.setEntityAttribute(true);
				attributeHandlingName.setSchemaAttribute(schemaAttr.getId());
				attributeHandlingName.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeHandlingName);

			} else if ("MODIFIED".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeHandlingName = new SysSystemAttributeMappingDto();
				attributeHandlingName.setIdmPropertyName("changed");
				attributeHandlingName.setName(schemaAttr.getName());
				attributeHandlingName.setEntityAttribute(false);
				attributeHandlingName.setExtendedAttribute(true);
				attributeHandlingName.setSchemaAttribute(schemaAttr.getId());
				attributeHandlingName.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeHandlingName);
	
			} else if ("DESCRIPTION".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeHandlingName = new SysSystemAttributeMappingDto();
				attributeHandlingName.setIdmPropertyName("description");
				attributeHandlingName.setName(schemaAttr.getName());
				attributeHandlingName.setEntityAttribute(true);;
				attributeHandlingName.setSchemaAttribute(schemaAttr.getId());
				attributeHandlingName.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeHandlingName);
	
			}
		});
	}

	private DefaultRoleSynchronizationServiceTest getBean() {
		return applicationContext.getAutowireCapableBeanFactory().createBean(this.getClass());
	}
}
