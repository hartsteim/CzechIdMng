package eu.bcvsolutions.idm.core.security.evaluator.eav;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.model.entity.eav.IdmTreeNodeFormValue;

/**
 *
 * @author Jan Potočiar
 *
 */
@Component(TreeNodeFormValueEvaluator.EVALUATOR_NAME)
@Description("Permissions to tree node form attribute values.")
public class TreeNodeFormValueEvaluator extends AbstractFormValueEvaluator<IdmTreeNodeFormValue> {

	public static final String EVALUATOR_NAME = "tree-node-form-value-evaluator";

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
}


