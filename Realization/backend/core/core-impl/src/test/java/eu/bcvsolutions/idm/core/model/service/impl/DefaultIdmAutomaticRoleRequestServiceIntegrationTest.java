package eu.bcvsolutions.idm.core.model.service.impl;

import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import eu.bcvsolutions.idm.core.AbstractCoreWorkflowIntegrationTest;
import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleComparison;
import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleType;
import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleRequestType;
import eu.bcvsolutions.idm.core.api.domain.RecursionType;
import eu.bcvsolutions.idm.core.api.domain.RequestOperationType;
import eu.bcvsolutions.idm.core.api.domain.RequestState;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeRuleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeRuleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmAutomaticRoleAttributeRuleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmAutomaticRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.exception.AcceptedException;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.exception.RoleRequestException;
import eu.bcvsolutions.idm.core.api.service.AutomaticRoleManager;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeRuleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeRuleService;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeService;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.ModuleService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.scheduler.api.config.SchedulerConfiguration;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;
import eu.bcvsolutions.idm.core.security.api.domain.IdmGroupPermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmJwtAuthentication;
import eu.bcvsolutions.idm.core.security.api.utils.IdmAuthorityUtils;
import eu.bcvsolutions.idm.core.workflow.model.dto.WorkflowFilterDto;
import eu.bcvsolutions.idm.core.workflow.model.dto.WorkflowTaskInstanceDto;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowTaskInstanceService;
import eu.bcvsolutions.idm.test.api.TestHelper;

/**
 * Test for change automatic role via request.
 * 
 * @author svandav
 * @author Radek Tomiška
 * @author Ondrej Husnik
 */
public class DefaultIdmAutomaticRoleRequestServiceIntegrationTest extends AbstractCoreWorkflowIntegrationTest {

	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private AutomaticRoleManager automaticRoleManager;
	@Autowired
	private IdmAutomaticRoleAttributeRuleRequestService ruleRequestService;
	@Autowired
	private IdmAutomaticRoleRequestService roleRequestService;
	@Autowired
	private ModuleService moduleService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmConfigurationService configurationService;
	@Autowired
	private IdmAutomaticRoleAttributeRuleService ruleService;
	@Autowired
	private IdmAutomaticRoleAttributeService automaticRoleAttributeService;
	@Autowired
	private IdmRoleTreeNodeService roleTreeNodeService;
	@Autowired
	private WorkflowTaskInstanceService workflowTaskInstanceService;
	@Autowired
	private IdmTreeNodeService treeNodeService;

	private static final String APPROVE_ROLE_BY_GUARANTEE_KEY = "approve-role-by-guarantee";
	private static final String APPROVE_ROLE_BY_GUARANTEE_AND_SECURITY_KEY = "approve-role-by-guarantee-security";
	private static final int APPROVE_ROLE_BY_GUARANTEE_PRIORITY = 100;

	@Before
	public void init() {
		loginAsAdmin();
	}

	@After
	public void logout() {
		super.logout();
	}

	private IdmRoleDto prepareRole() {
		// create roles
		IdmRoleDto role = getHelper().createRole();
		role.setPriority(APPROVE_ROLE_BY_GUARANTEE_PRIORITY);
		role.setApproveRemove(true);
		roleService.save(role);

		configurationService.setValue(IdmRoleService.WF_BY_ROLE_PRIORITY_PREFIX + role.getPriority(),
				APPROVE_ROLE_BY_GUARANTEE_KEY);
		return role;
	}

	@Test
	public void testCreateAutomaticAttributeRole() {
		getHelper().setConfigurationValue(SchedulerConfiguration.PROPERTY_TASK_ASYNCHRONOUS_ENABLED, true);
		try {
			IdmRoleDto role = prepareRole();
			IdmIdentityDto guaranteeIdentity = getHelper().createIdentity();
			getHelper().createRoleGuarantee(role, guaranteeIdentity);
	
			IdmAutomaticRoleRequestDto request = new IdmAutomaticRoleRequestDto();
			request.setState(RequestState.EXECUTED);
			request.setOperation(RequestOperationType.ADD);
			request.setRequestType(AutomaticRoleRequestType.ATTRIBUTE);
			request.setExecuteImmediately(true);
			request.setName(role.getCode());
			request.setRole(role.getId());
			request = roleRequestService.save(request);
	
			Assert.assertEquals(RequestState.CONCEPT, request.getState());
	
			IdmIdentityDto identity = getHelper().createIdentity((GuardedString) null);
			IdmAutomaticRoleAttributeRuleRequestDto rule = new IdmAutomaticRoleAttributeRuleRequestDto();
			rule.setRequest(request.getId());
			rule.setOperation(RequestOperationType.ADD);
			rule.setAttributeName(IdmIdentity_.username.getName());
			rule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
			rule.setType(AutomaticRoleAttributeRuleType.IDENTITY);
			rule.setValue(identity.getUsername());
			rule = ruleRequestService.save(rule);
	
			request = roleRequestService.startRequestInternal(request.getId(), true);
			// Recalculate
			Assert.assertNotNull(request.getAutomaticRole());
			request = roleRequestService.get(request.getId());
			Assert.assertEquals(RequestState.EXECUTED, request.getState());
			//
			getHelper().waitForResult(res -> {
				return identityRoleService.findAllByIdentity(identity.getId()).isEmpty();
			});
			//
			List<IdmIdentityRoleDto> identityRoles = identityRoleService.findAllByIdentity(identity.getId());
			Assert.assertFalse(identityRoles.isEmpty());
			Assert.assertEquals(1, identityRoles.size());
			Assert.assertEquals(role.getId(), identityRoles.get(0).getRole());
			Assert.assertNotNull(identityRoles.get(0).getAutomaticRole());
		} finally {
			getHelper().setConfigurationValue(SchedulerConfiguration.PROPERTY_TASK_ASYNCHRONOUS_ENABLED, false);
		}
	}

