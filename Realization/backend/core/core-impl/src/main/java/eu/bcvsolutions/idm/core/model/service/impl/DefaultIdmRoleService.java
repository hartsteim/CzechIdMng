package eu.bcvsolutions.idm.core.model.service.impl;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import eu.bcvsolutions.idm.core.api.config.domain.RequestConfiguration;
import eu.bcvsolutions.idm.core.api.config.domain.RoleConfiguration;
import eu.bcvsolutions.idm.core.api.domain.RoleType;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleFormAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleSystemDto;
import eu.bcvsolutions.idm.core.api.dto.filter.BaseFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFormAttributeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleSystemFilter;
import eu.bcvsolutions.idm.core.api.service.AbstractReadDtoService;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleFormAttributeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleSystemService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.service.AbstractFormableService;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormDefinition_;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmForestIndexEntity_;
import eu.bcvsolutions.idm.core.model.entity.IdmRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogue;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogueRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogueRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogue_;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleComposition;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleComposition_;
import eu.bcvsolutions.idm.core.model.entity.IdmRole_;
import eu.bcvsolutions.idm.core.model.event.RoleEvent;
import eu.bcvsolutions.idm.core.model.repository.IdmRoleRepository;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Default role service
 * - supports {@link RoleEvent}
 * 
 * @author Radek Tomiška
 *
 */
