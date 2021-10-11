import { Services, Domain } from 'czechidm-core';

export default class SyncItemLogService extends Services.AbstractService {

  // dto
  supportsPatch() {
    return false;
  }

  getNiceLabel(entity) {
    if (!entity) {
      return '';
    }
    return entity.displayName;
  }

  getApiPath() {
    return '/system-synchronization-item-logs';
  }

  supportsBulkAction() {
    return true;
  }

  getDefaultSearchParameters() {
    return super.getDefaultSearchParameters().setName(Domain.SearchParameters.NAME_QUICK).clearSort();
  }
}
