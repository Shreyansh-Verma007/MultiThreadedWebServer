# 📚 MultithreadedWebServer — Technical Documentation Suite

> A from-scratch, multithreaded HTTP/1.1 web server framework in pure Java 21.
> No Spring. No Netty. No external dependencies. Raw sockets, hand-rolled parsing, and custom algorithmic routing.

---

## Section 1 — Executive Summary

### What This Project Is

This is a **complete HTTP server framework** built from the ground up using only the Java standard library. It implements the full request lifecycle — from accepting a raw TCP connection on a socket, through hand-parsing HTTP/1.1 byte streams, to routing requests via a trie-based URL engine, and serving responses through a concurrent thread pool.

The architecture is modular and mirrors patterns found in production frameworks like Spring Boot (annotation-driven controller discovery, middleware pipeline, IoC wiring) — but every layer is implemented from scratch to expose the mechanics that enterprise tools abstract away.

### Why It Matters for Interviews & Internships

This project demonstrates mastery of concepts that come up in **every systems/backend interview**:

| Interview Topic | Where It Appears in This Project |
|---|---|
| **Concurrency & Thread Safety** | `ExecutorService` thread pool, `synchronized` LRU cache, `ConcurrentHashMap` + `AtomicInteger` in the rate limiter, JMM-safe immutable request objects |
| **Data Structures & Algorithms** | Trie (prefix tree) for O(K) route resolution, doubly-linked list + HashMap for O(1) LRU cache |
| **Systems Design** | Blocking I/O threading model, backpressure via bounded thread pool + OS backlog queue, fixed-window rate limiting |
| **Network Programming** | Raw TCP socket handling, HTTP/1.1 protocol parsing from byte streams, CRLF line termination, `Content-Length` body framing |
| **Design Patterns** | Chain of Responsibility (middleware), Strategy (functional interface handlers), Inversion of Control (annotation scanning), Builder (response construction) |
| **Java Reflection & Metaprogramming** | Runtime annotation processing, classpath scanning (filesystem + JAR), dynamic method invocation |
| **Security** | Path traversal prevention, Slowloris timeout defense, per-IP rate limiting, parser input bounds (8 KB lines, 10 MB body), static file size cap (50 MB) |

---

## Section 2 — Senior-Level Architecture Breakdown

