package com.Shreyansh.webserver.http;

public enum Status {
    OK(200, "Ok"),
    BAD_REQUEST(400, "BadRequest"),
    NOT_FOUND(404, "Not Found"),
    INTERNAL_ERROR(500, "Internal Server Error");

    private int code;
    private String message;
    Status(int code, String message) {
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
