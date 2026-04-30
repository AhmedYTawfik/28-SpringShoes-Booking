package com.team28.booking.user.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class CacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidator.class);

    private final StringRedisTemplate redis;

    public CacheInvalidator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void deleteKey(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Cache delete failed for key={}: {}", key, e.getMessage());
        }
    }

    /** SCAN + DEL — safe for large keyspaces; over-invalidation is acceptable (§4.4.6). */
    public void wildcardDelete(String pattern) {
        try {
            Set<String> keys = new HashSet<>();
            ScanOptions opts = ScanOptions.scanOptions().match(pattern).count(1000).build();
            try (Cursor<byte[]> c = redis.getConnectionFactory().getConnection().scan(opts)) {
                while (c.hasNext()) {
                    keys.add(new String(c.next()));
                }
            }
            if (!keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Cache wildcard delete failed for pattern={}: {}", pattern, e.getMessage());
        }
    }
}
