package eu.bcvsolutions.idm.core.api.service;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import eu.bcvsolutions.idm.core.api.config.datasource.CoreEntityManager;
import org.hibernate.query.criteria.internal.PathImplementor;
import org.hibernate.query.criteria.internal.predicate.ExistsPredicate;
import org.hibernate.query.criteria.internal.predicate.InPredicate;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import eu.bcvsolutions.idm.core.api.audit.service.SiemLoggerManager;
import eu.bcvsolutions.idm.core.api.domain.Embedded;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.ExportDescriptorDto;
import eu.bcvsolutions.idm.core.api.dto.IdmExportImportDto;
import eu.bcvsolutions.idm.core.api.dto.filter.BaseFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.DataFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.PermissionContext;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.api.exception.ForbiddenEntityException;
import eu.bcvsolutions.idm.core.api.repository.AbstractEntityRepository;
import eu.bcvsolutions.idm.core.api.repository.filter.FilterKey;
import eu.bcvsolutions.idm.core.api.repository.filter.FilterManager;
import eu.bcvsolutions.idm.core.api.rest.lookup.DtoMapper;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizableService;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.utils.PermissionUtils;

/**
 * Provide additional methods to retrieve DTOs and entities using the pagination
 * and sorting abstraction.
 *
 * @author Svanda
 * @author Radek Tomiška
 * @see Sort
 * @see Pageable
 * @see Page
 * @see DataFilter
 *
 * @param <DTO> dto type for entity type
 * @param <E> entity type
 * @param <F> filter {@link DataFilter} generalization is preferred.
 */
