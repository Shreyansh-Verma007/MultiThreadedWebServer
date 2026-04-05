package com.Shreyansh.webserver.http;

public enum HttpStatus {
    OK(200, "Ok"),
    NOT_FOUND(404, "Not Found"),
    INTERNAL_ERROR(500, "Internal Server Error"),
    TOO_MANY_REQUESTS(429, "Too Many Requests");

    private final int code;
    private final String message;
    HttpStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }
    public int getCode() {
        return code;
    }
    public String getMessage() {
        return message;
    }
}
