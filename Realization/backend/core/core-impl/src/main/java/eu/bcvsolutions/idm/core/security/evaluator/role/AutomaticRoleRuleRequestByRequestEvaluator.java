package eu.bcvsolutions.idm.core.security.evaluator.role;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.model.entity.IdmAutomaticRoleAttributeRuleRequest;
import eu.bcvsolutions.idm.core.model.entity.IdmAutomaticRoleAttributeRuleRequest_;
import eu.bcvsolutions.idm.core.model.entity.IdmAutomaticRoleRequest;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractTransitiveEvaluator;

/**
 * Permissions to automatic rule requests by automatic role request
 * 
 * Name was abbreviated from the
 * "AutomaticRoleAttributeRuleRequestByAutomaticRoleRequestEvaluator".
 * 
 * @author Radek Tomiška
 * @author Vít Švanda
 */
@Component(AutomaticRoleRuleRequestByRequestEvaluator.EVALUATOR_NAME)
@Description("Permissions to automatic rule requests by automatic role request")
public class AutomaticRoleRuleRequestByRequestEvaluator extends AbstractTransitiveEvaluator<IdmAutomaticRoleAttributeRuleRequest> {

	public static final String EVALUATOR_NAME = "core-automatic-role-rule-request-by-request-evaluator";

	@Autowired
	private AuthorizationManager authorizationManager;
	@Autowired
	private SecurityService securityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	protected Identifiable getOwner(IdmAutomaticRoleAttributeRuleRequest entity) {
		return entity.getRequest();
	}

	@Override
	protected Class<? extends Identifiable> getOwnerType() {
		return IdmAutomaticRoleRequest.class;
	}

	@Override
	public Predicate getPredicate(Root<IdmAutomaticRoleAttributeRuleRequest> root, CriteriaQuery<?> query, CriteriaBuilder builder,
			AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		// Automatic role request subquery
		Subquery<IdmAutomaticRoleRequest> subquery = query.subquery(IdmAutomaticRoleRequest.class);
		Root<IdmAutomaticRoleRequest> subRoot = subquery.from(IdmAutomaticRoleRequest.class);
		subquery.select(subRoot);
		subquery.where(builder.and(authorizationManager.getPredicate(subRoot, query, builder, permission),
				builder.equal(root.get(IdmAutomaticRoleAttributeRuleRequest_.request), subRoot) // correlation attribute
		));
		
		return builder.exists(subquery);
	}
}
