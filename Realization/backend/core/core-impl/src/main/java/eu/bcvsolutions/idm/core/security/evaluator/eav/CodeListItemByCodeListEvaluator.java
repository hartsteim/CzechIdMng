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
import eu.bcvsolutions.idm.core.eav.entity.IdmCodeList;
import eu.bcvsolutions.idm.core.eav.entity.IdmCodeListItem;
import eu.bcvsolutions.idm.core.eav.entity.IdmCodeListItem_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractTransitiveEvaluator;

/**
 * Permissions to code list items by code list.
 * 
 * @author Radek Tomiška
 *
 */
@Component(CodeListItemByCodeListEvaluator.EVALUATOR_NAME)
@Description("Permissions to code list items by code list.")
public class CodeListItemByCodeListEvaluator extends AbstractTransitiveEvaluator<IdmCodeListItem> {

	public static final String EVALUATOR_NAME = "core-code-list-item-by-code-list-evaluator";

	@Autowired private AuthorizationManager authorizationManager;
	@Autowired private SecurityService securityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	protected Identifiable getOwner(IdmCodeListItem entity) {
		return entity.getCodeList();
	}
	
	@Override
	protected Class<? extends Identifiable> getOwnerType() {
		return IdmCodeList.class;
	}
	
	@Override
	public Predicate getPredicate(Root<IdmCodeListItem> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		// form definition subquery
		Subquery<IdmCodeList> subquery = query.subquery(IdmCodeList.class);
		Root<IdmCodeList> subRoot = subquery.from(IdmCodeList.class);
		subquery.select(subRoot);		
		subquery.where(builder.and(
				authorizationManager.getPredicate(subRoot, query, builder, permission),
				builder.equal(root.get(IdmCodeListItem_.codeList), subRoot) // correlation attribute
				));
		//
		return builder.exists(subquery);
	}
}
