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

JAVA_OPTS=""
if [ -n "$NEW_RELIC_LICENSE_KEY" ]; then
    echo "New Relic: license key encontrada, ativando o agente APM."
    JAVA_OPTS="-javaagent:/app/newrelic/newrelic.jar"
else
    echo "New Relic: NEW_RELIC_LICENSE_KEY ausente, subindo sem o agente APM."
fi

exec java $JAVA_OPTS -jar /app/app.jar
