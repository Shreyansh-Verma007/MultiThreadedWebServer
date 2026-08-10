# 📄 `RestController.java` — Controller Marker Annotation

**Package:** `com.Shreyansh.webserver.annotations`  
**Path:** `src/main/java/com/Shreyansh/webserver/annotations/RestController.java`  
**Role:** Marker annotation to identify a class as an HTTP controller whose methods should be scanned for route mappings.

---

## File Overview

`@RestController` is a **marker annotation** (an annotation with no attributes) that you place on a class to tell the framework: *"This class contains HTTP endpoint handler methods. Scan it for `@GetMapping` and `@PostMapping` annotations."*

This is inspired by Spring Boot's `@RestController`. During server startup, the `RouteScanner` discovers all classes with this annotation and passes them to `Router.registerController()` for route registration.

---

## Line-by-Line Explanation

```java
package com.Shreyansh.webserver.annotations;                  // Line 1: Package declaration
```

```java
import java.lang.annotation.ElementType;                      // Line 3
import java.lang.annotation.Retention;                         // Line 4
import java.lang.annotation.RetentionPolicy;                   // Line 5
import java.lang.annotation.Target;                            // Line 6
```

```java
@Target(ElementType.TYPE)                                      // Line 8
```
**`@Target(ElementType.TYPE)`** — This annotation can only be applied to **types** (classes, interfaces, enums). This is the key difference from `@GetMapping`/`@PostMapping` which target `METHOD`.

```java
@Retention(RetentionPolicy.RUNTIME)                            // Line 9
```
**Retained at runtime** for reflective discovery by `RouteScanner`.

```java
public @interface RestController {                             // Line 10
}                                                              // Line 11
```
**A marker annotation** — it has no attributes. Its mere presence on a class is enough to signal that the class is a controller.

---

## Usage Example

```java
@RestController                    // ← This annotation marks the class as a controller
public class DemoController {
    @GetMapping("/api/status")
    public HttpResponse getStatus(HttpRequest request) { ... }
}
```

---

## How It's Used Internally

1. **`RouteScanner.processClass()`** loads a class via `Class.forName()`.
2. Checks: `clas.isAnnotationPresent(RestController.class)`.
3. If **true**: instantiates the class via `clas.getDeclaredConstructor().newInstance()` and calls `router.registerController(controller)`.
4. If **false**: the class is skipped — it's not a controller.

---

## Design Note

Without this annotation, the `RouteScanner` would have no way to distinguish controller classes from utility classes, data classes, or other non-controller code in the same package. It acts as an opt-in mechanism.
