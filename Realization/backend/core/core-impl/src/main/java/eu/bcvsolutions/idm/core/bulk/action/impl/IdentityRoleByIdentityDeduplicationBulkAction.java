package eu.bcvsolutions.idm.core.bulk.action.impl;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.CoreModuleDescriptor;
import eu.bcvsolutions.idm.core.api.bulk.action.AbstractBulkAction;
import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.PriorityType;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestedByType;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ForbiddenEntityException;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.ReadWriteDtoService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.model.event.RoleRequestEvent;
import eu.bcvsolutions.idm.core.model.event.RoleRequestEvent.RoleRequestEventType;
import eu.bcvsolutions.idm.core.model.event.processor.role.RoleRequestApprovalProcessor;
import eu.bcvsolutions.idm.core.notification.api.domain.NotificationLevel;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.Enabled;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.utils.PermissionUtils;

/**
 * {@link IdmIdentityRoleDto} deduplication by {@link IdmIdentityDto} (respective by {@link IdmIdentityContractDto}).
 * Bulk action create for each identity new {@link IdmRoleRequestDto} with concept trough all {@link IdmIdentityContractDto}.
 * As log items is created each {@link IdmIdentityRoleDto} that will be removed.
 *
 * @author Ondrej Kopr
 * @since 9.5.0
 *
 */

