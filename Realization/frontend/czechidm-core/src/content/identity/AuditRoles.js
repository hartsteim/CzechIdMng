import React from 'react';
import Helmet from 'react-helmet';
import { connect } from 'react-redux';
import * as Basic from '../../components/basic';
import AuditIdentityRolesTable from '../audit/identity/AuditIdentityRolesTable';
import { IdentityManager } from '../../redux/data';

const identityManager = new IdentityManager();

/**
 * Audit of roles.
 *
 * @author Ondrej Kopr
 * @since 9.5.0
 */
class AuditRoles extends Basic.AbstractContent {

  getContentKey() {
    return 'content.audit';
  }

  componentDidMount() {
    super.componentDidMount();
    const { entityId } = this.props.match.params;
    //
    this.context.store.dispatch(identityManager.fetchEntityIfNeeded(entityId));
  }

  getNavigationKey() {
    return 'profile-audit-roles';
  }

  render() {
    const { identity } = this.props;
    //
    if (!identity) {
      return (
        <Basic.Div showLoading>
          <Helmet title={this.i18n('title')} />
        </Basic.Div>
      );
    }
    //
    return (
      <div>
        <Helmet title={this.i18n('title')} />
        <AuditIdentityRolesTable
          singleUserMod
          id={ identity.id }
          uiKey={ `identity-roles-audit-table-${ identity.id }` }/>
      </div>
    );
  }
}

function select(state, component) {
  const { entityId } = component.match.params;
  return {
    identity: identityManager.getEntity(state, entityId)
  };
}

export default connect(select)(AuditRoles);
