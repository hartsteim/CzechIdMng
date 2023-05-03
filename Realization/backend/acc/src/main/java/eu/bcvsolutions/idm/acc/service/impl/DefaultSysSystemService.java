package eu.bcvsolutions.idm.acc.service.impl;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.util.Asserts;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.acc.connector.DefaultConnectorType;
import eu.bcvsolutions.idm.acc.domain.AccGroupPermission;
import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysConnectorKeyDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaObjectClassFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncConfigFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemMappingFilter;
import eu.bcvsolutions.idm.acc.entity.SysConnectorKey_;
import eu.bcvsolutions.idm.acc.entity.SysRemoteServer_;
import eu.bcvsolutions.idm.acc.entity.SysSystem;
import eu.bcvsolutions.idm.acc.entity.SysSystem_;
import eu.bcvsolutions.idm.acc.repository.SysSystemRepository;
import eu.bcvsolutions.idm.acc.service.api.ConnectorType;
import eu.bcvsolutions.idm.acc.service.api.FormPropertyManager;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncConfigService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemFormValueService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.ConfidentialStorage;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.utils.EntityUtils;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.AbstractFormableService;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.entity.IdmPasswordPolicy_;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;
import eu.bcvsolutions.idm.ic.api.IcAttributeInfo;
import eu.bcvsolutions.idm.ic.api.IcConfigurationProperties;
import eu.bcvsolutions.idm.ic.api.IcConfigurationProperty;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;
import eu.bcvsolutions.idm.ic.api.IcConnectorInstance;
import eu.bcvsolutions.idm.ic.api.IcConnectorKey;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.api.IcConnectorServer;
import eu.bcvsolutions.idm.ic.api.IcObjectClass;
import eu.bcvsolutions.idm.ic.api.IcObjectClassInfo;
import eu.bcvsolutions.idm.ic.api.IcObjectPoolConfiguration;
import eu.bcvsolutions.idm.ic.api.IcSchema;
import eu.bcvsolutions.idm.ic.czechidm.domain.IcConnectorConfigurationCzechIdMImpl;
import eu.bcvsolutions.idm.ic.exception.IcRemoteServerException;
import eu.bcvsolutions.idm.ic.impl.IcConfigurationPropertiesImpl;
import eu.bcvsolutions.idm.ic.impl.IcConnectorConfigurationImpl;
import eu.bcvsolutions.idm.ic.impl.IcConnectorInstanceImpl;
import eu.bcvsolutions.idm.ic.impl.IcConnectorKeyImpl;
import eu.bcvsolutions.idm.ic.impl.IcObjectPoolConfigurationImpl;
import eu.bcvsolutions.idm.ic.service.api.IcConfigurationFacade;
import eu.bcvsolutions.idm.ic.service.api.IcConnectorFacade;

/**
 * Default target system configuration service.
 *
 * @author Radek Tomiška
 * @author Ondrej Husnik
 *
 */
