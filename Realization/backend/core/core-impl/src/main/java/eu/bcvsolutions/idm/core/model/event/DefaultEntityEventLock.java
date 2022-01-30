package eu.bcvsolutions.idm.core.model.event;

import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.api.event.EntityEventLock;

/**
 * Start / complete asynchronous event in synchronized blocks.
 * Synchronized block is needed from LRT task too (=> public, but prevent to use it manually).
 * 
 * @author Radek Tomiška
 * @since 10.6.0
 */
@Component("entityEventLock")
public class DefaultEntityEventLock implements EntityEventLock {

	private static final ReentrantLock LOCK = new ReentrantLock(); // multi method synchronization
	
	@Override
	public void unlock() {
		LOCK.unlock();
	}
	
	@Override
	public void lock() {
		LOCK.lock();
	}

	@Override
	public int getQueueLength() {
		return LOCK.getQueueLength();
	}

}
