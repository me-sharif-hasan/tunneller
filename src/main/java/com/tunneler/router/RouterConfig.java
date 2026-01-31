package com.tunneler.router;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration manager with performance optimizations
 * Thread-safe, observable for UI binding
 */
public class RouterConfig {
    private static RouterConfig instance;

    // Domain configuration
    private final StringProperty domain = new SimpleStringProperty("lawfirm.inthespace.online");
    private final Set<String> domainHistory = new HashSet<>();

    // Operational mode
    private final ObjectProperty<OperationalMode> mode = new SimpleObjectProperty<>(OperationalMode.ROUTING_MODE);

    // Raw mode configuration
    private final StringProperty rawTargetHost = new SimpleStringProperty("127.0.0.1");
    private final IntegerProperty rawTargetPort = new SimpleIntegerProperty(80);

    // Signal server configuration
    private final StringProperty signalHost = new SimpleStringProperty("inthespace.online");
    private final IntegerProperty signalPort = new SimpleIntegerProperty(6060);
    private final IntegerProperty dataPort = new SimpleIntegerProperty(7070);

    // Routing rules with dual access patterns
    private final ObservableList<RoutingRule> routingRules = FXCollections.observableArrayList();
    private final Map<String, RoutingRule> exactMatchCache = new ConcurrentHashMap<>();

    // Autocomplete data
    private final Set<String> pathPatternHistory = new HashSet<>();
    private final Set<String> targetHostHistory = new HashSet<>();
    private final Set<Integer> targetPortHistory = new HashSet<>();

    // UI preferences
    private final BooleanProperty autoSave = new SimpleBooleanProperty(true);
    private final BooleanProperty autoLoad = new SimpleBooleanProperty(true);
    private final BooleanProperty autoReconnect = new SimpleBooleanProperty(true);
    private final BooleanProperty monitoringEnabled = new SimpleBooleanProperty(true);
    private final BooleanProperty loggingEnabled = new SimpleBooleanProperty(false);
    private final IntegerProperty bufferSize = new SimpleIntegerProperty(8192);

    // Web admin server configuration
    private final IntegerProperty adminPort = new SimpleIntegerProperty(8090);
    private final BooleanProperty adminAutoPort = new SimpleBooleanProperty(true); // Auto-find free port

    private RouterConfig() {
        // Initialize with default routes
        addDefaultRoutes();
    }

    public static synchronized RouterConfig getInstance() {
        if (instance == null) {
            instance = new RouterConfig();
        }
        return instance;
    }

    public void addDefaultRoutes() {
        addRoutingRule(new RoutingRule("/*", "localhost", 8080, "Default Fallback"));
    }

    /**
     * Listener for route changes
     */
    public interface RouteChangeListener {
        void onRoutesChanged();
    }

    private RouteChangeListener routeChangeListener;

    public void setRouteChangeListener(RouteChangeListener listener) {
        this.routeChangeListener = listener;
    }

    /**
     * Adds a routing rule and updates caches
     */
    public void addRoutingRule(RoutingRule rule) {
        routingRules.add(rule);

        // Update exact match cache if no wildcard
        if (!rule.getPathPattern().contains("*")) {
            exactMatchCache.put(rule.getPathPattern(), rule);
        }

        // Add to autocomplete history
        pathPatternHistory.add(rule.getPathPattern());
        targetHostHistory.add(rule.getTargetHost());
        targetPortHistory.add(rule.getTargetPort());

        // Notify listener
        if (routeChangeListener != null) {
            routeChangeListener.onRoutesChanged();
        }
    }

    /**
     * Removes a routing rule and updates caches
     */
    public void removeRoutingRule(RoutingRule rule) {
        routingRules.remove(rule);
        exactMatchCache.remove(rule.getPathPattern());

        // Notify listener
        if (routeChangeListener != null) {
            routeChangeListener.onRoutesChanged();
        }
    }

