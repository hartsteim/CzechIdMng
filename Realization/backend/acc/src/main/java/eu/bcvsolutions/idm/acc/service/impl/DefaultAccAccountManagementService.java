package eu.bcvsolutions.idm.acc.service.impl;

import eu.bcvsolutions.idm.acc.dto.filter.SysSystemGroupSystemFilter;
import eu.bcvsolutions.idm.acc.service.api.SysSystemGroupSystemService;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.domain.AccountType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.AccIdentityAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemAttributeMappingFilter;
import eu.bcvsolutions.idm.acc.entity.AccIdentityAccount_;
import eu.bcvsolutions.idm.acc.entity.SysRoleSystemAttribute_;
import eu.bcvsolutions.idm.acc.entity.SysRoleSystem_;
import eu.bcvsolutions.idm.acc.entity.SysSchemaObjectClass_;
import eu.bcvsolutions.idm.acc.exception.ProvisioningException;
import eu.bcvsolutions.idm.acc.service.api.AccAccountManagementService;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmAccountDto;
import eu.bcvsolutions.idm.core.api.dto.IdmEntityStateDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.OperationResultDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmEntityStateFilter;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.EntityStateManager;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;

/**
 * Service for control account management. Account management is supported for
 * {@link SystemEntityType#IDENTITY} only.
 * 
 * @author Vít Švanda
 * @author Peter Štrunc <github.com/peter-strunc>
 *
 */
@Service
public class DefaultAccAccountManagementService implements AccAccountManagementService {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory
			.getLogger(DefaultAccAccountManagementService.class);
	private final AccAccountService accountService;
	private final SysRoleSystemService roleSystemService;
	private final AccIdentityAccountService identityAccountService;
	private final SysRoleSystemAttributeService roleSystemAttributeService;
	private final SysSystemAttributeMappingService systemAttributeMappingService;
	private final SysSystemMappingService systemMappingService;
	private final SysSchemaObjectClassService schemaObjectClassService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private EntityStateManager entityStateManager;
	@Autowired
	private EntityEventManager entityEventManager;
	@Autowired
	private LookupService lookupService;
	@Autowired
	private SysSystemGroupSystemService systemGroupSystemService;

	@Autowired
	public DefaultAccAccountManagementService(SysRoleSystemService roleSystemService, AccAccountService accountService,
			AccIdentityAccountService identityAccountService, SysRoleSystemAttributeService roleSystemAttributeService,
			SysSystemAttributeMappingService systemAttributeMappingService,
			SysSystemMappingService systemMappingService, SysSchemaObjectClassService schemaObjectClassService) {
		super();
		//
		Assert.notNull(identityAccountService, "Service is required.");
		Assert.notNull(roleSystemService, "Service is required.");
		Assert.notNull(accountService, "Service is required.");
		Assert.notNull(roleSystemAttributeService, "Service is required.");
		Assert.notNull(systemAttributeMappingService, "Service is required.");
		Assert.notNull(systemMappingService, "Service is required.");
		Assert.notNull(schemaObjectClassService, "Service is required.");
		//
		this.roleSystemService = roleSystemService;
		this.accountService = accountService;
		this.identityAccountService = identityAccountService;
		this.roleSystemAttributeService = roleSystemAttributeService;
		this.systemAttributeMappingService = systemAttributeMappingService;
		this.systemMappingService = systemMappingService;
		this.schemaObjectClassService = schemaObjectClassService;
	}

