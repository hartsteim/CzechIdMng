import React from 'react';
import {Basic, Advanced, Domain} from 'czechidm-core';
import {SystemMappingManager, SystemAttributeMappingManager, SchemaAttributeManager} from '../../redux';

const systemMappingManager = new SystemMappingManager();
const systemAttributeMappingManager = new SystemAttributeMappingManager();
const schemaAttributeManager = new SchemaAttributeManager();

/**
 * Role's specific sync configuration
 *
 * @author Vít Švanda
 */
class SyncRoleConfig extends Basic.AbstractContent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      ...this.state,
      membershipSwitch: props.synchronizationConfig ? props.synchronizationConfig.membershipSwitch : false,
      assignRoleSwitch: props.synchronizationConfig ? props.synchronizationConfig.assignRoleSwitch : false,
      assignCatalogueSwitch: props.synchronizationConfig ? props.synchronizationConfig.assignCatalogueSwitch : false,
      removeCatalogueRoleSwitch: props.synchronizationConfig ? props.synchronizationConfig.removeCatalogueRoleSwitch : false,
      forwardAcmSwitch: props.synchronizationConfig ? props.synchronizationConfig.forwardAcmSwitch : false,
      skipValueIfExcludedSwitch: props.synchronizationConfig ? props.synchronizationConfig.skipValueIfExcludedSwitch : false,
      roleAttributeSearchParameters: new Domain.SearchParameters()
        .setFilter('systemMappingId', props.synchronizationConfig ? props.synchronizationConfig.systemMapping : Domain.SearchParameters.BLANK_UUID),
      membersAttributeSearchParameters: new Domain.SearchParameters()
        .setFilter('systemMappingId', props.synchronizationConfig && props.synchronizationConfig.memberSystemMapping
          ? props.synchronizationConfig.memberSystemMapping : Domain.SearchParameters.BLANK_UUID),
      identitySchemaAttributeSearchParameters: new Domain.SearchParameters()
        .setFilter('objectClassId', props.synchronizationConfig && props.synchronizationConfig.memberSystemMapping
          ? props.synchronizationConfig._embedded.memberSystemMapping.objectClass : Domain.SearchParameters.BLANK_UUID),
      identityMappingSearchParameters: new Domain.SearchParameters()
        .setFilter('operationType', 'PROVISIONING')
    };
  }

  getContentKey() {
    return 'acc:content.system.systemSynchronizationConfigDetail';
  }

  getData(allData) {
    if (this.refs.formSpecific) {
      return this.refs.formSpecific.getData(allData);
    }
    return null;
  }

  isFormValid() {
    if (this.refs.formSpecific) {
      return this.refs.formSpecific.isFormValid();
    }
    return true;
  }

  _onChangeMemberSystem(systemMapping) {
    const systemMappingId = systemMapping ? systemMapping.id : null;
    const objectClassId = systemMapping ? systemMapping.objectClass : null;
    this.setState({
      treeTypeId: systemMappingId,
      membersAttributeSearchParameters:
        this.state.membersAttributeSearchParameters.setFilter('systemMappingId', systemMappingId || Domain.SearchParameters.BLANK_UUID),
      identitySchemaAttributeSearchParameters:
        this.state.identitySchemaAttributeSearchParameters.setFilter('objectClassId', objectClassId || Domain.SearchParameters.BLANK_UUID)
    }, () => {
      this.refs.memberOfAttribute.setValue(null);
      this.refs.memberIdentifierAttribute.setValue(null);
    });
  }

  _toggleSwitch(key) {
    const state = {};
    state[key] = !this.state[key];

    this.setState(state);
  }

  getMappingNiceLabel(mapping) {
    if (mapping) {
      return mapping._embedded.objectClass._embedded.system.name + ": " + mapping.name;
    }
  }

  render() {
    const {
      identityMappingSearchParameters,
      membersAttributeSearchParameters,
      identitySchemaAttributeSearchParameters,
      membershipSwitch,
      assignRoleSwitch,
      assignCatalogueSwitch,
      removeCatalogueRoleSwitch,
      forwardAcmSwitch,
      skipValueIfExcludedSwitch
    } = this.state;
    const {synchronizationConfig, showLoading} = this.props;

    const hiddenMemberIdentifierAttribute = !assignRoleSwitch
      || membersAttributeSearchParameters.getFilters().get('systemMappingId') === Domain.SearchParameters.BLANK_UUID;
    const memberSystemMappingHidden = !membershipSwitch && !assignRoleSwitch;
    const removeCatalogueRoleParentNodeHidden = !assignCatalogueSwitch || !removeCatalogueRoleSwitch;
    const forwardAcmMappingAttribute = synchronizationConfig
      && synchronizationConfig.forwardAcmMappingAttribute ? synchronizationConfig._embedded.forwardAcmMappingAttribute : null;
    const skipValueIfExcludedMappingAttribute = synchronizationConfig
      && synchronizationConfig.skipValueIfExcludedMappingAttribute ? synchronizationConfig._embedded.skipValueIfExcludedMappingAttribute : null;
    const roleIdentifiersMappingAttribute = synchronizationConfig
      && synchronizationConfig.roleIdentifiersMappingAttribute ? synchronizationConfig._embedded.roleIdentifiersMappingAttribute : null;
    const assignCatalogueMappingAttribute = synchronizationConfig
      && synchronizationConfig.assignCatalogueMappingAttribute ? synchronizationConfig._embedded.assignCatalogueMappingAttribute : null;
    const roleMembersMappingAttribute = synchronizationConfig
      && synchronizationConfig.roleMembersMappingAttribute ? synchronizationConfig._embedded.roleMembersMappingAttribute : null;

    return (
      <Basic.AbstractForm ref="formSpecific" data={synchronizationConfig} showLoading={showLoading} className="panel-body">
        <Basic.ContentHeader text={this.i18n('roleConfigDetail.membershipHeader')} className="marginable"/>
        <Basic.Div style={{display: 'flex', justifyContent: 'start'}}>
          <Basic.Div style={{flex: 1, marginRight: 15}}>
            <Basic.ToggleSwitch
              ref="membershipSwitch"
              onChange={this._toggleSwitch.bind(this, 'membershipSwitch')}
              label={this.i18n('roleConfigDetail.membershipSwitch.label')}
              helpBlock={this.i18n('roleConfigDetail.membershipSwitch.helpBlock')}/>
          </Basic.Div>
          <Basic.Div style={{flex: 1}}>
            <Basic.Alert
              title={this.i18n(`roleConfigDetail.roleIdentifiersMappingAttribute.notfound.title`)}
              text={this.i18n(`roleConfigDetail.roleIdentifiersMappingAttribute.notfound.text`)}
              showHtmlText
              rendered={!!membershipSwitch && !roleIdentifiersMappingAttribute}
              level="warning"
            />
            <span style={{marginLeft: 15}}>
              <strong>{this.i18n(`roleConfigDetail.mappedAttribute`)}: </strong>
              <Advanced.EntityInfo
                level="info"
                popoverTitle={this.i18n(`roleConfigDetail.roleIdentifiersMappingAttribute.found.title`)}
                face="popover"
                rendered={!!roleIdentifiersMappingAttribute}
                entityType="SysSystemAttributeMappingDto"
                entity={roleIdentifiersMappingAttribute}/>
            </span>
          </Basic.Div>
        </Basic.Div>
        <Basic.Div style={{display: 'flex', justifyContent: 'start'}}>
          <Basic.Div style={{flex: 1, marginRight: 15}}>
            <Basic.SelectBox
              ref="memberSystemMapping"
              manager={systemMappingManager}
              required={!memberSystemMappingHidden}
              readOnly={memberSystemMappingHidden}
              niceLabel={(mapping) => this.getMappingNiceLabel(mapping)}
              forceSearchParameters={identityMappingSearchParameters}
              label={this.i18n('roleConfigDetail.memberSystemMapping.label')}
              onChange={this._onChangeMemberSystem.bind(this)}
              helpBlock={this.i18n('roleConfigDetail.memberSystemMapping.helpBlock')}/>
            <Basic.SelectBox
              ref="memberOfAttribute"
              manager={systemAttributeMappingManager}
              required={!memberSystemMappingHidden}
              readOnly={memberSystemMappingHidden}
              niceLabel={(attribute) => `${attribute.name} (${attribute._embedded.systemMapping._embedded.objectClass._embedded.system.name})`}
              forceSearchParameters={membersAttributeSearchParameters}
              label={this.i18n('roleConfigDetail.memberOfAttribute.label')}
              helpBlock={this.i18n('roleConfigDetail.memberOfAttribute.helpBlock')}/>
          </Basic.Div>
          <Basic.Div style={{flex: 1}} />
        </Basic.Div>
        <Basic.ContentHeader text={this.i18n('roleConfigDetail.assignRoleHeader')} className="marginable"/>
        <Basic.Div style={{display: 'flex', justifyContent: 'start'}}>
          <Basic.Div style={{flex: 1}}>
            <Basic.ToggleSwitch
              ref="assignRoleSwitch"
              onChange={this._toggleSwitch.bind(this, 'assignRoleSwitch')}
              label={this.i18n('roleConfigDetail.assignRoleSwitch.label')}
              helpBlock={this.i18n('roleConfigDetail.assignRoleSwitch.helpBlock')}/>
            <Basic.SelectBox
              ref="memberIdentifierAttribute"
              manager={schemaAttributeManager}
              required={!hiddenMemberIdentifierAttribute}
              readOnly={hiddenMemberIdentifierAttribute}
              niceLabel={(attribute) => `${attribute.name} (${attribute._embedded.objectClass._embedded.system.name})`}
              forceSearchParameters={identitySchemaAttributeSearchParameters}
              label={this.i18n('roleConfigDetail.memberIdentifierAttribute.label')}
              helpBlock={this.i18n('roleConfigDetail.memberIdentifierAttribute.helpBlock')}/>
          </Basic.Div>
          <Basic.Div style={{flex: 1}}>
            <Basic.ToggleSwitch
              level="danger"
              style={{marginLeft: 15}}
              ref="assignRoleRemoveSwitch"
              onChange={this._toggleSwitch.bind(this, 'assignRoleRemoveSwitch')}
              readOnly={!assignRoleSwitch}
              label={this.i18n('roleConfigDetail.assignRoleRemoveSwitch.label')}
              helpBlock={this.i18n('roleConfigDetail.assignRoleRemoveSwitch.helpBlock')}/>
            <Basic.Alert
              title={this.i18n(`roleConfigDetail.roleMembersMappingAttribute.notfound.title`)}
              text={this.i18n(`roleConfigDetail.roleMembersMappingAttribute.notfound.text`)}
              showHtmlText
              rendered={!!assignRoleSwitch && !roleMembersMappingAttribute}
              level="warning"
            />
            <span style={{marginLeft: 15}}>
              <strong>{this.i18n(`roleConfigDetail.mappedAttribute`)}: </strong>
              <Advanced.EntityInfo
                level="info"
                popoverTitle={this.i18n(`roleConfigDetail.roleMembersMappingAttribute.found.title`)}
                face="popover"
                rendered={!!roleMembersMappingAttribute}
                entityType="SysSystemAttributeMappingDto"
                entity={roleMembersMappingAttribute}/>
            </span>
            <Basic.Alert
              title={this.i18n(`roleConfigDetail.assignRoleAndDiffSyncWarning.title`)}
              text={this.i18n(`roleConfigDetail.assignRoleAndDiffSyncWarning.text`)}
              showHtmlText
              rendered={!!assignRoleSwitch}
              level="warning"
            />
          </Basic.Div>
        </Basic.Div>
        <Basic.ContentHeader text={this.i18n('roleConfigDetail.roleCatalogueHeader')} className="marginable"/>
        <Basic.Div style={{display: 'flex', justifyContent: 'start'}}>
          <Basic.Div style={{flex: 1}}>
            <Basic.ToggleSwitch
              ref="assignCatalogueSwitch"
              onChange={this._toggleSwitch.bind(this, 'assignCatalogueSwitch')}
              label={this.i18n('roleConfigDetail.assignCatalogueSwitch.label')}
              helpBlock={this.i18n('roleConfigDetail.assignCatalogueSwitch.helpBlock')}/>
            <Advanced.RoleCatalogueSelect
              ref="mainCatalogueRoleNode"
              readOnly={!assignCatalogueSwitch}
              label={this.i18n('roleConfigDetail.mainCatalogueRoleNode.label')}
              helpBlock={this.i18n('roleConfigDetail.mainCatalogueRoleNode.helpBlock')}/>
            <Advanced.RoleCatalogueSelect
              ref="removeCatalogueRoleParentNode"
              required={!removeCatalogueRoleParentNodeHidden}
              readOnly={removeCatalogueRoleParentNodeHidden}
              label={this.i18n('roleConfigDetail.removeCatalogueRoleParentNode.label')}
              helpBlock={this.i18n('roleConfigDetail.removeCatalogueRoleParentNode.helpBlock')}/>
          </Basic.Div>
          <Basic.Div style={{flex: 1}}>
            <Basic.ToggleSwitch
              level="danger"
              style={{marginLeft: 15}}
              ref="removeCatalogueRoleSwitch"
              onChange={this._toggleSwitch.bind(this, 'removeCatalogueRoleSwitch')}
              readOnly={!assignCatalogueSwitch}
              label={this.i18n('roleConfigDetail.removeCatalogueRoleSwitch.label')}
              helpBlock={this.i18n('roleConfigDetail.removeCatalogueRoleSwitch.helpBlock')}/>
            <Basic.Alert
              title={this.i18n(`roleConfigDetail.assignCatalogueMappingAttribute.notfound.title`)}
              text={this.i18n(`roleConfigDetail.assignCatalogueMappingAttribute.notfound.text`)}
              showHtmlText
              rendered={!!assignCatalogueSwitch && !assignCatalogueMappingAttribute}
              level="warning"
            />
            <span style={{marginLeft: 15}}>
              <strong>{this.i18n(`roleConfigDetail.mappedAttribute`)}: </strong>
              <Advanced.EntityInfo
                level="info"
                popoverTitle={this.i18n(`roleConfigDetail.assignCatalogueMappingAttribute.found.title`)}
                face="popover"
                rendered={!!assignCatalogueMappingAttribute}
                entityType="SysSystemAttributeMappingDto"
                entity={assignCatalogueMappingAttribute}/>
            </span>
          </Basic.Div>
        </Basic.Div>
        <Basic.ContentHeader text={this.i18n('roleConfigDetail.otherSettingsHeader')} className="marginable"/>
        <Basic.Div style={{display: 'flex', justifyContent: 'start'}}>
          <Basic.Div style={{flex: 1, marginRight: 15}}>
            <Basic.ToggleSwitch
              ref="forwardAcmSwitch"
              label={this.i18n('roleConfigDetail.forwardAcmSwitch.label')}
              onChange={this._toggleSwitch.bind(this, 'forwardAcmSwitch')}
              helpBlock={this.i18n('roleConfigDetail.forwardAcmSwitch.helpBlock')}/>
          </Basic.Div>
          <Basic.Div style={{flex: 1}}>
            <Basic.Alert
              title={this.i18n(`roleConfigDetail.forwardAcmMappingAttribute.notfound.title`)}
              text={this.i18n(`roleConfigDetail.forwardAcmMappingAttribute.notfound.text`)}
              showHtmlText
              rendered={!!forwardAcmSwitch && !forwardAcmMappingAttribute}
              level="warning"
            />
            <span style={{marginLeft: 15}}>
              <strong>{this.i18n(`roleConfigDetail.mappedAttribute`)}: </strong>
              <Advanced.EntityInfo
                level="info"
                popoverTitle={this.i18n(`roleConfigDetail.forwardAcmMappingAttribute.found.title`)}
                face="popover"
                rendered={!!forwardAcmMappingAttribute}
                entityType="SysSystemAttributeMappingDto"
                entity={forwardAcmMappingAttribute}/>
            </span>
          </Basic.Div>
        </Basic.Div>
        <Basic.Div style={{display: 'flex', justifyContent: 'start'}}>
          <Basic.Div style={{flex: 1, marginRight: 15}}>
            <Basic.ToggleSwitch
              ref="skipValueIfExcludedSwitch"
              onChange={this._toggleSwitch.bind(this, 'skipValueIfExcludedSwitch')}
              label={this.i18n('roleConfigDetail.skipValueIfExcludedSwitch.label')}
              helpBlock={this.i18n('roleConfigDetail.skipValueIfExcludedSwitch.helpBlock')}/>
          </Basic.Div>
          <Basic.Div style={{flex: 1}}>
            <Basic.Alert
              title={this.i18n(`roleConfigDetail.skipValueIfExcludedMappingAttribute.notfound.title`)}
              text={this.i18n(`roleConfigDetail.skipValueIfExcludedMappingAttribute.notfound.text`)}
              showHtmlText
              rendered={!!skipValueIfExcludedSwitch && !skipValueIfExcludedMappingAttribute}
              level="warning"
            />
            <span style={{marginLeft: 15}}>
              <strong>{this.i18n(`roleConfigDetail.mappedAttribute`)}: </strong>
              <Advanced.EntityInfo
                level="info"
                popoverTitle={this.i18n(`roleConfigDetail.skipValueIfExcludedMappingAttribute.found.title`)}
                face="popover"
                rendered={!!skipValueIfExcludedMappingAttribute}
                entityType="SysSystemAttributeMappingDto"
                entity={skipValueIfExcludedMappingAttribute}/>
            </span>
          </Basic.Div>
        </Basic.Div>
      </Basic.AbstractForm>
    );
  }
}

export default SyncRoleConfig;
