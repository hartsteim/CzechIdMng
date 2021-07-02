package eu.bcvsolutions.idm.test.api;

import java.util.Collection;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import eu.bcvsolutions.idm.IdmApplication;
import eu.bcvsolutions.idm.core.api.domain.TransactionContextHolder;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.entity.BaseEntity;
import eu.bcvsolutions.idm.core.api.repository.AbstractEntityRepository;
import eu.bcvsolutions.idm.core.api.service.IdmCacheManager;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.service.ModuleService;
import eu.bcvsolutions.idm.core.api.service.ReadWriteDtoService;
import eu.bcvsolutions.idm.core.security.api.domain.IdmGroupPermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmJwtAuthentication;
import eu.bcvsolutions.idm.core.security.api.utils.IdmAuthorityUtils;

/**
 * Test rest services will be based on spring integration tests with MockMvc / hamcrest and junit test framework
 *
 * http://docs.spring.io/spring-framework/docs/current/spring-framework-reference/html/integration-testing.html
 *
 * @author Radek Tomiška
 *
 */
@Ignore
@RunWith(SpringRunner.class)
@SpringBootTest(classes = IdmApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@Rollback(true)
public abstract class AbstractIntegrationTest {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AbstractIntegrationTest.class);
	//
	@Autowired private TestHelper helper;
	@Autowired private PlatformTransactionManager platformTransactionManager;
	@Autowired private LookupService lookupService;
	@Autowired private ModuleService moduleService;
	@Autowired private IdmCacheManager cacheManager;
	//
	private TransactionTemplate template;

	@BeforeClass
	public static void disableTestsOnDocumentation() {
		// when property '-DdocumentationOnly' is given in maven build, then all integration tests
		// are skipped. Override this method, when concrete tests have to be executed - e.g. AbstractSwaggerTest
		// is executed, because generate artifacts (swagger.json) for documentation itself.
		// Unit test are executed every time, they are quickly executed, but integration tests can take some time
		// and we want build artifact without waiting (e.q. when hotfix needs to be released).
	    Boolean documentationOnly = Boolean.valueOf(System.getProperty("documentationOnly", "false"));
	    Assume.assumeFalse(documentationOnly);
	}
	
	@Before
	public void init() {
		TransactionContextHolder.setContext(TransactionContextHolder.createEmptyContext()); // start transaction for each test method
	}
	
	/**
	 * Evict all caches after @Transactional + @Rollback(true) test ends.
	 */
	@After
	public void evictCaches() {
		Transactional transactional = this.getClass().getAnnotation(Transactional.class);
		if (transactional == null) {
			return;
		}
		Rollback rollback = this.getClass().getAnnotation(Rollback.class);
		if (rollback == null || !rollback.value()) {
			return;
		}
		//
		cacheManager.evictAllCaches();
		LOG.debug("All caches are evicted.");
	}

	/**
	 * Log in as "boss"
	 *
	 * @param username
	 */
	public void loginAsAdmin() {
		getHelper().loginAdmin();
	}

	/**
	 *  User will be logged as APP_ADMIN
	 *
	 *  Lookout: security context is mocked
	 *
	 * @param username
	 */
	public void loginAsAdmin(String username) {
		Collection<GrantedAuthority> authorities = IdmAuthorityUtils.toAuthorities(IdmGroupPermission.APP);
		IdmIdentityDto identity = (IdmIdentityDto) lookupService.getDtoLookup(IdmIdentityDto.class).lookup(username);
		SecurityContextHolder.getContext().setAuthentication(new IdmJwtAuthentication(identity, null, authorities, "test"));
	}

	/**
	 * User will be logged as user with all authorities without APP_ADMIN
	 *
	 * Lookout: security context is mocked
	 *
	 * @param user
	 */
	public void loginAsNoAdmin(String user) {
		Collection<GrantedAuthority> authorities = IdmAuthorityUtils.toAuthorities(moduleService.getAvailablePermissions()).stream().filter(authority -> {
			return !IdmGroupPermission.APP_ADMIN.equals(authority.getAuthority());
		}).collect(Collectors.toList());
		IdmIdentityDto identity = (IdmIdentityDto) lookupService.getDtoLookup(IdmIdentityDto.class).lookup(user);
		SecurityContextHolder.getContext().setAuthentication(new IdmJwtAuthentication(identity, null, authorities, "test"));
	}

	/**
	 * Login as user without authorities given in parameter authorities.
	 *
	 * @param user
	 * @param authorities
	 */
	public void loginWithout(String user, String ...authorities) {
		Collection<GrantedAuthority> authoritiesWithout = IdmAuthorityUtils.toAuthorities(moduleService.getAvailablePermissions()).stream().filter(authority -> {
			for (String auth: authorities) {
				if (auth.equals(authority.getAuthority())) {
					return false;
				}
			}
			return true;
		}).collect(Collectors.toList());
		IdmIdentityDto identity = (IdmIdentityDto) lookupService.getDtoLookup(IdmIdentityDto.class).lookup(user);
		SecurityContextHolder.getContext().setAuthentication(new IdmJwtAuthentication(identity, null, authoritiesWithout, "test"));
	}

	/**
	 * Clears security context
	 */
	public void logout(){
		getHelper().logout();
	}

	/**
	 * Creates new template by platformTransactionManager
	 */
	protected void prepareTransactionTemplate() {
		template = new TransactionTemplate(platformTransactionManager);
	}

	protected TransactionTemplate getTransactionTemplate() {
		if (template == null) {
			prepareTransactionTemplate();
		}
		return template;
	}

	/**
	 * Save entity in new transaction by given service
	 *
	 * @param object
	 * @param service
	 * @return
	 */
	protected <T extends BaseDto> T saveInTransaction(final T object, final ReadWriteDtoService<T, ?> service) {
		return getTransactionTemplate().execute(new TransactionCallback<T>() {
			public T doInTransaction(TransactionStatus transactionStatus) {
				return service.save(object);
			}
		});
	}

	/**
	 * Save entity in new transaction by given repository
	 *
	 * @param object
	 * @param repository
	 * @return
	 */
	protected <T extends BaseEntity> T saveInTransaction(final T object, final AbstractEntityRepository<T> repository) {
		return getTransactionTemplate().execute(new TransactionCallback<T>() {
			public T doInTransaction(TransactionStatus transactionStatus) {
				return repository.save(object);
			}
		});
	}

	/**
	 * Test helper with useful test utilities
	 *
	 * @return
	 */
	protected TestHelper getHelper() {
		return helper;
	}
	
	/**
	 * Provides lookup service
	 * 
	 * @return
	 */
	protected LookupService getLookupService() {
		return lookupService;
	}
}
