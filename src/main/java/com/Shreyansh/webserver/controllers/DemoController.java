package com.Shreyansh.webserver.controllers;

import com.Shreyansh.webserver.annotations.RestController;
import com.Shreyansh.webserver.annotations.GetMapping;
import com.Shreyansh.webserver.http.HttpRequest;
import com.Shreyansh.webserver.http.HttpResponse;

@RestController
public class DemoController {

    // Demonstrating the custom @GetMapping annotation!
    @GetMapping("/api/status")
    public HttpResponse getStatus(HttpRequest request) {
        String jsonResponse = "{\"status\": \"Online\", \"framework\": \"MultithreadedWebServer v1.0\", \"activeThreads\": 12}";
        
        HttpResponse response = new HttpResponse();
        response.addHeaders("Content-Type", "application/json");
        response.setBody(jsonResponse);
        return response;
    }
}
