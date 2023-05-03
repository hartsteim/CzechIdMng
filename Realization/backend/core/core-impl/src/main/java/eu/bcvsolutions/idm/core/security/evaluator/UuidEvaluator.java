package eu.bcvsolutions.idm.core.security.evaluator;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;

/**
 * Share entity with uuid.
 * 
 * @author Radek Tomiška
 *
 */
@Component(UuidEvaluator.EVALUATOR_NAME)
@Description("Share entity by uuid.")
public class UuidEvaluator extends AbstractUuidEvaluator<Identifiable> {

	public static final String EVALUATOR_NAME = "core-uuid-evaluator";

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
	@Override
	public boolean supports(Class<?> authorizableType) {
		Assert.notNull(authorizableType, "Authorizable type is required.");
		// uuid superclasses only
		return super.supports(authorizableType)
				&& (AbstractEntity.class.isAssignableFrom(authorizableType) || AbstractDto.class.isAssignableFrom(authorizableType));
	}
	
}
