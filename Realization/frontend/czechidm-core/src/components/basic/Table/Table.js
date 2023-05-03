import React from 'react'
import PropTypes from 'prop-types'
import invariant from 'invariant'
import Immutable from 'immutable'
import _ from 'lodash'
import classNames from 'classnames'
import Waypoint from 'react-waypoint'
//
import * as Utils from '../../../utils'
import AbstractContextComponent
  from '../AbstractContextComponent/AbstractContextComponent'
import Loading from '../Loading/Loading'
import Alert from '../Alert/Alert'
import Row from './Row'
import DefaultCell from './DefaultCell'
import Icon from '../Icon/Icon'

const HEADER = 'header'
// const FOOTER = 'footer';
const CELL = 'cell'
// FE page size - pagination on FE
const FE_PAGE_SIZE = 20

/**
 * Table component with header and columns.
 *
 * @author Radek Tomiška
 */
class Table extends AbstractContextComponent {

  constructor (props, context) {
    super(props, context)
    //
    this.tableBodyRef = React.createRef()
    //
    this.state = {
      startRowIndex: null,
      selectedRows: this.props.selectedRows ? new Immutable.Set(this.props.selectedRows) : new Immutable.Set(),
      showMax: FE_PAGE_SIZE,
      collapsed: props.collapsed
    }
  }

  getComponentKey () {
    return 'component.basic.Table'
  }

  UNSAFE_componentWillReceiveProps (nextProps) {
    this.setState({
      selectedRows: nextProps.selectedRows ? new Immutable.Set(nextProps.selectedRows) : new Immutable.Set()
    })
  }

  componentDidMount () {
    const {collapsed} = this.props
    if (collapsed) {
      this._setCollapsed(collapsed)
    }
  }

  toogleCollapse (event) {
    if (event) {
      event.preventDefault()
    }
    //
    const {collapsed} = this.state
    this._setCollapsed(!collapsed)
  }

  _setCollapsed (collapsed = false) {
    this.setState({
      collapsed
    }, () => {
      const {uiKey, collapsible} = this.props
      //
      if (uiKey && _.isFunction(collapsible)) {
        collapsible(uiKey, collapsed)
      }
    })
  }

  _resolveColumns () {
    const children = []
    //
    if (this.props.children) {
      React.Children.forEach(this.props.children, (child) => {
        if (child == null) {
          return
        }
        invariant(
          // child.type.__TableColumnGroup__ ||
          !child.type || child.type.__TableColumn__,
          'child type should be <TableColumn /> or ' +
          '<TableColumnGroup />'
        )
        // rendered columns only
        if (child.props.rendered) {
          children.push(child)
        }
      })
      return children
    }
    //
    // if columns aren't specified, then resolve columns from given data object
    if (children.length === 0) {
      let properties = []
      const {data} = this.props
      if (data && data.length !== 0) {
        // we can not use just first object, because first row could not contain all properties filled
        data.forEach(row => {
          for (const property in row) {
            if (!row.hasOwnProperty(property)) {
              continue
            }
            properties = this._appendProperty(properties, property, row[property], '')
          }
        })
      }
      properties.forEach(property => {
        children.push(
          <DefaultCell property={property} data={data}/>
        )
      })
    }
    return children
  }

  /**
   * Appends property to given properties array - supports nested properties
   *
   * @param  {array[string]} properties
   * @param  {string} property
   * @param  {object} propertyValue
   * @param  {string} propertyPrefix nested property prefix
   * @return {array} new properties
   */
  _appendProperty (properties, property, propertyValue, propertyPrefix) {
    if (properties.indexOf(propertyPrefix + property) === -1) {
      if (propertyValue && _.isObject(propertyValue)) { // nested property
        // remove nested property prefix - is nested object
        if (propertyPrefix) {
          properties = properties.filter(p => { return p !== propertyPrefix })
        }
        // recursion
        for (const nestedProperty in propertyValue) {
          if (!propertyValue.hasOwnProperty(nestedProperty)) {
            continue
          }
          properties = this._appendProperty(properties, nestedProperty, propertyValue[nestedProperty], `${propertyPrefix}${property}.`)
        }
      } else {
        properties.push(propertyPrefix + property)
      }
    }
    return properties
  }