    /**
     * Finds the best matching route for a given path
     * Performance: O(1) for exact matches, O(n) for wildcard patterns
     */
    public RoutingRule findRoute(String path) {
        // Try exact match first (O(1))
        RoutingRule exactMatch = exactMatchCache.get(path);
        if (exactMatch != null) {
            return exactMatch;
        }

        // Try wildcard patterns (most specific first)
        RoutingRule bestMatch = null;
        int bestSpecificity = -1;

        for (RoutingRule rule : routingRules) {
            if (rule.matches(path)) {
                int specificity = rule.getPathPattern().length();
                if (specificity > bestSpecificity) {
                    bestMatch = rule;
                    bestSpecificity = specificity;
                }
            }
        }

        return bestMatch;
    }

    /**
     * Validates domain format (must end with .inthespace.online)
     */
    public boolean isValidDomain(String domain) {
        return domain != null && domain.endsWith(".inthespace.online");
    }

    /**
     * Gets the full domain (including .inthespace.online)
     */
    public String getFullDomain() {
        String domainValue = domain.get();
        if (!domainValue.endsWith(".inthespace.online")) {
            return domainValue + ".inthespace.online";
        }
        return domainValue;
    }

    // Property getters for JavaFX binding
    public StringProperty domainProperty() {
        return domain;
    }

    public ObjectProperty<OperationalMode> modeProperty() {
        return mode;
    }

    public StringProperty rawTargetHostProperty() {
        return rawTargetHost;
    }

    public IntegerProperty rawTargetPortProperty() {
        return rawTargetPort;
    }

    public StringProperty signalHostProperty() {
        return signalHost;
    }

    public IntegerProperty signalPortProperty() {
        return signalPort;
    }

    public IntegerProperty dataPortProperty() {
        return dataPort;
    }

    public ObservableList<RoutingRule> getRoutingRules() {
        return routingRules;
    }

    public BooleanProperty autoSaveProperty() {
        return autoSave;
    }

    public BooleanProperty autoLoadProperty() {
        return autoLoad;
    }

    public BooleanProperty autoReconnectProperty() {
        return autoReconnect;
    }

    public BooleanProperty monitoringEnabledProperty() {
        return monitoringEnabled;
    }

    public BooleanProperty loggingEnabledProperty() {
        return loggingEnabled;
    }

    public IntegerProperty bufferSizeProperty() {
        return bufferSize;
    }

    // Value getters
    public String getDomain() {
        return domain.get();
    }

    public OperationalMode getMode() {
        return mode.get();
    }

    public String getRawTargetHost() {
        return rawTargetHost.get();
    }

    public int getRawTargetPort() {
        return rawTargetPort.get();
    }

    public String getSignalHost() {
        return signalHost.get();
    }

    public int getSignalPort() {
        return signalPort.get();
    }

    public int getDataPort() {
        return dataPort.get();
    }

    public boolean isAutoSave() {
        return autoSave.get();
    }

    public int getBufferSize() {
        return bufferSize.get();
    }

    public int getAdminPort() {
        return adminPort.get();
    }

    public boolean isAdminAutoPort() {
        return adminAutoPort.get();
    }

    // Setters
    public void setDomain(String value) {
        domain.set(value);
        domainHistory.add(value);
    }

    public void setMode(OperationalMode value) {
        mode.set(value);
    }

    public void setRawTargetHost(String value) {
        rawTargetHost.set(value);
    }

    public void setRawTargetPort(int value) {
        rawTargetPort.set(value);
    }

    public void setSignalHost(String value) {
        signalHost.set(value);
    }

    public void setSignalPort(int value) {
        signalPort.set(value);
    }

    public void setDataPort(int value) {
        dataPort.set(value);
    }

    // Autocomplete getters
    public Set<String> getPathPatternHistory() {
        return new HashSet<>(pathPatternHistory);
    }

    public Set<String> getTargetHostHistory() {
        return new HashSet<>(targetHostHistory);
    }

    public Set<Integer> getTargetPortHistory() {
        return new HashSet<>(targetPortHistory);
    }

    public Set<String> getDomainHistory() {
        return new HashSet<>(domainHistory);
    }
}
