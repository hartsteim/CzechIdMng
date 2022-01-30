package eu.bcvsolutions.idm.core.api.event;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Start / complete asynchronous event in synchronized blocks.
 * Synchronized block is needed from LRT task too (=> public, but prevent to use it manually).
 * 
 * @author Radek Tomiška
 * @since 10.6.0
 */
public interface EntityEventLock {

	/**
	 * Start / complete asynchronous event in synchronized blocks.
	 * Synchronized block is needed from LRT task too (=> public, but prevent to use it manually).
	 */
	void unlock();
	
	/**
	 * Start / complete asynchronous event in synchronized blocks.
	 * Synchronized block is needed from LRT task too (=> public, but prevent to use it manually).
	 */
	void lock();

	/**
	 * Note that this method returns only estimate. For more info, see {@link ReentrantLock#getQueueLength()}
	 *
	 * @return Number of threads waiting for this lock
	 */
	int getQueueLength();
}
