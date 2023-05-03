package eu.bcvsolutions.idm.core.security.evaluator.identity;

import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;

/**
 * Currently logged user manipulate over self roles.
 *
 * @author Ondrej Kopr <kopr@xyxy.cz>
 * @since 9.3.0
 *
 */
@Component(SelfIdentityRoleEvaluator.EVALUATOR_NAME)
@Description("Currently logged user manipulate over self roles.")
public class SelfIdentityRoleEvaluator extends AbstractAuthorizationEvaluator<IdmIdentityRole> {

	public static final String EVALUATOR_NAME = "core-self-identity-role-evaluator";

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SelfIdentityRoleEvaluator.class);

	@Autowired
	private SecurityService securityService;
	@Autowired
	private IdmIdentityContractService identityContractService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	public Predicate getPredicate(Root<IdmIdentityRole> root, CriteriaQuery<?> query, CriteriaBuilder builder,
			AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasPermission(policy, permission)) {
			return null;
		}
		if (!securityService.isAuthenticated()) {
			return null;
		}
		return builder.equal(root.get(IdmIdentityRole_.identityContract).get(IdmIdentityContract_.identity).get(IdmIdentity_.id), securityService.getCurrentId());
	}

	@Override
	public Set<String> getPermissions(IdmIdentityRole entity, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(entity, policy);
		if (entity == null || !securityService.isAuthenticated()) {
			return permissions;
		}

		// defensive behavior - all identity role must have contract, this is only for sure
		IdmIdentityContractDto identityContractDto = identityContractService.get(entity.getIdentityContract());
		if (identityContractDto == null) {
			LOG.error("Identity contract for identity role id: [{}], for contract id: [{}], not found.", entity.getId(), entity.getIdentityContract());
			return permissions;
		}
		if (securityService.getCurrentId().equals(identityContractDto.getIdentity())) {
			permissions.addAll(policy.getPermissions());
		}
		return permissions;

	}
}
