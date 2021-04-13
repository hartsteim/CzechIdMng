import React from 'react';
import TestUtils from 'react-dom/test-utils';
import ShallowRenderer from 'react-test-renderer/shallow';
import Immutable from 'immutable';
import chai, { expect } from 'chai';
import dirtyChai from 'dirty-chai';
//
import { Basic, Advanced } from 'czechidm-core';
import { App } from '../../src/layout/App';
//
chai.use(dirtyChai);

describe('App', function appTestSuite() {
  describe.skip('[without redux connected]', function appTest() {
    it('- without setting loding is rendered', function test() {
      const shallowRenderer = new ShallowRenderer();
      shallowRenderer.render(<App location={{}} userContext={{ username: 'johndoe'}}/>);
      const app = shallowRenderer.getRenderOutput();
      expect(app.props.id).to.equal('content-wrapper');
      expect(app.props.children.length).to.equal(2);
      expect(app.props.children[1].type).to.equal(Basic.Loading); // setting is not filled
    });

    it('- should contain flash messages', function test() {
      const shallowRenderer = new ShallowRenderer();
      shallowRenderer.render(<App location={{}} userContext={{ username: 'johndoe'}}/>);
      const app = shallowRenderer.getRenderOutput();
      const flashMessages = app.props.children.find(c => c && c.type === Basic.FlashMessages);
      expect(flashMessages).to.not.be.undefined();
    });

    it('- with setting is rendered navigation, body and footer', function test() {
      let setting = new Immutable.Map({});
      setting = setting.set('1', {});
      //
      const shallowRenderer = new ShallowRenderer();
      shallowRenderer.render(<App location={{}} setting={setting} userContext={{ username: 'johndoe'}}/>);
      const app = shallowRenderer.getRenderOutput();
      expect(app.props.id).to.equal('content-wrapper');
      expect(app.props.children.length).to.equal(2);
      expect(app.props.children[1].type).to.equal('div');
      expect(app.props.children[1].props.children[0].type).to.equal(Advanced.Navigation);
      expect(app.props.children[1].props.children[1].props.id).to.equal('content-container');
    });
  });
});
