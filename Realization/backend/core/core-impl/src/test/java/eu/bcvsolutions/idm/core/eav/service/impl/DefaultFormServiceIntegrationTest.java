package eu.bcvsolutions.idm.core.eav.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.CoreModuleDescriptor;
import eu.bcvsolutions.idm.core.api.config.domain.EventConfiguration;
import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.api.domain.PriorityType;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.FormableDto;
import eu.bcvsolutions.idm.core.api.dto.IdmEntityEventDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.DefaultFormableFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmEntityEventFilter;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.api.event.CoreEvent;
import eu.bcvsolutions.idm.core.api.event.CoreEvent.CoreEventType;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.IdmCacheManager;
import eu.bcvsolutions.idm.core.api.service.IdmEntityEventService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.eav.api.domain.FormDefinitionCache;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.FormDefinitionAttributes;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListItemDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.InvalidFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmFormValueFilter;
import eu.bcvsolutions.idm.core.eav.api.exception.ChangeConfidentialException;
import eu.bcvsolutions.idm.core.eav.api.exception.ChangePersistentTypeException;
import eu.bcvsolutions.idm.core.eav.api.service.AbstractFormableService;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.FormValueService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListItemService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormAttribute;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormAttribute_;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormDefinition;
import eu.bcvsolutions.idm.core.eav.processor.FormAttributeValidateProcessor;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.ecm.service.impl.DefaultAttachmentManagerIntegrationTest;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmEntityEvent_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.model.entity.IdmRole;
import eu.bcvsolutions.idm.core.model.entity.IdmTreeNode;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmIdentityFormValue;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmRoleFormValue;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmRoleFormValue_;
import eu.bcvsolutions.idm.core.model.event.IdentityEvent.IdentityEventType;
import eu.bcvsolutions.idm.core.model.event.processor.module.InitDemoDataProcessor;
import eu.bcvsolutions.idm.core.model.repository.IdmRoleRepository;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.evaluator.eav.AbstractFormValueEvaluator;
import eu.bcvsolutions.idm.core.security.evaluator.eav.IdentityFormValueEvaluator;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Form service integration tests.
 *
 * @author Radek Tomiška
 *
 */
@Transactional
public class DefaultFormServiceIntegrationTest extends AbstractIntegrationTest {

	private final static String FORM_VALUE_ONE = "one";
	private final static String FORM_VALUE_TWO = "two";
	private final static String FORM_VALUE_THREE = "three";
	private final static String FORM_VALUE_FOUR = "four";

	@Autowired private ApplicationContext context;
	@Autowired private IdmIdentityService identityService;
	@Autowired private IdmFormDefinitionService formDefinitionService;
	@Autowired private IdmFormAttributeService formAttributeService;
	@Autowired private IdmRoleRepository roleRepository;
	@Autowired private LookupService lookupService;
	@Autowired private AttachmentManager attachmentManager;
	@Autowired private IdmEntityEventService entityEventService;
	@Autowired private EventConfiguration eventConfiguration;
	@Autowired private EntityEventManager entityEventManager;
	@Autowired private IdmCacheManager cacheManager;
	@Autowired private IdmCodeListService codeListService;
	@Autowired private IdmCodeListItemService codeListItemService;
	//
	private DefaultFormService formService;

	@Before
	public void init() {
		formService = context.getAutowireCapableBeanFactory().createBean(DefaultFormService.class);
	}

