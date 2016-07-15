'use strict';

import React, { PropTypes } from 'react';
import Helmet from 'react-helmet';
import { connect } from 'react-redux';
import _ from 'lodash';
//
import * as Basic from '../../../../components/basic';
import * as Advanced from '../../../../components/advanced';
import * as Utils from '../../utils';
import SearchParameters from '../../domain/SearchParameters';
import { IdentityRoleManager, IdentityManager, RoleManager, WorkflowProcessInstanceManager, DataManager } from '../../redux';
import AuthoritiesPanel from '../role/AuthoritiesPanel';
import authorityHelp from '../role/AuthoritiesPanel_cs.md';

const uiKey = 'identity-roles';
const uiKeyAuthorities = 'identity-roles';
const roleManager = new RoleManager();
const identityRoleManager = new IdentityRoleManager();
const identityManager = new IdentityManager();
const workflowProcessInstanceManager = new WorkflowProcessInstanceManager();

class Roles extends Basic.AbstractContent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      detail: {
        show: false,
        entity: {}
      }
    }
  }

  getContentKey() {
    return 'content.user.roles';
  }

  componentDidMount() {
    this.selectSidebarItem('profile-roles');
    const { userID } = this.props.params;
    this.context.store.dispatch(identityRoleManager.fetchRoles(userID, `${uiKey}-${userID}`));
    this.context.store.dispatch(identityManager.fetchAuthorities(userID, `${uiKeyAuthorities}-${userID}`));
  }

  componentWillReceiveProps(nextProps){
    const {_addRoleProcessIds} = nextProps;
    if (_addRoleProcessIds && _addRoleProcessIds !== this.props._addRoleProcessIds){
      for (let idProcess of _addRoleProcessIds) {
        let processEntity = workflowProcessInstanceManager.getEntity(this.context.store.getState(), idProcess);
        if (processEntity && !roleManager.isShowLoading(this.context.store.getState(), `role-${processEntity.processVariables.roleIdentifier}`)){
          this.context.store.dispatch(roleManager.fetchEntityIfNeeded(processEntity.processVariables.roleIdentifier, `role-${processEntity.processVariables.roleIdentifier}`));
        }
      }
    }
  }

  showDetail(entity) {
    const entityFormData = _.merge({}, entity, {
      role: entity.id ? entity._embedded.role.name : null
    });

    this.setState({
      detail: {
        show: true,
        showLoading: false,
        entity: entityFormData
      }
    }, () => {
      this.refs.form.setData(entityFormData);
      this.refs.role.focus();
    });
  }

  closeDetail() {
    this.setState({
      detail: {
        ... this.state.detail,
        show: false
      }
    });
  }

  save(event) {
    if (event) {
      event.preventDefault();
    }
    if (!this.refs.form.isFormValid()) {
      return;
    }
    const entity = this.refs.form.getData();
    const { userID } = this.props.params;
    const role = roleManager.getEntity(this.context.store.getState(), entity.role);
    entity.identity = identityManager.getSelfLink(userID);
    entity.role = role._links.self.href;
    //
    if (entity.id === undefined) {
      this.context.store.dispatch(identityRoleManager.createEntity(entity, `${uiKey}-${userID}`, (savedEntity, error) => {
        if (!error) {
          this.addMessage({ message: this.i18n('create.success', { role: role.name, username: userID }) });
          this._afterSave(error);
        } else if (error.statusCode === 202) {
          this.addMessage({ level: 'info', message: this.i18n('create.accepted', { role: role.name, username: userID }) });
          this.refs.tableProcesses.getWrappedInstance().reload();
          this.closeDetail();
        } else {
          this._afterSave(error);
        }
      }));
    } else {
      this.context.store.dispatch(identityRoleManager.patchEntity(entity, `${uiKey}-${userID}`, (savedEntity, error) => {
        this._afterSave(error);
        if (!error) {
          this.addMessage({ message: this.i18n('edit.success', { role: role.name, username: userID }) });
        }
      }));
    }
  }

  _afterSave(error) {
    if (error) {
      this.refs.form.processEnded();
      this.addError(error);
      return;
    }
    const { userID } = this.props.params;
    this.context.store.dispatch(identityManager.fetchAuthorities(userID, `${uiKeyAuthorities}-${userID}`));
    this.closeDetail();
  }

  onDelete(entity, event) {
    if (event) {
      event.preventDefault();
    }
    const { userID } = this.props.params;
    this.refs['confirm-delete'].show(
      this.i18n(`action.delete.message`, { count: 1, record: entity._embedded.role.name }),
      this.i18n(`action.delete.header`, { count: 1 })
    ).then(result => {
      this.context.store.dispatch(identityRoleManager.deleteEntity(entity, `${uiKey}-${userID}`, (deletedEntity, error) => {
        if (!error) {
          this.addMessage({ message: this.i18n('delete.success', { role: deletedEntity._embedded.role.name, username: userID }) });
          this.context.store.dispatch(identityManager.fetchAuthorities(userID, `${uiKeyAuthorities}-${userID}`));
        } else {
          this.addError(error);
        }
      }));
    }, (err) => {
      //Rejected
    });
  }

  _onDeleteAddRoleProcessInstance(entity, event) {
    if (event) {
      event.preventDefault();
    }
    const { userID } = this.props.params;
    this.refs['confirm-delete'].show(
      this.i18n('content.user.roles.addRoleProcesse.deleteConfirm', {'processId': entity.id}),
      this.i18n(`action.delete.header`, { count: 1 })
    ).then(result => {
      this.context.store.dispatch(workflowProcessInstanceManager.deleteEntity(entity, null, (deletedEntity, error) => {
        if (!error) {
          this.addMessage({ message: this.i18n('content.user.roles.addRoleProcesse.deleteSuccess', {'processId': entity.id})});
        } else {
          this.addError(error);
        }
      }));
    }, (err) => {
      //Rejected
    });
  }

  _roleNameCell({rowIndex, data, property, ...props}) {
    const role = roleManager.getEntity(this.context.store.getState(), data[rowIndex].processVariables.roleIdentifier);
    if (role){
      return role.name;
    }
    return null;
  }

  render() {
    const { userID } = this.props.params;
    const { _entities, _showLoading, authorities } = this.props;
    const { detail } = this.state;
    let force = new SearchParameters();
    force = force.setFilter('identity', userID);
    force = force.setFilter('processDefinitionKey', 'approveRoleBySuperAdminRole');

    //
    // sort entities by role name
    // TODO: add sort by validFrom?
    const entities = _.slice(_entities).sort((a, b) => {
      return a._embedded.role.name > b._embedded.role.name;
    });
    //
    return (
      <div>
        <Basic.Confirm ref="confirm-delete" level="danger"/>
        <Helmet title={this.i18n('title')} />

        <Basic.Row>
          <div className="col-lg-8">
            <Basic.Panel style={{ marginTop: 15 }}>
              <Basic.PanelHeader text={this.i18n('navigation.menu.roles.title')}/>
              {
                _showLoading
                ?
                <Basic.Loading showLoading={true} className="static"/>
                :
                <div>
                  <Basic.Toolbar>
                    <div className="pull-right">
                      <Basic.Button level="success" className="btn-xs" onClick={this.showDetail.bind(this, {})}>
                        <Basic.Icon value="fa:plus"/>
                        {' '}
                        {this.i18n('button.add')}
                      </Basic.Button>
                    </div>
                    <div className="clearfix"></div>
                  </Basic.Toolbar>
                  <Basic.Table
                    data={entities}
                    showRowSelection={false}>
                    <Basic.Column
                      header=""
                      className="detail-button"
                      cell={
                        ({ rowIndex, data }) => {
                          return (
                            <Advanced.DetailButton
                              title={this.i18n('button.detail')}
                              onClick={this.showDetail.bind(this, data[rowIndex])}/>
                          );
                        }
                      }
                      sort={false}/>
                    <Basic.Column
                      header={this.i18n('entity.IdentityRole.role')}
                      property="_embedded.role.name"
                      />
                    <Basic.Column
                      property="validFrom"
                      header={this.i18n('label.validFrom')}
                      cell={<Basic.DateCell format={this.i18n('format.date')}/>}
                      />
                    <Basic.Column
                      property="validTill"
                      header={this.i18n('label.validTill')}
                      cell={<Basic.DateCell format={this.i18n('format.date')}/>}/>
                    <Basic.Column
                      header={this.i18n('label.action')}
                      className="action"
                      cell={
                        ({rowIndex, data, property, ...props}) => {
                          return (
                            <Basic.Button
                              level="danger"
                              onClick={this.onDelete.bind(this, data[rowIndex])}
                              className="btn-xs"
                              title={this.i18n('button.delete', { delegate: data[rowIndex]._embedded.role.name })}
                              titlePlacement="bottom">
                              <Basic.Icon icon="trash"/>
                            </Basic.Button>
                          );
                        }
                      }/>
                    </Basic.Table>
                  </div>
                }
              </Basic.Panel>
            </div>

            <div className="col-lg-4">
              <Basic.Panel  style={{ marginTop: 15 }}>
                <Basic.PanelHeader help={authorityHelp}>
                  <h3><span dangerouslySetInnerHTML={{ __html: 'Přidělená oprávnění <small>dle přiřazných rolí</small>' }}/></h3>
                </Basic.PanelHeader>
                <Basic.PanelBody>
                  <AuthoritiesPanel
                    roleManager={roleManager}
                    authorities={authorities}
                    disabled={true}/>
                </Basic.PanelBody>
              </Basic.Panel>
            </div>
          </Basic.Row>

          <Basic.Panel>
            <Basic.PanelHeader text=  {this.i18n('addRoleProcesse.header')}/>
            <Advanced.Table
              ref="tableProcesses"
              uiKey="table-processes"
              forceSearchParameters={force}
              manager={workflowProcessInstanceManager}>
              <Advanced.Column
                property="detail"
                cell={<Advanced.DetailButton
                  title={this.i18n('button.detail')}
                  onClick={this.showDetail.bind(this)}/>}
                header={' '}
                sort={false}
                face="text"/>

              <Advanced.Column
                property="currentTaskDefinition.name"
                header={this.i18n('content.roles.processAdd.currentActivity')}
                sort={false}
                face="text"/>
              <Advanced.Column
                property="processVariables.roleIdentifier"
                cell={this._roleNameCell.bind(this)}
                header={this.i18n('content.roles.processAdd.roleName')}
                sort={false}
                face="text"/>
              <Advanced.Column
                property="processVariables.validFrom"
                header={this.i18n('content.roles.processAdd.roleValidFrom')}
                sort={false}
                face="date"/>
              <Advanced.Column
                property="processVariables.validTill"
                header={this.i18n('content.roles.processAdd.roleValidTill')}
                sort={false}
                face="date"/>
              <Advanced.Column
                header={this.i18n('label.action')}
                className="action"
                cell={
                  ({rowIndex, data, property, ...props}) => {
                    return (
                      <Basic.Button
                        level="danger"
                        onClick={this._onDeleteAddRoleProcessInstance.bind(this, data[rowIndex])}
                        className="btn-xs"
                        title={this.i18n('button.delete')}
                        titlePlacement="bottom">
                        <Basic.Icon icon="trash"/>
                      </Basic.Button>
                    );
                  }
                }/>
            </Advanced.Table>
          </Basic.Panel>
          <Basic.Modal
            bsSize="default"
            show={detail.show}
            onHide={this.closeDetail.bind(this)}
            backdrop="static"
            keyboard={!_showLoading}>

            <form onSubmit={this.save.bind(this)}>
              <Basic.Modal.Header closeButton={!_showLoading} text={this.i18n('create.header')} rendered={detail.entity.id === undefined}/>
              <Basic.Modal.Header closeButton={!_showLoading} text={this.i18n('edit.header', { role: detail.entity.role })} rendered={detail.entity.id !== undefined}/>
              <Basic.Modal.Body>
                <Basic.AbstractForm ref="form" showLoading={_showLoading}>
                  <Basic.SelectBox
                    ref="role"
                    manager={roleManager}
                    label={this.i18n('entity.IdentityRole.role')}
                    required/>
                  <Basic.DateTimePicker
                    mode="date"
                    ref="validFrom"
                    label={this.i18n('label.validFrom')}/>
                  <Basic.DateTimePicker
                    mode="date"
                    ref="validTill"
                    label={this.i18n('label.validTill')}/>
                </Basic.AbstractForm>
              </Basic.Modal.Body>

              <Basic.Modal.Footer>
                <Basic.Button
                  level="link"
                  onClick={this.closeDetail.bind(this)}
                  showLoading={_showLoading}>
                  {this.i18n('button.close')}
                </Basic.Button>
                <Basic.Button
                  type="submit"
                  level="success"
                  showLoading={_showLoading}
                  showLoadingIcon={true}
                  showLoadingText={this.i18n('button.saving')}>
                  {this.i18n('button.save')}
                </Basic.Button>
              </Basic.Modal.Footer>
            </form>
          </Basic.Modal>
        </div>
      );
    }
  }

  Roles.propTypes = {
    _showLoading: PropTypes.bool,
    _entities: PropTypes.arrayOf(React.PropTypes.object),
    authorities: PropTypes.arrayOf(React.PropTypes.object)
  }
  Roles.defaultProps = {
    _showLoading: true,
    _entities: [],
    authorities: []
  }

  function select(state, component) {
    let addRoleProcessIds;
    if (state.data.ui['table-processes'] && state.data.ui['table-processes'].items){
      addRoleProcessIds = state.data.ui['table-processes'].items;
    }

    return {
      _showLoading: identityRoleManager.isShowLoading(state, `${uiKey}-${component.params.userID}`),
      _entities: identityRoleManager.getEntities(state, `${uiKey}-${component.params.userID}`),
      _addRoleProcessIds: addRoleProcessIds,
      authorities: DataManager.getData(state, `${uiKeyAuthorities}-${component.params.userID}`)
    };
  }

  export default connect(select)(Roles);
