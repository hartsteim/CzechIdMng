/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.bcvsolutions.idm.core.api.utils;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.util.Asserts;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.domain.Auditable;
import eu.bcvsolutions.idm.core.api.entity.ValidableEntity;

/**
 * Common entity helpers
 *
 * @author Radek Tomiška <tomiska@ders.cz>
 */
public class EntityUtils {

	private EntityUtils() {
	}

	/**
	 * Returns true, when given entity is valid, false otherwise
	 *
	 * @param entity
	 * @return
	 */
	public static boolean isValid(ValidableEntity entity) {
		return isValid(entity, LocalDate.now());
	}
	
	/**
	 * Returns if entity is valid for given date.
	 * 
	 * @param entity
	 * @param targetDate
	 * @return
	 */
	public static boolean isValid(ValidableEntity entity, LocalDate targetDate) {
		Assert.notNull(targetDate, "Target date to compare validity is required.");
		//
		if (entity == null) {
			return false;
		}				
		return (entity.getValidFrom() == null || entity.getValidFrom().compareTo(targetDate) <= 0)
				&& (entity.getValidTill() == null || entity.getValidTill().compareTo(targetDate) >= 0);
	}
	
	/**
	 * Returns true, when given dates are currently is valid, false otherwise
	 * 
	 * @param validFrom
	 * @param validTill
	 * @return
	 */
	public static boolean isValid(ZonedDateTime validFrom, ZonedDateTime validTill) {
		return isValid(validFrom, validTill, ZonedDateTime.now());
	}
	
	/**
	 * Returns if dates are valid for given date.
	 * 
	 * @param validFrom
	 * @param validTill
	 * @param targetDate
	 * @return
	 */
	public static boolean isValid(ZonedDateTime validFrom, ZonedDateTime validTill, ZonedDateTime targetDate) {
		Assert.notNull(targetDate, "Target date to compare validity is required.");
		//
		return (validFrom == null || validFrom.compareTo(targetDate) <= 0)
				&& (validTill == null || validTill.compareTo(targetDate) >= 0);
	}
	
	/**
	 * Returns true, if entity is valid in future, but not now.
	 * 
	 * @param entity
	 * @return
	 */
	public static boolean isValidInFuture(ValidableEntity entity) {
		if (entity == null) {
			return false;
		}		
		LocalDate now = LocalDate.now();	
		return entity.getValidFrom() != null && entity.getValidFrom().compareTo(now) > 0
				&& (entity.getValidTill() == null || entity.getValidTill().compareTo(now) > 0);
	}
	
	/**
	 * Returns true, if entity is valid now or in future.
	 * 
	 * @param entity
	 * @return
	 */
	public static boolean isValidNowOrInFuture(ValidableEntity entity) {
		if (entity == null) {
			return false;
		}	
		LocalDate now = LocalDate.now();
		return entity.getValidTill() == null || entity.getValidTill().compareTo(now) >= 0;
	}	
	
	/**
	 * Returns true, if entity is valid now or in future. Added Clock
	 * to simulate time change in tests.
	 * 
	 * @param entity
	 * @param clock
	 * @return
	 */
	public static boolean isValidNowOrInFuture(ValidableEntity entity, Clock clock) {
		if (entity == null) {
			return false;
		}	
		LocalDate now = LocalDate.now(clock);
		return entity.getValidTill() == null || entity.getValidTill().compareTo(now) >= 0;
	}
	
	/**
	 * Returns false, when validable information are the same
	 * 
	 * @param previous
	 * @param current
	 * @return
	 */
	public static boolean validableChanged(ValidableEntity previous, ValidableEntity current) {
		return !Objects.equals(previous.getValidFrom(), current.getValidFrom()) || !Objects.equals(previous.getValidTill(), current.getValidTill());
	}
	
	/**
	 * Returns module name by entity package (by convention) 
     * <p>
	 * TODO: Does not work for inline classes
	 * 
	 * @param entityClass
	 * @return module identifier
	 */
	public static String getModule(Class<?> entityClass) {
		Assert.notNull(entityClass, "Entity class is requered to get IdM module.");
		//
		String name = entityClass.getCanonicalName();
		if (StringUtils.isEmpty(name)) {
			return null;
		}
		String packages[] = name.split("\\.");
		if (packages.length > 3) {
			return packages[3];
		}
		return null;
	}
	
	/**
	 * Returns {@link UUID} from given {@code string} or {@link UUID}.
	 * 
	 * @param identifier {@code string} or {@link UUID} 
	 * @return
     * @throws ClassCastException If identifier does not conform to the string representation as
     *          described in {@link #toString}
	 */	
	public static UUID toUuid(Object identifier) {        
        if(identifier == null) {
        	return null;
        }
    
        try {
            if(identifier instanceof UUID) {
                return (UUID) identifier;
            }
            return UUID.fromString((String) identifier);
        } catch (Exception ex) {
            throw new ClassCastException(String.format("Identified object [%s] is not an UUID.", identifier));
        }
    }
	
	/**
	 * Check if is string convertible to {@link UUID} 
	 * 
	 * @param uuid
	 * @return true if is given string convertible to {@link UUID}
	 */
	public static boolean isUuid(String uuid){
		if( uuid == null){
			return false;
		}
		try {
			UUID.fromString(uuid);
		} catch(IllegalArgumentException ex){
			// Simple is not UUID
			return false;
		}
		return true;
	}

    public static Field getFirstFieldInClassHierarchy(Class<?> sourceType, String field) throws NoSuchFieldException {
        Field result = getFirstFieldInClassHierarchyInternal(sourceType, field);
        if (result == null) {
            throw new NoSuchFieldException(String.format("No field %s found in class %s", field, sourceType));
        }
        return result;
    }

