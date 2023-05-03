package eu.bcvsolutions.idm.acc.service.api.adapter;

import java.util.List;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccIdentityAccountFilter;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.core.api.dto.AbstractRoleAssignmentDto;
import eu.bcvsolutions.idm.core.api.service.adapter.PluggableRoleAssignmentDeduplicator;

/**
 * This deduplicator checks whether two assigned roles create two different accounts. If so, it does not
 * return the role as duplicate.
 *
 * Deduplicator returns always first role as duplicate and it does so in these cases:
 * 1. If the roles are not yet persisted (do not have an ID)  - this is becasue we cannot fetch accounts for them and hence we cannot check whether they create different accounts
 * 2. If the roles are persisted, but they both do not create any account
 *
 * @author Tomáš Doischer
 * @author Peter Štrunc <github.com/peter-strunc>
 */
@Component
public class AccIdentityAccountPluggableRoleAssignmentDeduplicator implements PluggableRoleAssignmentDeduplicator {

	private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AccIdentityAccountPluggableRoleAssignmentDeduplicator.class);
	@Autowired
	private AccIdentityAccountService identityAccountService;

	@Override
	public AbstractRoleAssignmentDto getDuplicated(AbstractRoleAssignmentDto one, AbstractRoleAssignmentDto two, Boolean skipSubdefinition) {

		if (one == null || two == null || one.getId() == null || two.getId() == null) {
			// This may be a bit counterintuitive, but if one of the roles is null, we do not want to interfere with other deduplicators
			// and we return the first role as if it were duplicated. If both roles are null, we return null because we have no other choice.
			// This situation should not happen, but if it does, we do not want to throw an exception.
			LOG.debug("One or both of the role assignments are null, or are not yet persisted (do not assign any account), returning as if role were duplicated");
			return one == null ? two : one;
		}
		AccIdentityAccountFilter identityAccountFilter = new AccIdentityAccountFilter();
		identityAccountFilter.setIdentityRoleId(one.getId());

		LOG.debug("Searching for identity accounts for role: {}", one.getId());
		List<AccIdentityAccountDto> accIdentityAccountsOne = identityAccountService.find(identityAccountFilter, null).getContent();
		LOG.debug("Found {} identity accounts for role: {}", accIdentityAccountsOne.size(), one.getId());

		identityAccountFilter = new AccIdentityAccountFilter();
		identityAccountFilter.setIdentityRoleId(two.getId());

		LOG.debug("Searching for identity accounts for role: {}", two.getId());
		List<AccIdentityAccountDto> accIdentityAccountsTwo = identityAccountService.find(identityAccountFilter, null).getContent();
		LOG.debug("Found {} identity accounts for role: {}", accIdentityAccountsTwo.size(), two.getId());

		if (accIdentityAccountsOne.isEmpty() && !accIdentityAccountsTwo.isEmpty() || !accIdentityAccountsOne.isEmpty() && accIdentityAccountsTwo.isEmpty()) {
			LOG.debug("One of the roles has no identity accounts, so the roles {} and {} are not duplicate", one.getId(), two.getId());
			return null;
		}

		for (AccIdentityAccountDto identityAccountOne : accIdentityAccountsOne) {
			for (AccIdentityAccountDto identityAccountTwo : accIdentityAccountsTwo) {
				if (!identityAccountOne.equals(identityAccountTwo)) {
					LOG.debug("Identity accounts {} and {} are different, so the role {} cannot be removed", identityAccountOne.getId(), identityAccountTwo.getId(), one.getId());
					return null;
				}
			}
		}
		LOG.debug("Role {} can be removed", one.getId());
		return one;
	}

	@Override
	public boolean considerOrder() {
		return false;
	}

}
