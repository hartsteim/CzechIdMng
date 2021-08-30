package eu.bcvsolutions.idm.core.api.dto.filter;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;

/**
 * Filter for concept role request.
 *
 * @author svandav
 */
public class IdmConceptRoleRequestFilter extends DataFilter {
	
    private UUID roleRequestId;
    private RoleRequestState state;
    private UUID identityRoleId;
    private UUID roleId;
    private String roleText;
    private UUID identityContractId;
    private UUID automaticRole;
    private ConceptRoleRequestOperation operation;
    private Set<UUID> identityRoleIds;
    private String roleEnvironment;
    // Find only concepts, where identityRoleId is null
    private boolean identityRoleIsNull = false;
    private List<String> roleEnvironments;
    
    public IdmConceptRoleRequestFilter() {
		this(new LinkedMultiValueMap<>());
	}
	
	public IdmConceptRoleRequestFilter(MultiValueMap<String, Object> data) {
		super(IdmConceptRoleRequestDto.class, data);
	}

    public UUID getRoleRequestId() {
        return roleRequestId;
    }

    public void setRoleRequestId(UUID roleRequestId) {
        this.roleRequestId = roleRequestId;
    }

    public RoleRequestState getState() {
        return state;
    }

    public void setState(RoleRequestState state) {
        this.state = state;
    }

    public UUID getIdentityRoleId() {
        return identityRoleId;
    }

    public void setIdentityRoleId(UUID identityRoleId) {
        this.identityRoleId = identityRoleId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }

    public UUID getIdentityContractId() {
        return identityContractId;
    }

    public void setIdentityContractId(UUID identityContractId) {
        this.identityContractId = identityContractId;
    }

    public UUID getAutomaticRole() {
		return automaticRole;
	}

	public void setAutomaticRole(UUID automaticRole) {
		this.automaticRole = automaticRole;
	}

	public ConceptRoleRequestOperation getOperation() {
        return operation;
    }

    public void setOperation(ConceptRoleRequestOperation operation) {
        this.operation = operation;
    }
    
    public Set<UUID> getIdentityRoleIds() {
		return identityRoleIds;
	}

	public void setIdentityRoleIds(Set<UUID> identityRoleIds) {
		this.identityRoleIds = identityRoleIds;
	}
    
	public String getRoleEnvironment() {
		return roleEnvironment;
	}

	public void setRoleEnvironment(String roleEnvironment) {
		this.roleEnvironment = roleEnvironment;
	}

	public boolean isIdentityRoleIsNull() {
		return identityRoleIsNull;
	}

	public void setIdentityRoleIsNull(boolean identityRoleIsNull) {
		this.identityRoleIsNull = identityRoleIsNull;
	}

	public List<String> getRoleEnvironments() {
		return roleEnvironments;
	}

	public void setRoleEnvironments(List<String> roleEnvironments) {
		this.roleEnvironments = roleEnvironments;
	}

	/**
	 * Role text ~ quick ~ like.
	 * 
	 * @return role text
	 * @since 11.2.0
	 */
	public String getRoleText() {
		return roleText;
	}
	
	/**
	 * Role text ~ quick ~ like.
	 * 
	 * @param roleText role text
	 * @since 11.2.0
	 */
	public void setRoleText(String roleText) {
		this.roleText = roleText;
	}
}
