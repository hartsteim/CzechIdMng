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

import eu.bcvsolutions.idm.core.model.entity.IdmRequest;
import eu.bcvsolutions.idm.core.model.entity.IdmRequest_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;

/**
 * Currently logged user can edit his requests.
 * 
 * @author svandav
 *
 */
@Component(SelfRequestEvaluator.EVALUATOR_NAME)
@Description("Currently logged user can edit his requests.")
public class SelfRequestEvaluator extends AbstractAuthorizationEvaluator<IdmRequest> {

	public static final String EVALUATOR_NAME = "core-self-request-evaluator";
	private final SecurityService securityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Autowired
	public SelfRequestEvaluator(SecurityService securityService) {
		Assert.notNull(securityService, "Service is required.");
		//
		this.securityService = securityService;
	}
	
	@Override
	public Predicate getPredicate(Root<IdmRequest> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasPermission(policy, permission)) {
			return null;
		}
		if (!securityService.isAuthenticated()) {
			return null;
		}
		return builder.equal(root.get(IdmRequest_.creatorId), securityService.getCurrentId());
	}
	
	@Override
	public Set<String> getPermissions(IdmRequest entity, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(entity, policy);
		if (entity == null || !securityService.isAuthenticated()) {
			return permissions;
		}
		if (securityService.getCurrentId().equals(entity.getCreatorId())) {
			permissions.addAll(policy.getPermissions());
		}
		return permissions;
	}
}
