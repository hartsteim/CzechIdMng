package eu.bcvsolutions.idm.core.security.auth.filter;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.google.common.base.Strings;

import eu.bcvsolutions.idm.core.CoreModuleDescriptor;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.security.api.domain.Enabled;
import eu.bcvsolutions.idm.core.security.api.domain.IdmGroupPermission;
import eu.bcvsolutions.idm.core.security.api.dto.LoginDto;
import eu.bcvsolutions.idm.core.security.api.exception.IdentityDisabledException;
import eu.bcvsolutions.idm.core.security.api.exception.IdentityNotFoundException;
import eu.bcvsolutions.idm.core.security.api.exception.IdmAuthenticationException;
import eu.bcvsolutions.idm.core.security.api.exception.TwoFactorAuthenticationRequiredException;
import eu.bcvsolutions.idm.core.security.api.filter.AbstractAuthenticationFilter;
import eu.bcvsolutions.idm.core.security.api.service.GrantedAuthoritiesFactory;
import eu.bcvsolutions.idm.core.security.api.service.JwtAuthenticationService;

/**
 * Authentication filter which enables Single-Sign-On (SSO) to IdM by a specific
 * HTTP header.
 * 
 * The main usage is for AD domain authentication, which will be done with the help
 * of a web server standing in front of IdM. The web server will take care of
 * the Kerberos authentication (e.g. mod_auth_kerb for Apache web server)
 * and will set the REMOTE_USER header to the value login@domain
 * of the successfully authenticated domain user.
 * 
 * The SSO filter is disabled by default and may be enabled by the property
 * {@link ConfigurationService#PROPERTY_ENABLED}.
 * 
 * The SSO authentication is always disabled for super admins - all identities
 * with the permission {@link IdmGroupPermission#APP_ADMIN} - for security reasons. 
 * TODO add permission which could disable SSO logon for other identities.
 * 
 * The order of the filter is greater than other Authentication filters, so processing
 * of the SSO header will be done only if the user isn't authorized by other IdM filters.
 * 
 * @author Alena Peterová
 * @author Radek Tomiška
 *
 */
@Order(50)
@Component(SsoIdmAuthenticationFilter.FILTER_NAME)
@Enabled(module = CoreModuleDescriptor.MODULE_ID)
public class SsoIdmAuthenticationFilter extends AbstractAuthenticationFilter {

	private static final Logger LOG = LoggerFactory.getLogger(SsoIdmAuthenticationFilter.class);
	//
	public static final String FILTER_NAME = "core-sso-authentication-filter";
	public static final String PARAMETER_HEADER_NAME = "header-name";
	public static final String DEFAULT_HEADER_NAME = "REMOTE_USER";
	public static final String PARAMETER_UID_SUFFIXES = "uid-suffixes"; 
	public static final String PARAMETER_FORBIDDEN_UIDS = "forbidden-uids";
	//
	@Autowired private LookupService lookupService;
	@Autowired private JwtAuthenticationService jwtAuthenticationService;
	@Autowired private GrantedAuthoritiesFactory grantedAuthoritiesFactory;
	@Autowired private AuthenticationExceptionContext ctx;

	@Override
	public String getName() {
		return FILTER_NAME;
	}

	protected LookupService getLookupService() {
		return lookupService;
	}

	@Override
	public boolean isDefaultDisabled() {
		return true;
	}
	
