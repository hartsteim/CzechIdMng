package eu.bcvsolutions.idm.core.config;

import java.util.List;
import java.util.concurrent.Executor;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import eu.bcvsolutions.idm.core.model.repository.filter.MonitoringIgnorableFilterBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;

import eu.bcvsolutions.idm.core.api.config.domain.ApplicationConfiguration;
import eu.bcvsolutions.idm.core.api.config.domain.ContractSliceConfiguration;
import eu.bcvsolutions.idm.core.api.config.domain.RoleConfiguration;
import eu.bcvsolutions.idm.core.api.config.domain.TreeConfiguration;
import eu.bcvsolutions.idm.core.api.domain.ModuleDescriptor;
import eu.bcvsolutions.idm.core.api.repository.filter.FilterBuilder;
import eu.bcvsolutions.idm.core.api.repository.filter.FilterManager;
import eu.bcvsolutions.idm.core.api.rest.lookup.DtoLookup;
import eu.bcvsolutions.idm.core.api.rest.lookup.DtoLookupByExample;
import eu.bcvsolutions.idm.core.api.rest.lookup.EntityLookup;
import eu.bcvsolutions.idm.core.api.rest.lookup.FormProjectionLookup;
import eu.bcvsolutions.idm.core.api.service.ConfidentialStorage;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.EntityStateManager;
import eu.bcvsolutions.idm.core.api.service.IdmAuthorizationPolicyService;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeRuleService;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeService;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmConfidentialStorageValueService;
import eu.bcvsolutions.idm.core.api.service.IdmConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmContractGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmContractPositionService;
import eu.bcvsolutions.idm.core.api.service.IdmEntityEventService;
import eu.bcvsolutions.idm.core.api.service.IdmEntityStateService;
import eu.bcvsolutions.idm.core.api.service.IdmGenerateValueService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmIncompatibleRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmPasswordHistoryService;
import eu.bcvsolutions.idm.core.api.service.IdmPasswordPolicyService;
import eu.bcvsolutions.idm.core.api.service.IdmPasswordService;
import eu.bcvsolutions.idm.core.api.service.IdmProfileService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCompositionService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.IdmTokenService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeTypeService;
import eu.bcvsolutions.idm.core.api.service.LoggerManager;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.service.ModuleService;
import eu.bcvsolutions.idm.core.api.service.thin.IdmIdentityRoleThinService;
import eu.bcvsolutions.idm.core.config.domain.DefaultApplicationConfiguration;
import eu.bcvsolutions.idm.core.config.domain.DefaultContractSliceConfiguration;
import eu.bcvsolutions.idm.core.config.domain.DefaultRoleConfiguration;
import eu.bcvsolutions.idm.core.config.domain.DefaultTreeConfiguration;
import eu.bcvsolutions.idm.core.eav.api.service.CodeListManager;
import eu.bcvsolutions.idm.core.eav.api.service.CommonFormService;
import eu.bcvsolutions.idm.core.eav.api.service.FormProjectionManager;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.FormValueService;
import eu.bcvsolutions.idm.core.eav.api.service.IdentityProjectionManager;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListItemService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormProjectionService;
import eu.bcvsolutions.idm.core.eav.repository.IdmCodeListItemRepository;
import eu.bcvsolutions.idm.core.eav.repository.IdmCodeListRepository;
import eu.bcvsolutions.idm.core.eav.repository.IdmFormAttributeRepository;
import eu.bcvsolutions.idm.core.eav.repository.IdmFormDefinitionRepository;
import eu.bcvsolutions.idm.core.eav.repository.IdmFormProjectionRepository;
import eu.bcvsolutions.idm.core.eav.repository.IdmFormRepository;
import eu.bcvsolutions.idm.core.eav.service.impl.DefaultCodeListManager;
import eu.bcvsolutions.idm.core.eav.service.impl.DefaultCommonFormService;
import eu.bcvsolutions.idm.core.eav.service.impl.DefaultFormProjectionManager;
import eu.bcvsolutions.idm.core.eav.service.impl.DefaultFormService;
import eu.bcvsolutions.idm.core.eav.service.impl.DefaultIdentityProjectionManager;
import eu.bcvsolutions.idm.core.eav.service.impl.DefaultIdmCodeListItemService;
import eu.bcvsolutions.idm.core.eav.service.impl.DefaultIdmCodeListService;
import eu.bcvsolutions.idm.core.eav.service.impl.DefaultIdmFormAttributeService;
import eu.bcvsolutions.idm.core.eav.service.impl.DefaultIdmFormDefinitionService;
import eu.bcvsolutions.idm.core.eav.service.impl.DefaultIdmFormProjectionService;
import eu.bcvsolutions.idm.core.ecm.api.config.AttachmentConfiguration;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.ecm.config.DefaultAttachmentConfiguration;
import eu.bcvsolutions.idm.core.ecm.repository.IdmAttachmentRepository;
import eu.bcvsolutions.idm.core.ecm.service.impl.DefaultAttachmentManager;
import eu.bcvsolutions.idm.core.model.repository.IdmAuthorizationPolicyRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmAutomaticRoleAttributeRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmAutomaticRoleAttributeRuleRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmConfidentialStorageValueRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmConfigurationRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmContractGuaranteeRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmContractPositionRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmEntityEventRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmEntityStateRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmGenerateValueRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmIdentityContractRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmIdentityRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmIdentityRoleRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmIncompatibleRoleRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmPasswordHistoryRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmPasswordPolicyRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmPasswordRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmProfileRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmRoleCompositionRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmRoleGuaranteeRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmRoleGuaranteeRoleRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmRoleRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmRoleTreeNodeRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmTokenRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmTreeNodeRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmTreeTypeRepository;
import eu.bcvsolutions.idm.core.model.repository.filter.DefaultFilterManager;
import eu.bcvsolutions.idm.core.model.repository.thin.IdmIdentityRoleThinRepository;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultConfigurationService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultEntityEventManager;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultEntityStateManager;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmAuthorizationPolicyService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmAutomaticRoleAttributeRuleService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmAutomaticRoleAttributeService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmConfidentialStorage;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmConfidentialStorageValueService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmContractGuaranteeService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmContractPositionService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmEntityEventService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmEntityStateService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmGenerateValueService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmIdentityContractService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmIdentityRoleService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmIdentityService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmIncompatibleRoleService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmPasswordHistoryService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmPasswordPolicyService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmPasswordService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmProfileService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmRoleCompositionService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmRoleGuaranteeRoleService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmRoleService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmRoleTreeNodeService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmTokenService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmTreeTypeService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultLookupService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultModuleService;
import eu.bcvsolutions.idm.core.model.service.impl.LogbackLoggerManager;
import eu.bcvsolutions.idm.core.model.service.thin.DefaultIdmIdentityRoleThinService;
import eu.bcvsolutions.idm.core.scheduler.api.config.SchedulerConfiguration;
import eu.bcvsolutions.idm.core.scheduler.api.service.IdmLongRunningTaskService;
import eu.bcvsolutions.idm.core.scheduler.api.service.IdmProcessedTaskItemService;
import eu.bcvsolutions.idm.core.scheduler.api.service.IdmScheduledTaskService;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.core.scheduler.repository.IdmLongRunningTaskRepository;
import eu.bcvsolutions.idm.core.scheduler.repository.IdmProcessedTaskItemRepository;
import eu.bcvsolutions.idm.core.scheduler.repository.IdmScheduledTaskRepository;
import eu.bcvsolutions.idm.core.scheduler.service.impl.DefaultIdmLongRunningTaskService;
import eu.bcvsolutions.idm.core.scheduler.service.impl.DefaultIdmProcessedTaskItemService;
import eu.bcvsolutions.idm.core.scheduler.service.impl.DefaultIdmScheduledTaskService;
import eu.bcvsolutions.idm.core.scheduler.service.impl.DefaultLongRunningTaskManager;
import eu.bcvsolutions.idm.core.security.api.service.AuthorizationManager;
import eu.bcvsolutions.idm.core.security.api.service.CryptService;
import eu.bcvsolutions.idm.core.security.api.service.EnabledEvaluator;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.api.service.TokenManager;
import eu.bcvsolutions.idm.core.security.service.impl.DefaultAuthorizationManager;
import eu.bcvsolutions.idm.core.security.service.impl.DefaultCryptService;
import eu.bcvsolutions.idm.core.security.service.impl.DefaultEnabledEvaluator;
import eu.bcvsolutions.idm.core.security.service.impl.DefaultSecurityService;
import eu.bcvsolutions.idm.core.security.service.impl.DefaultTokenManager;
import eu.bcvsolutions.idm.core.security.service.impl.IdmAuthorityHierarchy;

