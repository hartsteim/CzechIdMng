package eu.bcvsolutions.idm.core.api.service;

import eu.bcvsolutions.idm.core.api.dto.filter.BaseFilter;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.List;

/**
 * Beans of this type are injected into corresponding {@link AbstractReadDtoService} and are used as a way for
 * a module to provide specific filtering capabilities for services from other modules.
 *
 * @see {@link AbstractReadDtoService#toPredicates(Root, CriteriaQuery, CriteriaBuilder, BaseFilter)}
 *
 * @author Peter Štrunc <github.com/peter-strunc>
 */
public interface PluggablePredicateProvider<E extends BaseEntity, F extends BaseFilter> {

    List<Predicate> toPredicates(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder builder, F filter);

}
