package org.distribuidos.chat.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.distribuidos.chat.shared.ProtocolMessage;

public class ClientManager {
    // Map to keep track of active connection handlers by username
    private final Map<String, ConnectionHandler> activeClients = new ConcurrentHashMap<>();
    
    // Map of group name to set of member usernames
    private final Map<String, Set<String>> groups = new ConcurrentHashMap<>();
    
    // Map of group name to restricted organization/domain (e.g. group "judiciario-privado" restricted to "*.judiciario.*")
    private final Map<String, String> groupIngressRestrictions = new ConcurrentHashMap<>();

    public ClientManager() {
        // Pre-create some mock institutional groups
        groups.put("nacional.geral", ConcurrentHashMap.newKeySet());
        groups.put("sp.tjsp.grupo", ConcurrentHashMap.newKeySet());
        
        // Define some ingress restrictions
        groupIngressRestrictions.put("sp.tjsp.grupo", "tjsp"); // only users containing "tjsp" in hierarchy can join
    }

    public synchronized boolean registerClient(String username, ConnectionHandler handler) {
        if (activeClients.containsKey(username)) {
            return false; // User already logged in
        }
        activeClients.put(username, handler);
        System.out.println("[ClientManager] Usuário registrado: " + username);
        
        // Auto-join the global group
        joinGroup(username, "nacional.geral");
        return true;
    }

    public synchronized void unregisterClient(String username) {
        if (username != null) {
            activeClients.remove(username);
            System.out.println("[ClientManager] Usuário desconectado: " + username);
            
            // Remove user from all groups
            for (Set<String> members : groups.values()) {
                members.remove(username);
            }
        }
    }

    public ConnectionHandler getClientHandler(String username) {
        return activeClients.get(username);
    }

    public List<String> getOnlineUsers() {
        List<String> users = new ArrayList<>(activeClients.keySet());
        Collections.sort(users);
        return users;
    }

    /**
     * Checks if communication between sender and receiver is allowed based on hierarchical ID.
     * Rule: User format is: estado.orgao.usuario (e.g., sp.tjsp.alice, df.planalto.marcos).
     * Context check: "Judiciário" cannot talk directly to "Executivo" in certain states (e.g. SP) due to compliance.
     */
    public boolean isCommunicationAllowed(String sender, String recipient) {
        String[] senderParts = sender.split("\\.");
        String[] recipientParts = recipient.split("\\.");
        
        if (senderParts.length >= 2 && recipientParts.length >= 2) {
            String senderState = senderParts[0];
            String senderOrg = senderParts[1];
            
            String recipientState = recipientParts[0];
            String recipientOrg = recipientParts[1];
            
            // Exemplo de Restrição Federativa: Judiciário (ex: tjsp, tjrj, stf) não pode se comunicar diretamente 
            // com o Executivo (ex: planalto, sefaz, detran) em contextos estaduais diretos se pertencerem a estados diferentes,
            // ou se houver regra explícita de bloqueio.
            boolean isSenderJudiciary = senderOrg.toLowerCase().contains("tj") || senderOrg.toLowerCase().contains("jud");
            boolean isRecipientExec = recipientOrg.toLowerCase().contains("detran") || recipientOrg.toLowerCase().contains("sefaz") || recipientOrg.toLowerCase().contains("exec");
            
            if (isSenderJudiciary && isRecipientExec && !senderState.equalsIgnoreCase(recipientState)) {
                System.out.println("[ClientManager] Restrição violada: Comunicação proibida entre Judiciário de " + senderState + " e Executivo de " + recipientState);
                return false;
            }
        }
        return true;
    }

    public synchronized String joinGroup(String username, String groupName) {
        // Create group if it doesn't exist
        groups.putIfAbsent(groupName, ConcurrentHashMap.newKeySet());
        
        // Check group ingress restriction
        String restriction = groupIngressRestrictions.get(groupName);
        if (restriction != null) {
            if (!username.toLowerCase().contains(restriction.toLowerCase())) {
                return "Erro: Apenas membros do órgão '" + restriction + "' podem ingressar no grupo '" + groupName + "'.";
            }
        }
        
        groups.get(groupName).add(username);
        return "Sucesso: Ingressou no grupo " + groupName;
    }

    public Set<String> getGroupMembers(String groupName) {
        return groups.getOrDefault(groupName, Collections.emptySet());
    }

    public boolean groupExists(String groupName) {
        return groups.containsKey(groupName);
    }
}
