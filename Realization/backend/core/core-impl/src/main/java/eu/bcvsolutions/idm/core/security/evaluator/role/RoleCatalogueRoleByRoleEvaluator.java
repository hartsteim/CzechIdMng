package eu.bcvsolutions.idm.core.security.evaluator.role;

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
import eu.bcvsolutions.idm.core.model.entity.IdmRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogueRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogueRole_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractTransitiveEvaluator;

/**
 * Permissions to assigned role catalogue relations by role.
 * 
 * @author Radek Tomiška
 * @since 9.0.0
 */
@Component(RoleCatalogueRoleByRoleEvaluator.EVALUATOR_NAME)
@Description("Permissions to assigned role catalogue relations by role.")
public class RoleCatalogueRoleByRoleEvaluator extends AbstractTransitiveEvaluator<IdmRoleCatalogueRole> {

	public static final String EVALUATOR_NAME = "core-role-catalogue-role-by-role-evaluator";
	
	@Autowired private AuthorizationManager authorizationManager;
	@Autowired private SecurityService securityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
	
	@Override
	protected Identifiable getOwner(IdmRoleCatalogueRole entity) {
		return entity.getRole();
	}
	
	@Override
	protected Class<? extends Identifiable> getOwnerType() {
		return IdmRole.class;
	}
	
	@Override
	public Predicate getPredicate(Root<IdmRoleCatalogueRole> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		// identity subquery
		Subquery<IdmRole> subquery = query.subquery(IdmRole.class);
		Root<IdmRole> subRoot = subquery.from(IdmRole.class);
		subquery.select(subRoot);		
		subquery.where(builder.and(
				authorizationManager.getPredicate(subRoot, query, builder, permission),
				builder.equal(root.get(IdmRoleCatalogueRole_.role), subRoot) // correlation attribute
				));
		//
		return builder.exists(subquery);
	}
	
	@Override
	public Set<String> getPermissions(IdmRoleCatalogueRole entity, AuthorizationPolicy policy) {
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
		// add authorities, when update is available
		if (authorities.contains(IdmBasePermission.UPDATE.getName())) {
			authorities.add(IdmBasePermission.CREATE.getName());
			authorities.add(IdmBasePermission.DELETE.getName());
		}
		return authorities;
	}
}
