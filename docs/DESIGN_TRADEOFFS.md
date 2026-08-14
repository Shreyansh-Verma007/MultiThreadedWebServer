# ⚖️ Design Tradeoffs & Configuration Matrix

> Every engineering decision involves tradeoffs. This document makes them explicit — what was chosen, what was rejected, and why. Written for system design interviews and architectural discussions.

---

## Section 5 — Architectural Tradeoffs

### 5.1 Blocking I/O vs Non-Blocking I/O (NIO)

**Decision:** Blocking `ServerSocket` + `Socket` (java.net)
**Rejected:** `java.nio.channels.ServerSocketChannel` + `Selector`

| Aspect | Blocking I/O (chosen) | NIO with Selector |
|--------|----------------------|-------------------|
| **Thread usage** | 1 thread per connection (100 max) | 1 thread handles thousands of connections |
| **Code complexity** | Low — straightforward read/write | High — state machines, buffer management, channel registration |
| **Max concurrent connections** | Bounded by thread pool size (100) | Bounded by file descriptors (OS limit, typically 65K) |
| **Latency per request** | Low — dedicated thread, no context switching between connections | Potentially higher — single thread multiplexes across connections |
| **Memory per connection** | ~1 MB (thread stack) × 100 = ~100 MB | ~4 KB (buffer) × 10,000 = ~40 MB |
| **Debugging** | Easy — stack traces show full call chain | Hard — execution spread across event loop callbacks |
| **Learning value** | Teaches threads, synchronization, thread pools | Teaches event loops, selectors, non-blocking patterns |

**Why blocking was chosen:**
1. **Educational clarity.** The primary goal is demonstrating concurrency concepts — thread pools, synchronized blocks, atomic operations. These are directly visible in the blocking model.
2. **Sufficient performance.** At ~5,800 RPS with 100 threads, the server handles realistic workloads. The bottleneck is I/O, not thread count.
3. **Incremental migration path.** The architecture cleanly separates the accept loop (`Server`) from request processing (`RequestProcessor`). Migrating to NIO requires changing only `Server.start()` — the `RequestProcessor` logic remains identical.

**When NIO would be necessary:**
- >10,000 concurrent connections (thread-per-connection becomes prohibitively memory-expensive)
- Long-lived connections (WebSockets, Server-Sent Events)
- Ultra-low-latency requirements where thread context switching is measurable

---

### 5.2 Fixed Thread Pool vs Other Concurrency Models

**Decision:** `Executors.newFixedThreadPool(100)`
**Rejected alternatives:**

| Model | Characteristics | Why Not Chosen |
|-------|----------------|----------------|
| `newCachedThreadPool()` | Creates threads on demand, reuses idle threads | **Unbounded** — under DDoS, creates thousands of threads → OOM |
| `newVirtualThreadPerTaskExecutor()` (Java 21) | Lightweight virtual threads, millions possible | Would work well, but hides thread management mechanics that are educational goals |
| Manual `new Thread()` per request | Simplest possible model | No reuse, no bounds, no backpressure — production anti-pattern |
| `ForkJoinPool` | Work-stealing, good for recursive tasks | HTTP request processing is not recursive/fork-join shaped |

**Why fixed pool of 100:**

The fixed pool provides **bounded resource consumption** — exactly the right property for a server:

```
Memory bound:   100 threads × ~1 MB stack each = ~100 MB maximum thread stack memory
CPU bound:      100 threads on N cores → at most N threads running simultaneously
                On a 4-core machine: 25× overprovisioning → good for I/O-bound workloads
Backpressure:   Connection 101 is queued, not rejected
                Queue absorbs traffic bursts without spawning new threads
```

**The internal queue of `newFixedThreadPool()` is `LinkedBlockingQueue` with unbounded capacity.** This is a deliberate Java design choice — tasks are never rejected, they queue indefinitely. Under sustained overload, this queue grows without bound. A more robust production choice would be:

