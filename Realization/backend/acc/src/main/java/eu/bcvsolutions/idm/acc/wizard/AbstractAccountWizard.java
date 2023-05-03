package eu.bcvsolutions.idm.acc.wizard;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccountWizardDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemAttributeMappingFilter;
import eu.bcvsolutions.idm.acc.entity.AccAccount;
import eu.bcvsolutions.idm.acc.entity.SysSystemAttributeMapping_;
import eu.bcvsolutions.idm.acc.service.api.AccountWizardsService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmFormDefinitionFilter;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;

public abstract class AbstractAccountWizard implements AccountWizardsService {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AbstractAccountWizard.class);

	public static final String STEP_FIRST = "accountNew";
	public static final String STEP_LAST = "accountRecapitulation";

	private String beanName; // spring bean name - used as id

	private final SysSystemMappingService systemMappingService;
	private final SysSystemAttributeMappingService systemAttributeMappingService;
	private final IdmFormDefinitionService formDefinitionService;

	protected AbstractAccountWizard(SysSystemMappingService systemMappingService, SysSystemAttributeMappingService systemAttributeMappingService, IdmFormDefinitionService formDefinitionService) {
		this.systemMappingService = systemMappingService;
		this.systemAttributeMappingService = systemAttributeMappingService;
		this.formDefinitionService = formDefinitionService;
	}

	@Override
	public AccountWizardDto execute(AccountWizardDto wizardDto) {
		Assert.notNull(wizardDto.getWizardStepName(), "Wizard step name have to be filled for execute a connector type.");
		if (STEP_LAST.equals(wizardDto.getWizardStepName())) {
			// save all after last step = recapitulation
			executeStepFinish(wizardDto);
		} else if (STEP_FIRST.equals(wizardDto.getWizardStepName())) {
			wizardDto = executeFirstStep(wizardDto);
		}
		return wizardDto;
	}

	@Override
	public void setBeanName(String name) {
		this.beanName = name;
	}

	@Override
	public String getId() {
		return beanName;
	}

	abstract protected AccountWizardDto executeFirstStep(AccountWizardDto wizardDto);

	abstract protected void executeStepFinish(AccountWizardDto wizardDto);

	protected IdmFormDefinitionDto getFormDefinition(Map<String, String> metadata) {
		String systemId = metadata.getOrDefault("system", null);
		final UUID objectClassId = Optional.ofNullable(metadata.getOrDefault("systemMapping", null))
				.map(UUID::fromString)
				.map(systemMappingService::get)
				.map(SysSystemMappingDto::getObjectClass).orElse(null);

		IdmFormDefinitionFilter formDefinitionFilter = new IdmFormDefinitionFilter();
		formDefinitionFilter.setType(AccAccount.class.getName());
		final StringBuilder formDefinitionSearchStringBuilder = new StringBuilder();
		formDefinitionSearchStringBuilder.append("systemId=");
		formDefinitionSearchStringBuilder.append(systemId);

		if (objectClassId != null) {
			formDefinitionSearchStringBuilder.append(":object-classId=");
			formDefinitionSearchStringBuilder.append(objectClassId);
		}

		formDefinitionFilter.setText(formDefinitionSearchStringBuilder.toString());
		List<UUID> formDefinitionIds = formDefinitionService.findIds(formDefinitionFilter, null).getContent();
		if (formDefinitionIds.isEmpty()){
			return null;
		}
		return formDefinitionService.get(formDefinitionIds.get(0));
	}

	protected List<String> getNames(Map<String, String> metadata, Map<String, SysSystemAttributeMappingDto> attributeMappingMap) {
		String systemMappingId = metadata.getOrDefault("systemMapping", null);

		// return only attributes from mapping
		SysSystemAttributeMappingFilter attributeMappingFilter = new SysSystemAttributeMappingFilter();
		attributeMappingFilter.setSystemMappingId(UUID.fromString(systemMappingId));
		List<SysSystemAttributeMappingDto> attributeMappingDtos = systemAttributeMappingService.find(attributeMappingFilter, null).getContent();

		// get attributes names from schema attribute, filter merge and authoritative merge
		return attributeMappingDtos.stream()
				.filter(sysSystemAttributeMappingDto -> !(sysSystemAttributeMappingDto.getStrategyType() == AttributeMappingStrategyType.AUTHORITATIVE_MERGE ||
						sysSystemAttributeMappingDto.getStrategyType() == AttributeMappingStrategyType.MERGE))
				.map(sysSystemAttributeMappingDto -> {
					SysSchemaAttributeDto schemaAttr = DtoUtils.getEmbedded(sysSystemAttributeMappingDto, SysSystemAttributeMapping_.schemaAttribute, SysSchemaAttributeDto.class);
					// add value to Map so we don;t to iterate over this list again later
					attributeMappingMap.put(schemaAttr.getName(), sysSystemAttributeMappingDto);
					return schemaAttr.getName();
				}).collect(Collectors.toList());
	}

	protected static List<IdmFormAttributeDto> getAttributes(IdmFormDefinitionDto formDefinitionDto, List<String> names) {
		List<IdmFormAttributeDto> attributes = new ArrayList<>();
		formDefinitionDto.getFormAttributes().forEach(idmFormAttributeDto -> {
			if (names.contains(idmFormAttributeDto.getCode())) {
				attributes.add(idmFormAttributeDto);
			}
		});
		return attributes;
	}

	protected List<IdmFormValueDto> getValuesDto(Map<String, SysSystemAttributeMappingDto> attributeMappingMap, List<IdmFormAttributeDto> attributes, AbstractDto dto) {
		List<IdmFormValueDto> valuesDto = new ArrayList<>();
		attributes.forEach(idmFormAttributeDto -> {
			IdmFormValueDto value = new IdmFormValueDto();
			value.setPersistentType(idmFormAttributeDto.getPersistentType());
			// get value
			Serializable attributeValue = (Serializable) systemAttributeMappingService.getAttributeValue(null, dto, attributeMappingMap.get(idmFormAttributeDto.getCode()));
			value.setValue(attributeValue);

			value.setFormAttribute(idmFormAttributeDto.getId());
			value.getEmbedded().put("formAttribute", idmFormAttributeDto);
			valuesDto.add(value);
		});
		return valuesDto;
	}

	protected AccAccountDto prepareAccount(AccountWizardDto wizardDto, String systemMappingId) {
		String systemId = wizardDto.getMetadata().getOrDefault("system", null);
		IdmFormDefinitionDto formDefinition = wizardDto.getFormDefinition();

		AccAccountDto accountDto = new AccAccountDto();
		accountDto.setSystem(UUID.fromString(systemId));
		accountDto.setSystemMapping(UUID.fromString(systemMappingId));
		accountDto.setFormDefinition(formDefinition.getId());

		SysSystemMappingDto systemMappingDto = systemMappingService.get(systemMappingId);
		accountDto.setEntityType(systemMappingDto.getEntityType());

		IdmFormInstanceDto formInstanceDto = new IdmFormInstanceDto(accountDto, formDefinition, wizardDto.getValues());
		accountDto.setEavs(Lists.newArrayList(formInstanceDto));
		return accountDto;
	}

	protected String getUidValue(AccountWizardDto wizardDto, AbstractDto dto, UUID systemMappingId) {
		// Get identifier for account
		SysSystemAttributeMappingFilter systemAttributeMappingFilter = new SysSystemAttributeMappingFilter();
		systemAttributeMappingFilter.setSystemMappingId(systemMappingId);
		systemAttributeMappingFilter.setIsUid(true);
		List<SysSystemAttributeMappingDto> uidAttributes = systemAttributeMappingService.find(systemAttributeMappingFilter, null).getContent();
		if (uidAttributes.size() != 1) {
			LOG.info("UID attribute should only one");
			return null;
		}

		SysSystemAttributeMappingDto uidAttribute = uidAttributes.get(0);
		SysSchemaAttributeDto schemaUidAttributeDto = DtoUtils.getEmbedded(uidAttribute, SysSystemAttributeMapping_.schemaAttribute, SysSchemaAttributeDto.class);

		IdmFormAttributeDto formAttributeDto = wizardDto.getFormDefinition().getFormAttributes().stream()
				.filter(idmFormAttributeDto -> idmFormAttributeDto.getCode().equals(schemaUidAttributeDto.getName()))
				.findFirst().orElse(null);

		if (formAttributeDto == null) {
			LOG.info("No uid attribute found");
			return null;
		}

		Serializable uidValue = wizardDto.getValues().stream()
				.filter(idmFormValueDto -> idmFormValueDto.getFormAttribute().equals(formAttributeDto.getId()))
				.findFirst().map(idmFormValueDto -> idmFormValueDto.getValue(formAttributeDto.getPersistentType())).orElse(null);
		if (uidValue == null) {
			uidValue = (Serializable) systemAttributeMappingService.getAttributeValue(null, dto, uidAttribute);
		}
		return String.valueOf(uidValue);
	}


}
