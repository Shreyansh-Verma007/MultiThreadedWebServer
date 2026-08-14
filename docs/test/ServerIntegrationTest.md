# 📄 `ServerIntegrationTest.java` — Integration Test Suite

**Package:** `com.Shreyansh.webserver`  
**Path:** `src/test/java/com/Shreyansh/webserver/ServerIntegrationTest.java`  
**Role:** End-to-end integration tests that spin up the real server and validate HTTP behavior including routing, rate limiting, binary data integrity, and timeout handling.

---

## File Overview

This is the project's **integration test suite** using JUnit 5. Unlike unit tests that test components in isolation, these tests:

1. Start the actual `Server` on port **8089** (to avoid conflicting with the production port 8080).
2. Send real HTTP requests using `HttpURLConnection` and raw `Socket`.
3. Validate responses including status codes, body content, and timing behavior.

---

## Test Infrastructure

### `@BeforeAll setup()` — Server Bootstrap (Lines 34–52)

```java
@BeforeAll
public static void setup() throws Exception {                  // Line 35: Runs once before all tests
    Router router = new Router();
    router.registerController(new TestApiController());        // Line 37: Manually register test controller
    
    FilterChain filterChain = new FilterChain();
    filterChain.addFilter(new RateLimiter());                  // Line 40: Add rate limiter
    LRUCache cache = new LRUCache(50);
    StaticFileHandler fileHandler = new StaticFileHandler(cache);

    server = new Server(8089, 10, router, filterChain, fileHandler);  // Line 45: Port 8089, 10 threads

    serverThread = new Thread(() -> {
        server.scanAndStart("com.Shreyansh.webserver.dummy");  // Line 48: Scan dummy package (nothing found)
    });
    serverThread.start();                                      // Line 50: Start server in background thread
    Thread.sleep(1500);                                        // Line 51: Wait 1.5 seconds for server to boot
}
```

**Key decisions:**
- **Port 8089**: Avoids conflict with the production server on 8080.
- **10 threads**: Smaller pool sufficient for testing.
- **Manual controller registration**: `TestApiController` is registered directly (not via scanning) for reliable, predictable test routes.
- **Dummy package scan**: `"com.Shreyansh.webserver.dummy"` — scans a non-existent package so no production controllers interfere with tests.
- **`Thread.sleep(1500)`**: Gives the server time to bind to the port before tests start.

### `@AfterAll teardown()` — Cleanup (Lines 54–58)

```java
@AfterAll
public static void teardown() {                                // Line 55
    server.stop();                                             // Line 56: Gracefully stop the server socket & thread pool
    serverThread.interrupt();                                  // Line 57: Interrupt the server thread
}
```

Calls `server.stop()` to close the server socket and shut down the thread pool, then interrupts the server thread after all tests complete.

---

## Test Cases

### Test 1: `testSlowlorisTimeout()` — Slowloris Attack Protection (Lines 59–71)

```java
@Test
public void testSlowlorisTimeout() {                           // Line 60
    assertDoesNotThrow(() -> {
        try (Socket socket = new Socket("localhost", 8089)) {  // Line 62: Open raw TCP connection
            long start = System.currentTimeMillis();
            int read = socket.getInputStream().read();         // Line 64: Try to read (server sends nothing)
            long elapsed = System.currentTimeMillis() - start;
            
            assertEquals(-1, read, "Server did not close the slow connection correctly.");  // Line 67
            assertTrue(elapsed >= 4000 && elapsed <= 12000, ...);  // Line 68: Should timeout in ~5 seconds
        }
    });
}
```

**What it tests:** Connects to the server but **sends no data** (simulating a Slowloris attack). The server's `setSoTimeout(5000)` should cause the connection to be closed after ~5 seconds.

**Assertions:**
- `read == -1`: Server closed the connection (EOF).
- `elapsed` between 4–12 seconds: Timeout fired at approximately 5 seconds.

**Why this matters:** Without the timeout, a Slowloris attacker could hold connections open indefinitely, exhausting the thread pool.

### Test 2: `testGetApi()` — GET API Response (Lines 73–85)

```java
@Test
public void testGetApi() throws Exception {                    // Line 74
    URL url = URI.create("http://localhost:8089/api/test").toURL();  // Line 75
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("GET");                               // Line 77
    
    assertEquals(200, con.getResponseCode());                  // Line 79: Status should be 200
    
    try (InputStream in = con.getInputStream()) {
        String responseBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("Hello Test", responseBody);              // Line 83: Body should match
    }
}
```

**What it tests:** A simple `GET /api/test` request to verify the routing, controller invocation, and response serialization all work correctly.

