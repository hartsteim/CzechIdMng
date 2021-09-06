package eu.bcvsolutions.idm.core.api.dto;

import java.util.UUID;

import javax.validation.constraints.Size;

import org.springframework.hateoas.core.Relation;

import eu.bcvsolutions.idm.core.api.domain.Codeable;
import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;
import eu.bcvsolutions.idm.core.api.domain.Disableable;
import eu.bcvsolutions.idm.core.api.domain.Embedded;
import eu.bcvsolutions.idm.core.api.domain.ExternalIdentifiable;
import eu.bcvsolutions.idm.core.api.domain.Requestable;
import eu.bcvsolutions.idm.core.api.domain.RoleType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import io.swagger.annotations.ApiModelProperty;

/**
 * DTO for role
 *
 * @author svandav
 * @author Radek Tomiška
 */
@Relation(collectionRelation = "roles")
public class IdmRoleDto extends FormableDto implements Disableable, Codeable, ExternalIdentifiable, Requestable {

    private static final long serialVersionUID = 1L;
    //
    @ApiModelProperty(notes = "Code with environment (baseCode - environment).")
    @Size(max = DefaultFieldLengths.NAME)
    private String code;
    @Size(max = DefaultFieldLengths.NAME)
	@ApiModelProperty(notes = "Base code without environment.")
    private String baseCode;
    @ApiModelProperty(notes = "Environment.")
    @Size(max = DefaultFieldLengths.NAME)
    private String environment;
    @Size(max = DefaultFieldLengths.NAME)
    private String name;
    @Size(max = DefaultFieldLengths.NAME)
	@ApiModelProperty(notes = "Unique external identifier.")
	private String externalId;
    private boolean disabled;
    private boolean canBeRequested;
    private RoleType roleType;
    private int priority = 0;
    private boolean approveRemove;
    @Size(max = DefaultFieldLengths.DESCRIPTION)
    private String description;
    @Embedded(dtoClass = IdmRequestItemDto.class)
    private UUID requestItem; // Isn't persist in the entity
    @Embedded(dtoClass = IdmFormDefinitionDto.class)
	private UUID identityRoleAttributeDefinition;
    private long childrenCount;
    // Count of role-systems for this role, which are in cross-domain groups.
    private long systemsInCrossDomains;
    // Prefilled role-system which is in cross-domain groups and identity has account for that system.
    private UUID prefilledSystemInCrossDomains;

    public IdmRoleDto() {
	}
    
    public IdmRoleDto(UUID id) {
    	super(id);
	}
    
    @Override
    public String getCode() {
    	return code;
    }
    
    public void setCode(String code) {
		this.code = code;
	}
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
    	this.name = name;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public RoleType getRoleType() {
        return roleType;
    }

    public void setRoleType(RoleType roleType) {
        this.roleType = roleType;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isApproveRemove() {
        return approveRemove;
    }

    public void setApproveRemove(boolean approveRemove) {
        this.approveRemove = approveRemove;
    }
    
    public boolean isCanBeRequested() {
		return canBeRequested;
	}
    
    public void setCanBeRequested(boolean canBeRequested) {
		this.canBeRequested = canBeRequested;
	}
	
	@Override
	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}
	
	@Override
	public String getExternalId() {
		return externalId;
	}

	@Override
	public UUID getRequestItem() {
		return requestItem;
	}

	@Override
	public void setRequestItem(UUID requestItem) {
		this.requestItem = requestItem;
	}
	
	/**
	 * @since 9.3.0
	 * @param environment
	 */
	public void setEnvironment(String environment) {
		this.environment = environment;
	}
	
	/**
	 * @since 9.3.0
	 * @return
	 */
	public String getEnvironment() {
		return environment;
	}
	
	/**
	 * @since 9.3.0
	 * @return
	 */
	public String getBaseCode() {
		return baseCode;
	}
	
	/**
	 * @since 9.3.0
	 * @param baseCode
	 */
	public void setBaseCode(String baseCode) {
		this.baseCode = baseCode;
	}

	/**
	 * @since 9.4.0
	 */
	public UUID getIdentityRoleAttributeDefinition() {
		return identityRoleAttributeDefinition;
	}

	/**
	 * @since 9.4.0
	 */
	public void setIdentityRoleAttributeDefinition(UUID identityRoleAttributeDefinition) {
		this.identityRoleAttributeDefinition = identityRoleAttributeDefinition;
	}
	
	/**
	 * Count of sub roles
	 * 
	 * @since 9.4.0
	 * @return
	 */
	public long getChildrenCount() {
		return childrenCount;
	}
	
	/**
	 * Count of sub roles
	 * 
	 * @param childrenCount
	 * @since 9.4.0
	 */
	public void setChildrenCount(long childrenCount) {
		this.childrenCount = childrenCount;
	}

	/**
	 * Count of role-systems for this role, which are in cross-domain groups.
	 */
	public void setSystemsInCrossDomains(long systemsInCrossDomains) {
		this.systemsInCrossDomains = systemsInCrossDomains;
	}

	/**
	 * Count of role-systems for this role, which are in cross-domain groups.
	 */
	public long getSystemsInCrossDomains() {
		return systemsInCrossDomains;
	}

	/**
	 *  Prefilled role-system which is in cross-domain groups and identity has account for that system.
	 */
	public UUID getPrefilledSystemInCrossDomains() {
		return prefilledSystemInCrossDomains;
	}

	/**
	 *  Prefilled role-system which is in cross-domain groups and identity has account for that system.
	 */
	public void setPrefilledSystemInCrossDomains(UUID prefilledSystemInCrossDomains) {
		this.prefilledSystemInCrossDomains = prefilledSystemInCrossDomains;
	}
}
