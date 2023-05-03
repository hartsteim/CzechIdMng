package eu.bcvsolutions.idm.core.security.evaluator.eav;

import java.util.Set;
import java.util.UUID;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.eav.entity.IdmCodeList;
import eu.bcvsolutions.idm.core.eav.entity.IdmCodeList_;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormAttribute;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormAttribute_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;

/**
 * Permissions to form attributes by code list definition.
 * 
 * Note: AttribuTte ... I know, but it's too late :(
 * 
 * @author Radek Tomiška
 * @since 9.4.0
 */
@Component(FormAttributteByCodeListEvaluator.EVALUATOR_NAME)
@Description("Permissions to form attributes by code list definition.")
public class FormAttributteByCodeListEvaluator extends AbstractAuthorizationEvaluator<IdmFormAttribute> {

	public static final String EVALUATOR_NAME = "core-form-attribute-by-code-list-evaluator";
	@Autowired private AuthorizationManager authorizationManager;
	@Autowired private SecurityService securityService;
	@Autowired private LookupService lookupService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
	
	@Override
	public Predicate getPredicate(Root<IdmFormAttribute> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		// form definition subquery
		Subquery<IdmCodeList> subquery = query.subquery(IdmCodeList.class);
		Root<IdmCodeList> subRoot = subquery.from(IdmCodeList.class);
		subquery.select(subRoot);		
		subquery.where(builder.and(
				authorizationManager.getPredicate(subRoot, query, builder, permission),
				builder.equal(root.get(IdmFormAttribute_.formDefinition), subRoot.get(IdmCodeList_.formDefinition)) // correlation attribute
				));
		//
		return builder.exists(subquery);
	}
	
	@Override
	public boolean supportsPermissions() {
		return false;
	}
	
	@Override
	public Set<String> getPermissions(IdmFormAttribute entity, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(entity, policy);
		if (entity == null || !securityService.isAuthenticated()) {
			return permissions;
		}
		IdmCodeList codeList = (IdmCodeList) lookupService.lookupEntity(IdmCodeList.class, entity.getFormDefinition().getCode());
		if (codeList == null) {
			return permissions;
		}
		// evaluates permissions on owner class
		return authorizationManager.getPermissions(codeList);
	}
	
	@Override
	public Set<String> getAuthorities(UUID identityId, AuthorizationPolicy policy) {
		// evaluates authorities on owner type class
		return authorizationManager.getAuthorities(identityId, IdmCodeList.class);
	}
}
