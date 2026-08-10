# 📄 `RateLimiter.java` — IP-Based Rate Limiting Filter

**Package:** `com.Shreyansh.webserver.middleware`  
**Path:** `src/main/java/com/Shreyansh/webserver/middleware/RateLimiter.java`  
**Role:** Middleware filter that limits each client IP to 100 requests per second using a token bucket algorithm.

---

## File Overview

`RateLimiter` is a concrete implementation of the `Filter` interface that provides **per-IP rate limiting**. It uses a sliding window approach: each IP address gets a "bucket" that tracks the number of requests in the current 1-second window. If an IP exceeds 100 requests within a second, subsequent requests are rejected with HTTP 429 (Too Many Requests).

This protects the server from:
- **Denial of Service (DoS)** attacks
- **Brute force** attacks
- **Excessive polling** by misbehaving clients

---

## Line-by-Line Explanation

### Fields (Line 11)

```java
public class RateLimiter implements Filter {                   // Line 10: Implements the Filter interface
    private final ConcurrentHashMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();  // Line 11
```

**`ipBuckets`**: A thread-safe map from IP address (`String`) to `Bucket`. Each unique client IP gets its own bucket. `ConcurrentHashMap` is used because multiple request-processing threads access this map concurrently.

### Inner Class: `Bucket` (Lines 13–16)

```java
    private static class Bucket {                              // Line 13
        final AtomicInteger count = new AtomicInteger(0);      // Line 14: Number of requests in current window
        Long lastReset = System.currentTimeMillis();           // Line 15: Timestamp when the counter was last reset
    }
```

Each `Bucket` tracks:
- **`count`**: An `AtomicInteger` counting requests in the current 1-second window. Atomic for thread-safe increment.
- **`lastReset`**: Timestamp (milliseconds) when the counter was last reset to 0.

### `filter(HttpRequest, HttpResponse)` — The Rate Limiting Logic (Lines 18–41)

#### Step 1: Garbage Collection (Lines 20–23)

```java
    @Override
    public boolean filter(HttpRequest request, HttpResponse response) {  // Line 19
        if (ipBuckets.size() > 10000) {                        // Line 20: Too many tracked IPs?
            long now = System.currentTimeMillis();              // Line 21
            ipBuckets.entrySet().removeIf(e -> now - e.getValue().lastReset > 60000);  // Line 22
        }
```

**Memory protection.** If more than 10,000 unique IPs are being tracked, clean up buckets that haven't been active in the last 60 seconds. This prevents memory exhaustion from attackers using many different source IPs.

**Line 22**: `removeIf()` is an atomic operation on `ConcurrentHashMap` entries — it safely removes stale buckets.

#### Step 2: Get or Create Bucket (Lines 25–26)

```java
        String ip = request.getRemoteAddr();                   // Line 25: Get client's IP address
        Bucket bucket = ipBuckets.computeIfAbsent(ip, k -> new Bucket());  // Line 26: Get or create bucket
```

**`computeIfAbsent()`**: Atomically returns the existing bucket for this IP, or creates a new one if this is the first request from this IP. Thread-safe.

#### Step 3: Window Reset Check (Lines 28–33)

```java
        synchronized (bucket) {                                // Line 28: Lock this specific bucket
            if (System.currentTimeMillis() - bucket.lastReset > 1000) {  // Line 29: More than 1 second since last reset?
                bucket.count.set(0);                           // Line 30: Reset the counter
                bucket.lastReset = System.currentTimeMillis(); // Line 31: Update the reset timestamp
            }
        }
```

**Sliding window reset.** If more than 1 second has passed since the last reset, the counter is zeroed. The `synchronized(bucket)` block ensures that the reset check and the counter set are atomic for this bucket — preventing race conditions where two threads could both reset simultaneously.

#### Step 4: Increment and Check (Lines 34–40)

```java
        int MAX_PER_SECOND = 100;                              // Line 34: Maximum allowed requests per second per IP
        if (bucket.count.incrementAndGet() > MAX_PER_SECOND) { // Line 35: Atomically increment and check
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS);  // Line 36: Set 429 status
            response.setBody("{\"error\": \"IP Rate Limit Exceeded\"}");  // Line 37: JSON error body
            return false;                                      // Line 38: Block the request
        }
        return true;                                           // Line 40: Allow the request
    }
```

**`incrementAndGet()`**: Atomically increments the counter and returns the new value. If the new value exceeds 100:
- Sets the response to 429 with a JSON error message.
- Returns `false` to short-circuit the filter chain.

If under the limit, returns `true` to allow the request through.

---

## Rate Limiting Algorithm

```
Timeline for IP 192.168.1.1:
─────────────────────────────────────────────────

Second 1:  Requests 1-100 → ALLOWED (count: 1→100)
           Request 101     → BLOCKED (429)
           Request 102     → BLOCKED (429)

Second 2:  (1000ms elapsed → counter resets to 0)
           Requests 1-100  → ALLOWED (count: 1→100)
           Request 101     → BLOCKED (429)
```

---

## Thread Safety Analysis

| Component | Thread Safety Mechanism |
|-----------|----------------------|
| `ipBuckets` map | `ConcurrentHashMap` — thread-safe reads/writes |
| `computeIfAbsent` | Atomic get-or-create operation |
| Window reset check | `synchronized(bucket)` — per-bucket lock |
| `count.incrementAndGet()` | `AtomicInteger` — lock-free atomic operation |
| `removeIf()` cleanup | `ConcurrentHashMap` atomic bulk removal |

The per-bucket `synchronized` block is fine-grained — different IPs can be processed concurrently without contention.

---

## Key Design Notes

- **100 req/sec limit**: Hardcoded as a local variable (`MAX_PER_SECOND`). Could be extracted to a constructor parameter or config.
- **Per-IP tracking**: Each unique IP has its own counter. A proxy or load balancer sending requests from a single IP would share one bucket.
- **Memory cleanup**: The 10,000-IP threshold and 60-second staleness check prevent unbounded memory growth.
- **1-second window**: The window is not a sliding window per se — it's a fixed window that resets after 1 second. A burst of 100 requests at the end of window 1 and 100 at the start of window 2 (200 in <1s) would be allowed.
