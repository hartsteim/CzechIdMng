package eu.bcvsolutions.idm.acc.service.impl;

import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemAttributeFilter;
import eu.bcvsolutions.idm.acc.entity.AccAccount;
import eu.bcvsolutions.idm.acc.entity.AccAccountRoleAssignment;
import eu.bcvsolutions.idm.acc.entity.AccAccountRoleAssignment_;
import eu.bcvsolutions.idm.acc.entity.AccIdentityAccount;
import eu.bcvsolutions.idm.acc.entity.AccIdentityAccount_;
import eu.bcvsolutions.idm.acc.entity.SysRoleSystemAttribute;
import eu.bcvsolutions.idm.acc.entity.SysRoleSystemAttribute_;
import eu.bcvsolutions.idm.acc.entity.SysRoleSystem_;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.model.entity.AbstractRoleAssignment_;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Peter Štrunc <github.com/peter-strunc>
 */
@Component
public class AccAccountRoleAssignmentJPAPlugin implements SysRoleAssignmentJPAPlugin {
    @Override
    public List<Predicate> getOverridenAttributesPredicates(Root<SysRoleSystemAttribute> root, CriteriaQuery<?> query, CriteriaBuilder builder, SysRoleSystemAttributeFilter filter) {
        final List<Predicate> predicates = new ArrayList<>();
        // Search overridden attributes for this account (searching via
        // identity-account -> acc-account -> account-role -> role-systems -> role-system-attributes )
        if (filter.getIdentityId() != null && filter.getAccountId() != null) {
            predicates.add(getPredicateForOverridenAttributes(root, query, builder, filter));
        }

        // Find override for identity (via account-role)
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
        Subquery<AccAccountRoleAssignment> subquery = query.subquery(AccAccountRoleAssignment.class);
        Root<AccAccountRoleAssignment> subRoot = subquery.from(AccAccountRoleAssignment.class);
        subquery.select(subRoot);

        // Correlation attribute predicate
        Predicate correlationPredicate = builder.equal(
                subRoot.get(AbstractRoleAssignment_.role),
                root.get(SysRoleSystemAttribute_.roleSystem).get(SysRoleSystem_.role)); // Correlation attribute

        // Identity predicate - AccountRoleAssignment -> AccAccount -> IdmIdentityAccount

        Predicate identityRolePredicate = builder.isNull(subRoot.get(AbstractRoleAssignment_.roleSystem));

        // Identity-role predicate
        subquery.where(builder.and(correlationPredicate,  getIdentityPredicate(query, builder, filter, subRoot), identityRolePredicate,
                filter.getAccountId() != null ? builder.equal(subRoot.get(AccAccountRoleAssignment_.account).get(AbstractEntity_.id), filter.getAccountId()) : builder.conjunction()));

        // Query via role-system:
        Subquery<AccAccountRoleAssignment> subqueryRs = query.subquery(AccAccountRoleAssignment.class);
        Root<AccAccountRoleAssignment> subRootRs = subqueryRs.from(AccAccountRoleAssignment.class);
        subqueryRs.select(subRootRs);

        // Correlation attribute predicate
        Predicate correlationPredicateRs = builder.equal(
                subRootRs.get(AbstractRoleAssignment_.roleSystem),
                root.get(SysRoleSystemAttribute_.roleSystem)); // Correlation attribute

        // Identity predicate - AccountRoleAssignment -> AccAccount -> IdmIdentityAccount
        subqueryRs.where(builder.and(correlationPredicateRs, getIdentityPredicate(query, builder, filter, subRootRs)));

        // Query by role or by role-system
        return builder.or(builder.exists(subquery), builder.exists(subqueryRs));
    }

