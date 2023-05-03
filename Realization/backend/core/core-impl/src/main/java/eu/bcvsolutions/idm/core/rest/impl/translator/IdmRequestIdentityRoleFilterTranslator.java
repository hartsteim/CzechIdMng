package eu.bcvsolutions.idm.core.rest.impl.translator;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.filter.BaseRoleAssignmentFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.FormableFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmBaseConceptRoleRequestFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRequestIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.OwnerTypeFilter;
import eu.bcvsolutions.idm.core.api.rest.AbstractPluggableFilterTranslator;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

/**
 * @author Peter Štrunc <github.com/peter-strunc>
 */
@Component
public class IdmRequestIdentityRoleFilterTranslator extends AbstractPluggableFilterTranslator<IdmRequestIdentityRoleFilter> {

    @Autowired
    public IdmRequestIdentityRoleFilterTranslator(LookupService lookupService, ObjectMapper objectMapper) {
        super(lookupService, objectMapper);
    }

    @Override
    public IdmRequestIdentityRoleFilter transformInternal(IdmRequestIdentityRoleFilter filter, MultiValueMap<String, Object> parameters) {
        filter.setId(getParameterConverter().toUuid(parameters, "id"));
        filter.setIdentity(getParameterConverter().toEntityUuid(parameters, "identityId", IdmIdentityDto.class));
        filter.setIdentityContractId(getParameterConverter().toUuid(parameters, "identityContractId"));
        filter.setRoleId(getParameterConverter().toUuid(parameters, BaseRoleAssignmentFilter.PARAMETER_ROLE_ID));
        filter.setRoleText(getParameterConverter().toString(parameters, BaseRoleAssignmentFilter.PARAMETER_ROLE_TEXT));
        filter.setRoleEnvironments(getParameterConverter().toStrings(parameters, BaseRoleAssignmentFilter.PARAMETER_ROLE_ENVIRONMENT));
        filter.setRoleRequestId(getParameterConverter().toUuid(parameters, "roleRequestId"));
        filter.setDirectRoleId(getParameterConverter().toUuid(parameters, IdmBaseConceptRoleRequestFilter.DIRECT_ROLE_ID_PARAMETER));
        filter.setState(getParameterConverter().toEnum(parameters, "state", RoleRequestState.class));
        filter.setOnlyChanges(getParameterConverter().toBoolean(parameters, "onlyChanges", false));
        filter.setIncludeCandidates(getParameterConverter().toBoolean(parameters, "includeCandidates", false));
        filter.setIncludeCrossDomainsSystemsCount(getParameterConverter().toBoolean(parameters, "includeCrossDomainsSystemsCount", false));
        filter.setOwnerType(getParameterConverter().toClass(parameters, OwnerTypeFilter.OWNER_TYPE));
        filter.setDirectRole(getParameterConverter().toBoolean(parameters, "directRole"));
        filter.setAddEavMetadata(getParameterConverter().toBoolean(parameters, FormableFilter.PARAMETER_ADD_EAV_METADATA));
        filter.setOnlyAssignments(getParameterConverter().toBoolean(parameters, IdmRequestIdentityRoleFilter.ONLY_ASSIGNMENTS_PARAMETER));
        filter.setLoadAssignments(getParameterConverter().toBoolean(parameters, IdmRequestIdentityRoleFilter.LOAD_ASSIGNMENTS_PARAMETER));
        return filter;
    }

    @Override
    protected IdmRequestIdentityRoleFilter getEmptyFilter() {
        return new IdmRequestIdentityRoleFilter();
    }
}
