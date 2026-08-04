package br.ufmt.sd.chat.server;

/**
 * Módulo de Controle de Acesso Baseado em Papéis e Federação (RBAC Engine).
 * 
 * Regras Institucionais da Federação:
 * Padrão de ID de Usuário: <poder>-<uf>-<nome> (ex: exec-mt-joao, jud-df-maria, ctrl-br-tcu)
 * 
 * Poderes:
 * - exec: Executivo
 * - leg: Legislativo
 * - jud: Judiciário
 * - ctrl: Controle (TCU, CGU, MPF - possui acesso irrestrito para auditoria)
 */
public class RbacPolicyEngine {

    public static boolean canCommunicate(String senderId, String targetId) {
        if (senderId == null || targetId == null) return false;
        
        // Em mensagens direcionadas a grupos, a validação de acesso ao grupo é tratada separadamente
        if (targetId.startsWith("grp-")) {
            return true;
        }

        String[] senderParts = senderId.toLowerCase().split("-");
        String[] targetParts = targetId.toLowerCase().split("-");

        // Se o formato do ID não seguir o padrão federativo de 3 partes, permite para retrocompatibilidade
        if (senderParts.length < 3 || targetParts.length < 3) {
            return true;
        }

        String senderPoder = senderParts[0];
        String senderUf = senderParts[1];

        String targetPoder = targetParts[0];
        String targetUf = targetParts[1];

        // Regra 1: Órgão de Controle (ctrl) pode comunicar-se com todos os Poderes e UFs
        if ("ctrl".equals(senderPoder) || "ctrl".equals(targetPoder)) {
            return true;
        }

        // Regra 2: Mesmo Poder e mesma UF (comunicação estadual interna) -> Permitido
        if (senderPoder.equals(targetPoder) && senderUf.equals(targetUf)) {
            return true;
        }

        // Regra 3: Mesmo Poder em UFs diferentes (ex: Judiciário-SP para Judiciário-MT) -> Permitido
        if (senderPoder.equals(targetPoder)) {
            return true;
        }

        // Regra 4: Poderes diferentes dentro da mesma UF (ex: Executivo-MT para Judiciário-MT) -> Permitido
        if (senderUf.equals(targetUf)) {
            return true;
        }

        // Regra 5: Poderes DIFERENTES em UFs DIFERENTES (ex: Judiciário-SP enviando para Executivo-MT) -> BLOQUEADO por razões de soberania e conformidade
        return false;
    }
}
