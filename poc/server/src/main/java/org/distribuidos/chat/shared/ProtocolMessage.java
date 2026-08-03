package org.distribuidos.chat.shared;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public class ProtocolMessage {
    public static final int MAGIC_NUMBER = 0x43484154; // "CHAT"
    public static final byte VERSION = 0x01;

    private final MessageType type;
    private final byte[] payload;
    private final long checksum; // CRC32 checksum of the payload

    public ProtocolMessage(MessageType type, byte[] payload) {
        this.type = type;
        this.payload = payload == null ? new byte[0] : payload;
        this.checksum = calculateCRC32(this.payload);
    }

    public ProtocolMessage(MessageType type, byte[] payload, long checksum) {
        this.type = type;
        this.payload = payload == null ? new byte[0] : payload;
        this.checksum = checksum;
    }

    public MessageType getType() {
        return type;
    }

    public byte[] getPayload() {
        return payload;
    }

    public String getPayloadAsString() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    public int getPayloadLength() {
        return payload.length;
    }

    public long getChecksum() {
        return checksum;
    }

    public boolean validateChecksum() {
        return calculateCRC32(payload) == checksum;
    }

    public static long calculateCRC32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    public static ProtocolMessage createLogin(String username) {
        byte[] bytes = username.getBytes(StandardCharsets.UTF_8);
        return new ProtocolMessage(MessageType.LOGIN, bytes);
    }

    public static ProtocolMessage createText(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new ProtocolMessage(MessageType.TEXT, bytes);
    }

    public static ProtocolMessage createBroadcast(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new ProtocolMessage(MessageType.BROADCAST, bytes);
    }

    public static ProtocolMessage createAck(String info) {
        byte[] bytes = info.getBytes(StandardCharsets.UTF_8);
        return new ProtocolMessage(MessageType.ACK, bytes);
    }

    public static ProtocolMessage createError(String errorMsg) {
        byte[] bytes = errorMsg.getBytes(StandardCharsets.UTF_8);
        return new ProtocolMessage(MessageType.ERROR, bytes);
    }

    @Override
    public String toString() {
        return "ProtocolMessage{" +
                "type=" + type +
                ", length=" + getPayloadLength() +
                ", checksum=" + checksum +
                '}';
    }
}
