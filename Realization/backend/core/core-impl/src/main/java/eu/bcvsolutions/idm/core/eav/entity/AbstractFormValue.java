package eu.bcvsolutions.idm.core.eav.entity;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.ConstraintMode;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;
import org.springframework.core.GenericTypeResolver;
import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import eu.bcvsolutions.idm.core.api.domain.AuditSearchable;
import eu.bcvsolutions.idm.core.api.domain.Codeable;
import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.entity.FormableEntity;
import eu.bcvsolutions.idm.core.ecm.api.entity.AttachableEntity;

/**
 * Super class for "extended" attribute values, which can be added to custom
 * abstract entity
 * 
 * @author Radek Tomiška
 *
 * @param <O> Owner entity class
 */
@MappedSuperclass
public abstract class AbstractFormValue<O extends FormableEntity> 
		extends AbstractEntity 
		implements AttachableEntity, AuditSearchable {

	private static final long serialVersionUID = -5914285774914667917L;

	@NotNull
	@Audited
	@ManyToOne(optional = false)
	@JoinColumn(name = "attribute_id", referencedColumnName = "id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
	private IdmFormAttribute formAttribute;

	@NotNull
	@Audited
	@Enumerated(EnumType.STRING)
	@Column(name = "persistent_type", length = 45, nullable = false)
	@JsonProperty(access = Access.READ_ONLY)
	private PersistentType persistentType;
	
	@NotNull
	@Audited
	@Column(name = "confidential", nullable = false)
	@JsonProperty(access = Access.READ_ONLY)
	private boolean confidential;

	@Audited
	@Type(type = "org.hibernate.type.TextType")
	@Column(name = "string_value", nullable = true)
	private String stringValue;
	
	@Audited
	@Size(max = DefaultFieldLengths.DESCRIPTION)
	@Column(name = "short_text_value", nullable = true, length = DefaultFieldLengths.DESCRIPTION)
	private String shortTextValue;

	@Audited
	@Column(name = "boolean_value", nullable = true)
	private Boolean booleanValue;

	@Audited
	@Column(name = "long_value", nullable = true)
	private Long longValue;

	@Audited
	@Column(name = "double_value", nullable = true, precision = 38, scale = 4)
	private BigDecimal doubleValue;

	@Audited
	@Column(name = "date_value")
	private ZonedDateTime dateValue;
	
	@Audited
	@Column(name = "byte_value")
	private byte[] byteValue;
	
	@Audited
	@JsonDeserialize(as = UUID.class)
	@Column(name = "uuid_value", length = 16)
	private UUID uuidValue;

	@Audited
	@Min(Short.MIN_VALUE)
	@Max(Short.MAX_VALUE)
	@Column(name = "seq")
	private short seq;
	//
	private transient String ownerType;

	public AbstractFormValue() {
		this(null, null);
	}

	public AbstractFormValue(UUID id) {
		this(id, null);
	}

	public AbstractFormValue(IdmFormAttribute formAttribute) {
		this(null, formAttribute);
		// ~ backward compatibility
		Assert.notNull(formAttribute, "Form attribute is required for form value construction.");
	}
	
	/**
	 * @since 11.3.0
	 */
	public AbstractFormValue(UUID id, IdmFormAttribute formAttribute) {
		super(id);
		//
		Class<?>[] genericTypes = GenericTypeResolver.resolveTypeArguments(getClass(), AbstractFormValue.class);
		ownerType = ((Class<?>) genericTypes[0]).getCanonicalName();
		//
		if (formAttribute != null) {
			this.formAttribute = formAttribute;
			this.persistentType = formAttribute.getPersistentType();
			this.confidential = formAttribute.isConfidential();
		}
	}

	/**
	 * Returns entity, for witch is this attribute value
	 * 
	 * @return
	 */
	public abstract O getOwner();
	
	/**
	 * Sets this attribute value owner
	 * 
	 * @param owner
	 */
	public abstract void setOwner(O owner);

	/**
	 * Attribute definition
	 * 
	 * @return
	 */
	public IdmFormAttribute getFormAttribute() {
		return formAttribute;
	}

	public void setFormAttribute(IdmFormAttribute formAttribute) {
		this.formAttribute = formAttribute;
	}
	
	/**
	 * Set s owner and all attribute properties.
	 * 
	 * @param owner
	 * @param attribute
	 */
	public void setOwnerAndAttribute(O owner, IdmFormAttribute attribute) {
		setOwner(owner);
		setFormAttribute(attribute);
		if (attribute != null) {
			setPersistentType(attribute.getPersistentType());
			setConfidential(attribute.isConfidential());
		}
	}

	public String getStringValue() {
		return stringValue;
	}

	public void setStringValue(String stringValue) {
		this.stringValue = stringValue;
	}

	public short getSeq() {
		return seq;
	}

	public void setSeq(short seq) {
		this.seq = seq;
	}

	public void setPersistentType(PersistentType persistentType) {
		this.persistentType = persistentType;
	}

	public PersistentType getPersistentType() {
		return persistentType;
	}

	public Boolean getBooleanValue() {
		return booleanValue;
	}

	public void setBooleanValue(Boolean booleanValue) {
		this.booleanValue = booleanValue;
	}

	public Long getLongValue() {
		return longValue;
	}

	public void setLongValue(Long longValue) {
		this.longValue = longValue;
	}

	public BigDecimal getDoubleValue() {
		return doubleValue;
	}

	public void setDoubleValue(BigDecimal doubleValue) {
		this.doubleValue = doubleValue;
	}

	public ZonedDateTime getDateValue() {
		return dateValue;
	}

	public void setDateValue(ZonedDateTime dateValue) {
		this.dateValue = dateValue;
	}
	
	public byte[] getByteValue() {
		return byteValue;
	}

	public void setByteValue(byte[] byteValue) {
		this.byteValue = byteValue;
	}
	
	public UUID getUuidValue() {
		return uuidValue;
	}
	
	public void setUuidValue(UUID uuidValue) {
		this.uuidValue = uuidValue;
	}

	public boolean isConfidential() {
		return confidential;
	}
	
	public void setConfidential(boolean confidential) {
		this.confidential = confidential;
	}
	
	public String getShortTextValue() {
		return shortTextValue;
	}
	
	public void setShortTextValue(String shortTextValue) {
		this.shortTextValue = shortTextValue;
	}
	
	/**
	 * @since 11.3.0
	 */
	@Override
	public String getOwnerId() {
		return this.getOwner().getId().toString();
	}

	/**
	 * @since 11.3.0
	 */
	@Override
	public String getOwnerCode() {
		if (this.getOwner() instanceof Codeable) {
			return ((Codeable) this.getOwner()).getCode();
		}
		return null;
	}

	/**
	 * @since 11.3.0
	 */
	@Override
	public String getOwnerType() {
		return ownerType;
	}

	/**
	 * @since 11.3.0
	 */
	@Override
	public String getSubOwnerId() {
		return getFormAttribute().getId().toString();
	}

	/**
	 * @since 11.3.0
	 */
	@Override
	public String getSubOwnerCode() {
		return getFormAttribute().getCode();
	}

	/**
	 * @since 11.3.0
	 */
	@Override
	public String getSubOwnerType() {
		return IdmFormAttribute.class.getCanonicalName();
	}
}
