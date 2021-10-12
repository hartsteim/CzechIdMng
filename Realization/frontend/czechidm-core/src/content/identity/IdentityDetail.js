import React from 'react';
import PropTypes from 'prop-types';
import Joi from 'joi';
import { connect } from 'react-redux';
import Helmet from 'react-helmet';
import moment from 'moment';
//
import * as Domain from '../../domain';
import * as Basic from '../../components/basic';
import * as Advanced from '../../components/advanced';
import * as Utils from '../../utils';
import {
  IdentityManager,
  DataManager,
  ProfileManager,
  SecurityManager
} from '../../redux';
import IdentityStateEnum from '../../enums/IdentityStateEnum';
import DisableIdentityDashboardButton from '../dashboards/button/DisableIdentityDashboardButton';
import EnableIdentityDashboardButton from '../dashboards/button/EnableIdentityDashboardButton';

const identityManager = new IdentityManager();
const profileManager = new ProfileManager();

/**
 * Identity's detail form
 *
 * @author Radek Tomiška
 */
class IdentityDetail extends Advanced.AbstractFormableContent {

  constructor(props) {
    super(props);
    this.state = {
      showLoading: false,
      showLoadingIdentityTrimmed: false,
      deleteButton: false,
      showCropper: false,
      validationErrors: null
    };
  }

  getContentKey() {
    return 'content.identity.profile';
  }

  componentDidMount() {
    const { entityId } = this.props;
    this.context.store.dispatch(identityManager.fetchProfilePermissions(entityId));
  }

  // @Deprecated - since V10 ... replaced by dynamic key in Route
  // UNSAFE_componentWillReceiveProps(nextProps) {
  //   if (nextProps.identity) {
  //     if (nextProps.identity._trimmed) {
  //       this.setState({showLoadingIdentityTrimmed: true});
  //     } else {
  //       this.setState({showLoadingIdentityTrimmed: false});
  //     }
  //     if (nextProps.identity !== this.props.identity) {
  //       // after receive new Identity we will hide showLoading on form
  //       this.setState({showLoading: false, setDataToForm: true});
  //       this.context.store.dispatch(identityManager.fetchProfilePermissions(nextProps.entityId));
  //     }
  //   }
  // }

  // componentDidUpdate() {
  //   if (this.props.identity && !this.props.identity._trimmed && this.state.setDataToForm) {
  //     // We have to set data to form after is rendered
  //     this.transformData(this.props.identity, null, ApiOperationTypeEnum.GET);
  //   }
  // }

  onSave(event) {
    if (event) {
      event.preventDefault();
    }
    if (!this.refs.form.isFormValid()) {
      return;
    }
    const json = this.refs.form.getData();
    this.saveIdentity(json);
  }

  saveIdentity(json) {
    this.setState({
      showLoading: true
    }, () => {
      this.context.store.dispatch(identityManager.updateEntity(json, null, (updatedEntity, error) => {
        this._afterSave(updatedEntity, error);
      }));
    });
  }

  _afterSave(entity, error) {
    if (error) {
      let validationErrors = null;
      if (error.statusEnum === 'FORM_INVALID' && error.parameters) {
        validationErrors = error.parameters.attributes;
        // focus the first invalid component
        if (validationErrors && validationErrors.length > 0) {
          // identity owner
          const firstValidationError = validationErrors[0];
          if (this.refs[firstValidationError.attributeCode] && firstValidationError.definitionCode === 'idm:basic-fields') {
            this.refs[firstValidationError.attributeCode].focus();
          }
        }
      }
      this.setState({
        showLoading: false,
        validationErrors
      }, () => {
        this.addError(error);
      });
      //
      return;
    }
    //
    // ok
    this.setState({
      showLoading: false,
      validationErrors: null
    }, () => {
      this.addMessage({ level: 'success', key: 'form-success', message: this.i18n('messages.saved', { username: entity.username }) });
      //
      // when username was changed, then new url is replaced
      const { identity } = this.props;
      if (identity.username !== entity.username) {
        this.context.history.replace(`/identity/${ encodeURIComponent(entity.username) }/profile`);
      }
    });
  }

