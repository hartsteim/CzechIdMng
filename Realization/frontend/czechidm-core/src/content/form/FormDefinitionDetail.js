import PropTypes from 'prop-types';
import React from 'react';
import { connect } from 'react-redux';
import classnames from 'classnames';
import Joi from 'joi';
//
import * as Utils from '../../utils';
import * as Basic from '../../components/basic';
import * as Advanced from '../../components/advanced';
import { FormDefinitionManager, DataManager } from '../../redux';

const manager = new FormDefinitionManager();

const TYPES_UIKEY = 'typesUiKey';

/**
* Form definition detail.
*
* @author Ondřej Kopr
* @author Radek Tomiška
*/
class FormDefinitionDetail extends Basic.AbstractContent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      _showLoading: true
    };
  }

  getContentKey() {
    return 'content.formDefinitions';
  }

  componentDidMount() {
    super.componentDidMount();
    //
    const { entityId } = this.props.match.params;
    const { isNew } = this.props;
    this.context.store.dispatch(manager.fetchTypes(TYPES_UIKEY));
    if (isNew) {
      this.context.store.dispatch(manager.receiveEntity(entityId, { unmodifiable: false }, null, () => {
        this.refs.codeable.focus();
      }));
    } else {
      this.getLogger().debug(`[FormDetail] loading entity detail [id:${entityId}]`);
      this.context.store.dispatch(manager.fetchEntity(entityId, null, () => {
        this.refs.codeable.focus();
      }));
    }
  }

  getNavigationKey() {
    return 'forms-detail';
  }

  save(event) {
    const { uiKey } = this.props;

    if (event) {
      event.preventDefault();
    }
    if (!this.refs.form.isFormValid()) {
      return;
    }

    this.setState({
      _showLoading: true
    }, () => {
      this.refs.form.processStarted();
      const entity = this.refs.form.getData();
      entity.code = entity.codeable.code;
      entity.name = entity.codeable.name;
      //
      if (entity.id === undefined) {
        this.context.store.dispatch(manager.createEntity(entity, `${ uiKey }-detail`, (createdEntity, error) => {
          this._afterSave(createdEntity, error);
        }));
      } else {
        this.context.store.dispatch(manager.patchEntity(entity, `${ uiKey }-detail`, this._afterSave.bind(this)));
      }
    });
  }

  /**
  * Just set showloading to false and set processEnded to form.
  * Call after save/create
  */
  _afterSave(entity, error) {
    const { isNew } = this.props;
    if (error) {
      this.setState({
        _showLoading: false
      }, this.refs.form.processEnded());
      this.addError(error);
      return;
    }
    this.setState({
      _showLoading: false
    });
    this.addMessage({ message: this.i18n('save.success', { name: entity.name }) });
    if (isNew) {
      this.context.history.replace(`/forms/form-definitions`);
    }
  }

  onChangeType(definitionType) {
    if (definitionType) {
      this.refs.module.setValue(Utils.Ui.getModuleFromJavaType(definitionType.value));
    } else {
      this.refs.module.setValue(null);
    }
  }

  render() {
    const { uiKey, entity, showLoading, types, _permissions } = this.props;
    //
    return (
      <form onSubmit={ this.save.bind(this) }>
        <Basic.Panel
          className={
            classnames({
              last: !Utils.Entity.isNew(entity),
              'no-border': !Utils.Entity.isNew(entity)
            })
          }>
          <Basic.PanelHeader
            icon="component:form-definition"
            text={ Utils.Entity.isNew(entity) ? this.i18n('create.header') : this.i18n('content.formDefinitions.detail.title') } />
          <Basic.PanelBody style={ Utils.Entity.isNew(entity) ? { paddingTop: 0, paddingBottom: 0 } : { padding: 0 } }>
            <Basic.AbstractForm
              ref="form"
              uiKey={ uiKey }
              data={ entity }
              rendered={ types !== undefined }
              readOnly={ !manager.canSave(entity, _permissions) }>

              <Advanced.CodeableField
                ref="codeable"
                codeLabel={ this.i18n('entity.FormDefinition.code') }
                nameLabel={ this.i18n('entity.FormDefinition.name') }
                codeReadOnly={ !entity || entity.unmodifiable } />

              <Basic.Row>
                <Basic.Col lg={ 4 }>
                  <Basic.EnumSelectBox
                    ref="type"
                    label={ this.i18n('entity.FormDefinition.type') }
                    placeholder={ this.i18n('entity.FormDefinition.type') }
                    required
                    readOnly={ !entity || entity.unmodifiable || !Utils.Entity.isNew(entity) }
                    options={
                      types
                      ?
                      types.map(type => {
                        return { value: type, niceLabel: Utils.Ui.getSimpleJavaType(type) };
                      })
                      :
                      null
                    }
                    searchable
                    clearable={ false }
                    onChange={ this.onChangeType.bind(this) }/>
                </Basic.Col>
                <Basic.Col lg={ 8 }>
                  <Basic.TextField
                    ref="seq"
                    label={ this.i18n('entity.FormDefinition.seq.label') }
                    helpBlock={ this.i18n('entity.FormDefinition.seq.help') }
                    validation={
                      Joi
                        .number()
                        .allow(null)
                        .integer()
                        .min(-32768)
                        .max(32767)
                    }/>
                </Basic.Col>
              </Basic.Row>

              <Basic.TextField
                ref="module"
                label={ this.i18n('entity.FormDefinition.module.label') }
                max={ 255 }
                helpBlock={ this.i18n('entity.FormDefinition.module.help') }/>
              <Basic.Checkbox
                ref="main"
                readOnly={ entity ? entity.main : false }
                label={ this.i18n('entity.FormDefinition.main.label') }
                helpBlock={ this.i18n('entity.FormDefinition.main.help') }/>
              <Basic.Checkbox
                ref="unmodifiable"
                readOnly
                label={ this.i18n('entity.FormDefinition.unmodifiable.label') }
                helpBlock={ this.i18n('entity.FormDefinition.unmodifiable.help') }/>
              <Basic.TextArea
                ref="description"
                label={ this.i18n('entity.FormDefinition.description') }
                rows={ 4 }
                max={ 1000 }/>
            </Basic.AbstractForm>
          </Basic.PanelBody>
          <Basic.PanelFooter showLoading={ showLoading } >
            <Basic.Button type="button" level="link" onClick={ this.context.history.goBack }>
              { this.i18n('button.back') }
            </Basic.Button>
            <Basic.Button
              type="submit"
              level="success"
              showLoadingIcon
              showLoadingText={ this.i18n('button.saving') }
              rendered={ manager.canSave(entity, _permissions) }>
              { this.i18n('button.save') }
            </Basic.Button>
          </Basic.PanelFooter>
        </Basic.Panel>
      </form>
    );
  }
}

FormDefinitionDetail.propTypes = {
  uiKey: PropTypes.string,
  definitionManager: PropTypes.object,
  isNew: PropTypes.bool,
  _permissions: PropTypes.arrayOf(PropTypes.string)
};
FormDefinitionDetail.defaultProps = {
  isNew: false,
  _permissions: null
};

function select(state, component) {
  const { entityId } = component.match.params;
  const entity = manager.getEntity(state, entityId);
  if (entity) {
    entity.codeable = {
      code: entity.code,
      name: entity.name
    };
  }
  //
  return {
    entity,
    showLoading: manager.isShowLoading(state, null, entityId),
    types: DataManager.getData(state, TYPES_UIKEY),
    _permissions: manager.getPermissions(state, null, entityId)
  };
}

export default connect(select)(FormDefinitionDetail);
