import React from 'react';
import PropTypes from 'prop-types';
import classnames from 'classnames';
import _ from 'lodash';
//
import * as Basic from '../../basic';
import * as Utils from '../../../utils';
import { FormAttributeManager } from '../../../redux';
import FormInstance from '../../../domain/FormInstance';
//
const attributeManager = new FormAttributeManager();

/**
 * Content of eav form by given form instance (= form definition + form values).
 *
 * @author Radek Tomiška
 */
export default class EavForm extends Basic.AbstractContextComponent {

  /**
   * Return component identifier, with can be used in localization etc.
   *
   * @return {string} component identifier
   */
  getComponentKey() {
    return 'component.advanced.EavForm';
  }

  /**
   * Returns true, when form is valid, otherwise false
   *
   * @return {Boolean} [description]
   */
  isValid() {
    const { formInstance } = this.props;
    let isAllValid = true;
    let firstInvalidComponent = null;
    formInstance.getAttributes().forEach(attribute => {
      const formComponent = this.refs[attribute.code];
      if (!formComponent) {
        // unsupported persistentType
        return true;
      }
      // we need to call validate method on all component (break is not needed)
      if (!formComponent.isValid()) {
        isAllValid = false;
        if (!firstInvalidComponent) {
          firstInvalidComponent = formComponent;
        }
        return false;
      }
      return true;
    });
    //
    if (firstInvalidComponent) {
      firstInvalidComponent.focus(); // all eav renderers supports focus
    }
    //
    return isAllValid;
  }

  /**
   * Focus form.
   *
   * @param  {string} [attributeCode=null] attribute by given code ~ ref is focused or the first form attribute by default
   * @since 10.7.0
   */
  focus(attributeCode = null) {
    const { formInstance } = this.props;
    let formComponent = null;
    //
    if (attributeCode) {
      formComponent = this.refs[attributeCode];
    } else {
      for (const attribute of formInstance.getAttributes()) {
        formComponent = this.refs[attribute.code];
        if (formComponent) {
          break;
        }
      }
    }
    //
    if (formComponent) {
      formComponent.focus();
      return true;
    }
    // component not found
    return false;
  }

  /**
   * Return form definition used for form instance
   *
   * @return {formDefinition}
   */
  getFormDefinition() {
    const { formInstance } = this.props;
    //
    if (!formInstance) {
      return null;
    }
    return formInstance.getDefinition();
  }

  /**
   * Returns array of filled form values (form value object)
   *
   * @return {arrayOf(formValue)}
   */
  getValues() {
    const { formInstance } = this.props;
    let filledFormValues = [];
    //
    formInstance.getAttributes().forEach(attribute => {
      if (attribute.readonly) {
        // readOnly (~ disabled from our point of view) attributes are not sent to BE
        return true;
      }
      const formComponent = this.refs[attribute.code];
      if (!formComponent) {
        // unsupported persistentType
        return true;
      }
      const values = formComponent.getValues();
      if (values === undefined) {
        // values are not controlled
        return true;
      }
      filledFormValues = filledFormValues.concat(values);
      return true;
    });
    return filledFormValues;
  }

  /**
   * Returns filled values as properties object (ConfigurationMap on BE is preferred)
   *
   * TODO: multiple properties
   *
   * @return {object} [description]
   */
  getProperties() {
    const formInstance = new FormInstance(this.props.formInstance.getDefinition(), this.getValues());
    //
    return formInstance.getProperties();
  }

  getInvalidFormAttributes(validationErrors, code, formInstance) {
    if (!validationErrors) {
      return [];
    }
    //
    return validationErrors
      .filter(attribute => { // by attribute code
        return attribute.attributeCode === code;
      })
      .filter(attribute => { // by owner id
        if (!formInstance || !formInstance.getOwnerId() || !attribute.ownerId) { // backward compatible
          return true;
        }
        //
        return formInstance.getOwnerId() === attribute.ownerId;
      });
  }

