package eu.bcvsolutions.idm.core.security.evaluator;

import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;

/**
 * Adds base permissions by policy configuration
 * 
 * @author Radek Tomiška
 *
 */
@Component(BasePermissionEvaluator.EVALUATOR_NAME)
@Description("Simple permission evaluator - evaluates selected permissions on selected entity type")
public class BasePermissionEvaluator extends AbstractAuthorizationEvaluator<Identifiable> {

	public static final String EVALUATOR_NAME = "core-base-permission-evaluator";

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	public Predicate getPredicate(Root<Identifiable> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (hasPermission(policy, permission)) {
			return builder.conjunction();
		}
		return null;
	}
	
	@Override
	public Set<String> getPermissions(Identifiable entity, AuthorizationPolicy policy) {
		final Set<String> permissions = super.getPermissions(entity, policy);
		permissions.addAll(policy.getPermissions());
		return permissions;
	}
}
