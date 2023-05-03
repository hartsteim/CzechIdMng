package eu.bcvsolutions.idm.core.security.evaluator.task;

import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.scheduler.entity.IdmLongRunningTask;
import eu.bcvsolutions.idm.core.scheduler.entity.IdmLongRunningTask_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;

/**
 * Currently logged user and his tasks evaluator
 *
 * @author Ondrej Kopr <kopr@xyxy.cz>
 *
 */

@Component(SelfLongRunningTaskEvaluator.EVALUATOR_NAME)
@Description("Evaluator for currently logged user and his task.")
public class SelfLongRunningTaskEvaluator extends AbstractAuthorizationEvaluator<IdmLongRunningTask> {

	public static final String EVALUATOR_NAME = "core-self-long-running-task-evaluator";

	private final SecurityService securityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
	
	@Autowired
	public SelfLongRunningTaskEvaluator(SecurityService securityService) {
		Assert.notNull(securityService, "Service is required.");
		//
		this.securityService = securityService;
	}

	@Override
	public Predicate getPredicate(Root<IdmLongRunningTask> root, CriteriaQuery<?> query, CriteriaBuilder builder,
			AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasPermission(policy, permission)) {
			return null;
		}
		if (!securityService.isAuthenticated()) {
			return null;
		}
		// TODO: add probably original creator
		return builder.equal(root.get(IdmLongRunningTask_.creatorId), securityService.getCurrentId());
	}
	
	@Override
	public Set<String> getPermissions(IdmLongRunningTask entity, AuthorizationPolicy policy) {
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
