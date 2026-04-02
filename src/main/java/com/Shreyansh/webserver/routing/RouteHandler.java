package com.Shreyansh.webserver.routing;

import com.Shreyansh.webserver.http.HttpRequest;
import com.Shreyansh.webserver.http.HttpResponse;

@FunctionalInterface
public interface RouteHandler {
    HttpResponse handle(HttpRequest request);
}
