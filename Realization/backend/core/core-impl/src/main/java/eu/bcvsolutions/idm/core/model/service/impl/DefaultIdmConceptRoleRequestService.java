package eu.bcvsolutions.idm.core.model.service.impl;

import eu.bcvsolutions.idm.core.api.exception.InvalidFormException;
import eu.bcvsolutions.idm.core.api.service.ContractSliceManager;
import java.io.Serializable;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import java.util.stream.Collectors;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.collections.CollectionUtils;
import org.modelmapper.PropertyMap;
import org.modelmapper.TypeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.Loggable;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmConceptRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.exception.ForbiddenEntityException;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.AbstractReadWriteDtoService;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.service.ValueGeneratorManager;
import eu.bcvsolutions.idm.core.api.service.thin.IdmIdentityRoleThinService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.InvalidFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormValue_;
import eu.bcvsolutions.idm.core.model.entity.IdmAutomaticRole;
import eu.bcvsolutions.idm.core.model.entity.IdmAutomaticRoleAttribute;
import eu.bcvsolutions.idm.core.model.entity.IdmAutomaticRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmConceptRoleRequest;
import eu.bcvsolutions.idm.core.model.entity.IdmConceptRoleRequest_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleRequest_;
import eu.bcvsolutions.idm.core.model.entity.IdmRole_;
import eu.bcvsolutions.idm.core.model.repository.IdmAutomaticRoleRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmConceptRoleRequestRepository;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;
import eu.bcvsolutions.idm.core.workflow.model.dto.DecisionFormTypeDto;
import eu.bcvsolutions.idm.core.workflow.model.dto.WorkflowFilterDto;
import eu.bcvsolutions.idm.core.workflow.model.dto.WorkflowHistoricProcessInstanceDto;
import eu.bcvsolutions.idm.core.workflow.model.dto.WorkflowProcessInstanceDto;
import eu.bcvsolutions.idm.core.workflow.model.dto.WorkflowTaskInstanceDto;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowHistoricProcessInstanceService;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowProcessInstanceService;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowTaskInstanceService;

/**
 * Default implementation of concept role request service
 * 
 * @author svandav
 * @author Radek Tomiška
 */
