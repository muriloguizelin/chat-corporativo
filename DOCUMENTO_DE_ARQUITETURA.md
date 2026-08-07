# DOCUMENTO DE ARQUITETURA DE SOFTWARE E ESPECIFICAÇÃO TÉCNICA
## Sistema de Comunicação Corporativo Distribuído – Protocolo OSGURI

**Projeto:** Prova de Conceito (PoC) e Arquitetura do Chat Corporativo Governamental  
**Protocolo:** OSGURI (Open Secure Government Unified Relay Interface)  
**Tecnologia:** Java 21 (Standard Edition - Sem Frameworks Externos), TCP Sockets, Docker  
**Nível Técnico:** Engenharia de Software (Nível Pleno / Sênior)  
**Data:** Agosto / 2026  

---

## 1. SUMÁRIO EXECUTIVO

O sistema **OSGURI** foi projetado para prover uma infraestrutura de comunicação segura, auditável e distribuída para entes governamentais abrangendo mais de 30 estados e os quatro Poderes/Órgãos constitucionais: **Executivo, Legislativo, Judiciário e Controle**.

O objetivo principal desta documentação é detalhar as decisões arquiteturais, especificações do protocolo de camada de aplicação, mecanismo de ordenação global de mensagens, criptografia e matriz de restrição governamental, oferecendo clareza e rigidez técnica para engenheiros de software e auditores.

---

## 2. ARQUITETURA DO SISTEMA

### 2.1 Padrão Arquitetural
A solução adota o padrão **Cliente-Servidor mediado por Broker (Relay Broker)** via Sockets TCP puros (`java.net.Socket`, `java.net.ServerSocket`). 

- **Broker Centralizador (`ServidorBroker`)**: Atua como o elemento mediador unificado. Nenhum cliente estabelece conexão P2P direta com outro cliente, garantindo que toda mensagem seja inspecionada, autenticada, filtrada pela matriz de restrição de poderes e registrada para fins de não-repúdio.
- **Clientes CLI (`ClienteCLI`)**: Interfaces de linha de comando concorrentes que mantêm conexões Socket persistentes com o Broker, executando em threads distintas para recepção e transmissão.

### 2.2 Diagrama de Estruturação de Pacotes

```text
c:\Users\Muril\chat-corporativo\src\br\ufmt\osguri\
├── protocolo/
│   ├── Poder.java            -> Enumeração e Matriz Restritiva entre Poderes
│   ├── RelogioVetorial.java  -> Implementação de apoio/legada para Relógio Vetorial
│   ├── CriptografiaUtil.java -> Camada de Segurança (AES-256 e HMAC-SHA256)
│   ├── ProtocoloOSGURI.java  -> Constantes e Comandos do Protocolo
│   └── MensagemOSGURI.java   -> Envelope do Protocolo (Serialização/Desserialização)
├── broker/
│   ├── Grupo.java            -> Entidade de Grupos Institucionais e Privados
│   ├── LoggerNaoRepudio.java -> Audit Trail Imutável (Arquivo e Memória)
│   ├── ClienteHandler.java   -> Worker Runnable alocado no Pool de Threads
│   └── ServidorBroker.java   -> Servidor Principal (Thread Pool e Sequenciador Global)
└── client/
    └── ClienteCLI.java       -> Terminal Interativo CLI com menu numérico e comandos
```

### 2.3 Concorrência e Escalabilidade (Thread Pool)
Em vez de alocar uma nova thread de sistema operacional indefinidamente por conexão (`1 Thread per Connection`), o `ServidorBroker` utiliza o padrão **Thread Pool** via `java.util.concurrent.ExecutorService` (`Executors.newFixedThreadPool(100)`).

#### Justificativa Técnica:
1. **Prevenção de Thread Exhaustion**: Evita o esgotamento de memória stack do SO sob alta concorrência de clientes.
2. **Caminho para Escalabilidade Horizontal (Federação de Brokers)**: Em ambiente produtivo, múltiplos nós de Brokers podem rodar em cluster utilizando barramentos de mensageria internos (ex: Redis Pub/Sub ou Broker-to-Broker TCP Sockets) para trocar envelopes OSGURI. Nessa evolução, o sequenciamento global deve ser coordenado entre brokers por uma autoridade única de ordem, consenso ou particionamento explícito de sequência, preservando a validação de HMAC.

---

## 3. ESPECIFICAÇÃO DO PROTOCOLO OSGURI

O protocolo **OSGURI** é um protocolo textual de camada de aplicação estruturado em envelopes delimitados por caractere pipe (`|`) e codificados em UTF-8.

### 3.1 Formato do Envelope no Barramento TCP

```text
TIPO|REMETENTE|DESTINO|TIMESTAMP_LOGICO|CONTEUDO_CIFRADO|HMAC_SIGNATURE
```