	@Override
	public boolean resolveIdentityAccounts(IdmIdentityDto identity) {
		Assert.notNull(identity, "Identity is required.");
		// find not deleted identity accounts
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		List<AccIdentityAccountDto> allIdentityAccountList = identityAccountService.find(filter, null).getContent();
		List<IdmIdentityRoleDto> identityRoles = identityRoleService.findAllByIdentity(identity.getId());
		
		if (CollectionUtils.isEmpty(identityRoles) && CollectionUtils.isEmpty(allIdentityAccountList)) {
			// No roles and accounts ... we don't have anything to do
			return false;
		}
		
		// account with delete accepted states will be removed on the end
		IdmEntityStateFilter identityAccountStatesFilter = new IdmEntityStateFilter();
		identityAccountStatesFilter.setSuperOwnerId(identity.getId());
		identityAccountStatesFilter.setOwnerType(entityStateManager.getOwnerType(AccIdentityAccountDto.class));
		identityAccountStatesFilter.setResultCode(CoreResultCode.DELETED.getCode());
		List<IdmEntityStateDto> identityAccountStates = entityStateManager.findStates(identityAccountStatesFilter, null).getContent();
		List<AccIdentityAccountDto> identityAccountList = allIdentityAccountList //
				.stream() //
				.filter(ia -> {
					return !identityAccountStates //
							.stream() //
							.anyMatch(state -> { 
								return ia.getId().equals(state.getOwnerId());
							});
				}).collect(Collectors.toList());
		
		// create / remove accounts 
		if (!CollectionUtils.isEmpty(identityRoles) || !CollectionUtils.isEmpty(identityAccountList)) {
			List<AccIdentityAccountDto> identityAccountsToCreate = new ArrayList<>();
			List<AccIdentityAccountDto> identityAccountsToDelete = new ArrayList<>();
	
			// Is role valid in this moment
			resolveIdentityAccountForCreate(identity, identityAccountList, identityRoles, identityAccountsToCreate,
					identityAccountsToDelete, false, null);
	
			// Is role invalid in this moment
			resolveIdentityAccountForDelete(identityAccountList, identityRoles, identityAccountsToDelete);
			
			// Create new identity accounts
			identityAccountsToCreate.forEach(identityAccount -> identityAccountService.save(identityAccount));
	
			// Delete invalid identity accounts
			identityAccountsToDelete.forEach(identityAccount -> identityAccountService.deleteById(identityAccount.getId()));
		}		
		// clear identity accounts marked to be deleted
		identityAccountStates //
			.stream() //
			.forEach(state -> {	// 
				AccIdentityAccountDto deleteIdentityAccount = identityAccountService.get(state.getOwnerId());
				if (deleteIdentityAccount != null) {
					// identity account can be deleted manually.
					identityAccountService.delete(deleteIdentityAccount);
				}
				entityStateManager.deleteState(state);
			});
		
		// Return value is deprecated since version 9.5.0  (is useless)
		return true;
	}
	
	
	@Override
	public List<UUID> resolveNewIdentityRoles(IdmIdentityDto identity, IdmIdentityRoleDto... identityRoles) {
		Assert.notNull(identity, "Identity is required.");

		if (identityRoles == null || identityRoles.length == 0) {
			// No identity-roles ... we don't have anything to do
			return null;
		}

		List<IdmIdentityRoleDto> identityRolesList = Lists.newArrayList(identityRoles);
		List<AccIdentityAccountDto> identityAccountsToCreate = Lists.newArrayList();

		// For this account should be executed provisioning
		List<UUID> accounts = Lists.newArrayList();
		
		// Is role valid in this moment
		resolveIdentityAccountForCreate(identity, Lists.newArrayList(), identityRolesList, identityAccountsToCreate,
				Lists.newArrayList(), true, accounts);

		// Create new identity accounts
		identityAccountsToCreate.forEach(identityAccount -> {
			// Check if this identity-account already exists, if yes then is his account ID
			// add to accounts (for provisioning).
			createIdentityAccountIfNotExists(accounts, identityAccount);
		});
		return accounts;
	}
	
	@Override
	public  List<UUID>  resolveUpdatedIdentityRoles(IdmIdentityDto identity, IdmIdentityRoleDto... identityRoles) {
		Assert.notNull(identity, "Identity is required.");

		if (identityRoles == null || identityRoles.length == 0) {
			// No identity-roles ... we don't have anything to do
			return null;
		}
		List<IdmIdentityRoleDto> identityRolesList = Lists.newArrayList(identityRoles);
		// Find identity-accounts for changed identity-roles (using IN predicate)
		List<UUID> identityRoleIds = identityRolesList.stream() //
				.map(IdmIdentityRoleDto::getId) //
				.collect(Collectors.toList()); //
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		filter.setIdentityRoleIds(identityRoleIds);
		List<AccIdentityAccountDto> identityAccountList = identityAccountService.find(filter, null).getContent();

		// create / remove accounts
		List<AccIdentityAccountDto> identityAccountsToCreate = new ArrayList<>();
		List<AccIdentityAccountDto> identityAccountsToDelete = new ArrayList<>();

		// Is role valid in this moment
		resolveIdentityAccountForCreate(identity, identityAccountList, identityRolesList, identityAccountsToCreate,
				identityAccountsToDelete, false, null);

		// Is role invalid in this moment
		resolveIdentityAccountForDelete(identityAccountList, identityRolesList, identityAccountsToDelete);

		// For this accounts should be execute a provisioning. We have to execute provisioning for all changed accounts
		// although identity-role was not changed (EAV attributes could have been changed).
		List<UUID> accounts = identityAccountList.stream() //
				.map(AccIdentityAccountDto::getAccount) //
				.distinct() //
				.collect(Collectors.toList());

		// Create new identity accounts
		identityAccountsToCreate.forEach(identityAccount -> {
			// Check if this identity-account already exists, if yes then is his account ID
			// add to accounts (for provisioning).
			createIdentityAccountIfNotExists(accounts, identityAccount);
		});

		// Delete invalid identity accounts
		identityAccountsToDelete.forEach(identityAccount -> {
			identityAccountService.deleteById(identityAccount.getId());
		});
		
		return accounts;
	}

	

