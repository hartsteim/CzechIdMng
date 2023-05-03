import React from 'react'
import PropTypes from 'prop-types'
import { connect } from 'react-redux'
import invariant from 'invariant'
import _ from 'lodash'
import Immutable from 'immutable'
import moment from 'moment'
import classnames from 'classnames'
//
import Menu from '@material-ui/core/Menu'
import MenuList from '@material-ui/core/MenuList'
import Divider from '@material-ui/core/Divider'
import MoreVertIcon from '@material-ui/icons/MoreVert'
import IconButton from '@material-ui/core/IconButton'
//
import * as Basic from '../../basic'
import * as Utils from '../../../utils'
import * as Domain from '../../../domain'
import UuidInfo from '../UuidInfo/UuidInfo'
import RefreshButton from './RefreshButton'
import Filter from '../Filter/Filter'
import {
  DataManager,
  FormAttributeManager,
  LongRunningTaskManager,
  SecurityManager,
  ConfigurationManager,
  AuditManager
} from '../../../redux'
import EavAttributeForm from '../Form/EavAttributeForm'
import LongRunningTask from '../LongRunningTask/LongRunningTask'
import { selectEntities } from '../../../redux/selectors'

const DEFAULT_QUICK_BUTTON_COUNT = 5
const auditManager = new AuditManager()
const dataManager = new DataManager()

/**
 * Table component with header and columns.
 *
 * @author Radek Tomiška
 * @author Tomáš Doischer
 */
class AdvancedTable extends Basic.AbstractContextComponent {

  constructor (props, context) {
    super(props, context)
    // resolve static frontend actions (backend actions are loaded asynchronously ... check componentDidMount)
    const {manager, actions} = this.props
    let _actions = []
    if (!manager.supportsBulkAction() && actions !== null && actions.length > 0) {
      _actions = actions
        .filter(action => action.rendered === undefined || action.rendered === true || action.rendered === null)
        .map(action => {
          action.showWithSelection = (action.showWithSelection === null || action.showWithSelection === undefined)
            ? true
            : action.showWithSelection
          action.showWithoutSelection = (action.showWithoutSelection === null || action.showWithoutSelection === undefined)
            ? false
            : action.showWithoutSelection
          //
          return action
        })
    }
    //
    this.state = {
      filterOpened: this._isFilterOpened(props, context),
      selectedRows: this.props.selectedRows,
      removedRows: new Immutable.Set(),
      showBulkActionDetail: false,
      bulkActionShowLoading: false,
      anchorEl: null,
      _actions
    }
    this.attributeManager = new FormAttributeManager()
    this.longRunningTaskManager = new LongRunningTaskManager()
  }

  /**
   * Return component identifier, with can be used in localization etc.
   *
   * @return {string} component identifier
   */
  getComponentKey () {
    return 'component.advanced.Table'
  }

  _getBulkActionLabel (backendBulkAction) {
    return this.i18n(`${backendBulkAction.module}:eav.bulk-action.${backendBulkAction.name}.label`, {
      defaultValue: backendBulkAction.description || backendBulkAction.name
    })
  }

  componentDidMount () {
    const {initialReload} = this.props
    if (initialReload) {
      this.reload()
    }
    //
    this.reloadBulkActions()
  }

  UNSAFE_componentWillReceiveProps (newProps) {
    if (!Domain.SearchParameters.is(newProps.forceSearchParameters, this.props.forceSearchParameters)) {
      this.reload(newProps)
    } else if (!Domain.SearchParameters.is(newProps.defaultSearchParameters, this.props.defaultSearchParameters)) {
      this.reload(newProps)
    } else if (newProps.rendered !== this.props.rendered) {
      this.reload(newProps)
    }
  }

  reload (props = null) {
    let _props = this.props
    if (props) {
      _props = {
        ...this.props,
        ...props
      }
    }
    const {rendered, _searchParameters} = _props
    if (!rendered) {
      return
    }
    this.fetchEntities(_searchParameters, _props)
  }

  /**
   * Relaoad available backedn bulk actions.
   * e.g. refresh default values in bulk action setting.
   *
   * @since 11.1.0
   */
  reloadBulkActions () {
    const {manager} = this.props
    //
    if (manager.supportsBulkAction() && manager.canRead()) {
      this.context.store.dispatch(manager.fetchAvailableBulkActions((actions, error) => {
        if (error) {
          if (error.statusCode === 403) {
            // user doesn't have permissions for work with entities in the table
          } else {
            this.addErrorMessage({}, error)
          }
        } else {
          // TODO: react elements are stored in state ... redesign raw data and move cached actions into reducer
          const _actions = actions
            .map(backendBulkAction => {
              const actionKey = `${backendBulkAction.module}:eav.bulk-action.${backendBulkAction.name}`
              const iconKey = `${actionKey}.icon`
              const icon = backendBulkAction.icon || this.i18n(iconKey)
              const label = this._getBulkActionLabel(backendBulkAction)
              return {
                value: backendBulkAction.name,
                order: backendBulkAction.order,
                actionKey,
                icon,
                label,
                level: backendBulkAction.level && backendBulkAction.level !== 'SUCCESS' ? backendBulkAction.level.toLowerCase() : 'default',
                deleteAction: backendBulkAction.deleteAction,
                quickButton: backendBulkAction.quickButton,
                quickButtonable: backendBulkAction.quickButtonable,
                niceLabel: (
                  <span key={`b-a-${backendBulkAction.name}`}>
                    <Basic.Icon
                      value={icon}
                      rendered={backendBulkAction.module + icon !== iconKey}
                      style={{marginRight: 5, width: 18, textAlign: 'center'}}/>
                    {label}
                  </span>
                ),
                action: this.showBulkActionDetail.bind(this, backendBulkAction),
                disabled: !SecurityManager.hasAllAuthorities(backendBulkAction.authorities),
                showWithSelection: backendBulkAction.showWithSelection,
                showWithoutSelection: backendBulkAction.showWithoutSelection,
              }
            })
          //
          this.setState({
            _actions
          })
        }
      }))
    }
  }

  /**
   * Clears row selection
   */
  clearSelectedRows () {
    this.setState({
      selectedRows: []
    })
  }

  /**
   * Process select row in table. The method is ised only for bulk action.
   * Methot has own behavior for select all.
   *
   * @param  rowIndex
   * @param  selected
   * @return
   */
  selectRowForBulkAction (rowIndex, selected) {
    const {selectedRows, removedRows} = this.state
    let newRemovedRows = new Immutable.Set(removedRows)
    let newSelectedRows = new Immutable.Set(selectedRows)
    if (rowIndex === -1) {
      // de/select all rows
      // reset selected rows
      newSelectedRows = new Immutable.Set()
      if (selected) {
        newSelectedRows = newSelectedRows.add(Basic.Table.SELECT_ALL)
      } else {
        newSelectedRows = newSelectedRows.remove(Basic.Table.SELECT_ALL)
      }
      // reset removed rows
      newRemovedRows = new Immutable.Set()
    } else {
      const recordId = this.refs.table.getIdentifier(rowIndex)
      // de/select one row
      if (_.includes(selectedRows, Basic.Table.SELECT_ALL)) {
        if (selected) {
          newRemovedRows = newRemovedRows.remove(recordId)
        } else {
          newRemovedRows = newRemovedRows.add(recordId)
        }
      } else {
        newSelectedRows = (selected ? newSelectedRows.add(recordId) : newSelectedRows.remove(recordId))
      }
    }
    this.setState({
      selectedRows: newSelectedRows.toArray(),
      removedRows: newRemovedRows
    })
    return newSelectedRows
  }

  isAllRowSelected () {
    const {selectedRows} = this.state
    // if selected rows contains SELECT ALL return true
    return _.includes(selectedRows, Basic.Table.SELECT_ALL)
  }

  isRowSelected (identifier) {
    const {selectedRows, removedRows} = this.state
    if (_.includes(selectedRows, Basic.Table.SELECT_ALL)) {
      return !removedRows.has(identifier)
    }
    return _.includes(selectedRows, identifier)
  }

