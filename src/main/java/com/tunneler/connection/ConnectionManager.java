package com.tunneler.connection;

import java.io.Closeable;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for ALL active connections, sockets, streams, and threads.
 * Provides centralized cleanup on disconnect.
 */
public class ConnectionManager {
    private static final ConnectionManager INSTANCE = new ConnectionManager();

    // Track all resources
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final Set<Closeable> streams = ConcurrentHashMap.newKeySet();
    private final Set<Thread> threads = ConcurrentHashMap.newKeySet();
    private volatile Socket signalSocket;

    private ConnectionManager() {
    }

    public static ConnectionManager getInstance() {
        return INSTANCE;
    }

    // === REGISTRATION ===

    public void registerSocket(Socket socket) {
        if (socket != null) {
            sockets.add(socket);
        }
    }

    public void registerStream(Closeable stream) {
        if (stream != null) {
            streams.add(stream);
        }
    }

    public void registerThread(Thread thread) {
        if (thread != null) {
            threads.add(thread);
        }
    }

    public void setSignalSocket(Socket socket) {
        this.signalSocket = socket;
        if (socket != null) {
            registerSocket(socket);
        }
    }

    public Socket getSignalSocket() {
        return signalSocket;
    }

    // === CLEANUP ===

    public void unregisterSocket(Socket socket) {
        sockets.remove(socket);
    }

    public void unregisterStream(Closeable stream) {
        streams.remove(stream);
    }

    public void unregisterThread(Thread thread) {
        threads.remove(thread);
    }

    /**
     * Close ALL active resources - sockets, streams, and interrupt threads.
     * Called on disconnect.
     */
    public synchronized void closeAll() {
        int socketCount = sockets.size();
        int streamCount = streams.size();
        int threadCount = threads.size();

        System.out.println("=== ConnectionManager: Closing all resources ===");
        System.out.println("  Sockets: " + socketCount);
        System.out.println("  Streams: " + streamCount);
        System.out.println("  Threads: " + threadCount);

        // 1. Close all sockets
        for (Socket socket : sockets) {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        sockets.clear();
        signalSocket = null;

        // 2. Close all streams
        for (Closeable stream : streams) {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        streams.clear();

        // 3. Interrupt all threads
        for (Thread thread : threads) {
            try {
                if (thread != null && thread.isAlive()) {
                    thread.interrupt();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        threads.clear();

        System.out.println("=== ConnectionManager: All resources closed ===");
    }

    /**
     * Get current resource counts (for debugging/monitoring)
     */
    public String getStatus() {
        return String.format("Connections: %d sockets, %d streams, %d threads",
                sockets.size(), streams.size(), threads.size());
    }
}