	/**
	 * Test for AutomaticRoleManager. Create automatic role with rule.
	 */
	@Test
	public void testCreateAutomaticAttributeRoleViaManager() {
		getHelper().setConfigurationValue(SchedulerConfiguration.PROPERTY_TASK_ASYNCHRONOUS_ENABLED, true);
		try {
			IdmRoleDto role = prepareRole();
			IdmIdentityDto identity = getHelper().createIdentity((GuardedString) null);
	
			IdmAutomaticRoleAttributeDto automaticRole = new IdmAutomaticRoleAttributeDto();
			automaticRole.setRole(role.getId());
			automaticRole.setName(role.getCode());
	
			IdmAutomaticRoleAttributeRuleDto rule = new IdmAutomaticRoleAttributeRuleDto();
			rule.setAttributeName(IdmIdentity_.username.getName());
			rule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
			rule.setType(AutomaticRoleAttributeRuleType.IDENTITY);
			rule.setValue(identity.getUsername());
	
			// Create automatic role via manager
			automaticRole = automaticRoleManager.createAutomaticRoleByAttribute(automaticRole, true, rule);
			Assert.assertNotNull(automaticRole.getId());
			
			getHelper().waitForResult(res -> {
				return identityRoleService.findAllByIdentity(identity.getId()).isEmpty();
			});
	
			List<IdmIdentityRoleDto> identityRoles = identityRoleService.findAllByIdentity(identity.getId());
			Assert.assertFalse(identityRoles.isEmpty());
			Assert.assertEquals(1, identityRoles.size());
			Assert.assertEquals(role.getId(), identityRoles.get(0).getRole());
			Assert.assertNotNull(identityRoles.get(0).getAutomaticRole());
		} finally {
			getHelper().setConfigurationValue(SchedulerConfiguration.PROPERTY_TASK_ASYNCHRONOUS_ENABLED, false);
		}
	}

	@Test
	public void testDeleteAutomaticAttributeRoleApproval() {
		IdmRoleDto role = prepareRole();
		IdmIdentityDto guaranteeIdentity = getHelper().createIdentity();
		getHelper().createRoleGuarantee(role, guaranteeIdentity);
		IdmIdentityDto identity = getHelper().createIdentity();

		IdmAutomaticRoleAttributeDto automaticRole = new IdmAutomaticRoleAttributeDto();
		automaticRole.setRole(role.getId());
		automaticRole.setName(role.getCode());

		IdmAutomaticRoleAttributeRuleDto rule = new IdmAutomaticRoleAttributeRuleDto();
		rule.setAttributeName(IdmIdentity_.username.getName());
		rule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
		rule.setType(AutomaticRoleAttributeRuleType.IDENTITY);
		rule.setValue(identity.getUsername());

		// Create automatic role via manager
		automaticRole = automaticRoleManager.createAutomaticRoleByAttribute(automaticRole, true, rule);

		// Delete automatic role via manager
		try {
			automaticRoleManager.deleteAutomaticRole(automaticRole, false);
		} catch (AcceptedException ex) {
			// The request is in approval
			Assert.assertNotNull(ex.getIdentifier());
			UUID requestId = UUID.fromString(ex.getIdentifier());
			loginAsNoAdmin(guaranteeIdentity.getUsername());
			try {
				completeTasksFromUsers(guaranteeIdentity.getUsername(), "approve");
			} catch (ResultCodeException e) {
				fail("User has permission to approve task. Error message: " + e.getLocalizedMessage());
			} catch (Exception e) {
				fail("Some problem: " + e.getLocalizedMessage());
			}

			IdmAutomaticRoleRequestDto request = roleRequestService.get(requestId);
			Assert.assertEquals(RequestState.EXECUTED, request.getState());
			IdmRoleTreeNodeDto deletedAutomaticRole = roleTreeNodeService.get(automaticRole.getId());
			Assert.assertNull(deletedAutomaticRole);
			return;
		}
		fail("Automatic role request have to be approving by gurantee!");
	}