```java
// Bounded queue with rejection policy (not implemented — noted as future work):
new ThreadPoolExecutor(
    100, 100, 0L, TimeUnit.MILLISECONDS,
    new LinkedBlockingQueue<>(5000),           // Queue cap: 5000
    new ThreadPoolExecutor.CallerRunsPolicy()  // Overflow: main thread processes the task
);
```

---

### 5.3 HTTP/1.0-Style Connection Handling vs Keep-Alive

**Decision:** One request per socket, close after response.
**Rejected:** HTTP/1.1 persistent connections (keep-alive).

```java
// Current behavior (in RequestProcessor.run()):
finally {
    client.close();    // Always close — one request per connection
}
```

**Impact:**

```
Without keep-alive (current):
  Client sends 10 requests:
    SYN→SYN-ACK→ACK → Request → Response → FIN  (×10)
    = 10 TCP handshakes × ~1ms each = ~10ms overhead
    = 10 TIME_WAIT sockets on the client (ephemeral port consumption)

With keep-alive:
  Client sends 10 requests:
    SYN→SYN-ACK→ACK → Request → Response → Request → Response → ... → FIN
    = 1 TCP handshake = ~1ms overhead
    = 1 TIME_WAIT socket
```

**Why keep-alive was not implemented:**
1. **Complexity:** Keep-alive requires the `RequestProcessor` to loop, reading multiple HTTP requests from the same socket. This introduces state management (when to close? idle timeout? max requests per connection?).
2. **Thread pool interaction:** With keep-alive, a thread stays attached to a connection for its entire lifetime. If 100 clients hold persistent connections, the thread pool is fully saturated with idle connections, blocking new clients.
3. **Testing simplicity:** Each test creates a fresh connection — no shared state between requests.

**When it matters:** Under high-throughput benchmarks with many sequential requests from the same client. The TCP handshake overhead and ephemeral port exhaustion (observed during stress testing) are directly caused by the lack of keep-alive.

---

### 5.4 Graceful Shutdown ✅ (Implemented)

**Implementation:** `isRunning` is a `volatile boolean`. The `stop()` method sets it to `false` and closes the `ServerSocket` to break the accept loop. The `shutdown()` method waits for in-flight requests to complete.

```java
private volatile boolean isRunning;       // volatile for cross-thread visibility
private ServerSocket serverSocket;         // stored as field so stop() can close it

public void stop() {
    isRunning = false;
    if (serverSocket != null && !serverSocket.isClosed()) {
        serverSocket.close();              // Causes accept() to throw → exits loop
    }
}

private void shutdown() {
    executor.shutdown();                   // No new tasks accepted
    if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
        executor.shutdownNow();            // Force-kill after 30s
    }
}
```

**Why `volatile`?** The `stop()` method is called from a different thread (e.g., a test teardown or a shutdown hook). Without `volatile`, the accept loop thread might never see `isRunning = false` due to CPU cache coherency — it could keep running with its locally cached `true` value indefinitely.

**The accept loop** catches `IOException` from `serverSocket.close()` and checks `isRunning` to distinguish a shutdown-triggered exception from a real error:

```java
while (this.isRunning) {
    try {
        Socket client = serverSocket.accept();
        // ...
    } catch (IOException e) {
        if (!isRunning) break;             // Shutdown in progress — exit cleanly
        System.err.println("Error: " + e.getMessage());
    }
}
```

---

### 5.5 Manual DI vs Framework DI

**Decision:** All dependencies are manually wired in `Main.main()`.
**Rejected:** Spring's `@Autowired`, Google Guice, or a custom DI container.

```java
// Current: Explicit, visible wiring
Router router = new Router();
FilterChain filterChain = new FilterChain();
filterChain.addFilter(new RateLimiter());
LRUCache cache = new LRUCache(50);
StaticFileHandler fileHandler = new StaticFileHandler(cache);
Server server = new Server(8080, 100, router, filterChain, fileHandler);
```

