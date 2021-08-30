package eu.bcvsolutions.idm.core.api.dto;

import java.io.ObjectInputStream;
import java.io.ObjectInputStream.GetField;
import java.time.LocalDate;
import java.util.UUID;

import javax.persistence.Column;
import javax.validation.constraints.Size;

import org.hibernate.envers.Audited;
import org.springframework.hateoas.core.Relation;
import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonIgnore;

import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;
import eu.bcvsolutions.idm.core.api.domain.Embedded;
import eu.bcvsolutions.idm.core.api.domain.ExternalIdentifiable;
import eu.bcvsolutions.idm.core.api.entity.ValidableEntity;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import io.swagger.annotations.ApiModelProperty;

/**
 * IdentityRole DTO
 *
 * @author svanda
 * @author Radek Tomiška
 */
@Relation(collectionRelation = "identityRoles")
public class IdmIdentityRoleDto extends FormableDto implements ValidableEntity, ExternalIdentifiable {
	
	private static final long serialVersionUID = 1L;
	//
	public static final String PROPERTY_IDENTITY_CONTRACT = "identityContract";
	public static final String PROPERTY_ROLE = "role";
	public static final String PROPERTY_ROLE_TREE_NODE = "roleTreeNode";
	//
	@Size(max = DefaultFieldLengths.NAME)
	@ApiModelProperty(notes = "Unique external identifier.")
	private String externalId;
    @Embedded(dtoClass = IdmIdentityContractDto.class)
    private UUID identityContract;
    @Embedded(dtoClass = IdmContractPositionDto.class)
    private UUID contractPosition;
    @Embedded(dtoClass = IdmRoleDto.class)
    private UUID role;
    private LocalDate validFrom;
    private LocalDate validTill;
	@Deprecated
	@SuppressWarnings("unused")
    private boolean automaticRole; // this attribute can't be removed (serializable backward compatibility)
    @Embedded(dtoClass = AbstractIdmAutomaticRoleDto.class)
    private UUID roleTreeNode; // this attribute can't be renamed (backward compatibility) - AutomaticRole reference
    @Embedded(dtoClass = IdmIdentityRoleDto.class)
    private UUID directRole; // direct identity role
    @Embedded(dtoClass = IdmRoleCompositionDto.class)
    private UUID roleComposition; // direct role
	// Relation on sys-system form ACC. We need to working with that also in the core module (cross-domains).
	private UUID roleSystem;

    public IdmIdentityRoleDto() {
    }

    public IdmIdentityRoleDto(UUID id) {
        super(id);
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTill() {
        return validTill;
    }

    public void setValidTill(LocalDate validTo) {
        this.validTill = validTo;
    }

    public UUID getIdentityContract() {
        return identityContract;
    }

    public void setIdentityContract(UUID identityContract) {
        this.identityContract = identityContract;
    }
    
    @JsonIgnore
    public void setIdentityContractDto(IdmIdentityContractDto identityContract) {
    	Assert.notNull(identityContract, "Contract is requred to se into assigned role.");
    	//
        this.identityContract = identityContract.getId();
        this.getEmbedded().put(PROPERTY_IDENTITY_CONTRACT, identityContract);
    }

    public UUID getRole() {
        return role;
    }

    public void setRole(UUID role) {
        this.role = role;
    }
    
    public UUID getAutomaticRole() {
		return roleTreeNode;
	}
    
    public void setAutomaticRole(UUID automaticRole) {
		this.roleTreeNode = automaticRole;
	}
    
    @Override
	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}
	
	@Override
	public String getExternalId() {
		return externalId;
	}

	public UUID getDirectRole() {
		return directRole;
	}

	public void setDirectRole(UUID directRole) {
		this.directRole = directRole;
	}

	public UUID getRoleComposition() {
		return roleComposition;
	}

	public void setRoleComposition(UUID roleComposition) {
		this.roleComposition = roleComposition;
	}
	
	public UUID getContractPosition() {
		return contractPosition;
	}
	
	public void setContractPosition(UUID contractPosition) {
		this.contractPosition = contractPosition;
	}

	public UUID getRoleSystem() {
		return roleSystem;
	}

	public void setRoleSystem(UUID roleSystem) {
		this.roleSystem = roleSystem;
	}
	
	
	/**
	 * DTO are serialized in WF and embedded objects.
	 * We need to solve legacy issues with joda (old) vs. java time (new) usage.
	 * 
	 * @param ois
	 * @throws Exception
	 */
	private void readObject(ObjectInputStream ois) throws Exception {
		GetField readFields = ois.readFields();
		//
		externalId = (String) readFields.get("externalId", null);
	    identityContract = (UUID) readFields.get("identityContract", null);
	    contractPosition = (UUID) readFields.get("contractPosition", null);
	    role = (UUID) readFields.get("role", null);
	    validFrom = DtoUtils.toLocalDate(readFields.get("validFrom", null));
	    validTill = DtoUtils.toLocalDate(readFields.get("validTill", null));
	    roleTreeNode = (UUID) readFields.get("roleTreeNode", null);
	    directRole = (UUID) readFields.get("directRole", null);
	    roleComposition = (UUID) readFields.get("roleComposition", null);
		roleSystem = (UUID) readFields.get("roleSystem", null);
    }
}
