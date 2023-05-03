package eu.bcvsolutions.idm.core.model.service.impl;

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

import eu.bcvsolutions.idm.core.api.domain.Codeable;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.service.NiceLabelable;
import eu.bcvsolutions.idm.core.api.service.ReadDtoService;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.fusesource.hawtbuf.ByteArrayInputStream;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import eu.bcvsolutions.idm.core.api.audit.service.SiemLoggerManager;
import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import static eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation.*;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.Loggable;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.PriorityType;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestedByType;
import eu.bcvsolutions.idm.core.api.dto.AbstractConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.AbstractRoleAssignmentDto;
import eu.bcvsolutions.idm.core.api.dto.ApplicantDto;
import eu.bcvsolutions.idm.core.api.dto.ApplicantImplDto;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.DuplicateRolesDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAccountDto;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRequestIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCompositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestByIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.OperationResultDto;
import static eu.bcvsolutions.idm.core.api.dto.OperationResultDto.PROPERTY_STATE;
import eu.bcvsolutions.idm.core.api.dto.ResolvedIncompatibleRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.BaseRoleAssignmentFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmBaseConceptRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRequestIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.exception.RoleRequestException;
import eu.bcvsolutions.idm.core.api.service.AbstractEventableDtoService;
import eu.bcvsolutions.idm.core.api.service.ApplicantService;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestManager;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmGeneralConceptRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmIncompatibleRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleAssignmentManager;
import eu.bcvsolutions.idm.core.api.service.IdmRoleAssignmentService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCompositionService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.thin.IdmIdentityRoleThinService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.api.utils.ExceptionUtils;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.InvalidFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.eav.entity.AbstractFormValue_;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.AbstractConceptRoleRequest_;
import eu.bcvsolutions.idm.core.model.entity.AbstractRoleAssignment_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleComposition_;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleRequest;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleRequest_;
import eu.bcvsolutions.idm.core.model.event.AbstractRoleAssignmentEvent;
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
	private IdmConceptRoleRequestManager conceptRoleRequestManager;

	@Autowired
	private IdmRoleAssignmentManager roleAssignmentManager;

	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private SecurityService securityService;
	@Autowired
	private WorkflowProcessInstanceService workflowProcessInstanceService;
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

	@Lazy
	@Autowired
	private LookupService lookupService;
	//
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
					builder.equal(root.get(IdmRoleRequest_.applicant), filter.getApplicantId()));
		}
		// duplicatedToRequestId
		if (filter.getDuplicatedToRequestId() != null) {
			predicates.add(builder.equal(root.get(IdmRoleRequest_.duplicatedToRequest).get(AbstractEntity_.id),
					filter.getDuplicatedToRequestId()));
		}
		UUID creatorId = filter.getCreatorId();
		if (creatorId != null) {
			predicates.add(builder.equal(root.get(AbstractEntity_.creatorId), creatorId));
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
			predicates.add(root.get(IdmRoleRequest_.applicant).in(applicants));
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
			return ((DefaultIdmRoleRequestService) service).startRequestNewTransactional(event);
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
						if (!conceptRoleRequestManager.getServiceForConcept(concept).cancelInvalidConcept(null, concept, request)) {
							concept.setState(RoleRequestState.APPROVED);
							conceptRoleRequestManager.save(concept);
						} else {
							// save request log, after concept was canceled
							this.save(request);
						}
					});

			// Execute request immediately
			return true;
		}

		// Embedded applicant must be present
		BaseDto applicant = DtoUtils.getEmbedded(request, IdmRoleRequest_.applicant, BaseDto.class);
		Map<String, Object> variables = new HashMap<>();
		IdmRoleRequestDto eventRequest = event.getContent();
		// Minimize size of DTO persisting to WF;
		trimRequest(eventRequest);
		event.setContent(eventRequest);
		variables.put(EntityEvent.EVENT_PROPERTY, event);
		variables.put("applicantInfo", request.getApplicantInfo());

		ProcessInstance processInstance = workflowProcessInstanceService.startProcess(wfDefinition,
				IdmIdentity.class.getSimpleName(), applicant.getId().toString(),
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

		List<? extends AbstractConceptRoleRequestDto> concepts = request.getConceptRoles();
		UUID applicantId = request.getApplicantInfo().getId();

		boolean identityNotSame = concepts.stream().anyMatch(concept -> !conceptRoleRequestManager.getServiceForConcept(concept).validOwnership(concept, applicantId));

		if (identityNotSame) {
			throw new RoleRequestException(CoreResultCode.ROLE_REQUEST_APPLICANTS_NOT_SAME,
					Map.of("request", request, "applicant", applicantId));
		}

		// Add changed identity-roles to event (prevent redundant search). We will used them for recalculations (ACM / provisioning).
		// Beware!! Sets have to be defined here, because without that will be not propagated to a sub event (role-request -> identity-role event)!
		requestEvent.getProperties().put(AbstractRoleAssignmentEvent.PROPERTY_ASSIGNED_NEW_ROLES, Sets.newHashSet());
		requestEvent.getProperties().put(AbstractRoleAssignmentEvent.PROPERTY_ASSIGNED_UPDATED_ROLES, Sets.newHashSet());
		requestEvent.getProperties().put(AbstractRoleAssignmentEvent.PROPERTY_ASSIGNED_REMOVED_ROLES, Sets.newHashSet());
		requestEvent.getProperties().put(IdmAccountDto.IDENTITY_ACCOUNT_FOR_DELAYED_ACM, Sets.newHashSet());
		requestEvent.getProperties().put(IdmAccountDto.ACCOUNT_FOR_ADDITIONAL_PROVISIONING, Sets.newHashSet());
		
		// Remove not approved concepts.
		List<AbstractConceptRoleRequestDto> approvedConcepts = concepts
				.stream()
				.filter(concept -> {
					// Only approved concepts can be executed.
					// Concepts in concept state will be executed too (for situation, when will be
					// approval event disabled).
					return RoleRequestState.APPROVED == concept.getState() || RoleRequestState.CONCEPT == concept.getState();
				})
				.collect(Collectors.toList());
		
		// Add concepts for business roles.
		final List<AbstractRoleAssignmentDto> allAssignedRoles = roleAssignmentManager.findAllByIdentity(applicantId);
		List<AbstractConceptRoleRequestDto> allApprovedConcepts = appendBusinessRoleConcepts(approvedConcepts, allAssignedRoles);

		// Create new identity role.
		allApprovedConcepts.stream()
				.filter(concept -> ADD == concept.getOperation())
				.filter(concept -> !conceptRoleRequestManager.getServiceForConcept(concept).cancelInvalidConcept(allAssignedRoles, concept, request))
				.forEach(concept -> {
					// assign new role
					conceptRoleRequestManager.getServiceForConcept(concept).createAssignedRole(allApprovedConcepts, concept, requestEvent);
					flushHibernateSession();
				});

		// Update identity role
		allApprovedConcepts.stream()
				.filter(concept -> UPDATE == concept.getOperation())
				.filter(concept -> !conceptRoleRequestManager.getServiceForConcept(concept).cancelInvalidConcept(allAssignedRoles, concept, request))
				.forEach(concept -> {
					conceptRoleRequestManager.getServiceForConcept(concept).updateAssignedRole(allApprovedConcepts, concept, requestEvent);
					flushHibernateSession();
				});
		
		// Delete identity sub roles at first (prevent to delete sub roles by referential integrity).
		allApprovedConcepts
			.stream()
			.filter(concept -> REMOVE == concept.getOperation())
			.filter(concept -> concept.getDirectConcept() != null)
			.filter(concept -> !conceptRoleRequestManager.getServiceForConcept(concept).cancelInvalidConcept(allAssignedRoles, concept, request))
			.forEach(concept -> {
				conceptRoleRequestManager.getServiceForConcept(concept).removeAssignedRole(concept, requestEvent);
				flushHibernateSession();
			});

		// Delete direct identity role
		allApprovedConcepts
			.stream()
			.filter(concept -> REMOVE == concept.getOperation())
			.filter(concept -> concept.getDirectConcept() == null)
			.filter(concept -> !conceptRoleRequestManager.getServiceForConcept(concept).cancelInvalidConcept(allAssignedRoles, concept, request))
			.forEach(concept -> {
				conceptRoleRequestManager.getServiceForConcept(concept).removeAssignedRole(concept, requestEvent);
				flushHibernateSession();
			});

		return this.save(request);
	}

	@Override
	public void validate(IdmRoleRequestDto request) {
		Assert.notNull(request, "Request is required.");
		List<? extends AbstractConceptRoleRequestDto> conceptRoles = request.getConceptRoles();

		conceptRoles.forEach(concept -> {
			List<InvalidFormAttributeDto> validationResults = conceptRoleRequestManager.getServiceForConcept(concept).validateFormAttributes(concept);
			if (validationResults != null && !validationResults.isEmpty()) {
				IdmRoleDto role = conceptRoleRequestManager.getServiceForConcept(concept).determineRoleFromConcept(concept);

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
		if (entity != null && entity.getApplicant() != null) {
			// this ensures backwards compatibility for requests which do not have applicantType set
			final String applicantType = entity.getApplicantType() == null ? IdmIdentityDto.class.getCanonicalName() : entity.getApplicantType();
			requestDto.setApplicantInfo(new ApplicantImplDto(entity.getApplicant(), applicantType));

			var applicantService = getApplicantService(entity.getApplicantType());
			if (applicantService != null) {
				BaseDto applicantDto = applicantService.get(entity.getApplicant());
				requestDto.getEmbedded().put(IdmRoleRequest_.applicant.getName(), applicantDto);
			}

		}
		// Set concepts to request DTO, but only if given filter has sets include-concepts attribute
		if (requestDto != null && filter != null && filter.isIncludeConcepts()) {
			requestDto.setConceptRoles(conceptRoleRequestManager.findAllByRoleRequest(requestDto.getId(), null));
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
	public ApplicantService getApplicantService(String applicantType) {
		return applicationContext
				.getBeansOfType(ApplicantService.class)
				.values()
				.stream()
				.filter(abstractEventableDtoService -> abstractEventableDtoService.getDtoClass().getCanonicalName().equals(applicantType))
				.findFirst()
				.orElse(null);
	}

	@Override
	public ApplicantService getApplicantServiceByAccountType(String accountType) {
		return applicationContext
				.getBeansOfType(ApplicantService.class)
				.values()
				.stream()
				.filter(abstractEventableDtoService -> accountType.equals(abstractEventableDtoService.getAccountType()))
				.findFirst()
				.orElse(null);
	}

	@Override
	@SuppressWarnings("unchecked")
	public String getApplicantLabel(ApplicantDto applicant) {
		final BaseDto applicantDto = lookupService.lookupDto(applicant.getApplicantType(), applicant.getId());
		final ReadDtoService<?, ?> dtoService = lookupService.getDtoService(applicantDto.getClass());

		// TODO replace with pattern matching in java 17
		if(dtoService instanceof NiceLabelable) {
			// This unchecked cast is safe because applicantDto will always be compatible with dtoService
			return ((NiceLabelable<BaseDto>) dtoService).getNiceLabel(applicantDto);
		}
		// fallback to identity applicant, which was default behavior prior to introduction of different applicant types
		return identityService.getNiceLabel(identityService.get(applicant.getId()));
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

		IdmRoleRequest idmRoleRequest = super.toEntity(dto, entity);
		// Backwards compatibility and preventing unwanted error if applicant field is not set, but applicantInfo is
		if (dto.getApplicantInfo() != null) {
			if (idmRoleRequest.getApplicant() == null) {
				idmRoleRequest.setApplicant(dto.getApplicantInfo().getId());
			}
			if (idmRoleRequest.getApplicantType() == null) {
				idmRoleRequest.setApplicantType(dto.getApplicantInfo().getApplicantType());
			}
		}
		return idmRoleRequest;
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
		conceptRoleRequestManager.findAllByRoleRequest(dto.getId(), null)
				.forEach(concept -> conceptRoleRequestManager.getServiceForConcept(concept).delete(concept));
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
	public IdmRoleRequestDto createRequest(IdmRequestIdentityRoleDto dto) {
		final IdmGeneralConceptRoleRequestService<AbstractRoleAssignmentDto, ? extends AbstractConceptRoleRequestDto, IdmBaseConceptRoleRequestFilter> serviceForConcept =
				conceptRoleRequestManager.getServiceForConcept(dto.getAssignmentType());
		ApplicantDto applicant = serviceForConcept.resolveApplicant(dto);
		return createRequest(dto.getRoleRequest(), applicant.getId(), applicant.getApplicantType(), dto.getRoleAssignmentUuid(), applicant.getValidFrom(), applicant.getValidTill());
	}

	@Override
	public IdmIdentityDto getApplicantAsIdentity(IdmRoleRequestDto request) {
		if (IdmIdentityDto.class.getCanonicalName().equals(request.getApplicantInfo().getApplicantType())) {
			return identityService.get(request.getApplicantInfo().getId());
		}
		return null;
	}

	@Override
	public List<IdmIdentityDto> getGuarantorsForApplicant(IdmRoleRequestDto request) {
		var applicantService = getApplicantService(request.getApplicantInfo().getApplicantType());

		if (applicantService != null) {
			return applicantService.findAllManagers(request.getApplicantInfo().getId());
		}

		return new ArrayList<>();
	}

	protected IdmRoleRequestDto createRequest(UUID id, UUID applicant, String applicantType, UUID assignmentOwner, LocalDate validFrom, LocalDate validTill, IdmRoleDto... roles) {
		Assert.notNull(applicant, "Applicant must be filled for create role request!");
		IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setId(id);
		roleRequest.setApplicantInfo(new ApplicantImplDto(applicant, applicantType));
		roleRequest.setRequestedByType(RoleRequestedByType.MANUALLY);
		roleRequest = this.save(roleRequest);
		if (roles != null) {
			for (IdmRoleDto role : roles) {
				createConcept(roleRequest, assignmentOwner, null, role.getId(), ADD, validFrom, validTill);
			}
		}
		return roleRequest;
	}

	@Override
	@Transactional
	public IdmRoleRequestDto createRequest(IdmIdentityContractDto contract, IdmRoleDto... roles) {
		Assert.notNull(contract, "Contract must be filled for create role request!");
		IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setApplicantInfo(new ApplicantImplDto(contract.getIdentity(), IdmIdentityDto.class.getCanonicalName()));
		roleRequest.setRequestedByType(RoleRequestedByType.MANUALLY);
		roleRequest = this.save(roleRequest);
		if (roles != null) {
			for (IdmRoleDto role : roles) {
				createConcept(roleRequest, contract, null, role.getId(), ADD);
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
		LocalDate validFrom = requestByIdentityDto.getValidFrom();
		LocalDate validTill = requestByIdentityDto.getValidTill();
		boolean copyRoleParameters = requestByIdentityDto.isCopyRoleParameters();
		final UUID roleRequestId = getRoleRequestId(requestByIdentityDto, identityContractId);

		final IdmRequestIdentityRoleFilter filter = new IdmRequestIdentityRoleFilter();
		filter.setIds(requestByIdentityDto.getIdentityRoles());

		roleAssignmentManager.find(filter, null, (assignment, service) -> processRoleToCopy(assignment, service, roleRequestId, validFrom, validTill, copyRoleParameters));

		return this.get(roleRequestId);
	}

	private UUID getRoleRequestId(IdmRoleRequestByIdentityDto requestByIdentityDto, UUID identityContractId) {
		if (requestByIdentityDto.getRoleRequest() == null) {
			IdmIdentityContractDto identityContractDto = identityContractService.get(identityContractId);
			IdmRoleRequestDto request = this.createManualRequest(identityContractDto.getIdentity());
			return request.getId();
		} else {
			return requestByIdentityDto.getRoleRequest();
		}
	}

	private void processRoleToCopy(AbstractRoleAssignmentDto assignment, IdmRoleAssignmentService<AbstractRoleAssignmentDto, BaseRoleAssignmentFilter> service, UUID roleRequestId, LocalDate validFrom, LocalDate validTill, boolean copyRoleParameters) {
		// Flush Hibernate in batch - performance improving TODO this is not the way to do it
		/*if (i % 20 == 0 && i > 0) {
			flushHibernateSession();
		}*/
		final AbstractConceptRoleRequestDto concept = conceptRoleRequestManager.getServiceForConcept(service.getRelatedConceptType()).createEmptyConcept();
		concept.setOwnerUuid(assignment.getEntity());
		concept.setRoleRequest(roleRequestId);
		concept.setRole(assignment.getRole());
		concept.setValidFrom(validFrom);
		concept.setValidTill(validTill);
		concept.setOperation(ADD);
		concept.addToLog(MessageFormat.format(
				"Concept was added from the copy roles operation (includes identity-role attributes [{0}]).",
				copyRoleParameters));

		IdmRoleDto roleDto = DtoUtils.getEmbedded(assignment, AbstractRoleAssignment_.role, IdmRoleDto.class);
		// Copy role parameters
		if (copyRoleParameters) {
			copyParameters(assignment, concept, roleDto);
		}

		conceptRoleRequestManager.save(concept);
	}

	private void copyParameters(AbstractRoleAssignmentDto assignment, AbstractConceptRoleRequestDto concept, IdmRoleDto roleDto) {
		// For copy must exist identity role attribute definition
		if (roleDto.getIdentityRoleAttributeDefinition() != null) {

			IdmFormInstanceDto formInstance = roleAssignmentManager.getServiceForAssignment(assignment).getRoleAttributeValues(assignment);

			List<IdmFormValueDto> values = formInstance.getValues();
			List<IdmFormValueDto> finalValues = new ArrayList<>(values);
			// Iterate over all values and find values that must be deep copied
			for (IdmFormValueDto value : values) {
				IdmFormAttributeDto attribute = DtoUtils.getEmbedded(value, AbstractFormValue_.formAttribute, IdmFormAttributeDto.class, null);
				if (attribute == null) {
					attribute = formAttributeService.get(value.getFormAttribute());
				}

				// Attachments are one of attribute with deep copy
				// TODO: confidential values are another, but identity role doesn't support them
				if (attribute.getPersistentType() == PersistentType.ATTACHMENT) {
					finalValues.remove(value);
					IdmFormValueDto valueCopy = copyAttachmentValue(value, attribute);
					finalValues.add(valueCopy);
				}
			}

			formInstance.setValues(finalValues);

			concept.setEavs(Lists.newArrayList(formInstance));
		}
	}

	private IdmFormValueDto copyAttachmentValue(IdmFormValueDto value, IdmFormAttributeDto attribute) {
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
		return valueCopy;
	}

	@Override
	public Set<ResolvedIncompatibleRoleDto> getIncompatibleRoles(IdmRoleRequestDto request, IdmBasePermission... permissions) {
		// Currently assigned roles
		var applicantService = getApplicantService(request.getApplicantInfo().getApplicantType());
		List<AbstractRoleAssignmentDto> identityRoles = new ArrayList<>();
		if (applicantService != null) {
			identityRoles = applicantService.getAllRolesForApplicant(request.getApplicantInfo().getId(), permissions);
		}
		// Roles from concepts
		List<AbstractConceptRoleRequestDto> concepts = conceptRoleRequestManager.findAllByRoleRequest(request.getId(), null, permissions);
		Set<UUID> removedRoleAssignments = new HashSet<>();

		// We don't want calculate incompatible roles for ended or disapproved concepts
		List<AbstractConceptRoleRequestDto> conceptsForCheck = concepts //
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
					boolean isDelete = concept.getOperation() == REMOVE;
					if (isDelete) {
						// removed role fixes the incompatibility
						removedRoleAssignments.add(concept.getRoleAssignmentUuid());
					}
					return !isDelete;
				})
				.forEach(concept -> roles.add(DtoUtils.getEmbedded(concept, AbstractConceptRoleRequest_.role)));

		identityRoles
				.stream()
				.filter(identityRole -> !removedRoleAssignments.contains(identityRole.getId()))
				.forEach(identityRole -> roles.add(DtoUtils.getEmbedded(identityRole, AbstractRoleAssignment_.role)));

		// We want to returns only incompatibilities caused by new added roles
	 	Set<ResolvedIncompatibleRoleDto> incompatibleRoles = incompatibleRoleService.resolveIncompatibleRoles(Lists.newArrayList(roles));
		return incompatibleRoles.stream() //
			.filter(incompatibleRole -> {
				return conceptsForCheck.stream() //
					.anyMatch(concept -> concept.getOperation() == ADD
						&& (concept.getRole().equals(incompatibleRole.getDirectRole().getId())
								|| concept.getRole().equals(incompatibleRole.getIncompatibleRole().getSuperior())
								|| concept.getRole().equals(incompatibleRole.getIncompatibleRole().getSub())
							)); //
			}).collect(Collectors.toSet());
	}

	@Override
	public List<AbstractConceptRoleRequestDto> markDuplicates(List<AbstractConceptRoleRequestDto> concepts, List<AbstractRoleAssignmentDto> allByIdentity) {
		Assert.notNull(concepts, "Role request concepts are required.");
		LOG.debug("Mark duplicates in concepts [{}]", concepts.size());
		// Check duplicates between concepts
		markDuplicatesInConcepts(concepts);

		LOG.debug("Mark duplicates in concepts and identity roles [{}]", concepts.size());
		// Check duplicates between concepts and identity roles
		// Split by role UUID
		Map<UUID, List<AbstractRoleAssignmentDto>> identityRolesByRole = allByIdentity
		.stream() //
		.collect( //
				Collectors.groupingBy( // Group identity roles by role
						AbstractRoleAssignmentDto::getRole) //
				); //
		LOG.debug("Fetched identity roles by role [{}]", identityRolesByRole.size());
		// TODO: create hashMap with used roles (simple cache)
		for (AbstractConceptRoleRequestDto concept : concepts) {
			LOG.debug("Check concept [{}]", concept.getId());
			// Only add or modification will be processed
			if (concept.getOperation() == REMOVE) {
				continue;
			}
			UUID roleId = concept.getRole();

			// Get all identity roles by role
			List<AbstractRoleAssignmentDto> identityRoles = identityRolesByRole.get(roleId);
			if (identityRoles == null) {
				continue;
			}

			// Create temporary identity role
			AbstractRoleAssignmentDto tempIdentityRole = conceptRoleRequestManager.getServiceForConcept(concept).createAssignmentFromConcept(concept);//createTempIdentityRole(concept);

			// Iterate over all identity roles, but only with same roles.
			for (AbstractRoleAssignmentDto identityRole : identityRoles) {
				LOG.debug("Check identity role [{}] against concept [{}]", identityRole.getId(), concept.getId());
				// We must get eavs by service. This is expensive operation. But we need it.
				IdmFormInstanceDto instanceDto = roleAssignmentManager.getServiceForAssignment(identityRole).getRoleAttributeValues(identityRole);
				if (instanceDto != null) {
					identityRole.setEavs(Lists.newArrayList(instanceDto));
				}
				AbstractRoleAssignmentDto duplicated = roleAssignmentManager.getServiceForAssignment(identityRole).getDuplicated(tempIdentityRole, identityRole, Boolean.FALSE);

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
		LOG.debug("Mark duplicates in concepts and identity roles [{}] - end", concepts.size());
		return concepts;
	}


	@Override
	public List<AbstractConceptRoleRequestDto> removeDuplicities(List<AbstractConceptRoleRequestDto> concepts, UUID identityId) {
		Assert.notNull(identityId, "Identity identifier is required.");
		Assert.notNull(concepts, "Role request concepts are required.");

		// TODO: check duplicity between concepts

		// List of uuid's identity roles that will be removed in this concept
		List<UUID> identityRolesForRemove = concepts //
				.stream() //
				.filter(concept -> concept.getOperation() == REMOVE)
				.map(AbstractConceptRoleRequestDto::getRoleAssignmentUuid) //
				.collect(Collectors.toList()); //

		// Filter identity roles for that exists concept for removing
		List<AbstractRoleAssignmentDto> identityRoles = roleAssignmentManager.findAllByIdentity(identityId).stream()
				.filter(identityRole -> !identityRolesForRemove.contains(identityRole.getId()))
				.collect(Collectors.toList());

		// Just mark duplicities
		concepts = this.markDuplicates(concepts, identityRoles);

		// Remove duplicities with subroles
		concepts = this.removeDuplicitiesSubRole(concepts, identityRoles);

		// Create final concepts and add non duplicities
		List<AbstractConceptRoleRequestDto> conceptRolesFinal = new ArrayList<>();
		for (AbstractConceptRoleRequestDto concept : concepts) {
			if (BooleanUtils.isNotTrue(concept.getDuplicate())) {
				conceptRolesFinal.add(concept);
			}
		}
		return conceptRolesFinal;
	}

	@Override
	@Transactional
	public IdmRoleRequestDto executeConceptsImmediate(UUID applicant, List<? extends AbstractConceptRoleRequestDto> concepts) {
		return this.executeConceptsImmediate(applicant, concepts, null);
	}

	@Override
	@Transactional
	public IdmRoleRequestDto executeConceptsImmediate(UUID applicant, List<? extends AbstractConceptRoleRequestDto> concepts, Map<String, Serializable> additionalProperties) {
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
		roleRequest.setApplicantInfo(new ApplicantImplDto(applicant, IdmIdentityDto.class.getCanonicalName()));
		roleRequest.setRequestedByType(RoleRequestedByType.AUTOMATICALLY);
		roleRequest = save(roleRequest);
		//
		for (AbstractConceptRoleRequestDto concept : concepts) {
			concept.setRoleRequest(roleRequest.getId());
			//
			final AbstractConceptRoleRequestDto save = conceptRoleRequestManager.getServiceForConcept(concept).save(concept);
			roleRequest.getConceptRoles().add(save);
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
		List<? extends AbstractConceptRoleRequestDto> concepts = roleRequest.getConceptRoles();
		ApplicantDto applicant = roleRequest.getApplicantInfo();
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
		for (AbstractConceptRoleRequestDto concept : concepts) {
			concept.setRoleRequest(roleRequest.getId());
			//
			conceptRoleRequestManager.save(concept);
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
		requestEvent.setSuperOwnerId(applicant.getId());
		//
		return startRequestInternal(requestEvent, parentEvent);
	}
	@Override
	public AbstractConceptRoleRequestDto createConcept(IdmRoleRequestDto roleRequest, IdmIdentityContractDto contract, UUID roleAssignmentUuid,
													   UUID roleId, ConceptRoleRequestOperation operation) {
		return createConcept(roleRequest,
				contract == null ? null : contract.getId(),
				roleAssignmentUuid,
				roleId,
				operation,
				contract == null ? null : contract.getValidFrom(),
				contract == null ? null : contract.getValidTill());
	}

	@Override
	public AbstractConceptRoleRequestDto createConceptWithoutValidity(IdmRoleRequestDto roleRequest, IdmIdentityContractDto contract, UUID roleAssignmentUuid,
													   UUID roleId, ConceptRoleRequestOperation operation) {
		return createConcept(roleRequest,
				contract == null ? null : contract.getId(),
				roleAssignmentUuid,
				roleId,
				operation,
				null,
				null);
	}

	public AbstractConceptRoleRequestDto createConcept(IdmRoleRequestDto roleRequest, UUID ownerUuid, UUID roleAssignmentUuid,
			UUID roleId, ConceptRoleRequestOperation operation, LocalDate validFrom, LocalDate validTill) {
		IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
		conceptRoleRequest.setRoleRequest(roleRequest.getId());
		if (ownerUuid != null) {
			conceptRoleRequest.setIdentityContract(ownerUuid);
			// We don't want filling validity for REMOVE operation
			if (REMOVE != operation) {
				conceptRoleRequest.setValidFrom(validFrom);
				conceptRoleRequest.setValidTill(validTill);
			}
		}
		conceptRoleRequest.setIdentityRole(roleAssignmentUuid);
		conceptRoleRequest.setRole(roleId);
		conceptRoleRequest.setOperation(operation);
		return conceptRoleRequestManager.getServiceForConcept(conceptRoleRequest).save(conceptRoleRequest);
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



	private IdmRoleRequestService getIdmRoleRequestService() {
		return applicationContext.getBean(IdmRoleRequestService.class);
	}

	/**
	 * Trim request and its role concepts. Remove embedded objects. It is important
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
	private <C extends AbstractConceptRoleRequestDto, A extends AbstractRoleAssignmentDto> List<AbstractConceptRoleRequestDto> removeDuplicitiesSubRole(List<AbstractConceptRoleRequestDto> concepts, List<A> allByIdentity) {
		List<AbstractConceptRoleRequestDto> conceptsToRemove = new ArrayList<>();
		for (AbstractConceptRoleRequestDto concept : concepts) {
			// Only add or modification
			if ((concept.getOperation() != ADD && concept.getOperation() != UPDATE) || concept.getDuplicate() != null) {
				continue;
			}

			// Find all sub roles for role.
			final List<IdmRoleCompositionDto> subRoles = roleCompositionService.findAllSubRoles(concept.getRole());

			for (IdmRoleCompositionDto subRoleComposition : subRoles) {
				IdmRoleDto subRole = DtoUtils.getEmbedded(subRoleComposition, IdmRoleComposition_.sub, IdmRoleDto.class, null);
				AbstractRoleAssignmentDto tempIdentityRoleSub = conceptRoleRequestManager.getServiceForConcept(concept).getRoleAssignmentDto(concept, subRole);
				for (AbstractRoleAssignmentDto identityRoleAssignment : allByIdentity) {
					// Get identity role eavs. This is also expensive operation.
					identityRoleAssignment.setEavs(Lists.newArrayList(roleAssignmentManager.getServiceForAssignment(identityRoleAssignment).getRoleAttributeValues(identityRoleAssignment)));
					AbstractRoleAssignmentDto duplicated = roleAssignmentManager.getServiceForAssignment(identityRoleAssignment).getDuplicated(tempIdentityRoleSub, identityRoleAssignment, Boolean.FALSE);
					// Duplication found, create request
					if (duplicated != null && identityRoleAssignment.getId().equals(duplicated.getId())) {
						AbstractConceptRoleRequestDto removeConcept = conceptRoleRequestManager.getServiceForConcept(concept).createConceptToRemoveIdentityRole(concept, identityRoleAssignment);
						removeConcept.addToLog(MessageFormat.format("Removed by duplicates with subrole id [{}]", identityRoleAssignment.getRoleComposition()));
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
		roleRequest.setApplicantInfo(new ApplicantImplDto(identityId, IdmIdentityDto.class.getCanonicalName()));
		roleRequest.setRequestedByType(RoleRequestedByType.MANUALLY);
		roleRequest.setExecuteImmediately(false);
		roleRequest = this.save(roleRequest);
		return roleRequest;
	}

	private void markDuplicatesInConcepts(List<AbstractConceptRoleRequestDto> concepts) {
		// Mark duplicates with concepts
		// Compare conceptOne with conceptTwo
		LOG.debug("Mark duplicates in concepts");
		for (AbstractConceptRoleRequestDto conceptOne : concepts) {
			LOG.debug("Compare concept [{}] with others", conceptOne.getId());
			// Only add or modification will be processed
			if (conceptOne.getOperation() == REMOVE) {
				conceptOne.setDuplicate(Boolean.FALSE); // REMOVE concept can't be duplicated
				continue;
			}
			// role and owner can be removed in the mean time
			if (conceptOne.getRole() == null || conceptOne.getOwnerUuid() == null) {
				conceptOne.setDuplicate(Boolean.FALSE);
				continue;
			}

			if (BooleanUtils.isTrue(conceptOne.getDuplicate())) {
				continue;
			}

			AbstractRoleAssignmentDto identityRoleOne = conceptRoleRequestManager.getServiceForConcept(conceptOne).createAssignmentFromConcept(conceptOne);

			// check duplicates for concept
			for (AbstractConceptRoleRequestDto conceptTwo : concepts) {
				LOG.debug("Compare concept [{}] with concept [{}]", conceptOne.getId(), conceptTwo.getId());
				// Only add or modification will be processed
				if (conceptTwo.getOperation() == REMOVE) {
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
				AbstractRoleAssignmentDto identityRoleTwo = conceptRoleRequestManager.getServiceForConcept(conceptTwo).createAssignmentFromConcept(conceptTwo);

				// Get duplicated must be quick, because the method doesn't made query to
				// database
				AbstractRoleAssignmentDto duplicated = roleAssignmentManager.getServiceForAssignment(identityRoleOne).getDuplicated(identityRoleOne, identityRoleTwo, Boolean.FALSE);
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
		LOG.debug("Mark duplicates in concepts finished");
	}
	

	
	/**
	 * Create and append business role concepts.
	 * 
	 * @param concepts original concepts
	 * @param identityRoles 
	 * @return original + business role concepts (~all)
	 * @since 10.6.0
	 */
	private List<AbstractConceptRoleRequestDto> appendBusinessRoleConcepts(
			List<AbstractConceptRoleRequestDto> concepts,
			List<AbstractRoleAssignmentDto> identityRoles) {
		List<AbstractConceptRoleRequestDto> results = Lists.newArrayList(concepts); // include original
		Map<UUID, List<AbstractRoleAssignmentDto>> currentlyAssignedSubRoles = identityRoles
			.stream()
			.filter(identityRole -> identityRole.getDirectRole() != null)
			.collect(Collectors.groupingBy(AbstractRoleAssignmentDto::getDirectRole));
		
		concepts
			.stream()
			.filter(concept -> ADD == concept.getOperation())
			.forEach(concept -> {
				// find and assign all sub roles as concepts
				roleCompositionService
					.findAllSubRoles(concept.getRole())
					.forEach(subRole -> {
						AbstractConceptRoleRequestDto conceptRoleRequest = concept.copy();
						// from assigned (~changed) sub role
						conceptRoleRequest.setRole(subRole.getSub());
						conceptRoleRequest.setRoleComposition(subRole.getId());
						// save and add to concepts to be processed
						results.add(conceptRoleRequestManager.getServiceForConcept(conceptRoleRequest).save(conceptRoleRequest));
					});
			});
		
		concepts
			.stream()
			.filter(concept -> UPDATE == concept.getOperation())
			.filter(concept -> currentlyAssignedSubRoles.containsKey(concept.getRoleAssignmentUuid()))
			.forEach(concept -> {
				// update sub roles by direct role
				UUID directRole = concept.getRoleAssignmentUuid();
				currentlyAssignedSubRoles
					.get(directRole)
					.forEach(subRole -> {
						AbstractConceptRoleRequestDto conceptRoleRequest = concept.copy();
						// from assigned (~changed) sub role
						conceptRoleRequest.setRole(subRole.getRole());
						conceptRoleRequest.setRoleAssignmentUuid(subRole.getId());
						conceptRoleRequest.setAutomaticRole(subRole.getAutomaticRole());
						conceptRoleRequest.setDirectRole(directRole);
						conceptRoleRequest.setRoleComposition(subRole.getRoleComposition());
						// save and add to concepts to be processed
						results.add(conceptRoleRequestManager.getServiceForConcept(concept).save(conceptRoleRequest));
					});
			});

		concepts
			.stream()
			.filter(concept -> REMOVE == concept.getOperation())
			.filter(concept -> currentlyAssignedSubRoles.containsKey(concept.getRoleAssignmentUuid()))
			.forEach(concept -> {
				// remove sub roles by direct role
				UUID directRole = concept.getRoleAssignmentUuid();
				currentlyAssignedSubRoles
					.get(directRole)
					.forEach(subRole -> {
						AbstractConceptRoleRequestDto conceptRoleRequest = concept.copy();
						// from assigned (~changed) sub role
						conceptRoleRequest.setRole(subRole.getRole());
						conceptRoleRequest.setRoleAssignmentUuid(subRole.getId());
						conceptRoleRequest.setAutomaticRole(subRole.getAutomaticRole());
						conceptRoleRequest.setDirectRole(directRole);
						conceptRoleRequest.setDirectConcept(concept.getId());
						conceptRoleRequest.setRoleComposition(subRole.getRoleComposition());
						// save and add to concepts to be processed
						results.add(conceptRoleRequestManager.getServiceForConcept(concept).save(conceptRoleRequest));
					});
			});

		return results;
	}
}