  _selectColumnElement (columns, type) {
    const newColumns = []
    for (let i = 0; i < columns.length; ++i) {
      const column = columns[i]
      newColumns.push(React.cloneElement(
        column,
        {
          cell: type ? column.props[type] : column.props[CELL]
        }
      ))
    }
    return newColumns
  }

  _showRowSelection ({rowIndex, data, showRowSelection}) {
    if (typeof showRowSelection === 'function') {
      return showRowSelection({
        rowIndex,
        data
      })
    }
    return showRowSelection
  }

  selectRow (rowIndex, selected) {
    const {selectRowCb} = this.props
    let newSelectedRows
    if (selectRowCb != null) {
      newSelectedRows = selectRowCb(rowIndex, selected)
    } else if (rowIndex !== undefined && rowIndex !== null && rowIndex > -1) {
      const recordId = this.getIdentifier(rowIndex)
      newSelectedRows = (selected ? this.state.selectedRows.add(recordId) : this.state.selectedRows.remove(recordId))
    } else { // de/select all
      newSelectedRows = this.state.selectedRows
      const {data} = this.props
      //
      for (let i = 0; i < data.length; i++) {
        if (this._showRowSelection({...this.props, rowIndex: i})) {
          if (selected) {
            newSelectedRows = newSelectedRows.add(this.getIdentifier(i))
          } else {
            newSelectedRows = newSelectedRows.remove(this.getIdentifier(i))
          }
        }
      }
    }
    this.setState({
      selectedRows: newSelectedRows
    }, this._onRowSelect(rowIndex, selected, newSelectedRows.toArray()))
  }

  /**
   * Clears row selection
   */
  clearSelectedRows () {
    const {selectedRows} = this.state
    //
    this.setState({
      selectedRows: selectedRows.clear()
    })
  }

  _onRowSelect (rowIndex, selected, selection) {
    const {onRowSelect} = this.props
    if (!onRowSelect) {
      return
    }
    onRowSelect(rowIndex, selected, selection)
  }

  getSelectedRows () {
    return this.state.selectedRows.toArray()
  }

  _isAllRowsSelected () {
    const {data, isAllRowsSelectedCb} = this.props
    const {selectedRows} = this.state
    if (isAllRowsSelectedCb) {
      return isAllRowsSelectedCb()
    }
    if (!data || data.length === 0) {
      return false
    }
    let enabledRowsCount = 0
    for (let i = 0; i < data.length; i++) {
      if (this._showRowSelection({...this.props, rowIndex: i})) {
        if (!selectedRows.has(this.getIdentifier(i))) {
          return false
        }
        enabledRowsCount += 1
      }
    }
    return enabledRowsCount > 0
  }

  getIdentifierProperty () {
    // TODO: support for custom property?
    return 'id'
  }

  getIdentifier (rowIndex) {
    const {data} = this.props
    return data[rowIndex][this.getIdentifierProperty()]
  }

  _incrementData () {
    const {showMax} = this.state
    this.setState({
      showMax: showMax + FE_PAGE_SIZE
    })
  }

  renderHeader (columns) {
    const {
      showLoading,
      showRowSelection,
      noHeader,
      data,
      draggable
    } = this.props
    if (noHeader) {
      return null
    }
    //
    const headerColumns = this._selectColumnElement(columns, HEADER)
    return (
      <thead key="basic-table-header">
      <Row
        key="row-header"
        columns={headerColumns}
        rowIndex={-1}
        showLoading={showLoading}
        showRowSelection={showRowSelection}
        onRowSelect={showRowSelection ? this.selectRow.bind(this) : null}
        selected={this._isAllRowsSelected()}
        data={data}
        draggable={draggable}/>
      </thead>
    )
  }

