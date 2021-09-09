package eu.bcvsolutions.idm.acc.service.impl;

import java.io.Serializable;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.domain.AttributeMapping;
import eu.bcvsolutions.idm.acc.domain.OperationResultType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationContext;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccContractSliceAccountDto;
import eu.bcvsolutions.idm.acc.dto.EntityAccountDto;
import eu.bcvsolutions.idm.acc.dto.SyncIdentityContractDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncActionLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncContractConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncItemLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccContractSliceAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.EntityAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemAttributeMappingFilter;
import eu.bcvsolutions.idm.acc.entity.SysSyncContractConfig_;
import eu.bcvsolutions.idm.acc.exception.ProvisioningException;
import eu.bcvsolutions.idm.acc.service.api.AccContractSliceAccountService;
import eu.bcvsolutions.idm.acc.service.api.EntityAccountService;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningService;
import eu.bcvsolutions.idm.acc.service.api.SynchronizationEntityExecutor;
import eu.bcvsolutions.idm.core.api.domain.Codeable;
import eu.bcvsolutions.idm.core.api.domain.ContractState;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.IdmContractSliceDto;
import eu.bcvsolutions.idm.core.api.dto.IdmContractSliceGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeTypeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.CorrelationFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractSliceFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractSliceGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmTreeNodeFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.service.AutomaticRoleManager;
import eu.bcvsolutions.idm.core.api.service.ContractSliceManager;
import eu.bcvsolutions.idm.core.api.service.IdmContractSliceGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmContractSliceService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.model.entity.IdmContractSlice_;
import eu.bcvsolutions.idm.core.model.entity.IdmTreeNode_;
import eu.bcvsolutions.idm.core.model.event.ContractSliceEvent;
import eu.bcvsolutions.idm.core.model.event.ContractSliceEvent.ContractSliceEventType;
import eu.bcvsolutions.idm.core.model.event.ContractSliceGuaranteeEvent;
import eu.bcvsolutions.idm.core.model.event.ContractSliceGuaranteeEvent.ContractSliceGuaranteeEventType;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmScheduledTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.Task;
import eu.bcvsolutions.idm.core.scheduler.api.dto.filter.IdmLongRunningTaskFilter;
import eu.bcvsolutions.idm.core.scheduler.api.service.IdmScheduledTaskService;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.core.scheduler.api.service.SchedulableTaskExecutor;
import eu.bcvsolutions.idm.core.scheduler.api.service.SchedulerManager;
import eu.bcvsolutions.idm.core.scheduler.task.impl.ClearDirtyStateForContractSliceTaskExecutor;
import eu.bcvsolutions.idm.core.scheduler.task.impl.ProcessAllAutomaticRoleByAttributeTaskExecutor;
import eu.bcvsolutions.idm.core.scheduler.task.impl.ProcessSkippedAutomaticRoleByTreeForContractTaskExecutor;
import eu.bcvsolutions.idm.core.scheduler.task.impl.SelectCurrentContractSliceTaskExecutor;
import eu.bcvsolutions.idm.core.scheduler.task.impl.hr.HrContractExclusionProcess;
import eu.bcvsolutions.idm.core.scheduler.task.impl.hr.HrEnableContractProcess;
import eu.bcvsolutions.idm.core.scheduler.task.impl.hr.HrEndContractProcess;
import eu.bcvsolutions.idm.ic.api.IcAttribute;

