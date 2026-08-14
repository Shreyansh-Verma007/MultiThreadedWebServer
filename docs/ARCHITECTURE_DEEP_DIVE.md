# 🔬 Architecture Deep Dive — Algorithmic & Mathematical Analysis

> This document provides the technical depth behind every non-trivial algorithm, data structure, and concurrency mechanism in the MultithreadedWebServer framework. Written for senior engineering audiences and interview preparation.

---

## Section 3 — Core Algorithms & Optimizations

### 3.1 Trie-Based URL Router

**File:** [Router.java](../src/main/java/com/Shreyansh/webserver/routing/Router.java) | [TrieNode.java](../src/main/java/com/Shreyansh/webserver/routing/TrieNode.java)

#### Why a Trie Instead of a HashMap?

The naive approach to URL routing is a flat `HashMap<String, Handler>`:

```java
// Naive approach
Map<String, Handler> routes = new HashMap<>();
routes.put("GET:/api/status", statusHandler);
routes.put("POST:/api/users", createUserHandler);
```

This gives O(1) lookup but has fundamental limitations:

| Criterion | HashMap | Trie (our implementation) |
|-----------|---------|--------------------------|
| Lookup time | O(1) amortized, O(L) for string hashing where L = path length | O(K) where K = number of path segments |
| Path parameters | Requires regex iteration over all keys — O(N) where N = total routes | Tree walk with wildcard node matching — O(K) |
| Prefix matching | Not supported | Natural — every intermediate node is a valid prefix |
| Memory for shared prefixes | Full path stored per entry | Shared prefix segments stored once |
| HTTP method dispatch | Requires composite key (`"GET:/path"`) or nested map | Built into tree nodes — `handlers.get(method)` at leaf |

**The real insight:** HashMap lookup is O(1) for exact key match, but the key itself — a URL path string — must be hashed. Java's `String.hashCode()` is O(L) where L = string length. So HashMap lookup for a path like `/api/v2/users/profile` is O(18) (character count). The trie lookup splits this into 4 segment lookups, each hashing a shorter segment. The total hash work is similar, but the trie gains the ability to support path parameters and prefix matching in the future.

#### Trie Structure in Memory

For registered routes `GET /api/status` and `POST /api/users`:

```
TrieNode (root)                          ← 1 HashMap for children, 1 HashMap for handlers
  └── children: { "api" → TrieNode }     ← HashMap.get("api") = O(1)
        └── children: {
              "status" → TrieNode         ← handlers: { GET → λ(getStatus) }
              "users"  → TrieNode         ← handlers: { POST → λ(createUser) }
            }
```

**Memory overhead per TrieNode:**
- `children`: `HashMap<String, TrieNode>` → 48 bytes base + 16 bytes per entry (Java 21 x64)
- `handlers`: `HashMap<HttpMethod, RouteHandler>` → 48 bytes base + ~16 bytes per entry
- Object header: 16 bytes (Java 21 compressed oops)
- **Total per node: ~128 bytes minimum** (even with 0 children/handlers due to HashMap initialization)
- **Total for the example above: 4 nodes × ~128 bytes = ~512 bytes**

For a typical REST API with 50 routes sharing common prefixes like `/api/v1/*`, the trie would have roughly 60-80 nodes — well under 10 KB of memory. This is negligible.

#### Complexity Analysis

**`addRoute(HttpMethod, String, RouteHandler)`:**
```
Time:  O(K) where K = number of non-empty segments in the path
       Each segment: HashMap.containsKey() = O(1) + HashMap.put() = O(1)
       Total: K × O(1) = O(K)

Space: O(K) — creates at most K new TrieNode objects (for new paths)
       Shared prefixes reuse existing nodes
```

**`route(HttpRequest)`:**
```
Time:  O(K) where K = number of non-empty segments
       Each segment: HashMap.containsKey() + HashMap.get() = O(1)
       Final: handlers.containsKey() + handlers.get() = O(1)
       Plus: method.invoke() = ~3-5ns overhead for reflective call (after JIT warmup)
       Total: O(K) + O(1) reflection

Space: O(1) — only traverses existing nodes, no allocations
       (ignoring the String[] from path.split("/") which is O(K))
```

