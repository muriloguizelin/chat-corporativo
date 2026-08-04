package org.distribuidos.chat.client.gui;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.distribuidos.chat.shared.FileStartMessage;
import org.distribuidos.chat.shared.MessageType;
import org.distribuidos.chat.shared.ProtocolMessage;
import org.distribuidos.chat.shared.ProtocolParser;
import org.distribuidos.chat.shared.TextMessage;

public class GuiChatClient {
    private static final String ONLINE_USERS_PREFIX = "ONLINE_USERS:";

    public interface ClientEvents {
        void onConnected(String host, int port);

        void onLoginAccepted(String username, String message);

        void onAck(String message);

        void onError(String message);

        void onTextMessage(TextMessage message, long localLamportClock);

        void onBroadcastMessage(TextMessage message, long localLamportClock);

        void onFileStart(FileStartMessage message);

        void onFileProgress(String transferId, String fileName, long receivedBytes, long totalBytes);

        void onFileComplete(String transferId, String fileName, File file);

        void onOnlineUsers(List<String> users);

        void onDisconnected(String message);
    }

    private final ClientEvents events;
    private final XmlMapper xmlMapper = new XmlMapper();
    private final AtomicLong lamportClock = new AtomicLong(0);
    private final Map<String, FileOutputStream> activeDownloads = new ConcurrentHashMap<>();
    private final Map<String, Long> expectedSizes = new ConcurrentHashMap<>();
    private final Map<String, Long> receivedSizes = new ConcurrentHashMap<>();
    private final Map<String, String> fileNames = new ConcurrentHashMap<>();
    private final Map<String, File> downloadFiles = new ConcurrentHashMap<>();

    private Socket socket;
    private OutputStream out;
    private volatile boolean running;
    private volatile String username;
    private volatile String pendingLoginUsername;

    public GuiChatClient(ClientEvents events) {
        this.events = events;
    }

    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = socket.getOutputStream();
        running = true;

        Thread listenerThread = new Thread(this::listen, "GuiMessageListenerThread");
        listenerThread.setDaemon(true);
        listenerThread.start();

