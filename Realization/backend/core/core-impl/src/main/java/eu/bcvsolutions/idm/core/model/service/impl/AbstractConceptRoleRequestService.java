package eu.bcvsolutions.idm.core.model.service.impl;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.Loggable;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.dto.AbstractConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.DataFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmBaseConceptRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.exception.ForbiddenEntityException;
import eu.bcvsolutions.idm.core.api.exception.InvalidFormException;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.repository.AbstractEntityRepository;
import eu.bcvsolutions.idm.core.api.repository.filter.FilterManager;
import eu.bcvsolutions.idm.core.api.service.AbstractReadWriteDtoService;
import eu.bcvsolutions.idm.core.api.service.IdmGeneralConceptRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.service.ValueGeneratorManager;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.InvalidFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.entity.AbstractFormValue_;
import eu.bcvsolutions.idm.core.model.entity.AbstractConceptRoleRequest;
import eu.bcvsolutions.idm.core.model.entity.AbstractConceptRoleRequest_;
import eu.bcvsolutions.idm.core.model.entity.IdmAutomaticRole;
import eu.bcvsolutions.idm.core.model.entity.IdmAutomaticRoleAttribute;
import eu.bcvsolutions.idm.core.model.entity.IdmRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRole_;
import eu.bcvsolutions.idm.core.model.repository.IdmAutomaticRoleRepository;
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
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.modelmapper.PropertyMap;
import org.modelmapper.TypeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import java.io.Serializable;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @author Peter Štrunc <github.com/peter-strunc>
 */