	@Test
	public void testChangeRule() {
		IdmRoleDto role = prepareRole();
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityDto identityTwo = getHelper().createIdentity();

		IdmAutomaticRoleAttributeDto automaticRole = new IdmAutomaticRoleAttributeDto();
		automaticRole.setRole(role.getId());
		automaticRole.setName(role.getCode());

		IdmAutomaticRoleAttributeRuleDto rule = new IdmAutomaticRoleAttributeRuleDto();
		rule.setAttributeName(IdmIdentity_.username.getName());
		rule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
		rule.setType(AutomaticRoleAttributeRuleType.IDENTITY);
		rule.setValue(identity.getUsername());

		// Create automatic role via manager
		automaticRole = automaticRoleManager.createAutomaticRoleByAttribute(automaticRole, true, rule);
		Assert.assertNotNull(automaticRole.getId());

		IdmAutomaticRoleAttributeRuleFilter ruleFilter = new IdmAutomaticRoleAttributeRuleFilter();
		ruleFilter.setAutomaticRoleAttributeId(automaticRole.getId());
		List<IdmAutomaticRoleAttributeRuleDto> rules = ruleService.find(ruleFilter, null).getContent();
		Assert.assertEquals(1, rules.size());
		rule = rules.get(0);
		rule.setValue(identityTwo.getUsername());

		// Change automatic role via manager
		automaticRole = automaticRoleManager.changeAutomaticRoleRules(automaticRole, true, rule);
		// Find current rules
		rules = ruleService.find(ruleFilter, null).getContent();
		Assert.assertEquals(1, rules.size());
		// We updated rule ... must has same id and changed value
		Assert.assertEquals(rule.getId(), rules.get(0).getId());
		Assert.assertEquals(identityTwo.getUsername(), rules.get(0).getValue());
	}

	@Test
	public void testRemoveRule() {
		getHelper().setConfigurationValue(SchedulerConfiguration.PROPERTY_TASK_ASYNCHRONOUS_ENABLED, false);
		//
		IdmRoleDto role = prepareRole();
		IdmIdentityDto identity = getHelper().createIdentity((GuardedString) null);
		IdmIdentityDto identityTwo = getHelper().createIdentity((GuardedString) null);

		IdmAutomaticRoleAttributeDto automaticRole = new IdmAutomaticRoleAttributeDto();
		automaticRole.setRole(role.getId());
		automaticRole.setName(role.getCode());

		IdmAutomaticRoleAttributeRuleDto rule = new IdmAutomaticRoleAttributeRuleDto();
		rule.setAttributeName(IdmIdentity_.username.getName());
		rule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
		rule.setType(AutomaticRoleAttributeRuleType.IDENTITY);
		rule.setValue(identity.getUsername());

		// Create automatic role via manager
		automaticRole = automaticRoleManager.createAutomaticRoleByAttribute(automaticRole, true, rule);
		Assert.assertNotNull(automaticRole.getId());

		IdmAutomaticRoleAttributeRuleFilter ruleFilter = new IdmAutomaticRoleAttributeRuleFilter();
		ruleFilter.setAutomaticRoleAttributeId(automaticRole.getId());
		List<IdmAutomaticRoleAttributeRuleDto> rules = ruleService.find(ruleFilter, null).getContent();
		Assert.assertEquals(1, rules.size());
		rule = rules.get(0);
		rule.setValue(identityTwo.getUsername());

		// Change automatic role via manager
		automaticRole = automaticRoleManager.changeAutomaticRoleRules(automaticRole, true);
		// Find current rules
		rules = ruleService.find(ruleFilter, null).getContent();
		Assert.assertEquals(0, rules.size());
	}

