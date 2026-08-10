# 📄 `Filter.java` — Middleware Filter Interface

**Package:** `com.Shreyansh.webserver.middleware`  
**Path:** `src/main/java/com/Shreyansh/webserver/middleware/Filter.java`  
**Role:** Functional interface defining the contract for middleware filters that intercept HTTP requests before routing.

---

## File Overview

`Filter` is a **functional interface** (annotated with `@FunctionalInterface`) that defines the contract all middleware filters must implement. A filter can inspect and potentially reject an HTTP request before it reaches the router.

Being a `@FunctionalInterface` means it can be implemented as a lambda expression, method reference, or traditional class.

---

## Line-by-Line Explanation

```java
package com.Shreyansh.webserver.middleware;                    // Line 1: Package declaration
```

```java
import com.Shreyansh.webserver.http.HttpRequest;               // Line 3
import com.Shreyansh.webserver.http.HttpResponse;              // Line 4
```

```java
@FunctionalInterface                                           // Line 6: Enforces single abstract method
public interface Filter {                                      // Line 7
    boolean filter(HttpRequest request, HttpResponse response); // Line 8: The single abstract method
}
```

### The `filter` Method Contract

| Parameter | Type | Purpose |
|-----------|------|---------|
| `request` | `HttpRequest` | The incoming request to inspect |
| `response` | `HttpResponse` | A pre-created response object the filter can modify (e.g., set 429 status) |
| **Return** | `boolean` | `true` = allow request to continue; `false` = block request |

**Return semantics:**
- **`true`**: The filter approves the request. The next filter in the chain (or the router) will process it.
- **`false`**: The filter blocks the request. The filter should set the response status and body before returning. The response is sent immediately without routing.

---

## Implementations

Currently there is one implementation:

| Class | Purpose |
|-------|---------|
| `RateLimiter` | Limits requests to 100/sec per IP. Returns `false` (429) when exceeded. |

---

## How to Create a Custom Filter

```java
public class LoggingFilter implements Filter {
    @Override
    public boolean filter(HttpRequest request, HttpResponse response) {
        System.out.println("[LOG] " + request.getMethod() + " " + request.getPath());
        return true;  // Always allow — just logs
    }
}

// Or as a lambda:
Filter logger = (req, res) -> {
    System.out.println("[LOG] " + req.getMethod() + " " + req.getPath());
    return true;
};
```

Register it in `Main.java`:
```java
filterChain.addFilter(new LoggingFilter());
```

---

## Design Notes

- **`@FunctionalInterface`**: The compiler enforces that this interface has exactly one abstract method. This enables lambda usage.
- **Pre-routing**: Filters run **before** the router. They cannot inspect which handler will be called — only the raw request data.
- **Mutable response**: The `response` parameter allows filters to set error responses (status, body, headers) when blocking a request.
