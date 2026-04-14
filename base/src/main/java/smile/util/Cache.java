/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.util;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * An LRU (Least Recently Used) cache with TTL (Time To Live) combines
 * size-based eviction with time-based expiration.
 *
 * @param <K> the type of cache keys.
 * @param <V> the type of cache values.
 * @author Haifeng Li
 */
public class Cache<K, V> {
    /** Cache values. */
    private final Map<K, V> values = new LinkedHashMap<>();
    /** Cache timestamps. */
    private final Map<K, Instant> timestamps = new LinkedHashMap<>();
    /** Maximum number of entries in the cache. */
    private final int capacity;
    /** Time-to-live duration for each cache entry. */
    private final Duration ttl;

    /**
     * Constructor.
     * @param capacity the maximum number of entries in the cache.
     * @param ttl the time-to-live duration for each cache entry.
     */
    public Cache(int capacity, Duration ttl) {
        this.capacity = capacity;
        this.ttl = ttl;
    }

    /**
     * Returns the set of keys in the cache.
     * Expired entries will be evicted first.
     * @return the set of keys in the cache.
     */
    public Set<K> keySet() {
        evict();
        return values.keySet();
    }

    /**
     * Returns the set of values in the cache.
     * Expired entries will be evicted first.
     * @return the set of values in the cache.
     */
    public Collection<V> valueSet() {
        evict();
        return values.values();
    }

    /**
     * Puts a value in the cache with the current timestamp.
     * If the cache exceeds its capacity, the oldest entry will be evicted.
     *
     * @param key the cache key.
     * @param value the cache value.
     * @return the previous value associated with the key,
     *         or null if there was no mapping for the key.
     */
    public V put(K key, V value) {
        evict();
        timestamps.put(key, Instant.now());
        return values.put(key, value);
    }

    /**
     * Gets a value from the cache. If the entry has expired,
     * it will be removed and null will be returned.
     * @param key the cache key.
     * @return the value associated with the key,
     *         or null if the key is not present or has expired.
     */
    public V get(K key) {
        evict();
        Instant ts = timestamps.get(key);
        if (ts == null || Duration.between(ts, Instant.now()).compareTo(ttl) > 0) {
            values.remove(key);
            if (key instanceof String k) timestamps.remove(k);
            return null;
        }
        return values.get(key);
    }

    /**
     * Removes the cache value for a key.
     * @param key the key whose cache value is to be removed from the map.
     * @return the previous value associated with key, or null if there was
     *         no cache value for key.
     */
    public V remove(K key) {
        timestamps.remove(key);
        return values.remove(key);
    }

    /**
     * Evicts expired entries and the least recently used entries
     * if the cache exceeds its capacity.
     */
    private void evict() {
        Instant now = Instant.now();
        timestamps.entrySet().removeIf(e -> {
            if (Duration.between(e.getValue(), now).compareTo(ttl) > 0) {
                values.remove(e.getKey());
                return true;
            }
            return false;
        });

        while (values.size() > capacity) {
            K oldest = values.keySet().iterator().next();
            values.remove(oldest);
            timestamps.remove(oldest);
        }
    }
}
