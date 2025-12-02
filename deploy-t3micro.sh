#!/bin/bash

# ============================================
# Script de Deploy para EC2 t3.micro
# ============================================
# ⚠️ Este script é otimizado para instâncias t3.micro (1 GB RAM)
# ⚠️ NÃO é recomendado para produção com alto volume
# ============================================

set -e  # Para em caso de erro

echo "============================================"
echo "Deploy Backend Recibo - EC2 t3.micro"
echo "============================================"
echo ""

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Verificar se está no diretório correto
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}Erro: pom.xml não encontrado. Execute este script no diretório do backend.${NC}"
    exit 1
fi

# Verificar memória disponível
echo -e "${YELLOW}Verificando memória disponível...${NC}"
TOTAL_MEM=$(free -m | awk '/^Mem:/{print $2}')
echo "Memória total: ${TOTAL_MEM} MB"

if [ "$TOTAL_MEM" -lt 900 ]; then
    echo -e "${YELLOW}⚠️  Aviso: Memória muito baixa (< 900 MB). Pode haver problemas.${NC}"
fi

# Verificar se Java está instalado
if ! command -v java &> /dev/null; then
    echo -e "${RED}Java não encontrado. Instalando Java 21...${NC}"
    if [ -f /etc/redhat-release ]; then
        # Amazon Linux / CentOS / RHEL
        sudo dnf install -y java-21-amazon-corretto
    else
        # Ubuntu / Debian
        sudo apt update
        sudo apt install -y openjdk-21-jdk
    fi
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo -e "${GREEN}Java instalado: ${JAVA_VERSION}${NC}"

# Verificar se Maven está disponível
if [ ! -f "./mvnw" ]; then
    echo -e "${YELLOW}Maven Wrapper não encontrado. Instalando Maven...${NC}"
    if [ -f /etc/redhat-release ]; then
        sudo dnf install -y maven
    else
        sudo apt install -y maven
    fi
    MVN_CMD="mvn"
else
    chmod +x ./mvnw
    MVN_CMD="./mvnw"
fi

# Verificar arquivo .env
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}Arquivo .env não encontrado.${NC}"
    if [ -f "env.t3micro.txt" ]; then
        echo -e "${GREEN}Copiando env.t3micro.txt para .env...${NC}"
        cp env.t3micro.txt .env
        echo -e "${YELLOW}⚠️  IMPORTANTE: Edite o arquivo .env com suas credenciais antes de continuar!${NC}"
        echo "Pressione Enter após editar o .env..."
        read
    else
        echo -e "${RED}Erro: Arquivo .env não encontrado e env.t3micro.txt também não existe.${NC}"
        exit 1
    fi
fi

# Criar swap se não existir (recomendado para t3.micro)
if [ ! -f /swapfile ]; then
    echo -e "${YELLOW}Criando arquivo de swap de 1 GB...${NC}"
    sudo dd if=/dev/zero of=/swapfile bs=1M count=1024
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    echo -e "${GREEN}Swap criado com sucesso!${NC}"
else
    echo -e "${GREEN}Swap já existe.${NC}"
fi

# Parar aplicação anterior se estiver rodando
if pgrep -f "recibo.*jar" > /dev/null; then
    echo -e "${YELLOW}Parando aplicação anterior...${NC}"
    pkill -f "recibo.*jar"
    sleep 2
fi

# Limpar builds anteriores
echo -e "${YELLOW}Limpando builds anteriores...${NC}"
rm -rf target/

# Build da aplicação
echo -e "${YELLOW}Compilando aplicação...${NC}"
export JAVA_TOOL_OPTIONS="-Xmx256m -Xms128m"
$MVN_CMD clean package -DskipTests

# Verificar se o JAR foi criado
JAR_FILE=$(find target -name "*.jar" -not -name "*sources.jar" -not -name "*javadoc.jar" | head -1)
if [ -z "$JAR_FILE" ]; then
    echo -e "${RED}Erro: JAR não foi criado após o build.${NC}"
    exit 1
fi

echo -e "${GREEN}Build concluído: ${JAR_FILE}${NC}"

# Carregar variáveis de ambiente
if [ -f ".env" ]; then
    echo -e "${YELLOW}Carregando variáveis de ambiente do .env...${NC}"
    export $(cat .env | grep -v '^#' | xargs)
fi

# Executar aplicação em background com nohup
echo -e "${YELLOW}Iniciando aplicação...${NC}"
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
echo -e "${GREEN}Aplicação iniciada com PID: ${APP_PID}${NC}"
echo "PID salvo em: app.pid"
echo $APP_PID > app.pid

# Aguardar alguns segundos e verificar se está rodando
sleep 5

if ps -p $APP_PID > /dev/null; then
    echo -e "${GREEN}✅ Aplicação está rodando!${NC}"
    echo ""
    echo "============================================"
    echo "Informações:"
    echo "============================================"
    echo "PID: $APP_PID"
    echo "Logs: tail -f app.log"
    echo "Status: curl http://localhost:8080/actuator/health"
    echo "Parar: kill $APP_PID"
    echo "============================================"
else
    echo -e "${RED}❌ Aplicação não está rodando. Verifique os logs:${NC}"
    tail -20 app.log
    exit 1
fi

