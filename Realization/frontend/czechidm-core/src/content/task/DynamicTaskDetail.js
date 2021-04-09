
import PropTypes from 'prop-types';

import React from 'react';
import Helmet from 'react-helmet';
import _ from 'lodash';
//
import * as Basic from '../../components/basic';
import UiUtils from '../../utils/UiUtils';
import IdentityInfo from '../../components/advanced/IdentityInfo/IdentityInfo';
import EntityInfo from '../../components/advanced/EntityInfo/EntityInfo';
import DetailHeader from '../../components/advanced/Content/DetailHeader';
import TaskHistoricInfo from '../../components/advanced/TaskHistoricInfo/TaskHistoricInfo';
import WorkflowTaskInfo from '../../components/advanced/WorkflowTaskInfo/WorkflowTaskInfo';
import WorkflowProcessInfo from '../../components/advanced/WorkflowProcessInfo/WorkflowProcessInfo';
import DateValue from '../../components/advanced/DateValue/DateValue';
import DecisionButtons from './DecisionButtons';

/**
 * Default component for render task detail.
 * Detail is compound from basic fields (name of task, name of applicant ...) and
 * dynamic compoentns. Dynamic components are generated by task definition.
 *
 * @author Vít Švanda
 */
class DynamicTaskDetail extends Basic.AbstractContent {

  constructor(props) {
    super(props);
    this.state = {showLoading: props.showLoading, readOnly: true};
  }

  getContentKey() {
    return 'content.task.instance';
  }

  _toFormDataValues(formDatas) {
    const result = {};
    for (const formData of formDatas) {
      if (formData.type !== 'selectBox') {
        result[formData.id] = formData.value;
      }
    }
    return result;
  }

  _toFormData(formDataValues, formDatas) {
    const result = _.merge({}, formDataValues);
    for (const formData of formDatas) {
      if (!formData.writable && !formData.required) {
        delete result[formData.id];
      }
    }
    return result;
  }

  _validateAndCompleteTask(decision) {
    if (!decision.skipValidation) {
      if (this.refs.form && !this.refs.form.isFormValid()) {
        return;
      }
      if (this.refs.formData && !this.refs.formData.isFormValid()) {
        return;
      }
    }
    if (decision.showWarning) {
      this.refs.confirm.show(
        this.i18n(decision.warningMessage ? decision.warningMessage : 'completeTaskConfirmDetail'), this.i18n('completeTaskConfirmTitle')
      )
        .then(() => {
          this._completeTask(decision);
        }, () => {
          // Rejected
        });
    } else {
      this._completeTask(decision);
    }
  }

  _completeTask(decision) {
    const formDataValues = this.refs.formData ? this.refs.formData.getData() : {};
    const task = this.refs.form.getData();
    const formData = {decision: decision.id, formData: this._toFormData(formDataValues, task.formData)};
    this.setState({
      showLoading: true
    }, () => {
      const { taskManager, uiKey } = this.props;
      this.context.store.dispatch(taskManager.completeTask(task, formData, `${uiKey}`, this._afterComplete.bind(this)));
    });
  }

  _getLocalization(property, formData) {
    return this.i18n(formData[property] ? formData[property] : `wf.formData.${formData.id}.${property}`);
  }

  _afterComplete(task, error) {
    if (error) {
      this.setState({
        showLoading: false
      });
      this.refs.form.processEnded();
      this.addError(error);
      return;
    }
    this.addMessage({ message: this.i18n('successComplete', { name: task.name }) });
    // transmition to /tasks
    // goBack not working in Firefox!!!
    this.context.history.push(`/tasks/identity/${ task.variables.implementerIdentifier }`);
  }