    private Predicate getIdentityPredicate(CriteriaQuery<?> query, CriteriaBuilder builder, SysRoleSystemAttributeFilter filter, Root<AccAccountRoleAssignment> subRootRs) {
        final Join<AccAccountRoleAssignment, AccAccount> assignmentAccountJoinRs = subRootRs.join(AccAccountRoleAssignment_.account);

        Subquery<AccIdentityAccount> identAccSubqueryRs = query.subquery(AccIdentityAccount.class);
        Root<AccIdentityAccount> identAccSubrootRs = identAccSubqueryRs.from(AccIdentityAccount.class);
        identAccSubqueryRs.select(identAccSubrootRs);
        identAccSubqueryRs.where(
                builder.and(
                        builder.equal(identAccSubrootRs.get(AccIdentityAccount_.identity).get(AbstractEntity_.id), filter.getRoleSystemRelationForIdentityId())),
                builder.equal(assignmentAccountJoinRs, identAccSubrootRs.get(AccIdentityAccount_.account))
        );

        return builder.and(
                builder.exists(identAccSubqueryRs)
        );
    }

    private Predicate getOverridenAttributesForIdentity(Root<SysRoleSystemAttribute> root, CriteriaQuery<?> query, CriteriaBuilder builder, SysRoleSystemAttributeFilter filter) {
        Subquery<AccAccountRoleAssignment> subquery = query.subquery(AccAccountRoleAssignment.class);
        Root<AccAccountRoleAssignment> subRoot = subquery.from(AccAccountRoleAssignment.class);
        subquery.select(subRoot);

        // Correlation attribute predicate
        Predicate correlationPredicate = builder.equal(
                subRoot.get(AbstractRoleAssignment_.role),
                root.get(SysRoleSystemAttribute_.roleSystem).get(SysRoleSystem_.role)); // Correlation attribute

        // Identity predicate - AccountRoleAssignment -> AccAccount -> IdmIdentityAccount
        return getIdentityPredicate(query, builder, filter, subquery, subRoot, correlationPredicate);
    }

    private Predicate getIdentityPredicate(CriteriaQuery<?> query, CriteriaBuilder builder, SysRoleSystemAttributeFilter filter, Subquery<AccAccountRoleAssignment> subquery,
            Root<AccAccountRoleAssignment> subRoot, Predicate correlationPredicate) {
        final Join<AccAccountRoleAssignment, AccAccount> assignmentAccountJoin = subRoot.join(AccAccountRoleAssignment_.account);

        Subquery<AccIdentityAccount> identAccSubquery = query.subquery(AccIdentityAccount.class);
        Root<AccIdentityAccount> identAccSubroot = identAccSubquery.from(AccIdentityAccount.class);
        identAccSubquery.select(identAccSubroot);
        identAccSubquery.where(
                builder.and(
                        builder.equal(identAccSubroot.get(AccIdentityAccount_.identity).get(AbstractEntity_.id), filter.getIdentityId())),
                builder.equal(assignmentAccountJoin, identAccSubroot.get(AccIdentityAccount_.account))
        );


        Predicate identityPredicate = builder.and(
                builder.exists(identAccSubquery)
        );


        subquery.where(builder.and(correlationPredicate, identityPredicate));

        return builder.exists(subquery);
    }

    private Predicate getPredicateForOverridenAttributes(Root<SysRoleSystemAttribute> root, CriteriaQuery<?> query, CriteriaBuilder builder,
            SysRoleSystemAttributeFilter filter) {
        Subquery<AccAccountRoleAssignment> subquery = query.subquery(AccAccountRoleAssignment.class);
        Root<AccAccountRoleAssignment> subRoot = subquery.from(AccAccountRoleAssignment.class);
        subquery.select(subRoot);

        // Correlation attribute predicate
        Predicate correlationPredicate = builder.and(
                        builder.equal(subRoot.get(AbstractRoleAssignment_.role), root.get(SysRoleSystemAttribute_.roleSystem).get(SysRoleSystem_.role)),
                        builder.equal(subRoot.get(AccAccountRoleAssignment_.account).get(AbstractEntity_.id), filter.getAccountId())
                ); // Correlation attribute

        // Identity predicate - AccountRoleAssignment -> AccAccount -> IdmIdentityAccount
        return getIdentityPredicate(query, builder, filter, subquery, subRoot, correlationPredicate);
    }
}
