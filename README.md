# Tunneller - Dynamic HTTP Router

Dynamic HTTP router with JavaFX UI for path-based routing to different servers.

## What is Tunneller?

Tunneller is a **Fast Reverse Proxy (FRP) Client** that exposes your local servers (running on localhost) to the public internet securely and efficiently. It connects to a remote relay server and forwards traffic to your local services, allowing you to demo apps, test webhooks, or access local resources remotely without configuring port forwarding on your router.

### Why Tunneller?

Unlike generic tunneling tools like **ngrok** or **Cloudflared**, Tunneller is built with specific developer needs in mind:

- **🔐 Permanent Free Subdomains**: We use **[inthespace.online](https://inthespace.online)** to provide consistent, free subdomains for your tunnels. No more random, changing URLs every time you restart.
- **🔀 Path-Based Routing**: Route traffic to different local ports based on the URL path (e.g., `/api` -> localhost:8080, `/frontend` -> localhost:3000) using a single tunnel connection.
- **🖥️ Native JavaFX UI**: A clean, dark-themed GUI (IntelliJ style) to manage your routes and connection status easily, without memorizing CLI commands.
- **⚡ High Performance**: Built with Java 25 Virtual Threads to handle high concurrency with minimal overhead.

## Downloads

Use stable releases for production.
**[Download Latest Release](https://github.com/me-sharif-hasan/tunneller/releases)**

| Platform | Format |
| :--- | :--- |
| ![Windows](https://img.shields.io/badge/Windows-0078D6?style=for-the-badge&logo=windows&logoColor=white) | `.exe` Installer |
| ![Linux](https://img.shields.io/badge/Linux-FCC624?style=for-the-badge&logo=linux&logoColor=black) | `.deb` Package |

## Features

- ✅ **Routing Mode**: HTTP path-based routing to multiple local services.
- ✅ **Raw Mode**: Direct TCP pipe for 100% original performance (single target).
- ✅ **Auto-complete**: Smart suggestions for your routes.
- ✅ **Configuration Persistence**: Remembers your settings and routes between restarts.

## Tech Stack

- **Java**: 25 (Preview Features enabled)
- **JavaFX**: 25.0.1
- **Build Tool**: Maven

## System Architecture

Tunneller uses a dual-connection architecture to ensure reliability and performance:

1.  **Signal Connection**: A persistent control channel that handles registration, heartbeats (`PING`/`PONG`), and connection requests.
2.  **Data Connections**: On-demand connections created when a user accesses your tunnel URL.
    - Uses **Java 21+ Virtual Threads** to spin up lightweight threads for every incoming request, allowing the client to handle thousands of concurrent connections effortlessly.

### Routing Logic
- **RouteHandlers**: Each route is isolated in its own handler.
- **Specificity Sorting**: Routes are automatically sorted by specificity. Exact matches have higher priority than wildcard matches (e.g., `/api/v1` takes precedence over `/api/*`).

## Quick Start

### Prerequisites
- **Java 25 JDK** installed.
- **Maven** installed.

### Run Locally

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/me-sharif-hasan/tunneller.git
    cd tunneller
    ```

2.  **Run with Maven**:
    ```bash
    mvn javafx:run
    ```

### IDE Setup (IntelliJ IDEA)

Since this project uses Java 25 and JavaFX modules, you may need to add VM options if running directly from a main method in your IDE.

**VM Options:**
```text
--module-path "C:\Program Files\Java\javafx-sdk-25.0.2\lib" --add-modules javafx.controls,javafx.fxml
```
*(Update the path to match your local JavaFX SDK installation)*

## Build

To build the executable JAR or platform-specific installers:

```bash
# Build JAR
mvn clean package

# Run JAR
java -jar target/tunneller-router-1.0.0-shaded.jar
```
