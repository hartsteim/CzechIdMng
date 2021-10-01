package eu.bcvsolutions.idm.core.model.entity.eav;

import javax.persistence.ConstraintMode;
import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.envers.Audited;

import eu.bcvsolutions.idm.core.api.domain.AuditSearchable;
import eu.bcvsolutions.idm.core.eav.entity.AbstractFormValue;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormAttribute;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;

/**
 * Identity extended attributes
 * 
 * @author Radek Tomiška
 *
 */
@Entity
@Table(name = "idm_identity_form_value", indexes = {
		@Index(name = "idx_idm_identity_form_a", columnList = "owner_id"),
		@Index(name = "idx_idm_identity_form_a_def", columnList = "attribute_id"),
		@Index(name = "idx_idm_identity_form_stxt", columnList = "short_text_value"),
		@Index(name = "idx_idm_identity_form_uuid", columnList = "uuid_value") })
public class IdmIdentityFormValue extends AbstractFormValue<IdmIdentity> implements AuditSearchable {

	private static final long serialVersionUID = -6873566385389649927L;
	
	@Audited
	@ManyToOne(optional = false)
	@JoinColumn(name = "owner_id", referencedColumnName = "id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
	private IdmIdentity owner;
	
	public IdmIdentityFormValue() {
	}
	
	public IdmIdentityFormValue(IdmFormAttribute formAttribute) {
		super(formAttribute);
	}
	
	@Override
	public IdmIdentity getOwner() {
		return owner;
	}
	
	public void setOwner(IdmIdentity owner) {
		this.owner = owner;
	}
}
