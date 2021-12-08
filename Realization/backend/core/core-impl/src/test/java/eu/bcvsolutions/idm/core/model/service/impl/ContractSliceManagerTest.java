package eu.bcvsolutions.idm.core.model.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.config.domain.ContractSliceConfiguration;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmContractSliceDto;
import eu.bcvsolutions.idm.core.api.dto.IdmContractSliceGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmEntityStateDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.OperationResultDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractSliceFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractSliceGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmEntityStateFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityContractFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.event.EventContext;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.ContractSliceManager;
import eu.bcvsolutions.idm.core.api.service.EntityStateManager;
import eu.bcvsolutions.idm.core.api.service.IdmContractSliceGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmContractSliceService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.utils.AutowireHelper;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.entity.IdmContractSlice;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.model.event.ContractSliceEvent;
import eu.bcvsolutions.idm.core.model.event.ContractSliceEvent.ContractSliceEventType;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.service.IdmLongRunningTaskService;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.core.scheduler.task.impl.ClearDirtyStateForContractSliceTaskExecutor;
import eu.bcvsolutions.idm.core.scheduler.task.impl.SelectCurrentContractSliceTaskExecutor;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import eu.bcvsolutions.idm.test.api.TestHelper;

/**
 * Integration tests with identity contracts slices
 * 
 * @author svandav
 *
 */
public class ContractSliceManagerTest extends AbstractIntegrationTest {

	@Autowired
	private TestHelper helper;
	@Autowired
	private IdmContractSliceService contractSliceService;
	@Autowired
	private IdmContractSliceGuaranteeService contractGuaranteeService;
	@Autowired
	protected IdmLongRunningTaskService longRunningTaskService;
	@Autowired
	private IdmIdentityContractService contractService;
	@Autowired
	private ContractSliceManager contractSliceManager;
	@Autowired
	private ConfigurationService configurationService;
	@Autowired
	private EntityStateManager entityStateManager;
	@Autowired
	private LongRunningTaskManager longRunningTaskManager;
	@Autowired
	private FormService formService;
	
	private final static String IP = "IP";
	private final static String NUMBER_OF_FINGERS = "NUMBER_OF_FINGERS";
	//

	@Before
	public void init() {
		loginAsAdmin();
	}

	@After
	public void logout() {
		configurationService.setValue(ContractSliceConfiguration.PROPERTY_PROTECTION_INTERVAL, "0");
		//
		super.logout();
	}

	@Test
	public void createSliceValidInPastTest() {
		IdmIdentityDto identity = helper.createIdentity();
		String contractCode = "contract-one";

		IdmContractSliceDto slice = helper.createContractSlice(identity, null, LocalDate.now().minusDays(10), null,
				LocalDate.now().minusDays(5));
		slice.setContractCode(contractCode);
		contractSliceService.save(slice);

		IdmContractSliceFilter filter = new IdmContractSliceFilter();
		filter.setIdentity(identity.getId());
		List<IdmContractSliceDto> results = contractSliceService.find(filter, null).getContent();
		assertEquals(1, results.size());
		IdmContractSliceDto createdSlice = results.get(0);
		assertTrue(createdSlice.isValid());
		assertEquals(null, createdSlice.getValidTill());

		// Check created contract by that slice
		IdmIdentityContractFilter contractFilter = new IdmIdentityContractFilter();
		contractFilter.setIdentity(identity.getId());
		List<IdmIdentityContractDto> resultsContract = contractService.find(filter, null).getContent().stream() //
				.filter(c -> contractService.get(c.getId()).getControlledBySlices()) //
				.collect(Collectors.toList());

		assertEquals(1, resultsContract.size()); //
		IdmIdentityContractDto contract = resultsContract.get(0);
		assertEquals(slice.getContractValidFrom(), contract.getValidFrom());
		assertEquals(slice.getContractValidTill(), contract.getValidTill());
		assertFalse(contract.isValidNowOrInFuture());
	}
	
	@Test
	public void testCreateSliceWithEAVs() {
		IdmIdentityDto identity = helper.createIdentity();
		String contractCode = "contract-one";

		IdmContractSliceDto slice = helper.createContractSlice(identity, null, null, null,
				null);
		slice.setContractCode(contractCode);
		slice = contractSliceService.save(slice);
		
		// Init form definition for identity-contract
		IdmFormDefinitionDto definition = this.initIdentityContractFormDefinition();

		// Create slice with EAV values
		IdmFormInstanceDto formInstanceDto = formService.getFormInstance(slice, definition);
		Assert.assertNotNull(formInstanceDto);
		Assert.assertNotNull(formInstanceDto.getFormDefinition());
		Assert.assertEquals(0, formInstanceDto.getValues().size());
		IdmFormAttributeDto attribute = formInstanceDto.getMappedAttributeByCode(NUMBER_OF_FINGERS);
		formService.saveValues(slice, attribute, Lists.newArrayList(BigDecimal.TEN));
		
		// We need to save slice for invoke save slice to the contract
		slice = contractSliceService.save(slice);
		formInstanceDto = formService.getFormInstance(slice, definition);
		Assert.assertNotNull(formInstanceDto);
		Assert.assertNotNull(formInstanceDto.getFormDefinition());
		Assert.assertEquals(1, formInstanceDto.getValues().size());
		Assert.assertEquals(BigDecimal.TEN.longValue(),
				((BigDecimal) formInstanceDto.getValues().get(0).getValue()).longValue());
		
		IdmContractSliceFilter filter = new IdmContractSliceFilter();
		filter.setIdentity(identity.getId());
		List<IdmContractSliceDto> results = contractSliceService.find(filter, null).getContent();
		assertEquals(1, results.size());
		IdmContractSliceDto createdSlice = results.get(0);
		assertTrue(createdSlice.isValid());
		assertEquals(null, createdSlice.getValidTill());

		// Check created contract by that slice
		IdmIdentityContractFilter contractFilter = new IdmIdentityContractFilter();
		contractFilter.setIdentity(identity.getId());
		List<IdmIdentityContractDto> resultsContract = contractService.find(filter, null).getContent().stream() //
				.filter(c -> contractService.get(c.getId()).getControlledBySlices()) //
				.collect(Collectors.toList());

		assertEquals(1, resultsContract.size()); //
		IdmIdentityContractDto contract = resultsContract.get(0);
		assertEquals(slice.getContractValidFrom(), contract.getValidFrom());
		assertEquals(slice.getContractValidTill(), contract.getValidTill());
		assertTrue(contract.isValidNowOrInFuture());
		
		IdmFormInstanceDto contractFormInstanceDto = formService.getFormInstance(contract);
		Assert.assertNotNull(contractFormInstanceDto);
		Assert.assertNotNull(contractFormInstanceDto.getFormDefinition());
		Assert.assertEquals(1, contractFormInstanceDto.getValues().size());
		Assert.assertEquals(BigDecimal.TEN.longValue(),
				((BigDecimal) contractFormInstanceDto.getValues().get(0).getValue()).longValue());
	}
	
