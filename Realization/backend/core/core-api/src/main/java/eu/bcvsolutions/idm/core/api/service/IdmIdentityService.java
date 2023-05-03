package eu.bcvsolutions.idm.core.api.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.google.common.annotations.Beta;

import eu.bcvsolutions.idm.core.api.domain.IdentityState;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.PasswordChangeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.event.CoreEvent;
import eu.bcvsolutions.idm.core.api.exception.ForbiddenEntityException;
import eu.bcvsolutions.idm.core.api.script.ScriptEnabled;
import eu.bcvsolutions.idm.core.eav.api.service.FormableDtoService;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizableService;

/**
 * Operations with identities.
 * 
 * @author Radek Tomiška
 *
 */
public interface IdmIdentityService extends 
		FormableDtoService<IdmIdentityDto, IdmIdentityFilter>,
		AuthorizableService<IdmIdentityDto>,
		CodeableService<IdmIdentityDto>,
		ScriptEnabled, NiceLabelable<IdmIdentityDto> {

	/**
	 * Returns identity by given username
	 * @param username
	 * @return
	 */
	IdmIdentityDto getByUsername(String username);

	/**
	 * Changes given identity's password by the event processing. New password property has to be set in event properties.
	 * 
	 * @param passwordChangeEvent
	 * @return
	 * @since 8.1.4 - use {@link #passwordChange(CoreEvent)}
	 */
	List<OperationResult> passwordChange(CoreEvent<IdmIdentityDto> passwordChangeEvent);
	
	/**
	 * Changes given identity's password
	 * 
	 * @param identity
	 * @param passwordChangeDto
	 * @return - change on accounts
	 */
	List<OperationResult> passwordChange(IdmIdentityDto identity, PasswordChangeDto passwordChangeDto);
	
	/**
	 * Find all identities by assigned role. Returns even identities with invalid roles (future valid and expired) and invalid identities.
	 * 
	 * @param roleId
	 * @return List of identities with assigned role
	 * @see #findValidByRole(UUID)
	 */
	List<IdmIdentityDto> findAllByRole(UUID roleId);
	
	/**
	 * Find valid identities by assigned currently valid role. Identities with valid identity roles from valid contracts only.
	 * 
	 * @param roleId
	 * @return List of identities with assigned currently valid role
	 * @see {@link #findAllByRole(UUID)} when you need to return identities with invalid role assigned
	 * @since 7.7.0
	 */
	List<IdmIdentityDto> findValidByRole(UUID roleId);
	

	/**
	 * Find valid identities by assigned currently valid role. Identities with valid identity roles from valid contracts only.
	 * 
	 * @param roleId
	 * @param pageable
	 * @return Page of identities with assigned currently valid role
	 * @see {@link #findAllByRole(UUID)} when you need to return identities with invalid role assigned
	 * @since 8.2.0
	 */
	@Beta
	Page<IdmIdentityDto> findValidByRolePage(UUID roleId, Pageable pageable);
	
	/**
	 * Find all identities by assigned role name. Returns even identities with invalid roles (future valid and expired) and invalid identities.
	 * 
	 * @param roleName
	 * @return List of identities with assigned role
	 */
	List<IdmIdentityDto> findAllByRoleName(String roleName);

	/**
	 * Method finds all identity's managers by identity contract (guarantee or by assigned tree structure).
	 * If no manager is found, then identities wit admin role (by configuration) is returned.
	 * 
	 * @param forIdentity
	 * @param byTreeType [optional] If tree type is given, then only managers defined with this type is returned
	 * @param validContractManagers [optional] filter managers for valid now or in future subordinate contracts (true => valid now or in future contract managers, false => ended contract managers, all otherwise).
	 * @return identity managers
	 * @since 10.3.0
	 */
	List<IdmIdentityDto> findAllManagers(UUID forIdentity, UUID byTreeType, Boolean validContractManagers);
	
	/**
	 * Method finds all identity's managers by identity contract (guarantee or by assigned tree structure).
	 * If no manager is found, then identities wit admin role (by configuration) is returned.
	 * 
	 * @param forIdentity
	 * @param byTreeType If optional tree type is given, then only managers defined with this type is returned
	 * @return identity managers
	 */
	List<IdmIdentityDto> findAllManagers(UUID forIdentity, UUID byTreeType);

	/**
	 * Contains list of identities some identity with given username.
	 * If yes, then return true.
	 * 
	 * @param identities
	 * @param username
	 * @return
	 */
	@Beta
	boolean containsUser(List<IdmIdentityDto> identities, String username);

	/**
	 * Convert given identities to string of user names separate with comma 
	 * 
	 * @param identities
	 * @return
	 */
	@Beta
	String convertIdentitiesToString(List<IdmIdentityDto> identities);

	/**
	 * Find guarantees for given role ID. 
	 * 
	 * @param roleId
	 * @param pageable
	 * @return
	 */
	Page<IdmIdentityDto> findGuaranteesByRoleId(UUID roleId, Pageable pageable);
	
	/**
	 * Method create new IdentityEvent for pre validate password
	 * 
	 * @param passwordChange
	 */
	void validatePassword(PasswordChangeDto passwordChange);

	/**
	 * Enable a given identity manually.
	 * 
	 * @since 7.6.0
	 * @param identityId
	 * @param permission permissions to evaluate
	 * @return the saved DTO
	 * @throws IllegalArgumentException in case the given identity is {@literal null}.
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 */
	IdmIdentityDto enable(UUID identityId, BasePermission... permission);
	
	/**
	 * Disabled a given identity manually.
	 * 
	 * @since 7.6.0
	 * @param identityId
	 * @param permission permissions to evaluate
	 * @return the saved DTO
	 * @throws IllegalArgumentException in case the given identity is {@literal null}.
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 */
	IdmIdentityDto disable(UUID identityId, BasePermission... permission);
	
	/**
	 * Returns state evaluated from current identity contracts. Identity is not changed, just state is evaluated.
	 * - manually disabled identity  remains disabled manually even valid contracts are found
	 * 
	 * @since 7.6.0
	 * @param identityId
	 * @return
	 */
	IdentityState evaluateState(UUID identityId);

}
