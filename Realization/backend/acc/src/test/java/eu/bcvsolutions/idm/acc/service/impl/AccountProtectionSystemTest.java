package eu.bcvsolutions.idm.acc.service.impl;

import java.util.List;

import javax.persistence.EntityManager;

import java.time.ZonedDateTime;
import java.time.LocalDate;

import eu.bcvsolutions.idm.acc.dto.*;
import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.dto.*;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.TestHelper;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.filter.AccIdentityAccountFilter;
import eu.bcvsolutions.idm.acc.entity.TestResource;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Protection account system
 * 
 * @author Svanda
 *
 */
public class AccountProtectionSystemTest extends AbstractIntegrationTest {

	private static String ROLE_ONE;

	@Autowired
	private TestHelper helper;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private AccAccountService accountService;
	@Autowired
	private SysSystemMappingService systemMappingService;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private AccIdentityAccountService identityAccountService;
	@Autowired
	private DefaultSysRoleSystemService roleSystemService;

	@Before
	public void init() {
		loginAsAdmin();
	}

	@After
	public void logout() {
		super.logout();
	}

	@Test
	public void accountWithoutProtectionTest() {

		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = initSystem();
		IdmIdentityRoleDto identityRole = helper.createIdentityRole(identity, roleService.getByCode(ROLE_ONE));

		AccAccountDto account = accountService.getAccount(identity.getUsername(), system.getId());

		Assert.assertNotNull(account);
		Assert.assertFalse(account.isInProtection());
		TestResource createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());

		// Remove role from identity
		identityRoleService.deleteById(identityRole.getId());

		account = accountService.getAccount(identity.getUsername(), system.getId());

