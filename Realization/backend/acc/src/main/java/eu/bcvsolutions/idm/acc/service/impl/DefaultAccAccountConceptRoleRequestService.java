package eu.bcvsolutions.idm.acc.service.impl;

import eu.bcvsolutions.idm.acc.dto.AccAccountConceptRoleRequestDto;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccAccountRoleAssignmentDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccAccountConceptRoleRequestFilter;
import eu.bcvsolutions.idm.acc.dto.filter.AccIdentityAccountFilter;
import eu.bcvsolutions.idm.acc.entity.AccAccountConceptRoleRequest;
import eu.bcvsolutions.idm.acc.entity.AccAccountConceptRoleRequest_;
import eu.bcvsolutions.idm.acc.entity.AccIdentityAccount;
import eu.bcvsolutions.idm.acc.entity.AccIdentityAccount_;
import eu.bcvsolutions.idm.acc.event.AccAccountRoleAssignmentEvent;
import eu.bcvsolutions.idm.acc.service.api.AccAccountConceptRoleRequestService;
import eu.bcvsolutions.idm.acc.service.api.AccAccountRoleAssignmentService;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.impl.adapter.AccAccountConceptRoleRequestAdapter;
import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.ApplicantDto;
import eu.bcvsolutions.idm.core.api.dto.ApplicantImplDto;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAccountDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRequestIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.filter.BaseFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRequestIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.event.CoreEvent;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.repository.AbstractEntityRepository;
import eu.bcvsolutions.idm.core.api.service.ApplicantService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCompositionService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleSystemService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.service.adapter.DtoAdapter;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.entity.AbstractConceptRoleRequest_;
import eu.bcvsolutions.idm.core.model.entity.AbstractRoleAssignment_;
import eu.bcvsolutions.idm.core.model.event.AbstractRoleAssignmentEvent;
import eu.bcvsolutions.idm.core.model.repository.IdmAutomaticRoleRepository;
import eu.bcvsolutions.idm.core.model.service.impl.AbstractConceptRoleRequestService;
import eu.bcvsolutions.idm.core.security.api.domain.ContractBasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.RoleBasePermission;
import eu.bcvsolutions.idm.core.security.api.utils.PermissionUtils;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowProcessInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Peter Štrunc <github.com/peter-strunc>
 */
