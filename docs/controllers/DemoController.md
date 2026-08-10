# 📄 `DemoController.java` — Example REST Controller

**Package:** `com.Shreyansh.webserver.controllers`  
**Path:** `src/main/java/com/Shreyansh/webserver/controllers/DemoController.java`  
**Role:** A sample controller demonstrating how to create API endpoints using the custom annotation-based routing framework.

---

## File Overview

`DemoController` is a **demonstration controller** that shows how to use the custom `@RestController` and `@GetMapping` annotations to define HTTP API endpoints. It serves as a reference implementation for building new controllers.

This class is automatically discovered by the `RouteScanner` at server startup because it is annotated with `@RestController` and resides within the scanned package (`com.Shreyansh.webserver`).

---

## Line-by-Line Explanation

```java
package com.Shreyansh.webserver.controllers;                   // Line 1: Package declaration
```

```java
import com.Shreyansh.webserver.annotations.RestController;     // Line 3: Import the controller marker annotation
import com.Shreyansh.webserver.annotations.GetMapping;          // Line 4: Import the GET route annotation
import com.Shreyansh.webserver.http.HttpRequest;                // Line 5: Import the request object
import com.Shreyansh.webserver.http.HttpResponse;               // Line 6: Import the response object
```

```java
@RestController                                                // Line 8: Marks this class as a controller
public class DemoController {                                  // Line 9
```
The `@RestController` annotation tells the `RouteScanner` to inspect this class's methods for route annotations during startup.

### `getStatus(HttpRequest)` — GET /api/status (Lines 12–20)

```java
    // Demonstrating the custom @GetMapping annotation!        // Line 11: Explanatory comment
    @GetMapping("/api/status")                                 // Line 12: Maps GET /api/status to this method
    public HttpResponse getStatus(HttpRequest request) {       // Line 13: Handler method signature
```
**Method signature contract:** Handler methods must:
- Accept a single `HttpRequest` parameter
- Return an `HttpResponse`

```java
        String jsonResponse = "{\"status\": \"Online\", \"framework\": \"MultithreadedWebServer v1.0\", \"activeThreads\": 12}";  // Line 14
```
Builds a hardcoded JSON string as the response body. (In a production app, this would be dynamically generated.)

```java
        HttpResponse response = new HttpResponse();            // Line 16: Create a new response (defaults to 200 OK)
        response.addHeaders("Content-Type", "application/json");  // Line 17: Set the content type to JSON
        response.setBody(jsonResponse);                        // Line 18: Set the response body
        return response;                                       // Line 19: Return the response to the framework
    }
```

---

## Example Request/Response

**Request:**
```
GET /api/status HTTP/1.1
Host: localhost:8080
```

**Response:**
```
HTTP/1.1 200 Ok
Content-Type: application/json
Content-Length: 76

{"status": "Online", "framework": "MultithreadedWebServer v1.0", "activeThreads": 12}
```

---

## How to Create Your Own Controller

1. Create a new class in any sub-package of `com.Shreyansh.webserver`.
2. Annotate the class with `@RestController`.
3. Add methods with `@GetMapping("/your/path")` or `@PostMapping("/your/path")`.
4. Each method must accept `HttpRequest` and return `HttpResponse`.

```java
@RestController
public class MyController {
    @GetMapping("/api/hello")
    public HttpResponse sayHello(HttpRequest request) {
        HttpResponse response = new HttpResponse();
        response.addHeaders("Content-Type", "text/plain");
        response.setBody("Hello, World!");
        return response;
    }
    
    @PostMapping("/api/data")
    public HttpResponse receiveData(HttpRequest request) {
        HttpResponse response = new HttpResponse();
        response.setBody("Received: " + request.getBody());
        return response;
    }
}
```
