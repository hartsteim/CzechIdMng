import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import Joi from 'joi';
//
import AbstractFormComponent from '../AbstractFormComponent/AbstractFormComponent';
import Tooltip from '../Tooltip/Tooltip';

/**
 * Form label decorator.
 *
 * @author Vít Švanda
 * @author Radek Tomiška
 */
class LabelWrapper extends AbstractFormComponent {

  getRequiredValidationSchema() {
    return Joi.string().required();
  }

  /**
   * Focus input field
   */
  focus() {
    // this.refs.input.focus();
  }

  onChange() {
    // super.onChange(event);
  }

  getBody(feedback) {
    const { labelSpan, label, componentSpan, required, rendered } = this.props;
    //
    if (!rendered) {
      return null;
    }
    //
    const labelClassName = classNames(labelSpan, 'control-label');
    let showAsterix = false;
    if (required && !this.state.value) {
      showAsterix = true;
    }
    const title = this.getValidationResult() != null ? this.getValidationResult().message : null;
    const _label = [];
    if (label) {
      _label.push(label);
    }
    if (_label.length > 0 && required) {
      _label.push(' *');
    }
    //
    return (
      <div className={
        classNames(
          { 'has-feedback': feedback }
        )
      }>
        {
          _label.length === 0
          ||
          <label
            className={ labelClassName }>
            { _label }
            { this.renderHelpIcon() }
          </label>
        }
        <div className={componentSpan}>
          <Tooltip ref="popover" placement="right" value={ title }>
            <span>
              { this.props.children }
              {
                feedback
                ||
                showAsterix
                ?
                <span className="form-control-feedback" style={{color: 'red', zIndex: 0}}>*</span>
                :
                ''
              }
            </span>
          </Tooltip>
          { _label.length === 0 ? this.renderHelpIcon() : null }
          { this.renderHelpBlock() }
        </div>
      </div>
    );
  }
}

LabelWrapper.propTypes = {
  ...AbstractFormComponent.propTypes,
  type: PropTypes.string,
  placeholder: PropTypes.string,
  help: PropTypes.string
};

LabelWrapper.defaultProps = {
  ...AbstractFormComponent.defaultProps,
  type: 'text'
};

export default LabelWrapper;