public abstract class AbstractReadDtoService<DTO extends BaseDto, E extends BaseEntity, F extends BaseFilter>
		implements ReadDtoService<DTO, F>, DtoMapper<DTO, E> {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AbstractReadDtoService.class);
	//
	@Autowired
	protected ModelMapper modelMapper;
	@Autowired
	private ApplicationContext context;
	@Autowired
	@CoreEntityManager
	private EntityManager entityManager;
	@Autowired
	@Lazy
	private ExportManager exportManager;
	@Autowired
	@Lazy
	protected SiemLoggerManager siemLoggerManager;
	//
	@Autowired(required = false)
	private List<PluggablePredicateProvider<E,F>> pluggablePredicateProviders;
	//
	private final Class<E> entityClass;
	private final Class<F> filterClass;
	private final Class<DTO> dtoClass;
	private final AbstractEntityRepository<E> repository;
	private AuthorizationManager authorizationManager;
	private FilterManager filterManager;

	@SuppressWarnings("unchecked")
	public AbstractReadDtoService(AbstractEntityRepository<E> repository) {
		Class<?>[] genericTypes = GenericTypeResolver.resolveTypeArguments(getClass(), AbstractReadDtoService.class);
		dtoClass = (Class<DTO>) genericTypes[0];
		entityClass = (Class<E>) genericTypes[1];
		filterClass = (Class<F>) genericTypes[2];
		//
		Assert.notNull(repository, MessageFormat.format("Repository for class [{0}] is required!", entityClass));
		//
		this.repository = repository;
	}

	@Override
	public boolean supports(Class<?> delimiter) {
		return dtoClass.isAssignableFrom(delimiter);
	}

	@Override
	public boolean supportsToDtoWithFilter() {
		return false;
	}

	/**
	 * Returns underlying repository
	 *
	 * @return
	 */
	protected AbstractEntityRepository<E> getRepository() {
		return repository;
	}

	/**
	 * Returns {@link BaseDto} type class, which is controlled by this service
	 *
	 * @return
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Class<DTO> getDtoClass() {
		return ((Class<DTO>)this.getDtoClass(null));
	}

	/**
	 * Returns {@link BaseDto} type class, which is controlled by this service
	 * @param entity
	 *
	 * @return
	 */
	protected Class<? extends DTO> getDtoClass(E entity) {
		return dtoClass;
	}

	/**
	 * Returns {@link BaseEntity} type class, which is controlled by this service.
	 *
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public Class<E> getEntityClass() {
		return (Class<E>) this.getEntityClass(null);
	}

	/**
	 * Returns {@link BaseEntity} type class, which is controlled by this service.
	 * @param dto
	 *
	 * @return
	 */
	protected Class<? extends E> getEntityClass(DTO dto) {
		return entityClass;
	}

	/**
	 * Returns {@link BaseFilter} type class, which is controlled by this service.
	 * {@link DataFilter} generalization is preferred.
	 *
	 * @return
	 */
	@Override
	public Class<F> getFilterClass() {
		return filterClass;
	}

	/**
	 * Creates and return new instance of filter class.
	 */
	public F createFilterInstance() {
		try {
			return filterClass.getDeclaredConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			throw new CoreException(e);
		}
	}

	/**
	 * Returns DTO by given id. Returns null, if DTO is not exists. For
	 * AbstractDto uuid or string could be given.
	 */
	@Override
	@Transactional(readOnly = true)
	public DTO get(Serializable id, BasePermission... permission) {
		return get(id, (F) null, permission);
	}

	@Override
	@Transactional(readOnly = true)
	public DTO get(Serializable id, F context, BasePermission... permission) {
		DTO dto;
		//
		if (supportsToDtoWithFilter()) {
			dto = toDto(getEntity(id, permission), null, context);
		} else {
			dto = toDto(getEntity(id, permission));
		}
		//
		return applyContext(dto, context, permission);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<DTO> find(Pageable pageable, BasePermission... permission) {
		return find(null, pageable, permission);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<DTO> find(final F filter, Pageable pageable, BasePermission... permission) {
		Page<DTO> results = toDtoPage(findEntities(filter, pageable, permission), filter);
		//
		results.getContent().forEach(dto -> {
			// apply context on each loaded dto
			applyContext(dto, filter, permission);
		});
		//
		return results;
	}

	@Override
	public Page<UUID> findIds(Pageable pageable, BasePermission... permission) {
		return this.findIds(null, pageable, permission);
	}

	@Override
	public Page<UUID> findIds(F filter, Pageable pageable, BasePermission... permission) {
		Specification<E> criteria = toCriteria(filter, false, permission);

		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<UUID> cq = criteriaBuilder.createQuery(UUID.class);
		Root<E> root = cq.from(getEntityClass());

		cq.select(root.get(AbstractEntity_.id.getName()));

		Predicate predicate = criteria.toPredicate(root, cq, criteriaBuilder);
		cq.where(predicate);

		// prepare sort
		if (pageable != null) {
			List<Order> orders = QueryUtils.toOrders(
					pageable.getSort() == null ? Sort.by(AbstractEntity_.id.getName()) : pageable.getSort(),
					root,
					criteriaBuilder);
			cq.orderBy(orders);
		}

		TypedQuery<UUID> query = entityManager.createQuery(cq);

		// if pageable is empty return result
		if (pageable == null) {
			return new PageImpl<UUID>(query.getResultList());
		}

		// count query
		CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
		countQuery.select(criteriaBuilder.count(countQuery.from(getEntityClass())));
		countQuery.where(predicate);
		Long total = entityManager.createQuery(countQuery).getSingleResult();

		query.setFirstResult((int) pageable.getOffset());
		query.setMaxResults(pageable.getPageSize());

		List<UUID> content = total > pageable.getOffset() ? query.getResultList() : Collections.<UUID> emptyList();

		return new PageImpl<UUID>(content, pageable, total);
	}

	@Override
	@Transactional(readOnly = true)
	public long count(final F filter, BasePermission... permission) {
		return getRepository().count(toCriteria(filter, false, permission));
	}
	
	@Override
	public boolean isNew(DTO dto) {
		Assert.notNull(dto, "DTO is required for check, if is new.");
		//
		return dto.getId() == null || !getRepository().existsById((UUID) dto.getId());
	}


	/**
	 * Returns, what currently logged identity can do with given dto
	 *
	 * @param id
	 * @return
	 */
	@Override
	@Transactional(readOnly = true)
	public Set<String> getPermissions(Serializable id) {
		E entity = getEntity(id);
		Assert.notNull(entity, String.format("Entity [%s] not found", id));
		//
		return getPermissions(entity);
	}

	@Override
	public Set<String> getPermissions(DTO dto) {
		E entity = toEntity(dto); // TODO: read entity?
		//
		return getPermissions(entity);
	}

	@Override
	public DTO checkAccess(DTO dto, BasePermission... permission) {
		checkAccess(toEntity(dto, null), permission);
		//
		return dto;
	}

	/**
	 * Supposed to be overriden - use super.toPredicates to transform default DataFilter props.
	 * Transforms given filter to jpa predicate, never returns null.
	 *
	 * @since 13.0.0 - uses PluggablePredicateProvider beans to add predicates from other modules
	 *
	 * @param root
	 * @param query
	 * @param builder
	 * @param filter
	 * @return
	 */
	protected List<Predicate> toPredicates(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder builder, F filter) {
		// Add all predicates from registered PluggablePredicateProvider beans
		final Stream<Predicate> pluggedPredicates = Optional.ofNullable(pluggablePredicateProviders).orElse(Collections.emptyList()).stream()
				.flatMap(provider -> provider.toPredicates(root, query, builder, filter).stream());
		//
		final Stream<Predicate> filterManagerPredicates = filter instanceof DataFilter ?
				getFilterManager().toPredicates(root, query, builder, (DataFilter) filter).stream() : Stream.empty();
		//
		return Stream.concat(
				// Add all predicates from registered PluggablePredicateProvider beans
				pluggedPredicates,
				// Add generic predicates for data filters
				filterManagerPredicates
		).collect(Collectors.toList());
	}

	/**
	 * Returns entity by given id. Returns null, if entity is not exists. For
	 * AbstractEntity uuid or uuid as string could be given.
	 */
	protected E getEntity(Serializable id, BasePermission... permission) {
		if (id instanceof Identifiable) {
			// dto or entity could be given
			id = ((Identifiable) id).getId();
		}
		// resolve identifier
		UUID identifier;
		if (AbstractEntity.class.isAssignableFrom(getEntityClass()) && (id instanceof String)) {
			// workflow / rest usage with string uuid variant
			// EL does not recognize two methods with the same name and
			// different argument type
			try {
				identifier = UUID.fromString((String) id);
			} catch (IllegalArgumentException ex) {
				// simply not found
				LOG.trace("Identity cannot be found by given identifier [{}]", id);
				//
				return null;
			}
		} else {
			identifier = (UUID) id;
		}
		// get entity
		E entity = getRepository().findById(identifier).orElse(null);
		//
		LOG.trace("Entity found [{}], permissions [{}] will be evaluated ...", entity, permission);
		entity = checkAccess(entity, permission);
		//
		LOG.trace("Entity [{}] passed permission check [{}]", entity, permission);
		return entity;
	}

	protected Page<E> findEntities(Pageable pageable, BasePermission... permission) {
		return findEntities(null, pageable, permission);
	}

	protected Page<E> findEntities(F filter, Pageable pageable, BasePermission... permission) {
		LOG.trace("Find entities for the filter [{}] with pageable [{}] starts", filter != null, pageable != null);
		//
		if (pageable == null) {
			// Underlying repository requires pageable is defined.
			pageable = PageRequest.of(0, Integer.MAX_VALUE);
		}
		//
		if (pageable.getSort() == null) {
			// #1872 - apply default pageable, if sort is not defined
			if (AbstractEntity.class.isAssignableFrom(getEntityClass())) {
				LOG.debug("Default sort by [id] will be added, Sort is not specified.");
				//
				pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(AbstractEntity_.id.getName()));
			} else {
				LOG.warn("Default sort by [id] cannot be added, specify Sort for service [{}] usage.", getClass());
			}
		}
		//
		Page<E> entities = getRepository().findAll(toCriteria(filter, true, permission), pageable);
		//
		LOG.trace("Found entities [{}].", entities.getTotalElements());
		return entities;
	}

	/**
	 * Constructs find / count jpa criteria from given filter and permissions
	 *
	 * @param filter
	 * @param permission
	 * @return
	 * @deprecated @since 9.6.0 use {@link #toCriteria(BaseFilter, boolean, BasePermission...)}
	 */
	@Deprecated
	protected Specification<E> toCriteria(F filter, BasePermission... permission) {
		return toCriteria(filter, true, permission);
	}

	/**
	 * Constructs find / count jpa criteria from given filter and permissions
	 *
	 * @param filter
	 * @param applyFetchMode fetch related entities in the master select
	 * @param permission
	 * @return
	 */
	protected Specification<E> toCriteria(F filter, boolean applyFetchMode, BasePermission... permission) {
		return new Specification<E>() {
			private static final long serialVersionUID = 1L;

			public Predicate toPredicate(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
				List<Predicate> predicates = new ArrayList<>();
				//
				// if filter is null, no filter predicates will be built
				if (filter != null) {
					predicates.addAll(AbstractReadDtoService.this.toPredicates(root, query, builder, filter));
				}
				//
				// permissions are not evaluated, if no permission was given 
				// or authorizable type is null (=> authorization policies are not supported)
				BasePermission[] permissions = PermissionUtils.trimNull(permission);
				if (!ObjectUtils.isEmpty(permissions) && (AbstractReadDtoService.this instanceof AuthorizableService)) {
					AuthorizableType authorizableType = ((AuthorizableService<?>) AbstractReadDtoService.this).getAuthorizableType();
					if (authorizableType != null && authorizableType.getType() != null) {
						boolean usePermissionOperatorOr = false;
						if (filter instanceof PermissionContext) {
							PermissionContext permissionContext = (PermissionContext) filter;
							usePermissionOperatorOr = permissionContext.usePermissionOperatorOr();
						}
						if (usePermissionOperatorOr) {
							predicates.add(getAuthorizationManager().getPredicateOr(root, query, builder, permissions));
						} else {
							predicates.add(getAuthorizationManager().getPredicate(root, query, builder, permissions));
						}						
					}
				}
				//
				// check IN predicates limit
				predicates.forEach(predicate -> {
					checkFilterSizeExceeded(predicate);
				});
				//
				// include referenced entity in "master" select  => reduces number of sub selects
				if (applyFetchMode) {
					// FIXME: is needed in new hibernate?
					// applyFetchMode(root);
				}
				//
				return query.where(predicates.toArray(new Predicate[predicates.size()])).getRestriction();
			}
		};
	}
	
	/**
	 * Apply context on given dto.
	 * Lookout: {@link #supportsToDtoWithFilter()} is required to return {@code true}.
	 * 
	 * @param dto nullable
	 * @param context nullable
	 * @param permission
	 * @since 10.2.0
	 */
	protected DTO applyContext(DTO dto, F context, BasePermission... permission) {
		// DTO not supports permissions
		if (!(dto instanceof AbstractDto)) {
			return dto;
		}
		// context not support permissions
		if (!(context instanceof PermissionContext)) {
			return dto;
		}
		// load permissions is not needed
		if (!((PermissionContext) context).getAddPermissions()) {
			return dto;
		}
		// load permissions
		((AbstractDto) dto).setPermissions(getPermissions(dto));
		//
		return dto;
	}
	
	@Override
	public void export(UUID id, IdmExportImportDto batch) {
		Assert.notNull(batch, "Export batch must exist!");
		
		ExportDescriptorDto exportDescriptorDto = null;
		// Workaround - I need to use BLANK UUID (UUID no exists in DB), because I have
		// to ensure add all DTO types (in full deep) in correct order (even when no child entity
		// exists (no schema, no sync ...)).
		if (ExportManager.BLANK_UUID.equals(id)) {
			exportDescriptorDto = new ExportDescriptorDto(this.getDtoClass());
		} else {
			DTO export = internalExport(id);
			batch.getExportedDtos().add(export);			
			exportDescriptorDto = new ExportDescriptorDto(export.getClass());
		}
		
		if (!batch.getExportOrder().contains(exportDescriptorDto)) {
			batch.getExportOrder().add(exportDescriptorDto);
		}
	}
	
	protected DTO internalExport(UUID id) {
		DTO dto =  this.get(id);
		if (dto != null) {
			this.checkAccess(dto, IdmBasePermission.READ);
		}
		if (dto instanceof AbstractDto) {
			// Clear embedded data
			((AbstractDto) dto).getEmbedded().clear(); 
		}
		return dto;
	}

	protected Set<String> getPermissions(E entity) {
		Assert.notNull(entity, "Entity is required get permissions.");
		//
		return getAuthorizationManager().getPermissions(entity);
	}

	/**
	 *
	 * Converts entity to DTO
	 *
	 * @param entity
	 * @return
	 */
	protected DTO toDto(E entity) {
		return toDto(entity, null);
	}

	/**
	 * Converts entity to DTO. When service support transform DTO with filter
	 * this method will not be called.
	 *
	 * @see Embedded
	 * @param entity
	 * @param dto
	 *            if is not null, then will be use as input to convert
	 * @return
	 */
	protected DTO toDto(E entity, DTO dto) {
		return toDto(entity, dto, null);
	}

	/**
	 * Convert entity to DTO. This method can receive {@link F} if method
	 * {@link AbstractReadDtoService#supportsToDtoWithFilter()} return true this
	 * method will be called from method {@link AbstractReadDtoService#toDtoPage(Page, BaseFilter)}
	 * instead {@link AbstractReadDtoService#toDto(BaseEntity, BaseDto)}.
	 *
	 * @since 9.4.0
	 * @param entity
	 * @param dto
	 * @param context - load additional attributes, if needed
	 * @return
	 */
	protected DTO toDto(E entity, DTO dto, F context) {
		if (entity == null) {
			return null;
		}
		if (dto == null) {
			return modelMapper.map(entity, this.getDtoClass(entity));
		}
		modelMapper.map(entity, dto);
		return dto;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public DTO map(E entity, DTO dto, DataFilter context) {
		if (context == null) {
			return toDto(entity, dto, null);
		}
		if (getFilterClass().isAssignableFrom(context.getClass())) {
			return toDto(entity, dto, (F) context);
		}
		// TODO: try to convert data filter automatically
		return toDto(entity, dto, null);
	}
	
	@Override
	public void siemLog(String action, String status, String targetName, String targetUuid, String subjectName, String subjectUuid, String transactionUuid, String reason) {
		siemLoggerManager.log(action, status, targetName, targetUuid, subjectName, subjectUuid, transactionUuid, reason);
	}
	
	@Override
	public void siemLog(String action, String status, BaseDto targetDto, BaseDto subjectDto, String transactionUuid, String reason) {
		siemLoggerManager.log(action, status, targetDto, subjectDto, transactionUuid, reason);
	}

	/**
	 * Converts list of entities wrapped to Page object to list of DTOs wrapped
	 * to Page object.
	 *
	 * @param entityPage
	 * @return
	 */
	protected Page<DTO> toDtoPage(Page<E> entityPage) {
		return toDtoPage(entityPage, null);
	}

	/**
	 * Converts list of entities and wrap to Page object to list wrapped list of DTOs.
	 * Method check returned value from method {@link AbstractReadDtoService#supportsToDtoWithFilter()}.
	 * If the method {@link AbstractReadDtoService#supportsToDtoWithFilter()} return true
	 * the method {@link AbstractReadDtoService#toDto(BaseEntity, BaseDto, BaseFilter)} will be called.
	 * Otherwise will be called method {@link AbstractReadDtoService#toDto(BaseEntity, BaseDto)}.
	 *
	 * @param entityPage
	 * @param filter
	 * @return
	 */
	protected Page<DTO> toDtoPage(Page<E> entityPage, F filter) {
		List<DTO> dtos = null;
		// Check if service supports filter mapping and use correct method
		if (supportsToDtoWithFilter()) {
			dtos = this.toDtos(entityPage.getContent(), true, filter);
		} else {
			dtos = this.toDtos(entityPage.getContent(), true);
		}
		PageRequest pageRequest = PageRequest.of(
				entityPage.getNumber(),
				entityPage.getSize() > 0 ? entityPage.getSize() : 10,
				entityPage.getSort());
		Page<DTO> dtoPage = new PageImpl<>(dtos, pageRequest, entityPage.getTotalElements());
		return dtoPage;
	}

	/**
	 * Converts list of entities to list of DTOs
	 *
	 * @param entities
	 * @param trimmed
	 * @return
	 */
	protected List<DTO> toDtos(List<E> entities, boolean trimmed) {
		return toDtos(entities, trimmed, null);
	}

	/**
	 * Converts list of entities to list of DTOs.
	 * Method check returned value from method {@link AbstractReadDtoService#supportsToDtoWithFilter()}.
	 * If the method {@link AbstractReadDtoService#supportsToDtoWithFilter()} return true
	 * the method {@link AbstractReadDtoService#toDto(BaseEntity, BaseDto, BaseFilter)} will be called.
	 * Otherwise will be called method {@link AbstractReadDtoService#toDto(BaseEntity, BaseDto)}.
	 *
	 * @param entities
	 * @param trimmed
	 * @param filter
	 * @return
	 */
	protected List<DTO> toDtos(List<E> entities, boolean trimmed, F filter) {
		if (entities == null) {
			return null;
		}
		List<DTO> dtos = new ArrayList<>();
		entities.forEach(entity -> {
			try {
				DTO newDto = this.getDtoClass(entity).getDeclaredConstructor().newInstance();
				if (newDto instanceof AbstractDto) {
					((AbstractDto) newDto).setTrimmed(trimmed);
				}

				DTO dto = null;
				// Check if service support filter mapping and use it for transform entity to DTO
				if (supportsToDtoWithFilter()) {
					dto = this.toDto(entity, newDto, filter);
				} else {
					dto = this.toDto(entity, newDto);
				}
				dtos.add(dto);

			} catch (ReflectiveOperationException ex) {
				throw new CoreException(ex);
			}
		});
		return dtos;
	}

	/**
	 * Converts given DTO to entity
	 *
	 * @param dto
	 * @return
	 */
	protected E toEntity(DTO dto) {
		return toEntity(dto, null);
	}

	/**
	 * Converts DTO to entity
	 *
	 * @see Embedded
	 * @param entity
	 *            if is not null, then will be use as input to convert
	 * @param dto
	 * @return
	 */
	protected E toEntity(DTO dto, E entity) {
		if (dto == null) {
			return null;
		}
		if (entity != null) {
			modelMapper.map(dto, entity);
			return entity;
		}
		return modelMapper.map(dto, getEntityClass(dto));
	}

	/**
	 * Evaluates authorization permission on given entity.
	 *
	 * @param permission base permissions to evaluate (all permission needed)
	 * @return
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 */
	protected E checkAccess(E entity, BasePermission... permission) {
		if (entity == null) {
			// nothing to check
			return null;
		}
		//
		if (this instanceof AuthorizableService) {
			AuthorizableType authorizableType = ((AuthorizableService<?>) AbstractReadDtoService.this).getAuthorizableType();
			if (authorizableType != null && authorizableType.getType() != null) {
				BasePermission[] permissions = PermissionUtils.trimNull(permission);
				if (!ObjectUtils.isEmpty(permissions) && !getAuthorizationManager().evaluate(entity, permissions)) {
					throw new ForbiddenEntityException((BaseEntity)entity, permissions);
				}
			}
		}
		return entity;
	}
	
	/**
	 * Check limit of values given for IN predicates.
	 * 
	 * @param predicate start predicate
	 * @since 10.6.0
	 */
	private void checkFilterSizeExceeded(Predicate predicate) {
		if (predicate instanceof InPredicate) {
			InPredicate<?> in = (InPredicate<?>) predicate;
			String propertyName = "filter"; // common property name => attribute is optional
			if (in.getExpression() instanceof PathImplementor) {
				// TODO: remove root path as 'null', but it is not fit to filter property name anyway => hidden in UI message
				propertyName = ((PathImplementor<?>) in.getExpression()).getPathIdentifier();
			}
			getFilterManager().checkFilterSizeExceeded(new FilterKey(getEntityClass(), propertyName), in.getValues());
		}
		if (predicate instanceof ExistsPredicate) {
			checkFilterSizeExceeded(((ExistsPredicate) predicate).getSubquery().getRestriction());
		}
		predicate.getExpressions().forEach(expression -> {
			if (expression instanceof Predicate) {
				checkFilterSizeExceeded((Predicate) expression);
			}
		});
	}

	/**
	 * Returns authorization manager
	 *
	 * @return
	 */
	protected AuthorizationManager getAuthorizationManager() {
		if (authorizationManager == null) {
			authorizationManager = context.getBean(AuthorizationManager.class);
		}
		return authorizationManager;
	}

	protected FilterManager getFilterManager() {
		if (filterManager == null) {
			filterManager = context.getBean(FilterManager.class);
		}
		return filterManager;
	}

	protected void setModelMapper(ModelMapper modelMapper) {
		this.modelMapper = modelMapper;
	}

	/**
	 * Return {@link Pageable} to find all sorted records.
	 *
	 * @param sort
	 * @return
	 */
	protected Pageable getPageableAll(Sort sort) {
		return PageRequest.of(0, Integer.MAX_VALUE, sort);
	}

	/**
	 * Returns entity manager
	 *
	 * @return
	 */
	protected EntityManager getEntityManager() {
		return entityManager;
	}

	/**
	 * Sets FETCH JOIN policy to selecting referenced entities.
	 *
	 * @param root
	 * @since 9.6.0
	 */
	protected void applyFetchMode(Root<E> root) {
		Class<E> controlledClass = getEntityClass();
		//
	    for (Field field : controlledClass.getDeclaredFields()) {
	    	ManyToOne relation = field.getAnnotation(ManyToOne.class);
	        if (relation != null && relation.fetch() == FetchType.EAGER) {
	        	String fieldName = field.getName();
	        	LOG.trace("Set fetch strategy LEFT to field [{}] of entity [{}]", fieldName, controlledClass.getSimpleName());
	        	// include referenced entity in "master" select
	        	// reduce number of sub selects
        		root.fetch(fieldName, JoinType.LEFT);
	        }
	    }
	}

	protected ExportManager getExportManager() {
		return exportManager;
	}

}
