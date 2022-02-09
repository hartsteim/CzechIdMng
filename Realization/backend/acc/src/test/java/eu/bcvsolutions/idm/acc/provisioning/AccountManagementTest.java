package eu.bcvsolutions.idm.acc.provisioning;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.time.LocalDate;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Sets;

import eu.bcvsolutions.idm.acc.TestHelper;
import eu.bcvsolutions.idm.acc.bulk.action.impl.IdentityAccountManagementBulkAction;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccRoleAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningArchiveDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccIdentityAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.AccRoleAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysProvisioningOperationFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.entity.TestResource;
import eu.bcvsolutions.idm.acc.entity.TestRoleResource;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccRoleAccountService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningArchiveService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.bulk.action.BulkActionManager;
import eu.bcvsolutions.idm.core.api.bulk.action.dto.IdmBulkActionDto;
import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Basic account management tests (tests for identity ACM are in
 * {@link IdentityAccountManagementTest})
 * 
 * @author Svanda
 *
 */
public class AccountManagementTest extends AbstractIntegrationTest {

	private static final String ATTRIBUTE_NAME = "__NAME__";
	private static final String ATTRIBUTE_EMAIL = "email";

	@Autowired
	private TestHelper helper;
	@Autowired
	private SysSystemService systemService;
	@Autowired
	private SysSystemMappingService systemMappingService;
	@Autowired
	private SysSystemAttributeMappingService schemaAttributeMappingService;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	@Qualifier("entityManager")
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private SysRoleSystemService roleSystemService;
	@Autowired
	private AccIdentityAccountService identityAccountService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private AccRoleAccountService roleAccountService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private IdmIdentityContractService identityContractService;
	@Autowired
	private AccAccountService accountService;
	@Autowired
	private SysProvisioningArchiveService provisioningArchiveService;
	@Autowired
	private BulkActionManager bulkActionManager;

	@Before
	public void init() {
		loginAsAdmin();
	}

	@After
	public void logout() {
		super.logout();
	}

	@Test
	/**
	 * Script on the mapping "Can be account created?" returns true (if priority is 1000).
	 */
	public void testAccountCanBeCreated() {
		SysSystemDto system = initData();
		Assert.assertNotNull(system);
		
		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(system.getId(), SystemEntityType.ROLE);
		Assert.assertNotNull(mapping);
		mapping.setCanBeAccountCreatedScript("return entity.getPriority() == 1000;");
		mapping = systemMappingService.save(mapping);
		IdmRoleDto defaultRole = helper.createRole();
		defaultRole.setPriority(500);
		roleService.save(defaultRole);
		
		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setEntityId(defaultRole.getId());
		roleAccountFilter.setOwnership(Boolean.TRUE);
		roleAccountFilter.setSystemId(system.getId());
		List<AccRoleAccountDto> roleAccounts = roleAccountService.find(roleAccountFilter, null).getContent();
		// Priority is 500 -> account should not be created 
		Assert.assertEquals(0, roleAccounts.size());
		
		// Set priority to 1000
		defaultRole.setPriority(1000);
		roleService.save(defaultRole);
		roleAccounts = roleAccountService.find(roleAccountFilter, null).getContent();
		// Priority is 1000 -> account had to be created 
		Assert.assertEquals(1, roleAccounts.size());
		
		// Delete role
		roleService.delete(defaultRole);
		// Delete role mapping
		systemMappingService.delete(mapping);
	}
	
