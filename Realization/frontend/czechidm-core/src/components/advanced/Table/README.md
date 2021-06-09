# AdvancedTable Component

Encapsulates all features from BasicTable component. All BasicTable parameters are supported. Added parameters:

| Parameter | Type | Description | Default  |
| --- | :--- | :--- | :--- |
| manager | object.isRequired | EntityManager subclass, which provides data fetching | |
| uiKey | string  | optional table identifier - it's used as key in store  | if isn't filled, then manager.getEntityType() is used |
| pagination | bool | If pagination is shown | true |
| onRowClick  | func  | Callback that is called when a row is clicked |  |
| onRowDoubleClick  | func  | Callback that is called when a row is double clicked. | |
| onReload  | func  | Callback that is called table is reload (e.g. when refresh button is clicked and data are loaded). | |
| defaultSearchParameters | object | "Default filter" - its useful for default sorting etc. ||
| forceSearchParameters | object | "Hard filter" - sometimes is useful show just some data (e.q. data filtered by logged user) |   |
| rowClass | oneOfType([string,func]) | ccs class added for row ||
| filter | element | Filter definition ||
| filterOpened | bool | If filter is opened by default | false |
| filterOpen | func | External filter open function. If false is returned, internal filterOpened is not set. |  |
| filterCollapsible | bool | If filter can be collapsed |  |
| actions | arrayOf(object) | Bulk actions. Ignored, if manager supports backend bulk actions. e.g. { value: 'activate', niceLabel: this.i18n('content.identities.action.activate.action'), action: this.onActivate.bind(this) } |  |
| buttons | arrayOf(element) | Buttons are shown on the right of toogle filter button | |
| showId | bool | Shows column with id. Default is id shown in Development stage. | true on development, false otherwise |
| showFilter | bool | Shows filter. | true |
| showPageSize | bool | Shows page size. | true |
| showToolbar | bool | Shows toolbar. | true |
| showRefreshButton | bool | Shows refresh button. | true |
| className | string | Css | |
| header | oneOfType([string, element]) | Table header |  |
| hover | bool | Activate table hover (highligth selected row) | true |
| prohibitedActions | arrayOf(string) | Prohibited actions. Defines array an keys of a bulk actions, that shouldn't be visible in this table. | [] |
| afterBulkAction | func | Callback after bulk action ends - called only if LRT detail is shown till end. Return 'false' in your callback, when standard table reload is not needed after end. | |
| quickAccessButtonCount | number | Count of quick access buttons for bulk actions in tables - the first count of bulk actions will be shown as button - next action will be rendered in drop down select box. Bulk action icon is required for quick access button - action without icon will be rendered in select box. Bulk action can enforce showing in quick access button (by bulk action configuration). | by BE configuration property ``idm.pub.app.show.table.quickAccessButton.count=5`` |
| draggable | bool | DnD support - table will not be orderable, pagination support will not be available. | false |
| onDraggableStop | bool | Callback after dragable ends. Available parameters:  data - table data, startIndex - dragged row index (start from 0),  differenceIndex - index difference (+ down, - up). | Default implementation based on **entity.seq** field and **service.patch** method is provided. |
| showDraggable | func | Show dragable column for change records order. Available parameters:  searchParameters - currently set filter, entities - rendered entities, total - count of all entites fit given filter  | Default implementation based on set **filter** and rendered entities is provided. |

# AdvancedColumn Component

Header text is automatically resolved by entity and column property. Advanced column supports different data types defined by face property.

| Parameter | Type | Description | Default  |
| --- | :--- | :--- | :--- |
| property | string.isRequired | Json property name. Nested properties can be used e.g. `identityManager.name` | |
| sort | bool | Column supports sorting | false |
| sortProperty | string | Property for sort can be different than rendering property - mainly for sorting by referenced sub entity | property |
| width | oneOfType([string,number]) | Pixel or percent width of table. If number is given, then pixels is used. | |
| face | oneOf(['text','date', 'datetime', 'bool']) | Data type | 'text' |

# AdvancedColumnLink Component

All parameters from AdvancedColumn are supported. Added parameters:

| Parameter | Type | Description | Default  |
| --- | :--- | :--- | :--- |
| to | string.isRequired  | React router links `to`. Parameters can be used and theirs value is propagated from data[rowIndex].property | |
| target | string  | optional entity property could be used as `_target` property in `to` property above.  | | |
| access | string  | link could be accessed, if current user has access to target agenda. Otherwise propertyValue without link is rendered.  | | |


