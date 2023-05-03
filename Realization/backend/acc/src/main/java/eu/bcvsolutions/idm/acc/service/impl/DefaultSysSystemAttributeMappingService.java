package eu.bcvsolutions.idm.acc.service.impl;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.ApplicationContext;
import org.springframework.plugin.core.OrderAwarePluginRegistry;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.domain.AttributeMapping;
import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.IdmAttachmentWithDataDto;
import eu.bcvsolutions.idm.acc.domain.MappingContext;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysAttributeControlledValueDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysAttributeControlledValueFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemAttributeMappingFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemGroupSystemFilter;
import eu.bcvsolutions.idm.acc.entity.AccAccount_;
import eu.bcvsolutions.idm.acc.entity.SysRoleSystemAttribute_;
import eu.bcvsolutions.idm.acc.entity.SysRoleSystem_;
import eu.bcvsolutions.idm.acc.entity.SysSchemaAttribute;
import eu.bcvsolutions.idm.acc.entity.SysSchemaAttribute_;
import eu.bcvsolutions.idm.acc.entity.SysSchemaObjectClass;
import eu.bcvsolutions.idm.acc.entity.SysSchemaObjectClass_;
import eu.bcvsolutions.idm.acc.entity.SysSyncRoleConfig_;
import eu.bcvsolutions.idm.acc.entity.SysSystemAttributeMapping;
import eu.bcvsolutions.idm.acc.entity.SysSystemAttributeMapping_;
import eu.bcvsolutions.idm.acc.entity.SysSystemMapping;
import eu.bcvsolutions.idm.acc.entity.SysSystemMapping_;
import eu.bcvsolutions.idm.acc.exception.ProvisioningException;
import eu.bcvsolutions.idm.acc.repository.SysRoleSystemAttributeRepository;
import eu.bcvsolutions.idm.acc.repository.SysSyncConfigRepository;
import eu.bcvsolutions.idm.acc.repository.SysSystemAttributeMappingRepository;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.FormPropertyManager;
import eu.bcvsolutions.idm.acc.service.api.SysAttributeControlledValueService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncConfigService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityTypeManager;
import eu.bcvsolutions.idm.acc.service.api.SysSystemGroupSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.system.entity.SystemEntityTypeRegistrable;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.api.domain.IdmScriptCategory;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmExportImportDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.AbstractEventableDtoService;
import eu.bcvsolutions.idm.core.api.service.ConfidentialStorage;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.GroovyScriptService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.api.utils.EntityUtils;
import eu.bcvsolutions.idm.core.api.utils.ExceptionUtils;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.script.evaluator.AbstractScriptEvaluator;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.impl.IcAttributeImpl;
import eu.bcvsolutions.idm.ic.impl.IcPasswordAttributeImpl;
import eu.bcvsolutions.idm.ic.service.api.IcConnectorFacade;

/**
 * Default schema attributes mapping
 * 
 * @author svandav
 *
 */
