import PropTypes from 'prop-types';
import React from 'react';
import _ from 'lodash';
//
import * as Basic from '../../../components/basic';
import * as Advanced from '../../../components/advanced';
import { AutomaticRoleAttributeRuleManager, FormAttributeManager, FormDefinitionManager } from '../../../redux';
import AutomaticRoleAttributeRuleTypeEnum from '../../../enums/AutomaticRoleAttributeRuleTypeEnum';
import AutomaticRoleAttributeRuleComparisonEnum from '../../../enums/AutomaticRoleAttributeRuleComparisonEnum';
import ContractStateEnum from '../../../enums/ContractStateEnum';
import AbstractEnum from '../../../enums/AbstractEnum';

/**
 * Constant for get eav attribute for identity contract
 * @type {String}
 */
const CONTRACT_EAV_TYPE = 'eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract';
/**
 * Constatn for get eav attribute for identity
 * @type {String}
 */
const IDENTITY_EAV_TYPE = 'eu.bcvsolutions.idm.core.model.entity.IdmIdentity';
const DEFINITION_TYPE_FILTER = 'definitionType';

const formDefinitionManager = new FormDefinitionManager();

/**
 * Modified ContractAttributeEnum - singular properties
 *
 * TODO: DRY, but how to generalize enum + static methods ...
 *
 * @author Ondrej Kopr
 */
class ContractAttributeEnum extends AbstractEnum {

  static getNiceLabel(key) {
    return super.getNiceLabel(`core:enums.ContractAttributeEnum.${key}`);
  }

  static findKeyBySymbol(sym) {
    return super.findKeyBySymbol(this, sym);
  }

  static findSymbolByKey(key) {
    return super.findSymbolByKey(this, key);
  }

  static getField(key) {
    if (!key) {
      return null;
    }

    const sym = super.findSymbolByKey(this, key);

    switch (sym) {
      case this.POSITION: {
        return 'position';
      }
      case this.EXTERNE: {
        return 'externe';
      }
      case this.MAIN: {
        return 'main';
      }
      case this.DESCRIPTION: {
        return 'description';
      }
      case this.STATE: {
        return 'state';
      }
      default: {
        return null;
      }
    }
  }

  static getEnum(field) {
    if (!field) {
      return null;
    }

    switch (field) {
      case 'position': {
        return this.POSITION;
      }
      case 'externe': {
        return this.EXTERNE;
      }
      case 'main': {
        return this.MAIN;
      }
      case 'description': {
        return this.DESCRIPTION;
      }
      case 'disabled': {
        return this.DISABLED;
      }
      case 'state': {
        return this.STATE;
      }
      default: {
        return null;
      }
    }
  }

  static getLevel(key) {
    if (!key) {
      return null;
    }

    const sym = super.findSymbolByKey(this, key);

    switch (sym) {
      default: {
        return 'default';
      }
    }
  }
}

ContractAttributeEnum.MAIN = Symbol('MAIN');
ContractAttributeEnum.STATE = Symbol('STATE');
ContractAttributeEnum.POSITION = Symbol('POSITION');
ContractAttributeEnum.EXTERNE = Symbol('EXTERNE');
ContractAttributeEnum.DESCRIPTION = Symbol('DESCRIPTION');

/**
 * Modified IdentityAttributeEnum - singular properties
 *
 * TODO: DRY, but how to generalize enum + static methods ...
 *
 * @author Radek Tomiška
 */
class IdentityAttributeEnum extends AbstractEnum {

  static getNiceLabel(key) {
    return super.getNiceLabel(`core:enums.IdentityAttributeEnum.${key}`);
  }

  static getHelpBlockLabel(key) {
    return super.getNiceLabel(`core:enums.IdentityAttributeEnum.helpBlock.${key}`);
  }

  static findKeyBySymbol(sym) {
    return super.findKeyBySymbol(this, sym);
  }

  static findSymbolByKey(key) {
    return super.findSymbolByKey(this, key);
  }