	/**
	 * Return UID for this identity and roleSystem. First will be find and use
	 * transform script from roleSystem attribute. If isn't UID attribute for
	 * roleSystem defined, then will be use default UID attribute handling.
	 * 
	 * @param entity
	 * @param roleSystem
	 * @return
	 */
	@Override
	public String generateUID(AbstractDto entity, SysRoleSystemDto roleSystem) {
	
		// Find attributes for this roleSystem
		SysRoleSystemAttributeFilter roleSystemAttrFilter = new SysRoleSystemAttributeFilter();
		roleSystemAttrFilter.setRoleSystemId(roleSystem.getId());
		roleSystemAttrFilter.setIsUid(Boolean.TRUE);

		List<SysRoleSystemAttributeDto> attributesUid = roleSystemAttributeService
				.find(roleSystemAttrFilter, null) //
				.getContent(); //
		
		if (attributesUid.size() > 1) {
			IdmRoleDto roleDto = DtoUtils.getEmbedded(roleSystem, SysRoleSystem_.role);
			SysSystemDto systemDto = DtoUtils.getEmbedded(roleSystem, SysRoleSystem_.system);
			throw new ProvisioningException(AccResultCode.PROVISIONING_ROLE_ATTRIBUTE_MORE_UID,
					ImmutableMap.of("role", roleDto.getCode(), "system", systemDto.getName()));
		}

		SysRoleSystemAttributeDto uidRoleAttribute = !attributesUid.isEmpty() ? attributesUid.get(0) : null;

		// If roleSystem UID attribute found, then we use his transformation
		// script.
		if (uidRoleAttribute != null) {
			// Default values (values from schema attribute handling)
			SysSystemAttributeMappingDto systemAttributeMapping = DtoUtils.getEmbedded(uidRoleAttribute,
					SysRoleSystemAttribute_.systemAttributeMapping.getName(), SysSystemAttributeMappingDto.class);
			
			uidRoleAttribute.setSchemaAttribute(systemAttributeMapping.getSchemaAttribute());
			uidRoleAttribute.setTransformFromResourceScript(systemAttributeMapping.getTransformFromResourceScript());
			Object uid = systemAttributeMappingService.getAttributeValue(null, entity, uidRoleAttribute);
			if (uid == null) {
				SysSystemDto systemEntity = DtoUtils.getEmbedded(roleSystem, SysRoleSystem_.system);
				throw new ProvisioningException(AccResultCode.PROVISIONING_GENERATED_UID_IS_NULL,
						ImmutableMap.of("system", systemEntity.getName()));
			}
			if (!(uid instanceof String)) {
				throw new ProvisioningException(AccResultCode.PROVISIONING_ATTRIBUTE_UID_IS_NOT_STRING,
						ImmutableMap.of("uid", uid));
			}
			return (String) uid;
		}

		// If roleSystem UID was not found, then we use default UID schema
		// attribute handling
		SysSystemAttributeMappingFilter attributeMappingFilter = new SysSystemAttributeMappingFilter();
		attributeMappingFilter.setSystemMappingId(roleSystem.getSystemMapping());
		attributeMappingFilter.setIsUid(Boolean.TRUE);
		attributeMappingFilter.setDisabledAttribute(Boolean.FALSE);
		List<SysSystemAttributeMappingDto> defaultUidAttributes = systemAttributeMappingService
				.find(attributeMappingFilter, null).getContent();
		if(defaultUidAttributes.size() == 1) {
			return systemAttributeMappingService.generateUid(entity, defaultUidAttributes.get(0));
		}
		
		// Default UID attribute was not correctly found, getUidAttribute method will be throw exception.
		// This is good time for loading the system (is used in exception message)
		SysSystemMappingDto mapping = systemMappingService.get(roleSystem.getSystemMapping());
		SysSchemaObjectClassDto objectClassDto = schemaObjectClassService.get(mapping.getObjectClass());
		SysSystemDto system = DtoUtils.getEmbedded(objectClassDto, SysSchemaObjectClass_.system);
		systemAttributeMappingService.getUidAttribute(defaultUidAttributes, system);
		// Exception occurred
		return null;
	}

