#!/bin/sh
# 适配 /docker-entrypoint.d/ 机制的脚本版本

CERT_DIR="/etc/nginx/cert"
KEY_FILE="$CERT_DIR/server.key"
CRT_FILE="$CERT_DIR/server.crt"

if [ ! -d "$CERT_DIR" ]; then
    mkdir -p "$CERT_DIR"
fi

if [ ! -f "$KEY_FILE" ] || [ ! -f "$CRT_FILE" ]; then
    echo "========================================================================"
    echo " [Auto-SSL] No SSL certs found. Generating self-signed certs for localhost..."
    echo "========================================================================"

    openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
        -keyout "$KEY_FILE" \
        -out "$CRT_FILE" \
        -subj "/C=CN/ST=Game/L=Dev/O=MyGame/OU=Server/CN=localhost"
else
    echo " [Auto-SSL] SSL certs found. Skipping generation."
fi

# 脚本结束后，Nginx 官方 Entrypoint 会继续启动 Nginx