### 2.1 System Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              STARTUP PHASE                                  │
│                                                                              │
│  Main.main()                                                                │
│    │                                                                        │
│    ├── new Router()                    ← Empty trie (just root node)        │
│    ├── new FilterChain()                                                    │
│    │     └── addFilter(new RateLimiter()) ← ConcurrentHashMap<IP, Bucket>  │
│    ├── new LRUCache(50)                ← HEAD ←→ TAIL (sentinel nodes)     │
│    ├── new StaticFileHandler(cache)    ← Injected LRU cache reference      │
│    │                                                                        │
│    └── new Server(8080, 100, router, filterChain, fileHandler)              │
│          └── scanAndStart("com.Shreyansh.webserver")                        │
│                │                                                            │
│                ├── RouteScanner.scan()                                       │
│                │     ├── ClassLoader.getResource() → file:// or jar://      │
│                │     ├── Recursive directory walk / JAR entry enumeration    │
│                │     ├── Class.forName() on each .class file                │
│                │     ├── Check @RestController annotation                    │
│                │     └── Router.registerController()                         │
│                │           ├── Reflect over declared methods                 │
│                │           ├── Find @GetMapping / @PostMapping              │
│                │           ├── Wrap method in lambda RouteHandler            │
│                │           └── addRoute() → insert into trie                │
│                │                                                            │
│                └── Server.start()                                           │
│                      └── new ServerSocket(8080, backlog=10000)              │
│                            └── ENTER ACCEPT LOOP                            │
└──────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│                           RUNTIME PHASE (per connection)                     │
│                                                                              │
│  Main Thread (blocked on accept)                                            │
│    │                                                                        │
│    ├── serverSocket.accept() ──────→ Socket (client TCP connection)          │
│    ├── client.setSoTimeout(5000)   ← Slowloris defense                      │
│    ├── new RequestProcessor(client, router, filterChain, fileHandler)        │
│    └── executor.execute(processor) ← Submit to ThreadPool[100]              │
│                                                                              │
│  Thread Pool Worker (1 of 100)                                              │
│    │                                                                        │
│    ├── [1] PARSE ── HttpParser.parseRequest(inputStream, clientIp)           │
│    │     ├── readLine() → "GET /api/status HTTP/1.1"                        │
│    │     ├── Validate request line (3 parts), method, Content-Length bounds   │
│    │     ├── Loop readLine() → headers HashMap (max 8 KB per line)          │
│    │     ├── Content-Length? → read(byte[], offset, remaining) loop          │
│    │     └── Return: HttpRequest (immutable, unmodifiable headers)          │
│    │                                                                        │
│    ├── [2] FILTER ── filterChain.execute(request, response)                  │
│    │     └── RateLimiter.filter()                                           │
│    │           ├── GC check: ipBuckets.size() > 10000? → purge stale        │
│    │           ├── computeIfAbsent(ip) → Bucket                             │
│    │           ├── synchronized(bucket): window reset if >1s elapsed         │
│    │           └── count.incrementAndGet() > 100? → 429 + return false      │
│    │                                                                        │
│    ├── [3] ROUTE ── router.route(request)                                    │
│    │     ├── Strip query string at '?' before trie walk                      │
│    │     ├── Split path by "/"                                              │
│    │     ├── Walk trie: root → "api" → "status"                             │
│    │     ├── Lookup handlers.get(GET) at leaf node                          │
│    │     ├── Wrong method on existing path? → 405 Method Not Allowed         │
│    │     └── handler.handle(request) → method.invoke(controller, request)   │
│    │                                                                        │
│    ├── [4] FALLBACK (only if route returned 404 AND method is GET)           │
│    │     ├── Rewrite "/" → "/index.html"                                    │
│    │     └── StaticFileHandler.get(path)                                    │
│    │           ├── LRU cache hit? → return immediately                      │
│    │           ├── Security: reject ".." paths                              │
│    │           ├── Filesystem: Files.readAllBytes(resolvedPath)             │
│    │           ├── JAR classpath: getResourceAsStream(relativePath)          │
│    │           └── Cache miss → read → cache.put() → return                 │
│    │                                                                        │
│    ├── [5] RESPOND ── response.send(outputStream)                            │
│    │     ├── Write: "HTTP/1.1 200 Ok\r\n"                                  │
│    │     ├── Write: each header as "Key: Value\r\n"                         │
│    │     ├── Write: "\r\n" (header/body separator)                          │
│    │     ├── Write: body byte[]                                             │
│    │     └── flush()                                                        │
│    │                                                                        │
│    └── [6] CLEANUP ── finally { client.close() }                             │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Component Dependency Graph

```
Main.java (composition root — manual DI wiring)
  │
  ├── Router ──────────────────┐
  │     ├── TrieNode           │ Routing subsystem
  │     └── RouteHandler       │ (no external dependencies)
  │                            │
  ├── FilterChain ─────────────┤
  │     ├── Filter (interface) │ Middleware subsystem
  │     └── RateLimiter        │ (depends on: http package)
  │                            │
  ├── LRUCache ────────────────┤
  │     └── StaticFileHandler  │ Caching subsystem
  │                            │ (depends on: LRUCache)
  │                            │
  └── Server ──────────────────┘
        ├── RouteScanner         Core subsystem
        └── RequestProcessor     (depends on: ALL subsystems)

  Annotations: @RestController, @GetMapping, @PostMapping
  HTTP Models: HttpRequest, HttpResponse, HttpMethod, HttpStatus, HttpParser
```

**Key observation:** The dependency flow is strictly one-directional. The `http` package is the foundational layer — everything depends on it, but it depends on nothing. The `routing`, `middleware`, and `cache` packages depend on `http` but not on each other. Only `core` (specifically `RequestProcessor`) touches all subsystems. This is clean layered architecture.

### 2.3 End-to-End Pipeline Trace — Data Transformations

