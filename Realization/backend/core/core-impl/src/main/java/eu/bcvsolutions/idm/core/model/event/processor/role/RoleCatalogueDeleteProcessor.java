package eu.bcvsolutions.idm.core.model.event.processor.role;

import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;

import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueRoleDto;
import eu.bcvsolutions.idm.core.api.event.CoreEventProcessor;
import eu.bcvsolutions.idm.core.api.event.DefaultEventResult;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueService;
import eu.bcvsolutions.idm.core.model.event.RoleCatalogueEvent;
import eu.bcvsolutions.idm.core.model.event.RoleCatalogueEvent.RoleCatalogueEventType;

/**
 * Deletes role catalogue items.
 * 
 * @author Radek Tomiška
 */
@Component
@Description("Deletes role catalogue items.")
public class RoleCatalogueDeleteProcessor extends CoreEventProcessor<IdmRoleCatalogueDto> {

	public static final String PROCESSOR_NAME = "role-catalogue-delete-processor";
	//
	@Autowired private IdmRoleCatalogueService service;
	@Autowired private IdmRoleCatalogueRoleService roleCatalogueRoleService;
	@Qualifier("entityManager")
	@Autowired private EntityManager entityManager;

	public RoleCatalogueDeleteProcessor() {
		super(RoleCatalogueEventType.DELETE);
	}

	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	@Override
	public EventResult<IdmRoleCatalogueDto> process(EntityEvent<IdmRoleCatalogueDto> event) {
		IdmRoleCatalogueDto roleCatalogue = event.getContent();
		UUID roleCatalogueId = roleCatalogue.getId();
		Assert.notNull(roleCatalogueId, "Role catalogue id is required.");
		boolean forceDelete = getBooleanProperty(PROPERTY_FORCE_DELETE, event.getProperties());
		//
		if (!forceDelete) {
			if (service.findChildrenByParent(roleCatalogueId, PageRequest.of(0, 1)).getTotalElements() != 0) {
				throw new ResultCodeException(CoreResultCode.ROLE_CATALOGUE_DELETE_FAILED_HAS_CHILDREN, ImmutableMap.of("roleCatalogue", roleCatalogue.getCode()));
			}
		} else {
			// 
			// remove children at first recursively
			List<IdmRoleCatalogueDto> children = service.findChildrenByParent(roleCatalogueId, null).getContent();
			for (int counter = 0; counter < children.size(); counter++) {
				RoleCatalogueEvent roleCatalogueEvent = new RoleCatalogueEvent(
						RoleCatalogueEventType.DELETE,
						children.get(counter)
				);
				service.publish(roleCatalogueEvent, event); // ~ propagate force and priority from parent event
				// children can have unlimited children => clear session
				clearSession();
			}
		}
		//
		// remove assigned roles into role catalogue
		List<IdmRoleCatalogueRoleDto> rolesInCatalogue = roleCatalogueRoleService.findAllByRoleCatalogue(roleCatalogue.getId());
		for (int counter = 0; counter < rolesInCatalogue.size(); counter++) {
			roleCatalogueRoleService.delete(rolesInCatalogue.get(counter));
			//
			if (counter % 100 == 0) {
				clearSession();
			}
		}
		//
		service.deleteInternal(event.getContent());
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
