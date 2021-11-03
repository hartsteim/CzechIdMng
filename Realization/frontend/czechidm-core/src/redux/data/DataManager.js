import Immutable from 'immutable';
//
import FlashMessagesManager from '../flash/FlashMessagesManager';
import * as Utils from '../../utils';
/**
 * action types
 */
export const REQUEST_DATA = 'REQUEST_DATA';
export const STOP_REQUEST = 'STOP_REQUEST';
export const RECEIVE_DATA = 'RECEIVE_DATA';
export const CLEAR_DATA = 'CLEAR_DATA';
export const RECEIVE_ERROR = 'RECEIVE_ERROR';
export const EXPAND_FILTER = 'EXPAND_FILTER';
export const COLLAPSE_FILTER = 'COLLAPSE_FILTER';
//
const UIKEY_MODALS = 'modal-content-state';

/**
 * Encapsulate redux action for form data (create, edit) etc.
 *
 * @author Radek Tomiška
 */
export default class DataManager {

  constructor() {
    this.flashMessagesManager = new FlashMessagesManager();
  }

  /**
   * Request data from store - simply sets loading flag fot given uiKey
   *
   * @param  {string} uiKey - access ui key
   * @return {action} - action
   */
  requestData(uiKey) {
    return this.startRequest(uiKey);
  }

  /**
   * Request data from store - simply sets loading flag fot given uiKey.
   *
   * @param  {string} uiKey - access ui key
   * @return {action} - action
   * @since 10.7.0
   */
  startRequest(uiKey) {
    return {
      type: REQUEST_DATA,
      uiKey
    };
  }

  /**
   * Stops request
   *
   * @param  {string} uiKey - ui key for loading indicator etc
   * @param  {object} error - received error
   * @return {action}
   */
  stopRequest(uiKey, error = null, cb = null) {
    if (cb) {
      cb(null, error);
    }
    return {
      type: STOP_REQUEST,
      uiKey,
      error
    };
  }

  /**
   * Add data to store - data can be read by other components (receiveData alias)
   *
   * @param  {string} uiKey - access ui key
   * @param  {any} data - stored data
   * @return {action} - action
   */
  storeData(uiKey, data) {
    return this.receiveData(uiKey, data);
  }

  /**
   * Set madals state.
   *
   * @param {Immutable.Map} modals
   * @since 12.0.0
   */
  setModals(modals) {
    return this.receiveData(UIKEY_MODALS, modals);
  }

  /**
   * Add data to store - data can be read by other components
   *
   * @param  {string} uiKey - access ui key
   * @param  {any} data - stored data
   * @return {action} - action
   */
  receiveData(uiKey, data, cb = null) {
    if (cb) {
      cb(data, null);
    }
    return {
      type: RECEIVE_DATA,
      uiKey,
      data
    };
  }

  /**
   * Clear (remove) data from store
   *
   * @param  {string} uiKey - access ui key
   * @return {action} - action
   */
  clearData(uiKey) {
    return {
      type: CLEAR_DATA,
      uiKey
    };
  }

  /**
   * Receive error from server call
   *
   * @param  {string|number} id - entity identifier (could be null)
   * @param  {object} entity - received entity
   * @param  {string} uiKey - ui key for loading indicator etc
   * @param  {object} error - received error
   * @return {object} - action
   */
  receiveError(data, uiKey, error = null, cb = null) {
    return (dispatch) => {
      if (cb) {
        cb(null, error);
      } else {
        dispatch(this.flashMessagesManager.addErrorMessage({
          key: `error-${ uiKey }`
        }, error));
      }
      dispatch({
        type: RECEIVE_ERROR,
        uiKey,
        error
      });
    };
  }

  /**
   * Expand filter with given ui key.
   *
   * @param uiKey {string}
   * @return {action}
   * @since 10.7.0
   */
  expandFilter(uiKey) {
    return {
      type: EXPAND_FILTER,
      uiKey
    };
  }

  /**
   * Collapse filter with given ui key.
   *
   * @param uiKey {string}
   * @return {action}
   * @since 10.7.0
   */
  collapseFilter(uiKey) {
    return {
      type: COLLAPSE_FILTER,
      uiKey
    };
  }

  /**
   * Returns data associated with the given key
   *
   * @param  {state} state - application state
   * @param  {string} uiKey - access ui key
   * @return {any} - stored data
   */
  static getData(state, uiKey) {
    if (!state || !uiKey) {
      return null;
    }
    if (!state.data.data.has(uiKey)) {
      return null;
    }
    return state.data.data.get(uiKey);
  }

  /**
   * Returns modals state.
   *
   * @param  {state} state - application state
   * @return {Immutable.Map} - modals state
   * @since 12.0.0
   */
  static getModals(state) {
    return DataManager.getData(state, UIKEY_MODALS) || new Immutable.Map({});
  }

  /**
   * Returns true, when loading for given uiKey proceed
   *
   * @param  {state} state - application state
   * @param  {string} uiKey - access ui key
   * @return {any} - stored data
   * @since 9.0.0
   */
  static isShowLoading(state, uiKey) {
    return Utils.Ui.isShowLoading(state, uiKey);
  }

  /**
   * Returns true, when filter is shown (~opened).
   *
   * @param  {state} state - application state
   * @param  {string} uiKey - ui key for loading indicator etc.
   * @return {boolean} - true, when filter is expanded, null - default, filter is inited  by underlying table
   * @since 10.7.0
   */
  static isFilterOpened(state, uiKey) {
    return Utils.Ui.isFilterOpened(state, uiKey);
  }
}
