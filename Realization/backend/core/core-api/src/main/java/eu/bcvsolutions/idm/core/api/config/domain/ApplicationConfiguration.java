package eu.bcvsolutions.idm.core.api.config.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.multipart.MultipartFile;

import eu.bcvsolutions.idm.core.api.AppModule;
import eu.bcvsolutions.idm.core.api.dto.theme.ThemeDto;
import eu.bcvsolutions.idm.core.api.exception.ForbiddenEntityException;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.Configurable;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;

/**
 * Common application configuration.
 * 
 * @author Radek Tomiška
 * @since 11.1.0
 */
public interface ApplicationConfiguration extends Configurable {
	
	String STAGE_PRODUCTION = "production";
	String STAGE_DEVELOPMENT = "development";
	String STAGE_TEST = "test";
	
	/**
	 * Application stage.
	 */
	String PROPERTY_STAGE = ConfigurationService.IDM_PUBLIC_PROPERTY_PREFIX + "app.stage";
	String DEFAULT_STAGE = STAGE_PRODUCTION;
	
	/**
	 * Show logout content (~ page) with message, after user is logged out.
	 * 
	 * Default: false => login content will be shown automatically (~ backward compatible)
	 * 
	 * @since 12.0.0
	 */
	String PROPERTY_SHOW_LOGOUT_CONTENT = ConfigurationService.IDM_PUBLIC_PROPERTY_PREFIX + "app.show.logout.content";
	boolean DEFAULT_SHOW_LOGOUT_CONTENT = false;
	
	/**
	 * Frontend server url. 
	 * E.g. http://localhost:3000
	 * Default: The first 'idm.pub.security.allowed-origins' configured value is used (~ backward compatible).
	 * Use {@link ConfigurationService#getFrontendUrl(String)} to append path
	 * 
	 * @since 12.0.0
	 */
	String PROPERTY_FRONTEND_URL= ConfigurationService.IDM_PUBLIC_PROPERTY_PREFIX + "app.frontend.url";
	
	/**
	 * Backend server url. 
	 * E.g. http://localhost:8080/idm
	 * Default: Url is resolved dynamically from current servlet request.
	 * 
	 * @since 12.0.0
	 */
	String PROPERTY_BACKEND_URL = ConfigurationService.IDM_PUBLIC_PROPERTY_PREFIX + "app.backend.url";
	
	/**
	 * Application logo (attachment identifier).
	 * 
	 * @since 12.0.0
	 */
	String PROPERTY_APPLICATION_LOGO = ConfigurationService.IDM_PUBLIC_PROPERTY_PREFIX + "app.show.logo";
	
	/**
	 * Application theme.
	 * 
	 * @since 12.0.0
	 */
	String PROPERTY_APPLICATION_THEME = ConfigurationService.IDM_PUBLIC_PROPERTY_PREFIX + "app.show.theme";
	
	/**
	 * Footer help link url.
	 * 
	 * @since 12.0.0
	 */
	String PROPERTY_SHOW_FOOTER_HELP_LINK = ConfigurationService.IDM_PUBLIC_PROPERTY_PREFIX + "app.show.footer.help.link";
	String DEFAULT_SHOW_FOOTER_HELP_LINK = "https://wiki.czechidm.com/start";
	
	/**
	 * Footer service desk link url.
	 * 
	 * @since 12.0.0
	 */
	String PROPERTY_SHOW_FOOTER_SERVICEDESK_LINK = ConfigurationService.IDM_PUBLIC_PROPERTY_PREFIX + "app.show.footer.serviceDesk.link";
	String DEFAULT_SHOW_FOOTER_SERVICEDESK_LINK = "https://redmine.czechidm.com/projects/czechidmng";
	
	@Override
	default String getConfigurableType() {
		return AppModule.MODULE_ID;
	}
	
	@Override
	default String getName() {
		return AppModule.MODULE_ID;
	}
	
	@Override
	default String getConfigurationPrefix() {
		return ConfigurationService.IDM_PUBLIC_PROPERTY_PREFIX
				+ getName();
	}
	
	@Override
	default boolean isDisableable() {
		return false;
	}
	
	@Override
	default public boolean isSecured() {
		return false;
	}
	
	@Override
	default List<String> getPropertyNames() {
		List<String> properties = new ArrayList<>(); // we are not using superclass properties - enable and order does not make a sense here
		properties.add(getPropertyName(PROPERTY_STAGE));
		properties.add(getPropertyName(PROPERTY_SHOW_LOGOUT_CONTENT));
		properties.add(getPropertyName(PROPERTY_FRONTEND_URL));
		properties.add(getPropertyName(PROPERTY_BACKEND_URL));
		properties.add(getPropertyName(PROPERTY_APPLICATION_LOGO));
		properties.add(getPropertyName(PROPERTY_APPLICATION_THEME));
		properties.add(getPropertyName(PROPERTY_SHOW_FOOTER_HELP_LINK));
		properties.add(getPropertyName(PROPERTY_SHOW_FOOTER_SERVICEDESK_LINK));
		//
		return properties;
	}
	
	/**
	 * Application stage.
	 * 
	 * @return configured application stage, or {@link #STAGE_PRODUCTION} as default
	 */
	String getStage();
	
	/**
	 * If application running in development stage.
	 * 
	 * @return true - development stage
	 */
	boolean isDevelopment();
	
	/**
	 * If application running in production stage. Production stage is used as default.
	 * 
	 * @return true - production stage
	 */
	boolean isProduction();

	/**
	 * Frontend server url. 
	 * E.g. http://localhost:3000
	 * Default: The first 'idm.pub.security.allowed-origins' configured value is used (~ backward compatible).
	 * Use {@link ConfigurationService#getFrontendUrl(String)} to append path
	 * 
	 * @return frontend url entry point
	 * @since 12.0.0
	 */
	String getFrontendUrl();

	/**
	 * Backend server url. 
	 * E.g. http://localhost:8080/idm
	 * Default: Url is resolved dynamically from current servlet request.
	 * 
	 * @param request
	 * @return backend url entry point
	 * @since 12.0.0
	 */
	String getBackendUrl(HttpServletRequest request);
	
	/**
	 * Application logo (attachment identifier).
	 * 
	 * @return attachment identifier
	 * @since 12.0.0
	 */
	UUID getApplicationLogoId();
	
	/**
	 * Upload new application logo version.
	 * 
	 * @param data - one of image/* content type is required.
	 * @param fileName Original file name 
	 * @param permission permissions to evaluate (AND)
	 * @return
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 * @throws ResultCodeException if content type is different than image (one of  image/*)
	 * @since 12.0.0
	 */
	UUID uploadApplicationLogo(MultipartFile data, String fileName, BasePermission... permission);
	
	/**
	 * Delete application logo (all versions).
	 * 
	 * @param permission permissions to evaluate (AND)
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 * @since 12.0.0
	 */
	void deleteApplicationLogo(BasePermission... permission);
	
	/**
	 * Configured application theme.
	 * 
	 * @return ~JSON theme
	 * @since 12.0.0
	 */
	ThemeDto getApplicationTheme();
}
