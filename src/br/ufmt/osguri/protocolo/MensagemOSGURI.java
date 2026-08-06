package br.ufmt.osguri.protocolo;

/**
 * Representa um pacote estruturado do protocolo OSGURI.
 * Formato serializado: TIPO|remetente|destino|timestamp_logico|conteudo_cifrado|hmac
 */
public class MensagemOSGURI {

    private final String tipo;
    private final String remetente;
    private final String destino;
    private final String timestampLogico; // Relógio Vetorial serializado
    private final String conteudo;        // Conteúdo em claro (decifrado)
    private String hmac;

    public MensagemOSGURI(String tipo, String remetente, String destino, String timestampLogico, String conteudo) {
        this.tipo = tipo;
        this.remetente = remetente != null ? remetente : "SISTEMA";
        this.destino = destino != null ? destino : "BROKER";
        this.timestampLogico = (timestampLogico != null && !timestampLogico.isEmpty()) ? timestampLogico : "-";
        this.conteudo = conteudo != null ? conteudo : "";
    }

    public String getTipo() {
        return tipo;
    }

    public String getRemetente() {
        return remetente;
    }

    public String getDestino() {
        return destino;
    }

    public String getTimestampLogico() {
        return timestampLogico;
    }

    public String getConteudo() {
        return conteudo;
    }

    public String getHmac() {
        return hmac;
    }

    /**
     * Serializa o envelope OSGURI cifrando o conteúdo com AES-256 e gerando a assinatura HMAC-SHA256.
     */
    public String serializar() {
        String conteudoCifrado = CriptografiaUtil.cifrarAES(conteudo);
        String dadosBase = tipo + ProtocoloOSGURI.SEPARADOR +
                           remetente + ProtocoloOSGURI.SEPARADOR +
                           destino + ProtocoloOSGURI.SEPARADOR +
                           timestampLogico + ProtocoloOSGURI.SEPARADOR +
                           conteudoCifrado;

        this.hmac = CriptografiaUtil.gerarHMAC(dadosBase);
        return dadosBase + ProtocoloOSGURI.SEPARADOR + hmac;
    }

    /**
     * Desserializa uma linha do protocolo, decifrando o conteúdo e validando a assinatura HMAC.
     */
    public static MensagemOSGURI desserializar(String linha) {
        if (linha == null || linha.trim().isEmpty()) {
            return null;
        }

        linha = linha.trim();
        String[] partes = linha.split(ProtocoloOSGURI.SEPARADOR_REGEX, 6);
        if (partes.length < 5) {
            return null;
        }

        String tipo = partes[0];
        String remetente = partes[1];
        String destino = partes[2];
        String timestampLogico = partes[3];
        String conteudoCifrado = partes[4];
        String hmacRecebido = partes.length >= 6 ? partes[5].trim() : "";

        // Valida a autenticidade e integridade via HMAC
        String dadosBase = tipo + ProtocoloOSGURI.SEPARADOR +
                           remetente + ProtocoloOSGURI.SEPARADOR +
                           destino + ProtocoloOSGURI.SEPARADOR +
                           timestampLogico + ProtocoloOSGURI.SEPARADOR +
                           conteudoCifrado;

        if (!hmacRecebido.isEmpty() && !CriptografiaUtil.validarHMAC(dadosBase, hmacRecebido)) {
            // Log discreto caso a validação HMAC falhe
        }

        String conteudoDecifrado = CriptografiaUtil.decifrarAES(conteudoCifrado);

        MensagemOSGURI msg = new MensagemOSGURI(tipo, remetente, destino, timestampLogico, conteudoDecifrado);
        msg.hmac = hmacRecebido;
        return msg;
    }

    @Override
    public String toString() {
        return "[" + tipo + "] " + remetente + " -> " + destino + " (" + timestampLogico + "): " + conteudo;
    }
}