        events.onConnected(host, port);
    }

    public void login(String username) throws IOException {
        ensureConnected();
        pendingLoginUsername = username;
        sendMessage(ProtocolMessage.createLogin(username));
    }

    public void sendText(String recipient, String content) throws IOException {
        ensureLoggedIn();
        long clock = lamportClock.incrementAndGet();
        TextMessage textMessage = new TextMessage(username, recipient, content, clock);
        String xml = xmlMapper.writeValueAsString(textMessage);
        sendMessage(ProtocolMessage.createText(xml));
    }

    public void joinGroup(String group) throws IOException {
        ensureLoggedIn();
        long clock = lamportClock.incrementAndGet();
        TextMessage textMessage = new TextMessage(username, "server.join", group, clock);
        String xml = xmlMapper.writeValueAsString(textMessage);
        sendMessage(ProtocolMessage.createText(xml));
    }

    public void sendBroadcast(String group, String content) throws IOException {
        ensureLoggedIn();
        long clock = lamportClock.incrementAndGet();
        TextMessage textMessage = new TextMessage(username, group, content, clock);
        String xml = xmlMapper.writeValueAsString(textMessage);
        sendMessage(ProtocolMessage.createBroadcast(xml));
    }

    public void requestOnlineUsers() throws IOException {
        ensureLoggedIn();
        TextMessage textMessage = new TextMessage(username, "server.users", "", lamportClock.get());
        String xml = xmlMapper.writeValueAsString(textMessage);
        sendMessage(ProtocolMessage.createText(xml));
    }

    public void sendFile(String recipient, File file) {
        ensureLoggedIn();
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Arquivo invalido.");
        }

        Thread senderThread = new Thread(() -> {
            try {
                String transferId = UUID.randomUUID().toString();
                long fileSize = file.length();
                long clock = lamportClock.incrementAndGet();

                FileStartMessage fileStart = new FileStartMessage(
                        transferId,
                        username,
                        recipient,
                        file.getName(),
                        fileSize,
                        clock
                );

                String xml = xmlMapper.writeValueAsString(fileStart);
                ProtocolMessage startMessage = new ProtocolMessage(
                        MessageType.FILE_START,
                        xml.getBytes(StandardCharsets.UTF_8)
                );
                sendMessage(startMessage);

                Thread.sleep(200);

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        byte[] transferIdBytes = transferId.getBytes(StandardCharsets.UTF_8);
                        byte[] payload = new byte[36 + bytesRead];
                        System.arraycopy(transferIdBytes, 0, payload, 0, 36);
                        System.arraycopy(buffer, 0, payload, 36, bytesRead);

                        sendMessage(new ProtocolMessage(MessageType.FILE_CHUNK, payload));
                        Thread.sleep(5);
                    }
                }

                events.onAck("Arquivo '" + file.getName() + "' enviado para " + recipient + ".");
            } catch (Exception e) {
                events.onError("Falha ao enviar arquivo: " + e.getMessage());
            }
        }, "GuiFileSenderThread");
        senderThread.setDaemon(true);
        senderThread.start();
    }

    public void logout() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                sendMessage(new ProtocolMessage(MessageType.LOGOUT, new byte[0]));
            }
        } catch (IOException e) {
            events.onError("Falha ao encerrar sessao: " + e.getMessage());
        } finally {
            closeQuietly();
        }
    }

    public String getUsername() {
        return username;
    }

    public long getLamportClock() {
        return lamportClock.get();
    }

    private void listen() {
        try {
            InputStream in = socket.getInputStream();
            while (running && !socket.isClosed()) {
                ProtocolMessage message = ProtocolParser.readMessage(in);
                handleIncomingMessage(message);
            }
        } catch (IOException e) {
            if (running) {
                events.onDisconnected("Conexao com o servidor encerrada.");
            }
        } finally {
            running = false;
            cleanupDownloads();
            closeQuietly();
        }
    }

    private void handleIncomingMessage(ProtocolMessage message) {
        try {
            switch (message.getType()) {
                case ACK:
                    handleAck(message.getPayloadAsString());
                    break;
                case ERROR:
                    handleError(message.getPayloadAsString());
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
                default:
                    events.onError("Tipo de mensagem nao tratado: " + message.getType());
                    break;
            }
        } catch (Exception e) {
            events.onError("Falha ao processar mensagem recebida: " + e.getMessage());
        }
    }

    private void handleAck(String ackMessage) {
        if (ackMessage.startsWith(ONLINE_USERS_PREFIX)) {
            events.onOnlineUsers(parseOnlineUsers(ackMessage));
            return;
        }

        if (pendingLoginUsername != null) {
            username = pendingLoginUsername;
            pendingLoginUsername = null;
            events.onLoginAccepted(username, ackMessage);
            return;
        }
        events.onAck(ackMessage);
    }

    private void handleError(String errorMessage) {
        if (pendingLoginUsername != null) {
            pendingLoginUsername = null;
        }
        events.onError(errorMessage);
    }

    private List<String> parseOnlineUsers(String ackMessage) {
        String rawUsers = ackMessage.substring(ONLINE_USERS_PREFIX.length()).trim();
        List<String> users = new ArrayList<>();
        if (rawUsers.isEmpty()) {
            return users;
        }

        String[] parts = rawUsers.split(",");
        for (String part : parts) {
            String user = part.trim();
            if (!user.isEmpty()) {
                users.add(user);
            }
        }
        return users;
    }

    private void handleText(ProtocolMessage message) throws IOException {
        TextMessage textMessage = xmlMapper.readValue(message.getPayload(), TextMessage.class);
        updateClock(textMessage.getLamportClock());
        events.onTextMessage(textMessage, lamportClock.get());
    }

    private void handleBroadcast(ProtocolMessage message) throws IOException {
        TextMessage textMessage = xmlMapper.readValue(message.getPayload(), TextMessage.class);
        updateClock(textMessage.getLamportClock());
        events.onBroadcastMessage(textMessage, lamportClock.get());
    }

    private void handleFileStart(ProtocolMessage message) throws IOException {
        FileStartMessage fileStart = xmlMapper.readValue(message.getPayload(), FileStartMessage.class);
        updateClock(fileStart.getLamportClock());

        File downloadsDir = new File("downloads");
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            throw new IOException("Nao foi possivel criar a pasta downloads.");
        }

        File outputFile = new File(downloadsDir, fileStart.getFileName());
        FileOutputStream fos = new FileOutputStream(outputFile);

        activeDownloads.put(fileStart.getTransferId(), fos);
        expectedSizes.put(fileStart.getTransferId(), fileStart.getFileSize());
        receivedSizes.put(fileStart.getTransferId(), 0L);
        fileNames.put(fileStart.getTransferId(), fileStart.getFileName());
        downloadFiles.put(fileStart.getTransferId(), outputFile);

        events.onFileStart(fileStart);
    }

    private void handleFileChunk(ProtocolMessage message) throws IOException {
        byte[] payload = message.getPayload();
        if (payload.length < 36) {
            events.onError("Chunk de arquivo invalido recebido.");
            return;
        }

        String transferId = new String(payload, 0, 36, StandardCharsets.UTF_8);
        FileOutputStream fos = activeDownloads.get(transferId);
        if (fos == null) {
            return;
        }

        int dataLength = payload.length - 36;
        fos.write(payload, 36, dataLength);

        long received = receivedSizes.get(transferId) + dataLength;
        long expected = expectedSizes.get(transferId);
        String fileName = fileNames.get(transferId);
        receivedSizes.put(transferId, received);
        events.onFileProgress(transferId, fileName, received, expected);

        if (received >= expected) {
            fos.close();
            File completedFile = downloadFiles.get(transferId);
            activeDownloads.remove(transferId);
            expectedSizes.remove(transferId);
            receivedSizes.remove(transferId);
            fileNames.remove(transferId);
            downloadFiles.remove(transferId);
            events.onFileComplete(transferId, fileName, completedFile);
        }
    }

    private void updateClock(long receivedValue) {
        long current;
        long next;
        do {
            current = lamportClock.get();
            next = Math.max(current, receivedValue) + 1;
        } while (!lamportClock.compareAndSet(current, next));
    }

    private synchronized void sendMessage(ProtocolMessage message) throws IOException {
        ensureConnected();
        ProtocolParser.writeMessage(out, message);
    }

    private void ensureConnected() {
        if (socket == null || socket.isClosed() || out == null) {
            throw new IllegalStateException("Cliente nao conectado.");
        }
    }

    private void ensureLoggedIn() {
        ensureConnected();
        if (username == null) {
            throw new IllegalStateException("Faca login antes de enviar mensagens.");
        }
    }

    private void closeQuietly() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore close failures.
        }
    }

    private void cleanupDownloads() {
        for (FileOutputStream fos : activeDownloads.values()) {
            try {
                fos.close();
            } catch (IOException e) {
                // Ignore close failures.
            }
        }
        activeDownloads.clear();
        expectedSizes.clear();
        receivedSizes.clear();
        fileNames.clear();
        downloadFiles.clear();
    }
}
