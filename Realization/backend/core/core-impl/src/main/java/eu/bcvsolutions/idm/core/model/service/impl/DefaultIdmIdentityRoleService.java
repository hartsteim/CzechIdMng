package eu.bcvsolutions.idm.core.model.service.impl;

import eu.bcvsolutions.idm.core.api.service.AbstractReadDtoService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleSystemService;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.audit.service.SiemLoggerManager;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.repository.filter.FilterManager;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.api.utils.RepositoryUtils;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.InvalidFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.service.AbstractFormableService;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmAutomaticRole;
import eu.bcvsolutions.idm.core.model.entity.IdmAutomaticRoleAttribute;
import eu.bcvsolutions.idm.core.model.entity.IdmAutomaticRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.model.entity.IdmRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogueRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogueRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleComposition_;
import eu.bcvsolutions.idm.core.model.entity.IdmRole_;
import eu.bcvsolutions.idm.core.model.repository.IdmAutomaticRoleRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmIdentityRoleRepository;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;

/**
 * Operations with identity roles.
 * 
 * @author svanda
 * @author Radek Tomiška
 * @author Ondrej Kopr
 *
 */
public class DefaultIdmIdentityRoleService 
		extends AbstractFormableService<IdmIdentityRoleDto, IdmIdentityRole, IdmIdentityRoleFilter>
		implements IdmIdentityRoleService {

	private final IdmIdentityRoleRepository repository;
	//
	@Autowired private LookupService lookupService;
	@Autowired private IdmAutomaticRoleRepository automaticRoleRepository;
	@Autowired private IdmRoleService roleService;
	@SuppressWarnings("rawtypes")
	@Autowired private IdmRoleSystemService roleSystemService;
	@Autowired private FilterManager filterManager;

	@Autowired
	public DefaultIdmIdentityRoleService(
			IdmIdentityRoleRepository repository,
			FormService formService,
			EntityEventManager entityEventManager) {
		super(repository, entityEventManager, formService);
		//
		this.repository = repository;
	}
	
	@Override
	public AuthorizableType getAuthorizableType() {
		return new AuthorizableType(CoreGroupPermission.IDENTITYROLE, getEntityClass());
	}
	
	@Override
	public IdmFormInstanceDto getRoleAttributeValues(IdmIdentityRoleDto dto) {
		Assert.notNull(dto, "DTO is required.");
		// If given identity-role contains one formInstance, then will be returned
		List<IdmFormInstanceDto> eavs = dto.getEavs();
		if(eavs != null && eavs.size() == 1) {
			return eavs.get(0);
		}
		
		UUID roleId = dto.getRole();
		if (roleId != null) {
			IdmRoleDto role = DtoUtils.getEmbedded(dto, IdmIdentityRole_.role, IdmRoleDto.class);
			// Has role filled attribute definition?
			UUID formDefinition = role.getIdentityRoleAttributeDefinition();
			if (formDefinition != null) {
				IdmFormDefinitionDto formDefinitionDto = roleService.getFormAttributeSubdefinition(role);
				// thin dto can be given -> owner is normal dto
				return this.getFormService().getFormInstance(new IdmIdentityRoleDto(dto.getId()), formDefinitionDto);
			}
		}
		return null;
	}
	
	@Override
	public List<InvalidFormAttributeDto> validateFormAttributes(IdmIdentityRoleDto identityRole) {
		IdmFormInstanceDto formInstanceDto = this.getRoleAttributeValues(identityRole);
		if (formInstanceDto != null) {
			return this.getFormService().validate(formInstanceDto);
		}
		return null;
	}
	
	@Override
	protected List<IdmFormInstanceDto> getFormInstances(IdmIdentityRoleDto result, BasePermission... permission) {
		IdmFormInstanceDto formInstanceDto = getRoleAttributeValues(result);
		if (formInstanceDto != null) {
			// Validate the form instance
			formInstanceDto.setValidationErrors(getFormService().validate(formInstanceDto));
			return Lists.newArrayList(formInstanceDto);
		}
		return null;
	}
	
	@Override
	protected IdmIdentityRoleDto toDto(IdmIdentityRole entity, IdmIdentityRoleDto dto) {
		dto = super.toDto(entity, dto);
		if (dto == null) {
			return null;
		}
		
		IdmAutomaticRole automaticRole = entity.getAutomaticRole();
		if (automaticRole != null) {
			dto.setAutomaticRole(automaticRole.getId());
			BaseDto baseDto = null;
			Map<String, BaseDto> embedded = dto.getEmbedded();
			if (automaticRole instanceof IdmAutomaticRoleAttribute) {
				baseDto = lookupService.getDtoService(IdmAutomaticRoleAttributeDto.class).get(automaticRole.getId());
			} else {
				baseDto = lookupService.getDtoService(IdmRoleTreeNodeDto.class).get(automaticRole.getId());
			}
			embedded.put(IdmIdentityRole_.automaticRole.getName(), baseDto);
			dto.setEmbedded(embedded);
		}

		UUID roleSystemId = entity.getRoleSystem();
		if (roleSystemId != null) {
			Map<String, BaseDto> embedded = dto.getEmbedded();
			if (roleSystemService instanceof AbstractReadDtoService) {
				@SuppressWarnings("rawtypes")
				BaseDto baseDto = ((AbstractReadDtoService) roleSystemService).get(roleSystemId);
				embedded.put(IdmIdentityRole_.roleSystem.getName(), baseDto);
				dto.setEmbedded(embedded);
			}
		}
		
		return dto;
	}
	
	@Override
	protected IdmIdentityRole toEntity(IdmIdentityRoleDto dto, IdmIdentityRole entity) {
		IdmIdentityRole resultEntity = super.toEntity(dto, entity);
		// set additional automatic role
		if (resultEntity != null && dto.getAutomaticRole() != null) {
			// it isn't possible use lookupService entity lookup
			IdmAutomaticRole automaticRole = automaticRoleRepository.findById(dto.getAutomaticRole()).orElse(null);
			resultEntity.setAutomaticRole(automaticRole);
		}
		return resultEntity;
	}
	
	@Override
	protected List<Predicate> toPredicates(Root<IdmIdentityRole> root, CriteriaQuery<?> query, CriteriaBuilder builder, IdmIdentityRoleFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
		// quick - by identity's username
		String text = filter.getText();
		if (StringUtils.isNotEmpty(text)) {
			text = text.toLowerCase();
			predicates.add(
					builder.like(
							builder.lower(root.get(IdmIdentityRole_.identityContract).get(IdmIdentityContract_.identity).get(IdmIdentity_.username)),
							"%" + text + "%")
					);
		}
		// by role text
		String roleText = filter.getRoleText();
		if (StringUtils.isNotEmpty(roleText)) {
			IdmRoleFilter subFilter = new IdmRoleFilter();
			subFilter.setText(roleText);
			Subquery<IdmRole> subquery = query.subquery(IdmRole.class);
			Root<IdmRole> subRoot = subquery.from(IdmRole.class);
			subquery.select(subRoot);
			//
			Predicate rolePredicate = filterManager
					.getBuilder(IdmRole.class, IdmRoleFilter.PARAMETER_TEXT)
					.getPredicate(subRoot, subquery, builder, subFilter);
			//
			subquery.where(
	                builder.and(
	                		builder.equal(root.get(IdmIdentityRole_.role), subRoot), // correlation attr
	                		rolePredicate
	                )
	        );
			//		
			predicates.add(builder.exists(subquery));
		}
		List<UUID> identities = filter.getIdentities();
		if (!identities.isEmpty()) {
			predicates.add(
					root.get(IdmIdentityRole_.identityContract).get(IdmIdentityContract_.identity).get(IdmIdentity_.id).in(identities) 
			);
		}
		List<UUID> roles = filter.getRoles();
		if (!roles.isEmpty()) {
			predicates.add(root.get(IdmIdentityRole_.role).get(IdmRole_.id).in(roles));
		}
		List<String> roleEnvironments = filter.getRoleEnvironments();
		if (CollectionUtils.isNotEmpty(roleEnvironments)) {
			predicates.add(root.get(IdmIdentityRole_.role).get(IdmRole_.environment).in(roleEnvironments));
		}
		UUID roleCatalogueId = filter.getRoleCatalogueId();
		if (roleCatalogueId != null) {
			Subquery<IdmRoleCatalogueRole> roleCatalogueRoleSubquery = query.subquery(IdmRoleCatalogueRole.class);
			Root<IdmRoleCatalogueRole> subRootRoleCatalogueRole = roleCatalogueRoleSubquery.from(IdmRoleCatalogueRole.class);
			roleCatalogueRoleSubquery.select(subRootRoleCatalogueRole);
			
			roleCatalogueRoleSubquery.where(
                    builder.and(
                    		builder.equal(subRootRoleCatalogueRole.get(IdmRoleCatalogueRole_.role), root.get(IdmIdentityRole_.role)),
                    		builder.equal(subRootRoleCatalogueRole.get(IdmRoleCatalogueRole_.roleCatalogue).get(AbstractEntity_.id), roleCatalogueId)
                    		));
			predicates.add(builder.exists(roleCatalogueRoleSubquery));
		}
		//
		Boolean valid = filter.getValid();
		if (valid != null) {
			// Only valid identity-role include check on contract validity too
			if (valid) {
				final LocalDate today = LocalDate.now();
				predicates.add(
						builder.and(
								RepositoryUtils.getValidPredicate(root, builder, today),
								RepositoryUtils.getValidPredicate(root.get(IdmIdentityRole_.identityContract), builder, today),
								builder.equal(root.get(IdmIdentityRole_.identityContract).get(IdmIdentityContract_.disabled), Boolean.FALSE)
						));
			}
			// Only invalid identity-role
			if (!valid) {
				final LocalDate today = LocalDate.now();
				predicates.add(
						builder.or(
								builder.not(RepositoryUtils.getValidPredicate(root, builder, today)),
								builder.not(RepositoryUtils.getValidPredicate(root.get(IdmIdentityRole_.identityContract), builder, today)),
								builder.equal(root.get(IdmIdentityRole_.identityContract).get(IdmIdentityContract_.disabled), Boolean.TRUE)
								)
						);
			}
		}
		//
		// is automatic role
		Boolean automaticRole = filter.getAutomaticRole();
		if (automaticRole != null) {
			if (automaticRole) {
				predicates.add(builder.isNotNull(root.get(IdmIdentityRole_.automaticRole)));
			} else {
				predicates.add(builder.isNull(root.get(IdmIdentityRole_.automaticRole)));
			}
		}
		//
		UUID automaticRoleId = filter.getAutomaticRoleId();
		if (automaticRoleId != null) {
			predicates.add(builder.equal(
					root.get(IdmIdentityRole_.automaticRole).get(IdmAutomaticRole_.id), 
					automaticRoleId)
					);
		}
		//
		UUID identityContractId = filter.getIdentityContractId();
		if (identityContractId != null) {
			predicates.add(builder.equal(
					root.get(IdmIdentityRole_.identityContract).get(AbstractEntity_.id), 
					identityContractId)
					);
		}
		//
		UUID contractPositionId = filter.getContractPositionId();
		if (contractPositionId != null) {
			predicates.add(builder.equal(
					root.get(IdmIdentityRole_.contractPosition).get(AbstractEntity_.id), 
					contractPositionId)
					);
		}
		//
		UUID directRoleId = filter.getDirectRoleId();
		if (directRoleId != null) {
			predicates.add(builder.equal(root.get(IdmIdentityRole_.directRole).get(IdmIdentityRole_.id), directRoleId));
		}
		//
		UUID roleCompositionId = filter.getRoleCompositionId();
		if (roleCompositionId != null) {
			predicates.add(builder.equal(root.get(IdmIdentityRole_.roleComposition).get(IdmRoleComposition_.id), roleCompositionId));
		}
		//
		// is direct role
		Boolean directRole = filter.getDirectRole();
		if (directRole != null) {
			if (directRole) {
				predicates.add(builder.isNull(root.get(IdmIdentityRole_.directRole)));
			} else {
				predicates.add(builder.isNotNull(root.get(IdmIdentityRole_.directRole)));
			}
		}
		// Role-system
		UUID roleSystemId = filter.getRoleSystemId();
		if (roleSystemId != null) {
			predicates.add(builder.equal(root.get(IdmIdentityRole_.roleSystem), roleSystemId));
		}
		return predicates;
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<IdmIdentityRoleDto> findAllByIdentity(UUID identityId) {
		return toDtos(repository.findAllByIdentityContract_Identity_Id(identityId, getDefaultSort()), false);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<IdmIdentityRoleDto> findAllByContract(UUID identityContractId) {
		Assert.notNull(identityContractId, "contract identifier is required.");
		//
		return toDtos(repository.findAllByIdentityContract_Id(identityContractId, getDefaultSort()), false);
	}
	
	@Override
	public List<IdmIdentityRoleDto> findAllByContractPosition(UUID contractPositionId) {
		Assert.notNull(contractPositionId, "contract position identifier is required.");
		//
		IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
		filter.setContractPositionId(contractPositionId);
		//
		return find(filter, null).getContent();
	}
	
	@Override
	@Transactional(readOnly = true)
	public Page<IdmIdentityRoleDto> findByAutomaticRole(UUID automaticRoleId, Pageable pageable) {
		return toDtoPage(repository.findByAutomaticRole_Id(automaticRoleId, pageable));
	}
	
	@Override
	@Transactional(readOnly = true)
	public Page<IdmIdentityRoleDto> findValidRoles(UUID identityId, Pageable pageable) {
		IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();
		identityRoleFilter.setValid(Boolean.TRUE);
		identityRoleFilter.setIdentityId(identityId);
		//
		return this.find(identityRoleFilter, pageable);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<IdmIdentityRoleDto> findExpiredRoles(LocalDate expirationDate, Pageable page) {
		Assert.notNull(expirationDate, "Expiration date is required.");
		//
		return toDtoPage(repository.findExpiredRoles(expirationDate, page));
	}
	
	@Override
	public Page<IdmIdentityRoleDto> findDirectExpiredRoles(LocalDate expirationDate, Pageable page) {
		Assert.notNull(expirationDate, "Expiration date is required.");
		//
		return toDtoPage(repository.findDirectExpiredRoles(expirationDate, page));
	}
	
	@Override
	public List<UUID> findDirectExpiredRoleIds(LocalDate expirationDate) {
		Assert.notNull(expirationDate, "Expiration date is required.");
		//
		return repository.findDirectExpiredRoleIds(expirationDate);
	}

	@Override
	public IdmIdentityRoleDto getDuplicated(IdmIdentityRoleDto one, IdmIdentityRoleDto two, Boolean skipSubdefinition) {

		if (!one.getRole().equals(two.getRole())) {
			// Role isn't same
			return null;
		}
		
		if (!one.getIdentityContract().equals(two.getIdentityContract())) {
			// Contract isn't same
			return null;
		}

		// Role-system isn't same.
		if (one.getRoleSystem() == null) {
			if (two.getRoleSystem() != null) {
				return null;
			}
		} else if (!one.getRoleSystem().equals(two.getRoleSystem())) {
			return null;
		}
		
		
		IdmIdentityRoleDto manually = null;
		IdmIdentityRoleDto automatic = null;
		
		if (isRoleAutomaticOrComposition(one)) {
			automatic = one;
			manually = two;
		}

		if (isRoleAutomaticOrComposition(two)) {
			if (automatic != null) {
				// Automatic role is set from role ONE -> Both identity roles are automatic
				if (one.getDirectRole() == null 
						|| two.getDirectRole() == null
						|| one.getRoleComposition() == null 
						|| two.getRoleComposition() == null) {
					// role was not created by business role definition
					return null;
				}
				if (Objects.equal(one.getDirectRole(), two.getDirectRole())
						&& Objects.equal(one.getRoleComposition(), two.getRoleComposition())) {
					// #2034 compositon is duplicate
					return getIdentityRoleForRemove(one, two);
				}
				// automatic roles or composition is not duplicate
				return null;
			}
			automatic = two;
			manually = one;
		}

		/// Check duplicity for validity
		IdmIdentityRoleDto validityDuplicity = null;
		if (automatic == null) {
			// Check if ONE role is dupliciti with TWO and change order
			boolean duplicitOne = isIdentityRoleDatesDuplicit(one, two);
			boolean duplicitTwo = isIdentityRoleDatesDuplicit(two, one);

			if (duplicitOne && duplicitTwo) {
				// Both roles are same call method for decide which role will be removed
				validityDuplicity = getIdentityRoleForRemove(one, two);
			} else if (duplicitOne) {
				// Only role ONE is duplicit with TWO
				validityDuplicity = one;
			} else if (duplicitTwo) {
				// Only role TWO is duplicit with ONE
				validityDuplicity = two;
			}
		} else {
			// In case that we have only manually and automatic compare only from one order
			if (isIdentityRoleDatesDuplicit(manually, automatic)) {
				validityDuplicity = manually;
			}
			
		}

		// Check subdefinition can be skipped
		// and must be checked after validity
		if (BooleanUtils.isNotTrue(skipSubdefinition)) {
			// Validity must be same and subdefinition also. Then is possible remove role.
			// Subdefinition must be exactly same and isn't different between manually and automatic identity role
			if (validityDuplicity != null && equalsSubdefinitions(one, two)) {
				return validityDuplicity;
			}
		} else {
			// Check for subdefintion is skipped return only duplicity
			return validityDuplicity;
		}

		// No duplicity founded
		return null;
	}

	/**
	 * Check if given {@link IdmIdentityRoleDto} is automatic or business role.
	 *
	 * @param identityRole
	 * @return
	 */
	@Override
	public boolean isRoleAutomaticOrComposition(IdmIdentityRoleDto identityRole) {
		return identityRole.getAutomaticRole() != null || identityRole.getDirectRole() != null;
	}
	
	/**
	 * Method provides specific logic for role assignment siem logging.
	 * 
	 */
	@Override
	protected void siemLog(EntityEvent<IdmIdentityRoleDto> event, String status, String detail) {
		if (event == null) {
			return;
		}
		IdmIdentityRoleDto dto = event.getContent();
		String operationType = event.getType().name();
		String action = siemLoggerManager.buildAction(SiemLoggerManager.ROLE_ASSIGNMENT_LEVEL_KEY, operationType);
		if(siemLoggerManager.skipLogging(action)) {
			return;
		}
		IdmIdentityContractDto contractDto = lookupService.lookupEmbeddedDto(dto, IdmIdentityRole_.identityContract.getName());
		IdmRoleDto subjectDto = lookupService.lookupEmbeddedDto(dto, IdmIdentityRole_.role.getName());
		IdmIdentityDto targetDto = lookupService.lookupEmbeddedDto(contractDto, IdmIdentityContract_.identity.getName());		
		String transactionUuid = java.util.Objects.toString(dto.getTransactionId(),"");
		siemLog(action, status, targetDto, subjectDto, transactionUuid, detail);
	}

	/**
	 * Method decides identity role that will be removed if both roles are same.
	 * In default behavior is for removing choosen the newer. Method is protected for easy
	 * overriding.
	 *
	 * @param one
	 * @param two
	 * @return
	 */
	protected IdmIdentityRoleDto getIdentityRoleForRemove(IdmIdentityRoleDto one, IdmIdentityRoleDto two) {
		// Both roles are same, remove newer
		if (one.getCreated().isAfter(two.getCreated())) {
			return one;
		}
		return two;
	}

	/**
	 * Check if role ONE is duplicit by date with role TWO. For example if is role ONE fully in interval of validite the
	 * role TWO.
	 *
	 * @param one
	 * @param two
	 * @return
	 */
	private boolean isIdentityRoleDatesDuplicit(IdmIdentityRoleDto one, IdmIdentityRoleDto two) {
		LocalDate validTillForFirst = getDateForValidTill(one);
		// Validity role is in interval in a second role
		if (isDatesInRange(one.getValidFrom(), validTillForFirst, two.getValidFrom(), two.getValidTill())) {
			return true;
		}
		
		// Both role are valid
		if (one.isValid() && two.isValid()) {
			LocalDate validTillForTwo = two.getValidTill();
			if ((validTillForFirst == null && validTillForTwo == null) ||
					(validTillForFirst != null && validTillForTwo != null && validTillForFirst.isEqual(validTillForTwo))) {
				// Valid tills from both identity roles are same
				return true;
			} else if (validTillForFirst != null && validTillForTwo == null) {
				// Second identity role has filled valid till but first not.
				// This mean that role TWO has bigger validity till than ONE
				return false;
			} else if (validTillForFirst != null && validTillForFirst.isBefore(validTillForTwo)) {
				// Valid till from manually role is before automatic, manually role could be removed
				return true;
			}
		}
		return false;
	}

	/**
	 * Get valid till for {@link IdmIdentityRoleDto}. Valid till could be set from contract if
	 * date is after valid till from contract.
	 *
	 * @param identityRole
	 * @return
	 */
	private LocalDate getDateForValidTill(IdmIdentityRoleDto identityRole) {
		LocalDate validTill = identityRole.getValidTill();
		IdmIdentityContractDto identityContractDto = DtoUtils.getEmbedded(identityRole, IdmIdentityRoleDto.PROPERTY_IDENTITY_CONTRACT, IdmIdentityContractDto.class, null);
		LocalDate validTillContract = identityContractDto.getValidTill();

		if (validTill != null && validTillContract != null && validTillContract.isAfter(validTill)) {
			return validTill;
		}

		if (validTillContract == null && validTill != null) {
			return validTill;
		}

		return validTillContract;
	}
	
	/**
	 * Default sort by role's name
	 * 
	 * @return
	 */
	private Sort getDefaultSort() {
		return Sort.by(IdmIdentityRole_.role.getName() + "." + IdmRole_.code.getName());
	}

	/**
	 * Check if given dates is in range/interval the second ones.
	 *
	 * @param validFrom
	 * @param validTill
	 * @param rangeFrom
	 * @param rangeTill
	 * @return
	 */
	private boolean isDatesInRange(LocalDate validFrom, LocalDate validTill, LocalDate rangeFrom, LocalDate rangeTill) {
		boolean leftIntervalSideOk = false;
		boolean rightIntervalSideOk = false;

		if (rangeFrom == null || (validFrom != null && (rangeFrom.isBefore(validFrom) || rangeFrom.isEqual(validFrom)))) {
			leftIntervalSideOk = true;
		}

		if (rangeTill == null || (validTill != null && (rangeTill.isAfter(validTill) || rangeTill.isEqual(validTill)))) {
			rightIntervalSideOk = true;
		}

		return leftIntervalSideOk && rightIntervalSideOk;
	}

	/**
	 * Compare subdefinition. Return true if subdefinition are same. If {@link IdmIdentityRoleDto} doesn't contain subdefinition
	 * return true.
	 *
	 * @param one
	 * @param two
	 * @return
	 */
	private boolean equalsSubdefinitions(IdmIdentityRoleDto one, IdmIdentityRoleDto two) {
		
		List<IdmFormInstanceDto> eavsOne = one.getEavs();
		List<IdmFormInstanceDto> eavsTwo = two.getEavs();

		// Size of form instance doesn't match
		if (eavsOne.size() != eavsTwo.size()) {
			return false;
		}

		// Form instances are empty, subdefiniton are equals
		if (eavsOne.isEmpty() && eavsTwo.isEmpty()) {
			return true;
		}
		
		// Now is possible only one form instance for identity role
		// Get form instance from both identity roles
		IdmFormInstanceDto formInstanceOne = eavsOne.get(0);
		IdmFormInstanceDto formInstanceTwo = eavsTwo.get(0);

		List<Serializable> oneValues = Collections.emptyList();
		List<Serializable> twoValues = Collections.emptyList();
		if (formInstanceOne != null) {
			oneValues = eavsOne.get(0) //
					.getValues() //
					.stream() //
					.map(IdmFormValueDto::getValue) //
					.collect(Collectors.toList()); //
		}
		if (formInstanceTwo != null) {
			twoValues = eavsTwo.get(0) //
					.getValues() //
					.stream() //
					.map(IdmFormValueDto::getValue) //
					.collect(Collectors.toList()); //
		}

		// Values doesn't match
		if (oneValues.size() != twoValues.size()) {
			return false;
		}

		// Compare collections
		return CollectionUtils.isEqualCollection(oneValues, twoValues);
	}
}
