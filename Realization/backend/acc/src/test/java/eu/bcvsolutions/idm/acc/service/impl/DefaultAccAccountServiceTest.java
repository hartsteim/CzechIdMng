package eu.bcvsolutions.idm.acc.service.impl;

import java.time.ZonedDateTime;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import eu.bcvsolutions.idm.acc.TestHelper;
import eu.bcvsolutions.idm.acc.domain.AccGroupPermission;
import eu.bcvsolutions.idm.acc.domain.AccountType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.entity.AccAccount;
import eu.bcvsolutions.idm.acc.entity.TestResource;
import eu.bcvsolutions.idm.acc.security.evaluator.ReadAccountByIdentityEvaluator;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.dto.IdmAuthorizationPolicyDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.exception.ForbiddenEntityException;
import eu.bcvsolutions.idm.core.api.service.IdmAuthorizationPolicyService;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.dto.LoginDto;
import eu.bcvsolutions.idm.core.security.api.service.LoginService;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Account tests
 * 
 * @author Svanda
 *
 */
public class DefaultAccAccountServiceTest extends AbstractIntegrationTest {

	@Autowired
	private TestHelper helper;
	@Autowired
	private SysSystemService systemService;
	@Autowired
	private AccAccountService accountService;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	@Qualifier("entityManager")
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	private IdmAuthorizationPolicyService authorizationPolicyService;
	@Autowired
	private AccIdentityAccountService identityAccountService;
	@Autowired
	private LoginService loginService;

	@Before
	public void init() {
		loginAsAdmin();
		this.getBean().deleteAllResourceData();
	}

	@After
	public void logout() {
		super.logout();
	}

	@Test
	public void getConnectorObjectTest() {
		String userOneName = "UserOne";
		String eavAttributeName = helper.getSchemaColumnName("EAV_ATTRIBUTE");
		SysSystemDto system = initData();
		Assert.assertNotNull(system);

		IdmIdentityDto identity = helper.createIdentity();

		// Create role with evaluator
		IdmRoleDto role = helper.createRole();
		IdmAuthorizationPolicyDto policyAccount = new IdmAuthorizationPolicyDto();
		policyAccount.setRole(role.getId());
		policyAccount.setGroupPermission(AccGroupPermission.ACCOUNT.getName());
		policyAccount.setAuthorizableType(AccAccount.class.getCanonicalName());
		policyAccount.setEvaluator(ReadAccountByIdentityEvaluator.class);
		authorizationPolicyService.save(policyAccount);

		// Change resources (set state on exclude) .. must be call in transaction
		this.getBean().persistResource(createResource(userOneName, ZonedDateTime.now()));
		AccAccountDto account = new AccAccountDto();
		account.setEntityType(SystemEntityType.IDENTITY);
		account.setSystem(system.getId());
		account.setAccountType(AccountType.PERSONAL);
		account.setUid(userOneName);
		account = accountService.save(account);

		AccIdentityAccountDto accountIdentityOne = new AccIdentityAccountDto();
		accountIdentityOne.setIdentity(identity.getId());
		accountIdentityOne.setOwnership(true);
		accountIdentityOne.setAccount(account.getId());
		accountIdentityOne = identityAccountService.save(accountIdentityOne);

		// Assign role with evaluator
		helper.createIdentityRole(identity, role);

		logout();
		loginService.login(new LoginDto(identity.getUsername(), identity.getPassword()));

		IcConnectorObject connectorObject = accountService.getConnectorObject(account, IdmBasePermission.READ);
		Assert.assertNotNull(connectorObject);
		Assert.assertEquals(userOneName, connectorObject.getUidValue());
		Assert.assertNotNull(connectorObject.getAttributeByName(eavAttributeName));
		Assert.assertEquals(userOneName, connectorObject.getAttributeByName(eavAttributeName).getValue());

	}

