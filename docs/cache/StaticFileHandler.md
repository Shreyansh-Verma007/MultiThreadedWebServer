# 📄 `StaticFileHandler.java` — Static File Server with LRU Caching

**Package:** `com.Shreyansh.webserver.cache`  
**Path:** `src/main/java/com/Shreyansh/webserver/cache/StaticFileHandler.java`  
**Role:** Resolves static file requests through a 3-tier lookup (LRU cache → filesystem → JAR classpath), with path traversal security, file size limits, and MIME type detection.

---

## File Overview

`StaticFileHandler` bridges the gap between URL paths and actual file content. When the Router returns 404 for a GET request, `RequestProcessor` falls back to this handler. It serves static assets (HTML, images, CSS, JS, fonts) with:

1. **LRU caching** — frequently requested files are served from memory
2. **Dual-source resolution** — files can come from disk (development) or JAR classpath (production)
3. **Path traversal prevention** — rejects `..` segments and validates resolved paths stay within the static root
4. **File size cap** — rejects files larger than 50 MB before loading into heap
5. **MIME type detection** — sets `Content-Type` based on file extension (15 types supported)

---

## Safety Limit

```java
private static final long MAX_FILE_SIZE = 52_428_800;  // 50 MB
```

Files exceeding this size return `null` (treated as 404 by `RequestProcessor`). This prevents `OutOfMemoryError` from serving multi-gigabyte files via `Files.readAllBytes()`.

---

## 3-Tier File Resolution Strategy

```
get("/index.html")
  │
  ├── Tier 1: LRU Cache (in-memory)
  │   └── cache.get("/index.html")
  │       ├── HIT  → return CachedFile immediately (zero I/O, ~50ns)
  │       └── MISS → proceed to Tier 2
  │
  ├── Tier 2: Filesystem (development mode)
  │   └── Paths.get("src/main/resources").resolve("index.html").normalize()
  │       ├── EXISTS + within root + not directory + size ≤ 50MB
  │       │     → Files.readAllBytes(), determine MIME, cache.put(), return (~1-5ms)
  │       └── NOT FOUND / too large → proceed to Tier 3
  │
  └── Tier 3: JAR Classpath (production mode)
      └── getClass().getClassLoader().getResourceAsStream("index.html")
          ├── EXISTS → readAllBytes(), determine MIME, cache.put(), return (~0.5-2ms)
          └── NOT FOUND → return null (file doesn't exist anywhere)
```

**Why this order?**
- **Cache first:** Avoids disk I/O for frequently requested files. After the first request, subsequent requests are served from memory.
- **Filesystem second:** During development, files are loose on disk at `src/main/resources/`. Allows editing files without rebuilding.
- **Classpath last:** In production (running from a JAR), files are packaged inside the JAR. `getResourceAsStream()` reads from ZIP entries.

---

## Line-by-Line Explanation

### Fields and Constructor (Lines 9–18)

```java
public class StaticFileHandler {
    private final String staticDirectory = "src/main/resources";  // Line 10: Filesystem base path
    private final LRUCache cache;                                 // Line 11: Injected LRU cache

    public StaticFileHandler(LRUCache cache) {
        this.cache = cache;
    }
```

**Line 10:** `staticDirectory` is hardcoded to `"src/main/resources"`. This path exists during development but not inside a production JAR. When the file isn't found on the filesystem, the handler falls through to the classpath resolver (Tier 3).

### `get(String requestPath)` — Main Resolution Method (Lines 20–64)

#### Tier 1: LRU Cache Check (Lines 21–25)

```java
    public LRUCache.CachedFile get(String requestPath) throws IOException {
        LRUCache.CachedFile cachedFile = cache.get(requestPath);
        if (cachedFile != null) {
            System.out.println("cache hit - Served the file from cache: " + requestPath);
            return cachedFile;
        }
```

**`cache.get(requestPath)` is `synchronized`** — thread-safe. On hit, returns immediately with zero I/O and promotes the entry to MRU position.

#### Security Check (Lines 27–31)

```java
        String relativePath = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;

        if (relativePath.contains("..")) {
            throw new SecurityException("Unauthorized access attempt: " + requestPath);
        }
```

