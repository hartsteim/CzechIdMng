package eu.bcvsolutions.idm.core.security.service;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;

import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRole;

/**
 * Granted authorities for users
 * 
 * @author svandav
 */
public interface GrantedAuthoritiesFactory {

	/**
	 * Returns unique set of authorities by assigned active roles for given identity.
	 * Sub roles are also processed.
	 * 
	 * @param username
	 * @return
	 */
	List<GrantedAuthority> getGrantedAuthorities(String username);

	/**
	 * @see GrantedAuthoritiesFactory#getGrantedAuthorities(String)
	 */
	Collection<GrantedAuthority> getActiveRoleAuthorities(IdmRole role);

	/**
	 * @see GrantedAuthoritiesFactory#getGrantedAuthorities(String)
	 */
	Collection<GrantedAuthority> getGrantedAuthoritiesForIdentity(IdmIdentity identity);

	/**
	 * @see GrantedAuthoritiesFactory#getGrantedAuthorities(String)
	 */
	Collection<GrantedAuthority> getGrantedAuthoritiesForValidRoles(Collection<IdmIdentityRole> roles);
	
	/**
	 * Decides whether the original collection contains all authorities
	 * in the given subset.
	 *  
	 * @param original
	 * @param subset
	 * @return
	 */
	boolean containsAllAuthorities(Collection<GrantedAuthority> original, Collection<GrantedAuthority> subset);
}
