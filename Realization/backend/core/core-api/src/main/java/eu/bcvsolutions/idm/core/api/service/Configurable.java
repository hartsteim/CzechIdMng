package eu.bcvsolutions.idm.core.api.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.utils.AutowireHelper;
import eu.bcvsolutions.idm.core.api.utils.EntityUtils;
import eu.bcvsolutions.idm.core.api.utils.SpinalCase;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;

/**
 * Configurable object by {@link ConfigurationService}.
 * 
 * @author Radek Tomiška
 *
 */
public interface Configurable {

	/**
	 * Configuration service for accessing to configured properties
	 * 
	 * @return
	 */
	ConfigurationService getConfigurationService();
	
	/**
	 * Configurable type identifier - e.g. "processor", "filter"
	 * @return
	 */
	String getConfigurableType();
	
	/**
	 * Module identifier
	 * 
	 * @return
	 */
	default String getModule() {
		return EntityUtils.getModule(this.getClass());
	}
	
	/**
	 * Unique (module scope) configurable object identifier. Its used in configuration key etc.
	 * 
	 * @return
	 */
	default String getName() {
		String name = this.getClass().getCanonicalName();
		if (StringUtils.isEmpty(name)) {
			// TODO: inline classes ...
			return null;
		}
		return SpinalCase.format(name);
	}
	
	/**
	 * Configurable object description
	 *   
	 * @return Bean description
	 */
	default String getDescription() {
		return AutowireHelper.getBeanDescription(this.getClass());
	}
	
	/**
	 * Returns true, when configurable object could be disabled
	 * 
	 * @return
	 */
	default boolean isDisableable() {
		return true;
	}
	
	/**
	 * Returns true, when all configuration properties are secured. False = public.
	 * 
	 * @see #getConfigurationPrefix() 
	 * @return
	 */
	default boolean isSecured() {
		return true;
	}
	
	/**
	 * Returns true, when configurable object is disabled
	 * 
	 * @return
	 */
	default boolean isDisabled() {
		// check configurable could be disabled
		if (!isDisableable()) {
			return false;
		}
		// check for processor is enabled, if configuration service is given
		if (getConfigurationService() != null) {
			return !getConfigurationService().getBooleanValue(
					getConfigurationPrefix()
					+ ConfigurationService.PROPERTY_SEPARATOR
					+ ConfigurationService.PROPERTY_ENABLED, !isDefaultDisabled());
		}
		// enabled by default
		return false;
	}
	
	default boolean isDefaultDisabled() {
		return false;
	}
	
	/**
	 * Returns prefix to configuration for this configurable object. 
	 * Under this prefix could be found all configurable object's properties.
	 * 
	 * @return 
	 */
	default String getConfigurationPrefix() {
		return (isSecured() ? ConfigurationService.IDM_PRIVATE_PROPERTY_PREFIX : ConfigurationService.IDM_PUBLIC_PROPERTY_PREFIX)
				+ getModule()
				+ ConfigurationService.PROPERTY_SEPARATOR
				+ getConfigurableType()
				+ ConfigurationService.PROPERTY_SEPARATOR
				+ getName();
	}
	
	/**
	 * Returns configuration property names for this configurable object
	 * 
	 * @return
	 */
	default List<String> getPropertyNames() {
		List<String> propertyNames = new ArrayList<>();
		propertyNames.add(ConfigurationService.PROPERTY_ENABLED);
		propertyNames.add(ConfigurationService.PROPERTY_ORDER);
		//
		return propertyNames;
	}
	
	/**
	 * Returns configuration properties for this configurable object (all properties by configuration prefix)
	 * 
	 * @see {@link #getConfigurationPrefix()}
	 * @see {@link #getPropertyNames()}
	 * @see ConfigurationService
	 * 
	 * @return
	 */
	default ConfigurationMap getConfigurationMap() {
		ConfigurationMap configs = new ConfigurationMap();
		if (getConfigurationService() == null) {
			return configs;
		}
		for (String propertyName : getPropertyNames()) {
			configs.put(propertyName, getConfigurationValue(propertyName));
		}
		return configs;
	}
	
	/**
	 * Returns full property name with prefix in configuration
	 * 
	 * @param propertyName without prefix
	 * @return
	 */
	default String getConfigurationPropertyName(String propertyName) {
		Assert.hasLength(propertyName, "Property name is required to get configuration property.");
		//
		return getConfigurationPrefix()
				+ ConfigurationService.PROPERTY_SEPARATOR
				+ propertyName;
	}
	
