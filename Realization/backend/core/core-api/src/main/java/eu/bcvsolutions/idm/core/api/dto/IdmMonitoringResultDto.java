package eu.bcvsolutions.idm.core.api.dto;

import java.util.UUID;

import org.springframework.hateoas.core.Relation;

import eu.bcvsolutions.idm.core.notification.api.domain.NotificationLevel;

/**
 * Monitoring DTO
 *
 * @author Vít Švanda
 * @since 10.4.0
 * @deprecated @since 11.1.0 use {@link eu.bcvsolutions.idm.core.monitoring.api.dto.IdmMonitoringResultDto}
 */
@Deprecated(since = "11.1.0")
@Relation(collectionRelation = "monitorings")
public class IdmMonitoringResultDto extends AbstractDto {

	private static final long serialVersionUID = 1L;
	//
	private String type;
	private int order;
	private NotificationLevel level;
	private String module;
	private String value;
	private String name;
	private String threshold;
	private UUID dtoId;
	private String dtoType;
	private BaseDto dto;
	private OperationResultDto result;

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public NotificationLevel getLevel() {
		return level;
	}

	public void setLevel(NotificationLevel level) {
		this.level = level;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getThreshold() {
		return threshold;
	}

	public void setThreshold(String threshold) {
		this.threshold = threshold;
	}

	public String getModule() {
		return module;
	}

	public void setModule(String module) {
		this.module = module;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public UUID getDtoId() {
		return dtoId;
	}

	public void setDtoId(UUID dtoId) {
		this.dtoId = dtoId;
	}

	public String getDtoType() {
		return dtoType;
	}

	public void setDtoType(String dtoType) {
		this.dtoType = dtoType;
	}

	public BaseDto getDto() {
		return dto;
	}

	public void setDto(BaseDto dto) {
		this.dto = dto;
	}

	public OperationResultDto getResult() {
		return result;
	}

	public void setResult(OperationResultDto result) {
		this.result = result;
	}

}
