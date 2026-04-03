package com.Shreyansh.webserver.demo;

import com.Shreyansh.webserver.annotations.GetMapping;
import com.Shreyansh.webserver.annotations.RestController;
import com.Shreyansh.webserver.http.HttpRequest;
import com.Shreyansh.webserver.http.HttpResponse;
import com.Shreyansh.webserver.http.HttpStatus;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public HttpResponse getHealth(HttpRequest request) {
        HttpResponse response = new HttpResponse();
        response.setStatus(HttpStatus.OK);
        response.addHeaders("Content-Type", "application/json");

        // In the real world, you might check DB connectivity here
        String json = "{\"status\": \"UP\", \"memory_healthy\": true}";
        response.setBody(json);

        return response;
    }
}