  _getApplicantAndRequester(task) {
    if (task) {
      const delegation = task.delegationDefinition;
      const type = UiUtils.getSimpleJavaType(task._dtotype);
      const isHistoricTask = type === 'WorkflowHistoricTaskInstanceDto';

      return (
        <Basic.Row>
          <Basic.Col lg={ 8 }>
            <div>
              <Basic.LabelWrapper rendered={task.applicant} readOnly ref="applicant" label={this.i18n('applicant')}>
                <IdentityInfo username={task.applicant} showLoading={!task} className="no-margin"/>
              </Basic.LabelWrapper>
              <Basic.LabelWrapper
                rendered={task.variables.implementerIdentifier}
                readOnly
                ref="implementerIdentifier"
                label={this.i18n('implementerIdentifier')}>
                <IdentityInfo
                  entityIdentifier={task.variables.implementerIdentifier}
                  showLoading={!task}
                  className="no-margin"
                  face="popover"/>
              </Basic.LabelWrapper>
            </div>
          </Basic.Col>
          <Basic.Col lg={ 4 } rendered={!!delegation} style={{paddingTop: 23}}>
            <EntityInfo
              entityType="DelegationDefinitionDto"
              entityIdentifier={delegation ? delegation.id : null}
              face="full"
              level="info"
              entity={delegation}
              showEntityType
              showLink
              collapse={!isHistoricTask}
              collapsable
              showIcon/>
          </Basic.Col>
        </Basic.Row>
      );
    }
    return null;
  }

  _getTaskInfo(task) {
    if (task) {
      const type = UiUtils.getSimpleJavaType(task._dtotype);
      const isHistoricTask = type === 'WorkflowHistoricTaskInstanceDto';

      return (
        <Basic.Div>
          <Basic.Div>
            <TaskHistoricInfo
              rendered={isHistoricTask}
              level="info"
              collapse
              collapsable
              entity={task}
              titleStyle={{fontSize: 'medium'}}/>
          </Basic.Div>
          <Basic.LabelWrapper
            rendered={isHistoricTask}
            ref="historicTaskInfo"
            label={this.i18n('description')}>
            <WorkflowTaskInfo
              entity={task}
              showLink={false}
              showLoading={!task}
              className="no-margin"/>
          </Basic.LabelWrapper>
        </Basic.Div>
      );
    }
    return null;
  }

  _getFormDataComponents(task) {
    const { canExecute } = this.props;
    if (!task) {
      return null;
    }
    const formDatas = task.formData;
    const formDataComponents = [];
    for (const formData of formDatas) {
      switch (formData.type) {
        case 'textField': {
          formDataComponents.push(
            <Basic.TextField
              key={formData.id}
              ref={formData.id}
              readOnly={!formData.writable || !canExecute}
              required={formData.required}
              tooltip={this._getLocalization('tooltip', formData)}
              placeholder={this._getLocalization('placeholder', formData)}
              label={this._getLocalization('name', formData)}/>
          );
          break;
        }
        case 'date':
        case 'localDate': {
          formDataComponents.push(
            <Basic.DateTimePicker
              key={formData.id}
              ref={formData.id}
              mode="date"
              readOnly={!formData.writable || !canExecute}
              required={formData.required}
              tooltip={this._getLocalization('tooltip', formData)}
              placeholder={this._getLocalization('placeholder', formData)}
              label={this._getLocalization('name', formData)}/>
          );
          break;
        }
        case 'checkbox': {
          formDataComponents.push(
            <Basic.Checkbox
              key={formData.id}
              ref={formData.id}
              readOnly={!formData.writable || !canExecute}
              required={formData.required}
              tooltip={this._getLocalization('tooltip', formData)}
              placeholder={this._getLocalization('placeholder', formData)}
              label={this._getLocalization('name', formData)}/>
          );
          break;
        }
        case 'textArea': {
          formDataComponents.push(
            <Basic.TextArea
              key={formData.id}
              ref={formData.id}
              readOnly={!formData.writable || !canExecute}
              required={formData.required}
              tooltip={this._getLocalization('tooltip', formData)}
              placeholder={this._getLocalization('placeholder', formData)}
              label={this._getLocalization('name', formData)}/>
          );
          break;
        }
        case 'selectBox': {
          const data = [];
          const map = new Map(Object.entries(JSON.parse(formData.value)));
          map.forEach((label, value) => {
            data.push({value, niceLabel: this.i18n(label)});
          });
          formDataComponents.push(
            <Basic.EnumSelectBox
              key={formData.id}
              ref={formData.id}
              readOnly={!formData.writable || !canExecute}
              required={formData.required}
              tooltip={this._getLocalization('tooltip', formData)}
              placeholder={this._getLocalization('placeholder', formData)}
              label={this._getLocalization('name', formData)}
              multiSelect={false}
              options={data}/>
          );
          break;
        }
        case 'taskHistory': {
          const history = JSON.parse(formData.value);
          formDataComponents.push(
            <Basic.Panel>
              <Basic.PanelHeader text={this._getLocalization('name', formData)}/>
              <Basic.Table
                uiKey={formData.id}
                data={history}
                rendered
                noData={this.i18n('component.basic.Table.noData')}
                rowClass={({rowIndex, data}) => {
                  return (data[rowIndex].changed) ? 'warning' : '';
                }}>
                <Basic.Column property="taskName" header={this.i18n('wf.formData.history.taskName')}/>
                <Basic.Column
                  property="endTime"
                  header={this.i18n('wf.formData.history.completeDate')}
                  cell={
                    ({rowIndex, data}) => {
                      return (<Basic.DateCell format={this.i18n('format.datetime')} rowIndex={rowIndex} data={data} property="endTime"/>);
                    }
                  }
                />
                <Basic.Column
                  property="taskAssignee"
                  header={this.i18n('wf.formData.history.assignee')}
                  cell={({rowIndex, data}) => {
                    return (<IdentityInfo
                      entityIdentifier={ data[rowIndex].taskAssignee }
                      face="popover" />);
                  }
                  }/>
                <Basic.Column property="completeTaskMessage" header={this.i18n('wf.formData.history.message')}/>
              </Basic.Table>
            </Basic.Panel>
          );
          break;
        }
        default: {
          formDataComponents.push(
            <Basic.TextField
              key={formData.id}
              ref={formData.id}
              readOnly={!formData.writable || !canExecute}
              required={formData.required}
              tooltip={this._getLocalization('tooltip', formData)}
              placeholder={this._getLocalization('placeholder', formData)}
              label={this._getLocalization('name', formData)}/>
          );
        }
      }
    }
    return formDataComponents;
  }

