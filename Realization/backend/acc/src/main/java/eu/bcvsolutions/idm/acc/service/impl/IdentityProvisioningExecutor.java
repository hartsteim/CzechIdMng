package eu.bcvsolutions.idm.acc.service.impl;

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import eu.bcvsolutions.idm.acc.domain.AccountType;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.core.api.dto.AbstractRoleAssignmentDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRequestIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.service.IdmRoleAssignmentManager;
import eu.bcvsolutions.idm.core.eav.entity.AbstractFormValue_;
import eu.bcvsolutions.idm.core.model.entity.AbstractRoleAssignment_;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.domain.AssignedRoleDto;
import eu.bcvsolutions.idm.acc.domain.AttributeMapping;
import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.MappingContext;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.EntityAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccIdentityAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemAttributeFilter;
import eu.bcvsolutions.idm.acc.entity.AccIdentityAccount_;
import eu.bcvsolutions.idm.acc.entity.SysRoleSystemAttribute_;
import eu.bcvsolutions.idm.acc.service.api.AccAccountManagementService;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningExecutor;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityTypeManager;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.acc.system.entity.SystemEntityTypeRegistrable;
import eu.bcvsolutions.idm.core.api.dto.AbstractIdmAutomaticRoleDto;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmContractPositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCompositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityContractFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.service.ReadWriteDtoService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormValue_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.ic.service.api.IcConnectorFacade;

/**
 * Service for do Identity provisioning
 * 
 * @author Vít Švanda
 * @author Radek Tomiška
 * @author Roman Kucera
 */
@Service
@Qualifier(value = IdentityProvisioningExecutor.NAME)
public class IdentityProvisioningExecutor extends AbstractProvisioningExecutor<IdmIdentityDto> {

	public static final String NAME = "identityProvisioningService";
	public final static String ASSIGNED_ROLES_FIELD = "assignedRoles";
	public final static String ASSIGNED_ROLES_FOR_SYSTEM_FIELD = "assignedRolesForSystem";
	public static final String IDENTITY_STATE_IDM_NAME = "state";
	public static final String SYSTEM_ENTITY_TYPE = "IDENTITY";

	private final AccIdentityAccountService identityAccountService;
	private final IdmIdentityService identityService;
	private final AccAccountManagementService accountManagementService;
	@Autowired
	private IdmRoleAssignmentManager roleAssignmentManager;
	@Autowired
	private IdmIdentityContractService identityContractService;
	@Autowired
	private LookupService lookupService;

	@Autowired
	public IdentityProvisioningExecutor(SysSystemMappingService systemMappingService,
			SysSystemAttributeMappingService attributeMappingService, IcConnectorFacade connectorFacade,
			SysSystemService systemService, SysRoleSystemService roleSystemService,
			AccAccountManagementService accountManagementService,
			SysRoleSystemAttributeService roleSystemAttributeService, SysSystemEntityService systemEntityService,
			AccAccountService accountService, AccIdentityAccountService identityAccountService,
			ProvisioningExecutor provisioningExecutor, EntityEventManager entityEventManager,
			SysSchemaObjectClassService schemaObjectClassService, SysSchemaAttributeService schemaAttributeService,
			SysSystemAttributeMappingService systemAttributeMappingService, IdmRoleService roleService,
			IdmIdentityService identityService, SysSystemEntityTypeManager systemEntityManager) {

		super(systemMappingService, attributeMappingService, connectorFacade, systemService, roleSystemService,
				roleSystemAttributeService, systemEntityService, accountService, provisioningExecutor,
				entityEventManager, schemaAttributeService, schemaObjectClassService, systemAttributeMappingService,
				roleService, systemEntityManager);
		//
		Assert.notNull(identityAccountService, "Service is required.");
		Assert.notNull(roleSystemService, "Service is required.");
		Assert.notNull(roleService, "Service is required.");
		Assert.notNull(identityService, "Service is required.");
		Assert.notNull(accountManagementService, "Service is required.");
		//
		this.identityAccountService = identityAccountService;
		this.identityService = identityService;
		this.accountManagementService = accountManagementService;
	}

