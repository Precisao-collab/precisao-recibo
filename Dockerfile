# ============================================
# Dockerfile OTIMIZADO para EC2 t3.micro (1 GB RAM)
# ============================================
# ⚠️ AVISO: NÃO é recomendado usar Docker em t3.micro
# ⚠️ Docker consome ~100-200 MB de RAM adicional
# ⚠️ Use este Dockerfile apenas se realmente necessário
# ⚠️ Para melhor performance, execute o JAR diretamente
# ============================================

# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copiar arquivo de configuração do Maven
COPY pom.xml .

# Baixar dependências
RUN mvn dependency:go-offline -B

# Copiar código fonte
COPY src ./src

# Build da aplicação
RUN mvn clean package -DskipTests

# Runtime stage - usando JRE slim para economizar espaço
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copiar o JAR do stage de build
COPY --from=build /app/target/*.jar app.jar

# Expor porta
EXPOSE 8080

# Comando para executar a aplicação com configurações otimizadas para t3.micro
# Configurações de memória reduzidas para funcionar em 1 GB RAM
ENTRYPOINT ["java", \
    "-Xmx256m", \
    "-Xms128m", \
    "-XX:MaxMetaspaceSize=128m", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-XX:+UseStringDeduplication", \
    "-XX:+OptimizeStringConcat", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-jar", \
    "app.jar"]

