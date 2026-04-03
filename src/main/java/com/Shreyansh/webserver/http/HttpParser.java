package com.Shreyansh.webserver.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class HttpParser {
    public static HttpRequest parseRequest(InputStream inputStream) throws IOException {
        HttpMethod httpMethod;
        String path;
        String version;
        Map<String, String> headers = new HashMap<>();
        String body = "";

        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));

        String firstLine = br.readLine();

        if (firstLine == null || firstLine.trim().isEmpty()) {
            return null;
        }
        String[] line1 = firstLine.split(" ");
        httpMethod = HttpMethod.valueOf(line1[0]);
        path = line1[1];
        version = line1[2];

        String line2 = "";
        while ((line2 = br.readLine()) != null && !line2.isEmpty()) {
            String [] parts = line2.split(":", 2);
            headers.put(parts[0].trim(), parts[1].trim());
        }

        if (headers.containsKey("Content-Length")) {
            int length = Integer.parseInt(headers.get("Content-Length"));
            char[] buffer = new char[length];
            int charsRead = 0;
            while (charsRead < length) {
                int read = br.read(buffer, charsRead, length - charsRead);
                if (read == -1) {
                    break;
                }
                charsRead += read;
            }
            body = new String(buffer);
        }

        return new HttpRequest(httpMethod, path, version, headers, body);
    }
}
