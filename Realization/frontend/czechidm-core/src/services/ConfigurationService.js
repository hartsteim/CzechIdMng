import AbstractService from './AbstractService';
import RestApiService from './RestApiService';
import SearchParameters from '../domain/SearchParameters';
import * as Utils from '../utils';
import PlainTextApi from './PlainTextApi';

/**
 * Application configuration.
 *
 * @author Radek Tomiška
 */
export default class ConfigurationService extends AbstractService {

  getApiPath() {
    return '/configurations';
  }

  getNiceLabel(entity) {
    if (!entity) {
      return '';
    }
    return entity.name;
  }

  supportsPatch() {
    return false;
  }

  supportsAuthorization() {
    return true;
  }

  supportsBulkAction() {
    return true;
  }

  getGroupPermission() {
    return 'CONFIGURATION';
  }

  /**
   * Returns default searchParameters for current entity type
   *
   * @return {object} searchParameters
   */
  getDefaultSearchParameters() {
    return super.getDefaultSearchParameters().setName(SearchParameters.NAME_QUICK).clearSort().setSort('name', 'asc');
  }

  /**
   * Returns all public configurations
   *
   * @return Promise
   */
  getPublicConfigurations() {
    return RestApiService
      .get(RestApiService.getUrl(`/public${ this.getApiPath() }`))
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

  /**
   * Returns all configurations from property files
   *
   * @return Promise
   */
  getAllConfigurationsFromFile() {
    return RestApiService
      .get(`${ this.getApiPath() }/all/file`)
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

  /**
   * @Beta
   * Return monitoring results for given type.
   *
   * @return Promise
   * @deprecated since 11.1.0
   */
  getMonitoringType(monitoringType) {
    return RestApiService
      .get(`${ this.getApiPath() }/monitoring-types/${ monitoringType }`)
      .then(response => {
        if (response.status === 204) {
          return null;
        }
        return response.json();
      })
      .then(json => {
        if (!json) {
          return null;
        }
        if (Utils.Response.hasError(json)) {
          throw Utils.Response.getFirstError(json);
        }
        return json;
      });
  }

  /**
   * Returns all configurations from property files
   *
   * @return Promise
   */
  getAllConfigurationsFromEnvironment() {
    return RestApiService
      .get(`${ this.getApiPath() }/all/environment`)
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

  /**
   * Creates more configuration's items
   *
   * @param {text}
   * @return Promise
   */
  addMoreEntities(text) {
    return PlainTextApi.put(`${ this.getApiPath() }/bulk/save`, text)
      .then(response => {
        if (response.status === 204) { // no content - ok
          return null;
        }
        return response.json();
      })
      .then(jsonResponse => {
        if (Utils.Response.hasError(jsonResponse)) {
          throw Utils.Response.getFirstError(jsonResponse);
        }
        if (Utils.Response.hasInfo(jsonResponse)) {
          throw Utils.Response.getFirstInfo(jsonResponse);
        }
        return jsonResponse;
      });
  }

  /**
   * Loads all registered read dto services.
   *
   * @return {promise}
   * @since 11.1.0
   */
  getReadDtoServices() {
    return RestApiService
      .get(`${ this.getApiPath() }/search/read-dto-services`)
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

  /**
   * Get application logo from BE.
   *
   * @since 12.0.0
   */
  downloadApplicationLogo() {
    return RestApiService.download(`/public${ this.getApiPath() }/application/logo`);
  }

  /**
   * Upload application logo to BE.
   *
   * @since 12.0.0
   */
  uploadApplicationLogo(formData) {
    return RestApiService
      .upload(`${ this.getApiPath() }/application/logo`, formData)
      .then(response => {
        if (response.status === 200) { // no content - ok
          return null;
        }
        return response.json();
      })
      .then(json => {
        if (Utils.Response.hasError(json)) {
          throw Utils.Response.getFirstError(json);
        }
        if (Utils.Response.hasInfo(json)) {
          throw Utils.Response.getFirstInfo(json);
        }
        return json;
      });
  }

  /**
   * Delete application logo from BE.
   *
   * @since 12.0.0
   */
  deleteApplicationLogo() {
    return RestApiService
      .delete(`${ this.getApiPath() }/application/logo`)
      .then(response => {
        if (response.status === 204) { // no content - ok
          return null;
        }
        return response.json();
      })
      .then(json => {
        if (Utils.Response.hasError(json)) {
          throw Utils.Response.getFirstError(json);
        }
        if (Utils.Response.hasInfo(json)) {
          throw Utils.Response.getFirstInfo(json);
        }
        return json;
      });
  }

  /**
   * Get configured application theme.
   *
   * @return {promise}
   * @since 12.0.0
   */
  getApplicationTheme(type = 'light') {
    return RestApiService
      .get(`/public${ this.getApiPath() }/application/theme?type=${ type }`)
      .then(response => {
        if (response.status === 204) { // no content - ok
          return {}; // empty theme
        }
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
