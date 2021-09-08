package eu.bcvsolutions.idm.acc.domain.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import java.util.stream.Collectors;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.ic.api.IcConfigurationProperty;
import eu.bcvsolutions.idm.ic.impl.IcConfigurationPropertyImpl;

/**
 * Connector property type vs. eav type conversion
 * 
 * @author Radek Tomiška
 *
 */
public abstract class AbstractFormPropertyConverter implements FormPropertyConverter {
	
	/**
	 * Supported Connector property types 
	 * 
	 * @return
	 */
	public abstract Set<String> getConnectorPropertyTypes();
	
	/**
	 * Returns true, if connector property supports multiple values
	 *
	 */
	public boolean isMultiple() {
		return false;
	}
	
	@Override
	public IdmFormAttributeDto toFormAttribute(IcConfigurationProperty propertyConfiguration) {
		Assert.notNull(propertyConfiguration, "Property configuration is required");
		//
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		attribute.setCode(propertyConfiguration.getName());
		attribute.setName(propertyConfiguration.getDisplayName());
		attribute.setDescription(propertyConfiguration.getHelpMessage());
		attribute.setPersistentType(getFormPropertyType());
		attribute.setConfidential(propertyConfiguration.isConfidential());
		attribute.setRequired(propertyConfiguration.isRequired());
		attribute.setMultiple(isMultiple());
		attribute.setDefaultValue(convertDefaultValue(propertyConfiguration));
		attribute.setFaceType(propertyConfiguration.getFace());
		return attribute;
	}

	@Override
	public IcConfigurationProperty toConnectorProperty(IcConfigurationProperty propertyConfiguration, List<IdmFormValueDto> formValues) {
		Assert.notNull(propertyConfiguration, "Property configuration is required");
		//
		IcConfigurationPropertyImpl property = new IcConfigurationPropertyImpl();
		property.setName(propertyConfiguration.getName());
		property.setValue(toConnectorPropertyValue(propertyConfiguration, formValues));
		return property;
	}
	
	@Override
	public String convertDefaultValue(IcConfigurationProperty propertyConfiguration) {
		Assert.notNull(propertyConfiguration, "Property configuration is required");
		//
		if (propertyConfiguration.getValue() == null) {
			return null;
		}		
		if (!isMultiple()) {
			return propertyConfiguration.getValue().toString();
		}		
		StringBuilder result = new StringBuilder();
		// arrays only
		// override for other data types
		Object[] values = (Object[]) propertyConfiguration.getValue();
		for (Object singleValue : values) {
			if (result.length() > 0) {
				result.append(System.getProperty("line.separator"));
			}
			result.append(singleValue);
		}
		return result.toString();
	}
	
	/**
	 * Converts eav form values to connector property value
	 * 
	 * @param propertyConfiguration
	 * @param formValues
	 * @return
	 */
	protected Object toConnectorPropertyValue(IcConfigurationProperty propertyConfiguration, List<IdmFormValueDto> formValues) {
		if (formValues == null || formValues.isEmpty()) {
			return null;
		}
		if (isMultiple()) {
			return convertMultipleConnectorPropertyValue(propertyConfiguration, formValues);
		}		
		// single value
		return convertSingleConnectorPropertyValue(propertyConfiguration, formValues.get(0));
	}
	
	/**
	 * Converts eav form values to connector property values (multi)
	 * 
	 * @param propertyConfiguration
	 * @param formValues
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected Object convertMultipleConnectorPropertyValue(IcConfigurationProperty propertyConfiguration, List<IdmFormValueDto> formValues) {
		Object value = null;
		List valueList = new ArrayList<>(formValues.size());
		for (IdmFormValueDto formValue : formValues) {
			valueList.add(convertSingleConnectorPropertyValue(propertyConfiguration, formValue));
		}
		if (!valueList.isEmpty()) {
			// arrays only
			// override for other data types
			if (PersistentType.TEXT == getFormPropertyType()) {
				// We need to trim a value (default value can contains /r).
				List convertedList = (List) valueList.stream().map(itemValue -> {
					if (itemValue instanceof String) {
						return ((String) itemValue).trim();
					}
					return itemValue;
				}).collect(Collectors.toList());
				value = convertedList.toArray(new String[]{});
			}else if(PersistentType.UUID == getFormPropertyType()) {
				value = valueList.toArray(new UUID[]{});
			} else {
				value = valueList.toArray();
			}
		}
		return value;
	}
	
	/**
	 * Returns single connector property value from given eav value
	 * 
	 * @param propertyConfiguration connector property definition
	 * @param formValue
	 * @return
	 */
	protected Object convertSingleConnectorPropertyValue(IcConfigurationProperty propertyConfiguration, IdmFormValueDto formValue) {	
		if (formValue == null) {
			return null;
		}
		return formValue.getValue();
	}

	@Override
	public boolean supports(IcConfigurationProperty delimiter) {
		Assert.notNull(delimiter, "Delimiter to check plugin is supported is required");
		//
		return getConnectorPropertyTypes().contains(delimiter.getType());
	}
	
	@Override
	public boolean supports(String configurationPropertyType) {
		Assert.notNull(configurationPropertyType, "Configuration property type is required.");
		//
		return getConnectorPropertyTypes().contains(configurationPropertyType);
	}
}

