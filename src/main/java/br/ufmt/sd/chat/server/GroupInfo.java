package br.ufmt.sd.chat.server;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Representa um Grupo de Comunicação Institucional.
 * Controla os membros, administrador, e políticas de restrição de entrada e fala.
 */
public class GroupInfo {
    private final String groupId;
    private final String groupName;
    private final String creatorId;
    private final boolean adminOnlySpeak;
    private final String restrictedPoder; // "all" ou poder específico "jud", "exec", "leg", "ctrl"
    private final Set<String> members = ConcurrentHashMap.newKeySet();

    public GroupInfo(String groupId, String groupName, String creatorId, boolean adminOnlySpeak, String restrictedPoder) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.creatorId = creatorId;
        this.adminOnlySpeak = adminOnlySpeak;
        this.restrictedPoder = restrictedPoder != null ? restrictedPoder.toLowerCase() : "all";
        this.members.add(creatorId);
    }

    public String getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public boolean isAdminOnlySpeak() {
        return adminOnlySpeak;
    }

    public String getRestrictedPoder() {
        return restrictedPoder;
    }

    public Set<String> getMembers() {
        return members;
    }

    public boolean addMember(String userId) {
        // Valida se o usuário pertence ao poder permitido para o grupo
        if (!"all".equals(restrictedPoder)) {
            String[] parts = userId.toLowerCase().split("-");
            if (parts.length >= 1 && !restrictedPoder.equals(parts[0]) && !"ctrl".equals(parts[0])) {
                return false; // Acesso negado: poder incompatível
            }
        }
        members.add(userId);
        return true;
    }

    public boolean isMember(String userId) {
        return members.contains(userId);
    }

    public boolean canSpeak(String userId) {
        if (!isMember(userId)) return false;
        if (adminOnlySpeak && !userId.equalsIgnoreCase(creatorId)) {
            return false; // Apenas o administrador/criador pode falar neste grupo
        }
        return true;
    }
}
