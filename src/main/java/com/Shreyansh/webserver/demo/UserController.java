package com.Shreyansh.webserver.demo;

import com.Shreyansh.webserver.annotations.GetMapping;
import com.Shreyansh.webserver.annotations.PostMapping;
import com.Shreyansh.webserver.annotations.RestController;
import com.Shreyansh.webserver.http.HttpRequest;
import com.Shreyansh.webserver.http.HttpResponse;
import com.Shreyansh.webserver.http.HttpStatus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
public class UserController {

    // A thread-safe mock database
    private final List<String> database = new CopyOnWriteArrayList<>(
            List.of("\"Alice\"", "\"Bob\"", "\"Charlie\"")
    );

    @GetMapping("/api/users")
    public HttpResponse getAllUsers(HttpRequest request) {
        HttpResponse response = new HttpResponse();
        response.setStatus(HttpStatus.OK);
        response.addHeaders("Content-Type", "application/json");

        // Manually building a JSON array string
        String jsonArray = "[" + String.join(", ", database) + "]";
        response.setBody("{\"users\": " + jsonArray + "}");

        return response;
    }
    @PostMapping("/api/users")
    public HttpResponse addUser(HttpRequest request) {
        // Grab the JSON body your parser just successfully read!
        String jsonBody = request.getBody();
        System.out.println("SERVER RECEIVED POST DATA: " + jsonBody);

        HttpResponse response = new HttpResponse();
        response.setStatus(HttpStatus.OK);
        response.addHeaders("Content-Type", "application/json");

        // Echo the data back to prove it worked
        response.setBody("{\"status\": \"User created!\", \"data_received\": " + jsonBody + "}");
        return response;
    }
}