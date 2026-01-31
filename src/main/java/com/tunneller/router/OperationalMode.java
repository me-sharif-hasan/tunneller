package com.tunneller.router;

/**
 * Operational mode for the tunneler
 */
public enum OperationalMode {
    /**
     * Raw mode: Direct pipe, no HTTP inspection (legacy behavior)
     * Performance: 100% original, zero overhead
     */
    RAW_MODE,

    /**
     * Routing mode: HTTP path-based routing with dynamic rules
     * Performance: <1ms added latency per request
     */
    ROUTING_MODE
}
