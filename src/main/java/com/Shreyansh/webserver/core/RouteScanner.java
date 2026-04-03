package com.Shreyansh.webserver.core;

import com.Shreyansh.webserver.annotations.RestController;
import com.Shreyansh.webserver.routing.Router;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class RouteScanner {
    private Router router;

    public RouteScanner(Router router) {
        this.router = router;
    }

    public void scan(String basePackage) {
        try {
            String path = basePackage.replace('.', '/');
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            URL url = loader.getResource(path);
            if (url == null) {
                System.out.println("Can't find resource " + path);
                return;
            }
            String directoryPath = URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8);
            File directory = new File(directoryPath);

            if (directory.exists()) {
                scanDirectory(directory, basePackage);
            }
        } catch (Exception e) {
            System.err.println("Scan failed: " + e.getMessage());;
        }
    }

    private void scanDirectory(File directory, String basePackage) {
        File[] files = directory.listFiles();
        if (files == null) { return; };
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, basePackage + "." + file.getName());
            }
            else if (file.getName().endsWith(".class")) {
                String className = basePackage + "." + file.getName().substring(0, file.getName().length() - 6);
                processClass(className);
            }
        }
    }
    private void processClass(String className) {
        try {
            Class<?> clas = Class.forName(className);
            if (clas.isAnnotationPresent(RestController.class)) {
                Object controller = clas.getDeclaredConstructor().newInstance();
                router.registerController(controller);
            }
        }
        catch (Exception e) {
            System.err.println("Skipping class " + e.getMessage());
        }
    }
}
