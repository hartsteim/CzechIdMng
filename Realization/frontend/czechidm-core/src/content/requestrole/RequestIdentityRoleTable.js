import PropTypes from 'prop-types';
import React from 'react';
import { connect } from 'react-redux';
import _ from 'lodash';
//
import * as Basic from '../../components/basic';
import * as Advanced from '../../components/advanced';
import * as Utils from '../../utils';
import { RequestIdentityRoleManager, RoleRequestManager, IdentityContractManager, SecurityManager } from '../../redux';
import SearchParameters from '../../domain/SearchParameters';
import RoleSelectByIdentity from './RoleSelectByIdentity';
import RoleConceptDetail from './RoleConceptDetail';
import FormInstance from '../../domain/FormInstance';
import ConfigLoader from '../../utils/ConfigLoader';
import IncompatibleRoleWarning from '../role/IncompatibleRoleWarning';
import IdentitiesInfo from '../identity/IdentitiesInfo';
import ComponentService from "../../services/ComponentService";

import { ConfigurationManager } from '../../redux';
import {IdentityRoleTableFilter} from "../identity/IdentityRoleTableFilter";
import OwnerCell from "./OwnerCell";
import {data} from "../../redux/data/reducer";
const uiKeyIncompatibleRoles = 'request-incompatible-roles-';
const requestIdentityRoleManager = new RequestIdentityRoleManager();
const roleRequestManager = new RoleRequestManager();
const componentService = new ComponentService();

/**
 * Table for keep identity role concepts.
 *
 * @author Vít Švanda
 */
export class RequestIdentityRoleTable extends Advanced.AbstractTableContent {

  constructor(props, context) {
    super(props, context);
    const {request} = this.props;

    let showChangesOnly = false;
    if (request && (request.state === 'EXECUTED'
      || request.state === 'CANCELED'
      || request.state === 'DISAPPROVED'
      || request.state === 'EXCEPTION'
      || request.state === 'DUPLICATED'
    )) {
      showChangesOnly = true;
    }
    this.state = {
      conceptData: [],
      showRoleByIdentitySelect: false,
      detail: {
        show: false,
        entity: {},
        add: false
      },
      validationErrors: null,
      showChangesOnly
    };
  }

  // eslint-disable-next-line camelcase
  UNSAFE_componentWillReceiveProps(nextProps) {
    const {request} = nextProps;
    if (request
      && request.state === 'EXECUTED'
      && this.props.request
      && this.props.request.state !== 'EXECUTED') {
      this.setState({showChangesOnly: true});
    }
  }

  getContentKey() {
    return 'content.task.IdentityRoleConceptTable';
  }

  getManager() {
    return requestIdentityRoleManager;
  }

  getDefaultSearchParameters() {
    let searchParameters = this.getManager().getDefaultSearchParameters();
    //
    if (this.props.showEnvironment) {
      searchParameters = searchParameters.setFilter('roleEnvironment', ConfigLoader.getConfig('concept-role.table.filter.environment', []));
    }
    //
    return searchParameters;
  }

  componentDidMount() {
    super.componentDidMount();
    this.cancelFilter(null);
  }

  useFilter(event) {
    if (event) {
      event.preventDefault();
    }
    this.refs.table.useFilterForm(this.refs.filterForm.getFilterForm());
  }

  cancelFilter(event) {
    if (event) {
      event.preventDefault();
    }
    this.refs.table.cancelFilter(this.refs.filterForm.getFilterForm());
  }

  /**
   * Close modal dialog
   */
  _closeDetail() {
    this.setState({
      detail: {
        // eslint-disable-next-line react/no-access-state-in-setstate
        ...this.state.detail,
        show: false,
        add: false
      },
      validationErrors: null
    });
  }

