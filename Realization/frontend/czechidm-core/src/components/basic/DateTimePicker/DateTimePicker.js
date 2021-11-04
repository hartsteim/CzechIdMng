import React from 'react';
import ReactDOM from 'react-dom';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import Joi from 'joi';
import moment from 'moment';
import Datetime from 'react-datetime';
import $ from 'jquery';
//
import { withStyles } from '@material-ui/core/styles';
//
import LocalizationService from '../../../services/LocalizationService';
import { ConfigurationManager } from '../../../redux';
import AbstractFormComponent from '../AbstractFormComponent/AbstractFormComponent';
import FormComponentLabel from '../AbstractFormComponent/FormComponentLabel';
import Button from '../Button/Button';
import Tooltip from '../Tooltip/Tooltip';

const INVALID_DATE = 'Invalid date';

const styles = theme => ({
  root: {
    '& .rdtPicker': {
      borderColor: theme.palette.divider,
      '& td.rdtActive': {
        backgroundColor: theme.palette.secondary.main,
        color: theme.palette.secondary.contrastText
      },
      '& td.rdtActive:hover': {
        backgroundColor: theme.palette.secondary.main,
        color: theme.palette.secondary.contrastText
      }
    },
    '& .form-control': {
      borderRadius: theme.shape.borderRadius,
      borderColor: theme.palette.type === 'light'
        ? 'rgba(0, 0, 0, 0.23)'
        : 'rgba(255, 255, 255, 0.23)', // ~ hardcoded somewhere in material text field
      '&:hover': {
        borderColor: theme.palette.text.primary
      },
      '&:focus': {
        boxShadow: 'none',
        borderColor: theme.palette.primary.main,
        borderWidth: 2,
        outline: 0,
        paddingLeft: 11,
        paddingRight: 11
      }
    },
    '&.has-error': {
      '& .form-control': {
        borderColor: `${ theme.palette.error.main } !important`
      }
    }
  },
  input: {
    '&.form-control': {
      borderRadius: theme.shape.borderRadius,
      borderColor: theme.palette.type === 'light'
        ? 'rgba(0, 0, 0, 0.23)'
        : 'rgba(255, 255, 255, 0.23)', // ~ hardcoded somewhere in material text field
      '&:focus': {
        boxShadow: 'none'
      },
      '&.has-error': {
        borderColor: `${ theme.palette.error.main } !important`
      }
    }
  }
});

/**
 * Wrapped react-datetime component
 *
 * @author Vít Švanda
 * @author Radek Tomiška
 */