    private static Field getFirstFieldInClassHierarchyInternal(Class<?> sourceType, String field) {
        if (sourceType == null || field == null) {
            return null;
        }
        final Field[] fields = sourceType.getDeclaredFields();
        return Arrays.stream(fields)
                .filter(f -> field.equals(f.getName()))
                .findFirst()
                .orElseGet(() -> getFirstFieldInClassHierarchyInternal(sourceType.getSuperclass(), field));
    }
    
    /**
     * Find field property descriptor.
     * 
     * @param entity dto / entity / object
     * @param propertyName field name
     * @return field property descriptor
     * @throws IntrospectionException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     * @since 10.2.0
     */
    public static PropertyDescriptor getFieldDescriptor(Object entity, String propertyName) 
    		throws IntrospectionException, IllegalAccessException {
    	Assert.notNull(entity, "Dto /  entity / ojec is required to find field property descriptor.");
    	Assert.hasLength(propertyName, "Property name is requred.");
    	Class<?> entityClass = entity.getClass();
    	//
		Optional<PropertyDescriptor> propertyDescriptionOptional = Arrays
				.asList(Introspector.getBeanInfo(entityClass).getPropertyDescriptors())
				.stream()
				.filter(propertyDescriptor -> {
					return propertyName.equals(propertyDescriptor.getName());
				})
				.findFirst();
		//
		if (!propertyDescriptionOptional.isPresent()) {
			throw new IllegalAccessException(String.format("Class [%s] field [%s] not found.", entityClass, propertyName));
		}
		return propertyDescriptionOptional.get();
	}
    
	/**
	 * Return object from entity for given property name
	 * 
	 * @param entity
	 * @param propertyName
	 * @return
	 * @throws IntrospectionException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	public static Object getEntityValue(Object entity, String propertyName) 
			throws IntrospectionException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		PropertyDescriptor propertyDescriptor = getFieldDescriptor(entity, propertyName);
		//
		return propertyDescriptor.getReadMethod().invoke(entity);
	}
	
	/**
	 * Get value from given entity field. 
	 * If is first parameter in write method String and value is not String, then will be value converted to String.
	 * 
	 * @param entity
	 * @param propertyName
	 * @param value
	 * @return
	 * @throws IntrospectionException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	public static Object setEntityValue(Object entity, String propertyName, Object value)
			throws IntrospectionException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Optional<PropertyDescriptor> propertyDescriptionOptional = Arrays
				.asList(Introspector.getBeanInfo(entity.getClass()).getPropertyDescriptors()).stream()
				.filter(propertyDescriptor -> {
					return propertyName.equals(propertyDescriptor.getName());
				}).findFirst(); 
		if (!propertyDescriptionOptional.isPresent()) {
			throw new IllegalAccessException("Field " + propertyName + " not found!");
		}
		PropertyDescriptor propertyDescriptor = propertyDescriptionOptional.get();
		Class<?> parameterClass = propertyDescriptor.getWriteMethod().getParameterTypes()[0];
		if (value != null && String.class.equals(parameterClass) && !(value instanceof String)) {
			value = String.valueOf(value);
		}
		// When is target LocalDate and value not, then we try create instance of LocalDate first
		if (value != null && LocalDate.class.equals(parameterClass) && !(value instanceof LocalDate)) {
			value = LocalDate.parse(value.toString());
		}
		if (value != null && !parameterClass.isAssignableFrom(value.getClass()) && !(value.getClass().isPrimitive() || parameterClass.isPrimitive())) {
			throw new IllegalAccessException(
					MessageFormat.format("Wrong type of value [{0}]. Value must be instance of [{1}] type, but has type [{2}]!",
							value, parameterClass, value.getClass()));
		}
		return propertyDescriptor.getWriteMethod().invoke(entity, value);
	}

	/**
	 * Method clear audit fields in entity. Entity must not be null.
	 * 
	 * @param entity or dto
	 */
	public static void clearAuditFields(Auditable auditable) {
		Asserts.notNull(auditable, "Entity must be not null!");
		//
		auditable.setCreated(null);
		auditable.setCreator(null);
		auditable.setCreatorId(null);
		auditable.setModified(null);
		auditable.setModifier(null);
		auditable.setModifierId(null);
		auditable.setOriginalCreator(null);
		auditable.setOriginalCreatorId(null);
		auditable.setOriginalModifier(null);
		auditable.setOriginalModifierId(null);
		auditable.setTransactionId(null);
	}
	
	/**
	 * Sets target audit fields by given source. 
	 * Id and realm id is not copied.
	 * 
	 * @param auditableSource entity or dto
	 * @param auditableTarget entity or dto
	 * @see #setAuditable(Auditable, Auditable)
	 */
	public static void copyAuditFields(Auditable source, Auditable target) {
		Asserts.notNull(target, "Entity must be not null!");
		Asserts.notNull(source, "Entity must be not null!");
		//
		target.setCreated(source.getCreated());
		target.setCreator(source.getCreator());
		target.setCreatorId(source.getCreatorId());
		target.setModified(source.getModified());
		target.setModifier(source.getModifier());
		target.setModifierId(source.getModifierId());
		target.setOriginalCreator(source.getOriginalCreator());
		target.setOriginalCreatorId(source.getOriginalCreatorId());
		target.setOriginalModifier(source.getModifier());
		target.setOriginalModifierId(source.getOriginalModifierId());
		target.setTransactionId(source.getTransactionId());
	}
}
