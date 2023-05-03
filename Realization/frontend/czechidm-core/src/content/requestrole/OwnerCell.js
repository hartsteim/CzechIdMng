import React from 'react';

import * as Basic from "../../components/basic";
import ComponentService from "../../services/ComponentService";
import AdvancedColumn from "../../components/advanced/Table/Column";
import LocalizationService from "../../services/LocalizationService";

const componentService = new ComponentService();

export default class OwnerCell extends AdvancedColumn {

    render() {
        const {entity} = this.props;

        const manager = componentService.getManagerForConceptByOwnerType(entity.ownerType)

        const owner = manager.getEmbeddedOwner(entity);
        if (!owner) {
            return (
                <Basic.Label
                    level="default"
                    value={ LocalizationService.i18n('label.removed') }
                    title={ LocalizationService.i18n('content.audit.revision.deleted') }/>
            );
        }
        //
        const componentInfo = componentService.getConcepComponentByOwnerType(entity.ownerType)
        return (
            <componentInfo.ownerInfoComponent
                entityIdentifier={ owner.id }
                entity={ owner }
                showIdentity={ false }
                showIcon
                face="popover" />
        );

    }

}