	@Test
	/**
	 * Script on the mapping "Can be account created?" returns false.
	 */
	public void testAccountCannotBeCreated() {
		SysSystemDto system = initData();
		Assert.assertNotNull(system);
		
		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(system.getId(), SystemEntityType.ROLE);
		Assert.assertNotNull(mapping);
		mapping.setCanBeAccountCreatedScript("return Boolean.FALSE;");
		mapping = systemMappingService.save(mapping);
		IdmRoleDto defaultRole = helper.createRole();
		
		AccRoleAccountFilter roleAccountFilter = new AccRoleAccountFilter();
		roleAccountFilter.setEntityId(defaultRole.getId());
		roleAccountFilter.setOwnership(Boolean.TRUE);
		roleAccountFilter.setSystemId(system.getId());
		List<AccRoleAccountDto> roleAccounts = roleAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(0, roleAccounts.size());
		
		// Delete role
		roleService.delete(defaultRole);
		// Delete role mapping
		systemMappingService.delete(mapping);
	}
	
	
	@Test
	/**
	 * Script on the mapping "Can be account created?" returns true.
	 */
	public void testIdentityAccountCanBeCreated() {
		SysSystemDto system = initIdentityData();
		Assert.assertNotNull(system);
		
		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(system.getId(), SystemEntityType.IDENTITY);
		Assert.assertNotNull(mapping);
		mapping.setCanBeAccountCreatedScript("return Boolean.FALSE;");
		mapping = systemMappingService.save(mapping);
		IdmIdentityDto identity = helper.createIdentity();
		
		AccIdentityAccountFilter roleAccountFilter = new AccIdentityAccountFilter();
		roleAccountFilter.setEntityId(identity.getId());
		roleAccountFilter.setOwnership(Boolean.TRUE);
		roleAccountFilter.setSystemId(system.getId());
		List<AccIdentityAccountDto> identityAccounts = identityAccountService.find(roleAccountFilter, null).getContent();
		// None role assigned
		Assert.assertEquals(0, identityAccounts.size());
		
		IdmRoleDto roleDefault = helper.createRole();
		SysRoleSystemDto roleSystemDefault = new SysRoleSystemDto();
		roleSystemDefault.setRole(roleDefault.getId());
		roleSystemDefault.setSystem(system.getId());
		roleSystemDefault.setSystemMapping(mapping.getId());
		roleSystemDefault = roleSystemService.save(roleSystemDefault);
		
		IdmIdentityRoleDto identityRole = new IdmIdentityRoleDto();
		identityRole.setIdentityContract(identityContractService.getPrimeContract(identity.getId()).getId());
		identityRole.setRole(roleDefault.getId());
		identityRole = identityRoleService.save(identityRole);
		
		identityAccounts = identityAccountService.find(roleAccountFilter, null).getContent();
		// Role assigned, but script returns false
		Assert.assertEquals(0, identityAccounts.size());
		
		mapping.setCanBeAccountCreatedScript("return Boolean.TRUE;");
		mapping = systemMappingService.save(mapping);
		// Resave run the ACM
		identityRole = identityRoleService.save(identityRole);
		identityAccounts = identityAccountService.find(roleAccountFilter, null).getContent();
		Assert.assertEquals(1, identityAccounts.size());
		
		// Delete
		identityService.delete(identity);
		roleService.delete(roleDefault);
	}
	
	@Test
	public void incrementalACM( ) {
		IdmRoleDto roleOne = getHelper().createRole();
		IdmRoleDto roleTwo = getHelper().createRole();

		// create test system with mapping and link her to role
		SysSystemDto systemOne = getHelper().createTestResourceSystem(true);
		SysSystemDto systemTwo = getHelper().createTestResourceSystem(true);
		getHelper().createRoleSystem(roleOne, systemOne);
		getHelper().createRoleSystem(roleTwo, systemTwo);

		IdmIdentityDto identity = getHelper().createIdentity();
		IdmRoleRequestDto roleRequestOne = getHelper().createRoleRequest(identity, roleOne, roleTwo);
		
		getHelper().executeRequest(roleRequestOne, false);

		// check after create
		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(2, assignedRoles.size());

		// check created account
		AccAccountDto accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNotNull(accountOne);
		Assert.assertNotNull(getHelper().findResource(accountOne.getRealUid()));
		AccAccountDto accountTwo = accountService.getAccount(identity.getUsername(), systemTwo.getId());
		Assert.assertNotNull(accountTwo);
	
		// check provisioning archive
		SysProvisioningOperationFilter archiveFilter = new SysProvisioningOperationFilter();
		archiveFilter.setEntityIdentifier(identity.getId());

		List<SysProvisioningArchiveDto> executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(2, executedOperations.size());
		
		IdmIdentityRoleDto identityRoleTwo = assignedRoles.stream() //
				.filter(assignedRole -> roleTwo.getId().equals(assignedRole.getRole())) //
				.findFirst() //
				.get(); //
		// Set identity-role as invalid and save without start ACM (save internal)
		identityRoleTwo.setValidTill(LocalDate.now().minusDays(1));
		assertFalse(identityRoleTwo.isValid());
		identityRoleService.saveInternal(identityRoleTwo);
		
		// Identity-role is invalid, but ACM was not executed -> account must still exist
		accountTwo = accountService.getAccount(identity.getUsername(), systemTwo.getId());
		Assert.assertNotNull(accountTwo);

		IdmRoleRequestDto removeRequest = getHelper().createRoleRequest(identity, 
				ConceptRoleRequestOperation.REMOVE,	roleOne);
		getHelper().executeRequest(removeRequest, false);
		// Check after remove request
		assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(1, assignedRoles.size());

		// Account for system One must not exist
		accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNull(accountOne);
		// Identity-role is invalid, but ACM was not executed for this account -> account must still exist
		accountTwo = accountService.getAccount(identity.getUsername(), systemTwo.getId());
		Assert.assertNotNull(accountTwo);
		
		// Execute ACM and provisioning via bulk action
		IdmBulkActionDto bulkAction = this.findBulkAction(IdmIdentity.class, IdentityAccountManagementBulkAction.NAME);
		bulkAction.setIdentifiers(Sets.newHashSet(identity.getId()));
		bulkActionManager.processAction(bulkAction);
		
		// Identity-role is invalid and ACM was executed for entire identity -> account must not exist
		accountTwo = accountService.getAccount(identity.getUsername(), systemTwo.getId());
		Assert.assertNull(accountTwo);
	}
	
