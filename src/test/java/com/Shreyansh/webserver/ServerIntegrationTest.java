package com.Shreyansh.webserver;

import com.Shreyansh.webserver.annotations.GetMapping;
import com.Shreyansh.webserver.annotations.PostMapping;
import com.Shreyansh.webserver.annotations.RestController;
import com.Shreyansh.webserver.cache.LRUCache;
import com.Shreyansh.webserver.cache.StaticFileHandler;
import com.Shreyansh.webserver.core.Server;
import com.Shreyansh.webserver.http.HttpRequest;
import com.Shreyansh.webserver.http.HttpResponse;
import com.Shreyansh.webserver.http.HttpStatus;
import com.Shreyansh.webserver.middleware.FilterChain;
import com.Shreyansh.webserver.middleware.RateLimiter;
import com.Shreyansh.webserver.routing.Router;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class ServerIntegrationTest {

    private static Server server;
    private static Thread serverThread;

    @BeforeAll
    public static void setup() throws Exception {
        Router router = new Router();
        router.registerController(new TestApiController()); // Manually register reliable test routes
        
        FilterChain filterChain = new FilterChain();
        filterChain.addFilter(new RateLimiter());
        LRUCache cache = new LRUCache(50);
        StaticFileHandler fileHandler = new StaticFileHandler(cache);

        // Expose on alternative port to avoid conflict
        server = new Server(8089, 10, router, filterChain, fileHandler);

        serverThread = new Thread(() -> {
            server.scanAndStart("com.Shreyansh.webserver.dummy"); // Scan dummy package
        });
        serverThread.start();
        Thread.sleep(1500); // Allow server to boot
    }

    @AfterAll
    public static void teardown() {
        serverThread.interrupt();
    }

    @Test
    public void testSlowlorisTimeout() {
        assertDoesNotThrow(() -> {
            try (Socket socket = new Socket("localhost", 8089)) {
                long start = System.currentTimeMillis();
                int read = socket.getInputStream().read(); 
                long elapsed = System.currentTimeMillis() - start;
                
                assertEquals(-1, read, "Server did not close the slow connection correctly.");
                assertTrue(elapsed >= 4000 && elapsed <= 12000, "Timeout should be ~5 seconds. Elapsed: " + elapsed);
            }
        });
    }

    @Test
    public void testGetApi() throws Exception {
        URL url = URI.create("http://localhost:8089/api/test").toURL();
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        
        assertEquals(200, con.getResponseCode());
        
        try (InputStream in = con.getInputStream()) {
            String responseBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("Hello Test", responseBody);
        }
    }

    @Test
    public void testPostBinaryEcho() throws Exception {
        URL url = URI.create("http://localhost:8089/api/echo").toURL();
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/octet-stream");

        // Use custom binary pseudo-random data
        byte[] payload = new byte[]{0x00, 0x01, (byte) 0xFF, (byte) 0x8A, 0x00, 0x05, 0x7F};
        con.setRequestProperty("Content-Length", String.valueOf(payload.length));

        try (OutputStream out = con.getOutputStream()) {
            out.write(payload);
            out.flush();
        }

        assertEquals(200, con.getResponseCode());
        
        try (InputStream in = con.getInputStream()) {
            byte[] responseBytes = in.readAllBytes();
            assertArrayEquals(payload, responseBytes, "Binary data should not be corrupted.");
        }
    }

    @Test
    public void testRateLimiterLimits() throws Exception {
        URL url = URI.create("http://localhost:8089/api/test").toURL();
        int responseCode = 200;
        
        // Spam 150 requests quickly. Limit is 100/sec per IP.
        for (int i = 0; i < 150; i++) {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            responseCode = con.getResponseCode();
            if (responseCode == 429) {
                break;
            }
        }
        
        assertEquals(429, responseCode, "Rate limiter should trigger 429 Too Many Requests");
    }

    @RestController
    public static class TestApiController {
        @GetMapping("/api/test")
        public HttpResponse handleGet(HttpRequest request) {
            HttpResponse response = new HttpResponse();
            response.setStatus(HttpStatus.OK);
            response.setBody("Hello Test");
            return response;
        }

        @PostMapping("/api/echo")
        public HttpResponse handleEcho(HttpRequest request) {
            HttpResponse response = new HttpResponse();
            response.setStatus(HttpStatus.OK);
            
            // Return exactly what was posted to verify binary corruption fixes
            byte[] rawBytes = request.getBody().getBytes(StandardCharsets.ISO_8859_1);
            response.setBody(rawBytes, "application/octet-stream");
            return response;
        }
    }
}
