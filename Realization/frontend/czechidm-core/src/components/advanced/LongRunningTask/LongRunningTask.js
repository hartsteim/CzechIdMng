import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
//
import * as Basic from '../../basic';
import EntityInfo from '../EntityInfo/EntityInfo';
import OperationResult from '../OperationResult/OperationResult';
import OperationResultDownloadButton from '../OperationResult/OperationResultDownloadButton';
import ProgressBar from '../ProgressBar/ProgressBar';
import * as Utils from '../../../utils';
import OperationStateEnum from '../../../enums/OperationStateEnum';
import { LongRunningTaskManager, ConfigurationManager, SecurityManager, SchedulerManager, DataManager } from '../../../redux';
import LongRunningTaskName from './LongRunningTaskName';
import LongRunningTaskProperties from './LongRunningTaskProperties';

const manager = new LongRunningTaskManager();
const schedulerManager = new SchedulerManager();

/**
 * Long running task detail - progress bar.
 *
 * @author Radek Tomiška
 */
class LongRunningTask extends Basic.AbstractContent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      running: false
    };
  }

  getContentKey() {
    return 'content.scheduler.running-tasks';
  }

  componentDidMount() {
    const { entity, _entity, supportedTasks } = this.props;
    if (entity || _entity) {
      this.setRefresh(entity || _entity);
    }
    this._loadEntity();
    if (!supportedTasks && SecurityManager.hasAuthority('SCHEDULER_READ')) {
      this.context.store.dispatch(schedulerManager.fetchSupportedTasks());
    }
  }

  updateLrtState() {
    const { entityIdentifier, entity, _entity, onComplete } = this.props;

    let identifier = entityIdentifier;
    if (entity || _entity) {
      identifier = entity ? entity.id : _entity.id;
    }

    if (this._calledComponentWillUnmount) {
      this.safelyClearInterval();
    }

    this.context.store.dispatch(manager.autocompleteEntity(identifier, null, (task) => {
      if (task && OperationStateEnum.findSymbolByKey(task.resultState) !== OperationStateEnum.RUNNING) {
        this.safelyClearInterval();
        if (onComplete) {
          onComplete({ task });
        }
      }
    }));
  }

  componentDidUpdate() {
    this._loadEntity();
  }

  componentWillUnmount() {
    this.safelyClearInterval();
  }

  /**
   * Safely clear interval - check if exists value (updateIntervalId) and
   * call clearInterval. Method also clear value.
   */
  safelyClearInterval() {
    if (this.updateIntervalId !== undefined && this.updateIntervalId !== null) {
      clearInterval(this.updateIntervalId);
      this.updateIntervalId = null;
    }
  }

  /**
   * if id is setted and entity is not - then load entity
   */
  _loadEntity() {
    const { entity, _entity, entityIdentifier } = this.props;
    if (entityIdentifier && !entity && !_entity) {
      const uiKey = manager.resolveUiKey(null, entityIdentifier);
      if (!Utils.Ui.isShowLoading(this.context.store.getState(), uiKey)
          && !Utils.Ui.getError(this.context.store.getState(), uiKey)) { // show loading check has to be here - new state is needed
        this.context.store.dispatch(manager.autocompleteEntity(entityIdentifier, null, (task) => {
          this.setRefresh(task);
        }));
      }
    }
  }

  onCancel(task) {
    const { supportedTasks, _supportedTasks } = this.props;
    // show confirm message for deleting entity or entities
    this.refs['confirm-cancel'].show(
      this.i18n(`action.task-cancel.message`, { record: manager.getNiceLabel(task, supportedTasks || _supportedTasks) }),
      this.i18n(`action.task-cancel.header`)
    ).then(() => {
      const uiKey = manager.resolveUiKey(null, task.id);
      this.context.store.dispatch(manager.cancel(task, uiKey, () => {
        this.addMessage({
          level: 'info',
          message: this.i18n(`action.task-cancel.success`, {
            record: manager.getNiceLabel(task, supportedTasks || _supportedTasks)
          })
        });
      }));
    }, () => {
      //
    });
  }

  onInterrupt(task) {
    const { supportedTasks, _supportedTasks } = this.props;
    // show confirm message for deleting entity or entities
    this.refs['confirm-interrupt'].show(
      this.i18n(`action.task-interrupt.message`, { record: manager.getNiceLabel(task, supportedTasks || _supportedTasks) }),
      this.i18n(`action.task-interrupt.header`)
    ).then(() => {
      const uiKey = manager.resolveUiKey(null, task.id);
      this.context.store.dispatch(manager.interrupt(task, uiKey, () => {
        this.addMessage({
          level: 'info',
          message: this.i18n(`action.task-interrupt.success`, {
            record: manager.getNiceLabel(task, supportedTasks || _supportedTasks)
          })
        });
      }));
    }, () => {
      //
    });
  }

  /**
   * Shows modal detail with given entity
   */
  showDetail(entity) {
    this.context.history.push(`/scheduler/all-tasks/${ encodeURIComponent(entity.id) }/detail`);
  }

  setRefresh(task) {
    const { updateInterval, onComplete } = this.props;
    if (task && OperationStateEnum.findSymbolByKey(task.resultState) === OperationStateEnum.RUNNING) {
      this.updateIntervalId = setInterval(this.updateLrtState.bind(this), updateInterval);
    } else if (onComplete) { // task ended right after he was ececuted
      onComplete({ task });
    }
  }

  getEntity() {
    const { entity, _entity } = this.props;
    //
    if (entity) { // entity is given by props
      return entity;
    }
    return _entity; // loaded by redux
  }

  _renderCount() {
    const entity = this.getEntity();
    //
    if (!entity.successItemCount && !entity.warningItemCount && !entity.failedItemCount) {
      return (
        <span>{ manager.getProcessedCount(entity) }</span>
      );
    }
    return (
      <span>
        <Basic.Label
          level="success"
          value={ entity.successItemCount }
          style={{ marginRight: 3 }}
          title={ this.i18n('entity.LongRunningTask.successItemCount.help') }/>
        <Basic.Label
          level="warning"
          value={ entity.warningItemCount }
          style={{ marginRight: 3 }}
          title={ this.i18n('entity.LongRunningTask.warningItemCount.help') }/>
        <Basic.Label
          level="danger"
          value={ entity.failedItemCount }
          style={{ marginRight: 3 }}
          title={ this.i18n('entity.LongRunningTask.failedItemCount.help') }/>
        <span
          title={ this.i18n('entity.LongRunningTask.count') }
          className={ !entity.count ? 'hidden' : '' }>
          { `/ ${entity.count}` }
        </span>
      </span>
    );
  }

  _renderInfoComponent(value) {
    if (!value) {
      return null;
    }
    const model = value.model;
    if (model && model.statusCode === OperationResult.PARTIAL_CONTENT_STATUS) {
      const parameters = model.parameters;
      if (!parameters || !parameters.ownerType || !parameters.ownerId) {
        return null;
      }

      return (
        <EntityInfo
          entityType={ Utils.Ui.getSimpleJavaType(parameters.ownerType) }
          entityIdentifier={ parameters.ownerId }
          showDefaultEntityInfo={ false }
          face="full"
          style={{ maxWidth: '100%', marginTop: 15, marginBottom: 0 }}/>
      );
    }
    return null;
  }

  _renderFull() {
    const {
      instanceId,
      _showLoading,
      header,
      footerButtons,
      showProperties,
      supportedTasks,
      _supportedTasks,
      style
    } = this.props;
    const _entity = this.getEntity();
    //
    const active = OperationStateEnum.findSymbolByKey(_entity.resultState) === OperationStateEnum.RUNNING;
    return (
      <Basic.Panel showLoading={ _showLoading } style={ style }>
        <Basic.Confirm ref="confirm-cancel" level="warning"/>
        <Basic.Confirm ref="confirm-interrupt" level="danger"/>
        <Basic.PanelHeader text={
          header
          ||
          <span>
            <LongRunningTaskName entity={ _entity } supportedTasks={ supportedTasks || _supportedTasks } showTaskType={ false }/>
            {' '}
            <small>{ _entity.taskDescription }</small>
          </span>
        } />
        <Basic.PanelBody>
          {
            !showProperties
            ||
            <LongRunningTaskProperties
              key={ `lrt-eav-${ _entity.id }` }
              entity={ _entity }
              supportedTasks={ supportedTasks || _supportedTasks } />
          }
          <ProgressBar
            style={{ marginTop: 15, marginBottom: 0 }}
            active={ active }
            max={ _entity.count }
            now={ _entity.counter }
            bars={[
              { now: _entity.failedItemCount, bsStyle: 'danger' },
              { now: _entity.warningItemCount, bsStyle: 'warning' },
              {
                now:
                  (_entity.counter > 0 && !_entity.successItemCount && !_entity.warningItemCount && !_entity.failedItemCount)
                  ?
                  _entity.counter
                  :
                  _entity.successItemCount,
                bsStyle:
                  _entity && _entity.result && _entity.result.state === 'EXCEPTION'
                  ?
                  'danger'
                  :
                  'success'
              }
            ]}/>
          { this._renderInfoComponent(_entity ? _entity.result : null)}
        </Basic.PanelBody>
        <Basic.PanelFooter>
          { footerButtons }
          <Basic.Button
            onClick={ this.onInterrupt.bind(this, _entity) }
            level="danger"
            style={{ marginRight: 5 }}
            rendered={ _entity.instanceId === instanceId && SecurityManager.hasAnyAuthority(['SCHEDULER_UPDATE']) && active }
            disabled={ _showLoading }
            icon="fa:bolt">
            { this.i18n('button.interrupt') }
          </Basic.Button>
          <Basic.Button
            level="warning"
            onClick={ this.onCancel.bind(this, _entity) }
            style={{ marginRight: 5 }}
            rendered={ SecurityManager.hasAnyAuthority(['SCHEDULER_UPDATE']) && active }
            disabled={_showLoading}>
            { this.i18n('button.cancel') }
          </Basic.Button>
          <OperationResultDownloadButton
            operationResult={ _entity ? _entity.result : null }
            style={{ marginRight: 5 }}
            btnSize=""
          />
          <Basic.Button
            title={ this.i18n('button.detail') }
            onClick={ this.showDetail.bind(this, _entity) }
            rendered={ SecurityManager.hasAnyAuthority(['SCHEDULER_READ']) }>
            { this.i18n('button.detail') }
          </Basic.Button>
        </Basic.PanelFooter>
      </Basic.Panel>
    );
  }

  render() {
    const {
      rendered,
      entityIdentifier,
      entity,
      face
    } = this.props;
    //
    let _entity = this.props._entity;
    if (entity) { // entity prop has higher priority
      _entity = entity;
    }
    //
    if (!rendered || (!_entity && !entityIdentifier)) {
      return null;
    }
    if (!_entity) {
      return (
        <Basic.Panel>
          <Basic.Loading isStatic show />
        </Basic.Panel>
      );
    }
    switch (face) {
      case 'count': {
        return this._renderCount();
      }
      default: {
        return this._renderFull();
      }
    }
  }
}