	@Test
	public void testAddRule() {
		IdmRoleDto role = prepareRole();
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityDto identityTwo = getHelper().createIdentity();

		IdmAutomaticRoleAttributeDto automaticRole = new IdmAutomaticRoleAttributeDto();
		automaticRole.setRole(role.getId());
		automaticRole.setName(role.getCode());

		IdmAutomaticRoleAttributeRuleDto rule = new IdmAutomaticRoleAttributeRuleDto();
		rule.setAttributeName(IdmIdentity_.username.getName());
		rule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
		rule.setType(AutomaticRoleAttributeRuleType.IDENTITY);
		rule.setValue(identity.getUsername());

		// Create automatic role via manager
		automaticRole = automaticRoleManager.createAutomaticRoleByAttribute(automaticRole, true, rule);
		Assert.assertNotNull(automaticRole.getId());

		IdmAutomaticRoleAttributeRuleFilter ruleFilter = new IdmAutomaticRoleAttributeRuleFilter();
		ruleFilter.setAutomaticRoleAttributeId(automaticRole.getId());
		List<IdmAutomaticRoleAttributeRuleDto> rules = ruleService.find(ruleFilter, null).getContent();
		Assert.assertEquals(1, rules.size());
		rule = rules.get(0);

		// Create new rule
		IdmAutomaticRoleAttributeRuleDto newRule = new IdmAutomaticRoleAttributeRuleDto();
		newRule.setAttributeName(IdmIdentity_.username.getName());
		newRule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
		newRule.setType(AutomaticRoleAttributeRuleType.IDENTITY);
		newRule.setValue(identityTwo.getUsername());

		// Change automatic role via manager
		automaticRole = automaticRoleManager.changeAutomaticRoleRules(automaticRole, true, newRule);
		// Find current rules
		rules = ruleService.find(ruleFilter, null).getContent();
		Assert.assertEquals(1, rules.size());
		// We created new rule and deleted old
		Assert.assertNotEquals(rule.getId(), rules.get(0).getId());
		Assert.assertEquals(identityTwo.getUsername(), rules.get(0).getValue());
	}

	@Test
	public void testCreateTreeAutomaticRole() {
		IdmRoleDto role = prepareRole();
		IdmTreeNodeDto nodeOne = getHelper().createTreeNode();

		IdmRoleTreeNodeDto automaticRole = new IdmRoleTreeNodeDto();
		automaticRole.setRole(role.getId());
		automaticRole.setName(role.getCode());
		automaticRole.setTreeNode(nodeOne.getId());

		// Create automatic role via manager
		automaticRole = automaticRoleManager.createAutomaticRoleByTree(automaticRole, true);
		Assert.assertNotNull(automaticRole.getId());

		IdmRoleTreeNodeDto treeAutomaticRole = roleTreeNodeService.get(automaticRole.getId());
		Assert.assertNotNull(treeAutomaticRole);
		Assert.assertEquals(nodeOne.getId(), treeAutomaticRole.getTreeNode());
		Assert.assertEquals(RecursionType.NO, treeAutomaticRole.getRecursionType());
		Assert.assertEquals(role.getId(), treeAutomaticRole.getRole());
	}

	@Test
	public void testCreateTreeAutomaticRoleWithRecursion() {
		IdmRoleDto role = prepareRole();
		IdmTreeNodeDto nodeOne = getHelper().createTreeNode();

		IdmRoleTreeNodeDto automaticRole = new IdmRoleTreeNodeDto();
		automaticRole.setRole(role.getId());
		automaticRole.setName(role.getCode());
		automaticRole.setRecursionType(RecursionType.DOWN);
		automaticRole.setTreeNode(nodeOne.getId());

		// Create automatic role via manager
		automaticRole = automaticRoleManager.createAutomaticRoleByTree(automaticRole, true);
		Assert.assertNotNull(automaticRole.getId());

		IdmRoleTreeNodeDto treeAutomaticRole = roleTreeNodeService.get(automaticRole.getId());
		Assert.assertNotNull(treeAutomaticRole);
		Assert.assertEquals(RecursionType.DOWN, treeAutomaticRole.getRecursionType());

		// Create second
		IdmRoleTreeNodeDto automaticRoleTwo = new IdmRoleTreeNodeDto();
		automaticRoleTwo.setRole(role.getId());
		automaticRoleTwo.setName(role.getCode());
		automaticRoleTwo.setRecursionType(RecursionType.UP);
		automaticRoleTwo.setTreeNode(nodeOne.getId());

		// Create automatic role via manager
		IdmRoleTreeNodeDto treeAutomaticRoleTwo = automaticRoleManager.createAutomaticRoleByTree(automaticRoleTwo,
				true);
		Assert.assertNotNull(treeAutomaticRoleTwo.getId());
		Assert.assertEquals(RecursionType.UP, treeAutomaticRoleTwo.getRecursionType());

		// Create third
		IdmRoleTreeNodeDto automaticRoleThree = new IdmRoleTreeNodeDto();
		automaticRoleThree.setRole(role.getId());
		automaticRoleThree.setName(role.getCode());
		automaticRoleThree.setRecursionType(RecursionType.NO);
		automaticRoleThree.setTreeNode(nodeOne.getId());

		// Create automatic role via manager
		IdmRoleTreeNodeDto treeAutomaticRoleThree = automaticRoleManager.createAutomaticRoleByTree(automaticRoleThree,
				true);
		Assert.assertNotNull(treeAutomaticRoleThree.getId());
		Assert.assertEquals(RecursionType.NO, treeAutomaticRoleThree.getRecursionType());
	}

