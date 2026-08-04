# Chat Corporativo Seguro da Federação (Sockets TCP + Thymeleaf Web UI + Docker)

**Trabalho Prático da Disciplina de Sistemas Distribuídos — UFMT**

Este repositório contém o sistema completo de comunicação corporativo nacional e seguro para o governo federativo (Executivo, Legislativo, Judiciário e Órgãos de Controle nos 26 estados + DF).

Possui arquitetura híbrida com **Servidor Broker TCP em Sockets Puros**, **Cliente CLI para Terminal**, **Interface Web em Thymeleaf (Spring Boot)** e **Containerização Completa em Docker Compose**.

---

## 🚀 Como Rodar Tudo com Docker (Forma Recomendada)

Você pode subir **todo o ambiente** (Servidor Broker TCP + Interface Web Thymeleaf) com um único comando:

```bash
docker compose up --build
```

Após subir os contêineres:
- **Interface Web (Thymeleaf)**: Acesse **`http://localhost:8080`** no seu navegador.
- **Porta do Servidor Broker TCP**: Escutando na porta **`9090`**.

---

## 💻 Como Rodar Localmente (Sem Docker)

### 1. Compilação Compatível (Java 8 ou superior)
O projeto foi recompilado para ser compatível com **Java 8 (52.0)** ou qualquer versão superior do JDK:

```bash
javac -source 1.8 -target 1.8 -encoding UTF-8 -d target/classes (Get-ChildItem -Recurse -Filter *.java src/main/java/br/ufmt/sd/chat/common, src/main/java/br/ufmt/sd/chat/server, src/main/java/br/ufmt/sd/chat/client).FullName
jar -cf target/chat-corporativo-distribuido-1.0.0.jar -C target/classes .
```

### 2. Iniciar o Servidor Broker TCP
```bash
java -cp target/chat-corporativo-distribuido-1.0.0.jar br.ufmt.sd.chat.server.ServerMain
```

### 3. Iniciar o Cliente CLI no Terminal
```bash
java -cp target/chat-corporativo-distribuido-1.0.0.jar br.ufmt.sd.chat.client.ClientMain
```

---

## 🌐 Interface Web Thymeleaf (Spring Boot)

A interface Web foi criada utilizando **Spring Boot + Thymeleaf** com design moderno em Dark Mode e Glassmorphic:

1. **Tela de Login Federativa (`/login`)**:
   - Seleção dos 26 Estados + Distrito Federal (MT, SP, RJ, DF, etc.).
   - Seleção do Poder Institucional: Executivo (`exec`), Judiciário (`jud`), Legislativo (`leg`) ou Controle (`ctrl`).
   - Geração automática do ID Federativo: `<poder>-<uf>-<nome>` (ex: `exec-mt-pedro`).

2. **Painel do Chat (`/chat`)**:
   - Lista dinâmica de usuários online.
   - Bate-papo 1-para-1 com validação de **Assinatura Digital HMAC** e **Relógio Vetorial**.
   - Envio de arquivos diretamente pelo formulário Web (salvos na pasta `downloads/`).
   - Criação de grupos com restrição de Poder e apenas admin fala.

---

## 🐳 Estrutura dos Contêineres Docker

- **`Dockerfile.server`**: Empacota o Servidor Broker TCP na porta 9090.
- **`Dockerfile.web`**: Empacota a aplicação Spring Boot Web Thymeleaf na porta 8080.
- **`docker-compose.yml`**: Cria a rede `fed-chat-network` e interliga os serviços.