  static getField(key) {
    if (!key) {
      return null;
    }

    const sym = super.findSymbolByKey(this, key);

    switch (sym) {
      case this.USERNAME: {
        return 'username';
      }
      case this.EXTERNAL_CODE: {
        return 'externalCode';
      }
      case this.DISABLED: {
        return 'disabled';
      }
      case this.FIRSTNAME: {
        return 'firstName';
      }
      case this.LASTNAME: {
        return 'lastName';
      }
      case this.EMAIL: {
        return 'email';
      }
      case this.PHONE: {
        return 'phone';
      }
      case this.TITLE_BEFORE: {
        return 'titleBefore';
      }
      case this.TITLE_AFTER: {
        return 'titleAfter';
      }
      case this.DESCRIPTION: {
        return 'description';
      }
      case this.FORM_PROJECTION: {
        return 'formProjection';
      }
      default: {
        return null;
      }
    }
  }

  static getEnum(field) {
    if (!field) {
      return null;
    }

    switch (field) {
      case 'username': {
        return this.USERNAME;
      }
      case 'externalCode': {
        return this.EXTERNAL_CODE;
      }
      case 'disabled': {
        return this.DISABLED;
      }
      case 'firstName': {
        return this.FIRSTNAME;
      }
      case 'lastName': {
        return this.LASTNAME;
      }
      case 'email': {
        return this.EMAIL;
      }
      case 'phone': {
        return this.PHONE;
      }
      case 'titleBefore': {
        return this.TITLE_BEFORE;
      }
      case 'titleAfter': {
        return this.TITLE_AFTER;
      }
      case 'description': {
        return this.DESCRIPTION;
      }
      case 'formProjection': {
        return this.FORM_PROJECTION;
      }
      default: {
        return null;
      }
    }
  }

  static getLevel(key) {
    if (!key) {
      return null;
    }

    const sym = super.findSymbolByKey(this, key);

    switch (sym) {
      default: {
        return 'default';
      }
    }
  }
}

IdentityAttributeEnum.USERNAME = Symbol('USERNAME');
IdentityAttributeEnum.EXTERNAL_CODE = Symbol('EXTERNAL_CODE');
IdentityAttributeEnum.DISABLED = Symbol('DISABLED');
IdentityAttributeEnum.FIRSTNAME = Symbol('FIRSTNAME');
IdentityAttributeEnum.LASTNAME = Symbol('LASTNAME');
IdentityAttributeEnum.EMAIL = Symbol('EMAIL');
IdentityAttributeEnum.PHONE = Symbol('PHONE');
IdentityAttributeEnum.TITLE_BEFORE = Symbol('TITLE_BEFORE');
IdentityAttributeEnum.TITLE_AFTER = Symbol('TITLE_AFTER');
IdentityAttributeEnum.DESCRIPTION = Symbol('DESCRIPTION');
IdentityAttributeEnum.FORM_PROJECTION = Symbol('FORM_PROJECTION');

/**
 * Form attrribute select box.
 *
 * @author Radek Tomiška
 * @since 10.2.0
 */
class AttributeOptionDecorator extends Basic.SelectBox.OptionDecorator {

  renderDescription(entity) {
    if (!entity || !entity._embedded || !entity._embedded.formDefinition) {
      return null;
    }
    //
    return (
      <Basic.Div style={{ color: '#555', fontSize: '0.95em', fontStyle: 'italic' }}>
        { `${ this.i18n('entity.FormDefinition._type') }: ${ formDefinitionManager.getNiceLabel(entity._embedded.formDefinition, false) }` }
      </Basic.Div>
    );
  }
}

/**
 * Detail rules of automatic role attribute
 *
 * @author Ondrej Kopr
 */
export default class AutomaticRoleAttributeRuleDetail extends Basic.AbstractContent {

  constructor(props, context) {
    super(props, context);
    this.manager = new AutomaticRoleAttributeRuleManager();
    this.formAttributeManager = new FormAttributeManager();
    this.state = {
      showLoading: false,
      typeForceSearchParameters: null, // force search parameters for EAV attribute
      // default type, show when create new entity
      type: AutomaticRoleAttributeRuleTypeEnum.findKeyBySymbol(AutomaticRoleAttributeRuleTypeEnum.IDENTITY),
      valueRequired: true, // flag for required field
      formAttribute: null, // instance of form attribute, is used for computed field input
      attributeName: null, // name of identity attribute
      hideValueField: false, // Flag for hide attribute value intput
      incompatibleWithMultiple: false // Flag for check multivalued eavs and comparsion
    };
  }

