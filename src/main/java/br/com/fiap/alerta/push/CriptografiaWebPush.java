package br.com.fiap.alerta.push;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Criptografia do payload do Web Push (RFC 8291, content-encoding aes128gcm do RFC 8188), só com o JDK:
 * ECDH P-256 entre uma chave efêmera e a chave do navegador, HKDF-SHA256 e AES-128-GCM num único registro.
 */
public final class CriptografiaWebPush {

    static final int TAMANHO_REGISTRO = 4096;
    /** Cabeçalho (86) + mensagem + delimitador (1) + tag (16) precisa caber nos 4096 bytes do push service. */
    public static final int TAMANHO_MAXIMO_MENSAGEM = 3993;

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final byte[] INFO_CHAVE = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] INFO_CEK = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] INFO_NONCE = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);

    private CriptografiaWebPush() {
    }

    /** Uso normal: salt e par de chaves efêmero novos a cada mensagem. */
    public static byte[] criptografar(byte[] mensagem, byte[] chaveP256dhNavegador, byte[] segredoAuth) {
        byte[] salt = new byte[16];
        ALEATORIO.nextBytes(salt);
        return criptografar(mensagem, ChavesEc.publica(chaveP256dhNavegador), segredoAuth, ChavesEc.gerarPar(), salt);
    }

    /** Versão determinística, usada pelo teste com o exemplo do RFC 8291. */
    static byte[] criptografar(byte[] mensagem, ECPublicKey chaveNavegador, byte[] segredoAuth, KeyPair parServidor,
            byte[] salt) {
        if (mensagem.length > TAMANHO_MAXIMO_MENSAGEM) {
            throw new IllegalArgumentException("mensagem de push acima de " + TAMANHO_MAXIMO_MENSAGEM + " bytes");
        }
        try {
            byte[] publicaNavegador = ChavesEc.codificar(chaveNavegador);
            byte[] publicaServidor = ChavesEc.codificar((ECPublicKey) parServidor.getPublic());

            KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
            ecdh.init(parServidor.getPrivate());
            ecdh.doPhase(chaveNavegador, true);
            byte[] segredoEcdh = ecdh.generateSecret();

            // HKDF em dois estágios (RFC 8291, seção 3.4): combina o ECDH com o segredo de autenticação
            byte[] prkChave = hmac(segredoAuth, segredoEcdh);
            byte[] ikm = hmac(prkChave, concatenar(INFO_CHAVE, publicaNavegador, publicaServidor, new byte[] {1}));
            byte[] prk = hmac(salt, ikm);
            byte[] cek = Arrays.copyOf(hmac(prk, concatenar(INFO_CEK, new byte[] {1})), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, concatenar(INFO_NONCE, new byte[] {1})), 12);

            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            // 0x02 = delimitador do último (e único) registro
            byte[] cifrado = aes.doFinal(concatenar(mensagem, new byte[] {2}));

            ByteBuffer cabecalho = ByteBuffer.allocate(16 + 4 + 1 + publicaServidor.length)
                    .put(salt)
                    .putInt(TAMANHO_REGISTRO)
                    .put((byte) publicaServidor.length)
                    .put(publicaServidor);
            return concatenar(cabecalho.array(), cifrado);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao criptografar o payload do push", e);
        }
    }

    private static byte[] hmac(byte[] chave, byte[] dados) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chave, "HmacSHA256"));
        return mac.doFinal(dados);
    }

    private static byte[] concatenar(byte[]... partes) {
        int total = 0;
        for (byte[] parte : partes) {
            total += parte.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(total);
        for (byte[] parte : partes) {
            buffer.put(parte);
        }
        return buffer.array();
    }
}
