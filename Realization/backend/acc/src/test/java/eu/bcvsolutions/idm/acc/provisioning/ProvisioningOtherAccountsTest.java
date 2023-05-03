package eu.bcvsolutions.idm.acc.provisioning;

import static eu.bcvsolutions.idm.acc.TestHelper.ATTRIBUTE_MAPPING_EMAIL;
import static eu.bcvsolutions.idm.acc.TestHelper.ATTRIBUTE_MAPPING_FIRSTNAME;
import static eu.bcvsolutions.idm.acc.TestHelper.ATTRIBUTE_MAPPING_LASTNAME;
import static eu.bcvsolutions.idm.acc.TestHelper.ATTRIBUTE_MAPPING_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import eu.bcvsolutions.idm.acc.DefaultAccTestHelper;
import eu.bcvsolutions.idm.acc.domain.AccountType;
import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.ProvisioningEventType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.ProvisioningAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningArchiveDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysProvisioningOperationFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.entity.AccAccount;
import eu.bcvsolutions.idm.acc.entity.AccAccount_;
import eu.bcvsolutions.idm.acc.entity.TestResource;
import eu.bcvsolutions.idm.acc.service.api.AccAccountManagementService;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningArchiveService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.acc.service.impl.IdentitySynchronizationExecutor;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmFormDefinitionFilter;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Test for personal other account use cases
 * @author Roman Kucera
 */
public class ProvisioningOtherAccountsTest extends AbstractIntegrationTest {

	@Autowired
	private DefaultAccTestHelper helper;
	@Autowired
	private AccAccountService accountService;
	@Autowired
	private SysSystemAttributeMappingService systemAttributeMappingService;
	@Autowired
	private SysSystemMappingService systemMappingService;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	@Autowired
	private IdmFormDefinitionService formDefinitionService;
	@Autowired
	private SysSystemService systemService;
	@Autowired
	private ProvisioningService provisioningService;
	@Autowired
	private SysProvisioningArchiveService provisioningArchiveService;
	@Autowired
	private FormService formService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private AccAccountManagementService accountManagementService;

	@Test
	public void createOtherAccountAlone() {
		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = helper.createTestResourceSystem(false, helper.createName());
		helper.createMapping(system, IdentitySynchronizationExecutor.SYSTEM_ENTITY_TYPE, AccountType.PERSONAL_OTHER);
		IdmRoleDto role = helper.createRole();
		helper.createRoleSystem(role, system, AccountType.PERSONAL_OTHER);

		AccAccountFilter accountFilter = new AccAccountFilter();
		accountFilter.setIdentityId(identity.getId());
		long accountsCount = accountService.count(accountFilter);
		assertEquals(0, accountsCount);

		helper.createIdentityRole(identity, role);

		List<AccAccountDto> accounts = accountService.find(accountFilter, null).getContent();
		assertEquals(1, accounts.size());

		AccAccountDto account = accounts.get(0);
		TestResource resource = helper.findResource(account.getUid());
		assertNotNull(resource);

		SysSystemMappingDto accountMapping = DtoUtils.getEmbedded(account, AccAccount_.systemMapping, SysSystemMappingDto.class, null);
		assertNotNull(accountMapping);
		assertEquals(AccountType.PERSONAL_OTHER, accountMapping.getAccountType());

		helper.deleteAllResourceData();
		helper.deleteIdentity(identity.getId());
		helper.deleteRole(role.getId());
		helper.deleteSystem(system.getId());
	}

