# Tapnod backend, landing page, customer web app and laptop POS (linux/amd64).
FROM node:20-alpine
WORKDIR /app
COPY server/package.json server/package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY server/ ./
ENV NODE_ENV=production PORT=8080
EXPOSE 8080
USER node
CMD ["node", "server.js"]
