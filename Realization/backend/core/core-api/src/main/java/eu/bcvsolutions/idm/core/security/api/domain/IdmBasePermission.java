package eu.bcvsolutions.idm.core.security.api.domain;

public enum IdmBasePermission implements BasePermission {
	
	ADMIN, // wildcard - all base permissions
	COUNT, // Number of entities - count only without details
	AUTOCOMPLETE, // SEARCH => trimmed only for autocomplete
	READ, // SEARCH, GET => full detail
	CREATE, // CREATE
	UPDATE, // UPDATE
	DELETE, // DELETE ONLY
	EXECUTE, METRICS; // RUN, START
	
	@Override
	public String getName() {
		return name();
	}
	
	@Override
	public String getModule() {
		// common base permission without module
		return null;
	}
}
