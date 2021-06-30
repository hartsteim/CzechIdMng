package eu.bcvsolutions.idm.core.eav.processor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.event.CoreEvent.CoreEventType;
import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.exception.InvalidFormException;
import eu.bcvsolutions.idm.core.api.service.ContractSliceManager;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.InvalidFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.event.processor.FormInstanceProcessor;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;

/**
 * validate form instance (eav attributes) before save.
 * 
 * @author Radek Tomiška
 * @since 9.4.0
 */
@Component(FormInstanceValidateProcessor.PROCESSOR_NAME)
@Description("Validate form instance.")
public class FormInstanceValidateProcessor 
		extends CoreEventProcessor<IdmFormInstanceDto>
		implements FormInstanceProcessor {
	
	public static final String PROCESSOR_NAME = "core-form-instance-validate-processor";
	//
	@Autowired private FormService formService;
	
	public FormInstanceValidateProcessor() {
		super(CoreEventType.UPDATE); // eavs are updated for CUD
	}
	
	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	@Override
	public EventResult<IdmFormInstanceDto> process(EntityEvent<IdmFormInstanceDto> event) {
		IdmFormInstanceDto formInstance = event.getContent();
		Assert.notNull(formInstance.getFormDefinition(), "Form definition is required for form instance validation.");
		//
		IdmFormDefinitionDto formDefinition = formService.getDefinition(formInstance.getFormDefinition().getId());
		Assert.notNull(formDefinition, "Form definition is required for form instance validation.");
		//
		Map<String, Serializable> properties = event.getProperties();
		//
		// get distinct attributes from the sent values
		// PATCH is used - only sent attributes are validated
		Set<IdmFormAttributeDto> sentAttributes = formInstance
				.getValues()
				.stream()
				.map(IdmFormValueDto::getFormAttribute)
				.map(attributeId -> {
					IdmFormAttributeDto mappedAttribute = formInstance.getFormDefinition().getMappedAttribute(attributeId);
					if (mappedAttribute != null) {
						return mappedAttribute;
					}
					return formDefinition.getMappedAttribute(attributeId);
				})
				.collect(Collectors.toSet());
		// only sent attributes in definition and instance
		formDefinition.setFormAttributes(Lists.newArrayList(sentAttributes));
		formInstance.setFormDefinition(formDefinition);
		// validate
		List<InvalidFormAttributeDto> errors = formService.validate(formInstance);
		//skip <required> validation if contract update is performed from time slice
		if (getBooleanProperty(ContractSliceManager.SKIP_CHECK_FOR_SLICES, properties)) {
			errors = errors.stream().filter(error -> {
				return !error.isMissingValue();
			}).collect(Collectors.toList());
		}
		if (!errors.isEmpty()) {
			throw new InvalidFormException(errors);
		}
		//
		return new DefaultEventResult<>(event, this);
	}

	@Override
	public boolean conditional(EntityEvent<IdmFormInstanceDto> event) {
		// Check if property for skip validation is sets to true.
		if(getBooleanProperty(FormService.SKIP_EAV_VALIDATION, event.getProperties())){
			return false;
		}
		return super.conditional(event);
	}

	/**
	 * Before validation
	 */
	@Override
	public int getOrder() {
		return -50;
	}
}
