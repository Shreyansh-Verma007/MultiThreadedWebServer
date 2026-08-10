# 📄 `Router.java` — Trie-Based URL Router

**Package:** `com.Shreyansh.webserver.routing`  
**Path:** `src/main/java/com/Shreyansh/webserver/routing/Router.java`  
**Role:** Core URL routing engine using a trie (prefix tree) data structure. Matches incoming requests to registered handlers and supports annotation-based controller registration.

---

## File Overview

The `Router` is the **central routing engine** of the web server. It has two main responsibilities:

1. **Route registration**: Stores URL path → handler mappings in a trie data structure.
2. **Route matching**: Looks up incoming request paths in the trie and invokes the matching handler.

It also provides **controller registration** via reflection — scanning a controller object's methods for `@GetMapping` and `@PostMapping` annotations.

---

## Trie Data Structure

The router uses a **trie (prefix tree)** where each node represents a URL path segment. This enables O(n) route matching where n is the number of path segments.

```
Example routes registered:
  GET /api/status
  GET /api/users
  POST /api/users

Trie structure:
  ROOT
   └── "api"
         ├── "status"  →  handlers: { GET: getStatus() }
         └── "users"   →  handlers: { GET: getUsers(), POST: createUser() }
```

Each `TrieNode` stores a map of `HttpMethod → RouteHandler` at its position. This allows different handlers for different HTTP methods on the same path.

---

## Line-by-Line Explanation

### Field and Constructor (Lines 13–18)

```java
public class Router {                                          // Line 13
    private final TrieNode root;                               // Line 14: Root of the trie (represents "/")

    public Router() {                                          // Line 16
        this.root = new TrieNode();                            // Line 17: Initialize empty trie
    }
```

### `addRoute(HttpMethod, String, RouteHandler)` — Route Registration (Lines 20–34)

```java
    public void addRoute(HttpMethod httpMethod, String path, RouteHandler routeHandler) {  // Line 20
        String[] segments = path.split("/");                   // Line 21: Split "/api/status" → ["", "api", "status"]
        TrieNode currentNode = root;                           // Line 22: Start at root

        for (String segment : segments) {                      // Line 24: Walk the trie
            if (segment.isEmpty()) {                           // Line 25: Skip empty segments (from leading "/")
                continue;                                      // Line 26
            }
            if (!currentNode.getChildren().containsKey(segment)) {  // Line 28: Node doesn't exist?
                currentNode.getChildren().put(segment, new TrieNode());  // Line 29: Create it
            }
            currentNode = currentNode.getChildren().get(segment);  // Line 31: Move to next node
        }
        currentNode.getHandlers().put(httpMethod, routeHandler);  // Line 33: Store handler at the leaf node
    }
```

**Walk-through for `addRoute(GET, "/api/status", handler)`:**
1. Split: `["", "api", "status"]`
2. Skip `""` (empty)
3. `"api"`: Create child node under root, move to it
4. `"status"`: Create child node under "api", move to it
5. Store `{GET: handler}` at the "status" node

### `route(HttpRequest)` — Route Matching (Lines 36–63)

```java
    public HttpResponse route(HttpRequest request) {           // Line 36
        String path = request.getPath();                       // Line 37: e.g., "/api/status"
        HttpMethod httpMethod = request.getMethod();           // Line 38: e.g., GET

        String[] segments = path.split("/");                   // Line 40
        TrieNode currentNode = root;                           // Line 41

        for (String segment : segments) {                      // Line 43
            if (segment.isEmpty()) {                           // Line 44
                continue;                                      // Line 45
            }
            if (!currentNode.getChildren().containsKey(segment)) {  // Line 47: Path segment not found in trie
                HttpResponse httpResponse = new HttpResponse();// Line 48
                httpResponse.setStatus(HttpStatus.NOT_FOUND);  // Line 49
                return httpResponse;                           // Line 50: Return 404
            }
            currentNode = currentNode.getChildren().get(segment);  // Line 52
        }
```

Walk the trie following the path segments. If any segment doesn't match, return 404 immediately.

```java
        if (currentNode.getHandlers().containsKey(httpMethod)) {  // Line 54: Handler exists for this method?
            RouteHandler routeHandler = currentNode.getHandlers().get(httpMethod);  // Line 55
            return routeHandler.handle(request);               // Line 56: Execute the handler!
        }
        else {                                                 // Line 58: Path exists but method not supported
            HttpResponse httpResponse = new HttpResponse();    // Line 59
            httpResponse.setStatus(HttpStatus.NOT_FOUND);      // Line 60
            return httpResponse;                               // Line 61: Return 404 (could be 405 Method Not Allowed)
        }
    }
```