		Assert.assertNull(account);
		createdAccount = entityManager.find(TestResource.class, identity.getUsername());
		Assert.assertNull(createdAccount);
	}

	@Test
	public void accountWithProtectionTest() {
		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = initSystem();
		IdmRoleDto roleOne = roleService.getByCode(ROLE_ONE);

		// Set system to protected mode
		SysSystemMappingDto mapping = systemMappingService
				.findBySystem(system, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY).get(0);
		mapping.setProtectionEnabled(Boolean.TRUE);
		mapping.setProtectionInterval(null);
		systemMappingService.save(mapping);

		IdmIdentityRoleDto identityRole = helper.createIdentityRole(identity, roleOne);

		AccAccountDto account = accountService.getAccount(identity.getUsername(), system.getId());

		Assert.assertNotNull(account);
		Assert.assertFalse(account.isInProtection());
		TestResource createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());

		// Remove role from identity
		identityRoleService.deleteById(identityRole.getId());

		account = accountService.getAccount(identity.getUsername(), system.getId());
		Assert.assertNotNull(account);
		Assert.assertTrue(account.isInProtection());
		Assert.assertNull(account.getEndOfProtection());
		createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());
	}
	
	@Test
	public void forceIdentityDeleteAndRelinkOrphanAccountTest() {
		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = initSystem();
		IdmRoleDto roleOne = roleService.getByCode(ROLE_ONE);

		// Set system to protected mode
		SysSystemMappingDto mapping = systemMappingService
				.findBySystem(system, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY).get(0);
		mapping.setProtectionEnabled(Boolean.TRUE);
		mapping.setProtectionInterval(null);
		systemMappingService.save(mapping);

		helper.createIdentityRole(identity, roleOne);

		AccAccountDto account = accountService.getAccount(identity.getUsername(), system.getId());

		Assert.assertNotNull(account);
		Assert.assertFalse(account.isInProtection());
		TestResource createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());

		account = accountService.getAccount(identity.getUsername(), system.getId());
		// Force delete of identity
		identityService.delete(identity);
		
		Assert.assertNull(identityService.get(identity.getId()));
		
		// Orphan must exists
		account = accountService.get(account.getId());
		Assert.assertNotNull(account);
		Assert.assertTrue(account.isInProtection());
		Assert.assertNull(account.getEndOfProtection());
		createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());
		
		// Create new identity with same username
		identity = helper.createIdentity(identity.getUsername());
		// Assign same role
		helper.createIdentityRole(identity, roleOne);
		
		// Same account must exist (same ID), but now must be not in protected mode.
		account = accountService.get(account.getId());
		Assert.assertNotNull(account);
		Assert.assertFalse(account.isInProtection());
		createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());
	}

	@Test
	public void deleteAccountOnProtectionSystemTest() {
		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = initSystem();
		IdmRoleDto roleOne = roleService.getByCode(ROLE_ONE);

		// Set system to protected mode
		SysSystemMappingDto mapping = systemMappingService
				.findBySystem(system, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY).get(0);
		mapping.setProtectionEnabled(Boolean.TRUE);
		mapping.setProtectionInterval(null);
		systemMappingService.save(mapping);
		// Assign the role
		helper.createIdentityRole(identity, roleOne);

		AccAccountDto account = accountService.getAccount(identity.getUsername(), system.getId());

		AccIdentityAccountFilter identityAccountFilter = new AccIdentityAccountFilter();
		identityAccountFilter.setAccountId(account.getId());
		List<AccIdentityAccountDto> identityAccounts = identityAccountService.find(identityAccountFilter, null)
				.getContent();
		// Identity account have relation on the role
		Assert.assertEquals(1, identityAccounts.size());
		Assert.assertNotNull(identityAccounts.get(0).getIdentityRole());

		Assert.assertNotNull(account);
		Assert.assertFalse(account.isInProtection());
		TestResource createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());

		// Remove account directly. Account must be transformed to the protection state.
		accountService.delete(account);

		account = accountService.getAccount(identity.getUsername(), system.getId());
		Assert.assertNotNull(account);
		Assert.assertTrue(account.isInProtection());
		Assert.assertNull(account.getEndOfProtection());
		createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());

		// Identity account have not relation on the role now.
		identityAccounts = identityAccountService.find(identityAccountFilter, null).getContent();
		Assert.assertEquals(1, identityAccounts.size());
		Assert.assertNull(identityAccounts.get(0).getIdentityRole());
	}

	@Test
	public void accountWithProtectionRetryTest() {

		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = initSystem();
		IdmRoleDto roleOne = roleService.getByCode(ROLE_ONE);

		// Set system to protected mode
		SysSystemMappingDto mapping = helper.getDefaultMapping(system);
		mapping.setProtectionInterval(null);
		mapping.setProtectionEnabled(true);
		mapping = systemMappingService.save(mapping);

		IdmIdentityRoleDto identityRole = helper.createIdentityRole(identity, roleOne);

		AccAccountDto account = accountService.getAccount(identity.getUsername(), system.getId());

		// Remove role from identity
		identityRoleService.deleteById(identityRole.getId());

		account = accountService.getAccount(identity.getUsername(), system.getId());
		Assert.assertNotNull(account);
		Assert.assertTrue(account.isInProtection());
		Assert.assertNull(account.getEndOfProtection());
		TestResource createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());

		// We again assign same role
		identityRole = helper.createIdentityRole(identity, roleOne);

		// Account must be unprotected
		account = accountService.getAccount(identity.getUsername(), system.getId());
		Assert.assertNotNull(account);
		Assert.assertFalse(account.isInProtection());
		createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());
	}

	@Test
	public void accountWithProtectionRetryBusinessRoleTest() {

		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = initSystem();
		IdmRoleDto roleTop = helper.createRole();
		IdmRoleDto roleSubNotGrantingAcc = helper.createRole();
		final SysRoleSystemDto roleSystem = helper.createRoleSystem(roleSubNotGrantingAcc, system);
		roleSystem.setCreateAccountByDefault(false);
		roleSystemService.save(roleSystem);

		IdmRoleDto roleSub = roleService.getByCode(ROLE_ONE);

		helper.createRoleComposition(roleTop, roleSub);
		helper.createRoleComposition(roleTop, roleSubNotGrantingAcc);

		// Set system to protected mode
		SysSystemMappingDto mapping = helper.getDefaultMapping(system);
		mapping.setProtectionInterval(null);
		mapping.setProtectionEnabled(true);
		mapping = systemMappingService.save(mapping);

		final IdmIdentityContractDto primeContract = helper.getPrimeContract(identity);
		final IdmRoleRequestDto idmRoleRequestDto = helper.assignRoles(primeContract, roleTop);

		final IdmRoleRequestDto roleRequest = helper.createRoleRequest(identity, ConceptRoleRequestOperation.REMOVE, roleTop);
		// Remove role from identity
		helper.executeRequest(roleRequest, true, true);

		AccAccountDto account = accountService.getAccount(identity.getUsername(), system.getId());
		Assert.assertNotNull(account);
		Assert.assertTrue(account.isInProtection());
		Assert.assertNull(account.getEndOfProtection());
		TestResource createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());

		// We again assign same role
		helper.assignRoles(primeContract, roleTop);

		// Account must be unprotected
		account = accountService.getAccount(identity.getUsername(), system.getId());
		Assert.assertNotNull(account);
		Assert.assertFalse(account.isInProtection());
		createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());
	}

	@Test
	public void accountWithProtectionAndIntervalTest() {

		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = initSystem();
		IdmRoleDto roleOne = roleService.getByCode(ROLE_ONE);

		int intervalInDays = 10;

		// Set system to protected mode
		SysSystemMappingDto mapping = systemMappingService
				.findBySystem(system, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY).get(0);
		mapping.setProtectionEnabled(Boolean.TRUE);
		mapping.setProtectionInterval(intervalInDays);
		mapping = systemMappingService.save(mapping);

		IdmIdentityRoleDto identityRole = helper.createIdentityRole(identity, roleOne);

		AccAccountDto account = accountService.getAccount(identity.getUsername(), system.getId());

		Assert.assertNotNull(account);
		Assert.assertFalse(account.isInProtection());
		TestResource createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());

		// Remove role from identity
		identityRoleService.deleteById(identityRole.getId());

		account = accountService.getAccount(identity.getUsername(), system.getId());
		Assert.assertNotNull(account);
		Assert.assertTrue(account.isInProtection());
		Assert.assertNotNull(account.getEndOfProtection());
		Assert.assertTrue(account.getEndOfProtection().toLocalDate().isEqual(LocalDate.now().plusDays(intervalInDays)));
		createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());
	}

	/**
	 * When is account in protection mode, then cannot be provisioned.
	 */
	@Test
	public void protectedAccountNoProvisioningTest() {

		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = initSystem();
		IdmRoleDto roleOne = roleService.getByCode(ROLE_ONE);

		int intervalInDays = 10;

		// Set system to protected mode
		SysSystemMappingDto mapping = systemMappingService
				.findBySystem(system, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY).get(0);
		mapping.setProtectionEnabled(Boolean.TRUE);
		mapping.setProtectionInterval(intervalInDays);
		systemMappingService.save(mapping);

		String changedValue = "changed";
		identity.setFirstName(changedValue);
		identityService.save(identity);

		IdmIdentityRoleDto identityRole = helper.createIdentityRole(identity, roleOne);

		AccAccountDto account = accountService.getAccount(identity.getUsername(), system.getId());

		Assert.assertNotNull(account);
		Assert.assertFalse(account.isInProtection());
		TestResource createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(changedValue, createdAccount.getFirstname());

		// Remove role from identity
		identityRoleService.deleteById(identityRole.getId());

		account = accountService.getAccount(identity.getUsername(), system.getId());
		Assert.assertNotNull(account);
		Assert.assertTrue(account.isInProtection());
		Assert.assertNotNull(account.getEndOfProtection());
		Assert.assertTrue(account.getEndOfProtection().toLocalDate().isEqual(LocalDate.now().plusDays(intervalInDays)));
		createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());

		// Change first name and emit provisioning (provisioning must be break)
		identity.setFirstName(identity.getUsername());
		identityService.save(identity);

		createdAccount = helper.findResource(account.getUid());
		Assert.assertNotEquals(identity.getFirstName(), createdAccount.getFirstname());
	}

	/**
	 * When is account in protection mode, then cannot be deleted.
	 */
	@Test(expected = ResultCodeException.class)
	public void protectedAccountDeleteTest() {

		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = initSystem();
		IdmRoleDto roleOne = roleService.getByCode(ROLE_ONE);

		// Set system to protected mode
		SysSystemMappingDto mapping = systemMappingService
				.findBySystem(system, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY).get(0);
		mapping.setProtectionEnabled(Boolean.TRUE);
		mapping.setProtectionInterval(null);
		systemMappingService.save(mapping);

		IdmIdentityRoleDto identityRole = helper.createIdentityRole(identity, roleOne);

		AccAccountDto account = accountService.getAccount(identity.getUsername(), system.getId());
		Assert.assertNotNull(account);
		Assert.assertFalse(account.isInProtection());
		TestResource createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);

		// Remove role from identity
		identityRoleService.deleteById(identityRole.getId());

		account = accountService.getAccount(identity.getUsername(), system.getId());
		Assert.assertNotNull(account);
		Assert.assertTrue(account.isInProtection());
		Assert.assertNull(account.getEndOfProtection());
		createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());

		// Delete AccAccount directly
		accountService.delete(account);
	}

	/**
	 * When is account in protection mode, then cannot be identity account deleted.
	 */
	@Test(expected = ResultCodeException.class)
	public void protectedIdentityAccountDeleteTest() {

		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = initSystem();
		IdmRoleDto roleOne = roleService.getByCode(ROLE_ONE);

		// Set system to protected mode
		SysSystemMappingDto mapping = systemMappingService
				.findBySystem(system, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY).get(0);
		mapping.setProtectionEnabled(Boolean.TRUE);
		mapping.setProtectionInterval(null);
		systemMappingService.save(mapping);

		IdmIdentityRoleDto identityRole = helper.createIdentityRole(identity, roleOne);

		AccAccountDto account = accountService.getAccount(identity.getUsername(), system.getId());

		Assert.assertNotNull(account);
		Assert.assertFalse(account.isInProtection());
		TestResource createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);

		// Remove role from identity
		identityRoleService.deleteById(identityRole.getId());

		AccIdentityAccountFilter identityAccountFilter = new AccIdentityAccountFilter();
		identityAccountFilter.setAccountId(account.getId());
		List<AccIdentityAccountDto> identityAccounts = identityAccountService.find(identityAccountFilter, null)
				.getContent();
		Assert.assertEquals(1, identityAccounts.size());
		// Remove identity account again. Now must end on the exception (account is
		// already in protection)
		identityAccountService.delete(identityAccounts.get(0));
	}

	/**
	 * On delete of the identity could deleted accounts in the protected mode too.
	 */
	@Test
	public void protectedIdentityDeleteTest() {

		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = initSystem();
		IdmRoleDto roleOne = roleService.getByCode(ROLE_ONE);

		// Set system to protected mode
		SysSystemMappingDto mapping = systemMappingService
				.findBySystem(system, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY).get(0);
		mapping.setProtectionEnabled(Boolean.TRUE);
		mapping.setProtectionInterval(null);
		systemMappingService.save(mapping);

		IdmIdentityRoleDto identityRole = helper.createIdentityRole(identity, roleOne);

		AccAccountDto account = accountService.getAccount(identity.getUsername(), system.getId());

		Assert.assertNotNull(account);
		Assert.assertFalse(account.isInProtection());
		TestResource createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);

		// Remove role from identity
		identityRoleService.deleteById(identityRole.getId());

		AccIdentityAccountFilter identityAccountFilter = new AccIdentityAccountFilter();
		identityAccountFilter.setAccountId(account.getId());
		List<AccIdentityAccountDto> identityAccounts = identityAccountService.find(identityAccountFilter, null)
				.getContent();
		Assert.assertEquals(1, identityAccounts.size());

		// On delete of the identity could deleted accounts in the protected mode too.
		identityService.delete(identity);

		// Identity have to be deleted
		Assert.assertNull(identityService.get(identity.getId()));

		// Force delete was used, so identity-account have to be deleted;
		Assert.assertNull(identityAccountService.get(identityAccounts.get(0).getId()));

		// Force delete does not delete the account. Account must exists and must be in
		// the protection mode.
		account = accountService.get(account.getId());
		Assert.assertNotNull(account);
		Assert.assertTrue(account.isInProtection());

	}

	/**
	 * When is account in protection mode (but expired), then can be deleted.
	 */
	@Test()
	public void protectedAccountExpiredDeleteTest() {

		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = initSystem();
		IdmRoleDto roleOne = roleService.getByCode(ROLE_ONE);

		// Set system to protected mode
		SysSystemMappingDto mapping = systemMappingService
				.findBySystem(system, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY).get(0);
		mapping.setProtectionEnabled(Boolean.TRUE);
		mapping.setProtectionInterval(null);
		systemMappingService.save(mapping);

		IdmIdentityRoleDto identityRole = helper.createIdentityRole(identity, roleOne);

		AccAccountDto account = accountService.getAccount(identity.getUsername(), system.getId());

		Assert.assertNotNull(account);
		Assert.assertFalse(account.isInProtection());
		TestResource createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);

		// Remove role from identity
		identityRoleService.deleteById(identityRole.getId());

		account = accountService.getAccount(identity.getUsername(), system.getId());
		Assert.assertNotNull(account);
		Assert.assertTrue(account.isInProtection());
		Assert.assertNull(account.getEndOfProtection());
		createdAccount = helper.findResource(account.getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());

		// Set account as expired
		account.setEndOfProtection(ZonedDateTime.now().minusMonths(1));
		account = accountService.save(account);

		// Delete AccAccount directly
		accountService.delete(account);
		account = accountService.getAccount(identity.getUsername(), system.getId());
		Assert.assertNull(account);
		createdAccount = helper.findResource(identity.getUsername());
		Assert.assertNull(createdAccount);
	}

	private SysSystemDto initSystem() {
		// create test system
		SysSystemDto system = helper.createTestResourceSystem(true);
		// Create role with link on system (default)
		IdmRoleDto role = helper.createRole();
		ROLE_ONE = role.getCode();
		// assign role to system
		helper.createRoleSystem(role, system);
		//
		return system;
	}
}
