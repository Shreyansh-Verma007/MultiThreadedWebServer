# 🚀 High-Performance Java Web Server Framework
<p align="center">
  <em>First Release - v1.0.0</em>
</p>
<p align="center">
  <img src="https://github.com/Shreyansh-Verma007/MultithreadedWebServer/actions/workflows/build.yml/badge.svg" alt="Java CI with Gradle" />
</p>

A high-performance, multithreaded web server built entirely from scratch in pure Java. Designed to peel back the abstraction layers of enterprise tools like Spring Boot, this project implements bare-metal networking, advanced algorithmic routing, and robust concurrency control.

## 🎯 Problem Statement & Solution Importance

### The Abstraction Barrier
- 📊 **Hidden Mechanics:** Modern enterprise frameworks hide the heavy lifting of network sockets, HTTP parsing, and thread life cycles, leaving many developers unfamiliar with the underlying architecture of the web.
- 💰 **Resource Inefficiency:** Generic "Thread-per-request" models can exhaust JVM memory entirely during traffic spikes, leading to `OutOfMemoryError` crashes.
- 🌐 **Static Routing Limits:** Standard O(1) `HashMap` routing works for static paths but fails gracefully when handling dynamic API segment variables.

### Our Unique Solution
This custom web server framework addresses these critical gaps through:

#### 🛡️ Thread Pool Concurrency
- **Memory Protection:** Utilizes a strict `ExecutorService` fixed thread pool boundary to prevent unbounded memory usage.
- **Non-Blocking Architecture:** Built to safely queue requests during heavy traffic loads, prioritizing system stability over unbounded processing.

#### 🤖 Algorithmic Routing
- **Trie-Based Resolution:** Replaces standard `HashMap`s with a custom Trie Node structure, achieving highly efficient O(K) path lookups and separating HTTP Methods (GET/POST) natively.
- **Lambda-Wrapped Reflection:** Safely executes controller methods using Java Reflection wrapped in Lambda functions to abstract messy invocation code from core routing logic.

#### 🌐 Architectural Excellence
- **From-Scratch Protocol Parsing:** Directly handles raw HTTP specification rules, reading HTTP headers and safely parsing HTTP JSON bodies while avoiding TCP Fragmentation deadlocks.
- **IoC & Metaprogramming:** Custom annotations (`@RestController`, `@GetMapping`, `@PostMapping`) use a dynamic `RouteScanner` to auto-register endpoints via Inversion of Control.

## ✨ Features

### 🎯 Core Engine
- 🧵 **Multithreaded Request Processing:** Concurrent handling of client TCP sockets via Java's `ExecutorService`.
- 🧩 **Object-Oriented HTTP Model:** Transforms raw byte streams into clean, fully-typed `HttpRequest` and `HttpResponse` structured models.
- 🚦 **Per-IP Rate Limiting:** Custom Fixed-Window algorithm to throttle excessive traffic per client IP without bottlenecking the main thread.
- 🛡️ **Middleware Pipeline:** Intercepts requests using the Filter Chain pattern for security checks.
- 💾 **In-Memory LRU Cache & Static Loader:** Thread-safe, synchronized Least Recently Used cache mapped to a custom Dual-Mode file loader. Serves pre-packaged resources directly from the JAR classpath in O(1) time without disk-IO bottlenecks.

### 📊 Stress Test Performance
- **The 50k Chaos Test:** Successfully handled a simulated load of 50,000 concurrent GET and POST requests across 100 threads.
- **Throughput:** Maintained a stable throughput of ~5,800 Requests Per Second (RPS) on a local machine.
- **DDoS Resilience:** The custom middleware pipeline successfully identified and blocked 34,000+ rate-limit-exceeding requests in milliseconds with zero server crashes.
- **OS-Level Bottlenecking:** The Java engine proved so efficient that the primary bottleneck became OS-level Ephemeral Port Exhaustion, successfully outpacing the Windows TCP stack.

## 🧱 Tech Stack

### Language & Core APIs
- ☕ **Java 21+** - Pure Core Java implementation with strictly no external dependencies.
- 📘 **Java Socket I/O** - Under-the-hood raw TCP network streams and Buffer handling.
- 🧬 **Java Reflection API** - Runtime class inspection and annotation scanning.
- 🚦 **Java Concurrency Utilities** - `java.util.concurrent` package (Executors, HashMaps, Locks).

