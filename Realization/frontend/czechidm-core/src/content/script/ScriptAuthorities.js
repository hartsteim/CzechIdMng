import PropTypes from 'prop-types';
import React from 'react';
import { connect } from 'react-redux';
import Helmet from 'react-helmet';
//
import * as Basic from '../../components/basic';
import * as Advanced from '../../components/advanced';
import * as Utils from '../../utils';
import { SecurityManager, ScriptAuthorityManager, DataManager, ScriptManager } from '../../redux';
import ScriptAuthorityTypeEnum from '../../enums/ScriptAuthorityTypeEnum';

// const uiKey = 'script-authorities';
const scriptManager = new ScriptManager();
const AVAILABLE_SERVICES_UIKEY = 'availableServicesUiKey';
const manager = new ScriptAuthorityManager();

/**
 * Authority tab for script
 *
 * @author Patrik Stloukal
 *
 * TODO: RT: refactor this content - use Advanced.AbstractTableContent
 */
class ScriptAuthorities extends Basic.AbstractContent {

  constructor(props, context) {
    super(props, context);
    const detail = {
      show: false,
      entity: null,
      isService: true
    };
    //
    this.state = {
      _showLoading: true,
      filterOpened: false,
      detail
    };
    //
    this.context.store.dispatch(manager.fetchAvailableServices(AVAILABLE_SERVICES_UIKEY));
  }

  getContentKey() {
    return 'content.scripts.authorities';
  }

  getNavigationKey() {
    return 'script-authorities';
  }

  onDelete(bulkActionValue, selectedRows) {
    const { uiKey } = this.props;
    const selectedEntities = manager.getEntitiesByIds(this.context.store.getState(), selectedRows);
    //
    // show confirm message for deleting entity or entities
    this.refs['confirm-' + bulkActionValue].show(
      this.i18n(`action.${bulkActionValue}.message`, { count: selectedEntities.length, record: manager.getNiceLabel(selectedEntities[0]), records: manager.getNiceLabels(selectedEntities).join(', ') }),
      this.i18n(`action.${bulkActionValue}.header`, { count: selectedEntities.length, records: manager.getNiceLabels(selectedEntities).join(', ') })
    ).then(() => {
      // try delete
      this.context.store.dispatch(manager.deleteEntities(selectedEntities, uiKey, (entity, error, successEntities) => {
        if (entity && error) {
          this.addErrorMessage({ title: this.i18n(`action.delete.error`, { record: manager.getNiceLabel(entity) }) }, error);
        }
        if (!error && successEntities) {
          // refresh data in table
          this.refs.table.reload();
        }
      }));
    }, () => {
      //
    });
  }

  /**
   * Save new or old script authority
   */
  save(values, event) {
    const { uiKey, _entity } = this.props;
    const entity = this.refs.form.getData();
    //
    if (event) {
      event.preventDefault();
    }
    //
    if (!this.refs.form.isFormValid()) {
      return;
    }
    //
    this.setState({
      _showLoading: true
    }, this.refs.form.processStarted());

    // if type == SERVICE remove unless attribute className
    if (ScriptAuthorityTypeEnum.findSymbolByKey(entity.type) === ScriptAuthorityTypeEnum.SERVICE) {
      delete entity.className;
    }
    // set script id into script authority
    entity.script = _entity.id;

    //
    if (entity.id === undefined) {
      this.context.store.dispatch(manager.createEntity(entity, `${uiKey}-detail`, (createdEntity, error) => {
        this._afterSave(createdEntity, error);
      }));
    } else {
      this.context.store.dispatch(manager.updateEntity(entity, `${uiKey}-detail`, this._afterSave.bind(this)));
    }
  }

