import React from 'react';
import { connect } from 'react-redux';

import { Basic, Domain } from 'czechidm-core';
import { AccountManager } from '../../redux';
import AccountTable from './AccountTable';

/**
 * List of personal accounts.
 *
 * @author Tomáš Doischer
 */
class AccountsPersonal extends Basic.AbstractContent {

  constructor(props, context) {
    super(props, context);
    //
    this.accountManager = new AccountManager();
  }

  getContentKey() {
    return 'acc:content.accounts.personal';
  }

  getNavigationKey() {
    return 'accounts-personal';
  }

  render() {
    const defaultSearchParameters = new Domain.SearchParameters()

    return (
      <Basic.Div>
        <Basic.Panel>
          <AccountTable
            history={ this.context.history }
            uiKey="account-table"
            accountManager={ this.accountManager }
            filterOpened
            forceSearchParameters={ defaultSearchParameters }
            showRowSelection />
        </Basic.Panel>
      </Basic.Div>
    );
  }
}

AccountsPersonal.propTypes = {
};
AccountsPersonal.defaultProps = {
};

function select() {
  return {
  };
}

export default connect(select)(AccountsPersonal);