public abstract class AbstractConceptRoleRequestService<
        D extends AbstractConceptRoleRequestDto,
        E extends AbstractConceptRoleRequest,
        F extends IdmBaseConceptRoleRequestFilter> extends
        AbstractReadWriteDtoService<D, E, F> implements IdmGeneralConceptRoleRequestService<D, F> {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory
            .getLogger(AbstractConceptRoleRequestService.class);

    @Autowired
    private WorkflowTaskInstanceService workflowTaskInstanceService;

    private final WorkflowProcessInstanceService workflowProcessInstanceService;

    @Autowired
    private WorkflowHistoricProcessInstanceService historicProcessService;

    @Autowired
    private IdmRoleService roleService;

    @Autowired
    private FormService formService;

    @Autowired
    private ValueGeneratorManager valueGeneratorManager;

    @Autowired
    private FilterManager filterManager;

    private final LookupService lookupService;
    private final IdmAutomaticRoleRepository automaticRoleRepository;

    protected AbstractConceptRoleRequestService(AbstractEntityRepository<E> repository, WorkflowProcessInstanceService workflowProcessInstanceService, LookupService lookupService, IdmAutomaticRoleRepository automaticRoleRepository) {
        super(repository);
        this.workflowProcessInstanceService = workflowProcessInstanceService;
        this.lookupService = lookupService;
        this.automaticRoleRepository = automaticRoleRepository;
    }

    @Override
    protected List<Predicate> toPredicates(Root<E> root, CriteriaQuery<?> query,
                                           CriteriaBuilder builder, F filter) {
        List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
        //
        if (filter.getRoleRequestId() != null) {
            predicates.add(builder.equal(root.get(AbstractConceptRoleRequest_.roleRequest).get(AbstractEntity_.id),
                    filter.getRoleRequestId()));
        }
        if (filter.getRoleId() != null) {
            predicates.add(builder.equal(root.get(AbstractConceptRoleRequest_.role).get(AbstractEntity_.id), filter.getRoleId()));
        }
        // by role text
        String roleText = filter.getRoleText();
        if (StringUtils.isNotEmpty(roleText)) {
            IdmRoleFilter subFilter = new IdmRoleFilter();
            subFilter.setText(roleText);
            Subquery<IdmRole> subquery = query.subquery(IdmRole.class);
            Root<IdmRole> subRoot = subquery.from(IdmRole.class);
            subquery.select(subRoot);

            Predicate rolePredicate = filterManager
                    .getBuilder(IdmRole.class, DataFilter.PARAMETER_TEXT)
                    .getPredicate(subRoot, subquery, builder, subFilter);

            subquery.where(
                    builder.and(
                            builder.equal(root.get(AbstractConceptRoleRequest_.role), subRoot), // correlation attr
                            rolePredicate
                    )
            );
            //
            predicates.add(builder.exists(subquery));
        }
        if (filter.getAutomaticRole() != null) {
            predicates.add(builder.equal(root.get(AbstractConceptRoleRequest_.automaticRole).get(AbstractEntity_.id),
                    filter.getAutomaticRole()));
        }
        if (filter.getOperation() != null) {
            predicates.add(builder.equal(root.get(AbstractConceptRoleRequest_.operation), filter.getOperation()));
        }
        if (filter.getState() != null) {
            predicates.add(builder.equal(root.get(AbstractConceptRoleRequest_.state), filter.getState()));
        }

        if (filter.getRoleEnvironment() != null) {
            predicates.add(builder.equal(root.get(AbstractConceptRoleRequest_.role).get(IdmRole_.environment), filter.getRoleEnvironment()));
        }

        List<String> roleEnvironments = filter.getRoleEnvironments();
        if (CollectionUtils.isNotEmpty(roleEnvironments)) {
            predicates.add(root.get(AbstractConceptRoleRequest_.role).get(IdmRole_.environment).in(roleEnvironments));
        }

        //
        return predicates;
    }

    @Override
    public List<InvalidFormAttributeDto> validateFormAttributes(D concept) {
        if (concept == null
                || ConceptRoleRequestOperation.REMOVE == concept.getOperation()
                || concept.getState() == null
                || concept.getState().isTerminatedState()) {
            return Collections.emptyList();
        }
        IdmFormInstanceDto formInstanceDto = this.getRoleAttributeValues(concept, false);
        if (formInstanceDto != null) {

            UUID identityRoleId = getIdentityRoleId(concept);

            if (identityRoleId != null && ConceptRoleRequestOperation.UPDATE == concept.getOperation()) {

                // Cache for save original ID of concepts.
                // Id will be replaced by identity-role id and have to be returned after
                // validation back, because formInstance is not immutable.
                Map<UUID, UUID> identityRoleConceptValueMap = new HashMap<>();

                // Find identity role value for concept value and change ID of value (because validation have to be made via identityRole).
                formInstanceDto.getValues().forEach(value -> {
                    IdmFormAttributeDto formAttributeDto = new IdmFormAttributeDto();
                    formAttributeDto.setId(value.getFormAttribute());
                    formAttributeDto.setFormDefinition(formInstanceDto.getFormDefinition().getId());

                    IdmFormValueDto identityRoleValueDto = getCurrentFormValue(identityRoleId, value, formAttributeDto);

                    // Replace concept IDs by identity-role IDs.
                    if (identityRoleValueDto != null) {
                        identityRoleConceptValueMap.put(identityRoleValueDto.getId(), value.getId());
                        value.setId(identityRoleValueDto.getId());
                    }
                });
                List<InvalidFormAttributeDto> validationErrors = formService.validate(formInstanceDto, false);

                // Set IDs of concept back to values (formInstance is not immutable).
                formInstanceDto.getValues().forEach(value -> {
                    if (identityRoleConceptValueMap.containsKey(value.getId())) {
                        value.setId(identityRoleConceptValueMap.get(value.getId()));
                    }
                });
                return validationErrors;
            }
            return formService.validate(formInstanceDto, false);
        }
        return Collections.emptyList();
    }

    protected abstract IdmFormValueDto getCurrentFormValue(UUID identityRoleId, IdmFormValueDto value, IdmFormAttributeDto formAttributeDto);


    @Override
    @Transactional
    public D saveInternal(D dto) {
        D savedDto = super.saveInternal(dto);
        if (dto != null && dto.getRole() != null) {
            // TODO: concept role request hasn't events, after implement events for the dto, please remove this.
            boolean isNew = false;
            if (isNew(dto)) {
                isNew = true;
                dto = valueGeneratorManager.generate(dto);
            }

            IdmRoleDto roleDto = roleService.get(dto.getRole());
            if (roleDto == null) {
                throw new ResultCodeException(CoreResultCode.NOT_FOUND, Map.of("entity", dto.getRole()));
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
    protected D toDto(E entity, D dto) {
        dto = super.toDto(entity, dto);
        if (dto == null) {
            return null;
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
    public E toEntity(D dto, E entity) {
        if (dto == null) {
            return null;
        }

        if (dto.getId() == null) {
            dto.setState(RoleRequestState.CONCEPT);
        }
        //
        // field automatic role exists in entity but not in dto
        TypeMap<D, E> typeMap = modelMapper.getTypeMap(getDtoClass(),
                getEntityClass());
        if (typeMap == null) {
            modelMapper.createTypeMap(getDtoClass(), getEntityClass());
            typeMap = modelMapper.getTypeMap(getDtoClass(), getEntityClass());
            typeMap.addMappings(new PropertyMap<D, E>() {

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
    public AuthorizableType getAuthorizableType() {
        // secured internally by role requests
        return null;
    }

    @Override
    public E checkAccess(E entity, BasePermission... permission) {
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
            // Ok process was not returned, but we need to check historic process (on involved user) too.
            WorkflowHistoricProcessInstanceDto historicProcess = historicProcessService.get(processId);
            if (historicProcess != null) {
                return entity;
            }
        }

        throw new ForbiddenEntityException(entity, permission);
    }

    @Override
    @Transactional
    public D cancel(D dto) {
        cancelWF(dto);
        dto.setState(RoleRequestState.CANCELED);
        return this.save(dto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<D> findAllByRoleRequest(UUID roleRequestId) {
        Assert.notNull(roleRequestId, "Role request identifier is required.");
        // find concepts by filter (fetch mode is applied)
        F filter = getFilter();
        filter.setRoleRequestId(roleRequestId);
        //
        return find(filter, null).getContent();
    }

    @Override
    @Transactional
    public void deleteInternal(D dto) {
        formService.deleteValues(dto);
        this.cancelWF(dto);
        super.deleteInternal(dto);
    }

    @Override
    public void addToLog(Loggable logItem, String text) {
        text = ZonedDateTime.now() + ": " + text;
        logItem.addToLog(text);
        LOG.info(text);
    }

    @Override
    public IdmFormInstanceDto getRoleAttributeValues(D dto, boolean checkChanges) {
        Assert.notNull(dto, "DTO is required.");
        UUID roleId = dto.getRole();
        if (roleId != null) {
            IdmRoleDto role = DtoUtils.getEmbedded(dto, AbstractConceptRoleRequest_.role, IdmRoleDto.class, null);
            if (role == null) {
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
                    if (conceptFormInstance.getFormDefinition() == null) {
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
                if (shouldProcessChanges(dto, operation)) {
                    IdmFormInstanceDto formInstance = getFormInstance(dto, formDefinitionDto);
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

                            if (missingConceptFormValue == null) {
                                IdmFormAttributeDto formAttributeDto = DtoUtils.getEmbedded(formValue, AbstractFormValue_.formAttribute.getName());

                                missingConceptFormValue = new IdmFormValueDto(formAttributeDto);
                                missingConceptFormValue.setChanged(true);
                                missingConceptFormValue.setOriginalValue(formValue);
                                List<IdmFormValueDto> newConceptValues = new ArrayList<>(conceptValues);
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

    private void cancelWF(D dto) {
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

    protected abstract IdmFormInstanceDto getFormInstance(D dto, IdmFormDefinitionDto definition);

    protected abstract boolean shouldProcessChanges(D dto, ConceptRoleRequestOperation operation);

    protected abstract F getFilter();

    protected abstract UUID getIdentityRoleId(D concept);
}