	@Test
	public void testModifiedEAVOnSlice() {
		IdmIdentityDto identity = helper.createIdentity();
		String contractCode = "contract-one";

		IdmContractSliceDto slice = helper.createContractSlice(identity, null, null, null,
				null);
		slice.setContractCode(contractCode);
		slice = contractSliceService.save(slice);
		
		// Init form definition for identity-contract
		IdmFormDefinitionDto definition = this.initIdentityContractFormDefinition();

		// Create slice with EAV values
		IdmFormInstanceDto formInstanceDto = formService.getFormInstance(slice, definition);
		Assert.assertNotNull(formInstanceDto);
		Assert.assertNotNull(formInstanceDto.getFormDefinition());
		Assert.assertEquals(0, formInstanceDto.getValues().size());
		IdmFormAttributeDto attribute = formInstanceDto.getMappedAttributeByCode(NUMBER_OF_FINGERS);
		formService.saveValues(slice, attribute, Lists.newArrayList(BigDecimal.TEN));
		
		// We need to save slice for invoke save slice to the contract
		slice = contractSliceService.save(slice);
		formInstanceDto = formService.getFormInstance(slice, definition);
		Assert.assertNotNull(formInstanceDto);
		Assert.assertNotNull(formInstanceDto.getFormDefinition());
		Assert.assertEquals(1, formInstanceDto.getValues().size());
		Assert.assertEquals(BigDecimal.TEN.longValue(),
				((BigDecimal) formInstanceDto.getValues().get(0).getValue()).longValue());
		
		formService.saveValues(slice, attribute, Lists.newArrayList(BigDecimal.ONE));
		
		// We need to save slice for invoke save slice to the contract
		slice = contractSliceService.save(slice);
		
		IdmContractSliceFilter filter = new IdmContractSliceFilter();
		filter.setIdentity(identity.getId());
		List<IdmContractSliceDto> results = contractSliceService.find(filter, null).getContent();
		assertEquals(1, results.size());
		IdmContractSliceDto createdSlice = results.get(0);
		assertTrue(createdSlice.isValid());
		assertEquals(null, createdSlice.getValidTill());

		// Check created contract by that slice
		IdmIdentityContractFilter contractFilter = new IdmIdentityContractFilter();
		contractFilter.setIdentity(identity.getId());
		List<IdmIdentityContractDto> resultsContract = contractService.find(filter, null).getContent().stream() //
				.filter(c -> contractService.get(c.getId()).getControlledBySlices()) //
				.collect(Collectors.toList());

		assertEquals(1, resultsContract.size()); //
		IdmIdentityContractDto contract = resultsContract.get(0);
		assertEquals(slice.getContractValidFrom(), contract.getValidFrom());
		assertEquals(slice.getContractValidTill(), contract.getValidTill());
		assertTrue(contract.isValidNowOrInFuture());
		
		IdmFormInstanceDto contractFormInstanceDto = formService.getFormInstance(contract);
		Assert.assertNotNull(contractFormInstanceDto);
		Assert.assertNotNull(contractFormInstanceDto.getFormDefinition());
		Assert.assertEquals(1, contractFormInstanceDto.getValues().size());
		Assert.assertEquals(BigDecimal.ONE.longValue(),
				((BigDecimal) contractFormInstanceDto.getValues().get(0).getValue()).longValue());
	}
	
	@Test
	public void testDeleteEAVOnSlice() {
		IdmIdentityDto identity = helper.createIdentity();
		String contractCode = "contract-one";

		IdmContractSliceDto slice = helper.createContractSlice(identity, null, null, null,
				null);
		slice.setContractCode(contractCode);
		slice = contractSliceService.save(slice);
		
		// Init form definition for identity-contract
		IdmFormDefinitionDto definition = this.initIdentityContractFormDefinition();

		// Create slice with EAV values
		IdmFormInstanceDto formInstanceDto = formService.getFormInstance(slice, definition);
		Assert.assertNotNull(formInstanceDto);
		Assert.assertNotNull(formInstanceDto.getFormDefinition());
		Assert.assertEquals(0, formInstanceDto.getValues().size());
		IdmFormAttributeDto attribute = formInstanceDto.getMappedAttributeByCode(NUMBER_OF_FINGERS);
		formService.saveValues(slice, attribute, Lists.newArrayList(BigDecimal.TEN));
		
		// We need to save slice for invoke save slice to the contract
		slice = contractSliceService.save(slice);
		formInstanceDto = formService.getFormInstance(slice, definition);
		Assert.assertNotNull(formInstanceDto);
		Assert.assertNotNull(formInstanceDto.getFormDefinition());
		Assert.assertEquals(1, formInstanceDto.getValues().size());
		Assert.assertEquals(BigDecimal.TEN.longValue(),
				((BigDecimal) formInstanceDto.getValues().get(0).getValue()).longValue());
		
		IdmContractSliceFilter filter = new IdmContractSliceFilter();
		filter.setIdentity(identity.getId());
		List<IdmContractSliceDto> results = contractSliceService.find(filter, null).getContent();
		assertEquals(1, results.size());
		IdmContractSliceDto createdSlice = results.get(0);
		assertTrue(createdSlice.isValid());
		assertEquals(null, createdSlice.getValidTill());

		// Check created contract by that slice
		IdmIdentityContractFilter contractFilter = new IdmIdentityContractFilter();
		contractFilter.setIdentity(identity.getId());
		List<IdmIdentityContractDto> resultsContract = contractService.find(filter, null).getContent().stream() //
				.filter(c -> contractService.get(c.getId()).getControlledBySlices()) //
				.collect(Collectors.toList());

		assertEquals(1, resultsContract.size()); //
		IdmIdentityContractDto contract = resultsContract.get(0);
		assertEquals(slice.getContractValidFrom(), contract.getValidFrom());
		assertEquals(slice.getContractValidTill(), contract.getValidTill());
		assertTrue(contract.isValidNowOrInFuture());
		
		IdmFormInstanceDto contractFormInstanceDto = formService.getFormInstance(contract);
		Assert.assertNotNull(contractFormInstanceDto);
		Assert.assertNotNull(contractFormInstanceDto.getFormDefinition());
		Assert.assertEquals(1, contractFormInstanceDto.getValues().size());
		Assert.assertEquals(BigDecimal.TEN.longValue(),
				((BigDecimal) contractFormInstanceDto.getValues().get(0).getValue()).longValue());
		
		formService.saveValues(slice, attribute, null);
		
		// We need to save slice for invoke save slice to the contract
		slice = contractSliceService.save(slice);
		
		contractFormInstanceDto = formService.getFormInstance(contract);
		Assert.assertNotNull(contractFormInstanceDto);
		Assert.assertNotNull(contractFormInstanceDto.getFormDefinition());
		Assert.assertEquals(0, contractFormInstanceDto.getValues().size());
	}

