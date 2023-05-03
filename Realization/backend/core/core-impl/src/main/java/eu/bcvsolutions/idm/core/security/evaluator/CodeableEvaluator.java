package eu.bcvsolutions.idm.core.security.evaluator;

import java.util.List;
import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.common.base.Objects;

import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;

/**
 * Share entity by their identifier - uuid or code
 * 
 * @author Radek Tomiška
 *
 */
@Component(CodeableEvaluator.EVALUATOR_NAME)
@Description("Share entity by their identifier - uuid or code.")
public class CodeableEvaluator extends AbstractAuthorizationEvaluator<Identifiable> {

	public static final String EVALUATOR_NAME = "core-codeable-evaluator";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CodeableEvaluator.class);
	public static final String PARAMETER_IDENTIFIER = "identifier";
	@Autowired private LookupService lookupService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
	@Override
	public boolean supports(Class<?> authorizableType) {
		Assert.notNull(authorizableType, "Authorizable type is required.");
		// support for all classes - found nothing, if class is not supported
		return super.supports(authorizableType)
				&& (AbstractEntity.class.isAssignableFrom(authorizableType) || AbstractDto.class.isAssignableFrom(authorizableType));
	}
	
	@Override
	public Predicate getPredicate(Root<Identifiable> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasPermission(policy, permission)) {
			return null;
		}
		//
		BaseEntity entity = lookupEntity(policy);
		if (entity == null) {
			return null;
		}
		// 
		return builder.equal(root.get(AbstractEntity_.id.getName()), entity.getId());
	}
	
	@Override
	public Set<String> getPermissions(Identifiable entity, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(entity, policy);
		if (entity == null || entity.getId() == null) {
			return permissions;
		}
		// check type
		if (StringUtils.isEmpty(policy.getAuthorizableType()) 
				|| !entity.getClass().getCanonicalName().equals(policy.getAuthorizableType())) {
			return permissions;
		}
		// load entity
		BaseEntity lookupEntity = lookupEntity(policy);
		if (lookupEntity == null) {
			return permissions;
		}
		//
		if (Objects.equal(lookupEntity, entity)) { // equals by id internally
			permissions.addAll(policy.getPermissions());
		}
		return permissions;
	}
	
	@Override
	public List<String> getPropertyNames() {
		List<String> parameters = super.getPropertyNames();
		parameters.add(PARAMETER_IDENTIFIER);
		return parameters;
	}
	
	/**
	 * Find entity by identifiable object ... this is little strange (we find entity only for adding it to other search)
	 * 
	 * @param policy
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private BaseEntity lookupEntity(AuthorizationPolicy policy) {
		Object identifier = policy.getEvaluatorProperties().get(PARAMETER_IDENTIFIER);
		if (identifier == null || StringUtils.isEmpty(policy.getAuthorizableType())) { 
			return null;
		}
		// find entity by identifiable object ... this is little strange (we find entity only for adding it to other search)
		BaseEntity entity;
		try {
			entity = lookupService.lookupEntity((Class<? extends Identifiable>) Class.forName(policy.getAuthorizableType()), identifier.toString());
		} catch (ClassNotFoundException ex) {
			LOG.warn("Class for name [{}] not found - skipping", policy.getAuthorizableType());
			return null;
		} catch (IllegalArgumentException ex) {
			LOG.warn("Authorizable type [{}] does not support entity lookup - skipping", policy.getAuthorizableType(), ex);
			return null;
		}
		if (entity == null) {
			LOG.debug("Entity for type [{}] and code [{}] wasn't found - skipping", policy.getAuthorizableType(), identifier);
			return null;
		}
		return entity;
	}
}
