import PropTypes from 'prop-types';
import React from 'react';
import Helmet from 'react-helmet';
import { connect } from 'react-redux';
import Joi from 'joi';
import _ from 'lodash';
//
import uuid from 'uuid';
import { Advanced, Basic, Domain, Enums, Managers, Utils } from 'czechidm-core';
import MappingContextCompleters
from 'czechidm-core/src/content/script/completers/MappingContextCompleters';
import {
  SchemaObjectClassManager,
  SystemAttributeMappingManager,
  SystemManager,
  SystemMappingManager,
  SystemEntityTypeManager
} from '../../redux';
import SystemOperationTypeEnum from '../../domain/SystemOperationTypeEnum';
import ValidationMessageSystemMapping from './ValidationMessageSystemMapping';
import AccountTypeEnum from '../../domain/AccountTypeEnum';

const uiKey = 'system-mappings';
const uiKeyAttributes = 'system-attribute-mappings';
const SYSTEM_MAPPING_VALIDATION = 'SYSTEM_MAPPING_VALIDATION';
const systemAttributeMappingManager = new SystemAttributeMappingManager();
const systemManager = new SystemManager();
const treeTypeManager = new Managers.TreeTypeManager();
const systemMappingManager = new SystemMappingManager();
const schemaObjectClassManager = new SchemaObjectClassManager();
const scriptManager = new Managers.ScriptManager();
const systemEntityTypeManager = new SystemEntityTypeManager();

/**
 * System mapping detail.
 *
 * @author Vít Švanda
 * @author Roman Kucera
 */
class SystemMappingDetail extends Advanced.AbstractTableContent {

  getUiKey() {
    return uiKey;
  }

  getManager() {
    // returns manager for underlying table
    return systemAttributeMappingManager;
  }

  getContentKey() {
    return 'acc:content.system.mappingDetail';
  }

  /**
   * Override because of validation message (_showValidateSystemMessage)
   */
  _onDelete(bulkActionValue, selectedRows) {
    const selectedEntities = this.getManager().getEntitiesByIds(this.context.store.getState(), selectedRows);
    //
    this.refs[`confirm-${bulkActionValue}`].show(
      this.i18n(`action.${bulkActionValue}.message`,
        {
          count: selectedEntities.length,
          record: this.getManager().getNiceLabel(selectedEntities[0]),
          records: this.getManager().getNiceLabels(selectedEntities).join(', ')
        }),
      this.i18n(`action.${bulkActionValue}.header`,
        {
          count: selectedEntities.length,
          records: this.getManager().getNiceLabels(selectedEntities).join(', ')
        })
    ).then(() => {
      this.context.store.dispatch(this.getManager().deleteEntities(selectedEntities, this.getUiKey(), (entity, error) => {
        if (entity && error) {
          if (error.statusCode !== 202) {
            this.addErrorMessage({title: this.i18n(`action.delete.error`, {record: this.getManager().getNiceLabel(entity)})}, error);
          } else {
            this.addError(error);
          }
        } else {
          this.refs.table.reload();
          this._showValidateSystemMessage(this.props.match.params.mappingId);
        }
      }));
    }, () => {
      // nothing
    });
  }

  showDetail(entity, add) {
    const mappingId = this.props._mapping.id;
    const systemId = this.props._mapping.system.id;
    const objectClassId = this.props._mapping.objectClass.id;
    if (add) {
      // If is in the wizard, then active step will be change to this attribute.
      if (this.isWizard()) {
        const activeStep = this.context.wizardContext.activeStep;
        if (activeStep) {
          activeStep.id = 'mappingAttributeNew';
          activeStep.mapping = this.props._mapping;
          activeStep.objectClass = this.props._mapping.objectClass;
          this.context.wizardContext.wizardForceUpdate();
        }
      } else {
        const uuidId = uuid.v1();
        this.context.history.push(`/system/${systemId}/attribute-mappings/${uuidId}/new?new=1&mappingId=${mappingId}&objectClassId=${objectClassId}`);
      }
    } else if (this.isWizard()) {
      // If is in the wizard, then active step will be change to this attribute.
      const activeStep = this.context.wizardContext.activeStep;
      if (activeStep) {
        activeStep.id = 'mappingAttribute';
        activeStep.attribute = entity;
        this.context.wizardContext.wizardForceUpdate();
      }
    } else {
      this.context.history.push(`/system/${systemId}/attribute-mappings/${entity.id}/detail`);
    }
  }