## Usage
```javascript


import React from 'react';
import Helmet from 'react-helmet';
//
import * as Basic from '../components/basic';
import * as Advanced from '../../../components/advanced';
import { IdentityManager } from '../../../redux/data';

const identityManager = new IdentityManager();

class Team extends Basic.AbstractContent {

  constructor(props, context) {
    super(props, context);
  }

  componentDidMount() {
    this.selectNavigationItem('team');
  }

  onRowClick(event, rowIndex, data) {
    console.log('onClick', rowIndex, data, event);
  }

  onRowDoubleClick(event, rowIndex, data) {
    console.log('onRowDoubleClick', rowIndex, data, event);
    // redirect to profile
    const identityIdentifier = data[rowIndex].username;
    this.context.history.push('/identity/' + identityIdentifier + '/profile');
  }

  useFilter(event) {
    if (event) {
      event.preventDefault();
    }
    this.refs.table.useFilterForm(this.refs.filterForm);
  }

  cancelFilter(event) {
    if (event) {
      event.preventDefault();
    }
    this.refs.table.cancelFilter(this.refs.filterForm);
  }

  render() {
    const { identities, total, showLoading, searchParameters} = this.props;
    return (
      <div>
        <Helmet title={this.i18n('navigation.menu.subordinates.label')} />
        <Basic.Panel>
          <Basic.PanelHeader text={this.i18n('navigation.menu.subordinates.label')} help="#kotva"/>
          <Advanced.Table
            ref="table"
            uiKey="identity-table"
            manager={identityManager}
            onRowClick={this.onRowClick.bind(this)}
            onRowDoubleClick={this.onRowDoubleClick.bind(this)}
            rowClass={({rowIndex, data}) => { return data[rowIndex]['disabled'] ? 'disabled' : ''}}
            filter={
              <Advanced.Filter onSubmit={this.useFilter.bind(this)}>
                <Basic.AbstractForm ref="filterForm">
                  <Basic.Row className="last">
                    <Basic.Col lg={ 4 }>
                      <Advanced.Filter.TextField
                        ref="filterCreatedAtFrom"
                        field="createdAt"
                        placeholder={this.i18n('filter.createdAtFrom.placeholder')}/>
                    </Basic.Col>
                    <Basic.Col lg={ 4 }>
                      <Advanced.Filter.TextField
                        ref="filterCreatedAtTill"
                        field="createdAt"
                        placeholder={this.i18n('filter.createdAtTill.placeholder')}/>
                    </Basic.Col>
                    <Basic.Col lg={ 4 } className="text-right">
                      <Advanced.Filter.FilterButtons cancelFilter={this.cancelFilter.bind(this)}/>
                    </Basic.Col>
                  </Basic.Row>
                </Basic.AbstractForm>
              </Advanced.Filter>
            }
            filterOpened={true}
            filterCollapsible={true}
            showRowSelection={true}
            actions={
              [
                { value: 'remove', niceLabel: this.i18n('content.identities.action.remove.action'), action: () => alert('not implemented'), disabled: true },
                { value: 'activate', niceLabel: this.i18n('content.identities.action.activate.action'), action: () => alert('not implemented') }
              ]
            }
            buttons={
              [
                <Basic.Button type="submit" className="btn-xs" onClick={() => alert('not implemented')} rendered={true}>
                  <Basic.Icon type="fa" icon="user-plus"/>
                  {this.i18n('content.identity.create.button.add')}
                </Basic.Button>
              ]
            }>
            <Advanced.ColumnLink to="/identity/:username/profile" property="username" width="20%" sort={true} face="text"/>
            <Advanced.Column property="lastName" width="15%" sort={true} face="text" />
            <Advanced.Column property="firstName" width="15%" face="text" />
            <Advanced.Column property="email" width="15%" face="text" />
            <Basic.Column
              header={this.i18n('entity.Identity.description')}
              cell={<Basic.TextCell property="description" maxLength={ 30 }/>}/>
            <Advanced.Column property="createdAt" width="10%" face="date" />
          </Advanced.Table>
        </Basic.Panel>
      </div>
    );
  }
}

Team.propTypes = {
}
Team.defaultProps = {
}

export default Team;
```
