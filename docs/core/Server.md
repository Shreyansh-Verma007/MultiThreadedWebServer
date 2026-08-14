# 📄 `Server.java` — Core TCP Server

**Package:** `com.Shreyansh.webserver.core`  
**Path:** `src/main/java/com/Shreyansh/webserver/core/Server.java`  
**Role:** Opens a TCP server socket, accepts incoming connections, and dispatches each connection to a fixed thread pool for concurrent processing.

---

## File Overview

`Server` is the **heart of the application** — the component that bridges the OS kernel's TCP stack to the Java application. It:

1. Opens a `ServerSocket` bound to a port, with a configurable backlog queue
2. Runs an infinite `accept()` loop on the main thread
3. For each accepted connection, sets a read timeout (Slowloris defense) and submits a `RequestProcessor` to the thread pool

This is what makes the server **multithreaded** — the main thread only accepts connections; all HTTP processing happens concurrently in pool worker threads.

---

## Threading Model Explained

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              MAIN THREAD                                    │
│                                                                              │
│  while(isRunning) {                                                          │
│    Socket client = serverSocket.accept();  ← BLOCKS until connection arrives│
│    client.setSoTimeout(5000);              ← 5s read timeout                │
│    executor.execute(new RequestProcessor(client, ...));  ← SUBMIT to pool   │
│  }                                                                           │
│                                                                              │
│  Time per iteration: ~2-5µs (accept + setSoTimeout + object creation + submit)│
│  Max theoretical throughput: ~200K-500K accepts/second                       │
│  Actual bottleneck: Thread pool saturation, not accept rate                  │
└──────────────────────────────────────────────────────────────────────────────┘
        │                │                │               │
        ▼                ▼                ▼               ▼
┌────────────┐  ┌────────────┐  ┌────────────┐  ┌──────────────┐
│  Thread-1  │  │  Thread-2  │  │  Thread-3  │  │  Thread-100  │
│  proc.run()│  │  proc.run()│  │  proc.run()│  │  proc.run()  │
│  parse     │  │  parse     │  │  parse     │  │  parse       │
│  filter    │  │  filter    │  │  filter    │  │  filter      │
│  route     │  │  route     │  │  route     │  │  route       │
│  respond   │  │  respond   │  │  respond   │  │  respond     │
│  close     │  │  close     │  │  close     │  │  close       │
└────────────┘  └────────────┘  └────────────┘  └──────────────┘
     Fixed Thread Pool: Executors.newFixedThreadPool(100)
     Internal queue: LinkedBlockingQueue (UNBOUNDED)
```

**Key insight:** The main thread and pool threads run completely independently. The main thread never processes HTTP — it only accepts TCP connections. This separation means a slow request being processed on Thread-3 does NOT block the main thread from accepting new connections.

---

## Line-by-Line Explanation

### Fields (Lines 15–21)

```java
private final int port;
private final ExecutorService executor;
private volatile boolean isRunning;                            // Cross-thread visibility for stop()
private final Router router;
private final FilterChain filterChain;
private final StaticFileHandler fileHandler;
private ServerSocket serverSocket;                             // Stored so stop() can close it
```

### Constructor (Lines 23–30)

```java
    public Server(int port, int poolSize, Router router, FilterChain filterChain, StaticFileHandler fileHandler) {
        this.port = port;
        this.fileHandler = fileHandler;
        this.isRunning = true;                                 // Server starts in "running" state
        this.executor = Executors.newFixedThreadPool(poolSize);
        this.router = router;
        this.filterChain = filterChain;
    }
```

**Line 26: `Executors.newFixedThreadPool(poolSize)`**

This creates a `ThreadPoolExecutor` with:
```java
// Equivalent to:
new ThreadPoolExecutor(
    poolSize,                          // corePoolSize: always keep this many threads alive
    poolSize,                          // maximumPoolSize: never create more than this
    0L, TimeUnit.MILLISECONDS,         // keepAliveTime: no idle timeout (core threads are permanent)
    new LinkedBlockingQueue<Runnable>() // workQueue: UNBOUNDED queue — tasks never rejected
);
```

**Memory implications for poolSize=100:**
```
Thread stack memory:  100 threads × 1 MB default stack (java -Xss) = ~100 MB
Thread objects:       100 × ~1 KB each = ~100 KB
Total overhead:       ~100 MB (dominated by stack allocation)
```

**The unbounded `LinkedBlockingQueue`** is both a feature and a risk:
- **Feature:** Tasks are never rejected, even under extreme load — they queue instead
- **Risk:** Under sustained overload (all 100 threads busy, requests arriving faster than completing), the queue grows without bound → `OutOfMemoryError`

### `scanAndStart(String basePackage)` (Lines 31–36)

```java
    public void scanAndStart(String basePackage) {             // Line 31
        System.out.println("Scanning " + basePackage + " for controllers...");
        RouteScanner routeScanner = new RouteScanner(this.router);  // Line 33
        routeScanner.scan(basePackage);                        // Line 34: All route registration happens HERE
        this.start();                                          // Line 35: Accept loop begins
    }
