package eu.bcvsolutions.idm.acc.security.evaluator;

import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.entity.AccAccount;
import eu.bcvsolutions.idm.acc.entity.AccAccountRoleAssignment;
import eu.bcvsolutions.idm.acc.entity.AccAccountRoleAssignment_;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractTransitiveEvaluator;

/**
 * Permissions to account role assignments by account
 *
 * @author Tomáš Doischer
 */
@Component(AccountRoleAssignmentByAccountEvaluator.EVALUATOR_NAME)
@Description("Permissions to account role assignments by account")
public class AccountRoleAssignmentByAccountEvaluator extends AbstractTransitiveEvaluator<AccAccountRoleAssignment> {
	public static final String EVALUATOR_NAME = "acc-account-role-assignment-by-account-evaluator";

	private final AuthorizationManager authorizationManager;
	private final SecurityService securityService;

	public AccountRoleAssignmentByAccountEvaluator(AuthorizationManager authorizationManager, SecurityService securityService) {
		this.authorizationManager = authorizationManager;
		this.securityService = securityService;
	}

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	protected Identifiable getOwner(AccAccountRoleAssignment entity) {
		return entity.getAccount();
	}

	@Override
	protected Class<? extends Identifiable> getOwnerType() {
		return AccAccount.class;
	}

	@Override
	public Predicate getPredicate(Root<AccAccountRoleAssignment> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		// account role subquery
		Subquery<AccAccount> subquery = query.subquery(AccAccount.class);
		Root<AccAccount> subRoot = subquery.from(AccAccount.class);
		subquery.select(subRoot);
		subquery.where(builder.and(
				authorizationManager.getPredicate(subRoot, query, builder, permission),
				builder.equal(root.get(AccAccountRoleAssignment_.account), subRoot) // correlation attribute
		));
		//
		return builder.exists(subquery);
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		return Lists.newArrayList(getIncludePermissionsFormAttribute());
	}
}
