package eu.bcvsolutions.idm.acc.service.impl;

import eu.bcvsolutions.idm.acc.TestHelper;
import eu.bcvsolutions.idm.acc.domain.AccountType;
import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.ProvisioningContext;
import eu.bcvsolutions.idm.acc.domain.ProvisioningEventType;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.ProvisioningAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysAttributeControlledValueDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningArchiveDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningOperationDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysAttributeControlledValueFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysProvisioningOperationFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.entity.AccAccount_;
import eu.bcvsolutions.idm.acc.exception.ProvisioningException;
import eu.bcvsolutions.idm.acc.service.api.SysAttributeControlledValueService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningArchiveService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningOperationService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.domain.ContractState;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestedByType;
import eu.bcvsolutions.idm.core.api.dto.AbstractConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.ApplicantDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import org.junit.Test;
import org.modelmapper.internal.util.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test for provisioning merge
 *
 * @author Vít Švanda
 * @author Tomáš Doischer
 */
public abstract class AbstractProvisioningMergeTest extends AbstractIntegrationTest {
	public static final String RIGHTS_ATTRIBUTE = "RIGHTS";
	public static final String ONE_VALUE = "ONE";
	public static final String TWO_VALUE = "TWO";

	@Autowired
	protected SysSystemAttributeMappingService attributeMappingService;
	@Autowired
	private SysSchemaObjectClassService schemaService;
	@Autowired
	protected SysSchemaAttributeService schemaAttributeService;
	@Autowired
	protected SysRoleSystemAttributeService roleSystemAttributeService;
	@Autowired
	private SysAttributeControlledValueService attributeControlledValueService;
	@Autowired
	private TestHelper helper;
	@Autowired
	private SysRoleSystemService roleSystemService;
	@Autowired
	protected SysProvisioningArchiveService provisioningArchiveService;
	@Autowired
	private IdmIdentityContractService identityContractService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	protected IdmRoleRequestService roleRequestService;
	@Autowired
	protected SysSystemService systemService;
	@Autowired
	protected SysProvisioningOperationService provisioningOperationService;

	@Test
	public void testBasicMerge() {
		SysSystemDto system = createTestSystem();
		SysSystemMappingDto mapping = createTestMapping(system);

		IdmRoleDto roleOne = helper.createRole();
		IdmRoleDto roleTwo = helper.createRole();
		IdmRoleDto loginRole = helper.createRole();

		SysRoleSystemDto roleSystemOne = createRoleSystem(system, roleOne);
		SysRoleSystemDto roleSystemTwo = createRoleSystem(system, roleTwo);
		createRoleSystem(system, loginRole);

		SysSchemaAttributeDto rightsSchemaAttribute = createSchemaAttribute(mapping, RIGHTS_ATTRIBUTE);
		final SysSystemAttributeMappingDto systemAttributeMapping = createSystemAttributeMapping(rightsSchemaAttribute, mapping, AttributeMappingStrategyType.MERGE);

		createRoleSystemAttribute(roleSystemOne, systemAttributeMapping, addQuotations(ONE_VALUE));
		createRoleSystemAttribute(roleSystemTwo, systemAttributeMapping, addQuotations(TWO_VALUE));

		IdmIdentityDto identity = helper.createIdentity();
		IdmIdentityContractDto contract = helper.getPrimeContract(identity);

		helper.assignRoles(contract, loginRole);
		helper.assignRoles(contract, roleOne);

		SysProvisioningOperationFilter filter = new SysProvisioningOperationFilter();
		filter.setSystemId(system.getId());
		filter.setSystemEntityUid(identity.getUsername());

		checkProvisioning(filter, List.of(ONE_VALUE));
		helper.assignRoles(contract, roleTwo);
		checkProvisioning(filter, List.of(ONE_VALUE, TWO_VALUE));
	}

	@Test
	public void testBasicMergeWithLoginRole() {
		SysSystemDto system = createTestSystem();
		SysSystemMappingDto mapping = createTestMapping(system);

		IdmRoleDto roleOne = helper.createRole();
		IdmRoleDto roleTwo = helper.createRole();
		IdmRoleDto loginRole = helper.createRole();

		SysRoleSystemDto roleSystemOne = createRoleSystem(system, roleOne, false);
		SysRoleSystemDto roleSystemTwo = createRoleSystem(system, roleTwo, false);
		SysRoleSystemDto roleSystemLoginRole = createRoleSystem(system, loginRole);

		SysSchemaAttributeDto rightsSchemaAttribute = createSchemaAttribute(mapping, RIGHTS_ATTRIBUTE);
		final SysSystemAttributeMappingDto systemAttributeMapping = createSystemAttributeMapping(rightsSchemaAttribute, mapping, AttributeMappingStrategyType.MERGE);

		createRoleSystemAttribute(roleSystemOne, systemAttributeMapping, addQuotations(ONE_VALUE));
		createRoleSystemAttribute(roleSystemTwo, systemAttributeMapping, addQuotations(TWO_VALUE));
		//
		IdmIdentityDto identity = helper.createIdentity();
		IdmIdentityContractDto contract = helper.getPrimeContract(identity);

		SysProvisioningOperationFilter spf = new SysProvisioningOperationFilter();
		spf.setSystemId(system.getId());
		spf.setSystemEntityUid(identity.getUsername());

		helper.assignRoles(contract, loginRole);

		checkProvisioning(spf, null);
		helper.assignRoles(contract, roleOne);
		checkProvisioning(spf, List.of(ONE_VALUE));
		helper.assignRoles(contract, roleTwo);
		checkProvisioning(spf, List.of(ONE_VALUE, TWO_VALUE));
	}

	private void checkProvisioning(SysProvisioningOperationFilter filter, List<String> expectedValues) {

		final Object content = provisioningOperationService.find(filter, null).stream()
				.map(SysProvisioningOperationDto::getProvisioningContext)
				.map(ProvisioningContext::getAccountObject)
				.flatMap(provisioningAttributeDtoObjectMap -> provisioningAttributeDtoObjectMap.entrySet().stream())
				.filter(e -> e.getKey().getSchemaAttributeName().equals(RIGHTS_ATTRIBUTE))
				.map(Entry::getValue)
				.filter(Objects::nonNull)
				.findFirst().orElse(null);
		if (expectedValues == null) {
			assertNull(content);
			//delete all provisionings
			provisioningOperationService.deleteAll(provisioningOperationService.find(filter, null));
			return;
		}
		assertNotNull(content);
		final List<?> values = content instanceof List ? (List<?>) content : List.of(content);

		assertEquals(expectedValues, values);

		//delete all provisionings
		provisioningOperationService.deleteAll(provisioningOperationService.find(filter, null));
	}

	private String addQuotations(String oneValue) {
		return "\"" + oneValue + "\"";
	}

	private SysRoleSystemAttributeDto createRoleSystemAttribute(SysRoleSystemDto roleSystemOne, SysSystemAttributeMappingDto systemAttributeMapping, String oneValue) {
		SysRoleSystemAttributeDto roleAttributeOne = new SysRoleSystemAttributeDto();
		roleAttributeOne.setName(RIGHTS_ATTRIBUTE);
		roleAttributeOne.setRoleSystem(roleSystemOne.getId());
		roleAttributeOne.setStrategyType(AttributeMappingStrategyType.MERGE);
		roleAttributeOne.setSystemAttributeMapping(systemAttributeMapping.getId());
		roleAttributeOne.setTransformToResourceScript(oneValue);
		roleAttributeOne.setEntityAttribute(false);
		return roleSystemAttributeService.saveInternal(roleAttributeOne);
	}

