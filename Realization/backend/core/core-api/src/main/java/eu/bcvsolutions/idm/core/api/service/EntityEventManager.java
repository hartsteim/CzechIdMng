package eu.bcvsolutions.idm.core.api.service;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import eu.bcvsolutions.idm.core.api.CoreModule;
import eu.bcvsolutions.idm.core.api.config.domain.EventConfiguration;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.EntityEventProcessorDto;
import eu.bcvsolutions.idm.core.api.dto.IdmEntityEventDto;
import eu.bcvsolutions.idm.core.api.dto.IdmEntityStateDto;
import eu.bcvsolutions.idm.core.api.dto.OperationResultDto;
import eu.bcvsolutions.idm.core.api.dto.filter.EntityEventProcessorFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmEntityEventFilter;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EntityEventProcessor;
import eu.bcvsolutions.idm.core.api.event.EventContext;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.exception.EventContentDeletedException;
import eu.bcvsolutions.idm.core.api.exception.ForbiddenEntityException;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.script.ScriptEnabled;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskExecutor;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;

/**
 * Entity processing based on synchronous {@link ApplicationEvent} publishing.
 * {@link AbstractEntity} type or {@link AbstractDto} can be used as change owner.
 * If {@link AbstractDto} is given as owner type, then {@link AbstractEntity} owner will be found by 
 * {@link LookupService} => transformation to {@link AbstractEntity}. 
 * {@link AbstractDto} or {@link AbstractEntity} descendants are supported => {@link UUID} identifier is needed.
 * 
 * @see IdmEntityEventDto
 * @see AbstractDto
 * @see AbstractEntity
 * @see LookupService
 * @see EntityEventProcessor
 * @see EntityEvent
 * @see EventContext
 * 
 * @author Radek Tomiška
 */
public interface EntityEventManager extends ScriptEnabled {
	
	String EVENT_PROPERTY_SKIP_NOTIFY = "idm:skip-notify";
	String EVENT_PROPERTY_SKIP_NOTIFICATION = "idm:skip-notification"; // skip sending notifications
	/**
	 * @deprecated @since 10.6.0 - added to solve backward compatibility, will be removed in future release.
	 */
	@Deprecated
	String EVENT_PROPERTY_SKIP_SUB_ROLES = "idm:skip-sub-roles"; // skip assign sub roles by asynchronous event processing
	
	/**
	 * Transaction events by transaction id.
	 * 
	 * @since 10.6.0
	 */
	String TRANSACTION_EVENT_CACHE_NAME = IdmCacheManager.getCacheName(CoreModule.MODULE_ID, "transaction-event-cache");
	
	/**
	 * Cancel all previously ran events
	 */
	void init();
	
	/**
	 * Process event through all registered entity processor in configured order with default context (newly created context). 
	 * Suspended event will continue.
	 * 
	 * @param event
	 * @return
	 */
	<E extends Serializable> EventContext<E> process(EntityEvent<E> event);
	
	/**
	 * Process event through all registered entity processor in configured order with default context (newly created context). 
	 * Suspended event will continue.
	 * 
	 * @param event
	 * @param parentEvent event is based on parent event
	 * @return
	 */
	<E extends Serializable> EventContext<E> process(EntityEvent<E> event, EntityEvent<?> parentEvent);
	
	/**
	 * Process event through all registered entity processor in configured order with default context (newly created context) asynchronously 
	 * 
	 * @param event
	 * @since 9.4.0
	 */
	void processOnBackground(EntityEvent<? extends Identifiable> event);
	
	/**
	 * Returns all registered entity event processors
	 * 
	 * @param filter
	 * @return
	 */
	List<EntityEventProcessorDto> find(EntityEventProcessorFilter filter);
	
	/**
	 * Get events by given filter.
	 * 
	 * @see IdmEntityEventFilter
	 * @param filter
	 * @param pageable
	 * @param permission base permissions to evaluate (AND) 
	 * @return states
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 * @since 10.8.0
	 */
	Page<IdmEntityEventDto> findEvents(IdmEntityEventFilter filter, Pageable pageable, BasePermission... permission);
	
	/**
	 * Returns registered and enabled processor to given event.
	 * Conditional method on the processor ({@link EntityEventProcessor#conditional(EntityEvent)}) is not evaluated -> all enabled processor 
	 * registreded to given event type and content are returned. 
	 * 
	 * @param event
	 * @return
	 * @see EntityEventProcessor#conditional(EntityEvent)
	 */
	@SuppressWarnings("rawtypes")
	List<EntityEventProcessor> getEnabledProcessors(EntityEvent<?> event);
	
	/**
	 * Get registered event processor by id
	 * 
	 * @param processorId
	 * @return
	 */
	EntityEventProcessorDto get(String processorId);
	
	/**
	 * Get registered event processor by id
	 * 
	 * @param processorId
	 * @return
	 */
	EntityEventProcessor<?> getProcessor(String processorId);
	
