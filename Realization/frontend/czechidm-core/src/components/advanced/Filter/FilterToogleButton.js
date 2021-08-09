import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
//
import AbstractContextComponent from '../../basic/AbstractContextComponent/AbstractContextComponent';
import * as Basic from '../../basic';
import * as Domain from '../../../domain';
import { DataManager, IdentityManager } from '../../../redux';
//
const dataManager = new DataManager();
const identityManager = new IdentityManager();

/**
 * Button for closable filter mainly for advanced table.
 *
 * @author Radek Tomiška
 */
class FilterToogleButton extends AbstractContextComponent {

  constructor(props, context) {
    super(props, context);
    //
    this.state = {
      filterOpened: this.props.filterOpened
    };
  }

  _filterOpen(opened) {
    const { filterOpen, uiKey, userContext } = this.props;
    //
    this.setState({
      filterOpened: opened
    }, () => {
      if (uiKey) {
        if (opened) {
          this.context.store.dispatch(dataManager.expandFilter(uiKey));
          if (userContext && userContext.username) {
            this.context.store.dispatch(identityManager.expandPanel(userContext.username, uiKey));
          }
        } else {
          this.context.store.dispatch(dataManager.collapseFilter(uiKey));
          if (userContext && userContext.username) {
            this.context.store.dispatch(identityManager.collapsePanel(userContext.username, uiKey));
          }
        }
      }
      if (filterOpen) {
        filterOpen(opened);
      }
    });
  }

  render() {
    const {
      rendered,
      showLoading,
      searchParameters,
      forceSearchParameters,
      ...others
    } = this.props;
    const { filterOpened } = this.state;
    if (!rendered) {
      return null;
    }
    let level = 'default';
    let tooltip = this.i18n('component.advanced.Table.filter.empty');
    if (!Domain.SearchParameters.isEmptyFilter(searchParameters, forceSearchParameters)) {
      level = 'info';
      tooltip = this.i18n('component.advanced.Table.filter.notEmpty');
    }
    //
    return (
      <Basic.Tooltip value={tooltip}>
        <span>
          <Basic.Button
            level={ level }
            buttonSize="xs"
            onClick={ this._filterOpen.bind(this, !filterOpened) }
            showLoading={ showLoading }
            icon="filter"
            { ...others }>
            { this.i18n('button.filter.toogle') }
            {' '}
            <Basic.Icon icon={ !filterOpened ? 'triangle-bottom' : 'triangle-top' } style={{ fontSize: '0.85em'}}/>
          </Basic.Button>
        </span>
      </Basic.Tooltip>
    );
  }
}

FilterToogleButton.propTypes = {
  ...Basic.AbstractContextComponent.propTypes,
  /**
   * Callback, when filter is opened
   * @type {function} function(bool)
   */
  filterOpen: PropTypes.func,
  /**
   * Filter is opened
   * @type {bool}
   */
  filterOpened: PropTypes.bool,
  /**
   * Used search parameters in redux
   *
   * @type {SearchParameters}
   */
  searchParameters: PropTypes.object,
  /**
   * "Hard filters"
   *
   * @type {SearchParameters}
   */
  forceSearchParameters: PropTypes.object,
};
FilterToogleButton.defaultProps = {
  ...Basic.AbstractContextComponent.defaultProps
};
function select(state) {
  return {
    userContext: state.security.userContext
  };
}

export default connect(select)(FilterToogleButton);