  handleStart (event, dnd) {
    const rowTableIndex = dnd.node.rowIndex
    const {noHeader} = this.props
    const startIndex = noHeader ? rowTableIndex : rowTableIndex - 1
    //
    this.setState({
      startIndex,
      differenceIndex: 0
    })
  }

  handleDrag (event, dnd) {
    const {startIndex} = this.state
    const {data} = this.props
    const tableBody = $(this.tableBodyRef.current)
    const curentRowIndex = startIndex + 1
    //
    let differenceIndex
    if (dnd.y > 0) {
      differenceIndex = parseInt((dnd.y + (Row.DRAGABLE_ROW_HEIGHT / 2)) / Row.DRAGABLE_ROW_HEIGHT, 10)
      //
      // move down
      for (let index = 1; index < curentRowIndex; index++) {
        // rest => unchanged
      }
      //
      for (let index = curentRowIndex; index <= startIndex + differenceIndex + 1; index++) {
        if (index === curentRowIndex) {
          continue
        }
        const rowBefore = tableBody.find(`tr:nth-child(${index})`)
        rowBefore.css({
          transform: `translate(0px, ${-Row.DRAGABLE_ROW_HEIGHT}px)`
        })
      }
      //
      // current row
      // const currentRow = tableBody.find(`tr:nth-child(${ curentRowIndex })`);
      //
      // next
      for (let index = startIndex + differenceIndex + 2; index <= data.length; index++) {
        const rowAfter = tableBody.find(`tr:nth-child(${index})`)
        rowAfter.css({
          transform: 'translate(0px, 0px)'
        })
      }
    } else {
      differenceIndex = parseInt((dnd.y - (Row.DRAGABLE_ROW_HEIGHT / 2)) / Row.DRAGABLE_ROW_HEIGHT, 10)
      // move up
      for (let index = 1; index <= startIndex + differenceIndex; index++) {
        const rowAfter = tableBody.find(`tr:nth-child(${index})`)
        rowAfter.css({
          transform: 'translate(0px, 0px)'
        })
      }
      //
      // current row
      // const currentRow = tableBody.find(`tr:nth-child(${ curentRowIndex })`);
      //
      for (let index = startIndex + differenceIndex + 1; index <= startIndex; index++) {
        if (index === (curentRowIndex)) {
          continue
        }
        if (index !== data.length) {
          const rowBefore = tableBody.find(`tr:nth-child(${index})`)
          rowBefore.css({
            transform: `translate(0px, ${Row.DRAGABLE_ROW_HEIGHT}px)`
          })
        }
      }
      // rest => unchanged
      for (let index = startIndex + 1; index <= data.length; index++) {
        // rest => unchanged
      }
    }
    //
    this.setState({
      differenceIndex
    })
  }

  handleStop (event, dnd) {
    const {data, onDraggableStop} = this.props
    const {startIndex, differenceIndex} = this.state
    const tableBody = $(this.tableBodyRef.current) // internal content elemet to enable jquery integration
    const currentRow = tableBody.find(`tr:nth-child(${startIndex + 1})`)
    //
    if (dnd.y > 0) { // move down
      const remain = dnd.y % Row.DRAGABLE_ROW_HEIGHT
      currentRow.css({
        top: (parseInt(dnd.y / Row.DRAGABLE_ROW_HEIGHT, 10) + (remain < (Row.DRAGABLE_ROW_HEIGHT / 2) ? 0 : 1)) * Row.DRAGABLE_ROW_HEIGHT,
        transform: 'translate(0px, 0px)'
      })
    } else { // move up
      const remain = dnd.y % Row.DRAGABLE_ROW_HEIGHT
      currentRow.css({
        top: (parseInt(dnd.y / Row.DRAGABLE_ROW_HEIGHT, 10) - (remain > -(Row.DRAGABLE_ROW_HEIGHT / 2) ? 0 : 1)) * Row.DRAGABLE_ROW_HEIGHT,
        transform: 'translate(0px, 0px)'
      })
    }
    if (differenceIndex !== 0 && onDraggableStop) {
      onDraggableStop({
        data,
        startIndex,
        differenceIndex
      })
    }
  }

