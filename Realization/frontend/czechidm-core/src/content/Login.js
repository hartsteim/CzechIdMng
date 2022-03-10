import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import Helmet from 'react-helmet';
import Joi from 'joi';
import { Link } from 'react-router-dom';
import _ from 'lodash';
//
import * as Basic from '../components/basic';
import { SecurityManager, DataManager, ConfigurationManager } from '../redux';
import { RestApiService } from '../services';
import {
  getNavigationItems,
} from '../redux/config/actions';
import { HelpContent } from '../domain';

const securityManager = new SecurityManager();
const dataManager = new DataManager();

/**
 * Login box.
 *
 * @author Radek Tomiška
 */
class Login extends Basic.AbstractContent {

  constructor(props, context) {
    super(props, context);
    const { query } = this.props.location;
    //
    this.state = {
      showTwoFactor: false,
      token: null,
      nocas: query && !!query.nocas
    };
  }

  getContentKey() {
    return 'content.login';
  }

  getNavigationKey() {
    return 'home';
  }

  hideFooter() {
    const { casEnabled } = this.props;
    const { showTwoFactor, nocas } = this.state;
    const _casEnabled = casEnabled && !nocas;
    //
    return !showTwoFactor && _casEnabled;
  }

  UNSAFE_componentWillReceiveProps(nextProps) {
    if (nextProps.userContext !== this.props.userContext) {
      this._tryRedirectLoggedUser();
    }
  }

  componentDidMount() {
    super.componentDidMount();
    console.log("componentDidMount 1")
    const { userContext, casEnabled, location } = this.props;
    const { nocas } = this.state;
    const _casEnabled = casEnabled && !nocas;
    //
    if (!_casEnabled) {
      console.log("componentDidMount 2")
      const data = {};
      if (userContext.isExpired) {
        data.username = userContext.username;
        console.log("componentDidMount 3")
      }
      this.refs.form.setData(data);
    }
    //
    if (!SecurityManager.isAuthenticated(userContext) && !userContext.isTryRemoteLogin) {
      console.log("componentDidMount 4")
      this.context.store.dispatch(securityManager.receiveLogin(
        _.merge({}, userContext, {
          isTryRemoteLogin: true
        })
      ));
      window.location.reload(); // so the change of remote login will make effect
    }
    if (!SecurityManager.isAuthenticated(userContext) && userContext.isTryRemoteLogin) {
      console.log("componentDidMount 4")
      this.context.store.dispatch(securityManager.remoteLogin((result, error) => {
        if (result) {
          console.log("componentDidMount 5")
          // ok - result = true
          this._tryRedirectLoggedUser();
        } else if (error && error.statusEnum && error.statusEnum === 'MUST_CHANGE_IDM_PASSWORD') {
          console.log("componentDidMount 5")
          // remember target url
          this._rememberUrl(userContext, location);
          // partialy ok - password has to be changed
          this.context.store.dispatch(securityManager.receiveLogin(
            _.merge({}, userContext, {
              isTryRemoteLogin: true // cancel password change => try remote login again
            })
          ), () => {
            this.context.history.replace(`/password/change`);
          });
        } else if (error && error.statusEnum && error.statusEnum === 'TWO_FACTOR_AUTH_REQIURED') { // look out - remember url cannot be overiden
          console.log("componentDidMount 6")
          // partialy ok - two factor is required
          this.setState({
            showTwoFactor: true,
            token: _casEnabled ? userContext.tokenCIDMST : error.parameters.token
          }, () => {
            this.context.store.dispatch(securityManager.receiveLogin(
              _.merge({}, userContext, {
                isTryRemoteLogin: true // cancel two factor => try remote login again
              })
            ));
            if (this.refs.verificationCode) {
              this.refs.verificationCode.focus();
            }
          });
        } else if (_casEnabled) {
          console.log("componentDidMount 7")
          // remember target url
          this._rememberUrl(userContext, location);
          // redirect to cas login request
          window.location.replace(RestApiService.getUrl('/authentication/cas-login-request'));
        } else {
          // remember target url
          this._rememberUrl(userContext, location);
        }
      }, userContext.tokenCIDMST));
    } else {
      console.log("componentDidMount 8")
      // redirect, if identity is logged => redirect to dashboard (#UNSAFE_componentWillReceiveProps is not called on the start)
      this._tryRedirectLoggedUser();
    }
  }