  /**
   * Compute background color row (added, removed, changed)
   */
  _getRowClass({rowIndex, data}) {
    const operation = data[rowIndex].operation;
    if (!operation) {
      return null;
    }
    if (operation === 'ADD') {
      return 'bg-success';
    }
    if (operation === 'REMOVE') {
      return 'bg-danger';
    }
    if (operation === 'UPDATE') {
      return 'bg-warning';
    }
    return null;
  }

  /**
   * Create new IdentityRoleConcet with virtual ID (UUID)
   */
  _addConcept() {
    const newIdentityRoleConcept = {operation: 'ADD'};
    this._showDetail(newIdentityRoleConcept, true, true);
  }

  _showDetail(entity, isEdit = false, multiAdd = false) {
    this.setState({
      detail: {
        show: true,
        edit: isEdit && !entity.automaticRole && !entity.directRole,
        entity,
        add: multiAdd
      }
    });
  }

  _showRoleByIdentitySelect() {
    this.setState({
      showRoleByIdentitySelect: true
    }, () => {
      setTimeout(() => {
        this.refs.roleSelectByIdentity.focus();
      }, 10);
    });
  }

  _hideRoleByIdentitySelect() {
    this.setState({
      showRoleByIdentitySelect: false
    });
  }

  _getIncompatibleRoles(role) {
    const { incompatibleRoles } = this.props;
    //
    if (!incompatibleRoles) {
      return [];
    }
    //
    return incompatibleRoles.filter(ir => ir.directRole.id === role.id);
  }

  _filterOpen(open) {
    this.refs.table._filterOpen(open);
  }

  _toggleShowChangesOnly() {
    this.setState({
      showChangesOnly: !this.refs.switchShowChangesOnly.getValue()
    });
  }

  _createConceptsByIdentity() {
    const { request, putRequestToRedux} = this.props;
    this.setState({
      showLoading: true
    }, () => {
      const roleRequestByIdentity = this.refs.roleSelectByIdentity.createRoleRequestByIdentity();
      roleRequestByIdentity.roleRequest = request.id;
      this.context.store.dispatch(roleRequestManager.copyRolesByIdentity(roleRequestByIdentity, null, (requestReturned, error) => {
        if (error) {
          this.setState({
            showLoading: false
          }, () => { this.addError(error); });
        } else {
          if (!request.id) {
            this.reload();
            putRequestToRedux(requestReturned);
          }
          this._hideRoleByIdentitySelect();
          this.setState({
            showLoading: false
          }, () => { this.reload(); });
        }
      }));
    });
  }

  _internalSave(event) {
    if (event) {
      event.preventDefault();
    }

    if(!this.refs.roleConceptDetail.isValid()) {
      return;
    }
    //
    this.setState({
      showLoading: true
    }, () => {
      const { request, requestId, putRequestToRedux} = this.props;


      // Save entity
      this.refs.roleConceptDetail.save(requestId, (createdEntity, error) => {
        if (error) {
          // If error contains parameters with attributes, then is it validation error
          if (error.parameters && error.parameters.attributes) {
            this.setState({
              validationErrors: error.parameters.attributes,
              showLoading: false
            });
          } else {
            this.addError(error);
            this.setState({
              showLoading: false
            });
          }
        } else {
          this.setState({
            showLoading: false
          }, () => {
            if (!request.id) {
              request.id = createdEntity.roleRequest;
              if (createdEntity._embedded.roleRequest) {
                putRequestToRedux(createdEntity._embedded.roleRequest);
              }
            }
            this._closeDetail();
            // We need to fetch incompatibleRoles (could be changed)
            this.context.store.dispatch(roleRequestManager.fetchIncompatibleRoles(request.id, `${ uiKeyIncompatibleRoles }${ request.id }`));
            this.reload();
          });
        }
      });
    });
  }

