package org.distribuidos.chat.client;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

public class ClientMain {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8080;

    private final AtomicLong lamportClock = new AtomicLong(0);
    private Socket socket;
    private String username;

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        if (args.length > 0) {
            host = args[0];
        }
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida. Utilizando porta padrão " + DEFAULT_PORT);
            }
        }

        new ClientMain().start(host, port);
    }

    public void start(String host, int port) {
        System.out.println("==================================================");
        System.out.println("   CLIENTE CHAT DISTRIBUÍDO INICIADO (ChatGov)   ");
        System.out.println("   Conectando a: " + host + ":" + port);
        System.out.println("==================================================");

        try {
            socket = new Socket(host, port);
            System.out.println("[Conexão] Conectado ao servidor com sucesso.");

            // Start Message Listener thread
            MessageListener listener = new MessageListener(this, socket);
            Thread listenerThread = new Thread(listener, "MessageListenerThread");
            listenerThread.setDaemon(true);
            listenerThread.start();

            // Start Command Processor in main thread
            CommandProcessor processor = new CommandProcessor(this, socket);
            processor.run();

        } catch (IOException e) {
            System.err.println("[Erro] Não foi possível conectar ao servidor: " + e.getMessage());
        } finally {
            closeSocket();
        }
    }

    public long getClock() {
        return lamportClock.get();
    }

    public long incrementClock() {
        return lamportClock.incrementAndGet();
    }

    public void updateClock(long receivedValue) {
        long current;
        long next;
        do {
            current = lamportClock.get();
            next = Math.max(current, receivedValue) + 1;
        } while (!lamportClock.compareAndSet(current, next));
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    private void closeSocket() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
        System.out.println("[Conexão] Sessão finalizada.");
    }
}