	@Override
	@Transactional
	public List<UUID> deleteIdentityAccount(IdmIdentityRoleDto entity) {
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityRoleId(entity.getId());
		List<UUID> accountIds = Lists.newArrayList();
		
		identityAccountService.find(filter, null).getContent() //
				.forEach(identityAccount -> { //
					accountIds.add(identityAccount.getAccount());
					identityAccountService.delete(identityAccount);
				});
		return accountIds;
	}
	
	@Override
	@Transactional
	public void deleteIdentityAccount(EntityEvent<IdmIdentityRoleDto> event) {
		Assert.notNull(event, "Event is required.");
		IdmIdentityRoleDto identityRole = event.getContent();
		Assert.notNull(identityRole, "Identity role is required.");
		Assert.notNull(identityRole, "Identity role identifier is required.");
		//
		boolean skipPropagate = event.getBooleanProperty(IdmAccountDto.SKIP_PROPAGATE);
		boolean bulk = event.getRootId() != null 
				&& entityEventManager.isRunnable(event.getRootId())
				&& !entityEventManager.getEvent(event.getRootId()).getOwnerType() // check parent event is not role request
					.equals(entityEventManager.getOwnerType(IdmRoleRequestDto.class)); 
		
		if (!skipPropagate && !bulk) {
			// role is deleted without request or without any parent ... we need to remove account synchronously
			List<UUID> accountIds = deleteIdentityAccount(identityRole);
			// We needs accounts which were connected to deleted identity-role in next
			// processor (we want to execute provisioning only for that accounts)
			event.getProperties().put(ACCOUNT_IDS_FOR_DELETED_IDENTITY_ROLE, (Serializable) accountIds);
			return;
		}
		
		// Role is deleted in bulk (e.g. role request) - account management has to be called outside
		// we just mark identity account to be deleted and remove identity role
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityRoleId(identityRole.getId());

		identityAccountService.find(filter, null).getContent() //
				.forEach(identityAccount -> { //
					// Set relation on identity-role to null
					identityAccount.setIdentityRole(null);

					if (bulk) {
						// For bulk create entity state for identity account. 
						IdmEntityStateDto stateDeleted = new IdmEntityStateDto();
						stateDeleted.setSuperOwnerId(identityAccount.getIdentity());
						stateDeleted.setResult(new OperationResultDto.Builder(OperationState.RUNNING)
								.setModel(new DefaultResultModel(CoreResultCode.DELETED)).build());
						entityStateManager.saveState(identityAccount, stateDeleted);
					} else {
						// Noting identity-accounts for delayed delete and account management
						notingIdentityAccountForDelayedAcm(event, identityAccount, IdmAccountDto.IDENTITY_ACCOUNT_FOR_DELAYED_ACM);
					}
					identityAccountService.save(identityAccount);
				});
		
		// If default creation of accounts is disabled for this role-system  (or system is in a cross-domain group), then relation between identity
		// and account may not exist. In this scenario we have to made provisioning too.
		// So we try to find these role-systems and its accounts.
		SysRoleSystemFilter roleSystemForProvisioningFilter = new SysRoleSystemFilter();
		roleSystemForProvisioningFilter.setRoleId(identityRole.getRole());

		roleSystemService.find(roleSystemForProvisioningFilter, null).getContent().stream()
				.filter(roleSystem -> {
					if (!roleSystem.isCreateAccountByDefault()) {
						return true;
					} else {
						SysSystemGroupSystemFilter systemGroupSystemFilter = new SysSystemGroupSystemFilter();
						systemGroupSystemFilter.setCrossDomainsGroupsForRoleSystemId(roleSystem.getId());
						if (systemGroupSystemService.count(systemGroupSystemFilter) >= 1
							&& (identityRole.getRoleSystem() == null || roleSystem.getId().equals(identityRole.getRoleSystem()))
						) {
							// This role-system overriding a merge attribute which is using in
							// active cross-domain group and roleSystem in identity-role is null or same as role-system.
							// -> Provisioning should be made.
							return true;
						}
					}
					return false;
				})
				.forEach(roleSystem -> {
					IdmIdentityContractDto contractDto = lookupService.lookupEmbeddedDto(identityRole, IdmIdentityRole_.identityContract);

					AccIdentityAccountFilter identityAccountFilter = new AccIdentityAccountFilter();
					identityAccountFilter.setSystemId(roleSystem.getSystem());
					identityAccountFilter.setIdentityId(contractDto.getIdentity());
					identityAccountService.find(identityAccountFilter, null).getContent()
							.forEach(identityAccount -> {
								// Noting identity-accounts for delayed additional provisioning.
								notingIdentityAccountForDelayedAcm(event, identityAccount, IdmAccountDto.ACCOUNT_FOR_ADDITIONAL_PROVISIONING);
							});
				});
	}
	
