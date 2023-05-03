package eu.bcvsolutions.idm.test.api;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Function;

import eu.bcvsolutions.idm.core.api.config.flyway.IdmFlywayMigrationStrategy;
import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleComparison;
import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleType;
import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.domain.Identifiable;
import eu.bcvsolutions.idm.core.api.domain.IdmScriptCategory;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.RecursionType;
import eu.bcvsolutions.idm.core.api.domain.ScriptAuthorityType;
import eu.bcvsolutions.idm.core.api.dto.IdmAuthorizationPolicyDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeRuleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmContractGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmContractPositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmContractSliceDto;
import eu.bcvsolutions.idm.core.api.dto.IdmContractSliceGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmDelegationDefinitionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIncompatibleRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmPasswordDto;
import eu.bcvsolutions.idm.core.api.dto.IdmProfileDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCompositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmScriptAuthorityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmScriptDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeTypeDto;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.api.event.EntityEventProcessor;
import eu.bcvsolutions.idm.core.api.repository.filter.FilterBuilder;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeTypeService;
import eu.bcvsolutions.idm.core.api.service.ReadDtoService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmProcessedTaskItemDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmScheduledTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskExecutor;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.domain.GroupPermission;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;
import eu.bcvsolutions.idm.core.security.api.dto.LoginDto;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationEvaluator;

/**
 * Creates common test entities
 *
 * @author Radek Tomiška
 *
 */
public interface TestHelper {
	
	String DEFAULT_AUTOMATIC_ROLE_NAME = "default";
	String ADMIN_USERNAME = "admin";
	String ADMIN_PASSWORD = "admin";
	String ADMIN_ROLE = "superAdminRole";
	String DEFAULT_PASSWORD = "password";
	String HAL_CONTENT_TYPE = "application/hal+json;charset=UTF-8";
	
	/**
	 * Login as test admin
	 *  
	 * @return
	 */
	LoginDto loginAdmin();
	
	/**
	 * Login as given identity.
	 * Identity has to exists and has to set password as guarded string, assigned identity roles and permissions will be used.
	 * 
	 * @param identity
	 * @return
	 */
	LoginDto login(IdmIdentityDto identity);
	
	/**
	 * Login as given identity.
	 * Identity has to exists, assigned identity roles and permissions will be used.
	 * 
	 * @param username
	 * @param password
	 */
	LoginDto login(String username, String password);
	
	/**
	 * Login as given identity.
	 * Identity has to exists, assigned identity roles and permissions will be used.
	 * 
	 * @param username
	 * @param password
	 * @return
	 */
	LoginDto login(String username, GuardedString password);
	
	/**
	 * Logout current logged identity ~ clear secutity context
	 */
	void logout();
	
	/**
	 * Get dto service from context
	 * 
	 * @param dtoServiceType
	 * @return
	 */
	<T extends ReadDtoService<?, ?>> T getService(Class<T> dtoServiceType);

	/**
	 * Creates random unique name
	 *
	 * @return
	 */
	String createName();

	/**
	 * Creates test identity with random username  and default "password".
	 * The password is set back to identity after save.
	 *
	 * @return create identity with default "password".
	 * @see #createIdentityOnly()
	 */
	IdmIdentityDto createIdentity();
	
	/**
	 * Creates test identity with random username, WITHOUT default contract and WITHOUT "password" => identity cannot login in tests, 
	 * but it's quicker (saving password takes ~800ms => bcrypt).
	 *
	 * @return create identity without password
	 * @since 10.6.0
	 */
	IdmIdentityDto createIdentityOnly();

	/**
	 * Creates test identity with given username and default "password".
	 * The password is set back to identity after save.
	 *
	 * @param username
	 * @return
	 */
	IdmIdentityDto createIdentity(String username);
	
	/**
	 * Creates test identity with random username and given password.
	 * The password is set back to identity after save.
	 *
	 * @param password [optional] when password is not given, then identity password will not be saved - useful when password is not needed
	 * @return
	 */
	IdmIdentityDto createIdentity(GuardedString password);
	
