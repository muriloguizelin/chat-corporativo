package br.ufmt.osguri.protocolo;

/**
 * Enumeração dos Poderes e Órgãos do Governo no protocolo OSGURI.
 * Contém a matriz de restrição de comunicação direta entre os poderes.
 */
public enum Poder {
    EXECUTIVO,
    LEGISLATIVO,
    JUDICIARIO,
    CONTROLE;

    /**
     * Valida a matriz de comunicação entre os Poderes.
     * 
     * Regras Estritas:
     * 1. Comunicação interna entre o mesmo Poder é permitida (ex: EXECUTIVO -> EXECUTIVO).
     * 2. O Órgão de CONTROLE tem permissão de comunicar com qualquer Poder.
     * 3. Comunicação direta entre Poderes DIFERENTES (ex: EXECUTIVO <-> JUDICIÁRIO, EXECUTIVO <-> LEGISLATIVO) é ESTRITAMENTE BLOQUEADA.
     */
    public static boolean podeComunicar(Poder origem, Poder destino) {
        if (origem == null || destino == null) {
            return false;
        }
        if (origem == destino) {
            return true;
        }
        if (origem == CONTROLE || destino == CONTROLE) {
            return true;
        }
        
        // Bloqueio entre quaisquer poderes diferentes se não for Controle
        return false;
    }

    public static Poder parse(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return EXECUTIVO;
        }
        String valor = texto.trim().toUpperCase();
        for (Poder p : values()) {
            if (p.name().equals(valor) || p.name().startsWith(valor)) {
                return p;
            }
        }
        return inferirPoder(valor);
    }

    /**
     * Infere o Poder automaticamente a partir do nome do Órgão.
     */
    public static Poder inferirPoder(String orgao) {
        if (orgao == null) return EXECUTIVO;
        String o = orgao.trim().toUpperCase();
        if (o.contains("TJ") || o.contains("TRIB") || o.contains("JUIZ") || o.contains("FORUM") || 
            o.contains("STJ") || o.contains("STF") || o.contains("TRF") || o.contains("TRT") || o.contains("JUSTICA")) {
            return JUDICIARIO;
        }
        if (o.contains("LEG") || o.contains("AL") || o.contains("CAMARA") || o.contains("SENADO") || 
            o.contains("DEP") || o.contains("CONGRESSO")) {
            return LEGISLATIVO;
        }
        if (o.contains("CONTROLE") || o.contains("CGU") || o.contains("TCU") || o.contains("TCE") || o.contains("AUDIT")) {
            return CONTROLE;
        }
        return EXECUTIVO;
    }
}
