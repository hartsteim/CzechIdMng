import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import _ from 'lodash';
import Helmet from 'react-helmet';
//
import { Basic, Advanced, Managers, Utils, Domain } from 'czechidm-core';
import { AccountManager, SystemEntityManager, SystemManager, SystemMappingManager } from '../../redux';
import AccountTypeEnum from '../../domain/AccountTypeEnum';
import SystemOperationTypeEnum from '../../domain/SystemOperationTypeEnum';
import AttributeTable from './AttributeTable';
import SystemEntityTypeManager from '../../redux/SystemEntityTypeManager';
import IdmContext from 'czechidm-core/src/context/idm-context';
import AccountWizardDetail from '../wizard/AccountWizardDetail';

const manager = new AccountManager();
const systemEntityManager = new SystemEntityManager();
const systemManager = new SystemManager();
const systemMappingManager = new SystemMappingManager();
const systemEntityTypeManager = new SystemEntityTypeManager();
const roleManager = new Managers.RoleManager();
const identityManager = new Managers.IdentityManager();

/**
 * Accounts on target system
 *
 * @author Vít Švanda
 * @author Radek Tomiška
 * @author Roman Kucera
 */
export class AccountTable extends Advanced.AbstractTableContent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      ...this.state,
      systemEntity: null,
      attributeNameFilter: null,
      showWizard: false
    };
    this.context.store.dispatch(manager.fetchSupportedTypes());
  }

  getManager() {
    return manager;
  }

  getUiKey() {
    return this.props.uiKey;
  }

  getContentKey() {
    return 'acc:content.system.accounts';
  }

  showDetail(entity) {
    if (!Utils.Entity.isNew(entity)) {
      this.context.store.dispatch(this.getManager().fetchPermissions(entity.id, `${this.getUiKey()}-detail`));
    }
    this.setState({
      systemEntity: entity.systemEntity
    }, () => {
      if (!Utils.Entity.isNew(entity) && Managers.SecurityManager.hasAuthority('SYSTEM_READ')) {
        manager.getService()
          .getConnectorObject(entity.id)
          .then(json => {
            this.setState({connectorObject: json});
          })
          .catch(error => {
            this.addError(error);
          });
      }
      super.showDetail(entity, () => {
      // TODO after full detail is implemented we can maybe focus something
        // this.refs.uid.focus();
      });
    });
  }

  closeDetail() {
    super.closeDetail();
    delete this.state.connectorObject;
  }

  save(entity, event) {
    const formEntity = this.refs.form.getData();
    //
    super.save(formEntity, event);
  }

  afterSave(entity, error) {
    if (!error) {
      this.addMessage({ message: this.i18n('save.success', { name: entity.uid }) });
    }
    super.afterSave(entity, error);
  }

  onChangeSystemEntity(systemEntity) {
    this.setState({
      systemEntity
    });
  }

  reload() {
    this.refs.table.reload();
  }

  /**
  * Method get last string of split string by dot.
  * Used for get niceLabel for type entity.
  */
  _getType(name) {
    return Utils.Ui.getSimpleJavaType(name);
  }

  renderTargetEntity({rowIndex, data}) {
    return (
      <Advanced.EntityInfo
        entityType={ this._getType(data[rowIndex].targetEntityType) }
        entityIdentifier={ data[rowIndex].targetEntityId}
        entity={ data[rowIndex]._embedded ? data[rowIndex]._embedded.targetEntityId : null }
        showIcon
        face="popover"
        showEntityType/>
    );
  }

  renderAccountOwner({rowIndex, data}) {
    return (
      <Advanced.EntityInfo
        entityType={ this._getType(data[rowIndex].targetEntityType) }
        entityIdentifier={ data[rowIndex].targetEntityId}
        entity={ data[rowIndex]._embedded ? data[rowIndex]._embedded.targetEntityId : null }
        showIcon
        face="popover"
        showEntityType/>
    );
  }

  closeWizard(finished, wizardContext) {
    this.setState({
      showWizard: false
    }, () => {
    // TODO redirect to full detail
      // if (finished && wizardContext && wizardContext.entity) {
      //   this.context.history.push(`/system/${wizardContext.entity.id}/detail`);
      // }
    });
  }

  render() {
    const {
      _showLoading,
      uiKey,
      forceSearchParameters,
      forceSystemEntitySearchParameters,
      columns,
      _permissions,
      showAddButton,
      className,
      filterOpened,
      _showEchoMetadata,
      renderAccountType,
      renderSystem,
      supportedTypes
    } = this.props;
    const { detail, systemEntity, connectorObject, showWizard, accountWizard } = this.state;
    //
    let systemId = null;
    if (forceSearchParameters.getFilters().has('systemId')) {
      systemId = forceSearchParameters.getFilters().get('systemId');
    } 
    if (systemId == null && detail && detail.entity) {
      systemId = detail.entity.system
    }

    const forceSearchMappings = new Domain.SearchParameters()
      .setFilter('operationType', SystemOperationTypeEnum.findKeyBySymbol(SystemOperationTypeEnum.PROVISIONING))
      .setFilter('systemId', systemId || Domain.SearchParameters.BLANK_UUID);

    let supportedTypesValues = [];
    if(supportedTypes) {
      supportedTypesValues = Array.from(supportedTypes.values());
    }

    return (
      <Basic.Div>
        {!showWizard &&
           <Helmet title={this.i18n('acc:content.accounts.header')} />
        }
        <Basic.Confirm ref="confirm-delete" level="danger"/>
        <Advanced.Table
          ref="table"
          uiKey={ uiKey }
          manager={ this.getManager() }
          forceSearchParameters={ forceSearchParameters }
          showRowSelection={ Managers.SecurityManager.hasAnyAuthority(['ACCOUNT_DELETE']) }
          className={ className }
          rowClass={ ({rowIndex, data}) => { return (data[rowIndex].inProtection) ? 'disabled' : ''; } }
          actions={
            Managers.SecurityManager.hasAnyAuthority(['ACCOUNT_DELETE'])
            ?
            [{ value: 'delete', niceLabel: this.i18n('action.delete.action'), action: this.onDelete.bind(this), disabled: false }]
            :
            null
          }
          buttons={
            [
              <Basic.Button
                level="success"
                key="add_button"
                className="btn-xs"
                onClick={ this.showDetail.bind(this, { system: systemId })}
                rendered={ showAddButton && Managers.SecurityManager.hasAnyAuthority(['ACCOUNT_CREATE']) }
                icon="fa:plus">
                { this.i18n('button.add') }
              </Basic.Button>
            ]
          }
          filter={
            <Filter
              ref="filterForm"
              onSubmit={ this.useFilter.bind(this) }
              onCancel={ this.cancelFilter.bind(this) }
              filterOpened={ filterOpened }
              renderAccountType={ renderAccountType }
              renderSystem={ renderSystem }
              forceSearchParameters={ forceSearchParameters }/>
          }
          _searchParameters={ this.getSearchParameters() }>
          <Advanced.Column
            property=""
            header=""
            className="detail-button"
            rendered={ Managers.SecurityManager.hasAllAuthorities(['SYSTEM_READ']) }
            cell={
              ({ rowIndex, data }) => {
                return (
                  <Advanced.DetailButton
                    title={this.i18n('button.detail')}
                    onClick={this.showDetail.bind(this, data[rowIndex])}/>
                );
              }
            }/>
          <Advanced.ColumnLink
            to={
              ({ rowIndex, data }) => {
                this.showDetail(data[rowIndex]);
              }
            }
            property="uid"
            sort
            header={ this.i18n('acc:entity.Account.uid') }
            rendered={ _.includes(columns, 'uid') }/>
          <Advanced.Column
            property="targetEntity"
            rendered={ _.includes(columns, 'targetEntity') }
            header={ this.i18n('acc:entity.Account.targetEntity') }
            cell={ this.renderTargetEntity.bind(this) }
          />
          <Advanced.Column
            header={ this.i18n('acc:entity.System.name') }
            rendered={ _.includes(columns, 'system') }
            sort
            sortProperty="system.name"
            cell={
              /* eslint-disable react/no-multi-comp */
              ({rowIndex, data}) => {
                return (
                  <Advanced.EntityInfo
                    entityType="system"
                    entityIdentifier={ data[rowIndex].system }
                    entity={ data[rowIndex]._embedded.system }
                    face="popover"
                    showIcon/>
                );
              }
            }/>
          <Advanced.Column
            property="inProtection"
            header={ this.i18n('acc:entity.Account.inProtection') }
            face="bool"
            sort
            rendered={ _.includes(columns, 'inProtection') }/>
          <Advanced.Column
            property="endOfProtection"
            header={ this.i18n('acc:entity.Account.endOfProtection') }
            face="datetime"
            sort
            rendered={ _.includes(columns, 'endOfProtection') }/>
        </Advanced.Table>

        <Basic.Modal
          bsSize="large"
          show={ detail.show }
          onHide={ this.closeDetail.bind(this) }
          backdrop="static"
          keyboard={ !_showLoading }>
          <Basic.Modal.Header
            closeButton={ !_showLoading }
            text={ this.i18n('create.header') }
            rendered={ Utils.Entity.isNew(detail.entity) }/>
          <Basic.Modal.Header
            closeButton={ !_showLoading }
            text={ this.i18n('edit.header', { name: detail.entity.name }) }
            rendered={ !Utils.Entity.isNew(detail.entity) }/>
          <Basic.Modal.Body>
            <Basic.Div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'center', margin: '10px 0' }}>
                {
                  !supportedTypesValues
                  ||
                  supportedTypesValues.map(accountWizard => (
                    <Basic.Button
                      buttonSize="large"
                      level='default'
                      style={{ minWidth: 240, height: 125, margin: 15 }}
                      onClick={ (event) => {
                        if (event) {
                          event.preventDefault();
                        }
                        // start wizard
                        this.setState({
                          showWizard: true,
                          accountWizard: accountWizard
                        });
                        this.closeDetail();
                      }}>
                      <Basic.Icon
                        type='fa'
                        icon="plus"
                        style={{ display: 'block', marginBottom: 10 }}
                        className="fa-2x"/>
                      {this.i18n(`${accountWizard.module}:content.system.accounts.${accountWizard.name}.title`)}
                    </Basic.Button>
                  ))
                }
              </Basic.Div>
          </Basic.Modal.Body>
          <Basic.Modal.Footer>
            <Basic.Button
              level="link"
              onClick={ this.closeDetail.bind(this) }
              showLoading={ _showLoading }>
              { this.i18n('button.close') }
            </Basic.Button>
          </Basic.Modal.Footer>
        </Basic.Modal>

        {accountWizard && 
          <IdmContext.Provider value={{ ...this.context }}>
            <Basic.Div rendered={showWizard}>
              <Helmet title={this.i18n(`${accountWizard.module}:content.system.accounts.${accountWizard.name}.header`)} />
              <AccountWizardDetail
                match={this.props.match}
                closeWizard={this.closeWizard.bind(this)}
                connectorType={accountWizard}
                show={showWizard} />
            </Basic.Div>
          </IdmContext.Provider>
        }

      </Basic.Div>
    );
  }
}

