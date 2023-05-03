package eu.bcvsolutions.idm.acc.service.impl;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import eu.bcvsolutions.idm.acc.domain.AccGroupPermission;
import eu.bcvsolutions.idm.acc.domain.ProvisioningEventType;
import eu.bcvsolutions.idm.acc.domain.SysValueChangeType;
import eu.bcvsolutions.idm.acc.dto.SysAttributeDifferenceDto;
import eu.bcvsolutions.idm.acc.dto.SysAttributeDifferenceValueDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningArchiveDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningArchiveDto.Builder;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningOperationDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysProvisioningOperationFilter;
import eu.bcvsolutions.idm.acc.entity.AccAccount_;
import eu.bcvsolutions.idm.acc.entity.SysProvisioningArchive;
import eu.bcvsolutions.idm.acc.entity.SysProvisioningArchive_;
import eu.bcvsolutions.idm.acc.entity.SysProvisioningAttribute;
import eu.bcvsolutions.idm.acc.entity.SysProvisioningAttribute_;
import eu.bcvsolutions.idm.acc.entity.SysProvisioningOperation_;
import eu.bcvsolutions.idm.acc.entity.SysSystem_;
import eu.bcvsolutions.idm.acc.repository.SysProvisioningArchiveRepository;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningArchiveService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.OperationResultDto;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.AbstractReadWriteDtoService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.impl.IcConnectorObjectImpl;

/**
 * Archived provisioning operations
 * 
 * @author Radek Tomiška
 *
 */
