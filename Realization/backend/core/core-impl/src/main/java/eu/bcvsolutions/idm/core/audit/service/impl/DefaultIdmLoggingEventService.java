package eu.bcvsolutions.idm.core.audit.service.impl;

import java.io.Serializable;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import eu.bcvsolutions.idm.core.api.service.AbstractReadWriteDtoService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.audit.dto.IdmLoggingEventDto;
import eu.bcvsolutions.idm.core.api.audit.dto.filter.IdmLoggingEventFilter;
import eu.bcvsolutions.idm.core.api.audit.service.IdmLoggingEventService;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.audit.entity.IdmLoggingEvent;
import eu.bcvsolutions.idm.core.audit.entity.IdmLoggingEvent_;
import eu.bcvsolutions.idm.core.audit.repository.IdmLoggingEventRepository;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;

/**
 * Default implementation of {@link IdmLoggingEventService}
 * 
 * @author Ondrej Kopr <kopr@xyxy.cz>
 *
 */
@Service
public class DefaultIdmLoggingEventService
		extends AbstractReadWriteDtoService<IdmLoggingEventDto, IdmLoggingEvent, IdmLoggingEventFilter>
		implements IdmLoggingEventService {

	private final IdmLoggingEventRepository repository;

	@Autowired
	public DefaultIdmLoggingEventService(IdmLoggingEventRepository repository) {
		super(repository);
		//
		this.repository = repository;
	}

	@Override
	protected List<Predicate> toPredicates(Root<IdmLoggingEvent> root, CriteriaQuery<?> query, CriteriaBuilder builder,
			IdmLoggingEventFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
		//
		if (StringUtils.isNotEmpty(filter.getText())) {
			predicates.add(builder.or(
					builder.like(builder.lower(root.get(IdmLoggingEvent_.formattedMessage)),
							"%" + filter.getText().toLowerCase() + "%"),
					builder.like(builder.lower(root.get(IdmLoggingEvent_.callerFilename)),
							"%" + filter.getText().toLowerCase() + "%"),
					builder.like(builder.lower(root.get(IdmLoggingEvent_.callerMethod)),
							"%" + filter.getText().toLowerCase() + "%"),
					builder.like(builder.lower(root.get(IdmLoggingEvent_.callerClass)),
							"%" + filter.getText().toLowerCase() + "%"),
					builder.like(builder.lower(root.get(IdmLoggingEvent_.loggerName)),
							"%" + filter.getText().toLowerCase() + "%")));
		}
		//
		// caller class
		if (StringUtils.isNotEmpty(filter.getCallerClass())) {
			predicates.add(
					builder.like(root.get(IdmLoggingEvent_.callerClass), "%" + filter.getCallerClass() + "%"));
		}
		//
		if (StringUtils.isNotEmpty(filter.getCallerFilename())) {
			predicates.add(
					builder.like(root.get(IdmLoggingEvent_.callerFilename), "%" + filter.getCallerFilename() + "%"));
		}
		//
		if (StringUtils.isNotEmpty(filter.getCallerLine())) {
			predicates.add(builder.equal(root.get(IdmLoggingEvent_.callerLine), filter.getCallerLine() + "%"));
		}
		//
		if (StringUtils.isNotEmpty(filter.getCallerMethod())) {
			predicates.add(builder.like(root.get(IdmLoggingEvent_.callerMethod), "%" + filter.getCallerMethod() + "%"));
		}
		//
		//
		if (StringUtils.isNotEmpty(filter.getLoggerName())) {
			predicates.add(builder.like(root.get(IdmLoggingEvent_.loggerName), "%" + filter.getLoggerName() + "%"));
		}
		//
		if (filter.getId() != null) {
			predicates.add(builder.equal(root.get(IdmLoggingEvent_.id), filter.getId()));
		}
		//
		if (filter.getLevelString() != null) {
			predicates.add(builder.equal(root.get(IdmLoggingEvent_.levelString), filter.getLevelString()));
		}
		//
		if (filter.getFrom() != null) {
			predicates.add(builder.ge(root.get(IdmLoggingEvent_.timestmp), filter.getFrom().toInstant().toEpochMilli()));
		}
		//
		if (filter.getTill() != null) {
			predicates.add(builder.le(root.get(IdmLoggingEvent_.timestmp), filter.getTill().toInstant().toEpochMilli()));
		}
		return predicates;
	}

	@Override
	public IdmLoggingEventDto get(Serializable id, BasePermission... permission) {
		Assert.notNull(id, "Id is required");
		//
		IdmLoggingEvent entity = this.repository.findOneById(Long.valueOf(id.toString()));
		//
		if (entity == null) {
			throw new ResultCodeException(CoreResultCode.NOT_FOUND, ImmutableMap.of("entity", id));
		}
		//
		return this.toDto(entity);
	}

	@Override
	public AuthorizableType getAuthorizableType() {
		return new AuthorizableType(CoreGroupPermission.AUDIT, null);
	}
	
	@Override
	protected IdmLoggingEvent getEntity(Serializable id, BasePermission... permission) {
		Assert.notNull(id, "Identifier is requiered for load an log event.");
		IdmLoggingEventFilter filter = new IdmLoggingEventFilter();
		filter.setId(Long.valueOf(id.toString()));
		List<IdmLoggingEventDto> entities = this.find(filter, null, permission).getContent();
		if (entities.isEmpty()) {
			return null;
		}
		// for given id must found only one entity
		IdmLoggingEvent entity = this.toEntity(entities.get(0));
		return checkAccess(entity, permission);
	}

	@Override
	@Transactional
	public void deleteAllById(Long eventId) {
		this.repository.deleteById(eventId);
	}

	@Override
	@Transactional
	public int deleteLowerOrEqualTimestamp(Long timestamp) {
		return this.repository.deleteLowerOrEqual(timestamp);
	}
}
