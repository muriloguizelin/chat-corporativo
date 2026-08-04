package br.ufmt.sd.chat.client;

import br.ufmt.sd.chat.common.model.MessagePacket;
import br.ufmt.sd.chat.common.model.MessageType;
import br.ufmt.sd.chat.common.model.VectorClock;
import br.ufmt.sd.chat.common.protocol.ProtocolCodec;
import br.ufmt.sd.chat.common.security.CryptoUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Cliente CLI (Terminal Interativo) do Chat Corporativo Seguro da Federação.
 * 
 * Permite enviar mensagens diretas, transferir arquivos, criar grupos, listar usuários online
 * e consultar o histórico de conversas com garantias de segurança e ordenação causal.
 */
public class ClientMain {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 9090;

    private static Socket socket;
    private static DataInputStream in;
    private static DataOutputStream out;
    private static String userId;
    private static final VectorClock localClock = new VectorClock();
    private static NetworkListener listener;

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        if (args.length >= 1) host = args[0];
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }

        Scanner scanner = new Scanner(System.in);

        printBanner();

        System.out.print("Digite seu ID Federativo (Formato: <poder>-<uf>-<nome>, ex: exec-mt-joao, jud-df-maria): ");
        userId = scanner.nextLine().trim();
        while (userId.isEmpty()) {
            System.out.print("ID não pode ser vazio. Digite seu ID: ");
            userId = scanner.nextLine().trim();
        }

        try {
            System.out.println("[CLIENTE] Conectando ao Servidor " + host + ":" + port + "...");
            socket = new Socket(host, port);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            // 1. Enviar pacote de LOGIN
            localClock.increment(userId);
            MessagePacket loginPacket = MessagePacket.createTextPacket(MessageType.LOGIN, userId, "SERVER", "LOGIN_REQUEST");
            loginPacket.setVectorClock(localClock);
            ProtocolCodec.sendPacket(out, loginPacket);

            // 2. Iniciar Thread de escuta de mensagens do servidor em segundo plano
            listener = new NetworkListener(socket, in, localClock, userId);
            Thread listenerThread = new Thread(listener, "NetworkListenerThread");
            listenerThread.start();

            Thread.sleep(300); // Pequena pausa para receber resposta inicial de login
            printHelp();

            // 3. Loop principal de leitura do terminal
            while (!socket.isClosed()) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) continue;

                if (input.startsWith("/")) {
                    handleCommand(input);
                } else {
                    System.out.println("Use os comandos que começam com '/'. Digite /help para ver as opções.");
                }
            }

        } catch (Exception e) {
            System.err.println("[CLIENTE] Erro de Conexão ou Protocolo: " + e.getMessage());
        } finally {
            close();
        }
    }

    private static void handleCommand(String input) {
        String[] tokens = input.split(" ", 3);
        String cmd = tokens[0].toLowerCase();

        try {
            switch (cmd) {
                case "/help":
                    printHelp();
                    break;
                case "/users":
                    sendSimpleCommand(MessageType.SEARCH_USERS, "SERVER");
                    break;
                case "/groups":
                    sendSimpleCommand(MessageType.LIST_GROUPS, "SERVER");
                    break;
                case "/msg":
                    if (tokens.length < 3) {
                        System.out.println("Uso correto: /msg <targetId> <mensagem>");
                        return;
                    }
                    sendTextMessage(tokens[1], tokens[2]);
                    break;
                case "/file":
                    if (tokens.length < 3) {
                        System.out.println("Uso correto: /file <targetId> <caminho_do_arquivo>");
                        return;
                    }
                    sendFileMessage(tokens[1], tokens[2]);
                    break;
                case "/creategroup":
                    String[] gArgs = input.split(" ");
                    if (gArgs.length < 3) {
                        System.out.println("Uso correto: /creategroup <groupId> <nomeGrupo> [adminOnly:true/false] [poder:all/exec/jud/leg]");
                        return;
                    }
                    String gId = gArgs[1];
                    String gName = gArgs[2];
                    boolean adminOnly = gArgs.length >= 4 && Boolean.parseBoolean(gArgs[3]);
                    String poder = gArgs.length >= 5 ? gArgs[4] : "all";
                    
                    String payload = gId + ";" + gName + ";" + adminOnly + ";" + poder;
                    sendPayloadCommand(MessageType.CREATE_GROUP, "SERVER", payload);
                    break;
                case "/groupmsg":
                    if (tokens.length < 3) {
                        System.out.println("Uso correto: /groupmsg <groupId> <mensagem>");
                        return;
                    }
                    sendGroupMessage(tokens[1], tokens[2]);
                    break;
                case "/history":
                    if (tokens.length < 2) {
                        System.out.println("Uso correto: /history <targetId_ou_groupId>");
                        return;
                    }
                    sendSimpleCommand(MessageType.HISTORY_REQ, tokens[1]);
                    break;
                case "/exit":
                    sendSimpleCommand(MessageType.LOGOUT, "SERVER");
                    close();
                    System.exit(0);
                    break;
                default:
                    System.out.println("Comando desconhecido. Digite /help.");
                    break;
            }
        } catch (Exception e) {
            System.out.println("[ERRO] Falha ao enviar comando: " + e.getMessage());
        }
    }

    private static void sendTextMessage(String targetId, String text) throws IOException {
        synchronized (localClock) {
            localClock.increment(userId);
        }

        // Assinatura HMAC para garantir Autenticidade e Não-Repúdio
        String signature = CryptoUtil.generateSignature(userId, text, CryptoUtil.DEFAULT_FEDERATION_KEY);

        MessagePacket packet = MessagePacket.createTextPacket(MessageType.TEXT_DIRECT, userId, targetId, text);
        packet.setVectorClock(localClock);
        packet.setSignature(signature);

        ProtocolCodec.sendPacket(out, packet);
        System.out.println("[ENVIADO ➔ " + targetId + "] (VC: " + localClock.serialize() + "): " + text);
    }

    private static void sendFileMessage(String targetId, String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            System.out.println("[ERRO] Arquivo não encontrado: " + filePath);
            return;
        }

        byte[] fileBytes = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(fileBytes);
        }

        synchronized (localClock) {
            localClock.increment(userId);
        }

        String signature = CryptoUtil.generateSignature(userId, file.getName(), CryptoUtil.DEFAULT_FEDERATION_KEY);

        MessagePacket packet = new MessagePacket(MessageType.FILE_TRANSFER, userId, targetId, fileBytes);
        packet.setFileName(file.getName());
        packet.setVectorClock(localClock);
        packet.setSignature(signature);

        ProtocolCodec.sendPacket(out, packet);
        System.out.println("[ARQUIVO ENVIADO 📁 ➔ " + targetId + "]: " + file.getName() + " (" + fileBytes.length + " bytes)");
    }

    private static void sendGroupMessage(String groupId, String text) throws IOException {
        synchronized (localClock) {
            localClock.increment(userId);
        }

        String signature = CryptoUtil.generateSignature(userId, text, CryptoUtil.DEFAULT_FEDERATION_KEY);

        MessagePacket packet = MessagePacket.createTextPacket(MessageType.GROUP_MSG, userId, groupId, text);
        packet.setVectorClock(localClock);
        packet.setSignature(signature);

        ProtocolCodec.sendPacket(out, packet);
        System.out.println("[GRUPO ENVIADO ➔ " + groupId + "]: " + text);
    }

    private static void sendSimpleCommand(MessageType type, String targetId) throws IOException {
        synchronized (localClock) {
            localClock.increment(userId);
        }
        MessagePacket packet = MessagePacket.createTextPacket(type, userId, targetId, "");
        packet.setVectorClock(localClock);
        ProtocolCodec.sendPacket(out, packet);
    }

    private static void sendPayloadCommand(MessageType type, String targetId, String payload) throws IOException {
        synchronized (localClock) {
            localClock.increment(userId);
        }
        MessagePacket packet = MessagePacket.createTextPacket(type, userId, targetId, payload);
        packet.setVectorClock(localClock);
        ProtocolCodec.sendPacket(out, packet);
    }

    private static void printBanner() {
        System.out.println("=================================================================");
        System.out.println("  SISTEMA DE CHAT CORPORATIVO SEGURO DA FEDERAÇÃO (UFMT - SD)   ");
        System.out.println("=================================================================");
    }

    private static void printHelp() {
        System.out.println("\n----------------- COMANDOS DISPONÍVEIS -----------------");
        System.out.println(" /msg <targetId> <texto>          - Envia mensagem direta");
        System.out.println(" /file <targetId> <caminho>       - Transfere arquivo");
        System.out.println(" /users                          - Lista usuários online");
        System.out.println(" /creategroup <id> <nome> [adminOnly:true/false] [poder]");
        System.out.println("                                 - Cria um novo grupo");
        System.out.println(" /groupmsg <groupId> <texto>     - Envia mensagem para grupo");
        System.out.println(" /groups                         - Lista grupos cadastrados");
        System.out.println(" /history <targetId>             - Exibe histórico de mensagens");
        System.out.println(" /help                           - Exibe este menu de ajuda");
        System.out.println(" /exit                           - Desconecta do sistema");
        System.out.println("--------------------------------------------------------\n");
    }

    private static void close() {
        try {
            if (listener != null) listener.stop();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
