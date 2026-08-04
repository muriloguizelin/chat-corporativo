package br.ufmt.sd.chat.server;

import br.ufmt.sd.chat.common.model.MessagePacket;
import br.ufmt.sd.chat.common.model.MessageType;
import br.ufmt.sd.chat.common.model.VectorClock;
import br.ufmt.sd.chat.common.security.CryptoUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Message Broker Central do Servidor.
 * Responsável pelo roteamento de mensagens entre clientes online, gestão de grupos,
 * ordenação causal via Vector Clocks e aplicação do RBAC.
 */
public class MessageBroker {

    private final Map<String, ClientHandler> activeClients = new ConcurrentHashMap<>();
    private final Map<String, GroupInfo> groups = new ConcurrentHashMap<>();
    private final VectorClock serverVectorClock = new VectorClock();
    private final HistoryManager historyManager = new HistoryManager();

    public MessageBroker() {
        // Criar um grupo inicial padrão da federação para testes
        GroupInfo fedGroup = new GroupInfo("grp-federal", "Conselho Federal de Crise", "ctrl-br-cgu", false, "all");
        groups.put(fedGroup.getGroupId(), fedGroup);
    }

    /**
     * Tenta registrar um novo usuário no servidor.
     */
    public synchronized boolean registerClient(String userId, ClientHandler handler) {
        if (activeClients.containsKey(userId)) {
            return false; // Usuário já está logado
        }
        activeClients.put(userId, handler);
        serverVectorClock.increment("SERVER");
        System.out.println("[BROKER] Usuário conectado com sucesso: " + userId);
        return true;
    }

    /**
     * Desconecta o usuário do servidor.
     */
    public synchronized void unregisterClient(String userId) {
        if (userId != null && activeClients.remove(userId) != null) {
            serverVectorClock.increment("SERVER");
            System.out.println("[BROKER] Usuário desconectado: " + userId);
        }
    }

    /**
     * Retorna a lista de IDs de usuários online cadastrados.
     */
    public List<String> getOnlineUsers() {
        return new ArrayList<>(activeClients.keySet());
    }

    /**
     * Processa o roteamento de uma mensagem de texto direta (1-para-1).
     */
    public void routeDirectMessage(MessagePacket packet, ClientHandler senderHandler) {
        String senderId = packet.getSenderId();
        String targetId = packet.getTargetId();

        // 1. Atualizar relógio vetorial do servidor e mesclar com o relógio da mensagem recebida
        synchronized (this) {
            serverVectorClock.merge(packet.getVectorClock());
            serverVectorClock.increment("SERVER");
            packet.setVectorClock(new VectorClock(serverVectorClock.getClockMap()));
        }

        // 2. Validação de Autenticidade e Integridade (HMAC)
        boolean validSignature = CryptoUtil.verifySignature(senderId, packet.getPayloadAsString(), packet.getSignature(), CryptoUtil.DEFAULT_FEDERATION_KEY);
        if (!validSignature) {
            senderHandler.sendError("Assinatura digital inválida! Falha no requisito de Não-Repúdio/Autenticidade.");
            return;
        }

        // 3. Validação de RBAC (Federação e Poderes)
        if (!RbacPolicyEngine.canCommunicate(senderId, targetId)) {
            senderHandler.sendError("Comunicação Bloqueada pelo RBAC: Usuários de Poderes/UFs distintas sem autorização.");
            return;
        }

        // 4. Gravar no Histórico Causal
        historyManager.recordMessage(packet);

        // 5. Entregar ao destinatário se estiver online
        ClientHandler targetHandler = activeClients.get(targetId);
        if (targetHandler != null) {
            try {
                targetHandler.sendPacket(packet);
            } catch (IOException e) {
                senderHandler.sendError("Falha na entrega de rede para " + targetId);
            }
        } else {
            senderHandler.sendError("Usuário " + targetId + " não está online no momento. Mensagem armazenada no histórico.");
        }
    }