	@Override
	public void doProvisioning(AccAccountDto account) {
		Assert.notNull(account, "Account is required.");
		//
		AccIdentityAccountFilter filter = new AccIdentityAccountFilter();
		filter.setAccountId(account.getId());
		filter.setOwnership(Boolean.TRUE);
		AccIdentityAccountDto identityAccount = identityAccountService.find(filter, null).stream().findFirst().orElse(null);
		if (identityAccount != null) {
			doProvisioning(account,
					DtoUtils.getEmbedded(identityAccount, AccIdentityAccount_.identity, IdmIdentityDto.class));
		}
	}

	/**
	 * Identities have own implementation of ACM
	 */
	@Override
	public boolean accountManagement(IdmIdentityDto dto) {
		return accountManagementService.resolveIdentityAccounts(dto);
	}

	/**
	 * Return list of all overloading attributes for given identity, system and uid
	 *
	 * @param entity
	 * @param system
	 * @param entityType
	 * @param account
	 * @return
	 */
	@Override
	protected List<SysRoleSystemAttributeDto> findOverloadingAttributes(IdmIdentityDto entity, SysSystemDto system,
			AccAccountDto account, String entityType) {
		
		List<SysRoleSystemAttributeDto> roleSystemAttributesAll = new ArrayList<>();
		UUID mapping = account.getSystemMapping();
		if (mapping == null) {
			return roleSystemAttributesAll;
		}
		// Search overridden attributes for this account.
		SysRoleSystemAttributeFilter roleSystemAttributeFilter = new SysRoleSystemAttributeFilter();
		roleSystemAttributeFilter.setSystemMappingId(mapping);
		// Filtering by identity-account relation.
		roleSystemAttributeFilter.setAccountId(account.getId());
		roleSystemAttributeFilter.setIdentityId(entity.getId());
		List<SysRoleSystemAttributeDto> roleAttributes = roleSystemAttributeService
				.find(roleSystemAttributeFilter, null).getContent();
		if (!CollectionUtils.isEmpty(roleAttributes)) {
			roleSystemAttributesAll.addAll(roleAttributes);
		}

		// Cross-domains attributes and no-login attributes will be added only for default UID.
		// It means, if some attributes override an UID attribute, then no additional attribute will be used!
		boolean uidIsOverridden = roleSystemAttributesAll.stream().anyMatch(SysRoleSystemAttributeDto::isUid);

		if (!uidIsOverridden) {
			// Add overridden attributes which are in a cross-domain group or is in no-login role.
			// Beware - these attributes are added for every account (overridden attributes are not supported)
			roleSystemAttributeFilter = new SysRoleSystemAttributeFilter();
			roleSystemAttributeFilter.setRoleSystemRelationForIdentityId(entity.getId());
			roleSystemAttributeFilter.setSystemMappingId(mapping);
			roleSystemAttributeFilter.setInCrossDomainGroupOrIsNoLogin(Boolean.TRUE);
			roleSystemAttributeFilter.setAccountId(account.getId());

			List<SysRoleSystemAttributeDto> roleAttributesInCrossGroup = roleSystemAttributeService
					.find(roleSystemAttributeFilter, null).getContent();

			if (!CollectionUtils.isEmpty(roleAttributesInCrossGroup)) {
				roleSystemAttributesAll.addAll(roleAttributesInCrossGroup);
			}
		}
		return roleSystemAttributesAll;
	}

