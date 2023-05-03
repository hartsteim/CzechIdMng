package eu.bcvsolutions.idm.acc.service.impl;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.domain.AttributeMapping;
import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.OperationResultType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationContext;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccRoleAccountDto;
import eu.bcvsolutions.idm.acc.dto.EntityAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncActionLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncItemLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncRoleConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.AccIdentityAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.AccRoleAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.EntityAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemAttributeMappingFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemEntityFilter;
import eu.bcvsolutions.idm.acc.entity.SysSchemaAttribute_;
import eu.bcvsolutions.idm.acc.entity.SysSyncRoleConfig_;
import eu.bcvsolutions.idm.acc.entity.SysSystemAttributeMapping_;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccRoleAccountService;
import eu.bcvsolutions.idm.acc.service.api.EntityAccountService;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningService;
import eu.bcvsolutions.idm.acc.service.api.SynchronizationEntityExecutor;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.domain.RoleType;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.OperationResultDto;
import eu.bcvsolutions.idm.core.api.dto.filter.CorrelationFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogueRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogue_;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleRequest_;
import eu.bcvsolutions.idm.core.model.event.RoleEvent;
import eu.bcvsolutions.idm.core.model.event.RoleEvent.RoleEventType;
import eu.bcvsolutions.idm.core.model.event.RoleRequestEvent;
import eu.bcvsolutions.idm.core.model.event.processor.role.RoleRequestApprovalProcessor;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;
import eu.bcvsolutions.idm.ic.api.IcConnectorInstance;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.api.IcObjectClass;
import eu.bcvsolutions.idm.ic.filter.api.IcFilter;
import eu.bcvsolutions.idm.ic.filter.impl.IcFilterBuilder;
import eu.bcvsolutions.idm.ic.impl.IcAttributeImpl;
import eu.bcvsolutions.idm.ic.impl.IcConnectorConfigurationImpl;
import eu.bcvsolutions.idm.ic.impl.IcObjectClassImpl;

/**
 * Synchronization of roles.
 * 
 * @author Vít Švanda
 * @since 11.1.0
 */
