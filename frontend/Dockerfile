# UI image (spec 10.2): node build stage -> nginx serving dist/, /api/ proxied to ${BACKEND_URL}.
FROM node:20-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm install -g npm@11.19.1 --no-audit --no-fund && npm ci --no-audit --no-fund --ignore-scripts
COPY . .
ARG APP_VERSION=""
ENV VITE_APP_VERSION=$APP_VERSION
RUN npx vite build

FROM nginx:alpine
# Run as the unprivileged nginx user; envsubst writes the config at start.
RUN rm -f /etc/nginx/conf.d/default.conf \
 && chown -R nginx:nginx /var/cache/nginx /etc/nginx/conf.d \
 && touch /var/run/nginx.pid && chown nginx:nginx /var/run/nginx.pid
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY security-headers.conf /etc/nginx/security-headers.conf
COPY --from=build /src/dist /usr/share/nginx/html
ENV BACKEND_URL=http://backend:8080
USER nginx
EXPOSE 80
HEALTHCHECK --interval=5s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