	@Override
	protected Object getAttributeValue(String uid, IdmIdentityDto dto, AttributeMapping attribute,
			SysSystemDto system, MappingContext mappingContext, AccAccountDto accountDto) {
		
		if (attribute instanceof SysRoleSystemAttributeDto) {
			SysRoleSystemAttributeDto roleSystemAttributeDto = (SysRoleSystemAttributeDto) attribute;
			if (roleSystemAttributeDto.isSkipValueIfExcluded() &&
					(AttributeMappingStrategyType.MERGE == roleSystemAttributeDto.getStrategyType() ||
					AttributeMappingStrategyType.AUTHORITATIVE_MERGE == roleSystemAttributeDto.getStrategyType())) {
				
				// Get ID of the role
				Assert.notNull(roleSystemAttributeDto.getRoleSystem(), "SysRoleSystem cannot be null!");	
				SysRoleSystemDto roleSystemDto = DtoUtils.getEmbedded(roleSystemAttributeDto,
						SysRoleSystemAttribute_.roleSystem.getName(), SysRoleSystemDto.class, (SysRoleSystemDto) null);
				if(roleSystemDto == null) {
					roleSystemDto = roleSystemService.get(roleSystemAttributeDto.getId());
				}
				UUID roleId = roleSystemDto.getRole();
				Assert.notNull(roleId, "Role cannot be null!");
				
				// Find count of NOT excluded contracts for this identity and role
				IdmIdentityContractFilter contractFilter = new IdmIdentityContractFilter();
				contractFilter.setIdentity(dto.getId());
				contractFilter.setExcluded(Boolean.FALSE);
				contractFilter.setRoleId(roleId);
				// If exists some not excluded contract, then value will be not skipped!
				long countOfNotExcludedContracts = identityContractService.count(contractFilter);
				if (countOfNotExcludedContracts == 0) {
					contractFilter.setExcluded(Boolean.TRUE);
					// For skip the value must exist at least one excluded contract
					long countOfexcludedContracts = identityContractService.count(contractFilter);
					if (countOfexcludedContracts >= 0) {
						return null;
					}
				}
				
			}
		}

		// If assigned roles fields are mapped, then we will searching and convert
		// identity-roles to list of AssignedRoleDtos (including values of EAV for 
		// identity-roles). That list will be input for that fields.
		if (attribute != null //
				&& (ASSIGNED_ROLES_FIELD.equals(attribute.getIdmPropertyName()) //
						|| ASSIGNED_ROLES_FOR_SYSTEM_FIELD.equals(attribute.getIdmPropertyName()) //
				)) { //
			assertNotNull(dto.getId());

			IdmRequestIdentityRoleFilter identityRoleFilter = new IdmRequestIdentityRoleFilter();
			identityRoleFilter.setIdentityId(dto.getId());
			identityRoleFilter.setValid(Boolean.TRUE);
			List<AbstractRoleAssignmentDto> identityRoles = roleAssignmentManager
					.find(identityRoleFilter,
							PageRequest.of(0, Integer.MAX_VALUE, Sort.by(AbstractEntity_.created.getName())), (a, b) -> {});
			List<AbstractRoleAssignmentDto> identityRolesToProcess;

			if (ASSIGNED_ROLES_FOR_SYSTEM_FIELD.equals(attribute.getIdmPropertyName())) {
				// For ASSIGNED_ROLES_FOR_SYSTEM_FIELD we will convert only identity-roles for
				// that identity and given system
				assertNotNull(system.getId());

				List<AbstractRoleAssignmentDto> identityRolesForSystem = Lists.newArrayList();
				AccIdentityAccountFilter identityAccountFilter = new AccIdentityAccountFilter();
				identityAccountFilter.setIdentityId(dto.getId());
				identityAccountFilter.setSystemId(system.getId());
				List<AccIdentityAccountDto> identityAccounts = identityAccountService.find(identityAccountFilter, null)
						.getContent();

				// Filtering only identity-roles for that system
				identityAccounts.forEach(identityAccount -> identityRolesForSystem.addAll(identityRoles.stream()
						.filter(identityRole -> identityRole.getId().equals(identityAccount.getIdentityRole()))
						.collect(Collectors.toList())
				));

				identityRolesToProcess = identityRolesForSystem;
			} else {
				// For ASSIGNED_ROLES_FIELD we will convert all identity-roles for that identity
				identityRolesToProcess = identityRoles;
			}

			List<AssignedRoleDto> assignedRoles = new ArrayList<>();
			identityRolesToProcess.forEach(identityRole -> {
				IdmFormInstanceDto formInstanceDto = roleAssignmentManager.getServiceForAssignment(identityRole).getRoleAttributeValues(identityRole);
				identityRole.getEavs().clear();
				identityRole.getEavs().add(formInstanceDto);
				// Convert identityRole to AssignedRoleDto
				assignedRoles.add(IdentityProvisioningExecutor.convertToAssignedRoleDto(identityRole));
			});

			return attributeMappingService.transformValueToResource(uid, assignedRoles, attribute, dto);
		}
		// For user-type (projection) will be attribute value IdmFormProjectionDto.
		if (attribute != null
				&& dto != null
				&& dto.getFormProjection() != null
				&& IdmIdentity_.formProjection.getName().equals(attribute.getIdmPropertyName())
				) {
			
			BaseDto projection = lookupService.lookupEmbeddedDto(dto, IdmIdentity_.formProjection);
			return attributeMappingService.transformValueToResource(uid, projection, attribute, dto);
		}
		// Default transformation of Identity state enum to string
		if (attribute != null && dto != null && IDENTITY_STATE_IDM_NAME.equals(attribute.getIdmPropertyName())) {
			String state = dto.getState().toString();
			return attributeMappingService.transformValueToResource(uid, state, attribute, dto);
		}
		
		return super.getAttributeValue(uid, dto, attribute, system, mappingContext, accountDto);
	}

