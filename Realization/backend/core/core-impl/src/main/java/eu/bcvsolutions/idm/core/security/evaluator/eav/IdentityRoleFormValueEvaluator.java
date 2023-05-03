package eu.bcvsolutions.idm.core.security.evaluator.eav;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.model.entity.eav.IdmIdentityRoleFormValue;

/**
 *
 * @author Jan Potočiar
 *
 */
@Component(IdentityRoleFormValueEvaluator.EVALUATOR_NAME)
@Description("Permissions to identity role form attribute values.")
public class IdentityRoleFormValueEvaluator extends AbstractFormValueEvaluator<IdmIdentityRoleFormValue> {

	public static final String EVALUATOR_NAME = "identity-role-form-value-evaluator";

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
}
