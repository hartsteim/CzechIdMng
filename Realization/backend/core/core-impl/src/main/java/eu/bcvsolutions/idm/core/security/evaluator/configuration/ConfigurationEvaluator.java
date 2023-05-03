package eu.bcvsolutions.idm.core.security.evaluator.configuration;

import java.util.List;
import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.model.entity.IdmConfiguration;
import eu.bcvsolutions.idm.core.model.entity.IdmConfiguration_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;

/**
 * Configurations
 * 
 * @author Radek Tomiška
 *
 */
@Component(ConfigurationEvaluator.EVALUATOR_NAME)
@Description("Permissions to application configurations. If secured is 'true', then secured configuration will be available too.")
public class ConfigurationEvaluator extends AbstractAuthorizationEvaluator<IdmConfiguration> {

	public static final String EVALUATOR_NAME = "core-configuration-evaluator";

	public static final String PARAMETER_SECURED = "secured";

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	public Predicate getPredicate(Root<IdmConfiguration> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasPermission(policy, permission)) {
			return null;
		}
		//
		boolean secured = isSecured(policy);
		if (secured) {
			return builder.conjunction();
		} else {
			return builder.isFalse(root.get(IdmConfiguration_.secured.getName()));
		}
	}
	
	@Override
	public Set<String> getPermissions(IdmConfiguration entity, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(entity, policy);
		if (entity == null) {
			return permissions;
		}	
		if (isSecured(policy) || !entity.isSecured()) {
			permissions.addAll(policy.getPermissions());
		}
		return permissions;
	}
	
	@Override
	public List<String> getPropertyNames() {
		List<String> parameters = super.getPropertyNames();
		parameters.add(PARAMETER_SECURED);
		return parameters;
	}
	
	private boolean isSecured(AuthorizationPolicy policy) {
		return policy.getEvaluatorProperties().getBooleanFromString(PARAMETER_SECURED);
	}
}