	/**
	 * Creates test identity given username and given password.
	 * The password is set back to identity after save.
	 *
	 * @param username [optional] username will be generated, if is not given
	 * @param password [optional] when password is not given, then identity password will not be saved - usefull when password is not needed
	 * @return
	 */
	IdmIdentityDto createIdentity(String username, GuardedString password);

	/**
	 * Creates test RoleCatalogue with random code and name
	 *
	 * @return
	 */
	IdmRoleCatalogueDto createRoleCatalogue();

	/**
	 * Creates test RoleCatalogue with given code = name
	 *
	 * @param code
	 * @return
	 */
	IdmRoleCatalogueDto createRoleCatalogue(String code);

	/**
	 * Creates test RoleCatalogue with given code = name and parent.
	 * 
	 * @param code
	 * @param parentId
	 * @return
	 */
	IdmRoleCatalogueDto createRoleCatalogue(String code, UUID parentId);
	
	/**
	 * assign role catalogue to role
	 * 
	 * @param role
	 * @param catalogue
	 * @return
	 */
	IdmRoleCatalogueRoleDto createRoleCatalogueRole(IdmRoleDto role, IdmRoleCatalogueDto catalogue);

	/**
	 * Deletes identity
	 *
	 * @param id
	 */
	void deleteIdentity(UUID id);

	/**
	 * Creates tree type with random name and code
	 * @return
	 */
	IdmTreeTypeDto createTreeType();

	/**
	 * Creates tree type with given name = code
	 *
	 * @param code
	 * @return
	 */
	IdmTreeTypeDto createTreeType(String code);
	
	/**
	 * Returns configured default tree type.
	 * 
	 * @return
	 */
	IdmTreeTypeDto getDefaultTreeType();

	/**
	 * Creates tree node with random name and code
	 *
	 * @return
	 */
	IdmTreeNodeDto createTreeNode();

	/**
	 * Creates tree node under default tree structure
	 *
	 * @see {@link IdmTreeTypeService#getDefaultTreeType()}
	 * @param name
	 * @param parent
	 * @return
	 */
	IdmTreeNodeDto createTreeNode(String code, IdmTreeNodeDto parent);
	IdmTreeNodeDto createTreeNode(IdmTreeTypeDto treeType, String code, IdmTreeNodeDto parent);
	IdmTreeNodeDto createTreeNode(IdmTreeTypeDto treeType, IdmTreeNodeDto parent);

	void deleteTreeNode(UUID id);

	void deleteTreeType(UUID id);

	/**
	 * Creates role with random code.
	 *
	 * @return
	 */
	IdmRoleDto createRole();

	/**
	 * Creates role with given code (name will be the same as code).
	 *
	 * @param code [optional] will be generated, if not given
	 * @return
	 */
	IdmRoleDto createRole(String code);
	
	/**
	 * Creates role with random code and given priority.
	 *
	 * @return
	 */
	IdmRoleDto createRole(int priority);

	/**
	 * Creates role with given id and code (name will be the same as code)
	 *
	 * @param id [optional] if no id is given, then new id is generated
	 * @param code [optional] will be generated, if not given
	 * @return
	 */
	IdmRoleDto createRole(UUID id, String code);
	
	/**
	 * Creates role with given id, base code and environment (name will be the same as code)
	 *
	 * @param id [optional] if no id is given, then new id is generated
	 * @param baseCode [optional] will be generated, if not given
	 * @param environment [optional]
	 * @return
	 */
	IdmRoleDto createRole(UUID id, String baseCode, String environment);
	
	/**
	 * Creates role with given id, base code and environment (name will be the same as code)
	 *
	 * @param id [optional] if no id is given, then new id is generated
	 * @param baseCode [optional] will be generated, if not given
	 * @param environment [optional]
	 * @param priority (default 0)
	 * @return
	 */
	IdmRoleDto createRole(UUID id, String baseCode, String environment, Integer priority);
	
