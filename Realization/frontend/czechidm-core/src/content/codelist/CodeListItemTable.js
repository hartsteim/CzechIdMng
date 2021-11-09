import PropTypes from 'prop-types';
import React from 'react';
import { connect } from 'react-redux';
import _ from 'lodash';
//
import * as Basic from '../../components/basic';
import * as Advanced from '../../components/advanced';
import * as Utils from '../../utils';
import * as Domain from '../../domain';
import { CodeListManager, CodeListItemManager } from '../../redux';

const manager = new CodeListItemManager();
const codeListManager = new CodeListManager();

/**
 * Table of code list items.
 *
 * @author Radek Tomiška
 * @since 9.4.0
 */
class CodeListItemTable extends Advanced.AbstractTableContent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      ...this.state,
      filterOpened: props.filterOpened,
      formDefinition: null
    };
  }

  getContentKey() {
    return 'content.code-lists.items';
  }

  getManager() {
    return manager;
  }

  getUiKey() {
    return this.props.uiKey;
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
    if (!Utils.Entity.isNew(entity)) {
      this.context.store.dispatch(this.getManager().fetchPermissions(entity.id, `${this.getUiKey()}-detail`));
    }
    const { codeList } = this.props;
    this.setState({
      detail: {
        show: true,
        showLoading: true,
        entity: {},
      },
      formDefinition: null
    }, () => {
      const entityFormData = _.merge({}, entity, {
        codeList,
        codeable: {
          code: entity.code,
          name: entity.name
        }
      });
      //
      this.setState({
        detail: {
          show: true,
          showLoading: false,
          entity: entityFormData,
        },
        formDefinition: codeList.formDefinition
      }, () => {
        this.refs.form.setData(entityFormData);
        this.refs.codeable.focus();
      });
    });
  }

  save(entity, event) {
    if (event) {
      event.preventDefault();
    }
    if (!this.refs.form.isFormValid() || (this.refs.formInstance && !this.refs.formInstance.isValid())) {
      return;
    }
    const formEntity = this.refs.form.getData();
    formEntity.code = formEntity.codeable.code;
    formEntity.name = formEntity.codeable.name;
    //
    // append eav values
    formEntity._eav = [{
      formDefinition: this.state.formDefinition,
      values: this.refs.formInstance.getValues()
    }];
    //
    super.save(formEntity, event);
  }

  afterSave(entity, error) {
    if (!error) {
      this.addMessage({ message: this.i18n('save.success', { count: 1, record: this.getManager().getNiceLabel(entity) }) });
      this.refs.table.reload();
      // clean up codelist in redux state => enforce loading
      if (entity._embedded && entity._embedded.codeList) {
        this.context.store.dispatch(codeListManager.clearCodeList(entity._embedded.codeList.code));
      }
    }
    //
    super.afterSave(entity, error);
  }

  render() {
    const { uiKey, codeList, _permissions, _showLoading } = this.props;
    const { filterOpened, detail, formDefinition } = this.state;
    let formInstance = new Domain.FormInstance({});
    if (formDefinition) {
      formInstance = new Domain.FormInstance(formDefinition);
      // resolve filled values
      if (detail.entity._eav && detail.entity._eav.length > 0) {
        formInstance = formInstance.setValues(detail.entity._eav[0].values);
      }
    }
    //
    return (
      <Basic.Div>
        <Advanced.Table
          ref="table"
          uiKey={ uiKey }
          showRowSelection
          manager={ this.getManager() }
          forceSearchParameters={ new Domain.SearchParameters().setFilter('codeListId', codeList.id) }
          rowClass={ ({rowIndex, data}) => { return data[rowIndex].disabled ? 'disabled' : ''; } }
          className="no-margin"
          filter={
            <Advanced.Filter onSubmit={this.useFilter.bind(this)}>
              <Basic.AbstractForm ref="filterForm">
                <Basic.Row className="last">
                  <Basic.Col lg={ 6 }>
                    <Advanced.Filter.TextField
                      ref="text"
                      placeholder={ this.i18n('filter.text.placeholder') }/>
                  </Basic.Col>
                  <Basic.Col lg={ 6 } className="text-right">
                    <Advanced.Filter.FilterButtons cancelFilter={ this.cancelFilter.bind(this) }/>
                  </Basic.Col>
                </Basic.Row>
              </Basic.AbstractForm>
            </Advanced.Filter>
          }
          buttons={
            [
              <Basic.Button
                level="success"
                key="add_button"
                className="btn-xs"
                onClick={ this.showDetail.bind(this, { }) }
                rendered={ this.getManager().canSave() }
                icon="fa:plus">
                { this.i18n('button.add') }
              </Basic.Button>
            ]
          }
          afterBulkAction={
            (processedBulkAction) => {
              // clean all codelists in redux state, after delete bulk action ends
              if (processedBulkAction.id === 'core-code-list-item-delete-bulk-action') {
                this.context.store.dispatch(codeListManager.clearCodeLists());
              }
            }
          }
          filterOpened={ !filterOpened }
          _searchParameters={ this.getSearchParameters() }>
          <Advanced.Column
            header=""
            className="detail-button"
            cell={
              ({ rowIndex, data }) => {
                return (
                  <Advanced.DetailButton
                    title={ this.i18n('button.detail') }
                    onClick={ this.showDetail.bind(this, data[rowIndex]) }/>
                );
              }
            }
            sort={ false }/>
          <Advanced.Column property="code" sort/>
          <Advanced.Column
            property="name"
            sort
            cell={
              ({ rowIndex, data, property }) => {
                const name = data[rowIndex][property];
                return (
                  <span>
                    { this.i18n(name) }
                  </span>
                );
              }
            }/>
        </Advanced.Table>

        <Basic.Modal
          bsSize="large"
          show={ detail.show }
          onHide={ this.closeDetail.bind(this) }
          backdrop="static"
          keyboard={ !_showLoading }
          showLoading={ detail.showLoading }>
          <Basic.Modal.Header
            closeButton={ !_showLoading }
            text={ this.i18n('create.header') }
            rendered={ Utils.Entity.isNew(detail.entity) }/>
          <Basic.Modal.Header
            closeButton={ !_showLoading }
            text={ this.i18n('edit.header', { record: this.getManager().getNiceLabel(detail.entity) }) }
            rendered={ !Utils.Entity.isNew(detail.entity) }/>
          <Basic.Modal.Body>
            <form onSubmit={ this.save.bind(this, {}) }>
              <Basic.AbstractForm
                ref="form"
                showLoading={ _showLoading }
                readOnly={ !manager.canSave(detail.entity, _permissions) }>

                <Basic.SelectBox
                  ref="codeList"
                  manager={ codeListManager }
                  label={ this.i18n('entity.CodeListItem.codeList.label') }
                  helpBlock={ this.i18n('entity.CodeListItem.codeList.help') }
                  readOnly
                  required/>

                <Advanced.CodeableField
                  ref="codeable"
                  codeLabel={ this.i18n('entity.CodeListItem.code.label') }
                  codeHelpBlock={ this.i18n('entity.CodeListItem.code.help') }
                  nameLabel={ this.i18n('entity.CodeListItem.name.label') }
                  nameHelpBlock={ this.i18n('entity.CodeListItem.name.help') }/>

                <Basic.Div style={ formInstance.getAttributes().size > 0 ? {} : { display: 'none' }}>
                  <Basic.ContentHeader text={ this.i18n('content.code-lists.attributes.header') }/>
                  <Advanced.EavForm
                    ref="formInstance"
                    formInstance={ formInstance }
                    useDefaultValue
                    readOnly={ !manager.canSave(detail.entity, _permissions) }/>
                </Basic.Div>
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
              onClick={ this.save.bind(this, {}) }>
              { this.i18n('button.save') }
            </Basic.Button>
          </Basic.Modal.Footer>
        </Basic.Modal>
      </Basic.Div>
    );
  }
}

CodeListItemTable.propTypes = {
  filterOpened: PropTypes.bool,
  uiKey: PropTypes.string.isRequired,
  codeList: PropTypes.string.isRequired
};

CodeListItemTable.defaultProps = {
  filterOpened: true,
};

function select(state, component) {
  return {
    _searchParameters: state.data.ui[component.uiKey] ? state.data.ui[component.uiKey].searchParameters : null,
    _showLoading: Utils.Ui.isShowLoading(state, `${component.uiKey}-detail`),
    _permissions: Utils.Permission.getPermissions(state, `${component.uiKey}-detail`)
  };
}

export default connect(select)(CodeListItemTable);
