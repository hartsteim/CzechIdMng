package eu.bcvsolutions.idm.acc.security.evaluator;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.acc.entity.SysSystemFormValue;
import eu.bcvsolutions.idm.core.security.evaluator.eav.AbstractFormValueEvaluator;

/**
 *
 * @author jan
 *
 */
@Component(eu.bcvsolutions.idm.acc.security.evaluator.SystemFormValueEvaluator.EVALUATOR_NAME)
@Description("Permissions to system form attribute values.")
public class SystemFormValueEvaluator extends AbstractFormValueEvaluator<SysSystemFormValue> {

	public static final String EVALUATOR_NAME = "system-form-value-evaluator";

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
}