  preprocessBulkAction (bulkAction, cb = null) {
    const _searchParameters = this._mergeSearchParameters(this.props._searchParameters)

    const {selectedRows, removedRows} = this.state

    if (bulkAction) {
      const bulkActionToProcess = {
        ...bulkAction
      }
      const {manager} = this.props
      //
      if (_.includes(selectedRows, Basic.Table.SELECT_ALL)) {
        bulkActionToProcess.filter = _searchParameters.getFilters().toJSON()
        bulkActionToProcess.removeIdentifiers = removedRows.toArray()
      } else {
        bulkActionToProcess.identifiers = selectedRows
      }

      this.setState({
        bulkActionShowLoading: true
      }, () => {
        this.context.store.dispatch(manager.preprocessBulkAction(bulkActionToProcess, (bulkActionPreprocessed) => {
          if (bulkActionPreprocessed) {
            this.setState({
              bulkActionShowLoading: false,
              backendBulkAction: bulkActionPreprocessed
            })
            if (cb) {
              cb(bulkActionPreprocessed)
            }
          } else {
            this.setState({
              bulkActionShowLoading: false
            })
          }
        }))
      })
    }
  }

  prevalidateBulkAction (bulkAction, event) {
    if (event) {
      event.preventDefault()
    }
    const _searchParameters = this._mergeSearchParameters(this.props._searchParameters)

    const {selectedRows, removedRows} = this.state

    if (bulkAction) {
      const bulkActionToProcess = {
        ...bulkAction
      }
      const {manager} = this.props
      // remove unnecessary attributes
      delete bulkActionToProcess.formAttributes
      delete bulkActionToProcess.longRunningTaskId
      delete bulkActionToProcess.permissions
      //
      bulkActionToProcess.properties = this.refs.bulkActionAttributes.getValues()
      if (_.includes(selectedRows, Basic.Table.SELECT_ALL)) {
        bulkActionToProcess.filter = _searchParameters.getFilters().toJSON()
        bulkActionToProcess.removeIdentifiers = removedRows.toArray()
      } else {
        bulkActionToProcess.identifiers = selectedRows
      }
      //
      this.setState({
        bulkActionShowLoading: true
      }, () => {
        this.context.store.dispatch(manager.prevalidateBulkAction(bulkActionToProcess, (resultModel, error) => {
          if (error) {
            this.addErrorMessage({}, error)
            this.setState({
              bulkActionShowLoading: false
            })
          } else if (resultModel) {
            const {backendBulkAction} = this.state
            backendBulkAction.prevalidateResult = resultModel
            this.setState({
              bulkActionShowLoading: false,
              backendBulkAction
            })
          } else {
            this.setState({
              bulkActionShowLoading: false
            })
          }
        }))
      })
    }
  }

  processBulkAction (bulkAction, event) {
    if (event) {
      event.preventDefault()
    }
    if (!this.refs.bulkActionAttributes.isValid()) {
      return
    }
    const _searchParameters = this._mergeSearchParameters(this.props._searchParameters)

    const {selectedRows, removedRows} = this.state

    if (bulkAction) {
      const bulkActionToProcess = {
        ...bulkAction
      }
      const {manager} = this.props
      // remove unnecessary attributes
      delete bulkActionToProcess.formAttributes
      delete bulkActionToProcess.longRunningTaskId
      delete bulkActionToProcess.permissions
      //
      bulkActionToProcess.properties = this.refs.bulkActionAttributes.getValues()
      if (_.includes(selectedRows, Basic.Table.SELECT_ALL)) {
        bulkActionToProcess.filter = _searchParameters.toFilterJson()
        bulkActionToProcess.removeIdentifiers = removedRows.toArray()
      } else {
        bulkActionToProcess.identifiers = selectedRows
      }
      //
      this.setState({
        bulkActionShowLoading: true
      })
      this.context.store.dispatch(manager.processBulkAction(bulkActionToProcess, (processBulkAction, error) => {
        if (error) {
          this.addErrorMessage({}, error)
          this.setState({
            bulkActionShowLoading: false
          })
        } else {
          this.addMessage({
            level: 'info',
            message: this.i18n('bulkAction.created', {
              longRunningTaskId: processBulkAction.longRunningTaskId,
              name: this.i18n(`${processBulkAction.module}:eav.bulk-action.${processBulkAction.name}.label`)
            })
          })
          this.setState({
            selectedRows: [],
            removedRows: new Immutable.Set(),
            bulkActionShowLoading: false,
            backendBulkAction: processBulkAction
          })
        }
      }))
    }
  }

  /**
   * Callback after bulk action ends - called only if LRT detail is shown till end.
   *
   * @param  {object} processedBulkAction currently processed bulk action
   * @since 10.6.0
   */
  _afterBulkAction (processedBulkAction) {
    const {afterBulkAction} = this.props
    let isReload = true
    if (afterBulkAction) {
      isReload = afterBulkAction(processedBulkAction)
    }
    //
    if (isReload !== false) { // null + undefined + true
      this.reload()
    }
  }

  /**
   * Merge hard, default and user deffined search parameters.
   */
  _mergeSearchParameters (searchParameters, props = null) {
    const _props = props || this.props
    const {
      defaultSearchParameters,
      forceSearchParameters,
      manager,
      defaultPageSize,
      pagination,
      draggable
    } = _props
    //
    let _forceSearchParameters = null
    if (forceSearchParameters) {
      // we dont want override setted pagination, if pagination is enabled
      _forceSearchParameters = forceSearchParameters.setSize(pagination && !draggable ? null : 10000).setPage(null)
    }
    let _searchParameters = manager.mergeSearchParameters(
      searchParameters || defaultSearchParameters || manager.getDefaultSearchParameters(), _forceSearchParameters
    )
    // default page size by profile
    // defaultPage size in not stored in redux
    if ((!searchParameters || !searchParameters.getSize()) && defaultPageSize) {
      _searchParameters = _searchParameters.setSize(defaultPageSize)
    }
    //
    return _searchParameters
  }

  fetchEntities (searchParameters, props = null) {
    const _props = props || this.props
    searchParameters = this._mergeSearchParameters(searchParameters, _props)
    const {onReload} = _props
    //
    if (!_props.hideTableShowLoading) {
      this._fetchEntities(searchParameters, _props, (data, error) => {
        if (onReload) {
          onReload(data, error)
        }
      })
    } else {
      this.setState({
        hideTableShowLoading: _props.hideTableShowLoading
      }, () => {
        this._fetchEntities(searchParameters, _props, (data, error) => {
          this.setState({
            hideTableShowLoading: null
          }, () => {
            if (onReload) {
              onReload(data, error)
            }
          })
        })
      })
    }
  }

  _fetchEntities (searchParameters, _props, cb) {
    const {uiKey, manager} = _props
    //
    this.context.store.dispatch(manager.fetchEntities(searchParameters, uiKey, (json, error) => {
      if (error) {
        this.addErrorMessage({
          key: `error-${manager.getEntityType()}-load`
        }, error)
        cb(null, error)
        // remove selection for unpresent records
      } else if (json && json._embedded) {
        const {selectedRows} = this.state
        const newSelectedRows = []
        let data = null
        if (json._embedded[manager.getCollectionType()]) {
          data = json._embedded[manager.getCollectionType()]
          data.forEach(entity => {
            if (_.includes(selectedRows, entity.id)) { // TODO: custom identifier - move to manager
              newSelectedRows.push(entity.id)
            }
          })
        }
        this.setState({
          selectedRows: newSelectedRows
        }, () => {
          cb(data, null)
        })
      } else {
        cb(null, null) // no data, no error
      }
    }))
  }

  _handlePagination (page, size) {
    const {uiKey, manager} = this.props
    this.context.store.dispatch(manager.handlePagination(page, size, uiKey, (json, error) => {
      // croll top - new page of items
      if (!error) {
        /*
        TODO: table top is not visible only
        $('html, body').animate({
          scrollTop: 0
        }, 'fast'); */
      }
    }))
  }

