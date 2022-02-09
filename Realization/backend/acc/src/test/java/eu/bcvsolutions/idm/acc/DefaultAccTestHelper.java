package eu.bcvsolutions.idm.acc;

import eu.bcvsolutions.idm.acc.dto.filter.AccUniformPasswordFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemGroupFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemMappingFilter;
import eu.bcvsolutions.idm.acc.service.api.AccUniformPasswordService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemGroupService;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleTreeNodeService;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;
import com.zaxxer.hikari.HikariDataSource;

import eu.bcvsolutions.idm.acc.domain.AccountType;
import eu.bcvsolutions.idm.acc.domain.OperationResultType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationActionType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysConnectorKeyDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncActionLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncItemLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncActionLogFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncConfigFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncItemLogFilter;
import eu.bcvsolutions.idm.acc.entity.TestResource;
import eu.bcvsolutions.idm.acc.scheduler.task.impl.SynchronizationSchedulableTaskExecutor;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.api.SynchronizationService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncActionLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncConfigService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncItemLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.acc.service.impl.DefaultSysSystemMappingService;
import eu.bcvsolutions.idm.core.api.config.flyway.IdmFlywayMigrationStrategy;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import joptsimple.internal.Strings;

/**
 * Acc / Provisioning test helper
 * 
 * @author Radek Tomiška
 * @author Vít Švanda
 */
@Primary
@Component("accTestHelper")
public class DefaultAccTestHelper extends eu.bcvsolutions.idm.test.api.DefaultTestHelper implements TestHelper {

	@Qualifier("entityManager")
	@Autowired private EntityManager entityManager;	
	@Autowired private SysSystemService systemService;
	@Autowired private SysSystemMappingService systemMappingService;
	@Autowired private SysSystemAttributeMappingService systemAttributeMappingService;
	@Autowired private SysSchemaAttributeService schemaAttributeService;
	@Autowired private SysRoleSystemService roleSystemService;
	@Autowired private FormService formService;
	@Autowired private DataSource dataSource;
	@Autowired private SysSystemEntityService systemEntityService;
	@Autowired private AccAccountService accountService;
	@Autowired private AccIdentityAccountService identityAccountService;
	@Autowired private DefaultSysSystemMappingService mappingService;
	@Autowired private ApplicationContext context;
	@Autowired private SysSyncConfigService syncConfigService;
	@Autowired private SysSyncItemLogService syncItemLogService;
	@Autowired private SysSyncActionLogService syncActionLogService;
	@Autowired private IdmRoleTreeNodeService roleTreeNodeService;
	@Autowired private IdmAutomaticRoleAttributeService automaticRoleAttributeService;
	@Autowired private AccUniformPasswordService uniformPasswordService;
	@Autowired private SysSystemGroupService systemGroupService;
	
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public TestResource findResource(String uid) {
		return entityManager.find(TestResource.class, uid);
	}
	
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public TestResource saveResource(TestResource testResource) {
		entityManager.persist(testResource);
		//
		return testResource;
	}
	
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deleteAllResourceData() {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM " + TestResource.TABLE_NAME);
		q.executeUpdate();
	}
	
	/**
	 * Create test system connected to same database (using configuration from dataSource)
	 * Generated system name will be used.
	 * 
	 * @return
	 */
	@Override
	public SysSystemDto createSystem(String tableName) {
		return createSystem(tableName, null);
	}
	
	/**
	 * 
	 * 
	 * @param tableName
	 * @param systemName
	 * @return
	 */
	@Override
	public SysSystemDto createSystem(String tableName, String systemName) {
		return this.createSystem(tableName, systemName, "status", "name");
	}
	
