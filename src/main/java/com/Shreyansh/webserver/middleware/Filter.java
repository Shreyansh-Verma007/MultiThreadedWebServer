package com.Shreyansh.webserver.middleware;

import com.Shreyansh.webserver.http.HttpRequest;
import com.Shreyansh.webserver.http.HttpResponse;

@FunctionalInterface
public interface Filter {
    boolean filter(HttpRequest request, HttpResponse response);
}