Here is exactly what happens to data at each stage when a client sends `GET /api/status HTTP/1.1`:

#### Stage 1: TCP Accept → Socket

```
OS Kernel:  SYN → SYN-ACK → ACK (TCP three-way handshake)
JVM:        serverSocket.accept() returns Socket object
            Socket.getInputStream()  → InputStream (raw TCP byte stream)
            Socket.getOutputStream() → OutputStream (raw TCP byte stream)
            Socket.setSoTimeout(5000) → read() will throw SocketTimeoutException after 5s
```

**Data type:** `java.net.Socket` — wraps an OS file descriptor for a TCP connection.

#### Stage 2: InputStream → HttpRequest

```
Raw bytes on the wire (what the client sent):
  47 45 54 20 2F 61 70 69 2F 73 74 61 74 75 73 20   "GET /api/status "
  48 54 54 50 2F 31 2E 31 0D 0A                       "HTTP/1.1\r\n"
  48 6F 73 74 3A 20 6C 6F 63 61 6C 68 6F 73 74 0D 0A "Host: localhost\r\n"
  0D 0A                                                "\r\n" (end of headers)

HttpParser.readLine():
  Reads byte-by-byte. On \r, peeks next byte. If \n → line complete.
  Returns: "GET /api/status HTTP/1.1"

HttpParser.parseRequest():
  firstLine.split(" ") → ["GET", "/api/status", "HTTP/1.1"]
  HttpMethod.valueOf("GET") → HttpMethod.GET  (enum lookup — O(1) via HashMap internally)
  Header loop → HashMap<String,String> { "Host" → "localhost" }
  No Content-Length → body = "" (empty string)

  Output → new HttpRequest(GET, "/api/status", "HTTP/1.1", headers, "", "127.0.0.1")
```

**Data type transformation:** `InputStream` (raw bytes) → `HttpRequest` (structured immutable POJO)

#### Stage 3: HttpRequest → FilterChain decision

```
filterChain.execute(request, response):
  for each Filter in [RateLimiter]:

  RateLimiter.filter(request, response):
    request.getRemoteAddr() → "127.0.0.1"
    ipBuckets.computeIfAbsent("127.0.0.1", Bucket::new) → Bucket{count=0, lastReset=t}
    synchronized(bucket):
      currentTimeMillis() - lastReset > 1000? → maybe reset count to 0
    bucket.count.incrementAndGet() → 1
    1 > 100? → false → return true (allow)

  Output → true (request passes all filters)
```

**Data type transformation:** `HttpRequest` → `boolean` (pass/fail gate)

#### Stage 4: HttpRequest → Router → HttpResponse

```
router.route(request):
  path = "/api/status"
  path.split("/") → ["", "api", "status"]

  Trie walk:
    root.children.get("api")    → TrieNode@0x1a  (HashMap lookup — O(1) amortized)
    node.children.get("status") → TrieNode@0x2b  (HashMap lookup — O(1) amortized)

  TrieNode@0x2b.handlers.get(GET) → RouteHandler (lambda)

  handler.handle(request):
    This lambda was created during registration:
      request -> method.invoke(controller, request)

    method = DemoController.class.getDeclaredMethod("getStatus")
    controller = DemoController instance (created by RouteScanner via newInstance())
    method.invoke(controller, request) → calls DemoController.getStatus(request)

    Inside getStatus():
      new HttpResponse()           → status=200, headers={}, body=byte[0]
      addHeaders("Content-Type", "application/json")
      setBody(jsonString)           → body = jsonString.getBytes(), headers += Content-Length

  Output → HttpResponse{status=200, headers={Content-Type, Content-Length}, body=byte[76]}
```

**Data type transformation:** `HttpRequest` → `HttpResponse` (via reflection invocation of controller method)

#### Stage 5: HttpResponse → OutputStream (raw bytes)

```
response.send(outputStream):
  Write: "HTTP/1.1 200 Ok\r\n"                              (17 bytes)
  Write: "Content-Type: application/json\r\n"                (32 bytes)
  Write: "Content-Length: 76\r\n"                            (20 bytes)
  Write: "\r\n"                                              (2 bytes — header terminator)
  Write: {"status":"Online","framework":"MultithreadedWebServer v1.0",...}  (76 bytes)
  flush() → pushes all buffered bytes to the TCP send buffer

Total bytes written: ~147 bytes
```