  /**
   * Dropzone component function called after select file
   * @param file selected file (multiple is not allowed)
   */
  _onDrop(files) {
    if (this.refs.dropzone.state.isDragReject) {
      this.addMessage({
        message: this.i18n('fileRejected'),
        level: 'warning'
      });
      return;
    }
    files.forEach((file) => {
      const fileName = file.name.toLowerCase();
      if (!fileName.endsWith('.jpg') && !fileName.endsWith('.jpeg') && !fileName.endsWith('.png') && !fileName.endsWith('.gif')) {
        this.addMessage({
          message: this.i18n('fileRejected', {name: file.name}),
          level: 'warning'
        });
        return;
      }
      const objectURL = URL.createObjectURL(file);
      this.setState({
        cropperSrc: objectURL,
        showCropper: true,
        fileName: file.name
      });
    });
  }

  deleteImage() {
    this.refs['confirm-delete'].show(
      this.i18n(`deleteImage.message`),
      this.i18n(`deleteImage.title`)
    ).then(() => {
      this.context.store.dispatch(identityManager.deleteProfileImage(this.props.entityId));
    }, () => {
      // Rejected
    });
  }

  _showCropper() {
    this.setState({
      showCropper: true
    });
  }

  _closeCropper() {
    this.setState({
      showCropper: false
    });
  }

  _crop() {
    this.refs.cropper.crop((formData) => {
      // append selected fileName
      formData.fileName = this.state.fileName;
      formData.name = this.state.fileName;
      formData.append('fileName', this.state.fileName);
      //
      this.context.store.dispatch(identityManager.uploadProfileImage(this.props.entityId, formData));
    });
    this._closeCropper();
  }

  // FIXME: unused, form update not work after changing validations
  onChangeFormProjection(formProjection) {
    let formInstance = null;
    if (formProjection) {
      try {
        const formValidations = JSON.parse(formProjection.formValidations);
        if (formValidations && formValidations.length > 0) {
          formInstance = new Domain.FormInstance({ definition: { formAttributes: formValidations }});
        }
      } catch (syntaxError) {
        formInstance = null; // not valid => will be checked on BE => preserve original validations
      }
    } else {
      formInstance = new Domain.FormInstance({});
    }
    //
    this.setState({
      formInstance
    });
  }

