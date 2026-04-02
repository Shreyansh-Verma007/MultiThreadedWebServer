package com.Shreyansh.webserver.routing;

import com.Shreyansh.webserver.http.HttpMethod;
import com.Shreyansh.webserver.http.HttpRequest;
import com.Shreyansh.webserver.http.HttpResponse;
import com.Shreyansh.webserver.http.HttpStatus;

public class Router {
    private final TrieNode root;

    public Router() {
        this.root = new TrieNode();
    }

    public void addRoute(HttpMethod httpMethod, String path, RouteHandler routeHandler) {
        String[] segments = path.split("/");
        TrieNode currentNode = root;

        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (!currentNode.getChildren().containsKey(segment)) {
                currentNode.getChildren().put(segment, new TrieNode());
            }
            currentNode = currentNode.getChildren().get(segment);
        }
        currentNode.getHandlers().put(httpMethod, routeHandler);
    }

    public HttpResponse route(HttpRequest request) {
        String path = request.getPath();
        HttpMethod httpMethod = request.getMethod();

        String[] segments = path.split("/");
        TrieNode currentNode = root;

        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (!currentNode.getChildren().containsKey(segment)) {
                HttpResponse httpResponse = new HttpResponse();
                httpResponse.setStatus(HttpStatus.NOT_FOUND);
                return httpResponse;
            }
            currentNode = currentNode.getChildren().get(segment);
        }
        if (currentNode.getHandlers().containsKey(httpMethod)) {
            RouteHandler routeHandler = currentNode.getHandlers().get(httpMethod);
            return routeHandler.handle(request);
        }
        else {
            HttpResponse httpResponse = new HttpResponse();
            httpResponse.setStatus(HttpStatus.NOT_FOUND);
            return httpResponse;
        }
    }
}