AccountTable.propTypes = {
  uiKey: PropTypes.string.isRequired,
  forceSearchParameters: PropTypes.object.isRequired,
  forceSystemEntitySearchParameters: PropTypes.object,
  /**
   * Button for create user will be shown
   */
  showAddButton: PropTypes.bool,
  //
  _showLoading: PropTypes.bool
};
AccountTable.defaultProps = {
  columns: ['accountType', 'entityType', 'uid', 'system', 'inProtection', 'endOfProtection', 'systemEntity', 'targetEntity'],
  showAddButton: true,
  _showLoading: false,
};

function select(state, component) {
  const {uiKey} = component;
  return {
    i18nReady: state.config.get('i18nReady'),
    _showLoading: Utils.Ui.isShowLoading(state, `${uiKey}-detail`),
    _searchParameters: Utils.Ui.getSearchParameters(state, uiKey),
    _permissions: Utils.Permission.getPermissions(state, `${uiKey}-detail`),
    _showEchoMetadata: Managers.ConfigurationManager.showSystemInformation(state),
    supportedTypes: Managers.DataManager.getData(state, AccountManager.UI_KEY_SUPPORTED_TYPES),
  };
}

export default connect(select, null, null, { forwardRef: true})(AccountTable);

/**
 * Table filter component
 *
 * @author Radek Tomiška
 */
