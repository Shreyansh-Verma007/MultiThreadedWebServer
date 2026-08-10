# 📄 `FilterChain.java` — Middleware Pipeline

**Package:** `com.Shreyansh.webserver.middleware`  
**Path:** `src/main/java/com/Shreyansh/webserver/middleware/FilterChain.java`  
**Role:** Manages an ordered list of `Filter` instances and executes them sequentially, short-circuiting if any filter rejects the request.

---

## File Overview

`FilterChain` is the **middleware pipeline**. It holds an ordered list of `Filter` objects and runs them one by one against each incoming request. If any filter returns `false`, the chain stops immediately and the request is blocked. If all filters return `true`, the request proceeds to the router.

This pattern is commonly known as the **Chain of Responsibility** design pattern.

---

## Line-by-Line Explanation

```java
package com.Shreyansh.webserver.middleware;                    // Line 1
```

```java
import com.Shreyansh.webserver.http.HttpRequest;               // Line 3
import com.Shreyansh.webserver.http.HttpResponse;              // Line 4

import java.util.ArrayList;                                    // Line 6
import java.util.List;                                         // Line 7
```

### Field (Line 10)

```java
public class FilterChain {                                     // Line 9
    private final List<Filter> filters = new ArrayList<>();    // Line 10: Ordered list of filters
```

The filters list maintains insertion order — filters run in the order they were added.

### `addFilter(Filter)` (Lines 12–14)

```java
    public void addFilter(Filter filter) {                     // Line 12
        filters.add(filter);                                   // Line 13: Append to the end of the list
    }
```

Adds a filter to the chain. Filters are executed in the order they are added. In `Main.java`, only `RateLimiter` is added, but you could add more:

```java
filterChain.addFilter(new RateLimiter());    // Runs first
filterChain.addFilter(new LoggingFilter());  // Runs second
filterChain.addFilter(new AuthFilter());     // Runs third
```

### `execute(HttpRequest, HttpResponse)` (Lines 16–23)

```java
    public boolean execute(HttpRequest request, HttpResponse response) {  // Line 16
        for (Filter filter : filters) {                        // Line 17: Iterate through all filters in order
            if (!filter.filter(request, response)) {           // Line 18: Run the filter
                return false;                                  // Line 19: Filter rejected → stop immediately
            }
        }
        return true;                                           // Line 22: All filters passed → allow
    }
```

**Execution semantics:**
- Iterates through filters **in order**.
- Calls `filter.filter(request, response)` on each.
- If **any** filter returns `false`:
  - The loop stops immediately (**short-circuit**).
  - `execute()` returns `false`.
  - The rejecting filter is expected to have set the response (status, body).
- If **all** filters return `true`:
  - `execute()` returns `true`.
  - The request proceeds to the router.

---

## Execution Flow

```
execute(request, response)
  │
  ├── Filter 1: RateLimiter.filter()
  │     ├── Under limit → return true → continue
  │     └── Over limit → set 429 → return false → STOP
  │
  ├── Filter 2: (hypothetical LoggingFilter)
  │     └── Always returns true → continue
  │
  └── All passed → return true → proceed to Router
```

---

## Key Design Notes

- **Order matters**: Filters execute in insertion order. Put critical filters (like rate limiting) first.
- **Short-circuit**: The first filter to return `false` stops the entire chain. Subsequent filters don't even run.
- **Shared response**: All filters receive the same `HttpResponse` object. A blocking filter should set the response before returning `false`.
- **Not thread-safe for modification**: The `ArrayList` is not synchronized. Filters should be added during startup (before the server starts accepting connections), not at runtime.