  _internalDelete(data) {
    const {request, putRequestToRedux} = this.props;
    this.setState({showLoadingActions: true}, () => {
      data.roleRequest = request.id;
      this.context.store.dispatch(this.getManager().deleteEntity(data, null, (json, error) => {
        if (error) {
          this.addError(error);
        }
        this.setState({showLoadingActions: false});
        if (!request.id && json._embedded.roleRequest) {
          const requestNew = json._embedded.roleRequest;
          request.id = requestNew.id;
          putRequestToRedux(requestNew);
        }
        // We need to fetch incompatibleRoles (could be changed)
        this.context.store.dispatch(roleRequestManager.fetchIncompatibleRoles(request.id, `${ uiKeyIncompatibleRoles }${ request.id }`));
        this.reload();
      }));
    });
  }

  beforeDelete(bulkActionValue, selectedEntities) {
    const {request} = this.props;

    for (const entity of selectedEntities) {
      entity.roleRequest = request.id;
    }
  }

  afterDelete(entities) {
    const {request, putRequestToRedux} = this.props;
    if (!request.id && entities && entities.length > 0) {
      if (entities[0]._embedded.roleRequest) {
        putRequestToRedux(entities[0]._embedded.roleRequest);
      }
    } else {
      this.reload();
    }
  }

  reload() {
    if (this.refs.table) {
      this.refs.table.reload();
    }
  }

  getOwnerTypeOptions() {
    const roleAssignmentComponents = componentService.getRoleAssignmentComponents();
    return [...roleAssignmentComponents].map(([key,value]) => {
      return {
        value: value.ownerType,
        niceLabel: this.i18n(value.locale + '.ownerType')
      }
    });
  }

  /**
   * We cannot show delete action for automatic and business roles
   */
  _showRowSelection({ rowIndex, data }) {
    if (!data || rowIndex < 0) {
      return true;
    }
    const request = data[rowIndex];
    if (request.directRole) {
      // sub role cannot be changed / removed
      return false;
    }
    if (request.automaticRole) {
      // automatic role cannot be changed / removed
      return false;
    }
    // concept cannot be changed / removed
    return this._canChangePermissions(request);
  }

  _canChangePermissions(request) {
    if (!request) {
      return false;
    }
    return Utils.Permission.hasPermission(request._permissions, 'CHANGEPERMISSION');
  }

  /**
   * Generate cell with detail button and info components
   */
  renderDetailCell({rowIndex, data}) {
    const requestIdentityRole = data[rowIndex];
    const role = requestIdentityRole._embedded.role;
    const operation = requestIdentityRole.operation;
    if (!role) {
      return '';
    }
    const content = [];
    //
    content.push(
      <Advanced.DetailButton
        title={ this.i18n('button.detail') }
        onClick={ this._showDetail.bind(
          this,
          requestIdentityRole,
          operation !== 'REMOVE' && this._canChangePermissions(requestIdentityRole),
          false
        )}/>
    );
    content.push(
      <IncompatibleRoleWarning incompatibleRoles={ this._getIncompatibleRoles(role) }/>
    );
    if (requestIdentityRole.duplicate) {
      //
      content.push(
        <span>
          <Basic.Icon
            icon="fa:warning"
            style={{ marginLeft: 3, color: '#337ab7' }}
            title={ this.i18n('entity.IdentityRole.duplicate.label') }/>
        </span>
      );
    }
    return content;
  }

  /**
   * Generate cell with actions (buttons)
   */
  renderConceptActionsCell({rowIndex, data}) {
    const { readOnly, requestId } = this.props;
    const { showLoadingActions } = this.state;

    const actions = [];
    const value = data[rowIndex];
    const manualRole = !value.automaticRole && !value.directRole;
    const operation = value.operation;
    //
    const item = {...data[rowIndex], roleRequest: requestId}

    actions.push(
      <Basic.Button
        level="danger"
        onClick={ this._internalDelete.bind(this, item) }
        className="btn-xs"
        disabled={ readOnly || !manualRole || !this._canChangePermissions(value) }
        showLoading={ showLoadingActions }
        role="group"
        title={ this.i18n('button.delete') }
        titlePlacement="bottom"
        icon="trash"/>
    );
    if (operation !== 'REMOVE') {

      actions.push(
        <Basic.Button
          level="warning"
          showLoading={ showLoadingActions }
          onClick={ this._showDetail.bind(this, item, true, false) }
          className="btn-xs"
          disabled={
            readOnly
              || !manualRole
              || !value.role
              || !value.ownerUuid
              || !this._canChangePermissions(value)
          }
          role="group"
          title={ this.i18n('button.edit') }
          titlePlacement="bottom"
          icon="edit"/>
      );
    }
    return (
      <div className="btn-group" role="group">
        { actions }
      </div>
    );
  }

