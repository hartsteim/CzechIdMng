package eu.bcvsolutions.idm.core.model.repository.listener;

import java.io.Serializable;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.hibernate.envers.EntityTrackingRevisionListener;
import org.hibernate.envers.RevisionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

import eu.bcvsolutions.idm.core.api.audit.dto.IdmAuditDto;
import eu.bcvsolutions.idm.core.api.audit.service.IdmAuditService;
import eu.bcvsolutions.idm.core.api.domain.AuditSearchable;
import eu.bcvsolutions.idm.core.api.domain.Codeable;
import eu.bcvsolutions.idm.core.api.domain.TransactionContextHolder;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.api.utils.AutowireHelper;
import eu.bcvsolutions.idm.core.audit.entity.IdmAudit;
import eu.bcvsolutions.idm.core.security.api.domain.AbstractAuthentication;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Creates records to global idm audit (searching through all entities, etc.)
 * TODO: refactor methods changeRevisionDto and changeRevision
 * 
 * @see IdmAuditStrategy
 * @author Ondrej Kopr <kopr@xyxy.cz>
 *
 */
@Configurable
public class IdmAuditListener implements EntityTrackingRevisionListener {

	@Autowired
	private IdmAuditService auditService;
	@Autowired
	private SecurityService securityService;
	@Qualifier("entityManager")
	@PersistenceContext
	private EntityManager entityManger;

	@Override
	public void newRevision(Object revisionEntity) {
		// nothing ... entityChanged is called instead this method
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void entityChanged(Class entityClass, String entityName, Serializable entityId, RevisionType revisionType,
			Object revisionEntity) {
		// autowire services
		autowireServices();

		IdmAudit auditEntity = (IdmAudit) revisionEntity;
		
		if (auditEntity.getEntityId() != null) { // child revision
			IdmAuditDto childRevision = new IdmAuditDto();
			childRevision.setChangedAttributes(auditEntity.getTemporaryChangedColumns());
			childRevision.setTimestamp(((IdmAudit) revisionEntity).getTimestamp());
			this.changeRevisionDto((Class<AbstractEntity>) entityClass, entityName, (UUID) entityId, childRevision,
					revisionType);
			this.auditService.save(childRevision);
		} else { // parent revision
			this.changeRevisionEntity((Class<AbstractEntity>) entityClass, entityName, (UUID) entityId,
					auditEntity, revisionType);
		}
	}

	private void changeRevisionDto(Class<AbstractEntity> entityClass, String entityName, UUID entityId,
		IdmAuditDto revisionEntity, RevisionType revisionType) {
		// List<String> changedColumns;

		// name of entity class - full name.
		revisionEntity.setType(entityName);
		// revision type - MOD, DEL, ADD
		revisionEntity.setModification(revisionType.name());
		// action executer identity
		AbstractAuthentication authentication = securityService.getAuthentication();
		IdmIdentityDto currentModifierIdentity = authentication == null ? null : authentication.getCurrentIdentity();
		IdmIdentityDto originalModifierIdentity = authentication == null ? null : authentication.getOriginalIdentity();
		//
		revisionEntity.setModifier(securityService.getUsername());
		revisionEntity.setModifierId(currentModifierIdentity == null ? null : currentModifierIdentity.getId());
		// original action executer identity (before switch)
		revisionEntity.setOriginalModifier(securityService.getOriginalUsername());
		revisionEntity.setOriginalModifierId(originalModifierIdentity == null ? null : originalModifierIdentity.getId());
		// entity id
		revisionEntity.setEntityId((UUID) entityId);
		//
		// get entity in new transaction if revision type is delete
		AbstractEntity currentEntity = null;
		if (revisionType == RevisionType.DEL) {
			currentEntity = auditService.getActualRemovedEntity(entityClass, entityId);
		} else {
			currentEntity = (AbstractEntity) entityManger.find(entityClass, entityId);
		}
		//
		if (currentEntity instanceof AuditSearchable) {
			AuditSearchable searchableEntity = ((AuditSearchable) currentEntity);
			revisionEntity.setOwnerCode(searchableEntity.getOwnerCode());
			revisionEntity.setOwnerId(searchableEntity.getOwnerId());
			revisionEntity.setOwnerType(searchableEntity.getOwnerType());
			revisionEntity.setSubOwnerCode(searchableEntity.getSubOwnerCode());
			revisionEntity.setSubOwnerId(searchableEntity.getSubOwnerId());
			revisionEntity.setSubOwnerType(searchableEntity.getSubOwnerType());
		} else if (currentEntity instanceof Codeable) {
			revisionEntity.setOwnerCode(((Codeable) currentEntity).getCode());
		}
		// transaction id
		revisionEntity.setTransactionId(TransactionContextHolder.getContext().getTransactionId());
	}
	
	private void changeRevisionEntity(Class<AbstractEntity> entityClass, String entityName, UUID entityId,
		IdmAudit revisionEntity, RevisionType revisionType) {
		// name of entity class - full name.
		revisionEntity.setType(entityName);
		// revision type - MOD, DEL, ADD
		revisionEntity.setModification(revisionType.name());
		// action executer identity
		AbstractAuthentication authentication = securityService.getAuthentication();
		IdmIdentityDto currentIdentity = authentication == null ? null : authentication.getCurrentIdentity();
		IdmIdentityDto originalIdentity = authentication == null ? null : authentication.getOriginalIdentity();
		//
		revisionEntity.setModifier(securityService.getUsername());
		revisionEntity.setModifierId(currentIdentity == null ? null : currentIdentity.getId());
		// original action executer identity (before switch)
		revisionEntity.setOriginalModifier(securityService.getOriginalUsername());
		revisionEntity.setOriginalModifierId(originalIdentity == null ? null : originalIdentity.getId());
		// entity id
		revisionEntity.setEntityId((UUID) entityId);
		//
		AbstractEntity currentEntity = null;
		if (revisionType == RevisionType.DEL) {
			currentEntity = auditService.getActualRemovedEntity(entityClass, entityId);
		} else {
			currentEntity = (AbstractEntity) entityManger.find(entityClass, entityId);
		}
		if (currentEntity instanceof AuditSearchable) {
			AuditSearchable searchableEntity = ((AuditSearchable) currentEntity);
			revisionEntity.setOwnerCode(searchableEntity.getOwnerCode());
			revisionEntity.setOwnerId(searchableEntity.getOwnerId());
			revisionEntity.setOwnerType(searchableEntity.getOwnerType());
			revisionEntity.setSubOwnerCode(searchableEntity.getSubOwnerCode());
			revisionEntity.setSubOwnerId(searchableEntity.getSubOwnerId());
			revisionEntity.setSubOwnerType(searchableEntity.getSubOwnerType());
		} else if (currentEntity instanceof Codeable) {
			revisionEntity.setOwnerCode(((Codeable) currentEntity).getCode());
		}
		// transaction id
		revisionEntity.setTransactionId(TransactionContextHolder.getContext().getTransactionId());
	}

	/**
	 * Method autowire audit service to this listener.
	 * 
	 */
	private void autowireServices() {
		AutowireHelper.autowire(this, this.auditService, this.entityManger, this.securityService);
	}
}
