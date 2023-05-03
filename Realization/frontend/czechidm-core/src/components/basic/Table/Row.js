import React from 'react'
import PropTypes from 'prop-types'
import Draggable from 'react-draggable'
//
import AbstractContextComponent
  from '../AbstractContextComponent/AbstractContextComponent'
import Cell from './Cell'
import Icon from '../Icon/Icon'

// TODO: implement dynamic row height
const DRAGABLE_ROW_HEIGHT = 64

/**
 * Component that renders the row for <Table />.
 * This component should not be used directly by developer. Instead,
 * only <Table /> should use the component internally.
 *
 * @author Radek Tomiška
 */
class Row extends AbstractContextComponent {

  _onClick (event) {
    this.props.onClick(event, this.props.rowIndex, this.props.data)
  }

  _onDoubleClick (event) {
    this.props.onDoubleClick(event, this.props.rowIndex, this.props.data)
  }

  _onSelect (event) {
    const {onRowSelect, rowIndex} = this.props
    if (!onRowSelect) {
      return
    }
    onRowSelect(rowIndex, event.currentTarget.checked)
  }

  _getTitle ({selected, rowIndex}) {
    if (selected) {
      return this.i18n('component.basic.Table.select.clear', {defaultValue: 'Clear selection'})
    }
    if (rowIndex !== undefined && rowIndex !== null && rowIndex > -1) {
      return this.i18n('component.basic.Table.select.add', {defaultValue: 'Select record'})
    }
    return this.i18n('component.basic.Table.select.addAll', {defaultValue: 'Select records'})
  }

  _getDraggableTitle () {
    return this.i18n('component.basic.Table.draggable.button.title', {defaultValue: 'Change order'})
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

  handleStart (event, dndData) {
    const {handleStart} = this.props
    //
    if (handleStart) {
      return handleStart(event, dndData)
    }
    return null
  }

  handleDrag (event, dndData) {
    const {handleDrag} = this.props
    //
    if (handleDrag) {
      return handleDrag(event, dndData)
    }
    return null
  }

  handleStop (event, dndData) {
    const {handleStop} = this.props
    //
    if (handleStop) {
      return handleStop(event, dndData)
    }
    return null
  }

  render () {
    const {
      rowIndex,
      columns,
      selected,
      rowClass,
      onRowSelect,
      data,
      draggable
    } = this.props
    const cells = new Array(columns.length)
    for (let i = 0, j = columns.length; i < j; i++) {
      const columnProps = columns[i].props

      cells[i] = (
        <Cell
          key={`cell_${i}`}
          rowIndex={rowIndex}
          cell={columnProps.cell}
          forceWrap={columnProps.forceWrap}
          property={columnProps.property}
          data={data}
          showLoading={this.props.showLoading}
          width={columnProps.width}
          className={columnProps.className}
        />
      )
    }
    let _rowClass
    if (!rowClass) {
      _rowClass = ''
    } else if (typeof rowClass === 'function') {
      _rowClass = rowClass({
        rowIndex,
        data
      })
    } else {
      _rowClass = rowClass
    }
    if (draggable) {
      _rowClass += ` draggable`
    }
    //
    const content = (
      <tr
        onClick={this.props.onClick ? this._onClick.bind(this) : null}
        onDoubleClick={this.props.onDoubleClick ? this._onDoubleClick.bind(this) : null}
        className={_rowClass}
        style={draggable ? {
          height: DRAGABLE_ROW_HEIGHT,
          position: 'relative'
        } : null}>
        {
          !draggable
          ||
          <td className="table-action-draggable">
            {
              rowIndex < 0
                ?
                null
                :
                <div className="handle"
                     title={this._getDraggableTitle(this.props)}>
                  <Icon value="fa:ellipsis-v"/>
                </div>
            }
          </td>
        }
        {
          !onRowSelect
          ||
          <td width="16px" className="bulk-selection">
            <input
              type="checkbox"
              checked={selected}
              onChange={this._onSelect.bind(this)}
              title={this._getTitle(this.props)}
              disabled={!this._showRowSelection(this.props)}/>
          </td>
        }
        {cells}
      </tr>
    )
    if (!draggable || rowIndex < 0) {
      return content
    }
    //
    // calculate dragable boundaries => relative to selected row by index.
    const top = -(rowIndex * DRAGABLE_ROW_HEIGHT)
    const bottom = top + ((data ? data.length : 0) * DRAGABLE_ROW_HEIGHT) - DRAGABLE_ROW_HEIGHT
    //
    return (
      <Draggable
        axis="y"
        onStart={this.handleStart.bind(this)}
        onDrag={this.handleDrag.bind(this)}
        onStop={this.handleStop.bind(this)}
        handle=".handle"
        bounds={{top, bottom}}
        defaultClassNameDragging="dragging">
        {content}
      </Draggable>
    )
  }
}

Row.propTypes = {
  rowIndex: PropTypes.number.isRequired,
  columns: PropTypes.arrayOf(PropTypes.any).isRequired,
  /**
   * loadinig indicator
   */
  showLoading: PropTypes.bool,
  /**
   * input data as array of json objects
   */
  data: PropTypes.arrayOf(PropTypes.object),
  /**
   * Callback that is called when a row is clicked.
   */
  onClick: PropTypes.func,

  /**
   * Callback that is called when a row is double clicked.
   */
  onDoubleClick: PropTypes.func,
  /**
   * Enable row selection - checkbox in first cell
   */
  showRowSelection: PropTypes.oneOfType([PropTypes.bool, PropTypes.func]),
  /**
   * Row selection
   */
  onRowSelect: PropTypes.func,
  /**
   * If row is selected
   */
  selected: PropTypes.bool,
  /**
   * css added to row
   */
  rowClass: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.func
  ]),
  /**
   * DnD support - table will not be orderable, pagination support will not be available.
   *
   * @since 10.7.0
   */
  draggable: PropTypes.bool
}
Row.defaultProps = {
  showLoading: false,
  selected: false,
  draggable: false
}

Row.DRAGABLE_ROW_HEIGHT = DRAGABLE_ROW_HEIGHT

export default Row
