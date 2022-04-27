import { Enums } from 'czechidm-core';

/**
 * SynchronizationLinkedActionType for synchronization.
 */
export default class SynchronizationLinkedActionTypeEnum extends Enums.AbstractEnum {

  static getNiceLabel(key) {
    return super.getNiceLabel(`acc:enums.SynchronizationLinkedActionTypeEnum.${key}`);
  }

  static findKeyBySymbol(sym) {
    return super.findKeyBySymbol(this, sym);
  }

  static findSymbolByKey(key) {
    return super.findSymbolByKey(this, key);
  }

  static getLevel(key) {
    if (!key) {
      return null;
    }

    const sym = super.findSymbolByKey(this, key);

    switch (sym) {
      case this.UPDATE_ENTITY: {
        return 'success';
      }
      case this.UPDATE_ACCOUNT: {
        return 'success';
      }
      case this.UNLINK_AND_REMOVE_ROLE: {
        return 'danger';
      }
      case this.UNLINK: {
        return 'warning';
      }
      case this.IGNORE: {
        return 'primary';
      }
      case this.IGNORE_AND_DO_NOT_LOG: {
        return 'primary';
      }
      default: {
        return 'default';
      }
    }
  }
}

SynchronizationLinkedActionTypeEnum.UPDATE_ENTITY = Symbol('UPDATE_ENTITY');
SynchronizationLinkedActionTypeEnum.UPDATE_ACCOUNT = Symbol('UPDATE_ACCOUNT');
SynchronizationLinkedActionTypeEnum.UNLINK = Symbol('UNLINK');
SynchronizationLinkedActionTypeEnum.UNLINK_AND_REMOVE_ROLE = Symbol('UNLINK_AND_REMOVE_ROLE');
SynchronizationLinkedActionTypeEnum.IGNORE = Symbol('IGNORE');
SynchronizationLinkedActionTypeEnum.IGNORE_AND_DO_NOT_LOG = Symbol('IGNORE_AND_DO_NOT_LOG');
