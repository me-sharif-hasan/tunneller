const http = require('http');
const PORT = process.env.PORT || 8080;
const NAME = process.env.SERVER_NAME || 'backend';

const server = http.createServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
        server: NAME,
        path: req.url,
        timestamp: new Date().toISOString(),
        host: req.headers.host,
        method: req.method
    }));
});

server.listen(PORT, () => {
    console.log(`${NAME} listening on :${PORT}`);
});
