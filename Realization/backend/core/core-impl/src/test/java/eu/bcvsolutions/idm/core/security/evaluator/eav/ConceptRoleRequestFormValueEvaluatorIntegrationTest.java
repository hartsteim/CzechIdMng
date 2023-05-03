package eu.bcvsolutions.idm.core.security.evaluator.eav;

import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmConceptRoleRequest;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmConceptRoleRequestFormValue;
import eu.bcvsolutions.idm.core.security.api.domain.GroupPermission;
import eu.bcvsolutions.idm.test.api.AbstractFormValueEvaluatorIntegrationTest;

public class ConceptRoleRequestFormValueEvaluatorIntegrationTest extends AbstractFormValueEvaluatorIntegrationTest<IdmConceptRoleRequest, IdmConceptRoleRequestFormValue, ConceptRoleRequestFormValueEvaluator> {
	@Autowired
	IdmConceptRoleRequestService conceptRoleRequestService;

	@Override
	protected GroupPermission getSpecificGroupPermission() {
		return CoreGroupPermission.AUTHORIZATIONPOLICY;
	}

	@Override
	protected Identifiable createSpecificOwner() {
		IdmRoleRequestDto request = getHelper().createRoleRequest(identity, role);
		IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
		conceptRoleRequest.setRoleRequest(request.getId());
		conceptRoleRequest.setRole(role.getId());
		conceptRoleRequest.setIdentityRole(identityRole.getId());
		conceptRoleRequest.setOperation(ConceptRoleRequestOperation.REMOVE);
		IdmConceptRoleRequestDto conceptRoleRequestSaved = conceptRoleRequestService.save(conceptRoleRequest);
		return conceptRoleRequestSaved;
	}
}
