package eu.bcvsolutions.idm.vs.service.impl;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.identityconnectors.framework.common.objects.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationLinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationMissingEntityActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationUnlinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysConnectorKeyDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncIdentityConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncConfigFilter;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncConfigService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.dto.IdmExportImportDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.ic.api.IcAttributeInfo;
import eu.bcvsolutions.idm.ic.api.IcConnector;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;
import eu.bcvsolutions.idm.ic.api.IcConnectorInfo;
import eu.bcvsolutions.idm.ic.api.IcConnectorInstance;
import eu.bcvsolutions.idm.ic.api.IcObjectClassInfo;
import eu.bcvsolutions.idm.ic.api.annotation.IcConnectorClass;
import eu.bcvsolutions.idm.ic.czechidm.domain.CzechIdMIcConvertUtil;
import eu.bcvsolutions.idm.ic.czechidm.domain.IcConnectorConfigurationCzechIdMImpl;
import eu.bcvsolutions.idm.ic.czechidm.service.impl.CzechIdMIcConfigurationService;
import eu.bcvsolutions.idm.ic.czechidm.service.impl.CzechIdMIcConnectorService;
import eu.bcvsolutions.idm.ic.exception.IcException;
import eu.bcvsolutions.idm.ic.impl.IcConnectorInstanceImpl;
import eu.bcvsolutions.idm.vs.VirtualSystemModuleDescriptor;
import eu.bcvsolutions.idm.vs.config.domain.VsConfiguration;
import eu.bcvsolutions.idm.vs.connector.api.VsVirtualConnector;
import eu.bcvsolutions.idm.vs.connector.basic.BasicVirtualConfiguration;
import eu.bcvsolutions.idm.vs.connector.basic.BasicVirtualConnector;
import eu.bcvsolutions.idm.vs.dto.VsSystemDto;
import eu.bcvsolutions.idm.vs.dto.VsSystemImplementerDto;
import eu.bcvsolutions.idm.vs.dto.filter.VsSystemImplementerFilter;
import eu.bcvsolutions.idm.vs.entity.VsAccount;
import eu.bcvsolutions.idm.vs.exception.VsException;
import eu.bcvsolutions.idm.vs.exception.VsResultCode;
import eu.bcvsolutions.idm.vs.service.api.VsSystemImplementerService;
import eu.bcvsolutions.idm.vs.service.api.VsSystemService;

/**
 * Service for virtual system
 *
 * @author Svanda
 * @author Marek Klement
 * @author Ondrej Husnik
 */
@Service
public class DefaultVsSystemService implements VsSystemService {

	private static final Logger LOG = LoggerFactory.getLogger(DefaultVsSystemService.class);

	public static final String NAME_OF_SYNC = "Link virtual accounts to identities";
	public static final String IDM_ATTRIBUTE_NAME = "username";

	private final SysSystemService systemService;
	private final FormService formService;
	private final IdmFormAttributeService formAttributeService;
	private final SysSystemMappingService systemMappingService;
	private final SysSystemAttributeMappingService systemAttributeMappingService;
	private final SysSchemaAttributeService schemaAttributeService;
	@Autowired
	private CzechIdMIcConnectorService czechIdMConnectorService;
	@Autowired
	private CzechIdMIcConfigurationService czechIdMConfigurationService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private VsSystemImplementerService systemImplementerService;
	@Autowired
	private VsConfiguration vsConfiguration;
	@Autowired
	private SysSyncConfigService configService;
	@Autowired
	private SysRoleSystemService roleSystemService;
	@Autowired
	private IdmFormDefinitionService formDefinitionService;

