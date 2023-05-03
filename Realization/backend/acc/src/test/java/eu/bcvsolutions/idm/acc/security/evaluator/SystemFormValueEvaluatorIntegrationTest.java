package eu.bcvsolutions.idm.acc.security.evaluator;

import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.TestHelper;
import eu.bcvsolutions.idm.acc.domain.AccGroupPermission;
import eu.bcvsolutions.idm.acc.entity.SysSystem;
import eu.bcvsolutions.idm.acc.entity.SysSystemFormValue;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.security.api.domain.GroupPermission;
import eu.bcvsolutions.idm.test.api.AbstractFormValueEvaluatorIntegrationTest;

public class SystemFormValueEvaluatorIntegrationTest extends AbstractFormValueEvaluatorIntegrationTest<SysSystem, SysSystemFormValue, SystemFormValueEvaluator> {
	@Autowired
	private TestHelper helper;

	@Override
	protected GroupPermission getSpecificGroupPermission() {
		return AccGroupPermission.SYSTEM;
	}

	@Override
	protected Identifiable createSpecificOwner() {
		return helper.createTestResourceSystem(true);
	}
}
