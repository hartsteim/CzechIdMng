package eu.bcvsolutions.idm.core.eav.service.impl;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import org.testng.collections.Lists;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.bcvsolutions.idm.core.api.config.domain.EventConfiguration;
import eu.bcvsolutions.idm.core.api.config.domain.RoleConfiguration;
import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleComparison;
import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleType;
import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.domain.PriorityType;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmContractPositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.dto.projection.IdmIdentityProjectionDto;
import eu.bcvsolutions.idm.core.api.exception.ForbiddenEntityException;
import eu.bcvsolutions.idm.core.api.exception.InvalidFormException;
import eu.bcvsolutions.idm.core.api.rest.BaseController;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.FormDefinitionAttributes;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormProjectionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.event.IdentityProjectionEvent;
import eu.bcvsolutions.idm.core.eav.api.event.IdentityProjectionEvent.IdentityProjectionEventType;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormProjectionService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmIdentityFormValue;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.evaluator.eav.IdentityFormValueEvaluator;
import eu.bcvsolutions.idm.core.security.evaluator.identity.SelfIdentityEvaluator;
import eu.bcvsolutions.idm.test.api.AbstractRestTest;
import eu.bcvsolutions.idm.test.api.TestHelper;

/**
 * Identity projection tests.
 * 
 * @author Radek Tomiška
 *
 */
public class DefaultIdentityProjectionManagerIntegrationTest extends AbstractRestTest {

	@Autowired private ApplicationContext context;
	@Autowired private FormService formService;
	@Autowired private ObjectMapper mapper;
	@Autowired private IdmFormProjectionService projectionService;
	@Autowired private LookupService lookupService;
	@Autowired private IdmIdentityService identityService;
	@Autowired private IdmIdentityRoleService identityRoleService;
	@Autowired private IdmIdentityContractService contractService;
	@Autowired private IdmRoleRequestService roleRequestService;
	@Autowired private EventConfiguration eventConfiguration;
	@Autowired private RoleConfiguration roleConfiguration;
	//
	private DefaultIdentityProjectionManager manager;
	//
	// FIXME: move to api (workflow config constant)
	private static final String APPROVE_BY_SECURITY_ENABLE = "idm.sec.core.wf.approval.security.enabled";
	private static final String APPROVE_BY_MANAGER_ENABLE = "idm.sec.core.wf.approval.manager.enabled";
	private static final String APPROVE_BY_USERMANAGER_ENABLE = "idm.sec.core.wf.approval.usermanager.enabled";
	private static final String APPROVE_BY_HELPDESK_ENABLE = "idm.sec.core.wf.approval.helpdesk.enabled";

	@Before
	public void init() {
		manager = context.getAutowireCapableBeanFactory().createBean(DefaultIdentityProjectionManager.class);
		//
		// FIXME: find the place, where is this forgotten
		getHelper().setConfigurationValue(APPROVE_BY_SECURITY_ENABLE, false);
		getHelper().setConfigurationValue(APPROVE_BY_MANAGER_ENABLE, false);
		getHelper().setConfigurationValue(APPROVE_BY_HELPDESK_ENABLE, false);
		getHelper().setConfigurationValue(APPROVE_BY_USERMANAGER_ENABLE, false);
	}
	
	@Test
	@Transactional
	public void testSaveAndGetSimpleIdentity() {
		IdmIdentityDto identity = new IdmIdentityDto(getHelper().createName());
		IdmIdentityProjectionDto projection = new IdmIdentityProjectionDto(identity);
		
		IdmIdentityProjectionDto createdProjection = manager
				.publish(new IdentityProjectionEvent(IdentityProjectionEventType.CREATE, projection))
				.getContent();
		//
		Assert.assertNotNull(createdProjection);
		Assert.assertNotNull(createdProjection.getId());
		Assert.assertEquals(createdProjection.getId(), createdProjection.getIdentity().getId());
		Assert.assertEquals(identity.getUsername(), createdProjection.getIdentity().getUsername());
		Assert.assertNull(createdProjection.getContract());
		Assert.assertTrue(createdProjection.getOtherContracts().isEmpty());
		Assert.assertTrue(createdProjection.getOtherPositions().isEmpty());
		Assert.assertTrue(createdProjection.getIdentityRoles().isEmpty());
	}
	
