package eu.bcvsolutions.idm.vs.service.api;

import java.util.List;
import java.util.UUID;

import eu.bcvsolutions.idm.core.api.service.ReadWriteDtoService;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizableService;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.api.IcUidAttribute;
import eu.bcvsolutions.idm.vs.repository.filter.VsRequestFilter;
import eu.bcvsolutions.idm.vs.service.api.dto.VsRequestDto;

/**
 * Service for request in virtual system
 * 
 * @author Svanda
 *
 */
public interface VsRequestService extends 
		ReadWriteDtoService<VsRequestDto, VsRequestFilter>, AuthorizableService<VsRequestDto> {

	IcUidAttribute execute(VsRequestDto request);

	IcUidAttribute internalStart(VsRequestDto request);

	VsRequestDto createRequest(VsRequestDto request);

	IcUidAttribute internalExecute(VsRequestDto request);

	VsRequestDto realize(UUID fromString);

	VsRequestDto cancel(UUID fromString, String reason);

	/**
	 * Find duplicity requests. All request in state IN_PROGRESS for same UID
	 * and system. For all operation types.
	 * 
	 * @param request
	 * @return
	 */
	List<VsRequestDto> findDuplicities(VsRequestDto request);

	IcConnectorObject getConnectorObject(UUID fromString);

}