  /**
   * Method set _showLoading to false and if isn't error then show success message
   */
  _afterSave(entity, error) {
    let { detail } = this.state;
    if (error) {
      this.setState({
        _showLoading: false
      }, this.refs.form.processEnded());
      this.addError(error);
      return;
    }
    this.addMessage({ message: this.i18n('save.success', { name: entity.name }) });
    this.refs.table.reload();
    this.refs.form.processEnded();
    //
    detail = {
      ...detail,
      show: false,
      entity: null
    };
    //
    this.setState({
      detail,
      _showLoading: false
    });
  }

  getOptions(services) {
    if (!services) {
      return null;
    }
    const servicesOptions = [];
    if (services && services._embedded && services._embedded.availableServices) {
      for (let index = 0; index < services._embedded.availableServices.length; index++) {
        const service = services._embedded.availableServices[index];
        const row = {
          niceLabel: service.serviceName,
          value: service.serviceName
        };
        servicesOptions.push(row);
      }
    }
    return servicesOptions;
  }

  /**
   * Change type, show dynamical text field with class name or service type
   */
  onChangeType(entity, event) {
    if (event) {
      event.preventDefault();
    }
    const { detail } = this.state;
    this.setState({
      detail: {
        ...detail,
        isService: entity.value === ScriptAuthorityTypeEnum.findKeyBySymbol(ScriptAuthorityTypeEnum.SERVICE)
      }
    }, () => {
      this.refs.service.setValue(null);
    });
  }

  closeDetail(entity, event) {
    let { detail } = this.state;
    if (event) {
      event.preventDefault();
    }
    detail = {
      ...detail,
      show: false,
      entity: null
    };
    this.setState({
      detail
    });
  }

  /**
   * Show new form or detail for script authority, show in modal window
   */
  showDetail(entity, event) {
    let { detail } = this.state;
    if (event) {
      event.preventDefault();
    }
    if (Utils.Entity.isNew(entity)) {
      entity.type = ScriptAuthorityTypeEnum.findKeyBySymbol(ScriptAuthorityTypeEnum.CLASS_NAME);
    }
    let isService = false;
    if (entity.type === ScriptAuthorityTypeEnum.findKeyBySymbol(ScriptAuthorityTypeEnum.SERVICE)) {
      isService = true;
    }
    detail = {
      entity,
      show: true,
      isService
    };
    // set detail for modal window
    this.setState({
      detail,
      _showLoading: false
    });
  }

