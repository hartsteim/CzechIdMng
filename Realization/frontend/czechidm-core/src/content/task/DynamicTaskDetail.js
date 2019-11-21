

import React, { PropTypes } from 'react';
import Helmet from 'react-helmet';
import _ from 'lodash';
//
import * as Basic from '../../components/basic';
import IdentityInfo from '../../components/advanced/IdentityInfo/IdentityInfo';
import WorkflowTaskInfo from '../../components/advanced/WorkflowTaskInfo/WorkflowTaskInfo';
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
      this.refs.confirm.show(this.i18n(decision.warningMessage ? decision.warningMessage : 'completeTaskConfirmDetail'), this.i18n('completeTaskConfirmTitle'))
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
    const formData = {'decision': decision.id, 'formData': this._toFormData(formDataValues, task.formData)};
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
    this.context.router.push(`tasks/identity/${task.variables.implementerIdentifier}`);
  }

  _getApplicantAndRequester(task) {
    if (task) {
      return (
        <div>
          <Basic.LabelWrapper rendered={task.applicant} readOnly ref="applicant" label={this.i18n('applicant')}>
            <IdentityInfo username={task.applicant} showLoading={!task} className="no-margin"/>
          </Basic.LabelWrapper>
          <Basic.LabelWrapper rendered={task.variables.implementerIdentifier} readOnly ref="implementerIdentifier" label={this.i18n('implementerIdentifier')}>
            <IdentityInfo entityIdentifier ={task.variables.implementerIdentifier} showLoading={!task} className="no-margin" face="popover"/>
          </Basic.LabelWrapper>
        </div>
      );
    }
  }

  _getTaskInfo(task) {
    if (task) {
      return (
        <div>
          <Basic.LabelWrapper readOnly ref="taskDescription" label={this.i18n('description')}>
            <WorkflowTaskInfo entity={task} showLink={false} showLoading={!task} className="no-margin"/>
          </Basic.LabelWrapper>
        </div>
      );
    }
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
                  <Basic.Column property="name" header={this.i18n('wf.formData.history.taskName')}/>
                  <Basic.Column property="endTime" header={this.i18n('wf.formData.history.completeDate')}
                    cell={
                      ({rowIndex, data}) => {
                        return (<Basic.DateCell format={this.i18n('format.datetime')} rowIndex={rowIndex} data={data} property="endTime"/>);
                      }
                    }
                  />
                  <Basic.Column property="assignee" header={this.i18n('wf.formData.history.assignee')}
                  cell={({rowIndex, data}) => {
                    return (<IdentityInfo
                      entityIdentifier={ data[rowIndex].assignee }
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

  render() {
    const {task, canExecute, taskManager} = this.props;
    const { showLoading} = this.state;
    const showLoadingInternal = task ? showLoading : true;
    const formDataValues = this._toFormDataValues(task.formData);
    const taskName = taskManager.localize(task, 'name');
    return (
      <div>
        <Helmet title={this.i18n('title')} />
        <Basic.Confirm ref="confirm"/>
        <Basic.PageHeader>{taskName}
          <small> {this.i18n('header')}</small>
        </Basic.PageHeader>
        <Basic.Panel showLoading = {showLoadingInternal}>
          <Basic.AbstractForm className="panel-body" ref="form" data={task}>
            {this._getTaskInfo(task)}
            {this._getApplicantAndRequester(task)}
            <Basic.DateTimePicker ref="taskCreated" readOnly label={this.i18n('createdDate')}/>
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
