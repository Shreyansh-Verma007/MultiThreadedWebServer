# 📄 `PostMapping.java` — POST Route Annotation

**Package:** `com.Shreyansh.webserver.annotations`  
**Path:** `src/main/java/com/Shreyansh/webserver/annotations/PostMapping.java`  
**Role:** Custom annotation to mark a method as a handler for HTTP POST requests at a specific URL path.

---

## File Overview

`@PostMapping` is a **custom runtime annotation** that you place on a method inside a `@RestController` class to declare that the method should handle HTTP `POST` requests for a particular URL path. It is the POST counterpart to `@GetMapping`.

When the `Router.registerController()` method encounters a method annotated with `@PostMapping`, it registers that method as a route handler for `HttpMethod.POST` at the given path.

---

## Line-by-Line Explanation

```java
package com.Shreyansh.webserver.annotations;                  // Line 1: Package declaration
```

```java
import java.lang.annotation.ElementType;                      // Line 3: Where annotations can be applied
import java.lang.annotation.Retention;                         // Line 4: Controls annotation lifetime
import java.lang.annotation.RetentionPolicy;                   // Line 5: Retention strategy options
import java.lang.annotation.Target;                            // Line 6: Controls annotation target
```

```java
@Target(ElementType.METHOD)                                    // Line 8
```
**Method-level only.** This annotation can only be placed on methods, not on classes or fields.

```java
@Retention(RetentionPolicy.RUNTIME)                            // Line 9
```
**Retained at runtime.** Accessible via Java reflection, which is how the `Router` discovers POST routes.

```java
public @interface PostMapping {                                // Line 10
    String value();                                            // Line 11
}                                                              // Line 12
```
**Defines the annotation** with one required attribute:
- **`value()`** — The URL path this method handles (e.g., `"/api/echo"`).

---

## Usage Example

```java
@RestController
public class MyController {
    @PostMapping("/api/echo")    // ← This annotation
    public HttpResponse handleEcho(HttpRequest request) {
        // handle POST /api/echo
    }
}
```

---

## How It's Used Internally

1. **`Router.registerController()`** checks each method for `@PostMapping`.
2. Extracts the path via `annotation.value()`.
3. Wraps the method in a reflective lambda `RouteHandler`.
4. Registers it with `router.addRoute(HttpMethod.POST, path, handler)`.
