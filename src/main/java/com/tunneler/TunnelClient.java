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
     * This prevents cross-route state pollution
     */
    private void initializeRouteHandlers() {
        routeHandlers = new ArrayList<>();
        for (RoutingRule rule : config.getRoutingRules()) {
            RouteHandler handler = new RouteHandler(rule);
            routeHandlers.add(handler);
            System.out.println("  [RouteHandler] Initialized: " + handler);
        }
        System.out.println("  [RouteHandler] Total handlers: " + routeHandlers.size());
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
        try {
            System.out.println("[" + requestId + "] ===== Opening data socket =====");
            Socket dataSocket = new Socket(signalHost, dataPort);
            System.out.println("[" + requestId + "] Data socket connected: " + dataSocket.isConnected());
            System.out.println("[" + requestId + "] Data socket bound: " + dataSocket.isBound());
            System.out.println("[" + requestId + "] Data socket closed: " + dataSocket.isClosed());

            String identifyCmd = "IDENTIFY " + requestId + "\n";
            System.out.println("[" + requestId + "] Sending IDENTIFY command: " + identifyCmd.trim());
            dataSocket.getOutputStream().write(identifyCmd.getBytes(StandardCharsets.UTF_8));
            dataSocket.getOutputStream().flush();
            System.out.println("[" + requestId + "] IDENTIFY sent successfully");

            // Decide handling mode based on config
            OperationalMode mode = config.getMode();
            System.out.println("[" + requestId + "] Mode: " + mode);

            if (mode == OperationalMode.RAW_MODE) {
                handleRawMode(requestId, dataSocket);
            } else {
                handleRoutingMode(requestId, dataSocket);
            }

            dataSocket.close();
        } catch (Exception e) {
            System.err.println("[" + requestId + "] Error: " + e.getMessage());
            e.printStackTrace();
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
     * ROUTING MODE: Find matching handler and delegate completely
     * Each handler is ISOLATED - prevents cross-route contamination
     */
    private void handleRoutingMode(String requestId, Socket dataSocket) throws Exception {
        InputStream clientInput = dataSocket.getInputStream();

        // Parse first line to extract method, path, version
        HttpRequestParser.ParseResult parseResult = HttpRequestParser.parseFirstLine(clientInput);

        if (parseResult == null) {
            System.err.println("[" + requestId + "] Invalid HTTP request");
            return;
        }

        String path = parseResult.path;
        System.out.println("[" + requestId + "] ROUTING MODE: Path = " + path);

        // Find matching handler
        RouteHandler handler = null;
        for (RouteHandler h : routeHandlers) {
            if (h.matches(path)) {
                handler = h;
                break;
            }
        }

        if (handler == null) {
            System.err.println("[" + requestId + "] No route found for path: " + path);
            return;
        }

        // Delegate COMPLETELY to the handler
        // Handler has its own isolated state and connections
        // NO SHARED STATE between routes!
        handler.handleRequest(requestId, dataSocket, clientInput, parseResult);
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