**Path traversal defense.** Requests like `/../../../etc/passwd` throw `SecurityException`, which `RequestProcessor` catches and converts to HTTP 500. The `..` check runs before any filesystem access.

**Additional defense in Tier 2:** `resolvedPath.startsWith(root)` after `normalize()` prevents symlink or normalization escapes even if `..` were somehow present.

#### Tier 2: Filesystem Resolution (Lines 35–46)

```java
        Path root = Paths.get(staticDirectory);
        Path resolvedPath = root.resolve(relativePath).normalize();

        if (Files.exists(resolvedPath) && !Files.isDirectory(resolvedPath) && resolvedPath.startsWith(root)) {
            long fileSize = Files.size(resolvedPath);
            if (fileSize > MAX_FILE_SIZE) {
                System.err.println("File too large to serve (" + fileSize + " bytes): " + requestPath);
                return null;
            }
            fileBytes = Files.readAllBytes(resolvedPath);
        }
```

**Line 38:** `normalize()` collapses `.` and `..` segments. Combined with `startsWith(root)`, ensures the resolved path cannot escape the static directory.

**Lines 40–44:** Size check before allocation. Files over 50 MB return `null` without reading into memory.

#### Tier 3: JAR Classpath Resolution (Lines 48–54)

```java
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(relativePath)) {
            if (is != null) {
                fileBytes = is.readAllBytes();
            }
        }
```

**Note:** Classpath reads do not currently enforce `MAX_FILE_SIZE` before `readAllBytes()`. In practice, bundled resources are small; the filesystem tier is the primary path for large files like `tech.jpg`.

#### Cache and Return (Lines 56–63)

```java
        if (fileBytes == null) {
            return null;
        }
        String contentType = determineContentType(requestPath);
        cache.put(requestPath, fileBytes, contentType);
        return new LRUCache.CachedFile(fileBytes, contentType);
```

After a cache miss read, the file is stored in the LRU cache for subsequent requests.

### `determineContentType(String path)` — MIME Detection (Lines 66–90)

```java
    private String determineContentType(String path) {
        int idx = path.lastIndexOf('.');
        if (idx == -1 || idx == path.length() - 1) {
            return "application/octet-stream";
        }
        String extension = path.substring(idx + 1).toLowerCase();
        return switch (extension) {
            case "html" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "json" -> "application/json";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            case "ico" -> "image/x-icon";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            case "ttf" -> "font/ttf";
            case "pdf" -> "application/pdf";
            case "xml" -> "application/xml";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }
```

**Supported extensions:** `.html`, `.css`, `.js`, `.json`, `.png`, `.jpg`/`.jpeg`, `.gif`, `.svg`, `.ico`, `.woff`, `.woff2`, `.ttf`, `.pdf`, `.xml`, `.txt`. All others fall back to `application/octet-stream`.

---

## Security Analysis

| Attack Vector | Defense | Effectiveness |
|--------------|---------|:---:|
| Path traversal (`/../../../etc/passwd`) | `relativePath.contains("..")` + `normalize()` + `startsWith(root)` | ✅ Multi-layer |
| Directory listing (`GET /controllers/`) | `Files.isDirectory()` check — directories skipped | ✅ No listing exposed |
| Null byte injection (`%00`) | Java `Path` rejects null bytes | ✅ Protected by JVM |
| Symlink escape | `startsWith(root)` after normalize | ⚠️ Partial — symlinks pointing outside root may still resolve |
| Large file DoS | `MAX_FILE_SIZE` (50 MB) on filesystem tier | ✅ Prevents OOM from huge files |

---

## Key Design Notes

- **Thread safety:** `get()` calls `cache.get()` and `cache.put()`, both `synchronized`. Multiple threads serialize on cache access but can parallelize on file I/O during misses.
- **Race condition on cache miss:** Two threads requesting the same uncached file may both read from disk. The second `put()` overwrites the first — harmless (same data) but wastes one disk read.
- **Hardcoded static directory:** `"src/main/resources"` only works during development. In production, Tier 3 (classpath) serves files.
- **Entire file in memory:** No streaming. The full file is read into a `byte[]`, cached, and written to the response. Files near the 50 MB cap use significant heap per cached entry.
