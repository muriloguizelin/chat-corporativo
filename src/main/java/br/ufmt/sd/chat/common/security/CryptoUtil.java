package br.ufmt.sd.chat.common.security;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Utilitário de Segurança para o Chat Corporativo da Federação.
 * Implementa os requisitos Não-Funcionais:
 * - Autenticidade (HMAC-SHA256)
 * - Não Repúdio (Assinatura baseada em HMAC acoplada ao ID do remetente)
 * - Confidencialidade (Criptografia simétrica AES-128)
 */
public class CryptoUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String AES_ALGORITHM = "AES";
    
    // Chave padrão da federação (para fins pedagógicos da PoC)
    public static final String DEFAULT_FEDERATION_KEY = "UFMT_SD_FEDERATION_SECRET_KEY_2026";

    /**
     * Gera uma assinatura HMAC-SHA256 sobre o conteúdo da mensagem.
     */
    public static String generateSignature(String senderId, String payloadContent, String secretKey) {
        try {
            String dataToSign = senderId + ":" + payloadContent;
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar assinatura digital HMAC", e);
        }
    }

    /**
     * Valida a assinatura de uma mensagem recebida para garantir integridade e autenticidade.
     */
    public static boolean verifySignature(String senderId, String payloadContent, String signature, String secretKey) {
        if (signature == null || signature.isEmpty()) return false;
        String expectedSignature = generateSignature(senderId, payloadContent, secretKey);
        return expectedSignature.equals(signature);
    }

    /**
     * Criptografa o payload com AES-128 para confidencialidade de ponta a ponta.
     */
    public static byte[] encryptAES(byte[] input, String secretKey) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(secretKey.getBytes(StandardCharsets.UTF_8));
            byte[] key16 = new byte[16];
            System.arraycopy(keyBytes, 0, key16, 0, 16);
            
            SecretKeySpec keySpec = new SecretKeySpec(key16, AES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new RuntimeException("Erro na criptografia AES", e);
        }
    }

    /**
     * Descriptografa o payload AES-128.
     */
    public static byte[] decryptAES(byte[] input, String secretKey) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(secretKey.getBytes(StandardCharsets.UTF_8));
            byte[] key16 = new byte[16];
            System.arraycopy(keyBytes, 0, key16, 0, 16);

            SecretKeySpec keySpec = new SecretKeySpec(key16, AES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new RuntimeException("Erro na descriptografia AES", e);
        }
    }
}