  // @Deprecated - since V10 ... replaced by dynamic key in Route
  // UNSAFE_componentWillReceiveProps(nextProps) {
  //   const { mappingId } = nextProps.match.params;
  //   if (mappingId && mappingId !== this.props.match.params.mappingId) {
  //     this._initComponent(nextProps);
  //   }
  // }

  // Did mount only call initComponent method
  componentDidMount() {
    this._initComponent(this.props);
  }

  /**
   * Method for init component from didMount method and from willReceiveProps method
   * @param props properties of component - props For didmount call is this.props for call from willReceiveProps is nextProps.
   */
  _initComponent(props) {
    const {entityId, mappingId} = props.match.params;
    // fetch system
    this.context.store.dispatch(systemManager.fetchEntity(entityId));

    if (this._getIsNew(props)) {
      this.setState({
        mapping: {
          name: 'Mapping',
          system: entityId,
          entityType: 'IDENTITY',
          operationType: SystemOperationTypeEnum.findKeyBySymbol(SystemOperationTypeEnum.PROVISIONING),
          accountType: AccountTypeEnum.findKeyBySymbol(AccountTypeEnum.PERSONAL)
        }
      });
    } else {
      this.context.store.dispatch(systemMappingManager.fetchEntity(mappingId));
    }
    this.selectNavigationItems(['sys-systems', 'system-mappings']);
    if (!this._getIsNew(props)) {
      this._showValidateSystemMessage(mappingId);
    }
    if (this.refs.name) {
      this.refs.name.focus();
    }
  }

  /**
   * Saves give entity
   */
  save(event) {
    if (event) {
      event.preventDefault();
    }
    if (!this.refs.form.isFormValid()) {
      return;
    }
    if (this.refs.formAcm) {
      const formAcmValid = this.refs.formAcm.isFormValid();
      if (!formAcmValid) {
        this.setState({activeKey: 2});
        return;
      }
    }
    if (this.refs.formProvisioningContext) {
      if (!this.refs.formProvisioningContext.isFormValid()) {
        this.setState({activeKey: 3});
        return;
      }
    }

    const formEntity = this.refs.form.getData();
    if (this.refs.formAcm) {
      const acmData = this.refs.formAcm.getData(false);
      // Merge specific data to form.
      _.merge(formEntity, acmData);
    }

    if (this.refs.formProvisioningContext) {
      const mappingContextData = this.refs.formProvisioningContext.getData(false);
      // Merge context data to form.
      _.merge(formEntity, mappingContextData);
    }
    const payload = {
      ...formEntity,
      entityType: formEntity?.entityType || this.props._mapping?.entityType || this.state._entityType || this.state.mapping?.entityType
    };

    if (formEntity.id === undefined) {
      this.context.store.dispatch(systemMappingManager.createEntity(payload, `${uiKey}-detail`, (createdEntity, error) => {
        this.afterSave(createdEntity, error);
        if (!error && this.refs.table) {
          this.refs.table.reload();
        }
      }));
    } else {
      this.context.store.dispatch(systemMappingManager.updateEntity(payload, `${uiKey}-detail`, this.afterSave.bind(this)));
    }
    if (!this._getIsNew()) {
      this._showValidateSystemMessage(this.props.match.params.mappingId);
    }
  }

  afterSave(entity, error) {
    if (!error) {
      if (this._getIsNew()) {
        this.addMessage({
          message: this.i18n('create.success', {
            entityType: entity.entityType,
            operationType: entity.operationType
          })
        });
      } else {
        this.addMessage({
          message: this.i18n('save.success', {
            entityType: entity.entityType,
            operationType: entity.operationType
          })
        });
      }
      const {entityId} = this.props.match.params;
      // Complete wizard step.
      // Set new entity to the wizard context and go to next step.
      if (this.isWizard()) {
        const wizardContext = this.context.wizardContext;
        wizardContext.mapping = entity;
        if (wizardContext.callBackNext) {
          wizardContext.callBackNext();
        } else if (wizardContext.onClickNext) {
          wizardContext.onClickNext(false, true);
        }
      } else {
        this.context.history.replace(`/system/${entityId}/mappings/${entity.id}/detail`, {mappingId: entity.id});
      }
    } else {
      this.addError(error);
    }
    super.afterSave();
  }

