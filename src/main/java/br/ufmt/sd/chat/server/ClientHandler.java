package br.ufmt.sd.chat.server;

import br.ufmt.sd.chat.common.model.MessagePacket;
import br.ufmt.sd.chat.common.model.MessageType;
import br.ufmt.sd.chat.common.protocol.ProtocolCodec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;

/**
 * Handler Concorrente de Cliente (Thread Worker TCP).
 * Cada conexão ativa de cliente é tratada por uma instância desta classe em uma thread dedicada.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final MessageBroker broker;
    private DataInputStream in;
    private DataOutputStream out;
    private String clientUserId;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, MessageBroker broker) {
        this.socket = socket;
        this.broker = broker;
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            while (running && !socket.isClosed()) {
                MessagePacket packet = ProtocolCodec.readPacket(in);
                processPacket(packet);
            }
        } catch (IOException e) {
            System.out.println("[SERVER] Conexão encerrada para " + (clientUserId != null ? clientUserId : socket.getRemoteSocketAddress()));
        } finally {
            close();
        }
    }

    private void processPacket(MessagePacket packet) {
        try {
            switch (packet.getType()) {
                case LOGIN:
                    handleLogin(packet);
                    break;
                case TEXT_DIRECT:
                    broker.routeDirectMessage(packet, this);
                    break;
                case FILE_TRANSFER:
                    broker.routeFileTransfer(packet, this);
                    break;
                case SEARCH_USERS:
                    handleSearchUsers(packet);
                    break;
                case CREATE_GROUP:
                    handleCreateGroup(packet);
                    break;
                case GROUP_MSG:
                    broker.routeGroupMessage(packet, this);
                    break;
                case LIST_GROUPS:
                    handleListGroups(packet);
                    break;
                case HISTORY_REQ:
                    handleHistoryRequest(packet);
                    break;
                case LOGOUT:
                    handleLogout();
                    break;
                default:
                    sendError("Tipo de mensagem não suportado: " + packet.getType());
                    break;
            }
        } catch (Exception e) {
            sendError("Erro interno no processamento: " + e.getMessage());
        }
    }

    private void handleLogin(MessagePacket packet) throws IOException {
        String userId = packet.getSenderId();
        if (userId == null || userId.trim().isEmpty()) {
            sendError("ID de usuário inválido.");
            return;
        }

        boolean success = broker.registerClient(userId, this);
        if (success) {
            this.clientUserId = userId;
            MessagePacket resp = MessagePacket.createTextPacket(MessageType.LOGIN_RESP, "SERVER", userId, "SUCCESS: Bem-vindo à rede da Federação, " + userId);
            sendPacket(resp);
        } else {
            MessagePacket resp = MessagePacket.createTextPacket(MessageType.LOGIN_RESP, "SERVER", userId, "ERROR: Usuário já conectado.");
            sendPacket(resp);
            running = false;
        }
    }

    private void handleSearchUsers(MessagePacket packet) throws IOException {
        List<String> users = broker.getOnlineUsers();
        String userListStr = String.join(", ", users);
        MessagePacket resp = MessagePacket.createTextPacket(MessageType.SEARCH_RESP, "SERVER", clientUserId, userListStr);
        sendPacket(resp);
    }

    private void handleCreateGroup(MessagePacket packet) {
        // Payload format: "groupId;groupName;adminOnly;restrictedPoder"
        String payload = packet.getPayloadAsString();
        String[] parts = payload.split(";");
        if (parts.length >= 2) {
            String groupId = parts[0];
            String groupName = parts[1];
            boolean adminOnly = parts.length >= 3 && Boolean.parseBoolean(parts[2]);
            String restrictedPoder = parts.length >= 4 ? parts[3] : "all";

            broker.createGroup(groupId, groupName, clientUserId, adminOnly, restrictedPoder, this);
        } else {
            sendError("Formato de criação de grupo inválido. Use: groupId;groupName;adminOnly;restrictedPoder");
        }
    }

    private void handleListGroups(MessagePacket packet) throws IOException {
        List<String> groups = broker.getGroupsList();
        String groupListStr = String.join("\n", groups);
        MessagePacket resp = MessagePacket.createTextPacket(MessageType.LIST_GROUPS_RESP, "SERVER", clientUserId, groupListStr);
        sendPacket(resp);
    }

    private void handleHistoryRequest(MessagePacket packet) throws IOException {
        String targetId = packet.getTargetId();
        List<MessagePacket> history = broker.getHistory(clientUserId, targetId);
        
        StringBuilder sb = new StringBuilder("=== Histórico de Conversa (" + targetId + ") ===\n");
        for (MessagePacket msg : history) {
            sb.append(String.format("[%s -> %s] (VC: %s): %s%n",
                    msg.getSenderId(), msg.getTargetId(), msg.getVectorClock().serialize(),
                    msg.getFileName().isEmpty() ? msg.getPayloadAsString() : "[Arquivo: " + msg.getFileName() + "]"));
        }
        
        MessagePacket resp = MessagePacket.createTextPacket(MessageType.HISTORY_RESP, "SERVER", clientUserId, sb.toString());
        sendPacket(resp);
    }

    private void handleLogout() {
        running = false;
        if (clientUserId != null) {
            broker.unregisterClient(clientUserId);
        }
    }

    public synchronized void sendPacket(MessagePacket packet) throws IOException {
        ProtocolCodec.sendPacket(out, packet);
    }

    public void sendError(String errorMessage) {
        try {
            MessagePacket errPacket = MessagePacket.createTextPacket(MessageType.ERROR, "SERVER", clientUserId != null ? clientUserId : "GUEST", errorMessage);
            sendPacket(errPacket);
        } catch (IOException ignored) {}
    }

    private void close() {
        running = false;
        if (clientUserId != null) {
            broker.unregisterClient(clientUserId);
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
