# 📄 `Router.java` — Trie-Based URL Router

**Package:** `com.Shreyansh.webserver.routing`  
**Path:** `src/main/java/com/Shreyansh/webserver/routing/Router.java`  
**Role:** Core URL routing engine using a trie (prefix tree) data structure. Matches incoming requests to registered handlers and supports annotation-based controller registration via Java Reflection.

---

## File Overview

The `Router` is the **central routing engine** of the web server. It has three responsibilities:

1. **Route registration (`addRoute`):** Inserts a URL path + HTTP method + handler into the trie
2. **Route matching (`route`):** Looks up an incoming request's path in the trie and invokes the matching handler
3. **Controller registration (`registerController`):** Uses Java Reflection to scan a controller object's methods for `@GetMapping`/`@PostMapping` annotations and registers them as routes

---

## Why a Trie? (Data Structure Selection)

### The Alternatives Considered

| Approach | Lookup Time | Path Parameters? | Prefix Sharing? | Memory |
|----------|------------|:-:|:-:|--------|
| `HashMap<String, Handler>` | O(1) amortized* | ❌ Needs regex scan | ❌ Full path per key | Lower for few routes |
| `List<Route>` linear scan | O(N) where N = total routes | ✅ Via regex matching | ❌ | Lowest |
| **Trie (our choice)** | O(K) where K = path segments | ✅ Future support | ✅ Shared prefixes | Moderate |
| Radix tree (compressed trie) | O(K) with better constants | ✅ | ✅ More compact | Lower than trie |

*HashMap lookup is O(1) for the map operation, but O(L) for string hashing where L = path string length. So for `/api/v2/users/profile` (22 chars), the hash computation traverses all 22 characters.

### Why Trie Wins for URL Routing

1. **O(K) is optimal for hierarchical paths.** URLs are inherently hierarchical (`/segment1/segment2/segment3`). A trie naturally represents this hierarchy — each node is a path segment. Lookup traverses exactly K nodes for K segments.

2. **Prefix sharing saves memory.** Routes `/api/users`, `/api/posts`, `/api/comments` share the `api` prefix node. With 50 routes under `/api/v1/*`, the `api` and `v1` nodes are allocated once.

3. **Future path parameters.** The trie can be extended to support `/users/{id}` by treating `{id}` as a wildcard node that matches any segment. This is how Express.js, Spring, and FastAPI implement dynamic routing. A flat HashMap cannot do this without regex scanning all keys.

4. **HTTP method dispatch is built-in.** Each trie node stores a `Map<HttpMethod, RouteHandler>`. This means `GET /users` and `POST /users` share the same node — the method dispatch happens at the leaf, not in the key.

---

## Trie Structure Visualization

For registered routes `GET /api/status`, `GET /api/users`, `POST /api/users`:

```
TrieNode (root)
  └── children: { "api" → TrieNode@1 }

TrieNode@1 (represents "/api")
  ├── children: {
  │     "status" → TrieNode@2,
  │     "users"  → TrieNode@3
  │   }
  └── handlers: { }    ← no handler for GET/POST /api itself

TrieNode@2 (represents "/api/status")
  ├── children: { }    ← leaf node
  └── handlers: { GET → λ(DemoController.getStatus) }

TrieNode@3 (represents "/api/users")
  ├── children: { }    ← leaf node
  └── handlers: { GET → λ(getUsers), POST → λ(createUser) }
```

**Memory per node:** Each `TrieNode` contains two `HashMap` instances:
- `children: HashMap<String, TrieNode>` → ~48 bytes base + ~32 bytes per entry
- `handlers: HashMap<HttpMethod, RouteHandler>` → ~48 bytes base + ~32 bytes per entry
- Object header: ~16 bytes
- **Total per node: ~112-160 bytes** depending on number of children/handlers

For a typical REST API with 50 routes and shared prefixes, the trie has ~60-80 nodes = **~10-12 KB**. Negligible.

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

The root node represents the `/` path. It has no handlers by default (unless someone registers a handler for `/`).

### `addRoute(HttpMethod, String, RouteHandler)` — O(K) Insertion (Lines 20–34)

```java
    public void addRoute(HttpMethod httpMethod, String path, RouteHandler routeHandler) {  // Line 20
        String[] segments = path.split("/");                   // Line 21
        TrieNode currentNode = root;                           // Line 22

        for (String segment : segments) {                      // Line 24
            if (segment.isEmpty()) {                           // Line 25: Skip empty segments from leading "/"
                continue;                                      // Line 26
            }
            if (!currentNode.getChildren().containsKey(segment)) {  // Line 28
                currentNode.getChildren().put(segment, new TrieNode());  // Line 29: Create missing node
            }
            currentNode = currentNode.getChildren().get(segment);  // Line 31: Advance
        }
        currentNode.getHandlers().put(httpMethod, routeHandler);  // Line 33: Store handler at leaf
    }
```

**Walk-through for `addRoute(GET, "/api/status", handler)`:**

