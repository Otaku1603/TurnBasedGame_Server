FROM nginx:alpine

# 安装 openssl (用于生成证书)
RUN apk add --no-cache openssl

# 设置工作目录
WORKDIR /etc/nginx

# 复制配置文件
COPY nginx.conf /etc/nginx/nginx.conf

# 复制并设置启动脚本权限
COPY init-ssl.sh /docker-entrypoint.d/99-init-ssl.sh
RUN chmod +x /docker-entrypoint.d/99-init-ssl.sh

# Nginx 官方镜像会自动执行 /docker-entrypoint.d/ 下的所有 .sh 脚本后再启动 Nginx
# 所以我们不需要覆盖 ENTRYPOINT，直接利用官方机制即可