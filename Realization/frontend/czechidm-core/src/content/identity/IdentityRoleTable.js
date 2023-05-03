import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import uuid from 'uuid';
import _ from 'lodash';
//
import * as Basic from '../../components/basic';
import * as Advanced from '../../components/advanced';
import * as Utils from '../../utils';
import { SearchParameters } from '../../domain';
import {
  IdentityManager,
  RoleTreeNodeManager,
  RoleManager,
  IdentityContractManager,
  CodeListManager,
  DataManager,
  ConfigurationManager, RequestIdentityRoleManager
} from '../../redux';
import IdentityRoleEav from './IdentityRoleEav';
import IncompatibleRoleWarning from '../role/IncompatibleRoleWarning';
import FormInstance from '../../domain/FormInstance';
import ConfigLoader from '../../utils/ConfigLoader';
import ComponentService from "../../services/ComponentService";
import {IdentityRoleTableFilter} from "./IdentityRoleTableFilter";
import OwnerCell from "../requestrole/OwnerCell";
import {AssignmentOwnerSelector} from "../requestrole/AssignmentOwnerSelector";

const manager =  new RequestIdentityRoleManager();
const identityManager = new IdentityManager();
const roleManager = new RoleManager();
const roleTreeNodeManager = new RoleTreeNodeManager();
const identityContractManager = new IdentityContractManager();
const codeListManager = new CodeListManager();
const uiKeyIncompatibleRoles = 'identity-incompatible-roles-';
const componentService = new ComponentService();


const TEST_ADD_ROLE_DIRECTLY = false;

/**
 * Table of assigned roles ~ identity roles
 * - table suppors add permission
 *
 * @author Radek Tomiška
 */
export class IdentityRoleTable extends Advanced.AbstractTableContent {

  componentDidMount() {
    super.componentDidMount();
    //
    const { entityId } = this.props.match.params;
    const { fetchIncompatibleRoles, fetchCodeLists, showEnvironment } = this.props;
    //
    if (fetchCodeLists && showEnvironment) {
      this.context.store.dispatch(codeListManager.fetchCodeListIfNeeded('environment'));
    }
    if (fetchIncompatibleRoles) {
      this.context.store.dispatch(identityManager.fetchIncompatibleRoles(entityId, `${ uiKeyIncompatibleRoles }${ entityId }`));
    }
  }

  getContentKey() {
    return 'content.identity.roles';
  }

  getManager() {
    return this.props.manager;
  }

