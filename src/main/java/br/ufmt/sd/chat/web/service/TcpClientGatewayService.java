package br.ufmt.sd.chat.web.service;

import br.ufmt.sd.chat.common.model.MessagePacket;
import br.ufmt.sd.chat.common.model.MessageType;
import br.ufmt.sd.chat.common.model.VectorClock;
import br.ufmt.sd.chat.common.protocol.ProtocolCodec;
import br.ufmt.sd.chat.common.security.CryptoUtil;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Serviço Gateway TCP que interliga a Interface Web Thymeleaf ao Servidor Broker TCP.
 */
@Service
public class TcpClientGatewayService {

    @Value("${chat.server.host:localhost}")
    private String serverHost;

    @Value("${chat.server.port:9090}")
    private int serverPort;

    // Sessões ativas da web: userId -> WebSessionState
    private final Map<String, WebSessionState> activeSessions = new ConcurrentHashMap<>();

    public static class WebMessageDTO {
        private String senderId;
        private String targetId;
        private String content;
        private String vectorClock;
        private boolean authentic;
        private boolean isFile;
        private String fileName;
        private String type;

        public WebMessageDTO(String senderId, String targetId, String content, String vectorClock, boolean authentic, boolean isFile, String fileName, String type) {
            this.senderId = senderId;
            this.targetId = targetId;
            this.content = content;
            this.vectorClock = vectorClock;
            this.authentic = authentic;
            this.isFile = isFile;
            this.fileName = fileName;
            this.type = type;
        }

        public String getSenderId() { return senderId; }
        public String getTargetId() { return targetId; }
        public String getContent() { return content; }
        public String getVectorClock() { return vectorClock; }
        public boolean isAuthentic() { return authentic; }
        public boolean isFile() { return isFile; }
        public String getFileName() { return fileName; }
        public String getType() { return type; }
    }

    public static class WebSessionState {
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private final String userId;
        private final VectorClock localClock = new VectorClock();
        private final List<WebMessageDTO> messageFeed = new CopyOnWriteArrayList<>();
        private List<String> onlineUsersCache = new CopyOnWriteArrayList<>();
        private List<String> groupsCache = new CopyOnWriteArrayList<>();
        private volatile boolean active = true;

        public WebSessionState(Socket socket, DataInputStream in, DataOutputStream out, String userId) {
            this.socket = socket;
            this.in = in;
            this.out = out;
            this.userId = userId;
        }

        public String getUserId() { return userId; }
        public List<WebMessageDTO> getMessageFeed() { return messageFeed; }
        public List<String> getOnlineUsersCache() { return onlineUsersCache; }
        public List<String> getGroupsCache() { return groupsCache; }
    }

    /**
     * Conecta e autentica uma sessão web no Servidor Broker TCP.
     */
    public boolean loginWebUser(String userId) {
        if (activeSessions.containsKey(userId)) {
            return true; // Já autenticado
        }

        try {
            Socket socket = new Socket(serverHost, serverPort);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            WebSessionState session = new WebSessionState(socket, in, out, userId);

            // Enviar pacote de LOGIN no protocolo OSGURI
            session.localClock.increment(userId);
            MessagePacket loginPacket = MessagePacket.createTextPacket(MessageType.LOGIN, userId, "SERVER", "LOGIN_WEB");
            loginPacket.setVectorClock(session.localClock);
            ProtocolCodec.sendPacket(out, loginPacket);

            // Registrar sessão no mapa
            activeSessions.put(userId, session);

            // Iniciar Thread receptora de pacotes TCP em segundo plano para esta sessão web
            Thread listenerThread = new Thread(() -> listenTcpLoop(session), "WebTcpListener-" + userId);
            listenerThread.setDaemon(true);
            listenerThread.start();

            // Solicitar atualização inicial de usuários e grupos
            refreshUsersAndGroups(userId);

            return true;
        } catch (IOException e) {
            System.err.println("[WEB-GATEWAY] Falha ao conectar no Servidor Broker TCP (" + serverHost + ":" + serverPort + "): " + e.getMessage());
            return false;
        }
    }

    /**
     * Loop em segundo plano que recebe quadros TCP do Broker e popula a fila Web.
     */
    private void listenTcpLoop(WebSessionState session) {
        try {
            while (session.active && !session.socket.isClosed()) {
                MessagePacket packet = ProtocolCodec.readPacket(session.in);
                
                synchronized (session.localClock) {
                    session.localClock.merge(packet.getVectorClock());
                }

                switch (packet.getType()) {
                    case TEXT_DIRECT: {
                        boolean validSig = CryptoUtil.verifySignature(packet.getSenderId(), packet.getPayloadAsString(), packet.getSignature(), CryptoUtil.DEFAULT_FEDERATION_KEY);
                        WebMessageDTO dto = new WebMessageDTO(packet.getSenderId(), packet.getTargetId(), packet.getPayloadAsString(), packet.getVectorClock().serialize(), validSig, false, "", "DIRECT");
                        session.messageFeed.add(dto);
                        break;
                    }
                    case GROUP_MSG: {
                        boolean validSig = CryptoUtil.verifySignature(packet.getSenderId(), packet.getPayloadAsString(), packet.getSignature(), CryptoUtil.DEFAULT_FEDERATION_KEY);
                        WebMessageDTO dto = new WebMessageDTO(packet.getSenderId(), packet.getTargetId(), packet.getPayloadAsString(), packet.getVectorClock().serialize(), validSig, false, "", "GROUP");
                        session.messageFeed.add(dto);
                        break;
                    }
                    case FILE_TRANSFER: {
                        saveFileLocally(packet);
                        WebMessageDTO dto = new WebMessageDTO(packet.getSenderId(), packet.getTargetId(), "Arquivo recebido: " + packet.getFileName(), packet.getVectorClock().serialize(), true, true, packet.getFileName(), "FILE");
                        session.messageFeed.add(dto);
                        break;
                    }
                    case SEARCH_RESP: {
                        String[] users = packet.getPayloadAsString().split(", ");
                        List<String> list = new ArrayList<>();
                        for (String u : users) if (!u.isEmpty()) list.add(u);
                        session.onlineUsersCache = new CopyOnWriteArrayList<>(list);
                        break;
                    }
                    case LIST_GROUPS_RESP: {
                        String[] grps = packet.getPayloadAsString().split("\n");
                        List<String> list = new ArrayList<>();
                        for (String g : grps) if (!g.isEmpty()) list.add(g);
                        session.groupsCache = new CopyOnWriteArrayList<>(list);
                        break;
                    }
                    case ERROR: {
                        WebMessageDTO dto = new WebMessageDTO("SERVIDORES_FEDERACAO", session.userId, packet.getPayloadAsString(), session.localClock.serialize(), false, false, "", "ERROR");
                        session.messageFeed.add(dto);
                        break;
                    }
                    default:
                        break;
                }
            }
        } catch (IOException e) {
            session.active = false;
            activeSessions.remove(session.userId);
        }
    }