```
Input: path = "/api/status"
Split: ["", "api", "status"]

Step 1: segment="" → skip (empty)
Step 2: segment="api"
  → root.children.containsKey("api")? No
  → root.children.put("api", new TrieNode())
  → currentNode = root.children.get("api")
Step 3: segment="status"
  → currentNode.children.containsKey("status")? No
  → currentNode.children.put("status", new TrieNode())
  → currentNode = that new node
Step 4: currentNode.handlers.put(GET, handler)   ← Handler stored at leaf
```

**Complexity:**
```
Time:  O(K) where K = number of non-empty segments
       Each segment: containsKey() O(1) + put() O(1) = O(1)
       path.split("/") itself is O(L) where L = path string length
       Total: O(L + K), simplified to O(K) since K ≤ L

Space: Creates at most K new TrieNode objects for a completely new path
       Shared prefixes reuse existing nodes — no new allocations
```

```java
    public HttpResponse route(HttpRequest request) {           // Line 36
        String path = request.getPath();                       // Line 37
        HttpMethod httpMethod = request.getMethod();           // Line 38

        // Strip query string before routing — "/api/search?q=hello" → "/api/search"
        int queryIndex = path.indexOf('?');                    // Line 41
        if (queryIndex != -1) {                                // Line 42
            path = path.substring(0, queryIndex);              // Line 43
        }

        String[] segments = path.split("/");                   // Line 46
        TrieNode currentNode = root;                           // Line 47

        for (String segment : segments) {                      // Line 49
            if (segment.isEmpty()) {                           // Line 50
                continue;                                      // Line 51
            }
            if (!currentNode.getChildren().containsKey(segment)) {  // Line 53
                HttpResponse httpResponse = new HttpResponse();// Line 54
                httpResponse.setStatus(HttpStatus.NOT_FOUND);  // Line 55
                return httpResponse;                           // Line 56: Path not found → 404
            }
            currentNode = currentNode.getChildren().get(segment);  // Line 58
        }
```

**Lines 41-43: Query string stripping.** URLs like `/api/search?q=hello&page=2` have everything after `?` stripped before the trie walk. Without this, the trie would try to match `search?q=hello&page=2` as a single segment — which would always fail.

**Line 53-56: Early termination.** If any segment doesn't exist in the trie, the path doesn't match any registered route. Return 404 immediately — no need to traverse further.

```java
        if (currentNode.getHandlers().containsKey(httpMethod)) {  // Line 60
            RouteHandler routeHandler = currentNode.getHandlers().get(httpMethod);  // Line 61
            return routeHandler.handle(request);               // Line 62: Execute the handler
        } else if (!currentNode.getHandlers().isEmpty()) {     // Line 63
            // Path exists but method doesn't match → 405 Method Not Allowed
            HttpResponse httpResponse = new HttpResponse();    // Line 65
            httpResponse.setStatus(HttpStatus.METHOD_NOT_ALLOWED);  // Line 66
            httpResponse.setBody("{\"error\": \"Method Not Allowed\"}");  // Line 67
            return httpResponse;                               // Line 68
        } else {                                               // Line 69
            HttpResponse httpResponse = new HttpResponse();    // Line 70
            httpResponse.setStatus(HttpStatus.NOT_FOUND);      // Line 71
            return httpResponse;                               // Line 72: No handlers at all → 404
        }
    }
```

**Lines 63-68: 405 Method Not Allowed.** When the trie node exists and has handlers, but not for the requested HTTP method, the router now returns 405 (HTTP-spec compliant) instead of 404. The `!handlers.isEmpty()` check distinguishes "path exists, wrong method" from "trie node exists but has no handlers" (e.g., an intermediate node like `/api`).

**Route matching complexity:**
```
Time:  O(K) for K path segments
       Plus: handler.handle(request) includes method.invoke() — ~5-10ns after JIT warmup
       Plus: path.split("/") — O(L) string traversal + K+1 string allocations

Space: O(K) for the String[] array from split()
       O(1) for the trie traversal itself (no new nodes created)
```

### `registerController(Object)` — Reflection-Based Registration (Lines 65–105)

```java
    public void registerController(Object controller) {        // Line 65
        Class<?> controllerClass = controller.getClass();      // Line 66
        if (!controllerClass.isAnnotationPresent(RestController.class)) { return; }  // Line 67
```

**Line 67: Guard check.** Redundant with `RouteScanner.processClass()` which already checks for `@RestController`. This is a safety guard — `registerController()` can be called from test code or manual registration without going through the scanner.

#### Lambda-Wrapped Reflection (Lines 69–86)

