package eu.bcvsolutions.idm.core.api.event;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.ResolvableTypeProvider;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.domain.PriorityType;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.service.Configurable;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;

/**
 * Event state holder (content + metadata)
 * 
 * @param <E> {@link BaseEntity}, {@link BaseDto} or any other {@link Serializable} content type
 * @author Radek Tomiška
 */
public interface EntityEvent<E extends Serializable> extends ResolvableTypeProvider, Serializable {
	
	String EVENT_PROPERTY = "entityEvent"; // event propagated into WF process variable
	//
	// internal event properties - are not propagated automatically from parent to child event
	String EVENT_PROPERTY_EVENT_ID = "idm:event-id"; // persisted event id
	String EVENT_PROPERTY_EXECUTE_DATE = "idm:execute-date"; // asynchronous event processing time
	String EVENT_PROPERTY_PRIORITY = "idm:priority"; // event priority
	String EVENT_PROPERTY_ROOT_EVENT_ID = "idm:root-event-id"; // root event id
	String EVENT_PROPERTY_PARENT_EVENT_ID = "idm:parent-event-id"; // parent event id
	String EVENT_PROPERTY_PARENT_EVENT_TYPE = "idm:parent-event-type"; // parent event type
	String EVENT_PROPERTY_SUPER_OWNER_ID = "idm:super-owner-id"; // entity event super owner id (e.g. identity (~super owner) - identityRole (event owner))
	String EVENT_PROPERTY_PERMISSION = "idm:permission"; // permission to evaluate (AND)
	String EVENT_PROPERTY_ORIGINAL_SOURCE = "idm:original-source"; // additional original source
	//
	// additional properties - are propagated automatically from parent to child event
	String EVENT_PROPERTY_TRANSACTION_ID = "idm:transaction-id"; // transaction identifier - whole event tree will be under one transaction id.
	
	/**
	 * Operation type
	 * 
	 * @return
	 */
	EventType getType();
	
	/**
	 * Persistent event id. Can be {@code null} if event is not persisted.
	 * 
	 * @return
	 */
	UUID getId();
	
	/**
	 * Persistent event id.
	 * 
	 * @param id
	 */
	void setId(UUID id);
	
	/**
	 * Content's super owner identifier - e.g. event is for identity role, but we want to work with identity as super owner (~batch). 
	 * 
	 * @return
	 */
	UUID getSuperOwnerId();
	
	/**
	 * Content's super owner identifier
	 * 
	 * @param superOwnerId
	 */
	void setSuperOwnerId(UUID superOwnerId);
	
	/**
	 * Persistent root event id. Can be {@code null} if event is root, or all events are not persisted.
	 * If root event is set, then event will be persisted.
	 * 
	 * @return
	 */
	UUID getRootId();
	
	/**
	 * Persistent root event id.
	 * 
	 * @param rootId
	 */
	void setRootId(UUID rootId);
	
	/**
	 * Persistent parent event id. Can be {@code null} if event doesn't have parent, or all events are not persisted.
	 * If parent event is set, then event will be persisted.
	 * 
	 * @return
	 */
	UUID getParentId();
	
	/**
	 * Persistent parent event id.
	 * 
	 * @param parentId
	 */
	void setParentId(UUID parentId);
	
	/**
	 * Parent event type.
	 * 
	 * @return
	 */
	String getParentType();
	
	/**
	 * Parent event type.
	 * 
	 * @param parentType
	 */
	void setParentType(String parentType);
	
	/**
	 * Event execute date. Can be {@code null}, if event is not processed completely.
	 * 
	 * @return
	 */
	ZonedDateTime getExecuteDate();
	
	/**
	 * Event execute date.
	 * 
	 * @param executeDate
	 */
	void setExecuteDate(ZonedDateTime executeDate);
	
	/**
	 * Event priority. 
	 * 
	 * @return
	 */
	PriorityType getPriority();
	
	/**
	 * Event priority.
	 * 
	 * @param priority
	 */
	void setPriority(PriorityType priority);

