package eu.bcvsolutions.idm.core.eav.processor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.dto.FormableDto;
import eu.bcvsolutions.idm.core.api.event.CoreEvent;
import eu.bcvsolutions.idm.core.api.event.CoreEvent.CoreEventType;
import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;

/**
 * Persists formable entity's (owner's) prepared eav attribute values.
 * 
 * @author Radek Tomiška
 * @since 9.2.0
 */
@Component(FormableSaveProcessor.PROCESSOR_NAME)
@Description("Persists formable entity's (owner's) prepared eav attribute values.")
public class FormableSaveProcessor extends CoreEventProcessor<FormableDto> {
	
	public static final String PROCESSOR_NAME = "core-formable-save-processor";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FormableSaveProcessor.class);
	//
	@Autowired private FormService formService;
	@Autowired private EntityEventManager entityEventManager;
	@Autowired private LookupService lookupService;
	
	public FormableSaveProcessor() {
		super(CoreEventType.CREATE, CoreEventType.UPDATE); // ~ save dto
	}
	
	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	@Override
	public EventResult<FormableDto> process(EntityEvent<FormableDto> event) {
		FormableDto savedDto = event.getContent(); // saved dto
		//
		// save filled eavs as "PATCH" - access is evaluated before - check {@link #publish} method in {@link AbstractFormableService}
		// the same behavior as saving form values separately
		List<IdmFormInstanceDto> processedInstances = new ArrayList<>();
		savedDto.getEavs().forEach(formInstance -> {
			// find definition by id or code -  loaded form definition is needed (with attributes)
			IdmFormDefinitionDto formDefinition = null;
			if (formInstance.getFormDefinition().getId() == null) {
				formDefinition = formService.getDefinition(savedDto.getClass(), formInstance.getFormDefinition().getCode());
			} else {
				formDefinition = formInstance.getFormDefinition();
			}
			//
			if (formDefinition == null) {
				LOG.debug("Internal form definition [{}] cannot be saved", formInstance.getFormDefinition().getCode());
				// Prevent to loose internal form instance => just flow through without change.
				processedInstances.add(formInstance);
			} else {
				formInstance.setOwnerId(savedDto.getId());
				formInstance.setOwnerType(lookupService.getEntityClass(savedDto.getClass())); // entity owner type is used
				formInstance.setFormDefinition(formDefinition);
				//
				CoreEvent<IdmFormInstanceDto> formInstanceEvent = new CoreEvent<IdmFormInstanceDto>(CoreEventType.UPDATE, formInstance);
				// We don't need to propagate other "NOTIFY" event on all form instances (duplicate to owner event)
				formInstanceEvent.getProperties().put(EntityEventManager.EVENT_PROPERTY_SKIP_NOTIFY, Boolean.TRUE);
				// we don't need to evaluate access on values again - see above publish method
				processedInstances.add(entityEventManager.process(formInstanceEvent, event).getContent());
			}
		});
		// set processed instances and content into event => can be used for another processing
		savedDto.setEavs(processedInstances);
		event.setContent(savedDto);
		//
		return new DefaultEventResult<>(event, this);
	}
	
	@Override
	public int getOrder() {
		return 50; // after save, before automatic roles
	}
}
