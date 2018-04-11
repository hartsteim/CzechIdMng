package eu.bcvsolutions.idm.core.api.service;

import java.util.List;
import java.util.UUID;

import eu.bcvsolutions.idm.core.api.dto.IdmContractSliceDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractSliceFilter;
import eu.bcvsolutions.idm.core.api.script.ScriptEnabled;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizableService;

/**
 * Service for operations with contract time slices
 * 
 * @author svandav
 *
 */
public interface IdmContractSliceService extends
		EventableDtoService<IdmContractSliceDto, IdmContractSliceFilter>,
		AuthorizableService<IdmContractSliceDto>,
		ScriptEnabled {
	

	/**
	 * Returns working positions for given identity
	 * 
	 * @param identityId
	 * @return
	 */
	List<IdmContractSliceDto> findAllByIdentity(UUID identityId);


}
