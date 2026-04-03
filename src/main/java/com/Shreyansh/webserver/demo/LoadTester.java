package com.Shreyansh.webserver.demo;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTester {

    public static void main(String[] args) throws InterruptedException {
        int totalRequests = 50000;
        int concurrency = 100;

        System.out.println("Initiating Chaos Stress Test (GET & POST)...");
        System.out.println("Load: " + totalRequests + " requests across " + concurrency + " concurrent threads.\n");

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            pool.execute(() -> {
                try {
                    // Randomly pick an endpoint to test the Router's method separation
                    int randomChoice = (int) (Math.random() * 3);
                    HttpURLConnection connection;

                    if (randomChoice == 0) {
                        URL url = new URL("http://localhost:8080/api/health");
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");
                    } else if (randomChoice == 1) {
                        URL url = new URL("http://localhost:8080/api/users");
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");
                    } else {
                        URL url = new URL("http://localhost:8080/api/users");
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("POST");
                        connection.setDoOutput(true); // Required to send a body
                        connection.setRequestProperty("Content-Type", "application/json");

                        // Send the JSON Body
                        String jsonInputString = "{\"name\": \"StressTester\", \"thread\": \"" + Thread.currentThread().getName() + "\"}";
                        try(OutputStream os = connection.getOutputStream()) {
                            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                            os.write(input, 0, input.length);
                        }
                    }

                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);

                    int statusCode = connection.getResponseCode();

                    if (statusCode == 200 || statusCode == 201) {
                        successCount.incrementAndGet();
                    } else if (statusCode == 429) {
                        blockedCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                        // THE MYSTERY TRAP: Print the exact HTTP code of the failure
                        if (failCount.get() <= 5) {
                            System.out.println("MYSTERY HTTP ERROR: Server returned HTTP " + statusCode);
                        }
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    // THE MYSTERY TRAP: Print the raw Java exception
                    if (failCount.get() <= 5) {
                        System.out.println("MYSTERY JAVA ERROR: " + e.getMessage());
                    }
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.MINUTES);

        long endTime = System.currentTimeMillis();
        long totalTimeMs = endTime - startTime;
        double rps = (totalRequests / (double) totalTimeMs) * 1000;

        System.out.println("\n====== CHAOS TEST COMPLETE ======");
        System.out.println("Total Time:       " + totalTimeMs + " ms");
        System.out.println("Success (200s):   " + successCount.get());
        System.out.println("Blocked (429s):   " + blockedCount.get());
        System.out.println("Fails/Drops:      " + failCount.get());
        System.out.println("Throughput:       " + String.format("%.2f", rps) + " requests/second");
    }
}