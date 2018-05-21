package eu.bcvsolutions.idm.core.eav.api.domain;

/**
 * Basic core faces types. Default face type is resolved by attribute's {@link PersistentType}. 
 * 
 * @author Radek Tomiška
 *
 */
public interface BaseFaceType {
  
	String TEXTAREA = "TEXTAREA";
	String RICHTEXTAREA = "RICHTEXTAREA";
	//
	String CURRENCY = "CURRENCY";
	//
	String IDENTITY_SELECT = "IDENTITY-SELECT";
	String ROLE_SELECT = "ROLE-SELECT";
	//
	String BOOLEAN_SELECT = "BOOLEAN-SELECT";
}