	@Test
	public void createSliceValidFromSameDateAsSomeExisting() {
		IdmIdentityDto identity = helper.createIdentity();
		String contractCode = "contract-three";

		LocalDate now = LocalDate.now();

		IdmContractSliceDto sliceCurrent = helper.createContractSlice(identity, contractCode, null, now, now,
				now.plusDays(200));

		IdmContractSliceDto sliceCurrentNew = helper.createContractSlice(identity, contractCode, null, now, now,
				now.plusDays(50));

		IdmContractSliceFilter filter = new IdmContractSliceFilter();
		filter.setIdentity(identity.getId());
		List<IdmContractSliceDto> results = contractSliceService.find(filter, null).getContent();
		assertEquals(2, results.size());
		UUID parentContract = results.get(0).getParentContract();
		List<IdmContractSliceDto> slices = contractSliceManager.findAllSlices(parentContract);
		assertEquals(2, slices.size());
		IdmContractSliceDto validSlice = contractSliceManager.findValidSlice(parentContract);
		// Valid slice should be currentSliceNew
		assertEquals(sliceCurrentNew, validSlice);

		IdmContractSliceDto previousSlice = contractSliceManager.findPreviousSlice(validSlice, slices);
		// Previous slice should be sliceCurrent with longer validity
		assertEquals(sliceCurrent, previousSlice);
		assertNotNull(previousSlice.getValidTill());

		// Check created contract by that slice
		IdmIdentityContractFilter contractFilter = new IdmIdentityContractFilter();
		contractFilter.setIdentity(identity.getId());
		List<IdmIdentityContractDto> resultsContract = contractService.find(filter, null).getContent().stream() //
				.filter(c -> contractService.get(c.getId()).getControlledBySlices()) //
				.collect(Collectors.toList());

		assertEquals(1, resultsContract.size());
		// Current slice should be contract
		IdmIdentityContractDto contract = resultsContract.get(0);
		assertTrue(contract.isValid());
		assertEquals(sliceCurrentNew.getContractValidFrom(), contract.getValidFrom());
	}

	@Test
	public void createSliceValidInFutureTest() {
		IdmIdentityDto identity = helper.createIdentity();
		String contractCode = "contract-one";

		IdmContractSliceDto slice = helper.createContractSlice(identity, null, LocalDate.now().plusDays(10), null,
				LocalDate.now().plusDays(100));
		slice.setContractCode(contractCode);
		contractSliceService.save(slice);

		IdmContractSliceFilter filter = new IdmContractSliceFilter();
		filter.setIdentity(identity.getId());
		List<IdmContractSliceDto> results = contractSliceService.find(filter, null).getContent();
		assertEquals(1, results.size());
		IdmContractSliceDto createdSlice = results.get(0);
		assertFalse(createdSlice.isValid());
		assertEquals(null, createdSlice.getValidTill());

		// Check created contract by that slice
		IdmIdentityContractFilter contractFilter = new IdmIdentityContractFilter();
		contractFilter.setIdentity(identity.getId());
		List<IdmIdentityContractDto> resultsContract = contractService.find(filter, null).getContent().stream() //
				.filter(c -> contractService.get(c.getId()).getControlledBySlices()) //
				.collect(Collectors.toList());

		assertEquals(1, resultsContract.size());

		IdmIdentityContractDto contract = resultsContract.get(0);
		assertTrue(contract.isValidNowOrInFuture());
	}

	@Test
	public void createSlicesForOneContractTest() {
		IdmIdentityDto identity = helper.createIdentity();
		String contractCode = "contract-one";

		IdmContractSliceDto sliceFuture = helper.createContractSlice(identity, null, LocalDate.now().plusDays(10), null,
				LocalDate.now().plusDays(100));
		sliceFuture.setContractCode(contractCode);
		contractSliceService.save(sliceFuture);

		IdmContractSliceDto sliceCurrent = helper.createContractSlice(identity, null, LocalDate.now(), null,
				LocalDate.now().plusDays(50));
		sliceCurrent.setContractCode(contractCode);
		contractSliceService.save(sliceCurrent);

		IdmContractSliceDto slicePast = helper.createContractSlice(identity, null, LocalDate.now().minusDays(10), null,
				LocalDate.now().plusDays(200));
		slicePast.setContractCode(contractCode);
		contractSliceService.save(slicePast);

		IdmContractSliceFilter filter = new IdmContractSliceFilter();
		filter.setIdentity(identity.getId());
		List<IdmContractSliceDto> results = contractSliceService.find(filter, null).getContent();
		assertEquals(3, results.size());
		UUID parentContract = results.get(0).getParentContract();
		List<IdmContractSliceDto> slices = contractSliceManager.findAllSlices(parentContract);
		assertEquals(3, slices.size());
		IdmContractSliceDto validSlice = contractSliceManager.findValidSlice(parentContract);
		// Valid slice should be currentSlice
		assertEquals(sliceCurrent, validSlice);

		IdmContractSliceDto nextSlice = contractSliceManager.findNextSlice(validSlice, slices);
		// Next slice should be futureSlice
		assertEquals(sliceFuture, nextSlice);

		IdmContractSliceDto previousSlice = contractSliceManager.findPreviousSlice(validSlice, slices);
		// Previous slice should be pasSlice
		assertEquals(slicePast, previousSlice);

		List<IdmContractSliceDto> unvalidSlices = contractSliceManager.findUnvalidSlices(null).getContent();
		assertEquals(0, unvalidSlices.size());

		// Check created contract by that slice
		IdmIdentityContractFilter contractFilter = new IdmIdentityContractFilter();
		contractFilter.setIdentity(identity.getId());
		List<IdmIdentityContractDto> resultsContract = contractService.find(filter, null).getContent().stream() //
				.filter(c -> contractService.get(c.getId()).getControlledBySlices()) //
				.collect(Collectors.toList());

		assertEquals(1, resultsContract.size());
		// Current slice should be contract
		IdmIdentityContractDto contract = resultsContract.get(0);
		assertTrue(contract.isValid());
		assertEquals(sliceCurrent.getContractValidFrom(), contract.getValidFrom());
	}

	@Test
	public void deleteSliceTest() {
		IdmIdentityDto identity = helper.createIdentity();
		String contractCode = "contract-one";

		IdmContractSliceDto sliceFuture = helper.createContractSlice(identity, null, LocalDate.now().plusDays(10), null,
				LocalDate.now().plusDays(100));
		sliceFuture.setContractCode(contractCode);
		contractSliceService.save(sliceFuture);

		IdmContractSliceDto sliceCurrent = helper.createContractSlice(identity, null, LocalDate.now(), null,
				LocalDate.now().plusDays(50));
		sliceCurrent.setContractCode(contractCode);
		contractSliceService.save(sliceCurrent);

		IdmContractSliceDto slicePast = helper.createContractSlice(identity, null, LocalDate.now().minusDays(10), null,
				LocalDate.now().plusDays(200));
		slicePast.setContractCode(contractCode);
		contractSliceService.save(slicePast);

		IdmContractSliceFilter filter = new IdmContractSliceFilter();
		filter.setIdentity(identity.getId());
		List<IdmContractSliceDto> results = contractSliceService.find(filter, null).getContent();
		assertEquals(3, results.size());
		UUID parentContract = results.get(0).getParentContract();
		List<IdmContractSliceDto> slices = contractSliceManager.findAllSlices(parentContract);
		assertEquals(3, slices.size());
		IdmContractSliceDto validSlice = contractSliceManager.findValidSlice(parentContract);
		// Valid slice should be currentSlice
		assertEquals(sliceCurrent, validSlice);

		// Check created contract by that slice
		IdmIdentityContractFilter contractFilter = new IdmIdentityContractFilter();
		contractFilter.setIdentity(identity.getId());
		List<IdmIdentityContractDto> resultsContract = contractService.find(filter, null).getContent().stream() //
				.filter(c -> contractService.get(c.getId()).getControlledBySlices()) //
				.collect(Collectors.toList());

		assertEquals(1, resultsContract.size());
		// Current slice should be contract
		IdmIdentityContractDto contract = resultsContract.get(0);
		assertTrue(contract.isValid());
		assertEquals(sliceCurrent.getContractValidFrom(), contract.getValidFrom());

		// Delete the current slice
		contractSliceService.delete(contractSliceService.get(sliceCurrent.getId()));

		slices = contractSliceManager.findAllSlices(parentContract);
		assertEquals(2, slices.size());
		validSlice = contractSliceManager.findValidSlice(parentContract);
		// Valid slice should be slicePast now
		assertEquals(slicePast, validSlice);
		List<IdmContractSliceDto> unvalidSlices = contractSliceManager.findUnvalidSlices(null).getContent();
		assertEquals(0, unvalidSlices.size());
		// Reload the contract
		contract = contractService.get(contract.getId());
		assertTrue(contract.isValid());
		assertEquals(slicePast.getContractValidFrom(), contract.getValidFrom());
		assertEquals(slicePast.getContractValidTill(), contract.getValidTill());
	}

