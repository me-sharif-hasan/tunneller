package com.tunneller.router;

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
        com.tunneller.monitor.ConnectionStats.getInstance().recordConnection(rule.getPathPattern());

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
            String pathToSend = originalPath;
            if (rule.isStripPrefix()) {
                String rewrittenPath = rule.rewritePath(originalPath);
                if (!rewrittenPath.equals(originalPath)) {
                    System.out.println("[" + requestId + "] Path rewriting: " + originalPath + " -> " + rewrittenPath);
                    pathToSend = rewrittenPath;
                }
            }

            // Consolidate request forwarding logic
            forwardRequest(targetOutput, parseResult, pathToSend, rule);

            targetOutput.flush();

            targetOutput.flush();

            // Bi-directional pipe (isolated to this connection)
            Thread upstream = createPipe(targetSocket.getInputStream(),
                    clientSocket.getOutputStream(), requestId + "-up");
            Thread downstream = createPipe(clientInput, targetOutput, requestId + "-down");

            upstream.join();
            downstream.join();
        } finally {
            // Mark connection complete
            com.tunneller.monitor.ConnectionStats.getInstance().completeConnection(rule.getPathPattern());
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
                    System.out.println(new String(buffer, 0, bytesRead));
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

    /**
     * Helper to robustly forward request with modified headers
     * Enforces Connection: close to prevent socket leaks/hangs
     */
    private void forwardRequest(OutputStream targetOutput, HttpRequestParser.ParseResult parseResult,
            String pathToSend, RoutingRule rule) throws IOException {

        // 1. Write Request Line
        String firstLine = parseResult.method + " " + pathToSend + " " + parseResult.version + "\r\n";
        targetOutput.write(firstLine.getBytes(StandardCharsets.UTF_8));

        // 2. Identify Header/Body Splitting
        int headerEnd = findHeaderEnd(parseResult.allBufferedBytes, parseResult.totalBytesRead);
        int bodyStart = (headerEnd != -1) ? headerEnd + 4 : parseResult.firstLineEndIndex; // Fallback if crazy

        // 3. Extract and Filter Headers
        int headersStart = parseResult.firstLineEndIndex;
        int headersLength = (headerEnd != -1) ? (headerEnd - headersStart)
                : (parseResult.totalBytesRead - headersStart);

        if (headersLength > 0) {
            String headersStr = new String(parseResult.allBufferedBytes, headersStart, headersLength,
                    StandardCharsets.UTF_8);
            String[] lines = headersStr.split("\r\n");

            boolean forceClose = RouterConfig.getInstance().isForceConnectionClose();

            for (String line : lines) {
                if (line.isEmpty())
                    continue;
                String lower = line.toLowerCase();

                // SKIPPING LOGIC:
                // 1. Skip Host if rewriting (we verify rule.isForwardHost later)
                // 2. Skip Connection/Keep-Alive if forceClose is enabled
                if ((rule.isForwardHost() && lower.startsWith("host:")) ||
                        (forceClose && (lower.startsWith("connection:") ||
                                lower.startsWith("keep-alive:") ||
                                lower.startsWith("proxy-connection:")))) {
                    continue;
                }

                targetOutput.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
        }

        // 4. Inject Headers

        // Host injection
        if (rule.isForwardHost()) {
            String originalHost = parseResult.headers.get("host");
            targetOutput.write(("Host: " + rule.getTargetHost() + "\r\n").getBytes(StandardCharsets.UTF_8));
            if (originalHost != null) {
                targetOutput.write(("X-Forwarded-Host: " + originalHost + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
        }

        // FORCE CONNECTION CLOSE (If enabled)
        if (RouterConfig.getInstance().isForceConnectionClose()) {
            targetOutput.write("Connection: close\r\n".getBytes(StandardCharsets.UTF_8));
        }

        // End of Headers
        targetOutput.write("\r\n".getBytes(StandardCharsets.UTF_8));

        // 5. Write Body (Binary Safe)
        if (bodyStart < parseResult.totalBytesRead) {
            int bodyLen = parseResult.totalBytesRead - bodyStart;
            targetOutput.write(parseResult.allBufferedBytes, bodyStart, bodyLen);
        }
    }

    private int findHeaderEnd(byte[] buffer, int length) {
        for (int i = 0; i < length - 3; i++) {
            if (buffer[i] == '\r' && buffer[i + 1] == '\n' && buffer[i + 2] == '\r' && buffer[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
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
