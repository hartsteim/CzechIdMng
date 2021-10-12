import React from 'react';
import PropTypes from 'prop-types';
//
import UiUtils from '../../../utils/UiUtils';
import AbstractComponent from '../AbstractComponent/AbstractComponent';

/**
 * Simple component to make text shorter.
 *
 * @author Marek Klement
 * @author Radek Tomiška
 */
export default function ShortText(props) {
  const { text, value, maxLength, cutPointEnd, cutChar, className, rendered, style, title } = props;
  const _text = text || value;
  //
  if (!rendered || !_text) {
    return null;
  }
  let shortText = '';
  if (maxLength !== null && _text.length > maxLength) {
    if (cutPointEnd) {
      shortText = UiUtils.substringBegin(_text, maxLength, cutChar, '...');
    } else {
      shortText = UiUtils.substringEnd(_text, maxLength, cutChar, '...');
    }
  } else {
    shortText = _text;
  }
  return (
    <span title={ title || _text} className={ className } style={ style }>
      { shortText }
    </span>
  );
}

ShortText.propTypes = {
  rendered: PropTypes.bool,
  /**
   * String to be shorten
   */
  text: PropTypes.string,
  /**
   * String to be shorten (text alias - text has higher priority)
   */
  value: PropTypes.string,
  /**
   * Number of lenght for string to be shorten to
   */
  maxLength: PropTypes.number,
  /**
   * Character acording which you cut
   */
  cutChar: PropTypes.string,
  /**
   * Boolean that indicates if you cut from begining or ending
   */
  cutPointEnd: PropTypes.bool,
  /**
   * Html title.
   *
   * @since 12.0.0
   */
  title: PropTypes.string
};

ShortText.defaultProps = {
  ...AbstractComponent.defaultProps,
  /**
   * default shorten value of maxLength
   */
  maxLength: 20,
  /**
   * default character for cutting
   */
  cutChar: ' ',
  /**
   * default point of cutting
   */
  cutPointEnd: true
};
