package eu.bcvsolutions.idm.core.api.dto.filter;

import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.domain.RoleType;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.utils.ParameterConverter;

/**
 * Filter for roles.
 *
 * Codeable filter parameter can be used.
 *
 * @author Ondrej Kopr <kopr@xyxy.cz>
 * @author Radek Tomiška
 *
 */
public class IdmRoleFilter 
		extends DataFilter 
		implements CorrelationFilter, ExternalIdentifiableFilter, DisableableFilter, FormableFilter {

	/**
	 * Parent role identifier - find sub roles by role composition
	 */
	public static final String PARAMETER_PARENT = IdmTreeNodeFilter.PARAMETER_PARENT;
	//
	public static final String PARAMETER_ROLE_CATALOGUE = "roleCatalogue";
	public static final String PARAMETER_GUARANTEE = "guarantee";
	public static final String PARAMETER_ENVIRONMENT = "environment"; // list - OR
	public static final String PARAMETER_BASE_CODE = "baseCode";
	public static final String PARAMETER_IDENTITY_ROLE_ATTRIBUTE_DEF = "identityRoleAttributeDefinition";
	public static final String PARAMETER_ROLE_TYPE = "roleType";
	/**
	 * If true, then will add to count of systems for this role, which are in cross-domain group.
	 */
	public static final String PARAMETER_INCLUDE_CROSS_DOMAINS_SYSTEMS_COUNT = "includeCrossDomainsSystemsCount";
	/**
	 * Roles, which are not placed in any catalogue folder.
	 * 
	 * @since 10.4.0
	 */
	public static final String PARAMETER_WITHOUT_CATALOGUE = "withoutCatalogue";

	public IdmRoleFilter() {
		this(new LinkedMultiValueMap<>());
	}

	public IdmRoleFilter(MultiValueMap<String, Object> data) {
		this(data, null);
	}

	public IdmRoleFilter(MultiValueMap<String, Object> data, ParameterConverter parameterConverter) {
		super(IdmRoleDto.class, data, parameterConverter);
	}

	public RoleType getRoleType() {
		return getParameterConverter().toEnum(getData(), PARAMETER_ROLE_TYPE, RoleType.class);
	}

	public void setRoleType(RoleType roleType) {
		set(PARAMETER_ROLE_TYPE, roleType);
	}

	public UUID getRoleCatalogueId() {
		return getParameterConverter().toUuid(getData(), PARAMETER_ROLE_CATALOGUE);
	}

	public void setRoleCatalogueId(UUID roleCatalogueId) {
		set(PARAMETER_ROLE_CATALOGUE, roleCatalogueId);
	}

	public UUID getAttributeFormDefinitionId() {
		return getParameterConverter().toUuid(getData(), PARAMETER_IDENTITY_ROLE_ATTRIBUTE_DEF);
	}

	public void setAttributeFormDefinitionId(UUID id) {
		set(PARAMETER_IDENTITY_ROLE_ATTRIBUTE_DEF, id);
	}

	public String getEnvironment() {
    	return getParameterConverter().toString(getData(), PARAMETER_ENVIRONMENT);
	}

    public void setEnvironment(String environment) {
    	if (StringUtils.isEmpty(environment)) {
    		remove(PARAMETER_ENVIRONMENT);
    	} else {
    		put(PARAMETER_ENVIRONMENT, Lists.newArrayList(environment));
    	}
	}

    public List<String> getEnvironments() {
		return getParameterConverter().toStrings(getData(), PARAMETER_ENVIRONMENT);
	}

	public void setEnvironments(List<String> environments) {
    	put(PARAMETER_ENVIRONMENT, environments);
	}

	public String getBaseCode() {
		return getParameterConverter().toString(getData(), PARAMETER_BASE_CODE);
	}

	public void setBaseCode(String baseCode) {
		set(PARAMETER_BASE_CODE, baseCode);
	}

	public UUID getGuaranteeId() {
		return getParameterConverter().toUuid(getData() ,PARAMETER_GUARANTEE);
	}

	public void setGuaranteeId(UUID guaranteeId) {
		set(PARAMETER_GUARANTEE, guaranteeId);
	}

	/**
	 * @since 9.4.0
	 * @return
	 */
	public UUID getParent() {
		return getParameterConverter().toUuid(getData(), PARAMETER_PARENT);
	}

	/**
	 * @since 9.4.0
	 * @param parent
	 */
	public void setParent(UUID parent) {
		set(PARAMETER_PARENT, parent);
	}
	
	/**
	 * Roles, which are not placed in any catalogue folder.
	 * 
	 * @since 10.4.0
	 * @return without catalogue
	 */
	public Boolean getWithoutCatalogue() {
		return getParameterConverter().toBoolean(getData(), PARAMETER_WITHOUT_CATALOGUE);
	}
	
	/**
	 * Roles, which are not placed in any catalogue folder.
	 * 
	 * @param withoutCatalogue without catalogue
	 */
	public void setWithoutCatalogue(Boolean withoutCatalogue) {
		set(PARAMETER_WITHOUT_CATALOGUE, withoutCatalogue);
	}
	
	/**
	 * If true, then will add to count of systems for this role, which are in cross-domain group.
	 */
	public Boolean getIncludeCrossDomainsSystemsCount() {
		return getParameterConverter().toBoolean(getData(), PARAMETER_INCLUDE_CROSS_DOMAINS_SYSTEMS_COUNT);
	}
	
	/**
	 * If true, then will add to count of systems for this role, which are in cross-domain group.
	 */
	public void setIncludeCrossDomainsSystemsCount(Boolean withoutCatalogue) {
		set(PARAMETER_INCLUDE_CROSS_DOMAINS_SYSTEMS_COUNT, withoutCatalogue);
	}
}
