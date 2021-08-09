import React from 'react';
import uuid from 'uuid';
//
import * as Basic from '../../components/basic';
import * as Advanced from '../../components/advanced';
import * as Utils from '../../utils';
import { ContractSliceManager, TreeTypeManager, TreeNodeManager, SecurityManager } from '../../redux';
import SearchParameters from '../../domain/SearchParameters';
import ContractStateEnum from '../../enums/ContractStateEnum';

const uiKey = 'contract-slices';

/**
 * Contract slices table.
 *
 * @author Vít Švanda
 */
export default class ContractSlices extends Advanced.AbstractTableContent {

  constructor(props, context) {
    super(props, context);
    this.contractSliceManager = new ContractSliceManager();
    this.treeTypeManager = new TreeTypeManager();
    this.treeNodeManager = new TreeNodeManager();
  }

  getUiKey() {
    const { entityId } = this.props.match.params;
    //
    return `${uiKey}-${entityId}`;
  }

  getManager() {
    return this.contractSliceManager;
  }

  getContentKey() {
    return 'content.identity.contractSlices';
  }

  showDetail(entity, event) {
    if (event) {
      event.preventDefault();
    }
    //
    const { entityId, identityId } = this.props.match.params;
    let identityLocalId = identityId;
    let contractLocalId = null;
    let isOnContractDetail = false;

    if (identityLocalId) {
      contractLocalId = entityId;
      isOnContractDetail = true;
    } else {
      identityLocalId = entityId;
    }

    if (entity.id === undefined) {
      const uuidId = uuid.v1();
      if (isOnContractDetail) {
        this.context.history.push(
          `/identity/${encodeURIComponent(identityLocalId)}/contract-slice/${uuidId}/new?new=1&contractId=${contractLocalId}`
        );
      } else {
        this.context.history.push(`/identity/${encodeURIComponent(identityLocalId)}/contract-slice/${uuidId}/new?new=1`);
      }
    } else {
      this.context.history.push(`/identity/${encodeURIComponent(identityLocalId)}/contract-slice/${entity.id}/detail`);
    }
  }

  afterDelete() {
    this.refs.table.reload();
    const {reloadExternal} = this.props;
    if (reloadExternal) {
      reloadExternal();
    }
  }

  render() {
    const { entityId, identityId} = this.props.match.params;
    const { rendered } = this.props;

    let identityLocalId = identityId;
    let contractLocalId = null;
    let isOnContractDetail = false;

    if (identityLocalId) {
      contractLocalId = entityId;
      isOnContractDetail = true;
    } else {
      identityLocalId = entityId;
    }

    if (rendered === false) {
      return null;
    }

    //
    return (
      <Basic.Div>
        <Basic.Confirm ref="confirm-delete" level="danger"/>

        <Basic.Panel className="no-border">
          <Advanced.Panel.Header
            uiKey="contract-slices-table-panel"
            text={ this.i18n('header') }
            collapsible={ false }/>
          <Basic.PanelBody style={{ padding: 0 }}>
            <Advanced.Table
              ref="table"
              uiKey={ this.getUiKey() }
              manager={ this.contractSliceManager }
              forceSearchParameters={ new SearchParameters().setFilter('identity', identityLocalId).setFilter('parentContract', contractLocalId) }
              rowClass={ ({rowIndex, data}) => (data[rowIndex].state ? 'disabled' : Utils.Ui.getRowClass(data[rowIndex])) }
              className="no-margin"
              showRowSelection={ SecurityManager.hasAuthority('CONTRACTSLICE_DELETE') }
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
                    onClick={this.showDetail.bind(this, {})}
                    rendered={ SecurityManager.hasAuthority('CONTRACTSLICE_CREATE') }>
                    <Basic.Icon value="fa:plus"/>
                    {' '}
                    {this.i18n('button.add')}
                  </Basic.Button>
                ]
              }>
              <Basic.Column
                className="detail-button"
                cell={
                  ({rowIndex, data}) => (
                    <Advanced.DetailButton onClick={this.showDetail.bind(this, data[rowIndex])}/>
                  )
                }/>
              <Advanced.Column
                property="contractCode"
                rendered={!isOnContractDetail}
                header={this.i18n('entity.ContractSlice.contractCode')}
                sort/>
              <Advanced.Column
                property="parent"
                rendered={!isOnContractDetail}
                header={this.i18n('entity.ContractSlice.parentContract')}
                cell={
                  /* eslint-disable react/no-multi-comp */
                  ({ rowIndex, data }) => {
                    const parentContract = data[rowIndex]._embedded.parentContract;
                    if (!parentContract) {
                      return '';
                    }
                    return (
                      <Advanced.IdentityContractInfo
                        entityIdentifier={ parentContract.id }
                        entity={ parentContract }
                        showIdentity={ false }
                        face="popover" />
                    );
                  }
                }/>
              <Advanced.Column
                property="usingAsContract"
                header={this.i18n('entity.ContractSlice.usingAsContract.label')}
                face="bool"
                sort/>
              <Basic.Column
                property="workPosition"
                header={this.i18n('entity.ContractSlice.workPosition')}
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
                        data[rowIndex].position
                      }
                    </span>
                  )
                }
              />
              <Advanced.Column
                property="validFrom"
                header={this.i18n('entity.ContractSlice.validFrom')}
                face="date"
                sort
              />
              <Advanced.Column
                property="validTill"
                header={this.i18n('entity.ContractSlice.validTill')}
                face="date"
                sort/>
              <Advanced.Column
                property="state"
                header={this.i18n('entity.ContractSlice.state.label')}
                face="enum"
                enumClass={ ContractStateEnum }
                sort/>
            </Advanced.Table>
          </Basic.PanelBody>
        </Basic.Panel>
      </Basic.Div>
    );
  }
}
