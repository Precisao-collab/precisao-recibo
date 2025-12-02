#!/bin/bash

# ============================================
# Script de Setup Inicial para EC2 t3.micro
# ============================================
# Execute este script APENAS UMA VEZ após criar a instância EC2
# ⚠️ Otimizado para t3.micro (1 GB RAM)
# ============================================

set -e

echo "============================================"
echo "Setup Inicial EC2 t3.micro - Backend Recibo"
echo "============================================"
echo ""

# Cores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Detectar distribuição
if [ -f /etc/redhat-release ]; then
    DISTRO="amazon"
    echo -e "${GREEN}Distribuição detectada: Amazon Linux${NC}"
elif [ -f /etc/debian_version ]; then
    DISTRO="ubuntu"
    echo -e "${GREEN}Distribuição detectada: Ubuntu/Debian${NC}"
else
    echo -e "${YELLOW}Distribuição não reconhecida. Tentando Amazon Linux...${NC}"
    DISTRO="amazon"
fi

# Atualizar sistema
echo -e "${YELLOW}Atualizando sistema...${NC}"
if [ "$DISTRO" = "amazon" ]; then
    sudo dnf update -y
    sudo dnf install -y git curl wget
elif [ "$DISTRO" = "ubuntu" ]; then
    sudo apt update
    sudo apt upgrade -y
    sudo apt install -y git curl wget
fi

# Instalar Java 21
echo -e "${YELLOW}Instalando Java 21...${NC}"
if [ "$DISTRO" = "amazon" ]; then
    sudo dnf install -y java-21-amazon-corretto
elif [ "$DISTRO" = "ubuntu" ]; then
    sudo apt install -y openjdk-21-jdk
fi

# Verificar instalação
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo -e "${GREEN}Java instalado: ${JAVA_VERSION}${NC}"

# Instalar Maven
echo -e "${YELLOW}Instalando Maven...${NC}"
if [ "$DISTRO" = "amazon" ]; then
    sudo dnf install -y maven
elif [ "$DISTRO" = "ubuntu" ]; then
    sudo apt install -y maven
fi

# Desabilitar serviços desnecessários para economizar memória
echo -e "${YELLOW}Otimizando serviços do sistema...${NC}"
if [ "$DISTRO" = "amazon" ]; then
    sudo systemctl disable postfix 2>/dev/null || true
    sudo systemctl stop postfix 2>/dev/null || true
elif [ "$DISTRO" = "ubuntu" ]; then
    sudo systemctl disable postfix 2>/dev/null || true
    sudo systemctl stop postfix 2>/dev/null || true
fi

# Criar swap de 1 GB (essencial para t3.micro)
echo -e "${YELLOW}Configurando swap de 1 GB...${NC}"
if [ ! -f /swapfile ]; then
    sudo dd if=/dev/zero of=/swapfile bs=1M count=1024
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    echo -e "${GREEN}Swap criado com sucesso!${NC}"
else
    echo -e "${GREEN}Swap já existe.${NC}"
fi

# Limpar cache do sistema
echo -e "${YELLOW}Limpando cache do sistema...${NC}"
if [ "$DISTRO" = "amazon" ]; then
    sudo dnf clean all
elif [ "$DISTRO" = "ubuntu" ]; then
    sudo apt clean
fi

# Criar diretório para aplicação
echo -e "${YELLOW}Criando estrutura de diretórios...${NC}"
mkdir -p ~/app/recibo
cd ~/app/recibo

# Mostrar informações de memória
echo ""
echo "============================================"
echo "Informações do Sistema:"
echo "============================================"
free -h
echo ""
df -h /
echo ""

# Configurar firewall local (se necessário)
echo -e "${YELLOW}Configurando firewall...${NC}"
if [ "$DISTRO" = "amazon" ]; then
    # Amazon Linux usa firewalld ou iptables
    if command -v firewall-cmd &> /dev/null; then
        sudo firewall-cmd --permanent --add-port=8080/tcp
        sudo firewall-cmd --reload
    fi
elif [ "$DISTRO" = "ubuntu" ]; then
    sudo ufw allow 8080/tcp
    sudo ufw --force enable
fi

echo ""
echo "============================================"
echo "✅ Setup concluído com sucesso!"
echo "============================================"
echo ""
echo "Próximos passos:"
echo "1. Faça upload do código para ~/app/recibo"
echo "2. Configure o arquivo .env (use env.t3micro.txt como base)"
echo "3. Execute: chmod +x deploy-t3micro.sh"
echo "4. Execute: ./deploy-t3micro.sh"
echo ""
echo "Para monitorar memória:"
echo "  watch -n 2 free -h"
echo ""
echo "Para ver processos usando mais memória:"
echo "  ps aux --sort=-%mem | head -10"
echo ""

