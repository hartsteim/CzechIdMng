# SelectBox component

Component for search and select item any entity. Extended from AbstractFormComponent.
Component supported single select and multi select mode.

## Requirements

- Autocomplete endpoint (e.q. ``<server>/api/identities/search/autocomplete``) has to exists - underlying find method has to support find by property "text"
- first page of records is returned, if input is empty. Page size can be configured by ``pageSize`` property
- if input is changed, then new search is executed on BE wit "text" property filled - other records can be found.
- when ``useFirst`` is ``true``, then first record is selected automatically - the first record from the first page.

## Parameters

All parameters from AbstractFormComponent are supported. Added parameters:

| Parameter | Type | Description | Default  |
| --- | :--- | :--- | :--- |
| manager  | instanceOf(EntityManager)   | Implementation of manager (extended from EnttityManager) for entity type where we want search - uses manager.getDefaultSearchParameters()  |  |
| forceSearchParameters | object | "Hard filter" - sometimes is useful show just some data (e.q. data filtered by logged user) |   |
| fieldLabel  | string   | Field use for show string representation of item in select box| 'niceLabel' this is automatic added field from service for item |
| multiSelect | bool   | The component is in multi select mode | false |
| value | string or Array of strings (object or Array of objects)  | Value can contains object (object type have to equals with service entity type) or id of entity in string. In multi select mod can be in value Array (string or object) | |
| placeholder  | string   | Short description for input  |  |
| clearable | bool   | Selected options can be cleared | true |
| disableable | bool  | Selected options cannot be selected - Disableable = ``true`` => disabled entity cannot be selected. Set property to ``false`` to enable select disabled entities. | true |
| niceLabel | func   | Function for transform nice label in select box|  |
| returnProperty | oneOfType([string, bool])  | If object is selected, then this property value will be returned. If value is false, then whole object is returned. | 'id' |
| useFirst | bool | Use the first searched value on component is inited, if selected value is empty. | false |
| useFirstIfOne | bool | Use the first searched value if exists only one and if selected value is empty. | true |
| pageSize | number | Search results page size | SearchParameters.getDefaultSize() |
| loadMoreContent | bool | Load next options after reached end of list | true
| optionComponent | func | Option decorator | OptionDecorator |
| valueComponent | func | Value decorator | ValueDecorator |
| emptyOptionLabel | string | Empty value label (text). Use ``false`` to hide emptyOption. | 'emptyOption.label' localization |
| additionalOptions | arrayOf(object) | Additional select box options - extend options with custom behavior. |  ||


## Usage

### Select
```html
<Basic.SelectBox ref="selectComponent"
     label="Select box test"
     manager={identityManager}
     placeholder="Select user ..."
     value="admin"
     validation={Joi.object().required()}/>
```

### Multi select

```html
<Basic.SelectBox
  ref="selectBoxMulti"
  label="Select box multi"
  service={identityService}
  value = {['admin','testCreate11','testCreate2','testCreate3','testCreate4','testCreate5','testCreate6']}
  searchInFields={['lastName', 'name','email']}
  placeholder="Select identity or start writing to search ..."
  multiSelect={true}
  required/>
```

### Custom page size

```html
<Basic.SelectBox
  ref="role"
  placeholder="Select role or type for search ..."
  manager={ roleManager }
  pageSize={ SearchParameters.MAX_SIZE }/>
```