	/**
	 * We do not create relation Identity account ... we must not have the
	 * permissions on the account
	 */
	@Test(expected = ForbiddenEntityException.class)
	public void getConnectorObjectForbiddenTest() {
		String userOneName = "UserOne";
		String eavAttributeName = "EAV_ATTRIBUTE";
		SysSystemDto system = initData();
		Assert.assertNotNull(system);

		IdmIdentityDto identity = helper.createIdentity();

		// Create role with evaluator
		IdmRoleDto role = helper.createRole();
		IdmAuthorizationPolicyDto policyAccount = new IdmAuthorizationPolicyDto();
		policyAccount.setRole(role.getId());
		policyAccount.setGroupPermission(AccGroupPermission.ACCOUNT.getName());
		policyAccount.setAuthorizableType(AccAccount.class.getCanonicalName());
		policyAccount.setEvaluator(ReadAccountByIdentityEvaluator.class);
		authorizationPolicyService.save(policyAccount);

		// Change resources (set state on exclude) .. must be call in transaction
		this.getBean().persistResource(createResource(userOneName, ZonedDateTime.now()));
		AccAccountDto account = new AccAccountDto();
		account.setEntityType(SystemEntityType.IDENTITY);
		account.setSystem(system.getId());
		account.setAccountType(AccountType.PERSONAL);
		account.setUid(userOneName);
		account = accountService.save(account);

		// Assign role with evaluator
		helper.createIdentityRole(identity, role);

		logout();
		loginService.login(new LoginDto(identity.getUsername(), identity.getPassword()));

		IcConnectorObject connectorObject = accountService.getConnectorObject(account, IdmBasePermission.READ);
		Assert.assertNotNull(connectorObject);
		Assert.assertEquals(userOneName, connectorObject.getUidValue());
		Assert.assertNotNull(connectorObject.getAttributeByName(eavAttributeName));
		Assert.assertEquals(userOneName, connectorObject.getAttributeByName(eavAttributeName).getValue());

	}

	@Test
	public void getConnectorObjectNotFullTest() {
		String userOneName = "UserOne";
		String eavAttributeName = "EAV_ATTRIBUTE";
		SysSystemDto system = initData();
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());
		// Find and delete EAV schema attribute.
		SysSchemaAttributeDto eavAttribute = schemaAttributeService.find(schemaAttributeFilter, null).getContent()
				.stream().filter(attribute -> attribute.getName().equalsIgnoreCase(eavAttributeName)).findFirst()
				.orElse(null);
		Assert.assertNotNull(eavAttribute);
		schemaAttributeService.delete(eavAttribute);
		Assert.assertNotNull(system);
		// Change resources (set state on exclude) .. must be call in transaction
		this.getBean().persistResource(createResource(userOneName, ZonedDateTime.now()));
		AccAccountDto account = new AccAccountDto();
		account.setEntityType(SystemEntityType.IDENTITY);
		account.setSystem(system.getId());
		account.setAccountType(AccountType.PERSONAL);
		account.setUid(userOneName);
		account = accountService.save(account);

		IdmIdentityDto identity = helper.createIdentity();

		AccIdentityAccountDto accountIdentityOne = new AccIdentityAccountDto();
		accountIdentityOne.setIdentity(identity.getId());
		accountIdentityOne.setOwnership(true);
		accountIdentityOne.setAccount(account.getId());
		accountIdentityOne = identityAccountService.save(accountIdentityOne);

		// Create role with evaluator
		IdmRoleDto role = helper.createRole();
		IdmAuthorizationPolicyDto policyAccount = new IdmAuthorizationPolicyDto();
		policyAccount.setRole(role.getId());
		policyAccount.setGroupPermission(AccGroupPermission.ACCOUNT.getName());
		policyAccount.setAuthorizableType(AccAccount.class.getCanonicalName());
		policyAccount.setEvaluator(ReadAccountByIdentityEvaluator.class);
		authorizationPolicyService.save(policyAccount);

		// Assign role with evaluator
		helper.createIdentityRole(identity, role);

		logout();
		loginService.login(new LoginDto(identity.getUsername(), identity.getPassword()));