/**
 * Overridable core services initialization (configuration with lower order wins).
 * 
 * @author Radek Tomiška
 *
 */
@Order(0)
@Configuration
public class IdmServiceConfiguration {
	
	//
	// Spring environment
	@Autowired private ConfigurableEnvironment environment;
	@Autowired private ApplicationContext context;
	@Qualifier(SchedulerConfiguration.TASK_EXECUTOR_NAME)
	@Autowired private Executor executor;
	@Qualifier("entityManager")
	@Autowired private EntityManager entityManager;
	//
	// Spring Data repositories through interfaces - they are constructed automatically
	@Autowired private IdmConfigurationRepository configurationRepository;
	@Autowired private IdmIdentityRepository identityRepository;
	@Autowired private IdmRoleRepository roleRepository;
	@Autowired private IdmRoleCompositionRepository roleCompositionRepository;
	@Autowired private IdmIncompatibleRoleRepository incompatibleRoleRepository;
	@Autowired private IdmRoleTreeNodeRepository roleTreeNodeRepository;
	@Autowired private IdmTreeTypeRepository treeTypeRepository;
	@Autowired private IdmTreeNodeRepository treeNodeRepository;
	@Autowired private IdmAuthorizationPolicyRepository authorizationPolicyRepository;
	@Autowired private IdmConfidentialStorageValueRepository confidentialStorageValueRepository;
	@Autowired private IdmFormDefinitionRepository formDefinitionRepository;
	@Autowired private IdmFormProjectionRepository formProjectionRepository;
	@Autowired private IdmFormAttributeRepository formAttributeRepository;
	@Autowired private IdmLongRunningTaskRepository longRunningTaskRepository;
	@Autowired private IdmContractGuaranteeRepository contractGuaranteeRepository;
	@Autowired private IdmContractPositionRepository contractPositionRepository;
	@Autowired private IdmIdentityRoleRepository identityRoleRepository;
	@Autowired private IdmIdentityRoleThinRepository identityRoleThinRepository;
	@Autowired private IdmIdentityContractRepository identityContractRepository;
	@Autowired private IdmProcessedTaskItemRepository processedTaskRepository;
	@Autowired private IdmScheduledTaskRepository scheduledTaskRepository;
	@Autowired private IdmRoleGuaranteeRepository roleGuaranteeRepository;
	@Autowired private IdmRoleGuaranteeRoleRepository roleGuaranteeRoleRepository;
	@Autowired private IdmPasswordRepository passwordRepository;
	@Autowired private IdmPasswordPolicyRepository passwordPolicyRepository;
	@Autowired private IdmFormRepository formRepository;
	@Autowired private IdmCodeListRepository codeListRepository;
	@Autowired private IdmCodeListItemRepository codeListItemRepository;
	@Autowired private IdmAttachmentRepository attachmentRepository;
	@Autowired private IdmAutomaticRoleAttributeRepository automaticRoleAttributeRepository;
	@Autowired private IdmAutomaticRoleAttributeRuleRepository automaticRoleAttributeRuleRepository;
	@Autowired private IdmEntityEventRepository entityEventRepository;
	@Autowired private IdmEntityStateRepository entityStateRepository;
	@Autowired private IdmPasswordHistoryRepository passwordHistoryRepository;
	@Autowired private IdmTokenRepository tokenRepository;
	@Autowired private IdmProfileRepository profileRepository;
	@Autowired private IdmGenerateValueRepository generatedValueRepository;
	@Autowired private MonitoringIgnorableFilterBuilder monitoringIgnorableFilterBuilder;
	//
	// Auto registered beans (plugins)
	@Autowired private PluginRegistry<ModuleDescriptor, String> moduleDescriptorRegistry;
	@Autowired private List<? extends FormValueService<?>> formValueServices;
	@Autowired private List<? extends FilterBuilder<?, ?>> filterBuilders;
	@Autowired private List<? extends EntityLookup<?>> entityLookups;
	@Autowired private List<? extends DtoLookup<?>> dtoLookups;
	@Autowired private List<? extends DtoLookupByExample<?>> dtoLookupByExamples;
	@Autowired private List<? extends FormProjectionLookup<?>> formProjectionLookups;