	/**
	 * 
	 * 
	 * @param tableName
	 * @param systemName
	 * @return
	 */
	@Override
	@SuppressWarnings("deprecation")
	public SysSystemDto createSystem(String tableName, String systemName, String statusColumnName, String keyColumnName) {
		// create owner
		SysSystemDto system = new SysSystemDto();
		system.setName(systemName == null ? tableName + "_" + System.currentTimeMillis() : systemName);

		system.setConnectorKey(new SysConnectorKeyDto(systemService.getTestConnectorKey()));

		system = systemService.save(system);

		IdmFormDefinitionDto savedFormDefinition = systemService.getConnectorFormDefinition(system.getConnectorInstance());

		List<IdmFormValueDto> values = new ArrayList<>();

		IdmFormValueDto jdbcUrlTemplate = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("jdbcUrlTemplate"));
		jdbcUrlTemplate.setValue(((HikariDataSource) dataSource).getJdbcUrl());
		values.add(jdbcUrlTemplate);
		IdmFormValueDto jdbcDriver = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("jdbcDriver"));
		jdbcDriver.setValue(((HikariDataSource) dataSource).getDriverClassName());
		values.add(jdbcDriver);

		IdmFormValueDto user = new IdmFormValueDto(savedFormDefinition.getMappedAttributeByCode("user"));
		user.setValue(((HikariDataSource) dataSource).getUsername());
		values.add(user);
		IdmFormValueDto password = new IdmFormValueDto(savedFormDefinition.getMappedAttributeByCode("password"));
		password.setValue(((HikariDataSource) dataSource).getPassword());
		values.add(password);
		IdmFormValueDto table = new IdmFormValueDto(savedFormDefinition.getMappedAttributeByCode("table"));
		table.setValue(tableName);
		values.add(table);
		if(!Strings.isNullOrEmpty(keyColumnName)) {
			IdmFormValueDto keyColumn = new IdmFormValueDto(
					savedFormDefinition.getMappedAttributeByCode("keyColumn"));
			keyColumn.setValue(keyColumnName);
			values.add(keyColumn);
		}
		IdmFormValueDto passwordColumn = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("passwordColumn"));
		passwordColumn.setValue("password");
		values.add(passwordColumn);
		IdmFormValueDto allNative = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("allNative"));
		allNative.setValue(true);
		values.add(allNative);
		IdmFormValueDto rethrowAllSQLExceptions = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("rethrowAllSQLExceptions"));
		rethrowAllSQLExceptions.setValue(true);
		values.add(rethrowAllSQLExceptions);
		if(!Strings.isNullOrEmpty(statusColumnName)) {
			IdmFormValueDto statusColumn = new IdmFormValueDto(
					savedFormDefinition.getMappedAttributeByCode("statusColumn"));
			statusColumn.setValue(statusColumnName);
			values.add(statusColumn);
		}
		IdmFormValueDto disabledStatusValue = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("disabledStatusValue"));
		disabledStatusValue.setValue("disabled");
		values.add(disabledStatusValue);
		IdmFormValueDto enabledStatusValue = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("enabledStatusValue"));
		enabledStatusValue.setValue("enabled");
		values.add(enabledStatusValue);
		IdmFormValueDto changeLogColumnValue = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("changeLogColumn"));
		changeLogColumnValue.setValue(null);
		values.add(changeLogColumnValue);
		
		formService.saveValues(system, savedFormDefinition, values);

		return system;
	}
	
	@Override
	public SysSystemDto createTestResourceSystem(boolean withMapping) {
		return createTestResourceSystem(withMapping, null);
	}
	
	@Override
	public SysSystemDto createTestResourceSystem(boolean withMapping, String systemName) {
		// create test system
		SysSystemDto system = createSystem(TestResource.TABLE_NAME, systemName);
		//
		if (withMapping) {
			createMapping(system);
		}
		//
		return system;
	}
	
	@Override
	public SysSystemMappingDto createMapping(SysSystemDto system) {
		//
		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);
		//
		SysSystemMappingDto systemMapping = new SysSystemMappingDto();
		systemMapping.setName("default_" + System.currentTimeMillis());
		systemMapping.setEntityType(SystemEntityType.IDENTITY);
		systemMapping.setOperationType(SystemOperationType.PROVISIONING);
		systemMapping.setObjectClass(objectClasses.get(0).getId());
		systemMapping = systemMappingService.save(systemMapping);

		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());
		
		Page<SysSchemaAttributeDto> schemaAttributesPage = schemaAttributeService.find(schemaAttributeFilter, null);
		for(SysSchemaAttributeDto schemaAttr : schemaAttributesPage) {
			if (ATTRIBUTE_MAPPING_NAME.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(true);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName(IdmIdentity_.username.getName());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			} else if (ATTRIBUTE_MAPPING_ENABLE.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(false);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName("disabled");
				attributeMapping.setTransformToResourceScript("return String.valueOf(!attributeValue);");
				attributeMapping.setTransformFromResourceScript("return String.valueOf(attributeValue);");
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			} else if (ATTRIBUTE_MAPPING_PASSWORD.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName("password");
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setPasswordAttribute(true);
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			} else if (ATTRIBUTE_MAPPING_FIRSTNAME.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName(IdmIdentity_.firstName.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			} else if (ATTRIBUTE_MAPPING_LASTNAME.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName(IdmIdentity_.lastName.getName());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			} else if (ATTRIBUTE_MAPPING_EMAIL.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName(IdmIdentity_.email.getName());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			}
		}
		return systemMapping;
	}
	
	@Override
	public SysSystemMappingDto getDefaultMapping(SysSystemDto system) {
		Assert.notNull(system, "System is required to get mapping.");
		//
		return getDefaultMapping(system.getId());
	}
	
	@Override
	public SysSystemMappingDto getDefaultMapping(UUID systemId) {
		List<SysSystemMappingDto> mappings = systemMappingService.findBySystemId(systemId, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		if(mappings.isEmpty()) {
			throw new CoreException(String.format("Default mapping for system[%s] not found", systemId));
		}
		//
		return mappings.get(0);
	}
	
	@Override
	public SysRoleSystemDto createRoleSystem(IdmRoleDto role, SysSystemDto system) {
		SysRoleSystemDto roleSystem = new SysRoleSystemDto();
		roleSystem.setRole(role.getId());
		roleSystem.setSystem(system.getId());
		// default mapping
		List<SysSystemMappingDto> mappings = systemMappingService.findBySystem(system, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		// required ...
		roleSystem.setSystemMapping(mappings.get(0).getId());
		//
		return roleSystemService.save(roleSystem);
	}
	
	@Override
	public SysSystemEntityDto createSystemEntity(SysSystemDto system) {
		SysSystemEntityDto systemEntity = new SysSystemEntityDto(createName(), SystemEntityType.IDENTITY);
		systemEntity.setSystem(system.getId());
		systemEntity.setWish(true);
		return systemEntityService.save(systemEntity);
	}

	@Override
	public AccIdentityAccountDto createIdentityAccount(SysSystemDto system, IdmIdentityDto identity) {
		AccAccountDto account = new AccAccountDto();
		account.setSystem(system.getId());
		account.setUid(identity.getUsername());
		account.setAccountType(AccountType.PERSONAL);
		account.setEntityType(SystemEntityType.IDENTITY);
		account = accountService.save(account);

		AccIdentityAccountDto accountIdentity = new AccIdentityAccountDto();
		accountIdentity.setIdentity(identity.getId());
		accountIdentity.setOwnership(true);
		accountIdentity.setAccount(account.getId());

		return identityAccountService.save(accountIdentity);
	}

	@Override
	public SysSystemMappingDto createMappingSystem(SystemEntityType type, SysSchemaObjectClassDto objectClass) {
		// system mapping
		SysSystemMappingDto mapping = new SysSystemMappingDto();
		mapping.setName(createName());
		mapping.setEntityType(type);
		mapping.setObjectClass(objectClass.getId());
		mapping.setOperationType(SystemOperationType.SYNCHRONIZATION);
		//
		return mappingService.save(mapping);
	}

	@Override
	public void startSynchronization(AbstractSysSyncConfigDto config) {
		Assert.notNull(config, "Sync config is required to be start.");
		SynchronizationSchedulableTaskExecutor lrt = context.getAutowireCapableBeanFactory()
		.createBean(SynchronizationSchedulableTaskExecutor.class);
		lrt.init(ImmutableMap.of(SynchronizationService.PARAMETER_SYNCHRONIZATION_ID, config.getId().toString()));
		lrt.process();
	}
	
	@Override
	public SysSyncLogDto checkSyncLog(AbstractSysSyncConfigDto config, SynchronizationActionType actionType, int count,
			OperationResultType resultType) {
		SysSyncConfigFilter logFilter = new SysSyncConfigFilter();
		logFilter.setId(config.getId());
		logFilter.setIncludeLastLog(Boolean.TRUE);
		List<AbstractSysSyncConfigDto> configs = syncConfigService.find(logFilter, null).getContent();
		assertEquals(1, configs.size());
		SysSyncLogDto log = configs.get(0).getLastSyncLog();
		if (actionType == null) {
			return log;
		}

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			if (resultType == null) {
				return actionType == action.getSyncAction();
			}
			return actionType == action.getSyncAction() && action.getOperationResult() == resultType;
		}).findFirst().get();

		if (resultType != null) {
			assertEquals(resultType, actionLog.getOperationResult());
		}
		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		assertEquals(count, items.size());

		return log;
	}
	
	/**
	 * Schema is generated in lower case for postgresql.
	 * 
	 * @param columnName
	 * @return
	 */
	@Override
	public String getSchemaColumnName(String columnName) {
		if (columnName.equals(ATTRIBUTE_MAPPING_NAME)
				|| columnName.equals(ATTRIBUTE_MAPPING_ENABLE)
				|| columnName.equals(ATTRIBUTE_MAPPING_PASSWORD)) {
			// reserved names
			return columnName;
		}
		//
        String dbName = getDatabaseName();
        if (dbName.equals(IdmFlywayMigrationStrategy.POSTGRESQL_DBNAME)) {
			return columnName.toLowerCase();
		}
		//
		return columnName;
	}


	/**
	 * Manual delete of all automatic roles, sync, mappings. Because previous tests didn't make a delete well.
	 */
	@Override
	public void cleaner() {
		// Delete all automatic roles.
		roleTreeNodeService.deleteAll(roleTreeNodeService.find(new IdmRoleTreeNodeFilter(), null).getContent());
		automaticRoleAttributeService.deleteAll(automaticRoleAttributeService.find(new IdmRoleTreeNodeFilter(), null).getContent());
		// Delete all syncs.
		syncConfigService.deleteAll(syncConfigService.find(new SysSyncConfigFilter(), null).getContent());
		// Delete all groups.
		systemGroupService.deleteAll(systemGroupService.find(new SysSystemGroupFilter(), null));
		// Delete all mappings.
		systemMappingService.deleteAll(systemMappingService.find(new SysSystemMappingFilter(), null).getContent());
		// Delete all uniform password definitions.
		uniformPasswordService.deleteAll(uniformPasswordService.find(new AccUniformPasswordFilter(), null).getContent());

	}
}
