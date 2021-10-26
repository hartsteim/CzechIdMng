//
// Aplication entry point
import React from 'react';
import ReactDOM from 'react-dom';
import Helmet from 'react-helmet';
import { HashRouter as Router, Route } from 'react-router-dom';
import { Provider } from 'react-redux';
import Promise from 'es6-promise';
//
import { Managers, Basic, ConfigActions, Services } from 'czechidm-core';
//
// this parts are genetater dynamicaly to dist - after build will be packed by browserify to sources
import IdmContext from 'czechidm-core/src/context/idm-context';
import config from '../dist/config.json';
import { moduleDescriptors } from '../dist/modules/moduleAssembler';
import { componentDescriptors } from '../dist/modules/componentAssembler';
// application routes root
import App from './layout/App';
import store from './store';

// global promise init
// TODO: https://github.com/qubyte/fetch-ponyfill
Promise.polyfill();

store.dispatch(ConfigActions.appInit(config, moduleDescriptors, componentDescriptors, (error) => {
  if (!error) {
    // We need to init routes after configuration will be loaded
    const routes = require('./routes').default;
    // App entry point
    // https://material-ui.com/customization/palette/
    ReactDOM.render(
      <Provider store={ store }>
        <IdmContext.Provider value={{ store, routes }}>
          <Router>
            <Route path="/" component={ App } />
          </Router>
        </IdmContext.Provider>
      </Provider>,
      document.getElementById('content')
    );
  } else {
    const flashManager = new Managers.FlashMessagesManager();
    if (store) {
      const logger = store.getState().logger;
      logger.error(`[APP INIT]: Error during app init:`, error);
    }
    ReactDOM.render(
      <div style={{ margin: 15 }}>
        <Helmet title="503" />
        <Basic.Container component="main" maxWidth="md">
          <Basic.FlashMessage
            icon="exclamation-sign"
            message={ flashManager.convertFromError(error) }
            buttons={[
              <Basic.Button
                icon="fa:refresh"
                onClick={() => document.location.reload() }>
                { Services.LocalizationService.i18n('button.refresh') }
              </Basic.Button>
            ]}/>
        </Basic.Container>
      </div>,
      document.getElementById('content')
    );
  }
}));
