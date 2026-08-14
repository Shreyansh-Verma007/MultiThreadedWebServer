package com.Shreyansh.webserver.middleware;

import com.Shreyansh.webserver.http.HttpRequest;
import com.Shreyansh.webserver.http.HttpResponse;
import com.Shreyansh.webserver.http.HttpStatus;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter implements Filter {
    private final ConcurrentHashMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final int maxRequestsPerSecond;
    private final long windowMillis;
    private final int gcThreshold;
    private final long gcStalenessMillis;

    /**
     * Creates a rate limiter with configurable parameters.
     *
     * @param maxRequestsPerSecond Maximum requests allowed per IP per window (default: 100)
     * @param windowMillis         Window duration in milliseconds (default: 1000)
     * @param gcThreshold          Number of tracked IPs before triggering stale bucket cleanup (default: 10000)
     * @param gcStalenessMillis    Buckets older than this are evicted during GC (default: 60000)
     */
    public RateLimiter(int maxRequestsPerSecond, long windowMillis, int gcThreshold, long gcStalenessMillis) {
        this.maxRequestsPerSecond = maxRequestsPerSecond;
        this.windowMillis = windowMillis;
        this.gcThreshold = gcThreshold;
        this.gcStalenessMillis = gcStalenessMillis;
    }

    /**
     * Creates a rate limiter with sensible defaults: 100 req/sec, 1s window,
     * GC at 10,000 tracked IPs, 60s staleness timeout.
     */
    public RateLimiter() {
        this(100, 1000, 10_000, 60_000);
    }

    private static class Bucket {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long lastReset = System.currentTimeMillis();
    }

    @Override
    public boolean filter(HttpRequest request, HttpResponse response) {
        // Memory protection: purge stale buckets when tracking too many IPs
        if (ipBuckets.size() > gcThreshold) {
            long now = System.currentTimeMillis();
            ipBuckets.entrySet().removeIf(e -> now - e.getValue().lastReset > gcStalenessMillis);
        }

        String ip = request.getRemoteAddr();
        Bucket bucket = ipBuckets.computeIfAbsent(ip, k -> new Bucket());

        // Per-bucket lock: only blocks threads checking the SAME IP
        synchronized (bucket) {
            if (System.currentTimeMillis() - bucket.lastReset > windowMillis) {
                bucket.count.set(0);
                bucket.lastReset = System.currentTimeMillis();
            }
        }

        if (bucket.count.incrementAndGet() > maxRequestsPerSecond) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS);
            response.setBody("{\"error\": \"IP Rate Limit Exceeded\"}");
            return false;
        }
        return true;
    }
}
