# 📄 `Main.java` — Application Entry Point

**Package:** `com.Shreyansh.webserver`  
**Path:** `src/main/java/com/Shreyansh/webserver/Main.java`  
**Role:** Bootstrap the entire web server — wire up all components and start listening for connections.

---

## File Overview

This is the **entry point** of the MultithreadedWebServer application. It contains the `main()` method which is the standard Java program launcher. Its responsibility is to:

1. Create and configure all the core components (router, filters, cache, file handler).
2. Compose them together into a `Server` instance.
3. Kick off the server, which automatically scans for annotated controllers and begins accepting HTTP connections.

---

## Line-by-Line Explanation

```java
package com.Shreyansh.webserver;                              // Line 1: Declares the root package
```

```java
import com.Shreyansh.webserver.cache.LRUCache;                // Line 3: Import the LRU cache implementation
import com.Shreyansh.webserver.cache.StaticFileHandler;        // Line 4: Import the static file serving handler
import com.Shreyansh.webserver.core.Server;                    // Line 5: Import the core TCP server
import com.Shreyansh.webserver.middleware.FilterChain;          // Line 6: Import the middleware pipeline
import com.Shreyansh.webserver.middleware.RateLimiter;          // Line 7: Import the rate-limiting filter
import com.Shreyansh.webserver.routing.Router;                 // Line 8: Import the trie-based URL router
```

These imports pull in every major subsystem of the application. Notice that all components are manually instantiated here — there is no dependency injection framework.

```java
public class Main {                                            // Line 10: The application's main class
    public static void main(String[] args) {                   // Line 11: Standard Java entry point
```

```java
        Router router = new Router();                          // Line 12
```
**Creates the URL router.** The `Router` uses a trie (prefix tree) data structure internally to store path → handler mappings. At this point, the router is empty — routes will be registered later by the `RouteScanner`.

```java
        FilterChain filterChain = new FilterChain();           // Line 13
        filterChain.addFilter(new RateLimiter());              // Line 14
```
**Sets up the middleware pipeline.** A `FilterChain` holds an ordered list of `Filter` objects. Here, only one filter is added — the `RateLimiter`, which limits each IP to 100 requests per second. Additional filters (e.g., authentication, logging, CORS) can be added by calling `addFilter()` again.

```java
        LRUCache cache = new LRUCache(50);                     // Line 15
```
**Creates an LRU (Least Recently Used) cache** with a capacity of **50 entries**. This cache stores the raw bytes and content type of static files. When a file is requested again, it's served from memory instead of re-reading it from disk/classpath.

```java
        StaticFileHandler fileHandler = new StaticFileHandler(cache);  // Line 16
```
**Creates the static file handler**, injecting the LRU cache into it. The `StaticFileHandler` is the component that resolves file paths (from the filesystem or the JAR classpath), reads file bytes, and caches them.

```java
        Server server = new Server(8080, 100, router, filterChain, fileHandler);  // Line 18
```
**Constructs the core server.** Parameters:
| Parameter | Value | Meaning |
|-----------|-------|---------|
| `port` | `8080` | The TCP port the server listens on |
| `poolSize` | `100` | Number of threads in the `ExecutorService` thread pool |
| `router` | `router` | The trie-based URL router |
| `filterChain` | `filterChain` | The middleware pipeline |
| `fileHandler` | `fileHandler` | The static file handler (with LRU cache) |

```java
        server.scanAndStart("com.Shreyansh.webserver");        // Line 19
```
**Scans for controllers and starts the server.** This single call does two things:
1. **Scans** the entire `com.Shreyansh.webserver` package tree (recursively) for classes annotated with `@RestController`, and registers their `@GetMapping`/`@PostMapping` methods as routes in the router.
2. **Starts** the server's accept loop — blocking the main thread while the server listens for incoming connections.

```java
    }                                                          // Line 20
}                                                              // Line 21
```

---

## Component Wiring Diagram

```
Main.main()
  │
  ├── new Router()              ──► Trie-based URL matching
  ├── new FilterChain()
  │     └── addFilter(RateLimiter)  ──► 100 req/sec/IP limit
  ├── new LRUCache(50)          ──► In-memory file cache
  ├── new StaticFileHandler(cache)  ──► File resolver + cacher
  │
  └── new Server(8080, 100, router, filterChain, fileHandler)
        └── scanAndStart("com.Shreyansh.webserver")
              ├── RouteScanner.scan()   ──► Finds @RestController classes
              └── Server.start()        ──► Opens ServerSocket, accepts connections
```

---

## Key Design Notes

- **Manual wiring:** All dependencies are manually composed. This is intentional — it avoids adding a DI framework and keeps the project lightweight.
- **Blocking main thread:** `scanAndStart()` blocks forever (it runs `while(isRunning)` inside). The server runs until the process is killed.
- **Thread pool size of 100:** This means up to 100 requests can be processed simultaneously. Additional connections queue in the `ServerSocket` backlog (configured to 10,000 in `Server.java`).
