package eu.bcvsolutions.idm.core.model.event.processor.identity;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleValidRequestDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractSliceFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractSliceGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.event.CoreEvent;
import eu.bcvsolutions.idm.core.api.event.CoreEvent.CoreEventType;
import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.event.processor.IdentityProcessor;
import eu.bcvsolutions.idm.core.api.service.IdmContractGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmContractSliceGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmContractSliceService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleValidRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmPasswordHistoryService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.model.entity.IdmAuthorityChange;
import eu.bcvsolutions.idm.core.model.event.IdentityEvent.IdentityEventType;
import eu.bcvsolutions.idm.core.model.repository.IdmAuthorityChangeRepository;
import eu.bcvsolutions.idm.core.notification.repository.IdmNotificationRecipientRepository;

/**
 * Delete identity - ensures referential integrity
 * 
 * @author Radek Tomiška
 *
 */
@Component
@Description("Deletes identity - ensures core referential integrity.")
public class IdentityDeleteProcessor
		extends CoreEventProcessor<IdmIdentityDto> 
		implements IdentityProcessor {

	public static final String PROCESSOR_NAME = "identity-delete-processor";
	private final IdmIdentityService service;
	private final IdentityPasswordProcessor passwordProcessor;
	private final IdmRoleGuaranteeService roleGuaranteeService;
	private final IdmIdentityContractService identityContractService;
	private final IdmNotificationRecipientRepository notificationRecipientRepository;
	private final IdmRoleRequestService roleRequestService;
	private final IdmIdentityRoleValidRequestService identityRoleValidRequestService;
	private final IdmContractGuaranteeService contractGuaranteeService;
	private final IdmAuthorityChangeRepository authChangeRepository;
	private final IdmPasswordHistoryService passwordHistoryService;
	@Autowired
	private IdmContractSliceService contractSliceService;
	@Autowired
	private IdmContractSliceGuaranteeService contractSliceGuaranteeService;
	
	@Autowired
	public IdentityDeleteProcessor(
			IdmIdentityService service,
			IdentityPasswordProcessor passwordProcessor,
			IdmRoleGuaranteeService roleGuaranteeService,
			IdmIdentityContractService identityContractService,
			IdmNotificationRecipientRepository notificationRecipientRepository,
			IdmRoleRequestService roleRequestService,
			IdmIdentityRoleValidRequestService identityRoleValidRequestService,
			IdmAuthorityChangeRepository authChangeRepository,
			IdmContractGuaranteeService contractGuaranteeService,
			IdmPasswordHistoryService passwordHistoryService) {
		super(IdentityEventType.DELETE);
		//
		Assert.notNull(service);
		Assert.notNull(passwordProcessor);
		Assert.notNull(roleGuaranteeService);
		Assert.notNull(identityContractService);
		Assert.notNull(notificationRecipientRepository);
		Assert.notNull(roleRequestService);
		Assert.notNull(identityRoleValidRequestService);
		Assert.notNull(contractGuaranteeService);
		Assert.notNull(authChangeRepository);
		Assert.notNull(passwordHistoryService);
		//
		this.service = service;
		this.passwordProcessor = passwordProcessor;
		this.roleGuaranteeService = roleGuaranteeService;
		this.identityContractService = identityContractService;
		this.notificationRecipientRepository = notificationRecipientRepository;
		this.roleRequestService = roleRequestService;
		this.identityRoleValidRequestService = identityRoleValidRequestService;
		this.contractGuaranteeService = contractGuaranteeService;
		this.authChangeRepository = authChangeRepository;
		this.passwordHistoryService = passwordHistoryService;
	}
	
	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	@Override
	public EventResult<IdmIdentityDto> process(EntityEvent<IdmIdentityDto> event) {
		IdmIdentityDto identity = event.getContent();
		Assert.notNull(identity.getId(), "Identity ID is required!");
		
		// delete contract slices
		IdmContractSliceFilter sliceFilter = new IdmContractSliceFilter();
		sliceFilter.setIdentity(identity.getId());
		contractSliceService.find(sliceFilter, null).forEach(guarantee -> {
			contractSliceService.delete(guarantee);
		});
		// delete contract slice guarantees
		IdmContractSliceGuaranteeFilter sliceGuaranteeFilter = new IdmContractSliceGuaranteeFilter();
		sliceGuaranteeFilter.setGuaranteeId(identity.getId());
		contractSliceGuaranteeService.find(sliceGuaranteeFilter, null).forEach(guarantee -> {
			contractSliceGuaranteeService.delete(guarantee);
		});
		
		// contracts
		identityContractService.findAllByIdentity(identity.getId()).forEach(identityContract -> {
			// when identity is deleted, then HR processes has to be shipped (prevent to update deleted identity, when contract is removed)
			Map<String, Serializable> properties = new HashMap<>();
			properties.put(IdmIdentityContractService.SKIP_HR_PROCESSES, Boolean.TRUE);
			identityContractService.publish(new CoreEvent<>(CoreEventType.DELETE, identityContract, properties));
		});
		// delete contract guarantees
		IdmContractGuaranteeFilter filter = new IdmContractGuaranteeFilter();
		filter.setGuaranteeId(identity.getId());
		contractGuaranteeService.find(filter, null).forEach(guarantee -> {
			contractGuaranteeService.delete(guarantee);
		});
		// remove role guarantee
		IdmRoleGuaranteeFilter roleGuaranteeFilter = new IdmRoleGuaranteeFilter();
		roleGuaranteeFilter.setGuarantee(identity.getId());
		roleGuaranteeService.find(roleGuaranteeFilter, null).forEach(roleGuarantee -> {
			roleGuaranteeService.delete(roleGuarantee);
		});
		// remove password
		passwordProcessor.deletePassword(identity);
		// delete password history for identity
		passwordHistoryService.deleteAllByIdentity(identity.getId());
		// set to null all notification recipients - real recipient remains (email etc.)
		notificationRecipientRepository.clearIdentity(identity.getId());
		// remove authorities last changed relation
		deleteAuthorityChange(identity);
		
		// Delete all role requests where is this identity applicant
		IdmRoleRequestFilter roleRequestFilter = new IdmRoleRequestFilter();
		roleRequestFilter.setApplicantId(identity.getId());
		roleRequestService.find(roleRequestFilter, null).forEach(request ->{
			roleRequestService.delete(request);
		});
		// remove all IdentityRoleValidRequest for this identity
		List<IdmIdentityRoleValidRequestDto> validRequests = identityRoleValidRequestService.findAllValidRequestForIdentityId(identity.getId());
		identityRoleValidRequestService.deleteAll(validRequests);
		// deletes identity
		service.deleteInternal(identity);
		return new DefaultEventResult<>(event, this);
	}
	
	private void deleteAuthorityChange(IdmIdentityDto identity) {
		IdmAuthorityChange ac = authChangeRepository.findOneByIdentity_Id(identity.getId());
		if (ac != null) {
			authChangeRepository.delete(ac);
		}
	}

	@Override
	public boolean isDisableable() {
		return false;
	}
}
