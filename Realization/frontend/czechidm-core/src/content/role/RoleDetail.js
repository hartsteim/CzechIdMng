import PropTypes from 'prop-types';
import React from 'react';
import Helmet from 'react-helmet';
import _ from 'lodash';
import { connect } from 'react-redux';
import uuid from 'uuid';
//
import * as Basic from '../../components/basic';
import * as Advanced from '../../components/advanced';
import * as Utils from '../../utils';
import RoleTypeEnum from '../../enums/RoleTypeEnum';
import RolePriorityEnum from '../../enums/RolePriorityEnum';
import { RoleManager, SecurityManager, RequestManager, ConfigurationManager } from '../../redux';
import RequestTable from '../request/RequestTable';
import SearchParameters from '../../domain/SearchParameters';

let roleManager = null;
const uiKeyRoleRequest = 'role-universal-request-table';
const requestManager = new RequestManager();

/**
 * Role detail
 *
 * @author Ondřej Kopr
 * @author Radek Tomiška
 */
class RoleDetail extends Basic.AbstractContent {

  constructor(props) {
    super(props);
    this.state = {
      _showLoading: true,
      activeKey: 1
    };
  }

  getContentKey() {
    return 'content.roles';
  }

  componentDidMount() {
    const { entity } = this.props;
    // Init manager - evaluates if we want to use standard (original) manager or
    // universal request manager (depends on existing of 'requestId' param)
    roleManager = this.getRequestManager(this.props.match.params, new RoleManager());

    if (Utils.Entity.isNew(entity)) {
      entity.priorityEnum = RolePriorityEnum.findKeyBySymbol(RolePriorityEnum.NONE);
      entity.priority = `${RolePriorityEnum.getPriority(RolePriorityEnum.NONE)}`;
      this._setSelectedEntity(entity);
    } else {
      this._setSelectedEntity(this._prepareEntity(entity));
    }
  }

  // @Deprecated - since V10 ... replaced by dynamic key in Route
  // UNSAFE_componentWillReceiveProps(nextProps) {
  //   const { entity } = this.props;
  //   if ((nextProps.match.params && this.props.match.params && nextProps.match.params.requestId !== this.props.match.params.requestId)
  //     || (nextProps.entity && nextProps.entity !== entity)) {
  //     // Init manager - evaluates if we want to use standard (original) manager or
  //     // universal request manager (depends on existing of 'requestId' param)
  //     roleManager = this.getRequestManager(nextProps.match.params, new RoleManager());
  //   }
  //   if (nextProps.entity && nextProps.entity !== entity && nextProps.entity) {
  //     this._setSelectedEntity(this._prepareEntity(nextProps.entity));
  //   }
  // }

  _prepareEntity(entity) {
    const copyOfEntity = _.merge({}, entity); // we can not modify given entity
    // we dont need to load entities again - we have them in embedded objects
    copyOfEntity.priorityEnum = RolePriorityEnum.findKeyBySymbol(RolePriorityEnum.getKeyByPriority(copyOfEntity.priority));
    copyOfEntity.priority += ''; // We have to do convert form int to string (cause TextField and validator)
    return copyOfEntity;
  }

  _setSelectedEntity(entity) {
    this.setState({
      _showLoading: false
    }, () => {
      entity.codeable = {
        code: entity.baseCode,
        name: entity.name
      };
      this.refs.form.setData(entity);
      this.refs.codeable.focus();
    });
  }

  save(afterAction, event) {
    if (event) {
      event.preventDefault();
    }
    if (!this.refs.form.isFormValid()) {
      return;
    }
    this.setState({
      _showLoading: true
    }, () => {
      const entity = this.refs.form.getData();
      entity.baseCode = entity.codeable.code;
      entity.name = entity.codeable.name;
      this.refs.form.processStarted();
      //
      this.getLogger().debug('[RoleDetail] save entity', entity);
      if (Utils.Entity.isNew(entity)) {
        this.context.store.dispatch(roleManager.createEntity(entity, null, (createdEntity, error) => {
          this._afterSave(createdEntity, error, afterAction);
        }));
      } else {
        this.context.store.dispatch(roleManager.updateEntity(entity, null, (patchedEntity, error) => {
          this._afterSave(patchedEntity, error, afterAction);
        }));
      }
    });
  }