	/**
	 * Returns property name without configuration prefix
	 * 
	 * @param configurationPropertyName
	 * @return
	 */
	default String getPropertyName(String configurationPropertyName) {
		Assert.hasLength(configurationPropertyName, "Configuration property is required to get property name.");
		//
		return configurationPropertyName.replaceFirst(getConfigurationPrefix(), "");
	}
	
	/**
	 * Returns configured value for given propertyName. If no value for given key is configured, then returns {@code null}.
	 * 
	 * @param propertyName
	 * @return
	 */
	default String getConfigurationValue(String propertyName) {
		return getConfigurationValue(propertyName, null);
	}
	
	/**
	 * Returns configured boolean value for given propertyName. If no value for given key is configured, then returns {@code null}.
	 * 
	 * @param propertyName
	 * @return
	 */
	default Boolean getConfigurationBooleanValue(String propertyName) {
		String value = getConfigurationValue(propertyName);
		return value == null ? null : Boolean.valueOf(value);
	}
	
	/**
	 * Returns configured value for given propertyName. If no value for given key is configured, then returns given defaultValue.
	 * 
	 * @param propertyName
	 * @param defaultValue
	 * @return
	 */
	default String getConfigurationValue(String propertyName, String defaultValue) {
		if (getConfigurationService() == null) {
			return null;
		}
		return getConfigurationService().getValue(
				getConfigurationPropertyName(propertyName), 
				defaultValue);
	}
	
	/**
	 * Returns configured boolean value for given propertyName. If no value for given key is configured, then returns given defaultValue.
	 * 
	 * @param propertyName
	 * @param defaultValue
	 * @return
	 */
	default boolean getConfigurationBooleanValue(String key, boolean defaultValue) {
		Boolean value = getConfigurationBooleanValue(key);
		return value == null ? defaultValue : value;
	}
	
	/**
	 * Returns configured Integer value for given propertyName. If no value for given key is configured, then returns null value.
	 * 
	 * @param key
	 * @return
	 */
	default Integer getConfigurationIntegerValue(String key) {
		String value = getConfigurationValue(key);
		return value == null ? null : Integer.valueOf(value);
	}
	
	/**
	 * Returns configured Long value for given propertyName. If no value for given key is configured, then returns null value.
	 * 
	 * @param key
	 * @return
	 */
	default Long getConfigurationLongValue(String key) {
		String value = getConfigurationValue(key);
		return value == null ? null : Long.valueOf(value);
	}
	
	/**
	 * Returns dform definition for this configurable object by defined property names.
	 * Override, when form definition should be persisted.
	 * 
	 * @return
	 */
	default IdmFormDefinitionDto getFormDefinition() {
		IdmFormDefinitionDto formDefinition = new IdmFormDefinitionDto();
		formDefinition.setType(getConfigurableType());
		String configurableName = getName();
		formDefinition.setCode(configurableName);
		formDefinition.setName(configurableName);
		formDefinition.setModule(getModule());
		formDefinition.setDescription(getDescription());
		formDefinition.setFormAttributes(getFormAttributes());
		//
		return formDefinition;
	}
	
	/**
	 * Initialize form instance for configured properties.
	 * Returns {@code null}, if no eav form instance is required for properties.
	 * Returns {@code null} by default, override if needed.
	 * 
	 * @param evaluatorProperties configured properties
	 * @return form instance
	 * @since 11.2.0
	 * @see Configurable#getFormDefinition()
	 */
	default IdmFormInstanceDto getFormInstance(ConfigurationMap properties) {
		return null;
	}
	
	/**
	 * Returns form definition attributes.
	 * Override, when properties will be defined with persistentType, face, etc.
	 * 
	 * @return
	 */
	default List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> attributes = new ArrayList<>();
		List<String> propertyNames = getPropertyNames();
		//
		for (short index = 0; index < propertyNames.size(); index ++) {
			String propertyName = propertyNames.get(index);
			if (propertyName.equals(ConfigurationService.PROPERTY_ENABLED)
					|| propertyName.equals(ConfigurationService.PROPERTY_ORDER)) {
				// internal base props
				continue;
			}
			IdmFormAttributeDto formAttribute = new IdmFormAttributeDto(propertyName);
			formAttribute.setSeq(index);
			attributes.add(formAttribute);
		}
		//
		return attributes;
	}
}
