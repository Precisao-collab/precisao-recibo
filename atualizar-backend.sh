#!/bin/bash
# ============================================
# Script de Atualização Rápida do Backend
# ============================================
# Execute este script no EC2 após fazer upload
# dos arquivos atualizados
# ============================================

set -e

echo "============================================"
echo "Atualizando Backend - EC2"
echo "============================================"

cd ~/app/recibo/precisao-recibo

# Parar aplicação atual
echo "🛑 Parando aplicação atual..."
if [ -f app.pid ]; then
    kill $(cat app.pid) 2>/dev/null || true
    sleep 2
fi
pkill -f "recibo.*jar" 2>/dev/null || true
sleep 2

# Configurar Java
export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto
export PATH=$JAVA_HOME/bin:$PATH

# Rebuild
echo "📦 Recompilando aplicação..."
export JAVA_TOOL_OPTIONS="-Xmx256m -Xms128m"
mvn clean package -DskipTests

# Encontrar JAR
JAR_FILE=$(find target -name "*.jar" -not -name "*sources.jar" -not -name "*javadoc.jar" -not -name "*.original" | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "❌ Erro: JAR não encontrado"
    exit 1
fi

echo "✅ Build concluído: $JAR_FILE"

# Iniciar aplicação
echo "🚀 Iniciando aplicação..."
nohup java \
    -Xmx256m \
    -Xms128m \
    -XX:MaxMetaspaceSize=128m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -XX:+ExitOnOutOfMemoryError \
    -jar "$JAR_FILE" \
    > app.log 2>&1 &

APP_PID=$!
echo $APP_PID > app.pid

echo "⏳ Aguardando inicialização (15 segundos)..."
sleep 15

# Verificar
if ps -p $APP_PID > /dev/null; then
    echo ""
    echo "============================================"
    echo "✅ APLICAÇÃO ATUALIZADA E RODANDO!"
    echo "============================================"
    echo "PID: $APP_PID"
    echo "URL: http://18.221.148.28:8080"
    echo "Swagger: http://18.221.148.28:8080/swagger-ui.html"
    echo ""
    echo "Testando endpoints..."
    curl -s http://localhost:8080/recibos/validar-cpf?cpf=12345678901 | head -c 100
    echo ""
    echo "============================================"
else
    echo "❌ Falha ao iniciar. Verifique os logs:"
    tail -50 app.log
    exit 1
fi

