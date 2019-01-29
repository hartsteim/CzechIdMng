import React, { PropTypes } from 'react';
//
import * as Basic from '../../basic';
import * as Domain from '../../../domain';
import { CodeListItemManager } from '../../../redux';

const codeListItemManager = new CodeListItemManager();

/**
* Code list select
* - render enum select box with available code list items (options)
* - decorator only - if code list is not available (204), then text box is shown
*
* TODO: creatable?
* TODO: multiselect
* TODO: big code list - pagination
* TODO: use redux data, when force search parameters are empty?
* TODO: readme
*
* @author Radek Tomiška
* @since 9.4.0
*/
export default class CodeListSelect extends Basic.AbstractFormComponent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      ...this.state,
      showLoading: true,
      options: []
    };
  }

  componentDidMount() {
    super.componentDidMount();
    //
    this._loadOptions();
  }

  getComponentKey() {
    return 'component.advanced.CodeListSelect';
  }

  getUiKey() {
    return this.props.uiKey;
  }

  getValue() {
    const { options } = this.state;
    //
    if (options.length > 0 ) {
      return this.refs.inputEnum.getValue();
    }
    return this.refs.inputText.getValue();
  }

  setValue(value, cb) {
    this.setState({ value }, cb);
  }

  isValid() {
    const { options } = this.state;
    //
    if (options.length > 0 ) {
      return this.refs.inputEnum.isValid();
    }
    return this.refs.inputText.isValid();
  }

  validate(showValidationError, cb) {
    const { readOnly, rendered } = this.props;
    const { options } = this.state;
    //
    if (readOnly || !rendered) {
      return true;
    }
    if (options.length > 0 ) {
      return this.refs.inputEnum.validate(true, cb);
    }
    return this.refs.inputText.validate(true, cb);
  }

  setState(json, cb) {
    super.setState(json, () => {
      // FIXME: abstract form component everride standard state to show validations => we need to propage this state into component
      if (json && json.showValidationError !== undefined) {
        this.refs.inputEnum.setState({ showValidationError: json.showValidationError}, cb);
        this.refs.inputText.setState({ showValidationError: json.showValidationError}, cb);
      } else if (cb) {
        cb();
      }
    });
  }

  /**
   * Focus input field
   */
  focus() {
    // TODO ...
    // this.refs.input.focus();
  }

  /**
   * Tree node field label
   *
   * @return  {string}
   */
  getLabel() {
    const { label } = this.props;
    if (label !== undefined) {
      return label;
    }
    return this.i18n('entity.CodeList._type');
  }

  /**
   * Select box field placeholder
   *
   * @return  {string}
   */
  getPlaceholder() {
    const { placeholder } = this.props;
    if (placeholder !== undefined) {
      return placeholder;
    }
    return null;
  }

  /**
   * Select box field help block
   *
   * @return  {string}
   */
  getHelpBlock() {
    const { helpBlock } = this.props;
    if (helpBlock !== undefined) {
      return helpBlock;
    }
    return null;
  }

  _loadOptions(props = null) {
    const _props = props ? props : this.props;
    const { code, forceSearchParameters, useFirst } = this.props;
    if (!_props.rendered) {
      // component is not rendered ... loading is not needed
      return;
    }
    //
    this.setState({
      showLoading: true
    }, () => {
      let searchParameters = new Domain.SearchParameters().setFilter('codeListId', code).setSort('name', false).setSize(10000);
      let _forceSearchParameters = null;
      if (forceSearchParameters) {
        _forceSearchParameters = forceSearchParameters.setSize(null).setPage(null); // we dont want override setted pagination
      }
      searchParameters = codeListItemManager.mergeSearchParameters(searchParameters, _forceSearchParameters);
      this.context.store.dispatch(codeListItemManager.fetchEntities(searchParameters, `${this.getUiKey()}-${code}`, (json, error) => {
        const options = [];
        let value = this.state.value;
        if (!error) {
          const data = json._embedded[codeListItemManager.getCollectionType()] || [];
          let valueIsPresent = false;
          // constuct operation
          data.forEach(item => {
            if (value && item.code === value) {
              valueIsPresent = true;
            }
            options.push({
              value: item.code,
              niceLabel: codeListItemManager.getNiceLabel(item)
            });
          });
          // filled value is not in the code list - append ar start
          if (value && !valueIsPresent) {
            options.unshift({
              value,
              niceLabel: this.i18n(value)
            });
          }
          if (!value && useFirst && options.length > 0) {
            value = options[0].value;
          }
        } else {
          if (error.statusCode === 400 || error.statusCode === 403) {
            // FIXME: 204 / 404 - codelist doesn't found
            // FIXME: 403 - input only?
            this.addErrorMessage({
              level: 'error',
              key: 'error-code-list-load',
              hidden: true
            }, error);
          } else {
            this.addErrorMessage({
              level: 'error',
              key: 'error-code-list-load'
            }, error);
          }
        }
        this.setState({
          options,
          showLoading: false
        }, () => {
          // TODO: enum refresh - normalize item has to be called.
          this.refs.inputEnum.setValue(value);
          this.refs.inputText.setValue(value);
        });
      }));
    });
  }

  render() {
    const { hidden, required, rendered } = this.props;
    const { options, value, disabled, readOnly } = this.state;
    const showLoading = this.props.showLoading || this.state.showLoading;
    //
    if (!rendered) {
      return null;
    }
    //
    return (
      <span>
        <Basic.EnumSelectBox
          ref="inputEnum"
          value={ value }
          label={ this.getLabel() }
          placeholder={ this.getPlaceholder() }
          helpBlock={ this.getHelpBlock() }
          readOnly={ readOnly || disabled }
          required={ required }
          hidden={ hidden || (options.length === 0 && !showLoading) }
          showLoading={ showLoading }
          options={ options }/>
        <Basic.TextField
          ref="inputText"
          value={ value }
          label={ this.getLabel() }
          placeholder={ this.getPlaceholder() }
          helpBlock={ this.getHelpBlock() }
          readOnly={ readOnly || disabled }
          required={ required }
          hidden={ showLoading || hidden || options.length > 0 }/>
      </span>
    );
  }
}

CodeListSelect.propTypes = {
  ...Basic.AbstractFormComponent.propTypes,
  /**
   * CodeList code
   */
  code: PropTypes.string.isRequired,
  /**
   * Ui key - identifier for loading data
   */
  uiKey: PropTypes.string,
  /**
   * Selectbox label
   */
  label: PropTypes.string,
  /**
   * Selectbox placeholder
   */
  placeholder: PropTypes.string,
  /**
   * Selectbox help block
   */
  helpBlock: PropTypes.string,
  /**
   * "Hard filters".
   */
  forceSearchParameters: PropTypes.object,
  /**
   * Use the first searched value, if value is empty
   */
  useFirst: PropTypes.bool
};

CodeListSelect.defaultProps = {
  ...Basic.AbstractFormComponent.defaultProps,
  uiKey: 'code-list-select',
  useFirst: false
};
