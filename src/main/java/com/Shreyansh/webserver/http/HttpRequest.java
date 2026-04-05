package com.Shreyansh.webserver.http;

import java.util.Map;

public class HttpRequest {
    private final HttpMethod httpMethod;
    private final String path;
    private final Map<String, String> headers;
    private final String body;
    private final String version;
    private final String remoteAddr;

    public HttpRequest(HttpMethod httpMethod, String path, String version, Map<String, String> headers, String body, String remoteAddr) {
        this.httpMethod = httpMethod;
        this.path = path;
        this.version = version;
        this.headers = headers;
        this.body = body;
        this.remoteAddr = remoteAddr;
    }

    public HttpMethod getMethod() {
        return httpMethod;
    }
    public String getPath() {
        return path;
    }

    public String getBody() {
        return body;
    }

    public String getRemoteAddr() { return remoteAddr; }
}
