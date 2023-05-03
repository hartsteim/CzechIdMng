package eu.bcvsolutions.idm.core.security.api.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import eu.bcvsolutions.idm.core.api.CoreModule;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.api.dto.IdmTokenDto;
import eu.bcvsolutions.idm.core.api.exception.ForbiddenEntityException;
import eu.bcvsolutions.idm.core.api.service.IdmCacheManager;
import eu.bcvsolutions.idm.core.api.service.IdmTokenService;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;

/**
 * IdM tokens
 * - for authentication (jwt)
 * 
 * @see IdmTokenService
 * @author Radek Tomiška
 * @since 8.2.0
 */
public interface TokenManager {
	
	/**
	 * Token cache - prevent to load token from DB repetitively between requests for the same user, when expiration is not prolonged.
	 * 
	 * @since 10.5.0
	 */
	String TOKEN_CACHE_NAME = IdmCacheManager.getCacheName(CoreModule.MODULE_ID, "token-cache");
	
	/**
	 * Returns currently used token in security context
	 * 
	 * @return
	 */
	IdmTokenDto getCurrentToken();
	
	/**
	 * Returns whether the given token is considered to be new.
	 * 
	 * @param token must never be {@literal null}
	 * @return true -> new token will be created
	 */
	boolean isNew(IdmTokenDto token);

	/**
	 * Save token
	 * 
	 * @param owner
	 * @param token
	 * @param permission permissions to evaluate (AND)
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 * @return
	 */
	IdmTokenDto saveToken(Identifiable owner, IdmTokenDto token, BasePermission... permission);
	
	/**
	 * get token by id
	 * 
	 * @param tokenId
	 * @param permission permissions to evaluate (AND)
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 * @return
	 */
	IdmTokenDto getToken(UUID tokenId, BasePermission... permission);
	
	/**
	 * Owner tokens, permissions are evaluated. No permission - returns empty list.
	 * 
	 * @param owner
	 * @param permission permissions to evaluate (AND)
	 * @return tokens sorted by expiration date asc
	 */
	List<IdmTokenDto> getTokens(Identifiable owner, BasePermission... permission);
	
	/**
	 * Return valid token (not expired, not disabled), otherwise throws exception 
	 * 
	 * @param tokenId token identifier
	 * @param permission permissions to evaluate (AND)
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 * @return
	 */
	IdmTokenDto verifyToken(UUID tokenId, BasePermission... permission);
	
	/**
	 * Delete all tokens 
	 * 
	 * @param owner
	 * @param permission permissions to evaluate (AND)
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 */
	void deleteTokens(Identifiable owner, BasePermission... permission);
	
	/**
	 * Delete given token. 
	 * 
	 * @param tokenId token identifier
	 * @param permission permissions to evaluate (AND)
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 * @since 10.8.0
	 */
	void deleteToken(UUID tokenId, BasePermission... permission);
	
	/**
	 * Disable owner tokens - e.g. logout, when owner state is changed, owner looses some authority.
	 * 
	 * @param owner
	 * @param permission permissions to evaluate (AND)
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 */
	void disableTokens(Identifiable owner, BasePermission... permission);
	
	/**
	 * Disable token - e.g. logout.
	 * 
	 * @param tokenId
	 * @param permission permissions to evaluate (AND)
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 * @return disabled token
	 */
	IdmTokenDto disableToken(UUID tokenId, BasePermission... permission);
	
	/**
	 * Delete tokens with expiration older than given
	 * - can be called from scheduler
	 * 
	 * @see TokenManager
	 * @param tokenType - [optional] - given type only (e.g. cidmst)
	 * @param olderThan - [optional] - with expiration older than given, all otherwise
	 */
	void purgeTokens(String tokenType, ZonedDateTime olderThan);
	
	/**
	 * Returns owner type - owner type has to be entity class - dto class can be given.
	 * Its used as default definition type for given owner type.
	 * 
	 * @param ownerType
	 * @return
	 */
	String getOwnerType(Identifiable owner);
	
	/**
	 * Returns owner type - owner type has to be entity class - dto class can be given.
	 * Its used as default definition type for given owner type.
	 * 
	 * @param ownerType
	 * @return
	 */
	String getOwnerType(Class<? extends Identifiable> ownerType);
}
