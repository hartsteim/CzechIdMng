package eu.bcvsolutions.idm.core.config.web;

import java.util.Set;

import eu.bcvsolutions.idm.core.api.rest.BaseController;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmGroupPermission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.MethodInvokingFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.spel.spi.EvaluationContextExtension;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.RequestContextFilter;

import eu.bcvsolutions.idm.core.api.rest.BaseDtoController;
import eu.bcvsolutions.idm.core.security.api.auth.filter.AuthenticationFilter;
import eu.bcvsolutions.idm.core.security.auth.filter.ExtendExpirationFilter;
import eu.bcvsolutions.idm.core.security.auth.filter.StartUserTransactionFilter;

/**
 * Web security configuration.
 * 
 * @author Radek Tomiška 
 *
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
	
	@Autowired private RoleHierarchy roleHierarchy;

	@Override
    protected void configure(HttpSecurity http) throws Exception {
    	 http.csrf().disable();
    	 http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    	 //
    	 AuthenticationFilter authenticationFilter = authenticationFilter();
    	 Set<RequestMatcher> publicPaths = authenticationFilter.getPublicPathRequestMatchers();
    	 //
    	 http
    	 	.addFilterBefore(requestContextFilter(), BasicAuthenticationFilter.class)
    	 	.addFilterBefore(startUserTransactionFilter(), BasicAuthenticationFilter.class)
    	 	.addFilterAfter(authenticationFilter, BasicAuthenticationFilter.class)
    	 	.addFilterAfter(extendExpirationFilter(), BasicAuthenticationFilter.class)
			.authorizeRequests()
			.expressionHandler(expressionHandler())
			.antMatchers(HttpMethod.OPTIONS).permitAll()
			.requestMatchers(publicPaths.toArray(new RequestMatcher[0])).permitAll()
				 .antMatchers(BaseController.BASE_PATH + "/**").fullyAuthenticated()
				 // securing actuator endpoints
				 .antMatchers("/actuator/**").hasAuthority(IdmGroupPermission.APP_METRICS)
			.anyRequest().permitAll(); // gui could run in application context
    }

	@Override
	public void configure(WebSecurity web) throws Exception {
		web // allow url encoded params
			.httpFirewall(allowUrlEncodedSlashHttpFirewall())
			// public controllers
			.ignoring()
			.antMatchers( //
					BaseDtoController.BASE_PATH, // endpoint with supported services list
					BaseDtoController.BASE_PATH + "/authentication", // login
					BaseDtoController.BASE_PATH + "/authentication/two-factor", // login two factor
					"/error/**",
					BaseDtoController.BASE_PATH + "/doc", // documentation is public
					BaseDtoController.BASE_PATH + "/doc/**"
					);
	}
	
	@Bean
	public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
	    StrictHttpFirewall firewall = new StrictHttpFirewall();
	    firewall.setAllowUrlEncodedSlash(true);
	    firewall.setAllowSemicolon(true);
	    firewall.setAllowUrlEncodedPercent(true);
	    //
	    return firewall;
	}
	
	
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
	
	@Bean
	public AuthenticationFilter authenticationFilter() {
		return new AuthenticationFilter();
	}
	
	@Bean
	public ExtendExpirationFilter extendExpirationFilter() {
		return new ExtendExpirationFilter();
	}
	
	/**
	 * User transaction holder.
	 * 
	 * @return
	 */
	@Bean
	public StartUserTransactionFilter startUserTransactionFilter() {
		return new StartUserTransactionFilter();
	}
	
	/**
	 * Request scope bean will be available in deferred requests.
	 * 
	 * @return
	 */
	@Bean
	public RequestContextFilter requestContextFilter() {
	    return new RequestContextFilter();
	}
	
	/**
	 * Inherit security context from parent thread
	 * 
	 * @return
	 */
	@Bean
	public MethodInvokingFactoryBean methodInvokingFactoryBean() {
	    MethodInvokingFactoryBean methodInvokingFactoryBean = new MethodInvokingFactoryBean();
	    methodInvokingFactoryBean.setTargetClass(SecurityContextHolder.class);
	    methodInvokingFactoryBean.setTargetMethod("setStrategyName");
	    // MODE_INHERITABLETHREADLOCAL mode is not recommended in environment where thread pools are used (old SecurityContext can be reused in next thread using.).
		// Instead that the DelegatingSecurityContextRunnable is used for delegating the SecurityContext to child the thread.
		// Same is applies for TransactionContext. You have to use DelegatingTransactionContextRunnable for delegating to the child thread.
		// Beware, you have to wrap every new Thread to this delegate objects (wrappers).
	    methodInvokingFactoryBean.setArguments(SecurityContextHolder.MODE_THREADLOCAL);
	    return methodInvokingFactoryBean;
	}
	
	/**
	 * Support hasAuthority etc. in search queries
	 * 
	 * @return
	 */
	@Bean
	public EvaluationContextExtension securityExtension() {
		return new EvaluationContextExtension() {
			
			@Override
			public String getExtensionId() {
				return "security";
			}

			@Override
			public SecurityExpressionRoot getRootObject() {
				Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
				if (authentication == null) {
					// not authenticated
					return null;
				}
				SecurityExpressionRoot root = new SecurityExpressionRoot(authentication) {};
				root.setRoleHierarchy(roleHierarchy);
				return root;
			}
		};
	}
	
	/**
	 * Inject role hierarchy to HttpSecurity expressions
	 * 
	 * @return
	 */
	private SecurityExpressionHandler<FilterInvocation> expressionHandler() {
        DefaultWebSecurityExpressionHandler defaultWebSecurityExpressionHandler = new DefaultWebSecurityExpressionHandler();
        defaultWebSecurityExpressionHandler.setRoleHierarchy(roleHierarchy);
        return defaultWebSecurityExpressionHandler;
    }
}