	@Test
	public void testFillFormValues() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		//
		// create definition one
		IdmFormAttributeDto attributeDefinitionOne = new IdmFormAttributeDto();
		attributeDefinitionOne.setCode(getHelper().createName());
		attributeDefinitionOne.setName(attributeDefinitionOne.getCode());
		attributeDefinitionOne.setPersistentType(PersistentType.TEXT);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentity.class.getCanonicalName(), getHelper().createName(), Lists.newArrayList(attributeDefinitionOne));
		attributeDefinitionOne = formDefinitionOne.getMappedAttributeByCode(attributeDefinitionOne.getCode());
		//
		// create definition two
		IdmFormAttributeDto attributeDefinitionTwo = new IdmFormAttributeDto();
		attributeDefinitionTwo.setCode(getHelper().createName());
		attributeDefinitionTwo.setName(attributeDefinitionTwo.getCode());
		attributeDefinitionTwo.setPersistentType(PersistentType.TEXT);
		IdmFormDefinitionDto formDefinitionTwo = formService.createDefinition(IdmIdentity.class.getCanonicalName(), getHelper().createName(), Lists.newArrayList(attributeDefinitionTwo));
		attributeDefinitionTwo = formDefinitionTwo.getMappedAttributeByCode(attributeDefinitionTwo.getCode());
		//
		IdmFormValueDto value1 = new IdmFormValueDto(attributeDefinitionOne);
		value1.setValue(FORM_VALUE_ONE);

		IdmFormValueDto value2 = new IdmFormValueDto(attributeDefinitionTwo);
		value2.setValue(FORM_VALUE_TWO);

		formService.saveValues(owner, formDefinitionOne, Lists.newArrayList(value1));
		formService.saveValues(owner, formDefinitionTwo, Lists.newArrayList(value2));

		assertEquals(FORM_VALUE_ONE, formService.getValues(owner, formDefinitionOne).get(0).getStringValue());
		assertEquals(FORM_VALUE_TWO, formService.getValues(owner, formDefinitionTwo).get(0).getStringValue());
		//
		// create second owner
		Identifiable owner2 = getHelper().createIdentity((GuardedString) null);

		assertEquals(0, formService.getValues(owner2, formDefinitionOne).size());
		assertEquals(1, formService.getValues(owner, formDefinitionOne).size());
		assertEquals(1, formService.getValues(owner, formDefinitionTwo).size());

		identityService.deleteById(owner2.getId());

		assertEquals(0, formService.getValues(new IdmIdentity((UUID)owner2.getId()), formDefinitionOne).size()); // TODO: remove this test? - not exists entity has to be given
		assertEquals(1, formService.getValues(owner, formDefinitionOne).size());
		assertEquals(1, formService.getValues(owner, formDefinitionTwo).size());

		formService.deleteValues(owner, formDefinitionOne);
		assertEquals(0, formService.getValues(owner, formDefinitionOne).size());
		assertEquals(1, formService.getValues(owner, formDefinitionTwo).size());
		assertEquals(FORM_VALUE_TWO, formService.getValues(owner, formDefinitionTwo).get(0).getStringValue());

		identityService.deleteById(owner.getId());

		assertEquals(0, formService.getValues(new IdmIdentity((UUID)owner.getId()), formDefinitionOne).size());
		assertEquals(0, formService.getValues(new IdmIdentity((UUID)owner.getId()), formDefinitionTwo).size());
	}

	/**
	 * Test multi values order and removal
	 */
	@Test
	public void testMultipleValues() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		//
		// create definition with multi parameter
		IdmFormAttributeDto multiAttribite = new IdmFormAttributeDto();
		String multiAttributeName = getHelper().createName();
		multiAttribite.setCode(multiAttributeName);
		multiAttribite.setName(multiAttribite.getCode());
		multiAttribite.setPersistentType(PersistentType.TEXT);
		multiAttribite.setMultiple(true);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentity.class.getCanonicalName(), getHelper().createName(), Lists.newArrayList(multiAttribite));
		multiAttribite = formDefinitionOne.getMappedAttributeByCode(multiAttribite.getCode());
		//
		// fill values
		IdmFormValueDto value1 = new IdmFormValueDto(multiAttribite);
		value1.setValue(FORM_VALUE_ONE);
		value1.setSeq((short) 0);

		IdmFormValueDto value2 = new IdmFormValueDto(multiAttribite);
		value2.setValue(FORM_VALUE_TWO);
		value2.setSeq((short) 1);

		formService.saveValues(owner, formDefinitionOne, Lists.newArrayList(value1, value2));

		Map<String, List<IdmFormValueDto>> m = formService.getFormInstance(owner, formDefinitionOne).toValueMap();

		// check order
		assertEquals(2, m.get(multiAttributeName).size());
		assertEquals(FORM_VALUE_ONE, (m.get(multiAttributeName).get(0)).getValue());
		assertEquals(FORM_VALUE_TWO, (m.get(multiAttributeName).get(1)).getValue());

		formService.saveValues(owner, formDefinitionOne, Lists.newArrayList(value1));

		// check delete unsaved multiple values
		m = formService.getFormInstance(owner, formDefinitionOne).toValueMap();
		assertEquals(1, m.get(multiAttributeName).size());
		assertEquals(FORM_VALUE_ONE, m.get(multiAttributeName).get(0).getValue());

		// checks value map
		Map<String, ? extends List<Serializable>> v = formService.getFormInstance(owner, formDefinitionOne).toPersistentValueMap();
		assertEquals(1, v.get(multiAttributeName).size());
		assertEquals(FORM_VALUE_ONE, v.get(multiAttributeName).get(0));
	}

	@Test
	public void testDefaultDefinitionType() {
		assertEquals(IdmIdentity.class.getCanonicalName(), formService.getDefaultDefinitionType(IdmIdentity.class));
	}

	@Test
	public void testReadDefaultDefinition() {
		IdmFormDefinitionDto formDefinition = formService.getDefinition(IdmIdentity.class);

		Assert.assertNotNull(formDefinition);
		Assert.assertEquals(IdmFormDefinitionService.DEFAULT_DEFINITION_CODE, formDefinition.getCode());
		Assert.assertEquals(IdmFormDefinitionService.DEFAULT_DEFINITION_CODE, formDefinition.getName());
		Assert.assertEquals(PersistentType.TEXT, formDefinition.getMappedAttributeByCode(InitDemoDataProcessor.FORM_ATTRIBUTE_PHONE).getPersistentType());
	}

	@Test
	public void testGetMainDefinition() {
		IdmFormDefinitionDto mainDefinition = formService.createDefinition(IdmIdentityDto.class, getHelper().createName(), null);
		mainDefinition.setMain(true);
		mainDefinition = formService.saveDefinition(mainDefinition);
		IdmFormDefinitionDto otherDefinition = formService.createDefinition(IdmIdentity.class, getHelper().createName(), null);
		//
		IdmFormDefinitionDto result = formService.getDefinition(IdmIdentity.class, (String) null);
		//
		Assert.assertEquals(mainDefinition, result);
		//
		result = formService.getDefinition(formService.getDefaultDefinitionType(IdmIdentity.class));
		//
		Assert.assertEquals(mainDefinition, result);
		//
		result = formService.getDefinition(formService.getDefaultDefinitionType(IdmIdentity.class), otherDefinition.getCode());
		//
		Assert.assertEquals(otherDefinition, result);
	}

	@Test
	public void testGetDefinitions() {
		IdmIdentityDto owner = getHelper().createIdentity((GuardedString) null);
		//
		IdmFormDefinitionDto definitionOne = formService.createDefinition(IdmIdentity.class, getHelper().createName(), null);
		IdmFormDefinitionDto definitionTwo = formService.createDefinition(owner.getClass(), getHelper().createName(), null);
		IdmFormDefinitionDto definitionOther = formService.createDefinition(IdmRole.class, getHelper().createName(), null);
		//
		List<IdmFormDefinitionDto> results = formService.getDefinitions(IdmIdentity.class);
		//
		Assert.assertTrue(results.stream().anyMatch(d -> d.getId().equals(definitionOne.getId())));
		Assert.assertTrue(results.stream().anyMatch(d -> d.getId().equals(definitionTwo.getId())));
		Assert.assertFalse(results.stream().anyMatch(d -> d.getId().equals(definitionOther.getId())));
		//
		results = formService.getDefinitions(owner.getClass());
		//
		Assert.assertTrue(results.stream().anyMatch(d -> d.getId().equals(definitionOne.getId())));
		Assert.assertTrue(results.stream().anyMatch(d -> d.getId().equals(definitionTwo.getId())));
		Assert.assertFalse(results.stream().anyMatch(d -> d.getId().equals(definitionOther.getId())));
		//
		results = formService.getDefinitions(owner);
		//
		Assert.assertTrue(results.stream().anyMatch(d -> d.getId().equals(definitionOne.getId())));
		Assert.assertTrue(results.stream().anyMatch(d -> d.getId().equals(definitionTwo.getId())));
		Assert.assertFalse(results.stream().anyMatch(d -> d.getId().equals(definitionOther.getId())));
	}

	@Test
	public void testIsFormable() {
		Assert.assertTrue(formService.isFormable(IdmIdentity.class));
		Assert.assertTrue(formService.isFormable(IdmIdentityDto.class));
		Assert.assertTrue(formService.isFormable(IdmRoleDto.class));
		//
		Assert.assertFalse(formService.isFormable(IdmFormDefinitionDto.class));
	}

	@Test
	public void testGetOwnerTypes() {
		List<String> ownerTypes = formService.getOwnerTypes();
		//
		Assert.assertTrue(ownerTypes.stream().anyMatch(o -> o.equals(formService.getDefaultDefinitionType(IdmIdentity.class))));
		Assert.assertTrue(ownerTypes.stream().anyMatch(o -> o.equals(formService.getDefaultDefinitionType(IdmRoleDto.class))));
		//
		Assert.assertFalse(ownerTypes.stream().anyMatch(o -> o.equals(IdmFormDefinition.class.getCanonicalName())));
	}
	
	@Test
	public void testGetAvailableFormValueServices() {
		List<FormValueService<?>> installedServices = formService.getAvailableFormValueServices();
		//
		// core services all time
		Assert.assertTrue(installedServices.stream().anyMatch(s -> s.getEntityClass().equals(IdmIdentityFormValue.class)));
	}
	
	@Test
	public void testGetFormValueService() {
		FormValueService<?> service = formService.getFormValueService(IdmIdentityDto.class);
		// isentity service
		Assert.assertEquals(IdmIdentityFormValue.class, service.getEntityClass());
	}

	@Test
	public void testGetAttributes() {
		IdmFormAttributeDto attributeOne = new IdmFormAttributeDto(getHelper().createName(), getHelper().createName(), PersistentType.SHORTTEXT);
		IdmFormAttributeDto attributeTwo = new IdmFormAttributeDto(getHelper().createName(), getHelper().createName(), PersistentType.SHORTTEXT);
		IdmFormDefinitionDto definitionOne = formService.createDefinition(
				IdmIdentity.class,
				getHelper().createName(),
				Lists.newArrayList(attributeOne, attributeTwo));
		IdmFormAttributeDto attributeOther = new IdmFormAttributeDto(getHelper().createName(), getHelper().createName(), PersistentType.SHORTTEXT);
		IdmFormDefinitionDto definitionOther = formService.createDefinition(
				IdmIdentityDto.class,
				getHelper().createName(),
				Lists.newArrayList(attributeOther));
		Assert.assertEquals(CoreModuleDescriptor.MODULE_ID, definitionOther.getModule());
		//
		List<IdmFormAttributeDto> results = formService.getAttributes(definitionOne);
		//
		Assert.assertTrue(results.stream().anyMatch(a -> a.getCode().equals(attributeOne.getCode())));
		Assert.assertTrue(results.stream().anyMatch(a -> a.getCode().equals(attributeTwo.getCode())));
		Assert.assertFalse(results.stream().anyMatch(a -> a.getCode().equals(attributeOther.getCode())));
		//
		results = formService.getAttributes(definitionOther);
		//
		Assert.assertFalse(results.stream().anyMatch(a -> a.getCode().equals(attributeOne.getCode())));
		Assert.assertFalse(results.stream().anyMatch(a -> a.getCode().equals(attributeTwo.getCode())));
		Assert.assertTrue(results.stream().anyMatch(a -> a.getCode().equals(attributeOther.getCode())));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testOwnerWithoutId() {
		// unpersisted identity
		Identifiable owner = new IdmIdentity();
		formService.getValues(owner);
	}

	@Test
	public void testUnpersistedOwnerWithId() {
		// unpersisted identity
		Identifiable owner = new IdmIdentity(UUID.randomUUID());
		assertTrue(formService.getValues(owner).isEmpty());
	}

	@Test
	public void testReadDefaultDefinitionValue() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		IdmFormDefinitionDto formDefinition = formService.getDefinition(IdmIdentity.class);
		// save value into default owner and default form definition
		IdmFormValueDto value1 = new IdmFormValueDto(formDefinition.getMappedAttributeByCode(InitDemoDataProcessor.FORM_ATTRIBUTE_PHONE));
		value1.setValue(FORM_VALUE_ONE);

		formService.saveValues(owner, formDefinition, Lists.newArrayList(value1));

		IdmFormInstanceDto savedValues = formService.getFormInstance(owner);
		assertEquals(1, savedValues.getValues().size());
		assertEquals(FORM_VALUE_ONE, savedValues.toSinglePersistentValue(InitDemoDataProcessor.FORM_ATTRIBUTE_PHONE));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReadDefaultDefinitionValueNotSingle() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		IdmFormDefinitionDto formDefinition = formService.getDefinition(IdmIdentity.class);
		// save value into default owner and default form definition
		IdmFormValueDto value1 = new IdmFormValueDto(formDefinition.getMappedAttributeByCode(InitDemoDataProcessor.FORM_ATTRIBUTE_WWW));
		value1.setValue(FORM_VALUE_ONE);
		IdmFormValueDto value2 = new IdmFormValueDto(formDefinition.getMappedAttributeByCode(InitDemoDataProcessor.FORM_ATTRIBUTE_WWW));
		value2.setValue(FORM_VALUE_TWO);

		formService.saveValues(owner, formDefinition, Lists.newArrayList(value1, value2));

		IdmFormInstanceDto savedValues = formService.getFormInstance(owner);
		assertEquals(2, savedValues.getValues().size());
		savedValues.toSinglePersistentValue(InitDemoDataProcessor.FORM_ATTRIBUTE_WWW);
	}

	@Test
	public void testReadConfidentialFormValue() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		IdmFormDefinitionDto formDefinition = formService.getDefinition(IdmIdentity.class);
		// save password
		IdmFormValueDto value1 = new IdmFormValueDto(formDefinition.getMappedAttributeByCode(InitDemoDataProcessor.FORM_ATTRIBUTE_PASSWORD));
		value1.setValue(FORM_VALUE_ONE);

		formService.saveValues(owner, formDefinition, Lists.newArrayList(value1));

		IdmFormInstanceDto savedValues = formService.getFormInstance(owner);
		assertEquals(1, savedValues.getValues().size());
		assertEquals(GuardedString.SECRED_PROXY_STRING, savedValues.toSinglePersistentValue(InitDemoDataProcessor.FORM_ATTRIBUTE_PASSWORD));
	}

	@Test(expected = ResultCodeException.class)
	public void testDeleteDefinitionWithFormValues() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		IdmFormDefinitionDto formDefinition = formService.getDefinition(IdmIdentity.class);

		// save password
		IdmFormValueDto value1 = new IdmFormValueDto(formDefinition.getMappedAttributeByCode(InitDemoDataProcessor.FORM_ATTRIBUTE_PASSWORD));
		value1.setValue(FORM_VALUE_ONE);

		formService.saveValues(owner, formDefinition, Lists.newArrayList(value1));

		List<IdmFormValueDto> savedValues = formService.getValues(owner);
		assertEquals(1, savedValues.size());

		formDefinitionService.delete(formDefinition);
	}

	@Test
	public void testSaveSingleAttributeValues() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		IdmFormDefinitionDto formDefinition = formService.getDefinition(IdmIdentity.class);
		IdmFormAttributeDto attribute = formDefinition.getMappedAttributeByCode(InitDemoDataProcessor.FORM_ATTRIBUTE_PHONE);
		// save value
		List<IdmFormValueDto> attributeValues = formService.saveValues(owner, attribute, Lists.newArrayList(FORM_VALUE_ONE));

		assertEquals(1, attributeValues.size());
		assertEquals(FORM_VALUE_ONE, attributeValues.get(0).getValue());

		attributeValues = formService.getValues(owner, attribute);

		assertEquals(1, attributeValues.size());
		assertEquals(FORM_VALUE_ONE, attributeValues.get(0).getValue());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSaveMultipleAttributeValuesToSingleAttribute() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		IdmFormDefinitionDto formDefinition = formService.getDefinition(IdmIdentity.class);
		IdmFormAttributeDto attribute = formDefinition.getMappedAttributeByCode(InitDemoDataProcessor.FORM_ATTRIBUTE_PHONE);
		// save value
		formService.saveValues(owner, attribute, Lists.newArrayList(FORM_VALUE_ONE, FORM_VALUE_TWO));
	}

	@Test
	public void testDeleteSingleAttributeValues() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		IdmFormDefinitionDto formDefinition = formService.getDefinition(IdmIdentity.class);
		IdmFormAttributeDto attribute = formDefinition.getMappedAttributeByCode(InitDemoDataProcessor.FORM_ATTRIBUTE_PHONE);
		IdmFormAttributeDto attributeWWW = formDefinition.getMappedAttributeByCode(InitDemoDataProcessor.FORM_ATTRIBUTE_WWW);
		// save value
		formService.saveValues(owner, attribute, Lists.newArrayList(FORM_VALUE_ONE));
		formService.saveValues(owner, attributeWWW, Lists.newArrayList(FORM_VALUE_ONE, FORM_VALUE_TWO));
		//
		List<IdmFormValueDto> attributeValues = formService.getValues(owner, attribute);
		assertEquals(1, attributeValues.size());
		assertEquals(FORM_VALUE_ONE, attributeValues.get(0).getValue());
		List<IdmFormValueDto> attributeWWWValues = formService.getValues(owner, formDefinition, InitDemoDataProcessor.FORM_ATTRIBUTE_WWW);
		assertEquals(2, attributeWWWValues.size());
		assertEquals(FORM_VALUE_ONE, attributeWWWValues.get(0).getValue());
		//
		formService.deleteValues(owner, attribute);
		//
		attributeValues = formService.getValues(owner, attribute);
		assertEquals(0, attributeValues.size());
		attributeWWWValues = formService.getValues(owner, attributeWWW);
		assertEquals(2, attributeWWWValues.size());
		assertEquals(FORM_VALUE_ONE, attributeWWWValues.get(0).getValue());
	}

	@Test
	public void testEditMultipleAttributeValues() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		// save value
		formService.saveValues(owner, InitDemoDataProcessor.FORM_ATTRIBUTE_WWW, Lists.newArrayList(FORM_VALUE_ONE, FORM_VALUE_TWO));
		//
		List<IdmFormValueDto> attributeWWWValues = formService.getValues(owner, InitDemoDataProcessor.FORM_ATTRIBUTE_WWW);
		assertEquals(2, attributeWWWValues.size());
		assertEquals(FORM_VALUE_ONE, attributeWWWValues.get(0).getValue());
		// update
		formService.saveValues(owner, InitDemoDataProcessor.FORM_ATTRIBUTE_WWW, Lists.newArrayList(FORM_VALUE_TWO));
		//
		attributeWWWValues = formService.getValues(owner, InitDemoDataProcessor.FORM_ATTRIBUTE_WWW);
		assertEquals(1, attributeWWWValues.size());
		assertEquals(FORM_VALUE_TWO, attributeWWWValues.get(0).getValue());
	}

	@Test
	public void testFindOwnersByMultiStringAttributeValue() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		Identifiable ownerTwo = getHelper().createIdentity((GuardedString) null);
		Identifiable ownerThree = getHelper().createIdentity((GuardedString) null);
		IdmFormDefinitionDto formDefinition = formService.getDefinition(owner.getClass());
		IdmFormAttributeDto attribute = formDefinition.getMappedAttributeByCode(InitDemoDataProcessor.FORM_ATTRIBUTE_WWW);
		// save values
		formService.saveValues(owner, attribute, Lists.newArrayList(FORM_VALUE_ONE, FORM_VALUE_TWO));
		formService.saveValues(ownerTwo, attribute, Lists.newArrayList(FORM_VALUE_THREE, FORM_VALUE_TWO));
		formService.saveValues(ownerThree, attribute, Lists.newArrayList(FORM_VALUE_FOUR, FORM_VALUE_FOUR));
		//
		Page<? extends Identifiable> owners = formService.findOwners(owner.getClass(), attribute, FORM_VALUE_ONE, null);
		//
		assertEquals(1, owners.getTotalElements());
		assertEquals(owner.getId(), owners.getContent().get(0).getId());
		//
		owners = formService.findOwners(owner.getClass(), attribute.getCode(), FORM_VALUE_TWO, null);
		assertEquals(2, owners.getTotalElements());
		//
		owners = formService.findOwners(owner.getClass(), attribute, FORM_VALUE_FOUR, null);
		assertEquals(1, owners.getTotalElements());
	}

	@Test
	public void testFindOwnersByStringAttributeValue() {
		IdmIdentityDto owner = getHelper().createIdentity((GuardedString) null);
		IdmIdentityDto ownerTwo = getHelper().createIdentity((GuardedString) null);
		IdmIdentityDto ownerThree = getHelper().createIdentity((GuardedString) null);
		IdmFormDefinitionDto formDefinition = formService.getDefinition(IdmIdentity.class);
		IdmFormAttributeDto attribute = formDefinition.getMappedAttributeByCode(InitDemoDataProcessor.FORM_ATTRIBUTE_PHONE);
		// save values
		formService.saveValues(owner.getId(), IdmIdentity.class, attribute, Lists.newArrayList(FORM_VALUE_ONE));
		formService.saveValues(ownerTwo.getId(), IdmIdentity.class, attribute, Lists.newArrayList(FORM_VALUE_TWO));
		formService.saveValues(ownerThree.getId(), IdmIdentity.class, attribute, Lists.newArrayList(FORM_VALUE_FOUR));
		//
		Page<? extends Identifiable> owners = formService.findOwners(IdmIdentity.class, attribute, FORM_VALUE_ONE, null);
		//
		assertEquals(1, owners.getTotalElements());
		assertEquals(owner.getId(), owners.getContent().get(0).getId());
		//
		owners = formService.findOwners(IdmIdentity.class, attribute.getCode(), FORM_VALUE_TWO, null);
		assertEquals(1, owners.getTotalElements());
		//
		owners = formService.findOwners(IdmIdentity.class, attribute, FORM_VALUE_FOUR, null);
		assertEquals(1, owners.getTotalElements());
	}

	@Test
	public void testFindOwnersByCodeListAttributeValue() {
		IdmIdentityDto owner = getHelper().createIdentity((GuardedString) null);
		IdmIdentityDto ownerTwo = getHelper().createIdentity((GuardedString) null);
		IdmIdentityDto ownerThree = getHelper().createIdentity((GuardedString) null);
		IdmFormDefinitionDto formDefinition = formService.getDefinition(IdmIdentity.class);
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		attribute.setCode(getHelper().createName());
		attribute.setFormDefinition(formDefinition.getId());
		attribute.setName(getHelper().createName());
		attribute.setPersistentType(PersistentType.CODELIST);
		//
		IdmCodeListDto codeList = new IdmCodeListDto();
		codeList.setCode(getHelper().createName());
		codeList.setName(getHelper().createName());
		codeList = codeListService.save(codeList);
		attribute.setFaceType(codeList.getCode());
		IdmCodeListItemDto codeListItem = new IdmCodeListItemDto();
		codeListItem.setCodeList(codeList.getId());
		final String value = "test";
		codeListItem.setCode(value);
		codeListItem.setName(value);
		codeListItem = codeListItemService.save(codeListItem);
		//
		attribute = formService.saveAttribute(attribute);
		// save values
		formService.saveValues(owner.getId(), IdmIdentity.class, attribute, Lists.newArrayList(value));
		//
		Page<? extends Identifiable> owners = formService.findOwners(IdmIdentity.class, attribute, value, null);
		//
		assertEquals(1, owners.getTotalElements());
		assertEquals(owner.getId(), owners.getContent().get(0).getId());
	}

	@Test
	public void testFindTreeNodesByNullShortTextAttributeValue() {
		IdmTreeNodeDto owner = getHelper().createTreeNode();
		//
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		attribute.setCode(getHelper().createName());
		attribute.setName(attribute.getCode());
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmTreeNode.class.getCanonicalName(), getHelper().createName(), Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		// save values
		formService.saveValues(owner.getId(), IdmTreeNode.class, attribute, Lists.newArrayList(FORM_VALUE_ONE));
		//
		Page<? extends Identifiable> owners = formService.findOwners(IdmTreeNode.class, attribute, FORM_VALUE_ONE, null);
		//
		assertEquals(1, owners.getTotalElements());
		assertEquals(owner.getId(), owners.getContent().get(0).getId());
		//
		owners = formService.findOwners(IdmTreeNode.class, attribute, null, null);
		assertEquals(0, owners.getTotalElements());
	}

	@Test
	public void testFindTreeNodesByNullTextAttributeValue() {
		IdmTreeNodeDto owner = getHelper().createTreeNode();
		//
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		attribute.setCode(getHelper().createName());
		attribute.setName(attribute.getCode());
		attribute.setPersistentType(PersistentType.TEXT);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmTreeNode.class.getCanonicalName(), getHelper().createName(), Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		// save values
		formService.saveValues(owner.getId(), IdmTreeNode.class, attribute, Lists.newArrayList(FORM_VALUE_ONE));
		//
		Page<? extends Identifiable> owners = formService.findOwners(IdmTreeNode.class, attribute, FORM_VALUE_ONE, null);
		//
		assertEquals(1, owners.getTotalElements());
		assertEquals(owner.getId(), owners.getContent().get(0).getId());
		//
		owners = formService.findOwners(IdmTreeNode.class, attribute, null, null);
		assertEquals(0, owners.getTotalElements());
	}

	@Test
	public void testFindOwnersByDateAttributeValue() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		Identifiable ownerTwo = getHelper().createIdentity((GuardedString) null);

		IdmFormDefinitionDto formDefinition = formService.getDefinition(owner.getClass());
		IdmFormAttributeDto attribute = formDefinition.getMappedAttributeByCode(InitDemoDataProcessor.FORM_ATTRIBUTE_DATETIME);
		// save values
		ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS);
		ZonedDateTime tomorrow =  now.plusDays(1).truncatedTo(ChronoUnit.MILLIS);
		formService.saveValues(owner, attribute, Lists.newArrayList(now));
		formService.saveValues(ownerTwo, attribute, Lists.newArrayList(tomorrow));
		//
		Page<? extends Identifiable> owners = formService.findOwners(owner.getClass(), InitDemoDataProcessor.FORM_ATTRIBUTE_DATETIME, now, null);
		//
		assertEquals(1, owners.getTotalElements());
		assertEquals(owner.getId(), owners.getContent().get(0).getId());
		//
		owners = formService.findOwners(owner.getClass(), InitDemoDataProcessor.FORM_ATTRIBUTE_DATETIME, tomorrow, null);
		//
		assertEquals(1, owners.getTotalElements());
		assertEquals(ownerTwo.getId(), owners.getContent().get(0).getId());
		//
		identityService.deleteById(owner.getId());
		identityService.deleteById(ownerTwo.getId());
	}

	@Test
	public void testFindOwnersByUuidAttributeValue() {
		IdmIdentityDto owner = getHelper().createIdentity((GuardedString) null);
		IdmIdentityDto ownerTwo = getHelper().createIdentity((GuardedString) null);

		IdmFormDefinitionDto formDefinition = formService.getDefinition(owner.getClass());
		IdmFormAttributeDto attribute = formDefinition.getMappedAttributeByCode(InitDemoDataProcessor.FORM_ATTRIBUTE_UUID);
		// save values
		UUID one = UUID.randomUUID();
		UUID two = UUID.randomUUID();
		formService.saveValues(owner, attribute, Lists.newArrayList(one));
		formService.saveValues(ownerTwo, attribute, Lists.newArrayList(two));
		//
		Page<? extends Identifiable> owners = formService.findOwners(owner.getClass(), InitDemoDataProcessor.FORM_ATTRIBUTE_UUID, one, null);
		//
		assertEquals(1, owners.getTotalElements());
		assertEquals(owner.getId(), owners.getContent().get(0).getId());
		//
		owners = formService.findOwners(owner.getClass(), InitDemoDataProcessor.FORM_ATTRIBUTE_UUID, two, null);
		//
		assertEquals(1, owners.getTotalElements());
		assertEquals(ownerTwo.getId(), owners.getContent().get(0).getId());
		//
		identityService.deleteById(owner.getId());
		identityService.deleteById(ownerTwo.getId());
	}

	@Test
	public void testFindOwnersByShortTextAttributeValue() {
		IdmIdentityDto owner = getHelper().createIdentity((GuardedString) null);
		IdmIdentityDto ownerTwo = getHelper().createIdentity((GuardedString) null);

		IdmFormAttributeDto shortTextAttr = new IdmFormAttributeDto();
		String shortTextAttrName = getHelper().createName();
		shortTextAttr.setCode(shortTextAttrName);
		shortTextAttr.setName(shortTextAttrName);
		shortTextAttr.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormAttributeDto attribute = formService.saveAttribute(owner.getClass(), shortTextAttr);
		// save values
		String one = "one";
		String two = "two";
		formService.saveValues(owner, attribute, Lists.newArrayList(one));
		formService.saveValues(ownerTwo, attribute, Lists.newArrayList(two));
		//
		Page<? extends Identifiable> owners = formService.findOwners(owner.getClass(), shortTextAttrName, one, null);
		//
		assertEquals(1, owners.getTotalElements());
		assertEquals(owner.getId(), owners.getContent().get(0).getId());
		//
		owners = formService.findOwners(owner.getClass(), shortTextAttrName, two, null);
		//
		assertEquals(1, owners.getTotalElements());
		assertEquals(ownerTwo.getId(), owners.getContent().get(0).getId());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testFindOwnersByConfidentialAttribute() {
		IdmIdentityDto owner = getHelper().createIdentity((GuardedString) null);
		//
		IdmFormAttributeDto confidentialAttr = new IdmFormAttributeDto(getHelper().createName());
		confidentialAttr.setConfidential(true);
		confidentialAttr = formService.saveAttribute(owner.getClass(), confidentialAttr);
		//
		formService.findOwners(owner.getClass(), confidentialAttr, "test", null);
	}

	@Test
	public void testFindAttribute() {
		IdmFormAttributeDto attribute = formService.getAttribute(IdmIdentity.class, InitDemoDataProcessor.FORM_ATTRIBUTE_DATETIME);
		//
		assertNotNull(attribute);
		assertEquals(PersistentType.DATETIME, attribute.getPersistentType());
	}

	@Test
	public void testFindAttributeNotExist() {
		IdmFormAttributeDto attribute = formService.getAttribute(IdmIdentity.class, "notExist_test");
		//
		assertNull(attribute);
	}

	@Test
	public void testSaveAttribute() {
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attribute.getCode());
		attribute.setPersistentType(PersistentType.TEXT);
		//
		attribute = formService.saveAttribute(IdmIdentity.class, attribute);
		//
		IdmFormAttributeDto savedAttr = formService.getAttribute(IdmIdentity.class, attribute.getCode());
		//
		assertNotNull(savedAttr);
		assertEquals(PersistentType.TEXT, savedAttr.getPersistentType());
		assertEquals(formService.getDefinition(IdmIdentity.class).getId(), savedAttr.getFormDefinition());
		//
		formService.deleteAttribute(attribute);
		//
		savedAttr = formService.getAttribute(IdmIdentity.class, attribute.getCode());
		//
		assertNull(savedAttr);
	}

	@Test
	public void findOwnerByCriteria() {
		IdmRoleDto owner = getHelper().createRole();
		IdmRoleDto ownerTwo = getHelper().createRole();

		IdmFormDefinitionDto formDefinition = formService.getDefinition(IdmRole.class);
		IdmFormAttributeDto attribute = formDefinition.getMappedAttributeByCode("extAttr");
		if (formDefinition == null) {
			formDefinition = getHelper().createFormDefinition(IdmRole.class.getCanonicalName());
		}
		if (attribute == null) {
			attribute = getHelper().createEavAttribute("extAttr", IdmRole.class, PersistentType.SHORTTEXT);
		}
		//
		formService.saveValues(owner.getId(), IdmRole.class, attribute, Lists.newArrayList("test"));
		formService.saveValues(ownerTwo.getId(), IdmRole.class, attribute, Lists.newArrayList("test2"));

		IdmFormAttributeDto finalAttribute = attribute; // to please the compiler
		Specification<IdmRole> criteria = new Specification<IdmRole>() {
			private static final long serialVersionUID = 1L;

			public Predicate toPredicate(Root<IdmRole> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
				Subquery<IdmRoleFormValue> subquery = query.subquery(IdmRoleFormValue.class);
				Root<IdmRoleFormValue> subRoot = subquery.from(IdmRoleFormValue.class);
				subquery.select(subRoot);

				Predicate predicate = builder.and(
						builder.equal(subRoot.get(IdmRoleFormValue_.owner), root),
						builder.equal(subRoot.get(IdmRoleFormValue_.formAttribute).get(IdmFormAttribute_.id), finalAttribute.getId()),
						builder.equal(subRoot.get(IdmRoleFormValue_.stringValue), "test"));
				subquery.where(predicate);
				//
				return query.where(builder.exists(subquery)).getRestriction();
			}
		};
		List<IdmRole> roles = roleRepository.findAll(criteria);
		assertEquals(1, roles.size());
		assertEquals(owner.getId(), roles.get(0).getId());
	}

	@Test
	public void testSaveValuesByOwnerId() {
		Identifiable owner =getHelper().createIdentity((GuardedString) null);
		IdmFormDefinitionDto formDefinition = formService.getDefinition(IdmIdentity.class);
		IdmFormAttributeDto attribute = formDefinition.getMappedAttributeByCode(InitDemoDataProcessor.FORM_ATTRIBUTE_PHONE);
		// save value
		List<IdmFormValueDto> attributeValues = formService.saveValues(
				(UUID) owner.getId(), owner.getClass(), attribute, Lists.newArrayList(FORM_VALUE_ONE));

		Assert.assertEquals(1, attributeValues.size());
		Assert.assertEquals(FORM_VALUE_ONE, attributeValues.get(0).getValue());

		List<IdmFormValueDto> getValues = formService.getValues((UUID) owner.getId(), owner.getClass(), attribute);

		Assert.assertEquals(1, getValues.size());
		Assert.assertEquals(FORM_VALUE_ONE, ((IdmFormValueDto) getValues.get(0)).getValue());
		Assert.assertEquals(FORM_VALUE_ONE, formService.getValue(owner, getValues.get(0).getId()).getValue());

		//
		identityService.deleteById(owner.getId());
	}

	@Test
	public void testFindDefinitionByDtoClass() {
		IdmFormDefinitionDto definitionByEntityClass = formService.getDefinition(IdmIdentity.class);
		IdmFormDefinitionDto definitionByDtoClass = formService.getDefinition(IdmIdentityDto.class);
		//
		Assert.assertNotNull(definitionByEntityClass);
		Assert.assertEquals(definitionByEntityClass, definitionByDtoClass);
	}

	/**
	 * #1051 - Change value persistent type, when single attribute is saved and attribute persistent type changed
	 */
	@Test
	public void testChangeAttributePersistentType() {
		getHelper().disable(FormAttributeValidateProcessor.class);
		try {
			Identifiable owner = getHelper().createIdentity((GuardedString) null);
			//
			// create definition with attribute
			IdmFormAttributeDto attribute = new IdmFormAttributeDto();
			String attributeName = getHelper().createName();
			attribute.setCode(attributeName);
			attribute.setName(attribute.getCode());
			attribute.setPersistentType(PersistentType.SHORTTEXT);
			IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentity.class.getCanonicalName(), getHelper().createName(), Lists.newArrayList(attribute));
			attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
			//
			// fill values
			formService.saveValues(owner, attribute, Lists.newArrayList(FORM_VALUE_ONE));

			Map<String, List<IdmFormValueDto>> m = formService.getFormInstance(owner, formDefinitionOne).toValueMap();

			// check value and persistent type
			assertEquals(1, m.get(attributeName).size());
			assertEquals(FORM_VALUE_ONE, (m.get(attributeName).get(0)).getValue());
			assertEquals(attribute.getPersistentType(), (m.get(attributeName).get(0)).getPersistentType());
			//
			// change attribute persistent type
			attribute.setPersistentType(PersistentType.TEXT);
			attribute = formService.saveAttribute(attribute);
			// update value
			formService.saveValues(owner, attribute, Lists.newArrayList(FORM_VALUE_TWO));
			m = formService.getFormInstance(owner, formDefinitionOne).toValueMap();
			//
			// check value and persistent type
			assertEquals(1, m.get(attributeName).size());
			assertEquals(FORM_VALUE_TWO, (m.get(attributeName).get(0)).getValue());
			assertEquals(attribute.getPersistentType(), (m.get(attributeName).get(0)).getPersistentType());
		} finally {
			getHelper().enable(FormAttributeValidateProcessor.class);
		}
	}

	@Test
	public void testDeleteEmptyValue() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		//
		// create definition with attribute
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attribute.getCode());
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentity.class.getCanonicalName(), getHelper().createName(), Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		// fill values
		formService.saveValues(owner, attribute, Lists.newArrayList(FORM_VALUE_ONE));

		Map<String, List<IdmFormValueDto>> m = formService.getFormInstance(owner, formDefinitionOne).toValueMap();

		// check value and persistent type
		assertEquals(1, m.get(attributeName).size());
		assertEquals(FORM_VALUE_ONE, (m.get(attributeName).get(0)).getValue());
		assertEquals(attribute.getPersistentType(), (m.get(attributeName).get(0)).getPersistentType());

		// update value
		formService.saveValues(owner, attribute, Lists.newArrayList("")); // empty string
		m = formService.getFormInstance(owner, formDefinitionOne).toValueMap();
		//
		assertEquals(1, m.get(attributeName).size());
		assertEquals("", (m.get(attributeName).get(0)).getValue());
		assertEquals(attribute.getPersistentType(), (m.get(attributeName).get(0)).getPersistentType());
	}

	@Test
	public void testPreserveNotChangedValues() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		//
		// create definition with multi parameter
		IdmFormAttributeDto multiAttribite = new IdmFormAttributeDto();
		String multiAttributeName = getHelper().createName();
		multiAttribite.setCode(multiAttributeName);
		multiAttribite.setName(multiAttribite.getCode());
		multiAttribite.setPersistentType(PersistentType.TEXT);
		multiAttribite.setMultiple(true);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentity.class.getCanonicalName(), getHelper().createName(), Lists.newArrayList(multiAttribite));
		multiAttribite = formDefinitionOne.getMappedAttributeByCode(multiAttribite.getCode());
		//
		// fill values
		IdmFormValueDto value1 = new IdmFormValueDto(multiAttribite);
		value1.setValue(FORM_VALUE_ONE);
		value1.setSeq((short) 0);

		IdmFormValueDto value2 = new IdmFormValueDto(multiAttribite);
		value2.setValue(FORM_VALUE_TWO);
		value2.setSeq((short) 1);

		formService.saveValues(owner, formDefinitionOne, Lists.newArrayList(value1, value2));

		Map<String, List<IdmFormValueDto>> created = formService.getFormInstance(owner, formDefinitionOne).toValueMap();

		// check order
		assertEquals(2, created.get(multiAttributeName).size());
		assertEquals(FORM_VALUE_ONE, created.get(multiAttributeName).get(0).getValue());
		assertEquals(FORM_VALUE_TWO, created.get(multiAttributeName).get(1).getValue());
		//
		// update
		formService.saveValues(owner, formDefinitionOne, Lists.newArrayList(created.get(multiAttributeName).get(0), created.get(multiAttributeName).get(1)));
		//
		// check after update
		// values uuid should be the same
		Map<String, List<IdmFormValueDto>> updated = formService.getFormInstance(owner, formDefinitionOne).toValueMap();
		assertEquals(2, updated.get(multiAttributeName).size());
		assertEquals(FORM_VALUE_ONE, updated.get(multiAttributeName).get(0).getValue());
		assertEquals(FORM_VALUE_TWO, updated.get(multiAttributeName).get(1).getValue());
		assertEquals(created.get(multiAttributeName).get(0).getId(), updated.get(multiAttributeName).get(0).getId());
		assertEquals(created.get(multiAttributeName).get(1).getId(), updated.get(multiAttributeName).get(1).getId());
	}

	@Test
	public void testDeleteNullValue() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		//
		// create definition with attribute
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attribute.getCode());
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentity.class.getCanonicalName(), getHelper().createName(), Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		// fill values
		formService.saveValues(owner, attribute, Lists.newArrayList(FORM_VALUE_ONE));

		Map<String, List<IdmFormValueDto>> m = formService.getFormInstance(owner, formDefinitionOne).toValueMap();

		// check value and persistent type
		assertEquals(1, m.get(attributeName).size());
		assertEquals(FORM_VALUE_ONE, (m.get(attributeName).get(0)).getValue());
		assertEquals(attribute.getPersistentType(), (m.get(attributeName).get(0)).getPersistentType());

		// update value
		formService.saveValues(owner, attribute, Lists.newArrayList());
		m = formService.getFormInstance(owner, formDefinitionOne).toValueMap();
		//
		assertNull(m.get(attributeName));
	}
	
	@Test
	public void testDeleteValue() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		//
		// create definition with attribute
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attribute.getCode());
		attribute.setMultiple(true);
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentity.class.getCanonicalName(), getHelper().createName(), Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		// fill values
		formService.saveValues(owner, attribute, Lists.newArrayList(FORM_VALUE_ONE, FORM_VALUE_TWO));
		Map<String, List<IdmFormValueDto>> m = formService.getFormInstance(owner, formDefinitionOne).toValueMap();
		//
		// check value and persistent type
		Assert.assertEquals(2, m.get(attributeName).size());
		Assert.assertTrue(m.get(attributeName).stream().anyMatch(v -> v.getValue().equals(FORM_VALUE_ONE)));
		Assert.assertTrue(m.get(attributeName).stream().anyMatch(v -> v.getValue().equals(FORM_VALUE_TWO)));
		//
		// delete one value
		formService.deleteValue(m.get(attributeName).stream().filter(v -> v.getValue().equals(FORM_VALUE_ONE)).findFirst().get());
		m = formService.getFormInstance(owner, formDefinitionOne).toValueMap();
		//
		Assert.assertEquals(1, m.get(attributeName).size());
		Assert.assertTrue(m.get(attributeName).stream().anyMatch(v -> v.getValue().equals(FORM_VALUE_TWO)));
	}

	@Test
	public void testMultipleValuesWithNull() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		//
		// create definition with multi parameter
		IdmFormAttributeDto multiAttribite = new IdmFormAttributeDto();
		String multiAttributeName = getHelper().createName();
		multiAttribite.setCode(multiAttributeName);
		multiAttribite.setName(multiAttribite.getCode());
		multiAttribite.setPersistentType(PersistentType.TEXT);
		multiAttribite.setMultiple(true);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentity.class.getCanonicalName(), getHelper().createName(), Lists.newArrayList(multiAttribite));
		multiAttribite = formDefinitionOne.getMappedAttributeByCode(multiAttribite.getCode());
		//
		// fill values
		IdmFormValueDto value1 = new IdmFormValueDto(multiAttribite);
		value1.setValue(FORM_VALUE_ONE);
		value1.setSeq((short) 0);

		IdmFormValueDto value2 = new IdmFormValueDto(multiAttribite);
		value2.setValue(FORM_VALUE_TWO);
		value2.setSeq((short) 1);

		formService.saveValues(owner, formDefinitionOne, Lists.newArrayList(value1, value2));

		Map<String, List<IdmFormValueDto>> m = formService.getFormInstance(owner, formDefinitionOne).toValueMap();

		// check order
		assertEquals(2, m.get(multiAttributeName).size());
		assertEquals(FORM_VALUE_ONE, (m.get(multiAttributeName).get(0)).getValue());
		assertEquals(FORM_VALUE_TWO, (m.get(multiAttributeName).get(1)).getValue());

		value2.setValue(null);

		formService.saveValues(owner, formDefinitionOne, Lists.newArrayList(value1, value2));

		// check delete unsaved multiple values
		m = formService.getFormInstance(owner, formDefinitionOne).toValueMap();
		assertEquals(1, m.get(multiAttributeName).size());
		assertEquals(FORM_VALUE_ONE, m.get(multiAttributeName).get(0).getValue());

		// checks value map
		Map<String, ? extends List<Serializable>> v = formService.getFormInstance(owner, formDefinitionOne).toPersistentValueMap();
		assertEquals(1, v.get(multiAttributeName).size());
		assertEquals(FORM_VALUE_ONE, v.get(multiAttributeName).get(0));
	}

	@Test
	public void testUpdateConfidentialProperty() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		//
		// create definition with confidential parameter
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attribute.getCode());
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		attribute.setConfidential(true);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentity.class.getCanonicalName(), getHelper().createName(), Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		// fill values
		IdmFormValueDto value1 = new IdmFormValueDto(attribute);
		value1.setValue(FORM_VALUE_ONE);
		formService.saveValues(owner, formDefinitionOne, Lists.newArrayList(value1));
		Map<String, List<IdmFormValueDto>> m = formService.getFormInstance(owner, formDefinitionOne).toValueMap();
		//
		// check
		Assert.assertEquals(1, m.get(attributeName).size());
		Assert.assertEquals(GuardedString.SECRED_PROXY_STRING, (m.get(attributeName).get(0)).getValue());
		Assert.assertTrue(m.get(attributeName).get(0).isConfidential());
		//
		// check confidential value
		Assert.assertEquals(FORM_VALUE_ONE, formService.getConfidentialPersistentValue(m.get(attributeName).get(0)));
		//
		// save other values - confidential will not be included
		formService.saveValues(owner, formDefinitionOne, Lists.newArrayList());
		Assert.assertEquals(FORM_VALUE_ONE, formService.getConfidentialPersistentValue(m.get(attributeName).get(0)));
		//
		// update
		IdmFormValueDto confidentialValue = m.get(attributeName).get(0);
		confidentialValue.setValue("");
		formService.saveValues(owner, formDefinitionOne, Lists.newArrayList(confidentialValue));
		Assert.assertEquals("", formService.getConfidentialPersistentValue(m.get(attributeName).get(0)));
		//
		// update 2
		confidentialValue = m.get(attributeName).get(0);
		confidentialValue.setValue(FORM_VALUE_ONE);
		formService.saveValues(owner, formDefinitionOne, Lists.newArrayList(confidentialValue));
		Assert.assertEquals(FORM_VALUE_ONE, formService.getConfidentialPersistentValue(m.get(attributeName).get(0)));
		//
		confidentialValue.setValue(null);
		formService.saveValues(owner, formDefinitionOne, Lists.newArrayList(confidentialValue));
		Assert.assertNull(formService.getConfidentialPersistentValue(m.get(attributeName).get(0)));
	}

	@Test
	public void testSaveMultipleConfidentialProperties() {
		Identifiable owner = getHelper().createIdentity((GuardedString) null);
		//
		// create definition with multi confidential parameter
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attribute.getCode());
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		attribute.setConfidential(true);
		attribute.setMultiple(true);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentity.class.getCanonicalName(), getHelper().createName(), Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		// save three values
		IdmFormValueDto valueOne = new IdmFormValueDto(attribute);
		valueOne.setValue(FORM_VALUE_ONE);
		IdmFormValueDto valueTwo = new IdmFormValueDto(attribute);
		valueTwo.setValue(FORM_VALUE_TWO);
		IdmFormValueDto valueThree = new IdmFormValueDto(attribute);
		valueThree.setValue(FORM_VALUE_THREE);
		formService.saveValues(owner, formDefinitionOne.getId(), Lists.newArrayList(valueOne, valueTwo, valueThree));
		List<IdmFormValueDto> values = formService.getValues(owner, attribute);
		//
		Assert.assertEquals(3, values.size());
		valueOne = values.get(0);
		valueTwo = values.get(1);
		valueThree = values.get(2);
		Assert.assertEquals(FORM_VALUE_ONE, formService.getConfidentialPersistentValue(valueOne));
		Assert.assertEquals(FORM_VALUE_TWO, formService.getConfidentialPersistentValue(valueTwo));
		Assert.assertEquals(FORM_VALUE_THREE, formService.getConfidentialPersistentValue(valueThree));
		//
		// remove value two - we need to save remaining (one, three)
		valueOne.setValue(FORM_VALUE_ONE);
		valueThree.setValue(FORM_VALUE_THREE);
		formService.saveValues(owner, formDefinitionOne.getId(), Lists.newArrayList(valueOne, valueThree));
		//
		values = formService.getValues(owner, attribute);
		Assert.assertEquals(2, values.size());
		Assert.assertEquals(FORM_VALUE_ONE, formService.getConfidentialPersistentValue(valueOne));
		Assert.assertEquals(FORM_VALUE_THREE, formService.getConfidentialPersistentValue(valueThree));
		Assert.assertEquals(valueOne.getId(), values.get(0).getId());
		Assert.assertEquals(valueThree.getId(), values.get(1).getId());
		Assert.assertNull(formService.getConfidentialPersistentValue(valueTwo));
	}

	@Test
	public void testFindValues() {
		IdmIdentityDto ownerIdentity = getHelper().createIdentity((GuardedString) null);
		IdmRoleDto ownerRole = getHelper().createRole();
		IdmTreeNodeDto ownerTreeNode = getHelper().createTreeNode();
		IdmIdentityContractDto ownerIdentityContract = getHelper().createContract(ownerIdentity);
		//
		Assert.assertEquals(1, prepareDataAndFind(IdmIdentity.class, ownerIdentity));
		Assert.assertEquals(1, prepareDataAndFind(IdmRole.class, ownerRole));
		Assert.assertEquals(1, prepareDataAndFind(IdmTreeNode.class, ownerTreeNode));
		Assert.assertEquals(1, prepareDataAndFind(IdmIdentityContract.class, ownerIdentityContract));
	}
	
	@Test
	public void testFindFormInstances() {
		IdmFormAttributeDto attributeOne = new IdmFormAttributeDto(getHelper().createName(), getHelper().createName(), PersistentType.SHORTTEXT);
		IdmFormAttributeDto attributeTwo = new IdmFormAttributeDto(getHelper().createName(), getHelper().createName(), PersistentType.SHORTTEXT);
		IdmFormDefinitionDto formDefinition = formService.createDefinition(
				IdmIdentity.class,
				getHelper().createName(),
				Lists.newArrayList(attributeOne, attributeTwo));
		attributeOne = formDefinition.getMappedAttributeByCode(attributeOne.getCode());
		attributeTwo = formDefinition.getMappedAttributeByCode(attributeTwo.getCode());		
		// create owner
		IdmIdentityDto owner = getHelper().createIdentity((GuardedString) null);
		// fill values
		IdmFormValueDto valueOne = new IdmFormValueDto(attributeOne);
		valueOne.setValue(FORM_VALUE_ONE);
		IdmFormValueDto valueTwo = new IdmFormValueDto(attributeTwo);
		valueTwo.setValue(FORM_VALUE_TWO);
		owner.getEavs().add(new IdmFormInstanceDto(owner, formDefinition, Lists.newArrayList(valueOne, valueTwo)));
		identityService.save(owner);
		//
		// find saved values without filter
		Map<String, List<IdmFormValueDto>> m = formService.findFormInstance(owner, formDefinition, null).toValueMap();
		Assert.assertEquals(FORM_VALUE_ONE, (m.get(attributeOne.getCode()).get(0)).getValue());
		Assert.assertEquals(FORM_VALUE_TWO, (m.get(attributeTwo.getCode()).get(0)).getValue());
		//
		m = formService
				.findFormInstances(owner, null)
				.stream()
				.filter(fi -> fi.getFormDefinition().equals(formDefinition))
				.findFirst()
				.get()
				.toValueMap();
		Assert.assertEquals(FORM_VALUE_ONE, (m.get(attributeOne.getCode()).get(0)).getValue());
		Assert.assertEquals(FORM_VALUE_TWO, (m.get(attributeTwo.getCode()).get(0)).getValue());
		
		//
		// find saved values with filter
		DefaultFormableFilter filter = new DefaultFormableFilter();
		FormDefinitionAttributes attributes = new FormDefinitionAttributes();
		attributes.setDefinition(formDefinition.getId());
		attributes.getAttributes().add(attributeOne.getId());
		filter.setFormDefinitionAttributes(Lists.newArrayList(attributes));
		List<IdmFormValueDto> values = formService.findFormInstances(owner, filter).get(0).getValues();
		Assert.assertEquals(1, values.size());
		Assert.assertEquals(FORM_VALUE_ONE, values.get(0).getValue());
	}

	@Test
	public void testSaveEavWithIdentityTogether() {
		// create owner
		IdmIdentityDto owner = new IdmIdentityDto();
		//
		// create definition with parameter
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		// fill values
		IdmFormValueDto value = new IdmFormValueDto(attribute);
		value.setValue(FORM_VALUE_ONE);
		// + change owner
		owner.setUsername(getHelper().createName());
		owner.setFirstName(FORM_VALUE_ONE);
		owner.getEavs().add(new IdmFormInstanceDto(owner, formDefinitionOne, Lists.newArrayList(value)));
		owner = identityService.save(owner);
		//
		// load saved
		Map<String, List<IdmFormValueDto>> m = formService.getFormInstance(owner, formDefinitionOne).toValueMap();
		Assert.assertEquals(1, m.get(attributeName).size());
		Assert.assertEquals(FORM_VALUE_ONE, (m.get(attributeName).get(0)).getValue());
		//
		// load owner
		Assert.assertEquals(FORM_VALUE_ONE, identityService.get(owner).getFirstName());
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testSaveEavWithOwner() {
		List<FormableDto> owners = new ArrayList<>();
		owners.add(getHelper().createIdentity((GuardedString) null)); // implemented authorization policies for eav attributes
		owners.add(getHelper().createRole()); // without authorization policies for eav attributes
		//
		owners.forEach(owner -> {
			//
			// create definition with parameter
			IdmFormAttributeDto attribute = new IdmFormAttributeDto();
			String attributeName = getHelper().createName();
			attribute.setCode(attributeName);
			attribute.setName(attributeName);
			attribute.setPersistentType(PersistentType.SHORTTEXT);
			IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
					owner.getClass().getCanonicalName(),
					getHelper().createName(),
					Lists.newArrayList(attribute));
			attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
			//
			// fill values
			IdmFormValueDto value = new IdmFormValueDto(attribute);
			value.setValue(FORM_VALUE_ONE);
			owner.getEavs().add(new IdmFormInstanceDto(owner, formDefinitionOne, Lists.newArrayList(value)));
			owner = (FormableDto) ((AbstractFormableService) lookupService.getDtoService(owner.getClass())).save(owner);
			// check form instance values were saved
			owner
				.getEavs()
				.forEach(formInstance -> {
					Assert.assertFalse(formInstance.getValues().isEmpty());
					formInstance.getValues().forEach(formValue -> {
						Assert.assertNotNull(formValue.getId());
						Assert.assertNotNull(formValue.getCreated());
					});
				});
			//
			// load saved
			Map<String, List<IdmFormValueDto>> m = formService.getFormInstance(owner, formDefinitionOne).toValueMap();
			Assert.assertEquals(1, m.get(attributeName).size());
			Assert.assertEquals(FORM_VALUE_ONE, (m.get(attributeName).get(0)).getValue());
		});
	}

	@Test
	public void testSaveEavByOwnerSecured() {
		// create owner
		IdmIdentityDto owner = getHelper().createIdentity();
		//
		// create definition with parameter
		IdmFormAttributeDto attributeNotSecured = new IdmFormAttributeDto();
		String attributeNotSecuredName = getHelper().createName();
		attributeNotSecured.setCode(attributeNotSecuredName);
		attributeNotSecured.setName(attributeNotSecuredName);
		attributeNotSecured.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormAttributeDto attributeSecured = new IdmFormAttributeDto();
		String attributeSecuredName = getHelper().createName();
		attributeSecured.setCode(attributeSecuredName);
		attributeSecured.setName(attributeSecuredName);
		attributeSecured.setPersistentType(PersistentType.SHORTTEXT);
		//
		IdmFormDefinitionDto formDefinition = formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attributeNotSecured, attributeSecured));
		attributeNotSecured = formDefinition.getMappedAttributeByCode(attributeNotSecured.getCode());
		attributeSecured = formDefinition.getMappedAttributeByCode(attributeSecured.getCode());
		//
		// fill values
		IdmFormValueDto valueNotSecured = new IdmFormValueDto(attributeNotSecured);
		valueNotSecured.setValue(FORM_VALUE_ONE);
		IdmFormValueDto valueSecured = new IdmFormValueDto(attributeSecured);
		valueSecured.setValue(FORM_VALUE_TWO);
		owner.getEavs().add(new IdmFormInstanceDto(owner, formDefinition, Lists.newArrayList(valueNotSecured, valueSecured)));
		identityService.save(owner);
		//
		// load saved
		Map<String, List<IdmFormValueDto>> m = formService.getFormInstance(owner, formDefinition).toValueMap();
		Assert.assertEquals(FORM_VALUE_ONE, (m.get(attributeNotSecuredName).get(0)).getValue());
		Assert.assertEquals(FORM_VALUE_TWO, (m.get(attributeSecuredName).get(0)).getValue());
		//
		// set authorization policies for owner - login + change
		IdmRoleDto role = getHelper().createRole();
		getHelper().createIdentityRole(owner, role);
		ConfigurationMap properties = new ConfigurationMap();
		properties.put(AbstractFormValueEvaluator.PARAMETER_FORM_DEFINITION, formDefinition.getId());
		properties.put(AbstractFormValueEvaluator.PARAMETER_FORM_ATTRIBUTES, attributeNotSecuredName);
		getHelper().createAuthorizationPolicy(
				role.getId(),
				CoreGroupPermission.FORMVALUE,
				IdmIdentityFormValue.class,
				IdentityFormValueEvaluator.class,
				properties,
				IdmBasePermission.READ, IdmBasePermission.UPDATE);
		getHelper().createBasePolicy(role.getId(), CoreGroupPermission.IDENTITY, IdmIdentity.class, IdmBasePermission.READ, IdmBasePermission.UPDATE);
		getHelper().createBasePolicy(role.getId(), CoreGroupPermission.FORMDEFINITION, IdmFormDefinition.class, IdmBasePermission.READ);
		getHelper().createBasePolicy(role.getId(), CoreGroupPermission.FORMATTRIBUTE, IdmFormAttribute.class, IdmBasePermission.READ);
		//
		String updatedValue = "updated";
		try {
			getHelper().login(owner);

			IdmIdentityDto updateOwner = identityService.get(owner, IdmBasePermission.READ);

			IdmFormValueDto changeValue = new IdmFormValueDto(attributeNotSecured);
			changeValue.setValue(updatedValue);
			updateOwner.getEavs().add(new IdmFormInstanceDto(updateOwner, formDefinition, Lists.newArrayList(changeValue)));
			identityService.save(updateOwner, IdmBasePermission.UPDATE);
			//
			// check
			m =  formService.getFormInstance(owner, formDefinition, IdmBasePermission.READ).toValueMap();
			Assert.assertEquals(updatedValue, (m.get(attributeNotSecuredName).get(0)).getValue());
			Assert.assertNull(FORM_VALUE_TWO, m.get(attributeSecuredName)); // not readable
		} finally {
			logout();
		}
		//
		// check secured attribute is untouched as admin
		formDefinition = formService.getDefinition(formDefinition.getId());
		m = formService.getFormInstance(owner, formDefinition).toValueMap();
		Assert.assertEquals(updatedValue, (m.get(attributeNotSecuredName).get(0)).getValue());
		Assert.assertEquals(FORM_VALUE_TWO, (m.get(attributeSecuredName).get(0)).getValue());
	}

	@Test
	public void testSaveAttachment() {
		IdmIdentityDto owner = getHelper().createIdentity((GuardedString) null);
		// create attachment ... upload attachment is solved by @IdmAttachmentControllerRestTest
		IdmAttachmentDto tempAttachment = createTempAttachment();
		//
		// create definition with attachment parameter
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.ATTACHMENT);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		// save new form value
		IdmFormValueDto value = new IdmFormValueDto(attribute);
		value.setShortTextValue(tempAttachment.getName()); // filename
		value.setValue(tempAttachment.getId());
		//
		List<IdmFormValueDto> saveValues = formService.saveValues(owner, formDefinitionOne.getId(), Lists.newArrayList(value));
		//
		Assert.assertEquals(1, saveValues.size());
		Assert.assertEquals(tempAttachment.getName(), saveValues.get(0).getShortTextValue()); // filename
		Assert.assertEquals(tempAttachment.getId(), saveValues.get(0).getUuidValue());
		//
		IdmAttachmentDto attachment = attachmentManager.get(saveValues.get(0).getUuidValue());
		Assert.assertEquals(saveValues.get(0).getId(), attachment.getOwnerId());
		Assert.assertNotEquals(AttachmentManager.TEMPORARY_ATTACHMENT_OWNER_TYPE, attachment.getOwnerType());
		//
		// update form value
		tempAttachment = createTempAttachment();
		value = new IdmFormValueDto(attribute);
		value.setShortTextValue(tempAttachment.getName()); // filename
		value.setValue(tempAttachment.getId());
		//
		saveValues = formService.saveValues(owner, formDefinitionOne.getId(), Lists.newArrayList(value));
		//
		Assert.assertEquals(1, saveValues.size());
		Assert.assertEquals(tempAttachment.getName(), saveValues.get(0).getShortTextValue()); // filename
		Assert.assertEquals(tempAttachment.getId(), saveValues.get(0).getUuidValue());
		//
		attachment = attachmentManager.get(attachment.getId());
		IdmAttachmentDto newAttachmentVersion = attachmentManager.get(saveValues.get(0).getUuidValue());
		Assert.assertEquals(saveValues.get(0).getId(), newAttachmentVersion.getOwnerId());
		Assert.assertNotEquals(AttachmentManager.TEMPORARY_ATTACHMENT_OWNER_TYPE, attachment.getOwnerType());
		Assert.assertEquals(attachment.getId(), newAttachmentVersion.getParent());
		Assert.assertEquals(newAttachmentVersion.getId(), attachment.getNextVersion());
		//
		// delete form value
		value = new IdmFormValueDto(attribute);
		saveValues = formService.saveValues(owner, formDefinitionOne.getId(), Lists.newArrayList(value));
		//
		Assert.assertTrue(saveValues.isEmpty());
		Assert.assertNull(attachmentManager.get(newAttachmentVersion.getId()));
		Assert.assertNull(attachmentManager.get(attachment.getId()));
	}

	@Test
	public void testRequiredValidation() {
		// prepare form definition a test saving form values
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		attribute.setRequired(true);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		IdmFormValueDto valueOne = new IdmFormValueDto(attribute);
		IdmFormValueDto valueTwo = new IdmFormValueDto(attribute);
		valueTwo.setShortTextValue("value");
		//
		IdmFormInstanceDto formInstance = new IdmFormInstanceDto();
		formInstance.setFormDefinition(formDefinitionOne);
		formInstance.setValues(Lists.newArrayList(valueOne));
		//
		List<InvalidFormAttributeDto> validationErrors = formService.validate(formInstance);
		//
		Assert.assertEquals(1, validationErrors.size());
		Assert.assertTrue(validationErrors.stream().anyMatch(error -> error.isMissingValue()));
		Assert.assertTrue(validationErrors.stream().anyMatch(error -> error.getAttributeCode().equals(attributeName)));
		//
		valueOne.setShortTextValue("value");
		validationErrors = formService.validate(formInstance);
		//
		Assert.assertTrue(validationErrors.isEmpty());

		formService.validate(formInstance);
		//
		Assert.assertTrue(validationErrors.isEmpty());
		//
		formInstance.setValues(Lists.newArrayList());
		//
		validationErrors = formService.validate(formInstance);
		//
		Assert.assertEquals(1, validationErrors.size());
		Assert.assertTrue(validationErrors.stream().anyMatch(error -> error.isMissingValue()));
		Assert.assertTrue(validationErrors.stream().anyMatch(error -> error.getAttributeCode().equals(attributeName)));
		//
		formInstance.setValues(Lists.newArrayList(new IdmFormValueDto(attribute)));
		//
		validationErrors = formService.validate(formInstance);
		//
		Assert.assertEquals(1, validationErrors.size());
		Assert.assertTrue(validationErrors.stream().anyMatch(error -> error.isMissingValue()));
		Assert.assertTrue(validationErrors.stream().anyMatch(error -> error.getAttributeCode().equals(attributeName)));
	}

	@Test
	public void testMinMaxValidation() {
		// prepare form definition a test saving form values
		IdmFormAttributeDto attributeDouble = new IdmFormAttributeDto();
		String attributeDoubleName = getHelper().createName();
		attributeDouble.setCode(attributeDoubleName);
		attributeDouble.setName(attributeDoubleName);
		attributeDouble.setPersistentType(PersistentType.DOUBLE);
		attributeDouble.setMin(new BigDecimal("0.5"));
		attributeDouble.setMax(new BigDecimal("0.7"));
		IdmFormAttributeDto attributeLong = new IdmFormAttributeDto();
		String attributeLongName = getHelper().createName();
		attributeLong.setCode(attributeLongName);
		attributeLong.setName(attributeLongName);
		attributeLong.setPersistentType(PersistentType.LONG);
		attributeLong.setMin(new BigDecimal("10"));
		attributeLong.setMax(new BigDecimal("20"));
		IdmFormAttributeDto attributeInt = new IdmFormAttributeDto();
		String attributeIntName = getHelper().createName();
		attributeInt.setCode(attributeIntName);
		attributeInt.setName(attributeIntName);
		attributeInt.setPersistentType(PersistentType.INT);
		attributeInt.setMin(new BigDecimal("10.1"));
		attributeInt.setMax(new BigDecimal("20"));
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attributeDouble, attributeInt, attributeLong));
		attributeDouble = formDefinitionOne.getMappedAttributeByCode(attributeDouble.getCode());
		attributeInt = formDefinitionOne.getMappedAttributeByCode(attributeInt.getCode());
		attributeLong = formDefinitionOne.getMappedAttributeByCode(attributeLong.getCode());
		//
		IdmFormValueDto valueDouble = new IdmFormValueDto(attributeDouble);
		IdmFormValueDto valueInt = new IdmFormValueDto(attributeInt);
		IdmFormValueDto valueLong = new IdmFormValueDto(attributeLong);
		//
		IdmFormInstanceDto formInstance = new IdmFormInstanceDto();
		formInstance.setFormDefinition(formDefinitionOne);
		formInstance.setValues(Lists.newArrayList(valueDouble, valueInt, valueLong));
		//
		List<InvalidFormAttributeDto> validationErrors = formService.validate(formInstance);
		//
		Assert.assertTrue(validationErrors.isEmpty());
		//
		valueDouble.setDoubleValue(new BigDecimal("0.4"));
		valueInt.setLongValue(4L);
		valueLong.setLongValue(4L);
		//
		validationErrors = formService.validate(formInstance);
		Assert.assertEquals(3, validationErrors.size());
		Assert.assertTrue(validationErrors.stream().allMatch(e -> e.getMinValue() != null));
		Assert.assertTrue(validationErrors.stream().anyMatch(e -> e.getMinValue().equals(new BigDecimal("0.5"))
				&& e.getAttributeCode().equals(attributeDoubleName)));
		Assert.assertTrue(validationErrors.stream().anyMatch(e -> e.getMinValue().equals(new BigDecimal("10.1"))
				&& e.getAttributeCode().equals(attributeIntName)));
		Assert.assertTrue(validationErrors.stream().anyMatch(e -> e.getMinValue().equals(new BigDecimal("10"))
				&& e.getAttributeCode().equals(attributeLongName)));
		//
		valueDouble.setDoubleValue(new BigDecimal("0.8"));
		valueInt.setLongValue(40L);
		valueLong.setLongValue(40L);
		//
		validationErrors = formService.validate(formInstance);
		Assert.assertEquals(3, validationErrors.size());
		Assert.assertTrue(validationErrors.stream().allMatch(e -> e.getMaxValue() != null));
		Assert.assertTrue(validationErrors.stream().anyMatch(e -> e.getMaxValue().equals(new BigDecimal("0.7"))
				&& e.getAttributeCode().equals(attributeDoubleName)));
		Assert.assertTrue(validationErrors.stream().anyMatch(e -> e.getMaxValue().equals(new BigDecimal("20"))
				&& e.getAttributeCode().equals(attributeIntName)));
		Assert.assertTrue(validationErrors.stream().anyMatch(e -> e.getMaxValue().equals(new BigDecimal("20"))
				&& e.getAttributeCode().equals(attributeLongName)));
		//
		valueDouble.setDoubleValue(new BigDecimal("0.5"));
		valueInt.setLongValue(11L);
		valueLong.setLongValue(10L);
		//
		validationErrors = formService.validate(formInstance);
		Assert.assertTrue(validationErrors.isEmpty());
		//
		valueDouble.setDoubleValue(new BigDecimal("0.7"));
		valueInt.setLongValue(20L);
		valueLong.setLongValue(20L);
		//
		validationErrors = formService.validate(formInstance);
		Assert.assertTrue(validationErrors.isEmpty());
		//
		valueDouble.setDoubleValue(new BigDecimal("0.6"));
		valueInt.setLongValue(15L);
		valueLong.setLongValue(15L);
		//
		validationErrors = formService.validate(formInstance);
		Assert.assertTrue(validationErrors.isEmpty());
	}
	
	@Test
	public void testMinMaxStringValidation() {
		// prepare form definition a test saving form values
		IdmFormAttributeDto attributeShortText = new IdmFormAttributeDto();
		String attributeShortTextCode = getHelper().createName();
		attributeShortText.setCode(attributeShortTextCode);
		attributeShortText.setName(attributeShortTextCode);
		attributeShortText.setPersistentType(PersistentType.SHORTTEXT);
		attributeShortText.setMin(new BigDecimal("3"));
		attributeShortText.setMax(new BigDecimal("5"));
		IdmFormAttributeDto attributeText = new IdmFormAttributeDto();
		String attributeTextCode = getHelper().createName();
		attributeText.setCode(attributeTextCode);
		attributeText.setName(attributeTextCode);
		attributeText.setPersistentType(PersistentType.TEXT);
		attributeText.setMin(new BigDecimal("6"));
		attributeText.setMax(new BigDecimal("8"));
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attributeShortText, attributeText));
		attributeShortText = formDefinitionOne.getMappedAttributeByCode(attributeShortText.getCode());
		attributeText = formDefinitionOne.getMappedAttributeByCode(attributeText.getCode());
		//
		IdmFormValueDto valueShortText = new IdmFormValueDto(attributeShortText);
		IdmFormValueDto valueText = new IdmFormValueDto(attributeText);
		//
		IdmFormInstanceDto formInstance = new IdmFormInstanceDto();
		formInstance.setFormDefinition(formDefinitionOne);
		formInstance.setValues(Lists.newArrayList(valueShortText, valueText));
		//
		List<InvalidFormAttributeDto> validationErrors = formService.validate(formInstance);
		//
		Assert.assertTrue(validationErrors.isEmpty());
		//
		valueShortText.setShortTextValue("xx");
		valueText.setStringValue("xxxxx");
		//
		validationErrors = formService.validate(formInstance);
		Assert.assertEquals(2, validationErrors.size());
		Assert.assertTrue(validationErrors.stream().allMatch(e -> e.getMinValue() != null));
		Assert.assertTrue(validationErrors.stream().anyMatch(e -> e.getMinValue().equals(new BigDecimal("3"))
				&& e.getAttributeCode().equals(attributeShortTextCode)));
		Assert.assertTrue(validationErrors.stream().anyMatch(e -> e.getMinValue().equals(new BigDecimal("6"))
				&& e.getAttributeCode().equals(attributeTextCode)));
		//
		valueShortText.setShortTextValue("xxxxxx");
		valueText.setStringValue("xxxxxxxxx");
		//
		validationErrors = formService.validate(formInstance);
		Assert.assertEquals(2, validationErrors.size());
		Assert.assertTrue(validationErrors.stream().allMatch(e -> e.getMaxValue() != null));
		Assert.assertTrue(validationErrors.stream().anyMatch(e -> e.getMaxValue().equals(new BigDecimal("5"))
				&& e.getAttributeCode().equals(attributeShortTextCode)));
		Assert.assertTrue(validationErrors.stream().anyMatch(e -> e.getMaxValue().equals(new BigDecimal("8"))
				&& e.getAttributeCode().equals(attributeTextCode)));
		//
		valueShortText.setShortTextValue("xxxxx");
		valueText.setStringValue("xxxxxxxx");
		//
		validationErrors = formService.validate(formInstance);
		Assert.assertTrue(validationErrors.isEmpty());
		//
		valueShortText.setShortTextValue("xxx");
		valueText.setStringValue("xxxxxx");
		//
		validationErrors = formService.validate(formInstance);
		Assert.assertTrue(validationErrors.isEmpty());
	}
	
	@Test
	public void testMinMaxDateValidation() {
		ZonedDateTime now = ZonedDateTime.now();
		
		// prepare form definition a test saving form values
		IdmFormAttributeDto attributeDate = new IdmFormAttributeDto();
		String attributeDateCode = getHelper().createName();
		attributeDate.setCode(attributeDateCode);
		attributeDate.setName(attributeDateCode);
		attributeDate.setPersistentType(PersistentType.DATE);
		attributeDate.setMin(new BigDecimal("3"));
		attributeDate.setMax(new BigDecimal("5"));
		IdmFormAttributeDto attributeDateTime = new IdmFormAttributeDto();
		String attributeDateTimeCode = getHelper().createName();
		attributeDateTime.setCode(attributeDateTimeCode);
		attributeDateTime.setName(attributeDateTimeCode);
		attributeDateTime.setPersistentType(PersistentType.DATETIME);
		attributeDateTime.setMin(new BigDecimal("6"));
		attributeDateTime.setMax(new BigDecimal("8"));
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attributeDate, attributeDateTime));
		attributeDate = formDefinitionOne.getMappedAttributeByCode(attributeDate.getCode());
		attributeDateTime = formDefinitionOne.getMappedAttributeByCode(attributeDateTime.getCode());
		//
		IdmFormValueDto valueDate = new IdmFormValueDto(attributeDate);
		IdmFormValueDto valueDateTime = new IdmFormValueDto(attributeDateTime);
		//
		IdmFormInstanceDto formInstance = new IdmFormInstanceDto();
		formInstance.setFormDefinition(formDefinitionOne);
		formInstance.setValues(Lists.newArrayList(valueDate, valueDateTime));
		//
		List<InvalidFormAttributeDto> validationErrors = formService.validate(formInstance);
		//
		Assert.assertTrue(validationErrors.isEmpty());
		//
		valueDate.setDateValue(now.plusDays(2));
		valueDateTime.setDateValue(now.plusDays(5));
		//
		validationErrors = formService.validate(formInstance);
		Assert.assertEquals(2, validationErrors.size());
		Assert.assertTrue(validationErrors.stream().allMatch(e -> e.getMinValue() != null));
		Assert.assertTrue(validationErrors.stream().anyMatch(e -> e.getMinValue().equals(new BigDecimal("3"))
				&& e.getAttributeCode().equals(attributeDateCode)));
		Assert.assertTrue(validationErrors.stream().anyMatch(e -> e.getMinValue().equals(new BigDecimal("6"))
				&& e.getAttributeCode().equals(attributeDateTimeCode)));
		//
		valueDate.setDateValue(now.plusDays(6));
		valueDateTime.setDateValue(now.plusDays(9));
		//
		validationErrors = formService.validate(formInstance);
		Assert.assertEquals(2, validationErrors.size());
		Assert.assertTrue(validationErrors.stream().allMatch(e -> e.getMaxValue() != null));
		Assert.assertTrue(validationErrors.stream().anyMatch(e -> e.getMaxValue().equals(new BigDecimal("5"))
				&& e.getAttributeCode().equals(attributeDateCode)));
		Assert.assertTrue(validationErrors.stream().anyMatch(e -> e.getMaxValue().equals(new BigDecimal("8"))
				&& e.getAttributeCode().equals(attributeDateTimeCode)));
		//
		valueDate.setDateValue(now.plusDays(5));
		valueDateTime.setDateValue(now.plusDays(8));
		//
		validationErrors = formService.validate(formInstance);
		Assert.assertTrue(validationErrors.isEmpty());
		//
		valueDate.setDateValue(now.plusDays(3));
		valueDateTime.setDateValue(now.plusDays(6).plusMinutes(2));
		//
		validationErrors = formService.validate(formInstance);
		Assert.assertTrue(validationErrors.isEmpty());
	}

	@Test(expected = ResultCodeException.class)
	public void testMinValidationWrongPersistentType() {
		// prepare form definition a test saving form values
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.ATTACHMENT);
		attribute.setMin(new BigDecimal("0.5"));
		formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
	}

	@Test(expected = ResultCodeException.class)
	public void testMaxValidationWrongPersistentType() {
		// prepare form definition a test saving form values
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.BOOLEAN);
		attribute.setMax(new BigDecimal("0.5"));
		formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
	}

	@Test
	public void testRegexValidation() {
		// prepare form definition a test saving form values
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		// ip address regex (copied from https://www.mkyong.com/regular-expressions/how-to-validate-ip-address-with-regular-expression/)
		String ipRegex = "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		attribute.setRegex(ipRegex);
		String validationMessage = "invalid...";
		attribute.setValidationMessage(validationMessage);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		IdmFormValueDto value = new IdmFormValueDto(attribute);
		value.setValue("one");
		//
		IdmFormInstanceDto formInstance = new IdmFormInstanceDto();
		formInstance.setFormDefinition(formDefinitionOne);
		formInstance.setValues(Lists.newArrayList(value));
		//
		List<InvalidFormAttributeDto> validationErrors = formService.validate(formInstance);
		//
		Assert.assertEquals(1, validationErrors.size());
		Assert.assertTrue(validationErrors.stream().allMatch(e -> e.getRegexValue().equals(ipRegex)));
		//
		value.setValue("125.123.255.111");
		validationErrors = formService.validate(formInstance);
		Assert.assertTrue(validationErrors.isEmpty());
		//
		value.setValue("1.1.1.1");
		validationErrors = formService.validate(formInstance);
		Assert.assertTrue(validationErrors.isEmpty());
		//
		value.setValue("1.1.1.1.");
		validationErrors = formService.validate(formInstance);
		Assert.assertEquals(1, validationErrors.size());
		Assert.assertTrue(validationErrors.stream().allMatch(e -> e.getRegexValue().equals(ipRegex)));
		Assert.assertTrue(validationErrors.stream().allMatch(e -> e.getMessage().equals(validationMessage)));
	}

	@Test(expected = ResultCodeException.class)
	public void testRegexValidationWrongPattern() {
		// prepare form definition a test saving form values
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.TEXT);
		attribute.setRegex("[");
		formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
	}

	@Test
	public void testUniqueValidation() {
		// prepare form definition a test saving form values
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.TEXT);
		attribute.setUnique(Boolean.TRUE);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		IdmIdentityDto owner = getHelper().createIdentity((GuardedString) null);
		IdmFormValueDto value = new IdmFormValueDto(attribute);
		value.setValue("one");
		//
		IdmFormInstanceDto formInstance = new IdmFormInstanceDto(owner, formDefinitionOne, Lists.newArrayList(value));
		//
		List<InvalidFormAttributeDto> validationErrors = formService.validate(formInstance);
		Assert.assertTrue(validationErrors.isEmpty());
		//
		IdmFormInstanceDto savedFormInstance = formService.saveFormInstance(owner, formDefinitionOne, Lists.newArrayList(value));
		IdmFormValueDto savedValue = savedFormInstance.toValueMap().get(attributeName).get(0);
		//
		validationErrors = formService.validate(formInstance);
		Assert.assertEquals(1, validationErrors.size());
		Assert.assertTrue(validationErrors.stream().allMatch(e -> e.getUniqueValue().equals("one")));
		//
		value.setId(savedValue.getId());
		validationErrors = formService.validate(formInstance);
		Assert.assertTrue(validationErrors.isEmpty());
		//
		value.setId(UUID.randomUUID());
		validationErrors = formService.validate(formInstance);
		Assert.assertEquals(1, validationErrors.size());
		Assert.assertTrue(validationErrors.stream().allMatch(e -> e.getUniqueValue().equals("one")));
	}

	@Test(expected = ResultCodeException.class)
	public void testUniqueValidationWrongType() {
		// prepare form definition a test saving form values
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.BYTEARRAY);
		attribute.setUnique(Boolean.TRUE);
		formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
	}

	@Test
	public void testChangePersistentTypeSuccess() {
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		attribute.setPersistentType(PersistentType.DATE);
		attribute = formService.saveAttribute(attribute);
		//
		Assert.assertEquals(PersistentType.DATE, attribute.getPersistentType());
	}

	@Test
	public void testChangeConfidentialSuccess() {
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		attribute.setConfidential(false);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		attribute.setConfidential(true);
		attribute = formService.saveAttribute(attribute);
		//
		Assert.assertTrue(attribute.isConfidential());
	}

	@Test(expected = ChangePersistentTypeException.class)
	public void testChangePersistentTypeFailed() {
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		formService.saveValues(getHelper().createIdentity((GuardedString) null), attribute, Lists.newArrayList("one"));
		//
		attribute.setPersistentType(PersistentType.DATE);
		formService.saveAttribute(attribute);
	}

	@Test(expected = ChangeConfidentialException.class)
	public void testChangeConfidentialFailed() {
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		attribute.setConfidential(false);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				IdmIdentity.class.getCanonicalName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		formService.saveValues(getHelper().createIdentity((GuardedString) null), attribute, Lists.newArrayList("one"));
		//
		attribute.setConfidential(true);
		formService.saveAttribute(attribute);
	}

	@Test(expected = ChangePersistentTypeException.class)
	public void testChangePersistentTypeFailedWhenTypeIsNotClass() {
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				getHelper().createName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		attribute.setPersistentType(PersistentType.DATE);
		formService.saveAttribute(attribute);
	}

	@Test(expected = ChangeConfidentialException.class)
	public void testChangeConfidentialFailedWhenTypeIsNotClass() {
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		attribute.setConfidential(false);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				getHelper().createName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		attribute.setConfidential(true);
		formService.saveAttribute(attribute);
	}

	@Test
	public void testChangeAttributeSuccess() {
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attributeName);
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		attribute.setConfidential(false);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				getHelper().createName(),
				getHelper().createName(),
				Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		attribute.setRequired(true);
		attribute = formService.saveAttribute(attribute);
		//
		Assert.assertTrue(attribute.isRequired());
	}
	
	@Test
	public void testChangeAttributeWithoutSeq() {
		IdmFormAttributeDto attributeOne = new IdmFormAttributeDto(getHelper().createName());
		IdmFormAttributeDto attributeTwo = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attributeTwo.setCode(attributeName);
		attributeTwo.setName(attributeName);
		attributeTwo.setPersistentType(PersistentType.SHORTTEXT);
		attributeTwo.setConfidential(false);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(
				getHelper().createName(),
				getHelper().createName(),
				Lists.newArrayList(attributeOne, attributeTwo));
		attributeTwo = formDefinitionOne.getMappedAttributeByCode(attributeTwo.getCode());
		//
		attributeTwo.setRequired(true);
		attributeTwo = formService.saveAttribute(attributeTwo);
		//
		Assert.assertNotNull(attributeTwo.getSeq());
		Assert.assertTrue(attributeTwo.getSeq().shortValue() > 0);
		Assert.assertTrue(attributeTwo.isRequired());
		//
		// update no zero
		attributeTwo.setSeq(null);
		attributeTwo = formService.saveAttribute(attributeTwo);
		//
		Assert.assertNotNull(attributeTwo.getSeq());
		Assert.assertTrue(attributeTwo.getSeq().shortValue() == 0);
		Assert.assertTrue(attributeTwo.isRequired());
		//
		attributeTwo.setSeq(Short.MAX_VALUE);
		attributeTwo = formService.saveAttribute(attributeTwo);
		//
		Assert.assertNotNull(attributeTwo.getSeq());
		Assert.assertTrue(attributeTwo.getSeq().equals(Short.MAX_VALUE));
		Assert.assertTrue(attributeTwo.isRequired());
		//
		// add new attribute
		IdmFormAttributeDto attributeThree = new IdmFormAttributeDto(getHelper().createName());
		attributeThree.setFormDefinition(formDefinitionOne.getId());
		attributeThree = formService.saveAttribute(attributeThree);
		//
		Assert.assertNotNull(attributeThree.getSeq());
		Assert.assertTrue(attributeThree.getSeq().equals(Short.MAX_VALUE));
	}

	@Test
	public void testPropagateHighEventPriority() {
		try {
			getHelper().setConfigurationValue(EventConfiguration.PROPERTY_EVENT_ASYNCHRONOUS_ENABLED, true);
			Assert.assertTrue(eventConfiguration.isAsynchronous());
			Assert.assertTrue(entityEventManager.isAsynchronous());
			//
			FormableDto owner = getHelper().createIdentity((GuardedString) null);
			getHelper().waitForResult(null, 1, 1);
			//
			// create definition one
			IdmFormAttributeDto attributeDefinitionOne = new IdmFormAttributeDto();
			attributeDefinitionOne.setCode(getHelper().createName());
			attributeDefinitionOne.setName(attributeDefinitionOne.getCode());
			attributeDefinitionOne.setPersistentType(PersistentType.SHORTTEXT);
			IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentity.class.getCanonicalName(), getHelper().createName(), Lists.newArrayList(attributeDefinitionOne));
			attributeDefinitionOne = formDefinitionOne.getMappedAttributeByCode(attributeDefinitionOne.getCode());
			//
			IdmFormValueDto value1 = new IdmFormValueDto(attributeDefinitionOne);
			value1.setValue(FORM_VALUE_ONE);

			IdmFormInstanceDto formInstance = new IdmFormInstanceDto(owner, formDefinitionOne, Lists.newArrayList(value1));
			// prepare event envelope
			CoreEvent<IdmFormInstanceDto> event = new CoreEvent<IdmFormInstanceDto>(CoreEventType.UPDATE, formInstance);
			// FE - high event priority
			event.setPriority(PriorityType.HIGH);
			// publish event for save form instance
			formService.publish(event);
			//
			Assert.assertEquals(FORM_VALUE_ONE, formService.getValues(owner, formDefinitionOne).get(0).getShortTextValue());
			//
			IdmEntityEventFilter filter = new IdmEntityEventFilter();
			filter.setOwnerId(owner.getId());
			filter.setEventType(IdentityEventType.NOTIFY.name());
			//
			List<IdmEntityEventDto> events = entityEventService
				.find(filter, PageRequest.of(0, 1, new Sort(Direction.DESC, IdmEntityEvent_.created.getName())))
				.getContent();
			//
			Assert.assertFalse(events.isEmpty());
			Assert.assertEquals(PriorityType.HIGH, events.get(0).getPriority());
		} finally {
			getHelper().setConfigurationValue(EventConfiguration.PROPERTY_EVENT_ASYNCHRONOUS_ENABLED, false);
			Assert.assertFalse(eventConfiguration.isAsynchronous());
		}
	}

	@Test
	public void testMoveValueIntoOtherOwner() {
		Identifiable ownerOne = getHelper().createIdentity((GuardedString) null);
		Identifiable ownerTwo = getHelper().createIdentity((GuardedString) null);
		//
		// create definition with attribute
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attribute.getCode());
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentity.class.getCanonicalName(), getHelper().createName(), Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		//
		// fill values
		List<IdmFormValueDto> savedValueOne = formService.saveValues(ownerOne, attribute, Lists.newArrayList(FORM_VALUE_ONE));
		List<IdmFormValueDto> savedValueTwo = formService.saveValues(ownerTwo, formDefinitionOne, savedValueOne);
		List<IdmFormValueDto> getValueOne = formService.getValues(ownerOne, attribute);
		// both values should be saved
		Assert.assertEquals(1, getValueOne.size());
		Assert.assertEquals(1, savedValueTwo.size());
		Assert.assertNotEquals(getValueOne.get(0).getId(), savedValueTwo.get(0).getId());
		//
		// prevent to move value for original owner
		List<IdmFormValueDto>  getValueAfterMoveOne = formService.getValues(ownerOne, attribute);
		Assert.assertEquals(1, getValueAfterMoveOne.size());
		Assert.assertEquals(getValueOne.get(0).getId(), getValueAfterMoveOne.get(0).getId());
	}
	
	@Test
	public void testEvictCacheAfterChange() {
		String definitionCode = getHelper().createName();
		String definitionType = formService.getOwnerType(IdmIdentity.class);
		formService.getDefinitions(definitionType);
		Assert.assertNull(((FormDefinitionCache) cacheManager.getValue(FormService.FORM_DEFINITION_CACHE_NAME, formService.getCacheKey(definitionType)).get()).getByCode(definitionCode));
		//
		// create definition with attribute
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attribute.getCode());
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentity.class, definitionCode, Lists.newArrayList(attribute));
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		formService.getDefinitions(definitionType);
		//
		IdmFormDefinitionDto byCode = ((FormDefinitionCache) cacheManager.getValue(FormService.FORM_DEFINITION_CACHE_NAME, formService.getCacheKey(definitionType)).get()).getByCode(definitionCode);
		Assert.assertNotNull(byCode);
		Assert.assertEquals(formDefinitionOne.getId(), byCode.getId());
		Assert.assertEquals(1, byCode.getFormAttributes().size());
		IdmFormDefinitionDto byId = ((FormDefinitionCache) cacheManager.getValue(FormService.FORM_DEFINITION_CACHE_NAME, formService.getCacheKey(formDefinitionOne.getId())).get()).getById(formDefinitionOne.getId());
		Assert.assertNotNull(byId);
		Assert.assertEquals(formDefinitionOne.getId(), byId.getId());
		Assert.assertEquals(1, byId.getFormAttributes().size());
		//
		// change form definition => evict both caches
		formDefinitionOne.setDescription(getHelper().createName());
		formService.saveDefinition(formDefinitionOne);
		Assert.assertNull(cacheManager.getValue(FormService.FORM_DEFINITION_CACHE_NAME, formService.getCacheKey(definitionType)));
		Assert.assertNull(cacheManager.getValue(FormService.FORM_DEFINITION_CACHE_NAME, formService.getCacheKey(formDefinitionOne.getId())));
		//
		formService.getDefinitions(definitionType); // fill cache
		Assert.assertNotNull(((FormDefinitionCache) cacheManager.getValue(FormService.FORM_DEFINITION_CACHE_NAME, formService.getCacheKey(definitionType)).get()).getByCode(definitionCode));
		Assert.assertNotNull(((FormDefinitionCache) cacheManager.getValue(FormService.FORM_DEFINITION_CACHE_NAME, formService.getCacheKey(formDefinitionOne.getId())).get()).getById(formDefinitionOne.getId()));
		//
		// change attribute
		attribute.setDescription(getHelper().createName());
		formAttributeService.save(attribute);
		Assert.assertNull(cacheManager.getValue(FormService.FORM_DEFINITION_CACHE_NAME, formService.getCacheKey(definitionType)));
		Assert.assertNull(cacheManager.getValue(FormService.FORM_DEFINITION_CACHE_NAME, formService.getCacheKey(formDefinitionOne.getId())));
	}
	
	@Test
	public void testPreventToChangeCache() {
		String definitionCode = getHelper().createName();
		String definitionType = formService.getOwnerType(IdmIdentity.class);
		formService.getDefinitions(definitionType);
		Assert.assertNull(((FormDefinitionCache) cacheManager.getValue(FormService.FORM_DEFINITION_CACHE_NAME, formService.getCacheKey(definitionType)).get()).getByCode(definitionCode));
		//
		// create definition with attribute
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attribute.getCode());
		attribute.setReadonly(false);
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		IdmFormDefinitionDto formDefinitionOne = formService.createDefinition(IdmIdentity.class, definitionCode, Lists.newArrayList(attribute));
		//
		// from cache
		formDefinitionOne = formService.getDefinition(formDefinitionOne.getId());
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		Assert.assertFalse(attribute.isReadonly());
		//
		// change attribute instance
		attribute.setReadonly(true);
		//
		formDefinitionOne = formService.getDefinition(formDefinitionOne.getId());
		attribute = formDefinitionOne.getMappedAttributeByCode(attribute.getCode());
		Assert.assertFalse(attribute.isReadonly());		
	}

	private long prepareDataAndFind(Class<? extends AbstractEntity> type, AbstractDto owner) {
		//
		//create attribute
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		String attributeName = getHelper().createName();
		attribute.setCode(attributeName);
		attribute.setName(attribute.getCode());
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		//
		//create definition
		IdmFormDefinitionDto definition = formService.createDefinition(type, getHelper().createName(), Lists.newArrayList(attribute));
		attribute = definition.getMappedAttributeByCode(attribute.getCode());
		//
		//save value
		formService.saveValues(owner.getId(), type, attribute, Lists.newArrayList(FORM_VALUE_ONE));
		//
		//find
		IdmFormValueFilter<?> filter = new IdmFormValueFilter<>();
		filter.setDefinitionId(definition.getId());
		Page<IdmFormValueDto> result = formService.findValues(filter, PageRequest.of(0, Integer.MAX_VALUE));
		return result.getTotalElements();
	}


	private IdmAttachmentDto createTempAttachment() {
		IdmAttachmentDto dto = DefaultAttachmentManagerIntegrationTest.prepareDto();
		//
		return attachmentManager.save(dto);
	}
}