	@Autowired
	public DefaultVsSystemService(SysSystemService systemService, FormService formService,
			SysSystemMappingService systemMappingService,
			SysSystemAttributeMappingService systemAttributeMappingService,
			SysSchemaAttributeService schemaAttributeService, IdmFormAttributeService formAttributeService) {
		Assert.notNull(systemService, "Service is required.");
		Assert.notNull(formService, "Form service (eav) is required.");
		Assert.notNull(systemMappingService, "Service is required.");
		Assert.notNull(systemAttributeMappingService, "Service is required.");
		Assert.notNull(schemaAttributeService, "Service is required.");
		Assert.notNull(formAttributeService, "Service is required.");
		//
		this.systemService = systemService;
		this.formService = formService;
		this.systemMappingService = systemMappingService;
		this.systemAttributeMappingService = systemAttributeMappingService;
		this.schemaAttributeService = schemaAttributeService;
		this.formAttributeService = formAttributeService;
	}

	@Transactional
	@Override
	public IcConnector getConnectorInstance(UUID systemId, IcConnectorInfo connectorInfo) {
		Assert.notNull(systemId, "System ID is required!");
		Assert.notNull(connectorInfo, "Connector info is required.");

		IcConnectorInstance connectorKeyInstance = new IcConnectorInstanceImpl(null, connectorInfo.getConnectorKey(),
				false);
		IcConnectorConfiguration configuration = systemService.getConnectorConfiguration(systemService.get(systemId));
		// VŠ: !Bigger change ... configuration of system is not load from the request,
		// but is online loading.
		// There was problem with implementers. They was updated by implementers stored
		// in the request configuration!
		IcConnector connectorInstance = czechIdMConnectorService.getConnectorInstance(connectorKeyInstance,
				configuration);
		return connectorInstance;
	}

	@Transactional
	@Override
	public VsVirtualConnector getVirtualConnector(UUID systemId, String connectorKey) {
		Assert.notNull(systemId, "System identifier is required.");
		Assert.notNull(connectorKey, "Connector key is required.");

		IcConnectorInfo connectorInfo = this.getConnectorInfo(connectorKey);
		if (connectorInfo == null) {
			throw new IcException(MessageFormat.format(
					"We cannot found connector info by connector key [{0}] from virtual system request!",
					connectorKey));
		}

		IcConnector connectorInstance = this.getConnectorInstance(systemId, connectorInfo);
		if (!(connectorInstance instanceof VsVirtualConnector)) {
			throw new IcException("Found connector instance is not virtual system connector!");
		}
		VsVirtualConnector virtualConnector = (VsVirtualConnector) connectorInstance;
		return virtualConnector;
	}

	@Transactional
	@Override
	public IcConnectorInfo getConnectorInfo(String connectorKey) {
		Assert.notNull(connectorKey, "Connector key is required.");
		return czechIdMConfigurationService.getAvailableLocalConnectors()//
				.stream()//
				.filter(info -> connectorKey.equals(info.getConnectorKey().getFullName()))//
				.findFirst()//
				.orElse(null);
	}

	@Transactional
	@Override
	public void updateSystemConfiguration(IcConnectorConfiguration configuration,
			Class<? extends IcConnector> connectorClass) {
		Assert.notNull(configuration, "Configuration is required.");

		if (!(configuration instanceof IcConnectorConfigurationCzechIdMImpl)) {
			throw new IcException(
					MessageFormat.format("Connector configuration for virtual system must be instance of [{0}]",
							IcConnectorConfigurationCzechIdMImpl.class.getName()));
		}

		UUID systemId = ((IcConnectorConfigurationCzechIdMImpl) configuration).getSystemId();
		if (systemId == null) {
			throw new IcException("System ID cannot be null (for virtual system)");
		}
		SysSystemDto system = this.systemService.get(systemId);
		if (system == null) {
			throw new IcException("System cannot be null (for virtual system)");
		}

		if (!system.isVirtual()) {
			system.setVirtual(true);
			system.getConnectorServer().setPassword(null); // Prevents from resaving password with GuardedString.SECRED_PROXY_STRING loaded above.
			system = this.systemService.save(system);
		}

		IcConnectorClass connectorAnnotation = connectorClass.getAnnotation(IcConnectorClass.class);
		IcConnectorInfo info = CzechIdMIcConvertUtil.convertConnectorClass(connectorAnnotation, connectorClass);

		// Load configuration object
		BasicVirtualConfiguration virtualConfiguration = (BasicVirtualConfiguration) CzechIdMIcConvertUtil
				.convertIcConnectorConfiguration(configuration, connectorAnnotation.configurationClass());

		// Validate configuration
		virtualConfiguration.validate();

		String connectorKey = info.getConnectorKey().getFullName();

		String virtualSystemKey = MessageFormat.format("{0}:systemId={1}", connectorKey, systemId.toString());
		String type = VsAccount.class.getName();

		// Create/Update form definition and attributes
		updateFormDefinition(virtualSystemKey, type, system, virtualConfiguration);

		// Update identity and role implementers relations
		updateSystemImplementers(virtualConfiguration, systemId);
	}

