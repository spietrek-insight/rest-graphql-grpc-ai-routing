FROM node:22-alpine AS build
WORKDIR /workspace
COPY package*.json .npmrc ./
RUN npm ci
COPY . .
RUN npm run lint && npm test && npm run build
FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 8080
