package eu.bcvsolutions.idm.core.security.evaluator.eav;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.model.entity.eav.IdmContractSliceFormValue;

/**
 *
 * @author Jan Potočiar
 *
 */
@Component(ContractSliceFormValueEvaluator.EVALUATOR_NAME)
@Description("Permissions to contract slice form attribute values.")
public class ContractSliceFormValueEvaluator extends AbstractFormValueEvaluator<IdmContractSliceFormValue> {

	public static final String EVALUATOR_NAME = "contract-slice-form-value-evaluator";

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
}

