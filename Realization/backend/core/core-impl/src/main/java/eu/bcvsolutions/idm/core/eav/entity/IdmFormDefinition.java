package eu.bcvsolutions.idm.core.eav.entity;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.hibernate.envers.Audited;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;

import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.api.entity.UnmodifiableEntity;

/**
 * Form definition for different entity / object types.
 * 
 * @author Radek Tomiška
 *
 */
@Entity
@Table(name = "idm_form_definition", indexes = { 
		@Index(name = "ux_idm_form_definition_tn", columnList = "definition_type,code", unique = true),
		@Index(name = "idx_idm_form_definition_code", columnList = "code") })
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class IdmFormDefinition extends AbstractEntity implements UnmodifiableEntity {

	private static final long serialVersionUID = 8267096009610364911L;
	
	@Audited
	@NotEmpty
	@Basic(optional = false)	
	@Size(min = 1, max = DefaultFieldLengths.NAME)
	@Column(name = "definition_type", nullable = false, length = DefaultFieldLengths.NAME)
	private String type; // for entity / object type
	
	@Audited
	@NotEmpty
	@Size(min = 0, max = DefaultFieldLengths.NAME)
	@Column(name = "code", length = DefaultFieldLengths.NAME, nullable = false)
	private String code;
	
	@Audited
	@NotEmpty
	@Size(min = 0, max = DefaultFieldLengths.NAME)
	@Column(name = "name", length = DefaultFieldLengths.NAME, nullable = false)
	private String name;
	
	@Audited
	@Size(max = DefaultFieldLengths.NAME)
	@Column(name = "module_id", length = DefaultFieldLengths.NAME)
	private String module;
	
	@Audited
	@NotNull
	@Column(name = "main", nullable = false)
	private boolean main;
	
	@Audited
	@Size(max = DefaultFieldLengths.DESCRIPTION)
	@Column(name = "description", length = DefaultFieldLengths.DESCRIPTION)
	private String description;
	
	@NotNull
	@Column(name = "unmodifiable", nullable = false)
	private boolean unmodifiable = false;
	
	@Min(Short.MIN_VALUE)
	@Max(Short.MAX_VALUE)
	@Column(name = "seq")
	private Short seq;

	public IdmFormDefinition() {
	}
	
	/**
	 * Form definition for entity / object type
	 * 
	 * @return
	 */
	public String getType() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * Unique name for entity / object type
	 * 
	 * @return
	 */
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public boolean isUnmodifiable() {
		return this.unmodifiable;
	}

	@Override
	public void setUnmodifiable(boolean unmodifiable) {
		this.unmodifiable = unmodifiable;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public boolean isMain() {
		return main;
	}

	public void setMain(boolean main) {
		this.main = main;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
	
	/**
	 * Form definition is from / for the module (identifier)
	 * 
	 * @return
	 * @since 9.4.0
	 */
	public String getModule() {
		return module;
	}
	
	/**
	 * Form definition is from / for the module (identifier)
	 * 
	 * @param module
	 */
	public void setModule(String module) {
		this.module = module;
	}
	
	/**
	 * Order on FE form.
	 * 
	 * @return order
	 * @since 11.1.0
	 */
	public Short getSeq() {
		return seq;
	}

	/**
	 * Order on FE form.
	 * 
	 * @param seq order
	 * @since 11.1.0
	 */
	public void setSeq(Short seq) {
		this.seq = seq;
	}
}
