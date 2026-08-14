package com.Shreyansh.webserver.core;

import com.Shreyansh.webserver.cache.StaticFileHandler;
import com.Shreyansh.webserver.middleware.FilterChain;
import com.Shreyansh.webserver.routing.Router;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {
    private final int port;
    private final ExecutorService executor;
    private volatile boolean isRunning;
    private final Router router;
    private final FilterChain filterChain;
    private final StaticFileHandler fileHandler;
    private ServerSocket serverSocket;

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
        try {
            serverSocket = new ServerSocket(this.port, 10000);
            System.out.println("Server started on port " + this.port + "...... ");

            while (this.isRunning) {
                try {
                    Socket client = serverSocket.accept();
                    client.setSoTimeout(5000);
                    RequestProcessor processor = new RequestProcessor(client, this.router, this.filterChain, this.fileHandler);
                    executor.execute(processor);
                } catch (IOException e) {
                    // accept() throws when serverSocket is closed during shutdown — expected
                    if (!isRunning) {
                        break;
                    }
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    /**
     * Gracefully shuts down the server:
     * 1. Sets isRunning to false (volatile — visible to all threads)
     * 2. Closes the ServerSocket (causes accept() to throw, breaking the loop)
     * 3. Shuts down the thread pool and waits for in-flight requests to complete
     */
    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }
    }

    private void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
