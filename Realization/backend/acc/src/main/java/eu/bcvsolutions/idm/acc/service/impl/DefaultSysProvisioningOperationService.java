package eu.bcvsolutions.idm.acc.service.impl;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import javax.sql.DataSource;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.acc.AccModuleDescriptor;
import eu.bcvsolutions.idm.acc.config.domain.ProvisioningConfiguration;
import eu.bcvsolutions.idm.acc.domain.AccGroupPermission;
import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.domain.EmptyProvisioningType;
import eu.bcvsolutions.idm.acc.domain.ProvisioningContext;
import eu.bcvsolutions.idm.acc.domain.ProvisioningEventType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.dto.ProvisioningAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningBatchDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningOperationDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysProvisioningOperationFilter;
import eu.bcvsolutions.idm.acc.entity.SysProvisioningArchive_;
import eu.bcvsolutions.idm.acc.entity.SysProvisioningAttribute;
import eu.bcvsolutions.idm.acc.entity.SysProvisioningAttribute_;
import eu.bcvsolutions.idm.acc.entity.SysProvisioningBatch_;
import eu.bcvsolutions.idm.acc.entity.SysProvisioningOperation;
import eu.bcvsolutions.idm.acc.entity.SysProvisioningOperation_;
import eu.bcvsolutions.idm.acc.entity.SysSystemEntity_;
import eu.bcvsolutions.idm.acc.entity.SysSystem_;
import eu.bcvsolutions.idm.acc.repository.SysProvisioningOperationRepository;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningArchiveService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningBatchService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningOperationService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.config.flyway.IdmFlywayMigrationStrategy;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.OperationResultDto;
import eu.bcvsolutions.idm.core.api.dto.ResultModel;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.AbstractReadWriteDtoService;
import eu.bcvsolutions.idm.core.api.service.ConfidentialStorage;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmMessageDto;
import eu.bcvsolutions.idm.core.notification.api.service.NotificationManager;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.ConfidentialString;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.api.IcPasswordAttribute;
import eu.bcvsolutions.idm.ic.impl.IcAttributeImpl;
import eu.bcvsolutions.idm.ic.impl.IcConnectorObjectImpl;
import eu.bcvsolutions.idm.ic.impl.IcPasswordAttributeImpl;

/**
 * Persists provisioning operations.
 * 
 * @author Radek Tomiška
 */
