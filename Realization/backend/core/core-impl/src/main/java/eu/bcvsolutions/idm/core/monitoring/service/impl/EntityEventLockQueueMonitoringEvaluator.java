package eu.bcvsolutions.idm.core.monitoring.service.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.OperationResultDto;
import eu.bcvsolutions.idm.core.api.dto.ResultModel;
import eu.bcvsolutions.idm.core.api.event.EntityEventLock;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.BaseDtoService;
import eu.bcvsolutions.idm.core.api.service.ReadDtoService;
import eu.bcvsolutions.idm.core.eav.api.domain.BaseFaceType;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.monitoring.api.dto.IdmMonitoringDto;
import eu.bcvsolutions.idm.core.monitoring.api.dto.IdmMonitoringResultDto;
import eu.bcvsolutions.idm.core.monitoring.api.service.AbstractMonitoringEvaluator;
import eu.bcvsolutions.idm.core.notification.api.domain.NotificationLevel;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import javax.persistence.Table;
import java.util.List;

/**
 * Warning about number of threads accessing {@link EntityEventLock#lock()}
 *
 * @author Peter Štrunc <peter.strunc@bcvsolutions.eu>
 * @since 12.1.0
 */
@Component(EntityEventLockQueueMonitoringEvaluator.NAME)
@Description("Warning about too many records in database table.")
public class EntityEventLockQueueMonitoringEvaluator extends AbstractMonitoringEvaluator {
	
	public static final String NAME = "core-entity-event-lock-thread-queue-monitoring-evaluator";
	public static final String PARAMETER_THRESHOLD = "threshold";
	public static final long DEFAULT_THRESHOLD = 5l;
	//
	@Autowired private EntityEventLock LOCK;
	
	@Override
	public String getName() {
		return NAME;
	}
	
	@Override
	public IdmMonitoringResultDto evaluate(IdmMonitoringDto monitoring) {
		long treshold = getParameterConverter().toLong(monitoring.getEvaluatorProperties(), PARAMETER_THRESHOLD, DEFAULT_THRESHOLD);
		long count = LOCK.getQueueLength();
		ResultModel resultModel = new DefaultResultModel(
				CoreResultCode.MONITORING_EVENT_LOCK_QUEUE,
				ImmutableMap.of(
						"count", Long.toString(count)
				)
		);
		IdmMonitoringResultDto result = new IdmMonitoringResultDto();
		result.setValue(Long.toString(count));
		result.setResult(new OperationResultDto.Builder(OperationState.EXECUTED).setModel(resultModel).build());
		if (treshold < count) {
			result.setLevel(NotificationLevel.WARNING);
		} 
		//
		return result;
	}
	
	@Override
	public List<String> getPropertyNames() {
		List<String> parameters = super.getPropertyNames();
		parameters.add(PARAMETER_THRESHOLD);
		//
		return parameters;
	}
	
	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		IdmFormAttributeDto treshold = new IdmFormAttributeDto(PARAMETER_THRESHOLD, PARAMETER_THRESHOLD, PersistentType.LONG);
		treshold.setDefaultValue(Long.toString(DEFAULT_THRESHOLD));
		treshold.setRequired(true);
		//
		return Lists.newArrayList(
				treshold
		);
	}
}
