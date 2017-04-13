package eu.bcvsolutions.idm.core.model.service.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.core.support.EntityLookup;
import org.springframework.plugin.core.OrderAwarePluginRegistry;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.rest.lookup.DefaultEntityLookup;
import eu.bcvsolutions.idm.core.api.service.EntityLookupService;
import eu.bcvsolutions.idm.core.api.service.ReadDtoService;
import eu.bcvsolutions.idm.core.api.service.ReadEntityService;

/**
 * Provide entity services through whole application. 
 * Support for loading {@link BaseEntity} by identifier.
 * 
 * @author Radek Tomiška
 *
 */
@Service
public class DefaultEntityLookupService implements EntityLookupService {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DefaultEntityLookupService.class);
	private final PluginRegistry<EntityLookup<?>, Class<?>> entityLookups;
	private final PluginRegistry<ReadEntityService<?, ?>, Class<?>> entityServices;
	private final PluginRegistry<ReadDtoService<?, ?, ?>, Class<?>> dtoServices;
	
	@Autowired
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public DefaultEntityLookupService(
			List<? extends EntityLookup<?>> entityLookups,
			List<? extends ReadEntityService<?, ?>> entityServices,
			List<? extends ReadDtoService<?, ?, ?>> dtoServices) {
		Assert.notNull(entityLookups, "Entity lookups are required");
		Assert.notNull(entityServices, "Entity services are required");
		Assert.notNull(dtoServices, "Dto services are required");
		//
		this.entityServices = OrderAwarePluginRegistry.create(entityServices);
		this.dtoServices = OrderAwarePluginRegistry.create(dtoServices);
		//
		List entityLookupsWithDefault = new ArrayList<>(entityLookups);
		this.entityServices.getPlugins().forEach(entityService -> {
			if(entityService instanceof ReadEntityService) {
				// register default lookup for given entity class to prevent
				entityLookupsWithDefault.add(new DefaultEntityLookup(entityService));
			}
		});		
		this.entityLookups = OrderAwarePluginRegistry.create(entityLookupsWithDefault);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public <E extends BaseEntity> ReadEntityService<E, ?> getEntityService(Class<E> entityClass) {
		return (ReadEntityService<E, ?>)entityServices.getPluginFor(entityClass);
	}

	@Override
	@SuppressWarnings("unchecked")
	public  <DTO extends BaseDto> ReadDtoService<DTO, ?, ?> getDtoService(Class<DTO> dtoClass) {
		return (ReadDtoService<DTO,?,?>)dtoServices.getPluginFor(dtoClass);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public <E extends BaseEntity, S extends ReadEntityService<E, ?>> S getEntityService(Class<E> entityClass, Class<S> entityServiceClass) {
		return (S)entityServices.getPluginFor(entityClass);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public <DTO extends BaseDto, S extends ReadDtoService<DTO, ?, ?>> S getDtoService(Class<DTO> dtoClass, Class<S> dtoServiceClass) {
		return (S)dtoServices.getPluginFor(dtoClass);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public <E extends BaseEntity> EntityLookup<E> getEntityLookup(Class<E> entityClass) {
		EntityLookup<E> lookup = (EntityLookup<E>)entityLookups.getPluginFor(entityClass);
		if(lookup == null) {
			ReadEntityService<E, ?> service = getEntityService(entityClass);
			if(service != null) {
				// register default lookup for given entity class to prevent
				return new DefaultEntityLookup<E>(service);
			}
		}
		return lookup;
		
	}

	@Override
	@SuppressWarnings("unchecked")
	public <E extends BaseEntity> E lookup(Class<E> entityClass, Serializable entityId) {
		EntityLookup<E> lookup = getEntityLookup(entityClass);
		if (lookup == null) {
			LOG.warn("Lookup for type [{}] does not found. Entity class is not loadable", entityClass);
			return null;
		}
		return (E)lookup.lookupEntity(entityId);
	}	
}
