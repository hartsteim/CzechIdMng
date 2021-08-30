# TextField component

Input text component. Extended from AbstractFormComponent.

## Parameters

All parameters from AbstractFormComponent are supported. Added parameters:

| Parameter | Type | Description | Default  |
| --- | :--- | :--- | :--- |
| type  | string   | html input type | text |
| placeholder  | string   | Short description for input.|  |
| min  | number   | Minimal number string characters for intput |  |
| max  | number   | Maximal number string characters for input  |  |
| confidential  | bool   | Confidential text field - if it is filled, then shows asterix only and supports to add new value. If new value is not given, then returns undefined value (this is used for preserving previous value).  | false  |
| warnIfTrimmable | bool | Enables/disables warning that there are any leading/trailing white-spaces in the input. | true |
| pwdAutocomplete | bool | Designed for turn off password prefilling. Uses workaround for turn off autocomplete for password input (false). This will works maybe only in the Chrome. | true |
| onKeyPress | func | onKeyPress Callback | ||

## Usage

```html
<TextField ref="email"
           label="Email"
           placeholder="User email"
           hidden={false}
           validation={Joi.string().email()}
           min={2}
           max={100}
           warnIfTrimmable
 />
```
