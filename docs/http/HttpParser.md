# 📄 `HttpParser.java` — HTTP/1.1 Request Parser

**Package:** `com.Shreyansh.webserver.http`  
**Path:** `src/main/java/com/Shreyansh/webserver/http/HttpParser.java`  
**Role:** Hand-rolled HTTP/1.1 request parser. Reads raw bytes from a TCP socket's `InputStream` and constructs a structured `HttpRequest` object.

---

## File Overview

`HttpParser` is a **hand-written HTTP/1.1 request parser** — no libraries, no frameworks. It reads raw bytes from a TCP socket and extracts:
- The **request line** (method, path, HTTP version)
- All **headers** (key-value pairs)
- The **request body** (if `Content-Length` is present)

This is arguably the most protocol-critical file in the project. A bug here means malformed requests, data corruption, or security vulnerabilities.

---

## HTTP/1.1 Message Format (RFC 7230)

Every HTTP request follows this exact wire format:

```
┌─────────────────────────────────────────────────────────────┐
│  GET /api/status HTTP/1.1\r\n          ← REQUEST LINE      │
│  Host: localhost:8080\r\n              ← HEADER             │
│  Content-Type: application/json\r\n    ← HEADER             │
│  Content-Length: 15\r\n                ← HEADER             │
│  \r\n                                  ← EMPTY LINE (CRLF)  │
│  {"key":"value"}                       ← BODY (optional)    │
└─────────────────────────────────────────────────────────────┘

\r = 0x0D (carriage return)
\n = 0x0A (line feed)
\r\n = CRLF (the HTTP line terminator)
```

The empty CRLF line after the headers is the **mandatory separator** between headers and body. This is how the parser knows headers are done.

---

## Safety Limits

Two constants guard against malicious or malformed input:

| Constant | Value | Purpose |
|----------|-------|---------|
| `MAX_LINE_LENGTH` | 8192 bytes (8 KB) | Caps each header/request line — matches Apache/Nginx defaults. Exceeding throws `IOException`. |
| `MAX_BODY_SIZE` | 10,485,760 bytes (10 MB) | Rejects oversized `Content-Length` values before allocating the body buffer. Parser returns `null`. |

---

## Line-by-Line Explanation

### `parseRequest(InputStream, String)` — Main Parser (Lines 14–86)

```java
public static HttpRequest parseRequest(InputStream inputStream, String clientIp) throws IOException {
    HttpMethod httpMethod;
    String path;
    String version;
    Map<String, String> headers = new HashMap<>();             // Line 18: Header storage
    String body = "";                                          // Line 19: Default empty body
```

#### Step 1: Parse the Request Line (Lines 21–43)

```java
    String firstLine = readLine(inputStream);                  // Line 21

    if (firstLine == null || firstLine.trim().isEmpty()) {     // Line 23: Empty/closed connection
        return null;                                           // Line 24
    }
    String[] line1 = firstLine.split(" ");                     // Line 28
    if (line1.length < 3) {                                    // Line 29: Malformed request line
        System.err.println("Malformed request line: " + firstLine);
        return null;
    }
    try {
        httpMethod = HttpMethod.valueOf(line1[0].toUpperCase()); // Line 36
    } catch (IllegalArgumentException e) {
        System.err.println("Unsupported HTTP method: " + line1[0]);
        return null;
    }
    path = line1[1];                                           // Line 42
    version = line1[2];                                        // Line 43
```

**Line 21: `readLine(inputStream)`** reads bytes one at a time until it finds `\r\n` (CRLF). Returns the line content without the CRLF. Returns `null` if the stream is closed.

**Line 23–24:** A `null` or empty first line means the client connected but sent nothing (e.g., a TCP health check, a Slowloris attack, or a browser pre-connect). Returning `null` tells `RequestProcessor` to silently close the connection.

**Lines 29–32:** Request lines must have exactly three space-separated parts (`METHOD PATH VERSION`). Malformed lines like `"GET\r\n"` return `null` instead of throwing `ArrayIndexOutOfBoundsException`.

**Lines 35–40:** Unsupported methods (`CONNECT`, `TRACE`, etc.) are caught via `IllegalArgumentException` from `HttpMethod.valueOf()`. The parser returns `null` — the connection closes without a 405/501 response (parsing fails before routing can run).

#### Step 2: Parse Headers (Lines 45–53)

```java
    String line2;
    while ((line2 = readLine(inputStream)) != null && !line2.isEmpty()) {  // Line 47
        String[] parts = line2.split(":", 2);                  // Line 48: Split on FIRST colon only
        if (parts.length == 2) {                               // Line 49: Valid header only
            headers.put(parts[0].trim(), parts[1].trim());     // Line 50
        }
        // Malformed headers without a colon are silently skipped
    }
```

