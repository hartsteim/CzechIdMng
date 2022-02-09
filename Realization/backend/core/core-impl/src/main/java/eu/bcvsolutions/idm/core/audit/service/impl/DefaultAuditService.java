package eu.bcvsolutions.idm.core.audit.service.impl;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.exception.RevisionDoesNotExistException;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.hibernate.envers.query.criteria.AuditConjunction;
import org.hibernate.envers.query.criteria.AuditDisjunction;
import org.hibernate.envers.query.criteria.AuditProperty;
import org.hibernate.envers.query.criteria.MatchMode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.audit.criteria.IdmPasswordSelfRelationWithOwnerExpression;
import eu.bcvsolutions.idm.core.api.audit.dto.IdmAuditDto;
import eu.bcvsolutions.idm.core.api.audit.dto.IdmAuditEntityDto;
import eu.bcvsolutions.idm.core.api.audit.dto.filter.IdmAuditFilter;
import eu.bcvsolutions.idm.core.api.audit.service.IdmAuditService;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.AbstractReadWriteDtoService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.utils.FilterConverter;
import eu.bcvsolutions.idm.core.audit.entity.IdmAudit;
import eu.bcvsolutions.idm.core.audit.entity.IdmAudit_;
import eu.bcvsolutions.idm.core.audit.repository.IdmAuditRepository;
import eu.bcvsolutions.idm.core.model.entity.IdmPassword;
import eu.bcvsolutions.idm.core.model.entity.IdmPassword_;
import eu.bcvsolutions.idm.core.model.repository.listener.IdmAuditListener;
import eu.bcvsolutions.idm.core.model.repository.listener.IdmAuditStrategy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;

/**
 * Implementation of service for auditing.
 * 
 * @see IdmAuditListener
 * @see IdmAudit
 * @see IdmAuditStrategy
 * @author Ondrej Kopr
 *
 */
