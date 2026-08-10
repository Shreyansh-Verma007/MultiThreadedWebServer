# 📄 `HttpParser.java` — HTTP Request Parser

**Package:** `com.Shreyansh.webserver.http`  
**Path:** `src/main/java/com/Shreyansh/webserver/http/HttpParser.java`  
**Role:** Parses raw HTTP/1.1 request bytes from an `InputStream` into a structured `HttpRequest` object.

---

## File Overview

`HttpParser` is a **hand-written HTTP/1.1 request parser**. It reads raw bytes from a TCP socket's input stream and extracts:
- The request line (method, path, HTTP version)
- All headers (key-value pairs)
- The request body (if `Content-Length` is present)

This parser does **not** use any HTTP library — it implements the HTTP/1.1 protocol directly.

---

## HTTP Request Format (for reference)

```
GET /api/status HTTP/1.1\r\n         ← Request line
Host: localhost:8080\r\n             ← Header
Content-Type: application/json\r\n   ← Header
\r\n                                 ← Empty line (end of headers)
{"key": "value"}                     ← Body (optional)
```

---

## Line-by-Line Explanation

### `parseRequest(InputStream, String)` — Main Parser (Lines 9–45)

```java
public static HttpRequest parseRequest(InputStream inputStream, String clientIp) throws IOException {  // Line 9
    HttpMethod httpMethod;                                     // Line 10
    String path;                                               // Line 11
    String version;                                            // Line 12
    Map<String, String> headers = new HashMap<>();             // Line 13: Header storage
    String body = "";                                          // Line 14: Default empty body
```

#### Step 1: Parse the Request Line (Lines 16–24)

```java
    String firstLine = readLine(inputStream);                  // Line 16: Read first line (e.g., "GET /api/status HTTP/1.1")

    if (firstLine == null || firstLine.trim().isEmpty()) {     // Line 18: Empty or no data
        return null;                                           // Line 19: Invalid request
    }
    String[] line1 = firstLine.split(" ");                     // Line 21: Split by space → ["GET", "/api/status", "HTTP/1.1"]
    httpMethod = HttpMethod.valueOf(line1[0]);                  // Line 22: "GET" → HttpMethod.GET
    path = line1[1];                                           // Line 23: "/api/status"
    version = line1[2];                                        // Line 24: "HTTP/1.1"
```

**`HttpMethod.valueOf(line1[0])`**: Converts the string method name to the `HttpMethod` enum. If the method is unsupported (e.g., `"CONNECT"`), this throws `IllegalArgumentException`.

#### Step 2: Parse Headers (Lines 26–30)

```java
    String line2;
    while ((line2 = readLine(inputStream)) != null && !line2.isEmpty()) {  // Line 27: Read until empty line
        String[] parts = line2.split(":", 2);                  // Line 28: Split on first ":" only (value may contain ":")
        headers.put(parts[0].trim(), parts[1].trim());         // Line 29: Store key=value (trimmed)
    }
```

**Line 27**: Headers end when an empty line (`\r\n\r\n`) is encountered. The `readLine()` method returns an empty string for the blank line separator.

**Line 28**: `split(":", 2)` splits into at most 2 parts. This is important because header values can contain colons (e.g., `Host: localhost:8080`).

#### Step 3: Parse Body (Lines 32–42)

```java
    if (headers.containsKey("Content-Length")) {               // Line 32: Body only if Content-Length is present
        int length = Integer.parseInt(headers.get("Content-Length").trim());  // Line 33: Parse body length
        byte[] bodyBytes = new byte[length];                   // Line 34: Allocate buffer
        int bytesRead = 0;                                     // Line 35
        while (bytesRead < length) {                           // Line 36: Read loop (data may arrive in chunks)
            int read = inputStream.read(bodyBytes, bytesRead, length - bytesRead);  // Line 37
            if (read == -1) break;                             // Line 38: Stream ended prematurely
            bytesRead += read;                                 // Line 39
        }
        body = new String(bodyBytes, java.nio.charset.StandardCharsets.ISO_8859_1);  // Line 41
    }
```

**Line 36–39**: A read loop is necessary because `inputStream.read()` is not guaranteed to return all requested bytes at once. TCP may deliver data in chunks.

**Line 41**: Uses **ISO-8859-1** encoding (not UTF-8) to convert body bytes to a string. ISO-8859-1 is a 1:1 mapping of byte values 0-255, which means **no data is lost during the conversion** — even for binary payloads. This is crucial for binary data integrity (tested in `ServerIntegrationTest.testPostBinaryEcho`).

#### Step 4: Construct and Return (Line 44)

```java
    return new HttpRequest(httpMethod, path, version, headers, body, clientIp);  // Line 44
```

### `readLine(InputStream)` — Line Reader (Lines 47–62)

```java
private static String readLine(InputStream in) throws IOException {  // Line 47
    StringBuilder sb = new StringBuilder();                    // Line 48
    int c;                                                     // Line 49
    while ((c = in.read()) != -1) {                            // Line 50: Read one byte at a time
        if (c == '\r') {                                       // Line 51: Carriage return
            int next = in.read();                              // Line 52: Peek at next byte
            if (next == '\n') break;                           // Line 53: Found \r\n → end of line
        } else if (c == '\n') {                                // Line 54: Bare \n (non-standard but tolerated)
            break;                                             // Line 55
        } else {                                               // Line 56
            sb.append((char) c);                               // Line 57: Append character to line
        }
    }
    if (sb.length() == 0 && c == -1) return null;              // Line 60: Stream ended with no data → null
    return sb.toString();                                      // Line 61: Return the line
}
```

**HTTP line terminator**: The HTTP spec requires `\r\n` (CRLF) as line endings. This implementation:
- Properly handles `\r\n` (standard)
- Also tolerates bare `\n` (non-standard but common)

**Line 60**: Returns `null` only if the stream ended (`c == -1`) and no data was read. This signals to `parseRequest()` that the connection was closed or empty.

---

## Parsing Flow

```
Raw bytes from socket:
"GET /api/status HTTP/1.1\r\nHost: localhost:8080\r\nContent-Length: 5\r\n\r\nHello"

Step 1: readLine() → "GET /api/status HTTP/1.1"
        split(" ") → method=GET, path=/api/status, version=HTTP/1.1

Step 2: readLine() → "Host: localhost:8080"
        split(":", 2) → headers["Host"] = "localhost:8080"
        readLine() → "Content-Length: 5"
        split(":", 2) → headers["Content-Length"] = "5"
        readLine() → "" (empty line = end of headers)

Step 3: Content-Length=5 → read 5 bytes → body="Hello"

Step 4: return HttpRequest(GET, "/api/status", "HTTP/1.1", headers, "Hello", "127.0.0.1")
```

---

## Key Design Notes

- **No chunked transfer encoding**: Only `Content-Length` based body reading is supported. Chunked encoding (`Transfer-Encoding: chunked`) is not implemented.
- **ISO-8859-1 for body**: This preserves binary data integrity since every byte maps to exactly one character.
- **Single-byte reading for lines**: `readLine()` reads one byte at a time. This is not the most efficient approach but is simple and correct for HTTP parsing where headers are typically small.
- **Static method**: `parseRequest()` is `static` — it doesn't need any instance state.