  _afterSave(entity, error, afterAction = 'CLOSE') {
    this.setState({
      _showLoading: false
    }, () => {
      this.refs.form.processEnded();
      if (error) {
        this.addError(error);
        return;
      }
      //
      this.addMessage({ message: this.i18n('save.success', { name: entity.name }) });
      if (afterAction === 'CLOSE') {
        this.context.history.replace(this.addRequestPrefix('roles', this.props.match.params));
      } else if (afterAction === 'NEW') {
        const uuidId = uuid.v1();
        const newEntity = {
          priority: `${ RolePriorityEnum.getPriority(RolePriorityEnum.NONE) }`, // conversion to string
          priorityEnum: RolePriorityEnum.findKeyBySymbol(RolePriorityEnum.NONE)
        };
        this.context.store.dispatch(roleManager.receiveEntity(uuidId, newEntity));
        this.context.history.replace(`${this.addRequestPrefix('role', this.props.match.params)}/${ uuidId }/new?new=1`);
        this._setSelectedEntity(newEntity);
      } else {
        this.context.history.replace(`${this.addRequestPrefix('role', this.props.match.params) }/${ entity.id }/detail`);
        // reload code (baseCode|environment) in form is needed ... TODO: data prop on form can be used instead.
        this._setSelectedEntity(this._prepareEntity(entity));
      }
    });
  }

  _onChangePriorityEnum(item) {
    if (item) {
      const priority = RolePriorityEnum.getPriority(item.value);
      this.refs.priority.setValue(`${ priority }`);
    } else {
      this.refs.priority.setValue(null);
    }
  }

  _onChangeSelectTabs(activeKey) {
    this.setState({
      activeKey
    });
  }