	@Test
	public void testRemoveIdentityAccountIfRoleSystemRemoved( ) {
		IdmRoleDto roleOne = getHelper().createRole();

		// create test system with mapping and link her to role
		SysSystemDto systemOne = getHelper().createTestResourceSystem(true);
		SysRoleSystemDto roleSystem = getHelper().createRoleSystem(roleOne, systemOne);

		IdmIdentityDto identity = getHelper().createIdentity();
		IdmRoleRequestDto roleRequestOne = getHelper().createRoleRequest(identity, roleOne);
		
		getHelper().executeRequest(roleRequestOne, false);

		// check after create
		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(1, assignedRoles.size());

		// check created account
		AccAccountDto accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNotNull(accountOne);
		Assert.assertNotNull(getHelper().findResource(accountOne.getRealUid()));
		
		roleSystemService.delete(roleSystem);
		
		// Execute ACM and provisioning via bulk action
		IdmBulkActionDto bulkAction = this.findBulkAction(IdmIdentity.class, IdentityAccountManagementBulkAction.NAME);
		bulkAction.setIdentifiers(Sets.newHashSet(identity.getId()));
		bulkActionManager.processAction(bulkAction);
		
		// Account must not exist
		accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNull(accountOne);
	}
	
	@Test
	public void testCreateIdentityAccountIfRoleSystemCreated( ) {
		IdmRoleDto roleOne = getHelper().createRole();

		// create test system with mapping
		SysSystemDto systemOne = getHelper().createTestResourceSystem(true);

		IdmIdentityDto identity = getHelper().createIdentity();
		IdmRoleRequestDto roleRequestOne = getHelper().createRoleRequest(identity, roleOne);
		
		getHelper().executeRequest(roleRequestOne, false);

		// check after create
		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(1, assignedRoles.size());

		// check created account -> role-system mapping does not exist -> account is null
		AccAccountDto accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNull(accountOne);
		
		getHelper().createRoleSystem(roleOne, systemOne);
		
		// Execute ACM and provisioning via bulk action
		IdmBulkActionDto bulkAction = this.findBulkAction(IdmIdentity.class, IdentityAccountManagementBulkAction.NAME);
		bulkAction.setIdentifiers(Sets.newHashSet(identity.getId()));
		bulkActionManager.processAction(bulkAction);
		
		// Account must exists now
		accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNotNull(accountOne);
	}
	
	@Test
	/**
	 * IdentityAccount already exist, but doesn't have relation on RoleSystem. This
	 * could happen if system mapping was deleted and recreated or if was role use
	 * as sync default role, but without mapping on this system.
	 */
	public void testNoRemoveIdentityAccountIfRoleSystemRemovedAndRecreate( ) {
		IdmRoleDto roleOne = getHelper().createRole();

		// create test system with mapping and link her to role
		SysSystemDto systemOne = getHelper().createTestResourceSystem(true);
		SysRoleSystemDto roleSystem = getHelper().createRoleSystem(roleOne, systemOne);

		IdmIdentityDto identity = getHelper().createIdentity();
		IdmRoleRequestDto roleRequestOne = getHelper().createRoleRequest(identity, roleOne);
		
		getHelper().executeRequest(roleRequestOne, false);

		// check after create
		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(1, assignedRoles.size());

		// check created account
		AccAccountDto accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNotNull(accountOne);
		Assert.assertNotNull(getHelper().findResource(accountOne.getRealUid()));
		
		// Delete role-system -> relation on role-system from identity-account will be removed.
		roleSystemService.delete(roleSystem);
		roleSystem = getHelper().createRoleSystem(roleOne, systemOne);
		
		// Execute ACM and provisioning via bulk action
		IdmBulkActionDto bulkAction = this.findBulkAction(IdmIdentity.class, IdentityAccountManagementBulkAction.NAME);
		bulkAction.setIdentifiers(Sets.newHashSet(identity.getId()));
		bulkActionManager.processAction(bulkAction);
		
		// Account must exist
		AccAccountDto accountTwo = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNotNull(accountTwo);
		Assert.assertNotNull(getHelper().findResource(accountTwo.getRealUid()));
		// Account must have same ID as original -> must not be deleted
		Assert.assertEquals(accountOne.getId(), accountTwo.getId());
	}
	
