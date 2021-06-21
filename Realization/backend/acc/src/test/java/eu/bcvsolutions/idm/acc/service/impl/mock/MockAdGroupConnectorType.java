package eu.bcvsolutions.idm.acc.service.impl.mock;

import eu.bcvsolutions.idm.acc.connector.AdGroupConnectorType;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.utils.AutowireHelper;
import org.springframework.stereotype.Component;

/**
 * Mock AD wizard for groups. Only for using in tests. (We do not have MS AD in test environment).
 *
 * @author Vít Švanda
 * @since 11.1.0
 */
@Component(MockAdGroupConnectorType.NAME)
public class MockAdGroupConnectorType extends AdGroupConnectorType {
	
	// Connector type ID.
	public static final String NAME = "mock-ad-group-connector-type";
	
	private MockSysSystemService systemService;

	@Override
	protected String findDn(String filter, String port, String host, String adUser, String adPassword, boolean ssl) {
		// Mock - We don't have an AD.
		return filter;
	}

	@Override
	protected String createTestUser(String username, String entryDN, String port, String host, String adUser, String adPassword, boolean ssl) {
		// Mock - We don't have an AD.
		return username;
	}

	@Override
	protected String findDnsHostName(String port, String host, String adUser, String adPassword, boolean ssl) {
		// Mock - We don't have an AD.
		return host;
	}

	@Override
	protected void deleteTestUser(String entryDN, String port, String host, String adUser, String adPassword, boolean ssl) {
		// Mock - We don't have an AD.
	}

	@Override
	protected void assignTestUserToGroup(String userDN, String groupDN, String port, String host, String adUser, String adPassword, boolean ssl) {
		// Mock - We don't have an AD.
	}

	@Override
	protected SysSystemService getSystemService() {
		return initMockSysSystemService();
	}

	private MockSysSystemService initMockSysSystemService(){
		if (systemService == null) {
			systemService = AutowireHelper.createBean(MockSysSystemService.class);
		}
		return systemService;
	}
}
