package eu.bcvsolutions.idm.acc.security.evaluator;

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

import eu.bcvsolutions.idm.acc.entity.SysSystem;
import eu.bcvsolutions.idm.acc.entity.SysSystemOwnerRole;
import eu.bcvsolutions.idm.acc.entity.SysSystemOwnerRole_;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractTransitiveEvaluator;

/**
 * Permissions to assigned owners (by role) by role.
 *
 * @author Roman Kucera
 * @since 13.0.0
 */
@Component(SystemOwnerAssignedByRoleEvaluator.EVALUATOR_NAME)
@Description("Permissions to assigned owners (by role) by role.")
public class SystemOwnerAssignedByRoleEvaluator extends AbstractTransitiveEvaluator<SysSystemOwnerRole> {

	public static final String EVALUATOR_NAME = "acc-system-owner-assigned-by-role-evaluator";

	@Autowired
	private AuthorizationManager authorizationManager;
	@Autowired
	private SecurityService securityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	protected Identifiable getOwner(SysSystemOwnerRole entity) {
		return entity.getSystem();
	}

	@Override
	protected Class<? extends Identifiable> getOwnerType() {
		return SysSystem.class;
	}

	@Override
	public Predicate getPredicate(Root<SysSystemOwnerRole> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		// identity subquery
		Subquery<SysSystem> subquery = query.subquery(SysSystem.class);
		Root<SysSystem> subRoot = subquery.from(SysSystem.class);
		subquery.select(subRoot);
		subquery.where(builder.and(
				authorizationManager.getPredicate(subRoot, query, builder, permission),
				builder.equal(root.get(SysSystemOwnerRole_.system), subRoot) // correlation attribute
		));

		return builder.exists(subquery);
	}

	@Override
	public Set<String> getPermissions(SysSystemOwnerRole entity, AuthorizationPolicy policy) {
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
