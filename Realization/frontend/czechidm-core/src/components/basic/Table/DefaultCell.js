import React from 'react'
import _ from 'lodash'
//
import AbstractComponent from '../AbstractComponent/AbstractComponent'

/**
 * Component that handles default cell.
 *
 * Example usage via from a `Column`:
 * ```
 * const MyColumn = (
 *   <Column
 *     cell={({rowIndex, width, height}) => (
 *       <Cell>
 *         Cell number: <span>{rowIndex}</span>
 *        </Cell>
 *     )}
 *   />
 * );
 * ```
 *
 * @author Radek Tomiška
 */
class DefaultCell extends AbstractComponent {

  /**
   * Returns value by property name from given rowData. Supports nested properties
   *
   * @param  {object} rowData entity / rowData
   * @param  {[type]} property e.q. `name`, `identityManager.name`
   * @return {any} property value
   */
  static getPropertyValue (rowData, property) {
    if (!rowData || !property) {
      return null
    }
    if (rowData[property] !== undefined) { // scalar property
      return rowData[property]
    }
    let propertyValue = _.merge({}, rowData)
    //
    if (_.includes(property, '.')) { // nested property
      const nestedProperties = property.split('.') // properties are joined by dot notation e.g `identityManager.name`
      for (const nestedProperty of nestedProperties) {
        if (!propertyValue[nestedProperty]) {
          return null // we don't need previous nested object property value
        }
        propertyValue = propertyValue[nestedProperty]
      }
    } else {
      return null
    }
    return propertyValue
  }

  render () {
    const {style, className, title} = this.props

    return (
      <div style={style} className={className} title={title}>
        {this.props.children}
      </div>
    )
  }
}

DefaultCell.propTypes = {}
DefaultCell.defaultProps = {}

export default DefaultCell