  getContentKey() {
    return 'content.automaticRoles.attribute';
  }

  componentDidMount() {
    super.componentDidMount();
    //
    const { entity } = this.props;
    this._initForm(entity);
  }

  // @Deprecated - since V10 ... replaced by dynamic key in Route
  // UNSAFE_componentWillReceiveProps(nextProps) {
  //   // check id of old and new entity
  //   if (nextProps.entity.id !== this.props.entity.id || nextProps.entity.attributeName !== this.props.entity.attributeName) {
  //     this._initForm(nextProps.entity);
  //   }
  // }

  getForm() {
    return this.refs.form;
  }

  getValue() {
    return this.refs.value;
  }

  isFormValid() {
    const { hideValueField } = this.state;
    // If valued is hidden (state property: hideValueField) isn't required validate value component
    return this.getForm().isFormValid() && (hideValueField || this.getValue().isValid());
  }

  getCompiledData() {
    const formData = this.getForm().getData();
    let value = this.getValue().getValue();
    if (_.isObject(value)) {
      // eav form value
      value = this._getValueFromEav(value);
    }
    // attribute in backend is String type, we must explicit cast to string
    formData.value = String(value);
    // we must transform attribute name with case sensitive letters
    if (formData.attributeName) {
      if (formData.type === AutomaticRoleAttributeRuleTypeEnum.findKeyBySymbol(AutomaticRoleAttributeRuleTypeEnum.IDENTITY)) {
        let attributeName = IdentityAttributeEnum.getField(IdentityAttributeEnum.findKeyBySymbol(formData.attributeName));
        if (!attributeName) {
          attributeName = IdentityAttributeEnum.getField(formData.attributeName);
        }
        formData.attributeName = attributeName;
      } else {
        let attributeName = ContractAttributeEnum.getField(ContractAttributeEnum.findKeyBySymbol(formData.attributeName));
        if (!attributeName) {
          attributeName = ContractAttributeEnum.getField(formData.attributeName);
        }
        formData.attributeName = attributeName;
      }
    }
    if (formData.type !== AutomaticRoleAttributeRuleTypeEnum.findKeyBySymbol(AutomaticRoleAttributeRuleTypeEnum.IDENTITY_EAV)
     && formData.type !== AutomaticRoleAttributeRuleTypeEnum.findKeyBySymbol(AutomaticRoleAttributeRuleTypeEnum.CONTRACT_EAV)) {
      formData.formAttribute = null;
    }
    const formAttribute = formData.formAttribute;
    if (formAttribute) {
      formData.formAttribute = formAttribute.id;
      if (!formData.attributeName) {
        formData.attributeName = formAttribute.code;
      }
    }
    return formData;
  }

