package com.Shreyansh.webserver.core;

import com.Shreyansh.webserver.http.HttpParser;
import com.Shreyansh.webserver.http.HttpRequest;

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