	/**
	 * Resolve identity account to delete
	 * 
	 * @param identityAccountList
	 * @param identityRoles
	 * @param identityAccountsToDelete
	 */
	private void resolveIdentityAccountForDelete(List<AccIdentityAccountDto> identityAccountList,
			List<IdmIdentityRoleDto> identityRoles, List<AccIdentityAccountDto> identityAccountsToDelete) {

		// Search IdentityAccounts to delete
		identityRoles.stream().filter(identityRole -> {
			return !identityRole.isValid();
		}).forEach(identityRole -> {
			identityAccountList.stream() //
					.filter(identityAccount -> identityRole.getId().equals(identityAccount.getIdentityRole())) //
					// Identity-account is not removed (even if that identity-role is invalid) if
					// the role-system has enabled forward account management and identity-role will
					// be valid in the future.
					.filter(identityAccount -> identityAccount.getRoleSystem() == null
							|| !( ((SysRoleSystemDto) DtoUtils
									.getEmbedded(identityAccount, AccIdentityAccount_.roleSystem))
									.isForwardAccountManagemen() && identityRole.isValidNowOrInFuture())) //
					.forEach(identityAccountsToDelete::add);
		});

		// Search IdentityAccounts to delete - we want to delete identity-account if
		// identity-role is valid, but mapped system on the role does not longer exist.
		identityRoles.stream().filter(identityRole -> {
			return identityRole.isValid();
		}).forEach(identityRole -> {
			identityAccountList.stream() //
					.filter(identityAccount -> identityRole.getId().equals(identityAccount.getIdentityRole()))
					.filter(identityAccount -> {
						// Remove account if role-system is null.
						if (identityAccount.getRoleSystem() == null) {
							return true;
						}
						// Remove an account if role-system does not supports creation by default or if is in cross-domain group.
						SysRoleSystemDto roleSystem = lookupService.lookupEmbeddedDto(identityAccount, AccIdentityAccount_.roleSystem);
						if (roleSystem != null
								&& !roleSystem.isCreateAccountByDefault()) {
							return true;
						} else if (roleSystem != null) {
							SysSystemGroupSystemFilter systemGroupSystemFilter = new SysSystemGroupSystemFilter();
							systemGroupSystemFilter.setCrossDomainsGroupsForRoleSystemId(roleSystem.getId());
							if (systemGroupSystemService.count(systemGroupSystemFilter) >= 1) {
								// This role-system overriding a merge attribute which is using in
								// active cross-domain group. -> Identity account should be deleted.
								return true;
							}
						}
						return false;
					})
					.forEach(identityAccountsToDelete::add);
		});
	}

