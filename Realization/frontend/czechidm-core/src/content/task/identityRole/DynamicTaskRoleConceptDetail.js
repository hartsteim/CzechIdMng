import PropTypes from 'prop-types';
import React from 'react';
import Helmet from 'react-helmet';
import _ from 'lodash';
import { connect } from 'react-redux';
//
import * as Basic from '../../../components/basic';
import * as Advanced from '../../../components/advanced';
import DecisionButtons from '../DecisionButtons';
import DynamicTaskDetail from '../DynamicTaskDetail';
import RoleConceptDetail from '../../requestrole/RoleConceptDetail';
import ComponentService from "../../../services/ComponentService";
import RequestIdentityRoleManager from "../../../redux/data/RequestIdentityRoleManager";

const componentService = new ComponentService();
const requestIdentityRoleManager = new RequestIdentityRoleManager();


/**
 * Custom task detail designed for use with RoleConceptDetail.
 * Extended from DynamicTaskDetail (it is standard task detail renderer)
 *
 * @author Vít Švanda
 * @author Ondrej Husnik
 */
class DynamicTaskRoleConceptDetail extends DynamicTaskDetail {

  getContentKey() {
    return 'content.task.instance';
  }

  componentDidMount() {
    super.componentDidMount();
    this._initComponent(this.props);
  }

  // @Deprecated - since V10 ... replaced by dynamic key in Route
  // UNSAFE_componentWillReceiveProps(nextProps) {
  //   if (nextProps && (
  //     JSON.stringify(nextProps.task) !== JSON.stringify(this.props.task)
  //   )) {
  //     this._initComponent(nextProps);
  //   }
  // }

  /**
   * Method for init component from didMount method and from willReceiveProps method
   * @param  {properties of component} props For didmount call is this.props for call from willReceiveProps is nextProps.
   */
  _initComponent(props) {
    const { task} = props;
    const _entityId = task && task.variables && task.variables.conceptRole ? task.variables.conceptRole.id : null;
    if (_entityId) {
      let params = requestIdentityRoleManager.getSearchParameters(null)
      params = params.setFilter("id", _entityId)
      params = params.setFilter("identityId", task?.variables?.conceptRole?._embedded?.identityContract?.identity)
      params = params.setFilter("accountId", task?.variables?.conceptRole?.account)
      params = params.setFilter("ownerType", task?.variables?.conceptRole?.ownerType)
      this.context.store.dispatch(requestIdentityRoleManager.fetchEntities(params, null, (entity, error) => {
        if (error) {
          this.setState({
            errorOccurred: true
          }, () => {
            this.addError(error);
          });
        }
      }));
    }
  }

  _completeTask(decision) {
    const formDataValues = this.refs.formData.getData();
    const task = this.refs.form.getData();
    const formDataConverted = this._toFormData(formDataValues, task.formData);
    this.setState({
      showLoading: true
    });
    const decisionReason = this.getDecisionReason();
    const formData = {
      decision: decision.id,
      formData: formDataConverted,
      variables: {taskCompleteMessage: decisionReason}
    };
    const { taskManager} = this.props;
    this.context.store.dispatch(taskManager.completeTask(task, formData, this.props.uiKey, this._afterComplete.bind(this)));
  }

  _updateConcept(data, type, formInstance) {
    this.setState({showLoading: true});
    const concept = {
      id: data.id,
      operation: type,
      roleRequest: data.roleRequest,
      identityContract: data.identityContract.id,
      role: data.role,
      identityRole: data.identityRole,
      validFrom: data.validFrom,
      validTill: data.validTill,
      state: data.state,
      wfProcessId: data.wfProcessId,
      log: data.log,
      _eav: [formInstance]
    };
    const manager = componentService.getManagerForConceptByOwnerType(data?.ownerType)
    this.context.store.dispatch(manager.updateEntity(concept, null, (updatedEntity, error) => {
      if (!error) {
        this._initComponent(this.props);
        this.setState({showLoading: false});
      } else {
        this.setState({showLoading: false});
        this.addError(error);
      }
    }));
  }

  /**
   * Save added or changed entities to arrays and recompile concept data.
   */
  _saveConcept(event) {
    if (event) {
      event.preventDefault();
    }

    const form = this.refs.roleConceptDetail.getForm();
    const eavForm = this.refs.roleConceptDetail.getEavForm();
    if (!form.isFormValid()) {
      return;
    }
    if (eavForm && !eavForm.isValid()) {
      return;
    }

    const entity = form.getData();
    let eavValues = null;
    if (eavForm) {
      eavValues = {values: eavForm.getValues()};
    }
    this._updateConcept(entity, entity.operation, eavValues);
  }

