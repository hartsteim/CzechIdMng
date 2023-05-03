package eu.bcvsolutions.idm.acc.service.impl;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.plugin.core.OrderAwarePluginRegistry;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.domain.AccountType;
import eu.bcvsolutions.idm.acc.domain.MappingContext;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccIdentityAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemAttributeMappingFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemMappingFilter;
import eu.bcvsolutions.idm.acc.entity.AccAccount_;
import eu.bcvsolutions.idm.acc.entity.SysSchemaObjectClass_;
import eu.bcvsolutions.idm.acc.entity.SysSystemAttributeMapping_;
import eu.bcvsolutions.idm.acc.entity.SysSystemMapping;
import eu.bcvsolutions.idm.acc.entity.SysSystemMapping_;
import eu.bcvsolutions.idm.acc.entity.SysSystem_;
import eu.bcvsolutions.idm.acc.exception.ProvisioningException;
import eu.bcvsolutions.idm.acc.repository.SysSystemMappingRepository;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityTypeManager;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.system.entity.SystemEntityTypeRegistrable;
import eu.bcvsolutions.idm.core.api.domain.IdmScriptCategory;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.AbstractRoleAssignmentDto;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.ExportDescriptorDto;
import eu.bcvsolutions.idm.core.api.dto.IdmExportImportDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRequestIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.AbstractEventableDtoService;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.ExportManager;
import eu.bcvsolutions.idm.core.api.service.GroovyScriptService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleAssignmentManager;
import eu.bcvsolutions.idm.core.api.service.IdmTreeTypeService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.api.utils.EntityUtils;
import eu.bcvsolutions.idm.core.model.entity.IdmTreeType_;
import eu.bcvsolutions.idm.core.script.evaluator.AbstractScriptEvaluator;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;

/**
 * Default system entity handling.
 *
 * @author svandav
 * @author Ondrej Husnik
 * @author Radek Tomiška
 * @author Roman Kucera
 */
