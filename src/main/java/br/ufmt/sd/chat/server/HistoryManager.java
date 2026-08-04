package br.ufmt.sd.chat.server;

import br.ufmt.sd.chat.common.model.MessagePacket;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gerenciador de Histórico de Mensagens do Servidor.
 * Garante a persistência e a recuperação do histórico de mensagens respeitando a ORDEM CAUSAL.
 */
public class HistoryManager {

    private final List<MessagePacket> messageHistory = new CopyOnWriteArrayList<>();
    private static final String HISTORY_FILE = "chat_history.log";

    public HistoryManager() {}

    /**
     * Adiciona uma mensagem ao histórico e registra no arquivo log.
     */
    public synchronized void recordMessage(MessagePacket packet) {
        messageHistory.add(packet);
        logToFile(packet);
    }

    /**
     * Retorna todas as mensagens trocadas entre o usuário e um destinatário (ou de um grupo).
     */
    public List<MessagePacket> getHistoryFor(String userId, String targetId) {
        List<MessagePacket> filtered = new ArrayList<>();
        for (MessagePacket msg : messageHistory) {
            boolean isGroup = targetId.startsWith("grp-");
            if (isGroup) {
                if (targetId.equals(msg.getTargetId())) {
                    filtered.add(msg);
                }
            } else {
                if ((userId.equals(msg.getSenderId()) && targetId.equals(msg.getTargetId())) ||
                    (targetId.equals(msg.getSenderId()) && userId.equals(msg.getTargetId()))) {
                    filtered.add(msg);
                }
            }
        }
        return filtered;
    }

    private void logToFile(MessagePacket packet) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(HISTORY_FILE, true))) {
            writer.write(String.format("[%s] De: %s | Para: %s | VC: %s | Msg: %s%n",
                    packet.getType(),
                    packet.getSenderId(),
                    packet.getTargetId(),
                    packet.getVectorClock().serialize(),
                    packet.getFileName().isEmpty() ? packet.getPayloadAsString() : "[Arquivo: " + packet.getFileName() + "]"));
        } catch (IOException e) {
            System.err.println("Erro ao gravar histórico no arquivo log: " + e.getMessage());
        }
    }
}
