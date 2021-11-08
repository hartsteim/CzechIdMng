package eu.bcvsolutions.idm.core.bulk.action.impl.contract;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang.ObjectUtils;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.CoreModuleDescriptor;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.IdmContractGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ForbiddenEntityException;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.security.api.domain.Enabled;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;

/**
 * Bulk operation for removing contract guarantees
 *
 * @author Ondrej Husnik
 *
 */

@Enabled(CoreModuleDescriptor.MODULE_ID)
@Component(IdentityChangeContractGuaranteeBulkAction.NAME)
@Description("Change contract guarantee of an idetity in bulk action.")
public class IdentityChangeContractGuaranteeBulkAction extends AbstractContractGuaranteeBulkAction {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(IdentityChangeContractGuaranteeBulkAction.class);

	public static final String NAME = "identity-change-contract-guarantee-bulk-action";
	

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> formAttributes = super.getFormAttributes();
		formAttributes.add(getGuaranteeAttribute(PROPERTY_OLD_GUARANTEE, true, false));
		formAttributes.add(getGuaranteeAttribute(PROPERTY_NEW_GUARANTEE, false, false));
		return formAttributes;
	}
	
	@Override
	public List<String> getAuthorities() {
		List<String> authorities =  super.getAuthorities();
		authorities.add(CoreGroupPermission.CONTRACTGUARANTEE_UPDATE);
		//
		return authorities;
	}

	@Override
	public String getName() {
		return IdentityChangeContractGuaranteeBulkAction.NAME;
	}
	
	@Override
	public int getOrder() {
		return DEFAULT_ORDER + 502;
	}

	@Override
	protected OperationResult processDto(IdmIdentityDto identity) {
		UUID newGuarantee = getSelectedGuaranteeUuid(PROPERTY_NEW_GUARANTEE);
		UUID oldGuarantee = getSelectedGuaranteeUuid(PROPERTY_OLD_GUARANTEE);
		
		if (ObjectUtils.equals(newGuarantee, oldGuarantee)) {
			return new OperationResult.Builder(OperationState.EXECUTED).build();
		}
		
		Map<UUID, List<IdmContractGuaranteeDto>> currentGuaranteesByContract = getIdentityGuaranteesOrderedByContract(identity.getId());
		// iterate over all contract UUIDs ~ keys and contractGuarantees in List ~ values
		currentGuaranteesByContract.forEach((contractId, contractGuarantees) -> {
			List<IdmContractGuaranteeDto> toUpdate = contractGuarantees.stream().filter(dto -> dto.getGuarantee().equals(oldGuarantee)).collect(Collectors.toList()); 
			if (toUpdate.isEmpty()) {
				// there is no guarantee who to replace for this contract, start new iteration
				return;
			}
			for (IdmContractGuaranteeDto guarantee : toUpdate) { // if same guarantee added multiple-times update all occurrences
				try {
					guarantee.setGuarantee(newGuarantee);
					contractGuaranteeService.save(guarantee, IdmBasePermission.UPDATE);
					logItemProcessed(guarantee, new OperationResult.Builder(OperationState.EXECUTED).build());
				} catch (ForbiddenEntityException ex) {
					LOG.warn("Not authorized to remove the contract guarantee [{}] from contract [{}]  .", guarantee, contractId, ex);
					logContractGuaranteePermissionError(guarantee, guarantee.getGuarantee(), contractId, IdmBasePermission.UPDATE, ex);
					return; // start the new iteration for another contract, this guarantee wasn't removed here
				} catch (ResultCodeException ex) {
					logResultCodeException(guarantee, ex);
					return; // start the new iteration for another contract, this guarantee wasn't removed here
				}
			}
		});
		return new OperationResult.Builder(OperationState.EXECUTED).build();
	}
	
	
}