@Component
public class RoleSynchronizationExecutor extends AbstractSynchronizationExecutor<IdmRoleDto>
		implements SynchronizationEntityExecutor {

	public final static String ROLE_TYPE_FIELD = "roleType";
	public final static String ROLE_MEMBERSHIP_ID_FIELD = "roleMembershipId";
	public final static String ROLE_FORWARD_ACM_FIELD = "roleForwardAcm";
	public final static String ROLE_SKIP_VALUE_IF_EXCLUDED_FIELD = "roleSkipValueIfExcluded";
	public final static String ROLE_CATALOGUE_FIELD = "roleCatalogue";
	public final static String ROLE_MEMBERS_FIELD = "roleMembers";
	public static final String SYSTEM_ENTITY_TYPE = "ROLE";
	private static final String USER_UID_CACHE_KEY = "userUidCache";
	private static final String ROLE_REQUEST_CACHE_KEY = "roleRequestCache";
	
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private AccRoleAccountService roleAccountService;
	@Autowired
	private LookupService lookupService;
	@Autowired
	private SysRoleSystemService roleSystemService;
	@Autowired
	private SysRoleSystemAttributeService roleSystemAttributeService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private IdmRoleCatalogueService roleCatalogueService;
	@Autowired
	private IdmRoleCatalogueRoleService roleCatalogueRoleService;
	@Autowired
	private AccIdentityAccountService identityAccountService;
	@Autowired
	private IdmIdentityContractService identityContractService;
	@Autowired
	private IdmRoleRequestService roleRequestService;
	
	/**
	 * Call provisioning for given account
	 * 
	 * @param entity
	 * @param entityType
	 * @param logItem
	 */
	@Override
	protected void callProvisioningForEntity(IdmRoleDto entity, String entityType, SysSyncItemLogDto logItem) {
		addToItemLog(logItem,
				MessageFormat.format(
						"Call provisioning (process RoleEventType.SAVE) for role ({0}) with username ({1}).",
						entity.getId(), entity.getCode()));
		entityEventManager.process(new RoleEvent(RoleEventType.UPDATE, entity)).getContent();
	}


	/**
	 * Operation remove RoleAccount relations and linked roles
	 * 
	 * @param account
	 * @param removeRoleRole
	 * @param log
	 * @param logItem
	 * @param actionLogs
	 */
	protected void doUnlink(AccAccountDto account, boolean removeRoleRole, SysSyncLogDto log, SysSyncItemLogDto logItem,
			List<SysSyncActionLogDto> actionLogs) {

		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setAccountId(account.getId());
		List<AccRoleAccountDto> roleAccounts = roleAccountService
				.find(roleAccountFilter, null).getContent();
		if (roleAccounts.isEmpty()) {
			addToItemLog(logItem, "Warning! - Role account relation was not found!");
			initSyncActionLog(SynchronizationActionType.UPDATE_ENTITY, OperationResultType.WARNING, logItem, log,
					actionLogs);
			return;
		}
		addToItemLog(logItem, MessageFormat.format("Role-account relations to delete {0}", roleAccounts));

		roleAccounts.forEach(roleAccount -> {
			// We will remove role account, but without delete connected
			// account
			roleAccountService.delete(roleAccount, false);
			addToItemLog(logItem,
					MessageFormat.format(
							"Role-account relation deleted (without call delete provisioning) (username: {0}, id: {1})",
							roleAccount.getRole(), roleAccount.getId()));

		});
	}

	@Override
	protected SynchronizationContext validate(UUID synchronizationConfigId) {
		 syncContext = super.validate(synchronizationConfigId);

		SysSyncRoleConfigDto config = getConfig(syncContext);

		SysSystemAttributeMappingFilter attributeMappingFilter = new SysSystemAttributeMappingFilter();
		attributeMappingFilter.setSystemMappingId(config.getSystemMapping());
		attributeMappingFilter.setDisabledAttribute(Boolean.FALSE);

		// Check if exist mapping attribute for 'UID attribute'.
		attributeMappingFilter.setIsUid(Boolean.TRUE);
		SysSystemAttributeMappingDto attributeMappingDto = systemAttributeMappingService.find(attributeMappingFilter, null)
				.getContent()
				.stream()
				.findFirst()
				.orElse(null);
		
		if (attributeMappingDto == null) {
			throw new ResultCodeException(AccResultCode.SYNC_OF_ROLES_MAPPING_ATTRIBUTE_MISSING, ImmutableMap.of("attribute", "UID"));
		}
		if (config.isForwardAcmSwitch() && config.getForwardAcmMappingAttribute() == null) {
			throw new ResultCodeException(AccResultCode.SYNC_OF_ROLES_MAPPING_ATTRIBUTE_MISSING, ImmutableMap.of("attribute", ROLE_FORWARD_ACM_FIELD));
		}
		if (config.isSkipValueIfExcludedSwitch() && config.getSkipValueIfExcludedMappingAttribute() == null) {
			throw new ResultCodeException(AccResultCode.SYNC_OF_ROLES_MAPPING_ATTRIBUTE_MISSING, ImmutableMap.of("attribute", ROLE_SKIP_VALUE_IF_EXCLUDED_FIELD));
		}
		if (config.isMembershipSwitch()) {
			if ( config.getRoleIdentifiersMappingAttribute() == null){
				throw new ResultCodeException(AccResultCode.SYNC_OF_ROLES_MAPPING_ATTRIBUTE_MISSING, ImmutableMap.of("attribute", ROLE_MEMBERSHIP_ID_FIELD));
			}
			if (config.getMemberSystemMapping() == null){
				throw new ResultCodeException(AccResultCode.SYNC_OF_ROLES_MAPPING_ATTRIBUTE_MISSING, ImmutableMap.of("attribute", "System mapping from an identity system"));
			}
			if (config.getMemberOfAttribute() == null){
				throw new ResultCodeException(AccResultCode.SYNC_OF_ROLES_MAPPING_ATTRIBUTE_MISSING, ImmutableMap.of("attribute", "Member of from an identity system"));
			}
		}
		if (config.isAssignRoleSwitch()){
			if (config.getRoleMembersMappingAttribute() == null) {
				throw new ResultCodeException(AccResultCode.SYNC_OF_ROLES_MAPPING_ATTRIBUTE_MISSING, ImmutableMap.of("attribute", ROLE_MEMBERS_FIELD));
			}
			if (config.getMemberIdentifierAttribute() == null){
				throw new ResultCodeException(AccResultCode.SYNC_OF_ROLES_MAPPING_ATTRIBUTE_MISSING, ImmutableMap.of("attribute", "Identity identifier from an identity system"));
			}
			
		}
		if (config.isAssignCatalogueSwitch() && config.getAssignCatalogueMappingAttribute() == null) {
			throw new ResultCodeException(AccResultCode.SYNC_OF_ROLES_MAPPING_ATTRIBUTE_MISSING, ImmutableMap.of("attribute", ROLE_CATALOGUE_FIELD));
		}

		return syncContext;
	}

	@Override
	protected Object getValueByMappedAttribute(AttributeMapping attribute, List<IcAttribute> icAttributes, SynchronizationContext context) {
		Object transformedValue =  super.getValueByMappedAttribute(attribute, icAttributes, context);
		// Transform role type enumeration from string
		if (transformedValue instanceof String && attribute.isEntityAttribute() && ROLE_TYPE_FIELD.equals(attribute.getIdmPropertyName())) {
			transformedValue = RoleType.valueOf((String) transformedValue);
		}
		return transformedValue;
	}

	@Override
	protected void setEntityValue(String uid, IdmRoleDto dto, SynchronizationContext context, SysSystemAttributeMappingDto attribute, String attributeProperty, Object transformedValue) {
		if (attribute.isEntityAttribute() 
				&& ROLE_MEMBERS_FIELD.equals(attribute.getIdmPropertyName()
		)){
			// Skip members, different sync is not supported here.
			return;
		}
		
		if (attribute.isEntityAttribute() && ROLE_MEMBERSHIP_ID_FIELD.equals(attribute.getIdmPropertyName())) {
			checkMembershipChange(dto, context, attributeProperty, transformedValue);
			return;
		}
		if (attribute.isEntityAttribute() && ROLE_FORWARD_ACM_FIELD.equals(attribute.getIdmPropertyName())) {
			checkForwardAcmChange(dto, context, attributeProperty, transformedValue);
			return;
		}
		if (attribute.isEntityAttribute() && ROLE_SKIP_VALUE_IF_EXCLUDED_FIELD.equals(attribute.getIdmPropertyName())) {
			checkSkipValueIfExcludedChange(dto, context, attributeProperty, transformedValue);
			return;
		}
		if (attribute.isEntityAttribute() && ROLE_CATALOGUE_FIELD.equals(attribute.getIdmPropertyName())) {
			checkCatalogChange(dto, context, attributeProperty, transformedValue);
			return;
		}
		
		
		super.setEntityValue(uid, dto, context, attribute, attributeProperty, transformedValue);
	}

	/**
	 * Save entity
	 * @param entity
	 * @param skipProvisioning
	 * @return
	 */
	@Override
	protected IdmRoleDto save(IdmRoleDto entity, boolean skipProvisioning, SynchronizationContext context) {
		boolean isNew = roleService.isNew(entity);
		EntityEvent<IdmRoleDto> event = new RoleEvent(
				isNew ? RoleEventType.CREATE : RoleEventType.UPDATE, 
				entity, 
				ImmutableMap.of(ProvisioningService.SKIP_PROVISIONING, skipProvisioning));

		IdmRoleDto roleDto = roleService.publish(event).getContent();
		SysSyncRoleConfigDto config = this.getConfig(context);
		SysSyncItemLogDto logItem = context.getLogItem();
		IcConnectorObject connectorObject = context.getIcObject();

		// Resolve 'Role catalogue'.
		if (roleDto != null && config.isAssignCatalogueSwitch()) {
			resolveRoleCatalogue(isNew, context, roleDto, logItem, connectorObject);
		}
		
		if (roleDto != null && (config.isMembershipSwitch() || config.isForwardAcmSwitch() || config.isSkipValueIfExcludedSwitch() || config.isAssignRoleSwitch())) {

			Assert.notNull(connectorObject, "Connector object cannot be null!");
			SysSystemAttributeMappingDto memberOfAttributeDto = lookupService.lookupEmbeddedDto(config, SysSyncRoleConfig_.memberOfAttribute);
			Assert.notNull(memberOfAttributeDto, "Member attribute cannot be null!");
			SysSchemaAttributeDto schemaAttributeDto = lookupService.lookupEmbeddedDto(memberOfAttributeDto, SysSystemAttributeMapping_.schemaAttribute);
			SysSchemaObjectClassDto schemaObjectClassDto = lookupService.lookupEmbeddedDto(schemaAttributeDto, SysSchemaAttribute_.objectClass);
			Assert.notNull(schemaObjectClassDto, "Schema cannot be null!");
		
			// Resolve role membership.
			if(config.isMembershipSwitch()) {
				boolean couldContinue = resolveMembership(isNew, context, roleDto, config, logItem, connectorObject, memberOfAttributeDto, schemaObjectClassDto);
				if (!couldContinue) {
					return roleDto;
				}
			}

			// Resolve 'Forward ACM'.
			if(config.isForwardAcmSwitch()) {
				resolveForwardAcm(isNew, context, roleDto, logItem, connectorObject, memberOfAttributeDto, schemaObjectClassDto);
			}
			
			// Resolve 'Skip value if is contract excluded'.
			if(config.isSkipValueIfExcludedSwitch()) {
				resolveSkipValueIfExcluded(isNew, context, roleDto, logItem, connectorObject, memberOfAttributeDto, schemaObjectClassDto);
			}
			
			
			// Resolve 'Assign the role to members'.
			if (config.isAssignRoleSwitch()) {
				boolean canContinue = resolveAssignRole(isNew, context, roleDto, config, logItem, connectorObject, memberOfAttributeDto, schemaObjectClassDto);
				if (!canContinue) {
					return roleDto;
				}
			}
		}
		
		return roleDto;
	}

	private boolean resolveAssignRole(boolean isNew,
									  SynchronizationContext context,
									  IdmRoleDto roleDto,
									  SysSyncRoleConfigDto config,
									  SysSyncItemLogDto logItem,
									  IcConnectorObject connectorObject,
									  SysSystemAttributeMappingDto memberOfAttributeDto,
									  SysSchemaObjectClassDto schemaObjectClassDto) {
		// Find attribute for get members (DNs)
		SysSystemAttributeMappingDto roleMembersAttributeDto = context.getMappedAttributes().stream()
				.filter(attribute -> !attribute.isDisabledAttribute() && attribute.isEntityAttribute() && ROLE_MEMBERS_FIELD.equals(attribute.getIdmPropertyName()))
				.findFirst()
				.orElse(null);
		Assert.notNull(roleMembersAttributeDto, "Mapped attribute with role's members was not found. Please create it!");

		if (!isNew && AttributeMappingStrategyType.CREATE == roleMembersAttributeDto.getStrategyType()) {
			addToItemLog(logItem, "The attribute with role's members has strategy set to 'Set only for new entity'. Role isn't new, so resolving controlling an assignment of roles to users by the external system will be skipped for this role.");
		} else {
			addToItemLog(logItem, "Controlling an assignment of roles to users by the external system is activated.");

			Object membersObj = this.getValueByMappedAttribute(roleMembersAttributeDto, connectorObject.getAttributes(), context);
			if (membersObj == null) {
				membersObj = Lists.newArrayList();
			}
			if (membersObj instanceof String) {
				membersObj = Lists.newArrayList(membersObj);
			}
			Assert.isInstanceOf(List.class, membersObj, "The value from attribute with role's members must be List of Strings!");

			@SuppressWarnings("unchecked")
			List<String> members = (List<String>) membersObj;

			SysRoleSystemDto roleSystemDto = findRoleSystemDto(roleDto, memberOfAttributeDto, schemaObjectClassDto);
			if (roleSystemDto == null) {
				addToItemLog(logItem, "Relation between this role and system was not found. Assigning of role to users will be skip for this role.");
				return false;
			}
			SysRoleSystemAttributeDto memberAttribute = findMemberAttribute(memberOfAttributeDto, schemaObjectClassDto, roleSystemDto);
			if (memberAttribute == null) {
				addToItemLog(logItem, "The member attribute between this role and system was not found. Assigning of role to users will be skip for this role.");
				return false;
			}
			// Find identities with this role.
			IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();
			identityRoleFilter.setRoleId(roleDto.getId());
			List<IdmIdentityRoleDto> existsIdentityRoleDtos = identityRoleService.find(identityRoleFilter, null).getContent();
			
			// Get cache with users (DN vs UID).
			Map<String, String> usersUidCache = getUserUidCache();

			SysSchemaAttributeDto memberIdentifierAttribute = lookupService.lookupEmbeddedDto(config, SysSyncRoleConfig_.memberIdentifierAttribute);
			Assert.notNull(memberIdentifierAttribute, "User identifier attribute cannot be null!");

			Set<String> membersUid = Sets.newHashSet();
			Set<UUID> membersContractIds = Sets.newHashSet();
			
			// Call user system for every member (if isn't already in the cache).
			SysSystemDto userSystemDto = systemService.get(roleSystemDto.getSystem());
				IcConnectorConfiguration icConfig = systemService.getConnectorConfiguration(userSystemDto);
			IcConnectorInstance connectorInstance = systemService.getConnectorInstance(userSystemDto);
			IcObjectClass objectClass = new IcObjectClassImpl(schemaObjectClassDto.getObjectClassName());
			if (icConfig instanceof IcConnectorConfigurationImpl) {
				// Enable pooling - a performance reason.
				IcConnectorConfigurationImpl icConfigImpl = (IcConnectorConfigurationImpl) icConfig;
				icConfigImpl.setConnectorPoolingSupported(true);
			}
			final int[] count = {0};
			for (String member : members) {
				if (!transformDnToUid(config, usersUidCache, memberIdentifierAttribute, membersUid, icConfig, connectorInstance, objectClass, count, member)){
					return false;
				}
			}
			
			count[0] = 0;
			membersUid.forEach(uid -> assignMissingIdentityRoles(roleDto, config, logItem, existsIdentityRoleDtos, membersContractIds, userSystemDto, count, uid, context));

			if (!checkForCancelAndFlush(config)) {
				return false;
			}

			// Remove redundant identity roles. 
			List<IdmIdentityRoleDto> redundantIdentityRoles = existsIdentityRoleDtos.stream()
					.filter(existsIdentityRole -> !membersContractIds.contains(existsIdentityRole.getIdentityContract()))
					.collect(Collectors.toList());

			count[0] = 0;
			redundantIdentityRoles.forEach(redundantIdentityRole -> removeRedundantIdentityRoles(roleDto, config, logItem, count, redundantIdentityRole));
		}
		return true;
	}

	@Override
	protected void syncEnd(SysSyncLogDto log, SynchronizationContext syncContext) {
		// Execute all role-requests for this sync.
		getRoleRequestCache().values().forEach(roleRequestId -> {
			IdmRoleRequestDto roleRequestDto = roleRequestService.get(roleRequestId);
			if (roleRequestDto == null) {
				log.addToLog(MessageFormat.format("Role-request with ID [{1}] was not found with result!!!", roleRequestId));
				return;
			}
			Map<String, Serializable> properties = new HashMap<>();
			properties.put(RoleRequestApprovalProcessor.CHECK_RIGHT_PROPERTY, Boolean.TRUE);
			RoleRequestEvent event = new RoleRequestEvent(RoleRequestEvent.RoleRequestEventType.EXCECUTE, roleRequestDto, properties);
			// Async start of request.
			roleRequestDto = roleRequestService.startRequestInternal(event);
			IdmIdentityDto applicant = DtoUtils.getEmbedded(roleRequestDto, IdmRoleRequest_.applicant, IdmIdentityDto.class);

			log.addToLog(MessageFormat.format("Role-request with ID [{1}] for applicant [{0}] was executed with result [{2}].", applicant.getUsername(), roleRequestDto.getId(), roleRequestDto.getState().name()));
		});
		super.syncEnd(log, syncContext);
	}

	/**
	 * Check if role catalog is different then value form transformation.
	 */
	private void checkCatalogChange(IdmRoleDto dto, SynchronizationContext context, String attributeProperty, Object transformedValue) {
		if (!context.isEntityDifferent() && dto.getId() != null && getConfig(context).isAssignCatalogueSwitch()) {
			// Check if catalog should be modified (differential sync).
			// If exist at least one role-catalogue in transformation without ID, then a change (create) will be made.
			List<IdmRoleCatalogueDto> roleCataloguesFromTransformation = getRoleCatalogueFromValue(transformedValue);
			boolean existCatalogueWithoutId = roleCataloguesFromTransformation
					.stream()
					.anyMatch(roleCatalogue -> roleCatalogue.getId() == null);
			if (existCatalogueWithoutId) {
				setDifferentChange(context, attributeProperty);
				return;
			}

			List<UUID> roleCatalogsFromTransformationUUIDs = roleCataloguesFromTransformation.stream()
					.map(AbstractDto::getId)
					.collect(Collectors.toList());
			List<UUID> currentUseRoleCatalogueRoleIds = Lists.newArrayListWithExpectedSize(10);
			for (UUID roleCatalogueId : roleCatalogsFromTransformationUUIDs) {
				Assert.notNull(roleCatalogueId, "Role catalogue ID cannot be null here!");
				IdmRoleCatalogueRoleFilter roleCatalogueRoleFilter = new IdmRoleCatalogueRoleFilter();
				roleCatalogueRoleFilter.setRoleCatalogueId(roleCatalogueId);
				roleCatalogueRoleFilter.setRoleId(dto.getId());
				IdmRoleCatalogueRoleDto roleCatalogueRoleDto = roleCatalogueRoleService.find(roleCatalogueRoleFilter, null).getContent()
						.stream()
						.findFirst()
						.orElse(null);
				if (roleCatalogueRoleDto == null) {
					// The role is not assigned to the catalog yet. Change will be made.
					setDifferentChange(context, attributeProperty);
					return;
				}
				currentUseRoleCatalogueRoleIds.add(roleCatalogueRoleDto.getId());
			}

			// If exist at least one redundant role-catalogue (and remove of redundant relations are allowed), then a change (delete) will be made.
			if (getConfig(context).isRemoveCatalogueRoleSwitch()) {
				List<IdmRoleCatalogueRoleDto> redundantRoleCatalogs = findRedundantRoleCatalogs(dto, currentUseRoleCatalogueRoleIds, getConfig(context));
				if (redundantRoleCatalogs.size() > 0) {
					setDifferentChange(context, attributeProperty);
				}
			}
		}
	}
	
	/**
	 * Check if forward ACM value is different then value form a transformation.
	 */
	private void checkForwardAcmChange(IdmRoleDto dto, SynchronizationContext context, String attributeProperty, Object transformedValue) {
		if (!context.isEntityDifferent() && dto.getId() != null && getConfig(context).isForwardAcmSwitch()) {
			// Check if forward ACM value should be modified (differential sync).
			SysSystemAttributeMappingDto memberOfAttributeDto = lookupService.lookupEmbeddedDto(getConfig(context), SysSyncRoleConfig_.memberOfAttribute);
			Assert.notNull(memberOfAttributeDto, "Member attribute cannot be null!");
			SysSchemaAttributeDto schemaAttributeDto = lookupService.lookupEmbeddedDto(memberOfAttributeDto, SysSystemAttributeMapping_.schemaAttribute);
			SysSchemaObjectClassDto schemaObjectClassDto = lookupService.lookupEmbeddedDto(schemaAttributeDto, SysSchemaAttribute_.objectClass);
			Assert.notNull(schemaObjectClassDto, "Schema cannot be null!");
			
			boolean forwardAcmFromValue = getForwardAcmFromValue(transformedValue);
			SysRoleSystemDto roleSystemDto = findRoleSystemDto(dto, memberOfAttributeDto, schemaObjectClassDto);
			if (roleSystemDto == null || roleSystemDto.isForwardAccountManagemen() != forwardAcmFromValue) {
				setDifferentChange(context, attributeProperty);
			}
		}
	}

	/**
	 * Check if role membership value is different.
	 */
	private void checkMembershipChange(IdmRoleDto dto, SynchronizationContext context, String attributeProperty, Object transformedValue) {
		if (!context.isEntityDifferent() && dto.getId() != null && getConfig(context).isMembershipSwitch()) {
			// Check if forward ACM value should be modified (differential sync).
			SysSystemAttributeMappingDto memberOfAttributeDto = lookupService.lookupEmbeddedDto(getConfig(context), SysSyncRoleConfig_.memberOfAttribute);
			Assert.notNull(memberOfAttributeDto, "Member attribute cannot be null!");
			SysSchemaAttributeDto schemaAttributeDto = lookupService.lookupEmbeddedDto(memberOfAttributeDto, SysSystemAttributeMapping_.schemaAttribute);
			SysSchemaObjectClassDto schemaObjectClassDto = lookupService.lookupEmbeddedDto(schemaAttributeDto, SysSchemaAttribute_.objectClass);
			Assert.notNull(schemaObjectClassDto, "Schema cannot be null!");

			String roleIdentifier = null;
			if (transformedValue != null) {
				Assert.isInstanceOf(String.class, transformedValue, "Role identifier must be String!");
				roleIdentifier = (String) transformedValue;
			} else {
				// Identifier form transformation is null -> We will delete role-system relations.
				setDifferentChange(context, attributeProperty);
				return;
			}
			SysRoleSystemDto roleSystemDto = findRoleSystemDto(dto, memberOfAttributeDto, schemaObjectClassDto);
			if (roleSystemDto != null) {

				SysRoleSystemAttributeDto roleMemberOfAttributeDto = findMemberAttribute(memberOfAttributeDto, schemaObjectClassDto, roleSystemDto);
				if (roleMemberOfAttributeDto == null) {
					// Role attribute by mapping system attribute will be created.
					setDifferentChange(context, attributeProperty);
					return;
				}
				String membershipTransformationScript = getMembershipTransformationScript(roleIdentifier);
				if (!roleMemberOfAttributeDto.getTransformScript().equals(membershipTransformationScript)) {
					// Transformation script is different and will be updated.
					setDifferentChange(context, attributeProperty);
				}
			} else {
				// Role system will be created.
				setDifferentChange(context, attributeProperty);
			}
		}
	}
	
	/**
	 * Check if 'SkipIfValueExcluded' value is different then value form a transformation.
	 */
	private void checkSkipValueIfExcludedChange(IdmRoleDto dto, SynchronizationContext context, String attributeProperty, Object transformedValue) {
		if (!context.isEntityDifferent() && dto.getId() != null  && getConfig(context).isSkipValueIfExcludedSwitch()) {
			// Check if 'SkipIfValueExcluded' value should be modified (differential sync).
			SysSystemAttributeMappingDto memberOfAttributeDto = lookupService.lookupEmbeddedDto(getConfig(context), SysSyncRoleConfig_.memberOfAttribute);
			Assert.notNull(memberOfAttributeDto, "Member attribute cannot be null!");
			SysSchemaAttributeDto schemaAttributeDto = lookupService.lookupEmbeddedDto(memberOfAttributeDto, SysSystemAttributeMapping_.schemaAttribute);
			SysSchemaObjectClassDto schemaObjectClassDto = lookupService.lookupEmbeddedDto(schemaAttributeDto, SysSchemaAttribute_.objectClass);
			Assert.notNull(schemaObjectClassDto, "Schema cannot be null!");
			
			boolean skipIfValueExcludedFromValue = getSkipIfValueExcludedFromValue(transformedValue);
			SysRoleSystemDto roleSystemDto = findRoleSystemDto(dto, memberOfAttributeDto, schemaObjectClassDto);
			if (roleSystemDto == null) {
				setDifferentChange(context, attributeProperty);
				return;
			}
			// Find member attribute.
			SysRoleSystemAttributeDto memberAttribute = findMemberAttribute(memberOfAttributeDto, schemaObjectClassDto, roleSystemDto);
			if (memberAttribute == null || memberAttribute.isSkipValueIfExcluded() != skipIfValueExcludedFromValue) {
				setDifferentChange(context, attributeProperty);
			}
		}
	}

	private void setDifferentChange(SynchronizationContext context, String attributeProperty) {
		context.setIsEntityDifferent(true);
		addToItemLog(context.getLogItem(),
				MessageFormat.format(
						"Value of entity attribute [{0}] was changed. First change was detected -> entity in IdM will be updated.",
						attributeProperty));
	}

	/**
	 * Transform given user identifier (DN) to UID, by call user system.
	 */
	private boolean transformDnToUid(SysSyncRoleConfigDto config,
									 Map<String, String> usersUidCache,
									 SysSchemaAttributeDto memberIdentifierAttribute,
									 Set<String> membersUid,
									 IcConnectorConfiguration icConfig,
									 IcConnectorInstance connectorInstance,
									 IcObjectClass objectClass,
									 int[] count,
									 String member) {

		// On every 20th item will be hibernate flushed and check if sync was not ended.
		if (count[0] % 20 == 0 && count[0] > 0) {
			if (!checkForCancelAndFlush(config)) {
				return false;
			}
		}
		count[0]++;

		if (usersUidCache.containsKey(member)) {
			membersUid.add(usersUidCache.get(member));
			return true;
		}

		IcAttributeImpl dnFilterAttribute = new IcAttributeImpl(memberIdentifierAttribute.getName(), member);
		IcFilter icFilter = IcFilterBuilder.equalTo(dnFilterAttribute);

		connectorFacade.search(
				connectorInstance,
				icConfig,
				objectClass,
				icFilter,
				connectorObject -> {
					if (connectorObject != null) {
						String uidValue = connectorObject.getUidValue();
						membersUid.add(uidValue);
					}
					return false;
				});
		return true;
	}

	/**
	 *  Remove redundant identity roles.
	 */
	private void removeRedundantIdentityRoles(IdmRoleDto roleDto, SysSyncRoleConfigDto config, SysSyncItemLogDto logItem, int[] count, IdmIdentityRoleDto redundantIdentityRole) {
		// On every 20th item will be hibernate flushed and check if sync was not ended.
		if (count[0] % 20 == 0 && count[0] > 0) {
			if (!checkForCancelAndFlush(config)) {
				return;
			}
		}
		count[0]++;

		IdmIdentityContractDto identityContractDto = DtoUtils.getEmbedded(redundantIdentityRole, IdmIdentityRole_.identityContract, IdmIdentityContractDto.class);
		IdmIdentityDto identityDto = DtoUtils.getEmbedded(identityContractDto, IdmIdentityContract_.identity, IdmIdentityDto.class);

		if (!config.isAssignRoleRemoveSwitch()) {
			addToItemLog(logItem, MessageFormat.format("!!Role is assigned for username [{0}] and contract [{1}], but isn't member of role. Role will be not removed from a user, because removing of redundant roles is not allowed in this sync now!", identityDto.getUsername(), identityContractDto.getId()));
			return;
		}
		if (redundantIdentityRole.getAutomaticRole() != null) {
			addToItemLog(logItem, MessageFormat.format("!!Role is assigned for username [{0}] and contract [{1}], but isn't member of role. Assigned role will be not removed, because the role was assigned by automatic role!", identityDto.getUsername(), identityContractDto.getId()));
			return;
		}
		if (redundantIdentityRole.getDirectRole() != null) {
			addToItemLog(logItem, MessageFormat.format("!!Role is assigned for username [{0}] and contract [{1}], but isn't member of role. Assigned role will be not removed, because the role was assigned by business role!", identityDto.getUsername(), identityContractDto.getId()));
			return;
		}
		addToItemLog(logItem, MessageFormat.format("Role is assigned for username [{0}] and contract [{1}], but isn't member of role. Assigned role will be removed.", identityDto.getUsername(), identityContractDto.getId()));
		// Get cache with role-requests by identity-contract.
		Map<UUID, UUID> roleRequestCache = getRoleRequestCache();

		// Get role-request for the primary contract from a cache. If no request is present, then create one.
		initRoleRequest(identityContractDto, roleRequestCache, config);
		UUID roleRequestId = roleRequestCache.get(identityContractDto.getId());
		IdmRoleRequestDto mockRoleRequest = new IdmRoleRequestDto();
		mockRoleRequest.setId(roleRequestId);
		// Create a concept for remove  an assigned role.
		roleRequestService.createConcept(mockRoleRequest, identityContractDto, redundantIdentityRole.getId(),
				roleDto.getId(), ConceptRoleRequestOperation.REMOVE);
	}

	/**
	 * Assign missing identity roles.
	 */
	private void assignMissingIdentityRoles(IdmRoleDto roleDto,
											SysSyncRoleConfigDto config,
											SysSyncItemLogDto logItem,
											List<IdmIdentityRoleDto> existsIdentityRoleDtos,
											Set<UUID> membersContractIds,
											SysSystemDto userSystemDto,
											int[] count,
											String uid,
											SynchronizationContext context) {
		// On every 20th item will be hibernate flushed and check if sync was not ended.
		if (count[0] % 20 == 0 && count[0] > 0) {
			if (!checkForCancelAndFlush(config)) {
				return;
			}
		}
		count[0]++;

		// Need to find account using SysSystemEntityDto uid, because uid of AccAccountDto can be different.
		SysSystemEntityFilter entityFilter = new SysSystemEntityFilter();
		entityFilter.setEntityType(IdentitySynchronizationExecutor.SYSTEM_ENTITY_TYPE);
		entityFilter.setSystemId(userSystemDto.getId());
		entityFilter.setUid(uid);
		SysSystemEntityDto systemEntity = systemEntityService.find(entityFilter, null)
				.stream()
				.findFirst()
				.orElse(null);
		if (systemEntity == null) {
			return;
		}

		AccAccountFilter accAccountFilter = new AccAccountFilter();
		accAccountFilter.setSystemEntityId(systemEntity.getId());
		final UUID accAccountId = accountService.findIds(accAccountFilter, null)
				.stream()
				.findFirst()
				.orElse(null);
		if (accAccountId == null) {
			return;
		}

		AccIdentityAccountFilter identityAccountWithoutRelationFilter = new AccIdentityAccountFilter();
		identityAccountWithoutRelationFilter.setAccountId(accAccountId);
		AccIdentityAccountDto identityAccountDto = identityAccountService.find(identityAccountWithoutRelationFilter, null).getContent()
				.stream()
				.findFirst()
				.orElse(null);
		if (identityAccountDto == null)  {
			return;
		}
		UUID identityId = identityAccountDto.getIdentity();

		IdmIdentityContractDto primeContract = identityContractService.getPrimeContract(identityId);
		if (primeContract == null) {
			addToItemLog(logItem, MessageFormat.format("!!Role was not assigned to the user [{0}], because primary contract was not found!!", uid));
			initSyncActionLog(SynchronizationActionType.UPDATE_ENTITY, OperationResultType.WARNING, logItem, context.getLog(),
					context.getActionLogs());
			return;
		}

		membersContractIds.add(primeContract.getId());

		IdmIdentityRoleDto existIdentityRoleDto = existsIdentityRoleDtos.stream()
				.filter(identityRole -> primeContract.getId().equals(identityRole.getIdentityContract()))
				.findFirst()
				.orElse(null);
		if (existIdentityRoleDto != null){
			// Identity already has the role.
			return;
		}

		addToItemLog(logItem, MessageFormat.format("Role is not assigned for user [{0}] and contract [{1}]. Role request for add role will be created.", uid, primeContract.getId()));

		// Get cache with role-requests by identity-contract.
		Map<UUID, UUID> roleRequestCache = getRoleRequestCache();

		// Get role-request for the primary contract from a cache. If no request is present, then create one.
		initRoleRequest(primeContract, roleRequestCache, config);

		UUID roleRequestId = roleRequestCache.get(primeContract.getId());
		IdmRoleRequestDto mockRoleRequest = new IdmRoleRequestDto();
		mockRoleRequest.setId(roleRequestId);
		// Create a concept for assign a role to primary contract.
		roleRequestService.createConceptWithoutValidity(mockRoleRequest, primeContract, null, roleDto.getId(),
				ConceptRoleRequestOperation.ADD);
	}

	/**
	 * Returns false if sync was ended.
	 * Provides flush of Hibernate in batch - performance improving.
	 */
	private boolean checkForCancelAndFlush(SysSyncRoleConfigDto config) {
		// Call hard hibernate session flush and clear
		if (getHibernateSession().isOpen()) {
			getHibernateSession().flush();
			getHibernateSession().clear();
		}
		// Cancel if sync ends.
		return synchronizationConfigService.isRunning(config);
	}

	/**
	 * Get role-request from a cache or create new one if no exist in a cache.
	 */
	private void initRoleRequest(IdmIdentityContractDto primeContract, Map<UUID, UUID> roleRequestCache, SysSyncRoleConfigDto config) {
		if (!roleRequestCache.containsKey(primeContract.getId())) {
			IdmRoleRequestDto roleRequest = roleRequestService.createRequest(primeContract);
			roleRequest.setState(RoleRequestState.CONCEPT);
			OperationResultDto systemResult = new OperationResultDto.Builder(OperationState.NOT_EXECUTED)
					.setModel(new DefaultResultModel(AccResultCode.SYNC_OF_ROLES_COMMON_ROLE_REQUEST, ImmutableMap.of("system", syncContext.getSystem().getCode())))
					.build();
			roleRequest.setSystemState(systemResult);
			roleRequest.addToLog(MessageFormat.format("Role-request created from ROLE sync with ID [{0}] on the system [{1}].", config.getId(), syncContext.getSystem().getCode()));
			roleRequest = roleRequestService.save(roleRequest);
			roleRequestCache.put(primeContract.getId(), roleRequest.getId());
		}
	}

	/**
	 * Get the cache with user-uid.
	 */
	@SuppressWarnings("unchecked")
	private Map<String, String> getUserUidCache() {
		Object userUidCacheObj = syncContext.getProperty(USER_UID_CACHE_KEY);
		if (!(userUidCacheObj instanceof Map)) {
			userUidCacheObj = new HashMap<String, String>();
			syncContext.addProperty(USER_UID_CACHE_KEY, userUidCacheObj);
		}
		return (Map<String, String>) userUidCacheObj;
	}

	/**
	 * Get the cache with role-requests.
	 */
	@SuppressWarnings("unchecked")
	private Map<UUID, UUID> getRoleRequestCache() {
		Object roleRequestCacheObj = syncContext.getProperty(ROLE_REQUEST_CACHE_KEY);
		if (!(roleRequestCacheObj instanceof Map)) {
			roleRequestCacheObj = new HashMap<UUID, UUID>();
			syncContext.addProperty(ROLE_REQUEST_CACHE_KEY, roleRequestCacheObj);
		}
		return (Map<UUID, UUID>) roleRequestCacheObj;
	}

	/**
	 * Resolve 'Skip value if is contract excluded'.
	 */
	private void resolveSkipValueIfExcluded(boolean isNew, SynchronizationContext context, IdmRoleDto roleDto, SysSyncItemLogDto logItem, IcConnectorObject connectorObject, SysSystemAttributeMappingDto memberOfAttributeDto, SysSchemaObjectClassDto schemaObjectClassDto) {
		SysSystemAttributeMappingDto skipValueIfExcludeAttributeDto = context.getMappedAttributes().stream()
				.filter(attribute -> !attribute.isDisabledAttribute() && attribute.isEntityAttribute() && ROLE_SKIP_VALUE_IF_EXCLUDED_FIELD.equals(attribute.getIdmPropertyName()))
				.findFirst()
				.orElse(null);

		Assert.notNull(skipValueIfExcludeAttributeDto, "Skip value if is contract excluded attribute cannot be null!");

		if (!isNew && AttributeMappingStrategyType.CREATE == skipValueIfExcludeAttributeDto.getStrategyType()) {
			addToItemLog(logItem, "The attribute for 'Skip value if is contract excluded' has strategy set to 'Set only for new entity'. Role isn't new, so resolving of forward ACM will be skipped for this role.");
		} else {
			addToItemLog(logItem, MessageFormat.format("Resolving of 'Skip value if is contract excluded' is activated for this role [{0}].", roleDto.getCode()));

			// Get value from attribute transformation.
			Object skipIfValueExcludedObj = this.getValueByMappedAttribute(skipValueIfExcludeAttributeDto, connectorObject.getAttributes(), context);
			boolean skipIfValueExcluded = getSkipIfValueExcludedFromValue(skipIfValueExcludedObj);

			// Save member attribute with new skip value if excluded value.
			SysRoleSystemDto roleSystemDto = findRoleSystemDto(roleDto, memberOfAttributeDto, schemaObjectClassDto);
			if (roleSystemDto != null) {
				// Find member attribute.
				SysRoleSystemAttributeDto memberAttribute = findMemberAttribute(memberOfAttributeDto, schemaObjectClassDto, roleSystemDto);
				if (memberAttribute != null) {
					memberAttribute.setSkipValueIfExcluded(skipIfValueExcluded);
					roleSystemAttributeService.save(memberAttribute);
					addToItemLog(logItem, MessageFormat.format("'Skip value if is contract excluded' parameter [{0}] was save for this role.", skipIfValueExcluded));
				}
			}
		}
	}
	
	/**
	 * Resolve 'Forward ACM'.
	 */
	private void resolveForwardAcm(boolean isNew, SynchronizationContext context, IdmRoleDto roleDto, SysSyncItemLogDto logItem, IcConnectorObject connectorObject, SysSystemAttributeMappingDto memberOfAttributeDto, SysSchemaObjectClassDto schemaObjectClassDto) {
		SysSystemAttributeMappingDto forwardAcmAttributeDto = context.getMappedAttributes().stream()
				.filter(attribute -> !attribute.isDisabledAttribute() && attribute.isEntityAttribute() && ROLE_FORWARD_ACM_FIELD.equals(attribute.getIdmPropertyName()))
				.findFirst()
				.orElse(null);

		Assert.notNull(forwardAcmAttributeDto, "Role identifier attribute cannot be null!");

		if (!isNew && AttributeMappingStrategyType.CREATE == forwardAcmAttributeDto.getStrategyType()) {
			addToItemLog(logItem, "The attribute for forward ACM has strategy set to 'Set only for new entity'. Role isn't new, so resolving of forward ACM will be skipped for this role.");
		} else {
			addToItemLog(logItem, MessageFormat.format("Resolving of forward ACM is activated for this role [{0}].", roleDto.getCode()));

			// Get forward ACM value from attribute transformation.
			Object forwardAcmObj = this.getValueByMappedAttribute(forwardAcmAttributeDto, connectorObject.getAttributes(), context);
			boolean forwardAcm = getForwardAcmFromValue(forwardAcmObj);

			// Save role-system with new forward ACM value.
			SysRoleSystemDto roleSystemDto = findRoleSystemDto(roleDto, memberOfAttributeDto, schemaObjectClassDto);
			if (roleSystemDto != null) {
				roleSystemDto.setForwardAccountManagemen(forwardAcm);
				roleSystemService.save(roleSystemDto);
				addToItemLog(logItem, MessageFormat.format("'Forward ACM' parameter [{0}] was save for this role.", forwardAcm));
			}
		}
	}

	/**
	 * Resolve 'Role catalogues'.
	 */
	private void resolveRoleCatalogue(boolean isNew, SynchronizationContext context, IdmRoleDto roleDto, SysSyncItemLogDto logItem, IcConnectorObject connectorObject) {
		SysSystemAttributeMappingDto roleCatalogueAttributeDto = context.getMappedAttributes().stream()
				.filter(attribute -> !attribute.isDisabledAttribute() && attribute.isEntityAttribute() && ROLE_CATALOGUE_FIELD.equals(attribute.getIdmPropertyName()))
				.findFirst()
				.orElse(null);

		Assert.notNull(roleCatalogueAttributeDto, "Attribute for resolve role catalogues cannot be null!");

		if (isNew && AttributeMappingStrategyType.CREATE == roleCatalogueAttributeDto.getStrategyType()) {
			addToItemLog(logItem, "The attribute for 'role catalogues' has strategy set to 'Set only for new entity'. Role isn't new, so resolving will be skipped for this role.");
			return;
		}
		addToItemLog(logItem, MessageFormat.format("Resolving of 'role catalogues' is activated for this role [{0}].", roleDto.getCode()));

		// Get role-catalogue values from attribute transformation.
		Object roleCataloguesObj = this.getValueByMappedAttribute(roleCatalogueAttributeDto, connectorObject.getAttributes(), context);
		List<IdmRoleCatalogueDto> roleCatalogueRoleDtos = getRoleCatalogueFromValue(roleCataloguesObj);

		List<UUID> currentRoleCatalogueRoles = Lists.newArrayList();

		roleCatalogueRoleDtos.forEach(roleCatalogueDto -> {
			List<IdmRoleCatalogueDto> parents = extractCatalogStructure(roleCatalogueDto);
			AtomicReference<IdmRoleCatalogueDto> parent = new AtomicReference<>();

			Lists.reverse(parents).forEach(catalogueDto -> {
				if (roleCatalogueService.isNew(catalogueDto)) {
					IdmRoleCatalogueDto newCatalogDto = new IdmRoleCatalogueDto();
					newCatalogDto.setCode(catalogueDto.getCode());
					newCatalogDto.setName(catalogueDto.getName());
					newCatalogDto.setExternalId(catalogueDto.getExternalId());
					newCatalogDto.setParent(catalogueDto.getParent());
					if (newCatalogDto.getParent() == null && parent.get() != null) {
						newCatalogDto.setParent(parent.get().getId());
					}

					if (newCatalogDto.getName() == null) {
						throw new ResultCodeException(AccResultCode.SYNC_OF_ROLES_CATALOGUE_NAME_IS_NULL);
					}
					
					// Make sure that the catalog with the same code and parent does not really exist.
					IdmRoleCatalogueFilter catalogueFilter = new IdmRoleCatalogueFilter();
					catalogueFilter.setCode(newCatalogDto.getCode());
					catalogueFilter.setParent(newCatalogDto.getParent());
					IdmRoleCatalogueDto byCode = roleCatalogueService.find(catalogueFilter, null).getContent()
							.stream()
							.findFirst()
							.orElse(null);
					if (byCode != null) {
						parent.set(roleCatalogueService.save(byCode));
						addToItemLog(logItem, MessageFormat.format("Role catalog item with code [{0}] was reused (catalog with same code and parent already exist).", parent.get().getCode()));
					}else {
						parent.set(roleCatalogueService.save(newCatalogDto));
						addToItemLog(logItem, MessageFormat.format("Role catalog item with code [{0}] was created.", parent.get().getCode()));
					}
				} else {
					parent.set(catalogueDto);
				}
			});
			// Last parent should be leaf catalog.
			IdmRoleCatalogueDto leafCatalogueDto = parent.get();
			IdmRoleCatalogueRoleDto roleCatalogueRoleDto = null;
			if (leafCatalogueDto.getId() != null) {
				IdmRoleCatalogueRoleFilter roleCatalogueRoleFilter = new IdmRoleCatalogueRoleFilter();
				roleCatalogueRoleFilter.setRoleCatalogueId(leafCatalogueDto.getId());
				roleCatalogueRoleFilter.setRoleId(roleDto.getId());
				roleCatalogueRoleDto = roleCatalogueRoleService.find(roleCatalogueRoleFilter, null)
						.getContent()
						.stream()
						.findFirst()
						.orElse(null);
			}

			if (roleCatalogueRoleDto == null) {
				// Create new role-catalogue-role.
				roleCatalogueRoleDto = new IdmRoleCatalogueRoleDto();
				roleCatalogueRoleDto.setRole(roleDto.getId());
				roleCatalogueRoleDto.setRoleCatalogue(leafCatalogueDto.getId());
				roleCatalogueRoleDto = roleCatalogueRoleService.save(roleCatalogueRoleDto);
				addToItemLog(logItem, MessageFormat.format("This role was included to the catalog with code [{0}].", leafCatalogueDto.getCode()));
			}
			currentRoleCatalogueRoles.add(roleCatalogueRoleDto.getId());
		});

		SysSyncRoleConfigDto config = getConfig(context);
		if (config.isRemoveCatalogueRoleSwitch()) {
			resolveRedundantCatalogueRole(roleDto, logItem, currentRoleCatalogueRoles, config);
		}
	}

	/**
	 * Transform result of transformation to list of role catalogs.
	 */
	@SuppressWarnings("unchecked")
	private List<IdmRoleCatalogueDto> getRoleCatalogueFromValue(Object roleCataloguesObj) {
		List<IdmRoleCatalogueDto> roleCatalogueRoleDtos = Lists.newArrayList();
		if (roleCataloguesObj != null) {
			if (roleCataloguesObj instanceof IdmRoleCatalogueDto) {
				roleCatalogueRoleDtos.add((IdmRoleCatalogueDto) roleCataloguesObj);
			}else {
				Assert.isInstanceOf(List.class, roleCataloguesObj, "Value from 'role catalogues' attribute must be List of IdmRoleCatalogueDto!");
				roleCatalogueRoleDtos = (List<IdmRoleCatalogueDto>) roleCataloguesObj;
			}
		}
		return roleCatalogueRoleDtos;
	}

	/**
	 * Transform result of transformation to boolean.
	 */
	private boolean getForwardAcmFromValue(Object forwardAcmObj) {
		boolean forwardAcm = false;
		if (forwardAcmObj != null) {
			Assert.isInstanceOf(Boolean.class, forwardAcmObj, "Value from forward AC attribute must be Boolean!");
			forwardAcm = (boolean) forwardAcmObj;
		}
		return forwardAcm;
	}
	
	/**
	 * Transform result of transformation to boolean.
	 */
	private boolean getSkipIfValueExcludedFromValue(Object valueObj) {
		boolean skipIfValueExcluded = false;
		if (valueObj != null) {
			Assert.isInstanceOf(Boolean.class, valueObj, "Value from 'Skip value if is contract excluded' attribute must be Boolean!");
			skipIfValueExcluded = (boolean) valueObj;
		}
		return skipIfValueExcluded;
	}

	/**
	 * Remove redundant role catalogue.
	 */
	private void resolveRedundantCatalogueRole(IdmRoleDto roleDto, SysSyncItemLogDto logItem, List<UUID> currentRoleCatalogueRoles, SysSyncRoleConfigDto config) {
		addToItemLog(logItem, "Removing the role from a catalog is allowed.");

		List<IdmRoleCatalogueRoleDto> redundantCatalogRoles = findRedundantRoleCatalogs(roleDto, currentRoleCatalogueRoles, config);
		redundantCatalogRoles.forEach(redundantCatalogRole -> {
			roleCatalogueRoleService.delete(redundantCatalogRole);
			IdmRoleCatalogueDto roleCatalogueDto = DtoUtils.getEmbedded(redundantCatalogRole, IdmRoleCatalogueRole_.roleCatalogue, IdmRoleCatalogueDto.class);
			addToItemLog(logItem, MessageFormat.format("This role was removed from the catalog with code [{0}]. Role was redundant (wasn't in system data).", roleCatalogueDto.getCode()));
		});
	}

	private List<IdmRoleCatalogueRoleDto> findRedundantRoleCatalogs(IdmRoleDto roleDto, List<UUID> currentRoleCatalogueRoles, SysSyncRoleConfigDto config) {

		UUID removeCatalogueRoleParentNode = config.getRemoveCatalogueRoleParentNode();
		Assert.notNull(removeCatalogueRoleParentNode, "A parent catalogue for remove redundant roles cannot be null!");
		IdmRoleCatalogueDto removeCatalogueRoleParentNodeDto = roleCatalogueService.get(removeCatalogueRoleParentNode);
		Assert.notNull(removeCatalogueRoleParentNodeDto, "A parent catalogue for remove redundant roles cannot be null!");
		
		IdmRoleCatalogueRoleFilter catalogueRoleFilter = new IdmRoleCatalogueRoleFilter();
		catalogueRoleFilter.setRoleId(roleDto.getId());
		List<IdmRoleCatalogueRoleDto> cataloguesWithRole = roleCatalogueRoleService.find(catalogueRoleFilter, null)
				.getContent()
				.stream()
				.filter(catalogueRole -> {
					// Check if catalog is under main remove catalog.
					if (catalogueRole.getRoleCatalogue().equals(removeCatalogueRoleParentNodeDto.getId())) {
						// If is role connected directly to parent remove catalog, then should checked too.
						return true;
					}
					
					IdmRoleCatalogueFilter catalogueFilter = new IdmRoleCatalogueFilter();
					catalogueFilter.setParent(removeCatalogueRoleParentNodeDto.getId());
					catalogueFilter.setId(catalogueRole.getRoleCatalogue());
					catalogueFilter.setRecursively(true);

					return roleCatalogueService.count(catalogueFilter) > 0;
				}).collect(Collectors.toList());

		return cataloguesWithRole.stream()
				.filter(catalogueWithRole -> !currentRoleCatalogueRoles.contains(catalogueWithRole.getId()))
				.collect(Collectors.toList());
	}

	/**
	 * Extract list of catalog's DTOs in order by planning catalog structure. First if a leaf.
	 */
	private List<IdmRoleCatalogueDto> extractCatalogStructure(IdmRoleCatalogueDto roleCatalogueDto) {
		List<IdmRoleCatalogueDto> parents = Lists.newArrayList();
		IdmRoleCatalogueDto parent = roleCatalogueDto;
		while (parent != null) {
			parents.add(parent);
			parent = DtoUtils.getEmbedded(parent, IdmRoleCatalogue_.parent, IdmRoleCatalogueDto.class, null);
		}
		return parents;
	}

	/**
	 * Resolve role membership.
	 */
	private boolean resolveMembership(boolean isNew, SynchronizationContext context, IdmRoleDto roleDto, SysSyncRoleConfigDto config, SysSyncItemLogDto logItem, IcConnectorObject connectorObject, SysSystemAttributeMappingDto memberOfAttributeDto, SysSchemaObjectClassDto schemaObjectClassDto) {
		UUID memberOfAttribute = config.getMemberOfAttribute();
		Assert.notNull(memberOfAttribute, "Member attribute cannot be null!");

		// Find attribute for get role identifier (DN)
		SysSystemAttributeMappingDto roleIdentifierAttributeDto = context.getMappedAttributes().stream()
				.filter(attribute -> !attribute.isDisabledAttribute() && attribute.isEntityAttribute() && ROLE_MEMBERSHIP_ID_FIELD.equals(attribute.getIdmPropertyName()))
				.findFirst()
				.orElse(null);
		Assert.notNull(roleIdentifierAttributeDto, "Role identifier attribute cannot be null!");


		if (!isNew && AttributeMappingStrategyType.CREATE == roleIdentifierAttributeDto.getStrategyType()) {
			addToItemLog(logItem, "The attribute with role identifier has strategy set to 'Set only for new entity'. Role isn't new, so resolving of membership will be skipped for this role.");
		} else {
			addToItemLog(logItem, MessageFormat.format("Resolving of membership is activated for this role {0}.", roleDto.getCode()));

			Object roleIdentifierObj = this.getValueByMappedAttribute(roleIdentifierAttributeDto, connectorObject.getAttributes(), context);
			String roleIdentifier;
			if (roleIdentifierObj != null) {
				Assert.isInstanceOf(String.class, roleIdentifierObj, "Role identifier must be String!");
				roleIdentifier = (String) roleIdentifierObj;
			} else {
				// Identifier form transformation is null -> We will delete role-system relations.
				addToItemLog(logItem, "The role identifier form a transformation is null -> We will try to delete role-system relation and member attribute.");
				SysRoleSystemDto roleSystemDto = findRoleSystemDto(roleDto, memberOfAttributeDto, schemaObjectClassDto);
				if (roleSystemDto != null) {
					// Find member attribute. If exist, then will be deleted.
					SysRoleSystemAttributeDto memberAttribute = findMemberAttribute(memberOfAttributeDto, schemaObjectClassDto, roleSystemDto);
					if (memberAttribute != null) {
						roleSystemAttributeService.delete(memberAttribute);
						addToItemLog(logItem, MessageFormat.format("Member attribute {0} was deleted.", memberAttribute.getName()));
					}
					// Check if role-system relationship contains others attribute. If not, the relationship will be deleted.
					SysRoleSystemAttributeDto someOtherAttribute = findMemberAttribute(null, schemaObjectClassDto, roleSystemDto);
					if (someOtherAttribute == null) {
						roleSystemService.delete(roleSystemDto);
						addToItemLog(logItem, MessageFormat.format("Role-system relation {0} was deleted.", roleSystemDto.getId()));
					}
				}
				// End of processing.
				return false;
			}

			// Resolve (create or update) relation on a system.
			SysRoleSystemDto roleSystemDto = resolveRoleSystem(roleDto, memberOfAttributeDto, schemaObjectClassDto);

			// Resolve (create or update) relation on member attribute (ldapGroups).
			resolveMemberAttribute(logItem, memberOfAttributeDto, roleIdentifier, schemaObjectClassDto, roleSystemDto);
		}
		return true;
	}

	/**
	 * Resolve (create or update) relation on member attribute (ldapGroups).
	 */
	private void resolveMemberAttribute(SysSyncItemLogDto logItem, SysSystemAttributeMappingDto memberOfAttributeDto, String roleIdentifier, SysSchemaObjectClassDto schemaObjectClassDto, SysRoleSystemDto roleSystemDto) {
		SysRoleSystemAttributeDto roleMemberOfAttributeDto = findMemberAttribute(memberOfAttributeDto, schemaObjectClassDto, roleSystemDto);
		if (roleMemberOfAttributeDto == null) {
			// Create role attribute by mapping system attribute.
			roleMemberOfAttributeDto = new SysRoleSystemAttributeDto();
		}
		transformMappingAttributeToRoleAttribute(memberOfAttributeDto, roleMemberOfAttributeDto, roleSystemDto);
		// Set merge value (transformation).
		roleMemberOfAttributeDto.setTransformScript(getMembershipTransformationScript(roleIdentifier));
		roleMemberOfAttributeDto = roleSystemAttributeService.save(roleMemberOfAttributeDto);

		addToItemLog(logItem, MessageFormat.format("Role-system attribute {0} with transformation {1} was created.", roleMemberOfAttributeDto.toString(), roleMemberOfAttributeDto.getTransformScript()));
	}

	private String getMembershipTransformationScript(String roleIdentifier) {
		return MessageFormat.format("return \"{0}\";", roleIdentifier);
	}

	private SysRoleSystemAttributeDto findMemberAttribute(SysSystemAttributeMappingDto memberOfAttributeDto, SysSchemaObjectClassDto schemaObjectClassDto, SysRoleSystemDto roleSystemDto) {
		SysRoleSystemAttributeFilter roleSystemAttributeFilter = new SysRoleSystemAttributeFilter();
		roleSystemAttributeFilter.setSystemId(schemaObjectClassDto.getSystem());
		roleSystemAttributeFilter.setRoleSystemId(roleSystemDto.getId());
		if (memberOfAttributeDto != null) {
			roleSystemAttributeFilter.setSystemAttributeMappingId(memberOfAttributeDto.getId());
		}

		return roleSystemAttributeService.find(roleSystemAttributeFilter, null)
				.getContent()
				.stream()
				.findFirst()
				.orElse(null);
	}

	/**
	 * Resolve (create or update) relation on system.
	 */
	private SysRoleSystemDto resolveRoleSystem(IdmRoleDto roleDto, SysSystemAttributeMappingDto memberOfAttributeDto, SysSchemaObjectClassDto schemaObjectClassDto) {
		SysRoleSystemDto roleSystemDto = findRoleSystemDto(roleDto, memberOfAttributeDto, schemaObjectClassDto);

		// Create a role-system relation.
		if (roleSystemDto == null) {
			roleSystemDto = new SysRoleSystemDto();
			roleSystemDto.setRole(roleDto.getId());
			roleSystemDto.setSystemMapping(memberOfAttributeDto.getSystemMapping());
			roleSystemDto.setSystem(schemaObjectClassDto.getSystem());
		}
		
		roleSystemDto = roleSystemService.save(roleSystemDto);
		return roleSystemDto;
	}

	private SysRoleSystemDto findRoleSystemDto(IdmRoleDto roleDto, SysSystemAttributeMappingDto memberOfAttributeDto, SysSchemaObjectClassDto schemaObjectClassDto) {
		SysRoleSystemFilter roleSystemFilter = new SysRoleSystemFilter();
		roleSystemFilter.setRoleId(roleDto.getId());
		roleSystemFilter.setSystemId(schemaObjectClassDto.getSystem());
		roleSystemFilter.setSystemMappingId(memberOfAttributeDto.getSystemMapping());

		return roleSystemService.find(roleSystemFilter, null)
				.getContent()
				.stream()
				.findFirst()
				.orElse(null);
	}

	/**
	 * Create role attribute by mapping system attribute.
	 */
	private void transformMappingAttributeToRoleAttribute(SysSystemAttributeMappingDto memberOfAttributeDto, SysRoleSystemAttributeDto roleMemberOfAttributeDto, SysRoleSystemDto roleSystemDto) {
		roleMemberOfAttributeDto.setRoleSystem(roleSystemDto.getId());
		roleMemberOfAttributeDto.setSystemAttributeMapping(memberOfAttributeDto.getId());
		roleMemberOfAttributeDto.setDisabledAttribute(memberOfAttributeDto.isDisabledAttribute());
		roleMemberOfAttributeDto.setIdmPropertyName(memberOfAttributeDto.getIdmPropertyName());
		roleMemberOfAttributeDto.setExtendedAttribute(memberOfAttributeDto.isExtendedAttribute());
		roleMemberOfAttributeDto.setConfidentialAttribute(memberOfAttributeDto.isConfidentialAttribute());
		roleMemberOfAttributeDto.setEntityAttribute(memberOfAttributeDto.isEntityAttribute());
		roleMemberOfAttributeDto.setCached(memberOfAttributeDto.isCached());
		roleMemberOfAttributeDto.setName(memberOfAttributeDto.getName());
		roleMemberOfAttributeDto.setPasswordAttribute(memberOfAttributeDto.isPasswordAttribute());
		roleMemberOfAttributeDto.setSendAlways(memberOfAttributeDto.isSendAlways());
		roleMemberOfAttributeDto.setSchemaAttribute(memberOfAttributeDto.getSchemaAttribute());
		roleMemberOfAttributeDto.setStrategyType(memberOfAttributeDto.getStrategyType());
		roleMemberOfAttributeDto.setSendOnlyIfNotNull(memberOfAttributeDto.isSendOnlyIfNotNull());
		roleMemberOfAttributeDto.setUid(memberOfAttributeDto.isUid());
	}

	private SysSyncRoleConfigDto getConfig(SynchronizationContext context) {
		return (SysSyncRoleConfigDto) context.getConfig();
	}

	@Override
	protected EntityAccountFilter createEntityAccountFilter() {
		return new AccRoleAccountFilter();
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected EntityAccountService<EntityAccountDto, EntityAccountFilter> getEntityAccountService() {
		return (EntityAccountService) roleAccountService;
	}

	@Override
	protected EntityAccountDto createEntityAccountDto() {
		return new AccRoleAccountDto();
	}

	@Override
	protected IdmRoleService getService() {
		return roleService;
	}
	
	@Override
	protected CorrelationFilter getEntityFilter(SynchronizationContext context) {
		return new IdmRoleFilter();
	}

	@Override
	protected IdmRoleDto createEntityDto() {
		return new IdmRoleDto();
	}
	
	@Override
	public String getSystemEntityType() {
		return SYSTEM_ENTITY_TYPE;
	}
}