	@Transactional
	@Override
	public SysSystemDto create(VsSystemDto vsSystem) {
		Assert.notNull(vsSystem, "Vs system dto cannot be null (for create new virtual system)");
		Assert.notNull(vsSystem.getName(), "Vs system name cannot be null (for create new virtual system)");
		LOG.info("Create new virtual system with name [{}].", vsSystem.getName());

		SysSystemDto system = new SysSystemDto();

		// Find connector for VS
		Class<? extends VsVirtualConnector> defaultVirtualConnector = BasicVirtualConnector.class;
		IcConnectorClass connectorAnnotation = defaultVirtualConnector.getAnnotation(IcConnectorClass.class);
		IcConnectorInfo info = CzechIdMIcConvertUtil.convertConnectorClass(connectorAnnotation,
				(Class<? extends IcConnector>) defaultVirtualConnector);

		// Set connector key for VS
		system.setConnectorKey(new SysConnectorKeyDto(info.getConnectorKey()));
		system.setName(vsSystem.getName());
		// Create system
		system = this.systemService.save(system, IdmBasePermission.CREATE);

		// Find and update attribute for implementers
		IdmFormDefinitionDto connectorFormDef = this.systemService.getConnectorFormDefinition(system);
		IdmFormAttributeDto implementersFormAttr = connectorFormDef.getMappedAttributeByCode(IMPLEMENTERS_PROPERTY);
		formService.saveValues(system, implementersFormAttr, new ArrayList<>(vsSystem.getImplementers()));

		// Find and update attribute for implementers by roles
		IdmFormAttributeDto implementerRolesFormAttr = connectorFormDef
				.getMappedAttributeByCode(IMPLEMENTER_ROLES_PROPERTY);
		formService.saveValues(system, implementerRolesFormAttr, new ArrayList<>(vsSystem.getImplementerRoles()));

		// Find and update attribute for properties
		IdmFormAttributeDto attributesFormAttr = connectorFormDef.getMappedAttributeByCode(ATTRIBUTES_PROPERTY);
		if (!vsSystem.getAttributes().isEmpty()) {
			formService.saveValues(system, attributesFormAttr, new ArrayList<>(vsSystem.getAttributes()));
		} else {
			List<Serializable> defaultAttributes = Lists
					.newArrayList((Serializable[]) BasicVirtualConfiguration.DEFAULT_ATTRIBUTES);
			defaultAttributes.add(RIGHTS_ATTRIBUTE);
			formService.saveValues(system, attributesFormAttr, defaultAttributes);
		}
		// Update virtual system configuration (implementers and definition)
		VsVirtualConnector virtualConnector = this.getVirtualConnector(system.getId(),
				system.getConnectorKey().getFullName());
		Assert.notNull(virtualConnector, "Connector is required.");
		this.updateSystemConfiguration(virtualConnector.getConfiguration(), virtualConnector.getClass());
		system = systemService.get(system.getId());

		// Search attribute definition for rights and set him to multivalue
		String virtualSystemKey = createVsFormDefinitionKey(system);
		String type = VsAccount.class.getName();
		IdmFormDefinitionDto definition = this.formService.getDefinition(type, virtualSystemKey);
		IdmFormAttributeDto rightsFormAttr = formAttributeService.findAttribute(type, definition.getCode(),
				RIGHTS_ATTRIBUTE);
		if (rightsFormAttr != null) {
			rightsFormAttr.setMultiple(true);
			formService.saveAttribute(rightsFormAttr);
		}

		// Update virtual system configuration (rights attribute ... multivalued)
		virtualConnector = this.getVirtualConnector(system.getId(), system.getConnectorKey().getFullName());
		this.updateSystemConfiguration(virtualConnector.getConfiguration(), virtualConnector.getClass());

		this.systemService.checkSystem(system);

		// Generate schema
		List<SysSchemaObjectClassDto> schemas = this.systemService.generateSchema(system);
		SysSchemaObjectClassDto schemaAccount = schemas.stream()
				.filter(schema -> IcObjectClassInfo.ACCOUNT.equals(schema.getObjectClassName())).findFirst()
				.orElse(null);
		Assert.notNull(schemaAccount, "We cannot found schema for ACCOUNT!");

		// Create mapping by default attributes
		SysSystemMappingDto defaultMapping = this.createDefaultMapping(system, schemaAccount, vsSystem);
		
		// Create mapping for Connection
		SysSystemMappingDto foundMapping = createMapping(system, schemaAccount.getId());
		Assert.notNull(foundMapping, "Mapping not found!");

		SysSystemAttributeMappingDto attributeMapping = createAttributeMapping(foundMapping.getId(),
				schemaAccount.getId());
		Assert.notNull(attributeMapping, "Attribute Mapping not found!");
		
		// Create default role
		IdmRoleDto role = createRoleAndConnectToSystem(vsSystem, system, defaultMapping.getId());
		
		SysSyncIdentityConfigDto synchronization = createReconciliationConfig(attributeMapping.getId(),
				foundMapping.getId(), system.getId(), role == null ? null : role.getId());
		Assert.notNull(synchronization, "Synchronization not found!");
		
		return this.systemService.get(system.getId());
	}
	