  renderConceptAttributesCell({rowIndex, data}) {
    const value = data[rowIndex];
    const result = [];
    if (value
      && value._eav
      && value._eav.length === 1
      && value._eav[0].formDefinition) {
      const formInstance = value._eav[0];
      const _formInstance = new FormInstance(formInstance.formDefinition, formInstance.values);
      result.push(
        // eslint-disable-next-line jsx-a11y/click-events-have-key-events
        <div onClick={ !value._removed ? this._showDetail.bind(this, value, true, false) : null }>
          <Advanced.EavForm
            key={ `${rowIndex}-${value.id}` }
            ref="eavForm"
            formInstance={ _formInstance }
            validationErrors={ formInstance.validationErrors }
            readOnly
            useDefaultValue={ false }/>
        </div>
      );
    }
    return (
      <Basic.Div className="abstract-form condensed" style={{minWidth: 150, padding: 0}}>
        { result }
      </Basic.Div>
    );
  }

  render() {
    const {
      readOnly,
      request,
      identityId,
      showRowSelection,
      showEnvironment,
      requestId,
      columns,
      isAccount,
      accountId
    } = this.props;
    const {
      showChangesOnly,
      detail,
      showRoleByIdentitySelect,
      validationErrors
    } = this.state;

    const identityUsername = request && request.applicantInfo.id;
    let forceSearchParameters = this.getSearchParameters(request, requestId, identityId, isAccount, accountId, showChangesOnly);
    //
    const showLoading = this.props.showLoading || this.state.showLoading;
    const contractForceSearchParameters = new SearchParameters().setFilter('identity', identityUsername);
    //
    return (
      <Basic.Div>
        <Basic.Panel rendered={ request !== null}>
          <Basic.Confirm ref="confirm-delete" level="danger"/>
          <Basic.Toolbar>
            <Basic.Div>
              <Basic.Div className="pull-left">
                <Basic.AbstractForm
                  ref="formShowChangesOnly"
                  style={{padding: '0px'}}
                  data={{switchShowChangesOnly: showChangesOnly}}>
                  <Basic.ToggleSwitch
                    ref="switchShowChangesOnly"
                    label={this.i18n('switchShowChangesOnly')}
                    onChange={this._toggleShowChangesOnly.bind(this)}
                  />
                </Basic.AbstractForm>
              </Basic.Div>
              <Basic.Div
                className="pull-right"
                rendered={ SecurityManager.hasAuthority('ROLE_CANBEREQUESTED') }>
                <Basic.Button
                  level="success"
                  className="btn-xs"
                  showLoading={showLoading}
                  disabled={readOnly}
                  onClick={this._addConcept.bind(this)}
                  icon="fa:plus"
                  text={ this.i18n('button.add') }/>
                <Basic.Button
                  level="success"
                  className="btn-xs"
                  showLoading={showLoading}
                  disabled={ readOnly }
                  onClick={ this._showRoleByIdentitySelect.bind(this) }
                  icon="fa:plus"
                  text={ this.i18n('addByIdentity.header') }
                  style={{ marginLeft: 3 }}/>
              </Basic.Div>
            </Basic.Div>
          </Basic.Toolbar>
          <Advanced.Table
            ref="table"
            columns={ this.props.columns }
            uiKey="request-identity-role-table"
            hover={ false }
            manager={ requestIdentityRoleManager }
            showLoading={showLoading}
            showRowSelection={ showRowSelection ? this._showRowSelection.bind(this) : false }
            actions={
              [{
                value: 'delete',
                niceLabel: this.i18n('action.delete.action'),
                action: this.onDelete.bind(this),
                disabled: readOnly
              }]
            }
            rowClass={this._getRowClass}
            forceSearchParameters={forceSearchParameters}
            filter={
              <IdentityRoleTableFilter ref="filterForm"
                                       showEnvironment={showEnvironment}
                                       hasRoleForceFilter={false}
                                       hasIdentityForceFilter={true}
                                       contractForceSearchParameters={contractForceSearchParameters}
                                       useFilter={this.useFilter.bind(this)}
                                       cancelFilter={this.cancelFilter.bind(this)}
              />
            }
            _searchParameters={ this.getSearchParameters() }>
            <Advanced.Column
              header=""
              className="detail-button"
              cell={ this.renderDetailCell.bind(this) }/>
            <Advanced.Column
              property="name"
              title={ this.i18n('entity.Role.name') }
              sort
              header={ this.i18n('entity.IdentityRole.role') }
              cell={
                /* eslint-disable react/no-multi-comp */
                ({ rowIndex, data }) => {
                  const role = data[rowIndex]._embedded.role;
                  if (!role) {
                    return (
                      <Basic.Label
                        level="default"
                        value={ this.i18n('label.removed') }
                        title={ this.i18n('content.audit.revision.deleted') }/>
                    );
                  }
                  return (
                    <Advanced.EntityInfo
                      entityType="role"
                      entityIdentifier={ role.id }
                      entity={ role }
                      face="popover"
                      showIcon
                      showCode={ false }
                      showEnvironment={ false }/>
                  );
                }
              }/>
            <Advanced.Column
              property="baseCode"
              sortProperty="role.baseCode"
              rendered={showEnvironment}
              face="text"
              header={ this.i18n('entity.Role.baseCode.label') }
              cell={
                ({ rowIndex, data }) =>
                  data[rowIndex]._embedded.role.baseCode
              }
              sort/>
            <Advanced.Column
              property="systemState"
              face="text"
              header={this.i18n('systemState')}
              rendered={showChangesOnly}
              cell={
                ({ rowIndex, data }) => {
                  const entity = data[rowIndex];
                  return (
                    <Advanced.OperationResult value={ entity.systemState }/>
                  );
                }
              }/>
            <Advanced.Column
              property="environment"
              rendered={showEnvironment}
              sortProperty="role.environment"
              face="text"
              header={ this.i18n('entity.Role.environment.label') }
              cell={
                ({ rowIndex, data }) =>
                  data[rowIndex]._embedded.role.environment
              }
              sort/>
            <Advanced.Column
              property="roleAttributes"
              header={this.i18n('content.task.IdentityRoleConceptTable.identityRoleAttributes.header')}
              cell={
                ({rowIndex, data}) => this.renderConceptAttributesCell({ rowIndex, data })
              }/>
            <Advanced.Column
              property="contractPosition"
              header={ this.i18n('entity.IdentityRole.owner.title')}
              cell={ ({rowIndex, data}) => <OwnerCell entity={ data[rowIndex]}/> }
              />
            <Advanced.Column
              header={ this.i18n('entity.RoleRequest.candicateUsers') }
              property="candicateUsers"
              cell={
                ({rowIndex, data}) => {
                  const candidates = data[rowIndex].candidates;
                  return (
                    <IdentitiesInfo
                      identities={ candidates }
                      isUsedIdentifier={ false }
                      maxEntry={ 5 }
                      header={ this.i18n('entity.WorkflowHistoricTaskInstance.candicateUsers') }/>
                  );
                }
              }/>
            <Advanced.Column
              sort
              property="validFrom"
              face="date"
              header={this.i18n('entity.ConceptRoleRequest.validFrom')}/>
            <Advanced.Column
              sort
              property="validTill"
              face="date"
              header={this.i18n('entity.ConceptRoleRequest.validTill')}/>
            <Advanced.Column
              property="description"
              header={this.i18n('entity.Role.description')}
              face="text"
              className='descriptionColumn'
              cell={
                ({ rowIndex, data }) =>
                  data[rowIndex]._embedded.role.description
              }/>
            <Advanced.Column
              property="priority"
              header={this.i18n('entity.Role.priority')}
              face="text"
              cell={
                ({ rowIndex, data }) =>
                  data[rowIndex]._embedded.role.priority
              }/>
            <Advanced.Column
              property="directRole"
              header={ this.i18n('entity.IdentityRole.directRole.label') }
              cell={
                /* eslint-disable react/no-multi-comp */
                ({ rowIndex, data, property }) => {
                  if (!data[rowIndex][property]) {
                    return null;
                  }
                  //
                  return (
                    <Advanced.EntityInfo
                      entityType="identityRole"
                      entityIdentifier={ data[rowIndex][property] }
                      entity={ data[rowIndex]._embedded[property] }
                      showIdentity={ false }
                      face="popover" />
                  );
                }
              }/>
            <Advanced.Column
              sort
              property="automaticRole"
              header={ <Basic.Icon value="component:automatic-role" title={ this.i18n('entity.IdentityRole.automaticRole.help') }/> }
              cell={
                /* eslint-disable react/no-multi-comp */
                ({ rowIndex, data }) => <Basic.BooleanCell
                  propertyValue={ data[rowIndex].automaticRole !== null }
                  className="column-face-bool"/>
              }/>
            <Advanced.Column
              property="action"
              header={ this.i18n('label.action') }
              className="action"
              cell={ this.renderConceptActionsCell.bind(this) }/>
          </Advanced.Table>
        </Basic.Panel>
        <Basic.Modal
          bsSize="large"
          show={showRoleByIdentitySelect}
          onHide={ this._hideRoleByIdentitySelect.bind(this) }
          backdrop="static"
          keyboard={!showLoading}>
          <Basic.Modal.Header
            closeButton={ !showLoading }
            text={ this.i18n('create.headerByIdentity') }
            rendered={ Utils.Entity.isNew(detail.entity) }/>
          <Basic.Modal.Body>
            <RoleSelectByIdentity
              ref="roleSelectByIdentity"
              showLoading={ showLoading }
              identityUsername={identityUsername}
              request={request}/>
          </Basic.Modal.Body>
          <Basic.Modal.Footer>
            <Basic.Button
              level="link"
              showLoading={ showLoading }
              onClick={ this._hideRoleByIdentitySelect.bind(this) }>
              { this.i18n('button.close') }
            </Basic.Button>
            <Basic.Button
              type="submit"
              level="success"
              showLoading={ showLoading }
              onClick={ this._createConceptsByIdentity.bind(this) }
              showLoadingIcon>
              { this.i18n('button.set') }
            </Basic.Button>
          </Basic.Modal.Footer>
        </Basic.Modal>

        <Basic.Modal
          bsSize="large"
          show={detail.show}
          onHide={ this._closeDetail.bind(this) }
          backdrop="static"
          keyboard={!showLoading}>
          <Basic.Modal.Header
            closeButton={ !showLoading }
            text={ this.i18n('create.header') }
            rendered={ Utils.Entity.isNew(detail.entity) }
            icon="fa:plus"/>
          <Basic.Modal.Header
            closeButton={ !showLoading }
            text={ this.i18n('edit.header', { role: detail.entity.role }) }
            rendered={ !Utils.Entity.isNew(detail.entity) }/>
          <Basic.Modal.Body>
            <RoleConceptDetail
              ref="roleConceptDetail"
              showEnvironment={showEnvironment}
              identityUsername={identityUsername}
              showLoading={showLoading}
              readOnly={readOnly}
              entity={detail.entity}
              isEdit={detail.edit}
              multiAdd={detail.add}
              requestId={requestId}
              validationErrors={ validationErrors }
              isAccount={isAccount}
              accountId={accountId}/>
          </Basic.Modal.Body>
          <Basic.Modal.Footer>
            <Basic.Button
              level="link"
              onClick={ this._closeDetail.bind(this) }
              showLoading={ showLoading }>
              { this.i18n('button.close') }
            </Basic.Button>
            <Basic.Button
              type="submit"
              onClick={ this._internalSave.bind(this) }
              level="success"
              showLoading={ showLoading }
              showLoadingIcon
              rendered={ detail.edit && !readOnly }>
              { this.i18n('button.set') }
            </Basic.Button>
          </Basic.Modal.Footer>
        </Basic.Modal>
      </Basic.Div>
    );
  }


