package eu.bcvsolutions.idm.acc.event.processor.provisioning;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.AccModuleDescriptor;
import eu.bcvsolutions.idm.acc.config.domain.ProvisioningConfiguration;
import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.ProvisioningContext;
import eu.bcvsolutions.idm.acc.domain.ProvisioningEventType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.ProvisioningAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningOperationDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.entity.AccAccount_;
import eu.bcvsolutions.idm.acc.entity.SysProvisioningOperation_;
import eu.bcvsolutions.idm.acc.entity.SysSchemaAttribute;
import eu.bcvsolutions.idm.acc.entity.SysSystemAttributeMapping_;
import eu.bcvsolutions.idm.acc.exception.ProvisioningException;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.ConnectorManager;
import eu.bcvsolutions.idm.acc.service.api.ConnectorType;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningArchiveService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningOperationService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityTypeManager;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.acc.service.api.UniformPasswordManager;
import eu.bcvsolutions.idm.acc.system.entity.SystemEntityTypeRegistrable;
import eu.bcvsolutions.idm.core.api.domain.IdmPasswordPolicyType;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmPasswordPolicyDto;
import eu.bcvsolutions.idm.core.api.event.AbstractEntityEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.ConfidentialStorage;
import eu.bcvsolutions.idm.core.api.service.IdmPasswordPolicyService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.security.api.domain.ConfidentialString;
import eu.bcvsolutions.idm.core.security.api.domain.Enabled;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.api.IcObjectClass;
import eu.bcvsolutions.idm.ic.impl.IcConnectorObjectImpl;

/**
 * Prepare provisioning - resolve connector object properties from account and
 * resolve create or update operations.
 * 
 * @author Radek Tomiška
 * @author Ondrej Husnik
 * @author Roman Kucera
 */
@Component(PrepareConnectorObjectProcessor.PROCESSOR_NAME)
@Enabled(AccModuleDescriptor.MODULE_ID)
@Description("Prepares connector object from account properties. Resolves create or update provisioning operation (reads object from target system).")
public class PrepareConnectorObjectProcessor extends AbstractEntityEventProcessor<SysProvisioningOperationDto> {

