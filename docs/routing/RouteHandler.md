# 📄 `RouteHandler.java` — Route Handler Interface

**Package:** `com.Shreyansh.webserver.routing`  
**Path:** `src/main/java/com/Shreyansh/webserver/routing/RouteHandler.java`  
**Role:** Functional interface defining the contract for HTTP request handler functions.

---

## File Overview

`RouteHandler` is a **functional interface** that represents a function which takes an `HttpRequest` and returns an `HttpResponse`. It is the type stored in the routing trie — when a URL path is matched, the corresponding `RouteHandler` is invoked to produce the response.

---

## Line-by-Line Explanation

```java
package com.Shreyansh.webserver.routing;                       // Line 1: Package declaration
```

```java
import com.Shreyansh.webserver.http.HttpRequest;               // Line 3
import com.Shreyansh.webserver.http.HttpResponse;              // Line 4
```

```java
@FunctionalInterface                                           // Line 6: Enforces single abstract method
public interface RouteHandler {                                // Line 7
    HttpResponse handle(HttpRequest request);                  // Line 8: The handler method
}
```

### The `handle` Method Contract

| Parameter | Type | Purpose |
|-----------|------|---------|
| `request` | `HttpRequest` | The parsed HTTP request |
| **Return** | `HttpResponse` | The response to send back to the client |

---

## How RouteHandlers Are Created

In `Router.registerController()`, controller methods are wrapped in lambda `RouteHandler`s:

```java
// Inside Router.registerController():
RouteHandler handler = request -> {
    try {
        return (HttpResponse) method.invoke(controller, request);  // Reflective call
    } catch (Exception e) {
        HttpResponse errorResponse = new HttpResponse();
        errorResponse.setStatus(HttpStatus.INTERNAL_ERROR);
        return errorResponse;
    }
};
router.addRoute(HttpMethod.GET, path, handler);
```

The lambda captures the `method` (a `java.lang.reflect.Method`) and the `controller` instance, and invokes the controller method via reflection when called.

---

## How RouteHandlers Are Invoked

In `Router.route()`:

```java
RouteHandler routeHandler = currentNode.getHandlers().get(httpMethod);
return routeHandler.handle(request);  // ← invokes the handler
```

---

## Design Notes

- **`@FunctionalInterface`**: Enables lambda usage. The handler is essentially a `Function<HttpRequest, HttpResponse>`.
- **Error handling**: The lambda wrappers in `Router.registerController()` catch reflection exceptions and return 500 responses.
- **Separation of concerns**: `RouteHandler` decouples the routing mechanism from the actual handler implementation. The router doesn't know or care whether the handler is a reflective call to a controller method, a static method, or an inline lambda.