| Campo | Descrição | Exemplo |
| :--- | :--- | :--- |
| **TIPO** | Operação realizada pelo protocolo | `LOGIN`, `MSG`, `ARQUIVO`, `ONLINE`, `GRUPO_MSG` |
| **REMETENTE** | ID único do remetente (normalizado) | `SP-SSP-MURILO` |
| **DESTINO** | ID do usuário de destino, grupo ou `BROKER` | `RJ-TJ-MARIA` ou `GRUPO_AUDITORIA` |
| **TIMESTAMP_LOGICO**| Sequência lógica global atribuída pelo Broker | `42` |
| **CONTEUDO_CIFRADO**| Cifragem AES-256 (Base64) do conteúdo útil | `k9aX8Q...==` |
| **HMAC_SIGNATURE** | Hash de autenticidade HMAC-SHA256 | `aF31b9...==` |

### 3.2 Tipos de Mensagens Suportados

- **`LOGIN`**: Registro inicial do cliente (`ID|Nome|Órgão|Poder`).
- **`MSG`**: Mensagem de texto privada direta entre dois usuários.
- **`ARQUIVO`**: Transferência de arquivo codificado em Base64 enviado no payload do envelope e salvo no destino na pasta `downloads/`.
- **`ONLINE` / `BUSCA`**: Solicitação ao Broker para listar os usuários atualmente conectados.
- **`GRUPO_CRIAR`**: Criação de grupo (`NomeGrupo|INSTITUCIONAL` ou `NomeGrupo|PRIVADO`).
- **`GRUPO_ENTRAR`**: Ingresso em um grupo existente.
- **`GRUPO_MSG`**: Transmissão de mensagem para todos os membros de um grupo.
- **`HISTORICO`**: Solicitação da trilha de auditoria e histórico de mensagens.
- **`OK` / `ERRO`**: Respostas de confirmação ou rejeição de comandos.

---

## 4. GOVERNANÇA E MATRIZ DE RESTRIÇÃO ENTRE PODERES

Um requisito essencial do sistema é a aplicação de políticas institucionais de segurança e independência entre Poderes.

### 4.1 Matriz de Comunicação (`Poder.podeComunicar`)

| Origem \ Destino | EXECUTIVO | LEGISLATIVO | JUDICIÁRIO | CONTROLE |
| :--- | :---: | :---: | :---: | :---: |
| **EXECUTIVO** | ✅ PERMITIDO | ❌ BLOQUEADO | ❌ BLOQUEADO | ✅ PERMITIDO |
| **LEGISLATIVO** | ❌ BLOQUEADO | ✅ PERMITIDO | ❌ BLOQUEADO | ✅ PERMITIDO |
| **JUDICIÁRIO** | ❌ BLOQUEADO | ❌ BLOQUEADO | ✅ PERMITIDO | ✅ PERMITIDO |
| **CONTROLE** | ✅ PERMITIDO | ✅ PERMITIDO | ✅ PERMITIDO | ✅ PERMITIDO |

- **Detalhamento**:
  - **Comunicação Intra-Poder**: Sempre permitida (membros do mesmo Poder conversam livremente).
  - **Órgãos de Controle (CGU, TCU, TCE)**: Possuem nível de acesso global para fiscalização e auditoria.
  - **Comunicação Inter-Poderes**: Bloqueada por padrão no servidor. A tentativa de envio retorna a mensagem de erro do protocolo:  
    `[ERRO/RESTRIÇÃO] BLOQUEIO ENTRE PODERES: Comunicacao direta proibida entre EXECUTIVO e JUDICIARIO`.

---

## 5. SISTEMAS DISTRIBUÍDOS: ORDENAÇÃO GLOBAL PELO BROKER

A implementação atual utiliza o Broker como autoridade central de ordenação. Em vez de depender do relógio local dos clientes, o `ServidorBroker` mantém um contador monotônico global (`AtomicLong timestampGlobal`) e atribui uma sequência lógica a cada mensagem encaminhada, arquivo, resposta de sistema ou mensagem de grupo.

O campo do envelope chamado `TIMESTAMP_LOGICO` foi preservado por compatibilidade de protocolo, mas seu conteúdo ativo agora é um número sequencial global gerado pelo Broker, como `1`, `2`, `3` e assim sucessivamente.

### 5.1 Algoritmo Ativo
1. O cliente envia comandos ao Broker com `TIMESTAMP_LOGICO` vazio ou `-`, sem impor ordem local.
2. O `ClienteHandler` valida autenticação, destino, grupo e matriz de restrição entre Poderes.
3. Antes de encaminhar a mensagem ou resposta, o Broker chama `gerarTimestampGlobal()`.
4. `gerarTimestampGlobal()` executa `timestampGlobal.incrementAndGet()`, garantindo incremento atômico mesmo com múltiplas conexões atendidas pelo pool de threads.
5. A nova `MensagemOSGURI` é reconstruída com a sequência global atribuída pelo Broker e então serializada, auditada e entregue ao destinatário.
6. O cliente armazena as mensagens recebidas e o comando `/historico` ordena a lista por comparação numérica do `TIMESTAMP_LOGICO`.

