package eu.bcvsolutions.idm.acc.event.processor;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.dto.AccAccountConceptRoleRequestDto;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccAccountRoleAssignmentDto;
import eu.bcvsolutions.idm.acc.dto.AccContractAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccContractSliceAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccRoleAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccRoleCatalogueAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccTreeAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccAccountConceptRoleRequestFilter;
import eu.bcvsolutions.idm.acc.dto.filter.AccContractAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.AccContractSliceAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.AccIdentityAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.AccRoleAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.AccRoleCatalogueAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.AccTreeAccountFilter;
import eu.bcvsolutions.idm.acc.entity.AccAccount_;
import eu.bcvsolutions.idm.acc.event.AccountEvent.AccountEventType;
import eu.bcvsolutions.idm.acc.service.api.AccAccountConceptRoleRequestService;
import eu.bcvsolutions.idm.acc.service.api.AccAccountRoleAssignmentService;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccContractAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccContractSliceAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccRoleAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccRoleCatalogueAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccTreeAccountService;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityTypeManager;
import eu.bcvsolutions.idm.acc.system.entity.SystemEntityTypeRegistrable;
import eu.bcvsolutions.idm.core.api.event.CoreEvent;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EntityEventProcessor;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.model.event.processor.ConceptCancellingProcessor;

/**
 * Deletes identity account
 * 
 * @author Vít Švanda
 */