  _handleSort (property, order, shiftKey) {
    const {uiKey, manager} = this.props
    //
    this.context.store.dispatch(manager.handleSort(property, order, uiKey, shiftKey))
  }

  _resolveColumns () {
    const {columns} = this.props
    const children = []
    //
    React.Children.forEach(this.props.children, (child) => {
      if (!React.isValidElement(child) || child == null) {
        return
      }
      invariant(
        // child.type.__TableColumnGroup__ ||
        child.type.__TableColumn__ ||
        child.type.__AdvancedColumn__ || child.type.__AdvancedColumnLink__,
        'child type should be <TableColumn /> or ' +
        'child type should be <AdvancedColumn /> or ' +
        '<AdvancedColumnGroup />'
      )
      children.push(child)
    })
    //
    // sort columns if given
    if (!columns) {
      return children
    }
    const renderedSortedChildren = children.filter(child => !child.props.property) // columns withou property will be at start
    // only columns with properties will be sorted
    columns.forEach(column => {
      const child = children.find(c => c.props.property && c.props.property.toLowerCase() === column.toLowerCase())
      if (child) {
        renderedSortedChildren.push(child)
      }
    })
    //
    return renderedSortedChildren
  }

  useFilterForm (filterForm) {
    this.useFilterData(Domain.SearchParameters.getFilterData(filterForm))
  }

  useFilterData (formData) {
    this.fetchEntities(this._getSearchParameters(formData))
  }

  /**
   * Load filter is opened from redux store or default.
   */
  _isFilterOpened (props, context) {
    const _manager = props.manager || DataManager
    let _filterOpened = null
    if (context && context.store) {
      _filterOpened = _manager.isFilterOpened(context.store.getState(), props.uiKey)
    }
    if (Utils.Ui.isNotEmpty(_filterOpened)) {
      return _filterOpened
    }
    return props.filterOpened // default ~ initial by table usage
  }

  /**
   * Returns search parameters filled from given filter form data
   *
   * @param  {object} formData
   * @return {SearchParameters}
   */
  _getSearchParameters (formData) {
    const {_searchParameters} = this.props
    //
    return Domain.SearchParameters.getSearchParameters(formData, _searchParameters)
  }

  /**
   * Returns search parameters filled from given filter form (referernce)
   *
   * @param  {ref} filterForm
   * @return {SearchParameters}
   */
  getSearchParameters (filterForm) {
    return this._getSearchParameters(Domain.SearchParameters.getFilterData(filterForm))
  }

  cancelFilter (filterForm) {
    const {manager, _searchParameters} = this.props
    //
    const filterValues = filterForm.getData()
    for (const property in filterValues) {
      if (!filterValues[property]) {
        continue
      }
      const filterComponent = filterForm.getComponent(property)
      if (!filterComponent) {
        // filter is not rendered
        continue
      }
      filterComponent.setValue(null)
    }
    // prevent sort and pagination
    let userSearchParameters = _searchParameters.setFilters(manager.getDefaultSearchParameters().getFilters())
    userSearchParameters = userSearchParameters.setPage(0)
    //
    this.fetchEntities(userSearchParameters)
  }

  _onRowSelect (rowIndex, selected, selection) {
    const {onRowSelect} = this.props
    //
    this.setState({
      selectedRows: selection
    }, () => {
      if (onRowSelect) {
        onRowSelect(rowIndex, selected, selection)
      }
    })
  }

  onBulkAction (actionItem) {
    if (actionItem.action) {
      actionItem.action(actionItem.value, this.state.selectedRows, actionItem)
    } else {
      this.addMessage({
        level: 'info',
        message: this.i18n('bulk-action.notImplemented')
      })
    }
    return false
  }

  getNoData (noData) {
    if (noData !== null && noData !== undefined) {
      return noData
    }
    // default noData
    return this.i18n('noData', {defaultValue: 'No record found'})
  }

  _showId () {
    const {showId, appShowId} = this.props
    //
    if (showId !== null && showId !== undefined) {
      // table prop => highest priority
      return showId
    }
    return appShowId
  }

  _filterOpen (open) {
    const {filterOpen} = this.props
    let result = true
    if (filterOpen) {
      result = filterOpen(open)
    }
    //
    if (result !== false) {
      this.setState({
        filterOpened: open
      })
    }
  }

  showBulkActionDetail (backendBulkAction, cb = null) {
    const {showBulkActionDetail} = this.state
    //
    if (showBulkActionDetail) { // FIXME: ~close bulk action ...
      this.setState({
        showBulkActionDetail: !showBulkActionDetail
      })
    } else {
      // move filter values int bulk action parameters automatically
      const searchParameters = this._mergeSearchParameters(this.props._searchParameters)
      const values = []
      searchParameters.getFilters().forEach((filter, property) => {
        if (filter !== null && filter !== undefined) {
          if (_.isArray(filter)) {
            filter.forEach(singleValue => {
              values.push({
                _embedded: {
                  formAttribute: {
                    code: property
                  }
                },
                value: singleValue
              })
            })
          } else if (_.isObject(filter)) {
            // TODO: expand nested properties is not supported
          } else {
            values.push({
              _embedded: {
                formAttribute: {
                  code: property
                }
              },
              value: filter
            })
          }
        }
      })
      //
      if (backendBulkAction.supportsPreprocessing) {
        this.preprocessBulkAction(backendBulkAction, (bulkActionPreprocessed) => {
          this.setState({
            showBulkActionDetail: !showBulkActionDetail,
            backendBulkAction: bulkActionPreprocessed,
            now: moment(new Date()).format(this.i18n('format.datetime')),
            formInstance: new Domain.FormInstance({}, values)
          }, () => {
            // @todo-upgrade-10 This is brutal hack!
            // I had to use the timeout, because Modal doesn't have rendered refs in this phase.
            // This problem occured after update on React 16
            // @todo-upgrade-12 still occurs with material-ui modals
            setTimeout(() => {
              this.prevalidateBulkAction(backendBulkAction)
            }, 10)
          })
        })
      } else {
        this.setState({
          showBulkActionDetail: !showBulkActionDetail,
          backendBulkAction,
          now: moment(new Date()).format(this.i18n('format.datetime')),
          formInstance: new Domain.FormInstance({}, values)
        }, () => {
          // @todo-upgrade-10 This is brutal hack!
          // I had to use the timeout, because Modal doesn't have rendered refs in this phase.
          // This problem occured after update on React 16
          // @todo-upgrade-12 still occurs with material-ui modals
          setTimeout(() => {
            this.prevalidateBulkAction(backendBulkAction)
          }, 10)
        })
      }
    }
  }

  showAudit (entity, property, event) {
    if (event) {
      event.preventDefault()
    }
    const propertyValue = property === 'entityId' ? entity.id : entity[property]
    // set search parameters in redux
    const searchParameters = auditManager.getDefaultSearchParameters()
      .setFilter(property === 'entityId' ? 'relatedOwnerId' : property, propertyValue)
      .setFilter('ownerIdType', property === 'entityId' ? 'relatedOwnerId' : null)
    // co conctete audit table
    this.context.store.dispatch(auditManager.requestEntities(searchParameters, 'audit-table'))
    // prevent to show loading, when transaction id is the same
    this.context.store.dispatch(dataManager.stopRequest('audit-table'))
    // redirect to audit of entities with prefiled search parameters
    if (this.props.uiKey === 'audit-table') {
      // audit table reloads externally ()
    } else {
      this.context.history.push(`/audit/entities?${property}=${propertyValue}`)
    }
  }

  /**
   * Removes prohibited actions.
   */
  _removeProhibitedActions (actions) {
    const {prohibitedActions} = this.props

    return actions.filter(action => {
      if (!prohibitedActions) {
        return true
      }
      return prohibitedActions
        .filter(prohibitedAction => action.value === prohibitedAction)
        .length === 0
    })
  }

