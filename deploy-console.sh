#!/bin/bash
# ============================================
# Script de Deploy Rápido - Console AWS
# ============================================
# Copie e cole este script completo no terminal
# da EC2 Instance Connect ou CloudShell
# ============================================

set -e

echo "============================================"
echo "Deploy Backend Recibo - EC2 t3.micro"
echo "============================================"

# Verificar se está no diretório correto
if [ ! -f "pom.xml" ]; then
    echo "❌ Erro: Execute no diretório do backend (onde está pom.xml)"
    echo "Execute: cd ~/app/recibo"
    exit 1
fi

# Parar aplicação anterior
echo "🛑 Parando aplicação anterior..."
pkill -f "recibo.*jar" 2>/dev/null || true
sleep 2

# Limpar builds anteriores
echo "🧹 Limpando builds anteriores..."
rm -rf target/

# Build
echo "📦 Compilando aplicação..."
export JAVA_TOOL_OPTIONS="-Xmx256m -Xms128m"
if [ -f "./mvnw" ]; then
    chmod +x ./mvnw
    ./mvnw clean package -DskipTests
else
    mvn clean package -DskipTests
fi

# Encontrar JAR
JAR_FILE=$(find target -name "*.jar" -not -name "*sources.jar" -not -name "*javadoc.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "❌ Erro: JAR não encontrado após build"
    exit 1
fi

echo "✅ Build concluído: $JAR_FILE"

# Carregar .env se existir
if [ -f ".env" ]; then
    echo "📝 Carregando variáveis de ambiente do .env..."
    export $(cat .env | grep -v '^#' | xargs)
fi

# Executar
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

echo "⏳ Aguardando inicialização..."
sleep 5

# Verificar se está rodando
if ps -p $APP_PID > /dev/null; then
    IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || echo "SEU_IP_PUBLICO")
    echo ""
    echo "============================================"
    echo "✅ APLICAÇÃO RODANDO!"
    echo "============================================"
    echo "PID: $APP_PID"
    echo "URL: http://$IP:8080"
    echo "Health: http://$IP:8080/actuator/health"
    echo "Swagger: http://$IP:8080/swagger-ui.html"
    echo ""
    echo "Comandos úteis:"
    echo "  Logs: tail -f app.log"
    echo "  Parar: kill $APP_PID"
    echo "  Memória: free -h"
    echo "============================================"
else
    echo "❌ Falha ao iniciar aplicação"
    echo "Verifique os logs:"
    tail -50 app.log
    exit 1
fi

