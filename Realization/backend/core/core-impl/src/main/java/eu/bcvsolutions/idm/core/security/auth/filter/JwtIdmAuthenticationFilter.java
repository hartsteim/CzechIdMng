package eu.bcvsolutions.idm.core.security.auth.filter;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.jwt.Jwt;
import org.springframework.security.jwt.crypto.sign.InvalidSignatureException;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.utils.HttpFilterUtils;
import eu.bcvsolutions.idm.core.security.api.authentication.AuthenticationManager;
import eu.bcvsolutions.idm.core.security.api.domain.IdmJwtAuthentication;
import eu.bcvsolutions.idm.core.security.api.dto.IdmJwtAuthenticationDto;
import eu.bcvsolutions.idm.core.security.api.filter.AbstractAuthenticationFilter;
import eu.bcvsolutions.idm.core.security.service.impl.JwtAuthenticationMapper;
import eu.bcvsolutions.idm.core.security.service.impl.OAuthAuthenticationManager;

/**
 * Internal Idm JWT authentication filter. This filter server the simple
 * purpose of disassembling the request into the JWT token bearer, decoding
 * the bearer and passing it to the {@link AuthenticationManager}.
 * 
 * @author Jan Helbich
 * @author Radek Tomiška
 */
@Order(0)
@Component
public class JwtIdmAuthenticationFilter extends AbstractAuthenticationFilter {
	
	private static final Logger LOG = LoggerFactory.getLogger(JwtIdmAuthenticationFilter.class);

	@Autowired private OAuthAuthenticationManager authenticationManager;
	@Autowired private JwtAuthenticationMapper jwtTokenMapper;
	@Autowired private AuthenticationExceptionContext ctx;
	
	@Override
	public boolean authorize(String token, HttpServletRequest request, HttpServletResponse response) {
		IdmJwtAuthenticationDto claims = null;
		try {
			Optional<Jwt> jwt = HttpFilterUtils.parseToken(token);
			if (!jwt.isPresent()) {
				return false;
			}
			HttpFilterUtils.verifyToken(jwt.get(), jwtTokenMapper.getVerifier());
			// authentication dto from request
			claims = jwtTokenMapper.getClaims(jwt.get());
			// check expiration for token given in header
			// we need to check expiration, before current (automatically prolonged) token is used by mapper
			if (claims.getExpiration() != null && claims.getExpiration().isBefore(ZonedDateTime.now())) {
				throw new ResultCodeException(CoreResultCode.AUTH_EXPIRED);
			}
			// resolve actual authentication from given authentication dto (token is loaded)
			IdmJwtAuthentication authentication = jwtTokenMapper.fromDto(claims);
			// set current authentication dto to context
			ctx.setToken(jwtTokenMapper.toDto(authentication));
			// try to authenticate
			Authentication auth = authenticationManager.authenticate(authentication);
			LOG.debug("User [{}] successfully logged in.", auth.getName());
			return auth.isAuthenticated();
		} catch (ResultCodeException ex) {
			String statusEnum = ex.getError().getError().getStatusEnum();
			if (CoreResultCode.TOKEN_NOT_FOUND.getCode().equals(statusEnum)
					|| CoreResultCode.AUTHORITIES_CHANGED.getCode().equals(statusEnum)
					|| CoreResultCode.AUTH_EXPIRED.getCode().equals(statusEnum)) {
				LOG.warn("Invalid token, reason: [{}]", ex.getMessage());
				ctx.setCodeEx(ex);
				ctx.setToken(claims); // only expired or authorities changed
			} else {
				// publish additional authentication requirement
				throw ex;
			}
		} catch (AuthenticationException ex) {
			LOG.warn("Invalid authentication, reason: [{}]", ex.getMessage());
			ctx.setAuthEx(ex);
		} catch (InvalidSignatureException | IOException | IllegalArgumentException ex) {
			// client sent some rubbish, just log and ignore
			LOG.warn("Invalid IdM auth token received.", ex);
		}
		return false;
	}
	
	@Override
	public String getAuthorizationHeaderName() {
		return JwtAuthenticationMapper.AUTHENTICATION_TOKEN_NAME;
	}

	@Override
	public String getAuthorizationHeaderPrefix() {
		return "";
	}
	
}