	/**
	 * Compose VS key of form attribute.
	 * 
	 * @param system
	 * @return
	 */
	@Override
	public String createVsFormDefinitionKey(SysSystemDto system) {
		if (system != null && system.getConnectorKey() != null && system.getId() != null) {
			return MessageFormat.format("{0}:systemId={1}", system.getConnectorKey().getFullName(),
					system.getId().toString());
		}
		return null;
	}
	
	
	@Override
	public void exportVsDefinition(UUID id, IdmExportImportDto batch) {
		Assert.notNull(id, "Id has to be provided.");
		SysSystemDto system = systemService.get(id);
		if (system.getConnectorKey() != null) {
			String vsKey = this.createVsFormDefinitionKey(system);
			String type = VsAccount.class.getName();
			IdmFormDefinitionDto definition = formService.getDefinition(type, vsKey);
			if (definition != null) {
				formDefinitionService.export(definition.getId(), batch);
			}
		}
	}

	/**
	 * Create role for system and connect it
	 * @param vsSystem 
	 *
	 * @param system
	 *            where we will create role
	 * @param foundMapping
	 *            in what mapping
	 * @return new role
	 */
	private IdmRoleDto createRoleAndConnectToSystem(VsSystemDto vsSystem, SysSystemDto system, UUID foundMapping) {
		if (!vsSystem.isCreateDefaultRole()) {
			return null;
		}
		
		Assert.hasLength(vsSystem.getRoleName(), "Role code is required.");
		
		String code = vsSystem.getRoleName();
		IdmRoleDto newRole = roleService.getByCode(code);
		if (newRole == null) {
			newRole = new IdmRoleDto();
			newRole.setCode(code);
			newRole.setName(code);
			newRole.setPriority(0);
			newRole = roleService.save(newRole);
		}
		//
		SysRoleSystemFilter systemFilter = new SysRoleSystemFilter();
		systemFilter.setRoleId(newRole.getId());
		systemFilter.setSystemId(system.getId());
		
		List<SysRoleSystemDto> systemRoles = roleSystemService.find(systemFilter, null).getContent();

		if (systemRoles.isEmpty()) {
			SysRoleSystemDto systemRole = new SysRoleSystemDto();
			systemRole.setRole(newRole.getId());
			systemRole.setSystem(system.getId());
			systemRole.setSystemMapping(foundMapping);
			roleSystemService.save(systemRole);
		}
		//
		return newRole;
	}

