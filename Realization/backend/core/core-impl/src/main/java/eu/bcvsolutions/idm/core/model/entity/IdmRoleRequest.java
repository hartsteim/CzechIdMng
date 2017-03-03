package eu.bcvsolutions.idm.core.model.entity;

import javax.persistence.Column;
import javax.persistence.ConstraintMode;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;

import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.model.domain.RoleRequestState;

/**
 * Request for roles
 * 
 * @author svandav
 *
 */
@Entity
@Table(name = "idm_role_request")
public class IdmRoleRequest extends AbstractEntity {

	private static final long serialVersionUID = 1L;

	@Audited
	@NotNull
	@ManyToOne(optional = false)
	@JoinColumn(name = "identity_id", referencedColumnName = "id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
	@SuppressWarnings("deprecation") // jpa FK constraint does not work in
										// hibernate 4
	@org.hibernate.annotations.ForeignKey(name = "none")
	private IdmIdentity identity;

	@Audited
	@NotNull
	@Column(name = "state")
	@Enumerated(EnumType.STRING)
	private RoleRequestState state = RoleRequestState.CREATED;

	@Audited
	@Column(name = "wf_process_id")
	private String wfProcessId;

	@Type(type = "org.hibernate.type.StringClobType")
	@Column(name = "original_request")
	private String originalRequest;

	@Type(type = "org.hibernate.type.StringClobType")
	@Column(name = "log")
	private String log;

	@Audited
	@NotNull
	@Column(name = "execute_immediately")
	private boolean executeImmediately = false;

	@Audited
	@ManyToOne(optional = true)
	@JoinColumn(name = "duplicated_to_request", referencedColumnName = "id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT), nullable = true)
	@SuppressWarnings("deprecation") // jpa FK constraint does not work in
										// hibernate 4
	private IdmRoleRequest duplicatedToRequest;

	public IdmIdentity getIdentity() {
		return identity;
	}

	public void setIdentity(IdmIdentity identity) {
		this.identity = identity;
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

	public String getOriginalRequest() {
		return originalRequest;
	}

	public void setOriginalRequest(String originalRequest) {
		this.originalRequest = originalRequest;
	}

	public boolean isExecuteImmediately() {
		return executeImmediately;
	}

	public void setExecuteImmediately(boolean executeImmediately) {
		this.executeImmediately = executeImmediately;
	}

	public IdmRoleRequest getDuplicatedToRequest() {
		return duplicatedToRequest;
	}

	public void setDuplicatedToRequest(IdmRoleRequest duplicatedToRequest) {
		this.duplicatedToRequest = duplicatedToRequest;
	}

	public String getLog() {
		return log;
	}

	public void setLog(String log) {
		this.log = log;
	}

}