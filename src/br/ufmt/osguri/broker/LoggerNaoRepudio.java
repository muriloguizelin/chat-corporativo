package br.ufmt.osguri.broker;

import br.ufmt.osguri.protocolo.MensagemOSGURI;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Registrador de Não-Repúdio (Audit Logger).
 * Garante o registro permanente em arquivo e memória de todas as mensagens transmitidas pelo Broker OSGURI.
 */
public class LoggerNaoRepudio {

    private static final String NOME_ARQUIVO_LOG = "osguri_audit.log";
    private final List<String> historicoEmMemoria;

    public LoggerNaoRepudio() {
        this.historicoEmMemoria = new ArrayList<>();
    }

    /**
     * Registra o evento de envio de mensagem no log de auditoria de não-repúdio.
     */
    public synchronized void registrarEvento(MensagemOSGURI msg) {
        String logRegistro = String.format("[%s] TIPO: %s | DE: %s | PARA: %s | ORDEM_GLOBAL: %s | HMAC: %s",
                LocalDateTime.now(),
                msg.getTipo(),
                msg.getRemetente(),
                msg.getDestino(),
                msg.getTimestampLogico(),
                msg.getHmac()
        );

        historicoEmMemoria.add(logRegistro);
        System.out.println("[AUDITORIA NAÕ-REPÚDIO] " + logRegistro);

        try (PrintWriter out = new PrintWriter(new FileWriter(NOME_ARQUIVO_LOG, true))) {
            out.println(logRegistro);
        } catch (IOException e) {
            System.err.println("Erro ao gravar log de auditoria: " + e.getMessage());
        }
    }

    public synchronized List<String> getHistorico() {
        return new ArrayList<>(historicoEmMemoria);
    }
}
