package eu.bcvsolutions.idm.core.api.service;

import java.util.UUID;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmPasswordDto;
import eu.bcvsolutions.idm.core.api.dto.PasswordChangeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmPasswordFilter;
import eu.bcvsolutions.idm.core.api.script.ScriptEnabled;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;

/**
 * Service for working with password.
 * Now is password connect only to entity IdmIdentity.
 * 
 * @author Ondrej Kopr <kopr@xyxy.cz>
 *
 */

public interface IdmPasswordService 
		extends ReadWriteDtoService<IdmPasswordDto, IdmPasswordFilter>, ScriptEnabled {
	
	/**
	 * Save password to identity. This method not validate password.
	 * 
	 * @param identity
	 * @param passwordDto
	 * @return
	 */
	IdmPasswordDto save(IdmIdentityDto identity, PasswordChangeDto passwordDto);
	
	/**
	 * Delete password by given identity
	 * 
	 * @param identity
	 */
	void delete(IdmIdentityDto identity);
	
	/**
	 * Return password for given identity
	 * 
	 * @param identityId
	 * @return
	 */
	IdmPasswordDto findOneByIdentity(UUID identityId);

	/**
	 * Return password for given username
	 *
	 * @param username
	 * @return
	 */
	IdmPasswordDto findOneByIdentity(String username);

	/**
	 * Check password matches a passwordToCheck
	 * 
	 * @param passwordToCheck
	 * @param password
	 * @return true if matches
	 */
	boolean checkPassword(GuardedString passwordToCheck, IdmPasswordDto password);
	
	/**
	 * Method generate password and return hash
	 * 
	 * @param password
	 * @return
	 */
	String generateHash(GuardedString password, String salt);
	
	/**
	 * Get salt for identity
	 * 
	 * @param identity
	 * @return
	 */
	String getSalt(IdmIdentityDto identity);

	/**
	 * If this username exists and password is incorrect -> increase count of unsuccessful attempts
	 *
	 * @param username
	 */
	void increaseUnsuccessfulAttempts(String username);

	/**
	 * If this username exists and the password is correct -> save timestamp of login
	 * 
	 * @param username
	 */
	void setLastSuccessfulLogin(String username);
}
