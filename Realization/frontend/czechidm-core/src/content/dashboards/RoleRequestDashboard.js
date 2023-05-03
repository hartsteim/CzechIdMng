import React from 'react';
import _ from 'lodash';
import { connect } from 'react-redux';
import * as Basic from '../../components/basic';
import * as Domain from '../../domain';
import { SecurityManager, RoleRequestManager } from '../../redux';
import RoleRequestTableComponent, { RoleRequestTable } from '../requestrole/RoleRequestTable';

const manager = new RoleRequestManager();
const uiKeyCreator = 'creator-role-request-dashboard-table';
const uiKeyApplicant = 'applicant-role-request-dashboard-table';

/**
 * Created role requests and requests "for me".
 *
 * @author Radek Tomiška
 * @since 9.4.0
 */
class RoleRequestDashboard extends Basic.AbstractContent {

  getContentKey() {
    return 'dashboard.role-request';
  }

  render() {
    const { identity, userContext, _totalCreator, _totalApplicant } = this.props;
    //
    if (!identity || !SecurityManager.hasAuthority('ROLEREQUEST_READ')) {
      return null;
    }
    //
    const columns = _.difference(RoleRequestTable.defaultProps.columns, ['modified', 'executeImmediately', 'startRequest', 'createNew']);
    const creatorForceSearch = new Domain.SearchParameters().setFilter('creator', identity.id).setFilter('states', ['IN_PROGRESS', 'APPROVED']).setFilter('includeApprovers', true);
    const applicantForceSearch = new Domain.SearchParameters().setFilter('applicant', identity.id).setFilter('applicantType', 'eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto').setFilter('states', ['IN_PROGRESS', 'APPROVED']).setFilter('includeApprovers', true);
    const isLoggedIdentity = userContext.id === identity.id;
    //
    return (
      <Basic.Div className={ !_totalCreator && !_totalApplicant ? 'hidden' : '' }>
        <Basic.ContentHeader
          icon="component:role-requests"
          text={ this.i18n('header') }/>
        <Basic.Row>
          <Basic.Col lg={ !_totalApplicant ? 12 : 6 } className={ !_totalCreator ? 'hidden' : '' }>
            <Basic.Panel>
              <RoleRequestTableComponent
                ref="table"
                uiKey={ uiKeyCreator }
                manager={ manager }
                showFilter={ false }
                header={ isLoggedIdentity ? this.i18n('creator.header') : this.i18n('creator.identity') }
                forceSearchParameters={ creatorForceSearch }
                createNewRequestFunc={ null }
                startRequestFunc={ null }
                columns={ columns }/>
            </Basic.Panel>
          </Basic.Col>
          <Basic.Col lg={ !_totalCreator ? 12 : 6 } className={ !_totalApplicant ? 'hidden' : '' }>
            <Basic.Panel>
              <RoleRequestTableComponent
                ref="table"
                uiKey={ uiKeyApplicant }
                manager={ manager }
                showFilter={ false }
                header={ isLoggedIdentity ? this.i18n('applicant.header') : this.i18n('applicant.identity') }
                forceSearchParameters={ applicantForceSearch }
                createNewRequestFunc={ null }
                startRequestFunc={ null }
                columns={ columns }/>
            </Basic.Panel>
          </Basic.Col>
        </Basic.Row>
      </Basic.Div>
    );
  }
}

function select(state) {
  const uiCreator = state.data.ui[uiKeyCreator];
  const uiApplicant = state.data.ui[uiKeyApplicant];
  //
  return {
    userContext: state.security.userContext,
    _totalCreator: !uiCreator ? null : uiCreator.total,
    _totalApplicant: !uiApplicant ? null : uiApplicant.total,
    i18nReady: state.config.get('i18nReady')
  };
}

export default connect(select)(RoleRequestDashboard);
