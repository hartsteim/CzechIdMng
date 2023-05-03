package eu.bcvsolutions.idm.test.api;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.domain.Page;

import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.api.dto.IdmAuthorizationPolicyDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmFormValueFilter;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
import eu.bcvsolutions.idm.core.security.api.domain.GroupPermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationEvaluator;

public abstract class AbstractFormValueEvaluatorIntegrationTest<O extends Identifiable, FV extends AbstractEntity, FVE extends AuthorizationEvaluator<? extends Identifiable>> extends AbstractEvaluatorIntegrationTest {
	@Autowired
	protected FormService service;
	@Autowired
	protected IdmFormDefinitionService formDefinitionService;
	@Autowired
	protected IdmFormAttributeService formAttributeService;
	protected IdmIdentityDto identity;
	protected IdmRoleDto role;
	protected IdmIdentityRoleDto identityRole;
	protected Identifiable owner;
	protected Class<O> typeOfO;
	protected Class<FV> typeOfFV;
	protected Class<FVE> typeOfFVE;

	protected abstract GroupPermission getSpecificGroupPermission();

	protected abstract Identifiable createSpecificOwner();

	@Before
	public void setup() {
		Class<?>[] types = GenericTypeResolver.resolveTypeArguments(getClass(), AbstractFormValueEvaluatorIntegrationTest.class);
		typeOfO = (Class<O>) types[0];
		typeOfFV = (Class<FV>) types[1];
		typeOfFVE = (Class<FVE>) types[2];
	}

	@Test
	public void canReadFormValues() {
		this.identity = getHelper().createIdentity();
		this.role = getHelper().createRole();
		this.identityRole = getHelper().createIdentityRole(identity, role);
		IdmFormDefinitionDto formDefinition = getHelper().createFormDefinition(typeOfO.getName(), false);
		this.owner = createSpecificOwner();
		
		IdmFormAttributeDto formAttribute = getHelper().createEavAttribute("test", formDefinition.getId(), PersistentType.SHORTTEXT);
		getHelper().setEavValue(owner, formAttribute, typeOfO, "testValue", PersistentType.SHORTTEXT);
		//
		// check created identity doesn't have compositions
		try {
			getHelper().login(identity.getUsername(), identity.getPassword());
			IdmFormValueFilter filter = new IdmFormValueFilter();
			filter.setAttributeId(formAttribute.getId());
			Page<IdmFormValueDto> forms = service.findValues(filter, null, IdmBasePermission.READ);
			Assert.assertTrue(forms.isEmpty());
		} finally {
			logout();
		}
		//
		// create authorization policy - assign to role
		ConfigurationMap evaluatorProperties = new ConfigurationMap();
		evaluatorProperties.put("form-definition", formDefinition.getId().toString());
		IdmAuthorizationPolicyDto policy = getHelper().createAuthorizationPolicy(
				role.getId(),
				getSpecificGroupPermission(),
				typeOfFV,
				typeOfFVE,
				evaluatorProperties,
				IdmBasePermission.READ);
		//
		try {
			//
			// evaluate	access
			getHelper().login(identity.getUsername(), identity.getPassword());
			IdmFormValueFilter filter = new IdmFormValueFilter();
			filter.setAttributeId(formAttribute.getId());
			Page<IdmFormValueDto> forms = service.findValues(filter, null, IdmBasePermission.READ);
			Assert.assertEquals(1, forms.getTotalElements());
		} finally {
			logout();
		}

		// cleanup
		try {
			loginAsAdmin();
			IdmFormValueFilter filter = new IdmFormValueFilter();
			filter.setAttributeId(formAttribute.getId());
			Page<IdmFormValueDto> forms = service.findValues(filter, null, IdmBasePermission.READ);
			forms.forEach(form -> service.deleteValue(form, IdmBasePermission.READ));
			formAttributeService.delete(formAttribute);
			formDefinitionService.delete(formDefinition);
		} finally {
			logout();
		}
	}
}
