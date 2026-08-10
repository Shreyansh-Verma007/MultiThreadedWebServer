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
package com.Shreyansh.webserver.http;                          // Line 1: Package declaration
```

```java
public enum HttpStatus {                                       // Line 3: Enum declaration
    OK(200, "Ok"),                                             // Line 4: 200 OK — Request succeeded
    NOT_FOUND(404, "Not Found"),                               // Line 5: 404 Not Found — No matching route or file
    INTERNAL_ERROR(500, "Internal Server Error"),              // Line 6: 500 ISE — Unexpected server error
    TOO_MANY_REQUESTS(429, "Too Many Requests");               // Line 7: 429 TMR — Rate limit exceeded
```

### Supported Status Codes

| Enum Value | Code | Message | When Used |
|------------|------|---------|-----------|
| `OK` | 200 | "Ok" | Successful API response or static file served |
| `NOT_FOUND` | 404 | "Not Found" | No route matched and no static file found |
| `INTERNAL_ERROR` | 500 | "Internal Server Error" | Exception during request processing, file read error, or reflection invocation failure |
| `TOO_MANY_REQUESTS` | 429 | "Too Many Requests" | Rate limiter threshold exceeded (>100 req/sec per IP) |

### Fields and Constructor (Lines 9–14)

```java
    private final int code;                                    // Line 9: Numeric HTTP status code
    private final String message;                              // Line 10: Human-readable reason phrase

    HttpStatus(int code, String message) {                     // Line 11: Private enum constructor
        this.code = code;                                      // Line 12
        this.message = message;                                // Line 13
    }
```

### Getter Methods (Lines 15–20)

```java
    public int getCode() {                                     // Line 15
        return code;                                           // Line 16: Returns 200, 404, 500, or 429
    }

    public String getMessage() {                               // Line 18
        return message;                                        // Line 19: Returns "Ok", "Not Found", etc.
    }
```

These are used by `HttpResponse.send()` to construct the HTTP status line:
```
HTTP/1.1 200 Ok\r\n
```

---

## Usage

```java
response.setStatus(HttpStatus.OK);               // 200
response.setStatus(HttpStatus.NOT_FOUND);         // 404
response.setStatus(HttpStatus.INTERNAL_ERROR);    // 500
response.setStatus(HttpStatus.TOO_MANY_REQUESTS); // 429
```

---

## Extending

To add a new status code (e.g., `201 Created`):
```java
CREATED(201, "Created"),
```

Then use it: `response.setStatus(HttpStatus.CREATED);`
