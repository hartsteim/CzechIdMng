package eu.bcvsolutions.idm.acc.integration;

import static org.junit.Assert.fail;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.schema.Schema;
import com.unboundid.ldif.LDIFException;
import com.unboundid.ldif.LDIFReader;

import eu.bcvsolutions.idm.acc.TestHelper;
import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.ReconciliationMissingAccountActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationLinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationMissingEntityActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationUnlinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.ProvisioningAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysConnectorKeyDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningOperationDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncContractConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncIdentityConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysProvisioningOperationFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncConfigFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemAttributeMappingFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemFilter;
import eu.bcvsolutions.idm.acc.entity.TestContractResource;
import eu.bcvsolutions.idm.acc.entity.TestResource;
import eu.bcvsolutions.idm.acc.scheduler.task.impl.SynchronizationSchedulableTaskExecutor;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningOperationService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncConfigService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.config.domain.EventConfiguration;
import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleComparison;
import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleType;
import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.IdentityState;
import eu.bcvsolutions.idm.core.api.domain.IdmScriptCategory;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.RecursionType;
import eu.bcvsolutions.idm.core.api.domain.ScriptAuthorityType;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeRuleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmEntityStateDto;
import eu.bcvsolutions.idm.core.api.dto.IdmGenerateValueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCompositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmScriptAuthorityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmScriptDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeTypeDto;
import eu.bcvsolutions.idm.core.api.dto.OperationResultDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmGenerateValueFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityContractFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmTreeTypeFilter;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EntityEventProcessor;
import eu.bcvsolutions.idm.core.api.service.EntityStateManager;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeRuleService;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeService;
import eu.bcvsolutions.idm.core.api.service.IdmGenerateValueService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCompositionService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.IdmScriptAuthorityService;
import eu.bcvsolutions.idm.core.api.service.IdmScriptService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeTypeService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmFormAttributeFilter;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmFormDefinitionFilter;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
import eu.bcvsolutions.idm.core.generator.identity.IdentityEmailGenerator;
import eu.bcvsolutions.idm.core.generator.identity.IdentityUsernameGenerator;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.model.event.RoleEvent;
import eu.bcvsolutions.idm.core.model.event.RoleEvent.RoleEventType;
import eu.bcvsolutions.idm.core.scheduler.api.config.SchedulerConfiguration;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.LongRunningFutureTask;
import eu.bcvsolutions.idm.core.scheduler.api.dto.Task;
import eu.bcvsolutions.idm.core.scheduler.api.dto.filter.IdmLongRunningTaskFilter;
import eu.bcvsolutions.idm.core.scheduler.api.service.IdmLongRunningTaskService;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.core.scheduler.api.service.SchedulerManager;
import eu.bcvsolutions.idm.core.scheduler.task.impl.hr.HrContractExclusionProcess;
import eu.bcvsolutions.idm.core.scheduler.task.impl.hr.HrEnableContractProcess;
import eu.bcvsolutions.idm.core.scheduler.task.impl.hr.HrEndContractProcess;
import eu.bcvsolutions.idm.ic.impl.IcConnectorKeyImpl;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * This class represents the set of IdM tests focused on the whole chain of actions performed in the IdM.
 * Tests perform identity and contract synchronization which are followed by generating of identity attributes such as an email etc. and automatic role assignment.
 * There are evaluated identity states, contract states, assigned roles and attributes prepared for provisioning into a target system.
 * Tests are supposed to prove that HR processes, role recalculation and other bound processes work properly.
 * 
 * @author Ondrej Husnik
 *
 */
public class ComplexHrProcessIntegrationTest extends AbstractIntegrationTest {

	private static String identitySysName = "hr_identity_system";
	private static String contractSysName = "HR contracts";
	private static String stringToLocalDateScript = "convertStringToLocalDate";
	private static String getIdentityUuidByPersonalNumScript = "getIdentityUuidByPersonalNumber";
	
	private static String targetAdSystem = "AD-system"; // Ldap server used
	// private static String targetSystem2 = "System2";
	
	// tree node name
	private static String orgTreeTypeName = "ORGANIZATIONS";
	private static String rootNodeName = "root";
	private static String dep1NodeName = "Department1";
	private static String dep2NodeName = "Department2";
	
	// contract
	private static String consultantPositionName = "consultant";
	private static String directorPositionName = "director";
	
	// role catalogues
	private static String adGroupCat = "AD groups";
	private static String businessRoleCat = "Business roles";
	
	// roles 
	private static String adGroupAllRole = "AD-group-all";
	private static String adGroupPkiRole = "AD-group-pki";
	private static String adGroupDep1Role = "AD-group-department1";
	private static String adGroupDep2Role = "AD-group-department2";
	private static String adGroupCons1Role = "AD-group-consultant1";
	private static String adGroupCons2Role = "AD-group-consultant2";
	private static String adGroupDirectorRole = "AD-group-director";
	private static String adUsersRole = "AD-users";
	private static String consultantBusinessRole = "Consultant";
	private static String allBusinessRole = "All";
	private static String system2ManualRole = "System2-users";
	
	// autorole by attribute name
	private static String eavAutoRoleAttrName = "contractType";
	
	// identities
	private static String dvorakUsername = "jan.dvorak";
	private static String novakUsername = "petr.novak";
	// contracts
	private final static String dvorakContractId0 = "0";
	private final static String dvorakContractId1 = "2";
	private final static String dvorakContractId2 = "3";
	private final static String novakContractId0 = "1";
	
