import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import _ from 'lodash';
import uuid from 'uuid';
//
import { Basic, Advanced, Managers, Utils } from 'czechidm-core';

/**
 * Table of system groups.
 *
 * @author Vít Švanda
 * @since 11.2.0
 *
 */
class SystemGroupTable extends Advanced.AbstractTableContent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      filterOpened: this.props.filterOpened
    };
  }

  componentDidMount() {
    super.componentDidMount();
    //
    this.refs.text.focus();
  }

  getContentKey() {
    return 'acc:content.systemGroup';
  }

  getManager() {
    return this.props.manager;
  }

  getDefaultSearchParameters() {
    return this.getManager().getDefaultSearchParameters();
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
      this.context.history.push(`/system-groups/${uuidId}/new?new=1`);
    } else {
      this.context.history.push(`/system-groups/${ encodeURIComponent(entity.id) }/detail`);
    }
  }

  render() {
    const {
      uiKey,
      columns,
      forceSearchParameters,
      className,
    } = this.props;
    const { filterOpened, showLoading } = this.state;
    //
    return (
      <Basic.Row>
        <Basic.Col lg={ 12 } >
          <Basic.Confirm ref="confirm-delete" level="danger"/>
          <Advanced.Table
            ref="table"
            uiKey={ uiKey }
            manager={ this.getManager() }
            rowClass={ ({rowIndex, data}) => Utils.Ui.getRowClass(data[rowIndex]) }
            filterOpened={ filterOpened }
            forceSearchParameters={ forceSearchParameters }
            showRowSelection
            showLoading={ showLoading }
            filter={
              <Advanced.Filter onSubmit={this.useFilter.bind(this)}>
                <Basic.AbstractForm ref="filterForm">
                  <Basic.Row>
                    <Basic.Col lg={ 8 }>
                      <Advanced.Filter.TextField
                        ref="text"
                        placeholder={this.i18n('filter.text')}/>
                    </Basic.Col>
                    <Basic.Col lg={ 4 } className="text-right">
                      <Advanced.Filter.FilterButtons cancelFilter={ this.cancelFilter.bind(this) }/>
                    </Basic.Col>
                  </Basic.Row>
                </Basic.AbstractForm>
              </Advanced.Filter>
            }
            buttons={[
              <span>
                <Basic.Button
                  level="success"
                  key="add_button"
                  className="btn-xs"
                  onClick={ this.showDetail.bind(this, { }) }
                  rendered={ Managers.SecurityManager.hasAuthority('SYSTEM_GROUP_CREATE') }
                  icon="fa:plus">
                  { this.i18n('button.add') }
                </Basic.Button>
              </span>
            ]}
            _searchParameters={ this.getSearchParameters() }
            className={ className }>

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
              to="/system-groups/:id/detail"
              property="code"
              header={this.i18n('acc:entity.SystemGroup.code')}
              width="15%"
              sort
              face="text"
              rendered={_.includes(columns, 'code')}/>
            <Advanced.Column
              property="description"
              header={this.i18n('acc:entity.SystemGroup.description')}
              sort
              face="text"
              rendered={ _.includes(columns, 'description') }
              maxLength={ 100 }/>
            <Advanced.Column
              property="disabled"
              header={this.i18n('acc:entity.SystemGroup.disabled.label')}
              sort
              face="bool"
              width={ 75 }
              rendered={ _.includes(columns, 'disabled') }/>
          </Advanced.Table>
        </Basic.Col>
      </Basic.Row>
    );
  }
}

SystemGroupTable.propTypes = {
  uiKey: PropTypes.string.isRequired,
  roleManager: PropTypes.object.isRequired,
  columns: PropTypes.arrayOf(PropTypes.string),
  filterOpened: PropTypes.bool,
  /**
   * "Hard filters"
   */
  forceSearchParameters: PropTypes.object,
  /**
   * Css
   */
  className: PropTypes.string,
  /**
   * Button for create user will be shown
   */
  showAddButton: PropTypes.bool
};

SystemGroupTable.defaultProps = {
  columns: ['code', 'disabled', 'description'],
  filterOpened: true,
  forceSearchParameters: null,
  showAddButton: true
};

function select() {
  return {
  };
}

export default connect(select, null, null, { forwardRef: true })(SystemGroupTable);
