package eu.bcvsolutions.idm.core.security.evaluator.eav;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormAttribute;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormAttribute_;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormDefinition;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractTransitiveEvaluator;

/**
 * Permissions to form attribute by form definition
 * 
 * @author Radek Tomiška
 *
 */
@Component(FormAttributteByDefinitionEvaluator.EVALUATOR_NAME)
@Description("Permissions to form attributes by form definition")
public class FormAttributteByDefinitionEvaluator extends AbstractTransitiveEvaluator<IdmFormAttribute> {

	public static final String EVALUATOR_NAME = "core-form-attribute-by-definition-evaluator";

	@Autowired private AuthorizationManager authorizationManager;
	@Autowired private SecurityService securityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	protected Identifiable getOwner(IdmFormAttribute entity) {
		return entity.getFormDefinition();
	}
	
	@Override
	protected Class<? extends Identifiable> getOwnerType() {
		return IdmFormDefinition.class;
	}
	
	@Override
	public Predicate getPredicate(Root<IdmFormAttribute> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		// form definition subquery
		Subquery<IdmFormDefinition> subquery = query.subquery(IdmFormDefinition.class);
		Root<IdmFormDefinition> subRoot = subquery.from(IdmFormDefinition.class);
		subquery.select(subRoot);		
		subquery.where(builder.and(
				authorizationManager.getPredicate(subRoot, query, builder, permission),
				builder.equal(root.get(IdmFormAttribute_.formDefinition), subRoot) // correlation attribute
				));
		//
		return builder.exists(subquery);
	}
}