**Advantages of manual wiring:**
1. **Zero magic.** Every dependency is visible in one file. No scanning, no proxy generation, no annotation processing.
2. **Fast startup.** No classpath scanning for `@Component`/`@Service`/`@Bean` (unlike Spring which scans thousands of classes).
3. **IDE navigability.** Ctrl+click on any constructor takes you directly to the dependency — no framework indirection.
4. **Testability.** The integration test manually constructs a `Server` with a custom configuration (port 8089, 10 threads) — no test framework magic needed.

**Where it breaks down:** With >20 components, manual wiring becomes a long, error-prone list. The current 5-component system is well within the comfort zone of manual DI.

---

### 5.6 Annotation-Based Routing: Reflection Tradeoffs

**Decision:** Controller methods are invoked via `method.invoke(controller, request)`.

```java
RouteHandler handler = request -> {
    return (HttpResponse) method.invoke(controller, request);
};
```

**Performance cost of reflection:**

```
Direct method call:      ~1-2 nanoseconds (JIT-compiled to a single CALL instruction)
method.invoke():         ~5-10 nanoseconds (after JIT warmup)
                         ~50 nanoseconds (first few calls, before JVM optimizes)
                         ~5000 nanoseconds (first call ever — method resolution)

Overhead at 5,800 RPS:  ~58 microseconds/second total
                         = 0.0058% of a single core
                         = Completely negligible
```

The JVM's `method.invoke()` is heavily optimized via **inflation** — after 15 invocations (default `sun.reflect.inflationThreshold`), the JVM generates a dedicated bytecode accessor class that eliminates most reflection overhead. The lambda wrapper also helps — the `Method` and `controller` references are captured once during registration, avoiding repeated lookups.

**Alternative: Code generation at startup**
Frameworks like Spring use CGLIB or ByteBuddy to generate proxy classes at startup, replacing reflection with direct calls. This is faster but adds a code-generation dependency and complexity. For this project's scale, reflection is more than sufficient.

---

## Section 6 — The Configuration Matrix

Every tunable parameter in the system, its default value, where it's defined, and the effect of changing it.

### 6.1 Server Configuration

