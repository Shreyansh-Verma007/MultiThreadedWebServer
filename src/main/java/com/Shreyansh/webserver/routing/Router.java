package com.Shreyansh.webserver.routing;

import com.Shreyansh.webserver.annotations.GetMapping;
import com.Shreyansh.webserver.annotations.PostMapping;
import com.Shreyansh.webserver.annotations.RestController;
import com.Shreyansh.webserver.http.HttpMethod;
import com.Shreyansh.webserver.http.HttpRequest;
import com.Shreyansh.webserver.http.HttpResponse;
import com.Shreyansh.webserver.http.HttpStatus;

import java.lang.reflect.Method;

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

    public void registerController(Object controller) {
        Class<?> controllerClass = controller.getClass();
        if (!controllerClass.isAnnotationPresent(RestController.class)) { return; }

        for (Method method : controllerClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GetMapping.class)) {
                GetMapping annotation = method.getAnnotation(GetMapping.class);
                String path = annotation.value();
                RouteHandler handler = request -> {
                    try {
                        return (HttpResponse) method.invoke(controller, request);
                    }
                    catch (Exception e) {
                        System.err.println("Error executing GET method: " + e.getMessage());
                        HttpResponse errorResponse = new HttpResponse();
                        errorResponse.setStatus(HttpStatus.INTERNAL_ERROR);
                        return errorResponse;
                    }
                };
                this.addRoute(HttpMethod.GET, path, handler);
                System.out.println("Mapped GET: " + path +  " onto " + controllerClass.getSimpleName() + "." + method.getName());
            }
            if (method.isAnnotationPresent( PostMapping.class)) {
                PostMapping annotation = method.getAnnotation(PostMapping.class);
                String path = annotation.value();
                RouteHandler handler = request -> {
                    try {
                        return (HttpResponse) method.invoke(controller, request);
                    }
                    catch (Exception e) {
                        System.err.println("Error executing POST method: " + e.getMessage());
                        HttpResponse errorResponse = new HttpResponse();
                        errorResponse.setStatus(HttpStatus.INTERNAL_ERROR);
                        return errorResponse;
                    }
                };
                this.addRoute(HttpMethod.POST, path, handler);
                System.out.println("Mapped POST: " + path +  " onto " + controllerClass.getSimpleName() + "." + method.getName());
            }
        }
    }
}
