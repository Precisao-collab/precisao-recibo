# Deploy no EC2 t3.micro - Guia Rápido

⚠️ **AVISO IMPORTANTE**: Este guia é para instâncias **t3.micro (1 GB RAM)**. Esta configuração é adequada apenas para **testes/desenvolvimento**. Para produção, use **t3.small (2 GB)** ou superior.

## 📋 Pré-requisitos

- Instância EC2 t3.micro criada
- Acesso SSH configurado
- Security Group configurado (porta 8080 aberta)

## 🚀 Deploy Rápido (3 Passos)

### Passo 1: Setup Inicial (Execute apenas uma vez)

```bash
# Conectar à instância EC2
ssh -i "sua-chave.pem" ec2-user@SEU_IP_PUBLICO

# Fazer upload do código (do seu computador local)
# No Windows PowerShell:
scp -i "sua-chave.pem" -r "recibo" ec2-user@SEU_IP_PUBLICO:~/app/

# Ou clonar do repositório:
cd ~/app
git clone SEU_REPOSITORIO .
cd recibo

# Executar setup inicial
chmod +x setup-ec2-t3micro.sh
./setup-ec2-t3micro.sh
```

### Passo 2: Configurar Variáveis de Ambiente

```bash
cd ~/app/recibo

# Copiar template de configuração
cp env.t3micro.txt .env

# Editar com suas credenciais
nano .env
```

**Importante**: Configure pelo menos:
- `SPRING_MAIL_USERNAME` - Email do Gmail
- `SPRING_MAIL_PASSWORD` - Senha de app do Gmail
- `APP_BACKEND_URL` - URL pública da sua instância EC2

### Passo 3: Deploy

```bash
# Tornar script executável
chmod +x deploy-t3micro.sh

# Executar deploy
./deploy-t3micro.sh
```

A aplicação estará rodando em: `http://SEU_IP_PUBLICO:8080`

## 📊 Monitoramento

### Ver logs em tempo real:
```bash
tail -f app.log
```

### Verificar uso de memória:
```bash
watch -n 2 free -h
```

### Ver processos usando mais memória:
```bash
ps aux --sort=-%mem | head -10
```

### Verificar se aplicação está rodando:
```bash
curl http://localhost:8080/actuator/health
```

## 🔧 Gerenciamento

### Parar aplicação:
```bash
kill $(cat app.pid)
```

### Reiniciar aplicação:
```bash
./deploy-t3micro.sh
```

### Ver status:
```bash
ps aux | grep java
```

## ⚙️ Configurações Otimizadas

O projeto foi configurado com:

- **Heap Java**: 256 MB máximo, 128 MB inicial
- **Garbage Collector**: G1GC (otimizado para baixa memória)
- **Swap**: 1 GB automático
- **Thread Pool**: Reduzido para 50 threads máximas
- **Timeouts**: Reduzidos para economizar recursos

## ⚠️ Limitações Conhecidas

1. **Memória limitada**: Aplicação pode travar ao gerar PDFs muito grandes
2. **Performance**: Respostas podem ser mais lentas sob carga
3. **Concorrência**: Limite de ~10-15 requisições simultâneas
4. **Docker**: NÃO recomendado usar (economiza ~200 MB sem Docker)

## 🐛 Troubleshooting

### Aplicação não inicia:
```bash
# Ver logs
tail -50 app.log

# Verificar memória
free -h

# Verificar se porta está em uso
sudo netstat -tulpn | grep 8080
```

### OutOfMemoryError:
```bash
# Reduzir ainda mais a memória no .env
JAVA_TOOL_OPTIONS=-Xmx192m -Xms96m -XX:MaxMetaspaceSize=96m
```

### Aplicação muito lenta:
- Considere upgrade para t3.small
- Verifique se há outros processos consumindo memória
- Verifique uso de swap: `free -h`

## 📝 Arquivos Criados

- `env.t3micro.txt` - Template de variáveis de ambiente otimizado
- `Dockerfile.t3micro` - Dockerfile otimizado (não recomendado usar)
- `deploy-t3micro.sh` - Script de deploy automatizado
- `setup-ec2-t3micro.sh` - Script de setup inicial
- `application-t3micro.properties` - Configurações Spring otimizadas

## 🔄 Atualizar Código

```bash
cd ~/app/recibo

# Se usar Git:
git pull

# Re-executar deploy
./deploy-t3micro.sh
```

## 💡 Dicas

1. **Monitore constantemente**: Use `watch -n 2 free -h` para acompanhar memória
2. **Logs**: Mantenha logs pequenos, rotacione se necessário
3. **Backup**: Configure backup do arquivo `.env`
4. **Alerts**: Configure alertas no CloudWatch para uso de memória > 90%

## 🆘 Suporte

Se encontrar problemas:
1. Verifique os logs: `tail -100 app.log`
2. Verifique memória: `free -h`
3. Verifique processos: `ps aux --sort=-%mem | head -10`
4. Considere upgrade para t3.small se problemas persistirem

---

**Lembrete**: Para produção, considere usar **t3.small (2 GB)** que oferece muito mais estabilidade por apenas ~$15/mês.

