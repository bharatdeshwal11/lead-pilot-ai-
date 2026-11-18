FROM node:24-alpine AS build
RUN --mount=type=bind,target=/source \
    mkdir -p /tmp/leadpilot /out/etc/nginx/conf.d /out/usr/share/nginx/html && \
    cp -R /source/. /tmp/leadpilot/ && cd /tmp/leadpilot && npm ci && npm run build && \
    cp nginx.conf /out/etc/nginx/conf.d/default.conf && cp -R dist/. /out/usr/share/nginx/html/ && \
    rm -rf /tmp/leadpilot /root/.npm

FROM nginx:1.29-alpine
COPY --from=build /out/ /
EXPOSE 80