	@Test
	public void selectCurrentSliceAsContractLrtTest() {
		IdmIdentityDto identity = helper.createIdentity();
		String contractCode = "contract-one";

		IdmContractSliceDto sliceFuture = helper.createContractSlice(identity, null, LocalDate.now().plusDays(10), null,
				LocalDate.now().plusDays(100));
		sliceFuture.setContractCode(contractCode);
		contractSliceService.save(sliceFuture);

		IdmContractSliceDto sliceCurrent = helper.createContractSlice(identity, null, LocalDate.now(), null,
				LocalDate.now().plusDays(50));
		sliceCurrent.setContractCode(contractCode);
		contractSliceService.save(sliceCurrent);

		IdmContractSliceDto slicePast = helper.createContractSlice(identity, null, LocalDate.now().minusDays(10), null,
				LocalDate.now().plusDays(200));
		slicePast.setContractCode(contractCode);
		contractSliceService.save(slicePast);

		IdmContractSliceFilter filter = new IdmContractSliceFilter();
		filter.setIdentity(identity.getId());
		List<IdmContractSliceDto> results = contractSliceService.find(filter, null).getContent();
		assertEquals(3, results.size());
		UUID parentContract = results.get(0).getParentContract();
		List<IdmContractSliceDto> slices = contractSliceManager.findAllSlices(parentContract);
		assertEquals(3, slices.size());
		IdmContractSliceDto validSlice = contractSliceManager.findValidSlice(parentContract);
		// Valid slice should be currentSlice
		assertEquals(sliceCurrent, validSlice);

		// Check created contract by that slice
		IdmIdentityContractFilter contractFilter = new IdmIdentityContractFilter();
		contractFilter.setIdentity(identity.getId());
		List<IdmIdentityContractDto> resultsContract = contractService.find(filter, null).getContent().stream() //
				.filter(c -> contractService.get(c.getId()).getControlledBySlices()) //
				.collect(Collectors.toList());

		assertEquals(1, resultsContract.size());
		// Current slice should be contract
		IdmIdentityContractDto contract = resultsContract.get(0);
		assertTrue(contract.isValid());
		assertEquals(sliceCurrent.getContractValidFrom(), contract.getValidFrom());
		// None invalid slices
		List<IdmContractSliceDto> unvalidSlices = contractSliceManager.findUnvalidSlices(null).getContent();
		assertEquals(0, unvalidSlices.size());

		// Set current slice as not currently using
		sliceCurrent = contractSliceService.get(sliceCurrent.getId());
		sliceCurrent.setUsingAsContract(false);
		// Save without recalculation
		contractSliceService.publish(new ContractSliceEvent(ContractSliceEventType.UPDATE, sliceCurrent,
				ImmutableMap.of(IdmContractSliceService.SKIP_RECALCULATE_CONTRACT_SLICE, Boolean.TRUE)));

		// One invalid slice
		unvalidSlices = contractSliceManager.findUnvalidSlices(null).getContent();
		assertEquals(1, unvalidSlices.size());

		SelectCurrentContractSliceTaskExecutor lrt = new SelectCurrentContractSliceTaskExecutor();
		AutowireHelper.autowire(lrt);
		OperationResult result = lrt.process();
		assertEquals(OperationState.EXECUTED, result.getState());
	}

