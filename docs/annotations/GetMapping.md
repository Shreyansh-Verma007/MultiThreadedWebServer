# 📄 `GetMapping.java` — GET Route Annotation

**Package:** `com.Shreyansh.webserver.annotations`  
**Path:** `src/main/java/com/Shreyansh/webserver/annotations/GetMapping.java`  
**Role:** Custom annotation to mark a method as a handler for HTTP GET requests at a specific URL path.

---

## File Overview

`@GetMapping` is a **custom runtime annotation** that you place on a method inside a `@RestController` class to declare that the method should handle HTTP `GET` requests for a particular URL path. This is directly inspired by Spring Boot's `@GetMapping` annotation.

When the `RouteScanner` discovers a class marked with `@RestController`, it inspects all methods for this annotation and registers them in the `Router`'s trie with `HttpMethod.GET`.

---

## Line-by-Line Explanation

```java
package com.Shreyansh.webserver.annotations;                  // Line 1: Package declaration
```

```java
import java.lang.annotation.ElementType;                      // Line 3: Enum specifying where an annotation can be applied
import java.lang.annotation.Retention;                         // Line 4: Meta-annotation for annotation lifetime
import java.lang.annotation.RetentionPolicy;                   // Line 5: Enum for retention strategies
import java.lang.annotation.Target;                            // Line 6: Meta-annotation for annotation target
```

These are Java's built-in meta-annotation types, used to configure how `@GetMapping` itself behaves.

```java
@Target(ElementType.METHOD)                                    // Line 8
```
**`@Target(ElementType.METHOD)`** — Restricts this annotation so it can **only be placed on methods**, not on classes, fields, or parameters. Attempting `@GetMapping` on a class would cause a compile error.

```java
@Retention(RetentionPolicy.RUNTIME)                            // Line 9
```
**`@Retention(RetentionPolicy.RUNTIME)`** — Ensures this annotation is **retained in the compiled bytecode and accessible via reflection at runtime**. This is critical — the `RouteScanner` uses `method.isAnnotationPresent(GetMapping.class)` at runtime to discover routes. Without `RUNTIME` retention, the annotation would be stripped during compilation.

```java
public @interface GetMapping {                                 // Line 10
    String value();                                            // Line 11
}                                                              // Line 12
```
**Declares the annotation** with a single required attribute:
- **`value()`** — The URL path this method handles (e.g., `"/api/status"`). When used: `@GetMapping("/api/status")`.

Because the attribute is named `value`, Java allows the shorthand syntax `@GetMapping("/path")` instead of `@GetMapping(value = "/path")`.

---

## Usage Example

```java
@RestController
public class DemoController {
    @GetMapping("/api/status")    // ← This annotation
    public HttpResponse getStatus(HttpRequest request) {
        // handle GET /api/status
    }
}
```

---

## How It's Used Internally

1. **`RouteScanner.processClass()`** finds `@RestController` classes.
2. **`Router.registerController()`** iterates over each method in the class.
3. For each method with `@GetMapping`:
   - Extracts the path from `annotation.value()`.
   - Creates a lambda `RouteHandler` that invokes the method via reflection.
   - Calls `router.addRoute(HttpMethod.GET, path, handler)`.
