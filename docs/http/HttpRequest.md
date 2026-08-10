# 📄 `HttpRequest.java` — HTTP Request Data Object

**Package:** `com.Shreyansh.webserver.http`  
**Path:** `src/main/java/com/Shreyansh/webserver/http/HttpRequest.java`  
**Role:** Immutable data object representing a parsed HTTP request with method, path, version, headers, body, and client IP.

---

## File Overview

`HttpRequest` is an **immutable data class** that holds all the parsed components of an incoming HTTP request. It's created by `HttpParser.parseRequest()` and passed through the filter chain, router, and ultimately to the controller handler method.

All fields are `final` — once constructed, the request cannot be modified. This ensures thread safety and data integrity as the request passes through multiple processing stages.

---

## Line-by-Line Explanation

### Fields (Lines 6–11)

```java
private final HttpMethod httpMethod;                           // Line 6: The HTTP method (GET, POST, etc.)
private final String path;                                     // Line 7: The request path (e.g., "/api/status")
private final Map<String, String> headers;                     // Line 8: All HTTP headers as key-value pairs
private final String body;                                     // Line 9: The request body (empty string if none)
private final String version;                                  // Line 10: HTTP version (e.g., "HTTP/1.1")
private final String remoteAddr;                               // Line 11: Client's IP address (e.g., "127.0.0.1")
```

### Constructor (Lines 13–20)

```java
public HttpRequest(HttpMethod httpMethod, String path, String version, Map<String, String> headers, String body, String remoteAddr) {  // Line 13
    this.httpMethod = httpMethod;                               // Line 14
    this.path = path;                                          // Line 15
    this.version = version;                                    // Line 16
    this.headers = headers;                                    // Line 17
    this.body = body;                                          // Line 18
    this.remoteAddr = remoteAddr;                              // Line 19
}
```

The constructor takes all fields as parameters. Called exclusively by `HttpParser.parseRequest()`.

### Getter Methods (Lines 22–33)

```java
public HttpMethod getMethod() {                                // Line 22
    return httpMethod;                                         // Line 23: Returns the HTTP method enum
}

public String getPath() {                                      // Line 25
    return path;                                               // Line 26: Returns the URL path
}

public String getBody() {                                      // Line 29
    return body;                                               // Line 30: Returns the request body as a string
}

public String getRemoteAddr() { return remoteAddr; }           // Line 33: Returns the client's IP address
```

**Note**: There is no `getHeaders()` method, and `getVersion()` is also absent. The headers are only used internally by `HttpParser` (for `Content-Length`), and the version is stored but not currently exposed. These could be added as needed.

---

## Usage Throughout the Codebase

| Consumer | Fields Used | Purpose |
|----------|-------------|---------|
| `RequestProcessor` | `getMethod()`, `getPath()` | Logging, static file fallback decision |
| `FilterChain` / `RateLimiter` | `getRemoteAddr()` | Per-IP rate limiting |
| `Router` | `getMethod()`, `getPath()` | Trie lookup and handler selection |
| Controller methods | `getBody()` | Reading POST data |

---

## Design Notes

- **Immutable**: All fields are `final`. The `headers` map reference is final, but the map itself is mutable (`HashMap`). In a stricter implementation, `Collections.unmodifiableMap(headers)` could be used.
- **Body as String**: The body is stored as a `String` (ISO-8859-1 encoded). This preserves binary data integrity since ISO-8859-1 maps bytes 1:1 to characters. To recover original bytes: `body.getBytes(StandardCharsets.ISO_8859_1)`.
- **No query parameter parsing**: The `path` includes query strings (e.g., `/search?q=hello`). Query parameter extraction is not implemented.
