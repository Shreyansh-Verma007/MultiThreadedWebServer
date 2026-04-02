package com.Shreyansh.webserver;

import com.Shreyansh.webserver.core.Server;
import com.Shreyansh.webserver.http.HttpMethod;
import com.Shreyansh.webserver.http.HttpResponse;
import com.Shreyansh.webserver.http.HttpStatus;
import com.Shreyansh.webserver.routing.Router;

public class Main {
    public static void main(String[] args) {
        Router router = new Router();

        router.addRoute(HttpMethod.GET, "/", request -> {
            HttpResponse httpResponse = new  HttpResponse();
            httpResponse.setStatus(HttpStatus.OK);
            httpResponse.addHeaders("Content-Type", "text/html");
            httpResponse.setBody("<h1>Welcome to my API</h1>");
            return httpResponse;
        });
        router.addRoute(HttpMethod.GET, "/api/users", request -> {
            HttpResponse response = new HttpResponse();
            response.setStatus(HttpStatus.OK);
            response.addHeaders("Content-Type", "application/json");
            response.setBody("{\"users\": [\"Alice\", \"Bob\", \"Charlie\"]}");
            return response;
        });

        Server server = new Server(8080, 100, router);
        server.start();
    }
}