	private SysSystemAttributeMappingDto createSystemAttributeMapping(SysSchemaAttributeDto rightsSchemaAttribute, SysSystemMappingDto mapping, AttributeMappingStrategyType merge) {
		SysSystemAttributeMappingDto rightsAttribute = new SysSystemAttributeMappingDto();
		rightsAttribute.setSchemaAttribute(rightsSchemaAttribute.getId());
		rightsAttribute.setSystemMapping(mapping.getId());
		rightsAttribute.setName(RIGHTS_ATTRIBUTE);
		rightsAttribute.setStrategyType(merge);
		rightsAttribute.setEntityAttribute(false);
		return attributeMappingService.save(rightsAttribute);
	}

	private SysSchemaAttributeDto createSchemaAttribute(SysSystemMappingDto mapping, String name) {
		SysSchemaAttributeDto rightsSchemaAttribute = new SysSchemaAttributeDto();
		rightsSchemaAttribute.setObjectClass(mapping.getObjectClass());
		rightsSchemaAttribute.setName(name);
		rightsSchemaAttribute.setMultivalued(true);
		rightsSchemaAttribute.setClassType(String.class.getName());
		rightsSchemaAttribute.setReadable(true);
		rightsSchemaAttribute.setUpdateable(true);
		rightsSchemaAttribute.setReturnedByDefault(true);
		rightsSchemaAttribute = schemaAttributeService.save(rightsSchemaAttribute);
		return rightsSchemaAttribute;
	}

	@Test
	public void testAttribteControlledValues() {
		SysSystemDto system = createTestSystem();
		SysSystemMappingDto mapping = createTestMapping(system);
		IdmRoleDto roleOne = helper.createRole();
		IdmRoleDto roleTwo = helper.createRole();
		IdmRoleDto roleOneDuplicated = helper.createRole();

		SysRoleSystemDto roleSystemOne = createRoleSystem(system, roleOne);
		SysRoleSystemDto roleSystemTwo = createRoleSystem(system, roleTwo);
		SysRoleSystemDto roleSystemOneDuplicated = createRoleSystem(system, roleOneDuplicated);

		SysSchemaAttributeDto rightsSchemaAttribute = new SysSchemaAttributeDto();
		rightsSchemaAttribute.setObjectClass(mapping.getObjectClass());
		rightsSchemaAttribute.setName(RIGHTS_ATTRIBUTE);
		rightsSchemaAttribute.setMultivalued(true);
		rightsSchemaAttribute.setClassType(String.class.getName());
		rightsSchemaAttribute.setReadable(true);
		rightsSchemaAttribute.setUpdateable(true);

		rightsSchemaAttribute = schemaAttributeService.save(rightsSchemaAttribute);

		SysSystemAttributeMappingDto rightsAttribute = createSystemAttributeMapping(rightsSchemaAttribute, mapping, AttributeMappingStrategyType.MERGE);

		createRoleSystemAttribute(roleSystemOne, rightsAttribute, "return '" + ONE_VALUE + "';");

		List<Serializable> controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(1, controlledAttributeValues.size());
		assertEquals(ONE_VALUE, controlledAttributeValues.get(0));

		createRoleSystemAttribute(roleSystemOneDuplicated, rightsAttribute, "return '" + TWO_VALUE + "';");

		createRoleSystemAttribute(roleSystemTwo, rightsAttribute, "return '" + TWO_VALUE + "';");

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(2, controlledAttributeValues.size());
		assertTrue(controlledAttributeValues.contains(ONE_VALUE));
		assertTrue(controlledAttributeValues.contains(TWO_VALUE));
	}

	protected SysSystemMappingDto createTestMapping(SysSystemDto system) {
		return helper.createMapping(system);
	}

	protected SysSystemDto createTestSystem() {
		return helper.createSystem("test_resource");
	}

	@Test(expected = ProvisioningException.class)
	public void testAttribteConflictStrategy() {
		AbstractDto owner = createOwner();
		SysSystemDto system;
		SysSystemMappingDto mapping;
		ApplicantDto applicant = getApplicant(owner);
		if (owner instanceof AccAccountDto) {
			system = DtoUtils.getEmbedded(owner, AccAccount_.system, SysSystemDto.class, null);
			mapping = DtoUtils.getEmbedded(owner, AccAccount_.systemMapping, SysSystemMappingDto.class, null);
		} else if (owner instanceof IdmIdentityContractDto) {
			system = createTestSystem();
			mapping = createTestMapping(system);

			final IdmRoleDto loginRole = getHelper().createRole();
			createRoleSystem(system, loginRole);
			getHelper().assignRoles((IdmIdentityContractDto) owner, loginRole);
		} else {
			throw new UnsupportedOperationException(String.format("This owner type is not supported! Owner: [{}]", owner));
		}

		IdmRoleDto roleOne = helper.createRole();

		SysRoleSystemDto roleSystemOne = createRoleSystem(system, roleOne);

		SysSchemaAttributeDto rightsSchemaAttribute = new SysSchemaAttributeDto();
		rightsSchemaAttribute.setObjectClass(mapping.getObjectClass());
		rightsSchemaAttribute.setName(RIGHTS_ATTRIBUTE);
		rightsSchemaAttribute.setMultivalued(true);
		rightsSchemaAttribute.setClassType(String.class.getName());
		rightsSchemaAttribute.setReadable(true);
		rightsSchemaAttribute.setUpdateable(true);

		rightsSchemaAttribute = schemaAttributeService.save(rightsSchemaAttribute);

		SysSystemAttributeMappingDto rightsAttribute = new SysSystemAttributeMappingDto();
		rightsAttribute.setSchemaAttribute(rightsSchemaAttribute.getId());
		rightsAttribute.setSystemMapping(mapping.getId());
		rightsAttribute.setName(RIGHTS_ATTRIBUTE);
		rightsAttribute.setStrategyType(AttributeMappingStrategyType.MERGE);
		rightsAttribute.setEntityAttribute(false);
		rightsAttribute = attributeMappingService.save(rightsAttribute);

		SysRoleSystemAttributeDto roleAttributeOne = new SysRoleSystemAttributeDto();
		roleAttributeOne.setName(RIGHTS_ATTRIBUTE);
		roleAttributeOne.setRoleSystem(roleSystemOne.getId());
		roleAttributeOne.setStrategyType(AttributeMappingStrategyType.SET);
		roleAttributeOne.setSystemAttributeMapping(rightsAttribute.getId());
		roleAttributeOne.setEntityAttribute(false);
		roleAttributeOne.setTransformToResourceScript("return '" + ONE_VALUE + "';");
		roleAttributeOne = roleSystemAttributeService.saveInternal(roleAttributeOne);

		getHelper().loginAdmin();

		// create request
		IdmRoleRequestDto request = new IdmRoleRequestDto();
		request.setApplicantInfo(applicant);
		request.setExecuteImmediately(true);
		request.setRequestedByType(RoleRequestedByType.MANUALLY);
		request.setState(RoleRequestState.EXECUTED);
		request = roleRequestService.save(request);
		assertEquals(RoleRequestState.CONCEPT, request.getState());
		AbstractConceptRoleRequestDto concept = createConceptRoleRequest(request, roleOne, owner.getId());
		assertEquals(RoleRequestState.CONCEPT, concept.getState());

		getHelper().startRequestInternal(request, true, true);
		final UUID reqId = request.getId();
		getHelper().waitForResult(res -> roleRequestService.get(reqId).getState() != RoleRequestState.EXECUTED, 1000, 30);
		request = roleRequestService.get(request.getId());

	}

