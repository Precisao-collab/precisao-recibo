# Deploy via Console AWS - Guia Passo a Passo

Este guia é para fazer o deploy diretamente pelo console da AWS, usando **AWS CloudShell** ou **EC2 Instance Connect**.

## 🎯 Opção 1: Usando AWS CloudShell (Recomendado)

### Passo 1: Abrir CloudShell

1. No **AWS Console**, clique no ícone **CloudShell** (terminal) no topo da página
2. Aguarde o CloudShell inicializar (pode levar 1-2 minutos na primeira vez)

### Passo 2: Fazer Upload do Código

**Opção A: Via Git (se seu código está em repositório)**

```bash
# Criar diretório
mkdir -p ~/app/recibo
cd ~/app/recibo

# Clonar repositório
git clone SEU_REPOSITORIO_GIT .
```

**Opção B: Via Upload de Arquivos**

1. No CloudShell, clique no menu **Actions** → **Upload file**
2. Faça upload de um arquivo ZIP com todo o código do backend
3. Extrair o arquivo:

```bash
# O arquivo será salvo em ~/environment
cd ~/environment
unzip seu-arquivo.zip -d ~/app/recibo
cd ~/app/recibo
```

### Passo 3: Conectar à Instância EC2

```bash
# Conectar via SSH (substitua pelos seus valores)
ssh -i ~/.ssh/sua-chave.pem ec2-user@SEU_IP_PUBLICO

# Se não tiver a chave no CloudShell, faça upload dela primeiro
# Actions → Upload file → sua-chave.pem
# Depois:
chmod 400 ~/environment/sua-chave.pem
ssh -i ~/environment/sua-chave.pem ec2-user@SEU_IP_PUBLICO
```

---

## 🎯 Opção 2: Usando EC2 Instance Connect (Mais Direto)

### Passo 1: Conectar à Instância

1. No **EC2 Console**, selecione sua instância
2. Clique em **Connect**
3. Escolha a aba **EC2 Instance Connect**
4. Clique em **Connect**

### Passo 2: Setup Inicial (Execute apenas uma vez)

Copie e cole este bloco completo no terminal:

```bash
# Atualizar sistema
sudo dnf update -y

# Instalar dependências
sudo dnf install -y git java-21-amazon-corretto maven curl wget

# Criar swap de 1 GB
sudo dd if=/dev/zero of=/swapfile bs=1M count=1024
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Desabilitar serviços desnecessários
sudo systemctl disable postfix 2>/dev/null || true
sudo systemctl stop postfix 2>/dev/null || true

# Limpar cache
sudo dnf clean all

# Criar diretório da aplicação
mkdir -p ~/app/recibo
cd ~/app/recibo

echo "✅ Setup inicial concluído!"
free -h
```

### Passo 3: Fazer Upload do Código

**Método 1: Via SCP do seu computador local**

No seu computador (PowerShell no Windows):

```powershell
# Compactar o código
Compress-Archive -Path "recibo" -DestinationPath "recibo.zip"

# Fazer upload via SCP
scp -i "sua-chave.pem" recibo.zip ec2-user@SEU_IP_PUBLICO:~/app/
```

Depois, no terminal da EC2:

```bash
cd ~/app
unzip recibo.zip -d recibo
cd recibo/recibo
```

**Método 2: Via Git (se tiver repositório)**

```bash
cd ~/app/recibo
git clone SEU_REPOSITORIO_GIT .
```

**Método 3: Criar arquivos manualmente (para pequenos ajustes)**

```bash
# Usar nano ou vi para criar/editar arquivos
nano pom.xml
```

### Passo 4: Configurar Variáveis de Ambiente

```bash
cd ~/app/recibo

# Criar arquivo .env a partir do template
cat > .env << 'EOF'
# Gmail SMTP Configuration
SPRING_MAIL_USERNAME=seu-email@gmail.com
SPRING_MAIL_PASSWORD=sua-senha-app

# Email Configuration
APP_EMAIL_REMETENTE=seu-email@gmail.com
APP_EMAIL_NOME_REMETENTE=Sistema de Recibos - Precisao

# Backend URL (substitua pelo IP público da sua instância)
APP_BACKEND_URL=http://SEU_IP_PUBLICO:8080

# Frontend URL for CORS
APP_FRONTEND_URL=https://precisao-recibo-frontend.onrender.com

# Java Options OTIMIZADAS para t3.micro
JAVA_TOOL_OPTIONS=-Xmx256m -Xms128m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -XX:+OptimizeStringConcat

# API Externa - Empreendimentos
API_EMPREENDIMENTO_NM_SISTEMA=PRE
API_EMPREENDIMENTO_ID_PESSOAFISICA=2091
API_EMPREENDIMENTO_ID_SESSAO=f81b72d3-ab99-4078-9d03-77a4d9051420
API_EMPREENDIMENTO_ID_CHAVEDISPOSITIVO=api

# API Externa - Validação de CPF
API_CPF_VALIDACAO_URL=https://api.receitaws.com.br/v1/cpf
EOF

# Editar o arquivo com suas credenciais
nano .env
```

**Importante**: Substitua:
- `SEU_IP_PUBLICO` pelo IP público da sua instância EC2
- `seu-email@gmail.com` pelo seu email do Gmail
- `sua-senha-app` pela senha de app do Gmail

### Passo 5: Deploy da Aplicação

