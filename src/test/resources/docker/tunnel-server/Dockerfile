FROM node:18-alpine

WORKDIR /app

# Copy tunnel server
COPY tunnel-server.js .

# Expose ports
EXPOSE 6060 7070 6061

# Start server
CMD ["node", "tunnel-server.js"]
