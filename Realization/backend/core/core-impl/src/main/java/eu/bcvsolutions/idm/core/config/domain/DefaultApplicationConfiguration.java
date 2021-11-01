package eu.bcvsolutions.idm.core.config.domain;

import java.io.IOException;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.bcvsolutions.idm.core.api.config.domain.AbstractConfiguration;
import eu.bcvsolutions.idm.core.api.config.domain.ApplicationConfiguration;
import eu.bcvsolutions.idm.core.api.dto.theme.ThemeDto;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;

/**
 * Common application configuration.
 * 
 * @author Radek Tomiška
 * @since 11.1.0
 */
public class DefaultApplicationConfiguration extends AbstractConfiguration implements ApplicationConfiguration {	
	
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DefaultApplicationConfiguration.class);
	//
	private String backendUrl = null;
	//
	@Autowired private ObjectMapper mapper;
	
	@Override
	public String getStage() {
		String stage = getConfigurationService().getValue(PROPERTY_STAGE);
		if (StringUtils.isBlank(stage)) {
			LOG.debug("Stage by property [{}] is not configured, return default [{}]", PROPERTY_STAGE, DEFAULT_STAGE);
			return DEFAULT_STAGE;
		}
		//
		return stage;
	}
	
	@Override
	public boolean isDevelopment() {
		return STAGE_DEVELOPMENT.equalsIgnoreCase(getStage());
	}
	
	@Override
	public boolean isProduction() {
		return STAGE_PRODUCTION.equalsIgnoreCase(getStage());
	}
	
	@Override
	public String getFrontendUrl() {
		return getConfigurationService().getFrontendUrl("");
	}
	
	@Override
	public String getBackendUrl(HttpServletRequest request) {
		String configuredBackendUrl = getConfigurationService().getValue(PROPERTY_BACKEND_URL);
		if (StringUtils.isNotBlank(configuredBackendUrl)) {
			return configuredBackendUrl;
		}
		// from cache
		if (StringUtils.isNotBlank(backendUrl)) {
			return backendUrl;
		}
		// try to resolve from given request
		if (request == null) {
			return null;
		}
		//
		backendUrl = ServletUriComponentsBuilder
				.fromRequest(request)
				.replacePath(request.getContextPath())
				.replaceQuery(null)
				.build()
				.toUriString();
		//
		return backendUrl;
	}
	
	@Override
	public UUID getApplicationLogoId() {
		return DtoUtils.toUuid(getConfigurationService().getValue(PROPERTY_APPLICATION_LOGO));
	}
	
	@Override
	public ThemeDto getApplicationTheme() {
		String themeJson = getConfigurationService().getValue(PROPERTY_APPLICATION_THEME);
		if (StringUtils.isBlank(themeJson)) {
			return null; // ~ not configured
		}
		//
		try {
			return mapper.readValue(themeJson, ThemeDto.class);
		} catch (IOException ex) {
			LOG.warn("Application theme is wrongly configured. Fix configured application theme [{}], default theme will be used till then.",
					PROPERTY_APPLICATION_THEME, ex);
			return null;
		}
	}
}
