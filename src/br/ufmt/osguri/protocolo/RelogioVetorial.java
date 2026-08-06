package br.ufmt.osguri.protocolo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação de Relógio Vetorial (Vector Clock) para garantia de ordem causal no protocolo OSGURI.
 */
public class RelogioVetorial implements Comparable<RelogioVetorial> {

    private final Map<String, Integer> relogio;

    public RelogioVetorial() {
        this.relogio = new ConcurrentHashMap<>();
    }

    public RelogioVetorial(Map<String, Integer> mapaInicial) {
        this.relogio = new ConcurrentHashMap<>(mapaInicial);
    }

    /**
     * Incrementa o contador do nó/usuário especificado.
     */
    public synchronized void incrementar(String id) {
        relogio.put(id, relogio.getOrDefault(id, 0) + 1);
    }

    /**
     * Funde (merge) o relógio local com o relógio recebido de outra entidade,
     * atualizando cada posição para o valor máximo encontrado.
     */
    public synchronized void merge(Map<String, Integer> outroRelogio) {
        if (outroRelogio == null) return;
        for (Map.Entry<String, Integer> entry : outroRelogio.entrySet()) {
            String chave = entry.getKey();
            int valorOutro = entry.getValue();
            int valorAtual = relogio.getOrDefault(chave, 0);
            relogio.put(chave, Math.max(valorAtual, valorOutro));
        }
    }

    public synchronized Map<String, Integer> getRelogio() {
        return Collections.unmodifiableMap(new HashMap<>(relogio));
    }

    /**
     * Serializa o relógio no formato: id1:v1,id2:v2
     */
    public synchronized String serializar() {
        StringBuilder sb = new StringBuilder();
        boolean primeiro = true;
        for (Map.Entry<String, Integer> entry : relogio.entrySet()) {
            if (!primeiro) sb.append(",");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
            primeiro = false;
        }
        return sb.toString();
    }

    /**
     * Reconstrói o relógio a partir de uma String serializada.
     */
    public static RelogioVetorial desserializar(String str) {
        RelogioVetorial rv = new RelogioVetorial();
        if (str == null || str.trim().isEmpty() || str.equals("{}") || str.equals("-")) {
            return rv;
        }
        String[] pares = str.split(",");
        for (String par : pares) {
            String[] partes = par.split(":");
            if (partes.length == 2) {
                try {
                    String id = partes[0].trim();
                    int val = Integer.parseInt(partes[1].trim());
                    rv.relogio.put(id, val);
                } catch (NumberFormatException e) {
                    // Ignora pares malformados
                }
            }
        }
        return rv;
    }

    /**
     * Comparação simples de ordem causal baseada no somatório das componentes do relógio.
     */
    @Override
    public int compareTo(RelogioVetorial outro) {
        if (outro == null) return 1;
        int somaThis = this.relogio.values().stream().mapToInt(Integer::intValue).sum();
        int somaOutro = outro.relogio.values().stream().mapToInt(Integer::intValue).sum();
        return Integer.compare(somaThis, somaOutro);
    }

    @Override
    public String toString() {
        return serializar();
    }
}