	@Test
	public void incrementalProvisioningWithoutRequest( ) {
		IdmRoleDto roleOne = getHelper().createRole();
		IdmRoleDto roleTwo = getHelper().createRole();

		// create test system with mapping and link her to role
		SysSystemDto systemOne = getHelper().createTestResourceSystem(true);
		SysSystemDto systemTwo = getHelper().createTestResourceSystem(true);
		getHelper().createRoleSystem(roleOne, systemOne);
		getHelper().createRoleSystem(roleTwo, systemTwo);

		IdmIdentityDto identity = getHelper().createIdentity();
		IdmRoleRequestDto roleRequestOne = getHelper().createRoleRequest(identity, roleOne, roleTwo);
		
		getHelper().executeRequest(roleRequestOne, false);

		// check after create
		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(2, assignedRoles.size());

		// check created account
		AccAccountDto accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNotNull(accountOne);
		Assert.assertNotNull(getHelper().findResource(accountOne.getRealUid()));
		AccAccountDto accountTwo = accountService.getAccount(identity.getUsername(), systemTwo.getId());
		Assert.assertNotNull(accountTwo);
	
		// check provisioning archive
		SysProvisioningOperationFilter archiveFilter = new SysProvisioningOperationFilter();
		archiveFilter.setEntityIdentifier(identity.getId());
		archiveFilter.setSystemId(systemOne.getId());

		List<SysProvisioningArchiveDto> executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(1, executedOperations.size());
		
		IdmRoleDto roleThree = getHelper().createRole();
		getHelper().createRoleSystem(roleThree, systemOne);
		
		// Assign role Three without request
		getHelper().createIdentityRole(identity, roleThree);
		
		// We expect new provisioning record in archive for system One
		executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(2, executedOperations.size());
		
		// We expect still only one provisioning record in archive for system Two
		archiveFilter.setSystemId(systemTwo.getId());
		executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(1, executedOperations.size());
	}
	
	@Test
	public void incrementalProvisioningWithRequest() {
		IdmRoleDto roleOne = getHelper().createRole();
		IdmRoleDto roleTwo = getHelper().createRole();

		// create test system with mapping and link her to role
		SysSystemDto systemOne = getHelper().createTestResourceSystem(true);
		SysSystemDto systemTwo = getHelper().createTestResourceSystem(true);
		getHelper().createRoleSystem(roleOne, systemOne);
		getHelper().createRoleSystem(roleTwo, systemTwo);

		IdmIdentityDto identity = getHelper().createIdentity();
		IdmRoleRequestDto roleRequestOne = getHelper().createRoleRequest(identity, roleOne, roleTwo);
		
		getHelper().executeRequest(roleRequestOne, false);

		// check after create
		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(2, assignedRoles.size());

		// check created account
		AccAccountDto accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNotNull(accountOne);
		Assert.assertNotNull(getHelper().findResource(accountOne.getRealUid()));
		AccAccountDto accountTwo = accountService.getAccount(identity.getUsername(), systemTwo.getId());
		Assert.assertNotNull(accountTwo);
	
		// check provisioning archive
		SysProvisioningOperationFilter archiveFilter = new SysProvisioningOperationFilter();
		archiveFilter.setEntityIdentifier(identity.getId());
		archiveFilter.setSystemId(systemOne.getId());

		List<SysProvisioningArchiveDto> executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(1, executedOperations.size());
		
		IdmRoleDto roleThree = getHelper().createRole();
		getHelper().createRoleSystem(roleThree, systemOne);
		
		// Assign role Three with request
		IdmRoleRequestDto roleRequestThree = getHelper().createRoleRequest(identity, roleThree);
		getHelper().executeRequest(roleRequestThree, false);
		
		// We expect new provisioning record in archive for system One
		executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(2, executedOperations.size());
		
		// We expect still only one provisioning record in archive for system Two
		archiveFilter.setSystemId(systemTwo.getId());
		executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(1, executedOperations.size());
	}
	
