package eu.bcvsolutions.idm.example.security.evaluator;

import java.math.BigDecimal;
import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;
import eu.bcvsolutions.idm.example.entity.ExampleProduct;
import eu.bcvsolutions.idm.example.entity.ExampleProduct_;

/**
 * Adds permissions to products for free.
 *
 * @author Radek Tomiška
 */
@Component(FreeProductEvaluator.EVALUATOR_NAME)
@Description("Adds permissions to products for free..")
public class FreeProductEvaluator extends AbstractAuthorizationEvaluator<ExampleProduct> {

	public static final String EVALUATOR_NAME = "example-free-product-evaluator";

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	@Override
	public Predicate getPredicate(Root<ExampleProduct> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		return builder.or(
				builder.isNull(root.get(ExampleProduct_.price)),
				builder.equal(root.get(ExampleProduct_.price), BigDecimal.ZERO)
				);
	}

	@Override
	public Set<String> getPermissions(ExampleProduct authorizable, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(authorizable, policy);
		if (authorizable.getPrice() == null || BigDecimal.ZERO.compareTo(authorizable.getPrice()) == 0) {
			permissions.addAll(policy.getPermissions());
		}
		return permissions;
	}

}
