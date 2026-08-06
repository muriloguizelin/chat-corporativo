package br.ufmt.osguri.protocolo;

/**
 * Definições e constantes do protocolo OSGURI (Open Secure Government Unified Relay Interface).
 */
public class ProtocoloOSGURI {

    // Tipos de Mensagens Obrigatórios
    public static final String LOGIN = "LOGIN";
    public static final String MSG = "MSG";
    public static final String ARQUIVO = "ARQUIVO";
    public static final String BUSCA = "BUSCA";
    public static final String ONLINE = "ONLINE";
    public static final String GRUPO_CRIAR = "GRUPO_CRIAR";
    public static final String GRUPO_ENTRAR = "GRUPO_ENTRAR";
    public static final String GRUPO_MSG = "GRUPO_MSG";
    public static final String HISTORICO = "HISTORICO";
    public static final String ERRO = "ERRO";
    public static final String OK = "OK";

    // Separadores do Protocolo OSGURI
    public static final String SEPARADOR = "|";
    public static final String SEPARADOR_REGEX = "\\|";

    // Porta Padrão do Broker
    public static final int PORTA_PADRAO = 12345;
}
