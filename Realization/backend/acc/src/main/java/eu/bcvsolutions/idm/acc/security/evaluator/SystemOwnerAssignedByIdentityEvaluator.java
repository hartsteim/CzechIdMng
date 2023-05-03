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
import eu.bcvsolutions.idm.acc.entity.SysSystemOwner;
import eu.bcvsolutions.idm.acc.entity.SysSystemOwner_;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractTransitiveEvaluator;

/**
 * Permissions to assigned owners (by identity) by role.
 *
 * @author Roman Kucera
 * @since 13.0.0
 */
@Component(SystemOwnerAssignedByIdentityEvaluator.EVALUATOR_NAME)
@Description("Permissions to assigned owners (by identity) by role.")
public class SystemOwnerAssignedByIdentityEvaluator extends AbstractTransitiveEvaluator<SysSystemOwner> {

	public static final String EVALUATOR_NAME = "acc-system-owner-assigned-by-identity-evaluator";

	@Autowired
	private AuthorizationManager authorizationManager;
	@Autowired
	private SecurityService securityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	protected Identifiable getOwner(SysSystemOwner entity) {
		return entity.getSystem();
	}

	@Override
	protected Class<? extends Identifiable> getOwnerType() {
		return SysSystem.class;
	}

	@Override
	public Predicate getPredicate(Root<SysSystemOwner> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		// identity subquery
		Subquery<SysSystem> subquery = query.subquery(SysSystem.class);
		Root<SysSystem> subRoot = subquery.from(SysSystem.class);
		subquery.select(subRoot);
		subquery.where(builder.and(
				authorizationManager.getPredicate(subRoot, query, builder, permission),
				builder.equal(root.get(SysSystemOwner_.system), subRoot) // correlation attribute
		));

		return builder.exists(subquery);
	}

	@Override
	public Set<String> getPermissions(SysSystemOwner entity, AuthorizationPolicy policy) {
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
