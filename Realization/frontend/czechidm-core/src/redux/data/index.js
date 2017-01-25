import EntityManager from './EntityManager';
import DataManager from './DataManager';
import IdentityManager from './IdentityManager';
import TreeNodeManager from './TreeNodeManager';
import TreeTypeManager from './TreeTypeManager';
import RoleManager from './RoleManager';
import WorkflowTaskInstanceManager from './WorkflowTaskInstanceManager';
import IdentityRoleManager from './IdentityRoleManager';
import IdentityContractManager from './IdentityContractManager';
import WorkflowProcessInstanceManager from './WorkflowProcessInstanceManager';
import WorkflowHistoricProcessInstanceManager from './WorkflowHistoricProcessInstanceManager';
import WorkflowHistoricTaskInstanceManager from './WorkflowHistoricTaskInstanceManager';
import WorkflowProcessDefinitionManager from './WorkflowProcessDefinitionManager';
import NotificationManager from './NotificationManager';
import ConfigurationManager from './ConfigurationManager';
import EmailManager from './EmailManager';
import BackendModuleManager from './BackendModuleManager';
import RoleCatalogueManager from './RoleCatalogueManager';
import AuditManager from './AuditManager';
import ScriptManager from './ScriptManager';
import NotificationConfigurationManager from './NotificationConfigurationManager';
import WebsocketManager from './WebsocketManager';
import PasswordPolicyManager from './PasswordPolicyManager';
import EntityEventProcessorManager from './EntityEventProcessorManager';

const ManagerRoot = {
  EntityManager,
  DataManager,
  IdentityManager,
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
  RoleCatalogueManager,
  AuditManager,
  ScriptManager,
  NotificationConfigurationManager,
  WebsocketManager,
  PasswordPolicyManager,
  EntityEventProcessorManager
};

ManagerRoot.version = '0.0.1';
module.exports = ManagerRoot;
