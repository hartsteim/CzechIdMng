package eu.bcvsolutions.idm.acc.service.impl;

import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemAttributeFilter;
import eu.bcvsolutions.idm.acc.entity.AccIdentityAccount;
import eu.bcvsolutions.idm.acc.entity.AccIdentityAccount_;
import eu.bcvsolutions.idm.acc.entity.SysRoleSystemAttribute;
import eu.bcvsolutions.idm.acc.entity.SysRoleSystemAttribute_;
import eu.bcvsolutions.idm.acc.entity.SysRoleSystem_;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.model.entity.AbstractRoleAssignment_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Štrunc <github.com/peter-strunc>
 */
@Component
public class IdmIdentityRoleJPAPlugin implements SysRoleAssignmentJPAPlugin {
    @Override
    public List<Predicate> getOverridenAttributesPredicates(Root<SysRoleSystemAttribute> root, CriteriaQuery<?> query, CriteriaBuilder builder, SysRoleSystemAttributeFilter filter) {
        final List<Predicate> predicates = new ArrayList<>();
        // Search overridden attributes for this account (searching via
        // identity-accounts -> identity-roles -> role-systems ->
        // role-system-attributes)
        if (filter.getIdentityId() != null && filter.getAccountId() != null) {
            predicates.add(getPredicateForOverridenAttributes(root, query, builder, filter));
        }

        // Find override for identity (via identity-role)
        if (filter.getIdentityId() != null  && filter.getAccountId() == null) {
            final Predicate exists = getOverridenAttributesForIdentity(root, query, builder, filter);
            predicates.add(exists);
        }

        // Find override attributes for identity (via identity-role and role-system).
        // We want to find all override attribute for identity, where relation between identity-role
        // and role-system is null or if relation is not null, then return override attributes where same role-systems are used.
        if (filter.getRoleSystemRelationForIdentityId() != null) {
            predicates.add(getOverridenAttributesPredicateSystemRelation(root, query, builder, filter));
        }
        return predicates;
    }

    private Predicate getOverridenAttributesPredicateSystemRelation(Root<SysRoleSystemAttribute> root, CriteriaQuery<?> query, CriteriaBuilder builder, SysRoleSystemAttributeFilter filter) {
        // Query via role:
        Subquery<IdmIdentityRole> subquery = query.subquery(IdmIdentityRole.class);
        Root<IdmIdentityRole> subRoot = subquery.from(IdmIdentityRole.class);
        subquery.select(subRoot);

        // Correlation attribute predicate
        Predicate correlationPredicate = builder.equal(
                subRoot.get(AbstractRoleAssignment_.role),
                root.get(SysRoleSystemAttribute_.roleSystem).get(SysRoleSystem_.role)); // Correlation attribute
        // Identity predicate
        Predicate identityPredicate = builder.equal(subRoot.get(IdmIdentityRole_.identityContract)
                        .get(IdmIdentityContract_.identity).get(AbstractEntity_.id),
                filter.getRoleSystemRelationForIdentityId());
        // Identity-role predicate
        Predicate identityRolePredicate = builder.isNull(subRoot.get(AbstractRoleAssignment_.roleSystem));

        subquery.where(builder.and(correlationPredicate, identityPredicate, identityRolePredicate));

        // Query via role-system:
        Subquery<IdmIdentityRole> subqueryViaRoleSystem = query.subquery(IdmIdentityRole.class);
        Root<IdmIdentityRole> subRootViaRoleSystem = subqueryViaRoleSystem.from(IdmIdentityRole.class);
        subqueryViaRoleSystem.select(subRootViaRoleSystem);

        // Correlation attribute predicate
        Predicate correlationPredicateViaRoleSystem = builder.equal(
                subRootViaRoleSystem .get(AbstractRoleAssignment_.roleSystem),
                root.get(SysRoleSystemAttribute_.roleSystem)); // Correlation attribute
        // Identity predicate
        Predicate identityPredicateViaRoleSystem  = builder.equal(subRootViaRoleSystem.get(IdmIdentityRole_.identityContract)
                        .get(IdmIdentityContract_.identity).get(AbstractEntity_.id),
                filter.getRoleSystemRelationForIdentityId());

        subqueryViaRoleSystem.where(builder.and(correlationPredicateViaRoleSystem, identityPredicateViaRoleSystem));

        // Query by role or by role-system
        return builder.or(builder.exists(subquery), builder.exists(subqueryViaRoleSystem));
    }

    private Predicate getOverridenAttributesForIdentity(Root<SysRoleSystemAttribute> root, CriteriaQuery<?> query, CriteriaBuilder builder, SysRoleSystemAttributeFilter filter) {
        Subquery<IdmIdentityRole> subquery = query.subquery(IdmIdentityRole.class);
        Root<IdmIdentityRole> subRoot = subquery.from(IdmIdentityRole.class);
        subquery.select(subRoot);

        // Correlation attribute predicate
        Predicate correlationPredicate = builder.equal(
                subRoot.get(AbstractRoleAssignment_.role),
                root.get(SysRoleSystemAttribute_.roleSystem).get(SysRoleSystem_.role)); // Correlation attribute
        // Identity predicate
        Predicate identityPredicate = builder.equal(subRoot.get(IdmIdentityRole_.identityContract)
                        .get(IdmIdentityContract_.identity).get(AbstractEntity_.id),
                filter.getIdentityId());

        subquery.where(builder.and(correlationPredicate, identityPredicate));

        return builder.exists(subquery);
    }

    private Predicate getPredicateForOverridenAttributes(Root<SysRoleSystemAttribute> root, CriteriaQuery<?> query, CriteriaBuilder builder,
            SysRoleSystemAttributeFilter filter) {
        Subquery<AccIdentityAccount> subquery = query.subquery(AccIdentityAccount.class);
        Root<AccIdentityAccount> subRoot = subquery.from(AccIdentityAccount.class);
        subquery.select(subRoot);

        // Correlation attribute predicate
        Predicate correlationPredicate = builder.equal(
                subRoot.get(AccIdentityAccount_.identityRole).get(AbstractRoleAssignment_.role),
                root.get(SysRoleSystemAttribute_.roleSystem).get(SysRoleSystem_.role)); // Correlation attribute
        // Identity predicate
        Predicate identityPredicate = builder.equal(subRoot.get(AccIdentityAccount_.identityRole)
                        .get(IdmIdentityRole_.identityContract).get(IdmIdentityContract_.identity).get(AbstractEntity_.id),
                filter.getIdentityId());
        // Account predicate
        Predicate accountPredicate = builder.equal(subRoot.get(AccIdentityAccount_.account).get(AbstractEntity_.id),
                filter.getAccountId());

        subquery.where(builder.and(correlationPredicate, identityPredicate, accountPredicate));
        return builder.exists(subquery);
    }
}
