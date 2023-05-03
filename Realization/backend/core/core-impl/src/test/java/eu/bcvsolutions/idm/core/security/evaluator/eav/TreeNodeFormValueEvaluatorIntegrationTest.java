package eu.bcvsolutions.idm.core.security.evaluator.eav;

import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmTreeNode;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmTreeNodeFormValue;
import eu.bcvsolutions.idm.core.security.api.domain.GroupPermission;
import eu.bcvsolutions.idm.test.api.AbstractFormValueEvaluatorIntegrationTest;

public class TreeNodeFormValueEvaluatorIntegrationTest extends AbstractFormValueEvaluatorIntegrationTest<IdmTreeNode, IdmTreeNodeFormValue, TreeNodeFormValueEvaluator> {
	@Override
	protected GroupPermission getSpecificGroupPermission() {
		return CoreGroupPermission.AUTHORIZATIONPOLICY;
	}

	@Override
	protected Identifiable createSpecificOwner() {
		return getHelper().createTreeNode();
	}
}
