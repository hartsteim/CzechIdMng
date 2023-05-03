import React from 'react';
import _ from 'lodash';
import { connect } from 'react-redux';
//
import { Basic, Advanced, Domain } from 'czechidm-core';
import ProvisioningOperations from '../provisioning/ProvisioningOperations';
import { ProvisioningOperationTable } from '../provisioning/ProvisioningOperationTable';
import AccountManager from '../../redux/AccountManager';
//
const manager = new AccountManager();

/**
* Account detail
*
* @author Roman Kucera
*/
class AccountProvisioning extends Advanced.AbstractTableContent {

  constructor(props, context) {
    super(props, context);
  }

  getContentKey() {
    return 'acc:content.accounts.detail';
  }

  componentDidMount() {
    super.componentDidMount();
    //
    this.selectNavigationItems(['sys-systems-main-menu', 'accounts', 'account-provisioning']);
  }

  getNavigationKey() {
    return 'account-provisioning';
  }

  render() {
    const { entity } = this.props;
    if (!entity) {
      return (
        <Basic.Loading isStatic show />
      );
    }
    const forceSearchParameters = new Domain.SearchParameters()
      .setFilter('entityIdentifier', entity.targetEntityId)
      .setFilter('entityType', entity.targetEntityType)
      .setFilter('systemId', entity.system)
      .setFilter('accountId', entity.id);
    let columns = ProvisioningOperationTable.defaultProps.columns;
    columns = _.difference(columns, ['entityType', 'entityIdentifier']);
    //
    return (
      <Basic.Div>
        <Basic.ContentHeader text={ this.i18n('acc:content.provisioningOperations.header', { escape: false }) }/>

        <ProvisioningOperations
          uiKey="account-provisioning-operation-table"
          forceSearchParameters={ forceSearchParameters }
          columns={ columns }
          showDeleteAllButton={ false }/>
      </Basic.Div>
    );
  }
}

function select(state, component) {
  const { entityId } = component.match.params;
  //
  return {
    entity: manager.getEntity(state, entityId),
  };
}

export default connect(select)(AccountProvisioning);
