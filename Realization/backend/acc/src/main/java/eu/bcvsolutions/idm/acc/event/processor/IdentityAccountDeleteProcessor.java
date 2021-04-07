package eu.bcvsolutions.idm.acc.event.processor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccIdentityAccountFilter;
import eu.bcvsolutions.idm.acc.event.AccountEvent;
import eu.bcvsolutions.idm.acc.event.AccountEvent.AccountEventType;
import eu.bcvsolutions.idm.acc.event.IdentityAccountEvent.IdentityAccountEventType;
import eu.bcvsolutions.idm.acc.event.ProvisioningEvent;
import eu.bcvsolutions.idm.acc.event.ProvisioningEvent.ProvisioningEventType;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.core.api.event.CoreEvent;
import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;

/**
 * Deletes identity account
 * 
 * @author Svanda
 */
@Component("accIdentityAccountDeleteProcessor")
@Description("Ensures referential integrity. Cannot be disabled.")
public class IdentityAccountDeleteProcessor extends CoreEventProcessor<AccIdentityAccountDto> {

	private static final String PROCESSOR_NAME = "identity-account-delete-processor";
	//
	@Autowired private AccIdentityAccountService service;
	@Autowired private AccAccountService accountService;
	@Autowired private SysSystemMappingService systemMappingService;
	@Autowired private EntityEventManager entityEventManager;
	@Autowired private IdmIdentityService identityService;

	public IdentityAccountDeleteProcessor() {
		super(IdentityAccountEventType.DELETE);
	}

	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	@Override
	public EventResult<AccIdentityAccountDto> process(EntityEvent<AccIdentityAccountDto> event) {
		AccIdentityAccountDto entity = event.getContent();
		UUID account = entity.getAccount();
		AccAccountDto accountDto = accountService.get(account);
		Assert.notNull(accountDto, "Account cannot be null!");

		// We check if exists another (ownership) identity-accounts, if not
		// then we will delete account
		AccIdentityAccountFilter identityAccountFilter = new AccIdentityAccountFilter();
		identityAccountFilter.setAccountId(account);
		identityAccountFilter.setOwnership(Boolean.TRUE);
		identityAccountFilter.setNotIdentityAccount(entity.getId());

		boolean moreIdentityAccounts = service.find(identityAccountFilter, PageRequest.of(0, 1))
				.getTotalElements() > 0;
		boolean deleteTargetAccount = (boolean) event.getProperties()
				.get(AccIdentityAccountService.DELETE_TARGET_ACCOUNT_KEY);
		boolean deleteAccAccount = true;

		// If is account in protection, then we will not delete
		// identity-account
		// But is here exception from this. When is presented
		// attribute FORCE_DELETE_OF_IDENTITY_ACCOUNT_KEY, then
		// we will do delete of identity-account (it is important
		// for integrity ... for example during delete of whole
		// identity).
		boolean forceDeleteIdentityAccount = isForceDeleteAttributePresent(event.getProperties());
		if (!moreIdentityAccounts && entity.isOwnership()) {
			if (accountDto.isAccountProtectedAndValid()) {
				if (forceDeleteIdentityAccount) {
					// Target account and AccAccount will NOT be deleted!
					deleteAccAccount = false;
				} else {
					throw new ResultCodeException(AccResultCode.ACCOUNT_CANNOT_BE_DELETED_IS_PROTECTED,
							ImmutableMap.of("uid", accountDto.getUid()));
				}
				// Is account protection activated on system mapping?
				// Set account as protected we can only on account without protection (event has already invalid protection)!
			} else if (!accountDto.isInProtection() && systemMappingService.isEnabledProtection(accountDto)) {
				// This identity account is last ... protection will be
				// activated
				activateProtection(accountDto);
				accountDto = accountService.save(accountDto);
				entity.setRoleSystem(null);
				entity.setIdentityRole(null);
				service.save(entity);
				doProvisioningSkipAccountProtection(accountDto, entity.getEntity());

				// If is account in protection, then we will not delete
				// identity-account
				if (forceDeleteIdentityAccount) {
					// Target account and AccAccount will NOT be deleted!
					deleteAccAccount = false;
				} else {
					return new DefaultEventResult<>(event, this);
				}
			}
		}
		service.deleteInternal(entity);
		// Finally we can delete AccAccount
		if (!moreIdentityAccounts && entity.isOwnership() && deleteAccAccount) {
			// We delete all NOT ownership identity accounts first
			AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
			filter.setAccountId(account);
			filter.setOwnership(Boolean.FALSE);
			// Find NOT ownership identity accounts
			service.find(filter, null).getContent().stream()
					.filter(identityAccount -> !identityAccount.isOwnership() && !identityAccount.equals(entity))
					.forEach(identityAccount -> {
						service.delete(identityAccount);
					});
			UUID roleRequestId = this.getRoleRequestIdProperty(event.getProperties());
			Map<String, Serializable> properties = new HashMap<String, Serializable>();
			properties.put(AccAccountService.DELETE_TARGET_ACCOUNT_PROPERTY, deleteTargetAccount);
			properties.put(AccAccountService.ENTITY_ID_PROPERTY, entity.getEntity());
		
			if (roleRequestId != null) {
				properties.put(IdmRoleRequestService.ROLE_REQUEST_ID_KEY, roleRequestId);
			}
			accountService.publish(new AccountEvent(AccountEventType.DELETE, accountDto, properties));
		}
		return new DefaultEventResult<>(event, this);
	}

	/**
	 * Activate protection for given account and set end of protection.
	 * Method has protected modifier -> can be override on projects.
	 */
	protected void activateProtection(AccAccountDto accountEntity) {
		Integer daysInterval = systemMappingService.getProtectionInterval(accountEntity);
		accountEntity.setInProtection(true);
		if (daysInterval == null) {
			// Interval is null, protection is infinite
			accountEntity.setEndOfProtection(null);
		} else {
			accountEntity.setEndOfProtection(ZonedDateTime.now().plusDays(daysInterval));
		}
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

	/**
	 * We need do provisioning (for example move account to archive)
	 * 
	 * @param account
	 * @param entity
	 */
	private void doProvisioningSkipAccountProtection(AccAccountDto account, UUID entity) {
		// TODO check propagtion request id!!!!!!!!!!!!
		entityEventManager.process(new ProvisioningEvent(ProvisioningEventType.START, account,
				ImmutableMap.of(ProvisioningService.DTO_PROPERTY_NAME, identityService.get(entity),
						ProvisioningService.CANCEL_PROVISIONING_BREAK_IN_PROTECTION, Boolean.TRUE)));

	}

	private boolean isForceDeleteAttributePresent(Map<String, Serializable> properties) {
		Object value = properties.get(AccIdentityAccountService.FORCE_DELETE_OF_IDENTITY_ACCOUNT_KEY);
		if (value instanceof Boolean && (Boolean) value) {
			return true;
		}
		return false;
	}

	@Override
	public int getOrder() {
		return CoreEvent.DEFAULT_ORDER;
	}

	@Override
	public boolean isDisableable() {
		return false;
	}

}
