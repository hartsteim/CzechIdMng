package eu.bcvsolutions.idm.core.rest.lookup;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.rest.lookup.CodeableDtoLookup;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;

/**
 * Identity lookup.
 * 
 * @author Radek Tomiška
 */
@Component
public class IdmIdentityDtoLookup extends CodeableDtoLookup<IdmIdentityDto> {

	@Autowired private ApplicationContext applicationContext;
	//
	private IdmIdentityService identityService;

	/**
	 * We need to inject service lazily - we need security AOP to take effect.
	 * 
	 * @return service
	 */
	@Override
	protected IdmIdentityService getService() {
		if (identityService == null) {
			identityService = applicationContext.getBean(IdmIdentityService.class);
		}
		return identityService;
	}
}
