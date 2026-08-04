package br.ufmt.sd.chat.common.model;

/**
 * Enumeração de tipos de mensagens do Protocolo de Aplicação da Federação.
 * Define o identificador numérico de cada tipo de operação trafegada via Socket TCP.
 */
public enum MessageType {
    LOGIN(1),
    LOGIN_RESP(2),
    TEXT_DIRECT(3),
    FILE_TRANSFER(4),
    SEARCH_USERS(5),
    SEARCH_RESP(6),
    CREATE_GROUP(7),
    GROUP_MSG(8),
    LIST_GROUPS(9),
    LIST_GROUPS_RESP(10),
    HISTORY_REQ(11),
    HISTORY_RESP(12),
    ERROR(13),
    LOGOUT(14);

    private final int code;

    MessageType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * Converte o código numérico no Enum correspondente.
     */
    public static MessageType fromCode(int code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Código de mensagem desconhecido: " + code);
    }
}
