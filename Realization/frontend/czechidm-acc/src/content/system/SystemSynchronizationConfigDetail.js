import PropTypes from 'prop-types';
import React from 'react';
import Helmet from 'react-helmet';
import { connect } from 'react-redux';
import _ from 'lodash';
//
import { Advanced, Basic, Domain, Managers, Utils } from 'czechidm-core';
import { SynchronizationConfigManager, SynchronizationLogManager, SystemAttributeMappingManager, SystemMappingManager } from '../../redux';
import ReconciliationMissingAccountActionTypeEnum from '../../domain/ReconciliationMissingAccountActionTypeEnum';
import SynchronizationLinkedActionTypeEnum from '../../domain/SynchronizationLinkedActionTypeEnum';
import SynchronizationMissingEntityActionTypeEnum from '../../domain/SynchronizationMissingEntityActionTypeEnum';
import SynchronizationUnlinkedActionTypeEnum from '../../domain/SynchronizationUnlinkedActionTypeEnum';
import IcFilterOperationTypeEnum from '../../domain/IcFilterOperationTypeEnum';
import SystemEntityTypeEnum from '../../domain/SystemEntityTypeEnum';
import SyncIdentityConfig from '../sync/SyncIdentityConfig';
import SyncContractConfig from '../sync/SyncContractConfig';
import SyncTreeConfig from '../sync/SyncTreeConfig';
import SyncRoleConfig from '../sync/SyncRoleConfig';
import SyncStatistic from '../sync/SyncStatistic';
import SyncResult from '../sync/SyncResult';

const uiKey = 'system-synchronization-config';
const uiKeyLogs = 'system-synchronization-logs';
const syncActionWfKey = 'eu.bcvsolutions.sync.action';
const synchronizationLogManager = new SynchronizationLogManager();
const synchronizationConfigManager = new SynchronizationConfigManager();
const systemMappingManager = new SystemMappingManager();
const systemAttributeMappingManager = new SystemAttributeMappingManager();
const workflowProcessDefinitionManager = new Managers.WorkflowProcessDefinitionManager();

