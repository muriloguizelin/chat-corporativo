package br.ufmt.sd.chat.common.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Implementação de Relógio Vetorial (Vector Clock) para garantia de ORDEM CAUSAL
 * em Sistemas Distribuídos.
 * 
 * Mantém um mapa do tipo <NodeId, Contador> que acompanha os eventos lógicos
 * registrados por cada participante do sistema.
 */
public class VectorClock implements Serializable {
    private static final long serialVersionUID = 1L;

    // Mapa contendo a contagem de eventos por nó/processo
    private final Map<String, Long> clockMap;

    public VectorClock() {
        this.clockMap = new HashMap<>();
    }

    public VectorClock(Map<String, Long> initialMap) {
        this.clockMap = new HashMap<>(initialMap);
    }

    /**
     * Incrementa o contador do nó informado indicando que um novo evento local ocorreu.
     */
    public synchronized void increment(String nodeId) {
        long current = clockMap.getOrDefault(nodeId, 0L);
        clockMap.put(nodeId, current + 1);
    }

    /**
     * Atualiza o relógio local ao receber um relógio vetorial de outra entidade
     * V_local[k] = max(V_local[k], V_recebido[k])
     */
    public synchronized void merge(VectorClock other) {
        if (other == null) return;
        for (Map.Entry<String, Long> entry : other.clockMap.entrySet()) {
            String nodeId = entry.getKey();
            long remoteTime = entry.getValue();
            long localTime = this.clockMap.getOrDefault(nodeId, 0L);
            this.clockMap.put(nodeId, Math.max(localTime, remoteTime));
        }
    }

    /**
     * Retorna uma cópia imutável dos contadores.
     */
    public Map<String, Long> getClockMap() {
        return Collections.unmodifiableMap(clockMap);
    }

    /**
     * Serializa o relógio em formato string simples: "node1:1;node2:3"
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        Map<String, Long> sorted = new TreeMap<>(clockMap);
        for (Map.Entry<String, Long> entry : sorted.entrySet()) {
            if (sb.length() > 0) sb.append(";");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * Reconstrói o relógio vetorial a partir da string serializada.
     */
    public static VectorClock deserialize(String data) {
        VectorClock vc = new VectorClock();
        if (data == null || data.trim().isEmpty()) {
            return vc;
        }
        String[] pairs = data.split(";");
        for (String pair : pairs) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                try {
                    vc.clockMap.put(kv[0].trim(), Long.parseLong(kv[1].trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return vc;
    }

    @Override
    public String toString() {
        return "VC[" + serialize() + "]";
    }
}