```

**The sequential call order on Lines 34-35 is critical for thread safety.** All trie writes (route registration) complete on Line 34 before `start()` opens the accept loop on Line 35. This establishes a happens-before relationship — all routes are visible to all pool threads that subsequently read the trie. See [Router.md — Thread Safety](../routing/Router.md#thread-safety-of-the-trie) for the full JMM analysis.

### `start()` — The Accept Loop (Lines 38–62)

```java
    public void start() {                                      // Line 38
        try {
            serverSocket = new ServerSocket(this.port, 10000);  // Line 40
            System.out.println("Server started on port " + this.port + "...... ");
```

**Note:** The `ServerSocket` is assigned to the instance field (not a try-with-resources local) so that `stop()` can close it from another thread.

**`new ServerSocket(this.port, 10000)` — Two parameters:**

| Parameter | Value | What It Controls |
|-----------|-------|-----------------|
| `port` | `8080` | TCP port to bind to. The OS reserves this port exclusively for this process. |
| `backlog` | `10000` | Maximum number of pending TCP connections the OS kernel will queue before refusing new ones. |

**Understanding the backlog:**
```
When a client initiates a TCP connection (SYN):
  1. Kernel receives SYN, responds with SYN-ACK
  2. Client responds with ACK → connection is "established" at the OS level
  3. Connection sits in the kernel's "accept queue" until accept() is called
  4. If the accept queue is full (>10,000 pending), kernel drops new SYN packets

The backlog does NOT affect:
  - How many connections can be handled simultaneously (that's the thread pool)
  - How fast connections are processed (that's request processing time)

It DOES affect:
  - How many connections can wait during brief periods when accept() is slower
    than incoming connections (burst absorption)
```

**OS may cap this value.** On Linux, `net.core.somaxconn` limits the backlog (default: 4096). On Windows, the limit is typically 200. Setting 10,000 may be silently reduced by the OS.

```java
            while (this.isRunning) {                           // Line 44
                try {
                    Socket client = serverSocket.accept();     // Line 46: BLOCKS until connection arrives
                    client.setSoTimeout(5000);                 // Line 47: 5-second read timeout
                    RequestProcessor processor = new RequestProcessor(client, this.router, this.filterChain, this.fileHandler);
                    executor.execute(processor);               // Line 49: Submit to thread pool
                } catch (IOException e) {                      // Line 50
                    if (!isRunning) {                          // Line 51: Shutdown in progress
                        break;                                 // Line 52: Exit cleanly
                    }
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
```

**Line 46: `accept()` is a blocking call.** The main thread sits here doing nothing until a TCP connection arrives. There is zero CPU usage during this wait — the thread is parked in the kernel's `poll()`/`epoll()` mechanism.

**Lines 50-54: Shutdown-aware exception handling.** When `stop()` is called, `serverSocket.close()` causes `accept()` to throw an `IOException`. The handler checks `isRunning` to distinguish this expected shutdown exception from a real error.

**Line 47: `setSoTimeout(5000)` — Slowloris defense.**

```
Slowloris attack: Attacker opens a TCP connection but sends data very slowly
  (or not at all), holding the connection open indefinitely.
  Without a timeout, the thread processing this connection is blocked forever.
  With 100 such connections: all 100 threads are stuck → server is dead.

setSoTimeout(5000): If no data is read from the socket within 5 seconds,
  a SocketTimeoutException is thrown → RequestProcessor.run() catches IOException
  → connection is closed in the finally block → thread returns to pool.

This limits the damage of Slowloris to a maximum of 5 seconds per attack connection.
```

Validated by `testSlowlorisTimeout()` in the integration test suite.

**Line 49: `executor.execute(processor)` — Non-blocking submission.**
This call returns immediately. The main thread goes right back to `accept()`. The `RequestProcessor.run()` method will be executed by a pool thread when one becomes available. If all 100 threads are busy, the task enters the `LinkedBlockingQueue` and waits.

```java
        } catch (IOException e) {                              // Line 56
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();                                        // Line 60: Always drain thread pool
        }
    }
```

**Line 60:** The `finally` block ensures `shutdown()` is called regardless of how the loop exits — whether by `stop()`, by exception, or by `isRunning` becoming false.

### `stop()` — Graceful Shutdown Trigger (Lines 68–77)

```java
    public void stop() {                                       // Line 68
        isRunning = false;                                     // Line 69: volatile write — visible to accept loop
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();                          // Line 72: Breaks the blocking accept() call
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }
    }
```

**The shutdown sequence:**
1. `isRunning = false` — volatile write, immediately visible to the accept loop thread
2. `serverSocket.close()` — causes the blocked `accept()` call to throw `IOException`
3. The accept loop catches the exception, sees `!isRunning`, and breaks
4. The `finally` block calls `shutdown()` to drain the thread pool

### `shutdown()` — Thread Pool Drain (Lines 79–88)

```java
    private void shutdown() {                                  // Line 79
        executor.shutdown();                                   // Line 80: No new tasks accepted
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {  // Line 82: Wait for in-flight requests
                executor.shutdownNow();                        // Line 83: Force-kill after 30s
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();                            // Line 85: Force-kill on interrupt
            Thread.currentThread().interrupt();                 // Line 86: Preserve interrupt status
        }
    }
```

**`executor.shutdown()` vs `shutdownNow()`:**
- `shutdown()`: Stops accepting new tasks but lets in-flight tasks complete
- `shutdownNow()`: Interrupts all running threads immediately
- `awaitTermination(30s)`: Gives in-flight requests up to 30 seconds to finish before force-killing
- `Thread.currentThread().interrupt()`: Preserves the interrupt flag so callers can detect the interruption

---

## Backpressure & Overload Behavior

What happens when the server is overwhelmed:

```
Load Level 1: Normal (< 100 concurrent requests)
  accept() → setSoTimeout → execute → thread immediately picks up task
  All threads available. Zero queueing. Lowest latency.

Load Level 2: Thread Pool Saturated (> 100 concurrent requests)
  accept() → setSoTimeout → execute → task enters LinkedBlockingQueue
  TCP connection is accepted (client's SYN-ACK completes), but response is delayed.
  Client sees: connection succeeds, but response hangs until a thread frees up.
  
  ⚠️ The 5-second SoTimeout starts when setSoTimeout() is called (Line 44),
  but the SocketTimeoutException fires during read(), which happens inside
  RequestProcessor.run(). If the task is queued for >5 seconds before a thread
  picks it up, the timeout may fire during parsing — causing the connection
  to appear as a "slow client" even though it was just queued too long.

Load Level 3: Queue Overflow (sustained overload, millions queued)
  LinkedBlockingQueue grows without bound.
  Each queued task holds a reference to a Socket + Router + FilterChain + FileHandler.
  The Socket itself holds an OS file descriptor.
  Eventually: OutOfMemoryError (heap exhaustion) or "too many open files" (FD exhaustion).

Load Level 4: OS Backlog Full (> 10,000 pending connections in kernel)
  Kernel stops responding to new SYN packets.
  New clients see: connection refused (ECONNREFUSED) or timeout.
```

---

## Key Design Notes

- **`volatile boolean isRunning`:** Enables graceful shutdown via `stop()`. The `volatile` keyword ensures cross-thread visibility without requiring `synchronized`. See [Design Tradeoffs — Graceful Shutdown](../DESIGN_TRADEOFFS.md#54-graceful-shutdown--implemented).
- **Backlog of 10,000:** Large value for burst absorption. May be OS-capped.
- **5-second timeout:** Balances Slowloris protection with tolerance for slow legitimate clients.
- **Unbounded queue:** `newFixedThreadPool` uses `LinkedBlockingQueue` with no cap. A bounded queue with a rejection policy would be safer for production.
- **30-second shutdown drain:** `awaitTermination(30s)` gives in-flight requests time to complete. After 30s, `shutdownNow()` force-interrupts remaining threads.
