package com.tunneler.router;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

/**
 * Handles all requests for a specific routing rule.
 * Each handler is ISOLATED - no shared state with other handlers.
 * This prevents cross-route contamination and caching issues.
 */
public class RouteHandler {
    private final RoutingRule rule;
    private static SSLContext sslContext = null;

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

        // Track connection (non-blocking)
        com.tunneler.monitor.ConnectionStats.getInstance().recordConnection(rule.getPathPattern());

        // Create connection (SSL or plain)
        Socket targetSocket;
        if (rule.isUseSSL()) {
            SSLSocketFactory factory = getTrustAllSSLContext().getSocketFactory();
            targetSocket = factory.createSocket(targetHost, targetPort);
            ((SSLSocket) targetSocket).startHandshake();
            System.out.println("[" + requestId + "] SSL handshake completed");
        } else {
            targetSocket = new Socket(targetHost, targetPort);
        }

        // Create ISOLATED connection for THIS route only
        try (Socket socket = targetSocket) {
            OutputStream targetOutput = socket.getOutputStream();

            // Path rewriting (if enabled for this route)
            if (rule.isStripPrefix()) {
                String rewrittenPath = rule.rewritePath(originalPath);

                if (!rewrittenPath.equals(originalPath)) {
                    System.out.println("[" + requestId + "] Path rewriting: " + originalPath + " -> " + rewrittenPath);

                    // Reconstruct first line with new path
                    String newFirstLine = parseResult.method + " " + rewrittenPath + " " +
                            parseResult.version + "\r\n";
                    targetOutput.write(newFirstLine.getBytes(StandardCharsets.UTF_8));

                    // Host header handling if enabled
                    if (rule.isForwardHost()) {
                        String originalHost = parseResult.headers.get("host");
                        if (originalHost != null) {
                            // Rewrite Host header to target
                            String newHost = "Host: " + targetHost + "\r\n";
                            targetOutput.write(newHost.getBytes(StandardCharsets.UTF_8));
                            // Add X-Forwarded-Host with original
                            String forwardHeader = "X-Forwarded-Host: " + originalHost + "\r\n";
                            targetOutput.write(forwardHeader.getBytes(StandardCharsets.UTF_8));
                            System.out
                                    .println("[" + requestId + "] Host rewrite: " + originalHost + " -> " + targetHost);
                        }
                    }

                    // Forward remaining buffered data (headers + body), skipping original Host
                    // header
                    int remainingStart = parseResult.firstLineEndIndex;
                    int remainingLength = parseResult.totalBytesRead - remainingStart;

                    if (rule.isForwardHost()) {
                        // Skip the original Host header when forwarding
                        String remainingData = new String(parseResult.allBufferedBytes, remainingStart,
                                remainingLength, StandardCharsets.UTF_8);
                        String[] lines = remainingData.split("\r\n", -1);
                        StringBuilder filtered = new StringBuilder();
                        for (String line : lines) {
                            if (!line.toLowerCase().startsWith("host:")) {
                                filtered.append(line).append("\r\n");
                            }
                        }
                        // Remove trailing CRLF and write
                        String filteredStr = filtered.toString();
                        if (filteredStr.endsWith("\r\n\r\n")) {
                            filteredStr = filteredStr.substring(0, filteredStr.length() - 2);
                        }
                        targetOutput.write(filteredStr.getBytes(StandardCharsets.UTF_8));
                    } else {
                        // No host forwarding - forward all headers as-is
                        targetOutput.write(parseResult.allBufferedBytes, remainingStart, remainingLength);
                    }
                } else {
                    // No actual rewriting needed
                    targetOutput.write(parseResult.allBufferedBytes);
                }
            } else {
                // No rewriting - check if we need to add X-Forwarded-Host
                if (rule.isForwardHost()) {
                    String originalHost = parseResult.headers.get("host");
                    if (originalHost != null) {
                        // Write the first line
                        String firstLine = parseResult.method + " " + parseResult.path + " " +
                                parseResult.version + "\r\n";
                        targetOutput.write(firstLine.getBytes(StandardCharsets.UTF_8));

                        // Rewrite Host header to target
                        String newHost = "Host: " + targetHost + "\r\n";
                        targetOutput.write(newHost.getBytes(StandardCharsets.UTF_8));
                        // Add X-Forwarded-Host with original
                        String forwardHeader = "X-Forwarded-Host: " + originalHost + "\r\n";
                        targetOutput.write(forwardHeader.getBytes(StandardCharsets.UTF_8));
                        System.out.println("[" + requestId + "] Host rewrite: " + originalHost + " -> " + targetHost);

                        // Forward remaining buffered data (headers + body), skipping original Host
                        // header
                        int remainingStart = parseResult.firstLineEndIndex;
                        int remainingLength = parseResult.totalBytesRead - remainingStart;

                        // Skip the original Host header when forwarding
                        String remainingData = new String(parseResult.allBufferedBytes, remainingStart,
                                remainingLength, StandardCharsets.UTF_8);
                        String[] lines = remainingData.split("\r\n", -1);
                        StringBuilder filtered = new StringBuilder();
                        for (String line : lines) {
                            if (!line.toLowerCase().startsWith("host:")) {
                                filtered.append(line).append("\r\n");
                            }
                        }
                        // Remove trailing CRLF and write
                        String filteredStr = filtered.toString();
                        if (filteredStr.endsWith("\r\n\r\n")) {
                            filteredStr = filteredStr.substring(0, filteredStr.length() - 2);
                        }
                        targetOutput.write(filteredStr.getBytes(StandardCharsets.UTF_8));
                    } else {
                        // No Host header found, forward as-is
                        targetOutput.write(parseResult.allBufferedBytes);
                    }
                } else {
                    // No forwardHost - forward as-is
                    targetOutput.write(parseResult.allBufferedBytes);
                }
            }

            targetOutput.flush();

            // Bi-directional pipe (isolated to this connection)
            Thread upstream = createPipe(targetSocket.getInputStream(),
                    clientSocket.getOutputStream(), requestId + "-up");
            Thread downstream = createPipe(clientInput, targetOutput, requestId + "-down");

            upstream.join();
            downstream.join();
        } finally {
            // Mark connection complete
            com.tunneler.monitor.ConnectionStats.getInstance().completeConnection(rule.getPathPattern());
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

    /**
     * Create SSL context that trusts all certificates (disables verification)
     * This allows connecting to servers with self-signed or expired certificates
     */
    private static synchronized SSLContext getTrustAllSSLContext() throws Exception {
        if (sslContext == null) {
            TrustManager[] trustAll = new TrustManager[] {
                    new X509TrustManager() {
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                    }
            };

            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());
        }
        return sslContext;
    }
}
