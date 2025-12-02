#!/bin/bash
# ============================================
# Setup Inicial - Console AWS
# ============================================
# Execute este script APENAS UMA VEZ após criar a instância
# Copie e cole completo no terminal
# ============================================

set -e

echo "============================================"
echo "Setup Inicial EC2 t3.micro"
echo "============================================"

# Atualizar sistema
echo "📦 Atualizando sistema..."
sudo dnf update -y

# Instalar dependências
echo "📦 Instalando dependências..."
sudo dnf install -y git java-21-amazon-corretto maven curl wget

# Verificar Java
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo "✅ Java instalado: $JAVA_VERSION"

# Criar swap de 1 GB
echo "💾 Configurando swap de 1 GB..."
if [ ! -f /swapfile ]; then
    sudo dd if=/dev/zero of=/swapfile bs=1M count=1024
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    echo "✅ Swap criado"
else
    echo "✅ Swap já existe"
fi

# Desabilitar serviços desnecessários
echo "🔧 Otimizando serviços..."
sudo systemctl disable postfix 2>/dev/null || true
sudo systemctl stop postfix 2>/dev/null || true

# Limpar cache
sudo dnf clean all

# Criar diretório da aplicação
echo "📁 Criando estrutura de diretórios..."
mkdir -p ~/app/recibo
cd ~/app/recibo

# Mostrar informações
echo ""
echo "============================================"
echo "✅ SETUP CONCLUÍDO!"
echo "============================================"
echo ""
echo "Informações do sistema:"
free -h
echo ""
df -h /
echo ""
echo "Próximos passos:"
echo "1. Faça upload do código para ~/app/recibo"
echo "2. Configure o arquivo .env"
echo "3. Execute: cd ~/app/recibo && bash deploy-console.sh"
echo ""

