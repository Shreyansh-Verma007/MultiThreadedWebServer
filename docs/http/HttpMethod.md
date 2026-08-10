# 📄 `HttpMethod.java` — HTTP Method Enum

**Package:** `com.Shreyansh.webserver.http`  
**Path:** `src/main/java/com/Shreyansh/webserver/http/HttpMethod.java`  
**Role:** Enum defining all supported HTTP request methods.

---

## File Overview

`HttpMethod` is a simple **Java enum** that enumerates all the HTTP methods (verbs) recognized by the server. It provides type safety — instead of passing method names as raw strings (error-prone), the codebase uses this enum throughout.

---

## Line-by-Line Explanation

```java
package com.Shreyansh.webserver.http;                          // Line 1: Package declaration
```

```java
public enum HttpMethod {                                       // Line 3: Enum declaration
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS                // Line 4: All supported HTTP methods
}
```

### Enum Values

| Value | HTTP Meaning | Used in this project? |
|-------|-------------|----------------------|
| `GET` | Retrieve a resource | ✅ Yes — `@GetMapping`, static files |
| `POST` | Submit data / create resource | ✅ Yes — `@PostMapping` |
| `PUT` | Replace a resource entirely | ❌ Not yet (router supports it, no annotation) |
| `DELETE` | Remove a resource | ❌ Not yet |
| `PATCH` | Partially update a resource | ❌ Not yet |
| `HEAD` | Same as GET but no response body | ❌ Not yet |
| `OPTIONS` | Describe communication options (CORS) | ❌ Not yet |

---

## Usage

The enum is used in three key places:

1. **`HttpParser.parseRequest()`** — Converts the method string from the HTTP request line (e.g., `"GET"`) to the enum via `HttpMethod.valueOf("GET")`.

2. **`Router.addRoute()`** — Routes are keyed by `HttpMethod` + path.

3. **`RequestProcessor.run()`** — Checks `request.getMethod() == HttpMethod.GET` to determine if static file fallback should be attempted.

---

## Adding a New Method

To support a new HTTP method (e.g., `PUT`):
1. The enum already includes `PUT` ✅
2. Create a `@PutMapping` annotation (similar to `@GetMapping`)
3. Add handling in `Router.registerController()` for `@PutMapping`