	/**
	 * Creates role composition
	 * 
	 * @param superior
	 * @param sub
	 * @return
	 */
	IdmRoleCompositionDto createRoleComposition(IdmRoleDto superior, IdmRoleDto sub);
	
	/**
	 * Creates incompatible role
	 * 
	 * @param superior
	 * @param sub
	 * @return
	 * @since 9.4.0
	 */
	IdmIncompatibleRoleDto createIncompatibleRole(IdmRoleDto superior, IdmRoleDto sub);
	
	/**
	 * Create role guarantee - identity
	 * 
	 * @param role
	 * @param guarantee
	 * @return
	 */
	IdmRoleGuaranteeDto createRoleGuarantee(IdmRoleDto role, IdmIdentityDto guarantee);
	
	/**
	 * Create role guarantee - identity
	 * 
	 * @param role
	 * @param guarantee
	 * @param guaranteeType
	 * @return
	 */
	IdmRoleGuaranteeDto createRoleGuarantee(IdmRoleDto role, IdmIdentityDto guarantee, String guaranteeType);
	
	/**
	 * Create role guarantee - role
	 * 
	 * @param role
	 * @param guarantee
	 * @return
	 */
	IdmRoleGuaranteeRoleDto createRoleGuaranteeRole(IdmRoleDto role, IdmRoleDto guarantee);
	
	/**
	 * Create role guarantee - role
	 * 
	 * @param role
	 * @param guarantee
	 * @param guaranteeType
	 * @return
	 */
	IdmRoleGuaranteeRoleDto createRoleGuaranteeRole(IdmRoleDto role, IdmRoleDto guarantee, String guaranteeType);
	
	/**
	 * Deletes role
	 *
	 * @param id
	 */
	void deleteRole(UUID id);

	/**
	 * Creates automatic role by tree structure without recursion ({@link RecursionType#NO}).
	 *
	 * @param role
	 * @param treeNode
	 * @param skipLongRunningTask
	 * @return
	 */
	IdmRoleTreeNodeDto createRoleTreeNode(IdmRoleDto role, IdmTreeNodeDto treeNode, boolean skipLongRunningTask);
	
	/**
	 * Creates automatic role by tree structure.
	 * 
	 * @param role
	 * @param treeNode
	 * @param recursionType [optional] {@link RecursionType#NO} is used as default
	 * @param skipLongRunningTask
	 * @return
	 * @since 10.4.0
	 */
	IdmRoleTreeNodeDto createRoleTreeNode(
			IdmRoleDto role,
			IdmTreeNodeDto treeNode,
			RecursionType recursionType,
			boolean skipLongRunningTask);

	/**
	 * Creates simple uuid permission evaluator authorization policy.
	 * 
	 * @param roleId - assigned role
	 * @param authorizableEntity added permission to
	 * @param permission
	 * @return
	 */
	IdmAuthorizationPolicyDto createUuidPolicy(UUID roleId, UUID authorizableEntity, BasePermission... permission);
	
	/**
	 * Creates simple uuid permission evaluator authorization policy,
	 * 
	 * @param role assigned role
	 * @param authorizableEntity added permission to
	 * @param permission
	 * @return
	 */
	IdmAuthorizationPolicyDto createUuidPolicy(IdmRoleDto role, Identifiable authorizableEntity, BasePermission... permission);

	/**
	 * Creates base permission evaluator authorization policy
	 *
	 * @param role
	 * @param permission
	 * @return
	 */
	IdmAuthorizationPolicyDto createBasePolicy(UUID role, BasePermission... permission);

	/**
	 * Creates base permission evaluator authorization policy
	 *
	 * @param role
	 * @param groupPermission
	 * @param authorizableType
	 * @param permission
	 * @return
	 */
	IdmAuthorizationPolicyDto createBasePolicy(UUID role, GroupPermission groupPermission, Class<?> authorizableType, BasePermission... permission);