  /**
   * Method for basic initial form
   */
  _initForm(entity) {
    let attributeName = null;
    let hideValueField = false;
    if (entity !== undefined) {
      let formAttribute = null;
      if (!entity.id) {
        entity.type = AutomaticRoleAttributeRuleTypeEnum.findKeyBySymbol(AutomaticRoleAttributeRuleTypeEnum.IDENTITY);
        entity.comparison = AutomaticRoleAttributeRuleComparisonEnum.findKeyBySymbol(AutomaticRoleAttributeRuleComparisonEnum.EQUALS);
        attributeName = IdentityAttributeEnum.findKeyBySymbol(IdentityAttributeEnum.USERNAME);
        entity.attributeName = attributeName;
      } else if (entity.type !== AutomaticRoleAttributeRuleTypeEnum.findKeyBySymbol(AutomaticRoleAttributeRuleTypeEnum.IDENTITY_EAV)
        && entity.type !== AutomaticRoleAttributeRuleTypeEnum.findKeyBySymbol(AutomaticRoleAttributeRuleTypeEnum.CONTRACT_EAV)) {
        if (entity.type === AutomaticRoleAttributeRuleTypeEnum.findKeyBySymbol(AutomaticRoleAttributeRuleTypeEnum.IDENTITY)) {
          entity.attributeName = IdentityAttributeEnum.getEnum(entity.attributeName);
          attributeName = IdentityAttributeEnum.findKeyBySymbol(entity.attributeName);
        } else {
          entity.attributeName = ContractAttributeEnum.getEnum(entity.attributeName);
          attributeName = ContractAttributeEnum.findKeyBySymbol(entity.attributeName);
        }
      } else if (entity._embedded && entity._embedded.formAttribute) {
        // eav is used
        formAttribute = entity._embedded.formAttribute;
      }
      if (entity.comparison === AutomaticRoleAttributeRuleComparisonEnum.findKeyBySymbol(AutomaticRoleAttributeRuleComparisonEnum.IS_EMPTY) ||
        entity.comparison === AutomaticRoleAttributeRuleComparisonEnum.findKeyBySymbol(AutomaticRoleAttributeRuleComparisonEnum.IS_NOT_EMPTY)) {
        hideValueField = true;
      }
      this.setState({
        typeForceSearchParameters: this._getForceSearchParametersForType(entity.type),
        type: entity.type,
        formAttribute,
        entity,
        attributeName,
        hideValueField
      });
      this.refs.type.focus();
    }
  }

  _getForceSearchParametersForType(type) {
    let typeForceSearchParameters = this.formAttributeManager.getDefaultSearchParameters().setFilter('confidential', false);
    if (type === AutomaticRoleAttributeRuleTypeEnum.findKeyBySymbol(AutomaticRoleAttributeRuleTypeEnum.IDENTITY_EAV)) {
      typeForceSearchParameters = typeForceSearchParameters.setFilter(DEFINITION_TYPE_FILTER, IDENTITY_EAV_TYPE);
    } else if (type === AutomaticRoleAttributeRuleTypeEnum.findKeyBySymbol(AutomaticRoleAttributeRuleTypeEnum.CONTRACT_EAV)) {
      typeForceSearchParameters = typeForceSearchParameters.setFilter(DEFINITION_TYPE_FILTER, CONTRACT_EAV_TYPE);
    } else {
      typeForceSearchParameters = null;
    }
    return typeForceSearchParameters;
  }

  /**
   * Get field form EAV, saved eav form value contains
   * value, seq and then value by persistent type for example: dateValue, doubleValue, ...
   */
  _getValueFromEav(eav) {
    for (const field in eav) {
      if (field !== 'value' && field.includes('Value')) {
        return eav[field];
      }
    }
    //
    return null;
  }

  _typeChange(option) {
    let typeForceSearchParameters = null;
    if (option) {
      typeForceSearchParameters = this._getForceSearchParametersForType(option.value);
    }
    //
    const { entity } = this.state;
    const newEntity = _.merge({}, entity);
    newEntity.type = option ? option.value : null;
    this.setState({
      typeForceSearchParameters,
      type: option ? option.value : null,
      entity: newEntity,
      incompatibleWithMultiple: false
    }, () => {
      // clear values in specific fields
      this.refs.attributeName.setValue(null);
      this.refs.formAttribute.setValue(null);
      this.refs.comparison.setValue(AutomaticRoleAttributeRuleComparisonEnum.EQUALS);
    });
  }