	@Test
	public void testChangeValueDefinition() {
		SysSystemDto system = createTestSystem();
		SysSystemMappingDto mapping = createTestMapping(system);
		IdmRoleDto roleOne = helper.createRole();
		IdmRoleDto roleTwo = helper.createRole();

		SysRoleSystemDto roleSystemOne = createRoleSystem(system, roleOne);
		SysRoleSystemDto roleSystemTwo = createRoleSystem(system, roleTwo);

		SysSchemaAttributeDto rightsSchemaAttribute = new SysSchemaAttributeDto();
		rightsSchemaAttribute.setObjectClass(mapping.getObjectClass());
		rightsSchemaAttribute.setName(RIGHTS_ATTRIBUTE);
		rightsSchemaAttribute.setMultivalued(true);
		rightsSchemaAttribute.setClassType(String.class.getName());
		rightsSchemaAttribute.setReadable(true);
		rightsSchemaAttribute.setUpdateable(true);

		rightsSchemaAttribute = schemaAttributeService.save(rightsSchemaAttribute);

		SysSystemAttributeMappingDto rightsAttribute = createSystemAttributeMapping(rightsSchemaAttribute, mapping, AttributeMappingStrategyType.MERGE);

		createRoleSystemAttribute(roleSystemOne, rightsAttribute, "return '" + ONE_VALUE + "';");

		List<Serializable> controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		SysRoleSystemAttributeDto roleAttributeTwo = createRoleSystemAttribute(roleSystemTwo, rightsAttribute, "return '" + TWO_VALUE + "';");

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(2, controlledAttributeValues.size());
		assertTrue(controlledAttributeValues.contains(ONE_VALUE));
		assertTrue(controlledAttributeValues.contains(TWO_VALUE));

		SysAttributeControlledValueFilter attributeControlledValueFilter = new SysAttributeControlledValueFilter();
		attributeControlledValueFilter.setAttributeMappingId(rightsAttribute.getId());
		attributeControlledValueFilter.setHistoricValue(Boolean.TRUE);

		List<Serializable> historicControlledValues = attributeControlledValueService //
				.find(attributeControlledValueFilter, null) //
				.getContent() //
				.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());

		assertNotNull(historicControlledValues);
		assertEquals(0, historicControlledValues.size());

		// Change value definition on attribute TWO (should be changed in controlled
		// values
		// and old value appears in the history)
		roleAttributeTwo.setTransformToResourceScript("return '" + TWO_VALUE + "Changed';");
		roleAttributeTwo = roleSystemAttributeService.saveInternal(roleAttributeTwo);

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(2, controlledAttributeValues.size());
		assertTrue(controlledAttributeValues.contains(ONE_VALUE));
		assertTrue(controlledAttributeValues.contains(TWO_VALUE + "Changed"));

