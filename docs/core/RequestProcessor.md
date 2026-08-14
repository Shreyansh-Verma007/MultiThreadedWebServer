# 📄 `RequestProcessor.java` — Request Lifecycle Manager

**Package:** `com.Shreyansh.webserver.core`  
**Path:** `src/main/java/com/Shreyansh/webserver/core/RequestProcessor.java`  
**Role:** Implements `Runnable` — the complete lifecycle of a single HTTP request/response cycle on a thread pool worker.

---

## File Overview

`RequestProcessor` is the **execution unit** for each incoming connection. It implements `Runnable` so it can be submitted to the `ExecutorService` thread pool. Each instance handles exactly one HTTP request from one TCP connection:

```
Socket accepted → RequestProcessor created → submitted to pool → run() called → socket closed
```

This class is the **orchestrator** — it ties together every subsystem in the correct order: parsing, filtering, routing, static file fallback, and response serialization.

---

## The Request Lifecycle (5 Phases)

```
┌──────────────────────────────────────────────────────────┐
│  Phase 1: PARSE                                          │
│    HttpParser.parseRequest(inputStream, clientIp)         │
│    Raw bytes → structured HttpRequest                    │
│    null? → skip to cleanup (no response sent)            │
│                                                          │
│  Phase 2: FILTER + ROUTE (conditional)                   │
│    if filterChain.execute(request, response):            │
│      router.route(request) → controller or 404/405       │
│    else: response already has 429 from RateLimiter       │
│                                                          │
│  Phase 3: STATIC FILE FALLBACK                           │
│    Only if: filters passed AND status == 404 AND GET     │
│    "/" → "/index.html" rewrite                           │
│    StaticFileHandler.get(path) → LRU cache or disk       │
│    SecurityException / IO error → 500                    │
│                                                          │
│  Phase 4: RESPOND                                        │
│    response.send(outputStream) → raw bytes on wire       │
│    (runs for both allowed and rate-limited requests)     │
│                                                          │
│  Phase 5: CLEANUP                                        │
│    finally { socket.close() }                            │
└──────────────────────────────────────────────────────────┘
```

---

## Line-by-Line Explanation

### Fields and Constructor (Lines 12–23)

```java
public class RequestProcessor implements Runnable {
    private final Socket client;                               // Unique per connection
    private final Router router;                               // Shared across all processors
    private final FilterChain filterChain;                     // Shared across all processors
    private final StaticFileHandler fileHandler;               // Shared across all processors

    public RequestProcessor(Socket client, Router router, FilterChain filterChain, StaticFileHandler fileHandler) {
        this.client = client;
        this.router = router;
        this.filterChain = filterChain;
        this.fileHandler = fileHandler;
    }
```

**Thread safety implication:** Shared objects must be either thread-safe (Router: read-only after startup; FilterChain: read-only after startup; RateLimiter: `ConcurrentHashMap` + per-bucket locking; StaticFileHandler: synchronized LRU cache) or stateless (HttpParser: static method).

### `run()` — The Core Lifecycle Method (Lines 25–78)

#### Setup and Parse (Lines 27–37)

```java
    public void run() {
        try {
            InputStream inputStream = client.getInputStream();
            OutputStream outputStream = client.getOutputStream();

            String clientIp = client.getInetAddress().getHostAddress();
            if (clientIp == null) clientIp = "0.0.0.0";        // Line 32: Fallback for unresolved addresses
            HttpRequest request = HttpParser.parseRequest(inputStream, clientIp);

            if (request == null) {
                return;                                        // Invalid/empty request → close silently
            }
```

**Line 32:** Null IP fallback ensures rate limiting always has a bucket key, even for edge-case socket states.

**Null request:** Malformed parsing, unsupported methods, invalid `Content-Length`, or empty connections all return `null`. No HTTP response is sent — the `finally` block closes the socket.

#### Filter, Route, and Static Fallback (Lines 41–61)

```java
            HttpResponse response = new HttpResponse();
            if (filterChain.execute(request, response)) {
                response = router.route(request);
                if (response.getStatus() == HttpStatus.NOT_FOUND &&
                        request.getMethod() == HttpMethod.GET) {

                    String path = request.getPath().equals("/") ? "/index.html" : request.getPath();

                    try {
                        LRUCache.CachedFile file = fileHandler.get(path);

                        if (file != null) {
                            response.setStatus(HttpStatus.OK);
                            response.setBody(file.data, file.contentType);
                        }
                    } catch (Exception e) {
                        System.err.println("File read error: " + e.getMessage());
                        response.setStatus(HttpStatus.INTERNAL_ERROR);  // Path traversal → 500
                    }
                }
            }

            response.send(outputStream);
```

**Filter short-circuit:** When `filterChain.execute()` returns `false` (rate limited), routing is skipped. The `RateLimiter` has already set status 429 and an error body on the shared `response` object. `response.send()` still runs at the end — one send path for all outcomes.

**405 vs 404:** If the router returns 405 (method not allowed), the static file fallback does **not** run — only `NOT_FOUND` triggers fallback.

**Static file errors:** `SecurityException` from path traversal (`..` in path) is caught by the broad `catch (Exception e)` and converted to HTTP 500.

#### Cleanup (Lines 65–77)

```java
        } catch (IOException e) {
            System.err.println("Error processing client: " + e.getMessage());
        } finally {
            try {
                if (client != null && !client.isClosed()) {
                    client.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }
```

**`finally` guarantees cleanup:** SocketTimeoutException (Slowloris), client disconnect, or write errors all lead to socket closure. Prevents file descriptor leaks.

---

## Memory Footprint Per Connection

```
Socket:                       OS file descriptor + kernel buffers (~8 KB send + ~8 KB recv)
InputStream wrapper:          ~32 bytes
OutputStream wrapper:         ~32 bytes
HttpRequest:                  ~200 bytes (fields) + body.length
HttpResponse:                 ~200 bytes (fields) + body.length
String[] from path.split():   ~200 bytes (for typical paths)
RequestProcessor object:      ~48 bytes (4 reference fields + header)

Approximate total per connection: ~16.5 KB + request body + response body

For a typical API response (1 KB body): ~18 KB per connection
For serving tech.jpg (10 MB): ~10 MB per connection (dominated by file bytes)
```

With 100 concurrent connections serving API responses: **~1.8 MB** of heap (trivial).
With 100 concurrent connections serving 10MB images: **~1 GB** of heap (significant).

---

## Key Design Notes

- **One request per connection:** `run()` handles exactly one request and then closes the socket. No HTTP keep-alive or connection reuse.
- **Fallback is GET-only + 404-only:** Static file serving triggers only when the router returns 404. POST to a non-existent route returns 404; wrong-method requests return 405 without fallback.
- **Root path hardcoded:** `/` → `/index.html` rewrite is inline. No configurable default document.
- **Single response send:** Both successful and rate-limited responses go through one `response.send()` call at the end.
- **Shared dependencies are thread-safe:** Router (read-only), FilterChain (read-only), StaticFileHandler (synchronized cache), RateLimiter (per-bucket locking).