  _formAttributeOrComparisonChange(option) {
    const { entity } = this.state;
    let formAttribute = null;
    let comparison = null;
    if (option && option.id) {
      formAttribute = option;
      comparison = entity.comparison;
    } else {
      comparison = option;
      formAttribute = entity.formAttribute;
    }

    let valueRequired = true;
    let hideValueField = false;
    let incompatibleWithMultiple = true;

    if (comparison && comparison.value && (comparison.value === AutomaticRoleAttributeRuleComparisonEnum.findKeyBySymbol(AutomaticRoleAttributeRuleComparisonEnum.IS_EMPTY) ||
    comparison.value === AutomaticRoleAttributeRuleComparisonEnum.findKeyBySymbol(AutomaticRoleAttributeRuleComparisonEnum.IS_NOT_EMPTY)) &&
    (!formAttribute || !formAttribute.persistentType || formAttribute.persistentType !== 'TEXT')) {
      valueRequired = false;
      hideValueField = true;
    }
    if (comparison && (
      comparison.value === AutomaticRoleAttributeRuleComparisonEnum.findKeyBySymbol(AutomaticRoleAttributeRuleComparisonEnum.EQUALS) ||
      comparison.value === AutomaticRoleAttributeRuleComparisonEnum.findKeyBySymbol(AutomaticRoleAttributeRuleComparisonEnum.IS_EMPTY) ||
      comparison.value === AutomaticRoleAttributeRuleComparisonEnum.findKeyBySymbol(AutomaticRoleAttributeRuleComparisonEnum.IS_NOT_EMPTY))) {
      incompatibleWithMultiple = false;
    }

    entity.comparison = comparison;
    entity.formAttribute = formAttribute;
    //
    this.setState({
      entity,
      formAttribute,
      valueRequired,
      hideValueField,
      incompatibleWithMultiple
    });
  }

  _attributeNameChange(option) {
    // set new attribute name
    this.setState({
      attributeName: option ? option.value : null
    });
  }

  /**
   * Return component that corespond with persisntent type of value type.
   * As default show text field.
   */
  _getValueField(type, valueRequired, formAttribute, attributeName) {
    const { entity } = this.props;
    const value = entity.value;
    let finalComponent = null;
    //
    if (type === AutomaticRoleAttributeRuleTypeEnum.findKeyBySymbol(AutomaticRoleAttributeRuleTypeEnum.IDENTITY) ||
    type === AutomaticRoleAttributeRuleTypeEnum.findKeyBySymbol(AutomaticRoleAttributeRuleTypeEnum.CONTRACT)) {
      finalComponent = this._getValueFieldForEntity(entity, type, value, attributeName, valueRequired);
    } else if (formAttribute) {
      finalComponent = this._getValueFieldForEav(formAttribute, value, valueRequired);
    } else {
      // form attribute doesn't exists
      finalComponent = this._getDefaultTextField(value, valueRequired);
    }
    return finalComponent;
  }

  _getValueFieldForEntity(entity, type, value, attributeName, valueRequired) {
    if (attributeName == null) {
      return this._getDefaultTextField(value, valueRequired);
    }
    // identity attributes
    if (type === AutomaticRoleAttributeRuleTypeEnum.findKeyBySymbol(AutomaticRoleAttributeRuleTypeEnum.IDENTITY)) {
      // disabled is obly attribute that has different face
      if (IdentityAttributeEnum.findSymbolByKey(attributeName) === IdentityAttributeEnum.DISABLED) {
        return this._getDefaultBooleanSelectBox(value, valueRequired);
      }
      if (IdentityAttributeEnum.findSymbolByKey(attributeName) === IdentityAttributeEnum.FORM_PROJECTION) {
        return (
          <Advanced.FormProjectionSelect
            ref="value"
            label={ this.i18n('entity.AutomaticRole.attribute.value.label') }
            helpBlock={ this.i18n('entity.AutomaticRole.attribute.value.help') }
            value={ value }
            readOnly={ this.props.readOnly }
            required={ valueRequired }
            showIcon/>
        );
      }
      return this._getDefaultTextField(value, valueRequired);
    }
    // contracts attributes
    // contract has externe and main as boolean and valid attributes as date
    if (ContractAttributeEnum.findSymbolByKey(attributeName) === ContractAttributeEnum.MAIN
        || ContractAttributeEnum.findSymbolByKey(attributeName) === ContractAttributeEnum.EXTERNE) {
      return this._getDefaultBooleanSelectBox(value, valueRequired);
    }
    if (ContractAttributeEnum.findSymbolByKey(attributeName) === ContractAttributeEnum.VALID_FROM
        || ContractAttributeEnum.findSymbolByKey(attributeName) === ContractAttributeEnum.VALID_TILL) {
      return this._getDefaultDateTimePicker(value, valueRequired);
    }
    if (ContractAttributeEnum.findSymbolByKey(attributeName) === ContractAttributeEnum.STATE) {
      return this._getContractStateEnum(value, valueRequired);
    }
    return this._getDefaultTextField(value, valueRequired);
  }

