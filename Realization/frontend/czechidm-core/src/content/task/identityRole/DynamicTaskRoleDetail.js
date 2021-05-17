import PropTypes from 'prop-types';
import React from 'react';
import Helmet from 'react-helmet';
//
import { connect } from 'react-redux';
import * as Basic from '../../../components/basic';
import * as Advanced from '../../../components/advanced';
import DecisionButtons from '../DecisionButtons';
import DynamicTaskDetail from '../DynamicTaskDetail';
import RoleRequestDetail from '../../requestrole/RoleRequestDetail';

/**
 * Custom task detail designed for use with RoleConceptTable.
 * Extended from DynamicTaskDetail (it is standard task detail renderer)
 */
class DynamicTaskRoleDetail extends DynamicTaskDetail {

  getContentKey() {
    return 'content.task.instance';
  }

  componentDidMount() {
    super.componentDidMount();
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

  render() {
    const {task, canExecute} = this.props;
    const {showLoading, reasonRequired} = this.state;
    const showLoadingInternal = task ? showLoading : true;
    const formDataValues = this._toFormDataValues(task.formData);
    const decisionReasonText = task.completeTaskMessage;

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
            <DecisionButtons task={task} onClick={this._validateAndCompleteTask.bind(this)} readOnly={!canExecute} />
          </Basic.PanelFooter>
        </Basic.Panel>
        <RoleRequestDetail
          ref="identityRoleConceptTable"
          uiKey="identity-role-concept-table"
          entityId={task.variables.entityEvent.content.id}
          showRequestDetail={false}
          editableInStates={['IN_PROGRESS']}
          canExecute={canExecute}
          showLoading={showLoadingInternal}/>
      </div>
    );
  }
}

DynamicTaskRoleDetail.propTypes = {
  task: PropTypes.object,
  readOnly: PropTypes.bool,
  taskManager: PropTypes.object.isRequired,
  canExecute: PropTypes.bool
};

DynamicTaskRoleDetail.defaultProps = {
  task: null,
  readOnly: false,
  canExecute: true
};
function select(state, component) {
  const task = component.task;
  if (task) {
    return {
      _showLoading: false
    };
  }
  return {
    _showLoading: true
  };
}

export default connect(select, null, null, { forwardRef: true })(DynamicTaskRoleDetail);
