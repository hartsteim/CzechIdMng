package eu.bcvsolutions.idm.acc.provisioning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.TestHelper;
import eu.bcvsolutions.idm.acc.domain.MappingContext;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.entity.TestResource;
import eu.bcvsolutions.idm.acc.exception.ProvisioningException;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Basic account management tests (tests for identity ACM are in {@link IdentityAccountManagementTest})
 *
 * @author Svanda
 */
public class MappingContextTest extends AbstractIntegrationTest {

	@Autowired
	private TestHelper helper;
	@Autowired
	private SysSystemMappingService systemMappingService;
	@Autowired
	private SysSystemAttributeMappingService attributeMappingService;
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
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private IdmIdentityContractService identityContractService;
	@Autowired
	private SysSystemEntityService systemEntityService;


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
	public void testMappingContext() {
		SysSystemDto system = helper.createTestResourceSystem(true);
		Assert.assertNotNull(system);
		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(system.getId(), SystemEntityType.IDENTITY);
		Assert.assertNotNull(mapping);

		// Create the description attribute (print context as string).
		createDescriptionAttribute(system, mapping);
		// Set context transformation to the mapping.
		mapping = initContextForMapping(mapping);

		IdmRoleDto roleWithSystem = helper.createRole();
		helper.createRoleSystem(roleWithSystem, system);
		IdmIdentityDto identity = helper.createIdentity();
		helper.createIdentityRole(identity, roleWithSystem, null, null);

		TestResource resource = helper.findResource(identity.getUsername());
		assertNotNull(resource);
		assertEquals(identity.getFirstName(), resource.getFirstname());
		MappingContext context = new MappingContext();
		context.put("test", "TestValueOne");

		assertEquals(context.toString(), resource.getDescrip());

		// Delete role mapping
		systemMappingService.delete(mapping);
	}

	@Test
	public void testMappingContextContract() {
		SysSystemDto system = helper.createTestResourceSystem(true);
		Assert.assertNotNull(system);
		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(system.getId(), SystemEntityType.IDENTITY);
		Assert.assertNotNull(mapping);

		// Create the description attribute (print context as string).
		createDescriptionAttribute(system, mapping);
		// Set context transformation to the mapping
		// Add identity contracts to the context.
		mapping.setAddContextContracts(true);
		mapping = initContextForMapping(mapping);

		IdmRoleDto roleWithSystem = helper.createRole();
		helper.createRoleSystem(roleWithSystem, system);
		IdmIdentityDto identity = helper.createIdentity();
		helper.createContract(identity, null, LocalDate.now(), null);
		List<IdmIdentityContractDto> contracts = identityContractService.findAllByIdentity(identity.getId());
		Assert.assertEquals(2, contracts.size());

		helper.createIdentityRole(identity, roleWithSystem, null, null);

		TestResource resource = helper.findResource(identity.getUsername());
		assertNotNull(resource);
		assertEquals(identity.getFirstName(), resource.getFirstname());
		MappingContext context = new MappingContext();
		context.put("test", "TestValueOne");
		context.setContracts(contracts);

		assertEquals(context.toString(), resource.getDescrip());

		// Delete role mapping
		systemMappingService.delete(mapping);
	}

	@Test
	public void testMappingContextIdentityRoles() {
		SysSystemDto system = helper.createTestResourceSystem(true);
		Assert.assertNotNull(system);
		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(system.getId(), SystemEntityType.IDENTITY);
		Assert.assertNotNull(mapping);

		// Create the description attribute (print context as string).
		createDescriptionAttribute(system, mapping);
		// Set context transformation to the mapping.
		// Add identity roles to the context.
		mapping.setAddContextIdentityRoles(true);
		mapping = initContextForMapping(mapping);

		IdmRoleDto roleWithSystem = helper.createRole();
		IdmRoleDto roleWithoutSystem = helper.createRole();
		helper.createRoleSystem(roleWithSystem, system);
		IdmIdentityDto identity = helper.createIdentity();

		helper.createIdentityRole(identity, roleWithoutSystem, null, null);
		helper.createIdentityRole(identity, roleWithSystem, null, null);

		IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();
		identityRoleFilter.setIdentityId(identity.getId());
		List<IdmIdentityRoleDto> identityRoles = identityRoleService
				.find(identityRoleFilter,
						PageRequest.of(0, Integer.MAX_VALUE, Sort.by(IdmIdentityRole_.created.getName())))
				.getContent();
		Assert.assertEquals(2, identityRoles.size());

		TestResource resource = helper.findResource(identity.getUsername());
		assertNotNull(resource);
		assertEquals(identity.getFirstName(), resource.getFirstname());
		MappingContext context = new MappingContext();
		context.put("test", "TestValueOne");
		context.setIdentityRoles(identityRoles);

		assertEquals(context.toString(), resource.getDescrip());

		// Delete role mapping
		systemMappingService.delete(mapping);
	}

