package eu.bcvsolutions.idm.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.CoreModule;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.PropertyModuleDescriptor;
import eu.bcvsolutions.idm.core.api.domain.ResultCode;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.event.processor.identity.IdentityMonitoredFieldsProcessor;
import eu.bcvsolutions.idm.core.notification.api.dto.NotificationConfigurationDto;
import eu.bcvsolutions.idm.core.notification.domain.NotificationGroupPermission;
import eu.bcvsolutions.idm.core.notification.entity.IdmConsoleLog;
import eu.bcvsolutions.idm.core.notification.entity.IdmEmailLog;
import eu.bcvsolutions.idm.core.notification.entity.IdmSmsLog;
import eu.bcvsolutions.idm.core.security.api.authentication.AuthenticationManager;
import eu.bcvsolutions.idm.core.security.api.domain.GroupPermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmGroupPermission;

/**
 * Core module descriptor - required module
 * 
 * TODO: refactor to configuration
 * 
 * @author Radek Tomiška
 *
 */
@Component
@PropertySource("classpath:module-" + CoreModule.MODULE_ID + ".properties")
@ConfigurationProperties(prefix = "module." + CoreModule.MODULE_ID + ".build", ignoreUnknownFields = true, ignoreInvalidFields = true)
public class CoreModuleDescriptor extends PropertyModuleDescriptor implements CoreModule {

	@Override
	public String getId() {
		return MODULE_ID;
	}
	
	/**
	 * Core module can't be disabled
	 */
	@Override
	public boolean isDisableable() {
		return false;
	}
	
	@Override
	public List<GroupPermission> getPermissions() {
		List<GroupPermission> groupPermissions = new ArrayList<>();
		groupPermissions.addAll(Arrays.asList(IdmGroupPermission.values()));
		groupPermissions.addAll(Arrays.asList(CoreGroupPermission.values()));
		groupPermissions.addAll(Arrays.asList(NotificationGroupPermission.values()));
		return groupPermissions;
	}
	
