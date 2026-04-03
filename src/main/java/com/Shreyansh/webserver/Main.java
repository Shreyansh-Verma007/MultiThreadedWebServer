package com.Shreyansh.webserver;

import com.Shreyansh.webserver.core.Server;
import com.Shreyansh.webserver.routing.Router;

public class Main {
    public static void main(String[] args) {
        Router router = new Router();

        Server server = new Server(8080, 100, router);
        server.scanAndStart("com.Shreyansh.webserver");
    }
}
