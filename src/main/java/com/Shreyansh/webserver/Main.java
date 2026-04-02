package com.Shreyansh.webserver;

import com.Shreyansh.webserver.core.Server;

public class Main {
    public static void main(String[] args) {
        Server server = new Server(8080, 100);
        server.start();
    }
}