	/**
	 * Create new Reconciliation configuration for future run
	 * 
	 * @author Marek Klement
	 *
	 * @param correlationAttribute
	 *            connecting attribute
	 * @param systemMapping
	 *            mapping
	 * @param systemId
	 *            uuid of system
	 * @param roleId
	 *            uuid of role for system
	 * @return synchronzation configuration
	 */
	private SysSyncIdentityConfigDto createReconciliationConfig(UUID correlationAttribute, UUID systemMapping,
			UUID systemId, UUID roleId) {
		SysSyncConfigFilter filter = new SysSyncConfigFilter();
		filter.setName(NAME_OF_SYNC);
		filter.setSystemId(systemId);
		List<AbstractSysSyncConfigDto> allSync = configService.find(filter, null).getContent();
		SysSyncIdentityConfigDto synchronization;
		if (allSync.size() > 0) {
			synchronization = (SysSyncIdentityConfigDto) allSync.get(0);
		} else {
			synchronization = new SysSyncIdentityConfigDto();
			synchronization.setEnabled(true);
			synchronization.setName(NAME_OF_SYNC);
			synchronization.setCorrelationAttribute(correlationAttribute);
			synchronization.setReconciliation(true);
			synchronization.setSystemMapping(systemMapping);
			synchronization.setUnlinkedAction(SynchronizationUnlinkedActionType.LINK);
			synchronization.setLinkedAction(SynchronizationLinkedActionType.IGNORE);
			synchronization.setMissingEntityAction(SynchronizationMissingEntityActionType.IGNORE);
			synchronization.setDefaultRole(roleId);
			synchronization = (SysSyncIdentityConfigDto) configService.save(synchronization);
		}
		return synchronization;
	}

	/**
	 * Creates attribute mapping for synchronization mapping
	 *
	 * @author Marek Klement
	 * 
	 * @param foundMapping
	 *            created mapping for sync
	 * @param schemaId
	 *            uuid of schema
	 * @return new attribute mapping
	 */
	private SysSystemAttributeMappingDto createAttributeMapping(UUID foundMapping, UUID schemaId) {
		SysSchemaAttributeFilter filter = new SysSchemaAttributeFilter();
		filter.setObjectClassId(schemaId);
		List<SysSchemaAttributeDto> schemaAttributes = schemaAttributeService.find(filter, null).getContent();
		UUID idOfSchemaAttributeName = null;
		for (SysSchemaAttributeDto attribute : schemaAttributes) {
			if (attribute.getName().equals(Name.NAME)) {
				idOfSchemaAttributeName = attribute.getId();
				break;
			}
		}
		//
		SysSystemAttributeMappingDto attributeMapping = systemAttributeMappingService
				.findBySystemMappingAndName(foundMapping, IDM_ATTRIBUTE_NAME);
		//
		if (attributeMapping == null) {
			attributeMapping = new SysSystemAttributeMappingDto();
			attributeMapping.setEntityAttribute(true);
			Assert.notNull(idOfSchemaAttributeName, "Attribute uid name not found!");
			attributeMapping.setSchemaAttribute(idOfSchemaAttributeName);
			attributeMapping.setIdmPropertyName(IDM_ATTRIBUTE_NAME);
			attributeMapping.setSystemMapping(foundMapping);
			attributeMapping.setName(IDM_ATTRIBUTE_NAME);
			attributeMapping.setUid(true);
			attributeMapping = systemAttributeMappingService.save(attributeMapping);
		} else if (!attributeMapping.isUid()) {
			throw new CoreException("Attribute mapping with name was already set and is not IDENTIFIER!");
		}

		return attributeMapping;
	}

