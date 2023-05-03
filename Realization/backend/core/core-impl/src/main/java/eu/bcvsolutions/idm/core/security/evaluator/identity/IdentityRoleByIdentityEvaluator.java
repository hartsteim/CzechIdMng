package eu.bcvsolutions.idm.core.security.evaluator.identity;

import java.util.Set;
import java.util.UUID;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractTransitiveEvaluator;

/**
 * Permissions to roles by identity.
 * 
 * @author Radek Tomiška
 *
 */
@Component(IdentityRoleByIdentityEvaluator.EVALUATOR_NAME)
@Description("Permissions to assigned roles by identity.")
public class IdentityRoleByIdentityEvaluator extends AbstractTransitiveEvaluator<IdmIdentityRole> {

	public static final String EVALUATOR_NAME = "core-identity-role-by-identity-evaluator";

	@Autowired private AuthorizationManager authorizationManager;
	@Autowired private SecurityService securityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	protected Identifiable getOwner(IdmIdentityRole entity) {
		return entity.getIdentityContract().getIdentity();
	}
	
	@Override
	protected Class<? extends Identifiable> getOwnerType() {
		return IdmIdentity.class;
	}
	
	@Override
	public Predicate getPredicate(Root<IdmIdentityRole> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		// identity subquery
		Subquery<IdmIdentity> subquery = query.subquery(IdmIdentity.class);
		Root<IdmIdentity> subRoot = subquery.from(IdmIdentity.class);
		subquery.select(subRoot);		
		subquery.where(builder.and(
				authorizationManager.getPredicate(subRoot, query, builder, permission),
				builder.equal(root.get(IdmIdentityRole_.identityContract).get(IdmIdentityContract_.identity), subRoot)
				));
		//
		return builder.exists(subquery);
	}
	
	@Override
	public Set<String> getPermissions(IdmIdentityRole entity, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(entity, policy);
		// add permissions, when update is available
		if (permissions.contains(IdmBasePermission.UPDATE.getName())) {
			permissions.add(IdmBasePermission.CREATE.getName());
			permissions.add(IdmBasePermission.DELETE.getName());
		}
		return permissions;
	}
	
	@Override
	public Set<String> getAuthorities(UUID identityId, AuthorizationPolicy policy) {
		Set<String> authorities = super.getAuthorities(identityId, policy);
		// add permissions, when update is available
		if (authorities.contains(IdmBasePermission.UPDATE.getName())) {
			authorities.add(IdmBasePermission.CREATE.getName());
			authorities.add(IdmBasePermission.DELETE.getName());
		}
		return authorities;
	}
}
