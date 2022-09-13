/**
 * Data managers register
 *
 * import { IdentityManager } from './redux/data' can be used in react components (ui layer)
 *
 * @author Radek Tomiška
 */
import EntityManager from "./EntityManager";
import FormableEntityManager from "./FormableEntityManager";
import DataManager from "./DataManager";
import IdentityManager from "./IdentityManager";
import IdentityProjectionManager from "./IdentityProjectionManager";
import TreeNodeManager from "./TreeNodeManager";
import TreeTypeManager from "./TreeTypeManager";
import RoleManager from "./RoleManager";
import WorkflowTaskInstanceManager from "./WorkflowTaskInstanceManager";
import IdentityRoleManager from "./IdentityRoleManager";
import IdentityContractManager from "./IdentityContractManager";
import WorkflowProcessInstanceManager from "./WorkflowProcessInstanceManager";
import WorkflowHistoricProcessInstanceManager from "./WorkflowHistoricProcessInstanceManager";
import WorkflowHistoricTaskInstanceManager from "./WorkflowHistoricTaskInstanceManager";
import WorkflowProcessDefinitionManager from "./WorkflowProcessDefinitionManager";
import NotificationManager from "./NotificationManager";
import ConfigurationManager from "./ConfigurationManager";
import EmailManager from "./EmailManager";
import BackendModuleManager from "./BackendModuleManager";
import CacheManager from "./CacheManager";
import RoleCatalogueManager from "./RoleCatalogueManager";
import RoleCatalogueRoleManager from "./RoleCatalogueRoleManager";
import RoleCompositionManager from "./RoleCompositionManager";
import AuditManager from "./AuditManager";
import ScriptManager from "./ScriptManager";
import NotificationConfigurationManager from "./NotificationConfigurationManager";
import WebsocketManager from "./WebsocketManager";
import PasswordPolicyManager from "./PasswordPolicyManager";
import EntityEventProcessorManager from "./EntityEventProcessorManager";
import FilterBuilderManager from "./FilterBuilderManager";
import LongRunningTaskManager from "./LongRunningTaskManager";
import SchedulerManager from "./SchedulerManager";
import NotificationTemplateManager from "./NotificationTemplateManager";
import RoleRequestManager from "./RoleRequestManager";
import ConceptRoleRequestManager from "./ConceptRoleRequestManager";
import RoleTreeNodeManager from "./RoleTreeNodeManager";
import FormDefinitionManager from "./FormDefinitionManager";
import FormProjectionManager from "./FormProjectionManager";
import FormAttributeManager from "./FormAttributeManager";
import FormValueManager from "./FormValueManager";
import AuthorizationPolicyManager from "./AuthorizationPolicyManager";
import ScriptAuthorityManager from "./ScriptAuthorityManager";
import ContractGuaranteeManager from "./ContractGuaranteeManager";
import ContractPositionManager from "./ContractPositionManager";
import ContractSliceGuaranteeManager from "./ContractSliceGuaranteeManager";
import NotificationRecipientManager from "./NotificationRecipientManager";
import SmsManager from "./SmsManager";
import LoggingEventManager from "./LoggingEventManager";
import LoggingEventExceptionManager from "./LoggingEventExceptionManager";
import ConfidentialStorageValueManager from "./ConfidentialStorageValueManager";
import AutomaticRoleAttributeManager from "./AutomaticRoleAttributeManager";
import AutomaticRoleAttributeRuleManager from "./AutomaticRoleAttributeRuleManager";
import LongRunningTaskItemManager from "./LongRunningTaskItemManager";
import AutomaticRoleRequestManager from "./AutomaticRoleRequestManager";
import AutomaticRoleAttributeRuleRequestManager from "./AutomaticRoleAttributeRuleRequestManager";
import EntityEventManager from "./EntityEventManager";
import EntityStateManager from "./EntityStateManager";
import ContractSliceManager from "./ContractSliceManager";
import RoleGuaranteeManager from "./RoleGuaranteeManager";
import RoleGuaranteeRoleManager from "./RoleGuaranteeRoleManager";
import RequestManager from "./RequestManager";
import RequestItemManager from "./RequestItemManager";
import AbstractRequestFormableManager from "./AbstractRequestFormableManager";
import AbstractRequestManager from "./AbstractRequestManager";
import ProfileManager from "./ProfileManager";
import GenerateValueManager from "./GenerateValueManager";
import CodeListManager from "./CodeListManager";
import CodeListItemManager from "./CodeListItemManager";
import IncompatibleRoleManager from "./IncompatibleRoleManager";
import RoleFormAttributeManager from "./RoleFormAttributeManager";
import PasswordHistoryManager from "./PasswordHistoryManager";
import PasswordManager from "./PasswordManager";
import RequestIdentityRoleManager from "./RequestIdentityRoleManager";
import LongPollingManager from "./LongPollingManager";
import ExportImportManager from "./ExportImportManager";
import AvailableServiceManager from "./AvailableServiceManager";
import ImportLogManager from "./ImportLogManager";
import DelegationDefinitionManager from "./DelegationDefinitionManager";
import DelegationManager from "./DelegationManager";
import BulkActionManager from "./BulkActionManager";
import NotificationAttachmentManager from "./NotificationAttachmentManager";
import TokenManager from "./TokenManager";
import MonitoringManager from "./MonitoringManager";
import MonitoringResultManager from "./MonitoringResultManager";
import RoleSystemManager from "./RoleSystemManager";
import UniversalSearchManager from "./UniversalSearchManager";

