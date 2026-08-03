package org.distribuidos.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerMain {
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida. Utilizando porta padrão " + DEFAULT_PORT);
            }
        }

        ClientManager clientManager = new ClientManager();
        ExecutorService threadPool = Executors.newCachedThreadPool();

        System.out.println("==================================================");
        System.out.println("   SERVIDOR CHAT DISTRIBUÍDO INICIADO (ChatGov)   ");
        System.out.println("   Porta de escuta: " + port);
        System.out.println("   Modo: TCP puro com protocolo binário customizado");
        System.out.println("==================================================");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ConnectionHandler handler = new ConnectionHandler(clientSocket, clientManager);
                threadPool.submit(handler);
            }
        } catch (IOException e) {
            System.err.println("Erro crítico no servidor de socket: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }
}
