package eu.bcvsolutions.idm.core.api.config.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.service.Configurable;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;

/**
 * Configuration for roles
 * 
 * @author Radek Tomiška
 *
 */
public interface RoleConfiguration extends Configurable {
	
	/**
	 * Default user role - permissions configured for the default role are automatically assigned to every logged identity.
	 * Business roles are NOT evaluated
	 */
	String PROPERTY_DEFAULT_ROLE = ConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + "core.role.default";
	String DEFAULT_DEFAULT_ROLE = "userRole";
	
	/**
	 * Admin role - e.g. used as fallback, when no approver in wf is defined.
	 */
	String PROPERTY_ADMIN_ROLE = ConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + "core.role.admin";
	String DEFAULT_ADMIN_ROLE = "superAdminRole";
	String PROPERTY_APPROVE_ROLE_CHANGE_ROLE =  ConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + "core.wf.approval.role-change.role";
	
	/**
	 * If is true, then role-request description will be show on the detail.
	 * 
	 * Description will hidden if this property will be false and role request
	 * doesn't contains any value in description (can be filled during the approval
	 * process)
	 *
	 * Default is true.
	 *
	 */
	String PROPERTY_SHOW_ROLE_REQUEST_DESCRIPTION =  ConfigurationService.IDM_PUBLIC_PROPERTY_PREFIX + "app.show.roleRequest.description";
	
	/**
	 * Show role environment in frontend application for roles (table, role detail, niceLabel, info components, role select).
	 */
	String PROPERTY_SHOW_ENVIRONMENT = ConfigurationService.IDM_PUBLIC_PROPERTY_PREFIX + "app.show.environment";
	boolean SHOW_ENVIRONMENT = true;
	
	/**
	 * Show role baseCode in frontend application for roles (table, role detail, niceLabel, info components, role select).
	 * 
	 * @since 10.4.1
	 */
	String PROPERTY_SHOW_BASE_CODE =  ConfigurationService.IDM_PUBLIC_PROPERTY_PREFIX + "app.show.role.baseCode";
	boolean DEFAULT_SHOW_BASE_CODE = true;
	
	/**
	 * Separator for the suffix with environment used in role code.
	 * Look out: when separator is changed, then all roles should be updated (manually from ui, by scripted LRT or by change script).
	 */
	String PROPERTY_CODE_ENVIRONMENT_SEPARATOR = ConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX + "core.role.codeEnvironmentSeperator";
	String DEFAULT_CODE_ENVIRONMENT_SEPARATOR = "|";
	
	@Override
	default String getConfigurableType() {
		return "role";
	}
	
	@Override
	default boolean isDisableable() {
		return false;
	}
	
	@Override
	default public boolean isSecured() {
		return true;
	}
	
	@Override
	default List<String> getPropertyNames() {
		List<String> properties = new ArrayList<>(); // we are not using superclass properties - enable and order does not make a sense here
		properties.add(getPropertyName(PROPERTY_DEFAULT_ROLE));
		properties.add(getPropertyName(PROPERTY_ADMIN_ROLE));
		properties.add(getPropertyName(PROPERTY_APPROVE_ROLE_CHANGE_ROLE));		
		properties.add(getPropertyName(PROPERTY_SHOW_ROLE_REQUEST_DESCRIPTION));
		properties.add(getPropertyName(PROPERTY_SHOW_ENVIRONMENT));
		properties.add(getPropertyName(PROPERTY_SHOW_BASE_CODE));
		properties.add(getPropertyName(PROPERTY_CODE_ENVIRONMENT_SEPARATOR));
		return properties;
	}
	
	/**
	 * Returns default role identifier
	 * 
	 * @return
	 */
	UUID getDefaultRoleId();
	
	/**
	 * Returns default role
	 * 
	 * @return
	 */
	IdmRoleDto getDefaultRole();
	
	/**
	 * Returns admin role identifier
	 * 
	 * @return
	 */
	UUID getAdminRoleId();
	
	/**
	 * Returns admin role
	 * 
	 * @return
	 */
	IdmRoleDto getAdminRole();

	/**
	 * Return role for approve change of role
	 * If is not defined, then admin role will be used.
	 * 
	 * @return
	 */
	IdmRoleDto getRoleForApproveChangeOfRole();
	
	/**
	 * Separator for the suffix with environment used in role code.
	 * 
	 * @return
	 */
	String getCodeEnvironmentSeperator();
}