  render() {
    const { identity, readOnly, _permissions, _profilePermissions, _imageUrl, _imageLoading, userContext } = this.props;
    const { showLoading, showLoadingIdentityTrimmed, showCropper, cropperSrc, validationErrors } = this.state;
    const formInstance = this.getBasicAttributesFormInstance(identity);
    const blockLoginDate = identity && identity.blockLoginDate ? moment(identity.blockLoginDate).format(this.i18n('format.datetime')) : null;
    const _readOnly = !identityManager.canSave(identity, _permissions) || readOnly;
    const _readOnlyUsername = _readOnly || !Utils.Permission.hasPermission(_permissions, 'CHANGEUSERNAME');
    const _readOnlyName = _readOnly || !Utils.Permission.hasPermission(_permissions, 'CHANGENAME');
    const _readOnlyExternalCode = _readOnly || !Utils.Permission.hasPermission(_permissions, 'CHANGEEXTERNALCODE');
    const _readOnlyEmail = _readOnly || !Utils.Permission.hasPermission(_permissions, 'CHANGEEMAIL');
    const _readOnlyPhone = _readOnly || !Utils.Permission.hasPermission(_permissions, 'CHANGEPHONE');
    const _readOnlyDescription = _readOnly || !Utils.Permission.hasPermission(_permissions, 'CHANGEDESCRIPTION');
    //
    return (
      <Basic.Div className="identity-detail">
        <Basic.Confirm ref="confirm-delete" level="danger"/>
        <Helmet title={ identity && identity.id === userContext.id ? this.i18n('title') : this.i18n('content.identity.profile.userDetail') } />
        <form onSubmit={ this.onSave.bind(this) }>
          <Basic.Panel className="no-border last">
            <Basic.PanelHeader text={ this.i18n('header') }/>
            <Basic.Alert
              ref="blockLoginDate"
              level="warning"
              rendered={ blockLoginDate !== null }
              text={ this.i18n('blockLoginDate', { date: blockLoginDate }) } />

            <Basic.AbstractForm
              ref="form"
              data={ identity }
              showLoading={ showLoadingIdentityTrimmed || showLoading }>
              <Basic.Div className="image-field-container">
                <Basic.Div className="image-col">
                  <Basic.Div className="image-wrapper">
                    <Advanced.ImageDropzone
                      ref="dropzone"
                      accept="image/*"
                      multiple={ false }
                      onDrop={ this._onDrop.bind(this) }
                      showLoading={ _imageLoading }
                      readOnly={ !profileManager.canSave(identity, _profilePermissions) }>
                      <img className="img-thumbnail" alt="profile" src={ _imageUrl } />
                    </Advanced.ImageDropzone>
                    <Basic.Fab color="inherit" className={ cropperSrc && _imageUrl ? 'btn-edit' : 'hidden' } size="small">
                      <Basic.Button
                        type="button"
                        buttonSize="xs"
                        rendered={ !!(cropperSrc && _imageUrl) }
                        titlePlacement="right"
                        onClick={ this._showCropper.bind(this) }
                        icon="edit"/>
                    </Basic.Fab>
                    <Basic.Fab
                      color="secondary"
                      className={ _imageUrl && profileManager.canSave(identity, _profilePermissions) ? 'btn-remove' : 'hidden' }
                      size="small">
                      <Basic.Button
                        type="button"
                        level="danger"
                        buttonSize="xs"
                        style={{ color: 'white' }}
                        rendered={ !!(_imageUrl && profileManager.canSave(identity, _profilePermissions)) }
                        titlePlacement="left"
                        onClick={ this.deleteImage.bind(this) }
                        icon="fa:trash"/>
                    </Basic.Fab>
                  </Basic.Div>
                </Basic.Div>
                <Basic.Div className="field-col">
                  <Basic.TextField
                    ref="username"
                    label={ this.getLabel(formInstance, 'username', this.i18n('username')) }
                    placeholder={ this.getPlaceholder(formInstance, 'username') }
                    readOnly={ this.isReadOnly(formInstance, 'username', _readOnlyUsername) }
                    required
                    min={ this.getMin(formInstance, 'username', _readOnlyUsername, 3) }
                    max={ this.getMax(formInstance, 'username', _readOnlyUsername, 255) }
                    validationMessage={ this.getValidationMessage(formInstance, 'username') }
                    validationErrors={
                      this.getInvalidBasicField(validationErrors, 'username')
                    }/>
                  <Basic.TextField
                    ref="firstName"
                    label={ this.getLabel(formInstance, 'firstName', this.i18n('firstName')) }
                    placeholder={ this.getPlaceholder(formInstance, 'firstName') }
                    readOnly={ this.isReadOnly(formInstance, 'firstName', _readOnlyName) }
                    required={ this.isRequired(formInstance, 'firstName', _readOnlyName) }
                    min={ this.getMin(formInstance, 'firstName', _readOnlyName) }
                    max={ this.getMax(formInstance, 'firstName', _readOnlyName, 255) }
                    validationMessage={ this.getValidationMessage(formInstance, 'firstName') }
                    validationErrors={
                      this.getInvalidBasicField(validationErrors, 'firstName')
                    }/>
                  <Basic.TextField
                    ref="lastName"
                    label={ this.getLabel(formInstance, 'lastName', this.i18n('lastName')) }
                    placeholder={ this.getPlaceholder(formInstance, 'lastName') }
                    readOnly={ this.isReadOnly(formInstance, 'lastName', _readOnlyName) }
                    required={ this.isRequired(formInstance, 'lastName', _readOnlyName) }
                    min={ this.getMin(formInstance, 'lastName', _readOnlyName) }
                    max={ this.getMax(formInstance, 'lastName', _readOnlyName, 255) }
                    validationMessage={ this.getValidationMessage(formInstance, 'lastName') }
                    validationErrors={
                      this.getInvalidBasicField(validationErrors, 'lastName')
                    }/>
                  <Basic.TextField
                    ref="externalCode"
                    label={ this.getLabel(formInstance, 'externalCode', this.i18n('content.identity.profile.externalCode')) }
                    placeholder={ this.getPlaceholder(formInstance, 'externalCode') }
                    readOnly={ this.isReadOnly(formInstance, 'externalCode', _readOnlyExternalCode) }
                    required={ this.isRequired(formInstance, 'externalCode', _readOnlyExternalCode) }
                    min={ this.getMin(formInstance, 'externalCode', _readOnlyExternalCode) }
                    max={ this.getMax(formInstance, 'externalCode', _readOnlyExternalCode, 255) }
                    validationMessage={ this.getValidationMessage(formInstance, 'externalCode') }
                    validationErrors={
                      this.getInvalidBasicField(validationErrors, 'externalCode')
                    }/>
                </Basic.Div>
              </Basic.Div>
              <Basic.Row>
                <Basic.Col lg={ 6 }>
                  <Basic.TextField
                    ref="titleBefore"
                    label={ this.getLabel(formInstance, 'titleBefore', this.i18n('entity.Identity.titleBefore')) }
                    placeholder={ this.getPlaceholder(formInstance, 'titleBefore') }
                    readOnly={ this.isReadOnly(formInstance, 'titleBefore', _readOnlyName) }
                    required={ this.isRequired(formInstance, 'titleBefore', _readOnlyName) }
                    min={ this.getMin(formInstance, 'titleBefore', _readOnlyName) }
                    max={ this.getMax(formInstance, 'titleBefore', _readOnlyName, 100) }
                    validationMessage={ this.getValidationMessage(formInstance, 'titleBefore') }
                    validationErrors={
                      this.getInvalidBasicField(validationErrors, 'titleBefore')
                    }/>
                </Basic.Col>
                <Basic.Col lg={ 6 }>
                  <Basic.TextField
                    ref="titleAfter"
                    label={ this.getLabel(formInstance, 'titleAfter', this.i18n('entity.Identity.titleAfter')) }
                    placeholder={ this.getPlaceholder(formInstance, 'titleAfter') }
                    readOnly={ this.isReadOnly(formInstance, 'titleAfter', _readOnlyName) }
                    required={ this.isRequired(formInstance, 'titleAfter', _readOnlyName) }
                    min={ this.getMin(formInstance, 'titleAfter', _readOnlyName) }
                    max={ this.getMax(formInstance, 'titleAfter', _readOnlyName, 100) }
                    validationMessage={ this.getValidationMessage(formInstance, 'titleAfter') }
                    validationErrors={
                      this.getInvalidBasicField(validationErrors, 'titleAfter')
                    }/>
                </Basic.Col>
              </Basic.Row>
              <Basic.Row>
                <Basic.Col lg={ 6 }>
                  <Basic.TextField
                    ref="email"
                    label={ this.getLabel(formInstance, 'email', this.i18n('email.label')) }
                    placeholder={ this.getPlaceholder(formInstance, 'email', this.i18n('email.placeholder')) }
                    readOnly={ this.isReadOnly(formInstance, 'email', _readOnlyEmail) }
                    validation={ Joi.string().allow(null).email() }
                    required={ this.isRequired(formInstance, 'email', _readOnlyEmail) }
                    min={ this.getMin(formInstance, 'email', _readOnlyEmail) }
                    max={ this.getMax(formInstance, 'email', _readOnlyEmail, 255) }
                    validationMessage={ this.getValidationMessage(formInstance, 'email') }
                    validationErrors={
                      this.getInvalidBasicField(validationErrors, 'email')
                    }/>
                </Basic.Col>
                <Basic.Col lg={ 6 }>
                  <Basic.TextField
                    ref="phone"
                    label={ this.getLabel(formInstance, 'phone', this.i18n('phone.label')) }
                    placeholder={ this.getPlaceholder(formInstance, 'phone', this.i18n('phone.placeholder')) }
                    readOnly={ this.isReadOnly(formInstance, 'phone', _readOnlyPhone) }
                    required={ this.isRequired(formInstance, 'phone', _readOnlyPhone) }
                    min={ this.getMin(formInstance, 'phone', _readOnlyPhone) }
                    max={ this.getMax(formInstance, 'phone', _readOnlyPhone, 30) }
                    validationMessage={ this.getValidationMessage(formInstance, 'phone') }
                    validationErrors={
                      this.getInvalidBasicField(validationErrors, 'phone')
                    }/>
                </Basic.Col>
              </Basic.Row>
              <Basic.TextArea
                ref="description"
                label={ this.getLabel(formInstance, 'description', this.i18n('description.label')) }
                placeholder={ this.getPlaceholder(formInstance, 'description', this.i18n('description.placeholder')) }
                rows={ 4 }
                readOnly={ this.isReadOnly(formInstance, 'description', _readOnlyDescription) }
                required={ this.isRequired(formInstance, 'description', _readOnlyDescription) }
                min={ this.getMin(formInstance, 'description', _readOnlyDescription) }
                max={ this.getMax(formInstance, 'description', _readOnlyDescription, 1000) }
                validationMessage={ this.getValidationMessage(formInstance, 'description') }
                validationErrors={
                  this.getInvalidBasicField(validationErrors, 'description')
                }/>
              {
                !SecurityManager.hasAllAuthorities(['FORMPROJECTION_AUTOCOMPLETE'], userContext)
                ||
                <Advanced.FormProjectionSelect
                  ref="formProjection"
                  label={ this.i18n('entity.Identity.formProjection.label') }
                  helpBlock={ this.i18n('entity.Identity.formProjection.help') }
                  readOnly={ _readOnly || !Utils.Permission.hasPermission(_permissions, 'CHANGEPROJECTION') }
                  showIcon/>
              }
              <Basic.EnumSelectBox
                ref="state"
                enum={ IdentityStateEnum }
                useSymbol={ false }
                label={ this.i18n('entity.Identity.state.label') }
                helpBlock={ <span>{ this.i18n('entity.Identity.state.help') }</span> }
                readOnly/>
              <Basic.Checkbox
                ref="disabled"
                label={ this.i18n('entity.Identity.disabledReadonly.label') }
                helpBlock={ this.i18n('entity.Identity.disabledReadonly.help') }
                readOnly />
            </Basic.AbstractForm>
            <Basic.PanelFooter>
              <Basic.Button
                type="button"
                level="link"
                onClick={ this.context.history.goBack }
                showLoading={ showLoading }>
                { this.i18n('button.back') }
              </Basic.Button>
              {
                !identity || !identity.formProjection
                ||
                <Basic.Button
                  type="button"
                  level="link"
                  rendered={ SecurityManager.hasAllAuthorities(['FORMPROJECTION_UPDATE'], userContext) && this.isDevelopment() }
                  onClick={ () => this.context.history.push(`/form-projections/${ identity.formProjection }/detail`) }>
                  { this.i18n('content.identity.projection.button.formProjection.label') }
                </Basic.Button>
              }
              {
                !identity
                ||
                <Basic.Div style={{ display: 'inline' }}>
                  <DisableIdentityDashboardButton
                    entityId={ identity.username }
                    identity={ identity }
                    permissions={ _permissions }
                    buttonSize="default"/>
                  <EnableIdentityDashboardButton
                    entityId={ identity.username }
                    identity={ identity }
                    permissions={ _permissions }
                    buttonSize="default"/>
                </Basic.Div>
              }
              <Basic.Button
                type="submit"
                level="success"
                showLoading={ showLoading }
                showLoadingIcon
                showLoadingText={ this.i18n('button.saving') }
                rendered={ identityManager.canSave(identity, _permissions) }
                hidden={ readOnly }>
                { this.i18n('button.save') }
              </Basic.Button>
            </Basic.PanelFooter>
          </Basic.Panel>
        </form>

        <Basic.Modal
          bsSize="default"
          show={ showCropper }
          onHide={ this._closeCropper.bind(this) }
          backdrop="static" >
          <Basic.Modal.Body>
            <Advanced.ImageCropper
              ref="cropper"
              src={ cropperSrc }/>
          </Basic.Modal.Body>
          <Basic.Modal.Footer>
            <Basic.Button
              level="link"
              onClick={ this._closeCropper.bind(this) }
              showLoading={ showLoading }>
              { this.i18n('button.close') }
            </Basic.Button>
            <Basic.Button
              level="info"
              onClick={ this._crop.bind(this) }
              showLoading={ showLoading }>
              { this.i18n('button.crop') }
            </Basic.Button>
          </Basic.Modal.Footer>
        </Basic.Modal>
      </Basic.Div>
    );
  }
}

IdentityDetail.propTypes = {
  identity: PropTypes.object,
  entityId: PropTypes.string.isRequired,
  readOnly: PropTypes.bool,
  userContext: PropTypes.object,
  _permissions: PropTypes.oneOfType([
    PropTypes.bool,
    PropTypes.arrayOf(PropTypes.string)
  ]),
  _profilePermissions: PropTypes.oneOfType([
    PropTypes.bool,
    PropTypes.arrayOf(PropTypes.string)
  ]),
};
IdentityDetail.defaultProps = {
  userContext: null,
  _permissions: null,
  _profilePermissions: null,
  readOnly: false,
  _imageUrl: null
};

function select(state, component) {
  const identifier = component.entityId;
  const profileUiKey = identityManager.resolveProfileUiKey(identifier);
  const profile = DataManager.getData(state, profileUiKey);
  //
  return {
    userContext: state.security.userContext,
    _permissions: identityManager.getPermissions(state, null, identifier),
    _profilePermissions: profileManager.getPermissions(state, null, identifier),
    _imageLoading: DataManager.isShowLoading(state, profileUiKey),
    _imageUrl: profile ? profile.imageUrl : null,
  };
}
export default connect(select)(IdentityDetail);
