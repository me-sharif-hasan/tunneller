package com.tunneller;

import com.tunneller.router.*;
import com.tunneller.connection.ConnectionManager;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Tunnel client with dual-mode support (Raw/Routing)
 * Uses isolated RouteHandler objects to prevent cross-route contamination
 */
public class TunnelClient {

    private final RouterConfig config;
    private final ConnectionManager connectionManager;
    private volatile List<RouteHandler> routeHandlers; // Isolated handlers - one per route
    private volatile boolean running = false;
    private Thread clientThread;

    public TunnelClient() {
        this.config = RouterConfig.getInstance();
        this.connectionManager = ConnectionManager.getInstance();
        // Listen for route changes and re-sort
        config.setRouteChangeListener(this::initializeRouteHandlers);
    }

    public synchronized void connect() {
        if (running) {
            System.out.println("Client already running");
            return;
        }

        running = true;
        clientThread = new Thread(this::start, "TunnelClient-Main");
        clientThread.start();
        System.out.println("Client started");
    }

    public synchronized void disconnect() {
        if (!running) {
            System.out.println("Client not running");
            return;
        }

        System.out.println("Shutting down client...");
        running = false;

        // Use ConnectionManager to close ALL resources
        connectionManager.closeAll();

        // Interrupt client thread
        if (clientThread != null) {
            clientThread.interrupt();
        }

        System.out.println("Client shutdown complete");
    }

    public boolean isRunning() {
        return running;
    }

    private void start() {
        // Initialize route handlers for complete isolation
        initializeRouteHandlers();

        String signalHost = config.getSignalHost();
        int signalPort = config.getSignalPort();
        int dataPort = config.getDataPort();

        int reconnectAttempt = 0;

        // Keep trying to connect to signal server ONLY while running
        while (running) {
            try {
                reconnectAttempt++;
                runClient(signalHost, signalPort, dataPort);

                // Connection ended normally, reset backoff
                reconnectAttempt = 0;

            } catch (Exception e) {
                if (!running) {
                    // Shutdown requested - stop reconnecting
                    System.out.println("Client disabled - stopping reconnection");
                    break;
                }

                System.err.println("Signal connection failed: " + e.getMessage());

                if (config.autoReconnectProperty().get() && running) {
                    // Exponential backoff: 3s, 6s, 12s, max 60s
                    int delaySec = Math.min(3 * (int) Math.pow(2, Math.min(reconnectAttempt - 1, 4)), 60);
                    System.err.println("Retrying in " + delaySec + " seconds... (attempt " + reconnectAttempt + ")");

                    try {
                        Thread.sleep(delaySec * 1000L);
                    } catch (InterruptedException ie) {
                        // Shutdown requested - stop reconnecting
                        System.out.println("Client disabled during sleep - stopping reconnection");
                        break;
                    }

                    // Double-check running flag after sleep
                    if (!running) {
                        System.out.println("Client disabled - stopping reconnection");
                        break;
                    }
                } else {
                    // Auto-reconnect disabled or client stopped
                    if (!running) {
                        System.out.println("Client disabled - stopping reconnection");
                    } else {
                        System.err.println("Auto-reconnect disabled. Stopping.");
                    }
                    break;
                }
            }
        }

        running = false;
        System.out.println("Client stopped");
    }

    /**
     * Initialize route handlers - one isolated handler per route
     * SORTED BY SPECIFICITY for fast first-match lookup
     * PUBLIC so it can be called when routes change
     */
    public void initializeRouteHandlers() {
        // Build list LOCALLY first to avoid concurrency issues during iteration by
        // other threads
        List<RouteHandler> newHandlers = new ArrayList<>();
        for (RoutingRule rule : config.getRoutingRules()) {
            RouteHandler handler = new RouteHandler(rule);
            newHandlers.add(handler);
        }

        // PRE-SORT by priority (lower = higher priority), then by specificity
        // This is Spring Cloud Gateway's approach - sort ONCE, not on every request
        newHandlers.sort((h1, h2) -> {
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
        for (RouteHandler handler : newHandlers) {
            System.out.println("    - " + handler);
        }
        System.out.println("  [RouteHandler] Total handlers: " + newHandlers.size());

        // Atomic assignment to volatile field
        this.routeHandlers = newHandlers;
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
        try (Socket socket = new Socket(signalHost, signalPort)) {
            socket.setKeepAlive(true); // Detect broken connections (half-open) to trigger reconnect
            connectionManager.setSignalSocket(socket); // Register with ConnectionManager

            // 1. Register (server protocol expects REGISTER, not IDENTIFY)
            String registerCmd = "REGISTER " + config.getFullDomain() + "\n";
            socket.getOutputStream().write(registerCmd.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            System.out.println("Registered as " + config.getFullDomain());

            // 2. Listen for commands
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            String line;
            while (running && (line = reader.readLine()) != null) {
                if (line.isEmpty())
                    continue;

                if (line.equals("PING")) {
                    socket.getOutputStream()
                            .write("PONG\n".getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                    System.out.println("Heartbeat: PING <-> PONG");
                    continue;
                }

                if (line.startsWith("CONNECT ")) {
                    String[] parts = line.split(" ");
                    if (parts.length < 2) {
                        System.err.println("Invalid CONNECT command: " + line);
                        continue;
                    }
                    String requestId = parts[1];

                    System.out.println("Received CONNECT request: " + requestId);

                    // Use VIRTUAL THREAD for instant startup (<1μs vs ~1ms for platform threads)
                    Thread.startVirtualThread(() -> handleTunnel(requestId, signalHost, dataPort));
                } else {
                    System.out.println("Unknown command: " + line);
                }
            }

            // Connection closed by server (normal exit from while loop)
            System.out.println("Signal server closed connection");

        } // Socket auto-closed here by try-with-resources

        System.out.println("Signal socket cleanup complete. Ready to reconnect.");
    }

    private void handleTunnel(String requestId, String signalHost, int dataPort) {
        Socket dataSocket = null;
        try {
            if (!running) {
                return; // Shutdown in progress
            }
            dataSocket = new Socket(signalHost, dataPort);
            connectionManager.registerSocket(dataSocket); // Track for cleanup

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
            // ALWAYS unregister and close socket
            if (dataSocket != null) {
                connectionManager.unregisterSocket(dataSocket);
                if (!dataSocket.isClosed()) {
                    try {
                        dataSocket.close();
                    } catch (Exception ignored) {
                    }
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
