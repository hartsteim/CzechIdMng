package eu.bcvsolutions.idm.core.model.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.api.dto.IdmAuthorizationPolicyDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmAuthorizationPolicyFilter;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.AbstractEventableDtoService;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.IdmAuthorizationPolicyService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCompositionService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.ModuleService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmAuthorizationPolicy;
import eu.bcvsolutions.idm.core.model.entity.IdmAuthorizationPolicy_;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleComposition_;
import eu.bcvsolutions.idm.core.model.entity.IdmRole_;
import eu.bcvsolutions.idm.core.model.repository.IdmAuthorizationPolicyRepository;
import eu.bcvsolutions.idm.core.security.api.domain.DefaultGrantedAuthority;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmGroupPermission;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;

/**
 * Assign authorization evaluator to role.
 * 
 * @author Radek Tomiška
 *
 */
public class DefaultIdmAuthorizationPolicyService 
		extends AbstractEventableDtoService<IdmAuthorizationPolicyDto, IdmAuthorizationPolicy, IdmAuthorizationPolicyFilter> 
		implements IdmAuthorizationPolicyService {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DefaultIdmAuthorizationPolicyService.class);
	private final IdmAuthorizationPolicyRepository repository;
	private final IdmRoleService roleService;
	private final ModuleService moduleService;
	//
	@Autowired private IdmRoleCompositionService roleCompositionService;
	
	public DefaultIdmAuthorizationPolicyService(
			IdmAuthorizationPolicyRepository repository, 
			IdmRoleService roleService,
			ModuleService moduleService,
			EntityEventManager eventManager) {
		super(repository, eventManager);
		//
		Assert.notNull(roleService, "Service is required.");
		Assert.notNull(moduleService, "Service is required.");
		Assert.notNull(eventManager, "Manager is required.");
		//
		this.repository = repository;
		this.roleService = roleService;
		this.moduleService = moduleService;
	}
	
	@Override
	public AuthorizableType getAuthorizableType() {
		return new AuthorizableType(CoreGroupPermission.AUTHORIZATIONPOLICY, getEntityClass());
	}
	
	@Override
	public IdmAuthorizationPolicyDto saveInternal(IdmAuthorizationPolicyDto dto) {
		if (StringUtils.isNotEmpty(dto.getAuthorizableType()) && StringUtils.isEmpty(dto.getGroupPermission())) {
			throw new ResultCodeException(CoreResultCode.AUTHORIZATION_POLICY_GROUP_AUTHORIZATION_TYPE, 
					ImmutableMap.of("authorizableType", dto.getAuthorizableType(), "groupPermission", dto.getGroupPermission()));
		}
		//
		return super.saveInternal(dto);
	}

	@Override
	@Transactional(readOnly = true)
	public List<IdmAuthorizationPolicyDto> getEnabledPolicies(UUID identityId, Class<? extends Identifiable> entityType) {
		Assert.notNull(entityType, "Entity type is required.");
		//
		List<IdmAuthorizationPolicyDto> results = toDtos(repository.getPolicies(identityId, entityType.getCanonicalName(), false, LocalDate.now()), false);
		results.addAll(getDefaultPolicies(entityType));
		return results;
	}
	
	@Override
	protected List<Predicate> toPredicates(Root<IdmAuthorizationPolicy> root, CriteriaQuery<?> query, CriteriaBuilder builder, IdmAuthorizationPolicyFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
		// like in evaluator type
		String text = filter.getText();
		if (StringUtils.isNotEmpty(text)) {
			text = text.toLowerCase();
			predicates.add(builder.or(
					builder.like(builder.lower(root.get(IdmAuthorizationPolicy_.authorizableType)), "%" + text + "%"),
					builder.like(builder.lower(root.get(IdmAuthorizationPolicy_.groupPermission)), "%" + text + "%"),
					builder.like(builder.lower(root.get(IdmAuthorizationPolicy_.evaluatorType)), "%" + text + "%"),
					builder.like(builder.lower(root.get(IdmAuthorizationPolicy_.description)), "%" + text + "%")
			));
		}
		// role id
		if (filter.getRoleId() != null) {
			predicates.add(builder.equal(root.get(IdmAuthorizationPolicy_.role).get(IdmRole_.id), filter.getRoleId()));
		}
		if (filter.getDisabled() != null) {
			predicates.add(builder.equal(root.get(IdmAuthorizationPolicy_.disabled), filter.getDisabled()));
		}
		if (filter.getAuthorizableType() != null) {
			predicates.add(builder.or(
					// wildcard policy - usable for all authorizable types
					builder.and(
							builder.isNull(root.get(IdmAuthorizationPolicy_.authorizableType)),
							builder.isNull(root.get(IdmAuthorizationPolicy_.groupPermission))
							),
					builder.equal(root.get(IdmAuthorizationPolicy_.authorizableType), filter.getAuthorizableType())
			));
		}
		String groupPermission = filter.getGroupPermission();
		if (StringUtils.isNotEmpty(groupPermission)) {
			predicates.add(builder.equal(root.get(IdmAuthorizationPolicy_.groupPermission), groupPermission));
		}
		
		return predicates;
	}
	
	@Override
	@Transactional(readOnly = true)
	public Set<GrantedAuthority> getDefaultAuthorities(UUID identityId) {
		IdmRoleDto defaultRole = roleService.getDefaultRole();
		if (defaultRole == null) {
			LOG.debug("Default role not found, no default authorities will be added. Change configuration [{}].", IdmRoleService.PROPERTY_DEFAULT_ROLE);
			return Collections.<GrantedAuthority>emptySet();
		}
		if (defaultRole.isDisabled()) {
			LOG.debug("Default role [{}] is disabled, no default authorities will be added.", defaultRole.getCode());
			return Collections.<GrantedAuthority>emptySet();
		}
		//
		UUID defaultRoleId = defaultRole.getId();
		Set<GrantedAuthority> defaultAuthorities = new HashSet<>();
		// default role authorities
		defaultAuthorities.addAll(getEnabledRoleAuthorities(identityId, defaultRoleId));
		// all sub roles authorities
		roleCompositionService
			.findAllSubRoles(defaultRoleId)
			.stream()
			.filter(roleComposition -> {
				IdmRoleDto subRole = DtoUtils.getEmbedded(roleComposition, IdmRoleComposition_.sub);
				return !subRole.isDisabled();
			})
			.forEach(roleComposition -> {
				defaultAuthorities.addAll(getEnabledRoleAuthorities(identityId, roleComposition.getSub()));
			});
		//
		LOG.debug("Found [{}] default authorities", defaultAuthorities.size());
		return defaultAuthorities;
	}

	@Override
	@Transactional(readOnly = true)
	public List<IdmAuthorizationPolicyDto> getDefaultPolicies(Class<? extends Identifiable> entityType) {
		IdmRoleDto defaultRole = roleService.getDefaultRole();
		if (defaultRole == null) {
			LOG.debug("Default role not found, no default authorization policies will be added.  Change configuration [{}].", IdmRoleService.PROPERTY_DEFAULT_ROLE);
			return Collections.<IdmAuthorizationPolicyDto>emptyList();
		}
		if (defaultRole.isDisabled()) {
			LOG.debug("Default role [{}] is disabled, no default authorization policies will be added.", defaultRole.getCode());
			return Collections.<IdmAuthorizationPolicyDto>emptyList();
		}
		//
		UUID defaultRoleId = defaultRole.getId();
		IdmAuthorizationPolicyFilter filter = new IdmAuthorizationPolicyFilter();
		filter.setDisabled(Boolean.FALSE);
		if (entityType != null) { // optional
			filter.setAuthorizableType(entityType.getCanonicalName());
		}
		// default role policies
		filter.setRoleId(defaultRoleId);
		List<IdmAuthorizationPolicyDto> defaultPolicies = new ArrayList<>();
		defaultPolicies.addAll(find(filter, null).getContent());
		// all sub roles policies
		roleCompositionService
			.findAllSubRoles(defaultRoleId)
			.stream()
			.filter(roleComposition -> {
				IdmRoleDto subRole = DtoUtils.getEmbedded(roleComposition, IdmRoleComposition_.sub);
				return !subRole.isDisabled();
			})
			.forEach(roleComposition -> {
				filter.setRoleId(roleComposition.getSub());
				defaultPolicies.addAll(find(filter, null).getContent());
			});
		//
		LOG.debug("Found [{}] default policies", defaultPolicies.size());
		//
		return defaultPolicies;
	}
	
	@Override
	@Transactional(readOnly = true)
	public Set<GrantedAuthority> getEnabledRoleAuthorities(UUID identityId, UUID roleId) {
		return getGrantedAuthorities(identityId, getRolePolicies(roleId, false));
	}
	
	@Override
	public List<IdmAuthorizationPolicyDto> getRolePolicies(UUID roleId, boolean disabled) {
		return toDtos(repository.getPolicies(roleId, disabled), false);
	}

	@Override
	@Transactional(readOnly = true)
	public Set<GrantedAuthority> getGrantedAuthorities(UUID identityId, List<IdmAuthorizationPolicyDto> policies) {
		final Set<GrantedAuthority> authorities = new HashSet<>();
		// find all active policies and return their authority by authorizable type
		for (IdmAuthorizationPolicyDto policy : policies) {
			// evaluate policy permissions - authorities are evaluated on null entity
			String groupPermission = policy.getGroupPermission();
			Set<String> baseAuthorities = getAuthorizationManager().getAuthorities(identityId, policy);
			//
			if (IdmGroupPermission.APP.getName().equals(groupPermission)
					|| (StringUtils.isEmpty(groupPermission) && baseAuthorities.contains(IdmBasePermission.ADMIN.getName()))) {
				// admin
				return Sets.newHashSet(new DefaultGrantedAuthority(IdmGroupPermission.APP.getName(), IdmBasePermission.ADMIN.getName()));
			}		
			if (StringUtils.isEmpty(groupPermission)) {
				if (baseAuthorities.contains(IdmBasePermission.ADMIN.getName())) {
					// all groups => synonym to APP_ADMIN
					authorities.add(new DefaultGrantedAuthority(IdmGroupPermission.APP.getName(), IdmBasePermission.ADMIN.getName()));					
				} else {
					// some base permission only
					moduleService.getAvailablePermissions().forEach(availableGroupPermission -> {
						if (IdmGroupPermission.APP != availableGroupPermission) { // app is wildcard only - skipping
							for (String permission : baseAuthorities) {
								authorities.add(new DefaultGrantedAuthority(availableGroupPermission.getName(), permission));
							}
						}
					});
				}
			} else if (baseAuthorities.contains(IdmBasePermission.ADMIN.getName())) {	
				authorities.add(new DefaultGrantedAuthority(groupPermission, IdmBasePermission.ADMIN.getName()));					
			} else {
				for (String permission : baseAuthorities) {
					authorities.add(new DefaultGrantedAuthority(groupPermission, permission));
				}
			}			
		}
		//
		return authorities;
	}
}