  render() {
    const { entity, showLoading, _permissions, _requestUi, showEnvironment } = this.props;
    const { _showLoading, activeKey } = this.state;
    if (!roleManager || !entity) {
      return null;
    }
    let requestsForceSearch = new SearchParameters();
    requestsForceSearch = requestsForceSearch.setFilter('ownerId', entity.id ? entity.id : SearchParameters.BLANK_UUID);
    requestsForceSearch = requestsForceSearch.setFilter('ownerType', 'eu.bcvsolutions.idm.core.api.dto.IdmRoleDto');
    requestsForceSearch = requestsForceSearch.setFilter('states', ['IN_PROGRESS', 'CONCEPT', 'EXCEPTION']);
    //
    return (
      <Basic.Div style={{ paddingTop: 15 }}>
        <Helmet title={ Utils.Entity.isNew(entity) ? this.i18n('create.header') : this.i18n('edit.title') } />
        <Basic.Tabs
          activeKey={ activeKey }
          onSelect={ this._onChangeSelectTabs.bind(this)}>
          <Basic.Tab eventKey={ 1 } title={ this.i18n('entity.Role._type') } className="bordered">
            <form onSubmit={ this.save.bind(this, 'CONTINUE') }>
              <Basic.ContentHeader
                text={ Utils.Entity.isNew(entity) ? this.i18n('create.header') : this.i18n('tabs.basic') }
                style={{ marginBottom: 0, paddingTop: 15, paddingRight: 15, paddingLeft: 15 }}/>
              <Basic.Panel className="no-border last">
                <Basic.PanelBody style={{ paddingTop: 0, paddingBottom: 0 }}>
                  <Basic.AbstractForm
                    ref="form"
                    showLoading={ _showLoading || showLoading }
                    readOnly={ !roleManager.canSave(entity, _permissions) }>

                    <Advanced.CodeableField
                      ref="codeable"
                      codeLabel={ this.i18n('entity.Role.baseCode.label') }
                      codeHelpBlock={ this.i18n('entity.Role.baseCode.help') }
                      nameLabel={ this.i18n('entity.Role.name') }/>

                    <Basic.Row>
                      <Basic.Col lg={ 4 }>
                        <Advanced.CodeListSelect
                          ref="environment"
                          hidden={ !showEnvironment }
                          code="environment"
                          label={ this.i18n('entity.Role.environment.label') }
                          helpBlock={
                            this.i18n(
                              `entity.Role.environment.${ entity.environment ? 'helpCode' : 'help' }`,
                              { escape: false, code: entity.code }
                            )
                          }
                          max={ 255 }/>
                      </Basic.Col>
                      <Basic.Col lg={ 8 }>
                        <Basic.EnumSelectBox
                          ref="roleType"
                          label={ this.i18n('entity.Role.roleType') }
                          enum={ RoleTypeEnum }
                          useSymbol={ false }/>
                      </Basic.Col>
                    </Basic.Row>

                    <Basic.EnumSelectBox
                      ref="priorityEnum"
                      label={ this.i18n('entity.Role.priorityEnum') }
                      enum={ RolePriorityEnum }
                      onChange={ this._onChangePriorityEnum.bind(this) }
                      clearable={ false }
                      required/>
                    <Basic.TextField
                      ref="priority"
                      label={ this.i18n('entity.Role.priority') }
                      readOnly
                      required/>
                    <Basic.Checkbox
                      ref="approveRemove"
                      label={ this.i18n('entity.Role.approveRemove') }/>
                    <Basic.Checkbox
                      ref="canBeRequested"
                      label={ this.i18n('entity.Role.canBeRequested') }/>
                    <Basic.TextArea
                      ref="description"
                      label={ this.i18n('entity.Role.description') }
                      max={2000}/>
                    <Basic.Checkbox
                      ref="disabled"
                      label={ this.i18n('entity.Role.disabled') }/>
                  </Basic.AbstractForm>
                </Basic.PanelBody>

                <Basic.PanelFooter style={{ paddingLeft: 15, paddingRight: 15 }}>
                  <Basic.Button
                    type="button"
                    level="link"
                    onClick={this.context.history.goBack}
                    showLoading={ _showLoading} >
                    { this.i18n('button.back') }
                  </Basic.Button>
                  <Basic.SplitButton
                    level="success"
                    title={ this.i18n('button.saveAndContinue') }
                    onClick={ this.save.bind(this, 'CONTINUE') }
                    showLoading={ _showLoading }
                    showLoadingIcon
                    showLoadingText={ this.i18n('button.saving') }
                    rendered={ roleManager.canSave(entity, _permissions) }
                    pullRight
                    dropup>
                    <Basic.MenuItem
                      eventKey="1"
                      onClick={ this.save.bind(this, 'CLOSE') }>
                      {this.i18n('button.saveAndClose')}
                    </Basic.MenuItem>
                    <Basic.MenuItem
                      eventKey="2"
                      onClick={ this.save.bind(this, 'NEW') }
                      rendered={ SecurityManager.hasAuthority('ROLE_CREATE') }>
                      { this.i18n('button.saveAndNew') }
                    </Basic.MenuItem>
                  </Basic.SplitButton>
                </Basic.PanelFooter>
              </Basic.Panel>
              {/* onEnter action - is needed because SplitButton is used instead standard submit button */}
              <input type="submit" className="hidden"/>
            </form>
          </Basic.Tab>
          <Basic.Tab
            eventKey={ 2 }
            rendered={ !!(entity.id && SecurityManager.hasAuthority('REQUEST_READ')) }
            disabled={ roleManager.isRequestModeEnabled() || !entity.id }
            title={
              <span>
                { this.i18n('content.requests.header') }
                <Basic.Badge
                  level="warning"
                  style={{ marginLeft: 5 }}
                  text={ _requestUi ? _requestUi.total : null }
                  rendered={!roleManager.isRequestModeEnabled() && _requestUi && _requestUi.total > 0 }
                  title={ this.i18n('content.requests.header') }/>
              </span>
            }
            className="bordered">
            <Basic.ContentHeader
              text={ this.i18n('content.requests.header', { escape: false }) }
              style={{ marginBottom: 0, paddingTop: 15, paddingRight: 15, paddingLeft: 15 }}/>
            <RequestTable
              ref="table"
              uiKey={ uiKeyRoleRequest }
              forceSearchParameters={ requestsForceSearch }
              showFilter={false}
              showLoading={ _showLoading }
              manager={ requestManager }
              columns={[ 'state', 'created', 'modified', 'wf', 'detail' ]}/>
          </Basic.Tab>
        </Basic.Tabs>
      </Basic.Div>
    );
  }
}

RoleDetail.propTypes = {
  entity: PropTypes.object,
  showLoading: PropTypes.bool,
  _permissions: PropTypes.arrayOf(PropTypes.string)
};
RoleDetail.defaultProps = {
  _permissions: null
};

function select(state, component) {
  const result = {
    showEnvironment: ConfigurationManager.getPublicValueAsBoolean(state, 'idm.pub.app.show.environment', true)
  };
  //
  if (!roleManager) {
    return result;
  }
  if (!component.entity) {
    return result;
  }
  return {
    ...result,
    _permissions: roleManager.getPermissions(state, null, component.entity.id),
    _requestUi: Utils.Ui.getUiState(state, uiKeyRoleRequest)
  };
}

export default connect(select)(RoleDetail);
