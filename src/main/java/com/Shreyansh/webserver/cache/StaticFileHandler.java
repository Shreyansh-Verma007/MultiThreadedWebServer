package com.Shreyansh.webserver.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StaticFileHandler {
    private final String staticDirectory = "src/main/java/resources";
    private final LRUCache cache;

    public StaticFileHandler(LRUCache cache) {
        this.cache = cache;
    }

    public LRUCache.cachedFile get(String requestPath) throws IOException {
        LRUCache.cachedFile cachedFile = cache.get(requestPath);
        if (cachedFile != null) {
            return cachedFile;
        }

        Path root = Paths.get(staticDirectory);

        String relativePath = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
        Path resolvedPath = root.resolve(relativePath).normalize();

        if (!resolvedPath.startsWith(root)) {
            throw new SecurityException("Unauthorized access attempt: " + requestPath);
        }

        if (!Files.exists(resolvedPath) || Files.isDirectory(resolvedPath)) {
            return null;
        }

        byte[] fileBytes = Files.readAllBytes(resolvedPath);
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