const ManagerRoot = {
  EntityManager,
  FormableEntityManager,
  DataManager,
  IdentityManager,
  IdentityProjectionManager,
  TreeNodeManager,
  TreeTypeManager,
  RoleManager,
  WorkflowTaskInstanceManager,
  IdentityRoleManager,
  IdentityContractManager,
  WorkflowProcessInstanceManager,
  WorkflowHistoricProcessInstanceManager,
  WorkflowHistoricTaskInstanceManager,
  WorkflowProcessDefinitionManager,
  NotificationManager,
  ConfigurationManager,
  EmailManager,
  BackendModuleManager,
  CacheManager,
  RoleCatalogueManager,
  RoleCatalogueRoleManager,
  RoleCompositionManager,
  AuditManager,
  ScriptManager,
  NotificationConfigurationManager,
  WebsocketManager,
  PasswordPolicyManager,
  EntityEventProcessorManager,
  FilterBuilderManager,
  LongRunningTaskManager,
  SchedulerManager,
  NotificationTemplateManager,
  RoleRequestManager,
  ConceptRoleRequestManager,
  RoleTreeNodeManager,
  FormDefinitionManager,
  FormProjectionManager,
  FormAttributeManager,
  FormValueManager,
  AuthorizationPolicyManager,
  ScriptAuthorityManager,
  ContractGuaranteeManager,
  ContractPositionManager,
  NotificationRecipientManager,
  NotificationAttachmentManager,
  SmsManager,
  LoggingEventManager,
  LoggingEventExceptionManager,
  ConfidentialStorageValueManager,
  AutomaticRoleAttributeManager,
  AutomaticRoleAttributeRuleManager,
  LongRunningTaskItemManager,
  AutomaticRoleRequestManager,
  AutomaticRoleAttributeRuleRequestManager,
  EntityEventManager,
  EntityStateManager,
  ContractSliceManager,
  ContractSliceGuaranteeManager,
  RoleGuaranteeManager,
  RoleGuaranteeRoleManager,
  RequestManager,
  RequestItemManager,
  AbstractRequestFormableManager,
  AbstractRequestManager,
  ProfileManager,
  GenerateValueManager,
  CodeListManager,
  CodeListItemManager,
  IncompatibleRoleManager,
  RoleFormAttributeManager,
  PasswordHistoryManager,
  PasswordManager,
  RequestIdentityRoleManager,
  LongPollingManager,
  ExportImportManager,
  AvailableServiceManager,
  ImportLogManager,
  DelegationDefinitionManager,
  DelegationManager,
  BulkActionManager,
  TokenManager,
  MonitoringManager,
  MonitoringResultManager,
  RoleSystemManager,
  UniversalSearchManager,
};

ManagerRoot.version = "11.1.0";
module.exports = ManagerRoot;