  goBack() {
    if (this.isWizard()) {
      // If is component in the wizard, then set new ID (master component)
      // to the active action and render wizard.
      const activeStep = this.context.wizardContext.activeStep;
      if (activeStep) {
        activeStep.id = 'mappingAttributes';
        this.context.wizardContext.wizardForceUpdate();
      }
    } else {
      this.context.history.goBack();
    }
  }

  /**
   * This method is call from the wizard if next action was executed.
   */
  wizardNext() {
    if (!this.isWizard()) {
      return;
    }
    if (this.props.showOnlyAttributes) {
      const wizardContext = this.context.wizardContext;
      if (wizardContext.callBackNext) {
        wizardContext.callBackNext();
      }
    } else {
      this.save();
    }
  }

  _getIsNew(nextProps) {
    if ((nextProps && nextProps.location) || this.props.location) {
      const {query} = nextProps ? nextProps.location : this.props.location;
      return (query) ? query.new : null;
    }
    return false;
  }

  _getBoolColumn(value) {
    return (<Basic.BooleanCell
      propertyValue={value !== null && value !== ''}
      className="column-face-bool"/>);
  }

  _onChangeEntityType(entity) {
    this.setState({_entityType: entity.value});
  }

  _onChangeOperationType(entity) {
    this.setState({_operationType: entity.value});
  }

  _showValidateSystemMessage(mappingId) {
    systemMappingManager.validate(mappingId)
      .then(response => {
        if (response.status === 204) {
          return {};
        }
        return response.json();
      })
      .then(json => {
        if (Utils.Response.hasError(json)) {
          const error = Utils.Response.getFirstError(json);
          this.setState({
            validationError: error
          });
          throw error;
        }
      })
      .catch(error => {
        if (error.statusEnum === SYSTEM_MAPPING_VALIDATION) {
          this.addErrorMessage({hidden: true}, error);
        } else {
          this.addError(error);
        }
      });
  }

