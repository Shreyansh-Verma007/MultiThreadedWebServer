# 📚 MultithreadedWebServer — Project Documentation

Welcome to the comprehensive documentation for the **MultithreadedWebServer** project. This is a custom-built, multithreaded HTTP web server written in Java 21, featuring annotation-based routing (inspired by Spring Boot), an LRU cache for static files, a middleware/filter pipeline, and a trie-based URL router.

---

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         Main.java                               │
│              (Application entry point & wiring)                 │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Server.java                              │
│         (Listens on port, accepts connections via               │
│          ServerSocket, dispatches to thread pool)               │
└───────────────────────────┬─────────────────────────────────────┘
                            │ each connection
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   RequestProcessor.java                         │
│        (Parses HTTP, runs filters, routes request,              │
│         falls back to static files, sends response)             │
└────────┬──────────────┬──────────────┬──────────────────────────┘
         │              │              │
         ▼              ▼              ▼
   FilterChain      Router       StaticFileHandler
   (Middleware)   (Trie-based)    (LRU Cache-backed)
```

---

## 📂 Project Structure

```
MultithreadedWebServer/
├── build.gradle                      # Gradle build configuration
├── settings.gradle                   # Gradle project settings
├── gradle.properties                 # Gradle properties
├── .gitignore                        # Git ignore rules
├── src/
│   ├── main/
│   │   ├── java/com/Shreyansh/webserver/
│   │   │   ├── Main.java                         # Entry point
│   │   │   ├── annotations/                      # Custom annotations
│   │   │   │   ├── GetMapping.java
│   │   │   │   ├── PostMapping.java
│   │   │   │   └── RestController.java
│   │   │   ├── cache/                            # Caching layer
│   │   │   │   ├── LRUCache.java
│   │   │   │   └── StaticFileHandler.java
│   │   │   ├── controllers/                      # User-defined controllers
│   │   │   │   └── DemoController.java
│   │   │   ├── core/                             # Core server engine
│   │   │   │   ├── Server.java
│   │   │   │   ├── RequestProcessor.java
│   │   │   │   └── RouteScanner.java
│   │   │   ├── http/                             # HTTP protocol layer
│   │   │   │   ├── HttpMethod.java
│   │   │   │   ├── HttpParser.java
│   │   │   │   ├── HttpRequest.java
│   │   │   │   ├── HttpResponse.java
│   │   │   │   └── HttpStatus.java
│   │   │   ├── middleware/                       # Middleware / filters
│   │   │   │   ├── Filter.java
│   │   │   │   ├── FilterChain.java
│   │   │   │   └── RateLimiter.java
│   │   │   └── routing/                          # URL routing engine
│   │   │       ├── RouteHandler.java
│   │   │       ├── Router.java
│   │   │       └── TrieNode.java
│   │   └── resources/
│   │       ├── index.html                        # Default landing page
│   │       ├── pc.jpg                            # Static image asset
│   │       └── tech.jpg                          # Static image asset
│   └── test/
│       └── java/com/Shreyansh/webserver/
│           └── ServerIntegrationTest.java        # Integration tests
└── docs/                                         # ← You are here
```

---

## 📖 Documentation Index

| Package | File | Documentation |
|---------|------|---------------|
| *(root)* | `Main.java` | [Main.md](./Main.md) |
| `annotations` | `GetMapping.java` | [annotations/GetMapping.md](./annotations/GetMapping.md) |
| `annotations` | `PostMapping.java` | [annotations/PostMapping.md](./annotations/PostMapping.md) |
| `annotations` | `RestController.java` | [annotations/RestController.md](./annotations/RestController.md) |
| `cache` | `LRUCache.java` | [cache/LRUCache.md](./cache/LRUCache.md) |
| `cache` | `StaticFileHandler.java` | [cache/StaticFileHandler.md](./cache/StaticFileHandler.md) |
| `controllers` | `DemoController.java` | [controllers/DemoController.md](./controllers/DemoController.md) |
| `core` | `Server.java` | [core/Server.md](./core/Server.md) |
| `core` | `RequestProcessor.java` | [core/RequestProcessor.md](./core/RequestProcessor.md) |
| `core` | `RouteScanner.java` | [core/RouteScanner.md](./core/RouteScanner.md) |
| `http` | `HttpMethod.java` | [http/HttpMethod.md](./http/HttpMethod.md) |
| `http` | `HttpParser.java` | [http/HttpParser.md](./http/HttpParser.md) |
| `http` | `HttpRequest.java` | [http/HttpRequest.md](./http/HttpRequest.md) |
| `http` | `HttpResponse.java` | [http/HttpResponse.md](./http/HttpResponse.md) |
| `http` | `HttpStatus.java` | [http/HttpStatus.md](./http/HttpStatus.md) |
| `middleware` | `Filter.java` | [middleware/Filter.md](./middleware/Filter.md) |
| `middleware` | `FilterChain.java` | [middleware/FilterChain.md](./middleware/FilterChain.md) |
| `middleware` | `RateLimiter.java` | [middleware/RateLimiter.md](./middleware/RateLimiter.md) |
| `routing` | `RouteHandler.java` | [routing/RouteHandler.md](./routing/RouteHandler.md) |
| `routing` | `Router.java` | [routing/Router.md](./routing/Router.md) |
| `routing` | `TrieNode.java` | [routing/TrieNode.md](./routing/TrieNode.md) |
| *(build)* | `build.gradle` | [build/build_gradle.md](./build/build_gradle.md) |
| *(resources)* | `index.html` | [resources/index_html.md](./resources/index_html.md) |
| *(test)* | `ServerIntegrationTest.java` | [test/ServerIntegrationTest.md](./test/ServerIntegrationTest.md) |

---

## 🔑 Key Design Decisions

1. **No external frameworks** — The entire HTTP stack is hand-written (parsing, routing, response building).
2. **Annotation-driven routing** — Inspired by Spring Boot: `@RestController`, `@GetMapping`, `@PostMapping` enable declarative route registration via reflection.
3. **Trie-based router** — URL paths are split by `/` and stored in a trie for O(path-length) lookup.
4. **Thread pool** — Uses `java.util.concurrent.ExecutorService` with a fixed thread pool to handle concurrent connections.
5. **LRU Cache** — Static files are cached in a doubly-linked-list + `ConcurrentHashMap` LRU cache for fast repeated access.
6. **Middleware pipeline** — A `FilterChain` runs all registered `Filter` instances (e.g., `RateLimiter`) before routing.
7. **Classpath + filesystem serving** — Static files are resolved from the filesystem first, then from the JAR classpath, enabling both development and production modes.

---

## 🚀 How to Run

```bash
# Build the project
./gradlew build

# Run the server
./gradlew run
# or
java -jar build/libs/MultithreadedWebServer-1.0.0.jar
```

The server starts on **port 8080** by default and serves:
- API routes registered via annotated controllers (e.g., `GET /api/status`)
- Static files from `src/main/resources/` (e.g., `/index.html`, `/pc.jpg`)

---

## 🧪 How to Test

```bash
./gradlew test
```

Integration tests spin up the server on port **8089** and validate:
- Slowloris timeout protection
- GET API responses
- POST binary echo (data integrity)
- Rate limiter enforcement (429 after 100 req/sec)
