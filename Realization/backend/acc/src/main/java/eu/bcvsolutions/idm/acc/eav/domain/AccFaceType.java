package eu.bcvsolutions.idm.acc.eav.domain;

import eu.bcvsolutions.idm.core.eav.api.domain.BaseFaceType;

/**
 * Eav faces types registered in acc module
 * 
 * @author Radek Tomiška
 *
 */
public interface AccFaceType extends BaseFaceType {

	String SYSTEM_SELECT = "SYSTEM-SELECT"; // target system select
	String SYNCHRONIZATION_CONFIG_SELECT = "SYNCHRONIZATION-CONFIG-SELECT";
	String SYSTEM_MAPPING_ATTRIBUTE_FILTERED_SELECT = "SYSTEM-MAPPING-ATTRIBUTE-FILTERED-SELECT";
	
}
