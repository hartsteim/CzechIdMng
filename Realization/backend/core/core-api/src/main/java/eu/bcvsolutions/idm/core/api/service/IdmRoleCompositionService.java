package eu.bcvsolutions.idm.core.api.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import eu.bcvsolutions.idm.core.api.CoreModule;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCompositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCompositionFilter;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.exception.ForbiddenEntityException;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizableService;

/**
 * Role composition (~ business roles).
 * 
 * @author Radek Tomiška
 * @since 9.0.0
 */
public interface IdmRoleCompositionService extends
		EventableDtoService<IdmRoleCompositionDto, IdmRoleCompositionFilter>,
		AuthorizableService<IdmRoleCompositionDto> {
	
	/**
	 * Sub roles cache.
	 * 
	 * @since 10.6.0
	 */
	String ALL_SUB_ROLES_CACHE_NAME = IdmCacheManager.getCacheName(CoreModule.MODULE_ID, "all-sub-roles");
	
	/**
	 * Return list of sub roles (only one level in depth)
	 * of role given by its role ID. Returning available sub roles by given permissions (AND).
	 * 
	 * @param superiorId
	 * @param permission permissions to evaluate (AND)
	 * @return
	 */
	List<IdmRoleCompositionDto> findDirectSubRoles(UUID superiorId, BasePermission... permission);
	
	/**
	 * Return list of all sub roles (all level in depth).
	 * of role given by its role ID. Returning available sub roles by given permissions (AND).
	 * 
	 * @param superiorId
	 * @param permission permissions to evaluate (AND)
	 * @return
	 * @since 9.4.0
	 */
	List<IdmRoleCompositionDto> findAllSubRoles(UUID superiorId, BasePermission... permission);
	
	/**
	 * Returns all superior roles for given sub role. Superior roles are sorted from sub to upper superior roles.
	 * 
	 * @param subId
	 * @param permission
	 * @return
	 */
	List<IdmRoleCompositionDto> findAllSuperiorRoles(UUID subId, BasePermission... permission);	
	
	/**
	 * Assign (create) identity roles for given direct identity role sub roles.
	 * Sub roles will have the same metadata - contract, validFrom, validTill and direct identity role will be filled by given.
	 * 
	 * @param directRoleEvent event is needed for skip already processed identity roles (prevent cycles)
	 * @param permission permissions to evaluate (AND)
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 * @deprecated @since 10.6.0 business roles are processed by standard role request => prevent to use this method
	 */
	@Deprecated
	void assignSubRoles(EntityEvent<IdmIdentityRoleDto> event, BasePermission... permission);
	
	/**
	 * Assign (create) identity roles for given direct identity role sub roles.
	 * Sub roles will have the same metadata - contract, validFrom, validTill and direct identity role will be filled by given.
	 * 
	 * @param directRoleEvent event is needed for skip already processed identity roles (prevent cycles)
	 * @param roleCompositionId [optional] assign roles by this composition only. All configured compositions are processed otherwise.
	 * @param permission permissions to evaluate (AND)
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 * @since 9.7.15
	 * @deprecated @since 10.6.0 business roles are processed by standard role request => prevent to use this method
	 */
	@Deprecated
	void assignSubRoles(EntityEvent<IdmIdentityRoleDto> event, UUID roleCompositionId, BasePermission... permission);
	
	/**
	 * Remove identity roles assigned (created) by given direct identity role sub roles.
	 * 
	 * @param directRole
	 * @param permission permissions to evaluate (AND)
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 * @deprecated @since 10.6.0 business roles are processed by standard role request => prevent to use this method
	 */
	@Deprecated
	void removeSubRoles(EntityEvent<IdmIdentityRoleDto> event, BasePermission... permission);
	
	/**
	 * Updates metadata (contract, validFrom, validTill) on sub roles assigned (created) by given direct identity role
	 * 
	 * @param event
	 * @param permission permissions to evaluate (AND)
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 * @deprecated @since 10.6.0 business roles are processed by standard role request => prevent to use this method
	 */
	@Deprecated
	void updateSubRoles(EntityEvent<IdmIdentityRoleDto> event, BasePermission... permission);
	
	/**
	 * Returns all roles used by given compositions (used as superior or sub). Use {@link #resolveDistinctRoles(List)} is dtos should be returned.
	 * 
	 * @param compositions
	 * @return
	 * @see #resolveDistinctRoles(List)
	 */
	Set<UUID> getDistinctRoles(List<IdmRoleCompositionDto> compositions);
	
	/**
	 * Returns all roles used by given compositions (used as superior or sub).
	 * 
	 * @param compositions - fully loaded role composition with embedded objects.
	 * @return
	 * @throws IllegalArrgumentException if role composition's embedded object doesn't contain referenced superior and sub role. 
	 */
	Set<IdmRoleDto> resolveDistinctRoles(List<IdmRoleCompositionDto> compositions);
	
}
