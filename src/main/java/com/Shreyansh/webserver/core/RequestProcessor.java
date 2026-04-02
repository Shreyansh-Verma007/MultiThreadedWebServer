package com.Shreyansh.webserver.core;

import com.Shreyansh.webserver.http.HttpParser;
import com.Shreyansh.webserver.http.HttpRequest;
import com.Shreyansh.webserver.http.HttpResponse;
import com.Shreyansh.webserver.http.HttpStatus;
import java.io.*;
import java.net.Socket;

public class RequestProcessor implements Runnable {
    private final Socket client;

    public RequestProcessor(Socket client) {
        this.client = client;
    }

    @Override
    public void run() {
        try {
            InputStream inputStream = client.getInputStream();
            OutputStream outputStream = client.getOutputStream();
            HttpRequest request = HttpParser.parseRequest(inputStream);
            System.out.println("Received: " + request.getMethod() + " " + request.getPath());
            HttpResponse response = new HttpResponse();
            response.setStatus(HttpStatus.OK);
            response.addHeaders("Content-Type", "text/html");
            String htmlText = "<html><body><h1>Hello from my custom Java Web Server!</h1><p>Path requested: " + request.getPath() + "</p></body></html>";
            response.setBody(htmlText);
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
