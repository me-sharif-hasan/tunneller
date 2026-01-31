FROM node:18-alpine

WORKDIR /app

# Install dependencies
COPY package.json .
RUN npm install

# Copy server script
COPY server.js .

# Port will be set via ENV
EXPOSE 8080

CMD ["node", "server.js"]
