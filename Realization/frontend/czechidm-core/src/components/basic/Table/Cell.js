import React from 'react'
import PropTypes from 'prop-types'
import classNames from 'classnames'
import { isFunction, isString } from 'swiss-knife-utils'
//
import AbstractComponent from '../AbstractComponent/AbstractComponent'
import DefaultCell from './DefaultCell'

/**
 * Component that renders the cell for <Table />.
 * This component should not be used directly by developer. Instead,
 * only <Row /> should use the component internally.
 *
 * @author Radek Tomiška
 */
class Cell extends AbstractComponent {

  render () {
    const {
      property,
      data,
      showLoading,
      width,
      className,
      forceWrap,
      ...props
    } = this.props
    const cellProps = {
      showLoading
    }
    const innerStyle = {
      width
    }

    if (props.rowIndex >= 0) {
      cellProps.rowIndex = props.rowIndex
    }
    // default property from owner component
    if (props.cell && props.cell.props && !props.cell.props.property) {
      cellProps.property = property
    }
    // default data from owner component
    if (props.cell && props.cell.props && !props.cell.props.data) {
      cellProps.data = data
    }

    let content
    if (!props.cell) {
      let text = null
      if (props.rowIndex === -1) { // header
        text = property
      } else if (data && property && data[cellProps.rowIndex]) { // body
        text = DefaultCell.getPropertyValue(data[cellProps.rowIndex], property)
      }
      content = (
        <DefaultCell
          {...cellProps}>
          {text}
        </DefaultCell>
      )
    } else if (React.isValidElement(props.cell)) {
      content = React.cloneElement(props.cell, cellProps)
    } else if (isFunction(props.cell)) {
      content = props.cell(this.props)
    } else {
      content = (
        <DefaultCell
          {...cellProps}>
          {props.cell}
        </DefaultCell>
      )
    }

    return (
      <td style={innerStyle}
          className={classNames(className, {
            forceWrap,
            [`forceWrap-${forceWrap}`]: forceWrap && isString(forceWrap)
          })}>
        {content}
      </td>
    )
  }
}

// Properties will be passed to `cellRenderer` to render.
Cell.propTypes = {
  /**
   * The row index that will be passed to `cellRenderer` to render.
   */
  rowIndex: PropTypes.number,
  /**
   * Property from data object - optional. Can be defined in header (cell or footer) element. Nested properties can be used e.g. `identityManager.name`.
   */
  property: PropTypes.string,
  /**
   * input data as array of json objects
   */
  data: PropTypes.array,

  forceWrap: PropTypes.oneOfType([
    PropTypes.bool,
    PropTypes.oneOf(['anywhere', 'break-word'])
  ])
}

Cell.defaultProps = {}

export default Cell