@Enabled(CoreModuleDescriptor.MODULE_ID)
@Component("identityRoleByIdentityDeduplicationBulkAction")
@Description("Deduplicate identity role by identity.")
public class IdentityRoleByIdentityDeduplicationBulkAction
		extends AbstractBulkAction<IdmIdentityDto, IdmIdentityFilter> {

	public static final String NAME = "core-identity-role-by-identity-deduplication-bulk-action";

	public static final String CHECK_SUBDEFINITION_CODE = "checkSubdefinition";
	public static final String APPROVE_CODE = "approve";

	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private IdmIdentityContractService identityContractService;
	@Autowired
	private IdmRoleRequestService roleRequestService;
	@Autowired
	private IdmConceptRoleRequestService conceptRoleRequestService;

	@Override
	public ReadWriteDtoService<IdmIdentityDto, IdmIdentityFilter> getService() {
		return identityService;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> formAttributes = super.getFormAttributes();
		formAttributes.add(getApproveAttribute());
		formAttributes.add(getCheckSubdefinition());
		return formAttributes;
	}

	@Override
	public String getName() {
		return IdentityRoleByIdentityDeduplicationBulkAction.NAME;
	}

	@Override
	protected OperationResult processDto(IdmIdentityDto dto) {
		// Result will be list of concepts
		List<IdmConceptRoleRequestDto> concepts = new ArrayList<>();

		List<IdmIdentityContractDto> contracts = identityContractService.findAllValidForDate(dto.getId(), LocalDate.now(), null);
		for (IdmIdentityContractDto contract : contracts) {

			// Check access for contract
			try {
				identityContractService.checkAccess(contract, PermissionUtils.toPermissions(getAuthoritiesForIdentityContract()).toArray(new BasePermission[] {}));
			} catch (ForbiddenEntityException e) {
				continue;
			}
			// Process deduplication per identity contract
			concepts.addAll(processDuplicitiesForContract(contract));
		}

		// Log item

		// If result is empty for identity will be removed any roles.
		if (concepts.isEmpty()) {
			return new OperationResult.Builder(OperationState.EXECUTED).build();
		}

		IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setApplicant(dto.getId());
		roleRequest.setRequestedByType(RoleRequestedByType.MANUALLY);
		roleRequest.setLog("Request was created by bulk action (deduplication).");
		roleRequest.setExecuteImmediately(!isApprove()); // if set approve, dont execute immediately
		roleRequest = roleRequestService.save(roleRequest, IdmBasePermission.CREATE);

		for (IdmConceptRoleRequestDto concept : concepts) {
			concept.setRoleRequest(roleRequest.getId());
			concept = conceptRoleRequestService.save(concept, IdmBasePermission.CREATE);
		}
		Map<String, Serializable> properties = new HashMap<>();
		properties.put(RoleRequestApprovalProcessor.CHECK_RIGHT_PROPERTY, true);
		RoleRequestEvent event = new RoleRequestEvent(RoleRequestEventType.EXCECUTE, roleRequest, properties);
		event.setPriority(PriorityType.HIGH);
		IdmRoleRequestDto request = roleRequestService.startRequestInternal(event);
		//
		if (request.getState() == RoleRequestState.EXECUTED) {
			return new OperationResult.Builder(OperationState.EXECUTED).build();
		} else {
			return new OperationResult.Builder(OperationState.CREATED).build();
		}
	}

	@Override
	protected List<String> getAuthoritiesForEntity() {
		return Lists.newArrayList(CoreGroupPermission.IDENTITY_READ, CoreGroupPermission.IDENTITY_CHANGEPERMISSION);
	}

	/**
	 * Authorities for identity role
	 *
	 * @return
	 */
	private List<String> getAuthoritiesForIdentityRole() {
		return Lists.newArrayList(CoreGroupPermission.IDENTITYROLE_READ);
	}
	
	/**
	 * Authorities for identity contract
	 *
	 * @return
	 */
	private List<String> getAuthoritiesForIdentityContract() {
		return Lists.newArrayList(CoreGroupPermission.IDENTITYCONTRACT_AUTOCOMPLETE);
	}

	/**
	 * Method prepeare {@link IdmConceptRoleRequestDto} whit duplicities for given contract.
	 *
	 * @param contract
	 * @return
	 */
	private List<IdmConceptRoleRequestDto> processDuplicitiesForContract(IdmIdentityContractDto contract) {
		
		List<IdmIdentityRoleDto> duplicatesIdentityRoleForContract = getDuplicatesIdentityRoleForContract(contract);
		List<IdmConceptRoleRequestDto> concepts = new ArrayList<>(duplicatesIdentityRoleForContract.size());

		for (IdmIdentityRoleDto duplicity : duplicatesIdentityRoleForContract) {
			IdmConceptRoleRequestDto concept = new IdmConceptRoleRequestDto();
			concept.setRole(duplicity.getRole());
			concept.setIdentityRole(duplicity.getId());
			concept.setIdentityContract(duplicity.getIdentityContract());
			concept.setOperation(ConceptRoleRequestOperation.REMOVE);
			concepts.add(concept);
		}

		return concepts;
	}

	/**
	 * Method return duplicities for {@link IdmIdentityContractDto}
	 * @param contract
	 * @return
	 */
	public List<IdmIdentityRoleDto> getDuplicatesIdentityRoleForContract(IdmIdentityContractDto contract) {
		Boolean skipSubdefinition = !isCheckSubdefinition();

		// Identity roles must be sorted by create, for duplicities with manually will be removed always the newer.
		Pageable page = PageRequest.of(0, Integer.MAX_VALUE, new Sort(Direction.DESC, IdmIdentityRole_.created.getName()));
		
		List<IdmIdentityRoleDto> duplicities = new ArrayList<>();

		// Get all identity roles
		IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();
		identityRoleFilter.setIdentityId(contract.getIdentity());
		identityRoleFilter.setIdentityContractId(contract.getId());
		List<IdmIdentityRoleDto> identityRoles = identityRoleService.find(identityRoleFilter, page,
				PermissionUtils.toPermissions(getAuthoritiesForIdentityRole()).toArray(new BasePermission[] {})).getContent();
		
		// Get map of duplicity roles 
		Map<UUID, List<IdmIdentityRoleDto>> duplictRoles = identityRoles
				.stream() //
				.collect( //
						Collectors.groupingBy( // Group identity roles by role
								IdmIdentityRoleDto::getRole) //
						).entrySet() //
				.stream() //
				.filter( //
						entry -> entry.getValue().size() > 1 // Filter only by values where is more than one record (possible duplicites)
						) //
				.collect( //
						Collectors.toMap( //
								k -> k.getKey(), // Collect as map where key is UUID of role
								v -> v.getValue() // And value is list of identity roles for this role
								) //
						); //

		// Iterate over duplicated roles. In Key is ID of role that has more finding
		// for the contract
		for (Entry<UUID, List<IdmIdentityRoleDto>> entry : duplictRoles.entrySet()) {
			List<IdmIdentityRoleDto> duplicitIdentityRoles = entry.getValue();

			List<IdmIdentityRoleDto> rolesToCheck = duplicitIdentityRoles //
					.stream() //
					.filter(idenityRole -> { //
						return idenityRole.getAutomaticRole() == null;
					}) //
					.collect(Collectors.toList());
			// Copy of manually added roles is for prevent comparing with already removed role
			List<IdmIdentityRoleDto> rolesToCheckCopy = new ArrayList<>(rolesToCheck);

			if (rolesToCheck.isEmpty()) {
				continue;
			}

			// Remove form duplicated manually added
			duplicitIdentityRoles.removeAll(rolesToCheck);
			
			for (IdmIdentityRoleDto checkedRole : rolesToCheck) {
				IdmIdentityRoleDto duplicit = null;
				// Add identity form attributes with value into eavs
				if (BooleanUtils.isFalse(skipSubdefinition)) {
					checkedRole.setEavs(Lists.newArrayList(identityRoleService.getRoleAttributeValues(checkedRole)));
				}
				
				for (IdmIdentityRoleDto duplicitIdentityRole : duplicitIdentityRoles) {
					// Add identity form attributes with value into eavs
					if (BooleanUtils.isFalse(skipSubdefinition)) {
						duplicitIdentityRole.setEavs(Lists.newArrayList(identityRoleService.getRoleAttributeValues(duplicitIdentityRole)));
					}
					duplicit = identityRoleService.getDuplicated(checkedRole, duplicitIdentityRole, skipSubdefinition);

					if (duplicit != null) {
						break;
					}
				}

				// If this role isn't duplicated check also manually added
				if (duplicit == null) {
					for (IdmIdentityRoleDto checkedRoleSecond : rolesToCheckCopy) {

						// Skip itself
						if (checkedRole.getId().equals(checkedRoleSecond.getId())) {
							continue;
						}

						// Add identity form attributes with value into eavs
						if (BooleanUtils.isFalse(skipSubdefinition)) {
							checkedRoleSecond.setEavs(Lists.newArrayList(identityRoleService.getRoleAttributeValues(checkedRoleSecond)));
						}

						duplicit = identityRoleService.getDuplicated(checkedRole, checkedRoleSecond, skipSubdefinition);

						if (duplicit != null) {
							break;
						}
					}
				}

				// Finally role is duplicated
				if (duplicit != null) {
					rolesToCheckCopy.remove(checkedRole);
					// prevent to remove roles with the same created date
					if (!duplicities.contains(duplicit)) {
						duplicities.add(duplicit);
					}
				}
			}
		}
		
		return duplicities;
	}
	
	/**
	 * Is set approve
	 *
	 * @return
	 */
	private boolean isApprove() {
		return this.getParameterConverter().toBoolean(getProperties(), APPROVE_CODE, true);
	}

	/**
	 * Is check subdefinition
	 *
	 * @return
	 */
	private boolean isCheckSubdefinition() {
		return this.getParameterConverter().toBoolean(getProperties(),CHECK_SUBDEFINITION_CODE, true);
	}

	/**
	 * Get {@link IdmFormAttributeDto} for approve checkbox
	 *
	 * @return
	 */
	private IdmFormAttributeDto getApproveAttribute() {
		IdmFormAttributeDto approve = new IdmFormAttributeDto(
				APPROVE_CODE, 
				APPROVE_CODE, 
				PersistentType.BOOLEAN);
		approve.setDefaultValue(Boolean.TRUE.toString());
		return approve;
	}
	
	/**
	 * Get {@link IdmFormAttributeDto} for check subdefinition checkbox
	 *
	 * @return
	 */
	private IdmFormAttributeDto getCheckSubdefinition() {
		IdmFormAttributeDto approve = new IdmFormAttributeDto(
				CHECK_SUBDEFINITION_CODE, 
				CHECK_SUBDEFINITION_CODE, PersistentType.BOOLEAN);
		approve.setDefaultValue(Boolean.TRUE.toString());
		return approve;
	}

	@Override
	public int getOrder() {
		return super.getOrder() + 800;
	}
	
	@Override
	public NotificationLevel getLevel() {
		return NotificationLevel.WARNING;
	}
}
