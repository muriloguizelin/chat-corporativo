package org.distribuidos.chat.shared;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ProtocolParser {

    /**
     * Serializes and writes a ProtocolMessage to the output stream.
     */
    public static void writeMessage(OutputStream out, ProtocolMessage message) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(ProtocolMessage.MAGIC_NUMBER);
        dos.writeByte(ProtocolMessage.VERSION);
        dos.writeByte(message.getType().getCode());
        dos.writeInt(message.getPayloadLength());
        dos.writeInt((int) (message.getChecksum() & 0xFFFFFFFFL));
        if (message.getPayloadLength() > 0) {
            dos.write(message.getPayload());
        }
        dos.flush();
    }

    /**
     * Reads and deserializes a ProtocolMessage from the input stream.
     * Blocks until a message is available or EOF is reached.
     */
    public static ProtocolMessage readMessage(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        
        // Read magic number
        int magic;
        try {
            magic = dis.readInt();
        } catch (IOException e) {
            // Usually indicates connection closed by client
            throw e;
        }

        if (magic != ProtocolMessage.MAGIC_NUMBER) {
            throw new IOException("Protocol Error: Invalid Magic Number (Expected: 0x" + 
                    Integer.toHexString(ProtocolMessage.MAGIC_NUMBER) + ", Got: 0x" + 
                    Integer.toHexString(magic) + ")");
        }

        byte version = dis.readByte();
        if (version != ProtocolMessage.VERSION) {
            throw new IOException("Protocol Error: Unsupported version " + version);
        }

        byte typeCode = dis.readByte();
        MessageType type = MessageType.fromCode(typeCode);

        int payloadLength = dis.readInt();
        if (payloadLength < 0 || payloadLength > 100 * 1024 * 1024) { // 100MB limit for safety
            throw new IOException("Protocol Error: Invalid payload length: " + payloadLength);
        }

        int checksumInt = dis.readInt();
        long checksum = checksumInt & 0xFFFFFFFFL;

        byte[] payload = new byte[payloadLength];
        if (payloadLength > 0) {
            dis.readFully(payload);
        }

        ProtocolMessage message = new ProtocolMessage(type, payload, checksum);
        if (!message.validateChecksum()) {
            throw new IOException("Protocol Error: Checksum validation failed for message type " + type);
        }

        return message;
    }
}