	/**
	 * Creates authorization policy
	 * 
	 * @param role
	 * @param groupPermission
	 * @param authorizableType
	 * @param evaluatorType
	 * @param permission
	 * @return
	 */
	IdmAuthorizationPolicyDto createAuthorizationPolicy(
			UUID role, 
			GroupPermission groupPermission, 
			Class<? extends AbstractEntity> authorizableType, 
			Class<? extends AuthorizationEvaluator<? extends Identifiable>> evaluatorType, 
		    BasePermission... permission);
	
	/**
	 * Creates authorization policy
	 * 
	 * @param role
	 * @param groupPermission
	 * @param authorizableType
	 * @param evaluatorType
	 * @param evaluatorProperties
	 * @param permission
	 * @return
	 */
	IdmAuthorizationPolicyDto createAuthorizationPolicy(
			UUID role, 
			GroupPermission groupPermission, 
			Class<? extends AbstractEntity> authorizableType, 
			Class<? extends AuthorizationEvaluator<? extends Identifiable>> evaluatorType, 
			ConfigurationMap evaluatorProperties,
		    BasePermission... permission);

	/**
	 * Creates assigned identity's role directly (without approving etc.)
	 *
	 * @param identity
	 * @param role
	 * @return
	 */
	IdmIdentityRoleDto createIdentityRole(IdmIdentityDto identity, IdmRoleDto role);
	
	/**
	 * Creates assigned identity's role directly (without approving etc.)
	 * 
	 * @param identity
	 * @param role
	 * @param validFrom
	 * @param validTill
	 * @return
	 */
	IdmIdentityRoleDto createIdentityRole(IdmIdentityDto identity, IdmRoleDto role, LocalDate validFrom, LocalDate validTill);

	/**
	 * Creates assigned identity's role directly (without approving etc.)
	 *
	 * @param identityContract
	 * @param role
	 * @return
	 */
	IdmIdentityRoleDto createIdentityRole(IdmIdentityContractDto identityContract, IdmRoleDto role);
	
	/**
	 * Creates assigned identity's role directly (without approving etc.)
	 * 
	 * @param contractPosition
	 * @param role
	 * @return
	 */
	IdmIdentityRoleDto createIdentityRole(IdmContractPositionDto contractPosition, IdmRoleDto role);
	
	/**
	 * Creates assigned identity's role directly (without approving etc.)
	 * 
	 * @param identityContract
	 * @param role
	 * @param validFrom
	 * @param validTill
	 * @return
	 */
	IdmIdentityRoleDto createIdentityRole(IdmIdentityContractDto identityContract, IdmRoleDto role, LocalDate validFrom, LocalDate validTill);
	
	/**
	 * Creates assigned identity's role directly (without approving etc.)
	 * 
	 * @param contractPosition
	 * @param role
	 * @param validFrom
	 * @param validTill
	 * @return
	 */
	IdmIdentityRoleDto createIdentityRole(IdmContractPositionDto contractPosition, IdmRoleDto role, LocalDate validFrom, LocalDate validTill);

	/**
	 * Returns prime identity contract
	 *
	 * @param identityId
	 * @return
	 */
	IdmIdentityContractDto getPrimeContract(UUID identityId);
	
	/**
	 * Returns prime identity contract
	 * 
	 * @param identity
	 * @return
	 */
	IdmIdentityContractDto getPrimeContract(IdmIdentityDto identity);
	
	/**
	 * Get password for given identity
	 * 
	 * @param identity
	 * @return
	 */
	IdmPasswordDto getPassword(IdmIdentityDto identity);
	
	/**
	 * Creates simple identity contract slice
	 *
	 * @param identity
	 * @return
	 */
	IdmContractSliceDto createContractSlice(IdmIdentityDto identity);
	
	/**
	 * Creates identity contract slice on given position
	 * 
	 * @param identity
	 * @param position
	 * @param validFrom
	 * @param contractValidFrom
	 * @param contractValidTill
	 * @return
	 */
	IdmContractSliceDto createContractSlice(IdmIdentityDto identity, IdmTreeNodeDto position, LocalDate validFrom, LocalDate contractValidFrom, LocalDate contractValidTill);

