package com.tunneller.monitor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

/**
 * Thread-safe connection statistics tracker
 */
public class ConnectionStats {
    private static ConnectionStats instance;

    // Per-route statistics
    private final Map<String, RouteStats> routeStats = new ConcurrentHashMap<>();

    // Global stats
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong activeConnections = new AtomicLong(0);

    public static class RouteStats {
        public final String routePattern;
        public final AtomicLong totalRequests = new AtomicLong(0);
        public final AtomicLong activeRequests = new AtomicLong(0);
        public final List<Long> recentTimestamps = Collections.synchronizedList(new ArrayList<>());

        public RouteStats(String routePattern) {
            this.routePattern = routePattern;
        }

        public void recordRequest() {
            totalRequests.incrementAndGet();
            activeRequests.incrementAndGet();
            recentTimestamps.add(System.currentTimeMillis());

            // Keep only last 60 seconds
            long cutoff = System.currentTimeMillis() - 60000;
            recentTimestamps.removeIf(ts -> ts < cutoff);
        }

        public void completeRequest() {
            activeRequests.decrementAndGet();
        }

        public int getRequestsPerMinute() {
            return recentTimestamps.size();
        }
    }

    private ConnectionStats() {
    }

    public static synchronized ConnectionStats getInstance() {
        if (instance == null) {
            instance = new ConnectionStats();
        }
        return instance;
    }

    public void recordConnection(String routePattern) {
        totalConnections.incrementAndGet();
        activeConnections.incrementAndGet();
        routeStats.computeIfAbsent(routePattern, RouteStats::new).recordRequest();
    }

    public void completeConnection(String routePattern) {
        activeConnections.decrementAndGet();
        RouteStats stats = routeStats.get(routePattern);
        if (stats != null) {
            stats.completeRequest();
        }
    }

    public Map<String, RouteStats> getRouteStats() {
        return new HashMap<>(routeStats);
    }

    public long getTotalConnections() {
        return totalConnections.get();
    }

    public long getActiveConnections() {
        return activeConnections.get();
    }
}
