package eu.bcvsolutions.idm.core.scheduler.service.impl;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.lang3.StringUtils;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.entity.OperationResult_;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.AbstractEventableDtoService;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.utils.AutowireHelper;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmScheduledTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.filter.IdmLongRunningTaskFilter;
import eu.bcvsolutions.idm.core.scheduler.api.dto.filter.IdmProcessedTaskItemFilter;
import eu.bcvsolutions.idm.core.scheduler.api.service.IdmLongRunningTaskService;
import eu.bcvsolutions.idm.core.scheduler.api.service.IdmProcessedTaskItemService;
import eu.bcvsolutions.idm.core.scheduler.api.service.SchedulableTaskExecutor;
import eu.bcvsolutions.idm.core.scheduler.entity.IdmLongRunningTask;
import eu.bcvsolutions.idm.core.scheduler.entity.IdmLongRunningTask_;
import eu.bcvsolutions.idm.core.scheduler.repository.IdmLongRunningTaskRepository;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;

/**
 * Persists long running tasks.
 * 
 * Look out: 
 * @Transactional(propagation = Propagation.REQUIRES_NEW) is needed. 
 * LRT has to be persist and read separately from outer transaction => we want to see LRT progress all time. And result with exception.
 * 
 * @author Radek Tomiška
 *
 */
