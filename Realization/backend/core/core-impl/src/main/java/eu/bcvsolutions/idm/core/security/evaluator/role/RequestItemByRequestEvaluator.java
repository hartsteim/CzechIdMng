package eu.bcvsolutions.idm.core.security.evaluator.role;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.model.entity.IdmRequest;
import eu.bcvsolutions.idm.core.model.entity.IdmRequestItem;
import eu.bcvsolutions.idm.core.model.entity.IdmRequestItem_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractTransitiveEvaluator;

/**
 * Permissions to universal request items by request
 * 
 * @author Vít Švanda
 */
@Component(RequestItemByRequestEvaluator.EVALUATOR_NAME)
@Description("Permissions to universal request items by request")
public class RequestItemByRequestEvaluator extends AbstractTransitiveEvaluator<IdmRequestItem> {

	public static final String EVALUATOR_NAME = "core-request-item-by-request-evaluator";

	@Autowired
	private AuthorizationManager authorizationManager;
	@Autowired
	private SecurityService securityService;

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
	@Override
	protected Identifiable getOwner(IdmRequestItem entity) {
		return entity.getRequest();
	}

	@Override
	protected Class<? extends Identifiable> getOwnerType() {
		return IdmRequest.class;
	}

	@Override
	public Predicate getPredicate(Root<IdmRequestItem> root, CriteriaQuery<?> query, CriteriaBuilder builder,
			AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		// Request subquery
		Subquery<IdmRequest> subquery = query.subquery(IdmRequest.class);
		Root<IdmRequest> subRoot = subquery.from(IdmRequest.class);
		subquery.select(subRoot);
		subquery.where(builder.and(authorizationManager.getPredicate(subRoot, query, builder, permission),
				builder.equal(root.get(IdmRequestItem_.request), subRoot) // correlation attribute
		));
		
		return builder.exists(subquery);
	}
}
