package eu.bcvsolutions.idm.core.security.evaluator.identity;

import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.repository.filter.FilterManager;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;

/**
 * Permissions to subordinates.
 * 
 * {@link SubordinateContractEvaluator} is more flexible than {@link SubordinatesEvaluator} - contracts can be secured by manager (by tree structure or by guarantee).
 * If {@link IdentityRoleByContractEvaluator} is configured, then logged identity can see / edit roles assigned to managed contracts.
 * 
 * @author Radek Tomiška
 *
 */
@Component(SubordinatesEvaluator.EVALUATOR_NAME)
@Description("Permissions to subordinates.")
public class SubordinatesEvaluator extends AbstractAuthorizationEvaluator<IdmIdentity> {

	public static final String EVALUATOR_NAME = "core-subordinates-identity-evaluator";
	@Autowired private SecurityService securityService;
	@Autowired private FilterManager filterManager;
	@Autowired private IdmIdentityService identityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	public Predicate getPredicate(Root<IdmIdentity> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasPermission(policy, permission)) {
			return null;
		}
		if (!securityService.isAuthenticated()) {
			return null;
		}
		IdmIdentityFilter filter = new IdmIdentityFilter();
		filter.setSubordinatesFor(securityService.getAuthentication().getCurrentIdentity().getId());
		//
		return filterManager
				.getBuilder(IdmIdentity.class, IdmIdentityFilter.PARAMETER_SUBORDINATES_FOR)
				.getPredicate(root, query, builder, filter);
	}
	
	@Override
	public Set<String> getPermissions(IdmIdentity entity, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(entity, policy);
		if (entity == null || !securityService.isAuthenticated()) {
			return permissions;
		}
		IdmIdentityFilter filter = new IdmIdentityFilter();
		filter.setManagersFor(entity.getId());
		filter.setValidContractManagers(Boolean.TRUE);
		filter.setUsername(securityService.getUsername());
		boolean isManager = identityService
				.findIds(filter, PageRequest.of(0, 1))
				.getTotalElements() > 0;
		if (isManager) {
			permissions.addAll(policy.getPermissions());
		}
		return permissions;
	}
}
