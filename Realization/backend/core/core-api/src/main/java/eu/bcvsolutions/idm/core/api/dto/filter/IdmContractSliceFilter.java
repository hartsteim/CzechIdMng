package eu.bcvsolutions.idm.core.api.dto.filter;

import java.util.UUID;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import eu.bcvsolutions.idm.core.api.dto.IdmContractSliceDto;
import eu.bcvsolutions.idm.core.api.utils.ParameterConverter;

/**
 * Filter for {@link IdmContractSliceDto} dtos.
 * 
 * FIXME: rewrite to pure DataFilter, lookout - IdmIdentityContractFilter properties are not fully implemented now!
 *
 * @author svandav
 */
public class IdmContractSliceFilter extends IdmIdentityContractFilter {
	
	private UUID excludeContract; // For choose the parent contract. I want to exclude itself contract.
	private Boolean withoutParent; // Returns contract without filled the parent field.
	private UUID parentContract; // Internal relation on the main identity-contract
	private String contractCode; // Identifier for the main contract.
	private Boolean shouldBeUsingAsContract; // Return slices if are valid (for now). Checks only time validity.
	private Boolean usingAsContract; // Slice is use as contract (boolean)
	private UUID treeNode;
	
	public IdmContractSliceFilter() {
		this(new LinkedMultiValueMap<>());
	}

	public IdmContractSliceFilter(MultiValueMap<String, Object> data) {
		this(data, null);
	}
	
	public IdmContractSliceFilter(MultiValueMap<String, Object> data, ParameterConverter parameterConverter) {
		super(IdmContractSliceDto.class, data, parameterConverter);
	}
	
	public UUID getExcludeContract() {
		return excludeContract;
	}

	public void setExcludeContract(UUID excludeContract) {
		this.excludeContract = excludeContract;
	}

	public Boolean getWithoutParent() {
		return withoutParent;
	}

	public void setWithoutParent(Boolean withoutParent) {
		this.withoutParent = withoutParent;
	}

	public String getContractCode() {
		return contractCode;
	}

	public void setContractCode(String contractCode) {
		this.contractCode = contractCode;
	}
	
	public UUID getParentContract() {
		return parentContract;
	}

	public void setParentContract(UUID parentContract) {
		this.parentContract = parentContract;
	}

	public Boolean getShouldBeUsingAsContract() {
		return shouldBeUsingAsContract;
	}

	public void setShouldBeUsingAsContract(Boolean shouldBeUsingAsContract) {
		this.shouldBeUsingAsContract = shouldBeUsingAsContract;
	}

	public Boolean getUsingAsContract() {
		return usingAsContract;
	}

	public void setUsingAsContract(Boolean usingAsContract) {
		this.usingAsContract = usingAsContract;
	}

	public UUID getTreeNode() {
		return treeNode;
	}

	public void setTreeNode(UUID treeNode) {
		this.treeNode = treeNode;
	}
}