public class DefaultIdmRoleService 
		extends AbstractFormableService<IdmRoleDto, IdmRole, IdmRoleFilter> 
		implements IdmRoleService {

	private final IdmRoleRepository repository;
	//
	@Autowired private ConfigurationService configurationService;
	@Autowired private RoleConfiguration roleConfiguration;
	@Autowired private IdmRoleCatalogueRoleService roleCatalogueRoleService;
	@Autowired @Lazy private IdmIdentityService identityService;
	@Autowired private IdmRoleFormAttributeService roleFormAttributeService;
	@Autowired private RequestConfiguration requestConfiguration;
	@Autowired(required = false) private IdmRoleSystemService roleSystemService;
	
	@Autowired
	public DefaultIdmRoleService(
			IdmRoleRepository repository,
			EntityEventManager entityEventManager,
			FormService formService) {
		super(repository, entityEventManager, formService);
		//
		this.repository = repository;
	}
	
	@Override
	public AuthorizableType getAuthorizableType() {
		return new AuthorizableType(CoreGroupPermission.ROLE, getEntityClass());
	}
	
	@Override
	@Transactional
	public IdmRoleDto saveInternal(IdmRoleDto dto) {
		if (StringUtils.isEmpty(dto.getName())) {
			dto.setName(dto.getCode());
		} else if (StringUtils.isEmpty(dto.getCode())) {
			dto.setCode(dto.getName());
		}
		//
		return super.saveInternal(dto);
	}

	@Override
	public boolean supports(Class<?> delimiter) {
		// We need to use equals, because IdmRoleDto is parent of IdmRoleThinDto.
		return getDtoClass().equals(delimiter);
	}

	@Override
	@Transactional(readOnly = true)
	public IdmRoleDto getByCode(String code) {
		return toDto(repository.findOneByCode(code));
	}
	
	@Override
	@Transactional(readOnly = true)
	public IdmRoleDto getByBaseCodeAndEnvironment(String baseCode, String environment) {
		IdmRoleDto role = new IdmRoleDto();
		role.setBaseCode(baseCode);
		role.setEnvironment(environment);
		//
		String code = getCodeWithEnvironment(role);
		// lookout: filter cannot be used - environment could be empty (role without environment is correct state)
		return getByCode(code);
	}
	
	@Override
	public IdmFormDefinitionDto getFormAttributeSubdefinition(IdmRoleDto role) {
		Assert.notNull(role, "Role is required.");
		UUID identityRoleAttributeDefinition = role.getIdentityRoleAttributeDefinition();
		if(identityRoleAttributeDefinition == null) {
			return null;
		}
		IdmFormDefinitionDto definition = this.getFormService().getDefinition(identityRoleAttributeDefinition);
		List<IdmFormAttributeDto> allAttributes = definition.getFormAttributes();
		// Find sub-definition for given role
		IdmRoleFormAttributeFilter attributeFilter = new IdmRoleFormAttributeFilter();
		attributeFilter.setRole(role.getId());
		List<IdmRoleFormAttributeDto> roleFormAttributes = roleFormAttributeService.find(attributeFilter, null).getContent();
		
		// Find allowed attributes by sub-definition (and set default value from sub-definition attribute)
		List<IdmFormAttributeDto> allowedAttributes = allAttributes.stream() //
				.filter(attribute -> {
					IdmRoleFormAttributeDto result =  roleFormAttributes.stream() //
							.filter(roleFormAttribute -> attribute.getId().equals(roleFormAttribute.getFormAttribute()))
							.findFirst() //
							.orElse(null); //
					if (result != null) {
						// Set default value from sub-definition attribute
						attribute.setDefaultValue(result.getDefaultValue());
						// Set validations
						attribute.setRequired(result.isRequired());
						attribute.setUnique(result.isUnique());
						attribute.setMin(result.getMin());
						attribute.setMax(result.getMax());
						attribute.setRegex(result.getRegex());
						attribute.setValidationMessage(result.getValidationMessage());
						return true;
					}
					return false;
				}).collect(Collectors.toList());
		definition.setFormAttributes(allowedAttributes);
		return definition;
	}

	@Override
	public boolean supportsToDtoWithFilter() {
		return true;
	}

	@Override
	protected IdmRoleDto toDto(IdmRole entity, IdmRoleDto dto, IdmRoleFilter context) {
		IdmRoleDto roleDto = super.toDto(entity, dto, context);
		// Adds to result count of systems for this role, which are in cross-domain group.
		if (context != null
				&& context.getIncludeCrossDomainsSystemsCount() != null
				&& context.getIncludeCrossDomainsSystemsCount()
				&& roleDto != null
				&& roleDto.getId() != null
				&& roleSystemService instanceof AbstractReadDtoService) {
			@SuppressWarnings(value = "rawtypes")
			AbstractReadDtoService roleSystemService = (AbstractReadDtoService) this.roleSystemService;
			BaseFilter roleSystemFilter = roleSystemService.createFilterInstance();
			if (roleSystemFilter instanceof IdmRoleSystemFilter) {
				IdmRoleSystemFilter idmRoleSystemFilter = (IdmRoleSystemFilter) roleSystemFilter;
				idmRoleSystemFilter.setIsInCrossDomainGroupRoleId(roleDto.getId());
				// Permission: User can read role -> can read connected systems.
				long count = roleSystemService.count(idmRoleSystemFilter);
				roleDto.setSystemsInCrossDomains(count);
				List<IdmRoleSystemDto> roleSystems = roleSystemService.find(idmRoleSystemFilter, null).getContent();
				IdmRoleSystemDto roleSystemDto = roleSystems.stream().findFirst().orElse(null);
				if (roleSystemDto != null) {
					roleDto.setPrefilledSystemInCrossDomains(roleSystemDto.getId());
				}
			}
		}

		return roleDto;
	}

	@Override
	protected List<Predicate> toPredicates(Root<IdmRole> root, CriteriaQuery<?> query, CriteriaBuilder builder, IdmRoleFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
		//
		// role type
		RoleType roleType = filter.getRoleType();
		if (roleType != null) {
			predicates.add(builder.equal(root.get(IdmRole_.roleType), roleType));
		}
		// role catalogue by forest index
		UUID roleCatalogueId = filter.getRoleCatalogueId();
		if (roleCatalogueId != null) {
			Subquery<IdmRoleCatalogueRole> subquery = query.subquery(IdmRoleCatalogueRole.class);
			Root<IdmRoleCatalogueRole> subRoot = subquery.from(IdmRoleCatalogueRole.class);
			subquery.select(subRoot);
			
			Subquery<IdmRoleCatalogue> subqueryCatalogue = query.subquery(IdmRoleCatalogue.class);
			Root<IdmRoleCatalogue> subRootCatalogue = subqueryCatalogue.from(IdmRoleCatalogue.class);
			subqueryCatalogue.select(subRootCatalogue);
			subqueryCatalogue.where(
					builder.and(
							builder.equal(subRootCatalogue.get(IdmRoleCatalogue_.id), roleCatalogueId),
							builder.between(
                    				subRoot.get(IdmRoleCatalogueRole_.roleCatalogue).get(IdmRoleCatalogue_.forestIndex).get(IdmForestIndexEntity_.lft), 
                    				subRootCatalogue.get(IdmRoleCatalogue_.forestIndex).get(IdmForestIndexEntity_.lft),
                    				subRootCatalogue.get(IdmRoleCatalogue_.forestIndex).get(IdmForestIndexEntity_.rgt)
                    		)
					));				

			subquery.where(
                    builder.and(
                    		builder.equal(subRoot.get(IdmRoleCatalogueRole_.role), root), // correlation attr
                    		builder.exists(subqueryCatalogue)
                    		)
                    );
			predicates.add(builder.exists(subquery));
		}
		// environment
		List<String> environments = filter.getEnvironments();
		if (!CollectionUtils.isEmpty(environments)) {
			predicates.add(root.get(IdmRole_.environment).in(environments));
		}
		// baseCode
		String baseCode = filter.getBaseCode();
		if (StringUtils.isNotEmpty(baseCode)) {
			predicates.add(builder.equal(root.get(IdmRole_.baseCode), baseCode));
		}
		UUID parent = filter.getParent();
		if (parent != null) {
			Subquery<IdmRoleComposition> subquery = query.subquery(IdmRoleComposition.class);
			Root<IdmRoleComposition> subRoot = subquery.from(IdmRoleComposition.class);
			subquery.select(subRoot);
			subquery.where(
                    builder.and(
                    		builder.equal(subRoot.get(IdmRoleComposition_.sub), root), // correlation attr
                    		builder.equal(subRoot.get(IdmRoleComposition_.superior).get(IdmRole_.id), parent)
                    		)
            );
			//
			predicates.add(builder.exists(subquery));
		}
		// form definition for role attributes
		UUID definitionId = filter.getAttributeFormDefinitionId();
		if (definitionId != null) {
			predicates.add(builder.equal(root.get(IdmRole_.identityRoleAttributeDefinition).get(IdmFormDefinition_.id),
					definitionId));
		}
		// without role catalogue
		Boolean withoutCatalogue = filter.getWithoutCatalogue();
		if (withoutCatalogue != null) {
			Subquery<IdmRoleCatalogueRole> subquery = query.subquery(IdmRoleCatalogueRole.class);
			Root<IdmRoleCatalogueRole> subRoot = subquery.from(IdmRoleCatalogueRole.class);
			subquery.select(subRoot);
			subquery.where(builder.equal(subRoot.get(IdmRoleCatalogueRole_.role), root));
			//
			if (withoutCatalogue) { // without
				predicates.add(builder.not(builder.exists(subquery)));
			} else { // with some
				predicates.add(builder.exists(subquery));
			}
		}
		//
		return predicates;
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<IdmRoleDto> getRolesByIds(String roles) {
		if (roles == null) {
			return null;
		}
		String[] rolesArray = roles.split(",");
		List<IdmRoleDto> idmRoles = new ArrayList<>(rolesArray.length);
		for (String id : rolesArray) {
			idmRoles.add(get(id));
		}
		return idmRoles;
	}
	
	@Override
	public String findAssignRoleWorkflowDefinition(UUID roleId){
		if (roleId == null) {
			// role can be removed in the mean time
			return null;
		}
		
		String key =  configurationService.getValue(WF_BY_ROLE_PRIORITY_PREFIX + this.get(roleId).getPriority());
		return Strings.isNullOrEmpty(key) ? null : key;
	}

	@Override
	public String findChangeAssignRoleWorkflowDefinition(UUID roleId){
		Assert.notNull(roleId, "Role ID is required!");
	
		String key =  configurationService.getValue(WF_BY_ROLE_PRIORITY_PREFIX + this.get(roleId).getPriority());
		return Strings.isNullOrEmpty(key) ? null : key;
	}
	
	@Override
	public String findUnAssignRoleWorkflowDefinition(UUID roleId){
		Assert.notNull(roleId, "Role ID is required!");
		String key = null;
		if(this.get(roleId).isApproveRemove()){
			key =  configurationService.getValue(WF_BY_ROLE_PRIORITY_PREFIX + "remove");
		}
		return Strings.isNullOrEmpty(key) ? null : key;
	}

	@Override
	@Transactional(readOnly = true)
	public IdmRoleDto getDefaultRole() {
		return roleConfiguration.getDefaultRole();
	}

	@Override
	@Transactional(readOnly = true)
	public IdmRoleDto getAdminRole() {
		return roleConfiguration.getAdminRole();
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<IdmRoleDto> findAllByRoleCatalogue(UUID roleCatalogueId) {
		Assert.notNull(roleCatalogueId, "Role catalogue identifier is required.");
		//
		IdmRoleCatalogueRoleFilter filter = new IdmRoleCatalogueRoleFilter();
		filter.setRoleCatalogueId(roleCatalogueId);
		//
		return roleCatalogueRoleService
			.find(filter, null)
			.getContent()
			.stream()
			.map(roleCatalogueRole -> DtoUtils.getEmbedded(roleCatalogueRole, IdmRoleCatalogueRole_.role, IdmRoleDto.class))
			.collect(Collectors.toList());
	}
	
	@Override
	public Page<IdmIdentityDto> findApproversByRoleId(UUID roleId, Pageable pageable) {
		Assert.notNull(roleId, "Role is required.");
		
		IdmRoleDto role = this.get(roleId);
		// Given role should be null (new role created with request)
		if (role != null) {
			IdmIdentityFilter filter = new IdmIdentityFilter();
			// Set guarantee type (if is defined).
			filter.setGuaranteeType(requestConfiguration.getRequestApprovalGuaranteeType(IdmRoleDto.class));
			filter.setGuaranteesForRole(roleId);
			long guaranteesCount = identityService.find(filter, pageable).getTotalElements();
			if (guaranteesCount > 0) {
				return identityService.find(filter, pageable);
			}
		}

		IdmRoleDto approvingRole = roleConfiguration.getRoleForApproveChangeOfRole();

		if (approvingRole != null) {
			IdmIdentityFilter filter = new IdmIdentityFilter();
			filter.setRoles(Lists.newArrayList(approvingRole.getId()));

			return identityService.find(filter, pageable);
		}

		return new PageImpl<IdmIdentityDto>(Lists.newArrayList());
	}
	
	@Override
	public String getCodeWithoutEnvironment(IdmRoleDto role) {
		Assert.notNull(role, "Role is required.");
		//
		if (role.getCode() == null) {
			return role.getBaseCode();
		}
		if (StringUtils.isEmpty(role.getEnvironment())) {
			return role.getCode();
		}
		//
		return role.getCode().replaceAll(String.format("%s%s$", roleConfiguration.getCodeEnvironmentSeperator(), role.getEnvironment()), "");
	}
	
	@Override
	public String getCodeWithEnvironment(IdmRoleDto role) {
		Assert.notNull(role, "Role is required.");
		//
		String code = role.getBaseCode() == null ? role.getCode() : role.getBaseCode();
		if (StringUtils.isEmpty(role.getEnvironment())) {
			return code;
		}
		//
		return String.format("%s%s%s", code, roleConfiguration.getCodeEnvironmentSeperator(), role.getEnvironment());
	}
}
