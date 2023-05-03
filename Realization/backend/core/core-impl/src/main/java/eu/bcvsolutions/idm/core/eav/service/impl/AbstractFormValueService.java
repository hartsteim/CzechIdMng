package eu.bcvsolutions.idm.core.eav.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.api.dto.filter.DataFilter;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.service.AbstractReadWriteDtoService;
import eu.bcvsolutions.idm.core.api.service.ConfidentialStorage;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.service.RequestManager;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmFormValueFilter;
import eu.bcvsolutions.idm.core.eav.api.entity.FormableEntity;
import eu.bcvsolutions.idm.core.eav.api.service.FormValueService;
import eu.bcvsolutions.idm.core.eav.entity.AbstractFormValue;
import eu.bcvsolutions.idm.core.eav.entity.AbstractFormValue_;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormAttribute_;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormDefinition_;
import eu.bcvsolutions.idm.core.eav.repository.AbstractFormValueRepository;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;

/**
 * Custom form value service can be registered by spring plugin.
 *
 * @author Radek Tomiška
 *
 * @param <O> values owner
 * @param <E> values entity
 */
public abstract class AbstractFormValueService<O extends FormableEntity, E extends AbstractFormValue<O>>
		extends AbstractReadWriteDtoService<IdmFormValueDto, E, IdmFormValueFilter<O>>
		implements FormValueService<O> {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AbstractFormValueService.class);
	private final Class<O> ownerClass;
	private final Class<E> formValueClass;
	private final AbstractFormValueRepository<O, E> repository;
	//
	@Autowired
	private ConfidentialStorage confidentialStorage;
	@Autowired @Lazy
	private LookupService lookupService;
	@Autowired @Lazy
	private RequestManager requestManager;
	@Autowired @Lazy
	private AttachmentManager attachmentManager;

	@SuppressWarnings("unchecked")
	public AbstractFormValueService(AbstractFormValueRepository<O, E> repository) {
		super(repository);
		//
		Assert.notNull(repository, "Repository is required for service construction.");
		//
		Class<?>[] genericTypes = GenericTypeResolver.resolveTypeArguments(getClass(), AbstractFormValueService.class);
		this.ownerClass = (Class<O>)genericTypes[0];
		this.formValueClass = (Class<E>)genericTypes[1];
		this.repository = repository;
	}

	@Override
	public Class<O> getOwnerClass() {
		return ownerClass;
	}

	public Class<E> getFormValueClass() {
		return formValueClass;
	}

	@Override
	public AuthorizableType getAuthorizableType() {
		return null; // each implementation should be secured itself
	}
	
	@Override
	public boolean supportsToDtoWithFilter() {
		return true;
	}
	
	@Override
	protected IdmFormValueDto toDto(E entity, IdmFormValueDto dto, IdmFormValueFilter<O> context) {
		if (entity == null) {
			return null;
		}
		return lookupService.toDto(entity, dto, context);
	}
	
	@Override
	public IdmFormValueDto map(E entity, IdmFormValueDto dto, DataFilter context) {
		if (context == null) {
			return toDto(entity, dto, null);
		}
		//
		return toDto(entity, dto, new IdmFormValueFilter<O>(context.getData()));
	}

	@Override
	@SuppressWarnings("unchecked")
	protected E toEntity(IdmFormValueDto dto, E entity) {
		entity = super.toEntity(dto, entity);
		// If DTO does not contains a owner entity, then we try to find it by owner type and ID.
		if (dto.getOwner() == null && dto.getOwnerId() != null && dto.getOwnerType() != null) {
			entity.setOwner((O) this.getOwnerEntity((UUID) dto.getOwnerId(), dto.getOwnerType()));
		} else {
			entity.setOwner((O) dto.getOwner());
		}
		return entity;
	}

	@Override
	public boolean supports(Class<?> delimiter) {
		return ownerClass.isAssignableFrom(delimiter);
	}

	/**
	 * Returns entity repository.
	 *
	 * @return
	 */
	protected AbstractFormValueRepository<O, E> getRepository() {
		return repository;
	}
	
	@Override
	@Transactional(readOnly = true)
	public IdmFormValueDto get(Serializable id, BasePermission... permission) {
		IdmFormValueFilter<O> context = new IdmFormValueFilter<>();
		context.setAddSecredProxyString(true); // proxy string by default
		//
		return this.get(id, context, permission);
	}

	/**
	 * Returns entity by given id. Returns null, if entity is not exists. For AbstractEntity uuid or string could be given.
	 */
	@Override
	@Transactional(readOnly = true)
	public IdmFormValueDto get(Serializable id, IdmFormValueFilter<O> context, BasePermission... permission) {
		E formValue = getEntity(id, permission);
		// leave reading from confidential store to client
		if (formValue != null && formValue.isConfidential()) {
			LOG.debug("FormValue [{}] is persisted id confidential storage, returning proxy string only", formValue.getId());
		}
		return toDto(formValue, null, context);
	}

	/**
	 * Saves a given entity. Use the returned instance for further operations as the save operation might have changed the
	 * entity instance completely.
	 *
	 * @param entity
	 * @return the saved entity
	 */
	@Override
	@Transactional
	public IdmFormValueDto saveInternal(IdmFormValueDto dto) {
		Assert.notNull(dto, "DTO is required to be saved.");
		Serializable formValue = dto.getValue();
		//
		E persistedEntity = null;
		if (dto.getId() != null) {
			persistedEntity = this.getEntity(dto.getId());
		}
		// check, if value has to be persisted in confidential storage
		if (dto.isConfidential()) {
			dto.clearValues();
			if (formValue != null) {
				String previousFormValue;
				if (dto.getId() != null) {
					previousFormValue = confidentialStorage.get(
							dto.getId(), 
							getEntityClass(), 
							getConfidentialStorageKey(dto.getFormAttribute()),
							String.class
					);
				} else {
					previousFormValue = null;
				}
				if (Objects.equals(formValue, previousFormValue) && persistedEntity != null) {
					dto.setStringValue(persistedEntity.getStringValue());
					dto.setShortTextValue(persistedEntity.getShortTextValue());
				} else {				
					String proxyValue = String.format("%s-%s", CONFIDENTIAL_STORAGE_VALUE_PREFIX, UUID.randomUUID());
					// we need only to know, if value was filled and when (~ for audit)
					dto.setStringValue(proxyValue);
					dto.setShortTextValue(proxyValue);
				}
			}
		}
		// persist entity
		E entity = getRepository().save(toEntity(dto, persistedEntity));
		//
		// save values to confidential storage
		if (entity.isConfidential()) {
			confidentialStorage.save(entity.getId(), entity.getClass(), getConfidentialStorageKey(entity.getFormAttribute().getId()), formValue);
			LOG.debug("FormValue [{}] is persisted in confidential storage", entity.getId());
		}
		// return actual dto with proxy string
		IdmFormValueFilter<O> context = new IdmFormValueFilter<>();
		context.setAddSecredProxyString(true);
		return toDto(entity, null, context);
	}

	@Override
	@Transactional
	public void deleteInternal(IdmFormValueDto dto) {
		Assert.notNull(dto, "DTO is required to delete.");
		UUID id = dto.getId();
		Assert.notNull(id, "DTO identifier is required to delete.");
		//
		LOG.debug("FormValue [{}] will be removed", id);
		if (dto.isConfidential()) {
			LOG.debug("FormValue [{}] will be removed from confidential storage", id);
			confidentialStorage.delete(id, toEntity(dto).getClass(), getConfidentialStorageKey(dto.getFormAttribute()));
		}
		//
		// Cancel requests and request items using that deleting DTO
		requestManager.onDeleteRequestable(dto);
		//
		// remove attachments for this form value
		if (PersistentType.ATTACHMENT == dto.getPersistentType()) {
			attachmentManager.deleteAttachments(id, attachmentManager.getOwnerType(toEntity(dto).getClass()));
		}
		//
		super.deleteInternal(dto);
	}

	@Override
	protected List<Predicate> toPredicates(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder builder,IdmFormValueFilter<O> filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
		//
		String text = filter.getText();
		if (StringUtils.isNotEmpty(text)) {
			text = text.toLowerCase();
			predicates.add(builder.or(
					builder.like(builder.lower(root.get(AbstractFormValue_.formAttribute).get(IdmFormAttribute_.code)), "%" + text + "%"),
					builder.like(builder.lower(root.get(AbstractFormValue_.formAttribute).get(IdmFormAttribute_.name)), "%" + text + "%")
			));
		}
		//
		PersistentType persistentType = filter.getPersistentType();
		if (persistentType != null) {
			predicates.add(builder.equal(root.get(AbstractFormValue_.persistentType), persistentType));
		}
		//
		UUID definitionId = filter.getDefinitionId();
		if (definitionId != null) {
			predicates.add(builder.equal(root.get(AbstractFormValue_.formAttribute).get(IdmFormAttribute_.formDefinition).get(IdmFormDefinition_.id), definitionId));
		}
		//
		List<UUID> attributeIds = filter.getAttributeIds();
		if (CollectionUtils.isNotEmpty(attributeIds)) {
			predicates.add(root.get(AbstractFormValue_.formAttribute).get(IdmFormAttribute_.id).in(attributeIds));
		}
		//
		O owner = filter.getOwner();
		if (owner != null) {
			// by id - owner doesn't need to be persisted
			Serializable ownerId = owner.getId();
			if (ownerId != null) {
				predicates.add(builder.equal(root.get(FormValueService.PROPERTY_OWNER).get(BaseEntity.PROPERTY_ID), ownerId));
			}
		}
		//
		String stringValue = filter.getStringValue();
		if (StringUtils.isNotEmpty(stringValue)) {
			predicates.add(builder.equal(root.get(AbstractFormValue_.stringValue), stringValue));
		}
		//
		String shortTextValue = filter.getShortTextValue();
		if (StringUtils.isNotEmpty(shortTextValue)) {
			predicates.add(builder.equal(root.get(AbstractFormValue_.shortTextValue), shortTextValue));
		}
		//
		String stringValueLike = filter.getStringValueLike();
		if (StringUtils.isNotEmpty(stringValueLike)) {
			predicates.add(builder.like(builder.lower(root.get(AbstractFormValue_.stringValue)), "%" + stringValueLike.toLowerCase() + "%"));
		}
		//
		String shortTextValueLike = filter.getShortTextValueLike();
		if (StringUtils.isNotEmpty(shortTextValueLike)) {
			predicates.add(builder.like(builder.lower(root.get(AbstractFormValue_.shortTextValue)), "%" + shortTextValueLike.toLowerCase() + "%"));
		}
		//
		Boolean booleanValue = filter.getBooleanValue();
		if (booleanValue != null) {
			predicates.add(builder.equal(root.get(AbstractFormValue_.booleanValue), booleanValue));
		}
		//
		Long longValue = filter.getLongValue();
		if (longValue != null) {
			predicates.add(builder.equal(root.get(AbstractFormValue_.longValue), longValue));
		}
		//
		BigDecimal doubleValue = filter.getDoubleValue();
		if (doubleValue != null) {
			predicates.add(builder.equal(root.get(AbstractFormValue_.doubleValue), doubleValue));
		}
		//
		ZonedDateTime dateValue = filter.getDateValue();
		if (dateValue != null) {
			predicates.add(builder.equal(root.get(AbstractFormValue_.dateValue), dateValue));
		}
		ZonedDateTime dateValueFrom = filter.getDateValueFrom();
		if (dateValueFrom != null) {
			predicates.add(builder.greaterThanOrEqualTo(root.get(AbstractFormValue_.dateValue), dateValueFrom));
		}
		ZonedDateTime dateValueTill = filter.getDateValueTill();
		if (dateValueTill != null) {
			predicates.add(builder.lessThanOrEqualTo(root.get(AbstractFormValue_.dateValue), dateValueTill));
		}
		//
		UUID uuidValue = filter.getUuidValue();
		if (uuidValue != null) {
			predicates.add(builder.equal(root.get(AbstractFormValue_.uuidValue), uuidValue));
		}
		//
		return predicates;
	}

	@Override
	@Transactional(readOnly = true)
	public List<IdmFormValueDto> getValues(O owner, IdmFormDefinitionDto formDefinition, BasePermission... permission) {
		Assert.notNull(owner, "Owner is required to get form values.");
		Assert.notNull(owner.getId(), "Owner identifier is required to get form values.");
		//
		IdmFormValueFilter<O> filter = new IdmFormValueFilter<>();
		filter.setOwner(owner);
		if (formDefinition != null) {
			filter.setDefinitionId(formDefinition.getId());
		}
		return find(filter, PageRequest.of(0, Integer.MAX_VALUE, Sort.by(AbstractFormValue_.seq.getName())), permission).getContent();
	}

	@Override
	@Transactional(readOnly = true)
	public List<IdmFormValueDto> getValues(O owner, IdmFormAttributeDto attribute, BasePermission... permission) {
		Assert.notNull(owner, "Owner is required to get form values.");
		Assert.notNull(owner.getId(), "Owner identifier is required to get form values.");
		Assert.notNull(attribute, "Form attribute definition is required!");
		//
		IdmFormValueFilter<O> filter = new IdmFormValueFilter<>();
		filter.setOwner(owner);
		filter.setAttributeId(attribute.getId());
		//
		return find(filter, PageRequest.of(0, Integer.MAX_VALUE, Sort.by(AbstractFormValue_.seq.getName())), permission).getContent();
	}

	@Override
	@Transactional(readOnly = true)
	public Page<IdmFormValueDto> find(IdmFormValueFilter<O> filter, Pageable pageable, BasePermission... permission) {
		return super.find(filter, pageable, permission);
	}

	@Transactional
	public void deleteValues(O owner, IdmFormDefinitionDto formDefinition, BasePermission... permission) {
		getValues(owner, formDefinition).forEach(formValue -> {
			delete(formValue, permission);
		});
	}

	@Transactional
	public void deleteValues(O owner, IdmFormAttributeDto attribute, BasePermission... permission) {
		Assert.notNull(attribute, "Form attribute definition is required!");
		//
		getValues(owner, attribute).forEach(formValue -> {
			delete(formValue, permission);
		});
	}

	@Override
	public String getConfidentialStorageKey(UUID formAttributeId) {
		Assert.notNull(formAttributeId, "For attribute identifier is required to get confidential storage key.");
		//
		return CONFIDENTIAL_STORAGE_VALUE_PREFIX + ":" + formAttributeId;
	}

	@Override
	public Serializable getConfidentialPersistentValue(IdmFormValueDto guardedValue) {
		Assert.notNull(guardedValue, "Form value holding the confidential falue is required to get them.");
		//
		return confidentialStorage.get(guardedValue.getId(), getEntityClass(), getConfidentialStorageKey(guardedValue.getFormAttribute()));
	}

	@Override
	@Transactional(readOnly = true)
	@SuppressWarnings("deprecation")
	public Page<O> findOwners(IdmFormAttributeDto attribute, Serializable persistentValue, Pageable pageable) {
		Assert.notNull(attribute, "Attribute is required.");
		if (pageable == null) {
			// pageable is required now
			pageable = PageRequest.of(0, Integer.MAX_VALUE);
		}
		//
		if (persistentValue == null) {
			LOG.debug("Null values are not stored (null form values are removed from repository). Owners cannot be found by 'null' persistent value.");
			//
			return new PageImpl<>(new ArrayList<>(), pageable, 0);
		}
		//
		IdmFormValueDto value = new IdmFormValueDto(attribute);
		value.setValue(persistentValue);
		AbstractFormValueRepository<O, E> repository = getRepository();
		//
		switch (attribute.getPersistentType()) {
			case INT:
			case LONG:
				return repository.findOwnersByLongValue(attribute.getId(), value.getLongValue(), pageable);
			case BOOLEAN:
				return repository.findOwnersByBooleanValue(attribute.getId(), value.getBooleanValue(), pageable);
			case DATE:
			case DATETIME:
				return repository.findOwnersByDateValue(attribute.getId(), value.getDateValue(), pageable);
			case DOUBLE:
				return repository.findOwnersByDoubleValue(attribute.getId(), value.getDoubleValue(), pageable);
			case BYTEARRAY: {
				return repository.findOwnersByByteArrayValue(attribute.getId(), value.getByteValue(), pageable);
			}
			case ATTACHMENT:
			case UUID: {
				return repository.findOwnersByUuidValue(attribute.getId(), value.getUuidValue(), pageable);
			}
			case SHORTTEXT:
			case CODELIST: {
				return repository.findOwnersByShortTextValue(attribute.getId(), value.getShortTextValue(), pageable);
			}
			// texts
			default:
				return repository.findOwnersByStringValue(attribute.getId(), value.getStringValue(), pageable);
		}
	}

	/**
	 * Returns owner entity by given id and type
	 *
	 * @param ownerId
	 * @param ownerType
	 * @return
	 */
	private O getOwnerEntity(UUID ownerId, Class<? extends Identifiable> ownerType) {
		Assert.notNull(ownerId, "Form values owner id is required!");
		Assert.notNull(ownerType, "Form values owner type is required!");
		@SuppressWarnings("unchecked")
		O owner = (O) lookupService.lookupEntity(ownerType, ownerId);
		Assert.notNull(owner, "Form values owner is required!");
		//
		return owner;
	}
}
