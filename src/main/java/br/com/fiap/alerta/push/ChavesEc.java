package br.com.fiap.alerta.push;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.util.Arrays;
import java.util.Base64;

/** Chaves P-256 no formato do Web Push: pública não comprimida (65 bytes), privada como escalar (32 bytes). */
public final class ChavesEc {

    public static final ECParameterSpec P256 = parametrosP256();

    private ChavesEc() {
    }

    public static byte[] decodificarBase64Url(String valor) {
        try {
            return Base64.getUrlDecoder().decode(valor.strip().replace('+', '-').replace('/', '_').replace("=", ""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("valor base64url inválido", e);
        }
    }

    public static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Valida que o ponto está na curva (RFC 8291, seção 7) antes de usar a chave. */
    public static ECPublicKey publica(byte[] naoComprimida) {
        if (naoComprimida.length != 65 || naoComprimida[0] != 0x04) {
            throw new IllegalArgumentException("chave pública P-256 deve ter 65 bytes começando com 0x04");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(naoComprimida, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(naoComprimida, 33, 65));
        if (!estaNaCurva(x, y)) {
            throw new IllegalArgumentException("chave pública fora da curva P-256");
        }
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("chave pública P-256 inválida", e);
        }
    }

    public static ECPrivateKey privada(byte[] escalar) {
        if (escalar.length != 32) {
            throw new IllegalArgumentException("chave privada P-256 deve ter 32 bytes");
        }
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, escalar), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("chave privada P-256 inválida", e);
        }
    }

    public static byte[] codificar(ECPublicKey chave) {
        byte[] saida = new byte[65];
        saida[0] = 0x04;
        System.arraycopy(bytes32(chave.getW().getAffineX()), 0, saida, 1, 32);
        System.arraycopy(bytes32(chave.getW().getAffineY()), 0, saida, 33, 32);
        return saida;
    }

    public static byte[] codificar(ECPrivateKey chave) {
        return bytes32(chave.getS());
    }

    public static KeyPair gerarPar() {
        try {
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("EC");
            gerador.initialize(P256);
            return gerador.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 indisponível na JVM", e);
        }
    }

    private static boolean estaNaCurva(BigInteger x, BigInteger y) {
        EllipticCurve curva = P256.getCurve();
        BigInteger p = ((ECFieldFp) curva.getField()).getP();
        if (x.signum() < 0 || x.compareTo(p) >= 0 || y.signum() < 0 || y.compareTo(p) >= 0) {
            return false;
        }
        BigInteger esquerda = y.pow(2).mod(p);
        BigInteger direita = x.pow(3).add(curva.getA().multiply(x)).add(curva.getB()).mod(p);
        return esquerda.equals(direita);
    }

    private static byte[] bytes32(BigInteger valor) {
        byte[] bruto = valor.toByteArray();
        if (bruto.length == 32) {
            return bruto;
        }
        byte[] saida = new byte[32];
        if (bruto.length > 32) {
            // BigInteger acrescenta um zero à esquerda quando o bit mais alto é 1
            System.arraycopy(bruto, bruto.length - 32, saida, 0, 32);
        } else {
            System.arraycopy(bruto, 0, saida, 32 - bruto.length, bruto.length);
        }
        return saida;
    }

    private static ECParameterSpec parametrosP256() {
        try {
            AlgorithmParameters parametros = AlgorithmParameters.getInstance("EC");
            parametros.init(new ECGenParameterSpec("secp256r1"));
            return parametros.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 indisponível na JVM", e);
        }
    }
}
