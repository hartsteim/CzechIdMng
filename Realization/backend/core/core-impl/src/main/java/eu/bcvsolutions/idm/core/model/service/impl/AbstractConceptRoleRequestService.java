package eu.bcvsolutions.idm.core.model.service.impl;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.Loggable;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.dto.AbstractConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.AbstractRoleAssignmentDto;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAccountDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRequestIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCompositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.BaseRoleAssignmentFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.DataFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmBaseConceptRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.event.CoreEvent;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.exception.ForbiddenEntityException;
import eu.bcvsolutions.idm.core.api.exception.InvalidFormException;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.repository.AbstractEntityRepository;
import eu.bcvsolutions.idm.core.api.repository.filter.FilterManager;
import eu.bcvsolutions.idm.core.api.service.AbstractReadWriteDtoService;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.IdmGeneralConceptRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleAssignmentService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCompositionService;
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
import eu.bcvsolutions.idm.core.model.entity.AbstractRoleAssignment_;
import eu.bcvsolutions.idm.core.model.entity.IdmAutomaticRole;
import eu.bcvsolutions.idm.core.model.entity.IdmAutomaticRoleAttribute;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRole_;
import eu.bcvsolutions.idm.core.model.event.AbstractRoleAssignmentEvent;
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
import java.util.stream.Collectors;

import static eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation.ADD;
import static eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation.UPDATE;

/**
 * @author Peter Štrunc <github.com/peter-strunc>
 */
