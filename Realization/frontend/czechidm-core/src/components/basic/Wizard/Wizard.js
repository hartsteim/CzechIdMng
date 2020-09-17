import React from 'react';
import PropTypes from 'prop-types';
import Steps from 'react-steps';
//
import AbstractContextComponent from '../AbstractContextComponent/AbstractContextComponent';
import Div from '../Div/Div';
import Modal from '../Modal/Modal';
import Button from '../Button/Button';
import { Panel, PanelBody, PanelFooter, PanelHeader } from '../Panel/Panel';
import Alert from '../Alert/Alert';

/**
 * Basic wizard component
 *
 * @since 10.6.0
 * @author Vít Švanda
 */
export default class Wizard extends AbstractContextComponent {

  constructor(props, context) {
    super(props, context);
    const {getSteps} = this.props;
    this.state = {
      ...this.state,
      steps: this.initSteps(getSteps ? getSteps(props, context) : [])
    };
    if (context && context.wizardContext) {
      context.wizardContext.wizardForceUpdate = this.wizardForceUpdate.bind(this);
    }
  }

  /**
   * Immutable copy of a steps form props to the state.
   */
  initSteps(steps) {
    const localSteps = steps.map(step => {
      return this.initStep(step);
    });

    if (localSteps.length > 0 && !localSteps[0].isDone) {
      const firstStep = localSteps[0];
      firstStep.isActive = true;
      firstStep.isFirst = true;
    }
    return localSteps;
  }

  initStep(step) {
    return {
      id: step.id,
      text: this._getStepLabel(step),
      help: step.help,
      component: step.component,
      getComponent: step.getComponent,
      getValidation: step.getValidation,
      isActive: !!step.isActive,
      isDone: !!step.isDone,
      isFirst: false,
      isLast: false,
      isSkipable: !!step.isSkipable,
      hideFooter: step.hideFooter
    };
  }

  _getStepLabel(step) {
    const {
      id,
      module
    } = this.props;

    let label = step.label;
    if (!label) {
      const locKey = `wizard.${id}.steps.${step.id}.name`;
      label = this.i18n(`${module}:${locKey}`);
      if (label === locKey) {
        label = null;
      }
    }
    return label;
  }

  _getWizardLabel() {
    const {
      id,
      module,
      name
    } = this.props;

    let label = name;
    if (!label) {
      const locKey = `wizard.${id}.name`;
      label = this.i18n(`${module}:${locKey}`);
      if (label === locKey) {
        label = null;
      }
    }
    return label;
  }

  /**
   * Merge new steps to old.
   */
  mergeSteps(steps) {
    const localSteps = this.state.steps;
    const newSteps = [];
    steps.forEach(step => {
      let stepFound = null;
      localSteps.filter(localStep => step.id === localStep.id)
        .forEach(localStep => {
          stepFound = localStep;
          if (localStep.component !== step.component) {
            localStep.component = step.component;
          }
          if (localStep.getComponent !== step.getComponent) {
            localStep.getComponent = step.getComponent;
          }
          if (localStep.text !== this._getStepLabel(step)) {
            localStep.text = this._getStepLabel(step);
          }
          if (localStep.hideFooter !== step.hideFooter) {
            localStep.hideFooter = step.hideFooter;
          }
        });
      if (stepFound) {
        newSteps.push(stepFound);
      } else {
        newSteps.push(this.initStep(step));
      }
    });

    // TODO: I don't want set new steps to state in the render method.
    // So I using same instance of array. Works well, but it isn't nice.
    localSteps.length = 0;
    newSteps.forEach(newStep => {
      localSteps.push(newStep);
    });

    return localSteps;
  }

  /**
   * Action executing on click next button.
   */
  onClickNext(skip = false) {
    const {steps} = this.state;
    const wizardContext = this.context.wizardContext;

    const step = this.getActiveStep(steps);
    if (step && step.getValidation) {
      if (!step.getValidation(wizardContext)) {
        return;
      }
    }
    const wizardNext = () => {
      const nextStep = this._getNextStep(step);
      if (nextStep) {
        nextStep.isActive = true;
        nextStep.isLast = !this._getNextStep(nextStep);
      }
      step.isActive = false;
      step.isDone = !skip;
      this.setShowLoading(false);
      this.forceUpdate();
    };

    if (!skip && (step.component || step.getComponent)) {
      if (wizardContext.componentCallBackNext) {
        wizardContext.callBackNext = wizardNext;
        wizardContext.setShowLoading = this.setShowLoading.bind(this);
        wizardContext.componentCallBackNext();
      } else {
        wizardNext();
      }
    } else {
      wizardNext();
    }
  }

  /**
   * Action executing on click previous button.
   */
  onClickPrevious() {
    const {steps} = this.state;

    steps.filter(step => step.isActive)
      .forEach(step => {
        const prevStep = this._getPreviousStep(step);
        if (prevStep) {
          prevStep.isActive = true;
          prevStep.isDone = false;
          prevStep.isFirst = !this._getPreviousStep(prevStep);
        }
        step.isActive = false;
      });
    this.forceUpdate();
  }

  setShowLoading(showLoading) {
    this.setState({showLoading});
  }

  _getNextStep(step) {
    const {steps} = this.state;
    const index = steps.indexOf(step);
    if (index >= 0 && index < steps.length - 1) {
      return steps[index + 1];
    }
    return null;
  }

