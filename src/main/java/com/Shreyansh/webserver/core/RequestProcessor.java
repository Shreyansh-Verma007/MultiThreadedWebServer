package com.Shreyansh.webserver.core;

import com.Shreyansh.webserver.cache.LRUCache;
import com.Shreyansh.webserver.cache.StaticFileHandler;
import com.Shreyansh.webserver.http.*;
import com.Shreyansh.webserver.middleware.FilterChain;
import com.Shreyansh.webserver.routing.Router;

import java.io.*;
import java.net.Socket;

public class RequestProcessor implements Runnable {
    private final Socket client;
    private final Router router;
    private final FilterChain filterChain;
    private final StaticFileHandler fileHandler;

    public RequestProcessor(Socket client, Router router, FilterChain filterChain, StaticFileHandler fileHandler) {
        this.client = client;
        this.router = router;
        this.filterChain = filterChain;
        this.fileHandler = fileHandler;
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
                if (response.getStatus() == HttpStatus.NOT_FOUND &&
                        request.getMethod() == HttpMethod.GET) {

                    String path = request.getPath().equals("/") ? "/index.html" : request.getPath();

                    try {
                        LRUCache.cachedFile file = fileHandler.get(path);

                        if (file != null) {
                            response.setStatus(HttpStatus.OK);
                            response.setBody(file.data, file.contentType);
                        }
                    } catch (Exception e) {
                        System.err.println("File read error: " + e.getMessage());
                        response.setStatus(HttpStatus.INTERNAL_ERROR);
                    }
                }
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
