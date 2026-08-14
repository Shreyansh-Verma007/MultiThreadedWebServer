# 📄 `RateLimiter.java` — IP-Based Rate Limiting Filter

**Package:** `com.Shreyansh.webserver.middleware`  
**Path:** `src/main/java/com/Shreyansh/webserver/middleware/RateLimiter.java`  
**Role:** Middleware filter that limits each client IP to 100 requests per second using a fixed-window counter algorithm.

---

## File Overview

`RateLimiter` is a concrete implementation of the `Filter` interface that provides **per-IP rate limiting**. It uses a **Fixed Window Counter** algorithm: each IP address gets a "bucket" that counts requests within a 1-second window. When the window expires (>1 second since last reset), the counter resets to zero. If an IP exceeds 100 requests within a single window, subsequent requests are rejected with HTTP 429 (Too Many Requests).

This protects the server from:
- **Denial of Service (DoS)** attacks — a single IP can't exhaust the thread pool
- **Brute force** attacks — login/API abuse is rate-bounded
- **Runaway clients** — misbehaving scripts won't monopolize server resources

---

## Algorithm Classification — Why "Fixed Window Counter"

The implementation is sometimes confused with "sliding window" or "token bucket" algorithms. Here's the precise classification:

| Algorithm | Mechanism | Our Implementation? |
|-----------|-----------|:---:|
| **Fixed Window Counter** | Counter resets at fixed time boundaries | ✅ **This one** |
| Sliding Window Log | Stores every request timestamp, counts within trailing window | ❌ |
| Sliding Window Counter | Weighted blend of current + previous window | ❌ |
| Token Bucket | Tokens refill at fixed rate, 1 consumed per request | ❌ |
| Leaky Bucket | Requests drain at fixed rate from a FIFO queue | ❌ |

**How to identify it in the code:**

```java
// The tell-tale sign of a fixed window counter:
if (System.currentTimeMillis() - bucket.lastReset > 1000) {   // Window boundary crossed?
    bucket.count.set(0);                                        // Hard reset to zero
    bucket.lastReset = System.currentTimeMillis();              // New window starts NOW
}
```

A sliding window would never reset to zero — it would subtract expired counts. A token bucket would add tokens proportionally to elapsed time. The hard reset to zero is the defining characteristic of a fixed window counter.

---

## The Boundary Burst Problem

The fixed-window approach has a known edge case: up to **2× the rate limit** can pass through in a burst lasting less than 1 second across a window boundary.

```
Timeline (→ = request accepted, × = request rejected):

Window 1 (t=0 to t=999ms)                Window 2 (t=1000ms to t=1999ms)
────────────────────────────────────────┬─────────────────────────────────
                    .                   |
       idle...      . 100 requests     |100 requests          idle...
                    . at t=950ms       |at t=1050ms
                    . → all accepted   |→ all accepted
                    .                   |
────────────────────────────────────────┴─────────────────────────────────

Result: 200 requests in 100ms span, all accepted.
        Each window individually sees ≤100 requests — the limit is satisfied per-window.
        But the instantaneous rate is 200/0.1s = 2000 req/sec.
```

**Why this is acceptable:**
- The burst lasts only a fraction of a second and cannot be sustained
- Over any 2-second span, at most 200 requests pass through (100 per window)
- The average sustained rate is still capped at 100 req/sec
- More sophisticated algorithms (sliding window, token bucket) add complexity and memory without significantly improving protection for this use case

---

## Line-by-Line Explanation

### Fields (Lines 11–15)

```java
public class RateLimiter implements Filter {                   // Line 10
    private final ConcurrentHashMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();  // Line 11
    private final int maxRequestsPerSecond;                    // Line 12: Configurable limit
    private final long windowMillis;                           // Line 13: Configurable window duration
    private final int gcThreshold;                             // Line 14: Configurable cleanup trigger
    private final long gcStalenessMillis;                      // Line 15: Configurable staleness timeout
```

**`ipBuckets`:** Maps each client IP address (String) to its rate-limiting bucket. `ConcurrentHashMap` is used because multiple thread-pool workers access this map simultaneously from different connections.

