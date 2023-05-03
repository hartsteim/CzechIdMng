package eu.bcvsolutions.idm.acc.security.evaluator;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.acc.entity.AccRoleCatalogueAccount;
import eu.bcvsolutions.idm.acc.entity.AccRoleCatalogueAccount_;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogue;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractTransitiveEvaluator;

/**
 * Permissions to role catalogue accounts by their role catalogue
 *
 * @author Kučera
 *
 */
@Component(RoleCatalogueAccountByRoleCatalogueEvaluator.EVALUATOR_NAME)
@Description("Permission to role catalogue accounts by role")
public class RoleCatalogueAccountByRoleCatalogueEvaluator extends AbstractTransitiveEvaluator<AccRoleCatalogueAccount> {

	public static final String EVALUATOR_NAME = "acc-role-catalogue-account-by-role-catalogue-evaluator";

	@Autowired private AuthorizationManager authorizationManager;
	@Autowired private SecurityService securityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	protected Identifiable getOwner(AccRoleCatalogueAccount entity) {
		return entity.getRoleCatalogue();
	}

	@Override
	protected Class<? extends Identifiable> getOwnerType() {
		return IdmRoleCatalogue.class;
	}

	@Override
	public Predicate getPredicate(Root<AccRoleCatalogueAccount> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		// role subquery
		Subquery<IdmRoleCatalogue> subquery = query.subquery(IdmRoleCatalogue.class);
		Root<IdmRoleCatalogue> subRoot = subquery.from(IdmRoleCatalogue.class);
		subquery.select(subRoot);
		subquery.where(builder.and(
				authorizationManager.getPredicate(subRoot, query, builder, permission),
				builder.equal(root.get(AccRoleCatalogueAccount_.roleCatalogue), subRoot) // correlation attribute
		));
		//
		return builder.exists(subquery);
	}
}
