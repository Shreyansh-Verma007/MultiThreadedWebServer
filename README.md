# 🚀 High-Performance Java Web Server Framework
First Release - v1.0.0

A high-performance, multithreaded web server built entirely from scratch in pure Java. Designed to peel back the abstraction layers of enterprise tools like Spring Boot, this project implements bare-metal networking, advanced algorithmic routing, and robust concurrency control.

## 🎯 Problem Statement & Solution Importance

### The Abstraction Barrier
📊 **Hidden Mechanics:** Modern enterprise frameworks hide the heavy lifting of network sockets, HTTP parsing, and thread life cycles, leaving many developers unfamiliar with the underlying architecture of the web.
💰 **Resource Inefficiency:** Generic "Thread-per-request" models can exhaust JVM memory entirely during traffic spikes, leading to OutOfMemoryError crashes.
🌐 **Static Routing Limits:** Standard O(1) HashMap routing works for static paths but fails gracefully when handling dynamic API segment variables (e.g., `/users/{id}/profile`).

### Our Unique Solution
This custom web server framework addresses these critical gaps through:

🛡️ **Thread Pool Concurrency**
* **Memory Protection:** Utilizes a strict `ExecutorService` fixed thread pool boundary to prevent unbounded memory usage.
* **Non-Blocking Architecture:** Built to safely queue requests during heavy traffic loads, prioritizing system stability over unbounded processing.

🤖 **Algorithmic Routing**
* **Trie-Based Resolution:** Replaces standard HashMaps with a custom Radix/Prefix Trie structure, achieving highly efficient O(K) path lookups.
* **Dynamic Variables:** Safely extracts path variables using pure graph traversal logic without relying on slow regex engines.

🌐 **Architectural Excellence**
* **From-Scratch Protocol Parsing:** Directly handles raw HTTP specification rules (CRLF, HTTP Methods, headers) through a custom parser.
* **IoC & Metaprogramming:** Custom annotations (e.g., `@GetMapping`) using Java Reflection to dynamically auto-register endpoints via Inversion of Control.

## ✨ Features

### 🎯 Core Engine
* **🧵 Multithreaded Request Processing:** Concurrent handling of client TCP sockets via Java's `ExecutorService`.
* **🧩 Object-Oriented HTTP Model:** Transforms raw byte streams into clean, fully-typed `HttpRequest` and `HttpResponse` structured models.
* **🚦 Rate Limiting (Token Bucket):** Custom Token Bucket algorithm logic implemented with `ConcurrentHashMap` to throttle excessive traffic and prevent simulated DDoS events.
* **🛡️ Middleware Pipeline:** Intercepts requests using the Chain of Responsibility design pattern for security checks, authentication, and logging.

### 🧠 Performance & Optimization
* **💾 In-Memory LRU Cache:** Custom Least Recently Used cache (Doubly Linked List + HashMap) for O(1) constant-time reads & writes of static files.
* **⚡ Algorithmic Path Trees:** Segments and parses complex variable-driven REST API requests efficiently using a custom Trie Node system.
* **🔌 NIO Ready:** Pluggable design prepared for Java NIO (New I/O) adoption to manage thousands of active connections concurrently.

## 🧱 Tech Stack

**Language & Core APIs**
* ☕ **Java 17+** - Pure Core Java implementation with strictly no external dependencies.
* 📘 **Java Socket I/O** - Under-the-hood raw TCP network streams and Buffer handling.
* 🧬 **Java Reflection API** - Runtime class inspection and annotation scanning.
* 🚦 **Java Concurrency Utilities** - `java.util.concurrent` package (Executors, HashMaps, Locks).

**Build & Tooling**
* 🐘 **Gradle** - Automation and dependency management.

## 🚀 Getting Started

### Prerequisites
* Java JDK 17 or higher (check with `java -version`)
* Gradle (check with `gradle -v`)

### Quick Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/Shreyansh-iittirupati/MultithreadedWebServer.git
   cd MultithreadedWebServer
   ```

2. **Build the framework**
   ```bash
   gradlew build
   ```

3. **Start the Development Server**
   ```bash
   gradlew run
   ```
   *Open http://localhost:8080 🎉*

## 📂 Project Structure

```text
MultithreadedWebServer/
├── src/main/java/com/shreyansh/webserver/
│   ├── Main.java                        # Server entry point & listener
│   ├── annotations/                     # Metaprogramming framework
│   │   ├── GetMapping.java              
│   │   ├── PostMapping.java             
│   │   ├── RestController.java          
│   │   └── RouteScanner.java            # Reflection auto-registration
│   ├── cache/                           # Memory Management
│   │   ├── LruCache.java                # Doubly Linked List + HashMap
│   │   └── StaticFileHandler.java       # Disk I/O static file serving
│   ├── core/                            # Concurrency Engine
│   │   ├── Server.java                  # Thread Pool setup
│   │   └── RequestProcessor.java        # Runnable client task
│   ├── http/                            # Protocol Data Models
│   │   ├── HttpMethod.java              
│   │   ├── HttpParser.java              # Raw bytes -> HttpRequest
│   │   ├── HttpRequest.java             
│   │   ├── HttpResponse.java            
│   │   └── HttpStatus.java              
│   ├── middleware/                      # Chain of Responsibility
│   │   ├── Filter.java                  
│   │   ├── FilterChain.java             
│   │   └── RateLimiter.java             # Token Bucket limiter
│   └── routing/                         # Algorithmic Engine
│       ├── RouteHandler.java            
│       ├── Router.java                  
│       └── TrieNode.java                # O(K) path resolution tree
├── build.gradle                         # Build configuration
└── .gitignore                           # Ignored framework elements
```

## 🔐 Security & Safety

🚨 **Middleware Defense Protocol**
* **⚡ Token Bucket Tracking:** Real-time IP connection rate-limiting using thread-safe data structures.
* **🚫 Thread Pool Upper Limits:** Restricts connection worker exhaustion to maintain 100% server uptime during surges.
* **📝 Chain of Responsibility Checks:** Forces incoming HTTP traffic to flow through rigorous authentication gates before reaching handler logic.

## 🤝 Contributing

We welcome contributions focused on algorithm optimization, memory safety, and framework capabilities!

### 🎯 Priority Areas
* 🤖 **NIO Upgrades:** Migrating blocking Sockets to asynchronous `SocketChannel` and `Selector`.
* 📊 **HTTP/2 Support:** Extending the parsing engine for modern frame-based HTTP traffic protocols.
* 🧪 **Testing:** Comprehensive unit test suites for edge-case HTTP protocol structures and reflection parsing.

## 📜 License
Copyright (c) 2026 The MultithreadedWebServer Contributors.

📚 **Resources**
* [MDN HTTP Messages Documentation](https://developer.mozilla.org/en-US/docs/Web/HTTP/Messages)
* [Java Concurrency Models](https://docs.oracle.com/javase/tutorial/essential/concurrency/)
* [Trie Data Structures](https://en.wikipedia.org/wiki/Trie)
