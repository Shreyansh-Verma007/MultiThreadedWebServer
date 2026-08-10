# 📄 `Server.java` — Core TCP Server

**Package:** `com.Shreyansh.webserver.core`  
**Path:** `src/main/java/com/Shreyansh/webserver/core/Server.java`  
**Role:** Opens a TCP server socket, accepts incoming connections, and dispatches each connection to a thread pool for processing.

---

## File Overview

`Server` is the **heart of the application**. It:

1. Opens a `ServerSocket` on the configured port.
2. Runs an infinite loop calling `serverSocket.accept()` to wait for incoming TCP connections.
3. For each accepted connection, wraps it in a `RequestProcessor` (a `Runnable`) and submits it to a fixed-size `ExecutorService` thread pool.

This is what makes the server **multithreaded** — multiple connections are handled concurrently by different threads in the pool.

---

## Line-by-Line Explanation

### Imports (Lines 1–11)

```java
package com.Shreyansh.webserver.core;                          // Line 1

import com.Shreyansh.webserver.cache.StaticFileHandler;        // Line 3: Static file serving
import com.Shreyansh.webserver.middleware.FilterChain;          // Line 4: Middleware pipeline
import com.Shreyansh.webserver.routing.Router;                 // Line 5: URL router

import java.io.IOException;                                    // Line 7
import java.net.ServerSocket;                                  // Line 8: TCP server socket
import java.net.Socket;                                        // Line 9: Individual client connection
import java.util.concurrent.ExecutorService;                   // Line 10: Thread pool interface
import java.util.concurrent.Executors;                         // Line 11: Thread pool factory
```

### Class Declaration and Fields (Lines 13–20)

```java
@SuppressWarnings("ALL")                                       // Line 13: Suppresses all compiler warnings
public class Server {                                          // Line 14
    private final int port;                                    // Line 15: TCP port to listen on
    private final ExecutorService executor;                    // Line 16: Thread pool for concurrent request processing
    private final boolean isRunning;                           // Line 17: Server running flag (always true once started)
    private final Router router;                               // Line 18: The trie-based URL router
    private final FilterChain filterChain;                     // Line 19: The middleware filter chain
    private final StaticFileHandler fileHandler;               // Line 20: The static file handler with LRU cache
```

All fields are `final` — they are set once in the constructor and never changed. The `isRunning` flag is set to `true` in the constructor and never toggled (the server runs until the process is killed).

### Constructor (Lines 22–29)

```java
    public Server(int port, int poolSize, Router router, FilterChain filterChain, StaticFileHandler fileHandler) {  // Line 22
        this.port = port;                                      // Line 23
        this.fileHandler = fileHandler;                        // Line 24
        this.isRunning = true;                                 // Line 25: Server is always running
        this.executor = Executors.newFixedThreadPool(poolSize); // Line 26: Create fixed thread pool
        this.router = router;                                  // Line 27
        this.filterChain = filterChain;                        // Line 28
    }
```

**`Executors.newFixedThreadPool(poolSize)`** creates a thread pool with exactly `poolSize` threads. If all threads are busy, new tasks are queued internally until a thread becomes available. In `Main.java`, this is set to **100 threads**.

### `scanAndStart(String basePackage)` (Lines 31–36)

```java
    public void scanAndStart(String basePackage) {             // Line 31
        System.out.println("Scanning " + basePackage + " for controllers...");  // Line 32
        RouteScanner routeScanner = new RouteScanner(this.router);  // Line 33: Create scanner with our router
        routeScanner.scan(basePackage);                        // Line 34: Scan for @RestController classes
        this.start();                                          // Line 35: Start the accept loop
    }
```

This convenience method combines two operations:
1. **Scan** for controllers: Creates a `RouteScanner` and tells it to scan the given package. The scanner finds `@RestController` classes and registers their annotated methods in the router.
2. **Start** the server: Calls `this.start()` which opens the `ServerSocket` and begins accepting connections.

### `start()` — The Accept Loop (Lines 38–53)

```java
    public void start() {                                      // Line 38
        try (ServerSocket serverSocket = new ServerSocket(this.port, 10000)) {  // Line 39
            System.out.println("Server started on port " + this.port + "...... ");  // Line 40
```

**`new ServerSocket(this.port, 10000)`**:
| Parameter | Value | Meaning |
|-----------|-------|---------|
| `port` | `8080` | TCP port to bind to |
| `backlog` | `10000` | Maximum number of pending connections in the OS queue before new connections are refused |

The `try-with-resources` ensures the socket is closed when the server exits.

```java
            while (this.isRunning) {                           // Line 42: Infinite loop (isRunning is always true)
                Socket client = serverSocket.accept();         // Line 43: BLOCKS until a client connects
                client.setSoTimeout(5000);                     // Line 44: 5-second read timeout per connection
                RequestProcessor processor = new RequestProcessor(client, this.router, this.filterChain, this.fileHandler);  // Line 45
                executor.execute(processor);                   // Line 46: Submit to thread pool
            }
```

**Line 43** — `accept()` is a **blocking call**. The thread sits here waiting until a new TCP connection arrives.

**Line 44** — `setSoTimeout(5000)` sets a **5-second socket read timeout**. If no data is received within 5 seconds, a `SocketTimeoutException` is thrown. This is a **Slowloris attack mitigation** — it prevents a malicious client from holding a connection open indefinitely by sending data very slowly.

**Line 45** — Creates a `RequestProcessor` which is a `Runnable` that will parse the HTTP request, run filters, route the request, and send the response.

**Line 46** — `executor.execute(processor)` submits the processor to the thread pool. It will be picked up by an available thread. If all 100 threads are busy, the task is queued.

```java
        }
        catch (IOException e) {                               // Line 49
            System.err.println("Server error: " + e.getMessage());  // Line 50
            e.printStackTrace();                               // Line 51
        }
    }
```

---

## Threading Model

```
Main Thread                    Thread Pool (100 threads)
    │
    ▼
 accept() ──── blocks ─────┐
    │                       │
 client connects           │
    │                       │
 create RequestProcessor    │
    │                       │
 executor.execute(proc) ───►  Thread-1: proc.run()  → parse → filter → route → respond
    │
 accept() ──── blocks ─────┐
    │                       │
 client connects           │
    │                       │
 executor.execute(proc) ───►  Thread-2: proc.run()  → parse → filter → route → respond
    │
   ...
```

The main thread only accepts connections. All actual HTTP processing happens in the thread pool.

---

## Key Design Notes

- **`isRunning` is never set to `false`**: There's no graceful shutdown mechanism. The server runs until the JVM process is terminated (e.g., `Ctrl+C`, `kill`).
- **Backlog of 10,000**: This is a large backlog, allowing the server to queue many pending connections before refusing new ones.
- **5-second timeout**: Critical for security — prevents connection exhaustion attacks.
- **`@SuppressWarnings("ALL")`**: Suppresses warnings about `isRunning` being effectively always `true`.
