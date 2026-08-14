package com.Shreyansh.webserver.cache;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StaticFileHandler {
    private final String staticDirectory = "src/main/resources";
    private final LRUCache cache;

    // Maximum file size to load into memory (50 MB) — prevents OOM from huge files
    private static final long MAX_FILE_SIZE = 52_428_800;

    public StaticFileHandler(LRUCache cache) {
        this.cache = cache;
    }

    public LRUCache.CachedFile get(String requestPath) throws IOException {
        LRUCache.CachedFile cachedFile = cache.get(requestPath);
        if (cachedFile != null) {
            System.out.println("cache hit - Served the file from cache: " + requestPath);
            return cachedFile;
        }

        String relativePath = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;

        if (relativePath.contains("..")) {
            throw new SecurityException("Unauthorized access attempt: " + requestPath);
        }

        byte[] fileBytes = null;

        Path root = Paths.get(staticDirectory);
        Path resolvedPath = root.resolve(relativePath).normalize();

        if (Files.exists(resolvedPath) && !Files.isDirectory(resolvedPath) && resolvedPath.startsWith(root)) {
            // Reject files that exceed the safety limit
            long fileSize = Files.size(resolvedPath);
            if (fileSize > MAX_FILE_SIZE) {
                System.err.println("File too large to serve (" + fileSize + " bytes): " + requestPath);
                return null;
            }
            System.out.println("cache miss - Served the file from hard disk: " + requestPath);
            fileBytes = Files.readAllBytes(resolvedPath);
        } else {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(relativePath)) {
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

        return new LRUCache.CachedFile(fileBytes, contentType);
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
}