	@Override
	public boolean authorize(String token, HttpServletRequest request, HttpServletResponse response) {
		try {
			LOG.debug("Starting SSO filter authorization, value of the SSO header is: [{}]", token);
			if (Strings.isNullOrEmpty(token)) {
				return false;
			}
			// Remove suffix from the token - typically the domain
			String userName = removeUidSuffix(token);
			// Check forbidden uids
			if (isForbiddenUid(userName)) {
				LOG.info("The uid [{}] is forbidden for SSO authentication.", userName);
				return false;
			}
			// Find the corresponding identity
			IdmIdentityDto identity = (IdmIdentityDto) lookupService.lookupDto(IdmIdentityDto.class, userName);
			if (identity == null) {
				throw new IdentityNotFoundException(MessageFormat.format(
						"Check identity can login: The identity [{0}] either doesn't exist or is deleted.",
						userName));
			}
			// identity is valid
			if (identity.isDisabled()) {
				throw new IdentityDisabledException(MessageFormat.format(
						"Check identity can login: The identity [{0}] is disabled.", 
						userName));
			}
			// Check forbidden identity - identity can be found by different attribute than id / username - depends on registered lookup
			if (isForbidden(identity)) {
				LOG.info("The uid [{}] is forbidden for SSO authentication.", userName);
				return false;
			}
			// Check that the identity can authenticate by SSO
			if (isSsoDisabledForIdentity(identity)) {
				LOG.info("The user [{}] can't be authenticated by SSO due to security reasons.", userName);
				return false;
			}
			// Authenticate the user
			LOG.info("User [{}] will be authenticated by SSO.", userName);
			LoginDto loginDto = createLoginDto(userName);
			LoginDto fullLoginDto = jwtAuthenticationService.createJwtAuthenticationAndAuthenticate(
					loginDto,
					identity, 
					CoreModuleDescriptor.MODULE_ID
			);
			//
			return fullLoginDto != null;
			
		} catch (TwoFactorAuthenticationRequiredException ex) { // must change password exception is never thrown
			LOG.warn("Authentication exception raised during SSO authentication: [{}].", ex.getMessage());
			// publish additional authentication requirement
			ctx.setCodeEx(ex);
			throw ex;
		} catch (IdmAuthenticationException e) {
			LOG.warn("Authentication exception raised during SSO authentication: [{}].", e.getMessage());
			ctx.setAuthEx(e);
		} 

		return false;
	}
	
	private boolean isForbidden(IdmIdentityDto identity) {
		return isForbiddenUid(identity.getId().toString()) || isForbiddenUid(identity.getUsername());
	}

	private boolean isForbiddenUid(String uid) {
		List<String> forbiddenUids = getConfigurationService().getValues(getConfigurationPropertyName(PARAMETER_FORBIDDEN_UIDS));
		return !CollectionUtils.isEmpty(forbiddenUids) && forbiddenUids.contains(uid);
	}

	protected String removeUidSuffix(String token) {
		List<String> suffixes = getConfigurationService().getValues(getConfigurationPropertyName(PARAMETER_UID_SUFFIXES));
		if (CollectionUtils.isEmpty(suffixes)) {
			return token;
		}
		for (String suffix : suffixes) {
			if (token.endsWith(suffix)) {
				return token.substring(0,  token.length() - suffix.length());
			}
		}
		return token;
	}

	protected boolean isSsoDisabledForIdentity(IdmIdentityDto identity) {
		// TODO 
		// Create a new permission APP_SSODISABLED, which could disable SSO for "less important" administrators.
		// This requires a change in IdmAuthorizationPolicyService#getGrantedAuthorities
		// because it currently evaluates all APP permissions as APP_ADMIN.
		// Also create a new method IdmAuthorizationPolicyService#getAssignedAuthorities
		// which will return all effective authorities without trimming.
		// Then we don't have to check both APP_ADMIN and APP_SSODISABLED, but only APP_SSODISABLED
		// because APP_ADMIN has all authorities automatically.
		Collection<GrantedAuthority> authorities = grantedAuthoritiesFactory.getGrantedAuthoritiesForIdentity(identity.getId());
		return authorities.stream().anyMatch(action ->
			//action.getAuthority().equals(IdmGroupPermission.APP_SSODISABLED)
			//||
			action.getAuthority().equals(IdmGroupPermission.APP_ADMIN)
		);
	}

	private LoginDto createLoginDto(String userName) {
		LoginDto ldto = new LoginDto();
		ldto.setUsername(userName);
		return ldto;
	}

	@Override
	public String getAuthorizationHeaderName() {
		return getConfigurationValue(PARAMETER_HEADER_NAME, DEFAULT_HEADER_NAME);
	}

	@Override
	public String getAuthorizationHeaderPrefix() {
		return "";
	}
}
