package eu.bcvsolutions.idm.core.model.service.impl;

import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.dto.AbstractConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.AbstractRoleAssignmentDto;
import eu.bcvsolutions.idm.core.api.dto.ApplicantDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRequestIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.BaseFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmBaseConceptRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRequestIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.repository.filter.FilterManager;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestManager;
import eu.bcvsolutions.idm.core.api.service.IdmGeneralConceptRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.adapter.AdaptableService;
import eu.bcvsolutions.idm.core.api.service.adapter.DtoAdapter;
import eu.bcvsolutions.idm.core.api.utils.AutowireHelper;
import eu.bcvsolutions.idm.core.api.utils.ReflectionUtils;
import eu.bcvsolutions.idm.core.model.service.util.MultiSourcePagedResource;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import org.modelmapper.ModelMapper;
import org.modelmapper.internal.util.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Peter Štrunc <github.com/peter-strunc>
 */
@Service("conceptManager")
public class DefaultIdmConceptRoleRequestManager extends  AbstractAdaptableMultiService<IdmRequestIdentityRoleDto, IdmRequestIdentityRoleFilter, IdmRequestIdentityRoleDto> implements IdmConceptRoleRequestManager {

    private final Map<Class<? extends AbstractConceptRoleRequestDto>, IdmGeneralConceptRoleRequestService> conceptServices;
    private final ModelMapper modelMapper;

    private final FilterManager filterManager;
    @Autowired
    public DefaultIdmConceptRoleRequestManager(
            List<IdmGeneralConceptRoleRequestService<?, ?, ?>> conceptServices,
            ModelMapper modelMapper, FilterManager filterManager) {
        super(modelMapper, conceptServices);
        this.conceptServices = conceptServices.stream()
                .collect(Collectors.toMap(IdmGeneralConceptRoleRequestService::getType, s -> s, ReflectionUtils::resolveMultipleServices));
        this.modelMapper = modelMapper;
        this.filterManager = filterManager;
    }

    @Override
    public <C extends AbstractConceptRoleRequestDto> void save(C concept) {
        getServiceForConcept(concept).save(concept);
    }

    @Override
    public <C extends AbstractConceptRoleRequestDto> IdmGeneralConceptRoleRequestService<AbstractRoleAssignmentDto, C, IdmBaseConceptRoleRequestFilter> getServiceForConcept(AbstractConceptRoleRequestDto concept) {
        return conceptServices.get(concept.getClass());
    }

    @Override
    public <C extends AbstractConceptRoleRequestDto> IdmGeneralConceptRoleRequestService<AbstractRoleAssignmentDto, C, IdmBaseConceptRoleRequestFilter> getServiceForConcept(Class<C> assignmentType) {
        return conceptServices.get(assignmentType);
    }

    @Override
    public List<AbstractConceptRoleRequestDto> findAllByRoleRequest(UUID id, Pageable pageable, IdmBasePermission... permissions) {
        final List<AbstractConceptRoleRequestDto> result = new ArrayList<>();
        conceptServices.values()
                .forEach(idmGeneralConceptRoleRequestService ->
                        result.addAll(
                                idmGeneralConceptRoleRequestService.findAllByRoleRequest(id, pageable, permissions)
                        ));
        return result;
    }

    @Override
    public Collection<AbstractConceptRoleRequestDto> findAllByRoleAssignment(UUID identityRoleId) {
        return conceptServices.values().stream().flatMap(idmGeneralConceptRoleRequestService -> {
            final IdmBaseConceptRoleRequestFilter filter = idmGeneralConceptRoleRequestService.getFilter();
            filter.setRoleAssignmentUuid(identityRoleId);
            final Stream<? extends AbstractConceptRoleRequestDto> concepts = idmGeneralConceptRoleRequestService.find(filter, null).stream();
            return concepts;
        }).collect(Collectors.toList());
    }

    @Override
    public List<AbstractConceptRoleRequestDto> getAllByRoleId(UUID roleId) {
        return conceptServices.values().stream().flatMap(idmGeneralConceptRoleRequestService -> {
            final IdmBaseConceptRoleRequestFilter filter = idmGeneralConceptRoleRequestService.getFilter();
            filter.setRoleId(roleId);
            final Stream<? extends AbstractConceptRoleRequestDto> concepts = idmGeneralConceptRoleRequestService.find(filter, null).stream();
            return concepts;
        }).collect(Collectors.toList());
    }

    @Override
    public List<AbstractConceptRoleRequestDto> getAllByRoleRequest(UUID requestId, ConceptRoleRequestOperation operation) {
        Assert.notNull(requestId, "No request id provided");
        final List<AbstractConceptRoleRequestDto> collect = conceptServices.values().stream().flatMap(idmGeneralConceptRoleRequestService -> {
            final IdmBaseConceptRoleRequestFilter filter = idmGeneralConceptRoleRequestService.getFilter();
            filter.setRoleRequestId(requestId);
            filter.setOperation(operation);
            final Stream<? extends AbstractConceptRoleRequestDto> concepts = idmGeneralConceptRoleRequestService.find(filter, null).stream();
            return concepts;
        }).collect(Collectors.toList());
        return collect;
    }

    @Override
    public Class<IdmRequestIdentityRoleFilter> getFilterClass() {
        return IdmRequestIdentityRoleFilter.class;
    }

    @Override
    public IdmRequestIdentityRoleDto checkAccess(IdmRequestIdentityRoleDto dto, BasePermission... permission) {
        return null;
    }


    @Override
    public Class<IdmRequestIdentityRoleDto> getDtoClass() {
        return IdmRequestIdentityRoleDto.class;
    }

    @Override
    public <F2 extends BaseFilter> DtoAdapter<IdmRequestIdentityRoleDto, IdmRequestIdentityRoleDto> getAdapter(F2 originalFilter) {
        return input -> input;
    }

    @Override
    public MultiSourcePagedResource<IdmRequestIdentityRoleDto, IdmRequestIdentityRoleFilter, IdmRequestIdentityRoleFilter, IdmRequestIdentityRoleDto> getMultiResource() {
        List<AdaptableService<IdmRequestIdentityRoleDto, IdmRequestIdentityRoleFilter, IdmRequestIdentityRoleDto>> services = new ArrayList<>();
        conceptServices.values().forEach(services::add);
        return new MultiSourcePagedResource<>(services, modelMapper);
    }

    @Override
    public boolean supports(Class<?> delimiter) {
        return false;
    }


}