  getSearchParameters(request, requestId, identityId, isAccount, accountId, showChangesOnly) {
    let forceSearchParameters = new SearchParameters();
    if (request) {
      forceSearchParameters = forceSearchParameters.setFilter('roleRequestId', request.id);
    }
    if (requestId) {
      forceSearchParameters = forceSearchParameters.setFilter('roleRequestId', requestId);
    }
    if (identityId && !isAccount) {
      forceSearchParameters = forceSearchParameters.setFilter('identityId', identityId);
      forceSearchParameters = forceSearchParameters.setFilter('accountId', null);
      forceSearchParameters = forceSearchParameters.setFilter('loadAssignments', null);
    }
    if (isAccount && accountId) {
      forceSearchParameters = forceSearchParameters.setFilter('identityId', null);
      forceSearchParameters = forceSearchParameters.setFilter('accountId', accountId);
      forceSearchParameters = forceSearchParameters.setFilter('ownerType', 'eu.bcvsolutions.idm.acc.dto.AccAccountDto');
      forceSearchParameters = forceSearchParameters.setFilter('loadAssignments', true);
    }
    // when opening role request, we do not have any info passed from top component or GET param, so we need to rely on
    // applicantInfo to correctly set params. Otherwise, this could cause sending identityId search param with account identifier
    // for technical accounts
    const isTechAccount = request?.applicantInfo?.applicantType === 'eu.bcvsolutions.idm.tech.model.dto.TechnicalAccountDto'
    if (isTechAccount) {
      forceSearchParameters = forceSearchParameters.setFilter('identityId', null);
      forceSearchParameters = forceSearchParameters.setFilter('technicalAccountId', identityId);
    }

    forceSearchParameters = forceSearchParameters.setFilter('onlyChanges', showChangesOnly);
    forceSearchParameters = forceSearchParameters.setFilter('includeCandidates', true);
    return forceSearchParameters.setFilter('includeCrossDomainsSystemsCount', true);
  }
}