	@Test
	public void testDeleteTreeAutomaticRole() {
		IdmRoleDto role = prepareRole();
		IdmTreeNodeDto nodeOne = getHelper().createTreeNode();

		IdmRoleTreeNodeDto automaticRole = new IdmRoleTreeNodeDto();
		automaticRole.setRole(role.getId());
		automaticRole.setName(role.getCode());
		automaticRole.setTreeNode(nodeOne.getId());

		// Create automatic role via manager
		automaticRole = automaticRoleManager.createAutomaticRoleByTree(automaticRole, true);
		Assert.assertNotNull(automaticRole.getId());

		IdmRoleTreeNodeDto treeAutomaticRole = roleTreeNodeService.get(automaticRole.getId());
		Assert.assertNotNull(treeAutomaticRole);
		Assert.assertEquals(nodeOne.getId(), treeAutomaticRole.getTreeNode());
		Assert.assertEquals(role.getId(), treeAutomaticRole.getRole());

		// Delete automatic role via manager
		automaticRoleManager.deleteAutomaticRole(automaticRole, true);
		IdmRoleTreeNodeDto deletedAutomaticRole = roleTreeNodeService.get(automaticRole.getId());
		Assert.assertNull(deletedAutomaticRole);
	}

	@Test
	public void testDeleteTreeAutomaticRoleApproval() {
		IdmRoleDto role = prepareRole();
		IdmTreeNodeDto nodeOne = getHelper().createTreeNode();
		IdmIdentityDto guaranteeIdentity = getHelper().createIdentity();
		getHelper().createRoleGuarantee(role, guaranteeIdentity);

		IdmRoleTreeNodeDto automaticRole = new IdmRoleTreeNodeDto();
		automaticRole.setRole(role.getId());
		automaticRole.setName(role.getCode());
		automaticRole.setTreeNode(nodeOne.getId());

		// Create automatic role via manager
		automaticRole = automaticRoleManager.createAutomaticRoleByTree(automaticRole, true);
		Assert.assertNotNull(automaticRole.getId());

		IdmRoleTreeNodeDto treeAutomaticRole = roleTreeNodeService.get(automaticRole.getId());
		Assert.assertNotNull(treeAutomaticRole);
		Assert.assertEquals(nodeOne.getId(), treeAutomaticRole.getTreeNode());
		Assert.assertEquals(role.getId(), treeAutomaticRole.getRole());

		// Delete automatic role via manager
		try {
			automaticRoleManager.deleteAutomaticRole(automaticRole, false);
		} catch (AcceptedException ex) {
			// The request is in approval
			Assert.assertNotNull(ex.getIdentifier());
			UUID requestId = UUID.fromString(ex.getIdentifier());
			loginAsNoAdmin(guaranteeIdentity.getUsername());
			try {
				completeTasksFromUsers(guaranteeIdentity.getUsername(), "approve");
			} catch (ResultCodeException e) {
				fail("User has permission to approve task. Error message: " + e.getLocalizedMessage());
			} catch (Exception e) {
				fail("Some problem: " + e.getLocalizedMessage());
			}

			IdmAutomaticRoleRequestDto request = roleRequestService.get(requestId);
			Assert.assertEquals(RequestState.EXECUTED, request.getState());
			IdmRoleTreeNodeDto deletedAutomaticRole = roleTreeNodeService.get(automaticRole.getId());
			Assert.assertNull(deletedAutomaticRole);
			return;
		}
		fail("Automatic role request have to be approving by gurantee!");
	}
	
	
	@Test
	public void testAutomaticRoleRequestReferentialIntegrity () {
		getHelper().setConfigurationValue(SchedulerConfiguration.PROPERTY_TASK_ASYNCHRONOUS_ENABLED, false);
		//
		IdmRoleDto role = prepareRole();
		IdmTreeNodeDto nodeOne = getHelper().createTreeNode();
		IdmIdentityDto guaranteeIdentity = getHelper().createIdentity();
		getHelper().createRoleGuarantee(role, guaranteeIdentity);
		
		// Creates automatic role assigned to tree node and then delete them
		// in order to be able to delete tree node.
		IdmRoleTreeNodeDto automaticRole = new IdmRoleTreeNodeDto();
		automaticRole.setRole(role.getId());
		automaticRole.setName(role.getCode());
		automaticRole.setTreeNode(nodeOne.getId());
		automaticRole = automaticRoleManager.createAutomaticRoleByTree(automaticRole, true);
		Assert.assertNotNull(automaticRole.getId());
		automaticRoleManager.deleteAutomaticRole(automaticRole, true);
		
		// Check existence of some requests for roles previously assigned to tree node 
		IdmAutomaticRoleRequestFilter requestFilter = new IdmAutomaticRoleRequestFilter();
		requestFilter.setTreeNodeId(nodeOne.getId());
		List<IdmAutomaticRoleRequestDto> requestDtos = roleRequestService.find(requestFilter,null).getContent();
		Assert.assertTrue(requestDtos.size() > 0);
		// Try to delete node 
		treeNodeService.delete(nodeOne);
		// Check that requests have removed reference to deleted tree node
		requestDtos = roleRequestService.find(requestFilter,null).getContent();
		Assert.assertEquals(requestDtos.size(), 0);
	}

