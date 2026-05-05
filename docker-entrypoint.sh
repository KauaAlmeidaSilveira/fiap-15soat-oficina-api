#!/bin/sh
set -e

KEY_DIR=/app/keys

mkdir -p "$KEY_DIR"

if [ ! -f "$KEY_DIR/jwt.private.key" ]; then
    echo "Gerando chaves JWT RSA..."
    openssl genrsa -out "$KEY_DIR/jwt.private.key" 2048
    openssl rsa -in "$KEY_DIR/jwt.private.key" -pubout -out "$KEY_DIR/jwt.public.key"
    echo "Chaves JWT geradas com sucesso."
fi

exec java -jar /app/app.jar