	@Test
	public void incrementalProvisioningRemoveWithRequest() {
		IdmRoleDto roleOne = getHelper().createRole();
		IdmRoleDto roleTwo = getHelper().createRole();
		IdmRoleDto roleThree = getHelper().createRole();

		// create test system with mapping and link her to role
		SysSystemDto systemOne = getHelper().createTestResourceSystem(true);
		SysSystemDto systemTwo = getHelper().createTestResourceSystem(true);
		getHelper().createRoleSystem(roleOne, systemOne);
		getHelper().createRoleSystem(roleTwo, systemTwo);
		getHelper().createRoleSystem(roleThree, systemOne);

		IdmIdentityDto identity = getHelper().createIdentity();
		IdmRoleRequestDto roleRequestOne = getHelper().createRoleRequest(identity, roleOne, roleTwo, roleThree);
		
		getHelper().executeRequest(roleRequestOne, false);

		// check after create
		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(3, assignedRoles.size());

		// check created account
		AccAccountDto accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNotNull(accountOne);
		Assert.assertNotNull(getHelper().findResource(accountOne.getRealUid()));
		AccAccountDto accountTwo = accountService.getAccount(identity.getUsername(), systemTwo.getId());
		Assert.assertNotNull(accountTwo);
	
		// check provisioning archive
		SysProvisioningOperationFilter archiveFilter = new SysProvisioningOperationFilter();
		archiveFilter.setEntityIdentifier(identity.getId());
		archiveFilter.setSystemId(systemOne.getId());

		List<SysProvisioningArchiveDto> executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(1, executedOperations.size());
		
		// Unassigns role Three with request
		IdmRoleRequestDto roleRequestThree = getHelper().createRoleRequest(identity, ConceptRoleRequestOperation.REMOVE, roleThree);
		getHelper().executeRequest(roleRequestThree, false);
		
		// We expect new provisioning record in archive for system One
		executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(2, executedOperations.size());
		
		// We expect still only one provisioning record in archive for system Two
		archiveFilter.setSystemId(systemTwo.getId());
		executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(1, executedOperations.size());
		
		assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(2, assignedRoles.size());
		accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNotNull(accountOne);
		accountTwo = accountService.getAccount(identity.getUsername(), systemTwo.getId());
		Assert.assertNotNull(accountTwo);
	}
	
	@Test
	public void incrementalProvisioningRemoveWithoutRequest() {
		IdmRoleDto roleOne = getHelper().createRole();
		IdmRoleDto roleTwo = getHelper().createRole();
		IdmRoleDto roleThree = getHelper().createRole();

		// create test system with mapping and link her to role
		SysSystemDto systemOne = getHelper().createTestResourceSystem(true);
		SysSystemDto systemTwo = getHelper().createTestResourceSystem(true);
		getHelper().createRoleSystem(roleOne, systemOne);
		getHelper().createRoleSystem(roleTwo, systemTwo);
		getHelper().createRoleSystem(roleThree, systemOne);

		IdmIdentityDto identity = getHelper().createIdentity();
		IdmRoleRequestDto roleRequestOne = getHelper().createRoleRequest(identity, roleOne, roleTwo, roleThree);
		
		getHelper().executeRequest(roleRequestOne, false);

		// check after create
		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(3, assignedRoles.size());

		// check created account
		AccAccountDto accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNotNull(accountOne);
		Assert.assertNotNull(getHelper().findResource(accountOne.getRealUid()));
		AccAccountDto accountTwo = accountService.getAccount(identity.getUsername(), systemTwo.getId());
		Assert.assertNotNull(accountTwo);
	
		// check provisioning archive
		SysProvisioningOperationFilter archiveFilter = new SysProvisioningOperationFilter();
		archiveFilter.setEntityIdentifier(identity.getId());
		archiveFilter.setSystemId(systemOne.getId());

		List<SysProvisioningArchiveDto> executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(1, executedOperations.size());
		
		// Unassigns role Three without request
		IdmIdentityRoleDto identityRoleThree = assignedRoles.stream() //
				.filter(assignedRole -> roleThree.getId().equals(assignedRole.getRole())) //
				.findFirst() //
				.get(); //
		identityRoleService.delete(identityRoleThree);
		
		// We expect new provisioning record in archive for system One
		executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(2, executedOperations.size());
		
		// We expect still only one provisioning record in archive for system Two
		archiveFilter.setSystemId(systemTwo.getId());
		executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(1, executedOperations.size());
		
		assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(2, assignedRoles.size());
		accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNotNull(accountOne);
		accountTwo = accountService.getAccount(identity.getUsername(), systemTwo.getId());
		Assert.assertNotNull(accountTwo);
	}
	
