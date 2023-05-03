package eu.bcvsolutions.idm.core.security.evaluator.eav;

import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.api.dto.IdmContractSliceDto;
import eu.bcvsolutions.idm.core.api.service.IdmContractSliceService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmContractSlice;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmContractSliceFormValue;
import eu.bcvsolutions.idm.core.security.api.domain.GroupPermission;
import eu.bcvsolutions.idm.test.api.AbstractFormValueEvaluatorIntegrationTest;

public class ContractSliceFormValueEvaluatorIntegrationTest extends AbstractFormValueEvaluatorIntegrationTest<IdmContractSlice, IdmContractSliceFormValue, ContractSliceFormValueEvaluator> {
	@Autowired
	private IdmContractSliceService contractSliceService;

	@Override
	protected GroupPermission getSpecificGroupPermission() {
		return CoreGroupPermission.AUTHORIZATIONPOLICY;
	}

	@Override
	protected Identifiable createSpecificOwner() {
		IdmContractSliceDto slice = getHelper().createContractSlice(identity, null, null, null,
				null);
		slice.setContractCode(getHelper().createName());
		IdmContractSliceDto contractSlice = contractSliceService.save(slice);
		return contractSlice;
	}
}
