import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
//
import * as Basic from '../../components/basic';
import * as Advanced from '../../components/advanced';
import * as Domain from '../../domain';
import { SecurityManager } from '../../redux';

const securityManager = new SecurityManager();

/**
 * Switch user - modal dialog.
 *
 * @author Radek Tomiška
 * @since 10.5.0
 */
class SwitchUser extends Basic.AbstractContent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      _showLoading: false
    };
  }

  onSave(event) {
    if (event) {
      event.preventDefault();
    }
    if (!this.refs.form.isFormValid()) {
      return;
    }
    const username = this.refs.targetIdentity.getValue();
    //
    this.context.store.dispatch(securityManager.switchUser(username, (result) => {
      const { onHide } = this.props;
      if (result) {
        this.context.history.replace(`/`);
        this.addMessage({
          level: 'success',
          key: 'core-switch-user-success',
          message: this.i18n('content.identity.switch-user.message.success', { username })
        });
      }
      // modal is closed on error too => currently logged user is logout anyway
      onHide();
    }));
  }

  render() {
    const {
      rendered,
      show,
      onHide,
      userContext
    } = this.props;
    const showLoading = userContext.showLoading;
    //
    if (!rendered) {
      return null;
    }
    //
    const forceSearchParameters = new Domain.SearchParameters().setFilter('_permission', 'SWITCHUSER');
    //
    return (
      <Basic.Modal
        show={ show }
        onHide={ onHide }
        keyboard
        backdrop="static"
        onEnter={ () => { this.refs.targetIdentity.focus(); } }>
        <Basic.Modal.Header
          closeButton
          icon="component:switch-user"
          text={ this.i18n('content.identity.switch-user.header') }/>
        <Basic.Modal.Body>
          <form onSubmit={ this.onSave.bind(this) }>
            <Basic.AbstractForm
              ref="form"
              showLoading={ showLoading }>
              <Advanced.IdentitySelect
                ref="targetIdentity"
                label={ this.i18n('content.identity.switch-user.username.label') }
                helpBlock={ this.i18n('content.identity.switch-user.username.help') }
                required
                returnProperty="username"
                emptyOptionLabel={ false }
                forceSearchParameters={ forceSearchParameters }/>
            </Basic.AbstractForm>
            {/* onEnter action - is needed because footer submit button is outside form */}
            <input type="submit" className="hidden"/>
          </form>
        </Basic.Modal.Body>
        <Basic.Modal.Footer>
          <Basic.Button
            level="link"
            showLoading={ showLoading }
            onClick={ onHide }>
            { this.i18n('button.close') }
          </Basic.Button>
          <Basic.Button
            type="submit"
            level="success"
            showLoading={ showLoading }
            showLoadingIcon
            showLoadingText={ this.i18n('content.identity.switch-user.button.saving') }
            rendered={ SecurityManager.hasAuthority('IDENTITY_SWITCHUSER') }
            onClick={ this.onSave.bind(this) }>
            { this.i18n('content.identity.switch-user.button.save') }
          </Basic.Button>
        </Basic.Modal.Footer>
      </Basic.Modal>
    );
  }

}

SwitchUser.propTypes = {
  ...Basic.AbstractContent.propTypes,
  /**
   * Modal is shown.
   */
  show: PropTypes.bool,
  /**
   * onHide callback
   */
  onHide: PropTypes.func
};

SwitchUser.defaultProps = {
  ...Basic.AbstractContent.defaultProps
};

function select(state) {
  return {
    userContext: state.security.userContext
  };
}

export default connect(select)(SwitchUser);
