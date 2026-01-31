package com.tunneller.router;

/**
 * Represents a single routing rule for path-based forwarding
 */
public class RoutingRule {
    private final String pathPattern;
    private final String targetHost;
    private final int targetPort;
    private final String description;
    private final boolean stripPrefix; // If true, removes matched prefix before forwarding
    private final int priority; // Lower number = higher priority (checked first)
    private final boolean forwardHost; // If true, adds X-Forwarded-Host header
    private final boolean useSSL; // If true, uses SSL/TLS for HTTPS connections

    public RoutingRule(String pathPattern, String targetHost, int targetPort, String description) {
        this(pathPattern, targetHost, targetPort, description, false, 100, false, false);
    }

    public RoutingRule(String pathPattern, String targetHost, int targetPort, String description, boolean stripPrefix) {
        this(pathPattern, targetHost, targetPort, description, stripPrefix, 100, false, false);
    }

    public RoutingRule(String pathPattern, String targetHost, int targetPort, String description, boolean stripPrefix,
            int priority) {
        this(pathPattern, targetHost, targetPort, description, stripPrefix, priority, false, false);
    }

    public RoutingRule(String pathPattern, String targetHost, int targetPort, String description, boolean stripPrefix,
            int priority, boolean forwardHost) {
        this(pathPattern, targetHost, targetPort, description, stripPrefix, priority, forwardHost, false);
    }

    public RoutingRule(String pathPattern, String targetHost, int targetPort, String description, boolean stripPrefix,
            int priority, boolean forwardHost, boolean useSSL) {
        this.pathPattern = pathPattern;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.description = description;
        this.stripPrefix = stripPrefix;
        this.priority = priority;
        this.forwardHost = forwardHost;
        this.useSSL = useSSL;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public String getDescription() {
        return description;
    }

    public boolean isStripPrefix() {
        return stripPrefix;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isForwardHost() {
        return forwardHost;
    }

    public boolean isUseSSL() {
        return useSSL;
    }

    /**
     * Checks if this rule matches the given path
     */
    public boolean matches(String path) {
        if (pathPattern.equals(path)) {
            return true; // Exact match
        }

        // Wildcard matching: /api/* matches /api/anything
        if (pathPattern.endsWith("/*")) {
            String prefix = pathPattern.substring(0, pathPattern.length() - 2);
            // Strict prefix matching: match "/api" OR "/api/..." but NOT "/apiblah"
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }

        return false;
    }

    /**
     * Rewrites the path by stripping the matched prefix if enabled
     * 
     * Example:
     * Pattern: /api/users/*
     * stripPrefix: true
     * Input: /api/users/123
     * Output: /123
     * 
     * @param originalPath The original request path
     * @return Rewritten path (or original if stripPrefix is false)
     */
    public String rewritePath(String originalPath) {
        if (!stripPrefix) {
            return originalPath; // No rewriting
        }

        // Strip the prefix
        String newPath;
        if (pathPattern.endsWith("/*")) {
            // Remove the pattern prefix (without /*)
            String prefix = pathPattern.substring(0, pathPattern.length() - 2);
            if (originalPath.startsWith(prefix)) {
                newPath = originalPath.substring(prefix.length());
            } else {
                newPath = originalPath;
            }
        } else {
            // Exact match - return /
            newPath = "/";
        }

        // Ensure path starts with /
        if (!newPath.startsWith("/")) {
            newPath = "/" + newPath;
        }

        return newPath;
    }

    @Override
    public String toString() {
        return pathPattern + " -> " + targetHost + ":" + targetPort +
                " (priority=" + priority +
                (stripPrefix ? ", strip prefix)" : ")");
    }
}