	@Test
	public void incrementalProvisioningRemoveAccountWithRequest() {
		IdmRoleDto roleOne = getHelper().createRole();
		IdmRoleDto roleTwo = getHelper().createRole();
		IdmRoleDto roleThree = getHelper().createRole();

		// create test system with mapping and link her to role
		SysSystemDto systemOne = getHelper().createTestResourceSystem(true);
		SysSystemDto systemTwo = getHelper().createTestResourceSystem(true);
		getHelper().createRoleSystem(roleOne, systemOne);
		getHelper().createRoleSystem(roleTwo, systemTwo);
		getHelper().createRoleSystem(roleThree, systemOne);

		IdmIdentityDto identity = getHelper().createIdentity();
		IdmRoleRequestDto roleRequestOne = getHelper().createRoleRequest(identity, roleOne, roleTwo, roleThree);
		
		getHelper().executeRequest(roleRequestOne, false);

		// check after create
		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(3, assignedRoles.size());

		// check created account
		AccAccountDto accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNotNull(accountOne);
		Assert.assertNotNull(getHelper().findResource(accountOne.getRealUid()));
		AccAccountDto accountTwo = accountService.getAccount(identity.getUsername(), systemTwo.getId());
		Assert.assertNotNull(accountTwo);
	
		// check provisioning archive
		SysProvisioningOperationFilter archiveFilter = new SysProvisioningOperationFilter();
		archiveFilter.setEntityIdentifier(identity.getId());
		archiveFilter.setSystemId(systemOne.getId());

		List<SysProvisioningArchiveDto> executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(1, executedOperations.size());
		
		// Unassigns role One and Three with request
		IdmRoleRequestDto removeRoleRequest = getHelper().createRoleRequest(identity, ConceptRoleRequestOperation.REMOVE, roleThree, roleOne);
		getHelper().executeRequest(removeRoleRequest, false);
		
		// We expect only one new provisioning record in archive for system One
		executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(2, executedOperations.size());
		
		// We expect still only one provisioning record in archive for system Two
		archiveFilter.setSystemId(systemTwo.getId());
		executedOperations = provisioningArchiveService.find(archiveFilter, null).getContent();
		Assert.assertEquals(1, executedOperations.size());
		
		assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(1, assignedRoles.size());
		accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNull(accountOne);
		accountTwo = accountService.getAccount(identity.getUsername(), systemTwo.getId());
		Assert.assertNotNull(accountTwo);
	}
	
	@Test
	public void testOneRoleAssingnTwoSystemsViaRequest() {
		IdmRoleDto roleOne = getHelper().createRole();

		// create test system with mapping and link her to role
		SysSystemDto systemOne = getHelper().createTestResourceSystem(true);
		SysSystemDto systemTwo = getHelper().createTestResourceSystem(true);
		getHelper().createRoleSystem(roleOne, systemOne);
		getHelper().createRoleSystem(roleOne, systemTwo);

		IdmIdentityDto identity = getHelper().createIdentity();
		IdmRoleRequestDto roleRequestOne = getHelper().createRoleRequest(identity, roleOne);
		
		getHelper().executeRequest(roleRequestOne, false);

		// check after create
		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(1, assignedRoles.size());

		// check created account
		AccAccountDto accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNotNull(accountOne);
		Assert.assertNotNull(getHelper().findResource(accountOne.getRealUid()));
		AccAccountDto accountTwo = accountService.getAccount(identity.getUsername(), systemTwo.getId());
		Assert.assertNotNull(accountTwo);
	
		AccIdentityAccountFilter identityAccountFilter = new AccIdentityAccountFilter();
		identityAccountFilter.setIdentityId(identity.getId());

		List<AccIdentityAccountDto> identityAccounts = identityAccountService.find(identityAccountFilter, null)
				.getContent();
		// We have one role and two system -> two identity-accounts for roleOne should be exists.
		Assert.assertEquals(2, identityAccounts.size());
		long countIdentityAccountsWithRoleOne = identityAccounts.stream()
				.filter(identityAccount -> identityAccount.getIdentityRole().equals(assignedRoles.get(0).getId()))
				.count();
		Assert.assertEquals(2, countIdentityAccountsWithRoleOne);
	}
	
