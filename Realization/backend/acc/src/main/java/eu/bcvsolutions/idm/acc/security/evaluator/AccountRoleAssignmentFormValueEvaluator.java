package eu.bcvsolutions.idm.acc.security.evaluator;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.acc.entity.AccAccountRoleAssignmentFormValue;
import eu.bcvsolutions.idm.core.security.evaluator.eav.AbstractFormValueEvaluator;

/**
 *
 * @author jan
 *
 */
@Component(eu.bcvsolutions.idm.acc.security.evaluator.AccountRoleAssignmentFormValueEvaluator.EVALUATOR_NAME)
@Description("Permissions to account role assignment form attribute values.")
public class AccountRoleAssignmentFormValueEvaluator extends AbstractFormValueEvaluator<AccAccountRoleAssignmentFormValue> {

	public static final String EVALUATOR_NAME = "account-role-assignment-form-value-evaluator";

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
}


