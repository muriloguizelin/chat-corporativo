package org.distribuidos.chat.client;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;
import org.distribuidos.chat.shared.FileStartMessage;
import org.distribuidos.chat.shared.ProtocolMessage;
import org.distribuidos.chat.shared.ProtocolParser;
import org.distribuidos.chat.shared.TextMessage;

public class CommandProcessor {
    private final ClientMain client;
    private final Socket socket;
    private final XmlMapper xmlMapper = new XmlMapper();
    private OutputStream out;

    public CommandProcessor(ClientMain client, Socket socket) {
        this.client = client;
        this.socket = socket;
    }

    public void run() {
        try {
            out = socket.getOutputStream();
            Scanner scanner = new Scanner(System.in);
            printHelp();

            while (!socket.isClosed()) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) break;
                
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("/")) {
                    handleCommand(line);
                } else {
                    System.out.println("Use comandos iniciados com '/'. Digite '/help' para opções.");
                }
            }
        } catch (Exception e) {
            System.err.println("[Erro] Falha no console de comandos: " + e.getMessage());
        }
    }

    private void handleCommand(String line) throws IOException {
        String[] parts = line.split("\\s+", 3);
        String command = parts[0].toLowerCase();

        switch (command) {
            case "/help":
                printHelp();
                break;
            case "/login":
                if (parts.length < 2) {
                    System.out.println("Uso: /login <estado>.<orgao>.<usuario> (Ex: /login sp.tjsp.alice)");
                } else {
                    String username = parts[1];
                    sendLogin(username);
                }
                break;
            case "/send":
                if (parts.length < 3) {
                    System.out.println("Uso: /send <usuario_destino> <mensagem>");
                } else {
                    String recipient = parts[1];
                    String text = parts[2];
                    sendText(recipient, text);
                }
                break;
            case "/broadcast":
                if (parts.length < 3) {
                    System.out.println("Uso: /broadcast <grupo> <mensagem>");
                } else {
                    String group = parts[1];
                    String text = parts[2];
                    sendBroadcast(group, text);
                }
                break;
            case "/join":
                if (parts.length < 2) {
                    System.out.println("Uso: /join <grupo>");
                } else {
                    String group = parts[1];
                    joinGroup(group);
                }
                break;
            case "/sendfile":
                if (parts.length < 3) {
                    System.out.println("Uso: /sendfile <usuario_destino> <caminho_arquivo>");
                } else {
                    String recipient = parts[1];
                    String filePath = parts[2];
                    sendFile(recipient, filePath);
                }
                break;
            case "/exit":
                sendLogout();
                break;
            default:
                System.out.println("Comando inválido: " + command + ". Digite '/help' para opções.");
                break;
        }
    }

    private void sendLogin(String username) throws IOException {
        ProtocolMessage loginMsg = ProtocolMessage.createLogin(username);
        ProtocolParser.writeMessage(out, loginMsg);
        client.setUsername(username);
    }

    private void sendText(String recipient, String content) throws IOException {
        if (client.getUsername() == null) {
            System.err.println("Por favor, faça login primeiro usando o comando /login.");
            return;
        }
        
        // Logical Lamport clock tick
        long clock = client.incrementClock();
        
        TextMessage textMsg = new TextMessage(client.getUsername(), recipient, content, clock);
        String xml = xmlMapper.writeValueAsString(textMsg);
        
        ProtocolMessage msg = ProtocolMessage.createText(xml);
        ProtocolParser.writeMessage(out, msg);
        System.out.printf("[Lamport: %d] Enviando para %s...\n", clock, recipient);
    }

    private void sendBroadcast(String group, String content) throws IOException {
        if (client.getUsername() == null) {
            System.err.println("Por favor, faça login primeiro usando o comando /login.");
            return;
        }
        
        long clock = client.incrementClock();
        
        TextMessage textMsg = new TextMessage(client.getUsername(), group, content, clock);
        String xml = xmlMapper.writeValueAsString(textMsg);
        
        ProtocolMessage msg = ProtocolMessage.createBroadcast(xml);
        ProtocolParser.writeMessage(out, msg);
        System.out.printf("[Lamport: %d] Enviando broadcast para grupo %s...\n", clock, group);
    }

    private void joinGroup(String group) throws IOException {
        if (client.getUsername() == null) {
            System.err.println("Por favor, faça login primeiro usando o comando /login.");
            return;
        }
        
        long clock = client.incrementClock();
        // We reuse the TEXT message type sending to special target 'server.join' to handle group enrollment
        TextMessage textMsg = new TextMessage(client.getUsername(), "server.join", group, clock);
        String xml = xmlMapper.writeValueAsString(textMsg);
        
        ProtocolMessage msg = ProtocolMessage.createText(xml);
        ProtocolParser.writeMessage(out, msg);
    }

    private void sendFile(String recipient, String filePath) {
        if (client.getUsername() == null) {
            System.err.println("Por favor, faça login primeiro usando o comando /login.");
            return;
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            System.err.println("Arquivo não encontrado: " + filePath);
            return;
        }

        new Thread(() -> {
            try {
                String transferId = UUID.randomUUID().toString(); // 36 characters
                long fileSize = file.length();
                String fileName = file.getName();
                long clock = client.incrementClock();

                System.out.printf("[Arquivo] Iniciando transmissão de '%s' (%d bytes) para '%s'...\n", fileName, fileSize, recipient);

                // 1. Send FILE_START packet
                FileStartMessage fileStart = new FileStartMessage(transferId, client.getUsername(), recipient, fileName, fileSize, clock);
                String xml = xmlMapper.writeValueAsString(fileStart);
                ProtocolMessage startMsg = new ProtocolMessage(org.distribuidos.chat.shared.MessageType.FILE_START, xml.getBytes(StandardCharsets.UTF_8));
                
                synchronized (out) {
                    ProtocolParser.writeMessage(out, startMsg);
                }

                // Brief sleep before transferring chunks
                Thread.sleep(200);

                // 2. Stream FILE_CHUNK packets
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    int chunkIndex = 0;
                    
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        // Chunk payload format: [36 bytes UUID] + [bytesRead data]
                        byte[] chunkPayload = new byte[36 + bytesRead];
                        
                        // Copy UUID bytes
                        byte[] uuidBytes = transferId.getBytes(StandardCharsets.UTF_8);
                        System.arraycopy(uuidBytes, 0, chunkPayload, 0, 36);
                        
                        // Copy data bytes
                        System.arraycopy(buffer, 0, chunkPayload, 36, bytesRead);

                        ProtocolMessage chunkMsg = new ProtocolMessage(org.distribuidos.chat.shared.MessageType.FILE_CHUNK, chunkPayload);
                        
                        synchronized (out) {
                            ProtocolParser.writeMessage(out, chunkMsg);
                        }

                        chunkIndex++;
                        
                        // Yield to prevent socket buffer congestion
                        Thread.sleep(5);
                    }
                }

                System.out.printf("[Arquivo] Arquivo '%s' transmitido por completo para o servidor!\n", fileName);

            } catch (Exception e) {
                System.err.println("[Arquivo] Erro ao transmitir arquivo: " + e.getMessage());
            }
        }, "FileSenderThread").start();
    }

    private void sendLogout() throws IOException {
        ProtocolMessage logoutMsg = new ProtocolMessage(org.distribuidos.chat.shared.MessageType.LOGOUT, new byte[0]);
        ProtocolParser.writeMessage(out, logoutMsg);
        socket.close();
        System.exit(0);
    }

    private void printHelp() {
        System.out.println("\nComandos disponíveis:");
        System.out.println("  /login <estado>.<orgao>.<usuario>  - Realiza identificação no chat. Ex: /login sp.tjsp.alice");
        System.out.println("  /join <grupo>                     - Ingressa em um canal de comunicação institucional");
        System.out.println("  /send <usuario> <mensagem>        - Envia mensagem privada 1:1");
        System.out.println("  /broadcast <grupo> <mensagem>     - Envia mensagem para todos os membros do grupo");
        System.out.println("  /sendfile <usuario> <caminho>     - Envia arquivo para o usuário através do socket");
        System.out.println("  /help                             - Exibe este painel de ajuda");
        System.out.println("  /exit                             - Desconecta e finaliza o chat\n");
    }
}