	@Test
	public void testOneRoleAssingnTwoSystems() {
		IdmRoleDto roleOne = getHelper().createRole();

		// create test system with mapping and link her to role
		SysSystemDto systemOne = getHelper().createTestResourceSystem(true);
		SysSystemDto systemTwo = getHelper().createTestResourceSystem(true);
		getHelper().createRoleSystem(roleOne, systemOne);

		IdmIdentityDto identity = getHelper().createIdentity();
		getHelper().createIdentityRole(identity, roleOne);
		// Role assign systemTwo now -> second account is not created 
		getHelper().createRoleSystem(roleOne, systemTwo);

		// check after create
		List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(1, assignedRoles.size());

		// check created account (second account is not created )
		AccAccountDto accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNotNull(accountOne);
		Assert.assertNotNull(getHelper().findResource(accountOne.getRealUid()));
		AccAccountDto accountTwo = accountService.getAccount(identity.getUsername(), systemTwo.getId());
		Assert.assertNull(accountTwo);

		// Execute ACM and provisioning via bulk action
		IdmBulkActionDto bulkAction = this.findBulkAction(IdmIdentity.class, IdentityAccountManagementBulkAction.NAME);
		bulkAction.setIdentifiers(Sets.newHashSet(identity.getId()));
		bulkActionManager.processAction(bulkAction);
		
		// check created account
		accountOne = accountService.getAccount(identity.getUsername(), systemOne.getId());
		Assert.assertNotNull(accountOne);
		Assert.assertNotNull(getHelper().findResource(accountOne.getRealUid()));
		accountTwo = accountService.getAccount(identity.getUsername(), systemTwo.getId());
		Assert.assertNotNull(accountTwo);
	
		AccIdentityAccountFilter identityAccountFilter = new AccIdentityAccountFilter();
		identityAccountFilter.setIdentityId(identity.getId());

		List<AccIdentityAccountDto> identityAccounts = identityAccountService.find(identityAccountFilter, null)
				.getContent();
		// We have one role and two system -> two identity-accounts for roleOne should be exists.
		Assert.assertEquals(2, identityAccounts.size());
		long countIdentityAccountsWithRoleOne = identityAccounts.stream()
				.filter(identityAccount -> identityAccount.getIdentityRole().equals(assignedRoles.get(0).getId()))
				.count();
		Assert.assertEquals(2, countIdentityAccountsWithRoleOne);
	}

	@Test
	public void testMappingContext() {
		SysSystemDto system = initData();
		Assert.assertNotNull(system);

		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(system.getId(), SystemEntityType.ROLE);
		Assert.assertNotNull(mapping);
		mapping.setMappingContextScript("context.put(\"test\", \"testOne\");");
		mapping = systemMappingService.save(mapping);



		// Delete role mapping
		systemMappingService.delete(mapping);
	}

	private SysSystemDto initData() {

		// create test system
		SysSystemDto system = helper.createSystem(TestRoleResource.TABLE_NAME);
		Assert.assertNotNull(system);

		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);