    /**
     * Transfere arquivos entre remetente e destinatário.
     */
    public void routeFileTransfer(MessagePacket packet, ClientHandler senderHandler) {
        String senderId = packet.getSenderId();
        String targetId = packet.getTargetId();

        if (!RbacPolicyEngine.canCommunicate(senderId, targetId)) {
            senderHandler.sendError("Envio de arquivo bloqueado pelas políticas de segurança da Federação.");
            return;
        }

        synchronized (this) {
            serverVectorClock.merge(packet.getVectorClock());
            serverVectorClock.increment("SERVER");
            packet.setVectorClock(new VectorClock(serverVectorClock.getClockMap()));
        }

        historyManager.recordMessage(packet);

        ClientHandler targetHandler = activeClients.get(targetId);
        if (targetHandler != null) {
            try {
                targetHandler.sendPacket(packet);
            } catch (IOException e) {
                senderHandler.sendError("Erro ao transferir arquivo para " + targetId);
            }
        } else {
            senderHandler.sendError("Destinatário do arquivo não está online.");
        }
    }

    /**
     * Cria um novo grupo institucional.
     */
    public void createGroup(String groupId, String groupName, String creatorId, boolean adminOnly, String restrictedPoder, ClientHandler creatorHandler) {
        if (groups.containsKey(groupId)) {
            creatorHandler.sendError("Grupo com ID '" + groupId + "' já existe.");
            return;
        }

        GroupInfo newGroup = new GroupInfo(groupId, groupName, creatorId, adminOnly, restrictedPoder);
        groups.put(groupId, newGroup);

        String msg = "Grupo '" + groupName + "' (" + groupId + ") criado com sucesso!";
        MessagePacket resp = MessagePacket.createTextPacket(MessageType.CREATE_GROUP, "SERVER", creatorId, msg);
        try {
            creatorHandler.sendPacket(resp);
        } catch (IOException ignored) {}
    }

    /**
     * Roteia uma mensagem enviada para um Grupo.
     */
    public void routeGroupMessage(MessagePacket packet, ClientHandler senderHandler) {
        String groupId = packet.getTargetId();
        String senderId = packet.getSenderId();

        GroupInfo group = groups.get(groupId);
        if (group == null) {
            senderHandler.sendError("Grupo não encontrado: " + groupId);
            return;
        }

        // Tenta adicionar ao grupo se ainda não for membro
        if (!group.isMember(senderId)) {
            if (!group.addMember(senderId)) {
                senderHandler.sendError("Acesso negado ao grupo! Seu Poder/Órgão não possui permissão de ingresso.");
                return;
            }
        }

        // Valida se o usuário pode falar no grupo (Exemplo: apenas admin)
        if (!group.canSpeak(senderId)) {
            senderHandler.sendError("Restrição de Fala: Apenas o Administrador pode enviar mensagens neste grupo.");
            return;
        }

        synchronized (this) {
            serverVectorClock.merge(packet.getVectorClock());
            serverVectorClock.increment("SERVER");
            packet.setVectorClock(new VectorClock(serverVectorClock.getClockMap()));
        }

        historyManager.recordMessage(packet);

        // Repassar mensagem a todos os membros do grupo que estiverem online
        for (String memberId : group.getMembers()) {
            if (!memberId.equals(senderId)) {
                ClientHandler memberHandler = activeClients.get(memberId);
                if (memberHandler != null) {
                    try {
                        memberHandler.sendPacket(packet);
                    } catch (IOException ignored) {}
                }
            }
        }
    }

    /**
     * Retorna a lista de grupos cadastrados.
     */
    public List<String> getGroupsList() {
        List<String> list = new ArrayList<>();
        for (GroupInfo g : groups.values()) {
            list.add(g.getGroupId() + " - " + g.getGroupName() + " [Admin: " + g.getCreatorId() + " | SomenteAdmin: " + g.isAdminOnlySpeak() + "]");
        }
        return list;
    }

    /**
     * Busca o histórico de mensagens trocadas.
     */
    public List<MessagePacket> getHistory(String userId, String targetId) {
        return historyManager.getHistoryFor(userId, targetId);
    }
}
