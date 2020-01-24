package eu.bcvsolutions.idm.core.bulk.action.impl.configuration;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.CoreModuleDescriptor;
import eu.bcvsolutions.idm.core.api.bulk.action.AbstractRemoveBulkAction;
import eu.bcvsolutions.idm.core.api.config.domain.IdentityConfiguration;
import eu.bcvsolutions.idm.core.api.dto.IdmConfigurationDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.filter.DataFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.ReadWriteDtoService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmConfiguration;
import eu.bcvsolutions.idm.core.security.api.domain.Enabled;

/**
 * Delete selected configurations
 *
 * @author Ondrej Husnik
 *
 */

@Component("configurationDeleteBulkAction")
@Description("Delete selected configurations")
public class ConfigurationDeleteBulkAction extends AbstractRemoveBulkAction<IdmConfigurationDto, DataFilter> {

	public static final String NAME = "configuration-delete-bulk-action";

	@Autowired
	private IdmConfigurationService configurationService;

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	protected List<String> getAuthoritiesForEntity() {
		return Lists.newArrayList(CoreGroupPermission.CONFIGURATION_DELETE);
	}

	@Override
	public ReadWriteDtoService<IdmConfigurationDto, DataFilter> getService() {
		return configurationService;
	}
}
