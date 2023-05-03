import AbstractRequestService from './AbstractRequestService';
import SearchParameters from '../domain/SearchParameters';
import RoleService from './RoleService';
import {i18n} from './LocalizationService';

const roleService = new RoleService();

/**
 * Role composition - define busimess role
 *
 * @author Radek Tomiška
 */
export default class RoleCompositionService extends AbstractRequestService {

  /**
   * Using in the request
   */
  getSubApiPath() {
    return '/role-compositions';
  }

  getNiceLabel(entity) {
    if (!entity || !entity._embedded || !entity._embedded.superior || !entity._embedded.sub) {
      return i18n('entity.RoleComposition._type');
    }
    let label = `${roleService.getNiceLabel(entity._embedded.superior)}`;
    label += ` - ${roleService.getNiceLabel(entity._embedded.sub)}`;
    //
    return label;
  }

  supportsPatch() {
    if (this.isRequestModeEnabled()) {
      return false;
    }
    return true;
  }

  getGroupPermission() {
    return 'ROLECOMPOSITION';
  }

  supportsBulkAction() {
    return true;
  }

  /**
   * Returns default searchParameters for current entity type
   *
   * @return {object} searchParameters
   */
  getDefaultSearchParameters() {
    return super.getDefaultSearchParameters().setName(SearchParameters.NAME_QUICK).clearSort().setSort('superior.name', 'asc').setSort('sub.name', 'asc');
  }
}
