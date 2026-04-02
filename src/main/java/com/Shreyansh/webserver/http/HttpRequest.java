package com.Shreyansh.webserver.http;

import java.util.Map;

public class HttpRequest {
    private HttpMethod httpMethod;
    private String path;
    private Map<String, String> headers;
    private String body;
    private String version;

    public HttpRequest(HttpMethod httpMethod, String path, String version, Map<String, String> headers, String body) {
        this.httpMethod = httpMethod;
        this.path = path;
        this.version = version;
        this.headers = headers;
        this.body = body;
    }

    public HttpMethod getMethod() {
        return httpMethod;
    }
    public String getPath() {
        return path;
    }
    public Map<String, String> getHeaders() {
        return headers;
    }
    public String getBody() {
        return body;
    }
    public String getVersion() {
        return version;
    }

}