@Service
public class DefaultSysSystemAttributeMappingService 
		extends AbstractEventableDtoService<SysSystemAttributeMappingDto, SysSystemAttributeMapping, SysSystemAttributeMappingFilter>
		implements SysSystemAttributeMappingService {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory
			.getLogger(DefaultSysSystemAttributeMappingService.class);

	private final GroovyScriptService groovyScriptService;
	private final FormService formService;
	private final ConfidentialStorage confidentialStorage;
	private final SysRoleSystemAttributeRepository roleSystemAttributeRepository;
	private final FormPropertyManager formPropertyManager;
	private final SysSyncConfigRepository syncConfigRepository;
	private final PluginRegistry<AbstractScriptEvaluator, IdmScriptCategory> pluginExecutors;
	private final SysSchemaAttributeService schemaAttributeService;
	private final SysSchemaObjectClassService schemaObjectClassService;
	private final SysSystemMappingService systemMappingService;
	@Autowired
	private SysAttributeControlledValueService attributeControlledValueService;
	@Autowired
	private SysRoleSystemAttributeService roleSystemAttributeService;
	@Autowired
	private AttachmentManager attachmentManager;
	@Autowired
	private IdmFormDefinitionService formDefinitionService;
	@Autowired
	private IdmFormAttributeService formAttributeService;
	@Autowired
	private SysSyncConfigService syncConfigService;
	@Autowired
	private SysSystemGroupSystemService systemGroupSystemService;
	@Autowired
	private SysSystemEntityTypeManager systemEntityManager;
	@Autowired
	private AccAccountService accountService;
	@Autowired
	private LookupService lookupService;
	private final ApplicationContext applicationContext;


	@Autowired
	public DefaultSysSystemAttributeMappingService(SysSystemAttributeMappingRepository repository,
			GroovyScriptService groovyScriptService, FormService formService,
			SysRoleSystemAttributeRepository roleSystemAttributeRepository, FormPropertyManager formPropertyManager,
			SysSyncConfigRepository syncConfigRepository, List<AbstractScriptEvaluator> evaluators,
			ConfidentialStorage confidentialStorage, SysSchemaAttributeService schemaAttributeService,
			SysSchemaObjectClassService schemaObjectClassService, SysSystemMappingService systemMappingService,
			EntityEventManager entityEventManager, ApplicationContext applicationContext) {
		super(repository, entityEventManager);
		//
		Assert.notNull(groovyScriptService, "Groovy script service is required.");
		Assert.notNull(formService, "Form service (eav) is required.");
		Assert.notNull(roleSystemAttributeRepository, "Repository is required.");
		Assert.notNull(formPropertyManager, "Manager is required.");
		Assert.notNull(syncConfigRepository, "Repository is required.");
		Assert.notNull(evaluators, "Script evaluators is required.");
		Assert.notNull(confidentialStorage, "Confidential storage is required.");
		Assert.notNull(schemaAttributeService, "Service is required.");
		Assert.notNull(schemaObjectClassService, "Service is required.");
		Assert.notNull(systemMappingService, "Service is required.");
		//
		this.formService = formService;
		this.groovyScriptService = groovyScriptService;
		this.roleSystemAttributeRepository = roleSystemAttributeRepository;
		this.formPropertyManager = formPropertyManager;
		this.syncConfigRepository = syncConfigRepository;
		this.confidentialStorage = confidentialStorage;
		this.schemaAttributeService = schemaAttributeService;
		this.schemaObjectClassService = schemaObjectClassService;
		this.systemMappingService = systemMappingService;
		//
		this.pluginExecutors = OrderAwarePluginRegistry.create(evaluators);
		this.applicationContext = applicationContext;
	}

	@Override
	public AuthorizableType getAuthorizableType() {
		// RT: commented till system agenda will be secured properly
		// return new AuthorizableType(AccGroupPermission.SYSTEMATTRIBUTEMAPPING, getEntityClass());
		//
		return null;
	}

	@Override
	protected List<Predicate> toPredicates(Root<SysSystemAttributeMapping> root, CriteriaQuery<?> query,
			CriteriaBuilder builder, SysSystemAttributeMappingFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);

		// fulltext
		String text = filter.getText();
		if (!StringUtils.isEmpty(text)) {
			predicates.add(builder.like(builder.lower(root.get(SysSystemAttributeMapping_.name)), "%" + text.toLowerCase() + "%"));
		}

		if (filter.getSystemMappingId() != null) {
			predicates.add(builder.equal(root.get(SysSystemAttributeMapping_.systemMapping).get(AbstractEntity_.id), filter.getSystemMappingId()));
		}
		
		if (filter.getSchemaAttributeId() != null) {
			predicates.add(builder.equal(root.get(SysSystemAttributeMapping_.schemaAttribute).get(AbstractEntity_.id), filter.getSchemaAttributeId()));
		}

		if (!StringUtils.isEmpty(filter.getSchemaAttributeName())) {
			Subquery<SysSchemaAttribute> subquery = query.subquery(SysSchemaAttribute.class);
			Root<SysSchemaAttribute> subRoot = subquery.from(SysSchemaAttribute.class);
			subquery.select(subRoot);
			subquery.where(
                    builder.and(
                    		builder.equal(root.get(SysSystemAttributeMapping_.schemaAttribute), subRoot), // correlation attr
                    		builder.equal(subRoot.get(SysSchemaAttribute_.name), filter.getSchemaAttributeName())
                    		)
            );
			predicates.add(builder.exists(subquery));
		}

		if (filter.getSystemId() != null) {
			Subquery<SysSystemMapping> subquerySystemMapping = query.subquery(SysSystemMapping.class);
			Root<SysSystemMapping> subRootSystemMapping = subquerySystemMapping.from(SysSystemMapping.class);
			subquerySystemMapping.select(subRootSystemMapping);

            Subquery<SysSchemaObjectClass> subqueryObjectClass = query.subquery(SysSchemaObjectClass.class);
			Root<SysSchemaObjectClass> subRootObjectClass = subqueryObjectClass.from(SysSchemaObjectClass.class);
			subqueryObjectClass.select(subRootObjectClass);
			subqueryObjectClass.where(
                    builder.and(
                    		builder.equal(subRootSystemMapping.get(SysSystemMapping_.objectClass), subRootObjectClass), // correlation attr
                    		builder.equal(subRootObjectClass.get(SysSchemaObjectClass_.system).get(AbstractEntity_.id), filter.getSystemId())
                    		));

			Predicate predicate = builder.exists(
							subquerySystemMapping.where(
									builder.and(
											builder.equal(root.get(SysSystemAttributeMapping_.systemMapping), subRootSystemMapping),
											builder.exists(
													subqueryObjectClass.where(
										                    builder.and(
										                    		builder.equal(subRootSystemMapping.get(SysSystemMapping_.objectClass), subRootObjectClass), // correlation attr
										                    		builder.equal(subRootObjectClass.get(SysSchemaObjectClass_.system).get(AbstractEntity_.id), filter.getSystemId())
										                    		))
													)
					)));
			
			predicates.add(predicate);
		}

		if (filter.getIsUid() != null) {
			if (BooleanUtils.isFalse(filter.getIsUid())) {
				predicates.add(builder.isFalse(root.get(SysSystemAttributeMapping_.uid)));
			} else {
				predicates.add(builder.isTrue(root.get(SysSystemAttributeMapping_.uid)));
			}
		}

		if (!StringUtils.isEmpty(filter.getIdmPropertyName())) {
			predicates.add(builder.equal(root.get(SysSystemAttributeMapping_.idmPropertyName), filter.getIdmPropertyName()));
		}

		if (filter.getSendOnPasswordChange() != null) {
			if (BooleanUtils.isFalse(filter.getSendOnPasswordChange())) {
				predicates.add(builder.isFalse(root.get(SysSystemAttributeMapping_.sendOnPasswordChange)));
			} else {
				predicates.add(builder.isTrue(root.get(SysSystemAttributeMapping_.sendOnPasswordChange)));
			}
		}
		
		if (filter.getSendOnlyOnPasswordChange() != null) {
			if (BooleanUtils.isFalse(filter.getSendOnlyOnPasswordChange())) {
				predicates.add(builder.isFalse(root.get(SysSystemAttributeMapping_.sendOnlyOnPasswordChange)));
			} else {
				predicates.add(builder.isTrue(root.get(SysSystemAttributeMapping_.sendOnlyOnPasswordChange)));
			}
		}

		if (filter.getPasswordAttribute() != null) {
			if (BooleanUtils.isFalse(filter.getPasswordAttribute())) {
				predicates.add(builder.isFalse(root.get(SysSystemAttributeMapping_.passwordAttribute)));
			} else {
				predicates.add(builder.isTrue(root.get(SysSystemAttributeMapping_.passwordAttribute)));
			}
		}

		if (filter.getDisabledAttribute() != null) {
			if (BooleanUtils.isFalse(filter.getDisabledAttribute())) {
				predicates.add(builder.isFalse(root.get(SysSystemAttributeMapping_.disabledAttribute)));
			} else {
				predicates.add(builder.isTrue(root.get(SysSystemAttributeMapping_.disabledAttribute)));
			}
		}

		if (filter.getOperationType() != null) {
			Subquery<SysSystemMapping> subquery = query.subquery(SysSystemMapping.class);
			Root<SysSystemMapping> subRoot = subquery.from(SysSystemMapping.class);
			subquery.select(subRoot);

			subquery.where(
                    builder.and(
                    		builder.equal(root.get(SysSystemAttributeMapping_.systemMapping), subRoot), // correlation attr
                    		builder.equal(subRoot.get(SysSystemMapping_.operationType), filter.getOperationType())
                    		)
            );

			predicates.add(builder.exists(subquery));
		}

		if (filter.getEntityType() != null) {
			Subquery<SysSystemMapping> subquery = query.subquery(SysSystemMapping.class);
			Root<SysSystemMapping> subRoot = subquery.from(SysSystemMapping.class);
			subquery.select(subRoot);

			subquery.where(
                    builder.and(
                    		builder.equal(root.get(SysSystemAttributeMapping_.systemMapping), subRoot), // correlation attr
                    		builder.equal(subRoot.get(SysSystemMapping_.entityType), filter.getEntityType())
                    		)
            );

			predicates.add(builder.exists(subquery));
		}

		if (!StringUtils.isEmpty(filter.getName())) {
			predicates.add(builder.equal(root.get(SysSystemAttributeMapping_.name), filter.getName()));
		}

		if (filter.getAuthenticationAttribute() != null) {
			if (BooleanUtils.isFalse(filter.getAuthenticationAttribute())) {
				predicates.add(builder.isFalse(root.get(SysSystemAttributeMapping_.authenticationAttribute)));
			} else {
				predicates.add(builder.isTrue(root.get(SysSystemAttributeMapping_.authenticationAttribute)));
			}
		}

		if (filter.getPasswordFilter() != null) {
			if (BooleanUtils.isFalse(filter.getPasswordFilter())) {
				predicates.add(builder.isFalse(root.get(SysSystemAttributeMapping_.passwordFilter)));
			} else {
				predicates.add(builder.isTrue(root.get(SysSystemAttributeMapping_.passwordFilter)));
			}
		}
		
		if (filter.getStrategyType() != null) {
			predicates.add(builder.equal(root.get(SysSystemAttributeMapping_.strategyType), filter.getStrategyType()));
		}
		
		if (filter.getId() != null) {
			predicates.add(builder.equal(root.get(SysSystemAttributeMapping_.id), filter.getId()));
		}

		if (!StringUtils.isEmpty(filter.getTransformToScript())) {
			predicates.add(builder.like(root.get(SysSystemAttributeMapping_.transformToResourceScript), "%" + filter.getTransformToScript() + "%"));
		}

		if (!StringUtils.isEmpty(filter.getTransformFromScript())) {
			predicates.add(builder.like(root.get(SysSystemAttributeMapping_.transformFromResourceScript), "%" + filter.getTransformFromScript() + "%"));
		}

		return predicates;
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<SysSystemAttributeMappingDto> findBySystemMapping(SysSystemMappingDto systemMapping) {
		Assert.notNull(systemMapping, "System mapping is required.");

		// Backward compatible for create new SysSystemMappingDto
		// method in repository findAllBySystemMapping_Id return empty list for null value!
		if (systemMapping.getId() == null) {
			return Lists.newArrayList();
		}

		SysSystemAttributeMappingFilter filter = new SysSystemAttributeMappingFilter();
		filter.setSystemMappingId(systemMapping.getId());
		return this.find(filter, null).getContent();
	}

	@Override
	@Transactional(readOnly = true)
	public SysSystemAttributeMappingDto findBySystemMappingAndName(UUID systemMappingId, String name) {
		SysSystemAttributeMappingFilter filter = new SysSystemAttributeMappingFilter();
		filter.setSystemMappingId(systemMappingId);
		filter.setName(name);
		List<SysSystemAttributeMappingDto> content = this.find(filter, null).getContent();
		// Name must be unique for system mapping, checked by application and database
		return content.isEmpty() ? null : content.get(0);
	}

	@Override
	public Object transformValueToResource(String uid, Object value, AttributeMapping attributeMapping,
										   AbstractDto entity) {
		return transformValueToResource(uid, value, attributeMapping, entity, null);
	}

	@Override
	public Object transformValueToResource(String uid, Object value, AttributeMapping attributeMapping,
			AbstractDto entity, MappingContext mappingContext) {
		Assert.notNull(attributeMapping, "Attribute mapping is required.");
		try {
			return transformValueToResource(uid, value, attributeMapping.getTransformToResourceScript(), entity,
					getSystemFromAttributeMapping(attributeMapping), mappingContext);
		} catch (Exception e) {
			Map<String, Object> logParams = createTransformationScriptFailureParams(e, attributeMapping);
			ResultCodeException ex = new ResultCodeException(AccResultCode.GROOVY_SCRIPT_ATTR_TRANSFORMATION_FAILED,
					logParams, e);
			ExceptionUtils.log(log, ex);
			throw ex;
		}
	}

	@Override
	public Object transformValueToResource(String uid, Object value, String script, AbstractDto entity,
										   SysSystemDto system) {
		return transformValueToResource(uid, value, script, entity, system, null);
	}

	@Override
	public Object transformValueToResource(String uid, Object value, String script, AbstractDto entity,
			SysSystemDto system, MappingContext mappingContext) {
		if (!StringUtils.isEmpty(script)) {
			Map<String, Object> variables = new HashMap<>();
			variables.put(CONTEXT_KEY, mappingContext);
			variables.put(ACCOUNT_UID, uid);
			variables.put(ATTRIBUTE_VALUE_KEY, value);
			variables.put(SYSTEM_KEY, system);
			variables.put(ENTITY_KEY, entity);
			variables.put(AbstractScriptEvaluator.SCRIPT_EVALUATOR,
					pluginExecutors.getPluginFor(IdmScriptCategory.TRANSFORM_TO)); // add default script evaluator, for
																					// call another scripts
			//
			// Add access for script evaluator
			List<Class<?>> extraClass = new ArrayList<>();
			extraClass.add(AbstractScriptEvaluator.Builder.class);
			//
			return groovyScriptService.evaluate(script, variables, extraClass);
		}
		
		// If script is empty and value is instance of IdmAttachmentWithDataDto, then
		// attachment's data (array of bytes) will be returned.
		if (value instanceof IdmAttachmentWithDataDto) {
			IdmAttachmentWithDataDto attachmentWithDataDto = (IdmAttachmentWithDataDto) value;
			return attachmentWithDataDto.getData();
		}

		return value;
	}

	@Override
	public Object transformValueFromResource(Object value, AttributeMapping attributeMapping,
			List<IcAttribute> icAttributes) {
		Assert.notNull(attributeMapping, "Attribute mapping is required.");
		//
		try {
			return transformValueFromResource(value, attributeMapping.getTransformFromResourceScript(), icAttributes,
					getSystemFromAttributeMapping(attributeMapping), attributeMapping);
		} catch (Exception e) {
			Map<String, Object> logParams = createTransformationScriptFailureParams(e, attributeMapping);
			ResultCodeException ex = new ResultCodeException(AccResultCode.GROOVY_SCRIPT_ATTR_TRANSFORMATION_FAILED,
					logParams, e);
			ExceptionUtils.log(log, ex);
			throw ex;
		}
	}

	@Override
	public Object transformValueFromResource(Object value, String script, List<IcAttribute> icAttributes,
											 SysSystemDto system) {

		return transformValueFromResource(value, script, icAttributes, system, null);
	}
	
	@Override
	public Object transformValueFromResource(Object value, String script, List<IcAttribute> icAttributes,
											 SysSystemDto system, AttributeMapping attributeMapping) {

		if (!StringUtils.isEmpty(script)) {
			Map<String, Object> variables = new HashMap<>();
			variables.put(ATTRIBUTE_VALUE_KEY, value);
			variables.put(ATTRIBUTE_MAPPING_KEY, attributeMapping);
			variables.put(SYSTEM_KEY, system);
			variables.put(IC_ATTRIBUTES_KEY, icAttributes);
			variables.put(AbstractScriptEvaluator.SCRIPT_EVALUATOR,
					pluginExecutors.getPluginFor(IdmScriptCategory.TRANSFORM_FROM)); // add default script evaluator,
																						// for call another scripts
			// Add access for script evaluator
			List<Class<?>> extraClass = new ArrayList<>();
			extraClass.add(AbstractScriptEvaluator.Builder.class);
			//
			return groovyScriptService.evaluate(script, variables, extraClass);
		}

		return value;
	}


	@Override
	@Transactional
	public SysSystemAttributeMappingDto saveInternal(SysSystemAttributeMappingDto dto) {
		Assert.notNull(dto, "Attribute is mandatory!");
		Assert.notNull(dto.getSystemMapping(), "System mapping is mandatory!");
		SysSystemMappingDto systemMappingDto = systemMappingService.get(dto.getSystemMapping());

		validate(dto, systemMappingDto);

		// Check if exist some else attribute which is defined like unique identifier
		// If exists, then we will set they to uid = false. Only currently saving
		// attribute will be unique identifier
		if (dto.isUid() && dto.getSystemMapping() != null) {
			SysSystemAttributeMappingFilter filter = new SysSystemAttributeMappingFilter();
			filter.setSystemMappingId(dto.getSystemMapping());
			find(filter, null).forEach(attributeMapping -> {
				if (dto.getId() == null || !dto.getId().equals(attributeMapping.getId())) {
					attributeMapping.setUid(false);
					//
					save(attributeMapping);
				}
			});
		}
		// We will do script validation (on compilation errors), before save
		if (dto.getTransformFromResourceScript() != null) {
			groovyScriptService.validateScript(dto.getTransformFromResourceScript());
		}
		if (dto.getTransformToResourceScript() != null) {
			groovyScriptService.validateScript(dto.getTransformToResourceScript());
		}

		SystemEntityTypeRegistrable systemEntityType = systemEntityManager.getSystemEntityByCode(systemMappingDto.getEntityType());
		Class<? extends Identifiable> entityType = systemEntityType.getExtendedAttributeOwnerType();
		if (entityType != null && dto.isExtendedAttribute() && formService.isFormable(entityType)) {
			createExtendedAttributeDefinition(dto, entityType);
		}

		if (dto.isSendOnlyOnPasswordChange() && !dto.isSendOnPasswordChange()) {
			// This is invalid state. Check box "send only on password changed" can be checked
			// only if "send on password change" is checked too. 
			dto.setSendOnlyOnPasswordChange(false);
		}
		
		return super.saveInternal(dto);
	}

	/**
	 * Validation. If validation does not pass, then runtime exception is throw.
	 * 
	 * @param dto
	 * @return
	 */
	@Override
	public void validate(SysSystemAttributeMappingDto dto, SysSystemMappingDto systemMappingDto) {

		// Check if doesn't exists overridden attribute in role mapping,
		// For new attribute is this not required.
		if (!isNew(dto) && dto.isPasswordAttribute()) {
			SysRoleSystemAttributeFilter filter = new SysRoleSystemAttributeFilter();
			filter.setSystemAttributeMappingId(dto.getId());
			List<SysRoleSystemAttributeDto> overridden = roleSystemAttributeService.find(filter, null).getContent();
			// If exists overridden attribute throw error
			if (!overridden.isEmpty()) {
				// Get first role system attribute and show it in error message
				SysRoleSystemAttributeDto sysRoleSystemAttributeDto = overridden.get(0);
				throw new ResultCodeException(AccResultCode.SYSTEM_MAPPING_PASSWORD_EXITS_OVERRIDDEN,
						ImmutableMap.of("roleSystemAttributeId", sysRoleSystemAttributeDto.getId()));
			}
		}
				
		/**
		 * When provisioning is set, then can be schema attribute mapped only once.
		 */
		if (dto.getSchemaAttribute() != null && systemMappingDto != null
				&& SystemOperationType.PROVISIONING == systemMappingDto.getOperationType()) {
			SysSystemAttributeMappingFilter systemAttributeMappingFilter = new SysSystemAttributeMappingFilter();
			systemAttributeMappingFilter.setSchemaAttributeId(dto.getSchemaAttribute());
			systemAttributeMappingFilter.setSystemMappingId(systemMappingDto.getId());

			long count = this.find(systemAttributeMappingFilter, null).getContent() //
					.stream() //
					.filter(attribute -> !attribute.getId().equals(dto.getId())) //
					.count(); //

			if (count > 0) {
				throw new ResultCodeException(AccResultCode.PROVISIONING_DUPLICATE_ATTRIBUTE_MAPPING,
						ImmutableMap.of("schemaAttribute", dto.getSchemaAttribute()));
			}
		}
	}

	@Override
	public List<SysSystemAttributeMappingDto> getAllPasswordAttributes(UUID systemId, UUID systemMappingId) {
		// all password attributes, that isn't disabled
		SysSystemAttributeMappingFilter filter = new SysSystemAttributeMappingFilter();
		filter.setDisabledAttribute(Boolean.FALSE);
		filter.setPasswordAttribute(Boolean.TRUE);
		filter.setSystemId(systemId);
		filter.setSystemMappingId(systemMappingId);
		return this.find(filter, null).getContent();
	}

	/**
	 * Check on exists EAV definition for given attribute. If the definition not
	 * exist, then we try create it.
	 * 
	 * @param entity
	 * @param ownerType
	 */
	@Override
	@Transactional
	public void createExtendedAttributeDefinition(AttributeMapping entity, Class<? extends Identifiable> ownerType) {
		IdmFormAttributeDto attribute = formService.getAttribute(ownerType, entity.getIdmPropertyName());
		if (attribute == null) {
			log.info(MessageFormat.format(
					"IdmFormAttribute for identity and property {0} not found. We will create definition now.",
					entity.getIdmPropertyName()));
			formService.saveAttribute(ownerType, convertMappingAttribute(entity));
		}
	}

	@Override
	@Transactional
	public void delete(SysSystemAttributeMappingDto dto, BasePermission... permission) {
		Assert.notNull(dto, "DTO is required.");
		SysSystemAttributeMapping entity = this.getEntity(dto.getId());
		Assert.notNull(entity, "Entity is required.");

		SysSystemMappingDto systemMappingDto = DtoUtils.getEmbedded(dto, SysSystemAttributeMapping_.systemMapping, SysSystemMappingDto.class);
		SysSchemaObjectClassDto objectClassDto = DtoUtils.getEmbedded(systemMappingDto, SysSystemMapping_.objectClass, SysSchemaObjectClassDto.class);
		SysSystemDto systemDto = DtoUtils.getEmbedded(objectClassDto, SysSchemaObjectClass_.system, SysSystemDto.class);
		// Check if attribute is used in some systems group.
		SysSystemGroupSystemFilter groupSystemFilter = new SysSystemGroupSystemFilter();
		groupSystemFilter.setMergeMappingAttributeId(entity.getId());
		long count = systemGroupSystemService.count(groupSystemFilter);
		if (count > 0) {
			throw new ResultCodeException(AccResultCode.SYSTEM_DELETE_FAILED_HAS_SYSTEM_GROUPS,
					ImmutableMap.of("attribute", dto.getName(), "count", count));
		}
		if (syncConfigRepository.countByCorrelationAttribute_Id(dto.getId()) > 0) {
			throw new ResultCodeException(AccResultCode.ATTRIBUTE_MAPPING_DELETE_FAILED_USED_IN_SYNC,
					ImmutableMap.of("attribute", dto.getName(), "system", systemDto.getName()));
		}
		if (syncConfigRepository.countByFilterAttribute(entity) > 0) {
			throw new ResultCodeException(AccResultCode.ATTRIBUTE_MAPPING_DELETE_FAILED_USED_IN_SYNC,
					ImmutableMap.of("attribute", dto.getName(), "system", systemDto.getName()));
		}
		if (syncConfigRepository.countByTokenAttribute(entity) > 0) {
			throw new ResultCodeException(AccResultCode.ATTRIBUTE_MAPPING_DELETE_FAILED_USED_IN_SYNC,
					ImmutableMap.of("attribute", dto.getName(), "system", systemDto.getName()));
		}

		List<AbstractSysSyncConfigDto> syncConfigs = syncConfigService.findRoleConfigByMemberOfAttribute(entity.getId());
		if (syncConfigs.size() > 0){
			systemMappingDto = DtoUtils.getEmbedded(syncConfigs.get(0), SysSyncRoleConfig_.systemMapping, SysSystemMappingDto.class);
			objectClassDto = DtoUtils.getEmbedded(systemMappingDto, SysSystemMapping_.objectClass, SysSchemaObjectClassDto.class);
			systemDto = DtoUtils.getEmbedded(objectClassDto, SysSchemaObjectClass_.system, SysSystemDto.class);

			throw new ResultCodeException(AccResultCode.ATTRIBUTE_MAPPING_DELETE_FAILED_USED_IN_SYNC,
					ImmutableMap.of("attribute", dto.getName(), "system", systemDto.getName()));
		}
		
		// Delete attributes
		roleSystemAttributeRepository.deleteBySystemAttributeMapping(entity);

		// Delete of controlled and historic values
		if (dto.getId() != null) {
			SysAttributeControlledValueFilter attributeControlledValueFilter = new SysAttributeControlledValueFilter();
			attributeControlledValueFilter.setAttributeMappingId(dto.getId());

			List<SysAttributeControlledValueDto> controlledAndHistoricValues = attributeControlledValueService
					.find(attributeControlledValueFilter, null).getContent();
			controlledAndHistoricValues.forEach(value -> attributeControlledValueService.delete(value));
		}

		super.delete(dto, permission);
	}

	/**
	 * Create instance of IC attribute for given name. Given idm value will be
	 * transformed to resource.
	 */
	@Override
	public IcAttribute createIcAttribute(SysSchemaAttributeDto schemaAttribute, Object idmValue) {
		// Check type of value
		try {
			Class<?> classType = Class.forName(schemaAttribute.getClassType());

			// If is multivalue and value is list, then we will iterate list and check every
			// item on correct type
			if (schemaAttribute.isMultivalued() && idmValue instanceof List) {
				((List<?>) idmValue).stream().forEachOrdered(value -> {
					if (value != null && !(classType.isAssignableFrom(value.getClass()))) {
						throw new ProvisioningException(AccResultCode.PROVISIONING_ATTRIBUTE_VALUE_WRONG_TYPE,
								ImmutableMap.of("attribute", schemaAttribute.getName(), "schemaAttributeType",
										schemaAttribute.getClassType(), "valueType", value.getClass().getName()));
					}
				});

				// Check single value on correct type
			} else if (idmValue != null && !(classType.isAssignableFrom(idmValue.getClass()))) {
				if (idmValue instanceof GuardedString && classType.isAssignableFrom(String.class)) {
					// Value can be different type from schema but the type must be instance of
					// guarded string
					// and schema type must be assignable from string. Value to string will be
					// transform at the end.
				} else {
					throw new ProvisioningException(AccResultCode.PROVISIONING_ATTRIBUTE_VALUE_WRONG_TYPE,
							ImmutableMap.of("attribute", schemaAttribute.getName(), "schemaAttributeType",
									schemaAttribute.getClassType(), "valueType", idmValue.getClass().getName()));
				}
			}
		} catch (ClassNotFoundException e) {
			throw new ProvisioningException(AccResultCode.PROVISIONING_ATTRIBUTE_TYPE_NOT_FOUND, ImmutableMap.of(
					"attribute", schemaAttribute.getName(), "schemaAttributeType", schemaAttribute.getClassType()), e);
		}

		IcAttributeImpl icAttributeForUpdate = null;
		if (IcConnectorFacade.PASSWORD_ATTRIBUTE_NAME.equals(schemaAttribute.getName())) {
			// Attribute is password type
			icAttributeForUpdate = new IcPasswordAttributeImpl(schemaAttribute.getName(), (GuardedString) idmValue);

		} else {
			if (idmValue == null && schemaAttribute.isMultivalued()) {
				icAttributeForUpdate = new IcAttributeImpl(schemaAttribute.getName(), null, true);
			} else if (idmValue instanceof List) {
				@SuppressWarnings("unchecked")
				List<Object> values = (List<Object>) idmValue;
				icAttributeForUpdate = new IcAttributeImpl(schemaAttribute.getName(), values, true);
			} else {
				icAttributeForUpdate = new IcAttributeImpl(schemaAttribute.getName(), idmValue);
			}
			// Multivalued must be set by schema setting
			icAttributeForUpdate.setMultiValue(schemaAttribute.isMultivalued());
		}
		return icAttributeForUpdate;
	}

	@Override
	public SysSystemAttributeMappingDto clone(UUID id) {
		SysSystemAttributeMappingDto original = this.get(id);
		Assert.notNull(original, "Schema attribute must be found!");

		original.setId(null);
		EntityUtils.clearAuditFields(original);
		return original;
	}

	/**
	 * Convert schema attribute handling to Form attribute
	 * 
	 * @param entity
	 * @return
	 */
	private IdmFormAttributeDto convertMappingAttribute(AttributeMapping entity) {
		SysSchemaAttributeDto schemaAttribute = getSchemaAttribute(entity);
		IdmFormAttributeDto attributeDefinition = new IdmFormAttributeDto();
		attributeDefinition.setCode(entity.getIdmPropertyName());
		attributeDefinition.setName(entity.getName());
		attributeDefinition.setPersistentType(formPropertyManager.getPersistentType(schemaAttribute.getClassType()));
		attributeDefinition.setRequired(schemaAttribute.isRequired());
		attributeDefinition.setMultiple(schemaAttribute.isMultivalued());
		attributeDefinition.setReadonly(!schemaAttribute.isUpdateable());
		attributeDefinition.setConfidential(entity.isConfidentialAttribute());
		attributeDefinition.setUnmodifiable(false); // attribute can be deleted
		
		// We want to use short text as default (but only on this place)
		if (PersistentType.TEXT == attributeDefinition.getPersistentType()) {
			attributeDefinition.setPersistentType(PersistentType.SHORTTEXT);
		}
		SysSystemDto system = getSystemFromSchemaAttribute(schemaAttribute);
		//
		attributeDefinition.setDescription(
				MessageFormat.format("Generated by schema attribute {0} in resource {1}. Created by SYSTEM.",
						schemaAttribute.getName(), system.getName()));
		return attributeDefinition;
	}

	@Override
	public SysSystemAttributeMappingDto getAuthenticationAttribute(UUID systemId, String entityType) {
		Assert.notNull(systemId, "System identifier is required.");
		Assert.notNull(entityType, "Entity type is required.");
		// authentication attribute is only from provisioning operation type
		SysSystemAttributeMappingFilter filter = new SysSystemAttributeMappingFilter();
		filter.setEntityType(entityType);
		filter.setSystemId(systemId);
		filter.setOperationType(SystemOperationType.PROVISIONING);
		filter.setAuthenticationAttribute(Boolean.TRUE);
		List<SysSystemAttributeMappingDto> attributes = this.find(filter, null).getContent();
		
		// defensive, if authentication attribute don't exists find attribute flagged as
		// UID authentication attribute may be only one the integrity is checked by application before.
		if (attributes.isEmpty()) {
			filter.setIsUid(Boolean.TRUE);
			filter.setAuthenticationAttribute(null);
			attributes = this.find(filter, null).getContent();
			if (attributes.isEmpty()) {
				return null;
			}
			return attributes.get(0);
		}
		return attributes.get(0);
	}

	/**
	 * Find value for this mapped attribute by property name. Returned value can be
	 * list of objects. Returns transformed value.
	 * 
	 * @param uid               - Account identifier
	 * @param entity
	 * @param attributeHandling
	 * @return
	 * @throws IntrospectionException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 */
	@Override
	public Object getAttributeValue(String uid, AbstractDto entity, AttributeMapping attributeHandling) {
		return getAttributeValue(uid, entity, attributeHandling, null);
	}

	/**
	 * Find value for this mapped attribute by property name. Returned value can be
	 * list of objects. Returns transformed value.
	 * 
	 * @param uid               - Account identifier
	 * @param entity
	 * @param attributeHandling
	 * @return
	 * @throws IntrospectionException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 */
	@Override
	public Object getAttributeValue(String uid, AbstractDto entity, AttributeMapping attributeHandling, MappingContext mappingContext) {
		return getAttributeValue(uid, entity, attributeHandling, mappingContext, null);
	}

	@Override
	public Object getAttributeValue(String uid, AbstractDto entity, AttributeMapping attributeHandling, MappingContext mappingContext, AccAccountDto accountDto) {
		Object idmValue = null;
		//
		if (attributeHandling.isPasswordAttribute()) {
			// if attribute is mapped to PASSWORD transformation will be process
			// there but in PrepareConnectorObjectProcessor
			return null;
		}
		//
		SysSchemaAttributeDto schemaAttributeDto = getSchemaAttribute(attributeHandling);
		// Get value from account EAV = overriding value
		if (accountDto != null && accountDto.getFormDefinition() != null) {
			IdmFormDefinitionDto formDefinitionDto = DtoUtils.getEmbedded(accountDto, AccAccount_.formDefinition, IdmFormDefinitionDto.class, null);
			if (formDefinitionDto == null) {
				formDefinitionDto = formDefinitionService.get(accountDto.getFormDefinition());
			}

			List<IdmFormValueDto> values = formService.getValues(accountDto, formDefinitionDto, schemaAttributeDto.getName());
			if (!values.isEmpty()) {
				idmValue = getValuesFromEav(schemaAttributeDto, values);
			}
		}
		// Return overridden value without transformation
		if (idmValue != null) {
			return idmValue;
		}
		//
		if (attributeHandling.isExtendedAttribute() && entity != null && formService.isFormable(entity.getClass())) {
			List<IdmFormValueDto> formValues = formService.getValues(entity, attributeHandling.getIdmPropertyName());
			idmValue = getValuesFromEav(schemaAttributeDto, formValues);
		}
		// Find value from entity
		else if (attributeHandling.isEntityAttribute()) {
			if (attributeHandling.isConfidentialAttribute()) {
				// If is attribute isConfidential, then we will find value in
				// secured storage
				idmValue = confidentialStorage.getGuardedString(entity.getId(), entity.getClass(), attributeHandling.getIdmPropertyName());
			} else {
				try {
					// We will search value directly in entity by property name
					idmValue = EntityUtils.getEntityValue(entity, attributeHandling.getIdmPropertyName());
				} catch (IntrospectionException | IllegalAccessException | IllegalArgumentException
						 | InvocationTargetException | ProvisioningException ex) {
					throw new ProvisioningException(AccResultCode.PROVISIONING_IDM_FIELD_NOT_FOUND,
							ImmutableMap.of("property", attributeHandling.getIdmPropertyName(), "entityType",
									entity.getClass(), "schemaAtribute",
									attributeHandling.getSchemaAttribute().toString()),
							ex);
				}
			}
		}
		return this.transformValueToResource(uid, idmValue, attributeHandling, entity, mappingContext);
	}

	private Object getValuesFromEav(SysSchemaAttributeDto schemaAttributeDto, List<IdmFormValueDto> formValues) {
		Object idmValue;
		if (formValues.isEmpty()) {
			if (schemaAttributeDto.getClassType().equals(Boolean.class.getCanonicalName())) {
				// convert empty value to false for booleans
				idmValue = Boolean.FALSE;
			} else {
				idmValue = null;
			}
		} else if (schemaAttributeDto.isMultivalued()) {
			// Multiple value extended attribute
			List<Object> values = new ArrayList<>();
			formValues.stream().forEachOrdered(formValue -> {
				values.add(this.resolveFormValue(formValue));
			});
			idmValue = values;
		} else {
			// Single value extended attribute
			IdmFormValueDto formValue = formValues.get(0);
			if (formValue.isConfidential()) {
				Object confidentialValue = formService.getConfidentialPersistentValue(formValue);
				// If is confidential value String and schema attribute is GuardedString type,
				// then convert to GuardedString will be did.
				if (confidentialValue instanceof String
						&& schemaAttributeDto.getClassType().equals(GuardedString.class.getName())) {
					idmValue = new GuardedString((String) confidentialValue);
				} else {
					idmValue = confidentialValue;
				}
			} else {
				idmValue = this.resolveFormValue(formValue);
			}
		}
		return idmValue;
	}

	@Override
	public String generateUid(AbstractDto entity, SysSystemAttributeMappingDto uidAttribute) {
		Object uid = this.getAttributeValue(null, entity, uidAttribute);
		if (uid == null) {
			SysSystemDto systemEntity = getSystemFromAttributeMapping(uidAttribute);
			throw new ProvisioningException(AccResultCode.PROVISIONING_GENERATED_UID_IS_NULL,
					ImmutableMap.of("system", systemEntity.getName()));
		}
		if (!(uid instanceof String)) {
			throw new ProvisioningException(AccResultCode.PROVISIONING_ATTRIBUTE_UID_IS_NOT_STRING,
					ImmutableMap.of("uid", uid));
		}
		return (String) uid;
	}

	@Override
	public SysSystemAttributeMappingDto getUidAttribute(List<SysSystemAttributeMappingDto> mappedAttributes,
			SysSystemDto system) {
		List<SysSystemAttributeMappingDto> systemAttributeMappingUid = mappedAttributes.stream().filter(attribute -> {
			return !attribute.isDisabledAttribute() && attribute.isUid();
		}).collect(Collectors.toList());

		if (systemAttributeMappingUid.size() > 1) {
			throw new ProvisioningException(AccResultCode.PROVISIONING_ATTRIBUTE_MORE_UID,
					ImmutableMap.of("system", system.getName()));
		}
		if (systemAttributeMappingUid.isEmpty()) {
			throw new ProvisioningException(AccResultCode.PROVISIONING_ATTRIBUTE_UID_NOT_FOUND,
					ImmutableMap.of("system", system.getName()));
		}
		return systemAttributeMappingUid.get(0);
	}

	@Override
	public Object getValueByMappedAttribute(AttributeMapping attribute, List<IcAttribute> icAttributes) {
		Object icValue = null;
		Optional<IcAttribute> optionalIcAttribute = icAttributes.stream().filter(icAttribute -> {
			SysSchemaAttributeDto schemaAttributeDto = getSchemaAttribute(attribute);
			return schemaAttributeDto.getName().equals(icAttribute.getName());
		}).findFirst();
		if (optionalIcAttribute.isPresent()) {
			IcAttribute icAttribute = optionalIcAttribute.get();
			if (icAttribute.isMultiValue()) {
				icValue = icAttribute.getValues();
			} else {
				icValue = icAttribute.getValue();
			}
		}
		return this.transformValueFromResource(icValue, attribute, icAttributes);
	}

	@Override
	public String getUidValueFromResource(List<IcAttribute> icAttributes,
			List<SysSystemAttributeMappingDto> mappedAttributes, SysSystemDto system) {
		SysSystemAttributeMappingDto uidAttribute = this.getUidAttribute(mappedAttributes, system);
		Object uid = this.getValueByMappedAttribute(uidAttribute, icAttributes);

		if (uid == null) {
			SysSystemDto systemEntity = getSystemFromAttributeMapping(uidAttribute);
			throw new ProvisioningException(AccResultCode.PROVISIONING_GENERATED_UID_IS_NULL,
					ImmutableMap.of("system", systemEntity.getName()));
		}
		if (!(uid instanceof String)) {
			SysSystemDto systemEntity = getSystemFromAttributeMapping(uidAttribute);
			throw new ProvisioningException(AccResultCode.PROVISIONING_ATTRIBUTE_UID_IS_NOT_STRING,
					ImmutableMap.of("uid", uid.getClass(), "system", systemEntity.getName()));
		}
		return (String) uid;
	}

	@Override
	public List<Serializable> getControlledAttributeValues(UUID systemId, String entityType,
			String schemaAttributeName, UUID mappingId) {
		Assert.notNull(systemId, "System ID is mandatory for get controlled values!");
		Assert.notNull(entityType, "Entity type is mandatory for get controlled values!");
		Assert.notNull(schemaAttributeName, "Schema attribute name is mandatory for get controlled values!");

		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(systemId, entityType, mappingId);
		Assert.notNull(mapping, "System provisioning mapping is mandatory for search controlled attribute values!");
		List<Serializable> results = Lists.newArrayList();

		// Obtains controlled values from role-attributes
		SysRoleSystemAttributeFilter roleSystemAttributeFilter = new SysRoleSystemAttributeFilter();
		roleSystemAttributeFilter.setSystemMappingId(mapping.getId());
		roleSystemAttributeFilter.setSchemaAttributeName(schemaAttributeName);
		List<SysRoleSystemAttributeDto> roleSystemAttributes = roleSystemAttributeService
				.find(roleSystemAttributeFilter, null).getContent();

		roleSystemAttributes.stream() // We need values for merge and enabled attributes only
				.filter(roleSystemAttr -> AttributeMappingStrategyType.MERGE == roleSystemAttr.getStrategyType() //
						&& !roleSystemAttr.isDisabledAttribute()) //
				.forEach(roleSystemAttr -> { //
					Serializable value = getControlledValue(roleSystemAttr, systemId, schemaAttributeName);
					if (value != null && !results.contains(value)) {
						results.add(value);
					}
				});

		return results;
	}

	/**
	 * Get controlled merge value. Evaluates transformation to resource.
	 * 
	 * @param roleSystemAttr
	 * @param systemId
	 * @param schemaAttributeName
	 * @return
	 */
	private Serializable getControlledValue(AttributeMapping roleSystemAttr, UUID systemId,
			String schemaAttributeName) {
		// We predicate only static script (none input variables, only system)!
		Object value = this.transformValueToResource(null, null, roleSystemAttr, null);
		if (value != null) {
			if (!(value instanceof Serializable)) {
				throw new ResultCodeException(AccResultCode.PROVISIONING_CONTROLLED_VALUE_IS_NOT_SERIALIZABLE,
						ImmutableMap.of("value", value, "name", schemaAttributeName, "system", systemId));
			}
			return (Serializable) value;
		}
		return null;
	}

	@Override
	public List<Serializable> getCachedControlledAndHistoricAttributeValues(UUID systemId, String entityType,
																			String schemaAttributeName, UUID mappingId) {
		Assert.notNull(systemId, "System ID is mandatory for get controlled values!");
		Assert.notNull(entityType, "Entity type is mandatory for get controlled values!");
		Assert.notNull(schemaAttributeName, "Schema attribute name is mandatory for get controlled values!");

		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(systemId, entityType, mappingId);
		Assert.notNull(mapping, "System provisioning mapping is mandatory for search controlled attribute values!");

		List<SysSystemAttributeMappingDto> attributes = this.getAttributeMapping(schemaAttributeName, mapping,
				systemId);
		Assert.notEmpty(attributes, "Mapping attribute must exists!");
		Assert.isTrue(attributes.size() == 1,
				"Only one mapping attribute is allowed for same schema attribute in the provisioning!");
		SysSystemAttributeMappingDto attributeMapping = attributes.get(0);

		SysAttributeControlledValueFilter attributeControlledValueFilter = new SysAttributeControlledValueFilter();
		attributeControlledValueFilter.setAttributeMappingId(attributeMapping.getId());
		attributeControlledValueFilter.setHistoricValue(Boolean.FALSE);

		// Search controlled values for that attribute
		List<Serializable> cachedControlledValues = attributeControlledValueService //
				.find(attributeControlledValueFilter, null) //
				.getContent() //
				.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());
		if (attributeMapping.isEvictControlledValuesCache()) {
			List<Serializable> controlledAttributeValues = recalculateAttributeControlledValues(systemId, entityType,
					schemaAttributeName, attributeMapping, mappingId);
			cachedControlledValues = controlledAttributeValues;
		}

		// Set filter for search historic values
		attributeControlledValueFilter.setHistoricValue(Boolean.TRUE);

		// Search historic controlled values for that attribute
		List<Serializable> historicControlledValues = attributeControlledValueService //
				.find(attributeControlledValueFilter, null) //
				.getContent() //
				.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());

		List<Serializable> controlledValues = Lists.newArrayList();
		controlledValues.addAll(cachedControlledValues);
		controlledValues.addAll(historicControlledValues);

		return controlledValues;
	}

	@Override
	public synchronized List<Serializable> recalculateAttributeControlledValues(UUID systemId, String entityType,
			String schemaAttributeName, SysSystemAttributeMappingDto attributeMapping, UUID mappingId) {
		// This is needed so that the transaction has bigger context than synchronized block. This will prevent deadlocks
		return applicationContext.getBean(DefaultSysSystemAttributeMappingService.class).recalculateAttributeControlledValuesInternal(systemId, entityType, schemaAttributeName, attributeMapping, mappingId);
	}

	@Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRES_NEW)
	public List<Serializable>  recalculateAttributeControlledValuesInternal(UUID systemId, String entityType,
			String schemaAttributeName, SysSystemAttributeMappingDto attributeMapping, UUID mappingId) {
		// Computes values
		List<Serializable> controlledAttributeValues = this.getControlledAttributeValues(systemId, entityType,
				schemaAttributeName, mappingId);
		// Save results
		attributeControlledValueService.setControlledValues(attributeMapping, controlledAttributeValues);
		return controlledAttributeValues;
	}
	
	@Override
	public void export(UUID id, IdmExportImportDto batch) {
		
		// Export EAV definition connected to this attribute
		SysSystemAttributeMappingDto attribute = get(id);
		if (attribute!= null) {
			SysSystemMappingDto mapping = DtoUtils.getEmbedded(attribute,
					SysSystemAttributeMapping_.systemMapping.getName(), SysSystemMappingDto.class);
	
			SystemEntityTypeRegistrable systemEntityType = systemEntityManager.getSystemEntityByCode(mapping.getEntityType());
			Class<? extends Identifiable> entityType = systemEntityType.getExtendedAttributeOwnerType();
			if (entityType != null && attribute.isExtendedAttribute() && formService.isFormable(entityType)) {
				IdmFormAttributeDto formAttribute = formService.getAttribute(entityType, attribute.getIdmPropertyName());
				if (formAttribute != null) {
					// EAV attribute definition was found for this mapped attribute.
					// Export definition without attributes.
					formDefinitionService.exportOnlyDefinition(formAttribute.getFormDefinition(), batch);
					// Export given attribute (not all).
					formAttributeService.export(formAttribute.getId(), batch);
				}
			}
		}
		
		super.export(id, batch);
		
	}
	
	/**
	 * Resolve form value
	 * 
	 * @param formValue
	 * @return
	 */
	private Object resolveFormValue(IdmFormValueDto formValue) {
		if(formValue == null) {
			return null;
		}
		Serializable value = formValue.getValue();
		
		// If EAV attribute is Attachment and value is UUID, then attachment will be
		// loaded and transformed to IdmAttachmentWithDataDto. Value will be replaced by
		// IdmAttachmentWithDataDto.
		if (PersistentType.ATTACHMENT == formValue.getPersistentType() && value instanceof UUID) {
			IdmAttachmentDto attachmentDto = attachmentManager.get((UUID) value);
			
			// Convert attachment to attachment with data
			IdmAttachmentWithDataDto attachmentWithDataDto = this.convertAttachment(attachmentDto);
			
			try (InputStream inputStream = attachmentManager.getAttachmentData((UUID) value)) {
				if (inputStream != null) {
					byte[] bytes = IOUtils.toByteArray(inputStream);
					attachmentWithDataDto.setData(bytes);
				}	
			} catch (IOException e) {
				throw new CoreException(e);
			}
			
			return attachmentWithDataDto;
		}
		
		return value;
	}

	/**
	 * Convert attachment to {@link IdmAttachmentWithDataDto}
	 * 
	 * @param attachmentDto
	 * @return
	 */
	private IdmAttachmentWithDataDto convertAttachment(IdmAttachmentDto attachmentDto) {
		if(attachmentDto == null) {
			return null;
		}
		
		IdmAttachmentWithDataDto data = new IdmAttachmentWithDataDto(attachmentDto);
		data.setAttachmentType(attachmentDto.getAttachmentType());
		data.setContentId(attachmentDto.getContentId());
		data.setContentPath(attachmentDto.getContentPath());
		data.setDescription(attachmentDto.getDescription());
		data.setEncoding(attachmentDto.getEncoding());
		data.setFilesize(attachmentDto.getFilesize());
		data.setId(attachmentDto.getId());
		data.setMimetype(attachmentDto.getMimetype());
		data.setName(attachmentDto.getName());
		data.setNextVersion(attachmentDto.getNextVersion());
		data.setOwnerId(attachmentDto.getOwnerId());
		data.setOwnerState(attachmentDto.getOwnerState());
		data.setParent(attachmentDto.getParent());
		data.setVersionLabel(attachmentDto.getVersionLabel());
		data.setVersionNumber(attachmentDto.getVersionNumber());
		
		return data;
	}

	/**
	 * Method return schema attribute from interface attribute mapping. Schema may
	 * be null from RoleSystemAttribute
	 * 
	 * @return
	 */
	private SysSchemaAttributeDto getSchemaAttribute(AttributeMapping attributeMapping) {
		if (attributeMapping.getSchemaAttribute() != null) {
			if (attributeMapping instanceof SysSystemAttributeMappingDto) {
				 SysSchemaAttributeDto schemaAttributeDto = DtoUtils.getEmbedded((SysSystemAttributeMappingDto) attributeMapping,
						SysSystemAttributeMapping_.schemaAttribute, SysSchemaAttributeDto.class, null);
				 if (schemaAttributeDto != null) {
					 return schemaAttributeDto;
				 }
			}
			return schemaAttributeService.get(attributeMapping.getSchemaAttribute());
		} else {
			// schema attribute is null = roleSystemAttribute
			SysSystemAttributeMappingDto dto = this
					.get(((SysRoleSystemAttributeDto) attributeMapping).getSystemAttributeMapping());
			return schemaAttributeService.get(dto.getSchemaAttribute());
		}
	}

	/**
	 * Method return {@link SysSystemDto} from {@link AttributeMapping}
	 * 
	 * @param attributeMapping
	 * @return
	 */
	private SysSystemDto getSystemFromAttributeMapping(AttributeMapping attributeMapping) {
		SysSchemaAttributeDto schemaAttrDto = getSchemaAttribute(attributeMapping);
		return getSystemFromSchemaAttribute(schemaAttrDto);
	}

	private SysSystemDto getSystemFromSchemaAttribute(SysSchemaAttributeDto schemaAttrDto) {
		SysSchemaObjectClassDto schemaObject = schemaObjectClassService.get(schemaAttrDto.getObjectClass());
		return getSystemFromSchemaObjectClass(schemaObject);
	}

	private SysSystemDto getSystemFromSchemaObjectClass(SysSchemaObjectClassDto schemaObject) {
		return DtoUtils.getEmbedded(schemaObject, SysSchemaObjectClass_.system);
	}

	/**
	 * Find all mapped attribute for given schema attribute name and mapping.
	 *
	 */
	private List<SysSystemAttributeMappingDto> getAttributeMapping(String schemaAttributeName,
			SysSystemMappingDto mapping, UUID systemId) {
		Assert.notNull(schemaAttributeName, "Schema attribute is required.");
		Assert.notNull(mapping, "Mapping is required.");
		Assert.notNull(systemId, "System identifier is required.");

		SysSystemAttributeMappingFilter filter = new SysSystemAttributeMappingFilter();
		filter.setSchemaAttributeName(schemaAttributeName);
		filter.setSystemMappingId(mapping.getId());
		filter.setSystemId(systemId);

		return this.find(filter, null).getContent();
	}
	
	/**
	 * Creates a list of attribute mapping info from which an error originates
	 * Contains: system name / mapping or role name / mapped attribute name
	 * 
	 * @param attributeMapping
	 * @return
	 */
	private List<String> createMappingIdmPath(AttributeMapping attributeMapping) {
		List<String> path = new ArrayList<>();
		// attribute name
		path.add(String.format("Attr: %s", attributeMapping.getName()));

		// role and system mapping name
		SysSystemAttributeMappingDto sysMapping = null;
		if (attributeMapping instanceof SysRoleSystemAttributeDto) {
			SysRoleSystemAttributeDto mapping = (SysRoleSystemAttributeDto) attributeMapping;
			// get role name
			SysRoleSystemDto roleSystem = DtoUtils.getEmbedded(mapping, SysRoleSystemAttribute_.roleSystem, SysRoleSystemDto.class, null);
			// mapping name and role name are not be available in case of script pre-evaluation during saving
			if (roleSystem != null) {
			IdmRoleDto roleDto = DtoUtils.getEmbedded(roleSystem, SysRoleSystem_.role, IdmRoleDto.class);
			path.add(String.format("Role: %s", roleDto.getCode()));
			sysMapping = DtoUtils.getEmbedded(mapping, SysRoleSystemAttribute_.systemAttributeMapping,
					SysSystemAttributeMappingDto.class, null);
			}
		} else if (attributeMapping instanceof SysSystemAttributeMappingDto) {
			sysMapping = (SysSystemAttributeMappingDto) attributeMapping;
		}
		
		if (sysMapping != null) {
			String mappingName = DtoUtils
					.getEmbedded(sysMapping, SysSystemAttributeMapping_.systemMapping, SysSystemMappingDto.class)
					.getName();
			path.add(String.format("Mapping: %s", mappingName));
		}
		
		// system name
		path.add(String.format("System: %s", getSystemFromAttributeMapping(attributeMapping).getCode()));
		return path;
	}
	
	/**
	 * Composes parameters of {@linkGROOVY_SCRIPT_ATTR_TRANSFORMATION_FAILED} exception.
	 * 
	 * @param ex
	 * @param attributeMapping
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> createTransformationScriptFailureParams(Throwable ex, AttributeMapping attributeMapping) {
		Map<String, Object> result = new LinkedHashMap<String, Object>(3);
		List<String> idmPath = createMappingIdmPath(attributeMapping);
		List<String> codePath = (List<String>) (List<?>) ExceptionUtils.getParameterChainByKey(ex,
				AbstractScriptEvaluator.SCRIPT_NAME_KEY, CoreResultCode.GROOVY_SCRIPT_EXCEPTION);

		String message = Throwables.getRootCause(ex).getLocalizedMessage();
		result.put(SysSystemAttributeMappingService.MAPPING_SCRIPT_FAIL_MESSAGE_KEY, message);

		StringBuilder sb = new StringBuilder();
		for (int i = idmPath.size() - 1; i >= 0; i--) {
			sb.append(idmPath.get(i));
			if (i > 0) {
				sb.append(" / ");
			}
		}
		result.put(SysSystemAttributeMappingService.MAPPING_SCRIPT_FAIL_IDM_PATH_KEY, sb.toString());

		sb.setLength(0);
		for (int i = 0; i < codePath.size(); i++) {
			sb.append(codePath.get(i));
			if (i < codePath.size() - 1) {
				sb.append(" / ");
			}
		}
		result.put(SysSystemAttributeMappingService.MAPPING_SCRIPT_FAIL_SCRIPT_PATH_KEY, sb.toString());
		return result;
	}

	@Override
	public List<SysSystemAttributeMappingDto> getScriptTransformToUsage(String scriptCode) {
		SysSystemAttributeMappingFilter attributeFilter = new SysSystemAttributeMappingFilter();
		attributeFilter.setTransformToScript(scriptCode);
		//
		return this.find(attributeFilter, null).getContent();
	}

	@Override
	public List<SysSystemAttributeMappingDto> getScriptTransformFromUsage(String scriptCode) {
		SysSystemAttributeMappingFilter attributeFilter = new SysSystemAttributeMappingFilter();
		attributeFilter.setTransformFromScript(scriptCode);
		//
		return this.find(attributeFilter, null).getContent();
	}

	@Override
	public List<SysSystemAttributeMappingDto> getScriptUsage(String scriptCode) {
		List<SysSystemAttributeMappingDto> result = new ArrayList<>();
		List<SysSystemAttributeMappingDto> inTransformationFrom = getScriptTransformFromUsage(scriptCode);
		result.addAll(inTransformationFrom);
		List<SysSystemAttributeMappingDto> inTransformationTo = getScriptTransformToUsage(scriptCode);
		result.addAll(inTransformationTo);

		return result;
	}

	@Override
	public Object getTransformedValueForAttributeAndAccount(String accountId, String schemaAttrName) {
		AccAccountDto accountDto = accountService.get(accountId);

		SysSystemAttributeMappingFilter attributeMappingFilter = new SysSystemAttributeMappingFilter();
		attributeMappingFilter.setSchemaAttributeName(schemaAttrName);
		attributeMappingFilter.setSystemMappingId(accountDto.getSystemMapping());
		attributeMappingFilter.setSystemId(accountDto.getSystem());
		List<SysSystemAttributeMappingDto> systemAttributeMappingDtos = this.find(attributeMappingFilter, null).getContent();

		if (systemAttributeMappingDtos.size() != 1) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}

		SysSystemAttributeMappingDto systemAttributeMappingDto = systemAttributeMappingDtos.get(0);

		SystemEntityTypeRegistrable systemEntityType = systemEntityManager.getSystemEntityByCode(accountDto.getTargetEntityType());
		AbstractDto entity = lookupService.lookupDto(systemEntityType.getEntityType(), accountDto.getTargetEntityId());

		SysSystemMappingDto systemMappingDto = DtoUtils.getEmbedded(accountDto, AccAccount_.systemMapping, SysSystemMappingDto.class);
		SysSystemEntityDto systemEntityDto = DtoUtils.getEmbedded(accountDto, AccAccount_.systemEntity, SysSystemEntityDto.class);
		SysSystemDto systemDto = DtoUtils.getEmbedded(accountDto, AccAccount_.system, SysSystemDto.class);
		MappingContext mappingContext = systemMappingService.getMappingContext(systemMappingDto, systemEntityDto, entity, systemDto);

		Object transformValueToResource = this.getAttributeValue(accountDto.getUid(), entity, systemAttributeMappingDto, mappingContext);
		if (transformValueToResource == null) {
			transformValueToResource = "";
		}
		return transformValueToResource;
	}

}
