package eu.bcvsolutions.idm.vs.security.evaluator;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.security.evaluator.eav.AbstractFormValueEvaluator;
import eu.bcvsolutions.idm.vs.entity.VsAccountFormValue;

/**
 *
 * @author jan
 *
 */
@Component(eu.bcvsolutions.idm.vs.security.evaluator.VsAccountFormValueEvaluator.EVALUATOR_NAME)
@Description("Permissions to account form attribute values.")
public class VsAccountFormValueEvaluator extends AbstractFormValueEvaluator<VsAccountFormValue> {

	public static final String EVALUATOR_NAME = "vs-account-form-value-evaluator";

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
}