	@Test(expected = RoleRequestException.class)
	public void notRightForExecuteImmediatelyExceptionTest() {
		this.logout();
		IdmIdentityDto identity = getHelper().createIdentity();
		// Log as user without right for immediately execute role request (without
		// approval)
		Collection<GrantedAuthority> authorities = IdmAuthorityUtils
				.toAuthorities(moduleService.getAvailablePermissions()).stream().filter(authority -> {
					return !CoreGroupPermission.AUTOMATIC_ROLE_REQUEST_ADMIN.equals(authority.getAuthority())
							&& !IdmGroupPermission.APP_ADMIN.equals(authority.getAuthority());
				}).collect(Collectors.toList());
		SecurityContextHolder.getContext().setAuthentication(
				new IdmJwtAuthentication(new IdmIdentityDto(identity.getUsername()), null, authorities, "test"));

		IdmRoleDto role = prepareRole();
		IdmAutomaticRoleRequestDto request = new IdmAutomaticRoleRequestDto();
		request.setState(RequestState.EXECUTED);
		request.setOperation(RequestOperationType.ADD);
		request.setRequestType(AutomaticRoleRequestType.ATTRIBUTE);
		request.setExecuteImmediately(true);
		request.setName(role.getCode());
		request.setRole(role.getId());
		request = roleRequestService.save(request);

		Assert.assertEquals(RequestState.CONCEPT, request.getState());

		IdmAutomaticRoleAttributeRuleRequestDto rule = new IdmAutomaticRoleAttributeRuleRequestDto();
		rule.setRequest(request.getId());
		rule.setOperation(RequestOperationType.ADD);
		rule.setAttributeName(IdmIdentity_.username.getName());
		rule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
		rule.setType(AutomaticRoleAttributeRuleType.IDENTITY);
		rule.setValue("test");
		rule = ruleRequestService.save(rule);

		// We expect exception state (we don`t have right for execute without approval)
		roleRequestService.startRequestInternal(request.getId(), true);
	}

	@Test
	public void testCreateAutomaticAttributeRoleWithApproval() {
		IdmRoleDto role = prepareRole();
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityDto guaranteeIdentity = getHelper().createIdentity();
		getHelper().createRoleGuarantee(role, guaranteeIdentity);

		IdmAutomaticRoleAttributeDto automaticRole = new IdmAutomaticRoleAttributeDto();
		automaticRole.setRole(role.getId());
		automaticRole.setName(role.getCode());

		IdmAutomaticRoleAttributeRuleDto rule = new IdmAutomaticRoleAttributeRuleDto();
		rule.setAttributeName(IdmIdentity_.username.getName());
		rule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
		rule.setType(AutomaticRoleAttributeRuleType.IDENTITY);
		rule.setValue(identity.getUsername());

		// Create automatic role via manager
		try {
			automaticRole = automaticRoleManager.createAutomaticRoleByAttribute(automaticRole, false, rule);
		} catch (AcceptedException ex) {
			// The request is in approval
			Assert.assertNotNull(ex.getIdentifier());
			UUID requestId = UUID.fromString(ex.getIdentifier());
			loginAsNoAdmin(guaranteeIdentity.getUsername());
			try {
				completeTasksFromUsers(guaranteeIdentity.getUsername(), "approve");
			} catch (ResultCodeException e) {
				fail("User has permission to approve task. Error message: " + e.getLocalizedMessage());
			} catch (Exception e) {
				fail("Some problem: " + e.getLocalizedMessage());
			}

			IdmAutomaticRoleRequestDto request = roleRequestService.get(requestId);
			Assert.assertEquals(RequestState.EXECUTED, request.getState());
			Assert.assertNotNull(request.getAutomaticRole());

			automaticRole = automaticRoleAttributeService.get(request.getAutomaticRole());
			Assert.assertNotNull(automaticRole);
			Assert.assertEquals(role.getId(), automaticRole.getRole());
			return;
		}
		fail("Automatic role request have to be approving by gurantee!");
	}