	/**
	 * Crypt service for confidential storage
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(CryptService.class)
	public CryptService cryptService() {
		return new DefaultCryptService(environment);
	}
	
	/**
	 * Saving confidential values
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(ConfidentialStorage.class)
	public ConfidentialStorage confidentialStorage() {
		return new DefaultIdmConfidentialStorage(confidentialStorageValueRepository,  cryptService());
	}
	
	/**
	 * App configuration 
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmConfigurationService.class)
	public IdmConfigurationService configurationService() {
		return new DefaultConfigurationService(configurationRepository, confidentialStorage(), environment, entityEventManager());
	}
	
	/**
	 * App modules
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(ModuleService.class)
	public ModuleService moduleService() {
		return new DefaultModuleService(moduleDescriptorRegistry, configurationService());
	}
	
	/**
	 * IdM authority hierarchy (group and app admin wildcards)
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(name = "roleHierarchy")
	public RoleHierarchy roleHierarchy() {
	    return new IdmAuthorityHierarchy(moduleService());
	}
	
	/**
	 * Enabled modules and enabled configuration property helper
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(EnabledEvaluator.class)
	public EnabledEvaluator enabledEvaluator() {
		return new DefaultEnabledEvaluator(moduleService(), configurationService());
	}
	
	/**
	 * Entity <=> Dto lookup service
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(LookupService.class)
	public LookupService lookupService() {
		return new DefaultLookupService(context, entityManager, entityLookups, dtoLookups, dtoLookupByExamples);
	}
	
	/**
	 * Event manager for entity event publishing.
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(EntityEventManager.class)
	public EntityEventManager entityEventManager() {
		return new DefaultEntityEventManager();
	}
	
	/**
	 * State manager for entities.
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(EntityStateManager.class)
	public EntityStateManager entityStateManager() {
		return new DefaultEntityStateManager();
	}
	
	/**
	 * Entity changes
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmEntityEventService.class)
	public IdmEntityEventService entityEventService() {
		return new DefaultIdmEntityEventService(entityEventRepository, entityEventManager());
	}
	
	/**
	 * Entity states
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmEntityStateService.class)
	public IdmEntityStateService entityStateService() {
		return new DefaultIdmEntityStateService(entityStateRepository, entityEventManager());
	}
	
	/**
	 * Authentication security service
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(SecurityService.class)
	public SecurityService securityService() {
		return new DefaultSecurityService(roleHierarchy());
	}
	
	/**
	 * Token service - persists tokens (e.g. for authentication). Use {@link TokenManager}.
	 * 
	 * @see #tokenManager()
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmTokenService.class)
	public IdmTokenService tokenService() {
		return new DefaultIdmTokenService(tokenRepository, entityEventManager());
	}
	
	/**
	 * Token manager - create and dispose tokens.
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(TokenManager.class)
	public TokenManager tokenManager() {
		return new DefaultTokenManager();
	}
	
	/**
	 * Filter manager for repositories
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(FilterManager.class)
	public FilterManager filterManager() {
		return new DefaultFilterManager(context, filterBuilders);
	}
	
	/**
	 * Authorization manager - authorization security service
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(AuthorizationManager.class)
	public AuthorizationManager authorizationManager() {
		return new DefaultAuthorizationManager(context, authorizationPolicyService(), securityService(), moduleService());
	}
	
	/**
	 * EAV attributes
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmFormAttributeService.class)
	public IdmFormAttributeService formAttributeService() {
		return new DefaultIdmFormAttributeService(formAttributeRepository, entityEventManager(), formValueServices);
	}
	
	/**
	 * EAV definitions
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmFormDefinitionService.class)
	public IdmFormDefinitionService formDefinitionService() {
		return new DefaultIdmFormDefinitionService(formDefinitionRepository, entityEventManager());
	}
	
	/**
	 * Form projections.
	 * 
	 * @return default projection service
	 * @since 10.2.0
	 */
	@Bean
	@ConditionalOnMissingBean(IdmFormProjectionService.class)
	public IdmFormProjectionService formProjectionService() {
		return new DefaultIdmFormProjectionService(formProjectionRepository, entityEventManager());
	}
	
