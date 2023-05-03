package eu.bcvsolutions.idm.core.security.evaluator.eav;

import java.util.List;


import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.eav.api.domain.BaseFaceType;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmIdentityFormValue;

/**
 * Permissions to form attribute values.
 * 
 * @author Radek Tomiška
 *
 */
@Component(IdentityFormValueEvaluator.EVALUATOR_NAME)
@Description("Permissions to identity form attribute values. By definition (main if not specified)"
		+ " and attrinute code (all if not specified).")
public class IdentityFormValueEvaluator extends AbstractFormValueEvaluator<IdmIdentityFormValue> {
	
	public static final String EVALUATOR_NAME = "identity-form-value-evaluator";
	
	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	public List<String> getPropertyNames() {
		List<String> parameters = super.getPropertyNames();
		parameters.add(PARAMETER_FORM_DEFINITION);
		parameters.add(PARAMETER_FORM_ATTRIBUTES);
		parameters.add(PARAMETER_SELF_ONLY); // <--- add self-only parameter
		parameters.add(PARAMETER_OWNER_UPDATE);
		parameters.add(PARAMETER_OWNER_READ);
		return parameters;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		return Lists.newArrayList(
				new IdmFormAttributeDto(PARAMETER_FORM_DEFINITION, PARAMETER_FORM_DEFINITION, PersistentType.UUID, BaseFaceType.FORM_DEFINITION_SELECT),
				new IdmFormAttributeDto(PARAMETER_FORM_ATTRIBUTES, PARAMETER_FORM_ATTRIBUTES, PersistentType.SHORTTEXT),
				new IdmFormAttributeDto(PARAMETER_SELF_ONLY, PARAMETER_SELF_ONLY, PersistentType.BOOLEAN), // <--- add self-only parameter
				new IdmFormAttributeDto(PARAMETER_OWNER_UPDATE, PARAMETER_OWNER_UPDATE, PersistentType.BOOLEAN),
				new IdmFormAttributeDto(PARAMETER_OWNER_READ, PARAMETER_OWNER_READ, PersistentType.BOOLEAN)
		);
	}
}