	@Test
	public void changeSlicesForTwoContractTest() {
		IdmIdentityDto identity = helper.createIdentity();
		String contractCodeOne = "contract-one";
		String contractCodeTwo = "contract-two";

		IdmContractSliceDto sliceFuture = helper.createContractSlice(identity, null, LocalDate.now().plusDays(10), null,
				LocalDate.now().plusDays(100));
		sliceFuture.setContractCode(contractCodeOne);
		contractSliceService.save(sliceFuture);

		IdmContractSliceDto sliceCurrentTwo = helper.createContractSlice(identity, null, LocalDate.now(), null,
				LocalDate.now().plusDays(50));
		sliceCurrentTwo.setContractCode(contractCodeTwo);
		contractSliceService.save(sliceCurrentTwo);

		IdmContractSliceDto slicePastTwo = helper.createContractSlice(identity, null, LocalDate.now().minusDays(10),
				null, LocalDate.now().plusDays(200));
		slicePastTwo.setContractCode(contractCodeTwo);
		contractSliceService.save(slicePastTwo);

		IdmContractSliceDto slicePast = helper.createContractSlice(identity, null, LocalDate.now().minusDays(10), null,
				LocalDate.now().plusDays(200));
		slicePast.setContractCode(contractCodeOne);
		contractSliceService.save(slicePast);

		IdmContractSliceFilter filter = new IdmContractSliceFilter();
		filter.setIdentity(identity.getId());
		List<IdmContractSliceDto> results = contractSliceService.find(filter, null).getContent();
		assertEquals(4, results.size());

		// Get contract for contractCodeOne
		UUID parentContractOne = results.stream().filter(s -> s.getContractCode().equals(contractCodeOne)).findFirst()
				.get().getParentContract();
		List<IdmContractSliceDto> slices = contractSliceManager.findAllSlices(parentContractOne);
		assertEquals(2, slices.size());
		IdmContractSliceDto validSlice = contractSliceManager.findValidSlice(parentContractOne);
		// Valid slice should be slicePast now
		assertEquals(slicePast, validSlice);

		IdmContractSliceDto nextSlice = contractSliceManager.findNextSlice(validSlice, slices);
		// Next slice should be futureSlice
		assertEquals(sliceFuture, nextSlice);

		IdmContractSliceDto previousSlice = contractSliceManager.findPreviousSlice(validSlice, slices);
		// Previous slice should be null
		assertNull(previousSlice);

		List<IdmContractSliceDto> unvalidSlices = contractSliceManager.findUnvalidSlices(null).getContent();
		assertEquals(0, unvalidSlices.size());

		// Get contract for contractCodeTwo
		UUID parentContractTwo = results.stream().filter(s -> s.getContractCode().equals(contractCodeTwo)).findFirst()
				.get().getParentContract();
		List<IdmContractSliceDto> slicesTwo = contractSliceManager.findAllSlices(parentContractTwo);
		assertEquals(2, slicesTwo.size());
		IdmContractSliceDto validSliceTwo = contractSliceManager.findValidSlice(parentContractTwo);
		// Valid slice should be sliceCurrentTwo now
		assertEquals(sliceCurrentTwo, validSliceTwo);

		// Check created contract by that slice
		IdmIdentityContractFilter contractFilter = new IdmIdentityContractFilter();
		contractFilter.setIdentity(identity.getId());
		List<IdmIdentityContractDto> resultsContract = contractService.find(filter, null).getContent().stream() //
				.filter(c -> contractService.get(c.getId()).getControlledBySlices()) //
				.collect(Collectors.toList());

		// Two contract controlled by slices must exists now
		assertEquals(2, resultsContract.size());
		// Past slice should be contract
		IdmIdentityContractDto contract = resultsContract.stream().filter(c -> c.getId().equals(parentContractOne))
				.findFirst().get();
		assertTrue(contract.isValid());
		assertEquals(slicePast.getContractValidFrom(), contract.getValidFrom());

		// Change parent contract from Two to One
		sliceCurrentTwo.setContractCode(contractCodeOne);
		contractSliceService.save(sliceCurrentTwo);

		// Check slice for contractCodeTwo
		slicesTwo = contractSliceManager.findAllSlices(parentContractTwo);
		assertEquals(1, slicesTwo.size());
		validSliceTwo = contractSliceManager.findValidSlice(parentContractTwo);
		// Valid slice should be sliceCurrentTwo now
		assertEquals(slicePastTwo, validSliceTwo);
		assertTrue(validSliceTwo.isUsingAsContract());

		// Check slice for contractCodeOne
		slices = contractSliceManager.findAllSlices(parentContractOne);
		assertEquals(3, slices.size());
		validSlice = contractSliceManager.findValidSlice(parentContractOne);
		// Valid slice should be currentSliceTwo now
		assertEquals(sliceCurrentTwo, validSlice);

		nextSlice = contractSliceManager.findNextSlice(validSlice, slices);
		// Next slice should be futureSlice
		assertEquals(sliceFuture, nextSlice);

		previousSlice = contractSliceManager.findPreviousSlice(validSlice, slices);
		// Previous slice should be pastSlice
		assertEquals(slicePast, previousSlice);

		unvalidSlices = contractSliceManager.findUnvalidSlices(null).getContent();
		assertEquals(0, unvalidSlices.size());

		// Check created contract by that slice
		resultsContract = contractService.find(filter, null).getContent().stream() //
				.filter(c -> contractService.get(c.getId()).getControlledBySlices()) //
				.collect(Collectors.toList());

		assertEquals(2, resultsContract.size());

		// Change parent contract from Two to One (slicePastTwo)
		slicePastTwo.setContractCode(contractCodeOne);
		contractSliceService.save(slicePastTwo);

		// Contract TWO was deleted
		resultsContract = contractService.find(filter, null).getContent().stream() //
				.filter(c -> contractService.get(c.getId()).getControlledBySlices()) //
				.collect(Collectors.toList());

		assertEquals(1, resultsContract.size());

		// Past slice should be contract
		contract = resultsContract.get(0);
		assertTrue(contract.isValid());
		assertEquals(sliceCurrentTwo.getContractValidFrom(), contract.getValidFrom());

	}

	@Test
	public void testReferentialIntegrityOnIdentityDelete() {
		// prepare data
		IdmIdentityDto identity = helper.createIdentity();
		IdmIdentityDto identityWithContract = helper.createIdentity();
		IdmContractSliceDto slice = helper.createContractSlice(identityWithContract);
		helper.createContractSliceGuarantee(slice.getId(), identity.getId());
		//
		IdmContractSliceGuaranteeFilter filter = new IdmContractSliceGuaranteeFilter();
		filter.setContractSliceId(slice.getId());
		List<IdmContractSliceGuaranteeDto> guarantees = contractGuaranteeService.find(filter, null).getContent();
		assertEquals(1, guarantees.size());
		//
		helper.deleteIdentity(identity.getId());
		//
		guarantees = contractGuaranteeService.find(filter, null).getContent();
		assertEquals(0, guarantees.size());
	}

	@Test
	public void testReferentialIntegrityOnContractDelete() {
		// prepare data
		IdmIdentityDto identity = helper.createIdentity();
		IdmIdentityDto identityWithContract = helper.createIdentity();
		IdmContractSliceDto slice = helper.createContractSlice(identityWithContract);
		helper.createContractSliceGuarantee(slice.getId(), identity.getId());
		//
		IdmContractSliceGuaranteeFilter filter = new IdmContractSliceGuaranteeFilter();
		filter.setGuaranteeId(identity.getId());
		List<IdmContractSliceGuaranteeDto> guarantees = contractGuaranteeService.find(filter, null).getContent();
		assertEquals(1, guarantees.size());
		//
		contractSliceService.deleteById(slice.getId());
		//
		guarantees = contractGuaranteeService.find(filter, null).getContent();
		assertEquals(0, guarantees.size());
	}

	@Test
	public void identityFilterTest() {
		IdmIdentityDto identity = helper.createIdentity();

		IdmTreeNodeDto node = helper.createTreeNode();
		IdmTreeNodeDto node2 = helper.createTreeNode();

		IdmContractSliceDto slice = helper.createContractSlice(identity, node, null, null, null);
		IdmContractSliceDto slice2 = helper.createContractSlice(identity, node2, null, null, null);

		IdmContractSliceFilter filter = new IdmContractSliceFilter();
		filter.setIdentity(identity.getId());
		Page<IdmContractSliceDto> result = contractSliceService.find(filter, null);
		assertEquals("Wrong Identity", 2, result.getTotalElements());
		assertTrue(result.getContent().contains(slice));
		assertTrue(result.getContent().contains(slice2));
	}

	@Test
	public void externeFilterTest() {
		IdmIdentityDto identity = helper.createIdentity();
		IdmIdentityDto identity2 = helper.createIdentity();

		IdmTreeNodeDto node = helper.createTreeNode();
		IdmTreeNodeDto node2 = helper.createTreeNode();

		IdmContractSliceDto slice = helper.createContractSlice(identity, node, null, null, null);
		IdmContractSliceDto slice2 = helper.createContractSlice(identity2, node2, null, null, null);

		slice.setExterne(true);
		contractSliceService.save(slice);

		slice2.setExterne(false);
		contractSliceService.save(slice2);

		IdmContractSliceFilter filter = new IdmContractSliceFilter();
		filter.setExterne(true);
		Page<IdmContractSliceDto> result = contractSliceService.find(filter, null);
		assertTrue(result.getContent().contains(slice));
		assertFalse(result.getContent().contains(slice2));

		filter.setExterne(false);
		result = contractSliceService.find(filter, null);
		assertTrue(result.getContent().contains(slice2));
		assertFalse(result.getContent().contains(slice));
	}

