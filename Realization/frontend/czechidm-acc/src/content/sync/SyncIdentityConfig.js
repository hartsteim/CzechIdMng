import React from 'react';
//
import { Basic, Advanced } from 'czechidm-core';

import SynchronizationInactiveOwnerBehaviorTypeEnum from '../../domain/SynchronizationInactiveOwnerBehaviorTypeEnum';

/**
 * Identity's specific sync configuration
 *
 * @author Vít Švanda
 */
class SyncIdentityConfig extends Basic.AbstractContent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      defaultRoleId: this.props.synchronizationConfig ? this.props.synchronizationConfig.defaultRole : null
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
    return false;
  }

  _defaultRoleChange(defaultRole) {
    const defaultRoleId = defaultRole;
    this.setState({
      defaultRoleId
    });
  }

  render() {
    const { synchronizationConfig, showLoading, isNew } = this.props;
    const { defaultRoleId } = this.state;
    // Set default values when create new sync configuration
    if (isNew) {
      synchronizationConfig.createDefaultContract = false;
      synchronizationConfig.startAutoRoleRec = true;
      synchronizationConfig.assignDefaultRoleToAll = true;
    }
    //
    return (
      <Basic.AbstractForm ref="formSpecific" data={synchronizationConfig} showLoading={showLoading} className="panel-body">
        <Advanced.RoleSelect
          ref="defaultRole"
          onChange={this._defaultRoleChange.bind(this)}
          label={this.i18n('identityConfigDetail.defaultRole.label')}
          helpBlock={this.i18n('identityConfigDetail.defaultRole.helpBlock')}/>
        <Basic.Checkbox
          ref="assignDefaultRoleToAll"
          hidden={ !defaultRoleId }
          label={this.i18n('identityConfigDetail.assignDefaultRoleToAll.label')}
          helpBlock={this.i18n('identityConfigDetail.assignDefaultRoleToAll.helpBlock')}/>
        <Basic.EnumSelectBox
          ref="inactiveOwnerBehavior"
          useSymbol={ false }
          enum={ SynchronizationInactiveOwnerBehaviorTypeEnum }
          hidden={ !defaultRoleId }
          required={ !!defaultRoleId }
          label={ this.i18n('identityConfigDetail.inactiveOwnerBehavior.label') }
          helpBlock={ this.i18n('identityConfigDetail.inactiveOwnerBehavior.helpBlock') }/>
        <Basic.Checkbox
          ref="startAutoRoleRec"
          label={this.i18n('identityConfigDetail.startAutoRoleRec.label')}
          helpBlock={this.i18n('identityConfigDetail.startAutoRoleRec.helpBlock')}/>
        <Basic.Checkbox
          ref="createDefaultContract"
          label={this.i18n('identityConfigDetail.createDefaultContract.label')}
          helpBlock={this.i18n('identityConfigDetail.createDefaultContract.helpBlock')}/>
      </Basic.AbstractForm>
    );
  }
}

export default SyncIdentityConfig;
