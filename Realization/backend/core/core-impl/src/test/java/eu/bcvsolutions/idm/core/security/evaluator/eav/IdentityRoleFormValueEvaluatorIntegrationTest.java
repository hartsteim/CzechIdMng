package eu.bcvsolutions.idm.core.security.evaluator.eav;

import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmIdentityRoleFormValue;
import eu.bcvsolutions.idm.core.security.api.domain.GroupPermission;
import eu.bcvsolutions.idm.test.api.AbstractFormValueEvaluatorIntegrationTest;

public class IdentityRoleFormValueEvaluatorIntegrationTest extends AbstractFormValueEvaluatorIntegrationTest<IdmIdentityRole, IdmIdentityRoleFormValue, IdentityRoleFormValueEvaluator> {
	@Override
	protected GroupPermission getSpecificGroupPermission() {
		return CoreGroupPermission.AUTHORIZATIONPOLICY;
	}

	@Override
	protected Identifiable createSpecificOwner() {
		return identityRole;
	}
}