public class DefaultIdmLongRunningTaskService
	extends AbstractEventableDtoService<IdmLongRunningTaskDto, IdmLongRunningTask, IdmLongRunningTaskFilter>
	implements IdmLongRunningTaskService {
	
	private final IdmLongRunningTaskRepository repository;
	//
	@Autowired private IdmProcessedTaskItemService itemService;
	
	@Autowired
	public DefaultIdmLongRunningTaskService(IdmLongRunningTaskRepository repository, EntityEventManager entityEventManager) {
		super(repository, entityEventManager);
		//
		this.repository = repository;
	}

	@Override
	public AuthorizableType getAuthorizableType() {
		return new AuthorizableType(CoreGroupPermission.SCHEDULER, getEntityClass());
	}
	
	@Override
	public boolean supportsToDtoWithFilter() {
		return true;
	}
	
	@Override
	protected IdmLongRunningTaskDto applyContext(IdmLongRunningTaskDto dto, IdmLongRunningTaskFilter context, BasePermission... permission) {
		dto = super.applyContext(dto, context, permission);
		//
		if (context != null && context.isIncludeItemCounts()) {
			dto = setFailedAndSuccessItems(dto);
		}
		return dto;
	}
	
	@Override
	protected List<Predicate> toPredicates(Root<IdmLongRunningTask> root, CriteriaQuery<?> query,
			CriteriaBuilder builder, IdmLongRunningTaskFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
		//
		// quick - "fulltext"
		String text = filter.getText();
		if (StringUtils.isNotEmpty(text)) {
			text = text.toLowerCase();
			predicates.add(builder.or(
					builder.like(builder.lower(root.get(IdmLongRunningTask_.taskType)), "%" + text + "%"),
					builder.like(builder.lower(root.get(IdmLongRunningTask_.taskDescription)), "%" + text + "%")					
					));
		}
		String instanceId = filter.getInstanceId();
		if (StringUtils.isNotEmpty(instanceId)) {
			predicates.add(builder.equal(root.get(IdmLongRunningTask_.instanceId), instanceId));
		}
		String taskType = filter.getTaskType();
		if (StringUtils.isNotEmpty(taskType)) {
			predicates.add(builder.equal(root.get(IdmLongRunningTask_.taskType), taskType));
		}
		ZonedDateTime from = filter.getFrom();
		if (from != null) {
			predicates.add(builder.greaterThanOrEqualTo(root.get(IdmLongRunningTask_.created), from));
		}
		ZonedDateTime till = filter.getTill();
		if (till != null) {
			predicates.add(builder.lessThanOrEqualTo(root.get(IdmLongRunningTask_.created), till));
		}
		OperationState operationState = filter.getOperationState();
		if (operationState != null) {
			predicates.add(builder.equal(root.get(IdmLongRunningTask_.result).get(OperationResult_.state), operationState));
		}
		Boolean running = filter.getRunning();
		if (running != null) {
			predicates.add(builder.equal(root.get(IdmLongRunningTask_.running), running));
		}
		Boolean stateful = filter.getStateful();
		if (stateful != null) {
			predicates.add(builder.equal(root.get(IdmLongRunningTask_.stateful), stateful));
		}
		UUID creatorId = filter.getCreatorId();
		if (creatorId != null) {
			predicates.add(builder.equal(root.get(IdmLongRunningTask_.creatorId), creatorId));
		}
		//
		return predicates;
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<IdmLongRunningTaskDto> findAllByInstance(String instanceId, OperationState state) {
		return toDtos(repository.findAllByInstanceIdAndResult_StateOrderByCreatedAsc(instanceId, state), false);
	}
	
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void updateState(UUID id, Long count, Long counter) {
		repository.updateState(id, count, counter, ZonedDateTime.now());
	}
	
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public IdmLongRunningTaskDto save(IdmLongRunningTaskDto dto, BasePermission... permission) {
		Assert.notNull(dto, "DTO is required.");
		// description is from some place dynamically generated, we must check the description length and cutoff more characters
		// this is defensive behavior, descriptions longer than 2000 characters will 
		if (StringUtils.length(dto.getTaskDescription()) > DefaultFieldLengths.DESCRIPTION) {
			dto.setTaskDescription(StringUtils.abbreviate(dto.getTaskDescription(), DefaultFieldLengths.DESCRIPTION));
		}
		return super.save(dto, permission);
	}
	
	@Override
	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	public IdmLongRunningTaskDto get(Serializable id, BasePermission... permission) {
		return super.get(id, permission);
	}
	
	@Override
	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	public IdmLongRunningTaskDto get(Serializable id, IdmLongRunningTaskFilter context, BasePermission... permission) {
		return super.get(id, context, permission);
	}
	
	@Override
	@Transactional
	public IdmLongRunningTaskDto create(IdmScheduledTaskDto scheduledTask, SchedulableTaskExecutor<?> taskExecutor, String instanceId) {
		IdmLongRunningTaskDto task = new IdmLongRunningTaskDto();
		//
		task.setTaskType(AutowireHelper.getTargetType(taskExecutor));
		task.setTaskDescription(taskExecutor.getDescription());	
		task.setInstanceId(instanceId);
		task.setResult(new OperationResult.Builder(OperationState.CREATED).build());
		task.setScheduledTask(scheduledTask.getId());
		task.setRecoverable(taskExecutor.isRecoverable());
		//
		return this.save(task);
	}
	
	@Override
	@Transactional
	public void deleteInternal(IdmLongRunningTaskDto dto) {
		Assert.notNull(dto, "DTO is required.");
		Assert.notNull(dto.getId(), "DTO identifier is required.");
		//
		// running task cannot be deleted
		if (dto.isRunning()) {
			throw new ResultCodeException(CoreResultCode.LONG_RUNNING_TASK_DELETE_FAILED_IS_RUNNING,
					ImmutableMap.of("taskId", dto.getId()));
		}
		//
		itemService.deleteAllByLongRunningTask(get(dto.getId()));
		//
		super.deleteInternal(dto);
	}
	
	/**
	 * Method defensively set up failed and success items count into dto.
	 * When is given dto null, return null.
	 *
	 * @param longRunningTaskDto
	 * @return
	 */
	private IdmLongRunningTaskDto setFailedAndSuccessItems(IdmLongRunningTaskDto longRunningTaskDto) {
		if (longRunningTaskDto == null) {
			return null;
		}
		//
		IdmProcessedTaskItemFilter filter = new IdmProcessedTaskItemFilter();
		filter.setLongRunningTaskId(longRunningTaskDto.getId());
		long totalElements = itemService.findIds(filter, null).getTotalElements();
		//
		filter.setOperationState(OperationState.EXECUTED);
		longRunningTaskDto.setSuccessItemCount(itemService.findIds(filter, null).getTotalElements());
		// TODO: multi state filter
		filter.setOperationState(OperationState.CREATED);
		longRunningTaskDto.setSuccessItemCount(longRunningTaskDto.getSuccessItemCount() + itemService.findIds(filter, null).getTotalElements());
		//
		filter.setOperationState(OperationState.EXCEPTION);
		longRunningTaskDto.setFailedItemCount(itemService.findIds(filter, null).getTotalElements());
		//
		// warning items is all another items except executed and exception (eq. not_executed, ...)
		totalElements = totalElements - (longRunningTaskDto.getFailedItemCount() + longRunningTaskDto.getSuccessItemCount());
		longRunningTaskDto.setWarningItemCount(totalElements > 0 ? totalElements : 0); // total can be decremented, when LRT runs
		return longRunningTaskDto;
	}
}