	@Test
	public void testMappingContextIdentityRolesForSystem() {
		SysSystemDto system = helper.createTestResourceSystem(true);
		Assert.assertNotNull(system);
		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(system.getId(), SystemEntityType.IDENTITY);
		Assert.assertNotNull(mapping);

		// Create the description attribute (print context as string).
		createDescriptionAttribute(system, mapping);
		// Set context transformation to the mapping.
		// Add identity roles for this system to the context.
		mapping.setAddContextIdentityRolesForSystem(true);
		mapping = initContextForMapping(mapping);

		IdmRoleDto roleWithSystem = helper.createRole();
		IdmRoleDto roleWithoutSystem = helper.createRole();
		helper.createRoleSystem(roleWithSystem, system);
		IdmIdentityDto identity = helper.createIdentity();

		helper.createIdentityRole(identity, roleWithoutSystem, null, null);
		IdmIdentityRoleDto identityRoleWithSystem = helper.createIdentityRole(identity, roleWithSystem, null, null);

		IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();
		identityRoleFilter.setIdentityId(identity.getId());
		List<IdmIdentityRoleDto> identityRoles = identityRoleService
				.find(identityRoleFilter,
						PageRequest.of(0, Integer.MAX_VALUE, Sort.by(IdmIdentityRole_.created.getName())))
				.getContent();
		Assert.assertEquals(2, identityRoles.size());

		TestResource resource = helper.findResource(identity.getUsername());
		assertNotNull(resource);
		assertEquals(identity.getFirstName(), resource.getFirstname());
		MappingContext context = new MappingContext();
		context.put("test", "TestValueOne");
		context.setIdentityRolesForSystem(Lists.newArrayList(identityRoleWithSystem));

		assertEquals(context.toString(), resource.getDescrip());

		// Delete role mapping
		systemMappingService.delete(mapping);
	}

	@Test
	public void testMappingContextConnectorObject() {
		SysSystemDto system = helper.createTestResourceSystem(true);
		Assert.assertNotNull(system);
		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(system.getId(), SystemEntityType.IDENTITY);
		Assert.assertNotNull(mapping);

		// Create the description attribute (print context as string).
		createDescriptionAttribute(system, mapping);
		// Set context transformation to the mapping
		// Add connector object to the context.
		mapping.setAddContextConnectorObject(true);
		mapping = initContextForMapping(mapping);

		IdmRoleDto roleWithSystem = helper.createRole();
		helper.createRoleSystem(roleWithSystem, system);
		IdmIdentityDto identity = helper.createIdentity();
		helper.createContract(identity, null, LocalDate.now(), null);
		List<IdmIdentityContractDto> contracts = identityContractService.findAllByIdentity(identity.getId());
		Assert.assertEquals(2, contracts.size());

		helper.createIdentityRole(identity, roleWithSystem, null, null);

		TestResource resource = helper.findResource(identity.getUsername());
		assertNotNull(resource);
		assertEquals(identity.getFirstName(), resource.getFirstname());
		MappingContext context = new MappingContext();
		context.put("test", "TestValueOne");
		assertEquals(context.toString(), resource.getDescrip());

		SysSystemEntityDto systemEntity = systemEntityService.getBySystemAndEntityTypeAndUid(system, SystemEntityType.IDENTITY, identity.getUsername());
		IcConnectorObject connectorObject = systemEntityService.getConnectorObject(systemEntity);
		Assert.assertNotNull(connectorObject);

		// Invoke provisioning
		identityService.save(identity);
		resource = helper.findResource(identity.getUsername());
		assertNotNull(resource);
		assertEquals(identity.getFirstName(), resource.getFirstname());
		context = new MappingContext();
		context.put("test", "TestValueOne");
		context.setConnectorObject(connectorObject);
		assertEquals(context.toString(), resource.getDescrip());

		// Delete role mapping
		systemMappingService.delete(mapping);
	}

	@Test(expected = ProvisioningException.class)
	public void testMappingContextWrongType() {
		SysSystemDto system = helper.createTestResourceSystem(true);
		Assert.assertNotNull(system);

		SysSystemMappingDto mapping = systemMappingService.findProvisioningMapping(system.getId(), SystemEntityType.IDENTITY);
		Assert.assertNotNull(mapping);
		mapping.setMappingContextScript("return \"WrongType\";");
		mapping = systemMappingService.save(mapping);

		IdmRoleDto roleWithSystem = helper.createRole();
		helper.createRoleSystem(roleWithSystem, system);
		IdmIdentityDto identity = helper.createIdentity();
		IdmIdentityContractDto primeContract = helper.getPrimeContract(identity);
		assertNotNull(primeContract);
		helper.createContractPosition(primeContract);

		helper.createIdentityRole(identity, roleWithSystem, null, null);
		Assert.fail("ProvisioningException: (Script 'get mapping context' must return 'MappingContext' type!) must be throw!");
	}


	@Transactional
	public void deleteAllResourceData() {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM " + TestResource.TABLE_NAME);
		q.executeUpdate();
	}

	private SysSystemMappingDto initContextForMapping(SysSystemMappingDto mapping) {
		// Set context transformation to the mapping.
		mapping.setMappingContextScript(
				"context.put(\"test\", \"TestValueOne\");\n" +
						"\n" +
						"return context;");
		mapping = systemMappingService.save(mapping);
		return mapping;
	}

	private void createDescriptionAttribute(SysSystemDto system, SysSystemMappingDto mapping) {
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());
		schemaAttributeFilter.setName(TestHelper.ATTRIBUTE_MAPPING_DESCRIPTION);
		SysSchemaAttributeDto descriptionSchemaAttribute = schemaAttributeService.find(schemaAttributeFilter, null)
				.getContent()
				.get(0);
		Assert.assertNotNull(descriptionSchemaAttribute);
		SysSystemAttributeMappingDto descriptionAttribute = new SysSystemAttributeMappingDto();
		descriptionAttribute.setUid(false);
		descriptionAttribute.setEntityAttribute(false);
		descriptionAttribute.setName(descriptionSchemaAttribute.getName());
		descriptionAttribute.setSchemaAttribute(descriptionSchemaAttribute.getId());
		descriptionAttribute.setSystemMapping(mapping.getId());
		descriptionAttribute.setTransformToResourceScript("return context.toString();");
		descriptionAttribute = attributeMappingService.save(descriptionAttribute);
	}

	private MappingContextTest getBean() {
		return applicationContext.getAutowireCapableBeanFactory().createBean(this.getClass());
	}

}