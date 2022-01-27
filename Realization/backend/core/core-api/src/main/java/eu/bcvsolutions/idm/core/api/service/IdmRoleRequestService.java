package eu.bcvsolutions.idm.core.api.service;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.Loggable;
import eu.bcvsolutions.idm.core.api.domain.PriorityType;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestByIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.ResolvedIncompatibleRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizableService;
import eu.bcvsolutions.idm.core.security.api.service.ExceptionProcessable;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for role request.
 * 
 * @author Vít Švanda
 * 
 */
public interface IdmRoleRequestService extends 
		EventableDtoService<IdmRoleRequestDto, IdmRoleRequestFilter>,
		AuthorizableService<IdmRoleRequestDto>,
		ExceptionProcessable<IdmRoleRequestDto> {
	
	String ROLE_REQUEST_ID_KEY = "roleRequestId";

	/**
	 * Start approval process for given request. Request approving will be started
	 * in new transaction.
	 * 
	 * @param requestId
	 * @param checkRight
	 *            - If is true, then will be check right for immediately execution
	 *            (if is requires)
	 */
	IdmRoleRequestDto startRequest(UUID requestId, boolean checkRight);
	
	/**
	 * Start approval process for given request. Request approving will be started
	 * in new transaction.
	 * 
	 * @param event
	 * @return
	 */
	IdmRoleRequestDto startRequest(EntityEvent<IdmRoleRequestDto> event);

	/**
	 * Internal start request. Not accessible from REST.
	 * 
	 * @param requestId
	 * @param checkRight
	 *            - If is true, then will be check right for immediately execution
	 *            (if is requires)
	 */
	IdmRoleRequestDto startRequestInternal(UUID requestId, boolean checkRight);
	
	/**
	 * Internal start request. Not accessible from REST.
	 * 
	 * @see PriorityType#IMMEDIATE
	 * @param requestId
	 * @param checkRight- If is true, then will be check right for immediately execution (if is requires)
	 * @param immediate - will be executed synchronously
	 * @deprecated @since 11.1.0 use {@link IdmRoleRequestService#startRequestInternal(EntityEvent)} with {@link RoleRequestApprovalProcessor.CHECK_RIGHT_PROPERTY}. Synchronous role request are prohibited.
	 */
	@Deprecated
	IdmRoleRequestDto startRequestInternal(UUID requestId, boolean checkRight, boolean immediate);

	@Transactional
	IdmRoleRequestDto startRequestInternal(EntityEvent<IdmRoleRequestDto> requestEvent, EntityEvent<?> parentEvent);

	/**
	 * Internal start request. 
	 * 
	 * @param event
	 * @return
	 */
	IdmRoleRequestDto startRequestInternal(EntityEvent<IdmRoleRequestDto> event);

	/**
	 * Add record to request log
	 * 
	 * @param logItem
	 * @param text
	 */
	void addToLog(Loggable logItem, String text);

	/**
	 * Realization of request (applying the requested changes).
	 * Lookout: method realize request synchronously. Use {@link IdmRoleRequestService#startRequest(EntityEvent)) instead.
	 * 
	 * @param requestId
	 * @return
	 * @see IdmRoleRequestService#startRequest(EntityEvent)
	 */
	IdmRoleRequestDto executeRequest(UUID requestId);
	
	/**
	 * Realization of request (applying the requested changes).
	 * Lookout: method realize request synchronously. Use {@link IdmRoleRequestService#startRequest(EntityEvent)) instead.
	 * 
	 * @param requestEvent
	 * @return
	 */
	IdmRoleRequestDto executeRequest(EntityEvent<IdmRoleRequestDto> requestEvent);
	
	/**
	 * Execute concepts via request - usable programmatically, where identity roles are added / updated / removed.
	 * Lookout: synchronous request (and provisioning) is executed - usable for removal (delete identity, contract) 
	 * operations only or where provisioning is skipped.
	 * Lookout: method realize request synchronously. Use {@link IdmRoleRequestService#startRequest(EntityEvent)) instead.
	 * 
	 * @param applicant
	 * @param concepts [optional] - if empty concepts are given, then no request will be executed.
	 * @return
	 * @since 9.6.0
	 * @see #startConcepts(EntityEvent, EntityEvent)
	 */
	IdmRoleRequestDto executeConceptsImmediate(UUID applicant, List<IdmConceptRoleRequestDto> concepts);

	/**
	 * Execute concepts via request - usable programmatically, where identity roles are added / updated / removed.
	 * Lookout: synchronous request (and provisioning) is executed - usable for removal (delete identity, contract) 
	 * operations only or where provisioning is skipped.
	 * Lookout: method realize request synchronously. Use {@link IdmRoleRequestService#startRequest(EntityEvent)) instead.
	 * 
	 * 
	 * @param applicant
	 * @param concepts [optional] - if empty concepts are given, then no request will be executed.
	 * @param additional properties (will be added to the request event)
	 * @return
	 * @since 9.7.1
	 * @see #startConcepts(EntityEvent, EntityEvent)
	 */
	IdmRoleRequestDto executeConceptsImmediate(UUID applicant, List<IdmConceptRoleRequestDto> concepts,
			Map<String, Serializable> additionalProperties);
	
	/**
	 * Save role requests (and concepts) and start request by given event.
	 * Request and concepts will be saved - prepare evnt only.
	 * 
	 * @param requestEvent filled request, event priority
	 * @param parentEvent parent event
	 * @return created role request
	 * @since 10.4.4
	 */
	IdmRoleRequestDto startConcepts(EntityEvent<IdmRoleRequestDto> requestEvent, EntityEvent<?> parentEvent);

	/**
	 * Start approval process for this request.
	 * 
	 * @param request
	 * @param checkRight
	 * @param event
	 * @param wfDefinition
	 * @return
	 */
	boolean startApprovalProcess(IdmRoleRequestDto request, boolean checkRight, EntityEvent<IdmRoleRequestDto> event,
			String wfDefinition);

	/**
	 * Set request state to CANCELED and stop workflow process (connected to this
	 * request)
	 * 
	 * @param dto
	 */
	void cancel(IdmRoleRequestDto dto);

	/**
	 * Creates request by given contract. If some roles are given, then will be
	 * create role request concepts for them.
	 * 
	 * @param contract
	 * @param roles
	 * @return
	 */
	IdmRoleRequestDto createRequest(IdmIdentityContractDto contract, IdmRoleDto... roles);

	/**
	 * Copy roles from identity, by whole identity or contract or selected identity roles.
	 *
	 * @param requestByIdentityDto
	 * @return
	 */
	IdmRoleRequestDto copyRolesByIdentity(IdmRoleRequestByIdentityDto requestByIdentityDto);

	/**
	 * Request and concept validation
	 *  
	 * @param request
	 */
	void validate(IdmRoleRequestDto request);

	/**
	 * Incompatible roles are resolved from currently assigned identity roles and the current request concepts.
	 * 
	 * @param request
	 * @param permissions
	 * @return
	 */
	Set<ResolvedIncompatibleRoleDto> getIncompatibleRoles(IdmRoleRequestDto request, IdmBasePermission... permissions);

	/**
	 * Remove founded duplicates with given {@link IdmConceptRoleRequestDto} and between {@link IdmIdentityRoleDto}.
	 * This operation return new list of {@link IdmConceptRoleRequestDto} without duplicates.<br />
	 * <br />
	 * Duplicates will be removed with duplicates founded in concept and with all another identity roles (except automatic and subroles).
	 * <br />
	 * Given concepts must not be immutable.
	 *
	 * @param concepts
	 * @param identityId
	 * @return
	 */
	List<IdmConceptRoleRequestDto> removeDuplicities(List<IdmConceptRoleRequestDto> concepts, UUID identityId);

	/**
	 * Mark all returned {@link IdmConceptRoleRequestDto} with {@link IdmConceptRoleRequestDto#isDuplicit()}.
	 * This operation expect that for given concept will be exists eavs as subdefinition (if role supports this).
	 *
	 * @param concepts
	 * @param allByIdentity
	 * @return
	 */
	List<IdmConceptRoleRequestDto> markDuplicates(List<IdmConceptRoleRequestDto> concepts,
			List<IdmIdentityRoleDto> allByIdentity);

	/**
	 * Method create {@link IdmConceptRoleRequestDto}
	 * 
	 * @param roleRequest
	 * @param contract
	 * @param roleId
	 * @param operation
	 * @return
	 */
	IdmConceptRoleRequestDto createConcept(IdmRoleRequestDto roleRequest, IdmIdentityContractDto contract, UUID identityRoleId, UUID roleId,
			ConceptRoleRequestOperation operation);

	/**
	 * Refresh state on a systems. If is state changed, then will be returned in the request
	 * 
	 * @param request
	 */
	IdmRoleRequestDto refreshSystemState(IdmRoleRequestDto request);

}
