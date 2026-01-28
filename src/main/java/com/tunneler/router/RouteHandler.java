package com.tunneler.router;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Handles all requests for a specific routing rule.
 * Each handler is ISOLATED - no shared state with other handlers.
 * This prevents cross-route contamination and caching issues.
 */
public class RouteHandler {
    private final RoutingRule rule;

    public RouteHandler(RoutingRule rule) {
        this.rule = rule;
    }

    /**
     * Check if this handler's route matches the given path
     */
    public boolean matches(String path) {
        return rule.matches(path);
    }

    /**
     * Get the routing rule this handler manages
     */
    public RoutingRule getRule() {
        return rule;
    }

    /**
     * Handle the complete request lifecycle for this route.
     * ISOLATED - Each handler manages its own connections and state.
     * 
     * @param requestId    Unique identifier for logging
     * @param clientSocket Socket from the client
     * @param clientInput  InputStream from client (already positioned after
     *                     parseResult read)
     * @param parseResult  Parsed HTTP request data
     */
    public void handleRequest(String requestId, Socket clientSocket, InputStream clientInput,
            HttpRequestParser.ParseResult parseResult) throws Exception {

        String originalPath = parseResult.path;
        String targetHost = rule.getTargetHost();
        int targetPort = rule.getTargetPort();

        System.out.println("[" + requestId + "] RouteHandler [" + rule.getPathPattern() +
                "] -> " + targetHost + ":" + targetPort + " (" + rule.getDescription() + ")");

        // Create ISOLATED connection for THIS route only
        try (Socket targetSocket = new Socket(targetHost, targetPort)) {
            OutputStream targetOutput = targetSocket.getOutputStream();

            // Path rewriting (if enabled for this route)
            if (rule.isStripPrefix()) {
                String rewrittenPath = rule.rewritePath(originalPath);

                if (!rewrittenPath.equals(originalPath)) {
                    System.out.println("[" + requestId + "] Path rewriting: " + originalPath + " -> " + rewrittenPath);

                    // Reconstruct first line with new path
                    String newFirstLine = parseResult.method + " " + rewrittenPath + " " +
                            parseResult.version + "\r\n";
                    targetOutput.write(newFirstLine.getBytes(StandardCharsets.UTF_8));

                    // Forward remaining buffered data (headers + body)
                    int remainingStart = parseResult.firstLineEndIndex;
                    int remainingLength = parseResult.totalBytesRead - remainingStart;
                    targetOutput.write(parseResult.allBufferedBytes, remainingStart, remainingLength);
                } else {
                    // No actual rewriting needed
                    targetOutput.write(parseResult.allBufferedBytes);
                }
            } else {
                // No rewriting - forward as-is
                targetOutput.write(parseResult.allBufferedBytes);
            }

            targetOutput.flush();

            // Bi-directional pipe (isolated to this connection)
            Thread upstream = createPipe(targetSocket.getInputStream(),
                    clientSocket.getOutputStream(), requestId + "-up");
            Thread downstream = createPipe(clientInput, targetOutput, requestId + "-down");

            upstream.join();
            downstream.join();
        }
    }

    /**
     * Create a pipe thread for streaming data between sockets
     */
    private Thread createPipe(InputStream input, OutputStream output, String name) {
        Thread thread = new Thread(() -> {
            try {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    output.flush();
                }
            } catch (Exception e) {
                // Connection closed normally
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    @Override
    public String toString() {
        return "RouteHandler[" + rule.getPathPattern() + " -> " +
                rule.getTargetHost() + ":" + rule.getTargetPort() + "]";
    }
}
