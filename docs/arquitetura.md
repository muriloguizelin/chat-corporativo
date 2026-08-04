# Documento de Arquitetura de Software: Chat Corporativo Seguro da Federação

**Disciplina:** Sistemas Distribuídos — Universidade Federal de Mato Grosso (UFMT)  
**Autor:** Estudante de Ciência da Computação / Engenharia de Software  
**Versão:** 1.0.0  
**Data:** 2026  

---

## 1. Arquitetura Geral do Sistema

O sistema de **Chat Corporativo Seguro da Federação** adota uma **Arquitetura Híbrida Orientada a Eventos baseada em Broker Centralizador com Roteamento Distribuído Federativo**.

```
 +------------------+            +-------------------+            +------------------+
 |  Cliente CLI     |            | Servidor Broker   |            |  Cliente CLI     |
 |  (Executivo MT)  |            |   (Central SD)    |            |  (Judiciário MT) |
 |                  |            |                   |            |                  |
 | +--------------+ | Sockets    | +---------------+ | Sockets    | +--------------+ |
 | |NetworkListen.| | TCP (FED1) | | MessageBroker | | TCP (FED1) | |NetworkListen.| |
 | +--------------+ | <--------> | +---------------+ | <--------> | +--------------+ |
 | |  VectorClock | |            | | VectorClock   | |            | |  VectorClock | |
 | +--------------+ |            | +---------------+ |            | +--------------+ |
 +------------------+            | |  RBAC Engine  | |            +------------------+
                                 | +---------------+ |
                                 | | History (Log) | |
                                 | +---------------+ |
                                 +-------------------+
```

### Por que esta Arquitetura foi Escolhida?
1. **Controle Estrito de Soberania e Auditoria**: Um modelo puramente Peer-to-Peer (P2P) impediria o cumprimento dos requisitos federais de auditoria e a aplicação centralizada de políticas de acesso (RBAC entre os Poderes Executivo, Legislativo, Judiciário e Controle).
2. **Garantia de Entrega e Desempenho**: O Broker central gerencia as conexões Socket TCP simultâneas, mantendo filas de mensagens e tabelas de roteamento em memória (`ConcurrentHashMap`), entregando latência milissegundica.
3. **Desacoplamento e Federação**: Permite que os 26 Estados + DF operem seus próprios nós federados (Brokers regionais) interconectados no futuro sem alterar o protocolo do cliente final.

---

## 2. Protocolos de Rede e Comunicação

O sistema opera na **Camada de Aplicação (Layer 7 do modelo OSI)** diretamente sobre a camada de transporte **TCP (Transmission Control Protocol)**.

### Protocolo Próprio da Federação: `FED1`
Para evitar *overhead* excessivo do HTTP/REST em mensagens de tempo real, foi criado um **framing binário/textual customizado**:

#### Layout do Quadro (Frame) TCP:
```
+---------------------------------------------------------------------------------------------------+
| MAGIC_NUMBER | TYPE_CODE | SENDER_ID | TARGET_ID | VECTOR_CLOCK | SIGNATURE | FILE_NAME | PAYLOAD_LEN | PAYLOAD_BYTES |
|   (4 bytes)  | (4 bytes) |   (UTF)   |   (UTF)   |    (UTF)     |   (UTF)   |   (UTF)   |  (4 bytes)  |   (N bytes)   |
+---------------------------------------------------------------------------------------------------+
```

- **MAGIC_NUMBER (4 bytes)**: `0x46454431` ("FED1"). Garante que o Socket está recebendo quadros válidos do sistema.
- **TYPE_CODE (4 bytes)**: Inteiro identificando a ação (`1=LOGIN`, `3=TEXT_DIRECT`, `4=FILE_TRANSFER`, `7=CREATE_GROUP`, `8=GROUP_MSG`, etc.).
- **SENDER_ID / TARGET_ID (UTF-8)**: Identificadores no padrão da Federação (ex: `exec-mt-joao`, `jud-df-maria`).
- **VECTOR_CLOCK (UTF-8)**: Representação serializada do Relógio Vetorial (ex: `SERVER:4;exec-mt-joao:2`).
- **SIGNATURE (UTF-8)**: HMAC-SHA256 gerado pelo remetente para garantir **Autenticidade** e **Não-Repúdio**.
- **FILE_NAME (UTF-8)**: Nome do arquivo em transferências binárias.
- **PAYLOAD_LEN / PAYLOAD_BYTES**: Tamanho e array de bytes bruto do conteúdo (texto UTF-8 ou bytes de arquivos PDF/imagens).

---

## 3. Modelo de Comunicação

- **Comunicação Assíncrona e Duplex Integrada**:
  - Cada cliente mantém uma conexão Socket TCP persistente (`Keep-Alive`).
  - O cliente utiliza **duas Threads separadas**: a Thread principal escuta a digitação do usuário no CLI, enquanto a Thread secundária (`NetworkListener`) recebe mensagens do servidor de forma completamente não-bloqueante.
- **Paradigma Publish/Subscribe para Grupos**:
  - Mensagens de grupo utilizam o paradigma Pub/Sub gerenciado pelo Broker. Os clientes publicam no tópico do grupo (`grp-xxx`) e o servidor entrega aos inscritos/membros online.