  _sortActions (one, two) {
    if (one.order === two.order) {
      return this._getBulkActionLabel(one).localeCompare(this._getBulkActionLabel(two))
    }
    //
    return one.order - two.order
  }

  /**
   * Default implementation - based on entity.seq field and patch method on service.
   *
   * @param  {object} dndProps data, startIndex, differenceIndex
   * @since 10.7.0
   */
  _onDraggableStop (dndProps) {
    const {manager, uiKey, onDraggableStop} = this.props

    if (onDraggableStop) {
      onDraggableStop(dndProps)
      return
    }
    //
    // default implementation - based on entity.seq field
    const {data, startIndex, differenceIndex} = dndProps
    const patchEntities = []
    //
    if (differenceIndex > 0) { // move down
      // startIndex => increment for difference
      // last index
      let lastOrder = null
      const currentRow = data[startIndex]
      // decrement others
      for (let index = startIndex + 1; index <= startIndex + differenceIndex; index++) {
        const decrementRow = data[index]
        if (lastOrder >= decrementRow.seq - 1) {
          // 0 by default => change is needed
          if (lastOrder === null) {
            lastOrder = 0
          } else {
            lastOrder += 1
          }
        } else if (decrementRow.seq - 1 < 0) {
          lastOrder = 0
        } else {
          lastOrder = decrementRow.seq - 1
        }
        patchEntities.push({id: decrementRow.id, seq: lastOrder})
      }
      lastOrder += 1
      if (lastOrder !== currentRow.seq) {
        patchEntities.push({id: currentRow.id, seq: lastOrder})
      }
      // check order for rows after
      for (let index = startIndex + differenceIndex + 1; index < data.length; index++) {
        lastOrder += 1
        const rowAfter = data[index]
        if (rowAfter.seg >= lastOrder) {
          break
        }
        patchEntities.push({id: rowAfter.id, seq: lastOrder})
      }
    } else { // move up
      // start index => decrement by difference
      const firstIndexSeq = data[startIndex + differenceIndex].seq
      const currentRow = data[startIndex]
      if (firstIndexSeq !== currentRow.seq) { // 0 by default => no change is needed
        patchEntities.push({id: currentRow.id, seq: firstIndexSeq})
      }
      // increment others
      let lastOrder = firstIndexSeq
      for (let index = startIndex + differenceIndex; index < startIndex; index++) {
        const incrementRow = data[index]
        if (lastOrder >= incrementRow.seq + 1) {
          // 0 by default => change is needed
          lastOrder += 1
        } else {
          lastOrder = incrementRow.seq + 1
        }
        patchEntities.push({id: incrementRow.id, seq: lastOrder})
      }
      // check order for rows after
      for (let index = startIndex + 1; index < data.length; index++) {
        lastOrder += 1
        const rowAfter = data[index]
        if (rowAfter.seg >= lastOrder) {
          break
        }
        patchEntities.push({id: rowAfter.id, seq: (lastOrder)})
      }
    }
    //
    let hasError = false
    if (patchEntities.length > 0) {
      this.context.store.dispatch(dataManager.startRequest(uiKey))
      patchEntities.reduce((sequence, entity) => {
        return sequence
          .then(() => {
            // we need raw promise
            return manager.getService().patchById(entity.id, entity)
          })
          .catch(error => {
            this.addError(error)
            hasError = true
            //
            throw error
          })
      }, Promise.resolve())
        .catch((error) => {
          // nothing - message is propagated before
          // catch is before then - we want execute next then clausule
          return error
        })
        .then(() => {
          if (!hasError) {
            this.addMessage({
              key: 'basic-table-draggable-message',
              level: 'success',
              message: this.i18n('component.basic.Table.draggable.message.success')
            })
          }
          this.reload() // ~ stop request
        })
    }
  }

  /**
   * Show dragable column for change records order.
   *
   * @param  {SearchParameters} _searchParameters filter
   * @param  {arrayOf[dto]} _entities             fetched entities
   * @param  {number} total                       count of all entites fit given filter
   * @return {bool}
   * @since 11.1.0
   */
  showDraggable (searchParameters) {
    const {
      showDraggable,
      draggable,
      forceSearchParameters,
      _entities,
      _total
    } = this.props
    //
    // external callback -the highest priority
    if (showDraggable) {
      return showDraggable({
        searchParameters,
        entities: _entities,
        total: _total
      })
    }
    //
    if (!draggable) {
      // dragable is not enabled
      return false
    }
    if (!_entities || _entities.length === 0) {
      // entities are not given
      return false
    }
    if (!Domain.SearchParameters.isEmptyFilter(searchParameters, forceSearchParameters)) {
      // filter is defined => order cannot be changed on sub group
      return false
    }
    if (_total > _entities.length) {
      // pagiantioni is set  => order cannot be changed on sub group
      return false
    }
    //
    return true
  }

  _handleOpenBulkActionMenu (event) {
    this.setState({
      anchorEl: event.currentTarget
    })
  }

  _handleCloseBulkActionMenu (cb = null) {
    this.setState({
      anchorEl: null
    }, cb)
  }

  _renderPrevalidateMessages (backendBulkAction) {
    if (!backendBulkAction.prevalidateResult) {
      return null
    }
    if (!backendBulkAction.prevalidateResult._infos) {
      return null
    }

    const result = []
    for (const model of backendBulkAction.prevalidateResult._infos) {
      result.push(
        <Basic.FlashMessage
          showHtmlText
          message={this.getFlashManager().convertFromResultModel(model)}/>
      )
    }
    return result
  }

