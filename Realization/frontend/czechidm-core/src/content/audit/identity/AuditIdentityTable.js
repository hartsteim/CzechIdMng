import PropTypes from 'prop-types';
import React from 'react';
import { connect } from 'react-redux';
import _ from 'lodash';
//
import * as Utils from '../../../utils';
import * as Basic from '../../../components/basic';
import * as Advanced from '../../../components/advanced';
import { AuditManager } from '../../../redux';
import AuditModificationEnum from '../../../enums/AuditModificationEnum';
import SearchParameters from '../../../domain/SearchParameters';

const auditManager = new AuditManager();

/**
* Table of Audit for identities
*
* @author Ondřej Kopr
*/
export class AuditIdentityTable extends Advanced.AbstractTableContent {

  getContentKey() {
    return 'content.audit';
  }

  useFilter(event) {
    if (event) {
      event.preventDefault();
    }
    this.refs.table.useFilterForm(this.refs.filterForm);
  }

  componentDidMount() {
    super.componentDidMount();
    //
    this.context.store.dispatch(auditManager.fetchAuditedEntitiesNames());
  }

  cancelFilter(event) {
    if (event) {
      event.preventDefault();
    }
    if (this.refs.table !== undefined) {
      this.refs.table.cancelFilter(this.refs.filterForm);
    }
  }

  /**
  * Method get last string of split string by dot.
  * Used for get niceLabel for type entity.
  */
  _getType(name) {
    if (name) {
      const type = name.split('.');
      return type[type.length - 1];
    }
    return null;
  }

  _getAdvancedFilter(auditedEntities) {
    const { singleUserMod } = this.props;
    return (
      <Advanced.Filter onSubmit={this.useFilter.bind(this)}>
        <Basic.AbstractForm ref="filterForm">
          <Basic.Row>
            <Basic.Col lg={ 8 }>
              <Advanced.Filter.FilterDate ref="fromTill"/>
            </Basic.Col>
            <div className="col-lg-4 text-right">
              <Advanced.Filter.FilterButtons cancelFilter={this.cancelFilter.bind(this)}/>
            </div>
          </Basic.Row>
          <Basic.Row rendered={!singleUserMod}>
            <div className="col-lg-4">
              <Advanced.Filter.TextField
                className="pull-right"
                ref="ownerCode"
                placeholder={this.i18n('content.audit.identities.username')}/>
            </div>
            <div className="col-lg-4">
              <Advanced.Filter.TextField
                ref="entityId"
                placeholder={this.i18n('content.audit.identities.identityId')}/>
            </div>
          </Basic.Row>
          <Basic.Row>
            <div className="col-lg-4">
              <Advanced.Filter.EnumSelectBox
                ref="type"
                searchable
                placeholder={this.i18n('entity.Audit.type')}
                options={ auditedEntities }/>
            </div>
            <div className="col-lg-4">
              <Advanced.Filter.TextField
                className="pull-right"
                ref="modifier"
                placeholder={this.i18n('content.audit.identities.modifier')}/>
            </div>
          </Basic.Row>
          <Basic.Row className="last">
            <Basic.Col lg={ 12 } >
              <Advanced.Filter.CreatableSelectBox
                ref="changedAttributesList"
                placeholder={this.i18n('entity.Audit.changedAttributes.placeholder')}
                tooltip={this.i18n('entity.Audit.changedAttributes.tooltip')}/>
            </Basic.Col>
          </Basic.Row>
        </Basic.AbstractForm>
      </Advanced.Filter>
    );
  }

  /**
  * Method for show detail of revision, redirect to detail
  *
  * @param entityId id of revision
  */
  showDetail(entityId) {
    this.context.history.push(`/audit/entities/${entityId}/diff/`);
  }

  _getForceSearchParameters() {
    const { username, id } = this.props;
    let forceSearchParameters = new SearchParameters('entity')
      .setFilter('withVersion', true)
      .setFilter('ownerType', 'eu.bcvsolutions.idm.core.model.entity.IdmIdentity'); // TODO: this isn't best way, hard writen class
    if (username) {
      forceSearchParameters = forceSearchParameters.setFilter('ownerCode', username);
    }
    if (id) {
      forceSearchParameters = forceSearchParameters.setFilter('ownerId', id);
    }
    return forceSearchParameters;
  }

