import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Main {

    private static final String SIGNAL_HOST = "inthespace.online";
    private static final int SIGNAL_PORT = 6060;
    private static final int DATA_PORT = 7070;

    // Local target to forward to
    private static final String TARGET_HOST = "127.0.0.1";
    private static final int TARGET_PORT = 80;

    // The public hostname we want to register
    private static final String MY_HOSTNAME = "lawfirm.inthespace.online";

    public static void main(String[] args) {
        // Keep trying to connect to signal server
        while (true) {
            try {
                runClient();
            } catch (Exception e) {
                System.err.println("Signal connection failed or dropped: " + e.getMessage());
                System.err.println("Retrying in 3 seconds...");
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            }
        }
    }

    private static void runClient() throws IOException {
        System.out.println("Connecting to signal server " + SIGNAL_HOST + ":" + SIGNAL_PORT + "...");
        try (Socket signalSocket = new Socket(SIGNAL_HOST, SIGNAL_PORT)) {

            // 1. Register
            String registerCmd = "REGISTER " + MY_HOSTNAME + "\n"; // explicit newline
            signalSocket.getOutputStream().write(registerCmd.getBytes(StandardCharsets.UTF_8));
            signalSocket.getOutputStream().flush();
            System.out.println("Registered as " + MY_HOSTNAME);

            // 2. Listen for commands
            BufferedReader reader = new BufferedReader(new InputStreamReader(signalSocket.getInputStream(), StandardCharsets.UTF_8));

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("CONNECT ")) {
                    // Expected format: CONNECT <requestId>
                    String[] parts = line.split(" ");
                    if (parts.length < 2) {
                        System.err.println("Invalid CONNECT command: " + line);
                        continue;
                    }
                    String requestId = parts[1];

                    System.out.println("Received CONNECT request: " + requestId);

                    // Spawn a new thread to handle this specific tunnel
                    new Thread(() -> handleTunnel(requestId)).start();
                } else {
                    System.out.println("Unknown command: " + line);
                }
            }
        }
    }

    private static void handleTunnel(String requestId) {
        try {
            System.out.println("[" + requestId + "] Connecting to data server...");
            try (Socket dataSocket = new Socket(SIGNAL_HOST, DATA_PORT)) {

                // Handshake: REGISTER <hostname> <requestId>
                String handshake = "REGISTER " + MY_HOSTNAME + " " + requestId + "\n"; // explicit newline needed by Node.js server
                dataSocket.getOutputStream().write(handshake.getBytes(StandardCharsets.UTF_8));
                dataSocket.getOutputStream().flush();

                System.out.println("[" + requestId + "] Handshake sent. Connecting to local target...");

                try (Socket targetSocket = new Socket(TARGET_HOST, TARGET_PORT)) {
                    System.out.println("[" + requestId + "] Connected to target. Piping data...");

                    // Bi-directional pipe
                    Thread upstream = pipe(targetSocket.getInputStream(), dataSocket.getOutputStream(), requestId + "-up");
                    Thread downstream = pipe(dataSocket.getInputStream(), targetSocket.getOutputStream(), requestId + "-down");

                    upstream.join();
                    downstream.join();
                }
            }
            System.out.println("[" + requestId + "] Tunnel closed.");
        } catch (Exception e) {
            System.err.println("[" + requestId + "] Tunnel failed: " + e.getMessage());
        }
    }

    private static Thread pipe(InputStream in, OutputStream out, String name) {
        Thread t = new Thread(() -> {
            byte[] buffer = new byte[8192];
            int read;
            try {
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    out.flush();
                }
            } catch (IOException e) {
                // Normal closure usually happens here
            }
            // Ensure we close output to signal EOF to other side
            try { out.close(); } catch (IOException ignored) {}
        }, "Pipe-" + name);
        t.start();
        return t;
    }
}
