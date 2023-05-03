package eu.bcvsolutions.idm.acc.service.impl;

import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.plugin.core.OrderAwarePluginRegistry;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.acc.domain.AccGroupPermission;
import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.AccIdentityAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaObjectClassFilter;
import eu.bcvsolutions.idm.acc.entity.AccAccount;
import eu.bcvsolutions.idm.acc.entity.AccAccount_;
import eu.bcvsolutions.idm.acc.entity.AccIdentityAccount;
import eu.bcvsolutions.idm.acc.entity.AccIdentityAccount_;
import eu.bcvsolutions.idm.acc.entity.AccUniformPasswordSystem;
import eu.bcvsolutions.idm.acc.entity.AccUniformPasswordSystem_;
import eu.bcvsolutions.idm.acc.entity.SysSchemaAttribute_;
import eu.bcvsolutions.idm.acc.entity.SysSchemaObjectClass;
import eu.bcvsolutions.idm.acc.entity.SysSchemaObjectClass_;
import eu.bcvsolutions.idm.acc.entity.SysSystem;
import eu.bcvsolutions.idm.acc.entity.SysSystemAttributeMapping;
import eu.bcvsolutions.idm.acc.entity.SysSystemAttributeMapping_;
import eu.bcvsolutions.idm.acc.entity.SysSystemEntity_;
import eu.bcvsolutions.idm.acc.entity.SysSystemMapping;
import eu.bcvsolutions.idm.acc.entity.SysSystemMapping_;
import eu.bcvsolutions.idm.acc.entity.SysSystem_;
import eu.bcvsolutions.idm.acc.event.AccountEvent;
import eu.bcvsolutions.idm.acc.event.AccountEvent.AccountEventType;
import eu.bcvsolutions.idm.acc.repository.AccAccountRepository;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.api.ConnectorManager;
import eu.bcvsolutions.idm.acc.service.api.ConnectorType;
import eu.bcvsolutions.idm.acc.service.api.PasswordFilterManager;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningService;
import eu.bcvsolutions.idm.acc.service.api.SynchronizationEntityExecutor;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityTypeManager;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.acc.system.entity.SystemEntityTypeRegistrable;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.EntityEventManager;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.service.AbstractFormableService;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.entity.IdmFormDefinition_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.model.entity.IdmRole_;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.dto.AuthorizableType;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.api.IcObjectClass;
import eu.bcvsolutions.idm.ic.api.IcObjectClassInfo;
import eu.bcvsolutions.idm.ic.impl.IcConnectorObjectImpl;

/**
 * Accounts on target system.
 *
 * @author Radek Tomiška
 * @author svandav
 * @author Roman Kucera
 * @author Tomáš Doischer
 *
 */
