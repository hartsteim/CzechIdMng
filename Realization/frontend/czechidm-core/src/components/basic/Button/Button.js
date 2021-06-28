import React from 'react';
import PropTypes from 'prop-types';
import classnames from 'classnames';
//
import AbstractComponent from '../AbstractComponent/AbstractComponent';
import Icon from '../Icon/Icon';
import Tooltip from '../Tooltip/Tooltip';

/**
 * Basic button.
 *
 * @author Radek Tomiška
 */
class Button extends AbstractComponent {

  focus() {
    this.refs.button.focus();
  }

  render() {
    const {
      level,
      buttonSize,
      text,
      className,
      children,
      showLoading,
      showLoadingIcon,
      showLoadingText,
      disabled,
      hidden,
      type,
      rendered,
      title,
      titlePlacement,
      titleDelayShow,
      style,
      onClick,
      onDoubleClick,
      icon,
      tabIndex
    } = this.props;
    //
    if (!rendered) {
      return null;
    }
    let _level = level;
    if (_level) {
      _level = _level.toLowerCase();
    }
    //
    const classNames = classnames(
      'btn',
      `btn-${ (_level === 'error' ? 'danger' : _level) }`,
      { [`btn-${ buttonSize }`]: buttonSize !== 'default' },
      { hidden },
      className
    );
    let _showLoadingText = text || children;
    if (showLoadingText !== null) {
      _showLoadingText = showLoadingText;
    }
    //
    return (
      <Tooltip placement={ titlePlacement } value={ title } delayShow={ titleDelayShow }>
        <span>
          {/* button type is given dynamicaly */}
          {/* eslint-disable react/button-has-type */}
          <button
            ref="button"
            type={ type || 'button' }
            disabled={ disabled || showLoading }
            className={ classNames }
            style={ style }
            onClick={ onClick }
            onDoubleClick={ onDoubleClick }
            tabIndex={ tabIndex }>
            {
              showLoading
              ?
              <span>
                {
                  showLoadingIcon
                  ?
                  <Icon type="fa" icon="refresh" showLoading/>
                  :
                  <Icon value={ icon } className="icon-left"/>
                }
                {
                  showLoadingIcon && _showLoadingText
                  ?
                  '\u00a0'
                  :
                  null
                }
                {_showLoadingText}
              </span>
              :
              <span>
                <Icon
                  value={ icon }
                  className="icon-left"
                  style={ (text || (children && React.Children.count(children) > 0)) ? { marginRight: 5 } : {} }/>
                { text }
                { children }
              </span>
            }
          </button>
        </span>
      </Tooltip>
    );
  }
}

Button.propTypes = {
  ...AbstractComponent.propTypes,
  /**
   * Button level / css / class
   */
  level: PropTypes.oneOf(['default', 'success', 'warning', 'info', 'danger', 'error', 'link', 'primary']),
  /**
   * When showLoading is true, then showLoadingIcon is shown
   */
  showLoadingIcon: PropTypes.bool,
  /**
   *  When showLoading is true, this text will be shown
   */
  showLoadingText: PropTypes.string,
  /**
   * Title position
   */
  titlePlacement: PropTypes.oneOf(['top', 'bottom', 'right', 'left']),
  /**
   * Title show delay
   */
  titleDelayShow: PropTypes.number,
  /**
   * Button icon
   */
  icon: PropTypes.string,
  /**
   * On click node callback
   */
  onClick: PropTypes.func,
  /**
   * On double click node callback
   */
  onDoubleClick: PropTypes.func,
  /**
   * Button size (by bootstrap).
   *
   * @since 10.3.0
   */
  buttonSize: PropTypes.oneOf(['default', 'xs', 'sm', 'lg'])
};
Button.defaultProps = {
  ...AbstractComponent.defaultProps,
  type: 'button',
  level: 'default',
  buttonSize: 'default',
  hidden: false,
  showLoadingIcon: false,
  showLoadingText: null,
  titlePlacement: 'right',
  icon: null
};

export default Button;
