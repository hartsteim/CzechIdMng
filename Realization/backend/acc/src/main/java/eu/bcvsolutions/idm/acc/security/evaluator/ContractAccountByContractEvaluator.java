package eu.bcvsolutions.idm.acc.security.evaluator;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.acc.entity.AccContractAccount;
import eu.bcvsolutions.idm.acc.entity.AccContractAccount_;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractTransitiveEvaluator;

/**
 * Permissions to contract accounts
 * 
 * @author svandav
 *
 */
@Component(ContractAccountByContractEvaluator.EVALUATOR_NAME)
@Description("Permissions to contract accounts by role")
public class ContractAccountByContractEvaluator extends AbstractTransitiveEvaluator<AccContractAccount> {

	public static final String EVALUATOR_NAME = "acc-contract-account-by-contract-evaluator";

	@Autowired private AuthorizationManager authorizationManager;
	@Autowired private SecurityService securityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
	
	@Override
	protected Identifiable getOwner(AccContractAccount entity) {
		return entity.getContract();
	}
	
	@Override
	protected Class<? extends Identifiable> getOwnerType() {
		return IdmIdentityContract.class;
	}
	
	@Override
	public Predicate getPredicate(Root<AccContractAccount> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		// role subquery
		Subquery<IdmIdentityContract> subquery = query.subquery(IdmIdentityContract.class);
		Root<IdmIdentityContract> subRoot = subquery.from(IdmIdentityContract.class);
		subquery.select(subRoot);		
		subquery.where(builder.and(
				authorizationManager.getPredicate(subRoot, query, builder, permission),
				builder.equal(root.get(AccContractAccount_.contract), subRoot) // correlation attribute
				));
		//
		return builder.exists(subquery);
	}
}

