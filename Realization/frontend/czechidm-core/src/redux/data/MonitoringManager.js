import Immutable from 'immutable';
//
import * as Utils from '../../utils';
import EntityManager from './EntityManager';
import DataManager from './DataManager';
import FormAttributeManager from './FormAttributeManager';
import { MonitoringService } from '../../services';
//
const formAttributeManager = new FormAttributeManager();

/**
 * Configured monitoring evaluators.
 *
 * @author Radek Tomiška
 * @since 11.1.0
 */
export default class MonitoringManager extends EntityManager {

  constructor() {
    super();
    this.service = new MonitoringService();
  }

  getService() {
    return this.service;
  }

  getEntityType() {
    return 'Monitoring';
  }

  getCollectionType() {
    return 'monitorings';
  }

  getNiceLabel(entity, supportedEvaluators = null, showEvaluatorType = true) {
    let _taskType;
    if (supportedEvaluators && supportedEvaluators.has(entity.evaluatorType)) {
      _taskType = supportedEvaluators.get(entity.evaluatorType);
    }
    if (_taskType && _taskType.formDefinition) {
      const simpleTaskType = Utils.Ui.getSimpleJavaType(entity.evaluatorType);
      let _label = formAttributeManager.getLocalization(_taskType.formDefinition, null, 'label', simpleTaskType);
      if (showEvaluatorType && _label !== simpleTaskType) {
        _label += ` (${ simpleTaskType })`;
      }
      return _label;
    }
    return this.getService().getNiceLabel(entity);
  }

  /**
   * Loads all registered evaluators.
   *
   * @return {action}
   */
  fetchSupportedEvaluators(cb = null) {
    const uiKey = MonitoringManager.UI_KEY_SUPPORTED_EVALUATORS;
    //
    return (dispatch, getState) => {
      const loaded = DataManager.getData(getState(), uiKey);
      if (loaded) {
        // we dont need to load them again - change depends on BE restart
        if (cb) {
          cb(loaded);
        }
      } else {
        dispatch(this.dataManager.requestData(uiKey));
        this.getService().getSupportedEvaluators()
          .then(json => {
            let evaluators = new Immutable.Map();
            if (json._embedded && json._embedded.monitoringEvaluators) {
              json._embedded.monitoringEvaluators.forEach(item => {
                evaluators = evaluators.set(item.evaluatorType, item);
              });
            }
            dispatch(this.dataManager.receiveData(uiKey, evaluators, cb));
          })
          .catch(error => {
            // TODO: data uiKey
            dispatch(this.dataManager.receiveError(null, uiKey, error, cb));
          });
      }
    };
  }
}

MonitoringManager.UI_KEY_SUPPORTED_EVALUATORS = 'authorization-supported-evaluators';