	/**
	 * EAV forms
	 * 
	 * @return
	 */
	@Bean(name = { "formService", "idmFormService" })
	@ConditionalOnMissingBean(FormService.class)
	public FormService formService() {
		return new DefaultFormService(formValueServices);
	}
	
	/**
	 * Common application configuration.
	 * 
	 * @return
	 * @since 11.1.0
	 */
	@Bean
	@ConditionalOnMissingBean(ApplicationConfiguration.class)
	public ApplicationConfiguration applicationConfiguration() {
		return new DefaultApplicationConfiguration();
	}

	/**
	 * Configuration for role agenda
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(RoleConfiguration.class)
	public RoleConfiguration roleConfiguration() {
		return new DefaultRoleConfiguration();
	}
	
	/**
	 * Configuration for contract slice agenda
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(ContractSliceConfiguration.class)
	public ContractSliceConfiguration contractSliceConfiguration() {
		return new DefaultContractSliceConfiguration();
	}
	
	/**
	 * Role service
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmRoleService.class)
	public IdmRoleService roleService() {
		return new DefaultIdmRoleService(
				roleRepository,
				entityEventManager(), 
				formService());
	}
	
	/**
	 * Role composition - defines business role
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmRoleCompositionService.class)
	public IdmRoleCompositionService roleCompositionService() {
		return new DefaultIdmRoleCompositionService(roleCompositionRepository, entityEventManager());
	}
	
	/**
	 * Incompatible role - defines Segregation of Duties
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmIncompatibleRoleService.class)
	public IdmIncompatibleRoleService incompatibleRoleService() {
		return new DefaultIdmIncompatibleRoleService(incompatibleRoleRepository, entityEventManager());
	}
	
	/**
	 * Service for assigning authorization evaluators to roles.
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmAuthorizationPolicyService.class)
	public IdmAuthorizationPolicyService authorizationPolicyService() {
		return new DefaultIdmAuthorizationPolicyService(authorizationPolicyRepository, roleService(),
				moduleService(), entityEventManager());
	}

	/**
	 * Persists long running tasks
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmLongRunningTaskService.class)
	public IdmLongRunningTaskService longRunningTaskService() {
		return new DefaultIdmLongRunningTaskService(longRunningTaskRepository, entityEventManager());
	}
	
	/**
	 * Long running task manager
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(LongRunningTaskManager.class)
	public LongRunningTaskManager longRunningTaskManager() {
		return new DefaultLongRunningTaskManager(
				longRunningTaskService(), 
				executor, 
				entityEventManager(),
				configurationService(), 
				securityService());
	}
	
	/**
	 * Identity service
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmIdentityService.class)
	public IdmIdentityService identityService() {
		return new DefaultIdmIdentityService(
				identityRepository, 
				formService(),
				entityEventManager());
	}
	
	/**
	 * Common form projection manager.
	 * 
	 * @return manager
	 * @since 10.3.0
	 */
	@Bean
	@ConditionalOnMissingBean(FormProjectionManager.class)
	public FormProjectionManager formProjectionManager() {
		return new DefaultFormProjectionManager(formProjectionLookups);
	}
	
