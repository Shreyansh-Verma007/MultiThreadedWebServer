package com.Shreyansh.webserver.core;

import com.Shreyansh.webserver.http.HttpParser;
import com.Shreyansh.webserver.http.HttpRequest;
import com.Shreyansh.webserver.http.HttpResponse;
import com.Shreyansh.webserver.middleware.FilterChain;
import com.Shreyansh.webserver.routing.Router;

import java.io.*;
import java.net.Socket;

public class RequestProcessor implements Runnable {
    private final Socket client;
    private final Router router;
    private final FilterChain filterChain;

    public RequestProcessor(Socket client, Router router, FilterChain filterChain) {
        this.client = client;
        this.router = router;
        this.filterChain = filterChain;
    }

    @Override
    public void run() {
        try {
            InputStream inputStream = client.getInputStream();
            OutputStream outputStream = client.getOutputStream();

            String clientIp = client.getInetAddress().getHostAddress();
            if (clientIp == null) clientIp = "0.0.0.0";
            HttpRequest request = HttpParser.parseRequest(inputStream, clientIp);

            if (request == null) {
                return;
            }

            System.out.println("Received: " + request.getMethod() + " " + request.getPath());

            HttpResponse response = new HttpResponse();
            if (filterChain.execute(request, response)) {
                response = router.route(request);
            }

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
