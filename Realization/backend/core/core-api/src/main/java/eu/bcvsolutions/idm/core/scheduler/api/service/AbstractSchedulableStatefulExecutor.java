package eu.bcvsolutions.idm.core.scheduler.api.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.persistence.EntityManager;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmProcessedTaskItemDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.filter.IdmProcessedTaskItemFilter;
import eu.bcvsolutions.idm.core.scheduler.api.exception.DryRunNotSupportedException;

/**
 * Abstract base class for statefull tasks, which handles common
 * process flow for context-less and stateful processes (the ones
 * with inner memory.
 * 
 * All stateful processes work with entity IDs (of type UUID) as
 * references to already processed items. 
 * 
 * @author Jan Helbich
 * @author Radek Tomiška
 *
 * @param <DTO> process DTO type, 
 * @since 7.6.0
 */
public abstract class AbstractSchedulableStatefulExecutor<DTO extends AbstractDto>
	extends AbstractSchedulableTaskExecutor<Boolean>
	implements SchedulableStatefulExecutor<DTO, Boolean> {
	
	private static final Logger LOG = LoggerFactory.getLogger(AbstractSchedulableStatefulExecutor.class);
	private static final int DEFAULT_PAGE_SIZE = 100;
	private boolean continueOnException = false; 
	private boolean requireNewTransaction = false;
	//
	@Autowired private IdmProcessedTaskItemService itemService;
	@Autowired private PlatformTransactionManager platformTransactionManager;
	@Qualifier("entityManager")
	@Autowired private EntityManager entityManager;

	@Override
	public Boolean process() {
		this.counter = 0L;
		//
		return executeProcess();
	}

	@Override
	public IdmProcessedTaskItemDto addToProcessedQueue(DTO dto, OperationResult opResult) {
		if (!supportsQueue()) {
			return null;
		}
		Assert.notNull(dto, "DTO is required for LRT processing.");
		Assert.notNull(opResult, "Result is required for add item into queue.");
		//
		if (this.getScheduledTaskId() == null) {
			// manually executed task -> ignore stateful queue
			LOG.debug("Running stateful tasks outside scheduler programatically.");
			//
			return null;
		}
		return itemService.createQueueItem(dto, opResult, this.getScheduledTaskId());
	}
	
	@Override
	public Collection<UUID> getProcessedItemRefsFromQueue() {
		if (!supportsQueue()) {
			return new ArrayList<>();
		}
		//
		if (this.getScheduledTaskId() == null) {
			LOG.debug("Running stateful tasks outside scheduler programatically.");
			//
			return new ArrayList<>();
		}
		return itemService.findAllRefEntityIdsInQueueByScheduledTaskId(this.getScheduledTaskId());
	}
	
	@Override
	public boolean isInProcessedQueue(DTO dto) {
		if (!supportsQueue()) {
			return false;
		}
		Assert.notNull(dto, "DTO is required for LRT processing.");
		//
		Page<IdmProcessedTaskItemDto> p = getItemFromQueue(dto.getId());
		
		return p.getTotalElements() > 0;
	}

	@Override
	public void removeFromProcessedQueue(UUID entityRef) {
		if (!supportsQueue()) {
			return;
		}
		Assert.notNull(entityRef, "Entity identifier is requred for remove from queue.");
		UUID scheduledTaskId = this.getScheduledTaskId();
		if (scheduledTaskId == null) {
			// nothing to delete
			return;
		}
		// remove entity from processed queue
		itemService.deleteItem(scheduledTaskId, entityRef);
	}

	@Override
	public void removeFromProcessedQueue(DTO dto) {
		Assert.notNull(dto, "DTO is required for remove from queue.");
		//
		removeFromProcessedQueue(dto.getId());
	}
	
	/**
	 * Returns true, if given task supports dry run mode. Returns {@code true} as default.
	 * 
	 * @since 7.8.3
	 */
	@Override
	public boolean supportsDryRun() {
		return true;
	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * Returns {@code false} by default (backward compatibility - each task can solve this his own way).
	 * @since 9.3.0
	 */
	@Override
	public boolean continueOnException() {
		return continueOnException;
	}
	
	@Override
	public void setContinueOnException(boolean continueOnException) {
		this.continueOnException = continueOnException;
	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * Returns {@code false} by default (backward compatibility - each task can solve this his own way).
	 * @since 9.3.0
	 */
	@Override
	public boolean requireNewTransaction() {
		return requireNewTransaction;
	}
	
	@Override
	public void setRequireNewTransaction(boolean requireNewTransaction) {
		this.requireNewTransaction = requireNewTransaction;
	}

	/**
	 * Process long running task.
	 * 
	 * @return true - completed, false - not complete
	 */
	private boolean executeProcess() {
		Set<UUID> processedRefs = new HashSet<>();
		//
		boolean canContinue = true;
		boolean updateStateSucceed = true;
		boolean dryRun = longRunningTaskService.get(this.getLongRunningTaskId()).isDryRun();
		Pageable pageable = PageRequest.of(
				0, 
				getPageSize(),
				new Sort(Direction.ASC, BaseEntity.PROPERTY_ID)
		);
		//
		do {
			Page<DTO> candidates = this.getItemsToProcess(pageable);
			//
			if (count == null) {
				count = candidates.getTotalElements();
			}
			//
			for (Iterator<DTO> i = candidates.iterator(); i.hasNext() && canContinue;) {
				DTO candidate = i.next();
				Assert.notNull(candidate, "DTO candidate is required for LRT processing.");
				Assert.notNull(candidate.getId(), "DTO candidate has to be persisted for LRT processing.");
				//
				Optional<OperationResult> result = processCandidate(candidate, dryRun);
				if (!result.isPresent() 
						|| result.get().getState().isSuccessful() // executed
						|| result.get().getState().isRunnable()) { // running (e.q. asynchronously)
					if (supportsQueue()) {
						processedRefs.add(candidate.getId());
					}					
				}
				updateStateSucceed = this.updateState();
				canContinue &= updateStateSucceed;
 				//
 				// flush and clear session - if LRT is wrapped in parent transaction, we need to clear it
 				if (getHibernateSession().isOpen()) {
 					getHibernateSession().flush();
 					getHibernateSession().clear();
 				}
			}
			canContinue &= candidates.hasNext();			
			pageable = candidates.nextPageable();
			//
		} while (canContinue);
		//
		// if task doesn't support queue, we can end
		if (!supportsQueue()) {
			return updateStateSucceed;
		}
		//
		// check task was not canceled or interrupted, then clean history
		// task is not ended yet - running is the correct state in this phase
		IdmLongRunningTaskDto task = longRunningTaskService.get(getLongRunningTaskId());
		OperationState resultState = task.getResultState();
		if (resultState.isSuccessful() // executed (set manually somehow ... just for sure)
				|| resultState.isRunnable()) { // running is the correct state in this phase
			List<UUID> queueEntityRefs = Lists.newArrayList(this.getProcessedItemRefsFromQueue());
			// processed should remain in history (is not related to whole task is canceled)
			queueEntityRefs.removeAll(processedRefs);
			queueEntityRefs.forEach(entityRef -> this.removeFromProcessedQueue(entityRef));
		}
		//
		return updateStateSucceed;
	}
	
	private Session getHibernateSession() {
		return (Session) this.entityManager.getDelegate();
	}

	private Optional<OperationResult> processCandidate(DTO candidate, boolean dryRun) {
		if (isInProcessedQueue(candidate)) {
			// item was processed earlier - just drop the count by one
			// FIXME: this is confusing => task ends with 0 count, if all items are skipped (processed before)
			--count;
			return Optional.empty();
		}
		// Is not possible to get real cause from UnexpectedRollbackException,
		// so result has to be evaluated inside before this exception is catch.
		List<Optional<OperationResult>> results = new ArrayList<>(1); 
		//
		if (dryRun) {
			if (!supportsDryRun()) {
				throw new DryRunNotSupportedException(getName());
			}
			// dry run mode - operation is not executed with dry run code (no content)
			results.add(Optional.of(new OperationResult
					.Builder(OperationState.NOT_EXECUTED)
					.setModel(new DefaultResultModel(CoreResultCode.DRY_RUN))
					.build()));
		} else if (requireNewTransaction()) {
			TransactionTemplate template = new TransactionTemplate(platformTransactionManager);
			template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
			//
			try {
				template.execute(new TransactionCallbackWithoutResult() {
					
					@Override
					protected void doInTransactionWithoutResult(TransactionStatus status) {
						results.add(processItemInternal(candidate));
					}
				});
			} catch (UnexpectedRollbackException ex ) {
				// Just log for sure ... exception solved in new transaction, but this lower transaction is marked as roll-back.
				LOG.debug("Statefull process [{}] processed item [{}] failed",
						getClass().getSimpleName(), candidate, ex);
			}
		} else {
			results.add(this.processItemInternal(candidate));
		}
		//
		++counter;
		Optional<OperationResult> result;
		if (!results.isEmpty()) {
			result = results.get(0);
			if (result == null) {
				// null can be returned simply => empty result
				result = Optional.empty();
			}
		} else {
			result = Optional.empty();
		}
		//
		if (result.isPresent()) {
			OperationResult opResult = result.get();
			this.logItemProcessed(candidate, opResult);
			if (OperationState.isSuccessful(opResult.getState())) {
				this.addToProcessedQueue(candidate, opResult);
			}
			LOG.debug("Statefull process [{}] intermediate result: [{}], count: [{}/{}]",
					getClass().getSimpleName(), opResult.getState(), count, counter);
			if (!continueOnException() && opResult.getException() != null) {
				ResultCodeException resultCodeException;
				if (opResult.getException() instanceof ResultCodeException) {
					resultCodeException = (ResultCodeException) opResult.getException();
				} else {
					resultCodeException = new ResultCodeException(
							CoreResultCode.LONG_RUNNING_TASK_ITEM_FAILED, 
							ImmutableMap.of(
									"referencedEntityId", candidate.getId()),
							opResult.getException());	
				}
				LOG.error("[" + resultCodeException.getId() + "] ", resultCodeException);
				//
				throw resultCodeException;
			}
		} else {
			LOG.debug("Statefull process [{}] processed item [{}] without result.",
					getClass().getSimpleName(), candidate);
		}
		//
		return result;
	}
	
	private Optional<OperationResult> processItemInternal(DTO candidate) {
		try {
			return processItem(candidate);
		} catch (Exception ex) {
			// convert exception to result code exception with model
			ResultCodeException resultCodeException;
			if (ex instanceof ResultCodeException) {
				resultCodeException = (ResultCodeException) ex;
			} else {
				resultCodeException = new ResultCodeException(
						CoreResultCode.LONG_RUNNING_TASK_ITEM_FAILED, 
						ImmutableMap.of(
								"referencedEntityId", candidate.getId()),
						ex);	
			}
			LOG.error("[" + resultCodeException.getId() + "] ", resultCodeException);
			//
			return Optional.of(new OperationResult
						.Builder(OperationState.EXCEPTION)
						.setException(resultCodeException)
						.build());
		}
	}
	
	private Page<IdmProcessedTaskItemDto> getItemFromQueue(UUID entityRef) {
		// if scheduled task is null process all item including already processed items 
		// TODO: this is probably not good idea, but for now it is only choice
		if (this.getScheduledTaskId() == null) {
			return new PageImpl<>(Collections.emptyList());
		}
		IdmProcessedTaskItemFilter filter = new IdmProcessedTaskItemFilter();
		filter.setReferencedEntityId(entityRef);
		filter.setScheduledTaskId(this.getScheduledTaskId());
		Page<IdmProcessedTaskItemDto> p = itemService.find(filter, PageRequest.of(0, 1));
		if (p.getTotalElements() > 1) {
			LOG.warn("Multiple same item references found in [{}] process queue.", this.getClass());
		}
		return p;
	}
	
	/**
	 * Persists LRT items
	 * 
	 * @return
	 */
	protected IdmProcessedTaskItemService getItemService() {
		return itemService;
	}	
	
	/**
	 * LRT page size - search batch size.
	 * 
	 * @return {@value #PAGE_SIZE} by default.
	 * @since 10.2.0
	 */
	protected int getPageSize() {
		return DEFAULT_PAGE_SIZE;
	}
}