  renderBody (columns) {
    const {data, showLoading, supportsPagination} = this.props
    const {showMax} = this.state
    if (!data || data.length === 0) {
      return null
    }
    const rows = []
    for (let i = 0; i < data.length && (!supportsPagination || i < showMax); i++) {
      rows.push(this.renderRow(columns, i))
    }
    return (
      <tbody ref={this.tableBodyRef} className="basic-table-body">
      {rows}
      {
        !supportsPagination
        ||
        showLoading
        ||
        showMax >= data.length
        ||
        <tr>
          <th colSpan={columns.length}>
            <div className="text-center" style={{padding: 15, color: '#ccc'}}>
              <Icon value="fa:refresh" showLoading/>
            </div>
            <div>
              <Waypoint onEnter={this._incrementData.bind(this)}>
                <div>
                  {' '}
                </div>
              </Waypoint>
            </div>
          </th>
        </tr>
      }
      </tbody>
    )
  }

  renderRow (columns, rowIndex) {
    const {
      data,
      onRowClick,
      onRowDoubleClick,
      showRowSelection,
      rowClass,
      isRowSelectedCb,
      draggable
    } = this.props
    const key = `row-${rowIndex}`

    return (
      <Row
        key={key}
        data={data}
        columns={columns}
        rowIndex={rowIndex}
        showRowSelection={showRowSelection}
        onRowSelect={showRowSelection ? this.selectRow.bind(this) : null}
        selected={
          isRowSelectedCb === null
            ?
            this.state.selectedRows.has(this.getIdentifier(rowIndex))
            :
            isRowSelectedCb(this.getIdentifier(rowIndex))
        }
        onClick={onRowClick}
        onDoubleClick={onRowDoubleClick}
        rowClass={rowClass}
        draggable={draggable}
        handleStart={this.handleStart.bind(this)}
        handleDrag={this.handleDrag.bind(this)}
        handleStop={this.handleStop.bind(this)}/>
    )
  }

  renderFooter () {
    return null
  }

  render () {
    const {
      uiKey,
      data,
      noData,
      rendered,
      showLoading,
      hover,
      className,
      condensed,
      header,
      noHeader,
      supportsPagination,
      style,
      draggable,
      collapsible
    } = this.props
    const {collapsed} = this.state
    //
    if (!rendered) {
      return null
    }

    const columns = this._resolveColumns()
    const columnsHeaders = this.renderHeader(columns)
    const body = this.renderBody(columns)
    const footer = this.renderFooter()
    const classNamesTable = classNames(
      {table: true},
      {'table-hover': hover},
      {'table-condensed': condensed},
      {'table-no-header': noHeader}
    )
    //
    const content = []

    if (!data || data.length === 0) {
      if (showLoading) {
        content.push(
          <tr key="row-show-loading">
            <td colSpan={columns.length}>
              <Loading showLoading className="static"/>
            </td>
          </tr>
        )
      } else {
        content.push(
          <tr key="row-no-data">
            <td colSpan={columns.length}>
              <Alert text={noData} className="no-data"/>
            </td>
          </tr>
        )
      }
    } else {
      content.push(columnsHeaders)
      content.push(body)
      content.push(footer)
    }
    //
    const _style = {cursor: 'pointer'}
    if (!collapsed) {
      _style.color = '#ccc'
    }
    //
    return (
      <div
        key={uiKey && draggable ? `${uiKey}-${Utils.Ui.getComponentKey(data)}` : null}
        className={classNames(className, 'basic-table')}
        style={supportsPagination ? {} : {overflowX: 'auto'}}>
        <Loading showLoading={showLoading && data && data.length > 0}>
          <table className={classNamesTable} style={style}>
            {
              !header || noHeader
              ||
              <thead>
              <tr className="basic-table-header">
                <th
                  colSpan={columns.length}
                  style={collapsed ? {borderBottom: 0} : {}}>
                  <div style={{display: 'flex', alignItems: 'center'}}>
                    <div style={{flex: 1}}>
                      {header}
                    </div>
                    <Icon
                      icon={!collapsed ? 'fa:angle-double-up' : 'fa:angle-double-down'}
                      style={_style}
                      onClick={(event) => this.toogleCollapse(event)}
                      rendered={collapsible !== false}
                      title={!collapsed ? this.i18n('button.collapse.title') : this.i18n('button.expand.title')}/>
                  </div>
                </th>
              </tr>
              </thead>
            }
            {
              collapsed
              ||
              content
            }
          </table>
        </Loading>
      </div>
    )
  }
}

