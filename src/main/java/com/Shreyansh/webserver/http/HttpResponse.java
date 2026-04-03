package com.Shreyansh.webserver.http;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class HttpResponse {
    private HttpStatus httpStatus;
    private final Map<String, String> headers;
    private byte[] body;

    public HttpResponse() {
        this.httpStatus = HttpStatus.OK;
        this.headers = new HashMap<>();
        this.body = new byte[0];
    }
    public void setStatus(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }
    public void addHeaders(String key, String value) {
        headers.put(key, value);
    }
    public void setBody(byte[] body) {
        this.body = body;
        this.headers.put("Content-Length", String.valueOf(this.body.length));
    }
    public void setBody(String body) {
        this.body = body.getBytes();
        this.headers.put("Content-Length", String.valueOf(this.body.length));
    }

    public void send(OutputStream out) throws IOException {
        String statusLine = "HTTP/1.1 " + httpStatus.getCode() + " " + httpStatus.getMessage() + "\r\n";
        out.write(statusLine.getBytes());

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String headerLine = entry.getKey() + ": " + entry.getValue() + "\r\n";
            out.write(headerLine.getBytes());
        }

        out.write("\r\n".getBytes());
        out.write(body);
        out.flush();
    }

}