All tunable parameters are now constructor-injected. The no-arg `RateLimiter()` constructor delegates to the 4-arg constructor with sensible defaults: `this(100, 1000, 10_000, 60_000)`.

### Inner Class: `Bucket` (Lines 42–45)

```java
    private static class Bucket {                              // Line 42
        final AtomicInteger count = new AtomicInteger(0);      // Line 43
        volatile long lastReset = System.currentTimeMillis();  // Line 44
    }
```

| Field | Type | Why This Type |
|-------|------|---------------|
| `count` | `AtomicInteger` | Supports lock-free `incrementAndGet()` via CPU CAS instruction. Multiple threads can increment concurrently without a lock. |
| `lastReset` | `volatile long` | `volatile` ensures cross-thread visibility. Reads/writes go to main memory, not CPU cache. Primitive `long` avoids autoboxing overhead. |

**Memory per Bucket:** ~16 bytes (header) + ~16 bytes (AtomicInteger) + 8 bytes (volatile long) = **~40 bytes** + map entry overhead (~32 bytes) + IP key string (~56 bytes) ≈ **~128 bytes total per IP**

### `filter(HttpRequest, HttpResponse)` — The Core Logic (Lines 18–41)

#### Step 1: Memory Protection GC Sweep (Lines 49–52)

```java
    @Override
    public boolean filter(HttpRequest request, HttpResponse response) {  // Line 48
        if (ipBuckets.size() > gcThreshold) {                  // Line 49
            long now = System.currentTimeMillis();              // Line 50
            ipBuckets.entrySet().removeIf(e -> now - e.getValue().lastReset > gcStalenessMillis);  // Line 51
        }
```

**Purpose:** Prevents unbounded memory growth from distributed attacks using thousands of unique IPs.

**Complexity analysis:**
```
Trigger:    Only when ipBuckets.size() > 10,000
Time:       O(N) where N = number of tracked IPs (iterates all entries)
Space:      O(1) — in-place removal
Condition:  Removes buckets not accessed in 60 seconds

Memory math:
  10,000 IPs × ~128 bytes per entry (bucket + map entry + key) = ~1.25 MB
  This is the threshold before cleanup kicks in — trivial heap usage.

Latency impact:
  O(N) scan on the request thread — at N=10,000, takes ~1-2ms
  This causes a one-time latency spike for whichever request triggers it.
  Optimization: Move to a background ScheduledExecutorService.
```

**`removeIf()` on `ConcurrentHashMap`** is atomic per-entry — it locks each bucket individually during removal, allowing concurrent reads on other buckets to continue.

#### Step 2: Get or Create Bucket (Lines 25–26)

```java
        String ip = request.getRemoteAddr();                   // Line 25
        Bucket bucket = ipBuckets.computeIfAbsent(ip, k -> new Bucket());  // Line 26
```

**`computeIfAbsent()`** is an atomic operation on `ConcurrentHashMap`:
- If the key exists: returns the existing `Bucket` (no new object created)
- If the key is missing: atomically inserts a new `Bucket` and returns it
- Thread-safe: two threads calling `computeIfAbsent` for the same new IP will never create duplicate buckets

#### Step 3: Window Reset Check (Lines 57–62)

```java
        synchronized (bucket) {                                // Line 57
            if (System.currentTimeMillis() - bucket.lastReset > windowMillis) {  // Line 58
                bucket.count.set(0);                           // Line 59
                bucket.lastReset = System.currentTimeMillis(); // Line 60
            }
        }
```

**Why `synchronized(bucket)` and not a global lock?**

The synchronized block locks on the **individual bucket**, not on `this` or the entire `ipBuckets` map. This means:

```
Thread A checking IP 192.168.1.1 → acquires lock on Bucket@0x1a
Thread B checking IP 192.168.1.2 → acquires lock on Bucket@0x2b

These two threads run in PARALLEL — no contention.

Thread C checking IP 192.168.1.1 → BLOCKS on Bucket@0x1a (same IP as Thread A)

Only same-IP requests contend. Different IPs never block each other.
```

**Why synchronize at all?** The reset check and the counter set must be atomic together. Without the synchronized block:

