package eu.bcvsolutions.idm.acc.bulk.action.impl;

import java.text.MessageFormat;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.ImmutableSet;

import eu.bcvsolutions.idm.acc.TestHelper;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemMappingFilter;
import eu.bcvsolutions.idm.acc.entity.SysSystem;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.bulk.action.dto.IdmBulkActionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.test.api.AbstractBulkActionTest;
import static org.junit.Assert.*;

/**
 * Integration tests for {@link SystemDuplicateBulkAction}
 *
 * @author svandav
 *
 */

public class SystemDuplicateBulkActionTest extends AbstractBulkActionTest {

	@Autowired
	private TestHelper helper;
	@Autowired
	private SysSystemService systemService;
	@Autowired
	private SysSystemMappingService mappingService;

	@Before
	public void login() {
		IdmIdentityDto adminIdentity = this.createUserWithAuthorities(IdmBasePermission.UPDATE, IdmBasePermission.READ);
		loginAsNoAdmin(adminIdentity.getUsername());
	}

	@After
	public void logout() {
		super.logout();
	}

	@Test
	public void processBulkActionByIds() {
		SysSystemDto system = helper.createSystem(getHelper().createName());

		IdmBulkActionDto bulkAction = this.findBulkAction(SysSystem.class, SystemDuplicateBulkAction.NAME);
		bulkAction.setIdentifiers(ImmutableSet.of(system.getId()));

		String name = MessageFormat.format("{0}{1}", "Copy-of-", system.getName());
		assertNull(systemService.getByCode(name));

		IdmBulkActionDto processAction = bulkActionManager.processAction(bulkAction);
		checkResultLrt(processAction, 1l, null, null);
		assertNotNull(systemService.getByCode(name));
	}

	@Test
	public void testDuplication() {
		SysSystemDto system = helper.createTestResourceSystem(true, getHelper().createName());
		//
		SysSystemMappingFilter filter = new SysSystemMappingFilter();
		filter.setSystemId(system.getId());
		List<SysSystemMappingDto> mappings = mappingService.find(filter, null).getContent();

		// Contains only one created system mapping
		assertEquals(1, mappings.size());

		// Add connected mapping
		SysSystemMappingDto origMapping = mappings.get(0);
		SysSystemMappingDto connectedMapping = helper.createMapping(system);
		connectedMapping.setOperationType(SystemOperationType.SYNCHRONIZATION);
		mappingService.save(connectedMapping);
		origMapping.setConnectedSystemMappingId(connectedMapping.getId());
		mappingService.save(origMapping);

		IdmBulkActionDto bulkAction = this.findBulkAction(SysSystem.class, SystemDuplicateBulkAction.NAME);
		bulkAction.setIdentifiers(ImmutableSet.of(system.getId()));

		String name = MessageFormat.format("{0}{1}", "Copy-of-", system.getName());
		assertNull(systemService.getByCode(name));

		IdmBulkActionDto processAction = bulkActionManager.processAction(bulkAction);
		checkResultLrt(processAction, 1l, null, null);
		SysSystemDto duplicatedSystem = systemService.getByCode(name);
		assertNotNull(duplicatedSystem);
		//
		filter.setSystemId(duplicatedSystem.getId());
		List<SysSystemMappingDto> mappingsInDuplicatedSystem = mappingService.find(filter, null).getContent();
		// Contains two system mappings
		assertEquals(2, mappingsInDuplicatedSystem.size());
		// The synchronization mapping must be connected with the provisioning mapping
		SysSystemMappingDto synchronizationMapping = mappingsInDuplicatedSystem.stream()
				.filter(m -> m.getOperationType() == SystemOperationType.SYNCHRONIZATION)
				.findFirst()
				.get();
		SysSystemMappingDto provisioningMapping = mappingsInDuplicatedSystem.stream()
				.filter(m -> m.getOperationType() == SystemOperationType.PROVISIONING)
				.findFirst()
				.get();
		assertEquals(synchronizationMapping.getConnectedSystemMappingId(), provisioningMapping.getId());
	}
}
