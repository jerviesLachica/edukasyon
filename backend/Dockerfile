# Dependencies
COPY package*.json ./
RUN npm ci --only=production

# Copy source
COPY . .

# Expose HF standard port
EXPOSE 7860

# Start backend (PORT=7860 will be injected by HF Spaces)
CMD ["node", "server.js"]
