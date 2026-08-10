# 📄 `HttpResponse.java` — HTTP Response Builder

**Package:** `com.Shreyansh.webserver.http`  
**Path:** `src/main/java/com/Shreyansh/webserver/http/HttpResponse.java`  
**Role:** Mutable builder for constructing HTTP/1.1 responses and serializing them to an output stream.

---

## File Overview

`HttpResponse` is a **mutable response builder** that allows controllers and the framework to:
1. Set the HTTP status code
2. Add headers
3. Set the response body (as text or binary)
4. Serialize the entire response to raw HTTP bytes and write it to a socket

Unlike `HttpRequest` (which is immutable), `HttpResponse` is designed to be built incrementally through setter methods.

---

## Line-by-Line Explanation

### Fields (Lines 9–11)

```java
private HttpStatus httpStatus;                                 // Line 9: The response status (200, 404, etc.)
private final Map<String, String> headers;                     // Line 10: Response headers
private byte[] body;                                           // Line 11: Response body as raw bytes
```

### Constructor (Lines 13–17)

```java
public HttpResponse() {                                        // Line 13
    this.httpStatus = HttpStatus.OK;                           // Line 14: Default status is 200 OK
    this.headers = new HashMap<>();                            // Line 15: Empty headers map
    this.body = new byte[0];                                   // Line 16: Empty body
}
```

A new `HttpResponse` defaults to **200 OK** with no headers and an empty body.

### `getStatus()` / `setStatus(HttpStatus)` (Lines 18–21)

```java
public HttpStatus getStatus() { return this.httpStatus; }      // Line 18: Get current status
public void setStatus(HttpStatus httpStatus) {                 // Line 19
    this.httpStatus = httpStatus;                              // Line 20: Override the status
}
```

Used by `RequestProcessor` to check if the router returned a 404 (to trigger static file fallback) and by filters/handlers to set error statuses.

### `addHeaders(String, String)` (Lines 22–24)

```java
public void addHeaders(String key, String value) {            // Line 22
    headers.put(key, value);                                   // Line 23: Add or overwrite a header
}
```

Adds a single response header. Example: `response.addHeaders("Content-Type", "application/json")`.

### `setBody(String)` — Text Body (Lines 26–29)

```java
public void setBody(String body) {                             // Line 26
    this.body = body.getBytes();                               // Line 27: Convert string to bytes (platform default encoding)
    this.headers.put("Content-Length", String.valueOf(this.body.length));  // Line 28: Auto-set Content-Length
}
```

Sets the body from a `String`. **Automatically sets the `Content-Length` header** based on the byte length. Uses the platform's default charset for encoding (typically UTF-8).

### `setBody(byte[], String)` — Binary Body (Lines 31–35)

```java
public void setBody(byte[] body, String contentType) {         // Line 31
    this.body = body;                                          // Line 32: Set raw bytes directly
    this.headers.put("Content-Type", contentType);             // Line 33: Set Content-Type header
    this.headers.put("Content-Length", String.valueOf(this.body.length));  // Line 34: Set Content-Length header
}
```

Sets the body from raw bytes with an explicit content type. Used for:
- Static files (HTML, images, etc.)
- Binary echo in tests

This overload sets **both** `Content-Type` and `Content-Length` automatically.

### `send(OutputStream)` — Serialize & Send (Lines 37–49)

```java
public void send(OutputStream out) throws IOException {        // Line 37
    String statusLine = "HTTP/1.1 " + httpStatus.getCode() + " " + httpStatus.getMessage() + "\r\n";  // Line 38
    out.write(statusLine.getBytes());                          // Line 39: Write status line
```

**Line 38**: Constructs the HTTP status line, e.g., `"HTTP/1.1 200 Ok\r\n"`.

```java
    for (Map.Entry<String, String> entry : headers.entrySet()) {  // Line 41
        String headerLine = entry.getKey() + ": " + entry.getValue() + "\r\n";  // Line 42
        out.write(headerLine.getBytes());                      // Line 43: Write each header
    }
```

**Lines 41–44**: Writes each header as `Key: Value\r\n`.

```java
    out.write("\r\n".getBytes());                              // Line 46: Blank line separating headers from body
    out.write(body);                                           // Line 47: Write the body bytes
    out.flush();                                               // Line 48: Flush all buffered data to the socket
}
```

**Line 46**: The empty `\r\n` line marks the end of headers and start of body per HTTP/1.1 spec.

**Line 48**: `flush()` ensures all bytes are actually sent over the network, not left in any internal buffer.

---

## Example Output

For a JSON API response:
```
HTTP/1.1 200 Ok\r\n
Content-Type: application/json\r\n
Content-Length: 76\r\n
\r\n
{"status": "Online", "framework": "MultithreadedWebServer v1.0", "activeThreads": 12}
```

For a rate-limited response:
```
HTTP/1.1 429 Too Many Requests\r\n
Content-Length: 38\r\n
\r\n
{"error": "IP Rate Limit Exceeded"}
```

---

## Key Design Notes

- **Two `setBody` overloads**: The `String` version is for convenience (controllers use it). The `byte[]` version is for static files and binary data.
- **Auto Content-Length**: Both `setBody` methods automatically set the `Content-Length` header, preventing mismatches.
- **No response code shorthand**: Unlike most HTTP frameworks, there's no `response.ok()` or `response.notFound()`. Status must be set explicitly via `setStatus()`.
- **Not streaming**: The entire body must be in memory as a `byte[]` before calling `send()`. This is fine for typical API responses but could be a limitation for very large files.
