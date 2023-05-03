package eu.bcvsolutions.idm.core.security.evaluator.eav;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.utils.EntityUtils;
import eu.bcvsolutions.idm.core.eav.api.domain.BaseFaceType;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.entity.FormableEntity;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.FormValueService;
import eu.bcvsolutions.idm.core.eav.entity.AbstractFormValue;
import eu.bcvsolutions.idm.core.eav.entity.AbstractFormValue_;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormAttribute;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormAttribute_;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormDefinition;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormDefinition_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.api.utils.PermissionUtils;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;

/**
 * Permissions to form attribute by form definition and attribute codes
 * 
 * @author Radek Tomiška
 *
 */
public class AbstractFormValueEvaluator<T extends AbstractFormValue<?>> extends AbstractAuthorizationEvaluator<T> {
	
	public static final String PARAMETER_FORM_DEFINITION = "form-definition";
	public static final String PARAMETER_FORM_ATTRIBUTES = "attributes";
	public static final String PARAMETER_SELF_ONLY = "self-only"; // only self form values
	public static final String PARAMETER_OWNER_UPDATE = "owner-update"; // can update owner => can edit form values
	public static final String PARAMETER_OWNER_READ = "owner-read"; // can read owner => can edit form values
	//
	@Autowired private FormService formService;
	@Autowired private SecurityService securityService;
	@Autowired private AuthorizationManager authorizationManager;
	@Autowired private LookupService lookupService;
	
	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Predicate getPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasPermission(policy, permission)) {
			return null;
		}
		if (!securityService.isAuthenticated()) {
			return null;
		}
		//
		List<Predicate> predicates = new ArrayList<>();
		//
		// by form definition
		IdmFormDefinitionDto formDefinition = getFormDefinition(policy);
		if (formDefinition == null) {
			// if form definition is empty ... we disable all
			return builder.disjunction();
		}
		// prevent to generate cross joins by default
		Join<T, IdmFormAttribute> eavAttr = root.join(AbstractFormValue_.formAttribute);
		Join<IdmFormAttribute, IdmFormDefinition> extDef = eavAttr.join(IdmFormAttribute_.formDefinition);
		//
		predicates.add(builder.equal(extDef.get(IdmFormDefinition_.id), formDefinition.getId()));
		//
		// by form attributes
		Set<String> formAttributes = getFormAttributes(policy);
		if (!formAttributes.isEmpty()) {
			predicates.add(eavAttr.get(IdmFormAttribute_.code).in(formAttributes));
		}
		//
		// by self
		if (isSelfOnly(policy)) {
			predicates.add(builder.equal(root.get(FormValueService.PROPERTY_OWNER).get(BaseEntity.PROPERTY_ID), securityService.getCurrentId()));
		}
		//
		// owner read or update - reuse the same subquery
		if (isOwnerRead(policy) || isOwnerUpdate(policy)) {
			Class<? extends FormableEntity> ownerType = getOwnerType();
			Subquery subquery = query.subquery(ownerType);
			Root subRoot = subquery.from(ownerType);
			subquery.select(subRoot);	// TODO: select 1 or something like this	
			subquery.where(builder.and(
					authorizationManager.getPredicate(subRoot, query, builder, 
							isOwnerRead(policy) ? IdmBasePermission.READ : null,
							isOwnerUpdate(policy) ? IdmBasePermission.UPDATE : null),
					builder.equal(root.get(FormValueService.PROPERTY_OWNER), subRoot) // correlation attribute
					));
			//
			// predicates.add(builder.greaterThan(builder.count(subquery), 0L));
			
			predicates.add(builder.exists(subquery));
		}		
		//
		return builder.and(predicates.toArray(new Predicate[predicates.size()]));
	}
	
	@Override
	public Set<String> getPermissions(T entity, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(entity, policy);
		if (entity == null || entity.getFormAttribute() == null) {
			return permissions;
		}
		IdmFormDefinitionDto formDefinition = getFormDefinition(policy);
		//
		// if form definition is empty ... we disable all
		if (formDefinition == null) {
			return permissions;
		}
		//
		// form definition doesn't fit
		if (!entity.getFormAttribute().getFormDefinition().getId().equals(formDefinition.getId())) {
			return permissions;
		}
		// 
		// self only
		if (isSelfOnly(policy) && !securityService.getCurrentId().equals(getOwner(entity).getId())) {
			return permissions;
		}
		//
		// owner read
		if (isOwnerRead(policy)) {
			if (!PermissionUtils.hasPermission(authorizationManager.getPermissions(getOwner(entity)), IdmBasePermission.READ)) {
				return permissions;
			}
		}
		//
		// owner update
		if (isOwnerUpdate(policy)) {
			FormableEntity owner = getOwner(entity); // Owner instance is required (type is needed to resolve configured policies).
			Serializable ownerId = owner.getId();			
			if (ownerId == null || lookupService.lookupEntity(owner.getClass(), ownerId) == null) { // ~ is newly created owner
				// newly created owner together with extended values
				if (!PermissionUtils.hasAnyPermission(
						authorizationManager.getPermissions(owner), 
						IdmBasePermission.CREATE, 
						IdmBasePermission.UPDATE)) { // UPDATE - backward compatible for create.
					return permissions;
				}
			} else {
				if (!PermissionUtils.hasPermission(authorizationManager.getPermissions(owner), IdmBasePermission.UPDATE)) {
					return permissions;
				}
			}
		}
		//
		// fit form attributes 
		Set<String> formAttributes = getFormAttributes(policy);
		if (formAttributes.isEmpty() || formAttributes.contains(entity.getFormAttribute().getCode())) {
			permissions.addAll(policy.getPermissions());
		}
		//
		return permissions;
	}
	
	@Override
	public List<String> getPropertyNames() {
		List<String> parameters = super.getPropertyNames();
		parameters.add(PARAMETER_FORM_DEFINITION);
		parameters.add(PARAMETER_FORM_ATTRIBUTES);
		parameters.add(PARAMETER_OWNER_UPDATE);
		parameters.add(PARAMETER_OWNER_READ);
		return parameters;
	}
	
	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		return Lists.newArrayList(
				new IdmFormAttributeDto(PARAMETER_FORM_DEFINITION, PARAMETER_FORM_DEFINITION, PersistentType.UUID, BaseFaceType.FORM_DEFINITION_SELECT),
				new IdmFormAttributeDto(PARAMETER_FORM_ATTRIBUTES, PARAMETER_FORM_ATTRIBUTES, PersistentType.SHORTTEXT),
				new IdmFormAttributeDto(PARAMETER_OWNER_UPDATE, PARAMETER_OWNER_UPDATE, PersistentType.BOOLEAN),
				new IdmFormAttributeDto(PARAMETER_OWNER_READ, PARAMETER_OWNER_READ, PersistentType.BOOLEAN)
				);
	}
	
	/**
	 * Returns owning entity type (e.g. IdmIdentity, )
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected Class<? extends FormableEntity> getOwnerType() {
		Class<? extends AbstractFormValue<?>> valueClass = getEntityClass();
		try {
			Class<?> ownerType = valueClass.getDeclaredField(FormValueService.PROPERTY_OWNER).getType();
			return (Class<? extends FormableEntity>) ownerType;
		} catch (NoSuchFieldException | SecurityException ex) {
			throw new CoreException("Extended form attribute [" + valueClass  + "] has not owner specified, connot be secured.", ex); // rea
		}
	}
	
	/**
	 * Returns owning entity for authorization evaluation
	 * 
	 * @param entity
	 * @return
	 */
	protected FormableEntity getOwner(T entity) {
		return entity.getOwner();
	}
	
	private IdmFormDefinitionDto getFormDefinition(AuthorizationPolicy policy) {
		String ownerType = formService.getOwnerType(getOwnerType());
		String code = getFormDefinitionCode(policy);
		//
		// main form definition
		if (StringUtils.isEmpty(code)) {
			return formService.getDefinition(ownerType);
		}
		try {
			// by uuid
			UUID formDefinitionId = EntityUtils.toUuid(code);
			//
			return formService.getDefinition(formDefinitionId);
		} catch (ClassCastException ex) {
			// then by code
			return formService.getDefinition(ownerType, code);
		}
	}
	
	private String getFormDefinitionCode(AuthorizationPolicy policy) {
		Object code = policy.getEvaluatorProperties().get(PARAMETER_FORM_DEFINITION);
		if (code == null) {
			return null;
		}
		return code.toString();
	}
	
	private Set<String> getFormAttributes(AuthorizationPolicy policy) {
		Set<String> attributes = new HashSet<>();
		String configAttributes = policy.getEvaluatorProperties().getString(PARAMETER_FORM_ATTRIBUTES);
		if (StringUtils.isBlank(configAttributes)) {
			return attributes;
		}
		return Arrays
			.stream(configAttributes.split(","))
			.filter(StringUtils::isNotBlank)
			.map(StringUtils::trim)
			.collect(Collectors.toSet());
	}
	
	private boolean isSelfOnly(AuthorizationPolicy policy) {
		return policy.getEvaluatorProperties().getBoolean(PARAMETER_SELF_ONLY);
	}
	
	private boolean isOwnerUpdate(AuthorizationPolicy policy) {
		return policy.getEvaluatorProperties().getBoolean(PARAMETER_OWNER_UPDATE);
	}
	
	private boolean isOwnerRead(AuthorizationPolicy policy) {
		return policy.getEvaluatorProperties().getBoolean(PARAMETER_OWNER_READ);
	}
}