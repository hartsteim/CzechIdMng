import React from 'react';
import PropTypes from 'prop-types';
import Helmet from 'react-helmet';
import { connect } from 'react-redux';
import _ from 'lodash';
import uuid from 'uuid';
//
import * as Basic from '../../components/basic';
import * as Advanced from '../../components/advanced';
import * as Utils from '../../utils';
import SearchParameters from '../../domain/SearchParameters';
import {
  IdentityRoleManager,
  IdentityContractManager,
  IdentityManager,
  WorkflowTaskInstanceManager,
  WorkflowProcessInstanceManager,
  SecurityManager,
  RoleRequestManager,
  CodeListManager,
  ConfigurationManager,
  LongPollingManager
} from '../../redux';
import RoleRequestTable from '../requestrole/RoleRequestTable';
import IdentityRoleTableComponent, { IdentityRoleTable } from './IdentityRoleTable';
import IdentitiesInfo from './IdentitiesInfo';

const uiKey = 'identity-roles';
const uiKeyContracts = 'role-identity-contracts';
const identityRoleManager = new IdentityRoleManager();
const identityManager = new IdentityManager();
const identityContractManager = new IdentityContractManager();
const workflowProcessInstanceManager = new WorkflowProcessInstanceManager();
const roleRequestManager = new RoleRequestManager();
const workflowTaskInstanceManager = new WorkflowTaskInstanceManager();
const codeListManager = new CodeListManager();
const uiKeyIncompatibleRoles = 'identity-incompatible-roles-';

/**
 * Assigned identity roles
 * Created role requests
 * Roles in approval (wf)
 *
 * @author VS
 * @author Radek Tomiška
 */