	@Test
	public void createOtherAccountWithDifferentUid() {
		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = helper.createTestResourceSystem(false, helper.createName());
		helper.createMapping(system, IdentitySynchronizationExecutor.SYSTEM_ENTITY_TYPE, AccountType.PERSONAL_OTHER);
		SysSystemMappingDto mapping = helper.createMapping(system, IdentitySynchronizationExecutor.SYSTEM_ENTITY_TYPE, AccountType.PERSONAL);
		SysSystemAttributeMappingDto uidAttribute = systemAttributeMappingService.findBySystemMappingAndName(mapping.getId(), ATTRIBUTE_MAPPING_NAME);
		uidAttribute.setTransformToResourceScript("return 'test_' + attributeValue");
		systemAttributeMappingService.save(uidAttribute);

		IdmRoleDto role = helper.createRole();
		IdmRoleDto rolePersonal = helper.createRole();
		helper.createRoleSystem(role, system, AccountType.PERSONAL);
		helper.createRoleSystem(rolePersonal, system, AccountType.PERSONAL_OTHER);

		AccAccountFilter accountFilter = new AccAccountFilter();
		accountFilter.setIdentityId(identity.getId());
		long accountsCount = accountService.count(accountFilter);
		assertEquals(0, accountsCount);

		helper.createIdentityRole(identity, role);
		helper.createIdentityRole(identity, rolePersonal);

		List<AccAccountDto> accounts = accountService.find(accountFilter, null).getContent();
		assertEquals(2, accounts.size());

		AccAccountDto account = accounts.stream().filter(accAccountDto -> accAccountDto.getUid().equals(identity.getUsername())).findFirst().orElse(null);
		AccAccountDto account1 = accounts.stream().filter(accAccountDto -> !accAccountDto.getUid().equals(identity.getUsername())).findFirst().orElse(null);
		assertNotNull(account);
		assertNotNull(account1);
		TestResource resource = helper.findResource(account.getUid());
		TestResource resource1 = helper.findResource(account1.getUid());
		assertNotNull(resource);
		assertNotNull(resource1);
		SysSystemMappingDto accountMapping = DtoUtils.getEmbedded(account, AccAccount_.systemMapping, SysSystemMappingDto.class, null);
		SysSystemMappingDto accountMapping1 = DtoUtils.getEmbedded(account1, AccAccount_.systemMapping, SysSystemMappingDto.class, null);
		assertNotNull(accountMapping);
		assertNotNull(accountMapping1);
		assertEquals(AccountType.PERSONAL_OTHER, accountMapping.getAccountType());
		assertEquals(AccountType.PERSONAL, accountMapping1.getAccountType());

		helper.deleteAllResourceData();
		helper.deleteIdentity(identity.getId());
		helper.deleteRole(role.getId());
		helper.deleteRole(rolePersonal.getId());
		helper.deleteSystem(system.getId());
	}

	@Test
	public void createOtherAccountWithDifferentUidThenChangeUidMapping() {
		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = helper.createTestResourceSystem(false, helper.createName());
		helper.createMapping(system, IdentitySynchronizationExecutor.SYSTEM_ENTITY_TYPE, AccountType.PERSONAL_OTHER);
		SysSystemMappingDto mapping = helper.createMapping(system, IdentitySynchronizationExecutor.SYSTEM_ENTITY_TYPE, AccountType.PERSONAL);
		SysSystemAttributeMappingDto uidAttribute = systemAttributeMappingService.findBySystemMappingAndName(mapping.getId(), ATTRIBUTE_MAPPING_NAME);
		uidAttribute.setTransformToResourceScript("return 'test_' + attributeValue");
		systemAttributeMappingService.save(uidAttribute);

		IdmRoleDto role = helper.createRole();
		IdmRoleDto rolePersonal = helper.createRole();
		helper.createRoleSystem(role, system, AccountType.PERSONAL);
		helper.createRoleSystem(rolePersonal, system, AccountType.PERSONAL_OTHER);

		AccAccountFilter accountFilter = new AccAccountFilter();
		accountFilter.setIdentityId(identity.getId());
		long accountsCount = accountService.count(accountFilter);
		assertEquals(0, accountsCount);

		helper.createIdentityRole(identity, role);
		helper.createIdentityRole(identity, rolePersonal);

		List<AccAccountDto> accounts = accountService.find(accountFilter, null).getContent();
		assertEquals(2, accounts.size());

		AccAccountDto account = accounts.stream().filter(accAccountDto -> accAccountDto.getUid().equals(identity.getUsername())).findFirst().orElse(null);
		AccAccountDto account1 = accounts.stream().filter(accAccountDto -> !accAccountDto.getUid().equals(identity.getUsername())).findFirst().orElse(null);
		assertNotNull(account);
		assertNotNull(account1);
		TestResource resource = helper.findResource(account.getUid());
		TestResource resource1 = helper.findResource(account1.getUid());
		assertNotNull(resource);
		assertNotNull(resource1);
		SysSystemMappingDto accountMapping = DtoUtils.getEmbedded(account, AccAccount_.systemMapping, SysSystemMappingDto.class, null);
		SysSystemMappingDto accountMapping1 = DtoUtils.getEmbedded(account1, AccAccount_.systemMapping, SysSystemMappingDto.class, null);
		assertNotNull(accountMapping);
		assertNotNull(accountMapping1);
		assertEquals(AccountType.PERSONAL_OTHER, accountMapping.getAccountType());
		assertEquals(AccountType.PERSONAL, accountMapping1.getAccountType());

		// remove transformation script
		uidAttribute.setTransformToResourceScript(null);
		systemAttributeMappingService.save(uidAttribute);

		// Provisioning will fail, because account with same uid already exists
		assertThrows(ResultCodeException.class, () -> provisioningService.doProvisioning(identity));

		helper.deleteAllResourceData();
		helper.deleteIdentity(identity.getId());
		helper.deleteRole(role.getId());
		helper.deleteRole(rolePersonal.getId());
		helper.deleteSystem(system.getId());
	}