@Service
public class DefaultSysProvisioningOperationService
		extends AbstractReadWriteDtoService<SysProvisioningOperationDto, SysProvisioningOperation, SysProvisioningOperationFilter> 
		implements SysProvisioningOperationService {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DefaultSysProvisioningOperationService.class);
	private static final String CONFIDENTIAL_KEY_PATTERN = "%s:%s:%d";
	private static final String ACCOUNT_OBJECT_PROPERTY_PREFIX = "sys:account:";
	private static final String CONNECTOR_OBJECT_PROPERTY_PREFIX = "sys:connector:";
	
	private final SysProvisioningOperationRepository repository;
	//
	@Autowired private SysProvisioningArchiveService provisioningArchiveService;
	@Autowired private SysProvisioningBatchService batchService;
	@Autowired private NotificationManager notificationManager;
	@Autowired private ConfidentialStorage confidentialStorage;
	@Autowired private SysSystemService systemService;
	@Autowired private SecurityService securityService;
	@Autowired private SysSystemEntityService systemEntityService;
	@Autowired private ProvisioningConfiguration provisioningConfiguration;
	@Autowired private SysProvisioningAttributeService provisioningAttributeService;
	@Qualifier("entityManager")
	@Autowired private EntityManager entityManager;
	@Autowired private IdmFlywayMigrationStrategy flywayMigrationStrategy;
	@Autowired private DataSource dataSource;

	@Autowired
	public DefaultSysProvisioningOperationService(SysProvisioningOperationRepository repository) {
		super(repository);
		//
		this.repository = repository;
	}
	
	@Override
	public AuthorizableType getAuthorizableType() {
		return new AuthorizableType(AccGroupPermission.PROVISIONINGOPERATION, getEntityClass());
	}

	@Override
	protected List<Predicate> toPredicates(Root<SysProvisioningOperation> root, CriteriaQuery<?> query,
			CriteriaBuilder builder, SysProvisioningOperationFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
		//
		// quick - "fulltext"
		if (StringUtils.isNotEmpty(filter.getText())) {
			throw new ResultCodeException(CoreResultCode.BAD_FILTER, "Filter by text is not supported.");
		}
		// System Id
		UUID systemId = filter.getSystemId();
		if (systemId != null) {
			predicates.add(builder.equal(root.get(SysProvisioningOperation_.system).get(SysSystem_.id), systemId));
		}
		// From
		ZonedDateTime from = filter.getFrom();
		if (from != null) {
			predicates.add(builder.greaterThanOrEqualTo(root.get(SysProvisioningOperation_.created), from));
		}
		// Till
		ZonedDateTime till = filter.getTill();
		if (till != null) {
			predicates.add(builder.lessThanOrEqualTo(root.get(SysProvisioningOperation_.created), till));
		}
		// Operation type
		ProvisioningEventType operationType = filter.getOperationType();
		if (operationType != null) {
			predicates.add(builder.equal(root.get(SysProvisioningOperation_.operationType), operationType));
		}
		// Entity type
		SystemEntityType entityType = filter.getEntityType();
		if (entityType != null) {
			predicates.add(builder.equal(root.get(SysProvisioningOperation_.entityType), entityType));
		}
		// Entity identifier
		UUID entityIdentifier = filter.getEntityIdentifier();
		if (entityIdentifier != null) {
			predicates.add(builder.equal(root.get(SysProvisioningOperation_.entityIdentifier), entityIdentifier));
		}
		// System entity
		UUID systemEntity = filter.getSystemEntity();
		if (systemEntity != null) {
			predicates.add(builder.equal(root.get(SysProvisioningOperation_.systemEntity).get(SysSystemEntity_.id), systemEntity));
		}
		// System entity UID
		String systemEntityUid = filter.getSystemEntityUid();
		if (StringUtils.isNotEmpty(systemEntityUid)) {
			predicates.add(builder.equal(root.get(SysProvisioningOperation_.systemEntity).get(SysSystemEntity_.uid), systemEntityUid));
		}
		
		// Operation result and his state
		OperationState resultState = filter.getResultState();
		if (resultState != null) {
			predicates.add(builder.equal(root.get(SysProvisioningOperation_.result).get(OperationResultDto.PROPERTY_STATE), resultState));
		}
		
		// Is not in this operation state
		OperationState notInState = filter.getNotInState();
		if (notInState != null) {
			predicates.add(builder.notEqual(
					root.get(SysProvisioningOperation_.result).get(OperationResultDto.PROPERTY_STATE), notInState));
		}
		
		// Batch ID
		UUID batchId = filter.getBatchId();
		if (batchId != null) {
			predicates.add(builder.equal(root.get(SysProvisioningOperation_.batch).get(SysProvisioningBatch_.id), batchId));
		}
		// Role-request ID
		UUID roleRequestId = filter.getRoleRequestId();
		if (roleRequestId != null) {
			predicates.add(builder.equal(root.get(SysProvisioningOperation_.roleRequestId), roleRequestId));
		}
		// updated attributes
		List<String> attributeUpdated = filter.getAttributeUpdated();
		if (!CollectionUtils.isEmpty(attributeUpdated)) {
			Subquery<SysProvisioningAttribute> subquery = query.subquery(SysProvisioningAttribute.class);
			Root<SysProvisioningAttribute> subRoot = subquery.from(SysProvisioningAttribute.class);
			subquery.select(subRoot);
			subquery.where(
                    builder.and(
                    		builder.equal(subRoot.get(SysProvisioningAttribute_.provisioningId), root.get(SysProvisioningArchive_.id)), // correlation attr
                    		subRoot.get(SysProvisioningAttribute_.name).in(attributeUpdated),
                    		builder.isFalse(subRoot.get(SysProvisioningAttribute_.removed))
                    		)
            );		
			predicates.add(builder.exists(subquery));
		}
		// removed attributes
		List<String> attributeRemoved = filter.getAttributeRemoved();
		if (!CollectionUtils.isEmpty(attributeRemoved)) {
			Subquery<SysProvisioningAttribute> subquery = query.subquery(SysProvisioningAttribute.class);
			Root<SysProvisioningAttribute> subRoot = subquery.from(SysProvisioningAttribute.class);
			subquery.select(subRoot);
			subquery.where(
                    builder.and(
                    		builder.equal(subRoot.get(SysProvisioningAttribute_.provisioningId), root.get(SysProvisioningArchive_.id)), // correlation attr
                    		subRoot.get(SysProvisioningAttribute_.name).in(attributeRemoved),
                    		builder.isTrue(subRoot.get(SysProvisioningAttribute_.removed))
                    		)
            );		
			predicates.add(builder.exists(subquery));
		}
		// empty provisioning
		Boolean emptyProvisioning = filter.getEmptyProvisioning();
		EmptyProvisioningType emptyProvisioningType = filter.getEmptyProvisioningType();
		if (emptyProvisioning != null || emptyProvisioningType != null) {
			if (BooleanUtils.isTrue(emptyProvisioning) 
					&& emptyProvisioningType != null
					&& emptyProvisioningType != EmptyProvisioningType.EMPTY) {
				// empty + not empty => nothing
				predicates.add(builder.disjunction());
			} else if(BooleanUtils.isFalse(emptyProvisioning)
					&& emptyProvisioningType != null
					&& emptyProvisioningType != EmptyProvisioningType.NON_EMPTY) {
				// not empty + filled somehow => nothing
				predicates.add(builder.disjunction());
			} else if (emptyProvisioningType == null) {
				// fill by boolean value
				emptyProvisioningType = emptyProvisioning ? EmptyProvisioningType.EMPTY : EmptyProvisioningType.NON_EMPTY;
			}
			
			Subquery<SysProvisioningAttribute> subquery = query.subquery(SysProvisioningAttribute.class);
			Root<SysProvisioningAttribute> subRoot = subquery.from(SysProvisioningAttribute.class);
			subquery.select(subRoot);
			subquery.where(
                    builder.and(builder.equal(subRoot.get(SysProvisioningAttribute_.provisioningId), root.get(SysProvisioningArchive_.id))) // correlation attr)
            );
			//
			Predicate provisioningPredicate = builder.exists(subquery); // has attributes
			Predicate notProcessedPredicate = builder.or(
					// Not executed operations (already in queue, created) are not wanted - attributes are not computed in this phase.
					builder.equal(root.get(SysProvisioningOperation_.result).get(OperationResultDto.PROPERTY_STATE), OperationState.CREATED),
					builder.equal(root.get(SysProvisioningOperation_.result).get(OperationResultDto.PROPERTY_STATE), OperationState.RUNNING),
					builder.equal(root.get(SysProvisioningOperation_.result).get(OperationResultDto.PROPERTY_STATE), OperationState.BLOCKED),
					builder.and(
							builder.equal(
									root.get(SysProvisioningOperation_.result).get(OperationResultDto.PROPERTY_STATE), 
									OperationState.NOT_EXECUTED
							),
							// only readOnly has attributes evaluated
							builder.or(
								builder.isNull(root.get(SysProvisioningOperation_.result).get(OperationResultDto.PROPERTY_CODE)),
								builder.notEqual(
										root.get(SysProvisioningOperation_.result).get(OperationResultDto.PROPERTY_CODE), 
										AccResultCode.PROVISIONING_SYSTEM_READONLY.name()
								)
							)
					)
			);
			switch (emptyProvisioningType) {
				case EMPTY: {
					provisioningPredicate = builder.and(
							builder.not(provisioningPredicate), // empty
							builder.notEqual(root.get(SysProvisioningOperation_.operationType), ProvisioningEventType.DELETE), // delete operations are not considered as empty
							builder.not(notProcessedPredicate)
					);
					break;
				}
				case NON_EMPTY: {
					// delete operations are not considered as empty or filled => show all time
					provisioningPredicate = builder.and(
						builder.or(
								provisioningPredicate,
								builder.equal(root.get(SysProvisioningOperation_.operationType), ProvisioningEventType.DELETE)
						),
						builder.not(notProcessedPredicate)
					);
					break;
				}
				case NOT_PROCESSED: {
					provisioningPredicate = notProcessedPredicate;
					break;
				}
				default: {
					throw new UnsupportedOperationException(String.format("Empty profisioning type [%s] is not supported by filter predicates.", 
							emptyProvisioningType));
				}
			}
			predicates.add(provisioningPredicate);
		}
		return predicates;
	}

	@Override
	protected SysProvisioningOperationDto toDto(SysProvisioningOperation entity, SysProvisioningOperationDto dto) {
		dto = super.toDto(entity, dto);
		//
		if (dto != null) {
			// copy => detach
			dto.setProvisioningContext(new ProvisioningContext(dto.getProvisioningContext()));
			if (entity != null && entity.getSystemEntity() != null) {
				dto.setSystemEntityUid(entity.getSystemEntity().getUid());
			}
		}
		return dto;
	}
	
	@Override
	@Transactional(readOnly = true)
	public SysProvisioningOperationDto get(Serializable id, BasePermission... permission) {
		return super.get(id, permission);
	}
	
	@Override
	@Transactional
	public SysProvisioningOperationDto saveInternal(SysProvisioningOperationDto dto) {
		// replace guarded strings to confidential strings (save to persist)
		Map<String, Serializable> confidentialValues = replaceGuardedStrings(dto.getProvisioningContext());
		// save operation
		dto = super.saveInternal(dto);
		// save prepared guarded strings into confidential storage 
		for(Entry<String, Serializable> entry : confidentialValues.entrySet()) {
			confidentialStorage.save(dto.getId(), SysProvisioningOperation.class, entry.getKey(), entry.getValue());
		}
		//
		return dto;
	}

	@Override
	@Transactional
	public void deleteInternal(SysProvisioningOperationDto provisioningOperation) {
		Assert.notNull(provisioningOperation, "Operation is required for delete.");
		//
		// delete persisted confidential storage values
		deleteConfidentialStrings(provisioningOperation);
		//
		// create archived operation
		provisioningArchiveService.archive(provisioningOperation);
		// delete attributes
		provisioningAttributeService.deleteAttributes(provisioningOperation);
		
		if (provisioningOperation.getSystemEntity() != null) {
			super.deleteInternal(provisioningOperation);
			//
			return;
		}
		//
		// try to resolve native query by used database
		String dbName = flywayMigrationStrategy.resolveDbName(dataSource);
		if (dbName.equals(IdmFlywayMigrationStrategy.POSTGRESQL_DBNAME)) {
			// delete an invalid provisioning operation by native sql => invalid operation cannot be deleted by hibernate on postgresql
			Query query = entityManager.createNativeQuery(
					String.format("delete from sys_provisioning_operation where id = uuid_send('%s')", provisioningOperation.getId())
			);
			int result = query.executeUpdate();
			//
			LOG.warn("Removed [{}] row from sys_provisioning_operation table", result);
		} else if (dbName.equals(IdmFlywayMigrationStrategy.MSSQL_DBNAME)) {
			// delete an invalid provisioning operation by ugly native sql => invalid operation cannot be deleted by hibernate on mssql
			Query query = entityManager.createNativeQuery(
					String.format(
							"delete from sys_provisioning_operation where id = 0x%s", 
							provisioningOperation.getId().toString().toUpperCase().replaceAll("-", "")
					)
			);
			int result = query.executeUpdate();
			//
			LOG.warn("Removed [{}] row from sys_provisioning_operation table", result);
	    } else {
			// try to delete invalid operation a standard way on other databases
			super.deleteInternal(provisioningOperation);
		}
	}
	
	@Override
	@Transactional(readOnly = true)
	public Page<SysProvisioningOperationDto> findByBatchId(UUID batchId,  Pageable pageable) {
		SysProvisioningOperationFilter filter = new SysProvisioningOperationFilter();
		filter.setBatchId(batchId);
		return this.find(filter, pageable);
	}

	@Override
	@Transactional(readOnly = true)
	public List<SysProvisioningOperationDto> getByTimelineAndBatchId(UUID batchId) {
		// sort from higher created
		List<SysProvisioningOperationDto> sortedList = this
				.findByBatchId(
					batchId, 
					PageRequest.of(0, Integer.MAX_VALUE, new Sort(Direction.ASC, SysProvisioningOperation_.created.getName()))
				)
				.getContent();
		
		return Collections.unmodifiableList(sortedList);
	}

	@Override
	@Transactional(readOnly = true)
	public SysProvisioningOperationDto getFirstOperationByBatchId(UUID batchId) {
		List<SysProvisioningOperationDto> requests = getByTimelineAndBatchId(batchId);
		return (requests.isEmpty()) ? null : requests.get(0);
	}

	@Override
	@Transactional(readOnly = true)
	public SysProvisioningOperationDto getLastOperationByBatchId(UUID batchId) {
		List<SysProvisioningOperationDto> requests = getByTimelineAndBatchId(batchId);
		return (requests.isEmpty()) ? null : requests.get(requests.size() - 1);
	}
	
	/**
	 * Returns fully loaded AccountObject with guarded strings.
	 * 
	 * @param provisioningOperation
	 * @return
	 */
	@Override
	public Map<ProvisioningAttributeDto, Object> getFullAccountObject(SysProvisioningOperationDto provisioningOperation) {
		if (provisioningOperation == null 
				|| provisioningOperation.getProvisioningContext() == null 
				|| provisioningOperation.getProvisioningContext().getAccountObject() == null) {
			return null;
		}
		//
		Map<ProvisioningAttributeDto, Object> fullAccountObject = new HashMap<>();
		Map<ProvisioningAttributeDto, Object> accountObject = provisioningOperation.getProvisioningContext().getAccountObject();
		for (Entry<ProvisioningAttributeDto, Object> entry : accountObject.entrySet()) {
			if (entry.getValue() == null) {
				fullAccountObject.put(entry.getKey(), entry.getValue());
				continue;
			}
			Object idmValue = entry.getValue();
			// single value
			if (idmValue instanceof ConfidentialString) {
				fullAccountObject.put(
						entry.getKey(), 
						confidentialStorage.getGuardedString(
								provisioningOperation.getId(), 
								SysProvisioningOperation.class, 
								((ConfidentialString)idmValue).getKey())
						);
				continue;
			}
			// array
			if(idmValue.getClass().isArray()) {
				if (!idmValue.getClass().getComponentType().isPrimitive()) { // objects only, we dont want pto proces byte, boolean etc.
					Object[] idmValues = (Object[]) idmValue;
					List<GuardedString> processedValues = new ArrayList<>();
					for(int j = 0; j < idmValues.length; j++) {
						Object singleValue = idmValues[j];
						if (singleValue instanceof ConfidentialString) {
							processedValues.add(confidentialStorage.getGuardedString(
									provisioningOperation.getId(), 
									SysProvisioningOperation.class, 
									((ConfidentialString)singleValue).getKey()));
						}
					}
					if (!processedValues.isEmpty()) {
						fullAccountObject.put(entry.getKey(), processedValues.toArray(new GuardedString[processedValues.size()]));
						continue;
					}
				}
			}
			// collection
			else if (idmValue instanceof Collection) {
				Collection<?> idmValues = (Collection<?>) idmValue;
				List<GuardedString> processedValues = new ArrayList<>();
				idmValues.forEach(singleValue -> {
					if (singleValue instanceof ConfidentialString) {													
						processedValues.add(confidentialStorage.getGuardedString(
								provisioningOperation.getId(), 
								SysProvisioningOperation.class, 
								((ConfidentialString)singleValue).getKey()));
					}
				});
				if (!processedValues.isEmpty()) {
					fullAccountObject.put(entry.getKey(), processedValues);
					continue;
				}
			}
			// copy value
			fullAccountObject.put(entry.getKey(), entry.getValue());
		}	
		return fullAccountObject;
	}
	
	/**
	 * Returns fully loaded ConnectorObject with guarded strings.
	 * 
	 * TODO: don't update connectorObject in provisioningOperation (needs attribute defensive clone)
	 * 
	 * @param provisioningOperation
	 * @return
	 */
	@Override
	public IcConnectorObject getFullConnectorObject(SysProvisioningOperationDto provisioningOperation) {
		if (provisioningOperation == null 
				|| provisioningOperation.getProvisioningContext() == null 
				|| provisioningOperation.getProvisioningContext().getConnectorObject() == null) {
			return null;
		}
		List<IcAttribute> attributes = new ArrayList<>();
		//
		IcConnectorObject connectorObject = provisioningOperation.getProvisioningContext().getConnectorObject();		
		connectorObject.getAttributes().forEach(attribute -> {
			IcAttribute attributeCopy = null;
			if (attribute.isMultiValue()) {
				List<Object> values = (List<Object>)attribute.getValues();
				attributeCopy = new IcAttributeImpl(attribute.getName(), values, true);
			} else if (attribute instanceof IcPasswordAttribute && attribute.getValue() != null) {
				attributeCopy = new IcPasswordAttributeImpl(
						attribute.getName(), 
						confidentialStorage.getGuardedString(
								provisioningOperation.getId(), 
								SysProvisioningOperation.class, 
								((ConfidentialString) attribute.getValue()).getKey()));
			} else if (attribute instanceof IcPasswordAttribute && attribute.getValue() == null) {
				attributeCopy = new IcPasswordAttributeImpl(attribute.getName(), (GuardedString) null);
			} else if (attribute.getValue() instanceof ConfidentialString) {
				attributeCopy = new IcAttributeImpl(
						attribute.getName(),
						confidentialStorage.getGuardedString(
						provisioningOperation.getId(),
						SysProvisioningOperation.class,
						((ConfidentialString) attribute.getValue()).getKey()));
			} else {
				attributeCopy = new IcAttributeImpl(attribute.getName(), attribute.getValue());
			}
			attributes.add(attributeCopy);
		});
		
		IcConnectorObject newConnectorObject = new IcConnectorObjectImpl(connectorObject.getUidValue(), connectorObject.getObjectClass(), attributes);
		return newConnectorObject;
	}
	
	/**
	 * REQUIRES_NEW => we handle success in the new transaction too, we need to prepare (save) her in new transaction too.
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public SysProvisioningOperationDto saveOperation(SysProvisioningOperationDto operation) {
		if (operation.isDryRun()) {
			return operation;
		}
		return save(operation);
	}
	
	/**
	 * REQUIRES_NEW => we handle success in the new transaction too, we need to prepare (save) her in new transaction too.
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deleteOperation(SysProvisioningOperationDto operation) {
		delete(operation);
	}
	
	/**
	 * REQUIRES_NEW => we want to have log in queue / archive all time, even original transaction ends with exception.
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public SysProvisioningOperationDto handleFailed(SysProvisioningOperationDto operation, Exception ex) {
		ResultModel resultModel;
		if (ex instanceof ResultCodeException) {
			resultModel = ((ResultCodeException) ex).getError().getError();
		} else {
			String uid = getByProvisioningOperation(operation).getUid();
			resultModel = new DefaultResultModel(AccResultCode.PROVISIONING_FAILED, 
					ImmutableMap.of(
							"name", uid, 
							"system", getSystem(operation).getName(),
							"operationType", operation.getOperationType(),
							"objectClass", operation.getProvisioningContext().getConnectorObject().getObjectClass().getType()));	
		}
		LOG.error(resultModel.toString(), ex);
		//
		operation.increaseAttempt();
		operation.setMaxAttempts(provisioningConfiguration.getRetryMaxAttempts());
		operation.setResult(new OperationResult
				.Builder(OperationState.EXCEPTION)
				.setModel(resultModel)
				.setCause(ex)
				.build());
		//
		if (!operation.isDryRun()) {
			operation = save(operation);
			// create archive operation for the current attempt
			provisioningArchiveService.archive(operation);
			//
			// calculate next attempt
			SysProvisioningOperationDto firstOperation = getFirstOperationByBatchId(operation.getBatch());
			if (firstOperation.equals(operation)) {
				SysProvisioningBatchDto batch = batchService.get(operation.getBatch());
				batch.setNextAttempt(batchService.calculateNextAttempt(operation));
				batch = batchService.save(batch);
			}
			//
			if (securityService.getCurrentId() != null) { // TODO: check account owner
				// TODO: notification is moved into console ... simple LOG instead?
				notificationManager.send(
						AccModuleDescriptor.TOPIC_PROVISIONING, new IdmMessageDto.Builder()
						.setModel(resultModel)
						.build());
			}
		}
		return operation;
	}

	/**
	 * REQUIRES_NEW => we want to have log in queue / archive all time, even original transaction ends with exception (after calling this method).
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public SysProvisioningOperationDto handleSuccessful(SysProvisioningOperationDto operation) {
		ResultModel resultModel = new DefaultResultModel(
				AccResultCode.PROVISIONING_SUCCEED, 
				ImmutableMap.of(
						"name", operation.getSystemEntityUid(), // FIXME: String uid = getByProvisioningOperation(operation).getUid();
						"system", getSystem(operation).getName(),
						"operationType", operation.getOperationType(),
						"objectClass", operation.getProvisioningContext().getConnectorObject().getObjectClass().getType()));
		operation.setResult(new OperationResult.Builder(OperationState.EXECUTED).setModel(resultModel).build());
		if (!operation.isDryRun()) {
			operation = save(operation);
		
			//
			// cleanup next attempt time - batch are not removed
			SysProvisioningBatchDto batch = DtoUtils.getEmbedded(operation, SysProvisioningOperation_.batch, (SysProvisioningBatchDto) null);
			if (batch == null) {
				batch = batchService.get(operation.getBatch());
			}
			if (batch.getNextAttempt() != null) {
				batch.setNextAttempt(null);
				batch = batchService.save(batch);
			}
			//
			LOG.info(resultModel.toString());
			//
		}
		return operation;
	}
	
	@Override
	@Transactional(readOnly = true)
	public SysSystemEntityDto getByProvisioningOperation(SysProvisioningOperationDto operation) {
		return systemEntityService.getByProvisioningOperation(operation);
	}
	
	/**
	 * Replaces GuardedStrings as ConfidentialStrings in given {@link ProvisioningContext}. 
	 * 
	 * TODO: don't update accountObject in provisioningOperation (needs attribute defensive clone)
	 *
	 * @param context
	 * @return Returns values (key / value) to store in confidential storage. 
	 */
	protected Map<String, Serializable> replaceGuardedStrings(ProvisioningContext context) {
		try {
			Map<String, Serializable> confidentialValues = new HashMap<>();
			if (context == null) {
				return confidentialValues;
			}
			//
			Map<ProvisioningAttributeDto, Object> accountObject = context.getAccountObject();
			if (accountObject != null) {
				for (Entry<ProvisioningAttributeDto, Object> entry : accountObject.entrySet()) {
					if (entry.getValue() == null) {
						continue;
					}
					Object idmValue = entry.getValue();
					// single value
					if (idmValue instanceof GuardedString) {
						GuardedString guardedString = (GuardedString) entry.getValue();
						// save value into confidential storage
						String confidentialStorageKey = createAccountObjectPropertyKey(entry.getKey().getKey(), 0);
						confidentialValues.put(confidentialStorageKey, guardedString.asString());
						accountObject.put(entry.getKey(), new ConfidentialString(confidentialStorageKey));
					}
					// array
					else if(idmValue.getClass().isArray()) {
						if (!idmValue.getClass().getComponentType().isPrimitive()) {  // objects only, we dont want pto proces byte, boolean etc.
							Object[] idmValues = (Object[]) idmValue;
							List<ConfidentialString> processedValues = new ArrayList<>();
							for(int j = 0; j < idmValues.length; j++) {
								Object singleValue = idmValues[j];
								if (singleValue instanceof GuardedString) {
									GuardedString guardedString = (GuardedString) singleValue;
									// save value into confidential storage
									String confidentialStorageKey = createAccountObjectPropertyKey(entry.getKey().getKey(), j);
									confidentialValues.put(confidentialStorageKey, guardedString.asString());
									processedValues.add(new ConfidentialString(confidentialStorageKey));
								}
							}
							if (!processedValues.isEmpty()) {
								accountObject.put(entry.getKey(), processedValues.toArray(new ConfidentialString[processedValues.size()]));
							}
						}
					}
					// collection
					else if (idmValue instanceof Collection) {
						Collection<?> idmValues = (Collection<?>) idmValue;
						List<ConfidentialString> processedValues = new ArrayList<>();
						idmValues.forEach(singleValue -> {
							if (singleValue instanceof GuardedString) {
								GuardedString guardedString = (GuardedString) singleValue;
								// save value into confidential storage
								String confidentialStorageKey = createAccountObjectPropertyKey(entry.getKey().getKey(), processedValues.size());
								confidentialValues.put(confidentialStorageKey, guardedString.asString());							
								processedValues.add(new ConfidentialString(confidentialStorageKey));
							}
						});
						if (!processedValues.isEmpty()) {
							accountObject.put(entry.getKey(), processedValues);
						}
					}
					
				}
			}
			//
			IcConnectorObject connectorObject = context.getConnectorObject();
			if (connectorObject != null) {
				for(IcAttribute attribute : connectorObject.getAttributes()) {
					if(attribute.getValues() != null){
						for(int j = 0; j < attribute.getValues().size(); j++) {
							Object attributeValue = attribute.getValues().get(j);
							if (attributeValue instanceof GuardedString) {
								GuardedString guardedString = (GuardedString) attributeValue;
								String confidentialStorageKey = createConnectorObjectPropertyKey(attribute, j);
								confidentialValues.put(confidentialStorageKey, guardedString.asString());
								attribute.getValues().set(j, new ConfidentialString(confidentialStorageKey));
							}
						}
					}
				}
			}
			//
			return confidentialValues;
		} catch (Exception ex) {
			throw new CoreException("Replace guarded strings for provisioning operation failed.", ex);
		}
	}
	
	/**
	 * Creates account object property key into confidential storage
	 * 
	 * @param property
	 * @param index
	 * @return
	 */
	@Override
	public String createAccountObjectPropertyKey(String property, int index) {
		return String.format(CONFIDENTIAL_KEY_PATTERN, ACCOUNT_OBJECT_PROPERTY_PREFIX, property, index);
	}
	
	/**
	 * Creates connector object property key into confidential storage
	 * 
	 * @param property
	 * @param index
	 * @return
	 */
	@Override
	public String createConnectorObjectPropertyKey(IcAttribute property, int index) {
		return String.format(CONFIDENTIAL_KEY_PATTERN, CONNECTOR_OBJECT_PROPERTY_PREFIX, property.getName(), index);
	}
	
	@Override
	@Transactional
	public long deleteOperations(UUID systemId) {
		Assert.notNull(systemId, "System identifier is requred for delete provisioning operations.");
		//
		long deleted = repository.deleteBySystem(systemId);
		// delete attributes for the deleted operations
		provisioningAttributeService.cleanupAttributes();
		//
		LOG.warn("Deleted [{}] operations from provisioning queue of target system [{}], executed by identity [{}].",
				deleted, systemId, securityService.getCurrentId());
		//
		return deleted;
	}

	@Override
	@Transactional
	public void deleteAllOperations() {
		repository.deleteAll();
		// delete attributes for the deleted operations
		provisioningAttributeService.cleanupAttributes();
		//
		LOG.warn("Deleted all operations from provisioning queue of all target system, executed by identity [{}].",
				securityService.getCurrentId());
	}
	/**
	 * Deletes persisted confidential storage values
	 * 
	 * @param provisioningOperation
	 */
	protected void deleteConfidentialStrings(SysProvisioningOperationDto provisioningOperation) {
		Assert.notNull(provisioningOperation, "Provisioning operation is required for delete related confidential values.");
		//
		ProvisioningContext context = provisioningOperation.getProvisioningContext();
		if (context == null) {
			return;
		}
		
		Map<ProvisioningAttributeDto, Object> accountObject = context.getAccountObject();
		if (accountObject != null) {
			for (Entry<ProvisioningAttributeDto, Object> entry : accountObject.entrySet()) {
				Object idmValue = entry.getValue();
				if (idmValue == null) {
					continue;
				}
				// single value
				if (idmValue instanceof ConfidentialString) {
					confidentialStorage.delete(provisioningOperation.getId(), SysProvisioningOperation.class, ((ConfidentialString)entry.getValue()).getKey());
				}
				// array
				else if(idmValue.getClass().isArray()) {
					if (!idmValue.getClass().getComponentType().isPrimitive()) {
						Object[] idmValues = (Object[]) idmValue;
						for(int j = 0; j < idmValues.length; j++) {
							Object singleValue = idmValues[j];
							if (singleValue instanceof ConfidentialString) {
								confidentialStorage.delete(
										provisioningOperation.getId(), 
										SysProvisioningOperation.class, 
										((ConfidentialString)singleValue).getKey());
							}
						}
					}
				}
				// collection
				else if (idmValue instanceof Collection) {
					Collection<?> idmValues = (Collection<?>) idmValue;
					idmValues.forEach(singleValue -> {
						if (singleValue instanceof ConfidentialString) {
							confidentialStorage.delete(
									provisioningOperation.getId(), 
									SysProvisioningOperation.class, 
									((ConfidentialString)singleValue).getKey());
						}
					});
				}		
			}
		}
		//
		IcConnectorObject connectorObject = context.getConnectorObject();
		if (connectorObject != null) {
			connectorObject.getAttributes().forEach(attribute -> {
				if(attribute.getValues() != null){
					attribute.getValues().forEach(attributeValue -> {
						if (attributeValue instanceof ConfidentialString) {
							confidentialStorage.delete(
									provisioningOperation.getId(), 
									SysProvisioningOperation.class, 
									((ConfidentialString)attributeValue).getKey());
						}
					});	
				}
			});
		}
	}
	
	/**
	 * Optimize - system can be pre-loaded in DTO.
	 * 
	 * @param operation
	 * @return
	 */
	@Override
	public SysSystemDto getSystem(SysProvisioningOperationDto operation) {
		SysSystemDto system = DtoUtils.getEmbedded(operation, SysProvisioningOperation_.system, (SysSystemDto) null);
		if (system == null) {
			// just for sure, self constructed operation can be given
			system = systemService.get(operation.getSystem());
		}
		//
		return system;
	}
}