  _getPreviousStep(step) {
    const {steps} = this.state;
    const index = steps.indexOf(step);
    if (index >= 1) {
      return steps[index - 1];
    }
    return null;
  }

  wizardForceUpdate() {
    this.forceUpdate();
  }

  renderBody() {

    const {
      rendered,
      getSteps,
      type,
      id,
      module
    } = this.props;
    const localSteps = getSteps ? this.mergeSteps(getSteps(this.props, this.context)) : [];

    if (rendered === null || rendered === undefined || rendered === '' || rendered === false) {
      return null;
    }

    const activeStep = this.getActiveStep(localSteps);
    let ActiveComponent = activeStep.component;
    if (activeStep.getComponent) {
      ActiveComponent = activeStep.getComponent(this.context.wizardContext);
    }
    if (this.context.wizardContext) {
      this.context.wizardContext.activeStep = activeStep;
    }
    let stepHelp = activeStep.help;
    if (!stepHelp) {
      const locKey = `wizard.${id}.steps.${activeStep.id}.help`;
      stepHelp = this.i18n(`${module}:${locKey}`);
      if (stepHelp === locKey) {
        stepHelp = null;
      }
    }

    return (
      <Div>
        <Steps items={localSteps} type={type} flat/>
        <Alert rendered={!!stepHelp} text={stepHelp} style={{marginTop: 15}} showHtmlText level="info"/>
        {ActiveComponent}
      </Div>
    );
  }

  getActiveStep(localSteps) {
    let activeStep = {};
    const activeSteps = localSteps.filter(step => step.isActive);
    if (activeSteps.length > 0) {
      activeStep = activeSteps[0];
    }
    return activeStep;
  }

  renderFooter() {
    const {
      rendered,
      showLoading,
      onCloseWizard
    } = this.props;

    const _showLoading = showLoading || this.state.showLoading;
    const {wizardContext} = this.context;

    if (rendered === null || rendered === undefined || rendered === '' || rendered === false) {
      return null;
    }

    const activeStep = (wizardContext && wizardContext.activeStep) ? wizardContext.activeStep : {};
    const isLast = activeStep.isLast;
    const isFirst = activeStep.isFirst;
    const isSkipable = activeStep.isSkipable;

    let additionalButtons = null;
    if (activeStep && activeStep.wizardAddButtons) {
      additionalButtons = activeStep.wizardAddButtons(_showLoading);
    }

    if (additionalButtons) {
      return additionalButtons;
    }

    return (
      <Div>
        <Button
          disabled={isFirst}
          style={{marginRight: 5}}
          showLoading={_showLoading}
          onClick={this.onClickPrevious.bind(this)}>
          {this.i18n('component.basic.Wizard.button.previous')}
        </Button>
        <Button
          rendered={isSkipable}
          level="warning"
          style={{marginRight: 5}}
          showLoading={_showLoading}
          onClick={this.onClickNext.bind(this, true)}>
          {this.i18n('component.basic.Wizard.button.skip')}
        </Button>
        <Button
          rendered={!isLast}
          level="success"
          showLoading={_showLoading}
          onClick={this.onClickNext.bind(this, false)}
          showLoadingIcon>
          {this.i18n('component.basic.Wizard.button.next')}
        </Button>
        <Button
          rendered={isLast}
          level="success"
          showLoading={_showLoading}
          onClick={onCloseWizard ? onCloseWizard.bind(null, true, this.context.wizardContext) : null}
          showLoadingIcon>
          {this.i18n('component.basic.Wizard.button.finish')}
        </Button>
      </Div>
    );
  }

  render() {
    const {
      className,
      rendered,
      style,
      getSteps,
      modal,
      show,
      onCloseWizard,
      showLoading
    } = this.props;
    const localSteps = getSteps ? this.mergeSteps(getSteps(this.props, this.context)) : [];
    if (rendered === null || rendered === undefined || rendered === '' || rendered === false) {
      return null;
    }

    const activeStep = this.getActiveStep(localSteps);
    const hideFooter = activeStep && activeStep.hideFooter;
    const wizardName = this._getWizardLabel();

    return (
      <Div showLoading={showLoading} className={className} style={style}>
        <Div rendered={modal}>
          <Modal
            show={show}
            bsSize="large"
            onHide={onCloseWizard ? onCloseWizard.bind(null, false, this.context.wizardContext) : null}
            backdrop="static">
            <Modal.Header closeButton text={wizardName || this.i18n('component.basic.Wizard.defaultHeader')}/>
            <Modal.Body>
              {this.renderBody()}
            </Modal.Body>
            <Modal.Footer hidden={hideFooter}>
              {this.renderFooter()}
            </Modal.Footer>
          </Modal>
        </Div>
        <Div rendered={!modal}>
          <Panel>
            <PanelHeader text={wizardName}/>
            <PanelBody>
              {this.renderBody()}
            </PanelBody>
            <PanelFooter hidden={hideFooter}>
              {this.renderFooter()}
            </PanelFooter>
          </Panel>
        </Div>
      </Div>
    );
  }
}

Wizard.propTypes = {
  ...AbstractContextComponent.propTypes,
  type: PropTypes.oneOf(['basic', 'circle', 'point']),
  onCloseWizard: PropTypes.func,
  getSteps: PropTypes.func,
  modal: PropTypes.bool,
  show: PropTypes.bool
};
Wizard.defaultProps = {
  ...AbstractContextComponent.defaultProps,
  type: 'point',
  show: false,
  modal: true
};
