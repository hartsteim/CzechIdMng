package eu.bcvsolutions.idm.core.scheduler.task.impl;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;

import org.hibernate.Session;
import org.quartz.DisallowConcurrentExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmContractSliceDto;
import eu.bcvsolutions.idm.core.api.dto.IdmEntityStateDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.ResultModel;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmEntityStateFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.service.ContractSliceManager;
import eu.bcvsolutions.idm.core.api.service.EntityStateManager;
import eu.bcvsolutions.idm.core.api.service.IdmContractSliceService;
import eu.bcvsolutions.idm.core.model.entity.IdmContractSlice;
import eu.bcvsolutions.idm.core.model.event.ContractSliceEvent;
import eu.bcvsolutions.idm.core.model.event.ContractSliceEvent.ContractSliceEventType;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;

/**
 * Task for clear all dirty states for all contract slices.
 *
 * @author Ondrej Kopr
 * @author Vít Švanda
 *
 */
@DisallowConcurrentExecution
@Component(ClearDirtyStateForContractSliceTaskExecutor.TASK_NAME)
public class ClearDirtyStateForContractSliceTaskExecutor extends AbstractSchedulableTaskExecutor<OperationResult> {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ClearDirtyStateForContractSliceTaskExecutor.class);
	public static final String TASK_NAME = "core-clear-dirty-state-contract-slice-long-running-task";
	public final static String ORIGINAL_SLICE = "originalSlice";
	public final static String CURRENT_SLICE = "currentSlice";
	public final static String TO_DELETE = "toDelete";
	// Workaround - skip provisioning is from the ACC module, but work with slices and dirty states is only in core. So this is safe way how remove a skip property.
	private static final String SKIP_PROVISIONING = "skip_provisioning";

	@Autowired
	private EntityStateManager entityStateManager;
	@Autowired
	private IdmContractSliceService contractSliceService;
	@Autowired
	private ContractSliceManager contractSliceManager;
	@Qualifier("entityManager")
	@Autowired
	private EntityManager entityManager;
	
	@Override
	public String getName() {
		return TASK_NAME;
	}

	@Override
	public OperationResult process() {
		boolean canContinue = true;

		List<IdmEntityStateDto> dirtyStates = findAllDirtyStatesForSlices(null).getContent();
		if (count == null) {
			count = Long.valueOf(dirtyStates.size());
		}

		counter = 0l;
		List<IdmEntityStateDto> updateDirtyStates = Lists.newArrayList();
		List<IdmEntityStateDto> validDirtyStates = Lists.newArrayList();
		List<IdmEntityStateDto> futureDirtyStates = Lists.newArrayList();
		List<IdmEntityStateDto> unvalidDirtyStates = Lists.newArrayList();
		List<IdmEntityStateDto> deleteDirtyStates = Lists.newArrayList();

		dirtyStates.forEach(dirtyState -> {
			ResultModel resultModel = dirtyState.getResult().getModel();

			Map<String, Object> parameters = new HashMap<>();
			if (resultModel != null) {
				parameters = resultModel.getParameters();
			}
			boolean sliceIsToDelete = this.getBooleanProperty(ClearDirtyStateForContractSliceTaskExecutor.TO_DELETE,
					parameters);
			if (sliceIsToDelete) {
				deleteDirtyStates.add(dirtyState);
			} else {
				updateDirtyStates.add(dirtyState);
			}
		});

		updateDirtyStates.forEach(dirtyState -> {
			IdmContractSliceDto contractSliceDto = contractSliceService.get(dirtyState.getOwnerId());
			if (contractSliceDto == null) {
				DefaultResultModel model = new DefaultResultModel(CoreResultCode.NOT_FOUND,
						ImmutableMap.of("ownerId", dirtyState.getOwnerId()));
				this.logItemProcessed(dirtyState,
						new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(model).build());
				return;
			}
			// Temporary put current slice to the dirtyState
			dirtyState.getEmbedded().put(CURRENT_SLICE, contractSliceDto);

			// Divide slices by validity
			IdmIdentityContractDto mockContract = new IdmIdentityContractDto();
			contractSliceManager.convertSliceToContract(contractSliceDto, mockContract);
			if (!mockContract.isValidNowOrInFuture()) {
				unvalidDirtyStates.add(dirtyState);
			} else if (mockContract.isValid()) {
				validDirtyStates.add(dirtyState);
			} else {
				futureDirtyStates.add(dirtyState);
			}
		});

		// Process for new and updated slices - valid
		for (IdmEntityStateDto dirtyState : validDirtyStates) {
			canContinue = processState(canContinue, dirtyState);
			if (!canContinue) {
				break;
			}
		}

		// Process for new and updated slices - future valid
		for (IdmEntityStateDto dirtyState : futureDirtyStates) {
			canContinue = processState(canContinue, dirtyState);
			if (!canContinue) {
				break;
			}
		}

		// Process for new and updated slices - invalid
		for (IdmEntityStateDto dirtyState : unvalidDirtyStates) {
			canContinue = processState(canContinue, dirtyState);
			if (!canContinue) {
				break;
			}
		}

		// Process for slices to delete
		for (IdmEntityStateDto dirtyState : deleteDirtyStates) {
			Assert.notNull(dirtyState, "State (dirty) is required.");
			Assert.notNull(dirtyState.getId(), "State identifier (dirty) is required.");
			processItemToDelete(dirtyState);
			counter++;

			// flush and clear session - if LRT is wrapped in parent transaction, we need to
			// clear it (same behavior as in stateful tasks)
			if (getHibernateSession().isOpen()) {
				getHibernateSession().flush();
				getHibernateSession().clear();
			}

			canContinue &= this.updateState();
			if (!canContinue) {
				break;
			}
		}

		return new OperationResult(OperationState.EXECUTED);
	}
	
	@Override
    public boolean isRecoverable() {
    	return true;
    }

	/**
	 * Process state for new or updated slices
	 * 
	 * @param canContinue
	 * @param dirtyState
	 * @return
	 */
	private boolean processState(boolean canContinue, IdmEntityStateDto dirtyState) {
		Assert.notNull(dirtyState, "State (dirty) is required.");
		Assert.notNull(dirtyState.getId(), "State identifier (dirty) is required.");
		processItem(dirtyState);
		counter++;

		// flush and clear session - if LRT is wrapped in parent transaction, we need to
		// clear it (same behavior as in stateful tasks)
		if (getHibernateSession().isOpen()) {
			getHibernateSession().flush();
			getHibernateSession().clear();
		}

		canContinue &= this.updateState();
		return canContinue;
	}

	private Session getHibernateSession() {
		return (Session) this.entityManager.getDelegate();
	}

	/**
	 * Process one dirty state for contract slice
	 *
	 * @param dirtyState
	 */
	private void processItem(IdmEntityStateDto dirtyState) {
		try {
			if (dirtyState.getOwnerType() == null
					|| !dirtyState.getOwnerType().equals(IdmContractSlice.class.getName())) {
				this.logItemProcessed(dirtyState, new OperationResult.Builder(OperationState.NOT_EXECUTED).build());
				return;
			}
			IdmContractSliceDto contractSliceDto = (IdmContractSliceDto) dirtyState.getEmbedded().get(CURRENT_SLICE);
			if (contractSliceDto == null) {
				contractSliceDto = contractSliceService.get(dirtyState.getOwnerId());
			}
			if (contractSliceDto == null) {
				DefaultResultModel model = new DefaultResultModel(CoreResultCode.NOT_FOUND,
						ImmutableMap.of("ownerId", dirtyState.getOwnerId()));
				this.logItemProcessed(dirtyState,
						new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(model).build());
				return;
			}
			ResultModel resultModel = dirtyState.getResult().getModel();

			Map<String, Object> parameters = new HashMap<>();
			if (resultModel != null) {
				parameters = resultModel.getParameters();
			}

			IdmContractSliceDto originalSlice = null;
			Object originalSliceAsObject = parameters.get(ORIGINAL_SLICE);
			if (originalSliceAsObject instanceof IdmContractSliceDto) {
				originalSlice = (IdmContractSliceDto) originalSliceAsObject;
			}

			// Transform saved parameters into map string and serializable value
			Map<String, Serializable> transformedParameters = transformParameters(parameters);
			// Current using flag was sets to FALSE (during making as dirty), we want to force recalculate
			transformedParameters.put(IdmContractSliceService.FORCE_RECALCULATE_CURRENT_USING_SLICE, Boolean.TRUE);
			// Provisioning skip have to by removed. We need to make a provisioning in this phase.
			// Workaround - skip provisioning is from the ACC module, but work with slices and dirty states is only in core. So this is safe way how remove a skip property.
			transformedParameters.put(SKIP_PROVISIONING, Boolean.FALSE);

			contractSliceManager.recalculateContractSlice(contractSliceDto, originalSlice, transformedParameters);

			this.logItemProcessed(contractSliceDto, new OperationResult.Builder(OperationState.EXECUTED).build());
			entityStateManager.deleteState(dirtyState);
		} catch (Exception e) {
			this.logItemProcessed(dirtyState,
					new OperationResult.Builder(OperationState.EXCEPTION).setCause(e).build());
		}
	}

	/**
	 * Process one dirty state for contract slice to delete
	 *
	 * @param dirtyState
	 */
	private void processItemToDelete(IdmEntityStateDto dirtyState) {
		try {
			if (dirtyState.getOwnerType() == null
					|| !dirtyState.getOwnerType().equals(IdmContractSlice.class.getName())) {
				this.logItemProcessed(dirtyState, new OperationResult.Builder(OperationState.NOT_EXECUTED).build());
				return;
			}
			IdmContractSliceDto contractSliceDto = contractSliceService.get(dirtyState.getOwnerId());
			;
			if (contractSliceDto == null) {
				DefaultResultModel model = new DefaultResultModel(CoreResultCode.NOT_FOUND,
						ImmutableMap.of("ownerId", dirtyState.getOwnerId()));
				this.logItemProcessed(dirtyState,
						new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(model).build());
				return;
			}

			ResultModel resultModel = dirtyState.getResult().getModel();

			Map<String, Object> parameters = new HashMap<>();
			if (resultModel != null) {
				parameters = resultModel.getParameters();
			}

			// Transform saved parameters into map string and serializable value
			Map<String, Serializable> transformedParameters = transformParameters(parameters);
			
			EntityEvent<IdmContractSliceDto> event = new ContractSliceEvent(ContractSliceEventType.DELETE,
					contractSliceDto, transformedParameters);
			// Delete slice (with recalculation)
			contractSliceService.publish(event);
			this.logItemProcessed(contractSliceDto, new OperationResult.Builder(OperationState.EXECUTED).build());
		} catch (Exception e) {
			this.logItemProcessed(dirtyState,
					new OperationResult.Builder(OperationState.EXCEPTION).setCause(e).build());
		}
	}

	/**
	 * Find all dirty states for contract slices
	 *
	 * @param pageable
	 * @return
	 */
	private Page<IdmEntityStateDto> findAllDirtyStatesForSlices(Pageable pageable) {
		IdmEntityStateFilter filter = new IdmEntityStateFilter();
		filter.setResultCode(CoreResultCode.DIRTY_STATE.getCode());
		filter.setOwnerType(entityStateManager.getOwnerType(IdmContractSliceDto.class));
		return entityStateManager.findStates(filter, pageable);
	}

	private boolean getBooleanProperty(String property, Map<String, Object> properties) {
		if (properties == null) {
			return false;
		}

		Object propertyValue = properties.get(property);

		if (propertyValue == null) {
			return false;
		}
		if ((Boolean) propertyValue) {
			return true;
		}

		return false;
	}

	/**
	 * Transformation of parameters. Removes the temporary parameters
	 * 
	 * @param parameters
	 * @return
	 */
	private Map<String, Serializable> transformParameters(Map<String, Object> parameters) {
		Map<String, Serializable> transformedParameters = new HashMap<>();
		parameters.forEach((key, value) -> {
			if (key != null && ORIGINAL_SLICE.equals(key)) {
				// skip original slice
			} else if (key != null && IdmContractSliceService.SET_DIRTY_STATE_CONTRACT_SLICE.equals(key)) {
				// remove skip recalculation for contract slice
			} else if (key != null && ClearDirtyStateForContractSliceTaskExecutor.CURRENT_SLICE.equals(key)) {
				// remove current slice
			} else if (key != null && ClearDirtyStateForContractSliceTaskExecutor.TO_DELETE.equals(key)) {
				// remove to delete for contract slice
			} else if (value == null) {
				transformedParameters.put(key, null);
			} else if (value instanceof Serializable) {
				transformedParameters.put(key, (Serializable) value);
			} else {
				LOG.error(
						"Given value [{}] with key [{}] for parameters is not posible cast to serializable. Skip the value",
						value, key);
			}
		});
		return transformedParameters;
	}
}
