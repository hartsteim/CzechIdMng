import React from 'react';
//
import { Basic } from 'czechidm-core';
import ProvisioningOperations from './ProvisioningOperations';

/**
 * Provisioning in audit route.
 *
 * @author Radek Tomiška
 */
export default class AuditProvisioningOperations extends Basic.AbstractContent {

  getContentKey() {
    return 'acc:content.provisioningOperations';
  }

  getNavigationKey() {
    return 'provisioning-operations';
  }

  render() {
    return (
      <Basic.Div>
        <Basic.PageHeader text={ this.i18n('header', { escape: false }) }/>

        <ProvisioningOperations uiKey="provisioning-operation-audit-table"/>
      </Basic.Div>
    );
  }
}