	@Override
	@SuppressWarnings("deprecation")
	public List<NotificationConfigurationDto> getDefaultNotificationConfigurations() {
		// TODO: this doesn't make good sense now - should be moved to xml at all?
		//
		List<NotificationConfigurationDto> configs = new ArrayList<>();
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_CHANGE_IDENTITY_ROLES, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"This message contains information about result WF (change identity roles).", 
				getNotificationTemplateId("changeIdentityRole")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_CHANGE_IDENTITY_ROLES_IMPLEMENTER, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"This message contains information about result WF (change identity roles).", 
				getNotificationTemplateId("changeIdentityRoleImplementer")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_REQUEST_REALIZED_APPLICANT, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"This message contains information about changes made in specific role-request."
				+ " Notification is send after realization of all changes on a systems."
				+ " If target system is virtual system, then is notification send after resolving his request."
				+ " Notification is send to the owner of the account in IdM.", 
				getNotificationTemplateId("changeIdentityRole"),
				true));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_REQUEST_REALIZED_IMPLEMENTER, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"This message contains information about changes made in specific role-request."
				+ " Notification is send after realization of all changes on a systems."
				+ " If target system is virtual system, then is notification send after resolving his request."
				+ " Notification is send to who made the request.", 
				getNotificationTemplateId("changeIdentityRoleImplementer"),
				true));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_DISAPPROVE_IDENTITY_ROLES, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"This message contains information about disapprove role request.", 
				getNotificationTemplateId("disapproveIdentityRole")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_DISAPPROVE_IDENTITY_ROLES_IMPLEMENTER, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"This message contains information about disapprove role request.", 
				getNotificationTemplateId("disapproveIdentityRoleImplementer")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_RETURN_REQUEST_IDENTITY_ROLES, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"This message contains information about return role request.", 
				getNotificationTemplateId("returnRequestIdentityRole")));
		//		
		configs.add(new NotificationConfigurationDto(
				TOPIC_RETURN_REQUEST_IDENTITY_ROLES_IMPLEMENTER, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"This message contains information about return role request.", 
				getNotificationTemplateId("returnRequestIdentityRoleImplementer")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_WF_TASK_ASSIGNED, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"This message contains information about new assigned task to user.", 
				getNotificationTemplateId("wfTaskNotificationMessage")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_WF_TASK_CREATED, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"This message contains information about new assigned task to user.", 
				getNotificationTemplateId("wfTaskNotificationMessage")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_PASSWORD_EXPIRATION_WARNING, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"Password expiration warning.", 
				getNotificationTemplateId("passwordExpirationWarning")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_PASSWORD_EXPIRED, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"Password expired.", 
				getNotificationTemplateId("passwordExpired")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_IDENTITY_MONITORED_CHANGED_FIELDS, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"This message contains information about changed fields on Identity.", 
				getNotificationTemplateId(IdentityMonitoredFieldsProcessor.EMAIL_TEMPLATE)));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_PASSWORD_CHANGED, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"Password has been changed.", 
				getNotificationTemplateId("passwordChanged")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_PASSWORD_SET, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"Password has been set.", 
				getNotificationTemplateId("passwordChanged")));
		configs.add(new NotificationConfigurationDto(
				TOPIC_COMMON_PASSWORD_SET, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"Common password has been set.", 
				getNotificationTemplateId("commonPasswordSet")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_EVENT, 
				null, 
				IdmConsoleLog.NOTIFICATION_TYPE,
				"Events (asynchronous).", 
				null));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_LOGIN_BLOCKED, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"Login is blocked.", 
				getNotificationTemplateId(AuthenticationManager.TEMPLATE_LOGIN_IS_BLOCKED)));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_BULK_ACTION_END, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"Bulk action ended.", 
				getNotificationTemplateId("bulkActionEnd")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_DELEGATION_CREATED_TO_DELEGATE, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"Notification informs the delegate about the new delegation.", 
				getNotificationTemplateId("delegationCreatedToDelegate")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_DELEGATION_CREATED_TO_DELEGATOR, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"Notification informs the delegator about the new delegation.", 
				getNotificationTemplateId("delegationCreatedToDelegator")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_DELEGATION_DELETED_TO_DELEGATE, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"Notification informs the delegate about the deleted delegation.", 
				getNotificationTemplateId("delegationDeletedToDelegate")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_DELEGATION_DELETED_TO_DELEGATOR, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"Notification informs the delegator about the deleted delegation.", 
				getNotificationTemplateId("delegationDeletedToDelegator")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_DELEGATION_INSTANCE_CREATED_TO_DELEGATE, 
				null, 
				IdmEmailLog.NOTIFICATION_TYPE,
				"Notification informs the delegate about the new delegation instance.", 
				getNotificationTemplateId("delegationInstanceCreatedToDelegate")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_TWO_FACTOR_VERIFICATION_CODE, 
				null, 
				IdmSmsLog.NOTIFICATION_TYPE,
				"Send verification code for two factor authentication.", 
				getNotificationTemplateId("twoFactorVerificationCode")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_APPROVE_ROLE_DEFINITION_CHANGE,
				null,
				IdmEmailLog.NOTIFICATION_TYPE,
				"Send notification to the applicant that role definition change was approved.",
				getNotificationTemplateId("approveRoleDefinitionChange")));
		//
		configs.add(new NotificationConfigurationDto(
				TOPIC_DISAPPROVE_ROLE_DEFINITION_CHANGE,
				null,
				IdmEmailLog.NOTIFICATION_TYPE,
				"Send notification to the applicant that role definition change was disapproved.",
				getNotificationTemplateId("disapproveRoleDefinitionChange")));
		return configs;
	}
	
	@Override
	public boolean isDocumentationAvailable() {
		return true;
	}

	@Override
	public List<ResultCode> getResultCodes() {
		return Arrays.asList(CoreResultCode.values());
	}
}
