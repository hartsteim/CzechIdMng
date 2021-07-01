package eu.bcvsolutions.idm.acc.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import java.time.ZonedDateTime;

import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;

/**
 * Entity for test table resource
 * 
 * @author svandav
 *
 */
@Entity
@Table(name = TestResource.TABLE_NAME)
public class TestResource {
	
	public static final String TABLE_NAME = "test_resource";

	@Id
	@Column(name = "NAME", length = DefaultFieldLengths.NAME)
	private String name;
	@Column(name = "LASTNAME", length = DefaultFieldLengths.NAME)
	private String lastname;
	@Column(name = "FIRSTNAME", length = DefaultFieldLengths.NAME)
	private String firstname;
	@Column(name = "PASSWORD", length = DefaultFieldLengths.NAME)
	private String password;
	@Column(name = "EMAIL", length = DefaultFieldLengths.NAME)
	private String email;
	@Column(name = "DESCRIP", length = DefaultFieldLengths.DESCRIPTION)
	private String descrip;
	@Column(name = "STATUS", length = DefaultFieldLengths.NAME)
	private String status;
	@Column(name = "MODIFIED", length = DefaultFieldLengths.NAME)
	private ZonedDateTime modified;
	@Column(name = "EAV_ATTRIBUTE", length = DefaultFieldLengths.NAME)
	private String eavAttribute;
	@Column(name = "TITLE_BEFORE", length = DefaultFieldLengths.NAME)
	private String titleBefore;
	@Column(name = "TITLE_AFTER", length = DefaultFieldLengths.NAME)
	private String titleAfter;
	@Column(name = "PERSONAL_NUMBER", length = DefaultFieldLengths.NAME)
	private String personalNumber;

	public String getEavAttribute() {
		return eavAttribute;
	}

	public void setEavAttribute(String eavAttribute) {
		this.eavAttribute = eavAttribute;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLastname() {
		return lastname;
	}

	public void setLastname(String lastname) {
		this.lastname = lastname;
	}

	public String getFirstname() {
		return firstname;
	}

	public void setFirstname(String firstname) {
		this.firstname = firstname;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getDescrip() {
		return descrip;
	}

	public void setDescrip(String descrip) {
		this.descrip = descrip;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public ZonedDateTime getModified() {
		return modified;
	}

	public void setModified(ZonedDateTime modified) {
		this.modified = modified;
	}

	public String getTitleBefore() {
		return titleBefore;
	}

	public void setTitleBefore(String titleBefore) {
		this.titleBefore = titleBefore;
	}

	public String getTitleAfter() {
		return titleAfter;
	}

	public void setTitleAfter(String titleAfter) {
		this.titleAfter = titleAfter;
	}

	public String getPersonalNumber() {
		return personalNumber;
	}

	public void setPersonalNumber(String personalNumber) {
		this.personalNumber = personalNumber;
	}
}
