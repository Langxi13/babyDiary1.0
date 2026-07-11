FROM node:22-alpine AS build

WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npm run build

FROM nginx:1.27-alpine
COPY --chown=nginx:nginx frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --chown=nginx:nginx --from=build /workspace/frontend/dist /usr/share/nginx/html
RUN sed -i '/^user[[:space:]]/d' /etc/nginx/nginx.conf \
    && touch /run/nginx.pid \
    && chown -R nginx:nginx /run/nginx.pid /var/cache/nginx

USER nginx

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=12 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