	public static AssignedRoleDto convertToAssignedRoleDto(AbstractRoleAssignmentDto identityRole) {
		if (identityRole == null) {
			return null;
		}

		AssignedRoleDto dto = new AssignedRoleDto();
		dto.setId(identityRole.getId());
		dto.setExternalId(identityRole.getExternalId());
		dto.setValidFrom(identityRole.getValidFrom());
		dto.setValidTill(identityRole.getValidTill());
		dto.setRole(DtoUtils.getEmbedded(identityRole, AbstractRoleAssignment_.role, IdmRoleDto.class, null));
		dto.setIdentityContract(DtoUtils.getEmbedded(identityRole, IdmIdentityRole_.identityContract,
				IdmIdentityContractDto.class, null));
		dto.setContractPosition(DtoUtils.getEmbedded(identityRole, IdmIdentityRole_.contractPosition,
				IdmContractPositionDto.class, null));
		dto.setDirectRole(
				DtoUtils.getEmbedded(identityRole, IdmIdentityRole_.directRole, IdmIdentityRoleDto.class, null));
		dto.setRoleTreeNode(DtoUtils.getEmbedded(identityRole, IdmIdentityRoleDto.PROPERTY_ROLE_TREE_NODE,
				AbstractIdmAutomaticRoleDto.class, null));
		dto.setRoleComposition(DtoUtils.getEmbedded(identityRole, AbstractRoleAssignment_.roleComposition,
				IdmRoleCompositionDto.class, null));

		UUID definition = dto.getRole().getIdentityRoleAttributeDefinition();
		if (definition != null) {
			// Definition for role attributes exists
			IdmFormInstanceDto formInstanceDto = identityRole.getEavs() //
					.stream() //
					.filter(formInstance -> definition.equals(formInstance.getFormDefinition().getId())) //
					.findFirst() //
					.orElse(null);

			if (formInstanceDto != null) {
				List<IdmFormValueDto> values = formInstanceDto.getValues();
				values.stream() // Search all attributes
						.map(IdmFormValueDto::getFormAttribute) //
						.distinct() //
						.forEach(attribute -> {

							List<IdmFormValueDto> formValues = values.stream() // Search all values for one attribute
									.filter(value -> attribute.equals(value.getFormAttribute())) //
									.collect(Collectors.toList()); //
							IdmFormAttributeDto formAttributeDto = DtoUtils.getEmbedded(formValues.get(0), AbstractFormValue_.formAttribute, IdmFormAttributeDto.class);

							dto.getAttributes().put(formAttributeDto.getCode(), formValues.stream() //
									.map(IdmFormValueDto::getValue) // Value is always list
									.collect(Collectors.toList()) //
							);
						});
			}
		}

		return dto;

	}

	@Override
	@SuppressWarnings("unchecked")
	protected AccIdentityAccountFilter createEntityAccountFilter() {
		return new AccIdentityAccountFilter();
	}

	@Override
	@SuppressWarnings("unchecked")
	protected AccIdentityAccountService getEntityAccountService() {
		return identityAccountService;
	}

	@Override
	protected EntityAccountDto createEntityAccountDto() {
		return new AccIdentityAccountDto();
	}

	@Override
	protected ReadWriteDtoService<IdmIdentityDto, ?> getService() {
		return identityService;
	}

	@Override
	public String getSystemEntityType() {
		return SYSTEM_ENTITY_TYPE;
	}

	@Override
	public boolean supports(SystemEntityTypeRegistrable delimiter) {
		return delimiter.getSystemEntityCode().equals(SYSTEM_ENTITY_TYPE) && delimiter.isSupportsProvisioning();
	}
}
