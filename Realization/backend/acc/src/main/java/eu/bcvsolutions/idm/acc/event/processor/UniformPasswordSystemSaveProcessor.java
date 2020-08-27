package eu.bcvsolutions.idm.acc.event.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.acc.dto.AccUniformPasswordSystemDto;
import eu.bcvsolutions.idm.acc.event.UniformPasswordEvent.UniformPasswordEventType;
import eu.bcvsolutions.idm.acc.service.api.AccUniformPasswordSystemService;
import eu.bcvsolutions.idm.core.api.event.CoreEvent;
import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;

/**
 * Save {@link AccUniformPasswordSystemDto}
 *
 * @author Ondrej Kopr
 * @since 10.5.0
 *
 */
@Component("accUniformPasswordSystemSaveProcessor")
@Description("Save newly created or update exists uniform password system.")
public class UniformPasswordSystemSaveProcessor extends CoreEventProcessor<AccUniformPasswordSystemDto> {

	private static final String PROCESSOR_NAME = "password-filter-save-processor";

	@Autowired
	private AccUniformPasswordSystemService uniformPasswordSystemService;

	public UniformPasswordSystemSaveProcessor() {
		super(UniformPasswordEventType.CREATE, UniformPasswordEventType.UPDATE);
	}

	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	@Override
	public EventResult<AccUniformPasswordSystemDto> process(EntityEvent<AccUniformPasswordSystemDto> event) {
		AccUniformPasswordSystemDto dto = event.getContent();

		dto = uniformPasswordSystemService.saveInternal(dto);
		event.setContent(dto);

		return new DefaultEventResult<>(event, this);
	}

	@Override
	public int getOrder() {
		return CoreEvent.DEFAULT_ORDER;
	}

	@Override
	public boolean isDisableable() {
		return false;
	}

}