  renderAttributesTable(showOnlyMapping, mapping, isNew, forceSearchParameters, systemId) {

    let nameColumn = <Advanced.ColumnLink
      to={`/system/${systemId}/attribute-mappings/:id/detail`}
      property="name"
      header={this.i18n('acc:entity.SystemAttributeMapping.name.label')}
      sort/>;
    if (this.isWizard()) {
      nameColumn = <Advanced.Column
        property="name"
        header={this.i18n('acc:entity.SystemAttributeMapping.name.label')}
        sort/>;
    }
    return (
      <span>
        <Basic.ContentHeader
          rendered={!showOnlyMapping && mapping && !isNew}
          style={{marginBottom: 0, paddingLeft: 15}}>
          <Basic.Icon value="list-alt"/>
          {' '}
          <span
            dangerouslySetInnerHTML={{__html: this.i18n('systemAttributesMappingHeader')}}/>
        </Basic.ContentHeader>
        <Basic.Panel
          rendered={!showOnlyMapping && mapping && !isNew}
          className="no-border last">
          <Advanced.Table
            ref="table"
            uiKey={uiKeyAttributes}
            manager={systemAttributeMappingManager}
            forceSearchParameters={forceSearchParameters}
            showRowSelection={Managers.SecurityManager.hasAnyAuthority(['SYSTEM_UPDATE'])}
            rowClass={({rowIndex, data}) => {
              return data[rowIndex].disabledAttribute ? 'disabled' : '';
            }}
            actions={
              Managers.SecurityManager.hasAnyAuthority(['SYSTEM_UPDATE'])
                ?
                [{
                  value: 'delete',
                  niceLabel: this.i18n('action.delete.action'),
                  action: this.onDelete.bind(this),
                  disabled: false
                }]
                :
                null
            }
            buttons={
              [
                <Basic.Button
                  level="success"
                  key="add_button"
                  className="btn-xs"
                  onClick={this.showDetail.bind(this, {}, true)}
                  rendered={Managers.SecurityManager.hasAnyAuthority(['SYSTEM_UPDATE'])}>
                  <Basic.Icon type="fa" icon="plus"/>
                  {' '}
                  {this.i18n('button.add')}
                </Basic.Button>
              ]
            }
            filter={
              <Advanced.Filter onSubmit={this.useFilter.bind(this)}>
                <Basic.AbstractForm ref="filterForm">
                  <Basic.Row className="last">
                    <div className="col-lg-6">
                      <Advanced.Filter.TextField
                        ref="idmPropertyName"
                        placeholder={this.i18n('filter.idmPropertyName.placeholder')}/>
                    </div>
                    <div className="col-lg-2"/>
                    <div className="col-lg-4 text-right">
                      <Advanced.Filter.FilterButtons
                        cancelFilter={this.cancelFilter.bind(this)}/>
                    </div>
                  </Basic.Row>
                </Basic.AbstractForm>
              </Advanced.Filter>
            }>
            <Advanced.Column
              property=""
              header=""
              className="detail-button"
              cell={
                ({rowIndex, data}) => {
                  return (
                    <Advanced.DetailButton
                      title={this.i18n('button.detail')}
                      onClick={this.showDetail.bind(this, data[rowIndex], false)}/>
                  );
                }
              }/>
            {nameColumn}
            <Advanced.Column
              property="idmPropertyName"
              header={this.i18n('acc:entity.SystemAttributeMapping.idmPropertyName.label')}
              sort/>
            <Advanced.Column
              property="uid"
              face="boolean"
              header={this.i18n('acc:entity.SystemAttributeMapping.uid.label')}
              sort/>
            <Advanced.Column
              property="entityAttribute"
              face="boolean"
              header={this.i18n('acc:entity.SystemAttributeMapping.entityAttribute')}
              sort/>
            <Advanced.Column
              property="extendedAttribute"
              face="boolean"
              header={this.i18n('acc:entity.SystemAttributeMapping.extendedAttribute.label')}
              sort/>
            <Advanced.Column
              property="transformationFromResource"
              rendered={!this.isWizard()}
              face="boolean"
              header={this.i18n('acc:entity.SystemAttributeMapping.transformationFromResource')}
              cell={
                ({rowIndex, data}) => {
                  return this._getBoolColumn(data[rowIndex].transformFromResourceScript);
                }
              }/>
            <Advanced.Column
              property="transformationToResource"
              rendered={!this.isWizard()}
              face="boolean"
              header={this.i18n('acc:entity.SystemAttributeMapping.transformationToResource')}
              cell={
                ({rowIndex, data}) => {
                  return this._getBoolColumn(data[rowIndex].transformToResourceScript);
                }
              }/>
          </Advanced.Table>
        </Basic.Panel>
      </span>);
  }

