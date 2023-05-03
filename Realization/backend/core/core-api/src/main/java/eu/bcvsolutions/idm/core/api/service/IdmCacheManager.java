package eu.bcvsolutions.idm.core.api.service;

import java.util.List;

import eu.bcvsolutions.idm.core.api.CoreModule;
import eu.bcvsolutions.idm.core.api.config.cache.domain.ValueWrapper;
import eu.bcvsolutions.idm.core.api.dto.IdmCacheDto;

/**
 * Provides useful methods for working with cache in CzechIdM. Note that this manager does not provide methods for
 * managing cached values. To create new caches and storing/retrieving values in/from them use {@link org.springframework.cache.CacheManager}
 *
 * @author Peter Štrunc <peter.strunc@bcvsolutions.eu>
 * @since 10.2.0
 */
public interface IdmCacheManager {

    /**
     * Returns all available caches. No filtering is possible. This method always returns all currently created caches.
     *
     * @return a list of {@link IdmCacheDto}
     */
    List<IdmCacheDto> getAllAvailableCaches();

    /**
     * Evict cache with given name. If cache with given name does not exist, then this method does nothing.
     *
     * @param cacheId Name of cache to evict
     */
    void evictCache(String cacheId);

    /**
     * Evicts all caches which are present in container at the time of method call. To determine which caches to evict
     * it uses {@link IdmCacheManager#getAllAvailableCaches()}
     */
    void evictAllCaches();

    /**
     * Stores value in cache. If there already was a value stored in the same cache under given key it is rewritten. If
     * cache with given name is not available, then this method throws {@link RuntimeException}.
     *
     * @param cacheName Name of cache to store values
     * @param key Key under which given value will be stored in cache
     * @param value Value to store in cache
     * @throws IllegalArgumentException if cacheName or key arguments are null
     * @return true if value was successfully stored in cache, false otherwise
     */
    boolean cacheValue(String cacheName, Object key, Object value);

    /**
     * Retrieves value form cache with given name. If there is no cached record for given key, method returns {@code null}.
     * If there is a null value stored in cache for given key, then {@link ValueWrapper} with null value is returned.
     *
     * @param cacheName Name of cache to search value in
     * @param key Key to search value in cache
     * @return wrapper containing stored value if any value for given key is present, {@code null} otherwise
     * @throws IllegalArgumentException if cacheName or key arguments are null
     */
    ValueWrapper getValue(String cacheName, Object key);

    /**
     * Removes value from cache.
     *
     * @param cacheName Name of the cache from which should the value be removed
     * @param key Key, under which is removed value currently stored
     * @throws IllegalArgumentException if cacheName or key arguments are null
     */
    void evictValue(String cacheName, Object key);

    public static String getCacheName(String moduleId, String cacheName) {
        return String.format("%s_%s", moduleId, cacheName);
    }

}
