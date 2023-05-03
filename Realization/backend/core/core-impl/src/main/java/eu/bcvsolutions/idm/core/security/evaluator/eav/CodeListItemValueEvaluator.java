package eu.bcvsolutions.idm.core.security.evaluator.eav;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.eav.entity.IdmCodeListItemValue;

/**
 *
 * @author Jan Potočiar
 *
 */
@Component(CodeListItemValueEvaluator.EVALUATOR_NAME)
@Description("Permissions to code list item value form attribute values.")
public class CodeListItemValueEvaluator extends AbstractFormValueEvaluator<IdmCodeListItemValue> {

	public static final String EVALUATOR_NAME = "code-list-item-value-evaluator";

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
}