	/**
	 * Creates new synchronization mapping by default
	 * 
	 * @author Marek Klement
	 *
	 * @param system
	 *            in what system
	 * @param schemaId
	 *            for what schema
	 * @return sync mapping
	 */
	private SysSystemMappingDto createMapping(SysSystemDto system, UUID schemaId) {
		boolean alreadyExists = false;
		SysSystemMappingDto foundMapping = null;
		List<SysSystemMappingDto> mappings = systemMappingService.findBySystem(system,
				SystemOperationType.SYNCHRONIZATION, SystemEntityType.IDENTITY);
		for (SysSystemMappingDto mapping : mappings) {
			if (mapping.getName().equals(NAME_OF_SYNC)) {
				alreadyExists = true;
				foundMapping = mapping;
				break;
			}
		}
		SysSystemMappingDto newMapping;
		if (!alreadyExists) {
			newMapping = new SysSystemMappingDto();
			newMapping.setName(NAME_OF_SYNC);
			newMapping.setEntityType(SystemEntityType.IDENTITY);
			newMapping.setOperationType(SystemOperationType.SYNCHRONIZATION);
			newMapping.setObjectClass(schemaId);
			newMapping.setProtectionEnabled(true);
			newMapping = systemMappingService.save(newMapping);
		} else {
			newMapping = foundMapping;
			LOG.warn("Attribute mapping already exists!");
		}

		return newMapping;
	}

	/**
	 * Create default mapping for virtual system by given default attributes
	 *
	 * @param system
	 * @param schema
	 * @param vsSystem
	 * @return 
	 */
	private SysSystemMappingDto createDefaultMapping(SysSystemDto system, SysSchemaObjectClassDto schema, VsSystemDto vsSystem) {
		SysSystemMappingDto systemMapping = new SysSystemMappingDto();
		systemMapping.setName("Default provisioning");
		systemMapping.setEntityType(SystemEntityType.IDENTITY);
		systemMapping.setOperationType(SystemOperationType.PROVISIONING);
		systemMapping.setObjectClass(schema.getId());
		systemMapping = systemMappingService.save(systemMapping);

		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());
		List<SysSchemaAttributeDto> schemaAttributes = schemaAttributeService.find(schemaAttributeFilter, null)
				.getContent();
		ArrayList<String> defaultAttributes = Lists.newArrayList(BasicVirtualConfiguration.DEFAULT_ATTRIBUTES);
		List<String> attributes = vsSystem.getAttributes().isEmpty() ? defaultAttributes : vsSystem.getAttributes();
		for (SysSchemaAttributeDto schemaAttr : schemaAttributes) {
			if (IcAttributeInfo.NAME.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(true);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName(IdmIdentity_.username.getName());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			} else if (IcAttributeInfo.ENABLE.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(false);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName(IdmIdentity_.disabled.getName());
				attributeMapping.setTransformToResourceScript("return !attributeValue;");
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			} else if (RIGHTS_ATTRIBUTE.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(false);
				attributeMapping.setEntityAttribute(false);
				attributeMapping.setStrategyType(AttributeMappingStrategyType.MERGE);
				attributeMapping.setExtendedAttribute(false);
				attributeMapping.setName("'Rights' - multivalued merge attribute.");
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			} else if (attributes.contains(schemaAttr.getName()) && defaultAttributes.contains(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(false);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			}
		}
		return systemMapping;
	}