```java
        for (Method method : controllerClass.getDeclaredMethods()) {  // Line 69
            if (method.isAnnotationPresent(GetMapping.class)) {  // Line 70
                GetMapping annotation = method.getAnnotation(GetMapping.class);  // Line 71
                String path = annotation.value();              // Line 72
                RouteHandler handler = request -> {            // Line 73: Lambda captures method + controller
                    try {
                        return (HttpResponse) method.invoke(controller, request);  // Line 75
                    }
                    catch (Exception e) {                      // Line 77
                        System.err.println("Error executing GET method: " + e.getMessage());
                        HttpResponse errorResponse = new HttpResponse();
                        errorResponse.setStatus(HttpStatus.INTERNAL_ERROR);
                        return errorResponse;                  // Line 81: 500 on failure
                    }
                };
                this.addRoute(HttpMethod.GET, path, handler);  // Line 84
                System.out.println("Mapped GET: " + path + " onto " + controllerClass.getSimpleName() + "." + method.getName());
            }
```

**Line 73-83: The lambda closure.** This is the key pattern. The lambda captures:
- `method` — a `java.lang.reflect.Method` object (resolved once at registration time)
- `controller` — the controller instance (created once by `RouteScanner`)

When the route is matched later, `handler.handle(request)` executes:
1. `method.invoke(controller, request)` — calls `DemoController.getStatus(request)` via reflection
2. Casts the return to `HttpResponse`
3. If the controller method throws, catches the exception and returns 500

**Reflection performance after JIT warmup:**
```
Cold (first call):           ~5000 ns (method resolution, security checks)
Warm (calls 2-15):           ~50 ns (JVM-interpreted reflective accessor)
Hot (after inflation at 16): ~5-10 ns (JVM generates bytecode accessor class)
```

The JVM's "inflation" mechanism (controlled by `sun.reflect.inflationThreshold=15`) replaces the generic reflective invoker with a generated class that makes a direct method call. After warmup, the overhead of reflection is nearly zero.

---

## Thread Safety of the Trie

The trie uses **no synchronization** — no `synchronized`, no `ConcurrentHashMap`, no `volatile`. This is safe because of the **happens-before guarantee** in the Java Memory Model (JMM):

```
STARTUP PHASE (single-threaded):
  RouteScanner.scan() → Router.registerController() → addRoute()
  ↑ All trie writes happen here, on the main thread

  Server.start() → new ServerSocket(port, backlog)
                    ↑ ServerSocket constructor involves I/O synchronization
                      This establishes a happens-before edge

RUNTIME PHASE (multi-threaded):
  Thread pool workers call router.route()
  ↑ All trie reads happen here, on pool threads

Because:
  1. All writes complete before ServerSocket construction
  2. ServerSocket construction involves monitor operations (I/O sync)
  3. Thread pool workers are created after ServerSocket is bound
  4. Thread creation establishes a happens-before edge (JLS §17.4.5)

Therefore: All trie writes are visible to all reader threads. No synchronization needed.
```

**If dynamic route registration at runtime were added** (e.g., hot-reloading controllers), the `HashMap` inside `TrieNode` would need to be replaced with `ConcurrentHashMap` or guarded by a `ReadWriteLock`.

---

## Route Matching Examples

```
Registered routes:
  GET  /api/status → DemoController.getStatus
  POST /api/echo   → TestController.handleEcho

Example 1: GET /api/status → ✅ 200
  root → "api" ✓ → "status" ✓ → handlers.get(GET) ✓ → invoke → response

Example 2: DELETE /api/status → ❌ 405 Method Not Allowed
  root → "api" ✓ → "status" ✓ → handlers.get(DELETE) ✗ → handlers.isEmpty()? No → return 405

Example 3: GET /api/unknown → ❌ 404
  root → "api" ✓ → "unknown" ✗ → return 404 immediately (no further traversal)

Example 4: GET /api → ❌ 404
  root → "api" ✓ → handlers.get(GET) ✗ → handlers.isEmpty()? Yes → return 404

Example 5: GET / → ❌ 404 (falls through to static file handler)
  split("/") → ["", ""] → all segments empty → stay at root
  root.handlers.get(GET) ✗ → return 404
  RequestProcessor catches 404 + GET → rewrites to /index.html → StaticFileHandler

Example 6: GET /api/status?verbose=true → ✅ 200
  query string stripped: "/api/status?verbose=true" → "/api/status"
  root → "api" ✓ → "status" ✓ → handlers.get(GET) ✓ → invoke → response
```

---

## Key Design Notes

- **Trie-based routing:** O(K) where K = number of path segments. Optimal for hierarchical URL structures.
- **Query string stripping:** Everything after `?` is removed before trie lookup. Query parameters are not parsed or made available to handlers.
- **405 Method Not Allowed:** When a trie node exists with handlers but not for the requested method, 405 is returned (HTTP-spec compliant). When the node has no handlers at all (intermediate node), 404 is returned.
- **No path parameters:** The current implementation matches segments exactly. `/users/{id}` would require wildcard nodes — a natural trie extension.
- **No wildcard routes:** No `*` or `**` glob patterns. Express-style catch-all routes are not supported.
- **Reflection overhead is negligible:** After JIT inflation (~15 calls), `method.invoke()` is ~5-10ns — comparable to a virtual method call.
