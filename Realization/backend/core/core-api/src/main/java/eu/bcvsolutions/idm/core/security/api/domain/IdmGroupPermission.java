package eu.bcvsolutions.idm.core.security.api.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Aggregate base permission. Name can't contain character '_' - its used for joining to authority name.
 * 
 * @author Radek Tomiška
 */
public enum IdmGroupPermission implements GroupPermission {
	
	APP(IdmBasePermission.ADMIN,// wildcard - system admin has all permissions
			IdmBasePermission.METRICS),
	APPSKIPCAS(IdmBasePermission.ADMIN);


	// String constants could be used in pre / post authotize SpEl expressions
	
	public static final String APP_ADMIN = "APP" + BasePermission.SEPARATOR + "ADMIN"; // big boss
	public static final String APPSKIPCAS_ADMIN = "APPSKIPCAS" + BasePermission.SEPARATOR + "ADMIN"; // user can log without CAS even if CAS is enabled
	public static final String APP_METRICS = "APP" + BasePermission.SEPARATOR + "METRICS";

	private final List<BasePermission> permissions;

	private IdmGroupPermission(BasePermission... permissions) {
		this.permissions = Arrays.asList(permissions);
	}
	
	@Override
	public List<BasePermission> getPermissions() {
		return permissions;
	}
	
	@Override
	public String getName() {
		return name();
	}	
	
	@Override
	public String getModule() {
		// common group permission without module
		return null;
	}
}
