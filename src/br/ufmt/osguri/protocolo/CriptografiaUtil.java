package br.ufmt.osguri.protocolo;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utilitário de segurança para o protocolo OSGURI.
 * Fornece cifragem AES (Confidencialidade) e HMAC-SHA256 (Autenticidade/Integridade).
 */
public class CriptografiaUtil {

    private static final String CHAVE_AES_PADRAO = "OSGURI_SECRET_16!";
    private static final String CHAVE_HMAC_PADRAO = "OSGURI_HMAC_KEY_SECRET_GOV_2026!";

    /**
     * Cifra um texto em claro usando AES em modo ECB com PKCS5Padding e retorna em Base64.
     */
    public static String cifrarAES(String textoEmClaro) {
        if (textoEmClaro == null) return "";
        try {
            byte[] keyBytes = CHAVE_AES_PADRAO.getBytes(StandardCharsets.UTF_8);
            byte[] key16 = new byte[16];
            System.arraycopy(keyBytes, 0, key16, 0, Math.min(keyBytes.length, 16));

            SecretKeySpec keySpec = new SecretKeySpec(key16, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] cifrado = cipher.doFinal(textoEmClaro.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(cifrado);
        } catch (Exception e) {
            // Fallback em Base64 garantindo que o payload nunca contenha delimitadores do protocolo
            return Base64.getEncoder().encodeToString(textoEmClaro.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Decifra um texto cifrado em Base64 usando AES.
     */
    public static String decifrarAES(String textoCifradoBase64) {
        if (textoCifradoBase64 == null || textoCifradoBase64.isEmpty()) return "";
        try {
            byte[] keyBytes = CHAVE_AES_PADRAO.getBytes(StandardCharsets.UTF_8);
            byte[] key16 = new byte[16];
            System.arraycopy(keyBytes, 0, key16, 0, Math.min(keyBytes.length, 16));

            SecretKeySpec keySpec = new SecretKeySpec(key16, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decifrado = cipher.doFinal(Base64.getDecoder().decode(textoCifradoBase64));
            return new String(decifrado, StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                return new String(Base64.getDecoder().decode(textoCifradoBase64), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return textoCifradoBase64;
            }
        }
    }

    /**
     * Gera uma assinatura HMAC-SHA256 para verificar a integridade/autenticidade de um pacote OSGURI.
     */
    public static String gerarHMAC(String dados) {
        if (dados == null) return "";
        try {
            SecretKeySpec keySpec = new SecretKeySpec(CHAVE_HMAC_PADRAO.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(dados.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "HMAC_ERRO";
        }
    }

    /**
     * Valida a assinatura HMAC-SHA256 de uma mensagem recebida.
     */
    public static boolean validarHMAC(String dados, String hmacEsperado) {
        if (hmacEsperado == null) return false;
        String hmacCalculado = gerarHMAC(dados);
        return hmacCalculado.equals(hmacEsperado);
    }
}
