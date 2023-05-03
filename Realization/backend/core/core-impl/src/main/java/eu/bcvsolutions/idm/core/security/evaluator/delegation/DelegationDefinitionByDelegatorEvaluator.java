package eu.bcvsolutions.idm.core.security.evaluator.delegation;

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
import eu.bcvsolutions.idm.core.model.entity.IdmDelegationDefinition;
import eu.bcvsolutions.idm.core.model.entity.IdmDelegationDefinition_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractTransitiveEvaluator;

/**
 * Permissions to delegation definition by delegator.
 *
 * @author Vít Švanda
 *
 */
@Component(DelegationDefinitionByDelegatorEvaluator.EVALUATOR_NAME)
@Description("Permissions to delegation definition by delegator.")
public class DelegationDefinitionByDelegatorEvaluator extends AbstractTransitiveEvaluator<IdmDelegationDefinition> {

	public static final String EVALUATOR_NAME = "core-delegation-definition-by-delegator-evaluator";

	@Autowired
	private AuthorizationManager authorizationManager;
	@Autowired
	private SecurityService securityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	protected Identifiable getOwner(IdmDelegationDefinition entity) {
		if (entity.getDelegatorContract() != null) {
			// If delegator contract is filled, then this evaluator cannot granted anything 
			// (DelegationDefinitionByDelegatorContractEvaluator will be use).
			return null;
		}
		return entity.getDelegator();
	}

	@Override
	protected Class<? extends Identifiable> getOwnerType() {
		return IdmIdentity.class;
	}

	@Override
	public Predicate getPredicate(Root<IdmDelegationDefinition> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		// identity subquery
		Subquery<IdmIdentity> subquery = query.subquery(IdmIdentity.class);
		Root<IdmIdentity> subRoot = subquery.from(IdmIdentity.class);
		subquery.select(subRoot);
		subquery.where(builder.and(
				authorizationManager.getPredicate(subRoot, query, builder, permission),
				builder.and(
						// If delegator contract is filled, then this evaluator cannot granted anything 
						// (DelegationDefinitionByDelegatorContractEvaluator will be use).
						builder.isNull(root.get(IdmDelegationDefinition_.delegatorContract)),
						builder.equal(root.get(IdmDelegationDefinition_.delegator), subRoot)
				)));
		//
		return builder.exists(subquery);
	}

	@Override
	public Set<String> getPermissions(IdmDelegationDefinition entity, AuthorizationPolicy policy) {
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
