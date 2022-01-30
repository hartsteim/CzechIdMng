package eu.bcvsolutions.idm.core.api.domain;

import org.springframework.http.HttpStatus;

import eu.bcvsolutions.idm.core.notification.api.domain.NotificationLevel;

/**
 * Enum class for formatting response messages (mainly errors). 
 * Every enum contains a string message and corresponding https HttpStatus code.
 * 
 * Used http codes:
 * - 2xx - success
 * - 4xx - client errors (validations, conflicts ...)
 * - 5xx - server errors
 * 
 * ImmutableMap can be used for code parameters - linked map is needed for parameter ordering.
 * Lookout - ImmutableMap parameter values cannot be {@code null}!
 * 
 * @author Radek Tomiška
 */
public enum CoreResultCode implements ResultCode {
	//
	// 2xx
	OK(HttpStatus.OK, "ok"),
	ACCEPTED(HttpStatus.ACCEPTED, "	"),
	DELETED(HttpStatus.ACCEPTED, "Request to delete content accepted."),
	DRY_RUN(HttpStatus.NO_CONTENT, "Dry run mode"),
	DIRTY_STATE(HttpStatus.CREATED, "For entity id [%s] was set dirty state flag."),
	//
	// Commons 4xx
	BAD_REQUEST(HttpStatus.BAD_REQUEST, "The value is wrong!"),
	BAD_VALUE(HttpStatus.BAD_REQUEST, "The value %s is wrong!"),
	BAD_UUID(HttpStatus.BAD_REQUEST, "The value %s is not uuid!"),
	CODE_CONFLICT(HttpStatus.CONFLICT, "Record with given code already exists, by constraint [%s]."),
	NAME_CONFLICT(HttpStatus.CONFLICT, "Record with for given name already exists, by constraint [%s]"),
	CONFLICT(HttpStatus.CONFLICT, "%s"),
	NULL_ATTRIBUTE(HttpStatus.BAD_REQUEST, "Attribute '%s' is NULL."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "%s not found."),
	ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "Entity type [%s] with id [%s] not found.", NotificationLevel.INFO),
	CONTENT_DELETED(HttpStatus.CONFLICT, "Content [%s] with type [%s] was deleted. Operation cannot be executed and will be canceled."),
	WF_WARNING(HttpStatus.BAD_REQUEST, "Warning occured during workflow execution: %s"),
	WF_TASK_FILTER_INVOLVED_ONLY(HttpStatus.BAD_REQUEST, "Task filter 'onlyInvolved' cannot be set to FALSE via REST!"),
	BAD_FILTER(HttpStatus.BAD_REQUEST, "The filter is wrong!"),
	UNMODIFIABLE_ATTRIBUTE_CHANGE(HttpStatus.BAD_REQUEST, "Attribute %s for class %s can't be changed!"),
	UNMODIFIABLE_DELETE_FAILED(HttpStatus.BAD_REQUEST, "Unmodifiable record [%s] can't be deleted!"),
	OPTIMISTIC_LOCK_ERROR(HttpStatus.CONFLICT, "Record was modified with the different process or identity. Try to reload the record and then retry the operation."),
	// http
	ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "The given endpoint doesn't exist!"),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method is not allowed!"),
	// auth errors
	AUTH_FAILED(HttpStatus.UNAUTHORIZED, "Authentication failed - bad credentials."),
	AUTH_BLOCKED(HttpStatus.UNAUTHORIZED, "Authentication failed - login for identity [%s] is blocked due [%s]. Seconds from now [%s]."),
	AUTH_EXPIRED(HttpStatus.UNAUTHORIZED, "Authentication expired.", NotificationLevel.INFO),
	CAS_TICKET_VALIDATION_FAILED(HttpStatus.UNAUTHORIZED, "Authentication failed - CAS ticket validation failed."),
	CAS_LOGIN_SERVER_URL_NOT_CONFIGURED(HttpStatus.UNAUTHORIZED, "CAS authentication failed - CAS server url is not configured."),
	CAS_LOGOUT_SERVER_URL_NOT_CONFIGURED(HttpStatus.UNAUTHORIZED, "CAS logout failed - CAS server url is not configured."),
	CAS_LOGIN_SERVER_NOT_AVAILABLE(HttpStatus.UNAUTHORIZED, "CAS authentication failed - CAS server is not available."),
	CAS_IDM_LOGIN_ADMIN_ONLY(HttpStatus.UNAUTHORIZED, "CAS authentication is enabled, appication admin can sign in by IdM only."),
	CAS_LOGOUT_SERVER_NOT_AVAILABLE(HttpStatus.UNAUTHORIZED, "CAS logout failed - CAS server is not available."),
	TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "Token not found."),
	TOKEN_READ_FAILED(HttpStatus.BAD_REQUEST, "Wrong token given."),
	TWO_FACTOR_INIT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Two factor authentication is not available."),
	TWO_FACTOR_AUTH_REQIURED(HttpStatus.UNAUTHORIZED, "Verification code is needed.", NotificationLevel.INFO),
	TWO_FACTOR_VERIFICATION_CODE_FAILED(HttpStatus.UNAUTHORIZED, "Verification code is not valid."),
	TWO_FACTOR_GENERATE_CODE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Verification code cannot be generated."),
	AUTHORITIES_CHANGED(HttpStatus.UNAUTHORIZED, "Authorities changed or user logged out, log in again.", NotificationLevel.INFO),
	LOG_IN(HttpStatus.UNAUTHORIZED, "You need to be logged in."),
	LOG_IN_FAILED(HttpStatus.UNAUTHORIZED, "Log in failed."),
	XSRF(HttpStatus.UNAUTHORIZED, "XSRF cookie failed."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden."),
	FORBIDDEN_ENTITY(HttpStatus.FORBIDDEN, "Forbidden: entity [%s], permission [%s], type [%s]."),
	FORBIDDEN_CODEABLE_ENTITY(HttpStatus.FORBIDDEN, "Forbidden: Entity with ID [%s], permission [%s], type [%s], code [%s]."),
	DUPLICATE_EXTERNAL_ID(HttpStatus.CONFLICT, "Entity type [%s] with external identifier [%s] already exist (id: [%s])."),
	DUPLICATE_EXTERNAL_CODE(HttpStatus.CONFLICT, "Entity type [%s] with external code [%s] already exist (id: [%s])."),
	ENTITY_TYPE_NOT_EXTERNAL_IDENTIFIABLE(HttpStatus.BAD_REQUEST, "Entity type [%s] is not external identifiable."),
	ENTITY_TYPE_NOT_EXTERNAL_CODEABLE(HttpStatus.BAD_REQUEST, "Entity type [%s] is not external codeable."),
	ENTITY_TYPE_NOT_DISABLEABLE(HttpStatus.BAD_REQUEST, "Entity type [%s] is not disableable."),
	CORRELATION_PROPERTY_NOT_FOUND(HttpStatus.BAD_REQUEST, "Entity type [%s] does not contains the correlation property [%s]."),
	CORRELATION_PROPERTY_WRONG_TYPE(HttpStatus.BAD_REQUEST, "Entity type [%s] and property [%s] has wrong type. Only String or UUID is supported now."),
	// data
	SEARCH_ERROR(HttpStatus.BAD_REQUEST, "Error during searching entities. Error: %s"),
	UNMODIFIABLE_LOCKED(HttpStatus.CONFLICT, "This entity [%s] cannot be modified (is locked)!"),
	// filter
	FILTER_IMPLEMENTATION_NOT_FOUND(HttpStatus.CONFLICT, "Filter implementation [%s] for property [%s] not found. Repair configuration property [%s]."),
	FILTER_PROPERTY_NOT_SUPPORTED(HttpStatus.NOT_IMPLEMENTED, "Filter for property [%s] for entity [%s] is not supported and cannot be used."),
	FILTER_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "Filter for property [%s] for entity [%s] - count of values exceeded, given [%s] values, max [%s]."),
	// identity
	IDENTITY_ALREADY_DISABLED_MANUALLY(HttpStatus.BAD_REQUEST, "Identity [%s] is already disabled manually, cannot be disable twice."),
	IDENTITY_NOT_DISABLED_MANUALLY(HttpStatus.BAD_REQUEST, "Identity [%s] is not disabled manually [%s], cannot be enabled."),
	IDENTITYIMAGE_WRONG_FORMAT(HttpStatus.BAD_REQUEST, "Uploaded file is not an image!"),
	IDENTITY_USERNAME_EXIST(HttpStatus.CONFLICT, "Username [%s] already exists!"),
	IDENTITY_UNABLE_GENERATE_UNIQUE_USERNAME(HttpStatus.CONFLICT, "Unable to generate unique username. All numbers from set range have been used!"),
	IDENTITY_DELETE_FAILED_HAS_CONTRACTS(HttpStatus.CONFLICT, "Identity [%s] cannot be deleted - have assigned contracts."),
	IDENTITY_PROFILE_IMAGE_WRONG_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "Profile image has wrong content type [%s]. Image content types (image/*) are supported only."),
	IDENTITY_PROFILE_IMAGE_MAX_FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "Profile image file size [%s] exceeded maximum [%s]."),
	// password change
	PASSWORD_CHANGE_NO_SYSTEM(HttpStatus.BAD_REQUEST, "No system selected."),
	PASSWORD_CHANGE_CURRENT_FAILED_IDM(HttpStatus.BAD_REQUEST, "Given current password doesn't match to current idm password."),
	PASSWORD_CHANGE_FAILED(HttpStatus.BAD_REQUEST, "Password change failed: %s."),
	PASSWORD_CHANGE_ACCOUNT_FAILED(HttpStatus.CONFLICT, "Password change failed on accounts [%s]."),
	PASSWORD_CHANGE_ACCOUNT_SUCCESS(HttpStatus.OK, "Password is changed on accounts [%s]."),
	PASSWORD_CHANGE_ALL_ONLY(HttpStatus.BAD_REQUEST, "Password change is enabled for all systems only. Select all systems (idm, all accounts)"),
	PASSWORD_CHANGE_DISABLED(HttpStatus.BAD_REQUEST, "Password change is disabled"),
	PASSWORD_EXPIRED(HttpStatus.UNAUTHORIZED, "Password expired"),
	MUST_CHANGE_IDM_PASSWORD(HttpStatus.UNAUTHORIZED, "User [%s] has to change password"),
	PASSWORD_DOES_NOT_MEET_POLICY(HttpStatus.BAD_REQUEST, "Password does not match password policy: %s", NotificationLevel.INFO),
	PASSWORD_PREVALIDATION(HttpStatus.ACCEPTED, "Password does not match password policy: %s"),
	PASSWORD_CANNOT_CHANGE(HttpStatus.BAD_REQUEST, "You cannot change your password yet. Please try it again after [%s]."),
	// password create
	PASSWORD_CANNOT_BE_CREATED(HttpStatus.BAD_REQUEST, "Password cannot be created"),
	TASK_SAME_DELEGATE_AS_CURRENT_IDENTITY(HttpStatus.BAD_REQUEST, "You cannot create self delegation [%s]"),
	// tree
	TREE_NODE_BAD_PARENT(HttpStatus.BAD_REQUEST, "Tree node [%s], have bad parent."),
	TREE_NODE_EMPTY_CODE(HttpStatus.BAD_REQUEST, "Empty code for newly saved tree node."),
	TREE_NODE_BAD_TYPE(HttpStatus.BAD_REQUEST, "Tree node [%s], have bad type."),
	TREE_NODE_BAD_CHILDREN(HttpStatus.BAD_REQUEST, "Tree node [%s], have bad children."),
	TREE_NODE_BAD_NICE_NAME(HttpStatus.CONFLICT, "Nice name [%s] is found at same level."),
	TREE_NODE_DELETE_FAILED_HAS_CHILDREN(HttpStatus.CONFLICT, "Tree node [%s] has children, cannot be deleted. Remove them at first."),
	TREE_NODE_DELETE_FAILED_HAS_CONTRACTS(HttpStatus.CONFLICT, "Tree node [%s] has contract assigned, cannot be deleted. Remove them at first."),
	TREE_NODE_DELETE_FAILED_HAS_CONTRACT_POSITIONS(HttpStatus.CONFLICT, "Tree node [%s] has contract posistion assigned, cannot be deleted. Remove them at first."),
	TREE_NODE_DELETE_FAILED_HAS_CONTRACT_SLICES(HttpStatus.CONFLICT, "Tree node [%s] has contract slice assigned, cannot be deleted. Remove them at first."),
	TREE_NODE_DELETE_FAILED_HAS_ROLE(HttpStatus.CONFLICT, "Tree node [%s] has assigned automatic roles. Remove automatic roles at first."),
	TREE_TYPE_DELETE_FAILED_HAS_CHILDREN(HttpStatus.CONFLICT, "Tree type [%s] has children, cannot be deleted. Remove them at first."),
	TREE_TYPE_DELETE_FAILED_HAS_CONTRACTS(HttpStatus.CONFLICT, "Tree type [%s] has contract assigned, cannot be deleted. Remove them at first."),
	// role catalogs
	ROLE_CATALOGUE_BAD_PARENT(HttpStatus.BAD_REQUEST, "Role catalogue [%s] has bad parent."),
	ROLE_CATALOGUE_DELETE_FAILED_HAS_CHILDREN(HttpStatus.CONFLICT, "Role catalogue [%s] has children, cannot be deleted. Remove them at first."),
	ROLE_CATALOGUE_FORCE_DELETE_HAS_CHILDREN(HttpStatus.FOUND, "Role catalogue [%s] has catalogue items (children). Remove sub catalogue items before or use force delete."),
	
	ROLE_CATALOGUE_BAD_NICE_NAME(HttpStatus.CONFLICT, "Nice name [%s] is found at same level."),
	//
	MODULE_NOT_DISABLEABLE(HttpStatus.BAD_REQUEST, "Module [%s] is not disableable."),
	MODULE_DISABLED(HttpStatus.BAD_REQUEST, "Module [%s] is disabled."),
	CONFIGURATION_DISABLED(HttpStatus.BAD_REQUEST, "Configuration [%s] is disabled."),
	CONFIGURATION_SWITCH_INSTANCE_NOT_CHANGED(HttpStatus.BAD_REQUEST, "Previous instance is the same as newly used for asynchronous processing [%s]. Instance will not be changed."),
	CONFIGURATION_SWITCH_INSTANCE_MORE_PREVIOUS_FOUND(HttpStatus.FOUND, "Found more previously used instances for asynchronous processing [%s].", NotificationLevel.INFO),
	CONFIGURATION_SWITCH_INSTANCE_SUCCESS(HttpStatus.OK, "Instance for asynchronous processing changed from [%s] to [%s]. Updated [%s] scheduled tasks, [%s] created long runnung tasks and [%s] created events."),
	// role
	ROLE_DELETE_FAILED_IDENTITY_ASSIGNED(HttpStatus.CONFLICT, "Role [%s] cannot be deleted - some identites have role assigned."),
	ROLE_DELETE_FAILED_HAS_TREE_NODE(HttpStatus.CONFLICT, "Role [%s] has assigned automatic roles. Remove automatic roles at first."),
	ROLE_DELETE_FAILED_AUTOMATIC_ROLE_ASSIGNED(HttpStatus.CONFLICT, "Role [%s] cannot be deleted - some automatic roles by attribe has assigned this role."),
	ROLE_DELETE_FAILED_HAS_COMPOSITION(HttpStatus.CONFLICT, "Role [%s] cannot be deleted - composition is defined. Remove role composition at first."),
	ROLE_CODE_ENVIRONMENT_CONFLICT(HttpStatus.CONFLICT, "Role code [%s] cannot be combined with environment [%s]. Use base code with environment instead."),
	ROLE_CODE_REQUIRED(HttpStatus.BAD_REQUEST, "Role code (code or base code) is required."),
	// groovy script
	GROOVY_SCRIPT_VALIDATION(HttpStatus.BAD_REQUEST, "Script contains compillation errors."),
	GROOVY_SCRIPT_SYNTAX_VALIDATION(HttpStatus.BAD_REQUEST, "Script contains syntaxt error: [%s] at line [%s]."),
	GROOVY_SCRIPT_SECURITY_VALIDATION(HttpStatus.BAD_REQUEST, "Script did not pass security inspection!"),
	GROOVY_SCRIPT_NOT_ACCESSIBLE_CLASS(HttpStatus.BAD_REQUEST, "Class [%s] isn't accessible!"),
	GROOVY_SCRIPT_NOT_ACCESSIBLE_SERVICE(HttpStatus.BAD_REQUEST, "Service [%s] isn't accessible!"),
	GROOVY_SCRIPT_EXCEPTION(HttpStatus.BAD_REQUEST, "Script has some errors: [%s]"),
	GROOVY_SCRIPT_INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "Script call script from invalid category: [%s]"),
	// eav
	FORM_VALUE_WRONG_TYPE(HttpStatus.BAD_REQUEST, "Form value [%s] for attribute [%s] has to be type of [%s], given [%s]"),
	FORM_ATTRIBUTE_DELETE_FAILED_HAS_VALUES(HttpStatus.CONFLICT, "Form attribute [%s] cannot be deleted - some form values already using this attribute."),
	FORM_ATTRIBUTE_CHANGE_PERSISTENT_TYPE_FAILED_HAS_VALUES(HttpStatus.CONFLICT, "Persistent type for form attribute [%s] cannot be changed - some form values already using this attribute. Data migrations are not implemented."),
	FORM_ATTRIBUTE_CHANGE_CONFIDENTIAL_FAILED_HAS_VALUES(HttpStatus.CONFLICT, "Confidential flag for form attribute [%s] cannot be changed - some form values already using this attribute. Data migrations are not implemented."),
	FORM_ATTRIBUTE_DELETE_FAILED_SYSTEM_ATTRIBUTE(HttpStatus.CONFLICT, "Form attribute [%s] cannot be deleted - this attribute is flaged as system attribute."),
	FORM_ATTRIBUTE_DELETE_FAILED_ROLE_ATTRIBUTE(HttpStatus.CONFLICT, "Form attribute [%s] cannot be deleted - is using as role-attribute for role [%s]."),
	FORM_DEFINITION_DELETE_FAILED_SYSTEM_DEFINITION(HttpStatus.CONFLICT, "Form definition [%s] cannot be deleted - this definition is flaged as system definition."),
	FORM_DEFINITION_INCOMPATIBLE_CHANGE(HttpStatus.CONFLICT, "Form definition [%s][%s] cannot be updated. Attribute's [%s] property [%s] cannot be updated automatically [%s to %s]. Provide change script for updating form definition or define new form definition (~new version)."),
	FORM_DEFINITION_DELETE_FAILED_MAIN_FORM(HttpStatus.CONFLICT, "Form definition [%s] cannot be deleted - this definition is flaged as main. Select another one as main before deletion."),
	FORM_DEFINITION_UPDATE_FAILED_MAIN_FORM(HttpStatus.CONFLICT, "Form definition [%s] cannot be updated - this definition was flaged as main. Select another one as main instead."),
	FORM_ATTRIBUTE_DELETE_FAILED_AUTOMATIC_ROLE_RULE_ASSIGNED(HttpStatus.CONFLICT, "Form attribute [%s] cannot be deleted - some automatic rules use this attribute."),
	FORM_DEFINITION_DELETE_FAILED_ROLE(HttpStatus.CONFLICT, "Form definition [%s] cannot be deleted - role [%s] using that defintion."),
	FORM_INVALID(HttpStatus.BAD_REQUEST, "Form is not valid. Attributes: [%s]."),
	FORM_VALIDATION_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "Form validation type [%s] is not supported for persistent type [%s] for attribute [%s]."),
	FORM_ATTRIBUTE_INVALID_REGEX(HttpStatus.BAD_REQUEST, "Regular expression [%s] for the form attribute [%s] is not valid."),
	FORM_VALUE_DELETE_FAILED_IS_REQUIRED(HttpStatus.CONFLICT, "Form values [%s] cannot be deleted. Form attribute [%s] is defined as required. Change form attribute definition if values are needed to be removed."),
	FORM_PROJECTION_WRONG_VALIDATION_CONFIGURATION(HttpStatus.CONFLICT, "Form projection [%s] is wrongly configured. Fix configured form validations."),
	// audit
	AUDIT_REVISION_NOT_SAME(HttpStatus.BAD_REQUEST, "Audit revision are not same."),
	AUDIT_ENTITY_CLASS_NOT_FOUND(HttpStatus.NOT_FOUND, "Entity class [%s] not found."),
	AUDIT_ENTITY_CLASS_IS_NOT_FILLED(HttpStatus.NOT_FOUND, "Entity class isn't filled."),
	//
	PASSWORD_POLICY_DEFAULT_TYPE(HttpStatus.CONFLICT, "Default password policy is exist. Name [%s]"),
	PASSWORD_POLICY_DEFAULT_TYPE_NOT_EXIST(HttpStatus.NOT_FOUND, "Default password policy is not exist."),
	PASSWORD_POLICY_BAD_TYPE(HttpStatus.BAD_REQUEST, "Password policy has bad type: [%s]."),
	PASSWORD_POLICY_VALIDATION(HttpStatus.BAD_REQUEST, "Password policy validation problem."),
	PASSWORD_POLICY_MAX_LENGTH_LOWER(HttpStatus.BAD_REQUEST, "Password policy has max length lower than min length."),
	PASSWORD_POLICY_ALL_MIN_REQUEST_ARE_HIGHER(HttpStatus.BAD_REQUEST, "Password policy has sum of all minimum request higher than maximum length."),
	PASSWORD_POLICY_MAX_AGE_LOWER(HttpStatus.BAD_REQUEST, "Password policy has max password age lower than min age."),
	PASSWORD_POLICY_MAX_RULE(HttpStatus.BAD_REQUEST, "Password policy: minimum rules to fulfill must be [%s] or lower."),
	PASSWORD_POLICY_NEGATIVE_VALUE(HttpStatus.BAD_REQUEST, "Password policy can not contain negative values. Attribute: [%s]"),
	PASSWORD_POLICY_BLOCK_TIME_IS_REQUIRED(HttpStatus.BAD_REQUEST, "Attribute 'Max login attempts' attribute has value, but time of blocking missing (for policy [%s])!"),
	PASSWORD_POLICY_INVALID_SETTING(HttpStatus.BAD_REQUEST, "No generated password is able to meet policy constraints."),
	//
	SCHEDULER_INVALID_CRON_EXPRESSION(HttpStatus.BAD_REQUEST, "Cron expression [%s] is invalid."),
	SCHEDULER_UNSUPPORTED_TASK_TRIGGER(HttpStatus.BAD_REQUEST, "Task trigger [%s] is not supported."),
	SCHEDULER_CREATE_TASK_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "%s"),
	SCHEDULER_DELETE_TASK_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "%s"),
	SCHEDULER_INTERRUPT_TASK_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "%s"),
	SCHEDULER_CREATE_TRIGGER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "%s"),
	SCHEDULER_DELETE_TRIGGER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "%s"),
	SCHEDULER_PAUSE_TRIGGER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "%s"),
	SCHEDULER_RESUME_TRIGGER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "%s"),
	SCHEDULER_DRY_RUN_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "Task type [%s] does not support dry run mode."),
	//
	// logger
	LOGGER_LEVEL_NOT_FOUND(HttpStatus.BAD_REQUEST, "Logger level [%s] does not exist. Available logger levels [TRACE, DEBUG, INFO, WARN, ERROR]."),
	//
	LONG_RUNNING_TASK_NOT_FOUND(HttpStatus.BAD_REQUEST, "Task type [%s] can not be instantiated"),
	LONG_RUNNING_TASK_NOT_RUNNING(HttpStatus.BAD_REQUEST, "Task [%s] is not running - can not be innterrupted or cancelled."),
	LONG_RUNNING_TASK_DIFFERENT_INSTANCE(HttpStatus.BAD_REQUEST, "Task [%s] has different instance [%s], can not be accessed from this instance [%s]."),
	LONG_RUNNING_TASK_IS_RUNNING(HttpStatus.BAD_REQUEST, "Task [%s] is already running - can not be started twice."),
	LONG_RUNNING_TASK_ACCEPTED(HttpStatus.ACCEPTED, "Concurrent task is already running. Task will be started asynchronously after concurrent task ends."),
	LONG_RUNNING_TASK_DELETE_FAILED_IS_RUNNING(HttpStatus.BAD_REQUEST, "Task [%s] is already running - cannot be deleted. Cancel task or wait to complete."),
	LONG_RUNNING_TASK_IS_PROCESSED(HttpStatus.BAD_REQUEST, "Task [%s] is already processed - can not be started twice"),
	LONG_RUNNING_TASK_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Task [%s] type [%s] ended on instance [%s] with exception."),
	LONG_RUNNING_TASK_ITEM_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Processing task item for candidate [%s] ended with exception."),
	LONG_RUNNING_TASK_CANCELED_BY_RESTART(HttpStatus.GONE, "Task [%s] type [%s] on instance [%s] was canceled during restart."),
	LONG_RUNNING_TASK_INTERRUPT(HttpStatus.INTERNAL_SERVER_ERROR, "Task [%s] type [%s] on instance [%s] was interrupted."),
	LONG_RUNNING_TASK_INIT_FAILED(HttpStatus.BAD_REQUEST, "Task [%s] type [%s] has invalid properties."),
	LONG_RUNNING_TASK_PARTITIAL_DOWNLOAD(HttpStatus.PARTIAL_CONTENT, "Task finished successfully, result can be download as separate attachment."),
	LONG_RUNNING_TASK_NOT_RECOVERABLE(HttpStatus.INTERNAL_SERVER_ERROR, "Task [%s] type [%s] cannot run again, is not recoverable."),
	//
	PASSWORD_EXPIRATION_TASK_DAYS_BEFORE(HttpStatus.BAD_REQUEST, "'Days before' parameter is required and has to be number greater than zero, given [%s]."),
	PASSWORD_EXPIRATION_TODAY_INFO(HttpStatus.NOT_MODIFIED, "Password is valid till today. Password expiration will be published next day."),
	//
	AUTOMATIC_ROLE_TASK_EMPTY(HttpStatus.BAD_REQUEST, "Automatic role id is required."),
	AUTOMATIC_ROLE_TASK_INVALID(HttpStatus.BAD_REQUEST, "Set one of automatic role by tree structure or by attribute."),
	AUTOMATIC_ROLE_TREE_TASK_INVALID(HttpStatus.BAD_REQUEST, "Set at least one of automatic role by tree structure."),
	AUTOMATIC_ROLE_ASSIGN_NOT_COMPLETE(HttpStatus.BAD_REQUEST, "Role [%s] by automatic role [%s] was not processed completely."),
	AUTOMATIC_ROLE_ASSIGN_TASK_NOT_COMPLETE(HttpStatus.BAD_REQUEST, "Role [%s] by automatic role [%s] was not assigned for identity [%s]."),
	AUTOMATIC_ROLE_ASSIGN_TASK_ROLE_ASSIGNED(HttpStatus.OK, "Role [%s] by automatic role [%s] for identity [%s] is assigned."),
	AUTOMATIC_ROLE_ASSIGN_TASK_ROLE_REMOVED(HttpStatus.OK, "Role [%s] by automatic role [%s] for identity [%s] is removed."),
	AUTOMATIC_ROLE_ALREADY_ASSIGNED(HttpStatus.OK, "Role [%s] by automatic role [%s] for identity [%s] is assigned."),
	AUTOMATIC_ROLE_ALREADY_ASSIGNED_TO_CONTRACT(HttpStatus.CONFLICT, "Role [%s] by automatic role [%s] for contract [%s] and position [%s] is already assigned."),
	AUTOMATIC_ROLE_CONTRACT_IS_NOT_VALID(HttpStatus.BAD_REQUEST, "Role [%s] by automatic role [%s] for identity [%s] was not assigned, contract is not valid (skip)."),
	AUTOMATIC_ROLE_REMOVE_TASK_NOT_COMPLETE(HttpStatus.BAD_REQUEST, "Role [%s] by automatic role [%s] was not removed for identity [%s]."),
	AUTOMATIC_ROLE_REMOVE_TASK_RUN_CONCURRENTLY(HttpStatus.BAD_REQUEST, "Automatic role [%s] is removed in concurent task [%s]"),
	AUTOMATIC_ROLE_REMOVE_TASK_ADD_RUNNING(HttpStatus.BAD_REQUEST, "Automatic role [%s] is added in concurent task [%s], wait for task is complete, after removal."),
	AUTOMATIC_ROLE_REMOVE_HAS_ASSIGNED_ROLES(HttpStatus.CONFLICT, "Remove automatic role [%s] is not complete, some identity roles [%s] were assigned to identities in the meantime."),
	AUTOMATIC_ROLE_TASK_RUNNING(HttpStatus.ACCEPTED, "Automatic role is processed in concurent task [%s], wait for task is complete."),
	AUTOMATIC_ROLE_RULE_ATTRIBUTE_EMPTY(HttpStatus.BAD_REQUEST, "Rule for automatic role hasn't filled necessary attribute: [%s]."),
	AUTOMATIC_ROLE_RULE_INVALID_COMPARSION_WITH_MULTIPLE_ATTIBUTE(HttpStatus.BAD_REQUEST, "Comparsion [%s] cannot be used with multiple form attribute."),
	AUTOMATIC_ROLE_RULE_COMPARSION_IS_ONLY_FOR_NUMERIC_ATTRIBUTE(HttpStatus.BAD_REQUEST, "Comparsion [%s] can be used only with numeric form attribute."),
	AUTOMATIC_ROLE_RULE_INVALID_COMPARSION_BOOLEAN(HttpStatus.BAD_REQUEST, "Comparsion [%s] cannot be used with boolean types."),
	AUTOMATIC_ROLE_RULE_PERSISTENT_TYPE_TEXT(HttpStatus.BAD_REQUEST, "Persistent type TEXT isn't allowed."),
	AUTOMATIC_ROLE_PROCESS_TASK_NOT_COMPLETE(HttpStatus.BAD_REQUEST, "Automatic role [%s] was not process correctly, failed contracts add: [%s], failed contracts remove: [%s]."),
	AUTOMATIC_ROLE_SKIPPED(HttpStatus.ACCEPTED, "Recount of automatic roles was skipped."),
	AUTOMATIC_ROLE_SKIPPED_INVALID_CONTRACT(HttpStatus.ACCEPTED, "Recount of automatic roles for invalid contract was skipped."),
	//
	// role tree node
	ROLE_TREE_NODE_TYPE_EXISTS(HttpStatus.CONFLICT, "Role tree node for this role id: [%s], tree node id: [%s] and recursion type [%s] already exists"),
	//
	// forest index
	FOREST_INDEX_DISABLED(HttpStatus.BAD_REQUEST, "Forest index is disabled. Enable configuration property [%s]."),
	FOREST_INDEX_RUNNING(HttpStatus.CONFLICT, "Rebuilding index for forest tree type [%s] already running."),
	//
	// notification
	NOTIFICATION_SYSTEM_TEMPLATE_DELETE_FAILED(HttpStatus.BAD_REQUEST, "System template [%s] can't be deleted."),
	NOTIFICATION_TOPIC_AND_LEVEL_EXISTS(HttpStatus.CONFLICT, "Configuration can not be saved. Topic [%s] and null level exists!"),
	NOTIFICATION_TEMPLATE_ERROR_XML_SCHEMA(HttpStatus.BAD_REQUEST, "Failed load template [%s], error in xml schema."),
	NOTIFICATION_TEMPLATE_MORE_CODE_FOUND(HttpStatus.CONFLICT, "More templates in resource found for code: [%s]."),
	NOTIFICATION_TEMPLATE_XML_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "XML file for template code: [%s] not found."),
	NOTIFICATION_SENDER_IMPLEMENTATION_NOT_FOUND(HttpStatus.CONFLICT, "Sender implementation [%s] for type [%s] not found. Repair configuration property [%s]."),
	NOTIFICATION_NOT_SENT(HttpStatus.CONFLICT, "Notification was not sent. Notification configuration for topic [%s] not found or is disabled."),
	NOTIFICATION_CONFIGURATION_RECIPIENT_NOT_FOUND(HttpStatus.BAD_REQUEST, "Recipients are empty. Recipients are required for for notification configuration with topic [%s] with redirect enabled."),
	NOTIFICATION_TEMPLATE_DELETE_FAILED_USED_CONFIGURATION(HttpStatus.CONFLICT, "Notification template [%s] cannot be deleted, it is used in [%s] notification configuration(s)."),
	NOTIFICATION_TEMPLATE_DELETE_FAILED_USED_NOTIFICATION(HttpStatus.CONFLICT, "Notification template [%s] cannot be deleted, it is used in [%s] notification(s)."),
	//
	// scripts
	SCRIPT_MORE_CODE_FOUND(HttpStatus.CONFLICT, "More scripts in resource found for code: [%s]."),
	SCRIPT_XML_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "XML file for script code: [%s] not found."),
	//
	// Role request
	ROLE_REQUEST_NO_EXECUTE_IMMEDIATELY_RIGHT(HttpStatus.FORBIDDEN, "You do not have right for immidiately execute role request [%s]!"),
	ROLE_REQUEST_EXECUTE_WRONG_STATE(HttpStatus.BAD_REQUEST, "Request is in state [%s], only state APPROVED and CONCEPT can be executed!"),
	ROLE_REQUEST_APPLICANTS_NOT_SAME(HttpStatus.BAD_REQUEST, "Some concept/s role in role request [%s] have different applicant than [%s]!"),
	ROLE_REQUEST_EXECUTED_CANNOT_DELETE(HttpStatus.BAD_REQUEST, "Request [%s] in EXECUTED state cannot be deleted!"),
	ROLE_REQUEST_AUTOMATICALLY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Field 'requested by' in request [%s] cannot be 'AUTOMATICALLY' via REST API!"),
	ROLE_REQUEST_UNVALID_CONCEPT_ATTRIBUTE(HttpStatus.BAD_REQUEST, "Concept [%s] (for role [%s]) in the request [%s] has unvalid attribute [%s]!"),
	ROLE_REQUEST_SYSTEM_STATE_CANCELED(HttpStatus.INTERNAL_SERVER_ERROR, "Request system state was canceled from state [%s]!"),
	//
	IDENTITY_ROLE_UNVALID_ATTRIBUTE(HttpStatus.BAD_REQUEST, "Identity-role [%s] (for role [%s]) has unvalid attribute [%s]!"),
	// 
	// Recaptcha
	RECAPTCHA_SECRET_KEY_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "Recaptcha component is wrong configured, property [%s] is missing - configure property value."),
	RECAPTCHA_CHECK_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Recaptcha check failed, error codes [%s]"),
	RECAPTCHA_SERVICE_UNAVAILABLE(HttpStatus.INTERNAL_SERVER_ERROR, "Recaptcha service is unavailable: %s"),
	//
	// Crypt
	CRYPT_DEMO_KEY_NOT_FOUND(HttpStatus.BAD_REQUEST, "Demo key: [%s] cannot be found! Please create primary key: [%s] or fix demo key."),
	CRYPT_INITIALIZATION_PROBLEM(HttpStatus.BAD_REQUEST, "Initialization problem, algorithm [%s]."),
	//
	AUTHORIZATION_POLICY_GROUP_AUTHORIZATION_TYPE(HttpStatus.BAD_REQUEST, "When authorization type is filled [%s] then groupPermission has to be filled too [%s]."),
	//
	// Common 5xx
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "%s"),
	NOT_IMPLEMENTED(HttpStatus.INTERNAL_SERVER_ERROR, "Not implemented: %s"),
	NOT_SUPPORTED(HttpStatus.INTERNAL_SERVER_ERROR, "Not supported: %s"),
	WF_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Error occured during workflow execution: %s"),
	MODEL_MAPPER_SERVICE_INIT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Service [%s] cannot be used, model mapper is wrongly inited, try to restart this application."),
	//
	// backup and redeploy
	BACKUP_FOLDER_FOUND(HttpStatus.OK, "Backup folder [%s] is configured in application properties."),
	BACKUP_FOLDER_NOT_FOUND(HttpStatus.BAD_REQUEST, "Backup folder doesn't exist in application properties, please specify this property: [%s]."),
	BACKUP_FAIL(HttpStatus.BAD_REQUEST, "Backup for script code: [%s] failed. Error message: [%s]."),
	DEPLOY_ERROR(HttpStatus.BAD_REQUEST, "Failed load entity from path [%s]."),
	DEPLOY_SCRIPT_FOLDER_FOUND(HttpStatus.OK, "Folder (or classpath) setting for redeploy scripts [%s] is configured in application properties."),
	DEPLOY_TEMPLATE_FOLDER_FOUND(HttpStatus.OK, "Folder (or classpath) setting for redeploy templates [%s] is configured in application properties."),
	XML_JAXB_INIT_ERROR(HttpStatus.BAD_REQUEST, "Failed init JAXB instance."),
	//
	// Rest template
	WRONG_PROXY_CONFIG(HttpStatus.INTERNAL_SERVER_ERROR, "Wrong configuration of http proxy. The required format is '[IP]:[PORT]'. Example: '153.25.16.8:1234'"),
	//
	// Automatic role request
	AUTOMATIC_ROLE_REQUEST_START_WITHOUT_RULE(HttpStatus.BAD_REQUEST, "Automatic role request must have at least one rule [%s]"),
	// ECM
	ATTACHMENT_INIT_DEFAULT_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Creating directory for default storage [%s] in temp directory failed."),
	ATTACHMENT_INIT_DEFAULT_TEMP_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Creating directory for default temp storage [%s] in temp directory failed."),
	ATTACHMENT_CREATE_TEMP_FILE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Creating temporary file [%s] in temp directory [%s] failed."),
	ATTACHMENT_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Update attachment [%s] with owner [%s][%s] failed."),
	ATTACHMENT_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Create attachment [%s] with owner [%s][%s] failed."),
	ATTACHMENT_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "Binary data for attachment [%s:%s] - [%s] not found."),
	ATTACHMENT_SIZE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "The attachment data exceeds its maximum permitted size of [%s] bytes."),
	//
	// Events
	EVENT_CANCELED_BY_RESTART(HttpStatus.GONE, "Event [%s] type [%s] on instance [%s] was canceled during restart."),
	EVENT_DUPLICATE_CANCELED(HttpStatus.OK, "Event [%s] type [%s] for owner [%s] on instance [%s] was canceled, it is duplicate to event [%s]."),
	EVENT_ALREADY_CLOSED(HttpStatus.OK, "Event is already closed, will be logged only."),
	EVENT_ACCEPTED(HttpStatus.ACCEPTED, "Event type [%s] for owner [%s] on instance [%s] was put into queue and will be processed asynchronously."),
	EVENT_EXECUTE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Event [%s] type [%s] for owner [%s] on instance [%s] failed."),
	EVENT_EXECUTE_PROCESSOR_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Event [%s] failed in processor [%s]."),
	EVENT_CONTENT_DELETED(HttpStatus.CONFLICT, "Content for event [%s] type [%s] for owner [%s] on instance [%s] was deleted. Event cannot be executed and will be canceled."),
	EVENT_NOT_RUNNING(HttpStatus.BAD_REQUEST, "Entity event [%s] is not running - can not be cancelled.", NotificationLevel.INFO),
	//
	// Identity bulk actions
	BULK_ACTION_BAD_FILTER(HttpStatus.BAD_REQUEST, "Filter must be instance of [%s], given instance [%s]."),
	BULK_ACTION_REQUIRED_PROPERTY(HttpStatus.BAD_REQUEST, "Property [%s] is required."),
	BULK_ACTION_ONLY_ONE_FILTER_CAN_BE_APPLIED(HttpStatus.BAD_REQUEST, "Only one filtering option can be applied."),
	BULK_ACTION_MODULE_DISABLED(HttpStatus.BAD_REQUEST, "Action [%s] can't be processed. Module [%s] is disabled."),
	BULK_ACTION_ENTITIES_ARE_NOT_SPECIFIED(HttpStatus.BAD_REQUEST, "Bulk action hasn't specified entities or filter."),
	BULK_ACTION_INSUFFICIENT_PERMISSION(HttpStatus.FORBIDDEN, "Insufficient permissions for execute bulk action [%s] on record [%s] with code [%s]."),
	BULK_ACTION_CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "Contract for identity: [%s] not found."),
	BULK_ACTION_ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "Roles for remove not found for identity: [%s]."),
	BULK_ACTION_IDENTITY_REMOVE_ROLE_FAILED(HttpStatus.NOT_FOUND, "Roles for identity [%s] not removed. Roles not found or cannot be removed (its automatic role, business role or for insufficient permissions)."),
	BULK_ACTION_NOT_AUTHORIZED_CONTRACT_GUARANTEE (HttpStatus.FORBIDDEN, "Insufficient permissions [%s] for the guarantee [%s] for contract [%s]."),
	BULK_ACTION_NOT_AUTHORIZED_MODIFY_CONTRACT (HttpStatus.FORBIDDEN, "Insufficient permissions for [%s] contract modification."),
	BULK_ACTION_NOT_AUTHORIZED_ASSING_ROLE_FOR_CONTRACT (HttpStatus.FORBIDDEN, "Insufficient permissions to assign role for contract [%s]."),
	// Role bulk actions
	ROLE_DELETE_BULK_ACTION_NUMBER_OF_IDENTITIES(HttpStatus.FOUND, "Role [%s] has [%s] role-identities."),
	ROLE_FORCE_DELETE_BULK_ACTION_NUMBER_OF_IDENTITIES(HttpStatus.FOUND, "Role [%s] is assigned [%s] to users. Remove assigned roles before or use force delete."),
	ROLE_DELETE_BULK_ACTION_CONCEPTS_TO_MODIFY(HttpStatus.FOUND, "[%s] request concepts need to be modified. It may tak a while."),
	//
	// Contract and slices
	CONTRACT_DELETE_FAILED_ROLE_ASSIGNED(HttpStatus.CONFLICT, "Contract [%s] cannot be deleted - have assigned roles."),
	CONTRACT_IS_CONTROLLED_CANNOT_BE_MODIFIED(HttpStatus.CONFLICT, "Contract [%s] is controlled by slices. It cannot be modified directly!"),
	CONTRACT_IS_CONTROLLED_CANNOT_BE_DELETED(HttpStatus.CONFLICT, "Contract [%s] is controlled by slices. It cannot be deleted directly!"),
	CONTRACT_IS_CONTROLLED_GUARANTEE_CANNOT_BE_MODIFIED(HttpStatus.CONFLICT, "Contract [%s] is controlled by slices. Contract guarantee cannot be modified directly!"),
	CONTRACT_IS_CONTROLLED_GUARANTEE_CANNOT_BE_DELETED(HttpStatus.CONFLICT, "Contract [%s] is controlled by slices. Contract guarantee cannot be deleted directly!"),
	CONTRACT_SLICE_DUPLICATE_CANDIDATES(HttpStatus.CONFLICT, "We found more then once slice which should be use as contract. This is not allowed. None from this slices will be used as contract. It means contracts [%s] are in incorrect state now!"),
	//
	// Universal requests
	REQUEST_EXECUTED_CANNOT_DELETE(HttpStatus.BAD_REQUEST, "Request [%s] in EXECUTED state cannot be deleted!"),
	DTO_CANNOT_BE_CONVERT_TO_JSON(HttpStatus.INTERNAL_SERVER_ERROR, "DTO [%s] cannot be convert to the JSON!"),
	JSON_CANNOT_BE_CONVERT_TO_DTO(HttpStatus.INTERNAL_SERVER_ERROR, "JSON [%s] cannot be convert to the DTO!"),
	REQUEST_CUD_OPERATIONS_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "CUD operations are not allowed on that controller [%s]. Use request endpoint!"),
	REQUEST_NO_EXECUTE_IMMEDIATELY_RIGHT(HttpStatus.FORBIDDEN, "You do not have right (REQUEST_ADMIN) for immidiately execute request [%s]!"),
	REQUEST_ITEM_IS_NOT_VALID(HttpStatus.BAD_REQUEST, "DTO [%s] in the request item [%s] is not valid!"),
	REQUEST_NO_WF_DEF_FOUND(HttpStatus.BAD_REQUEST, "No approval workflow definition found for entity type [%s]!"),
	REQUEST_OWNER_WAS_DELETED(HttpStatus.GONE, "Owner [%s] was deleted!"),
	REQUEST_OWNER_FROM_OTHER_REQUEST_WAS_DELETED(HttpStatus.GONE, "Owner [%s] from other request [%s] was deleted!"),
	REQUEST_CANNOT_BE_EXECUTED_NONE_ITEMS(HttpStatus.BAD_REQUEST, "Request [%s] cannot be executed. He doesn't have any request items!"),
	REQUEST_ITEM_CANNOT_BE_EXECUTED(HttpStatus.BAD_REQUEST, "Request item [%s] cannot be executed. Must be in state APPROVED or CONCEPT, but is in state [%s]!"),
	REQUEST_ITEM_CANNOT_BE_CREATED(HttpStatus.BAD_REQUEST, "Request item cannot be created/changed for object [%s]. Parent request must be in state INPROGRESS or CONCEPT or EXCEPTION, but is in state [%s]!"),
	REQUEST_ITEM_NOT_EXECUTED_PARENT_CANCELED(HttpStatus.BAD_REQUEST, "Request item [%s] could not be executed, because is using DTO from terminated item [%s]!"),
	REQUEST_ITEM_WRONG_FORM_DEFINITON_IN_VALUES(HttpStatus.BAD_REQUEST, "Request item [%s] could not be saved, because is contains attribute values with wrong form definition! Currently using form definition in the role [%s] is [%s]!"),
	//
	// Role composition
	ROLE_COMPOSITION_RUN_CONCURRENTLY(HttpStatus.ACCEPTED, "Other role composition is already in processing by task [%s]. Role composition [%s] will be processed asynchronously."),
	ROLE_COMPOSITION_ASSIGN_ROLE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Role [%s] by role composition was not assigned."),
	ROLE_COMPOSITION_ASSIGNED_ROLE_REMOVAL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Identity role [%s] was not removed."),
	ROLE_COMPOSITION_REMOVE_TASK_RUN_CONCURRENTLY(HttpStatus.BAD_REQUEST, "Role composition [%s] is removed in concurent task [%s]."),
	ROLE_COMPOSITION_REMOVE_TASK_ADD_RUNNING(HttpStatus.BAD_REQUEST, "Role composition [%s] is added in concurent task [%s], wait for task is complete, before composition can be removed."),
	ROLE_COMPOSITION_REMOVE_HAS_ASSIGNED_ROLES(HttpStatus.CONFLICT, "Remove role composition [%s] is not complete, some identity roles [%s] were assigned to identities in the meantime."),
	//
	// generator
	GENERATOR_FORM_ATTRIBUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "Form attribute for definition [%s] with code [%s] not found."),
	GENERATOR_FORM_DEFINITION_BAD_TYPE(HttpStatus.BAD_REQUEST, "Given form definition id [%s], has not type. Correct type: [%s]."),
	GENERATOR_SCRIPT_RETURN_NULL_OR_BAD_DTO_TYPE(HttpStatus.NOT_FOUND, "Script code [%s] return null or bad dto type. Returned value: [%s]."),
	//
	// Role form attribute
	ROLE_FORM_ATTRIBUTE_CHANGE_DEF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Change of form definition for role [%s] is not allowed, because for this role exists some IdmRoleFormAttribute. First delete them."),
	// Export
	EXPORT_GENERATE_JSON_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "JSON generating from DTO [%s] failed!"),
	// Import
	IMPORT_ZIP_EXTRACTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Extraction of ZIP import failed!"),
	EXPORT_ZIP_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Export - Creation of a ZIP failed!"),
	IMPORT_VALIDATION_FAILED_NO_MANIFEST(HttpStatus.BAD_REQUEST, "Import validation failed. Manifest [%s] was not found!"),
	IMPORT_CONVERT_TO_DTO_FAILED(HttpStatus.BAD_REQUEST, "Convert file [%s] to DTO [%s] failed!"),
	IMPORT_IS_ALREADY_RUNNING(HttpStatus.BAD_REQUEST, "Import for [%s] cannot be execute, because is already running!"),
	EXPORT_IMPORT_FILTER_PARENT_FIELD_MUST_BE_UUID(HttpStatus.BAD_REQUEST, "Export/import parent field [%s] must be UUID and not null, but is [%s]!"),
	EXPORT_IMPORT_REFLECTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Export/import reflection failed!"),
	EXPORT_IMPORT_IO_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Export/import IO exception occurred!"),
	IMPORT_FIELD_EMBEDDED_ANNOTATION_MISSING(HttpStatus.BAD_REQUEST, "Annotation embedded was not found for field [%s]!"),
	IMPORT_FIELD_MUST_CONTAINS_UUID(HttpStatus.BAD_REQUEST, "Field must be UUID but is [%s]!"),
	IMPORT_ADVANCED_PARING_FAILED_NOT_FOUND(HttpStatus.NOT_FOUND, "Advanced paring failed for field [%s] in DTO [%s]. No DTO [%s] was found on target IdM!"),
	IMPORT_FAILED_ROOT_NOT_FOUND(HttpStatus.BAD_REQUEST, "No roots found for DTO type [%s]!"),
	IMPORT_EXECUTED_AS_DRYRUN(HttpStatus.NOT_MODIFIED, "Import executed as dry-run!"),
	IMPORT_CAN_EXECUTE_ONLY_ADMIN(HttpStatus.BAD_REQUEST, "Import can execute only super-admin!"),
	IMPORT_DTO_SKIPPED(HttpStatus.NOT_MODIFIED, "Imported DTO [%s] was skipped. Relation on this DTO was not found and this DTO type is marked as optional."),
	IMPORT_DTO_SKIPPED_DRY_RUN(HttpStatus.NOT_IMPLEMENTED, "Relation on this DTO was not found. Imported DTO [%s] was skipped, because is import executed as dry-run."),
	IMPORT_ADVANCED_PARING_NOT_FOUND_OPTIONAL(HttpStatus.NOT_FOUND, "Advanced paring failed for field [%s] in DTO [%s]. No DTO [%s] was found on target IdM and import was skipped for it!"),
	// Delegations
	DELEGATION_UNSUPPORTED_TYPE(HttpStatus.NOT_FOUND, "Delegation type [%s] is not supported!"),
	DELEGATION_DEFINITION_CANNOT_BE_UPDATED(HttpStatus.BAD_REQUEST, "Definition of a delegation cannot be updated!"),
	DELEGATION_DEFINITION_DELEGATOR_AND_DELEGATE_ARE_SAME(HttpStatus.BAD_REQUEST, "Delegator and delegate [%s] cannot be same for one delegation definition!"),
	MANUAL_TASK_DELEGATION_DELEGATOR_MISSING(HttpStatus.BAD_REQUEST, "Delegator not found. You must apply a filter by delegator (assigned user)!"),
	MANUAL_TASK_DELEGATION_DELEGATOR_IS_NOT_CANDIDATE(HttpStatus.BAD_REQUEST, "Delegator [%s] isn't candidate of the task [%s]!"),
	// Uniform password
	IDENTITY_UNIFORM_PASSWORD(HttpStatus.ACCEPTED, "Identity uniform password."),
	// Monitoring
	MONITORING_IGNORED(HttpStatus.ACCEPTED, "Monitoring is ignored."),
	MONITORING_RESULT(HttpStatus.OK, "Monitoring finished successfully with result."),
	MONITORING_DATABASE_TABLE(HttpStatus.FOUND, "Table [%s]([%s]) contains [%s] records.", NotificationLevel.SUCCESS),
	MONITORING_H2_DATABASE_ERROR(HttpStatus.BAD_REQUEST, "H2 database is used on server instance [%s]. H2 database is not supposed to be used for production environment.", NotificationLevel.ERROR),
	MONITORING_H2_DATABASE_WARNING(HttpStatus.BAD_REQUEST, "H2 database is used on server instance [%s] for [%s] environment.", NotificationLevel.WARNING),
	MONITORING_H2_DATABASE_SUCCESS(HttpStatus.OK, "H2 database is not used on server instance [%s]. Used database [%s].", NotificationLevel.SUCCESS),
	MONITORING_DEMO_ADMIN_WARNING(HttpStatus.BAD_REQUEST, "Demo admin credentials are used. Change admin user password.", NotificationLevel.WARNING),
	MONITORING_DEMO_ADMIN_NOT_FOUND(HttpStatus.OK, "Demo admin user not found.", NotificationLevel.SUCCESS),
	MONITORING_ENTITY_EVENT_ERROR(HttpStatus.CONFLICT, "Entity event queue contains [%s] errors.", NotificationLevel.ERROR),
	MONITORING_LONG_RUNNING_TASK_ERROR(HttpStatus.CONFLICT, "Long running task queue contains [%s] errors.", NotificationLevel.ERROR),
	MONITORING_LOGGING_EVENT_ERROR(HttpStatus.CONFLICT, "Logging events contains [%s] errors.", NotificationLevel.ERROR),
	MONITORING_EVENT_LOCK_QUEUE(HttpStatus.FOUND, "[%s] threads wait for entity event lock", NotificationLevel.SUCCESS);
	
	private final HttpStatus status;
	private final String message;
	private final NotificationLevel level;
	
	private CoreResultCode(HttpStatus status, String message) {
		this(status, message, null);
	}
	
	private CoreResultCode(HttpStatus status, String message, NotificationLevel level) {
		this.message = message;
		this.status = status;
		this.level = level;
	}
	
	@Override
	public String getCode() {
		return this.name();
	}
	
	@Override
	public String getModule() {
		return "core";
	}
	
	@Override
	public HttpStatus getStatus() {
		return status;
	}
	
	@Override
	public String getMessage() {
		return message;
	}
	
	@Override
	public NotificationLevel getLevel() {
		return level;
	}
}
