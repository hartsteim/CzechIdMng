package eu.bcvsolutions.idm.core.scheduler.api.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

import org.springframework.hateoas.core.Relation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.scheduler.api.service.SchedulableTaskExecutor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiModelProperty.AccessMode;

/**
 * Scheduled task with unique id 
 * * could be scheduled 
 * * could run manually through long running task service
 * 
 * Lookout: task definition and instance is mixed together!
 * 
 * @author Radek Tomiška
 */
@Relation(collectionRelation = "tasks")
public class Task implements BaseDto {

	private static final long serialVersionUID = 1L;
	//
	public static final String PROPERTY_TASK_TYPE = "taskType";
	public static final String PROPERTY_DESCRIPTION = "description";
	public static final String PROPERTY_NEXT_FIRE_TIME = "nextFireTime";
	public static final String PROPERTY_INSTANCE_ID = ConfigurationService.PROPERTY_INSTANCE_ID;
	//
	@JsonDeserialize(as = String.class)
	private String id; // quartz job name
	private String module;
	@NotEmpty
	private String instanceId;
	private Class<? extends SchedulableTaskExecutor<?>> taskType; // task executor class
	@Size(max = DefaultFieldLengths.DESCRIPTION)
	private String description;
	private boolean disabled; // task is disabled
	@ApiModelProperty(accessMode = AccessMode.READ_ONLY)
	private List<AbstractTaskTrigger> triggers;
	private Map<String, String> parameters;
	private IdmFormDefinitionDto formDefinition;
	private boolean supportsDryRun;
	private boolean recoverable;
	@ApiModelProperty(accessMode = AccessMode.READ_ONLY)
	private ZonedDateTime modified;
	@ApiModelProperty(accessMode = AccessMode.READ_ONLY)
	private List<Task> dependentTasks;
	
	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(Serializable id) {
		this.id = id == null ? null : id.toString();
	}
	
	public String getModule() {
		return module;
	}
	
	public void setModule(String module) {
		this.module = module;
	}
	
	public Class<? extends SchedulableTaskExecutor<?>> getTaskType() {
		return taskType;
	}
	
	public void setTaskType(Class<? extends SchedulableTaskExecutor<?>> taskType) {
		this.taskType = taskType;
	}

	public List<AbstractTaskTrigger> getTriggers() {
		if (triggers == null) {
			triggers = new ArrayList<>();
		}
		return triggers;
	}

	public void setTriggers(List<AbstractTaskTrigger> triggers) {
		this.triggers = triggers;
	}

	public Map<String, String> getParameters() {
		if (parameters == null) {
			parameters = new LinkedHashMap<String, String>();
		}
		return parameters;
	}
	
	public void setParameters(Map<String, String> parameters) {
		this.parameters = parameters;
	}
	
	/**
	 * Adds task parameter. Returs previous values associated
	 * with the provided key.
     *
	 * @param key
	 * @param value
	 * @return previous value
	 */
	public String addParameter(String key, String value) {
		return getParameters().put(key, value);
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public String getInstanceId() {
		return instanceId;
	}
	
	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}
	
	/**
	 * Given task supports dry run mode.
	 * 
	 * @param supportsDryRun
	 * @since 7.8.3
	 */
	public void setSupportsDryRun(boolean supportsDryRun) {
		this.supportsDryRun = supportsDryRun;
	}
	
	/**
	 * Returns true, if given task supports dry run mode.
	 * 
	 * @return
	 * @since 7.8.3
	 */
	public boolean isSupportsDryRun() {
		return supportsDryRun;
	}
	
	/**
	 * Eav form definition for configuration
	 * 
	 * @since 9.2.0
	 * @param formDefinition
	 */
	public void setFormDefinition(IdmFormDefinitionDto formDefinition) {
		this.formDefinition = formDefinition;
	}
	
	/**
	 * Eav form definition for configuration
	 * 
	 * @since 9.2.0
	 * @return
	 */
	public IdmFormDefinitionDto getFormDefinition() {
		return formDefinition;
	}
	
	/**
	 * Task is disabled.
	 * 
	 * @return
	 * @since 9.7.7
	 */
	public boolean isDisabled() {
		return disabled;
	}
	
	/**
	 * Task is disabled.
	 * 
	 * @param disabled
	 * @since 9.7.7
	 */
	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}
	
	/**
	 * Task can be executed repetitively without reschedule is needed.
	 * When task is canceled (e.g. by server is restarted), then task can be executed again.
	 * 
	 * @return true - LRT can be executed again.
	 * @since 10.2.0
	 */
	public boolean isRecoverable() {
		return recoverable;
	}
	
	/**
	 * Task can be executed repetitively without reschedule is needed.
	 * When task is canceled (e.g. by server is restarted), then task can be executed again.
	 * 
	 * @param recoverable  true - LRT can be executed again.
	 * @since 10.2.0
	 */
	public void setRecoverable(boolean recoverable) {
		this.recoverable = recoverable;
	}
	
	
	/**
	 * Last modified date.
	 * 
	 * @return date
	 * @since 10.7.0
	 */
	public ZonedDateTime getModified() {
		return modified;
	}

	/**
	 * Last modified date.
	 * 
	 * @param modified date
	 * @since 10.7.0
	 */
	public void setModified(ZonedDateTime modified) {
		this.modified = modified;
	}
	
	/**
	 * Task is persisted in external scheduler, we don't have task modifications in our hands => reload is needed all time.
	 * 
	 * @return true
	 * @since 10.8.0
	 */
	@JsonProperty(value = "_trimmed", access = Access.READ_ONLY)
	@ApiModelProperty(
			accessMode = AccessMode.READ_ONLY, 
			notes = "Task is persisted in external scheduler, we don't have task modifications in our hands => reload is needed all time."
	)
	public boolean isTrimmed() {
		return true;
	}

	public List<Task> getDependentTasks() {
		if (dependentTasks == null) {
			dependentTasks = new ArrayList<>();
		}
		return dependentTasks;
	}

	public void setDependentTasks(List<Task> dependentTasks) {
		this.dependentTasks = dependentTasks;
	}
}
