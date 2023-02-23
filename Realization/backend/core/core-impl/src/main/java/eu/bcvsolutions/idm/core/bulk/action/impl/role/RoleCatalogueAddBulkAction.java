package eu.bcvsolutions.idm.core.bulk.action.impl.role;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.bulk.action.AbstractBulkAction;
import eu.bcvsolutions.idm.core.api.config.domain.RoleConfiguration;
import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.PriorityType;
import eu.bcvsolutions.idm.core.api.domain.TransactionContextHolder;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmEntityStateFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.event.CoreEvent;
import eu.bcvsolutions.idm.core.api.event.CoreEvent.CoreEventType;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.EntityStateManager;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.service.ReadWriteDtoService;
import eu.bcvsolutions.idm.core.eav.api.domain.BaseCodeList;
import eu.bcvsolutions.idm.core.eav.api.domain.BaseFaceType;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.event.RoleEvent;
import eu.bcvsolutions.idm.core.model.event.RoleEvent.RoleEventType;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;

/**
 * Bulk action for adding roles to catalogue.
 * 
 * @author Michal S
 */
@Component(RoleCatalogueAddBulkAction.NAME)
@Description("Bulk action add roles to catologue.")
public class RoleCatalogueAddBulkAction extends AbstractBulkAction<IdmRoleDto, IdmRoleFilter> {

	public static final String NAME = "core-adding-role-catalogue-bulk-action";
	public static final String PROPERTY_ROLE_CATALOGUE = "role-catalogue";
	//
	@Autowired private IdmRoleService roleService;
	@Autowired private IdmRoleCatalogueRoleService roleCatalogueRoleService;

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	protected List<String> getAuthoritiesForEntity() {
		return Lists.newArrayList(CoreGroupPermission.ROLE_CREATE, CoreGroupPermission.ROLE_UPDATE);
	}
	
	@Override
	public ReadWriteDtoService<IdmRoleDto, IdmRoleFilter> getService() {
		return roleService;
	}

	@Override
	protected OperationResult processDto(IdmRoleDto dto) {

		addRoleToCatalogue(dto, getRoleCatalogue()); 
		return null;
	}
	
	/**
     * Add role to catalogue
     * 
     * @param role
     * @param catalogueId
     */
    private void addRoleToCatalogue(IdmRoleDto role, UUID catalogueId) {
	    if (catalogueId == null) {
	    	return;
	    }
	    if (roleIsInCatalogue(role.getId(), catalogueId)) {
	    	return;
	    }
	    IdmRoleCatalogueRoleDto catRoleDto = new IdmRoleCatalogueRoleDto();
	    catRoleDto.setRole(role.getId());
	    catRoleDto.setRoleCatalogue(catalogueId);
//	    LOG.info("Putting role ${catRoleDto.getRole()} into catalogue ${catRoleDto.getRoleCatalogue()}");
	    roleCatalogueRoleService.save(catRoleDto);
    }
    
    /**
     * check if the role in the catalogue
     * 
     * @param roleId
     * @param catalogueId
     * @return
     */
    private boolean roleIsInCatalogue(UUID roleId, UUID catalogueId) {
	    IdmRoleCatalogueRoleFilter filter = new IdmRoleCatalogueRoleFilter();
	    filter.setRoleId(roleId);
	    filter.setRoleCatalogueId(catalogueId);
	    return roleCatalogueRoleService.count(filter, null) > 0;
    }
	
	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> formAttributes = super.getFormAttributes();
		formAttributes.add(getRoleCatalogueAttribute());
		return formAttributes;
	}
	
	protected IdmFormAttributeDto getRoleCatalogueAttribute() {
		IdmFormAttributeDto roleCatalogue = new IdmFormAttributeDto(
				PROPERTY_ROLE_CATALOGUE, 
				PROPERTY_ROLE_CATALOGUE, 
				PersistentType.UUID);
		roleCatalogue.setFaceType(BaseFaceType.ROLE_CATALOGUE_SELECT);
		return roleCatalogue;
	}
	
	protected UUID getRoleCatalogue() {
		return getParameterConverter().toUuid(getProperties(), PROPERTY_ROLE_CATALOGUE);
	}

	@Override
	public int getOrder() {
		return super.getOrder() + 500;
	}
	

}