**Data type transformation:** `HttpResponse` (structured object) → raw bytes on the TCP stream

#### Stage 6: Socket Close

```
finally block:
  client.isClosed()? → false
  client.close() → sends TCP FIN to the client, releases the file descriptor
  Thread returns to the pool, ready for the next connection
```

**Connection lifecycle:** One Socket → one HttpRequest → one HttpResponse → close. This is **HTTP/1.0-style** behavior (no keep-alive, no connection reuse).

---

## 📂 Project Structure

```
MultithreadedWebServer/
├── src/main/java/com/Shreyansh/webserver/
│   ├── Main.java                        # Composition root — manual DI wiring
│   ├── annotations/                     # Metaprogramming: custom runtime annotations
│   │   ├── GetMapping.java              # @Target(METHOD), @Retention(RUNTIME)
│   │   ├── PostMapping.java             # Same contract, different HttpMethod
│   │   └── RestController.java          # @Target(TYPE) — class-level marker
│   ├── cache/                           # Caching subsystem
│   │   ├── LRUCache.java                # Doubly-linked list + HashMap, O(1) all ops, synchronized
│   │   └── StaticFileHandler.java       # 3-tier resolver: cache → filesystem → JAR classpath
│   ├── controllers/                     # User-defined HTTP endpoint handlers
│   │   └── DemoController.java          # Example: GET /api/status → JSON response
│   ├── core/                            # Server engine
│   │   ├── RequestProcessor.java        # Runnable — full request lifecycle per connection
│   │   ├── RouteScanner.java            # Classpath scanner — filesystem + JAR dual-mode
│   │   └── Server.java                  # ServerSocket accept loop + FixedThreadPool(100)
│   ├── http/                            # Protocol layer (foundational — no internal deps)
│   │   ├── HttpMethod.java              # Enum: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
│   │   ├── HttpParser.java              # Hand-rolled HTTP/1.1 parser — byte-by-byte readLine
│   │   ├── HttpRequest.java             # Truly immutable request POJO (unmodifiable headers)
│   │   ├── HttpResponse.java            # Mutable response builder with send(OutputStream)
│   │   └── HttpStatus.java              # Enum: 200, 400, 404, 405, 429, 500
│   ├── middleware/                       # Chain of Responsibility pattern
│   │   ├── Filter.java                  # @FunctionalInterface — boolean filter(req, res)
│   │   ├── FilterChain.java             # Sequential executor with short-circuit
│   │   └── RateLimiter.java             # Fixed-window counter: configurable req/sec/IP
│   └── routing/                         # Trie-based URL engine
│       ├── RouteHandler.java            # @FunctionalInterface — HttpResponse handle(req)
│       ├── Router.java                  # Trie CRUD + reflection-based controller registration
│       └── TrieNode.java                # Node: Map<String,TrieNode> children + Map<Method,Handler>
├── src/main/resources/                  # Static assets (bundled into JAR)
│   ├── index.html                       # Default landing page (/ → /index.html rewrite)
│   ├── pc.jpg                           # Static image (~925 KB)
│   └── tech.jpg                         # Static image (~10 MB)
├── src/test/java/.../
│   └── ServerIntegrationTest.java       # 4 tests: Slowloris, GET, POST binary echo, rate limiter
├── build.gradle                         # Java 21 toolchain, JUnit 5, fat JAR with Main-Class
└── docs/                                # ← You are here
```

---

## 📖 Documentation Index

### Per-File Technical Reference (Line-by-Line)

