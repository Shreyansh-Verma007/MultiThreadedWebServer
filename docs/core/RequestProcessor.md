# 📄 `RequestProcessor.java` — HTTP Request Handler (Per-Connection)

**Package:** `com.Shreyansh.webserver.core`  
**Path:** `src/main/java/com/Shreyansh/webserver/core/RequestProcessor.java`  
**Role:** Handles a single HTTP request — parses it, runs middleware filters, routes it, falls back to static file serving, and sends the response.

---

## File Overview

`RequestProcessor` implements `Runnable` and is the unit of work submitted to the thread pool for each incoming TCP connection. Each instance handles exactly **one HTTP request** on **one socket**. It orchestrates the entire request lifecycle:

```
Raw TCP bytes → Parse HTTP → Run Filters → Route to Handler → (Fallback to Static File) → Send Response
```

---

## Line-by-Line Explanation

### Fields (Lines 13–16)

```java
private final Socket client;                                   // Line 13: The TCP connection to the client
private final Router router;                                   // Line 14: The trie-based URL router
private final FilterChain filterChain;                         // Line 15: The middleware pipeline
private final StaticFileHandler fileHandler;                   // Line 16: The static file handler with LRU cache
```

All fields are `final` — set once in the constructor.

### Constructor (Lines 18–23)

```java
public RequestProcessor(Socket client, Router router, FilterChain filterChain, StaticFileHandler fileHandler) {  // Line 18
    this.client = client;                                      // Line 19
    this.router = router;                                      // Line 20
    this.filterChain = filterChain;                            // Line 21
    this.fileHandler = fileHandler;                            // Line 22
}
```

### `run()` — The Request Lifecycle (Lines 25–78)

This is called by the thread pool when a thread picks up this task.

#### Step 1: Get I/O Streams and Parse HTTP (Lines 27–37)

```java
@Override
public void run() {                                            // Line 26
    try {
        InputStream inputStream = client.getInputStream();     // Line 28: Raw bytes from the client
        OutputStream outputStream = client.getOutputStream();  // Line 29: Raw bytes to the client

        String clientIp = client.getInetAddress().getHostAddress();  // Line 31: Get client's IP address
        if (clientIp == null) clientIp = "0.0.0.0";           // Line 32: Fallback if IP is null
        HttpRequest request = HttpParser.parseRequest(inputStream, clientIp);  // Line 33: Parse raw bytes → HttpRequest

        if (request == null) {                                 // Line 35: Null means empty/invalid request
            return;                                            // Line 36: Silently close connection
        }
```

**Line 33**: `HttpParser.parseRequest()` reads the raw HTTP request from the input stream and constructs an `HttpRequest` object with method, path, headers, and body. Returns `null` for empty or malformed requests.

#### Step 2: Log the Request (Line 39)

```java
        System.out.println("Received: " + request.getMethod() + " " + request.getPath());  // Line 39
```

Prints the HTTP method and path to stdout (e.g., `Received: GET /api/status`).

#### Step 3: Run Middleware Filters (Lines 41–42)

```java
        HttpResponse response = new HttpResponse();            // Line 41: Create a default response (200 OK)
        if (filterChain.execute(request, response)) {          // Line 42: Run all filters
```

**`filterChain.execute()`** runs each filter (currently just `RateLimiter`) in order. Each filter can:
- Return `true` → continue to the next filter / routing.
- Return `false` → short-circuit. The filter sets the response (e.g., 429 Too Many Requests) and the response is sent immediately without routing.

#### Step 4: Route the Request (Lines 43–60)

```java
            response = router.route(request);                  // Line 43: Look up and execute the route handler
            if (response.getStatus() == HttpStatus.NOT_FOUND &&  // Line 44: No route matched?
                    request.getMethod() == HttpMethod.GET) {   // Line 45: And it's a GET request?
```

If the router returns a 404 (no matching route) and the request is a GET, the processor falls back to serving a static file:

```java
                String path = request.getPath().equals("/") ? "/index.html" : request.getPath();  // Line 47

                try {
                    LRUCache.cachedFile file = fileHandler.get(path);  // Line 50: Try to find the static file

                    if (file != null) {                        // Line 52: File found!
                        response.setStatus(HttpStatus.OK);     // Line 53: Change status from 404 → 200
                        response.setBody(file.data, file.contentType);  // Line 54: Set file bytes as body
                    }
                } catch (Exception e) {                        // Line 56
                    System.err.println("File read error: " + e.getMessage());  // Line 57
                    response.setStatus(HttpStatus.INTERNAL_ERROR);  // Line 58: 500 on file read failure
                }
```

**Line 47**: Special case — if the path is `/` (root), rewrite it to `/index.html`. This is the standard behavior for serving a default landing page.

**Line 50**: `fileHandler.get(path)` checks the LRU cache first, then disk, then JAR classpath.

#### Step 5: Send the Response (Line 63)

```java
            response.send(outputStream);                       // Line 63: Write HTTP response bytes to the socket
```

#### Step 6: Error Handling and Cleanup (Lines 64–77)

```java
        }
        catch (IOException e) {                               // Line 65
            System.err.println("Error processing client: " + e.getMessage());  // Line 66
        }
        finally {                                             // Line 68
            try {
                if (client != null && !client.isClosed()) {   // Line 70
                    client.close();                            // Line 71: Always close the socket
                }
            }
            catch (IOException e) {                           // Line 73
                System.err.println("Error closing client socket: " + e.getMessage());  // Line 75
            }
        }
    }
```

The `finally` block ensures the client socket is **always closed**, even if an exception occurs. This prevents resource leaks (file descriptor exhaustion).

---

## Request Processing Flowchart

```
run()
  │
  ├── Parse HTTP request (HttpParser)
  │     └── null? → return (close connection silently)
  │
  ├── Log: "Received: GET /path"
  │
  ├── Run FilterChain
  │     └── Blocked (e.g., rate limited)? → Send 429 response
  │
  ├── Route request (Router)
  │     ├── Route found → Execute handler → Get response
  │     └── 404 + GET? → Try static file
  │           ├── "/" → rewrite to "/index.html"
  │           ├── StaticFileHandler.get(path)
  │           │     ├── Cache hit → Serve from cache
  │           │     ├── Disk hit → Read, cache, serve
  │           │     ├── JAR hit → Read, cache, serve
  │           │     └── Not found → Keep 404
  │           └── File read error → 500
  │
  ├── Send response to client
  │
  └── Close socket (finally)
```

---

## Key Design Notes

- **One request per instance**: Each `RequestProcessor` handles exactly one request, then the socket is closed. This is **HTTP/1.0 behavior** (no keep-alive / connection reuse).
- **Static file fallback**: Only triggers for `GET` requests that don't match any route. POST/PUT/DELETE to non-existent routes always return 404.
- **Root path rewrite**: `/ → /index.html` is hardcoded, similar to how Apache/Nginx handle `DirectoryIndex`.
- **Socket always closed**: The `finally` block guarantees no socket leaks.