@Service
public class DefaultSysSystemMappingService
		extends AbstractEventableDtoService<SysSystemMappingDto, SysSystemMapping, SysSystemMappingFilter>
		implements SysSystemMappingService {

	private static final String SYSTEM_MISSING_IDENTIFIER = "systemMissingIdentifier";
	private static final String SYSTEM_MISSING_OWNER = "systemMissingOwner";
	public static final String IDENTITY_STATE_USED_WITH_DISABLED = "identityStateUsedWithDisabled";
	private static final String IDENTITY_STATE_IDM_NAME = "state";
	private static final String IDENTITY_DISABLED_IDM_NAME = "disabled";
	//
	private final GroovyScriptService groovyScriptService;
	private final PluginRegistry<AbstractScriptEvaluator, IdmScriptCategory> pluginExecutors;
	private final ApplicationContext applicationContext;
	private SysSystemAttributeMappingService attributeMappingService;
	@Autowired
	@Lazy
	private SysSchemaAttributeService attributeService;
	@Lazy
	@Autowired
	private IdmRoleAssignmentManager roleAssignmentManager;

	@Lazy
	@Autowired
	private IdmTreeTypeService treeTypeService;
	@Lazy
	@Autowired
	private AccIdentityAccountService identityAccountService;
	@Lazy
	@Autowired
	private SysSystemEntityService systemEntityService;
	@Autowired
	private SysSystemEntityTypeManager systemEntityManager;

	@Lazy
	@Autowired
	private IdmIdentityContractService identityContractService;

	@Autowired
	public DefaultSysSystemMappingService(
			SysSystemMappingRepository repository, 
			EntityEventManager entityEventManager,
			GroovyScriptService groovyScriptService, 
			List<AbstractScriptEvaluator> evaluators,
			ApplicationContext applicationContext) {
		super(repository, entityEventManager);
		//
		Assert.notNull(groovyScriptService, "Groovy script service is required.");
		Assert.notNull(evaluators, "Script evaluators is required.");
		Assert.notNull(applicationContext, "Context is required.");
		//
		this.applicationContext = applicationContext;
		this.groovyScriptService = groovyScriptService;
		this.pluginExecutors = OrderAwarePluginRegistry.create(evaluators);
	}

	@Override
	@Transactional
	public SysSystemMappingDto saveInternal(SysSystemMappingDto dto) {
		String entityType = dto.getEntityType();
		SystemEntityTypeRegistrable systemEntityType = systemEntityManager.getSystemEntityByCode(entityType);
		if (SystemOperationType.PROVISIONING == dto.getOperationType() && !systemEntityType.isSupportsProvisioning()) {
			throw new ResultCodeException(AccResultCode.PROVISIONING_NOT_SUPPORTS_ENTITY_TYPE, ImmutableMap.of("entityType", entityType));
		}

		if (dto.getConnectedSystemMappingId() != null) {
			SysSystemMappingDto connectedSystemMappingDto = this.get(dto.getConnectedSystemMappingId());
			if (connectedSystemMappingDto != null) {
				if (dto.getOperationType().equals(connectedSystemMappingDto.getOperationType())) {
					throw new ResultCodeException(AccResultCode.SYSTEM_MAPPING_CONNECTED_MAPPING_SAME_TYPE, ImmutableMap.of("mapping", connectedSystemMappingDto.getName()));
				}
				SysSystemMappingFilter systemMappingFilter = new SysSystemMappingFilter();
				systemMappingFilter.setConnectedSystemMappingId(connectedSystemMappingDto.getId());
				List<UUID> connectedMappingsIds = this.findIds(systemMappingFilter, null).getContent();
				connectedMappingsIds.forEach(uuid -> {
					if (!uuid.equals(dto.getId())) {
						throw new ResultCodeException(AccResultCode.SYSTEM_MAPPING_CONNECTED_MAPPING_ALREADY_MAPPED, ImmutableMap.of("mapping", connectedSystemMappingDto.getName()));
					}
				});
			}
		}

		// Validate all sub attributes
		getAttributeMappingService()
				.findBySystemMapping(dto).forEach(attribute -> {
					getAttributeMappingService().validate(attribute, dto);
				});
		return super.saveInternal(dto);
	}

	@Override
	public List<SysSystemMappingDto> findBySystem(SysSystemDto system, SystemOperationType operation,
			String entityType) {
		Assert.notNull(system, "System is required.");
		//
		return findBySystemId(system.getId(), operation, entityType);
	}

	@Override
	public List<SysSystemMappingDto> findBySystemId(
			UUID systemId, 
			SystemOperationType operation,
			String entityType) {
		Assert.notNull(systemId, "System identifier is required.");
		//
		SysSystemMappingFilter filter = new SysSystemMappingFilter();
		filter.setSystemId(systemId);
		filter.setOperationType(operation);
		filter.setEntityType(entityType);
		//
		return find(filter, null).getContent();
	}

	@Override
	public List<SysSystemMappingDto> findByObjectClass(
			SysSchemaObjectClassDto objectClass,
			SystemOperationType operation, 
			String entityType) {
		Assert.notNull(objectClass, "Object class is required.");
		//
		SysSystemMappingFilter filter = new SysSystemMappingFilter();
		filter.setObjectClassId(objectClass.getId());
		filter.setOperationType(operation);
		filter.setEntityType(entityType);
		//
		return find(filter, null).getContent();
	}

	@Override
	public boolean isEnabledProtection(AccAccountDto account) {
		Assert.notNull(account, "Account cannot be null!");
		Assert.notNull(account.getEntityType(), "EntityType cannot be null!");
		SysSystemDto system = DtoUtils.getEmbedded(account, AccAccount_.system);
		List<SysSystemMappingDto> mappings = this.findBySystem(system, SystemOperationType.PROVISIONING,
				account.getEntityType());
		if (mappings.isEmpty()) {
			return false;
		}
		// We assume only one mapping for provisioning and entity type.
		return this.isEnabledProtection(mappings.get(0));
	}

	@Override
	public Integer getProtectionInterval(AccAccountDto account) {
		Assert.notNull(account, "Account cannot be null!");
		Assert.notNull(account.getEntityType(), "EntityType cannot be null!");

		SysSystemDto system = DtoUtils.getEmbedded(account, AccAccount_.system);
		List<SysSystemMappingDto> mappings = this.findBySystem(system, SystemOperationType.PROVISIONING,
				account.getEntityType());
		if (mappings.isEmpty()) {
			return -1;
		}
		// We assume only one mapping for provisioning and entity type.
		return this.getProtectionInterval(mappings.get(0));
	}

	@Override
	public SysSystemMappingDto clone(UUID id) {
		SysSystemMappingDto original = this.get(id);
		Assert.notNull(original, "System mapping must be found!");

		original.setId(null);
		EntityUtils.clearAuditFields(original);
		original.setConnectedSystemMappingId(null);
		return original;
	}

	@Override
	protected SysSystemMappingDto internalExport(UUID id) {
		// For searching tree-type by code, have to be tree-type DTO embedded.
		SysSystemMappingDto dto = this.get(id);
		if (dto != null && dto.getTreeType() != null) {
			BaseDto roleDto = dto.getEmbedded().get(SysSystemMapping_.treeType.getName());
			dto.getEmbedded().clear();
			dto.getEmbedded().put(SysSystemMapping_.treeType.getName(), roleDto);
		}
		return dto;
	}

	@Override
	public void export(UUID id, IdmExportImportDto batch) {
		super.export(id, batch);
		// Tree-type will be searching by code (advanced paring by treeType field)
		ExportDescriptorDto descriptorDto = getExportManager().getDescriptor(batch, this.getDtoClass());
		descriptorDto.getAdvancedParingFields().add(SysSystemMapping_.treeType.getName());

		// Export mapped attributes
		SysSystemAttributeMappingFilter filter = new SysSystemAttributeMappingFilter();
		filter.setSystemMappingId(id);
		List<SysSystemAttributeMappingDto> attributes = this.getAttributeMappingService().find(filter, null).getContent();
		if (attributes.isEmpty()) {
			this.getAttributeMappingService().export(ExportManager.BLANK_UUID, batch);
		}
		attributes.forEach(systemAttributeMapping -> {
					this.getAttributeMappingService().export(systemAttributeMapping.getId(), batch);
				});
		// Set parent field -> set authoritative mode.
		getExportManager().setAuthoritativeMode(SysSystemAttributeMapping_.systemMapping.getName(), "systemId",
				SysSystemAttributeMappingDto.class, batch);
	}

	/**
	 * Validate system mapping
	 *
	 * @param id(UUID
	 *            system mapping)
	 */
	@Override
	public void validate(UUID id) {
		Assert.notNull(id, "Identifier is required.");
		//
		Map<String, Object> errors = new HashMap<>();
		SysSystemMappingDto systemMapping = this.get(id);
		List<SysSystemAttributeMappingDto> attributesList = getAttributeMappingService()
				.findBySystemMapping(systemMapping);
		//
		errors = validateIdentifier(errors, systemMapping, attributesList);
		errors = validateSynchronizationContracts(errors, systemMapping, attributesList);
		errors = validateIdentityStateAndDisabled(errors, systemMapping, attributesList);

		if (!errors.isEmpty()) {
			throw new ResultCodeException(AccResultCode.SYSTEM_MAPPING_VALIDATION, errors);
		}
	}

	/**
	 * Validation of sync: Missing Identifier
	 *
	 * @param errors
	 * @param systemMapping
	 * @param attributesList
	 * @return
	 */
	private Map<String, Object> validateIdentifier(Map<String, Object> errors, SysSystemMappingDto systemMapping,
			List<SysSystemAttributeMappingDto> attributesList) {
		boolean isError = true;
		for (SysSystemAttributeMappingDto attribute : attributesList) {
			if (attribute.isUid()) {
				isError = false;
				break;
			}
		}
		if (isError) {
			errors.put(SYSTEM_MISSING_IDENTIFIER, "Identifier not found");
		}
		return errors;
	}

	/**
	 * Validation of sync
	 *
	 * @param errors
	 * @param systemMapping
	 * @param attributesList
	 * @return
	 */
	private Map<String, Object> validateSynchronizationContracts(Map<String, Object> errors,
			SysSystemMappingDto systemMapping, List<SysSystemAttributeMappingDto> attributesList) {
		final String idmProperty = "identity";
		boolean isError = true;
		if (systemMapping.getOperationType() == SystemOperationType.SYNCHRONIZATION
				&& systemMapping.getEntityType().equals(ContractSynchronizationExecutor.SYSTEM_ENTITY_TYPE)) {
			for (SysSystemAttributeMappingDto attribute : attributesList) {
				if (attribute.isEntityAttribute() && attribute.getIdmPropertyName().equals(idmProperty)) {
					isError = false;
					break;
				}
			}
			if (isError) {
				errors.put(SYSTEM_MISSING_OWNER, "Synchronization does not have IdM key: identity");
			}
		}
		return errors;
	}
	
	/**
	 * Detects and informs when there are used both IdentityState and Disabled attributes.
	 * IdentityState is preferred over Disabled.
	 * 
	 * @param errors
	 * @param systemMapping
	 * @param attributesList
	 * @return
	 */
	private Map<String, Object> validateIdentityStateAndDisabled(Map<String, Object> errors,
			SysSystemMappingDto systemMapping, List<SysSystemAttributeMappingDto> attributesList) {
		
		if (IdentitySynchronizationExecutor.SYSTEM_ENTITY_TYPE == systemMapping.getEntityType()) {
			Set<String> attrs = attributesList
					.stream()
					.map(SysSystemAttributeMappingDto::getIdmPropertyName)
					.filter(name -> {
				return IDENTITY_STATE_IDM_NAME.equals(name) ||
						IDENTITY_DISABLED_IDM_NAME.equals(name);
			}).collect(Collectors.toSet());
		
			if (attrs.size() > 1) {
				errors.put(IDENTITY_STATE_USED_WITH_DISABLED, "Use either state or disabled identity attribute not both. State is preferred.");
			}
		}
		return errors;
	}

	@Override
	public boolean canBeAccountCreated(String uid, AbstractDto dto, String script, SysSystemDto system) {

		if (StringUtils.isEmpty(script)) {
			return true;
		} else {
			Map<String, Object> variables = new HashMap<>();
			variables.put(SysSystemAttributeMappingService.ACCOUNT_UID, uid);
			variables.put(SysSystemAttributeMappingService.SYSTEM_KEY, system);
			variables.put(SysSystemAttributeMappingService.ENTITY_KEY, dto);
			// Add null variables (because is using transformation to system).
			variables.put(SysSystemAttributeMappingService.ATTRIBUTE_VALUE_KEY, null);
			variables.put(SysSystemAttributeMappingService.CONTEXT_KEY, null);
			// add default script evaluator, for call another scripts
			variables.put(AbstractScriptEvaluator.SCRIPT_EVALUATOR,
					pluginExecutors.getPluginFor(IdmScriptCategory.TRANSFORM_TO));

			// Add access for script evaluator
			List<Class<?>> extraClass = new ArrayList<>();
			extraClass.add(AbstractScriptEvaluator.Builder.class);
			//
			Object result = groovyScriptService.evaluate(script, variables, extraClass);
			if (result instanceof Boolean) {
				return (boolean) result;
			} else {
				throw new ProvisioningException(
						AccResultCode.PROVISIONING_SCRIPT_CAN_BE_ACC_CREATED_MUST_RETURN_BOOLEAN,
						ImmutableMap.of("system", system.getCode()));
			}
		}
	}

	@Override
	public SysSystemMappingDto findProvisioningMapping(UUID systemId, String entityType, UUID mappingId) {
		Assert.notNull(systemId, "System identifier is required.");
		Assert.notNull(entityType, "Entity type is required.");

		if (mappingId != null) {
			return this.get(mappingId);
		}

		SysSystemMappingFilter mappingFilter = new SysSystemMappingFilter();
		mappingFilter.setSystemId(systemId);
		mappingFilter.setEntityType(entityType);
		mappingFilter.setOperationType(SystemOperationType.PROVISIONING);
		List<SysSystemMappingDto> mappings = this.find(mappingFilter, null).getContent();
		if (mappings.isEmpty()) {
			return null;
		}
		// Only one mapping for provisioning and entity type and system can exists
		return mappings.get(0);
	}

	@Override
	public SysSystemMappingDto findProvisioningMapping(UUID systemId, String entityType) {
		return this.findProvisioningMapping(systemId, entityType, null);
	}

	private Integer getProtectionInterval(SysSystemMappingDto systemMapping) {

		Assert.notNull(systemMapping, "Mapping cannot be null!");
		return systemMapping.getProtectionInterval();
	}

	private boolean isEnabledProtection(SysSystemMappingDto systemMapping) {
		Assert.notNull(systemMapping, "Mapping cannot be null!");
		return systemMapping.isProtectionEnabled();
	}

	private SysSystemAttributeMappingService getAttributeMappingService() {
		if (attributeMappingService == null)
			attributeMappingService = applicationContext.getBean(SysSystemAttributeMappingService.class);
		return attributeMappingService;
	}

	@Override
	public MappingContext getMappingContext(SysSystemMappingDto mapping, SysSystemEntityDto systemEntity, AbstractDto dto, SysSystemDto system) {
		Assert.notNull(mapping, "Mapping cannot be null!");
		Assert.notNull(systemEntity, "System entity cannot be null!");
		Assert.notNull(system, "System cannot be null!");

		// Create new context.
		MappingContext mappingContext = new MappingContext();

		if (dto == null) {
			return mappingContext;
		}

		if ((mapping.isAddContextIdentityRoles() || mapping.isAddContextIdentityRolesForSystem()) &&  dto instanceof IdmIdentityDto) {
			IdmRequestIdentityRoleFilter identityRoleFilter = new IdmRequestIdentityRoleFilter();
			identityRoleFilter.setIdentityId(dto.getId());
			List<AbstractRoleAssignmentDto> identityRoles = roleAssignmentManager
					.find(identityRoleFilter,
							PageRequest.of(0, Integer.MAX_VALUE, Sort.by(AbstractEntity_.created.getName())), (a,b) -> {});
			if (mapping.isAddContextIdentityRoles()) {
				// Set all identity-roles to the context.
				mappingContext.setIdentityRoles(identityRoles);
			}
			if (mapping.isAddContextIdentityRolesForSystem()) {
				Assert.notNull(system.getId(), "System identifier is required.");
				List<AbstractRoleAssignmentDto> identityRolesForSystem = Lists.newArrayList();
				AccIdentityAccountFilter identityAccountFilter = new AccIdentityAccountFilter();
				identityAccountFilter.setIdentityId(dto.getId());
				identityAccountFilter.setSystemId(system.getId());
				List<AccIdentityAccountDto> identityAccounts = identityAccountService.find(identityAccountFilter, null)
						.getContent();

				// Filtering only identity-roles for this system.
				identityAccounts.forEach(identityAccount -> {
					identityRolesForSystem.addAll(identityRoles.stream()
							.filter(identityRole -> identityRole.getId().equals(identityAccount.getIdentityRole()))
							.collect(Collectors.toList())
					);
				});
				// Set identity-roles for this system to the context.
				mappingContext.setIdentityRolesForSystem(identityRolesForSystem);
			}
		}

		if (mapping.isAddContextContracts()  &&  dto instanceof IdmIdentityDto) {
			// Set all identity contracts to the context.
			mappingContext.setContracts(identityContractService.findAllByIdentity(dto.getId()));
		}
		if (mapping.isAddContextConnectorObject()) {
			// Set connector object to the context.
			mappingContext.setConnectorObject(systemEntityService.getConnectorObject(systemEntity));
		}

		String script = mapping.getMappingContextScript();

		if (StringUtils.isEmpty(script)) {
			return mappingContext;
		} else {
			Map<String, Object> variables = new HashMap<>();
			variables.put(SysSystemAttributeMappingService.ACCOUNT_UID, systemEntity.getUid());
			variables.put(SysSystemAttributeMappingService.SYSTEM_KEY, system);
			variables.put(SysSystemAttributeMappingService.ENTITY_KEY, dto);
			variables.put(SysSystemAttributeMappingService.CONTEXT_KEY, mappingContext);
			// Add default script evaluator, for call another scripts
			variables.put(AbstractScriptEvaluator.SCRIPT_EVALUATOR,
					pluginExecutors.getPluginFor(IdmScriptCategory.MAPPING_CONTEXT));

			// Add access for script evaluator
			List<Class<?>> extraClass = new ArrayList<>();
			extraClass.add(AbstractScriptEvaluator.Builder.class);
			extraClass.add(IcConnectorObject.class);
			//
			Object result = groovyScriptService.evaluate(script, variables, extraClass);
			if (result instanceof MappingContext) {
				return (MappingContext) result;
			} else {
				throw new ProvisioningException(
						AccResultCode.MAPPING_CONTEXT_SCRIPT_RETURNS_WRONG_TYPE,
						ImmutableMap.of("system", system.getCode()));
			}
		}
	}

	@Override
	@Transactional
	public SysSystemMappingDto duplicateMapping(UUID id, SysSchemaObjectClassDto schema,
			Map<UUID, UUID> schemaAttributesIds, Map<UUID, UUID> mappedAttributesIds, boolean usedInSameSystem) {
		Assert.notNull(id, "Id of duplication mapping, must be filled!");
		Assert.notNull(schema, "Parent schema must be filled!");

		SysSystemMappingDto clonedMapping = this.clone(id);
		clonedMapping.setObjectClass(schema.getId());

		if (usedInSameSystem) {
			String newName = duplicateName(clonedMapping.getName());
			clonedMapping.setName(newName);
		}
		SysSystemMappingDto mapping = this.save(clonedMapping);

		// Clone mapped attributes
		SysSystemAttributeMappingFilter attributesFilter = new SysSystemAttributeMappingFilter();
		attributesFilter.setSystemMappingId(id);
		attributeMappingService.find(attributesFilter, null).forEach(attribute -> {
			UUID originalAttributeId = attribute.getId();
			SysSystemAttributeMappingDto clonedAttribute = attributeMappingService.clone(originalAttributeId);
			// Find cloned schema attribute in cache (by original Id)
			SysSchemaAttributeDto clonedSchemaAttribute = attributeService
					.get(schemaAttributesIds.get(clonedAttribute.getSchemaAttribute()));

			clonedAttribute.setSystemMapping(mapping.getId());
			clonedAttribute.setSchemaAttribute(clonedSchemaAttribute.getId());
			clonedAttribute = attributeMappingService.save(clonedAttribute);
			// Put original and new id to cache
			mappedAttributesIds.put(originalAttributeId, clonedAttribute.getId());
		});

		return mapping;
	}

	/**
	 *
	 * @param name - name copy of which is to be created
	 */
	private String duplicateName(String name) {
		String newNameBase = MessageFormat.format("{0}{1}", "Copy-of-", name);
		String newName = newNameBase;
		SysSystemMappingFilter filter = new SysSystemMappingFilter();
		int i = 1;
		do {
			filter.setText(newName);
			if (!this.find(filter, null).hasContent()) {
				return newName;
			} else {
				newName = MessageFormat.format("{0}{1}", newNameBase, i);
				i++;
			}
		} while (true);
	}
	
	@Override
	protected List<Predicate> toPredicates(Root<SysSystemMapping> root, CriteriaQuery<?> query, CriteriaBuilder builder, SysSystemMappingFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
		//
		String text = filter.getText();
		if (StringUtils.isNotEmpty(text)) {
			text = text.toLowerCase();
			List<Predicate> textPredicates = new ArrayList<>(2);
			//
			textPredicates.add(builder.like(builder.lower(root.get(SysSystemMapping_.name)), "%" + text + "%"));
			textPredicates.add(builder.like(builder.lower(
					root.get(SysSystemMapping_.objectClass).get(SysSchemaObjectClass_.system).get(SysSystem_.name)), "%" + text + "%")
			);
			//
			predicates.add(builder.or(textPredicates.toArray(new Predicate[textPredicates.size()])));
		}
		//
		UUID systemId = filter.getSystemId();
		if (systemId != null) {
			predicates.add(
					builder.equal(
							root.get(SysSystemMapping_.objectClass).get(SysSchemaObjectClass_.system).get(SysSystem_.id),
							systemId
					)
			);
		}
		UUID objectClassId = filter.getObjectClassId();
		if (objectClassId != null) {
			predicates.add(
					builder.equal(
							root.get(SysSystemMapping_.objectClass).get(SysSchemaObjectClass_.id),
							objectClassId
					)
			);
		}
		SystemOperationType operationType = filter.getOperationType();
		if (operationType != null) {
			predicates.add(
					builder.equal(root.get(SysSystemMapping_.operationType), operationType)
			);
		}
		UUID treeTypeId = filter.getTreeTypeId();
		if (treeTypeId != null) {
			predicates.add(
					builder.equal(
							root.get(SysSystemMapping_.treeType).get(IdmTreeType_.id),
							treeTypeId
					)
			);
		}
		//
		String entityType = filter.getEntityType();
		if (entityType != null) {
			predicates.add(builder.equal(root.get(SysSystemMapping_.entityType), entityType));
		}
		UUID connectedSystemMappingId = filter.getConnectedSystemMappingId();
		if (connectedSystemMappingId != null) {
			predicates.add(
					builder.equal(
							root.get(SysSystemMapping_.connectedSystemMappingId).get(SysSystemMapping_.id),
							connectedSystemMappingId
					)
			);
		}
		AccountType accountType = filter.getAccountType();
		if (accountType != null) {
			predicates.add(builder.equal(root.get(SysSystemMapping_.accountType), accountType));
		}
		//
		return predicates;
	}
}