| Package | File | Documentation | Key Concepts |
|---------|------|---------------|-------------|
| *(root)* | `Main.java` | [Main.md](./Main.md) | Manual DI, composition root, startup sequence |
| `annotations` | `GetMapping.java` | [GetMapping.md](./annotations/GetMapping.md) | `@Retention(RUNTIME)`, `@Target(METHOD)`, meta-annotations |
| `annotations` | `PostMapping.java` | [PostMapping.md](./annotations/PostMapping.md) | Same meta-annotation contract as GetMapping |
| `annotations` | `RestController.java` | [RestController.md](./annotations/RestController.md) | `@Target(TYPE)`, marker annotation, opt-in discovery |
| `cache` | `LRUCache.java` | [LRUCache.md](./cache/LRUCache.md) | Doubly-linked list + HashMap, sentinel nodes, O(1) eviction |
| `cache` | `StaticFileHandler.java` | [StaticFileHandler.md](./cache/StaticFileHandler.md) | 3-tier resolution, path traversal defense, MIME detection |
| `controllers` | `DemoController.java` | [DemoController.md](./controllers/DemoController.md) | Handler method contract, JSON response construction |
| `core` | `Server.java` | [Server.md](./core/Server.md) | `ServerSocket`, `ExecutorService`, accept loop, Slowloris defense |
| `core` | `RequestProcessor.java` | [RequestProcessor.md](./core/RequestProcessor.md) | Request lifecycle, static file fallback, socket cleanup |
| `core` | `RouteScanner.java` | [RouteScanner.md](./core/RouteScanner.md) | `Class.forName()`, `newInstance()`, filesystem + JAR scanning |
| `http` | `HttpMethod.java` | [HttpMethod.md](./http/HttpMethod.md) | Enum type safety, `valueOf()` parsing |
| `http` | `HttpParser.java` | [HttpParser.md](./http/HttpParser.md) | Byte-by-byte parsing, CRLF handling, input validation, ISO-8859-1 body encoding |
| `http` | `HttpRequest.java` | [HttpRequest.md](./http/HttpRequest.md) | Immutable POJO, unmodifiable headers, getHeaders()/getVersion() |
| `http` | `HttpResponse.java` | [HttpResponse.md](./http/HttpResponse.md) | Mutable builder, `send()` serialization, auto Content-Length |
| `http` | `HttpStatus.java` | [HttpStatus.md](./http/HttpStatus.md) | Enum: 200, 400, 404, 405, 429, 500 |
| `middleware` | `Filter.java` | [Filter.md](./middleware/Filter.md) | `@FunctionalInterface`, boolean gate contract |
| `middleware` | `FilterChain.java` | [FilterChain.md](./middleware/FilterChain.md) | Sequential execution, short-circuit on `false` |
| `middleware` | `RateLimiter.java` | [RateLimiter.md](./middleware/RateLimiter.md) | Fixed-window counter, per-IP tracking, GC sweep |
| `routing` | `RouteHandler.java` | [RouteHandler.md](./routing/RouteHandler.md) | `@FunctionalInterface`, lambda-wrapped reflection |
| `routing` | `Router.java` | [Router.md](./routing/Router.md) | Trie insert/lookup, annotation-based registration |
| `routing` | `TrieNode.java` | [TrieNode.md](./routing/TrieNode.md) | HashMap children, per-method handler map |

### Deep-Dive Documents

| Document | Content |
|----------|---------|
| [Architecture Deep Dive](./ARCHITECTURE_DEEP_DIVE.md) | Algorithmic complexity, data structure rationale, edge cases, failure modes, thread safety proofs |
| [Design Tradeoffs](./DESIGN_TRADEOFFS.md) | Blocking I/O vs NIO, thread pool sizing, HTTP/1.0 decision, configuration matrix, known limitations |
| [Build Configuration](./build/build_gradle.md) | Gradle setup, Java 21 toolchain, JUnit 5, JAR packaging |
| [Integration Tests](./test/ServerIntegrationTest.md) | Slowloris, GET/POST validation, binary echo, rate limiter enforcement |

---

## 🔑 Key Design Decisions (Summary)

