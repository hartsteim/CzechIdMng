package eu.bcvsolutions.idm.acc.event.processor.provisioning;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.acc.AccModuleDescriptor;
import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.domain.ProvisioningEventType;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningOperationDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningOperationService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.ResultModel;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.event.AbstractEntityEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmMessageDto;
import eu.bcvsolutions.idm.core.notification.api.service.NotificationManager;
import eu.bcvsolutions.idm.core.security.api.domain.Enabled;

/**
 * Readonly system provisioning - only saves provisioning operations
 * 
 * @author Radek Tomiška
 *
 */
@Component
@Enabled(AccModuleDescriptor.MODULE_ID)
@Description("Checks readonly system before provisioning is called.")
public class ReadonlySystemProcessor extends AbstractEntityEventProcessor<SysProvisioningOperationDto> {
	
	public static final String PROCESSOR_NAME = "readonly-system-processor";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ReadonlySystemProcessor.class);
	private final NotificationManager notificationManager;
	private final SysProvisioningOperationService provisioningOperationService;
	private final SysSystemService systemService;
	
	@Autowired
	public ReadonlySystemProcessor(
			NotificationManager notificationManager,
			SysProvisioningOperationService provisioningOperationService,
			SysSystemService systemService) {
		super(ProvisioningEventType.CREATE, ProvisioningEventType.UPDATE, ProvisioningEventType.DELETE);
		//
		Assert.notNull(notificationManager, "Manager is required.");
		Assert.notNull(provisioningOperationService, "Service is required.");
		Assert.notNull(systemService, "Service is required.");
		//
		this.notificationManager = notificationManager;
		this.provisioningOperationService = provisioningOperationService;
		this.systemService = systemService;
	}
	
	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}
	
	@Override
	public boolean conditional(EntityEvent<SysProvisioningOperationDto> event) {
		return !event.getContent().isDryRun();
	}
	
	@Override
	public EventResult<SysProvisioningOperationDto> process(EntityEvent<SysProvisioningOperationDto> event) {
		SysProvisioningOperationDto provisioningOperation = event.getContent();
		SysSystemDto system = systemService.get(provisioningOperation.getSystem());
		boolean closed = false;
		if (system.isReadonly()) {
			String uid = provisioningOperationService.getByProvisioningOperation(provisioningOperation).getUid();
			ResultModel resultModel = new DefaultResultModel(AccResultCode.PROVISIONING_SYSTEM_READONLY, 
					ImmutableMap.of("name", uid, "system", system.getName()));
			provisioningOperation.setResult(new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(resultModel).build());
			//
			provisioningOperation = provisioningOperationService.saveOperation(provisioningOperation);
			//
			LOG.info(resultModel.toString());
			notificationManager.send(
					AccModuleDescriptor.TOPIC_PROVISIONING,
					new IdmMessageDto.Builder()
					.setModel(resultModel)
					.build());
			//
			closed = true;
		}
		// set back to event content
		event.setContent(provisioningOperation);
		return new DefaultEventResult<>(event, this, closed);
	}
	
	@Override
	public boolean isClosable() {
		return true;
	}

	@Override
	public int getOrder() {
		// after account attributes preparation and before realization
		return -500;
	}
}
