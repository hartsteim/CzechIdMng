package eu.bcvsolutions.idm.core.security.evaluator.role;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.model.entity.IdmRoleRequest;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;
import eu.bcvsolutions.idm.core.workflow.model.dto.WorkflowHistoricProcessInstanceDto;
import eu.bcvsolutions.idm.core.workflow.model.dto.WorkflowProcessInstanceDto;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowHistoricProcessInstanceService;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowProcessInstanceService;

/**
 * Currently logged user can work with role requests, when identity was involved to approving.
 * 
 * Search ({@link #getPermissions(IdmRoleRequest, AuthorizationPolicy)}) is not implemented.
 * 
 * @author Radek Tomiška
 */
@Component(RoleRequestByWfInvolvedIdentityEvaluator.EVALUATOR_NAME)
@Description("Currently logged user can work with role requests, when identity was involved to approving.")
public class RoleRequestByWfInvolvedIdentityEvaluator extends AbstractAuthorizationEvaluator<IdmRoleRequest> {

	public static final String EVALUATOR_NAME = "core-role-request-by-wf-involved-identity-evaluator";

	private final WorkflowProcessInstanceService processService;
	@Autowired
	private WorkflowHistoricProcessInstanceService historicProcessService;
	private final SecurityService securityService;
	
	@Autowired
	public RoleRequestByWfInvolvedIdentityEvaluator(
			SecurityService securityService,
			WorkflowProcessInstanceService processService) {
		Assert.notNull(securityService, "Service is required.");
		Assert.notNull(processService, "Service is required.");
		//
		this.securityService = securityService;
		this.processService = processService;
	}

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
	
	@Override
	public Set<String> getPermissions(IdmRoleRequest entity, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(entity, policy);
		if (entity == null || !securityService.isAuthenticated() || entity.getWfProcessId() == null) {
			return permissions;
		}
		//
		// Search process instance by role request - its returned, if currently logged identity was involved in wf.
		WorkflowProcessInstanceDto processInstance = processService.get(entity.getWfProcessId(), true);
		if (processInstance != null) {
			permissions.addAll(policy.getPermissions());
		}		
		if (processInstance == null) {
			// Ok process was not returned, but we need to check historic process (on involved user) too.
			WorkflowHistoricProcessInstanceDto historicProcess = historicProcessService.get(entity.getWfProcessId());
			if (historicProcess != null) {
				permissions.addAll(policy.getPermissions());
			}
		}
		return permissions;
	}
}