| Decision | Choice Made | Why |
|----------|------------|-----|
| No external frameworks | Pure Java 21 stdlib | Educational: exposes every layer. Zero dependency surface. |
| Blocking I/O (`ServerSocket`) | Over `java.nio` / `Selector` | Simpler mental model. Sufficient for learning concurrency. NIO is on the roadmap. |
| Fixed thread pool (100) | `Executors.newFixedThreadPool` | Bounded memory. Prevents OOM under load. Backpressure via task queue. |
| Trie for routing | Over `HashMap<String, Handler>` | Supports hierarchical paths. O(K) lookup where K = path depth (not total routes). |
| LRU cache (doubly-linked list + HashMap) | Over `LinkedHashMap` | Hand-built for interview demonstration. O(1) get/put/evict. Thread-safe via `synchronized`. Uses `HashMap` (not `ConcurrentHashMap`) since all access is serialized. |
| Fixed-window rate limiter | Over sliding window / token bucket | Simplest correct implementation. Fully configurable via constructor (limit, window, GC threshold). Known boundary-burst tradeoff documented. |
| Annotation-based routing | `@RestController` + `@GetMapping` + `@PostMapping` | Spring-inspired DX. Demonstrates reflection, metaprogramming, and IoC. |
| HTTP/1.0-style connections | No keep-alive | Simplifies connection lifecycle. One request per socket. |
| ISO-8859-1 body encoding | Over UTF-8 | 1:1 byte mapping preserves binary data integrity (validated by integration test). |
| Manual DI (no framework) | Constructor injection in `Main.java` | Keeps project lightweight. All wiring visible in one place. |
| Graceful shutdown | `volatile isRunning` + `stop()` + `awaitTermination(30s)` | Clean teardown for tests and production; breaks accept loop via socket close |

> For the full tradeoff analysis with alternatives considered and rejected, see [Design Tradeoffs](./DESIGN_TRADEOFFS.md).

---

## 🚀 How to Run

### Prerequisites
- Java JDK 21+ (`java -version`)
- Gradle (`gradle -v`) or use the included `./gradlew` wrapper

### Option 1: Run the Pre-built JAR
```bash
# Download from GitHub Actions → server-jar artifact
java -jar MultithreadedWebServer-1.0-SNAPSHOT.jar
```

### Option 2: Build from Source
```bash
git clone https://github.com/Shreyansh-Verma007/MultithreadedWebServer.git
cd MultithreadedWebServer
./gradlew build        # Compile + test + package
./gradlew run          # Or: java -jar build/libs/MultithreadedWebServer-1.0.0.jar
```

### Verify It Works
```bash
# Dynamic API endpoint (annotation-routed, reflection-invoked)
curl http://localhost:8080/api/status
# → {"status": "Online", "framework": "MultithreadedWebServer v1.0", "activeThreads": 12}

# Static file (LRU cache-backed, dual-mode resolution)
curl http://localhost:8080/          # Serves index.html (/ → /index.html rewrite)
curl http://localhost:8080/tech.jpg  # 10MB image, cached after first read
```

## 🧪 How to Test

```bash
./gradlew test
```

The integration test suite (`ServerIntegrationTest.java`) spins up a real server on port **8089** and validates:

| Test | What It Validates | Key Assertion |
|------|-------------------|---------------|
| `testSlowlorisTimeout` | Connection held open with no data is terminated in ~5s | `elapsed >= 4000 && elapsed <= 12000` |
| `testGetApi` | Full stack: accept → parse → route → controller → response | Body == `"Hello Test"`, status == 200 |
| `testPostBinaryEcho` | Binary data integrity through ISO-8859-1 encoding round-trip | `assertArrayEquals(payload, responseBytes)` |
| `testRateLimiterLimits` | 150 rapid requests trigger 429 after 100 | `responseCode == 429` |

---

## 🚧 Roadmap

- **NIO Migration:** Replace blocking `ServerSocket` + `accept()` with `java.nio.channels.Selector` for event-driven I/O
- **HTTP/2 Support:** Frame-based protocol parsing with multiplexed streams
- **Path Parameters:** Support `/users/{id}` dynamic segments in the trie router
- **Connection Keep-Alive:** HTTP/1.1 persistent connections with configurable idle timeout
- ~~**Graceful Shutdown:** Replace `final boolean isRunning` with `volatile` flag and `ServerSocket.close()` interrupt~~ ✅ Implemented

## 📜 License
Copyright (c) 2026 The MultithreadedWebServer Contributors.