	/**
	 *  Creates identity contract slice on given position
	 *  
	 * @param identity
	 * @param contractCode
	 * @param position
	 * @param validFrom
	 * @param contractValidFrom
	 * @param contractValidTill
	 * @return
	 */
	IdmContractSliceDto createContractSlice(IdmIdentityDto identity, String contractCode, IdmTreeNodeDto position,
			LocalDate validFrom, LocalDate contractValidFrom, LocalDate contractValidTill);
	
	/**
	 * Creates simple identity contract.
	 *
	 * @param identity
	 * @return
	 * @deprecated @since 10.6.0 use {@link #createContact(IdmIdentityDto)}
	 */
	@Deprecated
	IdmIdentityContractDto createIdentityContact(IdmIdentityDto identity);
	
	/**
	 * Creates simple identity contract.
	 *
	 * @param identity
	 * @return
	 * @since 10.6.0
	 */
	IdmIdentityContractDto createContract(IdmIdentityDto identity);
	
	/**
	 * Creates identity contract on given position
	 *
	 * @param identity
	 * @param position
	 * @return
	 * @deprecated @since 10.6.0 use {@link #createContract(IdmIdentityDto, IdmTreeNodeDto)}
	 */
	@Deprecated
	IdmIdentityContractDto createIdentityContact(IdmIdentityDto identity, IdmTreeNodeDto position);
	
	/**
	 * Creates identity contract on given position
	 *
	 * @param identity
	 * @param position
	 * @return
	 * @since 10.6.0
	 */
	IdmIdentityContractDto createContract(IdmIdentityDto identity, IdmTreeNodeDto position);

	/**
	 * Creates identity contract on given position.
	 *
	 * @param identity
	 * @param position
	 * @param validFrom
	 * @param validTill
	 * @return
	 * @deprecated @since 10.6.0 use {@link #createContract(IdmIdentityDto, IdmTreeNodeDto, LocalDate, LocalDate)}
	 */
	@Deprecated
	IdmIdentityContractDto createIdentityContact(IdmIdentityDto identity, IdmTreeNodeDto position, LocalDate validFrom, LocalDate validTill);

	/**
	 * Creates identity contract on given position.
	 *
	 * @param identity
	 * @param position
	 * @param validFrom
	 * @param validTill
	 * @return
	 * @since 10.6.0
	 */
	IdmIdentityContractDto createContract(IdmIdentityDto identity, IdmTreeNodeDto position, LocalDate validFrom, LocalDate validTill);
	
	/**
	 * Deletes identity's contract.
	 *
	 * @param id
	 * @deprecated @since 10.6.0 use {@link #deleteContract(UUID)}
	 */
	@Deprecated 
	void deleteIdentityContact(UUID id);
	
	/**
	 * Deletes identity's contract.
	 *
	 * @param id
	 * @since 10.6.0
	 */
	void deleteContract(UUID id);
	
	/**
	 * Creates identity contract's guarantee
	 *
	 * @param identityContract
	 * @param guarantee
	 * @return
	 */
	IdmContractGuaranteeDto createContractGuarantee(IdmIdentityContractDto identityContract, IdmIdentityDto guarantee);

	/**
	 * Creates identity contract's guarantee
	 *
	 * @param identityContractId
	 * @param guaranteeId
	 * @return
	 */
	IdmContractGuaranteeDto createContractGuarantee(UUID identityContractId, UUID guaranteeId);
	
	/**
	 * Create contact position with random work position (tree node)
	 * 
	 * @param identityContractId
	 * @return
	 */
	IdmContractPositionDto createContractPosition(UUID identityContractId);
	
	/**
	 * Create contact position with given work position (tree node) and random name.
	 * 
	 * @param identityContractId
	 * @param treeNodeId
	 * @return
	 */
	IdmContractPositionDto createContractPosition(UUID identityContractId, UUID treeNodeId);
	
	/**
	 * Create contact position with random work position (tree node)
	 * 
	 * @param contract
	 * @return
	 */
	IdmContractPositionDto createContractPosition(IdmIdentityContractDto contract);
	