```bash
cd ~/app/recibo

# Tornar Maven Wrapper executável (se existir)
chmod +x mvnw 2>/dev/null || true

# Parar aplicação anterior se estiver rodando
pkill -f "recibo.*jar" 2>/dev/null || true

# Limpar builds anteriores
rm -rf target/

# Build da aplicação
export JAVA_TOOL_OPTIONS="-Xmx256m -Xms128m"
./mvnw clean package -DskipTests || mvn clean package -DskipTests

# Encontrar o JAR gerado
JAR_FILE=$(find target -name "*.jar" -not -name "*sources.jar" -not -name "*javadoc.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "❌ Erro: JAR não foi criado"
    exit 1
fi

echo "✅ Build concluído: $JAR_FILE"

# Carregar variáveis de ambiente
export $(cat .env | grep -v '^#' | xargs)

# Executar aplicação em background
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

# Aguardar alguns segundos
sleep 5

# Verificar se está rodando
if ps -p $APP_PID > /dev/null; then
    echo "✅ Aplicação iniciada com PID: $APP_PID"
    echo ""
    echo "============================================"
    echo "Aplicação rodando!"
    echo "============================================"
    echo "URL: http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):8080"
    echo "Logs: tail -f app.log"
    echo "Status: curl http://localhost:8080/actuator/health"
    echo "============================================"
else
    echo "❌ Aplicação não iniciou. Verifique os logs:"
    tail -50 app.log
fi
```

### Passo 6: Verificar se Está Funcionando

```bash
# Ver logs
tail -f app.log

# Testar API
curl http://localhost:8080/actuator/health

# Ver uso de memória
free -h

# Ver processos
ps aux --sort=-%mem | head -10
```

---

## 📋 Comandos Úteis

### Ver logs em tempo real:
```bash
tail -f ~/app/recibo/app.log
```

### Parar aplicação:
```bash
kill $(cat ~/app/recibo/app.pid)
```

### Reiniciar aplicação:
```bash
cd ~/app/recibo
# Execute novamente o Passo 5 (Deploy)
```

### Verificar uso de recursos:
```bash
# Memória
free -h

# CPU e processos
top

# Espaço em disco
df -h
```

### Obter IP público da instância:
```bash
curl http://169.254.169.254/latest/meta-data/public-ipv4
```

---

## 🔧 Troubleshooting

### Aplicação não inicia:
```bash
cd ~/app/recibo
tail -100 app.log
```

### Erro de memória:
```bash
# Verificar memória disponível
free -h

# Se necessário, reduzir ainda mais no .env
nano .env
# Altere JAVA_TOOL_OPTIONS para:
# JAVA_TOOL_OPTIONS=-Xmx192m -Xms96m -XX:MaxMetaspaceSize=96m
```

### Porta 8080 não acessível de fora:

1. Verifique o **Security Group** no EC2 Console
2. Adicione regra de entrada:
   - **Type**: Custom TCP
   - **Port**: 8080
   - **Source**: 0.0.0.0/0 (ou seu IP específico)

### Verificar se porta está aberta:
```bash
sudo netstat -tulpn | grep 8080
```

---

## 🚀 Script Completo de Deploy (Copiar e Colar)

Se preferir, aqui está um script completo que faz tudo de uma vez:

```bash
#!/bin/bash
set -e

echo "============================================"
echo "Deploy Backend Recibo - EC2 t3.micro"
echo "============================================"

# Verificar se está no diretório correto
if [ ! -f "pom.xml" ]; then
    echo "❌ Erro: Execute este script no diretório do backend (onde está o pom.xml)"
    exit 1
fi

# Parar aplicação anterior
pkill -f "recibo.*jar" 2>/dev/null || true
sleep 2

# Limpar builds anteriores
rm -rf target/

# Build
echo "📦 Compilando aplicação..."
export JAVA_TOOL_OPTIONS="-Xmx256m -Xms128m"
./mvnw clean package -DskipTests 2>&1 || mvn clean package -DskipTests 2>&1

# Encontrar JAR
JAR_FILE=$(find target -name "*.jar" -not -name "*sources.jar" -not -name "*javadoc.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "❌ Erro: JAR não encontrado"
    exit 1
fi

echo "✅ Build concluído: $JAR_FILE"

# Carregar .env se existir
if [ -f ".env" ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

# Executar
echo "🚀 Iniciando aplicação..."
nohup java \
    -Xmx256m -Xms128m \
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

sleep 5

if ps -p $APP_PID > /dev/null; then
    IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)
    echo "✅ Aplicação rodando!"
    echo "URL: http://$IP:8080"
    echo "PID: $APP_PID"
else
    echo "❌ Falha ao iniciar. Logs:"
    tail -50 app.log
    exit 1
fi
```

**Para usar o script:**
1. Salve como `deploy.sh` no diretório do backend
2. Execute: `chmod +x deploy.sh && ./deploy.sh`

---

## ✅ Checklist Final

- [ ] Instância EC2 t3.micro criada
- [ ] Security Group configurado (porta 8080 aberta)
- [ ] Conectado via EC2 Instance Connect
- [ ] Setup inicial executado
- [ ] Código enviado para a instância
- [ ] Arquivo `.env` configurado com credenciais
- [ ] Build executado com sucesso
- [ ] Aplicação rodando (verificar logs)
- [ ] API acessível de fora (testar com curl ou navegador)

---

**Pronto!** Sua aplicação deve estar rodando em `http://SEU_IP_PUBLICO:8080`

Para acessar a documentação Swagger: `http://SEU_IP_PUBLICO:8080/swagger-ui.html`

