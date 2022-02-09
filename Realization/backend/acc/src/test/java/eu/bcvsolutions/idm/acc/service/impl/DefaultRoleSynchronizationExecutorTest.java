package eu.bcvsolutions.idm.acc.service.impl;

import com.google.common.collect.Sets;
import eu.bcvsolutions.idm.acc.TestHelper;
import eu.bcvsolutions.idm.acc.domain.OperationResultType;
import eu.bcvsolutions.idm.acc.domain.ReconciliationMissingAccountActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationLinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationMissingEntityActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationUnlinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.AccRoleAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncIdentityConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncRoleConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccRoleAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncLogFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemAttributeMappingFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemMappingFilter;
import eu.bcvsolutions.idm.acc.entity.SysSyncConfig_;
import eu.bcvsolutions.idm.acc.entity.SysSystemMapping_;
import eu.bcvsolutions.idm.acc.entity.TestResource;
import eu.bcvsolutions.idm.acc.entity.TestRoleResource;
import eu.bcvsolutions.idm.acc.event.processor.MsAdSyncMappingRoleAutoAttributesProcessor;
import eu.bcvsolutions.idm.acc.service.api.AccRoleAccountService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncConfigService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.bulk.action.dto.IdmBulkActionDto;
import eu.bcvsolutions.idm.core.api.config.domain.EventConfiguration;
import eu.bcvsolutions.idm.core.api.domain.IdmScriptCategory;
import eu.bcvsolutions.idm.core.api.domain.RoleType;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmScriptDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmScriptFilter;
import eu.bcvsolutions.idm.core.api.event.processor.RoleProcessor;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmScriptService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.bulk.action.impl.role.RoleDeleteBulkAction;
import eu.bcvsolutions.idm.core.model.entity.IdmRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogueRole_;
import eu.bcvsolutions.idm.core.script.evaluator.AbstractScriptEvaluator;
import eu.bcvsolutions.idm.test.api.AbstractBulkActionTest;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.plugin.core.OrderAwarePluginRegistry;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.transaction.annotation.Transactional;


/**
 * Role/Group synchronization tests
 *
 * @author Vít Švanda
 * @since 11.0.1
 *
 */
public class DefaultRoleSynchronizationExecutorTest extends AbstractBulkActionTest {

	private static final String ATTRIBUTE_NAME = "__NAME__";
	private static final String CHANGED = "changed";

	@Autowired
	private TestHelper helper;
	@Autowired
	private SysSystemService systemService;
	@Autowired
	private SysSystemMappingService systemMappingService;
	@Autowired
	private SysSystemAttributeMappingService schemaAttributeMappingService;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	@Autowired
	private SysSyncConfigService syncConfigService;
	@Autowired
	private SysSyncLogService syncLogService;
	@Qualifier("entityManager")
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private AccRoleAccountService roleAccountService;
	@Autowired
	private SysRoleSystemService roleSystemService;
	@Autowired
	private SysRoleSystemAttributeService roleSystemAttributeService;
	@Autowired
	private SysSystemAttributeMappingService attributeMappingService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private IdmScriptService scriptService;
	@Autowired
	private IdmRoleCatalogueRoleService roleCatalogueRoleService;
	@Autowired
	private IdmRoleCatalogueService roleCatalogueService;
	@Autowired
	private List<AbstractScriptEvaluator> evaluators;
	private PluginRegistry<AbstractScriptEvaluator, IdmScriptCategory> pluginExecutors;

	@Before
	public void init() {
		loginAsAdmin();
		//
		getHelper().disableAsynchronousProcessing();
	}

	@After
	public void logout() {
		super.logout();
	}

	@Test
	public void testSyncRoles() {
		AbstractSysSyncConfigDto syncConfigCustom = createSyncConfig();
		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		Assert.assertTrue(syncConfigCustom instanceof SysSyncRoleConfigDto);
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

		SysSystemMappingDto systemMappingDto = DtoUtils.getEmbedded(syncConfigCustom, SysSyncConfig_.systemMapping, SysSystemMappingDto.class);
		SysSchemaObjectClassDto schemaObjectClassDto = DtoUtils.getEmbedded(systemMappingDto, SysSystemMapping_.objectClass, SysSchemaObjectClassDto.class);
		UUID systemId = schemaObjectClassDto.getSystem();
		Assert.assertNotNull(systemId);

		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.CREATE_ENTITY, 5, OperationResultType.SUCCESS);
		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setSystemId(systemId);
		List<AccRoleAccountDto> roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		roleAccountDtos.forEach(roleAccountDto -> {
			SysRoleSystemFilter roleSystemFilter = new SysRoleSystemFilter();
			roleSystemFilter.setRoleId(roleAccountDto.getRole());
			List<SysRoleSystemDto> roleSystemDtos = roleSystemService.find(roleSystemFilter, null).getContent();
			Assert.assertTrue(roleSystemDtos.isEmpty());
		});