**Line 47:** Headers end when `readLine()` returns an empty string (the mandatory `\r\n` separator between headers and body). The `readLine()` method returns `""` for a CRLF-only line.

**Line 48: `split(":", 2)` — The `2` limit is critical.** Without it, a header like `Host: localhost:8080` would split into three parts: `["Host", " localhost", "8080"]`. With limit 2, it splits into `["Host", " localhost:8080"]` — preserving the value.

**Lines 49–50:** Malformed header lines without a colon (e.g., `BadHeader\r\n`) are silently skipped instead of crashing with `ArrayIndexOutOfBoundsException`.

#### Step 3: Parse Body (Lines 55–83)

```java
    if (headers.containsKey("Content-Length")) {               // Line 56
        int length;
        try {
            length = Integer.parseInt(headers.get("Content-Length").trim());  // Line 59
        } catch (NumberFormatException e) {
            return null;                                       // Invalid Content-Length
        }
        if (length < 0 || length > MAX_BODY_SIZE) {            // Lines 65–72
            return null;                                       // Negative or oversized body
        }
        byte[] bodyBytes = new byte[length];                   // Line 75
        int bytesRead = 0;
        while (bytesRead < length) {                           // Line 77: Read loop
            int read = inputStream.read(bodyBytes, bytesRead, length - bytesRead);
            if (read == -1) break;
            bytesRead += read;
        }
        body = new String(bodyBytes, java.nio.charset.StandardCharsets.ISO_8859_1);  // Line 82
    }
```

**Lines 36–39: The body read loop — handling TCP fragmentation.**

TCP is a **stream protocol**, not a message protocol. When the sender writes 1000 bytes, the receiver might read them as:

```
Write:  [────────── 1000 bytes ──────────]

Read:   Call 1: [─── 536 bytes ───]       (TCP MSS fragment)
        Call 2: [── 400 bytes ──]          (next fragment)
        Call 3: [─ 64 bytes ─]            (remaining)
```

The loop ensures all `Content-Length` bytes are read, even if they arrive across multiple TCP segments. Without this loop, a large POST body could be truncated.

**Line 41: ISO-8859-1 encoding — the binary data preservation trick.**

```
The problem:
  HTTP bodies can contain binary data (images, protobuf, etc.)
  Java's String stores characters, not bytes
  We need: bytes → String → bytes with ZERO data loss

UTF-8 would corrupt binary data:
  Input:    [0xFF, 0x8A]
  UTF-8:    INVALID lead bytes → replaced with U+FFFD → [0xEF, 0xBF, 0xBD, 0xEF, 0xBF, 0xBD]
  Result:   2 bytes in, 6 bytes out. Data corrupted.

ISO-8859-1 preserves binary data perfectly:
  Input:    [0xFF, 0x8A]
  ISO-8859: [ÿ, Š]        ← Every byte value 0x00-0xFF has a valid character
  Encode:   [0xFF, 0x8A]    ← Identical to input. Zero data loss.
```

This is validated by `testPostBinaryEcho()` in the integration test suite, which sends bytes including `0x00` (null), `0xFF` (max), and `0x8A` (high bit) — and asserts they survive the round-trip.

**Validated edge cases (return `null`, connection closed gracefully):**
- `Content-Length: -1` → rejected before `new byte[]` allocation
- `Content-Length: 2000000000` → rejected when exceeding `MAX_BODY_SIZE` (10 MB)
- `Content-Length: abc` → caught by `NumberFormatException` handler

