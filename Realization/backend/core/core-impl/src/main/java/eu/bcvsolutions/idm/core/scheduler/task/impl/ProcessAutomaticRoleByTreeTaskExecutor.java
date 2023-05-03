package eu.bcvsolutions.idm.core.scheduler.task.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import eu.bcvsolutions.idm.core.model.entity.AbstractRoleAssignment_;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.ApplicantImplDto;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmContractPositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.ResultModel;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractPositionFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityContractFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.EntityStateManager;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmContractPositionService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleTreeNodeService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.domain.BaseFaceType;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.model.entity.IdmContractPosition_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleTreeNode_;
import eu.bcvsolutions.idm.core.model.entity.IdmRole_;
import eu.bcvsolutions.idm.core.model.event.RoleRequestEvent;
import eu.bcvsolutions.idm.core.model.event.RoleRequestEvent.RoleRequestEventType;
import eu.bcvsolutions.idm.core.scheduler.api.domain.IdmCheckConcurrentExecution;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableStatefulExecutor;

/**
 * Recount automatic role by tree structure for contract and other positions. 
 * Works for:
 * - Newly added automatic role,
 * - after tree structure is changed.
 * 
 * Can be executed repetitively to assign role to unprocessed identities, after process was stopped or interrupted (e.g. by server restart). 
 * 
 * @author Radek Tomiška                                                     
 * @since 10.4.0 
 */