**Important:** `path.split("/")` creates a new `String[]` array on every request. For a path with K segments, this is K+1 string allocations. In a high-throughput server, this would be a GC pressure point. An optimization would be to walk the path string in-place using `indexOf('/')` instead of split.

#### Thread Safety of the Router

The trie has **no synchronization**. This is safe because:

1. **Writes** (`addRoute`, `registerController`) happen exclusively during the startup phase (`RouteScanner.scan()`) — before `Server.start()` opens the accept loop.
2. **Reads** (`route()`) happen from multiple thread-pool workers during the runtime phase.
3. The **happens-before relationship** is established by the sequential call in `Server.scanAndStart()`:
   ```java
   routeScanner.scan(basePackage);  // All writes complete here
   this.start();                     // accept() loop begins — all reads after this
   ```
   The `start()` method calls `new ServerSocket()` which involves I/O synchronization, establishing a happens-before edge. All trie writes are visible to all threads that subsequently call `accept()`.

**If you added routes at runtime** (e.g., hot-reloading controllers), you'd need `ConcurrentHashMap` for the children/handlers maps or a `ReadWriteLock` around the trie.

---

### 3.2 LRU Cache — Doubly-Linked List + HashMap

**File:** [LRUCache.java](../src/main/java/com/Shreyansh/webserver/cache/LRUCache.java)

#### Algorithm Step-by-Step

The LRU cache is the classic `O(1) get / O(1) put / O(1) evict` data structure from LeetCode #146. Here's how each operation works internally:

**`get(key)`:**
```
1. map.containsKey(key)?
   ├── YES (cache hit):
   │   a. node = map.get(key)                      ← O(1) HashMap lookup
   │   b. Remove node from its current position:    ← O(1) pointer surgery
   │      node.prev.next = node.next
   │      node.next.prev = node.prev
   │   c. Insert node right after HEAD sentinel:    ← O(1) pointer surgery
   │      node.next = head.next
   │      head.next.prev = node
   │      head.next = node
   │      node.prev = head
   │   d. Return CachedFile(node.value, node.contentType)
   │
   └── NO (cache miss):
       Return null
```

**`put(key, value, contentType)`:**
```
1. map.containsKey(key)?
   └── YES: remove(map.get(key))                   ← Remove old version first

2. map.size() == capacity?
   └── YES: remove(tail.prev)                      ← Evict LRU entry (just before TAIL sentinel)

3. Create new Node(key, value, contentType)
4. insertToFront(node)                              ← Add to map + link after HEAD
```

**Visual example (capacity=3):**

```
Initial state (3 entries, full):
  HEAD ←→ [C] ←→ [B] ←→ [A] ←→ TAIL
  map: { "A"→A, "B"→B, "C"→C }

get("A") — move A to front:
  HEAD ←→ [A] ←→ [C] ←→ [B] ←→ TAIL

put("D", ...) — cache full, evict LRU (B):
  remove(tail.prev) → remove B
  HEAD ←→ [D] ←→ [A] ←→ [C] ←→ TAIL
  map: { "A"→A, "C"→C, "D"→D }
```

#### Why Sentinel Nodes?

The HEAD and TAIL sentinels are dummy nodes that are never removed. They eliminate null checks in pointer manipulation:

```java
// WITHOUT sentinels — must handle edge cases:
if (node == head) { head = node.next; }           // Special case: removing first element
if (node == tail) { tail = node.prev; }           // Special case: removing last element
if (node.prev != null) { node.prev.next = ... }   // Null check every time
if (node.next != null) { node.next.prev = ... }   // Null check every time

// WITH sentinels — always safe:
node.prev.next = node.next;    // prev is never null (at minimum it's HEAD)
node.next.prev = node.prev;    // next is never null (at minimum it's TAIL)
```

This reduces the `remove()` method from ~8 lines with conditionals to 2 lines. Sentinel nodes are a standard technique in linked list implementations — used in the Linux kernel's `list.h` and Java's `LinkedList`.

