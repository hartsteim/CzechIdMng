import PropTypes from 'prop-types';
import React from 'react';
import _ from 'lodash';
import moment from 'moment';
import { connect } from 'react-redux';
//
import * as Basic from '../../components/basic';
import * as Advanced from '../../components/advanced';
import * as Utils from '../../utils';
import { IdentityContractManager, RoleTreeNodeManager, RoleManager, DataManager, IdentityRoleManager } from '../../redux';
import SearchParameters from '../../domain/SearchParameters';
import FormInstance from '../../domain/FormInstance';
import ConfigLoader from '../../utils/ConfigLoader';

const identityRoleManager = new IdentityRoleManager();
const identityContractManager = new IdentityContractManager();
const roleTreeNodeManager = new RoleTreeNodeManager();
const roleManager = new RoleManager();
let selectedRole = null;
let selectedIdentityRole = null;
const uiKeyIdentityRoleFormInstance = 'identity-role-form-instance';
const uiKeyRoleAttributeFormDefinition = 'role-attribute-form-definition';

/**
 * Detail of role concept request
 *
 * @author VS
 */
export class RoleConceptDetail extends Basic.AbstractContent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      environment: ConfigLoader.getConfig('role.table.filter.environment', [])
    };
  }

  componentDidMount() {
    super.componentDidMount();
    // We have to create concept from props here, because same instance this component
    //  could be used in past (in this case may be this.prosp and nextProps same)
    this._initComponent(this.props);
  }

  getContentKey() {
    return 'content.task.IdentityRoleConceptTable';
  }

  // @Deprecated - since V10 ... replaced by dynamic key in Route
  UNSAFE_componentWillReceiveProps(nextProps) {
    if (nextProps && (
      JSON.stringify(nextProps.entity) !== JSON.stringify(this.props.entity) ||
      nextProps.isEdit !== this.props.isEdit ||
      nextProps.multiAdd !== this.props.multiAdd
    )) {
      this._initComponent(nextProps);
    }
  }

  getEavForm() {
    return this.refs.eavForm;
  }

  getForm() {
    return this.refs.form;
  }

  _initComponent(props) {
    const entity = props.entity;
    if (!entity) {
      return;
    }
    const entityFormData = _.merge({}, entity, {
      role: entity._embedded && entity._embedded.role ? entity._embedded.role : null
    });

    if (entityFormData.role && entityFormData.role.identityRoleAttributeDefinition) {
      selectedRole = entityFormData.role;
      selectedIdentityRole = entityFormData;
      this.context.store.dispatch(roleManager.fetchAttributeFormDefinition(
        entityFormData.role.id,
        `${uiKeyRoleAttributeFormDefinition}-${entityFormData.role.id}`,
        (json, error) => {
          this.handleError(error);
        }
      ));

      if (selectedIdentityRole.id && selectedIdentityRole.operation !== 'ADD') {
        // Form definition will be loaded from identityRole only if selectedIdentityRole is identity-role not concept
        if (selectedIdentityRole.state === undefined) {
          this.context.store.dispatch(identityRoleManager.fetchFormInstances(
            selectedIdentityRole.id,
            `${uiKeyIdentityRoleFormInstance}-${selectedIdentityRole.id}`,
            (formInstances, error) => {
              if (error) {
                this.addErrorMessage({ hidden: true, level: 'info' }, error);
                this.setState({ error });
              }
            }
          ));
        }
      } else {
        selectedIdentityRole = null;
      }
    } else {
      selectedRole = null;
      selectedIdentityRole = null;
    }
    if (this.refs.role) {
      this.refs.role.focus();
    }
  }

  /**
   * Pre-fill valid-from by contract validity
   */
  _onChangeSelectOfContract(value) {
    const entity = this.state && this.state.entity ? this.state.entity : this.props.entity;
    let validFrom = value ? value.validFrom : null;
    const now = moment().utc().valueOf();
    if (validFrom && moment(validFrom).isBefore(now)) {
      validFrom = now;
    }
    const entityFormData = _.merge({}, entity);
    entityFormData.validFrom = validFrom;
    entityFormData.identityContract = value;
    if (this.refs.role) {
      entityFormData.role = this.refs.role.getValue();
    }
    if (this.props.entity) {
      this.setState({entity: entityFormData});
    }

    return true;
  }

  _onChangeSelectOfRole(value, originalValue) {
    if (!_.isArray(originalValue) || originalValue.length === 1) {
      selectedRole = _.isArray(originalValue) ? originalValue[0] : originalValue;
      if (selectedRole.identityRoleAttributeDefinition) {
        this.context.store.dispatch(
          roleManager.fetchAttributeFormDefinition(selectedRole.id, `${uiKeyRoleAttributeFormDefinition}-${selectedRole.id}`, (json, error) => {
            this.handleError(error);
          })
        );
      }
    } else {
      selectedRole = null;
    }
    // TODO: move selectedRole, _identityRoleAttributeDefinition, _identityRoleFormInstance to state and get them directly from callback above
    this.setState({ selectedRole }, () => {
      if (this.refs.roleSystem) {
     //   this.refs.roleSystem.setValue(null);
      }
    });
    //
    return true;
  }

  _onEnvironmentChange(value) {
    const codes = [];
    if (value) {
      if (_.isArray(value)) { // codelist is available - list of object
        value.forEach(v => {
          codes.push(v.value);
        });
      } else if (value.currentTarget) { // is event (~text field onchange)
        codes.push(value.currentTarget.value);
      }
    }
    //
    this.setState({
      environment: codes
    });
  }

  render() {
    const {
      showLoading,
      identityUsername,
      readOnly,
      _identityRoleAttributeDefinition,
      _identityRoleFormInstance,
      style,
      isEdit,
      multiAdd,
      validationErrors,
      showEnvironment
    } = this.props;
    const { environment } = this.state;
    const entity = this.state.entity ? this.state.entity : this.props.entity;

    if (!entity) {
      return null;
    }
    const added = entity.operation === 'ADD';

    let _formInstance = null;
    let _showEAV = false;
    if (selectedRole && selectedRole.identityRoleAttributeDefinition) {
      _showEAV = true;
    }
    let _showSystems = false;
    if ((selectedRole && selectedRole.systemsInCrossDomains > 0)
      || entity.roleSystem
      || (entity && entity._embedded && entity._embedded.role && entity._embedded.role.systemsInCrossDomains > 0)) {
      _showSystems = true;
    }

    if (_showSystems && selectedRole && selectedRole.prefilledSystemInCrossDomains
      && entity && !entity.roleSystem && Utils.Entity.isNew(entity) && added) {
      entity.roleSystem = selectedRole.prefilledSystemInCrossDomains;
    }
    console.log('Class: RoleConceptDetail, Function: render, Line 214 selectedRole, _showSystems, entity.roleSystem(): '
    , selectedRole, _showSystems, entity.roleSystem);

    let selectedRoleId = null;
    if (selectedRole) {
      selectedRoleId = selectedRole.id;
    }
    if (!selectedRoleId && entity && entity._embedded && entity._embedded.role) {
      selectedRoleId = entity._embedded.role.id;
    }

    if (selectedRole && _identityRoleAttributeDefinition) {
      if (entity
        && entity._eav
        && entity._eav.length === 1) {
        _formInstance = new FormInstance(_identityRoleAttributeDefinition, entity._eav[0].values);
      } else if (multiAdd) {
        _formInstance = new FormInstance(_identityRoleAttributeDefinition, null);
      }
    }
    if (!_formInstance && _identityRoleFormInstance && _identityRoleFormInstance.size === 1) {
      _identityRoleFormInstance.forEach(instance => {
        _formInstance = instance;
      });
    }

    return (
      <Basic.AbstractForm
        ref="form"
        data={ entity }
        style={ style }
        showLoading={ showLoading }
        readOnly={ !isEdit || readOnly }>
        <Advanced.CodeListSelect
          code="environment"
          hidden={!showEnvironment}
          label={ this.i18n('entity.Role.environment.label') }
          placeholder={ this.i18n('entity.Role.environment.help') }
          multiSelect
          onChange={ this._onEnvironmentChange.bind(this) }
          rendered={ (!added || readOnly || !Utils.Entity.isNew(entity)) === false }
          value={ environment }/>
        <Advanced.RoleSelect
          required
          readOnly={ !added || readOnly || !Utils.Entity.isNew(entity)}
          multiSelect={ added && multiAdd }
          showActionButtons
          header={ this.i18n('selectRoleCatalogue.header') }
          onChange={ this._onChangeSelectOfRole.bind(this) }
          label={ this.i18n('entity.IdentityRole.role') }
          ref="role"
          forceSearchParameters={new SearchParameters('can-be-requested')
            .setFilter('environment', environment)
            .setFilter('includeCrossDomainsSystemsCount', 'true')
          }
        />
        <Advanced.EntitySelectBox
          ref="roleSystem"
          readOnly={ !added || readOnly }
          hidden={!_showSystems}
          entityType="roleSystem"
          disableable={false}
          clearable
          forceSearchParameters={
            new SearchParameters()
              .setFilter('isInCrossDomainGroupRoleId', selectedRoleId || SearchParameters.BLANK_UUID)
          }
          helpBlock={ this.i18n('entity.IdentityRole.roleSystem.help')}
          label={ this.i18n('entity.IdentityRole.roleSystem.label') }/>
        <Advanced.IdentityContractSelect
          ref="identityContract"
          manager={ identityContractManager }
          forceSearchParameters={
            new SearchParameters()
              .setFilter('identity', identityUsername)
              .setFilter('validNowOrInFuture', true)
              .setFilter('_permission', ['CHANGEPERMISSION', 'CANBEREQUESTED'])
              .setFilter('_permission_operator', 'or')
          }
          defaultSearchParameters={
            new SearchParameters().clearSort()
          }
          pageSize={ 100 }
          label={ this.i18n('entity.IdentityRole.identityContract.label') }
          placeholder={ this.i18n('entity.IdentityRole.identityContract.placeholder') }
          helpBlock={ this.i18n('entity.IdentityRole.identityContract.help') }
          returnProperty={false}
          readOnly={ !added || readOnly || !Utils.Entity.isNew(entity) }
          onChange={ this._onChangeSelectOfContract.bind(this) }
          niceLabel={ (contract) => identityContractManager.getNiceLabel(contract, false) }
          required
          useFirst
          clearable={ false }/>
        <Basic.LabelWrapper
          label={ this.i18n('entity.IdentityRole.automaticRole.label') }
          helpBlock={ this.i18n('entity.IdentityRole.automaticRole.help') }
          rendered={ entity.automaticRole !== null }
          hidden={ added }>
          { entity.automaticRole ? roleTreeNodeManager.getNiceLabel(entity._embedded.automaticRole) : null }
        </Basic.LabelWrapper>
        <Basic.Row>
          <Basic.Col lg={ 6 }>
            <Basic.DateTimePicker
              mode="date"
              className={entity.hasOwnProperty('_validFromChanged') ? 'text-danger' : null}
              ref={entity.hasOwnProperty('_validFromChanged') ? '_validFromChanged' : 'validFrom'}
              label={this.i18n('label.validFrom')}/>
          </Basic.Col>
          <Basic.Col lg={ 6 }>
            <Basic.DateTimePicker
              mode="date"
              className={entity.hasOwnProperty('_validTillChanged') ? 'text-danger' : null}
              ref={entity.hasOwnProperty('_validTillChanged') ? '_validTillChanged' : 'validTill'}
              label={this.i18n('label.validTill')}/>
          </Basic.Col>
        </Basic.Row>
        <Basic.Panel rendered={_showEAV} showLoading={!_formInstance} style={{border: '0px'}}>
          <Basic.ContentHeader>
            {this.i18n('identityRoleAttributes.header') }
          </Basic.ContentHeader>
          <Advanced.EavForm
            ref="eavForm"
            formInstance={ _formInstance }
            readOnly={!isEdit || readOnly}
            useDefaultValue={Utils.Entity.isNew(entity)}
            validationErrors={ validationErrors }/>
        </Basic.Panel>
      </Basic.AbstractForm>
    );
  }
}

RoleConceptDetail.propTypes = {
  multiAdd: PropTypes.bool,
  showLoading: PropTypes.bool,
  isEdit: PropTypes.bool,
  entity: PropTypes.object,
  identityUsername: PropTypes.string,
  readOnly: PropTypes.bool,
  showEnvironment: PropTypes.bool
};

RoleConceptDetail.defaultProps = {
  multiAdd: false,
  showLoading: false,
  readOnly: true,
  showEnvironment: true
};

function select(state) {
  if (!selectedRole || !selectedRole.identityRoleAttributeDefinition) {
    return {};
  }

  const identityRoleAttributeDefinition = selectedRole.identityRoleAttributeDefinition;
  return {
    _identityRoleFormInstance:
      selectedIdentityRole ? DataManager.getData(state, `${uiKeyIdentityRoleFormInstance}-${selectedIdentityRole.id}`) : null,
    _identityRoleAttributeDefinition:
      identityRoleAttributeDefinition ? DataManager.getData(state, `${uiKeyRoleAttributeFormDefinition}-${selectedRole.id}`) : null,
  };
}

export default connect(select, null, null, { forwardRef: true })(RoleConceptDetail);