```
Race condition without synchronized:
  Thread 1: reads (now - lastReset) → 1500ms → decides to reset
  Thread 2: reads (now - lastReset) → 1500ms → decides to reset
  Thread 1: count.set(0), lastReset = now
  Thread 2: count.set(0), lastReset = now    ← DOUBLE RESET, counter cleared twice

  Both threads then increment to 1. The bucket "lost" all previous counts.
  An attacker could exploit this to exceed the rate limit.
```

The synchronized block ensures only one thread resets the window at a time.

#### Step 4: Increment and Check (Lines 63–70)

```java
        if (bucket.count.incrementAndGet() > maxRequestsPerSecond) { // Line 64
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS);  // Line 65
            response.setBody("{\"error\": \"IP Rate Limit Exceeded\"}");  // Line 66
            return false;                                      // Line 67
        }
        return true;                                           // Line 69
    }
```

**`maxRequestsPerSecond` is now a constructor-injected field**, not a local variable. It can be configured per `RateLimiter` instance:

```java
// Default: 100 req/sec, 1s window
new RateLimiter()

// Custom: 50 req/sec, 2s window, GC at 5000 IPs, 30s staleness
new RateLimiter(50, 2000, 5000, 30_000)
```

**Line 35: `incrementAndGet()`** is a lock-free atomic operation using CPU-level Compare-And-Swap (CAS). Its implementation on x86:
```
LOCK XADD [memory], 1    ← single instruction, atomic, no lock acquisition needed
```

This is outside the `synchronized(bucket)` block intentionally — the increment doesn't need to be atomic with the reset check. Even if a small race allows count to reach 101 before the check fires, the behavior is correct: the 101st request is rejected.

**Line 38: `return false`** — short-circuits the `FilterChain`. The rate-limited response (429 with JSON body) is sent immediately without ever reaching the router.

---

## Concurrency Design Summary

The rate limiter uses a layered concurrency strategy, each level chosen for the minimum necessary synchronization:

```
┌─────────────────────────────────────────────────────────────┐
│  Level 1: ConcurrentHashMap (ipBuckets)                     │
│    Purpose: Thread-safe map operations (get, put, remove)   │
│    Contention: None between different keys                  │
│    Used for: computeIfAbsent, removeIf, size()              │
├─────────────────────────────────────────────────────────────┤
│  Level 2: synchronized(bucket) — per-IP lock                │
│    Purpose: Atomic window reset (check + set + reset)       │
│    Contention: Only between threads checking the SAME IP    │
│    Scope: ~100ns hold time (two field reads + one write)    │
├─────────────────────────────────────────────────────────────┤
│  Level 3: AtomicInteger.incrementAndGet() — lock-free       │
│    Purpose: Atomic counter increment without any lock       │
│    Contention: Near-zero (CPU CAS, ~10ns)                   │
│    Used for: count increment + threshold check              │
└─────────────────────────────────────────────────────────────┘
```

**Throughput under contention:**
- 100 unique IPs, 100 threads → each thread hits a different bucket → **zero contention**
- 1 IP, 100 threads → all 100 threads contend on one bucket → synchronized block is the bottleneck, but it holds for ~100ns, so maximum wait is ~10µs (100 threads × 100ns)
- Mixed workload → typical web server pattern. Contention is proportional to `threads_per_popular_IP / total_threads`

---

## Key Design Notes

- **All parameters are configurable** via the 4-arg constructor. The no-arg constructor uses sensible defaults (100 req/sec, 1s window, 10K IP GC threshold, 60s staleness).
- **Per-IP tracking** means a reverse proxy sending all requests from a single IP would share one bucket. Use `X-Forwarded-For` header for more accurate client identification behind proxies.
- **`volatile long lastReset`** ensures cross-thread visibility outside the synchronized block. The field is written inside `synchronized(bucket)` but read outside it by `incrementAndGet()`, so volatile is necessary.
- **1-second window** (default) is a fixed window, not sliding. The boundary burst problem allows up to 2× the limit in <1s across window boundaries.
- **No rate limit persistence.** Server restart clears all buckets. All clients get a fresh window.