RequestIdentityRoleTable.propTypes = {
  uiKey: PropTypes.string.isRequired,
  identityId: PropTypes.string.isRequired,
  className: PropTypes.string,
  request: PropTypes.object,
  readOnly: PropTypes.bool,
  showEnvironment: PropTypes.bool,
  putRequestToRedux: PropTypes.func,
  isAccount: PropTypes.bool,
  accountId: PropTypes.string
};

RequestIdentityRoleTable.defaultProps = {
  columns: ConfigLoader.getConfig('RequestIdentityRoleTable.table.columns', [
    'name',
    'baseCode',
    'systemState',
    'environment',
    'roleAttributes',
    'contractPosition',
    'validFrom',
    'validTill',
    'description',
    'priority',
    'directRole',
    'automaticRole',
    'candicateUsers',
    'action'
  ]),
  showLoading: false,
  showRowSelection: true,
  readOnly: false,
  showEnvironment: true,
  isAccount: false
};

function select(state, component) {
  return {
    columns: component.columns || ConfigurationManager.getPublicValueAsArray(
      state,
      'idm.pub.app.show.role.request.table.columns',
      RequestIdentityRoleTable.defaultProps.columns
    )
  };
}
export default connect(select, null, null, { forwardRef: true })(RequestIdentityRoleTable);
