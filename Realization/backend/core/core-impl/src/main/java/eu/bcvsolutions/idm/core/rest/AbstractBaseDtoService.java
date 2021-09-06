package eu.bcvsolutions.idm.core.rest;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.GenericTypeResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmExportImportDto;
import eu.bcvsolutions.idm.core.api.dto.filter.BaseFilter;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.service.ReadWriteDtoService;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;

/**
 * AbstractBaseDtoService uses in case when we don't have an Entity, but only DTO. For example in workflow services.
 * 
 * @author Vít Švanda
 *
 * @param <DTO>
 * @param <F>
 */
public class AbstractBaseDtoService<DTO extends BaseDto, F extends BaseFilter> implements  ReadWriteDtoService<DTO, F>  {

	private final Class<F> filterClass;
	private final Class<DTO> dtoClass;
	
	@SuppressWarnings("unchecked")
	public AbstractBaseDtoService() {
		Class<?>[] genericTypes = GenericTypeResolver.resolveTypeArguments(getClass(), AbstractBaseDtoService.class);
		dtoClass = (Class<DTO>) genericTypes[0];
		filterClass = (Class<F>) genericTypes[1];
	}
	
	@Override
	public boolean supports(Class<?> delimiter) {
		return dtoClass.isAssignableFrom(delimiter);
	}

	@Override
	public boolean supportsToDtoWithFilter() {
		return false;
	}

	@Override
	public Class<F> getFilterClass() {
		return filterClass;
	}
	

	@Override
	public Class<DTO> getDtoClass() {
		return dtoClass;
	}

	@Override
	public boolean isNew(DTO dto) {
		Assert.notNull(dto, "DTO is required.");
		//
		return dto.getId() == null;
	}
	
	
	@Override
	public DTO checkAccess(DTO dto, BasePermission... permission) {
		return dto;
	}

	@Override
	public DTO get(Serializable id, BasePermission... permission) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public DTO get(Serializable id, F context, BasePermission... permission) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Page<DTO> find(Pageable pageable, BasePermission... permission) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Page<DTO> find(F filter, Pageable pageable, BasePermission... permission) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public long count(F filter, BasePermission... permission) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Page<UUID> findIds(Pageable pageable, BasePermission... permission) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public Page<UUID> findIds(F filter, Pageable pageable, BasePermission... permission) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<String> getPermissions(Serializable id) {
		return null;
	}
	
	@Override
	public Set<String> getPermissions(DTO dto) {
		return null;
	}
	
	@Override
	public Class<? extends BaseEntity> getEntityClass() {
		return null;
	}


	@Override
	public DTO save(DTO dto, BasePermission... permission) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterable<DTO> saveAll(Iterable<DTO> dtos, BasePermission... permission) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void delete(DTO dto, BasePermission... permission) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void deleteById(Serializable id, BasePermission... permission) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void deleteAll(Iterable<DTO> dtos, BasePermission... permission) {
		throw new UnsupportedOperationException();
	}

	@Override
	public DTO saveInternal(DTO dto) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void deleteInternal(DTO dto) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void deleteInternalById(Serializable id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public DTO validateDto(DTO dto) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void export(UUID id, IdmExportImportDto batch) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void siemLog(String action, String status, String targetName, String targetUuid, String subjectName, String subjectUuid, String transactionUuid, String reason) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void siemLog(String action, String status, BaseDto targetDto, BaseDto subjectDto, String transactionUuid, String reason) {
		throw new UnsupportedOperationException();
	}
}