	@Test
	public void createOtherAccountWithSameUid() {
		IdmIdentityDto identity = helper.createIdentity();
		SysSystemDto system = helper.createTestResourceSystem(false, helper.createName());
		helper.createMapping(system, IdentitySynchronizationExecutor.SYSTEM_ENTITY_TYPE, AccountType.PERSONAL_OTHER);
		helper.createMapping(system, IdentitySynchronizationExecutor.SYSTEM_ENTITY_TYPE, AccountType.PERSONAL);

		IdmRoleDto role = helper.createRole();
		IdmRoleDto rolePersonal = helper.createRole();
		helper.createRoleSystem(role, system, AccountType.PERSONAL);
		helper.createRoleSystem(rolePersonal, system, AccountType.PERSONAL_OTHER);

		AccAccountFilter accountFilter = new AccAccountFilter();
		accountFilter.setIdentityId(identity.getId());
		long accountsCount = accountService.count(accountFilter);
		assertEquals(0, accountsCount);

		helper.createIdentityRole(identity, role);
		List<AccAccountDto> accounts = accountService.find(accountFilter, null).getContent();
		assertEquals(1, accounts.size());
		TestResource resource = helper.findResource(accounts.get(0).getUid());
		assertNotNull(resource);
		SysSystemMappingDto accountMapping = DtoUtils.getEmbedded(accounts.get(0), AccAccount_.systemMapping, SysSystemMappingDto.class, null);
		assertNotNull(accountMapping);
		assertEquals(AccountType.PERSONAL, accountMapping.getAccountType());

		assertThrows(ResultCodeException.class, () -> helper.createIdentityRole(identity, rolePersonal));

		helper.deleteAllResourceData();
		helper.deleteIdentity(identity.getId());
		helper.deleteRole(role.getId());
		helper.deleteRole(rolePersonal.getId());
		helper.deleteSystem(system.getId());
	}