@Component
public class ContractSliceSynchronizationExecutor extends AbstractSynchronizationExecutor<IdmContractSliceDto>
		implements SynchronizationEntityExecutor {

	@Autowired
	private IdmContractSliceService sliceService;
	@Autowired
	private AccContractSliceAccountService contractAccoutnService;
	@Autowired
	private IdmTreeNodeService treeNodeService;
	@Autowired
	private LookupService lookupService;
	@Autowired
	private LongRunningTaskManager longRunningTaskManager;
	@Autowired
	private SchedulerManager schedulerService;
	@Autowired
	private IdmScheduledTaskService scheduledTaskService;
	@Autowired
	private ContractSliceManager contractSliceManager;
	@Autowired
	private IdmContractSliceGuaranteeService guaranteeService;

	public final static String CONTRACT_STATE_FIELD = IdmContractSlice_.state.getName();
	public final static String CONTRACT_GUARANTEES_FIELD = "guarantees";
	public final static String CONTRACT_IDENTITY_FIELD = IdmContractSlice_.identity.getName();
	public final static String CONTRACT_WORK_POSITION_FIELD = IdmContractSlice_.workPosition.getName();
	public final static String CONTRACT_SLICE_CONTRACT_CODE_FIELD = IdmContractSlice_.contractCode.getName();
	public final static String CONTRACT_VALID_TILL_FIELD = IdmContractSlice_.validTill.getName();
	public final static String SYNC_CONTRACT_FIELD = "sync_contract";
	public final static String DEFAULT_TASK = "Default";

	@Override
	protected SynchronizationContext validate(UUID synchronizationConfigId) {

		AbstractSysSyncConfigDto config = synchronizationConfigService.get(synchronizationConfigId);
		SysSystemMappingDto mapping = systemMappingService.get(config.getSystemMapping());
		Assert.notNull(mapping, "Mapping is required.");
		SysSystemAttributeMappingFilter attributeHandlingFilter = new SysSystemAttributeMappingFilter();
		attributeHandlingFilter.setSystemMappingId(mapping.getId());
		List<SysSystemAttributeMappingDto> mappedAttributes = systemAttributeMappingService
				.find(attributeHandlingFilter, null).getContent();

		// Owner attribute check
		mappedAttributes.stream().filter(attribute -> {
			return CONTRACT_IDENTITY_FIELD.equals(attribute.getIdmPropertyName());
		}).findFirst().orElseThrow(() -> new ProvisioningException(AccResultCode.SYNCHRONIZATION_MAPPED_ATTR_MUST_EXIST,
				ImmutableMap.of("property", CONTRACT_IDENTITY_FIELD)));

		// Code of contract attribute check
		mappedAttributes.stream().filter(attribute -> {
			return CONTRACT_SLICE_CONTRACT_CODE_FIELD.equals(attribute.getIdmPropertyName());
		}).findFirst().orElseThrow(() -> new ProvisioningException(AccResultCode.SYNCHRONIZATION_MAPPED_ATTR_MUST_EXIST,
				ImmutableMap.of("property", CONTRACT_SLICE_CONTRACT_CODE_FIELD)));

		return super.validate(synchronizationConfigId);
	}

	@Override
	protected SysSyncLogDto syncCorrectlyEnded(SysSyncLogDto log, SynchronizationContext context) {
		log = super.syncCorrectlyEnded(log, context);
		log = synchronizationLogService.save(log);
		SysSyncContractConfigDto config = getConfig(context);

		if (config.isStartOfHrProcesses()) {
			// Together with HR process is also executed cleared dirty contract slice
			log = executeClearDirtyStateForContractSlice(log);

			// start all HR process with skip automatic role recalculation
			// Enable contracts task
			log = executeHrProcess(log, new HrEnableContractProcess(true));

			// End contracts task
			log = executeHrProcess(log, new HrEndContractProcess(true));

			// Exclude contracts task
			log = executeHrProcess(log, new HrContractExclusionProcess(true));
		} else {
			log.addToLog(MessageFormat.format("Start HR processes contracts (after sync) isn't allowed [{0}]",
					ZonedDateTime.now()));
		}

		if (config.isStartAutoRoleRec()) {
			log = executeAutomaticRoleRecalculation(log);
		} else {
			log.addToLog(MessageFormat.format("Start automatic role recalculation (after sync) isn't allowed [{0}]",
					ZonedDateTime.now()));
		}

		return log;
	}

	/**
	 * Operation remove IdentityContractAccount relations and linked roles
	 * 
	 * @param account
	 * @param removeIdentityContractIdentityContract
	 * @param log
	 * @param logItem
	 * @param actionLogs
	 */
	protected void doUnlink(AccAccountDto account, boolean removeIdentityContractIdentityContract, SysSyncLogDto log,
			SysSyncItemLogDto logItem, List<SysSyncActionLogDto> actionLogs) {

		EntityAccountFilter entityAccountFilter = new AccContractSliceAccountFilter();
		entityAccountFilter.setAccountId(account.getId());
		List<AccContractSliceAccountDto> entityAccounts = contractAccoutnService
				.find((AccContractSliceAccountFilter) entityAccountFilter, null).getContent();
		if (entityAccounts.isEmpty()) {
			addToItemLog(logItem, "Warning! - Contract-account relation was not found!");
			initSyncActionLog(SynchronizationActionType.UPDATE_ENTITY, OperationResultType.WARNING, logItem, log,
					actionLogs);
			return;
		}
		addToItemLog(logItem, MessageFormat.format("Contract-account relations to delete {0}", entityAccounts));

		entityAccounts.stream().forEach(entityAccount -> {
			// We will remove contract account, but without delete connected
			// account
			contractAccoutnService.delete(entityAccount, false);
			addToItemLog(logItem, MessageFormat.format(
					"Contract-account relation deleted (without call delete provisioning) (contract id: {0}, contract-account id: {1})",
					entityAccount.getSlice(), entityAccount.getId()));

		});
		return;
	}

	/**
	 * Fill entity with attributes from IC module (by mapped attributes).
	 * 
	 * @param mappedAttributes
	 * @param uid
	 * @param icAttributes
	 * @param entity
	 * @param create
	 *            (is create or update entity situation)
	 * @param context
	 * @return
	 */
	protected IdmContractSliceDto fillEntity(List<SysSystemAttributeMappingDto> mappedAttributes, String uid,
			List<IcAttribute> icAttributes, IdmContractSliceDto dto, boolean create, SynchronizationContext context) {
		mappedAttributes.stream().filter(attribute -> {
			// Skip disabled attributes
			// Skip extended attributes (we need update/ create entity first)
			// Skip confidential attributes (we need update/ create entity
			// first)
			boolean fastResult = !attribute.isDisabledAttribute() && attribute.isEntityAttribute()
					&& !attribute.isConfidentialAttribute();
			if (!fastResult) {
				return false;
			}
			// Can be value set by attribute strategy?
			return this.canSetValue(uid, attribute, dto, create);

		}).forEach(attribute -> {
			String attributeProperty = attribute.getIdmPropertyName();
			Object transformedValue = getValueByMappedAttribute(attribute, icAttributes, context);
			// Guarantees will be set no to the DTO (we does not have field for
			// they), but to the embedded map.
			if (CONTRACT_GUARANTEES_FIELD.equals(attributeProperty)) {
				if (transformedValue instanceof SyncIdentityContractDto) {
					dto.getEmbedded().put(SYNC_CONTRACT_FIELD, (SyncIdentityContractDto) transformedValue);
				} else {
					dto.getEmbedded().put(SYNC_CONTRACT_FIELD, new SyncIdentityContractDto());
				}
				// Check if new guarantees are different than current guarantees.
				SyncIdentityContractDto syncIdentityContractDto = (SyncIdentityContractDto) dto.getEmbedded().get(SYNC_CONTRACT_FIELD);
				if (syncIdentityContractDto != null && !context.isEntityDifferent()) {
					List<IdmIdentityDto> newGuarantees = syncIdentityContractDto.getGuarantees();
					if (!isGuaranteesSame(dto, newGuarantees)) {
						// Guarantees are different
						context.setIsEntityDifferent(true);
						addToItemLog(context.getLogItem(), MessageFormat.format("Value of entity attribute [{0}] was changed. Entity in IdM will be updated.", attributeProperty));
					}
				}
				return;
			}
			// Valid till attribute is sets only if is that slice last!
			if (CONTRACT_VALID_TILL_FIELD.equals(attributeProperty) && dto.getParentContract() != null) {
				IdmContractSliceDto nextSlice = contractSliceManager.findNextSlice(dto,
						contractSliceManager.findAllSlices(dto.getParentContract()));
				if (nextSlice != null) {
					context.getLogItem().addToLog(
							"Warning! - Valid till field wasn't changed, because the that slice is not a last!");
					return;
				}
			}
			// Set transformed value from target system to entity
			setEntityValue(uid, dto, context, attribute, attributeProperty, transformedValue);

		});
		return dto;
	}

	@Override
	protected Object getValueByMappedAttribute(AttributeMapping attribute, List<IcAttribute> icAttributes,
			SynchronizationContext context) {
		Object transformedValue = super.getValueByMappedAttribute(attribute, icAttributes, context);
		// Transform contract state enumeration from string
		if (CONTRACT_STATE_FIELD.equals(attribute.getIdmPropertyName()) && transformedValue instanceof String
				&& attribute.isEntityAttribute()) {
			return ContractState.valueOf((String) transformedValue);
		}
		// Transform contract guarantees
		if (CONTRACT_GUARANTEES_FIELD.equals(attribute.getIdmPropertyName()) && attribute.isEntityAttribute()) {
			return transformGuarantees(context, transformedValue);
		}
		// Transform work position (tree node)
		if (CONTRACT_WORK_POSITION_FIELD.equals(attribute.getIdmPropertyName()) && attribute.isEntityAttribute()) {

			if (transformedValue != null) {
				IdmTreeNodeDto workposition = this.findTreeNode(transformedValue, context);
				if (workposition != null) {
					return workposition.getId();
				}
				return null;
			} else {
				if (getConfig(context).getDefaultTreeNode() != null) {
					UUID defaultNode = ((SysSyncContractConfigDto) context.getConfig()).getDefaultTreeNode();
					IdmTreeNodeDto node = (IdmTreeNodeDto) lookupService.lookupDto(IdmTreeNodeDto.class, defaultNode);
					if (node != null) {
						context.getLogItem().addToLog(MessageFormat.format(
								"Warning! - None workposition was defined for this realtion, we use default workposition [{0}]!",
								node.getCode()));
						return node.getId();
					}
				}
			}
		}
		// Transform contract owner
		if (transformedValue != null && CONTRACT_IDENTITY_FIELD.equals(attribute.getIdmPropertyName())
				&& attribute.isEntityAttribute()) {
			context.getLogItem().addToLog(MessageFormat.format("Finding contract owner [{0}].", transformedValue));
			IdmIdentityDto identity = this.findIdentity(transformedValue, context);
			if (identity == null) {
				throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_IDM_FIELD_CANNOT_BE_NULL,
						ImmutableMap.of("property", CONTRACT_IDENTITY_FIELD));
			}
			return identity.getId();
		}
		// Code of contract cannot be null
		if (transformedValue == null && CONTRACT_SLICE_CONTRACT_CODE_FIELD.equals(attribute.getIdmPropertyName())
				&& attribute.isEntityAttribute()) {
			throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_IDM_FIELD_CANNOT_BE_NULL,
					ImmutableMap.of("property", CONTRACT_SLICE_CONTRACT_CODE_FIELD));
		}

		return transformedValue;
	}

	private Object transformGuarantees(SynchronizationContext context, Object transformedValue) {
		if (transformedValue != null) {
			SyncIdentityContractDto syncContract = new SyncIdentityContractDto();
			if (transformedValue instanceof List) {
				((List<?>) transformedValue).stream().forEach(guarantee -> {

					// Beware this DTO contains only identity ID, not
					// contract ... must be save separately.
					context.getLogItem().addToLog(MessageFormat.format("Finding guarantee [{0}].", guarantee));
					IdmIdentityDto guarranteeDto = this.findIdentity(guarantee, context);
					if (guarranteeDto != null) {
						context.getLogItem()
								.addToLog(MessageFormat.format("Guarantee [{0}] was found.", guarranteeDto.getCode()));
						syncContract.getGuarantees().add(guarranteeDto);
					}
				});
			} else {
				// Beware this DTO contains only identity ID, not
				// contract ... must be save separately.
				context.getLogItem().addToLog(MessageFormat.format("Finding guarantee [{0}].", transformedValue));
				IdmIdentityDto guarranteeDto = this.findIdentity(transformedValue, context);
				if (guarranteeDto != null) {
					context.getLogItem()
							.addToLog(MessageFormat.format("Guarantee [{0}] was found.", guarranteeDto.getCode()));
					syncContract.getGuarantees().add(guarranteeDto);
				}
			}
			transformedValue = syncContract;
		} else {
			if (getConfig(context).getDefaultLeader() != null) {
				UUID defaultLeader = ((SysSyncContractConfigDto) context.getConfig()).getDefaultLeader();
				IdmIdentityDto identity = (IdmIdentityDto) lookupService.lookupDto(IdmIdentityDto.class, defaultLeader);
				if (identity != null) {
					SyncIdentityContractDto syncContract = new SyncIdentityContractDto();
					syncContract.getGuarantees().add(identity);
					transformedValue = syncContract;
					context.getLogItem()
							.addToLog(MessageFormat.format(
									"Warning! - None leader was found for this realtion, we use default leader [{0}]!",
									identity.getCode()));
				}
			}
		}
		return transformedValue;
	}

	private IdmIdentityDto findIdentity(Object value, SynchronizationContext context) {
		if (value instanceof Serializable) {
			IdmIdentityDto identity = (IdmIdentityDto) lookupService.lookupDto(IdmIdentityDto.class,
					(Serializable) value);

			if (identity == null) {
				context.getLogItem()
						.addToLog(MessageFormat.format("Warning! - Identity [{0}] was not found for [{0}]!", value));
				this.initSyncActionLog(context.getActionType(), OperationResultType.WARNING, context.getLogItem(),
						context.getLog(), context.getActionLogs());
				return null;
			}

			return identity;
		} else {
			context.getLogItem()
					.addToLog(MessageFormat.format(
							"Warning! - Identity cannot be found, because transformed value [{0}] is not Serializable!",
							value));
			this.initSyncActionLog(context.getActionType(), OperationResultType.WARNING, context.getLogItem(),
					context.getLog(), context.getActionLogs());
		}
		return null;
	}

	private IdmTreeNodeDto findTreeNode(Object value, SynchronizationContext context) {
		if (value instanceof Serializable) {
			// Find by UUID
			context.getLogItem().addToLog(
					MessageFormat.format("Work position - try find directly by transformed value [{0}]!", value));
			IdmTreeNodeDto node = (IdmTreeNodeDto) lookupService.lookupDto(IdmTreeNodeDto.class, (Serializable) value);

			if (node != null) {
				IdmTreeTypeDto treeTypeDto = DtoUtils.getEmbedded(node, IdmTreeNode_.treeType);
				context.getLogItem().addToLog(MessageFormat.format(
						"Work position - One node [{1}] (in tree type [{2}]) was found directly by transformed value [{0}]!",
						value, node.getCode(), treeTypeDto.getCode()));
				return node;
			}
			context.getLogItem().addToLog(MessageFormat
					.format("Work position - was not not found directly from transformed value [{0}]!", value));
			if (value instanceof String && StringUtils.isNotEmpty(( String) value)) {
				// Find by code in default tree type
				SysSyncContractConfigDto config = this.getConfig(context);
				if (config.getDefaultTreeType() == null) {
					context.getLogItem().addToLog(MessageFormat.format(
							"Warning - Work position - we cannot finding node by code [{0}], because default tree type is not set (in sync configuration)!",
							value));
					this.initSyncActionLog(context.getActionType(), OperationResultType.WARNING, context.getLogItem(),
							context.getLog(), context.getActionLogs());
					return null;
				}
				IdmTreeNodeFilter treeNodeFilter = new IdmTreeNodeFilter();
				IdmTreeTypeDto defaultTreeType = DtoUtils.getEmbedded(config, SysSyncContractConfig_.defaultTreeType);
				treeNodeFilter.setTreeTypeId(config.getDefaultTreeType());
				treeNodeFilter.setCode((String) value);
				context.getLogItem()
						.addToLog(MessageFormat.format(
								"Work position - try find in default tree type [{1}] with code [{0}]!", value,
								defaultTreeType.getCode()));
				List<IdmTreeNodeDto> nodes = treeNodeService.find(treeNodeFilter, null).getContent();
				if (nodes.isEmpty()) {
					context.getLogItem().addToLog(
							MessageFormat.format("Warning - Work position - none node found for code [{0}]!", value));
					this.initSyncActionLog(context.getActionType(), OperationResultType.WARNING, context.getLogItem(),
							context.getLog(), context.getActionLogs());
					return null;
				} else {
					context.getLogItem().addToLog(MessageFormat.format(
							"Work position - One node [{1}] was found for code [{0}]!", value, nodes.get(0).getId()));
					return nodes.get(0);
				}
			}
		} else {
			context.getLogItem().addToLog(MessageFormat.format(
					"Warning! - Work position cannot be found, because transformed value [{0}] is not Serializable!",
					value));
			this.initSyncActionLog(context.getActionType(), OperationResultType.WARNING, context.getLogItem(),
					context.getLog(), context.getActionLogs());
		}
		return null;
	}

	private SysSyncContractConfigDto getConfig(SynchronizationContext context) {
		Assert.isInstanceOf(SysSyncContractConfigDto.class, context.getConfig(),
				"For identity sync must be sync configuration instance of SysSyncContractConfigDto!");
		return ((SysSyncContractConfigDto) context.getConfig());
	}

	/**
	 * Save entity
	 * 
	 * @param entity
	 * @param skipProvisioning
	 * @return
	 */
	@Override
	protected IdmContractSliceDto save(IdmContractSliceDto entity, boolean skipProvisioning, SynchronizationContext context) {

		if (entity.getIdentity() == null) {
			throw new ProvisioningException(AccResultCode.SYNCHRONIZATION_IDM_FIELD_CANNOT_BE_NULL,
					ImmutableMap.of("property", CONTRACT_IDENTITY_FIELD));
		}

		EntityEvent<IdmContractSliceDto> event = new ContractSliceEvent(
				sliceService.isNew(entity) ? ContractSliceEventType.CREATE : ContractSliceEventType.UPDATE, entity,
				ImmutableMap.of(ProvisioningService.SKIP_PROVISIONING, skipProvisioning));
		// We do not want execute HR processes for every contract. We need start
		// them for every identity only once.
		// For this we skip them now. HR processes must be start after whole
		// sync finished (by using dependent scheduled task)!
		event.getProperties().put(IdmIdentityContractService.SKIP_HR_PROCESSES, Boolean.TRUE);
		//
		// We don't want recalculate automatic role by attribute recalculation for every
		// contract.
		// Recalculation will be started only once.
		event.getProperties().put(AutomaticRoleManager.SKIP_RECALCULATION, Boolean.TRUE);
		//
		// Set dirty state during recalculation, the process of recalculation will be solved in ClearDirtyStateForContractSliceTaskExecutor
		// ClearDirtyStateForContractSliceTaskExecutor will be started with HR processes.
		event.getProperties().put(IdmContractSliceService.SET_DIRTY_STATE_CONTRACT_SLICE, Boolean.TRUE);

		IdmContractSliceDto slice = sliceService.publish(event).getContent();

		if (entity.getEmbedded().containsKey(SYNC_CONTRACT_FIELD)) {
			SyncIdentityContractDto syncContract = (SyncIdentityContractDto) entity.getEmbedded()
					.get(SYNC_CONTRACT_FIELD);
			IdmContractSliceGuaranteeFilter guaranteeFilter = new IdmContractSliceGuaranteeFilter();
			guaranteeFilter.setContractSliceId(slice.getId());

			List<IdmContractSliceGuaranteeDto> currentGuarantees = guaranteeService.find(guaranteeFilter, null)
					.getContent();

			// Search guarantees to delete
			List<IdmContractSliceGuaranteeDto> guaranteesToDelete = currentGuarantees.stream()
					.filter(sysImplementer -> {
						return sysImplementer.getGuarantee() != null && !syncContract.getGuarantees()
								.contains(new IdmIdentityDto(sysImplementer.getGuarantee()));
					}).collect(Collectors.toList());

			// Search guarantees to add
			List<IdmIdentityDto> guaranteesToAdd = syncContract.getGuarantees().stream().filter(identity -> {
				return !currentGuarantees.stream().filter(currentGuarrantee -> {
					return identity.getId().equals(currentGuarrantee.getGuarantee());
				}).findFirst().isPresent();
			}).collect(Collectors.toList());

			// Delete guarantees
			guaranteesToDelete.forEach(guarantee -> {
				EntityEvent<IdmContractSliceGuaranteeDto> guaranteeEvent = new ContractSliceGuaranteeEvent(
						ContractSliceGuaranteeEventType.DELETE, guarantee,
						ImmutableMap.of(ProvisioningService.SKIP_PROVISIONING, skipProvisioning));
				guaranteeService.publish(guaranteeEvent);
			});

			// Create new guarantees
			guaranteesToAdd.forEach(identity -> {
				IdmContractSliceGuaranteeDto guarantee = new IdmContractSliceGuaranteeDto();
				guarantee.setContractSlice(slice.getId());
				guarantee.setGuarantee(identity.getId());
				//
				EntityEvent<IdmContractSliceGuaranteeDto> guaranteeEvent = new ContractSliceGuaranteeEvent(
						ContractSliceGuaranteeEventType.CREATE, guarantee,
						ImmutableMap.of(ProvisioningService.SKIP_PROVISIONING, skipProvisioning));
				guaranteeService.publish(guaranteeEvent);
			});
		}

		return slice;
	}
	
	/**
	 * Delete entity linked with given account
	 * 
	 * @param account
	 * @param entityType
	 * @param log
	 * @param logItem
	 * @param actionLogs
	 */
	protected void doDeleteEntity(AccAccountDto account, SystemEntityType entityType, SysSyncLogDto log,
			SysSyncItemLogDto logItem, List<SysSyncActionLogDto> actionLogs) {
		IdmContractSliceDto dto =  this.getDtoByAccount(null, account);
		if (dto == null) {
			addToItemLog(logItem,  MessageFormat.format("Warning! - Entity for account [{0}] was not found!", account.getUid()));
			initSyncActionLog(SynchronizationActionType.DELETE_ENTITY, OperationResultType.WARNING, logItem, log,
					actionLogs);
			return;
		}
		String entityIdentification = dto.getId().toString();
		if (dto instanceof Codeable) {
			entityIdentification = ((Codeable) dto).getCode();
		}
		logItem.setDisplayName(entityIdentification);
		// Delete entity
		
		// Set dirty state during recalculation, the process of recalculation will be
		// solved in ClearDirtyStateForContractSliceTaskExecutor
		// ClearDirtyStateForContractSliceTaskExecutor will be started with HR
		// processes.
		EntityEvent<IdmContractSliceDto> event = new ContractSliceEvent(ContractSliceEventType.DELETE, dto,
				ImmutableMap.of(IdmContractSliceService.SET_DIRTY_STATE_CONTRACT_SLICE, Boolean.TRUE));

		// We do not want execute HR processes for every contract. We need start
		// them for every identity only once.
		// For this we skip them now. HR processes must be start after whole
		// sync finished (by using dependent scheduled task)!
		event.getProperties().put(IdmIdentityContractService.SKIP_HR_PROCESSES, Boolean.TRUE);
		//
		// We don't want recalculate automatic role by attribute recalculation for every
		// contract.
		// Recalculation will be started only once.
		event.getProperties().put(AutomaticRoleManager.SKIP_RECALCULATION, Boolean.TRUE);
		getService().publish(event);
	}
	
	

	@Override
	protected EntityAccountFilter createEntityAccountFilter() {
		return new AccContractSliceAccountFilter();
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected EntityAccountService<EntityAccountDto, EntityAccountFilter> getEntityAccountService() {
		return (EntityAccountService)contractAccoutnService;
	}


	@Override
	protected EntityAccountDto createEntityAccountDto() {
		return new AccContractSliceAccountDto();
	}

	@Override
	protected IdmContractSliceDto createEntityDto() {
		return new IdmContractSliceDto();
	}

	@Override
	protected IdmContractSliceService getService() {
		return sliceService;
	}

	@Override
	protected CorrelationFilter getEntityFilter(SynchronizationContext context) {
		return new IdmContractSliceFilter();
	}
	
	/**
	 * Check if is supported provisioning for given entity type.
	 * 
	 * @param entityType
	 * @param logItem
	 * @return
	 */
	@Override
	protected boolean isProvisioningImplemented(SystemEntityType entityType, SysSyncItemLogDto logItem) {
		// Contract does not supports provisioning, but we need publish 'save' event,
		// because identity provisioning still should be executed.
		return true;

	}
	
	/**
	 * Call provisioning for given account
	 * 
	 * @param entity
	 * @param entityType
	 * @param logItem
	 */
	@Override
	protected void callProvisioningForEntity(IdmContractSliceDto entity, SystemEntityType entityType,
			SysSyncItemLogDto logItem) {
		addToItemLog(logItem, MessageFormat.format(
				"Call provisioning (process IdmContractSliceDto.UPDATE) for contract ({0}) with position ({1}).",
				entity.getId(), entity.getPosition()));
		ContractSliceEvent event = new ContractSliceEvent(ContractSliceEventType.UPDATE, entity);
		// We do not want execute HR processes for every contract. We need start
		// them for every identity only once.
		// For this we skip them now. HR processes must be start after whole
		// sync finished (by using dependent scheduled task)!
		event.getProperties().put(IdmIdentityContractService.SKIP_HR_PROCESSES, Boolean.TRUE);
		//
		// We don't want recalculate automatic role by attribute recalculation for every
		// contract.
		// Recalculation will be started only once.
		event.getProperties().put(AutomaticRoleManager.SKIP_RECALCULATION, Boolean.TRUE);
		//
		// Skip recalculation, now isn't needed
		event.getProperties().put(IdmContractSliceService.SKIP_RECALCULATE_CONTRACT_SLICE, Boolean.TRUE);

		entityEventManager.process(event);
	}

	/**
	 * Execute {@link SelectCurrentContractSliceTaskExecutor}
	 *
	 * @param log
	 * @return
	 */
	private SysSyncLogDto executeClearDirtyStateForContractSlice(SysSyncLogDto log) {
		ClearDirtyStateForContractSliceTaskExecutor executor = new ClearDirtyStateForContractSliceTaskExecutor();
		executor.setRequireNewTransaction(true);

		log.addToLog(MessageFormat.format(
				"After success sync have to be run clear dirty state for contract slices. We start him (synchronously) now [{0}].",
				ZonedDateTime.now()));

		OperationResult executeSync = longRunningTaskManager.executeSync(executor);
		if (executeSync != null) {
			if (executeSync.getState() == OperationState.EXECUTED) {
				log.addToLog(MessageFormat.format("Clear dirty state end in [{0}].",
						ZonedDateTime.now()));
			} else if (executeSync.getState() == OperationState.EXCEPTION) {
				log.addToLog(MessageFormat.format("Warning - clear dirty state is not executed correctly. Ended in [{0}].",
						ZonedDateTime.now()));
			}
		} else {
			log.addToLog(MessageFormat.format("Warning - select corrent contract slice is not executed correctly, Returned operation result is null. Ended in [{0}].",
					ZonedDateTime.now()));
		}

		return log;
	}
	
	/**
	 * Start automatic roles recalculation synchronously.
	 *
	 * @param log
	 * @return
	 */
	private SysSyncLogDto executeAutomaticRoleRecalculation(SysSyncLogDto log) {

		log.addToLog(MessageFormat.format(
				"After success sync have to recount automatic roles (by attribute and tree structure). We start recount automatic roles by attribute (synchronously) now [{0}].",
				ZonedDateTime.now()));
		Boolean executed = longRunningTaskManager.executeSync(new ProcessAllAutomaticRoleByAttributeTaskExecutor());
		if (BooleanUtils.isTrue(executed)) {
			log.addToLog(MessageFormat.format("Recalculation automatic role by attribute ended in [{0}].",
					ZonedDateTime.now()));
		} else if (executed == null) {
			log.addToLog(MessageFormat.format("Recalculation of automatic roles by attributes ended in [{0}], role requests will be processed asynchronously.",
					ZonedDateTime.now()));
		} else {
			addToItemLog(log, "Warning - recalculation automatic role by attribute is not executed correctly.");
		}

		log.addToLog(MessageFormat.format(
				"We start recount automatic roles by tree structure (synchronously) now [{0}].",
				ZonedDateTime.now()));
		executed = longRunningTaskManager.executeSync(new ProcessSkippedAutomaticRoleByTreeForContractTaskExecutor());
		if (BooleanUtils.isTrue(executed)) {
			log.addToLog(MessageFormat.format("Recalculation automatic role by tree structure ended in [{0}].",
					ZonedDateTime.now()));
		} else if (executed == null) {
			log.addToLog(MessageFormat.format("Recalculation of automatic roles by tree structure ended in [{0}], role requests will be processed asynchronously.",
					ZonedDateTime.now()));
		} else {
			addToItemLog(log, "Warning - recalculation automatic role by tree structure is not executed correctly.");
		}
		
		return synchronizationLogService.save(log);
	}

	/**
	 * Start HR process. Find quartz task and LRT. If some LRT for this task type
	 * exists, then is used. If not exists, then is created new. Task is execute
	 * synchronously.
	 * 
	 * @param log
	 * @param executor
	 * @return
	 */
	private SysSyncLogDto executeHrProcess(SysSyncLogDto log, SchedulableTaskExecutor<?> executor) {

		@SuppressWarnings("unchecked")
		Class<? extends SchedulableTaskExecutor<?>> taskType = (Class<? extends SchedulableTaskExecutor<?>>) executor
				.getClass();

		IdmLongRunningTaskFilter filter = new IdmLongRunningTaskFilter();
		filter.setOperationState(OperationState.CREATED);
		filter.setTaskType(taskType.getCanonicalName());
		List<IdmLongRunningTaskDto> createdLrts = longRunningTaskManager.findLongRunningTasks(filter, null).getContent();

		IdmLongRunningTaskDto lrt = null;
		String simpleName = taskType.getSimpleName();
		if (createdLrts.isEmpty()) {
			// We do not have LRT for this task, we will create him
			Task processTask = findTask(taskType);
			if (processTask == null) {
				addToItemLog(log, MessageFormat.format(
						"Warning - HR process [{0}] cannot be executed, because task for this type was not found!",
						simpleName));
				log = synchronizationLogService.save(log);
				return log;
			}
			IdmScheduledTaskDto scheduledTask = scheduledTaskService.findByQuartzTaskName(processTask.getId());
			if (scheduledTask == null) {
				addToItemLog(log, MessageFormat.format(
						"Warning - HR process [{0}] cannot be executed, because scheduled task for this type was not found!",
						simpleName));
				log = synchronizationLogService.save(log);
				return log;
			}
			lrt = longRunningTaskManager.resolveLongRunningTask(executor, scheduledTask.getId(), OperationState.RUNNING);
		} else {
			lrt = createdLrts.get(0);
		}

		if (lrt != null) {
			log.addToLog(MessageFormat.format(
					"After success sync have to be run HR task [{1}]. We start him (synchronously) now [{0}]. LRT ID: [{2}]",
					ZonedDateTime.now(), simpleName, lrt.getId()));
			log = synchronizationLogService.save(log);
			executor.setLongRunningTaskId(lrt.getId());
			longRunningTaskManager.executeSync(executor);
			log.addToLog(MessageFormat.format("HR task [{1}] ended in [{0}].", ZonedDateTime.now(), simpleName));
			log = synchronizationLogService.save(log);
		}
		return log;
	}
	

	/**
	 * Find quartz task for given task type. If existed more then one task for same
	 * type, then is using that with name "Default". If none with this name exists,
	 * then is used first.
	 * 
	 * @param taskType
	 * @return
	 */
	private Task findTask(Class<? extends SchedulableTaskExecutor<?>> taskType) {
		List<Task> tasks = schedulerService.getAllTasksByType(taskType);
		if (tasks.size() == 1) {
			return tasks.get(0);
		}
		if (tasks.isEmpty()) {
			return null;
		}

		Task defaultTask = tasks.stream().filter(task -> {
			return task.getDescription().equals(DEFAULT_TASK);
		}).findFirst().orElse(null);
		if (defaultTask != null) {
			return defaultTask;
		}
		return tasks.get(0);
	}
	
	/**
	 * Check if current contract's slices guarantees are same as in account values
	 * 
	 * @param dto
	 * @param newGuarantees
	 * @return
	 */
	private boolean isGuaranteesSame(IdmContractSliceDto dto, List<IdmIdentityDto> newGuarantees) {
		// Guarantees
		IdmContractSliceGuaranteeFilter guaranteeFilter = new IdmContractSliceGuaranteeFilter();
		guaranteeFilter.setContractSliceId(dto.getId());

		List<IdmContractSliceGuaranteeDto> currentGuarantees = guaranteeService.find(guaranteeFilter, null).getContent();
		List<UUID> currentGuranteeIds = currentGuarantees.stream().map(gurrantee -> {
			return gurrantee.getGuarantee();
		}).collect(Collectors.toList());
		
		List<UUID> newGuranteeIds = newGuarantees.stream().map(gurrantee -> {
			return gurrantee.getId();
		}).collect(Collectors.toList());
		
		return CollectionUtils.isEqualCollection(currentGuranteeIds, newGuranteeIds);
	}
}
