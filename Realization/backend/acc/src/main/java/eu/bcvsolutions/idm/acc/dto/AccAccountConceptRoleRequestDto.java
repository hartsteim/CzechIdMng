package eu.bcvsolutions.idm.acc.dto;

import eu.bcvsolutions.idm.acc.entity.AccAccount;
import eu.bcvsolutions.idm.acc.entity.AccAccountRole;
import eu.bcvsolutions.idm.core.api.domain.Embedded;
import eu.bcvsolutions.idm.core.api.dto.AbstractConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.model.entity.AbstractConceptRoleRequest;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.envers.Audited;
import org.springframework.hateoas.core.Relation;

import javax.persistence.*;
import java.util.UUID;

/**
 * @author Peter Štrunc <github.com/peter-strunc>
 */
@Relation(collectionRelation = "accAccountConceptRoleRequests")
public class AccAccountConceptRoleRequestDto extends AbstractConceptRoleRequestDto {

    private static final long serialVersionUID = 1L;

    @Embedded(dtoClass = AccAccountDto.class)
    private UUID accAccount;

    @Embedded(dtoClass = AccAccountRoleDto.class)
    private UUID accountRole;

    public UUID getAccAccount() {
        return accAccount;
    }

    public void setAccAccount(UUID accAccount) {
        this.accAccount = accAccount;
    }

    public UUID getAccountRole() {
        return accountRole;
    }

    public void setAccountRole(UUID accountRole) {
        this.accountRole = accountRole;
    }

    @Override
    public UUID getRoleAssignmentUuid() {
        return getAccountRole();
    }

    @Override
    public void setRoleAssignmentUuid(UUID id) {
        setAccAccount(id);
    }

    @Override
    public UUID getOwnerUuid() {
        return getAccAccount();
    }

    @Override
    public void setOwnerUuid(UUID id) {
        setAccAccount(id);
    }

    @Override
    public AccAccountConceptRoleRequestDto copy() {
        AccAccountConceptRoleRequestDto conceptRoleRequest = new AccAccountConceptRoleRequestDto();
        conceptRoleRequest.setRoleRequest(getRoleRequest());
        conceptRoleRequest.setOperation(getOperation());
        // from concept
        conceptRoleRequest.setValidFrom(getValidFrom());
        conceptRoleRequest.setValidTill(getValidTill());
        conceptRoleRequest.setAccAccount(getAccAccount());
        // from assigned (~changed) sub role
        conceptRoleRequest.setDirectConcept(getId());
        // save and add to concepts to be processed
        return conceptRoleRequest;
    }
}