class IdentityRoles extends Basic.AbstractContent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      activeKey: 1,
      longPollingInprogress: false,
      automaticRefreshOn: true,
      requestControllers: [] // Contains array of requests controllers for abort a request.
    };
    this.canSendLongPollingRequest = false;
  }

  getContentKey() {
    return 'content.identity.roles';
  }

  getNavigationKey() {
    return 'profile-roles';
  }

  componentDidMount() {
    super.componentDidMount();
    //
    const { entityId } = this.props.match.params;
    this.context.store.dispatch(
      identityContractManager.fetchEntities(
        new SearchParameters(SearchParameters.NAME_AUTOCOMPLETE)
          .setFilter('identity', entityId)
          .setFilter('validNowOrInFuture', true)
          .setFilter('addPermissions', true),
        `${ uiKeyContracts }-${ entityId }`,
        () => {
          this.context.store.dispatch(identityManager.fetchIncompatibleRoles(entityId, `${ uiKeyIncompatibleRoles }${ entityId }`));
          this.context.store.dispatch(codeListManager.fetchCodeListIfNeeded('environment'));
          // Allow chcek of unresolved requests
          this.canSendLongPollingRequest = true;
          this._initComponent(this.props);
        }
      )
    );
  }

  // @Deprecated - since V10 ... replaced by dynamic key in Route
  // UNSAFE_componentWillReceiveProps(props) {
  //   this._initComponent(props);
  // }

  componentWillUnmount() {
    const {requestControllers} = this.state;
    super.componentWillUnmount();
    // Stop rquest of check rquests (next long-polling request will be not created)
    this.canSendLongPollingRequest = false;
    // Send abort signal to all registered requests in this component.
    requestControllers.forEach(controller => {
      controller.abort();
    });
  }

  _initComponent(props) {
    const { entityId } = props.match.params;
    if (!this.state.longPollingInprogress && this._isLongPollingEnabled()) {
      // Long-polling request can be send.
      this.setState({longPollingInprogress: true}, () => {
        this._sendLongPollingRequest(entityId);
      });
    }
  }

  _sendLongPollingRequest(entityId) {
    LongPollingManager.sendLongPollingRequest.bind(this, entityId, identityManager.getService())();
  }

  showProcessDetail(entity) {
    this.context.history.push(`/workflow/history/processes/${ entity.id }`);
  }

  /**
   * Compute background color row (added, removed, changed)
   */
  _rowClass({rowIndex, data}) {
    if (data[rowIndex].processVariables.operationType === 'add') {
      return 'bg-success';
    }
    if (data[rowIndex].processVariables.operationType === 'remove') {
      return 'bg-danger';
    }
    if (data[rowIndex].processVariables.operationType === 'change') {
      return 'bg-warning';
    }
    return null;
  }

  _onDeleteAddRoleProcessInstance(entity, event) {
    if (event) {
      event.preventDefault();
    }
    this.refs['confirm-delete'].show(
      this.i18n('content.identity.roles.changeRoleProcesses.deleteConfirm', { processId: entity.id }),
      this.i18n(`action.delete.header`, { count: 1 })
    ).then(() => {
      this.context.store.dispatch(workflowProcessInstanceManager.deleteEntity(entity, null, (deletedEntity, error) => {
        if (!error) {
          this.addMessage({ message: this.i18n('content.identity.roles.changeRoleProcesses.deleteSuccess', { processId: entity.id })});
        } else {
          this.addError(error);
        }
        this.refs.tableProcesses.reload();
      }));
    }, () => {
      // Rejected
    });
  }

  _roleNameCell({ rowIndex, data }) {
    const roleId = data[rowIndex].processVariables.conceptRole
      ? data[rowIndex].processVariables.conceptRole.role
      : data[rowIndex].processVariables.entityEvent.content.role;
    return (
      <Advanced.RoleInfo
        entityIdentifier={ roleId}
        face="popover" />
    );
  }

  _getWfTaskCell({ rowIndex, data}) {
    const entity = data[rowIndex];
    if (!entity || !entity.id) {
      return '';
    }
    entity.taskName = entity.name;
    entity.taskDescription = entity.description;
    entity.definition = {id: entity.activityId};
    return (
      workflowTaskInstanceManager.localize(entity, 'name')
    );
  }

  /**
   * Can change identity permission
   *
   * @return {[type]} [description]
   */
  _canChangePermissions() {
    const { _permissions, _contracts } = this.props;
    // identity permission
    if (Utils.Permission.hasPermission(_permissions, 'CHANGEPERMISSION')) {
      return true;
    }
    // contract permission
    if (!_contracts || _contracts.length === 0) {
      return false;
    }
    // can change permission for  at least one contract
    return _contracts.some(c => Utils.Permission.hasPermission(c._permissions, 'CHANGEPERMISSION')
      || Utils.Permission.hasPermission(c._permissions, 'CANBEREQUESTED'));
  }

  _isLongPollingEnabled() {
    const { _longPollingEnabled } = this.props;
    const hasAuthority = SecurityManager.hasAuthority('ROLEREQUEST_READ');
    return _longPollingEnabled && hasAuthority;
  }

  /**
   * Redirects to tab with identity contracts
   *
   * @param  {string} identityId
   */
  showContracts(identityId) {
    this.context.history.push(`/identity/${ encodeURIComponent(identityId) }/contracts`);
  }

  _getWfProcessCell({ rowIndex, data}) {
    const entity = data[rowIndex];
    if (!entity || !entity.id) {
      return '';
    }
    return (
      <Advanced.WorkflowProcessInfo entityIdentifier={entity.id}/>
    );
  }

  _getCandidatesCell({ rowIndex, data}) {
    const entity = data[rowIndex];
    if (!entity || !entity.candicateUsers) {
      return '';
    }
    return (
      <IdentitiesInfo identities={ entity.candicateUsers } maxEntry={ 2 } header={ this.i18n('entity.WorkflowHistoricTaskInstance.candicateUsers') }/>
    );
  }

  _onChangeSelectTabs(activeKey) {
    this.setState({
      activeKey
    });
  }

  _changePermissions() {
    const { entityId } = this.props.match.params;
    const identity = this.props.identity || identityManager.getEntity(this.context.store.getState(), entityId);
    //
    const uuidId = uuid.v1();
    this.context.history.push(`/role-requests/${ uuidId }/new?new=1&applicantId=${ identity.id }`);
  }

  _refreshAll(props = null) {
    this.refs.direct_roles.reload(props);
    this.refs.sub_roles.reload(props);
    this.refs.requestTable.reload(props);
    this.refs.tableProcesses.reload(props);
  }

  _toggleAutomaticRefresh() {
    LongPollingManager.toggleAutomaticRefresh.bind(this)();
  }

  _getToolbar(contracts, key) {
    const {automaticRefreshOn} = this.state;
    const longPollingEnabled = this._isLongPollingEnabled();
    const data = {};
    data[`automaticRefreshSwitch-${key}`] = automaticRefreshOn && longPollingEnabled;
    return (
      <Basic.Toolbar style={{ paddingTop: 0, paddingBottom: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <div style={{ flex: 1 }}>
            <Basic.AbstractForm
              ref={ `automaticRefreshForm-${key}` }
              readOnly={!longPollingEnabled}
              style={{ padding: '15px 0px' }}
              data={data}>
              <Basic.ToggleSwitch
                ref={`automaticRefreshSwitch-${key}`}
                label={this.i18n('automaticRefreshSwitch')}
                onChange={this._toggleAutomaticRefresh.bind(this, key)}
              />
            </Basic.AbstractForm>
          </div>
          <div>
            <Basic.Button
              level="warning"
              className="btn-xs"
              icon="component:role-request"
              rendered={ contracts.length > 0 }
              onClick={ this._changePermissions.bind(this) }
              disabled={ !this._canChangePermissions() }
              title={ this._canChangePermissions() ? null : this.i18n('security.access.denied') }
              titlePlacement="bottom">
              { this.i18n('changePermissions') }
            </Basic.Button>
            <Advanced.RefreshButton
              rendered={ !automaticRefreshOn || !longPollingEnabled }
              onClick={ this._refreshAll.bind(this) }/>
          </div>
        </div>
      </Basic.Toolbar>
    );
  }

  render() {
    const { entityId } = this.props.match.params;
    const { _showLoadingContracts, _contracts, _permissions, _requestUi, embedded, _columns } = this.props;
    const { activeKey } = this.state;
    //
    let force = new SearchParameters();
    force = force.setFilter('identity', entityId);
    force = force.setFilter('category', 'eu.bcvsolutions.role.approve');
    let roleRequestsForceSearch = new SearchParameters();
    roleRequestsForceSearch = roleRequestsForceSearch.setFilter('applicant', entityId);
    roleRequestsForceSearch = roleRequestsForceSearch.setFilter('applicantType', 'eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto');
    roleRequestsForceSearch = roleRequestsForceSearch.setFilter('executed', 'false');
    //
    return (
      <div style={{ paddingTop: 15 }}>
        <Basic.Confirm ref="confirm-delete" level="danger"/>
        {
          !!embedded
          ||
          <Helmet title={ this.i18n('title') } />
        }
        {
          (_contracts.length > 0) || !this._canChangePermissions()
          ||
          <Basic.Alert
            text={this.i18n('contracts.empty.message')}
            rendered={ !_showLoadingContracts }
            className="no-margin"
            buttons={[
              <Basic.Button
                level="info"
                rendered={ SecurityManager.hasAuthority('APP_ADMIN') }
                onClick={ this.showContracts.bind(this, entityId) }>
                { this.i18n('contracts.empty.button') }
              </Basic.Button>
            ]}/>
        }

        <Basic.Tabs activeKey={ activeKey } onSelect={ this._onChangeSelectTabs.bind(this) }>
          <Basic.Tab eventKey={ 1 } title={ this.i18n('header') } className="bordered">
            { this._getToolbar(_contracts, 'identity-role') }
            <Basic.ContentHeader
              icon="component:identity-roles"
              text={ this.i18n('directRoles.header') }
              style={{ marginBottom: 0, paddingRight: 15, paddingLeft: 15, paddingTop: 15 }}/>
            <IdentityRoleTableComponent
              ref="direct_roles"
              key="direct_roles"
              uiKey={ `${uiKey}-${entityId}` }
              forceSearchParameters={new SearchParameters()
                  .setFilter('identityId', entityId)
                  .setFilter('directRole', true)
                  .setFilter('addEavMetadata', true)
                  .setFilter('onlyAssignments', true)}
              showAddButton={ false }
              showRefreshButton={ false }
              match={ this.props.match }
              columns={ _.difference(_columns || IdentityRoleTable.defaultProps.columns, ['directRole']) }
              _permissions={ _permissions }
              fetchIncompatibleRoles={ false }
              fetchCodeLists={ false }/>

            <Basic.ContentHeader
              icon="component:sub-roles"
              text={ this.i18n('subRoles.header') }
              style={{ marginBottom: 0, paddingRight: 15, paddingLeft: 15 }}/>

            <IdentityRoleTableComponent
              ref="sub_roles"
              key="sub_roles"
              uiKey={ `${uiKey}-sub-${entityId}` }
              forceSearchParameters={ new SearchParameters()
                  .setFilter('identityId', entityId)
                  .setFilter('directRole', false)
                  .setFilter('addEavMetadata', true)
                  .setFilter('onlyAssignments', true)}
              showAddButton={ false }
              showRefreshButton={ false }
              match={ this.props.match }
              columns={ _.difference(_columns || IdentityRoleTable.defaultProps.columns, ['automaticRole']) }
              fetchIncompatibleRoles={ false }
              fetchCodeLists={ false }/>
          </Basic.Tab>

          <Basic.Tab
            eventKey={ 2 }
            title={
              <span>
                { this.i18n('changePermissionRequests.label') }
                <Basic.Badge
                  level="warning"
                  style={{ marginLeft: 5 }}
                  text={ _requestUi ? _requestUi.total : null }
                  rendered={ _requestUi && _requestUi.total > 0 }
                  title={ this.i18n('changePermissionRequests.header') }/>
              </span>
            }
            className="bordered">
            {this._getToolbar(_contracts, 'request')}
            {
              !SecurityManager.hasAuthority('ROLEREQUEST_READ')
              ||
              <Basic.Div>
                <Basic.ContentHeader
                  icon="fa:key"
                  text={ this.i18n('changePermissionRequests.header') }
                  style={{ marginBottom: 0, paddingRight: 15, paddingLeft: 15, paddingTop: 15 }}/>
                <RoleRequestTable
                  ref="requestTable"
                  uiKey="table-applicant-requests"
                  showFilter={ false }
                  forceSearchParameters={ roleRequestsForceSearch }
                  columns={ ['state', 'created', 'modified', 'wf', 'detail', 'systemState'] }
                  externalRefresh={this._refreshAll.bind(this)}
                  manager={ roleRequestManager }/>
              </Basic.Div>
            }

            <Basic.ContentHeader
              icon="fa:sitemap"
              text={ this.i18n('changeRoleProcesses.header') }
              style={{ marginBottom: 0, paddingRight: 15, paddingLeft: 15, paddingTop: SecurityManager.hasAuthority('ROLEREQUEST_READ') ? 10 : 15 }}
              rendered={ activeKey === 2 }/>
            <Advanced.Table
              ref="tableProcesses"
              uiKey="table-processes"
              rowClass={ this._rowClass }
              forceSearchParameters={ force }
              manager={ workflowProcessInstanceManager }
              pagination={ false }>
              <Advanced.Column
                property="detail"
                cell={
                  ({ rowIndex, data }) => (
                    <Advanced.DetailButton
                      title={this.i18n('button.detail')}
                      onClick={this.showProcessDetail.bind(this, data[rowIndex])}/>
                  )
                }
                header=" "
                sort={false}
                face="text"/>
              <Advanced.Column
                property="currentActivityName"
                header={this.i18n('content.roles.processRoleChange.currentActivity')}
                cell={this._getWfTaskCell}
                sort={false}/>
              <Advanced.Column
                property="processVariables.conceptRole.role"
                cell={this._roleNameCell.bind(this)}
                header={this.i18n('content.roles.processRoleChange.roleName')}
                sort={false}
                face="text"/>
              <Advanced.Column
                property="candicateUsers"
                header={this.i18n('content.roles.processRoleChange.candicateUsers')}
                face="text"
                cell={ this._getCandidatesCell.bind(this) }
              />
              <Advanced.Column
                property="processVariables.conceptRole.validFrom"
                header={this.i18n('content.roles.processRoleChange.roleValidFrom')}
                sort={false}
                face="date"/>
              <Advanced.Column
                property="processVariables.conceptRole.validTill"
                header={this.i18n('content.roles.processRoleChange.roleValidTill')}
                sort={false}
                face="date"/>
              <Advanced.Column
                property="id"
                cell={this._getWfProcessCell}
                header={this.i18n('content.roles.processRoleChange.wfProcessId')}
                sort={false}
                face="text"/>
            </Advanced.Table>
          </Basic.Tab>
        </Basic.Tabs>
      </div>
    );
  }
}

IdentityRoles.propTypes = {
  identity: PropTypes.object,
  _showLoading: PropTypes.bool,
  _showLoadingContracts: PropTypes.bool,
  _entities: PropTypes.arrayOf(PropTypes.object),
  _contracts: PropTypes.arrayOf(PropTypes.object),
  userContext: PropTypes.object,
  _permissions: PropTypes.oneOfType([
    PropTypes.bool,
    PropTypes.arrayOf(PropTypes.string)
  ])
};
IdentityRoles.defaultProps = {
  _showLoading: true,
  _showLoadingContracts: true,
  _contracts: [],
  userContext: null,
  _permissions: null,
};

function select(state, component) {
  let addRoleProcessIds;
  if (state.data.ui['table-processes'] && state.data.ui['table-processes'].items) {
    addRoleProcessIds = state.data.ui['table-processes'].items;
  }
  const entityId = component.match.params.entityId;
  const requestUi = Utils.Ui.getUiState(state, 'table-applicant-requests');
  const longPollingEnabled = ConfigurationManager.getPublicValueAsBoolean(state, 'idm.pub.app.long-polling.enabled', true);

  return {
    identity: component.identity || identityManager.getEntity(state, entityId),
    _showLoading: identityRoleManager.isShowLoading(state, `${ uiKey }-${ entityId }`),
    _showLoadingContracts: identityContractManager.isShowLoading(state, `${ uiKeyContracts }-${ entityId }`),
    _contracts: identityContractManager.getEntities(state, `${ uiKeyContracts }-${ entityId }`),
    _addRoleProcessIds: addRoleProcessIds,
    userContext: state.security.userContext,
    _permissions: identityManager.getPermissions(state, null, entityId),
    _searchParameters: Utils.Ui.getSearchParameters(state, `${ uiKey }-${ entityId }`),
    _requestUi: requestUi,
    _longPollingEnabled: longPollingEnabled,
    _columns: ConfigurationManager.getPublicValueAsArray(
      state,
      'idm.pub.app.show.identityRole.table.columns',
      IdentityRoleTable.defaultProps.columns
    )
  };
}

export default connect(select, null, null, { forwardRef: true })(IdentityRoles);
