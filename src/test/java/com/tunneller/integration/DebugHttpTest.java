package com.tunneller.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Debug test to check if simple HTTP requests work
 */
public class DebugHttpTest {

    @Test
    @DisplayName("Test: Simple HTTP Request Without Host Header")
    void testSimpleHttpRequest() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Make request to a known working endpoint
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status: " + response.statusCode());
            System.out.println("Body: " + response.body());
            System.out.println("✅ HTTP request test passed");
        } catch (Exception e) {
            System.out.println("❌ HTTP request failed: " + e.getMessage());
            throw e;
        }
    }
}
