package eu.bcvsolutions.idm.core.eav.entity;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;

import eu.bcvsolutions.idm.core.api.domain.Codeable;
import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;
import eu.bcvsolutions.idm.core.api.domain.Disableable;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;

/**
 * Form projection - entity can be created / edited by different form projection.
 * 
 * @author Radek Tomiška
 * @since 10.2.0
 */
@Entity
@Audited
@Table(name = "idm_form_projection", indexes = { 
		@Index(name = "idx_idm_form_proj_code", columnList = "code", unique = true),
		@Index(name = "idx_idm_form_proj_owner_type", columnList = "owner_type")
})
public class IdmFormProjection 
		extends AbstractEntity 
		implements Codeable, Disableable {

	private static final long serialVersionUID = 1L;
	
	@NotEmpty
	@Size(min = 1, max = DefaultFieldLengths.NAME)
	@Column(name = "code", length = DefaultFieldLengths.NAME, nullable = false)
	private String code;
	
	@NotEmpty
	@Size(min = 1, max = DefaultFieldLengths.NAME)
	@Column(name = "owner_type", length = DefaultFieldLengths.NAME, nullable = false)
	private String ownerType;
	
	@Size(max = DefaultFieldLengths.NAME)
	@Column(name = "module_id", length = DefaultFieldLengths.NAME)
	private String module;
	
	@Size(max = DefaultFieldLengths.NAME)
	@Column(name = "route", length = DefaultFieldLengths.NAME)
	private String route;
	
	@Size(max = DefaultFieldLengths.DESCRIPTION)
	@Column(name = "description", length = DefaultFieldLengths.DESCRIPTION)
	private String description;

	@NotNull
	@Column(name = "disabled", nullable = false)
	private boolean disabled;
	
	@Type(type = "org.hibernate.type.TextType")
	@Column(name = "form_definitions", nullable = true)
	private String formDefinitions;
	
	@Type(type = "org.hibernate.type.TextType")
	@Column(name = "basic_fields", nullable = true)
	private String basicFields;
	
	@Type(type = "org.hibernate.type.TextType")
	@Column(name = "form_validations", nullable = true)
	private String formValidations; // @since 11.0.0
	
	@Column(name = "projection_properties", length = Integer.MAX_VALUE)
	private ConfigurationMap properties; // cannot be string (~json) => needed on backend too.

	public IdmFormProjection() {
	}
	
	public IdmFormProjection(UUID id) {
		super(id);
	}
	
	@Override
	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getOwnerType() {
		return ownerType;
	}

	public void setOwnerType(String ownerType) {
		this.ownerType = ownerType;
	}
	
	public String getRoute() {
		return route;
	}
	
	public void setRoute(String route) {
		this.route = route;
	}
	
	public String getModule() {
		return module;
	}
	
	public void setModule(String module) {
		this.module = module;
	}
	
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
	
	@Override
	public boolean isDisabled() {
		return disabled;
	}
	
	@Override
	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}
	
	/**
	 * Enabled form definitions and attributes.
	 * Json representation - [{ "definition": "uuid", "attributes": [ "uuid", "uuid" ] }].
	 * 
	 * @return show definitions
	 */
	public String getFormDefinitions() {
		return formDefinitions;
	}
	
	/**
	 * Enabled form definitions and attributes.
	 * Json representation - [{ "definition": "uuid", "attributes": [ "uuid", "uuid" ] }].
	 * 
	 * @param formDefinitions
	 */
	public void setFormDefinitions(String formDefinitions) {
		this.formDefinitions = formDefinitions;
	}
	
	/**
	 * Rendered basic fields.
	 * Json representation - [ "username", "firstName", "lastName" ].
	 * 
	 * @return attributes joined by comma
	 */
	public String getBasicFields() {
		return basicFields;
	}
	
	/**
	 * Rendered basic fields.
	 * Json representation - [ "username", "firstName", "lastName" ].
	 * 
	 * @param basicFields attributes joined by comma
	 */
	public void setBasicFields(String basicFields) {
		this.basicFields = basicFields;
	}
	
	/**
	 * Additional projection properties (by owner type).
	 * 
	 * @return configured properties
	 */
	public ConfigurationMap getProperties() {
		return properties;
	}
	
	/**
	 * Additional projection properties (by owner type).
	 * 
	 * @param properties configured properties
	 */
	public void setProperties(ConfigurationMap properties) {
		this.properties = properties;
	}
	
	/**
	 * Overriden form attribute validations.
	 * Json representation - [ { id, basicField, code, formDefinition, required, min, max, regex, readOnly } ].
	 * 
	 * @return configured validations
	 * @since 11.0.0
	 */
	public String getFormValidations() {
		return formValidations;
	}
	
	/**
	 * Overriden form attribute validations.
	 * Json representation - [ { id, basicField, code, formDefinition, required, min, max, regex, readOnly } ].
	 * 
	 * @param formValidations configured validations
	 * @since 11.0.0
	 */
	public void setFormValidations(String formValidations) {
		this.formValidations = formValidations;
	}
}
