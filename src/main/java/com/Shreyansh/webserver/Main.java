package com.Shreyansh.webserver;

import com.Shreyansh.webserver.core.Server;
import com.Shreyansh.webserver.middleware.FilterChain;
import com.Shreyansh.webserver.middleware.RateLimiter;
import com.Shreyansh.webserver.routing.Router;

public class Main {
    public static void main(String[] args) {
        Router router = new Router();
        FilterChain filterChain = new FilterChain();
        filterChain.addFilter(new RateLimiter());

        Server server = new Server(8080, 100, router, filterChain);
        server.scanAndStart("com.Shreyansh.webserver");
    }
}
