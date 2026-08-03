  # Prova de Conceito (PoC) - ChatGov (Chat Corporativo Distribuído)

Esta é a implementação da Prova de Conceito (PoC) para o sistema de comunicação corporativo distribuído e seguro governamental, utilizando **Sockets TCP puros** em Java e um **protocolo binário de aplicação customizado**.

## 🛠️ Tecnologias Utilizadas
*   **Linguagem**: Java 17+
*   **Gerenciador de Dependências**: Maven 3.x+
*   **Serialização**: Jackson XML (para representação de metadados das mensagens/arquivos em XML)
*   **Redes**: `java.net.ServerSocket`, `java.net.Socket` (I/O bloqueante com Thread Pool para concorrência)

---

## 🏗️ Estrutura do Protocolo Binário Customizado
Cada mensagem que viaja sobre o socket TCP possui um cabeçalho rígido de **14 bytes**:

| Campo | Tamanho | Tipo | Descrição |
|---|---|---|---|
| **Magic Number** | 4 bytes | Int | Identificador fixo da aplicação (`0x43484154` = "CHAT") |
| **Version** | 1 byte | Byte | Versão atual do protocolo (`0x01`) |
| **Message Type** | 1 byte | Byte | Operação a ser realizada (`0x01` a `0x08`) |
| **Payload Length** | 4 bytes | Int | Tamanho em bytes do payload que virá em seguida |
| **Checksum** | 4 bytes | Int | Soma de verificação CRC32 do payload |

Os tipos de mensagens (`Message Type`) suportados são:
*   `0x01` (LOGIN): Registro inicial de identidade.
*   `0x02` (TEXT): Mensagem de texto direcionada (1:1).
*   `0x03` (FILE_START): Metadados de início de transferência de arquivo.
*   `0x04` (FILE_CHUNK): Fragmento de dados binários do arquivo.
*   `0x05` (ACK): Confirmação de recebimento/sucesso.
*   `0x06` (ERROR): Notificação de erro/violação de restrição.
*   `0x07` (BROADCAST): Mensagem enviada para todos os membros de um grupo.
*   `0x08` (LOGOUT): Encerramento de sessão.

---

## 🚀 Como Compilar o Projeto
O projeto está dividido em dois módulos independentes (`server` e `client`). Ambos contêm as definições do protocolo compartilhado.

1.  Abra o terminal no diretório principal `/poc`.
2.  Execute o comando de compilação do Maven para gerar os executáveis (Fat JARs):
    ```bash
    # Compilar e empacotar o Servidor
    cd server
    mvn clean package
    cd ..

    # Compilar e empacotar o Cliente
    cd client
    mvn clean package
    cd ..
    ```

---

## 💻 Como Executar o Protótipo

### 1. Iniciar o Servidor
O servidor escuta conexões de entrada. A porta padrão é `8080`.
```bash
java -jar server/target/chat-server-1.0-SNAPSHOT.jar [porta]
```
Exemplo:
```bash
java -jar server/target/chat-server-1.0-SNAPSHOT.jar 8080
```

### 2. Iniciar os Clientes
Abra novos terminais para cada cliente que deseja simular. O executável aceita opcionalmente o IP e a Porta do servidor.
```bash
java -jar client/target/chat-client-1.0-SNAPSHOT.jar [host] [porta]
```
Exemplo (conectando no localhost na porta 8080):
```bash
java -jar client/target/chat-client-1.0-SNAPSHOT.jar localhost 8080
```


### 3. Executando com Docker

Você também pode compilar e executar o projeto inteiramente em containers Docker, eliminando a necessidade de JDK ou Maven instalados localmente no seu host.

#### Iniciar o Servidor via Docker Compose
No diretório `/poc`, execute o Docker Compose para compilar e iniciar o servidor de chat:
```bash
docker-compose up --build
```
O servidor estará ativo e escutando na porta `8080` do seu `localhost`.

#### Iniciar o Cliente via Docker
Como o cliente necessita de interação de console via teclado (STDIN e TTY), ele deve ser executado no modo interativo utilizando as flags `-it`.

