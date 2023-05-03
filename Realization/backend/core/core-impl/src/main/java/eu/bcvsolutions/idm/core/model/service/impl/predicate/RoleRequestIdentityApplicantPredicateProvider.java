package eu.bcvsolutions.idm.core.model.service.impl.predicate;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.service.PluggablePredicateProvider;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleRequest;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleRequest_;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * This predicate provider is used to filter role requests by applicant identity.
 *
 * @author Peter Štrunc <github.com/peter-strunc>
 */
@Component
public class RoleRequestIdentityApplicantPredicateProvider implements PluggablePredicateProvider<IdmRoleRequest, IdmRoleRequestFilter> {
    @Override
    public List<Predicate> toPredicates(Root<IdmRoleRequest> root, CriteriaQuery<?> query, CriteriaBuilder builder, IdmRoleRequestFilter filter) {
        if (filter.getApplicantType() == null || !filter.getApplicantType().equals(IdmIdentityDto.class.getCanonicalName())) {
            // this predicate provider is used only for filtering by applicant identity
            return Collections.emptyList();
        }

        final var result = new ArrayList<Predicate>();
        if (StringUtils.isNotEmpty(filter.getApplicant())) {
            final var identitySubquery = query.subquery(IdmIdentity.class);
            final var identityRoot = identitySubquery.from(IdmIdentity.class);
            identitySubquery.select(identityRoot);
            identitySubquery.where(builder.and(
                    builder.equal(identityRoot.get(IdmIdentity_.username), filter.getApplicant()),
                    builder.equal(identityRoot.get(AbstractEntity_.id), root.get(IdmRoleRequest_.applicant))
            ));
            final Predicate predicate = builder.exists(identitySubquery);
            result.add(predicate);
        }
        return result;
    }
}