	// Ldap server attributes
	InMemoryDirectoryServer directoryServer = null;
	LDAPConnection ldapConnectionInfo = null;
	private static String ldapBaseOU = "dc=bcv,dc=cz";
	// private static String ldapGroupOU = "groups";
	private static String ldapUserOU = "people";
	private static String ldapAdminLogin = "uid=admin";
	private static String ldapPassword = "password";

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ComplexHrProcessIntegrationTest.class);

	@Autowired private ApplicationContext applicationContext;
	@Qualifier("entityManager")
	@Autowired private EntityManager entityManager;
	@Autowired private SysSystemService systemService;
	@Autowired private IdmTreeNodeService treeNodeService;
	@Autowired private IdmTreeTypeService treeTypeService;
	@Autowired private IdmRoleTreeNodeService roleTreeNodeService;
	@Autowired private IdmRoleCatalogueService roleCatalogueService;
	@Autowired private IdmRoleService roleService;
	@Autowired private IdmRoleCatalogueRoleService roleCatalogueRoleService;
	@Autowired private IdmAutomaticRoleAttributeService autoRoleAttrService;
	@Autowired private IdmAutomaticRoleAttributeRuleService autoRoleAttrRuleService;
	@Autowired private IdmIdentityService identityService;
	@Autowired private IdmIdentityRoleService identityRoleService;
	@Autowired private IdmFormAttributeService formAttrService;
	@Autowired private IdmFormDefinitionService formDefService;
	@Autowired private IdmRoleCompositionService roleCompositionService;
	@Autowired private IdmGenerateValueService generatedAttributeService;
	@Autowired private TestHelper accTestHelper;
	@Autowired private SysSystemMappingService systemMappingService;
	@Autowired private SysSchemaAttributeService schemaAttributeService;
	@Autowired private SysSystemAttributeMappingService systemAttributeMappingService;
	@Autowired private SysSyncConfigService sysSyncConfigService;
	@Autowired private SysRoleSystemService sysRoleService;
	@Autowired private IdmScriptService scriptService;
	@Autowired private IdmScriptAuthorityService scriptAuthorityService;
	@Autowired private IdmIdentityContractService identityContractService;
	@Autowired private SysRoleSystemAttributeService  roleSystemAttributeService;
	@Autowired private SysProvisioningOperationService provisioningOperationService;
	@Autowired private SchedulerManager schedulerManager;
	@Autowired private FormService formService;
	@Autowired private LongRunningTaskManager longRunningTaskManager;
	@Autowired private IdmLongRunningTaskService longRunningTaskService;
	@Autowired private EntityStateManager entityStateManager;

	@Before
	public void init() {
		createUniqueNames();
		disableAsyncProcessing();
		testEnvironmentInitialization();
	}
	
	@After
	public void cleanUp() {
		testEnvironmentCleanup();
	}
	
	
	/*********************** Test scenario implementation ***********************/
	
	/**
	 * Test scenario from general IdM functionality test
	 * Corresponds to BCV test scenario described in the Redmine ticket 2859
	 */
	
	
	/**
	 * The test case covers a situation of the first synchronization of a user and a contract into IdM 
	 */
	@Test
	public void tc01NewEmploymentCommencing() {
		// test case data preparation
		Map<String,String> identityPattern = getIdentityPattern(dvorakUsername);
		Map<String,String> contractPattern = getContractPattern(dvorakContractId0, LocalDate.now().minusDays(2).toString(), LocalDate.now().plusDays(2).toString());
		List<String> expectedRoleGroups = Arrays.asList(adGroupDep1Role, adGroupAllRole, adGroupPkiRole, adGroupCons1Role, adGroupCons2Role);
		
		// init HR system and start synchronization
		getBean().addIdentityToHRTable(identityPattern);
		getBean().addContractToHRTable(contractPattern);
		synchronizeHRSystems();
		
		// identity state evaluation
		matchIdentityWithPattern(dvorakUsername, identityPattern);
		checkIdentityState(dvorakUsername, IdentityState.VALID);
		
		// contract state evaluation
		matchContractWithPattern(dvorakUsername, contractPattern);
		
		// role assignement evaluation
		List<String> assignedRoles = new ArrayList<String>(expectedRoleGroups);
		assignedRoles.addAll(Arrays.asList(adUsersRole,allBusinessRole,consultantBusinessRole));
		for (String roleCode : assignedRoles) {
			Map<String,String> rolePattern = getRolePattern(roleCode, contractPattern.get("validFrom"), contractPattern.get("validTill"));
			checkRoleAssigned(contractPattern.get("extId"), rolePattern);
 		}
		
		// provisioning evaluation
		Map<String, Object> expectedValues = getLdapProvisioningValues(identityPattern, expectedRoleGroups);
		checkProvisioningQueue(targetAdSystem, expectedValues);
	}
	
	
	
	/*********************** Preparation of the test envirnonment *************/
	
	
	private void createUniqueNames() {
		identitySysName += getHelper().createName();
		contractSysName += getHelper().createName();
		stringToLocalDateScript += getHelper().createName();
		getIdentityUuidByPersonalNumScript += getHelper().createName();
		
		targetAdSystem += getHelper().createName(); // Ldap server used
		// targetSystem2 += getHelper().createName();
		
		// tree node name
		//orgTreeTypeName += getHelper().createName();
		rootNodeName += getHelper().createName();
		dep1NodeName += getHelper().createName();
		dep2NodeName += getHelper().createName();
		
		// contract
		consultantPositionName += getHelper().createName();
		directorPositionName += getHelper().createName();
		
		// role catalogues
		adGroupCat += getHelper().createName();
		businessRoleCat += getHelper().createName();
		
		// roles 
		adGroupAllRole += getHelper().createName();
		adGroupPkiRole += getHelper().createName();
		adGroupDep1Role += getHelper().createName();
		adGroupDep2Role += getHelper().createName();
		adGroupCons1Role += getHelper().createName();
		adGroupCons2Role += getHelper().createName();
		adGroupDirectorRole += getHelper().createName();
		adUsersRole += getHelper().createName();
		consultantBusinessRole += getHelper().createName();
		allBusinessRole += getHelper().createName();
		system2ManualRole += getHelper().createName();
		
		// autorole by attribute name
		eavAutoRoleAttrName += getHelper().createName();
	}	 
	
	/**
	 * Wrapper of the complete initialization
	 */
	private void testEnvironmentInitialization() {
		//
		initOrgStructure();
		addEavIdentityContractFormDef();
		createGenerators();
		createScriptDefinitions();
		// role initialization
		createRoleCatalogues();
		createRoles();
		addRolesToCatalogue();
		createAutoRoleByAttribute();
		createAutoRoleByStructure();
		createBusinessRole();
		// source system  init
		configureHrContractLrt();
		createHrIdentitySystem();
		createHrContractSystem();
		// target systems
		initLdapDestinationSystem();
	}
	
	private void testEnvironmentCleanup() {
		disableAsyncProcessing();	
		identityCleanup();		
		roleCleanup();
		roleCatalogueCleanup();
		otherStuffClenaup();
		organizationStructureCleanup();
		systemCleanup();
	}
	
	/**
	 * Creates organization structure common for all test cases
	 * 
	 */
	private void initOrgStructure() {
		IdmTreeNodeDto root = null;
		IdmTreeNodeDto node = null;
		
		// get tree type dto
		IdmTreeTypeFilter typeFilt = new IdmTreeTypeFilter();
		typeFilt.setCode(orgTreeTypeName);
		IdmTreeTypeDto treeType = treeTypeService.find(typeFilt, null).getContent().get(0);  
		
		IdmTreeNodeFilter filter = new IdmTreeNodeFilter();
		filter.setCode(rootNodeName);
		filter.setTreeTypeId(treeType.getId());
		List<IdmTreeNodeDto> roots = treeNodeService.find(filter, null).getContent(); 
		if (roots.size() == 0) {
			root = new IdmTreeNodeDto();
			root.setCode(rootNodeName);
			root.setName(rootNodeName);
			root.setTreeType(treeType.getId());
			root = treeNodeService.save(root);
		} else {
			root = roots.get(0);
		}
		// Department1
		node = new IdmTreeNodeDto();
		node.setCode(dep1NodeName);
		node.setName(dep1NodeName);
		node.setTreeType(treeType.getId());
		node.setParent(root.getId());
		node = treeNodeService.save(node);
		// Department2
		node = new IdmTreeNodeDto();
		node.setCode(dep2NodeName);
		node.setName(dep2NodeName);
		node.setTreeType(treeType.getId());
		node.setParent(root.getId());
		node = treeNodeService.save(node);
	}
	
	
	/********************************************************************************************
	 * ***********************************  ROLE PREPARATION  ***********************************
	 */
	
	/**
	 * Creates role catalogue items for all test cases
	 */
	private void createRoleCatalogues() {
		IdmRoleCatalogueDto catalogue = new IdmRoleCatalogueDto();
		catalogue.setCode(adGroupCat);
		catalogue.setName(adGroupCat);
		roleCatalogueService.save(catalogue);
		
		catalogue = new IdmRoleCatalogueDto();
		catalogue.setCode(businessRoleCat);
		catalogue.setName(businessRoleCat);
		roleCatalogueService.save(catalogue);
	}
	
	/**
	 * Creates all roles used in tests
	 */
	private void createRoles() {
		Set<String> names = new HashSet<String>(Arrays.asList(
		 	adGroupAllRole, adGroupPkiRole, adGroupDep1Role,
			adGroupDep2Role, adGroupCons1Role, adGroupCons2Role,
			adGroupDirectorRole, consultantBusinessRole, allBusinessRole,
			adUsersRole, system2ManualRole));
		for (String name : names) {
			IdmRoleDto role = new IdmRoleDto();
			role.setCode(name);
			role.setName(name);
			role.setCanBeRequested(true);
			role.setPriority(0);
			roleService.save(role);
		}
	}
	
	/**
	 * Method adding roles to catalogues
	 */
	private void addRolesToCatalogue() {
		IdmRoleCatalogueFilter catalogueFilt = new IdmRoleCatalogueFilter();
		IdmRoleFilter roleFilt = new IdmRoleFilter();
		IdmRoleCatalogueRoleDto roleCatRole = new IdmRoleCatalogueRoleDto();
		
		// AD Groups catalogue
		roleFilt.setText("AD-group");
		List<IdmRoleDto> roles = roleService.find(roleFilt, null).getContent();
		catalogueFilt.setCode(adGroupCat);
		IdmRoleCatalogueDto catalogue = roleCatalogueService.find(catalogueFilt, null).getContent().get(0);
		
		for (IdmRoleDto role : roles) {
			
			roleCatRole.setRoleCatalogue(catalogue.getId());
			roleCatRole.setRole(role.getId());
			roleCatalogueRoleService.save(roleCatRole);
		}
		
		// Business roles
		catalogueFilt.setCode(businessRoleCat);
		catalogue = roleCatalogueService.find(catalogueFilt, null).getContent().get(0);
		IdmRoleDto role = roleService.getByCode(consultantBusinessRole);
		roleCatRole = new IdmRoleCatalogueRoleDto();
		roleCatRole.setRoleCatalogue(catalogue.getId());
		roleCatRole.setRole(role.getId());
		roleCatalogueRoleService.save(roleCatRole);
		
		role = roleService.getByCode(allBusinessRole);
		roleCatRole = new IdmRoleCatalogueRoleDto();
		roleCatRole.setRoleCatalogue(catalogue.getId());
		roleCatRole.setRole(role.getId());
		roleCatalogueRoleService.save(roleCatRole);
	}
	
	/**
	 * Defines automatic roles assigned based on organization structure
	 */
	private void createAutoRoleByStructure() {
		IdmTreeNodeFilter treeNodeFilt = new IdmTreeNodeFilter();
		IdmRoleFilter roleFilt = new IdmRoleFilter();
		IdmRoleTreeNodeDto roleTreeNode = null;
		IdmRoleDto role = null;
		
		// root
		treeNodeFilt.setCode(rootNodeName);
		IdmTreeNodeDto node = treeNodeService.find(treeNodeFilt, null).getContent().get(0);
		
		// node root, role AD-users
		roleFilt.setText(adUsersRole);
		role = roleService.find(roleFilt, null).getContent().get(0);
		roleTreeNode = new IdmRoleTreeNodeDto();
		roleTreeNode.setName(adUsersRole);
		roleTreeNode.setTreeNode(node.getId());
		roleTreeNode.setRole(role.getId());
		roleTreeNode.setRecursionType(RecursionType.DOWN);
		roleTreeNodeService.save(roleTreeNode);

		// node root, role All
		roleFilt.setText(allBusinessRole);
		role = roleService.find(roleFilt, null).getContent().get(0);
		roleTreeNode = new IdmRoleTreeNodeDto();
		roleTreeNode.setName(allBusinessRole);
		roleTreeNode.setTreeNode(node.getId());
		roleTreeNode.setRole(role.getId());
		roleTreeNode.setRecursionType(RecursionType.DOWN);
		roleTreeNodeService.save(roleTreeNode);
		
		// Department 1 AD-group-department1
		treeNodeFilt.setCode(dep1NodeName);
		node = treeNodeService.find(treeNodeFilt, null).getContent().get(0);
		roleFilt.setText(adGroupDep1Role);
		role = roleService.find(roleFilt, null).getContent().get(0);
		roleTreeNode = new IdmRoleTreeNodeDto();
		roleTreeNode.setName(adGroupDep1Role);
		roleTreeNode.setTreeNode(node.getId());
		roleTreeNode.setRole(role.getId());
		roleTreeNode.setRecursionType(RecursionType.DOWN);
		roleTreeNodeService.save(roleTreeNode);
		
		// Department 2 AD-group-department2
		treeNodeFilt.setCode(dep2NodeName);
		node = treeNodeService.find(treeNodeFilt, null).getContent().get(0);
		roleFilt.setText(adGroupDep2Role);
		role = roleService.find(roleFilt, null).getContent().get(0);
		roleTreeNode = new IdmRoleTreeNodeDto();
		roleTreeNode.setName(adGroupDep2Role);
		roleTreeNode.setTreeNode(node.getId());
		roleTreeNode.setRole(role.getId());
		roleTreeNode.setRecursionType(RecursionType.DOWN);
		roleTreeNodeService.save(roleTreeNode);
	}
	
	/**
	 * Defines automatic roles assigned based on contract attribute
	 */
	private void createAutoRoleByAttribute() {
		IdmAutomaticRoleAttributeRuleDto attrRule = new IdmAutomaticRoleAttributeRuleDto();
		IdmFormAttributeDto formAttr = null;
		IdmAutomaticRoleAttributeDto roleAtt = null;
		
		IdmFormAttributeFilter formAttrFilt = new IdmFormAttributeFilter();
		formAttrFilt.setCode(eavAutoRoleAttrName);
		formAttr = formAttrService.find(formAttrFilt, null).getContent().get(0);
		
		// common part
		attrRule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
		attrRule.setType(AutomaticRoleAttributeRuleType.CONTRACT_EAV);
		attrRule.setAttributeName(formAttr.getName());
		attrRule.setFormAttribute(formAttr.getId());
		
		// Consultant autorole
		roleAtt = new IdmAutomaticRoleAttributeDto();
		roleAtt.setRole(roleService.getByCode(consultantBusinessRole).getId());
		roleAtt.setName("Consultant autorole");
		roleAtt = autoRoleAttrService.save(roleAtt);
	
		attrRule.setValue(consultantPositionName);
		attrRule.setAutomaticRoleAttribute(roleAtt.getId());
		attrRule = autoRoleAttrRuleService.save(attrRule);
		// concept needs to be set to false after rule's been saved
		roleAtt.setConcept(false);
		roleAtt = autoRoleAttrService.save(roleAtt);
		
		// Director autorole
		roleAtt = new IdmAutomaticRoleAttributeDto();
		roleAtt.setRole(roleService.getByCode(adGroupDirectorRole).getId());
		roleAtt.setName("Director autorole");
		roleAtt = autoRoleAttrService.save(roleAtt);
		
		attrRule.setId(null);
		attrRule.setValue(directorPositionName);
		attrRule.setAutomaticRoleAttribute(roleAtt.getId());
		attrRule = autoRoleAttrRuleService.save(attrRule);
		
		// concept needs to be set to false after rule's been saved
		roleAtt.setConcept(false);
		roleAtt = autoRoleAttrService.save(roleAtt);
	}
	
	/**
	 * Aggregation of roles into business roles
	 */
	private void createBusinessRole() {
		IdmRoleDto roleSuper = null;
		IdmRoleDto roleSub = null;
		IdmRoleCompositionDto roleCompose = null;
		
		// All business role
		roleSuper = roleService.getByCode(allBusinessRole);
		// Group all
		roleSub = roleService.getByCode(adGroupAllRole);
		roleCompose = new IdmRoleCompositionDto();
		roleCompose.setSuperior(roleSuper.getId());
		roleCompose.setSub(roleSub.getId());
		roleCompositionService.save(roleCompose);
		// Group Pki
		roleSub = roleService.getByCode(adGroupPkiRole);
		roleCompose = new IdmRoleCompositionDto();
		roleCompose.setSuperior(roleSuper.getId());
		roleCompose.setSub(roleSub.getId());
		roleCompositionService.save(roleCompose);
		
		// Consultant business role
		roleSuper = roleService.getByCode(consultantBusinessRole);
		// Consultant1
		roleSub = roleService.getByCode(adGroupCons1Role);
		roleCompose = new IdmRoleCompositionDto();
		roleCompose.setSuperior(roleSuper.getId());
		roleCompose.setSub(roleSub.getId());
		roleCompositionService.save(roleCompose);
		// Consultant2
		roleSub = roleService.getByCode(adGroupCons2Role);
		roleCompose = new IdmRoleCompositionDto();
		roleCompose.setSuperior(roleSuper.getId());
		roleCompose.setSub(roleSub.getId());
		roleCompositionService.save(roleCompose);
	}
		
	
	/**
	 * Creating required contract EAV attribute for the purpose of automatic roles assignment 
	 */
	private void addEavIdentityContractFormDef() {
		IdmFormDefinitionFilter filt = new IdmFormDefinitionFilter();
		filt.setType(IdmIdentityContract.class.getCanonicalName());
		filt.setMain(true);
		IdmFormDefinitionDto formDef = formDefService.find(filt, null).getContent().get(0);
		
		IdmFormAttributeFilter formAttrFilt = new IdmFormAttributeFilter();
		formAttrFilt.setCode(eavAutoRoleAttrName);
		formAttrFilt.setDefinitionId(formDef.getId());
		if (formAttrService.count(formAttrFilt) > 0) { // already created
			return;
		}
		
		IdmFormAttributeDto formAttr = new IdmFormAttributeDto();
		formAttr.setCode(eavAutoRoleAttrName);
		formAttr.setName(eavAutoRoleAttrName);
		formAttr.setPersistentType(PersistentType.SHORTTEXT);
		formAttr.setFormDefinition(formDef.getId());
		formAttrService.save(formAttr);
	}
	
	/**
	 * Configuration of necessary generators
	 * 1) username generator
	 * 2) email generator
	 */
	private void createGenerators() {
		Map<String, String> configUsername = ImmutableMap.of(
				IdentityUsernameGenerator.SEARCH_UNIQUE_USERNAME, Boolean.TRUE.toString(),
				IdentityUsernameGenerator.CONNECTING_CHARACTER, ".",
				IdentityUsernameGenerator.FIRST_NAME_FIRST, Boolean.TRUE.toString()				
				);
		
		IdmGenerateValueDto usernameGen = new IdmGenerateValueDto();
		usernameGen.setDtoType(IdmIdentityDto.class.getCanonicalName());
		usernameGen.setGeneratorType(IdentityUsernameGenerator.class.getCanonicalName());
		usernameGen.setSeq((short)0);
		usernameGen.setUnmodifiable(false);
		usernameGen.setGeneratorProperties(new ConfigurationMap(configUsername));
		generatedAttributeService.save(usernameGen);
				
		
		Map<String, String> emailConfig = ImmutableMap.of(
				IdentityEmailGenerator.PROPERTY_UNIQUE_EMAIL, Boolean.TRUE.toString(),
				IdentityEmailGenerator.CONNECTING_CHARACTER, ".",
				IdentityEmailGenerator.EMAIL_SUFFIX, "company.cz"				
				);
		
		IdmGenerateValueDto emailGen = new IdmGenerateValueDto();
		emailGen.setDtoType(IdmIdentityDto.class.getCanonicalName());
		emailGen.setGeneratorType(IdentityEmailGenerator.class.getCanonicalName());
		emailGen.setSeq((short)0);
		emailGen.setUnmodifiable(false);
		emailGen.setGeneratorProperties(new ConfigurationMap(emailConfig));
		generatedAttributeService.save(emailGen);
	}

	/**
	 * Prepare HrContract LRTs - End, Enable, Exclude
	 * They have to be run manually before they can be triggered by sync 
	 */
	private void configureHrContractLrt() {
		List<Task> tasks = schedulerManager.getAllTasks();
		tasks = tasks.stream().filter(task -> {
			return task.getTaskType().equals(HrContractExclusionProcess.class) ||
					task.getTaskType().equals(HrEndContractProcess.class) ||
					task.getTaskType().equals(HrEnableContractProcess.class);
		}).collect(Collectors.toList());
		
		tasks.stream().forEach(task -> {
			schedulerManager.runTask(task.getId());
		});
	}
		
	/************************************************************************************************************
	 * ***************************************  SYSTEM INITIALIZATION  ****************************************** 
	 */
	
	/************ SOURCE HR Identity and Contract systems ***************/
	
	private void createHrIdentitySystem() {
		getBean().deleteAllResourceData(TestResource.TABLE_NAME);
		// create new system
		SysSystemDto system =  accTestHelper.createSystem(TestResource.TABLE_NAME, identitySysName, null, "NAME");
		system.setReadonly(true);
		system.setDisabledProvisioning(true);
		system = systemService.save(system);
		
		List<IdmFormValueDto> values = new ArrayList<IdmFormValueDto>();
		IdmFormDefinitionDto savedFormDefinition = systemService.getConnectorFormDefinition(system);
		IdmFormValueDto changeLogColumnValue = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("changeLogColumn"));
		changeLogColumnValue.setValue("MODIFIED");
		values.add(changeLogColumnValue);
		formService.saveValues(system, savedFormDefinition, values);

		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);
		
		// Create synchronization mapping
		SysSystemMappingDto syncSystemMapping = new SysSystemMappingDto();
		syncSystemMapping.setName(getHelper().createName());
		syncSystemMapping.setEntityType(SystemEntityType.IDENTITY);
		syncSystemMapping.setOperationType(SystemOperationType.SYNCHRONIZATION);
		syncSystemMapping.setObjectClass(objectClasses.get(0).getId());
		syncSystemMapping = systemMappingService.save(syncSystemMapping);
		
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());
		List<SysSchemaAttributeDto> schemaAttributes = schemaAttributeService.find(schemaAttributeFilter, null).getContent();
		
		for (SysSchemaAttributeDto schemaAttr : schemaAttributes) {
			SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
			attributeMapping.setSchemaAttribute(schemaAttr.getId());
			attributeMapping.setSystemMapping(syncSystemMapping.getId());
			attributeMapping.setEntityAttribute(true);
			
			// just id, not mapped to attribute
			if (StringUtils.equalsIgnoreCase("__NAME__", schemaAttr.getName())) {
				attributeMapping.setUid(true);
				attributeMapping.setEntityAttribute(false);
				attributeMapping.setName(schemaAttr.getName());
				
			} else if (StringUtils.equalsIgnoreCase("FIRSTNAME", schemaAttr.getName())) {
				attributeMapping.setName(schemaAttr.getName().toLowerCase());
				attributeMapping.setIdmPropertyName("firstName");
				
			} else if (StringUtils.equalsIgnoreCase("lastname", schemaAttr.getName())) {
				attributeMapping.setName(schemaAttr.getName().toLowerCase());
				attributeMapping.setIdmPropertyName("lastName");
				
			} else if (StringUtils.equalsIgnoreCase("TITLE_BEFORE", schemaAttr.getName())) {
				attributeMapping.setName(schemaAttr.getName().toLowerCase());
				attributeMapping.setIdmPropertyName("titleBefore");
				
			} else if (StringUtils.equalsIgnoreCase("TITLE_AFTER", schemaAttr.getName())) {
				attributeMapping.setName(schemaAttr.getName().toLowerCase());
				attributeMapping.setIdmPropertyName("titleAfter");
				
			} else if (StringUtils.equalsIgnoreCase("PERSONAL_NUMBER", schemaAttr.getName())) {
				attributeMapping.setName(schemaAttr.getName().toLowerCase());
				attributeMapping.setIdmPropertyName("externalCode");
				
			} else { // skip those undefined
				continue;
			}
			attributeMapping = systemAttributeMappingService.save(attributeMapping);
		}
		
		// Create default synchronization config
		SysSystemAttributeMappingFilter mapAttrFilt = new SysSystemAttributeMappingFilter();
		mapAttrFilt.setSystemId(system.getId());
		mapAttrFilt.setName("__NAME__");
		SysSystemAttributeMappingDto correlationAttr = systemAttributeMappingService
				.find(mapAttrFilt, null)
				.getContent().get(0);
		SysSyncIdentityConfigDto syncConfigCustom = new SysSyncIdentityConfigDto();
		syncConfigCustom.setReconciliation(false);
		syncConfigCustom.setCustomFilter(false);
		syncConfigCustom.setSystemMapping(syncSystemMapping.getId());
		syncConfigCustom.setCorrelationAttribute(correlationAttr.getId());
		syncConfigCustom.setName(identitySysName+"-SYNC");
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UPDATE_ENTITY);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.LINK);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.CREATE_ENTITY);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);
		syncConfigCustom.setStartAutoRoleRec(true);
		syncConfigCustom = (SysSyncIdentityConfigDto) sysSyncConfigService.save(syncConfigCustom);
	}
	
	
	private void createHrContractSystem() {
		getBean().deleteAllResourceData(TestContractResource.TABLE_NAME);
		// create new system
		SysSystemDto system =  accTestHelper.createSystem(TestContractResource.TABLE_NAME, contractSysName, null, "ID");
		system.setReadonly(true);
		system.setDisabledProvisioning(true);
		system = systemService.save(system);
		
		List<IdmFormValueDto> values = new ArrayList<IdmFormValueDto>();
		IdmFormDefinitionDto savedFormDefinition = systemService.getConnectorFormDefinition(system);
		
		IdmFormValueDto changeLogColumnValue = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("changeLogColumn"));
		changeLogColumnValue.setValue("MODIFIED");
		values.add(changeLogColumnValue);
		formService.saveValues(system, savedFormDefinition, values);
		
		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);
		
		// Create synchronization mapping
		SysSystemMappingDto syncSystemMapping = new SysSystemMappingDto();
		syncSystemMapping.setName(getHelper().createName());
		syncSystemMapping.setEntityType(SystemEntityType.CONTRACT);
		syncSystemMapping.setOperationType(SystemOperationType.SYNCHRONIZATION);
		syncSystemMapping.setObjectClass(objectClasses.get(0).getId());
		syncSystemMapping = systemMappingService.save(syncSystemMapping);
		
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());
		List<SysSchemaAttributeDto> schemaAttributes = schemaAttributeService.find(schemaAttributeFilter, null).getContent();
		
		for (SysSchemaAttributeDto schemaAttr : schemaAttributes) {
			SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
			attributeMapping.setSchemaAttribute(schemaAttr.getId());
			attributeMapping.setSystemMapping(syncSystemMapping.getId());
			attributeMapping.setEntityAttribute(true);
			
			// just id, not mapped to attribute
			if (StringUtils.equalsIgnoreCase("ID", schemaAttr.getName())) {
				attributeMapping.setUid(true);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setName(schemaAttr.getName().toLowerCase()); // correlation attr
				attributeMapping.setIdmPropertyName("position"); // used for storing contract id
			} else if (StringUtils.equalsIgnoreCase("STATE", schemaAttr.getName())) {
				attributeMapping.setName(schemaAttr.getName().toLowerCase());
				attributeMapping.setIdmPropertyName("state");
				
			} else if (StringUtils.equalsIgnoreCase("VALIDFROM", schemaAttr.getName())) {
				attributeMapping.setName(schemaAttr.getName().toLowerCase());
				attributeMapping.setIdmPropertyName("validFrom");
				attributeMapping.setTransformFromResourceScript(getScriptCallExpression(stringToLocalDateScript));
				attributeMapping.setCached(true);
				
			} else if (StringUtils.equalsIgnoreCase("VALIDTILL", schemaAttr.getName())) {
				attributeMapping.setName(schemaAttr.getName().toLowerCase());
				attributeMapping.setIdmPropertyName("validTill");
				attributeMapping.setTransformFromResourceScript(getScriptCallExpression(stringToLocalDateScript));
				attributeMapping.setCached(true);
			
				//this maps personalNumber to identity to which contract belongs; transformation script is used
			} else if (StringUtils.equalsIgnoreCase("NAME", schemaAttr.getName())) { // NAME used for personalNumber aka externalCode
				attributeMapping.setName(schemaAttr.getName().toLowerCase());
				attributeMapping.setIdmPropertyName("identity"); // represents IdmIentityDto
				attributeMapping.setTransformFromResourceScript(getScriptCallExpression(getIdentityUuidByPersonalNumScript));
				attributeMapping.setCached(true);
				
			} else if (StringUtils.equalsIgnoreCase("WORKPOSITION", schemaAttr.getName())) {
				attributeMapping.setName(schemaAttr.getName().toLowerCase());
				attributeMapping.setIdmPropertyName("workPosition");
				
			} else if (StringUtils.equalsIgnoreCase("POSITIONS", schemaAttr.getName())) {
				attributeMapping.setName(schemaAttr.getName().toLowerCase());
				attributeMapping.setIdmPropertyName(eavAutoRoleAttrName);
				attributeMapping.setEntityAttribute(false);
				attributeMapping.setExtendedAttribute(true);
				
			} else { // skip those undefined
				continue;
			}
			attributeMapping = systemAttributeMappingService.save(attributeMapping);
		}
		
		// Create default synchronization config
		SysSystemAttributeMappingFilter mapAttrFilt = new SysSystemAttributeMappingFilter();
		mapAttrFilt.setSystemId(system.getId());
		mapAttrFilt.setName("id");
		SysSystemAttributeMappingDto correlationAttr = systemAttributeMappingService
				.find(mapAttrFilt, null)
				.getContent().get(0);
		
		
		IdmTreeTypeFilter typeFilt = new IdmTreeTypeFilter();
		typeFilt.setCode(orgTreeTypeName);
		IdmTreeTypeDto treeType = treeTypeService.find(typeFilt, null).getContent().get(0);
		
		SysSyncContractConfigDto syncConfigCustom = new SysSyncContractConfigDto();
		syncConfigCustom.setReconciliation(false);
		syncConfigCustom.setCustomFilter(false);
		syncConfigCustom.setSystemMapping(syncSystemMapping.getId());
		syncConfigCustom.setCorrelationAttribute(correlationAttr.getId());
		syncConfigCustom.setName(contractSysName+"-SYNC");
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UPDATE_ENTITY);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.LINK);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.CREATE_ENTITY);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);
		syncConfigCustom.setStartOfHrProcesses(true);
		syncConfigCustom.setStartAutoRoleRec(true);
		syncConfigCustom.setDefaultTreeType(treeType.getId());
		syncConfigCustom = (SysSyncContractConfigDto) sysSyncConfigService.save(syncConfigCustom);
		
	}
	
	/**
	 * Creating script definition used for pairing synchronized identity and contracts
	 */
	private void createScriptDefinitions() {
		{ // getIdentityUuidByPersonalNumber
			IdmScriptDto scriptDto = new IdmScriptDto();
			scriptDto.setCode(getIdentityUuidByPersonalNumScript);
			scriptDto.setName(getIdentityUuidByPersonalNumScript);
			scriptDto.setCategory(IdmScriptCategory.TRANSFORM_FROM);
			
			StringBuilder builder = new StringBuilder();
			builder.append("import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;\n");
			builder.append("import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;\n");
			builder.append("import java.util.List;\n");
			builder.append("String externalCode = null;\n");
			builder.append("if (attributeValue == null || attributeValue instanceof String) {\n");
			builder.append("    externalCode = attributeValue;\n");
			builder.append("} else {\n");
			builder.append("    externalCode = attributeValue.toString();\n");
			builder.append("}\n");
			builder.append("IdmIdentityFilter filter = new IdmIdentityFilter();\n");
			builder.append("filter.setExternalCode(externalCode);\n");
			builder.append("List<IdmIdentityDto> identities = identityService.find(filter, null).getContent();\n");
			builder.append("if (identities != null && !identities.isEmpty()) {\n");
			builder.append("    return identities.get(0).getId().toString();\n");
			builder.append("}\n");
			builder.append("return null;\n");
			
			scriptDto.setScript(builder.toString());
			scriptDto = scriptService.save(scriptDto);
			
			IdmScriptAuthorityDto authDto = new IdmScriptAuthorityDto();
			authDto.setType(ScriptAuthorityType.CLASS_NAME);
			authDto.setClassName("eu.bcvsolutions.idm.core.scheduler.task.impl.ExecuteScriptTaskExecutor");
			authDto.setScript(scriptDto.getId());
			scriptAuthorityService.save(authDto);
			
			authDto = new IdmScriptAuthorityDto();
			authDto.setType(ScriptAuthorityType.CLASS_NAME);
			authDto.setClassName("eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto");
			authDto.setScript(scriptDto.getId());
			scriptAuthorityService.save(authDto);
			
			authDto = new IdmScriptAuthorityDto();
			authDto.setType(ScriptAuthorityType.CLASS_NAME);
			authDto.setClassName("eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter");
			authDto.setScript(scriptDto.getId());
			scriptAuthorityService.save(authDto);
			
			authDto = new IdmScriptAuthorityDto();
			authDto.setType(ScriptAuthorityType.CLASS_NAME);
			authDto.setClassName("java.util.List");
			authDto.setScript(scriptDto.getId());
			scriptAuthorityService.save(authDto);
			
			authDto = new IdmScriptAuthorityDto();
			authDto.setType(ScriptAuthorityType.CLASS_NAME);
			authDto.setClassName("org.springframework.data.domain.PageImpl");
			authDto.setScript(scriptDto.getId());
			scriptAuthorityService.save(authDto);
			
			authDto = new IdmScriptAuthorityDto();
			authDto.setType(ScriptAuthorityType.SERVICE);
			authDto.setClassName("eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmIdentityService");
			authDto.setScript(scriptDto.getId());
			authDto.setService("identityService");
			scriptAuthorityService.save(authDto);
		}
		{ // stringToLocalDateScript
			IdmScriptDto scriptDto = new IdmScriptDto();
			scriptDto.setCode(stringToLocalDateScript);
			scriptDto.setName(stringToLocalDateScript);
			scriptDto.setCategory(IdmScriptCategory.TRANSFORM_FROM);
			
			StringBuilder builder = new StringBuilder();
			builder.append("import java.time.LocalDate;\n");
			builder.append("import java.time.format.DateTimeFormatter;\n");
			builder.append("import java.time.temporal.ChronoField;\n");
			builder.append("import java.time.format.DateTimeFormatterBuilder;\n");
			builder.append("if (attributeValue == null) {\n");
			builder.append("    return null;\n");
			builder.append("}\n");
			builder.append("DateTimeFormatter dateFormat = new DateTimeFormatterBuilder()\n");
			builder.append("	.appendPattern(\"yyyy-MM-dd\")\n");
			builder.append("	.appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true)\n");
			builder.append("        .toFormatter();\n");
			builder.append("LocalDate localDate = LocalDate.parse(attributeValue, dateFormat);\n");
			builder.append("return localDate;\n");
			
			scriptDto.setScript(builder.toString());
			scriptDto = scriptService.save(scriptDto);
			
			IdmScriptAuthorityDto authDto = new IdmScriptAuthorityDto();
			authDto.setType(ScriptAuthorityType.CLASS_NAME);
			authDto.setClassName("java.time.LocalDate");
			authDto.setScript(scriptDto.getId());
			scriptAuthorityService.save(authDto);
			
			authDto = new IdmScriptAuthorityDto();
			authDto.setType(ScriptAuthorityType.CLASS_NAME);
			authDto.setClassName("java.time.format.DateTimeFormatter");
			authDto.setScript(scriptDto.getId());
			scriptAuthorityService.save(authDto);
			
			authDto = new IdmScriptAuthorityDto();
			authDto.setType(ScriptAuthorityType.CLASS_NAME);
			authDto.setClassName("java.time.temporal.ChronoField");
			authDto.setScript(scriptDto.getId());
			scriptAuthorityService.save(authDto);
			
			authDto = new IdmScriptAuthorityDto();
			authDto.setType(ScriptAuthorityType.CLASS_NAME);
			authDto.setClassName("java.time.format.DateTimeFormatterBuilder");
			authDto.setScript(scriptDto.getId());
			scriptAuthorityService.save(authDto);
		}
	}
	
	private String getScriptCallExpression(String scriptCode) {
		StringBuilder builder = new StringBuilder();
		builder.append("scriptEvaluator.evaluate(\n");
		builder.append("	    scriptEvaluator.newBuilder()\n");
		builder.append("	        .setScriptCode('");
		builder.append(scriptCode);
		builder.append("')\n");
		builder.append("	        .addParameter('scriptEvaluator', scriptEvaluator)\n");
		builder.append("	        .addParameter('attributeValue', attributeValue)\n");
		builder.append("	        .addParameter('icAttributes', icAttributes)\n");
		builder.append("	        .addParameter('system', system)\n");
		builder.append("		.build());\n");
		return builder.toString();
	}
	
	/**
	 * Wrapper function triggering defined HR system sychronisation
	 * @param systemName
	 * @return
	 */
	private IdmLongRunningTaskDto startSynchronization(String systemName) {
		SysSystemFilter sysFilt = new SysSystemFilter();
		sysFilt.setText(systemName);
		SysSystemDto system = systemService.getByCode(systemName);
		Assert.assertNotNull(system);
		
		SysSyncConfigFilter syncFilt = new SysSyncConfigFilter();
		syncFilt.setSystemId(system.getId());
		List<AbstractSysSyncConfigDto> syncs = sysSyncConfigService.find(syncFilt, null).getContent();
		Assert.assertEquals(1, syncs.size());
		
		AbstractSysSyncConfigDto config = syncs.get(0);
		SynchronizationSchedulableTaskExecutor lrt = new SynchronizationSchedulableTaskExecutor(config.getId());
		LongRunningFutureTask<Boolean> longRunningFutureTask = longRunningTaskManager.execute(lrt);
		IdmLongRunningTaskDto lrtDto = longRunningTaskService.get(longRunningFutureTask.getExecutor().getLongRunningTaskId());
		
		return lrtDto;
	}
	
	/**
	 * Method runs both HR synchronizations in asynchronous mode and waits 
	 * for finish of the all subsequent tasks which are run in the same transaction.  
	 */
	private void synchronizeHRSystems() {
		enableAsyncProcessing();
		try {
			IdmLongRunningTaskDto identitySynctask = startSynchronization(identitySysName);
			waitForTransactionFinish(identitySynctask.getTransactionId(), 100, 300);

			IdmLongRunningTaskDto contractSynctask = startSynchronization(contractSysName);
			waitForTransactionFinish(contractSynctask.getTransactionId(), 100, 300);
		} catch (Exception ex) {
			fail("Synchronization of the HR systems has failed");
		} finally {
			disableAsyncProcessing();
		}
	}
	
		
	@Transactional
	public void initIdentityHRTable() {
		deleteAllResourceData(TestResource.TABLE_NAME);
		addIdentityToHRTable(getIdentityPattern(dvorakUsername));
	}
	
	@Transactional
	public void initContractHRTable() {
		deleteAllResourceData(TestContractResource.TABLE_NAME);
		addContractToHRTable(getContractPattern(dvorakContractId0, LocalDate.now().minusDays(2).toString(), LocalDate.now().plusDays(2).toString()));
	}
	
	/**
	 * Method for insertion of a new record into corresponding database table
	 */
	@Transactional
	public void addIdentityToHRTable(Map<String,String> identity) {
		TestResource resourceUserOne = new TestResource();
		resourceUserOne.setName(identity.get("extId")); // serves as Id
		resourceUserOne.setFirstname(identity.get("firstName"));
		resourceUserOne.setLastname(identity.get("lastName"));
		resourceUserOne.setTitleBefore(identity.get("titleBefore"));
		resourceUserOne.setTitleAfter(identity.get("titleAfter"));
		resourceUserOne.setPersonalNumber(identity.get("personalNumber"));
		resourceUserOne.setModified(ZonedDateTime.now());
		entityManager.persist(resourceUserOne);
	}
	
	/**
	 * Method for insertion of a new record into corresponding database table
	 */
	@Transactional
	public void addContractToHRTable(Map<String,String> contract) {
		TestContractResource resource = new TestContractResource();
		resource.setId(contract.get("extId"));
		resource.setState(null);
		resource.setWorkposition(contract.get("workPosition"));
		resource.setPositions(contract.get("contractType"));
		resource.setValidFrom(LocalDate.parse(contract.get("validFrom")));
		resource.setValidTill(LocalDate.parse(contract.get("validTill")));
		resource.setName(contract.get("personalNumber")); // name used for personalNumber of Identity
		resource.setModified(ZonedDateTime.now());
		entityManager.persist(resource);
	}
	
	/*
	 * Updates existing record with the new values.
	 * If updated record doesn't exist an assertion is thrown
	 * Id is the primary key of the table i.e. extId Map key
	 */
	@Transactional
	public void updateIdentityHRTable(String id, Map<String,String> identity) {
		TestResource resource = entityManager.find(TestResource.class, id);
		Assert.assertNotNull(resource);
		
		if (identity.containsKey("extId")) {
			resource.setName(identity.get("extId")); // serves as Id
		}
		if (identity.containsKey("firstName")) {
		resource.setFirstname(identity.get("firstName"));
		}
		if (identity.containsKey("lastName")) {
			resource.setLastname(identity.get("lastName"));
		}
		if (identity.containsKey("titleBefore")) {
			resource.setTitleBefore(identity.get("titleBefore"));
		}
		if (identity.containsKey("titleAfter")) {
			resource.setTitleAfter(identity.get("titleAfter"));
		}
		if (identity.containsKey("personalNumber")) {
			resource.setPersonalNumber(identity.get("personalNumber"));
		}
		if (!identity.isEmpty()) {
			resource.setModified(ZonedDateTime.now());
		}
	}
	
	/*
	 * Updates existing record with the new values.
	 * If updated record doesn't exist an assertion is thrown
	 * Id is the primary key of the table i.e. extId Map key
	 */
	@Transactional
	public void updateContractHRTable(String id, Map<String,String> contract) {
		TestContractResource resource = entityManager.find(TestContractResource.class, id);
		Assert.assertNotNull(resource);
		
		if (contract.containsKey("extId")) {
			resource.setId(contract.get("extId"));
		}
		if (contract.containsKey("workPosition")) {
			resource.setWorkposition(contract.get("workPosition"));
		}
		if (contract.containsKey("contractType")) {
			resource.setPositions(contract.get("contractType"));
		}
		if (contract.containsKey("validFrom")) {
			resource.setValidFrom(LocalDate.parse(contract.get("validFrom")));
		}
		if (contract.containsKey("validTill")) {
			resource.setValidTill(LocalDate.parse(contract.get("validTill")));
		}
		if (contract.containsKey("personalNumber")) {
			resource.setName(contract.get("personalNumber")); // name used for personalNumber of Identity
		}
		if (!contract.isEmpty()) {
			resource.setModified(ZonedDateTime.now());
		}
	}
	
	/**
	 * 
	 * DB table complete cleanup
	 */
	@Transactional
	public void deleteAllResourceData(String tableName) {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM " + tableName);
		q.executeUpdate();
	}
	
	/**
	 * ******* Section of the patterns definition ******
	 * 
	 * Following patterns serves as source data for HR system initialization 
	 * and following evaluation of the state in the IdM and target system.
	 * Outputs contain default values suitable for the first test case scenario
	 * but they are supposed to be modified according to needs
	 * 
	 */
	
	private Map<String,String> getIdentityPattern (String code) {
		Map<String,String> map = new HashMap<String, String>();
		if (novakUsername.equals(code)) {
			map.put("extId","1"); // serves as the primary key in source DB
			map.put("username",novakUsername);
			map.put("firstName","Petr");
			map.put("lastName","Novák");
			map.put("titleBefore","Ing.");
			map.put("titleAfter","PhD.");
			map.put("personalNumber","1001");
			map.put("email","novak.petr@company.cz");
		}
		if (dvorakUsername.equals(code)) {
			map.put("extId","0"); // serves as the primary key in source DB
			map.put("username",dvorakUsername);
			map.put("firstName","Jan");
			map.put("lastName","Dvořák");
			map.put("titleBefore","Ing.");
			map.put("titleAfter","");
			map.put("personalNumber","1000");
			map.put("email","dvorak.jan@company.cz");
		}
		return map;
	}
	
	private Map<String,String> getContractPattern (String contractId, String validFrom, String validTill) {
		Map<String,String> map = new HashMap<String, String>();
		map.put("extId", contractId); // keeps primary key of the contract table
		map.put("validFrom", validFrom);
		map.put("validTill", validTill);
		
		switch (contractId) {
		case dvorakContractId0:
			map.put("workPosition",dep1NodeName);
			map.put("personalNumber","1000");
			map.put("contractType",consultantPositionName);
			break;
		case dvorakContractId1:
			map.put("workPosition",dep2NodeName);
			map.put("personalNumber","1000");
			map.put("contractType",directorPositionName);
			break;
		case dvorakContractId2:
			map.put("workPosition",dep2NodeName);
			map.put("personalNumber","1000");
			map.put("contractType",null);
			break;
		case novakContractId0:
			map.put("workPosition",dep1NodeName);
			map.put("personalNumber","1001");
			map.put("contractType",consultantPositionName);
			break;

		default:
			break;
		}
		return map;
	}
	
	private Map<String,String> getRolePattern(String code, String validFrom, String validTill) {
		Map<String,String> map = new HashMap<String, String>();
		
		map.put("code", code);
		map.put("validFrom", validFrom);
		map.put("validTill", validTill);
		
		
		if (code.equals(adGroupAllRole) || 
				code.equals(adGroupPkiRole) || 
				code.equals(adGroupCons1Role) ||
				code.equals(adGroupCons2Role)) {
			map.put("directRole", Boolean.FALSE.toString());
		} else {
			map.put("directRole", Boolean.TRUE.toString());
		}
		return map;
	}
	
	
	/******************* DESTINATION Systems *********************/
	
	
	
	/**
	 * Create destination system2 
	 * 
	 */
	@SuppressWarnings("unused")
	private void createDestinationTableSystem() {
		
		//TestRoleResource.TABLE_NAME
		// TODO will be used for next test devel
		
	}
	
	
	/************ LDAP section *********/
	
	/**
	 * Wrapper of initialising methods for Ldap system
	 */
	private void initLdapDestinationSystem() {
		startLdapTestServer();
		SysSystemDto system = createLdapSystem(targetAdSystem);
		createLdapProvisioningMapping(system);
	}
	
	/**
	 * Create Ldap system
	 * @param systemName
	 * @return
	 */
	private SysSystemDto createLdapSystem(String systemName) {
		SysSystemDto system = new SysSystemDto();
		system.setName(systemName == null ? "ldap-test-system" + "_" + System.currentTimeMillis() : systemName);
		system.setConnectorKey(new SysConnectorKeyDto(getLdapConnectorKey()));
		system.setReadonly(true);
		system.setQueue(true);
		system = systemService.save(system);

		// Configured connector form values
		IdmFormDefinitionDto formDefinition = systemService.getConnectorFormDefinition(system);
		List<IdmFormAttributeDto> attributes = formService.getAttributes(formDefinition);
		ImmutableMap<String, String> connectorConfig = new ImmutableMap.Builder<String, String>()
				.put("host", ldapConnectionInfo.getConnectedIPAddress())
				.put("port", String.valueOf(ldapConnectionInfo.getConnectedPort()))
				.put("principal", ldapAdminLogin)
				.put("credentials", ldapPassword)
				.put("baseContexts", ldapBaseOU)
				.put("baseContextsToSynchronize", ldapBaseOU)
				.put("passwordAttribute", "userPassword")
				.put("uidAttribute", "uid")
				.put("dnAttribute", "")
				.build();

		List<IdmFormValueDto> values = new ArrayList<>();
		for (IdmFormAttributeDto attribute : attributes) {
			String code = attribute.getCode();
			if(!connectorConfig.containsKey(code)) {
				continue;
			}
			
			if ("useVlvControls".equals(code)) {
				IdmFormValueDto formVal = new IdmFormValueDto(attribute);
				formVal.setBooleanValue(true);
				values.add(formVal);
			} else if ("ssl".equals(code)) {
				IdmFormValueDto formVal = new IdmFormValueDto(attribute);
				formVal.setBooleanValue(false);
				values.add(formVal);
			} else {
				IdmFormValueDto formVal = new IdmFormValueDto(attribute);
				formVal.setValue(connectorConfig.get(code));
				values.add(formVal);
			}
		}
		formService.saveValues(system, formDefinition, values);		
		return system;
	}
	
	private void createLdapProvisioningMapping(SysSystemDto system) {
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);
		SysSchemaObjectClassDto objectClass = objectClasses
				.stream()
				.filter(oc -> oc.getObjectClassName().equals("__ACCOUNT__"))
				.findFirst()
				.orElse(null);
		
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());
		List<SysSchemaAttributeDto> schemaAttributes = schemaAttributeService.find(schemaAttributeFilter, null).getContent();
		
		// Provisioning mapping
		SysSystemMappingDto mapping = new SysSystemMappingDto();
		mapping.setName(getHelper().createName());
		mapping.setEntityType(SystemEntityType.IDENTITY);
		mapping.setOperationType(SystemOperationType.PROVISIONING);
		mapping.setObjectClass(objectClass.getId());
		mapping = systemMappingService.save(mapping);
		
		ImmutableMap<String, String> mappingConfig = new ImmutableMap.Builder<String, String>()
				.put("mail", IdmIdentity_.email.getName())
				.put("sn", IdmIdentity_.lastName.getName())
				.put("givenName", IdmIdentity_.firstName.getName())
				.put("cn", IdmIdentity_.username.getName())
				.put("__NAME__", IdmIdentity_.username.getName())
				//.put("mobile", IdmIdentity_.phone.getName())
				.put("title ", IdmIdentity_.titleBefore.getName())
				.put("__PASSWORD__", "__PASSWORD__")
				.put("initials", "multivalue")
				.build();
		
		for (SysSchemaAttributeDto schemaAttr : schemaAttributes) {
			
			String schemaName = schemaAttr.getName();
			if (!mappingConfig.containsKey(schemaName)) {
				continue;
			}
			
			SysSystemAttributeMappingFilter attrFilt = new SysSystemAttributeMappingFilter();
			attrFilt.setName(schemaName);
			attrFilt.setSystemId(system.getId());
			attrFilt.setOperationType(SystemOperationType.PROVISIONING);
			if (systemAttributeMappingService.count(attrFilt) > 0) {
				// some attributes are in the schema more than once, skip if already mapped
				continue;
			}
			
			SysSystemAttributeMappingDto mappingAttr = new SysSystemAttributeMappingDto();
			mappingAttr.setName(schemaName);
			mappingAttr.setIdmPropertyName(mappingConfig.get(schemaName));
			mappingAttr.setSystemMapping(mapping.getId());
			mappingAttr.setSchemaAttribute(schemaAttr.getId());
			mappingAttr.setEntityAttribute(true);
			
			if ("__NAME__".equals(schemaName)) {
				mappingAttr.setUid(true);
				mappingAttr.setTransformToResourceScript("return \"uid=\"+attributeValue+\",ou="+ldapUserOU+","+ldapBaseOU+"\";");
			} else if ("__PASSWORD__".equals(schemaName)) {
				mappingAttr.setPasswordAttribute(true);
				mappingAttr.setEntityAttribute(false);
				mappingAttr.setIdmPropertyName(null);
				mappingAttr.setSendAlways(true);
			} else if ("initials".equals(schemaName)) { // used as multivalue merged attr
				mappingAttr.setEntityAttribute(false);
				mappingAttr.setStrategyType(AttributeMappingStrategyType.MERGE);
				mappingAttr = systemAttributeMappingService.save(mappingAttr);
				createLdapGroupRoles(system, mappingAttr);
			}
			systemAttributeMappingService.save(mappingAttr);
		}
	}
	
	private void createLdapGroupRoles(SysSystemDto system, SysSystemAttributeMappingDto mappingAttr) {
		IdmRoleDto role = roleService.getByCode(adUsersRole);
		SysRoleSystemDto roleSystem = accTestHelper.createRoleSystem(role, system);
		roleSystem.setForwardAccountManagemen(true);
		roleSystem = sysRoleService.save(roleSystem);
		
		// adding group roles
		Set<String> groupRoles = new HashSet<String>(Arrays.asList(
			 	adGroupAllRole, adGroupPkiRole, adGroupDep1Role,
				adGroupDep2Role, adGroupCons1Role, adGroupCons2Role,
				adGroupDirectorRole));
		
		groupRoles.stream().forEach(roleName -> {
			IdmRoleDto roleDto = roleService.getByCode(roleName);
			SysRoleSystemDto roleGroupSystem = accTestHelper.createRoleSystem(roleDto, system);
			//String roleDN = "cn="+roleName + ", " + ldapGroupOU + ", " + ldapBaseOU;
			
			SysRoleSystemAttributeDto roleSystemAttributeDto = new SysRoleSystemAttributeDto();
			roleSystemAttributeDto.setStrategyType(AttributeMappingStrategyType.MERGE);
			roleSystemAttributeDto.setRoleSystem(roleGroupSystem.getId());			
			roleSystemAttributeDto.setSystemAttributeMapping(mappingAttr.getId());
			roleSystemAttributeDto.setSchemaAttribute(mappingAttr.getSchemaAttribute());
			roleSystemAttributeDto.setName(mappingAttr.getName());
			roleSystemAttributeDto.setEntityAttribute(false);
			roleSystemAttributeDto.setExtendedAttribute(false);
			roleSystemAttributeDto.setTransformToResourceScript("return '" + roleName + "';" + System.lineSeparator());
			roleSystemAttributeDto = roleSystemAttributeService.save(roleSystemAttributeDto);
		});
	}
	
	/**
	 * Ldap server initialization and start 
	 */
	private void startLdapTestServer() {
		try (InputStream inputStream = new FileInputStream("src/test/resources/eu/bcvsolutions/idm/ldap/schema.ldif");) {
			// Create the configuration to use for the server.
			InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(ldapBaseOU);

			// load schema
			config.addAdditionalBindCredentials(ldapAdminLogin, ldapPassword);
			//InputStream inputStream = new FileInputStream("src/test/resources/eu/bcvsolutions/idm/ldap/schema.ldif");
			final LDIFReader ldifReader = new LDIFReader(inputStream);
			final Entry schemaEntry = ldifReader.readEntry();
			ldifReader.close();

			Schema newSchema = new Schema(schemaEntry);
			config.setSchema(newSchema);

			// Create the directory server instance, populate it with data from the
			// "crossLdapDemoData.ldif" file, and start listening for client connections.
			directoryServer = new InMemoryDirectoryServer(config);
			directoryServer.importFromLDIF(true, "src/test/resources/eu/bcvsolutions/idm/ldap/ldapDemoData.ldif");
			directoryServer.startListening();

			// Get a client connection to the server and use it to perform various
			// operations.
			ldapConnectionInfo = directoryServer.getConnection();
		} catch (LDAPException | IOException | LDIFException e) {
			e.printStackTrace();
			Assert.fail();
		}
		LOG.error(String.format("Ldap server is running and available: %s", ldapConnectionInfo.getHostPort()));
	}
	
	private void stopLdapTestServer() {
		ldapConnectionInfo.close();
		directoryServer.shutDown(true);
	}
	
	private IcConnectorKeyImpl getLdapConnectorKey() {
		IcConnectorKeyImpl key = new IcConnectorKeyImpl();
		key.setFramework("connId");
		key.setConnectorName("net.tirasa.connid.bundles.ldap.LdapConnector");
		key.setBundleName("net.tirasa.connid.bundles.ldap");
		key.setBundleVersion("1.5.1");
		return key;
	}
	
	/**
	 * Transformation of a general pattern into the structure suitable for provisioning queue and archive evaluation
	 * 
	 */
	private Map<String, Object> getLdapProvisioningValues(Map<String,String> identity, Collection<String> roles) { 
		return ImmutableMap.<String,Object>builder()
		.put("givenName",identity.get("firstName"))
		.put("sn",identity.get("lastName"))
		.put("cn",identity.get("username"))
		.put("__NAME__","uid="+identity.get("username")+",ou="+ldapUserOU+","+ldapBaseOU)
		.put("mail",identity.get("email"))
		.put("initials", Lists.newArrayList(roles))
		.build();
	}
	
	
	
	/****************** Cleanup methods **********************/
	
	private void roleCleanup() {
		Set<String> roleNames = Set.of(adGroupAllRole, adGroupPkiRole, adGroupDep1Role,
				adGroupDep2Role, adGroupCons1Role, adGroupCons2Role,
				adGroupDirectorRole, consultantBusinessRole, allBusinessRole,
				adUsersRole, system2ManualRole);
		
		List<IdmRoleDto> roles = new ArrayList<IdmRoleDto>(roleNames.size());
		for (String name : roleNames) {
			IdmRoleDto dto = roleService.getByCode(name);
			if (dto != null) {
				roles.add(dto);
			}	
		}

		// sync role force deletion
		Map<String,Serializable> properties = ImmutableMap.of(EntityEventProcessor.PROPERTY_FORCE_DELETE, Boolean.TRUE);
		for (IdmRoleDto role : roles) {
			EntityEvent<IdmRoleDto> event = new RoleEvent(RoleEventType.DELETE, role, properties);
			roleService.publish(event);
		}
		
		// performing delete operation on all items marked in entity state as to delete 
		for (IdmRoleDto role : roles) {
			List<IdmEntityStateDto> entityStates = entityStateManager.findStates(role, null).getContent();
			OperationResultDto result = entityStates.get(0).getResult();
			if (OperationState.RUNNING.equals(result.getState()) && 
					CoreResultCode.DELETED.getCode().equals(result.getModel().getStatusEnum())) {
				roleService.delete(role);
			}
		}
		
		// check proper deletion
		for (String name : roleNames) {
			Assert.assertNull(roleService.getByCode(name));
		}
	}
	
	private void roleCatalogueCleanup() {
		IdmRoleCatalogueFilter catalogueFilter = new IdmRoleCatalogueFilter();
		catalogueFilter.setCode(adGroupCat);
		List<IdmRoleCatalogueDto> catalogues = roleCatalogueService.find(catalogueFilter, null).getContent();
		catalogues.forEach(catalogue -> roleCatalogueService.delete(catalogue));
		
		catalogueFilter.setCode(businessRoleCat);
		catalogues = roleCatalogueService.find(catalogueFilter, null).getContent();
		catalogues.forEach(catalogue -> roleCatalogueService.delete(catalogue));
	}
	
	private void otherStuffClenaup() {
		// script cleanup
		IdmScriptDto dto = scriptService.getByCode(stringToLocalDateScript);
		scriptService.delete(dto);
		dto = scriptService.getByCode(getIdentityUuidByPersonalNumScript);
		scriptService.delete(dto);
		
		// generator setting removal
		IdmGenerateValueFilter generatorFilter = new IdmGenerateValueFilter();
		generatorFilter.setDtoType(IdmIdentityDto.class.getCanonicalName());
		generatedAttributeService.find(generatorFilter,null)
			.getContent()
			.stream()
			.filter(gen -> IdentityUsernameGenerator.class.getCanonicalName().equals(gen.getGeneratorType()))
			.forEach(gen -> {
				generatedAttributeService.delete(gen);
			});
		
		generatedAttributeService.find(generatorFilter,null)
			.getContent()
			.stream()
			.filter(gen -> IdentityEmailGenerator.class.getCanonicalName().equals(gen.getGeneratorType()))
			.forEach(gen -> {
				generatedAttributeService.delete(gen);
			});
		
		// identity EAV
		IdmFormAttributeFilter formAttrFilt = new IdmFormAttributeFilter();
		formAttrFilt.setCode(eavAutoRoleAttrName);
		List<IdmFormAttributeDto> attrs = formAttrService.find(formAttrFilt, null).getContent();
		attrs.forEach(attr -> {
			// if deleted after identity is deleted, no value remains	
			formService.deleteAttribute(attr);
			});
	}
	
	private void identityCleanup() {
		IdmIdentityDto dto = identityService.getByUsername(dvorakUsername);
		if (dto!= null) {
			identityService.delete(dto);
		}
		dto = identityService.getByUsername(novakUsername);
		if (dto!= null) {
			identityService.delete(dto);
		}
	}
	
	private void organizationStructureCleanup() {
		IdmTreeNodeFilter filter = new IdmTreeNodeFilter();
		filter.setCode(dep1NodeName);
		treeNodeService.find(filter,null).getContent().forEach(node -> treeNodeService.delete(node)); 
		filter.setCode(dep2NodeName);
		treeNodeService.find(filter,null).getContent().forEach(node -> treeNodeService.delete(node));
		}
	
	private void systemCleanup() {
		stopLdapTestServer();
		clearProvisioningQueue(targetAdSystem);
		SysSystemDto system = systemService.getByCode(identitySysName);
		systemService.delete(system);
		system = systemService.getByCode(contractSysName);
		systemService.delete(system);
		system = systemService.getByCode(targetAdSystem);
		systemService.delete(system);
		
		getBean().deleteAllResourceData(TestResource.TABLE_NAME);
		getBean().deleteAllResourceData(TestContractResource.TABLE_NAME);
	}
	
	/******************** Test evaluation tools ****************/
	
	//****** Identity checks **********
	private void matchIdentityWithPattern(String username, Map<String,String> pattern) {
		IdmIdentityDto identity = identityService.getByUsername(username);
		Assert.assertNotNull(identity);
		Assert.assertEquals(pattern.get("username"),identity.getUsername());
		Assert.assertEquals(pattern.get("firstName"),identity.getFirstName());
		Assert.assertEquals(pattern.get("lastName"),identity.getLastName());
		Assert.assertEquals(pattern.get("titleBefore"),identity.getTitleBefore());
		Assert.assertEquals(pattern.get("titleAfter"),identity.getTitleAfter());
		Assert.assertEquals(pattern.get("personalNumber"),identity.getExternalCode());
		Assert.assertEquals(pattern.get("email"),identity.getEmail());		
	}
	
	private void checkIdentityState(String username, IdentityState state) {
		IdmIdentityDto identity = identityService.getByUsername(username);
		Assert.assertNotNull(identity);
		Assert.assertEquals(state,identity.getState());
	}
	
	// ******* Contract checks ********
	private void matchContractWithPattern(String username, Map<String,String> pattern) {
		IdmIdentityDto identity = identityService.getByUsername(username);
		Assert.assertNotNull(identity);
		
		IdmIdentityContractFilter filt = new IdmIdentityContractFilter();
		filt.setIdentity(identity.getId());
		
		if (pattern.containsKey("extId")) {
			String id = pattern.get("extId");
			filt.setPosition(id); // attribute position is used as field with contract unique identifier
		}
		
		if (pattern.containsKey("validFrom")) {
			String dateStr = pattern.get("validFrom");
			LocalDate date = dateStr == null ? null : LocalDate.parse(dateStr);
			filt.setValidFrom(date);
		}
		
		if (pattern.containsKey("validTill")) {
			String dateStr = pattern.get("validTill");
			LocalDate date = dateStr == null ? null : LocalDate.parse(dateStr);
			filt.setValidTill(date);
		}
		
		if (pattern.containsKey("workPosition")) {
			IdmTreeNodeFilter nodeFilt = new IdmTreeNodeFilter();
			nodeFilt.setCode(pattern.get("workPosition"));			
			List<IdmTreeNodeDto> nodes = treeNodeService.find(nodeFilt, null).getContent();
			Assert.assertEquals(1, nodes.size());
			filt.setWorkPosition(nodes.get(0).getId());
			}
		
		List<IdmIdentityContractDto> contracts = identityContractService.find(filt,null).getContent();
		Assert.assertEquals(1, contracts.size());

		if (pattern.containsKey("contractType")) {
			IdmIdentityContractDto contract = contracts.get(0);
			IdmFormAttributeDto attr = formService.getAttribute(contract.getClass(), eavAutoRoleAttrName);
			List<IdmFormValueDto> values = formService.getValues(contract, attr);
			Assert.assertEquals(1, values.size());
			Assert.assertEquals(pattern.get("contractType"), values.get(0).getShortTextValue());
		}
	}
	
	// *********** Role checks **************
	
	private void checkRoleAssigned(String contractId, Map<String,String> pattern) {
		IdmIdentityContractFilter contractFilt = new IdmIdentityContractFilter();
		contractFilt.setPosition(contractId); // misused as unique contract identificer
		List<IdmIdentityContractDto> contracts = identityContractService.find(contractFilt,null).getContent();
		Assert.assertEquals(1, contracts.size());
		
		IdmRoleDto roleDto = roleService.getByCode(pattern.get("code"));
		Assert.assertNotNull(roleDto);
		
		IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();
		identityRoleFilter.setIdentityContractId(contracts.get(0).getId());
		identityRoleFilter.setRoleId(roleDto.getId());
		List<IdmIdentityRoleDto> identityRoleDtos = identityRoleService.find(identityRoleFilter,null).getContent();
		Assert.assertEquals(1, identityRoleDtos.size());
		IdmIdentityRoleDto identityRole = identityRoleDtos.get(0);
		
		if (pattern.containsKey("directRole")) {
			Assert.assertEquals(Boolean.parseBoolean(pattern.get("directRole")), identityRole.getDirectRole() == null);
		}
		
		if (pattern.containsKey("validTill")) {
			String dateStr = pattern.get("validTill");
			LocalDate date = dateStr == null ? null : LocalDate.parse(dateStr);
			Assert.assertEquals(date, identityRole.getValidTill());
		}
		
		if (pattern.containsKey("validFrom")) {
			String dateStr = pattern.get("validFrom");
			LocalDate date = dateStr == null ? null : LocalDate.parse(dateStr);
			Assert.assertEquals(date, identityRole.getValidFrom());
		}
	}
	
	
	// *********** Provisioning checks ***********
	@SuppressWarnings("unchecked")
	private void checkProvisioningQueue(String systemCode, Map<String,Object> pattern) {
		UUID systemId = systemService.getByCode(systemCode).getId();
		SysProvisioningOperationFilter provisioningFilter = new SysProvisioningOperationFilter();
		provisioningFilter.setSystemId(systemId);
		provisioningFilter.setEntityType(SystemEntityType.IDENTITY);
		List<SysProvisioningOperationDto> provisioningOps = provisioningOperationService.find(provisioningFilter, null).getContent();
		Assert.assertTrue(provisioningOps.size() > 0);
		
		// FIXME test just the last state... prove that it is sufficient
		Map<ProvisioningAttributeDto,Object> accountValues = provisioningOps.get(provisioningOps.size()-1).getProvisioningContext().getAccountObject();		
		// evaluate state
		for (Map.Entry<ProvisioningAttributeDto, Object> accountItem : accountValues.entrySet()) {
			String name = accountItem.getKey().getSchemaAttributeName();
			if (!pattern.containsKey(name)) {
				continue;
			}
			Object testValObj = pattern.get(name);
			Object accountValObj = accountItem.getValue();
			
			boolean comparsionResult;
			if(testValObj instanceof Collection) {
				comparsionResult = CollectionUtils.isEqualCollection((Collection<? extends String>)testValObj, (Collection<? extends String>)accountValObj);
			} else {
				comparsionResult = Objects.equals(testValObj, accountValObj);
			}
			Assert.assertTrue(comparsionResult);	
		}
	}
	
	void clearProvisioningQueue(String systemCode) {
		UUID systemId = systemService.getByCode(systemCode).getId();
		SysProvisioningOperationFilter provisioningFilter = new SysProvisioningOperationFilter();
		provisioningFilter.setSystemId(systemId);
		provisioningFilter.setEntityType(SystemEntityType.IDENTITY);
		List<SysProvisioningOperationDto> provisioningOps = provisioningOperationService.find(provisioningFilter, null).getContent();
		provisioningOperationService.deleteAll(provisioningOps);
	}
	
	/************************** utilities *************************/
	
	private void waitForTransactionFinish(UUID transaction, int interval, int count) {
		IdmLongRunningTaskFilter taskFilter = new IdmLongRunningTaskFilter();
		taskFilter.setTransactionId(transaction);
		List<IdmLongRunningTaskDto> transactLrts = longRunningTaskService.find(taskFilter, null).getContent();
		getHelper().waitForResult(res -> {
			return !transactLrts.stream().allMatch(lrt -> {
				IdmLongRunningTaskDto currentLrt = longRunningTaskService.get(lrt.getId());
				return !currentLrt.isRunning() && OperationState.EXECUTED.equals(currentLrt.getResultState());
				});
		}, interval, count);
	}
	
	private void enableAsyncProcessing() {
		getHelper().setConfigurationValue(EventConfiguration.PROPERTY_EVENT_ASYNCHRONOUS_ENABLED, true);
		getHelper().setConfigurationValue(SchedulerConfiguration.PROPERTY_TASK_ASYNCHRONOUS_ENABLED, true);
	}
	
	private void disableAsyncProcessing() {
		getHelper().setConfigurationValue(EventConfiguration.PROPERTY_EVENT_ASYNCHRONOUS_ENABLED, false);
		getHelper().setConfigurationValue(SchedulerConfiguration.PROPERTY_TASK_ASYNCHRONOUS_ENABLED, false);
	}
	
	private ComplexHrProcessIntegrationTest getBean() {
		return applicationContext.getAutowireCapableBeanFactory().createBean(this.getClass());
	}
}