  _goBack() {
    const { task } = this.props;
    const { canGoBack } = this.state;
    if (canGoBack) {
      this.context.history.goBack();
    } else {
      // transmition to /task, history doesnt exist, for now transmition to identity task
      this.context.history.push(`/tasks/identity/${task.variables.implementerIdentifier}`);
    }
  }

  render() {
    const {task, canExecute, _entity} = this.props;
    const { showLoading, errorOccurred, reasonRequired} = this.state;
    let showLoadingInternal = task && _entity ? showLoading : true;
    // If some error occurred, then we want to hide the show loading.
    if (errorOccurred) {
      showLoadingInternal = false;
    }
    const formDataValues = this._toFormDataValues(task.formData);
    const entity = _entity ? _.merge({}, _entity) : null;
    if (_entity && _entity.identityContract && _entity._embedded && _entity._embedded.identityContract) {
      entity.identityContract = _entity._embedded.identityContract;
    }
    if (_entity && _entity.role && _entity._embedded && _entity._embedded.role) {
      entity.role = _entity._embedded.role;
    }
    const decisionReasonText = task.completeTaskMessage;
    const isAccount = entity?.ownerType === 'eu.bcvsolutions.idm.acc.dto.AccAccountDto';
    const accountId = entity?.account

    return (
      <div>
        <Helmet title={this.i18n('title')} />
        {this.renderDecisionConfirmation(reasonRequired)}
        {this.renderHeader(task)}

        <Basic.Panel showLoading={showLoadingInternal}>
          <Basic.AbstractForm className="panel-body" ref="form" data={task}>
            {this._getTaskInfo(task)}
            {this._getApplicantAndRequester(task)}
            <Basic.LabelWrapper
              ref="taskCreated"
              label={this.i18n('createdDate')}>
              <Advanced.DateValue value={task ? task.taskCreated : null} showTime/>
            </Basic.LabelWrapper>
            {this.renderDecisionReasonText(decisionReasonText)}
          </Basic.AbstractForm>
          <Basic.AbstractForm ref="formData" data={formDataValues} style={{ padding: '15px 15px 0px 15px' }}>
            {this._getFormDataComponents(task)}
          </Basic.AbstractForm>
          <Basic.PanelFooter>
            <DecisionButtons
              task={task}
              onClick={this._validateAndCompleteTask.bind(this)}
              showBackButton
              readOnly={!canExecute} />
          </Basic.PanelFooter>
        </Basic.Panel>
        <Basic.Panel style={{ display: (errorOccurred ? 'none' : '') }} showLoading={showLoadingInternal}>
          <RoleConceptDetail
            ref="roleConceptDetail"
            identityUsername={task.applicant}
            showLoading={showLoadingInternal}
            style={{ padding: '15px 15px 0px 15px' }}
            readOnly={!canExecute}
            entity={entity}
            isEdit={canExecute}
            multiAdd={false}
            isAccount={false}
            accountId={accountId}
            showEnvironment={false}
            requestId={entity?.roleRequest}
          />
          <Basic.PanelFooter>
            <Basic.Button
              type="button"
              level="link"
              onClick={this._goBack.bind(this)}
              showLoading={showLoading}>
              {this.i18n('button.back')}
            </Basic.Button>
            <Basic.Button
              level="success"
              onClick={this._saveConcept.bind(this)}
              showLoading={ showLoading }
              showLoadingIcon
              rendered={canExecute}>
              { this.i18n('button.save') }
            </Basic.Button>
          </Basic.PanelFooter>
        </Basic.Panel>
      </div>
    );
  }
}

DynamicTaskRoleConceptDetail.propTypes = {
  task: PropTypes.object,
  readOnly: PropTypes.bool,
  taskManager: PropTypes.object.isRequired,
  canExecute: PropTypes.bool
};

DynamicTaskRoleConceptDetail.defaultProps = {
  task: null,
  readOnly: false,
  canExecute: true
};
function select(state, component) {
  const task = component.task;
  const _entityId = task && task.variables && task.variables.conceptRole ? task.variables.conceptRole.id : null;
  const entity = requestIdentityRoleManager.getEntity(state, _entityId);
  if (task && entity) {
    return {
      _showLoading: false,
      _entity: entity
    };
  }
  return {
    _showLoading: true
  };
}

export default connect(select, null, null, { forwardRef: true })(DynamicTaskRoleConceptDetail);