	/**
	 * Identity projection.
	 * 
	 * @return manager
	 * @since 10.2.0
	 */
	@Bean
	@ConditionalOnMissingBean(IdentityProjectionManager.class)
	public IdentityProjectionManager identityProjectionService() {
		return new DefaultIdentityProjectionManager();
	}
	
	/***
	 * Profiles
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmProfileService.class)
	public IdmProfileService profileService() {
		return new DefaultIdmProfileService(
				profileRepository, 
				entityEventManager());
	}
	
	/**
	 * Saves and crypts identity's password
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmPasswordService.class)
	public IdmPasswordService passwordService() {
		return new DefaultIdmPasswordService(passwordRepository, passwordPolicyRepository, passwordHistoryService(), lookupService(), entityEventManager());
	}

	@Bean
	@ConditionalOnMissingBean(IdmPasswordPolicyService.class)
	public IdmPasswordPolicyService passwordPolicyService(IdmPasswordService passwordService) {
		return new DefaultIdmPasswordPolicyService(passwordPolicyRepository, entityEventManager(), securityService(), passwordService, passwordHistoryService());
	}
	
	/**
	 * Configuration for tree structures
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(TreeConfiguration.class)
	public TreeConfiguration treeConfiguration() {
		return new DefaultTreeConfiguration();
	}
	
	/**
	 * Tree type service
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmTreeTypeService.class)
	public IdmTreeTypeService treeTypeService() {
		return new DefaultIdmTreeTypeService(treeTypeRepository, configurationService(), treeConfiguration(), entityEventManager());
	}
	
	/**
	 * Automatic role service by treenode
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmRoleTreeNodeService.class)
	public IdmRoleTreeNodeService roleTreeNodeService(
			IdmRoleRequestService roleRequestService, 
			IdmConceptRoleRequestService conceptRoleRequestService) {
		return new DefaultIdmRoleTreeNodeService(roleTreeNodeRepository, treeNodeRepository, entityEventManager(), identityRoleService());
	}
	
	/**
	 * Automatic role service by attribute
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmAutomaticRoleAttributeService.class)
	public IdmAutomaticRoleAttributeService automaticRoleAttributeService(
			IdmIdentityService identityService,
			IdmRoleRequestService roleRequestService,
			IdmIdentityContractService identityContractService,
			IdmConceptRoleRequestService conceptRoleRequestService,
			EntityEventManager entityEventManager,
			FormService formService,
			IdmFormAttributeService formAttributeService,
			IdmAutomaticRoleAttributeRuleService automaticRoleAttributeRuleService,
			IdmIdentityRoleService identityRoleService) {
		return new DefaultIdmAutomaticRoleAttributeService(
				automaticRoleAttributeRepository,
				identityContractService,
				entityEventManager,
				formAttributeService,
				automaticRoleAttributeRuleService,
				identityRoleService,
				entityManager,
				longRunningTaskManager(),
				identityContractRepository);
	}
	
	@Bean
	@ConditionalOnMissingBean(IdmAutomaticRoleAttributeRuleService.class)
	public IdmAutomaticRoleAttributeRuleService automaticRoleAttributeRuleService() {
		return new DefaultIdmAutomaticRoleAttributeRuleService(
				automaticRoleAttributeRuleRepository,
				entityEventManager());
	}
	
	/**
	 * Identity's contract guarantee - manually defined  manager (if no tree structure is defined etc.)
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmContractGuaranteeService.class)
	public IdmContractGuaranteeService contractGuaranteeService() {
		return new DefaultIdmContractGuaranteeService(contractGuaranteeRepository, entityEventManager());
	}
	
	/**
	 * Identity's contract other positions
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmContractPositionService.class)
	public IdmContractPositionService contractPositionService() {
		return new DefaultIdmContractPositionService(contractPositionRepository, entityEventManager());
	}
	
	/**
	 * Assigned identity's contract
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmIdentityContractService.class)
	public IdmIdentityContractService identityContractService() {
		return new DefaultIdmIdentityContractService(
				identityContractRepository,
				formService(),
				entityEventManager());
	}
	
	/**
	 * Assigned identity's roles 
	 * 
	 * @return
	 */
	@Bean(name = {"identityRoleService", "idmIdentityRoleService"})
	@ConditionalOnMissingBean(IdmIdentityRoleService.class)
	public IdmIdentityRoleService identityRoleService() {
		return new DefaultIdmIdentityRoleService(identityRoleRepository,  formService(), entityEventManager());
	}
	
