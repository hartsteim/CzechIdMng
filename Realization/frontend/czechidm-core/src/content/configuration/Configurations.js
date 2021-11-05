import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import _ from 'lodash';
//
import IconButton from '@material-ui/core/IconButton';
import InvertColors from '@material-ui/icons/InvertColors';
//
import * as Basic from '../../components/basic';
import * as Advanced from '../../components/advanced';
import { ConfigurationManager, DataManager, SecurityManager, IdentityManager } from '../../redux';
import * as Utils from '../../utils';

const uiKey = 'configuration-table';
const manager = new ConfigurationManager();
const identityManager = new IdentityManager();

const IDM_CONFIGURATION_PREFIX = 'idm.';

/**
 * Application configurations.
 *
 * @author Radek Tomiška
 * @author Patrik Stroukal
 */
class Configurations extends Advanced.AbstractTableContent {

  constructor(props, context) {
    super(props, context);
    //
    //
    const { userContext } = props;
    let collapsedTableFile = true;
    let collapsedTableEnvironment = true;
    if (userContext && userContext.profile && userContext.profile.setting) { // or personalized by profile
      if (userContext.profile.setting[`${ uiKey }-file-panel`]) {
        collapsedTableFile = !!userContext.profile.setting[`${ uiKey }-file-panel`].collapsed;
      }
      if (userContext.profile.setting[`${ uiKey }-environment-panel`]) {
        collapsedTableEnvironment = !!userContext.profile.setting[`${ uiKey }-environment-panel`].collapsed;
      }
    }
    //
    this.state = {
      filterOpened: true,
      detail: {
        show: false,
        addMore: false,
        entity: {}
      },
      isGuarded: false,
      isSecured: false,
      showPrefixWarning: false,
      collapsedTableFile,
      collapsedTableEnvironment
    };
  }

  componentDidMount() {
    super.componentDidMount();
    //
    this.context.store.dispatch(this.getManager().fetchAllConfigurationsFromFile());
    this.context.store.dispatch(this.getManager().clearBulkActions()); // ~ reload bulk action default values
    if (SecurityManager.hasAuthority('CONFIGURATION_ADMIN')) {
      this.context.store.dispatch(this.getManager().fetchAllConfigurationsFromEnvironment());
    }
    this.refs.text.focus();
  }

  getNavigationKey() {
    return 'system-configuration';
  }

  getManager() {
    return manager;
  }

  getUiKey() {
    return uiKey;
  }

  getContentKey() {
    return 'content.configuration';
  }

  useFilter(event) {
    if (event) {
      event.preventDefault();
    }
    this.refs.table.useFilterForm(this.refs.filterForm);
  }

  cancelFilter(event) {
    if (event) {
      event.preventDefault();
    }
    this.refs.table.cancelFilter(this.refs.filterForm);
  }

  showDetail(entity) {
    const isGuarded = this._getGuarded(entity.confidential, entity.name);
    const isSecured = isGuarded || manager.shouldBeSecured(entity.name);
    //
    if (!Utils.Entity.isNew(entity)) {
      this.context.store.dispatch(this.getManager().fetchPermissions(entity.id, `${this.getUiKey()}-detail`));
    }
    //
    this.setState({
      detail: {
        show: true,
        showLoading: false,
        entity
      },
      isGuarded,
      isSecured
    }, () => {
      if (Utils.Entity.isNew(entity)) {
        if (this.refs.name) {
          this.refs.name.focus();
        }
      } else if (this.refs.value) {
        this.refs.value.focus();
      }
    });
  }

  closeDetail() {
    this.setState({
      detail: {
        show: false,
        entity: {}
      }
    }, this.loadFilter);
  }

  showAddMore(entity) {
    this.setState({
      detail: {
        addMore: true,
        showLoading: false,
        entity
      },
    }, () => {
      setTimeout(() => {
        this.refs.area.focus();
      }, 10);
    });
  }

  closeAddMore() {
    this.setState({
      detail: {
        addMore: false,
        entity: {}
      }
    }, this.loadFilter);
  }

