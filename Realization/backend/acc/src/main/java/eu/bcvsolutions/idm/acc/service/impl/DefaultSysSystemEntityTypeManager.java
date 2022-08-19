package eu.bcvsolutions.idm.acc.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityTypeManager;
import eu.bcvsolutions.idm.acc.system.entity.SystemEntityTypeRegistrable;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;

@Component("systemEntityManager")
public class DefaultSysSystemEntityTypeManager implements SysSystemEntityTypeManager {

	@Autowired
	private List<SystemEntityTypeRegistrable> systemEntityTypes;
	
	@Override
	public SystemEntityTypeRegistrable getSystemEntityByCode(String code) {
		for (SystemEntityTypeRegistrable entityType : systemEntityTypes) {
			if (entityType.getSystemEntityCode().equals(code)) {
				return entityType;
			}
		}
		
		return null;
	}

	@Override
	public SystemEntityTypeRegistrable getSystemEntityByClass(Class<? extends AbstractDto> clazz) {
		for (SystemEntityTypeRegistrable entityType : systemEntityTypes) {
			if (entityType.getEntityType().equals(clazz)) {
				return entityType;
			}
		}
		
		return null;
	}

	@Override
	public List<SystemEntityTypeRegistrable> getSupportedEntityTypes() {
		return systemEntityTypes;
	}
}