@Service
public class DefaultSysProvisioningArchiveService
		extends AbstractReadWriteDtoService<SysProvisioningArchiveDto, SysProvisioningArchive, SysProvisioningOperationFilter> 
		implements SysProvisioningArchiveService {
	
	@Autowired private SysSystemEntityService systemEntityService;
	@Autowired private SysProvisioningAttributeService provisioningAttributeService;
	@Autowired private SysSystemService systemService;

	@Autowired
	public DefaultSysProvisioningArchiveService(SysProvisioningArchiveRepository repository) {
		super(repository);
	}
	
	@Override
	public AuthorizableType getAuthorizableType() {
		return new AuthorizableType(AccGroupPermission.PROVISIONINGARCHIVE, getEntityClass());
	}
	
	@Override
	@Transactional
	public void deleteInternal(SysProvisioningArchiveDto dto) {
		Assert.notNull(dto, "DTO is required.");
		// delete attributes
		provisioningAttributeService.deleteAttributes(dto);
		//
		super.deleteInternal(dto);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW) // we want log in archive always
	public SysProvisioningArchiveDto archive(SysProvisioningOperationDto provisioningOperation) {
		Builder builder = new SysProvisioningArchiveDto.Builder(provisioningOperation);
		if(provisioningOperation.getSystemEntity() != null) {
			SysSystemEntityDto systemEntity =  DtoUtils.getEmbedded(provisioningOperation, SysProvisioningOperation_.systemEntity, (SysSystemEntityDto) null);
			if (systemEntity == null) {
				systemEntity = systemEntityService.get(provisioningOperation.getSystemEntity());
			}
			builder.setSystemEntityUid(systemEntity.getUid());
		}
		//
		SysProvisioningArchiveDto archive = builder.build();
		// preserve original operation creator
		archive.setCreator(provisioningOperation.getCreator());
		archive.setCreatorId(provisioningOperation.getCreatorId());
		archive.setOriginalCreator(provisioningOperation.getOriginalCreator());
		archive.setOriginalCreatorId(provisioningOperation.getOriginalCreatorId());
		// preserve original created => operation was created
		archive.setCreated(provisioningOperation.getCreated());
		// archive modified is used as the executed / canceled 
		archive.setModified(ZonedDateTime.now());
		// archive relation on the role-request
		archive.setRoleRequestId(provisioningOperation.getRoleRequestId());
		archive.setAccount(provisioningOperation.getAccount());
		//
		archive = save(archive);
		//
		// log attributes used in provisioning context into provisioning attributes
		provisioningAttributeService.saveAttributes(archive);
		//
		return archive;
	}
	
	/**
	 * Optimize - system can be pre-loaded in DTO.
	 * 
	 * @param archive
	 * @return
	 */
	@Override
	public SysSystemDto getSystem(SysProvisioningArchiveDto archive) {
		SysSystemDto system = DtoUtils.getEmbedded(archive, SysProvisioningArchive_.system, (SysSystemDto) null);
		if (system == null) {
			// just for sure, self constructed operation can be given
			system = systemService.get(archive.getSystem());
		}
		//
		return system;
	}
	
	@Override
	public List<SysAttributeDifferenceDto> evaluateProvisioningDifferences(IcConnectorObject current, IcConnectorObject changed) {
		List<SysAttributeDifferenceDto> resultAttributes = new ArrayList<>();
		IcConnectorObject currentObject = current != null ? current : new IcConnectorObjectImpl();
		IcConnectorObject changedObject = changed != null ? changed : new IcConnectorObjectImpl();
		
		List<IcAttribute> currentAttributes = currentObject.getAttributes();
		List<IcAttribute> changedAttributes = changedObject.getAttributes();
		
		changedAttributes.forEach(changedAttribute -> {
			if (currentObject.getAttributeByName(changedAttribute.getName()) == null) {
				SysAttributeDifferenceDto vsAttribute = new SysAttributeDifferenceDto(changedAttribute.getName(),
						changedAttribute.isMultiValue(), true);
				if (changedAttribute.isMultiValue()) {
					if (changedAttribute.getValues() != null) {
						changedAttribute.getValues().forEach(value -> {
							vsAttribute.getValues().add(new SysAttributeDifferenceValueDto(value, null, SysValueChangeType.ADDED));
						});
					}
				} else {
					vsAttribute.setValue(
							new SysAttributeDifferenceValueDto(changedAttribute.getValue(), null, SysValueChangeType.ADDED));
				}
				resultAttributes.add(vsAttribute);
			}
		});

		// Second add all already existing attributes
		currentAttributes.forEach(currentAttribute -> {
			SysAttributeDifferenceDto vsAttribute;
			// Attribute was changed
			if (changedObject.getAttributeByName(currentAttribute.getName()) != null) {
				IcAttribute changedAttribute = changedObject.getAttributeByName(currentAttribute.getName());
				boolean isMultivalue = isIcAttributeMultivalue(currentAttribute, changedAttribute);
				vsAttribute = new SysAttributeDifferenceDto(currentAttribute.getName(), isMultivalue, true);
				if (isMultivalue) {
					vsAttribute.setChanged(false);
					if (changedAttribute.getValues() != null) {
						changedAttribute.getValues().forEach(value -> {
							if (currentAttribute.getValues() != null && currentAttribute.getValues().contains(value)) {
								vsAttribute.getValues().add(new SysAttributeDifferenceValueDto(value, value, null));
							} else {
								vsAttribute.setChanged(true);
								vsAttribute.getValues()
										.add(new SysAttributeDifferenceValueDto(value, null, SysValueChangeType.ADDED));
							}
						});
					}
					if (currentAttribute.getValues() != null) {
						currentAttribute.getValues().forEach(value -> {
							if (changedAttribute.getValues() == null || !changedAttribute.getValues().contains(value)) {
								vsAttribute.setChanged(true);
								vsAttribute.getValues()
										.add(new SysAttributeDifferenceValueDto(value, value, SysValueChangeType.REMOVED));
							}
						});
					}
				} else {
					Object changedValue = changedAttribute.getValue();
					Object currentValue = currentAttribute.getValue();
					if ((changedValue == null && currentValue == null)
							|| (changedValue != null && changedValue.equals(currentValue))
							|| (currentValue != null && currentValue.equals(changedValue))) {
						vsAttribute.setChanged(false);
						vsAttribute.setValue(new SysAttributeDifferenceValueDto(changedValue, currentValue, null));
					} else {
						vsAttribute.setValue(
								new SysAttributeDifferenceValueDto(changedValue, currentValue, SysValueChangeType.UPDATED));
					}
				}
			} else {
				// Attribute was not changed
				boolean isMultivalue = isIcAttributeMultivalue(currentAttribute, null);
				vsAttribute = new SysAttributeDifferenceDto(currentAttribute.getName(), isMultivalue, false);
				if (isMultivalue) {
					if (currentAttribute.getValues() != null) {
						currentAttribute.getValues().forEach(value -> {
							vsAttribute.getValues().add(new SysAttributeDifferenceValueDto(value, value, null));
						});
					}
				} else {
					vsAttribute.setValue(
							new SysAttributeDifferenceValueDto(currentAttribute.getValue(), currentAttribute.getValue(), null));
				}
			}
			resultAttributes.add(vsAttribute);
		});
		
		return resultAttributes;
	}
	
	/**
	 * Auxiliary method to tell whether attribute contains multivalue attribute
	 * The flag itself is not sufficient  
	 */
	private boolean isIcAttributeMultivalue(IcAttribute current, IcAttribute changed) {
		boolean res1 = current != null &&
				(current.isMultiValue() || (current.getValues() != null && current.getValues().size() > 1));
		boolean res2 = changed != null &&
				(changed.isMultiValue() || (changed.getValues() != null && changed.getValues().size() > 1));
		return res1 || res2;
	}
	
	@Override
	protected List<Predicate> toPredicates(Root<SysProvisioningArchive> root, CriteriaQuery<?> query, CriteriaBuilder builder, SysProvisioningOperationFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
		//
		// quick - "fulltext"
		if (StringUtils.isNotEmpty(filter.getText())) {
			throw new ResultCodeException(CoreResultCode.BAD_FILTER, "Filter by text is not supported.");
		}
		// System Id
		UUID systemId = filter.getSystemId();
		if (systemId != null) {
			predicates.add(builder.equal(root.get(SysProvisioningArchive_.system).get(SysSystem_.id), systemId));
		}
		// From
		ZonedDateTime from = filter.getFrom();
		if (from != null) {
			predicates.add(builder.greaterThanOrEqualTo(root.get(SysProvisioningArchive_.created), from));
		}
		// Till
		ZonedDateTime till = filter.getTill();
		if (till != null) {
			predicates.add(builder.lessThanOrEqualTo(root.get(SysProvisioningArchive_.created), till));
		}
		// Operation type
		ProvisioningEventType operationType = filter.getOperationType();
		if (operationType != null) {
			predicates.add(builder.equal(root.get(SysProvisioningArchive_.operationType), operationType));
		}
		// Entity type
		String entityType = filter.getEntityType();
		if (entityType != null) {
			predicates.add(builder.equal(root.get(SysProvisioningArchive_.entityType), entityType));
		}
		// Entity identifier
		UUID entityIdentifier = filter.getEntityIdentifier();
		if (entityIdentifier != null) {
			predicates.add(builder.equal(root.get(SysProvisioningArchive_.entityIdentifier), entityIdentifier));
		}
		// System entity
		if (filter.getSystemEntity() != null) {
			throw new ResultCodeException(CoreResultCode.BAD_FILTER, "Filter by system entity identifier is not supported. Use system entity uid filter.");
		}
		// Account
		UUID account = filter.getAccountId();
		if (account != null) {
			predicates.add(builder.equal(root.get(SysProvisioningArchive_.ACCOUNT).get(AccAccount_.ID), account));
		}
		// System entity UID
		String systemEntityUid = filter.getSystemEntityUid();
		if (StringUtils.isNotEmpty(systemEntityUid)) {
			predicates.add(builder.equal(root.get(SysProvisioningArchive_.systemEntityUid), systemEntityUid));
		}
		// Operation result and his state
		OperationState resultState = filter.getResultState();
		if (resultState != null) {
			predicates.add(builder.equal(root.get(SysProvisioningArchive_.result).get(OperationResultDto.PROPERTY_STATE), resultState));
		}
		// Batch id
		if (filter.getBatchId() != null) {
			throw new UnsupportedOperationException("Filter by batch identifier is not supported in archive.");
		}
		// Role-request ID
		UUID roleRequestId = filter.getRoleRequestId();
		if (roleRequestId != null) {
			predicates.add(builder.equal(root.get(SysProvisioningArchive_.roleRequestId), roleRequestId));
		}
		// updated attributes
		List<String> attributeUpdated = filter.getAttributeUpdated();
		if (!CollectionUtils.isEmpty(attributeUpdated)) {
			Subquery<SysProvisioningAttribute> subquery = query.subquery(SysProvisioningAttribute.class);
			Root<SysProvisioningAttribute> subRoot = subquery.from(SysProvisioningAttribute.class);
			subquery.select(subRoot);
			subquery.where(
                    builder.and(
                    		builder.equal(subRoot.get(SysProvisioningAttribute_.provisioningId), root.get(SysProvisioningArchive_.id)), // correlation attr
                    		subRoot.get(SysProvisioningAttribute_.name).in(attributeUpdated),
                    		builder.isFalse(subRoot.get(SysProvisioningAttribute_.removed))
                    		)
            );		
			predicates.add(builder.exists(subquery));
		}
		// removed attributes
		List<String> attributeRemoved = filter.getAttributeRemoved();
		if (!CollectionUtils.isEmpty(attributeRemoved)) {
			Subquery<SysProvisioningAttribute> subquery = query.subquery(SysProvisioningAttribute.class);
			Root<SysProvisioningAttribute> subRoot = subquery.from(SysProvisioningAttribute.class);
			subquery.select(subRoot);
			subquery.where(
                    builder.and(
                    		builder.equal(subRoot.get(SysProvisioningAttribute_.provisioningId), root.get(SysProvisioningArchive_.id)), // correlation attr
                    		subRoot.get(SysProvisioningAttribute_.name).in(attributeRemoved),
                    		builder.isTrue(subRoot.get(SysProvisioningAttribute_.removed))
                    		)
            );		
			predicates.add(builder.exists(subquery));
		}
		// empty provisioning
		Boolean emptyProvisioning = filter.getEmptyProvisioning();
		if (emptyProvisioning != null) {
			Subquery<SysProvisioningAttribute> subquery = query.subquery(SysProvisioningAttribute.class);
			Root<SysProvisioningAttribute> subRoot = subquery.from(SysProvisioningAttribute.class);
			subquery.select(subRoot);
			subquery.where(
                    builder.and(builder.equal(subRoot.get(SysProvisioningAttribute_.provisioningId), root.get(SysProvisioningArchive_.id))) // correlation attr)
            );
			//
			Predicate provisioningPredicate = builder.exists(subquery); // has attributes
			if (emptyProvisioning) {
				provisioningPredicate = builder.and(
						builder.not(provisioningPredicate), // empty
						builder.notEqual(root.get(SysProvisioningArchive_.operationType), ProvisioningEventType.DELETE) // delete operations are not considered as empty
				);
			} else {
				// delete operations are not considered as empty or filled => show all time
				provisioningPredicate = builder.or(
						provisioningPredicate,
						builder.equal(root.get(SysProvisioningArchive_.operationType), ProvisioningEventType.DELETE)
				);
			}
			predicates.add(provisioningPredicate);
		}
		if (filter.getEmptyProvisioningType() != null) {
			throw new UnsupportedOperationException("Filter by empty provisioning type is not supported in archive. Use 'emptyProvisioning' parameter instead.");
		}
		//
		return predicates;
	}
}