	/**
	 * Assigned identity's roles - thin variant.
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmIdentityRoleThinService.class)
	public IdmIdentityRoleThinService identityRoleThinService() {
		return new DefaultIdmIdentityRoleThinService(identityRoleThinRepository);
	}

	/**
	 * Processed long running task items service.
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmProcessedTaskItemService.class)
	public IdmProcessedTaskItemService processedTaskItemService() {
		return new DefaultIdmProcessedTaskItemService(processedTaskRepository, monitoringIgnorableFilterBuilder);
	}
	
	/**
	 * Scheduled tasks service for stateful tasks support.
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmScheduledTaskService.class)
	public IdmScheduledTaskService scheduledTaskService() {
		return new DefaultIdmScheduledTaskService(scheduledTaskRepository, processedTaskItemService(),
				longRunningTaskService());
	}
	
	/**
	 * Role guarantees - by identity
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmRoleGuaranteeService.class)
	public IdmRoleGuaranteeService roleGuaranteeService() {
		return new DefaultIdmRoleGuaranteeService(roleGuaranteeRepository, entityEventManager());
	}
	
	/**
	 * Role guarantees - by role
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmRoleGuaranteeRoleService.class)
	public IdmRoleGuaranteeRoleService roleGuaranteeRoleService() {
		return new DefaultIdmRoleGuaranteeRoleService(roleGuaranteeRoleRepository, entityEventManager());
	}
	
	/**
	 * Confidential storage value
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmConfidentialStorageValueService.class)
	public IdmConfidentialStorageValueService confidentialStorageValueService() {
		return new DefaultIdmConfidentialStorageValueService(confidentialStorageValueRepository);
	}
	
	/**
	 * Common eav forms
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(CommonFormService.class)
	public CommonFormService commonFormService() {
		return new DefaultCommonFormService(formRepository, formService(), formDefinitionService());
	}
	
	/**
	 * Code lists. Use {@link #codeListManager()} instead.
	 * 
	 * @return
	 * @see #codeListManager() 
	 */
	@Bean
	@ConditionalOnMissingBean(IdmCodeListService.class)
	public IdmCodeListService codeListService() {
		return new DefaultIdmCodeListService(codeListRepository, entityEventManager());
	}
	