@Service
public class DefaultSysSystemService
		extends AbstractFormableService<SysSystemDto, SysSystem, SysSystemFilter>
		implements SysSystemService {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DefaultSysSystemService.class);

	private final SysSystemRepository systemRepository;
	private final IcConfigurationFacade icConfigurationFacade;
	private final SysSchemaObjectClassService objectClassService;
	private final SysSchemaAttributeService attributeService;
	private final SysSyncConfigService synchronizationConfigService;
	private final FormPropertyManager formPropertyManager;
	private final ConfidentialStorage confidentialStorage;
	private final SysSystemFormValueService systemFormValueService;
	private final SysSystemMappingService systemMappingService;
	private final SysSystemAttributeMappingService systemAttributeMappingService;
	private final SysSchemaObjectClassService schemaObjectClassService;
	@Autowired
	@Lazy
	@Qualifier("default-connector-type")
	private DefaultConnectorType defaultConnectorType;

	@Autowired
	@Lazy
	public DefaultSysSystemService(
			SysSystemRepository systemRepository,
			FormService formService,
			IcConfigurationFacade icConfigurationFacade,
			SysSchemaObjectClassService objectClassService,
			SysSchemaAttributeService attributeService,
			SysSyncConfigService synchronizationConfigService,
			FormPropertyManager formPropertyManager,
			ConfidentialStorage confidentialStorage,
			IcConnectorFacade connectorFacade,
			SysSystemFormValueService systemFormValueService,
			SysSystemMappingService systemMappingService,
			SysSystemAttributeMappingService systemAttributeMappingService,
			SysSchemaObjectClassService schemaObjectClassService,
			EntityEventManager entityEventManager) {
		super(systemRepository, entityEventManager, formService);
		//
		Assert.notNull(icConfigurationFacade, "Connector configuration facade is required.");
		Assert.notNull(objectClassService, "Service is required.");
		Assert.notNull(attributeService, "Service is required.");
		Assert.notNull(synchronizationConfigService, "Service is required.");
		Assert.notNull(formPropertyManager, "Manager is required.");
		Assert.notNull(confidentialStorage, "Confidential storage is required.");
		Assert.notNull(systemFormValueService, "Service is required.");
		Assert.notNull(systemMappingService, "Service is required.");
		Assert.notNull(systemAttributeMappingService, "Service is required.");
		Assert.notNull(schemaObjectClassService, "Service is required.");
		//
		this.systemRepository = systemRepository;
		this.icConfigurationFacade = icConfigurationFacade;
		this.objectClassService = objectClassService;
		this.attributeService = attributeService;
		this.synchronizationConfigService = synchronizationConfigService;
		this.formPropertyManager = formPropertyManager;
		this.confidentialStorage = confidentialStorage;
		this.systemFormValueService = systemFormValueService;
		this.systemMappingService = systemMappingService;
		this.systemAttributeMappingService = systemAttributeMappingService;
		this.schemaObjectClassService = schemaObjectClassService;
	}

	@Override
	public AuthorizableType getAuthorizableType() {
		return new AuthorizableType(AccGroupPermission.SYSTEM, getEntityClass());
	}

	@Override
	@Transactional
	public SysSystemDto saveInternal(SysSystemDto dto) {
		if (dto != null) {
			if (dto.isDisabledProvisioning() && !dto.isReadonly() && !dto.isDisabled()) {
				// when provisioning is disabled (~super disabled), then system is disabled too (prevent to execute already created provisioning operations)
				dto.setDisabled(true);
			}
		}
		return super.saveInternal(dto);
	}
	
	@Override
	protected SysSystem toEntity(SysSystemDto dto, SysSystem entity) {
		SysSystem system = super.toEntity(dto, entity);
		if (system != null) {
			system.setRemote(system.getRemoteServer() != null); // flag is deprecated @since 10.8.0
		}
		return system;
	}

	@Override
	@Transactional(readOnly = true)
	public SysSystemDto getByCode(String name) {
		return toDto(systemRepository.findOneByName(name));
	}
	
	@Override
	public boolean supportsToDtoWithFilter() {
		return true;
	}

	@Override
	protected List<Predicate> toPredicates(Root<SysSystem> root, CriteriaQuery<?> query, CriteriaBuilder builder, SysSystemFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
		// quick
		if (StringUtils.isNotEmpty(filter.getText())) {
			predicates.add(
					builder.or(
							builder.like(builder.lower(root.get(SysSystem_.name)), "%" + filter.getText().toLowerCase() + "%"),
							builder.like(builder.lower(root.get(SysSystem_.description)), "%" + filter.getText().toLowerCase() + "%")
					));

		}
		if (filter.getVirtual() != null) {
			predicates.add(builder.equal(root.get(SysSystem_.virtual), filter.getVirtual()));
		}
		if (filter.getPasswordPolicyGenerationId() != null) {
			predicates.add(builder.equal(root.get(SysSystem_.passwordPolicyGenerate).get(IdmPasswordPolicy_.id), filter.getPasswordPolicyGenerationId()));
		}
		if (filter.getPasswordPolicyValidationId() != null) {
			predicates.add(builder.equal(root.get(SysSystem_.passwordPolicyValidate).get(IdmPasswordPolicy_.id), filter.getPasswordPolicyValidationId()));
		}
		//
		UUID remoteServerId = filter.getRemoteServerId();
		if (remoteServerId != null) {
			predicates.add(builder.equal(root.get(SysSystem_.remoteServer).get(SysRemoteServer_.id), remoteServerId));
		}
		//
		Boolean remote = filter.getRemote();
		if (remote != null) {
			if (remote) {
				predicates.add(
						builder.or(
								builder.isNotNull(root.get(SysSystem_.remoteServer)), 
								builder.isTrue(root.get(SysSystem_.remote)) // <= 10.7.x
						)
				);
			} else {
				// ~ local connector
				predicates.add(
						builder.and(
								builder.isNull(root.get(SysSystem_.remoteServer)), 
								builder.isFalse(root.get(SysSystem_.remote)) // <= 10.7.x
						)
				);
			}
		}
		//
		String connectorFramework = filter.getConnectorFramework();
		if (StringUtils.isNotEmpty(connectorFramework)) {
			predicates.add(builder.equal(root.get(SysSystem_.connectorKey).get(SysConnectorKey_.framework), connectorFramework));
		}
		//
		String connectorName = filter.getConnectorName();
		if (StringUtils.isNotEmpty(connectorName)) {
			predicates.add(builder.equal(root.get(SysSystem_.connectorKey).get(SysConnectorKey_.connectorName), connectorName));
		}
		//
		String connectorBundleName = filter.getConnectorBundleName();
		if (StringUtils.isNotEmpty(connectorBundleName)) {
			predicates.add(builder.equal(root.get(SysSystem_.connectorKey).get(SysConnectorKey_.bundleName), connectorBundleName));
		}
		//
		String connectorVersion = filter.getConnectorVersion();
		if (StringUtils.isNotEmpty(connectorVersion)) {
			predicates.add(builder.equal(root.get(SysSystem_.connectorKey).get(SysConnectorKey_.bundleVersion), connectorVersion));
		}
		//
		return predicates;
	}

	@Override
	@Transactional
	public IcConnectorConfiguration getConnectorConfiguration(SysSystemDto system) {
		Assert.notNull(system, "System is required.");
		if (system.getConnectorKey() == null) {
			return null;
		}
		IcConnectorConfiguration connectorConfig = null;
		// load connector properties, different between local and remote
		IcConnectorInstance connectorInstance = getConnectorInstance(system);
		connectorConfig = icConfigurationFacade.getConnectorConfiguration(connectorInstance);

		// load filled form values
		IdmFormDefinitionDto formDefinition = getConnectorFormDefinition(connectorInstance);
		IdmFormInstanceDto formInstance = getFormService().getFormInstance(system, formDefinition);
		Map<String, List<IdmFormValueDto>> attributeValues = formInstance.toValueMap();

		// fill connector configuration from form values
		IcConnectorConfigurationImpl configuration = null;
		if (SysSystemService.CONNECTOR_FRAMEWORK_CZECHIDM.equals(connectorInstance.getConnectorKey().getFramework())) {
			// For CzechIdM connector framework is needs system ID (exactly for virtual systems).
			configuration = new IcConnectorConfigurationCzechIdMImpl();
			((IcConnectorConfigurationCzechIdMImpl) configuration).setSystemId(system.getId());
		} else {
			configuration = new IcConnectorConfigurationImpl();
		}
		// Create configuration for pool
		fillPoolingConnectorConfiguration(configuration, connectorInstance, system);

		// Load operation options
		configuration.setOperationOptions(getOperationOptionsForSystem(connectorInstance, system));

		IcConfigurationProperties properties = new IcConfigurationPropertiesImpl();
		configuration.setConfigurationProperties(properties);
		//
		for (short seq = 0; seq < connectorConfig.getConfigurationProperties().getProperties().size(); seq++) {
			IcConfigurationProperty propertyConfig = connectorConfig.getConfigurationProperties().getProperties()
					.get(seq);

			IdmFormAttributeDto formAttribute = formInstance.getMappedAttributeByCode(propertyConfig.getName());
			List<IdmFormValueDto> eavAttributeValues = attributeValues.get(formAttribute.getCode());

			// create property instance from configuration
			IcConfigurationProperty property = formPropertyManager.toConnectorProperty(propertyConfig,
					eavAttributeValues);
			if (property.getValue() != null) {
				// only filled values to configuration
				properties.getProperties().add(property);
			}
		}

		return configuration;
	}

	@Override
	@Transactional
	public IcConnectorObject readConnectorObject(UUID systemId, String uid, IcObjectClass objectClass) {
		return readConnectorObject(systemId, uid, objectClass, defaultConnectorType);
	}
	
	@Override
	public IcConnectorObject readConnectorObject(UUID systemId, String uid, IcObjectClass objectClass, ConnectorType connectorTypeBySystem) {
		Assert.notNull(systemId, "System ID cannot be null!");
		Assert.notNull(uid, "Account UID cannot be null!");
		Assert.notNull(connectorTypeBySystem, "Connector-type cannot be null!");
		SysSystemDto system = this.get(systemId);
		Assert.notNull(system, "System cannot be null!");
		
		return connectorTypeBySystem.readConnectorObject(system, uid, objectClass);

	}


	@Override
	@Transactional
	public void checkSystem(SysSystemDto system) {
		Assert.notNull(system, "System is required.");

		// Find connector identification persisted in system
		IcConnectorKey connectorKey = system.getConnectorKey();
		if (connectorKey == null) {
			throw new ResultCodeException(AccResultCode.CONNECTOR_KEY_FOR_SYSTEM_NOT_FOUND,
					ImmutableMap.of("system", system.getName()));
		}

		// Find connector configuration persisted in system
		IcConnectorConfiguration connectorConfig = getConnectorConfiguration(system);
		if (connectorConfig == null) {
			throw new ResultCodeException(AccResultCode.CONNECTOR_CONFIGURATION_FOR_SYSTEM_NOT_FOUND,
					ImmutableMap.of("system", system.getName()));
		}

		// Call IC module
		icConfigurationFacade.test(getConnectorInstance(system), connectorConfig);
	}

	@Override
	@Transactional
	public List<SysSchemaObjectClassDto> generateSchema(SysSystemDto system) {
		Assert.notNull(system, "System is required.");
		Assert.notNull(system.getId(), "System identifier is required.");

		// Find connector identification persisted in system
		IcConnectorKey connectorKey = system.getConnectorKey();
		if (connectorKey == null) {
			throw new ResultCodeException(AccResultCode.CONNECTOR_KEY_FOR_SYSTEM_NOT_FOUND,
					ImmutableMap.of("system", system.getName()));
		}

		// Find connector configuration persisted in system
		IcConnectorConfiguration connectorConfig = getConnectorConfiguration(system);
		if (connectorConfig == null) {
			throw new ResultCodeException(AccResultCode.CONNECTOR_CONFIGURATION_FOR_SYSTEM_NOT_FOUND,
					ImmutableMap.of("system", system.getName()));
		}

		// Call IC module and find schema for given connector key and
		// configuration
		IcSchema icSchema = null;

		try {
			icSchema = icConfigurationFacade.getSchema(getConnectorInstance(system), connectorConfig);
		} catch (Exception ex) {
			throw new ResultCodeException(AccResultCode.CONNECTOR_SCHEMA_GENERATION_EXCEPTION,
					ImmutableMap.of("system", system.getName(), "exception", ex.getLocalizedMessage()), ex);
		}
		if (icSchema == null) {
			throw new ResultCodeException(AccResultCode.CONNECTOR_SCHEMA_FOR_SYSTEM_NOT_FOUND,
					ImmutableMap.of("system", system.getName()));
		}

		// Load existing object class from system
		SysSchemaObjectClassFilter objectClassFilter = new SysSchemaObjectClassFilter();
		objectClassFilter.setSystemId(system.getId());
		List<SysSchemaObjectClassDto> sysObjectClassesInSystem = null;
		Page<SysSchemaObjectClassDto> page = objectClassService.find(objectClassFilter, null);
		sysObjectClassesInSystem = page.getContent();

		// Convert IC schema to ACC entities
		List<IcObjectClassInfo> declaredObjectClasses = icSchema.getDeclaredObjectClasses();
		List<SysSchemaObjectClassDto> sysObjectClasses = new ArrayList<>(declaredObjectClasses.size());
		List<SysSchemaAttributeDto> sysAttributes = new ArrayList<>();
		for (IcObjectClassInfo objectClass : declaredObjectClasses) {

			// We can create only IC schemas, it means only schemas created for
			// __ACCOUNT__ and __GROUP__
			if (!(objectClass.getType().startsWith("__") && objectClass.getType().endsWith("__"))) {
				continue;
			}
			SysSchemaObjectClassDto sysObjectClass = null;
			// If existed some object class in system, then we will compared
			// every object with object class in resource
			// If will be same (same name), then we do only refresh object
			// values from resource
			if (sysObjectClassesInSystem != null) {
				Optional<SysSchemaObjectClassDto> objectClassSame = sysObjectClassesInSystem.stream()
						.filter(objectClassInSystem -> { //
							return objectClassInSystem.getObjectClassName().equals(objectClass.getType());
						}) //
						.findFirst();
				if (objectClassSame.isPresent()) {
					sysObjectClass = objectClassSame.get();
				}
			}
			// Convert IC object class to ACC (if is null, then will be created
			// new instance)
			sysObjectClass = convertIcObjectClassInfo(objectClass, sysObjectClass);
			sysObjectClass.setSystem(system.getId());

			// object class may not exist
			sysObjectClass = schemaObjectClassService.save(sysObjectClass);

			sysObjectClasses.add(sysObjectClass);

			List<SysSchemaAttributeDto> attributesInSystem = null;
			// Load existing attributes for existing object class in system
			if (sysObjectClass.getId() != null) {
				SysSchemaAttributeFilter attFilter = new SysSchemaAttributeFilter();
				attFilter.setSystemId(system.getId());
				attFilter.setObjectClassId(sysObjectClass.getId());

				Page<SysSchemaAttributeDto> attributesInSystemPage = attributeService.find(attFilter, null);
				attributesInSystem = attributesInSystemPage.getContent();
			}
			for (IcAttributeInfo attribute : objectClass.getAttributeInfos()) {
				// If will be IC and ACC attribute same (same name), then we
				// will do only refresh object values from resource
				SysSchemaAttributeDto sysAttribute = null;
				if (attributesInSystem != null) {
					Optional<SysSchemaAttributeDto> sysAttributeOptional = attributesInSystem.stream().filter(a -> {
						return a.getName().equals(attribute.getName());
					}).findFirst();
					if (sysAttributeOptional.isPresent()) {
						sysAttribute = sysAttributeOptional.get();
					}
				}
				sysAttribute = convertIcAttributeInfo(attribute, sysAttribute);
				sysAttribute.setObjectClass(sysObjectClass.getId());
				sysAttributes.add(sysAttribute);
			}
		}

		// Persist generated schema to system
		sysObjectClasses = (List<SysSchemaObjectClassDto>) objectClassService.saveAll(sysObjectClasses);
		attributeService.saveAll(sysAttributes);
		return sysObjectClasses;
	}

	@Override
	@Transactional
	public IdmFormDefinitionDto getConnectorFormDefinition(IcConnectorInstance connectorInstance) {
		Assert.notNull(connectorInstance, "Connector instance is required to create connector form definition.");
		Assert.notNull(connectorInstance.getConnectorKey(), "Connector key is required to create connector form definition.");
		//
		IdmFormDefinitionDto formDefinition = getFormService().getDefinition(SysSystem.class,
				connectorInstance.getConnectorKey().getFullName());
		//
		formDefinition = resolveConnectorFormDefinition(formDefinition, connectorInstance);
		formDefinition.setUnmodifiable(true);
		//
		return formDefinition;
	}
	
	@Override
	@Transactional
	public IdmFormDefinitionDto getConnectorFormDefinition(SysSystemDto system) {
		Assert.notNull(system, "System is required to create connector form definition.");
		//
		return getConnectorFormDefinition(getConnectorInstance(system));
	}

	@Override
	@Transactional
	public IdmFormDefinitionDto getPoolingConnectorFormDefinition(IcConnectorInstance connectorInstance) {
		Assert.notNull(connectorInstance, "Connector instance is required.");
		Assert.notNull(connectorInstance.getConnectorKey(), "Connector key is required.");

		IdmFormDefinitionDto formDefinitionPooling = getFormService().getDefinition(SysSystem.class,
				getPoolingFormDefinitionCode(connectorInstance));

		if (formDefinitionPooling == null) {
			formDefinitionPooling = createPoolingFormDefinition(connectorInstance);
		}
		return formDefinitionPooling;
	}
	
	@Override
	@Transactional
	public IdmFormDefinitionDto getPoolingConnectorFormDefinition(SysSystemDto system) {
		Assert.notNull(system, "System is required to create pooling connector form definition.");
		//
		return getPoolingConnectorFormDefinition(getConnectorInstance(system));
	}

	@Override
	@Transactional
	public IdmFormDefinitionDto getOperationOptionsConnectorFormDefinition(IcConnectorInstance connectorInstance) {
		Assert.notNull(connectorInstance, "Connector instance is required.");
		Assert.notNull(connectorInstance.getConnectorKey(), "Connector key is required.");

		IdmFormDefinitionDto formDefinition = getFormService().getDefinition(SysSystem.class,
				getOperationOptionsFormDefinitionCode(connectorInstance.getConnectorKey()));

		if (formDefinition == null) {
			formDefinition = createOperationOptionsFormDefinition(connectorInstance);
		}
		return formDefinition;
	}
	
	@Override
	@Transactional
	public IdmFormDefinitionDto getOperationOptionsConnectorFormDefinition(SysSystemDto system) {
		Assert.notNull(system, "System is required to create additional options connector form definition.");
		//
		return getOperationOptionsConnectorFormDefinition(getConnectorInstance(system));
	}

	@Override
	@Transactional
	public SysSystemDto duplicate(UUID id) {
		SysSystemDto originalSystem = this.get(id);
		Asserts.notNull(originalSystem, "System must be found!");

		// Clone and save system
		SysSystemDto clone = this.clone(id);
		String name = MessageFormat.format("{0}{1}", "Copy-of-", clone.getName());
		name = this.duplicateName(name, 0);

		clone.setName(name);
		// Set as inactive system
		clone.setDisabled(true);
		SysSystemDto system = this.save(clone);

		// Cache old and new IDs
		Map<UUID, UUID> schemaAttributesCache = new HashMap<UUID, UUID>();
		Map<UUID, UUID> mappedAttributesCache = new HashMap<UUID, UUID>();

		// Duplicate connector configuration values in EAV
		IcConnectorInstance connectorInstance = getConnectorInstance(originalSystem);

		if (connectorInstance != null && connectorInstance.getConnectorKey() != null && connectorInstance.getConnectorKey().getFramework() != null) {
			IdmFormDefinitionDto formDefinition = getConnectorFormDefinition(connectorInstance);
			List<IdmFormValueDto> originalFormValues = this.getFormService().getValues(id, SysSystem.class,
					formDefinition);
			SysSystem systemEntity = getEntity(system.getId());
			originalFormValues.stream().forEach(value -> {
				systemFormValueService.duplicate(value.getId(), systemEntity);
			});
		}

		// Duplicate schema
		SysSchemaObjectClassFilter objectClassFilter = new SysSchemaObjectClassFilter();
		objectClassFilter.setSystemId(id);
		// Save duplicated mappings to resolve connected mapping
		Map<UUID, SysSystemMappingDto> duplicatedMappings = new HashMap<>(); // <originalMappingId, duplicatedMapping>
		Map<UUID, UUID> connectedMappings = new HashMap<>(); // <originalMappingId, connectedMappingId>

		objectClassService.find(objectClassFilter, null).getContent().stream().forEach(schema -> {
			UUID originalSchemaId = schema.getId();
			SysSchemaObjectClassDto duplicatedSchema = this.duplicateSchema(originalSchemaId, system,
					schemaAttributesCache);

			// Duplicate mapped attributes
			SysSystemMappingFilter systemMappingFilter = new SysSystemMappingFilter();
			systemMappingFilter.setSystemId(id);
			systemMappingService.find(systemMappingFilter, null).getContent().stream().filter(mapping -> {
				// Find mapping for this schema
				return mapping.getObjectClass().equals(originalSchemaId);
			}).forEach(mapping -> {
				if (mapping.getConnectedSystemMappingId() != null) {
					connectedMappings.put(mapping.getId(), mapping.getConnectedSystemMappingId());
				}
				final UUID originalMappingId = mapping.getId();
				SysSystemMappingDto duplicatedMapping = systemMappingService.duplicateMapping(originalMappingId,
						duplicatedSchema, schemaAttributesCache, mappedAttributesCache, false);
				duplicatedMappings.put(originalMappingId, duplicatedMapping);
				// Duplicate sync configs
				List<AbstractSysSyncConfigDto> syncConfigs = findSyncConfigs(id);
				syncConfigs.stream().filter(syncConfig -> {

					// Find configuration of sync for this mapping
					return syncConfig.getSystemMapping().equals(originalMappingId);
				}).forEach(syncConfig -> {
					UUID syncConfigId = syncConfig.getId();
					duplicateSyncConf(syncConfigId, duplicatedMapping, mappedAttributesCache);
				});
			});
		});

		resolveConnectedMappings(duplicatedMappings, connectedMappings);

		return system;
	}

	/**
	 * Resolve connected mappings
	 * 
	 * @param duplicatedMappings
	 * @param connectedMappings
	 */
	private void resolveConnectedMappings(Map<UUID, SysSystemMappingDto> duplicatedMappings, Map<UUID, UUID> connectedMappings) {
		connectedMappings.entrySet().forEach(entry -> {
			SysSystemMappingDto duplicatedMapping = duplicatedMappings.get(entry.getKey());
			SysSystemMappingDto duplicatedConnectedMapping = duplicatedMappings.get(entry.getValue());
			duplicatedMapping.setConnectedSystemMappingId(duplicatedConnectedMapping.getId());
			systemMappingService.save(duplicatedMapping);
		});
	}

	@Override
	public SysSystemDto clone(UUID id) {
		SysSystemDto originalSystem = this.get(id);
		Asserts.notNull(originalSystem, "System must be found!");

		originalSystem.setId(null);
		EntityUtils.clearAuditFields(originalSystem);
		return originalSystem;
	}

	@Override
	protected SysSystemDto internalExport(UUID id) {
		SysSystemDto system = super.internalExport(id);
		// Set as inactive system.
		system.setDisabled(true);

		// Set remote connector server password to the null. We don't want update
		// password on target IdM.
		if (system.getConnectorServer() != null) {
			system.getConnectorServer().setPassword(null);
		}

		return system;
	}

	private SysSchemaObjectClassDto convertIcObjectClassInfo(IcObjectClassInfo objectClass,
															 SysSchemaObjectClassDto sysObjectClass) {
		if (objectClass == null) {
			return null;
		}
		if (sysObjectClass == null) {
			sysObjectClass = new SysSchemaObjectClassDto();
		}
		sysObjectClass.setObjectClassName(objectClass.getType());
		sysObjectClass.setAuxiliary(objectClass.isAuxiliary());
		sysObjectClass.setContainer(objectClass.isContainer());
		return sysObjectClass;
	}

	private SysSchemaAttributeDto convertIcAttributeInfo(IcAttributeInfo attributeInfo, SysSchemaAttributeDto sysAttribute) {
		if (attributeInfo == null) {
			return null;
		}
		if (sysAttribute == null) {
			sysAttribute = new SysSchemaAttributeDto();
		}
		sysAttribute.setClassType(attributeInfo.getClassType());
		sysAttribute.setName(attributeInfo.getName());
		sysAttribute.setMultivalued(attributeInfo.isMultivalued());
		sysAttribute.setNativeName(attributeInfo.getNativeName());
		sysAttribute.setReadable(attributeInfo.isReadable());
		sysAttribute.setRequired(attributeInfo.isRequired());
		sysAttribute.setReturnedByDefault(attributeInfo.isReturnedByDefault());
		sysAttribute.setUpdateable(attributeInfo.isUpdateable());
		sysAttribute.setCreateable(attributeInfo.isCreateable());
		return sysAttribute;
	}

	/**
	 * Creates configuration for pool by EAV values
	 *
	 * @param configuration
	 * @param connectorInstance
	 * @param system
	 */
	private void fillPoolingConnectorConfiguration(IcConnectorConfigurationImpl configuration,
												   IcConnectorInstance connectorInstance, SysSystemDto system) {

		IdmFormDefinitionDto formDefinition = getPoolingConnectorFormDefinition(connectorInstance);
		if (formDefinition == null) {
			return;
		}
		IdmFormInstanceDto formInstance = getFormService().getFormInstance(system, formDefinition);
		if (formInstance == null) {
			return;
		}

		IcObjectPoolConfiguration connectorPoolConfiguration = configuration.getConnectorPoolConfiguration();
		if (connectorPoolConfiguration == null) {
			connectorPoolConfiguration = new IcObjectPoolConfigurationImpl();
		}

		Serializable poolingSupported = formInstance.toSinglePersistentValue(POOLING_SUPPORTED_PROPERTY);
		if (poolingSupported instanceof Boolean) {
			configuration.setConnectorPoolingSupported(((Boolean) poolingSupported).booleanValue());
		} else {
			configuration.setConnectorPoolingSupported(false);
		}
		configuration.setConnectorPoolConfiguration(connectorPoolConfiguration);

		Serializable minIdle = formInstance.toSinglePersistentValue(MIN_IDLE_PROPERTY);
		if (minIdle instanceof Integer) {
			connectorPoolConfiguration.setMinIdle((int) minIdle);
		}
		Serializable minEvicTime = formInstance.toSinglePersistentValue(MIN_TIME_TO_EVIC_PROPERTY);
		if (minEvicTime instanceof Long) {
			connectorPoolConfiguration.setMinEvictableIdleTimeMillis((long) minEvicTime);
		}
		Serializable maxIdle = formInstance.toSinglePersistentValue(MAX_IDLE_PROPERTY);
		if (maxIdle instanceof Integer) {
			connectorPoolConfiguration.setMaxIdle((int) maxIdle);
		}
		Serializable maxObjects = formInstance.toSinglePersistentValue(MAX_OBJECTS_PROPERTY);
		if (maxObjects instanceof Integer) {
			connectorPoolConfiguration.setMaxObjects((int) maxObjects);
		}
		Serializable maxWait = formInstance.toSinglePersistentValue(MAX_WAIT_PROPERTY);
		if (maxWait instanceof Long) {
			connectorPoolConfiguration.setMaxWait((long) maxWait);
		}
	}

	/**
	 * Creates configuration for operation options by EAV values
	 *
	 * @param connectorInstance
	 * @param system
	 */
	private Map<String, Object> getOperationOptionsForSystem(IcConnectorInstance connectorInstance, SysSystemDto system) {
		IdmFormDefinitionDto formDefinition = getOperationOptionsConnectorFormDefinition(connectorInstance);
		if (formDefinition == null) {
			return Collections.emptyMap();
		}
		IdmFormInstanceDto formInstance = getFormService().getFormInstance(system, formDefinition);
		if (formInstance == null) {
			return Collections.emptyMap();
		}

		Map<String, List<IdmFormValueDto>> optionToValues = formInstance.toValueMap();
		return optionToValues
				.keySet()
				.stream()
				.filter(key -> !optionToValues.get(key).isEmpty())
				.collect(Collectors.toMap(key -> key, key -> {
					List<IdmFormValueDto> values = optionToValues.get(key);
					//
					return values.size() > 1
							? 
							toArray(values, formDefinition.getMappedAttributeByCode(key).getPersistentType()) 
							: 
							values.get(0).getValue();
				}));
	}


	/**
	 * This method solves issue of instantiation of multivalue array, which then can be sent to ConnId. If an array does not have specific type
	 * (String, int, etc..), then ConnId will throw an exception, because it checks the type if passed values. This is the reason, why we need
	 * to determine an array type by persistent type of EAV.
	 *
	 * @param values {@link List} of {@link IdmFormValueDto} to convert to an array
	 * @param persistentType {@link PersistentType} of given values
	 * @return Correctly typed array containing all values passed in values argument of this method
	 * @throws IllegalArgumentException if a type, which is not supported, is passed as an argument
	 */
	 private Object[] toArray(List<IdmFormValueDto> values, PersistentType persistentType) {
		switch (persistentType){
			case SHORTTEXT:
			case TEXT:
				return values.stream().map(IdmFormValueDto::getValue).toArray(value -> new String[values.size()]);
			case LONG:
				return values.stream().map(IdmFormValueDto::getValue).toArray(value -> new Long[values.size()]);
			case INT:
				return values.stream().map(IdmFormValueDto::getValue).toArray(value -> new Integer[values.size()]);
			case BOOLEAN:
				return values.stream().map(IdmFormValueDto::getValue).toArray(value -> new Boolean[values.size()]);
			case UUID:
				return values.stream().map(IdmFormValueDto::getValue).toArray(value -> new UUID[values.size()]);
			case DOUBLE:
				return values.stream().map(IdmFormValueDto::getValue).toArray(value -> new Double[values.size()]);
			case CHAR:
				return values.stream().map(IdmFormValueDto::getValue).toArray(value -> new Character[values.size()]);
			default:
				throw new IllegalArgumentException(String.format("Invalid multivalue operation option type %s.", persistentType));
		}
	}


	/**
	 * Create form definition to given connectorInstance by connector properties
	 *
	 * @param formDefinition
	 * @param connectorInstance
	 * @return
	 */
	private synchronized IdmFormDefinitionDto resolveConnectorFormDefinition(IdmFormDefinitionDto formDefinition, IcConnectorInstance connectorInstance) {
		IcConnectorConfiguration conf = null;
		try {
			conf = icConfigurationFacade.getConnectorConfiguration(connectorInstance);
		} catch(IcRemoteServerException ex) {
			if (formDefinition != null) {
				LOG.info("Connector definition cannot be updated, because connector returns remote-server exception."
						+ " Exists definition will be used.", ex);
				
				return formDefinition;
			}
			throw ex;
		}
		if (conf == null) {
			throw new IllegalStateException(MessageFormat.format("Connector with key [{0}] was not found on classpath.",
					connectorInstance.getConnectorKey().getFullName()));
		}
		
		//
		List<IcConfigurationProperty> properties = conf.getConfigurationProperties().getProperties();
		List<IdmFormAttributeDto> formAttributes = new ArrayList<>(properties.size());
		//
		if (formDefinition == null) {
			// create new form definition
			for (short seq = 0; seq < properties.size(); seq++) {
				IcConfigurationProperty property = properties.get(seq);
				IdmFormAttributeDto attribute = formPropertyManager.toFormAttribute(property);
				attribute.setSeq(seq);
				formAttributes.add(attribute);
			}
			formDefinition = getFormService().createDefinition(
					SysSystem.class,
					connectorInstance.getConnectorKey().getFullName(),
					formAttributes
			);
		} else {
			// check attributes / attribute can be added into form definition
			// update attribute is not supported now
			for (short seq = 0; seq < properties.size(); seq++) {
				IcConfigurationProperty property = properties.get(seq);

				IdmFormAttributeDto formAttribute = formDefinition.getMappedAttributeByCode(property.getName());
				if (formAttribute == null) {
					LOG.info("Connector attribute [{}] not found in definition, attributte will be added into definition with code [{}].",
							property.getName(),
							formDefinition.getCode());
					//
					formAttribute = formPropertyManager.toFormAttribute(property);
					formAttribute.setFormDefinition(formDefinition.getId());
					formAttribute.setSeq(seq);
					formAttribute = getFormService().saveAttribute(formAttribute);
					formDefinition.addFormAttribute(formAttribute);
				}
			}
		}
		//
		return formDefinition;
	}

	/**
	 * Create form definition for connector pooling configuration
	 *
	 * @param connectorInstance
	 * @return
	 */
	private synchronized IdmFormDefinitionDto createPoolingFormDefinition(IcConnectorInstance connectorInstance) {
		IcConnectorConfiguration config = icConfigurationFacade.getConnectorConfiguration(connectorInstance);

		String poolingDefinitionCode = getPoolingFormDefinitionCode(connectorInstance);
		if (config == null) {
			throw new IllegalStateException(MessageFormat.format("Connector with key [{0}] was not found!",
					poolingDefinitionCode));
		}
		//
		List<IdmFormAttributeDto> formAttributes = new ArrayList<>();
		IcObjectPoolConfiguration poolConfiguration = config.getConnectorPoolConfiguration();

		IdmFormAttributeDto attributePoolingSupported = new IdmFormAttributeDto(POOLING_SUPPORTED_PROPERTY, POOLING_SUPPORTED_NAME, PersistentType.BOOLEAN);
		attributePoolingSupported.setDefaultValue(String.valueOf(false));
		attributePoolingSupported.setSeq((short)1);
		formAttributes.add(attributePoolingSupported);

		// Max idle objects
		IdmFormAttributeDto attributeMaxIdle = new IdmFormAttributeDto(MAX_IDLE_PROPERTY, MAX_IDLE_NAME, PersistentType.INT);
		if(poolConfiguration != null) {
			attributeMaxIdle.setDefaultValue(String.valueOf(poolConfiguration.getMaxIdle()));
		}
		attributeMaxIdle.setSeq((short)2);
		formAttributes.add(attributeMaxIdle);

		// Max idle objects
		IdmFormAttributeDto attributeMinIdle = new IdmFormAttributeDto(MIN_IDLE_PROPERTY, MIN_IDLE_NAME, PersistentType.INT);
		if(poolConfiguration != null) {
			attributeMinIdle.setDefaultValue(String.valueOf(poolConfiguration.getMinIdle()));
		}
		attributeMinIdle.setSeq((short)3);
		formAttributes.add(attributeMinIdle);

		// Max objects (idle + active).
		IdmFormAttributeDto attributeMaxObjects = new IdmFormAttributeDto(MAX_OBJECTS_PROPERTY, MAX_OBJECTS_NAME, PersistentType.INT);
		if(poolConfiguration != null) {
			attributeMaxObjects.setDefaultValue(String.valueOf(poolConfiguration.getMaxObjects()));
		}
		attributeMaxObjects.setSeq((short)4);
		formAttributes.add(attributeMaxObjects);

		// Max time to wait if the pool is waiting for a free object. Zero means do not wait.
		IdmFormAttributeDto attributeMaxWait = new IdmFormAttributeDto(MAX_WAIT_PROPERTY, MAX_WAIT_NAME, PersistentType.LONG);
		if(poolConfiguration != null) {
			attributeMaxWait.setDefaultValue(String.valueOf(poolConfiguration.getMaxWait()));
		}
		attributeMaxWait.setSeq((short)5);
		formAttributes.add(attributeMaxWait);

		// Minimum time to wait before evicting idle objects. Zero means do not wait.
		IdmFormAttributeDto attributeMinEvicTime = new IdmFormAttributeDto(MIN_TIME_TO_EVIC_PROPERTY, MIN_TIME_TO_EVIC_NAME, PersistentType.LONG);
		if(poolConfiguration != null) {
			attributeMinEvicTime.setDefaultValue(String.valueOf(poolConfiguration.getMinEvictableIdleTimeMillis()));
		}
		attributeMinEvicTime.setSeq((short)6);
		formAttributes.add(attributeMinEvicTime);

		return getFormService().createDefinition(SysSystem.class, poolingDefinitionCode, formAttributes);
	}

	/**
	 * Create form definition for connector operation options configuration
	 *
	 * @param connectorInstance
	 * @return
	 */
	private synchronized IdmFormDefinitionDto createOperationOptionsFormDefinition(
			IcConnectorInstance connectorInstance) {
		String definitionCode = getOperationOptionsFormDefinitionCode(connectorInstance.getConnectorKey());
		//
		List<IdmFormAttributeDto> formAttributes = new ArrayList<>();

		formAttributes.add(createAttribute(OperationOptions.OP_PAGE_SIZE, OperationOptions.OP_PAGE_SIZE,
				PersistentType.INT, (short) 1));
		formAttributes.add(createAttribute(OperationOptions.OP_ATTRIBUTES_TO_GET, OperationOptions.OP_ATTRIBUTES_TO_GET,
				PersistentType.SHORTTEXT, (short) 2, true));

		return getFormService().createDefinition(SysSystem.class, definitionCode, formAttributes);
	}

	/**
	 * Creates {@link IdmFormAttributeDto} using given attributes
	 *
	 * @param code Code of attribute
	 * @param name Name of attribute
	 * @param type Persistent type of attribute
	 * @param order Order of attribute in definition
	 * @return A newly constructed {@link IdmFormAttributeDto}. Dto is not yet persisted.
	 */
	private IdmFormAttributeDto createAttribute(String code, String name, PersistentType type, short order) {
		return createAttribute(code, name, type, order, false);
	}

	/**
	 * Creates {@link IdmFormAttributeDto} using given attributes
	 *
	 * @param code Code of attribute
	 * @param name Name of attribute
	 * @param type Persistent type of attribute
	 * @param order Order of attribute in definition
	 * @param multi Indicates, whether the attribute supports multiple values
	 * @return A newly constructed {@link IdmFormAttributeDto}. Dto is not yet persisted.
	 */
	private IdmFormAttributeDto createAttribute(String code, String name, PersistentType type, short order, boolean multi) {
		IdmFormAttributeDto attribute = new IdmFormAttributeDto(code, name, type);
		attribute.setSeq(order);
		attribute.setMultiple(multi);
		return attribute;
	}

	/**
	 * Returns name (code) of form-definition pool configuration
	 * @param connectorInstance
	 * @return
	 */
	private String getPoolingFormDefinitionCode(IcConnectorInstance connectorInstance) {
		return POOLING_DEFINITION_KEY;
	}

	/**
	 * Returns name (code) of form-definition for operation options
	 * @return
	 * @param connectorKey
	 */
	private String getOperationOptionsFormDefinitionCode(IcConnectorKey connectorKey) {
		return MessageFormat.format("{0}-{1}", OPERATION_OPTIONS_DEFINITION_KEY, connectorKey.getFullName());
	}

	/**
	 * Duplication of schema attributes. Is not in attribute schema service, because we need use IDs cache (Old vs New IDs)
	 * @param id
	 * @param system
	 * @param schemaAttributesIds
	 * @return
	 */
	private SysSchemaObjectClassDto duplicateSchema(UUID id, SysSystemDto system, Map<UUID, UUID> schemaAttributesIds) {
		Assert.notNull(id, "Id of duplication schema, must be filled!");
		Assert.notNull(system, "Parent system must be filled!");
		SysSchemaObjectClassDto clonedSchema = objectClassService.clone(id);
		clonedSchema.setSystem(system.getId());
		SysSchemaObjectClassDto schema = objectClassService.save(clonedSchema);

		SysSchemaAttributeFilter schemaAttributesFilter = new SysSchemaAttributeFilter();
		schemaAttributesFilter.setObjectClassId(id);
		attributeService.find(schemaAttributesFilter, null).forEach(schemaAttribute -> {
			UUID originalSchemaAttributId = schemaAttribute.getId();
			SysSchemaAttributeDto clonedAttribut = attributeService.clone(originalSchemaAttributId);
			clonedAttribut.setObjectClass(schema.getId());
			clonedAttribut = attributeService.save(clonedAttribut);
			// Put original and new id to cache
			schemaAttributesIds.put(originalSchemaAttributId, clonedAttribut.getId());
		});

		return schema;
	}


	/**
	 * Duplication of sync configuration. Is not in sync service, because we need use IDs cache (Old vs New IDs)
	 * @param syncConfigId
	 * @param duplicatedMapping
	 * @param mappedAttributesCache
	 */
	private void duplicateSyncConf(UUID syncConfigId, SysSystemMappingDto duplicatedMapping,
			Map<UUID, UUID> mappedAttributesCache) {
		AbstractSysSyncConfigDto clonedSyncConfig = synchronizationConfigService.clone(syncConfigId);
		clonedSyncConfig.setSystemMapping(duplicatedMapping.getId());
		//
		if (clonedSyncConfig.getFilterAttribute() != null) {
			clonedSyncConfig.setFilterAttribute(this.getNewAttributeByOld(
					systemAttributeMappingService.get(clonedSyncConfig.getFilterAttribute()), mappedAttributesCache).getId());
		}
		//
		if (clonedSyncConfig.getCorrelationAttribute() != null) {
			clonedSyncConfig.setCorrelationAttribute(
					this.getNewAttributeByOld(systemAttributeMappingService.get(clonedSyncConfig.getCorrelationAttribute()),
							mappedAttributesCache).getId());
		}
		//
		if (clonedSyncConfig.getTokenAttribute() != null) {
			clonedSyncConfig.setTokenAttribute(
					this.getNewAttributeByOld(systemAttributeMappingService.get(clonedSyncConfig.getTokenAttribute()),
							mappedAttributesCache).getId());
		}
		//
		// Disabled cloned sync
		clonedSyncConfig.setEnabled(false);
		synchronizationConfigService.save(clonedSyncConfig);
	}

	/**
	 * Find new mapped attribute by old mapped attribute (uses cache ids)
	 * @param oldAttribute
	 * @param mappedAttributesCache
	 * @return
	 */
	private SysSystemAttributeMappingDto getNewAttributeByOld(SysSystemAttributeMappingDto oldAttribute, Map<UUID, UUID> mappedAttributesCache) {
		if(oldAttribute == null){
			return null;
		}
		UUID newAttributeId = mappedAttributesCache.get(oldAttribute.getId());
		return systemAttributeMappingService.get(newAttributeId);
	}

	/**
	 * Find sync configs for given system ID and do detach.
	 * @param id
	 * @return
	 */
	private List<AbstractSysSyncConfigDto> findSyncConfigs(UUID id) {
		SysSyncConfigFilter syncConfigFilter = new SysSyncConfigFilter();
		syncConfigFilter.setSystemId(id);
		return synchronizationConfigService.find(syncConfigFilter, null).getContent();
	}

	/**
	 * Create new system name for duplicate
	 *
	 * @param name
	 * @param i
	 *
	 * @return
	 */
	private String duplicateName(String name, int i) {
		SysSystemFilter filter = new SysSystemFilter();
		if (i > 0) {
			filter.setText(MessageFormat.format("{0}{1}", name, i));
		} else {
			filter.setText(name);
		}
		if (!this.find(filter, null).hasContent()) {
			return filter.getText();
		}
		return duplicateName(name, i + 1);

	}

	@Deprecated
	@Transactional
	public SysSystemDto createTestSystem() {
		// create owner
		SysSystemDto system = new SysSystemDto();
		system.setName("sysOne_" + System.currentTimeMillis());
		system.setConnectorKey(new SysConnectorKeyDto(getTestConnectorKey()));
		system = save(system);

		IdmFormDefinitionDto savedFormDefinition = getConnectorFormDefinition(system.getConnectorInstance());

		List<IdmFormValueDto> values = new ArrayList<>();
		IdmFormValueDto host = new IdmFormValueDto(savedFormDefinition.getMappedAttributeByCode("host"));
		host.setValue("localhost");
		values.add(host);
		IdmFormValueDto port = new IdmFormValueDto(savedFormDefinition.getMappedAttributeByCode("port"));
		port.setValue("5432");
		values.add(port);
		IdmFormValueDto user = new IdmFormValueDto(savedFormDefinition.getMappedAttributeByCode("user"));
		user.setValue("idmadmin");
		values.add(user);
		IdmFormValueDto password = new IdmFormValueDto(savedFormDefinition.getMappedAttributeByCode("password"));
		password.setValue("idmadmin");
		values.add(password);
		IdmFormValueDto database = new IdmFormValueDto(savedFormDefinition.getMappedAttributeByCode("database"));
		database.setValue("bcv_idm_storage");
		values.add(database);
		IdmFormValueDto table = new IdmFormValueDto(savedFormDefinition.getMappedAttributeByCode("table"));
		table.setValue("system_users");
		values.add(table);
		IdmFormValueDto keyColumn = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("keyColumn"));
		keyColumn.setValue("name");
		values.add(keyColumn);
		IdmFormValueDto passwordColumn = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("passwordColumn"));
		passwordColumn.setValue("password");
		values.add(passwordColumn);
		IdmFormValueDto allNative = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("allNative"));
		allNative.setValue(Boolean.TRUE);
		values.add(allNative);
		IdmFormValueDto jdbcDriver = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("jdbcDriver"));
		jdbcDriver.setValue("org.postgresql.Driver");
		values.add(jdbcDriver);
		IdmFormValueDto jdbcUrlTemplate = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("jdbcUrlTemplate"));
		jdbcUrlTemplate.setValue("jdbc:postgresql://%h:%p/%d");
		values.add(jdbcUrlTemplate);
		IdmFormValueDto rethrowAllSQLExceptions = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("rethrowAllSQLExceptions"));
		rethrowAllSQLExceptions.setValue(Boolean.TRUE);
		values.add(rethrowAllSQLExceptions);
		IdmFormValueDto statusColumn = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("statusColumn"));
		statusColumn.setValue("status");
		values.add(statusColumn);
		IdmFormValueDto disabledStatusValue = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("disabledStatusValue"));
		disabledStatusValue.setValue("disabled");
		values.add(disabledStatusValue);
		IdmFormValueDto enabledStatusValue = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("enabledStatusValue"));
		enabledStatusValue.setValue("enabled");
		values.add(enabledStatusValue);

		// TODO: eav to DTO
		getFormService().saveValues(getEntity(system.getId()), savedFormDefinition, values);

		return system;
	}

	/**
	 * Basic table connector
	 *
	 * @return
	 */
	@Deprecated
	public IcConnectorKey getTestConnectorKey() {
		IcConnectorKeyImpl key = new IcConnectorKeyImpl();
		key.setFramework("connId");
		key.setConnectorName("net.tirasa.connid.bundles.db.table.DatabaseTableConnector");
		key.setBundleName("net.tirasa.connid.bundles.db.table");
		key.setBundleVersion("2.2.4");
		return key;
	}

	@Override
	public IcConnectorInstance getConnectorInstance(SysSystemDto system) {
		IcConnectorServer connectorServer = system.getConnectorServer();
		if (connectorServer != null && system.isRemote()) {
			GuardedString password = connectorServer.getPassword();
			if (password == null || GuardedString.SECRED_PROXY_STRING.equals(password.asString())) { // password is not loaded properly yet
				connectorServer.setPassword(
						confidentialStorage.getGuardedString(
							system.getId(),
							SysSystem.class, 
							SysSystemService.REMOTE_SERVER_PASSWORD
						)
				);
			}
		}
		//
		return new IcConnectorInstanceImpl(
				connectorServer,
				system.getConnectorKey(),
				system.isRemote());
	}
	
}
