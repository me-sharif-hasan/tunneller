const net = require('net');

const SIGNAL_PORT = 6060;   // for commands
const DATA_PORT = 7070;     // for raw payload
const PROXY_PORT = 6061;    // receives user connections (Apache)

let socketIdSeq = 0;
let reqIdSeq = 0;

// hostname -> { signalSocket }
const clients = new Map();

// requestId -> userSocket (waiting for tunnel to connect)
const waitingRequests = new Map();

function now() { return new Date().toISOString(); }
function log(level, msg, meta = {}) {
    const m = Object.keys(meta).length ? ` ${JSON.stringify(meta)}` : '';
    console.log(`[${now()}] [${level}] ${msg}${m}`);
}

// ------------------------
// SIGNAL SERVER
// ------------------------
const signalServer = net.createServer(socket => {
    const sid = ++socketIdSeq;
    socket.__id = sid;

    log('INFO', 'signal_socket_connected', { sid, remote: socket.remoteAddress });

    let hostname = null;

    socket.once('data', chunk => {
        const text = chunk.toString('utf8').trim();
        // Expect: REGISTER <hostname>
        if (text.startsWith('REGISTER ')) {
            hostname = text.split(' ')[1].trim();
            // Close old connection if exists
            const existing = clients.get(hostname);
            if (existing && existing.signalSocket) {
                log('WARN', 'overwriting_existing_client', { hostname });
                existing.signalSocket.destroy();
            }

            clients.set(hostname, { signalSocket: socket });
            log('INFO', 'client_registered', { sid, hostname });

            socket.on('close', () => {
                log('INFO', 'signal_socket_disconnected', { sid, hostname });
                const c = clients.get(hostname);
                // Only unset if it's still us
                if (c && c.signalSocket === socket) {
                    clients.delete(hostname);
                }
            });

            socket.on('error', err => log('ERROR', 'signal_socket_error', { sid, hostname, error: err.message }));
        } else {
            log('WARN', 'first_data_not_register', { sid, data: text });
            socket.destroy();
        }
    });
});

// ------------------------
// DATA SERVER
// ------------------------
const dataServer = net.createServer(socket => {
    const sid = ++socketIdSeq;
    socket.__id = sid;
    // We don't log connection immediately to reduce noise, or we can:
    // log('INFO', 'data_socket_connected', { sid });

    // Expect first chunk: REGISTER <hostname> <requestId>
    socket.once('data', chunk => {
        const text = chunk.toString('utf8').trim();
        if (!text.startsWith('REGISTER ')) {
            log('WARN', 'data_first_chunk_not_register', { sid, data: text });
            socket.destroy();
            return;
        }

        // Parse: REGISTER <hostname> <requestId>
        const parts = text.split(' ');
        const hostname = parts[1];
        const requestId = parts[2];

        if (!hostname || !requestId) {
            log('WARN', 'invalid_data_register_format', { sid, text });
            socket.destroy();
            return;
        }

        const userSocket = waitingRequests.get(requestId);
        if (!userSocket) {
            log('WARN', 'req_id_not_found_or_expired', { sid, requestId });
            socket.destroy();
            return;
        }

        // Match found!
        waitingRequests.delete(requestId);
        log('INFO', 'tunnel_paired', { requestId, hostname });

        // Pipe the sockets
        // Note: userSocket might have buffered data (head of HTTP request),
        // but in our Proxy implementation we haven't read/buffered it yet?
        // Actually, we peeked at it to get the Host header. We must make sure we write that back.

        // In the proxy server below, we PAUSED the socket or put the first chunk back?
        // Let's modify Proxy Server to simply write the first chunk to the tunnel once connected.

        const firstChunk = userSocket.__firstChunk;
        if (firstChunk) {
            socket.write(firstChunk);
        }

        // Pipe rest
        userSocket.pipe(socket);
        socket.pipe(userSocket);

        // Errors/Close propagation
        socket.on('error', (err) => {
            log('ERROR', 'tunnel_socket_error', { requestId, error: err.message });
            userSocket.destroy();
        });
        userSocket.on('error', (err) => {
            log('ERROR', 'user_socket_error', { requestId, error: err.message });
            socket.destroy();
        });
    });
});

// ------------------------
// PROXY SERVER (Apache/Users)
// ------------------------
const proxyServer = net.createServer(userSocket => {
    const reqId = `req-${++reqIdSeq}-${Date.now()}`;
    const userId = ++socketIdSeq;
    userSocket.__id = userId;

    log('INFO', 'user_request_start', { reqId, remote: userSocket.remoteAddress });

    userSocket.once('data', chunk => {
        userSocket.pause(); // Stop reading until we have a tunnel
        userSocket.__firstChunk = chunk; // Store initial data

        const text = chunk.toString('utf8');
        const hostMatch = text.match(/^Host:\s*(.+?)(\r|\n)/im); // slightly more robust regex

        if (!hostMatch) {
            log('WARN', 'host_header_not_found', { reqId });
            userSocket.end('HTTP/1.1 400 Bad Request\r\n\r\nWidth Host header required');
            return;
        }

        const host = hostMatch[1].trim();
        const client = clients.get(host);

        if (!client || !client.signalSocket) {
            log('WARN', 'no_client_for_host', { reqId, host });
            userSocket.end('HTTP/1.1 502 Bad Gateway\r\n\r\nNo tunnel agent connected for this host');
            userSocket.destroy();
            return;
        }

        // Register in waiting map
        waitingRequests.set(reqId, userSocket);

        // Send CONNECT signal
        // Format: CONNECT <requestId> <host> (host is optional but good for debugging on client)
        // We really only need Request ID to pair.
        try {
            client.signalSocket.write(`CONNECT ${reqId}\n`);
            log('INFO', 'signal_sent', { reqId, host });
        } catch (err) {
            log('ERROR', 'signal_send_failed', { reqId, error: err.message });
            waitingRequests.delete(reqId);
            userSocket.destroy();
            return;
        }

        // Set a timeout for the tunnel to arrive
        setTimeout(() => {
            if (waitingRequests.has(reqId)) {
                log('WARN', 'tunnel_timeout', { reqId, host });
                waitingRequests.delete(reqId);
                userSocket.end('HTTP/1.1 504 Gateway Timeout\r\n\r\nTunnel agent did not respond');
                userSocket.destroy();
            }
        }, 10000); // 10s timeout
    });
});

signalServer.listen(SIGNAL_PORT, () => log('INFO', 'signal_server_listening', { port: SIGNAL_PORT }));
dataServer.listen(DATA_PORT, () => log('INFO', 'data_server_listening', { port: DATA_PORT }));
proxyServer.listen(PROXY_PORT, () => log('INFO', 'proxy_server_listening', { port: PROXY_PORT }));