export class DateTimePicker extends AbstractFormComponent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      positionUp: false
    };
    //
    this.containerRef = React.createRef();
  }

  getRequiredValidationSchema() {
    return Joi.any().required();
  }

  componentDidMount() {
    super.componentDidMount();
    this.setValue(this.props.value);
  }

  UNSAFE_componentWillReceiveProps(nextProps) {
    super.UNSAFE_componentWillReceiveProps(nextProps);
    if (nextProps.value && this.props.value && nextProps.value !== this.props.value) {
      this.setValue(nextProps.value);
    }
  }

  /**
   * Focus input field
   */
  focus() {
    if (this.refs.input) {
      // FIXME: react-datetime focus() is not available now
    }
  }

  getFormat() {
    const { mode, dateFormat, timeFormat } = this.props;
    //
    if (!mode || mode === 'datetime') {
      return `${ this._getDateFormat(dateFormat) } ${ this._getTimeFormat(timeFormat) }`;
    }
    if (mode === 'date') {
      return this._getDateFormat(dateFormat);
    }
    if (mode === 'time') {
      return this._getTimeFormat(timeFormat);
    }
    return undefined;
  }

  _getDateFormat(dateFormat) {
    return dateFormat || this.i18n('format.date', { defaultValue: 'DD.MM.YYYY'});
  }

  _getTimeFormat(timeFormat) {
    return timeFormat || this.i18n('format.time', { defaultValue: 'HH:mm' });
  }

  setValue(value, cb = null) {
    const dateTime = this._format(value);
    if (this.refs.input && !dateTime) {
      this.refs.input.setState({ inputValue: '', value: null }, cb); // we need to set empty string, null does not work
    }
    this.setState({ value: dateTime }, () => {
      this.validate(false);
      if (cb) {
        cb();
      }
    });
  }

  validate(showValidationError, cb = null) {
    const { required, isValidDate, mode, validate, maxDate, minDate } = this.props;
    const { value } = this.state;
    const showValidations = showValidationError != null ? showValidationError : true;
    //
    if (this.state.validation) {
      let result = true;
      let key;
      const params = {};
      if (required) {
        result = value !== null;
        key = 'string.base';
      }
      if (result && value) {
        const iso8601Value = moment(value, this.getFormat(), true);
        if (iso8601Value && !iso8601Value.isValid()) {
          result = false;
          key = 'date.unvalid';
        } else if (isValidDate && !isValidDate(value)) {
          result = false;
          key = 'date.unvalid';
        } else {
          // additional validations
          if (minDate) {
            let _minDate = moment(minDate, this.getFormat(), true);
            if (mode === 'date') {
              _minDate = _minDate.startOf('day');
            }
            if (iso8601Value.isBefore(_minDate)) {
              const validationResult = {
                error: {
                  details: [
                    {
                      type: 'date.min',
                      context: {
                        limit: _minDate.format(this.getFormat())
                      }
                    }
                  ]
                }
              };
              this.setValidationResult(validationResult, showValidations, cb);
              return false;
            }
          }
          if (maxDate) {
            let _maxDate = moment(maxDate, this.getFormat(), true);
            if (mode === 'date') {
              _maxDate = _maxDate.startOf('day');
            }
            if (iso8601Value.isAfter(_maxDate)) {
              const validationResult = {
                error: {
                  details: [
                    {
                      type: 'date.max',
                      context: {
                        limit: _maxDate.format(this.getFormat())
                      }
                    }
                  ]
                }
              };
              this.setValidationResult(validationResult, showValidations, cb);
              return false;
            }
          }
          if (validate) {
            const validationResult = validate(iso8601Value, null);
            if (validationResult && validationResult.error) {
              // show validation error on UI
              this.setValidationResult(validationResult, showValidations, cb);
              return false;
            }
          }
        }
      }
      //
      if (!result) {
        const message = this._localizationValidation(key, params);
        this.setState({
          validationResult: {
            status: 'error',
            class: 'has-error has-feedback',
            isValid: false,
            message
          },
          showValidationError: showValidations
        });
      } else {
        this.setState({
          validationResult: {
            status: null,
            class: '',
            isValid: true,
            message: null,
            showValidationError: true
          },
          showValidationError: showValidations
        });
      }
      // show validation error on UI
      return false;
    }
    //
    return true;
  }

  onChange(value) {
    if (value && value._isAMomentObject) {
      value = moment(value, this.getFormat());
    }
    let result = true;
    if (this.props.onChange) {
      result = this.props.onChange(value);
    }
    // if onChange listener returns false, then we can end
    if (result === false) {
      return;
    }
    this.setState({
      value
    }, () => {
      this.validate();
    });
  }

  /**
   * Resolve position of the component dialog.
   * If is component too down, then will be dialog open above the component.
   */
  resolvePosition(callback) {
    if (!this.refs.input) {
      return;
    }

    // TODO: Using of findDOMNode is not recommended. Find a another solution.
    /* eslint-disable react/no-find-dom-node */
    const rect = ReactDOM.findDOMNode(this.refs.input).getBoundingClientRect();
    const pageHeight = document.documentElement.clientHeight;
    const positionY = rect.y ? rect.y : rect.top;
    const heightOfDialog = 250;

    // Should be dialog show above the component?
    const positionUp = (positionY + heightOfDialog) > pageHeight;
    this.setState({
      positionUp
    }, () => {
      if (callback) {
        callback();
      }
      //
      const container = $(this.containerRef.current);
      const isModal = container.closest('.basic-modal-scroll-paper').length > 0;
      const picker = container.find('.rdtPicker');
      if (picker) {
        const style = {
          backgroundColor: ConfigurationManager.getApplicationTheme(this.context.store.getState()).palette.background.paper
        };
        if (isModal) {
          style.top = positionUp ? rect.top - heightOfDialog : rect.bottom;
          style.bottom = 'auto';
          style.position = 'fixed';
        }
        picker.css(style);
      }
    });
  }

  getValue() {
    const { mode } = this.props;
    const { value } = this.state;
    //
    if (value === INVALID_DATE || !value) {
      return null;
    }
    if (!mode || mode === 'datetime') {
      return moment(value, this.getFormat()).toISOString(); // iso 8601
    }
    if (mode === 'date') {
      return moment(value, this.getFormat()).format('YYYY-MM-DD'); // iso 8601
    }
    // time
    return moment(value, this.getFormat()).format('HH:mm'); // iso 8601
  }

  _format(iso8601Value) {
    if (!iso8601Value) {
      return null;
    }
    if (typeof iso8601Value === 'string') {
      // TODO: deprecated by next moment version
      return moment(iso8601Value).format(this.getFormat());
    }
    return moment(iso8601Value).format(this.getFormat());
  }

  _clear() {
    this.refs.input.setState({ inputValue: null }); // we need to set empty string, null does not work
    this.onChange(null);
  }

  _openDialog() {
    const dateTimePicker = this.refs.input;
    this.resolvePosition(() => { dateTimePicker.setState({ open: true }); });
  }

  getBody(feedback) {
    const {
      mode,
      labelSpan,
      label,
      componentSpan,
      placeholder,
      style,
      locale,
      dateFormat,
      timeFormat,
      isValidDate,
      required,
      classes
    } = this.props;
    const { readOnly, disabled, value, positionUp } = this.state;
    //
    // default prop values - we need initialized LocalizationService
    const _locale = locale || LocalizationService.getCurrentLanguage();
    const _dateFormat = this._getDateFormat(dateFormat);
    const _timeFormat = this._getTimeFormat(timeFormat);
    //
    const labelClassName = classNames(labelSpan);
    const inputClassName = classNames(
      { rdtPickerOpenUpwards: positionUp },
      { 'has-error': !!feedback },
      classes ? classes.root : null
    );
    const _label = [];
    if (label) {
      _label.push(label);
    } else if (placeholder) {
      _label.push(placeholder);
    }
    if (_label.length > 0 && required) {
      _label.push(' *');
    }
    // VS: I added value attribute to Datetime component. External set value (in setValue method) now set only empty string if value is null.
    // Without value attribute is in some case (detail of contract) value not show after first render.
    return (
      <div className={
        classNames(
          'basic-form-component',
          { 'has-feedback': !!feedback },
          { disabled: disabled || readOnly }
        )
      }>
        <FormComponentLabel
          className={ labelClassName }
          label={ _label }
          readOnly={disabled || readOnly}
          helpIcon={ this.renderHelpIcon() }/>
        <div className={ componentSpan }>
          <Tooltip ref="popover" placement={ this.getTitlePlacement() } value={ this.getTitle() }>
            <div
              ref={ this.containerRef }
              className="basic-date-time-picker">
              {
                (disabled || readOnly)
                ?
                <input
                  type="text"
                  value={ value && value._isAMomentObject ? this._format(value) : value }
                  readOnly
                  className={
                    classNames(
                      'form-control',
                      { 'has-error': !!feedback },
                      classes ? classes.input : null
                    )
                  }
                  style={ style }/>
                :
                <Datetime
                  ref="input"
                  value={value}
                  onChange={this.onChange}
                  disabled={disabled}
                  readOnly={readOnly}
                  className={ inputClassName }
                  closeOnSelect
                  onFocus={this.resolvePosition.bind(this, null)}
                  locale={_locale === 'cs' ? 'cs' : 'en'}
                  dateFormat={mode === 'time' ? false : _dateFormat}
                  timeFormat={mode === 'date' ? false : _timeFormat}
                  inputProps={{
                    title: (this.getValidationResult() != null ? this.getValidationResult().message : ''),
                    placeholder,
                    style: {
                      zIndex: 0
                    }
                  }}
                  isValidDate={isValidDate}/>
              }
              <Button
                type="button"
                buttonSize="xs"
                level="default"
                disabled={disabled || readOnly}
                style={{ marginTop: 0 }}
                onClick={this._openDialog.bind(this)}
                icon={ feedback ? 'fa:calendar' : 'fa:calendar' }/>
              <Button
                type="button"
                buttonSize="xs"
                level="default"
                disabled={disabled || readOnly}
                style={{ marginTop: 0 }}
                icon="fa:remove"
                onClick={this._clear.bind(this)}/>
            </div>
          </Tooltip>
          { _label.length === 0 ? this.renderHelpIcon() : null }
          { this.renderHelpBlock() }
        </div>
      </div>
    );
  }
}

