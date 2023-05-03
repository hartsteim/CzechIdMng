package eu.bcvsolutions.idm.core.config.domain;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.UUID;

import javax.persistence.EntityNotFoundException;

import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.spi.MappingContext;
import org.modelmapper.spi.PropertyMapping;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.domain.Embedded;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import static eu.bcvsolutions.idm.core.api.utils.EntityUtils.getFirstFieldInClassHierarchy;

/**
 * Converter for transform fields (marked with {@link Embedded} annotation) from BaseEntity to UUID 
 * and add entity to embedded part main DTO.
 * 
 * @author svandav
 * @author Radek Tomiška
 */
public class EntityToUuidConverter implements Converter<BaseEntity, UUID> {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(EntityToUuidConverter.class);
	//
	private ModelMapper modeler;
	private LookupService lookupService;
	private ApplicationContext applicationContext;

	public EntityToUuidConverter(ModelMapper modeler, ApplicationContext applicationContext) {
		Assert.notNull(modeler, "Modeler is required!");
		//
		this.modeler = modeler;
		this.applicationContext = applicationContext;
	}

	@Override
	public UUID convert(MappingContext<BaseEntity, UUID> context) {
		if (context.getSource() == null || !(context.getSource().getId() instanceof UUID)) {
			return null;
		}

		BaseEntity entity = null;

		MappingContext<?, ?> parentContext = context.getParent();
		if (parentContext != null && parentContext.getDestination() != null
				&& AbstractDto.class.isAssignableFrom(parentContext.getDestinationType())
				&& parentContext.getSource() != null
				&& BaseEntity.class.isAssignableFrom(parentContext.getSourceType())) {

			try {
				AbstractDto parentDto = (AbstractDto) parentContext.getDestination();
				entity = context.getSource();
				Map<String, BaseDto> embedded = parentDto.getEmbedded();

				entity = lookUpEntityIfEntityIsHibernateProxy(entity);

				PropertyMapping propertyMapping = (PropertyMapping) context.getMapping();
				// Find name of field by property mapping
				String field = propertyMapping.getLastDestinationProperty().getName();
				// Find field in DTO class
				Field fieldTyp = getFirstFieldInClassHierarchy(parentContext.getDestinationType(), field);
				if (fieldTyp.isAnnotationPresent(Embedded.class)) {
					Embedded embeddedAnnotation = fieldTyp.getAnnotation(Embedded.class);
					// if embedded has same type as parent we don't want to make it embedded, otherwise it will cycle.
					if (embeddedAnnotation.enabled() && (!embeddedAnnotation.dtoClass().equals(parentDto.getClass())
							|| embeddedAnnotation.dtoClass().equals(IdmTreeNodeDto.class))) {
						// If has field Embedded (enabled) annotation, then
						// we will create new
						// instance of DTO.
						//
						AbstractDto dto = null;
						// If dto class is abstract get dto from lookup.
						if (Modifier.isAbstract(embeddedAnnotation.dtoClass().getModifiers())) {
							dto = (AbstractDto) getLookupService().lookupDto(entity.getClass(), entity.getId());
						} else {
							dto = embeddedAnnotation.dtoClass().getDeclaredConstructor().newInstance();
						}
						dto.setTrimmed(true);
						// Separate map entity to new embedded DTO.
						try {
							dto = getLookupService().toDto(entity, dto, null);
						} catch (Exception ex) {
							LOG.warn("DTO [{}] cannot be converted into embedded by underlying service, try to convert by default converter.",
									dto.getClass().getCanonicalName(), ex);
							modeler.map(entity, dto);
						}
						embedded.put(field, dto);
						// Add filled DTO to embedded map to parent DTO.
						parentDto.setEmbedded(embedded);
					}
				}
			} catch (ReflectiveOperationException ex) {
				throw new CoreException(ex);
			}
		}
		if (entity == null) {
			return (UUID) context.getSource().getId();
		}
		return (UUID) entity.getId();
	}

	/**
	 * If entity is HibernateProxy we will try to look up the entity again from DB.
	 * We will do the look-up only if implementation in this hibernate proxy throws EntityNotFoundException
	 * This is used for use case when audit tables where truncated. This solution will return live data instead of nothing which
	 * is better for us.
	 * @param entity
	 * @return entity
	 */
	private BaseEntity lookUpEntityIfEntityIsHibernateProxy(BaseEntity entity) {
		if (entity instanceof HibernateProxy) {
			LazyInitializer hibernateLazyInitializer = ((HibernateProxy) entity).getHibernateLazyInitializer();
			try {
				hibernateLazyInitializer.getImplementation();
			} catch (EntityNotFoundException ex){
				String entityName = hibernateLazyInitializer.getEntityName();
				Serializable identifier = hibernateLazyInitializer.getIdentifier();
				entity = getLookupService().lookupEntity(entityName, identifier);
			}
		}
		return entity;
	}

	private LookupService getLookupService() {
		if (this.lookupService == null) {
			Assert.notNull(applicationContext, "Application context is required!");
			//
			this.lookupService = applicationContext.getBean(LookupService.class);
		}
		return this.lookupService;
	}
}