  _getValueFieldForEav(formAttribute, value, valueRequired) {
    const { readOnly } = this.props;

    const attribute = {
      ...formAttribute,
      faceType: formAttribute.persistentType === 'BOOLEAN' && !formAttribute.faceType ? 'BOOLEAN-SELECT' : formAttribute.faceType
    };

    const component = this.formAttributeManager.getFormComponent(attribute);
    if (!component || !component.component) {
      // when component doesn't exists show default field
      return this._getDefaultTextField(value, valueRequired);
    }
    if (formAttribute.persistentType === 'TEXT') {
      return (
        <Basic.LabelWrapper label={ this.i18n('entity.AutomaticRole.attribute.value.label') }>
          <Basic.Alert text={this.i18n('attributeCantBeUsed.persistentTypeText', {name: attribute.name})}/>
        </Basic.LabelWrapper>
      );
    }
    if (formAttribute.confidential) {
      return (
        <Basic.LabelWrapper label={ this.i18n('entity.AutomaticRole.attribute.value.label') }>
          <Basic.Alert text={this.i18n('attributeCantBeUsed.confidential', {name: attribute.name})}/>
        </Basic.LabelWrapper>
      );
    }
    const FormValueComponent = component.component;
    //
    // override helpBlock, label and placeholder
    const _formAttribute = _.merge({}, attribute); // immutable - form attribute is used twice on the form
    _formAttribute.description = this.i18n('entity.AutomaticRole.attribute.value.help');
    _formAttribute.name = this.i18n('entity.AutomaticRole.attribute.value.label');
    _formAttribute.placeholder = '';
    _formAttribute.defaultValue = null;
    _formAttribute.required = valueRequired && formAttribute.persistentType !== 'BOOLEAN';
    _formAttribute.readonly = readOnly; // readnOnly from props has prio, default value is false
    _formAttribute.multiple = false; // Multiple value cannot be added
    //
    // is neccessary transform value to array
    return (
      <FormValueComponent
        ref="value"
        attribute={ _formAttribute }
        readOnly={ readOnly }
        values={[{ value }]}/>
    );
  }

  /**
   * Return simple text field for value input
   */
  _getDefaultTextField(value, valueRequired) {
    const { readOnly } = this.props;
    //
    return (
      <Basic.TextField
        ref="value"
        value={ value }
        readOnly={ readOnly }
        required={ valueRequired }
        label={ this.i18n('entity.AutomaticRole.attribute.value.label') }
        helpBlock={ this.i18n('entity.AutomaticRole.attribute.value.help') }/>
    );
  }

  /**
   * Return date time picker
   */
  _getDefaultDateTimePicker(value, valueRequired) {
    const { readOnly } = this.props;
    //
    return (
      <Basic.DateTimePicker
        ref="value"
        mode="date"
        readOnly={ readOnly }
        value={ value }
        required={ valueRequired }
        label={ this.i18n('entity.AutomaticRole.attribute.value.label') }
        helpBlock={ this.i18n('entity.AutomaticRole.attribute.value.help') }/>
    );
  }

  /**
   * Return default boolean select box
   */
  _getDefaultBooleanSelectBox(value, valueRequired) {
    const { readOnly } = this.props;
    //
    return (
      <Basic.BooleanSelectBox
        ref="value"
        value={ value }
        readOnly={ readOnly }
        required={ valueRequired }
        label={ this.i18n('entity.AutomaticRole.attribute.value.label') }
        helpBlock={ this.i18n('entity.AutomaticRole.attribute.value.help') }/>
    );
  }

  /**
   * Return simple text field for value input
   */
  _getContractStateEnum(value, valueRequired) {
    const { readOnly } = this.props;
    //
    return (
      <Basic.EnumSelectBox
        ref="value"
        value={ value }
        readOnly={ readOnly }
        required={ valueRequired }
        enum={ ContractStateEnum }
        useSymbol={ false }
        label={ this.i18n('entity.AutomaticRole.attribute.value.label') }
        helpBlock={ this.i18n('entity.AutomaticRole.attribute.value.help') }/>
    );
  }

