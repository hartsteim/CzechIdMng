package eu.bcvsolutions.idm.core.api.service;


import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.audit.service.SiemLoggerManager;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.filter.BaseFilter;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.event.AbstractEntityEventProcessor;
import eu.bcvsolutions.idm.core.api.event.CoreEvent;
import eu.bcvsolutions.idm.core.api.event.CoreEvent.CoreEventType;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventContext;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.repository.AbstractEntityRepository;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;

/**
 * Adds event processing to abstract implementation for generic CRUD operations on a repository for a
 * specific type.
 * Base event processors should be provided (e.g. save, delete).
 * 
 * @author Radek Tomiška
 *
 * @param <DTO> dto type
 * @param <E> entity type
 * @param <F> filter type
 * @see AbstractEntityEventProcessor
 */
public abstract class AbstractEventableDtoService<DTO extends BaseDto, E extends BaseEntity, F extends BaseFilter>
		extends AbstractReadWriteDtoService<DTO, E, F> 
		implements EventableDtoService<DTO, F> {
	
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AbstractEventableDtoService.class);
	//
	private final EntityEventManager entityEventManager;
	
	public AbstractEventableDtoService(
			AbstractEntityRepository<E> repository,
			EntityEventManager entityEventManager
			) {
		super(repository);
		//
		Assert.notNull(entityEventManager, "Event manager is required for eventable service!");
		//
		this.entityEventManager = entityEventManager;
	}
	
	@Override
	@Transactional
	public EventContext<DTO> publish(EntityEvent<DTO> event, BasePermission... permission){
		return publish(event, (EntityEvent<?>) null, permission);
	}
	
	@Override
	@Transactional
	public EventContext<DTO> publish(EntityEvent<DTO> event, EntityEvent<?> parentEvent, BasePermission... permission) {
		Assert.notNull(event, "Event must be not null!");
		Assert.notNull(event.getContent(), "Content (dto) in event must be not null!");
		//
		try {
			checkAccess(toEntity(event.getContent(), null), permission);
			// Set permission into event, if some processor needs additional checks.
			event.setPermission(permission);
			//
			EventContext<DTO> resultContext = entityEventManager.process(event, parentEvent);
			EventResult<DTO> eventResult = resultContext.getLastResult();
			EntityEvent<DTO> newEvent = eventResult == null ? event : eventResult.getEvent();
			siemLog(newEvent, SiemLoggerManager.SUCCESS_ACTION_STATUS, null);
			return resultContext;
		} catch (Exception ex) {
			siemLog(event, SiemLoggerManager.FAILED_ACTION_STATUS, ex.getMessage());
			throw ex;
		}
	}
	
	/**
	 * Publish {@link CoreEvent} only.
	 * 
	 * @see AbstractEntityEventProcessor
	 */
	@Override
	@Transactional
	public DTO save(DTO dto, BasePermission... permission) {
		Assert.notNull(dto, "DTO is required for save.");
		//
		if (isNew(dto)) { // create
			LOG.debug("Saving new dto[{}]", dto);
			return publish(new CoreEvent<DTO>(CoreEventType.CREATE, dto), permission).getContent();
		}
		LOG.debug("Saving dto [{}] ", dto);
		return publish(new CoreEvent<DTO>(CoreEventType.UPDATE, dto), permission).getContent();
	}
	
	/**
	 * Publish {@link CoreEvent} only.
	 * 
	 * @see AbstractEntityEventProcessor
	 */
	@Override
	@Transactional
	public void delete(DTO dto, BasePermission... permission) {
		Assert.notNull(dto, "DTO is required for delete.");
		//
		LOG.debug("Deleting dto [{}]", dto);
		//
		publish(new CoreEvent<DTO>(CoreEventType.DELETE, dto), permission);
	}
	
	/**
	 * Logging method specified for entities propagated by events.
	 * Contains some logic to log interesting entities only.
	 * Provides extraction of some meaningful data for logging.
	 * Has to be implemented in particular services.
	 * 
	 * @param <E>
	 * @param event
	 * @param status
	 * @param reason
	 */
	protected void siemLog(EntityEvent<DTO> event, String status, String detail) {
		// This is default empty implementation of the log method.
		// it has to be overridden in services which are supposed to be logged. 
	}
	
}