	/**
	 * Constructs new entity event.
	 * 
	 * @param identifiable
	 * @param originalEvent parent event
	 * @return
	 */
	IdmEntityEventDto prepareEvent(Identifiable identifiable, EntityEvent<? extends Identifiable> originalEvent);
	
	/**
	 * Publish common event to all listeners
	 * 
	 * @param event
	 */
	void publishEvent(Object event);
	
	/**
	 * Publish entity changed event.
	 * 
	 * @param abstract dto / entity
	 * @since 8.0.0
	 */
	<E extends Identifiable> void changedEntity(E owner);
	
	/**
	 * Publish entity changed event.
	 * 
	 * 
	 * @param owner can be different than original event content (e.g. original event is for contract, but change is registered for identity)
	 * @param parentEvent parent event
	 * @since 8.0.0
	 */
	<E extends Identifiable> void changedEntity(E owner, EntityEvent<? extends Identifiable> parentEvent);
	
	/**
	 * Publish entity changed event.
	 * 
	 * @param ownerType
	 * @param ownerId
	 * @since 8.0.0
	 */
	void changedEntity(Class<? extends Identifiable> ownerType, UUID ownerId);
	
	/**
	 * Publish entity changed event.
	 * 
	 * 
	 * @param ownerType can be different than original event content type (e.g. original event is for contract, but change is on identity)
	 * @param ownerId
	 * @param parentEvent parent event
	 * @since 8.0.0
	 */
	void changedEntity(
			Class<? extends Identifiable> ownerType, 
			UUID ownerId, 
			EntityEvent<? extends Identifiable> parentEvent);
	
	/**
	 * Owner type has to be entity class - dto instance can be given.
	 * 
	 * @param owner
	 * @return
	 * @since 9.0.0
	 */
	String getOwnerType(Identifiable owner);
	
	/**
	 * Owner type has to be entity class - dto class can be given.
	 * 
	 * @param ownerType
	 * @return
	 * @since 8.0.0
	 */
	String getOwnerType(Class<? extends Identifiable> ownerType);
	
	/**
	 * Returns given event owner instance.
	 * 
	 * @param change
	 * @return
	 * @since 8.0.0
	 */
	AbstractDto findOwner(IdmEntityEventDto event);
	
	/**
	 * Returns given owner instance by event owner type and identifier.
	 * 
	 * TODO: move to LookupService.lookupOwner - refactor method usage
	 * 
	 * @param ownerType
	 * @param ownerId
	 * @return
	 */
	AbstractDto findOwner(String ownerType, Serializable ownerId);
	
	/**
	 * Returns entity event, if given event is based on persisted entity event, otherwise returns {@code null}. 
	 * 
	 * @param event
	 * @return
	 * @since 8.0.0
	 */
	IdmEntityEventDto getEvent(EntityEvent<? extends Serializable> event);
	
	/**
	 * Returns persisted event with given id, when event with given id is not persisted, then returns {@code null}.
	 * 
	 * @param event
	 * @return
	 * @since 9.1.0
	 */
	IdmEntityEventDto getEvent(UUID eventId);
	
	/**
	 * Returns entity event, if given event is based on persisted entity event, otherwise returns {@code null}. 
	 * 
	 * @param event
	 * @return
	 * @since 8.0.0
	 */
	UUID getEventId(EntityEvent<? extends Serializable> event);
	
	/**
	 * Returns true, when persisted event is runnable (created or running already). 
	 * 
	 * @see OperationState#isRunnable()
	 * @param eventId event identifier
	 * @return true, when event is created or running
	 * @since 9.1.0
	 * @see OperationState#isRunnable()
	 */
	boolean isRunnable(UUID eventId);
	
	/**
	 * Returns true, when persisted event is runnable (created or running already). 
	 * 
	 * @see OperationState#isRunnable()
	 * @param eventId event identifier
	 * @return true, when event is created or running
	 * @since 9.7.16
	 * @see OperationState#isRunnable()
	 */
	boolean isRunnable(IdmEntityEventDto event);
	
	/**
	 * Resurrects given event from persisted state. Event can be processed.
	 * 
	 * Look out: content of resurrected event could be deleted - original content was deleted and original source was not saved, 
	 * when event was persisted. In this case {@link EventContentDeletedException} is thrown.
	 * 
	 * @param entityEvent
	 * @return
	 * @throws EventContentDeletedException if event content was deleted already - event cannot be constructed again.
	 * @since 8.0.0
	 */
	EntityEvent<? extends Identifiable> toEvent(IdmEntityEventDto entityEvent);
	
	/**
	 * Execute given persisted event. Event will be executed by event priority:
	 * - immediate - synchronously
	 * - high / normal - asynchronously in new thread
	 * 
	 * @param entityEvent
	 * @since 8.0.0
	 */
	void executeEvent(IdmEntityEventDto entityEvent);
	
	/**
	 * Cancel given running event.
	 * 
	 * @param entityEvent
	 * @since 12.0.0
	 * @throws ResultCodeException if event is not runnable (~ created, running).
	 */
	void cancelEvent(IdmEntityEventDto entityEvent);
	
