# 📄 `TrieNode.java` — Trie Node for URL Routing

**Package:** `com.Shreyansh.webserver.routing`  
**Path:** `src/main/java/com/Shreyansh/webserver/routing/TrieNode.java`  
**Role:** Represents a single node in the URL routing trie, holding child nodes and HTTP method → handler mappings.

---

## File Overview

`TrieNode` is a simple data class that represents one node in the routing trie. Each node corresponds to a **single URL path segment** (e.g., `"api"`, `"status"`, `"users"`).

A node has:
- **Children**: Other `TrieNode`s keyed by path segment name (for deeper paths).
- **Handlers**: A map of `HttpMethod → RouteHandler` for the HTTP methods handled at this path.

---

## Line-by-Line Explanation

```java
package com.Shreyansh.webserver.routing;                       // Line 1
```

```java
import com.Shreyansh.webserver.http.HttpMethod;                // Line 3
import java.util.HashMap;                                      // Line 4
import java.util.Map;                                          // Line 5
```

```java
public class TrieNode {                                        // Line 7
    private final Map<String, TrieNode> children = new HashMap<>();  // Line 8
    private final Map<HttpMethod, RouteHandler> handlers = new HashMap<>();  // Line 9
```

### Fields

| Field | Type | Purpose |
|-------|------|---------|
| `children` | `Map<String, TrieNode>` | Maps a path segment name to the next node. E.g., `"api" → TrieNode` |
| `handlers` | `Map<HttpMethod, RouteHandler>` | Maps an HTTP method to its handler at this path. E.g., `GET → handler` |

### Constructor (Line 11)

```java
    public TrieNode() {}                                       // Line 11: Empty constructor — fields initialized inline
```

### Getters (Lines 13–18)

```java
    public Map<String, TrieNode> getChildren() {               // Line 13
        return children;                                       // Line 14: Mutable access to children map
    }

    public Map<HttpMethod, RouteHandler> getHandlers() {       // Line 16
        return handlers;                                       // Line 17: Mutable access to handlers map
    }
```

Both getters return the **mutable** maps directly. This allows `Router.addRoute()` and `Router.route()` to read and write to these maps.

---

## Trie Structure Example

For routes `GET /api/status` and `POST /api/users`:

```
Root TrieNode:
  children: {
    "api" → TrieNode:
      children: {
        "status" → TrieNode:
          children: {}
          handlers: { GET → statusHandler }
        "users" → TrieNode:
          children: {}
          handlers: { POST → createUserHandler }
      }
      handlers: {}    (no handler for /api itself)
  }
  handlers: {}        (no handler for / itself)
```

---

## Key Design Notes

- **Mutable maps returned**: The getters return the internal maps directly (no defensive copies). This is intentional — the `Router` needs to mutate these maps. In a larger codebase, this would be an encapsulation concern.
- **No path segment stored**: The node doesn't store its own segment name — that's the key in the parent's `children` map.
- **Multiple handlers per node**: A single path (e.g., `/api/users`) can have handlers for different HTTP methods (GET, POST, etc.) stored in the same node's `handlers` map.
