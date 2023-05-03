package eu.bcvsolutions.idm.core.model.service.impl;

import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmExportImportDto;
import eu.bcvsolutions.idm.core.api.dto.filter.BaseFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.DataFilter;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.service.MultiResourceProvider;
import eu.bcvsolutions.idm.core.api.service.ReadDtoService;
import eu.bcvsolutions.idm.core.api.service.adapter.AdaptableService;
import eu.bcvsolutions.idm.core.model.entity.AbstractRoleAssignment;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Peter Štrunc <github.com/peter-strunc>
 */
public abstract class AbstractAdaptableMultiService<
        D extends AbstractDto, // Destination class of inner services
        F extends DataFilter, // Supported filter
        R // Destination class of this service
        > implements AdaptableService<D, F, R>, MultiResourceProvider<D,F,R> {

    protected final ModelMapper modelMapper;

    private final Collection<? extends ReadDtoService<?,?>> services;

    protected AbstractAdaptableMultiService(ModelMapper modelMapper, Collection<? extends ReadDtoService<?, ?>> services) {
        this.modelMapper = modelMapper;
        this.services = services;
    }

    @Override
    public Set<String> getPermissions(Serializable id) {
        final Stream<String> vals= services.stream()
                .map(idmRoleAssignmentService -> (idmRoleAssignmentService.getPermissions(id)).stream())
                .reduce(Stream::concat).orElse(Stream.empty());
        return vals.collect(Collectors.toSet());
    }

    @Override
    public Page<D> find(Pageable pageable, BasePermission... permission) {
        return find(null, pageable, permission);
    }

    @Override
    public Page<UUID> findIds(Pageable pageable, BasePermission... permission) {
        return findIds(null, pageable, permission);
    }

    @Override
    public Page<D> find(F filter, Pageable pageable, BasePermission[] permission) {
        return getMultiResource().find(filter, pageable, permission);
    }

    @Override
    public Page<UUID> findIds(F filter, Pageable pageable, BasePermission... permission) {
        return getMultiResource().findIds(filter, pageable, permission);
    }

    @Override
    public long count(F filter, BasePermission... permission) {
        return getMultiResource().count(filter, permission);
    }

    @Override
    public boolean isNew(D dto) {
        return dto != null && dto.getId() != null;
    }

    @Override
    public void export(UUID id, IdmExportImportDto batch) {
        throw new UnsupportedOperationException("Aggregate services do not support exporting");
    }

    @Override
    public void siemLog(String action, String status, String targetName, String targetUuid, String subjectName, String subjectUuid, String transactionUuid, String reason) {
        // no need to log, concrete services will handle it
    }

    @Override
    public void siemLog(String action, String status, BaseDto targetDto, BaseDto subjectDto, String transactionUuid, String reason) {
        // no need to log, concrete services will handle it
    }

    @Override
    public Class<? extends BaseEntity> getEntityClass() {
        return AbstractRoleAssignment.class;
    }

    @Override
    public boolean supportsToDtoWithFilter() {
        return true;
    }


    @Override
    public Set<String> getPermissions(D dto) {
        return dto == null ? Collections.emptySet() : getPermissions(dto.getId());
    }

    @Override
    public D get(Serializable id, BasePermission... permission) {
        throw new UnsupportedOperationException("Aggregate services do not support getting by id");
    }

    @Override
    public D get(Serializable id, F context, BasePermission... permission) {
        throw new UnsupportedOperationException("Aggregate services do not support getting by id");
    }
}