If the path matches but the HTTP method doesn't (e.g., `POST /api/status` when only `GET` is registered), it returns 404. Note: A more strict implementation could return **405 Method Not Allowed**.

### `registerController(Object)` — Annotation-Based Registration (Lines 65–105)

```java
    public void registerController(Object controller) {        // Line 65
        Class<?> controllerClass = controller.getClass();      // Line 66: Get the class via reflection
        if (!controllerClass.isAnnotationPresent(RestController.class)) { return; }  // Line 67: Skip non-controllers
```

**Line 67**: Double-check that the class has `@RestController`. (The `RouteScanner` already checks this, but this is a safety guard.)

#### Scanning for @GetMapping (Lines 69–86)

```java
        for (Method method : controllerClass.getDeclaredMethods()) {  // Line 69: Iterate all declared methods
            if (method.isAnnotationPresent(GetMapping.class)) {  // Line 70: Has @GetMapping?
                GetMapping annotation = method.getAnnotation(GetMapping.class);  // Line 71: Get the annotation
                String path = annotation.value();              // Line 72: Extract the path (e.g., "/api/status")
                RouteHandler handler = request -> {            // Line 73: Create a lambda handler
                    try {
                        return (HttpResponse) method.invoke(controller, request);  // Line 75: Reflective invocation
                    }
                    catch (Exception e) {                      // Line 77
                        System.err.println("Error executing GET method: " + e.getMessage());  // Line 78
                        HttpResponse errorResponse = new HttpResponse();  // Line 79
                        errorResponse.setStatus(HttpStatus.INTERNAL_ERROR);  // Line 80
                        return errorResponse;                  // Line 81: Return 500 on error
                    }
                };
                this.addRoute(HttpMethod.GET, path, handler);  // Line 84: Register in the trie
                System.out.println("Mapped GET: " + path + " onto " + controllerClass.getSimpleName() + "." + method.getName());  // Line 85
            }
```

**Line 73–83**: Creates a lambda that wraps the controller method. When the route is matched later, the lambda:
1. Invokes `method.invoke(controller, request)` — calling the actual controller method via reflection.
2. Casts the return value to `HttpResponse`.
3. If reflection throws an exception (e.g., the method throws), catches it and returns a 500 error response.

**Line 85**: Logs the route registration, e.g., `Mapped GET: /api/status onto DemoController.getStatus`.

#### Scanning for @PostMapping (Lines 87–103)

```java
            if (method.isAnnotationPresent(PostMapping.class)) {  // Line 87: Has @PostMapping?
                PostMapping annotation = method.getAnnotation(PostMapping.class);  // Line 88
                String path = annotation.value();              // Line 89
                RouteHandler handler = request -> {            // Line 90
                    try {
                        return (HttpResponse) method.invoke(controller, request);  // Line 92
                    }
                    catch (Exception e) {                      // Line 94
                        System.err.println("Error executing POST method: " + e.getMessage());  // Line 95
                        HttpResponse errorResponse = new HttpResponse();  // Line 96
                        errorResponse.setStatus(HttpStatus.INTERNAL_ERROR);  // Line 97
                        return errorResponse;                  // Line 98
                    }
                };
                this.addRoute(HttpMethod.POST, path, handler); // Line 101
                System.out.println("Mapped POST: " + path + " onto " + controllerClass.getSimpleName() + "." + method.getName());  // Line 102
            }
        }
    }
```

Identical logic to `@GetMapping`, but for `@PostMapping` and `HttpMethod.POST`.

---

## Route Matching Example

```
Registered routes:
  GET /api/status → DemoController.getStatus
  POST /api/echo  → TestController.handleEcho

Incoming: GET /api/status

Trie walk:
  root → "api" → "status"
  handlers at "status" node: { GET: handler }
  GET exists → invoke handler → return response

Incoming: DELETE /api/status

Trie walk:
  root → "api" → "status"
  handlers: { GET: handler }
  DELETE not found → return 404

Incoming: GET /api/unknown

Trie walk:
  root → "api" → "unknown" not found → return 404
```

---

## Key Design Notes

- **Trie-based routing**: O(n) where n = number of path segments. Much faster than iterating through a list of routes.
- **No path parameters**: The current implementation doesn't support path parameters like `/users/{id}`. All segments are matched exactly.
- **No wildcard routes**: No support for `*` or `**` patterns.
- **Reflection-based handlers**: Controller methods are called via `method.invoke()`. This adds a small overhead compared to direct method calls but enables the annotation-driven programming model.
- **404 vs 405**: When a path exists but the method doesn't match, the router returns 404 instead of the more correct 405 (Method Not Allowed).
