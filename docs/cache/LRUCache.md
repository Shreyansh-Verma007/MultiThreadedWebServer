# 📄 `LRUCache.java` — Least Recently Used Cache

**Package:** `com.Shreyansh.webserver.cache`  
**Path:** `src/main/java/com/Shreyansh/webserver/cache/LRUCache.java`  
**Role:** Thread-safe, bounded in-memory cache that evicts the least-recently-used entries when capacity is exceeded.

---

## File Overview

The `LRUCache` implements a classic **Least Recently Used (LRU) cache** using a **doubly-linked list + HashMap** approach. It stores static file contents (`byte[]`) along with their MIME content types. When the cache reaches its capacity limit, the least recently accessed entry is automatically evicted.

This is a foundational performance component — it prevents the server from re-reading static files from disk/classpath on every request.

---

## Data Structure

```
 HEAD ←→ [Most Recent] ←→ [Second Recent] ←→ ... ←→ [Least Recent] ←→ TAIL
   ↑                                                                    ↑
   sentinel                                                        sentinel
   (dummy)                                                         (dummy)

 ConcurrentHashMap<String, Node>  →  O(1) key-to-node lookup
```

- **Doubly-linked list**: Maintains access order. Most recently used items are at the front (after `head`).
- **ConcurrentHashMap**: Provides O(1) lookup by key (the file path).
- **Sentinel nodes**: `head` and `tail` are dummy nodes that simplify insertion/removal logic by eliminating null checks.

---

## Line-by-Line Explanation

### Fields (Lines 6–9)

```java
private int capacity;                                          // Line 6: Maximum number of entries the cache can hold
private Node head;                                             // Line 7: Sentinel node — marks the front of the list (most recent)
private Node tail;                                             // Line 8: Sentinel node — marks the end of the list (least recent)
private ConcurrentHashMap<String, Node> map;                   // Line 9: Key → Node lookup map for O(1) access
```

### Constructor (Lines 11–18)

```java
public LRUCache(int capacity) {                                // Line 11
    this.capacity = capacity;                                  // Line 12: Store the max size
    this.map = new ConcurrentHashMap<String, Node>(capacity);  // Line 13: Pre-sized hash map
    head = new Node("", null, "");                             // Line 14: Create head sentinel (empty key/value)
    tail = new Node("", null, "");                             // Line 15: Create tail sentinel (empty key/value)
    head.next = tail;                                          // Line 16: Link head → tail
    tail.prev = head;                                          // Line 17: Link tail → head
}
```
The constructor creates an empty doubly-linked list with sentinel nodes. Initially: `HEAD ←→ TAIL`.

### Inner Class: `Node` (Lines 19–31)

```java
private static class Node {                                    // Line 19
    public String key;                                         // Line 20: The cache key (file path like "/index.html")
    public byte[] value;                                       // Line 21: The cached file contents as raw bytes
    public String contentType;                                 // Line 22: MIME type (e.g., "text/html", "image/jpeg")
    public Node prev;                                          // Line 23: Pointer to the previous node in the linked list
    public Node next;                                          // Line 24: Pointer to the next node in the linked list

    public Node(String key, byte[] value, String contentType) {// Line 26
        this.key = key;                                        // Line 27
        this.value = value;                                    // Line 28
        this.contentType = contentType;                        // Line 29
    }
}
```
Each `Node` stores the file data plus pointers for the doubly-linked list. This is `private static` — it's an implementation detail not exposed outside `LRUCache`.

### Inner Class: `cachedFile` (Lines 33–41)

```java
public static class cachedFile {                               // Line 33
    public byte[] data;                                        // Line 34: The file bytes
    public String contentType;                                 // Line 35: The MIME content type

    public cachedFile(byte[] data, String contentType) {       // Line 37
        this.data = data;                                      // Line 38
        this.contentType = contentType;                        // Line 39
    }
}
```
A **public DTO (Data Transfer Object)** used to return cached data to callers without exposing the internal `Node` structure. This decouples the cache's internal linked-list implementation from its public API.