	@Test
	public void mainFilterTest() {
		IdmIdentityDto identity = helper.createIdentity();
		IdmIdentityDto identity2 = helper.createIdentity();

		IdmTreeNodeDto node = helper.createTreeNode();
		IdmTreeNodeDto node2 = helper.createTreeNode();

		IdmContractSliceDto slice = helper.createContractSlice(identity, node, null, null, null);
		IdmContractSliceDto slice2 = helper.createContractSlice(identity2, node2, null, null, null);

		slice.setMain(true);
		contractSliceService.save(slice);

		slice2.setMain(false);
		contractSliceService.save(slice2);

		IdmContractSliceFilter filter = new IdmContractSliceFilter();
		filter.setMain(true);
		Page<IdmContractSliceDto> result = contractSliceService.find(filter, null);
		assertTrue(result.getContent().contains(slice));
		assertFalse(result.getContent().contains(slice2));

		filter.setMain(false);
		result = contractSliceService.find(filter, null);
		assertTrue(result.getContent().contains(slice2));
		assertFalse(result.getContent().contains(slice));
	}

	@Test
	public void contractValidityProtectionModeDisabledTest() {
		configurationService.setValue(ContractSliceConfiguration.PROPERTY_PROTECTION_INTERVAL, "0");
		IdmIdentityDto identity = helper.createIdentity();
		IdmContractSliceDto sliceOne = helper.createContractSlice(identity, "11", null, LocalDate.now().minusDays(100),
				LocalDate.now().minusDays(100), LocalDate.now().plusDays(5));
		IdmContractSliceDto sliceTwo = helper.createContractSlice(identity, "11", null, LocalDate.now().plusDays(10),
				LocalDate.now().plusDays(10), LocalDate.now().plusDays(100));

		assertNotNull(sliceOne.getParentContract());
		assertNotNull(sliceTwo.getParentContract());
		assertEquals(sliceOne.getParentContract(), sliceTwo.getParentContract());
		assertTrue(sliceOne.isUsingAsContract());
		assertFalse(sliceTwo.isUsingAsContract());

		IdmIdentityContractDto contract = contractService.get(sliceOne.getParentContract());

		// Protection mode is disabled, contract must have validity fields same as slice
		// One
		assertEquals(sliceOne.getContractValidFrom(), contract.getValidFrom());
		assertEquals(sliceOne.getContractValidTill(), contract.getValidTill());
	}

	@Test
	public void contractValidityProtectionModeEnabledTest() {
		// Enable protection mode (5 days, gap is 5 days)
		configurationService.setValue(ContractSliceConfiguration.PROPERTY_PROTECTION_INTERVAL, "5");

		IdmIdentityDto identity = helper.createIdentity();
		IdmContractSliceDto sliceOne = helper.createContractSlice(identity, "11", null, LocalDate.now().minusDays(100),
				LocalDate.now().minusDays(100), LocalDate.now().plusDays(5));
		IdmContractSliceDto sliceTwo = helper.createContractSlice(identity, "11", null, LocalDate.now().plusDays(10),
				LocalDate.now().plusDays(10), LocalDate.now().plusDays(100));

		assertNotNull(sliceOne.getParentContract());
		assertNotNull(sliceTwo.getParentContract());
		assertEquals(sliceOne.getParentContract(), sliceTwo.getParentContract());
		assertTrue(sliceOne.isUsingAsContract());
		assertFalse(sliceTwo.isUsingAsContract());

		IdmIdentityContractDto contract = contractService.get(sliceOne.getParentContract());

		// Protection mode is enabled
		assertEquals(sliceOne.getContractValidFrom(), contract.getValidFrom());
		assertEquals(sliceTwo.getContractValidFrom(), contract.getValidTill());
	}

	@Test
	public void contractValidityProtectionModeEnabledExpiredTest() {
		// Enable protection mode (4 days, gap is 5 days)
		configurationService.setValue(ContractSliceConfiguration.PROPERTY_PROTECTION_INTERVAL, "4");

		IdmIdentityDto identity = helper.createIdentity();
		IdmContractSliceDto sliceOne = helper.createContractSlice(identity, "11", null, LocalDate.now().minusDays(100),
				LocalDate.now().minusDays(100), LocalDate.now().plusDays(5));
		IdmContractSliceDto sliceTwo = helper.createContractSlice(identity, "11", null, LocalDate.now().plusDays(10),
				LocalDate.now().plusDays(10), LocalDate.now().plusDays(100));

		assertNotNull(sliceOne.getParentContract());
		assertNotNull(sliceTwo.getParentContract());
		assertEquals(sliceOne.getParentContract(), sliceTwo.getParentContract());
		assertTrue(sliceOne.isUsingAsContract());
		assertFalse(sliceTwo.isUsingAsContract());

		IdmIdentityContractDto contract = contractService.get(sliceOne.getParentContract());

		// Protection mode is enabled, but gap was too long
		assertEquals(sliceOne.getContractValidFrom(), contract.getValidFrom());
		assertEquals(sliceOne.getContractValidTill(), contract.getValidTill());
	}
	
	@Test
	public void contractValidityProtectionModeEnabledWithoutGapTest() {
		// Enable protection mode (4 days, gap is 0 days)
		configurationService.setValue(ContractSliceConfiguration.PROPERTY_PROTECTION_INTERVAL, "4");

		IdmIdentityDto identity = helper.createIdentity();
		IdmContractSliceDto sliceOne = helper.createContractSlice(identity, "11", null, LocalDate.now().minusDays(100),
				LocalDate.now().minusDays(100), LocalDate.now().plusDays(5));
		IdmContractSliceDto sliceTwo = helper.createContractSlice(identity, "11", null, LocalDate.now().plusDays(10),
				LocalDate.now().minusDays(100), LocalDate.now().plusDays(5));

		assertNotNull(sliceOne.getParentContract());
		assertNotNull(sliceTwo.getParentContract());
		assertEquals(sliceOne.getParentContract(), sliceTwo.getParentContract());
		assertTrue(sliceOne.isUsingAsContract());
		assertFalse(sliceTwo.isUsingAsContract());

		IdmIdentityContractDto contract = contractService.get(sliceOne.getParentContract());

		// Protection mode is enabled, but gap was too small
		assertEquals(sliceOne.getContractValidFrom(), contract.getValidFrom());
		assertEquals(sliceOne.getContractValidTill(), contract.getValidTill());
	}
	
	@Test
	public void contractValidityProtectionModeEnabledNegativeGapTest() {
		// Enable protection mode (4 days, gap is -10 days)
		configurationService.setValue(ContractSliceConfiguration.PROPERTY_PROTECTION_INTERVAL, "4");

		IdmIdentityDto identity = helper.createIdentity();
		IdmContractSliceDto sliceOne = helper.createContractSlice(identity, "11", null, LocalDate.now().minusDays(100),
				LocalDate.now().minusDays(100), LocalDate.now().plusDays(5));
		IdmContractSliceDto sliceTwo = helper.createContractSlice(identity, "11", null, LocalDate.now().plusDays(10),
				LocalDate.now().minusDays(100).minusDays(10), LocalDate.now().plusDays(5));

		assertNotNull(sliceOne.getParentContract());
		assertNotNull(sliceTwo.getParentContract());
		assertEquals(sliceOne.getParentContract(), sliceTwo.getParentContract());
		assertTrue(sliceOne.isUsingAsContract());
		assertFalse(sliceTwo.isUsingAsContract());

		IdmIdentityContractDto contract = contractService.get(sliceOne.getParentContract());

		// Protection mode is enabled, but gap was too small
		assertEquals(sliceOne.getContractValidFrom(), contract.getValidFrom());
		assertEquals(sliceOne.getContractValidTill(), contract.getValidTill());
	}