	/**
	 * Persists event "as it is". Event id is set (if it was empty).
	 * 
	 * @param event
	 * @param result event result [optional]
	 * @since 9.1.0
	 */
	IdmEntityEventDto saveEvent(EntityEvent<? extends Identifiable> event, OperationResultDto result);
	
	/**
	 * Persists event
	 * 
	 * @param event
	 * @param result
	 * @since 8.0.0
	 */
	IdmEntityEventDto saveEvent(IdmEntityEventDto event);
	
	/**
	 * Delete event. Removes running event from running event application cache => other events for the same owner will be executed.
	 * 
	 * @param event
	 * @since 9.4.0
	 */
	void deleteEvent(IdmEntityEventDto entityEvent);
	
	/**
	 * Delete all persisted events and their states. Removes running event from running event application cache => other events for the same owner will be executed.
	 * 
	 * @since 9.4.0
	 */
	void deleteAllEvents();
	
	/**
	 * Saves event result
	 * 
	 * @param event
	 * @param result
	 * @since 8.0.0
	 */
	IdmEntityEventDto saveResult(UUID eventId, OperationResultDto result);
	
	/**
	 * Saves event states.
	 * 
	 * @param event
	 * @param previousStates
	 * @param result
	 * @return
	 */
	List<IdmEntityStateDto> saveStates(
			EntityEvent<?> event, 
			List<IdmEntityStateDto> previousStates,
			EventResult<?> result);
	
	/**
	 * Enable given processor. Throws {@link IllegalArgumentException} when processorId is not installed.
	 * 
	 * @param processorId
	 */
	void enable(String processorId);

	/**
	 * Disable given processor. Throws {@link IllegalArgumentException} when processorId is not installed.
	 * 
	 * @param processorId
	 */
	void disable(String processorId);

	/**
	 * Enable / disable given processor.
	 *
	 * @param processorId
	 * @param enabled
	 */
	void setEnabled(String processorId, boolean enabled);
	
	/**
	 * Returns true, if asynchronous event processing is enabled
	 * 
	 * @see EventConfiguration
	 * @return
	 * @since 9.1.0
	 */
	boolean isAsynchronous();
	
	/**
	 * Propagate properties from parent to child event.
	 * Properties need for internal event processing are ignored (see {@link EntityEvent} properties). 
	 * 
	 * @param event
	 * @param parentEvent
	 * @since 9.6.0
	 */
	void propagateProperties(EntityEvent<?> event, EntityEvent<?> parentEvent);
	
	/**
	 * Register long running task executor for nofity on end, when all events created from this executor are completed.
	 * 
	 * @param executor LRT
	 * @return long running task event, when executor is registered. Null - task can end synchronously.
	 * @since 10.6.0
	 */
	IdmEntityEventDto registerAsynchronousTask(LongRunningTaskExecutor<?> executor);

	/**
	 * Creates a manual event (without execute). Manual means we will controlled creation and completion this event manually.
	 * The point for this is control end of main LRT. For example we need to ensure that a sync will be ends after
	 * all provisioning operations will be executed. For that we will create manual event for provisioning operation
	 * and by it we are able manually completed it (-> sync will be ended after complete all manual events).
	 * 
	 * @param entityEvent event to persist
	 * @return persisted event
	 * @since 11.0.0
	 */
	IdmEntityEventDto createManualEvent(IdmEntityEventDto entityEvent);

	/**
	 * Complete a manual event. Manual means we will controlled creation and completion this event manually.
	 * The point for this is control end of main LRT. For example we need to ensure that a sync will be ends after
	 * all provisioning operations will be executed. For that we will create manual event for provisioning operation
	 * and by it we are able manually completed it (-> sync will be ended after complete all manual events).
	 * 
	 * @param entityEvent event to complete
	 * @return persisted event
	 * @since 11.0.0
	 */
	IdmEntityEventDto completeManualEvent(IdmEntityEventDto entityEvent);

	/**
	 * Register long running task executor for nofity on end, when all events created from this executor are completed.
	 * 
	 * @param executor LRT
	 * @return false, when task published some asynchronous events, and this events are not processed yet => lrt cannot be deregistred. True - task can end synchronously.
	 * @since 10.6.0
	 */
	boolean deregisterAsynchronousTask(LongRunningTaskExecutor<?> executor);
	
	/**
	 * Complete event and check all event in the same transaction is completely processed.
	 * If event is last in the same transaction, then notify about end is called for each registered LRT.
	 * Event state is not updated internally.
	 * 
	 * @param event event to complete
	 * @since 10.8.0
	 */
	void completeEvent(IdmEntityEventDto event);
	
	/**
	 * Switch instanceId for processing asynchronous events.
	 * All events created for previous instance will be moved to new instance.
	 * 
	 * @param previousInstanceId previously used instance
	 * @param newInstanceId [optional] currently configured instance will be used as default
	 * @return updated enevents count
	 * @since 11.1.0
	 */
	int switchInstanceId(String previousInstanceId, String newInstanceId);
}
