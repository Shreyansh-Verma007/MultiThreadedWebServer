# 📄 `LRUCache.java` — Least Recently Used Cache

**Package:** `com.Shreyansh.webserver.cache`  
**Path:** `src/main/java/com/Shreyansh/webserver/cache/LRUCache.java`  
**Role:** Thread-safe, bounded in-memory cache that evicts the least-recently-used entries when capacity is exceeded.

---

## File Overview

The `LRUCache` implements the classic **LeetCode #146** data structure: a **doubly-linked list + HashMap** combination that achieves O(1) time for get, put, and evict operations. It stores static file contents (`byte[]`) along with their MIME content types.

This is a foundational performance component — it prevents the server from re-reading static files from disk or JAR classpath on every request. On a cache hit, the response is assembled entirely from memory with zero I/O.

---

## Why This Data Structure? (Interview Context)

### The Problem
We need a cache with:
- **O(1) lookup** by key (file path)
- **O(1) insertion** of new entries
- **O(1) eviction** of the *least recently used* entry when the cache is full
- **Access-order tracking** — every `get()` must promote the entry to "most recently used"

### Why Not Simpler Alternatives?

| Data Structure | Lookup | Insert | Evict LRU | Why Not Sufficient |
|---|---|---|---|---|
| `HashMap` alone | O(1) | O(1) | ❌ O(N) — must scan all entries to find oldest | Can't efficiently find the LRU entry |
| `LinkedList` alone | ❌ O(N) — must scan to find entry | O(1) at head | O(1) at tail | Can't efficiently look up by key |
| `TreeMap` (sorted by access time) | O(log N) | O(log N) | O(log N) at first entry | All operations are O(log N), not O(1) |
| **DLL + HashMap** (our choice) | O(1) via map | O(1) front insert | O(1) tail removal | ✅ All operations O(1) |
| `LinkedHashMap(accessOrder=true)` | O(1) | O(1) | O(1) via `removeEldestEntry()` | ✅ Works, but hand-built version teaches the internals |

### Why Not Java's `LinkedHashMap`?

Java provides a built-in LRU cache in ~5 lines:

```java
LinkedHashMap<String, CachedFile> cache = new LinkedHashMap<>(capacity, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, CachedFile> eldest) {
        return size() > capacity;
    }
};
```

The hand-built version was chosen for three reasons:
1. **Interview value.** LRU cache is among the most frequently asked data structure questions. Building it from scratch demonstrates pointer manipulation, sentinel nodes, and composite data structure design.
2. **Thread safety control.** `LinkedHashMap` is not thread-safe. We'd need `Collections.synchronizedMap()`, which provides less control than our explicit `synchronized` methods.
3. **Custom node data.** Our `Node` stores `byte[] value` + `String contentType` together — avoiding wrapper objects.

---

## Data Structure Internals

```
  HEAD ←→ [Most Recent] ←→ [Second Recent] ←→ ... ←→ [Least Recent] ←→ TAIL
    ↑                                                                    ↑
  sentinel                                                          sentinel
  (dummy node, never removed)                                    (dummy node, never removed)

  HashMap<String, Node>  →  key-to-node lookup in O(1) (all access serialized via synchronized)
```

**Sentinel nodes** (HEAD and TAIL) are dummy nodes that simplify pointer manipulation by eliminating null checks:

```java
// WITHOUT sentinels — 4 conditional branches:
if (node == head) head = node.next;
if (node == tail) tail = node.prev;
if (node.prev != null) node.prev.next = node.next;
if (node.next != null) node.next.prev = node.prev;

// WITH sentinels — 2 lines, no branches:
node.prev.next = node.next;    // prev is always valid (at minimum it's HEAD)
node.next.prev = node.prev;    // next is always valid (at minimum it's TAIL)
```

This is the same technique used in the Linux kernel's `list.h` implementation.

---

## Line-by-Line Explanation

### Fields (Lines 6–9)

```java
private final int capacity;                                    // Line 6: Max entries the cache can hold
private final Node head;                                       // Line 7: HEAD sentinel (most recent end)
private final Node tail;                                       // Line 8: TAIL sentinel (least recent end)
private final Map<String, Node> map;                           // Line 9: Key → Node for O(1) lookup
```