	@Test
	public void contractValidityProtectionModeEnabledInfinityTest() {
		// Enable protection mode (1 days)
		configurationService.setValue(ContractSliceConfiguration.PROPERTY_PROTECTION_INTERVAL, "1");

		IdmIdentityDto identity = helper.createIdentity();
		IdmContractSliceDto sliceOne = helper.createContractSlice(identity, "11", null, LocalDate.now().minusDays(100),
				LocalDate.now().minusDays(100), LocalDate.now().plusDays(5));
		IdmContractSliceDto sliceTwo = helper.createContractSlice(identity, "11", null, LocalDate.now().plusDays(10),
				null, LocalDate.now().plusDays(100));

		assertNotNull(sliceOne.getParentContract());
		assertNotNull(sliceTwo.getParentContract());
		assertEquals(sliceOne.getParentContract(), sliceTwo.getParentContract());
		assertTrue(sliceOne.isUsingAsContract());
		assertFalse(sliceTwo.isUsingAsContract());

		IdmIdentityContractDto contract = contractService.get(sliceOne.getParentContract());

		// Protection mode is enabled, next slice has contract valid from sets to null
		assertEquals(null, contract.getValidTill());
		assertEquals(sliceOne.getContractValidFrom(), contract.getValidFrom());
	}

	@Test
	public void contractValidityProtectionModeDisableInfinityTest() {
		// Disable protection mode (0 days)
		configurationService.setValue(ContractSliceConfiguration.PROPERTY_PROTECTION_INTERVAL, "0");

		IdmIdentityDto identity = helper.createIdentity();
		IdmContractSliceDto sliceOne = helper.createContractSlice(identity, "11", null, LocalDate.now().minusDays(100),
				LocalDate.now().minusDays(100), LocalDate.now().plusDays(5));
		IdmContractSliceDto sliceTwo = helper.createContractSlice(identity, "11", null, LocalDate.now().plusDays(10),
				null, LocalDate.now().plusDays(100));

		assertNotNull(sliceOne.getParentContract());
		assertNotNull(sliceTwo.getParentContract());
		assertEquals(sliceOne.getParentContract(), sliceTwo.getParentContract());
		assertTrue(sliceOne.isUsingAsContract());
		assertFalse(sliceTwo.isUsingAsContract());

		IdmIdentityContractDto contract = contractService.get(sliceOne.getParentContract());

		// Protection mode is disabled, next slice has contract valid from sets to null
		assertEquals(sliceOne.getContractValidFrom(), contract.getValidFrom());
		assertEquals(sliceOne.getContractValidTill(), contract.getValidTill());
	}

	@Test
	public void skipRecalculationTest() {
		IdmIdentityDto identity = this.getHelper().createIdentity();

		// remove all contracts
		List<IdmIdentityContractDto> allByIdentity = contractService.findAllByIdentity(identity.getId());
		allByIdentity.forEach(contract -> {
			contractService.delete(contract);
		});

		IdmContractSliceDto slice = new IdmContractSliceDto();
		slice.setContractCode("test");
		slice.setIdentity(identity.getId());
		slice.setValidFrom(LocalDate.now().minusDays(5));
		slice.setValidTill(LocalDate.now().plusDays(5));
		slice.setContractValidFrom(LocalDate.now().minusDays(5));
		slice.setMain(true);

		EventContext<IdmContractSliceDto> eventContext = contractSliceService.publish(new ContractSliceEvent(ContractSliceEventType.CREATE, slice,
				ImmutableMap.of(IdmContractSliceService.SKIP_RECALCULATE_CONTRACT_SLICE, Boolean.TRUE)));

		// slice has skip recalculation and dirty state isn't create
		allByIdentity = contractService.findAllByIdentity(identity.getId());
		assertTrue(allByIdentity.isEmpty());
		List<IdmEntityStateDto> dirtyStates = findDirtyStatesForSlice(null);
		assertTrue(dirtyStates.isEmpty());

		// Delete unused slice
		contractSliceService.delete(eventContext.getContent());
	}

	@Test
	public void setDirtyStateTest() {
		IdmIdentityDto identity = this.getHelper().createIdentity();

		// remove all contracts
		List<IdmIdentityContractDto> allByIdentity = contractService.findAllByIdentity(identity.getId());
		allByIdentity.forEach(contract -> {
			contractService.delete(contract);
		});

		IdmContractSliceDto slice = new IdmContractSliceDto();
		slice.setContractCode("test");
		slice.setIdentity(identity.getId());
		slice.setValidFrom(LocalDate.now().minusDays(5));
		slice.setValidTill(LocalDate.now().plusDays(5));
		slice.setContractValidFrom(LocalDate.now().minusDays(5));
		slice.setMain(true);
		
		EventContext<IdmContractSliceDto> eventContext = contractSliceService.publish(new ContractSliceEvent(ContractSliceEventType.CREATE, slice,
				ImmutableMap.of(IdmContractSliceService.SET_DIRTY_STATE_CONTRACT_SLICE, Boolean.TRUE)));

		// slice has skip recalculation and dirty state isn't create
		allByIdentity = contractService.findAllByIdentity(identity.getId());
		assertTrue(allByIdentity.isEmpty());
		List<IdmEntityStateDto> dirtyStates = findDirtyStatesForSlice(null);
		assertFalse(dirtyStates.isEmpty());
		assertEquals(1, dirtyStates.size());

		// delete the states
		entityStateManager.deleteState(dirtyStates.get(0));

		// Delete unused slice
		contractSliceService.delete(eventContext.getContent());
	}

	@Test
	public void clearStateExecutorTest() {
		IdmIdentityDto identity = this.getHelper().createIdentity();

		// remove all contracts
		List<IdmIdentityContractDto> allByIdentity = contractService.findAllByIdentity(identity.getId());
		allByIdentity.forEach(contract -> {
			contractService.delete(contract);
		});

		IdmContractSliceDto slice = new IdmContractSliceDto();
		slice.setContractCode("test");
		slice.setIdentity(identity.getId());
		slice.setValidFrom(LocalDate.now().minusDays(1));
		slice.setValidTill(LocalDate.now().plusDays(5));
		slice.setContractValidFrom(LocalDate.now().minusDays(50));
		slice.setMain(true);
		
		IdmContractSliceDto sliceTwo = new IdmContractSliceDto();
		sliceTwo.setContractCode("test");
		sliceTwo.setIdentity(identity.getId());
		sliceTwo.setValidFrom(LocalDate.now().minusDays(10));
		sliceTwo.setValidTill(LocalDate.now().minusDays(2));
		sliceTwo.setContractValidFrom(LocalDate.now().minusDays(50));
		sliceTwo.setMain(true);

		EventContext<IdmContractSliceDto> eventContextOne = contractSliceService.publish(new ContractSliceEvent(ContractSliceEventType.CREATE, slice,
				ImmutableMap.of(IdmContractSliceService.SET_DIRTY_STATE_CONTRACT_SLICE, Boolean.TRUE)));
		
		EventContext<IdmContractSliceDto> eventContextTwo = contractSliceService.publish(new ContractSliceEvent(ContractSliceEventType.CREATE, sliceTwo,
				ImmutableMap.of(IdmContractSliceService.SET_DIRTY_STATE_CONTRACT_SLICE, Boolean.TRUE)));

		// slice has skip recalculation and dirty state isn't create
		allByIdentity = contractService.findAllByIdentity(identity.getId());
		assertTrue(allByIdentity.isEmpty());
		List<IdmEntityStateDto> dirtyStates = findDirtyStatesForSlice(null);
		assertFalse(dirtyStates.isEmpty());
		assertEquals(2, dirtyStates.size());

		ClearDirtyStateForContractSliceTaskExecutor executor = new ClearDirtyStateForContractSliceTaskExecutor();
		OperationResult result = longRunningTaskManager.executeSync(executor);
		assertEquals(OperationState.EXECUTED, result.getState());

		dirtyStates = findDirtyStatesForSlice(null);
		assertTrue(dirtyStates.isEmpty());
		assertEquals(0, dirtyStates.size());

		allByIdentity = contractService.findAllByIdentity(identity.getId());
		assertEquals(1, allByIdentity.size());
		IdmIdentityContractDto contractDto = allByIdentity.get(0);
		assertTrue(contractDto.getControlledBySlices());
		assertEquals(LocalDate.now().minusDays(50), contractDto.getValidFrom());

		// Delete unused slices
		contractSliceService.delete(eventContextOne.getContent());
		contractSliceService.delete(eventContextTwo.getContent());
	}