**Remaining limitation:** No `Content-Length` but body present → body is silently ignored (common with `Transfer-Encoding: chunked`, which isn't supported).

#### Step 4: Construct and Return (Line 85)

```java
    return new HttpRequest(httpMethod, path, version, headers, body, clientIp);
```

All parsed components are assembled into an immutable `HttpRequest` object.

---

### `readLine(InputStream)` — Byte-by-Byte Line Reader (Lines 88–107)

```java
private static String readLine(InputStream in) throws IOException {
    StringBuilder sb = new StringBuilder();
    int c;
    while ((c = in.read()) != -1) {
        if (c == '\r') {
            int next = in.read();
            if (next == '\n') break;
        } else if (c == '\n') {
            break;
        } else {
            sb.append((char) c);
            if (sb.length() > MAX_LINE_LENGTH) {               // Line 100: OOM defense
                throw new IOException("Header line exceeds maximum length of " + MAX_LINE_LENGTH + " bytes");
            }
        }
    }
    if (sb.length() == 0 && c == -1) return null;
    return sb.toString();
}
```

**Why byte-by-byte instead of `BufferedReader`?**

`BufferedReader` has a critical problem for HTTP parsing: **it over-reads the stream.** `BufferedReader` fills an 8 KB internal buffer on each read operation. When parsing headers (which are typically ~200 bytes), it would consume body bytes into its buffer — making the subsequent `Content-Length`-based body read incorrect.

```
Example problem with BufferedReader:
  Request:  "GET / HTTP/1.1\r\nContent-Length: 5\r\n\r\nHello"

  BufferedReader.readLine() fills 8KB buffer:
    Buffer: "GET / HTTP/1.1\r\nContent-Length: 5\r\n\r\nHello"
    Returns: "GET / HTTP/1.1"
    The body bytes "Hello" are now INSIDE BufferedReader's buffer.

  Later: inputStream.read(bodyBytes, 0, 5)
    Reads from the RAW stream → but "Hello" is already consumed!
    Returns: -1 (no more data) or blocks forever.
```

The byte-by-byte approach reads exactly the bytes it needs — nothing more. Each `readLine()` call stops precisely at the `\r\n`, leaving all subsequent bytes untouched in the kernel's socket buffer.

**Performance cost:**
```
Each in.read() on a SocketInputStream may trigger a system call (JNI → kernel).
For a typical request with 200 bytes of headers: ~200 system calls.

At 5,800 RPS:
  200 bytes × 5,800 requests = 1,160,000 system calls/second just for headers
  At ~100ns per syscall: ~116ms total CPU time per second
  On a 4-core machine: ~2.9% of one core — acceptable but not optimal.

Optimization (not implemented): Use a position-tracking buffer that reads in
chunks but tracks the exact read position, allowing both header and body
parsing to share the same buffer without over-reading.
```

**Line 53–55: CRLF handling.** The HTTP spec mandates `\r\n` as line terminators. However, some clients send bare `\n` (without `\r`). The parser tolerates both:
- `\r\n` → standard CRLF → handled on Lines 51-53
- `\n` → bare LF → handled on Lines 54-55
- `\r` followed by non-`\n` → the `\r` is consumed but the next byte is lost (minor bug — extremely rare in practice)

**Line 60: Stream-closed detection.** When `in.read()` returns `-1` (EOF), and no data was accumulated, `readLine()` returns `null`. This signals to `parseRequest()` that the connection was closed or empty.

**Line length limit (Lines 100–102):** Each line is capped at 8 KB (`MAX_LINE_LENGTH`). Exceeding this throws `IOException`, which propagates to `RequestProcessor` and closes the connection. This prevents Slowloris-style attacks that send infinitely long header lines without CRLF.

---

## Parsing Flow Diagram

```
Raw bytes from socket:
  "GET /api/status HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nHello"

Step 1: readLine()
  ├── Read byte-by-byte: G, E, T, ' ', /, a, p, i, /, s, t, a, t, u, s, ' ', H, T, T, P, ...
  ├── Hit \r, peek \n → line complete
  └── Return: "GET /api/status HTTP/1.1"

  split(" ") → method=GET, path=/api/status, version=HTTP/1.1

Step 2: readLine() loop
  ├── "Host: localhost" → split(":", 2) → headers["Host"] = "localhost"
  ├── "Content-Length: 5" → split(":", 2) → headers["Content-Length"] = "5"
  └── "" (empty line) → loop exits

Step 3: Content-Length present → read(bodyBytes, 0, 5)
  ├── Kernel may deliver in 1 or more read() calls
  ├── Loop until 5 bytes accumulated
  └── body = new String([H,e,l,l,o], ISO-8859-1) → "Hello"

Step 4: return HttpRequest(GET, "/api/status", "HTTP/1.1",
                           {"Host":"localhost","Content-Length":"5"},
                           "Hello", "127.0.0.1")
```

---

## Key Design Notes

- **Input validation:** Malformed request lines, unsupported methods, invalid `Content-Length`, oversized bodies, and overlong header lines all return `null` or throw `IOException` — never crash the worker thread.
- **No chunked transfer encoding:** Only `Content-Length` based body reading. `Transfer-Encoding: chunked` is not implemented. Clients must send `Content-Length` for POST/PUT bodies.
- **ISO-8859-1 for binary safety:** Every byte maps to exactly one character. UTF-8 would corrupt bytes >0x7F.
- **Byte-by-byte reading:** Simple and correct, but ~200× slower than buffered reading for headers. Acceptable at current throughput.
- **Static method:** `parseRequest()` is stateless — multiple threads can call it simultaneously without contention.
- **No HTTP/2 support:** HTTP/2 uses a binary frame format (not text-based). This parser only handles HTTP/1.1 text protocol.