  render() {
    const {
      _showLoading,
      _mapping,
      showOnlyAttributes,
      showOnlyMapping
    } = this.props;
    const {
      _entityType,
      activeKey,
      validationError,
      _operationType
    } = this.state;
    const isNew = this._getIsNew();
    const mapping = isNew ? this.state.mapping : _mapping;

    let isSelectedTree = false;
    if (_entityType !== undefined) {
      if (_entityType === 'TREE') {
        isSelectedTree = true;
      }
    } else if (mapping && mapping.entityType === 'TREE') {
      isSelectedTree = true;
    }

    let isSelectedIdentity = false;
    if (_entityType !== undefined) {
      if (_entityType === 'IDENTITY') {
        isSelectedIdentity = true;
      }
    } else if (mapping && mapping.entityType === 'IDENTITY') {
      isSelectedIdentity = true;
    }

    let operationTypeToFilter = SystemOperationTypeEnum.findKeyBySymbol(SystemOperationTypeEnum.SYNCHRONIZATION);
    let isSelectedProvisioning = false;
    if (mapping && mapping.operationType === 'PROVISIONING') {
      isSelectedProvisioning = true;
      operationTypeToFilter = SystemOperationTypeEnum.findKeyBySymbol(SystemOperationTypeEnum.PROVISIONING);
    }

    if (_operationType !== undefined) {
      operationTypeToFilter = _operationType;
    }

    const systemId = this.props.match.params.entityId;
    const forceSearchParameters = new Domain.SearchParameters()
      .setFilter('systemMappingId', _mapping ? _mapping.id : Domain.SearchParameters.BLANK_UUID);
    const objectClassSearchParameters = new Domain.SearchParameters().setFilter('systemId', systemId || Domain.SearchParameters.BLANK_UUID);
    const forceSearchMappings = new Domain.SearchParameters()
      .setFilter('operationType', operationTypeToFilter === SystemOperationTypeEnum.findKeyBySymbol(SystemOperationTypeEnum.PROVISIONING) ?
        SystemOperationTypeEnum.findKeyBySymbol(SystemOperationTypeEnum.SYNCHRONIZATION) : SystemOperationTypeEnum.findKeyBySymbol(SystemOperationTypeEnum.PROVISIONING))
      .setFilter('systemId', systemId || Domain.SearchParameters.BLANK_UUID);

    return (
      <div>
        <Helmet title={this.i18n('title')}/>
        <Basic.Confirm ref="confirm-delete" level="danger"/>

        {showOnlyAttributes ? this.renderAttributesTable(showOnlyMapping, mapping, isNew, forceSearchParameters, systemId) : null}

        <Basic.ContentHeader rendered={!showOnlyAttributes}>
          <span dangerouslySetInnerHTML={{__html: this.i18n('header')}}/>
        </Basic.ContentHeader>
        <Basic.Tabs rendered={!showOnlyAttributes} activeKey={activeKey}>
          <Basic.Tab
            eventKey={1}
            title={this.i18n('title')}
            className="bordered">
            <form onSubmit={this.save.bind(this)}>
              <Basic.Panel className="no-border">
                <Basic.AbstractForm
                  ref="form"
                  className="panel-body"
                  data={mapping}
                  showLoading={_showLoading}
                  readOnly={!Managers.SecurityManager.hasAnyAuthority(['SYSTEM_UPDATE'])}>
                  <ValidationMessageSystemMapping error={validationError}/>
                  <Basic.SelectBox
                    ref="system"
                    manager={systemManager}
                    hidden
                    label={this.i18n('acc:entity.SystemMapping.system')}
                    readOnly/>
                  <Basic.EnumSelectBox
                    ref="operationType"
                    enum={SystemOperationTypeEnum}
                    onChange={this._onChangeOperationType.bind(this)}
                    label={this.i18n('acc:entity.SystemMapping.operationType')}
                    required
                    clearable={false}/>
                  <Basic.SelectBox
                    ref="connectedSystemMappingId"
                    manager={systemMappingManager}
                    forceSearchParameters={forceSearchMappings}
                    label={this.i18n('acc:entity.SystemMapping.connectedMapping')}
                    placeholder={systemId ? null : this.i18n('systemMapping.systemPlaceholder')}/>
                  <Basic.TextField
                    ref="name"
                    label={this.i18n('acc:entity.SystemMapping.name')}
                    required/>
                  <Basic.SelectBox
                    ref="objectClass"
                    manager={schemaObjectClassManager}
                    useFirst
                    forceSearchParameters={objectClassSearchParameters}
                    label={this.i18n('acc:entity.SystemMapping.objectClass')}
                    readOnly={!Utils.Entity.isNew(mapping)}
                    required
                    clearable={false}/>
                  <Basic.SelectBox
                    ref="entityType"
                    manager={systemEntityTypeManager}
                    onChange={this._onChangeEntityType.bind(this)}
                    label={this.i18n('acc:entity.SystemMapping.entityType')}
                    required
                    clearable={false}/>
                  <Basic.SelectBox
                    ref="treeType"
                    label={this.i18n('acc:entity.SystemMapping.treeType')}
                    hidden={!isSelectedTree}
                    required={isSelectedTree}
                    manager={treeTypeManager}
                  />
                  <Basic.EnumSelectBox
                    ref="accountType"
                    enum={AccountTypeEnum}
                    label={this.i18n('acc:entity.Account.accountType')}
                    hidden={!isSelectedIdentity}
                    required={isSelectedIdentity}/>
                  <Basic.Checkbox
                    ref="protectionEnabled"
                    label={this.i18n('acc:entity.SystemMapping.protectionEnabled')}
                    helpBlock={this.i18n('protectionEnabled.help')}
                    hidden={!isSelectedIdentity || !isSelectedProvisioning || isNew}
                  />
                  <Basic.TextField
                    style={{maxWidth: '300px'}}
                    ref="protectionInterval"
                    validation={Joi.number()
                      .allow(null)
                      .integer()
                      .min(1)
                      .max(2147483647)}
                    label={this.i18n('acc:entity.SystemMapping.protectionInterval')}
                    hidden={!isSelectedIdentity || !isSelectedProvisioning || isNew}
                  />
                </Basic.AbstractForm>
                <Basic.PanelFooter rendered={!this.isWizard()}>
                  <Basic.Button
                    type="button"
                    level="link"
                    onClick={this.goBack.bind(this)}
                    showLoading={_showLoading}>
                    {this.i18n('button.back')}
                  </Basic.Button>
                  <Basic.Button
                    level="success"
                    type="submit"
                    showLoading={_showLoading}
                    rendered={Managers.SecurityManager.hasAnyAuthority(['SYSTEM_UPDATE'])}>
                    {this.i18n('button.saveAndContinue')}
                  </Basic.Button>
                </Basic.PanelFooter>
              </Basic.Panel>
            </form>
            {this.renderAttributesTable(showOnlyMapping, mapping, isNew, forceSearchParameters, systemId)}
          </Basic.Tab>
          <Basic.Tab
            eventKey={2}
            title={this.i18n('acc:entity.SystemMapping.account-management')}
            rendered={isSelectedProvisioning}
            className="bordered">
            <form onSubmit={this.save.bind(this)}>
              <Basic.Panel className="no-border">
                <Basic.AbstractForm
                  ref="formAcm"
                  className="panel-body"
                  data={mapping}
                  showLoading={_showLoading}
                  readOnly={!Managers.SecurityManager.hasAnyAuthority(['SYSTEM_UPDATE'])}>
                  <Advanced.ScriptArea
                    ref="canBeAccountCreatedScript"
                    scriptCategory={Enums.ScriptCategoryEnum.findKeyBySymbol(Enums.ScriptCategoryEnum.TRANSFORM_TO)}
                    headerText={this.i18n('acc:entity.SystemAttributeMapping.transformToResourceScriptSelectBox.label')}
                    helpBlock={this.i18n('acc:entity.SystemMapping.canBeAccountCreatedScript.help')}
                    label={this.i18n('acc:entity.SystemMapping.canBeAccountCreatedScript.label')}
                    scriptManager={scriptManager}/>
                </Basic.AbstractForm>
                <Basic.PanelFooter rendered={!this.isWizard()}>
                  <Basic.Button
                    type="button"
                    level="link"
                    onClick={this.goBack.bind(this)}
                    showLoading={_showLoading}>
                    {this.i18n('button.back')}
                  </Basic.Button>
                  <Basic.Button
                    level="success"
                    type="submit"
                    showLoading={_showLoading}
                    rendered={Managers.SecurityManager.hasAnyAuthority(['SYSTEM_UPDATE'])}>
                    {this.i18n('button.saveAndContinue')}
                  </Basic.Button>
                </Basic.PanelFooter>
              </Basic.Panel>
            </form>
          </Basic.Tab>
          <Basic.Tab
            eventKey={3}
            title={this.i18n('acc:entity.SystemMapping.mappingContext.tab.title')}
            rendered={isSelectedProvisioning}
            className="bordered">
            <form onSubmit={this.save.bind(this)}>
              <Basic.Panel className="no-border">
                <Basic.AbstractForm
                  ref="formProvisioningContext"
                  className="panel-body"
                  data={mapping}
                  showLoading={_showLoading}
                  readOnly={!Managers.SecurityManager.hasAnyAuthority(['SYSTEM_UPDATE'])}>
                  <Basic.Checkbox
                    ref="addContextContracts"
                    hidden={!isSelectedIdentity}
                    label={this.i18n('acc:entity.SystemMapping.mappingContext.addContextContracts.label')}
                    helpBlock={this.i18n('acc:entity.SystemMapping.mappingContext.addContextContracts.help', {escape: false})}
                  />
                  <Basic.Checkbox
                    ref="addContextIdentityRoles"
                    hidden={!isSelectedIdentity}
                    label={this.i18n('acc:entity.SystemMapping.mappingContext.addContextIdentityRoles.label')}
                    helpBlock={this.i18n('acc:entity.SystemMapping.mappingContext.addContextIdentityRoles.help', {escape: false})}
                  />
                  <Basic.Checkbox
                    ref="addContextIdentityRolesForSystem"
                    hidden={!isSelectedIdentity}
                    label={this.i18n('acc:entity.SystemMapping.mappingContext.addContextIdentityRolesForSystem.label')}
                    helpBlock={this.i18n('acc:entity.SystemMapping.mappingContext.addContextIdentityRolesForSystem.help', {escape: false})}
                  />
                  <Basic.Checkbox
                    ref="addContextConnectorObject"
                    label={this.i18n('acc:entity.SystemMapping.mappingContext.addContextConnectorObject.label')}
                    helpBlock={this.i18n('acc:entity.SystemMapping.mappingContext.addContextConnectorObject.help', {escape: false})}
                  />
                  <Basic.LabelWrapper
                    label=" ">
                    <Basic.Alert
                      key="suggestionInfo"
                      level="info"
                      icon="exclamation-sign"
                      className="no-margin"
                      text={this.i18n('acc:entity.SystemMapping.mappingContext.suggestionInfo.label')}/>
                  </Basic.LabelWrapper>
                  <Advanced.ScriptArea
                    ref="mappingContextScript"
                    completers={MappingContextCompleters.getCompleters()}
                    scriptCategory={Enums.ScriptCategoryEnum.findKeyBySymbol(Enums.ScriptCategoryEnum.MAPPING_CONTEXT)}
                    headerText={this.i18n('acc:entity.SystemMapping.mappingContext.scriptSelectBox.label')}
                    helpBlock={this.i18n('acc:entity.SystemMapping.mappingContext.script.help')}
                    label={this.i18n('acc:entity.SystemMapping.mappingContext.script.label')}
                    scriptManager={scriptManager}/>
                </Basic.AbstractForm>
                <Basic.PanelFooter rendered={!this.isWizard()}>
                  <Basic.Button
                    type="button"
                    level="link"
                    onClick={this.goBack.bind(this)}
                    showLoading={_showLoading}>
                    {this.i18n('button.back')}
                  </Basic.Button>
                  <Basic.Button
                    level="success"
                    type="submit"
                    showLoading={_showLoading}
                    rendered={Managers.SecurityManager.hasAnyAuthority(['SYSTEM_UPDATE'])}>
                    {this.i18n('button.saveAndContinue')}
                  </Basic.Button>
                </Basic.PanelFooter>
              </Basic.Panel>
            </form>
          </Basic.Tab>
        </Basic.Tabs>
      </div>
    );
  }
}

SystemMappingDetail.propTypes = {
  _showLoading: PropTypes.bool,
  showOnlyAttributes: PropTypes.bool,
  showOnlyMapping: PropTypes.bool
};
SystemMappingDetail.defaultProps = {
  _showLoading: false,
  showOnlyAttributes: false,
  showOnlyMapping: false
};

function select(state, component) {
  const {mappingId, entityId} = component.match.params;
  const entity = Utils.Entity.getEntity(state, systemMappingManager.getEntityType(), mappingId);
  const system = systemManager.getEntity(state, entityId);

  if (entity && entity._embedded && entity._embedded.objectClass) {
    entity.system = system;
    entity.objectClass = entity._embedded.objectClass;
    entity.treeType = entity._embedded.treeType;
  }
  return {
    _mapping: entity,
    _showLoading: Utils.Ui.isShowLoading(state, `${uiKey}-detail`),
  };
}

export default connect(select)(SystemMappingDetail);