  _tryRedirectLoggedUser() {
    // Redirection to requested page before login.
    const { userContext, location } = this.props;
    let authenticated = !SecurityManager.isAuthenticated(userContext, (error) => {
      if (error && error.statusEnum && error.statusEnum === 'TWO_FACTOR_AUTH_REQIURED') { // look out - remember url cannot be overiden
        console.log("componentDidMount 6")
        // partialy ok - two factor is required
        this.setState({
          showTwoFactor: true,
          token: _casEnabled ? userContext.tokenCIDMST : error.parameters.token
        }, () => {
          this.context.store.dispatch(securityManager.receiveLogin(
            _.merge({}, userContext, {
              isTryRemoteLogin: true // cancel two factor => try remote login again
            })
          ));
          if (this.refs.verificationCode) {
            this.refs.verificationCode.focus();
          }
        });
    };
    });
    if (!authenticated) {
      return;
    }
    // If current url is login, then redirect to main page.
    const loginTargetPath = userContext.loginTargetPath || location.pathname;
    if (loginTargetPath === '/login') {
      this.context.history.replace('/', (error) => {
        if (error && error.statusEnum && error.statusEnum === 'TWO_FACTOR_AUTH_REQIURED') { // look out - remember url cannot be overiden
          console.log("componentDidMount 6")
          // partialy ok - two factor is required
          this.setState({
            showTwoFactor: true,
            token: _casEnabled ? userContext.tokenCIDMST : error.parameters.token
          }, () => {
            this.context.store.dispatch(securityManager.receiveLogin(
              _.merge({}, userContext, {
                isTryRemoteLogin: true // cancel two factor => try remote login again
              })
            ));
            if (this.refs.verificationCode) {
              this.refs.verificationCode.focus();
            }
          });
      };
      });
    } else {
      this.context.history.replace(loginTargetPath, (error) => {
        if (error && error.statusEnum && error.statusEnum === 'TWO_FACTOR_AUTH_REQIURED') { // look out - remember url cannot be overiden
          console.log("componentDidMount 6")
          // partialy ok - two factor is required
          this.setState({
            showTwoFactor: true,
            token: _casEnabled ? userContext.tokenCIDMST : error.parameters.token
          }, () => {
            this.context.store.dispatch(securityManager.receiveLogin(
              _.merge({}, userContext, {
                isTryRemoteLogin: true // cancel two factor => try remote login again
              })
            ));
            if (this.refs.verificationCode) {
              this.refs.verificationCode.focus();
            }
          });
      };
      });
    }
  }

  _rememberUrl(userContext, location) {
    // remember target url
    if (!userContext.isExpired) {
      this.context.store.dispatch(securityManager.receiveLogin(
        _.merge({}, userContext, {
          loginTargetPath: location.pathname
        })
      ));
    }
  }

  handleLogin(event) {
    console.log("handleLogin 1")
    if (event) {
      event.preventDefault();
    }
    if (!this.refs.form.isFormValid()) {
      console.log("handleLogin 2")
      return;
    }
    const { userContext, location } = this.props;
    const formData = this.refs.form.getData();
    const username = formData.username;
    const password = formData.password;
    //
    // check userame is the same, after expiration =>   clear target path, if not
    if (userContext.isExpired && username !== userContext.username) {
      console.log("handleLogin 3")
      this.context.store.dispatch(securityManager.receiveLogin(
        _.merge({}, userContext, { loginTargetPath: null })
      ));
    }
    //
    this.context.store.dispatch(securityManager.login(username, password, (result, error) => {
      console.log("handleLogin 4")
      if (error && error.statusEnum) {
        if (error.statusEnum === 'MUST_CHANGE_IDM_PASSWORD') {
          console.log("handleLogin 5")
          // remember target url
          this._rememberUrl(userContext, location);
          //
          this.context.store.dispatch(dataManager.storeData(SecurityManager.PASSWORD_MUST_CHANGE, password));
          this.context.history.replace(`/password/change?username=${ username }`);
        }
        if (error.statusEnum === 'TWO_FACTOR_AUTH_REQIURED') {
          console.log("handleLogin 6")
          this.setState({
            showTwoFactor: true,
            token: error.parameters.token,
            username,
            password
          }, () => {
            if (this.refs.verificationCode) {
              this.refs.verificationCode.focus();
            }
          });
        }
      }
    }));
  }

  handleTwoFactor(event) {
    console.log("handleTwoFactor 1")
    if (event) {
      event.preventDefault();
    }
    if (!this.refs.form.isFormValid()) {
      console.log("handleTwoFactor 2")
      return;
    }
    const formData = this.refs.form.getData();
    const verificationCode = formData.verificationCode;
    const { token } = this.state;
    this.context.store.dispatch(securityManager.loginTwoFactor({ token, verificationCode }, (result, error) => {
      if (error) {
        if (error.statusEnum && error.statusEnum === 'MUST_CHANGE_IDM_PASSWORD') {
          this.setState({
            showTwoFactor: false
          }, () => {
            this.context.store.dispatch(dataManager.storeData(SecurityManager.PASSWORD_MUST_CHANGE, this.state.password));
            if (this.state.username) {
              this.context.history.replace(`/password/change?username=${ this.state.username }`);
            } else {
              this.context.history.replace(`/password/change`);
            }
          });
        }
      } else {
        this.setState({
          showTwoFactor: false
        });
      }
    }));
  }

  handleCancel(event) {
    if (event) {
      event.preventDefault();
    }
    //
    this.context.history.push('/logout');
  }

  getHelp() {
    let helpContent = new HelpContent();
    helpContent = helpContent.setHeader(this.i18n('content.login.twoFactor.help.header'));
    helpContent = helpContent.setBody(
      <div>
        { this.i18n('content.login.twoFactor.help.content', { escape: false }) }
      </div>
    );
    //
    return helpContent;
  }

