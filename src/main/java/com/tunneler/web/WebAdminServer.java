package com.tunneler.web;

import com.tunneler.TunnelClient;
import com.tunneler.router.RouterConfig;
import com.tunneler.router.RoutingRule;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

/**
 * Web-based admin server using Javalin
 * Provides REST API for managing routes via browser
 */
public class WebAdminServer {
    private static WebAdminServer instance;
    private final RouterConfig config;
    private TunnelClient client;
    private Javalin app;
    private int actualPort;

    private WebAdminServer() {
        this.config = RouterConfig.getInstance();
    }

    public static synchronized WebAdminServer getInstance() {
        if (instance == null) {
            instance = new WebAdminServer();
        }
        return instance;
    }

    public void setTunnelClient(TunnelClient client) {
        this.client = client;
    }

    public void start() {
        int port = config.getAdminPort();

        // Find free port if auto-allocate is enabled
        if (config.isAdminAutoPort()) {
            port = findFreePort();
        }

        app = Javalin.create(javalinConfig -> {
            javalinConfig.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/web";
            });
        }).start(port);

        actualPort = port;
        setupRoutes();

        System.out.println("Web Admin started at http://localhost:" + actualPort);
    }

    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            System.err.println("Failed to find free port, using default 8090");
            return 8090;
        }
    }

    private void setupRoutes() {
        // Route management
        app.get("/api/routes", this::handleGetRoutes);
        app.post("/api/routes", this::handleAddRoute);
        app.put("/api/routes/{index}", this::handleUpdateRoute);
        app.delete("/api/routes/{index}", this::handleDeleteRoute);

        // Client control
        app.post("/api/client/start", this::handleStartClient);
        app.post("/api/client/stop", this::handleStopClient);
        app.get("/api/status", this::handleGetStatus);

        // Configuration management
        app.get("/api/config", this::handleGetConfig);
        app.put("/api/config/domain", this::handleUpdateDomain);
        app.put("/api/config/signal", this::handleUpdateSignalConfig);
        app.put("/api/config/mode", this::handleUpdateMode);
    }

    private void handleGetRoutes(Context ctx) {
        ctx.json(config.getRoutingRules());
    }

    private void handleAddRoute(Context ctx) {
        try {
            RoutingRule rule = ctx.bodyAsClass(RoutingRule.class);
            config.addRoutingRule(rule);
            ctx.status(201).json(rule);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    private void handleUpdateRoute(Context ctx) {
        try {
            int index = Integer.parseInt(ctx.pathParam("index"));
            RoutingRule updated = ctx.bodyAsClass(RoutingRule.class);

            if (index < 0 || index >= config.getRoutingRules().size()) {
                ctx.status(404).json(Map.of("error", "Route not found"));
                return;
            }

            RoutingRule old = config.getRoutingRules().get(index);
            config.removeRoutingRule(old);
            config.addRoutingRule(updated);
            ctx.json(updated);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    private void handleDeleteRoute(Context ctx) {
        try {
            int index = Integer.parseInt(ctx.pathParam("index"));

            if (index < 0 || index >= config.getRoutingRules().size()) {
                ctx.status(404).json(Map.of("error", "Route not found"));
                return;
            }

            RoutingRule rule = config.getRoutingRules().get(index);
            config.removeRoutingRule(rule);
            ctx.status(204);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    private void handleGetStatus(Context ctx) {
        Map<String, Object> status = new HashMap<>();
        status.put("running", client != null && client.isRunning());
        status.put("routeCount", config.getRoutingRules().size());
        status.put("domain", config.getFullDomain());
        status.put("mode", config.getMode().name());
        ctx.json(status);
    }

    private void handleGetConfig(Context ctx) {
        Map<String, Object> configData = new HashMap<>();
        configData.put("domain", config.getDomain());
        configData.put("signalHost", config.getSignalHost());
        configData.put("signalPort", config.getSignalPort());
        configData.put("dataPort", config.getDataPort());
        configData.put("mode", config.getMode().name());
        ctx.json(configData);
    }

    private void handleStartClient(Context ctx) {
        try {
            if (client != null && client.isRunning()) {
                ctx.status(400).json(Map.of("error", "Client is already running"));
                return;
            }
            if (client != null) {
                client.connect();
                ctx.json(Map.of("success", true, "message", "Client started"));
            } else {
                ctx.status(400).json(Map.of("error", "Client not initialized"));
            }
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void handleStopClient(Context ctx) {
        try {
            if (client == null || !client.isRunning()) {
                ctx.status(400).json(Map.of("error", "Client is not running"));
                return;
            }
            client.disconnect();
            ctx.json(Map.of("success", true, "message", "Client stopped"));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void handleUpdateDomain(Context ctx) {
        try {
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String domain = body.get("domain");
            if (domain == null || domain.trim().isEmpty()) {
                ctx.status(400).json(Map.of("error", "Domain is required"));
                return;
            }
            config.setDomain(domain.trim());
            ctx.json(Map.of("success", true, "domain", config.getFullDomain()));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    private void handleUpdateSignalConfig(Context ctx) {
        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            if (body.containsKey("signalHost")) {
                config.setSignalHost((String) body.get("signalHost"));
            }
            if (body.containsKey("signalPort")) {
                config.setSignalPort(((Number) body.get("signalPort")).intValue());
            }
            if (body.containsKey("dataPort")) {
                config.setDataPort(((Number) body.get("dataPort")).intValue());
            }
            ctx.json(Map.of("success", true, "message", "Signal configuration updated"));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    private void handleUpdateMode(Context ctx) {
        try {
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String modeName = body.get("mode");
            if (modeName == null) {
                ctx.status(400).json(Map.of("error", "Mode is required"));
                return;
            }
            // This will be implemented based on OperationalMode enum
            ctx.json(Map.of("success", true, "mode", modeName, "message", "Mode switching not yet fully implemented"));
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void stop() {
        if (app != null) {
            app.stop();
            System.out.println("Web Admin stopped");
        }
    }

    public String getUrl() {
        return "http://localhost:" + actualPort;
    }

    public int getActualPort() {
        return actualPort;
    }
}