	/**
	 * Code list items. Use {@link #codeListManager()} instead.
	 * 
	 * @return
	 * @see #codeListManager()
	 */
	@Bean
	@ConditionalOnMissingBean(IdmCodeListItemService.class)
	public IdmCodeListItemService codeListItemService() {
		return new DefaultIdmCodeListItemService(codeListItemRepository, formService(), entityEventManager());
	}
	
	/**
	 * Code list manager
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(CodeListManager.class)
	public CodeListManager codeListManager() {
		return new DefaultCodeListManager();
	}
	
	/**
	 * Configuration for attachments
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(AttachmentConfiguration.class)
	public AttachmentConfiguration attachmentConfiguration() {
		return new DefaultAttachmentConfiguration();
	}
	
	/**
	 * Attachment manager
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(AttachmentManager.class)
	public AttachmentManager attachmentManager() {
		return new DefaultAttachmentManager(attachmentRepository);
	}
	
	/**
	 * Password history service
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmPasswordHistoryService.class)
	public IdmPasswordHistoryService passwordHistoryService() {
		return new DefaultIdmPasswordHistoryService(passwordHistoryRepository);
	}

	/**
	 * Service for assigning authorization evaluators to roles.
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(IdmGenerateValueService.class)
	public IdmGenerateValueService generatedValueService() {
		return new DefaultIdmGenerateValueService(generatedValueRepository);
	}
	
	/**
	 * Configure logger programatically.
	 * 
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean(LoggerManager.class)
	public LoggerManager loggerManager() {
		return new LogbackLoggerManager();
	}
}
