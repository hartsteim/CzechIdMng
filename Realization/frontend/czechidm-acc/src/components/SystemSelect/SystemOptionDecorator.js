import {Basic, Utils} from 'czechidm-core';
/**
 * Identity select option decorator.
 *
 * @author Radek Tomiška
 * @since 10.1.0
 */
export default class SystemOptionDecorator extends Basic.SelectBox.OptionDecorator {

  /**
   * Returns entity icon (null by default - icon will not be rendered)
   *
   * @param  {object} entity
   */
  getEntityIcon(entity) {
    if (!entity) {
      // default
      return 'component:system';
    }
    if (Utils.Entity.isDisabled(entity) || (entity._disabled && entity._disabled === true)) {
      // disabled (+ _disabled by not disableable select box)
      return 'component:disabled-system';
    }
    // enabled
    return 'component:enabled-system';
  }

}
