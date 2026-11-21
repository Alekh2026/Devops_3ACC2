# ---------------------------------------------------------------
# Stage 1 - build the Angular app with Node
# ---------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# Install dependencies first so Docker can cache this layer
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npx ng build --configuration production

# ---------------------------------------------------------------
# Stage 2 - serve the static files with an unprivileged Nginx
# ---------------------------------------------------------------
FROM nginxinc/nginx-unprivileged:1.27-alpine

# Rendered by the image entrypoint (envsubst) into /etc/nginx/conf.d/default.conf
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 8080
