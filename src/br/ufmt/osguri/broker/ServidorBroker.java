package br.ufmt.osguri.broker;

import br.ufmt.osguri.protocolo.Poder;
import br.ufmt.osguri.protocolo.ProtocoloOSGURI;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servidor Broker Central do Sistema de Comunicação Corporativo OSGURI.
 * Gerencia conexões concorrentes utilizando pool de threads (ExecutorService).
 */
public class ServidorBroker {

    private final int porta;
    private final Map<String, ClienteHandler> clientesConectados;
    private final Map<String, Grupo> grupos;
    private final LoggerNaoRepudio loggerAudit;
    private final ExecutorService threadPool;

    public ServidorBroker(int porta) {
        this.porta = porta;
        this.clientesConectados = new ConcurrentHashMap<>();
        this.grupos = new ConcurrentHashMap<>();
        this.loggerAudit = new LoggerNaoRepudio();
        this.threadPool = Executors.newFixedThreadPool(100);
    }

    public void iniciar() {
        System.out.println("=================================================");
        System.out.println("   BROKER OSGURI INICIADO NA PORTA " + porta);
        System.out.println("   Modo: Governo Unificado Distribuído (30+ Estados)");
        System.out.println("=================================================");

        try (ServerSocket serverSocket = new ServerSocket(porta)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClienteHandler handler = new ClienteHandler(socket, this);
                threadPool.submit(handler);
            }
        } catch (IOException e) {
            System.err.println("Erro crítico no Broker OSGURI: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    public boolean registrarCliente(String id, ClienteHandler handler) {
        if (id == null) return false;
        return clientesConectados.putIfAbsent(id.toUpperCase(), handler) == null;
    }

    public void removerCliente(String id) {
        if (id != null) {
            clientesConectados.remove(id.toUpperCase());
        }
    }

    public ClienteHandler buscarCliente(String id) {
        if (id == null) return null;
        return clientesConectados.get(id.toUpperCase());
    }

    public String listarUsuariosCadastrados() {
        StringBuilder sb = new StringBuilder();
        for (ClienteHandler c : clientesConectados.values()) {
            sb.append("\n - ID: ").append(c.getUsuarioId())
              .append(" | Nome: ").append(c.getNome())
              .append(" | Órgão: ").append(c.getOrgao())
              .append(" | Poder: ").append(c.getPoder());
        }
        return sb.length() > 0 ? sb.toString() : "\nNenhum usuário cadastrado no momento.";
    }

    public boolean criarGrupo(String nomeGrupo, Grupo.TipoGrupo tipo, String criadorId, Poder poderRestrito) {
        if (grupos.containsKey(nomeGrupo)) {
            return false;
        }
        Grupo grupo = new Grupo(nomeGrupo, tipo, criadorId, poderRestrito);
        grupos.put(nomeGrupo, grupo);
        return true;
    }

    public boolean entrarGrupo(String nomeGrupo, String usuarioId, Poder poderUsuario) {
        Grupo grupo = grupos.get(nomeGrupo);
        if (grupo == null) {
            return false;
        }
        return grupo.adicionarMembro(usuarioId, poderUsuario);
    }

    public Grupo buscarGrupo(String nomeGrupo) {
        return grupos.get(nomeGrupo);
    }

    public LoggerNaoRepudio getLoggerAudit() {
        return loggerAudit;
    }

    public static void main(String[] args) {
        int porta = ProtocoloOSGURI.PORTA_PADRAO;
        String envPorta = System.getenv("OSGURI_PORT");
        if (envPorta != null && !envPorta.trim().isEmpty()) {
            try {
                porta = Integer.parseInt(envPorta.trim());
            } catch (NumberFormatException ignored) {}
        }
        if (args.length > 0 && !args[0].trim().isEmpty()) {
            try {
                porta = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException ignored) {}
        }

        ServidorBroker broker = new ServidorBroker(porta);
        broker.iniciar();
    }
}