**Assertions:**
- HTTP status 200 OK.
- Response body is exactly `"Hello Test"`.

### Test 3: `testPostBinaryEcho()` — Binary Data Integrity (Lines 87–110)

```java
@Test
public void testPostBinaryEcho() throws Exception {            // Line 88
    URL url = URI.create("http://localhost:8089/api/echo").toURL();
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("POST");
    con.setDoOutput(true);
    con.setRequestProperty("Content-Type", "application/octet-stream");

    byte[] payload = new byte[]{0x00, 0x01, (byte) 0xFF, (byte) 0x8A, 0x00, 0x05, 0x7F};  // Line 96
    con.setRequestProperty("Content-Length", String.valueOf(payload.length));

    try (OutputStream out = con.getOutputStream()) {
        out.write(payload);                                    // Line 100: Send binary data
        out.flush();
    }

    assertEquals(200, con.getResponseCode());                  // Line 104

    try (InputStream in = con.getInputStream()) {
        byte[] responseBytes = in.readAllBytes();
        assertArrayEquals(payload, responseBytes, "Binary data should not be corrupted.");  // Line 108
    }
}
```

**What it tests:** Sends binary data (including null bytes `0x00`, max byte `0xFF`, and other non-text values) via POST and verifies the exact same bytes are echoed back.

**Why this is critical:** This test validates that the `HttpParser` correctly preserves binary data by using **ISO-8859-1** encoding (which maps bytes 1:1 to characters). If UTF-8 were used instead, multi-byte sequences like `0xFF 0x8A` would be corrupted.

**Test payload:**
| Byte | Hex | Significance |
|------|-----|-------------|
| `0x00` | Null | Tests null byte handling |
| `0x01` | SOH | Non-printable character |
| `0xFF` | Max | Maximum byte value |
| `0x8A` | High | High-bit byte (>127) |
| `0x05` | ENQ | Non-printable character |
| `0x7F` | DEL | Boundary value (127) |

### Test 4: `testRateLimiterLimits()` — Rate Limiter Enforcement (Lines 112–128)

```java
@Test
public void testRateLimiterLimits() throws Exception {         // Line 113
    URL url = URI.create("http://localhost:8089/api/test").toURL();
    int responseCode = 200;
    
    for (int i = 0; i < 150; i++) {                            // Line 118: Send 150 requests rapidly
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        responseCode = con.getResponseCode();
        if (responseCode == 429) {                             // Line 122: Got rate limited?
            break;                                             // Line 123: Stop early
        }
    }
    
    assertEquals(429, responseCode, "Rate limiter should trigger 429 Too Many Requests");  // Line 127
}
```

**What it tests:** Sends 150 rapid requests from the same IP. Since the `RateLimiter` allows 100 requests per second per IP, requests beyond 100 should return 429.

**Assertions:**
- At least one of the 150 requests received HTTP 429.

---

## Inner Test Controller (Lines 130–150)

```java
@RestController
public static class TestApiController {                        // Line 131

    @GetMapping("/api/test")
    public HttpResponse handleGet(HttpRequest request) {       // Line 133
        HttpResponse response = new HttpResponse();
        response.setStatus(HttpStatus.OK);
        response.setBody("Hello Test");                        // Line 136: Simple text response
        return response;
    }

    @PostMapping("/api/echo")
    public HttpResponse handleEcho(HttpRequest request) {      // Line 141
        HttpResponse response = new HttpResponse();
        response.setStatus(HttpStatus.OK);
        
        byte[] rawBytes = request.getBody().getBytes(StandardCharsets.ISO_8859_1);  // Line 146
        response.setBody(rawBytes, "application/octet-stream");  // Line 147: Echo back raw bytes
        return response;
    }
}
```

**`handleGet`**: Returns a simple `"Hello Test"` response for GET validation.

**`handleEcho`** (Line 146): Converts the request body back to raw bytes using ISO-8859-1 (the same encoding `HttpParser` used to create the string). This round-trip preserves binary data perfectly:
```
Binary bytes → ISO-8859-1 String (parser) → ISO-8859-1 bytes (echo) → identical binary bytes
```

---

## Key Design Notes

- **Real server, real network**: These are true integration tests — they test the full stack over real TCP sockets.
- **Static inner controller**: `TestApiController` is a `static` inner class registered manually, avoiding dependency on classpath scanning.
- **Port isolation**: Port 8089 prevents conflict with a running production instance on 8080.
- **Test order independence**: Each test is independent and can run in any order (though `testRateLimiterLimits` might be affected by preceding tests due to shared rate limiter state).
