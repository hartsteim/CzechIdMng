import PropTypes from 'prop-types';
import React from 'react';
import { connect } from 'react-redux';
import uuid from 'uuid';
//
import * as Basic from '../../components/basic';
import * as Advanced from '../../components/advanced';
import * as Utils from '../../utils';
import { SecurityManager, ConfigurationManager } from '../../redux';

/**
* Table of roles catalogues.
*
* @author Ondřej Kopr
* @author Radek Tomiška
*/
class RoleCatalogueTable extends Advanced.AbstractTableContent {

  constructor(props, context) {
    super(props, context);
    //
    this.state = {
      filterOpened: props.filterOpened,
      selectedNodeId: null // selected node
    };
  }

  getContentKey() {
    return 'content.roleCatalogues';
  }

  getManager() {
    return this.props.roleCatalogueManager;
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
    if (entity.id === undefined) {
      const uuidId = uuid.v1();
      this.context.history.push(`/role-catalogue/${uuidId}/new?new=1`);
    } else {
      this.context.history.push(`/role-catalogue/${entity.id}/detail`);
    }
  }

  _useFilterByTree(nodeId, event) {
    if (event) {
      event.preventDefault();
      // Stop propagation is important for prohibition of node tree expand.
      // After click on link node, we want only filtering ... not node expand.
      event.stopPropagation();
    }
    const data = {
      ...this.refs.filterForm.getData(),
      parent: nodeId
    };
    this.setState({
      selectedNodeId: nodeId
    }, () => {
      this.refs.parent.setValue(nodeId);
      this.refs.table.useFilterData(data);
    });
  }

  _renderSelectedNode() {
    const { selectedNodeId } = this.state;
    if (!selectedNodeId) {
      return null;
    }
    const selectedNode = this.getManager().getEntity(this.context.store.getState(), selectedNodeId);
    if (!selectedNode) {
      return null;
    }
    return (
      <div className="basic-toolbar" style={{ borderLeft: '1px solid #ddd' }}>
        <Basic.Alert
          title={ this.i18n('label.selected') }
          level="info"
          style={{ margin: 0, maxWidth: 450 }}>
          <div style={{ display: 'flex'}}>
            <div style={{ flex: 1}}>
              <Basic.ShortText text={ this.getManager().getNiceLabel(selectedNode) } maxLength={ 40 }/>
            </div>
            <Basic.Button
              type="button"
              level="primary"
              className="btn-xs"
              icon="fa:search"
              onClick={ this.showDetail.bind(this, selectedNode) }>
              { this.i18n('component.advanced.EntityInfo.link.detail.label') }
            </Basic.Button>
          </div>
        </Basic.Alert>
      </div>
    );
  }

  render() {
    const { treePaginationRootSize, treePaginationNodeSize, showRoleCatalogueCode } = this.props;
    const { filterOpened } = this.state;
    const showTree = SecurityManager.hasAuthority('ROLECATALOGUE_AUTOCOMPLETE');
    //
    return (
      <Basic.Row className={ showTree ? 'tree-select-wrapper' : '' }>
        <Basic.Col
          lg={ 3 }
          rendered={ showTree === true }
          className="tree-select-tree-container">
          <Advanced.Tree
            ref="roleCatalogueTree"
            uiKey="role-catalogue-tree"
            manager={ this.getManager() }
            onChange={ this._useFilterByTree.bind(this) }
            header={ this.i18n('header') }
            rendered={ showTree }
            paginationRootSize={ treePaginationRootSize }
            paginationNodeSize={ treePaginationNodeSize }
            nodeNiceLabel={ (node) => this.getManager().getNiceLabel(node, showRoleCatalogueCode) }/>
        </Basic.Col>

        <Basic.Col
          lg={ showTree ? 9 : 12 }
          className="tree-select-table-container">
          <Basic.Confirm ref="confirm-delete" level="danger"/>

          { this._renderSelectedNode() }

          <Advanced.Table
            ref="table"
            uiKey={ this.getUiKey() }
            manager={ this.getManager() }
            className={ showTree ? 'show-tree' : '' }
            rowClass={ ({rowIndex, data}) => { return Utils.Ui.getRowClass(data[rowIndex]); } }
            filterOpened={ filterOpened }
            showRowSelection
            filter={
              <Advanced.Filter onSubmit={ this.useFilter.bind(this) }>
                <Basic.AbstractForm ref="filterForm">
                  <Basic.Row className="last">
                    <Basic.Col lg={ 4 }>
                      <Advanced.Filter.TextField
                        ref="text"
                        placeholder={this.i18n('filter.text')}/>
                    </Basic.Col>
                    <Basic.Col lg={ 4 }>
                      <Advanced.Filter.RoleCatalogueSelect
                        ref="parent"
                        label={ null }
                        placeholder={ this.i18n('filter.parentPlaceHolder') }
                        manager={ this.getManager() }/>
                    </Basic.Col>
                    <Basic.Col lg={ 4 } className="text-right">
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
                  rendered={ SecurityManager.hasAuthority('ROLECATALOGUE_CREATE') }
                  icon="fa:plus">
                  { this.i18n('button.add') }
                </Basic.Button>
              ]
            }
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
              sort={false}/>
            <Advanced.ColumnLink
              to="/role-catalogue/:id/detail"
              header={ this.i18n('entity.RoleCatalogue.code.name') }
              property="code"
              width="15%"
              sort
              face="text"/>
            <Advanced.Column property="name" header={ this.i18n('entity.RoleCatalogue.name.name') } sort face="text"/>
            <Advanced.Column
              property="parent.name"
              cell={
                ({rowIndex, data}) => {
                  // get parent name from _embedded
                  const parentName = (data[rowIndex]._embedded && data[rowIndex]._embedded.parent) ? data[rowIndex]._embedded.parent.name : null;
                  return parentName;
                }
              }/>
            <Advanced.Column
              header={ this.i18n('entity.RoleCatalogue.url') }
              cell={
                ({ rowIndex, data }) => {
                  return (<Basic.Link href={ data[rowIndex].url } text={ data[rowIndex].urlTitle } />);
                }
              }/>
            <Advanced.Column property="description" sort face="text"/>
          </Advanced.Table>
        </Basic.Col>
      </Basic.Row>
    );
  }
}

RoleCatalogueTable.propTypes = {
  uiKey: PropTypes.string.isRequired,
  roleCatalogueManager: PropTypes.object.isRequired,
  filterOpened: PropTypes.bool
};

RoleCatalogueTable.defaultProps = {
  filterOpened: true,
};

function select(state, component) {
  return {
    _searchParameters: Utils.Ui.getSearchParameters(state, component.uiKey),
    treePaginationRootSize: ConfigurationManager.getValue(state, 'idm.pub.app.show.roleCatalogue.tree.pagination.root.size'),
    treePaginationNodeSize: ConfigurationManager.getValue(state, 'idm.pub.app.show.roleCatalogue.tree.pagination.node.size'),
    showRoleCatalogueCode: ConfigurationManager.getPublicValueAsBoolean(state, 'idm.pub.app.show.roleCatalogue.tree.code', false)
  };
}

export default connect(select)(RoleCatalogueTable);