  render() {
    const { uiKey, rendered, availableServices, _entity } = this.props;
    const { filterOpened, detail, _showLoading } = this.state;
    const script = _entity;

    if (!rendered || this.props._entity == null) {
      return null;
    }

    return (
      <div>
        <Helmet title={ this.i18n('title') } />
        <Basic.Confirm ref="confirm-delete" level="danger"/>
        <Basic.ContentHeader text={ this.i18n('header') } style={{ marginBottom: 0 }}/>

        <Advanced.Table
          ref="table"
          uiKey={uiKey}
          manager={manager}
          forceSearchParameters={manager.getDefaultSearchParameters().setFilter('scriptId', script.id)}
          showRowSelection={SecurityManager.hasAuthority('SCRIPT_DELETE')}
          rowClass={({rowIndex, data}) => { return data[rowIndex].disabled ? 'disabled' : ''; }}
          filterOpened={!filterOpened}
          actions={
            [
              { value: 'delete', niceLabel: this.i18n('action.delete.action'), action: this.onDelete.bind(this), disabled: false }
            ]
          }
          buttons={
            [
              <Basic.Button
                level="success"
                key="add_button"
                className="btn-xs"
                onClick={ this.showDetail.bind(this, {}) }
                rendered={ SecurityManager.hasAuthority('SCRIPT_CREATE') }
                icon="fa:plus">
                {this.i18n('button.add')}
              </Basic.Button>
            ]
          }
          className="no-margin">
          <Advanced.Column
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
          <Advanced.Column
            property="type"
            header={ this.i18n('entity.ScriptAuthority.type.label') }
            sort
            face="enum"
            enumClass={ScriptAuthorityTypeEnum}/>
          <Advanced.Column
            property="name"
            sort
            cell={
              ({ rowIndex, data }) => {
                if (ScriptAuthorityTypeEnum.findSymbolByKey(data[rowIndex].type) === ScriptAuthorityTypeEnum.SERVICE) {
                  return data[rowIndex].service;
                }
                return data[rowIndex].className;
              }
            }
          />
        </Advanced.Table>

        <Basic.Modal
          bsSize="sm"
          show={ detail.show }
          onHide={ this.closeDetail.bind(this) }
          backdrop="static"
          keyboard={ !_showLoading }>

          <Basic.Modal.Header
            closeButton={ !_showLoading }
            text={ this.i18n('create.header', { name: script.name }) }
            rendered={ Utils.Entity.isNew(detail.entity)}/>
          <Basic.Modal.Header
            closeButton={ !_showLoading }
            text={ this.i18n('edit.header', { name: script.name }) }
            rendered={ !Utils.Entity.isNew(detail.entity) }/>
          <Basic.Modal.Body>
            <form onSubmit={ this.save.bind(this, {}) }>
              <Basic.AbstractForm
                ref="form"
                data={ detail.entity }
                showLoading={ _showLoading }>
                <Basic.EnumSelectBox
                  ref="type"
                  clearable={ false }
                  enum={ ScriptAuthorityTypeEnum }
                  onChange={ this.onChangeType.bind(this) }
                  label={ this.i18n('entity.ScriptAuthority.type.label') }
                  palceholder={ this.i18n('entity.ScriptAuthority.type.placeholder') }
                  helpBlock={ this.i18n('entity.ScriptAuthority.type.help') }/>
                <Basic.EnumSelectBox
                  required={detail.isService}
                  searchable
                  ref="service"
                  hidden={!detail.isService}
                  options={this.getOptions(availableServices)}
                  label={ this.i18n('entity.ScriptAuthority.type.label') }
                  palceholder={ this.i18n('entity.ScriptAuthority.type.placeholder') }
                  helpBlock={ this.i18n('entity.ScriptAuthority.type.help') }/>
                <Basic.TextField
                  required={!detail.isService}
                  hidden={detail.isService}
                  ref="className"
                  label={this.i18n('entity.ScriptAuthority.className')}
                  max={2000}/>
              </Basic.AbstractForm>
              {/* onEnter action - is needed because footer submit button is outside form */}
              <input type="submit" className="hidden"/>
            </form>
          </Basic.Modal.Body>

          <Basic.Modal.Footer>
            <Basic.Button
              level="link"
              onClick={ this.closeDetail.bind(this) }
              showLoading={_showLoading}>
              {this.i18n('button.close')}
            </Basic.Button>
            <Basic.Button
              type="submit"
              onClick={ this.save.bind(this, {}) }
              level="success"
              showLoading={ _showLoading }
              showLoadingIcon
              showLoadingText={ this.i18n('button.saving') }
              rendered={ SecurityManager.hasAuthority('SCRIPT_CREATE') }>
              { this.i18n('button.save') }
            </Basic.Button>
          </Basic.Modal.Footer>
        </Basic.Modal>
      </div>
    );
  }
}

ScriptAuthorities.propTypes = {
  _entity: PropTypes.object,
  _permissions: PropTypes.arrayOf(PropTypes.string),
  uiKey: PropTypes.string.isRequired,
  script: PropTypes.object.isRequired,
  rendered: PropTypes.bool.isRequired
};
ScriptAuthorities.defaultProps = {
  _entity: null,
  _permissions: null,
  rendered: true
};

function select(state, component) {
  return {
    _entity: scriptManager.getEntity(state, component.match.params.entityId),
    _permissions: scriptManager.getPermissions(state, null, component.match.params.entityId),
    availableServices: DataManager.getData(state, AVAILABLE_SERVICES_UIKEY)
  };
}

export default connect(select)(ScriptAuthorities);