  getDefaultSearchParameters() {
    let searchParameters = this.getManager().getDefaultSearchParameters();
    //
    if (this.props.showEnvironment) {
      searchParameters = searchParameters.setFilter('roleEnvironment', ConfigLoader.getConfig('identity-role.table.filter.environment', []));
    }
    //
    return searchParameters;
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
   * Can change identity permission
   *
   * @return {[type]} [description]
   */
  _canChangePermissions() {
    const { _permissions } = this.props;
    //
    return Utils.Permission.hasPermission(_permissions, 'CHANGEPERMISSION');
  }

  _changePermissions() {
    const { entityId } = this.props.match.params;
    const identity = identityManager.getEntity(this.context.store.getState(), entityId);
    //
    const uuidId = uuid.v1();
    this.context.history.push(`/role-requests/${ uuidId }/new?new=1&applicantId=${identity.id}`);
  }

  save(entity, event) {
    const formEntity = this.refs.form.getData();
    //
    super.save(formEntity, event);
  }

  showDetail(entity) {
    super.showDetail(entity, () => {
      this.setState({
        activeKey: 1
      }, () => {
        this.refs.role.focus();
      });
    });
  }

  _onChangeSelectTabs(activeKey) {
    this.setState({
      activeKey
    });
  }

  _getIncompatibleRoles(entity) {
    const { _incompatibleRoles } = this.props;
    //
    if (!_incompatibleRoles) {
      return [];
    }
    //
    return _incompatibleRoles.filter(ir => ir.directRole.id === entity.role);
  }

  _attributesCell({rowIndex, data}) {
    const value = data[rowIndex];
    const result = [];
    if (value
      && value._eav
      && value._eav.length === 1
      && value._eav[0].formDefinition) {
      const formInstance = value._eav[0];
      const _formInstance = new FormInstance(formInstance.formDefinition, formInstance.values, formInstance.validationErrors);
      result.push(
        <Advanced.EavForm
          key={ `${rowIndex}-${value.id}` }
          ref="eavForm"
          formInstance={ _formInstance }
          readOnly
          useDefaultValue={false}/>
      );
    }
    return (
      <Basic.Div className="abstract-form condensed" style={{minWidth: 150, padding: 0}}>
        { result }
      </Basic.Div>
    );
  }

  reload(props = null) {
    this.refs.table.reload(props);
  }

  render() {
    const {
      forceSearchParameters,
      showAddButton,
      showDetailButton,
      _showLoading,
      columns,
      className,
      rendered,
      environmentItems,
      showRefreshButton,
      showEnvironment,
      children,
      rowClass
    } = this.props;
    const { detail, activeKey } = this.state;
    //
    if (!rendered) {
      return null;
    }
    // contract force search parameters - contract filter is shown only if identity is given
    const hasIdentityForceFilter = forceSearchParameters.getFilters().has('identityId');
    const hasRoleForceFilter = forceSearchParameters.getFilters().has('roleId');
    let contractForceSearchParameters = null;
    if (forceSearchParameters && hasIdentityForceFilter) {
      contractForceSearchParameters = new SearchParameters().setFilter('identity', forceSearchParameters.getFilters().get('identityId'));
    }
    forceSearchParameters.setFilter('onlyAssignments', 'true');
    const _columns = this.getColumns();
    //
    return (
      <>
        <Advanced.Table
          ref="table"
          uiKey={ this.getUiKey() }
          columns={ _columns }
          manager={ this.getManager() }
          forceSearchParameters={ forceSearchParameters }
          showRefreshButton={ showRefreshButton }
          className={ className }
          buttons={[
            <Basic.Button
              level="success"
              className="btn-xs"
              onClick={ this.showDetail.bind(this, {}) }
              rendered={ showAddButton && TEST_ADD_ROLE_DIRECTLY }
              icon="fa:plus">
              { this.i18n('button.add') }
            </Basic.Button>,
            <Basic.Button
              level="warning"
              className="btn-xs"
              onClick={ this._changePermissions.bind(this) }
              rendered={ showAddButton }
              disabled={ !this._canChangePermissions() }
              title={ this._canChangePermissions() ? null : this.i18n('security.access.denied') }
              titlePlacement="bottom"
              icon="component:role-request">
              { this.i18n('changePermissions') }
            </Basic.Button>
          ]}
          _searchParameters={ this.getSearchParameters() }
          rowClass={
            ({ rowIndex, data, property }) => {
              if (rowClass) {
                return rowClass({ rowIndex, data, property });
              }
              const entity = data[rowIndex];
              if (this._getIncompatibleRoles(entity).length > 0) {
                // RT: is looks to agressive ... or combine disabled + incompatible
                // return 'warning';
              }
              return Utils.Ui.getRowClass(entity);
            }
          }
          filter={
            <IdentityRoleTableFilter ref="filterForm"
                                     showEnvironment={showEnvironment}
                                     hasRoleForceFilter={hasRoleForceFilter}
                                     hasIdentityForceFilter={hasIdentityForceFilter}
                                     environmentItems={environmentItems}
                                     contractForceSearchParameters={contractForceSearchParameters}
                                     useFilter={this.useFilter.bind(this)}
                                     cancelFilter={this.cancelFilter.bind(this)}
            />
         }>
          <Advanced.Column
            header=""
            className="detail-button"
            cell={
              ({ rowIndex, data }) => {
                const entity = data[rowIndex];
                const content = [];
                content.push(
                  <Advanced.DetailButton
                    title={ this.i18n('button.detail') }
                    onClick={ this.showDetail.bind(this, data[rowIndex]) }/>
                );
                if (_.includes(columns, 'incompatibleRoles')) {
                  content.push(
                    <IncompatibleRoleWarning incompatibleRoles={ this._getIncompatibleRoles(entity) }/>
                  );
                }
                return content;
              }
            }
            rendered={ showDetailButton }/>
          <Advanced.Column
            header={this.i18n('entity.IdentityRole.role')}
            property="role"
            sort
            sortProperty="role.name"
            cell={
              /* eslint-disable react/no-multi-comp */
              ({ rowIndex, data }) => {
                const entity = data[rowIndex];
                //
                return (
                  <Advanced.EntityInfo
                    entityType="role"
                    entityIdentifier={ entity.role }
                    entity={ entity._embedded.role }
                    face="popover"
                    showIcon
                    showEnvironment={ !_.includes(columns, 'environment') }
                    showCode={ !_.includes(columns, 'baseCode') }/>
                );
              }
            }
            rendered={ _.includes(columns, 'role') }/>
          <Advanced.Column
            header={ this.i18n('entity.Identity._type') }
            property="identityContract"
            cell={
              /* eslint-disable react/no-multi-comp */
              ({ rowIndex, data, property }) => (
                  <Advanced.EntityInfo
                  entityType="identity"
                  entityIdentifier={ data[rowIndex]._embedded[property].identity }
                  entity={ data[rowIndex]._embedded[property]._embedded.identity }
                  face="popover"
                  showIcon />
              )
            }
            rendered={ _.includes(columns, 'identity') }/>
          <Advanced.Column
            property="baseCode"
            header={ this.i18n('entity.Role.baseCode.label') }
            title={ this.i18n('entity.Role.baseCode.help') }
            face="text"
            sort
            sortProperty="role.baseCode"
            rendered={ _.includes(columns, 'baseCode') }
            cell={ ({ rowIndex, data }) => data[rowIndex]._embedded.role.baseCode }
          />
          <Advanced.Column
            property="system"
            header={ this.i18n('entity.RoleSystem.label') }
            face="text"
            sort={false}
            rendered={ _.includes(columns, 'system') }
            cell={
              ({ rowIndex, data }) => {
                if (data[rowIndex]._embedded.roleSystem) {
                  return data[rowIndex]._embedded.roleSystem._embedded.system.name;
                }
                //
                return null;
              }
            }
          />
          <Advanced.Column
            property="environment"
            header={ this.i18n('entity.Role.environment.label') }
            title={ this.i18n('entity.Role.environment.help') }
            face="text"
            sort
            sortProperty="role.environment"
            rendered={ showEnvironment && _.includes(columns, 'environment') }
            cell={
              ({ rowIndex, data }) => (
                <Advanced.CodeListValue code="environment" value={ data[rowIndex]._embedded.role.environment }/>
              )
            }
          />
          <Advanced.Column
            property="roleAttributes"
            header={this.i18n('content.task.IdentityRoleConceptTable.identityRoleAttributes.header')}
            cell={ ({rowIndex, data}) => this._attributesCell({ rowIndex, data }) }
            rendered={ _.includes(columns, 'roleAttributes') }/>
          <Advanced.Column
            header={this.i18n('entity.IdentityRole.owner.title')}
            property="owner"
            cell={ ({rowIndex, data}) => <OwnerCell entity={ data[rowIndex]}/> }
            rendered={ _.includes(columns, 'owner') }
              />
          <Advanced.Column
            header={this.i18n('entity.IdentityRole.contractPosition.label')}
            property="contractPosition"
            cell={
              /* eslint-disable react/no-multi-comp */
              ({ rowIndex, data, property }) => {
                if (!data[rowIndex][property]) {
                  return null;
                }
                return (
                  <Advanced.EntityInfo
                    entityType="contractPosition"
                    entityIdentifier={ data[rowIndex][property] }
                    entity={ data[rowIndex]._embedded[property] }
                    showIdentity={ false }
                    face="popover" />
                );
              }
            }
            rendered={ _.includes(columns, 'contractPosition') }
          />
          <Advanced.Column
            property="validFrom"
            header={this.i18n('label.validFrom')}
            face="date"
            sort
            rendered={ _.includes(columns, 'validFrom') }/>
          <Advanced.Column
            property="validTill"
            header={this.i18n('label.validTill')}
            face="date"
            sort
            rendered={ _.includes(columns, 'validTill') }/>
          <Advanced.Column
            property="directRole"
            cell={
              /* eslint-disable react/no-multi-comp */
              ({ rowIndex, data, property }) => {
                if (!data[rowIndex][property]) {
                  return null;
                }
                return (
                  <Advanced.EntityInfo
                    entityType="identityRole"
                    entityIdentifier={ data[rowIndex][property] }
                    entity={ data[rowIndex]._embedded[property] }
                    showIdentity={ false }
                    face="popover" />
                );
              }
            }
            rendered={ _.includes(columns, 'directRole') }/>

          <Advanced.Column
            property="description"
            header={this.i18n('entity.Role.description')}
            face="text"
            className='descriptionColumn'
            cell={ ({ rowIndex, data }) => data[rowIndex]._embedded.role.description }
            rendered={ _.includes(columns, 'description') }/>

          <Advanced.Column
            property="priority"
            header={this.i18n('entity.Role.priority')}
            face="text"
            cell={ ({ rowIndex, data }) => data[rowIndex]._embedded.role.priority }
            rendered={ _.includes(columns, 'priority') }/>

          <Advanced.Column
            property="automaticRole"
            header={ <Basic.Icon value="component:automatic-role"/> }
            title={ this.i18n('entity.IdentityRole.automaticRole.help') }
            face="bool"
            cell={
              /* eslint-disable react/no-multi-comp */
              ({ rowIndex, data }) => (
                <Basic.BooleanCell propertyValue={ data[rowIndex].automaticRole !== null } className="column-face-bool"/>
              )
            }
            rendered={ _.includes(columns, 'automaticRole') }/>
          { children }
        </Advanced.Table>

        <Basic.Modal
          bsSize="large"
          show={ detail.show }
          onHide={ this.closeDetail.bind(this) }
          backdrop="static"
          keyboard={ !_showLoading }
          rendered={ showDetailButton }>
          <Basic.Modal.Header
            icon="component:identity-role"
            closeButton={ !_showLoading }
            text={ this.i18n('create.header') }
            rendered={ Utils.Entity.isNew(detail.entity) }/>
          <Basic.Modal.Header
            icon={
              detail.entity._embedded && detail.entity._embedded.role && detail.entity._embedded.role.childrenCount > 0
              ?
              'component:business-role'
              :
              'component:identity-role'
            }
            closeButton={ !_showLoading }
            text={ this.i18n('edit.header', { role: detail.entity._embedded ? roleManager.getNiceLabel(detail.entity._embedded.role) : null }) }
            rendered={ !Utils.Entity.isNew(detail.entity) }/>
          <Basic.Modal.Body style={{ padding: 0 }}>
            <form onSubmit={ this.save.bind(this) }>
              <Basic.Tabs
                activeKey={ activeKey }
                onSelect={ this._onChangeSelectTabs.bind(this) }>
                <Basic.Tab
                  eventKey={ 1 }
                  title={ this.i18n('detail.tabs.basic') }
                  style={{ padding: 15, border: 'none' }}>
                  <Basic.AbstractForm ref="form" showLoading={ _showLoading } readOnly={ !TEST_ADD_ROLE_DIRECTLY }>
                    <Basic.SelectBox
                      ref="role"
                      manager={ roleManager }
                      label={ this.i18n('entity.IdentityRole.role') }
                      required/>
                    <Advanced.EntitySelectBox
                      ref="roleSystem"
                      entityType="roleSystem"
                      readOnly
                      label={ this.i18n('entity.RoleSystem.label') }/>
                    <AssignmentOwnerSelector
                        ref="ownerSelector"
                        entity={detail.entity}
                        isNew={false}
                        accountId={null}
                        identityUsername = {null}
                        isAccount = {false}
                        // changing role assignment owner is not supported since it would allow for changing
                        // between different owner types, which could result in unexpected behavior
                        readOnly = {true}
                        onChange={null}
                    />
                    <Basic.LabelWrapper
                      label={ this.i18n('entity.IdentityRole.automaticRole.label') }
                      helpBlock={ this.i18n('entity.IdentityRole.automaticRole.help') }
                      rendered={ detail.entity.automaticRole }>
                      { detail.entity.automaticRole ? roleTreeNodeManager.getNiceLabel(detail.entity._embedded.automaticRole) : null }
                    </Basic.LabelWrapper>
                    <Basic.Row>
                      <Basic.Col md={ 6 }>
                        <Basic.DateTimePicker
                          mode="date"
                          ref="validFrom"
                          label={ this.i18n('label.validFrom') }/>
                      </Basic.Col>
                      <Basic.Col md={ 6 }>
                        <Basic.DateTimePicker
                          mode="date"
                          ref="validTill"
                          label={ this.i18n('label.validTill') }/>
                      </Basic.Col>
                    </Basic.Row>
                  </Basic.AbstractForm>
                  {
                    detail.entity.directRole !== null
                    ||
                    <Basic.Div>
                      <Basic.ContentHeader
                        style={{ marginBottom: 0 }}
                        className="marginable"
                        icon="component:sub-roles">
                        { this.i18n('detail.directRole.subRoles.header') }
                      </Basic.ContentHeader>
                      <IdentityRoleTable
                        uiKey={ `${this.getUiKey()}-all-sub-roles` }
                        showAddButton={ false }
                        forceSearchParameters={ new SearchParameters()
                            .setFilter('directRoleId', detail.entity.id)
                            .setFilter('identityId', detail.entity.ownerId)
                            .setFilter('onlyAssignments', true)
                      }
                        showDetailButton={ false }
                        match={ this.props.match }
                        columns={ _.difference(IdentityRoleTable.defaultProps.columns, ['identityContract', 'directRole', 'automaticRole']) }
                        className="marginable"/>
                    </Basic.Div>
                  }
                </Basic.Tab>
                <Basic.Tab
                  eventKey={ 2 }
                  rendered={!!(detail && detail.entity && detail.entity._embedded && detail.entity._embedded.role.identityRoleAttributeDefinition)}
                  style={{ padding: 15, border: 'none' }}
                  title={this.i18n('detail.tabs.attributes')}>
                  <IdentityRoleEav
                    entityId={detail.entity.id}
                    entity={detail.entity}
                  />
                </Basic.Tab>
              </Basic.Tabs>
              {/* onEnter action - is needed because footer submit button is outside form */}
              <input type="submit" className="hidden"/>
            </form>
          </Basic.Modal.Body>

          <Basic.Modal.Footer>
            <Basic.Button
              level="link"
              onClick={ this.closeDetail.bind(this) }
              showLoading={ _showLoading }>
              { this.i18n('button.close') }
            </Basic.Button>
            <Basic.Button
              type="submit"
              level="success"
              showLoading={ _showLoading }
              showLoadingIcon
              showLoadingText={ this.i18n('button.saving') }
              rendered={ TEST_ADD_ROLE_DIRECTLY }
              onClick={ this.save.bind(this) }>
              { this.i18n('button.save') }
            </Basic.Button>
          </Basic.Modal.Footer>
        </Basic.Modal>
      </>
    );
  }
}

IdentityRoleTable.propTypes = {
  uiKey: PropTypes.string.isRequired,
  manager: PropTypes.object,
  /**
   * Rendered columns - see table columns above
   *
   * TODO: move to advanced table and add column sorting
   */
  columns: PropTypes.arrayOf(PropTypes.string),
  /**
   * "Hard filters"
   */
  forceSearchParameters: PropTypes.object,
  /**
   * Button for create user will be shown
   */
  showAddButton: PropTypes.bool,
  /**
   * Button for show entity detail
   */
  showDetailButton: PropTypes.bool,
  /**
   * Css
   */
  className: PropTypes.string,
  _permissions: PropTypes.oneOfType([
    PropTypes.bool,
    PropTypes.arrayOf(PropTypes.string)
  ]),
  /**
   * Load incompatible roles internally - incompatible roles can be pre loaded from outside.
   */
  fetchIncompatibleRoles: PropTypes.bool,
  /**
   * Load codeLists internally - codeLists can be pre loaded from outside.
   */
  fetchCodeLists: PropTypes.bool
};

IdentityRoleTable.defaultProps = {
  manager,
  rendered: true,
  columns: ConfigLoader.getConfig('identityRole.table.columns', [
    'role',
    'roleAttributes',
    'environment',
    'owner',
    'contractPosition',
    'validFrom',
    'validTill',
    'directRole',
    'automaticRole',
    'incompatibleRoles',
    'description',
    'priority'
  ]),
  forceSearchParameters: null,
  showAddButton: true,
  showDetailButton: true,
  fetchIncompatibleRoles: true,
  fetchCodeLists: true,
  showRefreshButton: true,
  _permissions: null
};

function select(state, component) {
  return {
    i18nReady: state.config.get('i18nReady'),
    showEnvironment: ConfigurationManager.getPublicValueAsBoolean(state, 'idm.pub.app.show.environment', true),
    _showLoading: Utils.Ui.isShowLoading(state, component.uiKey),
    _searchParameters: Utils.Ui.getSearchParameters(state, component.uiKey),
    _incompatibleRoles: DataManager.getData(state, `${ uiKeyIncompatibleRoles }${ component.match.params.entityId }`),
    environmentItems: codeListManager.getCodeList(state, 'environment'),
    columns: component.columns || ConfigurationManager.getPublicValueAsArray(
      state,
      'idm.pub.app.show.identityRole.table.columns',
      IdentityRoleTable.defaultProps.columns
    )
  };
}

export default connect(select, null, null, { forwardRef: true })(IdentityRoleTable);
