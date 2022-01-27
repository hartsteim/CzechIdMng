package eu.bcvsolutions.idm.core.scheduler.api.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import eu.bcvsolutions.idm.core.api.CoreModule;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.service.Configurable;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmProcessedTaskItemDto;

/**
 * Long running task executor.
 * 
 * @author Radek Tomiška
 */
public interface LongRunningTaskExecutor<V> extends Callable<V>, Configurable {
	
	String CONFIGURABLE_TYPE = "long-running-task";
	String PARAMETER_INSTANCE_ID = String.format("%s:%s", CoreModule.MODULE_ID, ConfigurationService.PROPERTY_INSTANCE_ID); // server instance id
	String PARAMETER_TRANSACTION_CONTEXT = String.format("%s:transactionContext", CoreModule.MODULE_ID); // user transaction context
	
	@Override
	default String getConfigurableType() {
		return CONFIGURABLE_TYPE;
	}
	
	/**
	 *  bean name / unique identifier (spring bean name)
	 *  
	 * @return
	 */
	String getId();
	
	/**
	 * Initialize task executor before task is processed.
	 * Look out: init is called by scheduler with configured task properties. 
	 * 
	 * @param context
	 */
	void init(Map<String, Object> properties);
	
	/**
	 * Returns persistent task parameter values. Don't forget to override this method additively.
	 * init(properties) => getProperties() should return the at least the same values as init method.
	 */
	Map<String, Object> getProperties();
	
	/**
	 * Main execution method
	 */
	V process();
	
	/**
	 * When task publish and execute some events asynchronously, then this method will be called,
	 * after all asynchronous events are processed => task end is delayed, till all events are processed.
	 * 
	 * Lookout: method is called, when LRT is in waiting 
	 * state only. Implement {@link AbstractLongRunningTaskExecutor#end(Object, Exception)} properly instead.
	 * 
	 * @see AbstractLongRunningTaskExecutor#end(Object, Exception)
	 * @since 10.6.0
	 */
	void notifyEnd();
	
	/**
	 * Executors description
	 * 
	 * @return
	 */
	String getDescription();
	
	/**
	 * Returns total item count
	 * 
	 * @return
	 */
	Long getCount();
	
	/**
	 * Returns processed items count
	 * 
	 * @return
	 */
	Long getCounter();
	
	/**
	 * Sets total item count
	 * 
	 */
	void setCount(Long count);

	/**
	 * Sets processed items count
	 * 
	 */
	void setCounter(Long counter);
	
	/**
	 * Get long running task result, after task ends.
	 * 
	 * @return result value. {@code null} => task is running
	 * @since 10.6.0
	 */
	Optional<V> getResult();
	
	/**
	 * Increase counter by 1 and return it.
	 * @return
	 */
	Long increaseCounter();
	
	/**
	 * Updates persisted task state (count, counter, etc.)
	 * 
	 * @param context
	 * @return Returns false, when long running task is canceled.
	 */
	boolean updateState();
	
	/**
	 * Returns true, when task updates its state when runs => can be canceled, progress can be shown,
	 * 
	 * @return
	 */
	boolean isStateful();
	
	/**
	 * Task can be executed repetitively without reschedule is needed.
	 * When task is canceled (e.g. by server is restarted), then task can be executed again.
	 * 
	 * @return true - LRT can be executed again.
	 * @since 10.2.0
	 */
	boolean isRecoverable();
	
	/**
	 * Gets long running task log id
	 * 
	 * @return
	 */
	UUID getLongRunningTaskId();
	
	/**
	 * Sets long running task log id
	 * 
	 * @param longRunningTask
	 */
	void setLongRunningTaskId(UUID taskId);
	
	/**
	 * Validates task before start e.q. if task already running or to prevent run task concurrently.
	 * 
	 * @param task persisted task to validate
	 */
	void validate(IdmLongRunningTaskDto task);
	
	/**
	 * Create new log in processed items
	 *
	 * @param item
	 * @param opResult
	 * @return
	 */
	<DTO extends AbstractDto> IdmProcessedTaskItemDto logItemProcessed(DTO item, OperationResult opResult);

	public Boolean finishEvent();
}
