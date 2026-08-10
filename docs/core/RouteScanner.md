# 📄 `RouteScanner.java` — Annotation-Based Controller Discovery

**Package:** `com.Shreyansh.webserver.core`  
**Path:** `src/main/java/com/Shreyansh/webserver/core/RouteScanner.java`  
**Role:** Scans a Java package at runtime to discover classes annotated with `@RestController` and registers them with the router.

---

## File Overview

`RouteScanner` implements **classpath scanning** — a technique used by frameworks like Spring Boot to automatically discover and register components. It:

1. Converts a package name to a file/resource path.
2. Determines if the code is running from a **directory** (development) or a **JAR** (production).
3. Recursively finds all `.class` files in the package.
4. Loads each class, checks for `@RestController`, and registers annotated classes with the `Router`.

---

## Line-by-Line Explanation

### Fields (Lines 15–19)

```java
public class RouteScanner {                                    // Line 15
    private final Router router;                               // Line 16: The router to register discovered controllers with

    public RouteScanner(Router router) {                       // Line 18
        this.router = router;                                  // Line 19
    }
```

### `scan(String basePackage)` — Entry Point (Lines 22–56)

```java
    public void scan(String basePackage) {                     // Line 22
        try {
            String path = basePackage.replace('.', '/');        // Line 24: "com.Shreyansh.webserver" → "com/Shreyansh/webserver"
            ClassLoader loader = Thread.currentThread().getContextClassLoader();  // Line 25: Get the classloader
            URL url = loader.getResource(path);                // Line 26: Find the resource (directory or JAR entry)
            if (url == null) {                                 // Line 27
                System.out.println("Can't find resource " + path);  // Line 28
                return;                                        // Line 29
            }
```

**Line 24**: Converts the Java package name to a path format (dots → slashes).

**Line 25–26**: Uses the thread's `ClassLoader` to locate the package as a resource. This works for both filesystem directories and JAR entries.

#### JAR Mode (Lines 32–44)

```java
            if ("jar".equals(url.getProtocol())) {             // Line 32: Running from a JAR file?
                JarURLConnection connection = (JarURLConnection) url.openConnection();  // Line 33
                try (JarFile jarFile = connection.getJarFile()) {  // Line 34: Open the JAR
                    Enumeration<JarEntry> entries = jarFile.entries();  // Line 35: List all entries in the JAR
                    while (entries.hasMoreElements()) {         // Line 36
                        JarEntry entry = entries.nextElement(); // Line 37
                        String entryName = entry.getName();     // Line 38: e.g., "com/Shreyansh/webserver/controllers/DemoController.class"
                        if (entryName.startsWith(path) && entryName.endsWith(".class")) {  // Line 39: In our package + is a class?
                            String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');  // Line 40
                            processClass(className);            // Line 41: Load and check the class
                        }
                    }
                }
            }
```

**Line 40**: Converts `com/Shreyansh/webserver/controllers/DemoController.class` → `com.Shreyansh.webserver.controllers.DemoController` by removing `.class` (last 6 chars) and replacing `/` with `.`.

#### Filesystem Mode (Lines 45–52)

```java
            } else {                                           // Line 45: Running from a directory (development)
                String directoryPath = URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8);  // Line 46
                File directory = new File(directoryPath);      // Line 47

                if (directory.exists()) {                      // Line 49
                    scanDirectory(directory, basePackage);      // Line 50: Recursively scan the directory
                }
            }
```

**Line 46**: `URLDecoder.decode()` handles spaces and special characters in file paths (e.g., `%20` → space).

### `scanDirectory(File, String)` — Recursive Directory Walk (Lines 58–70)

```java
    private void scanDirectory(File directory, String basePackage) {  // Line 58
        File[] files = directory.listFiles();                  // Line 59: List all files and subdirectories
        if (files == null) { return; }                         // Line 60: Empty directory or permission error
        for (File file : files) {                              // Line 61
            if (file.isDirectory()) {                          // Line 62: Subdirectory → recurse
                scanDirectory(file, basePackage + "." + file.getName());  // Line 63: Append directory name to package
            }
            else if (file.getName().endsWith(".class")) {      // Line 65: .class file → potential controller
                String className = basePackage + "." + file.getName().substring(0, file.getName().length() - 6);  // Line 66
                processClass(className);                       // Line 67
            }
        }
    }
```

**Recursive traversal**: For each entry in the directory:
- **Subdirectory** → recurse with an appended package name (e.g., `com.Shreyansh.webserver` + `controllers` → `com.Shreyansh.webserver.controllers`).
- **`.class` file** → strip `.class` extension, build fully qualified class name, and process it.

### `processClass(String)` — Load and Register (Lines 71–82)

```java
    private void processClass(String className) {              // Line 71
        try {
            Class<?> clas = Class.forName(className);          // Line 73: Load the class into the JVM
            if (clas.isAnnotationPresent(RestController.class)) {  // Line 74: Has @RestController?
                Object controller = clas.getDeclaredConstructor().newInstance();  // Line 75: Create instance via no-arg constructor
                router.registerController(controller);         // Line 76: Register with the router
            }
        }
        catch (Exception e) {                                 // Line 78
            System.err.println("Skipping class " + e.getMessage());  // Line 80
        }
    }
```

**Line 73**: `Class.forName()` dynamically loads the class by its fully qualified name.

**Line 74**: Checks if the class has `@RestController`. Non-controller classes (like `LRUCache`, `HttpParser`, etc.) are silently skipped.

**Line 75**: Instantiates the controller using its **no-argument constructor** via reflection. This means all `@RestController` classes must have a public no-arg constructor.

**Line 76**: Delegates to `Router.registerController()` which inspects the controller's methods for `@GetMapping` and `@PostMapping` annotations and registers them as routes.

---

## Scanning Flow

```
scan("com.Shreyansh.webserver")
  │
  ├── Convert: "com.Shreyansh.webserver" → "com/Shreyansh/webserver"
  │
  ├── ClassLoader.getResource("com/Shreyansh/webserver")
  │
  ├── Protocol = "file"? (development)
  │     └── scanDirectory(directory, "com.Shreyansh.webserver")
  │           ├── /annotations/
  │           │     ├── GetMapping.class → processClass() → no @RestController → skip
  │           │     └── ...
  │           ├── /controllers/
  │           │     └── DemoController.class → processClass() → HAS @RestController → register!
  │           └── ...
  │
  └── Protocol = "jar"? (production)
        └── Iterate JAR entries → same logic
```

---

## Key Design Notes

- **No-arg constructor required**: All `@RestController` classes must have a public no-argument constructor because `getDeclaredConstructor().newInstance()` is used.
- **Error tolerance**: If a class fails to load or instantiate, it's silently skipped with a log message. This prevents one bad class from breaking the entire server.
- **Dual-mode**: Supports both filesystem (development) and JAR (production) environments.
- **Package-scoped**: Only scans within the specified base package and its sub-packages. Classes outside this package are never inspected.
