package eu.bcvsolutions.idm.core.bulk.action.impl.role;

import java.util.UUID;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import eu.bcvsolutions.idm.core.api.bulk.action.dto.IdmBulkActionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueRoleFilter;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueRoleService;
import eu.bcvsolutions.idm.core.model.entity.IdmRole;
import eu.bcvsolutions.idm.test.api.AbstractBulkActionTest;

/**
 * 
 * @author stein
 *
 */
public class RoleCatalogueAddBulkActionTest extends AbstractBulkActionTest {

	@Autowired
	private IdmRoleCatalogueRoleService roleCatalogueRoleService;

	@Before
	public void before() {
		getHelper().loginAdmin();
	}

	@After
	public void after() {
		getHelper().logout();
	}

	@Test
	public void greenLine() {
		IdmRoleDto role = getHelper().createRole();
		IdmRoleCatalogueDto catalogue = getHelper().createRoleCatalogue();
		Assert.assertFalse(roleIsInCatalogue(role.getId(), catalogue.getId()));
		IdmBulkActionDto bulkAction = findBulkAction(IdmRole.class, RoleCatalogueAddBulkAction.NAME);
		bulkAction.setIdentifiers(Sets.newHashSet(role.getId()));
		bulkAction.getProperties().put(RoleCatalogueAddBulkAction.PROPERTY_ROLE_CATALOGUE,
				Lists.newArrayList(catalogue.getId().toString()));
		IdmBulkActionDto processAction = bulkActionManager.processAction(bulkAction);

		checkResultLrt(processAction, 1l, null, null);

		Assert.assertTrue(roleIsInCatalogue(role.getId(), catalogue.getId()));

	}

	// role is already in catalogue
	@Test
	public void redLine() {
		getHelper().loginAdmin();
		IdmRoleDto role = getHelper().createRole();
		IdmRoleCatalogueDto catalogue = getHelper().createRoleCatalogue();
		getHelper().createRoleCatalogueRole(role, catalogue);
		Assert.assertTrue(roleIsInCatalogue(role.getId(), catalogue.getId()));
		IdmBulkActionDto bulkAction = findBulkAction(IdmRole.class, RoleCatalogueAddBulkAction.NAME);
		bulkAction.setIdentifiers(Sets.newHashSet(role.getId()));
		bulkAction.getProperties().put(RoleCatalogueAddBulkAction.PROPERTY_ROLE_CATALOGUE,
				Lists.newArrayList(catalogue.getId().toString()));
		IdmBulkActionDto processAction = bulkActionManager.processAction(bulkAction);
		checkResultLrt(processAction, 1l, null, null);
		Assert.assertTrue(roleIsInCatalogue(role.getId(), catalogue.getId()));
	}

	// more roles
	@Test
	public void yellowLine() {
		getHelper().loginAdmin();
		IdmRoleDto role = getHelper().createRole();
		IdmRoleDto secondRole = getHelper().createRole();
		IdmRoleDto thirdRole = getHelper().createRole();
		IdmRoleCatalogueDto catalogue = getHelper().createRoleCatalogue();
		Assert.assertFalse(roleIsInCatalogue(role.getId(), catalogue.getId()));
		Assert.assertFalse(roleIsInCatalogue(secondRole.getId(), catalogue.getId()));
		Assert.assertFalse(roleIsInCatalogue(thirdRole.getId(), catalogue.getId()));
		IdmBulkActionDto bulkAction = findBulkAction(IdmRole.class, RoleCatalogueAddBulkAction.NAME);
		bulkAction.setIdentifiers(Sets.newHashSet(role.getId(), secondRole.getId(), thirdRole.getId()));
		bulkAction.getProperties().put(RoleCatalogueAddBulkAction.PROPERTY_ROLE_CATALOGUE,
				Lists.newArrayList(catalogue.getId().toString()));
		IdmBulkActionDto processAction = bulkActionManager.processAction(bulkAction);

		checkResultLrt(processAction, 3l, null, null);

		Assert.assertTrue(roleIsInCatalogue(role.getId(), catalogue.getId()));
		Assert.assertTrue(roleIsInCatalogue(secondRole.getId(), catalogue.getId()));
		Assert.assertTrue(roleIsInCatalogue(thirdRole.getId(), catalogue.getId()));
	}

	// more catalogues

	@Test
	public void blueLine() {
		getHelper().loginAdmin();
		IdmRoleDto role = getHelper().createRole();
		IdmRoleCatalogueDto catalogue = getHelper().createRoleCatalogue();
		IdmRoleCatalogueDto catalogueOther = getHelper().createRoleCatalogue();
		IdmRoleCatalogueDto catalogueThird = getHelper().createRoleCatalogue();
		Assert.assertFalse(roleIsInCatalogue(role.getId(), catalogue.getId()));
		Assert.assertFalse(roleIsInCatalogue(role.getId(), catalogueOther.getId()));
		Assert.assertFalse(roleIsInCatalogue(role.getId(), catalogueThird.getId()));
		IdmBulkActionDto bulkAction = findBulkAction(IdmRole.class, RoleCatalogueAddBulkAction.NAME);
		bulkAction.setIdentifiers(Sets.newHashSet(role.getId()));
		bulkAction.getProperties().put(RoleCatalogueAddBulkAction.PROPERTY_ROLE_CATALOGUE, Lists.newArrayList(
				catalogue.getId().toString(), catalogueOther.getId().toString(), (catalogueThird.getId().toString())));
		IdmBulkActionDto processAction = bulkActionManager.processAction(bulkAction);

		Assert.assertTrue(roleIsInCatalogue(role.getId(), catalogue.getId()));
		Assert.assertTrue(roleIsInCatalogue(role.getId(), catalogueOther.getId()));
		Assert.assertTrue(roleIsInCatalogue(role.getId(), catalogueThird.getId()));

		checkResultLrt(processAction, 1l, null, null);
	}

	private boolean roleIsInCatalogue(UUID roleId, UUID catalogueId) {
		IdmRoleCatalogueRoleFilter filter = new IdmRoleCatalogueRoleFilter();
		filter.setRoleId(roleId);
		filter.setRoleCatalogueId(catalogueId);
		return roleCatalogueRoleService.count(filter) > 0;
	}
}