	@Test
	@Transactional
	public void testSaveAndGetFullProjectionGreenLine() {
		loginAsAdmin(); // role request implementer is needed
		Assert.assertFalse(eventConfiguration.isAsynchronous());
		//
		try {
			// prepare eav definition
			IdmFormAttributeDto attributeOne = new IdmFormAttributeDto(getHelper().createName());
			attributeOne.setPersistentType(PersistentType.SHORTTEXT);
			IdmFormAttributeDto attributeTwo = new IdmFormAttributeDto(getHelper().createName());
			attributeTwo.setPersistentType(PersistentType.SHORTTEXT);
			IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentityDto.class, getHelper().createName(), Lists.newArrayList(attributeOne, attributeTwo));
			attributeOne = formDefinitionOne.getMappedAttributeByCode(attributeOne.getCode());
			attributeTwo = formDefinitionOne.getMappedAttributeByCode(attributeTwo.getCode());
			IdmFormAttributeDto attributeThree = new IdmFormAttributeDto(getHelper().createName());
			attributeThree.setPersistentType(PersistentType.SHORTTEXT);
			IdmFormDefinitionDto formDefinitionTwo = formService.createDefinition(IdmIdentityDto.class, getHelper().createName(), Lists.newArrayList(attributeThree));
			attributeThree = formDefinitionTwo.getMappedAttributeByCode(attributeThree.getCode());
			IdmFormAttributeDto attributeContract = new IdmFormAttributeDto(getHelper().createName());
			attributeContract.setPersistentType(PersistentType.SHORTTEXT);
			IdmFormDefinitionDto formDefinitionContract = formService.createDefinition(
					IdmIdentityContractDto.class, getHelper().createName(), Lists.newArrayList(attributeContract));
			attributeContract = formDefinitionContract.getMappedAttributeByCode(attributeContract.getCode());
			
			//
			IdmIdentityDto identity = new IdmIdentityDto(getHelper().createName());
			IdmIdentityProjectionDto projection = new IdmIdentityProjectionDto(identity);
			//
			// set eav 
			IdmFormInstanceDto instanceOne = new IdmFormInstanceDto();
			instanceOne.setFormDefinition(formDefinitionOne);
			IdmFormValueDto valueOne = new IdmFormValueDto(attributeOne);
			valueOne.setValue(getHelper().createName());
			IdmFormValueDto valueTwo = new IdmFormValueDto(attributeTwo);
			valueTwo.setValue(getHelper().createName());
			instanceOne.setValues(Lists.newArrayList(valueOne, valueTwo));
			IdmFormInstanceDto instanceTwo = new IdmFormInstanceDto();
			instanceTwo.setFormDefinition(formDefinitionTwo);
			IdmFormValueDto valueThree = new IdmFormValueDto(attributeThree);
			valueThree.setValue(getHelper().createName());
			instanceTwo.setValues(Lists.newArrayList(valueThree));
			identity.setEavs(Lists.newArrayList(instanceOne, instanceTwo));
			//
			// set contract
			IdmIdentityContractDto primeContract = new IdmIdentityContractDto();
			primeContract.setMain(true);
			primeContract.setWorkPosition(getHelper().createTreeNode().getId());
			primeContract.setPosition(getHelper().createName());
			primeContract.setValidFrom(LocalDate.now().minus(1l, ChronoUnit.DAYS));
			primeContract.setValidFrom(LocalDate.now().plus(1l, ChronoUnit.DAYS));
			projection.setContract(primeContract);
			IdmFormInstanceDto instanceContract = new IdmFormInstanceDto();
			instanceContract.setFormDefinition(formDefinitionContract);
			IdmFormValueDto valueContract = new IdmFormValueDto(attributeContract);
			valueContract.setValue(getHelper().createName());
			instanceContract.setValues(Lists.newArrayList(valueContract));
			primeContract.setEavs(Lists.newArrayList(instanceContract));
			//
			// set other contract
			IdmIdentityContractDto otherContractOne = new IdmIdentityContractDto();
			otherContractOne.setWorkPosition(getHelper().createTreeNode().getId());
			otherContractOne.setPosition(getHelper().createName());
			IdmIdentityContractDto otherContractTwo = new IdmIdentityContractDto(UUID.randomUUID()); // preset uuid
			otherContractTwo.setWorkPosition(getHelper().createTreeNode().getId());
			otherContractTwo.setPosition(getHelper().createName());
			projection.setOtherContracts(Lists.newArrayList(otherContractOne, otherContractTwo));
			//
			// set other contract position
			IdmContractPositionDto positionOne = new IdmContractPositionDto();
			positionOne.setWorkPosition(getHelper().createTreeNode().getId());
			positionOne.setPosition(getHelper().createName());
			IdmContractPositionDto positionTwo = new IdmContractPositionDto();
			positionTwo.setWorkPosition(getHelper().createTreeNode().getId());
			positionTwo.setPosition(getHelper().createName());
			positionTwo.setIdentityContract(otherContractTwo.getId());
			projection.setOtherPositions(Lists.newArrayList(positionOne, positionTwo));
			//
			// set assigned roles
			IdmRoleDto roleOne = getHelper().createRole();
			IdmRoleDto roleTwo = getHelper().createRole();
			IdmIdentityRoleDto identityRoleOne = new IdmIdentityRoleDto();
			identityRoleOne.setRole(roleOne.getId());
			identityRoleOne.setValidFrom(LocalDate.now().plus(2l, ChronoUnit.DAYS));
			IdmIdentityRoleDto identityRoleTwo = new IdmIdentityRoleDto();
			identityRoleTwo.setRole(roleTwo.getId());
			identityRoleTwo.setIdentityContract(otherContractTwo.getId());
			projection.setIdentityRoles(Lists.newArrayList(identityRoleOne, identityRoleTwo));
			//
			IdentityProjectionEvent identityProjectionEvent = new IdentityProjectionEvent(IdentityProjectionEventType.CREATE, projection);
			identityProjectionEvent.setPriority(PriorityType.IMMEDIATE);
			projection = manager
					.publish(identityProjectionEvent)
					.getContent();
			IdmIdentityProjectionDto createdProjection = manager.get(projection);
			//
			Assert.assertNotNull(createdProjection);
			Assert.assertNotNull(createdProjection.getId());
			Assert.assertEquals(createdProjection.getId(), createdProjection.getIdentity().getId());
			// eavs
			Assert.assertEquals(valueOne.getValue(), createdProjection
				.getIdentity()
				.getEavs()
				.stream()
				.filter(i -> i.getFormDefinition().getId().equals(formDefinitionOne.getId()))
				.findFirst()
				.get()
				.toSinglePersistentValue(attributeOne.getCode()));
			Assert.assertEquals(valueTwo.getValue(), createdProjection
					.getIdentity()
					.getEavs()
					.stream()
					.filter(i -> i.getFormDefinition().getId().equals(formDefinitionOne.getId()))
					.findFirst()
					.get()
					.toSinglePersistentValue(attributeTwo.getCode()));
			Assert.assertEquals(valueThree.getValue(), createdProjection
					.getIdentity()
					.getEavs()
					.stream()
					.filter(i -> i.getFormDefinition().getId().equals(formDefinitionTwo.getId()))
					.findFirst()
					.get()
					.toSinglePersistentValue(attributeThree.getCode()));
			// prime contract
			IdmIdentityContractDto createdPrimeContract = createdProjection.getContract();
			Assert.assertEquals(primeContract.getWorkPosition(), createdPrimeContract.getWorkPosition());
			Assert.assertEquals(primeContract.getPosition(), createdPrimeContract.getPosition());
			Assert.assertEquals(primeContract.getValidFrom(), createdPrimeContract.getValidFrom());
			Assert.assertEquals(primeContract.getValidTill(), createdPrimeContract.getValidTill());
			Assert.assertEquals(createdProjection.getIdentity().getId(), createdPrimeContract.getIdentity());
			// eavs
			Assert.assertEquals(valueContract.getValue(), createdProjection
				.getContract()
				.getEavs()
				.stream()
				.filter(i -> i.getFormDefinition().getId().equals(formDefinitionContract.getId()))
				.findFirst()
				.get()
				.toSinglePersistentValue(attributeContract.getCode()));
			// other contract
			Assert.assertEquals(2, createdProjection.getOtherContracts().size());
			Assert.assertTrue(createdProjection.getOtherContracts().stream().anyMatch(c -> {
				return c.getWorkPosition().equals(otherContractOne.getWorkPosition()) 
						&& c.getPosition().equals(otherContractOne.getPosition());
			}));
			Assert.assertTrue(createdProjection.getOtherContracts().stream().anyMatch(c -> {
				return c.getWorkPosition().equals(otherContractTwo.getWorkPosition()) 
						&& c.getPosition().equals(otherContractTwo.getPosition())
						&& c.getId().equals(otherContractTwo.getId()); // preserve id
			}));
			// other position
			Assert.assertEquals(2, createdProjection.getOtherPositions().size());
			Assert.assertTrue(createdProjection.getOtherPositions().stream().anyMatch(p -> {
				return p.getWorkPosition().equals(positionOne.getWorkPosition()) 
						&& p.getPosition().equals(positionOne.getPosition())
						&& p.getIdentityContract().equals(createdPrimeContract.getId());
			}));
			Assert.assertTrue(createdProjection.getOtherPositions().stream().anyMatch(p -> {
				return p.getWorkPosition().equals(positionTwo.getWorkPosition()) 
						&& p.getPosition().equals(positionTwo.getPosition())
						&& p.getIdentityContract().equals(positionTwo.getIdentityContract());
			}));
			// assigned roles
			// check directly by service
			IdmRoleRequestFilter roleRequestFilter = new IdmRoleRequestFilter();
			roleRequestFilter.setApplicantId(createdProjection.getIdentity().getId());
			List<IdmRoleRequestDto> roleRequests = roleRequestService.find(roleRequestFilter, null).getContent();
			Assert.assertFalse(roleRequests.isEmpty());
			Assert.assertTrue(roleRequests.stream().allMatch(r -> r.getState() == RoleRequestState.EXECUTED));
			List<IdmIdentityRoleDto> assignedRoles = identityRoleService.findAllByIdentity(createdProjection.getIdentity().getId());
			Assert.assertEquals(2, assignedRoles.size());
			Assert.assertTrue(assignedRoles.stream().anyMatch(ir -> {
				return ir.getRole().equals(roleOne.getId())
						&& ir.getValidFrom().equals(identityRoleOne.getValidFrom())
						&& ir.getIdentityContract().equals(createdPrimeContract.getId());
			}));
			Assert.assertTrue(assignedRoles.stream().anyMatch(ir -> {
				return ir.getRole().equals(roleTwo.getId())
						&& ir.getIdentityContract().equals(otherContractTwo.getId());
			}));
			// check by projection
			Assert.assertEquals(2, createdProjection.getIdentityRoles().size());
			Assert.assertTrue(createdProjection.getIdentityRoles().stream().anyMatch(ir -> {
				return ir.getRole().equals(roleOne.getId())
						&& ir.getValidFrom().equals(identityRoleOne.getValidFrom())
						&& ir.getIdentityContract().equals(createdPrimeContract.getId());
			}));
			Assert.assertTrue(createdProjection.getIdentityRoles().stream().anyMatch(ir -> {
				return ir.getRole().equals(roleTwo.getId())
						&& ir.getIdentityContract().equals(otherContractTwo.getId());
			}));
			//
			// simple update
			String newUsername = getHelper().createName();
			createdProjection.getIdentity().setUsername(newUsername);
			//
			IdmIdentityProjectionDto updatedProjection = manager
					.publish(new IdentityProjectionEvent(IdentityProjectionEventType.UPDATE, createdProjection))
					.getContent();
			// IdmIdentityProjectionDto updatedProjection = manager.get(projection);
			//
			Assert.assertNotNull(updatedProjection);
			Assert.assertNotNull(updatedProjection.getId());
			Assert.assertEquals(createdProjection.getId(), updatedProjection.getIdentity().getId());
			Assert.assertEquals(newUsername, updatedProjection.getIdentity().getUsername());
			// eavs
			Assert.assertEquals(valueOne.getValue(), updatedProjection
				.getIdentity()
				.getEavs()
				.stream()
				.filter(i -> i.getFormDefinition().getId().equals(formDefinitionOne.getId()))
				.findFirst()
				.get()
				.toSinglePersistentValue(attributeOne.getCode()));
			Assert.assertEquals(valueTwo.getValue(), updatedProjection
					.getIdentity()
					.getEavs()
					.stream()
					.filter(i -> i.getFormDefinition().getId().equals(formDefinitionOne.getId()))
					.findFirst()
					.get()
					.toSinglePersistentValue(attributeTwo.getCode()));
			Assert.assertEquals(valueThree.getValue(), updatedProjection
					.getIdentity()
					.getEavs()
					.stream()
					.filter(i -> i.getFormDefinition().getId().equals(formDefinitionTwo.getId()))
					.findFirst()
					.get()
					.toSinglePersistentValue(attributeThree.getCode()));
			// prime contract
			IdmIdentityContractDto updatedPrimeContract = updatedProjection.getContract();
			Assert.assertEquals(primeContract.getWorkPosition(), updatedPrimeContract.getWorkPosition());
			Assert.assertEquals(primeContract.getPosition(), updatedPrimeContract.getPosition());
			Assert.assertEquals(primeContract.getValidFrom(), updatedPrimeContract.getValidFrom());
			Assert.assertEquals(primeContract.getValidTill(), updatedPrimeContract.getValidTill());
			Assert.assertEquals(updatedProjection.getIdentity().getId(), updatedPrimeContract.getIdentity());
			Assert.assertFalse(updatedPrimeContract.getControlledBySlices());
			// other contract
			Assert.assertEquals(2, updatedProjection.getOtherContracts().size());
			Assert.assertTrue(updatedProjection.getOtherContracts().stream().anyMatch(c -> {
				return c.getWorkPosition().equals(otherContractOne.getWorkPosition()) && c.getPosition().equals(otherContractOne.getPosition());
			}));
			Assert.assertTrue(updatedProjection.getOtherContracts().stream().anyMatch(c -> {
				return c.getWorkPosition().equals(otherContractTwo.getWorkPosition()) 
						&& c.getPosition().equals(otherContractTwo.getPosition())
						&& c.getId().equals(otherContractTwo.getId()); // preserve id
			}));
			Assert.assertTrue(updatedProjection.getOtherContracts().stream().allMatch(c -> {
				return !c.getControlledBySlices();
			}));
			// other position
			Assert.assertEquals(2, updatedProjection.getOtherPositions().size());
			Assert.assertTrue(updatedProjection.getOtherPositions().stream().anyMatch(p -> {
				return p.getWorkPosition().equals(positionOne.getWorkPosition()) 
						&& p.getPosition().equals(positionOne.getPosition())
						&& p.getIdentityContract().equals(updatedPrimeContract.getId());
			}));
			Assert.assertTrue(updatedProjection.getOtherPositions().stream().anyMatch(p -> {
				return p.getWorkPosition().equals(positionTwo.getWorkPosition()) 
						&& p.getPosition().equals(positionTwo.getPosition())
						&& p.getIdentityContract().equals(positionTwo.getIdentityContract());
			}));
			// assigned roles
			Assert.assertEquals(2, updatedProjection.getIdentityRoles().size());
			Assert.assertTrue(updatedProjection.getIdentityRoles().stream().anyMatch(ir -> {
				return ir.getRole().equals(roleOne.getId())
						&& ir.getValidFrom().equals(identityRoleOne.getValidFrom())
						&& ir.getIdentityContract().equals(updatedPrimeContract.getId());
			}));
			Assert.assertTrue(updatedProjection.getIdentityRoles().stream().anyMatch(ir -> {
				return ir.getRole().equals(roleTwo.getId())
						&& ir.getIdentityContract().equals(otherContractTwo.getId());
			}));
			
		} finally {
			logout();
		}
	}
	
	@Test
	@Transactional
	public void testSaveAndGetSimpleIdentityByRest() throws Exception {
		IdmIdentityDto identity = new IdmIdentityDto(getHelper().createName());
		IdmIdentityProjectionDto projection = new IdmIdentityProjectionDto(identity);
		projection.setContract(new IdmIdentityContractDto());
		//
		String response = getMockMvc().perform(post(BaseController.BASE_PATH + "/identity-projection")
	    		.with(authentication(getAdminAuthentication()))
	    		.content(getMapper().writeValueAsString(projection))
	            .contentType(TestHelper.HAL_CONTENT_TYPE))
				.andExpect(status().is2xxSuccessful())
	            .andExpect(content().contentType(TestHelper.HAL_CONTENT_TYPE))
	            .andReturn()
	            .getResponse()
	            .getContentAsString();
		//
		IdmIdentityProjectionDto createdProjection = getMapper().readValue(response, IdmIdentityProjectionDto.class);
		//
		Assert.assertNotNull(createdProjection);
		Assert.assertNotNull(createdProjection.getId());
		Assert.assertEquals(createdProjection.getId(), createdProjection.getIdentity().getId());
		Assert.assertEquals(identity.getUsername(), createdProjection.getIdentity().getUsername());
		Assert.assertNotNull(createdProjection.getContract());
		Assert.assertNotNull(createdProjection.getContract().getId());
		Assert.assertTrue(createdProjection.getOtherContracts().isEmpty());
		Assert.assertTrue(createdProjection.getOtherPositions().isEmpty());
		Assert.assertTrue(createdProjection.getIdentityRoles().isEmpty());
		//
		response = getMockMvc().perform(get(BaseController.BASE_PATH + "/identity-projection/" + createdProjection.getIdentity().getUsername())
	    		.with(authentication(getAdminAuthentication()))
	            .contentType(TestHelper.HAL_CONTENT_TYPE))
				.andExpect(status().isOk())
	            .andExpect(content().contentType(TestHelper.HAL_CONTENT_TYPE))
	            .andReturn()
	            .getResponse()
	            .getContentAsString();
		//
		createdProjection = getMapper().readValue(response, IdmIdentityProjectionDto.class);
		//
		Assert.assertNotNull(createdProjection);
		Assert.assertNotNull(createdProjection.getId());
		Assert.assertEquals(createdProjection.getId(), createdProjection.getIdentity().getId());
		Assert.assertEquals(identity.getUsername(), createdProjection.getIdentity().getUsername());
		Assert.assertNotNull(createdProjection.getContract());
		Assert.assertNotNull(createdProjection.getContract().getId());
		Assert.assertTrue(createdProjection.getOtherContracts().isEmpty());
		Assert.assertTrue(createdProjection.getOtherPositions().isEmpty());
		Assert.assertTrue(createdProjection.getIdentityRoles().isEmpty());
	}
	
	@Test
	@Transactional
	public void testDeleteOtherContractAndPosition() {
		//
		IdmIdentityDto identity = new IdmIdentityDto(getHelper().createName());
		IdmIdentityProjectionDto projection = new IdmIdentityProjectionDto(identity);
		//
		// set contract
		IdmIdentityContractDto primeContract = new IdmIdentityContractDto();
		primeContract.setMain(true);
		primeContract.setWorkPosition(getHelper().createTreeNode().getId());
		primeContract.setPosition(getHelper().createName());
		primeContract.setValidFrom(LocalDate.now().minus(1l, ChronoUnit.DAYS));
		primeContract.setValidFrom(LocalDate.now().plus(1l, ChronoUnit.DAYS));
		projection.setContract(primeContract);
		//
		// set other contract
		IdmIdentityContractDto otherContractOne = new IdmIdentityContractDto();
		otherContractOne.setWorkPosition(getHelper().createTreeNode().getId());
		otherContractOne.setPosition(getHelper().createName());
		IdmIdentityContractDto otherContractTwo = new IdmIdentityContractDto(UUID.randomUUID()); // preset uuid
		otherContractTwo.setWorkPosition(getHelper().createTreeNode().getId());
		otherContractTwo.setPosition(getHelper().createName());
		projection.setOtherContracts(Lists.newArrayList(otherContractOne, otherContractTwo));
		//
		// set other contract position
		IdmContractPositionDto positionOne = new IdmContractPositionDto();
		positionOne.setWorkPosition(getHelper().createTreeNode().getId());
		positionOne.setPosition(getHelper().createName());
		IdmContractPositionDto positionTwo = new IdmContractPositionDto();
		positionTwo.setWorkPosition(getHelper().createTreeNode().getId());
		positionTwo.setPosition(getHelper().createName());
		positionTwo.setIdentityContract(otherContractTwo.getId());
		projection.setOtherPositions(Lists.newArrayList(positionOne, positionTwo));
		
		IdmIdentityProjectionDto createdProjection = manager
				.publish(new IdentityProjectionEvent(IdentityProjectionEventType.CREATE, projection))
				.getContent();
		
		// other contract
		Assert.assertEquals(2, createdProjection.getOtherContracts().size());
		Assert.assertTrue(createdProjection.getOtherContracts().stream().anyMatch(c -> {
			return c.getWorkPosition().equals(otherContractOne.getWorkPosition()) && c.getPosition().equals(otherContractOne.getPosition());
		}));
		Assert.assertTrue(createdProjection.getOtherContracts().stream().anyMatch(c -> {
			return c.getWorkPosition().equals(otherContractTwo.getWorkPosition()) 
					&& c.getPosition().equals(otherContractTwo.getPosition())
					&& c.getId().equals(otherContractTwo.getId()); // preserve id
		}));
		// other position
		Assert.assertEquals(2, createdProjection.getOtherPositions().size());
		Assert.assertTrue(createdProjection.getOtherPositions().stream().anyMatch(p -> {
			return p.getWorkPosition().equals(positionOne.getWorkPosition()) 
					&& p.getPosition().equals(positionOne.getPosition())
					&& p.getIdentityContract().equals(createdProjection.getContract().getId());
		}));
		Assert.assertTrue(createdProjection.getOtherPositions().stream().anyMatch(p -> {
			return p.getWorkPosition().equals(positionTwo.getWorkPosition()) 
					&& p.getPosition().equals(positionTwo.getPosition())
					&& p.getIdentityContract().equals(positionTwo.getIdentityContract());
		}));		
		//
		// remove contract and position
		createdProjection.getOtherContracts().removeIf(c -> {
			return c.getWorkPosition().equals(otherContractOne.getWorkPosition()) && c.getPosition().equals(otherContractOne.getPosition());
		});
		createdProjection.getOtherPositions().removeIf(p -> {
			return p.getWorkPosition().equals(positionOne.getWorkPosition()) 
					&& p.getPosition().equals(positionOne.getPosition())
					&& p.getIdentityContract().equals(createdProjection.getContract().getId());
		});
		
		IdmIdentityProjectionDto updatedProjection = manager
				.publish(new IdentityProjectionEvent(IdentityProjectionEventType.UPDATE, createdProjection))
				.getContent();
		
		// other contract
		Assert.assertEquals(1, updatedProjection.getOtherContracts().size());
		Assert.assertTrue(updatedProjection.getOtherContracts().stream().anyMatch(c -> {
			return c.getWorkPosition().equals(otherContractTwo.getWorkPosition()) 
					&& c.getPosition().equals(otherContractTwo.getPosition())
					&& c.getId().equals(otherContractTwo.getId()); // preserve id
		}));
		// other position
		Assert.assertEquals(1, updatedProjection.getOtherPositions().size());
		Assert.assertTrue(updatedProjection.getOtherPositions().stream().anyMatch(p -> {
			return p.getWorkPosition().equals(positionTwo.getWorkPosition()) 
					&& p.getPosition().equals(positionTwo.getPosition())
					&& p.getIdentityContract().equals(positionTwo.getIdentityContract());
		}));
	}
	
	@Test
	@Transactional
	public void testGetProjectionEavReadOnly() {		
		// create identity with read permission only
		IdmIdentityDto identityLogged = getHelper().createIdentity(); // with password
		IdmRoleDto role = getHelper().createRole();
		// read all
		getHelper().createBasePolicy(
				role.getId(), 
				IdmBasePermission.AUTOCOMPLETE, // form definition autocomplete is required
				IdmBasePermission.READ);
		getHelper().createIdentityRole(identityLogged, role);
		//
		// prepare eav definition
		IdmFormAttributeDto attributeOne = new IdmFormAttributeDto(getHelper().createName());
		attributeOne.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormAttributeDto attributeTwo = new IdmFormAttributeDto(getHelper().createName());
		attributeTwo.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentityDto.class, getHelper().createName(), Lists.newArrayList(attributeOne, attributeTwo));
		attributeOne = formDefinitionOne.getMappedAttributeByCode(attributeOne.getCode());
		attributeTwo = formDefinitionOne.getMappedAttributeByCode(attributeTwo.getCode());
		IdmFormAttributeDto attributeThree = new IdmFormAttributeDto(getHelper().createName());
		attributeThree.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormDefinitionDto formDefinitionTwo = formService.createDefinition(IdmIdentityDto.class, getHelper().createName(), Lists.newArrayList(attributeThree));
		attributeThree = formDefinitionTwo.getMappedAttributeByCode(attributeThree.getCode());
		IdmFormAttributeDto attributeContract = new IdmFormAttributeDto(getHelper().createName());
		attributeContract.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormDefinitionDto formDefinitionContract = formService.createDefinition(
				IdmIdentityContractDto.class, getHelper().createName(), Lists.newArrayList(attributeContract));
		attributeContract = formDefinitionContract.getMappedAttributeByCode(attributeContract.getCode());
		//
		IdmIdentityDto identity = new IdmIdentityDto(getHelper().createName());
		IdmIdentityProjectionDto projection = new IdmIdentityProjectionDto(identity);
		//
		// set eav 
		IdmFormInstanceDto instanceOne = new IdmFormInstanceDto();
		instanceOne.setFormDefinition(formDefinitionOne);
		IdmFormValueDto valueOne = new IdmFormValueDto(attributeOne);
		valueOne.setValue(getHelper().createName());
		IdmFormValueDto valueTwo = new IdmFormValueDto(attributeTwo);
		valueTwo.setValue(getHelper().createName());
		instanceOne.setValues(Lists.newArrayList(valueOne, valueTwo));
		IdmFormInstanceDto instanceTwo = new IdmFormInstanceDto();
		instanceTwo.setFormDefinition(formDefinitionTwo);
		IdmFormValueDto valueThree = new IdmFormValueDto(attributeThree);
		valueThree.setValue(getHelper().createName());
		instanceTwo.setValues(Lists.newArrayList(valueThree));
		identity.setEavs(Lists.newArrayList(instanceOne, instanceTwo));
		//
		// set contract
		IdmIdentityContractDto primeContract = new IdmIdentityContractDto();
		primeContract.setMain(true);
		primeContract.setWorkPosition(getHelper().createTreeNode().getId());
		primeContract.setPosition(getHelper().createName());
		primeContract.setValidFrom(LocalDate.now().minus(1l, ChronoUnit.DAYS));
		primeContract.setValidFrom(LocalDate.now().plus(1l, ChronoUnit.DAYS));
		projection.setContract(primeContract);
		IdmFormInstanceDto instanceContract = new IdmFormInstanceDto();
		instanceContract.setFormDefinition(formDefinitionContract);
		IdmFormValueDto valueContract = new IdmFormValueDto(attributeContract);
		valueContract.setValue(getHelper().createName());
		instanceContract.setValues(Lists.newArrayList(valueContract));
		primeContract.setEavs(Lists.newArrayList(instanceContract));
		//
		projection = manager
				.publish(new IdentityProjectionEvent(IdentityProjectionEventType.CREATE, projection))
				.getContent();
		
		getHelper().login(identityLogged);
		try {
			IdmIdentityProjectionDto createdProjection = manager.get(projection, IdmBasePermission.READ);
			//
			Assert.assertNotNull(createdProjection);
			Assert.assertNotNull(createdProjection.getId());
			Assert.assertEquals(createdProjection.getId(), createdProjection.getIdentity().getId());
			Assert.assertTrue(
					createdProjection
						.getIdentity()
						.getEavs()
						.stream()
						.allMatch(i -> {
							return i.getFormDefinition().getFormAttributes().stream().allMatch(IdmFormAttributeDto::isReadonly);
						})
			);
			// eavs
			Assert.assertEquals(valueOne.getValue(), createdProjection
				.getIdentity()
				.getEavs()
				.stream()
				.filter(i -> i.getFormDefinition().getId().equals(formDefinitionOne.getId()))
				.findFirst()
				.get()
				.toSinglePersistentValue(attributeOne.getCode()));
			Assert.assertEquals(valueTwo.getValue(), createdProjection
					.getIdentity()
					.getEavs()
					.stream()
					.filter(i -> i.getFormDefinition().getId().equals(formDefinitionOne.getId()))
					.findFirst()
					.get()
					.toSinglePersistentValue(attributeTwo.getCode()));
			Assert.assertEquals(valueThree.getValue(), createdProjection
					.getIdentity()
					.getEavs()
					.stream()
					.filter(i -> i.getFormDefinition().getId().equals(formDefinitionTwo.getId()))
					.findFirst()
					.get()
					.toSinglePersistentValue(attributeThree.getCode()));
			// prime contract eavs
			Assert.assertTrue(
					createdProjection
						.getContract()
						.getEavs()
						.stream()
						.allMatch(i -> {
							return i.getFormDefinition().getFormAttributes().stream().allMatch(IdmFormAttributeDto::isReadonly);
						})
			);
			Assert.assertEquals(valueContract.getValue(), createdProjection
				.getContract()
				.getEavs()
				.stream()
				.filter(i -> i.getFormDefinition().getId().equals(formDefinitionContract.getId()))
				.findFirst()
				.get()
				.toSinglePersistentValue(attributeContract.getCode()));			
		} finally {
			logout();
		}
	}
	
	@Test
	@Transactional
	public void testGetProjectionWithoutContractAuthority() {
		String defaultRoleCode = roleConfiguration.getDefaultRoleCode();
		// create identity with update identity permission only
		IdmIdentityDto identityLogged = getHelper().createIdentity(); // with password
		IdmRoleDto role = getHelper().createRole();
		// read all
		getHelper().createBasePolicy(
				role.getId(), 
				CoreGroupPermission.IDENTITY,
				IdmIdentity.class,
				IdmBasePermission.READ,
				IdmBasePermission.UPDATE);
		getHelper().createIdentityRole(identityLogged, role);
		//		
		IdmIdentityDto identity = new IdmIdentityDto(getHelper().createName());
		IdmIdentityProjectionDto projection = new IdmIdentityProjectionDto(identity);
		//
		// set contract
		IdmIdentityContractDto primeContract = new IdmIdentityContractDto();
		primeContract.setMain(true);
		primeContract.setWorkPosition(getHelper().createTreeNode().getId());
		primeContract.setPosition(getHelper().createName());
		primeContract.setValidFrom(LocalDate.now().minus(1l, ChronoUnit.DAYS));
		primeContract.setValidFrom(LocalDate.now().plus(1l, ChronoUnit.DAYS));
		projection.setContract(primeContract);
		//
		// set other contract
		IdmIdentityContractDto otherContractOne = new IdmIdentityContractDto();
		otherContractOne.setWorkPosition(getHelper().createTreeNode().getId());
		otherContractOne.setPosition(getHelper().createName());
		IdmIdentityContractDto otherContractTwo = new IdmIdentityContractDto(UUID.randomUUID()); // preset uuid
		otherContractTwo.setWorkPosition(getHelper().createTreeNode().getId());
		otherContractTwo.setPosition(getHelper().createName());
		projection.setOtherContracts(Lists.newArrayList(otherContractOne, otherContractTwo));
		//
		// set other contract position
		IdmContractPositionDto positionOne = new IdmContractPositionDto();
		positionOne.setWorkPosition(getHelper().createTreeNode().getId());
		positionOne.setPosition(getHelper().createName());
		IdmContractPositionDto positionTwo = new IdmContractPositionDto();
		positionTwo.setWorkPosition(getHelper().createTreeNode().getId());
		positionTwo.setPosition(getHelper().createName());
		positionTwo.setIdentityContract(otherContractTwo.getId());
		projection.setOtherPositions(Lists.newArrayList(positionOne, positionTwo));
		//
		projection = manager
				.publish(new IdentityProjectionEvent(IdentityProjectionEventType.CREATE, projection))
				.getContent();
		// assigned roles
		getHelper().createIdentityRole(projection.getIdentity(), role);
		try {
			// empty property => disable default role
			getHelper().setConfigurationValue(RoleConfiguration.PROPERTY_DEFAULT_ROLE, "");
			//
			getHelper().login(identityLogged);
			IdmIdentityProjectionDto createdProjection = manager.get(projection, IdmBasePermission.READ);
			//
			Assert.assertNotNull(createdProjection);
			Assert.assertNull(createdProjection.getContract());
			Assert.assertTrue(createdProjection.getOtherContracts().isEmpty());
			Assert.assertTrue(createdProjection.getOtherPositions().isEmpty());
			Assert.assertTrue(createdProjection.getIdentityRoles().isEmpty());
			//
			createdProjection = manager.get(projection);
			//
			Assert.assertNotNull(createdProjection);
			Assert.assertNotNull(createdProjection.getContract());
			Assert.assertFalse(createdProjection.getOtherContracts().isEmpty());
			Assert.assertFalse(createdProjection.getOtherPositions().isEmpty());
			Assert.assertFalse(createdProjection.getIdentityRoles().isEmpty());
		} finally {
			logout();
			getHelper().setConfigurationValue(RoleConfiguration.PROPERTY_DEFAULT_ROLE, defaultRoleCode);
		}
	}
	
	@Transactional
	@Test(expected = ForbiddenEntityException.class)
	public void testSaveProjectionWithoutContractAuthority() {
		String defaultRoleCode = roleConfiguration.getDefaultRoleCode();
		// create identity with update identity permission only
		IdmIdentityDto identityLogged = getHelper().createIdentity(); // with password
		IdmRoleDto role = getHelper().createRole();
		// read all
		getHelper().createBasePolicy(
				role.getId(), 
				CoreGroupPermission.IDENTITY,
				IdmIdentity.class,
				IdmBasePermission.READ,
				IdmBasePermission.CREATE,
				IdmBasePermission.UPDATE);
		getHelper().createIdentityRole(identityLogged, role);
		//		
		IdmIdentityDto identity = new IdmIdentityDto(getHelper().createName());
		IdmIdentityProjectionDto projection = new IdmIdentityProjectionDto(identity);
		projection.setContract(new IdmIdentityContractDto());
		//
		try {
			// empty property => disable default role
			getHelper().setConfigurationValue(RoleConfiguration.PROPERTY_DEFAULT_ROLE, "");
			//
			getHelper().login(identityLogged);
			//
			manager
				.publish(new IdentityProjectionEvent(IdentityProjectionEventType.CREATE, projection), IdmBasePermission.CREATE)
				.getContent();
		} finally {
			logout();
			getHelper().setConfigurationValue(RoleConfiguration.PROPERTY_DEFAULT_ROLE, defaultRoleCode);
		}
	}
	
	@Test
	@Transactional
	public void testSaveProjectionEavSecuredException() {
		//
		// create definition with two attributes
		IdmFormAttributeDto formAttributeOne = new IdmFormAttributeDto(getHelper().createName());
		IdmFormAttributeDto formAttributeTwo = new IdmFormAttributeDto(getHelper().createName());
		IdmFormDefinitionDto formDefinition = formService.createDefinition(
				IdmIdentityDto.class, 
				getHelper().createName(), 
				Lists.newArrayList(formAttributeOne, formAttributeTwo));
		formAttributeOne = formDefinition.getMappedAttributeByCode(formAttributeOne.getCode());
		formAttributeTwo = formDefinition.getMappedAttributeByCode(formAttributeTwo.getCode());
		//
		IdmIdentityDto identityOne = getHelper().createIdentity(); // password is needed
		IdmIdentityDto identityTwo = getHelper().createIdentity(); // password is needed
		IdmIdentityDto identityOther = getHelper().createIdentity((GuardedString) null);
		//
		// assign self identity authorization policy - READ - to identityOne
		IdmRoleDto roleReadIdentity = getHelper().createRole();		
		getHelper().createAuthorizationPolicy(
				roleReadIdentity.getId(), 
				CoreGroupPermission.IDENTITY, 
				IdmIdentity.class, 
				SelfIdentityEvaluator.class, 
				IdmBasePermission.AUTOCOMPLETE, IdmBasePermission.READ);
		getHelper().createUuidPolicy( // and other
				roleReadIdentity.getId(), 
				identityOther.getId(), 
				IdmBasePermission.AUTOCOMPLETE, IdmBasePermission.READ);
		getHelper().createIdentityRole(identityOne, roleReadIdentity);
		//
		// assign self identity authorization policy - UPDATE - to identityOne
		IdmRoleDto roleUpdateIdentity = getHelper().createRole();		
		getHelper().createAuthorizationPolicy(
				roleUpdateIdentity.getId(), 
				CoreGroupPermission.IDENTITY, 
				IdmIdentity.class, 
				SelfIdentityEvaluator.class, // self
				IdmBasePermission.AUTOCOMPLETE, IdmBasePermission.READ, IdmBasePermission.UPDATE);
		getHelper().createUuidPolicy( // and other
				roleUpdateIdentity.getId(), 
				identityOther.getId(), 
				IdmBasePermission.AUTOCOMPLETE, IdmBasePermission.READ, IdmBasePermission.UPDATE);
		getHelper().createIdentityRole(identityTwo, roleUpdateIdentity);
		//
		// assign autocomplete to form definition 
		getHelper().createUuidPolicy( 
				roleReadIdentity.getId(), 
				formDefinition.getId(), 
				IdmBasePermission.AUTOCOMPLETE,
				IdmBasePermission.READ);
		getHelper().createUuidPolicy( // and other
				roleUpdateIdentity.getId(), 
				formDefinition.getId(), 
				IdmBasePermission.AUTOCOMPLETE,
				IdmBasePermission.READ);
		//
		// save some values as admin to identity one
		IdmFormValueDto formValueOne = new IdmFormValueDto(formAttributeOne);
		formValueOne.setValue(getHelper().createName());
		IdmFormValueDto formValueTwo = new IdmFormValueDto(formAttributeTwo);
		formValueTwo.setValue(getHelper().createName());
		List<IdmFormValueDto> formValues = Lists.newArrayList(formValueOne, formValueTwo);
		identityOne.setEavs(Lists.newArrayList(new IdmFormInstanceDto(identityOne, formDefinition, formValues)));
		manager.publish(new IdentityProjectionEvent(IdentityProjectionEventType.UPDATE, new IdmIdentityProjectionDto(identityOne)));
		//
		// values cannot be read as identity one 
		getHelper().login(identityOne);
		try {
			IdmIdentityProjectionDto projection = manager.get(identityOne.getId(), IdmBasePermission.READ);
			IdmFormInstanceDto formInstance = projection
					.getIdentity()
					.getEavs()
					.stream()
					.filter(i -> i.getFormDefinition().getId().equals(formDefinition.getId()))
					.findFirst()
					.get();
			Assert.assertTrue(formInstance.getValues().isEmpty());
			Assert.assertEquals(0, formInstance.getFormDefinition().getFormAttributes().size());
		} finally {
			logout();
		}
		
		getHelper().login(identityTwo);
		try {
			IdmIdentityProjectionDto projection = manager.get(identityOther.getId(), IdmBasePermission.READ);
			IdmFormInstanceDto formInstance = projection
					.getIdentity()
					.getEavs()
					.stream()
					.filter(i -> i.getFormDefinition().getId().equals(formDefinition.getId()))
					.findFirst()
					.get();
			Assert.assertTrue(formInstance.getValues().isEmpty());
			Assert.assertEquals(0, formInstance.getFormDefinition().getFormAttributes().size());
		} finally {
			logout();
		}
		//
		// configure authorization policy to read attribute one and edit attribute two - for self
		ConfigurationMap properties = new ConfigurationMap();
		properties.put(IdentityFormValueEvaluator.PARAMETER_FORM_DEFINITION, formDefinition.getId());
		properties.put(IdentityFormValueEvaluator.PARAMETER_FORM_ATTRIBUTES, formAttributeOne.getCode());
		properties.put(IdentityFormValueEvaluator.PARAMETER_SELF_ONLY, true);
		getHelper().createAuthorizationPolicy(
				roleReadIdentity.getId(), 
				CoreGroupPermission.FORMVALUE, 
				IdmIdentityFormValue.class, 
				IdentityFormValueEvaluator.class,
				properties,
				IdmBasePermission.READ);
		//
		// read self attribute one
		getHelper().login(identityOne);
		try {
			IdmIdentityProjectionDto projection = manager.get(identityOne.getId(), IdmBasePermission.READ);
			IdmFormInstanceDto formInstance = projection
					.getIdentity()
					.getEavs()
					.stream()
					.filter(i -> i.getFormDefinition().getId().equals(formDefinition.getId()))
					.findFirst()
					.get();
			//
			Assert.assertEquals(1, formInstance.getValues().size());
			Assert.assertEquals(formValueOne.getShortTextValue(), formInstance.getValues().get(0).getShortTextValue());
			Assert.assertEquals(1, formInstance.getFormDefinition().getFormAttributes().size());
			Assert.assertEquals(formAttributeOne.getCode(), formInstance.getFormDefinition().getFormAttributes().get(0).getCode());
		} finally {
			logout();
		}
		//
		// update is forbidden
		getHelper().login(identityOne);
		try {
			identityOne.setEavs(Lists.newArrayList(new IdmFormInstanceDto(identityOne, formDefinition, Lists.newArrayList(formValueOne))));
			manager
				.publish(
						new IdentityProjectionEvent(IdentityProjectionEventType.UPDATE, new IdmIdentityProjectionDto(identityOne)),
						IdmBasePermission.UPDATE)
				.getContent();
		} catch (ForbiddenEntityException ex) {
			// ok
		} finally {
			logout();
		}
		getHelper().login(identityOne);
		try {
			identityTwo.setEavs(Lists.newArrayList(new IdmFormInstanceDto(identityOne, formDefinition, Lists.newArrayList(formValueOne))));
			manager
				.publish(
						new IdentityProjectionEvent(IdentityProjectionEventType.UPDATE, new IdmIdentityProjectionDto(identityTwo)),
						IdmBasePermission.UPDATE)
				.getContent();
		} catch (ForbiddenEntityException ex) {
			// ok
		} finally {
			logout();
		}
		//
		// add policy to edit attribute two for identity one
		properties = new ConfigurationMap();
		properties.put(IdentityFormValueEvaluator.PARAMETER_FORM_DEFINITION, formDefinition.getId());
		properties.put(IdentityFormValueEvaluator.PARAMETER_FORM_ATTRIBUTES, formAttributeTwo.getCode());
		properties.put(IdentityFormValueEvaluator.PARAMETER_SELF_ONLY, true);
		getHelper().createAuthorizationPolicy(
				roleReadIdentity.getId(), 
				CoreGroupPermission.FORMVALUE, 
				IdmIdentityFormValue.class, 
				IdentityFormValueEvaluator.class,
				properties,
				IdmBasePermission.READ, IdmBasePermission.UPDATE);
		//
		String updatedValue = getHelper().createName();
		formValueTwo.setValue(updatedValue);
	}
	
	@Test
	@Transactional
	public void testLoadProjectionDefinedEavsOnly() throws Exception {
		//
		// create definition with two attributes
		IdmFormAttributeDto formAttributeOne = new IdmFormAttributeDto(getHelper().createName());
		IdmFormAttributeDto formAttributeTwo = new IdmFormAttributeDto(getHelper().createName());
		IdmFormDefinitionDto formDefinition = formService.createDefinition(
				IdmIdentityDto.class, 
				getHelper().createName(), 
				Lists.newArrayList(formAttributeOne, formAttributeTwo));
		formAttributeOne = formDefinition.getMappedAttributeByCode(formAttributeOne.getCode());
		formAttributeTwo = formDefinition.getMappedAttributeByCode(formAttributeTwo.getCode());
		//
		// create projection with one attribute
		FormDefinitionAttributes attributes = new FormDefinitionAttributes();
		attributes.setDefinition(formDefinition.getId());
		attributes.getAttributes().add(formAttributeOne.getId());
		IdmFormProjectionDto projection = new IdmFormProjectionDto();
		projection.setCode(getHelper().createName());
		projection.setOwnerType(lookupService.getOwnerType(IdmIdentityDto.class));
		projection.setFormDefinitions(mapper.writeValueAsString(Lists.newArrayList(attributes)));
		projection = projectionService.save(projection);
		//
		// create identity with projection is defined
		IdmIdentityDto identity = new IdmIdentityDto(getHelper().createName());
		identity.setFormProjection(projection.getId());
		identity = identityService.save(identity);
		//
		// save some values
		IdmFormValueDto formValueOne = new IdmFormValueDto(formAttributeOne);
		formValueOne.setValue(getHelper().createName());
		IdmFormValueDto formValueTwo = new IdmFormValueDto(formAttributeTwo);
		formValueTwo.setValue(getHelper().createName());
		List<IdmFormValueDto> formValues = Lists.newArrayList(formValueOne, formValueTwo);
		identity.setEavs(Lists.newArrayList(new IdmFormInstanceDto(identity, formDefinition, formValues)));
		manager.publish(new IdentityProjectionEvent(IdentityProjectionEventType.UPDATE, new IdmIdentityProjectionDto(identity)));
		//
		IdmIdentityProjectionDto identityProjection = manager.get(identity.getId());
		//
		Assert.assertEquals(1, identityProjection.getIdentity().getEavs().size());
		Assert.assertEquals(1, identityProjection.getIdentity().getEavs().get(0).getValues().size());
		Assert.assertEquals(formValueOne.getValue(), identityProjection.getIdentity().getEavs().get(0).getValues().get(0).getValue());
	}
	
	@Test
	@Transactional
	public void testProjectionDontSaveOtherContractsAndPositions() {
		IdmFormProjectionDto formProjection = new IdmFormProjectionDto();
		formProjection.setCode(getHelper().createName());
		formProjection.setOwnerType(lookupService.getOwnerType(IdmIdentityDto.class));
		formProjection = projectionService.save(formProjection);
		//
		IdmIdentityDto identity = new IdmIdentityDto(getHelper().createName());
		identity.setFormProjection(formProjection.getId());
		IdmIdentityProjectionDto projection = new IdmIdentityProjectionDto(identity);
		//
		// set contract
		IdmIdentityContractDto primeContract = new IdmIdentityContractDto();
		primeContract.setMain(true);
		primeContract.setWorkPosition(getHelper().createTreeNode().getId());
		primeContract.setPosition(getHelper().createName());
		primeContract.setValidFrom(LocalDate.now().minus(1l, ChronoUnit.DAYS));
		primeContract.setValidFrom(LocalDate.now().plus(1l, ChronoUnit.DAYS));
		projection.setContract(primeContract);
		//
		// set other contract
		IdmIdentityContractDto otherContractOne = new IdmIdentityContractDto();
		otherContractOne.setWorkPosition(getHelper().createTreeNode().getId());
		otherContractOne.setPosition(getHelper().createName());
		IdmIdentityContractDto otherContractTwo = new IdmIdentityContractDto(UUID.randomUUID()); // preset uuid
		otherContractTwo.setWorkPosition(getHelper().createTreeNode().getId());
		otherContractTwo.setPosition(getHelper().createName());
		projection.setOtherContracts(Lists.newArrayList(otherContractOne, otherContractTwo));
		//
		// set other contract position
		IdmContractPositionDto positionOne = new IdmContractPositionDto();
		positionOne.setWorkPosition(getHelper().createTreeNode().getId());
		positionOne.setPosition(getHelper().createName());
		IdmContractPositionDto positionTwo = new IdmContractPositionDto();
		positionTwo.setWorkPosition(getHelper().createTreeNode().getId());
		positionTwo.setPosition(getHelper().createName());
		positionTwo.setIdentityContract(otherContractTwo.getId());
		projection.setOtherPositions(Lists.newArrayList(positionOne, positionTwo));
		
		IdmIdentityProjectionDto createdProjection = manager
				.publish(new IdentityProjectionEvent(IdentityProjectionEventType.CREATE, projection))
				.getContent();
		
		// other contract
		Assert.assertTrue(createdProjection.getOtherContracts().isEmpty());
		// other position
		Assert.assertTrue(createdProjection.getOtherPositions().isEmpty());
	}
	
	@Test
	@Transactional
	public void testPreventToDeleteOtherContractWhenPrimeContractIsChanged() {
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto primeContract = getHelper().getPrimeContract(identity);
		IdmIdentityContractDto otherContract = getHelper().createContract(identity);
		
		List<IdmIdentityContractDto> contracts = contractService.findAllByIdentity(identity.getId());
		Assert.assertEquals(2, contracts.size());
		Assert.assertTrue(contracts.stream().anyMatch(c -> c.getId().equals(primeContract.getId())));
		Assert.assertTrue(contracts.stream().anyMatch(c -> c.getId().equals(otherContract.getId())));
		
		IdmIdentityProjectionDto projection = new IdmIdentityProjectionDto(identity);
		projection.setContract(otherContract);
		projection.setOtherContracts(Lists.newArrayList(primeContract));
		
		manager.publish(new IdentityProjectionEvent(IdentityProjectionEventType.UPDATE, projection));
		
		contracts = contractService.findAllByIdentity(identity.getId());
		Assert.assertEquals(2, contracts.size());
		Assert.assertTrue(contracts.stream().anyMatch(c -> c.getId().equals(primeContract.getId())));
		Assert.assertTrue(contracts.stream().anyMatch(c -> c.getId().equals(otherContract.getId())));
	}
	
	@Test
	@Transactional
	public void testDeleteOtherContractWhenPrimeContractIsChanged() {
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto primeContract = getHelper().getPrimeContract(identity);
		IdmIdentityContractDto otherContract = getHelper().createContract(identity);
		
		List<IdmIdentityContractDto> contracts = contractService.findAllByIdentity(identity.getId());
		Assert.assertEquals(2, contracts.size());
		Assert.assertTrue(contracts.stream().anyMatch(c -> c.getId().equals(primeContract.getId())));
		Assert.assertTrue(contracts.stream().anyMatch(c -> c.getId().equals(otherContract.getId())));
		
		IdmIdentityProjectionDto projection = new IdmIdentityProjectionDto(identity);
		projection.setContract(otherContract);
		
		manager.publish(new IdentityProjectionEvent(IdentityProjectionEventType.UPDATE, projection));
		
		contracts = contractService.findAllByIdentity(identity.getId());
		Assert.assertEquals(1, contracts.size());
		Assert.assertTrue(contracts.stream().anyMatch(c -> c.getId().equals(otherContract.getId())));
	}
	
	@Test
	@Transactional
	public void testLoadAssignedRoles() {
		IdmFormProjectionDto projection = new IdmFormProjectionDto();
		projection.setCode(getHelper().createName());
		projection.setOwnerType(lookupService.getOwnerType(IdmIdentityDto.class));
		projection = projectionService.save(projection);
		//
		// create identity with projection is defined
		IdmIdentityDto identity = new IdmIdentityDto(getHelper().createName());
		identity.setFormProjection(projection.getId());
		identity = identityService.save(identity);
		IdmIdentityContractDto primeContract = getHelper().getPrimeContract(identity);
		getHelper().createIdentityRole(primeContract, getHelper().createRole());
		//
		IdmIdentityProjectionDto identityProjection = manager.get(identity.getId());
		Assert.assertFalse(identityProjection.getIdentityRoles().isEmpty());
		//
		projection.getProperties().put(IdentityFormProjectionRoute.PARAMETER_LOAD_ASSIGNED_ROLES, false);
		projectionService.save(projection);
		identityProjection = manager.get(identity.getId());
		Assert.assertTrue(identityProjection.getIdentityRoles().isEmpty());
	}
	
	@Test
	public void testAssignAutomaticRoleIdentityEav() throws Exception {
		UUID identityId = null;
		try {
			getHelper().setConfigurationValue(EventConfiguration.PROPERTY_EVENT_ASYNCHRONOUS_ENABLED, true);
			// create form definition, roles, automatic role etc.
			IdmRoleDto role = getHelper().createRole();
			IdmRoleDto subRole = getHelper().createRole();
			getHelper().createRoleComposition(role, subRole);
			//
			IdmFormAttributeDto formAttributeOne = new IdmFormAttributeDto(getHelper().createName());
			IdmFormDefinitionDto formDefinition = formService.createDefinition(
					IdmIdentityDto.class, 
					getHelper().createName(), 
					Lists.newArrayList(formAttributeOne));
			formAttributeOne = formDefinition.getMappedAttributeByCode(formAttributeOne.getCode());
			//
			IdmAutomaticRoleAttributeDto automaticRole = getHelper().createAutomaticRole(role.getId());
			getHelper().createAutomaticRoleRule(
					automaticRole.getId(), 
					AutomaticRoleAttributeRuleComparison.EQUALS, 
					AutomaticRoleAttributeRuleType.IDENTITY_EAV, 
					null, 
					formAttributeOne.getId(), 
					"mockOne");
			//
			// prepare identity projection with two contract
			IdmIdentityDto identity = new IdmIdentityDto(getHelper().createName());
			IdmFormValueDto formValue = new IdmFormValueDto(formAttributeOne);
			formValue.setValue("mockOne");
			identity.getEavs().add(new IdmFormInstanceDto(
					identity, 
					formDefinition, 
					Lists.newArrayList(formValue)));
			
			IdmIdentityContractDto contractOne = new IdmIdentityContractDto();
			contractOne.setPosition(getHelper().createName());
			IdmIdentityContractDto contractTwo = new IdmIdentityContractDto();
			contractTwo.setPosition(getHelper().createName());
			IdmIdentityProjectionDto projection = new IdmIdentityProjectionDto(identity);
			projection.setContract(contractOne);
			projection.setOtherContracts(Lists.newArrayList(contractTwo));
			//
			// create by projection
			projection = manager
					.publish(new IdentityProjectionEvent(IdentityProjectionEventType.CREATE, projection))
					.getContent();
			IdmIdentityProjectionDto createdProjection = manager.get(projection);
			identityId = createdProjection.getId();
			Assert.assertEquals(identity.getUsername(), createdProjection.getIdentity().getUsername());
			IdmIdentityContractDto primeContract = createdProjection.getContract();
			Assert.assertNotNull(primeContract);
			Assert.assertEquals(contractOne.getPosition(), primeContract.getPosition());
			Assert.assertEquals(1, createdProjection.getOtherContracts().size());
			IdmIdentityContractDto otherContract = createdProjection.getOtherContracts().get(0);
			Assert.assertEquals(contractTwo.getPosition(), otherContract.getPosition());
			//
			// 4 roles on each contract (2x role + sub) => role is assigned to each contract
			IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
			filter.setIdentityId(createdProjection.getIdentity().getId());
			getHelper().waitForResult(res -> {
				return identityRoleService.find(filter, null).getContent().size() != 4;
			});
			List<IdmIdentityRoleDto> identityRoles = identityRoleService.find(filter, null).getContent();
			Assert.assertEquals(4, identityRoles.size());
			Assert.assertEquals(2, identityRoles.stream().filter(ir -> ir.getRole().equals(role.getId())).count());
			Assert.assertEquals(2, identityRoles.stream().filter(ir -> ir.getRole().equals(subRole.getId())).count());
			//
			// change eav value => remove all automatic roles
			formValue.setValue("mockUpdate");
			createdProjection.getIdentity().getEavs().clear();
			createdProjection.getIdentity().getEavs().add(new IdmFormInstanceDto(
					identity, 
					formDefinition, 
					Lists.newArrayList(formValue)));
			manager.publish(new IdentityProjectionEvent(IdentityProjectionEventType.UPDATE, createdProjection)).getContent();
			getHelper().waitForResult(res -> {
				return !identityRoleService.find(filter, null).getContent().isEmpty();
			});
			//
			identityRoles =  Lists.newArrayList(identityRoleService.find(filter, null).getContent());
			Assert.assertTrue(identityRoles.isEmpty());
		} finally {
			getHelper().setConfigurationValue(EventConfiguration.PROPERTY_EVENT_ASYNCHRONOUS_ENABLED, false);
			getHelper().deleteIdentity(identityId);
		}
	}
	
	@Test
	public void testAssignAutomaticRoleIdentityAndContractEav() throws Exception {
		UUID identityId = null;
		try {
			getHelper().setConfigurationValue(EventConfiguration.PROPERTY_EVENT_ASYNCHRONOUS_ENABLED, true);
			// create form definition, roles, automatic role etc.
			IdmRoleDto role = getHelper().createRole();
			IdmRoleDto subRole = getHelper().createRole();
			getHelper().createRoleComposition(role, subRole);
			IdmRoleDto roleContract = getHelper().createRole();
			IdmRoleDto subRoleContract = getHelper().createRole();
			getHelper().createRoleComposition(roleContract, subRoleContract);
			//
			IdmFormAttributeDto formAttributeOne = new IdmFormAttributeDto(getHelper().createName());
			IdmFormDefinitionDto formDefinition = formService.createDefinition(
					IdmIdentityDto.class, 
					getHelper().createName(), 
					Lists.newArrayList(formAttributeOne));
			formAttributeOne = formDefinition.getMappedAttributeByCode(formAttributeOne.getCode());
			//
			IdmFormAttributeDto formAttributeContract = new IdmFormAttributeDto(getHelper().createName());
			IdmFormDefinitionDto formDefinitionContract = formService.createDefinition(
					IdmIdentityContractDto.class, 
					getHelper().createName(), 
					Lists.newArrayList(formAttributeContract));
			formAttributeContract = formDefinitionContract.getMappedAttributeByCode(formAttributeContract.getCode());
			//
			IdmAutomaticRoleAttributeDto automaticRole = getHelper().createAutomaticRole(role.getId());
			getHelper().createAutomaticRoleRule(
					automaticRole.getId(), 
					AutomaticRoleAttributeRuleComparison.EQUALS, 
					AutomaticRoleAttributeRuleType.IDENTITY_EAV, 
					null, 
					formAttributeOne.getId(), 
					"mockOne");
			IdmAutomaticRoleAttributeDto automaticRoleContract = getHelper().createAutomaticRole(roleContract.getId());
			getHelper().createAutomaticRoleRule(
					automaticRoleContract.getId(), 
					AutomaticRoleAttributeRuleComparison.EQUALS, 
					AutomaticRoleAttributeRuleType.CONTRACT_EAV, 
					null, 
					formAttributeContract.getId(), 
					"mockContract");
			// form projection
			IdmFormProjectionDto formProjection = new IdmFormProjectionDto();
			formProjection.setCode(getHelper().createName());
			formProjection.setOwnerType(lookupService.getOwnerType(IdmIdentityDto.class));
			formProjection.getProperties().put(IdentityFormProjectionRoute.PARAMETER_LOAD_ASSIGNED_ROLES, false);
			formProjection.getProperties().put(IdentityFormProjectionRoute.PARAMETER_ALL_CONTRACTS, true);
			FormDefinitionAttributes attributes = new FormDefinitionAttributes();
			attributes.setDefinition(formDefinition.getId());
			attributes.getAttributes().add(formAttributeOne.getId());
			FormDefinitionAttributes attributesContract = new FormDefinitionAttributes();
			attributesContract.setDefinition(formDefinitionContract.getId());
			attributesContract.getAttributes().add(formAttributeContract.getId());
			formProjection.setCode(getHelper().createName());
			formProjection.setOwnerType(lookupService.getOwnerType(IdmIdentityDto.class));
			formProjection.setFormDefinitions(mapper.writeValueAsString(Lists.newArrayList(attributes, attributesContract)));
			formProjection = projectionService.save(formProjection);
			//
			// prepare identity projection with two contract
			IdmIdentityDto identity = new IdmIdentityDto(getHelper().createName());
			identity.setFormProjection(formProjection.getId());
			IdmFormValueDto formValue = new IdmFormValueDto(formAttributeOne);
			formValue.setValue("mockOne");
			identity.getEavs().add(new IdmFormInstanceDto(
					identity, 
					formDefinition, 
					Lists.newArrayList(formValue)));
			
			IdmIdentityContractDto contractOne = new IdmIdentityContractDto();
			contractOne.setPosition(getHelper().createName());
			IdmIdentityContractDto contractTwo = new IdmIdentityContractDto();
			contractTwo.setPosition(getHelper().createName());
			IdmFormValueDto formValueContract = new IdmFormValueDto(formAttributeContract);
			formValueContract.setValue("mockContract");
			contractTwo.getEavs().add(new IdmFormInstanceDto(
					contractTwo, 
					formDefinitionContract, 
					Lists.newArrayList(formValueContract)));
			IdmIdentityProjectionDto projection = new IdmIdentityProjectionDto(identity);
			projection.setContract(contractOne);
			projection.setOtherContracts(Lists.newArrayList(contractTwo));
			//
			// create by projection
			projection = manager
					.publish(new IdentityProjectionEvent(IdentityProjectionEventType.CREATE, projection))
					.getContent();
			IdmIdentityProjectionDto createdProjection = manager.get(projection);
			identityId = createdProjection.getIdentity().getId();
			Assert.assertEquals(identity.getUsername(), createdProjection.getIdentity().getUsername());
			IdmIdentityContractDto primeContract = createdProjection.getContract();
			Assert.assertNotNull(primeContract);
			Assert.assertEquals(contractOne.getPosition(), primeContract.getPosition());
			Assert.assertEquals(1, createdProjection.getOtherContracts().size());
			IdmIdentityContractDto otherContract = createdProjection.getOtherContracts().get(0);
			Assert.assertEquals(contractTwo.getPosition(), otherContract.getPosition());
			//
			// 6 roles 
			IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
			filter.setIdentityId(createdProjection.getIdentity().getId());
			getHelper().waitForResult(res -> {
				return identityRoleService.find(filter, null).getContent().size() != 6;
			});
			List<IdmIdentityRoleDto> identityRoles =  Lists.newArrayList(identityRoleService.find(filter, null).getContent());
			
			Assert.assertEquals(6, identityRoles.size());
			Assert.assertEquals(2, identityRoles.stream().filter(ir -> ir.getRole().equals(role.getId())).count());
			Assert.assertEquals(2, identityRoles.stream().filter(ir -> ir.getRole().equals(subRole.getId())).count());
			Assert.assertEquals(1, identityRoles.stream().filter(ir -> ir.getRole().equals(roleContract.getId())).count());
			Assert.assertEquals(1, identityRoles.stream().filter(ir -> ir.getRole().equals(subRoleContract.getId())).count());
			//
			// change eav value => remove all automatic roles
			formValue.setValue("mockUpdate");
			createdProjection.getIdentity().getEavs().clear();
			createdProjection.getIdentity().getEavs().add(new IdmFormInstanceDto(
					identity, 
					formDefinition, 
					Lists.newArrayList(formValue)));
			manager.publish(new IdentityProjectionEvent(IdentityProjectionEventType.UPDATE, createdProjection)).getContent();
			getHelper().waitForResult(res -> {
				return identityRoleService.find(filter, null).getContent().size() != 2;
			});
			//
			identityRoles =  Lists.newArrayList(identityRoleService.find(filter, null).getContent());
			Assert.assertEquals(2, identityRoles.size());
			Assert.assertEquals(1, identityRoles.stream().filter(ir -> ir.getRole().equals(roleContract.getId())).count());
			Assert.assertEquals(1, identityRoles.stream().filter(ir -> ir.getRole().equals(subRoleContract.getId())).count());			
		} finally {
			getHelper().setConfigurationValue(EventConfiguration.PROPERTY_EVENT_ASYNCHRONOUS_ENABLED, false);
			getHelper().deleteIdentity(identityId);
		}
	}
	
	@Test
	@Transactional
	public void testValidateBasicFields() throws Exception {
		// prepare projection
		IdmFormProjectionDto formProjection = new IdmFormProjectionDto();
		formProjection.setCode(getHelper().createName());
		formProjection.setOwnerType(lookupService.getOwnerType(IdmIdentityDto.class));
		formProjection.getProperties().put(IdentityFormProjectionRoute.PARAMETER_ALL_CONTRACTS, true);
		IdmFormAttributeDto attributeExternalCode = new IdmFormAttributeDto();
		attributeExternalCode.setId(UUID.randomUUID());
		attributeExternalCode.setCode(IdmIdentity_.externalCode.getName());
		attributeExternalCode.setName(String.format("%s.%s", IdmIdentity.class.getSimpleName(), IdmIdentity_.externalCode.getName()));
		attributeExternalCode.setRequired(true);
		IdmFormAttributeDto attributeLastName = new IdmFormAttributeDto();
		attributeLastName.setId(UUID.randomUUID());
		attributeLastName.setCode(IdmIdentity_.lastName.getName());
		attributeLastName.setMax(BigDecimal.valueOf(3));
		IdmFormAttributeDto attributeValidTill = new IdmFormAttributeDto();
		attributeValidTill.setId(UUID.randomUUID());
		attributeValidTill.setCode(IdmIdentityContract_.validTill.getName());
		attributeValidTill.setName(String.format("%s.%s", 
				IdmIdentityContract.class.getSimpleName(), IdmIdentityContract_.validTill.getName()));
		attributeValidTill.setRequired(true);
		attributeValidTill.setMax(BigDecimal.valueOf(3));
		IdmFormAttributeDto attributeWorkPosition = new IdmFormAttributeDto();
		attributeWorkPosition.setId(UUID.randomUUID());
		attributeWorkPosition.setCode(IdmIdentityContract_.workPosition.getName());
		attributeWorkPosition.setName(String.format("%s.%s", 
				IdmIdentityContract.class.getSimpleName(), IdmIdentityContract_.workPosition.getName()));
		attributeWorkPosition.setRequired(true);
		
		formProjection.setFormValidations(mapper.writeValueAsString(Lists.newArrayList(
				attributeExternalCode, attributeLastName, attributeValidTill, attributeWorkPosition)));
		formProjection = projectionService.save(formProjection);
		//
		// create identity with projection is defined
		IdmIdentityDto identity = new IdmIdentityDto(getHelper().createName());
		identity.setExternalCode(getHelper().createName());
		identity.setLastName("xxx");
		identity.setFormProjection(formProjection.getId());
		IdmIdentityProjectionDto projection = new IdmIdentityProjectionDto(identity);
		//
		// set contract
		IdmIdentityContractDto primeContract = new IdmIdentityContractDto();
		primeContract.setMain(true);
		primeContract.setWorkPosition(getHelper().createTreeNode().getId());
		primeContract.setPosition(getHelper().createName());
		primeContract.setValidFrom(LocalDate.now().minus(1l, ChronoUnit.DAYS));
		primeContract.setValidTill(LocalDate.now().plus(3l, ChronoUnit.DAYS));
		projection.setContract(primeContract);
		//
		IdentityProjectionEvent identityProjectionEvent = new IdentityProjectionEvent(IdentityProjectionEventType.CREATE, projection);
		identityProjectionEvent.setPriority(PriorityType.IMMEDIATE);
		projection = manager
				.publish(identityProjectionEvent)
				.getContent();
		IdmIdentityProjectionDto createdProjection = manager.get(projection);
		//
		Assert.assertNotNull(createdProjection);
		Assert.assertNotNull(createdProjection.getId());
		Assert.assertEquals(createdProjection.getId(), createdProjection.getIdentity().getId());
		//
		// prime contract
		IdmIdentityContractDto createdPrimeContract = createdProjection.getContract();
		Assert.assertEquals(primeContract.getWorkPosition(), createdPrimeContract.getWorkPosition());
		Assert.assertEquals(primeContract.getPosition(), createdPrimeContract.getPosition());
		Assert.assertEquals(primeContract.getValidFrom(), createdPrimeContract.getValidFrom());
		Assert.assertEquals(primeContract.getValidTill(), createdPrimeContract.getValidTill());
		Assert.assertEquals(createdProjection.getIdentity().getId(), createdPrimeContract.getIdentity());
	}
	
	@Transactional
	@Test(expected = InvalidFormException.class)
	public void testValidateBasicFieldsIdentityFailed() throws Exception {
		// prepare projection
		IdmFormProjectionDto formProjection = new IdmFormProjectionDto();
		formProjection.setCode(getHelper().createName());
		formProjection.setOwnerType(lookupService.getOwnerType(IdmIdentityDto.class));
		formProjection.getProperties().put(IdentityFormProjectionRoute.PARAMETER_ALL_CONTRACTS, true);
		IdmFormAttributeDto attributeExternalCode = new IdmFormAttributeDto();
		attributeExternalCode.setId(UUID.randomUUID());
		attributeExternalCode.setCode(IdmIdentity_.externalCode.getName());
		attributeExternalCode.setName(String.format("%s.%s", IdmIdentity.class.getSimpleName(), IdmIdentity_.externalCode.getName()));
		attributeExternalCode.setRequired(true);
		IdmFormAttributeDto attributeLastName = new IdmFormAttributeDto();
		attributeLastName.setId(UUID.randomUUID());
		attributeLastName.setCode(IdmIdentity_.lastName.getName());
		attributeLastName.setMax(BigDecimal.valueOf(3));
		IdmFormAttributeDto attributeValidTill = new IdmFormAttributeDto();
		attributeValidTill.setId(UUID.randomUUID());
		attributeValidTill.setCode(IdmIdentityContract_.validTill.getName());
		attributeValidTill.setName(String.format("%s.%s", 
				IdmIdentityContract.class.getSimpleName(), IdmIdentityContract_.validTill.getName()));
		attributeValidTill.setRequired(true);
		attributeValidTill.setMax(BigDecimal.valueOf(3));
		
		formProjection.setFormValidations(mapper.writeValueAsString(Lists.newArrayList(
				attributeExternalCode, attributeLastName, attributeValidTill)));
		formProjection = projectionService.save(formProjection);
		//
		// create identity with projection is defined
		IdmIdentityDto identity = new IdmIdentityDto(getHelper().createName());
		identity.setExternalCode(getHelper().createName());
		identity.setLastName("xxxx");
		identity.setFormProjection(formProjection.getId());
		IdmIdentityProjectionDto projection = new IdmIdentityProjectionDto(identity);
		//
		// set contract
		IdmIdentityContractDto primeContract = new IdmIdentityContractDto();
		primeContract.setMain(true);
		primeContract.setWorkPosition(getHelper().createTreeNode().getId());
		primeContract.setPosition(getHelper().createName());
		primeContract.setValidFrom(LocalDate.now().minus(1l, ChronoUnit.DAYS));
		primeContract.setValidTill(LocalDate.now().plus(3l, ChronoUnit.DAYS));
		projection.setContract(primeContract);
		//
		IdentityProjectionEvent identityProjectionEvent = new IdentityProjectionEvent(IdentityProjectionEventType.CREATE, projection);
		identityProjectionEvent.setPriority(PriorityType.IMMEDIATE);
		manager.publish(identityProjectionEvent);
	}
	
	@Transactional
	@Test(expected = InvalidFormException.class)
	public void testValidateBasicFieldsContractFailed() throws Exception {
		// prepare projection
		IdmFormProjectionDto formProjection = new IdmFormProjectionDto();
		formProjection.setCode(getHelper().createName());
		formProjection.setOwnerType(lookupService.getOwnerType(IdmIdentityDto.class));
		formProjection.getProperties().put(IdentityFormProjectionRoute.PARAMETER_ALL_CONTRACTS, true);
		IdmFormAttributeDto attributeExternalCode = new IdmFormAttributeDto();
		attributeExternalCode.setCode(IdmIdentity_.externalCode.getName());
		attributeExternalCode.setName(String.format("%s.%s", IdmIdentity.class.getSimpleName(), IdmIdentity_.externalCode.getName()));
		attributeExternalCode.setRequired(true);
		IdmFormAttributeDto attributeLastName = new IdmFormAttributeDto();
		attributeLastName.setCode(IdmIdentity_.lastName.getName());
		attributeLastName.setMax(BigDecimal.valueOf(3));
		IdmFormAttributeDto attributeValidTill = new IdmFormAttributeDto();
		attributeValidTill.setCode(IdmIdentityContract_.validTill.getName());
		attributeValidTill.setName(String.format("%s.%s", 
				IdmIdentityContract.class.getSimpleName(), IdmIdentityContract_.validTill.getName()));
		attributeValidTill.setRequired(true);
		attributeValidTill.setMax(BigDecimal.valueOf(3));
		
		formProjection.setFormValidations(mapper.writeValueAsString(Lists.newArrayList(
				attributeExternalCode, attributeLastName, attributeValidTill)));
		formProjection = projectionService.save(formProjection);
		//
		// create identity with projection is defined
		IdmIdentityDto identity = new IdmIdentityDto(getHelper().createName());
		identity.setExternalCode(getHelper().createName());
		identity.setLastName("xxx");
		identity.setFormProjection(formProjection.getId());
		IdmIdentityProjectionDto projection = new IdmIdentityProjectionDto(identity);
		//
		// set contract
		IdmIdentityContractDto primeContract = new IdmIdentityContractDto();
		primeContract.setMain(true);
		primeContract.setWorkPosition(getHelper().createTreeNode().getId());
		primeContract.setPosition(getHelper().createName());
		primeContract.setValidFrom(LocalDate.now().minus(1l, ChronoUnit.DAYS));
		primeContract.setValidTill(LocalDate.now().plus(4l, ChronoUnit.DAYS));
		projection.setContract(primeContract);
		//
		IdentityProjectionEvent identityProjectionEvent = new IdentityProjectionEvent(IdentityProjectionEventType.CREATE, projection);
		identityProjectionEvent.setPriority(PriorityType.IMMEDIATE);
		manager.publish(identityProjectionEvent);
	}
}
