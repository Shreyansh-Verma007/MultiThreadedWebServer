# 📄 `StaticFileHandler.java` — Static File Serving

**Package:** `com.Shreyansh.webserver.cache`  
**Path:** `src/main/java/com/Shreyansh/webserver/cache/StaticFileHandler.java`  
**Role:** Resolves, reads, caches, and serves static files (HTML, CSS, JS, images) from the filesystem or JAR classpath.

---

## File Overview

The `StaticFileHandler` is responsible for serving static files (like `index.html`, `pc.jpg`, etc.) in response to HTTP GET requests. It implements a **three-tier resolution strategy**:

1. **LRU Cache** — Check if the file is already cached in memory (fastest).
2. **Filesystem** — Read from `src/main/resources/` on disk (development mode).
3. **JAR Classpath** — Read from the embedded resources inside the JAR (production mode).

After reading a file from disk or classpath, it's automatically stored in the LRU cache for future requests.

---

## Line-by-Line Explanation

### Fields (Lines 9–10)

```java
private final String staticDirectory = "src/main/resources";   // Line 9: Base directory for static files on disk
private final LRUCache cache;                                  // Line 10: Reference to the LRU cache instance
```

### Constructor (Lines 12–14)

```java
public StaticFileHandler(LRUCache cache) {                     // Line 12
    this.cache = cache;                                        // Line 13: Store the injected cache
}
```
The cache is injected via the constructor (manual dependency injection).

### `get(String requestPath)` — Main Method (Lines 16–56)

This is the core method that resolves and returns a file.

```java
public LRUCache.cachedFile get(String requestPath) throws IOException {  // Line 16
```

#### Step 1: Check the cache (Lines 17–21)
```java
    LRUCache.cachedFile cachedFile = cache.get(requestPath);   // Line 17: Look up in LRU cache
    if (cachedFile != null) {                                  // Line 18: Cache HIT
        System.out.println("cache hit - Served the file from cache: " + requestPath);  // Line 19
        return cachedFile;                                     // Line 20: Return immediately — fastest path
    }
```
If the file is in the cache, return it immediately without touching the disk.

#### Step 2: Security check (Lines 23–27)
```java
    String relativePath = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;  // Line 23

    if (relativePath.contains("..")) {                         // Line 25: Path traversal detection
        throw new SecurityException("Unauthorized access attempt: " + requestPath);  // Line 26
    }
```
**Path traversal protection.** If the request path contains `..` (e.g., `/../../../etc/passwd`), the request is rejected with a `SecurityException`. This prevents attackers from reading files outside the static directory.

#### Step 3: Try filesystem (Lines 29–46)
```java
    byte[] fileBytes = null;                                   // Line 29

    Path root = Paths.get(staticDirectory);                    // Line 32: Resolve base directory
    Path resolvedPath = root.resolve(relativePath).normalize();// Line 33: Resolve full path, normalize (remove redundant segments)
    
    if (Files.exists(resolvedPath) && !Files.isDirectory(resolvedPath) && resolvedPath.startsWith(root)) {  // Line 35
        System.out.println("cache miss - Served the file from hard disk: " + requestPath);  // Line 36
        fileBytes = Files.readAllBytes(resolvedPath);          // Line 37: Read entire file into byte array
    } else {                                                   // Line 38
        try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(relativePath)) {  // Line 40
            if (is != null) {                                  // Line 41
                System.out.println("cache miss - Served the file from JAR classpath: " + requestPath);  // Line 42
                fileBytes = is.readAllBytes();                 // Line 43: Read from classpath
            }
        }
    }
```

**Resolution order:**
1. **Filesystem**: Checks if `src/main/resources/<path>` exists as a real file on disk. Uses `resolvedPath.startsWith(root)` as a second layer of path traversal protection (even after normalization).
2. **JAR classpath**: If the file isn't on disk (e.g., running from a JAR), tries `ClassLoader.getResourceAsStream()` which looks inside the JAR.

#### Step 4: Handle not found (Lines 48–50)
```java
    if (fileBytes == null) {                                   // Line 48
        return null;                                           // Line 49: File not found anywhere
    }
```
If neither filesystem nor classpath produced the file, return `null`. The caller (`RequestProcessor`) will serve a 404 response.

#### Step 5: Cache and return (Lines 52–55)
```java
    String contentType = determineContentType(requestPath);    // Line 52: Determine MIME type from file extension
    cache.put(requestPath, fileBytes, contentType);            // Line 53: Store in LRU cache for next time

    return new LRUCache.cachedFile(fileBytes, contentType);    // Line 55: Return the file data
```

### `determineContentType(String path)` — MIME Type Detection (Lines 58–73)

```java
private String determineContentType(String path) {            // Line 58
    int idx = path.lastIndexOf('.');                           // Line 59: Find the last dot in the path
    if (idx == -1 || idx == path.length() - 1) {              // Line 60: No extension or trailing dot
        return "application/octet-stream";                     // Line 61: Default binary MIME type
    }
    String extension = path.substring(idx + 1).toLowerCase();  // Line 63: Extract lowercase extension
    return switch (extension) {                                // Line 64: Java 21 switch expression
        case "html" -> "text/html";                            // Line 65
        case "css" -> "text/css";                              // Line 66
        case "js" -> "application/javascript";                 // Line 67
        case "png" -> "image/png";                             // Line 68
        case "jpg", "jpeg" -> "image/jpeg";                    // Line 69: Multiple case labels (Java 14+)
        case "gif" -> "image/gif";                             // Line 70
        default -> "application/octet-stream";                 // Line 71: Unknown extensions → binary
    };
}
```
Uses a **Java 21 switch expression** (arrow syntax) to map file extensions to MIME content types. Supported types:

| Extension | MIME Type |
|-----------|-----------|
| `.html` | `text/html` |
| `.css` | `text/css` |
| `.js` | `application/javascript` |
| `.png` | `image/png` |
| `.jpg` / `.jpeg` | `image/jpeg` |
| `.gif` | `image/gif` |
| *(other)* | `application/octet-stream` |

---

## Request Flow

```
GET /index.html
  │
  ├── cache.get("/index.html") → HIT? Return immediately
  │
  ├── (MISS) Check: contains ".."? → SecurityException
  │
  ├── Check filesystem: src/main/resources/index.html
  │     └── Exists? → Read bytes from disk
  │
  ├── (Not on disk) Check JAR classpath: index.html
  │     └── Found? → Read bytes from classpath
  │
  ├── (Not found anywhere) → return null (404)
  │
  └── Cache the file → return cachedFile
```

---

## Security Considerations

1. **`..` path traversal**: Rejected immediately with `SecurityException`.
2. **`resolvedPath.startsWith(root)`**: Even after path normalization, ensures the resolved path is still within the static directory. This is a defense-in-depth measure.
3. **`!Files.isDirectory(resolvedPath)`**: Prevents directory listing.
