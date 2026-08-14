FROM node:22-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY . .
ENV PORT=8092
EXPOSE 8091 8092 8093
CMD ["node", "server.js"]
