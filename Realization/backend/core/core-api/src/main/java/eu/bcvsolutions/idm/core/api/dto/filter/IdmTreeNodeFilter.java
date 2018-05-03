package eu.bcvsolutions.idm.core.api.dto.filter;

import java.util.UUID;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import eu.bcvsolutions.idm.core.api.domain.ExternalIdentifiable;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;

/**
 * Filter for tree node
 *
 * @author Ondrej Kopr <kopr@xyxy.cz>
 * @author Radek Tomiška
 */
public class IdmTreeNodeFilter extends DataFilter implements CorrelationFilter, ExternalIdentifiable {

	public static final String PARAMETER_CODE = "code"; // PARAMETER_CODEABLE_IDENTIFIER can be used too
	//
	private UUID treeTypeId;
    private UUID treeNode; // parent - TODO: rename!
    private Boolean defaultTreeType; // Search for tree nodes within the default tree type
    private String property; // Attribute name to search for, like 'code' or 'name'
    private String value; // Value of the attribute defined in property to search for
    /**
     * Tree nodes by tree structure recursively down
     */
    private boolean recursively = true;

    public IdmTreeNodeFilter() {
        this(new LinkedMultiValueMap<>());
    }

    public IdmTreeNodeFilter(MultiValueMap<String, Object> data) {
        super(IdmTreeNodeDto.class, data);
    }

    public UUID getTreeTypeId() {
        return treeTypeId;
    }

    public void setTreeTypeId(UUID treeTypeId) {
        this.treeTypeId = treeTypeId;
    }

    public void setTreeNode(UUID treeNode) {
        this.treeNode = treeNode;
    }

    public UUID getTreeNode() {
        return treeNode;
    }

    public Boolean getDefaultTreeType() {
        return defaultTreeType;
    }

    public void setDefaultTreeType(Boolean defaultTreeType) {
        this.defaultTreeType = defaultTreeType;
    }

    public boolean isRecursively() {
        return recursively;
    }

    public void setRecursively(boolean recursively) {
        this.recursively = recursively;
    }

    @Override
    public String getProperty() {
        return property;
    }

    @Override
    public void setProperty(String property) {
        this.property = property;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public void setValue(String value) {
        this.value = value;
    }
    
    public String getCode() {
		return (String) data.getFirst(PARAMETER_CODE);
	}

	public void setCode(String username) {
		data.set(PARAMETER_CODE, username);
	}
	
	@Override
	public String getExternalId() {
		return (String) data.getFirst(PROPERTY_EXTERNAL_ID);
	}
	
	@Override
	public void setExternalId(String externalId) {
		data.set(PROPERTY_EXTERNAL_ID, externalId);
	}
}
