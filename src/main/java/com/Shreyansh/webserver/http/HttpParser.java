package com.Shreyansh.webserver.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class HttpParser {

    // Safety limits to prevent OOM from malicious input
    private static final int MAX_LINE_LENGTH = 8192;       // 8 KB max per header line (same as Apache/Nginx)
    private static final int MAX_BODY_SIZE = 10_485_760;   // 10 MB max body size

    public static HttpRequest parseRequest(InputStream inputStream, String clientIp) throws IOException {
        HttpMethod httpMethod;
        String path;
        String version;
        Map<String, String> headers = new HashMap<>();
        String body = "";

        String firstLine = readLine(inputStream);

        if (firstLine == null || firstLine.trim().isEmpty()) {
            return null;
        }

        // Validate request line has the required 3 parts: METHOD PATH VERSION
        String[] line1 = firstLine.split(" ");
        if (line1.length < 3) {
            System.err.println("Malformed request line: " + firstLine);
            return null;
        }

        // Handle unsupported HTTP methods gracefully instead of crashing
        try {
            httpMethod = HttpMethod.valueOf(line1[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("Unsupported HTTP method: " + line1[0]);
            return null;
        }

        path = line1[1];
        version = line1[2];

        // Parse headers with validation
        String line2;
        while ((line2 = readLine(inputStream)) != null && !line2.isEmpty()) {
            String[] parts = line2.split(":", 2);
            if (parts.length == 2) {
                headers.put(parts[0].trim(), parts[1].trim());
            }
            // Malformed headers without a colon are silently skipped
        }

        // Parse body with bounds validation
        if (headers.containsKey("Content-Length")) {
            int length;
            try {
                length = Integer.parseInt(headers.get("Content-Length").trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid Content-Length: " + headers.get("Content-Length"));
                return null;
            }

            if (length < 0) {
                System.err.println("Negative Content-Length: " + length);
                return null;
            }

            if (length > MAX_BODY_SIZE) {
                System.err.println("Content-Length exceeds limit (" + MAX_BODY_SIZE + "): " + length);
                return null;
            }

            byte[] bodyBytes = new byte[length];
            int bytesRead = 0;
            while (bytesRead < length) {
                int read = inputStream.read(bodyBytes, bytesRead, length - bytesRead);
                if (read == -1) break;
                bytesRead += read;
            }
            body = new String(bodyBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        }

        return new HttpRequest(httpMethod, path, version, headers, body, clientIp);
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\r') {
                int next = in.read();
                if (next == '\n') break;
            } else if (c == '\n') {
                break;
            } else {
                sb.append((char) c);
                // Prevent unbounded memory growth from malicious clients sending infinite header lines
                if (sb.length() > MAX_LINE_LENGTH) {
                    throw new IOException("Header line exceeds maximum length of " + MAX_LINE_LENGTH + " bytes");
                }
            }
        }
        if (sb.length() == 0 && c == -1) return null;
        return sb.toString();
    }
}
