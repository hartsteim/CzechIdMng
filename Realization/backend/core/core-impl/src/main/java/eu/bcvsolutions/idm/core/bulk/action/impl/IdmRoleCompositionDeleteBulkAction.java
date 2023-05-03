package eu.bcvsolutions.idm.core.bulk.action.impl;

import java.util.List;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.bulk.action.AbstractRemoveBulkAction;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCompositionDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCompositionFilter;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCompositionService;
import eu.bcvsolutions.idm.core.api.service.ReadWriteDtoService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;

/**
 * Remove role composition bulk action.
 *
 * @author Tomáš Doischer
 */
@Component(IdmRoleCompositionDeleteBulkAction.NAME)
@Description("Delete given role compositions")
public class IdmRoleCompositionDeleteBulkAction extends AbstractRemoveBulkAction<IdmRoleCompositionDto, IdmRoleCompositionFilter> {

	public static final String NAME = "role-composition-delete-bulk-action";

	private final IdmRoleCompositionService roleCompositionService;

	public IdmRoleCompositionDeleteBulkAction(IdmRoleCompositionService roleCompositionService) {
		this.roleCompositionService = roleCompositionService;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	protected List<String> getAuthoritiesForEntity() {
		return List.of(CoreGroupPermission.ROLECOMPOSITION_DELETE);
	}

	@Override
	public ReadWriteDtoService<IdmRoleCompositionDto, IdmRoleCompositionFilter> getService() {
		return roleCompositionService;
	}
}
