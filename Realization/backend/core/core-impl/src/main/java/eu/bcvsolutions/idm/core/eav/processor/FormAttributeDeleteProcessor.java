package eu.bcvsolutions.idm.core.eav.processor;

import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;

import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Description;
import org.springframework.plugin.core.OrderAwarePluginRegistry;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmFormValueFilter;
import eu.bcvsolutions.idm.core.eav.api.event.FormAttributeEvent.FormAttributeEventType;
import eu.bcvsolutions.idm.core.eav.api.event.processor.FormAttributeProcessor;
import eu.bcvsolutions.idm.core.eav.api.service.FormValueService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;

/**
 * Deletes form attribute - ensures referential integrity.
 * 
 * TODO: force delete of automatic role rules and role required parameters (LRT + recount required)?
 * 
 * @author Radek Tomiška
 * @since 9.4.0
 */
@Component(FormAttributeDeleteProcessor.PROCESSOR_NAME)
@Description("Deletes form attribute from repository.")
public class FormAttributeDeleteProcessor
		extends CoreEventProcessor<IdmFormAttributeDto> 
		implements FormAttributeProcessor {
	
	public static final String PROCESSOR_NAME = "core-form-attribute-delete-processor";
	//
	private final PluginRegistry<FormValueService<?>, Class<?>> formValueServices;
	//
	@Autowired private IdmFormAttributeService service;
	@Qualifier("entityManager")
	@Autowired private EntityManager entityManager;
	
	@Autowired
	public FormAttributeDeleteProcessor(List<? extends FormValueService<?>> formValueServices) {
		super(FormAttributeEventType.DELETE);
		//
		Assert.notNull(formValueServices, "Service is required.");
		//
		this.formValueServices = OrderAwarePluginRegistry.create(formValueServices);
	}
	
	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	
	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public EventResult<IdmFormAttributeDto> process(EntityEvent<IdmFormAttributeDto> event) {
		IdmFormAttributeDto formAttribute = event.getContent();
		UUID formAttributeId = formAttribute.getId();
		Assert.notNull(formAttributeId, "Form attribute id is required.");
		//
		boolean forceDelete = getBooleanProperty(PROPERTY_FORCE_DELETE, event.getProperties());
		if (forceDelete) {
			IdmFormValueFilter filter = new IdmFormValueFilter();
			filter.setAttributeId(formAttributeId);
			formValueServices.getPlugins().forEach(formValueService -> {
				formValueService.find(filter, null).getContent().forEach(formValue -> {
					formValueService.delete((IdmFormValueDto) formValue);
					//
					clearSession();
				});
			});
		}
		//		
		service.deleteInternal(formAttribute);
		//
		return new DefaultEventResult<>(event, this);
	}
	
	private void clearSession() {
		// flush and clear session - manager can have a lot of subordinates
		if (getHibernateSession().isOpen()) {
			getHibernateSession().flush();
			getHibernateSession().clear();
		}
	}
	
	private Session getHibernateSession() {
		return (Session) this.entityManager.getDelegate();
	}
}