### Build & Tooling
- 🐘 **Gradle** - Automation, dependency management, and JAR packaging.

## 🚀 Getting Started

### Prerequisites
- Java JDK 21 or higher (check with `java -version`)
- Gradle (check with `gradle -v`)

### Option 1: Run the Pre-built JAR (Quickest)
1. Download the latest `server-jar` artifact from the **GitHub Actions** tab.
2. Unzip the artifact and open your terminal in that directory.
3. Boot the server using:
   ```bash
   java -jar MultithreadedWebServer-1.0-SNAPSHOT.jar 
   ```

The server comes preloaded with static files to demonstrate the LRU Cache and Dual-Mode JAR Loader. Open your browser and test these endpoints:

- 🌐 **Homepage:** [http://localhost:8080/index.html](http://localhost:8080/index.html)
- 💻 **Tech Image:** [http://localhost:8080/tech.jpg](http://localhost:8080/tech.jpg)
- 🖥️ **PC Image:** [http://localhost:8080/pc.jpg](http://localhost:8080/pc.jpg)

*(Refresh the page to see the server instantly serve these from the RAM cache!)*

### Option 2: Build from Source
1. **Clone the repository**
   ```bash
   git clone https://github.com/Shreyansh-Verma007/MultithreadedWebServer.git
   cd MultithreadedWebServer
   ```

2. **Build the framework**
   ```bash
   ./gradlew build
   ```

3. **Start the Development Server**
   ```bash
   java -jar build/libs/MultithreadedWebServer-1.0-SNAPSHOT.jar 
   ```
   *Open [http://localhost:8080](http://localhost:8080) 🎉*

## 📂 Project Structure

```plaintext
MultithreadedWebServer/
├── src/main/java/com/shreyansh/webserver/
│   ├── Main.java                        # Server entry point & listener
│   ├── annotations/                     # Metaprogramming framework
│   │   ├── GetMapping.java              
│   │   ├── PostMapping.java             
│   │   ├── RestController.java          
│   │   └── RouteScanner.java            # Reflection auto-registration
│   ├── cache/                           # Memory & File Management
│   │   ├── LRUCache.java                # Thread-safe node pointers
│   │   └── StaticFileHandler.java       # Dual-Mode JAR/Disk file loader
│   ├── core/                            # Concurrency Engine
│   │   ├── Server.java                  # Thread Pool setup
│   │   └── RequestProcessor.java        # Runnable client task
│   ├── http/                            # Protocol Data Models
│   │   ├── HttpMethod.java              
│   │   ├── HttpParser.java              # Raw bytes & Headers -> HttpRequest
│   │   ├── HttpRequest.java             
│   │   ├── HttpResponse.java            
│   │   └── HttpStatus.java              
│   ├── middleware/                      # Chain of Responsibility
│   │   ├── Filter.java                  
│   │   ├── FilterChain.java             
│   │   └── RateLimiter.java             # Per-IP Limiter
│   └── routing/                         # Algorithmic Engine
│       ├── RouteHandler.java            
│       ├── Router.java                  
│       └── TrieNode.java                # O(K) path resolution tree
├── src/main/resources/                  # Static Files (index.html, images)
├── build.gradle                         # Build configuration
└── .gitignore                           # Ignored framework elements
```

## 🚧 Roadmap & Upcoming Features
- 🤖 **NIO Upgrades:** Migrating blocking Sockets to asynchronous `SocketChannel` and `Selector`.
- 📊 **HTTP/2 Support:** Extending the parsing engine for modern frame-based HTTP traffic protocols.

## 📜 License
Copyright (c) 2026 The MultithreadedWebServer Contributors.

## 📚 Resources
- [MDN HTTP Messages Documentation](https://developer.mozilla.org/en-US/docs/Web/HTTP/Messages)
- [Java Concurrency Models](https://docs.oracle.com/javase/tutorial/essential/concurrency/)
- [Trie Data Structures](https://en.wikipedia.org/wiki/Trie)