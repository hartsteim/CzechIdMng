package eu.bcvsolutions.idm.core.monitoring.api.service;

import java.util.List;

import eu.bcvsolutions.idm.core.api.service.IdmCacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import eu.bcvsolutions.idm.core.api.CoreModule;
import eu.bcvsolutions.idm.core.api.dto.filter.PermissionContext;
import eu.bcvsolutions.idm.core.api.exception.ForbiddenEntityException;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.monitoring.api.dto.IdmMonitoringDto;
import eu.bcvsolutions.idm.core.monitoring.api.dto.IdmMonitoringResultDto;
import eu.bcvsolutions.idm.core.monitoring.api.dto.MonitoringEvaluatorDto;
import eu.bcvsolutions.idm.core.monitoring.api.dto.filter.IdmMonitoringFilter;
import eu.bcvsolutions.idm.core.monitoring.api.dto.filter.IdmMonitoringResultFilter;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;

/**
 * Monitoring manager.
 * 
 * @author Radek Tomiška
 * @since 11.1.0
 */
public interface MonitoringManager {
	
	/**
	 * Last monitoring results - results are evaluated on different instances, so synchronized cache is needed.
	 */
	String LAST_RESULT_CACHE_NAME = IdmCacheManager.getCacheName(CoreModule.MODULE_ID, "monitoring-last-result-cache");
	
	/**
	 * Init monitoring manager - load last results into cache etc.
	 * 
	 * @since 11.2.1 called manually after application is fully initialized.
	 */
	void init();

	/**
	 * Execute monitoring by publishing entity event.
	 * 
	 * @param monitoring monitoring configuration
	 * @return monitoring result
	 */
	IdmMonitoringResultDto execute(IdmMonitoringDto monitoring, BasePermission... permission);
	
	/**
	 * Evaluate ~ process monitoring.
	 * Lookout: Use {@link #execute(IdmMonitoringDto, BasePermission...)} instead to call all registered processors
	 * 
	 * @param monitoring monitoring configuration
	 * @return monitoring result
	 */
	IdmMonitoringResultDto evaluate(IdmMonitoringDto monitoring);
	
	/**
	 * Returns supported evaluators definitions.
	 * 
	 * @return
	 */
	List<MonitoringEvaluatorDto> getSupportedEvaluators();
	
	/**
	 * Get congured monitoring evaluators by given filter.
	 * 
	 * @see IdmMonitoringFilter
	 * @param filter
	 * @param pageable
	 * @param permission base permissions to evaluate (AND) 
	 * @return states
	 * @throws ForbiddenEntityException if authorization policies doesn't met
	 * @since 11.2.0
	 */
	Page<IdmMonitoringDto> findMonitorings(IdmMonitoringFilter filter, Pageable pageable, BasePermission... permission);
	
	/**
	 * Returns last monitoring results.
	 * Never throws {@link ForbiddenEntityException} - returning available dtos by given permissions (AND).
	 * 
	 * @param filter - level property is supported only!
	 * @param pageable - sort is not supported - results are sorted by order
	 * @param permission base permissions to evaluate (AND / OR by {@link PermissionContext})
	 * @return
	 */
	Page<IdmMonitoringResultDto> getLastResults(IdmMonitoringResultFilter filter, Pageable pageable, BasePermission... permission);
	
	/**
	 * Initialize form instance for configured evaluator properties.
	 * Returns {@code null}, if no eav form instance is required for evaluator properties.
	 * 
	 * @param monitoringResult monitoring result
	 * @return form instance or {@code null}
	 * @since 11.2.0
	 */
	IdmFormInstanceDto getEvaluatorFormInstance(IdmMonitoringResultDto monitoringResult);
	
	/**
	 * Initialize form instance for configured evaluator properties.
	 * Returns {@code null}, if no eav form instance is required for evaluator properties.
	 * 
	 * @param monitoringResult monitoring
	 * @return form instance or {@code null}
	 * @since 11.2.0
	 */
	IdmFormInstanceDto getEvaluatorFormInstance(IdmMonitoringDto monitoring);
}
