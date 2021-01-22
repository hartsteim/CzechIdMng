import PropTypes from 'prop-types';
import React from 'react';
import { connect } from 'react-redux';
import * as Basic from '../../components/basic';
import { ScriptManager } from '../../redux';
import ScriptDetail from './ScriptDetail';

const scriptManager = new ScriptManager();

/**
 * Script detail content, there is difference between create new script and edit.
 * If set params new is new :-).
 *
 * @author Ondřej Kopr
 */
class ScriptContent extends Basic.AbstractContent {

  constructor(props, context) {
    super(props, context);
  }

  getContentKey() {
    return 'content.scripts';
  }

  getNavigationKey() {
    return 'script-detail';
  }

  componentDidMount() {
    super.componentDidMount();
    const { entityId } = this.props.match.params;
    //
    if (this._getIsNew()) {
      this.context.store.dispatch(scriptManager.receiveEntity(entityId, { }));
    } else {
      this.getLogger().debug(`[TypeContent] loading entity detail [id:${entityId}]`);
      this.context.store.dispatch(scriptManager.fetchEntity(entityId));
    }
  }

  /**
   * Function check if exist params new
   */
  _getIsNew() {
    const { query } = this.props.location;
    return (query) ? query.new : null;
  }

  render() {
    const { entity, showLoading } = this.props;
    return (
      <Basic.Row>
        <div className={ this._getIsNew() ? 'col-lg-offset-1 col-lg-10' : 'col-lg-12' }>
          {
            !entity
            ||
            <ScriptDetail entity={entity} showLoading={showLoading}/>
          }
        </div>
      </Basic.Row>
    );
  }
}

ScriptDetail.propTypes = {
  showLoading: PropTypes.bool
};
ScriptDetail.defaultProps = {
};

function select(state, component) {
  const { entityId } = component.match.params;
  const entity = scriptManager.getEntity(state, entityId);
  if (entity) {
    entity.codeable = {
      code: entity.code,
      name: entity.name
    };
  }
  //
  return {
    entity,
    showLoading: scriptManager.isShowLoading(state, null, entityId)
  };
}

export default connect(select)(ScriptContent);