### `insertToFront(Node)` (Lines 43–49)

```java
private void insertToFront(Node node) {                        // Line 43
    map.put(node.key, node);                                   // Line 44: Register in the hash map
    head.next.prev = node;                                     // Line 45: Old first node's prev → new node
    node.next = head.next;                                     // Line 46: New node's next → old first node
    head.next = node;                                          // Line 47: Head's next → new node
    node.prev = head;                                          // Line 48: New node's prev → head
}
```
Inserts a node right after the `head` sentinel, making it the **most recently used** entry. Also registers it in the hash map.

**Before:** `HEAD ←→ A ←→ ...`  
**After:** `HEAD ←→ node ←→ A ←→ ...`

### `remove(Node)` (Lines 51–55)

```java
private void remove(Node node) {                               // Line 51
    Node temp = map.remove(node.key);                          // Line 52: Remove from hash map, get the node
    temp.prev.next = temp.next;                                // Line 53: Bypass the node (prev's next → node's next)
    temp.next.prev = temp.prev;                                // Line 54: Bypass the node (next's prev → node's prev)
}
```
Removes a node from both the hash map and the linked list. The sentinel nodes ensure there's always a `prev` and `next`, avoiding null pointer issues.

### `get(String)` — synchronized (Lines 57–66)

```java
public synchronized cachedFile get(String key) {               // Line 57: Thread-safe via synchronized
    if (map.containsKey(key)) {                                // Line 58: Check if key exists in cache
        Node node = map.get(key);                              // Line 59: Get the node
        cachedFile cachedFile = new cachedFile(node.value, node.contentType);  // Line 60: Create return DTO
        remove(node);                                          // Line 61: Remove from current position
        insertToFront(node);                                   // Line 62: Re-insert at front (mark as recently used)
        return cachedFile;                                     // Line 63: Return the cached data
    }
    return null;                                               // Line 65: Cache miss
}
```
**Cache lookup with LRU promotion.** On a hit, the accessed node is moved to the front of the list (most recently used position). Returns `null` on a miss.

The `synchronized` keyword ensures that concurrent threads don't corrupt the linked list during reads.

### `put(String, byte[], String)` — synchronized (Lines 68–77)

```java
public synchronized void put(String key, byte[] value, String contentType) {  // Line 68
    if (map.containsKey(key)) {                                // Line 69: If key already cached...
        remove(map.get(key));                                  // Line 70: ...remove old entry first
    }
    if (map.size() == capacity) {                              // Line 72: If cache is full...
        remove(tail.prev);                                     // Line 73: ...evict least recently used (just before tail)
    }
    Node node = new Node(key, value, contentType);             // Line 75: Create new node
    insertToFront(node);                                       // Line 76: Insert at front (most recently used)
}
```
**Cache insertion with eviction.** Steps:
1. If the key already exists, remove the old entry (to update with new data).
2. If the cache is full, evict the **least recently used** entry (`tail.prev` — the node just before the tail sentinel).
3. Create a new node and insert it at the front.

### `remove(String)` — synchronized (Lines 79–83)

```java
public synchronized void remove(String key) {                  // Line 79
    if (map.containsKey(key)) {                                // Line 80: Check if key exists
        remove(map.get(key));                                  // Line 81: Delegate to private remove(Node)
    }
}
```
**Public API for manual cache invalidation.** Allows external code to explicitly remove a cached entry by key.

---

## Thread Safety

All public methods (`get`, `put`, `remove`) are **`synchronized`**, meaning only one thread can execute any of them at a time on the same `LRUCache` instance. This prevents:
- Linked list corruption from concurrent modifications
- Hash map inconsistencies

The `ConcurrentHashMap` provides additional safety for any unsynchronized reads, though in this implementation all access goes through `synchronized` methods.

---

## Complexity

| Operation | Time | Space |
|-----------|------|-------|
| `get(key)` | O(1) | — |
| `put(key, value, contentType)` | O(1) | O(capacity) total |
| `remove(key)` | O(1) | — |
