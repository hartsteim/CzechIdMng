package eu.bcvsolutions.idm.vs.security.evaluator.eav;

import java.util.UUID;


import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.security.api.domain.GroupPermission;
import eu.bcvsolutions.idm.test.api.AbstractFormValueEvaluatorIntegrationTest;
import eu.bcvsolutions.idm.vs.TestHelper;
import eu.bcvsolutions.idm.vs.domain.VirtualSystemGroupPermission;
import eu.bcvsolutions.idm.vs.dto.VsAccountDto;
import eu.bcvsolutions.idm.vs.entity.VsAccount;
import eu.bcvsolutions.idm.vs.entity.VsAccountFormValue;
import eu.bcvsolutions.idm.vs.security.evaluator.VsAccountFormValueEvaluator;
import eu.bcvsolutions.idm.vs.service.api.VsAccountService;

public class VsAccountFormValueEvaluatorIntegrationTest extends AbstractFormValueEvaluatorIntegrationTest<VsAccount, VsAccountFormValue, VsAccountFormValueEvaluator> {
	@Autowired
	private TestHelper helper;
	@Autowired
	private VsAccountService accountService;

	@Before
	public void login() {
		loginAsAdmin();
	}

	@After
	public void logout() {
		super.logout();
	}

	@Override
	protected GroupPermission getSpecificGroupPermission() {
		return VirtualSystemGroupPermission.VSACCOUNT;
	}

	@Override
	protected Identifiable createSpecificOwner() {
		SysSystemDto system = helper.createVirtualSystem(helper.createName());
		VsAccountDto account = new VsAccountDto();
		account.setSystemId(system.getId());
		account.setUid(UUID.randomUUID().toString());
		account.setConnectorKey(getHelper().createName());
		VsAccountDto validatedAccount = accountService.validateDto(account);
		VsAccountDto savedAccount = accountService.save(validatedAccount);
		return savedAccount;
	}
}
