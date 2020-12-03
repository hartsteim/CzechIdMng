import React from 'react';
//
import { Advanced, Basic } from 'czechidm-core';

/**
 * Icon for MSSQL connector.
 *
 * @author Vít Švanda
 * @since 10.7.0
 */
export default class MsSqlConnectorIcon extends Advanced.AbstractIcon {

  renderIcon() {
    const {iconStyle} = this.props;
    if (iconStyle === 'sm') {
      return (
        <Basic.Div>
          <Basic.Icon style={{marginTop: '1px', minWidth: '25px', height: '25px'}} level="warning" className="fa-2x" type="fa" value="database"/>
        </Basic.Div>
      );
    }
    return (
      <Basic.Div>
        <Basic.Icon style={{marginTop: '8px', minWidth: '120px', height: '100px'}} level="warning" className="fa-6x" type="fa" value="database"/>
      </Basic.Div>
    );
  }
}
