package com.Shreyansh.webserver.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class HttpParser {
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
        String[] line1 = firstLine.split(" ");
        httpMethod = HttpMethod.valueOf(line1[0]);
        path = line1[1];
        version = line1[2];

        String line2;
        while ((line2 = readLine(inputStream)) != null && !line2.isEmpty()) {
            String [] parts = line2.split(":", 2);
            headers.put(parts[0].trim(), parts[1].trim());
        }

        if (headers.containsKey("Content-Length")) {
            int length = Integer.parseInt(headers.get("Content-Length").trim());
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
            }
        }
        if (sb.length() == 0 && c == -1) return null;
        return sb.toString();
    }
}
