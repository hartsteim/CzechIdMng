package eu.bcvsolutions.idm.core.model.entity;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.ConstraintMode;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.Size;

import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import eu.bcvsolutions.idm.core.api.domain.AuditSearchable;
import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;
import eu.bcvsolutions.idm.core.api.dto.PanelDto;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity;
import eu.bcvsolutions.idm.core.ecm.api.entity.AttachableEntity;
import eu.bcvsolutions.idm.core.ecm.entity.IdmAttachment;
import eu.bcvsolutions.idm.core.security.api.domain.TwoFactorAuthenticationType;

/**
 * Identity profile.
 * 
 * @author Radek Tomiška 
 * @since 9.0.0
 */
@Entity
@Table(name = "idm_profile")
public class IdmProfile extends AbstractEntity implements AttachableEntity, AuditSearchable {
	
	private static final long serialVersionUID = 1L;
	//
	@Audited
	@OneToOne(optional = false)
	@JoinColumn(name = "identity_id", referencedColumnName = "id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
	private IdmIdentity identity;
	
	/**
	 * Attachment with the image
	 */
	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	@ManyToOne(optional = true)
	@JoinColumn(name = "image_id", referencedColumnName = "id", foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
	private IdmAttachment image;
	
	@Audited
	@Size(max = DefaultFieldLengths.ENUMARATION)
	@Column(name = "preferred_language", length = DefaultFieldLengths.ENUMARATION)
	private String preferredLanguage;
	
	@Audited
	@Column(name = "navigation_collapsed", nullable = false)
	private boolean navigationCollapsed;
	
	@Audited
	@Column(name = "system_information", nullable = false)
	private boolean systemInformation;
	
	@Audited
	@Column(name = "default_page_size", nullable = true)
	private Integer defaultPageSize;
	
	@Audited
	@Enumerated(EnumType.STRING)
	@Column(name = "two_factor_authentication_type", length = DefaultFieldLengths.ENUMARATION)
	private TwoFactorAuthenticationType twoFactorAuthenticationType;
	
	@Audited
	@Column(name = "setting", length = Integer.MAX_VALUE)
	private ConfigurationMap setting;

	public IdmProfile() {
	}
	
	public IdmProfile(UUID id) {
		super(id);
	}

	public IdmIdentity getIdentity() {
		return identity;
	}

	public void setIdentity(IdmIdentity identity) {
		this.identity = identity;
	}

	public IdmAttachment getImage() {
		return image;
	}

	public void setImage(IdmAttachment image) {
		this.image = image;
	}

	public String getPreferredLanguage() {
		return preferredLanguage;
	}

	public void setPreferredLanguage(String preferredLanguage) {
		this.preferredLanguage = preferredLanguage;
	}
	
	public boolean isNavigationCollapsed() {
		return navigationCollapsed;
	}
	
	public void setNavigationCollapsed(boolean navigationCollapsed) {
		this.navigationCollapsed = navigationCollapsed;
	}
	
	/**
	 * Show internal system information like identifiers, detail logs etc.
	 * 
	 * @return if internal system information will be shown
	 * @since 10.2.0 
	 */
	public boolean isSystemInformation() {
		return systemInformation;
	}
	
	/**
	 * Show internal system information like identifiers, detail logs etc.
	 * 
	 * @param systemInformation if internal system information will be shown
	 * @since 10.2.0
	 */
	public void setSystemInformation(boolean systemInformation) {
		this.systemInformation = systemInformation;
	}
	
	/**
	 * Default page size used in tables.
	 * 
	 * @return default page size
	 * @since 10.2.0
	 */
	public Integer getDefaultPageSize() {
		return defaultPageSize;
	}
	
	/**
	 * Default page size used in tables.
	 * 
	 * @param defaultPageSize default page size
	 * @since 10.2.0
	 */
	public void setDefaultPageSize(Integer defaultPageSize) {
		this.defaultPageSize = defaultPageSize;
	}
	
	/**
	 * Additional two factor authentication method.
	 * 
	 * @return selected method
	 * @Since 10.7.0
	 */
	public TwoFactorAuthenticationType getTwoFactorAuthenticationType() {
		return twoFactorAuthenticationType;
	}
	
	/**
	 * Additional two factor authentication method.
	 * 
	 * @param twoFactorAuthenticationType selected method
	 * @Since 10.7.0
	 */
	public void setTwoFactorAuthenticationType(TwoFactorAuthenticationType twoFactorAuthenticationType) {
		this.twoFactorAuthenticationType = twoFactorAuthenticationType;
	}
	
	/**
	 * User setting, e.g. panels configuration - collapsed, expanded.
	 * 
	 * @return user setting
	 * @see PanelDto
	 * @since 11.2.0
	 */
	public ConfigurationMap getSetting() {
		if (setting == null) {
			setting = new ConfigurationMap();
		}
		return setting;
	}
	
	/**
	 * User setting, e.g. panels configuration - collapsed, expanded.
	 * 
	 * @param user setting
	 * @see PanelDto
	 * @since 11.2.0
	 */
	public void setSetting(ConfigurationMap setting) {
		this.setting = setting;
	}
	
	/**
	 * @since 11.3.0
	 */
	@Override
	public String getOwnerId() {
		return identity.getId().toString();
	}

	/**
	 * @since 11.3.0
	 */
	@Override
	public String getOwnerCode() {
		return identity.getCode();
	}

	/**
	 * @since 11.3.0
	 */
	@Override
	public String getOwnerType() {
		return IdmIdentity.class.getName();
	}

	/**
	 * @since 11.3.0
	 */
	@Override
	public String getSubOwnerId() {
		return null;
	}

	/**
	 * @since 11.3.0
	 */
	@Override
	public String getSubOwnerCode() {
		return null;
	}

	/**
	 * @since 11.3.0
	 */
	@Override
	public String getSubOwnerType() {
		return null;
	}
}
