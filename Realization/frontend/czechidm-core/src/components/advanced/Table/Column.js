import React from 'react'
import PropTypes from 'prop-types'
import { AbstractComponent } from '../../basic'

/**
 * Component that defines the attributes of table column.
 *
 * @author Radek Tomiška
 */
class AdvancedColumn extends AbstractComponent {

  render () {
    const {rendered} = this.props
    if (!rendered) {
      return null
    }
    return (
      <span>Advanced column never render himself</span>
    )
  }
}

AdvancedColumn.propTypes = {
  /**
   * Property from json data object. Nested properties can be used e.g. `identityManager.name`.
   */
  property: PropTypes.string,
  /**
   * Pixel or percent width of table. If number is given, then pixels is used.
   */
  width: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.number
  ]),
  /**
   * column sorting
   */
  sort: PropTypes.bool,
  /**
   * Column data type
   */
  face: PropTypes.oneOf(['text', 'date', 'datetime', 'bool', 'boolean', 'enum']),
  /**
   * This is the body cell that will be cloned for this column.
   * This can either be a string a React element, or a function that generates
   * a React Element. Passing in a string will render a default header cell
   * with that string. By default, the React element passed in can expect to
   * receive the following props:
   *
   * ```
   * props: {
   *   rowIndex; number // (the row index of the cell),
   *   property: string // (of the column, if given),
   *   data: input data
   * }
   * ```
   *
   * Because you are passing in your own React element, you can feel free to
   * pass in whatever props you may want or need.
   *
   * If you pass in a function, you will receive the same props object as the
   * first argument.
   */
  cell: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.node,
    PropTypes.func
  ]),
  /**
   * TODO: css - style
   */
  // style: PropTypes.array
  /**
   * If component is rendered on page
   */
  rendered: PropTypes.bool,

  forceWrap: PropTypes.oneOfType([
    PropTypes.bool,
    PropTypes.oneOf(['anywhere', 'break-word'])
  ])
}
AdvancedColumn.defaultProps = {
  rendered: true
}
AdvancedColumn.__AdvancedColumn__ = true

export default AdvancedColumn
