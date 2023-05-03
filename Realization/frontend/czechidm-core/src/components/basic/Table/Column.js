import React from 'react'
import PropTypes from 'prop-types'
import AbstractComponent from '../AbstractComponent/AbstractComponent'

/**
 * Component that defines the attributes of table column.
 *
 * @author Radek Tomiška
 */
class Column extends AbstractComponent {

  render () {
    const {rendered} = this.props
    if (!rendered) {
      return null
    }
    return (
      <span>Column never render himself</span>
    )
  }
}

Column.propTypes = {
  /**
   * Property from data object - optional. Can be defined in header (cell or footer) element
   */
  property: PropTypes.string,
  /**
   * The header cell for this column.
   * This can either be a string a React element, or a function that generates
   * a React Element. Passing in a string will render a default header cell
   * with that string. By default, the React element passed in can expect to
   * receive the following props:
   *
   * ```
   * props: {
   *   property: string // (of the column, if given)
   * }
   * ```
   *
   * Because you are passing in your own React element, you can feel free to
   * pass in whatever props you may want or need.
   *
   * If you pass in a function, you will receive the same props object as the
   * first argument.
   */
  header: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.node,
    PropTypes.func
  ]),
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
   * Pixel or percent width of table. If number is given, then pixels is used.
   */
  width: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.number
  ]),
  /**
   * If component is rendered on page
   */
  rendered: PropTypes.bool,
  /**
   * css
   */
  className: PropTypes.string,

  forceWrap: PropTypes.oneOfType([
    PropTypes.bool,
    PropTypes.oneOf(['anywhere', 'break-word'])
  ])
}
Column.defaultProps = {
  rendered: true
}
Column.__TableColumn__ = true

export default Column