---

## 4. APIs, Interfaces e Tipos de Mensagem

| Código | Tipo Enum | Origem -> Destino | Descrição / Payload |
| :--- | :--- | :--- | :--- |
| `1` | `LOGIN` | Cliente -> Servidor | Solicitação de registro da sessão do usuário |
| `2` | `LOGIN_RESP` | Servidor -> Cliente | Confirmador de Login (`SUCCESS` ou `ERROR`) |
| `3` | `TEXT_DIRECT` | Cliente -> Cliente | Mensagem de texto direta 1-para-1 com HMAC e VectorClock |
| `4` | `FILE_TRANSFER` | Cliente -> Cliente | Transferência de arquivos binários |
| `5` | `SEARCH_USERS` | Cliente -> Servidor | Solicitação de lista de usuários online |
| `6` | `SEARCH_RESP` | Servidor -> Cliente | Retorno da lista de usuários ativos na federação |
| `7` | `CREATE_GROUP` | Cliente -> Servidor | Criação de sala de grupo (Payload: `id;nome;adminOnly;poder`) |
| `8` | `GROUP_MSG` | Cliente -> Grupo | Mensagem difusão para todos os membros do grupo |
| `9` | `LIST_GROUPS` | Cliente -> Servidor | Solicitação de lista de grupos públicos/órgão |
| `11` | `HISTORY_REQ` | Cliente -> Servidor | Requisição do histórico ordenado de mensagens |
| `13` | `ERROR` | Servidor -> Cliente | Notificação de falha de segurança, RBAC ou protocolo |

---

## 5. Serviços do Sistema

1. **Serviço de Autenticação e Sessão (`ClientHandler`)**: Valida o login e mantém o estado de presença online do usuário via Sockets atétivos.
2. **Serviço de Roteamento e Intermediador (`MessageBroker`)**: Entrega mensagens direcionadas para o socket do destinatário correto.
3. **Módulo de Segurança e Não-Repúdio (`CryptoUtil`)**:
   - Assinatura HMAC-SHA256 em cada mensagem.
   - Criptografia simétrica AES-128 para confidencialidade do payload.
4. **Módulo RBAC / Políticas de Acesso (`RbacPolicyEngine`)**:
   - Aplica regras estritas entre os Poderes (Executivo, Legislativo, Judiciário e Controle).
   - Impede comunicação cruzada não autorizada entre instâncias estaduais distintas.
5. **Serviço de Ordenação Causal e Histórico (`HistoryManager` / `VectorClock`)**:
   - Ordena os eventos garantindo que a causa anteceda o efeito (*causal delivery*), prevenindo divergências decorrentes de atrasos de rede ou relógios físicos desalinhados.

---

## 6. Transparências em Sistemas Distribuídos

O projeto implementa rigorosamente as dimensões clássicas de transparência (Tanenbaum & Van Steen):

- **Transparência de Acesso**: O cliente interage com a API do protocolo (`ProtocolCodec`) sem precisar manipular ponteiros manuais de rede ou offsets de bytes.
- **Transparência de Localização**: O usuário envia mensagens informando apenas o ID lógico do destinatário (`jud-df-maria`). O sistema oculta completamente o IP e porta física onde a maria está conectada.
- **Transparência de Concorrência**: O Servidor Broker utiliza coleções thread-safe (`ConcurrentHashMap`, `CopyOnWriteArrayList`) e exclusão mútua fina para permitir que múltiplos clientes enviem e recebam mensagens exatamente ao mesmo tempo sem corromper o estado global.
- **Transparência de Falhas**: Se a rede oscilar ou a entrega direta falhar, o sistema captura a exceção, grava a mensagem no histórico causal persistente e envia uma resposta elegante de erro sem derrubar o cliente.

---

## 7. Escalabilidade

Para suportar o crescimento nacional (milhões de servidores e funcionários públicos):

1. **Escalabilidade Vertical**: Uso de Sockets NIO (`java.nio`) ou Thread Pool gerenciado (`ExecutorService.newCachedThreadPool()`) no servidor central.
2. **Escalabilidade Horizontal (Particionamento por Estado/UF)**:
   - Arquitetura de **Brokers Federados**: Cada estado possui seu Broker (`broker-mt.gov.br`, `broker-sp.gov.br`).
   - Brokering Inter-Estadual: Quando um usuário de MT fala com SP, o Broker de MT abre um canal TCP direto de servidor-para-servidor (*Server-to-Server Link*) com o Broker de SP.

---

## 8. Tolerância a Falhas e Recuperação

1. **Detecção de Falhas**:
   - *Heartbeat* e leitura de bloco com *timeout* no `DataInputStream`.
   - Lançamento de `IOException` quando a conexão cai inesperadamente, ativando imediatamente o desregistro do cliente (`unregisterClient`).
2. **Prevenção e Recuperação**:
   - **Persistência em Arquivo Log (`chat_history.log`)**: Todas as mensagens com seus respectivos Relógios Vetoriais são salvas em disco de forma síncrona.
   - **Reconexão Transparente**: Se o servidor reiniciar, o histórico gravado permite reconstituir o estado das conversas sem perda de dados.
