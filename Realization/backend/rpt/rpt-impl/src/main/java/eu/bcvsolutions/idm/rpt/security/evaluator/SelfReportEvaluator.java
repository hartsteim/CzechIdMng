package eu.bcvsolutions.idm.rpt.security.evaluator;

import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;
import eu.bcvsolutions.idm.rpt.entity.RptReport;
import eu.bcvsolutions.idm.rpt.entity.RptReport_;

/**
 * Report's creator is logged identity
 * 
 * @author Radek Tomiška
 *
 */
@Component(SelfReportEvaluator.EVALUATOR_NAME)
@Description("Report's creator is logged identity")
public class SelfReportEvaluator extends AbstractAuthorizationEvaluator<RptReport> {

	public static final String EVALUATOR_NAME = "rpt-self-report-evaluator";

	private SecurityService securityService;
	
	@Autowired
	public SelfReportEvaluator(SecurityService securityService) {
		Assert.notNull(securityService, "Service is required.");
		//
		this.securityService = securityService;
	}

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
	
	@Override
	public Predicate getPredicate(Root<RptReport> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasPermission(policy, permission)) {
			return null;
		}
		if (!securityService.isAuthenticated()) {
			return null;
		}
		return builder.equal(root.get(RptReport_.creatorId), securityService.getCurrentId());
	}
	
	@Override
	public Set<String> getPermissions(RptReport entity, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(entity, policy);
		if (entity == null || !securityService.isAuthenticated()) {
			return permissions;
		}
		if ((entity.getId() == null && entity.getCreatorId() == null) || securityService.getCurrentId().equals(entity.getCreatorId())) {
			permissions.addAll(policy.getPermissions());
		}
		return permissions;
	}
}