  _renderBulkActionDetail () {
    const {
      backendBulkAction,
      showBulkActionDetail,
      bulkActionShowLoading,
      selectedRows,
      now,
      removedRows,
      formInstance
    } = this.state
    const {_total, manager} = this.props
    const count = _total - removedRows.size

    const isSelectedAll = _.includes(selectedRows, Basic.Table.SELECT_ALL)
    // get entities for currently selected
    let selectedEntities = []
    if (!isSelectedAll) {
      selectedEntities = manager.getEntitiesByIds(this.context.store.getState(), selectedRows)
    }
    //
    // get entitties for currently deselected
    let removedEnties = []
    if (removedRows.size > 0) {
      removedEnties = manager.getEntitiesByIds(this.props._state, removedRows.toArray())
    }
    const modalContent = []
    let bsSize = 'md'
    let fullWidth = false
    if (backendBulkAction && backendBulkAction.longRunningTaskId) {
      if (SecurityManager.hasAuthority('SCHEDULER_AUTOCOMPLETE')) {
        fullWidth = true
        bsSize = 'sm'
        modalContent.push(
          <LongRunningTask
            entityIdentifier={backendBulkAction.longRunningTaskId}
            header={this.i18n(`${backendBulkAction.module}:eav.bulk-action.${backendBulkAction.name}.label`)}
            showProperties={false}
            onComplete={() => this._afterBulkAction(backendBulkAction)}
            style={{marginBottom: 0}}
            footerButtons={
              <Basic.Button
                level="link"
                onClick={this.showBulkActionDetail.bind(this)}>
                {this.i18n('button.close')}
              </Basic.Button>
            }/>
        )
      } else {
        modalContent.push(
          <Basic.Modal.Header
            icon={this.i18n(`${backendBulkAction.module}:eav.bulk-action.${backendBulkAction.name}.icon`, {defaultValue: ''})}
            text={this.i18n(`${backendBulkAction.module}:eav.bulk-action.${backendBulkAction.name}.label`)}
            closeButton/>
        )
        modalContent.push(
          <Basic.Modal.Body>
            <Basic.Alert
              level="info"
              text={this.i18n('bulkAction.insufficientReadPermission')}/>
          </Basic.Modal.Body>
        )
        modalContent.push(
          <Basic.Modal.Footer>
            <Basic.Button
              level="link"
              onClick={this.showBulkActionDetail.bind(this)}>
              {this.i18n('button.close')}
            </Basic.Button>
          </Basic.Modal.Footer>
        )
      }
    } else if (backendBulkAction) {
      const helpKey = `${backendBulkAction.module}:eav.bulk-action.${backendBulkAction.name}.help`
      const help = this.i18n(helpKey)
      //
      modalContent.push(
        <Basic.Modal.Header
          icon={this.i18n(`${backendBulkAction.module}:eav.bulk-action.${backendBulkAction.name}.icon`, {defaultValue: ''})}
          text={this.i18n(`${backendBulkAction.module}:eav.bulk-action.${backendBulkAction.name}.label`)}
          closeButton/>
      )
      modalContent.push(
        <Basic.Modal.Body>
          <form onSubmit={this.processBulkAction.bind(this, backendBulkAction)}>
            <Basic.AbstractForm
              ref="bulkActionForm"
              showLoading={bulkActionShowLoading}>
              <Basic.Alert
                level="warning"
                text={
                  this.i18n('bulkAction.selectAllRecordsWarning', {
                    count,
                    action: this.i18n(`${backendBulkAction.module}:eav.bulk-action.${backendBulkAction.name}.label`),
                    date: now,
                    escape: false
                  })
                }
                rendered={isSelectedAll}/>
              <Basic.Row
                rendered={!isSelectedAll}
                style={{
                  marginLeft: 0,
                  marginRight: 0,
                  marginBottom: 15
                }}>
                {
                  this.i18n(`bulkAction.message${(selectedRows.length === 0 ? '_empty' : '')}`, {
                    count: selectedRows.length,
                    entities: manager.getNiceLabels(selectedEntities).join(', '),
                    name: this.i18n(`${backendBulkAction.module}:eav.bulk-action.${backendBulkAction.name}.label`),
                    escape: false
                  })
                }
              </Basic.Row>
              <Basic.Row
                rendered={removedEnties.length > 0}
                style={{
                  marginLeft: 0,
                  marginRight: 0,
                  marginBottom: 15
                }}>
                {
                  this.i18n('bulkAction.removedRecord', {
                    count: removedEnties.length,
                    entities: manager.getNiceLabels(removedEnties).join(', '),
                    escape: false
                  })
                }
              </Basic.Row>
              <Basic.Alert
                level="info"
                showHtmlText
                text={help}
                rendered={(`${backendBulkAction.module}:${help}`) !== helpKey}/>

              {this._renderPrevalidateMessages(backendBulkAction)}

              <EavAttributeForm
                ref="bulkActionAttributes"
                localizationKey={backendBulkAction.name}
                localizationModule={backendBulkAction.module}
                formAttributes={backendBulkAction.formAttributes}
                formInstance={formInstance}
                localizationType="bulk-action"/>
            </Basic.AbstractForm>
            {/* onEnter action - is needed because footer submit button is outside form */}
            <input type="submit" className="hidden"/>
          </form>
        </Basic.Modal.Body>
      )
      modalContent.push(
        <Basic.Modal.Footer>
          <Basic.Button
            level="link"
            onClick={this.showBulkActionDetail.bind(this)}>
            {this.i18n('button.close')}
          </Basic.Button>
          <Basic.Button
            type="submit"
            variant="contained"
            level={backendBulkAction.level ? backendBulkAction.level.toLowerCase() : 'success'}
            showLoading={bulkActionShowLoading}
            showLoadingIcon
            showLoadingText={this.i18n('button.saving')}
            onClick={this.processBulkAction.bind(this, backendBulkAction)}>
            {this.i18n('bulkAction.button.execute')}
          </Basic.Button>
        </Basic.Modal.Footer>
      )
    }
    //
    return (
      <Basic.Modal
        show={showBulkActionDetail}
        onHide={this.showBulkActionDetail.bind(this)}
        backdrop="static"
        bsSize={bsSize}
        fullWidth={fullWidth}>
        {modalContent}
      </Basic.Modal>
    )
  }