  render() {
    const { userContext, navigation, casEnabled } = this.props;
    const { showTwoFactor, nocas } = this.state;
    const _casEnabled = casEnabled && !nocas;
    const items = getNavigationItems(navigation, null, 'main', userContext, null, true);
    //
    return (
      <div>
        <Helmet title={ this.i18n('title') } />

        <Basic.Div rendered={ showTwoFactor }>
          <form onSubmit={ this.handleTwoFactor.bind(this) }>
            <Basic.Container component="main" maxWidth="xs">
              <Basic.Panel className="login-container" showLoading={ userContext.showLoading } style={{ maxWidth: 450 }}>
                <Basic.PanelHeader
                  text={ this.i18n('content.login.twoFactor.header') }
                  help={ this.getHelp() }/>
                <Basic.PanelBody>
                  <Basic.AbstractForm ref="form" className="form-horizontal" style={{ padding: 0, backgroundColor: '#fff' }}>
                    <Basic.TextField
                      ref="verificationCode"
                      labelSpan="col-sm-5"
                      componentSpan="col-sm-7"
                      className="last"
                      label={ this.i18n('content.login.twoFactor.verificationCode.label') }
                      placeholder={ this.i18n('content.login.twoFactor.verificationCode.placeholder') }
                      required
                      validation={ Joi.number().integer().min(0).max(999999) }/>
                  </Basic.AbstractForm>
                </Basic.PanelBody>
                <Basic.PanelFooter>
                  <Basic.Button level="link" onClick={ this.handleCancel.bind(this) }>
                    { this.i18n('button.cancel') }
                  </Basic.Button>
                  <Basic.Button type="submit" level="success">
                    { this.i18n('button.verify.label') }
                  </Basic.Button>
                </Basic.PanelFooter>
              </Basic.Panel>
            </Basic.Container>
          </form>
        </Basic.Div>

        {
          !showTwoFactor && _casEnabled
          ?
          <Basic.Loading className="global" showLoading />
          :
          <>
            <Basic.Div rendered={ !showTwoFactor }>
              <form onSubmit={ this.handleLogin.bind(this) }>
                <Basic.Container component="main" maxWidth="xs">
                  <div className="login-container">
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                      <Basic.Avatar level="secondary">
                        <Basic.Icon icon="component:password" />
                      </Basic.Avatar>
                      <h1 style={{ marginTop: 7, marginBottom: 15, fontWeight: 'normal' }}>
                        { this.i18n('header') }
                      </h1>
                    </div>
                    <Basic.Alert
                      level="info"
                      title={ this.i18n('error.LOG_IN.title') }
                      text={ this.i18n('error.LOG_IN.message') }
                      rendered={ userContext.isExpired }/>
                    <Basic.AbstractForm ref="form" className="form-horizontal" style={{ padding: 0 }} showLoading={ userContext.showLoading }>
                      <Basic.TextField
                        ref="username"
                        label={ this.i18n('username') }
                        required
                        fullWidth
                        size="normal"/>
                      <Basic.TextField
                        type="password"
                        ref="password"
                        label={ this.i18n('password') }
                        required
                        fullWidth
                        size="normal"/>

                      <Basic.Button
                        type="submit"
                        level="primary"
                        fullWidth
                        variant="contained"
                        showLoading={ userContext.showLoading }>
                        { this.i18n('button.login') }
                      </Basic.Button>

                      <div style={{ marginTop: 15, display: 'flex', justifyContent: 'space-between' }}>
                        {
                          items.map(item => {
                            if (item.to) {
                              return (
                                <Link
                                  to={ item.to }
                                  key={ `nav-item-${ item.id }` }
                                  onClick={ item.onClick }>
                                  <Basic.Icon value={ item.icon } style={{ marginRight: 5 }}/>
                                  { this.i18n(item.labelKey || item.label) }
                                </Link>
                              );
                            }
                            //
                            if (item.onClick) {
                              return (
                                <a
                                  href="#"
                                  key={ `nav-item-${ item.id }` }
                                  onClick={ item.onClick }>
                                  <Basic.Icon value={ item.icon } style={{ marginRight: 5 }}/>
                                  { this.i18n(item.labelKey || item.label) }
                                </a>
                              );
                            }
                            //
                            return null;
                          })
                        }
                      </div>
                    </Basic.AbstractForm>
                  </div>
                </Basic.Container>
              </form>
            </Basic.Div>
          </>
        }
      </div>
    );
  }
}

Login.propTypes = {
  ...Basic.AbstractContent.propTypes,
  userContext: PropTypes.object
};

Login.defaultProps = {
  ...Basic.AbstractContent.defaultProps,
  userContext: { isAuthenticated: false }
};

function select(state) {
  return {
    i18nReady: state.config.get('i18nReady'),
    userContext: state.security.userContext,
    navigation: state.config.get('navigation'),
    casEnabled: ConfigurationManager.getPublicValueAsBoolean(state, 'idm.pub.core.cas.enabled', false)
  };
}

export default connect(select)(Login);
