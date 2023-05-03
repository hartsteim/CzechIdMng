import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import Joi from 'joi';
import OutlinedInput from '@material-ui/core/OutlinedInput';
import InputLabel from '@material-ui/core/InputLabel';
import InputAdornment from '@material-ui/core/InputAdornment';
import FormControl from '@material-ui/core/FormControl';
import IconButton from '@material-ui/core/IconButton';
import Visibility from '@material-ui/icons/Visibility';
import VisibilityOff from '@material-ui/icons/VisibilityOff';
import Edit from '@material-ui/icons/Edit';
import ReportProblemOutlinedIcon from '@material-ui/icons/ReportProblemOutlined';
//
import AbstractFormComponent from '../AbstractFormComponent/AbstractFormComponent';
import Tooltip from '../Tooltip/Tooltip';
import HelpIcon from '../HelpIcon/HelpIcon';

const CONFIDENTIAL_VALUE = '*****';

/**
 * Basic input component.
 *
 * @author Vít Švanda
 * @author Radek Tomiška
 * @author Ondrej Husnik
 */
class BasicTextField extends AbstractFormComponent {

  constructor(props) {
    super(props);
    this.state = {
      ...this.state,
      confidentialState: {
        showInput: false
      },
      inputType: props.type,
      isTrimmableWarning: this.isTrimmable(props.value)
    };
    //
    this.inputRef = React.createRef();
  }

  UNSAFE_componentWillReceiveProps(nextProps) {
    super.UNSAFE_componentWillReceiveProps(nextProps);
    //
    if (nextProps.type !== this.props.type) {
      this.setState({
        inputType: nextProps.type
      });
    }
  }

  getComponentKey() {
    return 'component.basic.TextField';
  }

  getValidationDefinition(required) {
    const { min, max } = this.props;
    let validation = super.getValidationDefinition(min ? true : required);

    if (min && max) {
      validation = validation.concat(Joi.string().min(min).max(max));
    } else if (min) {
      validation = validation.concat(Joi.string().min(min));
    } else if (max) {
      if (!required) {
        // if set only max is necessary to set allow null and empty string
        validation = validation.concat(Joi.string().max(max).allow(null).allow(''));
      } else {
        // if set prop required it must not be set allow null or empty string
        validation = validation.concat(Joi.string().max(max));
      }
    }

    return validation;
  }

  getRequiredValidationSchema(validation = null) {
    const { type } = this.props;
    // join validation was given
    if (type === 'number' || (validation && validation._type === 'number')) {
      return Joi.number().required();
    }
    return Joi.string().required();
  }

  /**
   * Returns soft validation result invoking warning only
   * @return {validationResult object} Object containing setting of validationResult
   */
  softValidationResult() {
    const {type, warnIfTrimmable} = this.props;
    const {isTrimmableWarning} = this.state;

    // ommits password fields from validation
    if (type !== 'password' && warnIfTrimmable && isTrimmableWarning) {
      return {
        status: 'warning',
        class: 'has-warning has-feedback',
        isValid: true,
        message: this.i18n('validationError.string.isTrimmable')
      };
    }
    return null;
  }

  /**
   * Focus input field.
   */
  focus() {
    if (this.inputRef.current) {
      this.inputRef.current.focus();
    }
  }

  onChange(event) {
    super.onChange(event);
    if (this.refs.tooltip) {
      this.refs.tooltip.show();
    }
    this.setState({
      confidentialState: {
        showInput: true
      }
    });
    if (this.props.warnIfTrimmable) {
      const isTrimmableWarning = this.isTrimmable(event.target.value);
      this.setState({isTrimmableWarning});
    }
  }

  /**
   * Show / hide confidential. Call after save form.
   *
   * @param  {bool} showInput
   */
  openConfidential(showInput) {
    this.setState({
      value: CONFIDENTIAL_VALUE,
      confidentialState: {
        showInput
      }
    });
  }

  /**
   * Show / hide input istead confidential wrapper
   *
   * @param  {bool} showInput
   */
  toogleConfidentialState(showInput, event) {
    if (event) {
      event.preventDefault();
    }
    if (!this._showConfidentialWrapper()) {
      return;
    }
    //
    this.setState({
      value: null,
      confidentialState: {
        showInput
      }
    }, () => {
      this.focus();
    });
  }

  toogleInputType(inputType, event) {
    if (event) {
      event.preventDefault();
    }
    this.setState({
      inputType
    });
  }

  /**
   * Returns filled value. Depends on confidential property
   *
   * @return {string} filled value or undefined, if confidential value is not edited
   */
  getValue() {
    const { confidential } = this.props;
    const { confidentialState } = this.state;
    //
    if (confidential) {
      // preserve previous value
      if (!confidentialState.showInput) {
        return undefined;
      }
      return super.getValue() || ''; // we need to know, when clear confidential value in BE
    }
    // return filled value
    return super.getValue();
  }

  /**
   * Clears filled values
   */
  clearValue() {
    this.setState({ value: null }, () => { this.validate(); });
  }

  /**
   *  Sets value and calls validations
   */
  setValue(value, cb) {
    this.setState({
      isTrimmableWarning: this.isTrimmable(value),
      value
    }, this.validate.bind(this, false, cb));
  }

