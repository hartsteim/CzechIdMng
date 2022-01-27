package eu.bcvsolutions.idm.core.model.service.impl;

import static eu.bcvsolutions.idm.core.api.dto.OperationResultDto.PROPERTY_STATE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.activiti.engine.runtime.ProcessInstance;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.fusesource.hawtbuf.ByteArrayInputStream;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import eu.bcvsolutions.idm.core.api.audit.service.SiemLoggerManager;
import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.Loggable;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.PriorityType;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestedByType;
import eu.bcvsolutions.idm.core.api.dto.DuplicateRolesDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAccountDto;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCompositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestByIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.OperationResultDto;
import eu.bcvsolutions.idm.core.api.dto.ResolvedIncompatibleRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmConceptRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.exception.RoleRequestException;
import eu.bcvsolutions.idm.core.api.service.AbstractEventableDtoService;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmIncompatibleRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCompositionService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.ValueGeneratorManager;
import eu.bcvsolutions.idm.core.api.service.thin.IdmIdentityRoleThinService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.api.utils.ExceptionUtils;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.InvalidFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormValue_;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmConceptRoleRequest_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleComposition_;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleRequest;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleRequest_;
import eu.bcvsolutions.idm.core.model.event.IdentityRoleEvent;
import eu.bcvsolutions.idm.core.model.event.IdentityRoleEvent.IdentityRoleEventType;
import eu.bcvsolutions.idm.core.model.event.RoleRequestEvent;
import eu.bcvsolutions.idm.core.model.event.RoleRequestEvent.RoleRequestEventType;
import eu.bcvsolutions.idm.core.model.event.processor.role.RoleRequestApprovalProcessor;
import eu.bcvsolutions.idm.core.model.repository.IdmRoleRequestRepository;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.workflow.model.dto.WorkflowFilterDto;
import eu.bcvsolutions.idm.core.workflow.model.dto.WorkflowHistoricProcessInstanceDto;
import eu.bcvsolutions.idm.core.workflow.model.dto.WorkflowProcessInstanceDto;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowHistoricProcessInstanceService;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowProcessInstanceService;

/**
 * Default implementation of role request service.
 *
 * @author svandav
 *
 */
