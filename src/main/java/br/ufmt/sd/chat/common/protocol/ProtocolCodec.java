package br.ufmt.sd.chat.common.protocol;

import br.ufmt.sd.chat.common.model.MessagePacket;
import br.ufmt.sd.chat.common.model.MessageType;
import br.ufmt.sd.chat.common.model.VectorClock;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Codificador/Decodificador (Codec) do Protocolo de Aplicação da Federação sobre TCP.
 * 
 * Estrutura do Quadro (Frame) enviado pelo Socket:
 * [MAGIC_NUMBER - 4 bytes] (0x4F534755 = 'OSGU' / OSGURI Protocol)
 * [MESSAGE_TYPE  - 4 bytes] (1..14)
 * [SENDER_ID     - UTF]     (String)
 * [TARGET_ID     - UTF]     (String)
 * [VECTOR_CLOCK  - UTF]     (String serializada)
 * [SIGNATURE     - UTF]     (HMAC SHA-256)
 * [FILE_NAME     - UTF]     (String ou vazia)
 * [PAYLOAD_LEN   - 4 bytes] (int)
 * [PAYLOAD_BYTES - N bytes] (array de bytes do texto ou arquivo)
 */
public class ProtocolCodec {

    // Magic Number para validação do protocolo "OSGURI"
    public static final int MAGIC_NUMBER = 0x4F534755;

    /**
     * Serializa e envia um pacote pela conexão Socket TCP.
     */
    public static synchronized void sendPacket(DataOutputStream out, MessagePacket packet) throws IOException {
        out.writeInt(MAGIC_NUMBER);
        out.writeInt(packet.getType().getCode());
        out.writeUTF(packet.getSenderId() != null ? packet.getSenderId() : "");
        out.writeUTF(packet.getTargetId() != null ? packet.getTargetId() : "");
        out.writeUTF(packet.getVectorClock() != null ? packet.getVectorClock().serialize() : "");
        out.writeUTF(packet.getSignature() != null ? packet.getSignature() : "");
        out.writeUTF(packet.getFileName() != null ? packet.getFileName() : "");

        byte[] payload = packet.getPayload();
        if (payload == null) {
            payload = new byte[0];
        }
        out.writeInt(payload.length);
        if (payload.length > 0) {
            out.write(payload);
        }
        out.flush();
    }

    /**
     * Lê e desserializa um pacote da conexão Socket TCP.
     */
    public static MessagePacket readPacket(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC_NUMBER) {
            throw new IOException("Pacote inválido: Magic Number incorreto (0x" + Integer.toHexString(magic) + ")");
        }

        int typeCode = in.readInt();
        MessageType type = MessageType.fromCode(typeCode);

        String senderId = in.readUTF();
        String targetId = in.readUTF();
        String vectorClockStr = in.readUTF();
        String signature = in.readUTF();
        String fileName = in.readUTF();

        int payloadLen = in.readInt();
        byte[] payload = new byte[payloadLen];
        if (payloadLen > 0) {
            in.readFully(payload);
        }

        MessagePacket packet = new MessagePacket(type, senderId, targetId, payload);
        packet.setVectorClock(VectorClock.deserialize(vectorClockStr));
        packet.setSignature(signature);
        packet.setFileName(fileName);

        return packet;
    }
}