  render () {
    const {
      _entities,
      _total,
      _showLoading,
      _error,
      _quickButtonCount,
      _menuIncluded,
      uiKey,
      manager,
      pagination,
      onRowClick,
      onRowDoubleClick,
      rowClass,
      rendered,
      filter,
      filterCollapsible,
      filterViewportOffsetTop,
      buttons,
      noData,
      style,
      showRowSelection,
      showLoading,
      showFilter,
      showPageSize,
      showToolbar,
      showRefreshButton,
      showAuditLink,
      showTransactionId,
      condensed,
      header,
      forceSearchParameters,
      className,
      uuidEnd,
      hover,
      sizeOptions,
      quickButtonCount,
      noHeader
    } = this.props
    const {
      filterOpened,
      selectedRows,
      removedRows,
      anchorEl,
      _actions,
      hideTableShowLoading
    } = this.state
    //
    if (!rendered) {
      return null
    }
    //
    const columns = this._resolveColumns()
    let range = null
    const _searchParameters = this._mergeSearchParameters(this.props._searchParameters)
    if (_searchParameters) {
      range = {
        page: _searchParameters.getPage(),
        size: _searchParameters.getSize()
      }
    }
    const renderedColumns = []
    for (let i = 0; i < columns.length; i++) {
      const column = columns[i]
      // basic column support
      if (column.type.__TableColumn__) {
        // add cloned elemet with data provided
        renderedColumns.push(column)
        continue
      }
      // common props to all column faces
      const commonProps = {
        // title: column.props.title,
        className: `column-face-${column.props.face}`
      }
      // construct basic column from advanced column definition
      let columnHeader = column.props.header
      let columnTitle = column.props.title
      if (column.props.property) {
        if (!columnHeader) {
          columnHeader = this.i18n(
            `${manager.getModule()}:entity.${manager.getEntityType()}.${column.props.property}.label`, // label has higher priority
            {defaultValue: this.i18n(`${manager.getModule()}:entity.${manager.getEntityType()}.${column.props.property}`)}
          )
        }
        if (!columnTitle) {
          columnTitle = this.i18n(`${manager.getModule()}:entity.${manager.getEntityType()}.${column.props.property}.title`, {defaultValue: ''})
        }
      }
      if (column.props.sort) {
        columnHeader = (
          <Basic.BasicTable.SortHeaderCell
            header={columnHeader}
            sortHandler={this.showDraggable(_searchParameters) ? null : this._handleSort.bind(this)}
            sortProperty={column.props.sortProperty || column.props.property}
            searchParameters={_searchParameters}
            className={commonProps.className}
            title={columnTitle}/>
        )
      } else if (columnTitle) {
        columnHeader = (
          <span title={columnTitle}>
            {columnHeader}
          </span>
        )
      }
      //
      const key = `column_${i}`
      let cell = null
      if (column.props.cell) {
        cell = column.props.cell
      } else if (column.type.__AdvancedColumnLink__) {
        cell = (
          <Basic.BasicTable.LinkCell
            to={column.props.to}
            href={column.props.href}
            target={column.props.target}
            access={column.props.access}
            {...commonProps}/>
        )
      } else {
        switch (column.props.face) {
          case 'text': {
            cell = (
              <Basic.BasicTable.TextCell
                {...commonProps}
                maxLength={column.props.maxLength}/>
            )
            break
          }
          case 'date': {
            cell = (
              <Basic.BasicTable.DateCell
                format={this.i18n('format.date')}
                {...commonProps}/>
            )
            break
          }
          case 'datetime': {
            cell = (
              <Basic.BasicTable.DateCell
                format={this.i18n('format.datetime')}
                {...commonProps}/>
            )
            break
          }
          case 'bool':
          case 'boolean': {
            cell = (
              <Basic.BasicTable.BooleanCell {...commonProps}/>
            )
            break
          }
          case 'enum': {
            cell = (
              <Basic.BasicTable.EnumCell
                enumClass={column.props.enumClass}
                {...commonProps}/>
            )
            break
          }
          default: {
            this.getLogger().trace(`[AdvancedTable] usind default for column face [${column.props.face}]`)
          }
        }
      }
      // add target column with cell by data type
      renderedColumns.push(
        <Basic.BasicTable.Column
          key={key}
          property={column.props.property}
          rendered={column.props.rendered}
          className={column.props.className}
          forceWrap={column.props.forceWrap}
          width={column.props.width}
          header={columnHeader}
          title={columnTitle}
          cell={cell}/>
      )
    }
    //
    let _rowClass = rowClass
    if (!_rowClass) {
      // automatic rowClass by entity's "disabled" attribute
      _rowClass = ({
                     rowIndex,
                     data
                   }) => { return Utils.Ui.getDisabledRowClass(data[rowIndex]) }
    }
    // If is manager in the universal request mode, then row class by requests will be used (REMOVE / ADD / CHANGE).
    if (manager.isRequestModeEnabled && manager.isRequestModeEnabled()) {
      _rowClass = ({
                     rowIndex,
                     data
                   }) => { return Utils.Ui.getRequestRowClass(data[rowIndex]) }
    }
    //
    let count = 0
    if (_.includes(selectedRows, Basic.Table.SELECT_ALL)) {
      count = _total - removedRows.size
    } else {
      count = selectedRows.length
    }
    //
    let _actionsWithSelection = _actions
      .filter(action => { return action.showWithSelection })
    // Remove prohibited actions
    _actionsWithSelection = this._removeProhibitedActions(_actionsWithSelection)

    let _actionsWithoutSelection = _actions
      .filter(action => { return action.showWithoutSelection })
    // Remove prohibited actions
    _actionsWithoutSelection = this._removeProhibitedActions(_actionsWithoutSelection)
    //
    const _actionClassName = _actions.length === 0 ? 'hidden' : 'bulk-action'
    //
    const _isLoading = (_showLoading || showLoading) && !hideTableShowLoading
    //
    // resolve bulk actions
    let processActions = _actions
    // remove prohibited actions
    processActions = this._removeProhibitedActions(processActions)
    // disabled by security reason
    processActions = processActions.filter(a => !a.disabled)
    // quick action buttons - configured
    let buttonActions = processActions.filter(a => a.quickButton)
    processActions = processActions.filter(a => !a.quickButton)
    // delete actions has own menu (at end)
    let deleteActions = processActions.filter(a => a.deleteAction)
    processActions = processActions.filter(a => !a.deleteAction)
    // normal menu actions
    let menuActions = []
    //
    // add quick buttons - by order
    let buttonActionCount = DEFAULT_QUICK_BUTTON_COUNT
    if (!Utils.Ui.isEmpty(quickButtonCount)) {
      buttonActionCount = quickButtonCount
    } else if (!Utils.Ui.isEmpty(_quickButtonCount)) {
      buttonActionCount = parseInt(_quickButtonCount, 10)
    }
    //
    for (let i = 0; i < processActions.length; i++) {
      const action = processActions[i]
      if (action.icon === null || action.icon === undefined) {
        menuActions.push(action)
        // icon is required to quick access button
        continue
      }
      // add quick button
      if (buttonActions.length < buttonActionCount && action.quickButtonable) {
        buttonActions.push(action)
      } else {
        menuActions.push(action)
      }
    }
    if (buttonActions.length < buttonActionCount) {
      const newDeleteActions = []
      // try to add delete button to quick ...
      for (let i = 0; i < deleteActions.length; i++) {
        const action = deleteActions[i]
        if (action.icon === null || action.icon === undefined) {
          newDeleteActions.push(action)
          // icon is required to quick access button
          continue
        }
        //
        if (buttonActions.length < buttonActionCount && action.quickButtonable) {
          buttonActions.push(action)
        } else {
          newDeleteActions.push(action)
        }
      }
      deleteActions = newDeleteActions
    }
    // sort buttons by order and locale
    buttonActions = buttonActions.sort(this._sortActions.bind(this))
    menuActions = menuActions.sort(this._sortActions.bind(this))
    deleteActions = deleteActions.sort(this._sortActions.bind(this))
    // new quick buttons will be rendered
    const showBulkActionSelect = buttonActionCount === 0 || buttonActions.length === 0
    // included buttons action in menu
    let buttonMenuIncludedActions = null
    if (_menuIncluded && buttonActions.length > 0) {
      buttonMenuIncludedActions = buttonActions.map(action => (
        <Basic.MenuItem
          title={this.isDevelopment() ? `Action order: ${action.order}, Action key: ${action.actionKey}` : null}
          onClick={
            (selectedRows.length === 0 && action.showWithSelection && !action.showWithoutSelection)
            ||
            (selectedRows.length > 0 && !action.showWithSelection && action.showWithoutSelection)
              ?
              null
              :
              () => {
                this._handleCloseBulkActionMenu(() => {
                  this.onBulkAction(action)
                })
              }
          }
          disabled={
            (selectedRows.length === 0 && action.showWithSelection && !action.showWithoutSelection)
            ||
            (selectedRows.length > 0 && !action.showWithSelection && action.showWithoutSelection)
          }
          icon={
            <Basic.Icon icon={action.icon} level={action.level}/>
          }>
          {action.label || action.niceLabel}
        </Basic.MenuItem>
      ))
      if (menuActions.length > 0 || deleteActions.length > 0) {
        buttonMenuIncludedActions.push(<Divider/>)
        buttonMenuIncludedActions.push(
          <Basic.MenuItem disabled>
            {this.i18n(`bulkAction.button.next`)}
          </Basic.MenuItem>
        )
        buttonMenuIncludedActions.push(<Divider/>)
      }
    }
    //
    return (
      <div
        className={
          classnames(
            'advanced-table',
            {'bulk-action-supported': manager.supportsBulkAction()},
            {'quick-button-supported': !showBulkActionSelect},
            className
          )
        }
        style={style}>
        {
          (!filter && (_actions.length === 0 || !showRowSelection) && (buttons === null || buttons.length === 0))
          ||
          <Basic.Toolbar
            container={this}
            viewportOffsetTop={filterViewportOffsetTop}
            rendered={showToolbar}
            className="collapse-top">
            <Basic.Collapse in={filterOpened} rendered={showFilter}>
              <Basic.Div showLoading={_isLoading} showAnimation={false}>
                {filter}
              </Basic.Div>
            </Basic.Collapse>
            <div className="advanced-table-heading">
              <div className="bulk-action-container">
                {
                  showBulkActionSelect
                    ?
                    <Basic.EnumSelectBox
                      onChange={this.onBulkAction.bind(this)}
                      ref="bulkActionSelect"
                      componentSpan=""
                      className={_actionClassName}
                      multiSelect={false}
                      options={selectedRows.length <= 0 ? _actionsWithoutSelection : _actionsWithSelection}
                      placeholder={this.i18n(`bulk-action.selection${selectedRows.length === 0 ? '_empty' : ''}`, {count})}
                      rendered={
                        (
                          (selectedRows.length <= 0 && _actionsWithoutSelection.length > 0)
                          ||
                          (selectedRows.length > 0 && _actionsWithSelection.length > 0)
                        )
                        && showRowSelection
                      }
                      searchable={false}
                      emptyOptionLabel={false}/>
                    :
                    <Basic.Div
                      className={_actionClassName}
                      style={this.showDraggable(_searchParameters) ? {paddingLeft: 19} : {}}
                      rendered={_actions.length > 0 && showRowSelection}>
                      {
                        buttonActions.map(action => {
                          return (
                            <Basic.Button
                              className="bulk-action-button"
                              title={
                                this.isDevelopment()
                                  ?
                                  `${action.label || action.niceLabel} - Action order: ${action.order}, Action key: ${action.actionKey}`
                                  :
                                  (action.label || action.niceLabel)
                              }
                              disabled={
                                (selectedRows.length === 0 && action.showWithSelection && !action.showWithoutSelection)
                                ||
                                (selectedRows.length > 0 && !action.showWithSelection && action.showWithoutSelection)
                              }
                              titlePlacement="bottom"
                              onClick={this.onBulkAction.bind(this, action)}
                              icon={action.icon}
                              level={action.level}/>
                          )
                        })
                      }
                      {
                        (menuActions.length === 0 && deleteActions.length === 0 && (!_menuIncluded || buttonActions.length === 0))
                        ||
                        <>
                          <IconButton
                            bsRole="toggle"
                            aria-label="more"
                            aria-controls="bulk-action-menu"
                            aria-haspopup="true"
                            title={_menuIncluded ? this.i18n(`bulkAction.button.all`) : this.i18n(`bulkAction.button.next`)}
                            onClick={(event) => {
                              this._handleOpenBulkActionMenu(event)
                            }}>
                            <MoreVertIcon/>
                          </IconButton>
                          <Menu
                            id="bulk-action-menu"
                            anchorEl={anchorEl}
                            keepMounted
                            open={Boolean(anchorEl)}
                            onClose={this._handleCloseBulkActionMenu.bind(this, null)}>
                            <MenuList>
                              <Basic.MenuItem disabled>
                                {this.i18n(`bulk-action.selection${selectedRows.length === 0 ? '_empty' : ''}`, {count})}
                              </Basic.MenuItem>
                              <Divider/>

                              {
                                buttonMenuIncludedActions
                              }
                              {
                                menuActions.map(action => {
                                  return (
                                    <Basic.MenuItem
                                      title={this.isDevelopment() ? `Action order: ${action.order}, Action key: ${action.actionKey}` : null}
                                      onClick={
                                        (selectedRows.length === 0 && action.showWithSelection && !action.showWithoutSelection)
                                        ||
                                        (selectedRows.length > 0 && !action.showWithSelection && action.showWithoutSelection)
                                          ?
                                          null
                                          :
                                          () => {
                                            this._handleCloseBulkActionMenu(() => {
                                              this.onBulkAction(action)
                                            })
                                          }
                                      }
                                      disabled={
                                        (selectedRows.length === 0 && action.showWithSelection && !action.showWithoutSelection)
                                        ||
                                        (selectedRows.length > 0 && !action.showWithSelection && action.showWithoutSelection)
                                      }
                                      icon={
                                        <Basic.Icon
                                          icon={action.icon}
                                          level={action.level}/>
                                      }>
                                      {action.label || action.niceLabel}
                                    </Basic.MenuItem>
                                  )
                                })
                              }
                              {
                                deleteActions.length === 0 || menuActions.length === 0
                                ||
                                <Divider/>
                              }
                              {
                                deleteActions.map(action => {
                                  return (
                                    <Basic.MenuItem
                                      title={this.isDevelopment() ? `Action order: ${action.order}` : null}
                                      onClick={
                                        (selectedRows.length === 0 && action.showWithSelection && !action.showWithoutSelection)
                                        ||
                                        (selectedRows.length > 0 && !action.showWithSelection && action.showWithoutSelection)
                                          ?
                                          null
                                          :
                                          () => {
                                            this._handleCloseBulkActionMenu(() => {
                                              this.onBulkAction(action)
                                            })
                                          }
                                      }
                                      disabled={(selectedRows.length === 0 && action.showWithSelection && !action.showWithoutSelection)}>
                                      <Basic.Icon
                                        icon={action.icon}
                                        level={action.level}/>
                                      {action.label || action.niceLabel}
                                    </Basic.MenuItem>
                                  )
                                })
                              }
                            </MenuList>
                          </Menu>
                        </>
                      }
                    </Basic.Div>
                }
              </div>
              <div className="button-container">
                {buttons}

                <Filter.ToogleButton
                  uiKey={uiKey}
                  filterOpen={this._filterOpen.bind(this)}
                  filterOpened={filterOpened}
                  rendered={showFilter && filter !== undefined && filterCollapsible}
                  style={{marginLeft: 3}}
                  searchParameters={_searchParameters}
                  forceSearchParameters={forceSearchParameters}/>

                <RefreshButton
                  onClick={this.fetchEntities.bind(this, _searchParameters, this.props)}
                  title={this.i18n('button.refresh')}
                  showLoading={_showLoading}
                  rendered={showRefreshButton}/>
              </div>
            </div>
          </Basic.Toolbar>
        }
        {
          _error
            ?
            <Basic.Alert level="warning">
              <div style={{textAlign: 'center', display: 'none'}}>
                <Basic.Icon value="fa:warning"/>
                {' '}
                {this.i18n('error.load')}
              </div>
              <div style={{textAlign: 'center'}}>
                <Basic.Button onClick={this.reload.bind(this, this.props)}>
                  <Basic.Icon value="fa:refresh"/>
                  {' '}
                  {this.i18n('button.refresh')}
                </Basic.Button>
              </div>
            </Basic.Alert>
            :
            <>
              <Basic.BasicTable.Table
                ref="table"
                uiKey={uiKey ? `sub-basic-table-${uiKey}` : null}
                header={header}
                data={_entities}
                hover={hover}
                showLoading={_isLoading}
                onRowClick={onRowClick}
                onRowDoubleClick={onRowDoubleClick}
                showRowSelection={_actions.length > 0 && showRowSelection}
                selectedRows={selectedRows}
                onRowSelect={this._onRowSelect.bind(this)}
                rowClass={_rowClass}
                condensed={condensed}
                noData={this.getNoData(noData)}
                selectRowCb={manager.supportsBulkAction() ? this.selectRowForBulkAction.bind(this) : null}
                isRowSelectedCb={manager.supportsBulkAction() ? this.isRowSelected.bind(this) : null}
                isAllRowsSelectedCb={manager.supportsBulkAction() ? this.isAllRowSelected.bind(this) : null}
                draggable={this.showDraggable(_searchParameters)}
                onDraggableStop={this._onDraggableStop.bind(this)}
                noHeader={noHeader}>

                {renderedColumns}

                <Basic.Column
                  header={this.i18n('entity.id.label')}
                  property="id"
                  rendered={this._showId()}
                  className="text-center"
                  width={115}
                  cell={
                    ({rowIndex, data, property}) => {
                      const entity = data[rowIndex]
                      const identifier = entity[property]
                      const transactionId = entity.transactionId
                      const _showTransactionId = transactionId && showTransactionId
                      const content = []
                      //
                      content.push(
                        <div style={{display: 'flex', alignItems: 'center'}}>
                          {
                            !_showTransactionId
                            ||
                            <div title={this.i18n('entity.id.help')}>
                              {this.i18n('entity.id.short')}:
                            </div>
                          }
                          <UuidInfo
                            header={this.i18n('entity.id.help')}
                            value={identifier}
                            uuidEnd={uuidEnd}
                            placement="left"
                            buttons={
                              SecurityManager.hasAuthority('AUDIT_READ')
                              && uiKey !== 'audit-table'
                              && showAuditLink
                                ?
                                [
                                  <a
                                    href="#"
                                    onClick={this.showAudit.bind(this, entity, 'entityId')}
                                    title={this.i18n('button.entityId.title')}>
                                    <Basic.Icon icon="component:audit"/>
                                    {' '}
                                    {this.i18n('button.entityId.label')}
                                  </a>
                                ]
                                :
                                null
                            }/>
                        </div>
                      )
                      if (_showTransactionId) {
                        content.push(
                          <div style={{display: 'flex', alignItems: 'center'}}>
                            <div title={this.i18n('entity.transactionId.help')}>
                              {this.i18n('entity.transactionId.short')}:
                            </div>
                            <UuidInfo
                              value={transactionId}
                              uuidEnd={uuidEnd}
                              header={this.i18n('entity.transactionId.label')}
                              placement="left"
                              buttons={
                                SecurityManager.hasAuthority('AUDIT_READ')
                                && showAuditLink
                                  ?
                                  [
                                    <a
                                      href="#"
                                      onClick={this.showAudit.bind(this, entity, 'transactionId')}
                                      title={this.i18n('button.transactionId.title')}>
                                      <Basic.Icon icon="component:audit"/>
                                      {' '}
                                      {this.i18n('button.transactionId.label')}
                                    </a>
                                  ]
                                  :
                                  null
                              }/>
                          </div>
                        )
                      }
                      //
                      return content
                    }
                  }/>
              </Basic.BasicTable.Table>
              <Basic.BasicTable.Pagination
                ref="pagination"
                showPageSize={showPageSize}
                paginationHandler={pagination && !this.showDraggable(_searchParameters) ? this._handlePagination.bind(this) : null}
                total={pagination ? _total : _entities.length}
                sizeOptions={sizeOptions}
                {...range} />
            </>
        }
        {this._renderBulkActionDetail()}
      </div>
    )
  }
}

