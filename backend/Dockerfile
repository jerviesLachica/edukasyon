FROM node:20-alpine

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm ci --only=production

# Copy source (node_modules excluded via .dockerignore)
COPY . .

# Koyeb / most hosts use PORT env var, default 8080
EXPOSE 8080

USER appuser
CMD ["node", "server.js"]