@Service("conceptRoleRequestService")
public class DefaultIdmConceptRoleRequestService extends
		AbstractReadWriteDtoService<IdmConceptRoleRequestDto, IdmConceptRoleRequest, IdmConceptRoleRequestFilter>
		implements IdmConceptRoleRequestService {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory
			.getLogger(DefaultIdmConceptRoleRequestService.class);
	private final WorkflowProcessInstanceService workflowProcessInstanceService;
	private final LookupService lookupService;
	private final IdmAutomaticRoleRepository automaticRoleRepository;
	@Autowired
	private WorkflowTaskInstanceService workflowTaskInstanceService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private FormService formService;
	@Autowired
	private IdmIdentityRoleThinService identityRoleThinService;
	@Autowired
	private ValueGeneratorManager valueGeneratorManager;
	@Autowired
	private WorkflowHistoricProcessInstanceService historicProcessService;

	@Autowired
	public DefaultIdmConceptRoleRequestService(IdmConceptRoleRequestRepository repository,
			WorkflowProcessInstanceService workflowProcessInstanceService, LookupService lookupService,
			IdmAutomaticRoleRepository automaticRoleRepository) {
		super(repository);
		//
		Assert.notNull(workflowProcessInstanceService, "Workflow process instance service is required!");
		Assert.notNull(lookupService, "Service is required.");
		Assert.notNull(automaticRoleRepository, "Repository is required.");
		//
		this.workflowProcessInstanceService = workflowProcessInstanceService;
		this.lookupService = lookupService;
		this.automaticRoleRepository = automaticRoleRepository;
	}

	@Override
	public AuthorizableType getAuthorizableType() {
		// secured internally by role requests
		return null;
	}

	@Override
	public IdmConceptRoleRequest checkAccess(IdmConceptRoleRequest entity, BasePermission... permission) {
		if (entity == null) {
			// nothing to check
			return null;
		}

		if (ObjectUtils.isEmpty(permission)) {
			return entity;
		}

		// We can delete the concept if we have UPDATE permission on request
		Set<BasePermission> permissionsForRequest = Sets.newHashSet(); 
		for (BasePermission p : permission) {
			if (p.equals(IdmBasePermission.DELETE)) {
				permissionsForRequest.add(IdmBasePermission.UPDATE);
			} else {
				permissionsForRequest.add(p);
			}
		}
		
		// We have rights on the concept, when we have rights on whole request
		if (getAuthorizationManager().evaluate(entity.getRoleRequest(), permissionsForRequest.toArray(new BasePermission[0]))) {
			return entity;
		}

		// We have rights on the concept, when we have rights on workflow process using in the concept.
		// Beware, concet can use different WF process than whole request. So we need to check directly process on concept!
		String processId = entity.getWfProcessId();
		if (!Strings.isNullOrEmpty(processId)) {
			WorkflowProcessInstanceDto processInstance = workflowProcessInstanceService.get(processId, true);
			if (processInstance != null) {
				return entity;
			}
			if (processInstance == null) {
				// Ok process was not returned, but we need to check historic process (on involved user) too.
				WorkflowHistoricProcessInstanceDto historicProcess = historicProcessService.get(processId);
				if (historicProcess != null) {
					return entity;
				}
			}
		}

		throw new ForbiddenEntityException((BaseEntity)entity, permission);
	}

	@Override
	protected IdmConceptRoleRequestDto toDto(IdmConceptRoleRequest entity, IdmConceptRoleRequestDto dto) {
		dto = super.toDto(entity, dto);
		if (dto == null) {
			return null;
		}
		//
		// Contract from identity role has higher priority then contract ID in concept
		// role
		if (entity != null && entity.getIdentityRole() != null) {
			dto.setIdentityContract(entity.getIdentityRole().getIdentityContract().getId());
		}
		//
		// we must set automatic role to role tree node
		if (entity != null && entity.getAutomaticRole() != null) {
			dto.setAutomaticRole(entity.getAutomaticRole().getId());
			IdmAutomaticRole automaticRole = entity.getAutomaticRole();
			Map<String, BaseDto> embedded = dto.getEmbedded();
			//
			BaseDto baseDto = null;
			if (automaticRole instanceof IdmAutomaticRoleAttribute) {
				baseDto = lookupService.getDtoService(IdmAutomaticRoleAttributeDto.class).get(automaticRole.getId());
			} else {
				baseDto = lookupService.getDtoService(IdmRoleTreeNodeDto.class).get(automaticRole.getId());
			}
			embedded.put("roleTreeNode", baseDto); // roleTreeNode must be placed there as string, in meta model isn't
													// any attribute like this
			dto.setEmbedded(embedded);
		}

		return dto;
	}

	@Override
	@Transactional(readOnly = true)
	public IdmConceptRoleRequest toEntity(IdmConceptRoleRequestDto dto, IdmConceptRoleRequest entity) {
		if (dto == null) {
			return null;
		}
		
		if (dto.getId() == null) {
			dto.setState(RoleRequestState.CONCEPT);
		}
		//
		// field automatic role exists in entity but not in dto
		TypeMap<IdmConceptRoleRequestDto, IdmConceptRoleRequest> typeMap = modelMapper.getTypeMap(getDtoClass(),
				getEntityClass());
		if (typeMap == null) {
			modelMapper.createTypeMap(getDtoClass(), getEntityClass());
			typeMap = modelMapper.getTypeMap(getDtoClass(), getEntityClass());
			typeMap.addMappings(new PropertyMap<IdmConceptRoleRequestDto, IdmConceptRoleRequest>() {

				@Override
				protected void configure() {
					this.skip().setAutomaticRole(null);
				}
			});
		}
		//
		if (entity != null) {
			modelMapper.map(dto, entity);
		} else {
			entity = modelMapper.map(dto, getEntityClass(dto));
		}
		// set additional automatic role
		if (entity != null) {
			if (dto.getAutomaticRole() != null) {
				// it isn't possible use lookupService entity lookup
				IdmAutomaticRole automaticRole = automaticRoleRepository.findById(dto.getAutomaticRole()).orElse(null);
				entity.setAutomaticRole(automaticRole);
			} else {
				// relation was removed
				entity.setAutomaticRole(null);
			}
		}
		return entity;
	}

	@Override
	@Transactional
	public IdmConceptRoleRequestDto cancel(IdmConceptRoleRequestDto dto) {
		cancelWF(dto);
		dto.setState(RoleRequestState.CANCELED);
		return this.save(dto);
	}

	@Override
	@Transactional
	public IdmConceptRoleRequestDto saveInternal(IdmConceptRoleRequestDto dto) {
		IdmConceptRoleRequestDto savedDto = super.saveInternal(dto);
		if (dto != null && dto.getRole() != null) {
			// TODO: concept role request hasn't events, after implement events for the dto, please remove this.
			boolean isNew = false;
			if (isNew(dto)) {
				isNew = true;
				dto = valueGeneratorManager.generate(dto);
			}

			IdmRoleDto roleDto = roleService.get(dto.getRole());
			if (roleDto == null) {
				throw new ResultCodeException(CoreResultCode.NOT_FOUND, ImmutableMap.of("entity", dto.getRole()));
			}
			
			List<InvalidFormAttributeDto> validationErrors = validateFormAttributes(dto);
			
			if (validationErrors != null && !validationErrors.isEmpty()) {
				throw new InvalidFormException(validationErrors);
			}

			List<IdmFormValueDto> attributeValues = dto.getEavs().size() == 1 && dto.getEavs().get(0) != null
					? dto.getEavs().get(0).getValues()
					: null;
			
			// If concept is new, then we have to clear id of EAV values (new one have to be generated for this case).
			if (isNew && attributeValues != null) {
				attributeValues.forEach(value -> {
					DtoUtils.clearAuditFields(value);
					value.setId(null);
				});
			}
					
			// Load sub definition by role
			IdmFormDefinitionDto formDefinitionDto = roleService.getFormAttributeSubdefinition(roleDto);
			if (formDefinitionDto != null) {
				// Save form values for sub-definition. Validation is skipped. Was made before in this method, because now can be id of values null.
				List<IdmFormValueDto> savedValues = formService.saveFormInstance(savedDto, formDefinitionDto, attributeValues, false).getValues();
				IdmFormInstanceDto formInstance = new IdmFormInstanceDto();
				formInstance.setValues(savedValues);
				savedDto.getEavs().clear();
				savedDto.getEavs().add(formInstance);
			}
		}

		return savedDto;
	}

	@Override
	@Transactional
	public void deleteInternal(IdmConceptRoleRequestDto dto) {
		formService.deleteValues(dto);
		this.cancelWF(dto);
		super.deleteInternal(dto);
	}

	@Override
	public IdmFormInstanceDto getRoleAttributeValues(IdmConceptRoleRequestDto dto, boolean checkChanges) {
		Assert.notNull(dto, "DTO is required.");
		UUID roleId = dto.getRole();
		if (roleId != null) {
			IdmRoleDto role = DtoUtils.getEmbedded(dto, IdmConceptRoleRequest_.role, IdmRoleDto.class, null);
			if(role == null) {
				role = roleService.get(roleId);
			}
			// Has role filled attribute definition?
			UUID formDefintion = role.getIdentityRoleAttributeDefinition();
			if (formDefintion != null) {
				IdmFormDefinitionDto formDefinitionDto = roleService.getFormAttributeSubdefinition(role);
				IdmFormInstanceDto conceptFormInstance = null;
				List<IdmFormInstanceDto> eavs = dto.getEavs();
				// Get form instance from given concept first
				if (eavs != null && eavs.size() == 1) {
					conceptFormInstance = eavs.get(0);
					if(conceptFormInstance.getFormDefinition() == null) {
						conceptFormInstance.setFormDefinition(formDefinitionDto);
					}
				} else {
					conceptFormInstance = formService.getFormInstance(dto, formDefinitionDto);
				}
				
				if (!checkChanges) { // Return only EAV values, without compare changes
					return conceptFormInstance;
				}
				
				// If exists identity role, then we try to evaluate changes against EAVs in the
				// current identity role.
				ConceptRoleRequestOperation operation = dto.getOperation();
				if (dto.getIdentityRole() != null && ConceptRoleRequestOperation.UPDATE == operation) {
					IdmIdentityRoleDto identityRoleDto = DtoUtils.getEmbedded(dto, IdmConceptRoleRequest_.identityRole,
							IdmIdentityRoleDto.class, null);
					if(identityRoleDto == null) {
						identityRoleDto = identityRoleThinService.get(dto.getIdentityRole());
					}
					IdmFormInstanceDto formInstance = formService.getFormInstance(
							new IdmIdentityRoleDto(identityRoleDto.getId()), 
							formDefinitionDto);
					if (formInstance != null && conceptFormInstance != null) {
						IdmFormInstanceDto conceptFormInstanceFinal = conceptFormInstance;
						List<IdmFormValueDto> conceptValues = conceptFormInstanceFinal.getValues();
						List<IdmFormValueDto> values = formInstance.getValues();

						conceptValues.forEach(conceptFormValue -> {
							IdmFormValueDto formValue = values.stream() //
									.filter(value -> value.getFormAttribute()
											.equals(conceptFormValue.getFormAttribute())
											&& value.getSeq() == conceptFormValue.getSeq()) //
									.findFirst() //
									.orElse(null); //
							// Compile changes
							Serializable value = formValue != null ? formValue.getValue() : null;
							Serializable conceptValue = conceptFormValue.getValue();

							if (!Objects.equals(conceptValue, value)) {
								conceptFormValue.setChanged(true);
								conceptFormValue.setOriginalValue(formValue);
							}
						});
						
						// Find deleted values in a concepts. If will be found, then new instance of
						// IdmFormValue will be created with the value from original identity-role
						// attribute.
						values.forEach(formValue -> {
							IdmFormValueDto missingConceptFormValue = conceptValues.stream() //
									.filter(conceptFormValue -> conceptFormValue.getFormAttribute()
											.equals(formValue.getFormAttribute())
											&& conceptFormValue.getSeq() == formValue.getSeq()) //
									.findFirst() //
									.orElse(null); //
							
							if(missingConceptFormValue == null) {
								IdmFormAttributeDto formAttributeDto = DtoUtils.getEmbedded(formValue, IdmFormValue_.formAttribute.getName());
								
								missingConceptFormValue = new IdmFormValueDto(formAttributeDto);
								missingConceptFormValue.setChanged(true);
								missingConceptFormValue.setOriginalValue(formValue);
								List<IdmFormValueDto> newConceptValues = new ArrayList<IdmFormValueDto>(conceptValues);
								newConceptValues.add(missingConceptFormValue);
								conceptFormInstanceFinal.setValues(newConceptValues);
							}
						});
					}
				}
				return conceptFormInstance;
			}
		}
		return null;
	}

	@Override
	protected List<Predicate> toPredicates(Root<IdmConceptRoleRequest> root, CriteriaQuery<?> query,
			CriteriaBuilder builder, IdmConceptRoleRequestFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
		//
		if (filter.getRoleRequestId() != null) {
			predicates.add(builder.equal(root.get(IdmConceptRoleRequest_.roleRequest).get(IdmRoleRequest_.id),
					filter.getRoleRequestId()));
		}
		if (filter.getIdentityRoleId() != null) {
			predicates.add(builder.equal(root.get(IdmConceptRoleRequest_.identityRole).get(IdmIdentityRole_.id),
					filter.getIdentityRoleId()));
		}
		if (filter.getRoleId() != null) {
			predicates.add(builder.equal(root.get(IdmConceptRoleRequest_.role).get(IdmRole_.id), filter.getRoleId()));
		}
		if (filter.getIdentityContractId() != null) {
			predicates.add(builder.equal(root.get(IdmConceptRoleRequest_.identityContract).get(IdmIdentityContract_.id),
					filter.getIdentityContractId()));
		}
		if (filter.getAutomaticRole() != null) {
			predicates.add(builder.equal(root.get(IdmConceptRoleRequest_.automaticRole).get(IdmAutomaticRole_.id),
					filter.getAutomaticRole()));
		}
		if (filter.getOperation() != null) {
			predicates.add(builder.equal(root.get(IdmConceptRoleRequest_.operation), filter.getOperation()));
		}
		if (filter.getState() != null) {
			predicates.add(builder.equal(root.get(IdmConceptRoleRequest_.state), filter.getState()));
		}
		
		Set<UUID> ids = filter.getIdentityRoleIds();
		if (ids != null && !ids.isEmpty()) {
			predicates.add(root.get(IdmConceptRoleRequest_.identityRole).get(IdmIdentityRole_.id).in(ids));
		}
		
		if (filter.getRoleEnvironment() != null) {
			predicates.add(builder.equal(root.get(IdmConceptRoleRequest_.role).get(IdmRole_.environment), filter.getRoleEnvironment()));
		}
		
		List<String> roleEnvironments = filter.getRoleEnvironments();
		if (CollectionUtils.isNotEmpty(roleEnvironments)) {
			predicates.add(root.get(IdmConceptRoleRequest_.role).get(IdmRole_.environment).in(roleEnvironments));
		}
		
		if (filter.isIdentityRoleIsNull()) {
			predicates.add(builder.isNull(root.get(IdmConceptRoleRequest_.identityRole)));
		}
		//
		return predicates;
	}

	@Override
	@Transactional(readOnly = true)
	public List<IdmConceptRoleRequestDto> findAllByRoleRequest(UUID roleRequestId) {
		Assert.notNull(roleRequestId, "Role request identifier is required.");
		// find concepts by filter (fetch mode is applied)
		IdmConceptRoleRequestFilter filter = new IdmConceptRoleRequestFilter();
		filter.setRoleRequestId(roleRequestId);
		// 
		return find(filter, null).getContent();
	}
	
	@Override
	public List<InvalidFormAttributeDto> validateFormAttributes(IdmConceptRoleRequestDto concept) {
		if(concept == null || ConceptRoleRequestOperation.REMOVE == concept.getOperation()) {
			return null;
		}
		IdmFormInstanceDto formInstanceDto = this.getRoleAttributeValues(concept, false);
		if (formInstanceDto != null) {
			
			UUID identityRoleId = null;
			IdmIdentityRoleDto identityRoleDto = DtoUtils.getEmbedded(concept, IdmConceptRoleRequest_.identityRole,
					IdmIdentityRoleDto.class, null);
			if(identityRoleDto == null) {
				identityRoleId = concept.getIdentityRole();
			} else {
				identityRoleId = identityRoleDto.getId();
			}
			
			if (identityRoleId != null && ConceptRoleRequestOperation.UPDATE == concept.getOperation()) {

				// Cache for save original ID of concepts.
				// Id will be replaced by identity-role id and have to be returned after 
				// validation back, because formInstance is not immutable.
				Map<UUID, UUID> identityRoleConceptValueMap = new HashMap<>();

				// Find identity role value for concept value and change ID of value (because validation have to be made via identityRole).	
				UUID finalIdentityRoleId = identityRoleId;
				formInstanceDto.getValues().forEach(value -> {
					IdmFormAttributeDto formAttributeDto = new IdmFormAttributeDto();
					formAttributeDto.setId(value.getFormAttribute());
					formAttributeDto.setFormDefinition(formInstanceDto.getFormDefinition().getId());

					IdmFormValueDto identityRoleValueDto = formService.getValues(new IdmIdentityRoleDto(finalIdentityRoleId), formAttributeDto)
							.stream()
							.filter(identityRoleValue -> identityRoleValue.getSeq() == value.getSeq())
							.findFirst()
							.orElse(null);
					
					// Replace concept IDs by identity-role IDs.
					if (identityRoleValueDto != null) {
						identityRoleConceptValueMap.put(identityRoleValueDto.getId(), value.getId());
						value.setId(identityRoleValueDto.getId());
					}
				});
				List<InvalidFormAttributeDto> validationErrors = formService.validate(formInstanceDto);

				// Set IDs of concept back to values (formInstance is not immutable).
				formInstanceDto.getValues().forEach(value -> {
					if (identityRoleConceptValueMap.containsKey(value.getId())) {
						value.setId(identityRoleConceptValueMap.get(value.getId()));
					}
				});
				return validationErrors;
			}
			return formService.validate(formInstanceDto);
		}
		return null;
	}

	@Override
	public void addToLog(Loggable logItem, String text) {
		StringBuilder sb = new StringBuilder();
		sb.append(ZonedDateTime.now());
		sb.append(": ");
		sb.append(text);
		text = sb.toString();
		logItem.addToLog(text);
		LOG.info(text);
	}

	private void cancelWF(IdmConceptRoleRequestDto dto) {
		if (!Strings.isNullOrEmpty(dto.getWfProcessId())) {
			WorkflowFilterDto filter = new WorkflowFilterDto();
			filter.setProcessInstanceId(dto.getWfProcessId());

			List<WorkflowProcessInstanceDto> resources = workflowProcessInstanceService
					.find(filter, null).getContent();
			if (resources.isEmpty()) {
				// Process with this ID not exist ... maybe was ended
				this.addToLog(dto, MessageFormat.format(
						"Workflow process with ID [{0}] was not deleted, because was not found. Maybe was ended before.",
						dto.getWfProcessId()));
			} else {
				// Before delete/cancel process we try to finish process as disapprove. Cancel
				// process does not trigger the parent process. That means without correct
				// ending of process, parent process will be frozen!

				// Find active task for this process.
				WorkflowFilterDto taskFilter = new WorkflowFilterDto();
				taskFilter.setProcessInstanceId(dto.getWfProcessId());
				List<WorkflowTaskInstanceDto> tasks = workflowTaskInstanceService.find(taskFilter, null).getContent();
				if (tasks.size() == 1) {
					WorkflowTaskInstanceDto task = tasks.get(0);
					DecisionFormTypeDto disapprove = task.getDecisions() //
							.stream() //
							.filter(decision -> WorkflowTaskInstanceService.WORKFLOW_DECISION_DISAPPROVE
									.equals(decision.getId()))
							.findFirst() //
							.orElse(null);
					if (disapprove != null) {
						// Active task exists and has decision for 'disapprove'. Complete task (process)
						// with this decision.
						workflowTaskInstanceService.completeTask(task.getId(), disapprove.getId(), null, null, null);
						this.addToLog(dto, MessageFormat.format(
								"Workflow process with ID [{0}] was disapproved, because this concept is deleted/canceled",
								dto.getWfProcessId()));
						return;
					}
				}
				// We wasn't able to disapprove this process, we cancel him now.
				workflowProcessInstanceService.delete(dto.getWfProcessId(),
						"Role concept use this WF, was deleted. This WF was deleted too.");
				this.addToLog(dto,
						MessageFormat.format(
								"Workflow process with ID [{0}] was deleted, because this concept is deleted/canceled",
								dto.getWfProcessId()));
			}
		}
	}
}