@Component("accAccountDeleteProcessor")
@Description("Ensures referential integrity. Cannot be disabled.")
public class AccountDeleteProcessor
		extends ConceptCancellingProcessor<AccAccountDto, AccAccountConceptRoleRequestDto, AccAccountConceptRoleRequestFilter, AccAccountRoleAssignmentDto>
		implements AccountProcessor {

	private static final String PROCESSOR_NAME = "account-delete-processor";
	private final AccAccountService accountService;
	private final AccIdentityAccountService identityAccountService;
	private final AccRoleAccountService roleAccountService;
	private final AccTreeAccountService treeAccountService;
	private final AccContractAccountService contractAccountService;
	private final AccRoleCatalogueAccountService roleCatalogueAccountService;
	private final ProvisioningService provisioningService;
	@Autowired
	private AccContractSliceAccountService contractAccountSliceService;
	@Autowired private SysSystemEntityTypeManager systemEntityManager;

	@Autowired private AccAccountRoleAssignmentService accountRoleAssignmentService;

	private static final Logger LOG = LoggerFactory.getLogger(AccountDeleteProcessor.class);

	@Autowired
	public AccountDeleteProcessor(AccAccountService accountService, EntityEventManager entityEventManager,
			AccRoleAccountService roleAccountService, AccTreeAccountService treeAccountService,
			AccContractAccountService contractAccountService,
			AccRoleCatalogueAccountService roleCatalogueAccountService,
			AccIdentityAccountService identityAccountService, ProvisioningService provisioningService, AccAccountConceptRoleRequestService accountConceptRoleRequestService,
			AccAccountRoleAssignmentService accountRoleAssignmentService, IdmRoleRequestService roleRequestService) {
		super(accountConceptRoleRequestService, accountRoleAssignmentService, roleRequestService,AccountEventType.DELETE);
		//
		Assert.notNull(accountService, "Service is required.");
		Assert.notNull(entityEventManager, "Manager is required.");
		Assert.notNull(roleAccountService, "Service is required.");
		Assert.notNull(roleCatalogueAccountService, "Service is required.");
		Assert.notNull(treeAccountService, "Service is required.");
		Assert.notNull(contractAccountService, "Service is required.");
		Assert.notNull(identityAccountService, "Service is required.");
		Assert.notNull(provisioningService, "Service is required.");
		//
		this.accountService = accountService;
		this.roleAccountService = roleAccountService;
		this.roleCatalogueAccountService = roleCatalogueAccountService;
		this.treeAccountService = treeAccountService;
		this.contractAccountService = contractAccountService;
		this.identityAccountService = identityAccountService;
		this.provisioningService = provisioningService;
	}

	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	@Override
	public EventResult<AccAccountDto> process(EntityEvent<AccAccountDto> event) {
		AccAccountDto account = event.getContent();
		UUID entityId = null;
		Object entityIdObj = event.getProperties().get(AccAccountService.ENTITY_ID_PROPERTY);
		if (entityIdObj instanceof UUID) {
			entityId = (UUID) entityIdObj;
		}
		boolean deleteTargetAccount = false;
		Object deleteTargetAccountObj = event.getProperties().get(AccAccountService.DELETE_TARGET_ACCOUNT_PROPERTY);
		if (deleteTargetAccountObj instanceof Boolean) {
			deleteTargetAccount = (boolean) deleteTargetAccountObj;
		}

		Assert.notNull(account, "Account cannot be null!");
		// We do not allow delete account in protection unless force delete is set
		boolean forceDelete = false;
		Object forceDeleteObj = event.getProperties().get(EntityEventProcessor.PROPERTY_FORCE_DELETE);
		if (forceDeleteObj instanceof Boolean) {
			forceDelete = (boolean) forceDeleteObj;
		}
		if (account.isAccountProtectedAndValid() && !forceDelete) {
			throw new ResultCodeException(AccResultCode.ACCOUNT_CANNOT_BE_DELETED_IS_PROTECTED,
					ImmutableMap.of("uid", account.getUid()));
		}

		// Find all concepts and remove relation on contract
		removeRelatedConcepts(account.getId());
		// delete all identity accounts
		AccIdentityAccountFilter identityAccountFilter = new AccIdentityAccountFilter();
		identityAccountFilter.setAccountId(account.getId());
		List<AccIdentityAccountDto> identityAccounts = identityAccountService.find(identityAccountFilter, null)
				.getContent();
		for (AccIdentityAccountDto identityAccount : identityAccounts) {
			// delete referenced role assignment
			removeRelatedAssignedRoles(event,identityAccount.getIdentity(), account.getId(), false);
			if (forceDelete) {
				identityAccountService.forceDelete(identityAccount);
			} else {
				identityAccountService.delete(identityAccount, deleteTargetAccount);
			}
		}
		removeRelatedAssignedRoles(event, account.getId(), false);

		// delete all role accounts
		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setAccountId(account.getId());
		List<AccRoleAccountDto> roleAccounts = roleAccountService.find(roleAccountFilter, null).getContent();
		for (AccRoleAccountDto roleAccount : roleAccounts) {
			roleAccountService.delete(roleAccount, deleteTargetAccount);
		}

		// delete all roleCatalogue accounts
		AccRoleCatalogueAccountFilter roleCatalogueAccountFilter = new AccRoleCatalogueAccountFilter();
		roleCatalogueAccountFilter.setAccountId(account.getId());
		List<AccRoleCatalogueAccountDto> roleCatalogueAccounts = roleCatalogueAccountService
				.find(roleCatalogueAccountFilter, null).getContent();
		for (AccRoleCatalogueAccountDto roleCatalogueAccount : roleCatalogueAccounts) {
			roleCatalogueAccountService.delete(roleCatalogueAccount, deleteTargetAccount);
		}

		// delete all tree accounts
		AccTreeAccountFilter treeAccountFilter = new AccTreeAccountFilter();
		treeAccountFilter.setAccountId(account.getId());
		List<AccTreeAccountDto> treeAccounts = treeAccountService.find(treeAccountFilter, null).getContent();
		for (AccTreeAccountDto treeAccount : treeAccounts) {
			treeAccountService.delete(treeAccount, deleteTargetAccount);
		}

		// delete all contract accounts
		AccContractAccountFilter contractAccountFilter = new AccContractAccountFilter();
		contractAccountFilter.setAccountId(account.getId());
		List<AccContractAccountDto> contractAccounts = contractAccountService.find(contractAccountFilter, null)
				.getContent();
		for (AccContractAccountDto contractAccount : contractAccounts) {
			contractAccountService.delete(contractAccount, deleteTargetAccount);
		}

		// delete all contract slice accounts
		AccContractSliceAccountFilter contractSliceAccountFilter = new AccContractSliceAccountFilter();
		contractSliceAccountFilter.setAccountId(account.getId());
		List<AccContractSliceAccountDto> contractSliceAccounts = contractAccountSliceService.find(contractSliceAccountFilter, null)
				.getContent();
		for (AccContractSliceAccountDto contractSliceAccount : contractSliceAccounts) {
			contractAccountSliceService.delete(contractSliceAccount, deleteTargetAccount);
		}

		//
		AccAccountDto refreshAccount = accountService.get(account.getId());
		// If account still exists (was not deleted by entity-account), we delete him
		// directly now
		if (refreshAccount != null) {
			accountService.deleteInternal(refreshAccount);
		}
		if (deleteTargetAccount && account.getEntityType() != null) {
			String entityType = account.getEntityType();
			SystemEntityTypeRegistrable systemEntityType = systemEntityManager.getSystemEntityByCode(entityType);
			if (!systemEntityType.isSupportsProvisioning()) {
				LOG.warn(MessageFormat.format("Provisioning is not supported for [{1}] now [{0}]!", account.getUid(),
						entityType));
				return new DefaultEventResult<>(event, this);
			}
			LOG.debug(MessageFormat.format("Call delete provisioning for account with UID [{0}] and entity ID [{1}].", account.getUid(),
					entityId));
			// Create context for systemEntity in account DTO and set ID of role-request to it.
			UUID roleRequestId = this.getRoleRequestIdProperty(event.getProperties());
			this.initContext(account, roleRequestId);
			
			this.provisioningService.doDeleteProvisioning(account, account.getEntityType(), entityId);
		}

		return new DefaultEventResult<>(event, this);
	}


	@Override
	public int getOrder() {
		return CoreEvent.DEFAULT_ORDER;
	}

	@Override
	public boolean isDisableable() {
		return false;
	}
	
	/**
	 * Create context for systemEntity in account DTO and set ID of role-request to
	 * it.
	 * 
	 * @param account
	 * @param requestId
	 */
	private void initContext(AccAccountDto account, UUID requestId) {
		SysSystemEntityDto systemEntityDto = DtoUtils.getEmbedded(account, AccAccount_.systemEntity.getName(),
				SysSystemEntityDto.class, null);
		if (systemEntityDto == null) {
			return;
		}
		Map<String, Object> context = systemEntityDto.getContext();
		if (context == null) {
			context = new HashMap<String, Object>();
		}
		context.put(IdmRoleRequestService.ROLE_REQUEST_ID_KEY, requestId);
		systemEntityDto.setContext(context);
	}
	
	/**
	 * Get role-request ID from event
	 * 
	 * @param properties
	 */
	private UUID getRoleRequestIdProperty(Map<String, Serializable> properties) {
		Serializable requestIdObj = properties.get(IdmRoleRequestService.ROLE_REQUEST_ID_KEY);
		if (requestIdObj instanceof UUID) {
			return (UUID) requestIdObj;
		}
		return null;
	}

}
