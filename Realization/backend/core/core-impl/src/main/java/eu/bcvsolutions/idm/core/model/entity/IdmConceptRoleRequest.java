package eu.bcvsolutions.idm.core.model.entity;

import java.time.LocalDate;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.ConstraintMode;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ForeignKey;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;

import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.entity.ValidableEntity;
import eu.bcvsolutions.idm.core.eav.api.entity.FormableEntity;

/**
 * Concept for requested role.
 * @author svandav
 *
 */
@Entity
@Table(name = "idm_concept_role_request", indexes = {
		@Index(name = "idx_idm_conc_role_ident_c", columnList = "identity_contract_id"),
		@Index(name = "idx_idm_conc_role_c_p", columnList = "contract_position_id"),
		@Index(name = "idx_idm_conc_role_request", columnList = "request_role_id"),
		@Index(name = "idx_idm_conc_role_role", columnList = "role_id")
})
public class IdmConceptRoleRequest extends AbstractEntity implements ValidableEntity, FormableEntity {

	
	private static final long serialVersionUID = 1L;

	@Audited
	@NotNull
	@ManyToOne(optional = false)
	@JoinColumn(name = "request_role_id", referencedColumnName = "id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
	private IdmRoleRequest roleRequest;
	
	@Audited
	@ManyToOne(optional = true)
	@NotFound(action = NotFoundAction.IGNORE)
	@JoinColumn(name = "identity_contract_id", referencedColumnName = "id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
	private IdmIdentityContract identityContract;
	
	@Audited
	@Column(name = "contract_position_id", length = 16, nullable = true)
	private UUID contractPosition;
	
	@Audited
	@ManyToOne(optional = true)
	@NotFound(action = NotFoundAction.IGNORE)
	@JoinColumn(name = "role_id", referencedColumnName = "id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
	private IdmRole role;
	
	@Audited
	@ManyToOne(optional = true)
	@NotFound(action = NotFoundAction.IGNORE)
	@JoinColumn(name = "identity_role_id", referencedColumnName = "id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
	private IdmIdentityRole identityRole;
	
	@Audited
	@ManyToOne(optional = true)
	@NotFound(action = NotFoundAction.IGNORE)
	@JoinColumn(name = "automatic_role_id", referencedColumnName = "id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
	private IdmAutomaticRole automaticRole;
	
	@Audited
	@Column(name = "direct_role_id", length = 16, nullable = true)
	private UUID directRole;
	
	@Audited
	@Column(name = "direct_concept_id", length = 16, nullable = true)
	private UUID directConcept;
	
	@Audited
	@Column(name = "role_composition_id", length = 16, nullable = true)
	private UUID roleComposition;
	
	@Audited
	@Column(name = "valid_from")
	private LocalDate validFrom;
	
	@Audited
	@Column(name = "valid_till")
	private LocalDate validTill;
	
	@Audited
	@Column(name = "operation")
	@Enumerated(EnumType.STRING)
	private ConceptRoleRequestOperation operation;
	
	@Audited
	@Column(name = "state")
	@Enumerated(EnumType.STRING)
	@NotNull
	private RoleRequestState state = RoleRequestState.CONCEPT;
	
	// @Audited
	@Embedded
	private OperationResult systemState;
	
	@Audited
	@Column(name = "wf_process_id")
	private String wfProcessId;
	
	@Type(type = "org.hibernate.type.TextType")
	@Column(name = "log")
	private String log;
	
	// Relation on sys-system form ACC. We need to working with that also in the core module (cross-domains). 
	@Audited
	@Column(name = "role_system_id", length = 16)
	private UUID roleSystem;

	public IdmRoleRequest getRoleRequest() {
		return roleRequest;
	}

	public void setRoleRequest(IdmRoleRequest roleRequest) {
		this.roleRequest = roleRequest;
	}

	public IdmIdentityContract getIdentityContract() {
		return identityContract;
	}

	public void setIdentityContract(IdmIdentityContract identityContract) {
		this.identityContract = identityContract;
	}

	public IdmRole getRole() {
		return role;
	}

	public void setRole(IdmRole role) {
		this.role = role;
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

	public void setValidTill(LocalDate validTill) {
		this.validTill = validTill;
	}

	public ConceptRoleRequestOperation getOperation() {
		return operation;
	}

	public void setOperation(ConceptRoleRequestOperation operation) {
		this.operation = operation;
	}

	public RoleRequestState getState() {
		return state;
	}

	public void setState(RoleRequestState state) {
		this.state = state;
	}

	public String getWfProcessId() {
		return wfProcessId;
	}

	public void setWfProcessId(String wfProcessId) {
		this.wfProcessId = wfProcessId;
	}

	public IdmIdentityRole getIdentityRole() {
		return identityRole;
	}

	public void setIdentityRole(IdmIdentityRole identityRole) {
		this.identityRole = identityRole;
	}

	public String getLog() {
		return log;
	}

	public void setLog(String log) {
		this.log = log;
	}

	public IdmAutomaticRole getAutomaticRole() {
		return automaticRole;
	}

	public void setAutomaticRole(IdmAutomaticRole automaticRole) {
		this.automaticRole = automaticRole;
	}
	
	/**
	 * Other contract position - identifier only
	 * 
	 * @return
	 * @since 9.6.0
	 */
	public UUID getContractPosition() {
		return contractPosition;
	}
	
	/**
	 * Other contract position - identifier only
	 * 
	 * @param contractPosition
	 * @since 9.6.0
	 */
	public void setContractPosition(UUID contractPosition) {
		this.contractPosition = contractPosition;
	}

	public OperationResult getSystemState() {
		return systemState;
	}

	public void setSystemState(OperationResult systemState) {
		this.systemState = systemState;
	}
	
	/**
	 * Concept for business role.
	 * 
	 * @return directly assigned role
	 * @since 10.6.0
	 */
	public UUID getDirectRole() {
		return directRole;
	}
	
	/**
	 * Concept for business role.
	 * 
	 * @param directRole directly assigned role concept
	 * @since 10.6.0
	 */
	public void setDirectRole(UUID directRole) {
		this.directRole = directRole;
	}
	
	/**
	 * Concept for business role.
	 * 
	 * @return directly assigned role concept
	 * @since 10.6.0
	 */
	public UUID getDirectConcept() {
		return directConcept;
	}
	
	/**
	 * Concept for business role concept.
	 * 
	 * @since 10.6.0
	 */
	public void setDirectConcept(UUID directConcept) {
		this.directConcept = directConcept;
	}
	
	/**
	 * Concept for business role.
	 * 
	 * @return business role
	 * @since 10.6.0
	 */
	public UUID getRoleComposition() {
		return roleComposition;
	}	
	
	/**
	 * Concept for business role.
	 * 
	 * @param roleComposition business role
	 * @since 10.6.0
	 */
	public void setRoleComposition(UUID roleComposition) {
		this.roleComposition = roleComposition;
	}

	public UUID getRoleSystem() {
		return roleSystem;
	}

	public void setRoleSystem(UUID roleSystem) {
		this.roleSystem = roleSystem;
	}
}