### Constructor (Lines 11–18)

```java
public LRUCache(int capacity) {                                // Line 11
    this.capacity = capacity;                                  // Line 12
    this.map = new HashMap<>(capacity);                        // Line 13: Pre-sized to avoid rehashing
    head = new Node("", null, "");                             // Line 14: HEAD sentinel
    tail = new Node("", null, "");                             // Line 15: TAIL sentinel
    head.next = tail;                                          // Line 16: HEAD → TAIL
    tail.prev = head;                                          // Line 17: TAIL → HEAD
}
```

**Line 13:** A regular `HashMap` is used (not `ConcurrentHashMap`) because all public methods are `synchronized` — making CHM's fine-grained locking redundant. A `HashMap` avoids the CAS overhead inherent in CHM internals. Pre-sized with `capacity` to minimize rehashing.

**After constructor:** `HEAD ←→ TAIL` (empty list)

### Inner Class: `Node` (Lines 19–31)

```java
private static class Node {                                    // Line 21
    String key;                                                // Line 22: Cache key (e.g., "/index.html")
    byte[] value;                                              // Line 23: Cached file bytes
    String contentType;                                        // Line 24: MIME type (e.g., "text/html")
    Node prev;                                                 // Line 25: Previous node in the DLL
    Node next;                                                 // Line 26: Next node in the DLL

    Node(String key, byte[] value, String contentType) {       // Line 28
        this.key = key;
        this.value = value;
        this.contentType = contentType;
    }
}
```

Each `Node` combines the linked list element with the cached data. `private static` keeps it as an implementation detail.

**Memory per node:** ~16 bytes (object header) + 8 bytes (key ref) + 8 bytes (value ref) + 8 bytes (contentType ref) + 8 bytes (prev ref) + 8 bytes (next ref) = **~56 bytes** for the node structure itself, plus the actual `byte[]` and string data it references.

### Inner Class: `CachedFile` (Lines 33–41) — Public DTO

```java
public static class CachedFile {                               // Line 33
    public final byte[] data;                                  // Line 34
    public final String contentType;                           // Line 35

    public CachedFile(byte[] data, String contentType) {       // Line 37
        this.data = data;
        this.contentType = contentType;
    }
}
```

A **Data Transfer Object** that decouples the cache's internal `Node` structure from its public API. Callers receive a `CachedFile` — they never see `Node`, `prev`, `next`, or the linked list. Fields are `final` for immutability.

### `insertToFront(Node)` (Lines 43–49) — O(1) Promotion

```java
private void insertToFront(Node node) {                        // Line 43
    map.put(node.key, node);                                   // Line 44: Register in HashMap
    head.next.prev = node;                                     // Line 45: Old first ← new node
    node.next = head.next;                                     // Line 46: New node → old first
    head.next = node;                                          // Line 47: HEAD → new node
    node.prev = head;                                          // Line 48: New node ← HEAD
}
```

**Before:** `HEAD ←→ A ←→ B ←→ TAIL`
**After `insertToFront(X)`:** `HEAD ←→ X ←→ A ←→ B ←→ TAIL`

The 4 pointer assignments must happen in this exact order to avoid losing references. Drawing the pointer changes on paper is the best way to verify correctness.

### `remove(Node)` (Lines 51–55) — O(1) Extraction

```java
private void remove(Node node) {                               // Line 53
    map.remove(node.key);                                      // Line 54: Remove from HashMap
    node.prev.next = node.next;                                // Line 55: Bypass: prev → next
    node.next.prev = node.prev;                                // Line 56: Bypass: next → prev
}
```

**Before:** `... ←→ A ←→ X ←→ B ←→ ...`
**After `remove(X)`:** `... ←→ A ←→ B ←→ ...`  (X is unlinked and eligible for GC)

All public methods hold the intrinsic lock, so map removal and pointer surgery happen atomically within the same `synchronized` block.

### `get(String)` — synchronized, O(1) (Lines 57–66)