	public static final String PROCESSOR_NAME = "prepare-connector-object-processor";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PrepareConnectorObjectProcessor.class);
	//
	private final SysSystemAttributeMappingService attributeMappingService;
	private final SysSystemService systemService;
	private final SysProvisioningOperationService provisioningOperationService;
	private final SysSchemaAttributeService schemaAttributeService;
	private final SysSchemaObjectClassService schemaObjectClassService;
	private final ProvisioningConfiguration provisioningConfiguration;
	//
	@Autowired private ProvisioningService provisioningService;
	@Autowired private LookupService lookupService;
	@Autowired private IdmPasswordPolicyService passwordPolicyService;
	@Autowired private ConfidentialStorage confidentialStorage;
	@Autowired private SysProvisioningAttributeService provisioningAttributeService;
	@Autowired private UniformPasswordManager uniformPasswordManager;
	@Autowired private ConnectorManager connectorManager;
	@Autowired private SysSystemEntityTypeManager systemEntityManager;

	@Autowired
	private AccAccountService accountService;
	@Autowired
	private SysSystemMappingService systemMappingService;
	
	@Autowired
	public PrepareConnectorObjectProcessor(
			SysSystemService systemService,
			SysSystemEntityService systemEntityService,
			SysProvisioningOperationService provisioningOperationService,
			SysSystemAttributeMappingService attributeMappingService,
			SysSchemaAttributeService schemaAttributeService,
			SysProvisioningArchiveService provisioningArchiveService,
			SysSchemaObjectClassService schemaObjectClassService,
			ProvisioningConfiguration provisioningConfiguration) {
		super(ProvisioningEventType.CREATE, ProvisioningEventType.UPDATE);
		//
		Assert.notNull(systemEntityService, "Service is required.");
		Assert.notNull(attributeMappingService, "Service is required.");
		Assert.notNull(systemService, "Service is required.");
		Assert.notNull(provisioningOperationService, "Service is required.");
		Assert.notNull(schemaAttributeService, "Service is required.");
		Assert.notNull(provisioningArchiveService, "Service is required.");
		Assert.notNull(schemaObjectClassService, "Service is required.");
		Assert.notNull(provisioningConfiguration, "Configuration is required.");
		//
		this.attributeMappingService = attributeMappingService;
		this.systemService = systemService;
		this.provisioningOperationService = provisioningOperationService;
		this.schemaAttributeService = schemaAttributeService;
		this.schemaObjectClassService = schemaObjectClassService;
		this.provisioningConfiguration = provisioningConfiguration;
	}

	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	/**
	 * Prepare provisioning operation execution
	 */
	@Override
	public EventResult<SysProvisioningOperationDto> process(EntityEvent<SysProvisioningOperationDto> event) {
		SysProvisioningOperationDto provisioningOperation = event.getContent();
		SysSystemDto system = systemService.get(provisioningOperation.getSystem());
		IcObjectClass objectClass = provisioningOperation.getProvisioningContext().getConnectorObject()
				.getObjectClass();
		SysSystemEntityDto systemEntity = provisioningOperationService
				.getByProvisioningOperation(provisioningOperation);
		String uid = systemEntity.getUid();
		boolean isWish = systemEntity.isWish();
		LOG.debug(
				"Start preparing attribubes for provisioning operation [{}] for object with uid [{}] and connector object [{}]",
				provisioningOperation.getOperationType(), uid, objectClass.getType());
		//
		// Find connector identification persisted in system
		if (system.getConnectorKey() == null) {
			throw new ProvisioningException(AccResultCode.CONNECTOR_KEY_FOR_SYSTEM_NOT_FOUND,
					ImmutableMap.of("system", system.getName()));
		}
		
		try {
			IcConnectorObject existsConnectorObject = null;
			// We do not want search account on the target system, when this is the first
			// call the connector and auto mapping is not allowed.
			ConnectorType connectorType = connectorManager.findConnectorTypeBySystem(system);
			if (!isWish || provisioningConfiguration.isAllowedAutoMappingOnExistingAccount()) {
				existsConnectorObject = connectorType.readConnectorObject(system, uid, objectClass);
			}
			if (existsConnectorObject == null) {
				processCreate(provisioningOperation);
			} else {
				processUpdate(provisioningOperation, existsConnectorObject, connectorType);
				// prepare attributes on target system for FE view
				ProvisioningContext context = provisioningOperation.getProvisioningContext();
				IcConnectorObject systemAttrs = intersectAccountAndSystemAttrs(context.getAccountObject(), existsConnectorObject);
				context.setSystemConnectorObject(systemAttrs);
				if(!provisioningOperation.isDryRun()) {
					provisioningOperation = provisioningOperationService.saveOperation(provisioningOperation);
				}
			}
			//
			LOG.debug(
					"Preparing attribubes for provisioning operation [{}] for object with uid [{}] and connector object [{}] is sucessfully completed",
					provisioningOperation.getOperationType(), uid, objectClass.getType());
			if (!provisioningOperation.isDryRun()) {
				// set back to event content
				provisioningOperation = provisioningOperationService.saveOperation(provisioningOperation);
				// log attributes used in provisioning context into provisioning attributes
				provisioningAttributeService.saveAttributes(provisioningOperation);
			}
			event.setContent(provisioningOperation);
			return new DefaultEventResult<>(event, this);
		} catch (Exception ex) {
			if(!provisioningOperation.isDryRun()) {
				provisioningOperation = provisioningOperationService.handleFailed(provisioningOperation, ex);
			}
			event.setContent(provisioningOperation);
			return new DefaultEventResult<>(event, this, true);
		}
	}

	/**
	 * Create object on target system
	 * 
	 * @param provisioningOperation
	 */
	private void processCreate(SysProvisioningOperationDto provisioningOperation) {
		SysSystemDto system = systemService.get(provisioningOperation.getSystem());
		ProvisioningContext provisioningContext = provisioningOperation.getProvisioningContext();
		IcConnectorObject connectorObject = provisioningContext.getConnectorObject();
		//
		// prepare provisioning attributes from account attributes
		Map<ProvisioningAttributeDto, Object> fullAccountObject = provisioningOperationService
				.getFullAccountObject(provisioningOperation);
		if (fullAccountObject != null) {
			connectorObject.getAttributes().clear();
			SystemEntityTypeRegistrable systemEntityType = systemEntityManager.getSystemEntityByCode(provisioningOperation.getEntityType());

			SysSystemMappingDto mapping = getMapping(provisioningOperation);
			SysSchemaObjectClassDto schemaObjectClassDto = schemaObjectClassService.get(mapping.getObjectClass());

			List<SysSchemaAttributeDto> schemaAttributes = findSchemaAttributes(system, schemaObjectClassDto);

			List<SysSystemAttributeMappingDto> passwordAttributes = attributeMappingService
					.getAllPasswordAttributes(system.getId(), mapping.getId());
			GuardedString generatedPassword = null;
			// If exists at least one password attribute generate password and try set echos for current system
			if (!passwordAttributes.isEmpty()) {
				// Check if exists a uniform password for this entity. If yes, then use it.
				if (provisioningOperation.getEntityIdentifier() != null
						&& provisioningOperation.getEntityType() != null
						&& provisioningOperation.getSystem() != null
						&& provisioningOperation.getTransactionId() != null) {
					
					if (uniformPasswordManager.isSystemInUniformPasswordAgenda(provisioningOperation.getSystem())) {
						generatedPassword = uniformPasswordManager.generateUniformPassword(
								provisioningOperation.getEntityIdentifier(),
								systemEntityType.getEntityType(),
								provisioningOperation.getTransactionId());
					}
				}
				if (generatedPassword == null) {
					generatedPassword = generatePassword(system);
				}
			} else {
				generatedPassword = null;
			}

			// Found all given password from original provisioning context, these passwords will be skipped
			List<ProvisioningAttributeDto> givenPasswords = provisioningContext.getAccountObject().keySet().stream()
					.filter(ProvisioningAttributeDto::isPasswordAttribute).collect(Collectors.toList());
			
			// Iterate over all password attributes founded for system and mapping
			for (SysSystemAttributeMappingDto passwordAttribute : passwordAttributes) {

				// Password may be add by another process or execute existing provisioning operation, these password skip
				SysSchemaAttributeDto schemaByPasswordAttribute = DtoUtils.getEmbedded(passwordAttribute,
						SysSystemAttributeMapping_.schemaAttribute, SysSchemaAttributeDto.class);
				Optional<ProvisioningAttributeDto> findAnyPassword = givenPasswords //
						.stream() //
						.filter(givenPassword -> givenPassword.getSchemaAttributeName()
								.equals(schemaByPasswordAttribute.getName())) //
						.findAny(); //
				if (findAnyPassword.isPresent()) {
					continue;
				}
				
				// All non existing passwords in provisioning context will be added and
				// transformed. Then will be set as new attribute into fullAccountObject
				GuardedString transformPassword = transformPassword(provisioningOperation, passwordAttribute, generatedPassword);
				SysSchemaAttributeDto schemaAttribute = schemaAttributes //
						.stream() //
						.filter(schemaAtt -> schemaAtt.getId().equals(passwordAttribute.getSchemaAttribute())) //
						.findFirst()//
						.orElse(null);
				ProvisioningAttributeDto passwordProvisiongAttributeDto = ProvisioningAttributeDto
						.createProvisioningAttributeKey(passwordAttribute, schemaAttribute.getName(),
								schemaAttribute.getClassType());
				fullAccountObject.put(passwordProvisiongAttributeDto, transformPassword);

				// Update previous account object (gui left side)
				Map<ProvisioningAttributeDto, Object> accountObject = provisioningOperation.getProvisioningContext()
						.getAccountObject();

				// Is needed put password also into account object. Complete provisioning operation can be stored in
				// queue and while retry the provisioning operation is value get from confidential storage.
				// Confidential key is composed by account object.
				String confidentialStrorageKey = provisioningOperationService.createAccountObjectPropertyKey(passwordProvisiongAttributeDto.getKey(), 0);
				confidentialStorage.saveGuardedString(provisioningOperation, confidentialStrorageKey, transformPassword);
				accountObject.put(passwordProvisiongAttributeDto, new ConfidentialString(confidentialStrorageKey));
			}

			for (Entry<ProvisioningAttributeDto, Object> entry : fullAccountObject.entrySet()) {

				ProvisioningAttributeDto provisioningAttribute = entry.getKey();
				Optional<SysSchemaAttributeDto> schemaAttributeOptional = schemaAttributes.stream()
						.filter(schemaAttribute -> provisioningAttribute.getSchemaAttributeName().equals(schemaAttribute.getName())).findFirst();

				if (!schemaAttributeOptional.isPresent()) {
					throw new ProvisioningException(AccResultCode.PROVISIONING_SCHEMA_ATTRIBUTE_IS_FOUND,
							ImmutableMap.of("attribute", provisioningAttribute.getSchemaAttributeName()));
				}

				Object idmValue = fullAccountObject.get(provisioningAttribute);
				SysSchemaAttributeDto schemaAttribute = schemaAttributeOptional.get();

				if (provisioningAttribute.isSendOnlyIfNotNull() && this.isValueEmpty(idmValue)) {
					// Skip this attribute (marked with flag sendOnlyIfNotNull), because IdM value
					// is null
					continue;
				}

				if (AttributeMappingStrategyType.CREATE == provisioningAttribute.getStrategyType()
						|| AttributeMappingStrategyType.WRITE_IF_NULL == provisioningAttribute.getStrategyType()) {

					boolean existSetAttribute = fullAccountObject //
							.keySet() //
							.stream() //
							.anyMatch(provisioningAttributeKey -> provisioningAttributeKey.getSchemaAttributeName().equals(schemaAttribute.getName())
										&& AttributeMappingStrategyType.SET == provisioningAttributeKey.getStrategyType());

					boolean existIfResourceNulltAttribute = fullAccountObject  //
							.keySet() //
							.stream() //
							.anyMatch(provisioningAttributeKey -> provisioningAttributeKey.getSchemaAttributeName()
										.equals(schemaAttribute.getName())
										&& AttributeMappingStrategyType.WRITE_IF_NULL == provisioningAttributeKey
												.getStrategyType());

					boolean existMergeAttribute = fullAccountObject //
							.keySet() //
							.stream() //
							.anyMatch(provisioningAttributeKey -> provisioningAttributeKey.getSchemaAttributeName()
										.equals(schemaAttribute.getName())
										&& AttributeMappingStrategyType.MERGE == provisioningAttributeKey
												.getStrategyType());

					boolean existAuthMergeAttribute = fullAccountObject //
							.keySet() //
							.stream() //
							.anyMatch(provisioningAttributeKey -> provisioningAttributeKey.getSchemaAttributeName()
										.equals(schemaAttribute.getName())
										&& AttributeMappingStrategyType.AUTHORITATIVE_MERGE == provisioningAttributeKey
												.getStrategyType());

					if (AttributeMappingStrategyType.CREATE == provisioningAttribute.getStrategyType()) {

						if (existIfResourceNulltAttribute || existSetAttribute || existAuthMergeAttribute
								|| existMergeAttribute) {
							// Skip this attribute (with Create strategy), because exists same attribute
							// with SET/WRITE_IF_NULL/MERGE/AUTH_MERGE strategy
							// (this strategies has higher priority)
							continue;
						}
					}
					if (AttributeMappingStrategyType.WRITE_IF_NULL == provisioningAttribute.getStrategyType()) {

						if (existSetAttribute || existAuthMergeAttribute || existMergeAttribute) {
							// Skip this attribute (with WRITE_IF_NULL strategy), because exists same
							// attribute with SET/MERGE/AUTH_MERGE strategy
							// (this strategies has higher priority)
							continue;
						}
					}
				}

				IcAttribute createdAttribute = createAttribute(schemaAttribute,
						fullAccountObject.get(provisioningAttribute));
				if (createdAttribute != null) {
					connectorObject.getAttributes().add(createdAttribute);
				}
			}
			provisioningContext.setConnectorObject(connectorObject);
		}
		provisioningOperation.setOperationType(ProvisioningEventType.CREATE);
	}

	private void processUpdate(SysProvisioningOperationDto provisioningOperation,
							   IcConnectorObject existsConnectorObject,
							   ConnectorType connectorType) {
		SysSystemDto system = systemService.get(provisioningOperation.getSystem());
		String systemEntityUid = provisioningOperationService.getByProvisioningOperation(provisioningOperation)
				.getUid();
		ProvisioningContext provisioningContext = provisioningOperation.getProvisioningContext();
		IcConnectorObject connectorObject = provisioningContext.getConnectorObject();
		IcObjectClass objectClass = connectorObject.getObjectClass();
		//
		IcConnectorObject updateConnectorObject;
		if (provisioningContext.getAccountObject() == null) {
			updateConnectorObject = connectorObject;
		} else {
			Map<ProvisioningAttributeDto, Object> fullAccountObject = provisioningOperationService
					.getFullAccountObject(provisioningOperation);
			updateConnectorObject = new IcConnectorObjectImpl(systemEntityUid, objectClass, null);

			SysSystemMappingDto mapping = getMapping(provisioningOperation);
			SysSchemaObjectClassDto schemaObjectClassDto = schemaObjectClassService.get(mapping.getObjectClass());
			List<SysSchemaAttributeDto> schemaAttributes = findSchemaAttributes(system, schemaObjectClassDto);

			for (Entry<ProvisioningAttributeDto, Object> entry : fullAccountObject.entrySet()) {

				ProvisioningAttributeDto provisioningAttribute = entry.getKey();
				//  Resolve update for given attribute
				processUpdateByAttribute(provisioningAttribute, provisioningOperation, existsConnectorObject, system,
						updateConnectorObject, fullAccountObject, schemaAttributes, connectorType);
			}
		}
		//
		provisioningOperation.getProvisioningContext().setConnectorObject(updateConnectorObject);
		provisioningOperation.setOperationType(ProvisioningEventType.UPDATE);
	}

	/**
	 * Resolve update for given attribute
	 *
	 * @param provisioningAttribute
	 * @param provisioningOperation
	 * @param existsConnectorObject
	 * @param system
	 * @param updateConnectorObject
	 * @param fullAccountObject
	 * @param schemaAttributes
	 */
	private void processUpdateByAttribute(ProvisioningAttributeDto provisioningAttribute,
										  SysProvisioningOperationDto provisioningOperation,
										  IcConnectorObject existsConnectorObject,
										  SysSystemDto system,
										  IcConnectorObject updateConnectorObject,
										  Map<ProvisioningAttributeDto, Object> fullAccountObject,
										  List<SysSchemaAttributeDto> schemaAttributes,
										  ConnectorType connectorType) {
		Optional<SysSchemaAttributeDto> schemaAttributeOptional = schemaAttributes //
				.stream() //
				.filter(schemaAttribute -> provisioningAttribute.getSchemaAttributeName().equals(schemaAttribute.getName()))
				.findFirst();

		if (!schemaAttributeOptional.isPresent()) {
			throw new ProvisioningException(AccResultCode.PROVISIONING_SCHEMA_ATTRIBUTE_IS_FOUND,
					ImmutableMap.of("attribute", provisioningAttribute.getSchemaAttributeName()));
		}

		SysSchemaAttributeDto schemaAttribute = schemaAttributeOptional.get();
		if (schemaAttribute.isUpdateable()) {
			Object idmValue = fullAccountObject.get(provisioningAttribute);

			if (AttributeMappingStrategyType.CREATE == provisioningAttribute.getStrategyType()) {
				return;
			}

			if (provisioningAttribute.isSendOnlyIfNotNull() && this.isValueEmpty(idmValue)) {
				return;
			}
			if (schemaAttribute.isReturnedByDefault()) {
				if (AttributeMappingStrategyType.WRITE_IF_NULL == provisioningAttribute.getStrategyType()) {

					boolean existSetAttribute = fullAccountObject //
							.keySet() //
							.stream() //
							.anyMatch(provisioningAttributeKey -> provisioningAttributeKey.getSchemaAttributeName()
										.equals(schemaAttribute.getName())
										&& AttributeMappingStrategyType.SET == provisioningAttributeKey
										.getStrategyType());

					boolean existMergeAttribute = fullAccountObject //
							.keySet() //
							.stream() //
							.anyMatch(provisioningAttributeKey -> provisioningAttributeKey.getSchemaAttributeName()
										.equals(schemaAttribute.getName())
										&& AttributeMappingStrategyType.MERGE == provisioningAttributeKey
										.getStrategyType());

					boolean existAuthMergeAttribute = fullAccountObject //
							.keySet() //
							.stream() //
							.anyMatch(provisioningAttributeKey -> provisioningAttributeKey.getSchemaAttributeName()
										.equals(schemaAttribute.getName())
										&& AttributeMappingStrategyType.AUTHORITATIVE_MERGE == provisioningAttributeKey
										.getStrategyType());

					if (AttributeMappingStrategyType.WRITE_IF_NULL == provisioningAttribute.getStrategyType()) {
						List<IcAttribute> icAttributes = existsConnectorObject.getAttributes();
						//
						Optional<IcAttribute> icAttributeOptional = icAttributes.stream()
								.filter(ica -> schemaAttribute.getName().equals(ica.getName()))
								.findFirst();
						IcAttribute icAttribute = null;
						if (icAttributeOptional.isPresent()) {
							icAttribute = icAttributeOptional.get();
						}
						// We need do transform from resource first
						Object transformedConnectorValue = this.transformValueFromResource(
								provisioningAttribute.getTransformValueFromResourceScript(), schemaAttribute,
								icAttribute, icAttributes, system);

						if (transformedConnectorValue != null || existSetAttribute || existAuthMergeAttribute
								|| existMergeAttribute) {
							return;
						}
					}

				}
				Object resultValue = idmValue;
				if (AttributeMappingStrategyType.MERGE == provisioningAttribute.getStrategyType()) {
					List<Object> allConnectorValues = this.getAllConnectorValues(provisioningAttribute, existsConnectorObject);
					resultValue = resolveMergeValues(provisioningAttribute, idmValue, allConnectorValues,
							provisioningOperation);
				}

				// Update attribute on resource by given mapping
				// attribute and mapped value in entity
				IcAttribute updatedAttribute = updateAttribute(resultValue, schemaAttribute,
						existsConnectorObject, system, provisioningAttribute);
				// Add updated attribute to updateConnectorObject.
				connectorType.addUpdatedAttribute(schemaAttribute, updatedAttribute, updateConnectorObject, existsConnectorObject);
			} else {
				// create attribute without target system read - password etc.
				// filled values only
				if (fullAccountObject.get(provisioningAttribute) != null) {
					IcAttribute createdAttribute = createAttribute(schemaAttribute,
							fullAccountObject.get(provisioningAttribute));
					if (createdAttribute != null) {
						updateConnectorObject.getAttributes().add(createdAttribute);
					}
				}
			}
		}
	}

	private List<Object> getAllConnectorValues(ProvisioningAttributeDto provisioningAttribute,
											   IcConnectorObject existsConnectorObject) {

		if (existsConnectorObject == null) {
			return Lists.newArrayList();
		}
		IcAttribute attribute = existsConnectorObject.getAttributeByName(provisioningAttribute.getSchemaAttributeName());
		Object connectorValue = attribute != null
				? (attribute.isMultiValue() ? attribute.getValues() : attribute.getValue())
				: null;
		if (connectorValue != null && !(connectorValue instanceof List)) {
			connectorValue = Lists.newArrayList(connectorValue);
		}

		List<Object> connectorValues = Lists.newArrayList();

		if (connectorValue != null) {
			connectorValues.addAll((List<?>) connectorValue);
		}
		return connectorValues;
	}

	/**
	 * Returns merged values for given attribute
	 *
	 */
	@SuppressWarnings("unchecked")
	private Object resolveMergeValues(ProvisioningAttributeDto provisioningAttribute, Object idmValue,
			Object connectorValue, SysProvisioningOperationDto provisioningOperation) {

 		List<Object> resultValues = Lists.newArrayList();
		List<Object> connectorValues = Lists.newArrayList();
		List<Object> idmValues = Lists.newArrayList();

		if(connectorValue instanceof List) {
			resultValues.addAll((List<?>) connectorValue);
			connectorValues.addAll((List<?>) connectorValue);
		}else {
			if(connectorValue != null) {
				resultValues.add(connectorValue);
				connectorValues.add(connectorValue);
			}
		}
		if(idmValue instanceof List) {
			idmValues.addAll((List<?>) idmValue);
		}else {
			if(idmValue != null) {
				idmValues.add(idmValue);
			}
		}

		AccAccountDto account = DtoUtils.getEmbedded(provisioningOperation, SysProvisioningOperation_.account, AccAccountDto.class, null);
		if (account == null) {
			// account can be null in some cases (when provisioning operation is built directly in a builder)
			account = accountService.get(provisioningOperation.getAccount());
		}

		// Load definition of all controlled values in IdM for that attribute
		List<Serializable> controlledValues = attributeMappingService.getCachedControlledAndHistoricAttributeValues(
				provisioningOperation.getSystem(), provisioningOperation.getEntityType(),
				provisioningAttribute.getSchemaAttributeName(), account.getSystemMapping());
		List<Serializable> controlledValuesFlat = Lists.newArrayList();
		
		// Controlled value can be Collection, we need to create flat list for all values
		controlledValues.forEach(controlledValue -> {
			if(controlledValue instanceof Collection) {
				controlledValuesFlat.addAll((Collection<? extends Serializable>) controlledValue);
			}else {
				controlledValuesFlat.add(controlledValue);
			}
		});

		// Merge IdM values with connector values
		idmValues.forEach(value -> {
			if (!connectorValues.contains(value)) {
				resultValues.add(value);
			}
		});

		// Delete missing values
		// Search all deleted values (managed by IdM)
		List<?> deletedValues = controlledValuesFlat //
				.stream() //
				.filter(controlledValue -> !idmValues.contains(controlledValue))
				.collect(Collectors.toList());
		// Remove all deleted values (managed by IdM)
		resultValues.removeAll(deletedValues);

		return resultValues;
	}

	private boolean isValueEmpty(Object idmValue) {
		if (idmValue == null) {
			return true;
		}

		if (idmValue instanceof String && Strings.isNullOrEmpty((String) idmValue)) {
			return true;
		}

		if (idmValue instanceof List && (CollectionUtils.isEmpty((List<?>) idmValue)
				|| (((List<?>) idmValue).size() == 1 && ((List<?>) idmValue).get(0) == null))) {
			return true;
		}

		return false;
	}

	private SysSystemMappingDto getMapping(SysProvisioningOperationDto provisioningOperationDto) {
		UUID system = provisioningOperationDto.getSystem();
		String systemEntityUid = provisioningOperationDto.getSystemEntityUid();
		if (systemEntityUid == null || system == null) {
			throw new IllegalStateException(MessageFormat.format("Entity uid or system is null for provisioning operation [{0}]",
					provisioningOperationDto.getId()));
		}

		AccAccountDto accountDto = accountService.getAccount(systemEntityUid, system);
		if (accountDto == null) {
			// This is fallback, if account is not found, but system has some provisioning mapping, get first one
			List<SysSystemMappingDto> systemMappings = systemMappingService.findBySystemId(system,
					SystemOperationType.PROVISIONING, provisioningOperationDto.getEntityType());
			if (systemMappings == null || systemMappings.isEmpty()) {
				throw new IllegalStateException(MessageFormat.format(
						"System [{0}] does not have mapping, provisioning will not be executed. Add some mapping for entity type [{1}]",
						system, provisioningOperationDto.getEntityType()));
			}
			return systemMappings.get(0);
		}
		return DtoUtils.getEmbedded(accountDto, AccAccount_.systemMapping, SysSystemMappingDto.class);
	}

	/**
	 * Find list of {@link SysSchemaAttribute} by system and objectClass
	 * 
	 * @param objectClass
	 * @param system
	 * @return
	 */
	private List<SysSchemaAttributeDto> findSchemaAttributes(SysSystemDto system, SysSchemaObjectClassDto objectClass) {

		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());
		schemaAttributeFilter.setObjectClassId(objectClass.getId());
		return schemaAttributeService.find(schemaAttributeFilter, null).getContent();
	}

	/**
	 * Create IC attribute by schema attribute. IC attribute will be set with value
	 * obtained form given entity. This value will be transformed to system value
	 * first.
	 */
	private IcAttribute createAttribute(SysSchemaAttributeDto schemaAttribute, Object idmValue) {
		if (!schemaAttribute.isCreateable()) {
			return null;
		}
		return attributeMappingService.createIcAttribute(schemaAttribute, idmValue);
	}

	/**
	 * Update attribute on resource by given handling attribute and mapped value in
	 * entity.
	 *
	 */
	private IcAttribute updateAttribute(Object idmValue, SysSchemaAttributeDto schemaAttribute,
			IcConnectorObject existsConnectorObject, SysSystemDto system,
			ProvisioningAttributeDto provisioningAttributeDto) {
		List<IcAttribute> icAttributes = existsConnectorObject.getAttributes();
		//
		Optional<IcAttribute> icAttributeOptional = icAttributes.stream()
				.filter(ica -> schemaAttribute.getName().equals(ica.getName()))
				.findFirst();
		IcAttribute icAttribute = null;
		if (icAttributeOptional.isPresent()) {
			icAttribute = icAttributeOptional.get();
		}

		return updateAttributeValue(idmValue, schemaAttribute, icAttribute, icAttributes, system,
				provisioningAttributeDto.getTransformValueFromResourceScript(),
				provisioningAttributeDto.isSendAlways());
	}

	/**
	 * Check difference of attribute value on resource and in entity for given
	 * attribute. When is value changed, then add update of this attribute to map
	 * 
	 */
	private IcAttribute updateAttributeValue(Object idmValue, SysSchemaAttributeDto schemaAttribute,
			IcAttribute icAttribute, List<IcAttribute> icAttributes, SysSystemDto system,
			String transformValueFromResourceScript, boolean sendAlways) {
	    
		Object icValueTransformed = transformValueFromResource(transformValueFromResourceScript, schemaAttribute,
				icAttribute, icAttributes, system);
		if (sendAlways || (!provisioningService.isAttributeValueEquals(idmValue, icValueTransformed, schemaAttribute))) {
			// values is not equals
			// Or this attribute must be send every time (event if was not changed)
			return attributeMappingService.createIcAttribute(schemaAttribute, idmValue);
		}
		return null;
	}


	private Object transformValueFromResource(String transformValueFromResourceScript,
			SysSchemaAttributeDto schemaAttribute, IcAttribute icAttribute, List<IcAttribute> icAttributes,
			SysSystemDto system) {

		Object icValueTransformed = null;
		if (schemaAttribute.isMultivalued()) {
			// Multi value
			List<Object> icValues = icAttribute != null ? icAttribute.getValues() : null;
			icValueTransformed = attributeMappingService.transformValueFromResource(icValues,
					transformValueFromResourceScript, icAttributes, system);
		} else {
			// Single value
			Object icValue = icAttribute != null ? icAttribute.getValue() : null;
			icValueTransformed = attributeMappingService.transformValueFromResource(icValue,
					transformValueFromResourceScript, icAttributes, system);
		}

		return icValueTransformed;
	}

	@Override
	public int getOrder() {
		// before realization
		return -1000;
	}

	/**
	 * Generates password. Provisioning attributes must contains at least one attribute marked as password.
	 * And for system or CzechIdM must exists password policy for generated password.
	 *
	 * @param system
	 * @return
	 */
	private GuardedString generatePassword(SysSystemDto system) {
		final GuardedString generatedPassword;
		IdmPasswordPolicyDto passwordPolicyDto = null;
		if (system.getPasswordPolicyGenerate() != null) {
			passwordPolicyDto = passwordPolicyService.get(system.getPasswordPolicyGenerate());
		} else {
			passwordPolicyDto = passwordPolicyService.getDefaultPasswordPolicy(IdmPasswordPolicyType.GENERATE);
		}
		if (passwordPolicyDto != null) {
			generatedPassword = new GuardedString(passwordPolicyService.generatePassword(passwordPolicyDto));
		} else {
			generatedPassword = null;
		}
		return generatedPassword;
	}

	/**
	 * Transform given password with script in attribute mapping.
	 *
	 */
	private GuardedString transformPassword(SysProvisioningOperationDto provisioningOperation, SysSystemAttributeMappingDto passwordAttribute, GuardedString generatedPassword) {
		if (generatedPassword == null) {
			return null;
		}
		
		String uid = provisioningOperation.getSystemEntityUid();
		UUID entityIdentifier = provisioningOperation.getEntityIdentifier();

		AbstractDto abstractDto = null;
		if (entityIdentifier != null) {
			SystemEntityTypeRegistrable systemEntityType = systemEntityManager.getSystemEntityByCode(provisioningOperation.getEntityType());
			BaseDto dto = lookupService.lookupDto(systemEntityType.getEntityType(), entityIdentifier);
			if (dto instanceof AbstractDto) {
				abstractDto = (AbstractDto) dto;
			} else {
				LOG.warn("Dto with id [{}] hasn't type [{}]. Current type: [{}]. Into script will be send null as entity.", entityIdentifier, AbstractDto.class.getName(), dto == null ? null : dto.getClass());
			}
		} else {
			LOG.warn("Entity identifier for uid [{}] is null. Provisioning is probably called directly. Into password transfomartion will not be sent entity.", uid);
		}

		// transformed password
		Object transformValue = attributeMappingService.transformValueToResource(uid, generatedPassword, passwordAttribute, abstractDto);
		if (transformValue == null) {
			return null;
		} else if (transformValue instanceof GuardedString) {
			return (GuardedString) transformValue;
		}
		throw new ResultCodeException(AccResultCode.PROVISIONING_PASSWORD_TRANSFORMATION_FAILED,
				ImmutableMap.of("uid", uid, "mappedAttribute", passwordAttribute.getName()));	
	}
	
	/**
	 * Removes attributes from systemAttrs from system which are not in idmAccountAttrs i.e. are not mapped
	 * 
	 * @param idmAccountAttrs
	 * @param systemAttrs
	 * @return
	 */
	IcConnectorObject intersectAccountAndSystemAttrs(Map<ProvisioningAttributeDto, Object> idmAccountAttrs, IcConnectorObject systemAttrs) {
		if (idmAccountAttrs == null || systemAttrs == null) {
			return null;
		}
		Set<String> names = idmAccountAttrs.keySet()
				.stream()
				.map(ProvisioningAttributeDto::getSchemaName)
				.collect(Collectors.toSet());
		//
		List<IcAttribute> attributes = systemAttrs.getAttributes()
				.stream()
				.filter(attr -> names.contains(attr.getName()))
				.collect(Collectors.toList());
		//
		return new IcConnectorObjectImpl(systemAttrs.getUidValue(), systemAttrs.getObjectClass(), attributes);
	} 
}