	/**
	 *  Create contact position with given work position (tree node) and random name.
	 *  
	 * @param contract
	 * @param treeNode
	 * @return
	 */
	IdmContractPositionDto createContractPosition(IdmIdentityContractDto contract, IdmTreeNodeDto treeNode);
	
	/**
	 * Creates identity contract's guarantee slice
	 *
	 * @param identityContractId
	 * @param identityId
	 * @return
	 */
	IdmContractSliceGuaranteeDto createContractSliceGuarantee(UUID sliceId, UUID identityId);

	/**
	 * Create role request - request is not executed
	 * 
	 * @param identity - prime identity contract is used
	 * @param roles
	 * @return
	 */
	IdmRoleRequestDto createRoleRequest(IdmIdentityDto identity, IdmRoleDto... roles);
	
	/**
	 * Create role request - request is not executed
	 * 
	 * @param contract
	 * @param roles
	 * @return
	 */
	IdmRoleRequestDto createRoleRequest(IdmIdentityContractDto contract, IdmRoleDto... roles);
	
	/***
	 * Create role request - request is not executed
	 * 
	 * @param contract
	 * @param operation
	 * @param roles
	 * @return
	 */
	IdmRoleRequestDto createRoleRequest(IdmIdentityContractDto contract, ConceptRoleRequestOperation operation,
			IdmRoleDto... roles);
	
	/***
	 * Create role request - request is not executed
	 * 
	 * @param identity
	 * @param operation
	 * @param roles
	 * @return
	 */
	IdmRoleRequestDto createRoleRequest(IdmIdentityDto identity, ConceptRoleRequestOperation operation,
			IdmRoleDto... roles);

	
	/**
	 * Assign roles through role request (manual, execute immediately)
	 *
	 * @param contract
	 * @param roles roles to add
	 * @return
	 */
	IdmRoleRequestDto assignRoles(IdmIdentityContractDto contract, IdmRoleDto... roles);

	/**
	* Assign roles through role request (manual, execute immediately)
	* @param contract
	* @param startInNewTransaction
	* @param roles
	* @return
	*/
 	IdmRoleRequestDto assignRoles(IdmIdentityContractDto contract, boolean startInNewTransaction, IdmRoleDto... roles);
 	
 	/**
 	 * Execute created role request
 	 * 
 	 * @param roleRequest
 	 * @param startInNewTransaction
 	 * @return
 	 */
 	IdmRoleRequestDto executeRequest(IdmRoleRequestDto roleRequest, boolean startInNewTransaction);
 	
 	/**
 	 * Execute created role request "the old way" - synchronous role requests are prohibited now.
 	 * Use {@link IdmRoleRequestService#startRequestInternal(eu.bcvsolutions.idm.core.api.event.EntityEvent)
 	 * instead in production code.
 	 * 
 	 * @param roleRequest
 	 * @param startInNewTransaction
 	 * @param immediate - synchronously
 	 * @return
 	 */
 	IdmRoleRequestDto executeRequest(IdmRoleRequestDto roleRequest, boolean startInNewTransaction, boolean immediate);
 	
 	/**
 	 * Start role request "the old way" - synchronous role requests are prohibited now.
 	 * Use {@link IdmRoleRequestService#startRequestInternal(eu.bcvsolutions.idm.core.api.event.EntityEvent)
 	 * instead in production code.
 	 * 
 	 * @param roleRequest
 	 * @param checkRight
 	 * @param immediate
 	 * @return
 	 * @since 11.1.0
 	 */
 	IdmRoleRequestDto startRequestInternal(IdmRoleRequestDto roleRequest, boolean checkRight, boolean immediate);

	/**
	 * Prepare processed item instance by given LRT
	 *
	 * @param lrt IdmLongRunningTaskDto
	 * @return
	 */
	IdmProcessedTaskItemDto prepareProcessedItem(IdmLongRunningTaskDto lrt);

	/**
	 * Prepare processed item instance by given scheduled task
	 *
	 * @param d IdmScheduledTaskDto
	 * @return
	 */
	IdmProcessedTaskItemDto prepareProcessedItem(IdmScheduledTaskDto d);