	/**
	 * Create/Update form definition and attributes
	 *
	 * @param key
	 * @param type
	 * @param system
	 * @param virtualConfiguration
	 * @return
	 */
	private IdmFormDefinitionDto updateFormDefinition(String key, String type, SysSystemDto system,
			BasicVirtualConfiguration virtualConfiguration) {
		
		IdmFormDefinitionDto definition = this.formService.getDefinition(type, key);
		List<IdmFormAttributeDto> newFormAttributes = new ArrayList<>();
		
		for (String virtualAttribute : virtualConfiguration.getAttributes()) {
			IdmFormAttributeDto formAttribute = formAttributeService.findAttribute(type, key, virtualAttribute);
			if (formAttribute == null) {
				formAttribute = createFromAttribute(virtualAttribute);
				formAttribute.setFormDefinition(definition == null ? null : definition.getId());
				newFormAttributes.add(formAttribute);
			}
		}

		String definitionName = MessageFormat.format("Virtual system for [{0}]", system.getName());
		if (definition == null) {
			IdmFormDefinitionDto createdDefinition = this.formService.createDefinition(type, key, VirtualSystemModuleDescriptor.MODULE_ID, newFormAttributes);
			createdDefinition.setName(definitionName);
			createdDefinition.setUnmodifiable(true);
			return this.formService.saveDefinition(createdDefinition);
		} else {
			// update form definition name, if needed
			if (!definition.getName().equals(definitionName)) {
				definition.setName(definitionName);
				definition = formService.saveDefinition(definition);
			}
			
			newFormAttributes.forEach(formAttribute -> {
				this.formService.saveAttribute(formAttribute);
			});
			
			// delete VS form attributes which are not defined in the connector attribute list
			IdmFormDefinitionDto connectorFormDefinition = systemService.getConnectorFormDefinition(system);
			Set<String> vsAttributeNames = formService.getValues(system, connectorFormDefinition, ATTRIBUTES_PROPERTY)
					.stream()
					.map(IdmFormValueDto::getStringValue)
					.collect(Collectors.toSet());
			if(!vsAttributeNames.isEmpty()) {
				formService.getAttributes(definition).stream()
					.filter(attr -> !vsAttributeNames.contains(attr.getCode()))
					.forEach(attr -> formService.deleteAttribute(attr));
			}
			
			return definition;
		}
	}

	private IdmFormAttributeDto createFromAttribute(String virtualAttirbute) {
		IdmFormAttributeDto formAttribute = new IdmFormAttributeDto();
		formAttribute.setCode(virtualAttirbute);
		formAttribute.setConfidential(false);
		formAttribute.setPersistentType(PersistentType.TEXT);
		formAttribute.setMultiple(false);
		formAttribute.setName(virtualAttirbute);
		formAttribute.setRequired(false);
		return formAttribute;
	}

