package com.aichat.guard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存令牌桶
 */
@Component
public class RateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final int limit;

    private final long windowMs = 60_000;

    public RateLimiter(@Value("${agent.rate-limit.per-minute:20}")int limit) {
        this.limit = limit;
    }

    public boolean tryAcquire(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k->new Bucket());
        return bucket.tryAcquire(limit, windowMs);
    }


    static class Bucket {
        private final AtomicLong count = new AtomicLong(0);
        private volatile long windowStart = System.currentTimeMillis();
        synchronized boolean tryAcquire(int limit, long windowMs) {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMs) {
                count.set(0);
                windowStart = now;
            }
            return count.incrementAndGet() <= limit;
        }
    }

}