@Service
public class DefaultAuditService extends AbstractReadWriteDtoService<IdmAuditDto, IdmAudit, IdmAuditFilter>
		implements IdmAuditService {
	
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DefaultAuditService.class);

	@Qualifier("entityManager")
	@PersistenceContext()
	private EntityManager entityManager;
	private final IdmAuditRepository auditRepository;
	@LazyCollection(LazyCollectionOption.TRUE)
	private List<String> allAuditedEntititesNames;
	private FilterConverter filterConverter;
	@Autowired(required = false)
	private ObjectMapper mapper;
	@Autowired
	private LookupService lookupService;
	
	@Autowired
	public DefaultAuditService(IdmAuditRepository auditRepository) {
		super(auditRepository);
		//
		this.auditRepository = auditRepository;
	}

	@Override
	public boolean supportsToDtoWithFilter() {
		return true;
	}

	@Override
	protected List<Predicate> toPredicates(Root<IdmAudit> root, CriteriaQuery<?> query, CriteriaBuilder builder,
			IdmAuditFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);

		// Id in audit is long
		// TODO: id - use DataFilter super class some way (long id ...). 
		if (filter.getId() != null) {
			predicates.add(builder.equal(root.get(IdmAudit_.id), filter.getId()));
		}
		// TODO: transaction id - use DataFilter super class some way (long id ...). 
		UUID transactionId = filter.getTransactionId();
		if (transactionId != null) {
			predicates.add(builder.equal(root.get(IdmAudit_.transactionId), transactionId));
		}
		// Text filtering is by id and ownerCode
		String text = filter.getText();
		if (StringUtils.isNotEmpty(text)) {
			text = text.toLowerCase();
			//
			predicates.add(
					builder.or(
							builder.like(root.get(IdmAudit_.id).as(String.class), "%" + text + "%"),
							builder.like(root.get(IdmAudit_.ownerCode), "%" + text + "%")
					)
			);
		}

		if (StringUtils.isNotEmpty(filter.getModification())) {
			predicates.add(builder.equal(root.get(IdmAudit_.modification), filter.getModification()));
		}

		List<String> changedAttributes = filter.getChangedAttributesList();
		if (changedAttributes != null && !changedAttributes.isEmpty()) {
			List<Predicate> orPredicates = new ArrayList<>(changedAttributes.size());
			for (String attribute : changedAttributes) {
				orPredicates.add(builder.like(builder.lower(root.get(IdmAudit_.changedAttributes)), "%" + attribute.toLowerCase() + "%"));
			}
			predicates.add(builder.or(orPredicates.toArray(new Predicate[orPredicates.size()])));
		}

		if (StringUtils.isNotEmpty(filter.getModifier())) {
			predicates.add(builder.equal(root.get(IdmAudit_.modifier), filter.getModifier()));
		}

		if (filter.getEntityId() != null) {
			predicates.add(builder.equal(root.get(IdmAudit_.entityId), filter.getEntityId()));
		}

		if (filter.getFrom() != null) {
			predicates.add(builder.greaterThanOrEqualTo(root.get(IdmAudit_.timestamp), filter.getFrom().toInstant().toEpochMilli()));
		}
		
		if (filter.getTill() != null) {
			predicates.add(builder.lessThanOrEqualTo(root.get(IdmAudit_.timestamp), filter.getTill().toInstant().toEpochMilli()));
		}
		//
		List<String> types = filter.getTypes();
		if (CollectionUtils.isNotEmpty(types)) {
			predicates.add(root.get(IdmAudit_.type).in(types));
		}
		//
		if (StringUtils.isNotEmpty(filter.getOwnerCode())) {
			predicates.add(builder.equal(root.get(IdmAudit_.ownerCode), filter.getOwnerCode()));
		}

		if (StringUtils.isNotEmpty(filter.getOwnerType())) {
			predicates.add(builder.equal(root.get(IdmAudit_.ownerType), filter.getOwnerType()));
		}

		if (StringUtils.isNotEmpty(filter.getOwnerId())) {
			predicates.add(builder.equal(root.get(IdmAudit_.ownerId), filter.getOwnerId()));
		}

		if (filter.getOwnerIds() != null && !filter.getOwnerIds().isEmpty()) {
			predicates.add(root.get(IdmAudit_.ownerId).in(filter.getOwnerIds()));
		}
		
		if (StringUtils.isNotEmpty(filter.getSubOwnerCode())) {
			predicates.add(builder.equal(root.get(IdmAudit_.subOwnerCode), filter.getSubOwnerCode()));
		}

		if (StringUtils.isNotEmpty(filter.getSubOwnerId())) {
			predicates.add(builder.equal(root.get(IdmAudit_.subOwnerId), filter.getSubOwnerId()));
		}

		if (StringUtils.isNotEmpty(filter.getSubOwnerType())) {
			predicates.add(builder.equal(root.get(IdmAudit_.subOwnerType), filter.getSubOwnerType()));
		}
		
		UUID relatedOwnerId = filter.getRelatedOwnerId();
		if (relatedOwnerId != null) {
			predicates.add(
					builder.or(
							builder.equal(root.get(IdmAudit_.entityId), relatedOwnerId),
							builder.equal(root.get(IdmAudit_.ownerId), relatedOwnerId.toString()),
							builder.equal(root.get(IdmAudit_.subOwnerId), relatedOwnerId.toString())
					)
			);
		}
		//
		return predicates;
	}

	@Override
	protected IdmAuditDto toDto(IdmAudit entity, IdmAuditDto dto, IdmAuditFilter filter) {
		if (filter != null && BooleanUtils.isTrue(filter.getWithVersion())) {

			Class<?> forName;
			try {
				forName = Class.forName(entity.getType());
			} catch (ClassNotFoundException e) {
				LOG.warn("Class for type [{}], doesn't exists.", entity.getType(), e);
				return super.toDto(entity, dto, filter);
			}

			Object findVersion = findVersion(forName, entity.getEntityId(), Long.valueOf(entity.getId().toString()));

			// For delete operation is current version null, we must find the last one
			if (findVersion == null) {
				findVersion = this.findPreviousVersion(Long.valueOf(entity.getId().toString()));
			}
			if (findVersion != null) {
				IdmAuditEntityDto newDto = (IdmAuditEntityDto) super.toDto(entity, new IdmAuditEntityDto(), filter);
				newDto.setEntity(getValuesFromVersion(findVersion));
				return newDto;
			}
		}

		return super.toDto(entity, dto, filter);
	}

	@Override
	public <T> T findRevision(Class<T> classType, UUID entityId, Long revisionNumber) throws RevisionDoesNotExistException  {
		return this.find(classType, entityId, revisionNumber);
	}
	
	private AuditReader getAuditReader() {
		return AuditReaderFactory.get(entityManager);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T findPreviousVersion(T entity, Long currentRevId) {
		IdmAuditDto previousRevision = this.findPreviousRevision(currentRevId);

	    if (previousRevision != null) {
	        return (T) this.find(entity.getClass(), (UUID)((BaseEntity) entity).getId(), Long.valueOf(previousRevision.getId().toString()));
	    } else {
	        return null;
	    }
	}
	
	@Override
	public <T> List<IdmAuditDto> findRevisions(Class<T> classType, UUID entityId) {
		IdmAuditFilter filter = new IdmAuditFilter();
		filter.setEntityId(entityId);
		filter.setType(classType.getName());
		Pageable page = PageRequest.of(0, Integer.MAX_VALUE, Direction.ASC, "timestamp", "id");
		Page<IdmAuditDto> result = this.find(filter, page);
		return result.getContent();
	}
	
	@Override
	public <T> T findPreviousVersion(Class<T> entityClass, UUID entityId, Long currentRevisionId) {
		IdmAuditDto previousRevision = this.findPreviousRevision(currentRevisionId);

	    if (previousRevision != null) {
	        return this.find(entityClass, entityId, Long.valueOf(previousRevision.getId().toString()));
	    }
	    return null;
	}
	
	@Override
	public Object findPreviousVersion(Long currentRevisionId) {
		IdmAuditDto revision = this.get(currentRevisionId);
		
		Object result = null;

		IdmAuditDto previousRevision;
		try {
			previousRevision = this.findPreviousRevision(currentRevisionId);
			
			if (previousRevision != null) {
				result = this.find(Class.forName(previousRevision.getType()), previousRevision.getEntityId(), previousRevision.getId());
		    }
		} catch (ClassNotFoundException e) {
			throw new ResultCodeException(CoreResultCode.NOT_FOUND, ImmutableMap.of("class", revision.getType()), e);
		}
		return result;
	}
	
	@Override
	public <T> T findVersion(Class<T> entityClass, UUID entityId, Long currentRevId) {
		return this.find(entityClass, entityId, currentRevId);
	}

	@Override
	public Page<IdmAuditDto> findRevisionsForEntity(String entityClass, UUID entityId, Pageable pageable) {
		IdmAuditFilter filter = new IdmAuditFilter();
		filter.setType(entityClass);
		filter.setEntityId(entityId);
		return this.find(filter, pageable);
	}

	@Override
	public List<String> getAllAuditedEntitiesNames() {
		// load from 'cache'
		// TODO: disable or enable modules?
		if (this.allAuditedEntititesNames != null) {
			// return this.allAuditedEntititesNames;
		}
		//
		List<String> result = new ArrayList<>();
		Set<EntityType<?>> entities = entityManager.getMetamodel().getEntities();
		for (EntityType<?> entityType : entities) {
			if (entityType.getJavaType() == null) {
				continue;
			}
			// get entities methods and search annotation Audited in fields.
			if (getAuditedField(entityType.getJavaType().getDeclaredFields())) {
				result.add(entityType.getJavaType().getCanonicalName());
				continue;
			}
			//
			// TODO: add some better get of all class annotations
			Annotation[] annotations = null;
			try {
				annotations = entityType.getJavaType().getDeclaredConstructor().newInstance().getClass().getAnnotations();
			} catch (ReflectiveOperationException e) {
				// class is not accessible
				continue;
			}
			// entity can be annotated for all class
			if (getAuditedAnnotation(annotations)) {
				result.add(entityType.getJavaType().getCanonicalName());
				continue;
			}
		}
		// sort entities by name
		Collections.sort(result);
		//
		this.allAuditedEntititesNames = result;
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	public <T> Number findLastRevisionNumber(Class<T> entityClass, UUID entityId) {
		Object[] auditWrapper = findLastVersionWrapper(entityClass, entityId, true);
		if (auditWrapper == null) {
			return null;
		}
		//
		IdmAudit auditDto = (IdmAudit) auditWrapper[1];
		//
		return (Long) auditDto.getId();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	@Transactional(readOnly = true)
	public <T> T findLastPersistedVersion(Class<T> entityClass, UUID entityId) {
		Object[] auditWrapper = findLastVersionWrapper(entityClass, entityId, false);
		if (auditWrapper == null) {
			return null;
		}
		//
		return (T) auditWrapper[0];
	}
	
	/**
	 * Find last persisted version with revision information.
	 * E.g. when entity was already deleted, when last version its the last update / create.
	 * 
	 * @param entityClass entity type
	 * @param entityId entity identifier
	 * @param selectDeletedEntities If true, also revisions where entities were deleted will be returned. The additional entities will have revision type "delete", and contain no data (all fields null), except for the id field.
	 * @since 11.1.0
	 */
	@SuppressWarnings("rawtypes")
	private Object[] findLastVersionWrapper(Class<?> entityClass, UUID entityId, boolean selectDeletedEntities) {
		List resultWrapper = this.getAuditReader()
				.createQuery()
			    .forRevisionsOfEntity(entityClass, false, selectDeletedEntities)
			    .add(AuditEntity.id().eq(entityId))
			    .addOrder(AuditEntity.revisionNumber().desc())
			    .setMaxResults(1)
			    .getResultList();
		if (resultWrapper.isEmpty()) {
			return null;
		}
		//
		return (Object[]) resultWrapper.get(0);
	}
	
	/**
	 * Is necessary to override method get, because old get transform id to UUID and audits have Long ID.
	 * 
	 * @param id
	 * @return
	 */
	@Override
	public IdmAuditDto get(Serializable id, BasePermission... permission) {
		// TODO: add permission, now can't be use find, because Authentication object is null when call from IdmAuditLisener
		Assert.notNull(id, "Id is required");
		IdmAudit audit = this.auditRepository.findOneById(Long.valueOf(id.toString()));

		if (audit == null) {
			throw new ResultCodeException(CoreResultCode.NOT_FOUND, ImmutableMap.of("audit", id));
		}
		// return only one element
		return this.toDto(audit);
	}
	
	@Override
	public Map<String, Object> getDiffBetweenVersion(String clazz, Long firstRevId, Long secondRevId) {
		Map<String, Object> result = new HashMap<>();
		IdmAuditDto firstRevision = this.get(firstRevId);
		// first revision must exist
		if (firstRevision == null) {
			throw new ResultCodeException(CoreResultCode.NOT_FOUND, ImmutableMap.of("audit", firstRevId));
		}
		
		if (clazz == null) {
			clazz = firstRevision.getType();
		}
		IdmAuditDto secondRevision = null;
		
		if (secondRevId == null) {
			try {
				secondRevision = (IdmAuditDto)this.findPreviousVersion(Class.forName(clazz), firstRevision.getEntityId(), Long.valueOf(firstRevision.getId().toString()));
			} catch (NumberFormatException e) {
				throw new ResultCodeException(CoreResultCode.BAD_VALUE, ImmutableMap.of("audit", firstRevision.getId()), e);
			} catch (ClassNotFoundException e) {
				throw new ResultCodeException(CoreResultCode.NOT_FOUND, ImmutableMap.of("audit class", clazz), e);
			}
		} else {
			secondRevision = this.get(secondRevId);
		}
		// check if we have second revision
		if (secondRevision == null) {
			throw new ResultCodeException(CoreResultCode.NOT_FOUND, ImmutableMap.of("audit", secondRevId));
		}
		
		// check if revision are from same type (class, entity id)
		if (!firstRevision.getEntityId().equals(secondRevision.getEntityId())) {
			throw new ResultCodeException(CoreResultCode.AUDIT_REVISION_NOT_SAME, ImmutableMap.of("revision", clazz));
		}
		
		// now we will receive version from audit tables
		try {
			Object firstVersion = this.getRevision(clazz, firstRevision.getEntityId(), firstRevId);
			Object secondVersion = this.getRevision(clazz, secondRevision.getEntityId(), secondRevId);
			
			List<String> auditedClass = this.getAllAuditedEntitiesNames();
			
			Map<String, Object> firstValues = null;
			Map<String, Object> secondValues = null;
			
			// first revision is DEL, all attributes are null
			if (firstRevision.getModification().equals(RevisionType.DEL.name())) {
				firstValues = Collections.emptyMap();
			} else {
				firstValues = this.getValuesFromVersion(firstVersion, auditedClass);
			}
			
			if (secondRevision.getModification().equals(RevisionType.DEL.name())) {
				secondValues = Collections.emptyMap();
			} else {
				secondValues = this.getValuesFromVersion(secondVersion, auditedClass);
			}
			
			Set<Entry<String, Object>> entries = firstValues.entrySet();
			
			if (entries.isEmpty()) {
				entries = secondValues.entrySet();
			}
			
			for (Entry<String, Object> entry : entries) {
				if (!Objects.equals(entry.getValue(), secondValues.get(entry.getKey()))) {
					result.put(entry.getKey(), secondValues.get(entry.getKey()));
				}
			}
			
		} catch (ClassNotFoundException e) {
			throw new ResultCodeException(CoreResultCode.NOT_FOUND, ImmutableMap.of("audit class", clazz), e);
		}
		
		return result;
	}
	
	public Map<String, Object> getDiffBetweenVersion(Long firstRevId, Long secondRevId) {
		return this.getDiffBetweenVersion(null, firstRevId, secondRevId);
	}
	
	@Override
	public Map<String, Object> getValuesFromVersion(Object revisionObject) {
		return this.getValuesFromVersion(revisionObject, this.getAllAuditedEntitiesNames());
	}
	
	@Override
	public Map<String, Object> getValuesFromVersion(Object revisionObject, List<String> auditedClass) {
		Map<String, Object> revisionValues = new HashMap<>();
		if (revisionObject == null) {
			return Collections.emptyMap();
		}
		
		// for better debug and readable there is no stream
		// we cannot use Introspector.getBeanInfo
		
		// getAllFieldsList get all field also with field from superclass
		List<Field> fields = FieldUtils.getAllFieldsList(revisionObject.getClass());
		
		for (Field field : fields) {
			try {
				
				// check if field has Audited annotation, or class
				if (!field.isAnnotationPresent(Audited.class) && !field.getDeclaringClass().isAnnotationPresent(Audited.class)) {
					continue;
				}
				//
				PropertyDescriptor propertyDescriptor = PropertyUtils.getPropertyDescriptor(revisionObject, field.getName());
				//
				// get property descriptor for readMethod
				if (propertyDescriptor == null) {
					continue;
				}
				//
				Method readMethod = propertyDescriptor.getReadMethod();
				Object value = readMethod.invoke(revisionObject);
				//
				// value can be null, but we want it
				if (value == null) {
					revisionValues.put(field.getName(), null);
					continue;
				}
				//
				LazyInitializer hibernateLI = null;
				String className = null;
				if (value instanceof HibernateProxy) {
                    HibernateProxy proxy = (HibernateProxy) value;
                    hibernateLI = proxy.getHibernateLazyInitializer();
                    className = hibernateLI.getEntityName();
                }
				// we have all audited class, then some not audited class (binding) and others primitive types
				if (className != null) {
					// get id from hibernate lazy initializer, entity may no longer exist, but ID in DB is always
					revisionValues.put(field.getName(), hibernateLI.getIdentifier());
				} else {
					revisionValues.put(field.getName(), value);
				}
				//
			} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
				throw new ResultCodeException(CoreResultCode.BAD_REQUEST, ImmutableMap.of("field", field.getName()), e);
			}
		}
		
		return revisionValues;
	}
	
	@Override
	public IdmAuditDto findPreviousRevision(IdmAuditDto revision) {
		Assert.notNull(revision, MessageFormat.format("DefaultAuditService: method getPreviousRevision - current revision [{0}] can't be null.", revision));
		
		if (revision.getEntityId() == null) {
			return null;
		}
		
		return toDto(this.auditRepository.getPreviousVersion(revision.getEntityId(), revision.getId()));
	}
	
	@Override
	public IdmAuditDto findPreviousRevision(Long revisionId) {
		Assert.notNull(revisionId, MessageFormat.format("DefaultAuditService: method getPreviousRevision - current revision id [{0}] can't be null.", revisionId));
		return this.findPreviousRevision(this.get(revisionId));
	}

	@Override
	public Page<IdmAuditDto> findLogin(IdmAuditFilter filter, Pageable pageable) {
		// TODO: this behavior is much faster than search audit and then get request for version
		// it will be nice if this will be used in eq identity role audit
		//
		if (pageable == null) {
			// pageable is required noe
			pageable = PageRequest.of(0, Integer.MAX_VALUE);
		}
		// Create audit query for specific login audit
		// Conjunction solve connection between successful and failed query
		AuditConjunction conjunction = AuditEntity.conjunction();
		// Disjunctions solves connection between each query condition, there are two conditions for successful and failed logins
		AuditConjunction conjunctionForSuccessful = AuditEntity.conjunction();
		AuditConjunction conjunctionForFailed = AuditEntity.conjunction();

		AuditDisjunction disjunction = AuditEntity.disjunction();
		
		conjunctionForFailed.add(AuditEntity.revisionProperty(IdmAudit_.changedAttributes.getName()).ilike(IdmPassword_.unsuccessfulAttempts.getName(), MatchMode.ANYWHERE));
		conjunctionForFailed.add(AuditEntity.property(IdmPassword_.modifier.getName()).eq(SecurityService.GUEST_NAME));
		
		conjunctionForSuccessful.add(AuditEntity.revisionProperty(IdmAudit_.changedAttributes.getName()).ilike(IdmPassword_.lastSuccessfulLogin.getName(), MatchMode.ANYWHERE));
		// Self created relation, created by expression
		conjunctionForSuccessful.add(new IdmPasswordSelfRelationWithOwnerExpression());
		
		disjunction.add(conjunctionForSuccessful);
		disjunction.add(conjunctionForFailed);
		conjunction.add(disjunction);

		if (StringUtils.isNotEmpty(filter.getOwnerId())) {
			// 'coleration' attribute for connection with IdmAudit entity - ownerId
			conjunction.add(AuditEntity.revisionProperty(IdmAudit_.ownerId.getName()).eq(filter.getOwnerId()));
		}

		if (StringUtils.isNotEmpty(filter.getOwnerCode())) {
			// 'coleration' attribute for connection with IdmAudit entity - ownerCode
			conjunction.add(AuditEntity.revisionProperty(IdmAudit_.ownerCode.getName()).eq(filter.getOwnerCode()));
		}

		if (filter.getFrom() != null) {
			conjunction.add(AuditEntity.revisionProperty(IdmAudit_.timestamp.getName()).ge(filter.getFrom().toInstant().toEpochMilli()));
		}

		if (filter.getTill() != null) {
			conjunction.add(AuditEntity.revisionProperty(IdmAudit_.timestamp.getName()).le(filter.getTill().toInstant().toEpochMilli()));
		}

		// Count is for pageable and check if is required made query
		Object count = this.getAuditReader().createQuery().forRevisionsOfEntity(IdmPassword.class, false, true).add(conjunction).addProjection(AuditEntity.id().count()).getSingleResult();
		Long countAsLong = null;
		if (count instanceof Long) {
			countAsLong =  (Long) count;
		}

		// Count is zero. Count is for queries better than real query
		if (countAsLong == null || countAsLong == 0) {
			return new PageImpl<IdmAuditDto>(Collections.emptyList(), pageable, 0);
		}

		// Create final query and solve pagination and order
		AuditQuery query = this.getAuditReader().createQuery().forRevisionsOfEntity(IdmPassword.class, false, true).add(conjunction);
		int maxResults = pageable.getPageSize();
		int firstResult = pageable.getPageSize() * pageable.getPageNumber();
		query.setMaxResults(maxResults).setFirstResult(firstResult);
		
		Sort sort = pageable.getSort();
		if (sort != null) {
			sort.forEach(order -> {
				AuditProperty<Object> property = AuditEntity.revisionProperty(order.getProperty());
				if (order.isAscending()) {
					query.addOrder(property.asc());
				} else {
					query.addOrder(property.desc());
				}
			});
		}
		
		// Returned list contains three object IdmAudit, Version (IdmPassword) and type of modification
		@SuppressWarnings("unchecked")
		List<Object[]> resultList = query.getResultList();

		// We doesn't need made again get for version, because version is in result form audit query
		filter.setWithVersion(Boolean.FALSE);

		// Iterate over all result and transform it into dtos
		List<IdmAuditDto> result = new ArrayList<>(resultList.size());
		for (Object[] object : resultList) {
			Object version = object[PROPERTY_AUDIT_VERSION];
			IdmAudit entity = (IdmAudit) object[PROPERTY_AUDIT_ENTITY];
			IdmAuditEntityDto newDto = (IdmAuditEntityDto) super.toDto(entity, new IdmAuditEntityDto(), filter);
			newDto.setEntity(getValuesFromVersion(version));
			result.add(newDto);
		}
		
		return new PageImpl<IdmAuditDto>(result, pageable, countAsLong);
	}

	/**
	 * Method get version for @param revisionId
	 * 
	 * @param clazz
	 * @param entityId
	 * @param revisionId
	 * @return
	 * @throws ClassNotFoundException
	 */
	private Object getRevision(String clazz, UUID entityId, Long revisionId) throws ClassNotFoundException {
		return this.find(Class.forName(clazz), entityId, revisionId);
	}
	
	/**
	 * Method working with envers, find is realized in audited tables
	 * 
	 * @param entityClass
	 * @param entityId
	 * @param revisionId
	 * @return
	 */
	private <T> T find(Class<T> entityClass, UUID entityId, Long revisionId) {
		AuditReader reader = this.getAuditReader();

		return reader.find(entityClass, entityId, revisionId);
	}
	
	/**
	 * Method return true if at least one fields has annotation {@link Audited} 
	 * @param fields
	 * @return
	 */
	private boolean getAuditedField(Field[] fields) {
		if (fields == null) {
			return false;
		}
		//
		for (Field field : fields) {
			if (field.getAnnotation(Audited.class) != null) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Method return true if found {@link Audited} in annotations
	 * @param annotations
	 * @return
	 */
	private boolean getAuditedAnnotation(Annotation[] annotations) {
		if (annotations == null) {
			return false;
		}
		for (Annotation annotation : annotations) {
			if (annotation.annotationType().equals(Audited.class)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Page<IdmAuditDto> findEntityWithRelation(IdmAuditFilter filter, Pageable pageable) {
		// in entities can be more UUID, we search for all
		List<String> entitiesIds = auditRepository.findDistinctOwnerIdByOwnerTypeAndOwnerCode(filter.getOwnerType(), filter.getOwnerCode());
		// remove null values
		entitiesIds.removeAll(Collections.singleton(null));
		// no entity found for this code return empty list
		if (entitiesIds.isEmpty()) {
			return new PageImpl<>(Collections.emptyList());
		}

		if (!entitiesIds.isEmpty()) {
			filter.setOwnerIds(entitiesIds);
		}
		return find(filter, pageable);
	}
	
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public AbstractEntity getActualRemovedEntity(Class<AbstractEntity> entityClass, Object primaryKey) {
		return (AbstractEntity) entityManager.find(entityClass, primaryKey);
	}

	public IdmAuditFilter getFilter(MultiValueMap<String, Object> parameters) {
		IdmAuditFilter filter = getFilterConverter().toFilter(parameters, IdmAuditFilter.class);
		//
		return filter;
	}

	/**
	 * Method used for backward compatibility with method {@link #findEntityWithRelation(Class, MultiValueMap, Pageable)}.
	 *
	 * @return
	 */
	private FilterConverter getFilterConverter() {
		if (filterConverter == null) {
			filterConverter = new FilterConverter(lookupService, mapper);
		}
		return filterConverter;
	}
}