AdvancedTable.propTypes = {
  ...Basic.AbstractContextComponent.propTypes,
  /**
   * Table identifier - it's used as key in store
   */
  uiKey: PropTypes.string,
  /**
   * Table Header
   */
  header: PropTypes.oneOfType([PropTypes.string, PropTypes.element]),
  /**
   * EntityManager subclass, which provides data fetching
   */
  manager: PropTypes.object.isRequired,
  /**
   * If pagination is shown
   */
  pagination: PropTypes.bool, // enable paginator action
  /**
   * "Hard filters"
   */
  forceSearchParameters: PropTypes.object,
  /**
   * "Default filters"
   */
  defaultSearchParameters: PropTypes.object,
  /**
   * Callback that is called when a row is clicked.
   */
  onRowClick: PropTypes.func,
  /**
   * Callback that is called when a row is double clicked.
   */
  onRowDoubleClick: PropTypes.func,
  /**
   * Callback that is called when a row is selected.
   */
  onRowSelect: PropTypes.func,
  /**
   * Callback that is called table is refreshed (after refresh button is clicked and data are refreshed).
   *
   * @since 10.7.0
   */
  onReload: PropTypes.func,
  /**
   * Enable row selection - checkbox in first cell
   */
  showRowSelection: PropTypes.oneOfType([PropTypes.bool, PropTypes.func]),
  /**
   * Shows column with id. Default is id shown in Development stage.
   */
  showId: PropTypes.bool,
  /**
   * selected row indexes as immutable set
   */
  selectedRows: PropTypes.arrayOf(PropTypes.oneOfType([PropTypes.string, PropTypes.number])),
  /**
   * css added to row
   */
  rowClass: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.func
  ]),
  /**
   * Filter definition
   * @type {Filter}
   */
  filter: PropTypes.element,
  /**
   * Show filter
   */
  showFilter: PropTypes.bool,
  /**
   * If filter is opened by default
   */
  filterOpened: PropTypes.bool,
  /**
   * External filter open function. If false is returned, internal filterOpened is not set.
   */
  filterOpen: PropTypes.func,
  /**
   * If filter can be collapsed
   */
  filterCollapsible: PropTypes.bool,
  /**
   * When affixed, pixels to offset from top of viewport
   */
  filterViewportOffsetTop: PropTypes.number,
  /**
   * Bulk actions e.g. { value: 'activate', niceLabel: this.i18n('content.identities.action.activate.action'), action: this.onActivate.bind(this) }
   * This prop is ignored, if backend actions are used (see manager.supportsBulkAction())
   */
  actions: PropTypes.arrayOf(PropTypes.object),
  /**
   * Buttons are shown on the right of toogle filter button
   */
  buttons: PropTypes.arrayOf(PropTypes.element),
  /**
   * If table data is empty, then this text will be shown
   */
  noData: PropTypes.oneOfType([PropTypes.string, PropTypes.element]),
  /**
   * Shows page size
   */
  showPageSize: PropTypes.bool,
  /**
   * Shows toolbar.
   */
  showToolbar: PropTypes.bool,
  /**
   * Shows refresh button.
   */
  showRefreshButton: PropTypes.bool,
  /**
   * Shows links to audit
   */
  showAuditLink: PropTypes.bool,
  /**
   * Table css
   */
  className: PropTypes.string,
  /**
   * Table styles
   */
  style: PropTypes.object,
  /**
   * Shows ending uuid characters in shorten label.
   */
  uuidEnd: PropTypes.bool,
  /**
   * Data are loaded automatically, after component is mounted. Set to false, if initial load will be controlled programatically.
   */
  initialReload: PropTypes.bool,
  /**
   * Enable hover table class
   */
  hover: PropTypes.bool,
  /**
   * Prohibited actions. Defines array an keys of a bulk actions, that shouldn't be visible in this table.
   */
  prohibitedActions: PropTypes.arrayOf(PropTypes.string),
  /**
   * Callback after bulk action ends - called only if LRT detail is shown till end. Return 'false' in your callback, when standard table reload is not needed after end.
   */
  afterBulkAction: PropTypes.func,
  /**
   * Count of quick access buttons for bulk actions in tables - the first count of bulk actions will be shown as button - next action will be rendered in drop down select box.
   * Bulk action icon is required for quick access button - action without icon will be rendered in select box.
   * Bulk action can enforce showing in quick access button (by bulk action configuration).
   *
   * @since 10.6.0
   */
  quickButtonCount: PropTypes.number,
  /**
   * DnD support - table will not be orderable, pagination support, sort support will not be available.
   *
   * @since 10.7.0
   */
  draggable: PropTypes.bool,
  /**
   * Callback after dragable ends. Available parameters:
   * - startIndex - dragged row index (start from 0)
   * - differenceIndex - index difference (+ down, - up)
   *
   * @since 10.7.0
   */
  onDraggableStop: PropTypes.func,
  /**
   * Show dragable column for change records order. Available parameters:
   * - searchParameters - currently set filter
   * - entities - rendered entities
   * - total - count of all entites fit given filter
   *
   * @since 11.1.0
   */
  showDraggable: PropTypes.func,
  //
  // Private properties, which are used internally for async data fetching
  //

  /**
   * loadinig indicator
   */
  _showLoading: PropTypes.bool,
  _entities: PropTypes.arrayOf(PropTypes.object),
  _total: PropTypes.number,
  /**
   * Persisted / used search parameters in redux
   */
  _searchParameters: PropTypes.object,
  _backendBulkActions: PropTypes.arrayOf(PropTypes.object),
}
AdvancedTable.defaultProps = {
  ...Basic.AbstractContextComponent.defaultProps,
  _showLoading: true,
  _entities: [],
  _total: null,
  _searchParameters: null,
  _error: null,
  _backendBulkActions: null,
  _quickButtonCount: null,
  pagination: true,
  showRowSelection: false,
  showFilter: true,
  showId: null,
  selectedRows: [],
  filterCollapsible: true,
  actions: [],
  buttons: [],
  showPageSize: true,
  showToolbar: true,
  showRefreshButton: true,
  showAuditLink: true,
  uuidEnd: false,
  initialReload: true,
  hover: true,
  prohibitedActions: [],
  quickButtonCount: null,
  draggable: false
}

