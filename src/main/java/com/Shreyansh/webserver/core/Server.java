package com.Shreyansh.webserver.core;

import com.Shreyansh.webserver.routing.Router;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final int port;
    private final ExecutorService executor;
    private boolean isRunning;
    private final Router router;

    public Server(int port, int poolSize, Router router) {
        this.port = port;
        this.isRunning = true;
        this.executor = Executors.newFixedThreadPool(poolSize);
        this.router = router;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(this.port)) {
            System.out.println("Server started on port " + this.port + "...... ");

            while (this.isRunning) {
                Socket client = serverSocket.accept();
                RequestProcessor processor = new RequestProcessor(client, this.router);
                executor.execute(processor);
            }
        }
        catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }


}
