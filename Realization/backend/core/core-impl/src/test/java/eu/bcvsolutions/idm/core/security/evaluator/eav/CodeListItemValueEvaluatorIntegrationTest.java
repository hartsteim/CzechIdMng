package eu.bcvsolutions.idm.core.security.evaluator.eav;

import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListItemDto;
import eu.bcvsolutions.idm.core.eav.api.service.CodeListManager;
import eu.bcvsolutions.idm.core.eav.entity.IdmCodeListItem;
import eu.bcvsolutions.idm.core.eav.entity.IdmCodeListItemValue;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.security.api.domain.GroupPermission;
import eu.bcvsolutions.idm.test.api.AbstractFormValueEvaluatorIntegrationTest;

public class CodeListItemValueEvaluatorIntegrationTest extends AbstractFormValueEvaluatorIntegrationTest<IdmCodeListItem, IdmCodeListItemValue, CodeListItemValueEvaluator> {
	@Autowired
	private CodeListManager codeListManager;

	@Override
	protected GroupPermission getSpecificGroupPermission() {
		return CoreGroupPermission.CODELISTITEM;
	}

	@Override
	protected Identifiable createSpecificOwner() {
		IdmCodeListDto codeList = codeListManager.create(getHelper().createName());
		IdmCodeListItemDto codeListItem = codeListManager.createItem(codeList.getId(), getHelper().createName(), getHelper().createName());
		return codeListItem;
	}
}