	/**
	 * Starting event content =~ source entity. Could not be null. Events with empty content could not be processed.
	 *  
	 * @return
	 */
	E getSource();
	
	/**
	 * Event content - entity. Could not be null. Events with empty content could not be processed.
	 *  
	 * @return
	 */
	E getContent();
	
	/**
	 *  Event content - entity. Could not be null. Events with empty content could not be processed.
	 *  
	 * @param content
	 */
	void setContent(E content);
	
	/**
	 * Persisted event content before event starts. Usable in "check modifications" processors.
	 * 
	 * @return
	 */
	E getOriginalSource();
	
	/**
	 * Persisted event content before event starts. Usable in "check modifications" processors.
	 * 
	 * @param originalSource
	 */
	void setOriginalSource(E originalSource);
	
	/**
	 * Event properties (metadata)
	 * 
	 * TODO: ConfigurationMap should be used ... see {@link Configurable}.
	 * 
	 * @return
	 */
	Map<String, Serializable> getProperties();
	
	/**
	 * Event context
	 * 
	 * @return
	 */
	EventContext<E> getContext();
	
	/**
	 * Event is closed = no other events will be processed (break event chain)
	 * 
	 * @return
	 */
	boolean isClosed();
	
	/**
	 * Event is suspended = no other events will be processed, while event is suspended. 
	 * Suspended event could be republished again - when will continue when event was suspended - all processors 
	 * with greater order than getProcessedOrder will be called.
	 * 
	 * @return
	 */
	boolean isSuspended();
	
	/**
	 * Returns last processed order or {@code null}, if any processor was called (event is starting).
	 * 
	 * @return
	 */
	Integer getProcessedOrder();
	
	
	/**
	 * Returns true, if event's type equals given eventType.
	 * 
	 * @param eventType
	 * @return true - event has the same type
	 */
	default boolean hasType(EventType eventType) {
		Assert.notNull(eventType, "Event type is required to compare.");
		//
		return eventType.name().equals(getType().name());
	}
	
	/**
	 * Returns true, if event's priority equals given priority.
	 * 
	 * @param priority priority type
	 * @return true - event has the same priority
	 * @since 11.1.0
	 */
	default boolean hasPriority(PriorityType priority) {
		Assert.notNull(priority, "Priority type is required to compare.");
		//
		return priority == getPriority();
	}

	/**
	 * Event class type. If is not field 'eventClassType' sets (in constructor), then will be used class from content.
	 * Processors with this generic class will be called.
	 * @return
	 */
	Class<? extends E> getEventClassType();
	
	/**
	 * Permissions set to evaluate with this event (AND).
	 * Look out: permissions are not persisted (persistent events are executed under system)
	 * 
	 * @return
	 */
	BasePermission[] getPermission();
	
	/**
	 * Permissions set to evaluate with this event (AND).
	 * Look out: permissions are not persisted (persistent events are executed under system)
	 * 
	 * @param permission
	 */
	void setPermission(BasePermission... permission);
	
	/**
	 * Action / transaction id
	 * 
	 * @return
	 * @since 9.5.0
	 */
	UUID getTransactionId();
	
	/**
	 * Action / transaction id
	 * 
	 * @param transactionId
	 * @since 9.5.0
	 */
	void setTransactionId(UUID transactionId);

	/**
	 * Return list of values from event properties. Returns null, if value doesn't
	 * exists for given property or if value is not instance of List.
	 * 
	 * @param property
	 * @param type
	 * @return
	 */
	<T> List<T> getListProperty(String property, Class<T> type);

	/**
	 * Return true if event properties contains given property and this property is true.
	 * If event does not contains this property, then return false.
	 * 
	 * @param property
	 * @return
	 */
	boolean getBooleanProperty(String property);

	/**
	 * Return set of values from event properties. Returns null, if value doesn't
	 * exists for given property or if value is not instance of Set.
	 * 
	 * @param property
	 * @param type
	 * @return
	 */
	<T> Set<T> getSetProperty(String property, Class<T> type);
}