class Filter extends Advanced.Filter {

  render() {
    const { onSubmit, onCancel, forceSearchParameters, renderAccountType, renderSystem } = this.props;

    //
    return (
      <Advanced.Filter onSubmit={ onSubmit }>
        <Basic.AbstractForm ref="filterForm">
          <Basic.Row>
            <Basic.Col lg={ 2 }>
              <Advanced.Filter.TextField
                ref="text"
                placeholder={ this.i18n('acc:content.accounts.filter.accounts.identifier.placeholder') }/>
            </Basic.Col>
            <Basic.Col lg={ 3 } rendered = { renderSystem }>
              <Advanced.Filter.SelectBox
                ref="systems"
                manager={ systemManager }
                multiSelect
                placeholder={ this.i18n('acc:content.accounts.filter.accounts.system.placeholder') }/>
            </Basic.Col>
            <Basic.Col lg={ 3 }>
            <Advanced.Filter.RoleSelect
                ref="roles"
                label={ null }
                manager={ roleManager }
                placeholder={ this.i18n('acc:content.accounts.filter.accounts.roles.placeholder') }
                header={ this.i18n('filter.role.placeholder') }
                rendered={ Managers.SecurityManager.hasAuthority('ROLE_AUTOCOMPLETE') }
                multiSelect/>
            </Basic.Col>
            <Basic.Col lg={ 4 } className="text-right">
              <Advanced.Filter.FilterButtons cancelFilter={ onCancel }/>
            </Basic.Col>
          </Basic.Row>
          <Basic.Row className="last">
            <Basic.Col lg={ 3 }>
              <Advanced.Filter.IdentitySelect
                label={ null }
                manager={ identityManager }
                placeholder={ this.i18n('acc:content.accounts.filter.accounts.identity.placeholder') }
                multiSelect
                ref="identities"/>
            </Basic.Col>
            <Basic.Col lg={ 2 } rendered = { renderAccountType }>
              <Advanced.Filter.EnumSelectBox
                ref="accountType"
                placeholder={ this.i18n('acc:content.accounts.filter.accounts.type.placeholder') }
                enum={ AccountTypeEnum }/>
            </Basic.Col>
            <Basic.Col lg={ 4 }>
              <Advanced.Filter.SelectBox
                ref="entityType"
                placeholder={ this.i18n('acc:entity.SystemEntity.entityType') }
                multiSelect={ false }
                face='text'
                manager={ systemEntityTypeManager }
                rendered={ !forceSearchParameters.getFilters().has('entityType') }/>
            </Basic.Col>
            <Basic.Col lg={ 2 }>
              <Advanced.Filter.BooleanSelectBox
                ref="inProtection"
                placeholder={ this.i18n('acc:content.accounts.filter.accounts.protected.placeholder') }
                options={ [
                  { value: 'true', niceLabel: this.i18n('acc:content.accounts.filter.accounts.protected.yes') },
                  { value: 'false', niceLabel: this.i18n('acc:content.accounts.filter.accounts.protected.no') }
                ]}/>
            </Basic.Col>
            
            <Basic.Col lg={ 4 } />
          </Basic.Row>
        </Basic.AbstractForm>
      </Advanced.Filter>
    );
  }
}