	@Test
	public void createOtherAccountWithEAVs() {
		SysSystemDto system = helper.createTestResourceSystem(false, helper.createName());
		SysSystemMappingDto mapping = createMapping(system);

		IdmFormDefinitionFilter formDefinitionFilter = new IdmFormDefinitionFilter();
		formDefinitionFilter.setText(system.getId().toString());
		formDefinitionFilter.setType(AccAccount.class.getName());
		List<IdmFormDefinitionDto> allByType = formDefinitionService.find(formDefinitionFilter, null).getContent();
		// validate by system id and get the proper one
		assertTrue(allByType.size() > 0);

		IdmFormDefinitionDto formDefinitionDto = allByType.get(0);

		formDefinitionDto = formDefinitionService.get(formDefinitionDto.getId());

		List<IdmFormAttributeDto> formAttributes = formDefinitionDto.getFormAttributes().stream()
				.filter(idmFormAttributeDto -> idmFormAttributeDto.getCode().equals(ATTRIBUTE_MAPPING_FIRSTNAME) || idmFormAttributeDto.getCode().equals(ATTRIBUTE_MAPPING_LASTNAME))
				.collect(Collectors.toList());

		// add new eav attributes to mapping
		addAttributesToMapping(formAttributes, system, mapping);

		IdmRoleDto role = helper.createRole();
		helper.createRoleSystem(role, system, AccountType.PERSONAL_OTHER);

		// Identity
		IdmIdentityDto identityDto = helper.createIdentity(helper.createName());
		identityDto.setEmail("someemail@test.cz");
		identityDto = identityService.save(identityDto);

		AccAccountFilter accountFilter = new AccAccountFilter();
		accountFilter.setAccountType(AccountType.PERSONAL_OTHER);
		accountFilter.setIdentityId(identityDto.getId());
		long accountsCount = accountService.count(accountFilter);
		assertEquals(0, accountsCount);

		helper.createIdentityRole(identityDto, role);

		List<AccAccountDto> accounts = accountService.find(accountFilter, null).getContent();
		assertEquals(1, accounts.size());

		AccAccountDto accountFound = accounts.get(0);
		assertEquals(system.getId(), accountFound.getSystem());
		assertEquals(mapping.getId(), accountFound.getSystemMapping());
		assertEquals(identityDto.getUsername(), accountFound.getUid());

		// set values to EAV
		IdmFormDefinitionDto finalFormDefinitionDto = formDefinitionDto;
		formAttributes.forEach(idmFormAttributeDto -> getHelper().setEavValue(accountFound, idmFormAttributeDto, AccAccount.class, getHelper().createName(), PersistentType.SHORTTEXT, finalFormDefinitionDto));

		SysSystemMappingDto accountMapping = DtoUtils.getEmbedded(accountFound, AccAccount_.systemMapping, SysSystemMappingDto.class, null);
		assertNotNull(accountMapping);
		assertEquals(AccountType.PERSONAL_OTHER, accountMapping.getAccountType());

		// do account management
		accountManagementService.resolveIdentityAccounts(identityDto);
		// do provisioning
		provisioningService.doProvisioning(identityDto);

		SysProvisioningOperationFilter provisioningOperationFilter = new SysProvisioningOperationFilter();
		provisioningOperationFilter.setSystemId(system.getId());
		provisioningOperationFilter.setOperationType(ProvisioningEventType.UPDATE);
		List<SysProvisioningArchiveDto> provisioningArchiveDtos = provisioningArchiveService.find(provisioningOperationFilter, null).getContent();
		assertEquals(3, provisioningArchiveDtos.size());

		TestResource resource = helper.findResource(accountFound.getUid());
		assertNotNull(resource);

		// check values in provisioning archive
		Map<ProvisioningAttributeDto, Object> accountObject = provisioningArchiveDtos.get(2).getProvisioningContext().getAccountObject();
		assertNotNull(accountObject);
		IdmIdentityDto finalIdentityDto = identityDto;
		accountObject.forEach((provisioningAttributeDto, o) -> {
			switch (provisioningAttributeDto.getSchemaAttributeName()) {
				case ATTRIBUTE_MAPPING_NAME:
					assertEquals(finalIdentityDto.getUsername(), o);
					assertEquals(resource.getName(), o);
					break;
				case ATTRIBUTE_MAPPING_FIRSTNAME: {
					IdmFormAttributeDto firstNameEav = formAttributes.stream().filter(idmFormAttributeDto -> idmFormAttributeDto.getCode().equals(ATTRIBUTE_MAPPING_FIRSTNAME)).findFirst().orElse(null);
					assertNotNull(firstNameEav);
					List<IdmFormValueDto> values = formService.getValues(accountFound, finalFormDefinitionDto, firstNameEav.getCode());
					assertFalse(values.isEmpty());
					assertEquals(values.get(0).getValue(), o);
					assertEquals(resource.getFirstname(), o);
					break;
				}
				case ATTRIBUTE_MAPPING_LASTNAME: {
					IdmFormAttributeDto lastNameEav = formAttributes.stream().filter(idmFormAttributeDto -> idmFormAttributeDto.getCode().equals(ATTRIBUTE_MAPPING_LASTNAME)).findFirst().orElse(null);
					assertNotNull(lastNameEav);
					List<IdmFormValueDto> values = formService.getValues(accountFound, finalFormDefinitionDto, lastNameEav.getCode());
					assertFalse(values.isEmpty());
					assertEquals(values.get(0).getValue(), o);
					assertEquals(resource.getLastname(), o);
					break;
				}
				case ATTRIBUTE_MAPPING_EMAIL: {
					assertEquals(finalIdentityDto.getEmail(), o);
					assertEquals(resource.getEmail(), o);
					break;
				}
			}
		});

		// we will test overriding of value
		// save EAV to for email attribute
		IdmFormAttributeDto emailAttribute = formDefinitionDto.getFormAttributes().stream()
				.filter(idmFormAttributeDto -> idmFormAttributeDto.getCode().equals(ATTRIBUTE_MAPPING_EMAIL))
				.findFirst().orElse(null);
		assertNotNull(emailAttribute);
		getHelper().setEavValue(accountFound, emailAttribute, AccAccount.class, "overriden value", PersistentType.SHORTTEXT, finalFormDefinitionDto);

		// do account management
		accountManagementService.resolveIdentityAccounts(identityDto);
		// do provisioning
		provisioningService.doProvisioning(identityDto);

		provisioningArchiveDtos = provisioningArchiveService.find(provisioningOperationFilter, null).getContent();
		assertEquals(5, provisioningArchiveDtos.size());

		TestResource resource1 = helper.findResource(accountFound.getUid());
		assertNotNull(resource1);

		// check values in provisioning archive
		accountObject = provisioningArchiveDtos.get(4).getProvisioningContext().getAccountObject();
		assertNotNull(accountObject);
		accountObject.forEach((provisioningAttributeDto, o) -> {
			if (ATTRIBUTE_MAPPING_EMAIL.equals(provisioningAttributeDto.getSchemaAttributeName())) {
				List<IdmFormValueDto> values = formService.getValues(accountFound, finalFormDefinitionDto, emailAttribute.getCode());
				assertFalse(values.isEmpty());
				assertEquals(values.get(0).getValue(), o);
				assertEquals(resource1.getEmail(), o);
			}
		});

		allByType.forEach(idmFormDefinitionDto -> {
			formService.deleteValues(accountFound, finalFormDefinitionDto);
		});
		helper.deleteAllResourceData();
		helper.deleteIdentity(identityDto.getId());
		helper.deleteRole(role.getId());
		helper.deleteSystem(system.getId());
	}

