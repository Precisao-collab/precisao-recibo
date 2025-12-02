#!/bin/bash
# ============================================
# Script para Configurar Serviço Systemd
# ============================================
# Este script configura a aplicação para iniciar
# automaticamente quando a instância EC2 iniciar
# ============================================

set -e

echo "============================================"
echo "Configurando Serviço Systemd - Recibo"
echo "============================================"

# Verificar se está no diretório correto
if [ ! -f "target/recibo-0.0.1-SNAPSHOT.jar" ]; then
    echo "❌ Erro: Execute este script no diretório do backend"
    echo "Onde está o arquivo target/recibo-0.0.1-SNAPSHOT.jar"
    exit 1
fi

APP_DIR=$(pwd)
JAR_FILE="$APP_DIR/target/recibo-0.0.1-SNAPSHOT.jar"
ENV_FILE="$APP_DIR/.env"

echo "📁 Diretório da aplicação: $APP_DIR"
echo "📦 JAR: $JAR_FILE"

# Verificar se JAR existe
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ Erro: JAR não encontrado em $JAR_FILE"
    echo "Execute: mvn clean package -DskipTests"
    exit 1
fi

# Criar arquivo de serviço systemd
echo "📝 Criando arquivo de serviço systemd..."
sudo tee /etc/systemd/system/recibo.service > /dev/null <<EOF
[Unit]
Description=Recibo Backend Application
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=$APP_DIR
Environment="JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto"
Environment="PATH=/usr/lib/jvm/java-21-amazon-corretto/bin:/usr/local/bin:/usr/bin:/bin"
EOF

# Adicionar variáveis de ambiente do .env se existir
if [ -f "$ENV_FILE" ]; then
    echo "📝 Adicionando variáveis de ambiente do .env..."
    echo "EnvironmentFile=$ENV_FILE" | sudo tee -a /etc/systemd/system/recibo.service > /dev/null
fi

# Adicionar comando de execução
sudo tee -a /etc/systemd/system/recibo.service > /dev/null <<EOF

ExecStart=/usr/lib/jvm/java-21-amazon-corretto/bin/java \\
    -Xmx256m \\
    -Xms128m \\
    -XX:MaxMetaspaceSize=128m \\
    -XX:+UseG1GC \\
    -XX:MaxGCPauseMillis=200 \\
    -XX:+UseStringDeduplication \\
    -XX:+OptimizeStringConcat \\
    -XX:+ExitOnOutOfMemoryError \\
    -jar $JAR_FILE

Restart=always
RestartSec=10
StandardOutput=append:$APP_DIR/app.log
StandardError=append:$APP_DIR/app.log

[Install]
WantedBy=multi-user.target
EOF

# Recarregar systemd
echo "🔄 Recarregando systemd..."
sudo systemctl daemon-reload

# Habilitar serviço para iniciar automaticamente
echo "✅ Habilitando serviço para iniciar automaticamente..."
sudo systemctl enable recibo

# Parar aplicação atual se estiver rodando
echo "🛑 Parando aplicação atual (se estiver rodando)..."
pkill -f "recibo.*jar" 2>/dev/null || true
kill $(cat app.pid) 2>/dev/null || true
sleep 2

# Iniciar serviço
echo "🚀 Iniciando serviço..."
sudo systemctl start recibo

# Aguardar alguns segundos
sleep 5

# Verificar status
echo ""
echo "============================================"
echo "Status do Serviço:"
echo "============================================"
sudo systemctl status recibo --no-pager -l

echo ""
echo "============================================"
echo "✅ SERVIÇO CONFIGURADO!"
echo "============================================"
echo ""
echo "Comandos úteis:"
echo "  Status: sudo systemctl status recibo"
echo "  Iniciar: sudo systemctl start recibo"
echo "  Parar: sudo systemctl stop recibo"
echo "  Reiniciar: sudo systemctl restart recibo"
echo "  Logs: sudo journalctl -u recibo -f"
echo "  Logs do app: tail -f $APP_DIR/app.log"
echo ""
echo "O serviço iniciará automaticamente quando a instância EC2 iniciar."
echo ""

