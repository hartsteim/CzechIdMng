package eu.bcvsolutions.idm.acc.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.ImmutableList;

import eu.bcvsolutions.idm.acc.TestHelper;
import eu.bcvsolutions.idm.acc.config.domain.ProvisioningConfiguration;
import eu.bcvsolutions.idm.acc.domain.AccountType;
import eu.bcvsolutions.idm.acc.domain.AttributeMapping;
import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.ProvisioningContext;
import eu.bcvsolutions.idm.acc.domain.ProvisioningOperationType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.ProvisioningAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningArchiveDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.AccIdentityAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysProvisioningOperationFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemAttributeMappingFilter;
import eu.bcvsolutions.idm.acc.entity.AccAccount_;
import eu.bcvsolutions.idm.acc.entity.AccIdentityAccount_;
import eu.bcvsolutions.idm.acc.entity.TestResource;
import eu.bcvsolutions.idm.acc.event.SystemEvent;
import eu.bcvsolutions.idm.acc.event.SystemEvent.SystemEventType;
import eu.bcvsolutions.idm.acc.exception.ProvisioningException;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningArchiveService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.IdmPasswordPolicyGenerateType;
import eu.bcvsolutions.idm.core.api.domain.IdmPasswordPolicyType;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.IdmAccountDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmPasswordPolicyDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.PasswordChangeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmPasswordPolicyService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeTypeService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;
import eu.bcvsolutions.idm.ic.service.api.IcConnectorFacade;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Provisioning tests
 * 
 * Test cannot be @Transactional now - provisioning operations are executed after orignal transaction ends (we need to persist data)
 * 
 * @author Svanda
 * @author Radek Tomiška
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DefaultSysProvisioningServiceTest extends AbstractIntegrationTest {

	private static final String IDENTITY_PASSWORD_ONE = "password_one";
	private static final String IDENTITY_PASSWORD_TWO = "password_two";
	private static final String IDENTITY_PASSWORD_THREE = "password_three";
	private static final String IDENTITY_USERNAME = "provisioningTestUser";
	private static final String IDENTITY_USERNAME_TWO = "provisioningTestUserTwo";
	private static final String IDENTITY_USERNAME_CHANGED = "userChanged";
	private static final String IDENTITY_EXT_PASSWORD = "passwordExt";
	private static final String IDENTITY_CHANGED_FIRST_NAME = "changed first name";
	private static final String PASSWORD_POLICY = "passwordPolicy";
	private static final String EMAIL_ONE = "one.email@one.cz";
	private static final String EMAIL_TWO = "two.email@two.cz";
	private static final String SYSTEM_NAME = "DefaultSysProvisioningServiceTest";

	@Autowired private TestHelper helper;
	@Autowired private SysSystemService systemService;
	@Autowired private FormService formService;
	@Autowired private IdmIdentityService identityService;
	@Autowired private IdmIdentityContractService identityContractService;
	@Autowired private AccIdentityAccountService identityAccountService;
	@Autowired private AccAccountService accountService;
	@Autowired private ProvisioningService provisioningService;
	@Autowired private SysSystemMappingService systemEntityHandlingService;
	@Autowired private SysSystemAttributeMappingService systemAttributeMappingService;
	@Autowired private SysSchemaAttributeService schemaAttributeService;
	@Qualifier("entityManager")
	@Autowired private EntityManager entityManager;
	@Autowired private IdmPasswordPolicyService passwordPolicyService;
	@Autowired private IdmTreeNodeService treeNodeService;
	@Autowired private IdmTreeTypeService treeTypeService;
	@Autowired private SysSystemEntityService systemEntityService;
	@Autowired private SysRoleSystemService roleSystemService;
	@Autowired private SysRoleSystemAttributeService roleSystemAttributeService;
	@Autowired private SysSystemMappingService systemMappingService;
	@Autowired private IdmRoleService roleService;
	@Autowired private ConfigurationService configurationService;
	@Autowired private ProvisioningConfiguration provisioningConfiguration;
	@Autowired private SysProvisioningArchiveService porvisioningArchiveService;
	//
	private List<SysSchemaObjectClassDto> objectClasses = null;
	private SysSystemDto system = null;
	private SysSystemMappingDto systemMapping = null;
	
	@Before
	public void init() {
		loginAsAdmin();
	}

	@After
	public void logout() {
		super.logout();
	}

	@Test
	public void doIdentityProvisioningAddAccount() {
		initData();

		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);
		
		provisioningService.doProvisioning(DtoUtils.getEmbedded(accountIdentityOne, AccIdentityAccount_.identity, IdmIdentityDto.class));

		TestResource createdAccount = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());
	}

	@Test
	public void doIdentityProvisioningChangeAccount() {
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());

		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);
		TestResource createdAccount = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());

		identity.setFirstName(IDENTITY_CHANGED_FIRST_NAME);
		identity = identityService.save(identity);
		Assert.assertNotEquals(identity.getFirstName(), createdAccount.getFirstname());

		provisioningService.doProvisioning(identity);
		TestResource changedAccount = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertNotNull(changedAccount);
		Assert.assertEquals(identity.getFirstName(), changedAccount.getFirstname());
	}
	
	@Test
	public void doIdentityProvisioningChangeIdentityContract() {
		// change identity internally
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);
		identity.setFirstName("first-name-change");
		identity = identityService.saveInternal(identity);
		
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		
		IdmIdentityContractDto contract = identityContractService.getPrimeContract(identity.getId());
		contract.setDescription("update");
		identityContractService.save(contract);
		
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);
		TestResource changedAccount = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertNotNull(changedAccount);
		Assert.assertEquals(identity.getFirstName(), changedAccount.getFirstname());
		
		identity.setFirstName(IDENTITY_CHANGED_FIRST_NAME);
		identity = identityService.save(identity);

		changedAccount = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertNotNull(changedAccount);
		Assert.assertEquals(identity.getFirstName(), changedAccount.getFirstname());
	}
	
	/**
	 * Call provisioning for subordinates, after managers's contract changes - filled managers could be provisioned
	 * 
	 */
	@Test
	public void doIdentityProvisioningChangeManagersContract() {
		IdmIdentityDto managerOne = createIdentity();
		IdmIdentityDto subordinateOne = createIdentity();		
		IdmTreeNodeDto managerOnePosition = createTreeNode(null); 
		IdmIdentityContractDto managersContract = createIdentityContact(managerOne, managerOnePosition);		
		IdmTreeNodeDto subordinateOnePositionOne = createTreeNode(managerOnePosition);
		createIdentityContact(subordinateOne, subordinateOnePositionOne);
		AccIdentityAccountDto subordinateAccount = prepareAccount(subordinateOne);
		//
		provisioningService.doProvisioning(DtoUtils.getEmbedded(subordinateAccount, AccIdentityAccount_.identity, IdmIdentityDto.class));
		//
		TestResource account = entityManager.find(TestResource.class, accountService.get(subordinateAccount.getAccount()).getUid());
		Assert.assertNotNull(account);
		Assert.assertEquals(subordinateOne.getFirstName(), account.getFirstname());
		//
		IdmIdentityFilter filter = new IdmIdentityFilter();
		filter.setSubordinatesFor(managerOne.getId());
		List<IdmIdentityDto> subordinates = identityService.find(filter, null).getContent();
		Assert.assertEquals(1, subordinates.size());
		Assert.assertEquals(subordinateOne.getId(), subordinates.get(0).getId());
		//
		// change subordinate
		subordinateOne.setFirstName("first-name-change-one");
		subordinateOne = identityService.saveInternal(subordinateOne);
		//
		// change managers contract
		managersContract.setWorkPosition(null);
		managersContract = identityContractService.save(managersContract);
		//
		account = entityManager.find(TestResource.class, accountService.get(subordinateAccount.getAccount()).getUid());
		Assert.assertNotNull(account);
		Assert.assertEquals(subordinateOne.getFirstName(), account.getFirstname());
		subordinates = identityService.find(filter, null).getContent();
		Assert.assertEquals(0, subordinates.size());
		//
		// change subordinate again
		subordinateOne.setFirstName("first-name-change-two");
		subordinateOne = identityService.saveInternal(subordinateOne);
		//
		managersContract.setWorkPosition(managerOnePosition.getId());
		managersContract = identityContractService.save(managersContract);
		//
		account = entityManager.find(TestResource.class, accountService.get(subordinateAccount.getAccount()).getUid());
		Assert.assertNotNull(account);
		Assert.assertEquals(subordinateOne.getFirstName(), account.getFirstname());
		subordinates = identityService.find(filter, null).getContent();
		Assert.assertEquals(1, subordinates.size());
		Assert.assertEquals(subordinateOne.getId(), subordinates.get(0).getId());
	}
	
	/**
	 * Test for change account ID.
	 */
	@Test
	public void doIdentityProvisioningChangeAccountIdentifier() {
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME_TWO);
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());

		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);
		AccAccountDto account = accountService.get(accountIdentityOne.getAccount());

		identity.setUsername(IDENTITY_USERNAME_CHANGED);
		identity = identityService.save(identity);
		Assert.assertEquals("x"+IDENTITY_USERNAME_TWO, account.getUid());

		TestResource changedAccount = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertNotNull(changedAccount);
		Assert.assertEquals(identity.getUsername(), changedAccount.getName().substring(1));
		
		account = accountService.get(account.getId());
		Assert.assertEquals("x"+IDENTITY_USERNAME_CHANGED, account.getUid());
		Assert.assertEquals("x"+IDENTITY_USERNAME_CHANGED, account.getRealUid());
		
		
		// Change username back
		identity.setUsername(IDENTITY_USERNAME_TWO);
		identity = identityService.save(identity);
		account = accountService.get(account.getId());
		Assert.assertEquals("x"+IDENTITY_USERNAME_TWO, account.getUid());
		Assert.assertEquals("x"+IDENTITY_USERNAME_TWO, account.getRealUid());
	}

	@Test
	public void doIdentityProvisioningChangeAccountTransformFromResource() {
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());

		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);
		TestResource createdAccount = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());

		identity.setFirstName(IDENTITY_CHANGED_FIRST_NAME.substring(1));
		identity = identityService.save(identity);
		Assert.assertNotEquals(identity.getFirstName(), createdAccount.getFirstname());

		provisioningService.doProvisioning(identity);
		TestResource changedAccount = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertNotNull(changedAccount);
		// Must be with "c" on target system, because we have set transformation
		// from system!
		Assert.assertEquals(identity.getFirstName(), changedAccount.getFirstname().substring(1));
	}

	@Test
	public void doIdentityProvisioningChangeSingleAttribute() {
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);
		identity.setFirstName(IDENTITY_CHANGED_FIRST_NAME);
		identity = identityService.save(identity);
		Assert.assertEquals("Identity must have this first name!", IDENTITY_CHANGED_FIRST_NAME,
				identity.getFirstName());

		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		filter.setSystemId(systemService.getByCode(SYSTEM_NAME).getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);
		AccAccountDto account = accountService.get(accountIdentityOne.getAccount());
		SysSystemDto system = DtoUtils.getEmbedded(account, AccAccount_.system);
		SysSystemEntityDto systemEntity = DtoUtils.getEmbedded(account, AccAccount_.systemEntity);

		SysSystemAttributeMappingFilter attributeFilter = new SysSystemAttributeMappingFilter();
		attributeFilter.setSystemId(system.getId());
		attributeFilter.setIdmPropertyName("firstName");

		TestResource resourceAccount = entityManager.find(TestResource.class, "x" + IDENTITY_USERNAME);
		Assert.assertNotNull("Idenitity have to exists on target system (after account management)", resourceAccount);
		Assert.assertEquals("Account on target system, must have same first name as Identity",
				IDENTITY_CHANGED_FIRST_NAME, resourceAccount.getFirstname());

		provisioningService.doProvisioningForAttribute(
				systemEntity,
				systemAttributeMappingService.find(attributeFilter, null).getContent().get(0),
				IDENTITY_USERNAME,
				ProvisioningOperationType.UPDATE, 
				identity);

		resourceAccount = entityManager.find(TestResource.class, "x" + IDENTITY_USERNAME);
		Assert.assertNotNull("Idenitity have to exists on target system (after account management)", resourceAccount);
		Assert.assertEquals("Account on target system, must have changed first name!", IDENTITY_USERNAME,
				resourceAccount.getFirstname());
	}

	@Test
	public void doIdentityProvisioningChangePassword() {
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);
		SysSystemDto system = systemService.get(accountService.get(accountIdentityOne.getAccount()).getSystem());
		
		// Create new password one
		PasswordChangeDto passwordChange = new PasswordChangeDto();
		passwordChange.setAccounts(ImmutableList.of(accountIdentityOne.getAccount().toString()));
		passwordChange.setNewPassword(new GuardedString(IDENTITY_PASSWORD_ONE));
		passwordChange.setIdm(true);
		// Do change of password for selected accounts
		identityService.passwordChange(identity, passwordChange);
		accountIdentityOne = identityAccountService.get(accountIdentityOne.getId());

		// Check correct password One
		provisioningService.authenticate(accountService.get(accountIdentityOne.getAccount()).getUid(),
				new GuardedString(IDENTITY_PASSWORD_ONE), system, SystemEntityType.IDENTITY);

		// Check incorrect password
		try {
			provisioningService.authenticate(accountService.get(accountIdentityOne.getAccount()).getUid(),
					new GuardedString(IDENTITY_PASSWORD_TWO), system, SystemEntityType.IDENTITY);
			fail("Bad credentials exception is expected here!");
		} catch (ResultCodeException ex) {
			//
		}
		// Do change of password for selected accounts
		passwordChange.setNewPassword(new GuardedString(IDENTITY_PASSWORD_TWO));
		identityService.passwordChange(identityService.get(accountIdentityOne.getIdentity()), passwordChange);

		// Check correct password Two
		accountIdentityOne = identityAccountService.get(accountIdentityOne.getId());
		provisioningService.authenticate(accountService.get(accountIdentityOne.getAccount()).getUid(),
				new GuardedString(IDENTITY_PASSWORD_TWO), system, SystemEntityType.IDENTITY);
	}
	
	@Test
	public void doIdentityProvisioningChangePasswordUnsupportSystem() {
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);
		SysSystemDto system = systemService.get(accountService.get(accountIdentityOne.getAccount()).getSystem());
		
		EntityEvent<SysSystemDto> event = new SystemEvent(SystemEventType.DUPLICATE, system);
		SysSystemDto clonedSystem = systemService.publish(event).getContent();
		
		clonedSystem.setReadonly(false);
		clonedSystem.setDisabled(false);
		clonedSystem = systemService.save(clonedSystem);
		
		SysSystemAttributeMappingFilter attributeMappingFilter = new SysSystemAttributeMappingFilter();
		attributeMappingFilter.setSystemId(clonedSystem.getId());
		
		SysSystemAttributeMappingDto passwordAttribute = systemAttributeMappingService.find(attributeMappingFilter, null).getContent().stream().filter(attribute -> {
			return ProvisioningService.PASSWORD_SCHEMA_PROPERTY_NAME.equals(schemaAttributeService.get(attribute.getSchemaAttribute()).getName());
		}).findFirst().orElse(null);
		
		Assert.assertNotNull( passwordAttribute);
		
		SysSystemAttributeMappingDto uidAttribute = systemAttributeMappingService.find(attributeMappingFilter, null).getContent().stream().filter(attribute -> {
			return attribute.isUid();
		}).findFirst().orElse(null);
		
		Assert.assertNotNull(uidAttribute);
		

		uidAttribute.setTransformToResourceScript("if(attributeValue){return \"y\"+ attributeValue;}");
		uidAttribute = systemAttributeMappingService.save(uidAttribute);
		
		SysSystemEntityDto sysEntity = new SysSystemEntityDto("y" + IDENTITY_USERNAME, SystemEntityType.IDENTITY);
		sysEntity.setSystem(clonedSystem.getId());
		sysEntity = systemEntityService.save(sysEntity);
		
		AccAccountDto account = new AccAccountDto();
		account.setSystem(clonedSystem.getId());
		account.setUid("y" + IDENTITY_USERNAME);
		account.setAccountType(AccountType.PERSONAL);
		account.setEntityType(SystemEntityType.IDENTITY);
		account.setSystemEntity(sysEntity.getId());
		account = accountService.save(account);
		
		AccIdentityAccountDto accountIdentity = new AccIdentityAccountDto();
		accountIdentity.setIdentity(identity.getId());
		accountIdentity.setOwnership(true);
		accountIdentity.setAccount(account.getId());
		accountIdentity = identityAccountService.save(accountIdentity);
		
		provisioningService.doProvisioning(account);
		
		TestResource createdAccount = entityManager.find(TestResource.class, accountService.get(accountIdentity.getAccount()).getUid());
		Assert.assertNotNull(createdAccount);
		Assert.assertEquals(identity.getFirstName(), createdAccount.getFirstname());
		String password = createdAccount.getPassword();
		
		AccAccountFilter accountFilter = new AccAccountFilter();
		accountFilter.setIdentityId(identity.getId());
		accountFilter.setOwnership(Boolean.TRUE);
		accountFilter.setSupportChangePassword(Boolean.TRUE);
		// Two accounts supported change password expects
		Assert.assertEquals(2, accountService.find(accountFilter, null).getContent().size());
		
		PasswordChangeDto passwordChange = new PasswordChangeDto();
		passwordChange.setNewPassword(new GuardedString("newPWD12"));
		passwordChange.getAccounts().add(account.getId().toString());
		
		identityService.passwordChange(identity, passwordChange);
		
		createdAccount = entityManager.find(TestResource.class, accountService.get(accountIdentity.getAccount()).getUid());
		Assert.assertNotEquals(password, createdAccount.getPassword());
		
		// After success password change, we delete password attribute.
		systemAttributeMappingService.delete(passwordAttribute);
		
		// One account supported change password expects
		Assert.assertEquals(1, accountService.find(accountFilter, null).getContent().size());
		
		// Change password .. now this doesn't end with exception but with failed state
		passwordChange = new PasswordChangeDto();
		passwordChange.setNewPassword(new GuardedString("newPWDUnsupported"));
		passwordChange.getAccounts().add(account.getId().toString());
		List<OperationResult> results = identityService.passwordChange(identity, passwordChange);

		// check results
		// for idm will be password changed executed with success state for system doesn't
		for (OperationResult result : results) {
			IdmAccountDto idmAccount = (IdmAccountDto) result.getModel().getParameters().get(IdmAccountDto.PARAMETER_NAME);
			String statusEnum = result.getModel().getStatusEnum();
			assertNotNull(statusEnum);
			assertNotNull(idmAccount);
			if (statusEnum.equals(CoreResultCode.PASSWORD_CHANGE_ACCOUNT_SUCCESS.name())) {
				// idm
				assertNull(idmAccount.getSystemId());
				assertNull(idmAccount.getSystemName());
				continue;
			} else if (statusEnum.equals(CoreResultCode.PASSWORD_CHANGE_ACCOUNT_FAILED.name())) {
				assertEquals(clonedSystem.getId(), idmAccount.getSystemId());
				assertEquals(clonedSystem.getName(), idmAccount.getSystemName());
				continue;
			}
			fail("Different result!");
		}
	}
	

	@Test
	public void doIdentityProvisioningZRemoveAccount() {
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);

		// Delete account
		accountService.deleteById(accountIdentityOne.getAccount());
		Assert.assertNull(accountService.get(accountIdentityOne.getAccount()));
	}

	@Test
	public void doIdentityProvisioningExtendedAttribute() {
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);

		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);

		// We will use firstName attribute (password attribute is not returned
		// by default)
		SysSystemAttributeMappingFilter filterSchemaAttr = new SysSystemAttributeMappingFilter();
		filterSchemaAttr.setIdmPropertyName("firstName");
		filterSchemaAttr.setSystemId(accountService.get(accountIdentityOne.getAccount()).getSystem());
		SysSystemAttributeMappingDto attributeHandling = systemAttributeMappingService.find(filterSchemaAttr, null)
				.getContent().get(0);
		// Set attribute to extended attribute and modify idmPropety to
		// extPassword
		attributeHandling.setIdmPropertyName(IDENTITY_EXT_PASSWORD);
		attributeHandling.setExtendedAttribute(true);
		attributeHandling.setConfidentialAttribute(true);
		attributeHandling.setEntityAttribute(false);
		attributeHandling.setTransformToResourceScript("return attributeValue");
		// Form attribute definition will be created during save attribute
		// handling
		attributeHandling = systemAttributeMappingService.save(attributeHandling);

		// Create extended attribute value for password
		IdmFormDefinitionDto formDefinition = formService.getDefinition(IdmIdentity.class);
		List<IdmFormValueDto> values = new ArrayList<>();
		IdmFormValueDto phoneValue = new IdmFormValueDto();
		phoneValue.setFormAttribute(formDefinition.getMappedAttributeByCode(IDENTITY_EXT_PASSWORD).getId());
		phoneValue.setPersistentType(PersistentType.SHORTTEXT);
		phoneValue.setValue(IDENTITY_PASSWORD_THREE);
		values.add(phoneValue);
		formService.saveValues(identity, formDefinition, values);

		// save account
		provisioningService.doProvisioning(identity);
		TestResource resourceAccoutn = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertEquals(IDENTITY_PASSWORD_THREE, resourceAccoutn.getFirstname());
	}

	@Test
	public void doIdentityProvisioningStrategyCreate() {
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);

		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);

		// Default email strategy is CREATE, we check value
		TestResource resourceAccoutn = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertEquals(EMAIL_ONE, resourceAccoutn.getEmail());

		SysSystemAttributeMappingFilter filterSchemaAttr = new SysSystemAttributeMappingFilter();
		filterSchemaAttr.setIdmPropertyName("email");
		filterSchemaAttr.setSystemId(accountService.get(accountIdentityOne.getAccount()).getSystem());
		SysSystemAttributeMappingDto attributeHandling = systemAttributeMappingService.find(filterSchemaAttr, null)
				.getContent().get(0);

		attributeHandling.setEntityAttribute(true);
		attributeHandling.setStrategyType(AttributeMappingStrategyType.CREATE);
		attributeHandling.setTransformToResourceScript("return \"" + EMAIL_TWO + "\";");
		systemAttributeMappingService.save(attributeHandling);

		// Do provisioning
		provisioningService.doProvisioning(identity);
		// Email strategy is CREATE ... email in account must not have new value 
		resourceAccoutn = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertNotEquals(EMAIL_TWO, resourceAccoutn.getEmail());
	}
	
	@Test
	public void doIdentityProvisioningStrategyCreateNotReturnByDefault() {
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);

		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);

		// Default email strategy is CREATE, we check value
		TestResource resourceAccoutn = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertEquals(EMAIL_ONE, resourceAccoutn.getEmail());

		SysSystemAttributeMappingFilter filterSchemaAttr = new SysSystemAttributeMappingFilter();
		filterSchemaAttr.setIdmPropertyName("email");
		filterSchemaAttr.setSystemId(accountService.get(accountIdentityOne.getAccount()).getSystem());
		SysSystemAttributeMappingDto attributeHandling = systemAttributeMappingService.find(filterSchemaAttr, null)
				.getContent().get(0);

		attributeHandling.setEntityAttribute(true);
		attributeHandling.setStrategyType(AttributeMappingStrategyType.CREATE);
		attributeHandling.setTransformToResourceScript("return \"" + EMAIL_TWO + "\";");
		
		// Set schema attribute as returnByDefault = false.
		UUID schemaAttributeId = attributeHandling.getSchemaAttribute();
		SysSchemaAttributeDto schemaAttributeDto = schemaAttributeService.get(schemaAttributeId);
		schemaAttributeDto.setReturnedByDefault(false);
		schemaAttributeDto = schemaAttributeService.save(schemaAttributeDto);
		
		systemAttributeMappingService.save(attributeHandling);

		// Do provisioning
		provisioningService.doProvisioning(identity);
		// Email strategy is CREATE ... email in account must not have new value 
		resourceAccoutn = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertNotEquals(EMAIL_TWO, resourceAccoutn.getEmail());
		
		// Set back schema attribute as returnByDefault = true.
		schemaAttributeDto.setReturnedByDefault(true);
		schemaAttributeDto = schemaAttributeService.save(schemaAttributeDto);
	}
	
	@Test
	public void doIdentityProvisioningStrategyIfNull() {
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);

		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);

		// Default email strategy is CREATE, we check value
		TestResource resourceAccoutn = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertEquals(EMAIL_ONE, resourceAccoutn.getEmail());

		SysSystemAttributeMappingFilter filterSchemaAttr = new SysSystemAttributeMappingFilter();
		filterSchemaAttr.setIdmPropertyName("email");
		filterSchemaAttr.setSystemId(accountService.get(accountIdentityOne.getAccount()).getSystem());
		SysSystemAttributeMappingDto attributeHandling = systemAttributeMappingService.find(filterSchemaAttr, null)
				.getContent().get(0);

		attributeHandling.setEntityAttribute(true);
		attributeHandling.setStrategyType(AttributeMappingStrategyType.WRITE_IF_NULL);
		attributeHandling.setTransformToResourceScript("return \"" + EMAIL_TWO + "\";");
		systemAttributeMappingService.save(attributeHandling);

		// Do provisioning
		provisioningService.doProvisioning(identity);
		// Email strategy is WRITE_IF_NULL ... email in account must not have new value 
		resourceAccoutn = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertNotEquals(EMAIL_TWO, resourceAccoutn.getEmail());
		
		attributeHandling.setEntityAttribute(true);
		attributeHandling.setStrategyType(AttributeMappingStrategyType.SET);
		attributeHandling.setTransformToResourceScript("return \"" + EMAIL_TWO + "\";");
		systemAttributeMappingService.save(attributeHandling);
		
		// Do provisioning
		provisioningService.doProvisioning(identity);
		// Email strategy is SET ... email in account must have new value 
		resourceAccoutn = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertEquals(EMAIL_TWO, resourceAccoutn.getEmail());
	}

	
	@Test
	public void doIdentityProvisioningStrategySendOnlyIfNotNull() {
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);

		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);

		// Init value check
		TestResource resourceAccoutn = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertEquals(EMAIL_TWO, resourceAccoutn.getEmail());

		SysSystemAttributeMappingFilter filterSchemaAttr = new SysSystemAttributeMappingFilter();
		filterSchemaAttr.setIdmPropertyName("email");
		filterSchemaAttr.setSystemId(accountService.get(accountIdentityOne.getAccount()).getSystem());
		SysSystemAttributeMappingDto attributeHandling = systemAttributeMappingService.find(filterSchemaAttr, null)
				.getContent().get(0);

		attributeHandling.setEntityAttribute(true);
		attributeHandling.setStrategyType(AttributeMappingStrategyType.SET);
		attributeHandling.setSendOnlyIfNotNull(true);
		attributeHandling.setTransformToResourceScript("return null");
		systemAttributeMappingService.save(attributeHandling);

		// Do provisioning
		provisioningService.doProvisioning(identity);
		// Email strategy is SendOnlyIfNotNull ... email in account must have old value
		resourceAccoutn = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertEquals(EMAIL_TWO, resourceAccoutn.getEmail());
		
		attributeHandling.setStrategyType(AttributeMappingStrategyType.SET);
		attributeHandling.setTransformToResourceScript("return \"\";");
		systemAttributeMappingService.save(attributeHandling);
		
		// Do provisioning
		provisioningService.doProvisioning(identity);
		// Email strategy is SendOnlyIfNotNull (value is empty string) ... email in account must have old value
		resourceAccoutn = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertEquals(EMAIL_TWO, resourceAccoutn.getEmail());
		
		attributeHandling.setStrategyType(AttributeMappingStrategyType.SET);
		attributeHandling.setTransformToResourceScript("return \"" + EMAIL_ONE + "\";");
		systemAttributeMappingService.save(attributeHandling);
		
		// Do provisioning
		provisioningService.doProvisioning(identity);
		// Email strategy is SendOnlyIfNotNull  (value is not null and not empty)... email in account must have new value 
		resourceAccoutn = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());
		Assert.assertEquals(EMAIL_ONE, resourceAccoutn.getEmail());
	}
	
	
	@Test()
	public void doIdentityProvisioningStrategyMerge() {
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);

		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);

		SysSystemAttributeMappingFilter filterSchemaAttr = new SysSystemAttributeMappingFilter();
		filterSchemaAttr.setIdmPropertyName("email");
		filterSchemaAttr.setSystemId(accountService.get(accountIdentityOne.getAccount()).getSystem());
		SysSystemAttributeMappingDto attributeHandling = systemAttributeMappingService.find(filterSchemaAttr, null)
				.getContent().get(0);

		attributeHandling.setEntityAttribute(true);
		attributeHandling.setStrategyType(AttributeMappingStrategyType.MERGE);
		SysSchemaAttributeDto schemaAttributeDto = schemaAttributeService.get(attributeHandling.getSchemaAttribute());
		schemaAttributeDto.setMultivalued(true);
		schemaAttributeService.save(schemaAttributeDto);
		systemAttributeMappingService.save(attributeHandling);

		// Do provisioning
		provisioningService.doProvisioning(identity);
	}
	
	// Expected PROVISIONING_MERGE_ATTRIBUTE_IS_NOT_MULTIVALUE
	@Test(expected = ProvisioningException.class)
	public void doIdentityProvisioningStrategyMergeException() {
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);

		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);

		SysSystemAttributeMappingFilter filterSchemaAttr = new SysSystemAttributeMappingFilter();
		filterSchemaAttr.setIdmPropertyName("email");
		filterSchemaAttr.setSystemId(accountService.get(accountIdentityOne.getAccount()).getSystem());
		SysSystemAttributeMappingDto attributeHandling = systemAttributeMappingService.find(filterSchemaAttr, null)
				.getContent().get(0);

		attributeHandling.setEntityAttribute(true);
		attributeHandling.setStrategyType(AttributeMappingStrategyType.MERGE);
		SysSchemaAttributeDto schemaAttributeDto = schemaAttributeService.get(attributeHandling.getSchemaAttribute());
		schemaAttributeDto.setMultivalued(false);
		schemaAttributeService.save(schemaAttributeDto);
		systemAttributeMappingService.save(attributeHandling);

		// Do provisioning
		provisioningService.doProvisioning(identity);
	}

	
	@Test
	public void doIdentityProvisioningAndPasswordCheck() {
		IdmIdentityDto existIdentity = identityService.getByUsername(IDENTITY_USERNAME);
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(existIdentity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);

		TestResource createdAccount = entityManager.find(TestResource.class, accountService.get(accountIdentityOne.getAccount()).getUid());

		Assert.assertNotNull(createdAccount);
		// password must be exactly two 'a' characters, see setting for password
		// policy
		Assert.assertEquals("aa", createdAccount.getPassword());
	}

	@Test
	@Transactional
	public void compileAttributesDefaultTest() {
		List<AttributeMapping> defaultAttributes = new ArrayList<>();
		List<SysRoleSystemAttributeDto> overloadingAttributes = new ArrayList<>();

		AttributeMapping defOne = new SysRoleSystemAttributeDto();
		defOne.setEntityAttribute(true);
		defOne.setStrategyType(AttributeMappingStrategyType.SET);
		defOne.setIdmPropertyName("one");
		defOne.setName("defOne");
		defOne.setDisabledAttribute(true);
		defaultAttributes.add(defOne);

		AttributeMapping defTwo = new SysRoleSystemAttributeDto();
		defTwo.setEntityAttribute(true);
		defTwo.setStrategyType(AttributeMappingStrategyType.SET);
		defTwo.setIdmPropertyName("two");
		defTwo.setName("defTwo");
		defaultAttributes.add(defTwo);

		List<AttributeMapping> compilledAttributes = provisioningService.compileAttributes(defaultAttributes,
				overloadingAttributes, SystemEntityType.IDENTITY);
		Assert.assertEquals(1, compilledAttributes.size());
		Assert.assertEquals("defTwo", compilledAttributes.get(0).getName());
	}

	@Test
	@Transactional
	public void compileAttributesOverrloadedDisabledTest() {
		List<AttributeMapping> defaultAttributes = new ArrayList<>();
		List<SysRoleSystemAttributeDto> overloadingAttributes = new ArrayList<>();

		initDataSystem();
		
		SysSchemaAttributeDto attOne = new SysSchemaAttributeDto();
		attOne.setName("attOne");
		attOne.setObjectClass(objectClasses.get(0).getId());
		attOne.setClassType(String.class.getName());
		attOne = schemaAttributeService.save(attOne);
		
		SysSchemaAttributeDto attTwo = new SysSchemaAttributeDto();
		attTwo.setName("attTwo");
		attTwo.setObjectClass(objectClasses.get(0).getId());
		attTwo.setClassType(String.class.getName());
		attTwo = schemaAttributeService.save(attTwo);

		
		SysSystemAttributeMappingDto defOne = new SysSystemAttributeMappingDto();
		defOne.setEntityAttribute(true);
		defOne.setIdmPropertyName("one");
		defOne.setName("defOne");
		defOne.setDisabledAttribute(true);
		defOne.setSchemaAttribute(attOne.getId());
		defOne.setSystemMapping(systemMapping.getId());
		defOne = systemAttributeMappingService.save(defOne);
		defaultAttributes.add(defOne);

		SysSystemAttributeMappingDto defTwo = new SysSystemAttributeMappingDto();
		defTwo.setEntityAttribute(true);
		defTwo.setIdmPropertyName("two");
		defTwo.setName("defTwo");
		defTwo.setSchemaAttribute(attTwo.getId());
		defTwo.setSystemMapping(systemMapping.getId());
		defTwo = systemAttributeMappingService.save(defTwo);
		defaultAttributes.add(defTwo);

		IdmRoleDto roleOne = new IdmRoleDto();
		roleOne.setCode("roleOne");
		roleOne.setPriority(100);
		roleOne = roleService.save(roleOne);

		SysRoleSystemDto roleSystem = new SysRoleSystemDto();
		roleSystem.setRole(roleOne.getId());
		roleSystem.setSystem(system.getId());
		roleSystem.setSystemMapping(systemMapping.getId());
		roleSystem = roleSystemService.save(roleSystem);

		SysRoleSystemAttributeDto overloadedOne = new SysRoleSystemAttributeDto();
		overloadedOne.setSystemAttributeMapping(defOne.getId());
		overloadedOne.setEntityAttribute(true);
		overloadedOne.setIdmPropertyName("one");
		overloadedOne.setName("defOneOverloaded");
		overloadedOne.setDisabledDefaultAttribute(false);
		overloadedOne.setRoleSystem(roleSystem.getId()); 
		overloadedOne = roleSystemAttributeService.save(overloadedOne);
		overloadingAttributes.add(overloadedOne);

		List<AttributeMapping> compilledAttributes = provisioningService.compileAttributes(defaultAttributes,
				overloadingAttributes, SystemEntityType.IDENTITY);
		Assert.assertEquals(2, compilledAttributes.size());
		Assert.assertTrue(compilledAttributes.stream().filter(attribute -> {
			return "defOneOverloaded".equals(attribute.getName());
		}).findFirst().isPresent());
	}

	@Test
	@Transactional
	public void compileAttributesOverrloadedSamePriorityTest() {
		List<SysRoleSystemAttributeDto> overloadingAttributes = new ArrayList<>();
		List<AttributeMapping> defaultAttributes = new ArrayList<>();

		initDataSystem();
		
		initOverloadedAttributes(overloadingAttributes, defaultAttributes);

		List<AttributeMapping> compilledAttributes = provisioningService.compileAttributes(defaultAttributes,
				overloadingAttributes, SystemEntityType.IDENTITY);
		Assert.assertEquals(2, compilledAttributes.size());
		Assert.assertTrue(compilledAttributes.stream().filter(attribute -> {
			return "defOneOverloadedRoleTwo".equals(attribute.getName());
		}).findFirst().isPresent());

		// set name role One to zroleOne
		SysRoleSystemDto roleSystem = roleSystemService.get(overloadingAttributes.get(0).getRoleSystem());
		
		IdmRoleDto roleDto = roleService.get(roleSystem.getRole());
		roleDto.setCode("zroleOne");
		roleDto = roleService.save(roleDto);
		
		roleSystem = roleSystemService.save(roleSystem);

		List<SysRoleSystemAttributeDto> freshOverloadingAttributes = new ArrayList<>();
		overloadingAttributes.forEach(attributeOver -> {
			freshOverloadingAttributes.add(roleSystemAttributeService.get(attributeOver.getId()));
		});
		compilledAttributes = provisioningService.compileAttributes(defaultAttributes, freshOverloadingAttributes, SystemEntityType.IDENTITY);
		Assert.assertEquals(2, compilledAttributes.size());
		Assert.assertTrue(compilledAttributes.stream().filter(attribute -> {
			return "defOneOverloaded".equals(attribute.getName());
		}).findFirst().isPresent());

		// We set role mapping attribute to disabled, then must have higher
		// "priority", then role mapping one
		// and must missing in result
		SysRoleSystemAttributeDto attribute = overloadingAttributes.get(1);
		attribute.setDisabledDefaultAttribute(true);
		attribute = roleSystemAttributeService.save(attribute);
		overloadingAttributes.set(1, attribute);
		
		List<SysRoleSystemAttributeDto> freshOverloadingAttributesTwo = new ArrayList<>();
		overloadingAttributes.forEach(attributeOver -> {
			freshOverloadingAttributes.add(roleSystemAttributeService.get(attributeOver.getId()));
		});
		compilledAttributes = provisioningService.compileAttributes(defaultAttributes, freshOverloadingAttributesTwo, SystemEntityType.IDENTITY);
		Assert.assertEquals(1, compilledAttributes.size());
	}

	@Test
	@Transactional
	public void compileAttributesOverrloadedDiffPriorityTest() {
		List<SysRoleSystemAttributeDto> overloadingAttributes = new ArrayList<>();
		List<AttributeMapping> defaultAttributes = new ArrayList<>();

		initDataSystem();
		initOverloadedAttributes(overloadingAttributes, defaultAttributes);

		// roleOne
		SysRoleSystemDto roleSystem1 = roleSystemService.get(overloadingAttributes.get(0).getRoleSystem());
		
		IdmRoleDto roleDto = roleService.get(roleSystem1.getRole());
		roleDto.setPriority(200);
		roleDto = roleService.save(roleDto);
		
		// roleTwo
		SysRoleSystemDto roleSystem2 = roleSystemService.get(overloadingAttributes.get(1).getRoleSystem());
		
		roleDto = roleService.get(roleSystem2.getRole());
		roleDto.setPriority(100);
		roleDto = roleService.save(roleDto);
		
		List<SysRoleSystemAttributeDto> freshOverloadingAttributes = new ArrayList<>();
		overloadingAttributes.forEach(attribute -> {
			freshOverloadingAttributes.add(roleSystemAttributeService.get(attribute.getId()));
		});
		
		List<AttributeMapping> compilledAttributes = provisioningService.compileAttributes(defaultAttributes,
				freshOverloadingAttributes, SystemEntityType.IDENTITY);
		Assert.assertEquals(2, compilledAttributes.size());
		Assert.assertTrue(compilledAttributes.stream().filter(attribute -> {
			return "defOneOverloaded".equals(attribute.getName());
		}).findFirst().isPresent());
	}

	@Test
	@Transactional
	public void compileAttributesOverrloadedStrategyMergeTest() {
		List<SysRoleSystemAttributeDto> overloadingAttributes = new ArrayList<>();
		List<AttributeMapping> defaultAttributes = new ArrayList<>();

		initDataSystem();
		
		initOverloadedAttributes(overloadingAttributes, defaultAttributes);

		// roleOne
		SysRoleSystemDto roleSystem1 = roleSystemService.get(overloadingAttributes.get(0).getRoleSystem());
		IdmRoleDto roleDto = roleService.get(roleSystem1.getRole());
		roleDto.setPriority(200);
		roleDto = roleService.save(roleDto);
		
		// roleTwo
		SysRoleSystemDto roleSystem2 = roleSystemService.get(overloadingAttributes.get(1).getRoleSystem());
		roleDto = roleService.get(roleSystem2.getRole());
		roleDto.setPriority(200);
		roleDto = roleService.save(roleDto);

		// overloadedRoleOne
		SysRoleSystemAttributeDto attribute1 = overloadingAttributes.get(0);
		attribute1.setStrategyType(AttributeMappingStrategyType.MERGE);
		attribute1 = roleSystemAttributeService.save(attribute1);
		overloadingAttributes.set(0, attribute1);
		
		// overloadedRoleTwo
		SysRoleSystemAttributeDto attribute2 = overloadingAttributes.get(1);
		attribute2.setStrategyType(AttributeMappingStrategyType.MERGE);
		attribute2 = roleSystemAttributeService.save(attribute2);
		overloadingAttributes.set(1, attribute2);

		List<AttributeMapping> compilledAttributes = provisioningService.compileAttributes(defaultAttributes,
				overloadingAttributes, SystemEntityType.IDENTITY);
		Assert.assertEquals(3, compilledAttributes.size());
		Assert.assertTrue(compilledAttributes.stream().filter(attribute -> {
			return "defOneOverloadedRoleTwo".equals(attribute.getName());
		}).findFirst().isPresent());
	}

	@Test
	@Transactional
	public void compileAttributesOverrloadedStrategyMergeAuthoTest() {
		List<SysRoleSystemAttributeDto> overloadingAttributes = new ArrayList<>();
		List<AttributeMapping> defaultAttributes = new ArrayList<>();

		initDataSystem();
		
		initOverloadedAttributes(overloadingAttributes, defaultAttributes);

		// roleOne
		SysRoleSystemDto roleSystem1 = roleSystemService.get(overloadingAttributes.get(0).getRoleSystem());
		IdmRoleDto roleDto = roleService.get(roleSystem1.getRole());
		roleDto.setPriority(200);
		roleDto = roleService.save(roleDto);
		
		// roleTwo
		SysRoleSystemDto roleSystem2 = roleSystemService.get(overloadingAttributes.get(1).getRoleSystem());
		roleDto = roleService.get(roleSystem2.getRole());
		roleDto.setPriority(100);
		roleDto = roleService.save(roleDto);

		// overloadedRoleOne
		SysRoleSystemAttributeDto attribute1 = overloadingAttributes.get(0);
		attribute1.setStrategyType(AttributeMappingStrategyType.AUTHORITATIVE_MERGE);
		attribute1 = roleSystemAttributeService.save(attribute1);
		overloadingAttributes.set(0, attribute1);
		// overloadedRoleTwo
		SysRoleSystemAttributeDto attribute2 = overloadingAttributes.get(1);
		attribute2.setStrategyType(AttributeMappingStrategyType.AUTHORITATIVE_MERGE);
		attribute2 = roleSystemAttributeService.save(attribute2);
		overloadingAttributes.set(1, attribute2);

		List<AttributeMapping> compilledAttributes = provisioningService.compileAttributes(defaultAttributes,
				overloadingAttributes, SystemEntityType.IDENTITY);
		Assert.assertEquals(3, compilledAttributes.size());
		Assert.assertTrue(compilledAttributes.stream().filter(attribute -> {
			return "defOneOverloadedRoleTwo".equals(attribute.getName());
		}).findFirst().isPresent());
	}

	@Test
	public void compileAttributesOverrloadedStrategyMergeWithDuplTest() {
		String sameValue = "sameValue-" + System.currentTimeMillis();
		String eavAttribute = helper.getSchemaColumnName("EAV_ATTRIBUTE");

		IdmRoleDto role1 = helper.createRole();
		IdmRoleDto role2 = helper.createRole();
		IdmIdentityDto identity = helper.createIdentity();
		helper.createIdentityRole(identity, role1);
		helper.createIdentityRole(identity, role2);

		SysSystemDto system = helper.createTestResourceSystem(true);
		systemService.generateSchema(system);

		SysSchemaAttributeFilter schemaAttFilter = new SysSchemaAttributeFilter();
		schemaAttFilter.setSystemId(system.getId());
		schemaAttFilter.setName(eavAttribute);
		List<SysSchemaAttributeDto> schemaAttrs = schemaAttributeService.find(schemaAttFilter, null).getContent();
		assertEquals(1, schemaAttrs.size());
		SysSchemaAttributeDto schemaAttributeDto = schemaAttrs.get(0);
		schemaAttributeDto.setMultivalued(true);
		schemaAttributeDto = schemaAttributeService.save(schemaAttributeDto);

		SysSystemMappingDto systemMapping = helper.getDefaultMapping(system);

		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());
		schemaAttributeFilter.setName(eavAttribute);
		List<SysSchemaAttributeDto> atts = schemaAttributeService.find(schemaAttributeFilter, null).getContent();
		assertEquals(1, atts.size());
		SysSchemaAttributeDto sysSchemaAttributeEav = atts.get(0);

		// create eav attribute with merge
		SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
		attributeMapping.setExtendedAttribute(true);
		attributeMapping.setName(eavAttribute);
		attributeMapping.setIdmPropertyName(eavAttribute);
		attributeMapping.setStrategyType(AttributeMappingStrategyType.MERGE);
		attributeMapping.setSchemaAttribute(sysSchemaAttributeEav.getId());
		attributeMapping.setSystemMapping(systemMapping.getId());
		attributeMapping = systemAttributeMappingService.save(attributeMapping);

		SysRoleSystemDto roleSystem1 = helper.createRoleSystem(role1, system);

		SysRoleSystemDto roleSystem2 = helper.createRoleSystem(role2, system);

		SysRoleSystemAttributeDto overloadedRoleOne = new SysRoleSystemAttributeDto();
		overloadedRoleOne.setSystemAttributeMapping(attributeMapping.getId());
		overloadedRoleOne.setEntityAttribute(false);
		overloadedRoleOne.setExtendedAttribute(true);
		overloadedRoleOne.setStrategyType(AttributeMappingStrategyType.MERGE);
		overloadedRoleOne.setName(attributeMapping.getName());
		overloadedRoleOne.setDisabledDefaultAttribute(false);
		overloadedRoleOne.setIdmPropertyName(eavAttribute);
		overloadedRoleOne.setRoleSystem(roleSystem1.getId());
		overloadedRoleOne.setTransformToResourceScript("return '" + sameValue + "';");
		overloadedRoleOne = roleSystemAttributeService.save(overloadedRoleOne);

		SysRoleSystemAttributeDto overloadedRoleTwo = new SysRoleSystemAttributeDto();
		overloadedRoleTwo.setSystemAttributeMapping(attributeMapping.getId());
		overloadedRoleTwo.setEntityAttribute(false);
		overloadedRoleTwo.setExtendedAttribute(true);
		overloadedRoleTwo.setStrategyType(AttributeMappingStrategyType.MERGE);
		overloadedRoleTwo.setName(attributeMapping.getName());
		overloadedRoleTwo.setDisabledDefaultAttribute(false);
		overloadedRoleTwo.setIdmPropertyName(eavAttribute);
		overloadedRoleTwo.setRoleSystem(roleSystem2.getId());
		overloadedRoleTwo.setTransformToResourceScript("return '" + sameValue + "';");
		overloadedRoleTwo = roleSystemAttributeService.save(overloadedRoleTwo);

		provisioningService.accountManagement(identity);
		provisioningService.doProvisioning(identity);

		List<AccAccountDto> accounts = accountService.getAccounts(system.getId(), identity.getId());
		assertEquals(1, accounts.size());

		SysProvisioningOperationFilter filter = new SysProvisioningOperationFilter();
		filter.setSystemId(system.getId());

		List<SysProvisioningArchiveDto> archives = porvisioningArchiveService.find(filter, null).getContent();

		assertEquals(1, archives.size());
		SysProvisioningArchiveDto archive = archives.get(0);

		assertEquals(OperationState.EXECUTED, archive.getResultState());
		ProvisioningContext provisioningContext = archive.getProvisioningContext();

		Object values = null;
		for (ProvisioningAttributeDto key : provisioningContext.getAccountObject().keySet()) {
			if (key.getSchemaAttributeName().equals(eavAttribute)) {
				values = provisioningContext.getAccountObject().get(key);
				break;
			}
		}
		assertNotNull(values);

		if (values instanceof ArrayList<?>) {
			ArrayList<?> colleaction = (ArrayList<?>) values;
			assertEquals(1, colleaction.size());
			Object object = colleaction.get(0);
			assertEquals(sameValue, object);
		} else {
			fail();
		}
	}

	@Test
	@Transactional
	public void compileAttributesOverrloadedStrategyMergeAuthoDisableTest() {
		List<SysRoleSystemAttributeDto> overloadingAttributes = new ArrayList<>();
		List<AttributeMapping> defaultAttributes = new ArrayList<>();

		initDataSystem();
		
		initOverloadedAttributes(overloadingAttributes, defaultAttributes);

		// roleOne
		SysRoleSystemDto roleSystem1 = roleSystemService.get(overloadingAttributes.get(0).getRoleSystem());
		IdmRoleDto roleDto = roleService.get(roleSystem1.getRole());
		roleDto.setPriority(200);
		roleDto = roleService.save(roleDto);
		
		// roleTwo
		SysRoleSystemDto roleSystem2 = roleSystemService.get(overloadingAttributes.get(1).getRoleSystem());
		roleDto = roleService.get(roleSystem2.getRole());
		roleDto.setPriority(500);
		roleDto = roleService.save(roleDto);

		// overloadedRoleOne
		SysRoleSystemAttributeDto attribute1 = overloadingAttributes.get(0);
		attribute1.setStrategyType(AttributeMappingStrategyType.AUTHORITATIVE_MERGE);
		attribute1 = roleSystemAttributeService.save(attribute1);
		overloadingAttributes.set(0, attribute1);
		// overloadedRoleTwo
		SysRoleSystemAttributeDto attribute2 = overloadingAttributes.get(1);
		attribute2.setStrategyType(AttributeMappingStrategyType.AUTHORITATIVE_MERGE);
		attribute2.setDisabledDefaultAttribute(true);
		attribute2 = roleSystemAttributeService.save(attribute2);
		overloadingAttributes.set(1, attribute2);

		List<AttributeMapping> compilledAttributes = provisioningService.compileAttributes(defaultAttributes,
				overloadingAttributes, SystemEntityType.IDENTITY);
		Assert.assertEquals(2, compilledAttributes.size());
		Assert.assertTrue(compilledAttributes.stream().filter(attribute -> {
			return "defOneOverloaded".equals(attribute.getName());
		}).findFirst().isPresent());
	}

	@Test(expected = ProvisioningException.class)
	@Transactional
	public void compileAttributesOverrloadedConflictStrategies() {
		List<SysRoleSystemAttributeDto> overloadingAttributes = new ArrayList<>();
		List<AttributeMapping> defaultAttributes = new ArrayList<>();

		initDataSystem();
		initOverloadedAttributes(overloadingAttributes, defaultAttributes);

		// roleOne
		SysRoleSystemDto roleSystem1 = roleSystemService.get(overloadingAttributes.get(0).getRoleSystem());
		IdmRoleDto roleDto = roleService.get(roleSystem1.getRole());
		roleDto.setPriority(200);
		roleDto = roleService.save(roleDto);
		
		// roleTwo
		SysRoleSystemDto roleSystem2 = roleSystemService.get(overloadingAttributes.get(1).getRoleSystem());
		roleDto = roleService.get(roleSystem2.getRole());
		roleDto.setPriority(500);
		roleDto = roleService.save(roleDto);
		
		// overloadedRoleOne
		SysRoleSystemAttributeDto roleSystemAttribute1 = overloadingAttributes.get(0);
		roleSystemAttribute1.setStrategyType(AttributeMappingStrategyType.SET);
		roleSystemAttribute1 = roleSystemAttributeService.save(roleSystemAttribute1);
		overloadingAttributes.set(0, roleSystemAttribute1);
		
		// overloadedRoleTwo
		SysRoleSystemAttributeDto roleSystemAttribute2 = overloadingAttributes.get(1);
		roleSystemAttribute2.setStrategyType(AttributeMappingStrategyType.AUTHORITATIVE_MERGE);
		roleSystemAttribute2 = roleSystemAttributeService.save(roleSystemAttribute2);
		overloadingAttributes.set(1, roleSystemAttribute2);

		provisioningService.compileAttributes(defaultAttributes, overloadingAttributes, SystemEntityType.IDENTITY);
	}

	@Test
	@Transactional
	public void compileAttributesOverrloadedStrategyCreateTest() {
		List<SysRoleSystemAttributeDto> overloadingAttributes = new ArrayList<>();
		List<AttributeMapping> defaultAttributes = new ArrayList<>();

		initDataSystem();
		
		initOverloadedAttributes(overloadingAttributes, defaultAttributes);

		// roleOne
		SysRoleSystemDto roleSystem1 = roleSystemService.get(overloadingAttributes.get(0).getRoleSystem());
		IdmRoleDto roleDto = roleService.get(roleSystem1.getRole());
		roleDto.setPriority(200);
		roleDto = roleService.save(roleDto);
		
		// roleTwo
		SysRoleSystemDto roleSystem2 = roleSystemService.get(overloadingAttributes.get(1).getRoleSystem());
		roleDto = roleService.get(roleSystem2.getRole());
		roleDto.setPriority(500);
		roleDto = roleService.save(roleDto);
		
		// overloadedRoleOne
		SysRoleSystemAttributeDto attribute1 = overloadingAttributes.get(0);
		attribute1.setStrategyType(AttributeMappingStrategyType.CREATE);
		attribute1 = roleSystemAttributeService.save(attribute1);
		overloadingAttributes.set(0, attribute1);
		// overloadedRoleTwo
		SysRoleSystemAttributeDto attribute2 = overloadingAttributes.get(1);
		attribute2.setStrategyType(AttributeMappingStrategyType.CREATE);
		attribute2 = roleSystemAttributeService.save(attribute2);
		overloadingAttributes.set(1, attribute2);
		
		List<AttributeMapping> compilledAttributes = provisioningService.compileAttributes(defaultAttributes,
				overloadingAttributes, SystemEntityType.IDENTITY);
		Assert.assertEquals(2, compilledAttributes.size());
		Assert.assertTrue(compilledAttributes.stream().filter(attribute -> {
			return "defOneOverloadedRoleTwo".equals(attribute.getName());
		}).findFirst().isPresent());
	}
	
	@Test
	public void testPasswordChangeWithoutAdditionalAttributes() {
		// prepare account on target system
		SysSystemDto system = helper.createTestResourceSystem(true);
		IdmRoleDto role = helper.createRole();
		helper.createRoleSystem(role, system);
		IdmIdentityDto identity = helper.createIdentity();
		helper.createIdentityRole(identity, role);
		//
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);
		AccAccountDto account = accountService.get(accountIdentityOne.getAccount());
		// Create new password one
		PasswordChangeDto passwordChange = new PasswordChangeDto();
		passwordChange.setAccounts(ImmutableList.of(account.getId().toString()));
		passwordChange.setNewPassword(new GuardedString(IDENTITY_PASSWORD_ONE));
		passwordChange.setIdm(true);
		//
		// Do change of password for selected accounts
		String previousFirtsName = identity.getFirstName();
		String firstNameChange = "firstname-change";
		identity.setFirstName(firstNameChange);
		identityService.passwordChange(identity, passwordChange);
		//
		// Check correct password One
		TestResource resource = helper.findResource(account.getRealUid());
		Assert.assertNotNull(resource);
		Assert.assertEquals(IDENTITY_PASSWORD_ONE, resource.getPassword());
		Assert.assertEquals(previousFirtsName, resource.getFirstname());
	}
	
	@Test
	public void testSendAttributeOnlyOnPasswordChange() {
		Assert.assertTrue(provisioningConfiguration.isSendPasswordAttributesTogether());
		
		SysSystemDto system = helper.createTestResourceSystem(true);
		SysSystemMappingDto systemMapping = helper.getDefaultMapping(system);
		SysSystemAttributeMappingDto firstNameAttribute = systemAttributeMappingService.findBySystemMappingAndName(systemMapping.getId(), 
				helper.getSchemaColumnName(TestHelper.ATTRIBUTE_MAPPING_FIRSTNAME)); 
		firstNameAttribute.setSendOnPasswordChange(Boolean.TRUE);
		systemAttributeMappingService.save(firstNameAttribute);
		
		IdmRoleDto role = helper.createRole();
		helper.createRoleSystem(role, system);
		IdmIdentityDto identity = helper.createIdentity();
		helper.createIdentityRole(identity, role);
		
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);
		AccAccountDto account = accountService.get(accountIdentityOne.getAccount());

		// First name attribute is not set as "send only with password change" -> first name should be changed.
		String firstNameOne = getHelper().createName();
		identity.setFirstName(firstNameOne);
		identity = identityService.save(identity);
		TestResource resource = helper.findResource(account.getRealUid());
		Assert.assertNotNull(resource);
		Assert.assertEquals(firstNameOne, resource.getFirstname());

		// Set first name attribute as "send only with password change".
		firstNameAttribute.setSendOnlyOnPasswordChange(Boolean.TRUE);
		systemAttributeMappingService.save(firstNameAttribute);

		// First name attribute is set as "send only with password change" -> first name should be not changed.
		String firstNameTwo = getHelper().createName();
		identity.setFirstName(firstNameTwo);
		identity = identityService.save(identity);
		resource = helper.findResource(account.getRealUid());
		Assert.assertNotNull(resource);
		Assert.assertNotEquals(identity.getFirstName(), resource.getFirstname());
		Assert.assertEquals(firstNameOne, resource.getFirstname());

		// First name attribute should be send on password change.
		String newPassword = getHelper().createName();
		// Create new password one
		PasswordChangeDto passwordChange = new PasswordChangeDto();
		passwordChange.setAccounts(ImmutableList.of(account.getId().toString()));
		passwordChange.setNewPassword(new GuardedString(newPassword));
		passwordChange.setIdm(true);
		
		// Do change of password for selected accounts
		String firstNameChange =  getHelper().createName();
		identity.setFirstName(firstNameChange);
		identityService.passwordChange(identity, passwordChange);
		
		// Check correct password One
		resource = helper.findResource(account.getRealUid());
		Assert.assertNotNull(resource);
		Assert.assertEquals(newPassword, resource.getPassword());
		Assert.assertEquals(firstNameChange, resource.getFirstname());
	}
	
	@Test
	public void testPasswordChangeWithAdditionalAttributesInOneOperation() {
		Assert.assertTrue(provisioningConfiguration.isSendPasswordAttributesTogether());
		//
		// prepare account on target system
		SysSystemDto system = helper.createTestResourceSystem(true);
		SysSystemMappingDto systemMapping = helper.getDefaultMapping(system);
		SysSystemAttributeMappingDto firtstNameAttribute = systemAttributeMappingService.findBySystemMappingAndName(systemMapping.getId(), 
				helper.getSchemaColumnName(TestHelper.ATTRIBUTE_MAPPING_FIRSTNAME)); 
		firtstNameAttribute.setSendOnPasswordChange(Boolean.TRUE);
		systemAttributeMappingService.save(firtstNameAttribute);
		
		IdmRoleDto role = helper.createRole();
		helper.createRoleSystem(role, system);
		IdmIdentityDto identity = helper.createIdentity();
		helper.createIdentityRole(identity, role);
		//
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);
		AccAccountDto account = accountService.get(accountIdentityOne.getAccount());
		// Create new password one
		PasswordChangeDto passwordChange = new PasswordChangeDto();
		passwordChange.setAccounts(ImmutableList.of(account.getId().toString()));
		passwordChange.setNewPassword(new GuardedString(IDENTITY_PASSWORD_ONE));
		passwordChange.setIdm(true);
		//
		// Do change of password for selected accounts
		String firstNameChange = "firstname-change";
		identity.setFirstName(firstNameChange);
		identityService.passwordChange(identity, passwordChange);
		//
		// Check correct password One
		TestResource resource = helper.findResource(account.getRealUid());
		Assert.assertNotNull(resource);
		Assert.assertEquals(IDENTITY_PASSWORD_ONE, resource.getPassword());
		Assert.assertEquals(firstNameChange, resource.getFirstname());
	}
	
	@Test
	public void testPasswordChangeWithAdditionalAttributesInTwoOperations() {
		configurationService.setBooleanValue(ProvisioningConfiguration.PROPERTY_SEND_PASSWORD_ATTRIBUTES_TOGETHER, false);
		try {
			Assert.assertFalse(provisioningConfiguration.isSendPasswordAttributesTogether());
			// prepare account on target system
			SysSystemDto system = helper.createTestResourceSystem(true);
			SysSystemMappingDto systemMapping = helper.getDefaultMapping(system);
			SysSystemAttributeMappingDto firtstNameAttribute = systemAttributeMappingService.findBySystemMappingAndName(systemMapping.getId(), 
					helper.getSchemaColumnName(TestHelper.ATTRIBUTE_MAPPING_FIRSTNAME)); 
			firtstNameAttribute.setSendOnPasswordChange(Boolean.TRUE);
			systemAttributeMappingService.save(firtstNameAttribute);
			
			IdmRoleDto role = helper.createRole();
			helper.createRoleSystem(role, system);
			IdmIdentityDto identity = helper.createIdentity();
			helper.createIdentityRole(identity, role);
			//
			AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
			filter.setIdentityId(identity.getId());
			AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);
			AccAccountDto account = accountService.get(accountIdentityOne.getAccount());
			// Create new password one
			PasswordChangeDto passwordChange = new PasswordChangeDto();
			passwordChange.setAccounts(ImmutableList.of(account.getId().toString()));
			passwordChange.setNewPassword(new GuardedString(IDENTITY_PASSWORD_ONE));
			passwordChange.setIdm(true);
			//
			// Do change of password for selected accounts
			String firstNameChange = "firstname-change";
			identity.setFirstName(firstNameChange);
			identityService.passwordChange(identity, passwordChange);
			//
			// Check correct password One
			TestResource resource = helper.findResource(account.getRealUid());
			Assert.assertNotNull(resource);
			Assert.assertEquals(IDENTITY_PASSWORD_ONE, resource.getPassword());
			Assert.assertEquals(firstNameChange, resource.getFirstname());
		} finally {
			configurationService.setBooleanValue(ProvisioningConfiguration.PROPERTY_SEND_PASSWORD_ATTRIBUTES_TOGETHER, true);
			Assert.assertTrue(provisioningConfiguration.isSendPasswordAttributesTogether());
		}
	}
	
	@Test
	public void testUpdateWishIfSystemEntityExists() {
		String username = getHelper().createName();
		SysSystemDto system = getHelper().createTestResourceSystem(true);
		//
		// prepare resource
		IdmIdentityDto identity = getHelper().createIdentity(username, (GuardedString) null);
		TestResource resource = new TestResource();
		resource.setName(username);
		resource.setFirstname(identity.getFirstName());
		resource.setLastname(identity.getLastName());
		resource.setStatus("enabled"); // TODO: test constant
		resource = getHelper().saveResource(resource);
		//
		// prepare identity with account on target system with the same system entity as prepared
		IdmRoleDto role = getHelper().createRole();
		getHelper().createRoleSystem(role, system);
		getHelper().createIdentityRole(identity, role);
		//
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);
		AccAccountDto account = accountService.get(accountIdentityOne.getAccount());
		//
		SysSystemEntityDto systemEntity = DtoUtils.getEmbedded(account, AccAccount_.systemEntity);
		//
		resource = helper.findResource(account.getRealUid());
		//
		Assert.assertFalse(systemEntity.isWish());
	}
	
	@Override
	protected TestHelper getHelper() {
		return (TestHelper) super.getHelper();
	}
	
	private void initDataSystem() {

		// create test system
		system = helper.createSystem("test_resource");

		// generate schema for system
		objectClasses = systemService.generateSchema(system);
		
		// Create mapped attributes to schema
		systemMapping = new SysSystemMappingDto();
		systemMapping.setName("default_" + System.currentTimeMillis());
		systemMapping.setEntityType(SystemEntityType.IDENTITY);
		systemMapping.setOperationType(SystemOperationType.PROVISIONING);
		systemMapping.setObjectClass(objectClasses.get(0).getId());
		systemMapping = systemMappingService.save(systemMapping);
	}

	private void initOverloadedAttributes(List<SysRoleSystemAttributeDto> overloadingAttributes,
			List<AttributeMapping> defaultAttributes) {
		
		SysSchemaAttributeDto attOne = new SysSchemaAttributeDto();
		attOne.setName("attOne");
		attOne.setObjectClass(objectClasses.get(0).getId());
		attOne.setClassType(String.class.getName());
		attOne = schemaAttributeService.save(attOne);
		
		SysSchemaAttributeDto attTwo = new SysSchemaAttributeDto();
		attTwo.setName("attTwo");
		attTwo.setObjectClass(objectClasses.get(0).getId());
		attTwo.setClassType(String.class.getName());
		attTwo = schemaAttributeService.save(attTwo);
		
		SysSystemAttributeMappingDto defOne = new SysSystemAttributeMappingDto();
		defOne.setEntityAttribute(true);
		defOne.setIdmPropertyName("one");
		defOne.setName("defOne");
		defOne.setDisabledAttribute(true);
		defOne.setSchemaAttribute(attOne.getId());
		defOne.setSystemMapping(systemMapping.getId());
		defOne = systemAttributeMappingService.save(defOne);
		defaultAttributes.add(defOne);

		SysSystemAttributeMappingDto defTwo = new SysSystemAttributeMappingDto();
		defTwo.setEntityAttribute(true);
		defTwo.setIdmPropertyName("two");
		defTwo.setName("defTwo");
		defTwo.setSchemaAttribute(attTwo.getId());
		defTwo.setSystemMapping(systemMapping.getId());
		defTwo = systemAttributeMappingService.save(defTwo);
		defaultAttributes.add(defTwo);

		IdmRoleDto roleTwo = new IdmRoleDto();
		roleTwo.setCode("roleTwo");
		roleTwo.setPriority(100);
		roleTwo = roleService.save(roleTwo);

		IdmRoleDto roleOne = new IdmRoleDto();
		roleOne.setCode("roleOne");
		roleOne.setPriority(100);
		roleOne = roleService.save(roleOne);
		
		SysRoleSystemDto roleSystemTwo = new SysRoleSystemDto();
		roleSystemTwo.setRole(roleTwo.getId());
		roleSystemTwo.setSystem(system.getId());
		roleSystemTwo.setSystemMapping(systemMapping.getId());
		roleSystemTwo = roleSystemService.save(roleSystemTwo);

		SysRoleSystemDto roleSystemOne = new SysRoleSystemDto();
		roleSystemOne.setRole(roleOne.getId());
		roleSystemOne.setSystem(system.getId());
		roleSystemOne.setSystemMapping(systemMapping.getId());
		roleSystemOne = roleSystemService.save(roleSystemOne);

		SysRoleSystemAttributeDto overloadedRoleOne = new SysRoleSystemAttributeDto();
		overloadedRoleOne.setSystemAttributeMapping(defOne.getId());
		overloadedRoleOne.setEntityAttribute(true);
		overloadedRoleOne.setIdmPropertyName("one");
		overloadedRoleOne.setName("defOneOverloaded");
		overloadedRoleOne.setDisabledDefaultAttribute(false);
		overloadedRoleOne.setRoleSystem(roleSystemOne.getId());
		overloadedRoleOne = roleSystemAttributeService.save(overloadedRoleOne);
		overloadingAttributes.add(overloadedRoleOne);

		SysRoleSystemAttributeDto overloadedRoleTwo = new SysRoleSystemAttributeDto();
		overloadedRoleTwo.setSystemAttributeMapping(defOne.getId());
		overloadedRoleTwo.setEntityAttribute(true);
		overloadedRoleTwo.setIdmPropertyName("one");
		overloadedRoleTwo.setName("defOneOverloadedRoleTwo");
		overloadedRoleTwo.setDisabledDefaultAttribute(false);
		overloadedRoleTwo.setRoleSystem(roleSystemTwo.getId());
		overloadedRoleTwo = roleSystemAttributeService.save(overloadedRoleTwo);
		overloadingAttributes.add(overloadedRoleTwo);

	}

	private void initData() {
		IdmIdentityDto identity;
		AccAccountDto accountOne;
		AccIdentityAccountDto accountIdentityOne;

		// create test system
		SysSystemDto system = helper.createSystem(TestResource.TABLE_NAME, SYSTEM_NAME);

		// set default generate password policy for system
		IdmPasswordPolicyDto passwordPolicy = new IdmPasswordPolicyDto();
		passwordPolicy.setName(PASSWORD_POLICY);
		passwordPolicy.setType(IdmPasswordPolicyType.GENERATE);
		passwordPolicy.setGenerateType(IdmPasswordPolicyGenerateType.RANDOM);
		passwordPolicy.setLowerCharBase("a");
		passwordPolicy.setMinPasswordLength(2);
		passwordPolicy.setMaxPasswordLength(2);
		passwordPolicy.setMinLowerChar(2);
		passwordPolicy = passwordPolicyService.save(passwordPolicy);
		system.setPasswordPolicyGenerate(passwordPolicy.getId());
		system = systemService.save(system);

		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);

		// Create test identity for provisioning test
		identity = new IdmIdentityDto();
		identity.setUsername(IDENTITY_USERNAME);
		identity.setFirstName(IDENTITY_USERNAME);
		identity.setLastName(IDENTITY_USERNAME);
		identity = identityService.save(identity);

		accountOne = new AccAccountDto();
		accountOne.setSystem(system.getId());
		accountOne.setUid("x" + IDENTITY_USERNAME);
		accountOne.setAccountType(AccountType.PERSONAL);
		accountOne.setEntityType(SystemEntityType.IDENTITY);
		accountOne = accountService.save(accountOne);

		accountIdentityOne = new AccIdentityAccountDto();
		accountIdentityOne.setIdentity(identity.getId());
		accountIdentityOne.setOwnership(true);
		accountIdentityOne.setAccount(accountOne.getId());

		accountIdentityOne = identityAccountService.save(accountIdentityOne);
		
		IdmIdentityDto identityTwo = new IdmIdentityDto();
		identityTwo.setUsername(IDENTITY_USERNAME_TWO);
		identityTwo.setFirstName(IDENTITY_USERNAME_TWO);
		identityTwo.setLastName(IDENTITY_USERNAME_TWO);
		identityTwo = identityService.save(identityTwo);

		AccAccountDto accountTwo = new AccAccountDto();
		accountTwo.setSystem(system.getId());
		accountTwo.setUid("x" + IDENTITY_USERNAME_TWO);
		accountTwo.setAccountType(AccountType.PERSONAL);
		accountTwo.setEntityType(SystemEntityType.IDENTITY);
		accountTwo = accountService.save(accountTwo);

		AccIdentityAccountDto accountIdentityTwo = new AccIdentityAccountDto();
		accountIdentityTwo.setIdentity(identityTwo.getId());
		accountIdentityTwo.setOwnership(true);
		accountIdentityTwo.setAccount(accountTwo.getId());

		accountIdentityTwo = identityAccountService.save(accountIdentityTwo);

		SysSystemMappingDto systemMapping = new SysSystemMappingDto();
		systemMapping.setName("default_" + System.currentTimeMillis());
		systemMapping.setEntityType(SystemEntityType.IDENTITY);
		systemMapping.setOperationType(SystemOperationType.PROVISIONING);
		systemMapping.setObjectClass(objectClasses.get(0).getId());
		final SysSystemMappingDto entityHandlingResult = systemEntityHandlingService.save(systemMapping);

		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());

		Page<SysSchemaAttributeDto> schemaAttributesPage = schemaAttributeService.find(schemaAttributeFilter, null);
		schemaAttributesPage.forEach(schemaAttr -> {
			if ("__NAME__".equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(true);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName(IdmIdentity_.username.getName());
				attributeMapping.setTransformToResourceScript("if(attributeValue){return \"x\"+ attributeValue;}");
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				attributeMapping = systemAttributeMappingService.save(attributeMapping);

			} else if ("firstname".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName(IdmIdentity_.firstName.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping
						.setTransformFromResourceScript("if(attributeValue){return attributeValue.substring(1);}");
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				attributeMapping = systemAttributeMappingService.save(attributeMapping);

			} else if ("lastname".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName(IdmIdentity_.lastName.getName());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				attributeMapping = systemAttributeMappingService.save(attributeMapping);

			} else if (IcConnectorFacade.PASSWORD_ATTRIBUTE_NAME.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName("password");
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				attributeMapping.setPasswordAttribute(true); // Since 9.3.0 must be password marked as password attribute
				attributeMapping = systemAttributeMappingService.save(attributeMapping);

			} else if ("email".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName(IdmIdentity_.email.getName());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setStrategyType(AttributeMappingStrategyType.CREATE);
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				attributeMapping.setTransformToResourceScript("return \"" + EMAIL_ONE + "\";");
				attributeMapping = systemAttributeMappingService.save(attributeMapping);

			}
		});
	}
	
	private AccIdentityAccountDto prepareAccount(IdmIdentityDto identity) {
		AccAccountDto accountOne = new AccAccountDto();
		accountOne.setSystem(getSystem().getId());
		accountOne.setUid("x" + identity.getUsername());
		accountOne.setAccountType(AccountType.PERSONAL);
		accountOne.setEntityType(SystemEntityType.IDENTITY);
		accountOne = accountService.save(accountOne);
		//
		AccIdentityAccountDto accountIdentityOne = new AccIdentityAccountDto();
		accountIdentityOne.setIdentity(identity.getId());
		accountIdentityOne.setOwnership(true);
		accountIdentityOne.setAccount(accountOne.getId());
		//
		return identityAccountService.save(accountIdentityOne);
	}
	
	private SysSystemDto getSystem() {
		IdmIdentityDto identity = identityService.getByUsername(IDENTITY_USERNAME);
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setIdentityId(identity.getId());
		AccIdentityAccountDto accountIdentityOne = identityAccountService.find(filter, null).getContent().get(0);
		return systemService.get(accountService.get(accountIdentityOne.getAccount()).getSystem());
	}
	
	/**
	 * 
	 * @return
	 * @deprecated use testHepler after role + dto refactoring
	 */
	@Deprecated
	private IdmIdentityDto createIdentity() {
		IdmIdentityDto identity = new IdmIdentityDto();
		identity.setUsername("test" + "-" + UUID.randomUUID());
		identity.setFirstName("Test");
		identity.setLastName("Identity");
		identity.setPassword(new GuardedString("password"));
		return identityService.save(identity);
	}
	
	/**
	 * 
	 * @return
	 * @deprecated use testHepler after role + dto refactoring
	 */
	@Deprecated
	private IdmTreeNodeDto createTreeNode(IdmTreeNodeDto parent) {
		String name = "test" + "-" + UUID.randomUUID();
		IdmTreeNodeDto node = new IdmTreeNodeDto();
		node.setParent(parent == null ? null : parent.getId());
		node.setCode(name);
		node.setName(name);
		node.setTreeType(treeTypeService.getDefaultTreeType().getId());
		return treeNodeService.save(node);
	}

	/**
	 * 
	 * @return
	 * @deprecated use testHepler after role + dto refactoring
	 */
	@Deprecated
	public IdmIdentityContractDto createIdentityContact(IdmIdentityDto identity, IdmTreeNodeDto position) {
		IdmIdentityContractDto contract = new IdmIdentityContractDto();
		contract.setIdentity(identity.getId());
		contract.setPosition("test" + "-" + UUID.randomUUID());
		contract.setWorkPosition(position == null ? null : position.getId());
		return identityContractService.save(contract);
	}
}