	/**
	 * Resolve Identity account - to create.
	 */
	private void resolveIdentityAccountForCreate(IdmIdentityDto identity,
												 List<AccIdentityAccountDto> identityAccountList,
												 List<IdmIdentityRoleDto> identityRoles,
												 List<AccIdentityAccountDto> identityAccountsToCreate,
												 List<AccIdentityAccountDto> identityAccountsToDelete,
												 boolean onlyCreateNew,
												 List<UUID> additionalAccountsForProvisioning
												 ) {

		identityRoles.forEach(identityRole -> {
			SysRoleSystemFilter roleSystemFilter = new SysRoleSystemFilter();
			roleSystemFilter.setRoleId(identityRole.getRole());
			List<SysRoleSystemDto> roleSystems = roleSystemService.find(roleSystemFilter, null).getContent();

			// Is role valid in this moment or
			// role-system has enabled forward account management (identity-role have to be
			// valid in the future)
			roleSystems.stream()
					.filter(roleSystem -> (identityRole.isValid()
							|| (roleSystem.isForwardAccountManagemen() && identityRole.isValidNowOrInFuture())))
					// Create account only if role-systems supports creation by default (not for cross-domains).
					.filter(roleSystem -> supportsAccountCreation(roleSystem, identity, additionalAccountsForProvisioning))
					.forEach(roleSystem ->
							resolveIdentityAccountForCreate(identity, identityAccountList, identityAccountsToCreate,
							identityAccountsToDelete, onlyCreateNew, roleSystem, identityRole)
					);
		});
	}

	private void resolveIdentityAccountForCreate(IdmIdentityDto identity,
												 List<AccIdentityAccountDto> identityAccountList,
												 List<AccIdentityAccountDto> identityAccountsToCreate,
												 List<AccIdentityAccountDto> identityAccountsToDelete,
												 boolean onlyCreateNew,
												 SysRoleSystemDto roleSystem,
												 IdmIdentityRoleDto identityRole)  {
		String uid = generateUID(identity, roleSystem);
		// Check on change of UID is not executed if all given identity-roles are new
		if (!onlyCreateNew) {
			// Check identity-account for that role-system on change the definition of UID
			checkOnChangeUID(uid, roleSystem, identityAccountList, identityAccountsToDelete);
		}

		// Try to find identity-account for this identity-role. If exists and doesn't in
		// list of identity-account to delete, then we are done.
		AccIdentityAccountDto existsIdentityAccount = findAlreadyExistsIdentityAccount(
				identityAccountList, identityAccountsToDelete, identityRole, roleSystem);

		if (existsIdentityAccount != null) {
			if(existsIdentityAccount.getRoleSystem() == null) {
				// IdentityAccount already exist, but doesn't have relation on RoleSystem. This
				// could happen if system mapping was deleted and recreated or if was role use
				// as sync default role, but without mapping on this system.

				// We have to create missing relation, so we will set and save RoleSystem.
				existsIdentityAccount.setRoleSystem(roleSystem.getId());
				identityAccountService.save(existsIdentityAccount);
			}
			return;
		}

		// For this system we need to create new (or found exists) account
		AccAccountDto account = createAccountByRoleSystem(uid, identity, roleSystem,
				identityAccountsToCreate);
		if (account == null) {
			return;
		}

		// Prevent to create the same identity account
		if (identityAccountList.stream().filter(identityAccount -> {
			return identityAccount.getAccount().equals(account.getId())
					&& identityRole.getId().equals(identityAccount.getIdentityRole())
					&& roleSystem.getId().equals(identityAccount.getRoleSystem());
		}).count() == 0) {
			AccIdentityAccountDto identityAccount = new AccIdentityAccountDto();
			identityAccount.setAccount(account.getId());
			identityAccount.setIdentity(identity.getId());
			identityAccount.setIdentityRole(identityRole.getId());
			identityAccount.setRoleSystem(roleSystem.getId());
			identityAccount.setOwnership(true);
			identityAccount.getEmbedded().put(AccIdentityAccount_.account.getName(), account);

			identityAccountsToCreate.add(identityAccount);
		}
	}