@Service("accAccountService")
public class DefaultAccAccountService extends AbstractFormableService<AccAccountDto, AccAccount, AccAccountFilter>
		implements AccAccountService {

	private final AccAccountRepository accountRepository;
	private final AccIdentityAccountService identityAccountService;
	private final SysSystemService systemService;
	private final SysSchemaObjectClassService schemaObjectClassService;
	private final SysSchemaAttributeService schemaAttributeService;
	@Autowired
	private List<SynchronizationEntityExecutor> executors;
	private PluginRegistry<SynchronizationEntityExecutor, SystemEntityTypeRegistrable> pluginExecutors;
	@Lazy
	@Autowired
	private PasswordFilterManager passwordFilterManager;
	@Autowired
	private LookupService lookupService;
	@Autowired
	private ConnectorManager connectorManager;
	@Autowired
	private SysSystemEntityTypeManager systemEntityManager;

	@Autowired
	public DefaultAccAccountService(AccAccountRepository accountRepository,
			AccIdentityAccountService identityAccountService, @Lazy SysSystemService systemService,
			SysSchemaObjectClassService schemaObjectClassService, SysSchemaAttributeService schemaAttributeService,
			FormService formService, EntityEventManager entityEventManager) {
		super(accountRepository, entityEventManager, formService);
		//
		Assert.notNull(identityAccountService, "Service is required.");
		Assert.notNull(accountRepository, "Repository is required.");
		Assert.notNull(systemService, "Service is required.");
		Assert.notNull(schemaObjectClassService, "Service is required.");
		Assert.notNull(schemaAttributeService, "Service is required.");

		//
		this.identityAccountService = identityAccountService;
		this.accountRepository = accountRepository;
		this.systemService = systemService;
		this.schemaAttributeService = schemaAttributeService;
		this.schemaObjectClassService = schemaObjectClassService;
	}

	@Override
	public AuthorizableType getAuthorizableType() {
		return new AuthorizableType(AccGroupPermission.ACCOUNT, getEntityClass());
	}

	@Override
	protected AccAccountDto toDto(AccAccount entity, AccAccountDto dto) {
		AccAccountDto newDto = super.toDto(entity, dto);
		//
		// if dto exists add real uid
		if (newDto != null) {
			if (newDto.getSystemEntity() != null) {
				SysSystemEntityDto systemEntity = DtoUtils.getEmbedded(newDto, AccAccount_.systemEntity);
				newDto.setRealUid(systemEntity.getUid());
			} else {
				// If system entity do not exist, then return uid from account.
				newDto.setRealUid(newDto.getUid());
			}
			// Load and set target entity. For loading a target entity is using sync
			// executor.
			String entityType = newDto.getEntityType();
			SystemEntityTypeRegistrable systemEntityType = systemEntityManager.getSystemEntityByCode(entityType);
			if (systemEntityType != null && systemEntityType.isSupportsSync()) {
				SynchronizationEntityExecutor executor = this.getSyncExecutor(entityType);
				UUID targetEntity = executor.getEntityByAccount(newDto.getId());
				newDto.setTargetEntityType(systemEntityType.getSystemEntityCode());
				newDto.setTargetEntityId(targetEntity);
			}
		}
		return newDto;
	}

	@Override
	@Transactional
	public void delete(AccAccountDto account, BasePermission... permission) {
		Assert.notNull(account, "Account is required.");
		// delete all identity accounts (call event)
		AccIdentityAccountFilter identityAccountFilter = new AccIdentityAccountFilter();
		identityAccountFilter.setAccountId(account.getId());
		List<AccIdentityAccountDto> identityAccounts = identityAccountService.find(identityAccountFilter, null)
				.getContent();
		identityAccounts.forEach(identityAccount -> {
			identityAccountService.delete(identityAccount);
		});

		AccAccountDto potentialProtectedAccount = get(account);

		// Account was already deleted during relations identity-accounts deletion
		if (potentialProtectedAccount == null) {
			return;
		}
		// If was account marked as protected (after we try delete all
		// identity-accounts), then we don't want delete account. We have to prevent
		// throw "account is protected" exception, because we need do commit (not
		// rollback).
		if (!account.isAccountProtectedAndValid() && potentialProtectedAccount.isAccountProtectedAndValid()) {
			return;
		}

		this.publish(new AccountEvent(AccountEventType.DELETE, account,
				ImmutableMap.of(AccAccountService.DELETE_TARGET_ACCOUNT_PROPERTY, Boolean.TRUE)));
	}

	@Override
	public List<AccAccountDto> getAccounts(UUID systemId, UUID identityId) {
		return toDtos(accountRepository.findAccountBySystemAndIdentity(identityId, systemId), true);
	}

	@Override
	public AccAccountDto getAccount(String uid, UUID systemId) {
		Assert.notNull(uid, "UID cannot be null!");
		Assert.notNull(systemId, "System ID cannot be null!");

		AccAccountFilter filter = new AccAccountFilter();
		filter.setUid(uid);
		filter.setSystemId(systemId);

		List<AccAccountDto> accounts = this.find(filter, null).getContent();
		if (accounts.isEmpty()) {
			return null;
		}
		return accounts.get(0);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<AccAccountDto> findExpired(ZonedDateTime expirationDate, Pageable pageable) {
		Assert.notNull(expirationDate, "Expiration date is required.");
		//
		return toDtoPage(
				accountRepository.findByEndOfProtectionLessThanAndInProtectionIsTrue(expirationDate, pageable));
	}

	@Override
	public IcConnectorObject getConnectorObject(AccAccountDto account, BasePermission... permissions) {
		Assert.notNull(account, "Account cannot be null!");
		this.checkAccess(account, permissions);
		List<SysSchemaAttributeDto> schemaAttributes = this.getSchemaAttributes(account.getSystem(), null);
		if (schemaAttributes == null) {
			return null;
		}
		try{
			// Find connector-type.
			SysSystemDto systemDto = lookupService.lookupEmbeddedDto(account, AccAccount_.system);
			ConnectorType connectorType = connectorManager.findConnectorTypeBySystem(systemDto);
			// Find first mapping for entity type and system, from the account and return his object class.
			IcObjectClass icObjectClass = schemaObjectClassService.findByAccount(account.getSystem(), account.getEntityType());
			
			IcConnectorObject fullObject = this.systemService.readConnectorObject(account.getSystem(),
					account.getRealUid(), icObjectClass, connectorType);
			return this.getConnectorObjectForSchema(fullObject, schemaAttributes);
		} catch (Exception ex) {
			SysSystemDto system = DtoUtils.getEmbedded(account, AccAccount_.system, SysSystemDto.class);
			throw new ResultCodeException(AccResultCode.ACCOUNT_CANNOT_BE_READ_FROM_TARGET, ImmutableMap.of("account",
					account.getUid(), "system", system != null ? system.getName() : account.getSystem()), ex);
		}
	}

	/**
	 * Return only attributes for witch we have schema attribute definitions.
	 *
	 * @param fullObject
	 * @param schemaAttributes
	 * @return
	 */
	private IcConnectorObject getConnectorObjectForSchema(IcConnectorObject fullObject,
			List<SysSchemaAttributeDto> schemaAttributes) {
		if (fullObject == null || schemaAttributes == null) {
			return null;
		}

		List<IcAttribute> allAttributes = fullObject.getAttributes();
		List<IcAttribute> resultAttributes = allAttributes.stream().filter(attribute -> {
			return schemaAttributes.stream()
					.filter(schemaAttribute -> attribute.getName().equals(schemaAttribute.getName())).findFirst()
					.isPresent();
		}).collect(Collectors.toList());
		return new IcConnectorObjectImpl(fullObject.getUidValue(), fullObject.getObjectClass(), resultAttributes);
	}

	@Override
	protected AccAccountDto applyContext(AccAccountDto dto, AccAccountFilter context, BasePermission... permission) {
		AccAccountDto accountDto = super.applyContext(dto, context, permission);
		if (accountDto == null) {
			return null;
		}
		if (context != null && IdentitySynchronizationExecutor.SYSTEM_ENTITY_TYPE.equals(accountDto.getEntityType()) && BooleanUtils.isTrue(context.getIncludeEcho())) {
			Map<String, BaseDto> embedded = accountDto.getEmbedded();
			embedded.put(AccAccountDto.PROPERTY_ECHO, passwordFilterManager.getEcho(accountDto.getId()));
		}
		return accountDto;
	}

	/**
	 * Find schema's attributes for the system id and schema name.
	 *
	 * @param systemId
	 * @param schema
	 *            - If is schema name null, then will used default '__ACCOUNT__'.
	 * @return
	 */
	private List<SysSchemaAttributeDto> getSchemaAttributes(UUID systemId, String schema) {
		SysSchemaObjectClassFilter schemaFilter = new SysSchemaObjectClassFilter();
		schemaFilter.setSystemId(systemId);
		schemaFilter.setObjectClassName(schema != null ? schema : IcObjectClassInfo.ACCOUNT);

		List<SysSchemaObjectClassDto> schemas = schemaObjectClassService.find(schemaFilter, null).getContent();
		if (schemas.size() != 1) {
			return null;
		}
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setObjectClassId(schemas.get(0).getId());
		schemaAttributeFilter.setSystemId(systemId);
		return schemaAttributeService.find(schemaAttributeFilter, null).getContent();
	}

	@Override
	protected List<Predicate> toPredicates(Root<AccAccount> root, CriteriaQuery<?> query, CriteriaBuilder builder,
			AccAccountFilter filter) {
		List<Predicate> predicates = super.toPredicates(root, query, builder, filter);
		// full search
		if (StringUtils.isNotEmpty(filter.getText())) {
			predicates.add(builder.or(//
					builder.like(builder.lower(root.get(AccAccount_.uid)), "%" + filter.getText().toLowerCase() + "%"),
					builder.like(builder.lower(root.get(AccAccount_.systemEntity).get(SysSystemEntity_.uid)),
							"%" + filter.getText().toLowerCase() + "%")));
		}
		if (filter.getSystemId() != null) {
			predicates.add(builder.equal(root.get(AccAccount_.system).get(SysSystem_.id), filter.getSystemId()));
		}
		if (filter.getSystems() != null && !filter.getSystems().isEmpty()) {
			predicates.add(root.get(AccAccount_.system).get(SysSystem_.id).in(filter.getSystems()));
		}
		if (filter.getSystemEntityId() != null) {
			predicates.add(builder.equal(root.get(AccAccount_.systemEntity).get(SysSystemEntity_.id),
					filter.getSystemEntityId()));
		}
		if (filter.getFormDefinitionId() != null) {
			predicates.add(builder.equal(root.get(AccAccount_.formDefinition).get(IdmFormDefinition_.id),
					filter.getFormDefinitionId()));
		}
		if (filter.getHasFormDefinition() != null) {
			if (BooleanUtils.isTrue(filter.getHasFormDefinition())) {
				predicates.add(builder.isNotNull(root.get(AccAccount_.formDefinition)));
			} else {
				predicates.add(builder.isNull(root.get(AccAccount_.formDefinition)));
			}
		}
		if (filter.getSystemMapping() != null) {
			predicates.add(builder.equal(root.get(AccAccount_.systemMapping).get(SysSystemMapping_.id),
					filter.getSystemMapping()));
		}
		if (filter.getUid() != null) {
			predicates.add(builder.equal(root.get(AccAccount_.uid), filter.getUid()));
		}
		if (filter.getIdentityId() != null || filter.getOwnership() != null) {
			Subquery<AccIdentityAccount> identityAccountSubquery = query.subquery(AccIdentityAccount.class);
			Root<AccIdentityAccount> subRootIdentityAccount = identityAccountSubquery.from(AccIdentityAccount.class);
			identityAccountSubquery.select(subRootIdentityAccount);

			Predicate predicate = builder
					.and(builder.equal(subRootIdentityAccount.get(AccIdentityAccount_.account), root));
			Predicate identityPredicate = builder.equal(
					subRootIdentityAccount.get(AccIdentityAccount_.identity).get(IdmIdentity_.id),
					filter.getIdentityId());
			Predicate ownerPredicate = builder.equal(subRootIdentityAccount.get(AccIdentityAccount_.ownership),
					filter.getOwnership());

			if (filter.getIdentityId() != null && filter.getOwnership() == null) {
				predicate = builder.and(predicate, identityPredicate);
			} else if (filter.getOwnership() != null && filter.getIdentityId() == null) {
				predicate = builder.and(predicate, ownerPredicate);
			} else {
				predicate = builder.and(predicate, identityPredicate, ownerPredicate);
			}

			identityAccountSubquery.where(predicate);
			predicates.add(builder.exists(identityAccountSubquery));
		}
		if (filter.getRoleIds() != null && !filter.getRoleIds().isEmpty()) {
			Subquery<AccIdentityAccount> identityAccountSubquery = query.subquery(AccIdentityAccount.class);
			Root<AccIdentityAccount> subRootIdentityAccount = identityAccountSubquery.from(AccIdentityAccount.class);
			
			Subquery<IdmIdentityRole> identityRoleSubquery = query.subquery(IdmIdentityRole.class);
			Root<IdmIdentityRole> subRootIdentityRole = identityRoleSubquery.from(IdmIdentityRole.class);
			
			identityRoleSubquery.select(subRootIdentityRole);
			
			Join<AccIdentityAccount, IdmIdentityRole> identityRole = subRootIdentityAccount.join(AccIdentityAccount_.identityRole, JoinType.LEFT);
			identityRoleSubquery.where(
					builder.and(identityRole.get(IdmIdentityRole_.role).get(IdmRole_.id).in(filter.getRoleIds()),
							builder.equal(subRootIdentityAccount.get(AccIdentityAccount_.identityRole), identityRole))
			);
			
			identityAccountSubquery.select(subRootIdentityAccount);
			identityAccountSubquery.where(
                    builder.and(
                    		builder.equal(subRootIdentityAccount.get(AccIdentityAccount_.account), root), // correlation attr
                    		builder.exists(identityRoleSubquery))
            );			
			predicates.add(builder.exists(identityAccountSubquery));
		}
		if (filter.getIdentities() != null && !filter.getIdentities().isEmpty()) {
			Subquery<AccIdentityAccount> identityAccountSubquery = query.subquery(AccIdentityAccount.class);
			Root<AccIdentityAccount> subRootIdentityAccount = identityAccountSubquery.from(AccIdentityAccount.class);
			identityAccountSubquery.select(subRootIdentityAccount);

			Predicate predicate = builder
					.and(builder.equal(subRootIdentityAccount.get(AccIdentityAccount_.account), root));
			Predicate identityPredicate = 
					subRootIdentityAccount.get(AccIdentityAccount_.identity).get(IdmIdentity_.id).in(filter.getIdentities());

			predicate = builder.and(predicate, identityPredicate);

			identityAccountSubquery.where(predicate);
			predicates.add(builder.exists(identityAccountSubquery));
		}
		if (filter.getAccountType() != null) {
			Join<AccAccount, SysSystemMapping> accountMapping = root.join(AccAccount_.systemMapping);
			predicates.add(builder.equal(accountMapping.get(SysSystemMapping_.accountType), filter.getAccountType()));
		}

		if (filter.getSupportChangePassword() != null && filter.getSupportChangePassword()) {
			Subquery<SysSystemAttributeMapping> systemAttributeMappingSubquery = query
					.subquery(SysSystemAttributeMapping.class);
			Root<SysSystemAttributeMapping> subRootSystemAttributeMapping = systemAttributeMappingSubquery
					.from(SysSystemAttributeMapping.class);
			systemAttributeMappingSubquery.select(subRootSystemAttributeMapping);

			Path<SysSystem> systemPath = root.get(AccAccount_.system);
			Predicate predicate = builder.and(
					builder.isFalse(systemPath.get(SysSystem_.disabledProvisioning)),
					builder.equal(subRootSystemAttributeMapping//
						.get(SysSystemAttributeMapping_.schemaAttribute)//
						.get(SysSchemaAttribute_.objectClass)//
						.get(SysSchemaObjectClass_.system), //
						systemPath),
					builder.equal(subRootSystemAttributeMapping//
							.get(SysSystemAttributeMapping_.systemMapping)//
							.get(SysSystemMapping_.operationType), SystemOperationType.PROVISIONING),
					builder.equal(subRootSystemAttributeMapping//
							.get(SysSystemAttributeMapping_.schemaAttribute)//
							.get(SysSchemaAttribute_.name), ProvisioningService.PASSWORD_SCHEMA_PROPERTY_NAME)
					);

			systemAttributeMappingSubquery.where(predicate);
			predicates.add(builder.exists(systemAttributeMappingSubquery));
		}

		if (filter.getEntityType() != null) {
			predicates.add(builder.equal(root.get(AccAccount_.entityType), filter.getEntityType()));
		}

		if (filter.getInProtection() != null) {
			predicates.add(builder.equal(root.get(AccAccount_.inProtection), filter.getInProtection()));
		}

		if (filter.getUniformPasswordId() != null) {
			Subquery<SysSystem> subquerySystem = query.subquery(SysSystem.class);
			Root<SysSystem> subRootSystem = subquerySystem.from(SysSystem.class);
			subquerySystem.select(subRootSystem);

			Subquery<AccUniformPasswordSystem> subqueryUniformSystem = query.subquery(AccUniformPasswordSystem.class);
			Root<AccUniformPasswordSystem> subRootUniformSystem = subqueryUniformSystem.from(AccUniformPasswordSystem.class);
			subqueryUniformSystem.select(subRootUniformSystem);

			predicates.add(builder.exists(subquerySystem.where(
					builder.and(
						builder.equal(root.get(AccAccount_.system), subRootSystem), // Correlation attribute - connection to system
						builder.isFalse(root.get(AccAccount_.inProtection)), // Exclude in protection accounts
						// Disabled, readonly or without provisioning system are NOT excluded, because from these systems may be still receive password change requests
//						builder.isFalse(subRootSystem.get(SysSystem_.disabled)), // Exclude disabled system
//						builder.isFalse(subRootSystem.get(SysSystem_.readonly)), // Exclude readonly system
//						builder.isFalse(subRootSystem.get(SysSystem_.disabledProvisioning)), // Exclude system with disabled provisioning
							builder.exists(
									subqueryUniformSystem.where(
											builder.and(
													builder.equal(subRootUniformSystem.get(AccUniformPasswordSystem_.system), subRootSystem),
													builder.equal(subRootUniformSystem.get(AccUniformPasswordSystem_.uniformPassword).get(AbstractEntity_.id), filter.getUniformPasswordId())
													)
											)
									)
							)
					)));
		}

		if (filter.getSupportPasswordFilter() != null) {
			Subquery<SysSystem> subquerySystem = query.subquery(SysSystem.class);
			Root<SysSystem> subRootSystem = subquerySystem.from(SysSystem.class);
			subquerySystem.select(subRootSystem);
			
			Subquery<SysSchemaObjectClass> subquerySchema = query.subquery(SysSchemaObjectClass.class);
			Root<SysSchemaObjectClass> subRootSchema = subquerySchema.from(SysSchemaObjectClass.class);
			subquerySchema.select(subRootSchema);
			
			Subquery<SysSystemMapping> subqueryMapping = query.subquery(SysSystemMapping.class);
			Root<SysSystemMapping> subRootMapping = subqueryMapping.from(SysSystemMapping.class);
			subqueryMapping.select(subRootMapping);

			Subquery<SysSystemAttributeMapping> subqueryAttributeMapping = query.subquery(SysSystemAttributeMapping.class);
			Root<SysSystemAttributeMapping> subRootAttributeMapping = subqueryAttributeMapping.from(SysSystemAttributeMapping.class);
			subqueryAttributeMapping.select(subRootAttributeMapping);
			
			Subquery<SysSystemMapping> subquery = query.subquery(SysSystemMapping.class);
			Root<SysSystemMapping> subRoot = subquery.from(SysSystemMapping.class);
			subquery.select(subRoot);

			predicates.add(builder.exists(subquerySystem.where(
				builder.and(
					builder.equal(root.get(AccAccount_.system), subRootSystem), // Correlation attribute - connection to system
					builder.isFalse(root.get(AccAccount_.inProtection)), // Exclude in protection accounts
					// Disabled, readonly or without provisioning system are NOT excluded, because from these systems may be still receive password change requests
						builder.exists(
							subquerySchema.where(
								builder.and(
									builder.equal(subRootSchema.get(SysSchemaObjectClass_.system), subRootSystem), // Correlation attribute - connection to schem object class
									builder.exists(
										subqueryMapping.where(
											builder.and(
												builder.equal(subRootMapping.get(SysSystemMapping_.objectClass), subRootSchema), // Correlation attribute - connection to mapping
												builder.equal(subRootMapping.get(SysSystemMapping_.operationType), SystemOperationType.PROVISIONING), // System mapping must be provisioning
												builder.equal(subRootMapping.get(SysSystemMapping_.entityType), IdentitySynchronizationExecutor.SYSTEM_ENTITY_TYPE), // Password change is now allowed only for identities
												builder.exists(
													subqueryAttributeMapping.where(
														builder.and(
															builder.equal(subRootAttributeMapping.get(SysSystemAttributeMapping_.systemMapping), subRootMapping), // Correlation attribute - connection to attribute mapping
															builder.isTrue(subRootAttributeMapping.get(SysSystemAttributeMapping_.passwordAttribute)), // Only password attribute
															builder.isFalse(subRootAttributeMapping.get(SysSystemAttributeMapping_.disabledAttribute)), // Exclude disabled attribute
															BooleanUtils.isTrue(filter.getSupportPasswordFilter())
															?
																builder.isTrue(subRootAttributeMapping.get(SysSystemAttributeMapping_.passwordFilter))
															:
																builder.isFalse(subRootAttributeMapping.get(SysSystemAttributeMapping_.passwordFilter))
															)
														)
													)
												)
											)
										)
									)
								)
							)
						)
					)));
		}
		//
		return predicates;

	}

	@Override
	public SynchronizationEntityExecutor getSyncExecutor(String entityType) {

		if (this.pluginExecutors == null) {
			this.pluginExecutors = OrderAwarePluginRegistry.create(executors);
		}
		SystemEntityTypeRegistrable systemEntityType = systemEntityManager.getSystemEntityByCode(entityType);
		SynchronizationEntityExecutor executor = this.pluginExecutors.getPluginFor(systemEntityType);
		if (executor == null) {
			throw new UnsupportedOperationException(MessageFormat
					.format("Synchronization executor for SystemEntityType {0} is not supported!", entityType));
		}
		return executor;
	}

}
