# 📄 `RouteScanner.java` — Classpath Scanner

**Package:** `com.Shreyansh.webserver.core`  
**Path:** `src/main/java/com/Shreyansh/webserver/core/RouteScanner.java`  
**Role:** Scans a Java package tree at runtime to discover classes annotated with `@RestController`, instantiates them, and registers their route methods into the Router.

---

## File Overview

`RouteScanner` is the **auto-discovery engine** — the component that makes `@RestController` / `@GetMapping` annotations work. Without it, every controller would need to be manually registered in `Main.java`. With it, you annotate a class and the framework finds it.

This is the same pattern used by Spring Boot's `@ComponentScan`. The key difference: Spring uses a complex classpath scanning library (ASM-based bytecode analysis). We use plain Java reflection, which is simpler but demonstrates the same concept.

---

## How Classpath Scanning Works

### The Challenge

At runtime, Java doesn't provide a built-in "list all classes in package X" API. The `ClassLoader` can load a class by name (`Class.forName("com.Shreyansh.webserver.controllers.DemoController")`), but it cannot enumerate what's available. We have to do this manually.

### Dual-Mode Resolution

The scanner must work in two completely different environments:

```
Development (IDE / ./gradlew run):
  Classes are on disk as individual .class files:
    build/classes/java/main/com/Shreyansh/webserver/controllers/DemoController.class
  Strategy: Recursive File.listFiles() over the directory tree

Production (java -jar app.jar):
  Classes are inside the JAR archive:
    jar:file:///app.jar!/com/Shreyansh/webserver/controllers/DemoController.class
  Strategy: JarFile.entries() — iterate all entries, filter by package prefix
```

**Detection mechanism:** The scanner calls `ClassLoader.getResource(packagePath)` and checks the returned URL's protocol:
- `"file"` → filesystem mode
- `"jar"` → JAR mode

---

## Line-by-Line Explanation

### Field and Constructor (Lines 11–16)

```java
public class RouteScanner {                                    // Line 11
    private final Router router;                               // Line 12: Target for route registration

    public RouteScanner(Router router) {                       // Line 14
        this.router = router;                                  // Line 15
    }
```

### `scan(String basePackage)` — Entry Point (Lines 18–36)

```java
    public void scan(String basePackage) {                     // Line 18
        String path = basePackage.replace('.', '/');            // Line 19: "com.Shreyansh.webserver" → "com/Shreyansh/webserver"
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();  // Line 20
        URL resource = classLoader.getResource(path);          // Line 21
```

**Line 19:** Java packages use dots (`com.Shreyansh.webserver`), but filesystem paths and JAR entries use slashes (`com/Shreyansh/webserver`). This conversion is required for `ClassLoader.getResource()`.

**Line 20:** Uses the **context class loader** instead of `RouteScanner.class.getClassLoader()`. The context class loader is set by the thread's creator and is typically the application class loader — the one that loaded the main class. This is important when running inside containers (Tomcat, etc.) where class loaders form a hierarchy.

```java
        if (resource == null) {                                // Line 23
            System.out.println("No resource at " + path);      // Line 24
            return;                                            // Line 25
        }
        String protocol = resource.getProtocol();              // Line 27

        if (protocol.equals("file")) {                         // Line 29
            File directory = new File(resource.getPath());     // Line 30
            scanDirectory(directory, basePackage);              // Line 31: Filesystem mode
        } else if (protocol.equals("jar")) {                   // Line 33
            scanJar(resource, basePackage);                     // Line 34: JAR mode
        }
    }
```

### `scanDirectory(File, String)` — Filesystem Mode (Lines 38–54)

```java
    private void scanDirectory(File directory, String basePackage) {  // Line 38
        if (!directory.exists()) return;                        // Line 39
        File[] files = directory.listFiles();                   // Line 40
        if (files == null) return;                              // Line 41

        for (File file : files) {                              // Line 43
            if (file.isDirectory()) {                          // Line 44
                scanDirectory(file, basePackage + "." + file.getName());  // Line 45: Recurse
            } else if (file.getName().endsWith(".class")) {    // Line 47
                String className = basePackage + "." + file.getName().replace(".class", "");  // Line 48
                processClass(className);                       // Line 49: Load + inspect
            }
        }
    }
```

**Line 45: Recursive descent.** For each subdirectory, the scanner recurses with an updated package name. For directory `controllers/` under base package `com.Shreyansh.webserver`, the recursive call becomes `scanDirectory(controllersDir, "com.Shreyansh.webserver.controllers")`.

**Line 48:** Constructs the fully-qualified class name by replacing `.class` extension and prepending the package. `DemoController.class` in package `com.Shreyansh.webserver.controllers` becomes `com.Shreyansh.webserver.controllers.DemoController`.

