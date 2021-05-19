import FormableEntityService from './FormableEntityService';
import TreeNodeService from './TreeNodeService';
import IdentityService from './IdentityService';
import SearchParameters from '../domain/SearchParameters';
import * as Utils from '../utils';

/**
 * Contract slices
 *
 * @author Vít Švanda
 */
class ContractSliceService extends FormableEntityService {

  constructor() {
    super();
    this.treeNodeService = new TreeNodeService();
    this.identityService = new IdentityService();
  }

  getApiPath() {
    return '/contract-slices';
  }

  supportsPatch() {
    return false;
  }

  supportsAuthorization() {
    return true;
  }

  getGroupPermission() {
    return 'IDENTITYCONTRACT';
  }

  supportsAttachment() {
    return true;
  }

  /**
   * Extended nice label
   *
   * @param  {entity} entity
   * @param  {boolean} showIdentity identity will be rendered.
   * @return {string}
   */
  getNiceLabel(entity, showIdentity = true) {
    if (!entity) {
      return '';
    }
    if (!entity._embedded) {
      return entity.position || entity.id;
    }
    let niceLabel = null;
    if (showIdentity && entity._embedded.identity) {
      niceLabel = this.identityService.getNiceLabel(entity._embedded.identity);
    }
    let positionName = entity.position;
    if (entity._embedded.workPosition) {
      positionName = this.treeNodeService.getNiceLabel(entity._embedded.workPosition);
    }
    if (positionName === null) {
      positionName = 'default'; // TODO: locale or make at least one of position / tree node required!
    }
    return niceLabel ? `${niceLabel} - ${positionName}` : positionName;
  }

  /**
   * Returns default searchParameters for current entity type
   *
   * @return {object} searchParameters
   */
  getDefaultSearchParameters() {
    return super.getDefaultSearchParameters().setName(SearchParameters.NAME_QUICK).clearSort().setSort('validFrom', 'desc');
  }

  isValid(contractSlice) {
    return Utils.Entity.isValid(contractSlice);
  }
}

export default ContractSliceService;
