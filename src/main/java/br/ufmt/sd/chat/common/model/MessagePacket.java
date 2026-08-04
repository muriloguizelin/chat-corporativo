package br.ufmt.sd.chat.common.model;

import java.nio.charset.StandardCharsets;

/**
 * Representa a estrutura de dados encapsulada de uma mensagem do protocolo TCP.
 * 
 * Contém o cabeçalho completo com identificação, tipo, relógio vetorial,
 * assinatura HMAC para autenticidade/não-repúdio e o payload bruto (texto ou bytes de arquivo).
 */
public class MessagePacket {
    private MessageType type;
    private String senderId;
    private String targetId;
    private VectorClock vectorClock;
    private String signature;
    private String fileName; // Usado em transferências de arquivo (opcional)
    private byte[] payload;

    public MessagePacket() {
        this.vectorClock = new VectorClock();
        this.signature = "";
        this.fileName = "";
        this.payload = new byte[0];
    }

    public MessagePacket(MessageType type, String senderId, String targetId, byte[] payload) {
        this();
        this.type = type;
        this.senderId = senderId;
        this.targetId = targetId;
        this.payload = payload != null ? payload : new byte[0];
    }

    public static MessagePacket createTextPacket(MessageType type, String senderId, String targetId, String text) {
        byte[] payloadBytes = text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0];
        return new MessagePacket(type, senderId, targetId, payloadBytes);
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public VectorClock getVectorClock() {
        return vectorClock;
    }

    public void setVectorClock(VectorClock vectorClock) {
        this.vectorClock = vectorClock;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public String getPayloadAsString() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "MessagePacket{" +
                "type=" + type +
                ", sender='" + senderId + '\'' +
                ", target='" + targetId + '\'' +
                ", clock=" + vectorClock +
                ", fileName='" + fileName + '\'' +
                ", payloadLen=" + (payload != null ? payload.length : 0) +
                '}';
    }
}