	/**
	 * custom create mapping just with __NAME__ for identity account
	 *
	 * @param system
	 * @return
	 */
	private SysSystemMappingDto createMapping(SysSystemDto system) {
		//
		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);
		//
		SysSystemMappingDto systemMapping = new SysSystemMappingDto();
		systemMapping.setName("default_" + System.currentTimeMillis());
		systemMapping.setEntityType(IdentitySynchronizationExecutor.SYSTEM_ENTITY_TYPE);
		systemMapping.setOperationType(SystemOperationType.PROVISIONING);
		systemMapping.setObjectClass(objectClasses.get(0).getId());
		systemMapping.setAccountType(AccountType.PERSONAL_OTHER);
		systemMapping = systemMappingService.save(systemMapping);

		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());

		Page<SysSchemaAttributeDto> schemaAttributesPage = schemaAttributeService.find(schemaAttributeFilter, null);
		for (SysSchemaAttributeDto schemaAttr : schemaAttributesPage) {
			if (ATTRIBUTE_MAPPING_NAME.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(true);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName(IdmIdentity_.username.getName());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			}
			if (ATTRIBUTE_MAPPING_EMAIL.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(false);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName(IdmIdentity_.email.getName());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			}
		}
		return systemMapping;
	}

	private void addAttributesToMapping(List<IdmFormAttributeDto> formAttributes, SysSystemDto system, SysSystemMappingDto systemMapping) {
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());

		Page<SysSchemaAttributeDto> schemaAttributesPage = schemaAttributeService.find(schemaAttributeFilter, null);
		for (SysSchemaAttributeDto schemaAttr : schemaAttributesPage) {
			formAttributes.forEach(idmFormAttributeDto -> {
				if (idmFormAttributeDto.getCode().equals(schemaAttr.getName())) {
					SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
					attributeMapping.setEntityAttribute(false);
					attributeMapping.setExtendedAttribute(false);
					attributeMapping.setName(schemaAttr.getName());
					attributeMapping.setSchemaAttribute(schemaAttr.getId());
					attributeMapping.setSystemMapping(systemMapping.getId());
					attributeMapping.setStrategyType(AttributeMappingStrategyType.SET);
					systemAttributeMappingService.save(attributeMapping);
				}
			});
		}
	}

}
