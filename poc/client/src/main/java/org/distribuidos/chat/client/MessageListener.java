package org.distribuidos.chat.client;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.distribuidos.chat.shared.FileStartMessage;
import org.distribuidos.chat.shared.ProtocolMessage;
import org.distribuidos.chat.shared.ProtocolParser;
import org.distribuidos.chat.shared.TextMessage;

public class MessageListener implements Runnable {
    private final ClientMain client;
    private final Socket socket;
    private final XmlMapper xmlMapper = new XmlMapper();

    // Map to keep track of active downloads: transferId -> Open File Output Stream
    private final Map<String, FileOutputStream> activeDownloads = new ConcurrentHashMap<>();
    private final Map<String, Long> expectedSizes = new ConcurrentHashMap<>();
    private final Map<String, Long> receivedSizes = new ConcurrentHashMap<>();
    private final Map<String, String> fileNames = new ConcurrentHashMap<>();

    public MessageListener(ClientMain client, Socket socket) {
        this.client = client;
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            InputStream in = socket.getInputStream();
            while (!socket.isClosed()) {
                ProtocolMessage message = ProtocolParser.readMessage(in);
                handleIncomingMessage(message);
            }
        } catch (IOException e) {
            System.out.println("\n[Conexão] Conexão com o servidor fechada.");
        } finally {
            cleanupDownloads();
        }
    }

    private void handleIncomingMessage(ProtocolMessage message) {
        try {
            switch (message.getType()) {
                case ACK:
                    System.out.println("\n[Servidor] ACK: " + message.getPayloadAsString());
                    break;
                case ERROR:
                    System.err.println("\n[Servidor] ERRO: " + message.getPayloadAsString());
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
                    System.out.println("\n[Mensagem] Tipo desconhecido recebido do servidor.");
                    break;
            }
            // Reprint prompt indicator for CLI clarity
            System.out.print("> ");
            System.out.flush();
        } catch (Exception e) {
            System.err.println("\n[Erro] Falha ao processar mensagem do servidor: " + e.getMessage());
        }
    }

    private void handleText(ProtocolMessage message) throws IOException {
        TextMessage textMsg = xmlMapper.readValue(message.getPayload(), TextMessage.class);
        client.updateClock(textMsg.getLamportClock());
        System.out.printf("\n[Lamport: %d] %s: %s\n", 
                client.getClock(), textMsg.getSender(), textMsg.getContent());
    }

    private void handleBroadcast(ProtocolMessage message) throws IOException {
        TextMessage textMsg = xmlMapper.readValue(message.getPayload(), TextMessage.class);
        client.updateClock(textMsg.getLamportClock());
        System.out.printf("\n[Lamport: %d] [%s] %s: %s\n", 
                client.getClock(), textMsg.getRecipient(), textMsg.getSender(), textMsg.getContent());
    }

    private void handleFileStart(ProtocolMessage message) throws IOException {
        FileStartMessage fileStart = xmlMapper.readValue(message.getPayload(), FileStartMessage.class);
        client.updateClock(fileStart.getLamportClock());

        System.out.printf("\n[Arquivo] Recebendo arquivo '%s' (%d bytes) de '%s'...\n", 
                fileStart.getFileName(), fileStart.getFileSize(), fileStart.getSender());

        // Create downloads folder if not exists
        File downloadsDir = new File("downloads");
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs();
        }

        File newFile = new File(downloadsDir, fileStart.getFileName());
        FileOutputStream fos = new FileOutputStream(newFile);
        
        activeDownloads.put(fileStart.getTransferId(), fos);
        expectedSizes.put(fileStart.getTransferId(), fileStart.getFileSize());
        receivedSizes.put(fileStart.getTransferId(), 0L);
        fileNames.put(fileStart.getTransferId(), fileStart.getFileName());
    }

    private void handleFileChunk(ProtocolMessage message) throws IOException {
        byte[] payload = message.getPayload();
        if (payload.length < 36) {
            System.err.println("\n[Arquivo] Chunk inválido recebido.");
            return;
        }

        String transferId = new String(payload, 0, 36, StandardCharsets.UTF_8);
        FileOutputStream fos = activeDownloads.get(transferId);
        
        if (fos != null) {
            int dataLength = payload.length - 36;
            fos.write(payload, 36, dataLength);

            long currentReceived = receivedSizes.get(transferId) + dataLength;
            receivedSizes.put(transferId, currentReceived);
            
            long expectedSize = expectedSizes.get(transferId);
            
            // Print progress
            System.out.printf("\r[Arquivo] Baixando: %.2f%%", (double) currentReceived / expectedSize * 100);

            if (currentReceived >= expectedSize) {
                fos.close();
                System.out.printf("\n[Arquivo] Download finalizado! Arquivo salvo em: downloads/%s\n", fileNames.get(transferId));
                activeDownloads.remove(transferId);
                expectedSizes.remove(transferId);
                receivedSizes.remove(transferId);
                fileNames.remove(transferId);
            }
        }
    }

    private void cleanupDownloads() {
        for (FileOutputStream fos : activeDownloads.values()) {
            try {
                fos.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        activeDownloads.clear();
    }
}