#### Why Not `LinkedHashMap`?

Java provides `LinkedHashMap` with `accessOrder=true` which is essentially a built-in LRU cache:

```java
// Java's built-in approach:
LinkedHashMap<String, CachedFile> cache = new LinkedHashMap<>(capacity, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, CachedFile> eldest) {
        return size() > capacity;
    }
};
```

This would be ~5 lines vs our ~85 lines. The hand-built version was chosen because:

1. **Interview relevance:** LRU cache implementation is one of the most common data structure interview questions (LeetCode #146). Building it from scratch demonstrates understanding of pointer manipulation, sentinel nodes, and the HashMap + linked list combination.
2. **Thread safety control:** `LinkedHashMap` is not thread-safe. We'd need to wrap it in `Collections.synchronizedMap()`, which synchronizes on the wrapper — giving us less control than our explicit `synchronized` methods.
3. **Custom data model:** Our `Node` stores `byte[]` + `String contentType` together. `LinkedHashMap` would require a wrapper DTO anyway.

#### Thread Safety: `HashMap` + `synchronized` (Design Decision)

The implementation uses a plain `HashMap<String, Node>` for the map and wraps all public methods in `synchronized`:

```java
private final Map<String, Node> map;                 // Regular HashMap — not CHM

public synchronized CachedFile get(String key) { ... }  // Synchronized on 'this'
public synchronized void put(...) { ... }               // Same intrinsic lock
```

**Why `HashMap` instead of `ConcurrentHashMap`?** The `synchronized` keyword on the methods acquires the object's intrinsic lock, meaning only one thread can execute any public method at a time. A `ConcurrentHashMap`'s fine-grained segment locking would provide no additional benefit because all access is already serialized by the method-level lock. Using a regular `HashMap` avoids the CAS overhead inherent in CHM internals — making it both simpler and slightly faster.

**The three valid approaches:**
- **Option A (our choice, simplest):** `synchronized` + regular `HashMap` → serialized access, no unnecessary overhead
- **Option B (redundant):** `synchronized` + `ConcurrentHashMap` → works but CHM features are wasted
- **Option C (highest throughput):** `ConcurrentHashMap` + lock-free linked list operations → complex, error-prone

The current choice prioritizes correctness and simplicity over micro-optimization, which is the right tradeoff for a framework where the cache is not the bottleneck (disk I/O is).

---

### 3.3 Rate Limiter — Fixed Window Counter Algorithm

**File:** [RateLimiter.java](../src/main/java/com/Shreyansh/webserver/middleware/RateLimiter.java)

#### Algorithm Classification

The rate limiter implements a **Fixed Window Counter** algorithm. This is **not** a sliding window or token bucket (common mislabeling). Here's the distinction:

| Algorithm | How It Works | Boundary Burst? |
|-----------|-------------|----------------|
| **Fixed Window Counter** (ours) | Counter resets to 0 every 1-second window boundary | Yes — up to 2× limit across boundary |
| Sliding Window Log | Stores timestamps of all requests, counts within trailing window | No — exact, but O(N) memory |
| Sliding Window Counter | Weighted average of current and previous window | Approximately no — good tradeoff |
| Token Bucket | Tokens refill at a fixed rate, consumed per request | No — smooth rate, allows controlled bursts |
| Leaky Bucket | Requests queue, processed at fixed rate | No — smoothest, adds latency |

#### The Boundary Burst Problem

Our fixed-window implementation allows up to **200 requests in <1 second** across a window boundary:

```
Timeline (requests per millisecond):
                  Window 1                    Window 2
  ─────────────────|─────────────────────────|──────────────────
  t=0              t=1000ms                   t=2000ms

Scenario: 100 requests at t=999ms (end of window 1)
          100 requests at t=1001ms (start of window 2)

Result:   200 requests in 2ms — but each window individually allows only 100.
          The rate limiter doesn't catch this because windows are independent.
```

**Mathematical analysis:**
- Worst-case instantaneous throughput: `2 × maxRequestsPerSecond` in a <1ms burst (default: 200 req/s)
- Average sustained throughput: exactly `maxRequestsPerSecond` (default: 100 req/s — can't exceed this over any 2-second span)

This is a **known and accepted tradeoff**. The fixed-window approach was chosen for:
1. **O(1) time and space per request** — just an atomic increment and a timestamp check
2. **Simplicity** — easy to reason about, test, and debug
3. **Adequate protection** — the 2× burst is a theoretical worst case. In practice, distributed client requests rarely align exactly on window boundaries.

#### Memory Protection: The GC Sweep

```java
if (ipBuckets.size() > gcThreshold) {                     // default: 10,000
    long now = System.currentTimeMillis();
    ipBuckets.entrySet().removeIf(e -> now - e.getValue().lastReset > gcStalenessMillis);  // default: 60s
}
```

This prevents a **distributed DoS memory exhaustion attack** where an attacker uses thousands of source IPs to create unbounded `Bucket` entries. All thresholds are now configurable via constructor parameters (`gcThreshold`, `gcStalenessMillis`).

**Complexity of the sweep:**
```
Time:  O(N) where N = number of tracked IPs (iterates all entries)
Space: O(1) — in-place removal

Trigger condition: Only runs when size > gcThreshold (default: 10,000; amortized cost is low)
Removal criterion: Any bucket not accessed in the last gcStalenessMillis (default: 60 seconds)
```

**Latency impact:** When triggered, the O(N) sweep runs synchronously on the request thread. For N=10,000 entries, this is ~1-2ms on modern hardware. This causes a single-request latency spike — negligible, but worth noting. A background scheduled cleanup (e.g., `ScheduledExecutorService`) would eliminate this spike.

**Memory per bucket:**
- `Bucket` object: 16 bytes (header) + 16 bytes (AtomicInteger) + 8 bytes (volatile long) = ~40 bytes
- Map entry overhead: ~32 bytes
- IP string key: ~56 bytes (average IPv4 string "192.168.1.100")
- **Total per IP: ~128 bytes**
- **10,000 IPs: ~1.25 MB** — trivial for the JVM heap

The 10,000 threshold is the default, providing a good balance between memory usage and cleanup frequency. It can be tuned via the constructor.

#### Concurrency Design (Fine-Grained Locking)

The RateLimiter uses a sophisticated multi-level locking strategy:

```
Level 1: ConcurrentHashMap for ipBuckets
  └── Thread-safe get/put/computeIfAbsent
  └── Allows N IPs to be processed truly concurrently

Level 2: synchronized(bucket) for window reset
  └── Per-bucket lock — only blocks threads for the SAME IP
  └── Different IPs never contend

Level 3: AtomicInteger.incrementAndGet() for count
  └── Lock-free atomic operation via CPU CAS instruction
  └── No blocking, no lock acquisition
```

**Contention analysis:**
- Requests from **different IPs** → zero contention (separate buckets, separate locks)
- Requests from **same IP** → contention only during the window reset check (synchronized block, ~100ns)
- The `incrementAndGet()` after the sync block is **lock-free** — uses CPU-level Compare-And-Swap

This is why 100 threads can run the rate limiter concurrently with minimal contention — the synchronized block is scoped to individual buckets, not global.

---

### 3.4 HTTP Parser — Hand-Rolled Protocol Implementation

**File:** [HttpParser.java](../src/main/java/com/Shreyansh/webserver/http/HttpParser.java)

#### Why Byte-by-Byte Parsing?

The `readLine()` method reads one byte at a time from the InputStream:

```java
while ((c = in.read()) != -1) {
    if (c == '\r') {
        int next = in.read();
        if (next == '\n') break;    // Found CRLF → line complete
    }
    // ...
}
```

**Why not use `BufferedReader.readLine()`?**

`BufferedReader` has a critical problem for HTTP parsing: **it over-reads.** `BufferedReader` internally maintains an 8 KB buffer. When you call `readLine()`, it reads up to 8192 bytes from the underlying stream to fill its buffer — even if the line is only 20 bytes long. This means it will **consume body bytes that haven't been read yet**, making the subsequent `Content-Length`-based body read incorrect.

The byte-by-byte approach is the simplest way to read exactly the right amount from the stream without buffering artifacts. The performance cost is real but acceptable:

**Performance characteristics:**
```
Byte-by-byte:  ~1 system call per byte (in.read() delegates to socket read())
               For a typical request with 200 bytes of headers: ~200 system calls

BufferedReader: ~1 system call per 8KB buffer fill
                For the same request: 1 system call

Throughput impact: At 5,800 RPS, this adds ~1.2M extra syscalls/sec
                   At ~100ns per syscall: ~120ms total CPU time per second
                   On a multi-core system: negligible (spread across 100 threads)
```

**A better approach (future optimization):** Use a custom buffered reader that reads into a buffer but tracks the read position precisely, so body bytes aren't consumed during header parsing. This is how Tomcat and Jetty implement their HTTP parsers.

#### ISO-8859-1 Body Encoding — Binary Data Preservation

```java
body = new String(bodyBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
```

This is the single most important encoding decision in the parser. Here's why:

```
UTF-8 encoding table (variable-length):
  0x00-0x7F → 1 byte (identical to ASCII)
  0x80-0xBF → INVALID as leading byte → replaced with U+FFFD (corruption!)
  0xC0-0xFF → Multi-byte sequence leader

ISO-8859-1 encoding table (fixed-length):
  0x00-0xFF → 1:1 mapping, every byte value maps to exactly one character
             No invalid sequences. No multi-byte characters.
```

**If UTF-8 were used:**
```
Input bytes:  [0xFF, 0x8A, 0x00]
UTF-8 decode: [U+FFFD, U+FFFD, 0x00]  ← 0xFF and 0x8A are invalid UTF-8 lead bytes
UTF-8 encode: [0xEF, 0xBF, 0xBD, 0xEF, 0xBF, 0xBD, 0x00]  ← 7 bytes, not 3!
```

**With ISO-8859-1:**
```
Input bytes:  [0xFF, 0x8A, 0x00]
ISO decode:   [ÿ,    Š,    NUL]        ← Every byte has a valid character
ISO encode:   [0xFF, 0x8A, 0x00]        ← Identical to input. Zero data loss.
```

This is validated by `testPostBinaryEcho()` in the integration test suite, which sends bytes `[0x00, 0x01, 0xFF, 0x8A, 0x00, 0x05, 0x7F]` and asserts the exact same bytes come back via `assertArrayEquals`.

#### Input Validation Bounds

The parser enforces hard limits before allocating memory:

| Check | Limit | On Violation |
|-------|-------|--------------|
| Request line format | 3 space-separated parts | Return `null` |
| HTTP method | Must match `HttpMethod` enum | Return `null` (catch `IllegalArgumentException`) |
| Header line length | 8 KB (`MAX_LINE_LENGTH`) | Throw `IOException` |
| Malformed headers | Must contain `:` | Silently skip |
| `Content-Length` | 0–10 MB (`MAX_BODY_SIZE`) | Return `null` |
| Non-numeric `Content-Length` | Must parse as integer | Return `null` |

These bounds close the primary OOM and crash vectors that existed before the senior-level hardening pass.

#### TCP Fragmentation Handling

The body read loop handles TCP segmentation:

```java
while (bytesRead < length) {
    int read = inputStream.read(bodyBytes, bytesRead, length - bytesRead);
    if (read == -1) break;
    bytesRead += read;
}
```

TCP does **not** guarantee that a single `read()` call returns all requested bytes. The sender may send 1000 bytes, but the receiver might get them in chunks:

```
Send:    [────────── 1000 bytes ──────────]
Receive: [─── 536 ───][── 400 ──][─ 64 ─]   ← 3 separate read() calls
```

This happens because of TCP's **Maximum Segment Size (MSS)** — typically 1460 bytes on Ethernet — and kernel buffer timing. The loop ensures all `Content-Length` bytes are read regardless of how they arrive.

---

### 3.5 RouteScanner — Classpath Scanning via Reflection

**File:** [RouteScanner.java](../src/main/java/com/Shreyansh/webserver/core/RouteScanner.java)

#### Dual-Mode Resolution

The scanner detects its execution context and adapts:

```
ClassLoader.getResource("com/Shreyansh/webserver")
  │
  ├── Protocol = "file://" (development)
  │   URL = "file:///C:/project/build/classes/java/main/com/Shreyansh/webserver"
  │   Strategy: Recursive File.listFiles() over the directory tree
  │   Time: O(C) where C = total .class files in the package tree
  │
  └── Protocol = "jar://" (production)
      URL = "jar:file:///path/to/app.jar!/com/Shreyansh/webserver"
      Strategy: JarFile.entries() — linear scan of all JAR entries
      Time: O(E) where E = total entries in the JAR (includes non-class files)
```

**Performance concern with JAR mode:** The JAR scan iterates ALL entries in the JAR, not just the target package. For a JAR with 1000 entries where only 20 are in the target package, 980 entries are scanned and discarded. This is O(E) not O(C). For this project with ~20 classes, the difference is negligible, but for large applications this could be optimized with a filtered stream.

#### Reflection Cost Analysis

```java
Class<?> clas = Class.forName(className);              // ~50µs first call, ~1µs cached
clas.isAnnotationPresent(RestController.class);         // ~0.5µs
clas.getDeclaredConstructor().newInstance();             // ~5µs
router.registerController(controller);                   // Includes getDeclaredMethods() + addRoute()
```

The total startup scanning cost for the current codebase (~18 classes) is approximately **0.5-1ms** — imperceptible. This cost is paid once at startup and never again.

---

## Section 4 — Edge Cases, Failure Modes & Bottlenecks

### 4.1 Edge Cases — Fixed and Remaining

#### ✅ Fixed Edge Cases

| Scenario | Previous Behavior | Fix Applied |
|----------|------------------|-------------|
| HTTP method `CONNECT` or `TRACE` sent | `IllegalArgumentException` crash | HttpParser catches `IllegalArgumentException`, returns `null` → connection closed gracefully |
| Request line with <3 space-separated parts | `ArrayIndexOutOfBoundsException` crash | HttpParser validates `line1.length >= 3` before accessing indices |
| Extremely long header line (no CRLF for >10MB) | `OutOfMemoryError` — unbounded `StringBuilder` | `readLine()` now throws `IOException` when line exceeds 8 KB (`MAX_LINE_LENGTH`) |
| Static file >50MB requested | `OutOfMemoryError` — loads entire file into heap | `StaticFileHandler` checks `Files.size()` against `MAX_FILE_SIZE` (50 MB) before reading |
| Malformed `Content-Length` (non-numeric) | `NumberFormatException` crash | HttpParser catches `NumberFormatException`, returns `null` → connection closed |
| `Content-Length: -1` | `NegativeArraySizeException` crash | HttpParser validates `length >= 0` and `length <= MAX_BODY_SIZE` (10 MB) |
| POST/DELETE to valid path but wrong method | Returns 404 (incorrect) | Router now returns 405 Method Not Allowed when path exists but method doesn't match |
| Query parameters in URL (e.g., `/api?key=val`) | Route lookup includes `?key=val` → always 404 | Router strips query string at `?` before trie walk |
| Malformed header line (no colon) | `ArrayIndexOutOfBoundsException` crash | HttpParser validates `parts.length == 2` before accessing; malformed headers silently skipped |

#### ⚠️ Remaining Known Edge Cases

| Scenario | What Happens | Root Cause | Risk Level |
|----------|-------------|------------|------------|
| Concurrent route registration at runtime | Data race on HashMap in TrieNode | No synchronization on trie | **N/A** — not a supported operation (startup-only) |
| `Host` header with port (e.g., `Host: localhost:8080`) | Header stored correctly but never used | No virtual host support | **None** — no impact |
| Chunked transfer encoding (`Transfer-Encoding: chunked`) | Body silently ignored | Only `Content-Length` body framing supported | **Medium** — some HTTP clients use chunked by default |
| HTTP/2 connection preface (`PRI * HTTP/2.0`) | Parsed as an invalid HTTP/1.1 request | No HTTP/2 support | **Low** — returns null, connection closed |

### 4.2 Known Performance Bottlenecks

#### Bottleneck 1: Single-Threaded Accept Loop

```java
while (this.isRunning) {
    Socket client = serverSocket.accept();    // ← Main thread blocks here
    client.setSoTimeout(5000);
    executor.execute(processor);
}
```

The `accept()` call and subsequent `setSoTimeout()` + `RequestProcessor` construction + `executor.execute()` all happen sequentially on the main thread. At extremely high connection rates (>10,000 conn/sec), the accept loop becomes the bottleneck because each iteration takes ~1-5µs:

```
accept()               ~1µs (kernel returns pending connection)
setSoTimeout()         ~0.5µs (JNI call to set socket option)
new RequestProcessor() ~0.1µs (object allocation)
executor.execute()     ~0.5µs (enqueue to LinkedBlockingQueue)
───────────────────────────
Total per connection:  ~2-3µs
Max theoretical rate:  ~300K-500K accepts/sec
```

In practice, this is NOT the bottleneck (our measured ~5,800 RPS is far below this ceiling). The actual bottleneck is I/O and thread pool saturation.

#### Bottleneck 2: `String.split("/")` on Every Request

Both `addRoute()` and `route()` call `path.split("/")`, which:
1. Compiles the regex `/` pattern (JVM may cache this, but not guaranteed)
2. Allocates a new `String[]` array
3. Creates new `String` objects for each segment

At 5,800 RPS, this is 5,800 array allocations and ~23,000 string allocations per second — minor GC pressure.

#### Bottleneck 3: Non-Buffered Byte-by-Byte Socket Reading

`HttpParser.readLine()` calls `in.read()` per byte. Each `read()` on a `SocketInputStream` may trigger a kernel system call. For a typical request header of ~200 bytes, that's ~200 system calls. At 5,800 RPS: ~1.16M system calls/sec just for header parsing.

#### Bottleneck 4: Entire Response Body in Memory

```java
private byte[] body;    // Entire response must fit in a single byte[]
```

For the 10MB `tech.jpg` image: the file is read entirely into a `byte[]`, stored in the LRU cache as a `byte[]`, and written to the socket as a `byte[]`. Three copies of a 10MB buffer exist simultaneously during a response:
1. LRU cache node (permanent until evicted)
2. `CachedFile` DTO returned from `get()`
3. `HttpResponse.body` reference (same `byte[]` — not a deep copy)

Actually, references 2 and 3 point to the same `byte[]` (no deep copy), so it's 2 copies: one in the cache node, one referenced by the response.

### 4.3 Thread Pool Saturation Behavior

When all 100 threads are busy processing requests:

```
New connection arrives → accept() returns Socket
  → executor.execute(processor)
    → ThreadPoolExecutor internal queue (LinkedBlockingQueue, UNBOUNDED)
      → Task queued, waiting for a free thread

Client side: Connection is accepted (TCP handshake completes) but no response comes.
             After 5 seconds: server's SoTimeout fires, but the RequestProcessor
             hasn't even started running yet.
             The Socket times out BEFORE processing begins.
```

**Key insight:** The `LinkedBlockingQueue` inside `newFixedThreadPool()` is **unbounded** (default capacity = `Integer.MAX_VALUE`). This means the thread pool will never reject a task — it will queue indefinitely. Under sustained overload, this queue grows without bound, eventually causing `OutOfMemoryError`.

**The ServerSocket backlog (10,000)** is a separate queue at the OS kernel level. When 10,000 connections are waiting in the kernel queue AND the thread pool queue is also full, the OS starts **resetting new connections** (sending TCP RST).

```
Protection Layers (in order):
1. Rate Limiter → 100 req/sec/IP → blocks excessive IPs (runs INSIDE thread pool)
2. Thread Pool → 100 concurrent workers → queues excess tasks
3. ThreadPool Queue → Unbounded → absorbs bursts (but can OOM)
4. OS Backlog → 10,000 → kernel-level connection queue
5. OS TCP Stack → RST → rejects connections when backlog is full
```
