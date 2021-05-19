import AbstractService from './AbstractService';
import SearchParameters from '../domain/SearchParameters';
import RestApiService from './RestApiService';
import * as Utils from '../utils';

/**
 * Configuration for notifications.
 *
 * @author Radek Tomiška
 */
export default class NotificationService extends AbstractService {

  getApiPath() {
    return '/notification-configurations';
  }

  getNiceLabel(entity) {
    if (!entity) {
      return '';
    }
    return `${ entity.topic } - ${ entity.notificationType }`;
  }

  /**
   * Returns default searchParameters for current entity type
   *
   * @return {object} searchParameters
   */
  getDefaultSearchParameters() {
    return super.getDefaultSearchParameters().setName(SearchParameters.NAME_QUICK).clearSort().setSort('topic');
  }

  supportsPatch() {
    return false;
  }

  /**
   * Returns supported notification types
   *
   * @return {promise}
   */
  getSupportedNotificationTypes() {
    return RestApiService
      .get(`${ this.getApiPath() }/all/notification-types`)
      .then(response => {
        return response.json();
      })
      .then(json => {
        if (Utils.Response.hasError(json)) {
          throw Utils.Response.getFirstError(json);
        }
        return json;
      });
  }
}
