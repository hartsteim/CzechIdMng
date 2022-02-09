package eu.bcvsolutions.idm.core.audit.entity.key;

import java.io.Serializable;
import java.util.Objects;

import eu.bcvsolutions.idm.core.audit.entity.IdmLoggingEventProperty;

/**
 * Composite primary key for {@link IdmLoggingEventProperty}
 * 
 * @author Ondrej Kopr <kopr@xyxy.cz>
 *
 */

public class IdmLoggingEventPropertyPrimaryKey implements Serializable {

	private static final long serialVersionUID = -4847951833981320968L;

	private Long eventId;
	
	private String mappedKey;
	
	public IdmLoggingEventPropertyPrimaryKey() {
	}

	public IdmLoggingEventPropertyPrimaryKey(Long eventId, String mappedKey) {
		this.eventId = eventId;
		this.mappedKey = mappedKey;
	}
	
	public Long getEventId() {
		return eventId;
	}

	public void setEventId(Long eventId) {
		this.eventId = eventId;
	}

	public String getMappedKey() {
		return mappedKey;
	}

	public void setMappedKey(String mappedKey) {
		this.mappedKey = mappedKey;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		IdmLoggingEventPropertyPrimaryKey that = (IdmLoggingEventPropertyPrimaryKey) o;
		return Objects.equals(getEventId(), that.getEventId()) && Objects.equals(getMappedKey(), that.getMappedKey());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getEventId(), getMappedKey());
	}
}
