package com.Shreyansh.webserver.core;

import com.Shreyansh.webserver.cache.StaticFileHandler;
import com.Shreyansh.webserver.middleware.FilterChain;
import com.Shreyansh.webserver.routing.Router;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("ALL")
public class Server {
    private final int port;
    private final ExecutorService executor;
    private final boolean isRunning;
    private final Router router;
    private final FilterChain filterChain;
    private final StaticFileHandler fileHandler;

    public Server(int port, int poolSize, Router router, FilterChain filterChain, StaticFileHandler fileHandler) {
        this.port = port;
        this.fileHandler = fileHandler;
        this.isRunning = true;
        this.executor = Executors.newFixedThreadPool(poolSize);
        this.router = router;
        this.filterChain = filterChain;
    }

    public void scanAndStart(String basePackage) {
        System.out.println("Scanning " + basePackage + " for controllers...");
        RouteScanner routeScanner = new RouteScanner(this.router);
        routeScanner.scan(basePackage);
        this.start();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(this.port, 10000)) {
            System.out.println("Server started on port " + this.port + "...... ");

            while (this.isRunning) {
                Socket client = serverSocket.accept();
                client.setSoTimeout(5000);
                RequestProcessor processor = new RequestProcessor(client, this.router, this.filterChain, this.fileHandler);
                executor.execute(processor);
            }
        }
        catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }


}
