package eu.bcvsolutions.idm.acc.script.evaluator;

import org.springframework.stereotype.Service;

import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.core.api.domain.IdmScriptCategory;
import eu.bcvsolutions.idm.core.api.dto.IdmScriptDto;
import eu.bcvsolutions.idm.core.script.evaluator.AbstractScriptEvaluator;

/**
 * Default evaluator for {@link IdmScriptCategory} TRANSFORM_FROM resource.
 *  
 * @author Ondrej Kopr <kopr@xyxy.cz>
 *
 */
@Service("transformFromResourceEvaluator")
public class DefaultTransformFromResourceEvaluator extends AbstractScriptEvaluator {

	@Override
	public boolean supports(IdmScriptCategory category) {
		return category == IdmScriptCategory.TRANSFORM_FROM;
	}

	@Override
	public String generateTemplate(IdmScriptDto script) {
		StringBuilder example = new StringBuilder();
		example.append("// Inserted script: " + script.getCode() + "\n");
		example.append("/* Description:\n");
		example.append(script.getDescription());
		example.append("\n");
		example.append("*/\n");
		example.append(SCRIPT_EVALUATOR + ".evaluate(\n");
		example.append("    " + SCRIPT_EVALUATOR + ".newBuilder()\n");
		example.append("        .setScriptCode('" + script.getCode() + "')\n");
		example.append("        .addParameter('" + SCRIPT_EVALUATOR + "', " + SCRIPT_EVALUATOR + ")\n");
		example.append("        .addParameter('" + SysSystemAttributeMappingService.ATTRIBUTE_VALUE_KEY + "', " + SysSystemAttributeMappingService.ATTRIBUTE_VALUE_KEY + ")\n");
		example.append("        .addParameter('" + SysSystemAttributeMappingService.ENTITY_KEY + "', " + SysSystemAttributeMappingService.ENTITY_KEY + ")\n");
		example.append("	.build());\n");
		return example.toString();
	}
}
