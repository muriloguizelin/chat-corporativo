package br.ufmt.osguri.broker;

import br.ufmt.osguri.protocolo.Poder;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Representa um grupo corporativo de comunicação no Broker OSGURI.
 */
public class Grupo {

    public enum TipoGrupo {
        INSTITUCIONAL,
        PRIVADO
    }

    private final String nome;
    private final TipoGrupo tipo;
    private final String criadorId;
    private final Poder poderRestrito;
    private final Set<String> membros;

    public Grupo(String nome, TipoGrupo tipo, String criadorId, Poder poderRestrito) {
        this.nome = nome;
        this.tipo = tipo;
        this.criadorId = criadorId;
        this.poderRestrito = poderRestrito;
        this.membros = ConcurrentHashMap.newKeySet();
        if (criadorId != null) {
            this.membros.add(criadorId);
        }
    }

    public String getNome() {
        return nome;
    }

    public TipoGrupo getTipo() {
        return tipo;
    }

    public String getCriadorId() {
        return criadorId;
    }

    public Poder getPoderRestrito() {
        return poderRestrito;
    }

    public Set<String> getMembros() {
        return Collections.unmodifiableSet(membros);
    }

    /**
     * Valida se um usuário pode ingressar no grupo.
     * Grupos INSTITUCIONAIS exigem pertencimento ao mesmo Poder (ou pertencer ao Órgão de CONTROLE).
     */
    public boolean adicionarMembro(String usuarioId, Poder poderUsuario) {
        if (usuarioId == null) return false;
        String idNormalizado = usuarioId.toUpperCase();
        if (tipo == TipoGrupo.INSTITUCIONAL) {
            if (poderRestrito != null && poderUsuario != poderRestrito && poderUsuario != Poder.CONTROLE) {
                return false;
            }
        }
        membros.add(idNormalizado);
        return true;
    }

    public boolean eMembro(String usuarioId) {
        if (usuarioId == null) return false;
        return membros.contains(usuarioId.toUpperCase());
    }
}
