package eu.bcvsolutions.idm.acc.service.impl;

import eu.bcvsolutions.idm.acc.TestHelper;
import eu.bcvsolutions.idm.acc.domain.AccountType;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.dto.AbstractConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.ApplicantDto;
import eu.bcvsolutions.idm.core.api.dto.ApplicantImplDto;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

/**
 * Test for provisioning merge with other personal account
 *
 * @author Peter Štrunc <github.com/peter-strunc>
 *
 */
public class OtherPersonalAccountProvisioningMergeTest extends ProvisioningMergeTest {

	@Autowired
	private TestHelper helper;

	@Autowired
	private SysSystemMappingService systemMappingService;

	protected SysSystemMappingDto createTestMapping(SysSystemDto system) {
		final SysSystemMappingDto mapping = helper.createMapping(system);
		mapping.setAccountType(AccountType.PERSONAL_OTHER);
		//
		return systemMappingService.save(mapping);
	}

	@Override
	protected SysRoleSystemDto createRoleSystem(SysSystemDto system, IdmRoleDto role, boolean assignsAccount) {
		return helper.createRoleSystem(role, system, AccountType.PERSONAL_OTHER, assignsAccount);
	}
}