	@Test
	public void clearStateExecutorPageTest() {
		IdmIdentityDto identity = this.getHelper().createIdentity();

		List<IdmEntityStateDto> dirtyStates = findAllDirtyStatesForSlices();
		assertEquals(0, dirtyStates.size());

		for (int index = 0; index < 21; index++) {
			IdmContractSliceDto createContractSlice = this.getHelper().createContractSlice(identity);
			createDirtyState(createContractSlice);
		}

		dirtyStates = findAllDirtyStatesForSlices();
		assertEquals(21, dirtyStates.size());

		ClearDirtyStateForContractSliceTaskExecutor executor = new ClearDirtyStateForContractSliceTaskExecutor();
		OperationResult result = longRunningTaskManager.executeSync(executor);
		assertEquals(OperationState.EXECUTED, result.getState());

		dirtyStates = findAllDirtyStatesForSlices();
		assertEquals(0, dirtyStates.size());
		
		IdmLongRunningTaskDto taskDto = longRunningTaskService.get(executor.getLongRunningTaskId());

		assertNotNull(taskDto.getCount());
		assertNotNull(taskDto.getCounter());
		assertEquals(21, taskDto.getCount().longValue());
		assertEquals(21, taskDto.getCounter().longValue());
	}

	@Test
	public void setDirtyStateAndReferentialIntegrityTest() {
		IdmIdentityDto identity = this.getHelper().createIdentity();

		// remove all contracts
		List<IdmIdentityContractDto> allByIdentity = contractService.findAllByIdentity(identity.getId());
		allByIdentity.forEach(contract -> {
			contractService.delete(contract);
		});

		IdmContractSliceDto slice = new IdmContractSliceDto();
		slice.setContractCode("test");
		slice.setIdentity(identity.getId());
		slice.setValidFrom(LocalDate.now().minusDays(5));
		slice.setValidTill(LocalDate.now().plusDays(5));
		slice.setContractValidFrom(LocalDate.now().minusDays(5));
		slice.setMain(true);
		
		EventContext<IdmContractSliceDto> context = contractSliceService.publish(new ContractSliceEvent(ContractSliceEventType.CREATE, slice,
				ImmutableMap.of(IdmContractSliceService.SET_DIRTY_STATE_CONTRACT_SLICE, Boolean.TRUE)));

		IdmContractSliceDto sliceDto = context.getContent();
		
		// slice has skip recalculation and dirty state isn't create
		allByIdentity = contractService.findAllByIdentity(identity.getId());
		assertTrue(allByIdentity.isEmpty());
		List<IdmEntityStateDto> dirtyStates = findDirtyStatesForSlice(sliceDto.getId());
		assertFalse(dirtyStates.isEmpty());
		assertEquals(1, dirtyStates.size());
		
		contractSliceService.delete(sliceDto);

		dirtyStates = findDirtyStatesForSlice(sliceDto.getId());
		assertTrue(dirtyStates.isEmpty());
	}

	/**
	 * Find dirty states for contract slice
	 *
	 * @param pageable
	 * @return 
	 * @return
	 */
	private List<IdmEntityStateDto> findDirtyStatesForSlice(UUID sliceId) {
		IdmEntityStateFilter filter = new IdmEntityStateFilter();
		filter.setResultCode(CoreResultCode.DIRTY_STATE.getCode());
		filter.setOwnerType(IdmContractSlice.class.getName());
		filter.setOwnerId(sliceId);
		return entityStateManager.findStates(filter, null).getContent();
	}

	/**
	 * Create new dirty state for contract slice
	 *
	 * @param slice
	 * @param parameters
	 * @return
	 */
	private IdmEntityStateDto createDirtyState(IdmContractSliceDto slice) {
		Map<String, Object> transformedMarameters = new HashMap<String, Object>();
		transformedMarameters.put("entityId", slice.getId());
		
		DefaultResultModel resultModel = new DefaultResultModel(CoreResultCode.DIRTY_STATE, transformedMarameters);
		IdmEntityStateDto dirtyState = new IdmEntityStateDto();
		dirtyState.setResult(
				new OperationResultDto
					.Builder(OperationState.BLOCKED)
					.setModel(resultModel)
					.build());
		return entityStateManager.saveState(slice, dirtyState);
	}

	/**
	 * Find all dirty states for contract slices
	 *
	 * @param pageable
	 * @return
	 */
	private List<IdmEntityStateDto> findAllDirtyStatesForSlices() {
		IdmEntityStateFilter filter = new IdmEntityStateFilter();
		filter.setResultCode(CoreResultCode.DIRTY_STATE.getCode());
		filter.setOwnerType(IdmContractSlice.class.getName());
		return entityStateManager.findStates(filter, null).getContent();
	}
	
	private IdmFormDefinitionDto initIdentityContractFormDefinition() {

		IdmFormDefinitionDto definition = formService.getDefinition(IdmIdentityContract.class);
		
		if (definition.getMappedAttributeByCode(IP) == null) {
			IdmFormAttributeDto ipAttribute = new IdmFormAttributeDto(IP);
			ipAttribute.setPersistentType(PersistentType.TEXT);
			ipAttribute.setRequired(false);
			ipAttribute.setDefaultValue(getHelper().createName());
			ipAttribute.setFormDefinition(definition.getId());
			formService.saveAttribute(ipAttribute);
		}

		if (definition.getMappedAttributeByCode(NUMBER_OF_FINGERS) == null) {
			IdmFormAttributeDto numberOfFingersAttribute = new IdmFormAttributeDto(NUMBER_OF_FINGERS);
			numberOfFingersAttribute.setPersistentType(PersistentType.DOUBLE);
			numberOfFingersAttribute.setRequired(false);
			numberOfFingersAttribute.setMax(BigDecimal.TEN);
			numberOfFingersAttribute.setFormDefinition(definition.getId());
			formService.saveAttribute(numberOfFingersAttribute);
		}
		
		return formService.getDefinition(IdmIdentityContract.class);
	}
}
