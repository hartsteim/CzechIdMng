package eu.bcvsolutions.idm.core.model.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import eu.bcvsolutions.idm.core.api.config.domain.PrivateIdentityConfiguration;
import eu.bcvsolutions.idm.core.api.dto.IdmConfigurationDto;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Application configuration tests
 * 
 * @author Radek Tomiška
 *
 */
@Transactional
public class DefaultConfigurationServiceIntegrationTest extends AbstractIntegrationTest {
	
	private static final String TEST_PROPERTY_KEY = "test.property";
	private static final String TEST_PROPERTY_DB_KEY = "test.db.property";
	public static final String TEST_GUARDED_PROPERTY_KEY = "idm.sec.core.password.test";
	private static final String TEST_GUARDED_PROPERTY_VALUE = "secret_password";
	//
	@Autowired private ApplicationContext context;
	//
	private ConfigurationService configurationService;
	
	@Before
	public void init() {
		configurationService = context.getAutowireCapableBeanFactory().createBean(DefaultConfigurationService.class);
	}
	
	@Test
	public void testReadNotExists() {
		assertNull(configurationService.getValue(getHelper().createName()));
	}
	
	@Test
	public void testReadNotExistsWithDefault() {
		assertEquals("true", configurationService.getValue(getHelper().createName(), "true"));
	}
	
	@Test
	public void testReadBooleanNotExistsWithDefault() {
		assertTrue(configurationService.getBooleanValue(getHelper().createName(), true));
	}
	
	@Test
	public void testReadPropertyFromFile() {
		assertEquals("true", configurationService.getValue(TEST_PROPERTY_KEY));
	}
	
	@Test
	public void testReadBooleanPropertyFromFile() {
		assertTrue(configurationService.getBooleanValue(TEST_PROPERTY_KEY));
	}
	
	@Test
	public void testReadPropertyFromDb() {
		configurationService.saveConfiguration(new IdmConfigurationDto(TEST_PROPERTY_DB_KEY, "true"));
		assertTrue(configurationService.getBooleanValue(TEST_PROPERTY_DB_KEY));
	}
	
	@Test
	public void testReadOverridenPropertyFromDb() {
		configurationService.saveConfiguration(new IdmConfigurationDto(TEST_PROPERTY_KEY, "false"));
		assertEquals("false", configurationService.getValue(TEST_PROPERTY_KEY));
	}
	
	@Test
	public void testReadGuardedPropertyFromFile() {
		assertEquals(TEST_GUARDED_PROPERTY_VALUE, configurationService.getValue(TEST_GUARDED_PROPERTY_KEY));
	}
	
	@Test
	public void testReadConfidentialPropertyFromDB() {
		configurationService.saveConfiguration(new IdmConfigurationDto(TEST_GUARDED_PROPERTY_KEY, "secured_change"));
		assertEquals("secured_change", configurationService.getValue(TEST_GUARDED_PROPERTY_KEY));
	}
	
	@Test
	public void testGlobalDateFormatChange() {
		final String format = "dd.MM";
		configurationService.setValue(ConfigurationService.PROPERTY_APP_DATE_FORMAT, format);
		assertEquals(format, configurationService.getDateFormat());
		configurationService.setValue(ConfigurationService.PROPERTY_APP_DATE_FORMAT, ConfigurationService.DEFAULT_APP_DATE_FORMAT);
		assertEquals(ConfigurationService.DEFAULT_APP_DATE_FORMAT, configurationService.getDateFormat());
	}
	
	@Test
	public void testDefaultDateTimeFormat() {
		assertEquals(ConfigurationService.DEFAULT_APP_DATETIME_FORMAT, configurationService.getDateTimeFormat());
	}
	
	@Test(expected = ResultCodeException.class)
	public void testSaveWrongContractState() {
		configurationService.setValue(PrivateIdentityConfiguration.PROPERTY_IDENTITY_CREATE_DEFAULT_CONTRACT_STATE, "wrong");
	}
	
}
