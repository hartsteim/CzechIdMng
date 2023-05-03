package eu.bcvsolutions.idm.core.security.evaluator.role;

import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.model.entity.IdmRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRole_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;

/**
 *
 * Evaluator which does allow user to see roles which are available for request.
 *
 * @author Peter Sourek
 */
@Component(RoleCanBeRequestedEvaluator.EVALUATOR_NAME)
@Description("Only allows access to roles, which can be requested.")
public class RoleCanBeRequestedEvaluator extends AbstractAuthorizationEvaluator<IdmRole> {

	public static final String EVALUATOR_NAME = "core-role-can-be-requested-evaluator";

	private final SecurityService securityService;

	@Autowired
	public RoleCanBeRequestedEvaluator(SecurityService securityService) {
		Assert.notNull(securityService, "Service is required.");
		//
		this.securityService = securityService;
	}

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	public Predicate getPredicate(Root<IdmRole> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		//
		return builder.equal(root.get(IdmRole_.canBeRequested), Boolean.TRUE);
	}


	@Override
	public Set<String> getPermissions(IdmRole authorizable, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(authorizable, policy);
		if (authorizable == null || !securityService.isAuthenticated()) {
			return permissions;
		}

		if (authorizable.isCanBeRequested()) {
			permissions.addAll(policy.getPermissions());
		}
		return permissions;
	}

}