		// Create mapping
		SysSystemMappingDto syncSystemMapping = new SysSystemMappingDto();
		syncSystemMapping.setName("default_" + System.currentTimeMillis());
		syncSystemMapping.setEntityType(SystemEntityType.ROLE);
		syncSystemMapping.setOperationType(SystemOperationType.PROVISIONING);
		syncSystemMapping.setObjectClass(objectClasses.get(0).getId());
		final SysSystemMappingDto syncMapping = systemMappingService.save(syncSystemMapping);
		createMapping(system, syncMapping);
		this.getBean().initRoleData();
		return system;

	}

	@Transactional
	public void initRoleData() {
		deleteAllResourceData();

	}
	
	protected TestHelper getHelper() {
		return (TestHelper) super.getHelper();
	}

	private void createMapping(SysSystemDto system, final SysSystemMappingDto entityHandlingResult) {
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());

		Page<SysSchemaAttributeDto> schemaAttributesPage = schemaAttributeService.find(schemaAttributeFilter, null);
		schemaAttributesPage.forEach(schemaAttr -> {
			if (ATTRIBUTE_NAME.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeHandlingName = new SysSystemAttributeMappingDto();
				attributeHandlingName.setUid(true);
				attributeHandlingName.setEntityAttribute(true);
				attributeHandlingName.setIdmPropertyName("name");
				attributeHandlingName.setName(schemaAttr.getName());
				attributeHandlingName.setSchemaAttribute(schemaAttr.getId());
				// For provisioning .. we need create UID
				attributeHandlingName.setTransformToResourceScript("return entity.getName();");
				attributeHandlingName.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeHandlingName);

			} else if ("TYPE".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeHandlingName = new SysSystemAttributeMappingDto();
				attributeHandlingName.setIdmPropertyName("roleType");
				attributeHandlingName.setEntityAttribute(true);
				attributeHandlingName.setSchemaAttribute(schemaAttr.getId());
				attributeHandlingName.setName(schemaAttr.getName());
				attributeHandlingName.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeHandlingName);
			
			} else if ("PRIORITY".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeHandlingName = new SysSystemAttributeMappingDto();
				attributeHandlingName.setIdmPropertyName("priority");
				attributeHandlingName.setEntityAttribute(true);
				attributeHandlingName.setSchemaAttribute(schemaAttr.getId());
				attributeHandlingName.setName(schemaAttr.getName());
				attributeHandlingName.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeHandlingName);

			} else if ("APPROVE_REMOVE".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeHandlingName = new SysSystemAttributeMappingDto();
				attributeHandlingName.setIdmPropertyName("approveRemove");
				attributeHandlingName.setName(schemaAttr.getName());
				attributeHandlingName.setEntityAttribute(true);
				attributeHandlingName.setSchemaAttribute(schemaAttr.getId());
				attributeHandlingName.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeHandlingName);

			} else if ("MODIFIED".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeHandlingName = new SysSystemAttributeMappingDto();
				attributeHandlingName.setIdmPropertyName("changed");
				attributeHandlingName.setName(schemaAttr.getName());
				attributeHandlingName.setEntityAttribute(false);
				attributeHandlingName.setExtendedAttribute(true);
				attributeHandlingName.setSchemaAttribute(schemaAttr.getId());
				attributeHandlingName.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeHandlingName);
	
			} else if ("DESCRIPTION".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeHandlingName = new SysSystemAttributeMappingDto();
				attributeHandlingName.setIdmPropertyName("description");
				attributeHandlingName.setName(schemaAttr.getName());
				attributeHandlingName.setEntityAttribute(true);;
				attributeHandlingName.setSchemaAttribute(schemaAttr.getId());
				attributeHandlingName.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeHandlingName);
	
			}
		});
	}

	@Transactional
	public void deleteAllResourceData() {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM " + TestRoleResource.TABLE_NAME);
		q.executeUpdate();
	}
	
	private SysSystemDto initIdentityData() {

		// create test system
		SysSystemDto system = helper.createSystem(TestResource.TABLE_NAME);
		Assert.assertNotNull(system);

		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);

		// Create mapping
		SysSystemMappingDto syncSystemMapping = new SysSystemMappingDto();
		syncSystemMapping.setName("default_" + System.currentTimeMillis());
		syncSystemMapping.setEntityType(SystemEntityType.IDENTITY);
		syncSystemMapping.setOperationType(SystemOperationType.PROVISIONING);
		syncSystemMapping.setObjectClass(objectClasses.get(0).getId());
		final SysSystemMappingDto syncMapping = systemMappingService.save(syncSystemMapping);
		createIdentityMapping(system, syncMapping);
		return system;

	}
	
	private void createIdentityMapping(SysSystemDto system, final SysSystemMappingDto entityHandlingResult) {
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());

		Page<SysSchemaAttributeDto> schemaAttributesPage = schemaAttributeService.find(schemaAttributeFilter, null);
		schemaAttributesPage.forEach(schemaAttr -> {
			if (ATTRIBUTE_NAME.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(true);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName("username");
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMapping);

			} else if ("firstname".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName("firstName");
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMapping);

			} else if ("lastname".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName("lastName");
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMapping);

			} else if (ATTRIBUTE_EMAIL.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName("email");
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMapping);

			}
		});
	}

	private AccountManagementTest getBean() {
		return applicationContext.getAutowireCapableBeanFactory().createBean(this.getClass());
	}
	
	/**
	 * Find bulk action
	 *
	 * @param entity
	 * @param name
	 * @return
	 */
	protected IdmBulkActionDto findBulkAction(Class<? extends AbstractEntity> entity, String name) {
		List<IdmBulkActionDto> actions = bulkActionManager.getAvailableActions(entity);
		assertFalse(actions.isEmpty());
		
		for (IdmBulkActionDto action : actions) {
			if (action.getName().equals(name)) {
				return action;
			}
		}
		fail("For entity class: " + entity.getSimpleName() + " was not found bulk action: " + name);
		return null;
	}
}