@Service("roleRequestService")
public class DefaultIdmRoleRequestService
		extends AbstractEventableDtoService<IdmRoleRequestDto, IdmRoleRequest, IdmRoleRequestFilter>
		implements IdmRoleRequestService {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DefaultIdmRoleRequestService.class);
	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	private IdmConceptRoleRequestService conceptRoleRequestService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private SecurityService securityService;
	@Autowired
	private WorkflowProcessInstanceService workflowProcessInstanceService;
	@Autowired
	private FormService formService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private WorkflowHistoricProcessInstanceService workflowHistoricProcessInstanceService;
	@Autowired
	private IdmIncompatibleRoleService incompatibleRoleService;
	@Autowired
	private IdmIdentityContractService identityContractService;
	@Autowired
	private IdmFormAttributeService formAttributeService;
	@Autowired
	private AttachmentManager attachmentManager;
	@Autowired
	private IdmRoleCompositionService roleCompositionService;
	@Autowired
	private ValueGeneratorManager valueGeneratorManager;
	@Autowired
	private IdmIdentityRoleThinService identityRoleThinService;
	//
	private IdmRoleRequestService roleRequestService;

	@Autowired
	public DefaultIdmRoleRequestService(IdmRoleRequestRepository repository,
			IdmConceptRoleRequestService conceptRoleRequestService, EntityEventManager entityEventManager) {
		super(repository, entityEventManager);
	}

	@Override
	public AuthorizableType getAuthorizableType() {
		return new AuthorizableType(CoreGroupPermission.ROLEREQUEST, getEntityClass());
	}

	@Override
	protected List<Predicate> toPredicates(Root<IdmRoleRequest> root, CriteriaQuery<?> query, CriteriaBuilder builder,
			IdmRoleRequestFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
		// applicant
		if (filter.getApplicantId() != null) {
			predicates.add(
					builder.equal(root.get(IdmRoleRequest_.applicant).get(IdmIdentity_.id), filter.getApplicantId()));
		}
		// duplicatedToRequestId
		if (filter.getDuplicatedToRequestId() != null) {
			predicates.add(builder.equal(root.get(IdmRoleRequest_.duplicatedToRequest).get(IdmRoleRequest_.id),
					filter.getDuplicatedToRequestId()));
		}
		//
		if (StringUtils.isNotEmpty(filter.getApplicant())) {
			predicates.add(builder.equal(root.get(IdmRoleRequest_.applicant).get(IdmIdentity_.username),
					filter.getApplicant()));
		}
		UUID creatorId = filter.getCreatorId();
		if (creatorId != null) {
			predicates.add(builder.equal(root.get(IdmRoleRequest_.creatorId), creatorId));
		}
		//
		if (filter.getState() != null) {
			predicates.add(builder.equal(root.get(IdmRoleRequest_.state), filter.getState()));
		}
		List<RoleRequestState> states = filter.getStates();
		if (!states.isEmpty()) {
			predicates.add(root.get(IdmRoleRequest_.state).in(states));
		}
		//
		List<UUID> applicants = filter.getApplicants();
		if (!applicants.isEmpty()) {
			predicates.add(root.get(IdmRoleRequest_.applicant).get(IdmIdentity_.id).in(applicants));
		}
		//
		List<OperationState> resultStates = filter.getSystemStates();
		if (!CollectionUtils.isEmpty(resultStates)) {
			predicates.add(root.get(IdmRoleRequest_.systemState).get(PROPERTY_STATE).in(resultStates));
		}
		//

		Boolean executed = filter.getExecuted();
		// If Boolean.FALSE, then return all requests where IdM state is not DUPLICATED, CANCELED, DISAPPROVED and IdM state is not EXECUTED or system state is not EXECUTED and not null.
		// If Boolean.TRUE, then return all requests where IdM state is EXECUTED and system state is EXECUTED.
		if (executed != null) {
			if (executed) {
				predicates.add(builder.and( //
						builder.equal(root.get(IdmRoleRequest_.state), RoleRequestState.EXECUTED), //
						builder.equal(root.get(IdmRoleRequest_.systemState).get(PROPERTY_STATE), //
								OperationState.EXECUTED) //
				));
			} else {
				predicates.add(builder.and( //
						builder.notEqual(root.get(IdmRoleRequest_.state), RoleRequestState.CANCELED), //
						builder.notEqual(root.get(IdmRoleRequest_.state), RoleRequestState.DUPLICATED), //
						builder.notEqual(root.get(IdmRoleRequest_.state), RoleRequestState.DISAPPROVED) //
				));
				predicates.add(builder.or( //
						builder.notEqual(root.get(IdmRoleRequest_.state), RoleRequestState.EXECUTED), //
						builder.and(
								builder.notEqual(
										root.get(IdmRoleRequest_.systemState).get(PROPERTY_STATE),
										OperationState.EXECUTED),
								builder.notEqual(
										root.get(IdmRoleRequest_.systemState).get(PROPERTY_STATE),
										OperationState.CANCELED),
								builder.isNotNull(
										root.get(IdmRoleRequest_.systemState).get(PROPERTY_STATE))) //
				));
			}

		}
		return predicates;
	}

	@Override
	@Transactional
	public IdmRoleRequestDto startRequest(UUID requestId, boolean checkRight) {
		Assert.notNull(requestId, "Role request ID is required!");
		// Load request ... check right for read
		IdmRoleRequestDto request = get(requestId, new IdmRoleRequestFilter(true));
		Assert.notNull(request, "Role request DTO is required!");
		//
		Map<String, Serializable> variables = new HashMap<>();
		variables.put(RoleRequestApprovalProcessor.CHECK_RIGHT_PROPERTY, checkRight);
		RoleRequestEvent event = new RoleRequestEvent(RoleRequestEventType.EXCECUTE, request, variables);
		//
		return startRequest(event);
	}

	@Override
	@Transactional
	public IdmRoleRequestDto startRequest(EntityEvent<IdmRoleRequestDto> event) {
		try {
			IdmRoleRequestService service = this.getIdmRoleRequestService();
			if (!(service instanceof DefaultIdmRoleRequestService)) {
				throw new CoreException("We expects instance of DefaultIdmRoleRequestService!");
			}
			return ((DefaultIdmRoleRequestService) roleRequestService).startRequestNewTransactional(event);
		} catch (Exception ex) {
			LOG.error(ex.getLocalizedMessage(), ex);
			IdmRoleRequestDto request = get(event.getContent().getId());
			Throwable exceptionToLog = ExceptionUtils.resolveException(ex);
			// Whole stack trace is too big, so we will save only message to the request log.
			String message = exceptionToLog.getLocalizedMessage();
			this.addToLog(request, message != null ? message : ex.getLocalizedMessage());
			request.setState(RoleRequestState.EXCEPTION);

			return save(request);
		}
	}

	@Override
	public IdmRoleRequestDto processException(UUID requestId, Exception exception) {
		Assert.notNull(requestId, "Request identifier is required.");
		Assert.notNull(exception, "Exception is required.");
		IdmRoleRequestDto request = this.get(requestId);
		Assert.notNull(request, "Request is required.");

		LOG.error(exception.getLocalizedMessage(), exception);
		Throwable exceptionToLog = ExceptionUtils.resolveException(exception);
		// Whole stack trace is too big, so we will save only message to the request log.
		String message = exceptionToLog.getLocalizedMessage();
		this.addToLog(request, message != null ? message : exception.getLocalizedMessage());
		request.setState(RoleRequestState.EXCEPTION);

		return save(request);
	}

	/**
	 * Internal start request. Start in new transaction
	 *
	 * @param event
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public IdmRoleRequestDto startRequestNewTransactional(EntityEvent<IdmRoleRequestDto> event) {
		return this.getIdmRoleRequestService().startRequestInternal(event);
	}

	@Override
	@Transactional
	public IdmRoleRequestDto startRequestInternal(UUID requestId, boolean checkRight) {
		return startRequestInternal(requestId, checkRight, false);
	}

	@Override
	@Transactional
	public IdmRoleRequestDto startRequestInternal(UUID requestId, boolean checkRight, boolean immediate) {
		LOG.debug("Start role request [{}], checkRight [{}], immediate [{}]", requestId, checkRight, immediate);
		Assert.notNull(requestId, "Role request ID is required!");
		// Load request ... check right for read
		IdmRoleRequestDto request = get(requestId);
		Assert.notNull(request, "Role request DTO is required!");
		//
		// Throw event.
		Map<String, Serializable> properties = new HashMap<>();
		properties.put(RoleRequestApprovalProcessor.CHECK_RIGHT_PROPERTY, checkRight);
		RoleRequestEvent event = new RoleRequestEvent(RoleRequestEventType.EXCECUTE, request, properties);
		if (immediate) {
			event.setPriority(PriorityType.IMMEDIATE);
		}
		return startRequestInternal(event);
	}

	@Override
	@Transactional
	public IdmRoleRequestDto startRequestInternal(EntityEvent<IdmRoleRequestDto> event, EntityEvent<?> parentEvent) {
		IdmRoleRequestDto request = event.getContent();
		//
		LOG.debug("Start role request [{}], checkRight [{}], immediate [{}]",
				request.getId(),
				event.getProperties().get(RoleRequestApprovalProcessor.CHECK_RIGHT_PROPERTY),
				event.getPriority());
		Assert.notNull(request, "Role request DTO is required!");
		Assert.isTrue(
				RoleRequestState.CONCEPT == request.getState() || RoleRequestState.DUPLICATED == request.getState()
						|| RoleRequestState.EXCEPTION == request.getState(),
				"Only role request with CONCEPT or EXCEPTION or DUPLICATED state can be started!");

		// Request and concepts validation
		this.validate(request);

		// VS: Check on the duplicate request was removed - does not work for role attributes and is slow.

		// Convert whole request to JSON and persist (without logs and embedded data)
		// Original request was canceled (since 9.4.0)

		// Request will be set on in progress state
		request.setState(RoleRequestState.IN_PROGRESS);
		this.save(request);
		event.setContent(request);
		//
		IdmRoleRequestDto content = this.publish(event, parentEvent).getContent();
		// Returned content is not actual, we need to load fresh request
		return this.get(content.getId());
	}

	@Override
	@Transactional
	public IdmRoleRequestDto startRequestInternal(EntityEvent<IdmRoleRequestDto> event) {
		return startRequestInternal(event, null);
	}

	@Override
	@Transactional
	public boolean startApprovalProcess(IdmRoleRequestDto request, boolean checkRight,
			EntityEvent<IdmRoleRequestDto> event, String wfDefinition) {

		// If is request marked as executed immediately, then we will check right
		// and do realization immediately (without start approval process)
		if (request.isExecuteImmediately()) {
			boolean haveRightExecuteImmediately = securityService
					.hasAnyAuthority(CoreGroupPermission.ROLE_REQUEST_EXECUTE);

			if (checkRight && !haveRightExecuteImmediately) {
				throw new RoleRequestException(CoreResultCode.ROLE_REQUEST_NO_EXECUTE_IMMEDIATELY_RIGHT,
						ImmutableMap.of("new", request));
			}

			// All concepts in progress state will be set on approved (we can
			// execute it immediately)
			request.getConceptRoles()
					.stream()
					.filter(concept -> RoleRequestState.IN_PROGRESS == concept.getState())
					.forEach(concept -> {
						if (!cancelInvalidConcept(null, concept, request)) {
							concept.setState(RoleRequestState.APPROVED);
							conceptRoleRequestService.save(concept);
						} else {
							// save request log, after concept was canceled
							this.save(request);
						}
					});

			// Execute request immediately
			return true;
		}

		IdmIdentityDto applicant = identityService.get(request.getApplicant());
		Map<String, Object> variables = new HashMap<>();
		IdmRoleRequestDto eventRequest = event.getContent();
		// Minimize size of DTO persisting to WF;
		trimRequest(eventRequest);
		event.setContent(eventRequest);
		variables.put(EntityEvent.EVENT_PROPERTY, event);

		ProcessInstance processInstance = workflowProcessInstanceService.startProcess(wfDefinition,
				IdmIdentity.class.getSimpleName(), applicant.getUsername(), applicant.getId().toString(),
				variables);
		// We have to refresh request (maybe was changed in WF process)
		IdmRoleRequestDto requestAfterWf = this.get(eventRequest.getId());
		requestAfterWf.setWfProcessId(processInstance.getProcessInstanceId());
		this.save(requestAfterWf);

		return false;
	}

	@Override
	@Transactional
	public IdmRoleRequestDto executeRequest(UUID requestId) {
		// We can`t catch and log exception to request, because this transaction will be
		// marked as to rollback.
		// We can`t run this method in new transaction, because changes on request
		// (state modified in WF for example) is in uncommited transaction!
		//
		// prepare request event
		Assert.notNull(requestId, "Role request ID is required!");
		IdmRoleRequestDto request = this.get(requestId, new IdmRoleRequestFilter(true));
		Assert.notNull(request, "Role request is required!");
		RoleRequestEvent event = new RoleRequestEvent(RoleRequestEventType.EXCECUTE, request);
		//
		return this.executeRequestInternal(event);
	}

	@Override
	@Transactional
	public IdmRoleRequestDto executeRequest(EntityEvent<IdmRoleRequestDto> requestEvent) {
		return this.executeRequestInternal(requestEvent);
	}

	private IdmRoleRequestDto executeRequestInternal(EntityEvent<IdmRoleRequestDto> requestEvent) {
		UUID requestId = requestEvent.getContent().getId();
		Assert.notNull(requestId, "Role request ID is required!");
		IdmRoleRequestDto request = this.get(requestId, new IdmRoleRequestFilter(true));
		Assert.notNull(request, "Role request is required!");

		List<IdmConceptRoleRequestDto> concepts = request.getConceptRoles();
		IdmIdentityDto identity = identityService.get(request.getApplicant());

		boolean identityNotSame = concepts.stream().anyMatch(concept -> {
			// get contract DTO from embedded map
			IdmIdentityContractDto contract = (IdmIdentityContractDto) concept.getEmbedded()
					.get(IdmConceptRoleRequestService.IDENTITY_CONTRACT_FIELD);
			if (contract == null) {
				contract = identityContractService.get(concept.getIdentityContract());
			}
			Assert.notNull(contract, "Contract cannot be empty!");
			return !identity.getId().equals(contract.getIdentity());
		});

		if (identityNotSame) {
			throw new RoleRequestException(CoreResultCode.ROLE_REQUEST_APPLICANTS_NOT_SAME,
					ImmutableMap.of("request", request, "applicant", identity.getUsername()));
		}

		// Add changed identity-roles to event (prevent redundant search). We will used them for recalculations (ACM / provisioning).
		// Beware!! Sets have to be defined here, because without that will be not propagated to a sub event (role-request -> identity-role event)!
		requestEvent.getProperties().put(IdentityRoleEvent.PROPERTY_ASSIGNED_NEW_ROLES, Sets.newHashSet());
		requestEvent.getProperties().put(IdentityRoleEvent.PROPERTY_ASSIGNED_UPDATED_ROLES, Sets.newHashSet());
		requestEvent.getProperties().put(IdentityRoleEvent.PROPERTY_ASSIGNED_REMOVED_ROLES, Sets.newHashSet());
		requestEvent.getProperties().put(IdmAccountDto.IDENTITY_ACCOUNT_FOR_DELAYED_ACM, Sets.newHashSet());
		requestEvent.getProperties().put(IdmAccountDto.ACCOUNT_FOR_ADDITIONAL_PROVISIONING, Sets.newHashSet());
		
		// Remove not approved concepts.
		List<IdmConceptRoleRequestDto> approvedConcepts = concepts
				.stream()
				.filter(concept -> {
					// Only approved concepts can be executed.
					// Concepts in concept state will be executed too (for situation, when will be
					// approval event disabled).
					return RoleRequestState.APPROVED == concept.getState() || RoleRequestState.CONCEPT == concept.getState();
				})
				.collect(Collectors.toList());
		
		// Add concepts for business roles.
		List<IdmIdentityRoleDto> allAssignedRoles = identityRoleService.findAllByIdentity(identity.getId());
		List<IdmConceptRoleRequestDto> allApprovedConcepts = appendBusinessRoleConcepts(approvedConcepts, allAssignedRoles);

		// Create new identity role.
		allApprovedConcepts.stream()
				.filter(concept -> ConceptRoleRequestOperation.ADD == concept.getOperation())
				.forEach(concept -> {
					if (!cancelInvalidConcept(allAssignedRoles, concept, request)) {
						// assign new role
						createAssignedRole(allApprovedConcepts, concept, request, requestEvent);
					}

					flushHibernateSession();
				});

		// Update identity role
		allApprovedConcepts.stream()
				.filter(concept -> ConceptRoleRequestOperation.UPDATE == concept.getOperation())
				.forEach(concept -> {
					if (!cancelInvalidConcept(allAssignedRoles, concept, request)) {
						updateAssignedRole(allApprovedConcepts, concept, request, requestEvent);
					}

					flushHibernateSession();
				});
		
		// Delete identity sub roles at first (prevent to delete sub roles by referential integrity).
		allApprovedConcepts
			.stream()
			.filter(concept -> ConceptRoleRequestOperation.REMOVE == concept.getOperation())
			.filter(concept -> concept.getDirectConcept() != null)
			.forEach(concept -> {
				if (!cancelInvalidConcept(allAssignedRoles, concept, request)) {
					removeAssignedRole(concept, request, requestEvent);
				}
	
				flushHibernateSession();
			});

		// Delete direct identity role
		allApprovedConcepts
			.stream()
			.filter(concept -> ConceptRoleRequestOperation.REMOVE == concept.getOperation())
			.filter(concept -> concept.getDirectConcept() == null)
			.forEach(concept -> {
				if (!cancelInvalidConcept(allAssignedRoles, concept, request)) {
					removeAssignedRole(concept, request, requestEvent);
				}
	
				flushHibernateSession();
			});

		return this.save(request);
	}

	@Override
	public void validate(IdmRoleRequestDto request) {
		Assert.notNull(request, "Request is required.");

		List<IdmConceptRoleRequestDto> conceptRoles = request.getConceptRoles();

		conceptRoles.forEach(concept -> {
			List<InvalidFormAttributeDto> validationResults = conceptRoleRequestService.validateFormAttributes(concept);
			if (validationResults != null && !validationResults.isEmpty()) {
				IdmRoleDto role = null;
				if(concept.getRole() != null) {
					role = DtoUtils.getEmbedded(concept, IdmConceptRoleRequest_.role, IdmRoleDto.class, null);
					if (role == null) {
						role = roleService.get(concept.getRole());
					}
				} else {
					IdmIdentityRoleDto identityRole = DtoUtils.getEmbedded(concept, IdmConceptRoleRequest_.identityRole, IdmIdentityRoleDto.class, null);
					if (identityRole == null) {
						identityRole = identityRoleThinService.get(concept.getIdentityRole());
					}
					if (identityRole != null) {
						 role = DtoUtils.getEmbedded(concept, IdmIdentityRole_.role, IdmRoleDto.class);
					}
				}
				throw new ResultCodeException(CoreResultCode.ROLE_REQUEST_UNVALID_CONCEPT_ATTRIBUTE,
						ImmutableMap.of( //
								"concept", concept.getId(), //
								"roleCode", role != null ? role.getCode() : "",
								"request", concept.getRoleRequest(), //
								"attributeCode", validationResults.get(0).getAttributeCode() //
								) //
						); //
			}
		});
	}

	@Override
	public boolean supportsToDtoWithFilter() {
		return true;
	}

	@Override
	public IdmRoleRequestDto toDto(IdmRoleRequest entity, IdmRoleRequestDto dto, IdmRoleRequestFilter filter) {
		IdmRoleRequestDto requestDto = super.toDto(entity, dto, filter);
		// Set concepts to request DTO, but only if given filter has sets include-concepts attribute
		if (requestDto != null && filter != null && filter.isIncludeConcepts()) {
			requestDto.setConceptRoles(conceptRoleRequestService.findAllByRoleRequest(requestDto.getId()));
		}
		// Load and add WF process DTO to embedded. Prevents of many requests
		// from FE.
		if (requestDto != null && requestDto.getWfProcessId() != null) {
			if (RoleRequestState.IN_PROGRESS == requestDto.getState()) {
				String wfProcessId = requestDto.getWfProcessId();
				// Instance of process should exists only in 'IN_PROGRESS' state
				WorkflowProcessInstanceDto processInstanceDto = workflowProcessInstanceService.get(wfProcessId);
				// Trim a process variables - prevent security issues and too
				// high of response
				// size
				if (filter != null && filter.isIncludeApprovers()) {
					requestDto.setApprovers(workflowProcessInstanceService.getApproversForProcess(wfProcessId));
				}
				if (processInstanceDto != null) {
					processInstanceDto.setProcessVariables(null);
				}
				requestDto.getEmbedded().put(IdmRoleRequestDto.WF_PROCESS_FIELD, processInstanceDto);
			} else {
				// In others states we need load historic process
				WorkflowHistoricProcessInstanceDto processHistDto = workflowHistoricProcessInstanceService.get(requestDto.getWfProcessId());
				// Trim a process variables - prevent security issues and too
				// high of response
				// size
				if (processHistDto != null) {
					processHistDto.setProcessVariables(null);
				}
				requestDto.getEmbedded().put(IdmRoleRequestDto.WF_PROCESS_FIELD, processHistDto);
			}
		}

		return requestDto;
	}

	@Override
	public IdmRoleRequest toEntity(IdmRoleRequestDto dto, IdmRoleRequest entity) {
		if (dto == null) {
			return null;
		}

		if (this.isNew(dto)) {
			dto.setSystemState(new OperationResultDto(OperationState.CREATED));
			dto.setState(RoleRequestState.CONCEPT);
		}

		return super.toEntity(dto, entity);
	}

	@Override
	@Transactional
	public IdmRoleRequestDto refreshSystemState(IdmRoleRequestDto request) {
		Assert.notNull(request, "Role request cannot be null!");

		RoleRequestEvent requestEvent = new RoleRequestEvent(RoleRequestEventType.REFRESH_SYSTEM_STATE, request);
		this.publish(requestEvent);
		return requestEvent.getContent();
	}

	@Override
	public void addToLog(Loggable logItem, String text) {
		StringBuilder sb = new StringBuilder();
		sb.append(ZonedDateTime.now());
		sb.append(": ");
		sb.append(text);
		text = sb.toString();
		logItem.addToLog(text);
		LOG.info(text);

	}

	@Override
	@Transactional
	public void deleteInternal(IdmRoleRequestDto dto) {
		Assert.notNull(dto, "DTO is required.");
		Assert.notNull(dto.getId(), "DTO identifier is required.");

		// Find all request where is this request duplicated and remove relation
		IdmRoleRequestFilter conceptRequestFilter = new IdmRoleRequestFilter();
		conceptRequestFilter.setDuplicatedToRequestId(dto.getId());
		this.find(conceptRequestFilter, null).getContent().forEach(duplicant -> {
			duplicant.setDuplicatedToRequest(null);
			if (RoleRequestState.DUPLICATED == duplicant.getState()) {
				duplicant.setState(RoleRequestState.CONCEPT);
				duplicant.setDuplicatedToRequest(null);
			}
			String message = MessageFormat.format("Duplicated request [{0}] was deleted!", dto.getId());
			this.addToLog(duplicant, message);
			this.save(duplicant);
		});

		// Stop connected WF process
		cancelWF(dto);

		// We have to delete all concepts for this request
		IdmConceptRoleRequestFilter conceptFilter = new IdmConceptRoleRequestFilter();
		conceptFilter.setRoleRequestId(dto.getId());
		conceptRoleRequestService.find(conceptFilter, null) //
				.getContent() //
				.forEach(concept -> conceptRoleRequestService.delete(concept));
		super.deleteInternal(dto);
	}

	@Override
	@Transactional
	public void cancel(IdmRoleRequestDto dto) {
		cancelWF(dto);
		dto.setState(RoleRequestState.CANCELED);
		this.save(dto);
	}

	@Override
	@Transactional
	public IdmRoleRequestDto createRequest(IdmIdentityContractDto contract, IdmRoleDto... roles) {
		Assert.notNull(contract, "Contract must be filled for create role request!");
		IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setApplicant(contract.getIdentity());
		roleRequest.setRequestedByType(RoleRequestedByType.AUTOMATICALLY);
		roleRequest.setExecuteImmediately(true);
		roleRequest = this.save(roleRequest);
		if (roles != null) {
			for (IdmRoleDto role : roles) {
				createConcept(roleRequest, contract, null, role.getId(), ConceptRoleRequestOperation.ADD);
			}
		}
		return roleRequest;
	}

	@Override
	@Transactional
	public IdmRoleRequestDto copyRolesByIdentity(IdmRoleRequestByIdentityDto requestByIdentityDto) {
		Assert.notNull(requestByIdentityDto, "Request by identity must exist!");
		Assert.notNull(requestByIdentityDto.getIdentityContract(), "Contract must be filled for create role request!");

		UUID identityContractId = requestByIdentityDto.getIdentityContract();
		UUID roleRequestId = requestByIdentityDto.getRoleRequest();
		LocalDate validFrom = requestByIdentityDto.getValidFrom();
		LocalDate validTill = requestByIdentityDto.getValidTill();
		boolean copyRoleParameters = requestByIdentityDto.isCopyRoleParameters();

		List<UUID> identityRoles = requestByIdentityDto.getIdentityRoles();

		if (roleRequestId == null) {
			IdmIdentityContractDto identityContractDto = identityContractService.get(identityContractId);
			IdmRoleRequestDto request = this.createManualRequest(identityContractDto.getIdentity());
			roleRequestId = request.getId();
		}

		for (int i = 0; identityRoles.size() > i; i++) {
			UUID identityRoleId = identityRoles.get(i);
			// Flush Hibernate in batch - performance improving
			if (i % 20 == 0 && i > 0) {
				 flushHibernateSession();
			}
			IdmIdentityRoleDto identityRoleDto = identityRoleThinService.get(identityRoleId);
			if (identityRoleDto == null) {
				LOG.error("For given identity role id [{}] was not found entity. ", identityRoleId);
				continue;
			}

			IdmConceptRoleRequestDto conceptRoleRequestDto = new IdmConceptRoleRequestDto();
			conceptRoleRequestDto.setIdentityContract(identityContractId);
			conceptRoleRequestDto.setRoleRequest(roleRequestId);
			conceptRoleRequestDto.setRole(identityRoleDto.getRole());
			conceptRoleRequestDto.setValidFrom(validFrom);
			conceptRoleRequestDto.setValidTill(validTill);
			conceptRoleRequestDto.setOperation(ConceptRoleRequestOperation.ADD);
			conceptRoleRequestDto.addToLog(MessageFormat.format(
					"Concept was added from the copy roles operation (includes identity-role attributes [{0}]).",
					 copyRoleParameters));

			IdmRoleDto roleDto = DtoUtils.getEmbedded(identityRoleDto, IdmIdentityRole_.role, IdmRoleDto.class);
			// Copy role parameters
			if (copyRoleParameters) {
				// For copy must exist identity role attribute definition
				if (roleDto.getIdentityRoleAttributeDefinition() != null) {
					IdmFormInstanceDto formInstance = identityRoleService.getRoleAttributeValues(identityRoleDto);

					List<IdmFormValueDto> values = formInstance.getValues();
					List<IdmFormValueDto> finalValues = new ArrayList<>(values);
					// Iterate over all values and find values that must be deep copied
					for (IdmFormValueDto value : values) {
						IdmFormAttributeDto attribute = DtoUtils.getEmbedded(value, IdmFormValue_.formAttribute, IdmFormAttributeDto.class, null);
						if (attribute == null) {
							attribute = formAttributeService.get(value.getFormAttribute());
						}

						// Attachments are one of attribute with deep copy
						// TODO: confidential values are another, but identity role doesn't support them
						if (attribute.getPersistentType() == PersistentType.ATTACHMENT) {
							finalValues.remove(value);
							IdmFormValueDto valueCopy = new IdmFormValueDto(attribute);
							IdmAttachmentDto originalAttachmentDto = attachmentManager.get(value.getUuidValue());

							ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

							try (InputStream inputStream = attachmentManager.getAttachmentData(originalAttachmentDto.getId())) {
								IOUtils.copy(inputStream, outputStream);
							} catch (IOException e) {
								LOG.error("Error during copy attachment data.", e);
								throw new ResultCodeException(CoreResultCode.ATTACHMENT_CREATE_FAILED, ImmutableMap.of(
										"attachmentName", originalAttachmentDto.getName(),
										"ownerType", originalAttachmentDto.getOwnerType(),
										"ownerId", originalAttachmentDto.getOwnerId() == null ? "" : originalAttachmentDto.getOwnerId().toString())
										, e);
							}

							IdmAttachmentDto attachmentCopy = new IdmAttachmentDto();
							attachmentCopy.setOwnerType(AttachmentManager.TEMPORARY_ATTACHMENT_OWNER_TYPE);
							attachmentCopy.setName(originalAttachmentDto.getName());
							attachmentCopy.setMimetype(originalAttachmentDto.getMimetype());
							attachmentCopy.setInputData(new ByteArrayInputStream(outputStream.toByteArray()));

							attachmentCopy = attachmentManager.saveAttachment(null, attachmentCopy); // owner and version is resolved after attachment is saved
							valueCopy.setUuidValue(attachmentCopy.getId());
							valueCopy.setShortTextValue(attachmentCopy.getName());

							finalValues.add(valueCopy);
						}
					}

					formInstance.setValues(finalValues);

					conceptRoleRequestDto.setEavs(Lists.newArrayList(formInstance));
				}
			}

			conceptRoleRequestDto = conceptRoleRequestService.save(conceptRoleRequestDto);
		}

		return this.get(roleRequestId);
	}

	@Override
	public Set<ResolvedIncompatibleRoleDto> getIncompatibleRoles(IdmRoleRequestDto request, IdmBasePermission... permissions) {
		// Currently assigned roles
		IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();
		identityRoleFilter.setIdentityId(request.getApplicant());
		List<IdmIdentityRoleDto> identityRoles = identityRoleService.find(identityRoleFilter, null, permissions).getContent();
		// Roles from concepts
		IdmConceptRoleRequestFilter conceptFilter = new IdmConceptRoleRequestFilter();
		conceptFilter.setRoleRequestId(request.getId());
		List<IdmConceptRoleRequestDto> concepts = conceptRoleRequestService.find(conceptFilter, null, permissions).getContent();
		Set<UUID> removedIdentityRoleIds = new HashSet<>();

		// We don't want calculate incompatible roles for ended or disapproved concepts
		List<IdmConceptRoleRequestDto> conceptsForCheck = concepts //
				.stream() //
				.filter(concept -> {
					// role can be deleted in the mean time
					return concept.getRole() != null;
				})
				.filter(concept -> //
				RoleRequestState.CONCEPT == concept.getState() //
						|| RoleRequestState.IN_PROGRESS == concept.getState()
						|| RoleRequestState.APPROVED == concept.getState()
						|| RoleRequestState.EXECUTED == concept.getState()) //
				.collect(Collectors.toList());

		Set<IdmRoleDto> roles = new HashSet<>();
		conceptsForCheck
				.stream()
				.filter(concept -> {
					boolean isDelete = concept.getOperation() == ConceptRoleRequestOperation.REMOVE;
					if (isDelete) {
						// removed role fixes the incompatibility
						removedIdentityRoleIds.add(concept.getIdentityRole());
					}
					return !isDelete;
				})
				.forEach(concept -> roles.add(DtoUtils.getEmbedded(concept, IdmConceptRoleRequest_.role)));

		identityRoles
				.stream()
				.filter(identityRole -> !removedIdentityRoleIds.contains(identityRole.getId()))
				.forEach(identityRole -> roles.add(DtoUtils.getEmbedded(identityRole, IdmIdentityRole_.role)));

		// We want to returns only incompatibilities caused by new added roles
	 	Set<ResolvedIncompatibleRoleDto> incompatibleRoles = incompatibleRoleService.resolveIncompatibleRoles(Lists.newArrayList(roles));
		return incompatibleRoles.stream() //
			.filter(incompatibleRole -> {
				return conceptsForCheck.stream() //
					.anyMatch(concept -> concept.getOperation() == ConceptRoleRequestOperation.ADD
						&& (concept.getRole().equals(incompatibleRole.getDirectRole().getId())
								|| concept.getRole().equals(incompatibleRole.getIncompatibleRole().getSuperior())
								|| concept.getRole().equals(incompatibleRole.getIncompatibleRole().getSub())
							)); //
			}).collect(Collectors.toSet());
	}

	@Override
	public List<IdmConceptRoleRequestDto> markDuplicates(List<IdmConceptRoleRequestDto> concepts, List<IdmIdentityRoleDto> allByIdentity) {
		Assert.notNull(concepts, "Role request concepts are required.");

		// Check duplicates between concepts
		markDuplicatesInConcepts(concepts);

		// Split by role UUID
		Map<UUID, List<IdmIdentityRoleDto>> identityRolesByRole = allByIdentity
		.stream() //
		.collect( //
				Collectors.groupingBy( // Group identity roles by role
						IdmIdentityRoleDto::getRole) //
				); //

		// TODO: create hashMap with used roles (simple cache)
		for (IdmConceptRoleRequestDto concept : concepts) {
			// Only add or modification will be processed
			if (concept.getOperation() == ConceptRoleRequestOperation.REMOVE) {
				continue;
			}
			UUID roleId = concept.getRole();

			// Get all identity roles by role
			List<IdmIdentityRoleDto> identityRoles = identityRolesByRole.get(roleId);
			if (identityRoles == null) {
				continue;
			}

			// Create temporary identity role
			IdmIdentityRoleDto tempIdentityRole = createTempIdentityRole(concept);

			// Iterate over all identity roles, but only with same roles.
			for (IdmIdentityRoleDto identityRole : identityRoles) {
				// We must get eavs by service. This is expensive operation. But we need it.
				IdmFormInstanceDto instanceDto = identityRoleService.getRoleAttributeValues(identityRole);
				if (instanceDto != null) {
					identityRole.setEavs(Lists.newArrayList(instanceDto));
				}
				IdmIdentityRoleDto duplicated = identityRoleService.getDuplicated(tempIdentityRole, identityRole, Boolean.FALSE);

				// Duplicated founded. Add UUID from identity role
				// Duplicated is only when is object not null and hasn't filled ID. Identity role can't be duplicated with concept.
				if (duplicated != null && duplicated.getId() == null) {
					DuplicateRolesDto duplicates = concept.getDuplicates();
					duplicates.getIdentityRoles().add(identityRole.getId());
					concept.setDuplicates(duplicates);
					concept.setDuplicate(Boolean.TRUE);
				}
			}

		}

		return concepts;
	}

	@Override
	public List<IdmConceptRoleRequestDto> removeDuplicities(List<IdmConceptRoleRequestDto> concepts, UUID identityId) {
		Assert.notNull(identityId, "Identity identifier is required.");
		Assert.notNull(concepts, "Role request concepts are required.");

		// TODO: check duplicity between concepts

		// List of uuid's identity roles that will be removed in this concept
		List<UUID> identityRolesForRemove = concepts //
				.stream() //
				.filter(concept -> concept.getOperation() == ConceptRoleRequestOperation.REMOVE)
				.map(IdmConceptRoleRequestDto::getIdentityRole) //
				.collect(Collectors.toList()); //

		// Filter identity roles for that exists concept for removing
		List<IdmIdentityRoleDto> identityRoles = new ArrayList<>(identityRoleService.findAllByIdentity(identityId));
		identityRoles.removeIf(identityRole -> identityRolesForRemove.contains(identityRole.getId()));

		// Just mark duplicities
		concepts = this.markDuplicates(concepts, identityRoles);

		// Remove duplicities with subroles
		concepts = this.removeDuplicitiesSubRole(concepts, identityRoles);

		// Create final concepts and add non duplicities
		List<IdmConceptRoleRequestDto> conceptRolesFinal = new ArrayList<>();
		for (IdmConceptRoleRequestDto concept : concepts) {
			if (BooleanUtils.isNotTrue(concept.getDuplicate())) {
				conceptRolesFinal.add(concept);
			}
		}
		return conceptRolesFinal;
	}

	@Override
	@Transactional
	public IdmRoleRequestDto executeConceptsImmediate(UUID applicant, List<IdmConceptRoleRequestDto> concepts) {
		return this.executeConceptsImmediate(applicant, concepts, null);
	}

	@Override
	@Transactional
	public IdmRoleRequestDto executeConceptsImmediate(UUID applicant, List<IdmConceptRoleRequestDto> concepts, Map<String, Serializable> additionalProperties) {
		if (concepts == null || concepts.isEmpty()) {
			LOG.debug("No concepts are given, request for applicant [{}] will be not executed, returning null.", applicant);
			//
			return null;
		}
		Assert.notNull(applicant, "Applicant is required.");
		//
		IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setState(RoleRequestState.CONCEPT);
		roleRequest.setExecuteImmediately(true); // without approval
		roleRequest.setApplicant(applicant);
		roleRequest.setRequestedByType(RoleRequestedByType.AUTOMATICALLY);
		roleRequest = save(roleRequest);
		//
		for (IdmConceptRoleRequestDto concept : concepts) {
			concept.setRoleRequest(roleRequest.getId());
			//
			roleRequest.getConceptRoles().add(conceptRoleRequestService.save(concept));
		}
		//
		// start event with skip check authorities
		RoleRequestEvent requestEvent = new RoleRequestEvent(RoleRequestEventType.EXCECUTE, roleRequest);

		requestEvent.getProperties().put(IdmIdentityRoleService.SKIP_CHECK_AUTHORITIES, Boolean.TRUE);
		requestEvent.setPriority(PriorityType.IMMEDIATE); // execute request synchronously (asynchronicity schould be added from outside).
		// Add additional properties
		if (additionalProperties != null) {
			requestEvent.getProperties().putAll(additionalProperties);
		}
		//
		return startRequestInternal(requestEvent);
	}
	
	@Override
	@Transactional
	public IdmRoleRequestDto startConcepts(EntityEvent<IdmRoleRequestDto> requestEvent, EntityEvent<?> parentEvent) {
		IdmRoleRequestDto roleRequest = requestEvent.getContent();
		Assert.notNull(roleRequest, "Request is required.");
		List<IdmConceptRoleRequestDto> concepts = roleRequest.getConceptRoles();
		UUID applicant = roleRequest.getApplicant();
		Assert.notNull(applicant, "Applicant is required.");
		//
		if (concepts == null || concepts.isEmpty()) {
			LOG.debug("No concepts are given, request for applicant [{}] will be not executed, returning null.", applicant);
			//
			return null;
		}
		// set required request props and save request
		roleRequest.setState(RoleRequestState.CONCEPT);
		roleRequest.setExecuteImmediately(true); // without approval
		roleRequest.setRequestedByType(RoleRequestedByType.AUTOMATICALLY);
		roleRequest = save(roleRequest);
		requestEvent.setContent(roleRequest);
		//
		for (IdmConceptRoleRequestDto concept : concepts) {
			concept.setRoleRequest(roleRequest.getId());
			//
			conceptRoleRequestService.save(concept);
		}
		//
		// start event with skip check authorities
		requestEvent.getProperties().put(IdmIdentityRoleService.SKIP_CHECK_AUTHORITIES, Boolean.TRUE);
		// set parent (contract is disabled) event
		if (parentEvent != null) {
			requestEvent.setParentId(parentEvent.getId());
			requestEvent.setPriority(parentEvent.getPriority());
		}
		// prevent to start asynchronous event before previous update event is completed. 
		requestEvent.setSuperOwnerId(applicant);
		//
		return startRequestInternal(requestEvent, parentEvent);
	}



	@Override
	public IdmConceptRoleRequestDto createConcept(IdmRoleRequestDto roleRequest, IdmIdentityContractDto contract, UUID identityRoleId,
			UUID roleId, ConceptRoleRequestOperation operation) {
		IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
		conceptRoleRequest.setRoleRequest(roleRequest.getId());
		if (contract != null) {
			conceptRoleRequest.setIdentityContract(contract.getId());
			// We don't want filling validity for REMOVE operation
			if (ConceptRoleRequestOperation.REMOVE != operation) {
				conceptRoleRequest.setValidFrom(contract.getValidFrom());
				conceptRoleRequest.setValidTill(contract.getValidTill());
			}
		}
		conceptRoleRequest.setIdentityRole(identityRoleId);
		conceptRoleRequest.setRole(roleId);
		conceptRoleRequest.setOperation(operation);
		return conceptRoleRequestService.save(conceptRoleRequest);
	}
	
	/**
	 * Method provides specific logic for role request siem logging.
	 * 
	 */
	@Override
	protected void siemLog(EntityEvent<IdmRoleRequestDto> event, String status, String detail) {
		if (event == null) {
			return;
		}
		IdmRoleRequestDto dto = event.getContent();
		String operationType = event.getType().name();
		String action = siemLoggerManager.buildAction(SiemLoggerManager.ROLE_REQUEST_LEVEL_KEY, operationType);
		if(siemLoggerManager.skipLogging(action)) {
			return;
		}
		String transactionUuid = java.util.Objects.toString(dto.getTransactionId(),"");
		String result;
		if(StringUtils.isEmpty(detail)) {
			RoleRequestState state = dto.getState();
			result = java.util.Objects.toString(state,"");
			status = RoleRequestState.EXCEPTION == state ? SiemLoggerManager.FAILED_ACTION_STATUS : status;
		} else {
			result = detail;
		}
		siemLog(action, status, dto, null, transactionUuid, result);
	}

	/**
	 * Flush Hibernate session
	 */
	private void flushHibernateSession() {
		if (getHibernateSession().isOpen()) {
			getHibernateSession().flush();
			getHibernateSession().clear();
		}
	}

	private Session getHibernateSession() {
		return (Session) this.getEntityManager().getDelegate();
	}

	/**
	 * Remove identity-role by concept
	 *
	 * @param concept
	 * @param request
	 * @param requestEvent
	 */
	private void removeAssignedRole(IdmConceptRoleRequestDto concept, IdmRoleRequestDto request,
			EntityEvent<IdmRoleRequestDto> requestEvent) {
		Assert.notNull(concept.getIdentityRole(), "IdentityRole is mandatory for delete!");

		IdmIdentityRoleDto identityRole = DtoUtils.getEmbedded(concept, IdmConceptRoleRequest_.identityRole.getName(),
				IdmIdentityRoleDto.class, (IdmIdentityRoleDto) null);
		if (identityRole == null) {
			identityRole = identityRoleThinService.get(concept.getIdentityRole());
		}

		if (identityRole != null) {
			concept.setState(RoleRequestState.EXECUTED);
			concept.setIdentityRole(null); // we have to remove relation on
											// deleted identityRole
			String message = MessageFormat.format(
					"IdentityRole [{0}] (reqested in concept [{1}]) was deleted (from this role request).",
					identityRole.getId(), concept.getId());
			conceptRoleRequestService.addToLog(concept, message);
			conceptRoleRequestService.addToLog(request, message);
			conceptRoleRequestService.save(concept);

			IdentityRoleEvent event = new IdentityRoleEvent(IdentityRoleEventType.DELETE, identityRole,
					 ImmutableMap.of(IdmAccountDto.SKIP_PROPAGATE, Boolean.TRUE));

			identityRoleService.publish(event, requestEvent);
			// Add list of identity-accounts for delayed ACM to parent event
			Set<UUID> subIdentityAccountsForAcm = event
					.getSetProperty(IdmAccountDto.IDENTITY_ACCOUNT_FOR_DELAYED_ACM, UUID.class);
			Set<UUID> identityAccountsForAcm = requestEvent
					.getSetProperty(IdmAccountDto.IDENTITY_ACCOUNT_FOR_DELAYED_ACM, UUID.class);
			identityAccountsForAcm.addAll(subIdentityAccountsForAcm);
			// Add list of accounts for additional provisioning to parent event
			Set<UUID> subIdentityAccountsForProvisioning = event
					.getSetProperty(IdmAccountDto.ACCOUNT_FOR_ADDITIONAL_PROVISIONING, UUID.class);
			Set<UUID> identityAccountsForProvisioning = requestEvent
					.getSetProperty(IdmAccountDto.ACCOUNT_FOR_ADDITIONAL_PROVISIONING, UUID.class);
			identityAccountsForProvisioning.addAll(subIdentityAccountsForProvisioning);

			// Removed assigned roles by business roles
			Set<UUID> subRemovedIdentityRoles = event.getSetProperty(IdentityRoleEvent.PROPERTY_ASSIGNED_REMOVED_ROLES, UUID.class);
			// Add to parent event
			Set<UUID> removedIdentityRoles = requestEvent
					.getSetProperty(IdentityRoleEvent.PROPERTY_ASSIGNED_REMOVED_ROLES, UUID.class);
			removedIdentityRoles.addAll(subRemovedIdentityRoles);
			removedIdentityRoles.add(identityRole.getId());
		}
	}

	/**
	 * Update exists identity-role by concept
	 *
	 * @param concept
	 * @param request
	 * @param requestEvent
	 */
	private void updateAssignedRole(
			List<IdmConceptRoleRequestDto> allApprovedConcepts,
			IdmConceptRoleRequestDto concept, 
			IdmRoleRequestDto request,
			EntityEvent<IdmRoleRequestDto> requestEvent) {
		IdmIdentityRoleDto identityRole = identityRoleService.get(concept.getIdentityRole());
		identityRole = convertConceptRoleToIdentityRole(allApprovedConcepts, concept, identityRole);
		@SuppressWarnings("deprecation")
		IdentityRoleEvent event = new IdentityRoleEvent(
				IdentityRoleEventType.UPDATE, 
				identityRole, 
				ImmutableMap.of(
						IdmAccountDto.SKIP_PROPAGATE, Boolean.TRUE, // ~ skip provisioning
						EntityEventManager.EVENT_PROPERTY_SKIP_SUB_ROLES, Boolean.TRUE // sub roles are assigned by this request
				)
		);
		event.setPriority(PriorityType.IMMEDIATE);

		// propagate event
		identityRole = identityRoleService.publish(event, requestEvent).getContent();

		// Updated assigned roles by business roles
		Set<IdmIdentityRoleDto> subUpdatedIdentityRoles = event
				.getSetProperty(IdentityRoleEvent.PROPERTY_ASSIGNED_UPDATED_ROLES, IdmIdentityRoleDto.class);
		// Add to parent event
		Set<IdmIdentityRoleDto> updatedIdentityRoles = requestEvent
				.getSetProperty(IdentityRoleEvent.PROPERTY_ASSIGNED_UPDATED_ROLES, IdmIdentityRoleDto.class);
		updatedIdentityRoles.addAll(subUpdatedIdentityRoles);
		updatedIdentityRoles.add(identityRole);

		// Save created identity role id
		concept.setIdentityRole(identityRole.getId());
		concept.setState(RoleRequestState.EXECUTED);
		IdmRoleDto roleDto = DtoUtils.getEmbedded(identityRole, IdmIdentityRole_.role);
		String message = MessageFormat.format("Role [{0}] was changed. Requested in concept [{1}].",
				roleDto.getCode(), concept.getId());
		conceptRoleRequestService.addToLog(concept, message);
		conceptRoleRequestService.addToLog(request, message);
		conceptRoleRequestService.save(concept);
	}

	/**
	 * Create new identity-role by concept
	 *
	 * @param concept
	 * @param request
	 * @param requestEvent
	 */
	private void createAssignedRole(
			List<IdmConceptRoleRequestDto> allApprovedConcepts,
			IdmConceptRoleRequestDto concept,
			IdmRoleRequestDto request,
			EntityEvent<IdmRoleRequestDto> requestEvent) {
		IdmIdentityRoleDto identityRole = new IdmIdentityRoleDto();
		identityRole = convertConceptRoleToIdentityRole(allApprovedConcepts, concept, identityRole);
		@SuppressWarnings("deprecation")
		IdentityRoleEvent event = new IdentityRoleEvent(
				IdentityRoleEventType.CREATE,
				identityRole,
				ImmutableMap.of(
						IdmAccountDto.SKIP_PROPAGATE, Boolean.TRUE, // ~ skip provisioning
						EntityEventManager.EVENT_PROPERTY_SKIP_SUB_ROLES, Boolean.TRUE // sub roles are assigned by this request
				) 
		);
		event.setPriority(PriorityType.IMMEDIATE);

		// propagate event
		identityRole = identityRoleService.publish(event, requestEvent).getContent();

		// New assigned roles by business roles
		Set<IdmIdentityRoleDto> subNewIdentityRoles = event
				.getSetProperty(IdentityRoleEvent.PROPERTY_ASSIGNED_NEW_ROLES, IdmIdentityRoleDto.class);
		// Add to parent event
		Set<IdmIdentityRoleDto> addedIdentityRoles = requestEvent
				.getSetProperty(IdentityRoleEvent.PROPERTY_ASSIGNED_NEW_ROLES, IdmIdentityRoleDto.class);
		addedIdentityRoles.addAll(subNewIdentityRoles);
		addedIdentityRoles.add(identityRole);

		// Save created identity role id
		concept.setIdentityRole(identityRole.getId());
		concept.setState(RoleRequestState.EXECUTED);
		IdmRoleDto roleDto = DtoUtils.getEmbedded(identityRole, IdmIdentityRole_.role);
		String message = MessageFormat.format("Role [{0}] was added to applicant. Requested in concept [{1}].",
				roleDto.getCode(), concept.getId());
		conceptRoleRequestService.addToLog(concept, message);
		conceptRoleRequestService.addToLog(request, message);
		conceptRoleRequestService.save(concept);
	}

	private void cancelWF(IdmRoleRequestDto dto) {
		if (!Strings.isNullOrEmpty(dto.getWfProcessId())) {
			WorkflowFilterDto filter = new WorkflowFilterDto();
			filter.setProcessInstanceId(dto.getWfProcessId());

			List<WorkflowProcessInstanceDto> resources = workflowProcessInstanceService
					.find(filter, null)
					.getContent();
			if (resources.isEmpty()) {
				// Process with this ID not exist ... maybe was ended
				this.addToLog(dto, MessageFormat.format(
						"Workflow process with ID [{0}] was not deleted, because was not found. Maybe was ended before.",
						dto.getWfProcessId()));
				return;
			}

			workflowProcessInstanceService.delete(dto.getWfProcessId(),
					"Role request use this WF, was deleted. This WF was deleted too.");
			this.addToLog(dto,
					MessageFormat.format(
							"Workflow process with ID [{0}] was deleted, because this request is deleted/canceled",
							dto.getWfProcessId()));
		}
	}

	private IdmIdentityRoleDto convertConceptRoleToIdentityRole(
			List<IdmConceptRoleRequestDto> allConcepts,
			IdmConceptRoleRequestDto conceptRole,
			IdmIdentityRoleDto identityRole) {
		if (conceptRole == null || identityRole == null) {
			return null;
		}

		IdmRoleDto roleDto = DtoUtils.getEmbedded(conceptRole, IdmConceptRoleRequest_.role, IdmRoleDto.class);
		if (roleDto != null && roleDto.getIdentityRoleAttributeDefinition() != null) {
			IdmFormDefinitionDto formDefinitionDto = roleService.getFormAttributeSubdefinition(roleDto);
			formService.mergeValues(formDefinitionDto, conceptRole, identityRole);
		}

		identityRole.setRole(conceptRole.getRole());
		identityRole.setIdentityContract(conceptRole.getIdentityContract());
		identityRole.setContractPosition(conceptRole.getContractPosition());
		identityRole.setValidFrom(conceptRole.getValidFrom());
		identityRole.setValidTill(conceptRole.getValidTill());
		identityRole.setOriginalCreator(conceptRole.getOriginalCreator());
		identityRole.setOriginalCreatorId(conceptRole.getOriginalCreatorId());
		identityRole.setOriginalModifier(conceptRole.getOriginalModifier());
		identityRole.setOriginalModifierId(conceptRole.getOriginalModifierId());
		identityRole.setAutomaticRole(conceptRole.getAutomaticRole());
		// fill directly assigned role by superior concept
		UUID directRole = conceptRole.getDirectRole();
		UUID directConcept = conceptRole.getDirectConcept();
		if (directRole != null) { // update / delete / new role composition 
			identityRole.setDirectRole(directRole);
		} else if (directConcept != null) { // new identity role by superior concept
			directRole = allConcepts
				.stream()
				.filter(c -> c.getId().equals(directConcept))
				.findFirst()
				.get()
				.getIdentityRole();
			identityRole.setDirectRole(directRole);
		}
		identityRole.setRoleComposition(conceptRole.getRoleComposition());
		identityRole.setRoleSystem(conceptRole.getRoleSystem());

		return identityRole;
	}

	private IdmRoleRequestService getIdmRoleRequestService() {
		if (this.roleRequestService == null) {
			this.roleRequestService = applicationContext.getBean(IdmRoleRequestService.class);
		}
		return this.roleRequestService;
	}

	/**
	 * Trim request and his role concepts. Remove embedded objects. It is important
	 * for minimize size of dto persisted for example in WF process.
	 *
	 * @param request
	 */
	private void trimRequest(IdmRoleRequestDto request) {
		request.setLog(null);
		request.setEmbedded(null);
		request.setConceptRoles(null);
		request.setOriginalRequest(null);
	}


	/**
	 * Create concepts for removing duplicities with subroles.
	 * This operation execute get to database and slows the whole process.
	 *
	 * @param concepts
	 * @param allByIdentity
	 * @return
	 */
	private List<IdmConceptRoleRequestDto> removeDuplicitiesSubRole(List<IdmConceptRoleRequestDto> concepts, List<IdmIdentityRoleDto> allByIdentity) {
		List<IdmConceptRoleRequestDto> conceptsToRemove = new ArrayList<>();
		for (IdmConceptRoleRequestDto concept : concepts) {
			// Only add or modification
			if (concept.getOperation() != ConceptRoleRequestOperation.ADD &&
					concept.getOperation() != ConceptRoleRequestOperation.UPDATE) {
				continue;
			}

			if (concept.getDuplicate() != null) {
				continue;
			}

			UUID roleId = concept.getRole();
			IdmIdentityContractDto identityContract = DtoUtils.getEmbedded(concept, IdmConceptRoleRequest_.identityContract, IdmIdentityContractDto.class, null);

			// Find all sub roles for role.
			List<IdmRoleCompositionDto> subRoles = roleCompositionService.findAllSubRoles(roleId);
			for (IdmRoleCompositionDto subRoleComposition : subRoles) {
				IdmRoleDto subRole = DtoUtils.getEmbedded(subRoleComposition, IdmRoleComposition_.sub, IdmRoleDto.class, null);
				IdmIdentityRoleDto tempIdentityRoleSub = new IdmIdentityRoleDto();
				tempIdentityRoleSub.setDirectRole(UUID.randomUUID());
				tempIdentityRoleSub.setIdentityContract(concept.getIdentityContract());
				tempIdentityRoleSub.setRole(subRole.getId());
				tempIdentityRoleSub.setValidFrom(concept.getValidFrom());
				tempIdentityRoleSub.setValidTill(concept.getValidTill());
				tempIdentityRoleSub.setIdentityContractDto(identityContract);
				tempIdentityRoleSub.setCreated(ZonedDateTime.now());
				// This automatically add default values. This is also expensive operation.
				tempIdentityRoleSub = valueGeneratorManager.generate(tempIdentityRoleSub);

				for (IdmIdentityRoleDto identityRole : allByIdentity) {
					// Get identity role eavs. This is also expensive operation.
					identityRole.setEavs(Lists.newArrayList(identityRoleService.getRoleAttributeValues(identityRole)));
					IdmIdentityRoleDto duplicated = identityRoleService.getDuplicated(tempIdentityRoleSub, identityRole, Boolean.FALSE);

					// Duplication founded, create request
					if (duplicated != null && identityRole.getId().equals(duplicated.getId())) {
						IdmConceptRoleRequestDto removeConcept = new IdmConceptRoleRequestDto();
						removeConcept.setIdentityContract(identityRole.getIdentityContract());
						removeConcept.setIdentityRole(identityRole.getId());
						removeConcept.setOperation(ConceptRoleRequestOperation.REMOVE);
						removeConcept.setRoleRequest(concept.getRoleRequest());
						removeConcept.addToLog(MessageFormat.format("Removed by duplicates with subrole id [{}]", identityRole.getRoleComposition()));
						removeConcept = conceptRoleRequestService.save(removeConcept);
						conceptsToRemove.add(removeConcept);
					}
				}
			}
		}

		// Add all concept to remove
		concepts.addAll(conceptsToRemove);
		return concepts;
	}

	private IdmRoleRequestDto createManualRequest(UUID identityId) {
		Assert.notNull(identityId, "Identity id must be filled for create role request!");
		IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setApplicant(identityId);
		roleRequest.setRequestedByType(RoleRequestedByType.MANUALLY);
		roleRequest.setExecuteImmediately(false);
		roleRequest = this.save(roleRequest);
		return roleRequest;
	}

	/**
	 * Create temporary identity role from {@link IdmConceptRoleRequestDto}.
	 * Concept must contains identity contract in embedded and also EAV in _eavs.
	 *
	 * @param concept
	 * @return
	 */
	private IdmIdentityRoleDto createTempIdentityRole(IdmConceptRoleRequestDto concept) {
		IdmIdentityContractDto identityContract = DtoUtils.getEmbedded(concept, IdmConceptRoleRequest_.identityContract, IdmIdentityContractDto.class, null);

		IdmIdentityRoleDto temp = new IdmIdentityRoleDto();
		temp.setIdentityContract(concept.getIdentityContract());
		temp.setRole(concept.getRole());
		temp.setValidFrom(concept.getValidFrom());
		temp.setValidTill(concept.getValidTill());
		temp.setRoleSystem(concept.getRoleSystem());

		temp.setEavs(concept.getEavs());
		// Other way how to get eavs. But this way is to slow.
		// tempIdentityRole.setEavs(Lists.newArrayList(conceptRoleRequestService.getRoleAttributeValues(concept, false)));
		if (identityContract != null) {
			// contract cen be deleted in the mean time
			temp.setIdentityContractDto(identityContract);
		}
		// Created is set to now (with founded duplicity, this will be marked as duplicated)
		temp.setCreated(ZonedDateTime.now());
		return temp;
	}

	private void markDuplicatesInConcepts(List<IdmConceptRoleRequestDto> concepts) {
		// Mark duplicates with concepts
		// Compare conceptOne with conceptTwo
		for (IdmConceptRoleRequestDto conceptOne : concepts) {
			// Only add or modification will be processed
			if (conceptOne.getOperation() == ConceptRoleRequestOperation.REMOVE) {
				conceptOne.setDuplicate(Boolean.FALSE); // REMOVE concept can't be duplicated
				continue;
			}
			// role and contract can be removed in the mean time
			if (conceptOne.getRole() == null || conceptOne.getIdentityContract() == null) {
				conceptOne.setDuplicate(Boolean.FALSE);
				continue;
			}

			if (BooleanUtils.isTrue(conceptOne.getDuplicate())) {
				continue;
			}

			IdmIdentityRoleDto identityRoleOne = this.createTempIdentityRole(conceptOne);

			// check duplicates for concept
			for (IdmConceptRoleRequestDto conceptTwo : concepts) {
				// Only add or modification will be processed
				if (conceptTwo.getOperation() == ConceptRoleRequestOperation.REMOVE) {
					conceptTwo.setDuplicate(Boolean.FALSE); // REMOVE concept can be duplicated
					continue;
				}

				if (BooleanUtils.isTrue(conceptTwo.getDuplicate())) {
					continue;
				}

				// There must be compare by == not by equals. Because equals is overridden in
				// concept.
				if (conceptOne == conceptTwo) {
					continue;
				}
				IdmIdentityRoleDto identityRoleTwo = this.createTempIdentityRole(conceptTwo);

				// Get duplicated must be quick, because the method doesn't made query to
				// database
				IdmIdentityRoleDto duplicated = identityRoleService.getDuplicated(identityRoleOne, identityRoleTwo, Boolean.FALSE);
				if (duplicated == identityRoleOne) {
					// When is duplicate same as identityRoleOne set ID of concept two
					DuplicateRolesDto duplicates = conceptOne.getDuplicates();
					duplicates.getConcepts().add(conceptTwo.getId());
					conceptOne.setDuplicate(Boolean.TRUE);
					conceptOne.setDuplicates(duplicates);
				} else if (duplicated == identityRoleTwo) {
					// When is duplicate same as identityRoleTwo set ID of concept one
					DuplicateRolesDto duplicates = conceptTwo.getDuplicates();
					duplicates.getConcepts().add(conceptOne.getId());
					conceptTwo.setDuplicate(Boolean.TRUE);
					conceptTwo.setDuplicates(duplicates);
				}
			}

			// If concept isn't marked as duplicated set him false
			if (BooleanUtils.isNotTrue(conceptOne.getDuplicate())) {
				conceptOne.setDuplicate(Boolean.FALSE);
			}
		}
	}
	
	/**
	 * Check and cancel invalid concept => concept is canceled, when required entities as role, contract
	 * or assigned role is removed in the mean time in other session.
	 *
	 * @param concept
	 * @param request
	 * @return true, if concept is canceled
	 */
	private boolean cancelInvalidConcept(List<IdmIdentityRoleDto> automaticRoles, IdmConceptRoleRequestDto concept, IdmRoleRequestDto request) {
		String message = null;
		if (concept.getIdentityRole() == null
				&& ConceptRoleRequestOperation.ADD != concept.getOperation()) { // identity role is not given for ADD
			message = MessageFormat.format(
					"Request change in concept [{0}], was not executed, because assigned role was deleted before (not from this role request)!",
					concept.getId());
		} else if (concept.getIdentityContract() == null) {
			message = MessageFormat.format(
					"Request change in concept [{0}], was not executed, because identity contract was deleted before (not from this role request)!",
					concept.getId());
		} else if (concept.getRole() == null
				&& ConceptRoleRequestOperation.REMOVE != concept.getOperation()) { // role is optional in DELETE
			message = MessageFormat.format(
					"Request change in concept [{0}], was not executed, because requested role was deleted (not from this role request)!",
					concept.getId());
		} else if(ConceptRoleRequestOperation.ADD == concept.getOperation()
				&& concept.getAutomaticRole() != null 
				&& CollectionUtils.isNotEmpty(automaticRoles)) {
			// check duplicate assigned automatic role
			IdmIdentityRoleDto assignedRole = automaticRoles
				.stream()
				.filter(ir -> ir.getAutomaticRole() != null)
				.filter(ir -> Objects.equal(ir.getAutomaticRole(), concept.getAutomaticRole()))
				.filter(ir -> Objects.equal(ir.getContractPosition(), concept.getContractPosition()))
				.filter(ir -> Objects.equal(ir.getIdentityContract(), concept.getIdentityContract()))
				.findFirst()
				.orElse(null);
			if (assignedRole != null) {
				message = MessageFormat.format(
						"Request change in concept [{0}], was not executed, because requested automatic role was already assigned (not from this role request)!",
						concept.getId());
			}
		}

		if (message != null) {
			addToLog(request, message);
			conceptRoleRequestService.addToLog(concept, message);
			// Cancel concept and WF
			conceptRoleRequestService.cancel(concept);

			return true;
		}
		// concept is valid
		return false;
	}
	
	/**
	 * Create and append business role concepts.
	 * 
	 * @param concepts original concepts
	 * @param identityRoles 
	 * @return original + business role concepts (~all)
	 * @since 10.6.0
	 */
	private List<IdmConceptRoleRequestDto> appendBusinessRoleConcepts(
			List<IdmConceptRoleRequestDto> concepts,
			List<IdmIdentityRoleDto> identityRoles) {
		List<IdmConceptRoleRequestDto> results = Lists.newArrayList(concepts); // include original
		Map<UUID, List<IdmIdentityRoleDto>> subRoles = identityRoles
			.stream()
			.filter(identityRole -> identityRole.getDirectRole() != null)
			.collect(Collectors.groupingBy(IdmIdentityRoleDto::getDirectRole));
		
		concepts
			.stream()
			.filter(concept -> ConceptRoleRequestOperation.ADD == concept.getOperation())
			.forEach(concept -> {
				// find and assign all sub roles as concepts
				roleCompositionService
					.findAllSubRoles(concept.getRole())
					.forEach(subRole -> {
						IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
						conceptRoleRequest.setRoleRequest(concept.getRoleRequest());
						conceptRoleRequest.setOperation(ConceptRoleRequestOperation.ADD);
						// from concept
						conceptRoleRequest.setValidFrom(concept.getValidFrom());
						conceptRoleRequest.setValidTill(concept.getValidTill());
						conceptRoleRequest.setIdentityContract(concept.getIdentityContract());
						conceptRoleRequest.setContractPosition(concept.getContractPosition());
						// from assigned (~changed) sub role
						conceptRoleRequest.setRole(subRole.getSub());
						conceptRoleRequest.setDirectConcept(concept.getId());
						conceptRoleRequest.setRoleComposition(subRole.getId());
						// save and add to concepts to be processed
						results.add(conceptRoleRequestService.save(conceptRoleRequest));
					});
			});
		
		concepts
			.stream()
			.filter(concept -> ConceptRoleRequestOperation.UPDATE == concept.getOperation())
			.filter(concept -> subRoles.containsKey(concept.getIdentityRole()))
			.forEach(concept -> {
				// update sub roles by direct role
				UUID directRole = concept.getIdentityRole();
				subRoles
					.get(directRole)
					.forEach(subRole -> {
						IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
						conceptRoleRequest.setRoleRequest(concept.getRoleRequest());
						conceptRoleRequest.setOperation(ConceptRoleRequestOperation.UPDATE);
						// from concept
						conceptRoleRequest.setValidFrom(concept.getValidFrom());
						conceptRoleRequest.setValidTill(concept.getValidTill());
						conceptRoleRequest.setIdentityContract(concept.getIdentityContract());
						conceptRoleRequest.setContractPosition(concept.getContractPosition());
						// from assigned (~changed) sub role
						conceptRoleRequest.setRole(subRole.getRole());
						conceptRoleRequest.setIdentityRole(subRole.getId());
						conceptRoleRequest.setAutomaticRole(subRole.getAutomaticRole());
						conceptRoleRequest.setDirectRole(directRole);
						conceptRoleRequest.setDirectConcept(concept.getId());
						conceptRoleRequest.setRoleComposition(subRole.getRoleComposition());
						// save and add to concepts to be processed
						results.add(conceptRoleRequestService.save(conceptRoleRequest));
					});
			});
		
		concepts
			.stream()
			.filter(concept -> ConceptRoleRequestOperation.REMOVE == concept.getOperation())
			.filter(concept -> subRoles.containsKey(concept.getIdentityRole()))
			.forEach(concept -> {
				// remove sub roles by direct role
				UUID directRole = concept.getIdentityRole();
				subRoles
					.get(directRole)
					.forEach(subRole -> {
						IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
						conceptRoleRequest.setRoleRequest(concept.getRoleRequest());
						conceptRoleRequest.setOperation(ConceptRoleRequestOperation.REMOVE);
						// from concept
						conceptRoleRequest.setValidFrom(concept.getValidFrom());
						conceptRoleRequest.setValidTill(concept.getValidTill());
						conceptRoleRequest.setIdentityContract(concept.getIdentityContract());
						conceptRoleRequest.setContractPosition(concept.getContractPosition());
						// from assigned (~changed) sub role
						conceptRoleRequest.setRole(subRole.getRole());
						conceptRoleRequest.setIdentityRole(subRole.getId());
						conceptRoleRequest.setAutomaticRole(subRole.getAutomaticRole());
						conceptRoleRequest.setDirectRole(directRole);
						conceptRoleRequest.setDirectConcept(concept.getId());
						conceptRoleRequest.setRoleComposition(subRole.getRoleComposition());
						// save and add to concepts to be processed
						results.add(conceptRoleRequestService.save(conceptRoleRequest));
					});
			});

		return results;
	}
}
