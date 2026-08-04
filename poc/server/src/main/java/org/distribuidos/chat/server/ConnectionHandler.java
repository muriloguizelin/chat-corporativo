package org.distribuidos.chat.server;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Set;
import org.distribuidos.chat.shared.FileStartMessage;
import org.distribuidos.chat.shared.MessageType;
import org.distribuidos.chat.shared.ProtocolMessage;
import org.distribuidos.chat.shared.ProtocolParser;
import org.distribuidos.chat.shared.TextMessage;

public class ConnectionHandler implements Runnable {
    private static final String ONLINE_USERS_PREFIX = "ONLINE_USERS:";

    private final Socket socket;
    private final ClientManager clientManager;
    private final XmlMapper xmlMapper = new XmlMapper();
    
    private String username;
    private InputStream in;
    private OutputStream out;
    private volatile boolean running = true;

    public ConnectionHandler(Socket socket, ClientManager clientManager) {
        this.socket = socket;
        this.clientManager = clientManager;
    }

    @Override
    public void run() {
        try {
            in = socket.getInputStream();
            out = socket.getOutputStream();
            System.out.println("[ConnectionHandler] Nova conexão estabelecida de: " + socket.getRemoteSocketAddress());

            while (running) {
                try {
                    ProtocolMessage message = ProtocolParser.readMessage(in);
                    handleMessage(message);
                } catch (IOException e) {
                    // Connection reset/closed
                    System.out.println("[ConnectionHandler] Conexão encerrada para " + (username != null ? username : "desconhecido"));
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[ConnectionHandler] Erro na conexão: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleMessage(ProtocolMessage message) throws IOException {
        System.out.println("[ConnectionHandler] Recebido pacote de: " + (username != null ? username : "anon") + " Tipo: " + message.getType());

        switch (message.getType()) {
            case LOGIN:
                handleLogin(message);
                break;
            case TEXT:
                handleText(message);
                break;
            case BROADCAST:
                handleBroadcast(message);
                break;
            case FILE_START:
                handleFileStart(message);
                break;
            case FILE_CHUNK:
                handleFileChunk(message);
                break;
            case LOGOUT:
                sendAck("Desconectado com sucesso.");
                running = false;
                break;
            case ACK:
            case ERROR:
            default:
                sendError("Operação não suportada pelo servidor.");
                break;
        }
    }

    private void handleLogin(ProtocolMessage message) throws IOException {
        String inputUsername = message.getPayloadAsString().trim();
        if (inputUsername.isEmpty()) {
            sendError("Username inválido.");
            socket.close();
            running = false;
            return;
        }

        // Validate structure (expected: estado.orgao.usuario)
        String[] parts = inputUsername.split("\\.");
        if (parts.length < 3) {
            // Se o usuário não providenciou no formato correto, podemos converter ou alertar.
            // Para flexibilidade, aceitamos mas notificamos.
            sendError("Formato de identificador inválido. Use 'estado.orgao.usuario' (Ex: sp.tjsp.joao).");
            socket.close();
            running = false;
            return;
        }

        this.username = inputUsername;
        boolean registered = clientManager.registerClient(username, this);
        if (registered) {
            sendAck("Login realizado com sucesso como: " + username);
        } else {
            sendError("Nome de usuário já está online.");
            socket.close();
            running = false;
        }
    }

    private void handleText(ProtocolMessage message) throws IOException {
        if (username == null) {
            sendError("Necessário fazer login.");
            return;
        }

        TextMessage textMsg = xmlMapper.readValue(message.getPayload(), TextMessage.class);

        if ("server.users".equalsIgnoreCase(textMsg.getRecipient())) {
            sendAck(ONLINE_USERS_PREFIX + String.join(",", clientManager.getOnlineUsers()));
            return;
        }
        
        // Handle join group special recipient
        if ("server.join".equalsIgnoreCase(textMsg.getRecipient())) {
            String result = clientManager.joinGroup(username, textMsg.getContent());
            if (result.startsWith("Sucesso")) {
                sendAck(result);
            } else {
                sendError(result);
            }
            return;
        }

        // Apply communication restrictions
        if (!clientManager.isCommunicationAllowed(username, textMsg.getRecipient())) {
            sendError("Bloqueado: Restrições administrativas impedem a comunicação entre " + username + " e " + textMsg.getRecipient());
            return;
        }

        ConnectionHandler recipientHandler = clientManager.getClientHandler(textMsg.getRecipient());
        if (recipientHandler != null) {
            // Forward the message
            recipientHandler.sendMessage(message);
            sendAck("Mensagem entregue.");
        } else {
            sendError("Usuário '" + textMsg.getRecipient() + "' não está online.");
        }
    }

    private void handleBroadcast(ProtocolMessage message) throws IOException {
        if (username == null) {
            sendError("Necessário fazer login.");
            return;
        }

        TextMessage textMsg = xmlMapper.readValue(message.getPayload(), TextMessage.class);
        String groupName = textMsg.getRecipient(); // In group chats, recipient is the group name

        if (!clientManager.groupExists(groupName)) {
            sendError("Grupo '" + groupName + "' não existe.");
            return;
        }

        Set<String> members = clientManager.getGroupMembers(groupName);
        if (!members.contains(username)) {
            sendError("Você não é membro do grupo '" + groupName + "'.");
            return;
        }

        // Broadcast to other members
        int count = 0;
        for (String member : members) {
            if (!member.equals(username)) {
                ConnectionHandler handler = clientManager.getClientHandler(member);
                if (handler != null) {
                    handler.sendMessage(message);
                    count++;
                }
            }
        }
        sendAck("Broadcast enviado para " + count + " membros online.");
    }

    private void handleFileStart(ProtocolMessage message) throws IOException {
        if (username == null) {
            sendError("Necessário fazer login.");
            return;
        }

        FileStartMessage fileStart = xmlMapper.readValue(message.getPayload(), FileStartMessage.class);
        
        // Validate communication restriction
        if (!clientManager.isCommunicationAllowed(username, fileStart.getRecipient())) {
            sendError("Bloqueado: Restrições de comunicação impedem o envio do arquivo para " + fileStart.getRecipient());
            return;
        }

        ConnectionHandler recipientHandler = clientManager.getClientHandler(fileStart.getRecipient());
        if (recipientHandler != null) {
            // Register transfer mapping
            FileTransferRegistry.register(fileStart.getTransferId(), fileStart.getRecipient());
            
            // Forward File Start metadata to recipient
            recipientHandler.sendMessage(message);
            sendAck("Transmissão de arquivo autorizada.");
        } else {
            sendError("Destinatário do arquivo não está online.");
        }
    }

    private void handleFileChunk(ProtocolMessage message) throws IOException {
        if (username == null) {
            sendError("Necessário fazer login.");
            return;
        }

        // A file chunk payload starts with 36 bytes of Transfer ID (UUID)
        byte[] payload = message.getPayload();
        if (payload.length < 36) {
            sendError("Chunk de arquivo corrompido.");
            return;
        }

        String transferId = new String(payload, 0, 36);
        
        // Roteamento de chunks na PoC:
        // Como o server é stateless para o stream de arquivos (streaming),
        // precisamos descobrir o destinatário a partir do ClientManager.
        // Para simplificar a PoC, enviamos o chunk para o usuário destino.
        // O cliente envia no payload a identificação ou o servidor repassa para o cliente conectado.
        // Espera-se que o receptor saiba gerenciar os chunks do transferId correspondente.
        // Para fazer o roteamento do chunk, o cliente pode incluir o destino no payload,
        // ou o servidor pode manter o mapeamento de transferId -> recipient.
        // Vamos manter um mapa global de transferId -> recipient em memória no ClientManager?
        // Sim, ou podemos colocar o recipient no payload do chunk.
        // Para máxima robustez, vamos guardar o mapeamento de transferId -> recipient no servidor,
        // ou passar no pacote do chunk. Como o pacote de chunk da PoC contém:
        // [36 bytes UUID] + [restante bytes do chunk],
        // vamos armazenar no ClientManager o mapeamento do transferId.
        // Mas para simplificar, o ClientManager pode expor isso.
        // Vamos guardar o mapeamento dinamicamente ao receber FILE_START.
        // Vamos ver: como faremos?
        // Em ConnectionHandler.handleFileStart, podemos mapear transferId para recipient no ClientManager.
        // Vamos fazer isso!
        String recipient = FileTransferRegistry.getRecipient(transferId);
        if (recipient == null) {
            sendError("Transmissão do arquivo não registrada ou expirada.");
            return;
        }

        ConnectionHandler recipientHandler = clientManager.getClientHandler(recipient);
        if (recipientHandler != null) {
            recipientHandler.sendMessage(message);
        } else {
            sendError("Receptor ficou offline.");
        }
    }

    public synchronized void sendMessage(ProtocolMessage message) throws IOException {
        ProtocolParser.writeMessage(out, message);
    }

    public void sendAck(String info) throws IOException {
        sendMessage(ProtocolMessage.createAck(info));
    }

    public void sendError(String errorMsg) throws IOException {
        sendMessage(ProtocolMessage.createError(errorMsg));
    }

    private void cleanup() {
        running = false;
        clientManager.unregisterClient(username);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }
}
