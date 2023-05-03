package eu.bcvsolutions.idm.acc.service.impl;

import java.io.Serializable;
import java.util.UUID;

import org.apache.http.util.Asserts;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.entity.SysSystem;
import eu.bcvsolutions.idm.acc.entity.SysSystemFormValue;
import eu.bcvsolutions.idm.acc.service.api.SysSystemFormValueService;
import eu.bcvsolutions.idm.core.api.service.ConfidentialStorage;
import eu.bcvsolutions.idm.core.api.utils.EntityUtils;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.repository.AbstractFormValueRepository;
import eu.bcvsolutions.idm.core.eav.service.impl.AbstractFormValueService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;

/**
 * Service for control form value on system
 * 
 * @author svandav
 * @author Radek Tomiška
 *
 */
public class DefaultSysSystemFormValueService extends AbstractFormValueService<SysSystem, SysSystemFormValue>
		implements SysSystemFormValueService {

	@Autowired 
	private ConfidentialStorage confidentialStorage;

	public DefaultSysSystemFormValueService(AbstractFormValueRepository<SysSystem, SysSystemFormValue> repository) {
		super(repository);
	}

	@Override
	public IdmFormValueDto clone(UUID id) {
		IdmFormValueDto	 original = this.get(id);
		Asserts.notNull(original, "System form value must be found!");
		original.setId(null);
		EntityUtils.clearAuditFields(original);
		return original;
	}

	@Override
	public IdmFormValueDto duplicate(UUID id, SysSystem owner) {
		Asserts.notNull(owner, "Owner (system) must be set!");
		IdmFormValueDto cloned = this.clone(id);
		cloned.setOwner(owner);
		cloned = this.save(cloned);
		
		// For confidential we will load guarded value by old ID and save for new value.
		if (cloned.isConfidential()) {
			Serializable guardedValue = this.getConfidentialPersistentValue(this.get(id));
			this.confidentialStorage.save(
					cloned.getId(), 
					SysSystemFormValue.class,
					this.getConfidentialStorageKey(cloned.getFormAttribute()),
					guardedValue);
		}
		return cloned;
	}

	@Override
	public AuthorizableType getAuthorizableType() {
		return new AuthorizableType(CoreGroupPermission.FORMVALUE, getEntityClass());
	}

}