		// Delete a log.
		syncLogService.delete(log);
		// Delete roles.
		roleAccountDtos.forEach(roleAccountDto -> {
			roleService.delete(roleService.get(roleAccountDto.getRole()));
		});
		// Delete sync.
		syncConfigService.delete(syncConfigCustom);
		// Delete system.
		systemService.delete(systemService.get(systemId));
	}

	@Test
	public void testSyncRolesMembership() {
		AbstractSysSyncConfigDto syncConfigCustom = createSyncConfig();
		SysSystemDto userSystem = helper.createTestResourceSystem(true);
		List<SysSystemMappingDto> userSystemMappings = systemMappingService.findBySystem(userSystem, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		Assert.assertNotNull(userSystemMappings);
		Assert.assertEquals(1, userSystemMappings.size());
		SysSystemMappingDto userMappingDto = userSystemMappings.get(0);
		// Switch to the sync.
		userMappingDto.setOperationType(SystemOperationType.SYNCHRONIZATION);
		userMappingDto = systemMappingService.save(userMappingDto);

		List<SysSystemAttributeMappingDto> attributeMappingDtos = schemaAttributeMappingService.findBySystemMapping(userMappingDto);
		SysSystemAttributeMappingDto userEmailAttribute = attributeMappingDtos.stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase(TestHelper.ATTRIBUTE_MAPPING_EMAIL))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(userEmailAttribute);

		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		Assert.assertTrue(syncConfigCustom instanceof SysSyncRoleConfigDto);
		SysSyncRoleConfigDto roleConfigDto = (SysSyncRoleConfigDto) syncConfigCustom;

		SysSystemMappingDto systemMappingDto = DtoUtils.getEmbedded(syncConfigCustom, SysSyncConfig_.systemMapping, SysSystemMappingDto.class);
		SysSchemaObjectClassDto schemaObjectClassDto = DtoUtils.getEmbedded(systemMappingDto, SysSystemMapping_.objectClass, SysSchemaObjectClassDto.class);
		UUID roleSystemId = schemaObjectClassDto.getSystem();
		Assert.assertNotNull(roleSystemId);
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(roleSystemId);
		schemaAttributeFilter.setObjectClassId(schemaObjectClassDto.getId());

		SysSchemaAttributeDto schemaAttributeDto = schemaAttributeService.find(schemaAttributeFilter, null).getContent().stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase("name"))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(schemaAttributeDto);

		SysSystemDto roleSystemDto = new SysSystemDto();
		roleSystemDto.setId(roleSystemId);
		List<SysSystemMappingDto> roleSystemMappings = systemMappingService.findBySystem(roleSystemDto, SystemOperationType.SYNCHRONIZATION, SystemEntityType.ROLE);
		Assert.assertNotNull(roleSystemMappings);
		Assert.assertEquals(1, roleSystemMappings.size());
		SysSystemMappingDto roleMappingDto = roleSystemMappings.get(0);
		// Create mapping attribute for get ID of role.
		SysSystemAttributeMappingDto roleIdAttribute = new SysSystemAttributeMappingDto();
		roleIdAttribute.setEntityAttribute(true);
		roleIdAttribute.setUid(false);
		roleIdAttribute.setSystemMapping(roleMappingDto.getId());
		roleIdAttribute.setExtendedAttribute(false);
		roleIdAttribute.setIdmPropertyName(RoleSynchronizationExecutor.ROLE_MEMBERSHIP_ID_FIELD);
		roleIdAttribute.setSchemaAttribute(schemaAttributeDto.getId());
		roleIdAttribute.setName(helper.createName());
		attributeMappingService.save(roleIdAttribute);

		// Enable membership and use the user system.
		roleConfigDto.setMembershipSwitch(true);
		roleConfigDto.setMemberSystemMapping(userMappingDto.getId());
		roleConfigDto.setMemberOfAttribute(userEmailAttribute.getId());
		syncConfigCustom = syncConfigService.save(roleConfigDto);

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

		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.CREATE_ENTITY, 5, OperationResultType.SUCCESS);
		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setSystemId(roleSystemId);
		List<AccRoleAccountDto> roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		roleAccountDtos.forEach(roleAccountDto -> {
			SysRoleSystemFilter roleSystemFilter = new SysRoleSystemFilter();
			roleSystemFilter.setRoleId(roleAccountDto.getRole());
			List<SysRoleSystemDto> roleSystemDtos = roleSystemService.find(roleSystemFilter, null).getContent();
			Assert.assertEquals(1, roleSystemDtos.size());
			SysRoleSystemDto roleSystem = roleSystemDtos.get(0);
			// Check mapping attribute (should be email).
			SysRoleSystemAttributeFilter roleSystemAttributeFilter = new SysRoleSystemAttributeFilter();
			roleSystemAttributeFilter.setRoleSystemId(roleSystem.getId());
			List<SysRoleSystemAttributeDto> roleSystemAttributeDtos = roleSystemAttributeService.find(roleSystemAttributeFilter, null).getContent();
			Assert.assertEquals(1, roleSystemAttributeDtos.size());
			Assert.assertEquals(userEmailAttribute.getId(), roleSystemAttributeDtos.get(0).getSystemAttributeMapping());
		});
		cleanAfterTest(syncConfigCustom, roleSystemId, log, roleAccountDtos);
	}

	@Test
	public void testSyncDeleteRolesMembership() {
		AbstractSysSyncConfigDto syncConfigCustom = createSyncConfig();
		SysSystemDto userSystem = helper.createTestResourceSystem(true);
		List<SysSystemMappingDto> userSystemMappings = systemMappingService.findBySystem(userSystem, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		Assert.assertNotNull(userSystemMappings);
		Assert.assertEquals(1, userSystemMappings.size());
		SysSystemMappingDto userMappingDto = userSystemMappings.get(0);
		// Switch to the sync.
		userMappingDto.setOperationType(SystemOperationType.SYNCHRONIZATION);
		userMappingDto = systemMappingService.save(userMappingDto);

		List<SysSystemAttributeMappingDto> attributeMappingDtos = schemaAttributeMappingService.findBySystemMapping(userMappingDto);
		SysSystemAttributeMappingDto userEmailAttribute = attributeMappingDtos.stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase(TestHelper.ATTRIBUTE_MAPPING_EMAIL))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(userEmailAttribute);

		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		Assert.assertTrue(syncConfigCustom instanceof SysSyncRoleConfigDto);
		SysSyncRoleConfigDto roleConfigDto = (SysSyncRoleConfigDto) syncConfigCustom;

		SysSystemMappingDto systemMappingDto = DtoUtils.getEmbedded(syncConfigCustom, SysSyncConfig_.systemMapping, SysSystemMappingDto.class);
		SysSchemaObjectClassDto schemaObjectClassDto = DtoUtils.getEmbedded(systemMappingDto, SysSystemMapping_.objectClass, SysSchemaObjectClassDto.class);
		UUID roleSystemId = schemaObjectClassDto.getSystem();
		Assert.assertNotNull(roleSystemId);
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(roleSystemId);
		schemaAttributeFilter.setObjectClassId(schemaObjectClassDto.getId());

		SysSchemaAttributeDto schemaAttributeDto = schemaAttributeService.find(schemaAttributeFilter, null).getContent().stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase("name"))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(schemaAttributeDto);

		SysSystemDto roleSystemDto = new SysSystemDto();
		roleSystemDto.setId(roleSystemId);
		List<SysSystemMappingDto> roleSystemMappings = systemMappingService.findBySystem(roleSystemDto, SystemOperationType.SYNCHRONIZATION, SystemEntityType.ROLE);
		Assert.assertNotNull(roleSystemMappings);
		Assert.assertEquals(1, roleSystemMappings.size());
		SysSystemMappingDto roleMappingDto = roleSystemMappings.get(0);
		// Create mapping attribute for get ID of role.
		SysSystemAttributeMappingDto roleIdAttribute = new SysSystemAttributeMappingDto();
		roleIdAttribute.setEntityAttribute(true);
		roleIdAttribute.setUid(false);
		roleIdAttribute.setSystemMapping(roleMappingDto.getId());
		roleIdAttribute.setExtendedAttribute(false);
		roleIdAttribute.setIdmPropertyName(RoleSynchronizationExecutor.ROLE_MEMBERSHIP_ID_FIELD);
		roleIdAttribute.setSchemaAttribute(schemaAttributeDto.getId());
		roleIdAttribute.setName(helper.createName());
		roleIdAttribute = attributeMappingService.save(roleIdAttribute);

		// Enable membership and use the user system.
		roleConfigDto.setMembershipSwitch(true);
		roleConfigDto.setMemberSystemMapping(userMappingDto.getId());
		roleConfigDto.setMemberOfAttribute(userEmailAttribute.getId());
		syncConfigCustom = syncConfigService.save(roleConfigDto);
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

		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.CREATE_ENTITY, 5, OperationResultType.SUCCESS);
		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setSystemId(roleSystemId);
		List<AccRoleAccountDto> roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		// Delete a log.
		syncLogService.delete(log);

		// Transformation will return the null -> memberships should be deleted.
		roleIdAttribute.setTransformFromResourceScript("return null;");
		attributeMappingService.save(roleIdAttribute);

		// Start sync again - for update.
		helper.startSynchronization(syncConfigCustom);
		//		
		logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.UPDATE_ENTITY, 5, OperationResultType.SUCCESS);
		roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setSystemId(roleSystemId);
		roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		roleAccountDtos.forEach(roleAccountDto -> {
			SysRoleSystemFilter roleSystemFilter = new SysRoleSystemFilter();
			roleSystemFilter.setRoleId(roleAccountDto.getRole());
			List<SysRoleSystemDto> roleSystemDtos = roleSystemService.find(roleSystemFilter, null).getContent();
			Assert.assertEquals(0, roleSystemDtos.size());
		});

		cleanAfterTest(syncConfigCustom, roleSystemId, log, roleAccountDtos);
	}

	@Test
	public void testSyncRolesForwardAcm() {
		AbstractSysSyncConfigDto syncConfigCustom = createSyncConfig();
		SysSystemDto userSystem = helper.createTestResourceSystem(true);
		List<SysSystemMappingDto> userSystemMappings = systemMappingService.findBySystem(userSystem, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		Assert.assertNotNull(userSystemMappings);
		Assert.assertEquals(1, userSystemMappings.size());
		SysSystemMappingDto userMappingDto = userSystemMappings.get(0);
		// Switch to the sync.
		userMappingDto.setOperationType(SystemOperationType.SYNCHRONIZATION);
		userMappingDto = systemMappingService.save(userMappingDto);

		List<SysSystemAttributeMappingDto> attributeMappingDtos = schemaAttributeMappingService.findBySystemMapping(userMappingDto);
		SysSystemAttributeMappingDto userEmailAttribute = attributeMappingDtos.stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase(TestHelper.ATTRIBUTE_MAPPING_EMAIL))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(userEmailAttribute);

		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		Assert.assertTrue(syncConfigCustom instanceof SysSyncRoleConfigDto);
		SysSyncRoleConfigDto roleConfigDto = (SysSyncRoleConfigDto) syncConfigCustom;

		SysSystemMappingDto systemMappingDto = DtoUtils.getEmbedded(syncConfigCustom, SysSyncConfig_.systemMapping, SysSystemMappingDto.class);
		SysSchemaObjectClassDto schemaObjectClassDto = DtoUtils.getEmbedded(systemMappingDto, SysSystemMapping_.objectClass, SysSchemaObjectClassDto.class);
		UUID systemId = schemaObjectClassDto.getSystem();
		Assert.assertNotNull(systemId);
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(systemId);
		schemaAttributeFilter.setObjectClassId(schemaObjectClassDto.getId());

		SysSchemaAttributeDto schemaAttributeDto = schemaAttributeService.find(schemaAttributeFilter, null).getContent().stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase("name"))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(schemaAttributeDto);

		SysSystemDto roleSystemDto = new SysSystemDto();
		roleSystemDto.setId(systemId);
		List<SysSystemMappingDto> roleSystemMappings = systemMappingService.findBySystem(roleSystemDto, SystemOperationType.SYNCHRONIZATION, SystemEntityType.ROLE);
		Assert.assertNotNull(roleSystemMappings);
		Assert.assertEquals(1, roleSystemMappings.size());
		SysSystemMappingDto roleMappingDto = roleSystemMappings.get(0);
		// Create mapping attribute for get ID of role.
		SysSystemAttributeMappingDto roleIdAttribute = new SysSystemAttributeMappingDto();
		roleIdAttribute.setEntityAttribute(true);
		roleIdAttribute.setUid(false);
		roleIdAttribute.setSystemMapping(roleMappingDto.getId());
		roleIdAttribute.setExtendedAttribute(false);
		roleIdAttribute.setIdmPropertyName(RoleSynchronizationExecutor.ROLE_MEMBERSHIP_ID_FIELD);
		roleIdAttribute.setSchemaAttribute(schemaAttributeDto.getId());
		roleIdAttribute.setName(helper.createName());
		attributeMappingService.save(roleIdAttribute);
		
		// Create mapping attribute for get ID of role.
		SysSystemAttributeMappingDto frorwardAcmAttribute = new SysSystemAttributeMappingDto();
		frorwardAcmAttribute.setEntityAttribute(true);
		frorwardAcmAttribute.setUid(false);
		frorwardAcmAttribute.setSystemMapping(roleMappingDto.getId());
		frorwardAcmAttribute.setExtendedAttribute(false);
		frorwardAcmAttribute.setIdmPropertyName(RoleSynchronizationExecutor.ROLE_FORWARD_ACM_FIELD);
		frorwardAcmAttribute.setSchemaAttribute(schemaAttributeDto.getId());
		frorwardAcmAttribute.setName(helper.createName());
		frorwardAcmAttribute.setTransformFromResourceScript("return true");
		attributeMappingService.save(frorwardAcmAttribute);

		// Enable membership and use the user system.
		roleConfigDto.setMembershipSwitch(true);
		roleConfigDto.setMemberSystemMapping(userMappingDto.getId());
		roleConfigDto.setMemberOfAttribute(userEmailAttribute.getId());
		roleConfigDto.setForwardAcmSwitch(false);
		roleConfigDto = (SysSyncRoleConfigDto) syncConfigService.save(roleConfigDto);
		Assert.assertNotNull(roleConfigDto.getForwardAcmMappingAttribute());

		// Start sync of roles.
		helper.startSynchronization(roleConfigDto);
		
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(roleConfigDto.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		helper.checkSyncLog(roleConfigDto, SynchronizationActionType.CREATE_ENTITY, 5, OperationResultType.SUCCESS);
		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setSystemId(systemId);
		List<AccRoleAccountDto> roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		roleAccountDtos.forEach(roleAccountDto -> {
			SysRoleSystemFilter roleSystemFilter = new SysRoleSystemFilter();
			roleSystemFilter.setRoleId(roleAccountDto.getRole());
			List<SysRoleSystemDto> roleSystemDtos = roleSystemService.find(roleSystemFilter, null).getContent();
			Assert.assertEquals(1, roleSystemDtos.size());
			SysRoleSystemDto roleSystem = roleSystemDtos.get(0);
			// Forward ACM feature is disabled now -> value should be "false".
			Assert.assertFalse(roleSystem.isForwardAccountManagemen());
		});
		
		// Activate forward ACM in sync.
		roleConfigDto.setForwardAcmSwitch(true);
		roleConfigDto = (SysSyncRoleConfigDto) syncConfigService.save(roleConfigDto);

		// Start sync of roles.
		helper.startSynchronization(roleConfigDto);
		helper.checkSyncLog(roleConfigDto, SynchronizationActionType.UPDATE_ENTITY, 5, OperationResultType.SUCCESS);
		
		roleAccountFilter.setSystemId(systemId);
		roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		roleAccountDtos.forEach(roleAccountDto -> {
			SysRoleSystemFilter roleSystemFilter = new SysRoleSystemFilter();
			roleSystemFilter.setRoleId(roleAccountDto.getRole());
			List<SysRoleSystemDto> roleSystemDtos = roleSystemService.find(roleSystemFilter, null).getContent();
			Assert.assertEquals(1, roleSystemDtos.size());
			SysRoleSystemDto roleSystem = roleSystemDtos.get(0);
			// Forward ACM feature is enabled now -> value should be "true".
			Assert.assertTrue(roleSystem.isForwardAccountManagemen());
		});

		cleanAfterTest(syncConfigCustom, systemId, log, roleAccountDtos);
	}
	
	@Test
	public void testSyncRolesSkipValueIfExcluded() {
		AbstractSysSyncConfigDto syncConfigCustom = createSyncConfig();
		SysSystemDto userSystem = helper.createTestResourceSystem(true);
		List<SysSystemMappingDto> userSystemMappings = systemMappingService.findBySystem(userSystem, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		Assert.assertNotNull(userSystemMappings);
		Assert.assertEquals(1, userSystemMappings.size());
		SysSystemMappingDto userMappingDto = userSystemMappings.get(0);
		// Switch to the sync.
		userMappingDto.setOperationType(SystemOperationType.SYNCHRONIZATION);
		userMappingDto = systemMappingService.save(userMappingDto);

		List<SysSystemAttributeMappingDto> attributeMappingDtos = schemaAttributeMappingService.findBySystemMapping(userMappingDto);
		SysSystemAttributeMappingDto userEmailAttribute = attributeMappingDtos.stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase(TestHelper.ATTRIBUTE_MAPPING_EMAIL))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(userEmailAttribute);

		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		Assert.assertTrue(syncConfigCustom instanceof SysSyncRoleConfigDto);
		SysSyncRoleConfigDto roleConfigDto = (SysSyncRoleConfigDto) syncConfigCustom;

		SysSystemMappingDto systemMappingDto = DtoUtils.getEmbedded(syncConfigCustom, SysSyncConfig_.systemMapping, SysSystemMappingDto.class);
		SysSchemaObjectClassDto schemaObjectClassDto = DtoUtils.getEmbedded(systemMappingDto, SysSystemMapping_.objectClass, SysSchemaObjectClassDto.class);
		UUID systemId = schemaObjectClassDto.getSystem();
		Assert.assertNotNull(systemId);
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(systemId);
		schemaAttributeFilter.setObjectClassId(schemaObjectClassDto.getId());

		SysSchemaAttributeDto schemaAttributeDto = schemaAttributeService.find(schemaAttributeFilter, null).getContent().stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase("name"))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(schemaAttributeDto);

		SysSystemDto roleSystemDto = new SysSystemDto();
		roleSystemDto.setId(systemId);
		List<SysSystemMappingDto> roleSystemMappings = systemMappingService.findBySystem(roleSystemDto, SystemOperationType.SYNCHRONIZATION, SystemEntityType.ROLE);
		Assert.assertNotNull(roleSystemMappings);
		Assert.assertEquals(1, roleSystemMappings.size());
		SysSystemMappingDto roleMappingDto = roleSystemMappings.get(0);
		// Create mapping attribute for get ID of role.
		SysSystemAttributeMappingDto roleIdAttribute = new SysSystemAttributeMappingDto();
		roleIdAttribute.setEntityAttribute(true);
		roleIdAttribute.setUid(false);
		roleIdAttribute.setSystemMapping(roleMappingDto.getId());
		roleIdAttribute.setExtendedAttribute(false);
		roleIdAttribute.setIdmPropertyName(RoleSynchronizationExecutor.ROLE_MEMBERSHIP_ID_FIELD);
		roleIdAttribute.setSchemaAttribute(schemaAttributeDto.getId());
		roleIdAttribute.setName(helper.createName());
		attributeMappingService.save(roleIdAttribute);
		
		// Create mapping attribute for get ID of role.
		SysSystemAttributeMappingDto frorwardAcmAttribute = new SysSystemAttributeMappingDto();
		frorwardAcmAttribute.setEntityAttribute(true);
		frorwardAcmAttribute.setUid(false);
		frorwardAcmAttribute.setSystemMapping(roleMappingDto.getId());
		frorwardAcmAttribute.setExtendedAttribute(false);
		frorwardAcmAttribute.setIdmPropertyName(RoleSynchronizationExecutor.ROLE_SKIP_VALUE_IF_EXCLUDED_FIELD);
		frorwardAcmAttribute.setSchemaAttribute(schemaAttributeDto.getId());
		frorwardAcmAttribute.setName(helper.createName());
		frorwardAcmAttribute.setTransformFromResourceScript("return true");
		attributeMappingService.save(frorwardAcmAttribute);

		// Enable membership and use the user system.
		roleConfigDto.setMembershipSwitch(true);
		roleConfigDto.setMemberSystemMapping(userMappingDto.getId());
		roleConfigDto.setMemberOfAttribute(userEmailAttribute.getId());
		roleConfigDto.setSkipValueIfExcludedSwitch(false);
		roleConfigDto = (SysSyncRoleConfigDto) syncConfigService.save(roleConfigDto);
		Assert.assertNotNull(roleConfigDto.getSkipValueIfExcludedMappingAttribute());
		
		// Start sync of roles.
		helper.startSynchronization(roleConfigDto);
		
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(roleConfigDto.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		helper.checkSyncLog(roleConfigDto, SynchronizationActionType.CREATE_ENTITY, 5, OperationResultType.SUCCESS);
		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setSystemId(systemId);
		List<AccRoleAccountDto> roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		roleAccountDtos.forEach(roleAccountDto -> {
			SysRoleSystemFilter roleSystemFilter = new SysRoleSystemFilter();
			roleSystemFilter.setRoleId(roleAccountDto.getRole());
			List<SysRoleSystemDto> roleSystemDtos = roleSystemService.find(roleSystemFilter, null).getContent();
			Assert.assertEquals(1, roleSystemDtos.size());
			SysRoleSystemDto roleSystem = roleSystemDtos.get(0);
			// Skip value if contract excluded feature is disabled now -> value should be "false".
			SysRoleSystemAttributeFilter roleSystemAttributeFilter = new SysRoleSystemAttributeFilter();
			roleSystemAttributeFilter.setRoleSystemId(roleSystem.getId());
			List<SysRoleSystemAttributeDto> roleSystemAttributeDtos = roleSystemAttributeService.find(roleSystemAttributeFilter, null).getContent();
			Assert.assertEquals(1, roleSystemAttributeDtos.size());
			Assert.assertFalse(roleSystemAttributeDtos.get(0).isSkipValueIfExcluded());
		});
		
		// Activate 'Skip value if excluded' in sync.
		roleConfigDto.setSkipValueIfExcludedSwitch(true);
		roleConfigDto = (SysSyncRoleConfigDto) syncConfigService.save(roleConfigDto);

		// Start sync of roles.
		helper.startSynchronization(roleConfigDto);
		helper.checkSyncLog(roleConfigDto, SynchronizationActionType.UPDATE_ENTITY, 5, OperationResultType.SUCCESS);
		
		roleAccountFilter.setSystemId(systemId);
		roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		roleAccountDtos.forEach(roleAccountDto -> {
			SysRoleSystemFilter roleSystemFilter = new SysRoleSystemFilter();
			roleSystemFilter.setRoleId(roleAccountDto.getRole());
			List<SysRoleSystemDto> roleSystemDtos = roleSystemService.find(roleSystemFilter, null).getContent();
			Assert.assertEquals(1, roleSystemDtos.size());
			SysRoleSystemDto roleSystem = roleSystemDtos.get(0);
			// Skip value if contract excluded feature is enabled now -> value should be "true".
			SysRoleSystemAttributeFilter roleSystemAttributeFilter = new SysRoleSystemAttributeFilter();
			roleSystemAttributeFilter.setRoleSystemId(roleSystem.getId());
			List<SysRoleSystemAttributeDto> roleSystemAttributeDtos = roleSystemAttributeService.find(roleSystemAttributeFilter, null).getContent();
			Assert.assertEquals(1, roleSystemAttributeDtos.size());
			Assert.assertTrue(roleSystemAttributeDtos.get(0).isSkipValueIfExcluded());
		});

		cleanAfterTest(syncConfigCustom, systemId, log, roleAccountDtos);
	}

	@Test
	public void testSyncUpdateRolesMembership() {
		AbstractSysSyncConfigDto syncConfigCustom = createSyncConfig();
		SysSystemDto userSystem = helper.createTestResourceSystem(true);
		List<SysSystemMappingDto> userSystemMappings = systemMappingService.findBySystem(userSystem, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		Assert.assertNotNull(userSystemMappings);
		Assert.assertEquals(1, userSystemMappings.size());
		SysSystemMappingDto userMappingDto = userSystemMappings.get(0);
		// Switch to the sync.
		userMappingDto.setOperationType(SystemOperationType.SYNCHRONIZATION);
		userMappingDto = systemMappingService.save(userMappingDto);

		List<SysSystemAttributeMappingDto> attributeMappingDtos = schemaAttributeMappingService.findBySystemMapping(userMappingDto);
		SysSystemAttributeMappingDto userEmailAttribute = attributeMappingDtos.stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase(TestHelper.ATTRIBUTE_MAPPING_EMAIL))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(userEmailAttribute);

		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		Assert.assertTrue(syncConfigCustom instanceof SysSyncRoleConfigDto);
		SysSyncRoleConfigDto roleConfigDto = (SysSyncRoleConfigDto) syncConfigCustom;

		SysSystemMappingDto systemMappingDto = DtoUtils.getEmbedded(syncConfigCustom, SysSyncConfig_.systemMapping, SysSystemMappingDto.class);
		SysSchemaObjectClassDto schemaObjectClassDto = DtoUtils.getEmbedded(systemMappingDto, SysSystemMapping_.objectClass, SysSchemaObjectClassDto.class);
		UUID roleSystemId = schemaObjectClassDto.getSystem();
		Assert.assertNotNull(roleSystemId);
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(roleSystemId);
		schemaAttributeFilter.setObjectClassId(schemaObjectClassDto.getId());

		SysSchemaAttributeDto schemaAttributeDto = schemaAttributeService.find(schemaAttributeFilter, null).getContent().stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase("name"))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(schemaAttributeDto);

		SysSystemDto roleSystemDto = new SysSystemDto();
		roleSystemDto.setId(roleSystemId);
		List<SysSystemMappingDto> roleSystemMappings = systemMappingService.findBySystem(roleSystemDto, SystemOperationType.SYNCHRONIZATION, SystemEntityType.ROLE);
		Assert.assertNotNull(roleSystemMappings);
		Assert.assertEquals(1, roleSystemMappings.size());
		SysSystemMappingDto roleMappingDto = roleSystemMappings.get(0);
		// Create mapping attribute for get ID of role.
		SysSystemAttributeMappingDto roleIdAttribute = new SysSystemAttributeMappingDto();
		roleIdAttribute.setEntityAttribute(true);
		roleIdAttribute.setUid(false);
		roleIdAttribute.setSystemMapping(roleMappingDto.getId());
		roleIdAttribute.setExtendedAttribute(false);
		roleIdAttribute.setIdmPropertyName(RoleSynchronizationExecutor.ROLE_MEMBERSHIP_ID_FIELD);
		roleIdAttribute.setSchemaAttribute(schemaAttributeDto.getId());
		roleIdAttribute.setName(helper.createName());
		roleIdAttribute = attributeMappingService.save(roleIdAttribute);

		// Enable membership and use the user system.
		roleConfigDto.setMembershipSwitch(true);
		roleConfigDto.setMemberSystemMapping(userMappingDto.getId());
		roleConfigDto.setMemberOfAttribute(userEmailAttribute.getId());
		syncConfigCustom = syncConfigService.save(roleConfigDto);
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

		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.CREATE_ENTITY, 5, OperationResultType.SUCCESS);
		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setSystemId(roleSystemId);
		List<AccRoleAccountDto> roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		// Delete a log.
		syncLogService.delete(log);

		// Transformation will return new random value -> memberships should be updated.
		String updatedScriptValue = getHelper().createName();
		roleIdAttribute.setTransformFromResourceScript("return '" + updatedScriptValue + "';");
		attributeMappingService.save(roleIdAttribute);

		// Start sync again - for update.
		helper.startSynchronization(syncConfigCustom);
		//		
		logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.UPDATE_ENTITY, 5, OperationResultType.SUCCESS);
		roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setSystemId(roleSystemId);
		roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		roleAccountDtos.forEach(roleAccountDto -> {
			SysRoleSystemFilter roleSystemFilter = new SysRoleSystemFilter();
			roleSystemFilter.setRoleId(roleAccountDto.getRole());
			List<SysRoleSystemDto> roleSystemDtos = roleSystemService.find(roleSystemFilter, null).getContent();
			Assert.assertEquals(1, roleSystemDtos.size());
			SysRoleSystemDto roleSystem = roleSystemDtos.get(0);
			// Check mapping attribute (should be email).
			SysRoleSystemAttributeFilter roleSystemAttributeFilter = new SysRoleSystemAttributeFilter();
			roleSystemAttributeFilter.setRoleSystemId(roleSystem.getId());
			List<SysRoleSystemAttributeDto> roleSystemAttributeDtos = roleSystemAttributeService.find(roleSystemAttributeFilter, null).getContent();
			Assert.assertEquals(1, roleSystemAttributeDtos.size());
			Assert.assertEquals(userEmailAttribute.getId(), roleSystemAttributeDtos.get(0).getSystemAttributeMapping());

			String transformScript = roleSystemAttributeDtos.get(0).getTransformScript();
			Assert.assertTrue(transformScript.contains(updatedScriptValue));
		});

		cleanAfterTest(syncConfigCustom, roleSystemId, log, roleAccountDtos);
	}
	
	@Test
	public void testSyncUpdateRolesMembershipDiffSync() {
		AbstractSysSyncConfigDto syncConfigCustom = createSyncConfig();
		// Enable a diff sync.
		syncConfigCustom.setDifferentialSync(true);
		syncConfigCustom = syncConfigService.save(syncConfigCustom);
		
		SysSystemDto userSystem = helper.createTestResourceSystem(true);
		List<SysSystemMappingDto> userSystemMappings = systemMappingService.findBySystem(userSystem, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		Assert.assertNotNull(userSystemMappings);
		Assert.assertEquals(1, userSystemMappings.size());
		SysSystemMappingDto userMappingDto = userSystemMappings.get(0);
		// Switch to the sync.
		userMappingDto.setOperationType(SystemOperationType.SYNCHRONIZATION);
		userMappingDto = systemMappingService.save(userMappingDto);

		List<SysSystemAttributeMappingDto> attributeMappingDtos = schemaAttributeMappingService.findBySystemMapping(userMappingDto);
		SysSystemAttributeMappingDto userEmailAttribute = attributeMappingDtos.stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase(TestHelper.ATTRIBUTE_MAPPING_EMAIL))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(userEmailAttribute);

		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		Assert.assertTrue(syncConfigCustom instanceof SysSyncRoleConfigDto);
		SysSyncRoleConfigDto roleConfigDto = (SysSyncRoleConfigDto) syncConfigCustom;

		SysSystemMappingDto systemMappingDto = DtoUtils.getEmbedded(syncConfigCustom, SysSyncConfig_.systemMapping, SysSystemMappingDto.class);
		SysSchemaObjectClassDto schemaObjectClassDto = DtoUtils.getEmbedded(systemMappingDto, SysSystemMapping_.objectClass, SysSchemaObjectClassDto.class);
		UUID roleSystemId = schemaObjectClassDto.getSystem();
		Assert.assertNotNull(roleSystemId);
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(roleSystemId);
		schemaAttributeFilter.setObjectClassId(schemaObjectClassDto.getId());

		SysSchemaAttributeDto schemaAttributeDto = schemaAttributeService.find(schemaAttributeFilter, null).getContent().stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase("name"))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(schemaAttributeDto);

		SysSystemDto roleSystemDto = new SysSystemDto();
		roleSystemDto.setId(roleSystemId);
		List<SysSystemMappingDto> roleSystemMappings = systemMappingService.findBySystem(roleSystemDto, SystemOperationType.SYNCHRONIZATION, SystemEntityType.ROLE);
		Assert.assertNotNull(roleSystemMappings);
		Assert.assertEquals(1, roleSystemMappings.size());
		SysSystemMappingDto roleMappingDto = roleSystemMappings.get(0);
		// Create mapping attribute for get ID of role.
		SysSystemAttributeMappingDto roleIdAttribute = new SysSystemAttributeMappingDto();
		roleIdAttribute.setEntityAttribute(true);
		roleIdAttribute.setUid(false);
		roleIdAttribute.setSystemMapping(roleMappingDto.getId());
		roleIdAttribute.setExtendedAttribute(false);
		roleIdAttribute.setIdmPropertyName(RoleSynchronizationExecutor.ROLE_MEMBERSHIP_ID_FIELD);
		roleIdAttribute.setSchemaAttribute(schemaAttributeDto.getId());
		roleIdAttribute.setName(helper.createName());
		roleIdAttribute = attributeMappingService.save(roleIdAttribute);

		// Enable membership and use the user system.
		roleConfigDto.setMembershipSwitch(true);
		roleConfigDto.setMemberSystemMapping(userMappingDto.getId());
		roleConfigDto.setMemberOfAttribute(userEmailAttribute.getId());
		syncConfigCustom = syncConfigService.save(roleConfigDto);
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

		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.CREATE_ENTITY, 5, OperationResultType.SUCCESS);
		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setSystemId(roleSystemId);
		List<AccRoleAccountDto> roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		// Delete the log.
		syncLogService.delete(log);

		// Transformation will return new random value -> memberships should be updated.
		String updatedScriptValue = getHelper().createName();
		roleIdAttribute.setTransformFromResourceScript("return '" + updatedScriptValue + "';");
		attributeMappingService.save(roleIdAttribute);

		// Start sync again - for update.
		helper.startSynchronization(syncConfigCustom);
		//		
		logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.UPDATE_ENTITY, 5, OperationResultType.SUCCESS);
		roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setSystemId(roleSystemId);
		roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		roleAccountDtos.forEach(roleAccountDto -> {
			SysRoleSystemFilter roleSystemFilter = new SysRoleSystemFilter();
			roleSystemFilter.setRoleId(roleAccountDto.getRole());
			List<SysRoleSystemDto> roleSystemDtos = roleSystemService.find(roleSystemFilter, null).getContent();
			Assert.assertEquals(1, roleSystemDtos.size());
			SysRoleSystemDto roleSystem = roleSystemDtos.get(0);
			// Check mapping attribute (should be email).
			SysRoleSystemAttributeFilter roleSystemAttributeFilter = new SysRoleSystemAttributeFilter();
			roleSystemAttributeFilter.setRoleSystemId(roleSystem.getId());
			List<SysRoleSystemAttributeDto> roleSystemAttributeDtos = roleSystemAttributeService.find(roleSystemAttributeFilter, null).getContent();
			Assert.assertEquals(1, roleSystemAttributeDtos.size());
			Assert.assertEquals(userEmailAttribute.getId(), roleSystemAttributeDtos.get(0).getSystemAttributeMapping());

			String transformScript = roleSystemAttributeDtos.get(0).getTransformScript();
			Assert.assertTrue(transformScript.contains(updatedScriptValue));
		});

		// Delete old log.
		syncLogService.delete(log);
		// Run sync again. Sync has enabled diff feature -> all changes should be ignored.
		helper.startSynchronization(syncConfigCustom);
		//		
		logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.UPDATE_ENTITY, 5, OperationResultType.IGNORE);

		cleanAfterTest(syncConfigCustom, roleSystemId, log, roleAccountDtos);
	}

	@Test
	public void testSyncRolesAssignToUsers() {
		AbstractSysSyncConfigDto syncConfigCustom = createSyncConfig();
		SysSystemDto userSystem = helper.createTestResourceSystem(true);
		List<SysSystemMappingDto> userSystemMappings = systemMappingService.findBySystem(userSystem, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		Assert.assertNotNull(userSystemMappings);
		Assert.assertEquals(1, userSystemMappings.size());
		SysSystemMappingDto userMappingDto = userSystemMappings.get(0);
		// Switch to the sync.
		userMappingDto.setOperationType(SystemOperationType.SYNCHRONIZATION);
		userMappingDto = systemMappingService.save(userMappingDto);

		SysSyncIdentityConfigDto userSyncConfig = createUserSyncConfig(userSystem);

		List<SysSystemAttributeMappingDto> attributeMappingDtos = schemaAttributeMappingService.findBySystemMapping(userMappingDto);
		SysSystemAttributeMappingDto userEmailAttribute = attributeMappingDtos.stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase(TestHelper.ATTRIBUTE_MAPPING_EMAIL))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(userEmailAttribute);
		SysSystemAttributeMappingDto enableAttribute = attributeMappingDtos.stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase(TestHelper.ATTRIBUTE_MAPPING_ENABLE))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(enableAttribute);
		enableAttribute.setDisabledAttribute(true);
		attributeMappingService.save(enableAttribute);

		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		Assert.assertTrue(syncConfigCustom instanceof SysSyncRoleConfigDto);
		SysSyncRoleConfigDto roleConfigDto = (SysSyncRoleConfigDto) syncConfigCustom;

		SysSystemMappingDto systemMappingDto = DtoUtils.getEmbedded(syncConfigCustom, SysSyncConfig_.systemMapping, SysSystemMappingDto.class);
		SysSchemaObjectClassDto schemaObjectClassDto = DtoUtils.getEmbedded(systemMappingDto, SysSystemMapping_.objectClass, SysSchemaObjectClassDto.class);
		UUID roleSystemId = schemaObjectClassDto.getSystem();
		Assert.assertNotNull(roleSystemId);
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(roleSystemId);
		schemaAttributeFilter.setObjectClassId(schemaObjectClassDto.getId());

		SysSchemaAttributeDto schemaAttributeDto = schemaAttributeService.find(schemaAttributeFilter, null).getContent().stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase("name"))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(schemaAttributeDto);

		SysSystemDto roleSystemDto = new SysSystemDto();
		roleSystemDto.setId(roleSystemId);
		List<SysSystemMappingDto> roleSystemMappings = systemMappingService.findBySystem(roleSystemDto, SystemOperationType.SYNCHRONIZATION, SystemEntityType.ROLE);
		Assert.assertNotNull(roleSystemMappings);
		Assert.assertEquals(1, roleSystemMappings.size());
		SysSystemMappingDto roleMappingDto = roleSystemMappings.get(0);
		// Create mapping attribute for get ID of role.
		SysSystemAttributeMappingDto roleIdAttribute = new SysSystemAttributeMappingDto();
		roleIdAttribute.setEntityAttribute(true);
		roleIdAttribute.setUid(false);
		roleIdAttribute.setSystemMapping(roleMappingDto.getId());
		roleIdAttribute.setExtendedAttribute(false);
		roleIdAttribute.setIdmPropertyName(RoleSynchronizationExecutor.ROLE_MEMBERSHIP_ID_FIELD);
		roleIdAttribute.setSchemaAttribute(schemaAttributeDto.getId());
		roleIdAttribute.setName(helper.createName());
		attributeMappingService.save(roleIdAttribute);

		String usernameOne = getHelper().createName();
		String usernameTwo = getHelper().createName();
		// Create mapping attribute for get ID of role.
		SysSystemAttributeMappingDto membersRoleAttribute = new SysSystemAttributeMappingDto();
		membersRoleAttribute.setEntityAttribute(true);
		membersRoleAttribute.setUid(false);
		membersRoleAttribute.setSystemMapping(roleMappingDto.getId());
		membersRoleAttribute.setExtendedAttribute(false);
		membersRoleAttribute.setIdmPropertyName(RoleSynchronizationExecutor.ROLE_MEMBERS_FIELD);
		membersRoleAttribute.setSchemaAttribute(schemaAttributeDto.getId());
		membersRoleAttribute.setName(helper.createName());
		membersRoleAttribute.setTransformFromResourceScript("return ['" + usernameOne + "', '" + usernameTwo + "'];");
		membersRoleAttribute = attributeMappingService.save(membersRoleAttribute);

		SysSchemaAttributeFilter schemaUserAttributeFilter = new SysSchemaAttributeFilter();
		schemaUserAttributeFilter.setSystemId(userSystem.getId());

		SysSchemaAttributeDto nameUserSchemaAttribute = schemaAttributeService.find(schemaUserAttributeFilter, null)
				.getContent()
				.stream()
				.filter(attribute -> "name".equalsIgnoreCase(attribute.getName()))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(nameUserSchemaAttribute);

		// Enable membership, assign role to users,  and use the user system.
		roleConfigDto.setMembershipSwitch(true);
		roleConfigDto.setMemberSystemMapping(userMappingDto.getId());
		roleConfigDto.setMemberOfAttribute(enableAttribute.getId());
		roleConfigDto.setAssignRoleSwitch(true);
		roleConfigDto.setRoleMembersMappingAttribute(membersRoleAttribute.getId());
		roleConfigDto.setMemberIdentifierAttribute(nameUserSchemaAttribute.getId());
		roleConfigDto = (SysSyncRoleConfigDto) syncConfigService.save(roleConfigDto);
		Assert.assertNotNull(roleConfigDto.getMemberOfAttribute());
		Assert.assertNotNull(roleConfigDto.getRoleIdentifiersMappingAttribute());
		Assert.assertNotNull(roleConfigDto.getRoleMembersMappingAttribute());
		Assert.assertNotNull(roleConfigDto.getMemberIdentifierAttribute());

		// Init users on system.
		helper.deleteAllResourceData();
		TestResource resource = new TestResource();
		resource.setName(usernameOne);
		resource.setFirstname(usernameOne);
		resource.setLastname(usernameOne);
		helper.saveResource(resource);
		resource.setName(usernameTwo);
		resource.setFirstname(usernameTwo);
		resource.setLastname(usernameTwo);
		helper.saveResource(resource);

		// Start sync of users
		helper.startSynchronization(userSyncConfig);
		helper.checkSyncLog(userSyncConfig, SynchronizationActionType.CREATE_ENTITY, 2, OperationResultType.SUCCESS);
		IdmIdentityDto identityOne = identityService.getByUsername(usernameOne);
		Assert.assertNotNull(identityOne);
		IdmIdentityDto identityTwo = identityService.getByUsername(usernameTwo);
		Assert.assertNotNull(identityTwo);

		// Start sync of roles
		helper.startSynchronization(syncConfigCustom);
		//		
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.CREATE_ENTITY, 5, OperationResultType.SUCCESS);
		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setSystemId(roleSystemId);
		List<AccRoleAccountDto> roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		// Every role should be assigned to userOne and userTwo.
		roleAccountDtos.forEach(roleAccountDto -> {
			IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();

			identityRoleFilter.setRoleId(roleAccountDto.getRole());
			identityRoleFilter.setIdentityId(identityOne.getId());
			Assert.assertEquals(1, identityRoleService.find(identityRoleFilter, null).getContent().size());

			identityRoleFilter.setIdentityId(identityTwo.getId());
			Assert.assertEquals(1, identityRoleService.find(identityRoleFilter, null).getContent().size());
		});
		
		cleanAfterTest(syncConfigCustom, roleSystemId, log, roleAccountDtos);
	}

	@Test
	public void testSyncRolesDeleteAssignedFromUsers() {
		AbstractSysSyncConfigDto syncConfigCustom = createSyncConfig();
		SysSystemDto userSystem = helper.createTestResourceSystem(true);
		List<SysSystemMappingDto> userSystemMappings = systemMappingService.findBySystem(userSystem, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		Assert.assertNotNull(userSystemMappings);
		Assert.assertEquals(1, userSystemMappings.size());
		SysSystemMappingDto userMappingDto = userSystemMappings.get(0);
		// Switch to the sync.
		userMappingDto.setOperationType(SystemOperationType.SYNCHRONIZATION);
		userMappingDto = systemMappingService.save(userMappingDto);

		SysSyncIdentityConfigDto userSyncConfig = createUserSyncConfig(userSystem);

		List<SysSystemAttributeMappingDto> attributeMappingDtos = schemaAttributeMappingService.findBySystemMapping(userMappingDto);
		SysSystemAttributeMappingDto userEmailAttribute = attributeMappingDtos.stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase(TestHelper.ATTRIBUTE_MAPPING_EMAIL))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(userEmailAttribute);
		SysSystemAttributeMappingDto enableAttribute = attributeMappingDtos.stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase(TestHelper.ATTRIBUTE_MAPPING_ENABLE))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(enableAttribute);
		enableAttribute.setDisabledAttribute(true);
		attributeMappingService.save(enableAttribute);

		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		Assert.assertTrue(syncConfigCustom instanceof SysSyncRoleConfigDto);
		SysSyncRoleConfigDto roleConfigDto = (SysSyncRoleConfigDto) syncConfigCustom;

		SysSystemMappingDto systemMappingDto = DtoUtils.getEmbedded(syncConfigCustom, SysSyncConfig_.systemMapping, SysSystemMappingDto.class);
		SysSchemaObjectClassDto schemaObjectClassDto = DtoUtils.getEmbedded(systemMappingDto, SysSystemMapping_.objectClass, SysSchemaObjectClassDto.class);
		UUID roleSystemId = schemaObjectClassDto.getSystem();
		Assert.assertNotNull(roleSystemId);
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(roleSystemId);
		schemaAttributeFilter.setObjectClassId(schemaObjectClassDto.getId());

		SysSchemaAttributeDto schemaAttributeDto = schemaAttributeService.find(schemaAttributeFilter, null).getContent().stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase("name"))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(schemaAttributeDto);

		SysSystemDto roleSystemDto = new SysSystemDto();
		roleSystemDto.setId(roleSystemId);
		List<SysSystemMappingDto> roleSystemMappings = systemMappingService.findBySystem(roleSystemDto, SystemOperationType.SYNCHRONIZATION, SystemEntityType.ROLE);
		Assert.assertNotNull(roleSystemMappings);
		Assert.assertEquals(1, roleSystemMappings.size());
		SysSystemMappingDto roleMappingDto = roleSystemMappings.get(0);
		// Create mapping attribute for get ID of role.
		SysSystemAttributeMappingDto roleIdAttribute = new SysSystemAttributeMappingDto();
		roleIdAttribute.setEntityAttribute(true);
		roleIdAttribute.setUid(false);
		roleIdAttribute.setSystemMapping(roleMappingDto.getId());
		roleIdAttribute.setExtendedAttribute(false);
		roleIdAttribute.setIdmPropertyName(RoleSynchronizationExecutor.ROLE_MEMBERSHIP_ID_FIELD);
		roleIdAttribute.setSchemaAttribute(schemaAttributeDto.getId());
		roleIdAttribute.setName(helper.createName());
		attributeMappingService.save(roleIdAttribute);

		String usernameOne = getHelper().createName();
		String usernameTwo = getHelper().createName();
		String usernameThree = getHelper().createName();
		// Create mapping attribute for get ID of role.
		SysSystemAttributeMappingDto membersRoleAttribute = new SysSystemAttributeMappingDto();
		membersRoleAttribute.setEntityAttribute(true);
		membersRoleAttribute.setUid(false);
		membersRoleAttribute.setSystemMapping(roleMappingDto.getId());
		membersRoleAttribute.setExtendedAttribute(false);
		membersRoleAttribute.setIdmPropertyName(RoleSynchronizationExecutor.ROLE_MEMBERS_FIELD);
		membersRoleAttribute.setSchemaAttribute(schemaAttributeDto.getId());
		membersRoleAttribute.setName(helper.createName());
		membersRoleAttribute.setTransformFromResourceScript("return ['" + usernameOne + "', '" + usernameTwo + "'];");
		membersRoleAttribute = attributeMappingService.save(membersRoleAttribute);

		SysSchemaAttributeFilter schemaUserAttributeFilter = new SysSchemaAttributeFilter();
		schemaUserAttributeFilter.setSystemId(userSystem.getId());

		SysSchemaAttributeDto nameUserSchemaAttribute = schemaAttributeService.find(schemaUserAttributeFilter, null)
				.getContent()
				.stream()
				.filter(attribute -> "name".equalsIgnoreCase(attribute.getName()))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(nameUserSchemaAttribute);

		// Enable membership, assign role to users,  and use the user system.
		roleConfigDto.setMembershipSwitch(true);
		roleConfigDto.setMemberSystemMapping(userMappingDto.getId());
		roleConfigDto.setMemberOfAttribute(enableAttribute.getId());
		roleConfigDto.setAssignRoleSwitch(true);
		roleConfigDto.setRoleMembersMappingAttribute(membersRoleAttribute.getId());
		roleConfigDto.setMemberIdentifierAttribute(nameUserSchemaAttribute.getId());
		roleConfigDto = (SysSyncRoleConfigDto) syncConfigService.save(roleConfigDto);
		Assert.assertNotNull(roleConfigDto.getMemberOfAttribute());
		Assert.assertNotNull(roleConfigDto.getRoleIdentifiersMappingAttribute());
		Assert.assertNotNull(roleConfigDto.getRoleMembersMappingAttribute());
		Assert.assertNotNull(roleConfigDto.getMemberIdentifierAttribute());

		// Init users on system.
		helper.deleteAllResourceData();
		TestResource resource = new TestResource();
		resource.setName(usernameOne);
		resource.setFirstname(usernameOne);
		resource.setLastname(usernameOne);
		helper.saveResource(resource);
		resource.setName(usernameTwo);
		resource.setFirstname(usernameTwo);
		resource.setLastname(usernameTwo);
		helper.saveResource(resource);
		resource.setName(usernameThree);
		resource.setFirstname(usernameThree);
		resource.setLastname(usernameThree);
		helper.saveResource(resource);

		// Start sync of users
		helper.startSynchronization(userSyncConfig);
		helper.checkSyncLog(userSyncConfig, SynchronizationActionType.CREATE_ENTITY, 3, OperationResultType.SUCCESS);
		IdmIdentityDto identityOne = identityService.getByUsername(usernameOne);
		Assert.assertNotNull(identityOne);
		IdmIdentityDto identityTwo = identityService.getByUsername(usernameTwo);
		Assert.assertNotNull(identityTwo);
		IdmIdentityDto identityThree = identityService.getByUsername(usernameThree);
		Assert.assertNotNull(identityThree);

		// Start sync of roles
		helper.startSynchronization(syncConfigCustom);
		//		
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.CREATE_ENTITY, 5, OperationResultType.SUCCESS);
		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setSystemId(roleSystemId);
		List<AccRoleAccountDto> roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		// Every role should be assigned to userOne and userTwo.
		roleAccountDtos.forEach(roleAccountDto -> {
			IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();

			identityRoleFilter.setRoleId(roleAccountDto.getRole());
			identityRoleFilter.setIdentityId(identityOne.getId());
			Assert.assertEquals(1, identityRoleService.find(identityRoleFilter, null).getContent().size());

			identityRoleFilter.setIdentityId(identityTwo.getId());
			Assert.assertEquals(1, identityRoleService.find(identityRoleFilter, null).getContent().size());

			identityRoleFilter.setIdentityId(identityThree.getId());
			Assert.assertEquals(0, identityRoleService.find(identityRoleFilter, null).getContent().size());

			// Assign role to identityThree.
			IdmIdentityContractDto primeContract = getHelper().getPrimeContract(identityThree);
			getHelper().assignRoles(primeContract, roleService.get(roleAccountDto.getRole()));
		});

		// Start sync of roles again. Identity three has redundantly assigned roles, but sync has not activated removing now.
		helper.startSynchronization(syncConfigCustom);
		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.UPDATE_ENTITY, 5, OperationResultType.SUCCESS);

		roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		// Every role should be assigned to userOne and userTwo.
		roleAccountDtos.forEach(roleAccountDto -> {
			IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();

			identityRoleFilter.setRoleId(roleAccountDto.getRole());
			identityRoleFilter.setIdentityId(identityOne.getId());
			Assert.assertEquals(1, identityRoleService.find(identityRoleFilter, null).getContent().size());

			identityRoleFilter.setIdentityId(identityTwo.getId());
			Assert.assertEquals(1, identityRoleService.find(identityRoleFilter, null).getContent().size());

			// Identity three has redundantly assigned roles, but sync has not activated removing now.
			identityRoleFilter.setIdentityId(identityThree.getId());
			Assert.assertEquals(1, identityRoleService.find(identityRoleFilter, null).getContent().size());
		});

		// Start sync of roles again. Identity three has redundantly assigned roles and sync has activated removing. Role should be removed.
		roleConfigDto.setAssignRoleRemoveSwitch(true);
		roleConfigDto = (SysSyncRoleConfigDto) syncConfigService.save(roleConfigDto);
		helper.startSynchronization(roleConfigDto);
		helper.checkSyncLog(roleConfigDto, SynchronizationActionType.UPDATE_ENTITY, 5, OperationResultType.SUCCESS);

		roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());

		// Every role should be assigned to userOne and userTwo.
		roleAccountDtos.forEach(roleAccountDto -> {
			IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();

			identityRoleFilter.setRoleId(roleAccountDto.getRole());
			identityRoleFilter.setIdentityId(identityOne.getId());
			Assert.assertEquals(1, identityRoleService.find(identityRoleFilter, null).getContent().size());

			identityRoleFilter.setIdentityId(identityTwo.getId());
			Assert.assertEquals(1, identityRoleService.find(identityRoleFilter, null).getContent().size());

			// Identity three has redundantly assigned roles and sync has activated removing. Role should be removed.
			identityRoleFilter.setIdentityId(identityThree.getId());
			Assert.assertEquals(0, identityRoleService.find(identityRoleFilter, null).getContent().size());
		});

		// Clean after test.
		cleanAfterTest(syncConfigCustom, roleSystemId, log, roleAccountDtos);
	}

	@Test
	public void testSyncRolesCatalogueUnderMain() {
		AbstractSysSyncConfigDto syncConfigCustom = createSyncConfig();
		SysSystemDto userSystem = helper.createTestResourceSystem(true);
		List<SysSystemMappingDto> userSystemMappings = systemMappingService.findBySystem(userSystem, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		Assert.assertNotNull(userSystemMappings);
		Assert.assertEquals(1, userSystemMappings.size());
		SysSystemMappingDto userMappingDto = userSystemMappings.get(0);
		// Switch to the sync.
		userMappingDto.setOperationType(SystemOperationType.SYNCHRONIZATION);
		userMappingDto = systemMappingService.save(userMappingDto);

		List<SysSystemAttributeMappingDto> attributeMappingDtos = schemaAttributeMappingService.findBySystemMapping(userMappingDto);
		SysSystemAttributeMappingDto userEmailAttribute = attributeMappingDtos.stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase(TestHelper.ATTRIBUTE_MAPPING_EMAIL))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(userEmailAttribute);

		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		Assert.assertTrue(syncConfigCustom instanceof SysSyncRoleConfigDto);
		SysSyncRoleConfigDto roleConfigDto = (SysSyncRoleConfigDto) syncConfigCustom;

		SysSystemMappingDto systemMappingDto = DtoUtils.getEmbedded(syncConfigCustom, SysSyncConfig_.systemMapping, SysSystemMappingDto.class);
		SysSchemaObjectClassDto schemaObjectClassDto = DtoUtils.getEmbedded(systemMappingDto, SysSystemMapping_.objectClass, SysSchemaObjectClassDto.class);
		UUID roleSystemId = schemaObjectClassDto.getSystem();
		Assert.assertNotNull(roleSystemId);
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(roleSystemId);
		schemaAttributeFilter.setObjectClassId(schemaObjectClassDto.getId());

		SysSchemaAttributeDto schemaAttributeDto = schemaAttributeService.find(schemaAttributeFilter, null).getContent().stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase("name"))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(schemaAttributeDto);

		SysSystemDto roleSystemDto = new SysSystemDto();
		roleSystemDto.setId(roleSystemId);
		List<SysSystemMappingDto> roleSystemMappings = systemMappingService.findBySystem(roleSystemDto, SystemOperationType.SYNCHRONIZATION, SystemEntityType.ROLE);
		Assert.assertNotNull(roleSystemMappings);
		Assert.assertEquals(1, roleSystemMappings.size());
		SysSystemMappingDto roleMappingDto = roleSystemMappings.get(0);

		// Use ACC script "resolveRoleCatalogueUnderMainCatalogue".
		IdmScriptFilter scriptFilter = new IdmScriptFilter();
		scriptFilter.setCode(MsAdSyncMappingRoleAutoAttributesProcessor.RESOLVE_ROLE_CATALOG_UNDER_MAIN_SCRIPT);
		scriptFilter.setCategory(IdmScriptCategory.TRANSFORM_FROM);

		String catalogTransformationScript = null;
		IdmScriptDto scriptDto = scriptService.find(scriptFilter, null).getContent()
				.stream()
				.findFirst()
				.orElse(null);
		if (scriptDto != null) {
			catalogTransformationScript = this.getPluginExecutors().getPluginFor(IdmScriptCategory.TRANSFORM_FROM)
					.generateTemplate(scriptDto);
		}
		Assert.assertNotNull(catalogTransformationScript);

		// Create mapping attribute for get catalog.
		SysSystemAttributeMappingDto roleIdAttribute = new SysSystemAttributeMappingDto();
		roleIdAttribute.setEntityAttribute(true);
		roleIdAttribute.setUid(false);
		roleIdAttribute.setSystemMapping(roleMappingDto.getId());
		roleIdAttribute.setExtendedAttribute(false);
		roleIdAttribute.setIdmPropertyName(RoleSynchronizationExecutor.ROLE_CATALOGUE_FIELD);
		roleIdAttribute.setSchemaAttribute(schemaAttributeDto.getId());
		roleIdAttribute.setTransformFromResourceScript(catalogTransformationScript);
		roleIdAttribute.setName(helper.createName());
		roleIdAttribute = attributeMappingService.save(roleIdAttribute);

		IdmRoleCatalogueDto mainRoleCatalogue = getHelper().createRoleCatalogue();

		// Enable assign of role catalogue.
		roleConfigDto.setAssignCatalogueSwitch(true);
		roleConfigDto.setRemoveCatalogueRoleSwitch(false);
		roleConfigDto.setMainCatalogueRoleNode(mainRoleCatalogue.getId());
		syncConfigCustom = syncConfigService.save(roleConfigDto);
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

		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.CREATE_ENTITY, 5, OperationResultType.SUCCESS);
		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setSystemId(roleSystemId);
		List<AccRoleAccountDto> roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());
		roleAccountDtos.forEach(roleAccountDto -> {
			UUID roleId = roleAccountDto.getRole();
			IdmRoleCatalogueRoleFilter roleCatalogueRoleFilter = new IdmRoleCatalogueRoleFilter();
			roleCatalogueRoleFilter.setRoleId(roleId);

			List<IdmRoleCatalogueRoleDto> roleCatalogueRoleDtos = roleCatalogueRoleService.find(roleCatalogueRoleFilter, null).getContent();
			Assert.assertEquals(1, roleCatalogueRoleDtos.size());
			Assert.assertEquals(mainRoleCatalogue.getId(), roleCatalogueRoleDtos.get(0).getRoleCatalogue());
		});

		cleanAfterTest(syncConfigCustom, roleSystemId, log, roleAccountDtos);
	}
	
	@Test
	/**
	 * Test create role catalog by DN:
	 *  "CN=WizardGroup01,OU=one,OU=two,OU=one,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv"
	 *  "CN=WizardGroup02,OU=two,OU=one,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv"
	 *  "CN=WizardGroup03,OU=one,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv"
	 * 	"CN=WizardGroup04,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv"
	 * 	"CN=WizardGroup05,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv"
	 */
	public void testSyncRolesCatalogueByDn() {
		AbstractSysSyncConfigDto syncConfigCustom = createSyncConfig();
		SysSystemDto userSystem = helper.createTestResourceSystem(true);
		List<SysSystemMappingDto> userSystemMappings = systemMappingService.findBySystem(userSystem, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		Assert.assertNotNull(userSystemMappings);
		Assert.assertEquals(1, userSystemMappings.size());
		SysSystemMappingDto userMappingDto = userSystemMappings.get(0);
		// Switch to the sync.
		userMappingDto.setOperationType(SystemOperationType.SYNCHRONIZATION);
		userMappingDto = systemMappingService.save(userMappingDto);

		List<SysSystemAttributeMappingDto> attributeMappingDtos = schemaAttributeMappingService.findBySystemMapping(userMappingDto);
		SysSystemAttributeMappingDto userEmailAttribute = attributeMappingDtos.stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase(TestHelper.ATTRIBUTE_MAPPING_EMAIL))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(userEmailAttribute);

		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		Assert.assertTrue(syncConfigCustom instanceof SysSyncRoleConfigDto);
		SysSyncRoleConfigDto roleConfigDto = (SysSyncRoleConfigDto) syncConfigCustom;

		SysSystemMappingDto systemMappingDto = DtoUtils.getEmbedded(syncConfigCustom, SysSyncConfig_.systemMapping, SysSystemMappingDto.class);
		SysSchemaObjectClassDto schemaObjectClassDto = DtoUtils.getEmbedded(systemMappingDto, SysSystemMapping_.objectClass, SysSchemaObjectClassDto.class);
		UUID roleSystemId = schemaObjectClassDto.getSystem();
		Assert.assertNotNull(roleSystemId);
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(roleSystemId);
		schemaAttributeFilter.setObjectClassId(schemaObjectClassDto.getId());

		SysSchemaAttributeDto schemaAttributeDto = schemaAttributeService.find(schemaAttributeFilter, null).getContent().stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase("name"))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(schemaAttributeDto);

		SysSystemDto roleSystemDto = new SysSystemDto();
		roleSystemDto.setId(roleSystemId);
		List<SysSystemMappingDto> roleSystemMappings = systemMappingService.findBySystem(roleSystemDto, SystemOperationType.SYNCHRONIZATION, SystemEntityType.ROLE);
		Assert.assertNotNull(roleSystemMappings);
		Assert.assertEquals(1, roleSystemMappings.size());
		SysSystemMappingDto roleMappingDto = roleSystemMappings.get(0);

		// Use ACC script "resolveRoleCatalogueByDn".
		IdmScriptFilter scriptFilter = new IdmScriptFilter();
		scriptFilter.setCode(MsAdSyncMappingRoleAutoAttributesProcessor.RESOLVE_ROLE_CATALOG_BY_DN_SCRIPT);
		scriptFilter.setCategory(IdmScriptCategory.TRANSFORM_FROM);

		String catalogTransformationScript = null;
		IdmScriptDto scriptDto = scriptService.find(scriptFilter, null).getContent()
				.stream()
				.findFirst()
				.orElse(null);
		if (scriptDto != null) {
			catalogTransformationScript = this.getPluginExecutors().getPluginFor(IdmScriptCategory.TRANSFORM_FROM)
					.generateTemplate(scriptDto);
		}
		Assert.assertNotNull(catalogTransformationScript);

		// Create mapping attribute for get catalog.
		SysSystemAttributeMappingDto roleIdAttribute = new SysSystemAttributeMappingDto();
		roleIdAttribute.setEntityAttribute(true);
		roleIdAttribute.setUid(false);
		roleIdAttribute.setSystemMapping(roleMappingDto.getId());
		roleIdAttribute.setExtendedAttribute(false);
		roleIdAttribute.setIdmPropertyName(RoleSynchronizationExecutor.ROLE_CATALOGUE_FIELD);
		roleIdAttribute.setSchemaAttribute(schemaAttributeDto.getId());
		roleIdAttribute.setTransformFromResourceScript(catalogTransformationScript);
		roleIdAttribute.setName(helper.createName());
		roleIdAttribute = attributeMappingService.save(roleIdAttribute);

		IdmRoleCatalogueDto mainRoleCatalogue = getHelper().createRoleCatalogue();

		// Enable assign of role catalogue.
		roleConfigDto.setAssignCatalogueSwitch(true);
		roleConfigDto.setRemoveCatalogueRoleSwitch(false);
		roleConfigDto.setMainCatalogueRoleNode(mainRoleCatalogue.getId());
		syncConfigCustom = syncConfigService.save(roleConfigDto);
		
		// Init data - roles with DN.
		getBean().initDataRolesWithDn();
		// Start sync
		helper.startSynchronization(syncConfigCustom);
		//		
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(syncConfigCustom.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());
		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.CREATE_ENTITY, 5, OperationResultType.SUCCESS);

		// "CN=WizardGroup01,OU=one,OU=two,OU=one,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv"
		// "CN=WizardGroup02,OU=two,OU=one,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv"
		// "CN=WizardGroup03,OU=one,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv"
		// "CN=WizardGroup04,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv"
		// "CN=WizardGroup05,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv"
		
		// Check catalog structure:
		IdmRoleCatalogueDto wizardGroups = roleCatalogueService.getByCode("WizardGroups/"+mainRoleCatalogue.getCode());
		Assert.assertNotNull(wizardGroups);
		IdmRoleCatalogueDto one = roleCatalogueService.getByCode("one/WizardGroups/"+mainRoleCatalogue.getCode());
		Assert.assertNotNull(one);
		Assert.assertEquals(wizardGroups.getId(), one.getParent());
		IdmRoleCatalogueDto two = roleCatalogueService.getByCode("two/one/WizardGroups/"+mainRoleCatalogue.getCode());
		Assert.assertNotNull(two);
		Assert.assertEquals(one.getId(), two.getParent());
		IdmRoleCatalogueDto one2 = roleCatalogueService.getByCode("one/two/one/WizardGroups/"+mainRoleCatalogue.getCode());
		Assert.assertNotNull(one2);
		Assert.assertEquals(two.getId(), one2.getParent());

		IdmRoleCatalogueRoleFilter roleCatalogueRoleFilter = new IdmRoleCatalogueRoleFilter();
		roleCatalogueRoleFilter.setRoleCatalogueId(wizardGroups.getId());
		List<IdmRoleCatalogueRoleDto> roleCatalogRoles = roleCatalogueRoleService.find(roleCatalogueRoleFilter, null).getContent();
		Assert.assertEquals(2, roleCatalogRoles.size());
		Assert.assertTrue(roleCatalogRoles.stream()
				.map(roleCatalogRole -> (IdmRoleDto)roleCatalogRole.getEmbedded().get(IdmRoleCatalogueRole_.role.getName()))
				.anyMatch(role -> role.getCode().equals("CN=WizardGroup04,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv"))
		);
		Assert.assertTrue(roleCatalogRoles.stream()
				.map(roleCatalogRole -> (IdmRoleDto)roleCatalogRole.getEmbedded().get(IdmRoleCatalogueRole_.role.getName()))
				.anyMatch(role -> role.getCode().equals("CN=WizardGroup05,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv"))
		);
		
		roleCatalogueRoleFilter.setRoleCatalogueId(one.getId());
		roleCatalogRoles = roleCatalogueRoleService.find(roleCatalogueRoleFilter, null).getContent();
		Assert.assertEquals(1, roleCatalogRoles.size());
		Assert.assertTrue(roleCatalogRoles.stream()
				.map(roleCatalogRole -> (IdmRoleDto)roleCatalogRole.getEmbedded().get(IdmRoleCatalogueRole_.role.getName()))
				.anyMatch(role -> role.getCode().equals("CN=WizardGroup03,OU=one,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv"))
		);
		
		roleCatalogueRoleFilter.setRoleCatalogueId(two.getId());
		roleCatalogRoles = roleCatalogueRoleService.find(roleCatalogueRoleFilter, null).getContent();
		Assert.assertEquals(1, roleCatalogRoles.size());
		Assert.assertTrue(roleCatalogRoles.stream()
				.map(roleCatalogRole -> (IdmRoleDto)roleCatalogRole.getEmbedded().get(IdmRoleCatalogueRole_.role.getName()))
				.anyMatch(role -> role.getCode().equals("CN=WizardGroup02,OU=two,OU=one,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv"))
		);
		
		roleCatalogueRoleFilter.setRoleCatalogueId(one2.getId());
		roleCatalogRoles = roleCatalogueRoleService.find(roleCatalogueRoleFilter, null).getContent();
		Assert.assertEquals(1, roleCatalogRoles.size());
		Assert.assertTrue(roleCatalogRoles.stream()
				.map(roleCatalogRole -> (IdmRoleDto)roleCatalogRole.getEmbedded().get(IdmRoleCatalogueRole_.role.getName()))
				.anyMatch(role -> role.getCode().equals("CN=WizardGroup01,OU=one,OU=two,OU=one,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv"))
		);

		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setSystemId(roleSystemId);
		List<AccRoleAccountDto> roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());
		roleAccountDtos.forEach(roleAccountDto -> {
			UUID roleId = roleAccountDto.getRole();
			IdmRoleCatalogueRoleFilter roleCatalogueFilter = new IdmRoleCatalogueRoleFilter();
			roleCatalogueFilter.setRoleId(roleId);

			List<IdmRoleCatalogueRoleDto> roleCatalogueRoleDtos = roleCatalogueRoleService.find(roleCatalogueFilter, null).getContent();
			Assert.assertEquals(1, roleCatalogueRoleDtos.size());
		});

		cleanAfterTest(syncConfigCustom, roleSystemId, log, roleAccountDtos);
	}

	@Test
	public void testSyncRolesCatalogueRemoveUnderMain() {
		AbstractSysSyncConfigDto syncConfigCustom = createSyncConfig();
		SysSystemDto userSystem = helper.createTestResourceSystem(true);
		List<SysSystemMappingDto> userSystemMappings = systemMappingService.findBySystem(userSystem, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		Assert.assertNotNull(userSystemMappings);
		Assert.assertEquals(1, userSystemMappings.size());
		SysSystemMappingDto userMappingDto = userSystemMappings.get(0);
		// Switch to the sync.
		userMappingDto.setOperationType(SystemOperationType.SYNCHRONIZATION);
		userMappingDto = systemMappingService.save(userMappingDto);

		List<SysSystemAttributeMappingDto> attributeMappingDtos = schemaAttributeMappingService.findBySystemMapping(userMappingDto);
		SysSystemAttributeMappingDto userEmailAttribute = attributeMappingDtos.stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase(TestHelper.ATTRIBUTE_MAPPING_EMAIL))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(userEmailAttribute);

		Assert.assertFalse(syncConfigService.isRunning(syncConfigCustom));
		Assert.assertTrue(syncConfigCustom instanceof SysSyncRoleConfigDto);
		SysSyncRoleConfigDto roleConfigDto = (SysSyncRoleConfigDto) syncConfigCustom;

		SysSystemMappingDto systemMappingDto = DtoUtils.getEmbedded(syncConfigCustom, SysSyncConfig_.systemMapping, SysSystemMappingDto.class);
		SysSchemaObjectClassDto schemaObjectClassDto = DtoUtils.getEmbedded(systemMappingDto, SysSystemMapping_.objectClass, SysSchemaObjectClassDto.class);
		UUID roleSystemId = schemaObjectClassDto.getSystem();
		Assert.assertNotNull(roleSystemId);
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(roleSystemId);
		schemaAttributeFilter.setObjectClassId(schemaObjectClassDto.getId());

		SysSchemaAttributeDto schemaAttributeDto = schemaAttributeService.find(schemaAttributeFilter, null).getContent().stream()
				.filter(attribute -> attribute.getName().equalsIgnoreCase("name"))
				.findFirst()
				.orElse(null);
		Assert.assertNotNull(schemaAttributeDto);

		SysSystemDto roleSystemDto = new SysSystemDto();
		roleSystemDto.setId(roleSystemId);
		List<SysSystemMappingDto> roleSystemMappings = systemMappingService.findBySystem(roleSystemDto, SystemOperationType.SYNCHRONIZATION, SystemEntityType.ROLE);
		Assert.assertNotNull(roleSystemMappings);
		Assert.assertEquals(1, roleSystemMappings.size());
		SysSystemMappingDto roleMappingDto = roleSystemMappings.get(0);

		// Use ACC script "resolveRoleCatalogueUnderMainCatalogue".
		IdmScriptFilter scriptFilter = new IdmScriptFilter();
		scriptFilter.setCode("resolveRoleCatalogueUnderMainCatalogue");
		scriptFilter.setCategory(IdmScriptCategory.TRANSFORM_FROM);

		String catalogTransformationScript = null;
		IdmScriptDto scriptDto = scriptService.find(scriptFilter, null).getContent()
				.stream()
				.findFirst()
				.orElse(null);
		if (scriptDto != null) {
			catalogTransformationScript = this.getPluginExecutors().getPluginFor(IdmScriptCategory.TRANSFORM_FROM)
					.generateTemplate(scriptDto);
		}
		Assert.assertNotNull(catalogTransformationScript);

		// Create mapping attribute for get catalog.
		SysSystemAttributeMappingDto roleIdAttribute = new SysSystemAttributeMappingDto();
		roleIdAttribute.setEntityAttribute(true);
		roleIdAttribute.setUid(false);
		roleIdAttribute.setSystemMapping(roleMappingDto.getId());
		roleIdAttribute.setExtendedAttribute(false);
		roleIdAttribute.setIdmPropertyName(RoleSynchronizationExecutor.ROLE_CATALOGUE_FIELD);
		roleIdAttribute.setSchemaAttribute(schemaAttributeDto.getId());
		roleIdAttribute.setTransformFromResourceScript(catalogTransformationScript);
		roleIdAttribute.setName(helper.createName());
		roleIdAttribute = attributeMappingService.save(roleIdAttribute);
		
		IdmRoleCatalogueDto parentRoleCatalogue = getHelper().createRoleCatalogue();
		IdmRoleCatalogueDto mainRoleCatalogue = getHelper().createRoleCatalogue(getHelper().createName(), parentRoleCatalogue.getId());

		// Enable assign of role catalogue.
		roleConfigDto.setAssignCatalogueSwitch(true);
		roleConfigDto.setRemoveCatalogueRoleSwitch(false);
		roleConfigDto.setMainCatalogueRoleNode(mainRoleCatalogue.getId());
		roleConfigDto.setRemoveCatalogueRoleParentNode(mainRoleCatalogue.getId());
		syncConfigCustom = syncConfigService.save(roleConfigDto);
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

		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.CREATE_ENTITY, 5, OperationResultType.SUCCESS);
		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setSystemId(roleSystemId);
		List<AccRoleAccountDto> roleAccountDtos = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(5, roleAccountDtos.size());
		roleAccountDtos.forEach(roleAccountDto -> {
			UUID roleId = roleAccountDto.getRole();
			IdmRoleCatalogueRoleFilter roleCatalogueRoleFilter = new IdmRoleCatalogueRoleFilter();
			roleCatalogueRoleFilter.setRoleId(roleId);

			List<IdmRoleCatalogueRoleDto> roleCatalogueRoleDtos = roleCatalogueRoleService.find(roleCatalogueRoleFilter, null).getContent();
			Assert.assertEquals(1, roleCatalogueRoleDtos.size());
			Assert.assertEquals(mainRoleCatalogue.getId(), roleCatalogueRoleDtos.get(0).getRoleCatalogue());
		});
		
		roleAccountDtos.forEach(roleAccountDto -> {
			UUID roleId = roleAccountDto.getRole();
			getHelper().createRoleCatalogueRole(roleService.get(roleId), mainRoleCatalogue);
			getHelper().createRoleCatalogueRole(roleService.get(roleId), parentRoleCatalogue);
			IdmRoleCatalogueRoleFilter roleCatalogueRoleFilter = new IdmRoleCatalogueRoleFilter();
			roleCatalogueRoleFilter.setRoleId(roleId);

			List<IdmRoleCatalogueRoleDto> roleCatalogueRoleDtos = roleCatalogueRoleService.find(roleCatalogueRoleFilter, null).getContent();
			// The role is in 3 catalogs (one from system, two redundant).
			Assert.assertEquals(3, roleCatalogueRoleDtos.size());
			Assert.assertEquals(mainRoleCatalogue.getId(), roleCatalogueRoleDtos.get(0).getRoleCatalogue());
		});

		// Start a sync again. Remove redundant catalogs is not enabled -> Same results. 
		helper.startSynchronization(syncConfigCustom);
		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.UPDATE_ENTITY, 5, OperationResultType.SUCCESS);

		roleAccountDtos.forEach(roleAccountDto -> {
			UUID roleId = roleAccountDto.getRole();
			IdmRoleCatalogueRoleFilter roleCatalogueRoleFilter = new IdmRoleCatalogueRoleFilter();
			roleCatalogueRoleFilter.setRoleId(roleId);

			List<IdmRoleCatalogueRoleDto> roleCatalogueRoleDtos = roleCatalogueRoleService.find(roleCatalogueRoleFilter, null).getContent();
			// Remove redundant catalogs is not enabled -> Same results: The role is in 3 catalogs (one from system, two redundant).
			Assert.assertEquals(3, roleCatalogueRoleDtos.size());
			Assert.assertEquals(mainRoleCatalogue.getId(), roleCatalogueRoleDtos.get(0).getRoleCatalogue());
		});

		roleConfigDto.setRemoveCatalogueRoleSwitch(true);
		roleConfigDto.setRemoveCatalogueRoleParentNode(mainRoleCatalogue.getId());
		syncConfigCustom = syncConfigService.save(roleConfigDto);

		// Start a sync again. Remove redundant catalogs is enabled -> Redundant relation from main catalog should be removed. 
		helper.startSynchronization(syncConfigCustom);
		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.UPDATE_ENTITY, 5, OperationResultType.SUCCESS);

		roleAccountDtos.forEach(roleAccountDto -> {
			UUID roleId = roleAccountDto.getRole();
			IdmRoleCatalogueRoleFilter roleCatalogueRoleFilter = new IdmRoleCatalogueRoleFilter();
			roleCatalogueRoleFilter.setRoleId(roleId);

			List<IdmRoleCatalogueRoleDto> roleCatalogueRoleDtos = roleCatalogueRoleService.find(roleCatalogueRoleFilter, null).getContent();
			// Remove redundant catalogs is enabled -> Redundant relation from main catalog should be removed: The role is in 2 catalogs (one from system, one redundant).
			Assert.assertEquals(2, roleCatalogueRoleDtos.size());
			Assert.assertEquals(mainRoleCatalogue.getId(), roleCatalogueRoleDtos.get(0).getRoleCatalogue());
		});
		
		roleConfigDto.setRemoveCatalogueRoleSwitch(true);
		roleConfigDto.setRemoveCatalogueRoleParentNode(parentRoleCatalogue.getId());
		syncConfigCustom = syncConfigService.save(roleConfigDto);
		
		// Start a sync again. Remove redundant catalogs is enabled -> Redundant relation from parent catalog should be removed. 
		helper.startSynchronization(syncConfigCustom);
		helper.checkSyncLog(syncConfigCustom, SynchronizationActionType.UPDATE_ENTITY, 5, OperationResultType.SUCCESS);

		roleAccountDtos.forEach(roleAccountDto -> {
			UUID roleId = roleAccountDto.getRole();
			IdmRoleCatalogueRoleFilter roleCatalogueRoleFilter = new IdmRoleCatalogueRoleFilter();
			roleCatalogueRoleFilter.setRoleId(roleId);

			List<IdmRoleCatalogueRoleDto> roleCatalogueRoleDtos = roleCatalogueRoleService.find(roleCatalogueRoleFilter, null).getContent();
			// Remove redundant catalogs is enabled ->  Redundant relation from parent catalog should be removed: The role is in 1 catalog.
			Assert.assertEquals(1, roleCatalogueRoleDtos.size());
			Assert.assertEquals(mainRoleCatalogue.getId(), roleCatalogueRoleDtos.get(0).getRoleCatalogue());
		});

		cleanAfterTest(syncConfigCustom, roleSystemId, log, roleAccountDtos);
	}

	private void cleanAfterTest(AbstractSysSyncConfigDto syncConfigCustom, UUID roleSystemId, SysSyncLogDto log, List<AccRoleAccountDto> roleAccountDtos) {
		// Delete a log.
		syncLogService.delete(log);
		// Delete roles.
		roleAccountDtos.forEach(roleAccountDto -> {
			IdmRoleDto role = roleService.get(roleAccountDto.getRole());
			forceRoleDelete(role);
		});
		// Delete sync.
		syncConfigService.delete(syncConfigCustom);
		// Delete system.
		systemService.delete(systemService.get(roleSystemId));
	}

	private void forceRoleDelete(IdmRoleDto role) {
		// remove role async
		try {
			getHelper().setConfigurationValue(EventConfiguration.PROPERTY_EVENT_ASYNCHRONOUS_ENABLED, true);

			Map<String, Object> properties = new HashMap<>();
			properties.put(RoleProcessor.PROPERTY_FORCE_DELETE, Boolean.TRUE);
			// Delete by bulk action.
			IdmBulkActionDto bulkAction = this.findBulkAction(IdmRole.class, RoleDeleteBulkAction.NAME);
			bulkAction.setIdentifiers(Sets.newHashSet(role.getId()));
			bulkAction.setProperties(properties);
			bulkActionManager.processAction(bulkAction);

			getHelper().waitForResult(res -> {
				return roleService.get(role) != null;
			});
			Assert.assertNull(roleService.get(role));
		} finally {
			getHelper().setConfigurationValue(EventConfiguration.PROPERTY_EVENT_ASYNCHRONOUS_ENABLED, false);
		}
	}

	@Transactional
	public void deleteAllResourceData() {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM test_role_resource");
		q.executeUpdate();
	}

	private SysSystemDto initData() {

		// create test system
		SysSystemDto system = helper.createSystem("test_role_resource");

		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);

		// Create synchronization mapping
		SysSystemMappingDto syncSystemMapping = new SysSystemMappingDto();
		syncSystemMapping.setName(getHelper().createName());
		syncSystemMapping.setEntityType(SystemEntityType.ROLE);
		syncSystemMapping.setOperationType(SystemOperationType.SYNCHRONIZATION);
		syncSystemMapping.setObjectClass(objectClasses.get(0).getId());
		final SysSystemMappingDto syncMapping = systemMappingService.save(syncSystemMapping);

		createMapping(system, syncMapping);
		getBean().initRoleData();

		syncConfigService.find(null).getContent().forEach(config -> {
			syncConfigService.delete(config);
		});

		return system;

	}

	@Transactional
	public void initRoleData() {
		getBean().deleteAllResourceData();
		ZonedDateTime now = ZonedDateTime.now();
		entityManager.persist(this.createRole("1", RoleType.SYSTEM.name(), now.plusHours(1), 0));
		entityManager.persist(this.createRole("2", RoleType.SYSTEM.name(), now.plusHours(2), 0));
		entityManager.persist(this.createRole("3", RoleType.SYSTEM.name(), now.plusHours(3), 0));
		entityManager.persist(this.createRole("4", RoleType.SYSTEM.name(), now.plusHours(4), 1));
		entityManager.persist(this.createRole("5", RoleType.SYSTEM.name(), now.plusHours(5), 1));

	}
	
	@Transactional
	public void initDataRolesWithDn() {
		getBean().deleteAllResourceData();
		ZonedDateTime now = ZonedDateTime.now();
		entityManager.persist(this.createRole("CN=WizardGroup01,OU=one,OU=two,OU=one,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv", RoleType.SYSTEM.name(), now.plusHours(1), 0));
		entityManager.persist(this.createRole("CN=WizardGroup02,OU=two,OU=one,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv", RoleType.SYSTEM.name(), now.plusHours(2), 0));
		entityManager.persist(this.createRole("CN=WizardGroup03,OU=one,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv", RoleType.SYSTEM.name(), now.plusHours(3), 0));
		entityManager.persist(this.createRole("CN=WizardGroup04,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv", RoleType.SYSTEM.name(), now.plusHours(4), 1));
		entityManager.persist(this.createRole("CN=WizardGroup05,OU=WizardGroups,DC=kyblicek,DC=piskoviste,DC=bcv", RoleType.SYSTEM.name(), now.plusHours(5), 1));

	}

	private PluginRegistry<AbstractScriptEvaluator, IdmScriptCategory> getPluginExecutors() {
		if (this.pluginExecutors == null) {
			this.pluginExecutors = OrderAwarePluginRegistry.create(evaluators);
		}
		return this.pluginExecutors;
	}

	private TestRoleResource createRole(String code, String type, ZonedDateTime changed, int priority) {
		IdmRoleDto roleDto = roleService.getByCode(code);
		if (roleDto != null) {
			this.forceRoleDelete(roleDto);
		}
		TestRoleResource role = new TestRoleResource();
		role.setType(type);
		role.setName(code);
		role.setPriority(priority);
		role.setModified(changed);
		role.setDescription(code);
		return role;
	}

	@Transactional
	public void changeOne() {
		TestRoleResource one = entityManager.find(TestRoleResource.class, "1");
		one.setDescription(CHANGED);
		entityManager.persist(one);
	}

	@Transactional
	public void removeOne() {
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

			} else if ("priority".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeHandlingName = new SysSystemAttributeMappingDto();
				attributeHandlingName.setIdmPropertyName("priority");
				attributeHandlingName.setEntityAttribute(true);
				attributeHandlingName.setSchemaAttribute(schemaAttr.getId());
				attributeHandlingName.setName(schemaAttr.getName());
				attributeHandlingName.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeHandlingName);

			}
		});
	}

	public AbstractSysSyncConfigDto createSyncConfig() {
		SysSystemDto system = initData();

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

		// Create default synchronization config
		AbstractSysSyncConfigDto syncConfigCustom = new SysSyncRoleConfigDto();
		syncConfigCustom.setReconciliation(true);
		syncConfigCustom.setDifferentialSync(false);
		syncConfigCustom.setCustomFilter(false);
		syncConfigCustom.setSystemMapping(mapping.getId());
		syncConfigCustom.setCorrelationAttribute(uidAttribute.getId());
		syncConfigCustom.setName(getHelper().createName());
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UPDATE_ENTITY);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.IGNORE);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.CREATE_ENTITY);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);

		return syncConfigService.save(syncConfigCustom);
	}

	public SysSyncIdentityConfigDto createUserSyncConfig(SysSystemDto system) {
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
		SysSystemAttributeMappingDto uidAttribute = attributes.stream().filter(attribute -> {
			return attribute.isUid();
		}).findFirst().orElse(null);

		// Create default synchronization config
		SysSyncIdentityConfigDto syncConfigCustom = new SysSyncIdentityConfigDto();
		syncConfigCustom.setReconciliation(true);
		syncConfigCustom.setCustomFilter(false);
		syncConfigCustom.setSystemMapping(mapping.getId());
		syncConfigCustom.setCorrelationAttribute(uidAttribute.getId());
		syncConfigCustom.setName(getHelper().createName());
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UPDATE_ENTITY);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.IGNORE);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.CREATE_ENTITY);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);
		syncConfigCustom.setCreateDefaultContract(true);

		return (SysSyncIdentityConfigDto) syncConfigService.save(syncConfigCustom);
	}

	private DefaultRoleSynchronizationExecutorTest getBean() {
		return applicationContext.getAutowireCapableBeanFactory().createBean(this.getClass());
	}
}