	/**
	 * Prepare processed item instance by given scheduled task and given state
	 *
	 * @param d IdmScheduledTaskDto
	 * @param state OperationState
	 * @return
	 */
	IdmProcessedTaskItemDto prepareProcessedItem(IdmScheduledTaskDto d, OperationState state);

	/**
	 * Disables / enables given configuration property
	 *
	 * @param configurationPropertyName
	 */
	void setConfigurationValue(String configurationPropertyName, boolean value);
	
	/**
	 * Sets configuration value.
	 * 
	 * @param configurationPropertyName
	 * @param value
	 */
	void setConfigurationValue(String configurationPropertyName, String value);
	
	/**
	 * Delete configuration value.
	 * 
	 * @param configurationPropertyName
	 * @since 11.2.0
	 */
	void deleteConfigurationValue(String configurationPropertyName);
	
	/**
	 * Enable long running task and event asynchronous processing.
	 * 
	 * @since 11.1.0
	 */
	void enableAsynchronousProcessing();
	
	/**
	 * Disable long running task and event asynchronous processing => synchronous processing.
	 * 
	 * @since 11.1.0
	 */
	void disableAsynchronousProcessing();

	/**
	 * Enables given processor
	 *
	 * @param processorType
	 */
	void enable(Class<? extends EntityEventProcessor<?>> processorType);
	
	/**
	 * Enables given processor
	 *
	 * @param processorId - processor bean name
	 */
	void enableProcessor(String processorId);

	/**
	 * Disables given processor
	 *
	 * @param processorType
	 */
	void disable(Class<? extends EntityEventProcessor<?>> processorType);

	/**
	 * Disables given processor
	 *
	 * @param processorId - processor bean name
	 */
	void disableProcessor(String processorId);
	
	/**
	 * Enables given filter (filter will be used ~ will be effective and enabled).
	 *
	 * @param processorType
	 */
	void enableFilter(Class<? extends FilterBuilder<?, ?>> filterType);

	/**
	 * Disables given filter.
	 * Lookout: disable filter by configuration.
	 *
	 * @param processorType
	 */
	void disableFilter(Class<? extends FilterBuilder<?, ?>> filterType);
	
	/**
	 * Enable module
	 * 
	 * @param moduleId
	 */
	void enableModule(String moduleId);
	
	/**
	 * Disable module
	 * 
	 * @param moduleId
	 */
	void disableModule(String moduleId);

	/**
	 * Wait for result - usable for asynchronous tests
	 *
	 * @param continueFunction [optional] continue by default, until iterationCount is complete. If continueFunction is given, then is iterated while function returns true.
	 */
	void waitForResult(Function<String, Boolean> continueFunction);
	
	/**
	 * Wait for result - usable for asynchronous tests
	 * 
	 * @param continueFunction [optional] continue by default, until iterationCount is complete. If continueFunction is given, then is iterated while function returns true.
	 * @param interationWaitMilis [optional] default 500ms
	 * @param iterationCount [optional] default 50 => max wait 500ms x 50 = 25s. Maximum is 300.
	 * @throws IllegalStateException if continue function is defined and timeout exceeds before function returns false (~ before complete)
	 */
	void waitForResult(Function<String, Boolean> continueFunction, Integer interationWaitMilis, Integer iterationCount);

	/**
	 * Create schedulable task instance
	 * 
	 * @return
	 */
	IdmScheduledTaskDto createSchedulableTask();
	
	/**
	 * Create eav attribute, wit given code, owner class and type
	 * 
	 * @param code
	 * @param clazz
	 * @param type
	 * @return
	 */
	IdmFormAttributeDto createEavAttribute(String code, Class<? extends Identifiable> clazz, PersistentType type);

	/**
	 * Create eav attribute, wit given code, owner id and type
	 *
	 * @param code
	 * @param formDefinitionId
	 * @param type
	 * @return
	 */
	IdmFormAttributeDto createEavAttribute(String code, UUID formDefinitionId, PersistentType type);
	