### 5.2 Impacto Arquitetural
- **Fonte única de verdade da ordem**: a ordem de entrega auditável passa a ser definida pelo Broker, não pelo relógio local do cliente.
- **Concorrência segura**: o uso de `AtomicLong` evita condições de corrida na geração de timestamps em ambiente multithread.
- **Auditoria determinística**: o log de não-repúdio registra `ORDEM_GLOBAL`, permitindo reconstruir a sequência de eventos processados pelo Broker.
- **Limite distribuído conhecido**: em uma futura federação de brokers, esse contador local precisará ser substituído ou complementado por um mecanismo de coordenação distribuída de sequência.

### 5.3 Relógio Vetorial
O componente `RelogioVetorial` permanece no pacote `protocolo` como implementação de apoio/legada para cenários de ordenação causal distribuída. Entretanto, no fluxo atual do sistema, clientes não incrementam nem propagam vetores, e o Broker não realiza merge vetorial. Portanto, a ordem efetiva observada pelo histórico e pela auditoria é a **ordem global sequencial atribuída pelo Broker**.

---

## 6. ARQUITETURA DE SEGURANÇA E AUDITORIA

### 6.1 Confidencialidade (AES-256)
- O conteúdo sensível da mensagem (texto ou dados em Base64 do arquivo) é cifrado no cliente remetente utilizando **AES em modo ECB com PKCS5Padding** antes de ser anexado ao envelope.
- Somente o receptor final decifra a mensagem.

### 6.2 Autenticidade e Integridade (HMAC-SHA256)
- Cada envelope carrega uma assinatura HMAC construída sobre os campos `TIPO|REMETENTE|DESTINO|TIMESTAMP_LOGICO|CONTEUDO_CIFRADO`.
- O Broker e o receptor validam a chave HMAC. Caso o pacote seja adulterado em trânsito, o envelope é rejeitado.

### 6.3 Não-Repúdio (`LoggerNaoRepudio`)
- O Broker grava uma trilha imutável de auditoria no arquivo `osguri_audit.log` e na memória.
- Cada entrada registra: `Timestamp de Parede`, `Tipo`, `ID Remetente`, `ID Destino`, `ORDEM_GLOBAL` e `Assinatura HMAC`.
- Como todas as mensagens são assinadas pelo remetente e validadas pelo Broker, o remetente não pode negar a autoria de uma mensagem enviada.

---

## 7. CONTAINERIZAÇÃO E DEPLOYMENT

A aplicação inclui artefatos para execução conteinerizada usando Docker.

### 7.1 Multi-Stage Dockerfile
- **Stage 1 (Builder)**: Utiliza `eclipse-temurin:21-jdk-alpine` para compilar o código fonte de `src/br/ufmt/osguri/*/*.java` gerando os bytecodes na pasta `bin/`.
- **Stage 2 (Runtime)**: Utiliza `eclipse-temurin:21-jre-alpine` (imagem leve de runtime) copiando apenas a pasta `bin/` para execução limpa.

### 7.2 Docker Compose Orchestration
- `broker`: Sobe o serviço do Broker escutando na porta `12345`.
- `cliente`: Permite iniciar múltiplos terminais CLI interativos conectados à rede interna do Docker (`OSGURI_HOST=broker`), mapeando a pasta `./downloads` para o host.

---

## 8. GUIA DE EXECUÇÃO E TESTES DA ENGENHARIA

### 8.1 Compilação Local (Java 17/21)
```powershell
# Na raiz do projeto:
javac -encoding UTF-8 -d bin src/br/ufmt/osguri/*/*.java
```

### 8.2 Execução Local (Sem Docker)
```powershell
# Terminal 1: Iniciar o Broker
java -cp bin br.ufmt.osguri.broker.ServidorBroker

# Terminal 2: Iniciar o Cliente 1
java -cp bin br.ufmt.osguri.client.ClienteCLI

# Terminal 3: Iniciar o Cliente 2
java -cp bin br.ufmt.osguri.client.ClienteCLI
```

### 8.3 Execução via Docker
```powershell
# Subir o Broker
docker compose up -d broker

# Executar cliente interativo (em quantas janelas desejar)
docker compose run --rm cliente
```

---

## 9. CONCLUSÃO

O projeto cumpre integralmente os requisitos da Prova de Conceito e da Especificação Técnica do **Protocolo OSGURI**. A solução entrega uma arquitetura limpa, robusta, altamente performática via pools de threads, criptograficamente segura e com governança estrita alinhada aos requisitos governamentais de independência entre Poderes.
