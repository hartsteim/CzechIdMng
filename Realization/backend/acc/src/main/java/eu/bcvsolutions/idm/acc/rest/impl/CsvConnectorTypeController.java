package eu.bcvsolutions.idm.acc.rest.impl;

import eu.bcvsolutions.idm.acc.connector.CsvConnectorType;
import eu.bcvsolutions.idm.acc.domain.AccGroupPermission;
import eu.bcvsolutions.idm.acc.dto.ConnectorTypeDto;
import eu.bcvsolutions.idm.core.api.bulk.action.dto.IdmBulkActionDto;
import eu.bcvsolutions.idm.core.api.config.swagger.SwaggerConfig;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.dto.IdmScriptDto;
import eu.bcvsolutions.idm.core.api.dto.ResultModels;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmScriptFilter;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.rest.AbstractReadWriteDtoController;
import eu.bcvsolutions.idm.core.api.rest.BaseController;
import eu.bcvsolutions.idm.core.api.rest.BaseDtoController;
import eu.bcvsolutions.idm.core.api.service.IdmScriptService;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.ecm.api.entity.AttachableEntity;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for CSV connector wizard.
 *
 * @author Vít Švanda
 * @since 10.7.0
 */
@RestController
@RequestMapping(value = BaseDtoController.BASE_PATH + "/connector-types/csv-connector-type")
@Api(
		value = CsvConnectorTypeController.TAG,
		tags = {CsvConnectorTypeController.TAG},
		description = "Controller for CSV connector wizard.",
		produces = BaseController.APPLICATION_HAL_JSON_VALUE,
		consumes = MediaType.APPLICATION_JSON_VALUE)
public class CsvConnectorTypeController {

	protected static final String TAG = "CSV Wizard";

	@Autowired
	private AttachmentManager attachmentManager;
	@Autowired
	private CsvConnectorType csvConnectorType;

	/**
	 * Upload CSV file.
	 *
	 * @param name
	 * @param fileName
	 * @param data
	 * @return
	 * @throws IOException
	 */
	@ResponseBody
	@RequestMapping(value = "/deploy", method = RequestMethod.POST)
	@PreAuthorize("hasAuthority('" + AccGroupPermission.SYSTEM_CREATE + "')"
			+ " or hasAuthority('" + AccGroupPermission.SYSTEM_UPDATE + "')")
	@ApiOperation(
			value = "Upload CSV file.",
			nickname = "uploadCSV",
			tags = {CsvConnectorTypeController.TAG},
			authorizations = {
					@Authorization(value = SwaggerConfig.AUTHENTICATION_BASIC, scopes = {
							@AuthorizationScope(scope = AccGroupPermission.SYSTEM_CREATE, description = ""),
							@AuthorizationScope(scope = AccGroupPermission.SYSTEM_UPDATE, description = "")}),
					@Authorization(value = SwaggerConfig.AUTHENTICATION_CIDMST, scopes = {
							@AuthorizationScope(scope = AccGroupPermission.SYSTEM_CREATE, description = ""),
							@AuthorizationScope(scope = AccGroupPermission.SYSTEM_UPDATE, description = "")})
			},
			notes = "CSV file for system wizard.")
	public ResponseEntity<ConnectorTypeDto> deploy(String name, String fileName, String goalPath, MultipartFile data) throws IOException {
		// save attachment
		IdmAttachmentDto attachment = new IdmAttachmentDto();
		attachment.setOwnerType(AttachmentManager.TEMPORARY_ATTACHMENT_OWNER_TYPE);
		attachment.setName(fileName);
		attachment.setMimetype(StringUtils.isBlank(data.getContentType()) ? AttachableEntity.DEFAULT_MIMETYPE : data.getContentType());
		attachment.setInputData(data.getInputStream());
		attachment = attachmentManager.saveAttachment(null, attachment); // owner and version is resolved after attachment is saved
		// deploy
		ConnectorTypeDto connectorTypeDto = csvConnectorType.deployCsv(attachment, goalPath);

		return new ResponseEntity<ConnectorTypeDto>(connectorTypeDto, HttpStatus.CREATED);
	}

}
