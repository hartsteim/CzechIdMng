package eu.bcvsolutions.idm.acc.security.evaluator;

import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.TestHelper;
import eu.bcvsolutions.idm.acc.domain.AccGroupPermission;
import eu.bcvsolutions.idm.acc.dto.AccAccountConceptRoleRequestDto;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.entity.AccAccountConceptRoleRequest;
import eu.bcvsolutions.idm.acc.entity.AccAccountConceptRoleRequestFormValue;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.security.api.domain.GroupPermission;
import eu.bcvsolutions.idm.test.api.AbstractFormValueEvaluatorIntegrationTest;

public class AccountConceptRoleRequestFormValueEvaluatorIntegrationTest extends AbstractFormValueEvaluatorIntegrationTest<AccAccountConceptRoleRequest, AccAccountConceptRoleRequestFormValue, AccountConceptRoleRequestFormValueEvaluator> {
	@Autowired
	private TestHelper helper;
	@Autowired
	private AccAccountService accountService;

	@Override
	protected GroupPermission getSpecificGroupPermission() {
		return AccGroupPermission.ACCOUNT;
	}

	@Override
	protected Identifiable createSpecificOwner() {
		SysSystemDto system = helper.createTestResourceSystem(true);
		AccAccountDto account = new AccAccountDto();
		account.setSystem(system.getId());
		account.setUid(identity.getUsername());
		account = accountService.save(account);
		IdmRoleRequestDto request = helper.createRoleRequest(identity.getId(), false);
		AccAccountConceptRoleRequestDto accountConceptRoleRequest = helper.createAccountConceptRoleRequest(request.getId(), role.getId(), account.getId());
		return accountConceptRoleRequest;
	}
}
