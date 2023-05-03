import React from 'react';
import PropTypes from 'prop-types';
//
import * as Basic from "../../components/basic";
import * as Advanced from "../../components/advanced";
import AbstractContextComponent from "../../components/basic/AbstractContextComponent/AbstractContextComponent";
import {
    IdentityManager,
    RoleTreeNodeManager,
    RoleManager,
    IdentityContractManager,
    CodeListManager,
    DataManager,
    ConfigurationManager, RequestIdentityRoleManager
} from '../../redux';
import ComponentService from "../../services/ComponentService";
import SearchParameters from "../../domain/SearchParameters";
import * as Utils from "../../utils";

const identityManager = new IdentityManager();
const identityContractManager = new IdentityContractManager();
const componentService = new ComponentService();

export class IdentityRoleTableFilter extends AbstractContextComponent {

    constructor(props, context) {
        super(props, context);
        this.state = {
            selectedOwnerType: null
        };
    }

    getFilterForm() {
        return this.refs.filterForm;
    }

    // AbstractTableContent uses hard-coded ref filterForm to set data to underlying filter
    // this method is a way to delegate this call to AbstractForm rendered in this component
    setData(data) {
        this.refs.filterForm.setData(data);
    }

    getOwnerTypeOptions() {
        const roleAssignmentComponents = componentService.getRoleAssignmentComponents();
        return [...roleAssignmentComponents].map(([key,value]) => {
            return {
                value: value.ownerType,
                niceLabel: this.i18n(value.locale + '.title')
            }
        });
    }

    ownerTypeSelected = (value) => {
        this.setState({
            selectedOwnerType: value ? value.value : null
        });
    }

    getOwnerSelectComponent() {
        const {contractForceSearchParameters} = this.props;
        const {selectedOwnerType} = this.state;
        //
        if (!selectedOwnerType) {
            return <span/>
        }
        const component = componentService.getConcepComponentByOwnerType(selectedOwnerType);
        const ManagerType = component.ownerManager;
        const managerInstance = new ManagerType();

        return <component.ownerSelectComponent
            ref={component.filterCode}
            manager={managerInstance}
            forceSearchParameters={contractForceSearchParameters}
            defaultSearchParameters={
                new SearchParameters().clearSort()
            }
            label={this.i18n(component.locale + '.label')}
            placeholder={this.i18n(component.locale + '.placeholder')}
            niceLabel={(owner) => managerInstance.getNiceLabel(owner, false)}
        />
    }

    render() {
        const {useFilter, showEnvironment, hasRoleForceFilter, hasIdentityForceFilter,
            contractForceSearchParameters, cancelFilter, environmentItems} = this.props
        const {selectedOwnerType} = this.state;
        //
        const ownerTypeOptions = this.getOwnerTypeOptions();
        const ownerSelect = this.getOwnerSelectComponent();

        return <Advanced.Filter onSubmit={ useFilter }>
            <Basic.AbstractForm ref="filterForm">
                <Basic.Row className={selectedOwnerType ? "" : "last"}>
                    <Basic.Col lg={ showEnvironment ? 3 : 5 }>
                        <Advanced.Filter.TextField
                            ref="roleText"
                            placeholder={ this.i18n('content.identity.roles.filter.role.placeholder') }
                            header={ this.i18n('content.identity.roles.filter.role.placeholder') }
                            hidden={ hasRoleForceFilter }
                            help={ Advanced.Filter.getTextHelp() }/>
                        <Advanced.Filter.SelectBox
                            ref="identityId"
                            manager={ identityManager }
                            label={ null }
                            placeholder={ this.i18n('content.identity.roles.filter.identity.placeholder') }
                            header={ this.i18n('content.identity.roles.filter.identity.placeholder') }
                            hidden={ hasIdentityForceFilter }/>
                    </Basic.Col>
                    <Basic.Col lg={ 3 } rendered={ showEnvironment }>
                        <Advanced.CodeListSelect
                            ref="roleEnvironment"
                            code="environment"
                            label={ null }
                            placeholder={ this.i18n('entity.Role.environment.label') }
                            items={ environmentItems || [] }
                            hidden={ hasRoleForceFilter }
                            multiSelect/>
                    </Basic.Col>
                    <Basic.Col lg={ 3 }>
                        <Advanced.Filter.EnumSelectBox
                            onChange={this.ownerTypeSelected}
                            ref="ownerType"
                            placeholder={ this.i18n("content.identity.roles.filter.ownerSelect.placeholder") }
                            niceLabel={ this.i18n("content.identity.roles.filter.ownerSelect.placeholder") }
                            options={ ownerTypeOptions }/>
                    </Basic.Col>
                    <Basic.Col lg={ 3 } className="text-right">
                        <Advanced.Filter.FilterButtons useFilter={useFilter} cancelFilter={ cancelFilter }/>
                    </Basic.Col>
                </Basic.Row>
                <Basic.Row className={selectedOwnerType ? "last" : ""}>
                    <Basic.Col lg={ 3 }>
                        { ownerSelect }
                    </Basic.Col>
                </Basic.Row>
            </Basic.AbstractForm>
        </Advanced.Filter>
    }

}