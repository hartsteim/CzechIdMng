package eu.bcvsolutions.idm.acc.service.api;

import java.util.UUID;

import eu.bcvsolutions.idm.acc.AccModuleDescriptor;
import eu.bcvsolutions.idm.acc.dto.AccPasswordFilterEchoItemDto;
import eu.bcvsolutions.idm.acc.dto.AccPasswordFilterRequestDto;
import eu.bcvsolutions.idm.acc.dto.AccUniformPasswordDto;
import eu.bcvsolutions.idm.core.api.dto.IdmPasswordPolicyDto;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.script.ScriptEnabled;
import eu.bcvsolutions.idm.core.api.service.IdmCacheManager;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;

/**
 * Password filter manager that works with ECHOS and {@link AccUniformPasswordDto} and made
 * password changes and validation thought end systems including check via {@link IdmPasswordPolicyDto}
 *
 * @author Ondrej Kopr
 * @since 10.5.0
 *
 */
public interface PasswordFilterManager extends ScriptEnabled {

	String SCRIPT_SYSTEM_PARAMETER = "system";
	String SCRIPT_USERNAME_PARAMETER = "username";
	String SCRIPT_LOG_IDENTIFIER_PARAMETER = "logIdentifier";
	String SCRIPT_SYSTEM_ATTRIBUTE_MAPPING_PARAMETER = "systemAttributeMapping";
	// Parameter for processor properties that contains managed account - performance speedup 
	String MANAGED_ACCOUNTS = "managedAccounts";
	// Parameter for processors that contains system id and all accounts that is for the given system must be skipped
	String EXCLUDED_SYSTEM = "excludedSystem";
	
	String ECHO_CACHE_NAME = IdmCacheManager.getCacheName(AccModuleDescriptor.MODULE_ID, "password-filter-echo-cache");

	/**
	 * Process validation trough password policies stored in IdM.
	 *
	 * @param request
	 * @throws ResultCodeException - exception will be thrown for not valid passwords
	 */
	void validate(AccPasswordFilterRequestDto request);

	/**
	 * Process change password with definition stored in {@link AccUniformPasswordDto}.
	 * Password change is processed without validation!
	 *
	 * @param request
	 */
	void change(AccPasswordFilterRequestDto request);

	/**
	 * Set changed flag and changed date for existing echo. If echo record doesn't exist method will created new record, with only changed flag.
	 * Password parameter isn't required.
	 *
	 * @param accountId
	 * @param password
	 */
	void setEchoForChange(UUID accountId, GuardedString password);

	/**
	 * Create new or replace existing echo record for successfully processed password validation trough IdM.
	 *
	 * @param accountId
	 * @param password
	 * @param success
	 */
	void createEchoForValidation(UUID accountId, GuardedString password, boolean success);

	/**
	 * Create echo record with flag for validation and password change - classic echo record.
	 *
	 * @param accountId
	 * @param password
	 */
	void createEcho(UUID accountId, GuardedString password);

	/**
	 * Clear echo record about successful password change for given account id.
	 * Useful for not executed password changed, that is placed into provisioning queue.
	 *
	 * @param accountId
	 */
	void clearChangedEcho(UUID accountId);

	/**
	 * Return echo record for given accountId.
	 *
	 * @param accountId
	 * @return - null if echo record doesn't exist
	 */
	AccPasswordFilterEchoItemDto getEcho(UUID accountId);
}
