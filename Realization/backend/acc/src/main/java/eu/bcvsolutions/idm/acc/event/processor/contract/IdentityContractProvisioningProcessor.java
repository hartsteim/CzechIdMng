package eu.bcvsolutions.idm.acc.event.processor.contract;

import java.util.Set;
import java.util.UUID;

import javax.persistence.EntityManager;

import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.acc.AccModuleDescriptor;
import eu.bcvsolutions.idm.acc.event.ProvisioningEvent;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningService;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.model.event.IdentityContractEvent.IdentityContractEventType;
import eu.bcvsolutions.idm.core.security.api.domain.Enabled;

/**
 * Executes provisioning after identity contract is saved or deleted. 
 * Executes provisioning for contract's identity and all contract's subordinates (newly added and previous) - depends on configuration property.
 * 
 * @author Radek Tomiška
 */
@Enabled(AccModuleDescriptor.MODULE_ID)
@Component(IdentityContractProvisioningProcessor.PROCESSOR_NAME)
@Description("Executes provisioning for contract's identity and all contract's subordinates (newly added and previous) - depends on configuration property")
public class IdentityContractProvisioningProcessor extends AbstractIdentityContractProvisioningProcessor {

	public static final String PROCESSOR_NAME = "identity-contract-provisioning-processor";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(IdentityContractProvisioningProcessor.class);
	//	
	@Autowired private EntityEventManager entityEventManager;	
	@Autowired private ProvisioningService provisioningService;
	@Qualifier("entityManager")
	@Autowired private EntityManager entityManager;
	
	public IdentityContractProvisioningProcessor() {
		super(IdentityContractEventType.DELETE, IdentityContractEventType.NOTIFY);
	}
	
	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	@Override
	@SuppressWarnings("unchecked")
	public EventResult<IdmIdentityContractDto> process(EntityEvent<IdmIdentityContractDto> event) {
		UUID identityId = event.getContent().getIdentity();
		//
		// register change => provisioning will be executed for manager
		doProvisioning(identityId, event);
		//
		// execute provisioning for all subordinates by given contract
		if (isIncludeSubordinates()) {
			Set<UUID> originalSubordinates = (Set<UUID>) event.getProperties().get(PROPERTY_PREVIOUS_SUBORDINATES);
			findAllSubordinates(identityId)
				.forEach(subordinate -> {
					if (originalSubordinates != null && originalSubordinates.contains(subordinate.getId())) {
						originalSubordinates.remove(subordinate.getId());
					} else {
						// provisioning will be executed for new subordinate
						doProvisioning(subordinate, event);
					}
				});
			if (originalSubordinates != null) {
				originalSubordinates.forEach(originalSubordinateId -> {
					// provisioning will be executed for new subordinate
					doProvisioning(originalSubordinateId, event);
				});
			}
		}
		return new DefaultEventResult<>(event, this);
	}
	
	@Override
	public int getOrder() {
		return ProvisioningEvent.DEFAULT_PROVISIONING_ORDER;
	}
	
	private void doProvisioning(UUID identityId, EntityEvent<IdmIdentityContractDto> event) {
		if (!event.hasType(IdentityContractEventType.NOTIFY)) {
			// sync
			IdmIdentityDto identity = getLookupService().lookupDto(IdmIdentityDto.class, identityId);
			if (identity == null) {
				LOG.debug("Identity [{}] was already deleted, duplicate provisioning will be skipped.", identityId);
				//
				return;
			}
			doProvisioning(identity, event);
		} else {
			// async
			LOG.debug("Register change for identity [{}]", identityId);
			entityEventManager.changedEntity(IdmIdentityDto.class, identityId, event);
		}
	}
	
	private void doProvisioning(IdmIdentityDto identity, EntityEvent<IdmIdentityContractDto> event) {
		if (!event.hasType(IdentityContractEventType.NOTIFY)) {
			LOG.debug("Call provisioning for identity [{}]", identity.getUsername());
			provisioningService.doProvisioning(identity);
			//
			// flush and clear session - manager can have a lot of subordinates
			if (getHibernateSession().isOpen()) {
				getHibernateSession().flush();
				getHibernateSession().clear();
			}
		} else {
			// async
			LOG.debug("Register change for identity [{}]", identity.getId());
			entityEventManager.changedEntity(identity, event);
		}
	}
	
	private Session getHibernateSession() {
		return (Session) this.entityManager.getDelegate();
	}
}