DateTimePicker.propTypes = {
  ...AbstractFormComponent.propTypes,
  /**
   *  Defined mode of component see @DateTimePicker. Use 'datetime' for DateTime columns, timezone is ignored for LocalDate columns.
   */
  mode: PropTypes.oneOf(['date', 'time', 'datetime']),
  locale: PropTypes.oneOf(['cs', 'en']), // TODO: supports other locales needs import
  dateFormat: PropTypes.string,
  timeFormat: PropTypes.string,
  /**
   * Define the dates that can be selected. The function receives (currentDate, selectedDate) and shall return a true or false whether the currentDate is valid or not.
   *
   * @deprecated @since 11.0.0 use standard #validate method => validation message can be defined
   */
  isValidDate: PropTypes.func,
  /**
   * Minimum valid date (use moment as value).
   *
   * @since 11.0.0
   */
  minDate: PropTypes.object,
  /**
   * Maximum valid date (use moment as value).
   *
   * @since 11.0.0
   */
  maxDate: PropTypes.object
};

const { componentSpan, ...otherDefaultProps} = AbstractFormComponent.defaultProps; // componentSpan override
DateTimePicker.defaultProps = {
  ...otherDefaultProps,
  mode: 'datetime'
};
DateTimePicker.STYLES = styles;

export default withStyles(styles, { withTheme: true })(DateTimePicker);