Table.propTypes = {
  ...AbstractContextComponent.propTypes,
  /**
   * input data as array of json objects.
   */
  data: PropTypes.arrayOf(PropTypes.object),
  /**
   * Table Header
   */
  header: PropTypes.oneOfType([PropTypes.string, PropTypes.element]),
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
   * selected row indexes as immutable set.
   */
  selectedRows: PropTypes.arrayOf(PropTypes.oneOfType([PropTypes.string, PropTypes.number])),
  /**
   * Enable row selection - checkbox in first cell.
   */
  showRowSelection: PropTypes.oneOfType([PropTypes.bool, PropTypes.func]),
  /**
   * css added to row.
   */
  rowClass: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.func
  ]),
  /**
   * If table data is empty, then this text will be shown.
   */
  noData: PropTypes.oneOfType([PropTypes.string, PropTypes.element]),
  /**
   * Show column headers.
   */
  noHeader: PropTypes.bool,
  /**
   * Enable condensed table class, make tables more compact by cutting cell padding in half.
   */
  condensed: PropTypes.bool,
  /**
   * Enable hover table class.
   */
  hover: PropTypes.bool,
  /**
   * Function that is called after de/select row/s.
   */
  selectRowCb: PropTypes.func,
  /**
   * Function that is called for check if row is selcted.
   */
  isRowSelectedCb: PropTypes.func,
  /**
   * Function that is called for check if all row are selected.
   */
  isAllRowsSelectedCb: PropTypes.func,
  /**
   * Supports frontend pagination.
   *
   * @since 9.5.2
   */
  supportsPagination: PropTypes.bool,
  /**
   * DnD support - table will not be orderable, pagination support will not be available.
   *
   * @since 10.7.0
   */
  draggable: PropTypes.bool,
  /**
   * Callback after dragable ends. Available parameters:
   * - data - table data
   * - startIndex - dragged row index (start from 0)
   * - differenceIndex - index difference (+ down, - up)
   *
   * @since 10.7.0
   */
  onDraggableStop: PropTypes.func,
  /**
   * Collapsible table.
   *
   * @since 11.2.0
   */
  collapsible: PropTypes.oneOfType([PropTypes.bool, PropTypes.func]),
  /**
   * Table is expanded / collapsed.
   *
   * @since 11.2.0
   */
  collapsed: PropTypes.bool
}
Table.defaultProps = {
  ...AbstractContextComponent.defaultProps,
  data: [],
  selectedRows: [],
  showRowSelection: false,
  noData: 'No record found',
  hover: true,
  condensed: false,
  noHeader: false,
  selectRowCb: null,
  isRowSelectedCb: null,
  isAllRowsSelectedCb: null,
  supportsPagination: false,
  draggable: false,
  collapsible: false,
  collapsed: false
}

Table.SELECT_ALL = 'select-all-rows'

export default Table