  /**
   * Return warning for incompatible form attribute with comparsion
   */
  _showIncompatibleWarning(show, formAttribute) {
    if (show && formAttribute) {
      return (
        <Basic.Col lg={ 8 }>
          <Basic.LabelWrapper label={ this.i18n('entity.AutomaticRole.attribute.value.label') }>
            <Basic.Alert text={this.i18n('attributeCantBeUsed.multivaluedCantBeUsed', {name: formAttribute.name})}/>
          </Basic.LabelWrapper>
        </Basic.Col>
      );
    }
    return null;
  }

  render() {
    const { uiKey, entity, readOnly} = this.props;
    const {
      typeForceSearchParameters,
      hideValueField,
      type,
      formAttribute,
      incompatibleWithMultiple,
      valueRequired,
      attributeName
    } = this.state;

    const incompatibleFinal = incompatibleWithMultiple && formAttribute && formAttribute.multiple;

    let data = this.state.entity;
    if (!data) {
      data = entity;
    }
    //
    return (
      <Basic.Div>
        <Basic.AbstractForm
          ref="form"
          uiKey={ uiKey }
          data={ data }
          readOnly={ readOnly }>
          <Basic.EnumSelectBox
            ref="type"
            required
            clearable={ false }
            label={ this.i18n('entity.AutomaticRole.attribute.type.label') }
            helpBlock={ this.i18n('entity.AutomaticRole.attribute.type.help') }
            enum={ AutomaticRoleAttributeRuleTypeEnum }
            onChange={ this._typeChange.bind(this) }/>
          <Basic.EnumSelectBox
            ref="attributeName"
            clearable={ false }
            label={ this.i18n('entity.AutomaticRole.attribute.attributeName') }
            enum={
              type === AutomaticRoleAttributeRuleTypeEnum.findKeyBySymbol(AutomaticRoleAttributeRuleTypeEnum.IDENTITY)
              ?
              IdentityAttributeEnum
              :
              ContractAttributeEnum
            }
            hidden={ typeForceSearchParameters !== null }
            onChange={ this._attributeNameChange.bind(this) }
            required={ !(typeForceSearchParameters !== null) }/>
          <Basic.SelectBox
            ref="formAttribute"
            clearable={ false }
            returnProperty={ null }
            onChange={ this._formAttributeOrComparisonChange.bind(this) }
            forceSearchParameters={ typeForceSearchParameters }
            label={ this.i18n('entity.AutomaticRole.attribute.formAttribute') }
            hidden={ typeForceSearchParameters === null }
            required={ !(typeForceSearchParameters === null) }
            manager={ this.formAttributeManager }
            niceLabel={ (attribute) => this.formAttributeManager.getNiceLabel(attribute, true) }
            optionComponent={ AttributeOptionDecorator }/>
          <Basic.Row>
            <Basic.Col lg={ hideValueField ? 12 : 4 }>
              <Basic.EnumSelectBox
                ref="comparison"
                clearable={ false }
                required
                useFirst
                onChange={ this._formAttributeOrComparisonChange.bind(this) }
                label={ this.i18n('entity.AutomaticRole.attribute.comparison') }
                enum={ AutomaticRoleAttributeRuleComparisonEnum }/>
            </Basic.Col>
            { this._showIncompatibleWarning(incompatibleFinal, formAttribute) }
            <Basic.Col lg={ 8 } style={{ display: hideValueField || incompatibleFinal ? 'none' : '' }}>
              { this._getValueField(type, valueRequired, formAttribute, attributeName) }
            </Basic.Col>
          </Basic.Row>
        </Basic.AbstractForm>
      </Basic.Div>
    );
  }
}

AutomaticRoleAttributeRuleDetail.propTypes = {
  entity: PropTypes.object,
  uiKey: PropTypes.string.isRequired,
  attributeId: PropTypes.string,
  readOnly: PropTypes.bool
};
AutomaticRoleAttributeRuleDetail.defaultProps = {
  readOnly: false
};
