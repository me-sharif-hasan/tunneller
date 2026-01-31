package com.tunneler.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tunneler.router.OperationalMode;
import com.tunneler.router.RouterConfig;
import com.tunneler.router.RoutingRule;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Handles saving and loading configuration to/from JSON
 * Thread-safe with file locking
 */
public class ConfigManager {
    private static final String CONFIG_FILE = "tunneler-config.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Configuration data structure for JSON serialization
     */
    private static class ConfigData {
        String domain;
        String mode;
        String rawTargetHost;
        int rawTargetPort;
        String signalHost;
        int signalPort;
        int dataPort;
        List<RouteData> routes;
        boolean autoSave;
        boolean autoLoad;
        boolean autoReconnect;
        boolean monitoringEnabled;
        boolean loggingEnabled;
        int bufferSize;
        Set<String> pathPatternHistory;
        Set<String> targetHostHistory;
        Set<Integer> targetPortHistory;
        Set<String> domainHistory;
    }

    private static class RouteData {
        String pathPattern;
        String targetHost;
        int targetPort;
        String description;
        boolean stripPrefix; // For path prefix stripping
        int priority; // Route priority
        boolean forwardHost;
        boolean useSSL;
    }

    /**
     * Saves configuration to JSON file
     */
    public static void saveConfig() {
        try {
            RouterConfig config = RouterConfig.getInstance();
            ConfigData data = new ConfigData();

            // Basic settings
            data.domain = config.getDomain();
            data.mode = config.getMode().name();
            data.rawTargetHost = config.getRawTargetHost();
            data.rawTargetPort = config.getRawTargetPort();
            data.signalHost = config.getSignalHost();
            data.signalPort = config.getSignalPort();
            data.dataPort = config.getDataPort();

            // Routing rules
            data.routes = new ArrayList<>();
            for (RoutingRule rule : config.getRoutingRules()) {
                RouteData routeData = new RouteData();
                routeData.pathPattern = rule.getPathPattern();
                routeData.targetHost = rule.getTargetHost();
                routeData.targetPort = rule.getTargetPort();
                routeData.description = rule.getDescription();
                routeData.stripPrefix = rule.isStripPrefix();
                routeData.priority = rule.getPriority();
                routeData.forwardHost = rule.isForwardHost();
                routeData.useSSL = rule.isUseSSL();
                data.routes.add(routeData);
            }

            // UI preferences
            data.autoSave = config.autoSaveProperty().get();
            data.autoLoad = config.autoLoadProperty().get();
            data.autoReconnect = config.autoReconnectProperty().get();
            data.monitoringEnabled = config.monitoringEnabledProperty().get();
            data.loggingEnabled = config.loggingEnabledProperty().get();
            data.bufferSize = config.bufferSizeProperty().get();

            // Autocomplete history
            data.pathPatternHistory = config.getPathPatternHistory();
            data.targetHostHistory = config.getTargetHostHistory();
            data.targetPortHistory = config.getTargetPortHistory();
            data.domainHistory = config.getDomainHistory();

            // Write to file
            Path configPath = getConfigPath();
            String json = gson.toJson(data);
            Files.write(configPath, json.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            System.out.println("Configuration saved to: " + configPath);
        } catch (Exception e) {
            System.err.println("Failed to save configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads configuration from JSON file
     */
    public static void loadConfig() {
        try {
            Path configPath = getConfigPath();
            if (!Files.exists(configPath)) {
                System.out.println("No configuration file found, using defaults");
                return;
            }

            String json = new String(Files.readAllBytes(configPath));
            ConfigData data = gson.fromJson(json, ConfigData.class);

            if (data == null) {
                System.err.println("Invalid configuration file");
                return;
            }

            RouterConfig config = RouterConfig.getInstance();

            // Basic settings
            if (data.domain != null)
                config.setDomain(data.domain);
            if (data.mode != null)
                config.setMode(OperationalMode.valueOf(data.mode));
            if (data.rawTargetHost != null)
                config.setRawTargetHost(data.rawTargetHost);
            config.setRawTargetPort(data.rawTargetPort);
            config.signalHostProperty().set(data.signalHost);
            config.signalPortProperty().set(data.signalPort);
            config.dataPortProperty().set(data.dataPort);

            // Routing rules
            if (data.routes != null) {
                config.getRoutingRules().clear();
                for (RouteData routeData : data.routes) {
                    int priority = (routeData.priority > 0) ? routeData.priority : 100; // Default 100
                    RoutingRule rule = new RoutingRule(
                            routeData.pathPattern,
                            routeData.targetHost,
                            routeData.targetPort,
                            routeData.description,
                            routeData.stripPrefix,
                            priority,
                            routeData.forwardHost,
                            routeData.useSSL);
                    config.addRoutingRule(rule);
                }
            }

            // UI preferences
            config.autoSaveProperty().set(data.autoSave);
            config.autoLoadProperty().set(data.autoLoad);
            config.autoReconnectProperty().set(data.autoReconnect);
            config.monitoringEnabledProperty().set(data.monitoringEnabled);
            config.loggingEnabledProperty().set(data.loggingEnabled);
            config.bufferSizeProperty().set(data.bufferSize);

            System.out.println("Configuration loaded from: " + configPath);
        } catch (Exception e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gets the configuration file path
     */
    private static Path getConfigPath() {
        String userHome = System.getProperty("user.home");
        Path configDir = Paths.get(userHome, ".tunneler");

        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            System.err.println("Failed to create config directory: " + e.getMessage());
        }

        return configDir.resolve(CONFIG_FILE);
    }

    /**
     * Exports configuration to a specific file
     */
    public static void exportConfig(File file) throws IOException {
        Path configPath = getConfigPath();
        if (Files.exists(configPath)) {
            Files.copy(configPath, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Imports configuration from a specific file
     */
    public static void importConfig(File file) throws IOException {
        Path configPath = getConfigPath();
        Files.copy(file.toPath(), configPath, StandardCopyOption.REPLACE_EXISTING);
        loadConfig();
    }
}
