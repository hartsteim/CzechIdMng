package eu.bcvsolutions.idm.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import eu.bcvsolutions.idm.core.eav.entity.IdmCodeListItem;
import eu.bcvsolutions.idm.core.eav.entity.IdmCodeListItemValue;
import eu.bcvsolutions.idm.core.eav.entity.IdmForm;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormValue;
import eu.bcvsolutions.idm.core.eav.repository.AbstractFormValueRepository;
import eu.bcvsolutions.idm.core.eav.service.impl.AbstractFormValueService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmConceptRoleRequest;
import eu.bcvsolutions.idm.core.model.entity.IdmContractSlice;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRole;
import eu.bcvsolutions.idm.core.model.entity.IdmTreeNode;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmConceptRoleRequestFormValue;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmContractSliceFormValue;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmIdentityContractFormValue;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmIdentityFormValue;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmIdentityRoleFormValue;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmRoleFormValue;
import eu.bcvsolutions.idm.core.model.entity.eav.IdmTreeNodeFormValue;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;

/**
 * Configuration for eav.
 * 
 * @author Radek Tomiška
 *
 */
@Configuration
public class FormableConfiguration {

	/**
	 * Eav attributes for identity.
	 * 
	 * @param repository
	 * @param confidentialStorage
	 * @return
	 */
	@Bean
	public AbstractFormValueService<IdmIdentity, IdmIdentityFormValue> identityFormValueService(
			AbstractFormValueRepository<IdmIdentity, IdmIdentityFormValue> repository) {
		
		return new AbstractFormValueService<IdmIdentity, IdmIdentityFormValue>(repository) {
			
			/**
			 * Identity form values supports authorization policies.
			 */
			@Override
			public AuthorizableType getAuthorizableType() {
				return new AuthorizableType(CoreGroupPermission.FORMVALUE, getEntityClass());
			}
		};
	}
	
	
	/**
	 * Eav attributes for role.
	 * 
	 * @param repository
	 * @param confidentialStorage
	 * @return
	 */
	@Bean
	public AbstractFormValueService<IdmRole, IdmRoleFormValue> roleFormValueService(
			AbstractFormValueRepository<IdmRole, IdmRoleFormValue> repository) {
		return new AbstractFormValueService<IdmRole, IdmRoleFormValue>(repository) {
			/**
			 * Supports authorization policies.
			 */
			@Override
			public AuthorizableType getAuthorizableType() {
				return new AuthorizableType(CoreGroupPermission.FORMVALUE, getEntityClass());
			}
		};
	}
	
	/**
	 * Eav attributes for tree node.
	 * 
	 * @param repository
	 * @param confidentialStorage
	 * @return
	 */
	@Bean
	public AbstractFormValueService<IdmTreeNode, IdmTreeNodeFormValue> treeNodeFormValueService(
			AbstractFormValueRepository<IdmTreeNode, IdmTreeNodeFormValue> repository) {
		return new AbstractFormValueService<IdmTreeNode, IdmTreeNodeFormValue>(repository) {
			/**
			 * Supports authorization policies.
			 */
			@Override
			public AuthorizableType getAuthorizableType() {
				return new AuthorizableType(CoreGroupPermission.FORMVALUE, getEntityClass());
			}
		};
	}
	
	/**
	 * Eav attributes for identity contracts.
	 * 
	 * @param repository
	 * @param confidentialStorage
	 * @return
	 */
	@Bean
	public AbstractFormValueService<IdmIdentityContract, IdmIdentityContractFormValue> identityContractFormValueService(
			AbstractFormValueRepository<IdmIdentityContract, IdmIdentityContractFormValue> repository) {
		return new AbstractFormValueService<IdmIdentityContract, IdmIdentityContractFormValue>(repository) {
			
			/**
			 * Contract form values supports authorization policies.
			 */
			@Override
			public AuthorizableType getAuthorizableType() {
				return new AuthorizableType(CoreGroupPermission.FORMVALUE, getEntityClass());
			}
		};
	}
	
	/**
	 * Eav attributes for contract time slices.
	 * 
	 * @param repository
	 * @param confidentialStorage
	 * @return
	 */
	@Bean
	public AbstractFormValueService<IdmContractSlice, IdmContractSliceFormValue> contractSliceFormValueService(
			AbstractFormValueRepository<IdmContractSlice, IdmContractSliceFormValue> repository) {
		return new AbstractFormValueService<IdmContractSlice, IdmContractSliceFormValue>(repository) {
			/**
			 * Supports authorization policies.
			 */
			@Override
			public AuthorizableType getAuthorizableType() {
				return new AuthorizableType(CoreGroupPermission.FORMVALUE, getEntityClass());
			}
		};
	}
	
	/**
	 * Eav attributes for common eav form:
	 * - persists filters, configurable properties, etc.
	 * 
	 * @param repository
	 * @param confidentialStorage
	 * @return
	 */
	@Bean
	public AbstractFormValueService<IdmForm, IdmFormValue> formValueService(
			AbstractFormValueRepository<IdmForm, IdmFormValue> repository) {
		return new AbstractFormValueService<IdmForm, IdmFormValue>(repository) {
		};
	}
	
	/**
	 * Eav attributes for codelists.
	 * 
	 * @param repository
	 * @param confidentialStorage
	 * @return
	 */
	@Bean
	public AbstractFormValueService<IdmCodeListItem, IdmCodeListItemValue> codeListItemValueService(
			AbstractFormValueRepository<IdmCodeListItem, IdmCodeListItemValue> repository) {
		return new AbstractFormValueService<IdmCodeListItem, IdmCodeListItemValue>(repository) {
			/**
			 * Supports authorization policies.
			 */
			@Override
			public AuthorizableType getAuthorizableType() {
				return new AuthorizableType(CoreGroupPermission.FORMVALUE, getEntityClass());
			}
		};
	}
	
	/**
	 * Eav attributes for identity role.
	 * 
	 * @param repository
	 * @param confidentialStorage
	 * @return
	 */
	@Bean
	public AbstractFormValueService<IdmIdentityRole, IdmIdentityRoleFormValue> identityRoleFormValueService(
			AbstractFormValueRepository<IdmIdentityRole, IdmIdentityRoleFormValue> repository) {
		
		return new AbstractFormValueService<IdmIdentityRole, IdmIdentityRoleFormValue>(repository) {
			/**
			 * Supports authorization policies.
			 */
			@Override
			public AuthorizableType getAuthorizableType() {
				return new AuthorizableType(CoreGroupPermission.FORMVALUE, getEntityClass());
			}
		};
	}
	
	/**
	 * Eav attributes for concept role request.
	 * 
	 * @param repository
	 * @param confidentialStorage
	 * @return
	 */
	@Bean
	public AbstractFormValueService<IdmConceptRoleRequest, IdmConceptRoleRequestFormValue> conceptRoleRequestFormValueService(
			AbstractFormValueRepository<IdmConceptRoleRequest, IdmConceptRoleRequestFormValue> repository) {
		return new AbstractFormValueService<IdmConceptRoleRequest, IdmConceptRoleRequestFormValue>(repository) {
			/**
			 * Supports authorization policies.
			 */
			@Override
			public AuthorizableType getAuthorizableType() {
				return new AuthorizableType(CoreGroupPermission.FORMVALUE, getEntityClass());
			}
		};
	}
}