1. Construa a imagem Docker do cliente:
   ```bash
   docker build -t chat-client ./client
   ```

2. Inicie containers de clientes em terminais separados:
   * **Conectar usando a rede do host (Recomendado para Windows/Linux)**:
     ```bash
     docker run -it --network="host" chat-client localhost 8080
     ```
   * **Conectar usando a rede bridge criada pelo Docker Compose**:
     ```bash
     docker run -it --network="poc_chat-network" chat-client chat-server 8080
     ```

---

## 💬 Manual de Comandos do Cliente
Assim que o cliente se conectar, ele exibirá um prompt interativo (`> `). Utilize os seguintes comandos:

### 1. `/login <estado>.<orgao>.<usuario>`
Realiza o registro no servidor. O identificador deve seguir o padrão hierárquico da federação para aplicar as regras de controle.
*   Exemplo 1: `/login sp.tjsp.alice`
*   Exemplo 2: `/login rj.tjrj.bob`
*   Exemplo 3: `/login df.sefaz.carlos`

### 2. `/join <grupo>`
Ingressa em um canal de comunicação. Grupos podem ter restrições de ingresso por órgão.
*   Exemplo 1 (sucesso): `/join nacional.geral` (livre para todos)
*   Exemplo 2 (sucesso se for tjsp): `/join sp.tjsp.grupo` (exige `tjsp` na hierarquia do usuário)
*   Exemplo 3 (bloqueio de segurança): Se o usuário `df.sefaz.carlos` tentar `/join sp.tjsp.grupo`, o servidor retornará um `ERRO` informando a restrição de ingresso.

### 3. `/send <usuario_destino> <mensagem>`
Envia uma mensagem privada 1:1. O sistema verifica se há restrições de comunicação entre os órgãos.
*   Exemplo 1 (sucesso): Alice (`sp.tjsp.alice`) envia para Bob (`rj.tjrj.bob`):
    `/send rj.tjrj.bob Olá Bob!`
*   Exemplo 2 (bloqueio de segurança - Judiciário de um estado não fala com Executivo de outro):
    Se Alice (`sp.tjsp.alice` - Judiciário) tentar falar com Carlos (`df.sefaz.carlos` - Executivo/Sefaz de outro estado):
    `/send df.sefaz.carlos Olá Carlos!`
    O servidor interceptará o pacote e enviará de volta um `ERRO: Bloqueado por restrições de comunicação`.

### 4. `/broadcast <grupo> <mensagem>`
Envia uma mensagem para todos os membros que já entraram no grupo especificado.
*   Exemplo: `/broadcast nacional.geral Alerta para todos os funcionários públicos`

### 5. `/sendfile <usuario_destino> <caminho_arquivo>`
Transmite um arquivo local por meio do socket TCP em blocos (chunks), validando a integridade por meio de CRC32.
*   Exemplo: `/sendfile rj.tjrj.bob teste.txt`
*   O receptor receberá a notificação de arquivo de Alice e os pacotes de dados subsequentes. O arquivo final será reconstruído e salvo automaticamente em uma pasta local chamada `downloads/` com o nome original.

### 6. `/exit`
Desconecta do servidor e fecha o programa cliente.

---

## 📈 Recursos de Sistemas Distribuídos Demonstrados

1.  **Ordenação Causal por Lamport Clocks**: Cada mensagem enviada de texto aumenta o relógio lógico local do cliente. Quando outro cliente recebe a mensagem, ele ajusta seu próprio relógio (calculando `max(local, recebido) + 1`), garantindo que a ordenação causal seja mantida em todas as mensagens recebidas. Os relógios lógicos são impressos no terminal do receptor.
2.  **Segurança e Restrições Hierárquicas**: O servidor valida dinamicamente os nomes dos usuários em cada envio para garantir conformidade corporativa governamental (impedindo conversas não autorizadas inter-esferas ou acessos de grupos institucionais privados).
3.  **Fragmentação e Streaming de Arquivos**: O envio de arquivo lê bytes físicos e os encapsula no frame TCP customizado com identificador de transferência (`transferId`) e controle de fim de download.
