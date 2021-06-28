package eu.bcvsolutions.idm.core.monitoring.event.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.monitoring.api.dto.IdmMonitoringDto;
import eu.bcvsolutions.idm.core.monitoring.api.event.MonitoringEvent.MonitoringEventType;
import eu.bcvsolutions.idm.core.monitoring.api.event.processor.MonitoringProcessor;
import eu.bcvsolutions.idm.core.monitoring.api.service.MonitoringManager;

/**
 * Execute configured monitoring evaluator.
 * 
 * @author Radek Tomiška
 * @since 11.1.0
 */
@Component
@Description("Execute configured monitoring evaluator.")
public class MonitoringExecuteProcessor
		extends CoreEventProcessor<IdmMonitoringDto>
		implements MonitoringProcessor  {
	
	public static final String PROCESSOR_NAME = "monitoring-execute-processor";
	//
	@Autowired private MonitoringManager monitoringManager;
	
	public MonitoringExecuteProcessor() {
		super(MonitoringEventType.EXECUTE);
	}
	
	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	@Override
	public EventResult<IdmMonitoringDto> process(EntityEvent<IdmMonitoringDto> event) {
		monitoringManager.evaluate(event.getContent());
		//
		return new DefaultEventResult<>(event, this);
	}
}
