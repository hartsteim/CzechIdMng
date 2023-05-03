package eu.bcvsolutions.idm.core.security.evaluator.eav;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.model.entity.eav.IdmConceptRoleRequestFormValue;

/**
 *
 * @author Jan Potočiar
 *
 */
@Component(ConceptRoleRequestFormValueEvaluator.EVALUATOR_NAME)
@Description("Permissions to concept role request form attribute values.")
public class ConceptRoleRequestFormValueEvaluator extends AbstractFormValueEvaluator<IdmConceptRoleRequestFormValue> {

	public static final String EVALUATOR_NAME = "concept-role-request-form-value-evaluator";

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
}

