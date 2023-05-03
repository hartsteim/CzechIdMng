package eu.bcvsolutions.idm.acc.bulk.action.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.domain.AccGroupPermission;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccAccountFilter;
import eu.bcvsolutions.idm.acc.event.AccountEvent;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.core.api.bulk.action.AbstractRemoveBulkAction;
import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.PriorityType;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.event.EntityEventProcessor;
import eu.bcvsolutions.idm.core.api.service.ReadWriteDtoService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;

@Component("accountDeleteBulkAction")
@Description("Delete given account")
public class AccountDeleteBulkAction extends AbstractRemoveBulkAction<AccAccountDto, AccAccountFilter> {

	public static final String NAME = "account-delete-bulk-action";
	
	@Autowired
	private AccAccountService accountService;
	@Autowired
	private SecurityService securityService;
	
	@Override
	public String getName() {
		return NAME;
	}

	@Override
	protected List<String> getAuthoritiesForEntity() {
		return Lists.newArrayList(AccGroupPermission.ACCOUNT_DELETE);
	}
	
	@Override
	public ReadWriteDtoService<AccAccountDto, AccAccountFilter> getService() {
		return accountService;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> formAttributes = super.getFormAttributes();
		//
		// add force delete, if currently logged user is ROLE_ADMIN
		if (securityService.hasAnyAuthority(CoreGroupPermission.ROLE_ADMIN)) {
			formAttributes.add(new IdmFormAttributeDto(EntityEventProcessor.PROPERTY_FORCE_DELETE, "Force delete", PersistentType.BOOLEAN));
		}
		//
		return formAttributes;
	}

	@Override
	protected OperationResult processDto(AccAccountDto account) {
		boolean forceDelete = getParameterConverter().toBoolean(getProperties(), EntityEventProcessor.PROPERTY_FORCE_DELETE, false);
		if (!forceDelete) {
			return super.processDto(account);
		}

		// force delete can execute role admin only
		getService().checkAccess(account, IdmBasePermission.ADMIN);
		//
		AccountEvent accountEvent = new AccountEvent(AccountEvent.AccountEventType.DELETE, account, new ConfigurationMap(getProperties()).toMap());
		accountEvent.setPriority(PriorityType.HIGH);

		accountService.publish(accountEvent);
		//
		return new OperationResult.Builder(OperationState.EXECUTED).build();
	}

}