	@Test
	public void testCreateAutomaticAttributeRoleWithApprovalDisapprove() {
		IdmRoleDto role = prepareRole();
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityDto guaranteeIdentity = getHelper().createIdentity();
		getHelper().createRoleGuarantee(role, guaranteeIdentity);

		IdmAutomaticRoleAttributeDto automaticRole = new IdmAutomaticRoleAttributeDto();
		automaticRole.setRole(role.getId());
		automaticRole.setName(role.getCode());

		IdmAutomaticRoleAttributeRuleDto rule = new IdmAutomaticRoleAttributeRuleDto();
		rule.setAttributeName(IdmIdentity_.username.getName());
		rule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
		rule.setType(AutomaticRoleAttributeRuleType.IDENTITY);
		rule.setValue(identity.getUsername());

		// Create automatic role via manager
		try {
			automaticRole = automaticRoleManager.createAutomaticRoleByAttribute(automaticRole, false, rule);
		} catch (AcceptedException ex) {
			// The request is in approval
			Assert.assertNotNull(ex.getIdentifier());
			UUID requestId = UUID.fromString(ex.getIdentifier());
			loginAsNoAdmin(guaranteeIdentity.getUsername());
			try {
				completeTasksFromUsers(guaranteeIdentity.getUsername(), "disapprove");
			} catch (ResultCodeException e) {
				fail("User has permission to approve task. Error message: " + e.getLocalizedMessage());
			} catch (Exception e) {
				fail("Some problem: " + e.getLocalizedMessage());
			}

			IdmAutomaticRoleRequestDto request = roleRequestService.get(requestId);
			Assert.assertEquals(RequestState.DISAPPROVED, request.getState());
			Assert.assertNull(request.getAutomaticRole());
			return;
		}
		fail("Automatic role request have to be approving by gurantee!");
	}

	@Test
	public void testCreateTreeAutomaticRoleWithApproval() {
		IdmRoleDto role = prepareRole();
		IdmTreeNodeDto nodeOne = getHelper().createTreeNode();
		IdmIdentityDto guaranteeIdentity = getHelper().createIdentity();
		getHelper().createRoleGuarantee(role, guaranteeIdentity);

		IdmRoleTreeNodeDto automaticRole = new IdmRoleTreeNodeDto();
		automaticRole.setRole(role.getId());
		automaticRole.setName(role.getCode());
		automaticRole.setTreeNode(nodeOne.getId());

		// Create automatic role via manager
		try {
			automaticRole = automaticRoleManager.createAutomaticRoleByTree(automaticRole, false);
		} catch (AcceptedException ex) {
			// The request is in approval
			Assert.assertNotNull(ex.getIdentifier());
			UUID requestId = UUID.fromString(ex.getIdentifier());
			loginAsNoAdmin(guaranteeIdentity.getUsername());
			try {
				completeTasksFromUsers(guaranteeIdentity.getUsername(), "approve");
			} catch (ResultCodeException e) {
				fail("User has permission to approve task. Error message: " + e.getLocalizedMessage());
			} catch (Exception e) {
				fail("Some problem: " + e.getLocalizedMessage());
			}

			IdmAutomaticRoleRequestDto request = roleRequestService.get(requestId);
			Assert.assertEquals(RequestState.EXECUTED, request.getState());
			Assert.assertNotNull(request.getAutomaticRole());

			IdmRoleTreeNodeDto treeAutomaticRole = roleTreeNodeService.get(request.getAutomaticRole());
			Assert.assertNotNull(treeAutomaticRole);
			Assert.assertEquals(nodeOne.getId(), treeAutomaticRole.getTreeNode());
			Assert.assertEquals(role.getId(), treeAutomaticRole.getRole());
			return;
		}
		fail("Automatic role request have to be approving by gurantee!");
	}
	
	
	/**
	 * Tests aimed on approve-role-by-guarantee-security WF
	 * 
	 */
	@Test
	public void testTreeAutomaticRoleApprovalWithSecurityWF() {
		IdmRoleDto role = prepareRole();
		configurationService.setValue(IdmRoleService.WF_BY_ROLE_PRIORITY_PREFIX + role.getPriority(),
				APPROVE_ROLE_BY_GUARANTEE_AND_SECURITY_KEY);
		
		IdmTreeNodeDto nodeOne = getHelper().createTreeNode();
		IdmIdentityDto guaranteeIdentity = getHelper().createIdentity();
		getHelper().createRoleGuarantee(role, guaranteeIdentity);

		IdmRoleTreeNodeDto automaticRole = new IdmRoleTreeNodeDto();
		automaticRole.setRole(role.getId());
		automaticRole.setName(role.getCode());
		automaticRole.setTreeNode(nodeOne.getId());

		// Create automatic role via manager
		try {
			automaticRole = automaticRoleManager.createAutomaticRoleByTree(automaticRole, false);
		} catch (AcceptedException ex) {
			// The request is in approval
			Assert.assertNotNull(ex.getIdentifier());
			UUID requestId = UUID.fromString(ex.getIdentifier());
			loginAsNoAdmin(guaranteeIdentity.getUsername());
			try {
				// by guarantor
				completeTasksFromUsers(guaranteeIdentity.getUsername(), "approve");
				// by security
				loginAsAdmin();
				completeTasksFromUsers(TestHelper.ADMIN_USERNAME, "approve");
			} catch (ResultCodeException e) {
				fail("User has permission to approve task. Error message: " + e.getLocalizedMessage());
			} catch (Exception e) {
				fail("Some problem: " + e.getLocalizedMessage());
			}

			IdmAutomaticRoleRequestDto request = roleRequestService.get(requestId);
			Assert.assertEquals(RequestState.EXECUTED, request.getState());
			Assert.assertNotNull(request.getAutomaticRole());

			IdmRoleTreeNodeDto treeAutomaticRole = roleTreeNodeService.get(request.getAutomaticRole());
			Assert.assertNotNull(treeAutomaticRole);
			Assert.assertEquals(nodeOne.getId(), treeAutomaticRole.getTreeNode());
			Assert.assertEquals(role.getId(), treeAutomaticRole.getRole());
			return;
		} finally {
			configurationService.setValue(IdmRoleService.WF_BY_ROLE_PRIORITY_PREFIX + role.getPriority(),
					APPROVE_ROLE_BY_GUARANTEE_KEY);
		}
		fail("Automatic role request have to be approving by gurantee!");
	}
	
