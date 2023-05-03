package eu.bcvsolutions.idm.acc.security.evaluator;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.acc.entity.AccIdentityRoleAccount;
import eu.bcvsolutions.idm.acc.entity.AccIdentityRoleAccount_;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractTransitiveEvaluator;

/**
 * Permissions to identity-role accounts
 * 
 * @author svandav
 *
 */
@Component(IdentityRoleAccountByIdentityRoleEvaluator.EVALUATOR_NAME)
@Description("Permissions to identity-role-accounts by identity-role")
public class IdentityRoleAccountByIdentityRoleEvaluator extends AbstractTransitiveEvaluator<AccIdentityRoleAccount> {

	public static final String EVALUATOR_NAME = "acc-identity-role-account-by-identity-role-evaluator";

	@Autowired
	private AuthorizationManager authorizationManager;
	@Autowired
	private SecurityService securityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	protected Identifiable getOwner(AccIdentityRoleAccount entity) {
		return entity.getIdentityRole();
	}

	@Override
	protected Class<? extends Identifiable> getOwnerType() {
		return IdmIdentityRole.class;
	}

	@Override
	public Predicate getPredicate(Root<AccIdentityRoleAccount> root, CriteriaQuery<?> query, CriteriaBuilder builder,
			AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		// subquery
		Subquery<IdmIdentityRole> subquery = query.subquery(IdmIdentityRole.class);
		Root<IdmIdentityRole> subRoot = subquery.from(IdmIdentityRole.class);
		subquery.select(subRoot);
		subquery.where(builder.and(authorizationManager.getPredicate(subRoot, query, builder, permission),
				builder.equal(root.get(AccIdentityRoleAccount_.identityRole), subRoot) // correlation attribute
		));
		//
		return builder.exists(subquery);
	}
}
