package eu.bcvsolutions.idm.core.audit.service.impl;

import java.io.Serializable;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import eu.bcvsolutions.idm.core.api.service.AbstractReadWriteDtoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.audit.dto.IdmLoggingEventPropertyDto;
import eu.bcvsolutions.idm.core.api.audit.dto.filter.IdmLoggingEventPropertyFilter;
import eu.bcvsolutions.idm.core.api.audit.service.IdmLoggingEventPropertyService;
import eu.bcvsolutions.idm.core.audit.entity.IdmLoggingEventProperty;
import eu.bcvsolutions.idm.core.audit.entity.IdmLoggingEventProperty_;
import eu.bcvsolutions.idm.core.audit.entity.key.IdmLoggingEventPropertyPrimaryKey;
import eu.bcvsolutions.idm.core.audit.repository.IdmLoggingEventPropertyRepository;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;

@Service
public class DefaultIdmLoggingEventPropertyService extends
		AbstractReadWriteDtoService<IdmLoggingEventPropertyDto, IdmLoggingEventProperty, IdmLoggingEventPropertyFilter>
		implements IdmLoggingEventPropertyService {

	private final IdmLoggingEventPropertyRepository repository;

	@Autowired
	public DefaultIdmLoggingEventPropertyService(IdmLoggingEventPropertyRepository repository) {
		super(repository);
		//
		this.repository = repository;
	}

	@Override
	protected List<Predicate> toPredicates(Root<IdmLoggingEventProperty> root, CriteriaQuery<?> query,
										   CriteriaBuilder builder, IdmLoggingEventPropertyFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);

		if (filter.getEventId() != null) {
			predicates.add(builder.equal(root.get(IdmLoggingEventProperty_.eventId), filter.getEventId()));
		}

		if (filter.getMappedKey() != null) {
			predicates.add(builder.equal(root.get(IdmLoggingEventProperty_.mappedKey), filter.getMappedKey()));
		}

		if (filter.getMappedValue() != null) {
			predicates.add(builder.equal(root.get(IdmLoggingEventProperty_.mappedValue), filter.getMappedValue()));
		}

		return predicates;
	}

	@Override
	protected IdmLoggingEventPropertyDto toDto(IdmLoggingEventProperty entity) {
		IdmLoggingEventPropertyDto dto = new IdmLoggingEventPropertyDto();
		dto.setEventId(entity.getEventId());
		dto.setMappedKey(entity.getMappedKey());
		dto.setMappedValue(entity.getMappedValue());
		return dto;
	}

	@Override
	protected IdmLoggingEventProperty toEntity(IdmLoggingEventPropertyDto dto) {
		IdmLoggingEventProperty entity = new IdmLoggingEventProperty();
		entity.setEventId(dto.getEventId());
		entity.setMappedKey(dto.getMappedKey());
		entity.setMappedValue(dto.getMappedValue());
		return entity;
	}

	@Override
	protected IdmLoggingEventProperty getEntity(Serializable id, BasePermission... permission) {
		Assert.notNull(id, "Identifier is requiered for load an log event property.");
		Assert.isTrue(id instanceof IdmLoggingEventPropertyPrimaryKey, "Identifier has to generalize IdmLoggingEventPropertyPrimaryKey.");
		IdmLoggingEventPropertyPrimaryKey primaryKey = (IdmLoggingEventPropertyPrimaryKey) id;
		//
		IdmLoggingEventPropertyFilter filter = new IdmLoggingEventPropertyFilter();
		filter.setEventId(primaryKey.getEventId());
		filter.setMappedKey(primaryKey.getMappedKey());
		//
		List<IdmLoggingEventPropertyDto> entities = this.find(filter, null).getContent();
		if (entities.isEmpty()) {
			return null;
		}
		//
		return checkAccess(this.toEntity(entities.get(0)), permission);
	}

	@Override
	public AuthorizableType getAuthorizableType() {
		return new AuthorizableType(CoreGroupPermission.AUDIT, null);
	}

	@Override
	@Transactional
	public void deleteAllByEventId(Long eventId) {
		this.repository.deleteByEventId(eventId);
	}

}