public abstract class AbstractConceptRoleRequestService<A extends AbstractRoleAssignmentDto,
        D extends AbstractConceptRoleRequestDto, E extends AbstractConceptRoleRequest,
        F extends IdmBaseConceptRoleRequestFilter> extends AbstractReadWriteDtoService<D, E, F> implements IdmGeneralConceptRoleRequestService<A, D, F> {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(AbstractConceptRoleRequestService.class);

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

    private final IdmRoleCompositionService roleCompositionService;

    private final LookupService lookupService;
    private final IdmAutomaticRoleRepository automaticRoleRepository;

    private final IdmRoleAssignmentService<A, ? extends BaseRoleAssignmentFilter> roleAssignmentService;

    protected AbstractConceptRoleRequestService(AbstractEntityRepository<E> repository,
            WorkflowProcessInstanceService workflowProcessInstanceService,
            IdmRoleCompositionService roleCompositionService, LookupService lookupService,
            IdmAutomaticRoleRepository automaticRoleRepository,
            IdmRoleAssignmentService<A, ? extends BaseRoleAssignmentFilter> roleAssignmentService) {
        super(repository);
        this.workflowProcessInstanceService = workflowProcessInstanceService;
        this.roleCompositionService = roleCompositionService;
        this.lookupService = lookupService;
        this.automaticRoleRepository = automaticRoleRepository;
        this.roleAssignmentService = roleAssignmentService;
    }

    @Override
    protected List<Predicate> toPredicates(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder builder, F filter) {
        List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
        //
        if (filter.getOwnerType() != null && !filter.getOwnerType().isAssignableFrom(getOwnerType())) {
            // If supported owner type by this service does not match owner type specified in filter, we want to return
            // empty result set.
            predicates.add(builder.disjunction());
            return predicates;
        }
        if (filter.getId() != null) {
            predicates.add(builder.equal(root.get(AbstractEntity_.id), filter.getId()));
        }
        //
        if (filter.getRoleRequestId() != null) {
            predicates.add(builder.equal(root.get(AbstractConceptRoleRequest_.roleRequest).get(AbstractEntity_.id),
                    filter.getRoleRequestId()));
        }
        if (filter.getRoleId() != null) {
            predicates.add(builder.equal(root.get(AbstractConceptRoleRequest_.role).get(AbstractEntity_.id),
                    filter.getRoleId()));
        }
        // by role text
        String roleText = filter.getRoleText();
        if (StringUtils.isNotEmpty(roleText)) {
            IdmRoleFilter subFilter = new IdmRoleFilter();
            subFilter.setText(roleText);
            Subquery<IdmRole> subquery = query.subquery(IdmRole.class);
            Root<IdmRole> subRoot = subquery.from(IdmRole.class);
            subquery.select(subRoot);

            Predicate rolePredicate =
                    filterManager.getBuilder(IdmRole.class, DataFilter.PARAMETER_TEXT).getPredicate(subRoot, subquery
                            , builder, subFilter);

            subquery.where(builder.and(builder.equal(root.get(AbstractConceptRoleRequest_.role), subRoot), //
                    // correlation attr
                    rolePredicate));
            //
            predicates.add(builder.exists(subquery));
        }
        if (filter.getAutomaticRoleId() != null) {
            predicates.add(builder.equal(root.get(AbstractConceptRoleRequest_.automaticRole).get(AbstractEntity_.id),
                    filter.getAutomaticRoleId()));
        }
        if (filter.getAutomaticRole() != null) {
            predicates.add(Boolean.TRUE.equals(filter.getAutomaticRole()) ?
                        builder.isNotNull(root.get(AbstractConceptRoleRequest_.automaticRole)) :
                            builder.isNull(root.get(AbstractConceptRoleRequest_.automaticRole))
                    );
        }

        if (filter.getOperation() != null) {
            predicates.add(builder.equal(root.get(AbstractConceptRoleRequest_.operation), filter.getOperation()));
        }
        if (filter.getState() != null) {
            predicates.add(builder.equal(root.get(AbstractConceptRoleRequest_.state), filter.getState()));
        }

        if (filter.getRoleEnvironment() != null) {
            predicates.add(builder.equal(root.get(AbstractConceptRoleRequest_.role).get(IdmRole_.environment),
                    filter.getRoleEnvironment()));
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
        if (concept == null || ConceptRoleRequestOperation.REMOVE == concept.getOperation() || concept.getState() == null || concept.getState().isTerminatedState()) {
            return Collections.emptyList();
        }
        IdmFormInstanceDto formInstanceDto = this.getRoleAttributeValues(concept, false);
        if (formInstanceDto != null) {

            UUID identityRoleId = getIdentityRoleId(concept);

            if (identityRoleId != null && UPDATE == concept.getOperation()) {

                // Cache for save original ID of concepts.
                // Id will be replaced by identity-role id and have to be returned after
                // validation back, because formInstance is not immutable.
                Map<UUID, UUID> identityRoleConceptValueMap = new HashMap<>();

                // Find identity role value for concept value and change ID of value (because validation have to be
                // made via identityRole).
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

    protected abstract IdmFormValueDto getCurrentFormValue(UUID identityRoleId, IdmFormValueDto value,
            IdmFormAttributeDto formAttributeDto);


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

            List<IdmFormValueDto> attributeValues = dto.getEavs().size() == 1 && dto.getEavs().get(0) != null ?
                    dto.getEavs().get(0).getValues() : null;

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
                // Save form values for sub-definition. Validation is skipped. Was made before in this method,
                // because now can be id of values null.
                List<IdmFormValueDto> savedValues = formService.saveFormInstance(savedDto, formDefinitionDto,
                        attributeValues, false).getValues();
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
        TypeMap<D, E> typeMap = modelMapper.getTypeMap(getDtoClass(), getEntityClass());
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
        if (getAuthorizationManager().evaluate(entity.getRoleRequest(),
                permissionsForRequest.toArray(new BasePermission[0]))) {
            return entity;
        }

        // We have rights on the concept, when we have rights on workflow process using in the concept.
        // Beware, concet can use different WF process than whole request. So we need to check directly process on
        // concept!
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
                    if (conceptFormInstance != null && conceptFormInstance.getFormDefinition() == null) {
                        conceptFormInstance.setFormDefinition(formDefinitionDto);
                    }
                } else if (dto.getId() != null){
                    conceptFormInstance = formService.getFormInstance(dto, formDefinitionDto);
                } else {
                    conceptFormInstance = new IdmFormInstanceDto();
                    conceptFormInstance.setFormDefinition(formDefinitionDto);
                    //conceptFormInstance.setOwnerType();
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
                                    .filter(value -> value.getFormAttribute().equals(conceptFormValue.getFormAttribute()) && value.getSeq() == conceptFormValue.getSeq()) //
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
                                    .filter(conceptFormValue -> conceptFormValue.getFormAttribute().equals(formValue.getFormAttribute()) && conceptFormValue.getSeq() == formValue.getSeq()) //
                                    .findFirst() //
                                    .orElse(null); //

                            if (missingConceptFormValue == null) {
                                IdmFormAttributeDto formAttributeDto = DtoUtils.getEmbedded(formValue,
                                        AbstractFormValue_.formAttribute.getName());

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

            List<WorkflowProcessInstanceDto> resources = workflowProcessInstanceService.find(filter, null).getContent();
            if (resources.isEmpty()) {
                // Process with this ID not exist ... maybe was ended
                this.addToLog(dto, MessageFormat.format("Workflow process with ID [{0}] was not deleted, because was "
                        + "not found. Maybe was ended before.", dto.getWfProcessId()));
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
                            .filter(decision -> WorkflowTaskInstanceService.WORKFLOW_DECISION_DISAPPROVE.equals(decision.getId())).findFirst() //
                            .orElse(null);
                    if (disapprove != null) {
                        // Active task exists and has decision for 'disapprove'. Complete task (process)
                        // with this decision.
                        workflowTaskInstanceService.completeTask(task.getId(), disapprove.getId(), null, null, null);
                        this.addToLog(dto, MessageFormat.format("Workflow process with ID [{0}] was disapproved, " +
                                "because this concept is deleted/canceled", dto.getWfProcessId()));
                        return;
                    }
                }
                // We wasn't able to disapprove this process, we cancel him now.
                workflowProcessInstanceService.delete(dto.getWfProcessId(),
                        "Role concept use this WF, was deleted. " + "This WF was deleted too.");
                this.addToLog(dto,
                        MessageFormat.format("Workflow process with ID [{0}] was deleted, because this " + "concept " + "is deleted/canceled", dto.getWfProcessId()));
            }
        }
    }

    @Override
    public A createAssignmentFromConcept(D concept) {
        A temp = createAssignmentFromConceptInternal(concept);
        temp.setRole(concept.getRole());
        temp.setValidFrom(concept.getValidFrom());
        temp.setValidTill(concept.getValidTill());
        temp.setRoleSystem(concept.getRoleSystem());

        temp.setEavs(concept.getEavs());
        // Other way how to get eavs. But this way is to slow.
        // tempIdentityRole.setEavs(Lists.newArrayList(conceptRoleRequestService.getRoleAttributeValues(concept,
        // false)));

        // Created is set to now (with found duplicity, this will be marked as duplicated)
        temp.setCreated(ZonedDateTime.now());
        return temp;
    }

    /**
     * Check and cancel invalid concept => concept is canceled, when required entities as role, contract
     * or assigned role is removed in the mean time in other session.
     *
     * @param concept
     * @param request
     * @return true, if concept is canceled
     */
    @Override
    @Transactional
    public boolean cancelInvalidConcept(List<A> automaticRoles, D concept, IdmRoleRequestDto request) {
        String message = null;
        final String internalResult = cancelInvalidConceptInternal(automaticRoles, concept, request);
        //
        if (internalResult != null) {
            message = internalResult;
        } else if (concept.getRoleAssignmentUuid() == null && ADD != concept.getOperation()) { // identity role is
            // not given for ADD
            message = MessageFormat.format("Request change in concept [{0}], was not executed, because assigned role "
                    + "was deleted before (not from this role request)!", concept.getId());
        } else if (concept.getRole() == null && ConceptRoleRequestOperation.REMOVE != concept.getOperation()) { //
            // role is optional in DELETE
            message = MessageFormat.format("Request change in concept [{0}], was not executed, because requested " +
                    "role" + " was deleted (not from this role request)!", concept.getId());
        } else if (concept.getRoleComposition() != null) {
            // role is in composition, check if this same role is not already added by automatic parent role
            List<UUID> autoRolesIds =
                    request.getConceptRoles().stream().filter(idmConceptRoleRequestDto -> idmConceptRoleRequestDto.getState().equals(RoleRequestState.CANCELED)).map(AbstractConceptRoleRequestDto::getRole).collect(Collectors.toList());

            if (!autoRolesIds.isEmpty()) {
                List<UUID> allSuperiorRoles =
                        roleCompositionService.findAllSuperiorRoles(concept.getRole()).stream().map(IdmRoleCompositionDto::getSuperior).collect(Collectors.toList());
                if (!Collections.disjoint(autoRolesIds, allSuperiorRoles)) {
                    message = MessageFormat.format("Request change in concept [{0}], was not executed, because " +
                            "requested role was already assigned as a sub role of another role (not from this role " + "request)!", concept.getId());
                }
            }
        }

        if (message != null) {
            addToLog(request, message);
            addToLog(concept, message);
            // Cancel concept and WF
            cancel(concept);
            return true;
        }
        // concept is valid
        return false;
    }

    @Override
    public final A getRoleAssignmentDto(D concept, IdmRoleDto subRole) {
        A tempIdentityRoleSub = getRoleAssignmentDtoInternal(concept, subRole);
        tempIdentityRoleSub.setDirectRole(UUID.randomUUID());
        tempIdentityRoleSub.setRole(subRole.getId());
        tempIdentityRoleSub.setValidFrom(concept.getValidFrom());
        tempIdentityRoleSub.setValidTill(concept.getValidTill());
        tempIdentityRoleSub.setCreated(ZonedDateTime.now());
        // This automatically add default values. This is also expensive operation.
        tempIdentityRoleSub = valueGeneratorManager.generate(tempIdentityRoleSub);
        return tempIdentityRoleSub;
    }

    @Override
    public void createAssignedRole(List<D> allApprovedConcepts, D concept,
            EntityEvent<IdmRoleRequestDto> requestEvent) {
        A identityRole = convertConceptRoleToIdentityRole(allApprovedConcepts, concept, null);
        AbstractRoleAssignmentEvent<A> event = roleAssignmentService.getEventForAssignment(identityRole, AbstractRoleAssignmentEvent.RoleAssignmentEventType.CREATE,  IdmAccountDto.SKIP_PROPAGATE, EntityEventManager.EVENT_PROPERTY_SKIP_SUB_ROLES);


        // propagate event
        identityRole = this.roleAssignmentService.publish(event, requestEvent).getContent();

        // New assigned roles by business roles
        Set<AbstractRoleAssignmentDto> subNewIdentityRoles =
                event.getSetProperty(AbstractRoleAssignmentEvent.PROPERTY_ASSIGNED_NEW_ROLES,
                        AbstractRoleAssignmentDto.class);
        // Add to parent event
        Set<AbstractRoleAssignmentDto> addedIdentityRoles =
                requestEvent.getSetProperty(AbstractRoleAssignmentEvent.PROPERTY_ASSIGNED_NEW_ROLES,
                        AbstractRoleAssignmentDto.class);
        addedIdentityRoles.addAll(subNewIdentityRoles);
        addedIdentityRoles.add(identityRole);

        // Save created identity role id
        concept.setRoleAssignmentUuid(identityRole.getId());
        concept.setState(RoleRequestState.EXECUTED);
        IdmRoleDto roleDto = DtoUtils.getEmbedded(identityRole, AbstractRoleAssignment_.role);
        String message = MessageFormat.format("Role [{0}] was added to applicant. Requested in concept [{1}].",
                roleDto.getCode(), concept.getId());
        addToLog(concept, message);
        addToLog(requestEvent.getContent(), message);
        save(concept);
    }

    @Override
    public void updateAssignedRole(List<D> allApprovedConcepts, D concept, EntityEvent<IdmRoleRequestDto> requestEvent) {
        A identityRole = roleAssignmentService.get(concept.getRoleAssignmentUuid());
        identityRole = convertConceptRoleToIdentityRole(allApprovedConcepts, concept, identityRole);

        final AbstractRoleAssignmentEvent<A> event = roleAssignmentService.getEventForAssignment(identityRole, AbstractRoleAssignmentEvent.RoleAssignmentEventType.UPDATE,  IdmAccountDto.SKIP_PROPAGATE, EntityEventManager.EVENT_PROPERTY_SKIP_SUB_ROLES);

        // propagate event
        identityRole = roleAssignmentService.publish(event, requestEvent).getContent();

        // Updated assigned roles by business roles
        Set<AbstractRoleAssignmentDto> subUpdatedIdentityRoles = event.getSetProperty(AbstractRoleAssignmentEvent.PROPERTY_ASSIGNED_UPDATED_ROLES, AbstractRoleAssignmentDto.class);
        // Add to parent event
        Set<AbstractRoleAssignmentDto> updatedIdentityRoles = requestEvent.getSetProperty(AbstractRoleAssignmentEvent.PROPERTY_ASSIGNED_UPDATED_ROLES, AbstractRoleAssignmentDto.class);
        updatedIdentityRoles.addAll(subUpdatedIdentityRoles);
        updatedIdentityRoles.add(identityRole);

        // Save created identity role id
        concept.setRoleAssignmentUuid(identityRole.getId());
        concept.setState(RoleRequestState.EXECUTED);
        IdmRoleDto roleDto = DtoUtils.getEmbedded(identityRole, AbstractRoleAssignment_.role);
        String message = MessageFormat.format("Role [{0}] was changed. Requested in concept [{1}].", roleDto.getCode(), concept.getId());
        addToLog(concept, message);
        addToLog(requestEvent.getContent(), message);
        save(concept);
    }

    public void removeAssignedRole(D concept, EntityEvent<IdmRoleRequestDto> requestEvent) {
        Assert.notNull(concept.getRoleAssignmentUuid(), "Role assignment is mandatory for delete!");
        CoreEvent<A> event = removeRelatedRoleAssignment(concept, requestEvent);

        if (event != null) {

            // Add list of identity-accounts for delayed ACM to parent event
            Set<UUID> subIdentityAccountsForAcm = event
                    .getSetProperty(IdmAccountDto.IDENTITY_ACCOUNT_FOR_DELAYED_ACM, UUID.class);
            Set<UUID> identityAccountsForAcm = requestEvent
                    .getSetProperty(IdmAccountDto.IDENTITY_ACCOUNT_FOR_DELAYED_ACM, UUID.class);
            identityAccountsForAcm.addAll(subIdentityAccountsForAcm);
            // Add list of accounts for additional provisioning to parent event
            Set<UUID> subIdentityAccountsForProvisioning = event
                    .getSetProperty(IdmAccountDto.ACCOUNT_FOR_ADDITIONAL_PROVISIONING, UUID.class);
            Set<UUID> identityAccountsForProvisioning = requestEvent
                    .getSetProperty(IdmAccountDto.ACCOUNT_FOR_ADDITIONAL_PROVISIONING, UUID.class);
            identityAccountsForProvisioning.addAll(subIdentityAccountsForProvisioning);

            // Removed assigned roles by business roles
            Set<UUID> subRemovedIdentityRoles = event.getSetProperty(AbstractRoleAssignmentEvent.PROPERTY_ASSIGNED_REMOVED_ROLES, UUID.class);
            // Add to parent event
            Set<UUID> removedIdentityRoles = requestEvent
                    .getSetProperty(AbstractRoleAssignmentEvent.PROPERTY_ASSIGNED_REMOVED_ROLES, UUID.class);
            removedIdentityRoles.addAll(subRemovedIdentityRoles);
            removedIdentityRoles.add(concept.getRoleAssignmentUuid());
        }
    }

    @Override
    public CoreEvent<A> removeRelatedRoleAssignment(D concept, EntityEvent<IdmRoleRequestDto> requestEvent) {
        A identityRole = extractRoleAssignmentFromConcept(concept);

        if (identityRole != null) {
            concept.setState(RoleRequestState.EXECUTED);
            concept.setRoleAssignmentUuid(null); // we have to remove relation on
            // deleted identityRole
            String message = MessageFormat.format("IdentityRole [{0}] (reqested in concept [{1}]) was deleted (from " + "this role request).", identityRole.getId(), concept.getId());
            addToLog(concept, message);
            addToLog(requestEvent.getContent(), message);
            save(concept);

            final AbstractRoleAssignmentEvent<A> event = roleAssignmentService.getEventForAssignment(identityRole, AbstractRoleAssignmentEvent.RoleAssignmentEventType.DELETE, IdmAccountDto.SKIP_PROPAGATE);

            roleAssignmentService.publish(event, requestEvent);
            return event;
        }
        return null;
    }

    protected abstract A extractRoleAssignmentFromConcept(D concept);



    protected A convertConceptRoleToIdentityRole(List<D> allConcepts, D conceptRole, A roleAssignment) {

        if (roleAssignment == null) {
            roleAssignment = getRoleAssignmentFromConceptInternal(conceptRole);
        }

        if (conceptRole == null || roleAssignment == null) {
            return null;
        }

        IdmRoleDto roleDto = DtoUtils.getEmbedded(conceptRole, AbstractConceptRoleRequest_.role, IdmRoleDto.class);
        if (roleDto.getIdentityRoleAttributeDefinition() != null) {
            IdmFormDefinitionDto formDefinitionDto = roleService.getFormAttributeSubdefinition(roleDto);
            formService.mergeValues(formDefinitionDto, conceptRole, roleAssignment);
        }

        roleAssignment.setRole(conceptRole.getRole());

        roleAssignment.setValidFrom(conceptRole.getValidFrom());
        roleAssignment.setValidTill(conceptRole.getValidTill());
        roleAssignment.setOriginalCreator(conceptRole.getOriginalCreator());
        roleAssignment.setOriginalCreatorId(conceptRole.getOriginalCreatorId());
        roleAssignment.setOriginalModifier(conceptRole.getOriginalModifier());
        roleAssignment.setOriginalModifierId(conceptRole.getOriginalModifierId());
        roleAssignment.setAutomaticRole(conceptRole.getAutomaticRole());
        // fill directly assigned role by superior concept
        UUID directRole = conceptRole.getDirectRole();
        UUID directConcept = conceptRole.getDirectConcept();
        if (directRole != null) { // update / delete / new role composition
            roleAssignment.setDirectRole(directRole);
        } else if (directConcept != null) { // new identity role by superior concept
            directRole =
                    allConcepts.stream().filter(c -> c.getId().equals(directConcept)).findFirst().map(AbstractConceptRoleRequestDto::getRoleAssignmentUuid).orElse(null);
            roleAssignment.setDirectRole(directRole);
        }
        roleAssignment.setRoleComposition(conceptRole.getRoleComposition());
        roleAssignment.setRoleSystem(conceptRole.getRoleSystem());

        return roleAssignment;
    }

    @Override
    public IdmRequestIdentityRoleDto saveRequestRole(IdmRequestIdentityRoleDto dto, BasePermission[] permission) {
        if (dto.getId() != null && dto.getId().equals(dto.getRoleAssignmentUuid())) {
            // Given DTO is identity-role -> create UPDATE concept
            return saveConceptyByIdentityRole(dto, permission);
        }
		else if(dto.getId() == null && dto.getRoleAssignmentUuid() == null) {
            // Given DTO does not have ID neither identity-role ID -> create ADD concept
            return saveAddConcept(dto);
        }
        else {
            // Try to find role-concept
            D roleConceptDto = this.get(dto.getId());
            if (roleConceptDto != null) {
                dto.setState(roleConceptDto.getState());
                if (UPDATE == roleConceptDto.getOperation() || ADD == roleConceptDto.getOperation()) {
                    // Given DTO is concept -> update exists UPDATE concept
                    return this.conceptToRequestIdentityRole(this.save(requestIdentityRoleToConcept(dto), permission));
                }
            }
        }
        return null;
    }

    @Override
    public IdmRequestIdentityRoleDto deleteRequestRole(IdmRequestIdentityRoleDto dto, BasePermission[] permission) {
        // We don`t know if is given DTO identity-role or role-concept.
        if (dto.getId().equals(dto.getRoleAssignmentUuid())) {
            A identityRole = getRoleAssignment(dto.getId());
            // OK given DTO is identity-role

            UUID requestId = dto.getRoleRequest();
            IdmRoleRequestDto request = lookupService.lookupDto(IdmRoleRequestDto.class,dto.getRoleRequest());
            if(requestId == null) {
                throw new ResultCodeException(CoreResultCode.REQUEST_ITEM_CANNOT_BE_CREATED, "Trying to delete concept and not specifying role request");
            }
            IdmRoleRequestDto mockRequest = new IdmRoleRequestDto();
            mockRequest.setId(requestId);
            D concept = createConcept(identityRole, requestId, identityRole.getRole(), ConceptRoleRequestOperation.REMOVE);
            if (request != null) {
                // Add request to concept. Will be used on the FE (prevent loading of request).
                concept.getEmbedded().put(AbstractConceptRoleRequest_.roleRequest.getName(), request);
            }

            return this.conceptToRequestIdentityRole(concept);

        } else {
            // Try to find role-concept
            D roleConceptDto = get(dto.getId());
            if (roleConceptDto != null) {
                // OK given DTO is concept
                delete(roleConceptDto, permission);
                return dto;
            }
        }
        return null;
    }

    private IdmRequestIdentityRoleDto saveAddConcept(IdmRequestIdentityRoleDto dto) {
        Assert.notNull(dto.getOwnerUuid(), "Contract is required.");

        Set<UUID> roles = Sets.newHashSet();
        if (dto.getRole() != null) {
            roles.add(dto.getRole());
        }
        if (dto.getRoles() != null) {
            roles.addAll(dto.getRoles());
        }

        Assert.notEmpty(roles, "Roles cannot be empty!");


        final UUID requestId = dto.getRoleRequest();
        if (dto.getRoleRequest() == null) {
            throw new ResultCodeException(CoreResultCode.REQUEST_ITEM_CANNOT_BE_CREATED, "Creating request item before the request was vreated");
        }
        final IdmRoleRequestDto request = lookupService.lookupDto(IdmRoleRequestDto.class, dto.getRoleRequest());

        List<D> concepts = Lists.newArrayList();
        roles.forEach(role -> {
            D conceptRoleRequest = requestIdentityRoleToConcept(dto);
            conceptRoleRequest.setRoleRequest(requestId);
            conceptRoleRequest.setOperation(ADD);
            conceptRoleRequest.setRole(role);
            // Create concept with EAVs
            conceptRoleRequest = save(conceptRoleRequest);
            if (request != null) {
                // Add request to concept. Will be used on the FE (prevent loading of request).
                conceptRoleRequest.getEmbedded().put(AbstractConceptRoleRequest_.roleRequest.getName(), request);
            }
            concepts.add(conceptRoleRequest);
        });
        // Beware more then one concepts could be created, but only first will be returned!
        return this.conceptToRequestIdentityRole(concepts.get(0));
    }

    private IdmRequestIdentityRoleDto saveConceptyByIdentityRole(IdmRequestIdentityRoleDto dto, BasePermission[] permission) {
        A identityRole = getRoleAssignment(dto.getId());
        Assert.notNull(identityRole, "Identity role is required.");

        UUID requestId = dto.getRoleRequest();
        if(requestId == null) {
            throw new ResultCodeException(CoreResultCode.REQUEST_ITEM_CANNOT_BE_CREATED, "Creating request item, before request was created");
        }

        D conceptRoleRequest = createConcept(identityRole, requestId, identityRole.getRole(),UPDATE);
        conceptRoleRequest.setValidFrom(dto.getValidFrom());
        conceptRoleRequest.setValidTill(dto.getValidTill());
        conceptRoleRequest.setRoleSystem(dto.getRoleSystem());
        conceptRoleRequest.setEavs(dto.getEavs());
        // Create concept with EAVs
        conceptRoleRequest = save(conceptRoleRequest, permission);

        return this.conceptToRequestIdentityRole(conceptRoleRequest);
    }

    /**
     * Create new instance of concept without save
     *
     * @param roleAssignment
     * @param requestId
     * @param requestId
     * @param operation
     * @param roleId
     *
     * @return
     */
    private D createConcept(A roleAssignment, UUID requestId, UUID roleId, ConceptRoleRequestOperation operation) {
        D conceptRoleRequest = createEmptyConceptWithRoleAssignmentData(roleAssignment);
        conceptRoleRequest.setRoleRequest(requestId);
        conceptRoleRequest.setRoleAssignmentUuid(roleAssignment.getId());

        conceptRoleRequest.setRole(roleId);
        conceptRoleRequest.setOperation(operation);
        return save(conceptRoleRequest);
    }


    protected abstract D requestIdentityRoleToConcept(IdmRequestIdentityRoleDto dto);

    protected abstract IdmRequestIdentityRoleDto conceptToRequestIdentityRole(D save);

    protected abstract D createEmptyConceptWithRoleAssignmentData(A roleAssignment);

    protected abstract A getRoleAssignment(UUID id);

    protected abstract A getRoleAssignmentFromConceptInternal(D conceptRole);

    protected abstract A getRoleAssignmentDtoInternal(D concept, IdmRoleDto subRole);

    protected abstract String cancelInvalidConceptInternal(List<A> automaticRoles, D concept,
            IdmRoleRequestDto request);

    protected abstract A createAssignmentFromConceptInternal(D concept);

    protected abstract IdmFormInstanceDto getFormInstance(D dto, IdmFormDefinitionDto definition);

    protected abstract boolean shouldProcessChanges(D dto, ConceptRoleRequestOperation operation);

    protected abstract UUID getIdentityRoleId(D concept);
}
