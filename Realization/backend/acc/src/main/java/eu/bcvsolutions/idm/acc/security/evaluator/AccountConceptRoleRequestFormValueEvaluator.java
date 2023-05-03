package eu.bcvsolutions.idm.acc.security.evaluator;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.acc.entity.AccAccountConceptRoleRequestFormValue;
import eu.bcvsolutions.idm.core.security.evaluator.eav.AbstractFormValueEvaluator;

/**
 *
 * @author jan
 *
 */
@Component(eu.bcvsolutions.idm.acc.security.evaluator.AccountConceptRoleRequestFormValueEvaluator.EVALUATOR_NAME)
@Description("Permissions to account concept role form attribute values.")
public class AccountConceptRoleRequestFormValueEvaluator extends AbstractFormValueEvaluator<AccAccountConceptRoleRequestFormValue> {

	public static final String EVALUATOR_NAME = "account-concept-role-request-form-value-evaluator";

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
}


