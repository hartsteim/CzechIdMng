package eu.bcvsolutions.idm.core.api.bulk.action;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.GenericTypeResolver;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.CoreModule;
import eu.bcvsolutions.idm.core.api.bulk.action.dto.IdmBulkActionDto;
import eu.bcvsolutions.idm.core.api.domain.Codeable;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.ResultModels;
import eu.bcvsolutions.idm.core.api.dto.filter.BaseFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.DataFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmMessageDto;
import eu.bcvsolutions.idm.core.notification.api.service.NotificationManager;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.filter.IdmLongRunningTaskFilter;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractLongRunningTaskExecutor;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.utils.PermissionUtils;

/**
 * Abstract parent for all bulk actions.
 *
 * @author Ondrej Kopr <kopr@xyxy.cz>
 * @author Tomáš Doischer
 *
 * @param <DTO>
 * @param <F>
 */
public abstract class AbstractBulkAction<DTO extends AbstractDto, F extends BaseFilter>
		extends AbstractLongRunningTaskExecutor<OperationResult>
		implements IdmBulkAction<DTO, F> {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AbstractBulkAction.class);

	private IdmBulkActionDto action;
	public Class<? extends BaseEntity> entityClass;
	@Autowired
	private NotificationManager notificationManager;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private ObjectMapper mapper;
	@Autowired
	private PlatformTransactionManager platformTransactionManager;
	@Qualifier("entityManager")
	@Autowired
	private EntityManager entityManager;

	@Override
	public IdmBulkActionDto getAction() {
		return action;
	}

	@Override
	public void setAction(IdmBulkActionDto action) {
		this.action = action;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		return new ArrayList<>();
	}

	@Override
	public boolean supports(Class<? extends BaseEntity> clazz) {
		return clazz.isAssignableFrom(getService().getEntityClass());
	}

	@Override
	public void validate() {
		Assert.notNull(action, "Action can't be null");
		//
		if (!showWithoutSelection() && action.getIdentifiers().isEmpty() && action.getFilter() == null) {
			throw new ResultCodeException(CoreResultCode.BULK_ACTION_ENTITIES_ARE_NOT_SPECIFIED);
		}
		//
		if (!action.getIdentifiers().isEmpty() && action.getFilter() != null) {
			throw new ResultCodeException(CoreResultCode.BULK_ACTION_ONLY_ONE_FILTER_CAN_BE_APPLIED);
		}
		//
		for (IdmFormAttributeDto attribute : this.getFormAttributes()) {
			if (attribute.isRequired()) {
				if (action.getProperties().isEmpty()) {
					// this state is also possible
					throw new ResultCodeException(CoreResultCode.BULK_ACTION_REQUIRED_PROPERTY, ImmutableMap.of("attributeCode", attribute.getCode()));
				}
				Object value = action.getProperties().get(attribute.getCode());
				if (value == null) {
					throw new ResultCodeException(CoreResultCode.BULK_ACTION_REQUIRED_PROPERTY, ImmutableMap.of("attributeCode", attribute.getCode()));
				}
				if (attribute.isMultiple()) {
					Collection<?> multivaluedValue= (Collection<?>)value;
					if (multivaluedValue.isEmpty()) {
						throw new ResultCodeException(CoreResultCode.BULK_ACTION_REQUIRED_PROPERTY, ImmutableMap.of("attributeCode", attribute.getCode()));
					}
				}
			}
		}
	}

	@Override
	public ResultModels prevalidate() {
		return new ResultModels();
	}
	
	@Override
	public IdmBulkActionDto preprocessBulkAction(IdmBulkActionDto bulkAction) {
		return bulkAction;
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String, Object> properties = super.getProperties();
		IdmBulkActionDto configuredAction = this.getAction();
		if (configuredAction == null) {
			return properties;
		}
		// Add action properties => has higher priority, than LRT properties.
		properties.putAll(action.getProperties());
		// Propagate properties to parent LRT properties => will be persisted, when thread pool is exhausted.
		properties.put(IdmBulkAction.PARAMETER_BULK_ACTION, action);
		//
		return properties;
	}

	@Override
	public List<String> getAuthorities() {
		return this.getAuthoritiesForEntity();
	}

	@Override
	public int getOrder() {
		return DEFAULT_ORDER;
	}

	@Override
	public List<String> getPropertyNames() {
		List<String> propertyNames = super.getPropertyNames();
		//
		propertyNames.add(ConfigurationService.PROPERTY_ENABLED);
		propertyNames.add(ConfigurationService.PROPERTY_ORDER);
		propertyNames.add(ConfigurationService.PROPERTY_LEVEL);
		propertyNames.add(ConfigurationService.PROPERTY_ICON);
		propertyNames.add(PROPERTY_DELETE_ACTION);
		propertyNames.add(PROPERTY_QUICK_BUTTON);
		//
		return propertyNames;
	}

	@Override
	protected OperationResult end(OperationResult result, Exception ex) {
		OperationResult end = null;
		if (result != null && result.getException() != null) {
			end = super.end(result, (Exception) result.getException());
		} else {
			end = super.end(result, ex);
		}
		//
		// send message
		IdmLongRunningTaskFilter filter = new IdmLongRunningTaskFilter();
		filter.setIncludeItemCounts(true);
		IdmLongRunningTaskDto task = getLongRunningTaskService().get(getLongRunningTaskId(), filter);
		//
		if (task.getCreatorId() != null) {
			IdmIdentityDto identityDto = identityService.get(task.getCreatorId());
			ConfigurationService configurationService = getConfigurationService();
			if (identityDto != null) {
				notificationManager.send(
						CoreModule.TOPIC_BULK_ACTION_END,
						new IdmMessageDto.Builder()
							.addParameter("action", getAction())
							.addParameter("task", task)
							.addParameter("owner", identityDto)
							.addParameter("result", end)
							.addParameter("detailUrl", configurationService.getFrontendUrl(String.format("scheduler/all-tasks/%s/detail", task.getId())))
							.addParameter("processItemslUrl", configurationService.getFrontendUrl(String.format("scheduler/all-tasks/%s/items", task.getId())))
							.build(),
						identityDto);
			}
		}
		//
		return end;
	}

	@Override
	public OperationResult process() {
		if (requireNewTransaction()) {
			TransactionTemplate template = new TransactionTemplate(platformTransactionManager);
			template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
			// Bulk action will be processed in transaction.
			final OperationResult[] result = new OperationResult[1];
			try {
				template.execute(new TransactionCallbackWithoutResult() {

					@Override
					protected void doInTransactionWithoutResult(TransactionStatus status) {
						result[0] = internalProcess();
					}
				});
			} catch (UnexpectedRollbackException ex) {
				// Just log for sure ... exception solved in new transaction, but this lower transaction is marked as roll-back.
				LOG.debug("Bulk action [{}] processed item [{}] failed",
						getClass().getSimpleName(), result[0], ex);
			}
			return result[0];
		}else {
			return internalProcess();
		}
	}

	@SuppressWarnings("unchecked")
	public OperationResult internalProcess() {
		IdmBulkActionDto localAction = this.getAction();
		if (localAction == null) {
			// try to load bulk action from LRT persisted properties
			localAction = (IdmBulkActionDto) super.getProperties().get(PARAMETER_BULK_ACTION);
			// transform filter is needed
			if (localAction.getFilter() != null) {
				MultiValueMap<String, Object> multivaluedMap = new LinkedMultiValueMap<>();
				Map<String, Object> properties = localAction.getFilter();
				//
				properties.entrySet().forEach((entry) -> {
					Object value = entry.getValue();
					if(value instanceof List<?>) {
						multivaluedMap.put(entry.getKey(), (List<Object>) value);
					}else {
						multivaluedMap.add(entry.getKey(), entry.getValue());
					}
				});
				//
				Class<F> filterClass = getService().getFilterClass();
				F filter;
				if (DataFilter.class.isAssignableFrom(filterClass)) {
					// All filter properties has to be controlled by data filter (=> static fields only).
					try {
						// data vs field properties
						MultiValueMap<String, Object> fieldProperties = new LinkedMultiValueMap<>();
						fieldProperties.addAll(multivaluedMap);
						//
						for (Field declaredField : filterClass.getDeclaredFields()) {
							// prevent to resurrect half of filter only - is insecure
							// TODO: refactor all filters to pure DataFilter
							if (!Modifier.isStatic(declaredField.getModifiers())) {
								throw new CoreException(
										String.format(
											"Declared filter [%s] has to fully support DataFilter, "
												+ "refactor field [%s] to data usage before action can be executed asynchronously.",
											filterClass.getCanonicalName(),
											declaredField.getName()
										)
								);
							}
						}
						filter = filterClass.getDeclaredConstructor(MultiValueMap.class).newInstance(multivaluedMap);
					} catch (ReflectiveOperationException | IllegalArgumentException| SecurityException ex) {
						throw new CoreException(
								String.format(
									"Declared filter [%s] has to support constructor with parameters.",
									filterClass.getCanonicalName()
								), ex
						);
					}
				} else {
					filter = mapper.convertValue(multivaluedMap, filterClass);
				}
				localAction.setTransformedFilter(filter);
			}
		}
		Assert.notNull(localAction, "Bulk action is required.");
		//
		StringBuilder description = new StringBuilder();
		IdmLongRunningTaskDto longRunningTask = this.getLongRunningTaskService().get(this.getLongRunningTaskId());
		description.append(longRunningTask.getTaskDescription());
		//
		List<UUID> entities = getEntities(localAction, description);
		//
		this.count = Long.valueOf(entities.size());
		this.counter = 0l;
		//
		// update description
		longRunningTask.setTaskDescription(description.toString());
		longRunningTask.setCount(this.count);
		longRunningTask.setCounter(this.counter);
		this.getLongRunningTaskService().save(longRunningTask);
		//
		return processEntities(entities);
	}

	/**
	 * Obtains the all UUIDs of entities to processing
	 *
	 * @param action
	 * @param description
	 * @return
	 */
	protected List<UUID> getEntities(IdmBulkActionDto action, StringBuilder description) {
		List<UUID> entities = null;
		if (!action.getIdentifiers().isEmpty()) {
			entities = new ArrayList<>(action.getIdentifiers());
			//
			if (description != null) {
				description.append(System.lineSeparator());
				description.append("For filtering is used list of ID's.");
			}
		} else if (action.getTransformedFilter() != null) {
			// is necessary find entities with given base permission
			List<UUID> content = getService().findIds(this.transformFilter(action.getTransformedFilter()), null,
					getPermissionForEntity()).getContent();

			// it is necessary create new arraylist because return list form find is unmodifiable
			entities = new ArrayList<>(content);
			//
			if (description != null) {
				description.append(System.lineSeparator());
				description.append("For filtering is used filter:");
				description.append(System.lineSeparator());
				String filterAsString = Arrays.toString(action.getFilter().entrySet().toArray());
				description.append(filterAsString);
			}
		} else if (showWithoutSelection()) {
			entities = getAllEntities(action, description == null ? new StringBuilder() : description);
		} else {
			throw new ResultCodeException(CoreResultCode.BULK_ACTION_ENTITIES_ARE_NOT_SPECIFIED);
		}
		//
		// remove given ids
		if (!action.getRemoveIdentifiers().isEmpty()) {
			entities.removeAll(action.getRemoveIdentifiers());
		}
		return entities;
	}

	/**
	 * Returns all entities to be process, if {@link #showWithoutSelection()} is enabled.
	 * All entities are processed, if no filter and no identifiers was given.
	 * Override this method, if {@link #showWithoutSelection()} is enabled for your action.
	 *
	 * @param action
	 * @param description
	 * @return
	 * @throws ResultCodeException if action supports {@link #showWithoutSelection()} and this method is not implemented.
	 */
	protected List<UUID> getAllEntities(IdmBulkActionDto action, StringBuilder description) {
		throw new ResultCodeException(CoreResultCode.BULK_ACTION_ENTITIES_ARE_NOT_SPECIFIED);
	}

	protected DTO getDtoById(UUID id) {
		return getService().get(id);
	}

	/**
	 * Process all identities by given list of ID's
	 *
	 * @param entitiesId
	 * @return
	 */
	protected OperationResult processEntities(Collection<UUID> entitiesId) {
		for (UUID entityId : entitiesId) {
			final OperationResult[] result = new OperationResult[1];
			if (requireNewTransaction()) {
				TransactionTemplate template = new TransactionTemplate(platformTransactionManager);
				template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
				// Item will be processed in new transaction.
				try {
					template.execute(new TransactionCallbackWithoutResult() {

						@Override
						protected void doInTransactionWithoutResult(TransactionStatus status) {
							result[0] = processEntity(entityId);
						}
					});
				} catch (UnexpectedRollbackException ex) {
					// Just log for sure ... exception solved in new transaction, but this lower transaction is marked as roll-back.
					LOG.debug("Bulk action [{}] processed item [{}] failed",
							getClass().getSimpleName(), result[0], ex);
				}
			} else {
				result[0] = processEntity(entityId);
			}
			if (result[0] != null && result[0].getState() == OperationState.CANCELED) {
				return result[0];
			}
		}
		return new OperationResult.Builder(OperationState.EXECUTED).build();
	}

	/**
	 * Process one entity.
	 */
	private OperationResult processEntity(UUID entityId) {
		this.increaseCounter();
		DTO entity = getDtoById(entityId);
		if (entity == null) {
			LOG.warn("Entity with id [{}] not found. The Entity will be skipped.", entityId);
			return null;
		}
		try {
			if (checkPermissionForEntity(entity)) {
				this.logItemProcessed(entity, processDto(entity));
			} else {
				// check permission failed
				createPermissionFailedLog(entity);
			}
		} catch (ResultCodeException ex) {
			// log failed result and continue
			LOG.error("Processing of entity [{}] failed.", entityId, ex);
			this.logItemProcessed(entity, new OperationResult.Builder(OperationState.EXCEPTION).setException(ex).build());
		} catch (Exception ex) {
			// log failed result and continue
			LOG.error("Processing of entity [{}] failed.", entityId, ex);
			this.logItemProcessed(entity, new OperationResult.Builder(OperationState.EXCEPTION).setCause(ex).build());
		}
		if (!updateState()) {
			return new OperationResult.Builder(OperationState.CANCELED).build();
		}
		// flush and clear session - if LRT is wrapped in parent transaction, we need to clear it
		if (getHibernateSession().isOpen()) {
			getHibernateSession().flush();
			getHibernateSession().clear();
		}
		return null;
	}

	/**
	 * Create success log for given identity
	 *
	 * @param dto
	 */
	protected void createSuccessLog(DTO dto) {
		this.logItemProcessed(dto, new OperationResult.Builder(OperationState.EXECUTED).build());
	}

	/**
	 * Create failed log for given dto with exception for insufficient permission
	 *
	 * @param dto
	 */
	protected void createPermissionFailedLog(DTO dto) {
		String entityCode = "";
		if (dto instanceof Codeable) {
			entityCode = ((Codeable) dto).getCode();
		}
		DefaultResultModel model = new DefaultResultModel(CoreResultCode.BULK_ACTION_INSUFFICIENT_PERMISSION,
				ImmutableMap.of("bulkAction", this.getAction().getName(),
						"entityId", dto.getId(),
						"entityCode", entityCode));
		//
		this.logItemProcessed(dto, new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(model).build());
	}

	/**
	 * Transform filter to {@link IdmIdentityFilter}
	 *
	 * @param filter
	 * @return
	 */
	protected F transformFilter(BaseFilter filter) {
		if (this.getService().getFilterClass().isAssignableFrom(filter.getClass())) {
			return this.getService().getFilterClass().cast(filter);
		}
		throw new ResultCodeException(CoreResultCode.BULK_ACTION_BAD_FILTER, ImmutableMap.of("filter", IdmIdentityFilter.class.getName(), "givenFilter", filter.getClass().getName()));
	}

	/**
	 * Check permission for given entity.Permission for check is get by service.
	 * Required permission is given by method {@link #getPermissionForEntity}.
	 * 
	 * @param entity
	 * @return
	 */
	protected boolean checkPermissionForEntity(BaseDto entity) {
		return PermissionUtils.hasPermission(getService().getPermissions(entity),
				getPermissionForEntity());
	}

	protected BasePermission[] getPermissionForEntity() {
		return PermissionUtils.toPermissions(getAuthoritiesForEntity()).toArray(new BasePermission[] {});
	}

	/**
	 * Get required permissions for process entity.
	 * Returns empty permissions by default, override if needed.
	 *
	 * @return
	 */
	protected List<String> getAuthoritiesForEntity() {
		return new ArrayList<>();
	}

	/**
	 * Process one of DTO in queue
	 *
	 * @param dto
	 * @return return operation result for current processed DTO
	 */
	protected abstract OperationResult processDto(DTO dto);

	/**
	 * Generic action.
	 *
	 * If is action generic, then we need to create new instance and set entity
	 * class to it in every case (includes getAvailableActions too). Stateful actions
	 * are typically generic actions (uses for more than one entity type). That
	 * action doesn't have knowledge about entity type by default. And this is a way
	 * how we can propagete entity type to it.
	 *
	 * @return
	 */
	@Override
	public boolean isGeneric() {
		return false;
	}

	public Class<? extends BaseEntity> getEntityClass() {
		return entityClass;
	}

	public void setEntityClass(Class<? extends BaseEntity> entityClass) {
		this.entityClass = entityClass;
	}

	@SuppressWarnings("unchecked")
	public Class<? extends BaseDto> getDtoClass() {
		Class<?>[] genericTypes = GenericTypeResolver.resolveTypeArguments(getClass(), AbstractBulkAction.class);
		return (Class<? extends BaseDto>) genericTypes[0];
	}

	/**
	 * If return true, then every item (and whole bulk action) will executed in new transaction.
	 * @return
	 */
	protected boolean requireNewTransaction() {
		return false;
	}

	private Session getHibernateSession() {
		return (Session) this.entityManager.getDelegate();
	}
}
