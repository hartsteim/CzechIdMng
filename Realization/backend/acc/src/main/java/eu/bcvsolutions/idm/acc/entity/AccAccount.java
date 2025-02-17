package eu.bcvsolutions.idm.acc.entity;

import java.time.ZonedDateTime;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.ConstraintMode;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ForeignKey;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.envers.Audited;

import com.fasterxml.jackson.annotation.JsonIgnore;

import eu.bcvsolutions.idm.acc.domain.AccountType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;

/**
 * Account on target system
 * 
 * @author Radek Tomiška
 *
 */
@Entity
@Table(name = "acc_account", indexes = { 
		@Index(name = "ux_acc_account_sys_entity", columnList = "system_entity_id", unique = true),
		@Index(name = "ux_account_uid", columnList = "uid,system_id", unique = true),
		@Index(name = "idx_acc_account_sys_id", columnList = "system_id"),
		@Index(name = "idx_acc_account_sys_entity", columnList = "system_entity_id")
		})
public class AccAccount extends AbstractEntity {
	
	private static final long serialVersionUID = -565558977675057360L;

	@Audited
	@NotEmpty
	@Size(min = 1, max = DefaultFieldLengths.UID)
	@Column(name = "uid", length = DefaultFieldLengths.UID, nullable = false)
	private String uid;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "entity_type")
	private SystemEntityType entityType;
	
	@Audited
	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "account_type", nullable = false)
	private AccountType accountType;
	
	@Audited
	@NotNull
	@ManyToOne(optional = false)
	@JoinColumn(name = "system_id", referencedColumnName = "id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
	private SysSystem system;
	
	@NotFound(action = NotFoundAction.IGNORE) // system delete entity can be deleted in the meantime
	@Audited
	@ManyToOne(optional = true)
	@JoinColumn(name = "system_entity_id", referencedColumnName = "id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
	private SysSystemEntity systemEntity;
	
	@JsonIgnore
	@OneToMany(mappedBy = "account")
	private List<AccIdentityAccount> identityAccounts;  // only for hibernate mappnig - we dont want lazy lists
	
	@Audited
	@Column(name = "in_protection", nullable = true)
	private boolean inProtection = false;
	
	@Audited
	@Column(name = "end_of_protection", nullable = true)
	private ZonedDateTime endOfProtection;

	public void setAccountType(AccountType accountType) {
		this.accountType = accountType;
	}
	
	public AccountType getAccountType() {
		return accountType;
	}

	public SysSystem getSystem() {
		return system;
	}

	public void setSystem(SysSystem system) {
		this.system = system;
	}

	public SysSystemEntity getSystemEntity() {
		return systemEntity;
	}

	public void setSystemEntity(SysSystemEntity systemEntity) {
		this.systemEntity = systemEntity;
	}
	
	public void setUid(String uid) {
		this.uid = uid;
	}
	
	public String getUid() {
		return uid;
	}
	
	public boolean isInProtection() {
		return inProtection;
	}

	public void setInProtection(boolean inProtection) {
		this.inProtection = inProtection;
	}

	public ZonedDateTime getEndOfProtection() {
		return endOfProtection;
	}

	public void setEndOfProtection(ZonedDateTime endOfProtection) {
		this.endOfProtection = endOfProtection;
	}

	/**
	 * Return real uid from system entity.
	 * If system entity do not exist, then return uid from account.
	 * 
	 * @return
	 */
	public String getRealUid() {
		if (systemEntity != null) {
			return systemEntity.getUid();
		}
		return uid;
	}
	
	public SystemEntityType getEntityType() {
		return entityType;
	}
	
	public void setEntityType(SystemEntityType entityType) {
		this.entityType = entityType;
	}
}
