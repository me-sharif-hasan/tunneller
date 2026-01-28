package com.tunneler.router;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Parser for HTTP requests - extracts method, path, and version
 * WITH DEBUG LOGGING to identify parsing issues
 */
public class HttpRequestParser {

    /**
     * Result of parsing the first line of an HTTP request
     */
    public static class ParseResult {
        public final String method; // e.g., "GET"
        public final String path; // e.g., "/api/users/123"
        public final String version; // e.g., "HTTP/1.1"
        public final int firstLineEndIndex; // Index where first line ends (for path rewriting)
        public final byte[] allBufferedBytes; // All bytes read (for forwarding)
        public final int totalBytesRead; // Total number of bytes

        public ParseResult(String method, String path, String version,
                int firstLineEndIndex, byte[] allBufferedBytes, int totalBytesRead) {
            this.method = method;
            this.path = path;
            this.version = version;
            this.firstLineEndIndex = firstLineEndIndex;
            this.allBufferedBytes = allBufferedBytes;
            this.totalBytesRead = totalBytesRead;
        }
    }

    /**
     * Parse the first line of an HTTP request from an InputStream
     * WITH EXTENSIVE DEBUG LOGGING
     */
    public static ParseResult parseFirstLine(InputStream input) throws IOException {
        System.out.println("[HttpRequestParser] ===== Starting HTTP Parse =====");

        byte[] buffer = new byte[8192];
        int totalRead = 0;
        int crlfIndex = -1;

        System.out.println("[HttpRequestParser] Reading from InputStream...");

        // Read chunks until we find CRLF (end of first line)
        while (totalRead < buffer.length) {
            int bytesRead = input.read(buffer, totalRead, buffer.length - totalRead);

            if (bytesRead == -1) {
                System.err.println("[HttpRequestParser] ERROR: Connection closed, totalRead=" + totalRead);
                if (totalRead == 0) {
                    System.err.println("[HttpRequestParser] NO DATA received - InputStream returned -1 immediately");
                }
                return null;
            }

            totalRead += bytesRead;
            System.out.println("[HttpRequestParser] Read " + bytesRead + " bytes (total: " + totalRead + ")");

            // Show first 100 chars for debugging
            if (totalRead <= 100) {
                String preview = new String(buffer, 0, totalRead, StandardCharsets.UTF_8)
                        .replace("\r", "\\r")
                        .replace("\n", "\\n");
                System.out.println("[HttpRequestParser] Data so far: " + preview);
            }

            // Look for CRLF in the newly read bytes
            for (int i = (totalRead - bytesRead); i < totalRead - 1; i++) {
                if (buffer[i] == '\r' && buffer[i + 1] == '\n') {
                    crlfIndex = i;
                    System.out.println("[HttpRequestParser] Found CRLF at index " + crlfIndex);
                    break;
                }
            }

            if (crlfIndex != -1) {
                break; // Found CRLF - end of first line
            }
        }

        if (crlfIndex == -1) {
            System.err.println("[HttpRequestParser] ERROR: No CRLF found in " + totalRead + " bytes");
            System.err.println("[HttpRequestParser] Data: "
                    + new String(buffer, 0, Math.min(totalRead, 200), StandardCharsets.UTF_8));
            return null;
        }

        // Extract first line to parse the HTTP request line
        String firstLine = new String(buffer, 0, crlfIndex, StandardCharsets.UTF_8);
        System.out.println("[HttpRequestParser] First line: '" + firstLine + "'");

        // Parse HTTP method, path, version
        // Format: GET /api/user HTTP/1.1
        String[] parts = firstLine.split(" ");
        if (parts.length < 3) {
            System.err.println("[HttpRequestParser] ERROR: Expected 3 parts, got " + parts.length);
            System.err.println("[HttpRequestParser] Parts: " + String.join(" | ", parts));
            return null;
        }

        String method = parts[0]; // e.g., "GET"
        String path = parts[1]; // e.g., "/api/users/123"
        String version = parts[2]; // e.g., "HTTP/1.1"

        System.out.println("[HttpRequestParser] SUCCESS: method=" + method + ", path=" + path + ", version=" + version);
        System.out.println("[HttpRequestParser] =============================");

        // Return the parsed result including all buffered bytes
        return new ParseResult(method, path, version, crlfIndex + 2, buffer, totalRead);
    }
}