**Complexity:** O(F) where F = total files (including non-.class) in the package tree. For this project with ~20 files: <1ms.

### `scanJar(URL, String)` — JAR Mode (Lines 56–76)

```java
    private void scanJar(URL resource, String basePackage) {   // Line 56
        try {
            String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));  // Line 58
            JarFile jar = new JarFile(jarPath);                // Line 59
```

**Line 58: Extracting the JAR path.** A JAR resource URL looks like:
```
jar:file:///C:/path/to/app.jar!/com/Shreyansh/webserver
          ^^^^^^^^^^^^^^^^^^^^^
          We extract this part (after "file:" at position 5, before "!")
```

```java
            Enumeration<JarEntry> entries = jar.entries();     // Line 60
            while (entries.hasMoreElements()) {                // Line 61
                JarEntry entry = entries.nextElement();         // Line 62
                String name = entry.getName();                 // Line 63

                if (name.endsWith(".class") && name.startsWith(basePackage.replace('.', '/'))) {
                    String className = name.replace("/", ".").replace(".class", "");  // Line 66
                    processClass(className);                   // Line 67
                }
            }
```

**Line 64: Double filter.** An entry must:
1. End with `.class` (skip non-class files like images, manifests)
2. Start with the base package path (skip classes outside our scan scope)

**Performance note:** `jar.entries()` iterates ALL entries in the JAR — not just those in the target package. For a JAR with 1000 entries where only 20 are in `com.Shreyansh.webserver`, 980 entries are scanned and filtered. This is O(E) where E = total JAR entries.

### `processClass(String)` — Load, Check, Register (Lines 78–96)

```java
    private void processClass(String className) {              // Line 78
        try {
            Class<?> clas = Class.forName(className);          // Line 80: Load class into JVM
```

**Line 80: `Class.forName()`** loads the class bytecode, runs its static initializer, and returns the `Class<?>` object. Cost: ~50µs for the first call (bytecode verification), ~1µs for cached classes.

```java
            if (clas.isAnnotationPresent(RestController.class)) {  // Line 82: Check marker annotation
                Object controller = clas.getDeclaredConstructor().newInstance();  // Line 83: Instantiate
                router.registerController(controller);         // Line 84: Register routes
                System.out.println("Found Controller: " + className);
            }
```

**Line 82:** `isAnnotationPresent()` checks if `@RestController` (with `@Retention(RUNTIME)`) is present on the class. This is why `RUNTIME` retention is critical — without it, the annotation would be stripped at compile time and invisible to reflection.

**Line 83: `getDeclaredConstructor().newInstance()`** — instantiates the controller via its no-arg constructor. This is equivalent to `new DemoController()` but done reflectively. If the controller has no no-arg constructor, this throws `NoSuchMethodException`.

**Line 84:** Delegates to `Router.registerController()` which scans the controller's methods for `@GetMapping` / `@PostMapping` and registers them in the trie.

---

## The Complete Startup Discovery Flow

```
Main.main()
  └── server.scanAndStart("com.Shreyansh.webserver")
        └── routeScanner.scan("com.Shreyansh.webserver")
              │
              ├── ClassLoader.getResource("com/Shreyansh/webserver")
              │     └── Returns URL (file:// or jar://)
              │
              ├── scanDirectory() or scanJar()
              │     ├── Finds: Main.class                     → processClass → no @RestController → skip
              │     ├── Finds: Server.class                   → processClass → no @RestController → skip
              │     ├── Finds: HttpParser.class                → processClass → no @RestController → skip
              │     ├── Finds: DemoController.class            → processClass → HAS @RestController!
              │     │     └── newInstance() → DemoController object
              │     │     └── router.registerController(controller)
              │     │           ├── Finds: getStatus() with @GetMapping("/api/status")
              │     │           │     └── addRoute(GET, "/api/status", λ → method.invoke(controller))
              │     │           └── (no more annotated methods)
              │     ├── Finds: RateLimiter.class               → processClass → no @RestController → skip
              │     └── ... (all other classes: skip)
              │
              └── Trie now contains: root → api → status → {GET: handler}
```

---

## Key Design Notes

- **Dual-mode scanning:** Filesystem (development) + JAR (production). Detected automatically via URL protocol.
- **No-arg constructor required:** Controllers must have a public no-argument constructor for `newInstance()`. Constructor injection is not supported.
- **Single instance per controller:** Each controller is instantiated once during scanning. All routes from that controller share the same instance. If a controller has mutable state, it must be thread-safe.
- **Package-recursive:** Scanning discovers classes in the base package AND all sub-packages (e.g., `com.Shreyansh.webserver.controllers` is a sub-package of `com.Shreyansh.webserver`).
- **Startup-only:** Scanning runs once during `scanAndStart()`. No hot-reloading or runtime re-scanning.