		// Search historic controlled values for that attribute
		historicControlledValues = attributeControlledValueService //
				.find(attributeControlledValueFilter, null) //
				.getContent() //
				.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());

		assertNotNull(historicControlledValues);
		assertEquals(1, historicControlledValues.size());
		assertTrue(historicControlledValues.contains(TWO_VALUE));

	}

	@Test
	public void testDisableAttribteControlledValues() {
		SysSystemDto system = createTestSystem();
		SysSystemMappingDto mapping = createTestMapping(system);
		IdmRoleDto roleOne = helper.createRole();
		IdmRoleDto roleTwo = helper.createRole();

		SysRoleSystemDto roleSystemOne = createRoleSystem(system, roleOne);
		SysRoleSystemDto roleSystemTwo = createRoleSystem(system, roleTwo);

		SysSchemaAttributeDto rightsSchemaAttribute = new SysSchemaAttributeDto();
		rightsSchemaAttribute.setObjectClass(mapping.getObjectClass());
		rightsSchemaAttribute.setName(RIGHTS_ATTRIBUTE);
		rightsSchemaAttribute.setMultivalued(true);
		rightsSchemaAttribute.setClassType(String.class.getName());
		rightsSchemaAttribute.setReadable(true);
		rightsSchemaAttribute.setUpdateable(true);

		rightsSchemaAttribute = schemaAttributeService.save(rightsSchemaAttribute);

		SysSystemAttributeMappingDto rightsAttribute = createSystemAttributeMapping(rightsSchemaAttribute, mapping, AttributeMappingStrategyType.MERGE);

		createRoleSystemAttribute(roleSystemOne, rightsAttribute, "return '" + ONE_VALUE + "';");

		List<Serializable> controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		SysRoleSystemAttributeDto roleAttributeTwo = createRoleSystemAttribute(roleSystemTwo, rightsAttribute, "return '" + TWO_VALUE + "';");

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(2, controlledAttributeValues.size());
		assertTrue(controlledAttributeValues.contains(ONE_VALUE));
		assertTrue(controlledAttributeValues.contains(TWO_VALUE));

		SysAttributeControlledValueFilter attributeControlledValueFilter = new SysAttributeControlledValueFilter();
		attributeControlledValueFilter.setAttributeMappingId(rightsAttribute.getId());
		attributeControlledValueFilter.setHistoricValue(Boolean.TRUE);

		List<Serializable> historicControlledValues = attributeControlledValueService //
				.find(attributeControlledValueFilter, null) //
				.getContent() //
				.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());

		assertNotNull(historicControlledValues);
		assertEquals(0, historicControlledValues.size());

		// Set attribute TWO as disabled (should be disappears from controlled values
		// and appears in the history)
		roleAttributeTwo.setDisabledAttribute(true);
		roleAttributeTwo = roleSystemAttributeService.saveInternal(roleAttributeTwo);

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(1, controlledAttributeValues.size());
		assertTrue(controlledAttributeValues.contains(ONE_VALUE));

		// Search historic controlled values for that attribute
		historicControlledValues = attributeControlledValueService //
				.find(attributeControlledValueFilter, null) //
				.getContent() //
				.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());

		assertNotNull(historicControlledValues);
		assertEquals(1, historicControlledValues.size());
		assertTrue(historicControlledValues.contains(TWO_VALUE));

		// Set attribute TWO as enabled (should be appears in controlled values
		// and disappears from the history)
		roleAttributeTwo.setDisabledAttribute(false);
		roleAttributeTwo = roleSystemAttributeService.saveInternal(roleAttributeTwo);

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(2, controlledAttributeValues.size());
		assertTrue(controlledAttributeValues.contains(ONE_VALUE));
		assertTrue(controlledAttributeValues.contains(TWO_VALUE));

		// Recalculation
		attributeMappingService.getCachedControlledAndHistoricAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		// Search historic controlled values for that attribute
		historicControlledValues = attributeControlledValueService //
				.find(attributeControlledValueFilter, null) //
				.getContent() //
				.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());

		assertNotNull(historicControlledValues);
		assertEquals(0, historicControlledValues.size());
	}

	@Test
	public void testChangeStrategyAttribteControlledValues() {
		SysSystemDto system = createTestSystem();
		SysSystemMappingDto mapping = createTestMapping(system);
		IdmRoleDto roleOne = helper.createRole();
		IdmRoleDto roleTwo = helper.createRole();

		SysRoleSystemDto roleSystemOne = createRoleSystem(system, roleOne);
		SysRoleSystemDto roleSystemTwo = createRoleSystem(system, roleTwo);

		SysSchemaObjectClassDto objectClass = schemaService.get(mapping.getObjectClass());
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setObjectClassId(objectClass.getId());
		schemaAttributeService.find(schemaAttributeFilter, null);

		SysSchemaAttributeDto rightsSchemaAttribute = new SysSchemaAttributeDto();
		rightsSchemaAttribute.setObjectClass(objectClass.getId());
		rightsSchemaAttribute.setName(RIGHTS_ATTRIBUTE);
		rightsSchemaAttribute.setMultivalued(true);
		rightsSchemaAttribute.setClassType(String.class.getName());
		rightsSchemaAttribute.setReadable(true);
		rightsSchemaAttribute.setUpdateable(true);

		rightsSchemaAttribute = schemaAttributeService.save(rightsSchemaAttribute);

		SysSystemAttributeMappingDto rightsAttribute = createSystemAttributeMapping(rightsSchemaAttribute, mapping, AttributeMappingStrategyType.MERGE);

		createRoleSystemAttribute(roleSystemOne, rightsAttribute, "return '" + ONE_VALUE + "';");

		List<Serializable> controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		SysRoleSystemAttributeDto roleAttributeTwo = createRoleSystemAttribute(roleSystemTwo, rightsAttribute, "return '" + TWO_VALUE + "';");

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(2, controlledAttributeValues.size());
		assertTrue(controlledAttributeValues.contains(ONE_VALUE));
		assertTrue(controlledAttributeValues.contains(TWO_VALUE));

		SysAttributeControlledValueFilter attributeControlledValueFilter = new SysAttributeControlledValueFilter();
		attributeControlledValueFilter.setAttributeMappingId(rightsAttribute.getId());
		attributeControlledValueFilter.setHistoricValue(Boolean.TRUE);

		List<Serializable> historicControlledValues = attributeControlledValueService //
				.find(attributeControlledValueFilter, null) //
				.getContent() //
				.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());

		assertNotNull(historicControlledValues);
		assertEquals(0, historicControlledValues.size());

		// Set attribute TWO as SET (should be disappears from controlled values
		// and appears in the history)
		roleAttributeTwo.setStrategyType(AttributeMappingStrategyType.SET);
		roleAttributeTwo = roleSystemAttributeService.saveInternal(roleAttributeTwo);

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(1, controlledAttributeValues.size());
		assertTrue(controlledAttributeValues.contains(ONE_VALUE));

		// Search historic controlled values for that attribute
		historicControlledValues = attributeControlledValueService //
				.find(attributeControlledValueFilter, null) //
				.getContent() //
				.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());

		assertNotNull(historicControlledValues);
		assertEquals(1, historicControlledValues.size());
		assertTrue(historicControlledValues.contains(TWO_VALUE));

		// Set attribute TWO as MERGE (should be appears in controlled values
		// and disappears from the history)
		roleAttributeTwo.setStrategyType(AttributeMappingStrategyType.MERGE);
		roleAttributeTwo = roleSystemAttributeService.saveInternal(roleAttributeTwo);

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(2, controlledAttributeValues.size());
		assertTrue(controlledAttributeValues.contains(ONE_VALUE));
		assertTrue(controlledAttributeValues.contains(TWO_VALUE));

		// Manual recalculation (needed for deleting redundant historic value)
		attributeMappingService.recalculateAttributeControlledValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, rightsAttribute, mapping.getId());

		// Search historic controlled values for that attribute
		historicControlledValues = attributeControlledValueService //
				.find(attributeControlledValueFilter, null) //
				.getContent() //
				.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());

		assertNotNull(historicControlledValues);
		assertEquals(0, historicControlledValues.size());
	}

	@Test
	public void testCachedControlledAndHistoricValues() {
		SysSystemDto system = createTestSystem();
		SysSystemMappingDto mapping = createTestMapping(system);
		IdmRoleDto roleOne = helper.createRole();
		IdmRoleDto roleTwo = helper.createRole();

		SysRoleSystemDto roleSystemOne = createRoleSystem(system, roleOne);
		SysRoleSystemDto roleSystemTwo = createRoleSystem(system, roleTwo);

		SysSchemaAttributeDto rightsSchemaAttribute = new SysSchemaAttributeDto();
		rightsSchemaAttribute.setObjectClass(mapping.getObjectClass());
		rightsSchemaAttribute.setName(RIGHTS_ATTRIBUTE);
		rightsSchemaAttribute.setMultivalued(true);
		rightsSchemaAttribute.setClassType(String.class.getName());
		rightsSchemaAttribute.setReadable(true);
		rightsSchemaAttribute.setUpdateable(true);

		rightsSchemaAttribute = schemaAttributeService.save(rightsSchemaAttribute);

		SysSystemAttributeMappingDto rightsAttribute = createSystemAttributeMapping(rightsSchemaAttribute, mapping, AttributeMappingStrategyType.MERGE);

		createRoleSystemAttribute(roleSystemOne, rightsAttribute, "return '" + ONE_VALUE + "';");

		List<Serializable> controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		SysRoleSystemAttributeDto roleAttributeTwo = createRoleSystemAttribute(roleSystemTwo, rightsAttribute, "return '" + TWO_VALUE + "';");

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(2, controlledAttributeValues.size());
		assertTrue(controlledAttributeValues.contains(ONE_VALUE));
		assertTrue(controlledAttributeValues.contains(TWO_VALUE));

		SysAttributeControlledValueFilter attributeControlledValueFilter = new SysAttributeControlledValueFilter();
		attributeControlledValueFilter.setAttributeMappingId(rightsAttribute.getId());
		attributeControlledValueFilter.setHistoricValue(Boolean.TRUE);

		List<Serializable> historicControlledValues = attributeControlledValueService //
				.find(attributeControlledValueFilter, null) //
				.getContent() //
				.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());

		assertNotNull(historicControlledValues);
		assertEquals(0, historicControlledValues.size());

		// Manual recalculation
		attributeMappingService.recalculateAttributeControlledValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, rightsAttribute, mapping.getId());
		// Check cached controlled and historic values (results are controlled and
		// historic values in one lists)
		List<Serializable> cachedControlledAndHistoricAttributeValues = attributeMappingService.getCachedControlledAndHistoricAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());
		assertNotNull(cachedControlledAndHistoricAttributeValues);
		assertEquals(2, cachedControlledAndHistoricAttributeValues.size());
		assertTrue(cachedControlledAndHistoricAttributeValues.contains(ONE_VALUE));
		assertTrue(cachedControlledAndHistoricAttributeValues.contains(TWO_VALUE));

		// Change value definition on attribute TWO (should be changed in controlled
		// values
		// and old value appears in the history)
		roleAttributeTwo.setTransformToResourceScript("return '" + TWO_VALUE + "Changed';");
		roleAttributeTwo = roleSystemAttributeService.saveInternal(roleAttributeTwo);

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(2, controlledAttributeValues.size());
		assertTrue(controlledAttributeValues.contains(ONE_VALUE));
		assertTrue(controlledAttributeValues.contains(TWO_VALUE + "Changed"));

		// Search historic controlled values for that attribute
		List<SysAttributeControlledValueDto> historicControlledValueDtos = attributeControlledValueService //
				.find(attributeControlledValueFilter, null) //
				.getContent();

		assertNotNull(historicControlledValueDtos);
		assertEquals(1, historicControlledValueDtos.size());
		assertTrue(historicControlledValueDtos.get(0).getValue().equals(TWO_VALUE));

		// Manual recalculation (needed for deleting redundant historic value)
		attributeMappingService.recalculateAttributeControlledValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, rightsAttribute, mapping.getId());
		// Check cached controlled and historic values (results are controlled and
		// historic values in one lists)
		cachedControlledAndHistoricAttributeValues = attributeMappingService.getCachedControlledAndHistoricAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());
		assertNotNull(cachedControlledAndHistoricAttributeValues);
		assertEquals(3, cachedControlledAndHistoricAttributeValues.size());
		assertTrue(cachedControlledAndHistoricAttributeValues.contains(ONE_VALUE));
		assertTrue(cachedControlledAndHistoricAttributeValues.contains(TWO_VALUE));
		assertTrue(cachedControlledAndHistoricAttributeValues.contains(TWO_VALUE + "Changed"));

		// Delete historic value TWO. Should be disappear from the cached values
		attributeControlledValueService.delete(historicControlledValueDtos.get(0));

		cachedControlledAndHistoricAttributeValues = attributeMappingService.getCachedControlledAndHistoricAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());
		assertNotNull(cachedControlledAndHistoricAttributeValues);
		assertEquals(2, cachedControlledAndHistoricAttributeValues.size());
		assertTrue(cachedControlledAndHistoricAttributeValues.contains(ONE_VALUE));
		assertTrue(cachedControlledAndHistoricAttributeValues.contains(TWO_VALUE + "Changed"));

	}

	@Test
	public void testControlledAndHistoricValuesRemoveMapping() {
		SysSystemDto system = createTestSystem();
		SysSystemMappingDto mapping = createTestMapping(system);
		IdmRoleDto roleOne = helper.createRole();
		IdmRoleDto roleTwo = helper.createRole();

		SysRoleSystemDto roleSystemOne = createRoleSystem(system, roleOne);
		SysRoleSystemDto roleSystemTwo = createRoleSystem(system, roleTwo);

		SysSchemaAttributeDto rightsSchemaAttribute = new SysSchemaAttributeDto();
		rightsSchemaAttribute.setObjectClass(mapping.getObjectClass());
		rightsSchemaAttribute.setName(RIGHTS_ATTRIBUTE);
		rightsSchemaAttribute.setMultivalued(true);
		rightsSchemaAttribute.setClassType(String.class.getName());
		rightsSchemaAttribute.setReadable(true);
		rightsSchemaAttribute.setUpdateable(true);

		rightsSchemaAttribute = schemaAttributeService.save(rightsSchemaAttribute);

		SysSystemAttributeMappingDto rightsAttribute = createSystemAttributeMapping(rightsSchemaAttribute, mapping, AttributeMappingStrategyType.MERGE);

		createRoleSystemAttribute(roleSystemOne, rightsAttribute, "return '" + ONE_VALUE + "';");


		createRoleSystemAttribute(roleSystemTwo, rightsAttribute, "return '" + TWO_VALUE + "';");

		List<Serializable> controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(2, controlledAttributeValues.size());
		assertTrue(controlledAttributeValues.contains(ONE_VALUE));
		assertTrue(controlledAttributeValues.contains(TWO_VALUE));

		attributeMappingService.recalculateAttributeControlledValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, rightsAttribute, mapping.getId());

		SysAttributeControlledValueFilter attributeHistoricalValueFilter = new SysAttributeControlledValueFilter();
		attributeHistoricalValueFilter.setHistoricValue(Boolean.TRUE);
		attributeHistoricalValueFilter.setAttributeMappingId(rightsAttribute.getId());
		List<SysAttributeControlledValueDto> historicAttributeValues = attributeControlledValueService.find(attributeHistoricalValueFilter, null).getContent();
		List<Serializable> onlyValues = historicAttributeValues.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());

		assertNotNull(onlyValues);
		assertEquals(0, onlyValues.size());

		// Remove whole mapping (this is different between another tests)
		roleSystemService.delete(roleSystemOne);

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(1, controlledAttributeValues.size());
		assertTrue(controlledAttributeValues.contains(TWO_VALUE));

		historicAttributeValues = attributeControlledValueService.find(attributeHistoricalValueFilter, null).getContent();
		onlyValues = historicAttributeValues.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());


		assertNotNull(onlyValues);
		assertEquals(1, onlyValues.size());
		assertTrue(onlyValues.contains(ONE_VALUE));

		// Remove second whole mapping (this is different between another tests)
		roleSystemService.delete(roleSystemTwo);

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(0, controlledAttributeValues.size());

		historicAttributeValues = attributeControlledValueService.find(attributeHistoricalValueFilter, null).getContent();
		onlyValues = historicAttributeValues.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());

		assertNotNull(onlyValues);
		assertEquals(2, onlyValues.size());
		assertTrue(onlyValues.contains(ONE_VALUE));
		assertTrue(onlyValues.contains(TWO_VALUE));
	}

	@Test
	public void testAttribteControlledValueWithNull() {
		SysSystemDto system = createTestSystem();
		SysSystemMappingDto mapping = createTestMapping(system);
		IdmRoleDto role = helper.createRole();

		SysRoleSystemDto roleSystem = createRoleSystem(system, role);

		SysSchemaAttributeDto rightsSchemaAttribute = new SysSchemaAttributeDto();
		rightsSchemaAttribute.setObjectClass(mapping.getObjectClass());
		rightsSchemaAttribute.setName(RIGHTS_ATTRIBUTE);
		rightsSchemaAttribute.setMultivalued(true);
		rightsSchemaAttribute.setClassType(String.class.getName());
		rightsSchemaAttribute.setReadable(true);
		rightsSchemaAttribute.setUpdateable(true);

		rightsSchemaAttribute = schemaAttributeService.save(rightsSchemaAttribute);

		SysSystemAttributeMappingDto rightsAttribute = createSystemAttributeMapping(rightsSchemaAttribute, mapping, AttributeMappingStrategyType.MERGE);

		SysRoleSystemAttributeDto roleAttribute = new SysRoleSystemAttributeDto();
		roleAttribute.setName(RIGHTS_ATTRIBUTE);
		roleAttribute.setRoleSystem(roleSystem.getId());
		roleAttribute.setStrategyType(AttributeMappingStrategyType.MERGE);
		roleAttribute.setSystemAttributeMapping(rightsAttribute.getId());
		roleAttribute.setTransformToResourceScript(null); // Set null
		roleAttribute = roleSystemAttributeService.save(roleAttribute);

		List<Serializable> controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());
		assertNotNull(controlledAttributeValues);
		assertEquals(0, controlledAttributeValues.size());

		roleSystemAttributeService.delete(roleAttribute);

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());
		assertNotNull(controlledAttributeValues);
		assertEquals(0, controlledAttributeValues.size());
	}

	protected final SysRoleSystemDto createRoleSystem(SysSystemDto system, IdmRoleDto role) {
		return createRoleSystem(system, role, true);
	}

	protected SysRoleSystemDto createRoleSystem(SysSystemDto system, IdmRoleDto role, boolean assignsAccount) {
		return helper.createRoleSystem(role, system, AccountType.PERSONAL, assignsAccount);
	}

	@Test
	public void testAttribteControlledValueWithNullAfter() {
		String controlledValue = "test-" + System.currentTimeMillis();
		SysSystemDto system = createTestSystem();
		SysSystemMappingDto mapping = createTestMapping(system);
		IdmRoleDto role = helper.createRole();

		SysRoleSystemDto roleSystem = createRoleSystem(system, role);

		SysSchemaAttributeDto rightsSchemaAttribute = new SysSchemaAttributeDto();
		rightsSchemaAttribute.setObjectClass(mapping.getObjectClass());
		rightsSchemaAttribute.setName(RIGHTS_ATTRIBUTE);
		rightsSchemaAttribute.setMultivalued(true);
		rightsSchemaAttribute.setClassType(String.class.getName());
		rightsSchemaAttribute.setReadable(true);
		rightsSchemaAttribute.setUpdateable(true);

		rightsSchemaAttribute = schemaAttributeService.save(rightsSchemaAttribute);

		SysSystemAttributeMappingDto rightsAttribute = createSystemAttributeMapping(rightsSchemaAttribute, mapping, AttributeMappingStrategyType.MERGE);

		SysRoleSystemAttributeDto roleAttribute = new SysRoleSystemAttributeDto();
		roleAttribute.setName(RIGHTS_ATTRIBUTE);
		roleAttribute.setRoleSystem(roleSystem.getId());
		roleAttribute.setStrategyType(AttributeMappingStrategyType.MERGE);
		roleAttribute.setSystemAttributeMapping(rightsAttribute.getId());
		roleAttribute.setTransformToResourceScript("'" + controlledValue + "'");
		roleAttribute = roleSystemAttributeService.save(roleAttribute);

		List<Serializable> controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());
		assertNotNull(controlledAttributeValues);
		assertEquals(1, controlledAttributeValues.size());
		assertEquals(controlledValue, controlledAttributeValues.get(0));

		SysAttributeControlledValueFilter filter = new SysAttributeControlledValueFilter();
		filter.setHistoricValue(Boolean.TRUE);
		filter.setAttributeMappingId(rightsAttribute.getId());
		List<SysAttributeControlledValueDto> hitoricalValues = attributeControlledValueService.find(filter, null).getContent();
		assertEquals(0, hitoricalValues.size());

		roleAttribute.setTransformToResourceScript(null);
		roleAttribute = roleSystemAttributeService.save(roleAttribute);

		hitoricalValues = attributeControlledValueService.find(filter, null).getContent();
		assertEquals(1, hitoricalValues.size());
		assertEquals(controlledValue, hitoricalValues.get(0).getValue());

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());
		assertNotNull(controlledAttributeValues);
		assertEquals(0, controlledAttributeValues.size());

		roleSystemAttributeService.delete(roleAttribute);

		hitoricalValues = attributeControlledValueService.find(filter, null).getContent();
		assertEquals(1, hitoricalValues.size());
		assertEquals(controlledValue, hitoricalValues.get(0).getValue());
	}

	@Test
	public void testSkipMergeValueIfContractExcluded() {
		SysSystemDto system = createTestSystem();
		SysSystemMappingDto mapping = createTestMapping(system);
		IdmRoleDto roleOne = helper.createRole();
		IdmRoleDto roleTwo = helper.createRole();

		SysRoleSystemDto roleSystemOne = createRoleSystem(system, roleOne);
		SysRoleSystemDto roleSystemTwo = createRoleSystem(system, roleTwo);

		SysSchemaAttributeDto rightsSchemaAttribute = new SysSchemaAttributeDto();
		rightsSchemaAttribute.setObjectClass(mapping.getObjectClass());
		rightsSchemaAttribute.setName(RIGHTS_ATTRIBUTE);
		rightsSchemaAttribute.setMultivalued(true);
		rightsSchemaAttribute.setClassType(String.class.getName());
		rightsSchemaAttribute.setReadable(true);
		rightsSchemaAttribute.setUpdateable(true);

		rightsSchemaAttribute = schemaAttributeService.save(rightsSchemaAttribute);

		SysSystemAttributeMappingDto rightsAttribute = createSystemAttributeMapping(rightsSchemaAttribute, mapping, AttributeMappingStrategyType.MERGE);

		SysRoleSystemAttributeDto roleAttributeOne = new SysRoleSystemAttributeDto();
		roleAttributeOne.setName(RIGHTS_ATTRIBUTE);
		roleAttributeOne.setEntityAttribute(false);
		roleAttributeOne.setExtendedAttribute(false);
		roleAttributeOne.setRoleSystem(roleSystemOne.getId());
		roleAttributeOne.setStrategyType(AttributeMappingStrategyType.MERGE);
		roleAttributeOne.setSystemAttributeMapping(rightsAttribute.getId());
		roleAttributeOne.setTransformToResourceScript("return '" + ONE_VALUE + "';");
		roleAttributeOne = roleSystemAttributeService.saveInternal(roleAttributeOne);

		List<Serializable> controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		SysRoleSystemAttributeDto roleAttributeTwo = new SysRoleSystemAttributeDto();
		roleAttributeTwo.setName(RIGHTS_ATTRIBUTE);
		roleAttributeTwo.setEntityAttribute(false);
		roleAttributeTwo.setExtendedAttribute(false);
		roleAttributeTwo.setRoleSystem(roleSystemTwo.getId());
		roleAttributeTwo.setStrategyType(AttributeMappingStrategyType.MERGE);
		roleAttributeTwo.setSystemAttributeMapping(rightsAttribute.getId());
		roleAttributeTwo.setTransformToResourceScript("return '" + TWO_VALUE + "';");
		roleAttributeTwo = roleSystemAttributeService.saveInternal(roleAttributeTwo);

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(2, controlledAttributeValues.size());
		assertTrue(controlledAttributeValues.contains(ONE_VALUE));
		assertTrue(controlledAttributeValues.contains(TWO_VALUE));

		SysAttributeControlledValueFilter attributeControlledValueFilter = new SysAttributeControlledValueFilter();
		attributeControlledValueFilter.setAttributeMappingId(rightsAttribute.getId());
		attributeControlledValueFilter.setHistoricValue(Boolean.TRUE);

		List<Serializable> historicControlledValues = attributeControlledValueService //
				.find(attributeControlledValueFilter, null) //
				.getContent() //
				.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());

		assertNotNull(historicControlledValues);
		assertEquals(0, historicControlledValues.size());

		IdmIdentityDto identity = this.getHelper().createIdentity();
		IdmIdentityContractDto contract = this.getHelper().getPrimeContract(identity);
		// Set contract as excluded
		contract.setState(ContractState.EXCLUDED);
		contract = identityContractService.save(contract);
		assertTrue(contract.isExcluded());

		this.getHelper().createIdentityRole(identity, roleOne);
		this.getHelper().createIdentityRole(identity, roleTwo);

		SysProvisioningOperationFilter operationFilter = new SysProvisioningOperationFilter();
		operationFilter.setEntityIdentifier(identity.getId());

		List<SysProvisioningArchiveDto> archives = provisioningArchiveService.find(operationFilter, PageRequest.of(0, 1, new Sort(Direction.DESC, AbstractEntity_.created.getName()))).getContent();
		assertEquals(1, archives.size());

		SysProvisioningArchiveDto archive = archives.get(0);
		assertEquals(ProvisioningEventType.UPDATE, archive.getOperationType());
		ProvisioningContext provisioningContext = archive.getProvisioningContext();
		Map<ProvisioningAttributeDto, Object> accountObject = provisioningContext.getAccountObject();

		Entry<ProvisioningAttributeDto, Object> attributeEntry = accountObject.entrySet().stream() //
				.filter(entry -> RIGHTS_ATTRIBUTE.equals(entry.getKey().getSchemaAttributeName())).findFirst() //
				.get(); //

		List<?> values = (List<?>) attributeEntry.getValue();
		assertEquals(2, values.size());

		// Set attribute TWO to skip value if contract is excluded
		roleAttributeTwo.setSkipValueIfExcluded(true);
		roleAttributeTwo = roleSystemAttributeService.saveInternal(roleAttributeTwo);
		// Save identity -> execute provisioning
		identityService.save(identity);

		archives = provisioningArchiveService.find(operationFilter, PageRequest.of(0, 1, new Sort(Direction.DESC, AbstractEntity_.created.getName()))).getContent();
		assertEquals(1, archives.size());

		archive = archives.get(0);
		assertEquals(ProvisioningEventType.UPDATE, archive.getOperationType());
		provisioningContext = archive.getProvisioningContext();
		accountObject = provisioningContext.getAccountObject();

		attributeEntry = accountObject.entrySet().stream() //
				.filter(entry -> RIGHTS_ATTRIBUTE.equals(entry.getKey().getSchemaAttributeName())) //
				.findFirst() //
				.get(); //

		values = (List<?>) attributeEntry.getValue();
		assertEquals(1, values.size());
		assertEquals(ONE_VALUE, values.get(0));

		// Set contract as not excluded
		contract.setState(null);
		contract = identityContractService.save(contract);
		assertFalse(contract.isExcluded());

		// Save identity -> execute provisioning
		identityService.save(identity);

		archives = provisioningArchiveService.find(operationFilter, PageRequest.of(0, 1, new Sort(Direction.DESC, AbstractEntity_.created.getName()))).getContent();
		assertEquals(1, archives.size());

		archive = archives.get(0);
		assertEquals(ProvisioningEventType.UPDATE, archive.getOperationType());
		provisioningContext = archive.getProvisioningContext();
		accountObject = provisioningContext.getAccountObject();

		attributeEntry = accountObject.entrySet().stream() //
				.filter(entry -> RIGHTS_ATTRIBUTE.equals(entry.getKey().getSchemaAttributeName())) //
				.findFirst() //
				.get(); //

		values = (List<?>) attributeEntry.getValue();
		assertEquals(2, values.size());
	}

	@Test
	public void testSkipAuthoritativeMergeValueIfContractExcluded() {
		SysSystemDto system = createTestSystem();
		SysSystemMappingDto mapping = createTestMapping(system);
		IdmRoleDto roleOne = helper.createRole();
		IdmRoleDto roleTwo = helper.createRole();

		SysRoleSystemDto roleSystemOne = createRoleSystem(system, roleOne);
		SysRoleSystemDto roleSystemTwo = createRoleSystem(system, roleTwo);

		SysSchemaAttributeDto rightsSchemaAttribute = new SysSchemaAttributeDto();
		rightsSchemaAttribute.setObjectClass(mapping.getObjectClass());
		rightsSchemaAttribute.setName(RIGHTS_ATTRIBUTE);
		rightsSchemaAttribute.setMultivalued(true);
		rightsSchemaAttribute.setClassType(String.class.getName());
		rightsSchemaAttribute.setReadable(true);
		rightsSchemaAttribute.setUpdateable(true);

		rightsSchemaAttribute = schemaAttributeService.save(rightsSchemaAttribute);

		SysSystemAttributeMappingDto rightsAttribute = createSystemAttributeMapping(rightsSchemaAttribute, mapping, AttributeMappingStrategyType.AUTHORITATIVE_MERGE);

		SysRoleSystemAttributeDto roleAttributeOne = new SysRoleSystemAttributeDto();
		roleAttributeOne.setName(RIGHTS_ATTRIBUTE);
		roleAttributeOne.setEntityAttribute(false);
		roleAttributeOne.setExtendedAttribute(false);
		roleAttributeOne.setRoleSystem(roleSystemOne.getId());
		roleAttributeOne.setStrategyType(AttributeMappingStrategyType.AUTHORITATIVE_MERGE);
		roleAttributeOne.setSystemAttributeMapping(rightsAttribute.getId());
		roleAttributeOne.setTransformToResourceScript("return '" + ONE_VALUE + "';");
		roleAttributeOne = roleSystemAttributeService.saveInternal(roleAttributeOne);

		SysRoleSystemAttributeDto roleAttributeTwo = new SysRoleSystemAttributeDto();
		roleAttributeTwo.setName(RIGHTS_ATTRIBUTE);
		roleAttributeTwo.setEntityAttribute(false);
		roleAttributeTwo.setExtendedAttribute(false);
		roleAttributeTwo.setRoleSystem(roleSystemTwo.getId());
		roleAttributeTwo.setStrategyType(AttributeMappingStrategyType.AUTHORITATIVE_MERGE);
		roleAttributeTwo.setSystemAttributeMapping(rightsAttribute.getId());
		roleAttributeTwo.setTransformToResourceScript("return '" + TWO_VALUE + "';");
		roleAttributeTwo = roleSystemAttributeService.saveInternal(roleAttributeTwo);

		IdmIdentityDto identity = this.getHelper().createIdentity();
		IdmIdentityContractDto contract = this.getHelper().getPrimeContract(identity);
		// Set contract as excluded
		contract.setState(ContractState.EXCLUDED);
		contract = identityContractService.save(contract);
		assertTrue(contract.isExcluded());

		this.getHelper().createIdentityRole(identity, roleOne);
		this.getHelper().createIdentityRole(identity, roleTwo);

		SysProvisioningOperationFilter operationFilter = new SysProvisioningOperationFilter();
		operationFilter.setEntityIdentifier(identity.getId());

		List<SysProvisioningArchiveDto> archives = provisioningArchiveService.find(operationFilter, PageRequest.of(0, 1, new Sort(Direction.DESC, AbstractEntity_.created.getName()))).getContent();
		assertEquals(1, archives.size());

		SysProvisioningArchiveDto archive = archives.get(0);
		assertEquals(ProvisioningEventType.UPDATE, archive.getOperationType());
		ProvisioningContext provisioningContext = archive.getProvisioningContext();
		Map<ProvisioningAttributeDto, Object> accountObject = provisioningContext.getAccountObject();

		Entry<ProvisioningAttributeDto, Object> attributeEntry = accountObject.entrySet().stream() //
				.filter(entry -> RIGHTS_ATTRIBUTE.equals(entry.getKey().getSchemaAttributeName())).findFirst() //
				.get(); //

		List<?> values = (List<?>) attributeEntry.getValue();
		assertEquals(2, values.size());

		// Set attribute TWO to skip value if contract is excluded
		roleAttributeTwo.setSkipValueIfExcluded(true);
		roleAttributeTwo = roleSystemAttributeService.saveInternal(roleAttributeTwo);
		// Save identity -> execute provisioning
		identityService.save(identity);

		archives = provisioningArchiveService.find(operationFilter, PageRequest.of(0, 1, new Sort(Direction.DESC, AbstractEntity_.created.getName()))).getContent();
		assertEquals(1, archives.size());

		archive = archives.get(0);
		assertEquals(ProvisioningEventType.UPDATE, archive.getOperationType());
		provisioningContext = archive.getProvisioningContext();
		accountObject = provisioningContext.getAccountObject();

		attributeEntry = accountObject.entrySet().stream() //
				.filter(entry -> RIGHTS_ATTRIBUTE.equals(entry.getKey().getSchemaAttributeName())) //
				.findFirst() //
				.get(); //

		values = (List<?>) attributeEntry.getValue();
		assertEquals(1, values.size());
		assertEquals(ONE_VALUE, values.get(0));

		// Set contract as not excluded
		contract.setState(null);
		contract = identityContractService.save(contract);
		assertFalse(contract.isExcluded());

		// Save identity -> execute provisioning
		identityService.save(identity);

		archives = provisioningArchiveService.find(operationFilter, PageRequest.of(0, 1, new Sort(Direction.DESC, AbstractEntity_.created.getName()))).getContent();
		assertEquals(1, archives.size());

		archive = archives.get(0);
		assertEquals(ProvisioningEventType.UPDATE, archive.getOperationType());
		provisioningContext = archive.getProvisioningContext();
		accountObject = provisioningContext.getAccountObject();

		attributeEntry = accountObject.entrySet().stream() //
				.filter(entry -> RIGHTS_ATTRIBUTE.equals(entry.getKey().getSchemaAttributeName())) //
				.findFirst() //
				.get(); //

		values = (List<?>) attributeEntry.getValue();
		assertEquals(2, values.size());
	}

	@Test
	public void testSkipMergeValueIfTwoContractsExcluded() {
		SysSystemDto system = createTestSystem();
		SysSystemMappingDto mapping = createTestMapping(system);
		IdmRoleDto roleOne = helper.createRole();
		IdmRoleDto roleTwo = helper.createRole();

		SysRoleSystemDto roleSystemOne = createRoleSystem(system, roleOne);
		SysRoleSystemDto roleSystemTwo = createRoleSystem(system, roleTwo);

		SysSchemaAttributeDto rightsSchemaAttribute = new SysSchemaAttributeDto();
		rightsSchemaAttribute.setObjectClass(mapping.getObjectClass());
		rightsSchemaAttribute.setName(RIGHTS_ATTRIBUTE);
		rightsSchemaAttribute.setMultivalued(true);
		rightsSchemaAttribute.setClassType(String.class.getName());
		rightsSchemaAttribute.setReadable(true);
		rightsSchemaAttribute.setUpdateable(true);

		rightsSchemaAttribute = schemaAttributeService.save(rightsSchemaAttribute);

		SysSystemAttributeMappingDto rightsAttribute = createSystemAttributeMapping(rightsSchemaAttribute, mapping, AttributeMappingStrategyType.MERGE);

		SysRoleSystemAttributeDto roleAttributeOne = new SysRoleSystemAttributeDto();
		roleAttributeOne.setName(RIGHTS_ATTRIBUTE);
		roleAttributeOne.setEntityAttribute(false);
		roleAttributeOne.setExtendedAttribute(false);
		roleAttributeOne.setRoleSystem(roleSystemOne.getId());
		roleAttributeOne.setStrategyType(AttributeMappingStrategyType.MERGE);
		roleAttributeOne.setSystemAttributeMapping(rightsAttribute.getId());
		roleAttributeOne.setTransformToResourceScript("return '" + ONE_VALUE + "';");
		roleAttributeOne = roleSystemAttributeService.saveInternal(roleAttributeOne);

		List<Serializable> controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		SysRoleSystemAttributeDto roleAttributeTwo = new SysRoleSystemAttributeDto();
		roleAttributeTwo.setName(RIGHTS_ATTRIBUTE);
		roleAttributeTwo.setEntityAttribute(false);
		roleAttributeTwo.setExtendedAttribute(false);
		roleAttributeTwo.setRoleSystem(roleSystemTwo.getId());
		roleAttributeTwo.setStrategyType(AttributeMappingStrategyType.MERGE);
		roleAttributeTwo.setSystemAttributeMapping(rightsAttribute.getId());
		roleAttributeTwo.setTransformToResourceScript("return '" + TWO_VALUE + "';");
		roleAttributeTwo = roleSystemAttributeService.saveInternal(roleAttributeTwo);

		controlledAttributeValues = attributeMappingService.getControlledAttributeValues(system.getId(), mapping.getEntityType(), RIGHTS_ATTRIBUTE, mapping.getId());

		assertNotNull(controlledAttributeValues);
		assertEquals(2, controlledAttributeValues.size());
		assertTrue(controlledAttributeValues.contains(ONE_VALUE));
		assertTrue(controlledAttributeValues.contains(TWO_VALUE));

		SysAttributeControlledValueFilter attributeControlledValueFilter = new SysAttributeControlledValueFilter();
		attributeControlledValueFilter.setAttributeMappingId(rightsAttribute.getId());
		attributeControlledValueFilter.setHistoricValue(Boolean.TRUE);

		List<Serializable> historicControlledValues = attributeControlledValueService //
				.find(attributeControlledValueFilter, null) //
				.getContent() //
				.stream() //
				.map(SysAttributeControlledValueDto::getValue) //
				.collect(Collectors.toList());

		assertNotNull(historicControlledValues);
		assertEquals(0, historicControlledValues.size());

		IdmIdentityDto identity = this.getHelper().createIdentity();
		IdmIdentityContractDto contractOne = this.getHelper().getPrimeContract(identity);
		IdmIdentityContractDto contractTwo = this.getHelper().createContract(identity);
		// Set contract one as excluded
		contractOne.setState(ContractState.EXCLUDED);
		contractOne = identityContractService.save(contractOne);
		assertTrue(contractOne.isExcluded());

		// Set contract two as not excluded
		contractTwo.setState(null);
		contractTwo = identityContractService.save(contractTwo);
		assertFalse(contractTwo.isExcluded());

		this.getHelper().createIdentityRole(identity, roleOne);
		// Role two is assigned by contract one and contract two!
		this.getHelper().createIdentityRole(contractOne, roleTwo);
		this.getHelper().createIdentityRole(contractTwo, roleTwo);

		SysProvisioningOperationFilter operationFilter = new SysProvisioningOperationFilter();
		operationFilter.setEntityIdentifier(identity.getId());

		List<SysProvisioningArchiveDto> archives = provisioningArchiveService.find(operationFilter, PageRequest.of(0, 1, new Sort(Direction.DESC, AbstractEntity_.created.getName()))).getContent();
		assertEquals(1, archives.size());

		SysProvisioningArchiveDto archive = archives.get(0);
		assertEquals(ProvisioningEventType.UPDATE, archive.getOperationType());
		ProvisioningContext provisioningContext = archive.getProvisioningContext();
		Map<ProvisioningAttributeDto, Object> accountObject = provisioningContext.getAccountObject();

		Entry<ProvisioningAttributeDto, Object> attributeEntry = accountObject.entrySet().stream() //
				.filter(entry -> RIGHTS_ATTRIBUTE.equals(entry.getKey().getSchemaAttributeName())).findFirst() //
				.get(); //

		List<?> values = (List<?>) attributeEntry.getValue();
		assertEquals(2, values.size());

		// Set attribute TWO to skip value if contract is excluded
		roleAttributeTwo.setSkipValueIfExcluded(true);
		roleAttributeTwo = roleSystemAttributeService.saveInternal(roleAttributeTwo);

		// Save identity -> execute provisioning
		identityService.save(identity);

		archives = provisioningArchiveService.find(operationFilter, PageRequest.of(0, 1, new Sort(Direction.DESC, AbstractEntity_.created.getName()))).getContent();
		assertEquals(1, archives.size());

		archive = archives.get(0);
		assertEquals(ProvisioningEventType.UPDATE, archive.getOperationType());
		provisioningContext = archive.getProvisioningContext();
		accountObject = provisioningContext.getAccountObject();

		attributeEntry = accountObject.entrySet().stream() //
				.filter(entry -> RIGHTS_ATTRIBUTE.equals(entry.getKey().getSchemaAttributeName())) //
				.findFirst() //
				.get(); //

		values = (List<?>) attributeEntry.getValue();
		// Skip was not executed, because there is two contracts assigned same role and
		// second contract is not excluded
		assertEquals(2, values.size());

		// Set contract two as excluded
		contractTwo.setState(ContractState.EXCLUDED);
		contractTwo = identityContractService.save(contractTwo);
		assertTrue(contractTwo.isExcluded());

		// Save identity -> execute provisioning
		identityService.save(identity);

		archives = provisioningArchiveService.find(operationFilter, PageRequest.of(0, 1, new Sort(Direction.DESC, AbstractEntity_.created.getName()))).getContent();
		assertEquals(1, archives.size());

		archive = archives.get(0);
		assertEquals(ProvisioningEventType.UPDATE, archive.getOperationType());
		provisioningContext = archive.getProvisioningContext();
		accountObject = provisioningContext.getAccountObject();

		attributeEntry = accountObject.entrySet().stream() //
				.filter(entry -> RIGHTS_ATTRIBUTE.equals(entry.getKey().getSchemaAttributeName())) //
				.findFirst() //
				.get(); //

		values = (List<?>) attributeEntry.getValue();
		assertEquals(1, values.size());
		assertEquals(ONE_VALUE, values.get(0));

		// Set contract as not excluded
		contractOne.setState(null);
		contractOne = identityContractService.save(contractOne);
		assertFalse(contractOne.isExcluded());

		// Save identity -> execute provisioning
		identityService.save(identity);

		archives = provisioningArchiveService.find(operationFilter, PageRequest.of(0, 1, new Sort(Direction.DESC, AbstractEntity_.created.getName()))).getContent();
		assertEquals(1, archives.size());

		archive = archives.get(0);
		assertEquals(ProvisioningEventType.UPDATE, archive.getOperationType());
		provisioningContext = archive.getProvisioningContext();
		accountObject = provisioningContext.getAccountObject();

		attributeEntry = accountObject.entrySet().stream() //
				.filter(entry -> RIGHTS_ATTRIBUTE.equals(entry.getKey().getSchemaAttributeName())) //
				.findFirst() //
				.get(); //

		values = (List<?>) attributeEntry.getValue();
		assertEquals(2, values.size());
	}

	protected abstract AbstractConceptRoleRequestDto createConceptRoleRequest(IdmRoleRequestDto request, IdmRoleDto role, UUID ownerId);

	protected abstract AbstractDto createOwner();

	protected abstract ApplicantDto getApplicant(AbstractDto owner);
}