  /**
   * Return true, when confidential wrapper should be shown
   *
   * @return {bool}
   */
  _showConfidentialWrapper() {
    const { required, confidential } = this.props;
    const { value, confidentialState, disabled, readOnly } = this.state;
    return confidential && !confidentialState.showInput && (!required || value) && !disabled && !readOnly;
  }

  getBody(feedback) {
    const {
      type,
      label,
      placeholder,
      style,
      required: requiredProp,
      pwdAutocomplete,
      onKeyPress,
      fullWidth,
      size,
      multiline,
      rows,
      help,
      classes,
      hidden
    } = this.props;
    const { inputType, value, disabled, readOnly, autoFocus } = this.state;
    const required = requiredProp && !hidden;
    //
    const className = classNames(
      { confidential: this._showConfidentialWrapper() },
      { 'Mui-disabled': this._showConfidentialWrapper() }
    );
    //
    // value and readonly properties depends on confidential wrapper
    let _value = '';
    if (value !== undefined && value !== null) {
      _value = value;
    }
    if (this._showConfidentialWrapper()) {
      if (value) {
        _value = CONFIDENTIAL_VALUE; // asterix will be shown, when value is filled
      } else {
        _value = '';
      }
    }
    const endAdornment = [];
    //
    if (feedback) {
      endAdornment.push(
        <ReportProblemOutlinedIcon color="error"/>
      );
    }
    if (this._showConfidentialWrapper()) {
      endAdornment.push(
        <IconButton
          aria-label="toggle password visibility"
          onClick={ this.toogleConfidentialState.bind(this, true) }
          title={ this.i18n('confidential.edit') }
          tabIndex={ -1 }>
          <Edit />
        </IconButton>
      );
    } else if (type === 'password' && !readOnly) {
      endAdornment.push(
        <IconButton
          aria-label="toggle password visibility"
          onClick={ this.toogleInputType.bind(this, inputType === 'password' ? 'text' : 'password') }
          title={ inputType === 'password' ? this.i18n('label.show') : this.i18n('label.hide') }
          tabIndex={ -1 }>
          { inputType !== 'password' ? <Visibility /> : <VisibilityOff />}
        </IconButton>
      );
    }
    if (help) {
      endAdornment.push(
        <HelpIcon content={ help } tabIndex={ -1 }/>
      );
    }
    //
    const _label = !label ? placeholder : label;
    let _title = this.getTitle();
    if (_label === _title) {
      _title = null;
    }
    //
    return (
      <div
        className="basic-form-component"
        style={{ whiteSpace: 'nowrap' }}>
        <Tooltip ref="tooltip" placement={ this.getTitlePlacement() } value={ _title }>
          <span>
            <FormControl
              variant="outlined"
              fullWidth={ fullWidth }
              size={ size }
              error={ !!feedback }
              disabled={ readOnly || disabled }
              required={ required }>
              <InputLabel required={ required }>
                { _label }
              </InputLabel>
              <OutlinedInput
                inputRef={ this.inputRef }
                autoFocus={ autoFocus }
                required={ required }
                type={ inputType }
                classes={classes}
                autoComplete={ !pwdAutocomplete ? 'new-password' : null }
                className={ className }
                label={
                  <span>
                    { !label ? placeholder : label }
                    { required ? ' *' : '' }
                  </span>
                }
                placeholder={ !label || label === placeholder ? null : placeholder }
                onChange={ this.onChange.bind(this) }
                value={ _value }
                style={ style }
                onKeyPress={ onKeyPress }
                multiline={ multiline }
                rows={ rows }
                endAdornment={
                  endAdornment.length === 0
                  ?
                  null
                  :
                  <InputAdornment position="end">
                    { endAdornment }
                  </InputAdornment>
                }
                onClick={() => {
                  if (this._showConfidentialWrapper()) {
                    this.toogleConfidentialState(true);
                  }
                }}
              />
            </FormControl>
          </span>
        </Tooltip>
        { this.renderHelpBlock() }
      </div>
    );
  }
}

BasicTextField.propTypes = {
  ...AbstractFormComponent.propTypes,
  type: PropTypes.string,
  placeholder: PropTypes.string,
  help: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.object
  ]),
  min: PropTypes.number,
  max: PropTypes.number,
  /**
   * Confidential text field - if it is filled, then shows asterix only and supports to add new value
   */
  confidential: PropTypes.bool,
  warnIfTrimmable: PropTypes.bool,
  /**
   * Uses workaround for turn off autocomplete for password input (false). This will works maybe only in the Chrome.
   * Change ref of a password input doesn't work, because Chrome prefill any input with password type and !ANY! previous input.
   */
  pwdAutocomplete: PropTypes.bool,
  /**
   * onKeyPress Callback
   *
   * @since 11.2.0
   */
  onKeyPress: PropTypes.func
};

BasicTextField.defaultProps = {
  ...AbstractFormComponent.defaultProps,
  type: 'text',
  confidential: false,
  warnIfTrimmable: true,
  pwdAutocomplete: true,
  size: 'small',
  fullWidth: true,
  multiline: false
};

export default BasicTextField;
