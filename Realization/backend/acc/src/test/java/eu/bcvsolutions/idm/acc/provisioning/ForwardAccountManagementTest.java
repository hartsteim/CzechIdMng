package eu.bcvsolutions.idm.acc.provisioning;

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
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.bcvsolutions.idm.acc.TestHelper;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccIdentityAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.entity.TestResource;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Tests for forward account management
 * 
 * @author Svanda
 *
 */
@Service
public class ForwardAccountManagementTest extends AbstractIntegrationTest {

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
	private IdmIdentityService identityService;
	@Autowired
	private SysRoleSystemService roleSystemService;
	@Autowired
	private AccIdentityAccountService identityAccountService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private IdmIdentityContractService identityContractService;

	@Before
	public void init() {
		loginAsAdmin();
	}

	@After
	public void logout() {
		super.logout();
	}


	
	@Test
	public void forwardAcmDisabledTest() {
		SysSystemDto system = initIdentityData();
		Assert.assertNotNull(system);
		
		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(system.getId(), SystemEntityType.IDENTITY);
		Assert.assertNotNull(mapping);
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
		// Forward ACM is disabled
		roleSystemDefault.setForwardAccountManagemen(false);
		//
		roleSystemDefault = roleSystemService.save(roleSystemDefault);
		
		IdmIdentityRoleDto identityRole = new IdmIdentityRoleDto();
		identityRole.setIdentityContract(identityContractService.getPrimeContract(identity.getId()).getId());
		identityRole.setRole(roleDefault.getId());
		identityRole.setValidFrom(LocalDate.now().plusDays(10));
		identityRole = identityRoleService.save(identityRole);
		
		identityAccounts = identityAccountService.find(roleAccountFilter, null).getContent();
		// Role assigned, but is valid in the future and forward ACM is disabled
		Assert.assertEquals(0, identityAccounts.size());
		
		// Delete
		identityService.delete(identity);
		roleService.delete(roleDefault);
	}
	
	@Test
	public void forwardAcmEnabledTest() {
		SysSystemDto system = initIdentityData();
		Assert.assertNotNull(system);
		
		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(system.getId(), SystemEntityType.IDENTITY);
		Assert.assertNotNull(mapping);
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
		// Forward ACM is enabled
		roleSystemDefault.setForwardAccountManagemen(true);
		//
		roleSystemDefault = roleSystemService.save(roleSystemDefault);
		
		IdmIdentityRoleDto identityRole = new IdmIdentityRoleDto();
		identityRole.setIdentityContract(identityContractService.getPrimeContract(identity.getId()).getId());
		identityRole.setRole(roleDefault.getId());
		identityRole.setValidFrom(LocalDate.now().plusDays(10));
		identityRole = identityRoleService.save(identityRole);
		
		identityAccounts = identityAccountService.find(roleAccountFilter, null).getContent();
		// Role assigned - is valid in the future and forward ACM is enabled
		Assert.assertEquals(1, identityAccounts.size());
		
		// Delete
		identityService.delete(identity);
		roleService.delete(roleDefault);
	}
	
	@Test
	public void identityRoleIsValidInPastTest() {
		SysSystemDto system = initIdentityData();
		Assert.assertNotNull(system);
		
		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(system.getId(), SystemEntityType.IDENTITY);
		Assert.assertNotNull(mapping);
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
		// Forward ACM is enabled
		roleSystemDefault.setForwardAccountManagemen(true);
		//
		roleSystemDefault = roleSystemService.save(roleSystemDefault);
		
		IdmIdentityRoleDto identityRole = new IdmIdentityRoleDto();
		identityRole.setIdentityContract(identityContractService.getPrimeContract(identity.getId()).getId());
		identityRole.setRole(roleDefault.getId());
		identityRole.setValidFrom(LocalDate.now().minusDays(10));
		identityRole.setValidTill(LocalDate.now().minusDays(1)); // Assignment is expired
		identityRole = identityRoleService.save(identityRole);
		
		identityAccounts = identityAccountService.find(roleAccountFilter, null).getContent();
		// Role assigned - but is expired (forward ACM is enabled)
		Assert.assertEquals(0, identityAccounts.size());
		
		// Delete
		identityService.delete(identity);
		roleService.delete(roleDefault);
	}




	@Transactional
	public void deleteAllResourceData() {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM " + TestResource.TABLE_NAME);
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
}
