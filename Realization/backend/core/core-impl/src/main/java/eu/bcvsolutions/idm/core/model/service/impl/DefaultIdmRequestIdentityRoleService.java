package eu.bcvsolutions.idm.core.model.service.impl;

import eu.bcvsolutions.idm.core.api.domain.RoleRequestedByType;
import eu.bcvsolutions.idm.core.api.dto.AbstractRoleAssignmentDto;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRequestIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.filter.BaseFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRequestIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestManager;
import eu.bcvsolutions.idm.core.api.service.IdmRequestIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleAssignmentManager;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleSystemService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.service.adapter.AdaptableService;
import eu.bcvsolutions.idm.core.model.entity.AbstractConceptRoleRequest_;
import eu.bcvsolutions.idm.core.model.service.util.MultiSourcePagedResource;
import eu.bcvsolutions.idm.core.rest.AbstractBaseDtoService;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowProcessInstanceService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation.ADD;

/**
 * Default implementation of service for search and processing changes in assigned identity roles
 *
 * @author Vít Švanda
 */
@Service("requestIdentityRoleService")
public class DefaultIdmRequestIdentityRoleService extends
			AbstractBaseDtoService<IdmRequestIdentityRoleDto, IdmRequestIdentityRoleFilter>
		implements IdmRequestIdentityRoleService {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory
			.getLogger(DefaultIdmRequestIdentityRoleService.class);

	@Autowired
	private IdmRoleRequestService roleRequestService;
	@Autowired
	private ModelMapper modelMapper;
	@Autowired
	IdmRoleAssignmentManager roleAssignmentManager;
	@Autowired
	IdmConceptRoleRequestManager conceptRoleRequestManager;


	@Override
	public IdmRequestIdentityRoleDto get(Serializable id, BasePermission... permission) {
		return conceptRoleRequestManager.get(id, permission);
	}

	/**
	 * This method returns both {@link eu.bcvsolutions.idm.core.api.dto.AbstractConceptRoleRequestDto} and {@link AbstractRoleAssignmentDto} paged and filtered
	 * using given {@link IdmRequestIdentityRoleFilter} and {@link Pageable}.
	 *
	 * @param filter
	 * @param pageable
	 * @param permission base permissions to evaluate  (AND / OR by {@link eu.bcvsolutions.idm.core.api.dto.filter.PermissionContext})
	 * @return
	 */
	@Override
	public Page<IdmRequestIdentityRoleDto> find(IdmRequestIdentityRoleFilter filter, Pageable pageable, BasePermission... permission) {
		LOG.debug(MessageFormat.format("Find idm-request-identity-roles by filter [{0}] ", filter));
		Assert.notNull(filter, "Filter is required.");
		IdmRequestIdentityRoleFilter copyFilter = modelMapper.map(filter, IdmRequestIdentityRoleFilter.class);

		if (pageable == null) {
			// Page is null, so we set page to max value
			pageable = PageRequest.of(0, Integer.MAX_VALUE);
		}
		
		// If is true, then we want to return only concepts (not assigned roles)
		boolean returnOnlyChanges = copyFilter.isOnlyChanges();

		if (copyFilter.getRoleRequestId() != null) {
			if (!returnOnlyChanges) {
				// We want to load only new added roles
				copyFilter.setOperation(ADD);
				// We don`t want load ADD concepts with filled identityRoleId (such concepts were already executed )
				copyFilter.setIdentityRoleIsNull(true);
			}
		}
		copyFilter.setAddPermissions(true);

		final MultiSourcePagedResource.Builder<IdmRequestIdentityRoleDto,IdmRequestIdentityRoleFilter, IdmRequestIdentityRoleFilter, IdmRequestIdentityRoleDto> builder = new MultiSourcePagedResource.Builder<>();

		if (!filter.isOnlyAssignments()) {
			builder.addResource(conceptRoleRequestManager.getMultiResource());
		}
		if (shouldLoadAssignedRoles(copyFilter, returnOnlyChanges)) {
			builder.addResource(roleAssignmentManager.getMultiResource());
		}
		builder.setModelMapper(modelMapper);

		LOG.debug(MessageFormat.format("Find idm-request-identity-roles by multiresource [{0}] ", builder));
		return builder.build().find(copyFilter, pageable, permission);
	}

	private static boolean shouldLoadAssignedRoles(IdmRequestIdentityRoleFilter filter, boolean returnOnlyChanges) {
		return filter.isLoadRoleAssignments() || (!returnOnlyChanges && (filter.getIdentity() != null || filter.isOnlyAssignments()));
	}

	@Override
	@Transactional
	public IdmRequestIdentityRoleDto save(IdmRequestIdentityRoleDto dto, BasePermission... permission) {
		LOG.debug(MessageFormat.format("Save idm-request-identity-role [{0}] ", dto));
		Assert.notNull(dto, "DTO is required.");
		if (dto.getRoleRequest() == null || roleRequestService.get(dto.getRoleRequest()) == null) {
			final IdmRoleRequestDto request = roleRequestService.createRequest(dto);
			dto.setRoleRequest(request.getId());
			dto.getEmbedded().put(AbstractConceptRoleRequest_.ROLE_REQUEST, request);
		}
		//
		return conceptRoleRequestManager.getServiceForConcept(dto.getAssignmentType()).saveRequestRole(dto, permission);
	}

	@Override
	@Transactional
	public IdmRequestIdentityRoleDto deleteRequestIdentityRole(IdmRequestIdentityRoleDto dto, BasePermission... permission) {
		LOG.debug(MessageFormat.format("Delete idm-request-identity-role [{0}] ", dto));
		Assert.notNull(dto, "DTO cannot be null!");
		Assert.notNull(dto.getId(), "ID of request-identity-role DTO cannot be null!");

		if (dto.getRoleRequest() == null) {
			final IdmRoleRequestDto request = roleRequestService.createRequest(dto);
			dto.setRoleRequest(request.getId());
			dto.getEmbedded().put(AbstractConceptRoleRequest_.ROLE_REQUEST, request);
		}
		//
		return conceptRoleRequestManager.getServiceForConcept(dto.getAssignmentType()).deleteRequestRole(dto, permission);
	}
	
	/**
	 * Not supported, use deleteRequestIdentityRole!
	 */
	@Override
	@Deprecated
	public void delete(IdmRequestIdentityRoleDto dto, BasePermission... permission) {
		throw new UnsupportedOperationException();
	}

}