@IdmCheckConcurrentExecution
@Component(ProcessAutomaticRoleByTreeTaskExecutor.TASK_NAME)
public class ProcessAutomaticRoleByTreeTaskExecutor extends AbstractSchedulableStatefulExecutor<IdmRoleTreeNodeDto> {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ProcessAutomaticRoleByTreeTaskExecutor.class);
	public static final String TASK_NAME = "core-process-automatic-role-tree-long-running-task";
	//
	@Autowired private IdmIdentityContractService identityContractService;
	@Autowired private IdmContractPositionService contractPositionService;
	@Autowired private IdmRoleTreeNodeService roleTreeNodeService;
	@Autowired private IdmIdentityRoleService identityRoleService;
	@Autowired private IdmRoleRequestService roleRequestService;
	@Autowired private IdmConceptRoleRequestService conceptRoleRequestService;
	@Autowired private PlatformTransactionManager platformTransactionManager;
	@Autowired private EntityStateManager entityStateManager;
	//
	private List<UUID> automaticRoles = null;
	private boolean continueOnException = true; // change default to true
	private Set<UUID> processedRoleRequests = new HashSet<>(); // all processed role requests or assigned roles - invalid role removal is solved, after all automatic roles are assigned (prevent drop and create target account)
	private boolean removeNotProcessedIdentityRoles = true; // true - invalid role removal is solved, after all automatic roles are assigned (prevent drop and create target account)
	
	@Override
	public String getName() {
		return TASK_NAME;
	}
	
	@Override
	public boolean supportsQueue() {
		return false;
	}
	
	@Override
    public boolean isRecoverable() {
    	return true;
    }
	
	@Override
	public boolean continueOnException() {
		return continueOnException;
	}
	
	@Override
	public void setContinueOnException(boolean continueOnException) {
		this.continueOnException = continueOnException;
	}
	
	/**
	 * Invalid role removal is solved, after all automatic roles are assigned (prevent drop and create target account).
	 * 
	 * @param removeNotProcessedIdentityRoles true - removed, false - not removed (newly assigned roles are persisted only)
	 */
	protected void setRemoveNotProcessedIdentityRoles(boolean removeNotProcessedIdentityRoles) {
		this.removeNotProcessedIdentityRoles = removeNotProcessedIdentityRoles;
	}
	
	/**
	 * Processed role requests for assign automatic roles.
	 * Available after LRT ends - if {@link #removeNotProcessedIdentityRoles} is set to false, then invalid assigned roles has to be removed by caller.
	 * 
	 * @return processed role requests
	 */
	protected Set<UUID> getProcessedRoleRequests() {
		return processedRoleRequests;
	}
	
	/**
	 * Processed role requests for assign automatic roles.
	 * Available after LRT ends - if {@link #removeNotProcessedIdentityRoles} is set to false, then invalid assigned roles has to be removed by caller.
	 * 
	 * @param processedRoleRequests role requests by automatic roles definition
	 */
	protected void setProcessedRoleRequests(Set<UUID> processedRoleRequests) {
		this.processedRoleRequests = processedRoleRequests;
	}

	@Override
	public void init(Map<String, Object> properties) {
		super.init(properties);
		//
		automaticRoles = getAutomaticRoles(properties);
		if (CollectionUtils.isEmpty(automaticRoles)) {
			throw new ResultCodeException(CoreResultCode.AUTOMATIC_ROLE_TREE_TASK_INVALID);
		}
	}
	
	/**
	 * Automatic role removal can be start, if previously LRT ended.
	 */
	@Override
	public void validate(IdmLongRunningTaskDto task) {
		super.validate(task);
		//
		if (CollectionUtils.isEmpty(automaticRoles)) {
			throw new ResultCodeException(CoreResultCode.AUTOMATIC_ROLE_TREE_TASK_INVALID);
		}
	}
	
	@Override
	public Page<IdmRoleTreeNodeDto> getItemsToProcess(Pageable pageable) {
		IdmRoleTreeNodeFilter filter = new IdmRoleTreeNodeFilter();
		//
		// we need to process all automatic roles => assigned role removal is on the end 
		List<IdmRoleTreeNodeDto> items = new ArrayList<>(automaticRoles.size());
		//
		int pageSize = 500; // prevent to exceed IN limit sql clause
		Page<UUID> idPage = new PageImpl<UUID>(automaticRoles, PageRequest.of(0, pageSize), automaticRoles.size());
		for (int page = 0; page < idPage.getTotalPages(); page++) {
			int end = (page + 1) * pageSize;
			if (end > automaticRoles.size()) {
				end = automaticRoles.size();
			}
			filter.setIds(automaticRoles.subList(page * pageSize, end));
			
			items.addAll(roleTreeNodeService
					.find(
						filter, 
						PageRequest.of(
								0, 
								Integer.MAX_VALUE, 
								Sort.by(String.format("%s.%s", IdmRoleTreeNode_.role.getName(), IdmRole_.code.getName()))
						)
					)
					.getContent()
			);
		}
		//
		return new PageImpl<>(items, PageRequest.of(0, automaticRoles.size()), automaticRoles.size());
	}
	
	@Override
	public Optional<OperationResult> processItem(IdmRoleTreeNodeDto automaticRole) {
		try {
			// process contracts
			Set<UUID> processedRoleRequestsByContract = processContracts(automaticRole);
			if (processedRoleRequestsByContract == null) {
				// task was canceled from the outside => not continue 
				return Optional.of(new OperationResult.Builder(OperationState.EXECUTED).build());
			}
			processedRoleRequests.addAll(processedRoleRequestsByContract);
			//
			// process contract positions
			Set<UUID> processedRoleRequestsByPositions = processPositions(automaticRole);
			if (processedRoleRequestsByPositions == null) {
				// task was canceled from the outside => not continue 
				return Optional.of(new OperationResult.Builder(OperationState.EXECUTED).build());
			}
			processedRoleRequests.addAll(processedRoleRequestsByPositions);
			//
			// delete automatic role is skipped flag for already processed automatic role
			entityStateManager.deleteStates(automaticRole, OperationState.BLOCKED, CoreResultCode.AUTOMATIC_ROLE_SKIPPED);
			//
			return Optional.of(new OperationResult.Builder(OperationState.EXECUTED).build());
		} catch (Exception ex) {
			// catch - just for sure, but all exception will be processed in new transaction to prevent whole automatic role fails.
			IdmRoleDto role = getLookupService().lookupEmbeddedDto(automaticRole, IdmRoleTreeNode_.role);
			// 
			return Optional.of(new OperationResult
					.Builder(OperationState.EXCEPTION)
					.setModel(new DefaultResultModel(
							CoreResultCode.AUTOMATIC_ROLE_ASSIGN_TASK_NOT_COMPLETE,
							ImmutableMap.of(
									"role", role.getCode(),
									"roleTreeNode", automaticRole.getId())))
					.setCause(ex)
					.build());
		}
	}
	
	@Override
	protected Boolean end(Boolean result, Exception ex) {
		if (ex == null && BooleanUtils.isTrue(result) && removeNotProcessedIdentityRoles) {
			try {
				Set<UUID> processedIdentityRoles = new HashSet<>(processedRoleRequests);
				processedRoleRequests.forEach(requestId -> {
					processedIdentityRoles.addAll(
							conceptRoleRequestService
								.findAllByRoleRequest(requestId)
								.stream()
								.map(concept -> {
									UUID identityRole = concept.getIdentityRole();
									if (identityRole == null) {
										LOG.error("Concept [{}] is not executed [{}], identity role identifier is empty.", 
												concept.getId(), concept.getState());
										throw new ResultCodeException(
												CoreResultCode.AUTOMATIC_ROLE_ASSIGN_NOT_COMPLETE, 
												ImmutableMap.of(
														"role", String.valueOf(concept.getRole()),
														"roleTreeNode", String.valueOf(concept.getAutomaticRole())
												)
										);
									}
									return identityRole;
								})
								.collect(Collectors.toSet()
							)
					);
				});
				//
				// remove previously assigned role, which was not processed by any automatic role
				automaticRoles.forEach(automaticRole -> {
					// new transaction is wrapped inside
					processIdentityRoles(processedIdentityRoles, automaticRole);
				});
			} catch (Exception exception) {
				// propagate exception to super end
				ex = exception;
			}
		}
		//
		return super.end(result, ex);
	}
	
	private Set<UUID> processContracts(IdmRoleTreeNodeDto automaticRole) {
		Set<UUID> processedRoleRequests = new HashSet<>();
		//
		IdmIdentityContractFilter filter = new IdmIdentityContractFilter();
		filter.setWorkPosition(automaticRole.getTreeNode());
		filter.setRecursionType(automaticRole.getRecursionType());
		filter.setValidNowOrInFuture(Boolean.TRUE);
		//
		Pageable pageable = PageRequest.of(
				0, 
				getPageSize(),
				new Sort(Direction.ASC, BaseEntity.PROPERTY_ID)
		);
		//
		boolean canContinue = true;
		//
		do {
			Page<IdmIdentityContractDto> contracts = identityContractService.find(filter, pageable);
			//
			for (Iterator<IdmIdentityContractDto> i = contracts.iterator(); i.hasNext() && canContinue;) {
				IdmIdentityContractDto contract = i.next();
				UUID contractId = contract.getId();
				//
				if (!requireNewTransaction()) {
					processedRoleRequests.addAll(processContract(contract, automaticRole));
				} else {
					TransactionTemplate template = new TransactionTemplate(platformTransactionManager);
					template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
					//
					try {
						template.execute(new TransactionCallbackWithoutResult() {
							
							@Override
							public void doInTransactionWithoutResult(TransactionStatus status) {
								processedRoleRequests.addAll(processContract(contract, automaticRole));
							}
						});
					} catch (UnexpectedRollbackException ex ) {
						// Just log for sure ... exception solved in new transaction, but this lower transaction is marked as roll-back.
						LOG.debug("Statefull process [{}] processed contract [{}] failed",
								getClass().getSimpleName(), contractId, ex);
					}	
				}
				//
				if (!this.updateState()) {
					LOG.debug("Task [{}] was canceled.", getLongRunningTaskId());
					// return null => not continue with role removal
					return null;
				}
			}			
			canContinue &= contracts.hasNext();			
			pageable = contracts.nextPageable();
			//
		} while (canContinue);
		//
		return processedRoleRequests;
	}
	
	private Set<UUID> processPositions(IdmRoleTreeNodeDto automaticRole) {
		Set<UUID> processedRoles = new HashSet<>();
		//
		IdmContractPositionFilter filter = new IdmContractPositionFilter();
		filter.setWorkPosition(automaticRole.getTreeNode());
		filter.setRecursionType(automaticRole.getRecursionType());
		filter.setValidNowOrInFuture(Boolean.TRUE);
		Pageable pageable = PageRequest.of(
				0, 
				getPageSize(),
				new Sort(Direction.ASC, BaseEntity.PROPERTY_ID)
		);
		boolean canContinue = true;
		//
		do {
			Page<IdmContractPositionDto> positions = contractPositionService.find(filter, pageable);
			//
			for (Iterator<IdmContractPositionDto> i = positions.iterator(); i.hasNext() && canContinue;) {
				IdmContractPositionDto position = i.next();
				UUID contractId = position.getId();
				//
				if (!requireNewTransaction()) {
					processedRoles.addAll(processPosition(position, automaticRole));
				} else {
					TransactionTemplate template = new TransactionTemplate(platformTransactionManager);
					template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
					//
					try {
						template.execute(new TransactionCallbackWithoutResult() {
							
							@Override
							public void doInTransactionWithoutResult(TransactionStatus status) {
								processedRoles.addAll(processPosition(position, automaticRole));
							}
						});
					} catch (UnexpectedRollbackException ex ) {
						// Just log for sure ... exception solved in new transaction, but this lower transaction is marked as roll-back.
						LOG.debug("Statefull process [{}] processed contract [{}] failed",
								getClass().getSimpleName(), contractId, ex);
					}	
				}
				//
				if (!this.updateState()) {
					LOG.debug("Task [{}] was canceled.", getLongRunningTaskId());
					// return null => not continue with role removal
					return null;
				}
			}			
			canContinue &= positions.hasNext();			
			pageable = positions.nextPageable();
			//
		} while (canContinue);
		//
		return processedRoles;
	}
	
	protected void processIdentityRoles(Set<UUID> processedIdentityRoles, UUID automaticRole) {
		Assert.notNull(automaticRole, "Automatic role is required.");
		//
		// remove old assigned roles by automatic role
		Pageable pageable = PageRequest.of(
				0, 
				getPageSize(),
				new Sort(Direction.ASC, BaseEntity.PROPERTY_ID)
		);
		boolean canContinue = true;
		// filter - if role was assigned by contract or contract position doesn't matter => automatic role is the same
		IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
		filter.setAutomaticRoleId(automaticRole);
		//
		do {
			Page<IdmIdentityRoleDto> identityRoles = identityRoleService.find(filter, pageable);
			//
			for (Iterator<IdmIdentityRoleDto> i = identityRoles.iterator(); i.hasNext() && canContinue;) {
				IdmIdentityRoleDto identityRole = i.next();
				UUID identityRoleId = identityRole.getId();
				//
				if (!requireNewTransaction()) {
					removeUnprocessedIdentityRole(processedIdentityRoles, identityRole, automaticRole);
				} else {
					TransactionTemplate template = new TransactionTemplate(platformTransactionManager);
					template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
					//
					try {
						template.execute(new TransactionCallbackWithoutResult() {
							
							@Override
							public void doInTransactionWithoutResult(TransactionStatus status) {
								removeUnprocessedIdentityRole(processedIdentityRoles, identityRole, automaticRole);
							}
						});
					} catch (UnexpectedRollbackException ex ) {
						// Just log for sure ... exception solved in new transaction, but this lower transaction is marked as roll-back.
						LOG.error("Statefull process [{}] remove identity role [{}] failed",
								getClass().getSimpleName(), identityRoleId, ex);
					}	
				}
				//
				canContinue &= this.updateState();
			}
			canContinue &= identityRoles.hasNext();			
			pageable = identityRoles.nextPageable();
			//
		} while (canContinue);
	}
	
	/**
	 * Assign automatic role for given contract. Already assigned automatic role is checked.
	 * 
	 * @param contract
	 * @param automaticRole
	 * @return created assigned roles
	 */
	private Set<UUID> processContract(
			IdmIdentityContractDto contract, 
			IdmRoleTreeNodeDto automaticRole) {
		UUID contractId = contract.getId();
		Set<UUID> processedRoleRequests = new HashSet<>();
		UUID automaticRoleId = automaticRole.getId();
		IdmIdentityDto identity = getLookupService().lookupEmbeddedDto(contract, IdmIdentityContract_.identity);
		IdmRoleDto role = getLookupService().lookupEmbeddedDto(automaticRole, IdmRoleTreeNode_.role);
		//
		try {
			List<IdmIdentityRoleDto> allByContract = identityRoleService.findAllByContract(contractId);
			//
			// skip already assigned automatic roles
			for (IdmIdentityRoleDto roleByContract : allByContract) {
				if (ObjectUtils.equals(roleByContract.getAutomaticRole(), automaticRoleId)) {
					processedRoleRequests.add(roleByContract.getId());
					ResultModel resultModel = new DefaultResultModel(
							CoreResultCode.AUTOMATIC_ROLE_ALREADY_ASSIGNED,
							ImmutableMap.of(
									"role", role.getCode(),
									"roleTreeNode", automaticRoleId,
									"identity", identity.getUsername()));
					saveItemResult(roleByContract, OperationState.NOT_EXECUTED, resultModel, null);
					return processedRoleRequests;
				}
			}
			//
			// assign automatic role by tree node by role request
			IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
			conceptRoleRequest.setIdentityContract(contractId);
			conceptRoleRequest.setValidFrom(contract.getValidFrom());
			conceptRoleRequest.setValidTill(contract.getValidTill());
			conceptRoleRequest.setRole(automaticRole.getRole());
			conceptRoleRequest.setAutomaticRole(automaticRoleId);
			conceptRoleRequest.setOperation(ConceptRoleRequestOperation.ADD);
			//
			IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
			roleRequest.setConceptRoles(Lists.newArrayList(conceptRoleRequest));
			roleRequest.setApplicantInfo(new ApplicantImplDto(contract.getIdentity(), IdmIdentityDto.class.getCanonicalName()));
			RoleRequestEvent roleRequestEvent = new RoleRequestEvent(RoleRequestEventType.EXCECUTE, roleRequest);
			roleRequest = roleRequestService.startConcepts(roleRequestEvent, null);
			//
			// load role concepts and add created role to processed
			if (roleRequest != null) {
				processedRoleRequests.add(roleRequest.getId());
			}
			// Log successfully assigned role
			ResultModel resultModel = new DefaultResultModel(
					CoreResultCode.AUTOMATIC_ROLE_ASSIGN_TASK_ROLE_ASSIGNED,
					ImmutableMap.of(
							"role", role.getCode(),
							"roleTreeNode", automaticRoleId,
							"identity", identity.getUsername()));
			saveItemResult(contract, OperationState.EXECUTED, resultModel, null);
		} catch(Exception ex) {
			LOG.error("Adding role [{}] by automatic role [{}] for identity [{}] failed",
					role.getCode(), automaticRoleId, identity.getUsername(), ex);
			//
			ResultModel resultModel = new DefaultResultModel(
							CoreResultCode.AUTOMATIC_ROLE_ASSIGN_TASK_NOT_COMPLETE,
							ImmutableMap.of(
									"role", role.getCode(),
									"roleTreeNode", automaticRoleId,
									"identity", identity.getUsername())
			);
			saveItemResult(contract, OperationState.EXCEPTION, resultModel, ex);
		}
		//
		return processedRoleRequests;
	}
	
	/**
	 * Assign automatic role for given other. Already assigned automatic role is checked.
	 * 
	 * @param contract
	 * @param automaticRole
	 * @return created assigned roles
	 */
	private Set<UUID> processPosition(
			IdmContractPositionDto position, 
			IdmRoleTreeNodeDto automaticRole) {
		UUID positionId = position.getId();
		IdmIdentityContractDto contract = getLookupService().lookupEmbeddedDto(position, IdmContractPosition_.identityContract);
		UUID contractId = contract.getId();
		Set<UUID> processedRoleRequests = new HashSet<>();
		UUID automaticRoleId = automaticRole.getId();
		IdmIdentityDto identity = getLookupService().lookupEmbeddedDto(contract, IdmIdentityContract_.identity);
		IdmRoleDto role = getLookupService().lookupEmbeddedDto(automaticRole, IdmRoleTreeNode_.role);
		//
		try {
			List<IdmIdentityRoleDto> allByPosition = identityRoleService.findAllByContractPosition(positionId);
			//
			// skip already assigned automatic roles
			for (IdmIdentityRoleDto roleByContract : allByPosition) {
				if (ObjectUtils.equals(roleByContract.getAutomaticRole(), automaticRoleId)) {
					processedRoleRequests.add(roleByContract.getId());
					ResultModel resultModel = new DefaultResultModel(
							CoreResultCode.AUTOMATIC_ROLE_ALREADY_ASSIGNED,
							ImmutableMap.of(
									"role", role.getCode(),
									"roleTreeNode", automaticRoleId,
									"identity", identity.getUsername()));
					saveItemResult(roleByContract, OperationState.NOT_EXECUTED, resultModel, null);
					return processedRoleRequests;
				}
			}
			//
			// automatic role by tree node is added directly trough identity role
			IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
			conceptRoleRequest.setIdentityContract(contractId);
			conceptRoleRequest.setContractPosition(positionId);
			conceptRoleRequest.setValidFrom(contract.getValidFrom());
			conceptRoleRequest.setValidTill(contract.getValidTill());
			conceptRoleRequest.setRole(automaticRole.getRole());
			conceptRoleRequest.setAutomaticRole(automaticRoleId);
			conceptRoleRequest.setOperation(ConceptRoleRequestOperation.ADD);
			//
			IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
			roleRequest.setConceptRoles(Lists.newArrayList(conceptRoleRequest));
			roleRequest.setApplicantInfo(new ApplicantImplDto(contract.getIdentity(), IdmIdentityDto.class.getCanonicalName()));
			RoleRequestEvent roleRequestEvent = new RoleRequestEvent(RoleRequestEventType.EXCECUTE, roleRequest);
			roleRequest = roleRequestService.startConcepts(roleRequestEvent, null);
			//
			// load role concepts and add created role to processed
			if (roleRequest != null) {
				processedRoleRequests.add(roleRequest.getId());
			}
			// Log successfully assigned role
			ResultModel resultModel = new DefaultResultModel(
					CoreResultCode.AUTOMATIC_ROLE_ASSIGN_TASK_ROLE_ASSIGNED,
					ImmutableMap.of(
							"role", role.getCode(),
							"roleTreeNode", automaticRoleId,
							"identity", identity.getUsername()));
			saveItemResult(position, OperationState.EXECUTED, resultModel, null);
		} catch(Exception ex) {
			LOG.error("Adding role [{}] by automatic role [{}] for identity [{}] failed",
					role.getCode(), automaticRoleId, identity.getUsername(), ex);
			//
			ResultModel resultModel = new DefaultResultModel(
							CoreResultCode.AUTOMATIC_ROLE_ASSIGN_TASK_NOT_COMPLETE,
							ImmutableMap.of(
									"role", role.getCode(),
									"roleTreeNode", automaticRoleId,
									"identity", identity.getUsername())
			);
			saveItemResult(position, OperationState.EXCEPTION, resultModel, ex);
		}
		//
		return processedRoleRequests;
	}
	
	/**
	 * Check currently assigned role is processed by current automatic role definition.
	 * If not => assigned role will be removed by a role request.
	 *
	 * @param processedIdentityRoles assigned roles processed by automatic role
	 * @param identityRole           assigned role
	 * @param automaticRoleId        automatic role id (just for logging)
	 */
	private void removeUnprocessedIdentityRole(
			Set<UUID> processedIdentityRoles,
			IdmIdentityRoleDto identityRole,
			UUID automaticRoleId) {
		UUID identityRoleId = identityRole.getId();
		IdmIdentityContractDto identityContract = getLookupService().lookupEmbeddedDto(identityRole, IdmIdentityRole_.identityContract);
		IdmIdentityDto identity = getLookupService().lookupEmbeddedDto(identityContract, IdmIdentityContract_.identity);
		IdmRoleDto role = getLookupService().lookupEmbeddedDto(identityRole, AbstractRoleAssignment_.role);
		//
		if (!processedIdentityRoles.contains(identityRoleId) && !longRunningTaskService.get(this.getLongRunningTaskId()).isDryRun()) {
			// remove role by request
			try {
				IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
				conceptRoleRequest.setIdentityRole(identityRole.getId());
				conceptRoleRequest.setRole(identityRole.getRole());
				conceptRoleRequest.setOperation(ConceptRoleRequestOperation.REMOVE);
				conceptRoleRequest.setIdentityContract(identityRole.getIdentityContract());
				//
				IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
    			roleRequest.setConceptRoles(Lists.newArrayList(conceptRoleRequest));
				roleRequest.setApplicantInfo(new ApplicantImplDto(identity.getId(), IdmIdentityDto.class.getCanonicalName()));
    			roleRequest = roleRequestService.startConcepts(new RoleRequestEvent(RoleRequestEventType.EXCECUTE, roleRequest), null);
				// log successfully removed identity role
				ResultModel resultModel = new DefaultResultModel(
						CoreResultCode.AUTOMATIC_ROLE_ASSIGN_TASK_ROLE_REMOVED,
						ImmutableMap.of(
								"role", role.getCode(),
								"roleTreeNode", automaticRoleId,
								"identity", identity.getUsername()));
				saveItemResult(identityRole, OperationState.EXECUTED, resultModel, null);
			} catch(Exception ex) {
				LOG.error("Remove role [{}] by automatic role [{}] failed", role.getCode(), automaticRoleId, ex);
				//
				ResultModel resultModel = new DefaultResultModel(
						CoreResultCode.AUTOMATIC_ROLE_REMOVE_TASK_NOT_COMPLETE,
						ImmutableMap.of(
								"role", role.getCode(),
								"roleTreeNode", automaticRoleId,
								"identity", identity.getUsername())
				);
				saveItemResult(identityRole, OperationState.EXCEPTION, resultModel, ex);
			}
		}
	}
	
	@Override
	public Map<String, Object> getProperties() {
		Map<String, Object> properties =  super.getProperties();
		properties.put(AbstractAutomaticRoleTaskExecutor.PARAMETER_ROLE_TREE_NODE, automaticRoles);
		return properties;
	}
	
	@Override
	public List<String> getPropertyNames() {
		List<String> propertyNames = super.getPropertyNames();
		propertyNames.add(AbstractAutomaticRoleTaskExecutor.PARAMETER_ROLE_TREE_NODE);
		return propertyNames;
	}
	
	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		IdmFormAttributeDto automaticRoleAttribute = new IdmFormAttributeDto(
				AbstractAutomaticRoleTaskExecutor.PARAMETER_ROLE_TREE_NODE,
				AbstractAutomaticRoleTaskExecutor.PARAMETER_ROLE_TREE_NODE, 
				PersistentType.UUID,
				BaseFaceType.AUTOMATIC_ROLE_TREE_SELECT);
		automaticRoleAttribute.setRequired(true);
		automaticRoleAttribute.setMultiple(true);
		//
		return Lists.newArrayList(automaticRoleAttribute);
	}
	
	@Override
	public IdmFormInstanceDto getFormInstance(ConfigurationMap properties) {
		IdmFormInstanceDto formInstance = new IdmFormInstanceDto(getFormDefinition());
		//
		List<UUID> automaticRoles = getAutomaticRoles(properties);
		if (CollectionUtils.isEmpty(automaticRoles)) {
			return null;
		}
		automaticRoles.forEach(automaticRoleId -> {
			IdmFormValueDto value = new IdmFormValueDto(
					formInstance.getMappedAttributeByCode(AbstractAutomaticRoleTaskExecutor.PARAMETER_ROLE_TREE_NODE)
			);
			value.setUuidValue(automaticRoleId);
			IdmRoleTreeNodeDto automaticRole = roleTreeNodeService.get(automaticRoleId);
			if (automaticRole == null) {
				// id only => prevent to load on UI
				// TODO: load from audit => #978 required
				automaticRole = new IdmRoleTreeNodeDto(automaticRoleId);
			}
			value.getEmbedded().put(IdmFormValueDto.PROPERTY_UUID_VALUE, automaticRole);
			formInstance.getValues().add(value);
		});
		//
		return formInstance;
	}
	
	public void setAutomaticRoles(List<UUID> automaticRoles) {
		this.automaticRoles = automaticRoles;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected List<UUID> getAutomaticRoles(Map<String, Object> properties) {
		// support list (List) + multivalue LRT (from LRT manual configuration)
		Object propertyValue = properties.get(AbstractAutomaticRoleTaskExecutor.PARAMETER_ROLE_TREE_NODE);
		if (propertyValue == null) {
			return new ArrayList<>();
		}
		if (propertyValue instanceof List) { // TODO: resolve lists directly in parameter converter
			return (List) propertyValue;
		}
		//
		String rawUuids = getParameterConverter().toString(properties, AbstractAutomaticRoleTaskExecutor.PARAMETER_ROLE_TREE_NODE);
		if (StringUtils.isEmpty(rawUuids)) {
			return null;
		}
		return Arrays
				.stream(rawUuids.split(","))
				.map(DtoUtils::toUuid)
				.collect(Collectors.toList());
	}
	
	private void saveItemResult(AbstractDto dto, OperationState state, ResultModel resultModel, Exception ex) {
		getItemService().createLogItem(
				dto, 
				new OperationResult.Builder(state).setModel(resultModel).setCause(ex).build(), 
				this.getLongRunningTaskId()
		);
		//
		if (!continueOnException() && ex != null) {
			ResultCodeException resultCodeException;
			if (ex instanceof ResultCodeException) {
				resultCodeException = (ResultCodeException) ex;
			} else {
				resultCodeException = new ResultCodeException(
						CoreResultCode.LONG_RUNNING_TASK_ITEM_FAILED, 
						ImmutableMap.of(
								"referencedEntityId", dto.getId()),
						ex);	
			}
			LOG.error("[" + resultCodeException.getId() + "] ", resultCodeException);
			//
			throw resultCodeException;
		}
	}
}
