package eu.bcvsolutions.idm.core.eav.entity;

import java.math.BigDecimal;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.ConstraintMode;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ForeignKey;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;
import javax.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.api.entity.UnmodifiableEntity;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;

/**
 * Single attribute definition in one form definition.
 * 
 * @author Radek Tomiška
 *
 */
@Entity
@Audited
@Table(name = "idm_form_attribute", indexes = {
		@Index(name = "idx_idm_f_a_definition_def", columnList = "definition_id"),
		@Index(name = "ux_idm_f_a_definition_name", columnList = "definition_id, code", unique = true),
		@Index(name = "idx_idm_f_a_code", columnList = "code" )})
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class IdmFormAttribute extends AbstractEntity implements UnmodifiableEntity {

	private static final long serialVersionUID = 6037781154742359100L;
	//	
	@ManyToOne(optional = false)
	@JoinColumn(name = "definition_id", referencedColumnName = "id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
	private IdmFormDefinition formDefinition;
	
	@NotEmpty
	@Basic(optional = false)
	@Size(min = 1, max = DefaultFieldLengths.NAME)
	@Column(name = "code", nullable = false, length = DefaultFieldLengths.NAME)
	private String code; 
	
	@NotEmpty
	@Basic(optional = false)
	@Size(min = 1, max = DefaultFieldLengths.NAME)
	@Column(name = "name", nullable = false, length = DefaultFieldLengths.NAME)
	private String name;
	
	@Size(max = DefaultFieldLengths.DESCRIPTION)
	@Column(name = "description", nullable = true, length = DefaultFieldLengths.DESCRIPTION)
	private String description;	
	
	@Size(max = DefaultFieldLengths.NAME)
	@Column(name = "placeholder", nullable = true)
	private String placeholder;	
	
	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "persistent_type", length = 45, nullable = false)
	private PersistentType persistentType;
	
	@Column(name = "face_type", length = 45)
	@JsonProperty(access = Access.READ_ONLY)
	private String faceType;
	
	@Column(name = "face_properties", length = Integer.MAX_VALUE)
	private ConfigurationMap properties;
	
	@NotNull
	@Column(name = "multiple", nullable = false)
	private boolean multiple;
	
	@NotNull
	@Column(name = "required", nullable = false)
	private boolean required;
	
	@NotNull
	@Column(name = "readonly", nullable = false)
	private boolean readonly;
	
	@NotNull
	@Column(name = "confidential", nullable = false)
	private boolean confidential;
	
	@Min(Short.MIN_VALUE)
	@Max(Short.MAX_VALUE)
	@Column(name = "seq")
	private Short seq;
	
	@Type(type = "org.hibernate.type.TextType")
	@Column(name = "default_value", nullable = true)
	private String defaultValue;
	
	@NotNull
	@Column(name = "unmodifiable", nullable = false)
	private boolean unmodifiable = false;
	
	@NotNull
	@Column(name = "validation_unique", nullable = false)
	private boolean unique;
	
	@Column(name = "validation_max", nullable = true, precision = 38, scale = 4)
	private BigDecimal max;
	
	@Column(name = "validation_min", nullable = true, precision = 38, scale = 4)
	private BigDecimal min;
	
	@Size(max = DefaultFieldLengths.DESCRIPTION)
	@Column(name = "validation_regex", nullable = true, length = DefaultFieldLengths.DESCRIPTION)
	private String regex;
	
	@Size(max = DefaultFieldLengths.DESCRIPTION)
	@Column(name = "validation_message", nullable = true, length = DefaultFieldLengths.DESCRIPTION)
	private String validationMessage;

	public IdmFormAttribute() {
	}
	
	/**
	 * Code / key - unique in one form definition.
	 */
	public String getCode() {
		return code;
	}
	
	public void setCode(String code) {
		this.code = code;
	}

	/**
	 * User friendly name (label).
	 * 
	 * @return
	 */
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * Form definition.
	 * 
	 * @return
	 */
	public IdmFormDefinition getFormDefinition() {
		return formDefinition;
	}
	
	public void setFormDefinition(IdmFormDefinition formDefinition) {
		this.formDefinition = formDefinition;
	}

	/**
	 * Data type.
	 * 
	 * @return
	 */
	public PersistentType getPersistentType() {
		return persistentType;
	}

	public void setPersistentType(PersistentType persistentType) {
		this.persistentType = persistentType;
	}

	/**
	 * Multi values (list).
	 * @return
	 */
	public boolean isMultiple() {
		return multiple;
	}

	public void setMultiple(boolean multiple) {
		this.multiple = multiple;
	}

	/**
	 * Required attribute.
	 * 
	 * @return
	 */
	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	/**
	 * Order on FE form.
	 * 
	 * @return order
	 */
	public Short getSeq() {
		return seq;
	}

	/**
	 * Order on FE form.
	 * 
	 * @param seq order
	 */
	public void setSeq(Short seq) {
		this.seq = seq;
	}

	/**
	 * Attribute cannot be changed by user.
	 * 
	 * @return
	 */
	public boolean isReadonly() {
		return readonly;
	}

	public void setReadonly(boolean readonly) {
		this.readonly = readonly;
	}	

	/**
	 * User friendly description (tooltip).
	 * @return
	 */
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * If attribute value is secured (password, token, etc.).
	 * 
	 * @return
	 */
	public boolean isConfidential() {
		return confidential;
	}

	public void setConfidential(boolean confidential) {
		this.confidential = confidential;
	}
	
	/**
	 * Default value (toString).
	 * 
	 * @return
	 */
	public String getDefaultValue() {
		return defaultValue;
	}
	
	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}
	
	/**
	 * Attribute placeholder.
	 * 
	 * @return
	 */
	public String getPlaceholder() {
		return placeholder;
	}
	
	public void setPlaceholder(String placeholder) {
		this.placeholder = placeholder;
	}

	@Override
	public boolean isUnmodifiable() {
		return this.unmodifiable;
	}

	@Override
	public void setUnmodifiable(boolean unmodifiable) {
		this.unmodifiable = unmodifiable;
	}
	
	public void setFaceType(String faceType) {
		this.faceType = faceType;
	}
	
	public String getFaceType() {
		return faceType;
	}
	
	/**
	 * @return
	 * @since 9.4.0
	 */
	public boolean isUnique() {
		return unique;
	}

	/**
	 * @param unique
	 * @since 9.4.0
	 */
	public void setUnique(boolean unique) {
		this.unique = unique;
	}

	/**
	 * @return
	 * @since 9.4.0
	 */
	public BigDecimal getMax() {
		return max;
	}

	/**
	 * @param max
	 * @since 9.4.0
	 */
	public void setMax(BigDecimal max) {
		this.max = max;
	}

	/**
	 * @return
	 * @since 9.4.0
	 */
	public BigDecimal getMin() {
		return min;
	}

	/**
	 * @param min
	 * @since 9.4.0
	 */
	public void setMin(BigDecimal min) {
		this.min = min;
	}

	/**
	 * @return
	 * @since 9.4.0
	 */
	public String getRegex() {
		return regex;
	}

	/**
	 * @param regex
	 * @since 9.4.0
	 */
	public void setRegex(String regex) {
		this.regex = regex;
	}
	
	/**
	 * Custom message, when validation fails - localization key can be used.
	 * 
	 * @return
	 * @since 9.4.0
	 */
	public String getValidationMessage() {
		return validationMessage;
	}
	
	/**
	 * Custom message, when validation fails - localization key can be used.
	 * 
	 * @param validationMessage
	 * @since 9.4.0
	 */
	public void setValidationMessage(String validationMessage) {
		this.validationMessage = validationMessage;
	}
	
	/**
	 * Additional form attribute properties (by face type).
	 * 
	 * @return configured properties
	 * @since 10.8.0
	 */
	public ConfigurationMap getProperties() {
		return properties;
	}
	
	/**
	 * Additional form attribute properties (by face type).
	 * 
	 * @param properties configured properties
	 * @since 10.8.0
	 */
	public void setProperties(ConfigurationMap properties) {
		this.properties = properties;
	}
}
