package eu.bcvsolutions.idm.core.scheduler.rest.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.Resources;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import eu.bcvsolutions.idm.core.api.config.swagger.SwaggerConfig;
import eu.bcvsolutions.idm.core.api.rest.BaseController;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.utils.ParameterConverter;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.scheduler.api.dto.Task;
import eu.bcvsolutions.idm.core.scheduler.api.dto.filter.TaskFilter;
import eu.bcvsolutions.idm.core.scheduler.api.service.SchedulerManager;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;

@RestController
@RequestMapping(UpcomingTasksController.BASE_PATH + "/upcoming-tasks")
@Api(value = UpcomingTasksController.TAG, description = "Upcoming tasks for task dashboard", tags = { UpcomingTasksController.TAG })
public class UpcomingTasksController implements BaseController {

	protected static final String TAG = "Upcoming tasks";
	private SchedulerManager schedulerService;
	private LookupService lookupService;
	private PagedResourcesAssembler<Object> pagedResourcesAssembler;
	private ParameterConverter parameterConverter;

	public UpcomingTasksController(SchedulerManager schedulerService, LookupService lookupService, PagedResourcesAssembler<Object> pagedResourcesAssembler) {
		this.schedulerService = schedulerService;
		this.lookupService = lookupService;
		this.pagedResourcesAssembler = pagedResourcesAssembler;
		this.parameterConverter = new ParameterConverter(lookupService);
	}

	/**
	 * Finds upcoming scheduled tasks
	 *
	 * @return upcoming tasks
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@ResponseBody
	@RequestMapping(method = RequestMethod.GET)
	@PreAuthorize("hasAuthority('" + CoreGroupPermission.SCHEDULER_READ + "')")
	@ApiOperation(
			value = "Search upcoming scheduled tasks",
			nickname = "searchUpcomingSchedulerTasks",
			tags={ UpcomingTasksController.TAG },
			authorizations = {
					@Authorization(value = SwaggerConfig.AUTHENTICATION_BASIC, scopes = {
							@AuthorizationScope(scope = CoreGroupPermission.SCHEDULER_READ, description = "") }),
					@Authorization(value = SwaggerConfig.AUTHENTICATION_CIDMST, scopes = {
							@AuthorizationScope(scope = CoreGroupPermission.SCHEDULER_READ, description = "") })
			})
	@ApiImplicitParams({
			@ApiImplicitParam(name = "page", dataType = "string", paramType = "query",
					value = "Results page you want to retrieve (0..N)"),
			@ApiImplicitParam(name = "size", dataType = "string", paramType = "query",
					value = "Number of records per page."),
			@ApiImplicitParam(name = "nextFireTimesLimitSeconds", dataType = "string", paramType = "query",
					value = "Limit number of seconds in the future for cron trigger"),
			@ApiImplicitParam(name = "nextFireTimesLimitCount", dataType = "string", paramType = "query",
					value = "Limit size of nextFireTimes list"),
	})
	public Resources<?> findUpcomingTasks(
			@RequestParam(required = false) MultiValueMap<String, Object> parameters,
			@PageableDefault Pageable pageable) {
		Page tasks = schedulerService.findUpcomingTasks(toFilter(parameters), pageable);
		//
		return pageToResources(tasks, Task.class);
	}

	protected Resources<?> pageToResources(Page<Object> page, Class<?> domainType) {
		if (page.getContent().isEmpty()) {
			return pagedResourcesAssembler.toEmptyResource(page, domainType);
		}
		return pagedResourcesAssembler.toResource(page);
	}

	private ParameterConverter getParameterConverter() {
		return parameterConverter;
	}

	private TaskFilter toFilter(MultiValueMap<String, Object> parameters) {
		return new TaskFilter(parameters, getParameterConverter());
	}
}