	private boolean supportsAccountCreation(SysRoleSystemDto roleSystem, IdmIdentityDto identity, List<UUID> additionalAccountsForProvisioning)  {
		boolean canBeCreated = roleSystem.isCreateAccountByDefault();
		if (canBeCreated) {
			SysSystemGroupSystemFilter systemGroupSystemFilter = new SysSystemGroupSystemFilter();
			systemGroupSystemFilter.setCrossDomainsGroupsForRoleSystemId(roleSystem.getId());
			if (systemGroupSystemService.count(systemGroupSystemFilter) >= 1) {
				// This role-system overriding a merge attribute which is using in
				// active cross-domain group. -> Account will not be created.
				canBeCreated = false;
			}
		}

		if (!canBeCreated) {
			// We need to do provisioning for skipped identity-role/accounts (because Cross-domains).
			// We have to find all identity-accounts for identity and system.
			AccIdentityAccountFilter identityAccountFilter = new AccIdentityAccountFilter();
			identityAccountFilter.setSystemId(roleSystem.getSystem());
			identityAccountFilter.setIdentityId(identity.getId());
			AccIdentityAccountDto identityAccountDto = identityAccountService.find(identityAccountFilter, null)
					.getContent()
					.stream()
					.filter(accIdentityAccountDto -> accIdentityAccountDto.getRoleSystem() != null)
					.filter(identityAccount -> {
								SysRoleSystemDto roleSystemFromIdentityAccount = lookupService.lookupEmbeddedDto(identityAccount, AccIdentityAccount_.roleSystem);
								return roleSystemFromIdentityAccount != null
										&& roleSystem.getSystemMapping().equals(roleSystemFromIdentityAccount.getSystemMapping());
							}
					).findFirst().orElse(null);
			if (identityAccountDto != null && additionalAccountsForProvisioning != null) {
				additionalAccountsForProvisioning.add(identityAccountDto.getAccount());
			}
		}
		return canBeCreated;
	}
	
	/**
	 * Check identity-account for that role-system on change the definition of UID
	 *
	 */
	private void checkOnChangeUID(String uid, SysRoleSystemDto roleSystem,
			List<AccIdentityAccountDto> identityAccountList, List<AccIdentityAccountDto> identityAccountsToDelete) {
		identityAccountList.forEach(identityAccount -> { //
			if (roleSystem.getId().equals(identityAccount.getRoleSystem())) {
				// Has identity account same UID as account?
				AccAccountDto account = AccIdentityAccountService.getEmbeddedAccount(identityAccount);
				if (!uid.equals(account.getUid())) {
					// We found identityAccount for same identity and roleSystem, but this
					// identityAccount is link to Account with different UID. It's probably means
					// definition of UID
					// (transformation) on roleSystem was changed. We have to delete this
					// identityAccount.
					identityAccountsToDelete.add(identityAccount);
				}
			}
		});
	}

	/**
	 * Create Account by given roleSystem
	 * 
	 * @param identity
	 * @param roleSystem
	 * @param identityAccountsToCreate
	 * @return
	 */
	private AccAccountDto createAccountByRoleSystem(String uid, IdmIdentityDto identity, SysRoleSystemDto roleSystem,
			List<AccIdentityAccountDto> identityAccountsToCreate) {

		// We try find account for same UID on same system
		// First we try to search same account in list for create new accounts
		AccIdentityAccountDto sameAccount = identityAccountsToCreate.stream() //
				.filter(identityAccountToCreate -> {
					AccAccountDto account = DtoUtils.getEmbedded(identityAccountToCreate, AccIdentityAccount_.account.getName(), AccAccountDto.class);
					
					return account.getUid().equals(uid) 
							&& roleSystem.getId().equals(identityAccountToCreate.getRoleSystem());
				}).findFirst() //
				.orElse(null); //

		if (sameAccount != null) {
			return DtoUtils.getEmbedded(sameAccount, AccIdentityAccount_.account.getName(), AccAccountDto.class);
		}
		
		// If account is not in the list accounts to create, then we will search in
		// database

		// Account management - can be the account created? - execute the script on the
		// system mapping
		SysSystemDto system = DtoUtils.getEmbedded(roleSystem, SysRoleSystem_.system);
		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(system.getId(),
				SystemEntityType.IDENTITY);
		if (mapping == null) {
			return null;
		}
		if (!this.canBeAccountCreated(uid, identity, mapping, system)) {
			LOG.info(MessageFormat.format(
					"For entity [{0}] and entity type [{1}] cannot be created the account (on system [{2}]),"
							+ " because script \"Can be account created\" on the mapping returned \"false\"!",
					identity.getCode(), SystemEntityType.IDENTITY, system.getName()));
			return null;
		}

		AccAccountFilter accountFilter = new AccAccountFilter();
		accountFilter.setUid(uid);
		accountFilter.setSystemId(roleSystem.getSystem());
		List<AccAccountDto> sameAccounts = accountService.find(accountFilter, null).getContent();
		if (CollectionUtils.isEmpty(sameAccounts)) {
			// Create and persist new account
			return createAccount(uid, roleSystem);
		} else {
			// We use existed account
			return sameAccounts.get(0);
		}

	}

