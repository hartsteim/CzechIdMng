package eu.bcvsolutions.idm.acc.security.evaluator;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.acc.eav.entity.AccAccountFormValue;
import eu.bcvsolutions.idm.core.security.evaluator.eav.AbstractFormValueEvaluator;

/**
 * Evaluator for AccountFormValue
 * @author Jan Potočiar
 */
@Component(eu.bcvsolutions.idm.acc.security.evaluator.AccAccountFormValueEvaluator.EVALUATOR_NAME)
@Description("Permissions to account form attribute values.")
public class AccAccountFormValueEvaluator extends AbstractFormValueEvaluator<AccAccountFormValue> {

	public static final String EVALUATOR_NAME = "acc-account-form-value-evaluator";

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
}