@Service("accountConceptRoleService")
public class DefaultAccAccountConceptRoleRequestService extends AbstractConceptRoleRequestService<AccAccountRoleAssignmentDto, AccAccountConceptRoleRequestDto, AccAccountConceptRoleRequest,
        AccAccountConceptRoleRequestFilter> implements AccAccountConceptRoleRequestService {


    private final AccAccountRoleAssignmentService accRoleAccountService;
    private final FormService formService;
    private final IdmRoleService roleService;
    private final AccAccountService accountService;
    private final AccIdentityAccountService identityAccountService;

    private final IdmIdentityService identityService;

    private final LookupService lookupService;

    private final IdmRoleSystemService roleSystemService;

    private final WorkflowProcessInstanceService workflowProcessInstanceService;

    private final IdmIdentityContractService contractService;

    private final IdmRoleRequestService requestService;

    @Autowired
    public DefaultAccAccountConceptRoleRequestService(AbstractEntityRepository<AccAccountConceptRoleRequest> repository, WorkflowProcessInstanceService workflowProcessInstanceService,
            LookupService lookupService, IdmAutomaticRoleRepository automaticRoleRepository, AccAccountRoleAssignmentService accRoleAccountService, FormService formService,
            IdmRoleCompositionService roleCompositionService, IdmRoleService roleService, AccAccountService accountService, AccIdentityAccountService identityAccountService,
            AccAccountRoleAssignmentService roleAssignmentService, IdmIdentityService identityService, LookupService lookupService1, IdmRoleSystemService roleSystemService,
            WorkflowProcessInstanceService workflowProcessInstanceService1, IdmIdentityContractService contractService, @Lazy IdmRoleRequestService requestService) {
        super(repository, workflowProcessInstanceService, roleCompositionService, lookupService, automaticRoleRepository, roleAssignmentService);
        this.accRoleAccountService = accRoleAccountService;
        this.formService = formService;
        this.roleService = roleService;
        this.accountService = accountService;
        this.identityAccountService = identityAccountService;
        this.identityService = identityService;
        this.lookupService = lookupService1;
        this.roleSystemService = roleSystemService;
        this.workflowProcessInstanceService = workflowProcessInstanceService1;
        this.contractService = contractService;
        this.requestService = requestService;
    }

    @Override
    protected List<Predicate> toPredicates(Root<AccAccountConceptRoleRequest> root, CriteriaQuery<?> query, CriteriaBuilder builder, AccAccountConceptRoleRequestFilter filter) {
        List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
        //
        if (filter.getAccountRole() != null) {
            predicates.add(builder.equal(root.get(AccAccountConceptRoleRequest_.accountRole).get(AbstractEntity_.id), filter.getAccountRole()));
        }
        if (filter.getAccountuuid() != null) {
            predicates.add(builder.equal(root.get(AccAccountConceptRoleRequest_.account).get(AbstractEntity_.id), filter.getAccountuuid()));
        }

        Set<UUID> ids = filter.getAccountRoleUuids();
        if (ids != null && !ids.isEmpty()) {
            predicates.add(root.get(AccAccountConceptRoleRequest_.accountRole).get(AbstractEntity_.id).in(ids));
        }

        if (filter.isAccountRoleIsNull() && ids != null && ids.isEmpty()) {
            predicates.add(builder.isNull(root.get(AccAccountConceptRoleRequest_.accountRole)));
        }
        //
        if (Objects.nonNull(filter.getIdentity())) {
            Subquery<AccIdentityAccount> identityAccountSubquery = query.subquery(AccIdentityAccount.class);
            final Root<AccIdentityAccount> identityAccountRoot = identityAccountSubquery.from(AccIdentityAccount.class);
            identityAccountSubquery.select(identityAccountRoot);

            identityAccountSubquery.where(
                    builder.and(
                            builder.equal(root.get(AccAccountConceptRoleRequest_.account), identityAccountRoot.get(AccIdentityAccount_.account)),
                            builder.equal(identityAccountRoot.get(AccIdentityAccount_.identity).get(AbstractEntity_.id), filter.getIdentity())
                    ));
            predicates.add(builder.exists(identityAccountSubquery));
        }
        //
        return predicates;
    }

    @Override
    protected IdmFormValueDto getCurrentFormValue(UUID identityRoleId, IdmFormValueDto value, IdmFormAttributeDto formAttributeDto) {
        return formService.getValues(new AccAccountRoleAssignmentDto(identityRoleId), formAttributeDto).stream()
                .filter(identityRoleValue -> identityRoleValue.getSeq() == value.getSeq())
                .findFirst()
                .orElse(null);
    }


    @Override
    protected AccAccountRoleAssignmentDto getRoleAssignmentFromConceptInternal(AccAccountConceptRoleRequestDto conceptRole) {
        if (conceptRole == null) {
            return null;
        }
        AccAccountRoleAssignmentDto accountRoleDto = new AccAccountRoleAssignmentDto();
        accountRoleDto.setAccount(conceptRole.getAccount());
        return accountRoleDto;
    }

    @Override
    protected AccAccountRoleAssignmentDto getRoleAssignmentDtoInternal(AccAccountConceptRoleRequestDto concept, IdmRoleDto subRole) {
        AccAccountDto account = DtoUtils.getEmbedded(concept, AccAccountConceptRoleRequest_.account, AccAccountDto.class, null);
        AccAccountRoleAssignmentDto identityRoleDto = new AccAccountRoleAssignmentDto();
        identityRoleDto.setAccount(account.getId());
        return identityRoleDto;
    }

    @Override
    protected String cancelInvalidConceptInternal(List<AccAccountRoleAssignmentDto> automaticRoles, AccAccountConceptRoleRequestDto concept, IdmRoleRequestDto request) {
        return null;
    }

    @Override
    protected AccAccountRoleAssignmentDto createAssignmentFromConceptInternal(AccAccountConceptRoleRequestDto concept) {
        AccAccountRoleAssignmentDto result = new AccAccountRoleAssignmentDto();
        result.setAccount(concept.getAccount());

        return result;
    }

    @Override
    protected IdmFormInstanceDto getFormInstance(AccAccountConceptRoleRequestDto dto, IdmFormDefinitionDto definition) {
        AccAccountRoleAssignmentDto identityRoleDto = DtoUtils.getEmbedded(dto, AccAccountConceptRoleRequest_.accountRole, AccAccountRoleAssignmentDto.class, null);
        if (identityRoleDto == null) {
            identityRoleDto = accRoleAccountService.get(dto.getAccountRole());
        }
        return formService.getFormInstance(new IdmIdentityRoleDto(identityRoleDto.getId()), definition);
    }

    @Override
    protected boolean shouldProcessChanges(AccAccountConceptRoleRequestDto dto, ConceptRoleRequestOperation operation) {
        return dto.getAccountRole() != null && ConceptRoleRequestOperation.UPDATE == operation;
    }

    @Override
    public AccAccountConceptRoleRequestFilter getFilter() {
        return new AccAccountConceptRoleRequestFilter();
    }

    @Override
    public Set<String> getTransitivePermissions(AccAccountConceptRoleRequestDto concept) {
        Set<String> result = new HashSet<>();
        AccAccountDto account = accountService.get(concept.getOwnerUuid());
        Set<String> accountPermissions = accountService.getPermissions(account);

        if (PermissionUtils.hasPermission(accountPermissions, ContractBasePermission.CHANGEPERMISSION)) {
            result.add(ContractBasePermission.CHANGEPERMISSION.getName());
        } else {
            // by related role
            IdmRoleDto role = lookupService.lookupEmbeddedDto(concept, AbstractConceptRoleRequest_.role);
            Set<String> rolePermissions = roleService.getPermissions(role);

            if (PermissionUtils.hasPermission(rolePermissions, RoleBasePermission.CHANGEPERMISSION)) {
                result.add(RoleBasePermission.CHANGEPERMISSION.getName());
            }
        }
        return result;
    }

    @Override
    public AccAccountRoleAssignmentDto getEmbeddedAssignment(AccAccountConceptRoleRequestDto concept) {
        return DtoUtils.getEmbedded(concept,
                AccAccountConceptRoleRequest_.accountRole, AccAccountRoleAssignmentDto.class, null);
    }

    @Override
    public AccAccountRoleAssignmentDto fetchAssignment(AccAccountConceptRoleRequestDto concept) {
        return accRoleAccountService.get(concept.getAccountRole());
    }

    @Override
    public ApplicantDto resolveApplicant(IdmRequestIdentityRoleDto dto) {
        final AccAccountDto accAccountDto = accountService.get(dto.getOwnerUuid());

        ApplicantImplDto applicantDto = new ApplicantImplDto();
        applicantDto.setId(accAccountDto.getTargetEntityId());
        applicantDto.setConceptOwner(accAccountDto.getId());

        ApplicantService applicantService = requestService.getApplicantServiceByAccountType(accAccountDto.getTargetEntityType());
        if (applicantService != null) {
            BaseDto applicant = applicantService.get(accAccountDto.getTargetEntityId());
            applicantDto.setApplicantType(applicant.getClass().getCanonicalName());
            // We do not check validity of an account here. Maybe we could use contracts of identity
            // but at this point it is not necessary
        }
        return applicantDto;
    }

    @Override
    public List<UUID> resolveManagerContractsForApproval(AccAccountConceptRoleRequestDto conceptRole) {
        final LocalDate now = LocalDate.now();

        IdmRoleRequestDto roleRequest = DtoUtils.getEmbedded(conceptRole, "roleRequest", IdmRoleRequestDto.class);
        List<IdmIdentityDto> guarantorsForApplicant = requestService.getGuarantorsForApplicant(roleRequest);

        return guarantorsForApplicant.stream().flatMap(identityDto -> contractService.findAllValidForDate(identityDto.getId(), now, null).stream())
                .map(AbstractDto::getId)
                .collect(Collectors.toList());
    }

    @Override
    protected UUID getIdentityRoleId(AccAccountConceptRoleRequestDto concept) {
        UUID accountRoleId = null;
        AccAccountRoleAssignmentDto accountRoleDto = DtoUtils.getEmbedded(concept, AccAccountConceptRoleRequest_.accountRole, AccAccountRoleAssignmentDto.class, null);
        if (accountRoleDto == null) {
            accountRoleId = concept.getAccountRole();
        } else {
            accountRoleId = accountRoleDto.getId();
        }
        return accountRoleId;
    }

    @Override
    public IdmRoleDto determineRoleFromConcept(AccAccountConceptRoleRequestDto concept) {
        IdmRoleDto role = null;
        if (concept.getRole() != null) {
            role = DtoUtils.getEmbedded(concept, AbstractConceptRoleRequest_.role, IdmRoleDto.class, null);
            if (role == null) {
                role = roleService.get(concept.getRole());
            }
        } else {
            AccAccountRoleAssignmentDto accountRole = DtoUtils.getEmbedded(concept, AccAccountConceptRoleRequest_.accountRole, AccAccountRoleAssignmentDto.class, null);
            if (accountRole == null) {
                accountRole = accRoleAccountService.get(concept.getAccountRole());
            }
            if (accountRole != null) {
                role = DtoUtils.getEmbedded(concept, AbstractRoleAssignment_.role, IdmRoleDto.class);
            }
        }
        return role;
    }

    @Override
    public boolean validOwnership(AccAccountConceptRoleRequestDto concept, UUID applicantId) {
        // concept for account is valid only if applicant is the same as the account owner
        AccIdentityAccountFilter identityAccountFilter = new AccIdentityAccountFilter();
        identityAccountFilter.setAccountId(concept.getAccount());
        return identityAccountService.find(identityAccountFilter, null).stream().allMatch(accIdentityAccountDto -> accIdentityAccountDto.getIdentity().equals(applicantId));
    }

    @Override
    public List<AccAccountConceptRoleRequestDto> findAllByRoleRequest(UUID requestId, Pageable pa, IdmBasePermission... permissions) {
        AccAccountConceptRoleRequestFilter filter = new AccAccountConceptRoleRequestFilter();
        filter.setRoleRequestId(requestId);
        return find(filter, pa, permissions).getContent();
    }

    @Override
    public CoreEvent<AccAccountRoleAssignmentDto> removeRelatedRoleAssignment(AccAccountConceptRoleRequestDto concept, EntityEvent<IdmRoleRequestDto> requestEvent) {
        AccAccountRoleAssignmentDto accountRole = DtoUtils.getEmbedded(concept, AccAccountConceptRoleRequest_.accountRole.getName(), AccAccountRoleAssignmentDto.class, null);
        if (accountRole == null) {
            accountRole = accRoleAccountService.get(concept.getAccountRole());
        }

        if (accountRole != null) {
            concept.setState(RoleRequestState.EXECUTED);
            concept.setAccountRole(null); // we have to remove relation on
            // deleted accountRole
            String message = MessageFormat.format("IdentityRole [{0}] (reqested in concept [{1}]) was deleted (from " + "this " + "role request).", accountRole.getId(), concept.getId());
            addToLog(concept, message);
            addToLog(requestEvent.getContent(), message);
            save(concept);

            AccAccountRoleAssignmentEvent event = new AccAccountRoleAssignmentEvent(AbstractRoleAssignmentEvent.RoleAssignmentEventType.DELETE, accountRole, Map.of(IdmAccountDto.SKIP_PROPAGATE,
                    Boolean.TRUE));

            accRoleAccountService.publish(event, requestEvent);
            return event;
        }
        return null;
    }

    @Override
    protected AccAccountRoleAssignmentDto extractRoleAssignmentFromConcept(AccAccountConceptRoleRequestDto concept) {
        AccAccountRoleAssignmentDto accountRoleAssignment = DtoUtils.getEmbedded(concept, AccAccountConceptRoleRequest_.accountRole, AccAccountRoleAssignmentDto.class, (AccAccountRoleAssignmentDto) null);
        if (accountRoleAssignment == null) {
            accountRoleAssignment = accRoleAccountService.get(concept.getAccountRole());
        }
        return accountRoleAssignment;
    }

    @Override
    protected AccAccountConceptRoleRequestDto requestIdentityRoleToConcept(IdmRequestIdentityRoleDto dto) {
        final AccAccountConceptRoleRequestDto map = modelMapper.map(dto, AccAccountConceptRoleRequestDto.class);
        map.setAccount(dto.getOwnerUuid());
        map.setAccountRole(dto.getRoleAssignmentUuid());
        return map;
    }

    @Override
    protected IdmRequestIdentityRoleDto conceptToRequestIdentityRole(AccAccountConceptRoleRequestDto save) {
        return modelMapper.map(save, IdmRequestIdentityRoleDto.class);
    }

    @Override
    protected AccAccountConceptRoleRequestDto createEmptyConceptWithRoleAssignmentData(AccAccountRoleAssignmentDto roleAssignment) {
        AccAccountConceptRoleRequestDto conceptRoleRequest = new AccAccountConceptRoleRequestDto();
        conceptRoleRequest.setOwnerUuid(roleAssignment.getAccount());

        return conceptRoleRequest;
    }

    @Override
    protected AccAccountRoleAssignmentDto getRoleAssignment(UUID id) {
        return accRoleAccountService.get(id);
    }

    @Override
    public AccAccountConceptRoleRequestDto createConceptToRemoveIdentityRole(AccAccountConceptRoleRequestDto concept, AccAccountRoleAssignmentDto identityRoleAssignment) {
        AccAccountConceptRoleRequestDto removeConcept = new AccAccountConceptRoleRequestDto();
        removeConcept.setAccount(identityRoleAssignment.getAccount());
        removeConcept.setAccountRole(identityRoleAssignment.getId());
        removeConcept.setOperation(ConceptRoleRequestOperation.REMOVE);
        if (concept != null) {
            removeConcept.setRoleRequest(concept.getRoleRequest());
        }
        removeConcept.addToLog(MessageFormat.format("Removed by duplicates with subrole id [{}]", identityRoleAssignment.getRoleComposition()));
        removeConcept = save(removeConcept);
        return removeConcept;
    }

    @Override
    public AccAccountConceptRoleRequestDto createConceptToRemoveIdentityRole(AccAccountRoleAssignmentDto roleAssignment) {
        AccAccountConceptRoleRequestDto removeConcept = createEmptyConcept();
        removeConcept.setAccount(roleAssignment.getAccount());
        removeConcept.setAccountRole(roleAssignment.getId());
        removeConcept.setOperation(ConceptRoleRequestOperation.REMOVE);
        return removeConcept;
    }

    @Override
    public AccAccountConceptRoleRequestDto createEmptyConcept() {
        return new AccAccountConceptRoleRequestDto();
    }

    @Override
    public Class<AccAccountConceptRoleRequestDto> getType() {
        return AccAccountConceptRoleRequestDto.class;
    }

    @Override
    public <F2 extends BaseFilter> DtoAdapter<AccAccountConceptRoleRequestDto, IdmRequestIdentityRoleDto> getAdapter(F2 originalFilter) {

        // Need to translate the filter. API is too general here, but
        IdmRequestIdentityRoleFilter translatedFilter =  modelMapper.map(originalFilter, IdmRequestIdentityRoleFilter.class);
        return new AccAccountConceptRoleRequestAdapter(accRoleAccountService, this,
                roleSystemService, translatedFilter, workflowProcessInstanceService, modelMapper, requestService);
    }

    @Override
    public Class<?> getOwnerType() {
        return AccAccountDto.class;
    }
}
