# Build stage usando Java 21 JDK
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copia o código-fonte dos pacotes br.ufmt.osguri.* e realiza a compilação
COPY src/ ./src/
RUN mkdir bin && javac -encoding UTF-8 -d bin $(find src -name "*.java")

# Stage de execução usando Java 21 JRE
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copia o bytecode compilado
COPY --from=builder /app/bin ./bin

# Expõe a porta do Broker
EXPOSE 12345

# Por padrão inicia o Servidor Broker
CMD ["java", "-cp", "bin", "br.ufmt.osguri.broker.ServidorBroker"]