const makeMapStateToProps = () => {
  const selectEntitiesSelector = selectEntities()
  const mapStateToProps = function select (state, component) {
    const uiKey = component.manager.resolveUiKey(component.uiKey)
    const ui = state.data.ui[uiKey]
    const result = {
      i18nReady: state.config.get('i18nReady'),
      appShowId: ConfigurationManager.showId(state),
      showTransactionId: ConfigurationManager.showTransactionId(state),
      defaultPageSize: ConfigurationManager.getDefaultPageSize(state),
      sizeOptions: ConfigurationManager.getSizeOptions(state),
      _quickButtonCount: ConfigurationManager.getPublicValue(state, 'idm.pub.app.show.table.quickButton.count'),
      _menuIncluded: ConfigurationManager.getPublicValueAsBoolean(state, 'idm.pub.app.show.table.quickButton.menuIncluded', false)
    }
    //
    if (!ui) {
      return result
    }
    return {
      ...result,
      _showLoading: ui.showLoading,
      _entities: selectEntitiesSelector(state, component),
      _total: ui.total,
      _searchParameters: ui.searchParameters,
      _error: ui.error,
      _backendBulkActions: component.manager.supportsBulkAction() ? DataManager.getData(state, component.manager.getUiKeyForBulkActions()) : null
    }
  }
  return mapStateToProps
}

export default connect(makeMapStateToProps, null, null, {forwardRef: true})(AdvancedTable)
