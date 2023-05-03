package eu.bcvsolutions.idm.core.security.evaluator.delegation;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.model.entity.IdmDelegation;
import eu.bcvsolutions.idm.core.model.entity.IdmDelegationDefinition;
import eu.bcvsolutions.idm.core.model.entity.IdmDelegation_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractTransitiveEvaluator;

/**
 * Permissions to delegation by delegation definition.
 *
 * @author Vít Švanda
 *
 */
@Component(DelegationByDelegationDefinitionEvaluator.EVALUATOR_NAME)
@Description("Permissions to delegation by delegation definition.")
public class DelegationByDelegationDefinitionEvaluator extends AbstractTransitiveEvaluator<IdmDelegation> {

	public static final String EVALUATOR_NAME = "core-delegation-by-delegation-definition-evaluator";
	@Autowired
	private AuthorizationManager authorizationManager;
	@Autowired
	private SecurityService securityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	protected Identifiable getOwner(IdmDelegation entity) {
		return entity.getDefinition();
	}

	@Override
	protected Class<? extends Identifiable> getOwnerType() {
		return IdmDelegationDefinition.class;
	}

	@Override
	public Predicate getPredicate(Root<IdmDelegation> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		// Delegation definition subquery
		Subquery<IdmDelegationDefinition> subquery = query.subquery(IdmDelegationDefinition.class);
		Root<IdmDelegationDefinition> subRoot = subquery.from(IdmDelegationDefinition.class);
		subquery.select(subRoot);
		subquery.where(builder.and(
				authorizationManager.getPredicate(subRoot, query, builder, permission),
				builder.equal(root.get(IdmDelegation_.definition), subRoot)
		));
		//
		return builder.exists(subquery);
	}
}
