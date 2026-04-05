package com.Shreyansh.webserver.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StaticFileHandler {
    private final String staticDirectory = "src/main/resources";
    private final LRUCache cache;

    public StaticFileHandler(LRUCache cache) {
        this.cache = cache;
    }

    public LRUCache.cachedFile get(String requestPath) throws IOException {
        LRUCache.cachedFile cachedFile = cache.get(requestPath);
        if (cachedFile != null) {
            System.out.println("cache hit - Served the file from cache: " + requestPath);
            return cachedFile;
        }

        String relativePath = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;

        // 1. Directory Traversal Security Guard
        if (relativePath.contains("..")) {
            throw new SecurityException("Unauthorized access attempt: " + requestPath);
        }

        byte[] fileBytes = null;

        // 2. Try Local Filesystem execution (For VSCode/IntelliJ environments)
        Path root = Paths.get(staticDirectory);
        Path resolvedPath = root.resolve(relativePath).normalize();
        
        if (Files.exists(resolvedPath) && !Files.isDirectory(resolvedPath) && resolvedPath.startsWith(root)) {
            System.out.println("cache miss - Served the file from hard disk: " + requestPath);
            fileBytes = Files.readAllBytes(resolvedPath);
        } else {
            // 3. Try Native Classpath execution (For zipped .JAR execution or CI/CD pipelines)
            // Anything inside src/main/resources gets bundled at the root of the classpath
            try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(relativePath)) {
                if (is != null) {
                    System.out.println("cache miss - Served the file from JAR classpath: " + requestPath);
                    fileBytes = is.readAllBytes();
                }
            }
        }

        if (fileBytes == null) {
            return null;
        }

        String contentType = determineContentType(requestPath);
        cache.put(requestPath, fileBytes, contentType);

        return new LRUCache.cachedFile(fileBytes, contentType);
    }

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
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }
}
