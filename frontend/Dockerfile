FROM mirror.ccs.tencentyun.com/library/node:18-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
RUN npm run build

FROM mirror.ccs.tencentyun.com/library/nginx:alpine
RUN sed -i 's#/run/nginx.pid#/tmp/nginx.pid#' /etc/nginx/nginx.conf
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
