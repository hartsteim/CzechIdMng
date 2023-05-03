package eu.bcvsolutions.idm.core.api.dto.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.utils.ParameterConverter;

/**
 * Filter for role request.
 *
 * TODO: remove state field - use states.
 *
 * @author svandav
 */
public class IdmRoleRequestFilter extends DataFilter {
	
	public static final String PARAMETER_CREATOR_ID = "creator";
	
	private UUID applicantId;
	private String applicant;
	private RoleRequestState state;
	private List<OperationState> systemStates;
	private UUID duplicatedToRequestId;
	private List<RoleRequestState> states;
	private List<UUID> applicants;
	private UUID creatorId;
	private boolean includeConcepts = false;
	/**
	 * Include approvers for current tasks. Approvers will be receive from original process or from all subprocesses.
	 */
	private boolean includeApprovers = false;
	/**
	 * If true, then returns requests where state in IdM and state on a systems is
	 * EXECUTED.
	 * If Boolean.FALSE, then return all requests where IdM state is not
	 * DUPLICATED, CANCELED, DISAPPROVED and IdM state is not EXECUTED or system
	 * state is not EXECUTED and not null.
	 */
	private Boolean executed;

	private String applicantType;

	public IdmRoleRequestFilter() {
		this(new LinkedMultiValueMap<>());
	}

	public IdmRoleRequestFilter(boolean includeConcepts) {
		this(new LinkedMultiValueMap<>());
		this.includeConcepts = includeConcepts;
	}

	public IdmRoleRequestFilter(MultiValueMap<String, Object> data) {
		this(data, null);
	}
	
	public IdmRoleRequestFilter(MultiValueMap<String, Object> data, ParameterConverter parameterConverter) {
		super(IdmRoleRequestDto.class, data, parameterConverter);
	}

	public List<UUID> getApplicants() {
		if (applicants == null) {
			applicants = new ArrayList<>();
		}
		return applicants;
	}

	public void setApplicants(List<UUID> applicants) {
		this.applicants = applicants;
	}

	public UUID getApplicantId() {
		return applicantId;
	}

	public void setApplicantId(UUID applicantId) {
		this.applicantId = applicantId;
	}

	public RoleRequestState getState() {
		return state;
	}

	public void setState(RoleRequestState state) {
		this.state = state;
	}

	public String getApplicant() {
		return applicant;
	}

	public void setApplicant(String applicant) {
		this.applicant = applicant;
	}

	public UUID getDuplicatedToRequestId() {
		return duplicatedToRequestId;
	}

	public void setDuplicatedToRequestId(UUID duplicatedToRequestId) {
		this.duplicatedToRequestId = duplicatedToRequestId;
	}

	public List<RoleRequestState> getStates() {
		if (states == null) {
			states = new ArrayList<>();
		}
		return states;
	}

	public void setStates(List<RoleRequestState> states) {
		this.states = states;
	}

	public void setCreatorId(UUID creatorId) {
		this.creatorId = creatorId;
	}

	public UUID getCreatorId() {
		return creatorId;
	}

	public boolean isIncludeConcepts() {
		return includeConcepts;
	}

	public void setIncludeConcepts(boolean includeConcepts) {
		this.includeConcepts = includeConcepts;
	}

	public Boolean getExecuted() {
		return executed;
	}

	public void setExecuted(Boolean executed) {
		this.executed = executed;
	}

	public List<OperationState> getSystemStates() {
		return systemStates;
	}

	public void setSystemStates(List<OperationState> systemStates) {
		this.systemStates = systemStates;
	}

	public boolean isIncludeApprovers() {
		return includeApprovers;
	}

	public void setIncludeApprovers(boolean includeApprovers) {
		this.includeApprovers = includeApprovers;
	}

	public String getApplicantType() {
		return this.applicantType;
	}

	public void setApplicantType(String applicantType) {
		this.applicantType = applicantType;
	}
}
