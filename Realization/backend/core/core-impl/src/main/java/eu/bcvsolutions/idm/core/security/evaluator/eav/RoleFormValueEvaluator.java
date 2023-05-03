package eu.bcvsolutions.idm.core.security.evaluator.eav;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.model.entity.eav.IdmRoleFormValue;

/**
 *
 * @author Jan Potočiar
 *
 */
@Component(RoleFormValueEvaluator.EVALUATOR_NAME)
@Description("Permissions to role form attribute values.")
public class RoleFormValueEvaluator extends AbstractFormValueEvaluator<IdmRoleFormValue> {

	public static final String EVALUATOR_NAME = "role-form-value-evaluator";

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
}