```java
public synchronized CachedFile get(String key) {               // Line 59
    if (map.containsKey(key)) {                                // Line 60: Cache hit?
        Node node = map.get(key);                              // Line 61: O(1) lookup
        CachedFile result = new CachedFile(node.value, node.contentType);  // Line 62
        remove(node);                                          // Line 63: Remove from current position
        insertToFront(node);                                   // Line 64: Re-insert at front (MRU)
        return result;                                         // Line 65
    }
    return null;                                               // Line 67: Cache miss
}
```

**The remove-then-reinsert pattern** is the core of LRU behavior. Every time an entry is accessed, it's moved to the front. Entries that haven't been accessed drift toward the tail. The tail entry is always the least recently used.

**Why create a new `CachedFile` DTO (Line 62)?** To decouple the return value from the internal `Node`. If we returned the `Node` directly, callers could modify `prev`/`next` pointers and corrupt the linked list.

### `put(String, byte[], String)` — synchronized, O(1) (Lines 68–77)

```java
public synchronized void put(String key, byte[] value, String contentType) {  // Line 68
    if (map.containsKey(key)) {                                // Line 69: Key already cached?
        remove(map.get(key));                                  // Line 70: Remove old version
    }
    if (map.size() == capacity) {                              // Line 72: Cache full?
        remove(tail.prev);                                     // Line 73: Evict LRU (before TAIL sentinel)
    }
    Node node = new Node(key, value, contentType);             // Line 75: New node
    insertToFront(node);                                       // Line 76: Insert at front (MRU position)
}
```

**Line 73: `remove(tail.prev)`** — This is the eviction. `tail.prev` is always the least recently used entry because:
- New entries are inserted at the front (after HEAD)
- Accessed entries are moved to the front
- Entries that haven't been accessed stay near the tail
- Therefore `tail.prev` is the entry that has gone the longest without being accessed

### `remove(String)` — Public invalidation, O(1) (Lines 79–83)

```java
public synchronized void remove(String key) {                  // Line 79
    if (map.containsKey(key)) {                                // Line 80
        remove(map.get(key));                                  // Line 81: Delegate to private remove(Node)
    }
}
```

Public API for manual cache invalidation. Not used in the current codebase, but available for scenarios like hot-reloading a file.

---

## Thread Safety: `HashMap` + `synchronized` (Design Decision)

All public methods are `synchronized`, meaning only one thread can execute any cache operation at a time. This correctly prevents linked list corruption from concurrent modifications.

A regular `HashMap` is used instead of `ConcurrentHashMap` because:

```
synchronized methods → intrinsic lock on the LRUCache instance → serialized access
HashMap              → simple, no CAS overhead, no segment locking

Result: All access is serialized by the synchronized lock.
        ConcurrentHashMap's fine-grained locking would be redundant.
        HashMap is functionally identical and ~5% faster.
```

**Why not use CHM without `synchronized` (lock-free)?** The linked list operations (`remove` + `insertToFront`) modify 4-6 pointers that must be updated atomically. Without a lock, two concurrent `get()` calls could both try to move nodes, corrupting the list. Lock-free linked list modification is possible but extremely complex — not worth it for a file cache.

---

## Complexity Summary

| Operation | Time | Space | Synchronization |
|-----------|------|-------|----------------|
| `get(key)` | O(1) | O(1) — creates DTO object | `synchronized` — blocks other threads |
| `put(key, val, type)` | O(1) amortized | O(1) — creates Node | `synchronized` — blocks other threads |
| `remove(key)` | O(1) | O(1) | `synchronized` — blocks other threads |
| Eviction (inside `put`) | O(1) | O(1) — GC reclaims evicted node | Happens inside `put`'s synchronized block |
| Total cache memory | — | O(capacity × average_file_size) | — |

**Memory example:** With `capacity=50` and average static file size of 100 KB:
- Cache data: ~50 × 100 KB = ~5 MB
- Node overhead: ~50 × 56 bytes = ~2.8 KB
- HashMap overhead: ~50 × 32 bytes = ~1.6 KB
- **Total: ~5 MB** (dominated by file contents)

But with `tech.jpg` (10 MB) cached: just one entry uses 10 MB. Capacity is entry-count-based, not byte-based — a potential concern for large files.
