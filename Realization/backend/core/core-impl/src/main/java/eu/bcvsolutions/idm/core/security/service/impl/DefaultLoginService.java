package eu.bcvsolutions.idm.core.security.service.impl;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.CoreModuleDescriptor;
import eu.bcvsolutions.idm.core.api.audit.service.SiemLoggerManager;
import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmPasswordDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTokenDto;
import eu.bcvsolutions.idm.core.api.exception.EntityNotFoundException;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmPasswordService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmJwtAuthentication;
import eu.bcvsolutions.idm.core.security.api.dto.IdmJwtAuthenticationDto;
import eu.bcvsolutions.idm.core.security.api.dto.LoginDto;
import eu.bcvsolutions.idm.core.security.api.exception.IdmAuthenticationException;
import eu.bcvsolutions.idm.core.security.api.service.JwtAuthenticationService;
import eu.bcvsolutions.idm.core.security.api.service.LoginService;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.api.service.TokenManager;

/**
 * Default login service.
 * 
 * @author svandav
 * @author Radek Tomiška
 */
@Service("loginService")
public class DefaultLoginService implements LoginService {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DefaultLoginService.class);

	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private JwtAuthenticationService jwtAuthenticationService;
	@Autowired
	private IdmPasswordService passwordService;
	@Autowired
	private SecurityService securityService;
	@Autowired
	private TokenManager tokenManager;
	@Autowired
	private JwtAuthenticationMapper jwtTokenMapper;
	@Autowired 
	private OAuthAuthenticationManager oAuthAuthenticationManager;
	@Autowired
	private SiemLoggerManager siemLogger;

	@Override
	public LoginDto login(LoginDto loginDto) {
		String username = loginDto.getUsername();
		LOG.info("Identity with username [{}] authenticating.", username);
		
		IdmIdentityDto identity = getValidIdentity(loginDto, true);

		loginDto = jwtAuthenticationService.createJwtAuthenticationAndAuthenticate(
				loginDto, new IdmIdentityDto(identity, identity.getUsername()), loginDto.getAuthenticationModule());
		
		LOG.info("Identity with username [{}] is authenticated", username);
		
		return loginDto;
	}

	@Override
	public LoginDto loginAuthenticatedUser() {
		if (!securityService.isAuthenticated()) {
			throw new IdmAuthenticationException("Not authenticated!");
		}
		
		String username = securityService.getAuthentication().getCurrentUsername();
		
		LOG.info("Identity with username [{}] authenticating", username);
		
		IdmIdentityDto identity = identityService.getByUsername(username);
		// identity doesn't exist
		if (identity == null) {			
			throw new IdmAuthenticationException(MessageFormat.format(
					"Check identity can login: The identity "
					+ "[{0}] either doesn't exist or is deleted.",
					username));
		}
		
		LoginDto loginDto = new LoginDto();
		loginDto.setUsername(username);
		loginDto = jwtAuthenticationService.createJwtAuthenticationAndAuthenticate(
				loginDto, 
				identity,
				CoreModuleDescriptor.MODULE_ID);
		
		LOG.info("Identity with username [{}] is authenticated", username);

		return loginDto;
	}
	
	@Override
	public boolean validate(LoginDto loginDto) {
		return getValidIdentity(loginDto, false) != null;
	}
	
	private IdmIdentityDto getValidIdentity(LoginDto loginDto, boolean propagateException) {
		String username = loginDto.getUsername();
		LOG.info("Identity with username [{}] authenticating", username);
		
		IdmIdentityDto identity = identityService.getByUsername(username);
		// identity exists
		if (identity == null) {
			String validationMessage = MessageFormat.format("Check identity can login: The identity "
					+ "[{0}] either doesn't exist or is deleted.", username);
			if (!propagateException) {
				LOG.debug(validationMessage);
				return null;
			}
			throw new IdmAuthenticationException(validationMessage);
		}
		// identity is valid
		if (identity.isDisabled()) {
			String validationMessage = MessageFormat.format("Check identity can login: The identity [{0}] is disabled.", username);
			if (!propagateException) {
				LOG.debug(validationMessage);
				return null;
			}
			throw new IdmAuthenticationException(validationMessage);
		}
		// GuardedString isn't necessary password is in hash.
		IdmPasswordDto password = passwordService.findOneByIdentity(identity.getId());
		if (password == null) {
			String validationMessage = MessageFormat.format("Identity [{0}] does not have pasword stored in IdM.", username);
			if (!propagateException) {
				LOG.debug(validationMessage);
				return null;
			}
			throw new IdmAuthenticationException(validationMessage);
		}
		// check if password expired
		if (password.getValidTill() != null && password.getValidTill().isBefore(LocalDate.now())) {
			String validationMessage = MessageFormat.format("Password for identity [{0}] is expired.", username);
			if (!propagateException) {
				LOG.debug(validationMessage);
				return null;
			}
			throw new ResultCodeException(CoreResultCode.PASSWORD_EXPIRED);
		}
		// given password is correct
		if (!passwordService.checkPassword(loginDto.getPassword(), password)) {
			String validationMessage = MessageFormat.format("Identity [{0}] password check failed.", username);
			if (!propagateException) {
				LOG.debug(validationMessage);
				return null;
			}
			throw new IdmAuthenticationException(validationMessage);
		}
		//
		return identity;
	}

	@Override
	public void logout() {
		logout(tokenManager.getCurrentToken());
	}
	
	@Override
	public void logout(IdmTokenDto token) {
		jwtAuthenticationService.logout(token);
	}
	
	@Override
	public LoginDto switchUser(IdmIdentityDto identity, BasePermission... permission) {
		Assert.notNull(identity, "Target identity (to switch) is required.");
		String loggedAction = siemLogger.buildAction(SiemLoggerManager.LOGIN_LEVEL_KEY, SiemLoggerManager.SWITCH_SUBLEVEL_KEY);
		String targetUuid = Objects.toString(identity.getId(),"");
		String subjectUsername = securityService.getCurrentUsername();
		String subjectUuid = Objects.toString(securityService.getCurrentId(),"");
		try {
			
			identityService.checkAccess(identity, permission);
			//
			IdmTokenDto currentToken = tokenManager.getCurrentToken();
			ConfigurationMap properties = currentToken.getProperties();
			// Preserve the first original user => switch is available repetitively, but original user is preserved.
			properties.putIfAbsent(JwtAuthenticationMapper.PROPERTY_ORIGINAL_USERNAME, securityService.getCurrentUsername());
			properties.putIfAbsent(JwtAuthenticationMapper.PROPERTY_ORIGINAL_IDENTITY_ID, securityService.getCurrentId());
			currentToken.setProperties(properties);
			IdmTokenDto switchedToken = jwtTokenMapper.createToken(identity, currentToken);
			//
			// login by updated token
			LOG.info("Identity with username [{}] - login as switched user [{}].", 
					properties.get(JwtAuthenticationMapper.PROPERTY_ORIGINAL_USERNAME), 
					identity.getUsername());
			//
			LoginDto login = login(identity, switchedToken);
			siemLogger.log(loggedAction, SiemLoggerManager.SUCCESS_ACTION_STATUS, identity.getUsername(), targetUuid, subjectUsername, subjectUuid, null,  null);
			return login;
		} catch (Exception e) {
			siemLogger.log(loggedAction, SiemLoggerManager.FAILED_ACTION_STATUS, identity.getUsername(), targetUuid, subjectUsername, subjectUuid, null,  e.getMessage());			
			throw e;
		}
	}
	
	@Override
	public LoginDto switchUserLogout() {
		IdmTokenDto currentToken = tokenManager.getCurrentToken();
		ConfigurationMap properties = currentToken.getProperties();
		String originalUsername = properties.getString(JwtAuthenticationMapper.PROPERTY_ORIGINAL_USERNAME);
		UUID originalId = properties.getUuid(JwtAuthenticationMapper.PROPERTY_ORIGINAL_IDENTITY_ID);
		String loggedAction = siemLogger.buildAction(SiemLoggerManager.LOGIN_LEVEL_KEY, SiemLoggerManager.SWITCH_SUBLEVEL_KEY);
		String subjectUsername = securityService.getCurrentUsername();
		String subjectUuid = Objects.toString(securityService.getCurrentId(),"");
		String targetUuid = Objects.toString(originalId,"");
		try {
			//
			if (originalId == null) {
				throw new ResultCodeException(CoreResultCode.NULL_ATTRIBUTE, ImmutableMap.of("attribute", "originalUsername"));
			}
			// change logged token authorities
			IdmIdentityDto identity = identityService.get(originalId);
			if (identity == null) {
				throw new EntityNotFoundException(IdmIdentity.class, originalId);
			}
			//
			// Preserve the first original user => switch is available repetitively, but original user is preserved.
			properties.remove(JwtAuthenticationMapper.PROPERTY_ORIGINAL_USERNAME);
			properties.remove(JwtAuthenticationMapper.PROPERTY_ORIGINAL_IDENTITY_ID);
			currentToken.setProperties(properties);
			IdmTokenDto switchedToken = jwtTokenMapper.createToken(identity, currentToken);
			//
			// login by updated token
			LOG.info("Identity with username [{}] - logout from switched user [{}].", originalUsername, securityService.getCurrentUsername());
			//
			LoginDto login = login(identity, switchedToken);
			siemLogger.log(loggedAction, SiemLoggerManager.SUCCESS_ACTION_STATUS, originalUsername, targetUuid, subjectUsername, subjectUuid, null, null);
			return login;
		} catch (Exception e) {
			siemLogger.log(loggedAction, SiemLoggerManager.FAILED_ACTION_STATUS, originalUsername, targetUuid, subjectUsername, subjectUuid, null, e.getMessage());
			throw e;
		}
	}
	
	private LoginDto login(IdmIdentityDto identity, IdmTokenDto token) {
		IdmJwtAuthentication authentication = jwtTokenMapper.fromDto(token);
		//
		oAuthAuthenticationManager.authenticate(authentication);
		//
		LoginDto loginDto = new LoginDto(identity.getUsername(), null);
		loginDto.setAuthenticationModule(token.getModuleId());
		IdmJwtAuthenticationDto authenticationDto = jwtTokenMapper.toDto(token);
		loginDto.setAuthentication(authenticationDto);
		loginDto.setToken(jwtTokenMapper.writeToken(authenticationDto));
		loginDto.setAuthorities(jwtTokenMapper.getDtoAuthorities(token));
		//
		return loginDto;
	}
}
