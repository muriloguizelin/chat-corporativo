package org.distribuidos.chat.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileTransferRegistry {
    private static final Map<String, String> registry = new ConcurrentHashMap<>();

    public static void register(String transferId, String recipient) {
        registry.put(transferId, recipient);
    }

    public static String getRecipient(String transferId) {
        return registry.get(transferId);
    }

    public static void unregister(String transferId) {
        registry.remove(transferId);
    }
}