    private void saveFileLocally(MessagePacket packet) {
        try {
            File downloadsDir = new File("downloads");
            if (!downloadsDir.exists()) downloadsDir.mkdirs();
            File outFile = new File(downloadsDir, packet.getFileName());
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(packet.getPayload());
            }
        } catch (IOException ignored) {}
    }

    public void sendDirectMessage(String userId, String targetId, String text) throws IOException {
        WebSessionState session = activeSessions.get(userId);
        if (session == null) return;

        synchronized (session.localClock) {
            session.localClock.increment(userId);
        }
        String sig = CryptoUtil.generateSignature(userId, text, CryptoUtil.DEFAULT_FEDERATION_KEY);

        MessagePacket packet = MessagePacket.createTextPacket(MessageType.TEXT_DIRECT, userId, targetId, text);
        packet.setVectorClock(session.localClock);
        packet.setSignature(sig);

        ProtocolCodec.sendPacket(session.out, packet);
        
        // Adicionar localmente ao feed da web
        WebMessageDTO dto = new WebMessageDTO(userId, targetId, text, session.localClock.serialize(), true, false, "", "DIRECT");
        session.messageFeed.add(dto);
    }

    public void sendGroupMessage(String userId, String groupId, String text) throws IOException {
        WebSessionState session = activeSessions.get(userId);
        if (session == null) return;

        synchronized (session.localClock) {
            session.localClock.increment(userId);
        }
        String sig = CryptoUtil.generateSignature(userId, text, CryptoUtil.DEFAULT_FEDERATION_KEY);

        MessagePacket packet = MessagePacket.createTextPacket(MessageType.GROUP_MSG, userId, groupId, text);
        packet.setVectorClock(session.localClock);
        packet.setSignature(sig);

        ProtocolCodec.sendPacket(session.out, packet);

        WebMessageDTO dto = new WebMessageDTO(userId, groupId, text, session.localClock.serialize(), true, false, "", "GROUP");
        session.messageFeed.add(dto);
    }

    public void sendFile(String userId, String targetId, String fileName, byte[] fileBytes) throws IOException {
        WebSessionState session = activeSessions.get(userId);
        if (session == null) return;

        synchronized (session.localClock) {
            session.localClock.increment(userId);
        }
        String sig = CryptoUtil.generateSignature(userId, fileName, CryptoUtil.DEFAULT_FEDERATION_KEY);

        MessagePacket packet = new MessagePacket(MessageType.FILE_TRANSFER, userId, targetId, fileBytes);
        packet.setFileName(fileName);
        packet.setVectorClock(session.localClock);
        packet.setSignature(sig);

        ProtocolCodec.sendPacket(session.out, packet);

        WebMessageDTO dto = new WebMessageDTO(userId, targetId, "Arquivo enviado: " + fileName, session.localClock.serialize(), true, true, fileName, "FILE");
        session.messageFeed.add(dto);
    }

    public void createGroup(String userId, String groupId, String groupName, boolean adminOnly, String restrictedPoder) throws IOException {
        WebSessionState session = activeSessions.get(userId);
        if (session == null) return;

        String payload = groupId + ";" + groupName + ";" + adminOnly + ";" + restrictedPoder;
        MessagePacket packet = MessagePacket.createTextPacket(MessageType.CREATE_GROUP, userId, "SERVER", payload);
        ProtocolCodec.sendPacket(session.out, packet);
    }

    public void refreshUsersAndGroups(String userId) {
        WebSessionState session = activeSessions.get(userId);
        if (session == null) return;

        try {
            MessagePacket searchPacket = MessagePacket.createTextPacket(MessageType.SEARCH_USERS, userId, "SERVER", "");
            ProtocolCodec.sendPacket(session.out, searchPacket);

            MessagePacket groupsPacket = MessagePacket.createTextPacket(MessageType.LIST_GROUPS, userId, "SERVER", "");
            ProtocolCodec.sendPacket(session.out, groupsPacket);
        } catch (IOException ignored) {}
    }

    public WebSessionState getSession(String userId) {
        return activeSessions.get(userId);
    }
}
