package org.distribuidos.chat.shared;

public enum MessageType {
    LOGIN((byte) 0x01),
    TEXT((byte) 0x02),
    FILE_START((byte) 0x03),
    FILE_CHUNK((byte) 0x04),
    ACK((byte) 0x05),
    ERROR((byte) 0x06),
    BROADCAST((byte) 0x07),
    LOGOUT((byte) 0x08);

    private final byte code;

    MessageType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static MessageType fromCode(byte code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type code: " + code);
    }
}
