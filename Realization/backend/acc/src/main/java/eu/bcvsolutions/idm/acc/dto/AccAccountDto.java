package eu.bcvsolutions.idm.acc.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.UUID;

import javax.validation.constraints.Size;

import org.springframework.hateoas.core.Relation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.google.common.annotations.Beta;

import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;
import eu.bcvsolutions.idm.core.api.domain.Embedded;
import eu.bcvsolutions.idm.core.api.domain.ExternalIdentifiable;
import eu.bcvsolutions.idm.core.api.dto.FormableDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import io.swagger.annotations.ApiModelProperty;

/**
 * Account on target system DTO
 * 
 * @author Svanda
 * @author Roman Kucera
 * @author Tomáš Doischer
 *
 */

@Relation(collectionRelation = "accounts")
public class AccAccountDto extends FormableDto implements ExternalIdentifiable, Serializable {

	private static final long serialVersionUID = 1L;

	public static final String PROPERTY_END_OF_PROTECTION = "endOfProtection";
	public static final String PROPERTY_ECHO = "echo";

	@Size(max = DefaultFieldLengths.NAME)
	@ApiModelProperty(notes = "Unique external identifier.")
	private String externalId;
	private String uid;
	@Embedded(dtoClass = SysSystemDto.class)
	private UUID system;
	@Embedded(dtoClass = SysSystemEntityDto.class)
	private UUID systemEntity;
	private boolean inProtection;
	private ZonedDateTime endOfProtection;
	private String realUid;
	private String entityType;
	@Beta
	private UUID targetEntityId;
	@Beta
	private String targetEntityType;
	@Embedded(dtoClass = SysSystemMappingDto.class)
	private UUID systemMapping;
	@Embedded(dtoClass = IdmFormDefinitionDto.class)
	private UUID formDefinition;

	public String getUid() {
		return uid;
	}

	public void setUid(String uid) {
		this.uid = uid;
	}

	public UUID getSystem() {
		return system;
	}

	public void setSystem(UUID system) {
		this.system = system;
	}

	public UUID getSystemEntity() {
		return systemEntity;
	}

	public void setSystemEntity(UUID systemEntity) {
		this.systemEntity = systemEntity;
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
	 * Check if account is in protection. Validate end of protection too.
	 * 
	 * @param account
	 * @return
	 */
	public boolean isAccountProtectedAndValid() {
		if (this.isInProtection()) {
			if (this.getEndOfProtection() == null) {
				return true;
			}
			if (this.getEndOfProtection() != null && this.getEndOfProtection().isAfter(ZonedDateTime.now())) {
				return true;
			}
		}
		return false;
	}

	@JsonProperty(access = Access.READ_ONLY)
	public String getRealUid() {
		return realUid;
	}

	public void setRealUid(String realUid) {
		this.realUid = realUid;
	}
	
	public String getEntityType() {
		return entityType;
	}
	
	public void setEntityType(String entityType) {
		this.entityType = entityType;
	}

	@Beta
	public UUID getTargetEntityId() {
		return targetEntityId;
	}

	@Beta
	public void setTargetEntityId(UUID targetEntityId) {
		this.targetEntityId = targetEntityId;
	}

	@Beta
	public String getTargetEntityType() {
		return targetEntityType;
	}

	@Beta
	public void setTargetEntityType(String targetEntityType) {
		this.targetEntityType = targetEntityType;
	}

	public UUID getSystemMapping() {
		return systemMapping;
	}

	public void setSystemMapping(UUID systemMapping) {
		this.systemMapping = systemMapping;
	}

	public UUID getFormDefinition() {
		return formDefinition;
	}

	public void setFormDefinition(UUID formDefinition) {
		this.formDefinition = formDefinition;
	}

	@Override
	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	@Override
	public String getExternalId() {
		return externalId;
	}
}