  renderHeader(task) {
    const {taskManager} = this.props;

    const taskName = taskManager.localize(task, 'name');
    const options = [];
    options.push(
      {
        label: this.i18n('content.task.historicInstance.process'),
        value: (
          <WorkflowProcessInfo entityIdentifier={task.processInstanceId} maxLength={50}/>
        )
      }
    );

    return (
      <DetailHeader
        entity={ task }
        additionalOptions={options}>
        {taskName}
        <small>
          {' '}
          {this.i18n('header')}
        </small>
      </DetailHeader>
    );
  }

  render() {
    const {task, canExecute} = this.props;
    const { showLoading} = this.state;
    const showLoadingInternal = task ? showLoading : true;
    const formDataValues = this._toFormDataValues(task.formData);

    return (
      <div>
        <Helmet title={this.i18n('title')} />
        <Basic.Confirm ref="confirm"/>
        {this.renderHeader(task)}
        <Basic.Panel showLoading={showLoadingInternal}>
          <Basic.AbstractForm className="panel-body" ref="form" data={task}>
            {this._getTaskInfo(task)}
            {this._getApplicantAndRequester(task)}
            <Basic.LabelWrapper
              ref="taskCreated"
              label={this.i18n('createdDate')}>
              <DateValue value={task ? task.taskCreated : null} showTime/>
            </Basic.LabelWrapper>
          </Basic.AbstractForm>
          <Basic.AbstractForm ref="formData" data={formDataValues} className="panel-body">
            {this._getFormDataComponents(task)}
          </Basic.AbstractForm>
          <Basic.PanelFooter rendered={canExecute}>
            <DecisionButtons task={task} onClick={this._validateAndCompleteTask.bind(this)} readOnly={!canExecute}/>
          </Basic.PanelFooter>
        </Basic.Panel>
      </div>
    );
  }
}

DynamicTaskDetail.propTypes = {
  task: PropTypes.object,
  readOnly: PropTypes.bool,
  taskManager: PropTypes.object.isRequired,
  canExecute: PropTypes.bool
};

DynamicTaskDetail.defaultProps = {
  task: null,
  readOnly: false,
  canExecute: true
};

export default DynamicTaskDetail;
