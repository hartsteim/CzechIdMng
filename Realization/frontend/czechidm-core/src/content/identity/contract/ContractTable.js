import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import uuid from 'uuid';
import _ from 'lodash';
//
import * as Basic from '../../../components/basic';
import * as Advanced from '../../../components/advanced';
import * as Utils from '../../../utils';
import { IdentityContractManager, IdentityManager, TreeTypeManager, TreeNodeManager, SecurityManager } from '../../../redux';
import ManagersInfo from '../ManagersInfo';
import ContractStateEnum from '../../../enums/ContractStateEnum';

const manager = new IdentityContractManager(); // default manager
const identityManager = new IdentityManager();

/**
 * Identity contracts
 *
 * @author Radek Tomiška
 * @since 9.4.0
 */
export class ContractTable extends Advanced.AbstractTableContent {

  constructor(props, context) {
    super(props, context);
    this.treeTypeManager = new TreeTypeManager();
    this.treeNodeManager = new TreeNodeManager();
  }

  getManager() {
    return this.props.manager;
  }

  getContentKey() {
    return 'content.identity.identityContracts';
  }

  getNavigationKey() {
    return 'profile-contracts';
  }

  showDetail(entity, event) {
    if (event) {
      event.preventDefault();
    }
    //
    if (Utils.Entity.isNew(entity)) {
      const uuidId = uuid.v1();
      const { identity } = this.props;
      this.context.history.push(`/identity/${ encodeURIComponent(identity.id) }/identity-contract/${ uuidId }/new?new=1`);
    } else {
      this.context.history.push(`/identity/${encodeURIComponent(this._getIdentityIdentifier()) }/identity-contract/${ entity.id }/detail`);
    }
  }

  showGuarantees(entity, event) {
    if (event) {
      event.preventDefault();
    }
    this.context.history.push(`/identity/${encodeURIComponent(entity.identity)}/identity-contract/${entity.id}/guarantees`);
  }

  reload() {
    this.refs.table.reload();
  }

  _getIdentityIdentifier() {
    const { forceSearchParameters } = this.props;
    if (!forceSearchParameters || !forceSearchParameters.getFilters().has('identity')) {
      return null;
    }
    return forceSearchParameters.getFilters().get('identity');
  }

  render() {
    const {
      columns,
      forceSearchParameters,
      className,
      showAddButton,
      showDetailButton,
      identity
    } = this.props;
    //
    return (
      <Basic.Div>
        <Basic.Confirm ref="confirm-delete" level="danger"/>
        <Advanced.Table
          ref="table"
          uiKey={ this.getUiKey() }
          manager={ this.getManager() }
          forceSearchParameters={ forceSearchParameters }
          rowClass={({rowIndex, data}) => (data[rowIndex].state ? 'disabled' : Utils.Ui.getRowClass(data[rowIndex])) }
          showRowSelection
          actions={
            [
              { value: 'delete', niceLabel: this.i18n('action.delete.action'), action: this.onDelete.bind(this) },
            ]
          }
          buttons={
            [
              <Basic.Button
                level="success"
                className="btn-xs"
                onClick={ this.showDetail.bind(this, {}) }
                icon="fa:plus"
                text={ this.i18n('button.add') }
                rendered={
                  showAddButton
                  &&
                  this._getIdentityIdentifier()
                  &&
                  SecurityManager.hasAuthority('IDENTITYCONTRACT_CREATE')
                  &&
                  identity
                } />
            ]
          }
          _searchParameters={ this.getSearchParameters() }
          className={ className }>
          <Basic.Column
            className="detail-button"
            rendered={ showDetailButton }
            cell={
              ({rowIndex, data}) => (
                <Advanced.DetailButton onClick={ this.showDetail.bind(this, data[rowIndex]) }/>
              )
            }/>
          <Advanced.Column
            property="main"
            header={ <Basic.Icon value="component:main-contract"/> }
            title={ this.i18n('entity.IdentityContract.main.help') }
            face="bool"
            width={ 15 }
            rendered={ _.includes(columns, 'main') }/>
          <Advanced.Column
            property="position"
            header={this.i18n('entity.IdentityContract.position')}
            width={ 200 }
            sort
            rendered={ _.includes(columns, 'position') }/>
          <Basic.Column
            property="workPosition"
            header={this.i18n('entity.IdentityContract.workPosition')}
            width={ 350 }
            rendered={ _.includes(columns, 'workPosition') }
            cell={
              ({ rowIndex, data }) => (
                <span>
                  {
                    data[rowIndex]._embedded && data[rowIndex]._embedded.workPosition
                    ?
                    <Advanced.EntityInfo
                      entity={ data[rowIndex]._embedded.workPosition }
                      entityType="treeNode"
                      entityIdentifier={ data[rowIndex].workPosition }
                      face="popover" />
                    :
                    null
                  }
                </span>
              )
            }
          />
          <Basic.Column
            property="guarantee"
            header={ <span title={this.i18n('entity.IdentityContract.managers.title')}>{this.i18n('entity.IdentityContract.managers.label')}</span> }
            rendered={ _.includes(columns, 'guarantee') }
            cell={
              ({ rowIndex, data }) => (
                <ManagersInfo
                  managersFor={ data[rowIndex].identity }
                  identityContractId={ data[rowIndex].id }
                  detailLink={ this.showGuarantees.bind(this, data[rowIndex]) }/>
              )
            }
          />
          <Advanced.Column
            property="validFrom"
            header={ this.i18n('entity.IdentityContract.validFrom') }
            face="date"
            sort
            rendered={ _.includes(columns, 'validFrom') }
          />
          <Advanced.Column
            property="validTill"
            header={ this.i18n('entity.IdentityContract.validTill') }
            face="date"
            sort
            rendered={ _.includes(columns, 'validTill') }/>
          <Advanced.Column
            property="state"
            header={ this.i18n('entity.IdentityContract.state.label') }
            face="enum"
            enumClass={ ContractStateEnum }
            width={ 100 }
            sort
            rendered={ _.includes(columns, 'state') }/>
          <Advanced.Column
            property="externe"
            header={ this.i18n('entity.IdentityContract.externe') }
            face="bool"
            width={ 100 }
            sort
            rendered={ _.includes(columns, 'externe') }/>
        </Advanced.Table>
      </Basic.Div>
    );
  }
}

ContractTable.propTypes = {
  uiKey: PropTypes.string.isRequired,
  /**
   * Identity contract manager
   */
  manager: PropTypes.object,
  /**
   * Rendered columns - see table columns above
   *
   * TODO: move to advanced table and add column sorting
   */
  columns: PropTypes.arrayOf(PropTypes.string),
  /**
   * "Hard filters"
   */
  forceSearchParameters: PropTypes.object,
  /**
   * Button for create user will be shown
   */
  showAddButton: PropTypes.bool,
  /**
   * Button for show entity detail
   */
  showDetailButton: PropTypes.bool
};

ContractTable.defaultProps = {
  manager,
  columns: ['main', 'position', 'workPosition', 'guarantee', 'validFrom', 'validTill', 'state', 'externe'],
  showAddButton: true,
  showDetailButton: true
};

function select(state, component) {
  const { entityId } = component.match ? component.match.params : {}; // ~username
  //
  return {
    _searchParameters: Utils.Ui.getSearchParameters(state, component.uiKey),
    i18nReady: state.config.get('i18nReady'),
    identity: entityId ? identityManager.getEntity(state, entityId) : null
  };
}

export default connect(select, null, null, { forwardRef: true })(ContractTable);
