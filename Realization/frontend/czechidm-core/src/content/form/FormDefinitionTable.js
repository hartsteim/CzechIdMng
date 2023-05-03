import PropTypes from 'prop-types'
import React from 'react'
import { connect } from 'react-redux'
import uuid from 'uuid'
//
import * as Basic from '../../components/basic'
import * as Advanced from '../../components/advanced'
import * as Utils from '../../utils'
import { SecurityManager, DataManager } from '../../redux'

const TYPES_UIKEY = 'form-definition-types'

/**
 * Table of forms definitions (attributes is show in detail).
 *
 * @author Ondřej Kopr
 * @author Radek Tomiška
 */
export class FormDefinitionTable extends Advanced.AbstractTableContent {

  constructor (props, context) {
    super(props, context)
    this.state = {
      filterOpened: props.filterOpened,
      showLoading: true
    }
  }

  componentDidMount () {
    super.componentDidMount()
    //
    this.context.store.dispatch(this.getManager().fetchTypes(TYPES_UIKEY))
    this.refs.text.focus()
  }

  getContentKey () {
    return 'content.formDefinitions'
  }

  getManager () {
    return this.props.definitionManager
  }

  useFilter (event) {
    if (event) {
      event.preventDefault()
    }
    this.refs.table.useFilterForm(this.refs.filterForm)
  }

  cancelFilter (event) {
    if (event) {
      event.preventDefault()
    }
    this.refs.table.cancelFilter(this.refs.filterForm)
  }

  showDetail (entity) {
    if (entity.id === undefined) {
      const uuidId = uuid.v1()
      this.context.history.push(`/form-definitions/${uuidId}/detail?new=1`)
    } else {
      this.context.history.push(`/form-definitions/${entity.id}/detail`)
    }
  }

  render () {
    const {uiKey, definitionManager, types} = this.props
    const {filterOpened} = this.state
    return (
      <Basic.Div>
        <Basic.Confirm ref="confirm-delete" level="danger"/>
        <Advanced.Table
          ref="table"
          uiKey={uiKey}
          manager={definitionManager}
          showRowSelection
          showDraggable={
            ({searchParameters, entities, total}) => {
              if (!SecurityManager.hasAuthority('FORMDEFINITION_UPDATE')) {
                // dragable is not enabled
                return false
              }
              if (!entities || entities.length === 0) {
                // entities are not given
                return false
              }
              if (total > entities.length) {
                // pagiantioni is set  => order cannot be changed on sub group
                return false
              }
              // filter is set, but owner only
              if (!searchParameters) {
                return false
              }
              const filters = searchParameters.getFilters()
              if (filters.size !== 1) {
                return false
              }
              if (!filters.has('type')) {
                return false
              }
              // sort by order only
              const sorts = searchParameters.getSorts()
              if (sorts.size !== 1) {
                return false
              }
              if (!sorts.has('seq')) {
                return false
              }
              //
              return true
            }
          }
          rowClass={({
                       rowIndex,
                       data
                     }) => { return data[rowIndex].disabled ? 'disabled' : '' }}
          filter={
            <Advanced.Filter onSubmit={this.useFilter.bind(this)}>
              <Basic.AbstractForm ref="filterForm">
                <Basic.Row className="last">
                  <Basic.Col lg={6}>
                    <Advanced.Filter.TextField
                      ref="text"
                      placeholder={this.i18n('filter.text')}/>
                  </Basic.Col>
                  <Basic.Col lg={3}>
                    <Basic.EnumSelectBox
                      ref="type"
                      placeholder={this.i18n('entity.FormDefinition.type')}
                      options={
                        types
                          ?
                          types.map(type => {
                            return {
                              value: type,
                              niceLabel: Utils.Ui.getSimpleJavaType(type)
                            }
                          })
                          :
                          null
                      }
                      searchable
                      clearable={false}/>
                  </Basic.Col>
                  <Basic.Col lg={3} className="text-right">
                    <Advanced.Filter.FilterButtons
                      cancelFilter={this.cancelFilter.bind(this)}/>
                  </Basic.Col>
                </Basic.Row>
              </Basic.AbstractForm>
            </Advanced.Filter>
          }
          buttons={
            [
              <Basic.Button
                level="success"
                key="add_button"
                className="btn-xs"
                onClick={this.showDetail.bind(this, {})}
                rendered={SecurityManager.hasAuthority('FORMDEFINITION_CREATE')}
                icon="fa:plus">
                {this.i18n('button.add')}
              </Basic.Button>
            ]
          }
          filterOpened={filterOpened}
          draggable={SecurityManager.hasAuthority('FORMDEFINITION_UPDATE')}
          _searchParameters={this.getSearchParameters()}>
          <Advanced.Column
            header=""
            className="detail-button"
            cell={
              ({rowIndex, data}) => {
                return (
                  <Advanced.DetailButton
                    title={this.i18n('button.detail')}
                    onClick={this.showDetail.bind(this, data[rowIndex])}/>
                )
              }
            }
            sort={false}/>
          <Advanced.Column
            property="type"
            sort
            face="text"
            width={75}
            cell={
              ({rowIndex, data, property}) => {
                return Utils.Ui.getSimpleJavaType(data[rowIndex][property])
              }
            }/>
          <Advanced.Column property="main"
                           header={this.i18n('entity.FormDefinition.main.label')}
                           face="bool" sort/>
          <Advanced.Column property="code" sort forceWrap="anywhere"/>
          <Advanced.Column property="name" sort forceWrap="anywhere"/>
          <Advanced.Column property="module" sort/>
          <Advanced.Column property="unmodifiable"
                           header={this.i18n('entity.FormDefinition.unmodifiable.label')}
                           face="bool" sort/>
          <Advanced.Column property="seq"
                           header={this.i18n('entity.FormAttribute.seq.label')}
                           sort width={35}/>
        </Advanced.Table>
      </Basic.Div>
    )
  }
}

FormDefinitionTable.propTypes = {
  uiKey: PropTypes.string.isRequired,
  definitionManager: PropTypes.object.isRequired,
  filterOpened: PropTypes.bool
}

FormDefinitionTable.defaultProps = {
  filterOpened: true
}

function select (state, component) {
  return {
    types: DataManager.getData(state, TYPES_UIKEY),
    _searchParameters: Utils.Ui.getSearchParameters(state, component.uiKey)
  }
}

export default connect(select, null, null, {forwardRef: true})(FormDefinitionTable)