  _getNiceLabelForOwner(ownerType, ownerCode) {
    if (ownerCode && ownerCode !== null && ownerCode !== 'null') {
      return ownerCode;
    }
    return '';
  }

  render() {
    const { uiKey, singleUserMod, auditedEntities } = this.props;
    //
    return (
      <div>
        <Advanced.Table
          ref="table"
          filterOpened
          uiKey={ uiKey }
          manager={auditManager}
          forceSearchParameters={this._getForceSearchParameters()}
          rowClass={({rowIndex, data}) => { return Utils.Ui.getRowClass(data[rowIndex]); }}
          showId
          filter={ this._getAdvancedFilter(auditedEntities) }
          _searchParameters={ this.getSearchParameters() }>
          <Advanced.Column
            header=""
            className="detail-button"
            cell={
              ({ rowIndex, data }) => {
                return (
                  <Advanced.DetailButton
                    title={this.i18n('button.detail')}
                    onClick={this.showDetail.bind(this, data[rowIndex].id)}/>
                );
              }
            }
            sort={false}/>
          <Advanced.Column
            property="type"
            width={ 200 }
            cell={
              ({ rowIndex, data, property }) => {
                return (
                  <span title={data[rowIndex][property]}>
                    { this._getType(data[rowIndex][property]) }
                  </span>
                );
              }
            }/>
          <Advanced.Column
            property="entityId"
            header={ this.i18n('entity.Audit.entity') }
            cell={
              /* eslint-disable react/no-multi-comp */
              ({ rowIndex, data, property }) => {
                const value = data[rowIndex][property];
                //
                if (data[rowIndex]._embedded && data[rowIndex]._embedded[property]) {
                  return (
                    <Advanced.EntityInfo
                      entityType={ this._getType(data[rowIndex].type) }
                      entityIdentifier={ value }
                      face="popover"
                      entity={ data[rowIndex]._embedded[property] }
                      showEntityType={ false }
                      showIcon/>
                  );
                }
                if (data[rowIndex].revisionValues) {
                  return (
                    <Advanced.EntityInfo
                      entityType={ this._getType(data[rowIndex].type) }
                      entityIdentifier={ value }
                      entity={ data[rowIndex].revisionValues }
                      face="popover"
                      showLink={ false }
                      showEntityType={ false }
                      showIcon
                      deleted/>
                  );
                }
                //
                return (
                  <Advanced.UuidInfo value={ value } />
                );
              }
            }/>
          <Advanced.Column
            property="ownerCode"
            face="text"
            rendered={!singleUserMod}
            cell={
              ({ rowIndex, data }) => {
                return this._getNiceLabelForOwner(data[rowIndex].ownerType, data[rowIndex].ownerCode);
              }
            }
          />
          <Advanced.Column
            property="subOwnerCode"
            face="text"
            cell={
              ({ rowIndex, data }) => {
                return this._getNiceLabelForOwner(data[rowIndex].subOwnerType, data[rowIndex].subOwnerCode);
              }
            }
          />
          <Advanced.Column
            property="modification"
            width={ 100 }
            sort
            cell={
              ({ rowIndex, data, property }) => {
                return (
                  <Basic.Label
                    level={AuditModificationEnum.getLevel(data[rowIndex][property])}
                    text={AuditModificationEnum.getNiceLabel(data[rowIndex][property])}/>
                );
              }
            }/>
          <Advanced.Column property="modifier" sort face="text"/>
          <Advanced.Column property="timestamp" header={this.i18n('entity.Audit.revisionDate')} sort face="datetime"/>
          <Advanced.Column
            hidden
            property="changedAttributes"
            cell={
              ({ rowIndex, data, property }) => {
                return _.replace(data[rowIndex][property], ',', ', ');
              }
            }
          />
        </Advanced.Table>
      </div>
    );
  }
}

AuditIdentityTable.propTypes = {
  uiKey: PropTypes.string.isRequired,
  username: PropTypes.string,
  singleUserMod: PropTypes.boolean,
  id: PropTypes.string
};

AuditIdentityTable.defaultProps = {
  singleUserMod: false
};

function select(state, component) {
  return {
    _searchParameters: Utils.Ui.getSearchParameters(state, component.uiKey),
    auditedEntities: auditManager.prepareOptionsFromAuditedEntitiesNames(auditManager.getAuditedEntitiesNames(state))
  };
}

export default connect(select)(AuditIdentityTable);