  render() {
    const {
      formInstance,
      rendered,
      showLoading,
      readOnly,
      useDefaultValue,
      validationErrors,
      formableManager,
      showAttributes,
      formValidations,
      condensed,
      className
    } = this.props;
    //
    if (!rendered || !formInstance) {
      return null;
    }
    //
    if (showLoading) {
      return (
        <Basic.Loading isStatic showLoading/>
      );
    }
    if (formInstance.getAttributes().size === 0) {
      return (
        <Basic.Alert
          level="info"
          text={ this.i18n('attributes.empty') }
          className="no-margin"
          rendered={ !showAttributes && !condensed }/>
      );
    }
    //
    return (
      <span className={
        classnames(
          {
            'eav-form': true,
            condensed
          },
          className
        )
      }>
        {
          [...formInstance.getAttributes().map(attribute => {
            if (showAttributes && showAttributes.size > 0 && !showAttributes.has(attribute.code) && !showAttributes.has(attribute.id)) {
              return null;
            }
            //
            const values = formInstance.getValues(attribute.code);
            const filledValues = !values ? [] : values.filter(value => !Utils.Ui.isEmpty(value.value));
            if (filledValues.length === 0 && condensed) {
              return null;
            }
            const component = attributeManager.getFormComponent(attribute);
            if (!component) {
              if (condensed) {
                return (
                  <Basic.LabelWrapper label={ attribute.code } >
                    { filledValues.map(value => Utils.Ui.toStringValue(value.value)).join(', ') }
                  </Basic.LabelWrapper>
                );
              }
              return (
                <Basic.LabelWrapper label={attribute.name}>
                  <Basic.Alert
                    level="warning"
                    text={ this.i18n('persistentType.unsupported.title', { name: attribute.persistentType, face: attribute.faceType }) }
                    className="no-margin"/>
                </Basic.LabelWrapper>
              );
            }
            //
            // apply overridden form Validations and setting
            const _finalAttribute = _.merge({}, attribute);
            if (formValidations) {
              try {
                const _overridenAttribute = JSON
                  .parse(formValidations)
                  .find(a => a.id === attribute.id);
                if (_overridenAttribute) {
                  _finalAttribute.readonly = _overridenAttribute.readonly;
                  _finalAttribute.required = _overridenAttribute.required;
                  if (_overridenAttribute.placeholder) {
                    _finalAttribute.placeholder = _overridenAttribute.placeholder;
                  }
                  if (_overridenAttribute.label) {
                    _finalAttribute.name = _overridenAttribute.label;
                  }
                  if (_overridenAttribute.min) {
                    _finalAttribute.min = _overridenAttribute.min;
                  }
                  if (_overridenAttribute.max) {
                    _finalAttribute.max = _overridenAttribute.max;
                  }
                  if (_overridenAttribute.regex) {
                    _finalAttribute.regex = _overridenAttribute.regex;
                  }
                  if (_overridenAttribute.validationMessage) {
                    _finalAttribute.validationMessage = _overridenAttribute.validationMessage;
                  }
                  //
                  // set overridden attribute back into form instance definition => used in validations
                  formInstance.setAttribute(_finalAttribute);
                }
              } catch (syntaxError) {
                // nothing - attribute  will not be overriden
              }
            }
            //
            const FormValueComponent = component.component;
            const ManagerType = component.manager;
            //
            return (
              <FormValueComponent
                ref={ attribute.code }
                uiKey={ `form-attribute-${ attribute.code }` }
                formDefinition={ this.getFormDefinition() }
                attribute={ _finalAttribute }
                values={ values }
                readOnly={ readOnly }
                useDefaultValue={ useDefaultValue }
                manager={ ManagerType ? new ManagerType() : null }
                validationErrors={
                  this.getInvalidFormAttributes(validationErrors || formInstance.validationErrors, attribute.code, formInstance)
                }
                className={ !showAttributes && formInstance.getAttributes().last().id === attribute.id ? 'last' : '' }
                formableManager={ formableManager }
                component={ component }/>
            );
          }).values()]
        }
      </span>
    );
  }
}

EavForm.propTypes = {
  ...Basic.AbstractContextComponent.propTypes,
  /**
   * FormInstance (definition + values)
   */
  formInstance: PropTypes.object,
  /**
   * Manager controlls owners extended attributes, e.g. identityManager, roleManager.
   * Enable additional features, which depends on concrete manager (e.g. download attachment).
   * When manager is not given, features are disabled.
   */
  formableManager: PropTypes.object,
  /**
   * ReadOnly form
   */
  readOnly: PropTypes.bool,
  /**
   * Use default value as filled value
   */
  useDefaultValue: PropTypes.bool,
  /**
   * List of InvalidFormAttributeDto
   */
  validationErrors: PropTypes.arrayOf(PropTypes.object),
  /**
   * Render given attributes only. Render all atributes otherwise.
   */
  showAttributes: PropTypes.arrayOf(PropTypes.string),
  /**
   * Overriden attributes validations and settings
   *
   * @since 12.0.0
   */
  formValidations: PropTypes.arrayOf(PropTypes.object),
  /**
   * Condensed (shorten) form properties - usable in tables. Just filled values without help will be shown.
   */
  condensed: PropTypes.bool
};
EavForm.defaultProps = {
  ...Basic.AbstractContextComponent.defaultProps,
  formInstance: null,
  readOnly: false,
  useDefaultValue: false,
  condensed: false
};
