package eu.bcvsolutions.idm.acc.security.evaluator;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.TestHelper;
import eu.bcvsolutions.idm.acc.domain.AccGroupPermission;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccAccountRoleAssignmentDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccAccountRoleAssignmentFilter;
import eu.bcvsolutions.idm.acc.entity.AccAccount;
import eu.bcvsolutions.idm.acc.entity.AccAccountRoleAssignment;
import eu.bcvsolutions.idm.acc.service.api.AccAccountRoleAssignmentService;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.evaluator.BasePermissionEvaluator;
import eu.bcvsolutions.idm.test.api.AbstractEvaluatorIntegrationTest;

/**
 * @author Tomáš Doischer
 */
public class AccountRoleAssignmentByAccountEvaluatorTest extends AbstractEvaluatorIntegrationTest {
	@Autowired
	private TestHelper helper;
	@Autowired
	private AccAccountService accountService;
	@Autowired
	AccAccountRoleAssignmentService accountRoleAssignmentService;

	@Test
	public void testEvaluator() {
		AccAccountDto account = helper.createAccount();
		IdmRoleDto role = helper.createRole();
		AccAccountRoleAssignmentDto accountRoleAssignment = helper.createAccountRoleAssignment(account, role);
		AccAccountRoleAssignmentFilter roleAssignmentFilter = new AccAccountRoleAssignmentFilter();
		roleAssignmentFilter.setAccountId(account.getId());
		//
		IdmIdentityDto identity = helper.createIdentity();
		try {
			helper.login(identity);
			// check read
			List<AccAccountRoleAssignmentDto> assignedRoles = accountRoleAssignmentService.find(roleAssignmentFilter, null, IdmBasePermission.READ).getContent();
			Assert.assertEquals(0, assignedRoles.size());
		} finally {
			helper.logout();
		}
		//
		// create authorization policy
		IdmRoleDto roleWithPermission = helper.createRole();
		helper.createAuthorizationPolicy(
				roleWithPermission.getId(),
				AccGroupPermission.ACCOUNTROLEASSIGNMENT,
				AccAccountRoleAssignment.class,
				AccountRoleAssignmentByAccountEvaluator.class,
				IdmBasePermission.READ);
		//
		helper.createAuthorizationPolicy(
				roleWithPermission.getId(),
				AccGroupPermission.ACCOUNT,
				AccAccount.class,
				BasePermissionEvaluator.class,
				IdmBasePermission.READ);
		//
		helper.createIdentityRole(identity, roleWithPermission);
		try {
			helper.login(identity);
			// check read
			List<AccAccountRoleAssignmentDto> assignedRoles = accountRoleAssignmentService.find(roleAssignmentFilter, null, IdmBasePermission.READ).getContent();
			Assert.assertEquals(1, assignedRoles.size());
			Assert.assertEquals(accountRoleAssignment.getId(), assignedRoles.get(0).getId());
		} finally {
			helper.logout();
		}
	}

}
