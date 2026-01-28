package com.tunneler;

import com.tunneler.router.*;
import com.tunneler.config.ConfigManager;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Tunnel client with dual-mode support (Raw/Routing)
 * Uses isolated RouteHandler objects to prevent cross-route contamination
 */
public class TunnelClient {

    private final RouterConfig config;
    private List<RouteHandler> routeHandlers; // Isolated handlers - one per route

    public TunnelClient() {
        this.config = RouterConfig.getInstance();
        // Listen for route changes and re-sort
        config.setRouteChangeListener(this::initializeRouteHandlers);
    }

    public void start() {
        // Initialize route handlers for complete isolation
        initializeRouteHandlers();

        String signalHost = config.getSignalHost();
        int signalPort = config.getSignalPort();
        int dataPort = config.getDataPort();

        // Keep trying to connect to signal server
        while (true) {
            try {
                runClient(signalHost, signalPort, dataPort);
            } catch (Exception e) {
                System.err.println("Signal connection failed or dropped: " + e.getMessage());
                if (config.autoReconnectProperty().get()) {
                    System.err.println("Retrying in 3 seconds...");
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    System.err.println("Auto-reconnect disabled. Stopping.");
                    break;
                }
            }
        }
    }

    /**
     * Initialize route handlers - one isolated handler per route
     * SORTED BY SPECIFICITY for fast first-match lookup
     * PUBLIC so it can be called when routes change
     */
    public void initializeRouteHandlers() {
        routeHandlers = new ArrayList<>();
        for (RoutingRule rule : config.getRoutingRules()) {
            RouteHandler handler = new RouteHandler(rule);
            routeHandlers.add(handler);
        }

        // PRE-SORT by priority (lower = higher priority), then by specificity
        // This is Spring Cloud Gateway's approach - sort ONCE, not on every request
        routeHandlers.sort((h1, h2) -> {
            int priority1 = h1.getRule().getPriority();
            int priority2 = h2.getRule().getPriority();

            // First compare by priority (lower number = higher priority)
            int priorityCompare = Integer.compare(priority1, priority2);
            if (priorityCompare != 0) {
                return priorityCompare;
            }

            // If same priority, use specificity
            String p1 = h1.getRule().getPathPattern();
            String p2 = h2.getRule().getPathPattern();
            return Integer.compare(getRouteSpecificity(p2), getRouteSpecificity(p1)); // descending
        });

        System.out.println("  [RouteHandler] Routes sorted by specificity:");
        for (RouteHandler handler : routeHandlers) {
            System.out.println("    - " + handler);
        }
        System.out.println("  [RouteHandler] Total handlers: " + routeHandlers.size());
    }

    /**
     * Calculate route specificity (used ONLY at initialization)
     * Higher = more specific
     */
    private int getRouteSpecificity(String pattern) {
        if (pattern.endsWith("/*")) {
            // Longer prefix = more specific
            return 1000 + pattern.length();
        }
        // Exact match = highest
        return 10000;
    }

    private void runClient(String signalHost, int signalPort, int dataPort) throws IOException {
        System.out.println("Connecting to signal server " + signalHost + ":" + signalPort + "...");
        try (Socket signalSocket = new Socket(signalHost, signalPort)) {

            // 1. Register
            String registerCmd = "REGISTER " + config.getFullDomain() + "\n";
            signalSocket.getOutputStream().write(registerCmd.getBytes(StandardCharsets.UTF_8));
            signalSocket.getOutputStream().flush();
            System.out.println("Registered as " + config.getFullDomain());

            // 2. Listen for commands
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(signalSocket.getInputStream(), StandardCharsets.UTF_8));

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty())
                    continue;

                if (line.startsWith("CONNECT ")) {
                    String[] parts = line.split(" ");
                    if (parts.length < 2) {
                        System.err.println("Invalid CONNECT command: " + line);
                        continue;
                    }
                    String requestId = parts[1];

                    System.out.println("Received CONNECT request: " + requestId);

                    // Spawn a new thread to handle this specific tunnel
                    new Thread(() -> handleTunnel(requestId, signalHost, dataPort)).start();
                } else {
                    System.out.println("Unknown command: " + line);
                }
            }
        }
    }

    private void handleTunnel(String requestId, String signalHost, int dataPort) {
        Socket dataSocket = null;
        try {
            dataSocket = new Socket(signalHost, dataPort);

            // Handshake: REGISTER <hostname> <requestId> (per server protocol)
            String handshake = "REGISTER " + config.getFullDomain() + " " + requestId + "\n";
            dataSocket.getOutputStream().write(handshake.getBytes(StandardCharsets.UTF_8));
            dataSocket.getOutputStream().flush();

            // Route based on mode
            if (config.getMode() == OperationalMode.RAW_MODE) {
                handleRawMode(requestId, dataSocket);
            } else {
                handleRoutingMode(requestId, dataSocket);
            }
        } catch (Exception e) {
            System.err.println("[" + requestId + "] Error: " + e.getMessage());
        } finally {
            // ALWAYS close socket
            if (dataSocket != null && !dataSocket.isClosed()) {
                try {
                    dataSocket.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * RAW MODE: Forward all traffic to a single target
     */
    private void handleRawMode(String requestId, Socket dataSocket) throws Exception {
        String targetHost = config.getRawTargetHost();
        int targetPort = config.getRawTargetPort();

        System.out.println("[" + requestId + "] RAW MODE: Forwarding to " + targetHost + ":" + targetPort);

        try (Socket targetSocket = new Socket(targetHost, targetPort)) {
            Thread upstream = pipe(targetSocket.getInputStream(), dataSocket.getOutputStream(), requestId + "-up");
            Thread downstream = pipe(dataSocket.getInputStream(), targetSocket.getOutputStream(), requestId + "-down");

            upstream.join();
            downstream.join();
        }
    }

    /**
     * ROUTING MODE: Fast first-match lookup
     * Routes are PRE-SORTED by specificity at startup
     */
    private void handleRoutingMode(String requestId, Socket dataSocket) throws Exception {
        InputStream clientInput = dataSocket.getInputStream();

        // Parse HTTP request
        HttpRequestParser.ParseResult parseResult = HttpRequestParser.parseFirstLine(clientInput);
        if (parseResult == null) {
            System.err.println("[" + requestId + "] Invalid HTTP request");
            return;
        }

        String path = parseResult.path;
        System.out.println("[" + requestId + "] " + parseResult.method + " " + path);

        // FIRST MATCH WINS (routes already sorted by specificity)
        for (RouteHandler handler : routeHandlers) {
            if (handler.matches(path)) {
                handler.handleRequest(requestId, dataSocket, clientInput, parseResult);
                return;
            }
        }

        System.err.println("[" + requestId + "] No route found for: " + path);
    }

    private Thread pipe(InputStream input, OutputStream output, String name) {
        Thread thread = new Thread(() -> {
            try {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    output.flush();
                }
            } catch (Exception e) {
                // Connection closed
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