	@Test
	public void testTreeAutomaticRoleDisapprovalWithSecurityWF() {
		IdmRoleDto role = prepareRole();
		configurationService.setValue(IdmRoleService.WF_BY_ROLE_PRIORITY_PREFIX + role.getPriority(),
				APPROVE_ROLE_BY_GUARANTEE_AND_SECURITY_KEY);
		
		IdmTreeNodeDto nodeOne = getHelper().createTreeNode();
		IdmIdentityDto guaranteeIdentity = getHelper().createIdentity();
		getHelper().createRoleGuarantee(role, guaranteeIdentity);

		IdmRoleTreeNodeDto automaticRole = new IdmRoleTreeNodeDto();
		automaticRole.setRole(role.getId());
		automaticRole.setName(role.getCode());
		automaticRole.setTreeNode(nodeOne.getId());

		// Create automatic role via manager
		try {
			automaticRole = automaticRoleManager.createAutomaticRoleByTree(automaticRole, false);
		} catch (AcceptedException ex) {
			// The request is in approval
			Assert.assertNotNull(ex.getIdentifier());
			UUID requestId = UUID.fromString(ex.getIdentifier());
			loginAsNoAdmin(guaranteeIdentity.getUsername());
			try {
				// by guarantor
				completeTasksFromUsers(guaranteeIdentity.getUsername(), "disapprove");
			} catch (ResultCodeException e) {
				fail("User has permission to approve task. Error message: " + e.getLocalizedMessage());
			} catch (Exception e) {
				fail("Some problem: " + e.getLocalizedMessage());
			}

			IdmAutomaticRoleRequestDto request = roleRequestService.get(requestId);
			Assert.assertEquals(RequestState.DISAPPROVED, request.getState());
			Assert.assertNull(request.getAutomaticRole());
			return;
		} finally {
			configurationService.setValue(IdmRoleService.WF_BY_ROLE_PRIORITY_PREFIX + role.getPriority(),
					APPROVE_ROLE_BY_GUARANTEE_KEY);
		}
		fail("Automatic role request have to be approving by gurantee!");
	}

	/**
	 * Complete all tasks from user given in parameters. Complete will be done by
	 * currently logged user.
	 * 
	 * @param approverUser
	 * @param decision
	 */
	private void completeTasksFromUsers(String approverUser, String decision) {
		WorkflowFilterDto taskFilter = new WorkflowFilterDto();
		taskFilter.setCandidateOrAssigned(approverUser);
		List<WorkflowTaskInstanceDto> tasks = workflowTaskInstanceService.find(taskFilter, null).getContent();
		//
		for (WorkflowTaskInstanceDto task : tasks) {
			workflowTaskInstanceService.completeTask(task.getId(), decision);
		}
	}
}