| Parameter | Default | Location | Effect of Change |
|-----------|---------|----------|-----------------|
| **Port** | `8080` | [Main.java L18](../src/main/java/com/Shreyansh/webserver/Main.java#L18) | Changes the TCP port the server listens on. Ports <1024 require root/admin privileges. |
| **Thread Pool Size** | `100` | [Main.java L18](../src/main/java/com/Shreyansh/webserver/Main.java#L18) | Increase → more concurrent requests, more memory (~1MB stack/thread). Decrease → less memory, more queueing. |
| **Socket Backlog** | `10000` | [Server.java L39](../src/main/java/com/Shreyansh/webserver/core/Server.java#L39) | Max pending connections in OS queue. Increase → absorb larger bursts. Decrease → reject connections faster under load. OS may cap this (Linux: `net.core.somaxconn`). |
| **Socket Read Timeout** | `5000` ms | [Server.java L44](../src/main/java/com/Shreyansh/webserver/core/Server.java#L44) | Slowloris protection. Increase → more tolerance for slow clients, but more vulnerable to slow-read attacks. Decrease → aggressive timeout, may drop legitimate slow connections. |

### 6.2 Cache Configuration

| Parameter | Default | Location | Effect of Change |
|-----------|---------|----------|-----------------|
| **LRU Cache Capacity** | `50` entries | [Main.java L15](../src/main/java/com/Shreyansh/webserver/Main.java#L15) | Number of file entries (not bytes). Increase → fewer disk reads, more memory. Decrease → more disk reads, less memory. With the 10MB `tech.jpg`, 50 entries could mean ~500MB of cached file data in the worst case. |
| **Static Directory** | `"src/main/resources"` | [StaticFileHandler.java L9](../src/main/java/com/Shreyansh/webserver/cache/StaticFileHandler.java#L9) | Base directory for filesystem file resolution. Hardcoded — only relevant during development. In production (JAR), the classpath fallback serves files instead. |

### 6.3 Rate Limiter Configuration

All parameters are configurable via the `RateLimiter(int, long, int, long)` constructor. The no-arg `RateLimiter()` constructor uses these defaults:

| Parameter | Default | Constructor Arg | Effect of Change |
|-----------|---------|----------------|------------------|
| **Max Requests/Window/IP** | `100` | `maxRequestsPerSecond` | Increase → more permissive, less DoS protection. Decrease → stricter, may block legitimate high-traffic clients. |
| **Window Duration** | `1000` ms | `windowMillis` | The fixed-window duration. Shorter windows are stricter but more prone to boundary bursts. |
| **GC Threshold (IP count)** | `10000` | `gcThreshold` | Triggers stale bucket cleanup when exceeded. Increase → less frequent cleanup, more memory usage. Decrease → more frequent cleanup, O(N) scan more often. |
| **GC Staleness Timeout** | `60000` ms (60s) | `gcStalenessMillis` | Buckets older than this are evicted during GC. Increase → buckets live longer (track returning IPs better). Decrease → more aggressive cleanup, less memory. |

### 6.4 HTTP Parser Configuration

| Parameter | Default | Location | Effect of Change |
|-----------|---------|----------|-----------------|
| **Body Encoding** | `ISO-8859-1` | `HttpParser.java` | Changing to UTF-8 would corrupt binary request bodies. Must remain ISO-8859-1 for data integrity. |
| **Max Header Line Length** | `8192` bytes (8 KB) | `HttpParser.MAX_LINE_LENGTH` | Maximum length of a single header line. Exceeding this throws `IOException`, closing the connection. Matches Apache/Nginx defaults. |
| **Max Body Size** | `10,485,760` bytes (10 MB) | `HttpParser.MAX_BODY_SIZE` | Maximum `Content-Length` accepted. Requests exceeding this are rejected (parser returns `null`). Prevents OOM from malicious `Content-Length: 2000000000`. |

### 6.5 Static File Configuration

| Parameter | Default | Location | Effect of Change |
|-----------|---------|----------|-----------------|
| **Max File Size** | `52,428,800` bytes (50 MB) | `StaticFileHandler.MAX_FILE_SIZE` | Maximum file size that will be loaded into memory and served. Files exceeding this return `null` (404). Prevents OOM from serving very large files. |

### 6.6 Routing Configuration

| Parameter | Default | Location | Effect of Change |
|-----------|---------|----------|-----------------|
| **Scan Base Package** | `"com.Shreyansh.webserver"` | [Main.java](../src/main/java/com/Shreyansh/webserver/Main.java) | Scope of classpath scanning. Narrower → faster startup, fewer classes inspected. Wider → discovers controllers in more packages. |
| **Root Path Rewrite** | `/` → `/index.html` | [RequestProcessor.java](../src/main/java/com/Shreyansh/webserver/core/RequestProcessor.java) | Hardcoded rewrite. The default document name `index.html` is not configurable. |
| **Query String Handling** | Stripped before routing | [Router.java](../src/main/java/com/Shreyansh/webserver/routing/Router.java) | Everything after `?` is removed before trie lookup. Query parameters are not parsed or passed to handlers. |

### 6.7 Supported MIME Types

Defined in [StaticFileHandler.java](../src/main/java/com/Shreyansh/webserver/cache/StaticFileHandler.java):

| Extension | MIME Type | Category |
|-----------|-----------|----------|
| `.html` | `text/html` | Document |
| `.css` | `text/css` | Stylesheet |
| `.js` | `application/javascript` | Script |
| `.json` | `application/json` | Data |
| `.xml` | `application/xml` | Data |
| `.txt` | `text/plain` | Text |
| `.png` | `image/png` | Image |
| `.jpg` / `.jpeg` | `image/jpeg` | Image |
| `.gif` | `image/gif` | Image |
| `.svg` | `image/svg+xml` | Image |
| `.ico` | `image/x-icon` | Image |
| `.woff` | `font/woff` | Font |
| `.woff2` | `font/woff2` | Font |
| `.ttf` | `font/ttf` | Font |
| `.pdf` | `application/pdf` | Document |
| *(all others)* | `application/octet-stream` | Binary fallback |