LongRunningTask.propTypes = {
  rendered: PropTypes.bool,
  instanceId: PropTypes.string,
  entity: PropTypes.object,
  entityIdentifier: PropTypes.string,
  header: PropTypes.oneOfType([
    PropTypes.string,
    PropTypes.node
  ]),
  footerButtons: PropTypes.arrayOf(PropTypes.object),
  updateInterval: PropTypes.number,
  /**
   * Show task properties
   */
  showProperties: PropTypes.bool,
  /**
   * Externally loaded supported tasks
   */
  supportedTasks: PropTypes.arrayOf(PropTypes.object),
  /**
   * Internal entity loaded by given identifier
   */
  _entity: PropTypes.object,
  _showLoading: PropTypes.bool,
  /**
   * Decorator
   */
  face: PropTypes.oneOf(['count', 'full']),
  /**
   * When task is completed, this callback will be called. Processed task is given as func param.
   */
  onComplete: PropTypes.func
};
LongRunningTask.defaultProps = {
  rendered: true,
  instanceId: null,
  entity: null,
  entityIdentifier: null,
  header: null,
  footerButtons: null,
  updateInterval: 2500,
  showProperties: true,
  //
  _entity: null,
  _showLoading: false,
  face: 'full'
};

function select(state, component) {
  const _id = component.entityIdentifier || (!component.entity ? null : component.entity.id);
  //
  const _entity = manager.getEntity(state, _id);
  let _showLoading = manager.isShowLoading(state, null, _id);
  if (_showLoading && _entity && OperationStateEnum.findSymbolByKey(_entity.resultState) === OperationStateEnum.RUNNING) {
    _showLoading = false;
  }
  //
  return {
    instanceId: ConfigurationManager.getPublicValue(state, 'idm.pub.app.instanceId'),
    _entity,
    _showLoading,
    _supportedTasks: DataManager.getData(state, SchedulerManager.UI_KEY_SUPPORTED_TASKS)
  };
}

export default connect(select)(LongRunningTask);
