package br.ufmt.sd.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ponto de Entrada do Servidor Broker de Chat Corporativo da Federação (PoC Sockets TCP).
 * 
 * Disciplina: Sistemas Distribuídos - UFMT
 */
public class ServerMain {

    public static final int DEFAULT_PORT = 9090;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("[SERVER] Porta inválida fornecida nos argumentos. Usando porta padrão " + DEFAULT_PORT);
            }
        }

        MessageBroker broker = new MessageBroker();
        ExecutorService threadPool = Executors.newCachedThreadPool();

        System.out.println("===============================================================");
        System.out.println("   SERVIDOR CHAT CORPORATIVO SEGURO DA FEDERAÇÃO (UFMT-SD)    ");
        System.out.println("===============================================================");
        System.out.println(" Status: ONLINE");
        System.out.println(" Porta TCP: " + port);
        System.out.println(" Protocolo: OSGURI (Framing Binário sobre TCP)");
        System.out.println(" Recursos: Ordenação Causal (Vector Clocks), RBAC, Sockets Concorrentes");
        System.out.println("===============================================================");
        System.out.println("[SERVER] Aguardando conexões de clientes...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SERVER] Nova conexão recebida de: " + clientSocket.getRemoteSocketAddress());
                
                ClientHandler clientHandler = new ClientHandler(clientSocket, broker);
                threadPool.execute(clientHandler);
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Erro fatal no ServerSocket: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }
}
