# 📄 `HttpRequest.java` — HTTP Request Data Object

**Package:** `com.Shreyansh.webserver.http`  
**Path:** `src/main/java/com/Shreyansh/webserver/http/HttpRequest.java`  
**Role:** Immutable data object representing a parsed HTTP request with method, path, version, headers, body, and client IP.

---

## File Overview

`HttpRequest` is an **immutable data class** that holds all the parsed components of an incoming HTTP request. It's created by `HttpParser.parseRequest()` and passed through the filter chain, router, and ultimately to the controller handler method.

All fields are `final` — once constructed, the request cannot be modified. Headers are wrapped in `Collections.unmodifiableMap()` at construction time, making the object thread-safe by construction as it passes through multiple processing stages.

---

## Line-by-Line Explanation

### Fields (Lines 7–12)

```java
private final HttpMethod httpMethod;                           // Line 7: The HTTP method (GET, POST, etc.)
private final String path;                                     // Line 8: The request path (e.g., "/api/status")
private final Map<String, String> headers;                     // Line 9: Unmodifiable header map
private final String body;                                     // Line 10: The request body (empty string if none)
private final String version;                                  // Line 11: HTTP version (e.g., "HTTP/1.1")
private final String remoteAddr;                               // Line 12: Client's IP address (e.g., "127.0.0.1")
```

### Constructor (Lines 14–21)

```java
public HttpRequest(HttpMethod httpMethod, String path, String version, Map<String, String> headers, String body, String remoteAddr) {
    this.httpMethod = httpMethod;
    this.path = path;
    this.version = version;
    this.headers = Collections.unmodifiableMap(headers);       // Line 18: Defensive copy — truly immutable
    this.body = body;
    this.remoteAddr = remoteAddr;
}
```

The constructor takes all fields as parameters. Called exclusively by `HttpParser.parseRequest()`. The `Collections.unmodifiableMap()` wrapper prevents callers from mutating the header map after construction — a filter or handler cannot accidentally add or remove headers.

### Getter Methods (Lines 23–44)

```java
public HttpMethod getMethod() { return httpMethod; }
public String getPath() { return path; }
public String getBody() { return body; }
public Map<String, String> getHeaders() { return headers; }    // Line 35: Full header access
public String getVersion() { return version; }                 // Line 39: e.g., "HTTP/1.1"
public String getRemoteAddr() { return remoteAddr; }
```

**`getHeaders()`** returns the unmodifiable map — useful for future middleware (authentication, CORS, content negotiation). Currently used primarily by `HttpParser` internally for `Content-Length`; exposed for extensibility.

**`getVersion()`** exposes the HTTP version from the request line. Not currently used by routing or filtering, but available for keep-alive or HTTP/2 migration work.

---

## Usage Throughout the Codebase

| Consumer | Fields Used | Purpose |
|----------|-------------|---------|
| `RequestProcessor` | `getMethod()`, `getPath()` | Logging, static file fallback decision |
| `FilterChain` / `RateLimiter` | `getRemoteAddr()` | Per-IP rate limiting |
| `Router` | `getMethod()`, `getPath()` | Trie lookup and handler selection (query string stripped in Router) |
| Controller methods | `getBody()` | Reading POST data |

---

## Design Notes

- **Truly immutable:** All fields are `final`. Headers are wrapped with `Collections.unmodifiableMap()` — callers cannot mutate the map via `getHeaders()`.
- **Body as String:** The body is stored as a `String` (ISO-8859-1 encoded). This preserves binary data integrity since ISO-8859-1 maps bytes 1:1 to characters. To recover original bytes: `body.getBytes(StandardCharsets.ISO_8859_1)`.
- **Query strings preserved in path:** The `path` field includes query strings (e.g., `/search?q=hello`). `Router.route()` strips everything after `?` before trie lookup. Query parameter parsing is not implemented — handlers do not receive parsed query params.
