package com.Shreyansh.webserver.middleware;

import com.Shreyansh.webserver.http.HttpRequest;
import com.Shreyansh.webserver.http.HttpResponse;
import com.Shreyansh.webserver.http.HttpStatus;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter implements Filter {
    private final ConcurrentHashMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();

    private static class Bucket {
        final AtomicInteger count = new AtomicInteger(0);
        Long lastReset = System.currentTimeMillis();
    }

    @Override
    public boolean filter(HttpRequest request, HttpResponse response) {
        if (ipBuckets.size() > 10000) {
            long now = System.currentTimeMillis();
            ipBuckets.entrySet().removeIf(e -> now - e.getValue().lastReset > 60000);
        }

        String ip = request.getRemoteAddr();
        Bucket bucket = ipBuckets.computeIfAbsent(ip, k -> new Bucket());

        synchronized (bucket) {
            if (System.currentTimeMillis() - bucket.lastReset > 1000) {
                bucket.count.set(0);
                bucket.lastReset = System.currentTimeMillis();
            }
        }
        int MAX_PER_SECOND = 100;
        if (bucket.count.incrementAndGet() > MAX_PER_SECOND) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS);
            response.setBody("{\"error\": \"IP Rate Limit Exceeded\"}");
            return false;
        }
        return true;
    }
}
