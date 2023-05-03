package eu.bcvsolutions.idm.core.model.service.impl;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.domain.Pair;
import eu.bcvsolutions.idm.core.api.dto.AbstractRoleAssignmentDto;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAccountDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.BaseRoleAssignmentFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.DataFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.repository.filter.FilterManager;
import eu.bcvsolutions.idm.core.api.service.AbstractReadDtoService;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.IdmRoleAssignmentService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleSystemService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.service.adapter.PluggableRoleAssignmentDeduplicator;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.InvalidFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.service.AbstractFormableService;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.entity.AbstractRoleAssignment;
import eu.bcvsolutions.idm.core.model.entity.AbstractRoleAssignment_;
import eu.bcvsolutions.idm.core.model.entity.IdmAutomaticRole;
import eu.bcvsolutions.idm.core.model.entity.IdmAutomaticRoleAttribute;
import eu.bcvsolutions.idm.core.model.entity.IdmRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogueRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogueRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmRole_;
import eu.bcvsolutions.idm.core.model.event.AbstractRoleAssignmentEvent;
import eu.bcvsolutions.idm.core.model.repository.IdmAutomaticRoleRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmRoleAssignmentRepository;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;

/**
 * @author Peter Štrunc <github.com/peter-strunc>
 */
public abstract class AbstractRoleAssignmentService<D extends AbstractRoleAssignmentDto, E extends AbstractRoleAssignment, F extends BaseRoleAssignmentFilter>
        extends AbstractFormableService<D, E, F> implements IdmRoleAssignmentService<D, F>, ApplicationContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractRoleAssignmentService.class);

    private final IdmRoleService roleService;
    private final IdmAutomaticRoleRepository automaticRoleRepository;
    private final LookupService lookupService;
    @Autowired
    private IdmRoleSystemService roleSystemService;
    @Autowired(required = false)
    private List<PluggableRoleAssignmentDeduplicator> deduplicators;

    private final IdmRoleAssignmentRepository<E> roleAssignmentRepository;

    private final FilterManager filterManager;
    protected ApplicationContext applicationContext;

    protected AbstractRoleAssignmentService(IdmRoleAssignmentRepository<E> repository, EntityEventManager entityEventManager, FormService formService, IdmRoleService roleService, IdmAutomaticRoleRepository automaticRoleRepository, LookupService lookupService, FilterManager filterManager) {
        super(repository, entityEventManager, formService);
        this.roleService = roleService;
        this.automaticRoleRepository = automaticRoleRepository;
        this.lookupService = lookupService;
        this.roleAssignmentRepository = repository;
        this.filterManager = filterManager;
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
        // by role text
        String roleText = filter.getRoleText();
        if (StringUtils.isNotEmpty(roleText)) {
            IdmRoleFilter subFilter = new IdmRoleFilter();
            subFilter.setText(roleText);
            Subquery<IdmRole> subquery = query.subquery(IdmRole.class);
            Root<IdmRole> subRoot = subquery.from(IdmRole.class);
            subquery.select(subRoot);
            //
            Predicate rolePredicate = filterManager
                    .getBuilder(IdmRole.class, DataFilter.PARAMETER_TEXT)
                    .getPredicate(subRoot, subquery, builder, subFilter);
            //
            subquery.where(
                    builder.and(
                            builder.equal(root.get(AbstractRoleAssignment_.role), subRoot), // correlation attr
                            rolePredicate
                    )
            );
            //
            predicates.add(builder.exists(subquery));
        }
        List<UUID> roles = filter.getRoles();
        if (!roles.isEmpty()) {
            predicates.add(root.get(AbstractRoleAssignment_.role).get(AbstractEntity_.id).in(roles));
        }
        List<String> roleEnvironments = filter.getRoleEnvironments();
        if (CollectionUtils.isNotEmpty(roleEnvironments)) {
            predicates.add(root.get(AbstractRoleAssignment_.role).get(IdmRole_.environment).in(roleEnvironments));
        }
        UUID roleCatalogueId = filter.getRoleCatalogueId();
        if (roleCatalogueId != null) {
            Subquery<IdmRoleCatalogueRole> roleCatalogueRoleSubquery = query.subquery(IdmRoleCatalogueRole.class);
            Root<IdmRoleCatalogueRole> subRootRoleCatalogueRole = roleCatalogueRoleSubquery.from(IdmRoleCatalogueRole.class);
            roleCatalogueRoleSubquery.select(subRootRoleCatalogueRole);

            roleCatalogueRoleSubquery.where(
                    builder.and(
                            builder.equal(subRootRoleCatalogueRole.get(IdmRoleCatalogueRole_.role), root.get(AbstractRoleAssignment_.role)),
                            builder.equal(subRootRoleCatalogueRole.get(IdmRoleCatalogueRole_.roleCatalogue).get(AbstractEntity_.id), roleCatalogueId)
                    ));
            predicates.add(builder.exists(roleCatalogueRoleSubquery));
        }
        //
        // is automatic role
        Boolean automaticRole = filter.getAutomaticRole();
        if (automaticRole != null) {
            if (automaticRole) {
                predicates.add(builder.isNotNull(root.get(AbstractRoleAssignment_.automaticRole)));
            } else {
                predicates.add(builder.isNull(root.get(AbstractRoleAssignment_.automaticRole)));
            }
        }
        //
        UUID automaticRoleId = filter.getAutomaticRoleId();
        if (automaticRoleId != null) {
            predicates.add(builder.equal(
                    root.get(AbstractRoleAssignment_.automaticRole).get(AbstractEntity_.id),
                    automaticRoleId)
            );
        }
        //
        UUID roleCompositionId = filter.getRoleCompositionId();
        if (roleCompositionId != null) {
            predicates.add(builder.equal(root.get(AbstractRoleAssignment_.roleComposition).get(AbstractEntity_.id), roleCompositionId));
        }
        //
        // Role-system
        UUID roleSystemId = filter.getRoleSystemId();
        if (roleSystemId != null) {
            predicates.add(builder.equal(root.get(AbstractRoleAssignment_.roleSystem), roleSystemId));
        }


        return predicates;
    }


    @Override
    protected D toDto(E entity, D dto) {
        dto = super.toDto(entity, dto);
        if (dto == null) {
            return null;
        }

        IdmAutomaticRole automaticRole = entity.getAutomaticRole();
        if (automaticRole != null) {
            dto.setAutomaticRole(automaticRole.getId());
            BaseDto baseDto = null;
            Map<String, BaseDto> embedded = dto.getEmbedded();
            if (automaticRole instanceof IdmAutomaticRoleAttribute) {
                baseDto = lookupService.getDtoService(IdmAutomaticRoleAttributeDto.class).get(automaticRole.getId());
            } else {
                baseDto = lookupService.getDtoService(IdmRoleTreeNodeDto.class).get(automaticRole.getId());
            }
            embedded.put(AbstractRoleAssignment_.automaticRole.getName(), baseDto);
            dto.setEmbedded(embedded);
        }

        UUID roleSystemId = entity.getRoleSystem();
        if (roleSystemId != null) {
            Map<String, BaseDto> embedded = dto.getEmbedded();
            if (roleSystemService instanceof AbstractReadDtoService) {
                @SuppressWarnings("rawtypes")
                BaseDto baseDto = ((AbstractReadDtoService) roleSystemService).get(roleSystemId);
                embedded.put(AbstractRoleAssignment_.roleSystem.getName(), baseDto);
                dto.setEmbedded(embedded);
            }
        }

        return dto;
    }

    @Override
    protected List<IdmFormInstanceDto> getFormInstances(D result, BasePermission... permission) {
        IdmFormInstanceDto formInstanceDto = getRoleAttributeValues(result);
        if (formInstanceDto != null) {
            // Validate the form instance
            formInstanceDto.setValidationErrors(getFormService().validate(formInstanceDto));
            return Lists.newArrayList(formInstanceDto);
        }
        return null;
    }

    @Override
    protected E toEntity(D dto, E entity) {
        E resultEntity = super.toEntity(dto, entity);
        // set additional automatic role
        if (resultEntity != null && dto.getAutomaticRole() != null) {
            // it isn't possible use lookupService entity lookup
            IdmAutomaticRole automaticRole = automaticRoleRepository.findById(dto.getAutomaticRole()).orElse(null);
            resultEntity.setAutomaticRole(automaticRole);
        }
        return resultEntity;
    }

    public IdmFormInstanceDto getRoleAttributeValues(D dto) {
        Assert.notNull(dto, "DTO is required.");
        // If given identity-role contains one formInstance, then will be returned
        List<IdmFormInstanceDto> eavs = dto.getEavs();
        if (eavs != null && eavs.size() == 1) {
            return eavs.get(0);
        }

        UUID roleId = dto.getRole();
        if (roleId != null) {
            IdmRoleDto role = DtoUtils.getEmbedded(dto, AbstractRoleAssignment_.role, IdmRoleDto.class);
            // Has role filled attribute definition?
            UUID formDefinition = role.getIdentityRoleAttributeDefinition();
            if (formDefinition != null) {
                IdmFormDefinitionDto formDefinitionDto = roleService.getFormAttributeSubdefinition(role);
                // thin dto can be given -> owner is normal dto
                return this.getFormService().getFormInstance(dto, formDefinitionDto);
            }
        }
        return null;
    }

    public List<InvalidFormAttributeDto> validateFormAttributes(D identityRole) {
        IdmFormInstanceDto formInstanceDto = this.getRoleAttributeValues(identityRole);
        if (formInstanceDto != null) {
            return this.getFormService().validate(formInstanceDto);
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<D> findByAutomaticRole(UUID automaticRoleId, Pageable pageable) {
        return toDtoPage(roleAssignmentRepository.findByAutomaticRole_Id(automaticRoleId, pageable));
    }

    @Transactional(readOnly = true)
    public Page<D> findExpiredRoles(LocalDate expirationDate, Pageable page) {
        Assert.notNull(expirationDate, "Expiration date is required.");
        //
        return toDtoPage(roleAssignmentRepository.findExpiredRoles(expirationDate, page));
    }

    public Page<D> findDirectExpiredRoles(LocalDate expirationDate, Pageable page) {
        Assert.notNull(expirationDate, "Expiration date is required.");
        //
        return toDtoPage(roleAssignmentRepository.findDirectExpiredRoles(expirationDate, page));
    }

    public List<UUID> findDirectExpiredRoleIds(LocalDate expirationDate) {
        Assert.notNull(expirationDate, "Expiration date is required.");
        //
        return roleAssignmentRepository.findDirectExpiredRoleIds(expirationDate);
    }

    public D getDuplicated(D one, D two, Boolean skipSubdefinition) {
        LOG.debug("Deduplicating role assignments [{}] and [{}]", one, two);
        List<Pair<AbstractRoleAssignmentDto, Boolean>> duplicates = new ArrayList<>();
        if (deduplicators == null) {
            LOG.debug("No deduplicators found. Not possible to deduplicate role assignments [{}] and [{}].", one, two);
            return null;
        }
        for (PluggableRoleAssignmentDeduplicator deduplicator : deduplicators) {
            LOG.debug("Deduplicating role assignments [{}] and [{}] by [{}]", one, two, deduplicator);
            AbstractRoleAssignmentDto duplicated = deduplicator.getDuplicated(one, two, skipSubdefinition);
            if (duplicated == null) {
                LOG.debug("Deduplicating role assignments [{}] and [{}] by [{}] - not duplicated", one, two, deduplicator);
                return null;
            } else {
                Pair<AbstractRoleAssignmentDto, Boolean> duplicate = new Pair<>(duplicated, deduplicator.considerOrder());
                duplicates.add(duplicate);
                LOG.debug("Deduplicating role assignments [{}] and [{}] by [{}] - duplicated", one, two, deduplicator);
            }
        }
        if (duplicates.isEmpty()) {
            LOG.debug("No duplicates found.");
            return null;
        }

        Pair<AbstractRoleAssignmentDto, Boolean> result = null;

        for (Pair<AbstractRoleAssignmentDto, Boolean> duplicate : duplicates) {
            if (result == null) {
                result = duplicate;
            } else {
                if (result.getFirst() != duplicate.getFirst() && (!result.getSecond() && duplicate.getSecond())) {
                        result = duplicate;
                }
            }
        }

        if (result == null) {
            LOG.debug("No duplicates found.");
            return null;
        }

        LOG.debug("Deduplicated role assignments [{}] and [{}] with result [{}]", one, two, result.getFirst());
        return (D) result.getFirst();
    }

    /**
     * Check if given {@link IdmIdentityRoleDto} is automatic or business role.
     *
     * @param identityRole
     * @return
     */
    public boolean isRoleAutomaticOrComposition(D identityRole) {
        return identityRole.getAutomaticRole() != null || identityRole.getDirectRole() != null;
    }

    @Override
    public void unassignAllSubRoles(UUID identityRoleId, EntityEvent<D> parentEvent) {
        F filter = getFilter();
        filter.setDirectRoleId(identityRoleId);
        find(filter, null)
        .forEach(subIdentityRole -> {
            final AbstractRoleAssignmentEvent<D> subEvent = getEventForAssignment(subIdentityRole, AbstractRoleAssignmentEvent.RoleAssignmentEventType.DELETE);
            //IdentityRoleEvent subEvent = new IdentityRoleEvent(AbstractRoleAssignmentEvent.RoleAssignmentEventType.DELETE, subIdentityRole);
            //
            publish(subEvent, parentEvent);
            // Notes identity-accounts to ACM
            notingIdentityAccountForDelayedAcm(parentEvent, subEvent);
        });
    }

    /**
     * Method for noting identity-accounts for delayed account management
     *
     * @param event
     * @param subEvent
     */
    @SuppressWarnings("unchecked")
    private void notingIdentityAccountForDelayedAcm(EntityEvent<D> event,
            EntityEvent<D> subEvent) {
        Assert.notNull(event, "Event is required.");
        Assert.notNull(subEvent, "Sub event is required.");

        if (!event.getProperties().containsKey(IdmAccountDto.IDENTITY_ACCOUNT_FOR_DELAYED_ACM)) {
            event.getProperties().put(IdmAccountDto.IDENTITY_ACCOUNT_FOR_DELAYED_ACM, new HashSet<UUID>());
        }

        Set<UUID> identityAccounts = (Set<UUID>) subEvent.getProperties()
                .get(IdmAccountDto.IDENTITY_ACCOUNT_FOR_DELAYED_ACM);
        if (identityAccounts != null) {
            ((Set<UUID>) event.getProperties().get(IdmAccountDto.IDENTITY_ACCOUNT_FOR_DELAYED_ACM))
                    .addAll(identityAccounts);
        }

        if (!event.getProperties().containsKey(IdmAccountDto.ACCOUNT_FOR_ADDITIONAL_PROVISIONING)) {
            event.getProperties().put(IdmAccountDto.ACCOUNT_FOR_ADDITIONAL_PROVISIONING, new HashSet<UUID>());
        }

        Set<UUID> accounts = (Set<UUID>) subEvent.getProperties()
                .get(IdmAccountDto.ACCOUNT_FOR_ADDITIONAL_PROVISIONING);
        if (accounts != null) {
            ((Set<UUID>) event.getProperties().get(IdmAccountDto.ACCOUNT_FOR_ADDITIONAL_PROVISIONING))
                    .addAll(accounts);
        }
    }

    protected Map<String, Serializable> setupFlags(String... flags) {
        return Arrays.stream(Optional.ofNullable(flags).orElse(new String[0]))
                .collect(Collectors.toMap(s -> s, s -> Boolean.TRUE));
    }


    /**
     * Check if given dates is in range/interval the second ones.
     *
     * @param validFrom
     * @param validTill
     * @param rangeFrom
     * @param rangeTill
     * @return
     */
    private boolean isDatesInRange(LocalDate validFrom, LocalDate validTill, LocalDate rangeFrom, LocalDate rangeTill) {
        boolean leftIntervalSideOk = false;
        boolean rightIntervalSideOk = false;

        if (rangeFrom == null || (validFrom != null && (rangeFrom.isBefore(validFrom) || rangeFrom.isEqual(validFrom)))) {
            leftIntervalSideOk = true;
        }

        if (rangeTill == null || (validTill != null && (rangeTill.isAfter(validTill) || rangeTill.isEqual(validTill)))) {
            rightIntervalSideOk = true;
        }

        return leftIntervalSideOk && rightIntervalSideOk;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