	private AccAccountDto createAccount(String uid, SysRoleSystemDto roleSystem) {
		AccAccountDto account = new AccAccountDto();
		account.setUid(uid);
		account.setEntityType(SystemEntityType.IDENTITY);
		account.setAccountType(AccountType.PERSONAL);
		account.setSystem(roleSystem.getSystem());
		
		return accountService.save(account);
	}

	private boolean canBeAccountCreated(String uid, IdmIdentityDto dto, SysSystemMappingDto mapping,
			SysSystemDto system) {
		return systemMappingService.canBeAccountCreated(uid, dto, mapping.getCanBeAccountCreatedScript(), system);
	}
	
	/**
	 * Try to find identity-account for this identity-role and system. If exists and doesn't in
	 * list of identity-account to delete, then we are done.
	 * 
	 * @param identityAccountList
	 * @param identityAccountsToDelete
	 * @param identityRole
	 * @param roleSystem 
	 * @return
	 */
	private AccIdentityAccountDto findAlreadyExistsIdentityAccount(List<AccIdentityAccountDto> identityAccountList,
			List<AccIdentityAccountDto> identityAccountsToDelete, IdmIdentityRoleDto identityRole, SysRoleSystemDto roleSystem) {
		return identityAccountList.stream() //
				.filter(identityAccount -> {
			if (identityRole.getId().equals(identityAccount.getIdentityRole())
					&& !identityAccountsToDelete.contains(identityAccount)) {
				AccAccountDto account = DtoUtils.getEmbedded(identityAccount, AccIdentityAccount_.account.getName(), AccAccountDto.class);
				Assert.notNull(account.getSystem(), "System is required for account.");
				if(account.getSystem().equals(roleSystem.getSystem())) {
					return true;
				}
			}
			return false;
		}).findFirst().orElse(null);
	}
	
	/**
	 * Create identity-account, but first check if this identity-account already
	 * exist, if yes then is only his account ID add to accounts (for provisioning).
	 * 
	 * @param accounts
	 * @param identityAccount
	 */
	private void createIdentityAccountIfNotExists(List<UUID> accounts, AccIdentityAccountDto identityAccount) {
		AccIdentityAccountFilter identityAccountFilter = new AccIdentityAccountFilter();
		identityAccountFilter.setIdentityId(identityAccount.getIdentity());
		identityAccountFilter.setIdentityRoleId(identityAccount.getIdentityRole());
		identityAccountFilter.setAccountId(identityAccount.getAccount());
		identityAccountFilter.setRoleSystemId(identityAccount.getRoleSystem());
		// Check if on exist same identity-account (for same identity-role, account and
		// role-system)
		long count = identityAccountService.count(identityAccountFilter);
		if (count == 0) {
			AccIdentityAccountDto identityAccountDto = identityAccountService.save(identityAccount);
			accounts.add(identityAccountDto.getAccount());
		} else {
			// If this identity-account already exists, then we need to add his account ID
			// (for execute the provisioning).
			accounts.add(identityAccountService.find(identityAccountFilter, null).getContent().get(0).getAccount());
		}
	}
	
	/**
	 * Method for noting identity-accounts for delayed account management or delete.
	 */
	private void notingIdentityAccountForDelayedAcm(EntityEvent<IdmIdentityRoleDto> event,
			AccIdentityAccountDto identityAccount, String key) {
		Assert.notNull(identityAccount, "Identity account is required.");
		Assert.notNull(identityAccount.getId(), "Identity account identifier is required.");

		if (!event.getProperties().containsKey(key)) {
			event.getProperties().put(key,
					new HashSet<UUID>());
		}

		@SuppressWarnings("unchecked")
		Set<UUID> ids = (Set<UUID>) event.getProperties()
				.get(key);

		if (key.equals(IdmAccountDto.ACCOUNT_FOR_ADDITIONAL_PROVISIONING)) {
			// Add single account to parent event
			ids.add(identityAccount.getAccount());
		} else {
			// Add single identity-account to parent event
			ids.add(identityAccount.getId());
		}
	}
}
