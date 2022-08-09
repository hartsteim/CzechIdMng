package eu.bcvsolutions.idm.acc.entity;

import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;
import eu.bcvsolutions.idm.core.model.entity.AbstractRoleAssignment;
import org.hibernate.envers.Audited;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * @author Peter Štrunc <github.com/peter-strunc>
 */
@Entity
@Table(name = "idm_account_role", indexes = {
        @Index(name = "idx_idm_account_role_ident_a", columnList = "account_id"),
        @Index(name = "idx_idm_account_role_role", columnList = "role_id"),
        @Index(name = "idx_idm_account_role_aut_r", columnList = "automatic_role_id"),
        @Index(name = "idx_idm_account_role_ext_id", columnList = "external_id"),
        @Index(name = "idx_idm_account_role_d_r_id", columnList = "direct_role_id"),
        @Index(name = "idx_idm_account_role_comp_id", columnList = "role_composition_id")
})
public class AccAccountRole extends AbstractRoleAssignment<AccIdentityAccount> {

    private static final long serialVersionUID = 1L;

    @Audited
    @Size(max = DefaultFieldLengths.NAME)
    @Column(name = "external_id", length = DefaultFieldLengths.NAME)
    private String externalId;

    // this cannot be abstracted in AbstractRoleAssignment, because of different column names in each subclass
    @Audited
    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", referencedColumnName = "id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private AccAccount account;

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public AccAccount getAccount() {
        return account;
    }

    public void setAccount(AccAccount account) {
        this.account = account;
    }
}
