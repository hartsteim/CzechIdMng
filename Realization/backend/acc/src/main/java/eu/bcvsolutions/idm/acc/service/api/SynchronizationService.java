package eu.bcvsolutions.idm.acc.service.api;

import java.util.List;
import java.util.UUID;

import eu.bcvsolutions.idm.acc.domain.SynchronizationContext;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncItemLogDto;
import eu.bcvsolutions.idm.acc.entity.SysSyncConfig;
import eu.bcvsolutions.idm.acc.event.SynchronizationEventType;
import eu.bcvsolutions.idm.acc.event.processor.provisioning.ProvisioningStartProcessor;
import eu.bcvsolutions.idm.acc.event.processor.synchronization.SynchronizationCancelProcessor;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.ic.api.IcAttribute;

/**
 * Service for do synchronization and reconciliation
 * 
 * @author svandav
 *
 */
public interface SynchronizationService {

	String PARAMETER_SYNCHRONIZATION_ID = "Synchronization uuid";
	String WF_VARIABLE_KEY_UID = "uid";
	String WF_VARIABLE_KEY_ENTITY_TYPE = "entityType";
	String WF_VARIABLE_KEY_SYNC_SITUATION = "situation";
	String WF_VARIABLE_KEY_IC_ATTRIBUTES = "icAttributes";
	String WF_VARIABLE_KEY_ACTION_TYPE = "actionType";
	String WF_VARIABLE_KEY_SYNC_CONFIG_ID = "syncConfigId";
	String WF_VARIABLE_KEY_ENTITY_ID = "entityId";
	String WF_VARIABLE_KEY_ACC_ACCOUNT_ID = "accountId";
	String WF_VARIABLE_KEY_LOG_ITEM = "logItem";
	String WRAPPER_SYNC_ITEM = "wrapper_sync_item";
	String RESULT_SYNC_ITEM = "result_sync_item";
	String SYNC_EXECUTOR_CACHE_NAME = "sync-executor-cache";

	/**
	 * Cancels all previously ran synchronizations
	 */
	void init();

	/**
	 * Main method for start synchronization by given configuration. This method
	 * produces event {@link SynchronizationEventType.START}.
	 * 
	 * @param config
	 * @return
	 */
	AbstractSysSyncConfigDto startSynchronizationEvent(AbstractSysSyncConfigDto config);

	/**
	 * Main method for cancel running synchronization by given configuration. This
	 * method produces event {@link SynchronizationEventType.CANCEL}.
	 * 
	 * @param config
	 * @return
	 */
	AbstractSysSyncConfigDto stopSynchronizationEvent(AbstractSysSyncConfigDto config);

	/**
	 * Default implementation of synchronization. By default is call from
	 * {@link ProvisioningStartProcessor} as reaction on
	 * {@link SynchronizationEventType.START} event.
	 * 
	 * @param config
	 * @return
	 */
	void startSynchronization(AbstractSysSyncConfigDto config, AbstractSchedulableTaskExecutor<Boolean> longRunningTaskExecutor);

	/**
	 * Default implementation cancel running synchronization. By default is call
	 * from {@link SynchronizationCancelProcessor} as reaction on
	 * {@link SynchronizationEventType.CANCEL} event.
	 * 
	 * @param config
	 * @return
	 */
	AbstractSysSyncConfigDto stopSynchronization(AbstractSysSyncConfigDto config);

	/**
	 * Basic method for item synchronization. Item is obtained from target resource
	 * (searched). This method is called for synchronization and for reconciliation
	 * too. Every call this method starts new transaction.
	 * 
	 * @param wrapper
	 *            for easier handling required attributes for item synchronization
	 * @return If is true, then well be synchronization continued, if is false, then
	 *         will be cancel.
	 */
	boolean doItemSynchronization(SynchronizationContext wrapper);

	/**
	 * Public method for resolve missing entity situation for one item. This method
	 * can be call outside main synchronization loop. For example from workflow
	 * process.
	 * 
	 * @param uid
	 *            Item identification
	 * @param entityType
	 *            Type of resolve entity
	 * @param icAttributes
	 *            List of attributes given from target resource
	 * @param configId
	 *            Id of {@link SysSyncConfig}
	 * @param actionType
	 *            Action for this situation.
	 * @return
	 */
	SysSyncItemLogDto resolveMissingEntitySituation(String uid, SystemEntityType entityType,
			List<IcAttribute> icAttributes, UUID configId, String actionType);

	/**
	 * Public method for resolve linked situation for one item. This method can be
	 * call outside main synchronization loop. For example from workflow process.
	 * 
	 * @param uid
	 *            Item identification
	 * @param entityType
	 *            Type of resolve entity
	 * @param icAttributes
	 *            List of attributes given from target resource
	 * @param configId
	 *            Id of {@link SysSyncConfig}
	 * @param actionType
	 *            Action for this situation.
	 * @return
	 */
	SysSyncItemLogDto resolveLinkedSituation(String uid, SystemEntityType entityType, List<IcAttribute> icAttributes,
			UUID accountId, UUID configId, String actionType);

	/**
	 * Public method for resolve unlinked situation for one item. This method can be
	 * call outside main synchronization loop. For example from workflow process.
	 * 
	 * @param uid
	 *            Item identification
	 * @param entityType
	 *            Type of resolve entity
	 * @param icAttributes
	 *            List of attributes given from target resource
	 * @param configId
	 *            Id of {@link SysSyncConfig}
	 * @param actionType
	 *            Action for this situation.
	 * @return
	 */
	SysSyncItemLogDto resolveUnlinkedSituation(String uid, SystemEntityType entityType, UUID entityId, UUID configId,
			String actionType);

	/**
	 * Public method for resolve missing account situation for one item. This method
	 * can be call outside main synchronization loop. For example from workflow
	 * process.
	 * 
	 * @param uid
	 *            Item identification
	 * @param entityType
	 *            Type of resolve entity
	 * @param icAttributes
	 *            List of attributes given from target resource
	 * @param configId
	 *            Id of {@link SysSyncConfig}
	 * @param actionType
	 *            Action for this situation.
	 * @return
	 */
	SysSyncItemLogDto resolveMissingAccountSituation(String uid, SystemEntityType entityType, UUID accountId,
			UUID configId, String actionType);

	/**
	 * Find executor for synchronization by given ID of sync configuration.
	 * Executors are cached, when none executor is found for that sync config, then
	 * is found executor type by given entity type and for it is created new
	 * instance of executor.
	 * 
	 * @return
	 */
	SynchronizationEntityExecutor getSyncExecutor(SystemEntityType entityType, UUID syncConfigId);

}