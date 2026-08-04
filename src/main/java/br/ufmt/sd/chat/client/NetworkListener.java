package br.ufmt.sd.chat.client;

import br.ufmt.sd.chat.common.model.MessagePacket;
import br.ufmt.sd.chat.common.model.VectorClock;
import br.ufmt.sd.chat.common.protocol.ProtocolCodec;
import br.ufmt.sd.chat.common.security.CryptoUtil;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Listener de Rede do Cliente.
 * Executa em uma Thread dedicada em segundo plano, escutando mensagens do Servidor
 * sem bloquear a navegação e digitação do usuário no terminal.
 */
public class NetworkListener implements Runnable {

    private final Socket socket;
    private final DataInputStream in;
    private final VectorClock localClock;
    private final String clientUserId;
    private volatile boolean running = true;

    public NetworkListener(Socket socket, DataInputStream in, VectorClock localClock, String clientUserId) {
        this.socket = socket;
        this.in = in;
        this.localClock = localClock;
        this.clientUserId = clientUserId;
    }

    @Override
    public void run() {
        try {
            while (running && !socket.isClosed()) {
                MessagePacket packet = ProtocolCodec.readPacket(in);
                handleIncomingPacket(packet);
            }
        } catch (IOException e) {
            if (running) {
                System.out.println("\n[CONEXÃO] Conexão com o servidor foi encerrada.");
            }
        }
    }

    private void handleIncomingPacket(MessagePacket packet) {
        // Atualizar Relógio Vetorial local ao receber mensagem
        synchronized (localClock) {
            localClock.merge(packet.getVectorClock());
        }

        switch (packet.getType()) {
            case LOGIN_RESP:
                System.out.println("\n[RESPOSTA LOGIN] " + packet.getPayloadAsString());
                break;
            case TEXT_DIRECT: {
                boolean validSignature = CryptoUtil.verifySignature(packet.getSenderId(), packet.getPayloadAsString(), packet.getSignature(), CryptoUtil.DEFAULT_FEDERATION_KEY);
                String authTag = validSignature ? "[AUTÊNTICO OK]" : "[ASSINATURA INVÁLIDA ALERTA]";
                System.out.printf("%n[MENSAGEM DE %s %s (VC: %s)]: %s%n",
                        packet.getSenderId(), authTag, packet.getVectorClock().serialize(), packet.getPayloadAsString());
                break;
            }
            case GROUP_MSG:
                System.out.printf("%n[GRUPO %s | De: %s (VC: %s)]: %s%n",
                        packet.getTargetId(), packet.getSenderId(), packet.getVectorClock().serialize(), packet.getPayloadAsString());
                break;
            case FILE_TRANSFER:
                saveReceivedFile(packet);
                break;
            case SEARCH_RESP:
                System.out.println("\n[USUÁRIOS ONLINE]: " + packet.getPayloadAsString());
                break;
            case LIST_GROUPS_RESP:
                System.out.println("\n[GRUPOS CADASTRADOS]:\n" + packet.getPayloadAsString());
                break;
            case HISTORY_RESP:
                System.out.println("\n" + packet.getPayloadAsString());
                break;
            case ERROR:
                System.out.println("\n[ERRO DO SERVIDOR ALERTA]: " + packet.getPayloadAsString());
                break;
            default:
                System.out.println("\n[PACOTE RECEBIDO]: " + packet.getType() + " -> " + packet.getPayloadAsString());
                break;
        }
        System.out.print("> "); // Reimprime o prompt do CLI
    }

    private void saveReceivedFile(MessagePacket packet) {
        try {
            File downloadsDir = new File("downloads");
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }
            File outFile = new File(downloadsDir, packet.getFileName());
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(packet.getPayload());
            }
            System.out.printf("%n[ARQUIVO RECEBIDO 📁] De: %s | Salvo em: %s (%d bytes)%n",
                    packet.getSenderId(), outFile.getAbsolutePath(), packet.getPayload().length);
        } catch (IOException e) {
            System.out.println("\n[ERRO] Falha ao salvar arquivo recebido: " + e.getMessage());
        }
    }

    public void stop() {
        this.running = false;
    }
}
