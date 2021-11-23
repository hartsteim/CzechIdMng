import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import _ from 'lodash';
import * as Basic from '../../components/basic';
import * as Advanced from '../../components/advanced';
import * as Utils from '../../utils';
import * as Domain from '../../domain';
import IdentitiesInfo from '../identity/IdentitiesInfo';
/**
* Table of tasks
* Without given props search parameter show this table only tasks for logged identity.
*
* @author Vít Švanda
*/
export class TaskInstanceTable extends Advanced.AbstractTableContent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      filterOpened: this.props.filterOpened,
      detail: {
        show: false,
        entity: {}
      }
    };
  }

  getContentKey() {
    return 'content.task.instances';
  }

  getManager() {
    return this.props.taskInstanceManager;
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
    this.context.history.push(`/task/${entity.id}`);
  }

  closeDetail() {
    this.setState({
      detail: {
        show: false,
        entity: {}
      }
    });
  }

  _getWfTaskCell({ rowIndex, data}) {
    const entity = data[rowIndex];
    if (!entity || !entity.id) {
      return '';
    }
    return (
      <Advanced.WorkflowTaskInfo entity={entity}/>
    );
  }

  _renderDefinitionColumn({rowIndex, data}) {
    const value = data[rowIndex];
    if (!value) {
      return null;
    }
    const delegation = value.delegationDefinition;
    if (!delegation) {
      return null;
    }
    return (
      <Advanced.EntityInfo
        entityType="DelegationDefinitionDto"
        entityIdentifier={ delegation.id}
        face="popover"
        entity={delegation}
        showEntityType
        showLink
        showIcon/>
    );
  }

  render() {
    const { uiKey, taskInstanceManager, columns, searchParameters, showFilter, showToolbar, username, userContext, showRowSelection } = this.props;
    const { filterOpened} = this.state;
    let _searchParameters = null;
    if (searchParameters == null) {
      _searchParameters = new Domain.SearchParameters().setFilter('candidateOrAssigned', username || userContext.username);
    } else {
      _searchParameters = searchParameters;
    }
    return (
      <Advanced.Table
        ref="table"
        uiKey={uiKey}
        manager={taskInstanceManager}
        showRowSelection={showRowSelection}
        forceSearchParameters={_searchParameters}
        filterOpened={filterOpened}
        showFilter={showFilter}
        showToolbar={showToolbar || showRowSelection}
        filter={
          <Advanced.Filter onSubmit={this.useFilter.bind(this)}>
            <Basic.AbstractForm ref="filterForm">
              <Basic.Row>
                <Basic.Col lg={ 6 }>
                  <Advanced.Filter.IdentitySelect
                    ref="candidateOrAssigned"
                    rendered={ _.includes(columns, 'taskAssignee') }
                    placeholder={ this.i18n('entity.WorkflowTaskInstance.taskAssignee') }/>
                </Basic.Col>
                <Basic.Col lg={ 6 } className="text-right">
                  <Advanced.Filter.FilterButtons cancelFilter={this.cancelFilter.bind(this)}/>
                </Basic.Col>
              </Basic.Row>
              <Basic.Row className="last">
                <Basic.Col lg={ 8 }>
                  <Advanced.Filter.FilterDate
                    ref="fromTill"
                    facePlaceholder={ this.i18n('entity.WorkflowTaskInstance.filter.created') }
                    fromProperty="createdAfter"
                    fromPlaceholder={ this.i18n('entity.WorkflowTaskInstance.filter.createdAfter') }
                    tillProperty="createdBefore"
                    tillPlaceholder={ this.i18n('entity.WorkflowTaskInstance.filter.createdBefore') }/>
                </Basic.Col>
                <Basic.Col lg={ 4 } />
              </Basic.Row>
            </Basic.AbstractForm>
          </Advanced.Filter>
        }>
        <Advanced.Column
          header=""
          className="detail-button"
          cell={
            ({ rowIndex, data }) => (
              <Advanced.DetailButton
                title={this.i18n('button.detail')}
                onClick={this.showDetail.bind(this, data[rowIndex])}/>
            )
          }
          sort={false}/>
        <Advanced.Column
          header=""
          property="taskDescription"
          cell={this._getWfTaskCell}
          sort={false}
          rendered={_.includes(columns, 'description')}/>
        <Advanced.Column
          property="taskCreated"
          sort
          face="datetime"
          rendered={ _.includes(columns, 'created') }
          width={ 150 }/>
        <Advanced.Column
          property="taskAssignee"
          sort={ false }
          face="text"
          rendered={ _.includes(columns, 'taskAssignee') }
          width={ 175 }
          cell={ ({rowIndex, data}) => {
            const identityIds = [];
            const identityLinks = data[rowIndex].identityLinks;
            if (identityLinks) {
              identityLinks.forEach((identityLink) => {
                if (identityLink.type === 'candidate' || identityLink.type === 'assignee') {
                  identityIds.push(identityLink.userId);
                }
              });
            }
            return (
              <IdentitiesInfo
                identities={ identityIds }
                maxEntry={ 5 }
                header={ this.i18n('entity.WorkflowHistoricTaskInstance.candicateUsers') }/>
            );
          }}/>
        <Advanced.Column
          property="delegationDefinition"
          header={ this.i18n('content.task.instance.delegation.header') }
          width={ 200 }
          rendered={ _.includes(columns, 'delegation') }
          cell={this._renderDefinitionColumn.bind(this)}/>
      </Advanced.Table>
    );
  }
}

TaskInstanceTable.propTypes = {
  uiKey: PropTypes.string.isRequired,
  taskInstanceManager: PropTypes.object.isRequired,
  columns: PropTypes.arrayOf(PropTypes.string),
  filterOpened: PropTypes.bool,
  searchParameters: PropTypes.object,
  showFilter: PropTypes.bool,
  showToolbar: PropTypes.bool,
  showRowSelection: PropTypes.bool
};

TaskInstanceTable.defaultProps = {
  columns: ['created', 'description', 'id', 'delegation'],
  filterOpened: false,
  _showLoading: false,
  searchParameters: null,
  showFilter: false,
  showToolbar: false,
  showRowSelection: true
};

function select(state, component) {
  return {
    _searchParameters: Utils.Ui.getSearchParameters(state, component.uiKey),
    _showLoading: component.taskInstanceManager.isShowLoading(state, `${component.uiKey}-detail`),
    userContext: state.security.userContext
  };
}

export default connect(select, null, null, { forwardRef: true })(TaskInstanceTable);