  _filterOpen(filterOpened) {
    this.setState({
      filterOpened
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
    //
    if (entity.id === undefined) {
      this.context.store.dispatch(this.getManager().createEntity(entity, `${uiKey}-detail`, (createdEntity, error) => {
        this._afterSave(createdEntity, error);
      }));
    } else {
      this.context.store.dispatch(this.getManager().updateEntity(entity, `${uiKey}-detail`, this._afterSave.bind(this)));
    }
  }

  saveMore(event) {
    if (event) {
      event.preventDefault();
    }
    if (!this.refs.formAddMore.isFormValid()) {
      return;
    }
    const entities = this.refs.area.getValue();
    this.context.store.dispatch(this.getManager().addMoreEntities(entities, `${uiKey}-detail`, this._afterAddMoreSave.bind(this)));
  }

  _afterAddMoreSave(entity, error) {
    if (error) {
      this.refs.formAddMore.processEnded();
      this.addError(error);
      return;
    }
    this.addMessage({ message: this.i18n('save.sucessBulk') });
    this.closeAddMore();
    this.refs.table.reload();
    // reload public configurations
    this.context.store.dispatch(this.getManager().fetchPublicConfigurations());
    // reload all bulk actions
    this.context.store.dispatch(this.getManager().clearAllBulkActions());
  }

  _forceSave(entity) {
    this.context.store.dispatch(this.getManager().fetchEntity(entity.name, entity.name, (entityBackend) => {
      this.refs['confirm-task-save'].show(
        this.i18n(`content.configuration.forceSave.message`, { name: entityBackend.name, value: entityBackend.value }),
        this.i18n(`content.configuration.forceSave.header`)
      ).then(() => {
        entityBackend.value = entity.value;
        this.context.store.dispatch(this.getManager().updateEntity(entityBackend, `${uiKey}-detail`, this._afterSave.bind(this)));
      }, () => {
        this.closeDetail();
      });
    }));
  }

  _afterSave(entity, error) {
    if (error) {
      if (error.statusCode === 409) {
        this._forceSave(this.refs.form.getData());
      } else {
        this.refs.form.processEnded();
        this.addError(error);
      }
    } else {
      this.addMessage({ message: this.i18n('save.success', { name: entity.name }) });
      this.closeDetail();
      this.refs.table.reload();
      // reload public configurations
      this.context.store.dispatch(this.getManager().fetchPublicConfigurations());
      // reload all bulk actions
      this.context.store.dispatch(this.getManager().clearAllBulkActions());
      // reload application logo
      if (entity.code === 'idm.pub.app.show.logo') {
        this.context.store.dispatch(this.getManager().downloadApplicationLogo());
      }
      if (entity.code === 'idm.pub.app.show.theme') {
        this.context.store.dispatch(this.getManager().fetchApplicationTheme());
      }
    }
  }

  _changeName(event) {
    // check guarded depents on new entity name
    const confidential = this.refs.confidential.getValue();
    const name = event.currentTarget.value;
    //
    // if name doesn't start with prefix "idm.*"
    // defined in constatn IDM_CONFIGURATION_PREFIX show info message
    let showPrefixWarning = false;
    const containsPrefix = name.startsWith(IDM_CONFIGURATION_PREFIX);
    if (!containsPrefix && name.length >= IDM_CONFIGURATION_PREFIX.length) {
      showPrefixWarning = true;
    }
    this.setState({
      showPrefixWarning
    });
    //
    this._setForceProperties(confidential, name);
    //
    // if name constains also value (characters after =)
    // set this characters directly into value (1 = value, 0 = key)
    const keyAndValue = _.split(name, '=');
    if (keyAndValue.length === 2 && keyAndValue[1].length > 0) {
      this.refs.value.setValue(keyAndValue[1]);
      event.currentTarget.value = keyAndValue[0]; // setValue on name doestn work
      this.refs.value.focus();
    }
  }

  _changeConfidential(event) {
    const confidential = event.currentTarget.checked;
    const name = this.refs.name.getValue();
    //
    this._setForceProperties(confidential, name);
  }

  _setForceProperties(confidential, entityName) {
    const prevGuarded = this.state.isGuarded;
    const isGuarded = this._getGuarded(confidential, entityName);
    const isSecured = isGuarded || manager.shouldBeSecured(entityName);
    this.setState({
      isGuarded,
      isSecured
    }, () => {
      if (isGuarded) {
        this.refs.confidential.setValue(true);
      } else if (prevGuarded) {
        this.refs.value.setValue(null);
      }
      //
      if (isSecured) {
        this.refs.public.setValue(false);
      }
    });
  }

  _getGuarded(confidential, entityName) {
    if (manager.shouldBeGuarded(entityName)) {
      return 'by_name'; // has higher priority
    }

    if (confidential) {
      return 'by_confidential';
    }
    return false;
  }

  _onToogleCollapse(tableUiKey, collapsed) {
    const { userContext } = this.props;
    if (userContext && userContext.username) {
      if (collapsed) {
        this.context.store.dispatch(identityManager.collapsePanel(userContext.username, tableUiKey));
      } else {
        this.context.store.dispatch(identityManager.expandPanel(userContext.username, tableUiKey));
      }
    }
  }

  render() {
    const {
      _showLoading,
      fileConfigurations,
      _fileConfigurationsShowLoading,
      environmentConfigurations,
      _environmentConfigurationsShowLoading,
      _permissions
    } = this.props;
    //
    const {
      filterOpened,
      detail,
      isGuarded,
      isSecured,
      showPrefixWarning,
      collapsedTableFile,
      collapsedTableEnvironment
    } = this.state;
    const showTables = !(detail.show || detail.addMore);
    //
    return (
      <Basic.Div>
        <Basic.Confirm ref="confirm-delete" level="danger"/>
        <Basic.Confirm ref="confirm-task-save" level="danger"/>

        { this.renderPageHeader() }

        <Basic.ContentHeader
          rendered={ showTables }
          buttons={[
            <Basic.Button
              level="secondary"
              startIcon={ <InvertColors /> }
              title={ this.i18n('content.configuration-theme.action.edit.link.title') }
              onClick={() => {
                this.context.history.push('/configurations/theme');
              }}>
              { this.i18n('content.configuration-theme.action.edit.link.label') }
            </Basic.Button>
          ]}>
          { this.i18n('configurable', { escape: false }) }
        </Basic.ContentHeader>

        <Basic.Panel className={ detail.show || detail.addMore ? 'hidden' : '' }>
          <Advanced.Table
            ref="table"
            uiKey={ uiKey }
            manager={ this.getManager() }
            showRowSelection
            rendered={ !detail.show }
            filter={
              <Advanced.Filter onSubmit={ this.useFilter.bind(this) }>
                <Basic.AbstractForm ref="filterForm">
                  <Basic.Row className="last">
                    <Basic.Col lg={ 8 }>
                      <Advanced.Filter.TextField
                        ref="text"
                        placeholder={ this.i18n('entity.Configuration.name') }/>
                    </Basic.Col>
                    <Basic.Col lg={ 4 } className="text-right">
                      <Advanced.Filter.FilterButtons cancelFilter={ this.cancelFilter.bind(this) }/>
                    </Basic.Col>
                  </Basic.Row>
                </Basic.AbstractForm>
              </Advanced.Filter>
            }
            filterOpened={ filterOpened }
            filterOpen={ this._filterOpen.bind(this) }
            buttons={
              [
                <span>
                  <span style={{ marginRight: 3 }}>
                    <Basic.Button
                      level="success"
                      key="add_button"
                      className="btn-xs"
                      onClick={ this.showDetail.bind(this, { public: true }) }
                      rendered={ manager.canSave() }
                      icon="fa:plus">
                      { this.i18n('button.add') }
                    </Basic.Button>
                  </span>
                  <span>
                    <Basic.Button
                      level="success"
                      key="addMore_button"
                      className="btn-xs"
                      onClick={ this.showAddMore.bind(this, { public: true }) }
                      rendered={ manager.canSave() }
                      icon="fa:plus">
                      { this.i18n('button.addMore') }
                    </Basic.Button>
                  </span>
                </span>
              ]
            }
            afterBulkAction={
              (processedBulkAction) => {
                // clean all bulk actions in redux
                if (processedBulkAction.id === 'configuration-delete-bulk-action') {
                  // reload public configurations
                  this.context.store.dispatch(this.getManager().fetchPublicConfigurations());
                  // reload all bulk actions
                  this.context.store.dispatch(this.getManager().clearAllBulkActions());
                  // reload application logo
                  this.context.store.dispatch(this.getManager().downloadApplicationLogo());
                  // reload application theme
                  this.context.store.dispatch(this.getManager().fetchApplicationTheme());
                } else if (processedBulkAction.id === 'core-configuration-switch-instance-bulk-action') {
                  // reload all bulk actions
                  this.context.store.dispatch(this.getManager().clearBulkActions());
                  // enforce reload for underlying table
                  this.refs.table.reloadBulkActions();
                }
              }
            }
            _searchParameters={ this.getSearchParameters()} >
            <Advanced.Column
              property=""
              header=""
              className="detail-button"
              cell={
                ({ rowIndex, data }) => (
                  <Advanced.DetailButton
                    title={ this.i18n('button.detail') }
                    onClick={ this.showDetail.bind(this, data[rowIndex]) }/>
                )
              }/>
            <Advanced.Column property="name" sort width={ 250 }/>
            <Advanced.Column property="value" sort/>
            <Advanced.Column property="confidential" sort face="bool" width={ 150 }/>
            <Advanced.Column property="public" face="bool" width={ 100 }/>
          </Advanced.Table>
        </Basic.Panel>

        <Basic.Modal
          bsSize="large"
          show={ detail.show }
          onHide={ this.closeDetail.bind(this) }
          backdrop="static"
          keyboard={ !_showLoading }>
          <Basic.Modal.Header
            closeButton={ !_showLoading }
            text={ this.i18n('create.header') }
            rendered={ detail.entity.id === undefined }/>
          <Basic.Modal.Header
            closeButton={ !_showLoading }
            text={ this.i18n('edit.header', { name: detail.entity.name }) }
            rendered={ detail.entity.id !== undefined }
            buttons={[
              <Advanced.AuditableInfo entity={ detail.entity }/>
            ]}/>
          <Basic.Modal.Body>
            <form onSubmit={ this.save.bind(this) }>
              <Basic.AbstractForm
                ref="form"
                data={ detail.entity }
                showLoading={ _showLoading }
                readOnly={ !manager.canSave(detail.entity, _permissions) }>
                <Basic.Alert
                  text={ this.i18n('nameDoesntContainPrefixAlert', { prefix: IDM_CONFIGURATION_PREFIX, escape: false }) }
                  level="warning"
                  rendered={ showPrefixWarning }/>
                <Basic.TextField
                  ref="name"
                  label={ this.i18n('entity.Configuration.name') }
                  onChange={ this._changeName.bind(this) }
                  required
                  helpBlock={ this.i18n('guarded', { guarded: ConfigurationManager.GUARDED_PROPERTY_NAMES.join(', '), escape: false }) }
                  max={ 255 }/>
                <Basic.TextField
                  type={ isGuarded ? 'password' : 'text' }
                  ref="value"
                  label={ this.i18n('entity.Configuration.value') }
                  confidential={ isGuarded !== false }
                  max={ 2000 }/>
                <Basic.Checkbox
                  ref="confidential"
                  label={ this.i18n('entity.Configuration.confidential') }
                  helpBlock={ this.i18n('confidential.help') }
                  onChange={ this._changeConfidential.bind(this) }
                  readOnly={ isGuarded === 'by_name' }/>
                <Basic.Checkbox
                  ref="public"
                  label={ this.i18n('entity.Configuration.public') }
                  readOnly={ isSecured }
                  helpBlock={ this.i18n('secured.help') }/>
              </Basic.AbstractForm>
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
              rendered={ manager.canSave(detail.entity, _permissions) }
              onClick={ this.save.bind(this) }>
              { this.i18n('button.save') }
            </Basic.Button>
          </Basic.Modal.Footer>
        </Basic.Modal>

        <Basic.Modal
          bsSize="large"
          show={ detail.addMore }
          onHide={ this.closeAddMore.bind(this) }
          backdrop="static"
          keyboard={ !_showLoading }>

          <form onSubmit={ this.saveMore.bind(this) }>
            <Basic.Modal.Header closeButton={ !_showLoading } text={ this.i18n('addMore.header') } rendered/>
            <Basic.Modal.Body>
              <Basic.AbstractForm
                ref="formAddMore"
                showLoading={ _showLoading }
                readOnly={ !manager.canSave(detail.entity, _permissions) }>
                <Basic.TextArea
                  ref="area"
                  label={ this.i18n('addMore.configurationArea.header') }
                  required
                  helpBlock={ this.i18n('addMore.configurationArea.helpBlock') }
                />
              </Basic.AbstractForm>
            </Basic.Modal.Body>

            <Basic.Modal.Footer>
              <Basic.Button
                level="link"
                onClick={ this.closeAddMore.bind(this) }
                showLoading={ _showLoading }>
                { this.i18n('button.close') }
              </Basic.Button>
              <Basic.Button
                type="submit"
                level="success"
                showLoading={ _showLoading }
                showLoadingIcon
                showLoadingText={ this.i18n('button.saving') }
                rendered={ manager.canSave() }>
                { this.i18n('button.save') }
              </Basic.Button>
            </Basic.Modal.Footer>
          </form>
        </Basic.Modal>

        <Basic.ContentHeader rendered={ showTables }>
          { this.i18n('environment', { escape: false }) }
        </Basic.ContentHeader>

        <Basic.Panel rendered={ showTables }>
          <Basic.Table
            uiKey={ `${ uiKey }-file-panel` }
            header={ this.i18n('fromFile', { escape: false }) }
            data={ fileConfigurations }
            showLoading={ _fileConfigurationsShowLoading }
            noData={ this.i18n('component.basic.Table.noData') }
            supportsPagination
            collapsible={ this._onToogleCollapse.bind(this) }
            collapsed={ collapsedTableFile }>
            <Basic.Column
              property=""
              header=""
              className="edit-button"
              width={ 20 }
              cell={
                ({ rowIndex, data }) => (
                  <Basic.Button
                    title={ this.i18n('button.edit') }
                    className="btn-xs"
                    icon="fa:pencil"
                    onClick={ this.showDetail.bind(this, data[rowIndex]) }/>
                )
              }/>
            <Basic.Column property="name" header={ this.i18n('entity.Configuration.name') } width={ 250 }/>
            <Basic.Column property="value" header={ this.i18n('entity.Configuration.value') } />
            <Basic.Column
              property="confidential"
              header={ <Basic.Cell className="column-face-bool">{ this.i18n('entity.Configuration.confidential') }</Basic.Cell> }
              cell={ <Basic.BooleanCell className="column-face-bool"/> }
              width={ 150 }/>
            <Basic.Column
              property="public"
              header={ <Basic.Cell className="column-face-bool">{ this.i18n('entity.Configuration.public') }</Basic.Cell> }
              cell={ <Basic.BooleanCell className="column-face-bool"/> }
              width={ 100 }/>
          </Basic.Table>
        </Basic.Panel>

        {
          !SecurityManager.hasAuthority('CONFIGURATION_ADMIN')
          ||
          <Basic.Panel rendered={ showTables }>
            <Basic.Table
              uiKey={ `${ uiKey }-environment-panel` }
              data={ environmentConfigurations }
              header={ this.i18n('fromEnvironment', { escape: false }) }
              showLoading={ _environmentConfigurationsShowLoading }
              noData={ this.i18n('component.basic.Table.noData') }
              supportsPagination
              collapsible={ this._onToogleCollapse.bind(this) }
              collapsed={ collapsedTableEnvironment }>
              <Basic.Column property="name" header={ this.i18n('entity.Configuration.name') } width={ 150 }/>
              <Basic.Column property="value" header={ this.i18n('entity.Configuration.value') }/>
            </Basic.Table>
          </Basic.Panel>
        }
      </Basic.Div>
    );
  }
}

Configurations.propTypes = {
  fileConfigurations: PropTypes.arrayOf(PropTypes.object),
  environmentConfigurations: PropTypes.arrayOf(PropTypes.object),
  _permissions: PropTypes.arrayOf(PropTypes.string)
};

Configurations.defaultProps = {
  fileConfigurations: [],
  _showLoading: false,
  _fileConfigurationsShowLoading: false,
  _environmentConfigurationsShowLoading: false,
  _permissions: null
};

function select(state) {
  return {
    i18nReady: state.config.get('i18nReady'),
    userContext: state.security.userContext,
    _searchParameters: Utils.Ui.getSearchParameters(state, uiKey),
    _showLoading: Utils.Ui.isShowLoading(state, `${uiKey}-detail`),
    fileConfigurations: DataManager.getData(state, ConfigurationManager.FILE_CONFIGURATIONS),
    _fileConfigurationsShowLoading: Utils.Ui.isShowLoading(state, ConfigurationManager.FILE_CONFIGURATIONS),
    environmentConfigurations: DataManager.getData(state, ConfigurationManager.ENVIRONMENT_CONFIGURATIONS),
    _environmentConfigurationsShowLoading: Utils.Ui.isShowLoading(state, ConfigurationManager.ENVIRONMENT_CONFIGURATIONS),
    _permissions: Utils.Permission.getPermissions(state, `${uiKey}-detail`)
  };
}

export default connect(select)(Configurations);
