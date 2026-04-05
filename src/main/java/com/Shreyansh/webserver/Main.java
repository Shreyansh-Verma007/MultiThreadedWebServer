package com.Shreyansh.webserver;

import com.Shreyansh.webserver.cache.LRUCache;
import com.Shreyansh.webserver.cache.StaticFileHandler;
import com.Shreyansh.webserver.core.Server;
import com.Shreyansh.webserver.middleware.FilterChain;
import com.Shreyansh.webserver.middleware.RateLimiter;
import com.Shreyansh.webserver.routing.Router;

public class Main {
    public static void main(String[] args) {
        Router router = new Router();
        FilterChain filterChain = new FilterChain();
        filterChain.addFilter(new RateLimiter());
        LRUCache cache = new LRUCache(50);
        StaticFileHandler fileHandler = new StaticFileHandler(cache);

        Server server = new Server(8080, 100, router, filterChain, fileHandler);
        server.scanAndStart("com.Shreyansh.webserver");
    }
}
