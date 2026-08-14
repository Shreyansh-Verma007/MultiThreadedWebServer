# 📄 `HttpStatus.java` — HTTP Status Code Enum

**Package:** `com.Shreyansh.webserver.http`  
**Path:** `src/main/java/com/Shreyansh/webserver/http/HttpStatus.java`  
**Role:** Enum representing HTTP response status codes with their numeric codes and reason phrases.

---

## File Overview

`HttpStatus` is a Java enum that maps meaningful names to HTTP status codes and their standard reason phrases. It's used throughout the application to set response status codes in a type-safe manner.

---

## Line-by-Line Explanation

```java
public enum HttpStatus {
    OK(200, "Ok"),
    BAD_REQUEST(400, "Bad Request"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    TOO_MANY_REQUESTS(429, "Too Many Requests"),
    INTERNAL_ERROR(500, "Internal Server Error");
```

### Supported Status Codes

| Enum Value | Code | Message | When Used |
|------------|------|---------|-----------|
| `OK` | 200 | "Ok" | Successful API response or static file served |
| `BAD_REQUEST` | 400 | "Bad Request" | Malformed client request (reserved for future parser-level responses) |
| `NOT_FOUND` | 404 | "Not Found" | No matching route and no static file found |
| `METHOD_NOT_ALLOWED` | 405 | "Method Not Allowed" | Path exists in trie but no handler registered for the requested HTTP method |
| `TOO_MANY_REQUESTS` | 429 | "Too Many Requests" | Rate limiter threshold exceeded (>100 req/sec per IP by default) |
| `INTERNAL_ERROR` | 500 | "Internal Server Error" | Exception during request processing, file read error, or reflection invocation failure |

### Fields and Constructor (Lines 11–17)

```java
    private final int code;
    private final String message;

    HttpStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }
```

### Getter Methods (Lines 19–25)

```java
    public int getCode() { return code; }
    public String getMessage() { return message; }
```

These are used by `HttpResponse.send()` to construct the HTTP status line:
```
HTTP/1.1 200 Ok\r\n
HTTP/1.1 405 Method Not Allowed\r\n
```

---

## Usage

```java
response.setStatus(HttpStatus.OK);                  // 200
response.setStatus(HttpStatus.BAD_REQUEST);         // 400
response.setStatus(HttpStatus.NOT_FOUND);           // 404
response.setStatus(HttpStatus.METHOD_NOT_ALLOWED);  // 405 — Router returns this when path exists but method doesn't match
response.setStatus(HttpStatus.TOO_MANY_REQUESTS);  // 429
response.setStatus(HttpStatus.INTERNAL_ERROR);      // 500
```

**405 in practice:** When a client sends `DELETE /api/status` but only `GET /api/status` is registered, `Router.route()` returns 405 with body `{"error": "Method Not Allowed"}` instead of incorrectly returning 404.

---

## Extending

To add a new status code (e.g., `201 Created`):
```java
CREATED(201, "Created"),
```

Then use it: `response.setStatus(HttpStatus.CREATED);`
