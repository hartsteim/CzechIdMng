package eu.bcvsolutions.idm.core.config.domain;

import static eu.bcvsolutions.idm.core.api.utils.EntityUtils.getFirstFieldInClassHierarchy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.UUID;

import org.modelmapper.ModelMapper;
import org.modelmapper.spi.ConditionalConverter;
import org.modelmapper.spi.MappingContext;
import org.modelmapper.spi.PropertyMapping;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.domain.Embedded;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.api.service.LookupService;

/**
 * Converter for transform fields (marked with {@link Embedded} annotation) from BaseEntity to UUID 
 * and add entity to embedded part main DTO.
 * 
 * FIXME: Embedded will not be set, if field name is different with getter and setter (fieldType == null when setter has different name).
 * 
 * @author svandav
 * @author Radek Tomiška
 * @since 10.4.0
 */
public class EntityToUuidConditionalConverter implements ConditionalConverter<BaseEntity, UUID> {
	
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(EntityToUuidConditionalConverter.class);
	//
	private ModelMapper modeler;
	private LookupService lookupService;
	private ApplicationContext applicationContext;

	public EntityToUuidConditionalConverter(ModelMapper modeler, ApplicationContext applicationContext) {
		Assert.notNull(modeler, "Modeler is required!");
		//
		this.modeler = modeler;
		this.applicationContext = applicationContext;
	}
	
	@Override
	public MatchResult match(Class<?> sourceType, Class<?> destinationType) {
		return BaseEntity.class.isAssignableFrom(sourceType) && (UUID.class.isAssignableFrom(destinationType)) 
				? 
				MatchResult.FULL
				: 
				MatchResult.NONE;
	}

	@Override
	public UUID convert(MappingContext<BaseEntity, UUID> context) {
		if (context.getSource() == null || !(context.getSource().getId() instanceof UUID)) {
			return null;
		}
		//
		MappingContext<?, ?> parentContext = context.getParent();
		if (parentContext == null || parentContext.getDestination() == null
				|| !AbstractDto.class.isAssignableFrom(parentContext.getDestinationType())
				|| parentContext.getSource() == null
				|| !BaseEntity.class.isAssignableFrom(parentContext.getSourceType())) {
			return (UUID) context.getSource().getId();
		}
		//
		PropertyMapping propertyMapping = (PropertyMapping) context.getMapping();
		// Find name of field by property mapping
		String field = propertyMapping.getLastDestinationProperty().getName();
		// dto type
		Class<?> destinationType = parentContext.getDestinationType();
		try {
			AbstractDto parentDto = (AbstractDto) parentContext.getDestination();
			BaseEntity entity = (BaseEntity) context.getSource();
			Map<String, BaseDto> embedded = parentDto.getEmbedded();
			
			// Find field in DTO class
			// FIXME: Embedded will not be set, if field name is different with getter and setter (fieldType == null when setter has different name).
			Field fieldType = getFirstFieldInClassHierarchy(destinationType, field);
			if (fieldType.isAnnotationPresent(Embedded.class)) {
				Embedded embeddedAnnotation = fieldType.getAnnotation(Embedded.class);
				if (embeddedAnnotation.enabled()) {
					// If has field Embedded (enabled) annotation, then
					// we will create new
					// instance of DTO
					//
					AbstractDto dto = null;
					// If dto class is abstract get dto from lookup
					if (Modifier.isAbstract(embeddedAnnotation.dtoClass().getModifiers())) {
						dto = (AbstractDto) getLookupService().lookupDto(entity.getClass(), entity.getId());
					} else {
						dto = embeddedAnnotation.dtoClass().getDeclaredConstructor().newInstance();
					}
					dto.setTrimmed(true);
					// Separate map entity to new embedded DTO
					try {
						dto = getLookupService().toDto(entity, dto, null);
					} catch (Exception ex) {
						LOG.warn("DTO [{}] cannot be converted into embedded by underlying service, try to convert by default converter.",
								dto.getClass().getCanonicalName(), ex);
						modeler.map(entity, dto);
					}
					embedded.put(field, dto);
					// Add filled DTO to embedded map to parent DTO
					parentDto.setEmbedded(embedded);
				}
			}
		} catch (NoSuchFieldException ex) {
			// FIXME: Embedded will not be set, if field name is different with getter and setter (fieldType == null when setter has different name).
			LOG.debug("Field [{}] in dto class [{}] not found, embedded dto cannot be filled. Different dto field name vs. getter and setter name is not supported.",
					field, destinationType);
		} catch (ReflectiveOperationException ex) {
			throw new CoreException(ex);
		}
		//
		return (UUID) context.getSource().getId();
	}
	
	private LookupService getLookupService() {
		if (this.lookupService == null) {
			Assert.notNull(applicationContext, "Application context is required!");
			//
			this.lookupService = this.applicationContext.getBean(LookupService.class);
		}
		return this.lookupService;
	}
}