class SystemSynchronizationConfigDetail extends Advanced.AbstractTableContent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      ...this.state,
      systemMappingId: null, // dependant select box
      activeKey: 1,
      forceSearchCorrelationAttribute: new Domain.SearchParameters()
        .setFilter('mappingId', Domain.SearchParameters.BLANK_UUID), // dependant select box
      enabled: null
    };
  }

  getManager() {
    return synchronizationLogManager;
  }

  getUiKey() {
    return uiKey;
  }

  getContentKey() {
    return 'acc:content.system.systemSynchronizationConfigDetail';
  }

  getNavigationKey() {
    return 'system-synchronization-configs';
  }

  showDetail(entity) {
    const {entityId} = this.props.match.params;
    this.context.history.push(`/system/${entityId}/synchronization-logs/${entity.id}/detail`);
  }

  // @Deprecated - since V10 ... replaced by dynamic key in Route
  // UNSAFE_componentWillReceiveProps(nextProps) {
  //   const { synchronizationConfigId} = nextProps.match.params;
  //   if (synchronizationConfigId && synchronizationConfigId !== this.props.match.params.synchronizationConfigId) {
  //     this._initComponent(nextProps);
  //   }
  // }

  // Did mount only call initComponent method
  componentDidMount() {
    super.componentDidMount();
    //
    this._initComponent(this.props);
    if (this.refs.name) {
      this.refs.name.focus();
    }
  }

  /**
   * Method for init component from didMount method and from willReceiveProps method
   * @param props - properties of component - props For didmount call is this.props for call from willReceiveProps is nextProps.
   */
  _initComponent(props) {
    const {configId} = props.match.params;
    if (this._getIsNew(props)) {
      this.setState({
        synchronizationConfig: {
          system: props.location.query.systemId,
          linkedAction: SynchronizationLinkedActionTypeEnum.findKeyBySymbol(SynchronizationLinkedActionTypeEnum.UPDATE_ENTITY),
          unlinkedAction: SynchronizationUnlinkedActionTypeEnum.findKeyBySymbol(SynchronizationUnlinkedActionTypeEnum.LINK_AND_UPDATE_ENTITY),
          missingEntityAction: SynchronizationMissingEntityActionTypeEnum.findKeyBySymbol(SynchronizationMissingEntityActionTypeEnum.CREATE_ENTITY),
          missingAccountAction: ReconciliationMissingAccountActionTypeEnum.findKeyBySymbol(ReconciliationMissingAccountActionTypeEnum.IGNORE),
          filterOperation: IcFilterOperationTypeEnum.findKeyBySymbol(IcFilterOperationTypeEnum.GREATER_THAN),
          enabled: true,
          differentialSync: true,
          name: 'Sync',
          startAutoRoleRec: true
        },
        enabled: false
      });
    } else {
      this.context.store.dispatch(synchronizationConfigManager.fetchEntity(configId));
    }
  }

  /**
   * Saves give entity
   */
  save(startSynchronization, close, entityType, event) {
    if (event) {
      event.preventDefault();
    }
    const formValid = this.refs.form.isFormValid();
    const formFilterValid = this.refs.formFilter.isFormValid();
    if (this.refs.formSpecific) {
      const formSpecificValid = this.refs.formSpecific.isFormValid();
      if (!formSpecificValid) {
        this.setState({activeKey: 2});
        return;
      }
    }
    if (!formValid) {
      this.setState({activeKey: 1});
      return;
    }
    if (!formFilterValid) {
      this.setState({activeKey: 3});
      return;
    }
    this.setState({showLoading: true});
    const formEntity = this.refs.form.getData();
    const filterData = this.refs.formFilter.getData(false);
    if (this.refs.formSpecific) {
      const specificData = this.refs.formSpecific.getData(false);
      // Merge specific data to form.
      _.merge(formEntity, specificData);
    }
    // Merge filter data to form.
    _.merge(formEntity, filterData);

    if (entityType === SystemEntityTypeEnum.findKeyBySymbol(SystemEntityTypeEnum.CONTRACT)) {
      formEntity._type = 'SysSyncContractConfigDto';
    } else if (entityType === SystemEntityTypeEnum.findKeyBySymbol(SystemEntityTypeEnum.CONTRACT_SLICE)) {
      formEntity._type = 'SysSyncContractConfigDto';
    } else if (entityType === SystemEntityTypeEnum.findKeyBySymbol(SystemEntityTypeEnum.IDENTITY)) {
      formEntity._type = 'SysSyncIdentityConfigDto';
    } else if (entityType === SystemEntityTypeEnum.findKeyBySymbol(SystemEntityTypeEnum.ROLE)) {
      formEntity._type = 'SysSyncRoleConfigDto';
    } else if (entityType === SystemEntityTypeEnum.findKeyBySymbol(SystemEntityTypeEnum.TREE)) {
      formEntity._type = 'SysSyncTreeConfigDto';
    } else {
      formEntity._type = 'SysSyncConfigDto';
    }
    //
    if (formEntity.id === undefined) {
      this.context.store.dispatch(synchronizationConfigManager.createEntity(formEntity, `${uiKey}-detail`, (createdEntity, error) => {
        if (startSynchronization) {
          this.afterSaveAndStartSynchronization(createdEntity, error);
        } else {
          this.afterSave(createdEntity, error, close);
        }
        if (!error && this.refs.table) {
          this.refs.table.reload();
        }
      }));
    } else {
      this.context.store.dispatch(synchronizationConfigManager.updateEntity(formEntity, `${uiKey}-detail`, (updatedEntity, error) => {
        if (startSynchronization) {
          this.afterSaveAndStartSynchronization(updatedEntity, error);
        } else {
          this.afterSave(updatedEntity, error, close);
        }
        if (!error && this.refs.table) {
          this.refs.table.reload();
        }
      }));
    }
  }

  /**
   * This method is call from the wizard if next action was executed.
   */
  wizardNext() {
    if (!this.isWizard()) {
      return;
    }
    const {_synchronizationConfig} = this.props;
    const {entityType} = this.state;
    const isNew = this._getIsNew();
    const synchronizationConfig = isNew ? this.state.synchronizationConfig : _synchronizationConfig;
    const finalEntityType = this._getEntityType(synchronizationConfig, entityType);

    this.save(false, false, finalEntityType);
  }

  afterSave(entity, error, close) {
    const {entityId} = this.props.match.params;
    if (!error) {
      if (this._getIsNew()) {
        this.addMessage({message: this.i18n('create.success', {name: entity.name})});
      } else {
        this.addMessage({message: this.i18n('save.success', {name: entity.name})});
      }
      if (this.isWizard()) {
        // Set sync to the wizard context.
        const wizardContext = this.context.wizardContext;
        wizardContext.syncConfig = entity;
        if (wizardContext.callBackNext) {
          wizardContext.callBackNext();
        } else if (wizardContext.onClickNext) {
          wizardContext.onClickNext(false, true);
        }
      } else {
        this.context.history.replace(`/system/${entityId}/synchronization-configs/${entity.id}/detail`, {configId: entity.id});
        if (close) {
          this.context.history.replace(`/system/${entityId}/synchronization-configs/`);
        }
      }
    } else {
      this.addError(error);
    }
    super.afterSave();
    this.setState({showLoading: false});
  }

  afterSaveAndStartSynchronization(entity, error) {
    const {entityId} = this.props.match.params;
    if (!error) {
      this.context.history.replace(`/system/${entityId}/synchronization-configs/${entity.id}/detail`, {configId: entity.id});
      this._startSynchronization(entity);
    } else {
      this.addError(error);
    }
    super.afterSave();
    this.setState({showLoading: false});
  }

  _getIsNew(nextProps) {
    if ((nextProps && nextProps.location) || this.props.location) {
      const { query } = nextProps ? nextProps.location : this.props.location;
      return (query) ? query.new : null;
    }
    return false;
  }

  _startSynchronization(sync) {
    this.refs[`confirm-delete`].show(
      this.i18n(`acc:content.system.systemSynchronizationConfigs.action.startSynchronization.message`, {name: sync.name}),
      this.i18n(`acc:content.system.systemSynchronizationConfigs.action.startSynchronization.header`)
    ).then(() => {
      this.setState({
        showLoading: true
      });
      const promise = synchronizationConfigManager.getService().startSynchronization(sync.id);
      promise.then((json) => {
        this.setState({
          showLoading: false
        });
        this.addMessage({
          level: 'info',
          message: this.i18n('acc:content.system.systemSynchronizationConfigs.action.startSynchronization.started',
            {name: json.name})
        });
        this.context.store.dispatch(synchronizationConfigManager.fetchEntity(sync.id));
      }).catch(ex => {
        this.setState({
          showLoading: false
        });
        this.addError(ex);
        this.context.store.dispatch(synchronizationConfigManager.fetchEntity(sync.id));
      });
    }, () => {
      this.setState({
        showLoading: false
      });
    });
  }

  /**
   * Call after system mapping selection was changed. Create filter for correlationAttribute
   */
  _onChangeSystemMapping(systemMapping) {
    const systemMappingId = systemMapping ? systemMapping.id : null;
    const entityType = systemMapping ? systemMapping.entityType : null;
    const isSelectedTree = (entityType === SystemEntityTypeEnum.findKeyBySymbol(SystemEntityTypeEnum.TREE) ||
      entityType === SystemEntityTypeEnum.findKeyBySymbol(SystemEntityTypeEnum.ROLE_CATALOGUE));
    this.setState({
      systemMappingId,
      entityType
    }, () => {
      // clear selected correlationAttribute
      this.refs.correlationAttribute.setValue(null);
      // clear selected tokenAttribute
      if (this.refs.tokenAttribute) {
        this.refs.tokenAttribute.setValue(null);
      }
      // clear selected filterAttribute
      if (this.refs.filterAttribute) {
        this.refs.filterAttribute.setValue(null);
      }

      if (isSelectedTree) {
        this.refs.reconciliation.setValue(true);
      }
    });
  }

  /**
   * Call after filter attribute selection was changed.
   */
  _onChangeFilterAttribute(filterAttribute) {
    if (!this.refs.tokenAttribute.getValue()) {
      this.refs.tokenAttribute.setValue(filterAttribute);
    }
  }

  _generateResultCell(rowIndex, data) {
    const actions = [];
    if (!data[rowIndex].syncActionLogs) {
      return actions;
    }
    const log = data[rowIndex];
    return (
      <SyncResult log={log}/>
    );
  }

  _generateStatisticCell(rowIndex, data) {
    const actions = [];
    if (!data[rowIndex].syncActionLogs) {
      return actions;
    }
    const log = data[rowIndex];
    return (
      <SyncStatistic log={log}/>
    );
  }

  _onChangeSelectTabs(activeKey) {
    this.setState({activeKey});
  }

  _onChangeEnabled(event) {
    this.setState({
      enabled: event.currentTarget.checked
    });
  }

  _onChangeCustomFilterEnable(event) {
    this.setState({
      customFilterUsed: event.currentTarget.checked
    });
  }

  _onChangeReconciliation(event) {
    this.setState({
      reconciliationEnabled: event.currentTarget.checked
    });
  }

  _getEntityType(synchronizationConfig, entityType) {
    if (entityType !== undefined) {
      return entityType;
    }
    if (synchronizationConfig && synchronizationConfig._embedded && synchronizationConfig._embedded.systemMapping) {
      return synchronizationConfig._embedded.systemMapping.entityType;
    }
    return null;
  }

  getHelp() {
    let helpContent = new Domain.HelpContent();
    helpContent = helpContent.setHeader(
      <span>
        <Basic.Icon value="filter"/>
        {this.i18n('help.header')}
      </span>
    );
    helpContent = helpContent.setBody(this.i18n('help.body', {escape: false}));
    //
    return helpContent;
  }

  renderBaseInformation(finalEntityType,
    synchronizationConfig,
    innerShowLoading,
    isSelectedTree,
    isRoleCatalogue,
    customFilterUsed,
    reconciliationEnabled,
    forceSearchMappingAttributes,
    forceSearchCorrelationAttribute,
    forceSearchSyncActionWfKey,
    enabled) {

    const syncId = synchronizationConfig ? synchronizationConfig.id : 'no-id';

    return (
      <form onSubmit={this.save.bind(this, false, false, finalEntityType)}>
        <Basic.Panel className="no-border">
          <Basic.AbstractForm
            ref="form"
            data={synchronizationConfig}
            showLoading={innerShowLoading}
            className="panel-body"
            readOnly={!Managers.SecurityManager.hasAnyAuthority(['SYSTEM_UPDATE'])}>
            <Basic.Checkbox
              ref="enabled"
              label={this.i18n('acc:entity.SynchronizationConfig.enabled')}
              onChange={this._onChangeEnabled.bind(this)}/>
            <Basic.Checkbox
              ref="reconciliation"
              readOnly={isSelectedTree || isRoleCatalogue}
              label={this.i18n('acc:entity.SynchronizationConfig.reconciliation.label')}
              helpBlock={this.i18n('acc:entity.SynchronizationConfig.reconciliation.help')}
              onChange={this._onChangeReconciliation.bind(this)}/>
            <Basic.LabelWrapper
              hidden={!customFilterUsed || !reconciliationEnabled}>
              <Basic.Alert
                level="warning"
                icon="exclamation-sign"
                className="no-margin"
                text={this.i18n(`acc:content.system.systemSynchronizationConfigDetail.customFilterWithReconciliation`)}/>
            </Basic.LabelWrapper>
            <Basic.Checkbox
              ref="differentialSync"
              label={this.i18n('acc:entity.SynchronizationConfig.differentialSync.label')}
              helpBlock={this.i18n('acc:entity.SynchronizationConfig.differentialSync.help')}/>
            <Basic.LabelWrapper
              hidden={!isSelectedTree && !isRoleCatalogue}
              label=" ">
              <Basic.Alert
                key="treeInfo"
                level="warning"
                icon="exclamation-sign"
                className="no-margin"
                text={isRoleCatalogue ? this.i18n('roleCatalogueInfo') : this.i18n('treeInfo')}/>
            </Basic.LabelWrapper>
            <Basic.TextField
              ref="name"
              label={this.i18n('acc:entity.SynchronizationConfig.name')}
              required/>
            <Basic.SelectBox
              ref="systemMapping"
              manager={systemMappingManager}
              forceSearchParameters={forceSearchMappingAttributes}
              onChange={this._onChangeSystemMapping.bind(this)}
              label={this.i18n('acc:entity.SynchronizationConfig.systemMapping')}
              required/>
            <Basic.ScriptArea
              ref="rootsFilterScript"
              height="20em"
              hidden={!isSelectedTree && !isRoleCatalogue}
              helpBlock={this.i18n('acc:entity.SynchronizationConfig.rootsFilterScript.help')}
              label={this.i18n('acc:entity.SynchronizationConfig.rootsFilterScript.label')}/>
            <Basic.SelectBox
              ref="correlationAttribute"
              manager={systemAttributeMappingManager}
              forceSearchParameters={forceSearchCorrelationAttribute}
              label={this.i18n('acc:entity.SynchronizationConfig.correlationAttribute')}
              required/>
            <Basic.TextField
              ref="token"
              label={this.i18n('acc:entity.SynchronizationConfig.token')}/>
            <Basic.TextArea
              ref="description"
              label={this.i18n('acc:entity.SynchronizationConfig.description')}/>
            <Basic.LabelWrapper label=" ">
              <Basic.Alert
                key="situationActionsAndWfInfo"
                icon="exclamation-sign"
                className="no-margin"
                text={this.i18n('situationActionsAndWf')}/>
            </Basic.LabelWrapper>

            <Basic.Panel className="no-border marginable">
              <Advanced.Panel.Header
                uiKey={ `synchronization-detail-${ syncId }-panel-linked-action` }
                text={ this.i18n('acc:entity.SynchronizationConfig.linkedAction') }
                collapsible={ false }/>
              <Basic.PanelBody>
                <Basic.EnumSelectBox
                  className=""
                  ref="linkedAction"
                  enum={SynchronizationLinkedActionTypeEnum}
                  useSymbol={false}
                  label={this.i18n('situationAction')}
                  required/>
                <Basic.SelectBox
                  ref="linkedActionWfKey"
                  label={this.i18n('situationActionWf')}
                  forceSearchParameters={forceSearchSyncActionWfKey}
                  multiSelect={false}
                  manager={workflowProcessDefinitionManager}/>
              </Basic.PanelBody>
            </Basic.Panel>

            <Basic.Panel className="no-border marginable">
              <Advanced.Panel.Header
                uiKey={ `synchronization-detail-${ syncId }-panel-unlinked-action` }
                text={ this.i18n('acc:entity.SynchronizationConfig.unlinkedAction') }
                collapsible={ false }/>
              <Basic.PanelBody>
                <Basic.EnumSelectBox
                  ref="unlinkedAction"
                  enum={SynchronizationUnlinkedActionTypeEnum}
                  useSymbol={false}
                  label={this.i18n('situationAction')}
                  required/>
                <Basic.SelectBox
                  ref="unlinkedActionWfKey"
                  label={this.i18n('situationActionWf')}
                  forceSearchParameters={forceSearchSyncActionWfKey}
                  multiSelect={false}
                  manager={workflowProcessDefinitionManager}/>
              </Basic.PanelBody>
            </Basic.Panel>

            <Basic.Panel className="no-border marginable">
              <Advanced.Panel.Header
                uiKey={ `synchronization-detail-${ syncId }-panel-missing-entity-action` }
                text={ this.i18n('acc:entity.SynchronizationConfig.missingEntityAction') }
                collapsible={ false }/>
              <Basic.PanelBody>
                <Basic.EnumSelectBox
                  ref="missingEntityAction"
                  enum={SynchronizationMissingEntityActionTypeEnum}
                  useSymbol={false}
                  label={this.i18n('situationAction')}
                  required/>
                <Basic.SelectBox
                  ref="missingEntityActionWfKey"
                  label={this.i18n('situationActionWf')}
                  forceSearchParameters={forceSearchSyncActionWfKey}
                  multiSelect={false}
                  manager={workflowProcessDefinitionManager}/>
              </Basic.PanelBody>
            </Basic.Panel>

            <Basic.Panel className="no-border marginable">
              <Advanced.Panel.Header
                uiKey={ `synchronization-detail-${ syncId }-panel-missing-account-action` }
                text={ this.i18n('acc:entity.SynchronizationConfig.missingAccountAction') }
                collapsible={ false }/>
              <Basic.PanelBody>
                <Basic.EnumSelectBox
                  ref="missingAccountAction"
                  enum={ReconciliationMissingAccountActionTypeEnum}
                  useSymbol={false}
                  label={this.i18n('situationAction')}
                  required/>
                <Basic.SelectBox
                  ref="missingAccountActionWfKey"
                  label={this.i18n('situationActionWf')}
                  forceSearchParameters={forceSearchSyncActionWfKey}
                  multiSelect={false}
                  manager={workflowProcessDefinitionManager}/>
              </Basic.PanelBody>
            </Basic.Panel>
          </Basic.AbstractForm>
          <Basic.PanelFooter rendered={!this.isWizard()}>
            <Basic.Button
              type="button"
              level="link"
              onClick={this.context.history.goBack}
              showLoading={innerShowLoading}>
              {this.i18n('button.back')}
            </Basic.Button>
            <Basic.SplitButton
              level="success"
              title={this.i18n('button.saveAndContinue')}
              onClick={this.save.bind(this, false, false, finalEntityType)}
              showLoading={innerShowLoading}
              type="submit"
              showLoadingIcon
              showLoadingText={this.i18n('button.saving')}
              rendered={Managers.SecurityManager.hasAuthority('SYSTEM_UPDATE')}
              pullRight
              dropup>
              <Basic.MenuItem
                eventKey="1"
                rendered={(enabled === null || enabled === true)
                && synchronizationConfig && synchronizationConfig.enabled && Managers.SecurityManager.hasAuthority('SYNCHRONIZATION_CREATE')}
                onClick={this.save.bind(this, true, false, finalEntityType)}>
                {this.i18n('button.saveAndStartSynchronization')}
              </Basic.MenuItem>
              <Basic.MenuItem
                eventKey="2"
                onClick={this.save.bind(this, false, true, finalEntityType)}>
                {this.i18n('button.saveAndClose')}
              </Basic.MenuItem>
            </Basic.SplitButton>
          </Basic.PanelFooter>
        </Basic.Panel>
      </form>
    );
  }

  render() {
    const {_showLoading, _synchronizationConfig} = this.props;
    const isNew = this._getIsNew();
    if (!isNew && !_synchronizationConfig) {
      return null;
    }
    const {systemMappingId, showLoading, activeKey, entityType, enabled} = this.state;
    const innerShowLoading = _showLoading || showLoading;
    const systemId = this.props.match.params.entityId;
    const forceSearchParameters = new Domain.SearchParameters()
      .setFilter('synchronizationConfigId', _synchronizationConfig ? _synchronizationConfig.id : Domain.SearchParameters.BLANK_UUID);
    const forceSearchMappingAttributes = new Domain.SearchParameters().setFilter('systemId', systemId || Domain.SearchParameters.BLANK_UUID);
    const forceSearchSyncActionWfKey = new Domain.SearchParameters().setFilter('category', syncActionWfKey);
    const synchronizationConfig = isNew ? this.state.synchronizationConfig : _synchronizationConfig;
    const attributeMappingIdFromEntity = synchronizationConfig && synchronizationConfig.systemMapping ? synchronizationConfig.systemMapping : null;
    const forceSearchCorrelationAttribute = new Domain.SearchParameters()
      .setFilter('systemMappingId', systemMappingId || attributeMappingIdFromEntity || Domain.SearchParameters.BLANK_UUID);

    let customFilterUsed = this.state.customFilterUsed;
    let reconciliationEnabled = this.state.reconciliationEnabled;
    if (customFilterUsed === undefined) {
      customFilterUsed = synchronizationConfig ? synchronizationConfig.customFilter : undefined;
    }
    if (reconciliationEnabled === undefined) {
      reconciliationEnabled = synchronizationConfig ? synchronizationConfig.reconciliation : undefined;
    }

    let isSelectedTree = false;
    let isRoleCatalogue = false;
    let specificConfiguration = null;
    const finalEntityType = this._getEntityType(synchronizationConfig, entityType);
    if (finalEntityType) {
      if (finalEntityType === SystemEntityTypeEnum.findKeyBySymbol(SystemEntityTypeEnum.TREE)) {
        isSelectedTree = true;
      }
      if (finalEntityType === SystemEntityTypeEnum.findKeyBySymbol(SystemEntityTypeEnum.ROLE_CATALOGUE)) {
        isRoleCatalogue = true;
      }
    }
    if (finalEntityType) {
      if (finalEntityType === SystemEntityTypeEnum.findKeyBySymbol(SystemEntityTypeEnum.CONTRACT)) {
        specificConfiguration = <SyncContractConfig
          ref="formSpecific"
          synchronizationConfig={synchronizationConfig}
          showLoading={innerShowLoading}
          isNew={isNew}
          className="panel-body"/>;
      } else if (finalEntityType === SystemEntityTypeEnum.findKeyBySymbol(SystemEntityTypeEnum.CONTRACT_SLICE)) {
        specificConfiguration = <SyncContractConfig
          ref="formSpecific"
          synchronizationConfig={synchronizationConfig}
          showLoading={innerShowLoading}
          isNew={isNew}
          className="panel-body"/>;
      } else if (finalEntityType === SystemEntityTypeEnum.findKeyBySymbol(SystemEntityTypeEnum.IDENTITY)) {
        specificConfiguration = <SyncIdentityConfig
          ref="formSpecific"
          synchronizationConfig={synchronizationConfig}
          showLoading={innerShowLoading}
          isNew={isNew}
          className="panel-body"/>;
      } else if (finalEntityType === SystemEntityTypeEnum.findKeyBySymbol(SystemEntityTypeEnum.ROLE)) {
        specificConfiguration = <SyncRoleConfig
          ref="formSpecific"
          synchronizationConfig={synchronizationConfig}
          showLoading={innerShowLoading}
          isNew={isNew}
          className="panel-body"/>;
      } else if (finalEntityType === SystemEntityTypeEnum.findKeyBySymbol(SystemEntityTypeEnum.TREE)) {
        specificConfiguration = (
          <SyncTreeConfig
            ref="formSpecific"
            synchronizationConfig={synchronizationConfig}
            showLoading={innerShowLoading}
            isNew={isNew}
            className="panel-body"/>
        );
      }
    }
    return (
      <div>
        <Helmet title={this.i18n('title')}/>
        <Basic.Confirm ref="confirm-delete" level="danger"/>

        <Basic.ContentHeader rendered={!this.isWizard()}>
          <Basic.Icon value="component:synchronization"/>
          {' '}
          <span dangerouslySetInnerHTML={{__html: this.i18n('header')}}/>
        </Basic.ContentHeader>
        <Basic.Tabs activeKey={activeKey} onSelect={this._onChangeSelectTabs.bind(this)}>
          <Basic.Tab eventKey={1} title={this.i18n('tabs.basicConfiguration.label')} className="bordered">
            {this.renderBaseInformation(finalEntityType,
              synchronizationConfig,
              innerShowLoading,
              isSelectedTree,
              isRoleCatalogue,
              customFilterUsed,
              reconciliationEnabled,
              forceSearchMappingAttributes,
              forceSearchCorrelationAttribute,
              forceSearchSyncActionWfKey,
              enabled)}
          </Basic.Tab>
          <Basic.Tab
            rendered={specificConfiguration !== null}
            eventKey={2}
            title={this.i18n('tabs.specificConfiguration.label')}
            className="bordered">
            <form onSubmit={this.save.bind(this)}>
              <Basic.Panel className="no-border">
                {specificConfiguration}
                <Basic.PanelFooter rendered={!this.isWizard()}>
                  <Basic.Button
                    type="button"
                    level="link"
                    onClick={this.context.history.goBack}
                    showLoading={innerShowLoading}>
                    {this.i18n('button.back')}
                  </Basic.Button>
                  <Basic.SplitButton
                    level="success"
                    title={this.i18n('button.saveAndContinue')}
                    onClick={this.save.bind(this, false, false, finalEntityType)}
                    showLoading={innerShowLoading}
                    type="submit"
                    showLoadingIcon
                    showLoadingText={this.i18n('button.saving')}
                    rendered={Managers.SecurityManager.hasAuthority('SYSTEM_UPDATE')}
                    pullRight
                    dropup>
                    <Basic.MenuItem
                      eventKey="1"
                      rendered={(enabled === null || enabled === true)
                      && synchronizationConfig && synchronizationConfig.enabled && Managers.SecurityManager.hasAuthority('SYNCHRONIZATION_CREATE')}
                      onClick={this.save.bind(this, true, false, finalEntityType)}>
                      {this.i18n('button.saveAndStartSynchronization')}
                    </Basic.MenuItem>
                    <Basic.MenuItem
                      eventKey="2"
                      onClick={this.save.bind(this, false, true, finalEntityType)}>
                      {this.i18n('button.saveAndClose')}
                    </Basic.MenuItem>
                  </Basic.SplitButton>
                </Basic.PanelFooter>
              </Basic.Panel>
            </form>
          </Basic.Tab>
          <Basic.Tab eventKey={3} title={this.i18n('tabs.filterConfiguration.label')} className="bordered">
            <form onSubmit={this.save.bind(this)}>
              <Basic.Panel className="no-border">
                <Basic.AbstractForm ref="formFilter" data={synchronizationConfig} showLoading={innerShowLoading} className="panel-body">
                  <Basic.Checkbox
                    ref="customFilter"
                    onChange={this._onChangeCustomFilterEnable.bind(this)}
                    label={this.i18n('acc:entity.SynchronizationConfig.customFilter.label')}
                    helpBlock={this.i18n('acc:entity.SynchronizationConfig.customFilter.help')}/>
                  <Basic.LabelWrapper
                    hidden={!customFilterUsed || !reconciliationEnabled}>
                    <Basic.Alert
                      level="warning"
                      icon="exclamation-sign"
                      className="no-margin"
                      text={this.i18n(`acc:content.system.systemSynchronizationConfigDetail.customFilterWithReconciliation`)}/>
                  </Basic.LabelWrapper>
                  <Basic.LabelWrapper label=" ">
                    <Basic.Alert
                      key="customFilterInfo"
                      icon="exclamation-sign"
                      className="no-margin"
                      text={this.i18n('customFilterInfo')}/>
                  </Basic.LabelWrapper>
                  <Basic.SelectBox
                    ref="filterAttribute"
                    manager={systemAttributeMappingManager}
                    forceSearchParameters={forceSearchCorrelationAttribute}
                    label={this.i18n('acc:entity.SynchronizationConfig.filterAttribute.label')}
                    onChange={this._onChangeFilterAttribute.bind(this)}
                    tooltip={this.i18n('acc:entity.SynchronizationConfig.filterAttribute.help')}
                    readOnly={!customFilterUsed}/>
                  <Basic.EnumSelectBox
                    ref="filterOperation"
                    enum={IcFilterOperationTypeEnum}
                    label={this.i18n('acc:entity.SynchronizationConfig.filterOperation')}
                    required
                    clearable={false}
                    readOnly={!customFilterUsed}/>
                  <Basic.SelectBox
                    ref="tokenAttribute"
                    manager={systemAttributeMappingManager}
                    forceSearchParameters={forceSearchCorrelationAttribute}
                    label={this.i18n('acc:entity.SynchronizationConfig.tokenAttribute.label')}
                    helpBlock={this.i18n('acc:entity.SynchronizationConfig.tokenAttribute.help')}
                    readOnly={!customFilterUsed}/>
                  <Basic.ScriptArea
                    ref="customFilterScript"
                    height="20em"
                    helpBlock={this.i18n('acc:entity.SynchronizationConfig.customFilterScript.help')}
                    label={this.i18n('acc:entity.SynchronizationConfig.customFilterScript.label')}
                    help={this.getHelp()}
                    readOnly={!customFilterUsed}/>
                </Basic.AbstractForm>
                <Basic.PanelFooter rendered={!this.isWizard()}>
                  <Basic.Button
                    type="button"
                    level="link"
                    onClick={this.context.history.goBack}
                    showLoading={innerShowLoading}>
                    {this.i18n('button.back')}
                  </Basic.Button>
                  <Basic.SplitButton
                    level="success"
                    title={this.i18n('button.saveAndContinue')}
                    onClick={this.save.bind(this, false, false, finalEntityType)}
                    showLoading={innerShowLoading}
                    type="submit"
                    showLoadingIcon
                    showLoadingText={this.i18n('button.saving')}
                    rendered={Managers.SecurityManager.hasAuthority('SYSTEM_UPDATE')}
                    pullRight
                    dropup>
                    <Basic.MenuItem
                      eventKey="1"
                      rendered={(enabled === null || enabled === true)
                      && synchronizationConfig && synchronizationConfig.enabled && Managers.SecurityManager.hasAuthority('SYNCHRONIZATION_CREATE')}
                      onClick={this.save.bind(this, true, false, finalEntityType)}>
                      {this.i18n('button.saveAndStartSynchronization')}
                    </Basic.MenuItem>
                    <Basic.MenuItem
                      eventKey="2"
                      onClick={this.save.bind(this, false, true, finalEntityType)}>
                      {this.i18n('button.saveAndClose')}
                    </Basic.MenuItem>
                  </Basic.SplitButton>
                </Basic.PanelFooter>
              </Basic.Panel>
            </form>
          </Basic.Tab>
          <Basic.Tab rendered={!this.isWizard()} eventKey={4} title={this.i18n('tabs.logs.label')}>
            <Basic.ContentHeader rendered={synchronizationConfig !== null} style={{marginBottom: 0, paddingLeft: 15}}>
              <Basic.Icon value="transfer"/>
              {' '}
              <span dangerouslySetInnerHTML={{__html: this.i18n('synchronizationLogsHeader')}}/>
            </Basic.ContentHeader>

            <Advanced.Table
              ref="table"
              uiKey={uiKeyLogs}
              manager={synchronizationLogManager}
              showLoading={innerShowLoading}
              forceSearchParameters={forceSearchParameters}
              showRowSelection={Managers.SecurityManager.hasAnyAuthority(['SYSTEM_UPDATE'])}
              rowClass={({rowIndex, data}) => (data[rowIndex].containsError ? 'danger' : '')}
              actions={
                Managers.SecurityManager.hasAnyAuthority(['SYSTEM_UPDATE'])
                  ?
                  [{value: 'delete', niceLabel: this.i18n('action.delete.action'), action: this.onDelete.bind(this), disabled: false}]
                  :
                  null
              }>
              <Advanced.Column
                property=""
                header=""
                className="detail-button"
                cell={
                  ({rowIndex, data}) => (
                    <Advanced.DetailButton
                      title={this.i18n('button.detail')}
                      onClick={this.showDetail.bind(this, data[rowIndex], false)}/>
                  )
                }/>
              <Advanced.Column property="running" face="boolean" header={this.i18n('acc:entity.SynchronizationLog.running')} sort/>
              <Advanced.Column
                property="syncActionLogs"
                header={this.i18n('acc:entity.SynchronizationLog.results')}
                cell={
                  ({rowIndex, data}) => this._generateResultCell(rowIndex, data)
                }
              />
              <Advanced.Column property="started" face="datetime" header={this.i18n('acc:entity.SynchronizationLog.started')} sort/>
              <Advanced.Column property="ended" face="datetime" header={this.i18n('acc:entity.SynchronizationLog.ended')} sort/>
              <Advanced.Column
                property="statistic"
                header={this.i18n('acc:entity.SynchronizationLog.statistic.label')}
                cell={
                  ({rowIndex, data}) => this._generateStatisticCell(rowIndex, data)
                }
              />
            </Advanced.Table>
          </Basic.Tab>
        </Basic.Tabs>
      </div>
    );
  }
}

SystemSynchronizationConfigDetail.propTypes = {
  _showLoading: PropTypes.bool,
};
SystemSynchronizationConfigDetail.defaultProps = {
  _showLoading: false,
};

function select(state, component) {
  const entity = Utils.Entity.getEntity(state, synchronizationConfigManager.getEntityType(), component.match.params.configId);

  const {query} = component && component.location ? component.location : {};
  const isNew = (query) ? query.new : null;
  const _showLoading = isNew ? false : !entity;

  return {
    _synchronizationConfig: entity,
    _showLoading,
  };
}

export default connect(select)(SystemSynchronizationConfigDetail);