	/**
	 * Update identity and role implementers relations
	 *
	 * @param virtualConfiguration
	 * @param systemId
	 */
	private void updateSystemImplementers(BasicVirtualConfiguration virtualConfiguration, UUID systemId) {
		VsSystemImplementerFilter systemImplementerFilter = new VsSystemImplementerFilter();
		systemImplementerFilter.setSystemId(systemId);
		List<VsSystemImplementerDto> systemImplementers = systemImplementerService.find(systemImplementerFilter, null)
				.getContent();

		// Load implementers from config
		List<IdmIdentityDto> implementersFromConfig = this.loadImplementers(virtualConfiguration.getImplementers());
		// Load roles from config
		List<IdmRoleDto> rolesFromConfig = this.loadImplementerRoles(virtualConfiguration.getImplementerRoles(),
				implementersFromConfig);

		List<VsSystemImplementerDto> systemImplementersToAdd = new ArrayList<>();

		// Search system-implementers to delete (for identity)
		List<VsSystemImplementerDto> systemImplementersToDelete = systemImplementers.stream().filter(sysImplementer -> {
			return sysImplementer.getIdentity() != null
					&& !implementersFromConfig.contains(new IdmIdentityDto(sysImplementer.getIdentity()));
		}).collect(Collectors.toList());

		// Search implementers to add (for identity)
		List<IdmIdentityDto> implementersToAdd = implementersFromConfig.stream().filter(implementer -> {
			return !systemImplementers.stream().filter(sysImplementer -> {
				return implementer.getId().equals(sysImplementer.getIdentity());
			}).findFirst().isPresent();
		}).collect(Collectors.toList());

		implementersToAdd.forEach(identity -> {
			VsSystemImplementerDto sysImpl = new VsSystemImplementerDto();
			sysImpl.setIdentity(identity.getId());
			sysImpl.setSystem(systemId);
			systemImplementersToAdd.add(sysImpl);
		});

		// Search system-implementers to delete (for role)
		systemImplementersToDelete.addAll(systemImplementers.stream().filter(sysImplementer -> {
			return sysImplementer.getRole() != null
					&& !rolesFromConfig.contains(new IdmRoleDto(sysImplementer.getRole()));
		}).collect(Collectors.toList()));

		// Search implementers to add (for role)
		List<IdmRoleDto> rolesToAdd = rolesFromConfig.stream().filter(implementer -> {
			return !systemImplementers.stream().filter(sysImplementer -> {
				return implementer.getId().equals(sysImplementer.getRole());
			}).findFirst().isPresent();
		}).collect(Collectors.toList());

		rolesToAdd.forEach(role -> {
			VsSystemImplementerDto sysImpl = new VsSystemImplementerDto();
			sysImpl.setRole(role.getId());
			sysImpl.setSystem(systemId);
			systemImplementersToAdd.add(sysImpl);
		});

		// Save changes (add new and remove old)
		systemImplementerService.saveAll(systemImplementersToAdd);
		systemImplementersToDelete.forEach(sysImpl -> {
			systemImplementerService.delete(sysImpl);
		});
	}

	/**
	 * Load implementers by UUIDs in connector configuration. Throw exception when
	 * identity not found.
	 *
	 * @param implementersString
	 * @return
	 */
	private List<IdmIdentityDto> loadImplementers(UUID[] implementersUUID) {
		
		if (implementersUUID == null) {
			return new ArrayList<>();
		}
		
		List<IdmIdentityDto> implementers = new ArrayList<>(implementersUUID.length);
		for (UUID implementer : implementersUUID) {
			IdmIdentityDto identity = identityService.get(implementer);
			if (identity == null) {
				throw new VsException(VsResultCode.VS_IMPLEMENTER_WAS_NOT_FOUND,
						ImmutableMap.of("implementer", implementer));
			}
			implementers.add(identity);
		}
		return implementers;
	}

	/**
	 * Load implementer roles by UUIDs in connector configuration. If none role are
	 * set and none direct implementers are set, then will be used default role.
	 * Throw exception when identity not found.
	 *
	 * @param implementerRolesUUID
	 * @param implementersFromConfig
	 * @return
	 */
	private List<IdmRoleDto> loadImplementerRoles(UUID[] implementerRolesUUID,
			List<IdmIdentityDto> implementersFromConfig) {
		if ((implementerRolesUUID == null || implementerRolesUUID.length == 0)) {
			List<IdmRoleDto> implementerRoles = new ArrayList<>(1);
			if (CollectionUtils.isEmpty(implementersFromConfig)) {
				// Load default role from configuration
				IdmRoleDto defaultRole = vsConfiguration.getDefaultRole();
				if (defaultRole != null) {
					implementerRoles.add(defaultRole);
				}
			}
			return implementerRoles;
		}

		List<IdmRoleDto> implementerRoles = new ArrayList<>(implementerRolesUUID.length);
		for (UUID implementer : implementerRolesUUID) {
			IdmRoleDto role = roleService.get(implementer);
			if (role == null) {
				throw new VsException(VsResultCode.VS_IMPLEMENTER_ROLE_WAS_NOT_FOUND,
						ImmutableMap.of("role", implementer));
			}
			implementerRoles.add(role);
		}
		return implementerRoles;
	}
}