	/**
	 * Save value to eav with code
	 * 
	 * @param owner
	 * @param attribute
	 * @param clazz
	 * @param value
	 * @param type
	 */
	void setEavValue(Identifiable owner, IdmFormAttributeDto attribute, Class<? extends Identifiable> clazz, Serializable value, PersistentType type);
	
	/**
	 * Save value to eav with code
	 * 
	 * @param owner
	 * @param attribute
	 * @param clazz
	 * @param value
	 * @param type
	 * @param formDefinition
	 */
	void setEavValue(Identifiable owner, IdmFormAttributeDto attribute, Class<? extends Identifiable> clazz, Serializable value, PersistentType type, 
			IdmFormDefinitionDto formDefinition);
	
	/**
	 * Method create new automatic role by attribute for role id
	 * 
	 * @param roleId
	 * @return
	 */
	IdmAutomaticRoleAttributeDto createAutomaticRole(UUID roleId);
	
	/**
	 * Create new rule with given informations. See params.
	 * And remove concept state from automatic role by attribute, without recalculation!
	 * 
	 * @param automaticRoleId
	 * @param comparsion
	 * @param type
	 * @param attrName
	 * @param formAttrId
	 * @param value
	 * @return
	 */
	IdmAutomaticRoleAttributeRuleDto createAutomaticRoleRule(
			UUID automaticRoleId,
			AutomaticRoleAttributeRuleComparison comparsion, 
			AutomaticRoleAttributeRuleType type, 
			String attrName,
			UUID formAttrId, 
			String value);
	
	/**
	 * Creates automatic roles by tree structure without recursion.
	 * 
	 * @param role
	 * @param treeNode
	 * @return
	 */
	IdmRoleTreeNodeDto createAutomaticRole(IdmRoleDto role, IdmTreeNodeDto treeNode);
	
	/**
	 * Create or get identity profile
	 * 
	 * @param identity
	 * @return
	 */
	IdmProfileDto createProfile(IdmIdentityDto identity);

	/**
	 * Create or get delegation.
	 *
	 */
	IdmDelegationDefinitionDto createDelegation(IdmIdentityDto delegate, IdmIdentityDto delegator, BasePermission... permissions);

	/**
	 * Method recalculate automatic role for given ID.
	 *
	 * @param automaticRoleId
	 */
	void recalculateAutomaticRoleByAttribute(UUID automaticRoleId);
	
	/**
	 * Resolve dbName, which is used by {@Flyway} datasource.
	 * 
	 * @param flyway
	 * @return
	 * @since 10.1.0
	 * @see IdmFlywayMigrationStrategy
	 */
	String getDatabaseName();
	
	/**
	 * Underlying database is mssql. Usable 
	 * 
	 * @return true - underlying database is mssql.
	 * @since 10.1.0
	 * @see IdmFlywayMigrationStrategy
	 */
	boolean isDatabaseMssql();
	
	/**
	 *  Creates a form definition of given type and main flag
	 * 
	 * @param type
	 * @param isMain
	 * @return
	 */
	IdmFormDefinitionDto createFormDefinition(String type, boolean isMain);

	/**
	 * Creates a form definition of given type and man flag set to false
	 * 
	 * @param type
	 * @return
	 */
	IdmFormDefinitionDto createFormDefinition(String type);

	/**
	 * Method create and save {@link IdmScriptAuthority} for script id given in parameter
	 *
	 * @param scriptId
	 * @param type
	 * @param className
	 * @param service
	 * @return
	 */
	IdmScriptAuthorityDto createScriptAuthority(UUID scriptId, ScriptAuthorityType type, String className, String service);

	/**
	 * Creates script in given {@link IdmScriptCategory} with lines
	 *
	 * @param code
	 * @param category
	 * @param lines
	 * @return
	 */
	IdmScriptDto createScript(String code, IdmScriptCategory category, String ...lines);

	IdmLongRunningTaskDto createLongRunningTask(LongRunningTaskExecutor<?> executor);

	IdmRoleDto createRoleIfNotExists(String incompatibilityRoleTest);
}
