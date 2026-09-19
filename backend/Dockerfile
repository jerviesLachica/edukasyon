FROM node:20-alpine

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm ci --only=production

# Copy source (node_modules excluded via .dockerignore)
COPY . .

# HF Spaces uses 7860, Koyeb/Render use 8080 — PORT env var handles both
EXPOSE 7860
EXPOSE 8080

USER appuser
CMD ["node", "server.js"]