		IcConnectorObject connectorObject = accountService.getConnectorObject(account, IdmBasePermission.READ);
		Assert.assertNotNull(connectorObject);
		Assert.assertEquals(userOneName, connectorObject.getUidValue());
		// EAV attribute must be null, because we deleted the schema definition
		Assert.assertNull(connectorObject.getAttributeByName(eavAttributeName));

	}

	/**
	 * We do not create relation Identity account ... we must not have the
	 * permissions on the account
	 */
	@Test(expected = ForbiddenEntityException.class)
	public void getConnectorObjectNotFullForbiddenTest() {
		String userOneName = "UserOne";
		String eavAttributeName = "EAV_ATTRIBUTE";
		SysSystemDto system = initData();
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());
		// Find and delete EAV schema attribute.
		SysSchemaAttributeDto eavAttribute = schemaAttributeService.find(schemaAttributeFilter, null).getContent()
				.stream().filter(attribute -> attribute.getName().equalsIgnoreCase(eavAttributeName)).findFirst()
				.orElse(null);
		Assert.assertNotNull(eavAttribute);
		schemaAttributeService.delete(eavAttribute);
		Assert.assertNotNull(system);
		// Change resources (set state on exclude) .. must be call in transaction
		this.getBean().persistResource(createResource(userOneName, ZonedDateTime.now()));
		AccAccountDto account = new AccAccountDto();
		account.setEntityType(SystemEntityType.IDENTITY);
		account.setSystem(system.getId());
		account.setAccountType(AccountType.PERSONAL);
		account.setUid(userOneName);
		account = accountService.save(account);

		IdmIdentityDto identity = helper.createIdentity();

		// Create role with evaluator
		IdmRoleDto role = helper.createRole();
		IdmAuthorizationPolicyDto policyAccount = new IdmAuthorizationPolicyDto();
		policyAccount.setRole(role.getId());
		policyAccount.setGroupPermission(AccGroupPermission.ACCOUNT.getName());
		policyAccount.setAuthorizableType(AccAccount.class.getCanonicalName());
		policyAccount.setEvaluator(ReadAccountByIdentityEvaluator.class);
		authorizationPolicyService.save(policyAccount);

		// Assign role with evaluator
		helper.createIdentityRole(identity, role);

		logout();
		loginService.login(new LoginDto(identity.getUsername(), identity.getPassword()));

		IcConnectorObject connectorObject = accountService.getConnectorObject(account, IdmBasePermission.READ);
		Assert.assertNotNull(connectorObject);
		Assert.assertEquals(userOneName, connectorObject.getUidValue());
		// EAV attribute must be null, because we deleted the schema definition
		Assert.assertNull(connectorObject.getAttributeByName(eavAttributeName));
	}
	
	@Test
	public void targetEntityTest() {
		String userOneName = "UserOne";
		SysSystemDto system = initData();
		AccAccountDto account = new AccAccountDto();
		account.setEntityType(SystemEntityType.IDENTITY);
		account.setSystem(system.getId());
		account.setAccountType(AccountType.PERSONAL);
		account.setUid(userOneName);
		account = accountService.save(account);

		IdmIdentityDto identity = helper.createIdentity();

		AccIdentityAccountDto accountIdentityOne = new AccIdentityAccountDto();
		accountIdentityOne.setIdentity(identity.getId());
		accountIdentityOne.setOwnership(true);
		accountIdentityOne.setAccount(account.getId());
		accountIdentityOne = identityAccountService.save(accountIdentityOne);

		// Create role with evaluator
		IdmRoleDto role = helper.createRole();
		IdmAuthorizationPolicyDto policyAccount = new IdmAuthorizationPolicyDto();
		policyAccount.setRole(role.getId());
		policyAccount.setGroupPermission(AccGroupPermission.ACCOUNT.getName());
		policyAccount.setAuthorizableType(AccAccount.class.getCanonicalName());
		policyAccount.setEvaluator(ReadAccountByIdentityEvaluator.class);
		authorizationPolicyService.save(policyAccount);

		// Assign role with evaluator
		helper.createIdentityRole(identity, role);

		logout();
		loginService.login(new LoginDto(identity.getUsername(), identity.getPassword()));
		
		account = accountService.get(account.getId());
		
		Assert.assertEquals(identity.getId(), account.getTargetEntityId());
		Assert.assertEquals(IdmIdentityDto.class.getName(), account.getTargetEntityType());

	}

	@Transactional
	public void persistResource(TestResource resource) {
		entityManager.persist(resource);
	}

	private TestResource createResource(String code, ZonedDateTime modified) {
		TestResource resource = new TestResource();
		resource.setName(code);
		resource.setEmail(code);
		resource.setDescrip(code);
		resource.setFirstname(code);
		resource.setLastname(code);
		resource.setModified(modified);
		resource.setStatus(code);
		resource.setEavAttribute(code);

		return resource;
	}

	private SysSystemDto initData() {

		// create test system
		SysSystemDto system = helper.createSystem(TestResource.TABLE_NAME);
		Assert.assertNotNull(system);

		// generate schema for system
		systemService.generateSchema(system);
		return system;

	}

	@Transactional
	public void deleteAllResourceData() {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM " + TestResource.TABLE_NAME);
		q.executeUpdate();
	}

	private DefaultAccAccountServiceTest getBean() {
		return applicationContext.getAutowireCapableBeanFactory().createBean(this.getClass());
	}
}
