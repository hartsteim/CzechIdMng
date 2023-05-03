package eu.bcvsolutions.idm.core.api.service;

import eu.bcvsolutions.idm.core.api.CoreModule;
import eu.bcvsolutions.idm.core.api.dto.IdmConfigurationDto;
import eu.bcvsolutions.idm.core.api.dto.filter.DataFilter;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizableService;

/**
 * Provides configuration through application.
 * 
 * @author Radek Tomiška 
 * 
 * @see ConfigurationService
 */
public interface IdmConfigurationService extends 
		EventableDtoService<IdmConfigurationDto, DataFilter>, 
		AuthorizableService<IdmConfigurationDto>,
		CodeableService<IdmConfigurationDto>, 
		ConfigurationService {
	
	/**
	 * Key for confidential storage - value will be saved in confidential storage  under this key for configuration as owner.
	 */
	String CONFIDENTIAL_PROPERTY_VALUE = "config:value";
	
	/**
	 * Cache for configuration properties.
	 */
	String CACHE_NAME = IdmCacheManager.getCacheName(CoreModule.MODULE_ID, "configuration-cache");
}
