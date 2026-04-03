package com.Shreyansh.webserver.core;

import com.Shreyansh.webserver.http.HttpParser;
import com.Shreyansh.webserver.http.HttpRequest;
import com.Shreyansh.webserver.http.HttpResponse;
import com.Shreyansh.webserver.routing.Router;

import java.io.*;
import java.net.Socket;

public class RequestProcessor implements Runnable {
    private final Socket client;
    private final Router router;

    public RequestProcessor(Socket client, Router router) {
        this.client = client;
        this.router = router;
    }

    @Override
    public void run() {
        try {
            InputStream inputStream = client.getInputStream();
            OutputStream outputStream = client.getOutputStream();

            HttpRequest request = HttpParser.parseRequest(inputStream);

            System.out.println("Received: " + request.getMethod() + " " + request.getPath());

            HttpResponse response = router.route(request);

            response.send(outputStream);
        }
        catch (IOException e) {
            System.err.println("Error processing client: " + e.getMessage());
        }
        finally {
            try {
                if (client != null && !client.isClosed()) {
                    client.close();
                }
            }
            catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }
}
