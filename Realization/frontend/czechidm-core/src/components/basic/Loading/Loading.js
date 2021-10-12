import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import $ from 'jquery';
import ReactResizeDetector from 'react-resize-detector';
//
import * as Utils from '../../../utils';
import AbstractComponent from '../AbstractComponent/AbstractComponent';
import AbstractContextComponent from '../AbstractContextComponent/AbstractContextComponent';

/**
 * Loading indicator.
 *
 * ! Be careful: prevent to use Basic.Div inside => cicrular reference.
 *
 * @author Radek Tomiška
 */
class Loading extends AbstractContextComponent {

  constructor(props, context) {
    super(props, context);
    //
    this.containerRef = React.createRef();
    this.state = {
      ...this.state,
      theme: (
        context && context.store
        ?
        Utils.Ui.getTheme(context.store.getState())
        :
        null
      )
    };
  }

  componentDidMount() {
    this._resize();
  }

  componentDidUpdate() {
    this._resize();
  }

  _showLoading() {
    const { showLoading, show } = this.props;
    //
    return showLoading || show;
  }

  _resize() {
    const showLoading = this._showLoading();
    if (!showLoading) {
      return;
    }
    const panel = $(this.containerRef.current);
    const loading = panel.find('.loading');
    if (loading.hasClass('global') || loading.hasClass('static')) {
      // we don't want resize loading container
      return;
    }
    loading.css({
      top: panel.position().top,
      left: panel.position().left,
      width: panel.outerWidth(),
      height: panel.outerHeight()
    });
  }

  render() {
    const {
      rendered,
      className,
      containerClassName,
      showAnimation,
      isStatic,
      loadingTitle,
      style,
      containerTitle,
      onClick,
      ...others
    } = this.props;
    const { theme } = this.state;
    //
    if (!rendered) {
      return null;
    }
    const showLoading = this._showLoading();
    //
    // Loading is used as standard div => wee need to render css even if loading is not active
    const _containerClassNames = classNames(
      'loader-container',
      containerClassName
    );
    const loaderClassNames = classNames(
      className,
      'loading',
      { hidden: !showLoading },
      { static: isStatic }
    );
    // onClick required props
    others.onClick = onClick;
    others.tabIndex = others.tabIndex || (onClick ? 0 : null);
    others.role = others.role || (onClick ? 'button' : null);
    //
    return (
      <div
        ref={ this.containerRef }
        className={ _containerClassNames }
        style={ style }
        title={ containerTitle }
        { ...others }>
        {
          showLoading
          ?
          <div
            className={ loaderClassNames }
            style={
              theme
              ?
              { backgroundColor: theme.palette.action.loading }
              :
              {}
            }>
            <div className="loading-wave-top" />
            {
              showAnimation
              ?
              <div className="loading-wave-container" title={ loadingTitle }>
                <div className="loading-wave hidden">
                  <div/><div/><div/><div/><div/>
                </div>
                <div className="loading-logo">
                  <div/><div/><div/><div/><div/><div/><div/><div/><div/>
                </div>
              </div>
              :
              null
            }
            <div className="title hidden">{ loadingTitle }</div>
          </div>
          :
          null
        }
        { this.props.children }
      </div>
    );
  }
}

class ResizeLoading extends AbstractComponent {
  render() {
    const { rendered, ...others } = this.props;
    if (!rendered) {
      return null;
    }
    if (global.TEST_PROFILE === true) { // fully initialize DOM is required for ReactResizeDetector => not available in tests
      return (
        <Loading { ...others } />
      );
    }
    //
    return (
      <ReactResizeDetector
        handleHeight
        render={ ({ height }) => (
          <Loading height={ height } { ...others } />
        )}/>
    );
  }
}

Loading.propTypes = {
  ...AbstractContextComponent.propTypes,
  /**
   * Shows loading overlay (showLoadin alias)
   */
  show: PropTypes.bool,
  /**
   * when loading is visible, then show animation too
   */
  showAnimation: PropTypes.bool,
  /**
   * static loading without overlay
   */
  isStatic: PropTypes.bool,
  /**
   * Loading title
   */
  loadingTitle: PropTypes.string,
  /**
   * Title - static container (div wrapper).
   */
  containerTitle: PropTypes.string,
  /**
   * Css - static container (div wrapper).
   */
  containerClassName: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.func
  ])
};
Loading.defaultProps = {
  ...AbstractContextComponent.defaultProps,
  show: false,
  showAnimation: true,
  isStatic: false,
  loadingTitle: 'Zpracovávám ...' // TODO: localization or undefined ?
};

export default ResizeLoading;
